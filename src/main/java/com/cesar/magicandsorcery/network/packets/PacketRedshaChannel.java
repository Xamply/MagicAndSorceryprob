package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRedshaChannel {
    public static final byte ACTION_START = 0;
    public static final byte ACTION_CANCEL = 1;

    private final byte action;

    public PacketRedshaChannel(byte action) {
        this.action = action;
    }

    public PacketRedshaChannel(FriendlyByteBuf buf) {
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

            ModNetwork.sendToNearby(
                    new PacketRedshaChannelState(sender.getId(), action),
                    sender.serverLevel(),
                    sender.position(),
                    64.0
            );
        });
        return true;
    }
}
