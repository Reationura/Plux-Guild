package com.guild.api.v2;
import com.guild.api.GuildData;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildApiV2
{
    String API_VERSION = "2.0";

    boolean isReady();

    String getApiVersion();

    com.guild.GuildPlugin getPlugin();

    GuildData getGuild(String name);

    GuildData getGuildByTag(String tag);

    List<GuildData> searchGuilds(String keyword, int limit);

    GuildData getPlayerGuild(UUID playerUuid);

    boolean isInGuild(UUID playerUuid);

    String getMemberRole(UUID playerUuid);

    List<GuildData> getAllGuildList();

    List<GuildData> getTopGuilds(int limit);

    List<UUID> getOnlineMembers(String guildName);

    int getOnlineCount(String guildName);

    int getTotalGuildCount();

    int getTotalMemberCount();

    double getAverageLevel();

    long getBankBalance(String guildName);

    GuildData createGuild(String name, Player owner);

    boolean disbandGuild(String name, OfflinePlayer disbander);

    boolean joinGuild(Player player, String guildName);

    boolean leaveGuild(Player player);

    boolean kickMember(String guildName, UUID targetUuid, OfflinePlayer kicker);

    boolean promoteMember(String guildName, UUID targetUuid, UUID operatorUuid);

    boolean demoteMember(String guildName, UUID targetUuid, UUID operatorUuid);

    boolean transferOwnership(String guildName, UUID newOwner, UUID currentOwner);

    boolean upgradeGuild(String guildName, UUID playerUuid);

    boolean sendInvite(String guildName, UUID inviter, UUID target, String targetName);

    boolean acceptInvite(UUID playerUuid);

    boolean declineInvite(UUID playerUuid);

    void addExperience(UUID playerUuid, long amount);

    boolean depositToBank(String guildName, UUID playerUuid, long amount);

    boolean withdrawFromBank(String guildName, UUID playerUuid, long amount);

    CompletableFuture<GuildData> createGuildAsync(String name, Player owner);

    CompletableFuture<Boolean> disbandGuildAsync(String name, OfflinePlayer disbander);

    CompletableFuture<Boolean> joinGuildAsync(Player player, String guildName);

    CompletableFuture<Boolean> leaveGuildAsync(Player player);

    CompletableFuture<Boolean> kickMemberAsync(String guildName, UUID targetUuid, OfflinePlayer kicker);

    CompletableFuture<Boolean> promoteMemberAsync(String guildName, UUID targetUuid, UUID operatorUuid);

    CompletableFuture<Boolean> demoteMemberAsync(String guildName, UUID targetUuid, UUID operatorUuid);

    CompletableFuture<Boolean> transferOwnershipAsync(String guildName, UUID newOwner, UUID currentOwner);

    CompletableFuture<Boolean> upgradeGuildAsync(String guildName, UUID playerUuid);

    CompletableFuture<Boolean> depositToBankAsync(String guildName, UUID playerUuid, long amount);

    CompletableFuture<Boolean> withdrawFromBankAsync(String guildName, UUID playerUuid, long amount);

    long getGuildCurrencyBalance(UUID playerUuid);

    boolean depositGuildCurrency(UUID playerUuid, long amount);

    boolean withdrawGuildCurrency(UUID playerUuid, long amount);

    String getMessage(String key, String... replacements);

    void openMainGUI(Player player);

    void registerListener(Listener listener);
}
