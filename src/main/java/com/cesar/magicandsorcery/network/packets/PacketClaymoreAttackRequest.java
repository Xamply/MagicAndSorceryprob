package com.cesar.magicandsorcery.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketClaymoreAttackRequest {

    public PacketClaymoreAttackRequest() {
    }

    public PacketClaymoreAttackRequest(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            // No-op: standard weapon attacks are handled natively
        });
        return true;
    }
}
