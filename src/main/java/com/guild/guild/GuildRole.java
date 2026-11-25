package com.guild.guild;

public enum GuildRole
{
    MEMBER("\u6210\u5458", 1, "&7"), OFFICER("\u7ba1\u7406\u5458", 2, "&a"), OWNER("\u4f1a\u957f", 3, "&6");

    private String displayName;

    private int level;

    private String color;

    private GuildRole(String string2, int n2, String string3)
    {
        this.displayName = string2;
        this.level = n2;
        this.color = string3;
    }

    public String getDisplayName()
    {
        return this.displayName;
    }

    public int getLevel()
    {
        return this.level;
    }

    public String getColor()
    {
        return this.color;
    }

    public GuildRole promote()
    {
        switch ( this)
        {
            case MEMBER:
            {
                return OFFICER;
            }}

            return this;
        }

        public GuildRole demote()
        {
            switch ( this)
            {
                case OFFICER:
                {
                    return MEMBER;
                }}

                return this;
            }}
