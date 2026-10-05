package com.cesar.magicandsorcery.magic.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared raycast rules for spell targeting.
 * Water and lava surfaces count as "ground" so spell indicators sit on top of the liquid
 * instead of sinking to the sea floor. When the caster is already submerged, liquids are
 * ignored so underwater aiming keeps working.
 */
public final class SpellTargeting {

    private SpellTargeting() {
    }

    /**
     * Fluid mode for an aiming ray starting at {@code origin}.
     */
    public static ClipContext.Fluid aimFluidMode(Level level, Vec3 origin) {
        return isInsideFluid(level, origin) ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY;
    }

    public static boolean isInsideFluid(Level level, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        FluidState fluid = level.getFluidState(blockPos);
        return !fluid.isEmpty() && pos.y <= blockPos.getY() + fluid.getHeight(level, blockPos);
    }

    /**
     * Raycasts straight down from {@code start}. When fluids count and the start point is
     * already inside a liquid, the result snaps up to that liquid's surface.
     *
     * @return the ground point, or null if nothing was hit.
     */
    @Nullable
    public static Vec3 dropToGround(Level level, Vec3 start, @Nullable Entity entity, ClipContext.Fluid fluid) {
        if (fluid != ClipContext.Fluid.NONE && isInsideFluid(level, start)) {
            return fluidSurfaceAbove(level, start);
        }
        BlockHitResult hit = level.clip(new ClipContext(
                start,
                new Vec3(start.x, level.getMinBuildHeight(), start.z),
                ClipContext.Block.COLLIDER,
                fluid,
                entity
        ));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

    /**
     * Returns the top surface of the liquid column that contains {@code pos}.
     */
    public static Vec3 fluidSurfaceAbove(Level level, Vec3 pos) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(pos).mutable();
        for (int i = 0; i < 64 && !level.getFluidState(cursor.above()).isEmpty(); i++) {
            cursor.move(Direction.UP);
        }
        double top = cursor.getY() + level.getFluidState(cursor).getHeight(level, cursor);
        return new Vec3(pos.x, top, pos.z);
    }
}
