package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketDivineSwordSweep;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DivineSwordSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "divine_sword");

    public DivineSwordSpell() {
        super(ID, SpellSchool.HOLY, SpellType.AREA,
                50.0f,  // Base mana cost (50 MP)
                20,     // Base cast time ticks (1.0 second channel)
                100,    // Base cooldown ticks (5.0 seconds)
                90.0f   // Base damage (Hand: 67.5, Ring: 99.0, Book: 117.0, Wand: 144.0)
        );
    }

    @Override
    public double getRange() {
        return 5.0; // 5 blocks depth
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        float finalDamage = calculateFinalDamage(method);
        Vec3 playerPos = player.position();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();

        // Broad bounding box around the 6 (width) x 5 (depth) x 2 (height) sweep area
        AABB sweepBox = player.getBoundingBox().inflate(6.0, 2.5, 6.0);

        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e != player && !(e instanceof com.cesar.magicandsorcery.entity.BobEntity bob && bob.getUUID().equals(player.getUUID()))
                        && e.isAlive() && !e.isSpectator());

        int hitCount = 0;
        for (LivingEntity target : targets) {
            Vec3 diff = target.position().subtract(playerPos);
            double distFwd = diff.dot(forward);
            double distSide = Math.abs(diff.dot(side));
            double distY = target.getY() - player.getY();

            // Check dimensions: 5 blocks depth, 6 blocks width (±3.0 from center), 2 blocks height
            if (distFwd >= 0.0 && distFwd <= 5.5 && distSide <= 3.2 && distY >= -1.0 && distY <= 2.8) {
                target.hurt(player.damageSources().playerAttack(player), finalDamage);
                // Holy slash throws the target back (ragdolls it when Ragdoll Physics is installed)
                com.cesar.magicandsorcery.magic.spell.SpellImpacts.push(target, new Vec3(forward.x * 1.2, 0.45, forward.z * 1.2));
                hitCount++;

                // Spawn impact particles on hit entity
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        15, 0.3, 0.3, 0.3, 0.2);
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        12, 0.25, 0.25, 0.25, 0.15);
            }
        }

        // Sound effects
        // Strong sword sweep slash sound
        serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.85f);
        // Heavy divine resonating impact sound
        serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.8f, 1.25f);
        if (hitCount > 0) {
            serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 2.2f, 0.9f);
        }

        // Broadcast visual sweep packet to all players in radius 64
        ModNetwork.sendToNearby(new PacketDivineSwordSweep(player.getId(), yaw, pitch, playerPos),
                serverLevel, playerPos, 64.0);

        return true;
    }
}
