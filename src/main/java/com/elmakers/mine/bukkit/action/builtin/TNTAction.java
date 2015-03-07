package com.elmakers.mine.bukkit.action.builtin;

import com.elmakers.mine.bukkit.action.DelayedCompoundAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.action.ActionHandler;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import com.elmakers.mine.bukkit.action.BaseSpellAction;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

public class TNTAction extends DelayedCompoundAction
{
    private int size;
    private int count;
    private int fuse;
    private boolean useFire;
    private boolean breakBlocks;

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        this.parameters = parameters;
        size = parameters.getInt("size", 6);
        count = parameters.getInt("count", 1);
        fuse = parameters.getInt("fuse", 80);
        useFire = parameters.getBoolean("fire", false);
        breakBlocks = parameters.getBoolean("break_blocks", true);
    }

	@Override
	public SpellResult perform(CastContext context) {
		Mage mage = context.getMage();
		MageController controller = context.getController();
        int size = (int)(mage.getRadiusMultiplier() * this.size);

		Location loc = context.getEyeLocation();
		if (loc == null) {
			return SpellResult.LOCATION_REQUIRED;
		}
		if (!context.hasBuildPermission(loc.getBlock())) {
			return SpellResult.INSUFFICIENT_PERMISSION;
		}

		final Random rand = new Random();
		for (int i = 0; i < count; i++)
		{
			Location targetLoc = loc.clone();
			if (count > 1)
			{
				targetLoc.setX(targetLoc.getX() + rand.nextInt(2 * count) - count);
				targetLoc.setZ(targetLoc.getZ() + rand.nextInt(2 * count) - count);
			}
			TNTPrimed grenade = (TNTPrimed)context.getWorld().spawnEntity(targetLoc, EntityType.PRIMED_TNT);
			if (grenade == null) {
				return SpellResult.FAIL;
			}
			Vector aim = context.getDirection();
			grenade.setVelocity(aim);
			grenade.setYield(size);
			grenade.setFuseTicks(fuse);
			grenade.setIsIncendiary(useFire);
            context.registerForUndo(grenade);
			if (!breakBlocks)
			{
				grenade.setMetadata("cancel_explosion", new FixedMetadataValue(controller.getPlugin(), true));
			}
			ActionHandler.setActions(grenade, actions, context, parameters, "indirect_player_message");
			ActionHandler.setEffects(grenade, context, "explode");
		}
		
		return SpellResult.CAST;
	}

    @Override
    public void getParameterNames(Collection<String> parameters) {
		super.getParameterNames(parameters);
		parameters.add("size");
		parameters.add("count");
		parameters.add("fuse");
		parameters.add("fire");
		parameters.add("break_blocks");
	}

	@Override
	public void getParameterOptions(Collection<String> examples, String parameterKey) {
		if (parameterKey.equals("fire") || parameterKey.equals("break_blocks")) {
			examples.addAll(Arrays.asList((BaseSpell.EXAMPLE_BOOLEANS)));
		} else if (parameterKey.equals("size") || parameterKey.equals("count") || parameterKey.equals("fuse")) {
			examples.addAll(Arrays.asList((BaseSpell.EXAMPLE_SIZES)));
		} else {
			super.getParameterOptions(examples, parameterKey);
		}
	}

    @Override
    public boolean isUndoable()
    {
        return true;
    }

    @Override
    public boolean requiresBuildPermission()
    {
        return true;
    }
}
