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
 * Blizzard visuals: a realistic, smoky frost tornado with an arcane heart.
 * <ul>
 *   <li>Volumetric smoke: hundreds of soft translucent puffs churning up a rope-like funnel that bends and sways</li>
 *   <li>A debris skirt of snow dust at the base and a dark rotating wall cloud on top, lit by inner lightning</li>
 *   <li>Arcane layer: snowflake sigil on the ground, aurora ribbons, a column of light, wisps and glittering snow</li>
 *   <li>Ice crystals riding the spiral, hurled out of the top with physics, plus frost arcs between them</li>
 * </ul>
 * Shape and wander path come from {@link BlizzardSpell}, so they match the server physics.
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

    /** Ice spike erupting from the ground around the tornado. */
    private static final class Spike {
        final Vec3 base;
        final Vec3 dir;
        final Quaternionf rot;
        final float length;
        final float thickness;
        final int delay;

        Spike(Vec3 base, Vec3 dir, float length, float thickness, int delay) {
            this.base = base;
            this.dir = dir;
            this.rot = new Quaternionf().rotationTo(0, 1, 0, (float) dir.x, (float) dir.y, (float) dir.z);
            this.length = length;
            this.thickness = thickness;
            this.delay = delay;
        }
    }

    /** Frost lightning dropping from the wall cloud to the ground. */
    private record FrostStrike(Vec3 top, Vec3 bottom, long seed, int born) {
    }

    private static final class Tornado {
        final Vec3 origin;
        final long seed;
        final int duration;
        final List<Shard> shards = new ArrayList<>();
        final List<FrostArc> arcs = new ArrayList<>();
        final List<Spike> spikes = new ArrayList<>();
        final List<FrostStrike> strikes = new ArrayList<>();
        boolean spikesBuilt;
        int age;
        int cloudFlash = -100;
        double cloudFlashAngle;

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

    /**
     * Rope-like bend of the funnel: the higher, the further the axis sways from the ground contact point.
     */
    private static double bendX(double f, float t) {
        return Math.sin(f * 2.2 + t * 0.045) * 0.6 * f;
    }

    private static double bendZ(double f, float t) {
        return Math.cos(f * 1.7 + t * 0.038) * 0.6 * f;
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

            if (!tornado.spikesBuilt) {
                buildSpikes(mc, tornado, random);
            }
            tickShards(tornado, c, height, strength, t, random);
            spawnAmbient(mc, c, height, strength, random);
            tickSpectacle(mc, tornado, c, height, strength, random);

            if (tornado.age % 7 == 0 && tornado.shards.size() >= 2 && strength > 0.4f) {
                Shard a = tornado.shards.get(random.nextInt(tornado.shards.size()));
                Shard b = tornado.shards.get(random.nextInt(tornado.shards.size()));
                if (a != b) {
                    tornado.arcs.add(new FrostArc(new Vec3(a.x, a.y, a.z), new Vec3(b.x, b.y, b.z), random.nextLong(), tornado.age));
                }
            }
            tornado.arcs.removeIf(arc -> tornado.age - arc.born() > 4);

            // Lightning flickering inside the wall cloud
            if (strength > 0.5f && random.nextFloat() < 0.04f) {
                tornado.cloudFlash = tornado.age;
                tornado.cloudFlashAngle = random.nextDouble() * Math.PI * 2.0;
            }

            if (tornado.age >= tornado.duration) {
                for (Shard shard : tornado.shards) fling(shard, c, random);
                collapse(mc, tornado, c, random);
                it.remove();
            }
        }
    }

    private static void tickShards(Tornado tornado, Vec3 c, float height, float strength, float t, RandomSource random) {
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
            place(s, c, height, t);
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
            place(s, c, height, t);
            if (s.h > height * 0.93 || strength < 0.15f) {
                fling(s, c, random);
                it.remove();
            }
        }
    }

    private static void place(Shard s, Vec3 c, float height, float t) {
        double f = s.h / height;
        double r = BlizzardSpell.funnelRadius(f) * s.radial;
        s.x = c.x + bendX(f, t) + Math.cos(s.angle) * r;
        s.y = c.y + s.h;
        s.z = c.z + bendZ(f, t) + Math.sin(s.angle) * r;
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

    /** A ring of ice spikes bursts out of the ground around the tornado, slightly tilted outward. */
    private static void buildSpikes(Minecraft mc, Tornado tornado, RandomSource random) {
        tornado.spikesBuilt = true;
        int count = 16 + random.nextInt(7);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.25;
            double dist = 4.3 + random.nextDouble() * 1.3;
            int cluster = 1 + random.nextInt(3);
            for (int k = 0; k < cluster; k++) {
                double ca = a + (random.nextDouble() - 0.5) * 0.18;
                double cd = dist + (random.nextDouble() - 0.5) * 0.7;
                double x = tornado.origin.x + Math.cos(ca) * cd, z = tornado.origin.z + Math.sin(ca) * cd;
                Vec3 ground = groundAt(mc, x, tornado.origin.y, z);
                if (ground == null) continue;
                Vec3 dir = new Vec3(Math.cos(ca) * (0.3 + random.nextDouble() * 0.35), 1.0, Math.sin(ca) * (0.3 + random.nextDouble() * 0.35))
                        .add((random.nextDouble() - 0.5) * 0.25, 0, (random.nextDouble() - 0.5) * 0.25).normalize();
                float length = (k == 0 ? 1.2f : 0.6f) + random.nextFloat() * (k == 0 ? 1.6f : 0.7f);
                tornado.spikes.add(new Spike(ground.add(dir.scale(-0.15)), dir, length, length * (0.2f + random.nextFloat() * 0.08f),
                        2 + random.nextInt(16)));
            }
        }
    }

    private static Vec3 groundAt(Minecraft mc, double x, double refY, double z) {
        net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                new Vec3(x, refY + 3.0, z), new Vec3(x, refY - 6.0, z),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, mc.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

    /** Formation vortex, heavy snowfall, ground debris, frost lightning and the spikes' eruption sounds. */
    private static void tickSpectacle(Minecraft mc, Tornado tornado, Vec3 c, float height, float strength, RandomSource random) {
        int age = tornado.age;
        // Snow converging in a spiral while the tornado forms
        if (age < BlizzardSpell.FORM_TICKS + 6) {
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = 5.0 + random.nextDouble() * 2.0;
                double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                FxParticles.spawn(FxParticles.Kind.FLAKE).at(x, c.y + 0.2 + random.nextDouble() * 1.5, z)
                        .vel(-Math.cos(a) * 0.28 - Math.sin(a) * 0.22, 0.03, -Math.sin(a) * 0.28 + Math.cos(a) * 0.22)
                        .color(0.88f, 0.96f, 1.0f).size(0.08f + random.nextFloat() * 0.06f).life(18).physics(0, 0.95, 0).noCollide();
            }
        }
        // Spikes cracking out of the ground
        for (Spike spike : tornado.spikes) {
            if (age == spike.delay) {
                mc.level.playLocalSound(spike.base.x, spike.base.y, spike.base.z, net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_PLACE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.5f + random.nextFloat() * 0.4f, false);
                for (int k = 0; k < 4; k++) {
                    mc.level.addParticle(ParticleTypes.SNOWFLAKE, spike.base.x, spike.base.y + 0.1, spike.base.z,
                            (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
                }
            }
        }
        if (strength <= 0.1f) return;
        // Heavy snowfall over the whole area
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = random.nextDouble() * 12.0;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * r, c.y + 6.0 + random.nextDouble() * 4.0, c.z + Math.sin(a) * r,
                    -Math.sin(a) * 0.08, -0.12, Math.cos(a) * 0.08);
        }
        // Debris of the ground torn up and whirled around the base
        net.minecraft.world.level.block.state.BlockState below = mc.level.getBlockState(net.minecraft.core.BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (!below.isAir() && random.nextFloat() < 0.8f * strength) {
            net.minecraft.core.particles.BlockParticleOption chunk = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below);
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = 0.8 + random.nextDouble() * 2.2;
                mc.level.addParticle(chunk, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r,
                        -Math.sin(a) * 0.35, 0.3 + random.nextDouble() * 0.3, Math.cos(a) * 0.35);
            }
        }
        // Frost lightning from the wall cloud
        tornado.strikes.removeIf(f -> age - f.born() > 6);
        if (strength > 0.7f && height > 4.0f && random.nextFloat() < 0.05f) {
            double ta = random.nextDouble() * Math.PI * 2.0;
            double topR = BlizzardSpell.funnelRadius(1.0) * (1.0 + random.nextDouble() * 0.8);
            Vec3 top = new Vec3(c.x + bendX(1.0, age) + Math.cos(ta) * topR, c.y + height, c.z + bendZ(1.0, age) + Math.sin(ta) * topR);
            double ba = ta + (random.nextDouble() - 0.5) * 1.2;
            double br = 2.0 + random.nextDouble() * 3.0;
            Vec3 bottom = groundAt(mc, c.x + Math.cos(ba) * br, c.y, c.z + Math.sin(ba) * br);
            if (bottom != null) {
                tornado.strikes.add(new FrostStrike(top, bottom, random.nextLong(), age));
                FxParticles.sparkBurst(bottom.x, bottom.y + 0.1, bottom.z, 14, 0.3, 0xBFEFFF, random);
                mc.level.playLocalSound(bottom.x, bottom.y, bottom.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f + random.nextFloat() * 0.3f, false);
                mc.level.playLocalSound(bottom.x, bottom.y, bottom.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.7f, false);
            }
        }
    }

    /** The tornado collapses: spikes shatter, a burst of snow and ice blows outward. */
    private static void collapse(Minecraft mc, Tornado tornado, Vec3 c, RandomSource random) {
        for (Spike spike : tornado.spikes) {
            Vec3 mid = spike.base.add(spike.dir.scale(spike.length * 0.5));
            for (int k = 0; k < 3; k++) {
                FxParticles.P p = FxParticles.spawn(FxParticles.Kind.SHARD).at(mid.x, mid.y, mid.z)
                        .vel((random.nextDouble() - 0.5) * 0.35, 0.15 + random.nextDouble() * 0.25, (random.nextDouble() - 0.5) * 0.35)
                        .color(0.75f, 0.92f, 1.0f).size(spike.thickness * 0.6f).life(40 + random.nextInt(30)).physics(0.04, 0.98, 0.35)
                        .spin(random, 0.5f);
            }
        }
        for (int i = 0; i < 90; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double up = random.nextDouble();
            FxParticles.spawn(FxParticles.Kind.FLAKE).at(c.x, c.y + 0.5 + up * 3.0, c.z)
                    .vel(Math.cos(a) * (0.4 + random.nextDouble() * 0.4), 0.05 + up * 0.2, Math.sin(a) * (0.4 + random.nextDouble() * 0.4))
                    .color(0.9f, 0.97f, 1.0f).size(0.1f + random.nextFloat() * 0.1f).life(20 + random.nextInt(15)).physics(0.0, 0.9, 0.2).noCollide();
        }
        FxParticles.sparkBurst(c.x, c.y + 1.0, c.z, 30, 0.6, 0xCFF4FF, random);
        mc.level.playLocalSound(c.x, c.y + 1.0, c.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.5f, false);
        mc.level.playLocalSound(c.x, c.y + 1.0, c.z, net.minecraft.sounds.SoundEvents.POWDER_SNOW_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.6f, false);
    }

    private static void spawnAmbient(Minecraft mc, Vec3 c, float height, float strength, RandomSource random) {
        if (strength <= 0.05f) return;
        for (int i = 0; i < 5; i++) {
            double h = random.nextDouble() * height;
            double r = BlizzardSpell.funnelRadius(h / height) * (0.8 + random.nextDouble() * 0.5);
            double a = random.nextDouble() * Math.PI * 2.0;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            FxParticles.spawn(FxParticles.Kind.FLAKE).at(x, c.y + h, z)
                    .vel(-Math.sin(a) * 0.32 * strength, 0.07, Math.cos(a) * 0.32 * strength)
                    .color(0.85f, 0.95f, 1.0f).size(0.07f + random.nextFloat() * 0.06f)
                    .life(16 + random.nextInt(14)).physics(0.0, 0.93, 0.2).noCollide();
        }
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 0.5 + random.nextDouble() * 3.5;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * r, c.y + 0.1 + random.nextDouble() * 1.5, c.z + Math.sin(a) * r,
                    -Math.sin(a) * 0.3, 0.08, Math.cos(a) * 0.3);
        }
        if (random.nextFloat() < 0.7f) {
            double a = random.nextDouble() * Math.PI * 2.0;
            mc.level.addParticle(ParticleTypes.POOF, c.x + Math.cos(a) * 1.2, c.y + 0.05, c.z + Math.sin(a) * 1.2,
                    Math.cos(a) * 0.15 - Math.sin(a) * 0.1, 0.02, Math.sin(a) * 0.15 + Math.cos(a) * 0.1);
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (TORNADOES.isEmpty() || !FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();

        // Matter first: smoke and ice crystals (translucent, depth sorted)
        FxDraw.solid();
        Quaternionf q = new Quaternionf();
        for (Tornado tornado : TORNADOES) {
            float t = tornado.age + pt;
            renderSmoke(tornado, t);
            float strength = BlizzardSpell.intensity(t);
            renderSpikes(tornado, t, false);
            for (Shard s : tornado.shards) {
                s.prevRot.slerp(s.rot, pt, q);
                FxDraw.crystal(Mth.lerp(pt, s.px, s.x), Mth.lerp(pt, s.py, s.y), Mth.lerp(pt, s.pz, s.z), q,
                        s.length, s.thickness, 0.62f, 0.85f, 1.0f, 0.8f * Math.max(0.3f, strength));
            }
        }

        // Then all light on top
        FxDraw.glow();
        for (Tornado tornado : TORNADOES) {
            renderTornado(tornado, tornado.age + pt, pt);
        }
        FxDraw.end();
    }

    /**
     * Volumetric smoke: churning funnel puffs, a dust skirt at the base and a wall cloud on top.
     * Every puff is procedural (seeded), rising and orbiting with time, so no state is needed.
     */
    private static void renderSmoke(Tornado tornado, float t) {
        float height = BlizzardSpell.height(t);
        float strength = BlizzardSpell.intensity(t);
        if (strength <= 0.02f) return;
        Vec3 c = tornado.center(t);
        Random random = new Random(tornado.seed * 31L);

        // Funnel: dense puffs hugging the spiral, darker and dustier near the ground
        if (height > 0.3f) {
            for (int k = 0; k < 220; k++) {
                double phase = random.nextDouble() * Math.PI * 2.0;
                double base = random.nextDouble();
                double rise = 0.006 + random.nextDouble() * 0.01;
                double spread = 0.55 + random.nextDouble() * 0.6;
                double sizeJitter = 0.7 + random.nextDouble() * 0.6;
                float tone = random.nextFloat();
                double f = (base + t * rise) % 1.0;
                double r = BlizzardSpell.funnelRadius(f) * spread;
                double a = phase + t * (0.24 + 0.1 * tone) * (1.35 - 0.5 * f);
                double x = c.x + bendX(f, t) + Math.cos(a) * r;
                double y = c.y + f * height;
                double z = c.z + bendZ(f, t) + Math.sin(a) * r;
                double size = (0.45 + 0.75 * BlizzardSpell.funnelRadius(f) / 2.9) * sizeJitter;
                float env = (float) Math.sin(Math.PI * f);
                float light = 0.62f + 0.3f * (float) f + 0.08f * tone;
                FxDraw.puff(x, y, z, size, light * 0.9f, light * 0.95f, Math.min(1.0f, light * 1.05f),
                        0.22f * strength * env, (float) (a * 0.5));
            }
        }

        // Debris skirt: snow dust whipped around the base
        for (int k = 0; k < 70; k++) {
            double phase = random.nextDouble() * Math.PI * 2.0;
            double ring = 1.0 + random.nextDouble() * 3.4;
            double lift = random.nextDouble();
            double speed = 0.18 + random.nextDouble() * 0.12;
            double a = phase + t * speed * (2.2 / ring);
            double cycle = (lift + t * 0.01) % 1.0;
            double x = c.x + Math.cos(a) * ring * (0.9 + 0.2 * cycle);
            double y = c.y + 0.2 + cycle * 1.6;
            double z = c.z + Math.sin(a) * ring * (0.9 + 0.2 * cycle);
            float env = (float) Math.sin(Math.PI * cycle);
            FxDraw.puff(x, y, z, 0.5 + 0.5 * cycle, 0.8f, 0.85f, 0.92f, 0.16f * strength * env, (float) a);
        }

        // Wall cloud: a broad dark deck slowly rotating above the funnel
        if (height > 1.0f) {
            double top = c.y + height;
            double topX = c.x + bendX(1.0, t), topZ = c.z + bendZ(1.0, t);
            for (int k = 0; k < 64; k++) {
                double phase = random.nextDouble() * Math.PI * 2.0;
                double ring = 1.2 + random.nextDouble() * 4.6;
                double dy = (random.nextDouble() - 0.3) * 0.9;
                double a = phase + t * 0.03 * (3.0 / ring);
                double x = topX + Math.cos(a) * ring;
                double z = topZ + Math.sin(a) * ring;
                float shade = 0.42f + 0.18f * (float) random.nextDouble();
                FxDraw.puff(x, top + dy, z, 1.1 + random.nextDouble() * 0.9, shade, shade * 1.05f, shade * 1.25f,
                        0.3f * strength, (float) a);
            }
        }
    }

    private static void renderTornado(Tornado tornado, float t, float pt) {
        Vec3 c = tornado.center(t);
        float height = BlizzardSpell.height(t);
        float strength = BlizzardSpell.intensity(t);
        float circleAlpha = Mth.clamp(t / 10.0f, 0.0f, 1.0f) * Mth.clamp((tornado.duration - t) / 20.0f, 0.0f, 1.0f);

        renderSigil(c, t, circleAlpha);
        renderGroundWind(c, t, strength);
        if (height < 0.2f) return;

        renderSpikes(tornado, t, true);
        renderCore(c, height, strength, t);
        renderIceHeart(c, height, strength, t);
        renderGusts(c, strength, t, tornado.seed);
        renderFrostStrikes(tornado, t);
        renderRibbons(c, height, strength, t, 6, 0.34f, 0.72, 3.4, 0.55, 0.3f, false);
        renderRibbons(c, height, strength, t, 5, 0.21f, 1.02, 2.5, 0.42, 0.22f, true);
        renderWisps(c, height, strength, t);
        renderSnowWall(c, height, strength, t, tornado.seed);
        renderCloudGlow(tornado, c, height, strength, t);

        for (Shard s : tornado.shards) {
            FxDraw.glow(Mth.lerp(pt, s.px, s.x), Mth.lerp(pt, s.py, s.y), Mth.lerp(pt, s.pz, s.z),
                    s.length * 1.4, 0.5f, 0.85f, 1.0f, 0.3f * strength);
        }

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

    /** Ice spikes: translucent crystal bodies (matter pass) and frosty glowing tips (light pass). */
    private static void renderSpikes(Tornado tornado, float t, boolean light) {
        float end = Mth.clamp((tornado.duration - t) / 8.0f, 0.0f, 1.0f);
        for (Spike spike : tornado.spikes) {
            float g = Mth.clamp((t - spike.delay) / 5.0f, 0.0f, 1.0f);
            if (g <= 0.0f || end <= 0.0f) continue;
            float grow = 1.0f - (1.0f - g) * (1.0f - g) * (1.0f - g);
            float len = spike.length * grow;
            Vec3 center = spike.base.add(spike.dir.scale(len * 0.5));
            Vec3 tip = spike.base.add(spike.dir.scale(len));
            if (!light) {
                FxDraw.crystal(center.x, center.y, center.z, spike.rot, len, spike.thickness * grow, 0.55f, 0.8f, 1.0f, 0.82f * end);
            } else {
                float shimmer = 0.6f + 0.4f * (float) Math.sin(t * 0.25 + spike.base.x * 3.0);
                FxDraw.glow(tip.x, tip.y, tip.z, 0.25 + spike.thickness, 0.7f, 0.92f, 1.0f, 0.6f * shimmer * end);
                FxDraw.glow(center.x, center.y, center.z, len * 0.6, 0.4f, 0.75f, 1.0f, 0.12f * end);
                if (t - spike.delay < 6.0f) {
                    // Burst of frost where it broke through
                    float burst = 1.0f - (t - spike.delay) / 6.0f;
                    FxDraw.glow(spike.base.x, spike.base.y + 0.1, spike.base.z, 1.0 + spike.length * 0.4, 0.75f, 0.92f, 1.0f, 0.6f * burst);
                }
            }
        }
    }

    /** A glowing ice heart in the middle of the funnel, wrapped by three tilted rune rings turning on different axes. */
    private static void renderIceHeart(Vec3 c, float height, float strength, float t) {
        if (strength <= 0.05f || height < 2.0f) return;
        double f = 0.45;
        Vec3 heart = new Vec3(c.x + bendX(f, t), c.y + height * f, c.z + bendZ(f, t));
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.2);
        FxDraw.glow(heart.x, heart.y, heart.z, 1.6 + 0.4 * pulse, 0.5f, 0.82f, 1.0f, 0.35f * strength);
        FxDraw.glow(heart.x, heart.y, heart.z, 0.45 + 0.1 * pulse, 0.95f, 1.0f, 1.0f, 0.9f * strength);
        for (int k = 0; k < 3; k++) {
            double spin = t * (0.05 + 0.03 * k) * (k % 2 == 0 ? 1 : -1);
            double tilt = 0.6 + k * 0.5;
            Vec3 axisA = new Vec3(Math.cos(spin), 0, Math.sin(spin));
            Vec3 axisB = new Vec3(-Math.sin(spin) * Math.cos(tilt), Math.sin(tilt), Math.cos(spin) * Math.cos(tilt));
            double radius = 0.9 + k * 0.32;
            FxDraw.ring(heart, axisA, axisB, radius, 0.06, 0.7f, 0.92f, 1.0f, 0.75f * strength, 40);
            // Rune ticks travelling along each ring
            for (int i = 0; i < 6; i++) {
                double a = t * 0.1 * (k + 1) + i * Math.PI / 3.0;
                Vec3 p = heart.add(axisA.scale(Math.cos(a) * radius)).add(axisB.scale(Math.sin(a) * radius));
                FxDraw.glow(p.x, p.y, p.z, 0.1, 0.9f, 0.98f, 1.0f, 0.9f * strength);
            }
        }
    }

    /** Gusts: wind streaks sweeping around the storm in long arcs. */
    private static void renderGusts(Vec3 c, float strength, float t, long seed) {
        if (strength <= 0.05f) return;
        Random random = new Random(seed * 17);
        for (int k = 0; k < 22; k++) {
            double radius = 4.5 + random.nextDouble() * 5.0;
            double y = c.y + 0.3 + random.nextDouble() * 3.5;
            double phase = random.nextDouble() * Math.PI * 2.0;
            double speed = (0.3 + random.nextDouble() * 0.2) * (5.0 / radius);
            double span = 0.6 + random.nextDouble() * 0.6;
            double head = phase + t * speed;
            double prevX = 0, prevZ = 0;
            for (int i = 0; i <= 8; i++) {
                double a = head - span * i / 8.0;
                double x = c.x + Math.cos(a) * radius, z = c.z + Math.sin(a) * radius;
                if (i > 0) {
                    float fade = (float) Math.sin(Math.PI * i / 8.0);
                    FxDraw.beam(prevX, y, prevZ, x, y, z, 0.06, 0.85f, 0.95f, 1.0f, 0.45f * fade * strength, 0.45f * fade * strength);
                }
                prevX = x;
                prevZ = z;
            }
        }
    }

    private static void renderFrostStrikes(Tornado tornado, float t) {
        for (FrostStrike strike : tornado.strikes) {
            float age = t - strike.born();
            float life = 1.0f - age / 6.0f;
            if (life <= 0) continue;
            float flicker = (age < 1.0f || (age > 2.0f && age < 3.0f)) ? 1.0f : 0.5f;
            List<Vec3> path = BoltFx.jagged(strike.top(), strike.bottom(), strike.top().distanceTo(strike.bottom()) * 0.12,
                    new Random(strike.seed()));
            for (int i = 0; i + 1 < path.size(); i++) {
                FxDraw.beam(path.get(i), path.get(i + 1), 0.6, 0.45f, 0.75f, 1.0f, 0.2f * life * flicker);
                FxDraw.beam(path.get(i), path.get(i + 1), 0.14, 0.9f, 0.97f, 1.0f, life * flicker);
            }
            Vec3 b = strike.bottom();
            FxDraw.glow(b.x, b.y + 0.2, b.z, 1.8 * life, 0.6f, 0.85f, 1.0f, 0.6f * life);
        }
    }

    /** Spinning snowflake sigil with runic rings and pulses on the ground. */
    private static void renderSigil(Vec3 c, float t, float alpha) {
        if (alpha <= 0.01f) return;
        double y = c.y + 0.035;
        FxDraw.flatDisc(c.x, y, c.z, 4.6, 0.3f, 0.65f, 1.0f, 0.2f * alpha, 32);
        FxDraw.flatRing(c.x, y, c.z, 3.9, 0.22, 0.55f, 0.88f, 1.0f, 0.85f * alpha, 64);
        FxDraw.flatRing(c.x, y, c.z, 3.45, 0.09, 0.75f, 0.95f, 1.0f, 0.65f * alpha, 64);
        FxDraw.flatRing(c.x, y, c.z, 1.2, 0.1, 0.75f, 0.95f, 1.0f, 0.75f * alpha, 40);

        double rot = t * 0.02;
        for (int arm = 0; arm < 6; arm++) {
            double a = rot + arm * Math.PI / 3.0;
            double ca = Math.cos(a), sa = Math.sin(a);
            FxDraw.groundLine(c.x + ca * 1.2, c.z + sa * 1.2, c.x + ca * 3.45, c.z + sa * 3.45, y, 0.1, 0.7f, 0.92f, 1.0f, 0.8f * alpha);
            for (double at : new double[]{0.45, 0.68}) {
                double bx = c.x + ca * (1.2 + 2.25 * at), bz = c.z + sa * (1.2 + 2.25 * at);
                double len = at < 0.5 ? 0.6 : 0.42;
                for (int side = -1; side <= 1; side += 2) {
                    double ba = a + side * 0.85;
                    FxDraw.groundLine(bx, bz, bx + Math.cos(ba) * len, bz + Math.sin(ba) * len, y, 0.08, 0.7f, 0.92f, 1.0f, 0.7f * alpha);
                }
            }
        }

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
                FxDraw.groundLine(p0[0], p0[1], p1[0], p1[1], y + 0.002, 0.05, 0.85f, 0.97f, 1.0f, 0.85f * alpha);
            }
        }

        float pulse = (t % 25.0f) / 25.0f;
        FxDraw.flatRing(c.x, y + 0.004, c.z, 0.6 + pulse * 4.6, 0.35, 0.6f, 0.9f, 1.0f, 0.5f * (1.0f - pulse) * alpha, 48);
    }

    /** Spiral gusts sweeping across the ground at the base. */
    private static void renderGroundWind(Vec3 c, float t, float strength) {
        if (strength <= 0.02f) return;
        double y = c.y + 0.05;
        for (int arm = 0; arm < 4; arm++) {
            double base = t * 0.3 + arm * Math.PI / 2.0;
            double prevX = c.x, prevZ = c.z;
            for (int i = 1; i <= 16; i++) {
                double f = i / 16.0;
                double r = 0.3 + f * 3.6;
                double a = base + f * 2.6;
                double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                FxDraw.groundLine(prevX, prevZ, x, z, y, 0.22 * (1 - f) + 0.05, 0.8f, 0.95f, 1.0f, 0.4f * (1.0f - (float) f) * strength);
                prevX = x;
                prevZ = z;
            }
        }
    }

    /** Column of light at the heart of the funnel, following its bend. */
    private static void renderCore(Vec3 c, float height, float strength, float t) {
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 0.7);
        double prevX = c.x, prevY = c.y, prevZ = c.z;
        for (int k = 1; k <= 8; k++) {
            double f = k / 8.0;
            double x = c.x + bendX(f, t), y = c.y + height * f, z = c.z + bendZ(f, t);
            FxDraw.beam(prevX, prevY, prevZ, x, y, z, 0.9, 0.55f, 0.85f, 1.0f, 0.3f * strength * flicker, 0.3f * strength * flicker);
            FxDraw.beam(prevX, prevY, prevZ, x, y, z, 0.2, 1.0f, 1.0f, 1.0f, 0.65f * strength * flicker, 0.65f * strength * flicker);
            FxDraw.glow(x, y, z, BlizzardSpell.funnelRadius(f) * 0.8, 0.55f, 0.82f, 1.0f, 0.08f * strength);
            prevX = x;
            prevY = y;
            prevZ = z;
        }
        FxDraw.glow(c.x, c.y + 0.3, c.z, 1.8, 0.7f, 0.92f, 1.0f, 0.45f * strength);
    }

    /** Twisting translucent bands along the funnel surface with soft edges. */
    private static void renderRibbons(Vec3 c, float height, float strength, float t, int count, float speed,
                                      double radiusScale, double twist, double angularWidth, float alpha, boolean aurora) {
        int steps = 28;
        for (int band = 0; band < count; band++) {
            double offset = band * Math.PI * 2.0 / count;
            for (int i = 0; i < steps; i++) {
                double f0 = (double) i / steps, f1 = (double) (i + 1) / steps;
                double y0 = c.y + f0 * height, y1 = c.y + f1 * height;
                Vec3 c0 = new Vec3(c.x + bendX(f0, t), 0, c.z + bendZ(f0, t));
                Vec3 c1 = new Vec3(c.x + bendX(f1, t), 0, c.z + bendZ(f1, t));
                double r0 = BlizzardSpell.funnelRadius(f0) * radiusScale * (1.0 + 0.07 * Math.sin(f0 * 9.0 + t * 0.3 + band));
                double r1 = BlizzardSpell.funnelRadius(f1) * radiusScale * (1.0 + 0.07 * Math.sin(f1 * 9.0 + t * 0.3 + band));
                double a0 = t * speed + offset + f0 * twist;
                double a1 = t * speed + offset + f1 * twist;
                float env0 = (float) Math.sqrt(Math.sin(Math.PI * Math.max(0.03, f0)));
                float env1 = (float) Math.sqrt(Math.sin(Math.PI * Math.min(0.97, f1)));
                float[] col0 = ribbonColor(f0, t, band, aurora);
                float[] col1 = ribbonColor(f1, t, band, aurora);
                float al0 = alpha * strength * env0;
                float al1 = alpha * strength * env1;
                double w = angularWidth;
                strip(c0, c1, y0, y1, r0, r1, a0, a1, a0 + w * 0.5, a1 + w * 0.5, col0, col1, 0, 0, al0, al1);
                strip(c0, c1, y0, y1, r0, r1, a0 + w * 0.5, a1 + w * 0.5, a0 + w, a1 + w, col0, col1, al0, al1, 0, 0);
            }
        }
    }

    private static float[] ribbonColor(double f, float t, int band, boolean aurora) {
        if (!aurora) {
            float k = (float) f;
            return new float[]{0.55f + 0.4f * k, 0.85f + 0.13f * k, 1.0f};
        }
        float shift = 0.5f + 0.5f * (float) Math.sin(t * 0.05 + f * 3.0 + band * 1.3);
        return new float[]{0.3f + 0.5f * shift, 0.9f - 0.35f * shift, 1.0f};
    }

    private static void strip(Vec3 c0, Vec3 c1, double y0, double y1, double r0, double r1,
                              double aStart0, double aStart1, double aEnd0, double aEnd1,
                              float[] col0, float[] col1, float s0, float s1, float e0, float e1) {
        FxDraw.vertex(c0.x + Math.cos(aStart0) * r0, y0, c0.z + Math.sin(aStart0) * r0, col0[0], col0[1], col0[2], s0);
        FxDraw.vertex(c0.x + Math.cos(aEnd0) * r0, y0, c0.z + Math.sin(aEnd0) * r0, col0[0], col0[1], col0[2], e0);
        FxDraw.vertex(c1.x + Math.cos(aEnd1) * r1, y1, c1.z + Math.sin(aEnd1) * r1, col1[0], col1[1], col1[2], e1);
        FxDraw.vertex(c1.x + Math.cos(aStart1) * r1, y1, c1.z + Math.sin(aStart1) * r1, col1[0], col1[1], col1[2], s1);
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
                double x = c.x + bendX(f, t) + Math.cos(a) * r, y = c.y + f * height, z = c.z + bendZ(f, t) + Math.sin(a) * r;
                if (i > 0) {
                    float fade = (float) Math.sin(Math.PI * f);
                    FxDraw.beam(prevX, prevY, prevZ, x, y, z, 0.09, 0.9f, 0.97f, 1.0f, 0.45f * strength * fade, 0.45f * strength * fade);
                }
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        }
    }

    /** Glittering snow orbiting the funnel. */
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
            FxDraw.glow(c.x + bendX(climb, t) + Math.cos(a) * r, c.y + climb * height, c.z + bendZ(climb, t) + Math.sin(a) * r,
                    0.09 + 0.05 * twinkle, 0.9f, 0.97f, 1.0f, 0.8f * strength * twinkle);
        }
    }

    /** Frosty light under the wall cloud, and lightning flickering inside it. */
    private static void renderCloudGlow(Tornado tornado, Vec3 c, float height, float strength, float t) {
        double top = c.y + height;
        double topX = c.x + bendX(1.0, t), topZ = c.z + bendZ(1.0, t);
        double r = BlizzardSpell.funnelRadius(1.0);
        for (int k = 0; k < 3; k++) {
            double wobble = Math.sin(t * 0.08 + k * 2.1) * 0.15;
            FxDraw.flatRing(topX, top - 0.3 + wobble + k * 0.12, topZ, r * (1.05 + 0.18 * k), 0.4, 0.7f, 0.88f, 1.0f, 0.25f * strength, 40);
        }
        float since = tornado.age - tornado.cloudFlash + (t - tornado.age);
        if (since >= 0 && since < 6) {
            float f = 1.0f - since / 6.0f;
            float flicker = (since < 1 || (since > 2 && since < 3)) ? 1.0f : 0.5f;
            double a = tornado.cloudFlashAngle;
            double fx = topX + Math.cos(a) * r * 1.4, fz = topZ + Math.sin(a) * r * 1.4;
            FxDraw.glow(fx, top + 0.2, fz, 3.5, 0.75f, 0.85f, 1.0f, 0.6f * f * flicker * strength);
            FxDraw.glow(fx, top + 0.2, fz, 1.2, 1.0f, 1.0f, 1.0f, 0.8f * f * flicker * strength);
        }
    }
}
