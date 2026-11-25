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
        plugin.getGuildManager().ensurePlayerCurrencyRow(player.getUniqueId());
        if (plugin.getUpdateChecker() != null)
        {
            plugin.getUpdateChecker().notifyOnJoin(player);
        }
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

}
