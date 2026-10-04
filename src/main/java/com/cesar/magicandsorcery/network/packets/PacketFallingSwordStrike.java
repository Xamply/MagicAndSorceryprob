package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientFallingSwordRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketFallingSwordStrike {
    private final int casterId;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final double obstacleY;

    public PacketFallingSwordStrike(int casterId, Vec3 targetPos, double obstacleY) {
        this.casterId = casterId;
        this.targetX = targetPos.x;
        this.targetY = targetPos.y;
        this.targetZ = targetPos.z;
        this.obstacleY = obstacleY;
    }

    public PacketFallingSwordStrike(FriendlyByteBuf buf) {
        this.casterId = buf.readInt();
        this.targetX = buf.readDouble();
        this.targetY = buf.readDouble();
        this.targetZ = buf.readDouble();
        this.obstacleY = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeDouble(obstacleY);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientFallingSwordRenderer.triggerStrike(casterId, new Vec3(targetX, targetY, targetZ), obstacleY);
            });
        });
        return true;
    }
}
