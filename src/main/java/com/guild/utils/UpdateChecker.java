package com.guild.utils;
import com.guild.GuildPlugin;
import org.bukkit.Bukkit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class UpdateChecker
{
    private final GuildPlugin plugin;

    private volatile String latestVersion;

    private volatile boolean updateAvailable;

    private volatile boolean lastCheckFailed;

    public UpdateChecker(GuildPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void start()
    {
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) return;
        long delayTicks = 100L;
        long intervalHours = Math.max(1L, plugin.getConfig().getLong("update-check.interval-hours", 6L));
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkNow, delayTicks, intervalHours * 72000L);
    }

    public void checkNow()
    {
        List<String> mirrors = plugin.getConfig().getStringList("update-check.mirrors");
        if (mirrors.isEmpty())
        {
            mirrors = java.util.Arrays.asList(
                "https://raw.githubusercontent.com/Reationura/Plux-Guild/refs/heads/main/version",
                "https://ghfast.top/https://raw.githubusercontent.com/Reationura/Plux-Guild/refs/heads/main/version",
                "https://gh-proxy.com/https://raw.githubusercontent.com/Reationura/Plux-Guild/refs/heads/main/version");
        }
        String current = plugin.getDescription().getVersion();
        for (String mirror : mirrors)
        {
            if (mirror == null || mirror.trim().isEmpty()) continue;
            String remote = fetchVersion(mirror.trim());
            if (remote == null) continue;
            latestVersion = remote;
            lastCheckFailed = false;
            if (compareVersions(remote, current) > 0)
            {
                updateAvailable = true;
                plugin.getLogger().info("发现新版本: v" + remote + " (当前 v" + current + ") 下载地址: https://github.com/Reationura/Plux-Guild/releases");
                notifyOnlineAdmins(remote, current);
            }
            else
            {
                updateAvailable = false;
                plugin.getLogger().info("版本检查完成: 已是最新版本 (v" + current + ")");
            }
            return;
        }
        lastCheckFailed = true;
        plugin.getLogger().warning("版本检查失败: 所有更新源均无法访问（可在 config.yml update-check.mirrors 配置代理镜像）");
    }

    private String fetchVersion(String url)
    {
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "Guild-Plugin/" + plugin.getDescription().getVersion());
            int code = connection.getResponseCode();
            if (code != 200) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line = reader.readLine();
                if (line == null) return null;
                line = line.trim();
                if (line.startsWith("v") || line.startsWith("V"))
                {
                    line = line.substring(1);
                }
                return line.isEmpty() ? null : line;
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("更新源不可达: " + url + " (" + e.getClass().getSimpleName() + ")");
            return null;
        }
        finally
        {
            if (connection != null) connection.disconnect();
        }
    }

    private void notifyOnlineAdmins(String latest, String current)
    {
        String permission = plugin.getConfig().getString("update-check.notify-permission", "guild.admin");
        String message = plugin.getMessage("update.available", "%latest%", latest, "%current%", current);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers())
        {
            if (player.hasPermission(permission))
            {
                player.sendMessage(message);
            }
        }
    }

    public void notifyOnJoin(org.bukkit.entity.Player player)
    {
        if (!updateAvailable) return;
        String permission = plugin.getConfig().getString("update-check.notify-permission", "guild.admin");
        if (!player.hasPermission(permission)) return;
        player.sendMessage(plugin.getMessage("update.available", "%latest%", latestVersion, "%current%", plugin.getDescription().getVersion()));
    }

    public static int compareVersions(String a, String b)
    {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        for (int i = 0; i < 3; i++)
        {
            if (pa[i] != pb[i]) return pa[i] > pb[i] ? 1 : -1;
        }
        return 0;
    }

    private static int[] parseVersion(String version)
    {
        int[] result = new int[3];
        if (version == null) return result;
        String cleaned = version.trim();
        int dash = cleaned.indexOf('-');
        if (dash > 0) cleaned = cleaned.substring(0, dash);
        String[] parts = cleaned.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++)
        {
            try
            {
                result[i] = Integer.parseInt(parts[i].trim());
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        return result;
    }

    public boolean isUpdateAvailable()
    {
        return updateAvailable;
    }

    public String getLatestVersion()
    {
        return latestVersion;
    }

    public boolean isLastCheckFailed()
    {
        return lastCheckFailed;
    }
}
