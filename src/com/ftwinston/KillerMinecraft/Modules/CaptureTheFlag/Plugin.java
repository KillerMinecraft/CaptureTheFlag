package com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag;

import org.bukkit.Material;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.GameModePlugin;

public class Plugin extends GameModePlugin
{
	@Override
	public Material getMenuIcon() { return Material.BANNER; }
	
	@Override
	public String[] getDescriptionText() { return new String[] {"Two teams choose where to place their", "flags, and then defend them while", "trying to capture the other team's."}; }
	
	@Override
	public GameMode createInstance()
	{
		return new CaptureTheFlag();
	}
}