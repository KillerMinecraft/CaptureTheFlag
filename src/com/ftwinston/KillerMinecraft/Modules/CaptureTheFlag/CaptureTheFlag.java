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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.PlayerFilter;
import com.ftwinston.KillerMinecraft.Configuration.NumericOption;
import com.ftwinston.KillerMinecraft.Configuration.TeamInfo;
import com.ftwinston.KillerMinecraft.Configuration.ToggleOption;
import com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag.FlagTeamInfo.FlagState;

public class CaptureTheFlag extends GameMode
{
	private final Material floorMaterial = Material.QUARTZ_BLOCK;
	NumericOption setupTime, scoreLimit;
	ToggleOption breakableSpawners;
	boolean finishedSetup;
	int setupProcessID = -1;
	static final long ticksPerMinute = 1200L;
	static final int flagRoomDiameter = 3, respawnDelayTicks = 120;

	LinkedList<ItemSpawner> spawners;
	
	public CaptureTheFlag()
	{
		redTeam.otherTeam = blueTeam;
		blueTeam.otherTeam = redTeam;
		setTeams(teams);
		
		spawners = new LinkedList<ItemSpawner>();
	}
	
	@Override
	public int getMinPlayers() { return 2; } // one player on each team is our minimum

	@Override
	protected Option[] setupOptions()
	{
		setupTime = new NumericOption("Setup time, in minutes", 2, 8, Material.WATCH, 5);
		scoreLimit = new NumericOption("Captures needed to win", 1, 5, Material.BANNER, 3);
		breakableSpawners = new ToggleOption("Breakable item spawners", false, "Controls whether item spawners can be", "destroyed. If disabled, they are invincible.");
		return new Option[] { setupTime, scoreLimit, breakableSpawners };
	}
	
	FlagTeamInfo redTeam = new FlagTeamInfo() {
		@Override
		public String getName() { return "red team"; }
		
		@Override
		public ChatColor getChatColor() { return ChatColor.RED; }
		
		@Override
		public DyeColor getDyeColor() { return DyeColor.RED; }
		
		@Override
		public DyeColor getAccentColor() { return DyeColor.YELLOW; }
	};
	FlagTeamInfo blueTeam = new FlagTeamInfo() {
		@Override
		public String getName() { return "blue team"; }
		
		@Override
		public ChatColor getChatColor() { return ChatColor.BLUE; }
		
		@Override
		public DyeColor getDyeColor() { return DyeColor.BLUE; }
		
		@Override
		public DyeColor getAccentColor() { return DyeColor.GREEN; }
	};
	
	FlagTeamInfo[] teams = new FlagTeamInfo[] { redTeam, blueTeam };
	
	@Override
	public List<String> getHelpMessages(TeamInfo team)
	{
		LinkedList<String> messages = new LinkedList<String>();
		messages.add("Teams have " + setupTime.getValue() + " minutes to place their flags somewhere.");
		messages.add("Once that time is up, the game will start.");
		messages.add("Choose wisely! You will have to defend your flag, but you need to be able to reach it, too.");
		messages.add("Carry the other team's flag back to your own, to capture it.");
		
		if (breakableSpawners.isEnabled())
			messages.add("Item spawners can be destroyed, so defend them carefully.");
		else
			messages.add("Item spawners are indestructible, so place them carefully.");

		return messages;
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
		Material type = l.getBlock().getType();
		if (type == Material.STANDING_BANNER)
			return false;
		
		if (!breakableSpawners.isEnabled() && (type == Material.IRON_PLATE || type == Material.GOLD_PLATE))
			return true;
		
		for (FlagTeamInfo team : teams)
			if (team.flagLocation != null && isWithinProtectionRange(l, team.flagLocation))
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
	public void onPlayerDeath(PlayerDeathEvent event)
	{
		if (!finishedSetup)
			event.setKeepInventory(true);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event)
    {
		Block b = event.getBlock();
		if (b.getType() != Material.STANDING_BANNER)
			return;
		
		if (!finishedSetup)
		{
			event.setCancelled(true);
			return;
		}
		
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
	public void onEvent(InventoryPickupItemEvent event)
	{
		ItemStack stack = event.getItem().getItemStack(); 
		if (stack.getType() != Material.BANNER)
			return;
		
		
		if (event.getInventory().getHolder() == null || !(event.getInventory().getHolder() instanceof Player))
		{
			event.setCancelled(true);
			return;
		}
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(PlayerPickupItemEvent event)
	{
		ItemStack stack = event.getItem().getItemStack(); 
		if (stack.getType() != Material.BANNER)
			return;
		
		FlagTeamInfo flagTeam = null;
		for (FlagTeamInfo team : teams)
			if (event.getItem().getEntityId() == team.droppedFlagEntityID)
			{
				flagTeam = team;
				break;
			}
		
		if (flagTeam == null)
			return;
		
		Player player = event.getPlayer();
		TeamInfo playerTeam = getTeam(player);
		flagTeam.droppedFlagEntityID = -1;
		
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
		if (stack.getItemMeta().getDisplayName().equals(redTeam.getName() + FlagTeamInfo.flagNameSuffix))
			return redTeam;
		else if (stack.getItemMeta().getDisplayName().equals(blueTeam.getName() + FlagTeamInfo.flagNameSuffix))
			return blueTeam;
		else
			return null; 
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event)
	{
		ItemStack stack = event.getItemDrop().getItemStack();
		
		if (!finishedSetup)
		{
			// during setup, you cannot drop any of the "game" items		
			if (stack.getType() == Material.BANNER || stack.getType() == Material.GOLD_PLATE || stack.getType() == Material.IRON_PLATE)
				event.setCancelled(true);
		}
		else if (stack.getType() == Material.BANNER)
		{
			// if player drops the banner, show a message (and update the state)
			FlagTeamInfo flagTeam = determineFlagTeam(stack);
			if (flagTeam == null)
				return;

			broadcastMessage(event.getPlayer().getName() + " dropped the " + flagTeam.getChatColor() + flagTeam.getName() + ChatColor.RESET + " flag!");
			flagTeam.flagState = FlagState.Dropped;
			flagTeam.droppedFlagEntityID = event.getItemDrop().getEntityId();
		}
    }

	@Override
	protected void gameStarted()
	{
		finishedSetup = false;

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
				
				ItemStack powerupSpawner = new ItemStack(Material.GOLD_PLATE);
				meta = powerupSpawner.getItemMeta();
				meta.setDisplayName("Power-up spawner");
				meta.setLore(powerupSpawnerLore);
				powerupSpawner.setItemMeta(meta);
				
				player.getInventory().addItem(equipmentSpawner, powerupSpawner);
			}
	}

	private void startActivePhase()
	{
		finishedSetup = true;
		
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
		
		for (ItemSpawner spawner : spawners)
			spawner.enable();
	}

	@SuppressWarnings("deprecation")
	private void createFlagRoom(FlagTeamInfo team)
	{
		// clear some space, and add a floor
		int floorY = team.flagLocation.getBlockY() - 1;
		World world = getWorld(0);
		
		int flagX = team.flagLocation.getBlockX(), flagZ = team.flagLocation.getBlockZ();
		for (int x = flagX - flagRoomDiameter; x <= flagX + flagRoomDiameter; x++)
			for (int z = flagZ - flagRoomDiameter; z <= flagZ + flagRoomDiameter; z++)
			{
				world.getBlockAt(x, floorY, z).setType(floorMaterial);
				
				for (int y = floorY + 1; y <= floorY + flagRoomDiameter + 1; y++)
				{
					if (x == flagX && z == flagZ && y < floorY + 2)
						continue; // don't break the flag by placing air over it 
					
					world.getBlockAt(x, y, z).setType(Material.AIR);
				}
			}
		
		for (int x = flagX - 1; x <= flagX + 1; x++)
			for (int z = flagZ - 1; z <= flagZ + 1; z++)
			{
				// some floor decoration
				Block b = world.getBlockAt(x, floorY, z);
				b.setType(Material.WOOL);
				b.setData(team.getDyeColor().getWoolData());
				
				if (x == flagX && z == flagZ)
					continue;

				// and pressure plates to "return" the flag by
				b = world.getBlockAt(x, floorY + 1, z);
				b.setType(Material.STONE_PLATE);
			}

	}

	@Override
	public Location getCompassTarget(Player player)
	{
		if (!finishedSetup)
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
			// deliberately fall through to next case, if flag not found
			
		case Dropped: // find flag item ON THE GROUND and point at that
			Item flagItem = findFlagItem(playerTeam.otherTeam);
			if (flagItem != null)
				return flagItem.getLocation();
		}
		
		return null;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEvent(BlockPlaceEvent event)
	{
		Material type = event.getBlock().getType();
		
		if (type == Material.STANDING_BANNER)
		{
			FlagTeamInfo flagTeam = determineFlagTeam(event.getItemInHand());
			
			if (finishedSetup)
				event.setCancelled(true);
			else
			{
				if (flagTeam == null)
					return;
				flagTeam.flagLocation = event.getBlock().getLocation();
				createFlagRoom(flagTeam);
			}
		}
		else if (type == Material.WALL_BANNER)
		{
			event.setCancelled(true);
			if (!finishedSetup)
				event.getPlayer().sendMessage(ChatColor.YELLOW + "Place the flag on the ground, rather than on a wall");
			return;
		}
		else if (type == Material.IRON_PLATE && !finishedSetup)
		{
			spawners.add(new ItemSpawner(this, ItemSpawner.Type.Equipment, event.getBlock().getLocation()));
		}
		else if (type == Material.GOLD_PLATE && !finishedSetup)
		{
			spawners.add(new ItemSpawner(this, ItemSpawner.Type.Powerup, event.getBlock().getLocation()));
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		// if player trod on a protected pressure plate, this is one of the ones surrounding one of the flags
		if(event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.STONE_PLATE || event.getAction() != Action.PHYSICAL || !isLocationProtected(event.getClickedBlock().getLocation(), event.getPlayer()) )
			return;
		
	  	// see if this player is carrying the other team's flag
		if (!event.getPlayer().getInventory().contains(Material.BANNER))
			return;
		
		// are they at their OWN flag?
		FlagTeamInfo playerTeam = (FlagTeamInfo)getTeam(event.getPlayer());
		if (event.getClickedBlock().getLocation().distanceSquared(playerTeam.flagLocation) > 9)
			return;

		broadcastMessage(event.getPlayer().getName() + " captured the " + playerTeam.otherTeam.getChatColor() + playerTeam.otherTeam.getName() + ChatColor.RESET + " flag!");
		event.getPlayer().getInventory().remove(Material.BANNER);
				
		// increase score... if the limit is reached, win the game
		int score = playerTeam.score.getScore() + 1;
		playerTeam.score.setScore(score);
		playerTeam.otherTeam.createBannerBlock(playerTeam.otherTeam.flagLocation);
		
		if (score >= scoreLimit.getValue())
			finishGame();
	}
	
	private Item findFlagItem(FlagTeamInfo team)
	{
		for (Item item : getWorld(0).getEntitiesByClass(Item.class))
			if (item.getEntityId() == team.droppedFlagEntityID)
				return item;
		
		return null;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(org.bukkit.event.entity.EntityCombustEvent event)
	{
		if (event.getEntityType() != EntityType.DROPPED_ITEM)
			return;
		
		Item entity = (Item) event.getEntity();
		if (entity.getItemStack().getType() != Material.BANNER)
			return;
		
		for (FlagTeamInfo team : teams)
			if (team.flagState == FlagState.Dropped && entity.getEntityId() == team.droppedFlagEntityID)
			{
				team.createBannerBlock(team.flagLocation);
				team.flagState = FlagState.Safe;
				team.droppedFlagEntityID = -1;
				broadcastMessage("The " + team.getChatColor() + team.getName() + ChatColor.RESET + " flag was returned");
			}
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(org.bukkit.event.entity.ItemDespawnEvent event)
	{
		if (event.getEntity().getItemStack().getType() != Material.BANNER)
			return;
		
		for (FlagTeamInfo team : teams)
			if (team.flagState == FlagState.Dropped && event.getEntity().getEntityId() == team.droppedFlagEntityID)
			{
				team.createBannerBlock(team.flagLocation);
				team.flagState = FlagState.Safe;
				team.droppedFlagEntityID = -1;
				broadcastMessage("The " + team.getChatColor() + team.getName() + ChatColor.RESET + " flag was returned");
			}		
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(InventoryMoveItemEvent event)
	{
		// hoppers must never suck up flags
		if (event.getItem().getType() == Material.BANNER)
			event.setCancelled(true);
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();

        // don't let players place the flag into any other inventory
        if(top != null && bottom != null && top.getType() != InventoryType.PLAYER)
            if(event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BANNER)
            	if (event.getRawSlot() > top.getSize())
                    event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEvent(PlayerRespawnEvent event)
	{
		if (!finishedSetup)
			return;

		final Player player = event.getPlayer();
		// respawning players can't be seen, briefly
		for (Player other : getOnlinePlayers(new PlayerFilter().exclude(player)))
			other.hidePlayer(player);
		
		getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable() {
			
			@Override
			public void run()
			{
				// when respawning, if not in setup, spawn with a brief period of immobility
				player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, respawnDelayTicks, 50, false, false));
				player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, respawnDelayTicks, 50, false, false));
				player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, respawnDelayTicks, 50, false, false));
				
				final String playerName = player.getName();
				getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable() {
					public void run()
					{
						Player player = Helper.getPlayer(playerName);
						for (Player other : getOnlinePlayers(new PlayerFilter().exclude(player)))
							other.showPlayer(player);
					}
				}, (long)respawnDelayTicks);				
			}
		});
	}
	
	@EventHandler(priority = EventPriority.HIGH)
	public void onEvent(PrepareItemCraftEvent event)
	{
		// prevent crafting banners, golden plates or iron plates
		Material type = event.getInventory().getResult().getType();
		
		if (type == Material.BANNER || type == Material.GOLD_PLATE || type == Material.IRON_PLATE)
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
		
		for (ItemSpawner spawner : spawners)
			spawner.disable();
	}

	@Override
	public Location getSpawnLocation(Player player)
	{
		if (!finishedSetup)
		{
			Location spawnPoint = getWorld(0).getSpawnLocation();
			return Helper.getSafeSpawnLocationNear(spawnPoint);
		}
		
		FlagTeamInfo team = (FlagTeamInfo)getTeam(player);
		return team.flagLocation;
	}
}
