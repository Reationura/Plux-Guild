package com.guild.guild;
import com.guild.GuildPlugin;
import com.guild.api.event.GuildLevelUpEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Guild
{
    private String name;
    private String tag;
    private String tagColor;
    private UUID owner;
    private int level;
    private long experience;
    private long dailyExperience;
    private String motd;
    private boolean publicGuild;
    private long createdTime;
    private final Map<UUID, GuildMember> members = new ConcurrentHashMap<>();
    private final Map<String, GuildPermission> permissions = new ConcurrentHashMap<>();
    private GuildBank bank;

    /**
     * 插件主类引用（用于读取可配置的等级/成员/经验公式）
     * 反序列化或旧调用路径下可能为 null，此时回退到内置默认值
     */
    private final GuildPlugin plugin;

    public Guild(String name, UUID owner)
    {
        this(name, owner, null);
    }

    public Guild(String name, UUID owner, GuildPlugin plugin)
    {
        this.name = name;
        this.tag = name.substring(0, Math.min(4, name.length()));
        this.tagColor = plugin != null ? plugin.getGuildConfig().getDefaultTagColor() : "&f";
        this.owner = owner;
        this.level = 1;
        this.experience = 0L;
        this.dailyExperience = 0L;
        this.motd = "";
        this.publicGuild = true;
        this.createdTime = System.currentTimeMillis();
        this.plugin = plugin;
        GuildMember ownerMember = new GuildMember(owner, GuildRole.OWNER);
        members.put(owner, ownerMember);
        initializeDefaultPermissions();
        this.bank = new GuildBank();
        // 3.0.9：接入 bank.yml max-history-records（配置未就绪时保持默认 50 条）
        if (plugin != null && plugin.getBankConfig() != null)
        {
            this.bank.setMaxRecordsPerPlayer(plugin.getBankConfig().getMaxHistoryRecords());
        }
    }

    private void initializeDefaultPermissions()
    {
        permissions.put("invite", GuildPermission.OWNER);
        permissions.put("kick", GuildPermission.OFFICER);
        permissions.put("promote", GuildPermission.OFFICER);
        permissions.put("demote", GuildPermission.OFFICER);
        permissions.put("chat", GuildPermission.MEMBER);
        permissions.put("motd", GuildPermission.OFFICER);
        permissions.put("settings", GuildPermission.OFFICER);
        permissions.put("disband", GuildPermission.OWNER);
        permissions.put("withdraw", GuildPermission.OFFICER);
    }

    public int getMaxMembers()
    {
        if (plugin == null) return 25 + level * 5;
        return plugin.getGuildConfig().getBaseMembers() + level * plugin.getGuildConfig().getMembersPerLevel();
    }

    public int getMaxLevel()
    {
        return plugin != null ? plugin.getGuildConfig().getMaxLevel() : 100;
    }

    public boolean canAddMember()
    {
        return members.size() < getMaxMembers();
    }

    public void addMember(UUID uuid, GuildRole role)
    {
        members.put(uuid, new GuildMember(uuid, role));
    }

    public void removeMember(UUID uuid)
    {
        members.remove(uuid);
    }

    public void setMemberRole(UUID uuid, GuildRole role)
    {
        GuildMember member = members.get(uuid);
        if (member != null)
        {
            member.setRole(role);
        }
    }

    public void setMemberNickname(UUID uuid, String nickname)
    {
        GuildMember member = members.get(uuid);
        if (member != null)
        {
            member.setNickname(nickname);
        }
    }

    public GuildMember getMember(UUID uuid)
    {
        return members.get(uuid);
    }

    public boolean isMember(UUID uuid)
    {
        return members.containsKey(uuid);
    }

    public synchronized void addExperience(long amount)
    {
        if (amount <= 0) return;
        experience += amount;
        dailyExperience += amount;
        checkLevelUp();
    }

    private void checkLevelUp()
    {
        int maxLevel = getMaxLevel();
        boolean leveledUp = false;
        int oldLevel = level;
        long required = getRequiredExperience();
        while (experience >= required && level < maxLevel)
        {
            // 3.0.8：防止公式配置为 0（或 clxplev 清零后 level=0）时无限循环/瞬间刷满级
            if (required <= 0) break;
            experience -= required;
            level++;
            leveledUp = true;
            required = getRequiredExperience();
        }
        if (leveledUp && plugin != null)
        {
            try
            {
                Bukkit.getPluginManager().callEvent(new GuildLevelUpEvent(name, oldLevel, level));
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    public long getRequiredExperience()
    {
        if (plugin == null)
        {
            return (long) (500.0 * level * (1 + level * 0.02));
        }
        long base = plugin.getExperienceConfig().getLevelFormulaBase();
        double growth = plugin.getExperienceConfig().getLevelFormulaGrowthRate();
        long required = (long) (base * level * (1 + level * growth));
        long maxRequired = plugin.getExperienceConfig().getLevelFormulaMaxRequired();
        if (maxRequired > 0 && required > maxRequired) required = maxRequired;
        return required;
    }

    public boolean hasPermission(UUID playerUuid, String permissionKey)
    {
        GuildMember member = members.get(playerUuid);
        if (member == null) return false;
        GuildPermission perm = permissions.get(permissionKey);
        if (perm == null) return false;
        return member.getRole().getLevel() >= perm.getLevel();
    }

    public void broadcast(String message)
    {
        for (UUID memberUuid : members.keySet())
        {
            Player onlinePlayer = Bukkit.getPlayer(memberUuid);
            if (onlinePlayer != null && onlinePlayer.isOnline())
            {
                onlinePlayer.sendMessage(message);
            }
        }
    }

    public void broadcastToOfficers(String message)
    {
        for (GuildMember member : members.values())
        {
            if (member.getRole() != GuildRole.OFFICER && member.getRole() != GuildRole.OWNER) continue;
            Player onlinePlayer = Bukkit.getPlayer(member.getUuid());
            if (onlinePlayer != null && onlinePlayer.isOnline())
            {
                onlinePlayer.sendMessage(message);
            }
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = sanitizeInput(name); }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = sanitizeInput(tag); }
    public String getTagColor() { return tagColor; }
    public void setTagColor(String tagColor) { this.tagColor = tagColor; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public long getExperience() { return experience; }
    public void setExperience(long experience) { this.experience = experience; }
    public long getDailyExperience() { return dailyExperience; }
    public void setDailyExperience(long dailyExperience) { this.dailyExperience = dailyExperience; }
    public String getMotd() { return motd; }
    public void setMotd(String motd) { this.motd = motd; }
    public boolean isPublicGuild() { return publicGuild; }
    public void setPublicGuild(boolean publicGuild) { this.publicGuild = publicGuild; }
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    public Map<UUID, GuildMember> getMembers() { return members; }
    public Map<String, GuildPermission> getPermissions() { return permissions; }
    public GuildBank getBank() { return bank; }
    public void setBank(GuildBank bank) { this.bank = bank; }

    private static String sanitizeInput(String input)
    {
        if (input == null) return "";
        return input.replace("&", "").replace("\u00a7", "");
    }
}
