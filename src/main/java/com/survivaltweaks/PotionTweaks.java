package com.survivaltweaks;

import com.survivaltweaks.mixin.ArrowEffectsAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Potion effects work on every mob.
 *
 * 1. NeoForge asks MobEffectEvent.Applicable whenever an effect is about to be
 *    added (LivingEntity.addEffect -> CommonHooks.canMobEffectBeApplied). For every
 *    living entity that is not a player we answer APPLY, so vanilla immunities
 *    (undead vs. poison/regeneration, spiders vs. poison, wither skeletons vs.
 *    wither...) no longer block the effect. This replaces the Fabric edition's
 *    mixin around the canBeAffected call. Players keep vanilla rules.
 *    The other places vanilla checks canBeAffected (e.g. which mobs a splash
 *    potion or effect cloud targets) are handled by the canBeAffected mixins.
 *
 * 2. Tipped arrows on the Ender Dragon.
 *
 * Arrows hit the dragon's body PARTS, which are not living entities, so vanilla
 * never runs the arrow's effect code for the dragon. But all part damage is
 * passed on to the dragon itself, where NeoForge's LivingDamageEvent.Post fires with
 * the arrow as the direct damage source. At that point we run the arrow's own
 * vanilla effect method on the dragon (same effects, same 1/8 duration).
 * The dragon accepting effects at all is handled by mixin/BossEffectsMixin.
 *
 * Everything else about potions on mobs is done by the mixins (see MAINTAINING.md 3.15).
 */
public final class PotionTweaks {
    private PotionTweaks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Applicable event) -> {
            LivingEntity entity = event.getEntity();
            if (!(entity instanceof Player)) {
                event.setResult(MobEffectEvent.Applicable.Result.APPLY);
            }
        });

        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post event) -> {
            if (event.getBlockedDamage() <= 0.0f
                    && event.getEntity() instanceof EnderDragon dragon
                    && !dragon.level().isClientSide()
                    && event.getSource().getDirectEntity() instanceof ArrowEffectsAccessor arrow) {
                arrow.survivaltweaks$doPostHurtEffects(dragon);
            }
        });
    }
}
