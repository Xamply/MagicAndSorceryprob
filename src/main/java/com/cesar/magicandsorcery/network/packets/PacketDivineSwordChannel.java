package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketDivineSwordChannel {
    public static final byte ACTION_START = 0;
    public static final byte ACTION_CANCEL = 1;

    private final int casterId;
    private final byte action;

    public PacketDivineSwordChannel(byte action) {
        this(-1, action);
    }

    public PacketDivineSwordChannel(int casterId, byte action) {
        this.casterId = casterId;
        this.action = action;
    }

    public PacketDivineSwordChannel(FriendlyByteBuf buf) {
        this.casterId = buf.readInt();
        this.action = buf.readByte();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.casterId);
        buf.writeByte(this.action);
    }

    public static void handle(PacketDivineSwordChannel msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                // Client to Server: rebroadcast with sender's entity ID to other tracking clients
                PacketDivineSwordChannel broadcastPacket = new PacketDivineSwordChannel(sender.getId(), msg.action);
                ModNetwork.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> sender), broadcastPacket);
            } else {
                // Server to Client: notify local renderer
                com.cesar.magicandsorcery.client.render.ClientDivineSwordRenderer.onChannelUpdate(msg.casterId, msg.action);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
