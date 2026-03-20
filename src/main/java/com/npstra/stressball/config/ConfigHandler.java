package com.npstra.stressball.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.npstra.stressball.StressBall;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConfigHandler {
    private static final Logger LOGGER = LogManager.getLogger(StressBall.MODID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Set<String> itemBlacklist = new HashSet<>();
    private static Set<String> entityBlacklist = new HashSet<>();
    private static Set<String> rightClickItems = new HashSet<>();
    private static Set<String> autoMineItems = new HashSet<>();

    public static void load(File configDir) {
        File configFile = new File(configDir, "stressball.json");
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }
        try (FileReader reader = new FileReader(configFile)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                if (data.itemBlacklist != null) {
                    itemBlacklist = new HashSet<>(Arrays.asList(data.itemBlacklist));
                }
                if (data.entityBlacklist != null) {
                    entityBlacklist = new HashSet<>(Arrays.asList(data.entityBlacklist));
                }
                if (data.rightClickItems != null) {
                    rightClickItems = new HashSet<>(Arrays.asList(data.rightClickItems));
                }
                if (data.autoMineItems != null) {
                    autoMineItems = new HashSet<>(Arrays.asList(data.autoMineItems));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config file", e);
        }
    }

    private static void createDefaultConfig(File configFile) {
        ConfigData defaultData = new ConfigData();
        defaultData.itemBlacklist = new String[]{
                "cyclicmagic:storage_bag"
        };
        defaultData.entityBlacklist = new String[]{
                "minecraft:item_frame",
                "minecraft:painting",
                "minecraft:armor_stand",
                "minecraft:item"
        };
        defaultData.rightClickItems = new String[]{
                "tconstruct:shuriken",
                "tconevo:tool_sceptre",
                "plustic:laser_gun"
        };
        defaultData.autoMineItems = new String[]{
                "minecraft:diamond_pickaxe",
                "minecraft:diamond_axe",
                "minecraft:diamond_shovel"
        };
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(defaultData, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    public static boolean isItemBlacklisted(String registryName) {
        return itemBlacklist.contains(registryName);
    }

    public static boolean isEntityBlacklisted(String registryName) {
        return entityBlacklist.contains(registryName);
    }

    public static boolean isRightClickItem(String registryName) {
        return rightClickItems.contains(registryName);
    }

    public static boolean isAutoMineItem(String registryName) {
        return autoMineItems.contains(registryName);
    }

    private static class ConfigData {
        String[] itemBlacklist;
        String[] entityBlacklist;
        String[] rightClickItems;
        String[] autoMineItems;
    }
}