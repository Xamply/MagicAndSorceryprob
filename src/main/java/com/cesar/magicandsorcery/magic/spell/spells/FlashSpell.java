package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellImpacts;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellTargeting;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketFlashVisual;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flash: blink to where you are looking. The destination is computed by {@link #findDestination}, shared by the
 * client preview and the server, always lands on a spot where the caster fits, and swaps places with any
 * creature standing there.
 */
public class FlashSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "flash");

    /** Destination the caster's client saw when releasing the spell (validated before use). */
    private static final Map<UUID, Vec3> REQUESTED_TARGETS = new ConcurrentHashMap<>();

    public FlashSpell() {
        super(ID, SpellSchool.TELEPORTATION, SpellType.MOBILITY,
                25.0f, // Base mana cost
                20,    // Base cast time ticks (1.0s)
                120,   // Base cooldown ticks (6.0s)
                0.0f   // NO DAMAGE (immune to damage multipliers)
        );
    }

    @Override
    public int calculateFinalCastTime(CastingMethod method) {
        if (method == CastingMethod.RING) {
            return 10; // 0.5 segundos con el anillo
        }
        if (method == CastingMethod.WAND) {
            return 40; // 2.0 segundos con el bastón/vara de magia
        }
        return 20; // 1.0 segundo base (manos desnudas / libro)
    }

    @Override
    public double getRange() {
        return 9.0;
    }

    @Override
    public double getRange(CastingMethod method) {
        if (method == CastingMethod.WAND) {
            return 15.0; // 15 bloques de distancia con bastón/vara de magia
        }
        return 9.0; // 9 bloques estándar
    }

    public static void setRequestedTarget(Player player, Vec3 target) {
        REQUESTED_TARGETS.put(player.getUUID(), target);
    }

    // ------------------------------------------------------------------
    // Destination search (client preview + server)
    // ------------------------------------------------------------------

    /**
     * Where the caster would land: feet position on a spot where their hitbox fits, or null if there is none.
     */
    @Nullable
    public static Vec3 findDestination(Level level, Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        ClipContext.Fluid fluid = SpellTargeting.aimFluidMode(level, eye);
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, fluid, player));
        Vec3 rayEnd = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;

        // A creature under the crosshair: land exactly where it stands (and swap places with it)
        LivingEntity aimed = aimedCreature(level, player, eye, rayEnd);
        if (aimed != null) {
            for (double up = 0.0; up <= 1.0; up += 0.25) {
                Vec3 spot = aimed.position().add(0, up, 0);
                if (fits(level, player, spot)) {
                    return spot;
                }
            }
        }

        double halfWidth = player.getBbWidth() / 2.0;
        double height = player.getBbHeight();
        Vec3 candidate;
        if (hit.getType() == HitResult.Type.BLOCK) {
            Direction face = hit.getDirection();
            Vec3 loc = hit.getLocation();
            if (face == Direction.UP) {
                candidate = loc;
            } else if (face == Direction.DOWN) {
                // Aiming at a ceiling: hang just below it
                candidate = loc.subtract(0, height + 0.05, 0);
            } else {
                Vec3 ledge = ledgeTop(level, player, hit);
                if (ledge != null) {
                    // Aiming at the side of a step or low wall: climb on top of it
                    candidate = ledge;
                } else {
                    // A tall wall: land on the floor right in front of it
                    Vec3 out = loc.add(face.getStepX() * (halfWidth + 0.05), 0, face.getStepZ() * (halfWidth + 0.05));
                    candidate = snapDown(level, player, out, range, fluid, out.subtract(0, height * 0.5, 0));
                }
            }
        } else {
            // Nothing in reach: the farthest point of the ray, brought down to the ground beneath it
            Vec3 feet = end.subtract(0, player.getEyeHeight(), 0);
            candidate = snapDown(level, player, feet.add(0, player.getEyeHeight(), 0), range + player.getEyeHeight(), fluid, feet);
        }

        // Find the nearest spot where the body fits, backing off towards the caster if needed
        Vec3 flatBack = new Vec3(-look.x, 0, -look.z);
        flatBack = flatBack.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flatBack.normalize();
        double maxBack = Math.min(range, candidate.distanceTo(player.position()));
        for (double back = 0.0; back <= maxBack; back += 0.5) {
            Vec3 base = candidate.add(flatBack.scale(back));
            if (back > 0.0) {
                base = snapDown(level, player, base.add(0, 0.5, 0), 2.0, fluid, base);
            }
            for (double up = 0.0; up <= 1.5; up += 0.25) {
                Vec3 spot = base.add(0, up, 0);
                if (fits(level, player, spot)) {
                    return spot;
                }
            }
        }
        return null;
    }

    @Nullable
    private static LivingEntity aimedCreature(Level level, Player player, Vec3 eye, Vec3 rayEnd) {
        LivingEntity best = null;
        double bestDist = eye.distanceToSqr(rayEnd);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, rayEnd).inflate(1.0),
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.isPassenger())) {
            java.util.Optional<Vec3> clip = e.getBoundingBox().inflate(0.3).clip(eye, rayEnd);
            if (clip.isPresent() && eye.distanceToSqr(clip.get()) < bestDist) {
                bestDist = eye.distanceToSqr(clip.get());
                best = e;
            }
        }
        return best;
    }

    /** Top of the block whose side was hit, if it is low enough to step onto (and there is room up there). */
    @Nullable
    private static Vec3 ledgeTop(Level level, Player player, BlockHitResult hit) {
        net.minecraft.core.BlockPos pos = hit.getBlockPos();
        net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        double top = pos.getY() + (shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y));
        Vec3 loc = hit.getLocation();
        if (top - loc.y > 1.3) {
            return null;
        }
        Direction face = hit.getDirection();
        for (double in = 0.35; in <= 0.75; in += 0.2) {
            Vec3 spot = new Vec3(loc.x - face.getStepX() * in, top, loc.z - face.getStepZ() * in);
            if (fits(level, player, spot)) {
                return spot;
            }
        }
        return null;
    }

    private static Vec3 snapDown(Level level, Player player, Vec3 from, double maxDrop, ClipContext.Fluid fluid, Vec3 fallback) {
        Vec3 ground = SpellTargeting.dropToGround(level, from.add(0, 0.05, 0), player, fluid);
        if (ground != null && from.y - ground.y <= maxDrop) {
            return ground;
        }
        return fallback;
    }

    public static boolean fits(Level level, Player player, Vec3 feet) {
        AABB box = player.getBoundingBox().move(feet.subtract(player.position())).deflate(0.01);
        return level.noCollision(player, box);
    }

    /**
     * Creature standing at (or right next to) the destination, which will swap places with the caster.
     */
    @Nullable
    public static LivingEntity findSwapTarget(Level level, Player player, Vec3 dest) {
        AABB box = new AABB(dest.x - SWAP_RADIUS, dest.y - 1.0, dest.z - SWAP_RADIUS,
                dest.x + SWAP_RADIUS, dest.y + player.getBbHeight() + 0.5, dest.z + SWAP_RADIUS);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.isPassenger())) {
            double dx = entity.getX() - dest.x, dz = entity.getZ() - dest.z;
            double d = dx * dx + dz * dz;
            if (d <= SWAP_RADIUS * SWAP_RADIUS && d < bestDist) {
                bestDist = d;
                best = entity;
            }
        }
        return best;
    }

    /** How close to the destination a creature has to be to swap places with the caster. */
    private static final double SWAP_RADIUS = 1.6;

    // ------------------------------------------------------------------
    // Cast
    // ------------------------------------------------------------------

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        double range = getRange(method);

        Vec3 dest = null;
        Vec3 requested = REQUESTED_TARGETS.remove(player.getUUID());
        if (requested != null && isValidRequest(serverLevel, player, requested, range)) {
            dest = requested;
        }
        if (dest == null) {
            dest = findDestination(serverLevel, player, range);
        }
        if (dest == null) {
            player.displayClientMessage(Component.translatable("message.magic_and_sorcery.flash_blocked")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        Vec3 origin = player.position();
        float height = player.getBbHeight();
        LivingEntity swap = findSwapTarget(serverLevel, player, dest);

        // Everyone nearby sees the blink (both ends of the jump)
        ModNetwork.sendToNearby(new PacketFlashVisual(player.getId(), origin, dest, height, serverLevel.getRandom().nextLong()),
                serverLevel, origin.lerp(dest, 0.5), 80.0);

        // Departure: the vacuum left behind pulls nearby things in
        if (swap == null) {
            SpellImpacts.shockwave(serverLevel, origin, 3.0, -0.35, 0.12, player);
        }
        serverLevel.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.2f, 1.3f);
        serverLevel.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.6f);

        // Teleport
        player.teleportTo(dest.x, dest.y, dest.z);
        player.resetFallDistance();

        if (swap != null) {
            // Swap places: the creature at the destination is thrown back to where the caster stood
            ModNetwork.sendToNearby(new PacketFlashVisual(swap.getId(), swap.position(), origin, swap.getBbHeight(),
                    serverLevel.getRandom().nextLong()), serverLevel, origin.lerp(dest, 0.5), 80.0);
            swap.teleportTo(origin.x, origin.y, origin.z);
            swap.resetFallDistance();
            serverLevel.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f);
        } else {
            // Arrival: burst of force shoving everything around outward
            SpellImpacts.shockwave(serverLevel, dest, 3.5, 0.6, 0.3, player);
        }
        serverLevel.playSound(null, dest.x, dest.y, dest.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 1.5f);
        serverLevel.playSound(null, dest.x, dest.y, dest.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.9f, 1.7f);
        return true;
    }

    /**
     * Accepts the destination the client saw if it is in range, fits the caster and is visible from their eyes.
     */
    private static boolean isValidRequest(ServerLevel level, ServerPlayer player, Vec3 target, double range) {
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) return false;
        if (target.distanceTo(player.getEyePosition()) > range + 2.0) return false;
        if (!fits(level, player, target)) return false;
        Vec3 eye = player.getEyePosition();
        for (double frac : new double[]{0.9, 0.5, 0.1}) {
            Vec3 point = target.add(0, player.getBbHeight() * frac, 0);
            BlockHitResult los = level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (los.getType() == HitResult.Type.MISS || los.getLocation().distanceToSqr(point) < 0.25) {
                return true;
            }
        }
        return false;
    }
}
