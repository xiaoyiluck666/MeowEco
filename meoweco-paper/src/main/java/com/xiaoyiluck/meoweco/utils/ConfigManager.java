package com.xiaoyiluck.meoweco.utils;

import com.xiaoyiluck.meoweco.MeowEco;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public class ConfigManager {

    private final MeowEco plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String messagesResourceName;

    public ConfigManager(MeowEco plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        reloadMessages();
    }

    public void saveDefaultConfig() {
        plugin.saveDefaultConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            
            int currentVersion = config.getInt("config-version", -1);
            int newVersion = defConfig.getInt("config-version", -1);
            
            boolean updated = false;
            if (newVersion != currentVersion) {
                config.set("config-version", newVersion);
                plugin.getLogger().info("Config version updated from " + currentVersion + " to " + newVersion);
                updated = true;
            }

            // 自动补全缺失配置项，不覆盖用户已有的自定义设置
            for (String key : defConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defConfig.get(key));
                    updated = true;
                } else if (key.endsWith("-usage") || key.contains("header") || key.contains("footer")) {
                    // 对于提示类或模板类配置，如果版本变化则强制更新（可选）
                    if (newVersion != currentVersion) {
                        config.set(key, defConfig.get(key));
                        updated = true;
                    }
                }
            }
            
            if (updated) {
                try {
                    config.save(configFile);
                    plugin.reloadConfig();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", e);
                }
            }
        }
    }

    public void reloadMessages() {
        String language = plugin.getConfig().getString("messages.language", "en_US");
        String resourceName = resolveMessagesResourceName(language);

        if (messagesFile == null || messagesResourceName == null || !messagesResourceName.equalsIgnoreCase(resourceName)) {
            messagesResourceName = resourceName;
            File langDir = new File(plugin.getDataFolder(), "lang");
            if (!langDir.exists()) {
                langDir.mkdirs();
            }
            messagesFile = new File(langDir, resourceName);
        }

        if (!messagesFile.exists()) {
            File oldRootFile = new File(plugin.getDataFolder(), resourceName);
            if (oldRootFile.exists()) {
                try {
                    Files.move(oldRootFile.toPath(), messagesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    try {
                        Files.copy(oldRootFile.toPath(), messagesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Could not migrate old language file to lang/", e);
                    }
                }
            } else {
                plugin.saveResource("lang/" + resourceName, false);
            }
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defConfigStream = plugin.getResource("lang/" + resourceName);
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            
            boolean updated = false;
            int currentVersion = plugin.getConfig().getInt("config-version", -1);
            
            for (String key : defConfig.getKeys(true)) {
                // 仅补全缺失的语言项，不覆盖用户已翻译或自定义的内容
                if (!messagesConfig.contains(key)) {
                    messagesConfig.set(key, defConfig.get(key));
                    updated = true;
                } else if (key.endsWith("-usage") || key.contains("header") || key.contains("template")) {
                    // 对于关键指令提示，如果主配置版本升级，则尝试同步（可选）
                    if (currentVersion > 5) { // 假设版本 5 是一个分水岭
                         // 这里可以根据需要决定是否强制更新
                    }
                }
            }
            
            if (updated) {
                try {
                    messagesConfig.save(messagesFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save messages file in lang/", e);
                }
            }
        }
    }

    private String resolveMessagesResourceName(String language) {
        if (language == null || language.isBlank()) {
            return "en_US.yml";
        }
        String normalized = language.trim();
        String candidate = normalized + ".yml";
        if (plugin.getResource("lang/" + candidate) != null) {
            return candidate;
        }
        return "en_US.yml";
    }

    public Component parseColor(String text) {
        if (text == null) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(text.replace('&', '§'));
    }

    public Component getComponent(String key) {
        String prefix = messagesConfig.getString("prefix", "");
        if (prefix == null) prefix = "";

        if ("prefix".equalsIgnoreCase(key)) {
            return parseColor(prefix);
        }

        String msg = messagesConfig.getString(key);
        if (msg == null) return Component.text("Missing message: " + key);
        
        return parseColor(prefix + msg);
    }

    @Deprecated
    public String getMessage(String key) {
        if ("prefix".equalsIgnoreCase(key)) {
            String prefix = messagesConfig.getString("prefix", "");
            return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix == null ? "" : prefix).content();
        }

        String prefix = messagesConfig.getString("prefix", "");
        if (prefix == null) prefix = "";

        String msg = messagesConfig.getString(key);
        if (msg == null) return "Missing message: " + key;
        
        // Return legacy string for backward compatibility if needed
        // But we are refactoring.
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }
}
