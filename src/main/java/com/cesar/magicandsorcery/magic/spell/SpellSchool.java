package com.cesar.magicandsorcery.magic.spell;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum SpellSchool {
    ICE("ice", "school.magic_and_sorcery.ice", ChatFormatting.AQUA),
    LIGHTNING("lightning", "school.magic_and_sorcery.lightning", ChatFormatting.GOLD),
    TELEPORTATION("teleportation", "school.magic_and_sorcery.teleportation", ChatFormatting.LIGHT_PURPLE),
    HOLY("holy", "school.magic_and_sorcery.holy", ChatFormatting.YELLOW),
    PHYSICAL("physical", "school.magic_and_sorcery.physical", ChatFormatting.RED),
    ARCANE("arcane", "school.magic_and_sorcery.arcane", ChatFormatting.DARK_RED),
    INTERFERENCE("interference", "school.magic_and_sorcery.interference", ChatFormatting.DARK_PURPLE);

    private final String id;
    private final String translationKey;
    private final ChatFormatting color;

    SpellSchool(String id, String translationKey, ChatFormatting color) {
        this.id = id;
        this.translationKey = translationKey;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey).withStyle(color);
    }

    public ChatFormatting getColor() {
        return color;
    }
}
