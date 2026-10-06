package com.cesar.magicandsorcery.network;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.network.packets.PacketBlizzardTornado;
import com.cesar.magicandsorcery.network.packets.PacketBoltVisual;
import com.cesar.magicandsorcery.network.packets.PacketCastSpell;
import com.cesar.magicandsorcery.network.packets.PacketReorderSpells;
import com.cesar.magicandsorcery.network.packets.PacketSelectSpell;
import com.cesar.magicandsorcery.network.packets.PacketSyncMagicData;
import com.cesar.magicandsorcery.network.packets.PacketThundajaChannel;
import com.cesar.magicandsorcery.network.packets.PacketThundajaImpact;
import com.cesar.magicandsorcery.network.packets.PacketThundajaStormState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1.3";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MagicAndSorcery.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.messageBuilder(PacketSyncMagicData.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketSyncMagicData::new)
                .encoder(PacketSyncMagicData::toBytes)
                .consumerMainThread(PacketSyncMagicData::handle)
                .add();

        INSTANCE.messageBuilder(PacketCastSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketCastSpell::new)
                .encoder(PacketCastSpell::toBytes)
                .consumerMainThread(PacketCastSpell::handle)
                .add();

        INSTANCE.messageBuilder(PacketSelectSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketSelectSpell::new)
                .encoder(PacketSelectSpell::toBytes)
                .consumerMainThread(PacketSelectSpell::handle)
                .add();

        INSTANCE.messageBuilder(PacketReorderSpells.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketReorderSpells::new)
                .encoder(PacketReorderSpells::toBytes)
                .consumerMainThread(PacketReorderSpells::handle)
                .add();

        INSTANCE.messageBuilder(PacketBoltVisual.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketBoltVisual::new)
                .encoder(PacketBoltVisual::toBytes)
                .consumerMainThread(PacketBoltVisual::handle)
                .add();

        INSTANCE.messageBuilder(PacketBlizzardTornado.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketBlizzardTornado::new)
                .encoder(PacketBlizzardTornado::toBytes)
                .consumerMainThread(PacketBlizzardTornado::handle)
                .add();

        INSTANCE.messageBuilder(PacketThundajaChannel.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketThundajaChannel::new)
                .encoder(PacketThundajaChannel::toBytes)
                .consumerMainThread(PacketThundajaChannel::handle)
                .add();

        INSTANCE.messageBuilder(PacketThundajaStormState.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketThundajaStormState::new)
                .encoder(PacketThundajaStormState::toBytes)
                .consumerMainThread(PacketThundajaStormState::handle)
                .add();

        INSTANCE.messageBuilder(PacketThundajaImpact.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketThundajaImpact::new)
                .encoder(PacketThundajaImpact::toBytes)
                .consumerMainThread(PacketThundajaImpact::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketUpdateCreativeConfig.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketUpdateCreativeConfig::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketUpdateCreativeConfig::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketUpdateCreativeConfig::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannelState.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannelState::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannelState::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannelState::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordSweep.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordSweep::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketDivineSwordSweep::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketDivineSwordSweep::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannelState.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannelState::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannelState::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannelState::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordStrike.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordStrike::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketFallingSwordStrike::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketFallingSwordStrike::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketAssignSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketAssignSpell::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketAssignSpell::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketAssignSpell::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketFlashVisual.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketFlashVisual::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketFlashVisual::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketFlashVisual::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketRedshaSpawn.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketRedshaSpawn::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketRedshaSpawn::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketRedshaSpawn::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketRedshaTrigger.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketRedshaTrigger::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketRedshaTrigger::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketRedshaTrigger::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketRedshaRemove.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketRedshaRemove::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketRedshaRemove::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketRedshaRemove::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketRedshaChannel.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketRedshaChannel::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketRedshaChannel::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketRedshaChannel::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketRedshaChannelState.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketRedshaChannelState::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketRedshaChannelState::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketRedshaChannelState::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketDenySpawn.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketDenySpawn::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketDenySpawn::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketDenySpawn::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketDenyReflect.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketDenyReflect::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketDenyReflect::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketDenyReflect::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketIteratusFire.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketIteratusFire::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketIteratusFire::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketIteratusFire::handle)
                .add();

        INSTANCE.messageBuilder(com.cesar.magicandsorcery.network.packets.PacketIteratusEnd.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(com.cesar.magicandsorcery.network.packets.PacketIteratusEnd::new)
                .encoder(com.cesar.magicandsorcery.network.packets.PacketIteratusEnd::toBytes)
                .consumerMainThread(com.cesar.magicandsorcery.network.packets.PacketIteratusEnd::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToNearby(MSG message, ServerLevel level, Vec3 pos, double radius) {
        INSTANCE.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.x, pos.y, pos.z, radius, level.dimension()
        )), message);
    }
}
