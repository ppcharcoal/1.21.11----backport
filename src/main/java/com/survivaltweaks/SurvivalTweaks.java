package com.survivaltweaks;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Survival Tweaks - NeoForge 1.21.11 backport.
 *
 * Most features are built on NeoForge events or on plain data files. A few small
 * mixins (anvil costs, potion effects on all mobs, keep inventory, beds, Unbreaking
 * on unbreakable gear - see the mixin package) are in a non-required mixin config,
 * so if one fails to apply after an update only that feature falls back to vanilla.
 *
 * Data-only features (see src/main/resources/data):
 *  - totem recipe:                 data/survivaltweaks/recipe/totem_of_undying.json
 *  - enchanting/fletching table:   data/minecraft/recipe/*.json (made uncraftable)
 *  - villages/outposts/mansions/huts: data/minecraft/worldgen/structure_set/*.json
 *  - patrols/traders/raids/phantoms off: data/survivaltweaks/function/gamerules/*.mcfunction
 *  - removed advancements:         data/minecraft/advancement/** (neoforge:never condition)
 *  - rotten flesh -> leather:      data/survivaltweaks/recipe/leather_from_smoking.json
 *  - compostable rotten flesh:     data/neoforge/data_maps/item/compostables.json
 *  - wild cats:                    data/survivaltweaks/neoforge/biome_modifier/wild_cats.json
 *
 * Client-only (HUD): client/BetaHud.java (its own @Mod entry point for Dist.CLIENT).
 *
 * Full maintenance guide: MAINTAINING.md in the project folder.
 */
@Mod(SurvivalTweaks.MOD_ID)
public class SurvivalTweaks {
    public static final String MOD_ID = "survivaltweaks";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public SurvivalTweaks(IEventBus modBus) {
        WorldState.register();
        SpawnRules.register();
        TotemTweaks.register();
        BedRework.register();
        ItemTweaks.register(modBus);
        LootTweaks.register();
        BetaHunger.register();
        UnbreakingRules.register();
        PotionTweaks.register();
        MobDrops.register();
        KeepInventory.register(modBus);
    }
}
