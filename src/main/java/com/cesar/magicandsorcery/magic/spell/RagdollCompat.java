package com.cesar.magicandsorcery.magic.spell;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Optional bridge to the Ragdoll Physics mod (mod id "ragdollphysics").
 * Spell pushes are handed to its launch queue so strong hits turn players (and the bodies of killed mobs)
 * into ragdolls, and keep pushing bodies that are already ragdolled. Without that mod this does nothing.
 */
public final class RagdollCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "ragdollphysics";
    private static final String REACTIONS_CLASS = "com.aly.ragdollphysics.reaction.Reactions";
    private static final String RAGDOLL_CLASS = "com.aly.ragdollphysics.entity.RagdollEntity";

    // Same thresholds and formula the ragdoll mod uses for a knock-up (blocks/tick -> m/s)
    private static final double MIN_UPWARD = 0.45;
    private static final double MIN_HORIZONTAL = 1.1;
    private static final double LAUNCH_MULTIPLIER = 1.4 * 20.0;

    private static boolean resolved;
    private static Method queue;
    private static Class<?> ragdollClass;

    private RagdollCompat() {
    }

    /**
     * Whether the entity is currently riding a ragdoll body.
     */
    public static boolean isRagdolled(Entity entity) {
        resolve();
        Entity vehicle = entity.getVehicle();
        return ragdollClass != null && vehicle != null && ragdollClass.isInstance(vehicle);
    }

    /**
     * Whether the entity is a ragdoll body itself (it is pushed through its rider instead).
     */
    public static boolean isRagdollBody(Entity entity) {
        resolve();
        return ragdollClass != null && ragdollClass.isInstance(entity);
    }

    /**
     * Hands a spell push to the ragdoll mod.
     *
     * @param velocity push in blocks/tick (the same units as {@link Entity#setDeltaMovement})
     * @param force    launch even if the push is below the knock-up thresholds (e.g. a body that is already a ragdoll)
     */
    public static void launch(LivingEntity entity, Vec3 velocity, boolean force) {
        resolve();
        if (queue == null || entity.level().isClientSide()) return;

        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (!force && velocity.y < MIN_UPWARD && horizontal < MIN_HORIZONTAL) return;

        try {
            queue.invoke(null, entity,
                    velocity.x * LAUNCH_MULTIPLIER, velocity.y * LAUNCH_MULTIPLIER, velocity.z * LAUNCH_MULTIPLIER, true);
        } catch (Throwable t) {
            LOGGER.warn("Magic and Sorcery: Ragdoll Physics bridge disabled ({})", t.toString());
            queue = null;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            ClassLoader loader = RagdollCompat.class.getClassLoader();
            Class<?> reactions = Class.forName(REACTIONS_CLASS, true, loader);
            Method method = reactions.getDeclaredMethod("queue",
                    LivingEntity.class, double.class, double.class, double.class, boolean.class);
            method.setAccessible(true);
            queue = method;
            ragdollClass = Class.forName(RAGDOLL_CLASS, false, loader);
            LOGGER.info("Magic and Sorcery: Ragdoll Physics detected, spell pushes will ragdoll");
        } catch (Throwable t) {
            LOGGER.warn("Magic and Sorcery: Ragdoll Physics found but its launch API is unavailable ({})", t.toString());
            queue = null;
            ragdollClass = null;
        }
    }
}
