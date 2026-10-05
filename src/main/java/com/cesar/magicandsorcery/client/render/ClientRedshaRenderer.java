package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientRedshaRenderer {

    public static class ClientPortal {
        public final int id;
        public final Vec3 center;
        public final Vec3 forward;
        public final Vec3 right;
        public final Vec3 up;
        public final int totalTicks;
        public int remainingTicks;
        public int ticksActive;
        public int flashTicks;
        public Vec3 lastFlashPos;

        public ClientPortal(int id, Vec3 center, Vec3 forward, Vec3 right, Vec3 up, int duration) {
            this.id = id;
            this.center = center;
            this.forward = forward;
            this.right = right;
            this.up = up;
            this.totalTicks = duration;
            this.remainingTicks = duration;
            this.ticksActive = 0;
            this.flashTicks = 0;
            this.lastFlashPos = center;
        }
    }

    private static final List<ClientPortal> ACTIVE_PORTALS = new ArrayList<>();
    private static final Map<Integer, Integer> REMOTE_CHANNELS = new ConcurrentHashMap<>();

    // --- NETWORK HOOKS ---

    public static void spawnPortal(int id, Vec3 center, Vec3 forward, Vec3 right, Vec3 up, int durationTicks) {
        synchronized (ACTIVE_PORTALS) {
            ACTIVE_PORTALS.removeIf(p -> p.id == id);
            ACTIVE_PORTALS.add(new ClientPortal(id, center, forward, right, up, durationTicks));
        }
    }

    public static void triggerPortalFlash(int id, Vec3 hitPos) {
        synchronized (ACTIVE_PORTALS) {
            for (ClientPortal portal : ACTIVE_PORTALS) {
                if (portal.id == id) {
                    portal.flashTicks = 12; // 0.6s flash
                    portal.lastFlashPos = hitPos;
                    break;
                }
            }
        }
    }

    public static void removePortal(int id) {
        synchronized (ACTIVE_PORTALS) {
            ACTIVE_PORTALS.removeIf(p -> p.id == id);
        }
    }

    public static void onChannelUpdate(int casterId, byte action) {
        if (action == 0) { // START
            REMOTE_CHANNELS.put(casterId, 0);
        } else { // CANCEL / STOP
            REMOTE_CHANNELS.remove(casterId);
        }
    }

    // --- CLIENT TICKS ---

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Tick active portals
        synchronized (ACTIVE_PORTALS) {
            Iterator<ClientPortal> it = ACTIVE_PORTALS.iterator();
            while (it.hasNext()) {
                ClientPortal portal = it.next();
                portal.remainingTicks--;
                portal.ticksActive++;
                if (portal.flashTicks > 0) {
                    portal.flashTicks--;
                }

                if (portal.remainingTicks <= 0) {
                    it.remove();
                    continue;
                }

                // Ambient particles along the 2x2 circle boundary
                if (mc.level.random.nextFloat() < 0.65f) {
                    double angle = mc.level.random.nextDouble() * Math.PI * 2.0;
                    double u = Math.cos(angle) * RedshaSpell.PORTAL_RADIUS;
                    double v = Math.sin(angle) * RedshaSpell.PORTAL_RADIUS;
                    Vec3 pos = portal.center.add(portal.right.scale(u)).add(portal.up.scale(v));
                    mc.level.addParticle(ParticleTypes.CRIMSON_SPORE,
                            pos.x, pos.y, pos.z,
                            (Math.random() - 0.5) * 0.02, (Math.random() - 0.5) * 0.02, (Math.random() - 0.5) * 0.02);
                }
            }
        }

        // Tick remote channels
        REMOTE_CHANNELS.replaceAll((id, ticks) -> ticks + 1);
        REMOTE_CHANNELS.entrySet().removeIf(entry -> {
            net.minecraft.world.entity.Entity e = mc.level.getEntity(entry.getKey());
            return e == null || entry.getValue() > 80;
        });
    }

    // --- RENDERING ---

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ShaderCompatHelper.isInvalidRenderPass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean hasActive = false;
        synchronized (ACTIVE_PORTALS) {
            hasActive = !ACTIVE_PORTALS.isEmpty();
        }

        boolean isLocalChanneling = ClientMagicData.isChanneling() &&
                ClientMagicData.getSelectedSpell() instanceof RedshaSpell;

        if (!hasActive && !isLocalChanneling && REMOTE_CHANNELS.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lightning());

        // 1. Render Active Redsha Portals in the world
        synchronized (ACTIVE_PORTALS) {
            for (ClientPortal portal : ACTIVE_PORTALS) {
                renderPortal(consumer, matrix, portal, partialTick);
            }
        }

        // 2. Render Local Channel Preview
        if (isLocalChanneling && mc.player != null) {
            renderChannelPreview(consumer, matrix, mc.player, ClientMagicData.getChannelProgress());
        }

        // 3. Render Remote Channel Previews
        for (Map.Entry<Integer, Integer> entry : REMOTE_CHANNELS.entrySet()) {
            net.minecraft.world.entity.Entity caster = mc.level.getEntity(entry.getKey());
            if (caster instanceof net.minecraft.world.entity.player.Player player && player != mc.player) {
                float prog = Math.min(1.0f, (entry.getValue() + partialTick) / 40.0f);
                renderChannelPreview(consumer, matrix, player, prog);
            }
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderPortal(VertexConsumer consumer, Matrix4f matrix, ClientPortal portal, float partialTick) {
        float exactTicks = portal.ticksActive + partialTick;
        float pulse = 0.5f + 0.5f * (float) Math.sin(exactTicks * 0.15);

        // Crimson base with golden pulses
        float r = 1.0f;
        float g = 0.12f + 0.18f * pulse;
        float b = 0.22f + 0.08f * pulse;
        float alpha = 0.85f;

        if (portal.flashTicks > 0) {
            // Flash on amplification: bright golden-white energy
            float flashFactor = portal.flashTicks / 12.0f;
            r = 1.0f;
            g = 0.6f + 0.4f * flashFactor;
            b = 0.4f + 0.6f * flashFactor;
            alpha = 1.0f;
        }

        // 1. Outer Ring (Radius 1.0 = 2.0x2.0 blocks)
        float spinOuter = exactTicks * 1.2f;
        drawCircle(consumer, matrix, portal.center, portal.right, portal.up, RedshaSpell.PORTAL_RADIUS,
                36, spinOuter, 0.055f, r, g, b, alpha);

        // 2. Inner Counter-Rotating Ring (Radius 0.65)
        float spinInner = -exactTicks * 1.8f;
        drawCircle(consumer, matrix, portal.center, portal.right, portal.up, 0.65f,
                36, spinInner, 0.04f, r, Math.min(1.0f, g + 0.25f), b, alpha * 0.9f);

        // 3. Core Ring (Radius 0.28)
        float spinCore = exactTicks * 2.5f;
        drawCircle(consumer, matrix, portal.center, portal.right, portal.up, 0.28f,
                24, spinCore, 0.035f, 1.0f, 0.85f, 0.35f, alpha);

        // 4. 8 Arcane Spokes connecting Inner to Outer Ring
        int spokes = 8;
        for (int i = 0; i < spokes; i++) {
            double angleDeg = (i * (360.0 / spokes)) + spinOuter;
            double rad = Math.toRadians(angleDeg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            Vec3 pInner = portal.center.add(portal.right.scale(cos * 0.65)).add(portal.up.scale(sin * 0.65));
            Vec3 pOuter = portal.center.add(portal.right.scale(cos * RedshaSpell.PORTAL_RADIUS)).add(portal.up.scale(sin * RedshaSpell.PORTAL_RADIUS));

            draw3DQuadLine(consumer, matrix, pInner, pOuter, portal.forward, 0.035f, r, g, b, alpha * 0.75f);
        }

        // 5. Central Glyph Cross inside Core Ring
        Vec3 crossR1 = portal.center.add(portal.right.scale(-0.24));
        Vec3 crossR2 = portal.center.add(portal.right.scale(0.24));
        Vec3 crossU1 = portal.center.add(portal.up.scale(-0.24));
        Vec3 crossU2 = portal.center.add(portal.up.scale(0.24));

        draw3DQuadLine(consumer, matrix, crossR1, crossR2, portal.forward, 0.03f, 1.0f, 0.85f, 0.4f, alpha * 0.85f);
        draw3DQuadLine(consumer, matrix, crossU1, crossU2, portal.forward, 0.03f, 1.0f, 0.85f, 0.4f, alpha * 0.85f);

        // 6. Translucent Energy Membrane in the core
        drawEnergyMembrane(consumer, matrix, portal.center, portal.right, portal.up, 0.65f, 16,
                r, g, b, 0.16f + 0.08f * pulse);

        // 7. Ripple Ring on flash
        if (portal.flashTicks > 0) {
            float shockProgress = (12 - portal.flashTicks) / 12.0f;
            float shockRadius = 0.2f + 0.9f * shockProgress;
            float shockAlpha = (1.0f - shockProgress) * 0.95f;
            drawCircle(consumer, matrix, portal.center, portal.right, portal.up, shockRadius,
                    28, 0, 0.06f, 1.0f, 0.95f, 0.6f, shockAlpha);
        }
    }

    private static void renderChannelPreview(VertexConsumer consumer, Matrix4f matrix, net.minecraft.world.entity.player.Player player, float progress) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 center = eyePos.add(lookVec.scale(2.0));

        Vec3 forward = lookVec.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        float scale = 0.25f + 0.75f * progress;
        float radius = RedshaSpell.PORTAL_RADIUS * scale;
        float alpha = 0.35f + 0.55f * progress;
        float spin = player.tickCount * (3.0f + 6.0f * progress);

        // Charging circle
        drawCircle(consumer, matrix, center, right, up, radius, 32, spin, 0.045f,
                1.0f, 0.2f + 0.5f * progress, 0.25f, alpha);
        drawCircle(consumer, matrix, center, right, up, radius * 0.65f, 24, -spin * 1.5f, 0.035f,
                1.0f, 0.6f * progress, 0.2f, alpha * 0.8f);

        // Radial charging spokes
        int spokes = 6;
        for (int i = 0; i < spokes; i++) {
            double angleDeg = (i * (360.0 / spokes)) + spin;
            double rad = Math.toRadians(angleDeg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            Vec3 pInner = center.add(right.scale(cos * radius * 0.4f)).add(up.scale(sin * radius * 0.4f));
            Vec3 pOuter = center.add(right.scale(cos * radius)).add(up.scale(sin * radius));
            draw3DQuadLine(consumer, matrix, pInner, pOuter, forward, 0.03f, 1.0f, 0.3f, 0.2f, alpha * 0.65f);
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

        // Vector perpendicular to dir in the portal plane
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

    private static void drawEnergyMembrane(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                                           float radius, int segments, float r, float g, float b, float a) {
        float cx = (float) center.x;
        float cy = (float) center.y;
        float cz = (float) center.z;

        for (int i = 0; i < segments; i++) {
            double a1 = (i * 2.0 * Math.PI) / segments;
            double a2 = ((i + 1) * 2.0 * Math.PI) / segments;

            Vec3 p1 = center.add(right.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            Vec3 p2 = center.add(right.scale(Math.cos(a2) * radius)).add(up.scale(Math.sin(a2) * radius));

            consumer.vertex(matrix, cx, cy, cz).color(r, g, b, a * 1.2f).endVertex();
            consumer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, a * 0.3f).endVertex();
            consumer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, a * 0.3f).endVertex();
            consumer.vertex(matrix, cx, cy, cz).color(r, g, b, a * 1.2f).endVertex();
        }
    }
}
