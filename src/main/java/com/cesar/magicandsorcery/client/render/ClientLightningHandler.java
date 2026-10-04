package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
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
public class ClientLightningHandler {

    private static class ActiveBolt {
        final Vec3 start;
        final Vec3 end;
        final long seed;
        int age;
        final int maxAge = 3; // flash duration: 3 ticks (0.15s)

        ActiveBolt(Vec3 start, Vec3 end, long seed) {
            this.start = start;
            this.end = end;
            this.seed = seed;
            this.age = 0;
        }
    }

    private static final List<ActiveBolt> BOLTS = new ArrayList<>();

    public static void addBolt(Vec3 start, Vec3 end, long seed) {
        BOLTS.add(new ActiveBolt(start, end, seed));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (BOLTS.isEmpty()) return;

        Iterator<ActiveBolt> iterator = BOLTS.iterator();
        while (iterator.hasNext()) {
            ActiveBolt bolt = iterator.next();
            bolt.age++;
            if (bolt.age >= bolt.maxAge) {
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

        if (BOLTS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lightning());

        for (ActiveBolt bolt : BOLTS) {
            renderLightningBolt(consumer, matrix, bolt);
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderLightningBolt(VertexConsumer consumer, Matrix4f matrix, ActiveBolt bolt) {
        double dist = bolt.start.distanceTo(bolt.end);
        if (dist < 0.1) return;

        Vec3 dir = bolt.end.subtract(bolt.start).normalize();
        Vec3 up = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 norm1 = dir.cross(up).normalize();
        Vec3 norm2 = dir.cross(norm1).normalize();

        // Subdivide into segments of ~0.4 blocks
        int numSegments = Math.max(3, (int) Math.ceil(dist / 0.40));
        Vec3[] vertices = new Vec3[numSegments + 1];
        vertices[0] = bolt.start;
        vertices[numSegments] = bolt.end;

        Random rand = new Random(bolt.seed);
        float maxJitter = 0.22f; // Narrow, fine bolt

        for (int i = 1; i < numSegments; i++) {
            double t = (double) i / numSegments;
            Vec3 base = bolt.start.lerp(bolt.end, t);
            double envelope = Math.sin(t * Math.PI);
            double j1 = (rand.nextFloat() - 0.5) * 2.0 * maxJitter * envelope;
            double j2 = (rand.nextFloat() - 0.5) * 2.0 * maxJitter * envelope;
            vertices[i] = base.add(norm1.scale(j1)).add(norm2.scale(j2));
        }

        // Lightning flicker intensity
        float alpha = 1.0f - ((float) bolt.age / (float) bolt.maxAge) * 0.4f;

        // Pass 1: Outer cyan/electric aura (width: ~0.16 blocks)
        float outerWidth = 0.16f;
        float rOut = 0.45f;
        float gOut = 0.65f;
        float bOut = 1.0f;
        float aOut = 0.35f * alpha;

        // Pass 2: Inner brilliant white core (width: ~0.05 blocks)
        float innerWidth = 0.05f;
        float rIn = 1.0f;
        float gIn = 1.0f;
        float bIn = 1.0f;
        float aIn = 0.95f * alpha;

        for (int i = 0; i < numSegments; i++) {
            Vec3 p0 = vertices[i];
            Vec3 p1 = vertices[i + 1];

            // Outer layer: 2 cross quads
            drawCrossQuads(consumer, matrix, p0, p1, norm1, norm2, outerWidth, rOut, gOut, bOut, aOut);
            // Inner core layer: 2 cross quads
            drawCrossQuads(consumer, matrix, p0, p1, norm1, norm2, innerWidth, rIn, gIn, bIn, aIn);

            // Occasional secondary fork
            if (numSegments > 4 && (i == numSegments / 3 || i == (numSegments * 2) / 3)) {
                double bJ1 = (rand.nextFloat() - 0.5) * 0.4;
                double bJ2 = (rand.nextFloat() - 0.5) * 0.4;
                Vec3 forkEnd = p0.add(dir.scale(0.35)).add(norm1.scale(bJ1)).add(norm2.scale(bJ2));
                drawCrossQuads(consumer, matrix, p0, forkEnd, norm1, norm2, innerWidth * 0.8f, rIn, gIn, bIn, aIn * 0.8f);
            }
        }
    }

    private static void drawCrossQuads(VertexConsumer consumer, Matrix4f matrix,
                                       Vec3 p0, Vec3 p1, Vec3 n1, Vec3 n2,
                                       float w, float r, float g, float b, float a) {
        // Quad 1 along n1
        float n1x = (float) n1.x * w;
        float n1y = (float) n1.y * w;
        float n1z = (float) n1.z * w;

        consumer.vertex(matrix, (float) p0.x - n1x, (float) p0.y - n1y, (float) p0.z - n1z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) p0.x + n1x, (float) p0.y + n1y, (float) p0.z + n1z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) p1.x + n1x, (float) p1.y + n1y, (float) p1.z + n1z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) p1.x - n1x, (float) p1.y - n1y, (float) p1.z - n1z).color(r, g, b, a).endVertex();

        // Quad 2 along n2
        float n2x = (float) n2.x * w;
        float n2y = (float) n2.y * w;
        float n2z = (float) n2.z * w;

        consumer.vertex(matrix, (float) p0.x - n2x, (float) p0.y - n2y, (float) p0.z - n2z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) p0.x + n2x, (float) p0.y + n2y, (float) p0.z + n2z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) p1.x + n2x, (float) p1.y + n2y, (float) p1.z + n2z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) p1.x - n2x, (float) p1.y - n2y, (float) p1.z - n2z).color(r, g, b, a).endVertex();
    }
}
