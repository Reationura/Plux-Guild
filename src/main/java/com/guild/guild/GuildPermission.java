package com.guild.guild;

public enum GuildPermission
{
    MEMBER(1), OFFICER(2), OWNER(3);

    private int level;

    private GuildPermission(int n2)
    {
        this.level = n2;
    }

    public int getLevel()
    {
        return this.level;
    }

    public static GuildPermission fromLevel(int level)
    {
        for (GuildPermission perm : values())
        {
            if (perm.level == level) return perm;
        }
        return MEMBER;
    }}
