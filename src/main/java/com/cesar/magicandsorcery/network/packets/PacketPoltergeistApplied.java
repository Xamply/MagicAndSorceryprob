package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientPoltergeistRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPoltergeistApplied {
    private final int targetEntityId;
    private final boolean offhand;

    public PacketPoltergeistApplied(int targetEntityId, boolean offhand) {
        this.targetEntityId = targetEntityId;
        this.offhand = offhand;
    }

    public PacketPoltergeistApplied(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readVarInt();
        this.offhand = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        buf.writeBoolean(offhand);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPoltergeistRenderer.handleApplied(targetEntityId, offhand)));
        return true;
    }
}
