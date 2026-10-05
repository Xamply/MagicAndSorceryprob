package com.cesar.magicandsorcery.client.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;

/**
 * Drawing toolkit for the arcane interface: ornate panels, soft glows, rune rings and starfields.
 * Shapes are queued into the GUI buffer; call {@link GuiGraphics#flush()} before drawing text on top.
 */
public final class MagicGui {

    // Arcane palette
    public static final int FRAME = 0xFF4C8DFF;
    public static final int FRAME_LIGHT = 0xFFA9CCFF;
    public static final int PANEL_TOP = 0xF00C1838;
    public static final int PANEL_BOTTOM = 0xF0050A1D;
    public static final int TEXT = 0xFFE6F0FF;
    public static final int TEXT_DIM = 0xFF8FA6CC;
    public static final int TITLE = 0xFFCFE6FF;
    public static final int MANA = 0xFF4FC3FF;
    public static final int CHANNEL = 0xFFC792FF;
    public static final int COOLDOWN = 0xFFFFC857;
    public static final int DAMAGE = 0xFFFF6B6B;
    public static final int RANGE = 0xFF7CFFB2;
    public static final int CANCEL = 0xFFFF5A6E;

    private static final String RUNES = "abcdefghijklmnopqrstuvwxyz";
    private static final Style RUNE_STYLE = Style.EMPTY.withFont(Minecraft.ALT_FONT);

    private MagicGui() {
    }

    // ------------------------------------------------------------------
    // Color helpers
    // ------------------------------------------------------------------

    public static int alpha(int color, float alpha) {
        int base = (color >>> 24) & 0xFF;
        int a = Math.max(0, Math.min(255, (int) (base * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float easeOutBack(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3) + c1 * (float) Math.pow(t - 1.0f, 2);
    }

    public static float pulse(float time, float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time * speed);
    }

    // ------------------------------------------------------------------
    // Raw vertices
    // ------------------------------------------------------------------

    private static VertexConsumer buffer(GuiGraphics g, boolean additive) {
        return g.bufferSource().getBuffer(additive ? MagicRenderTypes.GUI_GLOW : MagicRenderTypes.GUI_FLAT);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, int color) {
        vc.vertex(m, x, y, 0).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x1, float y1, int c1, float x2, float y2, int c2,
                             float x3, float y3, int c3, float x4, float y4, int c4) {
        vertex(vc, m, x1, y1, c1);
        vertex(vc, m, x2, y2, c2);
        vertex(vc, m, x3, y3, c3);
        vertex(vc, m, x4, y4, c4);
    }

    // ------------------------------------------------------------------
    // Rectangles
    // ------------------------------------------------------------------

    public static void rect(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        gradientRect(g, x1, y1, x2, y2, color, color, false);
    }

    public static void gradientRect(GuiGraphics g, float x1, float y1, float x2, float y2, int top, int bottom, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, additive);
        quad(vc, m, x1, y1, top, x1, y2, bottom, x2, y2, bottom, x2, y1, top);
    }

    public static void horizontalGradient(GuiGraphics g, float x1, float y1, float x2, float y2, int left, int right, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, additive);
        quad(vc, m, x1, y1, left, x1, y2, left, x2, y2, right, x2, y1, right);
    }

    public static void outline(GuiGraphics g, float x1, float y1, float x2, float y2, float t, int color) {
        rect(g, x1, y1, x2, y1 + t, color);
        rect(g, x1, y2 - t, x2, y2, color);
        rect(g, x1, y1 + t, x1 + t, y2 - t, color);
        rect(g, x2 - t, y1 + t, x2, y2 - t, color);
    }

    /**
     * Soft additive halo around a rectangle.
     */
    public static void glowRect(GuiGraphics g, float x1, float y1, float x2, float y2, float spread, int color) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, true);
        int clear = color & 0x00FFFFFF;
        // Edges
        quad(vc, m, x1, y1 - spread, clear, x1, y1, color, x2, y1, color, x2, y1 - spread, clear);
        quad(vc, m, x1, y2, color, x1, y2 + spread, clear, x2, y2 + spread, clear, x2, y2, color);
        quad(vc, m, x1 - spread, y1, clear, x1 - spread, y2, clear, x1, y2, color, x1, y1, color);
        quad(vc, m, x2, y1, color, x2, y2, color, x2 + spread, y2, clear, x2 + spread, y1, clear);
        // Corners
        quad(vc, m, x1 - spread, y1 - spread, clear, x1 - spread, y1, clear, x1, y1, color, x1, y1 - spread, clear);
        quad(vc, m, x2, y1 - spread, clear, x2, y1, color, x2 + spread, y1, clear, x2 + spread, y1 - spread, clear);
        quad(vc, m, x1 - spread, y2, clear, x1 - spread, y2 + spread, clear, x1, y2 + spread, clear, x1, y2, color);
        quad(vc, m, x2, y2, color, x2, y2 + spread, clear, x2 + spread, y2 + spread, clear, x2 + spread, y2, clear);
    }

    // ------------------------------------------------------------------
    // Circles, rings, arcs
    // ------------------------------------------------------------------

    public static void circle(GuiGraphics g, float cx, float cy, float r, int color, int segments) {
        radialGradient(g, cx, cy, r, color, color, segments, false);
    }

    /**
     * Additive soft light: bright center fading to nothing at the edge.
     */
    public static void glowCircle(GuiGraphics g, float cx, float cy, float r, int color, int segments) {
        radialGradient(g, cx, cy, r, color, color & 0x00FFFFFF, segments, true);
    }

    public static void radialGradient(GuiGraphics g, float cx, float cy, float r, int inner, int outer, int segments, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, additive);
        double step = Math.PI * 2.0 / segments;
        for (int i = 0; i < segments; i++) {
            float x1 = cx + r * (float) Math.cos(i * step);
            float y1 = cy + r * (float) Math.sin(i * step);
            float x2 = cx + r * (float) Math.cos((i + 1) * step);
            float y2 = cy + r * (float) Math.sin((i + 1) * step);
            quad(vc, m, cx, cy, inner, x1, y1, outer, x2, y2, outer, cx, cy, inner);
        }
    }

    public static void ring(GuiGraphics g, float cx, float cy, float rIn, float rOut, int color, int segments) {
        arc(g, cx, cy, rIn, rOut, 0, Math.PI * 2.0, color, color, segments, false);
    }

    /**
     * Additive glowing ring: brightest at radius {@code r}, fading over {@code width} on both sides.
     */
    public static void glowRing(GuiGraphics g, float cx, float cy, float r, float width, int color, int segments) {
        int clear = color & 0x00FFFFFF;
        arc(g, cx, cy, Math.max(0, r - width), r, 0, Math.PI * 2.0, clear, color, segments, true);
        arc(g, cx, cy, r, r + width, 0, Math.PI * 2.0, color, clear, segments, true);
    }

    public static void arc(GuiGraphics g, float cx, float cy, float rIn, float rOut, double start, double end,
                           int innerColor, int outerColor, int segments, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, additive);
        segments = Math.max(1, segments);
        double step = (end - start) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = start + i * step;
            double a2 = start + (i + 1) * step;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            quad(vc, m,
                    cx + rIn * c1, cy + rIn * s1, innerColor,
                    cx + rOut * c1, cy + rOut * s1, outerColor,
                    cx + rOut * c2, cy + rOut * s2, outerColor,
                    cx + rIn * c2, cy + rIn * s2, innerColor);
        }
    }

    /**
     * Glowing arc with soft edges, used for progress indicators.
     */
    public static void glowArc(GuiGraphics g, float cx, float cy, float r, float width, double start, double end, int color, int segments) {
        int clear = color & 0x00FFFFFF;
        arc(g, cx, cy, Math.max(0, r - width), r, start, end, clear, color, segments, true);
        arc(g, cx, cy, r, r + width, start, end, color, clear, segments, true);
    }

    /**
     * Dashed ring of small diamonds, rotating with {@code rotation}.
     */
    public static void dottedRing(GuiGraphics g, float cx, float cy, float r, int count, float size, double rotation, int color) {
        for (int i = 0; i < count; i++) {
            double a = rotation + i * Math.PI * 2.0 / count;
            diamond(g, cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a), size, size, color, color, false);
        }
    }

    // ------------------------------------------------------------------
    // Diamonds and lines
    // ------------------------------------------------------------------

    public static void diamond(GuiGraphics g, float cx, float cy, float rx, float ry, int top, int bottom, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, additive);
        int mid = mix(top, bottom, 0.5f);
        quad(vc, m, cx, cy - ry, top, cx - rx, cy, mid, cx, cy + ry, bottom, cx + rx, cy, mid);
    }

    public static void diamondOutline(GuiGraphics g, float cx, float cy, float r, float t, int color, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, additive);
        float o = r;
        float i = Math.max(0, r - t * 1.41f);
        // top-right, bottom-right, bottom-left, top-left edges
        quad(vc, m, cx, cy - o, color, cx, cy - i, color, cx + i, cy, color, cx + o, cy, color);
        quad(vc, m, cx + o, cy, color, cx + i, cy, color, cx, cy + i, color, cx, cy + o, color);
        quad(vc, m, cx, cy + o, color, cx, cy + i, color, cx - i, cy, color, cx - o, cy, color);
        quad(vc, m, cx - o, cy, color, cx - i, cy, color, cx, cy - i, color, cx, cy - o, color);
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int c1, int c2, boolean additive) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * thickness / 2.0f;
        float ny = dx / len * thickness / 2.0f;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = buffer(g, additive);
        quad(vc, m, x1 + nx, y1 + ny, c1, x1 - nx, y1 - ny, c1, x2 - nx, y2 - ny, c2, x2 + nx, y2 + ny, c2);
    }

    /**
     * Horizontal divider that fades at both ends with a small gem in the middle.
     */
    public static void divider(GuiGraphics g, float x1, float x2, float y, int color) {
        float mid = (x1 + x2) / 2.0f;
        horizontalGradient(g, x1, y, mid, y + 1, color & 0x00FFFFFF, color, false);
        horizontalGradient(g, mid, y, x2, y + 1, color, color & 0x00FFFFFF, false);
        diamond(g, mid, y + 0.5f, 2.5f, 2.5f, color, color, false);
        diamond(g, mid, y + 0.5f, 1.0f, 1.0f, 0xFFFFFFFF, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------
    // Ornate panel
    // ------------------------------------------------------------------

    /**
     * Arcane panel: soft outer glow, deep night gradient, double frame, corner gems and
     * a spark of light running along the border.
     */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, int accent, float time) {
        float x2 = x + w;
        float y2 = y + h;
        float breathe = 0.75f + 0.25f * pulse(time, 0.12f);

        glowRect(g, x, y, x2, y2, 7.0f, alpha(accent, 0.30f * breathe));
        gradientRect(g, x, y, x2, y2, PANEL_TOP, PANEL_BOTTOM, false);
        // Inner light wash from the top
        gradientRect(g, x + 1, y + 1, x2 - 1, y + Math.min(18, h / 2), alpha(accent, 0.16f), accent & 0x00FFFFFF, true);

        outline(g, x, y, x2, y2, 1.0f, alpha(accent, 0.95f));
        outline(g, x + 2, y + 2, x2 - 2, y2 - 2, 1.0f, alpha(accent, 0.28f));

        // Running spark along the top and bottom edges
        float perimeterT = (time * 0.012f) % 1.0f;
        float sparkX = x + w * perimeterT;
        glowCircle(g, sparkX, y + 0.5f, 6.0f, alpha(FRAME_LIGHT, 0.55f), 12);
        glowCircle(g, x2 - w * perimeterT, y2 - 0.5f, 6.0f, alpha(FRAME_LIGHT, 0.45f), 12);

        // Corner gems
        cornerGem(g, x, y, accent);
        cornerGem(g, x2, y, accent);
        cornerGem(g, x, y2, accent);
        cornerGem(g, x2, y2, accent);
    }

    private static void cornerGem(GuiGraphics g, float cx, float cy, int accent) {
        diamond(g, cx, cy, 4.0f, 4.0f, mix(accent, 0xFFFFFFFF, 0.3f), accent, false);
        diamond(g, cx, cy, 1.6f, 1.6f, 0xFFFFFFFF, 0xFFFFFFFF, false);
        glowCircle(g, cx, cy, 7.0f, alpha(accent, 0.45f), 12);
    }

    // ------------------------------------------------------------------
    // Backdrop
    // ------------------------------------------------------------------

    /**
     * Night-sky backdrop with nebula light and twinkling stars, faded in by {@code fade}.
     */
    public static void starfield(GuiGraphics g, int width, int height, float time, float fade) {
        gradientRect(g, 0, 0, width, height, alpha(0xD0040818, fade), alpha(0xD00A0620, fade), false);

        float cx = width / 2.0f;
        float cy = height / 2.0f;
        glowCircle(g, cx, cy, Math.max(width, height) * 0.45f, alpha(0x40245BFF, fade), 40);
        glowCircle(g, width * 0.18f, height * 0.25f, height * 0.45f, alpha(0x283A1C8C, fade), 32);
        glowCircle(g, width * 0.85f, height * 0.78f, height * 0.5f, alpha(0x24157FB0, fade), 32);

        long seed = 0x5EEDL;
        for (int i = 0; i < 90; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float sx = ((seed >>> 33) % 10000) / 10000.0f * width;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float sy = ((seed >>> 33) % 10000) / 10000.0f * height;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float phase = ((seed >>> 33) % 1000) / 1000.0f * 6.28f;
            float twinkle = 0.25f + 0.75f * pulse(time * 0.6f + phase * 10.0f, 0.15f + (i % 5) * 0.03f);
            float size = (i % 7 == 0) ? 1.4f : 0.7f;
            int color = (i % 3 == 0) ? 0xFFB9D4FF : (i % 3 == 1 ? 0xFFFFFFFF : 0xFFD9C2FF);
            diamond(g, sx, sy, size, size, alpha(color, twinkle * fade), alpha(color, twinkle * fade), false);
            if (size > 1.0f) {
                glowCircle(g, sx, sy, 4.0f, alpha(color, 0.35f * twinkle * fade), 8);
            }
        }
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /**
     * Ring of enchanting-table runes rotating around a center.
     */
    public static void runeRing(GuiGraphics g, Font font, float cx, float cy, float radius, int count,
                                double rotation, int color, float scale, int seed) {
        if (((color >>> 24) & 0xFF) < 8) return;
        g.drawManaged(() -> {
            for (int i = 0; i < count; i++) {
                double angle = rotation + i * Math.PI * 2.0 / count;
                String rune = String.valueOf(RUNES.charAt(Math.floorMod(i * 7 + seed * 13, RUNES.length())));
                Component text = Component.literal(rune).withStyle(RUNE_STYLE);
                g.pose().pushPose();
                g.pose().translate(cx, cy, 0);
                g.pose().mulPose(Axis.ZP.rotation((float) (angle + Math.PI / 2.0)));
                g.pose().translate(0, -radius, 0);
                g.pose().scale(scale, scale, 1.0f);
                g.drawString(font, text, -font.width(text) / 2, -4, color, false);
                g.pose().popPose();
            }
        });
    }

    public static void centeredText(GuiGraphics g, Font font, Component text, float cx, float y, float scale, int color, boolean shadow) {
        if (((color >>> 24) & 0xFF) < 8) return;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, shadow);
        g.pose().popPose();
    }

    public static void centeredText(GuiGraphics g, Font font, String text, float cx, float y, float scale, int color, boolean shadow) {
        centeredText(g, font, Component.literal(text), cx, y, scale, color, shadow);
    }

    /**
     * Draws a glyph centered on (cx, cy) at the given scale.
     */
    public static void glyph(GuiGraphics g, Font font, String glyph, float cx, float cy, float scale, int color) {
        centeredText(g, font, glyph, cx, cy - 4.0f * scale, scale, color, true);
    }

    public static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }
}
