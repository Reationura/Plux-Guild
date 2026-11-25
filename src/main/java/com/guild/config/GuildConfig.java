package com.guild.config;
import com.guild.GuildPlugin;
import java.util.List;

public class GuildConfig extends SimpleFileConfig
{
    public GuildConfig(GuildPlugin plugin)
    {
        super(plugin, "guild.yml");
    }

    public int getMaxLevel() { return config.getInt("max-level", 100); }
    public int getMembersPerLevel() { return config.getInt("members-per-level", 5); }
    public int getBaseMembers() { return config.getInt("base-members", 10); }
    public int getMinNameLength() { return config.getInt("min-name-length", 3); }
    public int getMaxNameLength() { return config.getInt("max-name-length", 16); }
    public int getMaxTagLength() { return config.getInt("max-tag-length", 4); }
    public String getDefaultTagColor() { return config.getString("default-tag-color", "&f"); }
    public String getNameRegex() { return config.getString("name-regex", "[a-zA-Z0-9_]+"); }
    public List<String> getBannedNames() { return config.getStringList("banned-names"); }
    public List<String> getAllowedTagColors() { return config.getStringList("allowed-tag-colors"); }
    public long getDisbandCooldown() { return config.getLong("disband-cooldown", 86400L); }
    public long getInviteCooldown() { return config.getLong("invite-cooldown", 300L); }
    public long getJoinCooldown() { return config.getLong("join-cooldown", 600L); }
    public long getPromoteCooldown() { return config.getLong("promote-cooldown", 60L); }
    public long getDemoteCooldown() { return config.getLong("demote-cooldown", 60L); }
    public long getKickCooldown() { return config.getLong("kick-cooldown", 30L); }
    public long getRenameCooldown() { return config.getLong("rename-cooldown", 3600L); }
    public long getTagChangeCooldown() { return config.getLong("tag-change-cooldown", 3600L); }
    public int getMaxOfficers() { return config.getInt("max-officers", 3); }
    public boolean isAllowSelfDemote() { return config.getBoolean("allow-self-demote", false); }
    public int getAutoKickInactiveDays() { return config.getInt("auto-kick-inactive-days", 0); }
    public int getAutoDormantDays() { return config.getInt("auto-dormant-days", 0); }

    public String getChatFormat() { return config.getString("chat.format", "&6[公会] &f[{tag}] {player}: {message}"); }

    public String getOfficerChatFormat() { return config.getString("chat.officer-format", "&9[管理] &f{player}: {message}"); }
}
