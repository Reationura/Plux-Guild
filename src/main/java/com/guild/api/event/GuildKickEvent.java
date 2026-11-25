package com.guild.api.event;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class GuildKickEvent extends Event implements Cancellable
{
    private static final HandlerList handlers = new HandlerList();

    private final String guildName;

    private final UUID kickedUuid;

    private final OfflinePlayer kicker;

    private boolean cancelled;

    public GuildKickEvent(String guildName, UUID kickedUuid, OfflinePlayer kicker)
    {
        this.guildName = guildName;
        this.kickedUuid = kickedUuid;
        this.kicker = kicker;
        this.cancelled = false;
    }

    public String getGuildName()
    {
        return guildName;
    }

    public UUID getKickedUuid()
    {
        return kickedUuid;
    }

    public OfflinePlayer getKicker()
    {
        return kicker;
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
