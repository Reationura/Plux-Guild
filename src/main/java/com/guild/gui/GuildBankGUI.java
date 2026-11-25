package com.guild.gui;
import com.guild.GuildPlugin;
import com.guild.guild.Guild;
import com.guild.utils.VersionCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GuildBankGUI
{
    public static void openBankGUI(GuildPlugin plugin, Player player, Guild guild)
    {
        Inventory inv = Bukkit.createInventory( new SimpleGuildGUIHolder("bank"), 45, GuiBuilder.color("&6&l公会银行: &e" + guild.getName()));
        GuiBuilder.drawBorder(inv, 5, GuiBuilder.DECORATION_MATERIAL);
        long balance = guild.getBank().getBalance();
        inv.setItem(13, GuiBuilder.createItem( VersionCompat.getGoldIngotMaterial(), GuiBuilder.COLOR_PRIMARY + "当前余额", GuiBuilder.COLOR_SECONDARY + "" + balance + " &7公会币", "", GuiBuilder.COLOR_INFO + "左键存入 | 右键取出"));
        inv.setItem(20, GuiBuilder.createItem( VersionCompat.getEmeraldMaterial(), GuiBuilder.COLOR_SUCCESS + "存入资金", GuiBuilder.COLOR_INFO + "点击后输入金额", GuiBuilder.COLOR_INFO + "从你的账户转入公会"));
        inv.setItem(24, GuiBuilder.createItem( VersionCompat.getRedstoneMaterial(), GuiBuilder.COLOR_DANGER + "取出资金", GuiBuilder.COLOR_INFO + "点击后输入金额", GuiBuilder.COLOR_INFO + "从公会银行转出", "", GuiBuilder.COLOR_DANGER + "需要管理员权限"));
        inv.setItem(40, GuiBuilder.backButton());
        GuiBuilder.fillEmpty(inv, 45);
        player.openInventory(inv);
    }}
