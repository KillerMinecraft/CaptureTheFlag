package com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
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
		banner.setBaseColor(getDyeColor());
		banner.addPattern(new Pattern(getAccentColor(), PatternType.STRIPE_DOWNRIGHT));
		
		stack.setItemMeta(banner);
		
		return stack;
	}
	
	public Block createBannerBlock(Location loc)
	{
		Block b = loc.getBlock();
		b.setType(Material.STANDING_BANNER);
		Banner banner = (Banner)b.getState();
		banner.setBaseColor(getDyeColor());
		banner.addPattern(new Pattern(getAccentColor(), PatternType.STRIPE_DOWNRIGHT));
		
		banner.update();
		return b;
	}

	protected abstract DyeColor getAccentColor();
	
	enum FlagState
	{
		Safe,
		Dropped,
		Carried,
	}
}
