package com.guild;
import com.guild.api.GuildAPI;
import com.guild.cache.PlayerNameCache;
import com.guild.commands.GuildChatCommand;
import com.guild.commands.GuildCommand;
import com.guild.commands.GuildGUICommand;
import com.guild.config.BankConfig;
import com.guild.config.CurrencyConfig;
import com.guild.config.ExperienceConfig;
import com.guild.config.FeatureConfig;
import com.guild.config.GuildConfig;
import com.guild.config.GUIConfig;
import com.guild.config.ShopConfig;
import com.guild.currency.GuildCurrency;
import com.guild.database.DatabaseManager;
import com.guild.guild.GuildManager;
import com.guild.listeners.ChatInputListener;
import com.guild.listeners.ExperienceListener;
import com.guild.listeners.InventoryListener;
import com.guild.listeners.PlayerListener;
import com.guild.papi.GuildPlaceholderExpansion;
import com.guild.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GuildPlugin extends JavaPlugin
{
    private static GuildPlugin instance;

    private DatabaseManager databaseManager;

    private GuildManager guildManager;

    private GUIConfig guiConfig;

    private FeatureConfig featureConfig;

    private CurrencyConfig currencyConfig;

    private ShopConfig shopConfig;

    private GuildConfig guildConfig;

    private ExperienceConfig experienceConfig;

    private BankConfig bankConfig;

    private GuildCurrency guildCurrency;

    /** 3.0.9：改为 ConcurrentHashMap，异步任务（自动踢人/保存队列）读取文案时不会与重载并发写冲突 */
    private final Map<String, String> messages = new ConcurrentHashMap<>();

    private String currentLanguage;

    private PlayerNameCache playerNameCache;

    private ChatInputListener chatInputListener;

    private ExperienceListener experienceListener;

    private volatile long lastDailyResetDay;

    private static final double CURRENT_CONFIG_VERSION = 1.3;

    @Override
    public void onEnable()
    {
        instance = this;
        saveDefaultConfig();
        checkConfigVersion();
        loadLanguage();
        saveLanguageFiles();
        // 3.0.9：调整初始化顺序——先构建全部配置，再创建依赖配置的管理器与加载数据库。
        // 旧版先 initialize() 加载公会，Guild 构造时读不到 guild.yml/experience.yml/bank.yml（只能回退默认值），
        // 且极易重现 3.0.7 的"子类字段尚未初始化即被访问"崩溃。
        guiConfig = new GUIConfig(this);
        featureConfig = new FeatureConfig(this);
        currencyConfig = new CurrencyConfig(this);
        shopConfig = new ShopConfig(this);
        guildConfig = new GuildConfig(this);
        experienceConfig = new ExperienceConfig(this);
        bankConfig = new BankConfig(this);
        guildManager = new GuildManager(this);
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        playerNameCache = new PlayerNameCache(this);
        guildCurrency = new GuildCurrency(this);
        registerCommands();
        registerListeners();
        registerPlaceholderAPI();
        startDailyResetTask();
        GuildAPI.init(this);
        printEnableMessage();
    }

    @Override
    public void onDisable()
    {
        // 3.0.9：关闭前同步排空异步保存/删除队列，修复旧版 reload/重启时未落库数据丢失的问题
        if (guildManager != null)
        {
            guildManager.shutdown();
        }
        if (databaseManager != null)
        {
            databaseManager.close();
        }
        printDisableMessage();
    }

    /**
     * 每日重置任务：每分钟检测一次日期变更，
     * 跨天时重置日常经验/贡献与银行每日限额（受 features.yml daily-reset 开关控制）
     */
    private void startDailyResetTask()
    {
        lastDailyResetDay = LocalDate.now().toEpochDay();
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        {
            long today = LocalDate.now().toEpochDay();
            if (today == lastDailyResetDay) return;
            lastDailyResetDay = today;
            if (!featureConfig.isDailyResetEnabled()) return;
            guildManager.clearAllDailyData();
            getLogger().info("Daily data has been reset (experience/contribution/bank limits)");
        }, 1200L, 1200L);
    }

    private void loadLanguage()
    {
        currentLanguage = getConfig().getString("language", "zh_cn");
        messages.clear();
        Map<String, String> loaded = loadLanguageFile(currentLanguage);
        // 3.0.9：用 JAR 内置语言包补齐旧配置文件中缺失的新增键（玩家自定义值优先保留）
        fillMissingMessages(loaded, currentLanguage);
        messages.putAll(loaded);
        // 3.0.9：语言包中的 plugin.version 为旧硬编码值（如 v3.0.4），统一以 plugin.yml 实际版本覆盖
        messages.put("plugin.version", "v" + getDescription().getVersion());
        getLogger().info("Loaded language file: " + currentLanguage + ".yml");
        getLogger().info("Default language set to: " + currentLanguage);
    }

    /**
     * 升级用户 servers/{plugin}/lang/*.yml 是旧版本释放的文件，不包含新增文案键，
     * 直接使用会在聊天栏里输出 "guild.cooldown-join" 这样的原始键名。
     * 本方法仅补齐缺失键，不修改已有值，因此无需玩家手动对比新增配置。
     */
    private void fillMissingMessages(Map<String, String> target, String lang)
    {
        try (InputStream inputStream = getResource("lang/" + lang + ".yml"))
        {
            if (inputStream == null) return;
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, "UTF-8"));
            int added = 0;
            for (String key : bundled.getKeys(true))
            {
                if (target.containsKey(key)) continue;
                String value = bundled.getString(key);
                if (value == null || value.isEmpty()) continue;
                target.put(key, value);
                added++;
            }
            if (added > 0)
            {
                getLogger().info("Filled " + added + " missing message key(s) from built-in " + lang + ".yml");
            }
        }
        catch (Exception e)
        {
            getLogger().warning("Could not fill missing message keys for " + lang + ".yml");
        }
    }

    private Map<String, String> loadLanguageFile(String lang)
    {
        Map<String, String> result = new HashMap<>();
        String path = "lang/" + lang + ".yml";
        File file = new File(getDataFolder(), path);
        try (InputStream resourceStream = getResource(path))
        {
            if (resourceStream == null)
            {
                getLogger().warning("Could not load " + lang + ".yml, using default messages");
                return getDefaultMessages();
            }
            if (!file.getParentFile().exists())
            {
                file.getParentFile().mkdirs();
            }
            if (!file.exists())
            {
                Files.copy(resourceStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int colonIndex = line.indexOf(':');
                    if (colonIndex <= 0) continue;
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    if (!key.isEmpty() && !value.isEmpty())
                    {
                        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))
                        {
                            value = value.substring(1, value.length() - 1);
                        }
                        result.put(key, value);
                    }
                }
            }
        }
        catch (IOException e)
        {
            getLogger().log(Level.WARNING, "Could not load language file: " + lang + ".yml", e);
        }
        return result;
    }

    private Map<String, String> getDefaultMessages()
    {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("plugin.enabling", "Enabling Guild");
        defaults.put("plugin.enabled", "GuildPlugin has been enabled!");
        defaults.put("plugin.disabling", "Disabling Guild");
        defaults.put("plugin.disabled", "GuildPlugin has been disabled!");
        defaults.put("plugin.author", "By GuildPlugin");
        defaults.put("plugin.version", "v" + getDescription().getVersion());
        return defaults;
    }

    private void saveLanguageFiles()
    {
        // 修复旧版 " en_US" 带前导空格导致英文语言文件永远无法释放的问题
        for (String lang : new String[]
        {
            "en_US", "zh_cn", "zh_tw"
        })
        {
            String path = "lang/" + lang + ".yml";
            try (InputStream inputStream = getResource(path))
            {
                if (inputStream == null) continue;
                File file = new File(getDataFolder(), path);
                if (!file.getParentFile().exists())
                {
                    file.getParentFile().mkdirs();
                }
                if (!file.exists())
                {
                    Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            catch (IOException e)
            {
                getLogger().warning("Could not save language file: " + lang + ".yml");
            }
        }
    }

    private void checkConfigVersion()
    {
        double savedVersion = getConfig().getDouble("config-version", 0.0);
        if (savedVersion < CURRENT_CONFIG_VERSION)
        {
            getLogger().warning("配置文件版本较旧 (" + savedVersion + " -> " + CURRENT_CONFIG_VERSION + ")");
            getLogger().warning("建议备份现有配置后删除 config.yml，插件将自动生成新版配置");
            getLogger().warning("未添加的新配置项将使用默认值");
        }
    }

    private void printEnableMessage()
    {
        String version = getDescription().getVersion();
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "========================================");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + " \u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557 \u2588\u2588\u2554\u2550\u2550\u2550\u255d \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557 \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2554\u255d \u2588\u2588\u2551 \u2588\u2588\u2551");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557 \u2588\u2588\u2554\u2550\u2550\u255d \u2588\u2588\u2554\u2550\u2550\u2550\u255d \u2588\u2588\u2551 \u2588\u2588\u2551");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " \u2588\u2588\u2551 \u2588\u2588\u2551 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2551 \u255a\u2588\u2588\u2588\u2588\u2588\u2554\u255d");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " \u255a\u2550\u255d \u255a\u2550\u255d \u255a\u2550\u2550\u2550\u2550\u2550\u255d \u255a\u2550\u255d \u255a\u2550\u2550\u2550\u2550\u2550\u255d");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "========================================");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + " " + ChatColor.BOLD + "GUILD " + messages.getOrDefault("plugin.version", "v" + version));
        Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + " " + messages.getOrDefault("plugin.enabling", "Enabling Guild"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "========================================");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " " + messages.getOrDefault("plugin.enabled", "GuildPlugin has been enabled!"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + " " + messages.getOrDefault("plugin.author", "By GuildPlugin"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + " Loaded " + guildManager.getGuilds().size() + " guilds");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + " Server Type: " + VersionCompat.getServerType());
        Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + " Minecraft Version: " + VersionCompat.getMcVersion());
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "========================================");
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void printDisableMessage()
    {
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "========================================");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " " + messages.getOrDefault("plugin.disabling", "Disabling Guild"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " " + messages.getOrDefault("plugin.disabled", "GuildPlugin has been disabled!"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "========================================");
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void registerCommands()
    {
        getCommand("guild").setExecutor((CommandExecutor) new GuildCommand(this));
        getCommand("guildchat").setExecutor((CommandExecutor) new GuildChatCommand(this));
        getCommand("guildgui").setExecutor((CommandExecutor) new GuildGUICommand(this));
    }

    private void registerListeners()
    {
        Bukkit.getPluginManager().registerEvents((Listener) new PlayerListener(this), this);
        chatInputListener = new ChatInputListener(this);
        InventoryListener inventoryListener = new InventoryListener(this);
        inventoryListener.setChatInputListener(chatInputListener);
        Bukkit.getPluginManager().registerEvents((Listener) inventoryListener, this);
        Bukkit.getPluginManager().registerEvents((Listener) chatInputListener, this);
        // 3.0.9：接入 experience.yml 全部经验事件（旧版仅“击杀玩家”一项生效）
        experienceListener = ExperienceListener.register(this);
    }

    public ExperienceListener getExperienceListener()
    {
        return experienceListener;
    }

    private void registerPlaceholderAPI()
    {
        try
        {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Bukkit.getScheduler().runTaskLater(this, () ->
            {
                boolean success = new GuildPlaceholderExpansion(this).register();
                if (success)
                {
                    getLogger().info("PlaceholderAPI integration enabled");
                }
                else
                {
                    getLogger().warning("PlaceholderAPI integration failed to register, retrying in 3 seconds...");
                    Bukkit.getScheduler().runTaskLater(this, () ->
                    {
                        boolean retry = new GuildPlaceholderExpansion(this).register();
                        if (retry)
                        {
                            getLogger().info("PlaceholderAPI integration enabled on retry");
                        }
                        else
                        {
                            getLogger().warning("PlaceholderAPI integration failed after retry");
                        }
                    }, 60L);
                }
            }, 1L);
        }
        catch (ClassNotFoundException e)
        {
            getLogger().info("PlaceholderAPI not found, placeholder integration disabled");
        }
    }

    public static GuildPlugin getInstance()
    {
        return instance;
    }

    public DatabaseManager getDatabaseManager()
    {
        return databaseManager;
    }

    public GuildManager getGuildManager()
    {
        return guildManager;
    }

    public ChatInputListener getChatInputListener()
    {
        return chatInputListener;
    }

    public GUIConfig getGUIConfig()
    {
        return guiConfig;
    }

    public FeatureConfig getFeatureConfig()
    {
        return featureConfig;
    }

    public CurrencyConfig getCurrencyConfig()
    {
        return currencyConfig;
    }

    public ShopConfig getShopConfig()
    {
        return shopConfig;
    }

    public GuildConfig getGuildConfig()
    {
        return guildConfig;
    }

    public ExperienceConfig getExperienceConfig()
    {
        return experienceConfig;
    }

    public BankConfig getBankConfig()
    {
        return bankConfig;
    }

    public GuildCurrency getGuildCurrency()
    {
        return guildCurrency;
    }

    public PlayerNameCache getPlayerNameCache()
    {
        return playerNameCache;
    }

    public String getMessage(String key)
    {
        String msg = messages.get(key);
        if (msg == null)
        {
            msg = messages.get("messages." + key);
        }
        if (msg == null)
        {
            msg = messages.get("guild." + key);
        }
        if (msg != null)
        {
            msg = ChatColor.translateAlternateColorCodes('&', msg);
        }
        return msg != null ? msg : key;
    }

    public String getMessage(String key, String... replacements)
    {
        String msg = getMessage(key);
        for (int i = 0; i < replacements.length - 1; i += 2)
        {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public void reloadAll()
    {
        reloadConfig();
        getLogger().info("Core config reloaded");
        loadLanguage();
        getLogger().info("Language reloaded");
        if (featureConfig != null)
        {
            featureConfig.reload();
            getLogger().info("Features config reloaded");
        }
        if (currencyConfig != null)
        {
            currencyConfig.reload();
            getLogger().info("Currency config reloaded");
        }
        if (guildConfig != null)
        {
            guildConfig.reload();
            getLogger().info("Guild config reloaded");
        }
        if (experienceConfig != null)
        {
            experienceConfig.reload();
            getLogger().info("Experience config reloaded");
        }
        if (bankConfig != null)
        {
            bankConfig.reload();
            getLogger().info("Bank config reloaded");
            // 3.0.9：bank.yml max-history-records 修改后立即对内存中的公会生效（旧版需要重启）
            if (guildManager != null)
            {
                int maxRecords = bankConfig.getMaxHistoryRecords();
                for (com.guild.guild.Guild guild : guildManager.getGuilds().values())
                {
                    if (guild.getBank() != null) guild.getBank().setMaxRecordsPerPlayer(maxRecords);
                }
            }
        }
        if (guiConfig != null)
        {
            guiConfig.reloadConfig();
            getLogger().info("GUI config reloaded");
        }
        if (shopConfig != null)
        {
            shopConfig.reload();
            getLogger().info("Shop config reloaded");
        }
        guildCurrency = new GuildCurrency(this);
        getLogger().info("Currency system re-initialized");
        if (playerNameCache != null)
        {
            playerNameCache.clearAllCaches();
            getLogger().info("Player name cache cleared");
        }
    }
}
