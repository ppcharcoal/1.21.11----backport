package com.survivaltweaks;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Partial keep-inventory: on death you KEEP the tools, weapons and armor in your
 * hotbar, armor slots and off-hand. Everything else - blocks, materials, food,
 * the whole main inventory, XP - drops as usual.
 *
 * "Tool or armor" = any item with durability (the max_damage component): tools,
 * weapons, bows, crossbows, tridents, maces, shields, shears, fishing rods,
 * flint and steel, armor, elytra... Unbreakable netherite gear still counts (it
 * keeps max_damage, only adds the unbreakable component).
 *
 * Death penalty: every kept item loses DURABILITY_LOSS (20%) of its maximum
 * durability, but never breaks - it stops at 1 durability left. Unbreakable
 * items (netherite, elytra) lose nothing.
 *
 * Totem pops never cost durability: this code only runs from vanilla's
 * Player.dropEquipment, which is only reached from LivingEntity.die ->
 * dropAllDeathLoot. Vanilla's hand totem saves you before die() is called, and
 * our totem (TotemTweaks) cancels NeoForge's LivingDeathEvent, which fires at the
 * very start of die() - before any drops. Verified in the 1.21.11 game code.
 *
 * With the vanilla keep_inventory gamerule on, this does nothing (vanilla
 * keeps everything, no durability penalty).
 *
 * How it works:
 *  1. mixin/PlayerDropEquipmentMixin calls beforeDrops() at the very start of
 *     vanilla's Player.dropEquipment(ServerLevel) - the method that drops a dead
 *     player's inventory. The kept items are taken out of the inventory there, so
 *     vanilla never drops them.
 *  2. They are stored on the player as a NeoForge data attachment that is SAVED
 *     with the player and COPIED to the respawned player (copyOnDeath). So nothing
 *     is lost even if you quit to the title screen before clicking "Respawn".
 *  3. NeoForge's PlayerRespawnEvent puts them back into the same slots.
 *
 * No duplication: verified in the 1.21.11 game code that Inventory.dropAll() inside
 * dropEquipment is the ONLY place a dead player's items drop (the player loot
 * table is empty). Kept items are removed before it runs, so they never exist as
 * dropped items, not even for a tick, and after respawning they exist only in
 * the new player's inventory.
 *
 * Items with Curse of Vanishing are not kept - vanilla destroys them as usual.
 * If the mixin ever fails to apply (future update), deaths simply drop
 * everything like vanilla; nothing breaks. All members verified in the 1.21.11 game code.
 */
public final class KeepInventory {
    private KeepInventory() {
    }

    /** Equipment slots kept on death (MAINHAND is a hotbar slot, kept via the hotbar). */
    private static final List<EquipmentSlot> KEPT_EQUIPMENT = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);

    /** Share of an item's maximum durability lost on each death (0.20 = 20%). */
    private static final double DURABILITY_LOSS = 0.20;

    private static final String HOTBAR_PREFIX = "hotbar_";
    private static final String EQUIPMENT_PREFIX = "equipment_";

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SurvivalTweaks.MOD_ID);

    /**
     * slot key -> item. Keys look like "hotbar_3" or "equipment_HEAD". Never rename
     * (saved data). Only saved while it holds items; the old (dead) player entity
     * is thrown away after respawning, so the items exist in exactly one place.
     */
    public static final Supplier<AttachmentType<Map<String, ItemStack>>> KEPT_ITEMS = ATTACHMENTS.register(
            "kept_items",
            () -> AttachmentType.<Map<String, ItemStack>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, ItemStack.CODEC).fieldOf("items"), map -> !map.isEmpty())
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            // Respawning after death (not coming back from the End).
            if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer player) {
                restore(player);
            }
        });
    }

    /** Called by the mixin right before vanilla drops a dead player's inventory. */
    public static void beforeDrops(Player player, ServerLevel level) {
        // Only for a player who is really dying (dropEquipment is only called on death
        // in vanilla; this guards against other mods calling it).
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.isDeadOrDying()) {
            return;
        }
        // Vanilla keep_inventory gamerule on: vanilla keeps everything, leave it alone.
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }
        Map<String, ItemStack> kept = new HashMap<>();
        Inventory inventory = serverPlayer.getInventory();

        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (shouldKeep(stack)) {
                kept.put(HOTBAR_PREFIX + i, damagedCopy(stack));
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        for (EquipmentSlot slot : KEPT_EQUIPMENT) {
            ItemStack stack = serverPlayer.getItemBySlot(slot);
            if (shouldKeep(stack)) {
                kept.put(EQUIPMENT_PREFIX + slot.name(), damagedCopy(stack));
                serverPlayer.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        if (!kept.isEmpty()) {
            // Merge with anything still pending (e.g. died twice without respawning).
            Map<String, ItemStack> pending = serverPlayer.hasData(KEPT_ITEMS) ? serverPlayer.getData(KEPT_ITEMS) : null;
            if (pending != null && !pending.isEmpty()) {
                Map<String, ItemStack> merged = new HashMap<>(pending);
                merged.putAll(kept);
                kept = merged;
            }
            serverPlayer.setData(KEPT_ITEMS, Map.copyOf(kept));
        }
    }

    private static boolean shouldKeep(ItemStack stack) {
        return !stack.isEmpty()
                && stack.has(DataComponents.MAX_DAMAGE) // tools, weapons, armor - not materials/blocks/food
                && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP); // Curse of Vanishing
    }

    /**
     * Copy of the item with the death penalty applied: loses DURABILITY_LOSS of its
     * max durability (at least 1 point) but never breaks (stops at 1 left).
     * Unbreakable items (isDamageableItem() is false for them) are copied unchanged.
     */
    private static ItemStack damagedCopy(ItemStack stack) {
        ItemStack copy = stack.copy();
        if (copy.isDamageableItem()) {
            int max = copy.getMaxDamage();
            int loss = Math.max(1, (int) Math.ceil(max * DURABILITY_LOSS));
            int damage = Math.min(copy.getDamageValue() + loss, max - 1);
            if (damage > copy.getDamageValue()) {
                copy.setDamageValue(damage);
            }
        }
        return copy;
    }

    private static void restore(ServerPlayer player) {
        Map<String, ItemStack> kept = player.hasData(KEPT_ITEMS) ? player.removeData(KEPT_ITEMS) : null;
        if (kept == null || kept.isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (Map.Entry<String, ItemStack> entry : kept.entrySet()) {
            String key = entry.getKey();
            ItemStack stack = entry.getValue().copy();
            boolean placed = false;
            try {
                if (key.startsWith(HOTBAR_PREFIX)) {
                    int index = Integer.parseInt(key.substring(HOTBAR_PREFIX.length()));
                    if (inventory.getItem(index).isEmpty()) {
                        inventory.setItem(index, stack);
                        placed = true;
                    }
                } else if (key.startsWith(EQUIPMENT_PREFIX)) {
                    EquipmentSlot slot = EquipmentSlot.valueOf(key.substring(EQUIPMENT_PREFIX.length()));
                    if (player.getItemBySlot(slot).isEmpty()) {
                        player.setItemSlot(slot, stack);
                        placed = true;
                    }
                }
            } catch (IllegalArgumentException e) {
                // unknown/renamed slot in old save data: fall through to "add anywhere"
            }
            if (!placed && !inventory.add(stack) && !stack.isEmpty()) {
                player.spawnAtLocation(player.level(), stack); // inventory full: drop at feet
            }
        }
    }
}
