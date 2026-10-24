package com.guild.gui;
import com.guild.GuildPlugin;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import com.guild.guild.GuildRole;
import com.guild.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuildGUI
{
    public static void openGUI(GuildPlugin plugin, Player player)
    {
        Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
        if (guild == null)
        {
            openNoGuildGUI(plugin, player);
        }
        else
        {
            GuildMember member = guild.getMember(player.getUniqueId());
            switch (member.getRole())
            {
                case OWNER: openOwnerGUI(plugin, player, guild);
                break;
                case OFFICER: openOfficerGUI(plugin, player, guild);
                break;
                default: openMemberGUI(plugin, player, guild);
                break;
            }}

        }

        private static void openNoGuildGUI(GuildPlugin plugin, Player player)
        {
            Inventory inv = Bukkit.createInventory( new SimpleGuildGUIHolder("main"), 27, GuiBuilder.color("&6&l公 会 系 统"));
            GuiBuilder.drawDoubleBorder(inv, 3, GuiBuilder.BORDER_MATERIAL, GuiBuilder.GOLD_BORDER_MATERIAL);
            inv.setItem(13, GuiBuilder.noGuildBarrier());
            inv.setItem(11, GuiBuilder.createGuildButton());
            inv.setItem(15, GuiBuilder.viewAllGuildsButton());
            GuiBuilder.fillEmpty(inv, 27);
            player.openInventory(inv);
        }

        private static void openMemberGUI(GuildPlugin plugin, Player player, Guild guild)
        {
            Inventory inv = Bukkit.createInventory( new SimpleGuildGUIHolder("main"), 54, GuiBuilder.color("&6公会: &e" + guild.getName()));
            fillCommonLayout(inv, player, guild, false);
            fillMemberList(inv, guild);
            player.openInventory(inv);
        }

        private static void openOfficerGUI(GuildPlugin plugin, Player player, Guild guild)
        {
            Inventory inv = Bukkit.createInventory( new SimpleGuildGUIHolder("main"), 54, GuiBuilder.color("&6公会管理: &e" + guild.getName()));
            fillCommonLayout(inv, player, guild, true);
            fillMemberList(inv, guild);
            player.openInventory(inv);
        }

        private static void openOwnerGUI(GuildPlugin plugin, Player player, Guild guild)
        {
            Inventory inv = Bukkit.createInventory( new SimpleGuildGUIHolder("main"), 54, GuiBuilder.color("&6&l公会会长: &e" + guild.getName()));
            fillCommonLayout(inv, player, guild, true);
            String costStr = plugin.getGuildCurrency().formatAmount( plugin.getCurrencyConfig().getLevelUpCost(guild.getLevel()), plugin.getCurrencyConfig().getCurrencyType());
            inv.setItem(19, GuiBuilder.upgradeButton(guild.getLevel(), costStr));
            String expCost = plugin.getGuildCurrency().formatAmount( plugin.getCurrencyConfig().getExperienceCost(), plugin.getCurrencyConfig().getCurrencyType());
            inv.setItem(25, GuiBuilder.buyExpButton(expCost, plugin.getCurrencyConfig().getExperienceAmount()));
            fillMemberList(inv, guild);
            player.openInventory(inv);
        }

        public static void openGuildListGUI(GuildPlugin plugin, Player player)
        {
            Map<String, Guild> guilds = plugin.getGuildManager().getGuilds();
            if (guilds.isEmpty())
            {
                player.sendMessage(ChatColor.RED + "服务器上没有公会");
                return;
            }
            Inventory inv = Bukkit.createInventory( new SimpleGuildGUIHolder("list"), 54, GuiBuilder.color("&6&l所 有 公 会"));
            GuiBuilder.drawBorder(inv, 6, GuiBuilder.DECORATION_MATERIAL);
            int slot = 10;
            for (Guild guild : guilds.values())
            {
                if (slot >= 44) break;
                if (slot % 9 == 8)
                {
                    slot += 2;
                }
                if (slot >= 44) break;
                inv.setItem(slot, createGuildDisplayItem(guild));
                slot++;
                if (slot % 9 == 8) slot++;
            }
            inv.setItem(49, GuiBuilder.backButton());
            player.openInventory(inv);
        }

        private static void fillCommonLayout(Inventory inv, Player player, Guild guild, boolean showAdmin)
        {
            GuiBuilder.drawDoubleBorder(inv, 6, GuiBuilder.BORDER_MATERIAL, GuiBuilder.DECORATION_MATERIAL);
            inv.setItem(10, GuiBuilder.infoButton("公会信息", GuiBuilder.COLOR_INFO + "等级: " + GuiBuilder.COLOR_SECONDARY + guild.getLevel(), GuiBuilder.COLOR_INFO + "成员: " + GuiBuilder.COLOR_SECONDARY + guild.getMembers().size() + "/" + guild.getMaxMembers(), GuiBuilder.COLOR_INFO + "经验: " + GuiBuilder.COLOR_SECONDARY + guild.getExperience() + "/" + guild.getRequiredExperience()));
            inv.setItem(12, GuiBuilder.expProgressButton(guild.getExperience(), guild.getRequiredExperience(), guild.getLevel()));
            inv.setItem(13, GuiBuilder.memberListButton( GuiBuilder.COLOR_INFO + "当前: " + guild.getMembers().size() + " 人"));
            if (guild.getMotd() != null && !guild.getMotd().isEmpty())
            {
                inv.setItem(14, GuiBuilder.motdButton(guild.getMotd()));
            }
            inv.setItem(16, GuiBuilder.settingsButton());
            inv.setItem(21, GuiBuilder.contributionButton(guild.getMember(player.getUniqueId()) != null ? guild.getMember(player.getUniqueId()).getTotalContribution() : 0L));
            inv.setItem(22, GuiBuilder.bankButton(GuiBuilder.COLOR_INFO + "余额: &e" + guild.getBank().getBalance()));
            inv.setItem(23, GuiBuilder.shopButton(guild.getLevel()));
            if (showAdmin)
            {
                inv.setItem(28, GuiBuilder.manageButton());
            }
            else
            {
                inv.setItem(28, createToggleButton("邀请设置", "&7点击切换是否接受邀请"));
            }
            inv.setItem(34, createToggleButton("上下线通知", "&7点击切换上线/下线通知"));
            GuiBuilder.drawGradientLine(inv, 3, GuiBuilder.BORDER_MATERIAL, GuiBuilder.GOLD_BORDER_MATERIAL);
            inv.setItem(31, GuiBuilder.leaveButton());
            GuiBuilder.fillEmpty(inv, 54);
        }

        private static void fillMemberList(Inventory inv, Guild guild)
        {
            int slot = 45;
            for (Map.Entry<UUID, GuildMember> entry : guild.getMembers().entrySet())
            {
                if (slot > 53) break;
                UUID uuid = entry.getKey();
                GuildMember member = entry.getValue();
                boolean online = isPlayerOnline(uuid);
                String statusColor = online ? "&a在线" : "&c离线";
                String displayName = member.getNickname() != null ? member.getNickname() : getPlayerName(uuid);
                inv.setItem(slot, VersionCompat.createPlayerHead(uuid, GuiBuilder.color(member.getRole().getColor() + displayName), GuiBuilder.color(GuiBuilder.COLOR_INFO + "职位: " + member.getRole().getDisplayName()), GuiBuilder.color(GuiBuilder.COLOR_INFO + "状态: " + statusColor)));
                slot++;
            }}

            private static ItemStack createGuildDisplayItem(Guild guild)
            {
                return GuiBuilder.createItem(VersionCompat.getDiamondBlockMaterial(), GuiBuilder.COLOR_PRIMARY + guild.getName(), GuiBuilder.COLOR_INFO + "等级: " + GuiBuilder.COLOR_SECONDARY + guild.getLevel(), GuiBuilder.COLOR_INFO + "成员: " + GuiBuilder.COLOR_SECONDARY + guild.getMembers().size() + "/" + guild.getMaxMembers(), GuiBuilder.COLOR_INFO + "经验: " + GuiBuilder.COLOR_SECONDARY + guild.getExperience(), GuiBuilder.COLOR_INFO + "标签: " + (guild.getTag() != null ? GuiBuilder.COLOR_YELLOW + guild.getTag() : GuiBuilder.COLOR_DANGER + "无"));
            }

            private static ItemStack createToggleButton(String name, String... lore)
            {
                return GuiBuilder.createItem(VersionCompat.getLeverMaterial(), GuiBuilder.COLOR_SECONDARY + name, lore);
            }

            private static boolean isPlayerOnline(UUID uuid)
            {
                Player p = Bukkit.getPlayer(uuid);
                return p != null && p.isOnline();
            }

            private static String getPlayerName(UUID uuid)
            {
                // 3.0.8：优先使用管理器名称缓存，避免主线程 Bukkit.getOfflinePlayer 磁盘查询卡顿
                if (com.guild.api.GuildAPI.isApiReady())
                {
                    return com.guild.api.GuildAPI.getInstance().getPlugin().getGuildManager().getPlayerNameCached(uuid);
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                return op.getName() != null ? op.getName() : "未知";
            }}