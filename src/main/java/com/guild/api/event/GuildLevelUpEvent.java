package com.guild.api.event;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GuildLevelUpEvent extends Event
{
    private static final HandlerList handlers = new HandlerList();

    private final String guildName;

    private final int oldLevel;

    private final int newLevel;

    public GuildLevelUpEvent(String guildName, int oldLevel, int newLevel)
    {
        this.guildName = guildName;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public String getGuildName()
    {
        return guildName;
    }

    public int getOldLevel()
    {
        return oldLevel;
    }

    public int getNewLevel()
    {
        return newLevel;
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
