package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientDenyRenderer {

    public static class ClientBarrier {
        public final int playerId;
        public final Vec3 center;
        public final Vec3 direction;
        public final int totalTicks;
        public int remainingTicks;
        public int ticksActive;
        public int reflectTicks;
        public Vec3 lastReflectPos;

        public ClientBarrier(int playerId, Vec3 center, Vec3 direction, int duration) {
            this.playerId = playerId;
            this.center = center;
            this.direction = direction;
            this.totalTicks = duration;
            this.remainingTicks = duration;
            this.ticksActive = 0;
            this.reflectTicks = 0;
            this.lastReflectPos = Vec3.ZERO;
        }
    }

    private static final List<ClientBarrier> ACTIVE_BARRIERS = new ArrayList<>();

    public static void spawnBarrier(int playerId, Vec3 center, Vec3 direction, int durationTicks) {
        synchronized (ACTIVE_BARRIERS) {
            ACTIVE_BARRIERS.removeIf(b -> b.playerId == playerId);
            ACTIVE_BARRIERS.add(new ClientBarrier(playerId, center, direction, durationTicks));
        }
    }

    public static void triggerReflect(int playerId, Vec3 hitPos) {
        synchronized (ACTIVE_BARRIERS) {
            for (ClientBarrier barrier : ACTIVE_BARRIERS) {
                if (barrier.playerId == playerId) {
                    barrier.reflectTicks = 10;
                    barrier.lastReflectPos = hitPos;
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        synchronized (ACTIVE_BARRIERS) {
            Iterator<ClientBarrier> it = ACTIVE_BARRIERS.iterator();
            while (it.hasNext()) {
                ClientBarrier barrier = it.next();
                barrier.remainingTicks--;
                barrier.ticksActive++;
                if (barrier.reflectTicks > 0) {
                    barrier.reflectTicks--;
                }

                if (barrier.remainingTicks <= 0) {
                    it.remove();
                    continue;
                }

                if (mc.level.random.nextFloat() < 0.45f) {
                    Vec3 center = barrier.center;
                    mc.level.addParticle(ParticleTypes.ENCHANT,
                            center.x + (Math.random() - 0.5) * 0.8,
                            center.y + (Math.random() - 0.5) * 0.8,
                            center.z + (Math.random() - 0.5) * 0.8,
                            0, 0.02, 0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ShaderCompatHelper.isInvalidRenderPass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean hasActive = false;
        synchronized (ACTIVE_BARRIERS) {
            hasActive = !ACTIVE_BARRIERS.isEmpty();
        }
        if (!hasActive) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lightning());

        synchronized (ACTIVE_BARRIERS) {
            for (ClientBarrier barrier : ACTIVE_BARRIERS) {
                renderBarrier(consumer, matrix, barrier, partialTick);
            }
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderBarrier(VertexConsumer consumer, Matrix4f matrix, ClientBarrier barrier, float partialTick) {
        float exactTicks = barrier.ticksActive + partialTick;

        // Smooth entrance expansion & exit fade
        float enterScale = Math.min(1.0f, exactTicks / 4.0f);
        float exitAlpha = barrier.remainingTicks < 8 ? (barrier.remainingTicks / 8.0f) : 1.0f;
        float alpha = 0.85f * exitAlpha;

        // Position: stationary in world space where the barrier was cast
        Vec3 center = barrier.center;

        Vec3 forward = barrier.direction;
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(forward).normalize();

        // Shimmering prismatic cyan/silver base color
        float pulse = 0.5f + 0.5f * (float) Math.sin(exactTicks * 0.3);
        float r = 0.65f + 0.35f * pulse;
        float g = 0.90f + 0.10f * pulse;
        float b = 1.0f;

        if (barrier.reflectTicks > 0) {
            // Flash color when reflecting: brilliant golden-white
            float flashFactor = barrier.reflectTicks / 10.0f;
            r = 1.0f;
            g = 0.85f + 0.15f * flashFactor;
            b = 0.35f + 0.65f * flashFactor;
            alpha = 1.0f;
        }

        // Hexagonal Shield Perimeter Points (u along right, v along up)
        float w = 0.72f * enterScale;
        float h = 0.88f * enterScale;

        float[] uPoints = { 0.0f,  w,       w * 0.9f, 0.0f,   -w * 0.9f, -w };
        float[] vPoints = { h,     h * 0.5f, -h * 0.5f, -h,    -h * 0.5f,  h * 0.5f };

        // 1. Outer Border
        int count = uPoints.length;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            Vec3 p1 = center.add(right.scale(uPoints[i])).add(up.scale(vPoints[i]));
            Vec3 p2 = center.add(right.scale(uPoints[next])).add(up.scale(vPoints[next]));
            draw3DQuadLine(consumer, matrix, p1, p2, forward, 0.05f, r, g, b, alpha);
        }

        // 2. Inner Concentric Shield Border
        float innerScale = 0.75f;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            Vec3 p1 = center.add(right.scale(uPoints[i] * innerScale)).add(up.scale(vPoints[i] * innerScale));
            Vec3 p2 = center.add(right.scale(uPoints[next] * innerScale)).add(up.scale(vPoints[next] * innerScale));
            draw3DQuadLine(consumer, matrix, p1, p2, forward, 0.035f, r * 0.8f, g, b, alpha * 0.75f);
        }

        // 3. Central Parry Sigil Cross
        float crossW = 0.28f * enterScale;
        float crossH = 0.36f * enterScale;
        Vec3 cLeft = center.add(right.scale(-crossW));
        Vec3 cRight = center.add(right.scale(crossW));
        Vec3 cTop = center.add(up.scale(crossH));
        Vec3 cBottom = center.add(up.scale(-crossH));
        draw3DQuadLine(consumer, matrix, cLeft, cRight, forward, 0.035f, 1.0f, 1.0f, 1.0f, alpha * 0.9f);
        draw3DQuadLine(consumer, matrix, cTop, cBottom, forward, 0.035f, 1.0f, 1.0f, 1.0f, alpha * 0.9f);

        // 4. Prismatic Mirror Membrane Fill
        float cx = (float) center.x;
        float cy = (float) center.y;
        float cz = (float) center.z;
        float membraneAlpha = (0.22f + 0.10f * pulse) * exitAlpha;

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            Vec3 p1 = center.add(right.scale(uPoints[i] * innerScale)).add(up.scale(vPoints[i] * innerScale));
            Vec3 p2 = center.add(right.scale(uPoints[next] * innerScale)).add(up.scale(vPoints[next] * innerScale));

            consumer.vertex(matrix, cx, cy, cz).color(r, g, b, membraneAlpha * 1.3f).endVertex();
            consumer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, membraneAlpha * 0.5f).endVertex();
            consumer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, membraneAlpha * 0.5f).endVertex();
            consumer.vertex(matrix, cx, cy, cz).color(r, g, b, membraneAlpha * 1.3f).endVertex();
        }

        // 5. Deflection Ripple Wave on reflect
        if (barrier.reflectTicks > 0) {
            float shockProgress = (10 - barrier.reflectTicks) / 10.0f;
            float shockRadius = (0.2f + 0.95f * shockProgress) * enterScale;
            float shockAlpha = (1.0f - shockProgress) * 0.95f;
            drawCircle(consumer, matrix, center, right, up, shockRadius, 24, exactTicks * 4.0f, 0.06f,
                    1.0f, 0.95f, 0.4f, shockAlpha);
        }
    }

    private static void drawCircle(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                                   float radius, int segments, float spinDeg, float thickness,
                                   float r, float g, float b, float a) {
        Vec3 normal = right.cross(up).normalize();
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians((i * (360.0 / segments)) + spinDeg);
            double a2 = Math.toRadians(((i + 1) * (360.0 / segments)) + spinDeg);

            float u1 = (float) Math.cos(a1) * radius;
            float v1 = (float) Math.sin(a1) * radius;
            float u2 = (float) Math.cos(a2) * radius;
            float v2 = (float) Math.sin(a2) * radius;

            Vec3 p1 = center.add(right.scale(u1)).add(up.scale(v1));
            Vec3 p2 = center.add(right.scale(u2)).add(up.scale(v2));

            draw3DQuadLine(consumer, matrix, p1, p2, normal, thickness, r, g, b, a);
        }
    }

    private static void draw3DQuadLine(VertexConsumer consumer, Matrix4f matrix, Vec3 p1, Vec3 p2, Vec3 normal,
                                       float thickness, float r, float g, float b, float a) {
        Vec3 dir = p2.subtract(p1);
        double len = dir.length();
        if (len < 0.0001) return;

        Vec3 perp = dir.cross(normal).normalize().scale(thickness * 0.5f);

        float x1a = (float) (p1.x - perp.x);
        float y1a = (float) (p1.y - perp.y);
        float z1a = (float) (p1.z - perp.z);

        float x1b = (float) (p1.x + perp.x);
        float y1b = (float) (p1.y + perp.y);
        float z1b = (float) (p1.z + perp.z);

        float x2a = (float) (p2.x - perp.x);
        float y2a = (float) (p2.y - perp.y);
        float z2a = (float) (p2.z - perp.z);

        float x2b = (float) (p2.x + perp.x);
        float y2b = (float) (p2.y + perp.y);
        float z2b = (float) (p2.z + perp.z);

        // Quad Face A
        consumer.vertex(matrix, x1a, y1a, z1a).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1b, y1b, z1b).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2b, y2b, z2b).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2a, y2a, z2a).color(r, g, b, a).endVertex();

        // Quad Face B (reverse winding for double sided visibility)
        consumer.vertex(matrix, x2a, y2a, z2a).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2b, y2b, z2b).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1b, y1b, z1b).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1a, y1a, z1a).color(r, g, b, a).endVertex();
    }
}
