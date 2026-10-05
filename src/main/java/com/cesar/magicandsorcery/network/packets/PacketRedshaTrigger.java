package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientRedshaRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRedshaTrigger {
    private final int id;
    private final Vec3 hitPos;

    public PacketRedshaTrigger(int id, Vec3 hitPos) {
        this.id = id;
        this.hitPos = hitPos;
    }

    public PacketRedshaTrigger(FriendlyByteBuf buf) {
        this.id = buf.readInt();
        this.hitPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.id);
        buf.writeDouble(this.hitPos.x);
        buf.writeDouble(this.hitPos.y);
        buf.writeDouble(this.hitPos.z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientRedshaRenderer.triggerPortalFlash(id, hitPos);
            });
        });
        return true;
    }
}
