package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientDivineSwordRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDivineSwordSweep {
    private final int casterId;
    private final float yaw;
    private final float pitch;
    private final double posX;
    private final double posY;
    private final double posZ;

    public PacketDivineSwordSweep(int casterId, float yaw, float pitch, Vec3 pos) {
        this.casterId = casterId;
        this.yaw = yaw;
        this.pitch = pitch;
        if (pos != null) {
            this.posX = pos.x;
            this.posY = pos.y;
            this.posZ = pos.z;
        } else {
            this.posX = 0;
            this.posY = 0;
            this.posZ = 0;
        }
    }

    public PacketDivineSwordSweep(FriendlyByteBuf buf) {
        this.casterId = buf.readInt();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
        this.posX = buf.readDouble();
        this.posY = buf.readDouble();
        this.posZ = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.casterId);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
        buf.writeDouble(this.posX);
        buf.writeDouble(this.posY);
        buf.writeDouble(this.posZ);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientDivineSwordRenderer.triggerSweep(
                        casterId, yaw, pitch, new Vec3(posX, posY, posZ)
                );
            });
        });
        return true;
    }
}
