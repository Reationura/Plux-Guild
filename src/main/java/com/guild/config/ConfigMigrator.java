package com.guild.config;
import com.guild.GuildPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class ConfigMigrator
{
    public static final double TARGET_VERSION = 1.4;

    private static final String[] CONFIG_FILES =
    {
        "config.yml", "features.yml", "currency.yml", "guild.yml",
        "experience.yml", "bank.yml", "gui.yml", "shop.yml"
    };

    private static final String[] LANG_FILES =
    {
        "lang/zh_cn.yml", "lang/zh_tw.yml", "lang/en_US.yml"
    };

    private ConfigMigrator()
    {
    }

    public static void migrate(GuildPlugin plugin)
    {
        double savedVersion = plugin.getConfig().getDouble("config-version", 0.0);
        if (savedVersion >= TARGET_VERSION)
        {
            ensureMissingFiles(plugin);
            return;
        }
        plugin.getLogger().info("检测到旧版配置 (v" + savedVersion + ")，开始自动迁移至 v" + TARGET_VERSION);
        File backupDir = backupAll(plugin, savedVersion);
        int merged = mergeMissingKeys(plugin);
        if (backupDir != null)
        {
            plugin.getLogger().info("旧配置已备份至: " + backupDir.getName());
        }
        plugin.getConfig().set("config-version", TARGET_VERSION);
        plugin.saveConfig();
        plugin.getLogger().info("配置迁移完成，补齐缺失配置项 " + merged + " 个（原有自定义值全部保留）");
    }

    private static void ensureMissingFiles(GuildPlugin plugin)
    {
        for (String name : CONFIG_FILES)
        {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists())
            {
                saveResourceIfPresent(plugin, name);
            }
        }
        for (String name : LANG_FILES)
        {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists())
            {
                saveResourceIfPresent(plugin, name);
            }
        }
    }

    private static File backupAll(GuildPlugin plugin, double savedVersion)
    {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) return null;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backupDir = new File(dataFolder, "backup/config-v" + String.valueOf(savedVersion).replace('.', '_') + "-" + stamp);
        boolean any = false;
        for (String name : CONFIG_FILES)
        {
            File file = new File(dataFolder, name);
            if (file.exists())
            {
                copyFile(file, new File(backupDir, name));
                any = true;
            }
        }
        for (String name : LANG_FILES)
        {
            File file = new File(dataFolder, name);
            if (file.exists())
            {
                copyFile(file, new File(backupDir, name));
                any = true;
            }
        }
        return any ? backupDir : null;
    }

    private static void copyFile(File source, File target)
    {
        try
        {
            target.getParentFile().mkdirs();
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception ignored)
        {
        }
    }

    private static int mergeMissingKeys(GuildPlugin plugin)
    {
        int total = 0;
        for (String name : CONFIG_FILES)
        {
            total += mergeFile(plugin, name);
        }
        for (String name : LANG_FILES)
        {
            total += mergeFile(plugin, name);
        }
        return total;
    }

    private static int mergeFile(GuildPlugin plugin, String name)
    {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists())
        {
            saveResourceIfPresent(plugin, name);
            return 0;
        }
        InputStream resource = plugin.getResource(name);
        if (resource == null) return 0;
        try
        {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            if (current.getKeys(true).isEmpty() && !defaults.getKeys(true).isEmpty())
            {
                plugin.getLogger().warning("  " + name + " 解析失败（可能存在未加引号的值），已用内置默认文件重建");
                current = defaults;
            }
            int added = 0;
            for (String key : defaults.getKeys(true))
            {
                if (current.isSet(key)) continue;
                Object value = defaults.get(key);
                if (value == null) continue;
                current.set(key, value);
                added++;
            }
            if (added > 0)
            {
                current.save(file);
                plugin.getLogger().info("  " + name + " 补齐 " + added + " 个配置项");
            }
            return added;
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("迁移配置失败: " + name + " (" + e.getMessage() + ")");
            return 0;
        }
    }

    private static void saveResourceIfPresent(GuildPlugin plugin, String name)
    {
        if (plugin.getResource(name) == null) return;
        try
        {
            plugin.saveResource(name, false);
        }
        catch (Exception ignored)
        {
        }
    }
}
