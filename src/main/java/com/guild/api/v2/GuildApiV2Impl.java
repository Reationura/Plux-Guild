package com.guild.api.v2;
import com.guild.GuildPlugin;
import com.guild.api.GuildData;
import com.guild.guild.Guild;
import com.guild.gui.GuildBankGUI;
import com.guild.gui.GuildGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class GuildApiV2Impl implements GuildApiV2
{
    private final GuildPlugin plugin;

    public GuildApiV2Impl(GuildPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean isReady()
    {
        return plugin.getGuildManager() != null;
    }

    @Override
    public String getApiVersion()
    {
        return API_VERSION;
    }

    @Override
    public GuildPlugin getPlugin()
    {
        return plugin;
    }

    @Override
    public GuildData getGuild(String name)
    {
        if (name == null) return null;
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
    public List<GuildData> searchGuilds(String keyword, int limit)
    {
        if (keyword == null || keyword.trim().isEmpty() || limit <= 0) return Collections.emptyList();
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
        return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
    }

    @Override
    public GuildData getPlayerGuild(UUID playerUuid)
    {
        if (playerUuid == null) return null;
        Guild guild = plugin.getGuildManager().getPlayerGuild(playerUuid);
        return guild != null ? new GuildData(guild) : null;
    }

    @Override
    public boolean isInGuild(UUID playerUuid)
    {
        return playerUuid != null && plugin.getGuildManager().isInGuild(playerUuid);
    }

    @Override
    public String getMemberRole(UUID playerUuid)
    {
        if (playerUuid == null) return null;
        Guild guild = plugin.getGuildManager().getPlayerGuild(playerUuid);
        if (guild == null) return null;
        com.guild.guild.GuildMember member = guild.getMember(playerUuid);
        return member != null ? member.getRole().name() : null;
    }

    @Override
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
    public List<UUID> getOnlineMembers(String guildName)
    {
        Guild guild = guildName != null ? plugin.getGuildManager().getGuild(guildName) : null;
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

    @Override
    public int getTotalGuildCount()
    {
        return plugin.getGuildManager().getGuilds().size();
    }

    @Override
    public int getTotalMemberCount()
    {
        int count = 0;
        for (Guild guild : plugin.getGuildManager().getGuilds().values())
        {
            count += guild.getMembers().size();
        }
        return count;
    }

    @Override
    public double getAverageLevel()
    {
        java.util.Map<String, Guild> guilds = plugin.getGuildManager().getGuilds();
        if (guilds.isEmpty()) return 0.0;
        long total = 0;
        for (Guild guild : guilds.values())
        {
            total += guild.getLevel();
        }
        return (double) total / guilds.size();
    }

    @Override
    public long getBankBalance(String guildName)
    {
        return plugin.getGuildManager().getGuildBalance(guildName);
    }

    @Override
    public GuildData createGuild(String name, Player owner)
    {
        Guild guild = plugin.getGuildManager().createGuild(name, owner);
        return guild != null ? new GuildData(guild) : null;
    }

    @Override
    public boolean disbandGuild(String name, OfflinePlayer disbander)
    {
        Guild guild = name != null ? plugin.getGuildManager().getGuild(name) : null;
        if (guild == null) return false;
        UUID operatorUuid = disbander != null ? disbander.getUniqueId() : guild.getOwner();
        return plugin.getGuildManager().disbandGuild(guild.getName(), operatorUuid);
    }

    @Override
    public boolean joinGuild(Player player, String guildName)
    {
        if (player == null) return false;
        return plugin.getGuildManager().joinGuild(guildName, player);
    }

    @Override
    public boolean leaveGuild(Player player)
    {
        if (player == null) return false;
        return plugin.getGuildManager().leaveGuild(player.getUniqueId());
    }

    @Override
    public boolean kickMember(String guildName, UUID targetUuid, OfflinePlayer kicker)
    {
        if (targetUuid == null || kicker == null) return false;
        return plugin.getGuildManager().kickMember(guildName, targetUuid, kicker.getUniqueId());
    }

    @Override
    public boolean promoteMember(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        if (targetUuid == null || operatorUuid == null) return false;
        return plugin.getGuildManager().promoteMember(guildName, targetUuid, operatorUuid);
    }

    @Override
    public boolean demoteMember(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        if (targetUuid == null || operatorUuid == null) return false;
        return plugin.getGuildManager().demoteMember(guildName, targetUuid, operatorUuid);
    }

    @Override
    public boolean transferOwnership(String guildName, UUID newOwner, UUID currentOwner)
    {
        if (newOwner == null || currentOwner == null) return false;
        return plugin.getGuildManager().transferOwnership(guildName, newOwner, currentOwner);
    }

    @Override
    public boolean upgradeGuild(String guildName, UUID playerUuid)
    {
        if (playerUuid == null) return false;
        return plugin.getGuildManager().upgradeGuild(guildName, playerUuid);
    }

    @Override
    public boolean sendInvite(String guildName, UUID inviter, UUID target, String targetName)
    {
        if (inviter == null || target == null) return false;
        return plugin.getGuildManager().sendInvite(guildName, inviter, target, targetName);
    }

    @Override
    public boolean acceptInvite(UUID playerUuid)
    {
        return playerUuid != null && plugin.getGuildManager().acceptInvite(playerUuid);
    }

    @Override
    public boolean declineInvite(UUID playerUuid)
    {
        return playerUuid != null && plugin.getGuildManager().declineInvite(playerUuid);
    }

    @Override
    public void addExperience(UUID playerUuid, long amount)
    {
        plugin.getGuildManager().addExperience(playerUuid, amount);
    }

    @Override
    public boolean depositToBank(String guildName, UUID playerUuid, long amount)
    {
        if (playerUuid == null) return false;
        return plugin.getGuildManager().depositToBank(guildName, playerUuid, amount);
    }

    @Override
    public boolean withdrawFromBank(String guildName, UUID playerUuid, long amount)
    {
        if (playerUuid == null) return false;
        return plugin.getGuildManager().withdrawFromBank(guildName, playerUuid, amount);
    }

    private CompletableFuture<Boolean> runOnMainThread(java.util.function.Supplier<Boolean> task)
    {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Runnable wrapped = () -> future.complete(Boolean.TRUE.equals(task.get()));
        try
        {
            if (Bukkit.isPrimaryThread())
            {
                wrapped.run();
            }
            else
            {
                Bukkit.getScheduler().runTask(plugin, wrapped);
            }
        }
        catch (Exception e)
        {
            future.completeExceptionally(e);
        }
        return future;
    }

    private <T> CompletableFuture<T> supplyOnMainThread(java.util.function.Supplier<T> task)
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable wrapped = () ->
        {
            try
            {
                future.complete(task.get());
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
                wrapped.run();
            }
            else
            {
                Bukkit.getScheduler().runTask(plugin, wrapped);
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
    public CompletableFuture<Boolean> disbandGuildAsync(String name, OfflinePlayer disbander)
    {
        return runOnMainThread(() -> disbandGuild(name, disbander));
    }

    @Override
    public CompletableFuture<Boolean> joinGuildAsync(Player player, String guildName)
    {
        return runOnMainThread(() -> joinGuild(player, guildName));
    }

    @Override
    public CompletableFuture<Boolean> leaveGuildAsync(Player player)
    {
        return runOnMainThread(() -> leaveGuild(player));
    }

    @Override
    public CompletableFuture<Boolean> kickMemberAsync(String guildName, UUID targetUuid, OfflinePlayer kicker)
    {
        return runOnMainThread(() -> kickMember(guildName, targetUuid, kicker));
    }

    @Override
    public CompletableFuture<Boolean> promoteMemberAsync(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        return runOnMainThread(() -> promoteMember(guildName, targetUuid, operatorUuid));
    }

    @Override
    public CompletableFuture<Boolean> demoteMemberAsync(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        return runOnMainThread(() -> demoteMember(guildName, targetUuid, operatorUuid));
    }

    @Override
    public CompletableFuture<Boolean> transferOwnershipAsync(String guildName, UUID newOwner, UUID currentOwner)
    {
        return runOnMainThread(() -> transferOwnership(guildName, newOwner, currentOwner));
    }

    @Override
    public CompletableFuture<Boolean> upgradeGuildAsync(String guildName, UUID playerUuid)
    {
        return runOnMainThread(() -> upgradeGuild(guildName, playerUuid));
    }

    @Override
    public CompletableFuture<Boolean> depositToBankAsync(String guildName, UUID playerUuid, long amount)
    {
        return runOnMainThread(() -> depositToBank(guildName, playerUuid, amount));
    }

    @Override
    public CompletableFuture<Boolean> withdrawFromBankAsync(String guildName, UUID playerUuid, long amount)
    {
        return runOnMainThread(() -> withdrawFromBank(guildName, playerUuid, amount));
    }

    @Override
    public long getGuildCurrencyBalance(UUID playerUuid)
    {
        return playerUuid != null ? plugin.getGuildManager().getPlayerGuildCurrency(playerUuid) : 0L;
    }

    @Override
    public boolean depositGuildCurrency(UUID playerUuid, long amount)
    {
        return playerUuid != null && plugin.getGuildManager().depositPlayerGuildCurrency(playerUuid, amount);
    }

    @Override
    public boolean withdrawGuildCurrency(UUID playerUuid, long amount)
    {
        return playerUuid != null && plugin.getGuildManager().withdrawPlayerGuildCurrency(playerUuid, amount);
    }

    @Override
    public String getMessage(String key, String... replacements)
    {
        return plugin.getMessage(key, replacements);
    }

    @Override
    public void openMainGUI(Player player)
    {
        if (player == null) return;
        GuildGUI.openGUI(plugin, player);
    }

    @Override
    public void registerListener(Listener listener)
    {
        if (listener != null)
        {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }
}
