package com.guild.commands;
import com.guild.GuildPlugin;
import com.guild.gui.GuildBankGUI;
import com.guild.gui.GuildGUI;
import com.guild.guild.Guild;
import com.guild.guild.GuildManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuildGUICommand implements CommandExecutor
{
    private final GuildPlugin plugin;

    private final GuildManager guildManager;

    public GuildGUICommand(GuildPlugin guildPlugin)
    {
        this.plugin = guildPlugin;
        this.guildManager = guildPlugin.getGuildManager();
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray)
    {
        if (!(commandSender instanceof Player))
        {
            commandSender.sendMessage("\u6b64\u547d\u4ee4\u53ea\u80fd\u7531\u73a9\u5bb6\u6267\u884c");
            return true;
        }
        Player player = (Player)commandSender;
        if (stringArray.length > 0 && stringArray[0].equalsIgnoreCase("bank"))
        {
            Guild guild = this.guildManager.getPlayerGuild(player.getUniqueId());
            if (guild == null)
            {
                player.sendMessage(ChatColor.RED + "\u4f60\u4e0d\u5728\u4efb\u4f55\u516c\u4f1a\u4e2d");
                return true;
            }
            GuildBankGUI.openBankGUI( this.plugin, player, guild);
        }
        else
        {
            GuildGUI.openGUI( this.plugin, player);
        }
        return true;
    }}
