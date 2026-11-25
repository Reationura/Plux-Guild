package com.guild.listeners;
import com.guild.GuildPlugin;
import com.guild.api.event.GuildLevelUpEvent;
import com.guild.guild.Guild;
import com.guild.utils.VersionCompat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class ExperienceListener implements Listener
{

    private static final int T_MONSTER = 0;
    private static final int T_BLOCK_BREAK = 1;
    private static final int T_BLOCK_PLACE = 2;
    private static final int T_FISHING = 3;
    private static final int T_ENCHANT = 4;
    private static final int T_CRAFT = 5;
    private static final int T_TAME = 6;
    private static final int T_SMELT = 7;
    private static final int T_TRADE = 8;
    private static final int T_BREW = 9;
    private static final int T_BREED = 10;
    private static final int T_ADVANCE = 11;
    private static final int T_PLAYER_KILL = 12;
    private static final int TYPE_COUNT = 13;

    private static final long I_MONSTER = 250L;
    private static final long I_BLOCK = 1000L;
    private static final long I_FISHING = 1000L;
    private static final long I_ENCHANT = 1000L;
    private static final long I_CRAFT = 500L;
    private static final long I_TAME = 1000L;
    private static final long I_SMELT = 500L;
    private static final long I_TRADE = 10000L;
    private static final long I_BREW = 5000L;
    private static final long I_BREED = 1000L;
    private static final long I_ADVANCE = 0L;
    private static final long I_PLAYER_KILL = 3000L;

    private final Map<UUID, long[]> lastAwardAt = new ConcurrentHashMap<>();

    private volatile Set<String> noExpBlocks = Collections.emptySet();
    private volatile Set<String> noExpMobs = Collections.emptySet();
    private volatile long lastConfigRefresh = 0L;
    private static final long CONFIG_REFRESH_INTERVAL = 30000L;

    private static final long AWARD_STALE_TIME = 600000L;

    private static final Set<String> BREW_INGREDIENTS = new HashSet<>(Arrays.asList(
        "NETHER_WART", "NETHER_WARTS", "GLOWSTONE_DUST", "REDSTONE", "SUGAR", "SPIDER_EYE",
        "GUNPOWDER", "MELON", "GLISTERING_MELON", "CARROT", "GOLDEN_CARROT", "FERMENTED_SPIDER_EYE",
        "BLISTERING_MELON_SLICE", "MAGMA_CREAM", "DRAGON_BREATH", "TURTLE_HELMET", "PHANTOM_MEMBRANE"));

    private final GuildPlugin plugin;

    public ExperienceListener(GuildPlugin plugin)
    {
        this.plugin = plugin;
    }

    public static ExperienceListener register(GuildPlugin plugin)
    {
        ExperienceListener listener = new ExperienceListener(plugin);
        Bukkit.getPluginManager().registerEvents((Listener) listener, plugin);
        if (classPresent("org.bukkit.event.entity.EntityBreedEvent")
            && classPresent("org.bukkit.event.player.PlayerAdvancementDoneEvent"))
        {
            Bukkit.getPluginManager().registerEvents((Listener) listener.new Modern(), plugin);
            plugin.getLogger().info("Modern experience events enabled (breeding / advancement)");
        }
        listener.startStaleCleanup();
        return listener;
    }

    private static boolean classPresent(String name)
    {
        try
        {
            Class.forName(name, false, ExperienceListener.class.getClassLoader());
            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event)
    {
        Entity dead = event.getEntity();
        refreshConfigCache();
        if (dead instanceof Player)
        {
            Player victim = (Player) dead;
            Player killer = victim.getKiller();

            if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
            award(killer, T_PLAYER_KILL, I_PLAYER_KILL, () -> plugin.getExperienceConfig().getPlayerKillExp());
            return;
        }
        Player killer = safeGetKiller(dead);
        if (killer == null) return;
        if (noExpMobs.contains(dead.getType().name())) return;
        award(killer, T_MONSTER, I_MONSTER, () -> plugin.getExperienceConfig().getMonsterKillExp());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        refreshConfigCache();
        String blockName = event.getBlock().getType().name();
        if (noExpBlocks.contains(blockName)) return;
        award(event.getPlayer(), T_BLOCK_BREAK, I_BLOCK, () -> plugin.getExperienceConfig().getBlockBreakExp());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event)
    {
        award(event.getPlayer(), T_BLOCK_PLACE, I_BLOCK, () -> plugin.getExperienceConfig().getBlockPlaceExp());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event)
    {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        award(event.getPlayer(), T_FISHING, I_FISHING, () -> plugin.getExperienceConfig().getFishingExp());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event)
    {
        award(event.getEnchanter(), T_ENCHANT, I_ENCHANT, () -> plugin.getExperienceConfig().getEnchantingExp());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event)
    {
        if (!(event.getEntity() instanceof Tameable)) return;
        Object owner = ((Tameable) event.getEntity()).getOwner();
        if (!(owner instanceof Player)) return;
        award((Player) owner, T_TAME, I_TAME, () -> plugin.getExperienceConfig().getTamingExp());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event)
    {
        award(event.getPlayer(), T_SMELT, I_SMELT, () -> plugin.getExperienceConfig().getSmeltingExp());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event)
    {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getInventory();
        if (top == null) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;
        String actionName = event.getAction() == null ? "" : event.getAction().name();
        if (actionName.equals("NOTHING")) return;
        Player player = (Player) event.getWhoClicked();
        InventoryType type = top.getType();
        String typeName = type == null ? "" : type.name();

        if ((typeName.contains("WORKBENCH") || typeName.contains("CRAFTING")) && rawSlot == 0)
        {
            if (top.getItem(0) != null)
            {
                award(player, T_CRAFT, I_CRAFT, () -> plugin.getExperienceConfig().getCraftingExp());
            }
            return;
        }
        if (isMerchantWindow(top))
        {

            if (rawSlot == 2)
            {
                award(player, T_TRADE, I_TRADE, () -> plugin.getExperienceConfig().getTradingExp());
            }
            return;
        }
        if (isBrewingWindow(top, typeName))
        {

            if (!isPlacingAction(actionName)) return;
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == null) return;
            if (!BREW_INGREDIENTS.contains(cursor.getType().name())) return;
            award(player, T_BREW, I_BREW, () -> plugin.getExperienceConfig().getBrewingExp());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildLevelUp(GuildLevelUpEvent event)
    {

        if (!Bukkit.isPrimaryThread())
        {
            Bukkit.getScheduler().runTask(plugin, () -> broadcastLevelUp(event));
            return;
        }
        broadcastLevelUp(event);
    }

    private void broadcastLevelUp(GuildLevelUpEvent event)
    {
        Guild guild = plugin.getGuildManager().getGuild(event.getGuildName());
        if (guild == null) return;
        String message = plugin.getMessage("guild.level-up") .replace("%level%", String.valueOf(event.getNewLevel()))
            .replace("%name%", event.getGuildName());
        for (UUID memberUuid : guild.getMembers().keySet())
        {
            Player online = Bukkit.getPlayer(memberUuid);
            if (online == null || !online.isOnline()) continue;
            online.sendMessage(message);
            VersionCompat.sendTitle(online,
                ChatColor.YELLOW + "公会升级！",
                ChatColor.GRAY + event.getGuildName() + " 达到 " + ChatColor.GREEN + "Lv." + event.getNewLevel(),
                10, 40, 10);
        }
    }

    private class Modern implements Listener
    {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreed(org.bukkit.event.entity.EntityBreedEvent event)
        {
            Object breeder;
            try
            {
                breeder = event.getBreeder();
            }
            catch (Throwable ignored)
            {
                return;
            }
            if (!(breeder instanceof Player)) return;
            award((Player) breeder, T_BREED, I_BREED, () -> plugin.getExperienceConfig().getBreedingExp());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event)
        {
            award(event.getPlayer(), T_ADVANCE, I_ADVANCE, () -> plugin.getExperienceConfig().getAdvancementExp());
        }
    }

    private void award(Player player, int type, long intervalMs, LongSupplier expSupplier)
    {
        if (player == null) return;
        if (!plugin.getFeatureConfig().isExperienceEnabled()) return;
        UUID uuid = player.getUniqueId();

        if (!plugin.getGuildManager().isInGuild(uuid)) return;
        long exp = expSupplier.getAsLong();
        if (exp <= 0L) return;
        long now = System.currentTimeMillis();
        long[] stamps = lastAwardAt.get(uuid);
        if (stamps == null)
        {
            stamps = lastAwardAt.computeIfAbsent(uuid, k -> new long[TYPE_COUNT]);
        }
        if (intervalMs > 0L && now - stamps[type] < intervalMs) return;
        stamps[type] = now;
        plugin.getGuildManager().addExperience(uuid, exp);
    }

    private Player safeGetKiller(Entity entity)
    {
        try
        {
            Method method = VersionCompat.getCachedMethod(entity.getClass(), "getKiller");
            if (method == null) return null;
            Object result = VersionCompat.invokeCached(method, entity);
            return result instanceof Player ? (Player) result : null;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static boolean isPlacingAction(String actionName)
    {
        return actionName.startsWith("PLACE") || actionName.startsWith("HOTBAR_")
            || actionName.equals("SWAP_WITH_CURSOR");
    }

    private boolean isMerchantWindow(Inventory inventory)
    {
        InventoryType type = inventory.getType();
        if (type != null && type.name().contains("MERCHANT")) return true;
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Villager) return true;
        if (holder instanceof Entity)
        {
            return ((Entity) holder).getType().name().contains("TRADER");
        }
        return false;
    }

    private boolean isBrewingWindow(Inventory inventory, String typeName)
    {
        if (!typeName.contains("BREWING")) return false;
        int size = inventory.getSize();

        return size == 5 || size == 7;
    }

    private void refreshConfigCache()
    {
        long now = System.currentTimeMillis();
        if (now - lastConfigRefresh < CONFIG_REFRESH_INTERVAL) return;
        lastConfigRefresh = now;
        noExpBlocks = toUpperSet(plugin.getExperienceConfig().getNoExpBlocks());
        noExpMobs = toUpperSet(plugin.getExperienceConfig().getNoExpMobs());
    }

    private static Set<String> toUpperSet(List<String> list)
    {
        if (list == null || list.isEmpty()) return Collections.emptySet();
        Set<String> set = new HashSet<>(list.size() * 2);

        List<String> snapshot = new ArrayList<>(list);
        for (String value : snapshot)
        {
            if (value != null) set.add(value.trim().toUpperCase());
        }
        return set;
    }

    private void startStaleCleanup()
    {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
        {
            long now = System.currentTimeMillis();
            lastAwardAt.entrySet().removeIf(entry ->
            {
                long[] stamps = entry.getValue();
                long newest = 0L;
                for (long stamp : stamps)
                {
                    if (stamp > newest) newest = stamp;
                }
                return now - newest > AWARD_STALE_TIME;
            });
        }, 6000L, 6000L);
    }
}
