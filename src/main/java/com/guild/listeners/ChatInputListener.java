package com.guild.listeners;
import com.guild.GuildPlugin;
import com.guild.gui.GuildBankGUI;
import com.guild.gui.GuildGUI;
import com.guild.gui.GuildManageGUI;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import com.guild.guild.GuildRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatInputListener implements Listener
{
    private final GuildPlugin plugin;

    private final Map<UUID, PendingChatAction> pendingActions = new ConcurrentHashMap<>();

    public ChatInputListener(GuildPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void registerPendingAction(UUID playerUuid, String actionType, Object context)
    {
        pendingActions.put(playerUuid, new PendingChatAction(actionType, context));
    }

    public void removePendingAction(UUID playerUuid)
    {
        pendingActions.remove(playerUuid);
    }

    public boolean hasPendingAction(UUID playerUuid)
    {
        return pendingActions.containsKey(playerUuid);
    }@ EventHandler(priority = EventPriority.HIGHEST) public void onAsyncPlayerChat(AsyncPlayerChatEvent event)
    {
        Player player = event.getPlayer();
        PendingChatAction action = pendingActions.remove(player.getUniqueId());
        if (action == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        switch (action.getActionType())
        {
            case "guild_name": handleGuildNameCreation(player, input);
            break;
            case "guild_tag_for_create": handleGuildTagForCreate(player, input, action.getContext());
            break;
            case "guild_tag": handleGuildTagSetting(player, input, action.getContext());
            break;
            case "guild_motd": handleGuildMotdSetting(player, input);
            break;
            case "guild_rename": handleGuildRename(player, input);
            break;
            case "bank_deposit": handleBankDeposit(player, input);
            break;
            case "bank_withdraw": handleBankWithdraw(player, input);
            break;
            case "set_nickname": handleSetNickname(player, input, action.getContext());
            break;
            case "confirm_leave": handleConfirmLeave(player, input, action.getContext());
            break;
            case "confirm_disband": handleConfirmDisband(player, input, action.getContext());
            break;
            default: break;
        }}

        private void handleGuildNameCreation(Player player, String input)
        {
            if (input.equalsIgnoreCase("cancel"))
            {
                player.sendMessage(ChatColor.YELLOW + "已取消创建公会");
                return;
            }
            int minLen = plugin.getGuildConfig().getMinNameLength();
            int maxLen = plugin.getGuildConfig().getMaxNameLength();
            if (input.length() < minLen || input.length() > maxLen)
            {
                player.sendMessage(plugin.getMessage("guild.name-length-invalid") .replace("%min%", String.valueOf(minLen)) .replace("%max%", String.valueOf(maxLen)));
                return;
            }
            // 正则改为配置驱动（guild.yml name-regex），修复旧版硬编码导致配置失效
            String regex = plugin.getGuildConfig().getNameRegex();
            if (regex == null || regex.isEmpty())
            {
                regex = "[a-zA-Z0-9\u4e00-\u9fa5_]+";
            }
            if (!input.matches(regex))
            {
                player.sendMessage(plugin.getMessage("guild.name-chars-invalid"));
                return;
            }
            if (plugin.getGuildManager().getGuild(input) != null)
            {
                player.sendMessage(plugin.getMessage("guild.already-exists"));
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "========== 创建公会 ==========");
            player.sendMessage(ChatColor.GOLD + "公会名称: " + input);
            player.sendMessage(ChatColor.GOLD + "请输入公会标签（1-6字符）");
            player.sendMessage(ChatColor.GRAY + "输入 'cancel' 取消创建");
            registerPendingAction(player.getUniqueId(), "guild_tag_for_create", input);
        }

        private void handleGuildTagForCreate(Player player, String input, Object context)
        {
            String guildName = (String) context;
            if (input.equalsIgnoreCase("cancel"))
            {
                player.sendMessage(ChatColor.YELLOW + "已取消创建公会");
                return;
            }
            int maxTagLen = plugin.getGuildConfig().getMaxTagLength();
            if (input.length() < 1 || input.length() > maxTagLen)
            {
                player.sendMessage(plugin.getMessage("guild.tag-length-invalid") .replace("%max%", String.valueOf(maxTagLen)));
                player.sendMessage(ChatColor.GOLD + "请重新输入公会标签（1-" + maxTagLen + "字符）");
                registerPendingAction(player.getUniqueId(), "guild_tag_for_create", guildName);
                return;
            }
            // 3.0.8：创建流程切回主线程执行，GuildCreateEvent 不能在异步线程触发
            final String finalTag = input;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Guild guild = plugin.getGuildManager().createGuild(guildName, player);
                if (guild == null)
                {
                    if (plugin.getGuildManager().getGuild(guildName) != null)
                    {
                        player.sendMessage(plugin.getMessage("guild.already-exists"));
                    }
                    return;
                }
                guild.setTag(finalTag);
                plugin.getGuildManager().scheduleSavePublic(guild);
                player.sendMessage(plugin.getMessage("guild.created").replace("%name%", guildName));
                player.sendMessage(ChatColor.GREEN + "公会标签已设置为: " + finalTag);
                GuildGUI.openGUI(plugin, player);
            });
        }

        private void handleGuildTagSetting(Player player, String input, Object context)
        {
            int maxTagLen = plugin.getGuildConfig().getMaxTagLength();
            if (input.length() < 1 || input.length() > maxTagLen)
            {
                player.sendMessage(plugin.getMessage("guild.tag-length-invalid") .replace("%max%", String.valueOf(maxTagLen)));
                return;
            }
            Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
            if (guild == null || !guild.hasPermission(player.getUniqueId(), "settings"))
            {
                player.sendMessage(plugin.getMessage("guild.no-permission"));
                return;
            }
            // 3.0.9：标签修改统一走管理器，使 guild.yml tag-change-cooldown 生效
            if (plugin.getGuildManager().changeTag(guild, input, player.getUniqueId()) == 0)
            {
                player.sendMessage(plugin.getMessage("guild.tag-updated").replace("%tag%", input));
            }

            final Player finalPlayer = player;

            final Guild finalGuild = guild;
            Bukkit.getScheduler().runTask(plugin, () -> GuildManageGUI.openGUI(plugin, finalPlayer, finalGuild));
        }

        private void handleGuildMotdSetting(Player player, String input)
        {
            if (input.length() > 100)
            {
                player.sendMessage(plugin.getMessage("guild.motd-too-long"));
                return;
            }
            Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
            if (guild == null || !guild.hasPermission(player.getUniqueId(), "motd"))
            {
                player.sendMessage(plugin.getMessage("guild.no-permission"));
                return;
            }
            guild.setMotd(input);
            plugin.getGuildManager().scheduleSavePublic(guild);
            player.sendMessage(plugin.getMessage("guild.motd-updated"));

            final Player finalPlayer = player;

            final Guild finalGuild = guild;
            Bukkit.getScheduler().runTask(plugin, () -> GuildManageGUI.openGUI(plugin, finalPlayer, finalGuild));
        }

        // 3.0.8：新增 GUI 重命名的聊天输入处理（旧版重命名按钮错误地走了公告处理）
        private void handleGuildRename(Player player, String input)
        {
            if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("C"))
            {
                player.sendMessage(ChatColor.YELLOW + "已取消重命名");
                return;
            }
            Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
            if (guild == null)
            {
                player.sendMessage(plugin.getMessage("guild.not-in-guild"));
                return;
            }
            if (!guild.getOwner().equals(player.getUniqueId()))
            {
                player.sendMessage(ChatColor.RED + "只有会长可以重命名公会");
                return;
            }
            int minLen = plugin.getGuildConfig().getMinNameLength();
            int maxLen = plugin.getGuildConfig().getMaxNameLength();
            if (input.length() < minLen || input.length() > maxLen)
            {
                player.sendMessage(plugin.getMessage("guild.name-length-invalid") .replace("%min%", String.valueOf(minLen)) .replace("%max%", String.valueOf(maxLen)));
                return;
            }
            String regex = plugin.getGuildConfig().getNameRegex();
            if (regex == null || regex.isEmpty())
            {
                regex = "[a-zA-Z0-9\u4e00-\u9fa5_]+";
            }
            if (!input.matches(regex))
            {
                player.sendMessage(plugin.getMessage("guild.name-chars-invalid"));
                return;
            }
            for (String banned : plugin.getGuildConfig().getBannedNames())
            {
                if (input.toLowerCase().contains(banned.toLowerCase()))
                {
                    player.sendMessage(ChatColor.RED + "该公会名称不被允许");
                    return;
                }
            }
            if (input.equalsIgnoreCase(guild.getName()))
            {
                player.sendMessage(ChatColor.YELLOW + "新名称与当前名称相同");
                return;
            }
            if (plugin.getGuildManager().getGuild(input) != null)
            {
                player.sendMessage(plugin.getMessage("guild.already-exists"));
                return;
            }
            final String oldName = guild.getName();
            final UUID operatorUuid = player.getUniqueId();
            // 改名在主线程执行，原地迁移数据（成员/等级/银行不丢失）
            Bukkit.getScheduler().runTask(plugin, () ->
            {
                if (plugin.getGuildManager().renameGuild(oldName, input, operatorUuid))
                {
                    player.sendMessage(ChatColor.GREEN + "公会已重命名为: " + input);
                    GuildGUI.openGUI(plugin, player);
                }
                else
                {
                    player.sendMessage(ChatColor.RED + "重命名失败");
                }});
        }

        private void handleBankDeposit(Player player, String input)
        {
            long amount = parsePositiveLong(player, input);
            if (amount <= 0L) return;
            Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
            if (guild == null)
            {
                player.sendMessage(plugin.getMessage("guild.not-in-guild"));
                return;
            }
            if (plugin.getGuildManager().depositToBank(guild.getName(), player.getUniqueId(), amount))
            {
                player.sendMessage(plugin.getMessage("bank.deposit-success") .replace("%amount%", String.valueOf(amount)));
            }
            else
            {
                player.sendMessage(plugin.getMessage("bank.deposit-failed"));
            }

            final Player finalPlayer = player;

            final Guild finalGuild = guild;
            Bukkit.getScheduler().runTask(plugin, () -> GuildBankGUI.openBankGUI(plugin, finalPlayer, finalGuild));
        }

        private void handleBankWithdraw(Player player, String input)
        {
            long amount = parsePositiveLong(player, input);
            if (amount <= 0L) return;
            Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
            if (guild == null)
            {
                player.sendMessage(plugin.getMessage("guild.not-in-guild"));
                return;
            }
            if (plugin.getGuildManager().withdrawFromBank(guild.getName(), player.getUniqueId(), amount))
            {
                player.sendMessage(plugin.getMessage("bank.withdraw-success") .replace("%amount%", String.valueOf(amount)));
            }
            else
            {
                player.sendMessage(plugin.getMessage("bank.withdraw-failed"));
            }

            final Player finalPlayer = player;

            final Guild finalGuild = guild;
            Bukkit.getScheduler().runTask(plugin, () -> GuildBankGUI.openBankGUI(plugin, finalPlayer, finalGuild));
        }

        private long parsePositiveLong(Player player, String input)
        {
            long amount;
            try
            {
                amount = Long.parseLong(input);
            }
            catch (NumberFormatException e)
            {
                player.sendMessage(plugin.getMessage("bank.invalid-amount"));
                return -1L;
            }
            if (amount <= 0L)
            {
                player.sendMessage(plugin.getMessage("bank.amount-positive"));
                return -1L;
            }
            return amount;
        }

        private void handleSetNickname(Player player, String input, Object context)
        {
            if (input.equalsIgnoreCase("C"))
            {
                player.sendMessage(ChatColor.YELLOW + "已取消设置昵称");
                return;
            }
            if (input.length() > 16)
            {
                player.sendMessage(ChatColor.RED + "昵称长度不能超过16个字符");
                return;
            }
            UUID targetUuid = (UUID) context;
            Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
            if (guild == null)
            {
                player.sendMessage(ChatColor.RED + "你不在公会中");
                return;
            }
            GuildMember member = guild.getMember(player.getUniqueId());
            if (member == null || member.getRole() != GuildRole.OFFICER && member.getRole() != GuildRole.OWNER)
            {
                player.sendMessage(ChatColor.RED + "你没有权限设置昵称");
                return;
            }
            guild.setMemberNickname(targetUuid, input);
            // 3.0.8：昵称修改及时入库，避免重启后丢失
            plugin.getGuildManager().scheduleSavePublic(guild);
            String targetName = plugin.getGuildManager().getPlayerNameCached(targetUuid);
            player.sendMessage(ChatColor.GREEN + "已将 " + targetName + " 的昵称设置为: " + input);
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null)
            {
                targetPlayer.sendMessage(ChatColor.GREEN + "你的公会昵称已被设置为: " + input);
            }

            final Player finalPlayer = player;
            Bukkit.getScheduler().runTask(plugin, () -> GuildGUI.openGUI(plugin, finalPlayer));
        }

        private void handleConfirmLeave(Player player, String input, Object context)
        {
            if (!input.equalsIgnoreCase("confirm"))
            {
                player.sendMessage(ChatColor.YELLOW + "已取消离开公会");
                Bukkit.getScheduler().runTask(plugin, () -> GuildGUI.openGUI(plugin, player));
                return;
            }
            String guildName = (String) context;
            // 3.0.8：离开处理切回主线程，GuildLeaveEvent 不能在异步线程触发
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.getGuildManager().leaveGuild(player.getUniqueId()))
                {
                    player.sendMessage(ChatColor.GREEN + "你已成功离开公会");
                }
                else
                {
                    player.sendMessage(ChatColor.RED + "离开公会失败");
                }
                GuildGUI.openGUI(plugin, player);
            });
        }

        private void handleConfirmDisband(Player player, String input, Object context)
        {
            if (!input.equalsIgnoreCase("confirm"))
            {
                player.sendMessage(ChatColor.YELLOW + "已取消解散公会");
                Bukkit.getScheduler().runTask(plugin, () -> GuildGUI.openGUI(plugin, player));
                return;
            }
            String guildName = (String) context;
            // 3.0.8：解散处理切回主线程，GuildDisbandEvent 不能在异步线程触发
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.getGuildManager().disbandGuild(guildName, player.getUniqueId()))
                {
                    player.sendMessage(ChatColor.GREEN + "公会已成功解散");
                }
                else
                {
                    player.sendMessage(ChatColor.RED + "解散公会失败");
                }
                GuildGUI.openGUI(plugin, player);
            });
        }

        public static class PendingChatAction
        {
            private final String actionType;

            private final Object context;

            public PendingChatAction(String actionType, Object context)
            {
                this.actionType = actionType;
                this.context = context;
            }

            public String getActionType()
            {
                return actionType;
            }

            public Object getContext()
            {
                return context;
            }}

        }