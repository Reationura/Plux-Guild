package com.guild.cache;
import java.util.Map;
import java.util.UUID;

public interface CacheService
{
    String getPlayerName(UUID uuid);
    void invalidatePlayerName(UUID uuid);
    void clearAllCaches();
    long getCacheHitCount();
    long getCacheMissCount();
    double getHitRate();
}
