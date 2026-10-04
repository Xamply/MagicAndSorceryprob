package com.cesar.magicandsorcery.client.audio;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SoundOptionsScreenHandler {

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SoundOptionsScreen)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }

        OptionsList optionsList = null;
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof OptionsList list) {
                optionsList = list;
                break;
            }
        }

        if (optionsList == null) {
            return;
        }

        OptionInstance<Double> spellVolumeOption = SpellAudioConfig.createOptionInstance(mc);
        OptionInstance<Double> voiceOption = mc.options.getSoundSourceOptionInstance(SoundSource.VOICE);

        boolean injected = false;

        // In vanilla Minecraft SoundOptionsScreen:
        // Row 0: Master (big)
        // Rows 1-4: Music, Records, Weather, Blocks, Hostile, Neutral, Players, Ambient (small pairs)
        // Row 5: Voice (single small button on the left, empty slot on the right)
        // Row 6: Sound Device (big)
        // Row 7: Directional Audio, Subtitles (small pair)
        List rawList = optionsList.children();
        if (rawList.size() >= 6) {
            Object row5 = rawList.get(5);
            if (row5 instanceof ContainerEventHandler handler && handler.children().size() == 1) {
                rawList.remove(5);
                optionsList.addSmall(voiceOption, spellVolumeOption);
                Object pairedEntry = rawList.remove(rawList.size() - 1);
                rawList.add(5, pairedEntry);
                injected = true;
            }
        }

        // Fallback: If layout differed, safely append Spells slider as a big row
        if (!injected) {
            optionsList.addBig(spellVolumeOption);
        }
    }
}
