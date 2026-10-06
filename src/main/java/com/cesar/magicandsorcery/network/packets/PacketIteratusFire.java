package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.entity.IteratusMissileEntity;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.catalyst.ICatalyst;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketIteratusFire {

    public PacketIteratusFire() {
    }

    public PacketIteratusFire(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.isAlive()) return;

            player.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                boolean isCreative = player.isCreative();
                boolean bypassMana = isCreative && com.cesar.magicandsorcery.config.ModConfigs.INFINITE_MANA_IN_CREATIVE.get();
                float cost = 10.0f; // 10 Mana per missile

                if (!bypassMana && !magicData.canConsumeMana(cost)) {
                    // Out of mana! Apply 10s cooldown and notify client
                    magicData.setSpellCooldown(ModSpells.ITERATUS.getId(), 200);
                    ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), player);
                    return;
                }

                if (!bypassMana) {
                    magicData.consumeMana(cost);
                }

                // Determine catalyst casting method for multiplier
                CastingMethod method = CastingMethod.BARE_HANDS;
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof ICatalyst catalyst) {
                    method = catalyst.getCastingMethod(mainHand);
                } else {
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof ICatalyst catalyst) {
                        method = catalyst.getCastingMethod(offHand);
                    }
                }

                float finalDamage = ModSpells.ITERATUS.calculateFinalDamage(method);

                // Spawn missile entity in world
                ServerLevel level = player.serverLevel();
                Vec3 eyePos = player.getEyePosition();
                Vec3 lookVec = player.getViewVector(1.0f).normalize();

                Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0));
                rightVec = rightVec.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rightVec.normalize();
                double armOffset = (player.getMainArm() == HumanoidArm.RIGHT) ? 0.28 : -0.28;
                Vec3 spawnPos = eyePos.add(lookVec.scale(0.4)).add(rightVec.scale(armOffset)).add(0, -0.15, 0);

                IteratusMissileEntity missile = new IteratusMissileEntity(level, player, finalDamage);
                missile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                missile.setDeltaMovement(lookVec.scale(1.15));
                level.addFreshEntity(missile);

                // Casting audio
                level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                        SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 1.8f);
                level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.6f);

                // Sync updated mana to client immediately
                ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), player);
            });
        });
        return true;
    }
}
