package com.cesar.magicandsorcery.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfigs {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue INFINITE_MANA_IN_CREATIVE;
    public static final ForgeConfigSpec.BooleanValue NO_COOLDOWN_IN_CREATIVE;

    static {
        BUILDER.push("creative_mode_settings");

        INFINITE_MANA_IN_CREATIVE = BUILDER
                .comment("Si está activado, los hechizos no consumen maná mientras el jugador esté en Modo Creativo.")
                .translation("config.magic_and_sorcery.infinite_mana_in_creative")
                .define("infiniteManaInCreative", false);

        NO_COOLDOWN_IN_CREATIVE = BUILDER
                .comment("Si está activado, los hechizos no tienen enfriamiento (cooldown) mientras el jugador esté en Modo Creativo.")
                .translation("config.magic_and_sorcery.no_cooldown_in_creative")
                .define("noCooldownInCreative", false);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
