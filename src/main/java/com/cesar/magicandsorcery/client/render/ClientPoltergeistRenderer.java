package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side visual renderer for Enchantment: Poltergeist.
 * - Renders ghost weapon swinging arc after a valid attack connects.
 * - Displays spectral particle effects on enchanted weapons/targets.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientPoltergeistRenderer {

    public static class ActiveStrikeVisual {
        public final int attackerId;
        public final int victimId;
        public final ItemStack weaponStack;
        public int ticks = 0;
        public final int maxTicks = 10; // ~0.5s slash animation

        // Initial snapshot positions
        public final Vec3 attackerPos;
        public final Vec3 victimPos;
        public final float attackerYaw;

        public ActiveStrikeVisual(int attackerId, int victimId, ItemStack weaponStack, Vec3 attackerPos, Vec3 victimPos, float attackerYaw) {
            this.attackerId = attackerId;
            this.victimId = victimId;
            this.weaponStack = weaponStack.copy();
            this.attackerPos = attackerPos;
            this.victimPos = victimPos;
            this.attackerYaw = attackerYaw;
        }
    }

    public static class ActiveEnchantVisual {
        public final int entityId;
        public final boolean offhand;
        public int ticks = 0;
        public final int maxTicks = 1200; // 60s

        public ActiveEnchantVisual(int entityId, boolean offhand) {
            this.entityId = entityId;
            this.offhand = offhand;
        }
    }

    private static final List<ActiveStrikeVisual> ACTIVE_STRIKES = new ArrayList<>();
    private static final Map<Integer, ActiveEnchantVisual> ACTIVE_ENCHANTS = new ConcurrentHashMap<>();

    public static void handleApplied(int entityId, boolean offhand) {
        ACTIVE_ENCHANTS.put(entityId, new ActiveEnchantVisual(entityId, offhand));

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity != null) {
                // Spectral application burst
                for (int i = 0; i < 15; i++) {
                    double ox = (Math.random() - 0.5) * 0.8;
                    double oy = Math.random() * entity.getBbHeight();
                    double oz = (Math.random() - 0.5) * 0.8;
                    mc.level.addParticle(ParticleTypes.SOUL,
                            entity.getX() + ox, entity.getY() + oy, entity.getZ() + oz,
                            0, 0.05, 0);
                }
            }
        }
    }

    public static void handleStrike(int attackerId, int victimId, ItemStack weaponStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity attacker = mc.level.getEntity(attackerId);
        Entity victim = mc.level.getEntity(victimId);

        Vec3 aPos = attacker != null ? attacker.position() : Vec3.ZERO;
        Vec3 vPos = victim != null ? victim.position() : aPos;
        float yaw = attacker != null ? attacker.getYRot() : 0.0f;

        synchronized (ACTIVE_STRIKES) {
            ACTIVE_STRIKES.add(new ActiveStrikeVisual(attackerId, victimId, weaponStack, aPos, vPos, yaw));
        }

        // Spawn ghostly slash particles between attacker and victim
        if (victim != null) {
            double vx = victim.getX();
            double vy = victim.getY() + victim.getBbHeight() * 0.5;
            double vz = victim.getZ();

            for (int i = 0; i < 8; i++) {
                mc.level.addParticle(ParticleTypes.SOUL,
                        vx + (Math.random() - 0.5) * 0.6,
                        vy + (Math.random() - 0.5) * 0.6,
                        vz + (Math.random() - 0.5) * 0.6,
                        (Math.random() - 0.5) * 0.1, 0.04, (Math.random() - 0.5) * 0.1);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            synchronized (ACTIVE_STRIKES) {
                ACTIVE_STRIKES.clear();
            }
            ACTIVE_ENCHANTS.clear();
            return;
        }

        // 1. Tick active strike visuals
        synchronized (ACTIVE_STRIKES) {
            Iterator<ActiveStrikeVisual> it = ACTIVE_STRIKES.iterator();
            while (it.hasNext()) {
                ActiveStrikeVisual strike = it.next();
                strike.ticks++;
                if (strike.ticks >= strike.maxTicks) {
                    it.remove();
                }
            }
        }

        // 2. Tick active enchantments & ambient weapon wisps
        Iterator<Map.Entry<Integer, ActiveEnchantVisual>> it = ACTIVE_ENCHANTS.entrySet().iterator();
        while (it.hasNext()) {
            ActiveEnchantVisual visual = it.next().getValue();
            visual.ticks++;

            if (visual.ticks >= visual.maxTicks) {
                it.remove();
                continue;
            }

            Entity entity = mc.level.getEntity(visual.entityId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                if (living.tickCount % 4 == 0) {
                    double ox = (Math.random() - 0.5) * 0.6;
                    double oy = 0.2 + Math.random() * (living.getBbHeight() * 0.8);
                    double oz = (Math.random() - 0.5) * 0.6;
                    mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            living.getX() + ox, living.getY() + oy, living.getZ() + oz,
                            0, 0.02, 0);
                }
            } else if (entity == null || !entity.isAlive()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ShaderCompatHelper.isInvalidRenderPass()) return;

        if (ACTIVE_STRIKES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        synchronized (ACTIVE_STRIKES) {
            for (ActiveStrikeVisual strike : ACTIVE_STRIKES) {
                renderGhostSlash(mc, poseStack, bufferSource, camPos, strike, partialTick);
            }
        }
    }

    private static void renderGhostSlash(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                        Vec3 camPos, ActiveStrikeVisual strike, float partialTick) {
        if (strike.weaponStack.isEmpty()) return;

        Entity victim = mc.level.getEntity(strike.victimId);
        Entity attacker = mc.level.getEntity(strike.attackerId);

        Vec3 targetPos = (victim != null && victim.isAlive())
                ? victim.getPosition(partialTick).add(0, victim.getBbHeight() * 0.5, 0)
                : strike.victimPos.add(0, 0.9, 0);

        float progress = Math.min(1.0f, (strike.ticks + partialTick) / (float) strike.maxTicks);

        poseStack.pushPose();

        // Position spectral weapon slightly in front of victim along attacker's line of sight
        float yaw = (attacker != null) ? attacker.getViewYRot(partialTick) : strike.attackerYaw;
        double radYaw = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radYaw), 0, Math.cos(radYaw));
        Vec3 right = new Vec3(Math.cos(radYaw), 0, Math.sin(radYaw));

        // Arc swing motion across victim
        float swingAngle = -60.0f + progress * 120.0f; // -60 to +60 degrees
        double offsetX = right.x * (Math.sin(Math.toRadians(swingAngle)) * 0.7) - forward.x * 0.3;
        double offsetY = (1.0f - progress) * 0.4 - 0.2;
        double offsetZ = right.z * (Math.sin(Math.toRadians(swingAngle)) * 0.7) - forward.z * 0.3;

        poseStack.translate(targetPos.x + offsetX - camPos.x, targetPos.y + offsetY - camPos.y, targetPos.z + offsetZ - camPos.z);

        // Orient along slash angle
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw + 90.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-swingAngle));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0f));

        float scale = 1.35f * (1.0f - progress * 0.2f);
        poseStack.scale(scale, scale, scale);

        int fullBright = 15728880;
        mc.getItemRenderer().renderStatic(
                strike.weaponStack,
                ItemDisplayContext.FIXED,
                fullBright,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                mc.level,
                strike.attackerId
        );

        poseStack.popPose();
    }
}
