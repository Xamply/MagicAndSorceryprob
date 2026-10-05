package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellImpacts;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellTargeting;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketFallingSwordStrike;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID)
public class LaPollaCayendoSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "la_polla_cayendo");

    // Track active channeling target per player
    private static final Map<Integer, Vec3> PLAYER_TARGETS = new ConcurrentHashMap<>();

    // Server-side active strikes tracking
    public static class ActiveStrike {
        public final ServerPlayer player;
        public final ServerLevel level;
        public final Vec3 center;
        public final double obstacleY;
        public final float finalDamage;
        public int ticksElapsed;
        public static final int IMPACT_TICK = 34; // 16 fall + 12 pause + 6 slam

        public ActiveStrike(ServerPlayer player, ServerLevel level, Vec3 center, double obstacleY, float finalDamage) {
            this.player = player;
            this.level = level;
            this.center = center;
            this.obstacleY = obstacleY;
            this.finalDamage = finalDamage;
            this.ticksElapsed = 0;
        }
    }

    private static final List<ActiveStrike> ACTIVE_STRIKES = new ArrayList<>();

    public LaPollaCayendoSpell() {
        super(ID, SpellSchool.PHYSICAL, SpellType.DESTRUCTION,
                80.0f,  // Base mana cost (80 MP)
                100,    // Base cast time ticks (5.0 seconds)
                300,    // Base cooldown ticks (15.0 seconds)
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
        return 40.0;
    }

    @Override
    public float calculateFinalManaCost(CastingMethod method) {
        return 80.0f;
    }

    @Override
    public int calculateFinalCastTime(CastingMethod method) {
        return 100; // 5.0 seconds
    }

    @Override
    public int calculateFinalCooldown(CastingMethod method) {
        return 300; // 15.0 seconds
    }

    /**
     * Determines a reliable ground target position for the falling sword.
     * Projects forward along line of sight and snaps to the floor.
     */
    public static Vec3 findGroundTarget(Level level, Player player, double maxRange) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(maxRange));
        // Water/lava surfaces count as ground so the reticle stays visible on top of the liquid
        ClipContext.Fluid fluid = SpellTargeting.aimFluidMode(level, eyePos);

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                fluid,
                player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            if (hit.getDirection() == Direction.UP) {
                return hit.getLocation();
            }
            Vec3 hitLoc = hit.getLocation();
            Vec3 ground = SpellTargeting.dropToGround(level, hitLoc, player, fluid);
            return ground != null ? ground : hitLoc;
        }

        double flatDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        double targetX = eyePos.x + (flatDist > 0.001 ? (lookVec.x / flatDist) * maxRange * 0.75 : 0);
        double targetZ = eyePos.z + (flatDist > 0.001 ? (lookVec.z / flatDist) * maxRange * 0.75 : 0);

        Vec3 skyGround = SpellTargeting.dropToGround(level,
                new Vec3(targetX, Math.min(level.getMaxBuildHeight() - 2, player.getY() + 30.0), targetZ),
                player, fluid);
        if (skyGround != null) {
            return skyGround;
        }

        return new Vec3(targetX, player.getY(), targetZ);
    }

    /**
     * Finds the highest obstacle (block or entity) directly in the vertical path above the target.
     */
    public static double findHighestObstacle(Level level, Vec3 center) {
        double highest = center.y;

        // 1. Raycast blocks from 50 blocks above
        Vec3 topPos = new Vec3(center.x, Math.min(level.getMaxBuildHeight() - 1, center.y + 50.0), center.z);
        Vec3 bottomPos = new Vec3(center.x, Math.max(level.getMinBuildHeight(), center.y - 15.0), center.z);

        BlockHitResult blockHit = level.clip(new ClipContext(
                topPos, bottomPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null
        ));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            highest = Math.max(highest, blockHit.getLocation().y);
        }

        // 2. Check living entities near the vertical line
        AABB colBox = new AABB(
                center.x - 1.2, center.y - 2.0, center.z - 1.2,
                center.x + 1.2, center.y + 40.0, center.z + 1.2
        );
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, colBox,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity entity : entities) {
            double entityTop = entity.getY() + entity.getBbHeight();
            if (entityTop > highest) {
                highest = entityTop;
            }
        }

        return highest;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Vec3 rawCenter = PLAYER_TARGETS.remove(player.getId());
        if (rawCenter == null) {
            rawCenter = findGroundTarget(serverLevel, player, getRange());
        }
        final Vec3 center = rawCenter;
        final double obstacleY = findHighestObstacle(serverLevel, center);
        final float finalDamage = calculateFinalDamage(method);

        // Register active strike on server
        synchronized (ACTIVE_STRIKES) {
            ACTIVE_STRIKES.add(new ActiveStrike(player, serverLevel, center, obstacleY, finalDamage));
        }

        // Broadcast strike packet to nearby clients
        ModNetwork.sendToNearby(
                new PacketFallingSwordStrike(player.getId(), center, obstacleY),
                serverLevel,
                center,
                128.0
        );

        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        synchronized (ACTIVE_STRIKES) {
            Iterator<ActiveStrike> it = ACTIVE_STRIKES.iterator();
            while (it.hasNext()) {
                ActiveStrike strike = it.next();
                strike.ticksElapsed++;

                // Trigger catastrophic impact at designated tick
                if (strike.ticksElapsed >= ActiveStrike.IMPACT_TICK) {
                    performImpact(strike);
                    it.remove();
                }
            }
        }
    }

    private static void performImpact(ActiveStrike strike) {
        ServerLevel level = strike.level;
        ServerPlayer player = strike.player;
        Vec3 center = strike.center;
        double obstacleY = strike.obstacleY;
        float damage = strike.finalDamage;

        // 1. DAMAGE ENTITIES (Base 150 DMG in 6.5 block radius, including player if inside!)
        AABB impactBox = new AABB(
                center.x - 6.5, obstacleY - 4.0, center.z - 6.5,
                center.x + 6.5, obstacleY + 7.0, center.z + 6.5
        );

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, impactBox,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity target : targets) {
            double dx = center.x - target.getX();
            double dz = center.z - target.getZ();
            double distSq = dx * dx + dz * dz;

            if (distSq <= 6.5 * 6.5) {
                DamageSource src = (target == player)
                        ? player.damageSources().generic()
                        : player.damageSources().playerAttack(player);

                target.hurt(src, damage);
                if (target != player) {
                    target.setLastHurtByPlayer(player);
                }
            }
        }

        // Falling impact counts as a push: shockwave throws everything nearby outward and upward
        SpellImpacts.shockwave(level, new Vec3(center.x, obstacleY, center.z), 9.0, 1.9, 0.8);

        // 2. EXPLOSION
        // Generates a controlled high-potency explosion to complement physical destruction
        level.explode(player, center.x, obstacleY, center.z, 3.8f, Level.ExplosionInteraction.BLOCK);

        // 3. TERRAIN DESTRUCTION (Impact Crater)
        // Devastates solid ground in a hemispherical crater of radius 4.0
        int baseBX = (int) Math.floor(center.x);
        int baseBY = (int) Math.floor(obstacleY);
        int baseBZ = (int) Math.floor(center.z);
        int radius = 4;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= 2; dy++) {
                    double distSq = dx * dx + dy * dy * 1.3 + dz * dz;
                    if (distSq <= radius * radius + 0.5) {
                        BlockPos pos = new BlockPos(baseBX + dx, baseBY + dy, baseBZ + dz);
                        BlockState state = level.getBlockState(pos);

                        if (state.isAir()) continue;

                        // Preserve unbreakable structures (Bedrock, End Portals, etc.)
                        float speed = state.getDestroySpeed(level, pos);
                        if (speed >= 0.0f && !state.is(BlockTags.WITHER_IMMUNE)) {
                            // 25% drop chance for debris, rest pulverized into dust
                            boolean dropItem = level.random.nextFloat() < 0.25f;
                            level.destroyBlock(pos, dropItem, player);
                        }
                    }
                }
            }
        }

        // 4. RANDOM FIRE GENERATION
        // Ignites scattered fires around the destroyed impact area
        for (int fx = -5; fx <= 5; fx++) {
            for (int fz = -5; fz <= 5; fz++) {
                if (level.random.nextFloat() < 0.35f) {
                    int bx = baseBX + fx;
                    int bz = baseBZ + fz;
                    // Find highest solid block floor
                    for (int by = baseBY + 3; by >= baseBY - 5; by--) {
                        BlockPos checkPos = new BlockPos(bx, by, bz);
                        BlockPos abovePos = checkPos.above();
                        if (!level.getBlockState(checkPos).isAir()
                                && level.getBlockState(checkPos).isSolid()
                                && level.getBlockState(abovePos).isAir()) {
                            level.setBlock(abovePos, Blocks.FIRE.defaultBlockState(), 3);
                            break;
                        }
                    }
                }
            }
        }

        // 5. CATASTROPHIC IMPACT SOUNDS (Custom SwordExplosion Audio + Metallic Land)
        level.playSound(null, center.x, obstacleY, center.z,
                com.cesar.magicandsorcery.sound.ModSounds.SWORD_EXPLOSION.get(), SoundSource.PLAYERS, 4.0f, 1.0f);
        level.playSound(null, center.x, obstacleY, center.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 4.0f, 0.45f);

        // 6. SPECTACULAR PARTICLES
        level.sendParticles(ParticleTypes.FLASH, center.x, obstacleY + 1.0, center.z, 6, 1.2, 1.2, 1.2, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, obstacleY + 0.5, center.z, 4, 1.0, 0.5, 1.0, 0.0);
        level.sendParticles(ParticleTypes.LAVA, center.x, obstacleY + 0.5, center.z, 60, 4.0, 1.5, 4.0, 0.5);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, obstacleY + 0.5, center.z, 80, 5.0, 1.0, 5.0, 0.1);
        level.sendParticles(ParticleTypes.FLAME, center.x, obstacleY + 0.5, center.z, 50, 4.5, 1.0, 4.5, 0.2);
    }
}
