package com.cesar.magicandsorcery.entity;

import net.minecraft.resources.ResourceLocation;

/**
 * Interface implemented by projectile entities that can be interrupted in flight by Disrupt.
 */
public interface IInterruptibleProjectile {
    /**
     * @return The ResourceLocation ID of the spell that spawned this projectile.
     */
    ResourceLocation getAssociatedSpellId();

    /**
     * @return Whether this specific projectile instance can currently be disrupted.
     */
    default boolean canBeDisrupted() {
        return true;
    }
}
