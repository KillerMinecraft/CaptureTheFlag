package com.ftwinston.KillerMinecraft.Modules.CaptureTheFlag;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

public class ItemSpawner
{
	enum Type
	{
		Equipment,
		Powerup
	}
	
	private CaptureTheFlag game;
	private Type type;
	private Location location;
	private int scheduledTaskID = -1, spawnedItemID;
	private Runnable checkPickupTask, spawnItemTask;
	static final long checkInterval = 250L;
	
	public ItemSpawner(CaptureTheFlag g, Type t, Location l)
	{
		game = g;
		type = t;
		location = l.add(0.5, 0.5, 0.5);
		
		checkPickupTask = new Runnable() {
			public void run()
			{
				// see if item has been picked up
				if (checkBroken() || droppedEntityExists())
					return;
				
				// item has been picked up, so prepare to spawn another item
				long delay;
				switch (type)
				{
				default:
				case Equipment:
					delay = 400L; break;
				case Powerup:
					delay = 1200L; break;
				}
				
				scheduledTaskID = game.getScheduler().scheduleSyncDelayedTask(game.getPlugin(), spawnItemTask, delay);
			}
		};
		
		spawnItemTask = new Runnable() {
			public void run()
			{
				if (checkBroken())
					return;
				
				// create item
				ItemStack stack;
				
				switch (type)
				{
				default:
				case Equipment:
					stack = createEquipment(); break;
				case Powerup:
					stack = createPowerup(); break;
				}
				
				Item item = location.getWorld().dropItem(location, stack);
				item.setVelocity(new Vector(0,0,0));
				spawnedItemID = item.getEntityId();
				
				// start checking to see if item was picked up
				scheduledTaskID = game.getScheduler().scheduleSyncRepeatingTask(game.getPlugin(), checkPickupTask, 1, checkInterval);
			}
		};
	}

	public void enable()
	{
		long delay = 0;
		switch (type)
		{
		case Equipment:
			delay = game.random.nextInt(20); break;
		case Powerup:
			delay = game.random.nextInt(200) + 100L; break;
		}
		
		scheduledTaskID = game.getScheduler().scheduleSyncDelayedTask(game.getPlugin(), spawnItemTask, delay);
	}
	
	public void disable()
	{
		if (scheduledTaskID != -1)
		{
			game.getScheduler().cancelTask(scheduledTaskID);
			scheduledTaskID = -1;
		}
	}

	private boolean checkBroken()
	{
		if (location.getBlock().getType() == Material.IRON_PLATE || location.getBlock().getType() == Material.GOLD_PLATE)
			return false;
			
		disable();
		game.spawners.remove(this);
		return true;
	}
	
	private boolean droppedEntityExists()
	{
		for (Item item : game.getWorld(0).getEntitiesByClass(Item.class))
			if (item.getEntityId() == spawnedItemID)
				return true;
		
		return false;
	}
	
	private ItemStack createEquipment()
	{
		switch (game.random.nextInt(18))
		{
		default:
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
			return new ItemStack(Material.IRON_PICKAXE);
		case 5:
		case 6:
		case 7:
		case 8:
		case 9:
			return new ItemStack(Material.IRON_SWORD);
		case 10:
		case 11:
			return new ItemStack(Material.BOW);
		case 12:
		case 13:
			return new ItemStack(Material.ARROW, 12);
		case 14:
			return new ItemStack(Material.IRON_CHESTPLATE);
		case 15:
			return new ItemStack(Material.IRON_LEGGINGS);
		case 16:
			return new ItemStack(Material.IRON_HELMET);
		case 17:
			return new ItemStack(Material.IRON_BOOTS);
		}
	}

	private ItemStack createPowerup()
	{
		switch (game.random.nextInt(20))
		{
		default:
		case 0:
			return new ItemStack(Material.TNT, 4);
		case 1:
			return new ItemStack(Material.DIAMOND, 2);
		case 2:
			return new ItemStack(Material.BUCKET);
		case 3:
			return new ItemStack(Material.REDSTONE, 8);
		case 4:
			return new ItemStack(Material.COAL, 16);
		case 5:
			return new ItemStack(Material.SLIME_BLOCK, 2);
		case 6:
			return new ItemStack(Material.SOUL_SAND, 6);
		case 7:
		case 8:
			return createPotion(PotionType.INVISIBILITY);
		case 9:
		case 10:
			return createPotion(PotionType.SPEED);
		case 11:
		case 12:
			return createPotion(PotionType.STRENGTH);
		case 13:
		case 14:
			return createPotion(PotionType.INSTANT_HEAL);
		case 15:
			return createPotion(PotionType.JUMP);
		case 16:
			return createPotion(PotionType.NIGHT_VISION);
		case 17:
			return createPotion(PotionType.REGEN);
		case 18:
			return createPotion(PotionType.WATER_BREATHING);
		case 19:
			return createPotion(PotionType.FIRE_RESISTANCE);
		}
	}

	private ItemStack createPotion(PotionType type)
	{
		ItemStack stack = new ItemStack(Material.POTION);
		Potion potion = new Potion(type);
		potion.apply(stack);
		return stack;
	}
}
