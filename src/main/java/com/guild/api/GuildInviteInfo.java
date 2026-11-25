package com.guild.api;
import java.util.UUID;

public final class GuildInviteInfo
{
    private final String guildName;

    private final UUID inviterUuid;

    private final String inviterName;

    private final UUID targetUuid;

    private final String targetName;

    private final long inviteTime;

    public GuildInviteInfo(String guildName, UUID inviterUuid, String inviterName, UUID targetUuid, String targetName, long inviteTime)
    {
        this.guildName = guildName;
        this.inviterUuid = inviterUuid;
        this.inviterName = inviterName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.inviteTime = inviteTime;
    }

    public String getGuildName()
    {
        return guildName;
    }

    public UUID getInviterUuid()
    {
        return inviterUuid;
    }

    public String getInviterName()
    {
        return inviterName;
    }

    public UUID getTargetUuid()
    {
        return targetUuid;
    }

    public String getTargetName()
    {
        return targetName;
    }

    public long getInviteTime()
    {
        return inviteTime;
    }

    public long getAgeSeconds()
    {
        return (System.currentTimeMillis() - inviteTime) / 1000L;
    }

@Override
    public String toString() {
        return String.format("GuildInviteInfo{guild=%s, from=%s, to=%s}", guildName, inviterName, targetName);
    }
}
