# Survival Tweaks (NeoForge 1.21.11) – Maintenance & Update Guide

This is the NeoForge backport of Survival Tweaks for Minecraft **1.21.11**. The
main mod is the Fabric edition for Minecraft 26.x
(https://github.com/ppcharcoal/survivaltweaks). Both editions have the same
features and the same data/ids; this file explains how each feature works HERE,
where it lives, and what to check when updating NeoForge or porting a change from
the Fabric edition. Hand this file to whoever (or whatever AI) does the next
update, together with the project folder.

---

## 0. The compatibility checker (run this first on every update)

`tools/verify_against_minecraft.py` checks everything this mod relies on in
vanilla + NeoForge: mixin target methods, Java members, advancement ids/parents,
structure-set format, loot-table format (key by key), recipe/loot/entity/
gamerule/sprite ids. Needs only Python 3.

Minecraft 1.21.11 jars are obfuscated, so the checker reads the two jars
ModDevGradle creates (Mojang names + the game's data files):

    ./gradlew createMinecraftArtifacts
    python tools/verify_against_minecraft.py \
        build/moddev/artifacts/neoforge-<neo_version>.jar \
        build/moddev/artifacts/neoforge-<neo_version>-client-extra-aka-minecraft-resources.jar
    ... add --fix to re-generate the files that are copies of vanilla

Fix every FAIL before building; read every WARN. With NeoForge 21.11.48 it
reports 118 OK / 0 WARN / 0 FAIL.

## 1. Design rules (keep following these!)

1. **Prefer NeoForge events and data files; mixins only where there is no event.**
   Mixins: anvil cost (3.13), potion immunities and bosses (3.15), keep inventory
   (3.17), "beds don't skip the night" (3.5), Unbreaking on unbreakable gear
   (3.14). They are tiny, call plain Java classes, use no private fields, and
   their config `survivaltweaks.mixins.json` is `"required": false` - if an update
   breaks one, the game still starts and only that feature falls back to vanilla
   (the log shows a mixin error naming the class).
2. **Verify, don't guess.** Before using a Minecraft/NeoForge class or method,
   check it exists for that version: run the checker (section 0), or look in the
   NeoForge sources (in IntelliJ after `gradlew build`, or decompile
   `build/moddev/artifacts/neoforge-*.jar`). Copy data-file formats from the
   game's own `data/minecraft/...` files - never from the Fabric edition: 26.x
   changed several formats (see 3.9).
3. **Prefer data files** (JSON / mcfunction) for anything Minecraft supports as
   data. **EXCEPTION:** advancements, loot tables and cat variants load like
   registries - ONE broken file can stop every world from opening. Always test
   that a world opens after touching them.
4. **One thing per file** where a rename could break it (see gamerules below).
5. **Never rename saved IDs** (section 4). Existing worlds depend on them.

---

## 2. Updating – checklist

NeoForge for a newer 1.21.11 build:
1. Change `neo_version` in `gradle.properties` (versions:
   https://projects.neoforged.net/neoforged/neoforge). If the MDK template for
   1.21.11 changed the ModDevGradle plugin version or Gradle wrapper, copy those.
2. Run `./gradlew createMinecraftArtifacts`, then the checker (section 0).
3. `./gradlew build` and go through the test checklist (section 6).

Porting a change from the Fabric edition:
- Java: the feature classes have the same names and the same logic; only the
  event registration differs (Fabric event -> NeoForge event, table below).
- Data: copy the JSON, then convert it to the 1.21.11 format (loot tables: 3.9;
  removed advancements: 3.3) and run the checker.

| Fabric edition (26.x) | This edition (NeoForge 1.21.11) |
|---|---|
| `ServerEntityEvents.ALLOW_LOAD` / `ENTITY_LOAD` | `EntityJoinLevelEvent` (cancel = never exists) |
| `ServerLivingEntityEvents.ALLOW_DEATH` | `LivingDeathEvent` (cancel + set health) |
| `ServerLivingEntityEvents.AFTER_DEATH` | `LivingDeathEvent` (LOWEST, not cancelled) |
| `ServerLivingEntityEvents.AFTER_DAMAGE` | `LivingDamageEvent.Post` |
| `ServerTickEvents.END_SERVER_TICK` | `ServerTickEvent.Post` |
| `ServerLifecycleEvents` | `ServerStartingEvent` / `ServerStoppedEvent` |
| `EntitySleepEvents.ALLOW_RESETTING_TIME` | `mixin/PlayerSleepMixin` |
| `DefaultItemComponentEvents.MODIFY` | `ModifyDefaultComponentsEvent` (mod bus) |
| `compostable` item component (26.x) | data map `data/neoforge/data_maps/item/compostables.json` |
| `LootTableEvents.MODIFY` | `LootTableLoadEvent` |
| `EnchantmentEvents.ALLOW_ENCHANTING` | `mixin/EnchantmentSupportMixin` |
| `ServerPlayerEvents.AFTER_RESPAWN` | `PlayerEvent.PlayerRespawnEvent` |
| Fabric data attachments | NeoForge `AttachmentType` (DeferredRegister) |
| `BiomeModifications.addSpawn` | biome modifier JSON `data/survivaltweaks/neoforge/biome_modifier/wild_cats.json` |
| `HudElementRegistry` / `HudStatusBarHeightRegistry` | `RegisterGuiLayersEvent.wrapLayer` + `Gui.rightHeight` |
| `fabric:load_conditions` | `neoforge:conditions` |
| mixin around `canBeAffected` in `addEffect` | `MobEffectEvent.Applicable` -> `APPLY` |

---

## 3. Features – how each one works and what can break

### 3.1 Cave-only spawning + dragon progression — `SpawnRules.java`
- NeoForge `EntityJoinLevelEvent` runs right before ANY entity is added to a world.
  Cancelling it means the entity never exists.
- Before the dragon: entities in tag `survivaltweaks:cave_only` (creepers, spiders)
  are refused in the Overworld if they can see the sky.
- After the dragon: entities in tag `survivaltweaks:cave_only_after_dragon` are
  refused in the Overworld if they can see the sky OR are at y >= 63 (`SURFACE_Y`).
- Bosses (Wither, Ender Dragon) are never touched by any spawn rule.
- Only NEW mobs are checked (`loadedFromDisk()` = left alone). Deliberate spawns
  are always allowed: the reason comes from `Mob.getSpawnType()` (added by
  NeoForge, set in `finalizeSpawn`): `COMMAND`, `SPAWN_ITEM_USE`, `DISPENSER`.
  Known difference from the Fabric edition: `/summon` WITH extra NBT data skips
  `finalizeSpawn` in vanilla, so it has no reason and is judged like a natural
  spawn.
- Dragon detection: `LivingDeathEvent` on `EnderDragon` (fires when the killing
  blow lands, before the death animation; `/kill` removes the dragon without
  dying, like in vanilla, so it doesn't count), plus a fallback every 5 seconds
  that checks whether an online player has `minecraft:end/kill_dragon` or
  `main:end/kill_dragon` (the Matcha Flavoured one).
- Lists of mobs: `data/survivaltweaks/tags/entity_type/*.json`. Entries that might
  not exist in every version use `{"id": ..., "required": false}`.

### 3.2 No villagers / illagers / witches / phantoms — `SpawnRules.java` + data
- Tag `survivaltweaks:never_spawn` is refused everywhere, for every reason,
  including entities saved in old chunks and ones placed by world generation.
- Structures: `data/minecraft/worldgen/structure_set/` overrides `villages`,
  `pillager_outposts`, `woodland_mansions`, `swamp_huts` with `"frequency": 0.0`
  (GENERATED from the game's own files by the checker, `--fix`).
- Explorer maps: in 1.21.11 no chest loot leads to a removed structure (the 26.3
  campsite does not exist yet). The checker scans every vanilla loot table and
  FAILs if one appears - then add it to `MODIFIED_LOOT_TABLES` and run `--fix`.
- Gamerules: `data/survivaltweaks/function/gamerules/*.mcfunction`, one rule per
  file (`spawn_patrols`, `spawn_wandering_traders`, `raids`, `spawn_phantoms` -
  1.21.11 already uses these snake_case names), all run on world load via
  `data/minecraft/tags/function/load.json` (entries are `required: false`).
- Insomnia/phantoms: phantoms are in `never_spawn` AND `spawn_phantoms` is off.

### 3.3 Removed advancements — `data/minecraft/advancement/**`
- Each file overrides a vanilla advancement with only the NeoForge condition
  `"neoforge:conditions": [{"type": "neoforge:never"}]`. NeoForge then skips the
  file, so the advancement doesn't exist. Each file has a `__comment`.
- REMOVED: What a Deal!, Star Trader, Zombie Doctor, Surge Protector, Very Very
  Frightening, Voluntary Exile, Hero of the Village, Who's the Pillager Now?, Two
  Birds One Arrow, Enchanter, You've Got a Friend in Me, Birthday Song.
- MODIFIED (kept, impossible parts dropped) - GENERATED by the checker from the
  game's files, never hand-edited: Monsters Hunted, How Did We Get Here?,
  Smithing with Style. Hired Help is an exact vanilla copy re-parented to the
  Adventure root. Rules: `MODIFIED_ADVANCEMENTS` / `IMPOSSIBLE_EXTRA` in the
  checker; the checker FAILs if any other advancement needs something impossible.
- Do NOT remove recipes this way (other files reference recipes) - recipes are
  disabled by replacement instead (3.6).

### 3.4 Totem of Undying — `TotemTweaks.java` + recipe
- NeoForge `LivingDeathEvent` (HIGHEST). It fires at the start of `die()`, i.e.
  AFTER vanilla's own totem check failed - so a totem in your hand on a normal
  death is always used by vanilla (Postmortal keeps working).
- Totem anywhere else in the inventory, or ANY void death (vanilla never uses a
  totem there): the mod uses the totem, sets health to 1, applies vanilla's
  effects, cancels the event (= no death, no drops), and for the void teleports
  you to your respawn point with Slow Falling.
- `/kill` still kills you (damage that bypasses invulnerability), like vanilla.

### 3.5 Beds — `BedRework.java` + `mixin/PlayerSleepMixin.java`
- `Player.isSleepingLongEnough()` always returns false, so vanilla's sleep count
  never reaches "enough" and the night never skips (vanilla still wakes you in
  the morning). NeoForge's `SleepFinishedTimeEvent` is NOT used: vanilla would
  still wake everyone and clear the weather.
- A server tick loop heals sleeping players 1 HP every 100 ticks.

### 3.6 Uncraftable enchanting table & fletching table — data
- `data/minecraft/recipe/enchanting_table.json` / `fletching_table.json` replace
  the vanilla recipes with "barrier -> barrier". The IDs must keep existing.

### 3.7 Items — `ItemTweaks.java` + data map
- NeoForge `ModifyDefaultComponentsEvent` (mod bus) changes item defaults.
- Potions/splash/lingering: `max_stack_size` 16. Netherite items with durability +
  elytra: `unbreakable`. Rotten flesh: Slowness (30 s) + Nausea (15 s), 80%.
- Compostable rotten flesh: `data/neoforge/data_maps/item/compostables.json`
  (chance 0.5 = melon slice). In 1.21.11 compost chances are a NeoForge data map,
  not an item component.

### 3.8 Rotten flesh -> leather in the smoker — data
- `data/survivaltweaks/recipe/leather_from_smoking.json`.

### 3.9 Bonus loot, themed books, mob drops — `LootTweaks.java` + data
- `LootTweaks.java` has ONE map: vanilla loot table -> our tables, added as extra
  pools in NeoForge's `LootTableLoadEvent` (vanilla loot is never removed).
- Tiers (`loot_table/tiers/`), themed books (`loot_table/books/`), membranes
  (`chests/end_city_phantom_membranes`), wither skeleton skull 70%
  (`mobs/wither_skeleton_skull`): same content as the Fabric edition.
- **1.21.11 LOOT FORMAT** (different from 26.x!): functions are
  `"functions": [{"function": "minecraft:set_count", ...}]`, conditions are
  `"conditions": [{"condition": "minecraft:killed_by_player"}]`. The 26.x
  format (`"modifier"` / `"condition"` with `"type"`) would be silently ignored.
  The checker compares every key with vanilla's loot tables and FAILs on unknown
  ones. When copying a loot file from the Fabric edition, convert it.

### 3.10 Beta hunger — `BetaHunger.java` (server)
- Food level pinned at 17 with 0 saturation every tick (infinite sprint, no
  starvation, no natural regeneration, can always eat). Eating = saturation
  appears -> Regeneration II for saturation x `HP_PER_SATURATION` HP.

### 3.11 Beta HUD — `client/BetaHud.java` (client-only `@Mod`, `dist = Dist.CLIENT`)
- `RegisterGuiLayersEvent.wrapLayer`: `FOOD_LEVEL` is replaced by the armor bar
  drawn on the right (right-to-left); `ARMOR_LEVEL` only draws while riding a
  living mount. A wrapped layer replaces vanilla's visibility check too, so the
  code checks `canHurtPlayer()` and `hideGui` itself.
- Heights: NeoForge's `Gui.rightHeight` is raised by 10 when armor is drawn, so
  air bubbles sit above it.
- Half icon: the empty icon + the right part of the full icon (`enableScissor`,
  `HALF_SPLIT`), because vanilla's half sprite is filled on the left.

### 3.12 Unstackable meat — `ItemTweaks.java`
- `UNSTACKABLE_MEAT` (raw + cooked beef, porkchop, chicken, mutton, rabbit) get
  `max_stack_size` 1.

### 3.13 Anvil: free repair & rename — `AnvilRules.java` + `mixin/AnvilMenuMixin.java`
- Same as the Fabric edition: after `AnvilMenu.createResult()`, a result with
  unchanged enchantments costs 0 levels and keeps the old `repair_cost`;
  `mayPickup` allows taking 0-level results. NeoForge's `AnvilUpdateEvent` is not
  used: it replaces vanilla's whole calculation.
- If another mod handles the anvil through `AnvilUpdateEvent`, vanilla's
  calculation (and this hook) is skipped for that item - their result wins.

### 3.14 No Unbreaking on unbreakable gear — `UnbreakingRules.java` + `mixin/EnchantmentSupportMixin.java`
- `Enchantment.isSupportedItem(stack)` returns false for Unbreaking on any item
  with the `unbreakable` component. The anvil, enchanting table and
  `enchant_randomly` loot all ask it (through NeoForge's `supportsEnchantment`).
  Enchantments are data-driven, so the mixin compares against the Unbreaking
  instance(s) collected in `TagsUpdatedEvent` (fires on server and client).
- The anvil also refuses it directly (`AnvilRules`, needed for creative mode).
- Every 20 ticks unbreakable items in players' inventories lose Unbreaking.

### 3.15 Potion effects & tipped arrows on all mobs and bosses — `PotionTweaks.java`, `mixin/*Effect*`
- `MobEffectEvent.Applicable` (NeoForge asks it in `addEffect`): result `APPLY`
  for every non-player living entity.
- `LivingEntityEffectImmunityMixin`: `canBeAffected` returns true for non-players
  (other places that ask: splash potion/effect cloud targeting).
- `EffectImmunityOverridesMixin`: classes with their OWN `canBeAffected`
  (1.21.11: WitherBoss, Spider, WitherSkeleton, Parched, AbstractNautilus). The
  checker lists any new/removed ones.
- `BossEffectsMixin`: EnderDragon and WitherBoss refuse every effect in their own
  `addEffect`; replaced by vanilla's `forceAddEffect`.
- Dragon + tipped arrows: `LivingDamageEvent.Post` on the dragon with an arrow as
  direct source -> the arrow's own `doPostHurtEffects` (`ArrowEffectsAccessor`).

### 3.16 Drowned trident 70% — `MobDrops.java`
- `EntityJoinLevelEvent`: a drowned holding a trident gets main-hand drop chance
  0.70 (never lowered).

### 3.17 Partial keep inventory — `KeepInventory.java` + `mixin/PlayerDropEquipmentMixin.java`
- On death you keep the tools, weapons and armor (anything with `max_damage`) in
  hotbar, armor slots and off-hand; each loses 20% of max durability, never below
  1; unbreakable items lose nothing. Curse of Vanishing items are not kept.
- The mixin hooks the start of `Player.dropEquipment(ServerLevel)` and takes the
  kept items out before vanilla drops anything (no duplication: that is the only
  place a dead player's items drop, re-checked by the checker).
- Stored in the NeoForge player attachment `survivaltweaks:kept_items`
  (serialized, `copyOnDeath`), restored in `PlayerEvent.PlayerRespawnEvent`
  (not when coming back from the End).
- Totem saves never reach this code (3.4 cancels the death first).
- With the `keep_inventory` gamerule on, the mod does nothing.

### 3.18 Wild cats — biome modifier + data
- `data/survivaltweaks/neoforge/biome_modifier/wild_cats.json`
  (`neoforge:add_spawns`: cat, weight 4, groups 1-2) in biome tag
  `survivaltweaks:cats_spawn_in`. (26.x's Dappled Forest doesn't exist in 1.21.11.)
- Breeds by biome: `data/minecraft/cat_variant/*.json` are GENERATED by the
  checker (`CAT_VARIANT_RULES`, `--fix`).

---

## 4. Saved data – NEVER rename these

| What | Where | Used for |
|---|---|---|
| `<world>/data/survivaltweaks/world_state.properties`, key `dragon_defeated` | `WorldState.java` | Dragon flag. Plain text - the same file as the Fabric edition. |
| Player attachment `survivaltweaks:kept_items` | `KeepInventory.java` | Items kept on death until respawn. |
| Mod id `survivaltweaks` | everywhere | Namespace of all the above, the tags and the recipes. |

You can set `dragon_defeated=true` by hand (with the world closed).

---

## 5. Quick reference: where to change things

| Want to... | Edit |
|---|---|
| Change which mobs are cave-only | `data/survivaltweaks/tags/entity_type/cave_only*.json` |
| Block another mob completely | `data/survivaltweaks/tags/entity_type/never_spawn.json` |
| Change surface height | `SURFACE_Y` in `SpawnRules.java` |
| Change heal speed in bed | `BedRework.java` |
| Change potion stack size / meat stacking | `ItemTweaks.java` |
| Change food healing | `BetaHunger.java` |
| Change keep-inventory durability loss | `DURABILITY_LOSS` in `KeepInventory.java` |
| Remove/restore an advancement | `data/minecraft/advancement/...` |
| Change bonus loot | `data/survivaltweaks/loot_table/...`, map in `LootTweaks.java` |
| Change cat spawning / breeds | `wild_cats.json`, `cats_spawn_in.json`, `CAT_VARIANT_RULES` + `--fix` |
| Change skull / trident chance | `loot_table/mobs/wither_skeleton_skull.json`, `MobDrops.java` |

---

## 6. Test checklist after every update

- [ ] `./gradlew build` succeeds; the game launches; no `survivaltweaks` or mixin
      errors in `logs/latest.log`; worlds open and new worlds generate.
- [ ] `/locate structure minecraft:village_plains` (and mansion, pillager_outpost,
      swamp_hut) finds nothing.
- [ ] No creepers/spiders on the surface at night; they do appear in caves.
- [ ] No villagers, illagers, witches, phantoms. The four gamerules are `false`.
- [ ] Removed advancements don't show; Hired Help is in the Adventure tab.
- [ ] Kill the dragon: chat says "Evil has been banished from the surface.";
      `world_state.properties` says `dragon_defeated=true`; no zombies/skeletons
      spawn on the surface afterwards; a Wither can still be built there.
- [ ] Totem in inventory (not hand) saves you; totem saves you from the void and
      teleports you home; totem in hand still gives Postmortal.
- [ ] Sleeping heals and the night doesn't skip.
- [ ] Netherite gear and elytra are Unbreakable; Unbreaking can't be put on them
      (table, anvil, creative anvil). Potions stack to 16; meat doesn't stack.
- [ ] Enchanting/fletching tables can't be crafted; totem recipe works.
- [ ] Rotten flesh smokes into leather, goes into a composter, gives Slowness + Nausea.
- [ ] No hunger bar; armor on the right (left while riding a horse); air bubbles
      above it; odd armor values show a half icon filled on its RIGHT side.
- [ ] Infinite sprint; no natural regeneration; eating gives Regeneration II.
- [ ] Anvil repair/rename costs 0 levels and never gets more expensive.
- [ ] Poison on zombies/spiders works; effects stick on the Wither and the Dragon;
      a tipped arrow affects the dragon.
- [ ] `/loot give @s loot minecraft:chests/end_city_treasure` (repeat): membranes,
      looting/mending books. Nether fortress: fire books.
- [ ] Wither skeleton skulls ~70%; drowned tridents ~70%.
- [ ] Die: tools/weapons/armor in hotbar, armor slots and off-hand come back 20%
      more worn; everything else drops. Quitting before Respawn keeps them too.
- [ ] Wild cats in plains/forests/savanna/taiga/swamp with the right breeds.
