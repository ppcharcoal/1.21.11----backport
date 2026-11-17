package com.survivaltweaks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Per-world state of the mod, saved in a plain text file that does NOT depend on
 * Minecraft's or NeoForge's save formats:
 *
 *     <world folder>/data/survivaltweaks/world_state.properties
 *
 * Contents look like:
 *
 *     dragon_defeated=true
 *
 * The file is the same one the Fabric edition (Minecraft 26.x) writes, so a world
 * keeps its "dragon defeated" state whichever edition of the mod opens it. You can
 * also open the file in Notepad and change it by hand (while the world is closed).
 *
 * DO NOT rename FILE_DIR, FILE_NAME or KEY_DRAGON_DEFEATED - existing worlds would
 * lose their progress.
 */
public final class WorldState {
    private WorldState() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SurvivalTweaks.MOD_ID);

    private static final String FILE_DIR = SurvivalTweaks.MOD_ID;
    private static final String FILE_NAME = "world_state.properties";
    private static final String KEY_DRAGON_DEFEATED = "dragon_defeated";

    /** Cached value of the file for the currently open world. */
    private static volatile boolean dragonDefeated = false;

    public static void register() {
        // Read the file as early as possible, before mobs start spawning.
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> dragonDefeated = readFile(event.getServer()));
        // Singleplayer: the "server" stops when you leave a world - forget its state.
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> dragonDefeated = false);
    }

    public static boolean isDragonDefeated(MinecraftServer server) {
        return dragonDefeated;
    }

    public static void setDragonDefeated(MinecraftServer server) {
        dragonDefeated = true;
        writeFile(server);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_DIR).resolve(FILE_NAME);
    }

    private static boolean readFile(MinecraftServer server) {
        Path path = file(server);
        if (!Files.exists(path)) {
            return false;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            LOGGER.error("[Survival Tweaks] Could not read {}", path, e);
            return false;
        }
        return Boolean.parseBoolean(props.getProperty(KEY_DRAGON_DEFEATED, "false").trim());
    }

    private static void writeFile(MinecraftServer server) {
        Path path = file(server);
        Properties props = new Properties();
        props.setProperty(KEY_DRAGON_DEFEATED, Boolean.toString(dragonDefeated));
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                props.store(writer, "Survival Tweaks world state. Safe to edit while the world is closed.");
            }
        } catch (IOException e) {
            LOGGER.error("[Survival Tweaks] Could not write {}", path, e);
        }
    }
}
