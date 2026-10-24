package com.guild.listeners;
import com.guild.GuildPlugin;
import com.guild.currency.GuildCurrency;
import com.guild.gui.GuildBankGUI;
import com.guild.gui.GuildGUI;
import com.guild.gui.GuildGUIHolder;
import com.guild.gui.GuildManageGUI;
import com.guild.gui.GuildMemberSettingsGUI;
import com.guild.gui.GuildShopGUI;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import com.guild.guild.GuildRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryListener implements Listener
{
    private final GuildPlugin plugin;

    private ChatInputListener chatInputListener;

    private static final Map<UUID, String> PENDING_GUILD_NAME = new ConcurrentHashMap<>();

    private static final Map<UUID, String> ANVIL_TEXT_CACHE = new ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> REOPENING_ANVIL = new ConcurrentHashMap<>();

    private static final String GUI_MAIN = "main";

    private static final String GUI_LIST = "list";

    private static final String GUI_BANK = "bank";

    private static final String GUI_MANAGE = "manage";

    private static final String GUI_MEMBER_SETTINGS = "member_settings";

    private static final String GUI_SHOP = "shop";

    public InventoryListener(GuildPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void setChatInputListener(ChatInputListener listener)
    {
        this.chatInputListener = listener;
    }

    private boolean isGuildInventory(Inventory inventory)
    {
        return inventory != null && inventory.getHolder() instanceof GuildGUIHolder;
    }

    private String getGuiType(Inventory inventory)
    {
        if (inventory.getHolder() instanceof GuildGUIHolder)
        {
            return ((GuildGUIHolder) inventory.getHolder()).getGuiType();
        }
        return null;
    }@ EventHandler(priority = EventPriority.HIGHEST) public void onInventoryClick(InventoryClickEvent event)
    {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (handleAnvilInput(event, player)) return;
        Inventory topInv = event.getInventory();
        if (!isGuildInventory(topInv)) return;
        event.setCancelled(true);
        player.updateInventory();
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;
        if (clickedInv.getType() == InventoryType.PLAYER || event.getSlotType() == SlotType.QUICKBAR) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
        switch (getGuiType(topInv))
        {
            case GUI_BANK: handleBankClick(player, guild, displayName);
            break;
            case GUI_LIST: handleGuildListClick(player, displayName);
            break;
            case GUI_MANAGE: handleManageClick(player, displayName);
            break;
            case GUI_MEMBER_SETTINGS: handleMemberSettingsClick(player, displayName);
            break;
            case GUI_SHOP: handleShopClick(player, displayName);
            break;
            default: if (guild != null)
            {
                handleMainGUIClick(player, guild, displayName);
            }
            else
            {
                handleNoGuildClick(player, displayName);
            }
            break;
        }}

        @ EventHandler(priority = EventPriority.HIGHEST) public void onInventoryDrag(InventoryDragEvent event)
        {
            if (!(event.getWhoClicked() instanceof Player)) return;
            if (!isGuildInventory(event.getInventory())) return;
            event.setCancelled(true);
            ((Player) event.getWhoClicked()).updateInventory();
        }@ EventHandler(priority = EventPriority.HIGHEST) public void onInventoryInteract(InventoryInteractEvent event)
        {
            if (!(event.getWhoClicked() instanceof Player)) return;
            if (!isGuildInventory(event.getInventory())) return;
            event.setCancelled(true);
            ((Player) event.getWhoClicked()).updateInventory();
        }@ EventHandler(priority = EventPriority.HIGHEST) public void onInventoryMoveItem(InventoryMoveItemEvent event)
        {
            if (isGuildInventory(event.getSource()) || isGuildInventory(event.getDestination()))
            {
                event.setCancelled(true);
            }}

            @ EventHandler(priority = EventPriority.MONITOR) public void onInventoryClose(InventoryCloseEvent event)
            {
                if (!(event.getPlayer() instanceof Player)) return;
                if (event.getInventory() == null || event.getInventory().getType() != InventoryType.ANVIL) return;
                Player player = (Player) event.getPlayer();
                UUID uuid = player.getUniqueId();
                if (uuid == null) return;
                if (PENDING_GUILD_NAME.containsKey(uuid))
                {
                    if (!Boolean.TRUE.equals(REOPENING_ANVIL.get(uuid)))
                    {
                        PENDING_GUILD_NAME.remove(uuid);
                        ANVIL_TEXT_CACHE.remove(uuid);
                    }}

                }@ EventHandler(priority = EventPriority.HIGHEST) public void onPrepareAnvil(PrepareAnvilEvent event)
                {
                    Player player = null;
                    try
                    {
                        Object view = event.getView();
                        if (view != null)
                        {
                            player = (Player) view.getClass().getMethod("getPlayer").invoke(view);
                        }}

                        catch (Exception ignored)
                        {
                        }
                        if (player == null) return;
                        UUID uuid = player.getUniqueId();
                        if (uuid == null || !PENDING_GUILD_NAME.containsKey(uuid)) return;
                        String text = null;
                        if (event.getInventory() instanceof AnvilInventory)
                        {
                            AnvilInventory anvilInv = (AnvilInventory) event.getInventory();
                            try
                            {
                                text = anvilInv.getRenameText();
                            }
                            catch (NoSuchMethodError | Exception ignored)
                            {
                            }}

                            if (text == null || text.isEmpty())
                            {
                                ItemStack result = event.getResult();
                                if (result != null && result.hasItemMeta())
                                {
                                    text = ChatColor.stripColor(result.getItemMeta().getDisplayName());
                                }}

                                if (text != null && !text.isEmpty())
                                {
                                    ANVIL_TEXT_CACHE.put(uuid, text);
                                }
                                ItemStack slot0 = event.getInventory().getItem(0);
                                if (slot0 != null)
                                {
                                    ItemStack result = slot0.clone();
                                    ItemMeta meta = result.getItemMeta();
                                    if (meta != null)
                                    {
                                        String displayName = (text != null && !text.isEmpty()) ? text : ChatColor.GRAY + "请输入名称";
                                        meta.setDisplayName(ChatColor.GREEN + displayName);
                                        result.setItemMeta(meta);
                                    }
                                    event.setResult(result);
                                }}

                                private void handleNoGuildClick(Player player, String name)
                                {
                                    if (name.contains("创建公会"))
                                    {
                                        player.closeInventory();
                                        int minLen = plugin.getGuildConfig().getMinNameLength();
                                        int maxLen = plugin.getGuildConfig().getMaxNameLength();
                                        player.sendMessage(ChatColor.YELLOW + "========== 创建公会 ==========");
                                        player.sendMessage(ChatColor.GOLD + "请在聊天框输入公会名称（" + minLen + "-" + maxLen + "字符）");
                                        player.sendMessage(ChatColor.GRAY + "输入 'cancel' 取消创建");
                                        plugin.getChatInputListener().registerPendingAction(player.getUniqueId(), "guild_name", null);
                                    }
                                    else if (name.contains("查看所有公会"))
                                    {
                                        GuildGUI.openGuildListGUI(plugin, player);
                                    }}

                                    private void handleGuildListClick(Player player, String name)
                                    {
                                        if (name.contains("返回"))
                                        {
                                            GuildGUI.openGUI(plugin, player);
                                        }}

                                        private void handleBankClick(Player player, Guild guild, String name)
                                        {
                                            if (name.contains("存入资金"))
                                            {
                                                player.closeInventory();
                                                chatInputListener.registerPendingAction( player.getUniqueId(), "bank_deposit", null);
                                                player.sendMessage(ChatColor.YELLOW + "请输入要存入的金额:");
                                            }
                                            else if (name.contains("取出资金"))
                                            {
                                                player.closeInventory();
                                                chatInputListener.registerPendingAction( player.getUniqueId(), "bank_withdraw", null);
                                                player.sendMessage(ChatColor.YELLOW + "请输入要取出的金额:");
                                            }
                                            else if (name.contains("返回") && guild != null)
                                            {
                                                GuildGUI.openGUI(plugin, player);
                                            }}

                                            private void handleManageClick(Player player, String name)
                                            {
                                                if (name.contains("返回"))
                                                {
                                                    GuildGUI.openGUI(plugin, player);
                                                }
                                                else if (name.contains("重命名"))
                                                {
                                                    player.closeInventory();
                                                    // 3.0.8：修复重命名误注册为 guild_motd 导致改名实际修改公告的 BUG
                                                    chatInputListener.registerPendingAction( player.getUniqueId(), "guild_rename", null);
                                                    player.sendMessage(ChatColor.YELLOW + "请输入新名称:");
                                                }
                                                else if (name.contains("更改标签") || name.contains("标签"))
                                                {
                                                    player.closeInventory();
                                                    chatInputListener.registerPendingAction( player.getUniqueId(), "guild_tag", null);
                                                    player.sendMessage(ChatColor.YELLOW + "请输入新标签:");
                                                }
                                                else if (name.contains("解散公会"))
                                                {
                                                    player.closeInventory();
                                                    player.sendMessage(ChatColor.YELLOW + "请输入: /guild disband 来解散公会");
                                                }}

                                                private void handleMemberSettingsClick(Player player, String name)
                                                {
                                                    if (name.contains("返回"))
                                                    {
                                                        GuildGUI.openGUI(plugin, player);
                                                        return;
                                                    }
                                                    UUID targetUuid = GuildMemberSettingsGUI.getTargetUuid(player);
                                                    if (targetUuid == null)
                                                    {
                                                        player.sendMessage(ChatColor.RED + "无法获取目标玩家");
                                                        return;
                                                    }
                                                    Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
                                                    if (guild == null)
                                                    {
                                                        player.sendMessage(ChatColor.RED + "你不在公会中");
                                                        return;
                                                    }
                                                    GuildMember member = guild.getMember(player.getUniqueId());
                                                    if (member == null)
                                                    {
                                                        player.sendMessage(ChatColor.RED + "你不在公会中");
                                                        return;
                                                    }
                                                    if (name.contains("设置昵称"))
                                                    {
                                                        handleSetNickname(player, targetUuid);
                                                    }
                                                    else if (name.contains("设为管理员"))
                                                    {
                                                        handlePromote(player, guild, targetUuid);
                                                    }
                                                    else if (name.contains("降为成员"))
                                                    {
                                                        handleDemote(player, guild, targetUuid);
                                                    }
                                                    else if (name.contains("踢出公会"))
                                                    {
                                                        handleKick(player, guild, targetUuid);
                                                    }}

                                                    private void handleSetNickname(Player player, UUID targetUuid)
                                                    {
                                                        String targetName = plugin.getGuildManager().getPlayerNameCached(targetUuid);
                                                        player.closeInventory();
                                                        player.sendMessage(ChatColor.GOLD + "请输入 " + targetName + " 的新昵称（输入 C 取消）");
                                                        chatInputListener.registerPendingAction(player.getUniqueId(), "set_nickname", targetUuid);
                                                    }

                                                    private void handlePromote(Player player, Guild guild, UUID targetUuid)
                                                    {
                                                        GuildMember member = guild.getMember(player.getUniqueId());
                                                        if (member == null || member.getRole() != GuildRole.OFFICER && member.getRole() != GuildRole.OWNER)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "你没有权限执行此操作");
                                                            return;
                                                        }
                                                        GuildMember targetMember = guild.getMember(targetUuid);
                                                        if (targetMember == null)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "该玩家不在公会中");
                                                            return;
                                                        }
                                                        if (targetMember.getRole() == GuildRole.OFFICER || targetMember.getRole() == GuildRole.OWNER)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "该玩家已经是管理员或会长");
                                                            return;
                                                        }
                                                        // 3.0.9：GUI 晋升统一走管理器，使权限/职位上限/冷却校验与事件一致生效
                                                        if (!plugin.getGuildManager().promoteMember(guild.getName(), targetUuid, player.getUniqueId()))
                                                        {
                                                            if (targetMember.getRole() != GuildRole.MEMBER)
                                                            {
                                                                player.sendMessage(ChatColor.RED + "该玩家已经是管理员或会长");
                                                            }
                                                            return;
                                                        }
                                                        String targetName = plugin.getGuildManager().getPlayerNameCached(targetUuid);
                                                        player.sendMessage(ChatColor.GREEN + "已将 " + targetName + " 提升为管理员");
                                                        Player targetPlayer = Bukkit.getPlayer(targetUuid);
                                                        if (targetPlayer != null)
                                                        {
                                                            targetPlayer.sendMessage(ChatColor.GREEN + "你已被提升为管理员");
                                                        }
                                                        GuildGUI.openGUI(plugin, player);
                                                    }

                                                    private void handleDemote(Player player, Guild guild, UUID targetUuid)
                                                    {
                                                        GuildMember member = guild.getMember(player.getUniqueId());
                                                        if (member == null || member.getRole() != GuildRole.OFFICER && member.getRole() != GuildRole.OWNER)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "你没有权限执行此操作");
                                                            return;
                                                        }
                                                        GuildMember targetMember = guild.getMember(targetUuid);
                                                        if (targetMember == null)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "该玩家不在公会中");
                                                            return;
                                                        }
                                                        if (targetMember.getRole() == GuildRole.MEMBER)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "该玩家已经是普通成员");
                                                            return;
                                                        }
                                                        if (targetMember.getRole() == GuildRole.OWNER)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "不能降级会长");
                                                            return;
                                                        }
                                                        // 3.0.9：GUI 降级统一走管理器（含 allow-self-demote / demote-cooldown）
                                                        if (!plugin.getGuildManager().demoteMember(guild.getName(), targetUuid, player.getUniqueId()))
                                                        {
                                                            if (targetMember.getRole() == GuildRole.MEMBER)
                                                            {
                                                                player.sendMessage(ChatColor.RED + "该玩家已经是普通成员");
                                                            }
                                                            return;
                                                        }
                                                        String targetName = plugin.getGuildManager().getPlayerNameCached(targetUuid);
                                                        player.sendMessage(ChatColor.GREEN + "已将 " + targetName + " 降为普通成员");
                                                        Player targetPlayer = Bukkit.getPlayer(targetUuid);
                                                        if (targetPlayer != null)
                                                        {
                                                            targetPlayer.sendMessage(ChatColor.GREEN + "你已被降为普通成员");
                                                        }
                                                        GuildGUI.openGUI(plugin, player);
                                                    }

                                                    private void handleKick(Player player, Guild guild, UUID targetUuid)
                                                    {
                                                        GuildMember member = guild.getMember(player.getUniqueId());
                                                        if (member == null || member.getRole() != GuildRole.OFFICER && member.getRole() != GuildRole.OWNER)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "你没有权限执行此操作");
                                                            return;
                                                        }
                                                        GuildMember targetMember = guild.getMember(targetUuid);
                                                        if (targetMember == null)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "该玩家不在公会中");
                                                            return;
                                                        }
                                                        if (targetMember.getRole() == GuildRole.OWNER)
                                                        {
                                                            player.sendMessage(ChatColor.RED + "不能踢出会长");
                                                            return;
                                                        }
                                                        String targetName = plugin.getGuildManager().getPlayerNameCached(targetUuid);
                                                        // 3.0.9：GUI 踢人统一走管理器，触发 GuildKickEvent 并记录加入冷却
                                                        if (!plugin.getGuildManager().kickMember(guild.getName(), targetUuid, player.getUniqueId()))
                                                        {
                                                            player.sendMessage(ChatColor.RED + "踢出失败，目标职位高于你或数据异常");
                                                            return;
                                                        }
                                                        player.sendMessage(ChatColor.GREEN + "已将 " + targetName + " 踢出公会");
                                                        Player targetPlayer = Bukkit.getPlayer(targetUuid);
                                                        if (targetPlayer != null)
                                                        {
                                                            targetPlayer.sendMessage(ChatColor.RED + "你已被踢出公会");
                                                            GuildGUI.openGUI(plugin, targetPlayer);
                                                        }
                                                        GuildGUI.openGUI(plugin, player);
                                                    }

                                                    private void handleLeaveGuild(Player player, Guild guild)
                                                    {
                                                        GuildMember member = guild.getMember(player.getUniqueId());
                                                        if (member == null) return;
                                                        if (member.getRole() == GuildRole.OWNER)
                                                        {
                                                            player.closeInventory();
                                                            player.sendMessage(ChatColor.GOLD + "警告: 你是公会会长，离开公会将解散公会！");
                                                            player.sendMessage(ChatColor.GOLD + "请输入 confirm 确认解散，或输入 C 取消");
                                                            chatInputListener.registerPendingAction(player.getUniqueId(), "confirm_disband", guild.getName());
                                                        }
                                                        else
                                                        {
                                                            player.closeInventory();
                                                            player.sendMessage(ChatColor.GOLD + "请输入 confirm 确认离开公会，或输入 C 取消");
                                                            chatInputListener.registerPendingAction(player.getUniqueId(), "confirm_leave", guild.getName());
                                                        }}

                                                        private void handleShopClick(Player player, String name)
                                                        {
                                                            if (name.contains("返回"))
                                                            {
                                                                GuildGUI.openGUI(plugin, player);
                                                                return;
                                                            }
                                                            com.guild.config.ShopConfig shopConfig = plugin.getShopConfig();
                                                            if (shopConfig == null || !shopConfig.isEnabled())
                                                            {
                                                                player.sendMessage(org.bukkit.ChatColor.RED + "公会商店未启用");
                                                                return;
                                                            }
                                                            com.guild.config.ShopConfig.ShopItemEntry item = shopConfig.findItemByName(name);
                                                            if (item == null) return;
                                                            com.guild.guild.Guild guild = plugin.getGuildManager().getPlayerGuild(player.getUniqueId());
                                                            if (guild == null)
                                                            {
                                                                player.sendMessage(org.bukkit.ChatColor.RED + "你不在公会中");
                                                                return;
                                                            }
                                                            if (guild.getLevel() < item.getRequiredLevel())
                                                            {
                                                                player.sendMessage(org.bukkit.ChatColor.RED + "公会等级不足，需要 Lv." + item.getRequiredLevel());
                                                                return;
                                                            }
                                                            if (!item.getPermission().isEmpty() && !player.hasPermission(item.getPermission()))
                                                            {
                                                                player.sendMessage(org.bukkit.ChatColor.RED + "你没有权限购买此商品");
                                                                return;
                                                            }
                                                            if (!plugin.getGuildCurrency().withdraw(player.getUniqueId(), item.getPrice(), item.getCurrencyType()))
                                                            {
                                                                String curName = "";
                                                                switch (item.getCurrencyType())
                                                                {
                                                                    case VAULT: curName = plugin.getCurrencyConfig().getVaultCurrencyName(); break;
                                                                    case PLAYER_POINTS: curName = plugin.getCurrencyConfig().getPlayerPointsCurrencyName(); break;
                                                                    case GUILD_COIN: curName = plugin.getCurrencyConfig().getGuildCurrencyName(); break;
                                                                }
                                                                player.sendMessage(org.bukkit.ChatColor.RED + "购买失败，你需要 " + item.getPrice() + " " + curName);
                                                                return;
                                                            }
                                                            executeShopAction(player, guild, item);
                                                            if (!item.getBuyMessage().isEmpty())
                                                            {
                                                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                                                    item.getBuyMessage().replace("%player%", player.getName())));
                                                            }
                                                        }

                                                        private void executeShopAction(Player player, com.guild.guild.Guild guild, com.guild.config.ShopConfig.ShopItemEntry item)
                                                        {
                                                            String value = item.getActionValue().replace("%player%", player.getName());
                                                            switch (item.getAction())
                                                            {
                                                                case COMMAND:
                                                                    player.performCommand(value);
                                                                    break;
                                                                case CONSOLE:
                                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                                                                    break;
                                                                case GIVE_ITEM:
                                                                    giveShopItem(player, value);
                                                                    break;
                                                                case GUILD_EXP:
                                                                    try
                                                                    {
                                                                        long exp = Long.parseLong(value.trim());
                                                                        guild.addExperience(exp);
                                                                        plugin.getGuildManager().scheduleSavePublic(guild);
                                                                    }
                                                                    catch (NumberFormatException ignored) {}
                                                                    break;
                                                                case GUILD_COIN:
                                                                    try
                                                                    {
                                                                        long amount = Long.parseLong(value.trim());
                                                                        plugin.getGuildManager().depositPlayerGuildCurrency(player.getUniqueId(), amount);
                                                                    }
                                                                    catch (NumberFormatException ignored) {}
                                                                    break;
                                                            }
                                                        }

                                                        private void giveShopItem(Player player, String format)
                                                        {
                                                            try
                                                            {
                                                                String[] parts = format.split(":");
                                                                org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0].toUpperCase());
                                                                int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                                                                org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(mat, amount);
                                                                player.getInventory().addItem(stack);
                                                                player.updateInventory();
                                                            }
                                                            catch (Exception e)
                                                            {
                                                                plugin.getLogger().warning("Failed to give shop item: " + format + " - " + e.getMessage());
                                                            }
                                                        }

                                                            private void handleMainGUIClick(Player player, Guild guild, String name)
                                                            {
                                                                if (name.contains("离开公会"))
                                                                {
                                                                    handleLeaveGuild(player, guild);
                                                                    return;
                                                                }
                                                                if (name.contains("公会银行"))
                                                                {
                                                                    GuildBankGUI.openBankGUI(plugin, player, guild);
                                                                    return;
                                                                }
                                                                if (name.contains("公会商店"))
                                                                {
                                                                    GuildShopGUI.openShopGUI(plugin, player, guild);
                                                                    return;
                                                                }
                                                                if (handleToggleButtons(player, guild, name)) return;
                                                                GuildMember member = guild.getMember(player.getUniqueId());
                                                                if (member != null && member.getRole() == GuildRole.OWNER)
                                                                {
                                                                    handleOwnerSpecificClick(player, guild, name);
                                                                }
                                                                else if (member != null && member.getRole() == GuildRole.OFFICER)
                                                                {
                                                                    handleOfficerSpecificClick(player, guild, name);
                                                                }
                                                                else
                                                                {
                                                                    handleMemberHeadClick(player, guild, name);
                                                                }}

                                                                private boolean handleToggleButtons(Player player, Guild guild, String name)
                                                                {
                                                                    if (name.contains("公会邀请"))
                                                                    {
                                                                        boolean enabled = plugin.getGuildManager().togglePlayerInvites(player.getUniqueId());
                                                                        player.sendMessage(enabled ? ChatColor.GREEN + "已开启公会邀请" : ChatColor.RED + "已关闭公会邀请");
                                                                        GuildGUI.openGUI(plugin, player);
                                                                        return true;
                                                                    }
                                                                    if (name.contains("上下线通知"))
                                                                    {
                                                                        boolean enabled = plugin.getGuildManager().togglePlayerNotify(player.getUniqueId());
                                                                        player.sendMessage(enabled ? ChatColor.GREEN + "已开启上下线通知" : ChatColor.RED + "已关闭上下线通知");
                                                                        GuildGUI.openGUI(plugin, player);
                                                                        return true;
                                                                    }
                                                                    return false;
                                                                }

                                                                private void handleOwnerSpecificClick(Player player, Guild guild, String name)
                                                                {
                                                                    if (name.contains("管理公会"))
                                                                    {
                                                                        GuildManageGUI.openGUI(plugin, player, guild);
                                                                    }
                                                                    else if (name.contains("升级公会"))
                                                                    {
                                                                        handleUpgradeClick(player, guild);
                                                                    }
                                                                    else if (name.contains("购买经验"))
                                                                    {
                                                                        handleBuyExpClick(player, guild);
                                                                    }
                                                                    else
                                                                    {
                                                                        handleMemberHeadClick(player, guild, name);
                                                                    }}

                                                                    private void handleOfficerSpecificClick(Player player, Guild guild, String name)
                                                                    {
                                                                        if (name.contains("管理公会"))
                                                                        {
                                                                            GuildManageGUI.openGUI(plugin, player, guild);
                                                                        }
                                                                        else
                                                                        {
                                                                            handleMemberHeadClick(player, guild, name);
                                                                        }}

                                                                        private void handleMemberHeadClick(Player player, Guild guild, String name)
                                                                        {
                                                                            for (Map.Entry<UUID, GuildMember> entry : guild.getMembers().entrySet())
                                                                            {
                                                                                UUID targetUuid = entry.getKey();
                                                                                GuildMember targetMember = entry.getValue();
                                                                                String playerName = plugin.getGuildManager().getPlayerNameCached(targetUuid);
                                                                                String display = targetMember.getNickname() != null ? targetMember.getNickname() : playerName;
                                                                                if (name.equals(playerName) || name.equals(display))
                                                                                {
                                                                                    GuildMemberSettingsGUI.openGUI(plugin, player, guild, targetUuid);
                                                                                    return;
                                                                                }}

                                                                            }

                                                                            private void handleUpgradeClick(Player player, Guild guild)
                                                                            {
                                                                                if (!guild.getOwner().equals(player.getUniqueId()))
                                                                                {
                                                                                    player.sendMessage(ChatColor.RED + "只有公会会长才能升级公会");
                                                                                    return;
                                                                                }
                                                                                if (guild.getLevel() >= guild.getMaxLevel())
                                                                                {
                                                                                    player.sendMessage(ChatColor.RED + "公会已达到最高等级");
                                                                                    return;
                                                                                }
                                                                                if (plugin.getGuildManager().upgradeGuild(guild.getName(), player.getUniqueId()))
                                                                                {
                                                                                    long cost = plugin.getCurrencyConfig().getLevelUpCost(guild.getLevel() - 1);
                                                                                    String costStr = plugin.getGuildCurrency().formatAmount(cost, plugin.getCurrencyConfig().getCurrencyType());
                                                                                    player.sendMessage(ChatColor.GREEN + "成功使用 " + costStr + " 将公会升级到 " + guild.getLevel() + " 级！");
                                                                                    guild.broadcast(ChatColor.YELLOW + "恭喜！公会在 " + player.getName() + " 的努力下升级到了 " + guild.getLevel() + " 级！");
                                                                                    player.closeInventory();
                                                                                }
                                                                                else
                                                                                {
                                                                                    long cost = plugin.getCurrencyConfig().getLevelUpCost(guild.getLevel());
                                                                                    String costStr = plugin.getGuildCurrency().formatAmount(cost, plugin.getCurrencyConfig().getCurrencyType());
                                                                                    player.sendMessage(ChatColor.RED + "升级失败，你可能没有足够的 " + costStr);
                                                                                }}

                                                                                private void handleBuyExpClick(Player player, Guild guild)
                                                                                {
                                                                                    if (plugin.getGuildManager().addExperienceWithCurrency(guild.getName(), player.getUniqueId()))
                                                                                    {
                                                                                        int amount = plugin.getCurrencyConfig().getExperienceAmount();
                                                                                        long cost = plugin.getCurrencyConfig().getExperienceCost();
                                                                                        String costStr = plugin.getGuildCurrency().formatAmount(cost, plugin.getCurrencyConfig().getCurrencyType());
                                                                                        player.sendMessage(ChatColor.GREEN + "成功使用 " + costStr + " 购买了 " + amount + " 经验！");
                                                                                        guild.broadcast(ChatColor.YELLOW + player.getName() + " 使用 " + costStr + " 为公会购买了 " + amount + " 经验");
                                                                                    }
                                                                                    else
                                                                                    {
                                                                                        long cost = plugin.getCurrencyConfig().getExperienceCost();
                                                                                        String costStr = plugin.getGuildCurrency().formatAmount(cost, plugin.getCurrencyConfig().getCurrencyType());
                                                                                        player.sendMessage(ChatColor.RED + "购买失败，你可能没有足够的 " + costStr);
                                                                                    }}

                                                                                    private boolean handleAnvilInput(InventoryClickEvent event, Player player)
                                                                                    {
                                                                                        Inventory topInv = event.getInventory();
                                                                                        if (topInv == null || topInv.getType() != InventoryType.ANVIL) return false;
                                                                                        UUID uuid = player.getUniqueId();
                                                                                        if (uuid == null || !PENDING_GUILD_NAME.containsKey(uuid)) return false;
                                                                                        event.setCancelled(true);
                                                                                        if (event.getRawSlot() != 2) return true;
                                                                                        String input = ANVIL_TEXT_CACHE.get(uuid);
                                                                                        if (input == null || input.isEmpty())
                                                                                        {
                                                                                            ItemStack result = event.getCurrentItem();
                                                                                            if (result != null && result.hasItemMeta())
                                                                                            {
                                                                                                input = ChatColor.stripColor(result.getItemMeta().getDisplayName());
                                                                                            }}

                                                                                            if (input != null && input.startsWith(ChatColor.GREEN.toString()))
                                                                                            {
                                                                                                input = input.substring(2);
                                                                                            }
                                                                                            if (input == null || input.isEmpty())
                                                                                            {
                                                                                                ItemStack slot1 = topInv.getItem(1);
                                                                                                if (slot1 != null && slot1.hasItemMeta())
                                                                                                {
                                                                                                    input = ChatColor.stripColor(slot1.getItemMeta().getDisplayName());
                                                                                                }}

                                                                                                if (input != null && input.startsWith(ChatColor.GREEN.toString()))
                                                                                                {
                                                                                                    input = input.substring(2);
                                                                                                }
                                                                                                if (input == null || input.isEmpty())
                                                                                                {
                                                                                                    player.sendMessage(ChatColor.RED + "请输入有效内容");
                                                                                                    reopenCurrentAnvil(player);
                                                                                                    return true;
                                                                                                }
                                                                                                String pendingName = PENDING_GUILD_NAME.get(uuid);
                                                                                                if (pendingName == null || pendingName.isEmpty())
                                                                                                {
                                                                                                    processGuildNameInput(player, input);
                                                                                                }
                                                                                                else
                                                                                                {
                                                                                                    processGuildTagInput(player, pendingName, input);
                                                                                                }
                                                                                                return true;
                                                                                            }

                                                                                            private void reopenCurrentAnvil(Player player)
                                                                                            {
                                                                                                UUID uuid = player.getUniqueId();
                                                                                                if (uuid == null) return;
                                                                                                String pending = PENDING_GUILD_NAME.get(uuid);
                                                                                                String cachedText = ANVIL_TEXT_CACHE.get(uuid);
                                                                                                String finalPending = pending;
                                                                                                REOPENING_ANVIL.put(uuid, true);
                                                                                                player.closeInventory();
                                                                                                Bukkit.getScheduler().runTaskLater(plugin, () ->
                                                                                                {
                                                                                                    PENDING_GUILD_NAME.put(uuid, pending);
                                                                                                    if (cachedText != null && !cachedText.isEmpty())
                                                                                                    {
                                                                                                        ANVIL_TEXT_CACHE.put(uuid, cachedText);
                                                                                                    }
                                                                                                    if (pending == null || pending.isEmpty())
                                                                                                    {
                                                                                                        openGuildNameAnvil(player);
                                                                                                    }
                                                                                                    else
                                                                                                    {
                                                                                                        openGuildTagAnvil(player, finalPending);
                                                                                                    }
                                                                                                    REOPENING_ANVIL.remove(uuid);
                                                                                                }, 2L);
                                                                                            }

                                                                                            private void openGuildNameAnvil(Player player)
                                                                                            {
                                                                                                if (player == null) return;
                                                                                                UUID uuid = player.getUniqueId();
                                                                                                if (uuid == null) return;
                                                                                                Inventory anvil = Bukkit.createInventory( null, InventoryType.ANVIL, ChatColor.translateAlternateColorCodes('&', "&6&l输入公会名称"));
                                                                                                ItemStack nameTag = new ItemStack(Material.NAME_TAG);
                                                                                                ItemMeta meta = nameTag.getItemMeta();
                                                                                                String cachedText = ANVIL_TEXT_CACHE.get(uuid);
                                                                                                if (cachedText != null && !cachedText.isEmpty())
                                                                                                {
                                                                                                    meta.setDisplayName(cachedText);
                                                                                                }
                                                                                                else
                                                                                                {
                                                                                                    meta.setDisplayName(ChatColor.GRAY + "在此输入公会名称");
                                                                                                }
                                                                                                nameTag.setItemMeta(meta);
                                                                                                anvil.setItem(0, nameTag);
                                                                                                REOPENING_ANVIL.put(uuid, true);
                                                                                                PENDING_GUILD_NAME.put(uuid, "");
                                                                                                player.openInventory(anvil);
                                                                                                Bukkit.getScheduler().runTaskLater(plugin, () ->
                                                                                                {
                                                                                                    REOPENING_ANVIL.remove(uuid);
                                                                                                }, 1L);
                                                                                            }

                                                                                            private void openGuildTagAnvil(Player player, String guildName)
                                                                                            {
                                                                                                if (player == null) return;
                                                                                                UUID uuid = player.getUniqueId();
                                                                                                if (uuid == null) return;
                                                                                                Inventory anvil = Bukkit.createInventory( null, InventoryType.ANVIL, ChatColor.translateAlternateColorCodes('&', "&6&l输入公会标签"));
                                                                                                ItemStack nameTag = new ItemStack(Material.NAME_TAG);
                                                                                                ItemMeta meta = nameTag.getItemMeta();
                                                                                                String cachedText = ANVIL_TEXT_CACHE.get(uuid);
                                                                                                if (cachedText != null && !cachedText.isEmpty())
                                                                                                {
                                                                                                    meta.setDisplayName(cachedText);
                                                                                                }
                                                                                                else
                                                                                                {
                                                                                                    meta.setDisplayName(ChatColor.GRAY + "在此输入公会标签 (2-4字符)");
                                                                                                }
                                                                                                nameTag.setItemMeta(meta);
                                                                                                anvil.setItem(0, nameTag);
                                                                                                REOPENING_ANVIL.put(uuid, true);
                                                                                                PENDING_GUILD_NAME.put(uuid, guildName);
                                                                                                player.openInventory(anvil);
                                                                                                Bukkit.getScheduler().runTaskLater(plugin, () ->
                                                                                                {
                                                                                                    REOPENING_ANVIL.remove(uuid);
                                                                                                }, 1L);
                                                                                            }

                                                                                            private void processGuildNameInput(Player player, String name)
                                                                                            {
                                                                                                int minLen = plugin.getGuildConfig().getMinNameLength();
                                                                                                int maxLen = plugin.getGuildConfig().getMaxNameLength();
                                                                                                if (name.length() < minLen || name.length() > maxLen)
                                                                                                {
                                                                                                    player.sendMessage(plugin.getMessage("guild.name-length-invalid") .replace("%min%", String.valueOf(minLen)) .replace("%max%", String.valueOf(maxLen)));
                                                                                                    reopenCurrentAnvil(player);
                                                                                                    return;
                                                                                                }
                                                                                                if (plugin.getGuildManager().getGuild(name) != null)
                                                                                                {
                                                                                                    player.sendMessage(plugin.getMessage("guild.already-exists"));
                                                                                                    reopenCurrentAnvil(player);
                                                                                                    return;
                                                                                                }
                                                                                                UUID uuid = player.getUniqueId();
                                                                                                REOPENING_ANVIL.put(uuid, true);
                                                                                                PENDING_GUILD_NAME.put(uuid, name);
                                                                                                ANVIL_TEXT_CACHE.remove(uuid);
                                                                                                player.closeInventory();
                                                                                                Bukkit.getScheduler().runTaskLater(plugin, () ->
                                                                                                {
                                                                                                    openGuildTagAnvil(player, name);
                                                                                                    REOPENING_ANVIL.remove(uuid);
                                                                                                }, 1L);
                                                                                            }

                                                                                            private void processGuildTagInput(Player player, String guildName, String tag)
                                                                                            {
                                                                                                int maxTagLen = plugin.getGuildConfig().getMaxTagLength();
                                                                                                if (tag.length() < 2 || tag.length() > maxTagLen)
                                                                                                {
                                                                                                    player.sendMessage(plugin.getMessage("guild.tag-length-invalid") .replace("%max%", String.valueOf(maxTagLen)));
                                                                                                    reopenCurrentAnvil(player);
                                                                                                    return;
                                                                                                }
                                                                                                // 3.0.8：createGuild 内部已统一扣费，不再预扣/退款，避免 GUI 创建路径双重扣费
                                                                                                Guild guild = plugin.getGuildManager().createGuild(guildName, player);
                                                                                                if (guild == null)
                                                                                                {
                                                                                                    // 名称重复时给出明确提示，其余失败原因（权限/资金）由管理器发送
                                                                                                    if (plugin.getGuildManager().getGuild(guildName) != null)
                                                                                                    {
                                                                                                        player.sendMessage(plugin.getMessage("guild.already-exists"));
                                                                                                    }
                                                                                                    reopenCurrentAnvil(player);
                                                                                                    return;
                                                                                                }
                                                                                                guild.setTag(tag);
                                                                                                plugin.getGuildManager().scheduleSavePublic(guild);
                                                                                                PENDING_GUILD_NAME.remove(player.getUniqueId());
                                                                                                ANVIL_TEXT_CACHE.remove(player.getUniqueId());
                                                                                                player.sendMessage(plugin.getMessage("guild.created").replace("%name%", guildName));
                                                                                                player.sendMessage(ChatColor.GREEN + "公会标签: " + tag);
                                                                                                GuildGUI.openGUI(plugin, player);
                                                                                            }

                                                                                            public static void cleanupPlayer(UUID uuid)
                                                                                            {
                                                                                                PENDING_GUILD_NAME.remove(uuid);
                                                                                                ANVIL_TEXT_CACHE.remove(uuid);
                                                                                            }}