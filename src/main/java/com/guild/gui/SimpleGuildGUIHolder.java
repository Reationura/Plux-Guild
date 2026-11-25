package com.guild.gui;
import org.bukkit.inventory.Inventory;

public final class SimpleGuildGUIHolder implements GuildGUIHolder
{
    private final String guiType;

    public SimpleGuildGUIHolder(String guiType)
    {
        this.guiType = guiType;
    }

@Override
public String getGuiType()
    {
        return guiType;
    }

@Override
public Inventory getInventory()
    {
        return null;
    }}
