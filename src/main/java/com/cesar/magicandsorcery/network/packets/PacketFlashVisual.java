package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.fx.FlashFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells nearby clients to play the Flash blink: departure, light streak and arrival burst.
 */
public class PacketFlashVisual {
    private final int casterId;
    private final Vec3 from;
    private final Vec3 to;
    private final float height;
    private final long seed;

    public PacketFlashVisual(int casterId, Vec3 from, Vec3 to, float height, long seed) {
        this.casterId = casterId;
        this.from = from;
        this.to = to;
        this.height = height;
        this.seed = seed;
    }

    public PacketFlashVisual(FriendlyByteBuf buf) {
        this.casterId = buf.readVarInt();
        this.from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.to = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.height = buf.readFloat();
        this.seed = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(casterId);
        buf.writeDouble(from.x);
        buf.writeDouble(from.y);
        buf.writeDouble(from.z);
        buf.writeDouble(to.x);
        buf.writeDouble(to.y);
        buf.writeDouble(to.z);
        buf.writeFloat(height);
        buf.writeLong(seed);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                FlashFx.add(casterId, from, to, height, seed)));
        return true;
    }
}
