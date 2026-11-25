package com.guild.api;
import com.guild.GuildPlugin;
import com.guild.api.event.*;
import com.guild.guild.*;
import com.guild.gui.GuildGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Deprecated
public final class GuildAPI implements GuildAPIProvider
{
    private static volatile GuildAPI instance;

    private final GuildPlugin plugin;

    private GuildAPI(GuildPlugin plugin)
    {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public static void init(GuildPlugin plugin)
    {
        instance = new GuildAPI(plugin);
        com.guild.api.v2.GuildApiV2Provider.init(plugin);
    }

    public static GuildAPI getInstance()
    {
        return instance;
    }

    public static boolean isApiReady()
    {
        return instance != null;
    }

    public static GuildAPI requireInstance() throws IllegalStateException
    {
        if (!isApiReady())
        {
            throw new IllegalStateException("GuildAPI 尚未初始化！请确保在 onEnable 之后调用，可通过 GuildAPI.isApiReady() 检查。");
        }
        return instance;
    }

    @Override
    public boolean isReady()
    {
        return instance != null;
    }

    @Override
    public GuildPlugin getPlugin()
    {
        return plugin;
    }

    @Override
    public GuildData getGuild(String name)
    {
        Guild guild = plugin.getGuildManager().getGuild(name);
        return guild != null ? new GuildData(guild) : null;
    }

    @Override
    public GuildData getGuildByTag(String tag)
    {
        if (tag == null || tag.isEmpty()) return null;
        for (Guild guild : plugin.getGuildManager().getGuilds().values())
        {
            String guildTag = guild.getTag();
            if (guildTag != null && guildTag.equalsIgnoreCase(tag))
            {
                return new GuildData(guild);
            }
        }
        return null;
    }

    @Override
    public List<GuildData> searchGuilds(String keyword)
    {
        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();
        String kw = keyword.trim().toLowerCase();
        List<GuildData> matches = new ArrayList<>();
        for (Guild guild : plugin.getGuildManager().getGuilds().values())
        {
            String name = guild.getName().toLowerCase();
            String tag = guild.getTag() != null ? guild.getTag().toLowerCase() : "";
            if (name.contains(kw) || tag.contains(kw))
            {
                matches.add(new GuildData(guild));
            }
        }
        matches.sort((a, b) ->
        {
            boolean aExact = a.getName().equalsIgnoreCase(kw);
            boolean bExact = b.getName().equalsIgnoreCase(kw);
            if (aExact != bExact) return aExact ? -1 : 1;
            return Integer.compare(b.getLevel(), a.getLevel());
        });
        return matches;
    }

    @Override
    public GuildData getPlayerGuild(UUID playerUuid)
    {
        Guild guild = plugin.getGuildManager().getPlayerGuild(playerUuid);
        return guild != null ? new GuildData(guild) : null;
    }

    public String getPlayerGuildName(UUID playerUuid)
    {
        GuildData data = getPlayerGuild(playerUuid);
        return data != null ? data.getName() : null;
    }

    @Override
    public boolean isInGuild(UUID playerUuid)
    {
        return plugin.getGuildManager().isInGuild(playerUuid);
    }

    @Override
    public Map<String, GuildData> getAllGuilds()
    {
        Map<String, GuildData> result = new LinkedHashMap<>();
        plugin.getGuildManager().getGuilds().forEach((name, guild) -> result.put(name, new GuildData(guild)));
        return Collections.unmodifiableMap(result);
    }

    public List<GuildData> getAllGuildList()
    {
        List<GuildData> result = new ArrayList<>();
        for (Guild guild : plugin.getGuildManager().getGuilds().values())
        {
            result.add(new GuildData(guild));
        }
        return result;
    }

    @Override
    public List<GuildData> getTopGuilds(int limit)
    {
        if (limit <= 0) return Collections.emptyList();
        return plugin.getGuildManager().getGuilds().values().stream()
            .map(GuildData::new)
            .sorted(Comparator.comparingInt(GuildData::getLevel).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public String getMemberRole(UUID playerUuid)
    {
        Guild guild = plugin.getGuildManager().getPlayerGuild(playerUuid);
        if (guild == null) return null;
        GuildMember member = guild.getMember(playerUuid);
        return member != null ? member.getRole().name() : null;
    }

    @Override
    public List<UUID> getOnlineMembers(String guildName)
    {
        Guild guild = plugin.getGuildManager().getGuild(guildName);
        if (guild == null) return Collections.emptyList();
        List<UUID> online = new ArrayList<>();
        for (UUID uuid : guild.getMembers().keySet())
        {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) online.add(uuid);
        }
        return online;
    }

    @Override
    public int getOnlineCount(String guildName)
    {
        return getOnlineMembers(guildName).size();
    }

    public GuildData createGuild(String name, Player owner)
    {
        Guild guild = plugin.getGuildManager().createGuild(name, owner);
        return guild != null ? new GuildData(guild) : null;
    }

    public GuildData createGuild(String name, String tag, Player owner)
    {
        Guild guild = plugin.getGuildManager().createGuild(name, owner);
        if (guild != null && tag != null && !tag.isEmpty()
            && (guild.getTag() == null || guild.getTag().isEmpty()))
        {
            guild.setTag(tag);
            plugin.getGuildManager().scheduleSavePublic(guild);
        }
        return guild != null ? new GuildData(guild) : null;
    }

    public boolean disbandGuild(String name, OfflinePlayer disbander)
    {
        Guild guild = name != null ? plugin.getGuildManager().getGuild(name) : null;
        if (guild == null) return false;
        UUID operatorUuid = disbander != null ? disbander.getUniqueId() : guild.getOwner();
        return plugin.getGuildManager().disbandGuild(guild.getName(), operatorUuid);
    }

    public boolean joinGuild(Player player, String guildName)
    {
        return plugin.getGuildManager().joinGuild(guildName, player);
    }

    public boolean leaveGuild(Player player)
    {
        return plugin.getGuildManager().leaveGuild(player.getUniqueId());
    }

    public boolean kickMember(String guildName, UUID targetUuid, OfflinePlayer kicker)
    {
        return plugin.getGuildManager().kickMember(guildName, targetUuid, kicker != null ? kicker.getUniqueId() : null);
    }

    public boolean promoteMember(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        return plugin.getGuildManager().promoteMember(guildName, targetUuid, operatorUuid);
    }

    public boolean demoteMember(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        return plugin.getGuildManager().demoteMember(guildName, targetUuid, operatorUuid);
    }

    public boolean transferOwnership(String guildName, UUID newOwner, UUID currentOwner)
    {
        return plugin.getGuildManager().transferOwnership(guildName, newOwner, currentOwner);
    }

    public boolean sendInvite(String guildName, UUID inviter, UUID target, String targetName)
    {
        return plugin.getGuildManager().sendInvite(guildName, inviter, target, targetName);
    }

    public boolean acceptInvite(UUID playerUuid)
    {
        return plugin.getGuildManager().acceptInvite(playerUuid);
    }

    public boolean declineInvite(UUID playerUuid)
    {
        return plugin.getGuildManager().declineInvite(playerUuid);
    }

    public GuildInviteInfo getInvite(UUID playerUuid)
    {
        GuildManager.GuildInvite invite = plugin.getGuildManager().getInvite(playerUuid);
        if (invite == null) return null;
        return new GuildInviteInfo(
            invite.getGuildName(),
            invite.getInviterUuid(),
            Bukkit.getOfflinePlayer(invite.getInviterUuid()).getName(),
            invite.getTargetUuid(),
            invite.getTargetName(),
            invite.getInviteTime());
    }

    @Override
    public void addExperience(UUID playerUuid, long amount)
    {
        plugin.getGuildManager().addExperience(playerUuid, amount);
    }

    public boolean upgradeGuild(String guildName, UUID playerUuid)
    {
        return plugin.getGuildManager().upgradeGuild(guildName, playerUuid);
    }

    @Override
    public boolean depositToBank(String guildName, UUID playerUuid, long amount)
    {
        return plugin.getGuildManager().depositToBank(guildName, playerUuid, amount);
    }

    @Override
    public boolean withdrawFromBank(String guildName, UUID playerUuid, long amount)
    {
        return plugin.getGuildManager().withdrawFromBank(guildName, playerUuid, amount);
    }

    public long getBankBalance(String guildName)
    {
        return plugin.getGuildManager().getGuildBalance(guildName);
    }

    private <T> CompletableFuture<T> supplyOnMainThread(java.util.function.Supplier<T> supplier)
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () ->
        {
            try
            {
                future.complete(supplier.get());
            }
            catch (Exception e)
            {
                future.completeExceptionally(e);
            }
        };
        try
        {
            if (Bukkit.isPrimaryThread())
            {
                task.run();
            }
            else
            {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
        catch (Exception e)
        {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<GuildData> createGuildAsync(String name, Player owner)
    {
        return supplyOnMainThread(() -> createGuild(name, owner));
    }

    @Override
    public CompletableFuture<GuildData> createGuildAsync(String name, String tag, Player owner)
    {
        return supplyOnMainThread(() -> createGuild(name, tag, owner));
    }

    @Override
    public CompletableFuture<Boolean> disbandGuildAsync(String name)
    {
        return supplyOnMainThread(() -> disbandGuild(name, null));
    }

    @Override
    public CompletableFuture<Boolean> joinGuildAsync(Player player, String guildName)
    {
        return supplyOnMainThread(() -> joinGuild(player, guildName));
    }

    @Override
    public CompletableFuture<Boolean> leaveGuildAsync(Player player)
    {
        return supplyOnMainThread(() -> leaveGuild(player));
    }

    @Override
    public CompletableFuture<Boolean> kickMemberAsync(String guildName, UUID target, UUID kicker)
    {
        return supplyOnMainThread(() -> kickMember(guildName, target, kicker != null ? Bukkit.getOfflinePlayer(kicker) : null));
    }

    @Override
    public CompletableFuture<Boolean> upgradeGuildAsync(String guildName, UUID playerUuid)
    {
        return supplyOnMainThread(() -> upgradeGuild(guildName, playerUuid));
    }

    @Override
    public void openMainGUI(Player player)
    {
        GuildGUI.openGUI(plugin, player);
    }

    public void openBankGUI(Player player, GuildData guild)
    {
        if (player != null && guild != null)
        {
            com.guild.gui.GuildBankGUI.openBankGUI(plugin, player, guild.getInternalGuild());
        }
    }

    public void openShopGUI(Player player, GuildData guild)
    {
        if (player != null && guild != null)
        {
            com.guild.gui.GuildShopGUI.openShopGUI(plugin, player, guild.getInternalGuild());
        }
    }

    public void registerListener(Listener listener)
    {
        if (listener != null)
        {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }

    @Override
    public String getMessage(String key)
    {
        return plugin.getMessage(key);
    }

    public String getMessage(String key, String... replacements)
    {
        return plugin.getMessage(key, replacements);
    }

    public void reloadConfig()
    {
        plugin.reloadAll();
    }

    public int getTotalGuildCount()
    {
        return plugin.getGuildManager().getGuilds().size();
    }

    public int getTotalMemberCount()
    {
        int count = 0;
        for (Guild guild : plugin.getGuildManager().getGuilds().values())
        {
            count += guild.getMembers().size();
        }
        return count;
    }

    public double getAverageLevel()
    {
        Map<String, Guild> guilds = plugin.getGuildManager().getGuilds();
        if (guilds.isEmpty()) return 0.0;
        return guilds.values().stream().mapToInt(Guild::getLevel).average().orElse(0.0);
    }
}
