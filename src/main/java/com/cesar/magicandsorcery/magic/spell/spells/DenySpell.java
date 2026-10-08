package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketDenyReflect;
import com.cesar.magicandsorcery.network.packets.PacketDenySpawn;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DenySpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "deny");

    public static final int DURATION_TICKS = 40; // 2.0 seconds parry/defense window (+0.25s)
    private static final AtomicInteger BARRIER_ID_GEN = new AtomicInteger(1);
    private static final Map<UUID, ActiveBarrier> ACTIVE_BARRIERS = new ConcurrentHashMap<>();

    public DenySpell() {
        super(ID, SpellSchool.ARCANE, SpellType.UTILITY,
                30.0f, // 30 Mana cost
                0,     // 0 seconds cast time (instant reaction!)
                160,   // 8.0 seconds cooldown (160 ticks)
                0.0f   // 0 Damage
        );
    }

    @Override
    public double getRange() {
        return 3.0;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Vec3 lookVec = player.getViewVector(1.0f).normalize();
        Vec3 eyePos = player.getEyePosition();
        Vec3 center = eyePos.add(lookVec.scale(1.15)).add(0, -0.2, 0);
        int barrierId = BARRIER_ID_GEN.getAndIncrement();
        ActiveBarrier barrier = new ActiveBarrier(barrierId, player.getId(), player.getUUID(),
                serverLevel.dimension(), center, lookVec, DURATION_TICKS);

        ACTIVE_BARRIERS.put(player.getUUID(), barrier);

        // Broadcast barrier spawn to nearby players
        ModNetwork.sendToNearby(
                new PacketDenySpawn(player.getId(), center, lookVec, DURATION_TICKS),
                serverLevel,
                center,
                64.0
        );

        // Sound effect: custom Deny shield sound replacing default sound combo
        serverLevel.playSound(null, center.x, center.y, center.z,
                com.cesar.magicandsorcery.sound.ModSounds.SHIELD.get(), SoundSource.PLAYERS, 1.2f, 1.0f);

        // Hand swipe particle arc in front of the caster
        Vec3 forward = lookVec;
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(forward).normalize();

        for (int i = -6; i <= 6; i++) {
            double angle = (i / 6.0) * (Math.PI / 3.0); // 60-degree arc
            double u = Math.sin(angle) * 1.1;
            double f = Math.cos(angle) * 1.1;
            Vec3 pos = eyePos.add(right.scale(u)).add(forward.scale(f)).add(0, -0.15, 0);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y, pos.z,
                    1, 0.01, 0.01, 0.01, 0.02);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z,
                    1, 0.01, 0.01, 0.01, 0.01);
        }

        return true;
    }

    public static class ActiveBarrier {
        public final int id;
        public final int playerId;
        public final UUID playerUUID;
        public final ResourceKey<Level> dimension;
        public final Vec3 center; // Stationary position in the world
        public final Vec3 direction; // Normalized forward direction locked at cast
        public final int totalTicks;
        public int remainingTicks;
        public int ticksActive;

        public ActiveBarrier(int id, int playerId, UUID playerUUID, ResourceKey<Level> dimension, Vec3 center, Vec3 direction, int duration) {
            this.id = id;
            this.playerId = playerId;
            this.playerUUID = playerUUID;
            this.dimension = dimension;
            this.center = center;
            this.direction = direction;
            this.totalTicks = duration;
            this.remainingTicks = duration;
            this.ticksActive = 0;
        }

        /**
         * Checks if an incoming threat position is within the protected frontal cone of the stationary barrier.
         */
        public boolean isThreatInProtectedArc(Vec3 threatPos) {
            Vec3 toThreat = threatPos.subtract(center);
            if (toThreat.lengthSqr() < 1e-4) return true;
            return toThreat.normalize().dot(direction) >= 0.25;
        }

        /**
         * Checks if a target position is within the protected volume behind the barrier.
         */
        public boolean isProtectingPosition(Vec3 targetPos) {
            Vec3 fromBarrier = targetPos.subtract(center);
            if (fromBarrier.lengthSqr() > 3.2 * 3.2) return false;
            return fromBarrier.dot(direction) <= 0.45;
        }

        public boolean isThreatInProtectedArc(Vec3 threatPos, Vec3 playerPos) {
            return isThreatInProtectedArc(threatPos) && isProtectingPosition(playerPos);
        }
    }

    public static boolean hasActiveBarrier(Player player) {
        if (player == null) return false;
        ActiveBarrier barrier = ACTIVE_BARRIERS.get(player.getUUID());
        return barrier != null && barrier.remainingTicks > 0;
    }

    public static ActiveBarrier getBarrier(Player player) {
        return player != null ? ACTIVE_BARRIERS.get(player.getUUID()) : null;
    }

    /**
     * Attempts to block incoming damage if the attack originates from the protected frontal cone.
     * Returns true if blocked.
     */
    public static boolean tryBlockDamage(ServerPlayer player, DamageSource source, float amount) {
        ActiveBarrier barrier = ACTIVE_BARRIERS.get(player.getUUID());
        if (barrier == null || barrier.remainingTicks <= 0) {
            return false;
        }

        // Determine origin of the threat
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        Vec3 threatPos = null;
        if (attacker != null) {
            threatPos = attacker.getEyePosition();
        } else if (source.getSourcePosition() != null) {
            threatPos = source.getSourcePosition();
        } else {
            // Unpositioned damage (e.g. status effects like wither/poison, starvation, void, drowning)
            // Cannot be parried by a directional shield
            return false;
        }

        if (barrier.isThreatInProtectedArc(threatPos, player.position())) {
            // Frontal attack successfully blocked by stationary barrier!
            ServerLevel level = player.serverLevel();
            Vec3 blockPos = barrier.center;

            // Visual ripple & audio
            ModNetwork.sendToNearby(new PacketDenyReflect(player.getId(), blockPos), level, blockPos, 64.0);
            level.playSound(null, blockPos.x, blockPos.y, blockPos.z,
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4f, 1.15f);
            level.playSound(null, blockPos.x, blockPos.y, blockPos.z,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.7f);

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    blockPos.x, blockPos.y, blockPos.z,
                    10, 0.15, 0.15, 0.15, 0.08);

            return true;
        }

        // Attack from behind or flanks: NOT blocked!
        return false;
    }

    /**
     * Intercepts and reflects a projectile back towards its shooter or opposite direction.
     */
    public static void reflectProjectile(ServerLevel level, ServerPlayer player, ActiveBarrier barrier, Projectile proj) {
        Vec3 projPos = proj.position();
        Entity shooter = proj.getOwner();

        proj.addTag("deny_reflected_" + barrier.id);

        double currentSpeed = Math.max(1.3, proj.getDeltaMovement().length() * 1.15);
        Vec3 returnDir;
        if (shooter != null && shooter.isAlive()) {
            returnDir = shooter.getEyePosition().subtract(projPos).normalize();
        } else {
            // Reflect across barrier normal
            Vec3 v = proj.getDeltaMovement();
            Vec3 n = barrier.direction;
            returnDir = v.subtract(n.scale(2.0 * v.dot(n))).normalize();
        }

        proj.setDeltaMovement(returnDir.scale(currentSpeed));
        proj.hasImpulse = true;
        proj.setOwner(player); // Player now owns the reflected projectile!

        if (proj instanceof AbstractArrow arrow) {
            arrow.setCritArrow(true);
            arrow.setBaseDamage(arrow.getBaseDamage() * 1.25);
        }

        // Audio & visual deflection
        ModNetwork.sendToNearby(new PacketDenyReflect(player.getId(), projPos), level, projPos, 64.0);
        level.playSound(null, projPos.x, projPos.y, projPos.z,
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.3f, 1.2f);
        level.playSound(null, projPos.x, projPos.y, projPos.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6f, 1.8f);
        level.playSound(null, projPos.x, projPos.y, projPos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.4f);

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                projPos.x, projPos.y, projPos.z,
                14, 0.12, 0.12, 0.12, 0.08);
        level.sendParticles(ParticleTypes.ENCHANT,
                projPos.x, projPos.y, projPos.z,
                8, 0.15, 0.15, 0.15, 0.05);
    }

    /**
     * Ticks active barriers, handles projectile interception, and removes expired ones.
     */
    public static void tickServer(MinecraftServer server) {
        if (ACTIVE_BARRIERS.isEmpty() || server == null) return;

        Iterator<Map.Entry<UUID, ActiveBarrier>> it = ACTIVE_BARRIERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBarrier> entry = it.next();
            ActiveBarrier barrier = entry.getValue();
            barrier.remainingTicks--;
            barrier.ticksActive++;

            ServerPlayer player = server.getPlayerList().getPlayer(barrier.playerUUID);
            if (player == null || !player.isAlive() || barrier.remainingTicks <= 0) {
                it.remove();
                continue;
            }

            ServerLevel level = player.serverLevel();

            // Scan for incoming projectiles around the stationary barrier
            AABB scanBox = new AABB(barrier.center, barrier.center).inflate(2.4);
            for (Projectile proj : level.getEntitiesOfClass(Projectile.class, scanBox,
                    p -> p.isAlive() && p.getOwner() != player && !p.getTags().contains("deny_reflected_" + barrier.id))) {

                // Projectile is in front of the stationary barrier
                if (barrier.isThreatInProtectedArc(proj.position())) {
                    // Check if projectile is moving towards the barrier
                    double velDot = proj.getDeltaMovement().dot(barrier.direction);
                    if (velDot < 0.05) { // moving opposite to barrier direction (towards barrier)
                        reflectProjectile(level, player, barrier, proj);
                    }
                }
            }
        }
    }
}
