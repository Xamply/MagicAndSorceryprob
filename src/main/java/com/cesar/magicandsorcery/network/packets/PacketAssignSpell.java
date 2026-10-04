package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class PacketAssignSpell {
    private final int targetSlot;
    @Nullable
    private final ResourceLocation spellId;

    public PacketAssignSpell(int targetSlot, @Nullable ResourceLocation spellId) {
        this.targetSlot = targetSlot;
        this.spellId = spellId;
    }

    public PacketAssignSpell(FriendlyByteBuf buf) {
        this.targetSlot = buf.readInt();
        boolean hasSpell = buf.readBoolean();
        this.spellId = hasSpell ? buf.readResourceLocation() : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(targetSlot);
        buf.writeBoolean(spellId != null);
        if (spellId != null) {
            buf.writeResourceLocation(spellId);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            player.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                boolean modified = magicData.assignSpell(targetSlot, spellId);
                if (modified) {
                    ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), player);
                }
            });
        });
        return true;
    }
}
