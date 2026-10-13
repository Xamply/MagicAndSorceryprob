package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClaymoreFirstPersonRenderer;
import com.cesar.magicandsorcery.client.render.ClientClaymoreWorldRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketClaymoreSwingVisual {
    private final int casterId;
    private final int stage; // 1, 2, or 3
    private final float yaw;
    private final float pitch;
    private final Vec3 pos;
    private final int durationTicks;

    public PacketClaymoreSwingVisual(int casterId, int stage, float yaw, float pitch, Vec3 pos, int durationTicks) {
        this.casterId = casterId;
        this.stage = stage;
        this.yaw = yaw;
        this.pitch = pitch;
        this.pos = pos;
        this.durationTicks = durationTicks;
    }

    public PacketClaymoreSwingVisual(FriendlyByteBuf buf) {
        this.casterId = buf.readVarInt();
        this.stage = buf.readVarInt();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
        this.pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.durationTicks = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(casterId);
        buf.writeVarInt(stage);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeVarInt(durationTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == casterId) {
                ClaymoreFirstPersonRenderer.triggerSwingAnimation(stage, durationTicks);
            }
            ClientClaymoreWorldRenderer.triggerSlash(casterId, stage, yaw, pitch, pos, durationTicks);
        }));
        return true;
    }
}
