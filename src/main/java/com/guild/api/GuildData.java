package com.guild.api;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import com.guild.guild.GuildRole;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.stream.Collectors;

public final class GuildData
{
    private final Guild guild;

    public GuildData(Guild guild)
    {
        this.guild = Objects.requireNonNull(guild, "guild cannot be null");
    }

    public String getName()
    {
        return guild.getName();
    }

    public String getTag()
    {
        return guild.getTag();
    }

    public String getTagColor()
    {
        return guild.getTagColor();
    }

    public String getDisplayTag()
    {
        return guild.getTagColor() + guild.getTag();
    }

    public UUID getOwnerUuid()
    {
        return guild.getOwner();
    }

    public String getOwnerName()
    {
        OfflinePlayer op = Bukkit.getOfflinePlayer(guild.getOwner());
        return op != null ? op.getName() : "未知";
    }

    public int getLevel()
    {
        return guild.getLevel();
    }

    public long getExperience()
    {
        return guild.getExperience();
    }

    public long getRequiredExperience()
    {
        return guild.getRequiredExperience();
    }

    public long getDailyExperience()
    {
        return guild.getDailyExperience();
    }

    public String getMotd()
    {
        return guild.getMotd();
    }

    public boolean isPublicGuild()
    {
        return guild.isPublicGuild();
    }

    public long getCreatedTime()
    {
        return guild.getCreatedTime();
    }

    public int getMemberCount()
    {
        return guild.getMembers().size();
    }

    public int getMaxMembers()
    {
        return guild.getMaxMembers();
    }

    public boolean canAddMember()
    {
        return guild.canAddMember();
    }

    public double getCapacityUsage()
    {
        return (double) guild.getMembers().size() / guild.getMaxMembers();
    }

    public Set<UUID> getMemberUuids()
    {
        return Collections.unmodifiableSet( new LinkedHashSet<>(guild.getMembers().keySet()));
    }

    public List<UUID> getOnlineMembers()
    {
        return guild.getMembers().keySet().stream() .filter(uuid ->
        {
            Player p = Bukkit.getPlayer(uuid);
            return p != null && p.isOnline();
        }) .collect(Collectors.toList());
    }

    public int getOnlineCount()
    {
        return (int) guild.getMembers().keySet().stream() .filter(uuid ->
        {
            Player p = Bukkit.getPlayer(uuid);
            return p != null && p.isOnline();
        }) .count();
    }

    public long getBankBalance()
    {
        return guild.getBank().getBalance();
    }

    public boolean hasPermission(UUID playerUuid, String permissionKey)
    {
        return guild.hasPermission(playerUuid, permissionKey);
    }

    public boolean isOwner(UUID playerUuid)
    {
        return guild.getOwner().equals(playerUuid);
    }

    public boolean isOfficerOrAbove(UUID playerUuid)
    {
        GuildMember member = guild.getMember(playerUuid);
        if (member == null) return false;
        GuildRole role = member.getRole();
        return role.name().equals("OWNER") || role.name().equals("OFFICER");
    }

    public Guild getInternalGuild()
    {
        return guild;
    }

@Override
public boolean equals(Object o)
    {
        if ( this == o) return true;
        if (!(o instanceof GuildData)) return false;
        return guild.getName().equalsIgnoreCase(((GuildData) o).guild.getName());
    }

@Override
public int hashCode()
    {
        return guild.getName().toLowerCase().hashCode();
    }

@Override
    public String toString() {
        return String.format("GuildData{name=%s, tag=%s, level=%d, members=%d/%d}", getName(), getTag(), getLevel(), getMemberCount(), getMaxMembers());
    }
}
