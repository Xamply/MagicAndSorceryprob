package com.cesar.magicandsorcery.magic.spell;

import net.minecraft.network.chat.Component;

public enum SpellType {
    SINGLE_TARGET("single_target", "spelltype.magic_and_sorcery.single_target"),
    AREA("area", "spelltype.magic_and_sorcery.area"),
    MOBILITY("mobility", "spelltype.magic_and_sorcery.mobility"),
    DESTRUCTION("destruction", "spelltype.magic_and_sorcery.destruction");

    private final String id;
    private final String translationKey;

    SpellType(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }
}
