package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

/**
 * World slash and thrust arc renderer for Claymore attacks.
 * Produces bold dark/steel energy trails and concentrated pierce visual flares.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientClaymoreWorldRenderer {

    public static class ActiveWorldSlash {
        public final int casterId;
        public final int stage;
        public final float yaw;
        public final float pitch;
        public final Vec3 casterPos;
        public int ticks = 0;
        public final int maxTicks;

        public ActiveWorldSlash(int casterId, int stage, float yaw, float pitch, Vec3 casterPos, int maxTicks) {
            this.casterId = casterId;
            this.stage = stage;
            this.yaw = yaw;
            this.pitch = pitch;
            this.casterPos = casterPos;
            this.maxTicks = maxTicks;
        }
    }

    private static final List<ActiveWorldSlash> ACTIVE_SLASHES = new ArrayList<>();

    public static void triggerSlash(int casterId, int stage, float yaw, float pitch, Vec3 pos, int maxTicks) {
        synchronized (ACTIVE_SLASHES) {
            ACTIVE_SLASHES.add(new ActiveWorldSlash(casterId, stage, yaw, pitch, pos, maxTicks));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            if (stage == 3) {
                // Heavy thrust impact sound & particles
                mc.level.playLocalSound(pos.x, pos.y + 1.2, pos.z,
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 0.7f, false);
                mc.level.playLocalSound(pos.x, pos.y + 1.2, pos.z,
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 1.8f, false);

                double radYaw = Math.toRadians(yaw);
                Vec3 fwd = new Vec3(-Math.sin(radYaw), 0, Math.cos(radYaw)).normalize();
                for (int i = 0; i < 15; i++) {
                    double dist = 1.0 + Math.random() * 4.0;
                    mc.level.addParticle(ParticleTypes.CRIT,
                            pos.x + fwd.x * dist + (Math.random() - 0.5) * 0.4,
                            pos.y + 1.2 + (Math.random() - 0.5) * 0.4,
                            pos.z + fwd.z * dist + (Math.random() - 0.5) * 0.4,
                            fwd.x * 0.2, 0.05, fwd.z * 0.2);
                }
            } else {
                // Heavy sweeping swoosh
                mc.level.playLocalSound(pos.x, pos.y + 1.2, pos.z,
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.2f, 0.8f, false);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ClaymoreFirstPersonRenderer.tickClient();

        synchronized (ACTIVE_SLASHES) {
            Iterator<ActiveWorldSlash> it = ACTIVE_SLASHES.iterator();
            while (it.hasNext()) {
                ActiveWorldSlash slash = it.next();
                slash.ticks++;
                if (slash.ticks >= slash.maxTicks) {
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ShaderCompatHelper.isInvalidRenderPass()) return;
        if (ACTIVE_SLASHES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        synchronized (ACTIVE_SLASHES) {
            for (ActiveWorldSlash slash : ACTIVE_SLASHES) {
                renderSlashArc(poseStack, bufferSource, camPos, slash, partialTick);
            }
        }
    }

    private static void renderSlashArc(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                       Vec3 camPos, ActiveWorldSlash slash, float partialTick) {
        float progress = Math.min(1.0f, (slash.ticks + partialTick) / (float) slash.maxTicks);
        if (progress >= 0.9f) return;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());
        float alpha = (1.0f - progress) * 0.85f;

        double yawRad = Math.toRadians(slash.yaw);
        Vec3 fwd = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();

        double arcY = slash.casterPos.y + 1.25;

        if (slash.stage == 3) {
            // Concentrated straight thrust cone
            double tipDist = 1.0 + progress * 4.2;
            double pX = slash.casterPos.x + fwd.x * tipDist;
            double pZ = slash.casterPos.z + fwd.z * tipDist;
            float rThrust = 0.55f * (1.0f - progress);

            for (int i = 0; i < 8; i++) {
                double a1 = (i / 8.0) * Math.PI * 2.0;
                double a2 = ((i + 1) / 8.0) * Math.PI * 2.0;

                double x1 = pX + side.x * Math.cos(a1) * rThrust;
                double z1 = pZ + side.z * Math.cos(a1) * rThrust;
                double y1 = arcY + Math.sin(a1) * rThrust;

                double x2 = pX + side.x * Math.cos(a2) * rThrust;
                double z2 = pZ + side.z * Math.cos(a2) * rThrust;
                double y2 = arcY + Math.sin(a2) * rThrust;

                consumer.vertex(matrix, (float) slash.casterPos.x, (float) arcY, (float) slash.casterPos.z).color(0.9f, 0.8f, 0.5f, alpha * 0.3f).endVertex();
                consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(1.0f, 0.95f, 0.8f, alpha).endVertex();
                consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(1.0f, 0.95f, 0.8f, alpha).endVertex();
                consumer.vertex(matrix, (float) slash.casterPos.x, (float) arcY, (float) slash.casterPos.z).color(0.9f, 0.8f, 0.5f, alpha * 0.3f).endVertex();
            }
        } else {
            // Sweeping fan arc: Stage 1 = Right to Left (-70 to 70), Stage 2 = Left to Right (70 to -70)
            int segments = 12;
            double arcStart = slash.stage == 1 ? Math.toRadians(-70.0) : Math.toRadians(70.0);
            double arcEnd = slash.stage == 1 ? Math.toRadians(70.0) : Math.toRadians(-70.0);
            double rInner = 1.2;
            double rOuter = 4.6;

            for (int i = 0; i < segments; i++) {
                double a1 = arcStart + (arcEnd - arcStart) * (i / (double) segments);
                double a2 = arcStart + (arcEnd - arcStart) * ((i + 1) / (double) segments);

                double x1In = slash.casterPos.x + fwd.x * (Math.cos(a1) * rInner) + side.x * (Math.sin(a1) * rInner);
                double z1In = slash.casterPos.z + fwd.z * (Math.cos(a1) * rInner) + side.z * (Math.sin(a1) * rInner);

                double x1Out = slash.casterPos.x + fwd.x * (Math.cos(a1) * rOuter) + side.x * (Math.sin(a1) * rOuter);
                double z1Out = slash.casterPos.z + fwd.z * (Math.cos(a1) * rOuter) + side.z * (Math.sin(a1) * rOuter);

                double x2In = slash.casterPos.x + fwd.x * (Math.cos(a2) * rInner) + side.x * (Math.sin(a2) * rInner);
                double z2In = slash.casterPos.z + fwd.z * (Math.cos(a2) * rInner) + side.z * (Math.sin(a2) * rInner);

                double x2Out = slash.casterPos.x + fwd.x * (Math.cos(a2) * rOuter) + side.x * (Math.sin(a2) * rOuter);
                double z2Out = slash.casterPos.z + fwd.z * (Math.cos(a2) * rOuter) + side.z * (Math.sin(a2) * rOuter);

                // Steel silver and golden glint arc
                consumer.vertex(matrix, (float) x1In, (float) arcY, (float) z1In).color(0.85f, 0.85f, 0.9f, alpha * 0.4f).endVertex();
                consumer.vertex(matrix, (float) x1Out, (float) arcY, (float) z1Out).color(1.0f, 0.95f, 0.75f, alpha).endVertex();
                consumer.vertex(matrix, (float) x2Out, (float) arcY, (float) z2Out).color(1.0f, 0.95f, 0.75f, alpha).endVertex();
                consumer.vertex(matrix, (float) x2In, (float) arcY, (float) z2In).color(0.85f, 0.85f, 0.9f, alpha * 0.4f).endVertex();
            }
        }

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }
}
