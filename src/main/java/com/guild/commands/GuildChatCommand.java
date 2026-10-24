package com.guild.commands;
import com.guild.GuildPlugin;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuildChatCommand implements CommandExecutor
{
    private final GuildPlugin plugin;

    public GuildChatCommand(GuildPlugin guildPlugin)
    {
        this.plugin = guildPlugin;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray)
    {
        if (!(commandSender instanceof Player))
        {
            commandSender.sendMessage("\u6b64\u547d\u4ee4\u53ea\u80fd\u7531\u73a9\u5bb6\u6267\u884c");
            return true;
        }
        Player player = (Player)commandSender;
        if (stringArray.length == 0)
        {
            player.sendMessage(ChatColor.RED + "\u7528\u6cd5: /guildchat <\u6d88\u606f>");
            return true;
        }
        Guild guild = this.plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
        if (guild == null)
        {
            player.sendMessage(ChatColor.RED + "\u4f60\u4e0d\u5728\u4efb\u4f55\u516c\u4f1a\u4e2d");
            return true;
        }
        if (!guild.hasPermission(player.getUniqueId(), "chat"))
        {
            player.sendMessage(ChatColor.RED + "\u4f60\u4e0d\u80fd\u5728\u516c\u4f1a\u804a\u5929\u9891\u9053\u53d1\u8a00");
            return true;
        }
        GuildMember member = guild.getMember(player.getUniqueId());
        // 3.0.8：补充判空，修复“在公会索引但不在成员表”时 getMember(...).isMuted() 直接 NPE 的 BUG
        if (member == null)
        {
            player.sendMessage(ChatColor.RED + "\u6570\u636e\u5f02\u5e38\uff0c\u8bf7\u91cd\u65b0\u52a0\u5165\u516c\u4f1a");
            return true;
        }
        if (member.isMuted())
        {
            player.sendMessage(ChatColor.RED + "\u4f60\u5df2\u88ab\u7981\u8a00\uff0c\u65e0\u6cd5\u5728\u516c\u4f1a\u9891\u9053\u53d1\u8a00");
            return true;
        }
        String message = String.join(" ", stringArray);
        // 3.0.8：聊天格式统一改用 guild.yml chat.format 配置驱动，与 /guild chat 保持一致
        String format = plugin.getGuildConfig().getChatFormat();
        format = format.replace("{tag_color}", guild.getTagColor())
            .replace("{tag}", guild.getTag())
            .replace("{player}", player.getDisplayName())
            .replace("{message}", message);
        guild.broadcast(ChatColor.translateAlternateColorCodes('&', format));
        return true;
    }}