package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientThundajaRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketThundajaStormState {
    private final int casterId;
    private final byte action;
    private final double targetX;
    private final double targetY;
    private final double targetZ;

    public PacketThundajaStormState(int casterId, byte action, Vec3 targetPos) {
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

    public PacketThundajaStormState(FriendlyByteBuf buf) {
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
            Vec3 target = new Vec3(targetX, targetY, targetZ);
            if (action == PacketThundajaChannel.ACTION_START) {
                ClientThundajaRenderer.startStorm(casterId, target);
            } else if (action == PacketThundajaChannel.ACTION_UPDATE) {
                ClientThundajaRenderer.updateStorm(casterId, target);
            } else if (action == PacketThundajaChannel.ACTION_CANCEL) {
                ClientThundajaRenderer.cancelStorm(casterId);
            }
        });
        return true;
    }
}
