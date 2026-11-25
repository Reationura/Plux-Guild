package com.guild.api;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildAPIProvider
{
    default boolean isReady()
    {
        return false;
    }
    Plugin getPlugin();
    GuildData getGuild(String name);
    GuildData getGuildByTag(String tag);
    List<GuildData> searchGuilds(String keyword);
    GuildData getPlayerGuild(UUID playerUuid);
    boolean isInGuild(UUID playerUuid);
    Map<String, GuildData> getAllGuilds();
    List<GuildData> getTopGuilds(int limit);
    String getMemberRole(UUID playerUuid);
    List<UUID> getOnlineMembers(String guildName);
    int getOnlineCount(String guildName);
    CompletableFuture<GuildData> createGuildAsync(String name, Player owner);
    CompletableFuture<GuildData> createGuildAsync(String name, String tag, Player owner);
    CompletableFuture<Boolean> disbandGuildAsync(String name);
    CompletableFuture<Boolean> joinGuildAsync(Player player, String guildName);
    CompletableFuture<Boolean> leaveGuildAsync(Player player);
    CompletableFuture<Boolean> kickMemberAsync(String guildName, UUID target, UUID kicker);
    void addExperience(UUID playerUuid, long amount);
    CompletableFuture<Boolean> upgradeGuildAsync(String guildName, UUID playerUuid);
    boolean depositToBank(String guildName, UUID playerUuid, long amount);
    boolean withdrawFromBank(String guildName, UUID playerUuid, long amount);
    void openMainGUI(Player player);
    String getMessage(String key);
}
