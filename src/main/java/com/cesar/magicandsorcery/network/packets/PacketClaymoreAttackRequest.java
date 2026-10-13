package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.spell.spells.ClaymoreSpell;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketClaymoreAttackRequest {

    public PacketClaymoreAttackRequest() {
    }

    public PacketClaymoreAttackRequest(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.isAlive()) {
                ClaymoreSpell.handlePlayerSwing(player);
            }
        });
        return true;
    }
}
