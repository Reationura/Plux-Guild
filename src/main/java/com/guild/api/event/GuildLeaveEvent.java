package com.guild.api.event;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GuildLeaveEvent extends Event implements Cancellable
{
    private static final HandlerList handlers = new HandlerList();

    private final Player player;

    private final String guildName;

    private boolean cancelled;

    public GuildLeaveEvent(Player player, String guildName)
    {
        this.player = player;
        this.guildName = guildName;
        this.cancelled = false;
    }

    public Player getPlayer()
    {
        return player;
    }

    public String getGuildName()
    {
        return guildName;
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
