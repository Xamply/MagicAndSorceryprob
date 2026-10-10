package com.cesar.magicandsorcery;

import com.cesar.magicandsorcery.block.ModBlocks;
import com.cesar.magicandsorcery.item.ModCreativeModeTabs;
import com.cesar.magicandsorcery.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MagicAndSorcery.MODID)
public class MagicAndSorcery {
    public static final String MODID = "magic_and_sorcery";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MagicAndSorcery() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register deferred registers
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        com.cesar.magicandsorcery.sound.ModSounds.register(modEventBus);
        com.cesar.magicandsorcery.entity.ModEntities.register(modEventBus);

        // Register lifecycle event listeners
        modEventBus.addListener(this::commonSetup);

        // Register forge event bus
        MinecraftForge.EVENT_BUS.register(this);

        // Register Forge Config
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.cesar.magicandsorcery.config.ModConfigs.SPEC
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Magic and Sorcery initialized successfully!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Magic and Sorcery: Server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Magic and Sorcery: Client setup completed");

            // Staff arm pose has to exist before any player model is drawn (see WandAnimation)
            event.enqueueWork(com.cesar.magicandsorcery.client.render.WandAnimation::registerArmPose);

            // Register Config Screen for Mods menu: Options -> Mods -> Magic and Sorcery -> Config
            net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (mc, screen) -> new com.cesar.magicandsorcery.client.gui.MagicConfigScreen(screen)
                    )
            );
        }

        @SubscribeEvent
        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(com.cesar.magicandsorcery.entity.ModEntities.ITERATUS_MISSILE.get(),
                    com.cesar.magicandsorcery.client.render.IteratusMissileRenderer::new);
            event.registerEntityRenderer(com.cesar.magicandsorcery.entity.ModEntities.BOB.get(),
                    com.cesar.magicandsorcery.client.render.BobRenderer::new);
        }
    }
}
