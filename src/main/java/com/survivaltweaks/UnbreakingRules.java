package com.survivaltweaks;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.IdentityHashMap;

/**
 * Unbreaking is pointless on unbreakable gear, so:
 *  1. It can't be put on any item that has the "unbreakable" component (all
 *     netherite gear and the elytra, see ItemTweaks) - neither from the enchanting
 *     table nor with books in the anvil, nor by enchanted loot.
 *     mixin/EnchantmentSupportMixin makes Enchantment.isSupportedItem answer "no"
 *     for that case; vanilla's anvil, enchanting table and enchant_randomly loot
 *     all ask it (through NeoForge's supportsEnchantment). This is what the Fabric
 *     edition does with Fabric's EnchantmentEvents.ALLOW_ENCHANTING. Because it
 *     checks the component rather than a list of items, future netherite items are
 *     covered automatically. (Creative-mode anvils skip that check - AnvilRules
 *     handles them.)
 *  2. Items that already have it lose it. The typical case: diamond armor/tools
 *     with Unbreaking upgraded to netherite in the smithing table. Every second,
 *     each online player's inventory is checked and Unbreaking is removed from
 *     unbreakable items (also covers loot and old items from before this version).
 */
public final class UnbreakingRules {
    private UnbreakingRules() {
    }

    private static final int CHECK_INTERVAL_TICKS = 20;

    /**
     * The Unbreaking enchantment objects of the currently loaded registries
     * (server, and on a client the synced copy). Enchantments are data-driven, so
     * the mixin can't compare by id; it compares against these instances. Filled
     * whenever tags/registries (re)load - TagsUpdatedEvent fires on both sides.
     * Compared by identity (Enchantment is a record, so equals() would be slow);
     * each reload adds one small object, which is harmless.
     */
    private static final Set<Enchantment> UNBREAKING_INSTANCES = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    public static void register() {
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) ->
                event.getLookupProvider().lookupOrThrow(Registries.ENCHANTMENT)
                        .get(Enchantments.UNBREAKING)
                        .ifPresent(holder -> UNBREAKING_INSTANCES.add(holder.value())));

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                stripFromInventory(player);
            }
        });
    }

    /** Called by EnchantmentSupportMixin: true = this enchantment may NOT go on this item. */
    public static boolean blocks(Enchantment enchantment, ItemStack target) {
        return target.has(DataComponents.UNBREAKABLE) && UNBREAKING_INSTANCES.contains(enchantment);
    }

    private static void stripFromInventory(ServerPlayer player) {
        Holder<Enchantment> unbreaking = player.level().registryAccess().getOrThrow(Enchantments.UNBREAKING);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.has(DataComponents.UNBREAKABLE) || !stack.has(DataComponents.ENCHANTMENTS)) {
                continue;
            }
            // Work on a copy and only write back if something actually changed,
            // so untouched items never cause needless inventory updates.
            ItemStack copy = stack.copy();
            EnchantmentHelper.updateEnchantments(copy, enchantments -> enchantments.set(unbreaking, 0));
            if (!Objects.equals(copy.get(DataComponents.ENCHANTMENTS), stack.get(DataComponents.ENCHANTMENTS))) {
                stack.set(DataComponents.ENCHANTMENTS, copy.get(DataComponents.ENCHANTMENTS));
            }
        }
    }
}
