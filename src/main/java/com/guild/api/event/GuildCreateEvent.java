package com.guild.api.event;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GuildCreateEvent extends Event implements Cancellable
{
    private static final HandlerList handlers = new HandlerList();

    private final Player creator;

    private String guildName;

    private String guildTag;

    private boolean cancelled;

    public GuildCreateEvent(Player creator, String guildName, String guildTag)
    {
        this.creator = creator;
        this.guildName = guildName;
        this.guildTag = guildTag != null ? guildTag : "";
        this.cancelled = false;
    }

    public Player getCreator()
    {
        return creator;
    }

    public String getGuildName()
    {
        return guildName;
    }

    public void setGuildName(String name)
    {
        this.guildName = name;
    }

    public String getGuildTag()
    {
        return guildTag;
    }

    public void setGuildTag(String tag)
    {
        this.guildTag = tag;
    }

@Override
public boolean isCancelled()
    {
        return cancelled;
    }

@Override
public void setCancelled(boolean cancel)
    {
        this.cancelled = cancel;
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
