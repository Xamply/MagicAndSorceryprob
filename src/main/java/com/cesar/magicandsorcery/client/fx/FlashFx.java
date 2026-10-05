package com.cesar.magicandsorcery.client.fx;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Flash visuals: the caster leaves a dissolving ghost of light, a comet streak with a twin helix of sparks
 * traces the jump between two rune circles, and the arrival bursts in rings, a ground shockwave, a pillar
 * of light and sparks. The caster also gets an FOV kick and a violet screen flash.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlashFx {

    private static final int LIFE = 30;
    private static final float[] VIOLET = {0.78f, 0.42f, 1.0f};
    private static final float[] PINK = {1.0f, 0.55f, 0.95f};
    private static final int SPARK_COLOR = 0xE6B3FF;

    private static final class Blink {
        final Vec3 from;
        final Vec3 to;
        final float height;
        int age;

        Blink(Vec3 from, Vec3 to, float height) {
            this.from = from;
            this.to = to;
            this.height = height;
        }
    }

    private static final List<Blink> BLINKS = new ArrayList<>();
    private static int localKickAge = -1;

    // Destination preview while the local player charges Flash (recomputed every tick)
    private static Vec3 previewDest;
    private static Vec3 previewMiss;
    private static int previewSwapId = -1;
    private static int previewAge;

    private FlashFx() {
    }

    public static void add(int casterId, Vec3 from, Vec3 to, float height, long seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BLINKS.add(new Blink(from, to, height));
        RandomSource random = mc.level.random;
        Vec3 dir = to.subtract(from);
        Vec3 flatDir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize();

        // The departing body dissolves into motes drifting along the jump
        for (int i = 0; i < 40; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = random.nextDouble() * 0.35;
            float[] col = random.nextBoolean() ? VIOLET : PINK;
            FxParticles.spawn(FxParticles.Kind.GLOW)
                    .at(from.x + Math.cos(a) * r, from.y + random.nextDouble() * height, from.z + Math.sin(a) * r)
                    .vel(flatDir.x * 0.12 + (random.nextDouble() - 0.5) * 0.06, 0.03 + random.nextDouble() * 0.04,
                            flatDir.z * 0.12 + (random.nextDouble() - 0.5) * 0.06)
                    .color(col[0], col[1], col[2]).size(0.1f + random.nextFloat() * 0.1f)
                    .life(16 + random.nextInt(14)).physics(0.0, 0.9, 0.0).noCollide();
        }
        // Motes left along the path
        Vec3 a = from.add(0, height * 0.55, 0);
        Vec3 b = to.add(0, height * 0.55, 0);
        for (int i = 0; i < 30; i++) {
            Vec3 p = a.lerp(b, random.nextDouble());
            FxParticles.spawn(FxParticles.Kind.GLOW).at(p.x, p.y, p.z)
                    .vel((random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04)
                    .color(PINK[0], PINK[1], PINK[2]).size(0.08f).life(10 + random.nextInt(12)).physics(0, 0.92, 0).noCollide();
        }
        // Arrival: sparks with physics and a spiral of rising light
        FxParticles.sparkBurst(to.x, to.y + height * 0.5, to.z, 28, 0.45, SPARK_COLOR, random);
        for (int i = 0; i < 20; i++) {
            double ang = i * Math.PI * 2.0 / 20.0;
            FxParticles.spawn(FxParticles.Kind.GLOW).at(to.x + Math.cos(ang) * 0.6, to.y + 0.1, to.z + Math.sin(ang) * 0.6)
                    .vel(-Math.sin(ang) * 0.08, 0.12 + random.nextDouble() * 0.06, Math.cos(ang) * 0.08)
                    .color(VIOLET[0], VIOLET[1], VIOLET[2]).size(0.12f).life(18 + random.nextInt(10)).physics(0, 0.94, 0).noCollide();
        }
        for (int i = 0; i < 12; i++) {
            mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, to.x + (random.nextDouble() - 0.5),
                    to.y + random.nextDouble() * height, to.z + (random.nextDouble() - 0.5), 0, 0.05, 0);
        }

        if (mc.player != null && mc.player.getId() == casterId) {
            localKickAge = 0;
        }
    }

    /** Strength (0..1) of the violet screen flash for the local caster. */
    public static float screenFlash(float partialTick) {
        if (localKickAge < 0) return 0.0f;
        float k = localKickAge + partialTick;
        return k < 7.0f ? (1.0f - k / 7.0f) : 0.0f;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BLINKS.clear();
            localKickAge = -1;
            return;
        }
        if (mc.isPaused()) return;
        if (localKickAge >= 0 && ++localKickAge > 20) localKickAge = -1;
        updatePreview(mc);
        Iterator<Blink> it = BLINKS.iterator();
        while (it.hasNext()) {
            if (++it.next().age >= LIFE) it.remove();
        }
    }

    private static void updatePreview(Minecraft mc) {
        com.cesar.magicandsorcery.magic.spell.Spell spell = com.cesar.magicandsorcery.client.ClientMagicData.getChannelingSpell();
        boolean charging = mc.player != null && com.cesar.magicandsorcery.client.ClientMagicData.isChanneling()
                && spell != null && spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.FlashSpell.ID);
        if (!charging) {
            previewDest = null;
            previewMiss = null;
            previewSwapId = -1;
            previewAge = 0;
            return;
        }
        previewAge++;
        double range = spell.getRange(com.cesar.magicandsorcery.client.ClientMagicData.getChannelingMethod());
        previewDest = com.cesar.magicandsorcery.magic.spell.spells.FlashSpell.findDestination(mc.level, mc.player, range);
        previewMiss = previewDest == null ? mc.player.getEyePosition().add(mc.player.getViewVector(1.0f).scale(range * 0.6)) : null;
        net.minecraft.world.entity.LivingEntity swap = previewDest == null ? null
                : com.cesar.magicandsorcery.magic.spell.spells.FlashSpell.findSwapTarget(mc.level, mc.player, previewDest);
        previewSwapId = swap != null ? swap.getId() : -1;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (localKickAge < 0) return;
        float k = localKickAge + (float) event.getPartialTick();
        if (k >= 10.0f) return;
        // Quick outward punch of the field of view, settling back
        float kick = Mth.sin(k / 10.0f * (float) Math.PI) * (1.0f - k / 14.0f);
        event.setFOV(event.getFOV() * (1.0 + 0.16 * kick));
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        boolean preview = previewDest != null || previewMiss != null;
        if ((BLINKS.isEmpty() && !preview) || !FxDraw.begin(event)) return;
        float pt = FxDraw.partialTick();
        for (Blink blink : BLINKS) {
            renderBlink(blink, blink.age + pt);
        }
        if (preview) {
            renderPreview(previewAge + pt);
        }
        FxDraw.end();
    }

    /** Where the blink will land: rune circle, ghost silhouette, guiding motes and the creature that will swap. */
    private static void renderPreview(float t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float progress = com.cesar.magicandsorcery.client.ClientMagicData.isReadyToCast() ? 1.0f
                : com.cesar.magicandsorcery.client.ClientMagicData.getChannelProgress();
        float alpha = 0.35f + 0.65f * progress;

        if (previewDest == null) {
            // Nowhere to land: red X
            Vec3 m = previewMiss;
            Vec3[] basis = FxDraw.basis(m.subtract(mc.player.getEyePosition()));
            Vec3 a = basis[0].add(basis[1]).scale(0.35), b = basis[0].subtract(basis[1]).scale(0.35);
            FxDraw.beam(m.add(a), m.subtract(a), 0.12, 1.0f, 0.25f, 0.3f, 0.9f);
            FxDraw.beam(m.add(b), m.subtract(b), 0.12, 1.0f, 0.25f, 0.3f, 0.9f);
            FxDraw.ring(m, basis[0], basis[1], 0.5, 0.08, 1.0f, 0.25f, 0.3f, 0.7f, 24);
            return;
        }

        Vec3 d = previewDest;
        float h = mc.player.getBbHeight();
        runeCircle(d, t * 0.08f, alpha);
        float breathe = 0.6f + 0.4f * (float) Math.sin(t * 0.3);
        FxDraw.beam(d.x, d.y, d.z, d.x, d.y + h, d.z, 0.7, VIOLET[0], VIOLET[1], VIOLET[2], 0.3f * alpha * breathe, 0.1f * alpha);
        FxDraw.glow(d.x, d.y + h * 0.86, d.z, 0.32, PINK[0], PINK[1], PINK[2], 0.35f * alpha * breathe);
        FxDraw.glow(d.x, d.y + h * 0.5, d.z, 0.5, VIOLET[0], VIOLET[1], VIOLET[2], 0.3f * alpha * breathe);
        double scan = (t * 0.06 % 1.0) * h;
        FxDraw.flatRing(d.x, d.y + scan, d.z, 0.5, 0.08, 1.0f, 0.8f, 1.0f, 0.6f * alpha, 24);

        // Motes flowing from the caster to the destination
        Vec3 from = mc.player.getPosition(FxDraw.partialTick()).add(0, h * 0.6, 0);
        Vec3 to = d.add(0, h * 0.55, 0);
        double dist = from.distanceTo(to);
        int motes = Math.max(6, (int) (dist * 2.5));
        for (int i = 0; i < motes; i++) {
            double f = ((i + t * 0.15) / motes) % 1.0;
            Vec3 p = from.lerp(to, f).add(0, Math.sin(f * Math.PI) * Math.min(1.5, dist * 0.15), 0);
            FxDraw.glow(p.x, p.y, p.z, 0.08, PINK[0], PINK[1], PINK[2], 0.8f * alpha * (float) Math.sin(f * Math.PI));
        }

        if (previewSwapId >= 0) {
            net.minecraft.world.entity.Entity swap = mc.level.getEntity(previewSwapId);
            if (swap != null) {
                Vec3 c = swap.getPosition(FxDraw.partialTick());
                double r = swap.getBbWidth() * 0.8 + 0.3;
                for (int k = 0; k < 2; k++) {
                    double y = c.y + 0.1 + ((t * 0.05 + k * 0.5) % 1.0) * swap.getBbHeight();
                    FxDraw.flatRing(c.x, y, c.z, r, 0.1, 0.4f, 1.0f, 0.85f, 0.8f * alpha, 28);
                }
                FxDraw.glow(c.x, c.y + swap.getBbHeight() * 0.5, c.z, swap.getBbHeight() * 0.7, 0.4f, 1.0f, 0.85f, 0.2f * alpha);
            }
        }
    }

    private static void renderBlink(Blink b, float t) {
        float life = Mth.clamp(1.0f - t / LIFE, 0.0f, 1.0f);
        float appear = Mth.clamp(t / 2.0f, 0.0f, 1.0f);
        float h = b.height;

        // 1. Rune circles at both ends
        runeCircle(b.from, t * 0.06f, life * appear * 0.9f);
        runeCircle(b.to, -t * 0.06f, life * appear);

        // 2. Ghost afterimage dissolving at the departure point
        if (t < 16.0f) {
            float g = 1.0f - t / 16.0f;
            double rise = t * 0.03;
            double grow = 1.0 + t * 0.03;
            Vec3 f = b.from;
            FxDraw.glow(f.x, f.y + h * 0.86 + rise, f.z, 0.36 * grow, VIOLET[0], VIOLET[1], VIOLET[2], 0.6f * g);
            FxDraw.glow(f.x, f.y + h * 0.62 + rise, f.z, 0.5 * grow, VIOLET[0], VIOLET[1], VIOLET[2], 0.55f * g);
            FxDraw.glow(f.x, f.y + h * 0.40 + rise, f.z, 0.45 * grow, PINK[0], PINK[1], PINK[2], 0.45f * g);
            FxDraw.glow(f.x, f.y + h * 0.18 + rise, f.z, 0.35 * grow, PINK[0], PINK[1], PINK[2], 0.4f * g);
            FxDraw.beam(f.x, f.y + rise, f.z, f.x, f.y + h + rise, f.z, 0.75, VIOLET[0], VIOLET[1], VIOLET[2], 0.35f * g, 0.35f * g);
            // Scanning rings sweeping the silhouette
            for (int k = 0; k < 3; k++) {
                double scan = ((t * 0.12 + k / 3.0) % 1.0) * h;
                FxDraw.flatRing(f.x, f.y + scan + rise, f.z, 0.55, 0.12, 1.0f, 0.8f, 1.0f, 0.7f * g, 24);
            }
        }

        // 3. Implosion ring collapsing into the departure point
        if (t < 7.0f) {
            float k = t / 7.0f;
            FxDraw.flatRing(b.from.x, b.from.y + 0.05, b.from.z, 2.8 * (1.0 - k), 0.35, PINK[0], PINK[1], PINK[2], 0.8f * (1.0f - k), 40);
        }

        // 4. Comet streak along the jump: the tail is pulled into the arrival point
        Vec3 a = b.from.add(0, h * 0.55, 0);
        Vec3 z = b.to.add(0, h * 0.55, 0);
        if (t < 13.0f && a.distanceToSqr(z) > 0.01) {
            float s = 1.0f - t / 13.0f;
            float tail = Mth.clamp((t - 1.0f) / 9.0f, 0.0f, 1.0f);
            tail = tail * tail;
            Vec3 start = a.lerp(z, tail);
            FxDraw.beam(start.x, start.y, start.z, z.x, z.y, z.z, 1.4, VIOLET[0], VIOLET[1], VIOLET[2], 0.0f, 0.3f * s);
            FxDraw.beam(start.x, start.y, start.z, z.x, z.y, z.z, 0.42, PINK[0], PINK[1], PINK[2], 0.05f, 0.7f * s);
            FxDraw.beam(start.x, start.y, start.z, z.x, z.y, z.z, 0.12, 1.0f, 1.0f, 1.0f, 0.1f, s);

            // Twin helix of sparks winding around the streak
            Vec3[] basis = FxDraw.basis(z.subtract(a));
            double length = a.distanceTo(z);
            int points = Math.max(10, (int) (length * 3));
            for (int strand = 0; strand < 2; strand++) {
                for (int i = 0; i <= points; i++) {
                    double frac = (double) i / points;
                    if (frac < tail) continue;
                    double ang = frac * length * 2.2 + t * 0.6 + strand * Math.PI;
                    double rad = 0.4 * (1.0 - 0.5 * frac);
                    Vec3 p = a.lerp(z, frac).add(basis[0].scale(Math.cos(ang) * rad)).add(basis[1].scale(Math.sin(ang) * rad));
                    float[] col = strand == 0 ? VIOLET : PINK;
                    FxDraw.glow(p.x, p.y, p.z, 0.13, col[0], col[1], col[2], 0.85f * s);
                }
            }
        }

        // 5. Arrival burst
        Vec3 core = b.to.add(0, h * 0.5, 0);
        if (t < 12.0f) {
            float k = t / 12.0f;
            float inv = 1.0f - k;
            FxDraw.glow(core.x, core.y, core.z, 3.0 * inv + 0.4, VIOLET[0], VIOLET[1], VIOLET[2], 0.55f * inv);
            FxDraw.glow(core.x, core.y, core.z, 1.0 * inv + 0.2, 1, 1, 1, 0.9f * inv);

            Vec3 dir = b.to.subtract(b.from);
            Vec3[] facing = FxDraw.basis(dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir);
            double radius = 0.3 + k * 3.4;
            FxDraw.ring(core, facing[0], facing[1], radius, 0.3, PINK[0], PINK[1], PINK[2], 0.8f * inv, 36);
            FxDraw.ring(core, new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius * 0.85, 0.25, VIOLET[0], VIOLET[1], VIOLET[2], 0.7f * inv, 36);
            Vec3 tilted = new Vec3(0, 0.7071, 0.7071);
            FxDraw.ring(core, new Vec3(1, 0, 0), tilted, radius * 0.7, 0.2, 1.0f, 0.8f, 1.0f, 0.6f * inv, 36);
            FxDraw.flatRing(b.to.x, b.to.y + 0.05, b.to.z, 0.4 + k * 4.6, 0.45, VIOLET[0], VIOLET[1], VIOLET[2], 0.7f * inv, 48);
        }
        if (t < 16.0f) {
            float p = 1.0f - t / 16.0f;
            FxDraw.beam(b.to.x, b.to.y, b.to.z, b.to.x, b.to.y + 6.0, b.to.z, 1.0, VIOLET[0], VIOLET[1], VIOLET[2], 0.5f * p, 0.0f);
            FxDraw.beam(b.to.x, b.to.y, b.to.z, b.to.x, b.to.y + 6.0, b.to.z, 0.22, 1, 1, 1, 0.8f * p, 0.0f);
        }
    }

    /** Flat rune circle: double ring, hexagram and tick marks, slowly turning. */
    private static void runeCircle(Vec3 pos, float rotation, float alpha) {
        if (alpha <= 0.01f) return;
        double y = pos.y + 0.035;
        FxDraw.flatDisc(pos.x, y, pos.z, 1.9, VIOLET[0], VIOLET[1], VIOLET[2], 0.2f * alpha, 24);
        FxDraw.flatRing(pos.x, y, pos.z, 1.6, 0.14, VIOLET[0], VIOLET[1], VIOLET[2], 0.9f * alpha, 48);
        FxDraw.flatRing(pos.x, y, pos.z, 1.15, 0.07, PINK[0], PINK[1], PINK[2], 0.8f * alpha, 40);
        for (int tri = 0; tri < 2; tri++) {
            for (int i = 0; i < 3; i++) {
                double a0 = rotation + tri * Math.PI / 3.0 + i * Math.PI * 2.0 / 3.0;
                double a1 = a0 + Math.PI * 2.0 / 3.0;
                FxDraw.groundLine(pos.x + Math.cos(a0) * 1.15, pos.z + Math.sin(a0) * 1.15,
                        pos.x + Math.cos(a1) * 1.15, pos.z + Math.sin(a1) * 1.15, y + 0.002, 0.07, 1.0f, 0.75f, 1.0f, 0.85f * alpha);
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = -rotation * 1.5 + i * Math.PI / 8.0;
            FxDraw.groundLine(pos.x + Math.cos(a) * 1.25, pos.z + Math.sin(a) * 1.25,
                    pos.x + Math.cos(a) * 1.45, pos.z + Math.sin(a) * 1.45, y + 0.002, 0.05, 1.0f, 0.85f, 1.0f, 0.8f * alpha);
        }
    }
}
