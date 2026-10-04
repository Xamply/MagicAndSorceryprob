package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientThundajaRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketThundajaImpact {
    private final int casterId;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final long seed;

    public PacketThundajaImpact(int casterId, Vec3 target, long seed) {
        this.casterId = casterId;
        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        this.seed = seed;
    }

    public PacketThundajaImpact(Vec3 target, long seed) {
        this(-1, target, seed);
    }

    public PacketThundajaImpact(FriendlyByteBuf buf) {
        this.casterId = buf.readInt();
        this.targetX = buf.readDouble();
        this.targetY = buf.readDouble();
        this.targetZ = buf.readDouble();
        this.seed = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeLong(seed);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ClientThundajaRenderer.triggerImpact(casterId, new Vec3(targetX, targetY, targetZ), seed);
        });
        return true;
    }
}
