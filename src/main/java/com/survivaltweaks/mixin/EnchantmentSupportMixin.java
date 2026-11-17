package com.survivaltweaks.mixin;

import com.survivaltweaks.UnbreakingRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unbreaking can't go on unbreakable items (UnbreakingRules). Vanilla's anvil,
 * enchanting table and enchant_randomly loot function all decide through
 * Enchantment.isSupportedItem (via NeoForge's IItemExtension.supportsEnchantment).
 * Non-required: if this fails to apply, UnbreakingRules still strips Unbreaking
 * from unbreakable items within a second.
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentSupportMixin {
    @Inject(method = "isSupportedItem", at = @At("HEAD"), cancellable = true)
    private void survivaltweaks$noUnbreakingOnUnbreakable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (UnbreakingRules.blocks((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(false);
        }
    }
}
