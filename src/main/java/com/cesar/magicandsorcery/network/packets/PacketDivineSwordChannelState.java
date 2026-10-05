package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientDivineSwordRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDivineSwordChannelState {
    private final int casterId;
    private final byte action;

    public PacketDivineSwordChannelState(int casterId, byte action) {
        this.casterId = casterId;
        this.action = action;
    }

    public PacketDivineSwordChannelState(FriendlyByteBuf buf) {
        this.casterId = buf.readInt();
        this.action = buf.readByte();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.casterId);
        buf.writeByte(this.action);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientDivineSwordRenderer.onChannelUpdate(casterId, action);
            });
        });
        return true;
    }
}
