package com.cesar.magicandsorcery.client.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

public class RadialMenuRenderer {

    /**
     * Dibuja un sector de anillo (gajo circular entre dos radios y dos ángulos)
     * utilizando quads nativos de RenderType.gui() integrados con GuiGraphics.
     */
    public static void drawAnnularSector(GuiGraphics guiGraphics, Matrix4f matrix, float cx, float cy,
                                         float innerR, float outerR, double startAngle, double endAngle,
                                         int color, int segments) {
        if (segments < 1) segments = 1;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        double angleStep = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            double a1 = startAngle + i * angleStep;
            double a2 = startAngle + (i + 1) * angleStep;

            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2);
            float s2 = (float) Math.sin(a2);

            float x1In = cx + innerR * c1;
            float y1In = cy + innerR * s1;
            float x1Out = cx + outerR * c1;
            float y1Out = cy + outerR * s1;

            float x2In = cx + innerR * c2;
            float y2In = cy + innerR * s2;
            float x2Out = cx + outerR * c2;
            float y2Out = cy + outerR * s2;

            consumer.vertex(matrix, x1In, y1In, 0).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, x1Out, y1Out, 0).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, x2Out, y2Out, 0).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, x2In, y2In, 0).color(r, g, b, a).endVertex();
        }
    }

    /**
     * Dibuja un disco circular relleno utilizando RenderType.gui().
     */
    public static void drawFilledCircle(GuiGraphics guiGraphics, Matrix4f matrix, float cx, float cy,
                                        float radius, int color, int segments) {
        if (segments < 4) segments = 4;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        double angleStep = (2.0 * Math.PI) / segments;

        for (int i = 0; i < segments; i++) {
            double a1 = i * angleStep;
            double a2 = (i + 1) * angleStep;

            float x1 = cx + radius * (float) Math.cos(a1);
            float y1 = cy + radius * (float) Math.sin(a1);
            float x2 = cx + radius * (float) Math.cos(a2);
            float y2 = cy + radius * (float) Math.sin(a2);

            consumer.vertex(matrix, cx, cy, 0).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, cx, cy, 0).color(r, g, b, a).endVertex();
        }
    }

    /**
     * Dibuja un anillo concéntrico circular completo (360°).
     */
    public static void drawRing(GuiGraphics guiGraphics, Matrix4f matrix, float cx, float cy,
                                float innerR, float outerR, int color, int segments) {
        drawAnnularSector(guiGraphics, matrix, cx, cy, innerR, outerR, 0, 2.0 * Math.PI, color, segments);
    }

    /**
     * Dibuja una línea divisoria orientada con grosor configurable.
     */
    public static void drawRadialLine(GuiGraphics guiGraphics, Matrix4f matrix, float cx, float cy,
                                      float innerR, float outerR, double angle, float thickness, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float halfThick = thickness / 2.0f;
        float normalX = (float) -Math.sin(angle) * halfThick;
        float normalY = (float) Math.cos(angle) * halfThick;

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float p1X = cx + innerR * cos;
        float p1Y = cy + innerR * sin;
        float p2X = cx + outerR * cos;
        float p2Y = cy + outerR * sin;

        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        consumer.vertex(matrix, p1X + normalX, p1Y + normalY, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, p2X + normalX, p2Y + normalY, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, p2X - normalX, p2Y - normalY, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, p1X - normalX, p1Y - normalY, 0).color(r, g, b, a).endVertex();
    }

    /**
     * Dibuja una retícula / cursor romboide estilizado.
     */
    public static void drawReticle(GuiGraphics guiGraphics, Matrix4f matrix, float x, float y,
                                   float size, int fillColor, int borderColor) {
        drawDiamond(guiGraphics, matrix, x, y, size + 1.5f, borderColor);
        drawDiamond(guiGraphics, matrix, x, y, size, fillColor);
        drawFilledCircle(guiGraphics, matrix, x, y, 1.5f, 0xFFFFFFFF, 8);
    }

    private static void drawDiamond(GuiGraphics guiGraphics, Matrix4f matrix, float x, float y,
                                    float radius, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        consumer.vertex(matrix, x, y - radius, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x + radius, y, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x, y + radius, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x - radius, y, 0).color(r, g, b, a).endVertex();
    }

    public static final int TOTAL_SLOTS = 9;
    public static final float INNER_CANCEL_RADIUS = 32.0f;
    public static final float OUTER_RADIUS = 80.0f;
    public static final int LIBRARY_ITEM_HEIGHT = 18;
    public static final int LIBRARY_HEADER_HEIGHT = 24;
    public static final int LIBRARY_SCROLLBAR_WIDTH = 4;

    // Library Panel Geometry (Compact and anchored to the left)
    public static int getLibraryX(int screenWidth) {
        return 12;
    }

    public static int getLibraryWidth(int screenWidth) {
        return Math.min(118, Math.max(92, (int) (screenWidth * 0.22f)));
    }

    public static int getLibraryHeight(int screenHeight) {
        int availableH = Math.max(90, (screenHeight - 50) - 16);
        return Math.min(170, availableH);
    }

    public static int getLibraryY(int screenHeight) {
        int availableH = Math.max(90, (screenHeight - 50) - 16);
        int h = getLibraryHeight(screenHeight);
        return 16 + (availableH - h) / 2;
    }

    public static int getLibraryListY(int screenHeight) {
        return getLibraryY(screenHeight) + LIBRARY_HEADER_HEIGHT + 2;
    }

    public static int getLibraryListHeight(int screenHeight) {
        return getLibraryHeight(screenHeight) - LIBRARY_HEADER_HEIGHT - 6;
    }

    public static int getLibraryScrollBarX(int screenWidth) {
        return getLibraryX(screenWidth) + getLibraryWidth(screenWidth) - LIBRARY_SCROLLBAR_WIDTH - 3;
    }

    public static boolean isMouseInsideLibrary(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        int lx = getLibraryX(screenWidth);
        int ly = getLibraryY(screenHeight);
        int lw = getLibraryWidth(screenWidth);
        int lh = getLibraryHeight(screenHeight);
        return mouseX >= lx && mouseX <= lx + lw && mouseY >= ly && mouseY <= ly + lh;
    }

    // Radial Wheel is strictly centered on the screen as originally designed
    public static float getWheelCenterX(int screenWidth) {
        return screenWidth / 2.0f;
    }

    public static float getWheelCenterY(int screenHeight) {
        return screenHeight / 2.0f;
    }

    public static int calculateHoveredSlot(Minecraft mc) {
        if (mc.getWindow() == null) return -1;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        double windowW = Math.max(1.0, (double) mc.getWindow().getWidth());
        double windowH = Math.max(1.0, (double) mc.getWindow().getHeight());
        double mouseX = mc.mouseHandler.xpos() * (double) screenWidth / windowW;
        double mouseY = mc.mouseHandler.ypos() * (double) screenHeight / windowH;

        if (isMouseInsideLibrary(mouseX, mouseY, screenWidth, screenHeight)) {
            return -1;
        }

        float centerX = getWheelCenterX(screenWidth);
        float centerY = getWheelCenterY(screenHeight);

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER_CANCEL_RADIUS) return -1;
        if (dist > OUTER_RADIUS + 12.0) return -1;

        final double sectorAngle = (2.0 * Math.PI) / TOTAL_SLOTS;
        double mouseAngle = Math.atan2(dy, dx);
        double shiftedAngle = mouseAngle + Math.PI / 2.0;
        while (shiftedAngle < 0) shiftedAngle += 2.0 * Math.PI;
        while (shiftedAngle >= 2.0 * Math.PI) shiftedAngle -= 2.0 * Math.PI;

        return (int) Math.floor((shiftedAngle + sectorAngle / 2.0) / sectorAngle) % TOTAL_SLOTS;
    }
}
