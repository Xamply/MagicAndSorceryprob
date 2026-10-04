package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.config.ModConfigs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateCreativeConfig {
    private final boolean infiniteMana;
    private final boolean noCooldown;

    public PacketUpdateCreativeConfig(boolean infiniteMana, boolean noCooldown) {
        this.infiniteMana = infiniteMana;
        this.noCooldown = noCooldown;
    }

    public PacketUpdateCreativeConfig(FriendlyByteBuf buf) {
        this.infiniteMana = buf.readBoolean();
        this.noCooldown = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(infiniteMana);
        buf.writeBoolean(noCooldown);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Apply when player is in creative mode or has admin permissions
            if (player.isCreative() || player.hasPermissions(2)) {
                ModConfigs.INFINITE_MANA_IN_CREATIVE.set(infiniteMana);
                ModConfigs.NO_COOLDOWN_IN_CREATIVE.set(noCooldown);
                ModConfigs.SPEC.save();
            }
        });
        return true;
    }
}
