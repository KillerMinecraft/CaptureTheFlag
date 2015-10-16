package com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.scoreboard.Score;

import com.ftwinston.KillerMinecraft.Configuration.TeamInfo;

public abstract class FlagTeamInfo extends TeamInfo {
	public Score score;
	public Location flagLocation;
	public FlagState flagState = FlagState.Safe;
	public int droppedFlagEntityID;
	public FlagTeamInfo otherTeam;
	public static final String flagNameSuffix = " flag"; 
	
	public ItemStack createBannerItem() 
	{
		ItemStack stack = new ItemStack(Material.BANNER);
		BannerMeta banner = (BannerMeta)stack.getItemMeta();
		
		banner.setDisplayName(getName() + flagNameSuffix);
		setupBanner(banner);
		
		stack.setItemMeta(banner);
		
		return stack;
	}
	
	public Block createBannerBlock(Location loc)
	{
		Block b = loc.getBlock();
		b.setType(Material.STANDING_BANNER);
		Banner banner = (Banner)b.getState();
		
		setupBanner(banner);
		
		banner.update();
		return b;
	}

	protected abstract void setupBanner(BannerMeta banner);
	protected abstract void setupBanner(Banner banner);
	
	enum FlagState
	{
		Safe,
		Dropped,
		Carried,
	}
}
