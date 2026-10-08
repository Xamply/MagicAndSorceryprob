package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.fx.PraesidiumCureFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPraesidiumVisual {
    private final int targetEntityId;
    private final Vec3 pos;
    private final float height;
    private final float width;

    public PacketPraesidiumVisual(int targetEntityId, Vec3 pos, float height, float width) {
        this.targetEntityId = targetEntityId;
        this.pos = pos;
        this.height = height;
        this.width = width;
    }

    public PacketPraesidiumVisual(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readVarInt();
        this.pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.height = buf.readFloat();
        this.width = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(height);
        buf.writeFloat(width);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                PraesidiumCureFx.add(targetEntityId, pos, height, width)));
        return true;
    }
}
