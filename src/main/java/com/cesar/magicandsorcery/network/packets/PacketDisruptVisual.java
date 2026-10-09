package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.fx.DisruptFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from server to clients to spawn Disrupt visual shockwaves and dispersion effects.
 */
public class PacketDisruptVisual {
    private final Vec3 targetPos;
    private final int targetEntityId;
    private final boolean isProjectile;

    public PacketDisruptVisual(Vec3 targetPos, int targetEntityId, boolean isProjectile) {
        this.targetPos = targetPos;
        this.targetEntityId = targetEntityId;
        this.isProjectile = isProjectile;
    }

    public PacketDisruptVisual(FriendlyByteBuf buf) {
        this.targetPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.targetEntityId = buf.readInt();
        this.isProjectile = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(targetPos.x);
        buf.writeDouble(targetPos.y);
        buf.writeDouble(targetPos.z);
        buf.writeInt(targetEntityId);
        buf.writeBoolean(isProjectile);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                DisruptFx.add(targetEntityId, targetPos, isProjectile);
            });
        });
        return true;
    }
}
