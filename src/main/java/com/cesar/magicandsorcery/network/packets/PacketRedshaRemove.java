package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientRedshaRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRedshaRemove {
    private final int id;

    public PacketRedshaRemove(int id) {
        this.id = id;
    }

    public PacketRedshaRemove(FriendlyByteBuf buf) {
        this.id = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.id);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientRedshaRenderer.removePortal(id);
            });
        });
        return true;
    }
}
