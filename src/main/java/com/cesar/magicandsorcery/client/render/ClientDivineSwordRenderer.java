package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientMagicData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientDivineSwordRenderer {

    // Cached enchanted wooden sword itemstack to display the iconic visual model
    private static final ItemStack SWORD_STACK = new ItemStack(Items.WOODEN_SWORD);
    static {
        SWORD_STACK.enchant(Enchantments.UNBREAKING, 1);
    }

    public static class ActiveChannel {
        public final int casterId;
        public int ticksActive;
        public final int maxTicks = 20; // 1.0s channel

        public ActiveChannel(int casterId) {
            this.casterId = casterId;
            this.ticksActive = 0;
        }
    }

    public static class ActiveSweep {
        public final int casterId;
        public final float yaw;
        public final float pitch;
        public final Vec3 casterPos;
        public int ticks;
        public final int maxTicks = 7; // Fast 7-tick sweep (~0.35s)

        public ActiveSweep(int casterId, float yaw, float pitch, Vec3 casterPos) {
            this.casterId = casterId;
            this.yaw = yaw;
            this.pitch = pitch;
            this.casterPos = casterPos;
            this.ticks = 0;
        }
    }

    private static final Map<Integer, ActiveChannel> ACTIVE_CHANNELS = new ConcurrentHashMap<>();
    private static final List<ActiveSweep> ACTIVE_SWEEPS = new ArrayList<>();

    public static void startChannel(int casterId) {
        ACTIVE_CHANNELS.put(casterId, new ActiveChannel(casterId));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.player.getId() == casterId) {
            // Soft holy charging resonance sound
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1.5, mc.player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.2f, 1.3f, false);
        }
    }

    public static void cancelChannel(int casterId) {
        ACTIVE_CHANNELS.remove(casterId);
    }

    public static void onChannelUpdate(int casterId, byte action) {
        if (action == 0) {
            startChannel(casterId);
        } else {
            cancelChannel(casterId);
        }
    }

    public static void triggerSweep(int casterId, float yaw, float pitch, Vec3 pos) {
        com.cesar.magicandsorcery.client.fx.GroundMarks.holySlash(pos, yaw);
        // Remove channel state upon sweep
        ACTIVE_CHANNELS.remove(casterId);

        synchronized (ACTIVE_SWEEPS) {
            ACTIVE_SWEEPS.add(new ActiveSweep(casterId, yaw, pitch, pos));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // Strong swoosh/sweep slash sound
            mc.level.playLocalSound(pos.x, pos.y + 1.2, pos.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.4f, 0.85f, false);
            mc.level.playLocalSound(pos.x, pos.y + 1.2, pos.z,
                    SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.8f, 1.4f, false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE_CHANNELS.clear();
            synchronized (ACTIVE_SWEEPS) {
                ACTIVE_SWEEPS.clear();
            }
            return;
        }

        // 1. Tick Active Channels
        for (ActiveChannel channel : ACTIVE_CHANNELS.values()) {
            boolean isLocal = (mc.player != null && channel.casterId == mc.player.getId());
            if (isLocal && !ClientMagicData.isChanneling()) {
                ACTIVE_CHANNELS.remove(channel.casterId);
                continue;
            }

            Entity caster = mc.level.getEntity(channel.casterId);
            if (caster == null && !isLocal) {
                ACTIVE_CHANNELS.remove(channel.casterId);
                continue;
            }

            channel.ticksActive++;

            // Chime sound when full 1.0s charge is reached
            if (channel.ticksActive == channel.maxTicks && isLocal) {
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1.5, mc.player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.5f, 1.8f, false);
            }

            // Hovering aura particles above player head
            Vec3 pos = (isLocal && mc.player != null) ? mc.player.position() : caster.position();
            double swordY = pos.y + ((isLocal && mc.player != null) ? mc.player.getBbHeight() : caster.getBbHeight()) + 0.85;

            double spread = 0.45;
            mc.level.addParticle(ParticleTypes.ENCHANTED_HIT,
                    pos.x + (Math.random() - 0.5) * spread,
                    swordY + (Math.random() - 0.5) * spread,
                    pos.z + (Math.random() - 0.5) * spread,
                    (Math.random() - 0.5) * 0.1, 0.05, (Math.random() - 0.5) * 0.1);

            if (channel.ticksActive > 12 && Math.random() < 0.4) {
                mc.level.addParticle(ParticleTypes.END_ROD,
                        pos.x + (Math.random() - 0.5) * 0.3,
                        swordY + (Math.random() - 0.5) * 0.3,
                        pos.z + (Math.random() - 0.5) * 0.3,
                        0, 0.02, 0);
            }
        }

        // 2. Tick Active Sweeps
        synchronized (ACTIVE_SWEEPS) {
            Iterator<ActiveSweep> it = ACTIVE_SWEEPS.iterator();
            while (it.hasNext()) {
                ActiveSweep sweep = it.next();
                sweep.ticks++;

                // Spawn fan of sweep attack particles along horizontal forward arc
                if (sweep.ticks <= 4) {
                    double yawRad = Math.toRadians(sweep.yaw);
                    Vec3 fwd = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
                    Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();

                    float progress = (float) sweep.ticks / 4.0f;
                    double arcAngle = (progress - 0.5) * 1.8; // -0.9 to +0.9 radians
                    double dist = 2.8;

                    double px = sweep.casterPos.x + fwd.x * (Math.cos(arcAngle) * dist) + side.x * (Math.sin(arcAngle) * dist);
                    double py = sweep.casterPos.y + 1.2;
                    double pz = sweep.casterPos.z + fwd.z * (Math.cos(arcAngle) * dist) + side.z * (Math.sin(arcAngle) * dist);

                    mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, px, py, pz, 0, 0, 0);
                    mc.level.addParticle(ParticleTypes.CRIT, px, py, pz,
                            side.x * 0.15, 0.02, side.z * 0.15);
                    mc.level.addParticle(ParticleTypes.ENCHANTED_HIT, px, py, pz,
                            0, 0.05, 0);
                }

                if (sweep.ticks >= sweep.maxTicks) {
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        // Strict Oculus / Iris shaderpass check: never render in hand or shadow pass!
        if (ShaderCompatHelper.isInvalidRenderPass()) return;

        if (ACTIVE_CHANNELS.isEmpty() && ACTIVE_SWEEPS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // 1. Render Hovering Giant Swords during Channeling
        for (ActiveChannel channel : ACTIVE_CHANNELS.values()) {
            boolean isLocal = (mc.player != null && channel.casterId == mc.player.getId());
            Entity caster = isLocal ? mc.player : mc.level.getEntity(channel.casterId);
            if (caster == null) continue;

            renderHoveringSword(mc, poseStack, bufferSource, camPos, caster, channel, partialTick);
        }

        // 2. Render Sweeping Giant Swords during Attack
        synchronized (ACTIVE_SWEEPS) {
            for (ActiveSweep sweep : ACTIVE_SWEEPS) {
                renderSweepingSword(mc, poseStack, bufferSource, camPos, sweep, partialTick);
            }
        }
    }

    private static void renderHoveringSword(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            Vec3 camPos, Entity caster, ActiveChannel channel, float partialTick) {
        poseStack.pushPose();

        // Calculate world position above caster's head
        double posX = caster.xo + (caster.getX() - caster.xo) * partialTick;
        double posY = caster.yo + (caster.getY() - caster.yo) * partialTick + caster.getBbHeight() + 0.85;
        double posZ = caster.zo + (caster.getZ() - caster.zo) * partialTick;

        // Gentle hover bobbing
        float exactTicks = channel.ticksActive + partialTick;
        float bob = (float) Math.sin(exactTicks * 0.28f) * 0.08f;
        posY += bob;

        // High-frequency tremor as it reaches ready state (ticks > 12)
        float progress = Math.min(1.0f, exactTicks / 20.0f);
        if (progress > 0.6f) {
            float tremor = (progress - 0.6f) * 0.035f;
            posX += (Math.sin(exactTicks * 2.8) * tremor);
            posZ += (Math.cos(exactTicks * 2.8) * tremor);
        }

        poseStack.translate(posX - camPos.x, posY - camPos.y, posZ - camPos.z);

        // Rotation: face along caster's yaw
        float yaw = caster.getViewYRot(partialTick);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        // Tilt forward slightly downwards (poised to strike)
        poseStack.mulPose(Axis.XP.rotationDegrees(65.0f));
        // Rotate 45 degrees so standard 2D/3D sword item model aligns straight forward
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f));

        // Scale to GIANT supernatural proportions (3.8x scale)
        float scale = 3.8f;
        poseStack.scale(scale, scale, scale);

        // Render the enchanted wooden sword with full brightness glow
        int fullBright = 15728880;
        mc.getItemRenderer().renderStatic(
                SWORD_STACK,
                ItemDisplayContext.FIXED,
                fullBright,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                mc.level,
                channel.casterId
        );

        poseStack.popPose();
    }

    private static void renderSweepingSword(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            Vec3 camPos, ActiveSweep sweep, float partialTick) {
        poseStack.pushPose();

        float exactTicks = sweep.ticks + partialTick;
        float progress = Math.min(1.0f, exactTicks / (float) sweep.maxTicks);

        // Fast ease-out curve for rapid explosive slash
        float ease = 1.0f - (float) Math.pow(1.0f - progress, 3.0);

        // Sweep angles: horizontal slash from -75° (right) to +75° (left)
        float startAngle = -75.0f;
        float endAngle = 75.0f;
        float currentAngle = startAngle + (endAngle - startAngle) * ease;

        double yawRad = Math.toRadians(sweep.yaw);
        Vec3 fwd = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();

        // Sword position travels in an arc 2.4 blocks in front of the caster at chest height
        double arcRad = Math.toRadians(currentAngle);
        double dist = 2.4;
        double swordX = sweep.casterPos.x + fwd.x * (Math.cos(arcRad) * dist) + side.x * (Math.sin(arcRad) * dist);
        double swordY = sweep.casterPos.y + 1.25 - (ease * 0.35); // slight downward slice
        double swordZ = sweep.casterPos.z + fwd.z * (Math.cos(arcRad) * dist) + side.z * (Math.sin(arcRad) * dist);

        poseStack.translate(swordX - camPos.x, swordY - camPos.y, swordZ - camPos.z);

        // Orient along sweep arc
        poseStack.mulPose(Axis.YP.rotationDegrees(-sweep.yaw - currentAngle));
        // Flat horizontal slash plane with slight aggressive tilt
        poseStack.mulPose(Axis.XP.rotationDegrees(95.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f));

        float scale = 4.2f * (1.0f - progress * 0.25f);
        poseStack.scale(scale, scale, scale);

        int fullBright = 15728880;
        mc.getItemRenderer().renderStatic(
                SWORD_STACK,
                ItemDisplayContext.FIXED,
                fullBright,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                mc.level,
                sweep.casterId
        );

        poseStack.popPose();

        // 3. Render Radiant Energy Arc Fan
        renderSweepSlashArc(poseStack, bufferSource, camPos, sweep, progress, fwd, side);
    }

    private static void renderSweepSlashArc(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            Vec3 camPos, ActiveSweep sweep, float progress,
                                            Vec3 fwd, Vec3 side) {
        if (progress >= 0.85f) return;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

        float alpha = (1.0f - progress) * 0.85f;
        int segments = 12;
        double arcStart = Math.toRadians(-75.0);
        double arcEnd = Math.toRadians(75.0);
        double rInner = 1.8;
        double rOuter = 4.8;
        double arcY = sweep.casterPos.y + 1.15;

        for (int i = 0; i < segments; i++) {
            double a1 = arcStart + (arcEnd - arcStart) * (i / (double) segments);
            double a2 = arcStart + (arcEnd - arcStart) * ((i + 1) / (double) segments);

            double x1In = sweep.casterPos.x + fwd.x * (Math.cos(a1) * rInner) + side.x * (Math.sin(a1) * rInner);
            double z1In = sweep.casterPos.z + fwd.z * (Math.cos(a1) * rInner) + side.z * (Math.sin(a1) * rInner);

            double x1Out = sweep.casterPos.x + fwd.x * (Math.cos(a1) * rOuter) + side.x * (Math.sin(a1) * rOuter);
            double z1Out = sweep.casterPos.z + fwd.z * (Math.cos(a1) * rOuter) + side.z * (Math.sin(a1) * rOuter);

            double x2In = sweep.casterPos.x + fwd.x * (Math.cos(a2) * rInner) + side.x * (Math.sin(a2) * rInner);
            double z2In = sweep.casterPos.z + fwd.z * (Math.cos(a2) * rInner) + side.z * (Math.sin(a2) * rInner);

            double x2Out = sweep.casterPos.x + fwd.x * (Math.cos(a2) * rOuter) + side.x * (Math.sin(a2) * rOuter);
            double z2Out = sweep.casterPos.z + fwd.z * (Math.cos(a2) * rOuter) + side.z * (Math.sin(a2) * rOuter);

            // Golden-yellow celestial slash arc
            consumer.vertex(matrix, (float) x1In, (float) arcY, (float) z1In).color(1.0f, 0.9f, 0.4f, alpha * 0.4f).endVertex();
            consumer.vertex(matrix, (float) x1Out, (float) arcY, (float) z1Out).color(1.0f, 1.0f, 0.8f, alpha).endVertex();
            consumer.vertex(matrix, (float) x2Out, (float) arcY, (float) z2Out).color(1.0f, 1.0f, 0.8f, alpha).endVertex();
            consumer.vertex(matrix, (float) x2In, (float) arcY, (float) z2In).color(1.0f, 0.9f, 0.4f, alpha * 0.4f).endVertex();
        }

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }
}
