package com.cesar.magicandsorcery.client.audio;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SpellSoundHandler {

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }

        // Avoid re-wrapping if already wrapped
        if (sound instanceof ScaledSoundInstance) {
            return;
        }

        if (isSpellSound(sound, event.getName())) {
            double spellVolume = SpellAudioConfig.getSpellVolume();
            if (spellVolume <= 0.001) {
                // Mute sound completely
                event.setSound(null);
            } else if (spellVolume < 0.999) {
                // Scale volume by the spell volume multiplier
                event.setSound(ScaledSoundInstance.wrap(sound, (float) spellVolume));
            }
        }
    }

    private static boolean isSpellSound(SoundInstance sound, String name) {
        ResourceLocation loc = sound.getLocation();
        if (loc != null && MagicAndSorcery.MODID.equals(loc.getNamespace())) {
            return true;
        }
        if (name != null) {
            if (name.startsWith(MagicAndSorcery.MODID + ":") || name.startsWith("spell.")) {
                return true;
            }
        }
        return false;
    }
}
