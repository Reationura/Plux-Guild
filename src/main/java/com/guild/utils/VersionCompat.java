package com.guild.utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VersionCompat
{
    private static final String NMS_VERSION;

    private static final String MC_VERSION;

    private static final int MAJOR_VERSION;

    private static final boolean MODERN_MATERIALS;

    static
    {
        String nmsVer = "";
        try
        {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            nmsVer = pkg.substring(pkg.lastIndexOf(".") + 1);
        }
        catch (Exception ignored)
        {
        }
        NMS_VERSION = nmsVer;
        String mcVer = "";
        try
        {
            String ver = Bukkit.getVersion();
            int start = ver.indexOf("(MC: ");
            if (start >= 0)
            {
                start += 5;
                int end = ver.indexOf(')', start);
                mcVer = ver.substring(start, end);
            }}

            catch (Exception ignored)
            {
            }
            MC_VERSION = mcVer;
            int major = 8;
            try
            {
                String[] parts = MC_VERSION.split("\\.");
                major = Integer.parseInt(parts[1]);
            }
            catch (Exception ignored)
            {
            }
            MAJOR_VERSION = major;
            boolean modern = false;
            try
            {
                Material.valueOf("OAK_LOG");
                modern = true;
            }
            catch (IllegalArgumentException | NoSuchFieldError ignored)
            {
            }
            MODERN_MATERIALS = modern;
        }

        private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

        public static Method getCachedMethod(Class<?> clazz, String name, Class<?>... paramTypes)
        {
            String key = clazz.getName() + "." + name + Arrays.toString(paramTypes);
            return METHOD_CACHE.computeIfAbsent(key, k ->
            {
                try
                {
                    Method m = clazz.getMethod(name, paramTypes);
                    m.setAccessible(true);
                    return m;
                }
                catch (NoSuchMethodException e)
                {
                    return null;
                }}

                );
            }

            public static Object invokeCached(Method method, Object target, Object... args)
            {
                if (method == null) return null;
                try
                {
                    return method.invoke(target, args);
                }
                catch (Exception e)
                {
                    Bukkit.getLogger().warning("[Guild] 反射调用失败: " + method.getName());
                    return null;
                }}

                public static Class<?> getNmsClass(String className) throws ClassNotFoundException
                {
                    return Class.forName("net.minecraft.server." + NMS_VERSION + "." + className);
                }

                public static String getNmsVersion()
                {
                    return NMS_VERSION;
                }

                public static String getMcVersion()
                {
                    return MC_VERSION;
                }

                public static int getMajorVersion()
                {
                    return MAJOR_VERSION;
                }

                public static boolean isModernVersion()
                {
                    return MODERN_MATERIALS;
                }

                public static boolean isSeventeenPlus()
                {
                    return MAJOR_VERSION >= 17;
                }

                public static boolean isTwentyPlusFive()
                {
                    return MAJOR_VERSION >= 20 && MC_VERSION.compareTo("1.20.5") >= 0;
                }

                public static String getServerType()
                {
                    try
                    {
                        return Bukkit.getServer().getName();
                    }
                    catch (Exception e)
                    {
                        return "Unknown";
                    }}

                    public static String getInventoryTitleFromView(InventoryView view)
                    {
                        if (view == null) return "";
                        try
                        {
                            return view.getTitle();
                        }
                        catch (Throwable ignored)
                        {
                        }
                        Method titleMethod = getCachedMethod(InventoryView. class, "getTitle");
                        if (titleMethod != null)
                        {
                            Object result = invokeCached(titleMethod, view);
                            if (result instanceof String) return (String) result;
                        }
                        return "";
                    }

                    public static Material getBarrierMaterial()
                    {
                        return safeMaterial("BARRIER");
                    }

                    public static Material getArrowMaterial()
                    {
                        return safeMaterial("ARROW");
                    }

                    public static Material getDiamondMaterial()
                    {
                        return safeMaterial("DIAMOND");
                    }

                    public static Material getBookMaterial()
                    {
                        return safeMaterial("BOOK");
                    }

                    public static Material getPaperMaterial()
                    {
                        return safeMaterial("PAPER");
                    }

                    public static Material getPlayerHeadMaterial()
                    {
                        return legacyMaterial("PLAYER_HEAD", "SKULL_ITEM", (short)3);
                    }

                    public static Material getRedstoneMaterial()
                    {
                        return safeMaterial("REDSTONE");
                    }

                    public static Material getGoldIngotMaterial()
                    {
                        return safeMaterial("GOLD_INGOT");
                    }

                    public static Material getChestMaterial()
                    {
                        return safeMaterial("CHEST");
                    }

                    public static Material getLeverMaterial()
                    {
                        return safeMaterial("LEVER");
                    }

                    public static Material getNoteBlockMaterial()
                    {
                        return safeMaterial("NOTE_BLOCK");
                    }

                    public static Material getNameTagMaterial()
                    {
                        return safeMaterial("NAME_TAG");
                    }

                    public static Material getAnvilMaterial()
                    {
                        return safeMaterial("ANVIL");
                    }

                    public static Material getCommandBlockMaterial()
                    {
                        return safeMaterial("COMMAND_BLOCK");
                    }

                    public static Material getEmeraldMaterial()
                    {
                        return safeMaterial("EMERALD");
                    }

                    public static Material getExperienceBottleMaterial()
                    {
                        return safeMaterial("EXPERIENCE_BOTTLE");
                    }

                    public static Material getGoldenHelmetMaterial()
                    {
                        return safeMaterial("GOLDEN_HELMET");
                    }

                    public static Material getIronHelmetMaterial()
                    {
                        return safeMaterial("IRON_HELMET");
                    }

                    public static Material getGrayStainedGlassPaneMaterial()
                    {
                        return legacyMaterial("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short)8);
                    }

                    public static Material getLightBlueStainedGlassPaneMaterial()
                    {
                        return legacyMaterial("LIGHT_BLUE_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short)3);
                    }

                    public static Material getDiamondBlockMaterial()
                    {
                        return safeMaterial("DIAMOND_BLOCK");
                    }

                    public static Material getLimeStainedGlassPaneMaterial()
                    {
                        return legacyMaterial("LIME_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short)5);
                    }

                    public static Material getRedStainedGlassPaneMaterial()
                    {
                        return legacyMaterial("RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short)14);
                    }

                    public static Material getGoldStainedGlassPaneMaterial()
                    {
                        return legacyMaterial("YELLOW_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short)4);
                    }

                    public static Material getPurpleStainedGlassPaneMaterial()
                    {
                        return legacyMaterial("PURPLE_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short)10);
                    }

                    public static Material getNetherStarMaterial()
                    {
                        return safeMaterial("NETHER_STAR");
                    }

                    public static Material getIronIngotMaterial()
                    {
                        return safeMaterial("IRON_INGOT");
                    }

                    private static Material safeMaterial(String modernName)
                    {
                        try
                        {
                            return Material.valueOf(modernName);
                        }
                        catch (IllegalArgumentException | NoSuchFieldError e)
                        {
                            return Material.STONE;
                        }}

                        @SuppressWarnings("deprecation") private static Material legacyMaterial(String modernName, String legacyName, short legacyData)
                        {
                            if (MODERN_MATERIALS)
                            {
                                try
                                {
                                    return Material.valueOf(modernName);
                                }
                                catch (IllegalArgumentException | NoSuchFieldError ignored)
                                {
                                }}

                                try
                                {
                                    return Material.valueOf(legacyName);
                                }
                                catch (IllegalArgumentException | NoSuchFieldError ignored)
                                {
                                }
                                return Material.STONE;
                            }

                            public static Material getMaterial(String modernName)
                            {
                                return safeMaterial(modernName);
                            }

                            public static ItemStack createItem(Material material, String name, String... lore)
                            {
                                ItemStack item = new ItemStack(material);
                                ItemMeta meta = item.getItemMeta();
                                if (meta != null)
                                {
                                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                                    if (lore.length > 0)
                                    {
                                        meta.setLore(Arrays.stream(lore) .map(l -> ChatColor.translateAlternateColorCodes('&', l)) .collect(java.util.stream.Collectors.toList()));
                                    }
                                    item.setItemMeta(meta);
                                }
                                return item;
                            }

@SuppressWarnings("deprecation") public static ItemStack createPlayerHead(UUID playerUuid, String name, String... lore)
                            {
                                ItemStack head;
                                if (MODERN_MATERIALS)
                                {
                                    head = new ItemStack(Material.PLAYER_HEAD, 1);
                                }
                                else
                                {
                                    head = new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (short) 3);
                                }
                                ItemMeta meta = head.getItemMeta();
                                if (meta instanceof SkullMeta)
                                {
                                    org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
                                    try
                                    {
                                        ( (SkullMeta) meta).setOwningPlayer(offlinePlayer);
                                    }
                                    catch (NoSuchMethodError e)
                                    {
                                        try
                                        {
                                            String playerName = offlinePlayer.getName();
                                            if (playerName != null) ((SkullMeta) meta).setOwner(playerName);
                                        }
                                        catch (Exception ignored)
                                        {
                                        }}

                                    }
                                    if (meta != null)
                                    {
                                        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                                        if (lore.length > 0)
                                        {
                                            meta.setLore(Arrays.stream(lore) .map(l -> ChatColor.translateAlternateColorCodes('&', l)) .collect(java.util.stream.Collectors.toList()));
                                        }
                                        head.setItemMeta(meta);
                                    }
                                    return head;
                                }

                                public static void sendColoredMessage(Player player, String message)
                                {
                                    if (player == null || message == null) return;
                                    try
                                    {
                                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                                    }
                                    catch (Exception e)
                                    {
                                        player.sendMessage(message.replace("&", "\u00a7"));
                                    }}

                                    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut)
                                    {
                                        if (player == null) return;
                                        try
                                        {
                                            player.sendTitle( ChatColor.translateAlternateColorCodes('&', title), ChatColor.translateAlternateColorCodes('&', subtitle), fadeIn, stay, fadeOut);
                                        }
                                        catch (NoSuchMethodError e)
                                        {
                                            sendColoredMessage(player, title + " " + subtitle);
                                        }}

                                        private static Method SEND_RAW_METHOD;

                                        public static void sendClickableMessage(Player player, String text, String hoverText, String clickCommand, String action)
                                        {
                                            if (player == null) return;
                                            try
                                            {
                                                String escapedText = escapeJson(text);
                                                String escapedHover = escapeJson(hoverText);
                                                String escapedCmd = escapeJson(clickCommand);
                                                String json = "[{\"text\":\"" + colorize(escapedText) + "\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + colorize(escapedHover) + "\"},\"clickEvent\":{\"action\":\"" + action + "\",\"value\":\"" + escapedCmd + "\"}}]";
                                                    if (SEND_RAW_METHOD == null)
                                                    {
                                                        SEND_RAW_METHOD = getCachedMethod(Player.class, "sendRawMessage", String.class);
                                                    }
                                                    if (SEND_RAW_METHOD != null)
                                                    {
                                                        SEND_RAW_METHOD.invoke(player, json);
                                                        return;
                                                    }}

                                                    catch (Exception ignored)
                                                    {
                                                    }
                                                    sendColoredMessage(player, text + " -> " + clickCommand);
                                                }

                                                public static void sendAcceptDeclineMessage(Player player, String text, String acceptCmd, String declineCmd)
                                                {
                                                    sendClickableMessage(player, text + " [接受]", "点击接受", acceptCmd, "run_command");
                                                    sendClickableMessage(player, "[拒绝]", "点击拒绝", declineCmd, "run_command");
                                                }

                                                public static void sendAcceptMessage(Player player, String text, String acceptCmd)
                                                {
                                                    sendClickableMessage(player, text, "点击接受", acceptCmd, "run_command");
                                                }

                                                private static String escapeJson(String s)
                                                {
                                                    if (s == null) return "";
                                                    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                                                }

                                                private static String colorize(String s)
                                                {
                                                    return ChatColor.translateAlternateColorCodes('&', s);
                                                }}
