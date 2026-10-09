package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.ClientChannelTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Broadcast from server to nearby clients to update another player's channeling state.
 */
public class PacketPlayerChannelState {
    public static final byte ACTION_START = 0;
    public static final byte ACTION_CANCEL = 1;
    public static final byte ACTION_FINISH = 2;

    private final int casterId;
    private final ResourceLocation spellId;
    private final byte action;

    public PacketPlayerChannelState(int casterId, ResourceLocation spellId, byte action) {
        this.casterId = casterId;
        this.spellId = spellId;
        this.action = action;
    }

    public PacketPlayerChannelState(FriendlyByteBuf buf) {
        this.casterId = buf.readInt();
        this.action = buf.readByte();
        this.spellId = buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeByte(action);
        buf.writeBoolean(spellId != null);
        if (spellId != null) {
            buf.writeResourceLocation(spellId);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (action == ACTION_START) {
                    ClientChannelTracker.setChanneling(casterId, spellId);
                } else {
                    ClientChannelTracker.clearChanneling(casterId);
                }
            });
        });
        return true;
    }
}
