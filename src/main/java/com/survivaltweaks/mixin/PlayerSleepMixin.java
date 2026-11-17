package com.survivaltweaks.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Beds don't skip the night (BedRework). Vanilla only skips the night (and wakes
 * everyone, clears the weather) once enough players have "slept long enough"
 * (SleepStatus counts Player.isSleepingLongEnough). Answering "no" here means the
 * night never skips, while lying in bed works normally - vanilla still wakes you
 * up in the morning. Same effect as the Fabric edition's ALLOW_RESETTING_TIME.
 * Non-required: if this fails to apply, beds skip the night like vanilla.
 */
@Mixin(Player.class)
public abstract class PlayerSleepMixin {
    @Inject(method = "isSleepingLongEnough", at = @At("HEAD"), cancellable = true)
    private void survivaltweaks$neverSkipNight(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
