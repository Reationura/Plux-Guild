package com.guild.api;
import java.util.UUID;

@FunctionalInterface
public interface GuildAPICallback<T> {
    void onComplete(boolean success, T result, String error);
}
