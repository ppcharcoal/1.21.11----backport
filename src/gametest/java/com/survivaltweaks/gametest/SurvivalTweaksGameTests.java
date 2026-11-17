package com.survivaltweaks.gametest;

import com.mojang.authlib.GameProfile;
import com.survivaltweaks.KeepInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * In-game tests for Survival Tweaks (NeoForge GameTest framework). They are NOT
 * part of the released mod: this is a separate test mod in src/gametest, run by
 * `./gradlew runGameTestServer` (and on GitHub for every push). The server starts,
 * runs every test below in a real world, prints the results and exits with an
 * error if any test fails.
 *
 * Tests use a "mock" survival player (a real ServerPlayer with a fake network
 * connection) so totem, keep-inventory, anvil, hunger and bed code run exactly as
 * for a real player.
 */
@Mod(SurvivalTweaksGameTests.MOD_ID)
public final class SurvivalTweaksGameTests {
    public static final String MOD_ID = "survivaltweaks_tests";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    public SurvivalTweaksGameTests(IEventBus modBus) {
        TESTS.put("totem_from_inventory", SurvivalTweaksGameTests::totemFromInventory);
        TESTS.put("totem_saves_from_void", SurvivalTweaksGameTests::totemSavesFromVoid);
        TESTS.put("kill_command_still_kills", SurvivalTweaksGameTests::killStillKills);
        TESTS.put("keep_inventory_tools_and_armor", SurvivalTweaksGameTests::keepInventory);
        TESTS.put("beds_never_skip_night", SurvivalTweaksGameTests::bedsNeverSkip);
        TESTS.put("sleeping_heals", SurvivalTweaksGameTests::sleepingHeals);
        TESTS.put("beta_hunger_food_heals", SurvivalTweaksGameTests::betaHunger);
        TESTS.put("anvil_repair_is_free", SurvivalTweaksGameTests::anvilRepairFree);
        TESTS.put("anvil_rename_is_free", SurvivalTweaksGameTests::anvilRenameFree);
        TESTS.put("anvil_enchanting_still_costs", SurvivalTweaksGameTests::anvilEnchantCosts);
        TESTS.put("anvil_no_unbreaking_on_netherite_creative", SurvivalTweaksGameTests::anvilNoUnbreakingCreative);
        TESTS.put("no_unbreaking_on_unbreakable", SurvivalTweaksGameTests::noUnbreakingSupport);
        TESTS.put("unbreaking_stripped_from_inventory", SurvivalTweaksGameTests::unbreakingStripped);
        TESTS.put("rotten_flesh_effects_and_compost", SurvivalTweaksGameTests::rottenFlesh);
        TESTS.put("recipes", SurvivalTweaksGameTests::recipes);
        TESTS.put("advancements", SurvivalTweaksGameTests::advancements);
        TESTS.put("wild_cats_biomes", SurvivalTweaksGameTests::wildCats);
        TESTS.put("drowned_trident_drop_chance", SurvivalTweaksGameTests::drownedTrident);
        TESTS.put("effects_on_all_mobs", SurvivalTweaksGameTests::effectsOnMobs);
        TESTS.put("tipped_arrow_on_dragon", SurvivalTweaksGameTests::tippedArrowOnDragon);

        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        FUNCTIONS.register(modBus);
        modBus.addListener(SurvivalTweaksGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> env = event.registerEnvironment(Identifier.fromNamespaceAndPath(MOD_ID, "default"));
        for (String name : TESTS.keySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
            event.registerTest(id, new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(env, Identifier.withDefaultNamespace("empty"), 200, 0, true)));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A real survival ServerPlayer with a fake connection, placed in the test area. */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "st-test-" + UUID.randomUUID().toString().substring(0, 4)), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5));
        player.snapTo(pos.x, pos.y, pos.z, 0, 0);
        return player;
    }

    private static void remove(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static int levelOf(ItemStack stack, Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    // ------------------------------------------------------------------ totem

    private static void totemFromInventory(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.getInventory().setItem(20, new ItemStack(Items.TOTEM_OF_UNDYING));
        p.hurtServer(helper.getLevel(), p.damageSources().generic(), 1000f);
        check(helper, p.isAlive() && !p.isDeadOrDying(), "player died although a totem was in the inventory");
        check(helper, p.getInventory().getItem(20).isEmpty(), "totem was not used up");
        check(helper, p.hasEffect(MobEffects.REGENERATION) && p.hasEffect(MobEffects.ABSORPTION)
                && p.hasEffect(MobEffects.FIRE_RESISTANCE), "totem effects missing");
        remove(p);
        helper.succeed();
    }

    private static void totemSavesFromVoid(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        p.hurtServer(helper.getLevel(), p.damageSources().fellOutOfWorld(), 1000f);
        check(helper, p.isAlive() && !p.isDeadOrDying(), "void killed the player despite a totem");
        check(helper, p.getOffhandItem().isEmpty(), "totem was not used up");
        check(helper, p.hasEffect(MobEffects.SLOW_FALLING), "no slow falling after void rescue");
        remove(p);
        helper.succeed();
    }

    private static void killStillKills(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.getInventory().setItem(20, new ItemStack(Items.TOTEM_OF_UNDYING));
        p.hurtServer(helper.getLevel(), p.damageSources().genericKill(), Float.MAX_VALUE);
        check(helper, p.isDeadOrDying(), "/kill damage did not kill (totem must not save from it)");
        remove(p);
        helper.succeed();
    }

    // ------------------------------------------------------------------ keep inventory

    private static void keepInventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = player(helper);
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));          // tool -> kept
        p.getInventory().setItem(1, new ItemStack(Items.DIRT, 32));                 // material -> drops
        p.getInventory().setItem(2, new ItemStack(Items.NETHERITE_SWORD));          // unbreakable -> kept, no wear
        p.getInventory().setItem(20, new ItemStack(Items.IRON_PICKAXE));            // main inventory -> drops
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));    // armor -> kept
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));      // no durability -> drops
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));           // tool -> kept
        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.setDamageValue(worn.getMaxDamage() - 2);                                // nearly broken -> stops at 1 left
        p.getInventory().setItem(3, worn);

        p.hurtServer(level, p.damageSources().genericKill(), Float.MAX_VALUE);
        check(helper, p.isDeadOrDying(), "player did not die");
        check(helper, p.hasData(KeepInventory.KEPT_ITEMS) && !p.getData(KeepInventory.KEPT_ITEMS).isEmpty(),
                "kept items were not stored on the dead player");

        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(8));
        List<String> dropped = new ArrayList<>();
        for (ItemEntity e : drops) {
            dropped.add(e.getItem().getItem().toString());
        }
        check(helper, drops.stream().anyMatch(e -> e.getItem().is(Items.DIRT)), "dirt did not drop: " + dropped);
        check(helper, drops.stream().anyMatch(e -> e.getItem().is(Items.IRON_PICKAXE)), "main inventory did not drop: " + dropped);
        check(helper, drops.stream().anyMatch(e -> e.getItem().is(Items.CARVED_PUMPKIN)), "pumpkin did not drop: " + dropped);
        for (var kept : List.of(Items.DIAMOND_PICKAXE, Items.NETHERITE_SWORD, Items.IRON_CHESTPLATE, Items.SHIELD, Items.IRON_SWORD)) {
            check(helper, drops.stream().noneMatch(e -> e.getItem().is(kept)), kept + " was dropped (duplication risk)");
        }

        ServerPlayer respawned = level.getServer().getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
        respawned.connection.markClientLoaded();
        ItemStack pick = respawned.getInventory().getItem(0);
        check(helper, pick.is(Items.DIAMOND_PICKAXE), "pickaxe not back in hotbar slot 0: " + pick);
        int expected = (int) Math.ceil(pick.getMaxDamage() * 0.20);
        check(helper, pick.getDamageValue() == expected, "pickaxe wear " + pick.getDamageValue() + " != " + expected);
        ItemStack sword = respawned.getInventory().getItem(2);
        check(helper, sword.is(Items.NETHERITE_SWORD) && sword.getDamageValue() == 0, "netherite sword lost/worn: " + sword);
        ItemStack ironSword = respawned.getInventory().getItem(3);
        check(helper, ironSword.is(Items.IRON_SWORD) && ironSword.getDamageValue() == ironSword.getMaxDamage() - 1,
                "nearly broken sword should stop at 1 durability: " + ironSword);
        check(helper, respawned.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "chestplate not back");
        check(helper, respawned.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "shield not back");
        check(helper, respawned.getInventory().getItem(1).isEmpty(), "dirt came back (should have dropped)");
        check(helper, !respawned.hasData(KeepInventory.KEPT_ITEMS) || respawned.getData(KeepInventory.KEPT_ITEMS).isEmpty(),
                "stored copy not cleared after restoring");
        remove(respawned);
        drops.forEach(Entity::discard);
        helper.succeed();
    }

    // ------------------------------------------------------------------ beds / hunger

    private static void bedsNeverSkip(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.startSleeping(p.blockPosition());
        try {
            Field counter = Player.class.getDeclaredField("sleepCounter");
            counter.setAccessible(true);
            counter.setInt(p, 500);
        } catch (ReflectiveOperationException e) {
            throw helper.assertionException("cannot set sleepCounter: " + e);
        }
        check(helper, p.isSleeping(), "player is not sleeping");
        check(helper, !p.isSleepingLongEnough(), "isSleepingLongEnough() is true - the night would be skipped");
        p.stopSleeping();
        remove(p);
        helper.succeed();
    }

    private static void sleepingHeals(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setHealth(10f);
        p.startSleeping(p.blockPosition());
        // BedRework heals every TICKS_PER_HEAL ticks of the player's own tickCount;
        // the mock player doesn't tick on its own, so step its counter to a heal tick.
        p.tickCount = 0;
        helper.runAfterDelay(3, () -> {
            check(helper, p.getHealth() > 10f, "no healing while sleeping (health " + p.getHealth() + ")");
            p.stopSleeping();
            remove(p);
            helper.succeed();
        });
    }

    private static void betaHunger(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setHealth(4f);
        p.getFoodData().eat(5, 0.6f); // bread: 5 food, 6.0 saturation
        helper.runAfterDelay(3, () -> {
            check(helper, p.getFoodData().getFoodLevel() == 17, "food level not pinned at 17: " + p.getFoodData().getFoodLevel());
            check(helper, p.getFoodData().getSaturationLevel() == 0f, "saturation not reset");
            MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
            check(helper, regen != null && regen.getAmplifier() == 1, "no Regeneration II after eating");
            check(helper, regen.getDuration() >= 6 * 25 - 5 && regen.getDuration() <= 6 * 25,
                    "regen duration " + regen.getDuration() + " not ~150 ticks (6 HP)");
            remove(p);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ anvil

    private static AnvilMenu anvil(GameTestHelper helper, ServerPlayer p) {
        return new AnvilMenu(1, p.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
    }

    private static void anvilRepairFree(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.experienceLevel = 0;
        AnvilMenu menu = anvil(helper, p);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(1000);
        sword.set(DataComponents.REPAIR_COST, 7);
        menu.getSlot(0).set(sword);
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 2));
        ItemStack result = menu.getSlot(2).getItem();
        check(helper, !result.isEmpty() && result.getDamageValue() < 1000, "no repaired result");
        check(helper, menu.getCost() == 0, "repair costs " + menu.getCost() + " levels");
        check(helper, result.getOrDefault(DataComponents.REPAIR_COST, 0) == 7, "repair raised the prior-work penalty");
        check(helper, menu.getSlot(2).mayPickup(p), "a 0-level player can't take the free repair");
        remove(p);
        helper.succeed();
    }

    private static void anvilRenameFree(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.experienceLevel = 0;
        AnvilMenu menu = anvil(helper, p);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.REPAIR_COST, 3);
        menu.getSlot(0).set(sword);
        menu.setItemName("Sting");
        ItemStack result = menu.getSlot(2).getItem();
        check(helper, !result.isEmpty(), "no renamed result");
        check(helper, menu.getCost() == 0, "rename costs " + menu.getCost() + " levels");
        check(helper, result.getOrDefault(DataComponents.REPAIR_COST, 0) == 3, "rename raised the prior-work penalty");
        check(helper, menu.getSlot(2).mayPickup(p), "a 0-level player can't take the free rename");
        remove(p);
        helper.succeed();
    }

    private static void anvilEnchantCosts(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        AnvilMenu menu = anvil(helper, p);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        EnchantmentHelper.updateEnchantments(book, e -> e.set(enchantment(helper, Enchantments.SHARPNESS), 3));
        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        menu.getSlot(1).set(book);
        check(helper, !menu.getSlot(2).getItem().isEmpty(), "no result for sword + sharpness book");
        check(helper, menu.getCost() > 0, "applying an enchanted book was free (should cost levels)");
        remove(p);
        helper.succeed();
    }

    private static void anvilNoUnbreakingCreative(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setGameMode(GameType.CREATIVE);
        AnvilMenu menu = anvil(helper, p);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, e -> e.set(enchantment(helper, Enchantments.UNBREAKING), 3));
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_SWORD));
        menu.getSlot(1).set(book);
        ItemStack result = menu.getSlot(2).getItem();
        check(helper, result.isEmpty() || levelOf(result, enchantment(helper, Enchantments.UNBREAKING)) == 0,
                "creative anvil put Unbreaking on a netherite sword");
        remove(p);
        helper.succeed();
    }

    // ------------------------------------------------------------------ unbreaking

    private static void noUnbreakingSupport(GameTestHelper helper) {
        Holder<Enchantment> unbreaking = enchantment(helper, Enchantments.UNBREAKING);
        check(helper, !new ItemStack(Items.NETHERITE_SWORD).supportsEnchantment(unbreaking), "netherite sword accepts Unbreaking");
        check(helper, !new ItemStack(Items.ELYTRA).supportsEnchantment(unbreaking), "elytra accepts Unbreaking");
        check(helper, new ItemStack(Items.DIAMOND_SWORD).supportsEnchantment(unbreaking), "diamond sword no longer accepts Unbreaking");
        check(helper, new ItemStack(Items.NETHERITE_SWORD).supportsEnchantment(enchantment(helper, Enchantments.SHARPNESS)),
                "netherite sword no longer accepts Sharpness");
        helper.succeed();
    }

    private static void unbreakingStripped(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Holder<Enchantment> unbreaking = enchantment(helper, Enchantments.UNBREAKING);
        Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        EnchantmentHelper.updateEnchantments(sword, e -> {
            e.set(unbreaking, 3);
            e.set(sharpness, 2);
        });
        p.getInventory().setItem(5, sword);
        helper.runAfterDelay(25, () -> {
            ItemStack after = p.getInventory().getItem(5);
            check(helper, levelOf(after, unbreaking) == 0, "Unbreaking not stripped from netherite sword");
            check(helper, levelOf(after, sharpness) == 2, "other enchantments were touched");
            remove(p);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ items / data

    private static void rottenFlesh(GameTestHelper helper) {
        Consumable consumable = Items.ROTTEN_FLESH.components().get(DataComponents.CONSUMABLE);
        check(helper, consumable != null, "rotten flesh is not edible");
        boolean slowness = false, nausea = false, hunger = false;
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
                for (MobEffectInstance i : apply.effects()) {
                    slowness |= i.is(MobEffects.SLOWNESS);
                    nausea |= i.is(MobEffects.NAUSEA);
                    hunger |= i.is(MobEffects.HUNGER);
                }
            }
        }
        check(helper, slowness && nausea && !hunger, "rotten flesh effects wrong (slowness=" + slowness
                + ", nausea=" + nausea + ", hunger=" + hunger + ")");
        Compostable compost = new ItemStack(Items.ROTTEN_FLESH).getItemHolder().getData(NeoForgeDataMaps.COMPOSTABLES);
        check(helper, compost != null && compost.chance() == 0.5f, "rotten flesh not compostable at 50%: " + compost);
        helper.succeed();
    }

    private static Recipe<?> recipe(GameTestHelper helper, String id) {
        return helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)))
                .map(RecipeHolder::value)
                .orElse(null);
    }

    private static void recipes(GameTestHelper helper) {
        check(helper, recipe(helper, "survivaltweaks:totem_of_undying") != null, "totem recipe missing");
        check(helper, recipe(helper, "survivaltweaks:leather_from_smoking") != null, "leather smoking recipe missing");
        check(helper, recipe(helper, "minecraft:enchanting_table") instanceof ShapelessRecipe, "enchanting table recipe not replaced");
        check(helper, recipe(helper, "minecraft:fletching_table") instanceof ShapelessRecipe, "fletching table recipe not replaced");
        helper.succeed();
    }

    private static void advancements(GameTestHelper helper) {
        var adv = helper.getLevel().getServer().getAdvancements();
        for (String removed : List.of("adventure/trade", "adventure/hero_of_the_village", "adventure/voluntary_exile",
                "story/cure_zombie_villager", "story/enchant_item", "husbandry/allay_deliver_item_to_player")) {
            check(helper, adv.get(Identifier.withDefaultNamespace(removed)) == null, removed + " still exists");
        }
        var hiredHelp = adv.get(Identifier.withDefaultNamespace("adventure/summon_iron_golem"));
        check(helper, hiredHelp != null && hiredHelp.value().parent()
                .map(id -> id.equals(Identifier.withDefaultNamespace("adventure/root"))).orElse(false),
                "Hired Help missing or not under the Adventure root");
        var hunted = adv.get(Identifier.withDefaultNamespace("adventure/kill_all_mobs"));
        check(helper, hunted != null && !hunted.value().criteria().containsKey("minecraft:witch")
                && !hunted.value().criteria().containsKey("minecraft:pillager")
                && hunted.value().criteria().containsKey("minecraft:zombie"), "Monsters Hunted not adjusted");
        helper.succeed();
    }

    private static boolean catsSpawnIn(GameTestHelper helper, ResourceKey<net.minecraft.world.level.biome.Biome> biome) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome).value()
                .getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                .anyMatch(w -> w.value().type() == EntityType.CAT);
    }

    private static void wildCats(GameTestHelper helper) {
        for (var biome : List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.SAVANNA, Biomes.TAIGA, Biomes.SWAMP, Biomes.CHERRY_GROVE)) {
            check(helper, catsSpawnIn(helper, biome), "cats don't spawn in " + biome.identifier());
        }
        for (var biome : List.of(Biomes.DESERT, Biomes.JUNGLE, Biomes.SNOWY_PLAINS, Biomes.OCEAN)) {
            check(helper, !catsSpawnIn(helper, biome), "cats spawn in " + biome.identifier());
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ mobs

    private static void drownedTrident(GameTestHelper helper) {
        Drowned drowned = EntityType.DROWNED.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        check(helper, drowned != null, "could not create drowned");
        drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5));
        drowned.snapTo(pos.x, pos.y, pos.z, 0, 0);
        helper.getLevel().addFreshEntity(drowned);
        float chance = drowned.getDropChances().byEquipment(EquipmentSlot.MAINHAND);
        check(helper, chance >= 0.70f - 1e-4f, "trident drop chance " + chance);
        drowned.discard();
        helper.succeed();
    }

    private static void effectsOnMobs(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        check(helper, zombie.addEffect(new MobEffectInstance(MobEffects.POISON, 200)), "zombie refused poison");
        check(helper, zombie.hasEffect(MobEffects.POISON), "zombie has no poison");
        check(helper, zombie.canBeAffected(new MobEffectInstance(MobEffects.REGENERATION, 200)), "zombie immune to regeneration");
        Spider spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, 1, 1, 1);
        check(helper, spider.addEffect(new MobEffectInstance(MobEffects.POISON, 200)) && spider.hasEffect(MobEffects.POISON),
                "spider refused poison");
        WitherBoss wither = helper.spawnWithNoFreeWill(EntityType.WITHER, 1, 1, 1);
        wither.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200));
        check(helper, wither.hasEffect(MobEffects.SLOWNESS), "wither refused slowness");
        EnderDragon dragon = helper.spawnWithNoFreeWill(EntityType.ENDER_DRAGON, 1, 1, 1);
        dragon.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        check(helper, dragon.hasEffect(MobEffects.WEAKNESS), "dragon refused weakness");
        // players keep vanilla rules
        ServerPlayer p = player(helper);
        check(helper, p.addEffect(new MobEffectInstance(MobEffects.POISON, 200)), "player can't get poison (vanilla can)");
        remove(p);
        List.of(zombie, spider, wither, dragon).forEach(Entity::discard);
        helper.succeed();
    }

    private static void tippedArrowOnDragon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnderDragon dragon = helper.spawnWithNoFreeWill(EntityType.ENDER_DRAGON, 1, 3, 1);
        ItemStack tipped = PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.STRONG_SLOWNESS);
        Arrow arrow = new Arrow(level, dragon.getX(), dragon.getY() + 5, dragon.getZ(), tipped, null);
        ServerPlayer shooter = player(helper);
        arrow.setOwner(shooter);
        level.addFreshEntity(arrow);
        dragon.hurt(level, dragon.head, level.damageSources().arrow(arrow, shooter), 2f);
        check(helper, dragon.hasEffect(MobEffects.SLOWNESS), "tipped arrow effect not applied to the dragon");
        remove(shooter);
        arrow.discard();
        dragon.discard();
        helper.succeed();
    }
}
