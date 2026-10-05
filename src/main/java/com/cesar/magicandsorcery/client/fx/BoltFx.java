package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.spells.BoltSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Bolt visuals, modelled on real lightning:
 * charge gathering in the caster's hand, a faint stepped leader racing to the target, a blinding return stroke
 * with two re-strikes, ionised afterglow, a Lichtenberg scorch burned into the ground that cools from white to red,
 * debris, crawling ground arcs, an electrified outline on the victim, chain arcs, camera shake and a screen flash.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BoltFx {

    private static final int LEADER_TICKS = 2;
    private static final int[] STROKE_TICKS = {2, 5, 8};
    private static final float[] STROKE_POWER = {1.0f, 0.8f, 0.6f};
    private static final int FLASH_LIFE = 22;

    // Golden storm palette
    private static final float[] OUTER = {1.0f, 0.55f, 0.15f};
    private static final float[] MID = {1.0f, 0.88f, 0.42f};
    private static final float[] ION = {0.7f, 0.5f, 1.0f};
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
        final int victimId;
        final List<List<Strand>> strokes = new ArrayList<>();
        final List<Strand> leader;
        final List<List<List<Strand>>> chainStrokes = new ArrayList<>();
        int age;

        Strike(Vec3 start, Vec3 end, long seed, boolean hitEntity, List<Vec3> chain, Double groundY, int victimId) {
            this.start = start;
            this.end = end;
            this.seed = seed;
            this.hitEntity = hitEntity;
            this.chain = chain;
            this.groundY = groundY;
            this.victimId = victimId;
            this.leader = buildBolt(start, end, new Random(seed ^ 0x5DEECE66DL), 0.5f, true);
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

        int life() {
            return FLASH_LIFE;
        }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    private BoltFx() {
    }

    public static void add(Vec3 start, Vec3 end, long seed, boolean hitEntity, List<Vec3> chain) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Double groundY = null;
        BlockState groundState = null;
        BlockHitResult down = mc.level.clip(new ClipContext(end.add(0, 0.3, 0), end.add(0, -1.6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
        if (down.getType() == HitResult.Type.BLOCK) {
            groundY = down.getLocation().y;
            groundState = mc.level.getBlockState(down.getBlockPos());
        }

        int victimId = -1;
        if (hitEntity) {
            double best = 4.0;
            for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(1.5))) {
                double d = e.getBoundingBox().getCenter().distanceToSqr(end);
                if (d < best) {
                    best = d;
                    victimId = e.getId();
                }
            }
        }
        STRIKES.add(new Strike(start, end, seed, hitEntity, new ArrayList<>(chain), groundY, victimId));
        if (groundY != null) {
            GroundMarks.bolt(new Vec3(end.x, groundY, end.z), seed);
        }

        // Impact debris
        FxParticles.sparkBurst(end.x, end.y, end.z, 34, 0.55, SPARK_COLOR, mc.level.random);
        for (Vec3 c : chain) {
            FxParticles.sparkBurst(c.x, c.y, c.z, 12, 0.35, SPARK_COLOR, mc.level.random);
        }
        if (groundState != null && !groundState.isAir()) {
            BlockParticleOption chunks = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
            for (int i = 0; i < 18; i++) {
                double a = mc.level.random.nextDouble() * Math.PI * 2.0;
                double s = 0.1 + mc.level.random.nextDouble() * 0.25;
                mc.level.addParticle(chunks, end.x, groundY + 0.1, end.z, Math.cos(a) * s, 0.25 + mc.level.random.nextDouble() * 0.3, Math.sin(a) * s);
            }
        }
        for (int i = 0; i < 8; i++) {
            mc.level.addParticle(ParticleTypes.LARGE_SMOKE, end.x + (mc.level.random.nextDouble() - 0.5) * 0.8,
                    end.y + 0.1, end.z + (mc.level.random.nextDouble() - 0.5) * 0.8, 0, 0.05, 0);
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
        int branchCount = 3 + random.nextInt(4);
        for (int i = 0; i < branchCount; i++) {
            int idx = 2 + random.nextInt(trunk.size() - 4);
            Vec3 root = trunk.get(idx);
            double remaining = root.distanceTo(b);
            Vec3 branchDir = deviate(dir, 0.9, random);
            double branchLen = remaining * (0.18 + random.nextDouble() * 0.25) + 0.6;
            List<Vec3> branch = jagged(root, root.add(branchDir.scale(branchLen)), branchLen * 0.18, random);
            strands.add(new Strand(branch, width * 0.5f));

            if (random.nextFloat() < 0.65f && branch.size() > 3) {
                Vec3 twigRoot = branch.get(branch.size() / 2);
                Vec3 twigDir = deviate(branchDir, 1.1, random);
                double twigLen = branchLen * (0.3 + random.nextDouble() * 0.3);
                strands.add(new Strand(jagged(twigRoot, twigRoot.add(twigDir.scale(twigLen)), twigLen * 0.2, random), width * 0.28f));
            }
        }
        return strands;
    }

    /** Midpoint-displacement lightning path. */
    public static List<Vec3> jagged(Vec3 a, Vec3 b, double displacement, Random random) {
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
    // Tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
            for (int k = 1; k < STROKE_TICKS.length; k++) {
                if (s.age == STROKE_TICKS[k]) {
                    FxParticles.sparkBurst(s.end.x, s.end.y, s.end.z, 12, 0.4, SPARK_COLOR, mc.level.random);
                }
            }
            if (s.age < 12 && mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, s.end.x, s.end.y + 0.2, s.end.z,
                        (mc.level.random.nextDouble() - 0.5) * 0.3, 0.1, (mc.level.random.nextDouble() - 0.5) * 0.3);
            }
            if (s.age >= s.life()) it.remove();
        }
    }

    // ------------------------------------------------------------------
    // Camera shake and screen flash for strikes near the viewer
    // ------------------------------------------------------------------

    private static float nearbyIntensity(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || STRIKES.isEmpty()) return 0.0f;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float best = 0.0f;
        for (Strike s : STRIKES) {
            float t = s.age + partialTick - STROKE_TICKS[0];
            if (t < 0 || t > 12) continue;
            double dist = cam.distanceTo(s.end);
            float near = (float) Mth.clamp(1.0 - dist / 18.0, 0.0, 1.0);
            best = Math.max(best, near * (float) Math.exp(-t * 0.35f));
        }
        return best;
    }

    /** Strength (0..1) of the golden screen flash when a bolt lands close by. */
    public static float screenFlash(float partialTick) {
        return nearbyIntensity(partialTick) * 0.8f;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float k = nearbyIntensity((float) event.getPartialTick());
        if (k <= 0.01f) return;
        double t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() + event.getPartialTick() : 0;
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 9.1) * 1.6 * k));
        event.setPitch(event.getPitch() + (float) (Math.cos(t * 7.7) * 1.3 * k));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 11.3) * 2.0 * k));
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (STRIKES.isEmpty() || !FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();
        for (Strike s : STRIKES) {
            renderStrike(s, s.age + pt);
        }
        FxDraw.end();
    }

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
        if (t > FLASH_LIFE) return;

        // 1. Stepped leader: a faint branching channel racing toward the target
        if (t < LEADER_TICKS) {
            float reach = Mth.clamp(t / LEADER_TICKS, 0.0f, 1.0f);
            for (Strand strand : s.leader) {
                List<Vec3> pts = strand.points();
                int visible = (int) (pts.size() * reach);
                for (int i = 0; i + 1 < Math.min(visible, pts.size()); i++) {
                    FxDraw.beam(pts.get(i), pts.get(i + 1), 0.18 * strand.width(), ION[0], ION[1], ION[2], 0.35f);
                    FxDraw.beam(pts.get(i), pts.get(i + 1), 0.04 * strand.width(), 1, 1, 1, 0.5f);
                }
                if (visible > 0 && visible <= pts.size() && strand.width() >= 0.5f) {
                    Vec3 tip = pts.get(Math.max(0, visible - 1));
                    FxDraw.glow(tip.x, tip.y, tip.z, 0.5, MID[0], MID[1], MID[2], 0.8f);
                }
            }
            return;
        }

        float life = Math.max(0.0f, 1.0f - t / FLASH_LIFE);
        int stroke = activeStroke(t);
        float power = strokeIntensity(t, stroke);
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 9.0f);
        float intensity = Math.min(1.0f, power * flicker);

        // 2. Ionised afterglow (violet) and the current return stroke (gold/white)
        drawStrands(s.strokes.get(0), 0.22f * life, 0.7f, ION);
        drawStrands(s.strokes.get(stroke), intensity, 1.0f, OUTER);

        // 3. Chain arcs, each link fires a tick after the previous one
        for (int i = 0; i < s.chainStrokes.size(); i++) {
            float lt = t - STROKE_TICKS[0] - (i + 1);
            if (lt < 0) continue;
            int ls = activeStroke(lt + STROKE_TICKS[0]);
            drawStrands(s.chainStrokes.get(i).get(ls), Math.min(1.0f, strokeIntensity(lt + STROKE_TICKS[0], ls) * flicker), 1.0f, OUTER);
        }

        // 4. Impact flare (no flash at the caster's hand)
        FxDraw.glow(s.end.x, s.end.y, s.end.z, 1.2 + 2.8 * intensity, OUTER[0], OUTER[1], OUTER[2], 0.55f * intensity);
        FxDraw.glow(s.end.x, s.end.y, s.end.z, 0.3 + 1.0 * intensity, 1, 1, 1, 0.95f * intensity);

        // 5. Shockwave ring around the bolt axis
        float ringT = Mth.clamp((t - STROKE_TICKS[0]) / 10.0f, 0.0f, 1.0f);
        if (ringT < 1.0f) {
            Vec3[] basis = FxDraw.basis(s.end.subtract(s.start));
            FxDraw.ring(s.end, basis[0], basis[1], 0.2 + ringT * 2.8, 0.4, MID[0], MID[1], MID[2], 0.7f * (1.0f - ringT), 32);
        }

        // 7. Electrified outline crackling over the victim
        if (s.victimId >= 0 && t < 16) {
            Entity victim = Minecraft.getInstance().level.getEntity(s.victimId);
            if (victim != null) {
                outlineCrackle(victim, s.seed, t, 1.0f - t / 16.0f);
            }
        }

        // 8. Static over the chain victims
        if (t < 15) {
            Random staticRandom = new Random(s.seed * 7 + (long) (t * 3));
            float staticAlpha = 1.0f - t / 15.0f;
            for (Vec3 v : s.chain) {
                for (int k = 0; k < 3; k++) {
                    Vec3 a = v.add((staticRandom.nextDouble() - 0.5), (staticRandom.nextDouble() - 0.5) * 1.4, (staticRandom.nextDouble() - 0.5));
                    Vec3 b = v.add((staticRandom.nextDouble() - 0.5), (staticRandom.nextDouble() - 0.5) * 1.4, (staticRandom.nextDouble() - 0.5));
                    List<Vec3> path = jagged(a, b, 0.2, staticRandom);
                    for (int p = 0; p + 1 < path.size(); p++) {
                        FxDraw.beam(path.get(p), path.get(p + 1), 0.08, MID[0], MID[1], MID[2], staticAlpha);
                    }
                }
                FxDraw.glow(v.x, v.y, v.z, 1.2, OUTER[0], OUTER[1], OUTER[2], 0.3f * staticAlpha);
            }
        }
    }

    /** Jittering electric edges around an entity's bounding box. */
    private static void outlineCrackle(Entity victim, long seed, float t, float alpha) {
        AABB box = victim.getBoundingBox().inflate(0.08);
        Random random = new Random(seed + (long) (t * 4));
        double[][] corners = {
                {box.minX, box.minY, box.minZ}, {box.maxX, box.minY, box.minZ}, {box.maxX, box.minY, box.maxZ}, {box.minX, box.minY, box.maxZ},
                {box.minX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.maxZ}, {box.minX, box.maxY, box.maxZ}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            if (random.nextFloat() < 0.35f) continue;
            Vec3 a = new Vec3(corners[e[0]][0], corners[e[0]][1], corners[e[0]][2]);
            Vec3 b = new Vec3(corners[e[1]][0], corners[e[1]][1], corners[e[1]][2]);
            List<Vec3> path = jagged(a, b, 0.12, random);
            for (int p = 0; p + 1 < path.size(); p++) {
                FxDraw.beam(path.get(p), path.get(p + 1), 0.12, MID[0], MID[1], MID[2], 0.6f * alpha);
                FxDraw.beam(path.get(p), path.get(p + 1), 0.035, 1, 1, 1, alpha);
            }
        }
        Vec3 c = box.getCenter();
        FxDraw.glow(c.x, c.y, c.z, victim.getBbHeight() * 0.9, OUTER[0], OUTER[1], OUTER[2], 0.35f * alpha);
    }

    private static void drawStrands(List<Strand> strands, float intensity, float widthScale, float[] outer) {
        if (intensity <= 0.01f) return;
        for (Strand strand : strands) {
            List<Vec3> pts = strand.points();
            float w = strand.width() * widthScale;
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                FxDraw.beam(a, b, 1.1 * w, outer[0], outer[1], outer[2], 0.12f * intensity);
                FxDraw.beam(a, b, 0.3 * w, MID[0], MID[1], MID[2], 0.6f * intensity);
                FxDraw.beam(a, b, 0.08 * w, 1.0f, 1.0f, 1.0f, intensity);
            }
            // Bright knots along the trunk give it volume
            if (strand.width() >= 1.0f) {
                for (int i = 2; i < pts.size(); i += 3) {
                    Vec3 p = pts.get(i);
                    FxDraw.glow(p.x, p.y, p.z, 0.55 * w, MID[0], MID[1], MID[2], 0.2f * intensity);
                }
            }
        }
    }
}
