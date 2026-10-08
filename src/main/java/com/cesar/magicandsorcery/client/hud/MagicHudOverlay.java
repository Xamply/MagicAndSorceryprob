package com.cesar.magicandsorcery.client.hud;

import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.client.SpellVisuals;
import com.cesar.magicandsorcery.config.ModConfigs;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * Arcane HUD: active-spell medallion with mana bar, a charging ring around the crosshair,
 * and the radial spell menu (library, rune wheel and spell card).
 */
public class MagicHudOverlay {

    private static final double TAU = Math.PI * 2.0;
    private static final float[] SLOT_HOVER = new float[RadialMenuRenderer.TOTAL_SLOTS];
    private static long lastFrameMs = 0L;


    public static final IGuiOverlay HUD_MAGIC = (gui, g, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.options.hideGui) {
            return;
        }

        Font font = mc.font;
        float time = mc.player.tickCount + partialTick;

        // Flash: violet burst of light across the screen right after blinking
        float flash = com.cesar.magicandsorcery.client.fx.FlashFx.screenFlash(partialTick);
        if (flash > 0.01f) {
            MagicGui.gradientRect(g, 0, 0, screenWidth, screenHeight,
                    MagicGui.alpha(0xFFC27BFF, 0.45f * flash), MagicGui.alpha(0xFF6A2CFF, 0.3f * flash), true);
            g.flush();
        }
        // Bolt: golden flash when lightning lands close to the viewer
        float boltFlash = com.cesar.magicandsorcery.client.fx.BoltFx.screenFlash(partialTick);
        if (boltFlash > 0.01f) {
            MagicGui.gradientRect(g, 0, 0, screenWidth, screenHeight,
                    MagicGui.alpha(0xFFFFF2C0, 0.5f * boltFlash), MagicGui.alpha(0xFFFFB347, 0.3f * boltFlash), true);
            g.flush();
        }

        if (ClientMagicData.isRadialMenuOpen()) {
            renderRadialMenu(g, mc, font, screenWidth, screenHeight, time);
        } else if (ClientMagicData.isChanneling()) {
            renderChannelRing(g, font, screenWidth, screenHeight, time);
        }

        renderSpellHud(g, mc, font, screenWidth, screenHeight, time);
        g.flush();
    };

    // =========================================================================
    // ACTIVE SPELL HUD (bottom-left)
    // =========================================================================

    private static void renderSpellHud(GuiGraphics g, Minecraft mc, Font font, int screenWidth, int screenHeight, float time) {
        Spell spell = ClientMagicData.getSelectedSpell();
        CastingMethod method = ClientMagicData.getCurrentCastingMethod();
        SpellVisuals.Style style = SpellVisuals.of(spell);
        int accent = spell != null ? style.color() : MagicGui.FRAME;

        boolean isCreative = mc.player != null && mc.player.isCreative();
        boolean bypassCooldown = isCreative && ModConfigs.NO_COOLDOWN_IN_CREATIVE.get();
        boolean bypassMana = isCreative && ModConfigs.INFINITE_MANA_IN_CREATIVE.get();

        // Stay clear of the hotbar and of the offhand slot drawn to its left
        int hotbarLeft = screenWidth / 2 - 91;
        boolean offhandOnLeft = mc.player != null && !mc.player.getOffhandItem().isEmpty()
                && mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        int freeRight = hotbarLeft - (offhandOnLeft ? 29 : 0) - 8;
        int panelH = 42;
        int x = 8;
        int panelW = Math.min(156, freeRight - x);
        int y = screenHeight - panelH - 8;
        if (panelW < 112) {
            // Not enough room beside the hotbar: sit above the hotbar, hearts and armor instead
            panelW = 132;
            y = screenHeight - panelH - 56;
        }

        MagicGui.panel(g, x, y, panelW, panelH, accent, time);

        // --- Medallion ---
        float mx = x + 20;
        float my = y + panelH / 2.0f;
        float medR = 13.0f;
        float breathe = MagicGui.pulse(time, 0.1f);
        MagicGui.glowCircle(g, mx, my, medR + 9.0f, MagicGui.alpha(accent, 0.30f + 0.20f * breathe), 32);
        MagicGui.radialGradient(g, mx, my, medR, MagicGui.mix(style.deepColor(), accent, 0.45f), style.deepColor(), 32, false);
        MagicGui.ring(g, mx, my, medR - 1.2f, medR, accent, 40);
        MagicGui.dottedRing(g, mx, my, medR + 3.5f, 8, 0.9f, time * 0.04f, MagicGui.alpha(accent, 0.85f));

        int cooldownTicks = (spell == null || bypassCooldown) ? 0 : ClientMagicData.getSpellCooldown(spell.getId());
        if (cooldownTicks > 0) {
            float total = Math.max(1, spell.calculateFinalCooldown(method));
            float frac = Math.min(1.0f, cooldownTicks / total);
            MagicGui.arc(g, mx, my, 0, medR - 1.2f, -Math.PI / 2.0, -Math.PI / 2.0 + TAU * frac,
                    0xC0000000, 0xC0000000, 32, false);
        }

        if (ClientMagicData.isChanneling()) {
            float progress = ClientMagicData.isReadyToCast() ? 1.0f : ClientMagicData.getChannelProgress();
            MagicGui.glowArc(g, mx, my, medR + 0.5f, 2.0f, -Math.PI / 2.0, -Math.PI / 2.0 + TAU * progress,
                    MagicGui.alpha(MagicGui.mix(accent, 0xFFFFFFFF, 0.3f), 0.95f), 40);
        }

        // --- Mana bar geometry ---
        int tx = x + 40;
        int textW = panelW - 40 - 7;
        float barX = tx;
        float barY = y + 29;
        float barW = textW;
        float barH = 7;
        float mana = ClientMagicData.getMana();
        float maxMana = Math.max(1.0f, ClientMagicData.getMaxMana());
        float manaPct = bypassMana ? 1.0f : Math.max(0.0f, Math.min(1.0f, mana / maxMana));
        float fillW = barW * manaPct;

        MagicGui.rect(g, barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, MagicGui.alpha(MagicGui.FRAME, 0.7f));
        MagicGui.rect(g, barX, barY, barX + barW, barY + barH, 0xFF060B1C);
        if (fillW > 0.5f) {
            MagicGui.horizontalGradient(g, barX, barY, barX + fillW, barY + barH, 0xFF2440D8, 0xFF4FE3FF, false);
            MagicGui.gradientRect(g, barX, barY, barX + fillW, barY + barH / 2.0f, 0x40FFFFFF, 0x10FFFFFF, true);
            // Shimmer sweeping across the filled mana
            float sweep = (time * 2.2f) % (barW + 24.0f) - 12.0f;
            float s1 = Math.max(barX, barX + sweep - 8.0f);
            float s2 = Math.min(barX + fillW, barX + sweep + 8.0f);
            float sm = Math.max(s1, Math.min(s2, barX + sweep));
            if (s2 > s1) {
                MagicGui.horizontalGradient(g, s1, barY, sm, barY + barH, 0x00FFFFFF, 0x70FFFFFF, true);
                MagicGui.horizontalGradient(g, sm, barY, s2, barY + barH, 0x70FFFFFF, 0x00FFFFFF, true);
            }
            MagicGui.glowCircle(g, barX + fillW, barY + barH / 2.0f, 6.0f, 0x904FE3FF, 12);
        }
        g.flush();

        // --- Text pass ---
        if (cooldownTicks > 0) {
            MagicGui.centeredText(g, font, String.format("%.1f", cooldownTicks / 20.0f), mx, my - 3.0f, 0.75f, MagicGui.COOLDOWN, true);
        } else {
            MagicGui.glyph(g, font, style.glyph(), mx, my, 1.6f, MagicGui.mix(accent, 0xFFFFFFFF, 0.35f));
        }

        if (spell != null) {
            String name = MagicGui.fit(font, spell.getName().getString(), textW);
            g.drawString(font, name, tx, y + 6, accent, true);

            int sx = tx;
            int sy = y + 17;
            int maxX = tx + textW;
            String cost = bypassMana ? "∞ MP" : String.format("%.0f MP", spell.calculateFinalManaCost(method));
            sx = drawInline(g, font, cost, sx, sy, maxX, MagicGui.MANA);
            if (spell.getBaseDamage() > 0) {
                sx = drawInline(g, font, " • ", sx, sy, maxX, MagicGui.TEXT_DIM);
                sx = drawInline(g, font, String.format("%.0f DMG", spell.calculateFinalDamage(method)), sx, sy, maxX, MagicGui.DAMAGE);
            } else if (spell.getBaseHealing() > 0) {
                sx = drawInline(g, font, " • ", sx, sy, maxX, MagicGui.TEXT_DIM);
                sx = drawInline(g, font, String.format("%.1f HP", spell.calculateFinalHealing(method)), sx, sy, maxX, 0xFFFF7A96);
            }
            if (cooldownTicks > 0) {
                sx = drawInline(g, font, " • ", sx, sy, maxX, MagicGui.TEXT_DIM);
                drawInline(g, font, String.format("⟳%.1fs", cooldownTicks / 20.0f), sx, sy, maxX, MagicGui.COOLDOWN);
            }
        } else {
            g.drawString(font, MagicGui.fit(font, Component.translatable("hud.magic_and_sorcery.no_spell").getString(), textW),
                    tx, y + 6, MagicGui.TEXT_DIM, true);
        }

        String manaText = bypassMana ? "∞" : String.format("%.0f / %.0f", mana, maxMana);
        MagicGui.centeredText(g, font, manaText, barX + barW / 2.0f, barY + 0.5f, 0.75f, 0xFFFFFFFF, true);
    }

    private static int drawInline(GuiGraphics g, Font font, String text, int x, int y, int maxX, int color) {
        if (x >= maxX) return x;
        if (x + font.width(text) > maxX) {
            text = MagicGui.fit(font, text, maxX - x);
        }
        g.drawString(font, text, x, y, color, true);
        return x + font.width(text);
    }

    // =========================================================================
    // CHANNELING RING (around the crosshair)
    // =========================================================================

    /**
     * Minimal charging indicator around the crosshair: a thin progress ring with a bright head,
     * and tiny faint runes that reveal themselves as the spell charges. It stays perfectly still.
     */
    private static void renderChannelRing(GuiGraphics g, Font font, int screenWidth, int screenHeight, float time) {
        Spell spell = ClientMagicData.getChannelingSpell();
        if (spell == null) return;

        int accent = SpellVisuals.of(spell).color();
        boolean ready = ClientMagicData.isReadyToCast();
        float progress = ready ? 1.0f : ClientMagicData.getChannelProgress();
        float cx = screenWidth / 2.0f;
        float cy = screenHeight / 2.0f;
        float r = 7.5f;

        MagicGui.ring(g, cx, cy, r - 0.5f, r + 0.5f, 0x40000000, 40);
        if (!ready) {
            double start = -Math.PI / 2.0;
            double end = start + TAU * progress;
            MagicGui.glowArc(g, cx, cy, r, 1.2f, start, end, MagicGui.alpha(accent, 0.45f), 40);
            MagicGui.arc(g, cx, cy, r - 0.6f, r + 0.6f, start, end, accent, accent, 40, false);
            MagicGui.circle(g, cx + r * (float) Math.cos(end), cy + r * (float) Math.sin(end), 1.1f, 0xFFFFFFFF, 8);
        } else {
            float p = MagicGui.pulse(time, 0.25f);
            MagicGui.glowRing(g, cx, cy, r, 1.4f + 0.8f * p, MagicGui.alpha(accent, 0.55f), 40);
            MagicGui.ring(g, cx, cy, r - 0.6f, r + 0.6f, MagicGui.mix(accent, 0xFFFFFFFF, 0.3f), 40);
        }
        g.flush();

        float runeAlpha = ready ? 0.5f : 0.1f + 0.35f * progress;
        MagicGui.runeRing(g, font, cx, cy, r + 5.0f, 12, time * 0.02f, MagicGui.alpha(accent, runeAlpha), 0.33f, 3);

        // Support spell alternate self-cast hint
        if (spell.allowsSelfCast()) {
            boolean selfCast = ClientMagicData.isSelfCastSelected();
            String hintText;
            int textCol;
            int borderCol;

            if (selfCast) {
                hintText = Component.translatable("hud.magic_and_sorcery.self_cast_active").getString();
                textCol = 0xFFFFF2A8;
                borderCol = 0xFFFFD700;
            } else {
                if (ClientMagicData.isChanneledViaKey()) {
                    hintText = Component.translatable("hud.magic_and_sorcery.self_cast_prompt_right_click").getString();
                } else {
                    String keyName = com.cesar.magicandsorcery.client.KeyBindings.KEY_CAST_SPELL.getTranslatedKeyMessage().getString();
                    hintText = Component.translatable("hud.magic_and_sorcery.self_cast_prompt_key", keyName).getString();
                }
                textCol = 0xFFD8E4FF;
                borderCol = 0x804C8DFF;
            }

            float textScaledW = font.width(hintText) * 0.85f;
            float pillW = textScaledW + 16.0f;
            float pillH = 13.0f;
            float px1 = cx - pillW / 2.0f;
            float py1 = cy + 22.0f;
            float px2 = cx + pillW / 2.0f;
            float py2 = py1 + pillH;

            MagicGui.rect(g, px1, py1, px2, py2, 0xB8050C1C);
            MagicGui.rect(g, px1, py1, px2, py1 + 1.0f, borderCol);
            MagicGui.rect(g, px1, py2 - 1.0f, px2, py2, borderCol);
            MagicGui.rect(g, px1, py1, px1 + 1.0f, py2, borderCol);
            MagicGui.rect(g, px2 - 1.0f, py1, px2, py2, borderCol);
            g.flush();

            MagicGui.centeredText(g, font, hintText, cx, py1 + 2.5f, 0.85f, textCol, true);
        }
    }

    // =========================================================================
    // RADIAL SPELL MENU (hold R)
    // =========================================================================

    private static void renderRadialMenu(GuiGraphics g, Minecraft mc, Font font, int screenWidth, int screenHeight, float time) {
        List<ResourceLocation> preparedSpells = ClientMagicData.getPreparedSpells();
        List<ResourceLocation> learnedSpells = ClientMagicData.getLearnedSpells();
        int selectedIndex = ClientMagicData.getSelectedSpellIndex();
        CastingMethod method = ClientMagicData.getCurrentCastingMethod();

        long now = Util.getMillis();
        float dt = Math.max(0.0f, Math.min(0.1f, (now - lastFrameMs) / 1000.0f));
        lastFrameMs = now;
        float openT = Math.min(1.0f, (now - ClientMagicData.getRadialOpenedAtMs()) / 240.0f);
        float fade = Math.min(1.0f, openT * 1.5f);
        float pop = MagicGui.easeOutBack(openT);

        final int totalSlots = RadialMenuRenderer.TOTAL_SLOTS;
        final double sectorAngle = TAU / totalSlots;
        float centerX = RadialMenuRenderer.getWheelCenterX(screenWidth);
        float centerY = RadialMenuRenderer.getWheelCenterY(screenHeight);
        float inner = RadialMenuRenderer.INNER_CANCEL_RADIUS;
        float outer = RadialMenuRenderer.OUTER_RADIUS;

        double windowW = Math.max(1.0, (double) mc.getWindow().getWidth());
        double windowH = Math.max(1.0, (double) mc.getWindow().getHeight());
        double mouseX = mc.mouseHandler.xpos() * (double) screenWidth / windowW;
        double mouseY = mc.mouseHandler.ypos() * (double) screenHeight / windowH;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        int hoveredIndex = RadialMenuRenderer.calculateHoveredSlot(mc);
        ClientMagicData.setRadialHoveredIndex(hoveredIndex);
        boolean overLibrary = RadialMenuRenderer.isMouseInsideLibrary(mouseX, mouseY, screenWidth, screenHeight);

        boolean isDragging = ClientMagicData.isDragging();
        ClientMagicData.DragSource dragSource = ClientMagicData.getDragSource();
        int draggedSlot = ClientMagicData.getDraggedSlotIndex();
        Spell draggedSpell = ClientMagicData.getDraggedSpell();

        for (int i = 0; i < totalSlots; i++) {
            float target = i == hoveredIndex ? 1.0f : 0.0f;
            SLOT_HOVER[i] += (target - SLOT_HOVER[i]) * Math.min(1.0f, dt * 14.0f);
        }

        MagicGui.starfield(g, screenWidth, screenHeight, time, fade);

        // ---------------------------------------------------------------
        // A. LIBRARY (left)
        // ---------------------------------------------------------------
        ResourceLocation hoveredLibSpell = renderLibrary(g, font, screenWidth, screenHeight, time,
                learnedSpells, preparedSpells, selectedIndex, mouseX, mouseY, isDragging);
        ClientMagicData.setLibraryHoveredSpellId(hoveredLibSpell);

        // ---------------------------------------------------------------
        // B. RUNE WHEEL (center)
        // ---------------------------------------------------------------
        g.pose().pushPose();
        float wheelScale = 0.55f + 0.45f * pop;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(wheelScale, wheelScale, 1.0f);
        g.pose().translate(-centerX, -centerY, 0);

        // Halo and base disk
        MagicGui.glowCircle(g, centerX, centerY, outer + 46.0f, MagicGui.alpha(0x663A6BFF, fade), 48);
        MagicGui.arc(g, centerX, centerY, inner, outer, 0, TAU, 0xD0091433, 0xD0040817, 72, false);

        // Rune band rings
        float tagR = outer + 8.0f;
        float runeR = outer + 19.0f;
        MagicGui.ring(g, centerX, centerY, outer, outer + 1.0f, MagicGui.FRAME, 72);
        MagicGui.glowRing(g, centerX, centerY, outer + 0.5f, 3.0f, MagicGui.alpha(MagicGui.FRAME, 0.7f), 72);
        MagicGui.ring(g, centerX, centerY, outer + 13.0f, outer + 14.0f, MagicGui.alpha(MagicGui.FRAME, 0.8f), 72);
        MagicGui.ring(g, centerX, centerY, outer + 24.5f, outer + 25.5f, MagicGui.alpha(MagicGui.FRAME, 0.9f), 80);
        MagicGui.glowRing(g, centerX, centerY, outer + 25.0f, 4.0f, MagicGui.alpha(MagicGui.FRAME, 0.5f), 80);
        MagicGui.dottedRing(g, centerX, centerY, outer + 29.0f, 36, 0.8f, -time * 0.008f, MagicGui.alpha(MagicGui.FRAME_LIGHT, 0.7f));
        for (int k = 0; k < 4; k++) {
            double a = time * 0.008f + k * Math.PI / 2.0;
            float gx = centerX + (outer + 29.0f) * (float) Math.cos(a);
            float gy = centerY + (outer + 29.0f) * (float) Math.sin(a);
            MagicGui.diamond(g, gx, gy, 3.5f, 3.5f, MagicGui.FRAME_LIGHT, MagicGui.FRAME, false);
            MagicGui.glowCircle(g, gx, gy, 8.0f, 0x904C8DFF, 12);
        }

        // Spokes between sectors
        for (int i = 0; i < totalSlots; i++) {
            double a = -Math.PI / 2.0 + i * sectorAngle - sectorAngle / 2.0;
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            MagicGui.line(g, centerX + inner * c, centerY + inner * s, centerX + outer * c, centerY + outer * s, 1.0f,
                    MagicGui.alpha(MagicGui.FRAME, 0.65f), MagicGui.alpha(MagicGui.FRAME, 0.15f), false);
        }

        // Sector highlights
        for (int i = 0; i < totalSlots; i++) {
            double mid = -Math.PI / 2.0 + i * sectorAngle;
            double start = mid - sectorAngle / 2.0;
            double end = mid + sectorAngle / 2.0;
            Spell spell = ClientMagicData.getSpellAtSlot(i);
            float hover = SLOT_HOVER[i];
            if (hover > 0.01f) {
                int hc = slotHighlightColor(spell, isDragging);
                MagicGui.arc(g, centerX, centerY, inner, outer, start, end,
                        MagicGui.alpha(hc, 0.02f * hover), MagicGui.alpha(hc, 0.38f * hover), 16, true);
                MagicGui.arc(g, centerX, centerY, outer - 2.0f, outer + 1.0f, start, end, MagicGui.alpha(hc, hover), MagicGui.alpha(hc, hover), 16, false);
            } else if (spell != null && i == selectedIndex && !isDragging) {
                int sc = SpellVisuals.of(spell).color();
                MagicGui.arc(g, centerX, centerY, outer - 2.0f, outer + 1.0f, start, end, sc, sc, 16, false);
                MagicGui.arc(g, centerX, centerY, inner, inner + 2.0f, start, end, sc, sc, 16, false);
            }
        }

        // Slot diamonds
        float slotR = (inner + outer) / 2.0f;
        for (int i = 0; i < totalSlots; i++) {
            double mid = -Math.PI / 2.0 + i * sectorAngle;
            float px = centerX + (float) Math.cos(mid) * slotR;
            float py = centerY + (float) Math.sin(mid) * slotR;
            float hover = SLOT_HOVER[i];
            float size = 13.0f + 3.5f * hover;
            Spell spell = ClientMagicData.getSpellAtSlot(i);
            boolean isDraggedSource = isDragging && dragSource == ClientMagicData.DragSource.WHEEL && i == draggedSlot;

            if (spell != null) {
                SpellVisuals.Style st = SpellVisuals.of(spell);
                float breathe = MagicGui.pulse(time + i * 7.0f, 0.12f);
                MagicGui.glowCircle(g, px, py, size + 11.0f, MagicGui.alpha(st.color(), 0.22f + 0.14f * breathe + 0.35f * hover), 24);
                MagicGui.diamond(g, px, py, size + 1.5f, size + 1.5f, MagicGui.mix(st.color(), 0xFFFFFFFF, 0.35f), st.color(), false);
                MagicGui.diamond(g, px, py, size, size, MagicGui.mix(st.deepColor(), st.color(), 0.5f), st.deepColor(), false);
                MagicGui.diamondOutline(g, px, py, size - 3.0f, 1.0f, MagicGui.alpha(st.color(), 0.55f), false);
                if (i == selectedIndex && !isDraggedSource) {
                    MagicGui.dottedRing(g, px, py, size + 6.0f, 4, 1.3f, time * 0.08f, st.color());
                }
                if (isDraggedSource) {
                    MagicGui.diamond(g, px, py, size + 1.5f, size + 1.5f, 0xB0000000, 0xB0000000, false);
                }
            } else {
                MagicGui.diamond(g, px, py, size, size, 0xD0101A36, 0xD0070C1E, false);
                MagicGui.diamondOutline(g, px, py, size, 1.0f, MagicGui.alpha(MagicGui.FRAME, 0.45f + 0.45f * hover), false);
                if (isDragging && hover > 0.01f) {
                    MagicGui.glowCircle(g, px, py, size + 10.0f, MagicGui.alpha(0xFF00E5FF, 0.5f * hover), 20);
                }
            }

            // Number tag in the outer band
            float tx = centerX + (float) Math.cos(mid) * tagR;
            float ty = centerY + (float) Math.sin(mid) * tagR;
            int tagColor = spell != null ? SpellVisuals.of(spell).color() : MagicGui.FRAME;
            MagicGui.rect(g, tx - 4.5f, ty - 4.5f, tx + 4.5f, ty + 4.5f, 0xF0060B1C);
            MagicGui.outline(g, tx - 4.5f, ty - 4.5f, tx + 4.5f, ty + 4.5f, 1.0f, MagicGui.alpha(tagColor, 0.6f + 0.4f * hover));
        }

        // Center hub
        Spell hoveredSpell = hoveredIndex >= 0 ? ClientMagicData.getSpellAtSlot(hoveredIndex) : null;
        boolean cancelZone = hoveredIndex == -1 && !overLibrary;
        int hubColor = hoveredSpell != null ? SpellVisuals.of(hoveredSpell).color()
                : (cancelZone ? MagicGui.CANCEL : MagicGui.FRAME);
        int hubDeep = hoveredSpell != null ? SpellVisuals.of(hoveredSpell).deepColor() : 0xFF0A1230;
        float hubR = inner - 3.0f;
        MagicGui.radialGradient(g, centerX, centerY, hubR, MagicGui.mix(hubDeep, hubColor, 0.25f), 0xF5050A1C, 40, false);
        MagicGui.ring(g, centerX, centerY, hubR, hubR + 1.2f, hubColor, 48);
        MagicGui.glowRing(g, centerX, centerY, hubR + 0.6f, 3.5f, MagicGui.alpha(hubColor, 0.75f), 48);
        MagicGui.dottedRing(g, centerX, centerY, hubR - 4.0f, 12, 0.7f, -time * 0.05f, MagicGui.alpha(hubColor, 0.6f));
        g.flush();

        // Text pass: runes, slot glyphs, numbers, hub
        MagicGui.runeRing(g, font, centerX, centerY, runeR, 40, time * 0.012f, MagicGui.alpha(MagicGui.FRAME_LIGHT, 0.8f * fade), 0.6f, 1);

        for (int i = 0; i < totalSlots; i++) {
            double mid = -Math.PI / 2.0 + i * sectorAngle;
            float px = centerX + (float) Math.cos(mid) * slotR;
            float py = centerY + (float) Math.sin(mid) * slotR;
            float hover = SLOT_HOVER[i];
            Spell spell = ClientMagicData.getSpellAtSlot(i);
            boolean isDraggedSource = isDragging && dragSource == ClientMagicData.DragSource.WHEEL && i == draggedSlot;

            if (spell != null) {
                SpellVisuals.Style st = SpellVisuals.of(spell);
                int glyphColor = isDraggedSource ? 0x60FFFFFF : MagicGui.mix(MagicGui.mix(st.color(), 0xFFFFFFFF, 0.3f), 0xFFFFFFFF, hover);
                MagicGui.glyph(g, font, st.glyph(), px, py, 1.5f + 0.3f * hover, glyphColor);
                if (isDragging && hover > 0.5f && !isDraggedSource) {
                    String action = dragSource == ClientMagicData.DragSource.WHEEL ? "⇄" : "↓";
                    MagicGui.glyph(g, font, action, px, py - 22.0f, 1.0f, 0xFFFFB030);
                }
            } else if (isDragging && hover > 0.5f) {
                MagicGui.glyph(g, font, "↓", px, py, 1.4f, 0xFF7FF6FF);
            } else {
                MagicGui.glyph(g, font, "✦", px, py, 0.9f, MagicGui.alpha(MagicGui.FRAME_LIGHT, 0.35f + 0.5f * hover));
            }

            float tx = centerX + (float) Math.cos(mid) * tagR;
            float ty = centerY + (float) Math.sin(mid) * tagR;
            MagicGui.centeredText(g, font, String.valueOf(i + 1), tx + 0.25f, ty - 2.5f, 0.7f,
                    hover > 0.5f ? 0xFFFFFFFF : MagicGui.TEXT_DIM, false);
        }

        if (hoveredSpell != null) {
            SpellVisuals.Style st = SpellVisuals.of(hoveredSpell);
            MagicGui.glyph(g, font, st.glyph(), centerX, centerY - 4.0f, 2.2f, MagicGui.mix(st.color(), 0xFFFFFFFF, 0.2f));
            String name = MagicGui.fit(font, hoveredSpell.getName().getString(), (int) ((hubR * 2 - 8) / 0.6f));
            MagicGui.centeredText(g, font, name, centerX, centerY + 9.0f, 0.6f, MagicGui.TEXT, true);
        } else if (cancelZone) {
            MagicGui.glyph(g, font, "✕", centerX, centerY - 6.0f, 1.5f, MagicGui.CANCEL);
            MagicGui.centeredText(g, font, "CANCELAR", centerX, centerY + 3.0f, 0.6f, 0xFFFF8A98, true);
            String hint = isDragging ? "Soltar p/ cancelar" : Component.translatable("gui.magic_and_sorcery.keep_current").getString();
            hint = MagicGui.fit(font, hint, (int) ((hubR * 2 - 6) / 0.5f));
            MagicGui.centeredText(g, font, hint, centerX, centerY + 10.0f, 0.5f, MagicGui.TEXT_DIM, false);
        } else if (hoveredIndex >= 0) {
            MagicGui.glyph(g, font, String.valueOf(hoveredIndex + 1), centerX, centerY - 4.0f, 1.8f, MagicGui.FRAME_LIGHT);
            MagicGui.centeredText(g, font, "VACÍO", centerX, centerY + 9.0f, 0.6f, MagicGui.TEXT_DIM, true);
        } else {
            MagicGui.glyph(g, font, "✦", centerX, centerY, 1.8f, MagicGui.alpha(MagicGui.FRAME_LIGHT, 0.6f));
        }
        g.pose().popPose();

        // ---------------------------------------------------------------
        // C. SPELL CARD / DRAG FEEDBACK (right)
        // ---------------------------------------------------------------
        int minPanelX = (int) (centerX + outer + 32);
        int availableRight = Math.max(104, screenWidth - minPanelX - 8);
        int panelW = Math.max(110, Math.min(146, availableRight));
        int panelX = screenWidth - panelW - 10;

        float slide = (1.0f - pop) * 24.0f;
        g.pose().pushPose();
        g.pose().translate(slide, 0, 0);
        if (isDragging) {
            renderDragCard(g, font, panelX, panelW, centerY, time, preparedSpells, hoveredIndex, overLibrary,
                    dragSource, draggedSlot, draggedSpell);
        } else {
            Spell inspected = null;
            if (hoveredLibSpell != null) {
                inspected = ModSpells.getSpell(hoveredLibSpell);
            } else if (hoveredSpell != null) {
                inspected = hoveredSpell;
            }
            if (inspected != null) {
                // Info card drawn 20% smaller, anchored to the right edge
                float cardScale = 0.8f;
                float anchorX = panelX + panelW;
                g.pose().pushPose();
                g.pose().translate(anchorX, centerY, 0);
                g.pose().scale(cardScale, cardScale, 1.0f);
                g.pose().translate(-anchorX, -centerY, 0);
                renderSpellCard(g, mc, font, panelX, panelW, centerY, screenHeight, time, inspected, method);
                g.pose().popPose();
            }
        }
        g.pose().popPose();

        // ---------------------------------------------------------------
        // D. CURSOR: dragged spell ghost or aiming reticle
        // ---------------------------------------------------------------
        float curX = (float) mouseX;
        float curY = (float) mouseY;
        if (isDragging && draggedSpell != null) {
            SpellVisuals.Style st = SpellVisuals.of(draggedSpell);
            float bob = (float) Math.sin(time * 0.3f) * 1.0f;
            MagicGui.glowCircle(g, curX, curY + bob, 26.0f, MagicGui.alpha(st.color(), 0.55f), 24);
            MagicGui.diamond(g, curX, curY + bob, 15.5f, 15.5f, MagicGui.mix(st.color(), 0xFFFFFFFF, 0.35f), st.color(), false);
            MagicGui.diamond(g, curX, curY + bob, 14.0f, 14.0f, MagicGui.mix(st.deepColor(), st.color(), 0.5f), st.deepColor(), false);
            String dragName = draggedSpell.getName().getString();
            int nameW = font.width(dragName) + 10;
            float bx = curX - nameW / 2.0f;
            float by = curY + 20.0f;
            MagicGui.glowRect(g, bx, by, bx + nameW, by + 12, 4.0f, MagicGui.alpha(st.color(), 0.4f));
            MagicGui.gradientRect(g, bx, by, bx + nameW, by + 12, 0xF0101A3A, 0xF0060B1C, false);
            MagicGui.outline(g, bx, by, bx + nameW, by + 12, 1.0f, st.color());
            g.flush();
            MagicGui.glyph(g, font, st.glyph(), curX, curY + bob, 1.5f, 0xFFFFFFFF);
            g.drawString(font, dragName, (int) (bx + 5), (int) (by + 2), 0xFFFFFFFF, true);
        } else if (overLibrary) {
            MagicGui.glowCircle(g, curX, curY, 7.0f, 0xAAFFFFFF, 12);
            MagicGui.circle(g, curX, curY, 1.6f, 0xFFFFFFFF, 10);
        } else {
            double reach = Math.min(dist, outer + 6.0);
            float pipX = (float) (centerX + (dist > 0.001 ? dx / dist * reach : 0));
            float pipY = (float) (centerY + (dist > 0.001 ? dy / dist * reach : 0));
            int pipColor = hoveredSpell != null ? SpellVisuals.of(hoveredSpell).color()
                    : (hoveredIndex >= 0 ? 0xFFFFFFFF : MagicGui.CANCEL);
            if (dist > inner) {
                float ex = (float) (centerX + dx / dist * inner);
                float ey = (float) (centerY + dy / dist * inner);
                MagicGui.line(g, ex, ey, pipX, pipY, 2.0f, MagicGui.alpha(pipColor, 0.05f), MagicGui.alpha(pipColor, 0.6f), true);
            }
            MagicGui.glowCircle(g, pipX, pipY, 10.0f, MagicGui.alpha(pipColor, 0.7f), 16);
            MagicGui.diamond(g, pipX, pipY, 5.0f, 5.0f, 0xFFFFFFFF, pipColor, false);
            MagicGui.circle(g, pipX, pipY, 1.4f, 0xFFFFFFFF, 8);
        }
        g.flush();
    }

    private static int slotHighlightColor(Spell spell, boolean dragging) {
        if (dragging) {
            return spell != null ? 0xFFFFB030 : 0xFF00E5FF;
        }
        return spell != null ? SpellVisuals.of(spell).color() : MagicGui.FRAME_LIGHT;
    }

    // ---------------------------------------------------------------
    // Library panel
    // ---------------------------------------------------------------

    private static ResourceLocation renderLibrary(GuiGraphics g, Font font, int screenWidth, int screenHeight, float time,
                                                  List<ResourceLocation> learned, List<ResourceLocation> prepared,
                                                  int selectedIndex, double mouseX, double mouseY, boolean isDragging) {
        int libX = RadialMenuRenderer.getLibraryX(screenWidth);
        int libY = RadialMenuRenderer.getLibraryY(screenHeight);
        int libW = RadialMenuRenderer.getLibraryWidth(screenWidth);
        int libH = RadialMenuRenderer.getLibraryHeight(screenHeight);
        int listY = RadialMenuRenderer.getLibraryListY(screenHeight);
        int listH = RadialMenuRenderer.getLibraryListHeight(screenHeight);
        int sbX = RadialMenuRenderer.getLibraryScrollBarX(screenWidth);
        int sbW = RadialMenuRenderer.LIBRARY_SCROLLBAR_WIDTH;
        int itemH = RadialMenuRenderer.LIBRARY_ITEM_HEIGHT;
        int header = RadialMenuRenderer.LIBRARY_HEADER_HEIGHT;

        int totalH = learned.size() * itemH;
        int maxScroll = Math.max(0, totalH - listH);
        if (ClientMagicData.isDraggingScrollBar() && maxScroll > 0) {
            int thumbH = Math.max(12, (int) ((float) listH / Math.max(listH, totalH) * listH));
            float progress = (float) (mouseY - listY - thumbH / 2.0) / Math.max(1, listH - thumbH);
            ClientMagicData.setLibraryScrollOffset(progress * maxScroll);
        }
        float scroll = Math.max(0.0f, Math.min(maxScroll, ClientMagicData.getLibraryScrollOffset()));
        ClientMagicData.setLibraryScrollOffset(scroll);

        ResourceLocation activeId = selectedIndex >= 0 && selectedIndex < prepared.size() ? prepared.get(selectedIndex) : null;

        // Frame and header
        MagicGui.panel(g, libX, libY, libW, libH, MagicGui.FRAME, time);
        MagicGui.gradientRect(g, libX + 3, libY + 3, libX + libW - 3, libY + header - 1,
                MagicGui.alpha(MagicGui.FRAME, 0.30f), MagicGui.alpha(MagicGui.FRAME, 0.04f), false);
        MagicGui.divider(g, libX + 6, libX + libW - 6, libY + header, MagicGui.FRAME_LIGHT);
        // Little open book icon
        float bx = libX + 8;
        float by = libY + 8;
        MagicGui.gradientRect(g, bx, by, bx + 5, by + 9, 0xFF3F74E8, 0xFF1C3A99, false);
        MagicGui.gradientRect(g, bx + 6, by, bx + 11, by + 9, 0xFF3F74E8, 0xFF1C3A99, false);
        MagicGui.rect(g, bx + 5, by + 1, bx + 6, by + 10, MagicGui.FRAME_LIGHT);
        MagicGui.glowCircle(g, bx + 5.5f, by + 4.5f, 9.0f, 0x664C8DFF, 12);
        // Count badge
        float badgeX = libX + libW - 13;
        float badgeY = libY + header / 2.0f;
        MagicGui.diamond(g, badgeX, badgeY, 8.0f, 8.0f, MagicGui.FRAME, 0xFF1C3A99, false);
        MagicGui.diamond(g, badgeX, badgeY, 6.5f, 6.5f, 0xFF0C1838, 0xFF050A1D, false);

        // Row shapes
        int rowX1 = libX + 5;
        int rowX2 = (maxScroll > 0 ? sbX - 3 : libX + libW - 5);
        ResourceLocation hoveredId = null;

        g.flush();
        g.enableScissor(libX + 2, listY, libX + libW - 2, listY + listH);
        for (int i = 0; i < learned.size(); i++) {
            ResourceLocation id = learned.get(i);
            Spell s = ModSpells.getSpell(id);
            if (s == null) continue;
            int itemY = listY + (int) (i * itemH - scroll);
            if (itemY + itemH < listY || itemY > listY + listH) continue;

            boolean hovered = mouseX >= libX + 2 && mouseX < rowX2 && mouseY >= itemY && mouseY < itemY + itemH
                    && mouseY >= listY && mouseY <= listY + listH;
            if (hovered && !isDragging) hoveredId = id;
            boolean equipped = prepared.contains(id);
            SpellVisuals.Style st = SpellVisuals.of(id);

            float y1 = itemY + 1;
            float y2 = itemY + itemH - 1;
            if (hovered) {
                MagicGui.glowRect(g, rowX1, y1, rowX2, y2, 3.0f, MagicGui.alpha(st.color(), 0.45f));
                MagicGui.horizontalGradient(g, rowX1, y1, rowX2, y2, MagicGui.alpha(st.color(), 0.40f), MagicGui.alpha(st.color(), 0.06f), false);
                MagicGui.outline(g, rowX1, y1, rowX2, y2, 1.0f, st.color());
            } else {
                MagicGui.horizontalGradient(g, rowX1, y1, rowX2, y2, 0x90122046, 0x60081028, false);
                MagicGui.outline(g, rowX1, y1, rowX2, y2, 1.0f, MagicGui.alpha(id.equals(activeId) ? st.color() : MagicGui.FRAME, 0.45f));
            }
            if (equipped) {
                MagicGui.rect(g, rowX1, y1, rowX1 + 2, y2, st.color());
                MagicGui.diamond(g, rowX2 - 5, itemY + itemH / 2.0f, 2.5f, 2.5f, MagicGui.mix(st.color(), 0xFFFFFFFF, 0.4f), st.color(), false);
            }
            float ix = rowX1 + 4;
            float iy = itemY + 2.5f;
            MagicGui.gradientRect(g, ix, iy, ix + 13, iy + 13, MagicGui.mix(st.deepColor(), st.color(), 0.45f), st.deepColor(), false);
            MagicGui.outline(g, ix, iy, ix + 13, iy + 13, 1.0f, st.color());
        }
        g.flush();

        // Row text
        for (int i = 0; i < learned.size(); i++) {
            ResourceLocation id = learned.get(i);
            Spell s = ModSpells.getSpell(id);
            if (s == null) continue;
            int itemY = listY + (int) (i * itemH - scroll);
            if (itemY + itemH < listY || itemY > listY + listH) continue;

            SpellVisuals.Style st = SpellVisuals.of(id);
            boolean hovered = id.equals(hoveredId);
            boolean equipped = prepared.contains(id);
            MagicGui.glyph(g, font, st.glyph(), rowX1 + 10.5f, itemY + itemH / 2.0f, 1.0f, MagicGui.mix(st.color(), 0xFFFFFFFF, 0.25f));
            int nameX = rowX1 + 21;
            String name = MagicGui.fit(font, s.getName().getString(), rowX2 - nameX - (equipped ? 9 : 3));
            int color = hovered ? 0xFFFFFFFF : (equipped ? MagicGui.mix(st.color(), 0xFFFFFFFF, 0.55f) : MagicGui.TEXT);
            g.drawString(font, name, nameX, itemY + 5, color, true);
        }
        g.flush();
        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            MagicGui.rect(g, sbX, listY, sbX + sbW, listY + listH, 0x99050A1D);
            int thumbH = Math.max(12, (int) ((float) listH / Math.max(listH, totalH) * listH));
            float thumbY = listY + scroll / Math.max(1, maxScroll) * (listH - thumbH);
            boolean active = ClientMagicData.isDraggingScrollBar() || (mouseX >= sbX - 2 && mouseX <= sbX + sbW + 2
                    && mouseY >= listY && mouseY <= listY + listH);
            MagicGui.gradientRect(g, sbX, thumbY, sbX + sbW, thumbY + thumbH, MagicGui.FRAME_LIGHT, MagicGui.FRAME, false);
            if (active) {
                MagicGui.glowRect(g, sbX, thumbY, sbX + sbW, thumbY + thumbH, 3.0f, 0x904C8DFF);
            }
        }
        g.flush();

        // Header text
        g.drawString(font, "BIBLIOTECA", libX + 23, libY + 8, MagicGui.TITLE, true);
        MagicGui.centeredText(g, font, String.valueOf(learned.size()), badgeX + 0.5f, badgeY - 3.5f, 0.85f, 0xFFFFFFFF, false);
        return hoveredId;
    }

    // ---------------------------------------------------------------
    // Spell card (right)
    // ---------------------------------------------------------------

    private static void renderSpellCard(GuiGraphics g, Minecraft mc, Font font, int panelX, int panelW, float centerY,
                                        int screenHeight, float time, Spell spell, CastingMethod method) {
        SpellVisuals.Style st = SpellVisuals.of(spell);
        int accent = st.color();
        String desc = spell.getDescription().getString();
        int descW = panelW - 12;
        int descLines = Math.max(1, font.split(FormattedText.of(desc), descW).size());
        int statsCount = 5;
        int statsTop = 38;
        int descTop = statsTop + statsCount * 11 + 6;
        int panelH = descTop + descLines * font.lineHeight + 18;
        int panelY = (int) Math.max(8, Math.min(screenHeight - panelH - 8, centerY - panelH / 2.0f));

        MagicGui.panel(g, panelX, panelY, panelW, panelH, accent, time);

        // Icon box
        float ix = panelX + 7;
        float iy = panelY + 7;
        MagicGui.glowCircle(g, ix + 10, iy + 10, 18.0f, MagicGui.alpha(accent, 0.45f + 0.2f * MagicGui.pulse(time, 0.15f)), 20);
        MagicGui.gradientRect(g, ix, iy, ix + 20, iy + 20, MagicGui.mix(st.deepColor(), accent, 0.5f), st.deepColor(), false);
        MagicGui.outline(g, ix, iy, ix + 20, iy + 20, 1.0f, accent);
        MagicGui.divider(g, panelX + 6, panelX + panelW - 6, panelY + statsTop - 5, accent);
        MagicGui.divider(g, panelX + 6, panelX + panelW - 6, panelY + descTop - 4, MagicGui.alpha(accent, 0.7f));
        g.flush();

        MagicGui.glyph(g, font, st.glyph(), ix + 10, iy + 10, 1.5f, MagicGui.mix(accent, 0xFFFFFFFF, 0.3f));
        int titleX = panelX + 32;
        int titleW = panelW - 32 - 6;
        g.drawString(font, MagicGui.fit(font, spell.getName().getString().toUpperCase(), titleW), titleX, panelY + 8, accent, true);
        String sub = spell.getSchool().getDisplayName().getString() + " • " + spell.getType().getDisplayName().getString();
        g.pose().pushPose();
        g.pose().translate(titleX, panelY + 19, 0);
        g.pose().scale(0.8f, 0.8f, 1.0f);
        g.drawString(font, MagicGui.fit(font, sub, (int) (titleW / 0.8f)), 0, 0, MagicGui.TEXT_DIM, false);
        g.pose().popPose();

        boolean isCreative = mc.player != null && mc.player.isCreative();
        boolean bypassMana = isCreative && ModConfigs.INFINITE_MANA_IN_CREATIVE.get();
        boolean bypassCooldown = isCreative && ModConfigs.NO_COOLDOWN_IN_CREATIVE.get();

        int rowX = panelX + 7;
        int rowW = panelW - 14;
        int y = panelY + statsTop;
        String cost = bypassMana ? "0 MP" : String.format("%.0f MP", spell.calculateFinalManaCost(method));
        y = statRow(g, font, rowX, y, rowW, "◆", MagicGui.MANA, "Maná", cost, MagicGui.MANA);
        int castTicks = spell.calculateFinalCastTime(method);
        y = statRow(g, font, rowX, y, rowW, "⌛", MagicGui.CHANNEL, "Canal", castTicks > 0 ? String.format("%.1fs", castTicks / 20.0f) : "Inst.", MagicGui.CHANNEL);
        y = statRow(g, font, rowX, y, rowW, "⟳", MagicGui.COOLDOWN, "Recarga", String.format("%.1fs", spell.calculateFinalCooldown(method) / 20.0f), MagicGui.COOLDOWN);
        if (spell.getBaseDamage() > 0) {
            y = statRow(g, font, rowX, y, rowW, "✸", MagicGui.DAMAGE, "Daño", String.format("%.0f", spell.calculateFinalDamage(method)), MagicGui.DAMAGE);
        } else if (spell.getBaseHealing() > 0) {
            y = statRow(g, font, rowX, y, rowW, "♥", 0xFFFF5C7C, "Curación", String.format("%.1f", spell.calculateFinalHealing(method)), 0xFFFF7A96);
        } else {
            y = statRow(g, font, rowX, y, rowW, "➶", MagicGui.RANGE, "Alcance", String.format("%.0fm", spell.getRange(method)), MagicGui.RANGE);
        }
        int cd = bypassCooldown ? 0 : ClientMagicData.getSpellCooldown(spell.getId());
        if (cd > 0) {
            statRow(g, font, rowX, y, rowW, "✧", MagicGui.COOLDOWN, "Estado", String.format("%.1fs", cd / 20.0f), MagicGui.COOLDOWN);
        } else {
            statRow(g, font, rowX, y, rowW, "✧", accent, "Estado", "Listo", MagicGui.RANGE);
        }

        g.drawWordWrap(font, FormattedText.of(desc), panelX + 6, panelY + descTop, descW, 0xFFC8D6F0);

        String catalyst = method.getDisplayName().getString();
        g.pose().pushPose();
        g.pose().translate(panelX + 7, panelY + panelH - 10, 0);
        g.pose().scale(0.7f, 0.7f, 1.0f);
        g.drawString(font, MagicGui.fit(font, "✦ " + catalyst, (int) ((panelW - 14) / 0.7f)), 0, 0, MagicGui.alpha(accent, 0.85f), false);
        g.pose().popPose();
    }

    private static int statRow(GuiGraphics g, Font font, int x, int y, int w, String icon, int iconColor,
                               String label, String value, int valueColor) {
        g.drawString(font, icon, x, y, iconColor, true);
        g.drawString(font, label, x + 11, y, MagicGui.TEXT_DIM, true);
        g.drawString(font, value, x + w - font.width(value), y, valueColor, true);
        return y + 11;
    }

    // ---------------------------------------------------------------
    // Drag feedback card (right)
    // ---------------------------------------------------------------

    private static void renderDragCard(GuiGraphics g, Font font, int panelX, int panelW, float centerY, float time,
                                       List<ResourceLocation> prepared, int hoveredIndex, boolean overLibrary,
                                       ClientMagicData.DragSource dragSource, int draggedSlot, Spell draggedSpell) {
        String dragged = draggedSpell != null ? draggedSpell.getName().getString() : "Hechizo";
        String header;
        String line1 = null;
        int line1Color = 0xFFFFFFFF;
        String line2 = null;
        int line2Color = 0xFFFFFFFF;
        String body;
        int accent;

        if (overLibrary && dragSource == ClientMagicData.DragSource.WHEEL) {
            accent = MagicGui.CANCEL;
            header = "✕ DESEQUIPAR";
            line1 = dragged;
            body = "Suelta el click para desequipar de la rueda. Seguirá en la biblioteca.";
        } else if (hoveredIndex == -1) {
            accent = MagicGui.CANCEL;
            header = "✕ CANCELAR";
            body = "Suelta el click en el centro para cancelar la acción sin realizar cambios.";
        } else if (dragSource == ClientMagicData.DragSource.WHEEL && hoveredIndex == draggedSlot) {
            accent = 0xFFFFD166;
            header = "ORIGEN (SIN CAMBIO)";
            body = "El hechizo permanece en su espacio original #" + (draggedSlot + 1) + ".";
        } else if (hoveredIndex < prepared.size() && prepared.get(hoveredIndex) != null) {
            Spell target = ModSpells.getSpell(prepared.get(hoveredIndex));
            accent = 0xFFFFB030;
            header = dragSource == ClientMagicData.DragSource.WHEEL ? "⇄ INTERCAMBIAR" : "↓ REEMPLAZAR";
            line1 = dragged;
            line2 = "→ #" + (hoveredIndex + 1) + " (" + (target != null ? target.getName().getString() : "Hechizo") + ")";
            line2Color = 0xFFFFE066;
            body = "Suelta el click para asignar este hechizo al espacio #" + (hoveredIndex + 1) + ".";
        } else {
            accent = 0xFF00E5FF;
            header = dragSource == ClientMagicData.DragSource.WHEEL ? "↓ MOVER" : "↓ EQUIPAR";
            line1 = dragged;
            line2 = "→ Espacio #" + (hoveredIndex + 1);
            line2Color = 0xFF7FF6FF;
            body = "Suelta el click izquierdo para colocarlo en este espacio.";
        }
        if (draggedSpell != null && line1 != null) {
            line1Color = MagicGui.mix(SpellVisuals.of(draggedSpell).color(), 0xFFFFFFFF, 0.3f);
        }

        int textW = panelW - 12;
        int bodyLines = Math.max(1, font.split(FormattedText.of(body), textW).size());
        int panelH = 26 + (line1 != null ? 12 : 0) + (line2 != null ? 12 : 0) + bodyLines * font.lineHeight + 8;
        int panelY = (int) (centerY - panelH / 2.0f);

        MagicGui.panel(g, panelX, panelY, panelW, panelH, accent, time);
        MagicGui.divider(g, panelX + 6, panelX + panelW - 6, panelY + 19, accent);
        g.flush();

        g.drawString(font, MagicGui.fit(font, header, textW), panelX + 6, panelY + 7, accent, true);
        int y = panelY + 25;
        if (line1 != null) {
            g.drawString(font, MagicGui.fit(font, line1, textW), panelX + 6, y, line1Color, true);
            y += 12;
        }
        if (line2 != null) {
            g.drawString(font, MagicGui.fit(font, line2, textW), panelX + 6, y, line2Color, true);
            y += 12;
        }
        g.drawWordWrap(font, FormattedText.of(body), panelX + 6, y, textW, 0xFFC8D6F0);
    }
}
