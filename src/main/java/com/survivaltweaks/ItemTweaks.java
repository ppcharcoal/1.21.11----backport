package com.survivaltweaks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.List;
import java.util.Set;

/**
 * Changes vanilla items' default components through NeoForge's
 * ModifyDefaultComponentsEvent (no mixins). This also applies to items that already
 * exist in old worlds.
 *
 * Compostable rotten flesh is NOT here: in 1.21.11 compostability is a NeoForge
 * data map, see data/neoforge/data_maps/item/compostables.json (same 50% chance as
 * a melon slice).
 *
 * NOTE: item defaults live on both sides, so the mod must be installed on the
 * client as well as on a server for stacking/tooltips to display correctly.
 */
public final class ItemTweaks {
    private ItemTweaks() {
    }

    private static final int POTION_STACK_SIZE = 16;

    /**
     * Meat that can't stack (Beta style). Fish and rotten flesh are deliberately NOT
     * included (rotten flesh is needed in bulk for smoker leather and composting).
     * Add or remove items here.
     */
    private static final Set<Item> UNSTACKABLE_MEAT = Set.of(
            Items.BEEF, Items.COOKED_BEEF,
            Items.PORKCHOP, Items.COOKED_PORKCHOP,
            Items.CHICKEN, Items.COOKED_CHICKEN,
            Items.MUTTON, Items.COOKED_MUTTON,
            Items.RABBIT, Items.COOKED_RABBIT
    );

    /**
     * Rotten flesh: Slowness + Nausea instead of vanilla's Hunger (which does
     * nothing with the Beta hunger system). Same 80% chance as vanilla. Built
     * exactly like vanilla's Consumables.ROTTEN_FLESH (default food + apply
     * effects), only the effects differ. Verified in the 1.21.11 game code.
     */
    private static final float ROTTEN_FLESH_EFFECT_CHANCE = 0.8f;
    private static final int ROTTEN_FLESH_SLOWNESS_TICKS = 20 * 30; // 30 s
    private static final int ROTTEN_FLESH_NAUSEA_TICKS = 20 * 15;   // 15 s

    private static final Set<Item> POTIONS = Set.of(
            Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION
    );

    public static void register(IEventBus modBus) {
        modBus.addListener(ItemTweaks::modify);
    }

    private static void modify(ModifyDefaultComponentsEvent event) {
        for (Item item : UNSTACKABLE_MEAT) {
            event.modify(item, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 1));
        }
        for (Item item : POTIONS) {
            event.modify(item, builder -> builder.set(DataComponents.MAX_STACK_SIZE, POTION_STACK_SIZE));
        }

        // Every vanilla netherite item that has durability (tools, weapons, spear,
        // armor - whatever exists in this version) becomes unbreakable. Items
        // without durability (ingot, scrap, block, horse armor...) are skipped.
        event.modifyMatching(item -> isNetherite(item) && item.components().has(DataComponents.MAX_DAMAGE),
                builder -> builder.set(DataComponents.UNBREAKABLE, Unit.INSTANCE));

        event.modify(Items.ELYTRA, builder -> builder.set(DataComponents.UNBREAKABLE, Unit.INSTANCE));

        event.modify(Items.ROTTEN_FLESH, builder -> builder.set(DataComponents.CONSUMABLE,
                Consumables.defaultFood()
                        .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                                new MobEffectInstance(MobEffects.SLOWNESS, ROTTEN_FLESH_SLOWNESS_TICKS, 0),
                                new MobEffectInstance(MobEffects.NAUSEA, ROTTEN_FLESH_NAUSEA_TICKS, 0)),
                                ROTTEN_FLESH_EFFECT_CHANCE))
                        .build()));
    }

    private static boolean isNetherite(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("minecraft") && id.getPath().startsWith("netherite_");
    }
}
