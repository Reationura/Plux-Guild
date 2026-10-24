package com.guild.config;
import com.guild.GuildPlugin;
import com.guild.currency.GuildCurrency;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

public class ShopConfig extends SimpleFileConfig
{
    private boolean enabled;
    private String title;
    private int rows;
    private boolean showLockedItems;
    private String lockedDisplayName;
    private List<String> lockedLore;
    // 不能在字段初始化器中创建：父类 SimpleFileConfig 构造函数会先于字段初始化器
    // 调用 onLoad()，此时 items 仍为 null，直接 clear 会抛 NullPointerException
    private Map<String, ShopItemEntry> items;

    public ShopConfig(GuildPlugin plugin)
    {
        super(plugin, "shop.yml");
    }

    @Override
    protected void onLoad()
    {
        enabled = config.getBoolean("settings.enabled", true);
        title = config.getString("settings.title", "&6&l公会商店");
        rows = Math.max(1, Math.min(6, config.getInt("settings.rows", 6)));
        showLockedItems = config.getBoolean("settings.show-locked-items", true);
        lockedDisplayName = config.getString("settings.locked-display-name", "&7[锁定]");
        lockedLore = config.getStringList("settings.locked-lore");
        if (lockedLore.isEmpty())
        {
            lockedLore = Arrays.asList("&c需要公会等级: %level%", "&7公会达到 Lv.%level% 后解锁");
        }
        items = new LinkedHashMap<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null)
        {
            for (String key : itemsSection.getKeys(false))
            {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) continue;
                ShopItemEntry entry = parseItem(key, itemSection);
                if (entry != null)
                {
                    items.put(key, entry);
                }
            }
        }
        plugin.getLogger().info("Loaded " + items.size() + " shop items");
    }

    private ShopItemEntry parseItem(String key, ConfigurationSection section)
    {
        try
        {
            int slot = section.getInt("slot", -1);
            String materialName = section.getString("material", "STONE");
            Material material;
            try
            {
                material = Material.valueOf(materialName.toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                material = Material.STONE;
                plugin.getLogger().warning("Shop item " + key + " has invalid material: " + materialName + ", using STONE");
            }
            String name = section.getString("name", "&f" + key);
            List<String> lore = section.getStringList("lore");
            int requiredLevel = section.getInt("required-level", 1);
            long price = section.getLong("price", 0);
            String currencyTypeStr = section.getString("currency-type", "GUILD_COIN");
            GuildCurrency.CurrencyType currencyType;
            try
            {
                currencyType = GuildCurrency.CurrencyType.valueOf(currencyTypeStr.toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                currencyType = GuildCurrency.CurrencyType.GUILD_COIN;
            }
            String actionStr = section.getString("action", "COMMAND");
            ShopAction action;
            try
            {
                action = ShopAction.valueOf(actionStr.toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                action = ShopAction.COMMAND;
            }
            String actionValue = section.getString("action-value", "");
            String buyMessage = section.getString("buy-message", "");
            String permission = section.getString("permission", "");
            return new ShopItemEntry(key, slot, material, name, lore, requiredLevel, price,
                currencyType, action, actionValue, buyMessage, permission);
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("Failed to parse shop item " + key + ": " + e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getTitle() { return title; }
    public int getRows() { return rows; }
    public boolean isShowLockedItems() { return showLockedItems; }
    public String getLockedDisplayName() { return lockedDisplayName; }
    public List<String> getLockedLore() { return lockedLore; }
    public Collection<ShopItemEntry> getItems() { return items.values(); }

    public ShopItemEntry findItemByName(String displayName)
    {
        String stripped = org.bukkit.ChatColor.stripColor(displayName);
        for (ShopItemEntry item : items.values())
        {
            String itemName = org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', item.getName()));
            if (itemName.equals(stripped))
            {
                return item;
            }
        }
        return null;
    }

    public ShopItemEntry findItemByKey(String key)
    {
        return items.get(key);
    }

    public static class ShopItemEntry
    {
        private final String key;
        private final int slot;
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final int requiredLevel;
        private final long price;
        private final GuildCurrency.CurrencyType currencyType;
        private final ShopAction action;
        private final String actionValue;
        private final String buyMessage;
        private final String permission;

        public ShopItemEntry(String key, int slot, Material material, String name, List<String> lore,
            int requiredLevel, long price, GuildCurrency.CurrencyType currencyType,
            ShopAction action, String actionValue, String buyMessage, String permission)
        {
            this.key = key;
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.requiredLevel = requiredLevel;
            this.price = price;
            this.currencyType = currencyType;
            this.action = action;
            this.actionValue = actionValue;
            this.buyMessage = buyMessage;
            this.permission = permission;
        }

        public String getKey() { return key; }
        public int getSlot() { return slot; }
        public Material getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public int getRequiredLevel() { return requiredLevel; }
        public long getPrice() { return price; }
        public GuildCurrency.CurrencyType getCurrencyType() { return currencyType; }
        public ShopAction getAction() { return action; }
        public String getActionValue() { return actionValue; }
        public String getBuyMessage() { return buyMessage; }
        public String getPermission() { return permission; }
    }

    public enum ShopAction
    {
        COMMAND,
        CONSOLE,
        GIVE_ITEM,
        GUILD_EXP,
        GUILD_COIN
    }
}
