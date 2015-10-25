package com.elmakers.mine.bukkit.action.builtin;

import com.elmakers.mine.bukkit.action.CompoundAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.effect.EffectPlay;
import com.elmakers.mine.bukkit.api.effect.EffectPlayer;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.spell.TargetType;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import com.elmakers.mine.bukkit.utility.Target;
import com.elmakers.mine.bukkit.utility.Targeting;
import de.slikey.effectlib.util.DynamicLocation;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class CustomProjectileAction extends CompoundAction
{
    private Targeting targeting;

    private int interval;
    private int lifetime;
    private int range;
    private double speed;
    private int startDistance;
    private String projectileEffectKey;
    private String hitEffectKey;
    private String tickEffectKey;
    private double gravity;
    private double drag;
    private double tickSize;
    private boolean reorient;
    private boolean useWandLocation;
    private boolean useEyeLocation;
    private boolean trackEntity;
    private int targetSelfTimeout;

    private double distanceTravelled;
    private double effectDistanceTravelled;
    private boolean hasTickEffects;
    private long lastUpdate;
    private long nextUpdate;
    private long deadline;
    private long targetSelfDeadline;
    private boolean hit = false;
    private Vector velocity = null;
    private DynamicLocation effectLocation = null;
    private Collection<EffectPlay> activeProjectileEffects;

    @Override
    public void initialize(Spell spell, ConfigurationSection baseParameters) {
        super.initialize(spell, baseParameters);
        targeting = new Targeting();
    }

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        targeting.processParameters(parameters);
        interval = parameters.getInt("interval", 30);
        lifetime = parameters.getInt("lifetime", 5000);
        startDistance = parameters.getInt("start", 0);
        range = parameters.getInt("range", 0);
        projectileEffectKey = parameters.getString("projectile_effects", "projectile");
        hitEffectKey = parameters.getString("hit_effects", "hit");
        tickEffectKey = parameters.getString("tick_effects", "tick");
        gravity = parameters.getDouble("gravity", 0);
        drag = parameters.getDouble("drag", 0);
        tickSize = parameters.getDouble("tick_size", 0.5);
        hasTickEffects = context.getEffects(tickEffectKey).size() > 0;
        reorient = parameters.getBoolean("reorient", false);
        useWandLocation = parameters.getBoolean("use_wand_location", true);
        useEyeLocation = parameters.getBoolean("use_eye_location", true);
        trackEntity = parameters.getBoolean("track_target", false);
        targetSelfTimeout = parameters.getInt("target_self_timeout", 0);

        range *= context.getMage().getRangeMultiplier();

        speed = parameters.getDouble("speed", 0.1);
        speed = parameters.getDouble("velocity", speed * 20);

        // Some parameter tweaks to make sure things are sane
        TargetType targetType = targeting.getTargetType();
        if (targetType == TargetType.NONE) {
            targeting.setTargetType(TargetType.OTHER);
        }
    }

    @Override
    public void reset(CastContext context)
    {
        super.reset(context);
        long now = System.currentTimeMillis();
        nextUpdate = 0;
        distanceTravelled = 0;
        lastUpdate = 0;
        deadline =  now + lifetime;
        targetSelfDeadline = targetSelfTimeout > 0 ? now + targetSelfTimeout : 0;
        hit = false;
        effectLocation = null;
        velocity = null;
        activeProjectileEffects = null;
    }

	@Override
	public SpellResult perform(CastContext context) {
        long now = System.currentTimeMillis();
        if (now < nextUpdate)
        {
            return SpellResult.PENDING;
        }
        if (hit)
        {
            return super.perform(actionContext);
        }
        if (now > deadline)
        {
            return hit();
        }
        if (targetSelfDeadline > 0 && now > targetSelfDeadline)
        {
            targetSelfDeadline = 0;
            context.setTargetsCaster(true);
        }
        nextUpdate = now + interval;

        // Check for initialization required
        Location projectileLocation = null;
        if (velocity == null)
        {
            createActionContext(context);
            Location targetLocation = context.getTargetLocation();
            if (useWandLocation) {
                projectileLocation = context.getWandLocation().clone();
            } else if (useEyeLocation) {
                projectileLocation = context.getEyeLocation().clone();
            } else {
                projectileLocation = context.getLocation().clone();
            }

            if (targetLocation != null && !reorient) {
                velocity = targetLocation.toVector().subtract(projectileLocation.toVector()).normalize();
            } else {
                velocity = context.getDirection().clone().normalize();
            }
            projectileLocation.setDirection(velocity);
            actionContext.setTargetLocation(projectileLocation);
            actionContext.setTargetEntity(null);
            actionContext.setDirection(velocity);

            if (startDistance != 0) {
                projectileLocation.add(velocity.clone().multiply(startDistance));
            }

            // Start up projectile FX
            Collection<EffectPlayer> projectileEffects = context.getEffects(projectileEffectKey);
            for (EffectPlayer apiEffectPlayer : projectileEffects)
            {
                if (effectLocation == null) {
                    effectLocation = new DynamicLocation(projectileLocation);
                    effectLocation.setDirection(velocity);
                }
                if (activeProjectileEffects == null) {
                    activeProjectileEffects = new ArrayList<EffectPlay>();
                }
                // Hrm- this is ugly, but I don't want the API to depend on EffectLib.
                if (apiEffectPlayer instanceof com.elmakers.mine.bukkit.effect.EffectPlayer)
                {
                    com.elmakers.mine.bukkit.effect.EffectPlayer effectPlayer = (com.elmakers.mine.bukkit.effect.EffectPlayer)apiEffectPlayer;
                    effectPlayer.setEffectPlayList(activeProjectileEffects);
                    effectPlayer.startEffects(effectLocation, null);
                }
            }
        }
        else
        {
            projectileLocation = actionContext.getTargetLocation();
            if (effectLocation != null)
            {
                effectLocation.updateFrom(projectileLocation);
                effectLocation.setDirection(velocity);
            }
        }

        // Advance position
        // We default to 50 ms travel time (one tick) for the first iteration.
        long delta = lastUpdate > 0 ? now - lastUpdate : 50;
        lastUpdate = now;

        // Apply gravity and drag
        if (trackEntity)
        {
            Entity targetEntity = context.getTargetEntity();
            if (targetEntity != null && targetEntity.isValid() && context.canTarget(targetEntity))
            {
                Location targetLocation = targetEntity instanceof LivingEntity ?
                        ((LivingEntity)targetEntity).getEyeLocation() : targetEntity.getLocation();
                velocity = targetLocation.toVector().subtract(projectileLocation.toVector()).normalize();
            }
        }
        else if (reorient)
        {
            velocity = context.getDirection().clone().normalize();
        }
        else
        {
            if (gravity > 0) {
                velocity.setY(velocity.getY() - gravity * delta / 50).normalize();
            }
            if (drag > 0) {
                speed -= drag * delta / 50;
                if (speed <= 0) {
                    return hit();
                }
            }
        }

        projectileLocation.setDirection(velocity);
        targeting.start(projectileLocation);

        // Advance targeting to find Entity or Block
        double distance = Math.min(speed * delta / 1000, range - distanceTravelled);
        context.addWork((int)Math.ceil(distance));
        Target target = targeting.target(context, distance);
        Location targetLocation;
        Targeting.TargetingResult targetingResult = targeting.getResult();
        if (targetingResult == Targeting.TargetingResult.MISS) {

            actionContext.setTargetLocation(target.getLocation());
            actionContext.playEffects("blockmiss");

            targetLocation = projectileLocation.clone().add(velocity.clone().multiply(distance));
            context.getMage().sendDebugMessage(ChatColor.DARK_BLUE + "Projectile miss: " + now + " " + ChatColor.DARK_PURPLE
                    + " at " + targetLocation.getBlock().getType() + " : " + targetLocation.toVector() + " from range of " + distance + " over time " + delta, 7);
        } else {
            actionContext.playEffects("prehit");
            targetLocation = target.getLocation();
            context.getMage().sendDebugMessage(ChatColor.BLUE + "Projectile hit: " + now  + " " + ChatColor.LIGHT_PURPLE + targetingResult.name().toLowerCase()
                    + " at " + targetLocation.getBlock().getType() + " from " + projectileLocation.getBlock() + " to " +
                    targetLocation.toVector() + " from range of " + distance + " over time " + delta, 4);
            distance = targetLocation.distance(projectileLocation);
        }
        distanceTravelled += distance;
        effectDistanceTravelled += distance;

        // Max Height check
        int y = targetLocation.getBlockY();
        boolean maxHeight = y >= targetLocation.getWorld().getMaxHeight();
        boolean minHeight = y <= 0;

        if (maxHeight) {
            targetLocation.setY(targetLocation.getWorld().getMaxHeight());
        } else if (minHeight) {
            targetLocation.setY(0);
        }

        if (hasTickEffects && effectDistanceTravelled > tickSize) {
            // Sane limit here
            Vector speedVector = velocity.clone().multiply(tickSize);
            for (int i = 0; i < 256; i++) {
                actionContext.setTargetLocation(projectileLocation);
                actionContext.playEffects(tickEffectKey);

                projectileLocation.add(speedVector);

                effectDistanceTravelled -= tickSize;
                if (effectDistanceTravelled < tickSize) break;
            }
        }

        actionContext.setTargetLocation(targetLocation);
        actionContext.setTargetEntity(target.getEntity());

        actionContext.playEffects("fulltick");

        if (maxHeight || minHeight) {
            return hit();
        }

        if (targetingResult != Targeting.TargetingResult.MISS) {
            return hit();
        }

        if (distanceTravelled >= range) {
            return hit();
        }

        Block block = targetLocation.getBlock();
        if (!block.getChunk().isLoaded()) {
            return hit();
        }

		return SpellResult.PENDING;
	}

    protected SpellResult hit() {
        hit = true;
        if (activeProjectileEffects != null) {
            for (EffectPlay play : activeProjectileEffects) {
                play.cancel();
            }
        }
        if (actionContext == null) {
            return SpellResult.NO_ACTION;
        }
        actionContext.playEffects(hitEffectKey);
        return super.perform(actionContext);
    }

    @Override
    public void getParameterNames(Spell spell, Collection<String> parameters)
    {
        super.getParameterNames(spell, parameters);
        parameters.add("interval");
        parameters.add("lifetime");
        parameters.add("speed");
        parameters.add("start");
        parameters.add("gravity");
        parameters.add("drag");
        parameters.add("target_entities");
        parameters.add("track_target");
    }

    @Override
    public void getParameterOptions(Spell spell, String parameterKey, Collection<String> examples)
    {
        super.getParameterOptions(spell, parameterKey, examples);

        if (parameterKey.equals("speed") || parameterKey.equals("lifetime") ||
            parameterKey.equals("interval") || parameterKey.equals("start") || parameterKey.equals("size") ||
            parameterKey.equals("gravity") || parameterKey.equals("drag") || parameterKey.equals("tick_size")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_SIZES));
        } else if (parameterKey.equals("target_entities") || parameterKey.equals("track_target")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_BOOLEANS));
        }
    }
}
