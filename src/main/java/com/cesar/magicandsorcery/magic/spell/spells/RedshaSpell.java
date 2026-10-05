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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redsha: opens a crimson seal in front of the caster. Every projectile (or Bolt) that crosses it splits into
 * the original plus two temporary echoes fanning out. Echoes cannot be picked up and dissolve as soon as they hit
 * something or after a few seconds, so nothing is ever really duplicated.
 */
public class RedshaSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "redsha");

    public static final float PORTAL_SIZE = 2.0f;
    public static final float PORTAL_RADIUS = PORTAL_SIZE / 2.0f;
    public static final int DURATION_TICKS = 300; // 15.0 seconds
    /** The seal needs a moment to unfold before it can split anything. */
    public static final int OPEN_TICKS = 6;
    public static final double PLACE_DISTANCE = 2.5;

    private static final int ECHO_LIFETIME = 100;
    private static final double ECHO_ANGLE = Math.toRadians(13.0);
    private static final String ECHO_TAG = "redsha_echo";
    private static final DustParticleOptions CRIMSON_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.25f), 1.0f);

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final List<ActivePortal> ACTIVE_PORTALS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Echo> ECHOES = new ArrayList<>();

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
        return PLACE_DISTANCE;
    }

    /**
     * Seal center for a caster looking ahead (shared with the client preview).
     */
    public static Vec3 placement(Level level, Entity caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 wanted = eye.add(look.scale(PLACE_DISTANCE));
        BlockHitResult hit = level.clip(new ClipContext(eye, wanted, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() != HitResult.Type.MISS) {
            // Never embed the seal in a wall
            return eye.add(look.scale(Math.max(0.8, hit.getLocation().distanceTo(eye) - 0.4)));
        }
        return wanted;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Vec3 center = placement(serverLevel, player);
        Vec3 forward = player.getViewVector(1.0f).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();

        int portalId = NEXT_ID.getAndIncrement();
        ACTIVE_PORTALS.add(new ActivePortal(portalId, player.getUUID(), serverLevel.dimension(), center, forward, right, up, DURATION_TICKS));

        ModNetwork.sendToNearby(new PacketRedshaSpawn(portalId, center, forward, right, up, DURATION_TICKS),
                serverLevel, center, 64.0);

        // Crystalline unfolding (no thunder)
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 1.3f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.3f, 0.8f);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------
    // Portal data
    // ------------------------------------------------------------------

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

        public boolean isOpen() {
            return ticksActive >= OPEN_TICKS && remainingTicks > 0;
        }

        /**
         * Whether the segment start→end crosses the seal's disc (with a little tolerance at the rim).
         */
        public boolean intersectsRay(Vec3 start, Vec3 end, Vec3[] outHit) {
            Vec3 seg = end.subtract(start);
            double denom = seg.dot(this.forward);
            if (Math.abs(denom) < 1e-6) {
                return false;
            }
            double t = center.subtract(start).dot(this.forward) / denom;
            if (t < 0.0 || t > 1.0) {
                return false;
            }
            Vec3 hit = start.add(seg.scale(t));
            Vec3 diff = hit.subtract(center);
            double u = diff.dot(right);
            double v = diff.dot(up);
            if (u * u + v * v <= (radius + 0.2) * (radius + 0.2)) {
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

    /** A temporary echo and how many ticks it has lived. */
    private static final class Echo {
        final Entity entity;
        int age;

        Echo(Entity entity) {
            this.entity = entity;
        }
    }

    /**
     * Open portals in the level crossed by a ray segment.
     */
    public static List<IntersectionResult> findIntersections(Level level, Vec3 start, Vec3 end) {
        List<IntersectionResult> results = new ArrayList<>();
        ResourceKey<Level> dim = level.dimension();
        Vec3[] hitHolder = new Vec3[1];
        synchronized (ACTIVE_PORTALS) {
            for (ActivePortal portal : ACTIVE_PORTALS) {
                if (portal.dimension.equals(dim) && portal.isOpen() && portal.intersectsRay(start, end, hitHolder)) {
                    results.add(new IntersectionResult(portal, hitHolder[0]));
                }
            }
        }
        return results;
    }

    public static void triggerPortalAmplification(ServerLevel level, ActivePortal portal, Vec3 hitPos) {
        triggerPortalAmplification(level, portal, hitPos, new int[0]);
    }

    /**
     * Flash, chime and sparks when something crosses the seal.
     */
    public static void triggerPortalAmplification(ServerLevel level, ActivePortal portal, Vec3 hitPos, int[] echoIds) {
        ModNetwork.sendToNearby(new PacketRedshaTrigger(portal.id, hitPos, echoIds), level, portal.center, 64.0);

        // Glassy, resonant split (no thunder)
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6f, 1.6f);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.9f);
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6f, 1.8f);
    }

    /**
     * Rotates a 3D vector around an arbitrary unit axis by theta radians.
     */
    public static Vec3 rotateAroundAxis(Vec3 v, Vec3 axis, double theta) {
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        Vec3 cross = axis.cross(v);
        double dot = axis.dot(v);
        return v.scale(cosTheta).add(cross.scale(sinTheta)).add(axis.scale(dot * (1.0 - cosTheta)));
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    /**
     * Runs at the end of every server tick: entities have already moved this tick,
     * so (xOld → position) is exactly the path each projectile travelled.
     */
    public static void tickServer(MinecraftServer server) {
        if (server == null) return;
        tickEchoes(server);
        if (ACTIVE_PORTALS.isEmpty()) return;

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
                            SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
                    iterator.remove();
                    continue;
                }
                if (portal.isOpen()) {
                    splitProjectiles(level, portal);
                }
            }
        }
    }

    private static void splitProjectiles(ServerLevel level, ActivePortal portal) {
        // Wide enough to catch fast projectiles that crossed the seal during this tick
        AABB box = new AABB(portal.center, portal.center).inflate(PORTAL_RADIUS + 4.5);
        String seenTag = "redsha_seen_" + portal.id;
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive()
                && !p.getTags().contains(ECHO_TAG) && !p.getTags().contains(seenTag)
                && !(p instanceof FishingHook) && !(p instanceof ThrownEnderpearl));

        Vec3[] hitHolder = new Vec3[1];
        for (Projectile proj : projectiles) {
            Vec3 now = proj.position();
            Vec3 before = new Vec3(proj.xOld, proj.yOld, proj.zOld);
            if (before.distanceToSqr(now) < 1.0E-6) continue;
            if (!portal.intersectsRay(before, now, hitHolder)) continue;

            proj.addTag(seenTag);
            Vec3 hit = hitHolder[0];
            Vec3 vel = proj.getDeltaMovement();
            List<Integer> echoIds = new ArrayList<>(2);
            for (int side = -1; side <= 1; side += 2) {
                Entity echo = spawnEcho(level, proj, hit, rotateAroundAxis(vel, portal.up, ECHO_ANGLE * side), seenTag);
                if (echo != null) echoIds.add(echo.getId());
            }
            triggerPortalAmplification(level, portal, hit, echoIds.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    /**
     * Temporary copy of a projectile: same type and data, new direction, never collectable.
     */
    private static Entity spawnEcho(ServerLevel level, Projectile original, Vec3 pos, Vec3 velocity, String seenTag) {
        try {
            CompoundTag data = original.saveWithoutId(new CompoundTag());
            data.remove("UUID");
            Entity copy = original.getType().create(level);
            if (copy == null) return null;
            copy.load(data);
            copy.setPos(pos.x, pos.y, pos.z);
            copy.xOld = pos.x;
            copy.yOld = pos.y;
            copy.zOld = pos.z;
            copy.setDeltaMovement(velocity);
            double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            copy.setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
            copy.setXRot((float) (Math.atan2(velocity.y, horizontal) * (180.0 / Math.PI)));
            copy.yRotO = copy.getYRot();
            copy.xRotO = copy.getXRot();

            if (copy instanceof AbstractArrow arrow) {
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            }
            if (copy instanceof AbstractHurtingProjectile hurting && original instanceof AbstractHurtingProjectile source) {
                double speed = Math.sqrt(source.xPower * source.xPower + source.yPower * source.yPower + source.zPower * source.zPower);
                Vec3 dir = velocity.normalize().scale(speed);
                hurting.xPower = dir.x;
                hurting.yPower = dir.y;
                hurting.zPower = dir.z;
            }
            copy.addTag(ECHO_TAG);
            copy.addTag(seenTag);
            if (!level.addFreshEntity(copy)) return null;
            synchronized (ECHOES) {
                ECHOES.add(new Echo(copy));
            }
            return copy;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Echoes dissolve when they hit something, stop moving, or run out of time.
     */
    private static void tickEchoes(MinecraftServer server) {
        synchronized (ECHOES) {
            if (ECHOES.isEmpty()) return;
            Iterator<Echo> it = ECHOES.iterator();
            while (it.hasNext()) {
                Echo echo = it.next();
                Entity e = echo.entity;
                echo.age++;
                if (e.isRemoved()) {
                    it.remove();
                    continue;
                }
                boolean landed = e instanceof AbstractArrow arrow && (arrow.shakeTime > 0 || (echo.age > 2 && arrow.getDeltaMovement().lengthSqr() < 0.01));
                if (landed || echo.age >= ECHO_LIFETIME) {
                    if (e.level() instanceof ServerLevel level) {
                        level.sendParticles(CRIMSON_DUST, e.getX(), e.getY(), e.getZ(), 10, 0.15, 0.15, 0.15, 0.02);
                        level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY(), e.getZ(), 4, 0.1, 0.1, 0.1, 0.03);
                        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 0.6f, 1.8f);
                    }
                    e.discard();
                    it.remove();
                }
            }
        }
    }
}
