package com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.scoreboard.Score;

import com.ftwinston.KillerMinecraft.Configuration.TeamInfo;

public abstract class FlagTeamInfo extends TeamInfo
{
	private CaptureTheFlag game;
	public Score score;
	public Location flagLocation;
	public FlagState flagState = FlagState.Safe;
	public int droppedFlagEntityID;
	public FlagTeamInfo otherTeam;
	public static final String flagNameSuffix = " flag"; 
	
	public FlagTeamInfo(CaptureTheFlag game)
	{
		this.game = game;
	}
	
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
	
	public Block createBannerBlock()
	{
		Block b = flagLocation.getBlock();
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

	private int returnProcessID;
	public void setFlagState(FlagState state)
	{
		if (flagState == state)
			return;
		
		flagState = state;
		
		if (state != FlagState.Dropped)
		{
			droppedFlagEntityID = -1;
			if (returnProcessID != -1)
			{
				game.getScheduler().cancelTask(returnProcessID);
				returnProcessID = -1;
			}
			
			if (state == FlagState.Safe)
				createBannerBlock();
		}
		else
		{
			returnProcessID = game.getScheduler().scheduleSyncDelayedTask(game.getPlugin(), new Runnable() {
				@Override
				public void run()
				{
					game.broadcastMessage("The " + getChatColor() + getName() + ChatColor.RESET + " flag was returned");
					for (Item item : game.getWorld(0).getEntitiesByClass(Item.class))
						if (item.getEntityId() == droppedFlagEntityID)
						{
							item.remove();
							break;
						}
					
					returnProcessID = -1;
					
					setFlagState(FlagState.Safe);
				}
			}, CaptureTheFlag.automaticFlagRecoveryDelay);
		}
	}
}
