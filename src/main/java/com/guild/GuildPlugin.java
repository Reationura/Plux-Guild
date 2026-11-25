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
import com.guild.utils.UpdateChecker;
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

    private final Map<String, String> messages = new ConcurrentHashMap<>();

    private String currentLanguage;

    private PlayerNameCache playerNameCache;

    private ChatInputListener chatInputListener;

    private ExperienceListener experienceListener;

    private volatile long lastDailyResetDay;

    private UpdateChecker updateChecker;

    private static final double CURRENT_CONFIG_VERSION = 1.4;

    @Override
    public void onEnable()
    {
        instance = this;
        saveDefaultConfig();
        com.guild.config.ConfigMigrator.migrate(this);
        loadLanguage();
        saveLanguageFiles();
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
        updateChecker = new UpdateChecker(this);
        updateChecker.start();
        printEnableMessage();
    }

    @Override
    public void onDisable()
    {
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
        fillMissingMessages(loaded, currentLanguage);
        messages.putAll(loaded);
        messages.put("plugin.version", "v" + getDescription().getVersion());
        getLogger().info("Loaded language file: " + currentLanguage + ".yml");
        getLogger().info("Default language set to: " + currentLanguage);
    }

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

    public UpdateChecker getUpdateChecker()
    {
        return updateChecker;
    }

    public String getPrefix()
    {
        return getMessage("message.prefix");
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
