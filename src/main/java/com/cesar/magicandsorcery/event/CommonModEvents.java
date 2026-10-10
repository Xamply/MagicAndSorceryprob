package com.cesar.magicandsorcery.event;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicData;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonModEvents {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            ModSpells.init();
        });
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerMagicData.class);
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(com.cesar.magicandsorcery.entity.ModEntities.BOB.get(),
                com.cesar.magicandsorcery.entity.BobEntity.createAttributes().build());
    }
}
