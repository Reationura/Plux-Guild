package com.guild.guild;
import com.guild.GuildPlugin;
import com.guild.api.event.GuildCreateEvent;
import com.guild.api.event.GuildDisbandEvent;
import com.guild.api.event.GuildJoinEvent;
import com.guild.api.event.GuildKickEvent;
import com.guild.api.event.GuildLeaveEvent;
import com.guild.api.event.GuildLevelUpEvent;
import com.guild.currency.GuildCurrency;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public class GuildManager
{
    private final GuildPlugin plugin;
    private final Map<String, Guild> guilds = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerGuilds = new ConcurrentHashMap<>();
    private final Map<String, List<GuildRequest>> requests = new ConcurrentHashMap<>();
    private final Map<UUID, GuildInvite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerGuildCurrency = new ConcurrentHashMap<>();

    private final Map<UUID, long[]> dailyBankUsage = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();
    private final LongAdder totalCacheHits = new LongAdder();
    private final LongAdder totalCacheMisses = new LongAdder();
    private volatile long lastCacheCleanup = System.currentTimeMillis();
    private static final long CACHE_CLEANUP_INTERVAL = 300000L;
    private static final long INVITE_EXPIRE_TIME = 600000L;
    private static final long REQUEST_EXPIRE_TIME = 600000L;
    private final Queue<Guild> pendingSaves = new ConcurrentLinkedQueue<>();

    private final Queue<String> pendingDeletes = new ConcurrentLinkedQueue<>();
    private final Queue<String[]> pendingRenames = new ConcurrentLinkedQueue<>();

    private final Set<Guild> queuedSaves = Collections.newSetFromMap(new ConcurrentHashMap<Guild, Boolean>());
    private final AtomicBoolean saveRunning = new AtomicBoolean(false);

    private final Map<UUID, Long> joinCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inviteCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> disbandCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> promoteCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> demoteCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> kickCooldownUntil = new ConcurrentHashMap<>();
    private final Map<Guild, Long> renameCooldownUntil = new ConcurrentHashMap<>();
    private final Map<Guild, Long> tagCooldownUntil = new ConcurrentHashMap<>();

    public GuildManager(GuildPlugin plugin)
    {
        this.plugin = plugin;
        startSaveQueue();
        startInviteCleanup();
        startInactiveKickTask();
    }

    private void startInactiveKickTask()
    {
        if (plugin.getGuildConfig() == null) return;
        final int days = plugin.getGuildConfig().getAutoKickInactiveDays();
        if (days <= 0) return;
        final long threshold = days * 86400000L;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
        {
            long now = System.currentTimeMillis();
            int removed = 0;
            for (Guild guild : guilds.values())
            {
                for (GuildMember member : new ArrayList<>(guild.getMembers().values()))
                {
                    UUID memberUuid = member.getUuid();
                    if (memberUuid.equals(guild.getOwner())) continue;
                    if (Bukkit.getPlayer(memberUuid) != null) continue;
                    long lastPlayed = plugin.getServer().getOfflinePlayer(memberUuid).getLastPlayed();
                    if (lastPlayed <= 0L || now - lastPlayed < threshold) continue;
                    guild.removeMember(memberUuid);
                    playerGuilds.remove(memberUuid);
                    removed++;
                    scheduleSave(guild);
                }
            }
            if (removed > 0)
            {
                plugin.getLogger().info("Auto-kicked " + removed + " inactive member(s) (inactive over " + days + " days)");
            }
        }, 12000L, 72000L);
    }

    public String getPlayerNameCached(UUID uuid)
    {
        String cached = playerNameCache.get(uuid);
        if (cached != null)
        {
            totalCacheHits.increment();
            return cached;
        }
        totalCacheMisses.increment();
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        if (name != null)
        {
            playerNameCache.put(uuid, name);
        }
        cleanupCacheIfNeeded();
        return name != null ? name : "未知";
    }

    private void cleanupCacheIfNeeded()
    {
        long now = System.currentTimeMillis();
        if (now - lastCacheCleanup > CACHE_CLEANUP_INTERVAL)
        {
            lastCacheCleanup = now;
            playerNameCache.clear();
        }
    }

    private void startSaveQueue()
    {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
        {
            if (saveRunning.compareAndSet(false, true))
            {
                try
                {
                    drainSaveQueue();
                }
                finally
                {
                    saveRunning.set(false);
                }
            }
        }, 100L, 200L);
    }

    private void drainSaveQueue()
    {
        String[] rename;
        while ((rename = pendingRenames.poll()) != null)
        {
            try
            {
                plugin.getDatabaseManager().migrateGuildName(rename[0], rename[1]);
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("Failed to migrate guild name " + rename[0] + " -> " + rename[1] + ": " + e.getMessage());
            }
        }
        Guild guild;
        while ((guild = pendingSaves.poll()) != null)
        {
            queuedSaves.remove(guild);
            try
            {
                plugin.getDatabaseManager().saveGuild(guild);
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("Failed to save guild " + guild.getName() + ": " + e.getMessage());
            }
        }
        String guildName;
        while ((guildName = pendingDeletes.poll()) != null)
        {
            try
            {
                plugin.getDatabaseManager().deleteGuild(guildName);
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("Failed to delete guild " + guildName + ": " + e.getMessage());
            }
        }
    }

    public void shutdown()
    {

        for (Guild guild : guilds.values())
        {
            scheduleSave(guild);
        }
        drainSaveQueue();
    }

    public void deleteGuildData(String guildName)
    {
        pendingDeletes.offer(guildName);
    }

    public void scheduleSavePublic(Guild guild) { scheduleSave(guild); }

    private void scheduleSave(Guild guild)
    {
        if (guild == null) return;

        if (guilds.get(guild.getName().toLowerCase()) != guild) return;

        if (!queuedSaves.add(guild)) return;
        pendingSaves.offer(guild);
    }

    private static long remainingSeconds(Long until)
    {
        if (until == null) return 0L;
        long left = until - System.currentTimeMillis();
        return left > 0L ? (left + 999L) / 1000L : 0L;
    }

    public long getJoinCooldownRemaining(UUID playerUuid)
    {
        return remainingSeconds(joinCooldownUntil.get(playerUuid));
    }

    public long getInviteCooldownRemaining(UUID playerUuid)
    {
        return remainingSeconds(inviteCooldownUntil.get(playerUuid));
    }

    public long getRenameCooldownRemaining(Guild guild)
    {
        return guild == null ? 0L : remainingSeconds(renameCooldownUntil.get(guild));
    }

    public long getTagCooldownRemaining(Guild guild)
    {
        return guild == null ? 0L : remainingSeconds(tagCooldownUntil.get(guild));
    }

    private void applyJoinCooldown(UUID playerUuid)
    {
        long seconds = plugin.getGuildConfig().getJoinCooldown();
        if (seconds <= 0L) return;
        joinCooldownUntil.put(playerUuid, System.currentTimeMillis() + seconds * 1000L);
    }

    private void clearJoinCooldown(UUID playerUuid)
    {
        joinCooldownUntil.remove(playerUuid);
    }

    private void startInviteCleanup()
    {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
        {
            long now = System.currentTimeMillis();
            invites.entrySet().removeIf(entry -> now - entry.getValue().getInviteTime() > INVITE_EXPIRE_TIME);

            for (List<GuildRequest> requestList : requests.values())
            {
                requestList.removeIf(r -> now - r.getRequestTime() > REQUEST_EXPIRE_TIME);
            }
            requests.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            joinCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            inviteCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            disbandCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            promoteCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            demoteCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            kickCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            long today = java.time.LocalDate.now().toEpochDay();
            dailyBankUsage.entrySet().removeIf(entry -> entry.getValue()[0] != today);
        }, 60000L, 60000L);
    }

    public Guild createGuild(String name, Player player)
    {
        if (!player.hasPermission("guild.create"))
        {
            player.sendMessage(ChatColor.RED + "你没有创建公会的权限");
            return null;
        }

        GuildCreateEvent createEvent = new GuildCreateEvent(player, name, null);
        Bukkit.getPluginManager().callEvent(createEvent);
        if (createEvent.isCancelled()) return null;
        name = createEvent.getGuildName();
        String key = name.toLowerCase();
        if (guilds.containsKey(key)) return null;
        if (playerGuilds.containsKey(player.getUniqueId())) return null;

        long disbandLeft = getDisbandCooldownRemaining(player.getUniqueId());
        if (disbandLeft > 0L)
        {
            player.sendMessage(plugin.getMessage("guild.cooldown-disband", "%seconds%", String.valueOf(disbandLeft)));
            return null;
        }
        boolean requiresMoney = plugin.getCurrencyConfig().isCreateRequiresMoney();
        long createCost = plugin.getCurrencyConfig().getCreateCost();
        if (requiresMoney && createCost > 0)
        {
            GuildCurrency.CurrencyType currencyType = plugin.getCurrencyConfig().getCurrencyType();
            if (!plugin.getGuildCurrency().withdraw(player.getUniqueId(), createCost, currencyType))
            {
                String currencyName = plugin.getGuildCurrency().formatAmount(createCost, currencyType);
                player.sendMessage(ChatColor.RED + "创建公会需要 " + currencyName);
                return null;
            }
        }
        Guild guild = new Guild(name, player.getUniqueId(), plugin);

        if (!createEvent.getGuildTag().isEmpty())
        {
            guild.setTag(createEvent.getGuildTag());
        }
        guilds.put(key, guild);
        playerGuilds.put(player.getUniqueId(), key);
        scheduleSave(guild);
        return guild;
    }

    public boolean renameGuild(String oldName, String newName, UUID operatorUuid)
    {
        String oldKey = oldName.toLowerCase();
        Guild guild = guilds.get(oldKey);
        if (guild == null) return false;
        if (!guild.getOwner().equals(operatorUuid)) return false;
        String newKey = newName.toLowerCase();
        if (guilds.containsKey(newKey)) return false;

        long renameLeft = getRenameCooldownRemaining(guild);
        if (renameLeft > 0L)
        {
            Player operator = Bukkit.getPlayer(operatorUuid);
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.cooldown-rename", "%seconds%", String.valueOf(renameLeft)));
            return false;
        }
        guilds.remove(oldKey);
        guild.setName(newName);
        guilds.put(newKey, guild);
        for (UUID memberUuid : guild.getMembers().keySet())
        {
            playerGuilds.put(memberUuid, newKey);
        }

        List<GuildRequest> pendingRequests = requests.remove(oldKey);
        if (pendingRequests != null && !pendingRequests.isEmpty())
        {
            requests.put(newKey, pendingRequests);
        }

        pendingRenames.offer(new String[]
        { oldName, newName });

        long renameCooldown = plugin.getGuildConfig().getRenameCooldown();
        if (renameCooldown > 0L)
        {
            renameCooldownUntil.put(guild, System.currentTimeMillis() + renameCooldown * 1000L);
        }
        scheduleSave(guild);
        return true;
    }

    public int changeTag(Guild guild, String tag, UUID operatorUuid)
    {
        if (guild == null || !guild.hasPermission(operatorUuid, "settings")) return 1;
        long left = getTagCooldownRemaining(guild);
        if (left > 0L)
        {
            Player operator = Bukkit.getPlayer(operatorUuid);
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.cooldown-tag", "%seconds%", String.valueOf(left)));
            return 2;
        }
        guild.setTag(tag);
        long cooldown = plugin.getGuildConfig().getTagChangeCooldown();
        if (cooldown > 0L)
        {
            tagCooldownUntil.put(guild, System.currentTimeMillis() + cooldown * 1000L);
        }
        scheduleSave(guild);
        return 0;
    }

    public boolean disbandGuild(String name, UUID playerUuid)
    {
        String key = name.toLowerCase();
        Guild guild = guilds.get(key);
        if (guild == null) return false;
        if (!guild.getOwner().equals(playerUuid)) return false;
        guilds.remove(key);
        for (UUID memberUuid : guild.getMembers().keySet())
        {
            playerGuilds.remove(memberUuid);
        }

        recordDisbandCooldown(playerUuid);
        requests.remove(key);

        invites.entrySet().removeIf(entry -> entry.getValue().getGuildName().equals(key));

        queuedSaves.remove(guild);
        pendingSaves.removeIf(pending -> pending == guild);
        renameCooldownUntil.remove(guild);
        tagCooldownUntil.remove(guild);
        final String guildName = guild.getName();

        deleteGuildData(guildName);
        Bukkit.getPluginManager().callEvent(new GuildDisbandEvent(guildName, Bukkit.getOfflinePlayer(playerUuid)));
        return true;
    }

    public Guild getGuild(String name)
    {
        return guilds.get(name.toLowerCase());
    }

    public Guild getPlayerGuild(UUID playerUuid)
    {
        String guildName = playerGuilds.get(playerUuid);
        return guildName != null ? guilds.get(guildName) : null;
    }

    public boolean isInGuild(UUID playerUuid)
    {
        return playerGuilds.containsKey(playerUuid);
    }

    public void removePlayerFromGuild(UUID playerUuid)
    {
        playerGuilds.remove(playerUuid);
    }

    public boolean joinGuild(String name, Player player)
    {
        Guild guild = guilds.get(name.toLowerCase());
        if (guild == null || !guild.canAddMember()) return false;

        if (playerGuilds.containsKey(player.getUniqueId())) return false;

        long joinLeft = getJoinCooldownRemaining(player.getUniqueId());
        if (joinLeft > 0L)
        {
            player.sendMessage(plugin.getMessage("guild.cooldown-join", "%seconds%", String.valueOf(joinLeft)));
            return false;
        }
        GuildJoinEvent joinEvent = new GuildJoinEvent(player, guild.getName());
        Bukkit.getPluginManager().callEvent(joinEvent);
        if (joinEvent.isCancelled()) return false;
        guild.addMember(player.getUniqueId(), GuildRole.MEMBER);
        playerGuilds.put(player.getUniqueId(), name.toLowerCase());
        clearJoinCooldown(player.getUniqueId());
        inviteCooldownUntil.remove(player.getUniqueId());
        scheduleSave(guild);
        return true;
    }

    public boolean leaveGuild(UUID playerUuid)
    {
        String guildName = playerGuilds.get(playerUuid);
        if (guildName == null) return false;
        Guild guild = guilds.get(guildName);
        if (guild == null || guild.getOwner().equals(playerUuid)) return false;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null)
        {
            GuildLeaveEvent leaveEvent = new GuildLeaveEvent(player, guild.getName());
            Bukkit.getPluginManager().callEvent(leaveEvent);
            if (leaveEvent.isCancelled()) return false;
        }
        guild.removeMember(playerUuid);
        playerGuilds.remove(playerUuid);

        applyJoinCooldown(playerUuid);
        scheduleSave(guild);
        return true;
    }

    private void recordDisbandCooldown(UUID ownerUuid)
    {
        long seconds = plugin.getGuildConfig().getDisbandCooldown();
        if (seconds <= 0L) return;
        disbandCooldownUntil.put(ownerUuid, System.currentTimeMillis() + seconds * 1000L);
    }

    public long getDisbandCooldownRemaining(UUID ownerUuid)
    {
        return remainingSeconds(disbandCooldownUntil.get(ownerUuid));
    }

    public boolean kickMember(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || operatorUuid == null || !guild.hasPermission(operatorUuid, "kick")) return false;
        GuildMember targetMember = guild.getMember(targetUuid);
        GuildMember operatorMember = guild.getMember(operatorUuid);
        if (targetMember == null || operatorMember == null) return false;

        if (targetUuid.equals(operatorUuid)) return false;
        if (targetMember.getRole() == GuildRole.OWNER) return false;
        if (!guild.getOwner().equals(operatorUuid)
            && targetMember.getRole().getLevel() >= operatorMember.getRole().getLevel()) return false;

        long kickLeft = remainingSeconds(kickCooldownUntil.get(operatorUuid));
        if (kickLeft > 0L)
        {
            Player operator = Bukkit.getPlayer(operatorUuid);
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.cooldown-kick", "%seconds%", String.valueOf(kickLeft)));
            return false;
        }
        GuildKickEvent kickEvent = new GuildKickEvent(guild.getName(), targetUuid, Bukkit.getOfflinePlayer(operatorUuid));
        Bukkit.getPluginManager().callEvent(kickEvent);
        if (kickEvent.isCancelled()) return false;
        guild.removeMember(targetUuid);
        playerGuilds.remove(targetUuid);

        applyJoinCooldown(targetUuid);
        long kickCooldown = plugin.getGuildConfig().getKickCooldown();
        if (kickCooldown > 0L)
        {
            kickCooldownUntil.put(operatorUuid, System.currentTimeMillis() + kickCooldown * 1000L);
        }
        scheduleSave(guild);
        return true;
    }

    public boolean promoteMember(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.hasPermission(operatorUuid, "promote")) return false;
        GuildMember member = guild.getMember(targetUuid);
        if (member == null) return false;
        GuildRole newRole = member.getRole().promote();

        if (newRole == member.getRole()) return false;
        Player operator = Bukkit.getPlayer(operatorUuid);

        long left = remainingSeconds(promoteCooldownUntil.get(targetUuid));
        if (left > 0L)
        {
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.cooldown-promote", "%seconds%", String.valueOf(left)));
            return false;
        }

        int maxOfficers = plugin.getGuildConfig().getMaxOfficers();
        if (maxOfficers > 0 && newRole == GuildRole.OFFICER && countOfficers(guild) >= maxOfficers)
        {
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.officer-limit", "%max%", String.valueOf(maxOfficers)));
            return false;
        }
        member.setRole(newRole);
        promoteCooldownUntil.put(targetUuid, System.currentTimeMillis() + plugin.getGuildConfig().getPromoteCooldown() * 1000L);
        scheduleSave(guild);
        return true;
    }

    public boolean demoteMember(String guildName, UUID targetUuid, UUID operatorUuid)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.hasPermission(operatorUuid, "demote")) return false;
        GuildMember member = guild.getMember(targetUuid);
        if (member == null) return false;
        GuildRole newRole = member.getRole().demote();

        if (newRole == member.getRole()) return false;
        Player operator = Bukkit.getPlayer(operatorUuid);

        long left = remainingSeconds(demoteCooldownUntil.get(targetUuid));
        if (left > 0L)
        {
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.cooldown-demote", "%seconds%", String.valueOf(left)));
            return false;
        }

        if (targetUuid.equals(operatorUuid) && !plugin.getGuildConfig().isAllowSelfDemote())
        {
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.self-demote"));
            return false;
        }
        member.setRole(newRole);
        demoteCooldownUntil.put(targetUuid, System.currentTimeMillis() + plugin.getGuildConfig().getDemoteCooldown() * 1000L);
        scheduleSave(guild);
        return true;
    }

    private static int countOfficers(Guild guild)
    {
        int count = 0;
        for (GuildMember member : guild.getMembers().values())
        {
            if (member.getRole() == GuildRole.OFFICER) count++;
        }
        return count;
    }

    public boolean transferOwnership(String guildName, UUID targetUuid, UUID ownerUuid)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.getOwner().equals(ownerUuid)) return false;
        GuildMember targetMember = guild.getMember(targetUuid);
        if (targetMember == null) return false;
        GuildMember ownerMember = guild.getMember(ownerUuid);
        if (ownerMember != null) ownerMember.setRole(GuildRole.OFFICER);
        targetMember.setRole(GuildRole.OWNER);
        guild.setOwner(targetUuid);
        scheduleSave(guild);
        return true;
    }

    public void addRequest(String guildName, UUID playerUuid, String playerName)
    {
        List<GuildRequest> requestList = requests.computeIfAbsent(guildName.toLowerCase(), k -> new CopyOnWriteArrayList<>());

        for (GuildRequest existing : requestList)
        {
            if (existing.getPlayerUuid().equals(playerUuid)) return;
        }
        requestList.add(new GuildRequest(playerUuid, playerName));
    }

    public List<GuildRequest> getRequests(String guildName)
    {
        return requests.getOrDefault(guildName.toLowerCase(), Collections.emptyList());
    }

    public boolean acceptRequest(String guildName, UUID playerUuid)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.canAddMember()) return false;

        if (playerGuilds.containsKey(playerUuid)) return false;
        List<GuildRequest> requestList = requests.get(guildName.toLowerCase());
        if (requestList == null) return false;
        GuildRequest foundRequest = null;
        for (GuildRequest r : requestList)
        {
            if (r.getPlayerUuid().equals(playerUuid))
            {
                foundRequest = r;
                break;
            }
        }
        if (foundRequest == null) return false;

        long joinLeft = remainingSeconds(joinCooldownUntil.get(playerUuid));
        if (joinLeft > 0L)
        {
            Player applicant = Bukkit.getPlayer(playerUuid);
            if (applicant != null) applicant.sendMessage(plugin.getMessage("guild.cooldown-join", "%seconds%", String.valueOf(joinLeft)));
            return false;
        }

        requestList.remove(foundRequest);
        if (requestList.isEmpty())
        {
            requests.remove(guildName.toLowerCase());
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null)
        {
            GuildJoinEvent joinEvent = new GuildJoinEvent(player, guild.getName());
            Bukkit.getPluginManager().callEvent(joinEvent);
            if (joinEvent.isCancelled()) return false;
        }
        guild.addMember(playerUuid, GuildRole.MEMBER);
        playerGuilds.put(playerUuid, guildName.toLowerCase());
        clearJoinCooldown(playerUuid);
        scheduleSave(guild);
        return true;
    }

    public boolean sendInvite(String guildName, UUID inviterUuid, UUID targetUuid, String targetName)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.hasPermission(inviterUuid, "invite")) return false;
        if (invites.containsKey(targetUuid)) return false;

        long left = remainingSeconds(inviteCooldownUntil.get(targetUuid));
        if (left > 0L)
        {
            Player inviter = Bukkit.getPlayer(inviterUuid);
            if (inviter != null)
            {
                inviter.sendMessage(plugin.getMessage("guild.cooldown-invite", "%seconds%", String.valueOf(left))
                    .replace("%player%", targetName));
            }
            return false;
        }
        invites.put(targetUuid, new GuildInvite(guildName.toLowerCase(), inviterUuid, targetUuid, targetName));
        return true;
    }

    public boolean acceptInvite(UUID playerUuid)
    {
        GuildInvite invite = invites.remove(playerUuid);
        if (invite == null) return false;
        long now = System.currentTimeMillis();
        if (now - invite.getInviteTime() > INVITE_EXPIRE_TIME) return false;

        if (playerGuilds.containsKey(playerUuid)) return false;
        Guild guild = guilds.get(invite.getGuildName());
        if (guild == null || !guild.canAddMember()) return false;

        Player player = Bukkit.getPlayer(playerUuid);
        long joinLeft = remainingSeconds(joinCooldownUntil.get(playerUuid));
        if (joinLeft > 0L)
        {
            if (player != null) player.sendMessage(plugin.getMessage("guild.cooldown-join", "%seconds%", String.valueOf(joinLeft)));
            return false;
        }
        if (player != null)
        {
            GuildJoinEvent joinEvent = new GuildJoinEvent(player, guild.getName());
            Bukkit.getPluginManager().callEvent(joinEvent);
            if (joinEvent.isCancelled()) return false;
        }
        guild.addMember(playerUuid, GuildRole.MEMBER);
        playerGuilds.put(playerUuid, invite.getGuildName());
        clearJoinCooldown(playerUuid);
        scheduleSave(guild);
        return true;
    }

    public boolean declineInvite(UUID playerUuid)
    {
        GuildInvite removed = invites.remove(playerUuid);
        if (removed == null) return false;

        long seconds = plugin.getGuildConfig().getInviteCooldown();
        if (seconds > 0L)
        {
            inviteCooldownUntil.put(playerUuid, System.currentTimeMillis() + seconds * 1000L);
        }
        return true;
    }

    public GuildInvite getInvite(UUID playerUuid)
    {
        return invites.get(playerUuid);
    }

    public Map<UUID, GuildInvite> getInvites()
    {
        return invites;
    }

    public void addExperience(UUID playerUuid, long amount)
    {
        if (amount <= 0) return;
        String guildName = playerGuilds.get(playerUuid);
        if (guildName == null) return;
        Guild guild = guilds.get(guildName);
        if (guild == null) return;
        long finalAmount = amount;

        double multiplier = plugin.getExperienceConfig().getWeekendMultiplier();
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if ((dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) && multiplier > 1.0)
        {
            finalAmount = (long) (finalAmount * multiplier);
        }
        double holidayMultiplier = plugin.getExperienceConfig().getHolidayMultiplier();
        if (holidayMultiplier != 1.0)
        {
            finalAmount = (long) (finalAmount * holidayMultiplier);
        }
        if (finalAmount <= 0) return;
        GuildMember member = guild.getMember(playerUuid);

        long expLimit = plugin.getExperienceConfig().getDailyExpLimit();
        if (expLimit > 0 && guild.getDailyExperience() + finalAmount > expLimit)
        {
            finalAmount = Math.max(0L, expLimit - guild.getDailyExperience());
        }
        if (member != null)
        {
            long contributionLimit = plugin.getExperienceConfig().getDailyContributionLimit();
            long contribution = finalAmount;
            if (contributionLimit > 0 && member.getDailyContribution() + contribution > contributionLimit)
            {
                contribution = Math.max(0L, contributionLimit - member.getDailyContribution());
            }
            member.addContribution(contribution);
        }
        if (finalAmount <= 0) return;
        guild.addExperience(finalAmount);
        scheduleSave(guild);
    }

    public long getGuildBalance(String guildName)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        return guild != null ? guild.getBank().getBalance() : 0L;
    }

    private boolean hasRequiredBankRole(Guild guild, UUID playerUuid, String requiredRole)
    {
        if (requiredRole == null) return true;
        String key = requiredRole.trim().toUpperCase();
        if (key.isEmpty()) return true;
        GuildRole required;
        try
        {
            required = GuildRole.valueOf(key);
        }
        catch (IllegalArgumentException ignored)
        {
            return true;
        }
        GuildMember member = guild.getMember(playerUuid);
        if (member != null && member.getRole().getLevel() >= required.getLevel()) return true;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null)
        {
            player.sendMessage(plugin.getMessage("bank.permission-required", "%role%", required.getDisplayName()));
        }
        return false;
    }

    public boolean depositToBank(String guildName, UUID playerUuid, long amount)
    {
        if (amount <= 0L) return false;
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.isMember(playerUuid)) return false;
        com.guild.config.BankConfig bankConfig = plugin.getBankConfig();

        if (bankConfig != null && !hasRequiredBankRole(guild, playerUuid, bankConfig.getDepositPermission())) return false;

        long maxDeposit = bankConfig.getMaxDeposit();
        if (maxDeposit > 0 && amount > maxDeposit) return false;

        long dailyLimit = bankConfig.getDailyDepositLimit();
        if (dailyLimit > 0)
        {
            long[] usage = dailyBankUsage.computeIfAbsent(playerUuid, k -> new long[]
            { java.time.LocalDate.now().toEpochDay(), 0L, 0L });
            if (usage[0] != java.time.LocalDate.now().toEpochDay())
            {
                usage[0] = java.time.LocalDate.now().toEpochDay();
                usage[1] = 0L;
                usage[2] = 0L;
            }
            if (usage[1] + amount > dailyLimit) return false;
        }

        long maxBalance = bankConfig.getMaxBalance();
        if (maxBalance > 0 && guild.getBank().getBalance() + amount > maxBalance) return false;

        GuildCurrency.CurrencyType currencyType = plugin.getCurrencyConfig().getCurrencyType();
        if (!plugin.getGuildCurrency().withdraw(playerUuid, amount, currencyType)) return false;

        double tax = bankConfig.getDepositTax();
        long net = amount;
        if (tax > 0)
        {
            net = (long) (amount * (1.0 - Math.min(tax, 1.0)));
        }
        if (net <= 0)
        {

            plugin.getGuildCurrency().deposit(playerUuid, amount, currencyType);
            return false;
        }
        if (guild.getBank().deposit(net))
        {
            guild.getBank().addDepositRecord(getPlayerNameCached(playerUuid), net);

            logLargeTransaction(bankConfig, playerUuid, guild.getName(), "DEPOSIT", amount, net);
            if (dailyLimit > 0)
            {
                dailyBankUsage.get(playerUuid)[1] += amount;
            }
            scheduleSave(guild);
            return true;
        }

        plugin.getGuildCurrency().deposit(playerUuid, amount, currencyType);
        return false;
    }

    public boolean withdrawFromBank(String guildName, UUID playerUuid, long amount)
    {
        if (amount <= 0L) return false;
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.isMember(playerUuid) || !guild.hasPermission(playerUuid, "withdraw")) return false;
        com.guild.config.BankConfig bankConfig = plugin.getBankConfig();

        if (bankConfig != null && !hasRequiredBankRole(guild, playerUuid, bankConfig.getWithdrawPermission())) return false;

        long maxWithdraw = bankConfig.getMaxWithdraw();
        if (maxWithdraw > 0 && amount > maxWithdraw) return false;

        long dailyLimit = bankConfig.getDailyWithdrawLimit();
        if (dailyLimit > 0)
        {
            long[] usage = dailyBankUsage.computeIfAbsent(playerUuid, k -> new long[]
            { java.time.LocalDate.now().toEpochDay(), 0L, 0L });
            if (usage[0] != java.time.LocalDate.now().toEpochDay())
            {
                usage[0] = java.time.LocalDate.now().toEpochDay();
                usage[1] = 0L;
                usage[2] = 0L;
            }
            if (usage[2] + amount > dailyLimit) return false;
        }

        long minBalance = bankConfig.getMinBalance();
        if (guild.getBank().getBalance() - amount < minBalance) return false;

        double tax = bankConfig.getWithdrawTax();
        long net = amount;
        if (tax > 0)
        {
            net = (long) (amount * (1.0 - Math.min(tax, 1.0)));
        }
        if (net <= 0) return false;
        if (guild.getBank().withdraw(amount))
        {
            guild.getBank().addWithdrawRecord(getPlayerNameCached(playerUuid), amount);

            logLargeTransaction(bankConfig, playerUuid, guildName, "WITHDRAW", amount, net);

            plugin.getGuildCurrency().deposit(playerUuid, net, plugin.getCurrencyConfig().getCurrencyType());
            if (dailyLimit > 0)
            {
                dailyBankUsage.get(playerUuid)[2] += amount;
            }
            scheduleSave(guild);
            return true;
        }
        return false;
    }

    public long getPlayerGuildCurrency(UUID playerUuid)
    {
        return playerGuildCurrency.getOrDefault(playerUuid, 0L);
    }

    private void logLargeTransaction(com.guild.config.BankConfig bankConfig, UUID playerUuid, String guildName, String action, long gross, long net)
    {
        if (bankConfig == null || !bankConfig.isLogLargeTransactions()) return;
        long threshold = bankConfig.getLargeTransactionThreshold();
        if (threshold <= 0L || gross < threshold) return;
        plugin.getLogger().info("[GuildBank] " + action + " " + getPlayerNameCached(playerUuid)
            + " guild=" + guildName + " amount=" + gross + " actual=" + net);
    }

    public boolean depositPlayerGuildCurrency(UUID playerUuid, long amount)
    {
        if (amount <= 0L) return false;
        final long maxBalance = plugin.getCurrencyConfig().getMaxBalance();

        playerGuildCurrency.compute(playerUuid, (uuid, current) ->
        {
            long base = current == null ? 0L : current;
            long next = base + amount;
            if (maxBalance > 0 && next > maxBalance)
            {
                next = maxBalance;
            }
            return next;
        });
        savePlayerCurrencyAsync(playerUuid);
        return true;
    }

    public boolean withdrawPlayerGuildCurrency(UUID playerUuid, long amount)
    {
        if (amount <= 0L) return false;
        while (true)
        {
            Long current = playerGuildCurrency.get(playerUuid);
            long cur = current != null ? current : 0L;
            if (cur < amount) return false;
            if (playerGuildCurrency.replace(playerUuid, cur, cur - amount)) break;
        }
        savePlayerCurrencyAsync(playerUuid);
        return true;
    }

    public boolean setPlayerGuildCurrency(UUID playerUuid, long amount)
    {
        if (amount < 0L) return false;
        playerGuildCurrency.put(playerUuid, amount);
        savePlayerCurrencyAsync(playerUuid);
        return true;
    }

    private void savePlayerCurrencyAsync(UUID playerUuid)
    {
        long balance = playerGuildCurrency.getOrDefault(playerUuid, 0L);
        try
        {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().savePlayerCurrency(playerUuid, balance));
        }
        catch (IllegalStateException ignored)
        {

        }
    }

    public void loadPlayerCurrencyBalances(Map<UUID, Long> balances)
    {
        playerGuildCurrency.putAll(balances);
    }

    public void ensurePlayerCurrencyRow(UUID playerUuid)
    {
        if (playerGuildCurrency.containsKey(playerUuid)) return;
        try
        {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            {
                long initial = plugin.getCurrencyConfig().getInitialBalance();
                long balance = plugin.getDatabaseManager().ensurePlayerCurrency(playerUuid, initial);
                playerGuildCurrency.put(playerUuid, balance);
            });
        }
        catch (IllegalStateException ignored)
        {
        }
    }

    public boolean upgradeGuild(String guildName, UUID playerUuid)
    {
        Guild guild = guilds.get(guildName.toLowerCase());

        int maxLevel = plugin.getGuildConfig().getMaxLevel();
        if (guild == null || !guild.getOwner().equals(playerUuid) || guild.getLevel() >= maxLevel) return false;
        GuildCurrency.CurrencyType currencyType = plugin.getCurrencyConfig().getCurrencyType();
        long cost = plugin.getCurrencyConfig().getLevelUpCost(guild.getLevel());
        if (!plugin.getGuildCurrency().withdraw(playerUuid, cost, currencyType)) return false;
        int oldLevel = guild.getLevel();
        guild.setLevel(oldLevel + 1);

        Bukkit.getPluginManager().callEvent(new GuildLevelUpEvent(guild.getName(), oldLevel, guild.getLevel()));
        scheduleSave(guild);
        return true;
    }

    public long getUpgradeCost(String guildName)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null) return -1L;
        return plugin.getCurrencyConfig().getLevelUpCost(guild.getLevel());
    }

    public boolean addExperienceWithCurrency(String guildName, UUID playerUuid)
    {
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.isMember(playerUuid)) return false;
        GuildCurrency.CurrencyType currencyType = plugin.getCurrencyConfig().getCurrencyType();
        long cost = plugin.getCurrencyConfig().getExperienceCost();
        int expAmount = plugin.getCurrencyConfig().getExperienceAmount();
        if (!plugin.getGuildCurrency().withdraw(playerUuid, cost, currencyType)) return false;
        guild.addExperience(expAmount);
        GuildMember member = guild.getMember(playerUuid);
        if (member != null)
        {
            member.addContribution(expAmount);
        }
        scheduleSave(guild);
        return true;
    }

    public PlayerSettings getPlayerSettings(UUID playerUuid)
    {
        return playerSettings.computeIfAbsent(playerUuid, PlayerSettings::new);
    }

    public boolean togglePlayerInvites(UUID playerUuid)
    {
        PlayerSettings settings = getPlayerSettings(playerUuid);
        settings.toggleInvites();
        return settings.isAllowInvites();
    }

    public boolean togglePlayerNotify(UUID playerUuid)
    {
        PlayerSettings settings = getPlayerSettings(playerUuid);
        settings.toggleNotify();
        return settings.isNotifyOnlineStatus();
    }

    public Map<String, Guild> getGuilds()
    {
        return Collections.unmodifiableMap(guilds);
    }

    public void addGuild(Guild guild)
    {
        guilds.put(guild.getName().toLowerCase(), guild);
        for (UUID memberUuid : guild.getMembers().keySet())
        {
            playerGuilds.put(memberUuid, guild.getName().toLowerCase());
        }
    }

    public Map<UUID, String> getPlayerGuilds()
    {
        return Collections.unmodifiableMap(playerGuilds);
    }

    public void clearAllDailyData()
    {
        for (Guild guild : guilds.values())
        {
            guild.setDailyExperience(0L);
            for (GuildMember member : guild.getMembers().values())
            {
                member.setDailyContribution(0L);
            }
            scheduleSave(guild);
        }
        dailyBankUsage.clear();
    }

    public static class GuildRequest
    {
        private final UUID playerUuid;
        private final String playerName;
        private final long requestTime;

        public GuildRequest(UUID playerUuid, String playerName)
        {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.requestTime = System.currentTimeMillis();
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public long getRequestTime() { return requestTime; }
    }

    public static class GuildInvite
    {
        private final String guildName;
        private final UUID inviterUuid;
        private final UUID targetUuid;
        private final String targetName;
        private final long inviteTime;

        public GuildInvite(String guildName, UUID inviterUuid, UUID targetUuid, String targetName)
        {
            this.guildName = guildName;
            this.inviterUuid = inviterUuid;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.inviteTime = System.currentTimeMillis();
        }

        public String getGuildName() { return guildName; }
        public UUID getInviterUuid() { return inviterUuid; }
        public UUID getTargetUuid() { return targetUuid; }
        public String getTargetName() { return targetName; }
        public long getInviteTime() { return inviteTime; }
    }
}
