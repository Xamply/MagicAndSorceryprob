package com.cesar.magicandsorcery.magic.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared physical effects for spells that fall from the sky onto an area.
 */
public final class SpellImpacts {

    private SpellImpacts() {
    }

    /**
     * Shockwave push: every entity inside the cylinder is thrown outward and upward from the impact.
     * Strength fades towards the edge and respects knockback resistance. Riders push their vehicle.
     *
     * @param center   impact point (ground level)
     * @param radius   horizontal radius of the shockwave
     * @param strength horizontal push at the very center
     * @param lift     vertical push at the very center
     */
    public static void shockwave(ServerLevel level, Vec3 center, double radius, double strength, double lift) {
        AABB area = new AABB(
                center.x - radius, center.y - 4.0, center.z - radius,
                center.x + radius, center.y + 8.0, center.z + radius
        );

        for (Entity entity : level.getEntities((Entity) null, area, SpellImpacts::canBePushed)) {
            double dx = entity.getX() - center.x;
            double dz = entity.getZ() - center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;

            double falloff = 1.0 - 0.6 * (dist / radius);
            if (entity instanceof LivingEntity living) {
                falloff *= 1.0 - Math.min(1.0, living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            }
            if (falloff <= 0.0) continue;

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

            Vec3 push = new Vec3(dirX * strength * falloff, lift * falloff, dirZ * strength * falloff);
            entity.setDeltaMovement(entity.getDeltaMovement().add(push));
            entity.hasImpulse = true;
            entity.hurtMarked = true;
        }
    }

    private static boolean canBePushed(Entity entity) {
        return entity.isAlive()
                && !entity.isSpectator()
                && !entity.isPassenger()
                && !(entity instanceof HangingEntity)
                && !(entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().flying);
    }
}
