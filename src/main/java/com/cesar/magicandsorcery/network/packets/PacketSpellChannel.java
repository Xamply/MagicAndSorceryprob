package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from client to server when a player starts, cancels, or finishes channeling any spell.
 */
public class PacketSpellChannel {
    public static final byte ACTION_START = 0;
    public static final byte ACTION_CANCEL = 1;
    public static final byte ACTION_FINISH = 2;

    private final byte action;
    private final ResourceLocation spellId;
    private final CastingMethod method;

    public PacketSpellChannel(byte action, ResourceLocation spellId, CastingMethod method) {
        this.action = action;
        this.spellId = spellId;
        this.method = method != null ? method : CastingMethod.BARE_HANDS;
    }

    public PacketSpellChannel(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.spellId = buf.readBoolean() ? buf.readResourceLocation() : null;
        this.method = buf.readEnum(CastingMethod.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeBoolean(spellId != null);
        if (spellId != null) {
            buf.writeResourceLocation(spellId);
        }
        buf.writeEnum(method);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !sender.isAlive()) return;

            sender.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                if (action == ACTION_START) {
                    if (spellId != null) {
                        magicData.startChanneling(spellId, method);
                        // Broadcast channel start to nearby clients
                        ModNetwork.sendToNearby(
                                new PacketPlayerChannelState(sender.getId(), spellId, ACTION_START),
                                sender.serverLevel(),
                                sender.position(),
                                64.0
                        );
                    }
                } else if (action == ACTION_CANCEL) {
                    magicData.stopChanneling();
                    ModNetwork.sendToNearby(
                            new PacketPlayerChannelState(sender.getId(), null, ACTION_CANCEL),
                            sender.serverLevel(),
                            sender.position(),
                            64.0
                    );
                } else if (action == ACTION_FINISH) {
                    magicData.stopChanneling();
                    ModNetwork.sendToNearby(
                            new PacketPlayerChannelState(sender.getId(), null, ACTION_FINISH),
                            sender.serverLevel(),
                            sender.position(),
                            64.0
                    );
                }
            });
        });
        return true;
    }
}
