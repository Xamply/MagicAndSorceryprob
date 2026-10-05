package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketRedshaRemove;
import com.cesar.magicandsorcery.network.packets.PacketRedshaSpawn;
import com.cesar.magicandsorcery.network.packets.PacketRedshaTrigger;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class RedshaSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "redsha");

    public static final float PORTAL_SIZE = 2.0f; // 2x2 blocks (width 2.0, height 2.0)
    public static final float PORTAL_RADIUS = PORTAL_SIZE / 2.0f; // 1.0f radius
    public static final int DURATION_TICKS = 300; // 15.0 seconds

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final List<ActivePortal> ACTIVE_PORTALS = Collections.synchronizedList(new ArrayList<>());

    public RedshaSpell() {
        super(ID, SpellSchool.ARCANE, SpellType.UTILITY,
                50.0f, // 50 Mana Cost
                40,    // 2.0 seconds channel (40 ticks)
                300,   // 15.0 seconds cooldown (300 ticks)
                0.0f   // 0 Damage
        );
    }

    @Override
    public double getRange() {
        return 2.0;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        // 1. Calculate portal placement at exactly 2 blocks in front of the caster
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 center = eyePos.add(lookVec.scale(2.0));

        Vec3 forward = lookVec.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        int portalId = NEXT_ID.getAndIncrement();
        ActivePortal portal = new ActivePortal(portalId, player.getUUID(), serverLevel.dimension(), center, forward, right, up, DURATION_TICKS);
        ACTIVE_PORTALS.add(portal);

        // 2. Broadcast portal spawn to nearby players
        ModNetwork.sendToNearby(
                new PacketRedshaSpawn(portalId, center, forward, right, up, DURATION_TICKS),
                serverLevel,
                center,
                64.0
        );

        // 3. Audio & Particle effects on placement
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.2f);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Initial circular ring spark burst
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double angle = (i * 2.0 * Math.PI) / segments;
            double u = Math.cos(angle) * PORTAL_RADIUS;
            double v = Math.sin(angle) * PORTAL_RADIUS;
            Vec3 pos = center.add(right.scale(u)).add(up.scale(v));
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z,
                    1, 0.01, 0.01, 0.01, 0.02);
            serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    pos.x, pos.y, pos.z,
                    2, 0.02, 0.02, 0.02, 0.01);
        }

        return true;
    }

    public static class ActivePortal {
        public final int id;
        public final UUID casterUUID;
        public final ResourceKey<Level> dimension;
        public final Vec3 center;
        public final Vec3 forward;
        public final Vec3 right;
        public final Vec3 up;
        public final float radius;
        public final int totalTicks;
        public int remainingTicks;
        public int ticksActive;

        public ActivePortal(int id, UUID casterUUID, ResourceKey<Level> dimension, Vec3 center, Vec3 forward, Vec3 right, Vec3 up, int duration) {
            this.id = id;
            this.casterUUID = casterUUID;
            this.dimension = dimension;
            this.center = center;
            this.forward = forward;
            this.right = right;
            this.up = up;
            this.radius = PORTAL_RADIUS;
            this.totalTicks = duration;
            this.remainingTicks = duration;
            this.ticksActive = 0;
        }

        public boolean intersectsRay(Vec3 start, Vec3 end, Vec3[] outHit) {
            Vec3 seg = end.subtract(start);
            double denom = seg.dot(this.forward);
            if (Math.abs(denom) < 1e-6) {
                return false;
            }

            double t = center.subtract(start).dot(this.forward) / denom;
            if (t < -0.05 || t > 1.05) {
                return false;
            }

            Vec3 hit = start.add(seg.scale(t));
            Vec3 diff = hit.subtract(center);
            double u = diff.dot(right);
            double v = diff.dot(up);

            // Within 2x2 bounds (check both circular radius 1.15 and square 1.1)
            double distSq = u * u + v * v;
            if (distSq <= (1.15 * 1.15) || (Math.abs(u) <= 1.1 && Math.abs(v) <= 1.1)) {
                if (outHit != null && outHit.length > 0) {
                    outHit[0] = hit;
                }
                return true;
            }
            return false;
        }
    }

    public static class IntersectionResult {
        public final ActivePortal portal;
        public final Vec3 hitPos;

        public IntersectionResult(ActivePortal portal, Vec3 hitPos) {
            this.portal = portal;
            this.hitPos = hitPos;
        }
    }

    /**
     * Finds active portals in the given level intersected by a ray segment.
     */
    public static List<IntersectionResult> findIntersections(Level level, Vec3 start, Vec3 end) {
        List<IntersectionResult> results = new ArrayList<>();
        ResourceKey<Level> dim = level.dimension();
        Vec3[] hitHolder = new Vec3[1];

        synchronized (ACTIVE_PORTALS) {
            for (ActivePortal portal : ACTIVE_PORTALS) {
                if (portal.dimension.equals(dim) && portal.remainingTicks > 0) {
                    if (portal.intersectsRay(start, end, hitHolder)) {
                        results.add(new IntersectionResult(portal, hitHolder[0]));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Triggers portal amplification visuals and sounds when a spell passes through it.
     */
    public static void triggerPortalAmplification(ServerLevel level, ActivePortal portal, Vec3 hitPos) {
        ModNetwork.sendToNearby(new PacketRedshaTrigger(portal.id, hitPos), level, portal.center, 64.0);

        // Sharp resonant chime sound
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4f, 1.8f);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4f, 2.0f);

        // Flash and spark burst at the intersection point
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                hitPos.x, hitPos.y, hitPos.z,
                12, 0.12, 0.12, 0.12, 0.08);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE,
                hitPos.x, hitPos.y, hitPos.z,
                8, 0.15, 0.15, 0.15, 0.05);
    }

    /**
     * Rotates a 3D vector around an arbitrary unit axis by theta radians.
     */
    public static Vec3 rotateAroundAxis(Vec3 v, Vec3 axis, double theta) {
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        Vec3 cross = axis.cross(v);
        double dot = axis.dot(v);

        return v.scale(cosTheta)
                .add(cross.scale(sinTheta))
                .add(axis.scale(dot * (1.0 - cosTheta)));
    }

    /**
     * Ticks active portals on the server, removes expired ones, and duplicates passing entity projectiles.
     */
    public static void tickServer(MinecraftServer server) {
        if (ACTIVE_PORTALS.isEmpty() || server == null) return;

        synchronized (ACTIVE_PORTALS) {
            Iterator<ActivePortal> iterator = ACTIVE_PORTALS.iterator();
            while (iterator.hasNext()) {
                ActivePortal portal = iterator.next();
                portal.remainingTicks--;
                portal.ticksActive++;

                ServerLevel level = server.getLevel(portal.dimension);
                if (level == null) {
                    iterator.remove();
                    continue;
                }

                if (portal.remainingTicks <= 0) {
                    ModNetwork.sendToNearby(new PacketRedshaRemove(portal.id), level, portal.center, 64.0);
                    level.playSound(null, portal.center.x, portal.center.y, portal.center.z,
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
                    iterator.remove();
                    continue;
                }

                // Check for physical projectiles entering the 2x2 portal boundary
                if (portal.ticksActive % 2 == 0) {
                    tickPhysicalProjectiles(level, portal);
                }
            }
        }
    }

    private static void tickPhysicalProjectiles(ServerLevel level, ActivePortal portal) {
        AABB box = new AABB(portal.center.x - 1.5, portal.center.y - 1.5, portal.center.z - 1.5,
                portal.center.x + 1.5, portal.center.y + 1.5, portal.center.z + 1.5);

        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, box,
                p -> p.isAlive() && !p.getTags().contains("redsha_duplicated"));

        for (Projectile proj : projectiles) {
            Vec3 pos = proj.position();
            Vec3 vel = proj.getDeltaMovement();
            if (vel.lengthSqr() < 0.001) continue;

            Vec3 prevPos = pos.subtract(vel);
            Vec3[] hitHolder = new Vec3[1];
            if (portal.intersectsRay(prevPos, pos.add(vel), hitHolder) || pos.distanceTo(portal.center) <= PORTAL_RADIUS + 0.2) {
                Vec3 hit = hitHolder[0] != null ? hitHolder[0] : pos;
                proj.addTag("redsha_duplicated");
                triggerPortalAmplification(level, portal, hit);

                // Duplicate arrows / projectiles if supported
                if (proj instanceof AbstractArrow originalArrow) {
                    try {
                        AbstractArrow copy1 = (AbstractArrow) originalArrow.getType().create(level);
                        AbstractArrow copy2 = (AbstractArrow) originalArrow.getType().create(level);
                        if (copy1 != null && copy2 != null) {
                            Vec3 vel1 = rotateAroundAxis(vel, portal.up, Math.toRadians(15.0));
                            Vec3 vel2 = rotateAroundAxis(vel, portal.up, Math.toRadians(-15.0));

                            copy1.setPos(hit.x, hit.y, hit.z);
                            copy1.setDeltaMovement(vel1);
                            copy1.setOwner(originalArrow.getOwner());
                            copy1.setBaseDamage(originalArrow.getBaseDamage());
                            copy1.addTag("redsha_duplicated");

                            copy2.setPos(hit.x, hit.y, hit.z);
                            copy2.setDeltaMovement(vel2);
                            copy2.setOwner(originalArrow.getOwner());
                            copy2.setBaseDamage(originalArrow.getBaseDamage());
                            copy2.addTag("redsha_duplicated");

                            level.addFreshEntity(copy1);
                            level.addFreshEntity(copy2);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
