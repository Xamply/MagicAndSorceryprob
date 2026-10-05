package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.RagdollCompat;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellImpacts;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellTargeting;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketBlizzardTornado;
import com.cesar.magicandsorcery.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blizzard: summons a wandering frost tornado. It forms from the ground up, drags everything around it
 * into its spiral, lifts victims up the funnel and hurls them out of the top, freezing water as it passes.
 * Shape and wander path are deterministic from the seed, so client visuals match the server physics.
 */
public class BlizzardSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "blizzard");

    /** Matches the length of the Blizzard sound (11.47 s). */
    public static final int DURATION_TICKS = 229;
    public static final int FORM_TICKS = 24;
    public static final int FADE_TICKS = 34;
    public static final float MAX_HEIGHT = 7.5f;
    public static final double PULL_RADIUS = 6.0;

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

    // ------------------------------------------------------------------
    // Shared tornado shape (server physics + client visuals)
    // ------------------------------------------------------------------

    /** 0..1 strength: grows while forming, fades while dissipating. */
    public static float intensity(float age) {
        float form = Mth.clamp(age / FORM_TICKS, 0.0f, 1.0f);
        float fade = Mth.clamp((DURATION_TICKS - age) / FADE_TICKS, 0.0f, 1.0f);
        return Math.min(form, fade);
    }

    /** Current funnel height. Rises from the ground while forming. */
    public static float height(float age) {
        float form = Mth.clamp(age / FORM_TICKS, 0.0f, 1.0f);
        float eased = 1.0f - (1.0f - form) * (1.0f - form);
        return MAX_HEIGHT * eased;
    }

    /** Funnel radius at a fraction (0 = ground, 1 = top) of its height. */
    public static double funnelRadius(double heightFraction) {
        double f = Mth.clamp(heightFraction, 0.0, 1.0);
        return 0.5 + 2.4 * Math.pow(f, 1.35);
    }

    /** Wandering center of the tornado. */
    public static Vec3 center(Vec3 origin, long seed, float age) {
        double p1 = ((seed >>> 8) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        double p2 = ((seed >>> 24) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        double reach = 1.8 * Mth.clamp(age / 60.0f, 0.0f, 1.0f);
        double x = Math.cos(age * 0.031 + p1) * reach + Math.sin(age * 0.013 + p2) * reach * 0.5;
        double z = Math.sin(age * 0.027 + p2) * reach + Math.cos(age * 0.017 + p1) * reach * 0.5;
        return origin.add(x, 0, z);
    }

    // ------------------------------------------------------------------
    // Cast
    // ------------------------------------------------------------------

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        double reach = getRange();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));
        ClipContext.Fluid fluid = SpellTargeting.aimFluidMode(level, eyePos);

        BlockHitResult blockHit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, fluid, player));
        Vec3 hitPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : endPos;

        // Entities stop the spell: the tornado forms under them
        AABB searchBox = new AABB(eyePos, hitPos).inflate(2.0);
        double closestDistSq = eyePos.distanceToSqr(hitPos);
        Vec3 center = hitPos;
        for (Entity entity : serverLevel.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable() && e != player)) {
            java.util.Optional<Vec3> clip = entity.getBoundingBox().inflate(0.3).clip(eyePos, hitPos);
            if (clip.isPresent()) {
                double distSq = eyePos.distanceToSqr(clip.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    center = entity.position();
                }
            }
        }

        // Always touch down on the ground (or a water surface)
        Vec3 ground = SpellTargeting.dropToGround(serverLevel, center.add(0, 0.5, 0), player, fluid);
        if (ground != null && center.y - ground.y < 8.0) {
            center = ground;
        }

        long seed = serverLevel.getRandom().nextLong();
        float finalDamage = calculateFinalDamage(method);
        synchronized (ACTIVE_ZONES) {
            ACTIVE_ZONES.add(new ActiveBlizzardZone(serverLevel, player, center, seed, finalDamage));
        }

        ModNetwork.sendToNearby(new PacketBlizzardTornado(center, DURATION_TICKS, seed), serverLevel, center, 96.0);

        serverLevel.playSound(null, center.x, center.y, center.z,
                ModSounds.BLIZZARD.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.85f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.6f);
        return true;
    }

    public static void tickActiveZones() {
        synchronized (ACTIVE_ZONES) {
            Iterator<ActiveBlizzardZone> iterator = ACTIVE_ZONES.iterator();
            while (iterator.hasNext()) {
                ActiveBlizzardZone zone = iterator.next();
                zone.tick();
                if (zone.isExpired()) {
                    zone.release();
                    iterator.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Active tornado physics
    // ------------------------------------------------------------------

    private static class ActiveBlizzardZone {
        private final ServerLevel level;
        private final ServerPlayer caster;
        private final Vec3 origin;
        private final long seed;
        private final float totalDamage;
        private final Map<UUID, Integer> ejectedUntil = new HashMap<>();
        private int age;

        ActiveBlizzardZone(ServerLevel level, ServerPlayer caster, Vec3 origin, long seed, float totalDamage) {
            this.level = level;
            this.caster = caster;
            this.origin = origin;
            this.seed = seed;
            this.totalDamage = totalDamage;
        }

        void tick() {
            age++;
            if (caster.hasDisconnected()) {
                age = DURATION_TICKS;
                return;
            }
            float strength = intensity(age);
            float h = Math.max(1.0f, height(age));
            Vec3 c = center(origin, seed, age);

            AABB area = new AABB(c.x - PULL_RADIUS, c.y - 1.0, c.z - PULL_RADIUS,
                    c.x + PULL_RADIUS, c.y + h + 2.0, c.z + PULL_RADIUS);
            for (Entity entity : level.getEntities((Entity) null, area, e -> e != caster && SpellImpacts.canBePushed(e))) {
                Integer until = ejectedUntil.get(entity.getUUID());
                if (until != null && age < until) continue;
                spin(entity, c, h, strength);
            }

            if (age % 10 == 0) {
                damageTick(c, h);
            }
            if (age % 3 == 0 && strength > 0.3f) {
                freezeWater(c);
            }
        }

        private void spin(Entity entity, Vec3 c, float h, float strength) {
            double dx = entity.getX() - c.x;
            double dz = entity.getZ() - c.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double rel = entity.getY() - c.y;
            if (dist > PULL_RADIUS || rel < -1.0 || rel > h + 2.0) return;
            if (dist < 0.05) {
                dx = 0.05;
                dist = 0.05;
            }

            double frac = rel / h;
            double wall = funnelRadius(frac);
            // Counter-clockwise seen from above, matching the visual spin
            double tx = -dz / dist, tz = dx / dist;
            double ix = -dx / dist, iz = -dz / dist;

            double swirl = 0.17 * strength * (dist <= wall * 1.3 ? 1.0 : wall * 1.3 / dist);
            double pull = 0.06 * strength * (dist > wall ? 1.7 : 0.25);
            boolean inFunnel = dist <= wall * 1.25;
            // Strong enough to beat gravity inside the funnel: victims climb the spiral in about 1.5 s
            double lift = inFunnel ? (0.11 + 0.05 * frac) * strength : 0.015 * strength;

            Vec3 force = new Vec3(tx * swirl + ix * pull, lift, tz * swirl + iz * pull);

            // Near the top the tornado hurls its victims out
            if (inFunnel && frac > 0.82 && strength > 0.2f) {
                eject(entity, c, 1.0);
                return;
            }

            if (entity instanceof LivingEntity living && RagdollCompat.isRagdolled(living)) {
                // Bodies that are already ragdolls keep tumbling around the spiral
                if (age % 4 == 0) {
                    RagdollCompat.launch(living, force.scale(1.6), true);
                }
                return;
            }

            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(v.x * 0.8 + force.x, Math.max(v.y * 0.8, -0.2) + force.y, v.z * 0.8 + force.z);
            entity.hasImpulse = true;
            entity.hurtMarked = true;
            entity.fallDistance = 0.0f;
        }

        private void eject(Entity entity, Vec3 c, double power) {
            double dx = entity.getX() - c.x;
            double dz = entity.getZ() - c.z;
            double dist = Math.max(0.05, Math.sqrt(dx * dx + dz * dz));
            double ox = dx / dist, oz = dz / dist;
            double tx = -oz, tz = ox;
            Vec3 fling = new Vec3((ox * 1.15 + tx * 0.7) * power, 0.55 * power, (oz * 1.15 + tz * 0.7) * power);
            SpellImpacts.push(entity, fling);
            ejectedUntil.put(entity.getUUID(), age + 30);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.8f, 1.3f);
        }

        private void damageTick(Vec3 c, float h) {
            float tickDamage = totalDamage / (DURATION_TICKS / 10.0f);
            DamageSource magicDamage = caster.damageSources().indirectMagic(caster, caster);
            AABB box = new AABB(c.x - 3.0, c.y - 0.5, c.z - 3.0, c.x + 3.0, c.y + h + 0.5, c.z + 3.0);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != caster && e.isAlive() && !e.isSpectator())) {
                double dx = target.getX() - c.x;
                double dz = target.getZ() - c.z;
                double wall = funnelRadius((target.getY() - c.y) / h) + 0.6;
                if (dx * dx + dz * dz > wall * wall) continue;
                target.hurt(magicDamage, tickDamage);
                target.setLastHurtByPlayer(caster);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 100, 300));
            }
        }

        /** Like Frost Walker: still water under the tornado turns into frosted ice that melts on its own. */
        private void freezeWater(Vec3 c) {
            BlockPos base = BlockPos.containing(c.x, c.y - 0.5, c.z);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int r = 3;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    for (int y = -1; y <= 1; y++) {
                        pos.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof LiquidBlock && level.getFluidState(pos).is(Fluids.WATER)
                                && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir()) {
                            level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                            level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(level.random, 60, 120));
                        }
                    }
                }
            }
        }

        /** The tornado collapses: anything still caught inside is thrown out. */
        void release() {
            Vec3 c = center(origin, seed, age);
            AABB area = new AABB(c.x - 3.5, c.y - 1.0, c.z - 3.5, c.x + 3.5, c.y + MAX_HEIGHT + 2.0, c.z + 3.5);
            for (Entity entity : level.getEntities((Entity) null, area, e -> e != caster && SpellImpacts.canBePushed(e))) {
                if (entity.getY() - c.y > 1.0) {
                    eject(entity, c, 0.7);
                }
            }
        }

        boolean isExpired() {
            return age >= DURATION_TICKS;
        }
    }
}
