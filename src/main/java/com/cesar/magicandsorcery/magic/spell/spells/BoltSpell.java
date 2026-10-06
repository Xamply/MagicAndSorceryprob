package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellImpacts;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellTargeting;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketBoltVisual;
import com.cesar.magicandsorcery.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bolt: a fractal lightning strike from the catalyst. It throws the target back along the bolt
 * and arcs on to up to two nearby enemies.
 */
public class BoltSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "bolt");

    private static final double REACH = 32.0;
    private static final int MAX_CHAIN = 2;
    private static final double CHAIN_RANGE = 5.0;
    private static final float CHAIN_DAMAGE_FACTOR = 0.4f;

    public BoltSpell() {
        super(ID, SpellSchool.LIGHTNING, SpellType.SINGLE_TARGET,
                20.0f, // Base mana cost
                30,    // Base cast time ticks (1.5s)
                80,    // Base cooldown ticks (4.0s)
                20.0f  // Base damage (Hand: 15, Ring: 22, Book: 26, Wand: 32)
        );
    }

    @Override
    public double getRange() {
        return REACH;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(REACH));

        // 1. Block raycast (water surfaces stop the bolt)
        BlockHitResult blockHit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER,
                SpellTargeting.aimFluidMode(level, eyePos), player));
        Vec3 hitPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : endPos;

        // 2. Entity raycast: closest pickable entity along the ray
        AABB searchBox = new AABB(eyePos, hitPos).inflate(2.0);
        LivingEntity target = null;
        double closestDistSq = eyePos.distanceToSqr(hitPos);
        for (Entity entity : serverLevel.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable() && e != player)) {
            java.util.Optional<Vec3> clip = entity.getBoundingBox().inflate(0.3).clip(eyePos, hitPos);
            if (clip.isPresent()) {
                double distSq = eyePos.distanceToSqr(clip.get());
                if (distSq < closestDistSq && entity instanceof LivingEntity living) {
                    closestDistSq = distSq;
                    target = living;
                    hitPos = clip.get();
                }
            }
        }

        float finalDamage = calculateFinalDamage(method);
        List<Vec3> chainPoints = new ArrayList<>();

        if (target != null) {
            // Check if target has an active Deny barrier protecting from this angle
            if (target instanceof ServerPlayer defendingPlayer && DenySpell.hasActiveBarrier(defendingPlayer)) {
                DenySpell.ActiveBarrier barrier = DenySpell.getBarrier(defendingPlayer);
                if (barrier != null && barrier.isThreatInProtectedArc(player.getEyePosition(), defendingPlayer.position())) {
                    // Bolt is reflected back to the caster!
                    Vec3 reflectPos = hitPos;
                    ModNetwork.sendToNearby(new com.cesar.magicandsorcery.network.packets.PacketDenyReflect(
                            defendingPlayer.getId(), reflectPos), serverLevel, reflectPos, 64.0);
                    serverLevel.playSound(null, reflectPos.x, reflectPos.y, reflectPos.z,
                            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4f, 1.2f);
                    serverLevel.playSound(null, reflectPos.x, reflectPos.y, reflectPos.z,
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6f, 1.8f);

                    strike(defendingPlayer, player, finalDamage, lookVec.scale(-1.0), 1.25, 0.5);
                    ModNetwork.sendToNearby(new PacketBoltVisual(reflectPos, player.getEyePosition(), serverLevel.getRandom().nextLong(),
                            true, Collections.emptyList()), serverLevel, reflectPos.lerp(player.getEyePosition(), 0.5), 80.0);
                    target = null; // Defending player avoids the hit!
                }
            }

            if (target != null) {
                strike(player, target, finalDamage, lookVec, 1.25, 0.5);

                // 3. Chain lightning: arc to the nearest living enemies around the target
                Set<LivingEntity> struck = new HashSet<>();
                struck.add(target);
                LivingEntity from = target;
                for (int i = 0; i < MAX_CHAIN; i++) {
                    LivingEntity next = findChainTarget(serverLevel, player, from, struck);
                    if (next == null) break;
                    struck.add(next);
                    Vec3 arcDir = next.position().subtract(from.position());
                    strike(player, next, finalDamage * CHAIN_DAMAGE_FACTOR, arcDir, 0.55, 0.3);
                    chainPoints.add(next.getBoundingBox().getCenter());
                    from = next;
                }
            }
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            // Ground strike: small blast of static that shoves anything standing next to it
            SpellImpacts.shockwave(serverLevel, hitPos, 2.2, 0.45, 0.3);
        }

        // 4. Bolt starts at the catalyst tip
        Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0));
        rightVec = rightVec.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : rightVec.normalize();
        double armOffset = (player.getMainArm() == HumanoidArm.RIGHT) ? 0.28 : -0.28;
        Vec3 catalystPos = eyePos.add(lookVec.scale(0.35)).add(rightVec.scale(armOffset)).add(0, -0.18, 0);

        // 5. Visuals for everyone nearby
        ModNetwork.sendToNearby(new PacketBoltVisual(catalystPos, hitPos, serverLevel.getRandom().nextLong(),
                target != null, chainPoints), serverLevel, catalystPos.lerp(hitPos, 0.5), 80.0);

        // 6. Sound
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.BOLT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        if (catalystPos.distanceTo(hitPos) > 16.0) {
            serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                    ModSounds.BOLT.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        if (!chainPoints.isEmpty()) {
            serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                    SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.8f);
        }

        // 7. REDSHA INTERACTION: Check if the bolt passes through an active Redsha circle
        List<RedshaSpell.IntersectionResult> intersections = RedshaSpell.findIntersections(serverLevel, catalystPos, hitPos);
        if (intersections.isEmpty()) {
            intersections = RedshaSpell.findIntersections(serverLevel, eyePos, hitPos);
        }

        for (RedshaSpell.IntersectionResult res : intersections) {
            RedshaSpell.triggerPortalAmplification(serverLevel, res.portal, res.hitPos);

            // Create 2 additional copies of the bolt fanning out through the circle (total 3 bolts)
            Vec3 mainDir = lookVec.normalize();
            Vec3 dir1 = RedshaSpell.rotateAroundAxis(mainDir, res.portal.up, Math.toRadians(15.0));
            Vec3 dir2 = RedshaSpell.rotateAroundAxis(mainDir, res.portal.up, Math.toRadians(-15.0));

            fireBoltRay(player, serverLevel, res.hitPos, dir1, REACH, finalDamage);
            fireBoltRay(player, serverLevel, res.hitPos, dir2, REACH, finalDamage);
        }

        return true;
    }

    private static void strike(ServerPlayer caster, LivingEntity target, float damage, Vec3 direction, double push, double lift) {
        DamageSource magicDamage = caster.damageSources().indirectMagic(caster, caster);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        target.hurt(magicDamage, damage);
        target.setLastHurtByPlayer(caster);
        target.setSecondsOnFire(1);

        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        SpellImpacts.push(target, new Vec3(flat.x * push, lift, flat.z * push));
    }

    private static LivingEntity findChainTarget(ServerLevel level, ServerPlayer caster, LivingEntity from, Set<LivingEntity> struck) {
        AABB area = from.getBoundingBox().inflate(CHAIN_RANGE);
        LivingEntity best = null;
        double bestDist = CHAIN_RANGE * CHAIN_RANGE;
        Vec3 origin = from.getBoundingBox().getCenter();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && !e.isSpectator() && e != caster && !struck.contains(e))) {
            Vec3 c = candidate.getBoundingBox().getCenter();
            double d = c.distanceToSqr(origin);
            if (d >= bestDist) continue;
            // Needs a clear path for the arc
            BlockHitResult los = level.clip(new ClipContext(origin, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from));
            if (los.getType() != HitResult.Type.MISS) continue;
            best = candidate;
            bestDist = d;
        }
        return best;
    }

    /**
     * Fires a duplicated bolt ray originating from a Redsha magic circle with identical properties.
     */
    private void fireBoltRay(ServerPlayer player, ServerLevel serverLevel, Vec3 origin, Vec3 direction, double reach, float damage) {
        Vec3 endPos = origin.add(direction.scale(reach));

        // 1. Block raycast
        BlockHitResult blockHit = serverLevel.clip(new ClipContext(origin, endPos, ClipContext.Block.COLLIDER,
                SpellTargeting.aimFluidMode(serverLevel, origin), player));
        Vec3 hitPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : endPos;

        // 2. Entity raycast
        AABB searchBox = new AABB(origin, hitPos).inflate(2.0);
        EntityHitResult entityHit = null;
        double closestDistSq = origin.distanceToSqr(hitPos);

        for (Entity entity : serverLevel.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable() && e != player)) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> clip = entityBox.clip(origin, hitPos);
            if (clip.isPresent()) {
                double distSq = origin.distanceToSqr(clip.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    entityHit = new EntityHitResult(entity, clip.get());
                }
            }
        }

        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            hitPos = entityHit.getLocation();
            if (target instanceof ServerPlayer defendingPlayer && DenySpell.hasActiveBarrier(defendingPlayer)) {
                DenySpell.ActiveBarrier barrier = DenySpell.getBarrier(defendingPlayer);
                if (barrier != null && barrier.isThreatInProtectedArc(origin, defendingPlayer.position())) {
                    // Bolt is reflected back to the caster!
                    ModNetwork.sendToNearby(new com.cesar.magicandsorcery.network.packets.PacketDenyReflect(
                            defendingPlayer.getId(), hitPos), serverLevel, hitPos, 64.0);
                    serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4f, 1.2f);
                    serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6f, 1.8f);

                    strike(defendingPlayer, player, damage, direction.scale(-1.0), 1.25, 0.5);
                    ModNetwork.sendToNearby(new PacketBoltVisual(hitPos, player.getEyePosition(), serverLevel.getRandom().nextLong(),
                            true, Collections.emptyList()), serverLevel, hitPos.lerp(player.getEyePosition(), 0.5), 80.0);
                    target = null;
                }
            }

            if (target != null) {
                strike(player, target, damage, direction, 1.25, 0.5);
            }
        }

        // Spark at origin on magic circle
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                origin.x, origin.y, origin.z,
                4, 0.03, 0.03, 0.03, 0.03);

        // Visual lightning bolt
        ModNetwork.sendToNearby(new PacketBoltVisual(origin, hitPos, serverLevel.getRandom().nextLong(),
                entityHit != null, Collections.emptyList()), serverLevel, origin.lerp(hitPos, 0.5), 80.0);

        // Impact spark discharge
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                hitPos.x, hitPos.y, hitPos.z,
                8, 0.10, 0.10, 0.10, 0.06);

        // Sound effect
        serverLevel.playSound(null, origin.x, origin.y, origin.z,
                ModSounds.BOLT.get(), SoundSource.PLAYERS, 0.9f, 1.05f);
        if (origin.distanceTo(hitPos) > 16.0) {
            serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                    ModSounds.BOLT.get(), SoundSource.PLAYERS, 0.8f, 1.05f);
        }
    }
}
