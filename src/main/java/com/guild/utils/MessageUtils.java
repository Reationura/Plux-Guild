package com.guild.utils;
import com.guild.utils.VersionCompat;
import org.bukkit.entity.Player;

public class MessageUtils
{
    public static void sendClickableMessage(Player player, String string, String string2, String string3, String string4)
    {
        VersionCompat.sendClickableMessage(player, string, string2, string3, string4);
    }

    public static void sendAcceptDeclineMessage(Player player, String string, String string2, String string3)
    {
        VersionCompat.sendAcceptDeclineMessage(player, string, string2, string3);
    }

    public static void sendAcceptMessage(Player player, String string, String string2)
    {
        VersionCompat.sendAcceptMessage(player, string, string2);
    }}
