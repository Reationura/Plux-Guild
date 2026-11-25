package com.guild.guild;
import java.util.UUID;

public class PlayerSettings
{
    private final UUID playerUuid;

    private boolean allowInvites;

    private boolean notifyOnlineStatus;

    public PlayerSettings(UUID uUID)
    {
        this.playerUuid = uUID;
        this.allowInvites = true;
        this.notifyOnlineStatus = true;
    }

    public UUID getPlayerUuid()
    {
        return this.playerUuid;
    }

    public boolean isAllowInvites()
    {
        return this.allowInvites;
    }

    public void setAllowInvites(boolean bl)
    {
        this.allowInvites = bl;
    }

    public boolean isNotifyOnlineStatus()
    {
        return this.notifyOnlineStatus;
    }

    public void setNotifyOnlineStatus(boolean bl)
    {
        this.notifyOnlineStatus = bl;
    }

    public void toggleInvites()
    {
        this.allowInvites = ! this.allowInvites;
    }

    public void toggleNotify()
    {
        this.notifyOnlineStatus = ! this.notifyOnlineStatus;
    }}
