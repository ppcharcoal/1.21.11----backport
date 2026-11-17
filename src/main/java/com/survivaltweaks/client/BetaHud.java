package com.survivaltweaks.client;

import com.survivaltweaks.SurvivalTweaks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Beta-style HUD (client only): the hunger bar is gone and the ARMOR bar takes its
 * place on the right, filling right-to-left like the hunger bar did.
 *
 * Done with NeoForge's RegisterGuiLayersEvent.wrapLayer:
 *  - the vanilla FOOD_LEVEL layer is replaced by our right-side armor bar;
 *  - the vanilla ARMOR_LEVEL layer (left side) only draws while riding a mount
 *    with hearts, because the mount's hearts then take the right side (vanilla
 *    hides the food bar there too) - armor goes back to its vanilla spot.
 *
 * Gui.rightHeight / leftHeight are NeoForge's "how high are the status bars on
 * each side" counters; raising rightHeight by one row when we draw armor puts the
 * air bubbles above it underwater, exactly like above the old hunger bar.
 *
 * Vanilla's half-armor sprite is the LEFT half of an icon; on a bar that fills
 * right-to-left the half icon must show the RIGHT half. It is drawn as the empty
 * icon plus the right part of the full icon (scissored), so no new textures needed.
 */
@Mod(value = SurvivalTweaks.MOD_ID, dist = Dist.CLIENT)
public final class BetaHud {
    private static final Identifier ARMOR_EMPTY = Identifier.withDefaultNamespace("hud/armor_empty");
    private static final Identifier ARMOR_FULL = Identifier.withDefaultNamespace("hud/armor_full");
    /** Pixel column where vanilla's half icon is split (the sprite is 9 px wide). */
    private static final int HALF_SPLIT = 4;

    public BetaHud(IEventBus modBus) {
        modBus.addListener(BetaHud::registerLayers);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.FOOD_LEVEL, vanillaFood -> BetaHud::renderRightArmor);
        event.wrapLayer(VanillaGuiLayers.ARMOR_LEVEL, vanillaArmor -> (GuiGraphics graphics, DeltaTracker deltaTracker) -> {
            Player player = Minecraft.getInstance().player;
            if (player != null && isRidingMountWithHearts(player)) {
                vanillaArmor.render(graphics, deltaTracker);
            }
        });
    }

    private static void renderRightArmor(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !showsBars() || isRidingMountWithHearts(player)) {
            return;
        }
        int armor = player.getArmorValue();
        if (armor <= 0) {
            return;
        }
        int y = graphics.guiHeight() - minecraft.gui.rightHeight;
        int right = graphics.guiWidth() / 2 + 91;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            if (i * 2 + 1 < armor) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_FULL, x, y, 9, 9);
            } else if (i * 2 + 1 == armor) {
                drawMirroredHalf(graphics, x, y);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_EMPTY, x, y, 9, 9);
            }
        }
        minecraft.gui.rightHeight += 10;
    }

    private static void drawMirroredHalf(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_EMPTY, x, y, 9, 9);
        graphics.enableScissor(x + HALF_SPLIT, y, x + 9, y + 9);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_FULL, x, y, 9, 9);
        graphics.disableScissor();
    }

    /** Same rule vanilla uses for the health/food/armor layers (survival-like modes, HUD shown). */
    private static boolean showsBars() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer() && !minecraft.options.hideGui;
    }

    private static boolean isRidingMountWithHearts(Player player) {
        return player.getVehicle() instanceof LivingEntity;
    }
}
