package com.cesar.magicandsorcery.entity;

import com.cesar.magicandsorcery.magic.spell.spells.DenySpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class IteratusMissileEntity extends Projectile {
    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(IteratusMissileEntity.class, EntityDataSerializers.FLOAT);

    private static final int MAX_LIFESPAN = 100; // 5 seconds maximum life
    private static final double BASE_SPEED = 1.15;
    private static final double MAX_TURN_RATE = Math.toRadians(4.8); // ~4.8 degrees per tick (smooth natural curvature, not aimbot)

    private int lifespan = MAX_LIFESPAN;
    private int targetId = -1;

    public IteratusMissileEntity(EntityType<? extends IteratusMissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public IteratusMissileEntity(Level level, LivingEntity owner, float damage) {
        this(ModEntities.ITERATUS_MISSILE.get(), level);
        this.setOwner(owner);
        this.setDamage(damage);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_DAMAGE, 10.0f);
    }

    public float getDamage() {
        return this.entityData.get(DATA_DAMAGE);
    }

    public void setDamage(float damage) {
        this.entityData.set(DATA_DAMAGE, damage);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void tick() {
        super.tick();

        this.lifespan--;
        if (this.lifespan <= 0) {
            dissolve();
            return;
        }

        Vec3 vel = this.getDeltaMovement();
        double speed = vel.length();
        if (speed < 0.1) {
            speed = BASE_SPEED;
            vel = this.getViewVector(1.0f).scale(speed);
            this.setDeltaMovement(vel);
        }

        // Server-side target tracking & trajectory curvature
        if (!this.level().isClientSide()) {
            LivingEntity target = resolveTarget();
            if (target != null && target.isAlive()) {
                Vec3 curDir = vel.normalize();
                Vec3 targetCenter = target.getBoundingBox().getCenter();
                Vec3 toTarget = targetCenter.subtract(this.position());
                Vec3 desiredDir = toTarget.normalize();

                // Restrict turning by maximum angular rate:
                // Flies straight into frontal targets without dodging, and curves smoothly towards enemies to the side
                double dot = Math.max(-1.0, Math.min(1.0, curDir.dot(desiredDir)));
                double angle = Math.acos(dot);
                if (angle <= MAX_TURN_RATE) {
                    curDir = desiredDir;
                } else {
                    curDir = slerp(curDir, desiredDir, MAX_TURN_RATE / angle);
                }

                vel = curDir.scale(speed);
                this.setDeltaMovement(vel);
            }
        }

        // Raycast collision detection
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            return;
        }

        // Update position
        Vec3 newPos = this.position().add(this.getDeltaMovement());
        this.setPos(newPos);
        this.hasImpulse = true;

        // Update rotation based on motion vector
        double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        this.setYRot((float) (Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI)));
        this.setXRot((float) (Mth.atan2(vel.y, horizontalDist) * (180.0 / Math.PI)));

        // Flight particles along the trail
        spawnTrailParticles(vel);
    }

    private void spawnTrailParticles(Vec3 vel) {
        double px = this.getX();
        double py = this.getY();
        double pz = this.getZ();

        if (this.level().isClientSide()) {
            // Trailing arcane particles
            this.level().addParticle(ParticleTypes.ENCHANT,
                    px, py, pz,
                    -vel.x * 0.15, -vel.y * 0.15, -vel.z * 0.15);
            this.level().addParticle(ParticleTypes.WITCH,
                    px, py, pz,
                    (Math.random() - 0.5) * 0.05, (Math.random() - 0.5) * 0.05, (Math.random() - 0.5) * 0.05);
            if (this.tickCount % 2 == 0) {
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        px, py, pz,
                        (Math.random() - 0.5) * 0.04, 0.02, (Math.random() - 0.5) * 0.04);
            }
        }
    }

    private void dissolve() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    this.getX(), this.getY(), this.getZ(),
                    8, 0.12, 0.12, 0.12, 0.05);
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    this.getX(), this.getY(), this.getZ(),
                    6, 0.10, 0.10, 0.10, 0.02);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 0.6f, 1.8f);
        }
        this.discard();
    }

    private LivingEntity resolveTarget() {
        Vec3 curDir = this.getDeltaMovement().normalize();
        if (this.targetId != -1) {
            Entity ent = this.level().getEntity(this.targetId);
            if (ent instanceof LivingEntity living && living.isAlive() && canHitEntity(living)) {
                Vec3 toT = living.getBoundingBox().getCenter().subtract(this.position());
                if (toT.normalize().dot(curDir) >= 0.2) {
                    return living;
                }
            }
            this.targetId = -1;
        }

        // Search for nearest living entity in forward cone
        AABB searchBox = this.getBoundingBox().inflate(24.0);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class, searchBox, this::canHitEntity)) {
            Vec3 toCand = candidate.getBoundingBox().getCenter().subtract(this.position());
            double dist = toCand.length();
            if (dist > 24.0 || dist < 0.2) continue;

            Vec3 toCandNorm = toCand.normalize();
            double dot = toCandNorm.dot(curDir);
            if (dot < 0.25) continue; // Forward cone (~75 degrees)

            // Line of sight check
            BlockHitResult clip = this.level().clip(new ClipContext(
                    this.position(), candidate.getEyePosition(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this
            ));
            if (clip.getType() != HitResult.Type.MISS) continue;

            double score = dist * (2.0 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) {
            this.targetId = best.getId();
        }
        return best;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !entity.isPickable()) {
            return false;
        }
        if (entity == this.getOwner()) {
            return false;
        }
        if (this.getOwner() != null && entity.isAlliedTo(this.getOwner())) {
            return false;
        }
        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity target = hit.getEntity();

        // 1. Check if defending player has active Deny barrier protecting from this missile
        if (target instanceof ServerPlayer defendingPlayer && DenySpell.hasActiveBarrier(defendingPlayer)) {
            DenySpell.ActiveBarrier barrier = DenySpell.getBarrier(defendingPlayer);
            if (barrier != null && barrier.isThreatInProtectedArc(this.position(), defendingPlayer.position())) {
                DenySpell.reflectProjectile((ServerLevel) this.level(), defendingPlayer, barrier, this);
                return; // Reflected! Do not deal damage or discard!
            }
        }

        // 2. Normal impact
        if (this.level() instanceof ServerLevel serverLevel && target instanceof LivingEntity living) {
            DamageSource source = this.damageSources().indirectMagic(this, this.getOwner());
            living.invulnerableTime = 0;
            living.hurtTime = 0;
            living.hurt(source, this.getDamage());
            if (this.getOwner() instanceof LivingEntity livingOwner) {
                living.setLastHurtByPlayer(livingOwner instanceof Player p ? p : null);
            }

            // Audio & particle feedback
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.4f);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.6f);
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    this.getX(), this.getY(), this.getZ(),
                    14, 0.15, 0.15, 0.15, 0.06);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(),
                    8, 0.12, 0.12, 0.12, 0.05);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    this.getX(), this.getY(), this.getZ(),
                    10, 0.15, 0.15, 0.15, 0.08);
        }

        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 loc = hit.getLocation();
            serverLevel.playSound(null, loc.x, loc.y, loc.z,
                    SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 0.9f, 1.6f);
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    loc.x, loc.y, loc.z,
                    8, 0.10, 0.10, 0.10, 0.04);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    loc.x, loc.y, loc.z,
                    6, 0.10, 0.10, 0.10, 0.03);
        }
        this.discard();
    }

    private static Vec3 slerp(Vec3 start, Vec3 end, double t) {
        double dot = Math.max(-1.0, Math.min(1.0, start.dot(end)));
        double theta = Math.acos(dot) * t;
        Vec3 relative = end.subtract(start.scale(dot));
        if (relative.lengthSqr() < 1e-6) {
            return start;
        }
        relative = relative.normalize();
        return start.scale(Math.cos(theta)).add(relative.scale(Math.sin(theta)));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.getDamage());
        tag.putInt("Lifespan", this.lifespan);
        tag.putInt("TargetId", this.targetId);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Damage")) {
            this.setDamage(tag.getFloat("Damage"));
        }
        if (tag.contains("Lifespan")) {
            this.lifespan = tag.getInt("Lifespan");
        }
        if (tag.contains("TargetId")) {
            this.targetId = tag.getInt("TargetId");
        }
    }
}
