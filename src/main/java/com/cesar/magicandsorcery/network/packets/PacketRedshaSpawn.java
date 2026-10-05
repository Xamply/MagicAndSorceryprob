package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientRedshaRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRedshaSpawn {
    private final int id;
    private final Vec3 center;
    private final Vec3 forward;
    private final Vec3 right;
    private final Vec3 up;
    private final int durationTicks;

    public PacketRedshaSpawn(int id, Vec3 center, Vec3 forward, Vec3 right, Vec3 up, int durationTicks) {
        this.id = id;
        this.center = center;
        this.forward = forward;
        this.right = right;
        this.up = up;
        this.durationTicks = durationTicks;
    }

    public PacketRedshaSpawn(FriendlyByteBuf buf) {
        this.id = buf.readInt();
        this.center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.forward = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.right = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.up = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.durationTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.id);
        buf.writeDouble(this.center.x);
        buf.writeDouble(this.center.y);
        buf.writeDouble(this.center.z);
        buf.writeDouble(this.forward.x);
        buf.writeDouble(this.forward.y);
        buf.writeDouble(this.forward.z);
        buf.writeDouble(this.right.x);
        buf.writeDouble(this.right.y);
        buf.writeDouble(this.right.z);
        buf.writeDouble(this.up.x);
        buf.writeDouble(this.up.y);
        buf.writeDouble(this.up.z);
        buf.writeInt(this.durationTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientRedshaRenderer.spawnPortal(id, center, forward, right, up, durationTicks);
            });
        });
        return true;
    }
}
