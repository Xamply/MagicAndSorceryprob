package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSelectSpell {
    private final int targetIndex;
    private final boolean next;

    public PacketSelectSpell(boolean next) {
        this.targetIndex = -1;
        this.next = next;
    }

    public PacketSelectSpell(int targetIndex) {
        this.targetIndex = targetIndex;
        this.next = false;
    }

    public PacketSelectSpell(FriendlyByteBuf buf) {
        this.targetIndex = buf.readInt();
        this.next = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(targetIndex);
        buf.writeBoolean(next);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            player.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                if (targetIndex >= 0) {
                    magicData.setSelectedSpellIndex(targetIndex);
                } else {
                    magicData.cycleSpell(next);
                }
                ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), player);
            });
        });
        return true;
    }
}
