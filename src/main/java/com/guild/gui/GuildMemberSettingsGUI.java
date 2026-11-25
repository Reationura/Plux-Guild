package com.guild.gui;
import com.guild.GuildPlugin;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import com.guild.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuildMemberSettingsGUI
{
    private static final Map<UUID, UUID> targetPlayerMap = new ConcurrentHashMap<>();

    public static void openGUI(GuildPlugin plugin, Player player, Guild guild, UUID targetUuid)
    {
        GuildMember member = guild.getMember(targetUuid);
        if (member == null)
        {
            player.sendMessage(GuiBuilder.color("&c该玩家不在公会中"));
            return;
        }
        targetPlayerMap.put(player.getUniqueId(), targetUuid);
        String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
        Inventory inv = Bukkit.createInventory( new SimpleGuildGUIHolder("member_settings"), 27, GuiBuilder.color("&6&l玩家设置: &e" + targetName));
        GuiBuilder.drawBorder(inv, 3, GuiBuilder.DECORATION_MATERIAL);
        inv.setItem(13, VersionCompat.createPlayerHead(targetUuid, member.getRole().getColor() + targetName, GuiBuilder.color(GuiBuilder.COLOR_INFO + "职位: " + member.getRole().getDisplayName())));
        inv.setItem(10, GuiBuilder.createItem( VersionCompat.getNameTagMaterial(), GuiBuilder.COLOR_SECONDARY + "设置昵称", GuiBuilder.COLOR_INFO + "为该成员设置公会内昵称", "", GuiBuilder.COLOR_INFO + "点击后在聊天栏输入新昵称", GuiBuilder.COLOR_INFO + "1分钟内有效，输入 C 取消"));
        inv.setItem(12, GuiBuilder.createItem( VersionCompat.getGoldenHelmetMaterial(), GuiBuilder.COLOR_SUCCESS + "设为管理员", GuiBuilder.COLOR_INFO + "将此玩家提升为管理员职位", "", GuiBuilder.COLOR_YELLOW + "需要: 成员 → 管理员"));
        inv.setItem(14, GuiBuilder.createItem( VersionCompat.getIronHelmetMaterial(), GuiBuilder.COLOR_SECONDARY + "降为成员", GuiBuilder.COLOR_INFO + "将此玩家降为普通成员", "", GuiBuilder.COLOR_YELLOW + "需要: 管理员 → 成员"));
        inv.setItem(16, GuiBuilder.createItem( VersionCompat.getBarrierMaterial(), GuiBuilder.COLOR_DANGER + "踢出公会", GuiBuilder.COLOR_INFO + "将此玩家移出公会", "", GuiBuilder.COLOR_DANGER + "警告: 此操作不可撤销!", GuiBuilder.COLOR_DANGER + "需要二次确认"));
        inv.setItem(22, GuiBuilder.backButton());
        player.openInventory(inv);
    }

    public static UUID getTargetUuid(Player player)
    {
        return targetPlayerMap.get(player.getUniqueId());
    }

    public static void removeTargetUuid(Player player)
    {
        targetPlayerMap.remove(player.getUniqueId());
    }}
