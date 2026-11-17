package com.survivaltweaks;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Beds no longer skip the night. Instead, lying in one heals half a heart (1 HP)
 * every 5 seconds.
 *
 * The day-skip is disabled by mixin/PlayerSleepMixin: vanilla's "has this player
 * slept long enough?" check (Player.isSleepingLongEnough) always answers no - the
 * same thing the Fabric edition does through Fabric's ALLOW_RESETTING_TIME event.
 * NeoForge's own SleepFinishedTimeEvent can't be used: it only changes the new
 * time, vanilla would still wake everyone up and clear the weather.
 *
 * Everything else about beds stays vanilla: you can still only get in at night or
 * during a thunderstorm, monsters nearby still stop you, and the bed still sets
 * your spawn point. Vanilla wakes you up when morning comes.
 */
public final class BedRework {
    private BedRework() {
    }

    private static final int TICKS_PER_HEAL = 100; // 5 seconds
    private static final float HEAL_AMOUNT = 1.0f;  // half a heart

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (player.isSleeping() && player.tickCount % TICKS_PER_HEAL == 0) {
                    player.heal(HEAL_AMOUNT);
                }
            }
        });
    }
}
