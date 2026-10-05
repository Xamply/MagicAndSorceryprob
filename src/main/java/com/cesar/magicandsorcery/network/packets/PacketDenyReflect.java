package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientDenyRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDenyReflect {
    private final int playerId;
    private final Vec3 hitPos;

    public PacketDenyReflect(int playerId, Vec3 hitPos) {
        this.playerId = playerId;
        this.hitPos = hitPos;
    }

    public PacketDenyReflect(FriendlyByteBuf buf) {
        this.playerId = buf.readInt();
        this.hitPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.playerId);
        buf.writeDouble(this.hitPos.x);
        buf.writeDouble(this.hitPos.y);
        buf.writeDouble(this.hitPos.z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientDenyRenderer.triggerReflect(playerId, hitPos);
            });
        });
        return true;
    }
}
