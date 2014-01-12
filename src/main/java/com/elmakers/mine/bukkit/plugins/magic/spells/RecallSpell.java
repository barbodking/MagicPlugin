package com.elmakers.mine.bukkit.plugins.magic.spells;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.EntityDeathEvent;

import com.elmakers.mine.bukkit.plugins.magic.Spell;
import com.elmakers.mine.bukkit.plugins.magic.SpellEventType;
import com.elmakers.mine.bukkit.plugins.magic.SpellResult;
import com.elmakers.mine.bukkit.utilities.EffectUtils;
import com.elmakers.mine.bukkit.utilities.ParticleType;
import com.elmakers.mine.bukkit.utilities.borrowed.ConfigurationNode;

public class RecallSpell extends Spell
{
	public Location location;
	public boolean isActive;
	private boolean autoDropOnDeath = true;
	private int disableDistance = 5;

	@Override
	public SpellResult onCast(ConfigurationNode parameters) 
	{
		autoDropOnDeath = parameters.getBoolean("auto_resurrect", true);
		boolean autoSpawn = parameters.getBoolean("allow_spawn", true);

		if (autoDropOnDeath)
		{
			spells.registerEvent(SpellEventType.PLAYER_DEATH, this);
		}

		String typeString = parameters.getString("type", "");
		if (typeString.equals("spawn"))
		{
			castMessage("Returning you home");
			player.teleport(tryFindPlaceToStand(player.getWorld().getSpawnLocation()));
			return SpellResult.SUCCESS; 
		}

		if (getYRotation() > 80)
		{
			if (!isActive && autoSpawn)
			{
				castMessage("Returning you home");
				player.teleport(tryFindPlaceToStand(player.getWorld().getSpawnLocation()));
			}
			else
			{
				if (!isActive) return SpellResult.NO_TARGET;

				double distanceSquared = player.getLocation().distanceSquared(location);

				if (distanceSquared < disableDistance * disableDistance && autoSpawn)
				{
					castMessage("Returning you home");
					player.teleport(tryFindPlaceToStand(player.getWorld().getSpawnLocation()));
				}
				else
				{
					castMessage("Returning you to your marker");
					player.teleport(location);
				}
			}
			return SpellResult.SUCCESS;
		}

		if (!isActive)
		{
			return placeMarker(getTargetBlock());
		}

		return placeMarker(getTargetBlock());
	}

	protected boolean removeMarker()
	{
		if (!isActive || location == null) return false;
		isActive = false;
		return true;
	}

	protected SpellResult placeMarker(Block target)
	{
		if (target == null)
		{
			castMessage("No target");
			return SpellResult.NO_TARGET;
		}
		Block targetBlock = target.getRelative(BlockFace.UP);
		if (targetBlock.getType() != Material.AIR)
		{
			targetBlock = getFaceBlock();
		}
		if (targetBlock.getType() != Material.AIR)
		{
			castMessage("Can't place a marker there");
			return SpellResult.NO_TARGET;
		}

		if (removeMarker())
		{
			castMessage("You move your recall marker");
		}
		else
		{
			castMessage("You place a recall marker");
		}

		location = player.getLocation();
		location.setX(targetBlock.getX());
		location.setY(targetBlock.getY());
		location.setZ(targetBlock.getZ());

		player.setCompassTarget(location);
		EffectUtils.playEffect(targetBlock.getLocation(), ParticleType.CLOUD, 1, 1);
		
		isActive = true;

		return SpellResult.SUCCESS;
	}

	@Override
	public void onPlayerDeath(EntityDeathEvent event)
	{
		if (autoDropOnDeath)
		{
			if (!isActive)
			{
				sendMessage("Use recall to return to where you died");
				placeMarker(getPlayerBlock());
			}
		}
	}

	@Override
	public void onLoad(ConfigurationNode node)
	{
		isActive = node.getBoolean("active", false);
		location = node.getLocation("location");
	}

	@Override
	public void onSave(ConfigurationNode node)
	{
		node.setProperty("active", isActive);
		node.setProperty("location", location);
	}
}
