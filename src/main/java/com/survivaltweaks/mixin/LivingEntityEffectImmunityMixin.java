package com.survivaltweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Potion effects (drunk, splash, lingering, tipped arrows...) work on ALL mobs.
 *
 * Vanilla asks canBeAffected(effect) before adding an effect and when choosing
 * targets (splash potions, effect clouds...); mobs answer "no" for their
 * immunities (undead: poison/regeneration, entity-type immunity tags...). Here
 * LivingEntity.canBeAffected's answer becomes "yes" for every non-player mob.
 * Mob classes with their own canBeAffected are covered by
 * EffectImmunityOverridesMixin, and the actual adding of an effect also goes
 * through NeoForge's MobEffectEvent.Applicable, which PotionTweaks answers with
 * APPLY - so effects still work on mobs even if this mixin ever fails to apply.
 *
 * Not changed: undead are still HEALED by Instant Damage and HURT by Instant
 * Health - that is an inversion, not an immunity.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectImmunityMixin {
    @ModifyReturnValue(method = "canBeAffected", at = @At("RETURN"))
    private boolean survivaltweaks$ignoreImmunity(boolean original) {
        return original || !((Object) this instanceof Player);
    }
}
