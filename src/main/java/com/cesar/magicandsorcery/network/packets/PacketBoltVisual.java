package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.fx.BoltFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketBoltVisual {
    private static final int MAX_CHAIN = 8;

    private final Vec3 start;
    private final Vec3 end;
    private final long seed;
    private final boolean hitEntity;
    private final List<Vec3> chain;

    public PacketBoltVisual(Vec3 start, Vec3 end, long seed, boolean hitEntity, List<Vec3> chain) {
        this.start = start;
        this.end = end;
        this.seed = seed;
        this.hitEntity = hitEntity;
        this.chain = chain;
    }

    public PacketBoltVisual(FriendlyByteBuf buf) {
        this.start = readVec(buf);
        this.end = readVec(buf);
        this.seed = buf.readLong();
        this.hitEntity = buf.readBoolean();
        int count = Math.min(MAX_CHAIN, buf.readVarInt());
        this.chain = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chain.add(readVec(buf));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        writeVec(buf, start);
        writeVec(buf, end);
        buf.writeLong(seed);
        buf.writeBoolean(hitEntity);
        int count = Math.min(MAX_CHAIN, chain.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writeVec(buf, chain.get(i));
        }
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                BoltFx.add(start, end, seed, hitEntity, chain)));
        return true;
    }
}
