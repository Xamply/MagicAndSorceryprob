package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientTornadoHandler {

    public static class ActiveTornado {
        final Vec3 center;
        final int maxAge;
        final long seed;
        int age;

        ActiveTornado(Vec3 center, int maxAge, long seed) {
            this.center = center;
            this.maxAge = maxAge;
            this.seed = seed;
            this.age = 0;
        }
    }

    private static final List<ActiveTornado> TORNADOES = new ArrayList<>();

    public static void addTornado(Vec3 center, int durationTicks, long seed) {
        TORNADOES.add(new ActiveTornado(center, durationTicks, seed));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (TORNADOES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        net.minecraft.util.RandomSource rand = mc.level.random;
        Iterator<ActiveTornado> iterator = TORNADOES.iterator();

        while (iterator.hasNext()) {
            ActiveTornado tornado = iterator.next();
            tornado.age++;

            // Emit dynamic spiraling ice and snowflake particles
            Vec3 pos = tornado.center;
            for (int p = 0; p < 5; p++) {
                double yRel = rand.nextDouble() * 4.0;
                // Conical radius: ~0.35 at base up to ~1.50 at top (diameter 3.0)
                double r = 0.35 + 1.15 * Math.pow(yRel / 4.0, 0.85);
                double theta = (tornado.age + rand.nextFloat()) * 0.45 + (yRel * 1.6) + (rand.nextDouble() * Math.PI * 2.0);

                double px = pos.x + Math.cos(theta) * r;
                double py = pos.y + yRel;
                double pz = pos.z + Math.sin(theta) * r;

                // Tangential vortex velocity + upward lift
                double vx = -Math.sin(theta) * 0.28;
                double vy = 0.12 + rand.nextDouble() * 0.08;
                double vz = Math.cos(theta) * 0.28;

                mc.level.addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, vx, vy, vz);
            }

            // Base ground splash particles (frost poofs and ice scraping)
            for (int b = 0; b < 2; b++) {
                double bAngle = rand.nextDouble() * Math.PI * 2.0;
                double bDist = 0.2 + rand.nextDouble() * 1.1;
                double bx = pos.x + Math.cos(bAngle) * bDist;
                double bz = pos.z + Math.sin(bAngle) * bDist;
                mc.level.addParticle(ParticleTypes.POOF, bx, pos.y + 0.05, bz,
                        Math.cos(bAngle) * 0.06, 0.02, Math.sin(bAngle) * 0.06);
            }

            if (tornado.age >= tornado.maxAge) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (ShaderCompatHelper.isInvalidRenderPass()) {
            return;
        }

        if (TORNADOES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lightning());

        for (ActiveTornado tornado : TORNADOES) {
            renderTornado(consumer, matrix, tornado, partialTick);
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderTornado(VertexConsumer consumer, Matrix4f matrix, ActiveTornado tornado, float partialTick) {
        float exactAge = tornado.age + partialTick;

        // Smooth fade-in (first 6 ticks) and fade-out (last 10 ticks)
        float lifeProgress = exactAge / (float) tornado.maxAge;
        float alpha = 1.0f;
        if (tornado.age < 6) {
            alpha = exactAge / 6.0f;
        } else if (tornado.maxAge - tornado.age < 10) {
            alpha = Math.max(0.0f, (tornado.maxAge - exactAge) / 10.0f);
        }

        Vec3 c = tornado.center;
        float cx = (float) c.x;
        float cy = (float) c.y;
        float cz = (float) c.z;

        // Fast vortex rotation (rad/tick)
        float baseRot = exactAge * 0.38f;

        // --- 1. Base Ground Splash / Frost Disc (Y = 0.03) ---
        int groundSpikes = 14;
        float groundRot = -exactAge * 0.25f;
        float groundRadius = 1.25f;

        for (int i = 0; i < groundSpikes; i++) {
            double a0 = groundRot + (i * 2.0 * Math.PI / groundSpikes);
            double a1 = groundRot + ((i + 0.6) * 2.0 * Math.PI / groundSpikes);
            double aMid = groundRot + ((i + 0.3) * 2.0 * Math.PI / groundSpikes);

            float x0 = cx + (float) (Math.cos(a0) * 0.25);
            float z0 = cz + (float) (Math.sin(a0) * 0.25);
            float x1 = cx + (float) (Math.cos(a1) * 0.25);
            float z1 = cz + (float) (Math.sin(a1) * 0.25);
            float tipR = groundRadius * (0.85f + 0.30f * (float) Math.sin(i * 1.7 + exactAge * 0.15));
            float xTip = cx + (float) (Math.cos(aMid) * tipR);
            float zTip = cz + (float) (Math.sin(aMid) * tipR);

            float gy = cy + 0.03f;
            // Brilliant white center to cyan fade at tips
            consumer.vertex(matrix, x0, gy, z0).color(0.95f, 0.98f, 1.0f, 0.70f * alpha).endVertex();
            consumer.vertex(matrix, x1, gy, z1).color(0.95f, 0.98f, 1.0f, 0.70f * alpha).endVertex();
            consumer.vertex(matrix, xTip, gy + 0.08f, zTip).color(0.30f, 0.80f, 1.0f, 0.25f * alpha).endVertex();
            consumer.vertex(matrix, x0, gy, z0).color(0.95f, 0.98f, 1.0f, 0.70f * alpha).endVertex();
        }

        // --- 2. Inner Dense Luminous Funnel Core (Height 4.0, Narrow Base to Top) ---
        int heightSteps = 16;
        float totalHeight = 4.0f;
        int radialSlices = 12;
        float innerRot = baseRot * 1.25f;

        for (int h = 0; h < heightSteps; h++) {
            float y0 = cy + (h * (totalHeight / heightSteps));
            float y1 = cy + ((h + 1) * (totalHeight / heightSteps));

            float progress0 = (float) h / heightSteps;
            float progress1 = (float) (h + 1) / heightSteps;

            // Core radius: 0.18 at base up to 0.75 at top
            float r0 = 0.18f + 0.57f * (float) Math.pow(progress0, 0.85);
            float r1 = 0.18f + 0.57f * (float) Math.pow(progress1, 0.85);

            float twist0 = innerRot + progress0 * 1.8f;
            float twist1 = innerRot + progress1 * 1.8f;

            for (int s = 0; s < radialSlices; s++) {
                double a0_0 = twist0 + (s * 2.0 * Math.PI / radialSlices);
                double a0_1 = twist0 + ((s + 1) * 2.0 * Math.PI / radialSlices);
                double a1_0 = twist1 + (s * 2.0 * Math.PI / radialSlices);
                double a1_1 = twist1 + ((s + 1) * 2.0 * Math.PI / radialSlices);

                float p0x = cx + (float) Math.cos(a0_0) * r0;
                float p0z = cz + (float) Math.sin(a0_0) * r0;
                float p1x = cx + (float) Math.cos(a0_1) * r0;
                float p1z = cz + (float) Math.sin(a0_1) * r0;

                float p2x = cx + (float) Math.cos(a1_1) * r1;
                float p2z = cz + (float) Math.sin(a1_1) * r1;
                float p3x = cx + (float) Math.cos(a1_0) * r1;
                float p3z = cz + (float) Math.sin(a1_0) * r1;

                // Glowing arctic white-cyan core
                float rCol = 0.90f;
                float gCol = 0.96f;
                float bCol = 1.00f;
                float aCore = 0.55f * alpha;

                consumer.vertex(matrix, p0x, y0, p0z).color(rCol, gCol, bCol, aCore).endVertex();
                consumer.vertex(matrix, p1x, y0, p1z).color(rCol, gCol, bCol, aCore).endVertex();
                consumer.vertex(matrix, p2x, y1, p2z).color(rCol, gCol, bCol, aCore).endVertex();
                consumer.vertex(matrix, p3x, y1, p3z).color(rCol, gCol, bCol, aCore).endVertex();
            }
        }

        // --- 3. Outer Spiraling Helical Ribbon Bands (Matching Reference Image) ---
        // 4 interwoven spiral arms wrapping around the funnel, reaching 3.0 blocks wide at top
        int numBands = 4;
        int bandSteps = 24;
        float ribbonAngularSpan = 0.42f; // Angular width of each wind ribbon

        for (int b = 0; b < numBands; b++) {
            float bandOffset = b * (float) (2.0 * Math.PI / numBands);

            for (int step = 0; step < bandSteps; step++) {
                float t0 = (float) step / bandSteps;
                float t1 = (float) (step + 1) / bandSteps;

                float y0 = cy + t0 * totalHeight;
                float y1 = cy + t1 * totalHeight;

                // Funnel radius profile: ~0.35 at base up to 1.50 at top (3 blocks wide x 3 blocks deep!)
                float r0 = 0.35f + 1.15f * (float) Math.pow(t0, 0.85);
                float r1 = 0.35f + 1.15f * (float) Math.pow(t1, 0.85);

                // Helical twist upwards
                float angle0 = baseRot + bandOffset + t0 * 2.6f;
                float angle1 = baseRot + bandOffset + t1 * 2.6f;

                // Inner and outer edges of the ribbon for 3D body
                float x0_in = cx + (float) Math.cos(angle0) * r0;
                float z0_in = cz + (float) Math.sin(angle0) * r0;
                float x0_out = cx + (float) Math.cos(angle0 + ribbonAngularSpan) * (r0 * 1.05f);
                float z0_out = cz + (float) Math.sin(angle0 + ribbonAngularSpan) * (r0 * 1.05f);

                float x1_in = cx + (float) Math.cos(angle1) * r1;
                float z1_in = cz + (float) Math.sin(angle1) * r1;
                float x1_out = cx + (float) Math.cos(angle1 + ribbonAngularSpan) * (r1 * 1.05f);
                float z1_out = cz + (float) Math.sin(angle1 + ribbonAngularSpan) * (r1 * 1.05f);

                // Electric cyan to sky blue highlights
                float bandAlpha = (0.45f + 0.25f * (1.0f - t0)) * alpha;
                float rR = 0.40f + 0.35f * (1.0f - t0);
                float gR = 0.78f + 0.20f * (1.0f - t0);
                float bR = 1.00f;

                consumer.vertex(matrix, x0_in, y0, z0_in).color(rR, gR, bR, bandAlpha).endVertex();
                consumer.vertex(matrix, x0_out, y0, z0_out).color(rR * 0.7f, gR * 0.9f, bR, bandAlpha * 0.5f).endVertex();
                consumer.vertex(matrix, x1_out, y1, z1_out).color(rR * 0.7f, gR * 0.9f, bR, bandAlpha * 0.5f).endVertex();
                consumer.vertex(matrix, x1_in, y1, z1_in).color(rR, gR, bR, bandAlpha).endVertex();
            }
        }

        // --- 4. Outer Sweeping Wisps / Trailing Air Tendrils (Like in reference) ---
        int tendrils = 3;
        for (int td = 0; td < tendrils; td++) {
            float tdOffset = td * (float) (2.0 * Math.PI / tendrils) + 0.8f;
            float tdRot = baseRot * 0.85f + tdOffset;

            int tdSteps = 16;
            for (int s = 0; s < tdSteps; s++) {
                float t0 = (float) s / tdSteps;
                float t1 = (float) (s + 1) / tdSteps;

                float y0 = cy + 0.3f + t0 * 3.7f;
                float y1 = cy + 0.3f + t1 * 3.7f;

                // Tendril flares slightly outside the main cone
                float r0 = (0.38f + 1.25f * (float) Math.pow(t0, 0.90)) * (1.08f + 0.08f * (float) Math.sin(t0 * 3.0 + exactAge * 0.2));
                float r1 = (0.38f + 1.25f * (float) Math.pow(t1, 0.90)) * (1.08f + 0.08f * (float) Math.sin(t1 * 3.0 + exactAge * 0.2));

                float a0 = tdRot + t0 * 2.2f;
                float a1 = tdRot + t1 * 2.2f;

                float tx0 = cx + (float) Math.cos(a0) * r0;
                float tz0 = cz + (float) Math.sin(a0) * r0;
                float tx1 = cx + (float) Math.cos(a1) * r1;
                float tz1 = cz + (float) Math.sin(a1) * r1;

                float w0 = 0.06f;
                float w1 = 0.06f;

                consumer.vertex(matrix, tx0 - w0, y0, tz0 - w0).color(0.55f, 0.85f, 1.0f, 0.40f * alpha).endVertex();
                consumer.vertex(matrix, tx0 + w0, y0, tz0 + w0).color(0.55f, 0.85f, 1.0f, 0.40f * alpha).endVertex();
                consumer.vertex(matrix, tx1 + w1, y1, tz1 + w1).color(0.55f, 0.85f, 1.0f, 0.40f * alpha).endVertex();
                consumer.vertex(matrix, tx1 - w1, y1, tz1 - w1).color(0.55f, 0.85f, 1.0f, 0.40f * alpha).endVertex();
            }
        }
    }
}
