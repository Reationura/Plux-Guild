package com.guild.utils;
import com.guild.GuildPlugin;
import org.bukkit.ChatColor;

public class LangUtils
{
    private static GuildPlugin plugin;

    public static void initialize(GuildPlugin guildPlugin)
    {
        plugin = guildPlugin;
    }

    public static String get(String string)
    {
        if (plugin == null)
        {
            return string;
        }
        String string2 = plugin.getMessage(string);
        return ChatColor.translateAlternateColorCodes((char)'&', (String)string2);
    }

    public static String get(String string, String ... stringArray)
    {
        if (plugin == null)
        {
            return string;
        }
        String string2 = plugin.getMessage(string, stringArray);
        return ChatColor.translateAlternateColorCodes((char)'&', (String)string2);
    }

    public static String prefix(String string)
    {
        return LangUtils.get("messages.prefix") + LangUtils.get(string);
    }

    public static String prefix(String string, String ... stringArray)
    {
        return LangUtils.get("messages.prefix") + LangUtils.get(string, stringArray);
    }}
