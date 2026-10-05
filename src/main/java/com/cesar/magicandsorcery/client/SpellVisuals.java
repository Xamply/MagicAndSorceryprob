package com.cesar.magicandsorcery.client;

import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.spells.BlizzardSpell;
import com.cesar.magicandsorcery.magic.spell.spells.BoltSpell;
import com.cesar.magicandsorcery.magic.spell.spells.DivineSwordSpell;
import com.cesar.magicandsorcery.magic.spell.spells.FlashSpell;
import com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell;
import com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-spell visual identity for the magic interface: every spell has its own color and glyph.
 */
public final class SpellVisuals {

    public record Style(int color, int deepColor, String glyph) {
        public float r() { return ((color >> 16) & 0xFF) / 255.0f; }
        public float g() { return ((color >> 8) & 0xFF) / 255.0f; }
        public float b() { return (color & 0xFF) / 255.0f; }
    }

    private static final Map<ResourceLocation, Style> STYLES = new HashMap<>();
    private static final Style DEFAULT = new Style(0xFF9FB4FF, 0xFF2A3466, "✦");

    static {
        STYLES.put(BoltSpell.ID, new Style(0xFFFFE347, 0xFF5A4A08, "⚡"));
        STYLES.put(BlizzardSpell.ID, new Style(0xFF6FF3FF, 0xFF0C4A5C, "❄"));
        STYLES.put(FlashSpell.ID, new Style(0xFFD07BFF, 0xFF41135C, "✧"));
        STYLES.put(ThundajaSpell.ID, new Style(0xFF5C7CFF, 0xFF141F66, "☈"));
        STYLES.put(DivineSwordSpell.ID, new Style(0xFFFFB347, 0xFF5C3608, "†"));
        STYLES.put(LaPollaCayendoSpell.ID, new Style(0xFFFF4D5E, 0xFF5C0A14, "⚔"));
        STYLES.put(com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell.ID, new Style(0xFFFF3344, 0xFF5C0810, "❂"));
        STYLES.put(com.cesar.magicandsorcery.magic.spell.spells.DenySpell.ID, new Style(0xFF5CE1E6, 0xFF0E4347, "⛨"));
    }

    private SpellVisuals() {
    }

    public static Style of(Spell spell) {
        return spell == null ? DEFAULT : of(spell.getId());
    }

    public static Style of(ResourceLocation id) {
        if (id == null) return DEFAULT;
        Style style = STYLES.get(id);
        if (style != null) return style;
        // Unknown spell (e.g. added later): derive a stable hue from its id
        float hue = (id.hashCode() & 0xFFFF) / 65535.0f;
        int rgb = net.minecraft.util.Mth.hsvToRgb(hue, 0.55f, 1.0f) & 0xFFFFFF;
        int deep = net.minecraft.util.Mth.hsvToRgb(hue, 0.8f, 0.35f) & 0xFFFFFF;
        style = new Style(0xFF000000 | rgb, 0xFF000000 | deep, "✦");
        STYLES.put(id, style);
        return style;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
