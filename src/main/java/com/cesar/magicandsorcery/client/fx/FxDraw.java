package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.client.hud.MagicRenderTypes;
import com.cesar.magicandsorcery.client.render.ShaderCompatHelper;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immediate-mode drawing of spell effects in the world.
 * Vertices are emitted relative to the camera (no float precision loss far from the origin).
 * Usage per frame: {@code if (FxDraw.begin(event)) { ...draw...; FxDraw.end(); }}
 */
public final class FxDraw {

    public static final RenderLevelStageEvent.Stage STAGE = RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;

    private static Matrix4f matrix;
    private static double camX, camY, camZ;
    private static Vector3f camLeft = new Vector3f(1, 0, 0);
    private static Vector3f camUp = new Vector3f(0, 1, 0);
    private static MultiBufferSource.BufferSource buffers;
    private static VertexConsumer vc;
    private static RenderType current;
    private static float partialTick;

    private FxDraw() {
    }

    /**
     * Prepares drawing for this render stage. Returns false when nothing should be drawn.
     */
    public static boolean begin(RenderLevelStageEvent event) {
        if (event.getStage() != STAGE || ShaderCompatHelper.isInvalidRenderPass()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Camera camera = event.getCamera();
        Vec3 pos = camera.getPosition();
        camX = pos.x;
        camY = pos.y;
        camZ = pos.z;
        camLeft = new Vector3f(camera.getLeftVector());
        camUp = new Vector3f(camera.getUpVector());
        matrix = event.getPoseStack().last().pose();
        buffers = mc.renderBuffers().bufferSource();
        partialTick = event.getPartialTick();
        current = null;
        glow();
        return true;
    }

    public static void end() {
        buffers.endBatch(MagicRenderTypes.WORLD_TRANSLUCENT);
        buffers.endBatch(MagicRenderTypes.WORLD_GLOW);
        current = null;
    }

    public static float partialTick() {
        return partialTick;
    }

    public static Vec3 camera() {
        return new Vec3(camX, camY, camZ);
    }

    /** Switch to additive light. */
    public static void glow() {
        use(MagicRenderTypes.WORLD_GLOW);
    }

    /** Switch to translucent matter (ice, frost). */
    public static void solid() {
        use(MagicRenderTypes.WORLD_TRANSLUCENT);
    }

    private static void use(RenderType type) {
        if (current != type) {
            current = type;
            vc = buffers.getBuffer(type);
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    public static void vertex(double x, double y, double z, float r, float g, float b, float a) {
        vc.vertex(matrix, (float) (x - camX), (float) (y - camY), (float) (z - camZ)).color(r, g, b, a).endVertex();
    }

    public static void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                            double x3, double y3, double z3, double x4, double y4, double z4,
                            float r, float g, float b, float a) {
        vertex(x1, y1, z1, r, g, b, a);
        vertex(x2, y2, z2, r, g, b, a);
        vertex(x3, y3, z3, r, g, b, a);
        vertex(x4, y4, z4, r, g, b, a);
    }

    /**
     * Camera-facing soft light: bright at the center, fading to nothing at {@code radius}.
     */
    public static void glow(double x, double y, double z, double radius, float r, float g, float b, float a) {
        if (a <= 0.003f || radius <= 0.0) return;
        int segments = radius > 1.5 ? 16 : 10;
        double step = Math.PI * 2.0 / segments;
        float lx = camLeft.x(), ly = camLeft.y(), lz = camLeft.z();
        float ux = camUp.x(), uy = camUp.y(), uz = camUp.z();
        for (int i = 0; i < segments; i++) {
            double c1 = Math.cos(i * step) * radius, s1 = Math.sin(i * step) * radius;
            double c2 = Math.cos((i + 1) * step) * radius, s2 = Math.sin((i + 1) * step) * radius;
            vertex(x, y, z, r, g, b, a);
            vertex(x + lx * c1 + ux * s1, y + ly * c1 + uy * s1, z + lz * c1 + uz * s1, r, g, b, 0);
            vertex(x + lx * c2 + ux * s2, y + ly * c2 + uy * s2, z + lz * c2 + uz * s2, r, g, b, 0);
            vertex(x, y, z, r, g, b, a);
        }
    }

    /**
     * Camera-facing soft smoke puff (use with {@link #solid()}): lit from above, shaded below,
     * dense in the middle and fading to nothing at the edge. {@code spin} rotates the shading irregularity.
     */
    public static void puff(double x, double y, double z, double radius, float r, float g, float b, float a, float spin) {
        if (a <= 0.003f || radius <= 0.0) return;
        int segments = 12;
        double step = Math.PI * 2.0 / segments;
        float lx = camLeft.x(), ly = camLeft.y(), lz = camLeft.z();
        float ux = camUp.x(), uy = camUp.y(), uz = camUp.z();
        float cr = r * 0.92f, cg = g * 0.92f, cb = b * 0.95f;
        for (int i = 0; i < segments; i++) {
            double a1 = i * step + spin, a2 = (i + 1) * step + spin;
            // Lumpy outline so puffs do not look like perfect discs
            double r1 = radius * (0.85 + 0.15 * Math.sin(a1 * 3.0 + spin * 2.0));
            double r2 = radius * (0.85 + 0.15 * Math.sin(a2 * 3.0 + spin * 2.0));
            double c1 = Math.cos(a1) * r1, s1 = Math.sin(a1) * r1;
            double c2 = Math.cos(a2) * r2, s2 = Math.sin(a2) * r2;
            // Light from above: upper edge brighter, lower edge darker
            float l1 = 0.78f + 0.22f * (float) Math.sin(a1);
            float l2 = 0.78f + 0.22f * (float) Math.sin(a2);
            vertex(x, y, z, cr, cg, cb, a);
            vertex(x + lx * c1 + ux * s1, y + ly * c1 + uy * s1, z + lz * c1 + uz * s1, r * l1, g * l1, b * l1, 0);
            vertex(x + lx * c2 + ux * s2, y + ly * c2 + uy * s2, z + lz * c2 + uz * s2, r * l2, g * l2, b * l2, 0);
            vertex(x, y, z, cr, cg, cb, a);
        }
    }

    /**
     * Camera-facing ribbon from A to B with a bright center line and soft edges.
     */
    public static void beam(double ax, double ay, double az, double bx, double by, double bz, double width,
                            float r, float g, float b, float aStart, float aEnd) {
        if (aStart <= 0.003f && aEnd <= 0.003f) return;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * 0.5 - camX, my = (ay + by) * 0.5 - camY, mz = (az + bz) * 0.5 - camZ;
        // side = dir x toCamera
        double sx = dy * mz - dz * my;
        double sy = dz * mx - dx * mz;
        double sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-6) return;
        double k = width * 0.5 / len;
        sx *= k;
        sy *= k;
        sz *= k;
        // Left half: edge -> center
        vertex(ax - sx, ay - sy, az - sz, r, g, b, 0);
        vertex(ax, ay, az, r, g, b, aStart);
        vertex(bx, by, bz, r, g, b, aEnd);
        vertex(bx - sx, by - sy, bz - sz, r, g, b, 0);
        // Right half: center -> edge
        vertex(ax, ay, az, r, g, b, aStart);
        vertex(ax + sx, ay + sy, az + sz, r, g, b, 0);
        vertex(bx + sx, by + sy, bz + sz, r, g, b, 0);
        vertex(bx, by, bz, r, g, b, aEnd);
    }

    public static void beam(Vec3 a, Vec3 b, double width, float r, float g, float bl, float alpha) {
        beam(a.x, a.y, a.z, b.x, b.y, b.z, width, r, g, bl, alpha, alpha);
    }

    /**
     * Flat horizontal ring with soft inner and outer edges.
     */
    public static void flatRing(double x, double y, double z, double radius, double width,
                                float r, float g, float b, float a, int segments) {
        if (a <= 0.003f || radius <= 0.0) return;
        double inner = Math.max(0.0, radius - width * 0.5);
        double outer = radius + width * 0.5;
        double step = Math.PI * 2.0 / segments;
        for (int i = 0; i < segments; i++) {
            double c1 = Math.cos(i * step), s1 = Math.sin(i * step);
            double c2 = Math.cos((i + 1) * step), s2 = Math.sin((i + 1) * step);
            vertex(x + c1 * inner, y, z + s1 * inner, r, g, b, 0);
            vertex(x + c1 * radius, y, z + s1 * radius, r, g, b, a);
            vertex(x + c2 * radius, y, z + s2 * radius, r, g, b, a);
            vertex(x + c2 * inner, y, z + s2 * inner, r, g, b, 0);

            vertex(x + c1 * radius, y, z + s1 * radius, r, g, b, a);
            vertex(x + c1 * outer, y, z + s1 * outer, r, g, b, 0);
            vertex(x + c2 * outer, y, z + s2 * outer, r, g, b, 0);
            vertex(x + c2 * radius, y, z + s2 * radius, r, g, b, a);
        }
    }

    /**
     * Ring in an arbitrary plane given by two perpendicular unit axes.
     */
    public static void ring(Vec3 center, Vec3 axisA, Vec3 axisB, double radius, double width,
                            float r, float g, float b, float a, int segments) {
        if (a <= 0.003f || radius <= 0.0) return;
        double inner = Math.max(0.0, radius - width * 0.5);
        double outer = radius + width * 0.5;
        double step = Math.PI * 2.0 / segments;
        for (int i = 0; i < segments; i++) {
            double c1 = Math.cos(i * step), s1 = Math.sin(i * step);
            double c2 = Math.cos((i + 1) * step), s2 = Math.sin((i + 1) * step);
            Vec3 d1 = axisA.scale(c1).add(axisB.scale(s1));
            Vec3 d2 = axisA.scale(c2).add(axisB.scale(s2));
            Vec3 i1 = center.add(d1.scale(inner)), m1 = center.add(d1.scale(radius)), o1 = center.add(d1.scale(outer));
            Vec3 i2 = center.add(d2.scale(inner)), m2 = center.add(d2.scale(radius)), o2 = center.add(d2.scale(outer));
            vertex(i1.x, i1.y, i1.z, r, g, b, 0);
            vertex(m1.x, m1.y, m1.z, r, g, b, a);
            vertex(m2.x, m2.y, m2.z, r, g, b, a);
            vertex(i2.x, i2.y, i2.z, r, g, b, 0);
            vertex(m1.x, m1.y, m1.z, r, g, b, a);
            vertex(o1.x, o1.y, o1.z, r, g, b, 0);
            vertex(o2.x, o2.y, o2.z, r, g, b, 0);
            vertex(m2.x, m2.y, m2.z, r, g, b, a);
        }
    }

    /**
     * Flat horizontal disc, bright in the middle and fading at the edge.
     */
    public static void flatDisc(double x, double y, double z, double radius, float r, float g, float b, float a, int segments) {
        if (a <= 0.003f || radius <= 0.0) return;
        double step = Math.PI * 2.0 / segments;
        for (int i = 0; i < segments; i++) {
            double c1 = Math.cos(i * step) * radius, s1 = Math.sin(i * step) * radius;
            double c2 = Math.cos((i + 1) * step) * radius, s2 = Math.sin((i + 1) * step) * radius;
            vertex(x, y, z, r, g, b, a);
            vertex(x + c1, y, z + s1, r, g, b, 0);
            vertex(x + c2, y, z + s2, r, g, b, 0);
            vertex(x, y, z, r, g, b, a);
        }
    }

    /**
     * Flat line on the ground (horizontal ribbon).
     */
    public static void groundLine(double x1, double z1, double x2, double z2, double y, double width,
                                  float r, float g, float b, float a) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4 || a <= 0.003f) return;
        double nx = -dz / len * width * 0.5, nz = dx / len * width * 0.5;
        vertex(x1 - nx, y, z1 - nz, r, g, b, 0);
        vertex(x1, y, z1, r, g, b, a);
        vertex(x2, y, z2, r, g, b, a);
        vertex(x2 - nx, y, z2 - nz, r, g, b, 0);
        vertex(x1, y, z1, r, g, b, a);
        vertex(x1 + nx, y, z1 + nz, r, g, b, 0);
        vertex(x2 + nx, y, z2 + nz, r, g, b, 0);
        vertex(x2, y, z2, r, g, b, a);
    }

    /**
     * Elongated octahedral crystal (ice shard) with per-face shading.
     */
    public static void crystal(double x, double y, double z, Quaternionf rotation, float length, float thickness,
                               float r, float g, float b, float a) {
        Vector3f top = rotation.transform(new Vector3f(0, length * 0.5f, 0));
        Vector3f bottom = rotation.transform(new Vector3f(0, -length * 0.5f, 0));
        Vector3f[] ring = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0;
            ring[i] = rotation.transform(new Vector3f((float) Math.cos(angle) * thickness, length * 0.08f, (float) Math.sin(angle) * thickness));
        }
        for (int i = 0; i < 4; i++) {
            Vector3f p1 = ring[i];
            Vector3f p2 = ring[(i + 1) % 4];
            float shade = 0.75f + 0.25f * ((i % 2 == 0) ? 1.0f : 0.4f);
            face(x, y, z, top, p1, p2, r * shade, g * shade, b * shade, a);
            face(x, y, z, bottom, p2, p1, r * shade * 0.8f, g * shade * 0.85f, b * shade, a);
        }
    }

    private static void face(double x, double y, double z, Vector3f p0, Vector3f p1, Vector3f p2,
                             float r, float g, float b, float a) {
        vertex(x + p0.x(), y + p0.y(), z + p0.z(), Math.min(1, r * 1.25f), Math.min(1, g * 1.25f), Math.min(1, b * 1.25f), a);
        vertex(x + p1.x(), y + p1.y(), z + p1.z(), r, g, b, a * 0.8f);
        vertex(x + p2.x(), y + p2.y(), z + p2.z(), r, g, b, a * 0.8f);
        vertex(x + p0.x(), y + p0.y(), z + p0.z(), Math.min(1, r * 1.25f), Math.min(1, g * 1.25f), Math.min(1, b * 1.25f), a);
    }

    /**
     * Two perpendicular unit vectors orthogonal to {@code dir}.
     */
    public static Vec3[] basis(Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 up = Math.abs(d.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 a = d.cross(up).normalize();
        Vec3 b = d.cross(a).normalize();
        return new Vec3[]{a, b};
    }
}
