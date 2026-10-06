package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketIteratusEnd {

    public PacketIteratusEnd() {
    }

    public PacketIteratusEnd(FriendlyByteBuf buf) {
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
                boolean bypassCooldown = isCreative && com.cesar.magicandsorcery.config.ModConfigs.NO_COOLDOWN_IN_CREATIVE.get();

                if (!bypassCooldown) {
                    // Apply 10 seconds cooldown (200 ticks) upon ending the continuous barrage
                    magicData.setSpellCooldown(ModSpells.ITERATUS.getId(), 200);
                }

                ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), player);
            });
        });
        return true;
    }
}
