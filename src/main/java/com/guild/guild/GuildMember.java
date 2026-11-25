package com.guild.guild;
import java.util.UUID;

public class GuildMember
{
    private final UUID uuid;

    private GuildRole role;

    private String nickname;

    private final long joinedTime;

    private long totalContribution;

    private long dailyContribution;

    private boolean muted;

    private long mutedUntil;

    public GuildMember(UUID uuid, GuildRole role)
    {
        this.uuid = uuid;
        this.role = role;
        this.joinedTime = System.currentTimeMillis();
        this.totalContribution = 0L;
        this.dailyContribution = 0L;
        this.muted = false;
        this.mutedUntil = 0L;
    }

    public void addContribution(long amount)
    {
        totalContribution += amount;
        dailyContribution += amount;
    }

    public boolean isMuted()
    {
        return muted && System.currentTimeMillis() < mutedUntil;
    }

    public long getMutedUntil()
    {
        return mutedUntil;
    }

    public void mute(long durationMs)
    {
        muted = true;
        mutedUntil = System.currentTimeMillis() + durationMs;
    }

    public void unmute()
    {
        muted = false;
        mutedUntil = 0L;
    }

    public UUID getUuid()
    {
        return uuid;
    }

    public GuildRole getRole()
    {
        return role;
    }

    public void setRole(GuildRole role)
    {
        this.role = role;
    }

    public long getJoinedTime()
    {
        return joinedTime;
    }

    public long getTotalContribution()
    {
        return totalContribution;
    }

    public void setTotalContribution(long totalContribution)
    {
        this.totalContribution = totalContribution;
    }

    public long getDailyContribution()
    {
        return dailyContribution;
    }

    public void setDailyContribution(long dailyContribution)
    {
        this.dailyContribution = dailyContribution;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }}
