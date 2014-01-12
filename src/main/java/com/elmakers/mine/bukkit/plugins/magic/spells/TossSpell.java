package com.elmakers.mine.bukkit.plugins.magic.spells;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.plugins.magic.Spell;
import com.elmakers.mine.bukkit.plugins.magic.SpellResult;
import com.elmakers.mine.bukkit.utilities.borrowed.ConfigurationNode;

public class TossSpell extends Spell
{ 
	@Override
	public SpellResult onCast(ConfigurationNode parameters) 
	{
		Location location = player.getLocation();
		if (!hasBuildPermission(location)) {
			return SpellResult.INSUFFICIENT_PERMISSION;
		}

		location.setY(location.getY() - 1);
		Material material = location.getBlock().getType();
		if (material == Material.AIR) material = Material.SAND;
		byte data = 0;
		ItemStack buildWith = getBuildingMaterial();
		if (buildWith != null)
		{
			material = buildWith.getType();
			data = getItemData(buildWith);
		}
		
		int tossCount = 1;
		tossCount = parameters.getInt("count", tossCount);
		tossCount = (int)(playerSpells.getPowerMultiplier() * tossCount);	
		float speed = 0.6f;
		speed = (float)parameters.getDouble("speed", speed);
		
		Vector direction = player.getLocation().getDirection();
		direction.normalize().multiply(speed);
		Vector up = new Vector(0, 1, 0);
		Vector perp = new Vector();
		perp.copy(direction);
		perp.crossProduct(up);
		
		for (int i = 0; i < tossCount; i++)
		{
			FallingBlock block = null;
			location = player.getEyeLocation();
			location.setX(location.getX() + perp.getX() * (Math.random() * tossCount / 4 - tossCount / 8));
			location.setY(location.getY());
			location.setZ(location.getZ() + perp.getZ() * (Math.random() * tossCount / 4 - tossCount / 8));
			block = player.getWorld().spawnFallingBlock(location, material, data);

			if (block == null)
			{
				sendMessage("One of your blocks fizzled");
				return SpellResult.FAILURE;
			}

			block.setVelocity(direction);	
		}

		castMessage("You toss some blocks");

		return SpellResult.SUCCESS;
	}
	
	@Override
	public boolean usesMaterial() {
		return true;
	}
}
