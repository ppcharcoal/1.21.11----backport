package com.survivaltweaks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Totem of Undying rework (players only):
 *  - works from ANYWHERE in the inventory, not just the hands
 *  - saves you from the void: you're teleported to your respawn point
 *    (bed / respawn anchor / world spawn - vanilla's own lookup decides)
 *
 * Effects are exactly vanilla's: health set to 1, all effects cleared, then
 * Regeneration II (45s), Absorption II (5s), Fire Resistance (40s).
 *
 * Runs on NeoForge's LivingDeathEvent, which fires at the start of die() - AFTER
 * vanilla's own totem check has failed. A totem in your hand on a normal death is
 * therefore always used by vanilla first (so "Postmortal" and all vanilla behaviour
 * stay intact). This mod only acts where vanilla did NOT save you: a totem
 * elsewhere in the inventory, or the void. Cancelling the event (with health set
 * back to 1) cancels the death, so nothing is dropped.
 */
public final class TotemTweaks {
    private TotemTweaks() {
    }

    public static void register() {
        // HIGHEST: decide before other mods react to the death.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && rescue(player, event.getSource())) {
                event.setCanceled(true); // cancel the death
            }
        });
    }

    /** @return true if a totem saved the player. */
    private static boolean rescue(ServerPlayer player, DamageSource source) {
        boolean voidDeath = source.is(DamageTypes.FELL_OUT_OF_WORLD);

        // Like vanilla, /kill and similar "unstoppable" damage still kills you.
        // The void is the one exception we deliberately add.
        if (!voidDeath && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        // Normal death + totem in hand: vanilla already had its chance (and did not
        // use it, e.g. another mod vetoed it) - respect that.
        if (!voidDeath && hasTotemInHand(player)) {
            return false;
        }

        ItemStack totem = findTotem(player);
        if (totem.isEmpty()) {
            return false;
        }

        totem.shrink(1);
        // Vanilla "times used" statistic.
        player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));

        player.setHealth(1.0f);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));

        if (voidDeath) {
            // false = only read the bed/anchor position, don't use up an anchor charge.
            TeleportTransition transition =
                    player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
            player.teleport(transition);
            player.resetFallDistance();
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        }

        // Totem animation + sound on the client (entity event 35).
        player.level().broadcastEntityEvent(player, (byte) 35);
        return true;
    }

    private static boolean hasTotemInHand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }
        return false;
    }

    /** Hands first (like vanilla), then the rest of the inventory. */
    private static ItemStack findTotem(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                return stack;
            }
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
