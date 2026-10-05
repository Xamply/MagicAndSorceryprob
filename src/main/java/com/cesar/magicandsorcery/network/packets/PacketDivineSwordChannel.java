package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDivineSwordChannel {
    public static final byte ACTION_START = 0;
    public static final byte ACTION_CANCEL = 1;

    private final byte action;

    public PacketDivineSwordChannel(byte action) {
        this.action = action;
    }

    public PacketDivineSwordChannel(FriendlyByteBuf buf) {
        this.action = buf.readByte();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByte(this.action);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // Broadcast channel state from server to other nearby clients using PLAY_TO_CLIENT packet
            ModNetwork.sendToNearby(
                    new PacketDivineSwordChannelState(sender.getId(), action),
                    sender.serverLevel(),
                    sender.position(),
                    64.0
            );
        });
        return true;
    }
}
