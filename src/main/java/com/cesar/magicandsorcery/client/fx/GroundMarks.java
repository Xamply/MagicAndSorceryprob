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
 * Marks that spells leave on the ground, draped over the real terrain: they follow slopes, climb up the faces
 * of steps and skip gaps instead of floating flat. Every spell has its own kind of mark:
 * Bolt (Lichtenberg burn), Thundaja (great electric scar), falling sword (glowing cracks), Blizzard (frost),
 * Flash (rune seal) and Divine Sword (golden crescent).
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GroundMarks {

    public enum Kind {
        BOLT(1400, 70, new float[]{1.0f, 0.75f, 0.35f}),
        THUNDAJA(2000, 100, new float[]{0.55f, 0.75f, 1.0f}),
        CRATER(1800, 240, new float[]{1.0f, 0.45f, 0.12f}),
        FROST(320, 0, new float[]{0.75f, 0.92f, 1.0f}),
        RUNE(220, 160, new float[]{0.8f, 0.45f, 1.0f}),
        HOLY(240, 140, new float[]{1.0f, 0.78f, 0.35f});

        /** Total lifetime in ticks. */
        final int life;
        /** Ticks the mark glows before it is only a burn/stain. */
        final int glowTicks;
        final float[] glow;

        Kind(int life, int glowTicks, float[] glow) {
            this.life = life;
            this.glowTicks = glowTicks;
            this.glow = glow;
        }
    }

    private record Line(double x1, double z1, double x2, double z2, float width) {
    }

    private static final class Mark {
        final Kind kind;
        final double cx;
        final double cz;
        final double refY;
        final List<Line> lines = new ArrayList<>();
        /** Soft round stains: x, z, radius. */
        final List<double[]> stains = new ArrayList<>();
        final Map<Long, Double> heights = new HashMap<>();
        int age;

        Mark(Kind kind, Vec3 center) {
            this.kind = kind;
            this.cx = center.x;
            this.cz = center.z;
            this.refY = center.y;
        }
    }

    private static final int MAX_MARKS = 48;
    private static final double STEP = 0.1;
    private static final double LIFT = 0.02;
    private static final List<Mark> MARKS = new ArrayList<>();

    private GroundMarks() {
    }

    // ------------------------------------------------------------------
    // Spell marks
    // ------------------------------------------------------------------

    /** Bolt: a forking Lichtenberg figure burned around the impact. */
    public static void bolt(Vec3 at, long seed) {
        Mark m = new Mark(Kind.BOLT, at);
        Random r = new Random(seed * 13);
        int roots = 7 + r.nextInt(4);
        for (int k = 0; k < roots; k++) {
            fractal(m, at.x, at.z, r.nextDouble() * Math.PI * 2.0, 0.9 + r.nextDouble() * 0.6, 0.16f, 0, 4, 0.55f, r);
        }
        m.stains.add(new double[]{at.x, at.z, 1.2});
        add(m);
    }

    /** Thundaja: a huge branching scar and a scorched heart. */
    public static void thundaja(Vec3 at, long seed) {
        Mark m = new Mark(Kind.THUNDAJA, at);
        Random r = new Random(seed * 31);
        int roots = 12 + r.nextInt(5);
        for (int k = 0; k < roots; k++) {
            fractal(m, at.x, at.z, k * Math.PI * 2.0 / roots + (r.nextDouble() - 0.5) * 0.4, 1.6 + r.nextDouble() * 0.8, 0.3f, 0, 5, 0.6f, r);
        }
        m.stains.add(new double[]{at.x, at.z, 3.2});
        m.stains.add(new double[]{at.x, at.z, 1.6});
        add(m);
    }

    /** Falling sword: glowing radial cracks around the crater. */
    public static void crater(Vec3 at, long seed) {
        Mark m = new Mark(Kind.CRATER, at);
        Random r = new Random(seed * 7);
        int cracks = 9 + r.nextInt(4);
        for (int k = 0; k < cracks; k++) {
            double a = k * Math.PI * 2.0 / cracks + (r.nextDouble() - 0.5) * 0.3;
            double x = at.x + Math.cos(a) * 3.2, z = at.z + Math.sin(a) * 3.2;
            for (int seg = 0; seg < 4; seg++) {
                double len = 0.8 + r.nextDouble() * 0.7;
                a += (r.nextDouble() - 0.5) * 0.5;
                double nx = x + Math.cos(a) * len, nz = z + Math.sin(a) * len;
                m.lines.add(new Line(x, z, nx, nz, 0.35f * (1.0f - seg * 0.2f)));
                if (r.nextFloat() < 0.4f) {
                    double b = a + (r.nextBoolean() ? 0.8 : -0.8);
                    m.lines.add(new Line(nx, nz, nx + Math.cos(b) * len * 0.5, nz + Math.sin(b) * len * 0.5, 0.15f));
                }
                x = nx;
                z = nz;
            }
        }
        m.stains.add(new double[]{at.x, at.z, 4.5});
        add(m);
    }

    /** Blizzard: a patch of frost crystals where the tornado passes. */
    public static void frost(Vec3 at, long seed) {
        Mark m = new Mark(Kind.FROST, at);
        Random r = new Random(seed);
        int crystals = 5 + r.nextInt(4);
        for (int k = 0; k < crystals; k++) {
            double a = r.nextDouble() * Math.PI * 2.0, d = r.nextDouble() * 2.6;
            double cx = at.x + Math.cos(a) * d, cz = at.z + Math.sin(a) * d;
            double rot = r.nextDouble() * Math.PI;
            double size = 0.3 + r.nextDouble() * 0.45;
            for (int arm = 0; arm < 6; arm++) {
                double aa = rot + arm * Math.PI / 3.0;
                double ex = cx + Math.cos(aa) * size, ez = cz + Math.sin(aa) * size;
                m.lines.add(new Line(cx, cz, ex, ez, 0.05f));
                double bx = cx + Math.cos(aa) * size * 0.55, bz = cz + Math.sin(aa) * size * 0.55;
                for (int side = -1; side <= 1; side += 2) {
                    double ba = aa + side * 0.8;
                    m.lines.add(new Line(bx, bz, bx + Math.cos(ba) * size * 0.3, bz + Math.sin(ba) * size * 0.3, 0.035f));
                }
            }
        }
        m.stains.add(new double[]{at.x, at.z, 2.8});
        add(m);
    }

    /** Flash: a rune seal burned where the blink started or ended. */
    public static void rune(Vec3 at, double rotation) {
        Mark m = new Mark(Kind.RUNE, at);
        circle(m, at.x, at.z, 1.5, 0.1f, 40);
        circle(m, at.x, at.z, 1.1, 0.06f, 32);
        for (int tri = 0; tri < 2; tri++) {
            for (int i = 0; i < 3; i++) {
                double a0 = rotation + tri * Math.PI / 3.0 + i * Math.PI * 2.0 / 3.0;
                double a1 = a0 + Math.PI * 2.0 / 3.0;
                m.lines.add(new Line(at.x + Math.cos(a0) * 1.1, at.z + Math.sin(a0) * 1.1,
                        at.x + Math.cos(a1) * 1.1, at.z + Math.sin(a1) * 1.1, 0.05f));
            }
        }
        add(m);
    }

    /** Divine Sword: a golden crescent slashed across the ground in front of the caster. */
    public static void holySlash(Vec3 caster, float yaw) {
        Mark m = new Mark(Kind.HOLY, caster);
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        double base = Math.atan2(fz, fx);
        for (int band = 0; band < 3; band++) {
            double radius = 2.6 + band * 0.7;
            double span = 1.25 - band * 0.15;
            int segs = 18;
            for (int i = 0; i < segs; i++) {
                double a0 = base - span / 2.0 + span * i / segs;
                double a1 = base - span / 2.0 + span * (i + 1) / segs;
                float taper = (float) Math.sin(Math.PI * (i + 0.5) / segs);
                m.lines.add(new Line(caster.x + Math.cos(a0) * radius, caster.z + Math.sin(a0) * radius,
                        caster.x + Math.cos(a1) * radius, caster.z + Math.sin(a1) * radius, (0.22f - band * 0.05f) * taper + 0.02f));
            }
        }
        add(m);
    }

    private static void fractal(Mark m, double x, double z, double angle, double length, float width, int depth,
                                int maxDepth, float forkChance, Random r) {
        if (depth > maxDepth || length < 0.12) return;
        double a = angle + (r.nextDouble() - 0.5) * 0.7;
        double nx = x + Math.cos(a) * length, nz = z + Math.sin(a) * length;
        m.lines.add(new Line(x, z, nx, nz, width));
        fractal(m, nx, nz, a, length * 0.78, width * 0.8f, depth + 1, maxDepth, forkChance, r);
        if (r.nextFloat() < forkChance) {
            fractal(m, nx, nz, a + (r.nextBoolean() ? 0.7 : -0.7), length * 0.6, width * 0.6f, depth + 1, maxDepth, forkChance, r);
        }
    }

    private static void circle(Mark m, double cx, double cz, double radius, float width, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0 / segments, a1 = (i + 1) * Math.PI * 2.0 / segments;
            m.lines.add(new Line(cx + Math.cos(a0) * radius, cz + Math.sin(a0) * radius,
                    cx + Math.cos(a1) * radius, cz + Math.sin(a1) * radius, width));
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

    /** Height of the walkable surface at (x, z) near refY, or NaN where there is no ground close by. */
    private static double surface(Mark m, Level level, double x, double z) {
        long key = (((long) Math.round(x * 20.0)) << 32) ^ (Math.round(z * 20.0) & 0xFFFFFFFFL);
        Double cached = m.heights.get(key);
        if (cached != null) return cached;
        double result = Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int by = Mth.floor(m.refY + 2.5); by >= Mth.floor(m.refY - 4.0); by--) {
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
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = FxDraw.camera();
        float pt = FxDraw.partialTick();

        // Stains and burns (translucent), then the glowing part (additive) on top
        FxDraw.solid();
        for (Mark m : MARKS) {
            if (tooFar(m, cam)) continue;
            float t = m.age + pt;
            float fade = fadeOut(m, t);
            float[] stain = stainColor(m.kind);
            float stainAlpha = stain[3] * fade * Mth.clamp(t / 6.0f, 0.0f, 1.0f);
            for (double[] s : m.stains) {
                drawStain(m, mc.level, s[0], s[1], s[2], stain[0], stain[1], stain[2], stainAlpha);
            }
            float lineAlpha = stain[4] * fade;
            for (Line l : m.lines) {
                drawLine(m, mc.level, l, l.width() * 1.6, stain[0], stain[1], stain[2], lineAlpha);
            }
        }

        FxDraw.glow();
        for (Mark m : MARKS) {
            if (tooFar(m, cam)) continue;
            float t = m.age + pt;
            float heat = glowAmount(m, t);
            if (heat <= 0.01f) continue;
            float[] c = glowColor(m.kind, heat);
            for (Line l : m.lines) {
                drawLine(m, mc.level, l, l.width() * 2.6, c[0], c[1], c[2], 0.3f * heat);
                drawLine(m, mc.level, l, l.width() * 0.8, Math.min(1.0f, c[0] + 0.3f * heat), Math.min(1.0f, c[1] + 0.3f * heat),
                        Math.min(1.0f, c[2] + 0.3f * heat), heat);
            }
        }
        FxDraw.end();
    }

    private static boolean tooFar(Mark m, Vec3 cam) {
        double dx = cam.x - m.cx, dy = cam.y - m.refY, dz = cam.z - m.cz;
        return dx * dx + dy * dy + dz * dz > 96.0 * 96.0;
    }

    private static float fadeOut(Mark m, float t) {
        float remaining = m.kind.life - t;
        return Mth.clamp(remaining / 100.0f, 0.0f, 1.0f);
    }

    /** How strongly the mark still glows (cooling down after the spell). */
    private static float glowAmount(Mark m, float t) {
        Kind k = m.kind;
        if (k == Kind.FROST) {
            // Frost glitters faintly all its life
            return 0.25f * fadeOut(m, t) * Mth.clamp(t / 10.0f, 0.0f, 1.0f);
        }
        if (k.glowTicks <= 0) return 0.0f;
        float g = 1.0f - t / k.glowTicks;
        g = Mth.clamp(g, 0.0f, 1.0f);
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 0.9 + m.refY);
        return g * g * flicker;
    }

    /** Hot marks go from white through the spell color to a dull ember red as they cool. */
    private static float[] glowColor(Kind k, float heat) {
        float[] base = k.glow;
        if (k == Kind.BOLT || k == Kind.CRATER) {
            return new float[]{1.0f, Mth.clamp(0.2f + base[1] * heat * 1.3f, 0.0f, 1.0f), Mth.clamp(base[2] * heat * heat, 0.0f, 1.0f)};
        }
        return base;
    }

    /** Stain color: r, g, b, stain alpha, line alpha. */
    private static float[] stainColor(Kind k) {
        return switch (k) {
            case BOLT -> new float[]{0.08f, 0.06f, 0.05f, 0.35f, 0.75f};
            case THUNDAJA -> new float[]{0.06f, 0.06f, 0.09f, 0.5f, 0.8f};
            case CRATER -> new float[]{0.1f, 0.06f, 0.04f, 0.4f, 0.8f};
            case FROST -> new float[]{0.85f, 0.94f, 1.0f, 0.35f, 0.75f};
            case RUNE -> new float[]{0.18f, 0.08f, 0.25f, 0.0f, 0.55f};
            case HOLY -> new float[]{0.2f, 0.15f, 0.06f, 0.0f, 0.5f};
        };
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
                    // The riser sits just off the face, on the lower side (behind when climbing, ahead when dropping)
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

    /** A soft round stain made of small cells, each laid on the ground under it. */
    private static void drawStain(Mark m, Level level, double cx, double cz, double radius, float r, float g, float b, float a) {
        if (a <= 0.003f) return;
        double cell = 0.25;
        int n = (int) Math.ceil(radius / cell);
        for (int i = -n; i < n; i++) {
            for (int j = -n; j < n; j++) {
                double x0 = cx + i * cell, z0 = cz + j * cell;
                double mx = x0 + cell * 0.5, mz = z0 + cell * 0.5;
                double d = Math.sqrt((mx - cx) * (mx - cx) + (mz - cz) * (mz - cz)) / radius;
                if (d >= 1.0) continue;
                double y = surface(m, level, mx, mz);
                if (Double.isNaN(y)) continue;
                float ca = a * (float) (1.0 - d * d);
                double yy = y + LIFT * 0.75;
                FxDraw.quad(x0, yy, z0, x0, yy, z0 + cell, x0 + cell, yy, z0 + cell, x0 + cell, yy, z0, r, g, b, ca);
            }
        }
    }
}
