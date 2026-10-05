package com.cesar.magicandsorcery.magic.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared physical effects of spells: pushes, shockwaves and ragdoll launches.
 */
public final class SpellImpacts {

    private SpellImpacts() {
    }

    /**
     * Pushes an entity. Strong pushes ragdoll players (and killed mobs) when Ragdoll Physics is installed,
     * and bodies that are already ragdolls get pushed directly.
     *
     * @param velocity push in blocks/tick
     */
    public static void push(Entity entity, Vec3 velocity) {
        if (entity instanceof LivingEntity living) {
            if (RagdollCompat.isRagdolled(living)) {
                RagdollCompat.launch(living, velocity, true);
                return;
            }
            double resistance = Math.min(1.0, living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            velocity = velocity.scale(1.0 - resistance);
            if (velocity.lengthSqr() < 1.0E-6) return;
        }
        if (entity.isPassenger()) return;

        entity.setDeltaMovement(entity.getDeltaMovement().add(velocity));
        entity.hasImpulse = true;
        entity.hurtMarked = true;

        if (entity instanceof LivingEntity living) {
            RagdollCompat.launch(living, velocity, false);
        }
    }

    /**
     * Shockwave: every entity inside the cylinder is thrown outward and upward from the impact.
     * Strength fades towards the edge.
     *
     * @param center   impact point (ground level)
     * @param radius   horizontal radius of the shockwave
     * @param strength horizontal push at the very center (blocks/tick)
     * @param lift     vertical push at the very center (blocks/tick)
     */
    public static void shockwave(ServerLevel level, Vec3 center, double radius, double strength, double lift) {
        shockwave(level, center, radius, strength, lift, null);
    }

    /**
     * Shockwave that spares one entity (usually the caster). A negative strength pulls inward.
     */
    public static void shockwave(ServerLevel level, Vec3 center, double radius, double strength, double lift,
                                 @org.jetbrains.annotations.Nullable Entity exclude) {
        AABB area = new AABB(
                center.x - radius, center.y - 4.0, center.z - radius,
                center.x + radius, center.y + 8.0, center.z + radius
        );

        for (Entity entity : level.getEntities(exclude, area, SpellImpacts::canBePushed)) {
            double dx = entity.getX() - center.x;
            double dz = entity.getZ() - center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;

            double falloff = 1.0 - 0.6 * (dist / radius);
            double dirX;
            double dirZ;
            if (dist < 0.1) {
                // Standing dead center: launch in a random direction
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                dirX = Math.cos(angle);
                dirZ = Math.sin(angle);
            } else {
                dirX = dx / dist;
                dirZ = dz / dist;
            }

            push(entity, new Vec3(dirX * strength * falloff, lift * falloff, dirZ * strength * falloff));
        }
    }

    public static boolean canBePushed(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity instanceof HangingEntity) return false;
        if (RagdollCompat.isRagdollBody(entity)) return false;
        if (entity instanceof Player player && player.getAbilities().flying) return false;
        // Riders are moved through their vehicle, except bodies riding a ragdoll
        return !entity.isPassenger() || RagdollCompat.isRagdolled(entity);
    }
}
