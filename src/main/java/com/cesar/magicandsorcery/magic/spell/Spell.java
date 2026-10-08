package com.cesar.magicandsorcery.magic.spell;

import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public abstract class Spell {
    private final ResourceLocation id;
    private final SpellSchool school;
    private final SpellType type;
    private final float baseManaCost;
    private final int baseCastTimeTicks;
    private final int baseCooldownTicks;
    private final float baseDamage;

    public Spell(ResourceLocation id, SpellSchool school, SpellType type, float baseManaCost, int baseCastTimeTicks, int baseCooldownTicks, float baseDamage) {
        this.id = id;
        this.school = school;
        this.type = type;
        this.baseManaCost = baseManaCost;
        this.baseCastTimeTicks = baseCastTimeTicks;
        this.baseCooldownTicks = baseCooldownTicks;
        this.baseDamage = baseDamage;
    }

    public ResourceLocation getId() {
        return id;
    }

    public SpellSchool getSchool() {
        return school;
    }

    public SpellType getType() {
        return type;
    }

    public float getBaseManaCost() {
        return baseManaCost;
    }

    public int getBaseCastTimeTicks() {
        return baseCastTimeTicks;
    }

    public int getBaseCooldownTicks() {
        return baseCooldownTicks;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public double getRange() {
        return 16.0;
    }

    public double getRange(CastingMethod method) {
        return getRange();
    }

    /**
     * Damage multiplier is only applied if the spell actually deals damage (> 0).
     */
    public float calculateFinalDamage(CastingMethod method) {
        if (baseDamage <= 0.0f) {
            return 0.0f;
        }
        return baseDamage * method.getDamageMultiplier();
    }

    public float calculateFinalManaCost(CastingMethod method) {
        return baseManaCost * method.getManaCostMultiplier();
    }

    public int calculateFinalCooldown(CastingMethod method) {
        return Math.max(1, Math.round(baseCooldownTicks * method.getCooldownMultiplier()));
    }

    public int calculateFinalCastTime(CastingMethod method) {
        if (baseCastTimeTicks <= 0) {
            return 0;
        }
        float speedFactor = 1.0f + method.getCastSpeedModifier();
        if (speedFactor <= 0.1f) speedFactor = 0.1f;
        return Math.max(1, Math.round(baseCastTimeTicks / speedFactor));
    }

    public Component getName() {
        return Component.translatable("spell." + id.getNamespace() + "." + id.getPath());
    }

    public Component getDescription() {
        return Component.translatable("spell." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    /**
     * Whether this spell supports the in-channeling self-cast toggle mechanic
     * (switching targeting to the caster using the alternate cast input).
     */
    public boolean allowsSelfCast() {
        return false;
    }

    /**
     * Base healing amount if this is a healing/support spell (0 if none).
     */
    public float getBaseHealing() {
        return 0.0f;
    }

    public float calculateFinalHealing(CastingMethod method) {
        if (getBaseHealing() <= 0.0f) {
            return 0.0f;
        }
        return getBaseHealing() * method.getDamageMultiplier();
    }

    /**
     * Executes the spell effect on the server side.
     * @return true if the spell successfully took effect, false otherwise.
     */
    public abstract boolean execute(ServerPlayer player, Level level, CastingMethod method);

    public boolean execute(ServerPlayer player, Level level, CastingMethod method, boolean selfCast) {
        return execute(player, level, method);
    }
}
