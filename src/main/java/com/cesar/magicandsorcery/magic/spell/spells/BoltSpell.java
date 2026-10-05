package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketBoltVisual;
import com.cesar.magicandsorcery.sound.ModSounds;

import java.util.Random;

public class BoltSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "bolt");

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
        return 32.0;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        double reach = 32.0;
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        // 1. Block raycast
        BlockHitResult blockHit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER,
                com.cesar.magicandsorcery.magic.spell.SpellTargeting.aimFluidMode(level, eyePos), player));
        Vec3 hitPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : endPos;

        // 2. Entity raycast: find closest pickable entity intersecting the ray
        AABB searchBox = new AABB(eyePos, hitPos).inflate(2.0);
        EntityHitResult entityHit = null;
        double closestDistSq = eyePos.distanceToSqr(hitPos);

        for (Entity entity : serverLevel.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable() && e != player)) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> clip = entityBox.clip(eyePos, hitPos);
            if (clip.isPresent()) {
                double distSq = eyePos.distanceToSqr(clip.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    entityHit = new EntityHitResult(entity, clip.get());
                }
            }
        }

        float finalDamage = calculateFinalDamage(method);

        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            hitPos = entityHit.getLocation();
            // Attribute magic damage directly to the caster player
            net.minecraft.world.damagesource.DamageSource magicDamage = player.damageSources().indirectMagic(player, player);
            target.hurt(magicDamage, finalDamage);
            target.setLastHurtByPlayer(player);
            target.setSecondsOnFire(1);
        }

        // 3. Catalyst Origin Position (starts from casting hand / catalyst tip)
        Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
        if (rightVec.lengthSqr() < 0.001) {
            rightVec = new Vec3(1, 0, 0);
        }
        double armOffset = (player.getMainArm() == HumanoidArm.RIGHT) ? 0.28 : -0.28;
        Vec3 catalystPos = eyePos.add(lookVec.scale(0.35))
                .add(rightVec.scale(armOffset))
                .add(0, -0.18, 0);

        // 4. Launch spark at catalyst tip
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                catalystPos.x, catalystPos.y, catalystPos.z,
                3, 0.02, 0.02, 0.02, 0.02);

        // 5. Send authentic Minecraft lightning visual (emanating from catalyst toward target along aim angle)
        ModNetwork.sendToNearby(new PacketBoltVisual(catalystPos, hitPos, serverLevel.getRandom().nextLong()),
                serverLevel, catalystPos, 64.0);

        // 6. Impact spark discharge on hit
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                hitPos.x, hitPos.y, hitPos.z,
                8, 0.10, 0.10, 0.10, 0.06);

        // 7. Custom sound effect from Audios/Bolt.mp3 (registered as ModSounds.BOLT)
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.BOLT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        if (catalystPos.distanceTo(hitPos) > 16.0) {
            serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                    ModSounds.BOLT.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }

        return true;
    }
}
