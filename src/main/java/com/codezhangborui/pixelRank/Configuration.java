package com.codezhangborui.pixelRank;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Configuration {

    private static FileConfiguration config;
    private static File configFile;
    private static JavaPlugin plugin;
    private static Logger logger;

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getLogger();
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public static void reload() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public static void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, e);
        }
    }

    public static void setDefault(String path, Object value, String... comment) {
        if (!config.contains(path)) {
            config.set(path, value);
            if(comment.length > 0) {
                config.setComments(path, Arrays.asList(comment));
            }
            save();
        }
    }

    public static String getString(String path) {
        return config.getString(path);
    }

    public static int getInt(String path) {
        return config.getInt(path);
    }

    public static boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    /**
     * キーが無いときに false ではなく {@code fallback} を返す版。
     *
     * <p>あとから追加したキーに使う。{@link #getBoolean(String)} は未設定を false として返すので、
     * 既に配ってある config には無いキーで判定すると<b>その機能が黙って全部 OFF になる</b>。
     * （{@code scoreboard.*} を足したときに実際に踏んだ。{@link #setDefault} が書き込む前に
     * 読まれる経路が 1 つでもあると再現する。）</p>
     */
    public static boolean getBoolean(String path, boolean fallback) {
        return config.contains(path) ? config.getBoolean(path) : fallback;
    }

    public static double getDouble(String path) {
        return config.getDouble(path);
    }

    public static void set(String path, Object value) {
        config.set(path, value);
        save();
    }
}