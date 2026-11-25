package com.guild.cache;
import com.guild.GuildPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class PlayerNameCache implements CacheService
{
    private final GuildPlugin plugin;

    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();

    private final LongAdder cacheHits = new LongAdder();

    private final LongAdder cacheMisses = new LongAdder();

    private volatile long lastCleanupTime = System.currentTimeMillis();

    private static final long CLEANUP_INTERVAL = 300000L;

    public PlayerNameCache(GuildPlugin plugin)
    {
        this.plugin = plugin;
    }

@Override
public String getPlayerName(UUID uuid)
    {
        String cached = playerNameCache.get(uuid);
        if (cached != null)
        {
            cacheHits.increment();
            return cached;
        }
        cacheMisses.increment();
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        if (name != null && !name.isEmpty())
        {
            playerNameCache.put(uuid, name);
        }
        cleanupIfNeeded();
        return name != null ? name : "未知";
    }

@Override
public void invalidatePlayerName(UUID uuid)
    {
        playerNameCache.remove(uuid);
    }

@Override
public void clearAllCaches()
    {
        playerNameCache.clear();
    }

@Override
public long getCacheHitCount()
    {
        return cacheHits.sum();
    }

@Override
public long getCacheMissCount()
    {
        return cacheMisses.sum();
    }

@Override
public double getHitRate()
    {
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total * 100;
    }

    private void cleanupIfNeeded()
    {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL)
        {
            lastCleanupTime = now;
            playerNameCache.clear();
        }}

    }
