package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientTornadoHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketBlizzardTornado {
    private final double x;
    private final double y;
    private final double z;
    private final int durationTicks;
    private final long seed;

    public PacketBlizzardTornado(Vec3 center, int durationTicks, long seed) {
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        this.durationTicks = durationTicks;
        this.seed = seed;
    }

    public PacketBlizzardTornado(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.durationTicks = buf.readVarInt();
        this.seed = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(durationTicks);
        buf.writeLong(seed);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ClientTornadoHandler.addTornado(new Vec3(x, y, z), durationTicks, seed);
        });
        return true;
    }
}
