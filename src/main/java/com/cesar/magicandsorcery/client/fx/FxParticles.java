package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side spell particles with simple physics: gravity, drag, bouncing off blocks,
 * fizzling in water and ice shards that shatter on impact.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FxParticles {

    public enum Kind {
        /** Bright streak stretched along its motion. */
        SPARK,
        /** Soft floating light. */
        GLOW,
        /** Spinning ice crystal that shatters after bouncing. */
        SHARD,
        /** Tiny snow/frost mote that drifts. */
        FLAKE
    }

    public static final class P {
        public Kind kind = Kind.GLOW;
        public double x, y, z, px, py, pz;
        public double vx, vy, vz;
        public float r = 1, g = 1, b = 1;
        public float size = 0.1f;
        public int age, life = 20;
        public double gravity = 0.0;
        public double drag = 0.98;
        public double bounce = 0.4;
        public boolean collide = true;
        public int bounces;
        public final Quaternionf rot = new Quaternionf();
        public final Quaternionf prevRot = new Quaternionf();
        public float spinX, spinY, spinZ;

        public P at(double x, double y, double z) {
            this.x = this.px = x;
            this.y = this.py = y;
            this.z = this.pz = z;
            return this;
        }

        public P vel(double vx, double vy, double vz) {
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            return this;
        }

        public P color(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
            return this;
        }

        public P color(int rgb) {
            return color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        }

        public P life(int life) {
            this.life = life;
            return this;
        }

        public P size(float size) {
            this.size = size;
            return this;
        }

        public P physics(double gravity, double drag, double bounce) {
            this.gravity = gravity;
            this.drag = drag;
            this.bounce = bounce;
            return this;
        }

        public P noCollide() {
            this.collide = false;
            return this;
        }

        public P spin(RandomSource random, float speed) {
            rot.rotateXYZ(random.nextFloat() * 6.28f, random.nextFloat() * 6.28f, random.nextFloat() * 6.28f);
            prevRot.set(rot);
            spinX = (random.nextFloat() - 0.5f) * speed;
            spinY = (random.nextFloat() - 0.5f) * speed;
            spinZ = (random.nextFloat() - 0.5f) * speed;
            return this;
        }

        float fade(float partial) {
            float t = (age + partial) / life;
            return Mth.clamp(1.0f - t * t, 0.0f, 1.0f);
        }
    }

    private static final int MAX_PARTICLES = 3000;
    private static final List<P> PARTICLES = new ArrayList<>();
    private static final List<P> PENDING = new ArrayList<>();

    private FxParticles() {
    }

    public static P spawn(Kind kind) {
        P p = new P();
        p.kind = kind;
        if (PARTICLES.size() + PENDING.size() < MAX_PARTICLES) {
            PENDING.add(p);
        }
        return p;
    }

    /**
     * Radial burst of sparks.
     */
    public static void sparkBurst(double x, double y, double z, int count, double speed, int rgb, RandomSource random) {
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double s = speed * (0.35 + random.nextDouble() * 0.65);
            double vx = Math.sin(phi) * Math.cos(theta) * s;
            double vy = Math.abs(Math.cos(phi)) * s * 0.9 + 0.05;
            double vz = Math.sin(phi) * Math.sin(theta) * s;
            spawn(Kind.SPARK).at(x, y, z).vel(vx, vy, vz).color(rgb)
                    .size(0.035f + random.nextFloat() * 0.03f)
                    .life(14 + random.nextInt(18))
                    .physics(0.045, 0.94, 0.5);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PARTICLES.clear();
            PENDING.clear();
            return;
        }
        if (mc.isPaused()) return;

        PARTICLES.addAll(PENDING);
        PENDING.clear();

        Level level = mc.level;
        Iterator<P> it = PARTICLES.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.age++;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            p.px = p.x;
            p.py = p.y;
            p.pz = p.z;
            p.prevRot.set(p.rot);
            if (p.spinX != 0 || p.spinY != 0 || p.spinZ != 0) {
                p.rot.rotateXYZ(p.spinX, p.spinY, p.spinZ);
            }

            p.vy -= p.gravity;
            p.vx *= p.drag;
            p.vy *= p.drag;
            p.vz *= p.drag;

            if (p.kind == Kind.FLAKE) {
                // Gentle flutter
                p.vx += (level.random.nextDouble() - 0.5) * 0.01;
                p.vz += (level.random.nextDouble() - 0.5) * 0.01;
            }

            if (!p.collide) {
                p.x += p.vx;
                p.y += p.vy;
                p.z += p.vz;
                continue;
            }

            double nx = p.x + p.vx;
            if (solid(level, nx, p.y, p.z)) {
                p.vx = -p.vx * p.bounce;
                nx = p.x;
                onBounce(p, level);
            }
            double ny = p.y + p.vy;
            if (solid(level, nx, ny, p.z)) {
                if (p.vy < 0) {
                    // Resting on the ground: friction
                    p.vx *= 0.7;
                    p.vz *= 0.7;
                }
                p.vy = -p.vy * p.bounce;
                ny = p.y;
                onBounce(p, level);
            }
            double nz = p.z + p.vz;
            if (solid(level, nx, ny, nz)) {
                p.vz = -p.vz * p.bounce;
                nz = p.z;
                onBounce(p, level);
            }
            p.x = nx;
            p.y = ny;
            p.z = nz;

            // Hot sparks fizzle out in water
            if (p.kind == Kind.SPARK && !level.getFluidState(BlockPos.containing(p.x, p.y, p.z)).isEmpty()) {
                level.addParticle(ParticleTypes.BUBBLE, p.x, p.y, p.z, 0, 0.05, 0);
                level.addParticle(ParticleTypes.CLOUD, p.x, p.y + 0.2, p.z, 0, 0.02, 0);
                p.age = p.life;
            }
        }
    }

    private static void onBounce(P p, Level level) {
        p.bounces++;
        if (p.kind == Kind.SHARD && p.bounces >= 2 && p.age < p.life) {
            // The crystal shatters into glittering splinters
            for (int i = 0; i < 6; i++) {
                spawn(Kind.SPARK).at(p.x, p.y + 0.05, p.z)
                        .vel((level.random.nextDouble() - 0.5) * 0.25, level.random.nextDouble() * 0.2, (level.random.nextDouble() - 0.5) * 0.25)
                        .color(p.r, p.g, p.b).size(0.025f).life(10 + level.random.nextInt(8)).physics(0.04, 0.92, 0.3);
            }
            p.age = p.life;
        }
    }

    private static boolean solid(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        AABB box = shape.bounds();
        double lx = x - pos.getX();
        double ly = y - pos.getY();
        double lz = z - pos.getZ();
        return lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (PARTICLES.isEmpty() || !FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();
        Quaternionf q = new Quaternionf();

        // Ice crystals first (translucent matter), then all light on top
        FxDraw.solid();
        for (P p : PARTICLES) {
            if (p.kind != Kind.SHARD) continue;
            double x = Mth.lerp(pt, p.px, p.x), y = Mth.lerp(pt, p.py, p.y), z = Mth.lerp(pt, p.pz, p.z);
            p.prevRot.slerp(p.rot, pt, q);
            float a = p.fade(pt);
            FxDraw.crystal(x, y, z, q, p.size * 3.0f, p.size * 0.7f, p.r * 0.75f, p.g * 0.9f, p.b, 0.75f * a);
        }

        FxDraw.glow();
        for (P p : PARTICLES) {
            double x = Mth.lerp(pt, p.px, p.x), y = Mth.lerp(pt, p.py, p.y), z = Mth.lerp(pt, p.pz, p.z);
            float a = p.fade(pt);
            switch (p.kind) {
                case SPARK -> {
                    // Motion-blurred streak with a white-hot core
                    double tx = x - p.vx * 1.6, ty = y - p.vy * 1.6, tz = z - p.vz * 1.6;
                    FxDraw.beam(tx, ty, tz, x, y, z, p.size * 3.0, p.r, p.g, p.b, 0.0f, 0.9f * a);
                    FxDraw.beam(tx, ty, tz, x, y, z, p.size, 1.0f, 1.0f, 1.0f, 0.0f, a);
                    FxDraw.glow(x, y, z, p.size * 4.0, p.r, p.g, p.b, 0.35f * a);
                }
                case GLOW -> FxDraw.glow(x, y, z, p.size, p.r, p.g, p.b, 0.8f * a);
                case SHARD -> FxDraw.glow(x, y, z, p.size * 3.5, p.r, p.g, p.b, 0.25f * a);
                case FLAKE -> FxDraw.glow(x, y, z, p.size, p.r, p.g, p.b, 0.9f * a);
            }
        }
        FxDraw.end();
    }
}
