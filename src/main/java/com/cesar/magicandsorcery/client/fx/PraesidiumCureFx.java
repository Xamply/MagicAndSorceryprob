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
 * Visual effects for Praesidium cure:
 * A rapid upward swirling gust of emerald energy wrapping around the player,
 * ground glowing light pool, central healing pillar, and a radiant 4-pointed star at the crest.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PraesidiumCureFx {

    public static final class CureBurst {
        final int targetEntityId;
        final Vec3 fallbackPos;
        final float height;
        final float width;
        int age = 0;
        final int maxLife = 24; // ~1.2s total lifetime

        CureBurst(int targetEntityId, Vec3 fallbackPos, float height, float width) {
            this.targetEntityId = targetEntityId;
            this.fallbackPos = fallbackPos;
            this.height = Math.max(1.2f, height);
            this.width = Math.max(0.6f, width);
        }

        Vec3 getPos(float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && targetEntityId >= 0) {
                Entity e = mc.level.getEntity(targetEntityId);
                if (e != null && e.isAlive()) {
                    return e.getPosition(partialTick);
                }
            }
            return fallbackPos;
        }
    }

    private static final List<CureBurst> BURSTS = new ArrayList<>();

    private PraesidiumCureFx() {
    }

    public static void add(int targetEntityId, Vec3 pos, float height, float width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BURSTS.add(new CureBurst(targetEntityId, pos, height, width));

        // Initial burst of emerald sparkles
        for (int i = 0; i < 18; i++) {
            double angle = mc.level.random.nextDouble() * Math.PI * 2.0;
            double r = 0.4 + mc.level.random.nextDouble() * 0.6;
            double sx = pos.x + Math.cos(angle) * r;
            double sy = pos.y + 0.1 + mc.level.random.nextDouble() * height;
            double sz = pos.z + Math.sin(angle) * r;
            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, sx, sy, sz, 0, 0.08, 0);
            mc.level.addParticle(ParticleTypes.GLOW, sx, sy, sz, (Math.random() - 0.5) * 0.04, 0.05, (Math.random() - 0.5) * 0.04);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (BURSTS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Iterator<CureBurst> it = BURSTS.iterator();
        while (it.hasNext()) {
            CureBurst b = it.next();
            b.age++;

            if (mc.level != null && b.age < 14) {
                Vec3 p = b.getPos(1.0f);
                double angle = (b.age * 0.75) + mc.level.random.nextDouble() * 0.4;
                double r = b.width * 0.75;
                double h = (b.age / 14.0) * b.height;
                mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                        p.x + Math.cos(angle) * r, p.y + h, p.z + Math.sin(angle) * r,
                        -Math.sin(angle) * 0.06, 0.12, Math.cos(angle) * 0.06);
            }

            if (b.age >= b.maxLife) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (BURSTS.isEmpty() || !FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();
        for (CureBurst b : BURSTS) {
            renderBurst(b, b.age + pt);
        }
        FxDraw.end();
    }

    private static void renderBurst(CureBurst b, float t) {
        float lifeFrac = t / b.maxLife;
        if (lifeFrac >= 1.0f) return;

        float fadeIn = Mth.clamp(t / 3.5f, 0.0f, 1.0f);
        float fadeOut = Mth.clamp((b.maxLife - t) / 7.0f, 0.0f, 1.0f);
        float alpha = fadeIn * fadeOut;
        if (alpha <= 0.005f) return;

        Vec3 pos = b.getPos(FxDraw.partialTick());
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        float height = b.height;
        float baseR = b.width * 0.85f + 0.15f;

        // 1. Luminous Emerald Ground Pool
        FxDraw.flatRing(x, y + 0.04, z, baseR * 1.1, 0.32, 0.25f, 1.0f, 0.45f, alpha * 0.85f, 32);
        FxDraw.flatRing(x, y + 0.03, z, baseR * 1.5, 0.42, 0.15f, 0.85f, 0.35f, alpha * 0.4f, 32);
        FxDraw.glow(x, y + 0.08, z, baseR * 1.35, 0.3f, 1.0f, 0.5f, alpha * 0.5f);

        // 2. Central Healing Pillar
        FxDraw.beam(x, y, z, x, y + height + 0.7, z, 0.38, 0.2f, 0.95f, 0.4f, 0.0f, 0.45f * alpha);
        FxDraw.beam(x, y, z, x, y + height + 0.8, z, 0.12, 0.85f, 1.0f, 0.85f, 0.1f * alpha, 0.8f * alpha);

        // 3. Rapid Helical Gust (2 ribbon strands winding upwards around player)
        int strands = 2;
        int segments = 28;
        double totalRotations = 2.4;
        for (int s = 0; s < strands; s++) {
            double strandOffset = s * Math.PI;
            Vec3 prevPt = null;

            for (int i = 0; i <= segments; i++) {
                double frac = (double) i / segments;
                // Height rises with progress
                double currY = y + frac * (height + 0.3);
                // Swirl angle rotates rapidly around entity
                double angle = frac * totalRotations * Math.PI * 2.0 + (t * 0.4) + strandOffset;
                // Ribbon radius slightly wider in mid-height
                double radius = baseR * (0.85 + 0.3 * Math.sin(frac * Math.PI));
                Vec3 currPt = new Vec3(x + Math.cos(angle) * radius, currY, z + Math.sin(angle) * radius);

                if (prevPt != null) {
                    float segAlpha = alpha * (float) (Math.sin(frac * Math.PI) * 0.95);
                    // Core bright lime ribbon
                    FxDraw.beam(prevPt.x, prevPt.y, prevPt.z, currPt.x, currPt.y, currPt.z, 0.12,
                            0.8f, 1.0f, 0.8f, segAlpha, segAlpha);
                    // Emerald aura halo ribbon
                    FxDraw.beam(prevPt.x, prevPt.y, prevPt.z, currPt.x, currPt.y, currPt.z, 0.28,
                            0.2f, 1.0f, 0.45f, segAlpha * 0.6f, segAlpha * 0.6f);
                }
                prevPt = currPt;
            }
        }

        // 4. Radiant 4-Pointed Star / Cross at Crest (Head Height)
        double starY = y + height + 0.2;
        float starIntensity = Mth.clamp((t - 1.5f) / 4.0f, 0.0f, 1.0f) * fadeOut;
        if (starIntensity > 0.01f) {
            double flareW = 0.75;
            double flareH = 0.85;
            double flareThick = 0.07;

            // Camera-facing cross flares
            Vec3 cameraPos = FxDraw.camera();
            Vec3 toCam = cameraPos.subtract(new Vec3(x, starY, z));
            Vec3[] basis = FxDraw.basis(toCam);
            Vec3 right = basis[0];
            Vec3 up = basis[1];

            Vec3 starCenter = new Vec3(x, starY, z);
            Vec3 h1 = starCenter.add(right.scale(-flareW));
            Vec3 h2 = starCenter.add(right.scale(flareW));
            Vec3 v1 = starCenter.add(up.scale(-flareH));
            Vec3 v2 = starCenter.add(up.scale(flareH));

            // Horizontal flare
            FxDraw.beam(h1.x, h1.y, h1.z, h2.x, h2.y, h2.z, flareThick * 2.2,
                    0.25f, 1.0f, 0.45f, 0.0f, starIntensity * 0.7f);
            FxDraw.beam(h1.x, h1.y, h1.z, h2.x, h2.y, h2.z, flareThick,
                    0.9f, 1.0f, 0.9f, 0.05f, starIntensity * 0.95f);

            // Vertical flare
            FxDraw.beam(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, flareThick * 2.2,
                    0.25f, 1.0f, 0.45f, 0.0f, starIntensity * 0.7f);
            FxDraw.beam(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, flareThick,
                    0.9f, 1.0f, 0.9f, 0.05f, starIntensity * 0.95f);

            // Center radiant orb
            FxDraw.glow(x, starY, z, 0.65, 0.3f, 1.0f, 0.5f, starIntensity * 0.8f);
            FxDraw.glow(x, starY, z, 0.22, 0.9f, 1.0f, 0.9f, starIntensity * 1.0f);
        }
    }
}
