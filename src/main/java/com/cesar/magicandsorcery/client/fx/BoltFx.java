package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Bolt visuals: a fractal, branching golden lightning strike that re-strikes three times,
 * then leaves an afterglow, crawling ground arcs, static on the victims and bouncing sparks.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BoltFx {

    private static final int LIFE = 18;
    private static final int[] STROKE_TICKS = {0, 3, 6};
    private static final float[] STROKE_POWER = {1.0f, 0.8f, 0.6f};

    // Golden storm palette
    private static final float[] OUTER = {1.0f, 0.55f, 0.15f};
    private static final float[] MID = {1.0f, 0.88f, 0.42f};
    private static final int SPARK_COLOR = 0xFFE9A0;

    /** One drawn polyline of a bolt (trunk, branch or twig). */
    private record Strand(List<Vec3> points, float width) {
    }

    private static final class Strike {
        final Vec3 start;
        final Vec3 end;
        final long seed;
        final boolean hitEntity;
        final List<Vec3> chain;
        final Double groundY;
        final List<List<Strand>> strokes = new ArrayList<>();
        final List<List<List<Strand>>> chainStrokes = new ArrayList<>();
        int age;

        Strike(Vec3 start, Vec3 end, long seed, boolean hitEntity, List<Vec3> chain, Double groundY) {
            this.start = start;
            this.end = end;
            this.seed = seed;
            this.hitEntity = hitEntity;
            this.chain = chain;
            this.groundY = groundY;
            for (int s = 0; s < STROKE_TICKS.length; s++) {
                strokes.add(buildBolt(start, end, new Random(seed + s * 7919L), 1.0f, true));
            }
            Vec3 from = end;
            for (int i = 0; i < chain.size(); i++) {
                List<List<Strand>> linkStrokes = new ArrayList<>();
                for (int s = 0; s < STROKE_TICKS.length; s++) {
                    linkStrokes.add(buildBolt(from, chain.get(i), new Random(seed * 31 + i * 104729L + s), 0.6f, false));
                }
                chainStrokes.add(linkStrokes);
                from = chain.get(i);
            }
        }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    private BoltFx() {
    }

    public static void add(Vec3 start, Vec3 end, long seed, boolean hitEntity, List<Vec3> chain) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Double groundY = null;
        BlockHitResult down = mc.level.clip(new ClipContext(end.add(0, 0.3, 0), end.add(0, -1.6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
        if (down.getType() == HitResult.Type.BLOCK) {
            groundY = down.getLocation().y;
        }
        STRIKES.add(new Strike(start, end, seed, hitEntity, new ArrayList<>(chain), groundY));

        // Impact debris
        FxParticles.sparkBurst(end.x, end.y, end.z, 30, 0.5, SPARK_COLOR, mc.level.random);
        for (Vec3 c : chain) {
            FxParticles.sparkBurst(c.x, c.y, c.z, 12, 0.35, SPARK_COLOR, mc.level.random);
        }
        for (int i = 0; i < 6; i++) {
            mc.level.addParticle(ParticleTypes.SMOKE, end.x + (mc.level.random.nextDouble() - 0.5) * 0.6,
                    end.y + 0.1, end.z + (mc.level.random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
        }
        // Little flare at the catalyst
        for (int i = 0; i < 6; i++) {
            FxParticles.spawn(FxParticles.Kind.GLOW).at(start.x, start.y, start.z)
                    .vel((mc.level.random.nextDouble() - 0.5) * 0.08, mc.level.random.nextDouble() * 0.05, (mc.level.random.nextDouble() - 0.5) * 0.08)
                    .color(MID[0], MID[1], MID[2]).size(0.12f).life(10).physics(0, 0.9, 0).noCollide();
        }
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private static List<Strand> buildBolt(Vec3 a, Vec3 b, Random random, float width, boolean branches) {
        List<Strand> strands = new ArrayList<>();
        double length = a.distanceTo(b);
        List<Vec3> trunk = jagged(a, b, Math.min(1.6, length * 0.12), random);
        strands.add(new Strand(trunk, width));
        if (!branches || trunk.size() < 6) return strands;

        Vec3 dir = b.subtract(a).normalize();
        int branchCount = 3 + random.nextInt(3);
        for (int i = 0; i < branchCount; i++) {
            int idx = 2 + random.nextInt(trunk.size() - 4);
            Vec3 root = trunk.get(idx);
            double remaining = root.distanceTo(b);
            Vec3 branchDir = deviate(dir, 0.9, random);
            double branchLen = remaining * (0.18 + random.nextDouble() * 0.25) + 0.6;
            List<Vec3> branch = jagged(root, root.add(branchDir.scale(branchLen)), branchLen * 0.18, random);
            strands.add(new Strand(branch, width * 0.5f));

            if (random.nextFloat() < 0.6f && branch.size() > 3) {
                Vec3 twigRoot = branch.get(branch.size() / 2);
                Vec3 twigDir = deviate(branchDir, 1.1, random);
                double twigLen = branchLen * (0.3 + random.nextDouble() * 0.3);
                strands.add(new Strand(jagged(twigRoot, twigRoot.add(twigDir.scale(twigLen)), twigLen * 0.2, random), width * 0.28f));
            }
        }
        return strands;
    }

    /** Midpoint-displacement lightning path. */
    static List<Vec3> jagged(Vec3 a, Vec3 b, double displacement, Random random) {
        List<Vec3> points = new ArrayList<>();
        points.add(a);
        subdivide(a, b, displacement, random, points);
        points.add(b);
        return points;
    }

    private static void subdivide(Vec3 a, Vec3 b, double displacement, Random random, List<Vec3> out) {
        if (a.distanceToSqr(b) < 0.09 || displacement < 0.015) return;
        Vec3[] basis = FxDraw.basis(b.subtract(a));
        double angle = random.nextDouble() * Math.PI * 2.0;
        double amount = (random.nextDouble() - 0.5) * 2.0 * displacement;
        Vec3 mid = a.lerp(b, 0.5)
                .add(basis[0].scale(Math.cos(angle) * amount))
                .add(basis[1].scale(Math.sin(angle) * amount));
        subdivide(a, mid, displacement * 0.55, random, out);
        out.add(mid);
        subdivide(mid, b, displacement * 0.55, random, out);
    }

    private static Vec3 deviate(Vec3 dir, double amount, Random random) {
        Vec3[] basis = FxDraw.basis(dir);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double spread = amount * (0.4 + random.nextDouble() * 0.6);
        return dir.add(basis[0].scale(Math.cos(angle) * spread)).add(basis[1].scale(Math.sin(angle) * spread)).normalize();
    }

    // ------------------------------------------------------------------
    // Tick & render
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STRIKES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STRIKES.clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Strike> it = STRIKES.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            s.age++;
            // Re-strikes throw more sparks
            for (int k = 1; k < STROKE_TICKS.length; k++) {
                if (s.age == STROKE_TICKS[k]) {
                    FxParticles.sparkBurst(s.end.x, s.end.y, s.end.z, 10, 0.35, SPARK_COLOR, mc.level.random);
                }
            }
            if (s.age < 10 && mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, s.end.x, s.end.y + 0.2, s.end.z,
                        (mc.level.random.nextDouble() - 0.5) * 0.3, 0.1, (mc.level.random.nextDouble() - 0.5) * 0.3);
            }
            if (s.age >= LIFE) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (STRIKES.isEmpty() || !FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();
        for (Strike s : STRIKES) {
            renderStrike(s, s.age + pt);
        }
        FxDraw.end();
    }

    /** Intensity of the newest stroke at time t, and which stroke it is. */
    private static int activeStroke(float t) {
        int idx = 0;
        for (int k = 0; k < STROKE_TICKS.length; k++) {
            if (t >= STROKE_TICKS[k]) idx = k;
        }
        return idx;
    }

    private static float strokeIntensity(float t, int stroke) {
        float since = t - STROKE_TICKS[stroke];
        return STROKE_POWER[stroke] * (float) Math.exp(-since * 0.55f);
    }

    private static void renderStrike(Strike s, float t) {
        float life = Math.max(0.0f, 1.0f - t / LIFE);
        int stroke = activeStroke(t);
        float power = strokeIntensity(t, stroke);
        // Flicker between strokes
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 9.0f);
        float intensity = Math.min(1.0f, power * flicker);

        // Afterglow of the first path, fading over the whole life
        drawStrands(s.strokes.get(0), 0.18f * life, 0.6f);
        // Current stroke at full strength
        drawStrands(s.strokes.get(stroke), intensity, 1.0f);

        // Chain arcs (each link fires one tick after the previous one)
        for (int i = 0; i < s.chainStrokes.size(); i++) {
            float lt = t - (i + 1);
            if (lt < 0) continue;
            int ls = activeStroke(lt);
            drawStrands(s.chainStrokes.get(i).get(ls), Math.min(1.0f, strokeIntensity(lt, ls) * flicker), 1.0f);
        }

        // Catalyst flare
        FxDraw.glow(s.start.x, s.start.y, s.start.z, 0.8 * intensity + 0.2, MID[0], MID[1], MID[2], 0.8f * intensity);
        FxDraw.glow(s.start.x, s.start.y, s.start.z, 0.25, 1, 1, 1, intensity);

        // Impact flash
        FxDraw.glow(s.end.x, s.end.y, s.end.z, 1.0 + 2.2 * intensity, OUTER[0], OUTER[1], OUTER[2], 0.55f * intensity);
        FxDraw.glow(s.end.x, s.end.y, s.end.z, 0.3 + 0.8 * intensity, 1, 1, 1, 0.9f * intensity);

        // Shockwave ring around the bolt axis
        float ringT = Math.min(1.0f, t / 10.0f);
        if (ringT < 1.0f) {
            Vec3[] basis = FxDraw.basis(s.end.subtract(s.start));
            FxDraw.ring(s.end, basis[0], basis[1], 0.2 + ringT * 2.4, 0.35, MID[0], MID[1], MID[2], 0.7f * (1.0f - ringT), 28);
        }

        // Ground: expanding scorch ring and arcs crawling over the surface
        if (s.groundY != null) {
            double gy = s.groundY + 0.04;
            float gT = Math.min(1.0f, t / 12.0f);
            FxDraw.flatRing(s.end.x, gy, s.end.z, 0.3 + gT * 3.2, 0.5, OUTER[0], OUTER[1], OUTER[2], 0.6f * (1.0f - gT), 32);
            FxDraw.flatDisc(s.end.x, gy, s.end.z, 1.6, 1.0f, 0.7f, 0.3f, 0.35f * life, 20);
            if (t < 13) {
                Random arcRandom = new Random(s.seed ^ ((long) (t * 2) * 341873128712L));
                float arcAlpha = 0.9f * (1.0f - t / 13.0f);
                for (int a = 0; a < 6; a++) {
                    double angle = arcRandom.nextDouble() * Math.PI * 2.0;
                    double length = 0.8 + arcRandom.nextDouble() * 2.2;
                    Vec3 from = new Vec3(s.end.x, gy, s.end.z);
                    Vec3 to = from.add(Math.cos(angle) * length, 0, Math.sin(angle) * length);
                    List<Vec3> path = jagged(from, to, length * 0.25, arcRandom);
                    for (int p = 0; p + 1 < path.size(); p++) {
                        Vec3 p0 = path.get(p), p1 = path.get(p + 1);
                        FxDraw.groundLine(p0.x, p0.z, p1.x, p1.z, gy, 0.12, MID[0], MID[1], MID[2], arcAlpha);
                        FxDraw.groundLine(p0.x, p0.z, p1.x, p1.z, gy + 0.005, 0.04, 1, 1, 1, arcAlpha);
                    }
                }
            }
        }

        // Static crackling over everyone that was struck
        if (t < 14) {
            List<Vec3> victims = new ArrayList<>();
            if (s.hitEntity) victims.add(s.end);
            victims.addAll(s.chain);
            Random staticRandom = new Random(s.seed * 7 + (long) (t * 3));
            float staticAlpha = 1.0f - t / 14.0f;
            for (Vec3 v : victims) {
                for (int k = 0; k < 3; k++) {
                    Vec3 a = v.add((staticRandom.nextDouble() - 0.5) * 1.0, (staticRandom.nextDouble() - 0.5) * 1.4, (staticRandom.nextDouble() - 0.5) * 1.0);
                    Vec3 b = v.add((staticRandom.nextDouble() - 0.5) * 1.0, (staticRandom.nextDouble() - 0.5) * 1.4, (staticRandom.nextDouble() - 0.5) * 1.0);
                    List<Vec3> path = jagged(a, b, 0.2, staticRandom);
                    for (int p = 0; p + 1 < path.size(); p++) {
                        FxDraw.beam(path.get(p), path.get(p + 1), 0.08, MID[0], MID[1], MID[2], staticAlpha);
                    }
                }
                FxDraw.glow(v.x, v.y, v.z, 1.2, OUTER[0], OUTER[1], OUTER[2], 0.3f * staticAlpha);
            }
        }
    }

    private static void drawStrands(List<Strand> strands, float intensity, float widthScale) {
        if (intensity <= 0.01f) return;
        for (Strand strand : strands) {
            List<Vec3> pts = strand.points();
            float w = strand.width() * widthScale;
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                FxDraw.beam(a, b, 0.9 * w, OUTER[0], OUTER[1], OUTER[2], 0.14f * intensity);
                FxDraw.beam(a, b, 0.26 * w, MID[0], MID[1], MID[2], 0.6f * intensity);
                FxDraw.beam(a, b, 0.07 * w, 1.0f, 1.0f, 1.0f, intensity);
            }
            // Bright knots along the trunk
            if (strand.width() >= 1.0f) {
                for (int i = 2; i < pts.size(); i += 4) {
                    Vec3 p = pts.get(i);
                    FxDraw.glow(p.x, p.y, p.z, 0.4 * w, MID[0], MID[1], MID[2], 0.18f * intensity);
                }
            }
        }
    }
}
