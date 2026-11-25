package com.guild.api.v2;
import com.guild.GuildPlugin;

public final class GuildApiV2Provider
{
    private static volatile GuildApiV2 instance;

    private GuildApiV2Provider()
    {
    }

    public static void init(GuildPlugin plugin)
    {
        instance = new GuildApiV2Impl(plugin);
    }

    public static GuildApiV2 get()
    {
        return instance;
    }

    public static GuildApiV2 require()
    {
        GuildApiV2 api = instance;
        if (api == null || !api.isReady())
        {
            throw new IllegalStateException("Guild API v2 尚未就绪，请确保 Guild 插件已启用。可通过 GuildApiV2Provider.get() 判空后使用。");
        }
        return api;
    }

    public static boolean isAvailable()
    {
        GuildApiV2 api = instance;
        return api != null && api.isReady();
    }
}
