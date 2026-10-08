package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellImpacts;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class PraesidiumSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "praesidium");

    public static final float BASE_HEAL = 7.0f; // 7 Health points (3.5 hearts)
    public static final double WAVE_RADIUS = 5.5; // Radial knockback wave radius

    public PraesidiumSpell() {
        super(ID, SpellSchool.HOLY, SpellType.UTILITY,
                30.0f, // 30 Mana cost
                40,    // 2.0 seconds cast time (40 ticks base)
                100,   // 5.0 seconds cooldown (100 ticks base)
                0.0f   // 0 Damage
        );
    }

    @Override
    public double getRange() {
        return 16.0;
    }

    @Override
    public boolean allowsSelfCast() {
        return true;
    }

    @Override
    public float getBaseHealing() {
        return BASE_HEAL;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        return execute(player, level, method, null, false);
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method, boolean selfCast) {
        return execute(player, level, method, null, selfCast);
    }

    public boolean execute(ServerPlayer player, Level level, CastingMethod method, Vec3 clientTargetPos) {
        return execute(player, level, method, clientTargetPos, false);
    }

    public boolean execute(ServerPlayer player, Level level, CastingMethod method, Vec3 clientTargetPos, boolean selfCast) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        // 1. Determine target (ally or self)
        LivingEntity target;
        if (selfCast) {
            target = player;
        } else {
            target = null;
            if (clientTargetPos != null) {
                target = findEntityNear(serverLevel, clientTargetPos, 2.5, player);
            }
            if (target == null) {
                target = findTarget(player, serverLevel, getRange());
            }
            if (target == null) {
                target = player;
            }
        }

        // 2. Healing & Absorption calculation with catalyst multiplier
        float multiplier = method.getDamageMultiplier();
        float healAmount = BASE_HEAL * multiplier;

        applyHealingAndAbsorption(target, healAmount);

        // 3. Radial Knockback Wave (repels nearby enemies, does not push target or allies)
        applyRepulsionWave(serverLevel, player, target, WAVE_RADIUS);

        // 4. Audio & Visuals
        playEffects(serverLevel, target);

        return true;
    }

    public static void applyHealingAndAbsorption(LivingEntity target, float healAmount) {
        float currentHealth = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float missing = Math.max(0.0f, maxHealth - currentHealth);

        if (missing > 0.0f) {
            float healToApply = Math.min(healAmount, missing);
            target.heal(healToApply);
            float overflow = healAmount - healToApply;
            if (overflow > 0.0f) {
                // If there was leftover healing, add to absorption up to the single-cast cap
                float currentAbs = target.getAbsorptionAmount();
                if (currentAbs < healAmount) {
                    target.setAbsorptionAmount(Math.min(healAmount, currentAbs + overflow));
                }
            }
        } else {
            // Target is already full health! Convert to temporary golden hearts
            float currentAbs = target.getAbsorptionAmount();
            // Single-cast limit: does not stack infinitely beyond healAmount
            if (currentAbs < healAmount) {
                target.setAbsorptionAmount(healAmount);
            }
        }
    }

    public static void applyRepulsionWave(ServerLevel level, ServerPlayer caster, LivingEntity target, double radius) {
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        AABB scanBox = target.getBoundingBox().inflate(radius, 3.0, radius);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, scanBox)) {
            if (entity == target || entity == caster || !entity.isAlive()) continue;
            if (isAlly(caster, target, entity)) continue;

            Vec3 entityPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            Vec3 diff = entityPos.subtract(center);
            double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
            if (horizontalDist > radius) continue;

            Vec3 pushDir;
            if (horizontalDist < 0.1) {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                pushDir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            } else {
                pushDir = new Vec3(diff.x / horizontalDist, 0, diff.z / horizontalDist);
            }

            double falloff = 1.0 - 0.45 * (horizontalDist / radius);
            double strength = 1.45 * falloff;
            double lift = 0.45 * falloff;

            SpellImpacts.push(entity, new Vec3(pushDir.x * strength, lift, pushDir.z * strength));
        }
    }

    public static boolean isAlly(Player caster, LivingEntity target, LivingEntity other) {
        if (other == caster || other == target) return true;
        if (other.isAlliedTo(caster) || other.isAlliedTo(target)) return true;
        if (other instanceof TamableAnimal pet) {
            if (pet.isOwnedBy(caster) || (target instanceof Player targetPlayer && pet.isOwnedBy(targetPlayer))) {
                return true;
            }
        }
        if (other instanceof net.minecraft.world.entity.npc.Villager || other instanceof net.minecraft.world.entity.animal.allay.Allay) {
            return true;
        }
        return false;
    }

    public static boolean isPotentialTarget(Player caster, LivingEntity e) {
        if (e == caster) return true;
        if (e instanceof Enemy || e instanceof net.minecraft.world.entity.monster.Monster) return false;
        if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == caster) return false;
        if (e instanceof Player otherPlayer && caster.getTeam() != null && !caster.isAlliedTo(otherPlayer)) return false;
        return true;
    }

    public static void playEffects(ServerLevel level, LivingEntity target) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double h = target.getBbHeight();

        // Audio: custom cure audio from Audios/cure.mp3
        level.playSound(null, x, y, z, com.cesar.magicandsorcery.sound.ModSounds.CURE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        // Visual FX: emerald swirling gust, light pillar, and radiant star
        com.cesar.magicandsorcery.network.ModNetwork.sendToNearby(
                new com.cesar.magicandsorcery.network.packets.PacketPraesidiumVisual(target.getId(), target.position(), (float) h, target.getBbWidth()),
                level, target.position(), 64.0
        );

        // 1. Healing particles on target
        level.sendParticles(ParticleTypes.HEART, x, y + h + 0.35, z, 6, 0.35, 0.25, 0.35, 0.05);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h * 0.5, z, 20, 0.45, 0.6, 0.45, 0.08);
        level.sendParticles(ParticleTypes.GLOW, x, y + h * 0.5, z, 15, 0.35, 0.5, 0.35, 0.05);

        // 2. Expanding circular force wave
        int segments = 36;
        for (int i = 0; i < segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2.0;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            // Ring 1 (inner)
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x + cos * 1.2, y + 0.15, z + sin * 1.2,
                    1, cos * 0.45, 0.04, sin * 0.45, 0.15);

            // Ring 2 (mid sweep)
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    x + cos * 2.5, y + 0.25, z + sin * 2.5,
                    0, cos * 0.35, 0.0, sin * 0.35, 1.0);

            // Ring 3 (outer cloud shock)
            level.sendParticles(ParticleTypes.CLOUD,
                    x + cos * 3.5, y + 0.1, z + sin * 3.5,
                    1, cos * 0.3, 0.02, sin * 0.3, 0.05);
        }

        level.sendParticles(ParticleTypes.FLASH, x, y + h * 0.5, z, 1, 0, 0, 0, 0);
    }

    public static LivingEntity findEntityNear(ServerLevel level, Vec3 pos, double radius, Player caster) {
        AABB box = new AABB(pos, pos).inflate(radius);
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || !isPotentialTarget(caster, e)) continue;
            double d = e.distanceToSqr(pos);
            if (d < closestDist) {
                closestDist = d;
                closest = e;
            }
        }
        return closest;
    }

    public static LivingEntity findTarget(Player caster, Level level, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);

        LivingEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (e == caster || !e.isAlive() || !isPotentialTarget(caster, e)) continue;
            AABB aabb = e.getBoundingBox().inflate(0.5);
            Optional<Vec3> hit = aabb.clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestDistSqr) {
                    bestDistSqr = d;
                    best = e;
                }
            }
        }
        return best != null ? best : caster;
    }
}
