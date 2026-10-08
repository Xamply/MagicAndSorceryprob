package com.cesar.magicandsorcery.sound;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MagicAndSorcery.MODID);

    public static final RegistryObject<SoundEvent> BOLT = registerSoundEvent("spell.bolt");
    public static final RegistryObject<SoundEvent> BLIZZARD = registerSoundEvent("spell.blizzard");
    public static final RegistryObject<SoundEvent> SWORD_EXPLOSION = registerSoundEvent("spell.sword_explosion");
    public static final RegistryObject<SoundEvent> SHIELD = registerSoundEvent("spell.shield");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MagicAndSorcery.MODID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
