package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientLightningHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.function.Supplier;

public class PacketBoltVisual {
    private final double startX;
    private final double startY;
    private final double startZ;
    private final double endX;
    private final double endY;
    private final double endZ;
    private final long seed;

    public PacketBoltVisual(Vec3 start, Vec3 end, long seed) {
        this.startX = start.x;
        this.startY = start.y;
        this.startZ = start.z;
        this.endX = end.x;
        this.endY = end.y;
        this.endZ = end.z;
        this.seed = seed;
    }

    public PacketBoltVisual(FriendlyByteBuf buf) {
        this.startX = buf.readDouble();
        this.startY = buf.readDouble();
        this.startZ = buf.readDouble();
        this.endX = buf.readDouble();
        this.endY = buf.readDouble();
        this.endZ = buf.readDouble();
        this.seed = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(startX);
        buf.writeDouble(startY);
        buf.writeDouble(startZ);
        buf.writeDouble(endX);
        buf.writeDouble(endY);
        buf.writeDouble(endZ);
        buf.writeLong(seed);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientLightningHandler.addBolt(new Vec3(startX, startY, startZ), new Vec3(endX, endY, endZ), seed);
            });
        });
        return true;
    }
}
