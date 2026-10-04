package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientFallingSwordRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketFallingSwordChannelState {
    private final int casterId;
    private final byte action;
    private final double targetX;
    private final double targetY;
    private final double targetZ;

    public PacketFallingSwordChannelState(int casterId, byte action, Vec3 targetPos) {
        this.casterId = casterId;
        this.action = action;
        if (targetPos != null) {
            this.targetX = targetPos.x;
            this.targetY = targetPos.y;
            this.targetZ = targetPos.z;
        } else {
            this.targetX = 0;
            this.targetY = 0;
            this.targetZ = 0;
        }
    }

    public PacketFallingSwordChannelState(FriendlyByteBuf buf) {
        this.casterId = buf.readInt();
        this.action = buf.readByte();
        this.targetX = buf.readDouble();
        this.targetY = buf.readDouble();
        this.targetZ = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeByte(action);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Vec3 target = new Vec3(targetX, targetY, targetZ);
                if (action == PacketFallingSwordChannel.ACTION_START) {
                    ClientFallingSwordRenderer.startChannel(casterId, target);
                } else if (action == PacketFallingSwordChannel.ACTION_UPDATE) {
                    ClientFallingSwordRenderer.updateChannel(casterId, target);
                } else if (action == PacketFallingSwordChannel.ACTION_CANCEL) {
                    ClientFallingSwordRenderer.cancelChannel(casterId);
                }
            });
        });
        return true;
    }
}
