package com.cesar.magicandsorcery.event;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicData;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.magic.spell.spells.BlizzardSpell;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketSyncMagicData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID)
public class PlayerMagicEvents {
    private static final ResourceLocation CAPABILITY_KEY = new ResourceLocation(MagicAndSorcery.MODID, "magic_data");

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(PlayerMagicProvider.PLAYER_MAGIC).isPresent()) {
                event.addCapability(CAPABILITY_KEY, new PlayerMagicProvider());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(oldStore -> {
            event.getEntity().getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(newStore -> {
                newStore.copyFrom(oldStore);
            });
        });
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), serverPlayer);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), serverPlayer);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), serverPlayer);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END) {
            if (event.player instanceof ServerPlayer serverPlayer) {
                serverPlayer.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                    magicData.tickServer();

                    // Sync periodic updates (e.g. mana regen) every 10 ticks = 0.5s
                    if (serverPlayer.tickCount % 10 == 0) {
                        ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), serverPlayer);
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BlizzardSpell.tickActiveZones();
            com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell.tickServer(event.getServer());
        }
    }
}
