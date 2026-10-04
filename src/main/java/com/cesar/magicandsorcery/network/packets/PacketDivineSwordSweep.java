package com.cesar.magicandsorcery.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
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
        this.posX = pos.x;
        this.posY = pos.y;
        this.posZ = pos.z;
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

    public static void handle(PacketDivineSwordSweep msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.cesar.magicandsorcery.client.render.ClientDivineSwordRenderer.triggerSweep(
                    msg.casterId, msg.yaw, msg.pitch, new Vec3(msg.posX, msg.posY, msg.posZ)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
