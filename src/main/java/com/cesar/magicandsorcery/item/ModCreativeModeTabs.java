package com.cesar.magicandsorcery.item;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MagicAndSorcery.MODID);

    public static final RegistryObject<CreativeModeTab> MAGIC_TAB = CREATIVE_MODE_TABS.register("magic_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.magic_and_sorcery_tab"))
                    .icon(() -> new ItemStack(ModItems.MAGIC_CRYSTAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MAGIC_CRYSTAL.get());
                        output.accept(ModItems.MAGIC_RING.get());
                        output.accept(ModItems.SPELL_TOME.get());
                        output.accept(ModItems.MAGIC_WAND.get());
                        output.accept(ModItems.MANA_POTION.get());
                        output.accept(ModBlocks.SORCERY_STONE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
