package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell;
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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientFallingSwordRenderer {

    // Cached enchanted netherite sword itemstack to display the ominous physical blade
    private static final ItemStack SWORD_STACK = new ItemStack(Items.NETHERITE_SWORD);
    static {
        SWORD_STACK.enchant(Enchantments.UNBREAKING, 1);
    }

    public static class ActiveChannel {
        public final int casterId;
        public Vec3 targetPos;
        public int ticksActive;
        /** Ticks since the caster released the spell, waiting for the server strike (-1 = still channeling). */
        public int releasedTicks = -1;
        /** Ticks since the last target update (remote casters send one every 2 ticks while channeling). */
        public int silentTicks;

        public ActiveChannel(int casterId, Vec3 targetPos) {
            this.casterId = casterId;
            this.targetPos = targetPos;
            this.ticksActive = 0;
        }
    }

    public static class ActiveStrike {
        public final int casterId;
        public final Vec3 targetPos;
        public final double obstacleY;
        public int ticks;
        public static final int MAX_TICKS = 44;

        public ActiveStrike(int casterId, Vec3 targetPos, double obstacleY) {
            this.casterId = casterId;
            this.targetPos = targetPos;
            this.obstacleY = obstacleY;
            this.ticks = 0;
        }
    }

    private static final Map<Integer, ActiveChannel> ACTIVE_CHANNELS = new ConcurrentHashMap<>();
    private static final List<ActiveStrike> ACTIVE_STRIKES = new ArrayList<>();

    public static void startChannel(int casterId, Vec3 targetPos) {
        ACTIVE_CHANNELS.put(casterId, new ActiveChannel(casterId, targetPos));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.player.getId() == casterId) {
            // Low charging rumbling sound
            mc.level.playLocalSound(targetPos.x, targetPos.y + 1.0, targetPos.z,
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f, false);
        }
    }

    public static void cancelChannel(int casterId) {
        ACTIVE_CHANNELS.remove(casterId);
    }

    public static void updateChannel(int casterId, Vec3 targetPos) {
        // Only move an existing reticle: late update packets must never resurrect a finished channel
        ActiveChannel ch = ACTIVE_CHANNELS.get(casterId);
        if (ch != null && ch.releasedTicks < 0) {
            ch.targetPos = targetPos;
            ch.silentTicks = 0;
        }
    }

    /**
     * The local caster released the spell: keep the reticle briefly until the strike arrives,
     * and drop it if the server never confirms (cooldown, mana, rejected cast).
     */
    public static void markReleased(int casterId) {
        ActiveChannel ch = ACTIVE_CHANNELS.get(casterId);
        if (ch != null) {
            ch.releasedTicks = 0;
        }
    }

    public static Vec3 getCurrentTarget(int casterId) {
        ActiveChannel ch = ACTIVE_CHANNELS.get(casterId);
        return ch != null ? ch.targetPos : null;
    }

    public static void triggerStrike(int casterId, Vec3 targetPos, double obstacleY) {
        ActiveChannel ch = ACTIVE_CHANNELS.remove(casterId);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && casterId == mc.player.getId() && ch != null && ch.targetPos != null) {
            targetPos = ch.targetPos;
            if (mc.level != null) {
                obstacleY = LaPollaCayendoSpell.findHighestObstacle(mc.level, targetPos);
            }
        }
        synchronized (ACTIVE_STRIKES) {
            ACTIVE_STRIKES.add(new ActiveStrike(casterId, targetPos, obstacleY));
        }
    }

    private static boolean isLocalChanneling() {
        com.cesar.magicandsorcery.magic.spell.Spell spell = com.cesar.magicandsorcery.client.ClientMagicData.getChannelingSpell();
        return com.cesar.magicandsorcery.client.ClientMagicData.isChanneling()
                && spell != null && spell.getId().equals(LaPollaCayendoSpell.ID);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE_CHANNELS.clear();
            synchronized (ACTIVE_STRIKES) {
                ACTIVE_STRIKES.clear();
            }
            return;
        }

        // Update active channeling targets in real time
        java.util.Iterator<ActiveChannel> channels = ACTIVE_CHANNELS.values().iterator();
        while (channels.hasNext()) {
            ActiveChannel channel = channels.next();
            channel.ticksActive++;
            boolean isLocal = mc.player != null && channel.casterId == mc.player.getId();

            if (channel.releasedTicks >= 0) {
                // Released: wait a moment for the strike, then give up
                if (++channel.releasedTicks > 30) {
                    channels.remove();
                }
                continue;
            }
            if (!isLocal && ++channel.silentTicks > 40) {
                // Remote caster stopped sending updates (cast rejected or connection hiccup)
                channels.remove();
                continue;
            }
            if (isLocal && !isLocalChanneling()) {
                // The local cast ended without a strike (cancelled or interrupted)
                channels.remove();
                continue;
            }
            if (isLocal) {
                Vec3 newTarget = LaPollaCayendoSpell.findGroundTarget(mc.level, mc.player, 40.0);
                channel.targetPos = newTarget;

                // Sync to server every 2 ticks during channeling
                if (channel.ticksActive % 2 == 0) {
                    com.cesar.magicandsorcery.network.ModNetwork.sendToServer(
                            new com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel(
                                    com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel.ACTION_UPDATE,
                                    newTarget
                            )
                    );
                }
            }

            // Spawn ground channeling embers
            if (channel.ticksActive % 3 == 0) {
                net.minecraft.util.RandomSource r = mc.level.random;
                double angle = r.nextDouble() * Math.PI * 2.0;
                double dist = r.nextDouble() * 4.2;
                double px = channel.targetPos.x + Math.cos(angle) * dist;
                double pz = channel.targetPos.z + Math.sin(angle) * dist;
                mc.level.addParticle(ParticleTypes.FLAME, px, channel.targetPos.y + 0.1, pz, 0, 0.05, 0);
            }
        }

        // Update active strikes
        synchronized (ACTIVE_STRIKES) {
            Iterator<ActiveStrike> it = ACTIVE_STRIKES.iterator();
            while (it.hasNext()) {
                ActiveStrike strike = it.next();
                strike.ticks++;

                // Sound triggers during strike sequence (whistle and whoosh removed as requested)
                if (strike.ticks == 16) {
                    // Sudden pause / warning: resonant metallic ping & tension hum
                    mc.level.playLocalSound(strike.targetPos.x, strike.obstacleY + 1.0, strike.targetPos.z,
                            SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 2.5f, 1.9f, false);
                    mc.level.playLocalSound(strike.targetPos.x, strike.obstacleY + 1.0, strike.targetPos.z,
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 2.0f, 0.5f, false);
                } else if (strike.ticks == 34) {
                    // Giant sword impact: play SwordExplosion audio
                    mc.level.playLocalSound(strike.targetPos.x, strike.obstacleY, strike.targetPos.z,
                            com.cesar.magicandsorcery.sound.ModSounds.SWORD_EXPLOSION.get(), SoundSource.PLAYERS, 4.0f, 1.0f, false);
                    mc.level.playLocalSound(strike.targetPos.x, strike.obstacleY, strike.targetPos.z,
                            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 4.0f, 0.45f, false);
                }

                // Air streak particles while falling
                if (strike.ticks < 16) {
                    double fallProg = strike.ticks / 16.0;
                    double sy = (strike.obstacleY + 36.0) - 35.0 * (fallProg * fallProg);
                    mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, strike.targetPos.x, sy, strike.targetPos.z, 0, 0, 0);
                    mc.level.addParticle(ParticleTypes.CRIT, strike.targetPos.x, sy + 0.5, strike.targetPos.z, 0, 0.2, 0);
                } else if (strike.ticks >= 16 && strike.ticks < 28) {
                    // Suspended sword warning particles
                    net.minecraft.util.RandomSource r = mc.level.random;
                    double ox = (r.nextDouble() - 0.5) * 0.4;
                    double oz = (r.nextDouble() - 0.5) * 0.4;
                    mc.level.addParticle(ParticleTypes.ENCHANTED_HIT, strike.targetPos.x + ox, strike.obstacleY + 1.0, strike.targetPos.z + oz, 0, 0.1, 0);
                }

                if (strike.ticks >= ActiveStrike.MAX_TICKS) {
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

        if (ACTIVE_CHANNELS.isEmpty() && ACTIVE_STRIKES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // 1. Render Ground Channeling Reticles
        for (ActiveChannel channel : ACTIVE_CHANNELS.values()) {
            renderChannelingReticle(poseStack, bufferSource, camPos, channel, partialTick);
        }

        // 2. Render Falling Swords
        synchronized (ACTIVE_STRIKES) {
            for (ActiveStrike strike : ACTIVE_STRIKES) {
                renderFallingSword(mc, poseStack, bufferSource, camPos, strike, partialTick);
            }
        }
    }

    private static void renderChannelingReticle(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                                Vec3 camPos, ActiveChannel channel, float partialTick) {
        Vec3 target = channel.targetPos;
        float totalTicks = channel.ticksActive + partialTick;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

        float y = (float) target.y + 0.04f;
        float r = 1.0f;
        float g = 0.2f;
        float b = 0.2f;
        float alpha = 0.85f;

        // Outer rotating ring (radius 4.5 blocks)
        int segments = 36;
        float spinOuter = totalTicks * 2.0f;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians((i * (360.0 / segments)) + spinOuter);
            double a2 = Math.toRadians(((i + 1) * (360.0 / segments)) + spinOuter);
            float x1 = (float) (target.x + Math.cos(a1) * 4.5);
            float z1 = (float) (target.z + Math.sin(a1) * 4.5);
            float x2 = (float) (target.x + Math.cos(a2) * 4.5);
            float z2 = (float) (target.z + Math.sin(a2) * 4.5);
            drawFlatQuadLine(consumer, matrix, x1, y, z1, x2, y, z2, 0.12f, r, g, b, alpha);
        }

        // Inner counter-rotating ring (radius 2.5 blocks)
        float spinInner = -totalTicks * 3.5f;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians((i * (360.0 / segments)) + spinInner);
            double a2 = Math.toRadians(((i + 1) * (360.0 / segments)) + spinInner);
            float x1 = (float) (target.x + Math.cos(a1) * 2.5);
            float z1 = (float) (target.z + Math.sin(a1) * 2.5);
            float x2 = (float) (target.x + Math.cos(a2) * 2.5);
            float z2 = (float) (target.z + Math.sin(a2) * 2.5);
            drawFlatQuadLine(consumer, matrix, x1, y, z1, x2, y, z2, 0.08f, 1.0f, 0.6f, 0.1f, alpha * 0.75f);
        }

        // Central crosshair lines
        drawFlatQuadLine(consumer, matrix, (float) target.x - 1.2f, y, (float) target.z,
                (float) target.x + 1.2f, y, (float) target.z, 0.06f, 1.0f, 0.9f, 0.2f, 0.9f);
        drawFlatQuadLine(consumer, matrix, (float) target.x, y, (float) target.z - 1.2f,
                (float) target.x, y, (float) target.z + 1.2f, 0.06f, 1.0f, 0.9f, 0.2f, 0.9f);

        poseStack.popPose();
    }

    private static void renderFallingSword(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                           Vec3 camPos, ActiveStrike strike, float partialTick) {
        float exactTicks = strike.ticks + partialTick;

        double posX = strike.targetPos.x;
        double posY;
        double posZ = strike.targetPos.z;
        float scale;
        boolean isGiant = false;

        if (exactTicks < 16.0f) {
            // === ETAPA 1: PRIMERA ESPADA CAYENDO (Normal 1.0x) ===
            float p = exactTicks / 16.0f;
            scale = 1.0f;
            // Tip drops from sky +36.0 down to obstacleY + 1.0
            double tipY = (strike.obstacleY + 36.0) - 35.0 * (p * p);
            posY = tipY + (0.5 * scale);
        } else if (exactTicks < 28.0f) {
            // === ETAPA 2: ADVERTENCIA / PAUSA (Suspendida a 1.0m del obstáculo) ===
            scale = 1.0f;
            double tipY = strike.obstacleY + 1.0;
            posY = tipY + (0.5 * scale);
            // Subtle high-speed tremor in anticipation
            float tremor = (float) Math.sin(exactTicks * 2.8) * 0.035f;
            posX += tremor;
            posZ += Math.cos(exactTicks * 2.8) * 0.035f;
        } else if (exactTicks < 34.0f) {
            // === ETAPA 3: ESPADA GIGANTE 5X SLAM ===
            scale = 5.0f;
            isGiant = true;
            float slamProg = (exactTicks - 28.0f) / 6.0f;
            // Tip drops from obstacleY + 14.0 to obstacleY
            double tipY = (strike.obstacleY + 14.0) - 14.0 * (slamProg * slamProg);
            posY = tipY + (0.5 * scale);
        } else {
            // === ETAPA 4: IMPACTO & ENTIERRO EN CRÁTER ===
            scale = 5.0f;
            isGiant = true;
            double tipY = strike.obstacleY - 1.2;
            posY = tipY + (0.5 * scale);
        }

        poseStack.pushPose();
        poseStack.translate(posX - camPos.x, posY - camPos.y, posZ - camPos.z);

        // Rotation: Tip points vertically straight DOWN into the ground at all times!
        float spin = exactTicks * (isGiant ? 4.0f : 8.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));

        // In standard item coords with FIXED display context, rotating 135 degrees on Z
        // rotates the sword so the blade tip points vertically straight DOWN (-Y) towards the ground!
        poseStack.mulPose(Axis.ZP.rotationDegrees(135.0f));

        // Scale
        poseStack.scale(scale, scale, scale);

        // Render sword model with full brightness
        int fullBright = 15728880;
        mc.getItemRenderer().renderStatic(
                SWORD_STACK,
                ItemDisplayContext.FIXED,
                fullBright,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                mc.level,
                strike.casterId
        );

        poseStack.popPose();
    }

    private static void drawFlatQuadLine(VertexConsumer consumer, Matrix4f matrix,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float thickness, float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001f) return;

        float nx = (-dz / len) * (thickness / 2.0f);
        float nz = (dx / len) * (thickness / 2.0f);

        consumer.vertex(matrix, x1 - nx, y1, z1 - nz).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1 + nx, y1, z1 + nz).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2 + nx, y2, z2 + nz).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2 - nx, y2, z2 - nz).color(r, g, b, a).endVertex();
    }
}
