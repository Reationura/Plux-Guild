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
    /** 每日银行限额追踪: uuid -> [当天epochDay, 已存, 已取] */
    private final Map<UUID, long[]> dailyBankUsage = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();
    private final LongAdder totalCacheHits = new LongAdder();
    private final LongAdder totalCacheMisses = new LongAdder();
    private volatile long lastCacheCleanup = System.currentTimeMillis();
    private static final long CACHE_CLEANUP_INTERVAL = 300000L;
    private static final long INVITE_EXPIRE_TIME = 600000L;
    private static final long REQUEST_EXPIRE_TIME = 600000L;
    private final Queue<Guild> pendingSaves = new ConcurrentLinkedQueue<>();
    /** 3.0.9：公会删除任务排队，与保存共用同一个异步线程，保证"先写完再删"顺序，修复解散公会因保存任务尚未排空而复活的问题 */
    private final Queue<String> pendingDeletes = new ConcurrentLinkedQueue<>();
    /** 3.0.9：保存去重集合（按 Guild 对象身份），避免高频事件重复入队导致同一公会被反复整库写入 */
    private final Set<Guild> queuedSaves = Collections.newSetFromMap(new ConcurrentHashMap<Guild, Boolean>());
    private final AtomicBoolean saveRunning = new AtomicBoolean(false);

    /** 3.0.9：guild.yml 冷却配置落地（原为预留配置，完全不生效） */
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

    /**
     * 3.0.9：guild.yml auto-kick-inactive-days 落地（旧为预留配置）。
     * 每 6 小时扫描一次，剔除离线超过阈值（天）的非会长成员；
     * 使用服务端 usercache 记录的 lastPlayed，无需额外数据库字段。
     * 0 = 禁用（默认值，不影响现有服务器）；扫描在异步线程执行，避免逐个查磁盘卡主线程。
     */
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

    /** 排空保存队列（去重集合同步移除），供定时任务与插件关闭共用 */
    private void drainSaveQueue()
    {
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
        // 删除任务放在本批保存之后执行，避免"删除完成后又被旧队列写回"的公会复活
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

    /**
     * 3.0.9：插件关闭时同步落库，修复旧版重启/关闭后未及入队的变更（等级、经验、银行、成员）丢失的问题。
     * 必须在 DatabaseManager.close() 之前调用。
     */
    public void shutdown()
    {
        // 兜底：把仍在内存中但未被入队的公会也排入队列
        for (Guild guild : guilds.values())
        {
            scheduleSave(guild);
        }
        drainSaveQueue();
    }

    /** 将公会数据库删除排入异步保存线程（不阻塞主线程，且与保存严格串行） */
    public void deleteGuildData(String guildName)
    {
        pendingDeletes.offer(guildName);
    }

    public void scheduleSavePublic(Guild guild) { scheduleSave(guild); }

    private void scheduleSave(Guild guild)
    {
        if (guild == null) return;
        // 3.0.9：只保存仍在注册表中的公会——解散与异步保存并发时，
        // 避免已被删除的公会因在途入队而在数据库里“复活”
        if (guilds.get(guild.getName().toLowerCase()) != guild) return;
        // 已在队列中则跳过：挖矿等高频事件下同一公会只会被完整写库一次
        if (!queuedSaves.add(guild)) return;
        pendingSaves.offer(guild);
    }

    // ==================== 3.0.9：冷却工具方法 ====================

    /** 剩余冷却秒数，0 表示无冷却 */
    private static long remainingSeconds(Long until)
    {
        if (until == null) return 0L;
        long left = until - System.currentTimeMillis();
        return left > 0L ? (left + 999L) / 1000L : 0L;
    }

    /** 查询玩家加入公会的剩余冷却秒数（供命令/GUI 展示） */
    public long getJoinCooldownRemaining(UUID playerUuid)
    {
        return remainingSeconds(joinCooldownUntil.get(playerUuid));
    }

    /** 查询玩家邀请剩余冷却秒数 */
    public long getInviteCooldownRemaining(UUID playerUuid)
    {
        return remainingSeconds(inviteCooldownUntil.get(playerUuid));
    }

    /** 查询公会重命名剩余冷却秒数 */
    public long getRenameCooldownRemaining(Guild guild)
    {
        return guild == null ? 0L : remainingSeconds(renameCooldownUntil.get(guild));
    }

    /** 查询公会标签修改剩余冷却秒数 */
    public long getTagCooldownRemaining(Guild guild)
    {
        return guild == null ? 0L : remainingSeconds(tagCooldownUntil.get(guild));
    }

    /** 离开/被踢出后记录加入冷却（guild.yml join-cooldown，0 = 关闭） */
    private void applyJoinCooldown(UUID playerUuid)
    {
        long seconds = plugin.getGuildConfig().getJoinCooldown();
        if (seconds <= 0L) return;
        joinCooldownUntil.put(playerUuid, System.currentTimeMillis() + seconds * 1000L);
    }

    /** 加入成功（或已加入其他公会）后清除冷却，避免误伤正常玩家 */
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
            // 同步清理过期的入会申请，防止幽灵申请长期堆积
            for (List<GuildRequest> requestList : requests.values())
            {
                requestList.removeIf(r -> now - r.getRequestTime() > REQUEST_EXPIRE_TIME);
            }
            requests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            // 3.0.9：清理已到期的冷却记录，长期运行的插件不会因为冷却表无限增长
            joinCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            inviteCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            disbandCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            promoteCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            demoteCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
            kickCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        }, 60000L, 60000L);
    }

    public Guild createGuild(String name, Player player)
    {
        if (!player.hasPermission("guild.create"))
        {
            player.sendMessage(ChatColor.RED + "你没有创建公会的权限");
            return null;
        }
        // 创建事件下沉到管理器层，保证命令/GUI/API 所有路径都会触发一次事件
        GuildCreateEvent createEvent = new GuildCreateEvent(player, name, null);
        Bukkit.getPluginManager().callEvent(createEvent);
        if (createEvent.isCancelled()) return null;
        name = createEvent.getGuildName();
        String key = name.toLowerCase();
        if (guilds.containsKey(key)) return null;
        if (playerGuilds.containsKey(player.getUniqueId())) return null;
        // 3.0.9：解散冷却中的会长不允许再次创建公会（guild.yml disband-cooldown）
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
        // 监听器在事件中修改的标签同样生效
        if (!createEvent.getGuildTag().isEmpty())
        {
            guild.setTag(createEvent.getGuildTag());
        }
        guilds.put(key, guild);
        playerGuilds.put(player.getUniqueId(), key);
        scheduleSave(guild);
        return guild;
    }

    /**
     * 重命名公会：保留全部成员、等级、经验、银行与权限数据，
     * 仅迁移内存索引与数据库记录（替代旧版"解散后重建"的危险实现）
     */
    public boolean renameGuild(String oldName, String newName, UUID operatorUuid)
    {
        String oldKey = oldName.toLowerCase();
        Guild guild = guilds.get(oldKey);
        if (guild == null) return false;
        if (!guild.getOwner().equals(operatorUuid)) return false;
        String newKey = newName.toLowerCase();
        if (guilds.containsKey(newKey)) return false;
        // 3.0.9：guild.yml rename-cooldown 落地
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
        // 同步迁移旧名称下待处理的入会申请，避免改名后申请丢失
        List<GuildRequest> pendingRequests = requests.remove(oldKey);
        if (pendingRequests != null && !pendingRequests.isEmpty())
        {
            requests.put(newKey, pendingRequests);
        }
        // 迁移数据库记录（旧名称的成员/权限/银行记录全部转移）
        plugin.getDatabaseManager().migrateGuildName(oldName, newName);
        // 3.0.9：记录改名冷却（冷却表按 Guild 对象索引，改名后自动沿用）
        long renameCooldown = plugin.getGuildConfig().getRenameCooldown();
        if (renameCooldown > 0L)
        {
            renameCooldownUntil.put(guild, System.currentTimeMillis() + renameCooldown * 1000L);
        }
        scheduleSave(guild);
        return true;
    }

    /**
     * 3.0.9：统一的标签修改入口（命令与 GUI 两条路径均调用），
     * 接入 guild.yml tag-change-cooldown，避免不冷却无限改标签。
     * @return 0=成功；1=无权限/公会不存在；2=冷却中（remaining 可通过 getTagCooldownRemaining 查询）
     */
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
        // 3.0.9：解散冷却落地
        recordDisbandCooldown(playerUuid);
        requests.remove(key);
        // 清理指向该公会的待处理邀请
        invites.entrySet().removeIf(entry -> entry.getValue().getGuildName().equals(key));
        // 3.0.9：清除该公会的冷却记录与保存队列残留，避免内存泄漏与解散后复活
        queuedSaves.remove(guild);
        pendingSaves.removeIf(pending -> pending == guild);
        renameCooldownUntil.remove(guild);
        tagCooldownUntil.remove(guild);
        final String guildName = guild.getName();
        // 数据库删除统一交由异步保存线程串行处理（避开主线程 I/O，且不会与在途保存相互覆盖）
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
        // 防止已在其他公会的玩家重复加入产生幽灵成员记录
        if (playerGuilds.containsKey(player.getUniqueId())) return false;
        // 3.0.9：guild.yml join-cooldown 落地（离开/被踢后需等待才能再次加入）
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
        // 3.0.9：记录加入冷却（guild.yml join-cooldown）
        applyJoinCooldown(playerUuid);
        scheduleSave(guild);
        return true;
    }

    /**
     * 3.0.9：会长解散公会后记录解散冷却（guild.yml disband-cooldown），
     * 防止反复创建/解散刷取奖励；旧版该配置为预留项完全不生效。
     */
    private void recordDisbandCooldown(UUID ownerUuid)
    {
        long seconds = plugin.getGuildConfig().getDisbandCooldown();
        if (seconds <= 0L) return;
        disbandCooldownUntil.put(ownerUuid, System.currentTimeMillis() + seconds * 1000L);
    }

    /** 会长创建公会的解散冷却剩余秒数 */
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
        // 禁止踢出会长、踢出自己，以及踢出与自己同级或更高级的成员（会长除外）
        if (targetUuid.equals(operatorUuid)) return false;
        if (targetMember.getRole() == GuildRole.OWNER) return false;
        if (!guild.getOwner().equals(operatorUuid)
            && targetMember.getRole().getLevel() >= operatorMember.getRole().getLevel()) return false;
        // 3.0.9：guild.yml kick-cooldown 落地（同一管理两次踢人之间需等待）
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
        // 3.0.9：被踢同样进入加入冷却
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
        // 已是最高职位时返回失败，避免调用方误报“提升成功”
        if (newRole == member.getRole()) return false;
        Player operator = Bukkit.getPlayer(operatorUuid);
        // 3.0.9：guild.yml promote-cooldown 落地
        long left = remainingSeconds(promoteCooldownUntil.get(targetUuid));
        if (left > 0L)
        {
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.cooldown-promote", "%seconds%", String.valueOf(left)));
            return false;
        }
        // 3.0.9：guild.yml max-officers 落地（管理员数量上限）
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
        // 会长不可降级；已是最低职位时返回失败
        if (newRole == member.getRole()) return false;
        Player operator = Bukkit.getPlayer(operatorUuid);
        // 3.0.9：guild.yml demote-cooldown 落地
        long left = remainingSeconds(demoteCooldownUntil.get(targetUuid));
        if (left > 0L)
        {
            if (operator != null) operator.sendMessage(plugin.getMessage("guild.cooldown-demote", "%seconds%", String.valueOf(left)));
            return false;
        }
        // 3.0.9：guild.yml allow-self-demote 落地（默认禁止自己降级自己）
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

    /** 统计当前管理员数量（不含会长） */
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
        // 防止同一玩家重复提交申请
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
        // 防止申请人在此期间已加入其他公会
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
        // 3.0.9：审批通过前检查加入冷却，避免绕过 join-cooldown
        long joinLeft = remainingSeconds(joinCooldownUntil.get(playerUuid));
        if (joinLeft > 0L)
        {
            Player applicant = Bukkit.getPlayer(playerUuid);
            if (applicant != null) applicant.sendMessage(plugin.getMessage("guild.cooldown-join", "%seconds%", String.valueOf(joinLeft)));
            return false;
        }
        // CopyOnWriteArrayList 的迭代器不支持 remove()，必须直接按元素移除
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
        // 3.0.9：guild.yml invite-cooldown 落地（被拒绝后需等待才能再次被邀请）
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
        // 防止玩家在邀请有效期内已加入其他公会
        if (playerGuilds.containsKey(playerUuid)) return false;
        Guild guild = guilds.get(invite.getGuildName());
        if (guild == null || !guild.canAddMember()) return false;
        // 3.0.9：接受邀请同样受 join-cooldown 约束
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
        // 3.0.9：拒绝邀请后记录 invite-cooldown
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
        // 周末/节假日经验加成（experience.yml）
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
        // 每日经验/贡献上限（0 = 无限制，experience.yml）
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

    /** 3.0.9：bank.yml deposit-permission / withdraw-permission 落地（按职位等级判定，配置值非法时视为不限制） */
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
        // 3.0.9：bank.yml deposit-permission 最低存款职位（默认 MEMBER，行为与旧版一致）
        if (bankConfig != null && !hasRequiredBankRole(guild, playerUuid, bankConfig.getDepositPermission())) return false;
        // 单次存款上限
        long maxDeposit = bankConfig.getMaxDeposit();
        if (maxDeposit > 0 && amount > maxDeposit) return false;
        // 每日存款限额
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
        // 银行余额上限
        long maxBalance = bankConfig.getMaxBalance();
        if (maxBalance > 0 && guild.getBank().getBalance() + amount > maxBalance) return false;
        // 先从玩家钱包扣款（按 currency.yml 配置的货币类型）
        GuildCurrency.CurrencyType currencyType = plugin.getCurrencyConfig().getCurrencyType();
        if (!plugin.getGuildCurrency().withdraw(playerUuid, amount, currencyType)) return false;
        // 存款税率：扣除后实际入账
        double tax = bankConfig.getDepositTax();
        long net = amount;
        if (tax > 0)
        {
            net = (long) (amount * (1.0 - Math.min(tax, 1.0)));
        }
        if (net <= 0)
        {
            // 税后为0，退还扣款
            plugin.getGuildCurrency().deposit(playerUuid, amount, currencyType);
            return false;
        }
        if (guild.getBank().deposit(net))
        {
            guild.getBank().addDepositRecord(getPlayerNameCached(playerUuid), net);
            // 3.0.9：接入 bank.yml log-large-transactions（大额流水写入后台日志便于审计）
            logLargeTransaction(bankConfig, playerUuid, guild.getName(), "DEPOSIT", amount, net);
            if (dailyLimit > 0)
            {
                dailyBankUsage.get(playerUuid)[1] += amount;
            }
            scheduleSave(guild);
            return true;
        }
        // 入账失败则退还扣款
        plugin.getGuildCurrency().deposit(playerUuid, amount, currencyType);
        return false;
    }

    public boolean withdrawFromBank(String guildName, UUID playerUuid, long amount)
    {
        if (amount <= 0L) return false;
        Guild guild = guilds.get(guildName.toLowerCase());
        if (guild == null || !guild.isMember(playerUuid) || !guild.hasPermission(playerUuid, "withdraw")) return false;
        com.guild.config.BankConfig bankConfig = plugin.getBankConfig();
        // 3.0.9：bank.yml withdraw-permission 最低取款职位（默认 OFFICER，与权限表默认一致）
        if (bankConfig != null && !hasRequiredBankRole(guild, playerUuid, bankConfig.getWithdrawPermission())) return false;
        // 单次取款上限
        long maxWithdraw = bankConfig.getMaxWithdraw();
        if (maxWithdraw > 0 && amount > maxWithdraw) return false;
        // 每日取款限额
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
        // 最低保留余额
        long minBalance = bankConfig.getMinBalance();
        if (guild.getBank().getBalance() - amount < minBalance) return false;
        // 取款税率：扣除后实际到账
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
            // 3.0.9：接入 bank.yml log-large-transactions
            logLargeTransaction(bankConfig, playerUuid, guildName, "WITHDRAW", amount, net);
            // 将资金发放到玩家钱包（按 currency.yml 配置的货币类型）
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

    /** 3.0.9：大额流水后台审计日志（bank.yml log-large-transactions / large-transaction-threshold） */
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
        // 3.0.9：改用 compute 原子更新，修复旧版 merge 后再 put 上限截断的并发竞态（多线程同时发币会少加/多加）
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

    /** 异步持久化玩家公会币余额（修复旧版重启后余额丢失问题） */
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
            // 插件已禁用时跳过异步调度
        }
    }

    /** 启动时从数据库加载所有玩家的公会币余额 */
    public void loadPlayerCurrencyBalances(Map<UUID, Long> balances)
    {
        playerGuildCurrency.putAll(balances);
    }

    /**
     * 确保玩家在数据库中有公会币账户行（用于发放 initial-balance 初始余额）。
     * 仅在玩家首次加入服务器时异步调用一次。
     */
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
        // 最高等级改为配置驱动（guild.yml max-level）
        int maxLevel = plugin.getGuildConfig().getMaxLevel();
        if (guild == null || !guild.getOwner().equals(playerUuid) || guild.getLevel() >= maxLevel) return false;
        GuildCurrency.CurrencyType currencyType = plugin.getCurrencyConfig().getCurrencyType();
        long cost = plugin.getCurrencyConfig().getLevelUpCost(guild.getLevel());
        if (!plugin.getGuildCurrency().withdraw(playerUuid, cost, currencyType)) return false;
        int oldLevel = guild.getLevel();
        guild.setLevel(oldLevel + 1);
        // 付费升级同样触发升级事件（修复旧版仅 API 路径触发、命令路径无事件的问题）
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
