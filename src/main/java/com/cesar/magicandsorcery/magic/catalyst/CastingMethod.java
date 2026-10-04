package com.cesar.magicandsorcery.magic.catalyst;

import net.minecraft.network.chat.Component;

public enum CastingMethod {
    BARE_HANDS("bare_hands", 0.75f, -0.10f, 1.25f, 1.0f, "catalyst.magic_and_sorcery.bare_hands"),
    RING("ring", 1.10f, 0.50f, 1.0f, 0.90f, "catalyst.magic_and_sorcery.ring"),
    BOOK("book", 1.30f, 0.0f, 1.0f, 0.95f, "catalyst.magic_and_sorcery.book"),
    WAND("wand", 1.60f, -0.30f, 1.0f, 1.0f, "catalyst.magic_and_sorcery.wand");

    private final String id;
    private final float damageMultiplier;
    private final float castSpeedModifier;
    private final float manaCostMultiplier;
    private final float cooldownMultiplier;
    private final String translationKey;

    CastingMethod(String id, float damageMultiplier, float castSpeedModifier, float manaCostMultiplier, float cooldownMultiplier, String translationKey) {
        this.id = id;
        this.damageMultiplier = damageMultiplier;
        this.castSpeedModifier = castSpeedModifier;
        this.manaCostMultiplier = manaCostMultiplier;
        this.cooldownMultiplier = cooldownMultiplier;
        this.translationKey = translationKey;
    }

    public String getId() {
        return id;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    /**
     * Positive means faster casting, negative means slower casting.
     * e.g. +0.50 (+50% speed) -> cast ticks / 1.5
     * e.g. -0.10 (-10% speed) -> cast ticks / 0.9
     * e.g. -0.30 (-30% speed) -> cast ticks / 0.7
     */
    public float getCastSpeedModifier() {
        return castSpeedModifier;
    }

    public float getManaCostMultiplier() {
        return manaCostMultiplier;
    }

    public float getCooldownMultiplier() {
        return cooldownMultiplier;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }
}
