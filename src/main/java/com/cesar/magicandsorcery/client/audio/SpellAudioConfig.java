package com.cesar.magicandsorcery.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public class SpellAudioConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "magic_and_sorcery_audio.properties";
    private static final String KEY_SPELL_VOLUME = "spell_volume";

    private static double spellVolume = 1.0;
    private static boolean loaded = false;

    public static double getSpellVolume() {
        if (!loaded) {
            load();
        }
        return spellVolume;
    }

    public static void setSpellVolume(double volume) {
        spellVolume = Math.max(0.0, Math.min(1.0, volume));
        save();
    }

    public static synchronized void load() {
        loaded = true;
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            File configFile = configDir.resolve(FILE_NAME).toFile();
            if (!configFile.exists()) {
                spellVolume = 1.0;
                return;
            }

            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            }

            String valStr = props.getProperty(KEY_SPELL_VOLUME);
            if (valStr != null) {
                double parsed = Double.parseDouble(valStr);
                spellVolume = Math.max(0.0, Math.min(1.0, parsed));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load spell audio config, defaulting to 1.0: {}", e.getMessage());
            spellVolume = 1.0;
        }
    }

    public static synchronized void save() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            File configFile = configDir.resolve(FILE_NAME).toFile();

            Properties props = new Properties();
            props.setProperty(KEY_SPELL_VOLUME, String.valueOf(spellVolume));

            try (FileOutputStream out = new FileOutputStream(configFile)) {
                props.store(out, "Magic and Sorcery - Client Audio Configuration");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save spell audio config: {}", e.getMessage());
        }
    }

    public static OptionInstance<Double> createOptionInstance(Minecraft mc) {
        return new OptionInstance<>(
                "options.magic_and_sorcery.spell_volume",
                OptionInstance.noTooltip(),
                (caption, val) -> val == 0.0
                        ? Options.genericValueLabel(caption, CommonComponents.OPTION_OFF)
                        : Component.translatable("options.percent_value", caption, (int) Math.round(val * 100.0)),
                OptionInstance.UnitDouble.INSTANCE,
                getSpellVolume(),
                SpellAudioConfig::setSpellVolume
        );
    }
}
