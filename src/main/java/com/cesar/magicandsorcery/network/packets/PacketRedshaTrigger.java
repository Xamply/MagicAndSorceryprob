package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientRedshaRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Something crossed a Redsha seal: flash it, and mark the temporary echo copies so clients can trail them.
 */
public class PacketRedshaTrigger {
    private static final int MAX_ECHOES = 4;

    private final int id;
    private final Vec3 hitPos;
    private final int[] echoIds;

    public PacketRedshaTrigger(int id, Vec3 hitPos) {
        this(id, hitPos, new int[0]);
    }

    public PacketRedshaTrigger(int id, Vec3 hitPos, int[] echoIds) {
        this.id = id;
        this.hitPos = hitPos;
        this.echoIds = echoIds;
    }

    public PacketRedshaTrigger(FriendlyByteBuf buf) {
        this.id = buf.readInt();
        this.hitPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int count = Math.min(MAX_ECHOES, buf.readVarInt());
        this.echoIds = new int[count];
        for (int i = 0; i < count; i++) {
            echoIds[i] = buf.readVarInt();
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.id);
        buf.writeDouble(this.hitPos.x);
        buf.writeDouble(this.hitPos.y);
        buf.writeDouble(this.hitPos.z);
        int count = Math.min(MAX_ECHOES, echoIds.length);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(echoIds[i]);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientRedshaRenderer.triggerPortalFlash(id, hitPos, echoIds)));
        return true;
    }
}
