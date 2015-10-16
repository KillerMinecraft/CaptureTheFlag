package com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.Configuration.NumericOption;
import com.ftwinston.KillerMinecraft.Configuration.TeamInfo;

public class CaptureTheFlag extends GameMode
{
	private final Material floorMaterial = Material.QUARTZ_BLOCK;
	NumericOption setupTime;
	boolean inSetup;
	
	@Override
	public int getMinPlayers() { return 2; } // one player on each team is our minimum

	@Override
	protected Option[] setupOptions()
	{
		setupTime = new NumericOption("Setup time, in minutes", 2, 8, Material.WATCH, 4);
		return new Option[] { setupTime };
	}
	
	FlagTeamInfo redTeam = new FlagTeamInfo() {
		@Override
		public String getName() { return "red team"; }
		@Override
		public ChatColor getChatColor() { return ChatColor.RED; }
		
		protected void setupBanner(org.bukkit.block.Banner banner)
		{
			banner.setBaseColor(DyeColor.RED);
			banner.addPattern(new Pattern(DyeColor.YELLOW, PatternType.STRIPE_DOWNRIGHT));
		};
		
		protected void setupBanner(org.bukkit.inventory.meta.BannerMeta banner)
		{
			banner.setBaseColor(DyeColor.RED);
			banner.addPattern(new Pattern(DyeColor.YELLOW, PatternType.STRIPE_DOWNRIGHT));
		};
	};
	FlagTeamInfo blueTeam = new FlagTeamInfo() {
		@Override
		public String getName() { return "blue team"; }
		@Override
		public ChatColor getChatColor() { return ChatColor.BLUE; }
		
		protected void setupBanner(org.bukkit.block.Banner banner)
		{
			banner.setBaseColor(DyeColor.BLUE);
			banner.addPattern(new Pattern(DyeColor.GREEN, PatternType.STRIPE_DOWNLEFT));
		};
		
		protected void setupBanner(org.bukkit.inventory.meta.BannerMeta banner)
		{
			banner.setBaseColor(DyeColor.BLUE);
			banner.addPattern(new Pattern(DyeColor.GREEN, PatternType.STRIPE_DOWNLEFT));
		};

	};
	
	FlagTeamInfo[] teams = new FlagTeamInfo[] { redTeam, blueTeam };
	
	public CaptureTheFlag()
	{
		setTeams(teams);
	}
	
	@Override
	public String getHelpMessage(int num, TeamInfo team)
	{
		switch ( num )
		{
			case 0:
				return "Teams have " + setupTime.getValue() + " minutes to place their flags somewhere.";
			case 1:
				return "Once that time is up, the game will start.";
			case 2:
				return "Choose wisely! You will have to defend the flag, but you need to be able to reach it, too.";
			case 3:
				return "Place the other team's flag next to yours to capture it.";
			default:
				return null;
		}
	}
	
	Objective objective;
	
	@Override
	public Scoreboard createScoreboard()
	{
		Scoreboard scoreboard = super.createScoreboard();
		
		objective = scoreboard.registerNewObjective("captures", "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		objective.setDisplayName("Captures");
		
		String name = redTeam.getChatColor() + redTeam.getName();
		redTeam.score = objective.getScore(name);
		redTeam.score.setScore(0);
		
		name = blueTeam.getChatColor() + blueTeam.getName();
		blueTeam.score = objective.getScore(name);
		blueTeam.score.setScore(0);
		
		return scoreboard;
	}

	@Override
	public boolean isLocationProtected(Location l, Player player)
	{
		// TODO: the areas around each flag are protected
		return false;
	}
	
	@Override
	public Environment[] getWorldsToGenerate() { return new Environment[] { Environment.NORMAL }; }
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event)
    {
		Block b = event.getBlock();
		if (b.getType() != Material.STANDING_BANNER)
			return;

		// TODO: if this flag is one of the player flags, show alert
		// and don't let players take their own flag
	}

	@Override
	protected void gameStarted()
	{
		inSetup = true;

		// TODO: start setup timer task, and give a flag to one player on each team
	}

	// TODO: handle respawns, if not in setup, spawn on your own team's flag position, with a brief
	// period of invisiblity/immobility
	
	// TODO: when flag is placed for the first time, set the team's flag location, and make space/floor around it
	
	// TODO: when flag is placed by a player, adjacent to their own flag, give them a point and "respawn" it
	
	// TODO: when flag is placed otherwise, disallow
	
	// TODO: when flag is broken/picked up by other team, show warning. Otherwise, disallow.
	
	@Override
	protected void gameFinished()
	{
		if (redTeam.score.getScore() > blueTeam.score.getScore())
			broadcastMessage("The " + blueTeam.getChatColor() + "blue team " + ChatColor.RESET + " win the game!");
		else if (redTeam.score.getScore() < blueTeam.score.getScore())
			broadcastMessage("The " + redTeam.getChatColor() + "red team " + ChatColor.RESET + "win the game!");
		else
			broadcastMessage("Game drawn.");
	}

	@Override
	public Location getSpawnLocation(Player player)
	{
		// TODO: if game has "started", spawn players on their own flags
		
		Location spawnPoint = getWorld(0).getSpawnLocation();
		return Helper.getSafeSpawnLocationNear(spawnPoint);
	}
}
