package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.spell.spells.BlizzardSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Blizzard visuals: a wandering arcane frost tornado. A snowflake sigil spins on the ground, layered
 * aurora ribbons twist up the funnel around a column of light, a wall of snow orbits it, ice crystals ride
 * the spiral and are hurled out of the top (bouncing and shattering), and frost arcs jump between them.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TornadoFx {

    private static final class Shard {
        double h, angle, radial, rise, spinSpeed;
        float length, thickness;
        double x, y, z, px, py, pz;
        final Quaternionf rot = new Quaternionf();
        final Quaternionf prevRot = new Quaternionf();
        float sx, sy, sz;
    }

    private record FrostArc(Vec3 a, Vec3 b, long seed, int born) {
    }

    private static final class Tornado {
        final Vec3 origin;
        final long seed;
        final int duration;
        final List<Shard> shards = new ArrayList<>();
        final List<FrostArc> arcs = new ArrayList<>();
        int age;

        Tornado(Vec3 origin, long seed, int duration) {
            this.origin = origin;
            this.seed = seed;
            this.duration = duration;
        }

        Vec3 center(float t) {
            return BlizzardSpell.center(origin, seed, t);
        }
    }

    private static final List<Tornado> TORNADOES = new ArrayList<>();

    private TornadoFx() {
    }

    public static void add(Vec3 origin, int duration, long seed) {
        TORNADOES.add(new Tornado(origin, seed, duration));
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TORNADOES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TORNADOES.clear();
            return;
        }
        if (mc.isPaused()) return;

        RandomSource random = mc.level.random;
        Iterator<Tornado> it = TORNADOES.iterator();
        while (it.hasNext()) {
            Tornado tornado = it.next();
            tornado.age++;
            float t = tornado.age;
            Vec3 c = tornado.center(t);
            float height = Math.max(0.5f, BlizzardSpell.height(t));
            float strength = BlizzardSpell.intensity(t);

            tickShards(tornado, c, height, strength, random);
            spawnAmbient(mc, c, height, strength, random);

            if (tornado.age % 7 == 0 && tornado.shards.size() >= 2 && strength > 0.4f) {
                Shard a = tornado.shards.get(random.nextInt(tornado.shards.size()));
                Shard b = tornado.shards.get(random.nextInt(tornado.shards.size()));
                if (a != b) {
                    tornado.arcs.add(new FrostArc(new Vec3(a.x, a.y, a.z), new Vec3(b.x, b.y, b.z), random.nextLong(), tornado.age));
                }
            }
            tornado.arcs.removeIf(arc -> tornado.age - arc.born() > 4);

            if (tornado.age >= tornado.duration) {
                for (Shard shard : tornado.shards) fling(shard, c, random);
                it.remove();
            }
        }
    }

    private static void tickShards(Tornado tornado, Vec3 c, float height, float strength, RandomSource random) {
        if (strength > 0.3f && tornado.age % 2 == 0 && tornado.shards.size() < 36) {
            Shard s = new Shard();
            s.h = random.nextDouble() * 0.6;
            s.angle = random.nextDouble() * Math.PI * 2.0;
            s.radial = 0.9 + random.nextDouble() * 0.4;
            s.rise = 0.05 + random.nextDouble() * 0.07;
            s.spinSpeed = 0.18 + random.nextDouble() * 0.14;
            s.length = 0.25f + random.nextFloat() * 0.35f;
            s.thickness = s.length * (0.22f + random.nextFloat() * 0.12f);
            s.rot.rotateXYZ(random.nextFloat() * 6.28f, random.nextFloat() * 6.28f, random.nextFloat() * 6.28f);
            s.sx = (random.nextFloat() - 0.5f) * 0.4f;
            s.sy = (random.nextFloat() - 0.5f) * 0.4f;
            s.sz = (random.nextFloat() - 0.5f) * 0.4f;
            place(s, c, height);
            s.px = s.x;
            s.py = s.y;
            s.pz = s.z;
            s.prevRot.set(s.rot);
            tornado.shards.add(s);
        }

        Iterator<Shard> it = tornado.shards.iterator();
        while (it.hasNext()) {
            Shard s = it.next();
            s.px = s.x;
            s.py = s.y;
            s.pz = s.z;
            s.prevRot.set(s.rot);
            s.rot.rotateXYZ(s.sx, s.sy, s.sz);
            double frac = s.h / height;
            s.angle += s.spinSpeed * (1.35 - 0.5 * frac);
            s.h += s.rise * Math.max(0.2f, strength);
            place(s, c, height);
            if (s.h > height * 0.93 || strength < 0.15f) {
                fling(s, c, random);
                it.remove();
            }
        }
    }

    private static void place(Shard s, Vec3 c, float height) {
        double r = BlizzardSpell.funnelRadius(s.h / height) * s.radial;
        s.x = c.x + Math.cos(s.angle) * r;
        s.y = c.y + s.h;
        s.z = c.z + Math.sin(s.angle) * r;
    }

    /** Hands the crystal to the physics particles: it flies out of the funnel, bounces and shatters. */
    private static void fling(Shard s, Vec3 c, RandomSource random) {
        double dx = s.x - c.x, dz = s.z - c.z;
        double d = Math.max(0.05, Math.sqrt(dx * dx + dz * dz));
        double ox = dx / d, oz = dz / d;
        double tx = -oz, tz = ox;
        FxParticles.P p = FxParticles.spawn(FxParticles.Kind.SHARD).at(s.x, s.y, s.z)
                .vel(tx * 0.45 + ox * 0.3, 0.2 + random.nextDouble() * 0.15, tz * 0.45 + oz * 0.3)
                .color(0.75f, 0.92f, 1.0f).size(s.length / 3.0f).life(60 + random.nextInt(30))
                .physics(0.04, 0.985, 0.35);
        p.rot.set(s.rot);
        p.prevRot.set(s.rot);
        p.spinX = s.sx;
        p.spinY = s.sy;
        p.spinZ = s.sz;
    }

    private static void spawnAmbient(Minecraft mc, Vec3 c, float height, float strength, RandomSource random) {
        if (strength <= 0.05f) return;
        // Snow motes riding the spiral
        for (int i = 0; i < 6; i++) {
            double h = random.nextDouble() * height;
            double r = BlizzardSpell.funnelRadius(h / height) * (0.8 + random.nextDouble() * 0.5);
            double a = random.nextDouble() * Math.PI * 2.0;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            FxParticles.spawn(FxParticles.Kind.FLAKE).at(x, c.y + h, z)
                    .vel(-Math.sin(a) * 0.32 * strength, 0.07, Math.cos(a) * 0.32 * strength)
                    .color(0.85f, 0.95f, 1.0f).size(0.07f + random.nextFloat() * 0.06f)
                    .life(16 + random.nextInt(14)).physics(0.0, 0.93, 0.2).noCollide();
        }
        // Vanilla snowflakes and frost dust kicked up at the base
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 0.5 + random.nextDouble() * 2.8;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * r, c.y + 0.1 + random.nextDouble() * 1.5, c.z + Math.sin(a) * r,
                    -Math.sin(a) * 0.25, 0.08, Math.cos(a) * 0.25);
        }
        if (random.nextFloat() < 0.6f) {
            double a = random.nextDouble() * Math.PI * 2.0;
            mc.level.addParticle(ParticleTypes.POOF, c.x + Math.cos(a) * 0.8, c.y + 0.05, c.z + Math.sin(a) * 0.8,
                    Math.cos(a) * 0.12, 0.01, Math.sin(a) * 0.12);
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (TORNADOES.isEmpty() || !FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();

        // Ice crystals (matter) first
        FxDraw.solid();
        Quaternionf q = new Quaternionf();
        for (Tornado tornado : TORNADOES) {
            float strength = BlizzardSpell.intensity(tornado.age + pt);
            for (Shard s : tornado.shards) {
                s.prevRot.slerp(s.rot, pt, q);
                FxDraw.crystal(Mth.lerp(pt, s.px, s.x), Mth.lerp(pt, s.py, s.y), Mth.lerp(pt, s.pz, s.z), q,
                        s.length, s.thickness, 0.62f, 0.85f, 1.0f, 0.8f * Math.max(0.3f, strength));
            }
        }

        // Light
        FxDraw.glow();
        for (Tornado tornado : TORNADOES) {
            renderTornado(tornado, tornado.age + pt, pt);
        }
        FxDraw.end();
    }

    private static void renderTornado(Tornado tornado, float t, float pt) {
        Vec3 c = tornado.center(t);
        float height = BlizzardSpell.height(t);
        float strength = BlizzardSpell.intensity(t);
        float circleAlpha = Mth.clamp(t / 10.0f, 0.0f, 1.0f) * Mth.clamp((tornado.duration - t) / 20.0f, 0.0f, 1.0f);

        renderSigil(c, t, circleAlpha);
        renderGroundWind(c, t, strength);
        if (height < 0.2f) return;

        renderCore(c, height, strength, t);
        // Inner fast ribbons (white-cyan) and outer slower aurora ribbons
        renderRibbons(c, height, strength, t, 6, 0.34f, 0.78, 3.4, 0.55, 0.5f, false);
        renderRibbons(c, height, strength, t, 5, 0.21f, 1.06, 2.5, 0.42, 0.38f, true);
        renderWisps(c, height, strength, t);
        renderSnowWall(c, height, strength, t, tornado.seed);
        renderCloudCap(c, height, strength, t);

        // Crystal halos
        for (Shard s : tornado.shards) {
            FxDraw.glow(Mth.lerp(pt, s.px, s.x), Mth.lerp(pt, s.py, s.y), Mth.lerp(pt, s.pz, s.z),
                    s.length * 1.4, 0.5f, 0.85f, 1.0f, 0.3f * strength);
        }

        // Frost arcs between crystals
        for (FrostArc arc : tornado.arcs) {
            float life = 1.0f - (tornado.age - arc.born() + pt) / 5.0f;
            if (life <= 0) continue;
            List<Vec3> path = BoltFx.jagged(arc.a(), arc.b(), arc.a().distanceTo(arc.b()) * 0.15, new Random(arc.seed()));
            for (int i = 0; i + 1 < path.size(); i++) {
                FxDraw.beam(path.get(i), path.get(i + 1), 0.22, 0.55f, 0.75f, 1.0f, 0.35f * life);
                FxDraw.beam(path.get(i), path.get(i + 1), 0.05, 1.0f, 1.0f, 1.0f, 0.9f * life);
            }
        }
    }

    /** Spinning snowflake sigil with runic rings and pulses on the ground. */
    private static void renderSigil(Vec3 c, float t, float alpha) {
        if (alpha <= 0.01f) return;
        double y = c.y + 0.035;
        FxDraw.flatDisc(c.x, y, c.z, 4.6, 0.3f, 0.65f, 1.0f, 0.22f * alpha, 32);
        FxDraw.flatRing(c.x, y, c.z, 3.9, 0.22, 0.55f, 0.88f, 1.0f, 0.9f * alpha, 64);
        FxDraw.flatRing(c.x, y, c.z, 3.45, 0.09, 0.75f, 0.95f, 1.0f, 0.7f * alpha, 64);
        FxDraw.flatRing(c.x, y, c.z, 1.2, 0.1, 0.75f, 0.95f, 1.0f, 0.8f * alpha, 40);

        // Six-armed snowflake
        double rot = t * 0.02;
        for (int arm = 0; arm < 6; arm++) {
            double a = rot + arm * Math.PI / 3.0;
            double ca = Math.cos(a), sa = Math.sin(a);
            FxDraw.groundLine(c.x + ca * 1.2, c.z + sa * 1.2, c.x + ca * 3.45, c.z + sa * 3.45, y, 0.1, 0.7f, 0.92f, 1.0f, 0.85f * alpha);
            for (double at : new double[]{0.45, 0.68}) {
                double bx = c.x + ca * (1.2 + 2.25 * at), bz = c.z + sa * (1.2 + 2.25 * at);
                double len = at < 0.5 ? 0.6 : 0.42;
                for (int side = -1; side <= 1; side += 2) {
                    double ba = a + side * 0.85;
                    FxDraw.groundLine(bx, bz, bx + Math.cos(ba) * len, bz + Math.sin(ba) * len, y, 0.08, 0.7f, 0.92f, 1.0f, 0.75f * alpha);
                }
            }
        }

        // Rune diamonds between the outer rings, counter-rotating
        double runeRot = -t * 0.015;
        for (int i = 0; i < 12; i++) {
            double a = runeRot + i * Math.PI / 6.0;
            double rx = c.x + Math.cos(a) * 3.67, rz = c.z + Math.sin(a) * 3.67;
            double s = 0.14;
            double ca = Math.cos(a), sa = Math.sin(a);
            double[][] pts = {
                    {rx + ca * s, rz + sa * s}, {rx - sa * s, rz + ca * s},
                    {rx - ca * s, rz - sa * s}, {rx + sa * s, rz - ca * s}};
            for (int k = 0; k < 4; k++) {
                double[] p0 = pts[k], p1 = pts[(k + 1) % 4];
                FxDraw.groundLine(p0[0], p0[1], p1[0], p1[1], y + 0.002, 0.05, 0.85f, 0.97f, 1.0f, 0.9f * alpha);
            }
        }

        // Pulses rippling outward
        float pulse = (t % 25.0f) / 25.0f;
        FxDraw.flatRing(c.x, y + 0.004, c.z, 0.6 + pulse * 4.6, 0.35, 0.6f, 0.9f, 1.0f, 0.55f * (1.0f - pulse) * alpha, 48);
    }

    /** Spiral gusts sweeping across the ground at the base. */
    private static void renderGroundWind(Vec3 c, float t, float strength) {
        if (strength <= 0.02f) return;
        double y = c.y + 0.05;
        for (int arm = 0; arm < 3; arm++) {
            double base = t * 0.3 + arm * Math.PI * 2.0 / 3.0;
            double prevX = c.x, prevZ = c.z;
            for (int i = 1; i <= 14; i++) {
                double f = i / 14.0;
                double r = 0.3 + f * 3.0;
                double a = base + f * 2.6;
                double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                FxDraw.groundLine(prevX, prevZ, x, z, y, 0.22 * (1 - f) + 0.05, 0.8f, 0.95f, 1.0f, 0.45f * (1.0f - (float) f) * strength);
                prevX = x;
                prevZ = z;
            }
        }
    }

    /** Column of light at the heart of the funnel. */
    private static void renderCore(Vec3 c, float height, float strength, float t) {
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 0.7);
        FxDraw.beam(c.x, c.y, c.z, c.x, c.y + height, c.z, 0.9, 0.55f, 0.85f, 1.0f, 0.35f * strength * flicker, 0.15f * strength);
        FxDraw.beam(c.x, c.y, c.z, c.x, c.y + height, c.z, 0.22, 1.0f, 1.0f, 1.0f, 0.8f * strength * flicker, 0.3f * strength);
        for (int k = 0; k <= 8; k++) {
            double f = k / 8.0;
            FxDraw.glow(c.x, c.y + height * f, c.z, BlizzardSpell.funnelRadius(f) * 0.9, 0.55f, 0.82f, 1.0f, 0.1f * strength);
        }
        FxDraw.glow(c.x, c.y + 0.3, c.z, 1.8, 0.7f, 0.92f, 1.0f, 0.5f * strength);
    }

    /**
     * Twisting translucent bands along the funnel surface with soft edges.
     */
    private static void renderRibbons(Vec3 c, float height, float strength, float t, int count, float speed,
                                      double radiusScale, double twist, double angularWidth, float alpha, boolean aurora) {
        int steps = 28;
        for (int band = 0; band < count; band++) {
            double offset = band * Math.PI * 2.0 / count;
            for (int i = 0; i < steps; i++) {
                double f0 = (double) i / steps, f1 = (double) (i + 1) / steps;
                double y0 = c.y + f0 * height, y1 = c.y + f1 * height;
                double r0 = BlizzardSpell.funnelRadius(f0) * radiusScale * (1.0 + 0.07 * Math.sin(f0 * 9.0 + t * 0.3 + band));
                double r1 = BlizzardSpell.funnelRadius(f1) * radiusScale * (1.0 + 0.07 * Math.sin(f1 * 9.0 + t * 0.3 + band));
                double a0 = t * speed + offset + f0 * twist;
                double a1 = t * speed + offset + f1 * twist;
                // Fade in at the base and out at the top
                float env0 = (float) Math.sqrt(Math.sin(Math.PI * Math.max(0.03, f0)));
                float env1 = (float) Math.sqrt(Math.sin(Math.PI * Math.min(0.97, f1)));
                float[] col0 = ribbonColor(f0, t, band, aurora);
                float[] col1 = ribbonColor(f1, t, band, aurora);
                float al0 = alpha * strength * env0;
                float al1 = alpha * strength * env1;
                double w = angularWidth;
                // Left half (edge -> middle), right half (middle -> edge)
                strip(c, y0, y1, r0, r1, a0, a1, a0 + w * 0.5, a1 + w * 0.5, col0, col1, 0, 0, al0, al1);
                strip(c, y0, y1, r0, r1, a0 + w * 0.5, a1 + w * 0.5, a0 + w, a1 + w, col0, col1, al0, al1, 0, 0);
            }
        }
    }

    private static float[] ribbonColor(double f, float t, int band, boolean aurora) {
        if (!aurora) {
            float k = (float) f;
            return new float[]{0.55f + 0.4f * k, 0.85f + 0.13f * k, 1.0f};
        }
        // Shimmering aurora: teal <-> violet
        float shift = 0.5f + 0.5f * (float) Math.sin(t * 0.05 + f * 3.0 + band * 1.3);
        return new float[]{0.3f + 0.5f * shift, 0.9f - 0.35f * shift, 1.0f};
    }

    private static void strip(Vec3 c, double y0, double y1, double r0, double r1,
                              double aStart0, double aStart1, double aEnd0, double aEnd1,
                              float[] col0, float[] col1, float s0, float s1, float e0, float e1) {
        FxDraw.vertex(c.x + Math.cos(aStart0) * r0, y0, c.z + Math.sin(aStart0) * r0, col0[0], col0[1], col0[2], s0);
        FxDraw.vertex(c.x + Math.cos(aEnd0) * r0, y0, c.z + Math.sin(aEnd0) * r0, col0[0], col0[1], col0[2], e0);
        FxDraw.vertex(c.x + Math.cos(aEnd1) * r1, y1, c.z + Math.sin(aEnd1) * r1, col1[0], col1[1], col1[2], e1);
        FxDraw.vertex(c.x + Math.cos(aStart1) * r1, y1, c.z + Math.sin(aStart1) * r1, col1[0], col1[1], col1[2], s1);
    }

    /** Thin bright streaks whipping around outside the funnel. */
    private static void renderWisps(Vec3 c, float height, float strength, float t) {
        int steps = 22;
        for (int wisp = 0; wisp < 4; wisp++) {
            double offset = wisp * Math.PI / 2.0 + 0.4;
            double prevX = 0, prevY = 0, prevZ = 0;
            for (int i = 0; i <= steps; i++) {
                double f = (double) i / steps;
                double r = BlizzardSpell.funnelRadius(f) * 1.2;
                double a = t * 0.45 + offset + f * 2.0;
                double x = c.x + Math.cos(a) * r, y = c.y + f * height, z = c.z + Math.sin(a) * r;
                if (i > 0) {
                    float fade = (float) Math.sin(Math.PI * f);
                    FxDraw.beam(prevX, prevY, prevZ, x, y, z, 0.09, 0.9f, 0.97f, 1.0f, 0.55f * strength * fade, 0.55f * strength * fade);
                }
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        }
    }

    /** Dense wall of glittering snow orbiting the funnel (procedural, no particles). */
    private static void renderSnowWall(Vec3 c, float height, float strength, float t, long seed) {
        Random random = new Random(seed);
        for (int k = 0; k < 90; k++) {
            double f = random.nextDouble();
            double phase = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.22 + random.nextDouble() * 0.2;
            double spread = 0.85 + random.nextDouble() * 0.4;
            double climb = (f + t * 0.004 * (1 + k % 3)) % 1.0;
            double r = BlizzardSpell.funnelRadius(climb) * spread;
            double a = phase + t * speed * (1.3 - 0.5 * climb);
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 0.4 + k);
            FxDraw.glow(c.x + Math.cos(a) * r, c.y + climb * height, c.z + Math.sin(a) * r,
                    0.09 + 0.05 * twinkle, 0.9f, 0.97f, 1.0f, 0.85f * strength * twinkle);
        }
    }

    /** Swirling frost cloud crowning the tornado. */
    private static void renderCloudCap(Vec3 c, float height, float strength, float t) {
        double top = c.y + height;
        double r = BlizzardSpell.funnelRadius(1.0);
        FxDraw.flatDisc(c.x, top, c.z, r * 1.7, 0.75f, 0.88f, 1.0f, 0.22f * strength, 28);
        for (int k = 0; k < 3; k++) {
            double wobble = Math.sin(t * 0.08 + k * 2.1) * 0.15;
            FxDraw.flatRing(c.x, top + wobble + k * 0.12, c.z, r * (1.05 + 0.18 * k), 0.4, 0.8f, 0.93f, 1.0f, 0.35f * strength, 40);
        }
        FxDraw.glow(c.x, top, c.z, r * 1.3, 0.65f, 0.85f, 1.0f, 0.22f * strength);
    }
}
