package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketReorderSpells {
    private final int fromSlot;
    private final int toSlot;

    public PacketReorderSpells(int fromSlot, int toSlot) {
        this.fromSlot = fromSlot;
        this.toSlot = toSlot;
    }

    public PacketReorderSpells(FriendlyByteBuf buf) {
        this.fromSlot = buf.readInt();
        this.toSlot = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(fromSlot);
        buf.writeInt(toSlot);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            player.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                boolean modified = magicData.moveOrSwapSpell(fromSlot, toSlot);
                if (modified) {
                    ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), player);
                }
            });
        });
        return true;
    }
}
