package com.guild.listeners;
import com.guild.GuildPlugin;
import com.guild.gui.GuildMemberSettingsGUI;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import com.guild.guild.PlayerSettings;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener
{
    private final GuildPlugin plugin;

    public PlayerListener(GuildPlugin guildPlugin)
    {
        this.plugin = guildPlugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        // 异步确保公会币账户行存在（首次加入发放 initial-balance 初始余额）
        plugin.getGuildManager().ensurePlayerCurrencyRow(player.getUniqueId());
        Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
        if (guild == null) return;
        for (GuildMember member : guild.getMembers().values())
        {
            if (member.getUuid().equals(player.getUniqueId())) continue;
            PlayerSettings settings = plugin.getGuildManager().getPlayerSettings(member.getUuid());
            if (!settings.isNotifyOnlineStatus()) continue;
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online != null && online.isOnline())
            {
                online.sendMessage(ChatColor.YELLOW + player.getName() + " 上线了");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        // 3.0.8：下线时清理挂起的聊天输入与 GUI 状态，
        // 修复静态映射泄漏及玩家下次上线第一条聊天消息被误吞的 BUG
        plugin.getChatInputListener().removePendingAction(player.getUniqueId());
        InventoryListener.cleanupPlayer(player.getUniqueId());
        GuildMemberSettingsGUI.removeTargetUuid(player);
        Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
        if (guild == null) return;
        for (GuildMember member : guild.getMembers().values())
        {
            if (member.getUuid().equals(player.getUniqueId())) continue;
            PlayerSettings settings = plugin.getGuildManager().getPlayerSettings(member.getUuid());
            if (!settings.isNotifyOnlineStatus()) continue;
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online != null && online.isOnline())
            {
                online.sendMessage(ChatColor.YELLOW + player.getName() + " 下线了");
            }
        }
    }

    // 3.0.9：玩家击杀的公会经验发奖已整体迁移到 ExperienceListener.onEntityDeath 统一处理
    // （旧路径漏掉了 features.yml 的 experience 总开关与限流，并会与新监听器重复计入）
}
