package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.client.fx.FxDraw;
import com.cesar.magicandsorcery.client.fx.FxParticles;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redsha visuals: a crimson and gold arcane seal that unfolds, spins in layers (membrane, dashed rings, rune band,
 * octagram, vortex arms, golden core, orbiting motes), ripples when something crosses it, and gives the temporary
 * echo copies a crimson trail that dissolves into sparks.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientRedshaRenderer {

    private static final float[] CRIMSON = {1.0f, 0.12f, 0.22f};
    private static final float[] GOLD = {1.0f, 0.78f, 0.32f};
    private static final int SPARK_CRIMSON = 0xFF3348;
    private static final int SPARK_GOLD = 0xFFD27A;
    private static final int OPEN_ANIM = 12;
    private static final int CLOSE_ANIM = 16;

    public static class ClientPortal {
        public final int id;
        public final Vec3 center;
        public final Vec3 forward;
        public final Vec3 right;
        public final Vec3 up;
        public final int totalTicks;
        public int remainingTicks;
        public int ticksActive;
        final List<Ripple> ripples = new ArrayList<>();
        boolean closing;
        int closeTicks;

        public ClientPortal(int id, Vec3 center, Vec3 forward, Vec3 right, Vec3 up, int duration) {
            this.id = id;
            this.center = center;
            this.forward = forward;
            this.right = right;
            this.up = up;
            this.totalTicks = duration;
            this.remainingTicks = duration;
        }
    }

    private record Ripple(Vec3 at, int born) {
    }

    /** Trail of a temporary echo projectile. */
    private static final class EchoTrail {
        final int entityId;
        final Deque<Vec3> points = new ArrayDeque<>();
        boolean seen;
        int waited;
        int fading = -1;

        EchoTrail(int entityId) {
            this.entityId = entityId;
        }
    }

    private static final List<ClientPortal> ACTIVE_PORTALS = new ArrayList<>();
    private static final List<EchoTrail> TRAILS = new ArrayList<>();
    private static final Map<Integer, Integer> REMOTE_CHANNELS = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // Network hooks
    // ------------------------------------------------------------------

    public static void spawnPortal(int id, Vec3 center, Vec3 forward, Vec3 right, Vec3 up, int durationTicks) {
        synchronized (ACTIVE_PORTALS) {
            ACTIVE_PORTALS.removeIf(p -> p.id == id);
            ACTIVE_PORTALS.add(new ClientPortal(id, center, forward, right, up, durationTicks));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            ringBurst(center, right, up, RedshaSpell.PORTAL_RADIUS, 22, mc.level.random);
        }
    }

    public static void triggerPortalFlash(int id, Vec3 hitPos) {
        triggerPortalFlash(id, hitPos, new int[0]);
    }

    public static void triggerPortalFlash(int id, Vec3 hitPos, int[] echoIds) {
        Minecraft mc = Minecraft.getInstance();
        synchronized (ACTIVE_PORTALS) {
            for (ClientPortal portal : ACTIVE_PORTALS) {
                if (portal.id == id) {
                    portal.ripples.add(new Ripple(hitPos, portal.ticksActive));
                    if (mc.level != null) {
                        ringBurst(portal.center, portal.right, portal.up, RedshaSpell.PORTAL_RADIUS, 14, mc.level.random);
                    }
                    break;
                }
            }
        }
        if (mc.level != null) {
            FxParticles.sparkBurst(hitPos.x, hitPos.y, hitPos.z, 16, 0.3, SPARK_GOLD, mc.level.random);
            FxParticles.sparkBurst(hitPos.x, hitPos.y, hitPos.z, 10, 0.25, SPARK_CRIMSON, mc.level.random);
        }
        for (int echoId : echoIds) {
            TRAILS.add(new EchoTrail(echoId));
        }
    }

    public static void removePortal(int id) {
        synchronized (ACTIVE_PORTALS) {
            for (ClientPortal portal : ACTIVE_PORTALS) {
                if (portal.id == id && !portal.closing) {
                    portal.closing = true;
                    portal.closeTicks = 0;
                }
            }
        }
    }

    public static void onChannelUpdate(int casterId, byte action) {
        if (action == 0) { // START
            REMOTE_CHANNELS.put(casterId, 0);
        } else { // CANCEL / STOP
            REMOTE_CHANNELS.remove(casterId);
        }
    }

    private static void ringBurst(Vec3 center, Vec3 right, Vec3 up, double radius, int count, RandomSource random) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            Vec3 dir = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 p = center.add(dir.scale(radius));
            Vec3 tangent = right.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
            Vec3 v = dir.scale(0.06 + random.nextDouble() * 0.05).add(tangent.scale(0.08));
            boolean gold = random.nextFloat() < 0.4f;
            FxParticles.spawn(FxParticles.Kind.GLOW).at(p.x, p.y, p.z).vel(v.x, v.y, v.z)
                    .color(gold ? GOLD[0] : CRIMSON[0], gold ? GOLD[1] : CRIMSON[1], gold ? GOLD[2] : CRIMSON[2])
                    .size(0.09f + random.nextFloat() * 0.06f).life(12 + random.nextInt(10)).physics(0, 0.9, 0).noCollide();
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            synchronized (ACTIVE_PORTALS) {
                ACTIVE_PORTALS.clear();
            }
            TRAILS.clear();
            REMOTE_CHANNELS.clear();
            return;
        }
        if (mc.isPaused()) return;

        synchronized (ACTIVE_PORTALS) {
            Iterator<ClientPortal> it = ACTIVE_PORTALS.iterator();
            while (it.hasNext()) {
                ClientPortal portal = it.next();
                portal.ticksActive++;
                portal.remainingTicks--;
                if (portal.remainingTicks <= 0 && !portal.closing) {
                    portal.closing = true;
                }
                if (portal.closing && ++portal.closeTicks > CLOSE_ANIM) {
                    ringBurst(portal.center, portal.right, portal.up, 0.3, 16, mc.level.random);
                    it.remove();
                    continue;
                }
                portal.ripples.removeIf(r -> portal.ticksActive - r.born() > 14);

                // Embers drifting off the rim
                if (mc.level.random.nextFloat() < 0.5f) {
                    double a = mc.level.random.nextDouble() * Math.PI * 2.0;
                    Vec3 p = portal.center.add(portal.right.scale(Math.cos(a) * RedshaSpell.PORTAL_RADIUS))
                            .add(portal.up.scale(Math.sin(a) * RedshaSpell.PORTAL_RADIUS));
                    FxParticles.spawn(FxParticles.Kind.GLOW).at(p.x, p.y, p.z)
                            .vel((mc.level.random.nextDouble() - 0.5) * 0.02, 0.015, (mc.level.random.nextDouble() - 0.5) * 0.02)
                            .color(CRIMSON[0], CRIMSON[1] + 0.1f, CRIMSON[2]).size(0.07f).life(18).physics(0, 0.96, 0).noCollide();
                }
            }
        }

        // Echo trails follow their projectile; when it vanishes they dissolve
        Iterator<EchoTrail> trails = TRAILS.iterator();
        while (trails.hasNext()) {
            EchoTrail trail = trails.next();
            if (trail.fading >= 0) {
                if (!trail.points.isEmpty()) trail.points.removeFirst();
                if (++trail.fading > 8 || trail.points.isEmpty()) trails.remove();
                continue;
            }
            Entity e = mc.level.getEntity(trail.entityId);
            if (e == null || e.isRemoved()) {
                if (trail.seen || ++trail.waited > 20) {
                    Vec3 last = trail.points.peekLast();
                    if (last != null) {
                        FxParticles.sparkBurst(last.x, last.y, last.z, 12, 0.2, SPARK_CRIMSON, mc.level.random);
                    }
                    trail.fading = 0;
                }
                continue;
            }
            trail.seen = true;
            trail.points.addLast(e.position().add(0, e.getBbHeight() * 0.5, 0));
            while (trail.points.size() > 10) trail.points.removeFirst();
        }

        REMOTE_CHANNELS.replaceAll((id, ticks) -> ticks + 1);
        REMOTE_CHANNELS.entrySet().removeIf(entry -> mc.level.getEntity(entry.getKey()) == null || entry.getValue() > 80);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Spell channeling = ClientMagicData.getChannelingSpell();
        boolean localChanneling = ClientMagicData.isChanneling() && channeling instanceof RedshaSpell;
        boolean hasPortals;
        synchronized (ACTIVE_PORTALS) {
            hasPortals = !ACTIVE_PORTALS.isEmpty();
        }
        if (!hasPortals && !localChanneling && REMOTE_CHANNELS.isEmpty() && TRAILS.isEmpty()) return;
        if (!FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();

        synchronized (ACTIVE_PORTALS) {
            for (ClientPortal portal : ACTIVE_PORTALS) {
                renderPortal(portal, pt);
            }
        }

        if (localChanneling && mc.player != null) {
            float progress = ClientMagicData.isReadyToCast() ? 1.0f : ClientMagicData.getChannelProgress();
            renderPreview(mc.player, progress, mc.player.tickCount + pt);
        }
        for (Map.Entry<Integer, Integer> entry : REMOTE_CHANNELS.entrySet()) {
            Entity caster = mc.level.getEntity(entry.getKey());
            if (caster instanceof Player player && player != mc.player) {
                renderPreview(player, Math.min(1.0f, (entry.getValue() + pt) / 40.0f), player.tickCount + pt);
            }
        }

        for (EchoTrail trail : TRAILS) {
            renderTrail(trail);
        }
        FxDraw.end();
    }

    private static void renderPortal(ClientPortal portal, float pt) {
        float t = portal.ticksActive + pt;
        float open = MagicEase.backOut(Mth.clamp(t / OPEN_ANIM, 0.0f, 1.0f));
        float close = portal.closing ? 1.0f - Mth.clamp((portal.closeTicks + pt) / CLOSE_ANIM, 0.0f, 1.0f) : 1.0f;
        float scale = open * (portal.closing ? close * close : 1.0f);
        float alpha = Mth.clamp(t / 6.0f, 0.0f, 1.0f) * close;
        // Spins fast while unfolding/collapsing, then settles
        float spin = t * 0.04f + (1.0f - open) * 3.0f + (portal.closing ? (1.0f - close) * 6.0f : 0.0f);

        float flash = 0.0f;
        for (Ripple r : portal.ripples) {
            float age = portal.ticksActive - r.born() + pt;
            flash = Math.max(flash, 1.0f - age / 8.0f);
        }
        drawSeal(portal.center, portal.right, portal.up, RedshaSpell.PORTAL_RADIUS * scale, spin, t, alpha, flash);

        for (Ripple r : portal.ripples) {
            float age = (portal.ticksActive - r.born() + pt) / 14.0f;
            if (age >= 1.0f) continue;
            float a = 1.0f - age;
            FxDraw.ring(portal.center, portal.right, portal.up, RedshaSpell.PORTAL_RADIUS * (1.0 + age * 1.6), 0.25,
                    GOLD[0], GOLD[1], GOLD[2], 0.8f * a, 40);
            FxDraw.ring(portal.center, portal.right, portal.up, RedshaSpell.PORTAL_RADIUS * (0.3 + age * 2.2), 0.18,
                    CRIMSON[0], CRIMSON[1], CRIMSON[2], 0.6f * a, 40);
            FxDraw.glow(r.at().x, r.at().y, r.at().z, 1.2 * a + 0.2, 1.0f, 0.85f, 0.6f, 0.9f * a);
        }
    }

    /**
     * The seal itself, drawn in the plane spanned by right/up.
     */
    private static void drawSeal(Vec3 c, Vec3 right, Vec3 up, double radius, float spin, float t, float alpha, float flash) {
        if (radius < 0.02 || alpha <= 0.01f) return;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.15f);
        float[] rim = mixColor(CRIMSON, GOLD, flash * 0.7f);

        // Membrane: dark crimson glow fading to the rim
        planeDisc(c, right, up, radius * 0.98, rim[0], rim[1] * 0.6f, rim[2], (0.22f + 0.1f * pulse + 0.35f * flash) * alpha);
        FxDraw.glow(c.x, c.y, c.z, radius * 1.6, CRIMSON[0], CRIMSON[1], CRIMSON[2], 0.18f * alpha);

        // Outer rim and dashed counter-rotating ring
        FxDraw.ring(c, right, up, radius, 0.12, rim[0], rim[1], rim[2], 0.95f * alpha, 48);
        FxDraw.ring(c, right, up, radius, 0.035, 1.0f, 0.9f, 0.8f, 0.9f * alpha, 48);
        for (int i = 0; i < 24; i++) {
            if (i % 2 == 1) continue;
            double a0 = -spin * 1.6 + i * Math.PI / 12.0;
            double a1 = a0 + Math.PI / 12.0 * 0.8;
            FxDraw.beam(onPlane(c, right, up, radius * 1.1, a0), onPlane(c, right, up, radius * 1.1, a1), 0.05,
                    GOLD[0], GOLD[1], GOLD[2], 0.85f * alpha);
        }

        // Rune band: small chevrons marching around
        for (int i = 0; i < 16; i++) {
            double a = spin * 1.2 + i * Math.PI / 8.0;
            Vec3 tip = onPlane(c, right, up, radius * 0.92, a);
            Vec3 l = onPlane(c, right, up, radius * 0.8, a - 0.09);
            Vec3 r = onPlane(c, right, up, radius * 0.8, a + 0.09);
            FxDraw.beam(l, tip, 0.035, GOLD[0], GOLD[1], GOLD[2], 0.8f * alpha);
            FxDraw.beam(tip, r, 0.035, GOLD[0], GOLD[1], GOLD[2], 0.8f * alpha);
        }
        FxDraw.ring(c, right, up, radius * 0.76, 0.03, rim[0], rim[1], rim[2], 0.8f * alpha, 40);

        // Octagram (two interlaced squares)
        for (int sq = 0; sq < 2; sq++) {
            for (int i = 0; i < 4; i++) {
                double a0 = -spin * 0.8 + sq * Math.PI / 4.0 + i * Math.PI / 2.0;
                double a1 = a0 + Math.PI / 2.0;
                FxDraw.beam(onPlane(c, right, up, radius * 0.72, a0), onPlane(c, right, up, radius * 0.72, a1), 0.045,
                        rim[0], rim[1], rim[2], 0.85f * alpha);
            }
        }

        // Vortex arms spiralling into the core
        for (int arm = 0; arm < 4; arm++) {
            Vec3 prev = null;
            for (int k = 0; k <= 10; k++) {
                double f = k / 10.0;
                double a = spin * 2.5 + arm * Math.PI / 2.0 + f * 2.4;
                Vec3 p = onPlane(c, right, up, radius * (0.12 + 0.55 * f), a);
                if (prev != null) {
                    FxDraw.beam(prev, p, 0.05, rim[0], rim[1] * 0.8f, rim[2], 0.55f * alpha * (float) (1.0 - f));
                }
                prev = p;
            }
        }

        // Golden core
        FxDraw.glow(c.x, c.y, c.z, radius * (0.35 + 0.1 * pulse + 0.4 * flash), GOLD[0], GOLD[1], GOLD[2], (0.75f + 0.25f * flash) * alpha);
        FxDraw.glow(c.x, c.y, c.z, radius * 0.12, 1.0f, 1.0f, 1.0f, 0.9f * alpha);
        FxDraw.ring(c, right, up, radius * 0.24, 0.04, GOLD[0], GOLD[1], GOLD[2], 0.9f * alpha, 24);

        // Orbiting motes
        for (int i = 0; i < 6; i++) {
            double a = spin * 3.0 + i * Math.PI / 3.0;
            Vec3 p = onPlane(c, right, up, radius * (1.0 + 0.04 * Math.sin(t * 0.3 + i)), a);
            FxDraw.glow(p.x, p.y, p.z, 0.12, 1.0f, 0.9f, 0.7f, 0.9f * alpha);
        }
    }

    /** Ghost of the seal growing in front of a caster who is charging Redsha. */
    private static void renderPreview(Entity caster, float progress, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Interpolated with the frame so the seal stays glued to the view while walking (no jitter)
        float pt = FxDraw.partialTick();
        Vec3 center = RedshaSpell.placement(mc.level, caster, pt);
        Vec3 forward = caster.getViewVector(pt).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();
        float eased = progress * progress * (3 - 2 * progress);
        drawSeal(center, right, up, RedshaSpell.PORTAL_RADIUS * (0.25 + 0.75 * eased), t * (0.05f + 0.15f * progress), t,
                0.25f + 0.35f * progress, 0.0f);
    }

    private static void renderTrail(EchoTrail trail) {
        if (trail.points.size() < 2) return;
        float fade = trail.fading >= 0 ? 1.0f - trail.fading / 8.0f : 1.0f;
        Vec3 prev = null;
        int i = 0;
        int n = trail.points.size();
        for (Vec3 p : trail.points) {
            if (prev != null) {
                float f = (float) i / n;
                FxDraw.beam(prev, p, 0.28 * f, CRIMSON[0], CRIMSON[1], CRIMSON[2], 0.45f * f * fade);
                FxDraw.beam(prev, p, 0.07 * f, 1.0f, 0.75f, 0.6f, 0.9f * f * fade);
            }
            prev = p;
            i++;
        }
        Vec3 head = trail.points.peekLast();
        if (head != null && trail.fading < 0) {
            FxDraw.glow(head.x, head.y, head.z, 0.45, CRIMSON[0], CRIMSON[1], CRIMSON[2], 0.6f);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Vec3 onPlane(Vec3 c, Vec3 right, Vec3 up, double radius, double angle) {
        return c.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
    }

    private static void planeDisc(Vec3 c, Vec3 right, Vec3 up, double radius, float r, float g, float b, float a) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0 / segments;
            double a1 = (i + 1) * Math.PI * 2.0 / segments;
            Vec3 p0 = onPlane(c, right, up, radius, a0);
            Vec3 p1 = onPlane(c, right, up, radius, a1);
            FxDraw.vertex(c.x, c.y, c.z, r, g, b, a);
            FxDraw.vertex(p0.x, p0.y, p0.z, r, g, b, a * 0.25f);
            FxDraw.vertex(p1.x, p1.y, p1.z, r, g, b, a * 0.25f);
            FxDraw.vertex(c.x, c.y, c.z, r, g, b, a);
        }
    }

    private static float[] mixColor(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static final class MagicEase {
        static float backOut(float t) {
            float c1 = 1.70158f;
            float c3 = c1 + 1.0f;
            return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3) + c1 * (float) Math.pow(t - 1.0f, 2);
        }
    }
}
