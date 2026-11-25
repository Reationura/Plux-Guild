package com.guild.api.event;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GuildDisbandEvent extends Event
{
    private static final HandlerList handlers = new HandlerList();

    private final String guildName;

    private final OfflinePlayer disbander;

    public GuildDisbandEvent(String guildName, OfflinePlayer disbander)
    {
        this.guildName = guildName;
        this.disbander = disbander;
    }

    public String getGuildName()
    {
        return guildName;
    }

    public OfflinePlayer getDisbander()
    {
        return disbander;
    }

@Override
public HandlerList getHandlers()
    {
        return handlers;
    }

    public static HandlerList getHandlerList()
    {
        return handlers;
    }}
