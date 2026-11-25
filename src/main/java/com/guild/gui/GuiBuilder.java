package com.guild.gui;
import com.guild.utils.VersionCompat;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GuiBuilder
{
    private GuiBuilder()
    {
    }

    public static final String COLOR_PRIMARY = "&6";

    public static final String COLOR_SECONDARY = "&e";

    public static final String COLOR_SUCCESS = "&a";

    public static final String COLOR_DANGER = "&c";

    public static final String COLOR_INFO = "&7";

    public static final String COLOR_SPECIAL = "&b";

    public static final String COLOR_LEGENDARY = "&5";

    public static final String COLOR_YELLOW = "&e";

    public static final Material BORDER_MATERIAL = VersionCompat.getGrayStainedGlassPaneMaterial();

    public static final Material DECORATION_MATERIAL = VersionCompat.getLightBlueStainedGlassPaneMaterial();

    public static final Material GOLD_BORDER_MATERIAL = VersionCompat.getGoldStainedGlassPaneMaterial();

    public static final Material PURPLE_BORDER_MATERIAL = VersionCompat.getPurpleStainedGlassPaneMaterial();

    public static ItemStack createItem(Material material, String name, String... lore)
    {
        return createItem(material, name, Arrays.asList(lore));
    }

    public static ItemStack createItem(Material material, String name, List<String> lore)
    {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        if (lore != null && !lore.isEmpty())
        {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore)
            {
                coloredLore.add(color(line));
            }
            meta.setLore(coloredLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createSimpleItem(Material material, String name)
    {
        return createItem(material, name, Collections.emptyList());
    }

    public static void fillEmpty(org.bukkit.inventory.Inventory inventory, int size)
    {
        ItemStack filler = createSimpleItem(BORDER_MATERIAL, " ");
        for (int i = 0;
        i < size;
        i++)
        {
            if (inventory.getItem(i) == null)
            {
                inventory.setItem(i, filler);
            }}

        }

        public static void fillRange(org.bukkit.inventory.Inventory inventory, int start, int end, Material material)
        {
            ItemStack filler = createSimpleItem(material, " ");
            for (int i = start;
            i <= end;
            i++)
            {
                if (i >= 0 && i < inventory.getSize())
                {
                    inventory.setItem(i, filler);
                }}

            }

            public static void drawBorder(org.bukkit.inventory.Inventory inventory, int rows, Material material)
            {
                int size = rows * 9;
                ItemStack border = createSimpleItem(material, " ");
                for (int i = 0;
                i < 9;
                i++)
                {
                    inventory.setItem(i, border);
                    inventory.setItem(size - 9 + i, border);
                }
                for (int r = 1;
                r < rows - 1;
                r++)
                {
                    inventory.setItem(r * 9, border);
                    inventory.setItem(r * 9 + 8, border);
                }}

                public static void drawRow(org.bukkit.inventory.Inventory inventory, int row, Material material)
                {
                    ItemStack line = createSimpleItem(material, " ");
                    for (int i = 0;
                    i < 9;
                    i++)
                    {
                        inventory.setItem(row * 9 + i, line);
                    }}

                    public static ItemStack backButton(String... extraLore)
                    {
                        List<String> lore = new ArrayList<>();
                        lore.add(COLOR_INFO + "点击返回上一级界面");
                        for (String s : extraLore) lore.add(s);
                        return createItem(VersionCompat.getArrowMaterial(), COLOR_DANGER + "返回", lore);
                    }

                    public static ItemStack confirmButton(String name, String... lore)
                    {
                        return createItem(VersionCompat.getLimeStainedGlassPaneMaterial(), COLOR_SUCCESS + name, lore);
                    }

                    public static ItemStack cancelButton(String name, String... lore)
                    {
                        return createItem(VersionCompat.getRedStainedGlassPaneMaterial(), COLOR_DANGER + name, lore);
                    }

                    public static ItemStack infoButton(String name, String... lore)
                    {
                        return createItem(VersionCompat.getPaperMaterial(), COLOR_SPECIAL + name, lore);
                    }

                    public static ItemStack memberListButton(String... lore)
                    {
                        List<String> l = new ArrayList<>();
                        l.add(COLOR_INFO + "查看公会成员列表");
                        for (String s : lore) l.add(s);
                        return createItem(VersionCompat.getPlayerHeadMaterial(), COLOR_SECONDARY + "成员列表", l);
                    }

                    public static ItemStack bankButton(String... lore)
                    {
                        List<String> l = new ArrayList<>();
                        l.add(COLOR_INFO + "管理公会银行资金");
                        for (String s : lore) l.add(s);
                        return createItem(VersionCompat.getGoldIngotMaterial(), COLOR_PRIMARY + "公会银行", l);
                    }

                    public static ItemStack shopButton(int level)
                    {
                        return createItem(VersionCompat.getChestMaterial(), COLOR_PRIMARY + "公会商店", COLOR_INFO + "等级专属商店", COLOR_INFO + "当前等级: " + COLOR_SECONDARY + level, COLOR_YELLOW + "点击查看商品");
                    }

                    public static ItemStack settingsButton()
                    {
                        return createItem(VersionCompat.getRedstoneMaterial(), COLOR_SECONDARY + "个人设置", COLOR_INFO + "修改你的个人偏好");
                    }

                    public static ItemStack leaveButton()
                    {
                        return createItem(VersionCompat.getBarrierMaterial(), COLOR_DANGER + "离开公会", COLOR_INFO + "退出当前公会", COLOR_DANGER + "此操作不可撤销");
                    }

                    public static ItemStack upgradeButton(int level, String costStr)
                    {
                        return createItem(VersionCompat.getEmeraldMaterial(), COLOR_SUCCESS + "升级公会", COLOR_INFO + "当前等级: " + COLOR_SECONDARY + level, COLOR_INFO + "升级所需: " + costStr, COLOR_YELLOW + "点击升级公会");
                    }

                    public static ItemStack buyExpButton(String costStr, int expAmount)
                    {
                        return createItem(VersionCompat.getExperienceBottleMaterial(), COLOR_SPECIAL + "购买经验", COLOR_INFO + "获得: " + COLOR_SECONDARY + expAmount + " 经验", COLOR_INFO + "所需: " + costStr, COLOR_YELLOW + "点击购买经验");
                    }

                    public static ItemStack manageButton()
                    {
                        return createItem(VersionCompat.getCommandBlockMaterial(), COLOR_DANGER + "管理公会", COLOR_INFO + "重命名/改标签/解散");
                    }

                    public static ItemStack createGuildButton()
                    {
                        return createItem(VersionCompat.getDiamondMaterial(), COLOR_SUCCESS + "创建公会", COLOR_INFO + "建立属于你自己的公会", COLOR_YELLOW + "点击开始创建");
                    }

                    public static ItemStack viewAllGuildsButton()
                    {
                        return createItem(VersionCompat.getBookMaterial(), COLOR_SECONDARY + "查看所有公会", COLOR_INFO + "浏览服务器上的所有公会", COLOR_YELLOW + "点击浏览");
                    }

                    public static ItemStack noGuildBarrier()
                    {
                        return createItem(VersionCompat.getBarrierMaterial(), COLOR_DANGER + "你还没有公会", COLOR_INFO + "选择下方选项开始游戏");
                    }

                    public static String color(String text)
                    {
                        return ChatColor.translateAlternateColorCodes('&', text != null ? text : "");
                    }

                    public static String colorTrim(String text)
                    {
                        return color(text).trim();
                    }

                    public static void drawDoubleBorder(org.bukkit.inventory.Inventory inventory, int rows, Material outer, Material inner)
                    {
                        int size = rows * 9;
                        ItemStack outerBorder = createSimpleItem(outer, " ");
                        ItemStack innerBorder = createSimpleItem(inner, " ");
                        for (int i = 0;
                        i < 9;
                        i++)
                        {
                            inventory.setItem(i, outerBorder);
                            inventory.setItem(size - 9 + i, outerBorder);
                            if (rows > 2)
                            {
                                inventory.setItem(9 + i, innerBorder);
                                inventory.setItem(size - 18 + i, innerBorder);
                            }}

                            for (int r = 1;
                            r < rows - 1;
                            r++)
                            {
                                inventory.setItem(r * 9, outerBorder);
                                inventory.setItem(r * 9 + 8, outerBorder);
                                if (rows > 2 && r > 1 && r < rows - 2)
                                {
                                    inventory.setItem(r * 9 + 1, innerBorder);
                                    inventory.setItem(r * 9 + 7, innerBorder);
                                }}

                            }

                            public static void drawGradientLine(org.bukkit.inventory.Inventory inventory, int row, Material material1, Material material2)
                            {
                                ItemStack item1 = createSimpleItem(material1, " ");
                                ItemStack item2 = createSimpleItem(material2, " ");
                                for (int i = 0;
                                i < 9;
                                i++)
                                {
                                    inventory.setItem(row * 9 + i, (i % 2 == 0) ? item1 : item2);
                                }}

                                public static ItemStack memberLevelButton(String name, int level, int maxLevel, String... lore)
                                {
                                    List<String> l = new ArrayList<>();
                                    l.add(COLOR_INFO + "等级: " + COLOR_SECONDARY + level + "/" + maxLevel);
                                    for (String s : lore) l.add(s);
                                    Material material;
                                    if (level >= maxLevel)
                                    {
                                        material = VersionCompat.getNetherStarMaterial();
                                    }
                                    else if (level >= maxLevel * 0.7)
                                    {
                                        material = VersionCompat.getDiamondMaterial();
                                    }
                                    else if (level >= maxLevel * 0.4)
                                    {
                                        material = VersionCompat.getGoldIngotMaterial();
                                    }
                                    else
                                    {
                                        material = VersionCompat.getIronIngotMaterial();
                                    }
                                    return createItem(material, COLOR_PRIMARY + name, l);
                                }

                                public static ItemStack contributionButton(long contribution)
                                {
                                    return createItem(VersionCompat.getEmeraldMaterial(), COLOR_SUCCESS + "贡献值", COLOR_INFO + "当前贡献: " + COLOR_SECONDARY + contribution, COLOR_YELLOW + "参与公会活动获得贡献");
                                }

                                public static ItemStack motdButton(String motd)
                                {
                                    return createItem(VersionCompat.getPaperMaterial(), COLOR_SPECIAL + "公会公告", COLOR_INFO + (motd != null && !motd.isEmpty() ? motd : "暂无公告"));
                                }

                                public static ItemStack expProgressButton(long current, long required, int level)
                                {
                                    double percentage = (double) current / required * 100;
                                    String progress = "";
                                    for (int i = 0;
                                    i < 10;
                                    i++)
                                    {
                                        progress += (i * 10 < percentage) ? "\u2588" : "\u2591";
                                    }
                                    return createItem(VersionCompat.getExperienceBottleMaterial(), COLOR_PRIMARY + "经验进度", COLOR_INFO + "等级: " + COLOR_SECONDARY + level, COLOR_YELLOW + progress, COLOR_INFO + current + " / " + required);
                                }}
