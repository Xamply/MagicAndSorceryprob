package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketBlizzardTornado;
import com.cesar.magicandsorcery.sound.ModSounds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BlizzardSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "blizzard");
    private static final List<ActiveBlizzardZone> ACTIVE_ZONES = new ArrayList<>();

    public BlizzardSpell() {
        super(ID, SpellSchool.ICE, SpellType.AREA,
                35.0f, // Base mana cost
                45,    // Base cast time ticks (2.25s)
                240,   // Base cooldown ticks (12.0s)
                16.0f  // Base damage (Hand: 12, Ring: 17.6, Book: 20.8, Wand: 25.6)
        );
    }

    @Override
    public double getRange() {
        return 24.0;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        double reach = 24.0;
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        // 1. Block collision check
        BlockHitResult blockHit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 hitPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : endPos;

        // 2. Entity collision check: entities act as obstacles stopping the spell
        AABB searchBox = new AABB(eyePos, hitPos).inflate(2.0);
        double closestDistSq = eyePos.distanceToSqr(hitPos);
        Vec3 center = hitPos;

        for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable() && e != player)) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> clip = entityBox.clip(eyePos, hitPos);
            if (clip.isPresent()) {
                double distSq = eyePos.distanceToSqr(clip.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    center = entity.position();
                }
            }
        }

        float finalDamage = calculateFinalDamage(method);
        double radius = 1.5; // 3.0 blocks wide and 3.0 deep
        // Duration matches exact length of Blizzard audio (11.47 seconds = 229 ticks)
        int durationTicks = 229;

        synchronized (ACTIVE_ZONES) {
            ACTIVE_ZONES.add(new ActiveBlizzardZone(serverLevel, player, center, radius, finalDamage, durationTicks));
        }

        // Synchronize 3D animated tornado with nearby clients
        ModNetwork.sendToNearby(new PacketBlizzardTornado(center, durationTicks, serverLevel.getRandom().nextLong()),
                serverLevel, center, 64.0);

        // Play custom Blizzard audio from Audios/Blizzard.mp3
        serverLevel.playSound(null, center.x, center.y, center.z,
                ModSounds.BLIZZARD.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.85f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.POWDER_SNOW_FALL, SoundSource.PLAYERS, 1.3f, 0.7f);

        return true;
    }

    public static void tickActiveZones() {
        synchronized (ACTIVE_ZONES) {
            Iterator<ActiveBlizzardZone> iterator = ACTIVE_ZONES.iterator();
            while (iterator.hasNext()) {
                ActiveBlizzardZone zone = iterator.next();
                zone.tick();
                if (zone.isExpired()) {
                    iterator.remove();
                }
            }
        }
    }

    private static class ActiveBlizzardZone {
        private final ServerLevel level;
        private final ServerPlayer caster;
        private final Vec3 center;
        private final double radius;
        private final float totalDamage;
        private int remainingTicks;
        private final int initialDuration;

        public ActiveBlizzardZone(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, float totalDamage, int durationTicks) {
            this.level = level;
            this.caster = caster;
            this.center = center;
            this.radius = radius;
            this.totalDamage = totalDamage;
            this.remainingTicks = durationTicks;
            this.initialDuration = durationTicks;
        }

        public void tick() {
            remainingTicks--;

            // Tornado Vortex Suction and Lift (4 blocks height, 3 blocks diameter)
            AABB suctionBox = new AABB(
                    center.x - 2.8, center.y - 0.5, center.z - 2.8,
                    center.x + 2.8, center.y + 4.5, center.z + 2.8
            );

            List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, suctionBox,
                    e -> e != caster && e.isAlive() && !e.isSpectator());

            for (LivingEntity target : nearbyEntities) {
                double dx = center.x - target.getX();
                double dz = center.z - target.getZ();
                double distHoriz = Math.sqrt(dx * dx + dz * dz);

                if (distHoriz > 0.05 && distHoriz <= 2.8) {
                    // Pull towards central vortex
                    double pullStrength = 0.065;
                    double pullX = (dx / distHoriz) * pullStrength;
                    double pullZ = (dz / distHoriz) * pullStrength;

                    // Tangential swirl force (makes entities orbit inside the tornado)
                    double swirlStrength = 0.11;
                    double swirlX = (-dz / distHoriz) * swirlStrength;
                    double swirlZ = (dx / distHoriz) * swirlStrength;

                    // Gentle updraft lift inside the tornado funnel
                    double updraft = (distHoriz <= 1.5) ? 0.04 : 0.015;

                    Vec3 currentVel = target.getDeltaMovement();
                    target.setDeltaMovement(currentVel.x * 0.82 + pullX + swirlX,
                            currentVel.y * 0.82 + updraft,
                            currentVel.z * 0.82 + pullZ + swirlZ);
                    target.hurtMarked = true;
                }
            }

            // Damage, slowness, and freezing inside the tornado (every 10 ticks = 0.5s)
            if (remainingTicks % 10 == 0) {
                AABB damageBox = new AABB(
                        center.x - 1.8, center.y - 0.5, center.z - 1.8,
                        center.x + 1.8, center.y + 4.2, center.z + 1.8
                );

                float tickDamage = totalDamage / (initialDuration / 10.0f);
                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, damageBox,
                        e -> e != caster && e.isAlive() && !e.isSpectator());

                for (LivingEntity target : targets) {
                    double dx = center.x - target.getX();
                    double dz = center.z - target.getZ();
                    if ((dx * dx + dz * dz) <= (1.8 * 1.8)) {
                        net.minecraft.world.damagesource.DamageSource magicDamage = caster.damageSources().indirectMagic(caster, caster);
                        target.hurt(magicDamage, tickDamage);
                        target.setLastHurtByPlayer(caster);
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1)); // Slowness II
                        target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 100, 300));
                    }
                }
            }
        }

        public boolean isExpired() {
            return remainingTicks <= 0;
        }
    }
}
