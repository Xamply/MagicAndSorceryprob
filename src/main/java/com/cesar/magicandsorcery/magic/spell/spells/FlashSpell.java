package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FlashSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "flash");

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

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        double maxDistance = getRange(method);
        Vec3 start = player.position().add(0, player.getEyeHeight() * 0.5, 0);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 target = start.add(look.scale(maxDistance));

        // Clip against blocks to prevent phasing into walls
        BlockHitResult hit = level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 dest;
        if (hit.getType() != HitResult.Type.MISS) {
            // Step back slightly from hit point
            dest = hit.getLocation().subtract(look.scale(0.8));
        } else {
            dest = target;
        }

        // Ensure floor or safe vertical positioning
        BlockPos destBlock = BlockPos.containing(dest.x, dest.y, dest.z);
        while (level.getBlockState(destBlock).blocksMotion() && destBlock.getY() < level.getMaxBuildHeight()) {
            destBlock = destBlock.above();
            dest = new Vec3(dest.x, destBlock.getY(), dest.z);
        }

        // Origin particles and sound
        Vec3 origin = player.position();
        serverLevel.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1.0, origin.z, 20, 0.3, 0.5, 0.3, 0.1);
        serverLevel.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);

        // Teleport
        player.teleportTo(dest.x, dest.y, dest.z);
        player.resetFallDistance();

        // Destination particles and sound
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.5, 0.3, 0.1);
        serverLevel.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.4f);

        return true;
    }
}
