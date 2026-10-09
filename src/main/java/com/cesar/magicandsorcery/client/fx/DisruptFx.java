package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Visual effects for Disrupt:
 * A destabilizing chromatic shockwave distortion, radiant 4-pointed energy flare representing the broken magic,
 * and outward dispersion particles.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DisruptFx {

    public static final class DisruptBurst {
        final int targetEntityId;
        final Vec3 fallbackPos;
        final boolean isProjectile;
        int age = 0;
        final int maxLife = 18; // ~0.9s duration

        DisruptBurst(int targetEntityId, Vec3 fallbackPos, boolean isProjectile) {
            this.targetEntityId = targetEntityId;
            this.fallbackPos = fallbackPos;
            this.isProjectile = isProjectile;
        }

        Vec3 getPos(float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && targetEntityId >= 0) {
                Entity e = mc.level.getEntity(targetEntityId);
                if (e != null && e.isAlive()) {
                    return e.getPosition(partialTick).add(0, e.getBbHeight() * 0.5, 0);
                }
            }
            return fallbackPos;
        }
    }

    private static final List<DisruptBurst> BURSTS = new ArrayList<>();

    private DisruptFx() {
    }

    public static void add(int targetEntityId, Vec3 pos, boolean isProjectile) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BURSTS.add(new DisruptBurst(targetEntityId, pos, isProjectile));

        // Initial dispersion particles bursting outward
        for (int i = 0; i < 28; i++) {
            double vx = (Math.random() - 0.5) * 0.6;
            double vy = (Math.random() - 0.5) * 0.6;
            double vz = (Math.random() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, vx, vy, vz);
        }
        for (int i = 0; i < 16; i++) {
            double vx = (Math.random() - 0.5) * 0.4;
            double vy = (Math.random() - 0.5) * 0.4;
            double vz = (Math.random() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, vx, vy, vz);
        }
        for (int i = 0; i < 14; i++) {
            double vx = (Math.random() - 0.5) * 0.3;
            double vy = (Math.random() - 0.5) * 0.3;
            double vz = (Math.random() - 0.5) * 0.3;
            mc.level.addParticle(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, vx, vy, vz);
        }
        mc.level.addParticle(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 0, 0, 0);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (BURSTS.isEmpty()) return;

        Iterator<DisruptBurst> it = BURSTS.iterator();
        while (it.hasNext()) {
            DisruptBurst b = it.next();
            b.age++;
            if (b.age >= b.maxLife) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (BURSTS.isEmpty()) return;
        if (!FxDraw.begin(event)) return;

        float partial = FxDraw.partialTick();

        for (DisruptBurst b : BURSTS) {
            Vec3 pos = b.getPos(partial);
            float t = (b.age + partial) / (float) b.maxLife;
            t = Mth.clamp(t, 0.0f, 1.0f);

            // 1. Horizontal Expanding Shockwave Ring (Violet/Magenta)
            float r1 = 0.3f + 2.8f * (float) Math.pow(t, 0.55);
            float a1 = (1.0f - t) * 0.85f;
            FxDraw.flatRing(pos.x, pos.y, pos.z, r1, 0.24,
                    0.80f, 0.25f, 0.95f, a1, 32);

            // 2. Secondary Expanding Cyan Ring
            float r2 = 0.2f + 2.2f * (float) Math.pow(t, 0.65);
            float a2 = (1.0f - t) * 0.75f;
            FxDraw.flatRing(pos.x, pos.y + 0.05, pos.z, r2, 0.18,
                    0.20f, 0.85f, 1.00f, a2, 32);

            // 3. Central Radiant Energy Flare (First half of animation)
            if (t < 0.6f) {
                float starT = t / 0.6f;
                double flareSize = (1.0 - starT) * (b.isProjectile ? 1.4 : 2.2);
                float starAlpha = (1.0f - starT) * 0.95f;

                Vec3 cameraPos = FxDraw.camera();
                Vec3 toCam = cameraPos.subtract(pos);
                Vec3[] basis = FxDraw.basis(toCam);
                Vec3 right = basis[0];
                Vec3 up = basis[1];

                Vec3 h1 = pos.add(right.scale(-flareSize));
                Vec3 h2 = pos.add(right.scale(flareSize));
                Vec3 v1 = pos.add(up.scale(-flareSize));
                Vec3 v2 = pos.add(up.scale(flareSize));

                double thick = 0.12 * (1.0 - starT);
                FxDraw.beam(h1.x, h1.y, h1.z, h2.x, h2.y, h2.z, thick,
                        0.95f, 0.45f, 1.0f, 0.05f, starAlpha);
                FxDraw.beam(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, thick,
                        0.95f, 0.45f, 1.0f, 0.05f, starAlpha);

                // Central bright radiant orb
                FxDraw.glow(pos.x, pos.y, pos.z, flareSize * 0.45, 0.85f, 0.35f, 1.0f, starAlpha);
                FxDraw.glow(pos.x, pos.y, pos.z, flareSize * 0.18, 1.0f, 1.0f, 1.0f, starAlpha);
            }
        }

        FxDraw.end();
    }
}
