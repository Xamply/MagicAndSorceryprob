package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellImpacts;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellTargeting;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketThundajaImpact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ThundajaSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "thundaja");

    // Track active channeling target per player
    private static final Map<Integer, Vec3> PLAYER_TARGETS = new ConcurrentHashMap<>();

    public static class PreviousWeather {
        final boolean raining;
        final boolean thundering;
        final int clearWeatherTime;
        final int rainTime;
        final int thunderTime;

        public PreviousWeather(ServerLevel level) {
            if (level.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
                this.raining = level.isRaining();
                this.thundering = level.isThundering();
                this.clearWeatherTime = data.getClearWeatherTime();
                this.rainTime = data.getRainTime();
                this.thunderTime = data.getThunderTime();
            } else {
                this.raining = false;
                this.thundering = false;
                this.clearWeatherTime = 6000;
                this.rainTime = 0;
                this.thunderTime = 0;
            }
        }

        public void restore(ServerLevel level) {
            level.setWeatherParameters(clearWeatherTime, rainTime, raining, thundering);
        }
    }

    private static int activeStormCount = 0;
    private static PreviousWeather savedWeather = null;

    public static synchronized void onChannelStart(ServerLevel level) {
        if (activeStormCount == 0) {
            savedWeather = new PreviousWeather(level);
        }
        activeStormCount++;
    }

    public static synchronized void onChannelEnd(ServerLevel level) {
        activeStormCount = Math.max(0, activeStormCount - 1);
        if (activeStormCount == 0 && savedWeather != null) {
            savedWeather.restore(level);
            savedWeather = null;
        }
    }

    public ThundajaSpell() {
        super(ID, SpellSchool.LIGHTNING, SpellType.AREA,
                100.0f, // Base mana cost (100 MP)
                300,    // Base cast time ticks (15.0 seconds)
                600,    // Base cooldown ticks (30.0 seconds)
                150.0f  // Base damage (Hand: 112.5, Ring: 165, Book: 195, Wand: 240)
        );
    }

    public static void setPlayerChannelTarget(int playerId, Vec3 target) {
        PLAYER_TARGETS.put(playerId, target);
    }

    public static void clearPlayerChannelTarget(int playerId) {
        PLAYER_TARGETS.remove(playerId);
    }

    public static Vec3 getPlayerChannelTarget(int playerId) {
        return PLAYER_TARGETS.get(playerId);
    }

    @Override
    public double getRange() {
        return 36.0;
    }

    @Override
    public float calculateFinalManaCost(CastingMethod method) {
        // Exactly 100 mana regardless of catalyst, matching exact spell specification
        return 100.0f;
    }

    @Override
    public int calculateFinalCastTime(CastingMethod method) {
        // Fixed 15-second channel (300 ticks) as an intentional ritual
        return 300;
    }

    /**
     * Determines a reliable ground target position for Thundaja.
     * Accurately tracks where the cursor is looking:
     * 1. If looking at a mob/entity, snaps to the ground beneath that entity.
     * 2. If looking at terrain/blocks, snaps directly to the block surface or foot level on walls.
     * 3. If looking at the sky/open air, projects along the aim direction and raycasts down to the terrain.
     */
    public static Vec3 findThundajaGroundTarget(Level level, Player player, double maxRange) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(maxRange));
        // Water/lava surfaces count as ground so the storm circle stays visible on top of the liquid
        ClipContext.Fluid fluid = SpellTargeting.aimFluidMode(level, eyePos);

        // 1. Raycast along line of sight for blocks
        BlockHitResult blockHit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                fluid,
                player
        ));
        Vec3 maxCheckPos = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation() : endPos;

        // 2. Entity check along line of sight (if cursor is directly over an entity, snap to ground below it)
        AABB searchBox = new AABB(eyePos, maxCheckPos).inflate(1.5);
        EntityHitResult entityHit = null;
        double closestDistSq = eyePos.distanceToSqr(maxCheckPos);

        for (Entity entity : level.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable() && e != player)) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> clip = entityBox.clip(eyePos, maxCheckPos);
            if (clip.isPresent()) {
                double distSq = eyePos.distanceToSqr(clip.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    entityHit = new EntityHitResult(entity, clip.get());
                }
            }
        }

        if (entityHit != null && entityHit.getEntity() != null) {
            Entity targetEnt = entityHit.getEntity();
            Vec3 ground = SpellTargeting.dropToGround(level, targetEnt.position().add(0, 0.5, 0), player, fluid);
            return ground != null ? ground : targetEnt.position();
        }

        // 3. Block hit handling
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            if (blockHit.getDirection() == Direction.UP) {
                return blockHit.getLocation();
            }
            // If aimed at a cliff side or wall, step slightly outward and snap downward to floor
            Vec3 hitLoc = blockHit.getLocation();
            Vec3 stepOffset = new Vec3(
                    blockHit.getDirection().getStepX() * 0.15,
                    0.0,
                    blockHit.getDirection().getStepZ() * 0.15
            );
            Vec3 ground = SpellTargeting.dropToGround(level, hitLoc.add(stepOffset), player, fluid);
            return ground != null ? ground : hitLoc;
        }

        // 4. Looking into open air or sky (MISS):
        // Project forward along look vector horizontally and raycast downward to find the actual ground
        double flatDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        double targetDist = maxRange * 0.75;
        double targetX = eyePos.x + (flatDist > 0.001 ? (lookVec.x / flatDist) * targetDist : 0);
        double targetZ = eyePos.z + (flatDist > 0.001 ? (lookVec.z / flatDist) * targetDist : 0);

        double startY = Math.min(level.getMaxBuildHeight() - 1, player.getY() + 32.0);
        Vec3 ground = SpellTargeting.dropToGround(level, new Vec3(targetX, startY, targetZ), player, fluid);
        if (ground != null) {
            return ground;
        }

        return new Vec3(targetX, player.getY(), targetZ);
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        // 1. Retrieve or calculate target impact point
        Vec3 rawCenter = PLAYER_TARGETS.remove(player.getId());
        if (rawCenter == null) {
            rawCenter = findThundajaGroundTarget(serverLevel, player, getRange());
        }
        final Vec3 center = rawCenter;

        float finalDamage = calculateFinalDamage(method);

        // 2. 13x13 Block Area of Impact (6.5 blocks in each horizontal direction from center)
        AABB impactBox = new AABB(
                center.x - 6.5, center.y - 8.0, center.z - 6.5,
                center.x + 6.5, center.y + 12.0, center.z + 6.5
        );

        // 2.1. Damage all living entities in the 13x13 area (including caster if they remained inside)
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, impactBox,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity targetEntity : targets) {
            double dx = Math.abs(center.x - targetEntity.getX());
            double dz = Math.abs(center.z - targetEntity.getZ());
            if (dx <= 6.5 && dz <= 6.5) {
                DamageSource damageSource = (targetEntity == player) ?
                        player.damageSources().lightningBolt() :
                        player.damageSources().indirectMagic(player, player);
                targetEntity.hurt(damageSource, finalDamage);
                if (targetEntity != player) {
                    targetEntity.setLastHurtByPlayer(player);
                }
                targetEntity.setSecondsOnFire(6);
                targetEntity.setTicksFrozen(0);
            }
        }

        // 2.2. The falling bolt counts as a push: shockwave throws everything nearby outward and upward
        SpellImpacts.shockwave(serverLevel, center, 8.5, 1.5, 0.7);

        // 2.3. Destroy the first exposed surface layer of blocks directly struck by lightning in the 13x13 area
        int startY = (int) Math.floor(center.y + 6.0);
        int minY = (int) Math.floor(center.y - 6.0);
        int baseBlockX = (int) Math.floor(center.x);
        int baseBlockZ = (int) Math.floor(center.z);

        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int bx = baseBlockX + dx;
                int bz = baseBlockZ + dz;
                for (int by = startY; by >= minY; by--) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) continue;

                    // If replaceable foliage/snow, obliterate it without drops and continue down to the ground
                    if (state.canBeReplaced()) {
                        serverLevel.destroyBlock(pos, false, player);
                        continue;
                    }

                    // Destroy the first solid/liquid surface layer without drops (respect bedrock & indestructible)
                    float destroySpeed = state.getDestroySpeed(serverLevel, pos);
                    if (destroySpeed >= 0.0f && !state.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE)) {
                        serverLevel.destroyBlock(pos, false, player);
                    }
                    // Stop after destroying the first solid surface layer in this column!
                    break;
                }
            }
        }

        // 2.4. Obliterate all dropped items on the ground within the 13x13 area without drops
        List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class, impactBox,
                item -> item.isAlive() && Math.abs(center.x - item.getX()) <= 6.5 && Math.abs(center.z - item.getZ()) <= 6.5);
        for (ItemEntity item : items) {
            item.discard();
        }

        // 3. Broadcast colossal lightning impact to nearby clients
        long seed = serverLevel.getRandom().nextLong();
        ModNetwork.sendToNearby(new PacketThundajaImpact(player.getId(), center, seed), serverLevel, center, 96.0);

        // 4. Cataclysmic sound effects
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 4.0f, 0.9f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 0.8f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.6f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 3.5f, 0.75f);

        // 5. Ground scorched impact particles across the 13x13 area
        serverLevel.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 8, 2.0, 1.2, 2.0, 0.0);
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.5, center.z, 160, 6.2, 0.8, 6.2, 0.3);
        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 0.2, center.z, 50, 6.0, 0.4, 6.0, 0.05);

        // 6. Restore original world weather
        onChannelEnd(serverLevel);

        return true;
    }
}
