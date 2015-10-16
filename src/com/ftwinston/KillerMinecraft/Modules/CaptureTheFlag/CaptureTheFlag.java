package com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.PlayerFilter;
import com.ftwinston.KillerMinecraft.Configuration.NumericOption;
import com.ftwinston.KillerMinecraft.Configuration.TeamInfo;
import com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag.FlagTeamInfo.FlagState;

public class CaptureTheFlag extends GameMode
{
	private final Material floorMaterial = Material.QUARTZ_BLOCK;
	NumericOption setupTime, scoreLimit;
	boolean inSetup;
	int setupProcessID = -1;
	static final long ticksPerMinute = 1200L;
	static final int flagRoomDiameter = 3;

	LinkedList<Location> powerupSpawners, equipmentSpawners;
	
	public CaptureTheFlag()
	{
		redTeam.otherTeam = blueTeam;
		blueTeam.otherTeam = redTeam;
		setTeams(teams);
		
		powerupSpawners = new LinkedList<Location>();
		equipmentSpawners = new LinkedList<Location>();
	}
	
	@Override
	public int getMinPlayers() { return 2; } // one player on each team is our minimum

	@Override
	protected Option[] setupOptions()
	{
		setupTime = new NumericOption("Setup time, in minutes", 2, 8, Material.WATCH, 5);
		scoreLimit = new NumericOption("Captures needed to win", 1, 5, Material.BANNER, 3);
		return new Option[] { setupTime, scoreLimit };
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
		if (redTeam.flagLocation != null && isWithinProtectionRange(l, redTeam.flagLocation))
			return true;
		
		if (blueTeam.flagLocation != null && isWithinProtectionRange(l, blueTeam.flagLocation))
			return true;
		
		return false;
	}
	
	private boolean isWithinProtectionRange(Location testLocation, Location flagLocation)
	{
		if (Math.abs(testLocation.getBlockX() - flagLocation.getBlockX()) > flagRoomDiameter)
			return false;
		
		if (Math.abs(testLocation.getBlockZ() - flagLocation.getBlockZ()) > flagRoomDiameter)
			return false;
		
		if (testLocation.getBlockY() < flagLocation.getBlockY() - 1)
			return false;

		if (testLocation.getBlockY() > flagLocation.getBlockY() + flagRoomDiameter)
			return false;
			
		return true;
	}

	@Override
	public Environment[] getWorldsToGenerate() { return new Environment[] { Environment.NORMAL }; }
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event)
    {
		Block b = event.getBlock();
		if (b.getType() != Material.STANDING_BANNER)
			return;		
		
		FlagTeamInfo flagTeam; 
		if (blockLocationsEqual(b.getLocation(), redTeam.flagLocation))
		{
			// cancel unless broken by member of other team
			if (event.getPlayer() != null && getTeam(event.getPlayer()) != blueTeam)
			{
				event.setCancelled(true);
				return;
			}
			flagTeam = redTeam;
		}
		else if (blockLocationsEqual(b.getLocation(), blueTeam.flagLocation))
		{
			// cancel unless broken by member of other team
			if (event.getPlayer() != null && getTeam(event.getPlayer()) != redTeam)
			{
				event.setCancelled(true);
				return;
			}
			flagTeam = blueTeam;
		}
		else
			return; // some other banner... ?
		
		// show alert, cancel drop, and put directly into inventory
		broadcastMessage(event.getPlayer().getName() + " picked up the " + flagTeam.getChatColor() + flagTeam.getName() + ChatColor.RESET + " flag!");
		event.setCancelled(true);
		event.getBlock().setType(Material.AIR);
		event.getPlayer().getInventory().addItem(flagTeam.createBannerItem());
		flagTeam.flagState = FlagState.Carried;
	}
	
	private boolean blockLocationsEqual(Location a, Location b)
	{
		return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(InventoryPickupItemEvent event) throws EventException
	{
		ItemStack stack = event.getItem().getItemStack(); 
		if (stack.getType() != Material.BANNER)
			return;
		
		FlagTeamInfo flagTeam = determineFlagTeam(stack);
		if (flagTeam == null)
			return;
		
		if (event.getInventory().getHolder() == null || !(event.getInventory().getHolder() instanceof Player))
		{
			event.setCancelled(true);
			return;
		}
		
		Player player = (Player)event.getInventory().getHolder();
		TeamInfo playerTeam = getTeam(player);
		
		if (playerTeam == flagTeam)
		{
			broadcastMessage(player.getName() + " recovered the " + flagTeam.getChatColor() + flagTeam.getName() + ChatColor.RESET + " flag!");
			
			event.setCancelled(true);
			event.getItem().remove();
			flagTeam.createBannerBlock(flagTeam.flagLocation);
			flagTeam.flagState = FlagState.Safe;
		}
		else
		{
			broadcastMessage(player.getName() + " picked up the " + flagTeam.getChatColor() + flagTeam.getName() + ChatColor.RESET + " flag!");
			flagTeam.flagState = FlagState.Carried;
		}
	}
	
	private FlagTeamInfo determineFlagTeam(ItemStack stack)
	{
		if (stack.getItemMeta().getDisplayName() == redTeam.getName() + FlagTeamInfo.flagNameSuffix)
			return redTeam;
		else if (stack.getItemMeta().getDisplayName() == blueTeam.getName() + FlagTeamInfo.flagNameSuffix)
			return blueTeam;
		else
			return null; 
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event)
	{
		ItemStack stack = event.getItemDrop().getItemStack();
		
		if (inSetup)
		{
			// during setup, you cannot drop any of the "game" items		
			if (stack.getType() == Material.BANNER || stack.getType() == Material.GOLD_PLATE || stack.getType() == Material.IRON_PLATE)
				event.setCancelled(true);
		}
		else
		{
			// if player drops the banner, show a message (and update the state)
			FlagTeamInfo flagTeam = determineFlagTeam(stack);
			if (flagTeam == null)
				return;

			broadcastMessage(event.getPlayer().getName() + " dropped the " + flagTeam.getChatColor() + flagTeam.getName() + ChatColor.RESET + " flag!");
			flagTeam.flagState = FlagState.Dropped;
			
			flagTeam.droppedFlagID = event.getItemDrop().getEntityId();
		}
    }

	@Override
	protected void gameStarted()
	{
		inSetup = true;

		// start setup timer task
		setupProcessID = getScheduler().scheduleSyncRepeatingTask(getPlugin(), new Runnable() {
			int stepNumber = 0;
			public void run()
			{
				++stepNumber;
				
				switch (stepNumber)
				{
				case 1:
					broadcastMessage(ChatColor.YELLOW + "1 minute remaining before the game starts"); break;
				case 4:
					broadcastMessage(ChatColor.YELLOW + "30 seconds remaining before the game starts"); break;
				case 6:
					broadcastMessage(ChatColor.YELLOW + "10 seconds remaining before the game starts"); break;
				case 7:
					broadcastMessage(ChatColor.YELLOW + "The game has started - capture the other team's flag! (Your compass will point at the enemy flag.)");
					startActivePhase();
					getScheduler().cancelTask(setupProcessID);
					setupProcessID = -1;
					break; // actually start the game
				default:
					return;
				}
			}
		}, setupTime.getValue() * ticksPerMinute - ticksPerMinute, 200L); // initial wait: full setup time minus 1 minute, then tick every 10s
		
		giveTeamItems(redTeam);
		giveTeamItems(blueTeam);
	}
	
	private void giveTeamItems(FlagTeamInfo team)
	{
		List<Player> players = getOnlinePlayers(new PlayerFilter().team(team));

		// give flag to one player on each team
		Player flagCarrier = Helper.selectRandom(players);
		flagCarrier.getInventory().addItem(team.createBannerItem());
		
		ArrayList<String> equipmentSpawnerLore = new ArrayList<String>();
		equipmentSpawnerLore.add("Place this before the game starts,");
		equipmentSpawnerLore.add("and it will spawn weapons and equipment");
		
		ArrayList<String> powerupSpawnerLore = new ArrayList<String>();
		powerupSpawnerLore.add("Place this before the game starts,");
		powerupSpawnerLore.add("and it will spawn power-ups and health");
		
		// give item spawner and powerup spawner to every other player
		for (Player player : players)
			if (player != flagCarrier)
			{
				ItemStack equipmentSpawner = new ItemStack(Material.IRON_PLATE);
				ItemMeta meta = equipmentSpawner.getItemMeta();
				meta.setDisplayName("Equipment spawner");
				meta.setLore(equipmentSpawnerLore);
				equipmentSpawner.setItemMeta(meta);
				
				ItemStack powerupSpawner = new ItemStack(Material.IRON_PLATE);
				meta = powerupSpawner.getItemMeta();
				meta.setDisplayName("Power-up spawner");
				meta.setLore(powerupSpawnerLore);
				powerupSpawner.setItemMeta(meta);
				
				player.getInventory().addItem(equipmentSpawner, powerupSpawner);
			}
	}

	private void startActivePhase()
	{
		inSetup = false;
		
		for (Player player : getOnlinePlayers())
		{
			PlayerInventory inv = player.getInventory();
			
			if (inv.contains(Material.BANNER))
			{
				FlagTeamInfo team = (FlagTeamInfo)getTeam(player);
				if (team.flagLocation == null)
				{
					// force-place this team's banner, wherever the carrying player currently is
					team.flagLocation = player.getLocation();
					team.createBannerBlock(team.flagLocation);
					createFlagRoom(team);
				}
				
				inv.remove(Material.BANNER);
			}
			
			// remove any item spawner and powerup spawner items from each player
			inv.remove(Material.GOLD_PLATE);
			inv.remove(Material.IRON_PLATE);

			// give every player a compass
			inv.addItem(new ItemStack(Material.COMPASS, 1));
		}
		
		for (FlagTeamInfo team : teams)
		{
			if (team.flagLocation != null)
				continue;
			
			// something has gone wrong ... place the flag on any player in this team
			List<Player> players = getOnlinePlayers(new PlayerFilter().team(team));
			if (players.isEmpty())
			{
				finishGame();
			}
			else
			{
				team.flagLocation = players.get(0).getLocation();
				team.createBannerBlock(team.flagLocation);
				createFlagRoom(team);
			}
		}
		
		int someProcessID = getScheduler().scheduleSyncRepeatingTask(getPlugin(), new Runnable() {
			public void run()
			{
				// TODO: spawn equipment or something ... each spawner should have its own process, really
				// equipment spawn timer should be updated when the item is actually picked up

				// equipment spawners should spawn weapons/equipment/armor. Armor should be APPLIED AUTOMATICALLY WHEN PICKED UP, if not already wearing.
			}
		}, 0L, 600L); // equipment spawns right away, and then every 30s
		
		int someOtherProcessID = getScheduler().scheduleSyncRepeatingTask(getPlugin(), new Runnable() {
			public void run()
			{
				// TODO: spawn powerups or something ... each spawner should have its own process, really
				// powerup spawn timer should be updated when the item is actually picked up
				
				// powerup spawners should spawn powerup potions THAT ARE APPLIED AUTOMATICALLY WHEN PICKED UP
			}
		}, random.nextInt(30) + 15L, 600L); // powerups spawn after a random initial delay, and then every 45s
	}

	private void createFlagRoom(FlagTeamInfo team)
	{
		// clear some space, and add a floor
		int floorY = team.flagLocation.getBlockY() - 1;
		World world = getWorld(0);
		
		for (int x = team.flagLocation.getBlockX() - flagRoomDiameter; x <= team.flagLocation.getBlockX() + flagRoomDiameter; x++)
			for (int z = team.flagLocation.getBlockZ() - flagRoomDiameter; z <= team.flagLocation.getBlockZ() + flagRoomDiameter; z++)
			{
				world.getBlockAt(x, floorY, z).setType(floorMaterial);
				
				for (int y = floorY + 1; y <= floorY + flagRoomDiameter; y++)
				{
					if (x == team.flagLocation.getBlockX() && z == team.flagLocation.getBlockZ() && y < team.flagLocation.getBlockY() + 2)
						continue; // don't break the flag by placing air over it 
					
					world.getBlockAt(x, floorY, z).setType(Material.AIR);
				}
			}
	}

	@Override
	public Location getCompassTarget(Player player)
	{
		if (inSetup)
			return null;
		
		FlagTeamInfo playerTeam = (FlagTeamInfo)getTeam(player);
		
		// for the flag carrier, compass should point at their own flag location
		if (player.getInventory().contains(Material.BANNER))
		{
			return playerTeam.flagLocation;
		}
		
		switch (playerTeam.otherTeam.flagState)
		{
		case Safe: // point at enemy flag room
			return playerTeam.otherTeam.flagLocation;
			
		case Carried: // point at friendly carrier of enemy flag
			for (Player carrier : getOnlinePlayers(new PlayerFilter().team(playerTeam)))
				if (carrier.getInventory().contains(Material.BANNER))
					return carrier.getLocation();
			// deliberately flow through to next case, if flag not found
			
		case Dropped:
			; // TODO: find flag item ON THE GROUND and point at that
		}
		
		return null;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onEvent(BlockPlaceEvent event) throws EventException
	{
		Material type = event.getBlock().getType();
		if (type == Material.STANDING_BANNER)
		{
			FlagTeamInfo flagTeam = determineFlagTeam(event.getItemInHand());
			
			if (inSetup)
			{
				flagTeam.flagLocation = event.getBlock().getLocation();
				createFlagRoom(flagTeam);
			}
			else
			{
				// TODO: when flag is placed by a player, adjacent to their own flag, give them a point and "respawn" it. otherwise when flag is placed otherwise, disallow
				// how does this interact with protection?
			
				
				// increase score... if the limit is reached, win the game
				int score = flagTeam.otherTeam.score.getScore() + 1;
				flagTeam.otherTeam.score.setScore(score);
				
				if (score >= scoreLimit.getValue())
					finishGame();
			}
		}
		else if (type == Material.WALL_BANNER)
		{
			event.setCancelled(true);
			event.getPlayer().sendMessage(ChatColor.YELLOW + "Place the flag on the ground, rather than on a wall");
			return;
		}
		else if (type == Material.IRON_PLATE && inSetup)
		{
			equipmentSpawners.add(event.getBlock().getLocation());
		}
		else if (type == Material.GOLD_PLATE && inSetup)
		{
			powerupSpawners.add(event.getBlock().getLocation());
		}
	}
	
	// TODO: update team flag state when the item is destroyed
	
	// TODO: flags auto-return 30 seconds after being dropped
	
	// TODO: don't let players place the flag into any other inventory
	
	// TODO: prevent renaming banners, ever
	
	// TODO: when respawning, if not in setup, spawn with a brief period of invisiblity/immobility
	
	@EventHandler(priority = EventPriority.HIGH)
	public void onEvent(PrepareItemCraftEvent event) throws EventException
	{
		// prevent crafting banners at all, and golden plates or iron plates during setup
		Material type = event.getInventory().getResult().getType();
		
		if (type == Material.BANNER)
			event.getInventory().setResult(null);
		else if (inSetup && (type == Material.GOLD_PLATE || type == Material.IRON_PLATE))
			event.getInventory().setResult(null);
	}
	
	@Override
	protected void gameFinished()
	{
		if (redTeam.score.getScore() > blueTeam.score.getScore())
			broadcastMessage("The " + blueTeam.getChatColor() + "blue team " + ChatColor.RESET + " win the game!");
		else if (redTeam.score.getScore() < blueTeam.score.getScore())
			broadcastMessage("The " + redTeam.getChatColor() + "red team " + ChatColor.RESET + "win the game!");
		else
			broadcastMessage("Game drawn.");
		
		if (setupProcessID != -1)
		{
			getScheduler().cancelTask(setupProcessID);
			setupProcessID = -1;
		}
	}

	@Override
	public Location getSpawnLocation(Player player)
	{
		if (inSetup)
		{
			Location spawnPoint = getWorld(0).getSpawnLocation();
			return Helper.getSafeSpawnLocationNear(spawnPoint);
		}
		
		FlagTeamInfo team = (FlagTeamInfo)getTeam(player);
		return team.flagLocation;
	}
}
