package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientDenyRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDenySpawn {
    private final int playerId;
    private final Vec3 direction;
    private final int durationTicks;

    public PacketDenySpawn(int playerId, Vec3 direction, int durationTicks) {
        this.playerId = playerId;
        this.direction = direction;
        this.durationTicks = durationTicks;
    }

    public PacketDenySpawn(FriendlyByteBuf buf) {
        this.playerId = buf.readInt();
        this.direction = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.durationTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.playerId);
        buf.writeDouble(this.direction.x);
        buf.writeDouble(this.direction.y);
        buf.writeDouble(this.direction.z);
        buf.writeInt(this.durationTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientDenyRenderer.spawnBarrier(playerId, direction, durationTicks);
            });
        });
        return true;
    }
}
