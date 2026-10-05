package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Marks left on the ground by impacts, draped over the real terrain (they follow slopes and climb the faces of
 * steps instead of floating flat).
 * <ul>
 *   <li>Bolt: a short-lived white-hot burn that cools to red and fades in a few seconds.</li>
 *   <li>Thundaja / falling sword: blast scars of a huge explosion: an irregular scorched crater, hundreds of
 *   radial shock streaks, glowing zigzag fractures, embers, ash, and a hot rim that cools down.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GroundMarks {

    private enum Kind {
        BOLT(70),
        LIGHTNING_BLAST(400),
        FIRE_BLAST(400);

        final int life;

        Kind(int life) {
            this.life = life;
        }
    }

    private record Line(double x1, double z1, double x2, double z2, float width) {
    }

    /** A glowing ember or a fleck of ash: x, z, size, cool-down ticks. */
    private record Speck(double x, double z, float size, int heatTicks) {
    }

    private static final class Mark {
        final Kind kind;
        final double cx, cz, refY;
        final double radius;
        /** Scorch outline: radius multiplier per angle step (irregular edge). */
        double[] rim = new double[0];
        final List<Line> streaks = new ArrayList<>();
        final List<Line> cracks = new ArrayList<>();
        final List<Line> burns = new ArrayList<>();
        final List<Speck> embers = new ArrayList<>();
        final List<Speck> ash = new ArrayList<>();
        final Map<Long, Double> heights = new HashMap<>();
        int age;

        Mark(Kind kind, Vec3 center, double radius) {
            this.kind = kind;
            this.cx = center.x;
            this.cz = center.z;
            this.refY = center.y;
            this.radius = radius;
        }
    }

    private static final int MAX_MARKS = 24;
    private static final double STEP = 0.1;
    private static final double LIFT = 0.02;
    private static final List<Mark> MARKS = new ArrayList<>();

    private GroundMarks() {
    }

    // ------------------------------------------------------------------
    // Spell marks
    // ------------------------------------------------------------------

    /** Bolt: a white-hot forked burn that cools from white through orange to red in a few seconds. */
    public static void bolt(Vec3 at, long seed) {
        Mark m = new Mark(Kind.BOLT, at, 1.6);
        Random r = new Random(seed * 13);
        int roots = 7 + r.nextInt(4);
        for (int k = 0; k < roots; k++) {
            fork(m.burns, at.x, at.z, r.nextDouble() * Math.PI * 2.0, 0.9 + r.nextDouble() * 0.6, 0.16f, 0, r);
        }
        add(m);
    }

    /** Thundaja: the blast scar of a colossal lightning strike. */
    public static void thundaja(Vec3 at, long seed) {
        add(blast(Kind.LIGHTNING_BLAST, at, 6.5, new Random(seed * 31)));
    }

    /** Falling sword: the blast scar around its crater. */
    public static void crater(Vec3 at, long seed) {
        add(blast(Kind.FIRE_BLAST, at, 5.5, new Random(seed * 7)));
    }

    private static Mark blast(Kind kind, Vec3 at, double radius, Random r) {
        Mark m = new Mark(kind, at, radius);

        // Irregular scorched outline
        int steps = 64;
        m.rim = new double[steps];
        double p1 = r.nextDouble() * 6.28, p2 = r.nextDouble() * 6.28, p3 = r.nextDouble() * 6.28;
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2.0 / steps;
            m.rim[i] = 0.82 + 0.1 * Math.sin(a * 3 + p1) + 0.06 * Math.sin(a * 7 + p2) + 0.05 * Math.sin(a * 13 + p3)
                    + (r.nextDouble() - 0.5) * 0.06;
        }

        // Shock streaks: straight radial rays thrown outward by the blast, some reaching far past the edge
        int streaks = 90 + r.nextInt(40);
        for (int i = 0; i < streaks; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double from = radius * (0.12 + r.nextDouble() * 0.3);
            double to = radius * (0.7 + r.nextDouble() * (r.nextFloat() < 0.15f ? 0.9 : 0.45));
            float width = 0.05f + r.nextFloat() * (r.nextFloat() < 0.2f ? 0.28f : 0.12f);
            double drift = (r.nextDouble() - 0.5) * 0.04;
            double mid = (from + to) * 0.5;
            m.streaks.add(new Line(at.x + Math.cos(a) * from, at.z + Math.sin(a) * from,
                    at.x + Math.cos(a + drift) * mid, at.z + Math.sin(a + drift) * mid, width));
            m.streaks.add(new Line(at.x + Math.cos(a + drift) * mid, at.z + Math.sin(a + drift) * mid,
                    at.x + Math.cos(a + drift * 2) * to, at.z + Math.sin(a + drift * 2) * to, width * 0.55f));
        }

        // Fractures: zigzag cracks splitting the ground (never branching like roots)
        int cracks = 7 + r.nextInt(5);
        for (int i = 0; i < cracks; i++) {
            double a = i * Math.PI * 2.0 / cracks + (r.nextDouble() - 0.5) * 0.5;
            double dist = radius * 0.08;
            double x = at.x + Math.cos(a) * dist, z = at.z + Math.sin(a) * dist;
            int segments = 7 + r.nextInt(5);
            double segLen = radius * (0.8 + r.nextDouble() * 0.35) / segments;
            float width = 0.14f + r.nextFloat() * 0.08f;
            for (int k = 0; k < segments; k++) {
                double zig = (k % 2 == 0 ? 1 : -1) * (0.35 + r.nextDouble() * 0.35);
                double ca = a + zig;
                double nx = x + Math.cos(ca) * segLen, nz = z + Math.sin(ca) * segLen;
                m.cracks.add(new Line(x, z, nx, nz, width * (1.0f - 0.6f * k / segments)));
                x = nx;
                z = nz;
            }
        }

        // Embers scattered over the scar and ash thrown beyond it
        for (int i = 0; i < 70; i++) {
            double a = r.nextDouble() * Math.PI * 2.0, d = Math.sqrt(r.nextDouble()) * radius * 0.9;
            m.embers.add(new Speck(at.x + Math.cos(a) * d, at.z + Math.sin(a) * d, 0.04f + r.nextFloat() * 0.1f,
                    40 + r.nextInt(140)));
        }
        for (int i = 0; i < 110; i++) {
            double a = r.nextDouble() * Math.PI * 2.0, d = radius * (0.8 + r.nextDouble() * 0.8);
            m.ash.add(new Speck(at.x + Math.cos(a) * d, at.z + Math.sin(a) * d, 0.04f + r.nextFloat() * 0.12f, 0));
        }
        return m;
    }

    private static void fork(List<Line> out, double x, double z, double angle, double length, float width, int depth, Random r) {
        if (depth > 4 || length < 0.12) return;
        double a = angle + (r.nextDouble() - 0.5) * 0.7;
        double nx = x + Math.cos(a) * length, nz = z + Math.sin(a) * length;
        out.add(new Line(x, z, nx, nz, width));
        fork(out, nx, nz, a, length * 0.78, width * 0.8f, depth + 1, r);
        if (r.nextFloat() < 0.55f) {
            fork(out, nx, nz, a + (r.nextBoolean() ? 0.7 : -0.7), length * 0.6, width * 0.6f, depth + 1, r);
        }
    }

    private static void add(Mark m) {
        MARKS.add(m);
        while (MARKS.size() > MAX_MARKS) {
            MARKS.remove(0);
        }
    }

    // ------------------------------------------------------------------
    // Terrain
    // ------------------------------------------------------------------

    /** Height of the walkable surface at (x, z) near the mark, or NaN where there is no ground close by. */
    private static double surface(Mark m, Level level, double x, double z) {
        long key = (((long) Math.round(x * 20.0)) << 32) ^ (Math.round(z * 20.0) & 0xFFFFFFFFL);
        Double cached = m.heights.get(key);
        if (cached != null) return cached;
        double result = Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int by = Mth.floor(m.refY + 2.5); by >= Mth.floor(m.refY - 5.0); by--) {
            pos.set(bx, by, bz);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty() && level.getFluidState(pos.above()).isEmpty()) {
                result = by + fluid.getHeight(level, pos);
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            result = by + shape.max(Direction.Axis.Y);
            break;
        }
        m.heights.put(key, result);
        return result;
    }

    // ------------------------------------------------------------------
    // Tick & render
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            MARKS.clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Mark> it = MARKS.iterator();
        while (it.hasNext()) {
            Mark m = it.next();
            if (++m.age >= m.kind.life) {
                it.remove();
            } else if (m.age % 100 == 0 || (m.age < 40 && m.age % 5 == 0)) {
                // Terrain may change under the mark (the crater forming, broken blocks): read it again
                m.heights.clear();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (MARKS.isEmpty() || !FxDraw.begin(event)) return;
        Level level = Minecraft.getInstance().level;
        Vec3 cam = FxDraw.camera();
        float pt = FxDraw.partialTick();

        // Burned ground (translucent), then everything still glowing (additive) on top
        FxDraw.solid();
        for (Mark m : MARKS) {
            if (m.kind != Kind.BOLT && !tooFar(m, cam)) {
                renderBlastScorch(m, level, m.age + pt);
            }
        }
        FxDraw.glow();
        for (Mark m : MARKS) {
            if (tooFar(m, cam)) continue;
            if (m.kind == Kind.BOLT) {
                renderBoltBurn(m, level, m.age + pt);
            } else {
                renderBlastGlow(m, level, m.age + pt);
            }
        }
        FxDraw.end();
    }

    private static boolean tooFar(Mark m, Vec3 cam) {
        double dx = cam.x - m.cx, dy = cam.y - m.refY, dz = cam.z - m.cz;
        return dx * dx + dy * dy + dz * dz > 96.0 * 96.0;
    }

    private static float fadeOut(Mark m, float t) {
        return Mth.clamp((m.kind.life - t) / 100.0f, 0.0f, 1.0f);
    }

    /** Bolt: white-hot at first, cooling through orange to dull red, gone in ~3.5 s. */
    private static void renderBoltBurn(Mark m, Level level, float t) {
        float k = Mth.clamp(t / m.kind.life, 0.0f, 1.0f);
        float heat = 1.0f - k;
        float r = 1.0f;
        float g = Mth.clamp(0.25f + 0.75f * heat * heat, 0.0f, 1.0f);
        float b = Mth.clamp(heat * heat * heat, 0.0f, 1.0f);
        float alpha = heat * (0.7f + 0.3f * (float) Math.sin(t * 0.8));
        for (Line l : m.burns) {
            drawLine(m, level, l, l.width() * 2.5, r, g * 0.7f, b * 0.5f, 0.35f * alpha);
            drawLine(m, level, l, l.width(), r, g, b, alpha);
        }
        drawStain(m, level, m.cx, m.cz, 1.4, null, r, g, b, 0.45f * alpha);
    }

    private static void renderBlastScorch(Mark m, Level level, float t) {
        float fade = fadeOut(m, t);
        float appear = Mth.clamp(t / 4.0f, 0.0f, 1.0f);
        float a = fade * appear;
        boolean lightning = m.kind == Kind.LIGHTNING_BLAST;
        float sr = lightning ? 0.05f : 0.08f, sg = 0.05f, sb = lightning ? 0.08f : 0.04f;

        drawStain(m, level, m.cx, m.cz, m.radius, m.rim, sr, sg, sb, 0.78f * a);
        drawStain(m, level, m.cx, m.cz, m.radius * 0.45, null, sr * 0.6f, sg * 0.6f, sb * 0.6f, 0.6f * a);
        for (Line l : m.streaks) {
            drawLine(m, level, l, l.width(), sr, sg, sb, 0.55f * a);
        }
        for (Line l : m.cracks) {
            drawLine(m, level, l, l.width() * 1.3, 0.02f, 0.02f, 0.02f, 0.9f * a);
        }
        for (Speck s : m.ash) {
            drawSpeck(m, level, s, 0.06f, 0.055f, 0.05f, 0.6f * a);
        }
    }

    private static void renderBlastGlow(Mark m, Level level, float t) {
        boolean lightning = m.kind == Kind.LIGHTNING_BLAST;
        float fade = fadeOut(m, t);

        // Fractures glow with heat (blue-white for lightning, lava for the sword) and cool down in ~5 s
        float crackHeat = Mth.clamp(1.0f - t / 100.0f, 0.0f, 1.0f);
        crackHeat *= crackHeat * (0.85f + 0.15f * (float) Math.sin(t * 0.7 + m.cx));
        if (crackHeat > 0.01f) {
            float[] c = lightning ? new float[]{0.55f + 0.4f * crackHeat, 0.75f + 0.25f * crackHeat, 1.0f}
                    : new float[]{1.0f, 0.3f + 0.6f * crackHeat * crackHeat, 0.08f + 0.5f * crackHeat * crackHeat * crackHeat};
            for (Line l : m.cracks) {
                drawLine(m, level, l, l.width() * 3.0, c[0], c[1], c[2], 0.3f * crackHeat);
                drawLine(m, level, l, l.width() * 0.7, Math.min(1.0f, c[0] + 0.3f), Math.min(1.0f, c[1] + 0.3f),
                        Math.min(1.0f, c[2] + 0.3f), crackHeat);
            }
            // Hot rim of the blast
            float rimHeat = Mth.clamp(1.0f - t / 60.0f, 0.0f, 1.0f);
            if (rimHeat > 0.01f) {
                drawRim(m, level, 0.9, 0.35, c[0], c[1], c[2], 0.5f * rimHeat * rimHeat);
            }
        }
        // The heart of the blast stays hot a little longer
        float coreHeat = Mth.clamp(1.0f - t / 140.0f, 0.0f, 1.0f);
        if (coreHeat > 0.01f) {
            float[] c = lightning ? new float[]{0.6f, 0.75f, 1.0f} : new float[]{1.0f, 0.4f, 0.1f};
            drawStain(m, level, m.cx, m.cz, m.radius * 0.35, null, c[0], c[1], c[2], 0.35f * coreHeat * coreHeat);
        }
        // Embers flicker and die out one by one
        for (Speck s : m.embers) {
            float h = Mth.clamp(1.0f - t / s.heatTicks(), 0.0f, 1.0f);
            if (h <= 0.01f) continue;
            float flicker = 0.6f + 0.4f * (float) Math.sin(t * 1.3 + s.x() * 7.0 + s.z() * 3.0);
            float g = lightning ? 0.75f + 0.25f * h : 0.25f + 0.6f * h * h;
            float b = lightning ? 1.0f : 0.05f + 0.4f * h * h * h;
            float rr = lightning ? 0.6f + 0.4f * h : 1.0f;
            drawSpeck(m, level, s, rr, g, b, h * flicker * fade);
        }
    }

    // ------------------------------------------------------------------
    // Terrain-draped primitives
    // ------------------------------------------------------------------

    /**
     * A ground line split into short pieces, each laid on the surface beneath it. Where the ground steps up or
     * down between two pieces, the line climbs the block face instead of cutting through the air.
     */
    private static void drawLine(Mark m, Level level, Line l, double width, float r, float g, float b, float a) {
        if (a <= 0.003f) return;
        double dx = l.x2() - l.x1(), dz = l.z2() - l.z1();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return;
        int steps = Math.max(1, (int) Math.ceil(len / STEP));
        double nx = -dz / len * width * 0.5, nz = dx / len * width * 0.5;
        double px = l.x1(), pz = l.z1();
        double py = surface(m, level, px, pz);
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double x = l.x1() + dx * f, z = l.z1() + dz * f;
            double y = surface(m, level, x, z);
            if (!Double.isNaN(py) && !Double.isNaN(y)) {
                if (Math.abs(y - py) < 0.3) {
                    strip(px, py + LIFT, pz, x, y + LIFT, z, nx, nz, r, g, b, a);
                } else {
                    // Step: run flat to the edge, climb the block face, continue on the new level
                    double mx = (px + x) * 0.5, mz = (pz + z) * 0.5;
                    double low = Math.min(py, y), high = Math.max(py, y);
                    strip(px, py + LIFT, pz, mx, py + LIFT, mz, nx, nz, r, g, b, a);
                    double side = y > py ? -LIFT : LIFT;
                    double fx = mx + dx / len * side, fz = mz + dz / len * side;
                    FxDraw.quad(fx - nx, low + LIFT, fz - nz, fx + nx, low + LIFT, fz + nz,
                            fx + nx, high + LIFT, fz + nz, fx - nx, high + LIFT, fz - nz, r, g, b, a);
                    strip(mx, y + LIFT, mz, x, y + LIFT, z, nx, nz, r, g, b, a);
                }
            }
            px = x;
            pz = z;
            py = y;
        }
    }

    private static void strip(double x1, double y1, double z1, double x2, double y2, double z2,
                              double nx, double nz, float r, float g, float b, float a) {
        FxDraw.vertex(x1 - nx, y1, z1 - nz, r, g, b, a);
        FxDraw.vertex(x1 + nx, y1, z1 + nz, r, g, b, a);
        FxDraw.vertex(x2 + nx, y2, z2 + nz, r, g, b, a);
        FxDraw.vertex(x2 - nx, y2, z2 - nz, r, g, b, a);
    }

    /**
     * A soft round stain made of small cells, each laid on the ground under it.
     * {@code rim}: optional irregular outline (radius multiplier per angle).
     */
    private static void drawStain(Mark m, Level level, double cx, double cz, double radius, double[] rim,
                                  float r, float g, float b, float a) {
        if (a <= 0.003f) return;
        double cell = 0.25;
        int n = (int) Math.ceil(radius / cell);
        for (int i = -n; i < n; i++) {
            for (int j = -n; j < n; j++) {
                double x0 = cx + i * cell, z0 = cz + j * cell;
                double mx = x0 + cell * 0.5, mz = z0 + cell * 0.5;
                double ddx = mx - cx, ddz = mz - cz;
                double edge = radius;
                if (rim != null && rim.length > 0) {
                    double ang = Math.atan2(ddz, ddx);
                    double f = (ang < 0 ? ang + Math.PI * 2.0 : ang) / (Math.PI * 2.0) * rim.length;
                    int i0 = (int) f % rim.length, i1 = (i0 + 1) % rim.length;
                    double frac = f - Math.floor(f);
                    edge = radius * (rim[i0] * (1.0 - frac) + rim[i1] * frac);
                }
                double d = Math.sqrt(ddx * ddx + ddz * ddz) / edge;
                if (d >= 1.0) continue;
                double y = surface(m, level, mx, mz);
                if (Double.isNaN(y)) continue;
                float ca = a * (float) (1.0 - d * d * d);
                double yy = y + LIFT * 0.75;
                FxDraw.quad(x0, yy, z0, x0, yy, z0 + cell, x0 + cell, yy, z0 + cell, x0 + cell, yy, z0, r, g, b, ca);
            }
        }
    }

    /** Glowing ring hugging the blast's irregular outline. */
    private static void drawRim(Mark m, Level level, double scale, double width, float r, float g, float b, float a) {
        int n = m.rim.length;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2.0 / n, a1 = (i + 1) * Math.PI * 2.0 / n;
            double r0 = m.radius * m.rim[i] * scale, r1 = m.radius * m.rim[(i + 1) % n] * scale;
            drawLine(m, level, new Line(m.cx + Math.cos(a0) * r0, m.cz + Math.sin(a0) * r0,
                    m.cx + Math.cos(a1) * r1, m.cz + Math.sin(a1) * r1, (float) width), width, r, g, b, a);
        }
    }

    private static void drawSpeck(Mark m, Level level, Speck s, float r, float g, float b, float a) {
        if (a <= 0.003f) return;
        double y = surface(m, level, s.x(), s.z());
        if (Double.isNaN(y)) return;
        double h = s.size() * 0.5, yy = y + LIFT * 1.2;
        FxDraw.quad(s.x() - h, yy, s.z() - h, s.x() - h, yy, s.z() + h, s.x() + h, yy, s.z() + h, s.x() + h, yy, s.z() - h, r, g, b, a);
    }
}
