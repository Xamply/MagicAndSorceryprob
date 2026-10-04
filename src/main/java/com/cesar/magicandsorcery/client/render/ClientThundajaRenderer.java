package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientMagicData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientThundajaRenderer {

    public static class ActiveStorm {
        public final int casterId;
        public Vec3 targetPos;
        public int ticksActive;
        public final int maxTicks = 300; // 15.0s channel
        public boolean isImpacted;
        public int impactTicks;
        public long impactSeed;

        public ActiveStorm(int casterId, Vec3 targetPos) {
            this.casterId = casterId;
            this.targetPos = targetPos;
            this.ticksActive = 0;
            this.isImpacted = false;
            this.impactTicks = 0;
            this.impactSeed = 0;
        }
    }

    private static final List<ActiveStorm> ACTIVE_STORMS = new ArrayList<>();

    // Weather & Atmosphere synchronization
    private static boolean hasBaselineWeather = false;
    private static float baselineRain = 0.0f;
    private static float baselineThunder = 0.0f;

    private static float currentRainLevel = 0.0f;
    private static float currentThunderLevel = 0.0f;
    private static float currentStormFactor = 0.0f;

    // Smooth fade-out state
    private static boolean isFadingOut = false;
    private static int fadeOutTicks = 0;
    private static int maxFadeOutTicks = 80;
    private static float fadeOutStartRain = 0.0f;
    private static float fadeOutStartThunder = 0.0f;
    private static float fadeOutStartFactor = 0.0f;

    public static void startStorm(int casterId, Vec3 targetPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !hasBaselineWeather) {
            baselineRain = mc.level.getRainLevel(1.0f);
            baselineThunder = mc.level.getThunderLevel(1.0f);
            currentRainLevel = baselineRain;
            currentThunderLevel = baselineThunder;
            hasBaselineWeather = true;
        }
        isFadingOut = false;
        fadeOutTicks = 0;

        synchronized (ACTIVE_STORMS) {
            ActiveStorm existing = null;
            for (ActiveStorm s : ACTIVE_STORMS) {
                if (s.casterId == casterId && !s.isImpacted) {
                    existing = s;
                    break;
                }
            }
            if (existing != null) {
                existing.targetPos = targetPos;
            } else {
                ACTIVE_STORMS.removeIf(s -> s.casterId == casterId);
                ACTIVE_STORMS.add(new ActiveStorm(casterId, targetPos));
            }
        }
    }

    public static void updateStorm(int casterId, Vec3 targetPos) {
        synchronized (ACTIVE_STORMS) {
            for (ActiveStorm storm : ACTIVE_STORMS) {
                if (storm.casterId == casterId && !storm.isImpacted) {
                    storm.targetPos = targetPos;
                    break;
                }
            }
        }
    }

    public static void cancelStorm(int casterId) {
        synchronized (ACTIVE_STORMS) {
            ACTIVE_STORMS.removeIf(s -> s.casterId == casterId);
            if (ACTIVE_STORMS.isEmpty() && hasBaselineWeather && !isFadingOut) {
                // Return smoothly to baseline weather over 3 seconds (60 ticks)
                startFadeOut(60);
            }
        }
    }

    public static void triggerImpact(int casterId, Vec3 targetPos, long seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // Blinding full-sky lightning flash at moment of impact
            mc.level.setSkyFlashTime(6);
        }

        synchronized (ACTIVE_STORMS) {
            // Find storm by casterId or near targetPos or create impact visual
            boolean matched = false;
            for (ActiveStorm storm : ACTIVE_STORMS) {
                if (!storm.isImpacted && (storm.casterId == casterId || storm.targetPos.distanceToSqr(targetPos) < 100.0)) {
                    storm.targetPos = targetPos;
                    storm.isImpacted = true;
                    storm.impactTicks = 0;
                    storm.impactSeed = seed;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                ActiveStorm impactStorm = new ActiveStorm(casterId, targetPos);
                impactStorm.ticksActive = 300;
                impactStorm.isImpacted = true;
                impactStorm.impactTicks = 0;
                impactStorm.impactSeed = seed;
                ACTIVE_STORMS.add(impactStorm);
            }
        }

        // Camera Shake and Audio on client
        if (mc.player != null) {
            double distSq = mc.player.position().distanceToSqr(targetPos);
            if (distSq < 64.0 * 64.0) {
                float intensity = (float) Math.max(0.1, 1.0 - (Math.sqrt(distSq) / 64.0));
                mc.player.setXRot(mc.player.getXRot() + (float) ((Math.random() - 0.5) * 3.5 * intensity));
                mc.player.setYRot(mc.player.getYRot() + (float) ((Math.random() - 0.5) * 4.5 * intensity));
            }
        }
    }

    public static void triggerImpact(Vec3 targetPos, long seed) {
        triggerImpact(-1, targetPos, seed);
    }

    private static void startFadeOut(int durationTicks) {
        isFadingOut = true;
        fadeOutTicks = 0;
        maxFadeOutTicks = Math.max(1, durationTicks);
        fadeOutStartRain = currentRainLevel;
        fadeOutStartThunder = currentThunderLevel;
        fadeOutStartFactor = currentStormFactor;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            hasBaselineWeather = false;
            isFadingOut = false;
            ACTIVE_STORMS.clear();
            return;
        }

        if (ACTIVE_STORMS.isEmpty() && !isFadingOut) {
            return;
        }

        // 1. Handle Smooth Weather & Fog Fade-Out (cancellation or post-impact return to baseline)
        if (isFadingOut) {
            fadeOutTicks++;
            float progress = Math.min(1.0f, (float) fadeOutTicks / (float) maxFadeOutTicks);
            // Smooth Hermite ease-out curve
            float ease = progress * progress * (3.0f - 2.0f * progress);

            currentRainLevel = fadeOutStartRain + (baselineRain - fadeOutStartRain) * ease;
            currentThunderLevel = fadeOutStartThunder + (baselineThunder - fadeOutStartThunder) * ease;
            currentStormFactor = fadeOutStartFactor * (1.0f - ease);

            mc.level.setRainLevel(currentRainLevel);
            mc.level.setThunderLevel(currentThunderLevel);

            if (fadeOutTicks >= maxFadeOutTicks) {
                mc.level.setRainLevel(baselineRain);
                mc.level.setThunderLevel(baselineThunder);
                currentRainLevel = baselineRain;
                currentThunderLevel = baselineThunder;
                currentStormFactor = 0.0f;
                isFadingOut = false;
                hasBaselineWeather = false;
            }
            return;
        }

        // 2. Process Active Storms
        int maxActiveTicks = 0;
        boolean anyActive = false;
        boolean anyImpacted = false;

        synchronized (ACTIVE_STORMS) {
            Iterator<ActiveStorm> iterator = ACTIVE_STORMS.iterator();
            while (iterator.hasNext()) {
                ActiveStorm storm = iterator.next();

                if (storm.isImpacted) {
                    storm.impactTicks++;
                    anyImpacted = true;
                    if (storm.impactTicks > 24) { // 1.2s flash & dissipation
                        iterator.remove();
                        continue;
                    }
                } else {
                    boolean isLocalCaster = (mc.player != null && storm.casterId == mc.player.getId());
                    if (isLocalCaster) {
                        // The local player's storm remains active strictly while channeling
                        if (!ClientMagicData.isChanneling()) {
                            iterator.remove();
                            continue;
                        }

                        // Continuously track ground target where the cursor is looking
                        Vec3 newTarget = com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.findThundajaGroundTarget(
                                mc.level, mc.player, 36.0);
                        storm.targetPos = newTarget;

                        // Sync to server every 2 ticks during channeling (10 times per second)
                        if (storm.ticksActive % 2 == 0) {
                            com.cesar.magicandsorcery.network.ModNetwork.sendToServer(
                                    new com.cesar.magicandsorcery.network.packets.PacketThundajaChannel(
                                            com.cesar.magicandsorcery.network.packets.PacketThundajaChannel.ACTION_UPDATE,
                                            newTarget
                                    )
                            );
                        }
                    } else if (storm.casterId != -1) {
                        // Remote player: verify entity still exists in client world
                        if (mc.level.getEntity(storm.casterId) == null && storm.ticksActive > 100) {
                            iterator.remove();
                            continue;
                        }
                    }

                    storm.ticksActive++;
                    anyActive = true;
                    if (storm.ticksActive > maxActiveTicks) {
                        maxActiveTicks = storm.ticksActive;
                    }
                }

                // Tick Particle & Audio Progression
                tickStormEffects(mc, storm);
            }

            // If all storms just completed impact dissipation, smoothly return to baseline over 4 seconds (80 ticks)
            if (ACTIVE_STORMS.isEmpty() && hasBaselineWeather && !isFadingOut) {
                startFadeOut(80);
            }
        }

        // 3. Compute Progressive Weather and Atmospheric Storm Factor across the 5 Stages
        if (anyActive) {
            int t = maxActiveTicks;
            if (t < 60) {
                // Etapa 1: 0 - 3s (0 - 60 ticks) - Primera alteración del cielo, nubes iniciales, luz disminuye muy sutilmente
                float p = t / 60.0f;
                currentStormFactor = 0.15f * p;
                currentRainLevel = Math.max(baselineRain, 0.15f * p);
                currentThunderLevel = baselineThunder;
            } else if (t < 140) {
                // Etapa 2: 3 - 7s (60 - 140 ticks) - Cielo encapotado, gris más oscuro, luz ambiental desciende notablemente
                float p = (t - 60.0f) / 80.0f;
                currentStormFactor = 0.15f + 0.30f * p;
                currentRainLevel = Math.max(baselineRain, 0.15f + 0.35f * p);
                currentThunderLevel = Math.max(baselineThunder, 0.20f * p);
            } else if (t < 220) {
                // Etapa 3: 7 - 11s (140 - 220 ticks) - Comienza la lluvia en el mundo, nubes densas, truenos lejanos
                float p = (t - 140.0f) / 80.0f;
                currentStormFactor = 0.45f + 0.30f * p;
                currentRainLevel = Math.max(baselineRain, 0.50f + 0.35f * p);
                currentThunderLevel = Math.max(baselineThunder, 0.20f + 0.40f * p);
            } else if (t < 280) {
                // Etapa 4: 11 - 14s (220 - 280 ticks) - Tormenta eléctrica completa, lluvia pesada, relámpagos iluminando cielo
                float p = (t - 220.0f) / 60.0f;
                currentStormFactor = 0.75f + 0.20f * p;
                currentRainLevel = Math.max(baselineRain, 0.85f + 0.15f * p);
                currentThunderLevel = Math.max(baselineThunder, 0.60f + 0.35f * p);

                // Relámpagos intermitentes iluminando el cielo completo
                if (t == 235 || t == 265) {
                    mc.level.setSkyFlashTime(2);
                }
            } else {
                // Etapa 5: 14s en adelante (280+ ticks) — Clímax continuo que persiste hasta soltar la tecla o cancelar
                currentStormFactor = 1.0f;
                currentRainLevel = 1.0f;
                currentThunderLevel = 1.0f;

                // Destello blanco previo en el cielo al segundo 14.75 (tick 295)
                // y relámpagos periódicos en el cielo si se mantiene extendida la canalización (t >= 300)
                if (t == 295 || (t > 300 && t % 45 == 0)) {
                    mc.level.setSkyFlashTime(2);
                }
            }

            mc.level.setRainLevel(currentRainLevel);
            mc.level.setThunderLevel(currentThunderLevel);
        } else if (anyImpacted) {
            currentStormFactor = 1.0f;
            currentRainLevel = 1.0f;
            currentThunderLevel = 1.0f;
            mc.level.setRainLevel(currentRainLevel);
            mc.level.setThunderLevel(currentThunderLevel);
        }
    }

    private static void tickStormEffects(Minecraft mc, ActiveStorm storm) {
        Vec3 center = storm.targetPos;
        Random rand = mc.level.random != null ? new Random(mc.level.random.nextLong()) : new Random();
        int t = storm.ticksActive;

        if (!storm.isImpacted) {
            // === ETAPA 1: 0 - 3s (0 - 60 ticks) ===
            if (t < 60) {
                // Primeras nubes reuniéndose en la altura (Y + 16 a Y + 22)
                if (rand.nextFloat() < 0.45f) {
                    double cx = center.x + (rand.nextDouble() - 0.5) * 14.0;
                    double cy = center.y + 17.0 + rand.nextDouble() * 4.0;
                    double cz = center.z + (rand.nextDouble() - 0.5) * 14.0;
                    mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz,
                            (center.x - cx) * 0.015, -0.01, (center.z - cz) * 0.015);
                }

                // Trueno distante inicial
                if (t == 15) {
                    mc.level.playLocalSound(center.x, center.y + 12.0, center.z,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.4f, 0.55f, false);
                }
            }
            // === ETAPA 2: 3 - 7s (60 - 140 ticks) ===
            else if (t < 140) {
                // Nubes densas oscuras cubriendo el área
                if (rand.nextFloat() < 0.70f) {
                    double cx = center.x + (rand.nextDouble() - 0.5) * 16.0;
                    double cy = center.y + 18.0 + rand.nextDouble() * 4.5;
                    double cz = center.z + (rand.nextDouble() - 0.5) * 16.0;
                    mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz,
                            (center.x - cx) * 0.02, -0.012, (center.z - cz) * 0.02);
                }

                // Retumbo lejano en el cielo encapotado
                if (t == 90) {
                    mc.level.playLocalSound(center.x, center.y + 15.0, center.z,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.6f, 0.60f, false);
                }
            }
            // === ETAPA 3: 7 - 11s (140 - 220 ticks) ===
            else if (t < 220) {
                // Lluvia cayendo en el área 13x13
                int rainDrops = 10 + (t - 140) / 7; // 10 a 22 gotas
                for (int i = 0; i < rainDrops; i++) {
                    double rx = center.x + (rand.nextDouble() - 0.5) * 13.0;
                    double rz = center.z + (rand.nextDouble() - 0.5) * 13.0;
                    double ry = center.y + 15.0 + rand.nextDouble() * 3.0;
                    mc.level.addParticle(ParticleTypes.RAIN, rx, ry, rz, 0, -0.65, 0);

                    if (rand.nextFloat() < 0.25f) {
                        mc.level.addParticle(ParticleTypes.SPLASH, rx, center.y + 0.05, rz, 0, 0.05, 0);
                    }
                }

                // Chispas eléctricas esporádicas en el suelo
                if (rand.nextFloat() < 0.40f) {
                    double sx = center.x + (rand.nextDouble() - 0.5) * 12.5;
                    double sz = center.z + (rand.nextDouble() - 0.5) * 12.5;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, sx, center.y + 0.1, sz, 0, 0.03, 0);
                }

                // Truenos periódicos y chasquidos
                if (t == 160 || t == 200) {
                    mc.level.playLocalSound(center.x, center.y + 15.0, center.z,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0f, 0.65f, false);
                }
                if (t % 35 == 0) {
                    mc.level.playLocalSound(center.x, center.y + 2.0, center.z,
                            SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 1.2f, 1.40f, false);
                }
            }
            // === ETAPA 4: 11 - 14s (220 - 280 ticks) ===
            else if (t < 280) {
                // Lluvia torrencial continua en 13x13
                int heavyRain = 22;
                for (int i = 0; i < heavyRain; i++) {
                    double rx = center.x + (rand.nextDouble() - 0.5) * 13.0;
                    double rz = center.z + (rand.nextDouble() - 0.5) * 13.0;
                    double ry = center.y + 15.0 + rand.nextDouble() * 3.0;
                    mc.level.addParticle(ParticleTypes.RAIN, rx, ry, rz, 0, -0.85, 0);
                    mc.level.addParticle(ParticleTypes.SPLASH, rx, center.y + 0.05, rz, 0, 0.08, 0);
                }

                // Arcos de chispas eléctricas convergiendo intensamente hacia el centro
                for (int i = 0; i < 5; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2.0;
                    double dist = 1.0 + rand.nextDouble() * 5.2;
                    double sx = center.x + Math.cos(angle) * dist;
                    double sz = center.z + Math.sin(angle) * dist;
                    double vx = (center.x - sx) * 0.08;
                    double vz = (center.z - sz) * 0.08;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, sx, center.y + 0.1, sz, vx, 0.04, vz);
                }

                // Fuertes truenos en la tormenta
                if (t == 235 || t == 265) {
                    mc.level.playLocalSound(center.x, center.y + 15.0, center.z,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.6f, 0.75f, false);
                }
            }
            // === ETAPA 5: 14s EN ADELANTE (280+ ticks) — Clímax continuo que persiste hasta soltar o cancelar ===
            else {
                // Lluvia torrencial continua en el área 13x13 que NUNCA desaparece
                int heavyRain = 24;
                for (int i = 0; i < heavyRain; i++) {
                    double rx = center.x + (rand.nextDouble() - 0.5) * 13.0;
                    double rz = center.z + (rand.nextDouble() - 0.5) * 13.0;
                    double ry = center.y + 15.0 + rand.nextDouble() * 3.0;
                    mc.level.addParticle(ParticleTypes.RAIN, rx, ry, rz, 0, -0.85, 0);
                    mc.level.addParticle(ParticleTypes.SPLASH, rx, center.y + 0.05, rz, 0, 0.08, 0);
                }

                // Arcos de chispas eléctricas convergiendo activamente hacia el centro
                for (int i = 0; i < 7; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2.0;
                    double dist = 0.5 + rand.nextDouble() * 5.5;
                    double sx = center.x + Math.cos(angle) * dist;
                    double sz = center.z + Math.sin(angle) * dist;
                    double vx = (center.x - sx) * 0.12;
                    double vz = (center.z - sz) * 0.12;
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, sx, center.y + 0.1, sz, vx, 0.06, vz);
                }

                // Destellos pulsantes constantes en el núcleo
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.5, center.z,
                        (rand.nextDouble() - 0.5) * 0.5, 0.25, (rand.nextDouble() - 0.5) * 0.5);

                // Tono de advertencia acústico al aproximarse a 15s (tick 285)
                if (t == 285) {
                    mc.level.playLocalSound(center.x, center.y + 1.0, center.z,
                            SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 2.2f, 1.85f, false);
                }

                // Destello en el centro al segundo 14.75 (tick 295)
                if (t == 295) {
                    mc.level.addParticle(ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 0, 0, 0);
                    mc.level.playLocalSound(center.x, center.y + 10.0, center.z,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0f, 0.9f, false);
                }

                // Estado de sobrecarga sostenido mientras la canalización se mantiene extendida (t >= 300)
                if (t >= 300) {
                    // Truenos periódicos continuos
                    if (t % 40 == 0) {
                        mc.level.playLocalSound(center.x, center.y + 15.0, center.z,
                                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.5f, 0.75f, false);
                    }
                    if (t % 25 == 0) {
                        mc.level.playLocalSound(center.x, center.y + 2.0, center.z,
                                SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 1.3f, 1.35f, false);
                    }
                    // Destellos de plasma ocasionales en el núcleo
                    if (t % 30 == 0) {
                        mc.level.addParticle(ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 0, 0, 0);
                    }
                }
            }
        } else {
            // === ETAPA DE IMPACTO: EFECTOS POSTERIORES ===
            if (storm.impactTicks <= 5) {
                mc.level.addParticle(ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 0, 0, 0);
            }
            // Anillo de choque expansivo de chispas en el suelo (hasta 8.0 bloques)
            double shockR = Math.min(8.0, storm.impactTicks * 0.55);
            for (int i = 0; i < 14; i++) {
                double a = rand.nextDouble() * Math.PI * 2.0;
                double px = center.x + Math.cos(a) * shockR;
                double pz = center.z + Math.sin(a) * shockR;
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, center.y + 0.1, pz,
                        Math.cos(a) * 0.12, 0.04, Math.sin(a) * 0.12);
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (currentStormFactor <= 0.001f) return;

        // Tinte de tormenta pizarra / azul oscuro (#1F2433 = 0.12f, 0.14f, 0.20f)
        float factor = Math.min(0.85f, currentStormFactor * 0.85f);
        float targetR = 0.12f;
        float targetG = 0.14f;
        float targetB = 0.20f;

        event.setRed(event.getRed() * (1.0f - factor) + targetR * factor);
        event.setGreen(event.getGreen() * (1.0f - factor) + targetG * factor);
        event.setBlue(event.getBlue() * (1.0f - factor) + targetB * factor);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (currentStormFactor <= 0.05f) return;

        // Escala ligeramente la distancia de la niebla lejana para simular la atmósfera densa de la tormenta
        float scale = 1.0f - (0.28f * currentStormFactor);
        event.scaleFarPlaneDistance(Math.max(0.40f, scale));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ShaderCompatHelper.isInvalidRenderPass()) return;
        if (ACTIVE_STORMS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lightning());

        synchronized (ACTIVE_STORMS) {
            for (ActiveStorm storm : ACTIVE_STORMS) {
                if (!storm.isImpacted && mc.player != null && storm.casterId == mc.player.getId()) {
                    storm.targetPos = com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.findThundajaGroundTarget(
                            mc.level, mc.player, 36.0);
                }
                renderStorm(consumer, matrix, storm, partialTick);
            }
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderStorm(VertexConsumer consumer, Matrix4f matrix, ActiveStorm storm, float partialTick) {
        Vec3 center = storm.targetPos;
        float exactTicks = storm.ticksActive + partialTick;

        Minecraft mc = Minecraft.getInstance();
        double groundY = center.y;
        if (mc.level != null && mc.player != null) {
            double checkY = Math.min(mc.level.getMaxBuildHeight() - 1, center.y + 2.0);
            BlockHitResult groundHit = mc.level.clip(new ClipContext(
                    new Vec3(center.x, checkY, center.z),
                    new Vec3(center.x, mc.level.getMinBuildHeight(), center.z),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));
            if (groundHit.getType() == HitResult.Type.BLOCK) {
                groundY = groundHit.getLocation().y;
            }
        }
        Vec3 groundCenter = new Vec3(center.x, groundY, center.z);

        if (!storm.isImpacted) {
            float progress = Math.min(1.0f, exactTicks / 300.0f);

            // 1. Ground 13x13 Danger Perimeter & Runic Ring strictly at ground level
            renderGroundPerimeter(consumer, matrix, groundCenter, progress, exactTicks);

            // 2. Converging Electric Arcs (Phase 3: progress > 0.66)
            if (progress > 0.66f) {
                renderGroundArcs(consumer, matrix, groundCenter, progress, exactTicks);
            }
        } else {
            // 3. Phase 4: Colossal Lightning Bolt Impact
            renderColossalLightning(consumer, matrix, storm, partialTick);
        }
    }

    /**
     * Renders the 13x13 block danger square and inner runic circle on the ground.
     */
    private static void renderGroundPerimeter(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float progress, float ticks) {
        float y = (float) center.y + 0.035f;
        float halfSize = 6.5f; // 13x13 blocks: -6.5 to +6.5
        float minX = (float) center.x - halfSize;
        float maxX = (float) center.x + halfSize;
        float minZ = (float) center.z - halfSize;
        float maxZ = (float) center.z + halfSize;

        // Color shifts from golden-yellow (Phase 1) to bright cyan-white (Phase 3+)
        float r = 1.0f;
        float g = 0.85f + 0.15f * progress;
        float b = 0.30f + 0.70f * progress;
        float pulse = 0.5f + 0.5f * (float) Math.sin(ticks * 0.18);
        if (progress >= 1.0f) {
            // Overcharged ready aura while holding the spell ready to strike
            r = 0.4f + 0.6f * pulse;
            g = 1.0f;
            b = 1.0f;
        }
        float alpha = (0.25f + 0.55f * progress) * (0.8f + 0.2f * pulse);

        float lineThick = 0.07f + 0.04f * progress;

        // Draw 4 perimeter borders of 13x13 area
        drawFlatQuadLine(consumer, matrix, minX, y, minZ, maxX, y, minZ, lineThick, r, g, b, alpha);
        drawFlatQuadLine(consumer, matrix, maxX, y, minZ, maxX, y, maxZ, lineThick, r, g, b, alpha);
        drawFlatQuadLine(consumer, matrix, maxX, y, maxZ, minX, y, maxZ, lineThick, r, g, b, alpha);
        drawFlatQuadLine(consumer, matrix, minX, y, maxZ, minX, y, minZ, lineThick, r, g, b, alpha);

        // Inscribed circular warning ring (radius 6.5 blocks)
        int segments = 40;
        for (int i = 0; i < segments; i++) {
            double a1 = (i * 2.0 * Math.PI) / segments;
            double a2 = ((i + 1) * 2.0 * Math.PI) / segments;
            float x1 = (float) (center.x + Math.cos(a1) * 6.5);
            float z1 = (float) (center.z + Math.sin(a1) * 6.5);
            float x2 = (float) (center.x + Math.cos(a2) * 6.5);
            float z2 = (float) (center.z + Math.sin(a2) * 6.5);
            drawFlatQuadLine(consumer, matrix, x1, y, z1, x2, y, z2, lineThick * 0.6f, r, g, b, alpha * 0.65f);
        }
    }

    /**
     * Renders crackling electric arcs crawling on the ground toward the center in Phase 3.
     */
    private static void renderGroundArcs(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float progress, float ticks) {
        float y = (float) center.y + 0.05f;
        Random arcRand = new Random((long) (ticks * 1.5));

        for (int a = 0; a < 4; a++) {
            double startAngle = (a * Math.PI / 2.0) + (arcRand.nextDouble() - 0.5) * 0.6;
            double startDist = 5.5 + arcRand.nextDouble() * 0.9;
            Vec3 start = new Vec3(center.x + Math.cos(startAngle) * startDist, y, center.z + Math.sin(startAngle) * startDist);
            Vec3 end = new Vec3(center.x + (arcRand.nextDouble() - 0.5) * 0.8, y, center.z + (arcRand.nextDouble() - 0.5) * 0.8);

            drawGroundZigzagArc(consumer, matrix, start, end, arcRand, 0.04f, 0.9f, 0.95f, 1.0f, 0.85f);
        }
    }

    private static void drawGroundZigzagArc(VertexConsumer consumer, Matrix4f matrix,
                                            Vec3 p0, Vec3 p1, Random rand,
                                            float width, float r, float g, float b, float a) {
        int segs = 6;
        Vec3 prev = p0;
        Vec3 dir = p1.subtract(p0);
        Vec3 perp = new Vec3(-dir.z, 0, dir.x).normalize();

        for (int i = 1; i <= segs; i++) {
            double frac = (double) i / segs;
            Vec3 next = p0.lerp(p1, frac);
            if (i < segs) {
                double j = (rand.nextDouble() - 0.5) * 0.45;
                next = next.add(perp.scale(j));
            }
            drawFlatQuadLine(consumer, matrix, (float) prev.x, (float) prev.y, (float) prev.z,
                    (float) next.x, (float) next.y, (float) next.z, width, r, g, b, a);
            prev = next;
        }
    }

    /**
     * Renders Phase 4: Colossal Super-Lightning Bolt slamming from clouds to ground.
     */
    private static void renderColossalLightning(VertexConsumer consumer, Matrix4f matrix, ActiveStorm storm, float partialTick) {
        Vec3 center = storm.targetPos;
        float age = storm.impactTicks + partialTick;
        float life = 1.0f - (age / 24.0f);
        if (life <= 0) return;

        // Violent flicker intensity
        float flicker = 0.85f + 0.15f * (float) Math.sin(age * 2.2);
        float alpha = life * flicker;

        float groundY = (float) center.y;
        float skyY = groundY + 42.0f;
        float cx = (float) center.x;
        float cz = (float) center.z;

        // 1. Inner Pure White Plasma Core (Radius: ~1.2 blocks)
        float coreR = 1.2f * (0.6f + 0.4f * life);
        drawVerticalCylinder(consumer, matrix, cx, groundY, skyY, cz, coreR, 1.0f, 1.0f, 1.0f, alpha);

        // 2. Outer Blinding Cyan/Azure Sheath (Radius: ~2.5 blocks)
        float sheathR = 2.5f * (0.7f + 0.3f * life);
        drawVerticalCylinder(consumer, matrix, cx, groundY, skyY, cz, sheathR, 0.25f, 0.80f, 1.0f, alpha * 0.65f);

        // 3. Branching Jagged Lightning Strikes to the 4 corners of the 13x13 zone
        Random branchRand = new Random(storm.impactSeed);
        float[][] corners = {
                {-5.5f, -5.5f},
                { 5.5f, -5.5f},
                {-5.5f,  5.5f},
                { 5.5f,  5.5f},
                { 0.0f, -6.0f},
                { 0.0f,  6.0f}
        };

        for (float[] offset : corners) {
            float branchStartX = cx + (branchRand.nextFloat() - 0.5f) * 1.5f;
            float branchStartZ = cz + (branchRand.nextFloat() - 0.5f) * 1.5f;
            float branchStartY = groundY + 12.0f + branchRand.nextFloat() * 15.0f;

            float branchEndX = cx + offset[0];
            float branchEndZ = cz + offset[1];

            drawBranchingBolt(consumer, matrix,
                    new Vec3(branchStartX, branchStartY, branchStartZ),
                    new Vec3(branchEndX, groundY, branchEndZ),
                    branchRand, 0.12f, 0.8f, 0.95f, 1.0f, alpha * 0.9f);
        }

        // 4. Ground Shockwave Plasma Ring expanding outwards (0 -> 8.0 blocks)
        float shockR = Math.min(8.0f, age * 0.55f);
        float shockAlpha = Math.max(0.0f, (1.0f - (shockR / 8.0f))) * alpha;
        if (shockAlpha > 0.01f) {
            int segs = 32;
            float ringY = groundY + 0.05f;
            for (int i = 0; i < segs; i++) {
                double a1 = (i * 2.0 * Math.PI) / segs;
                double a2 = ((i + 1) * 2.0 * Math.PI) / segs;
                float x1 = (float) (cx + Math.cos(a1) * shockR);
                float z1 = (float) (cz + Math.sin(a1) * shockR);
                float x2 = (float) (cx + Math.cos(a2) * shockR);
                float z2 = (float) (cz + Math.sin(a2) * shockR);
                drawFlatQuadLine(consumer, matrix, x1, ringY, z1, x2, ringY, z2, 0.22f, 0.6f, 0.95f, 1.0f, shockAlpha);
            }
        }
    }

    private static void drawVerticalCylinder(VertexConsumer consumer, Matrix4f matrix,
                                             float cx, float yMin, float yMax, float cz,
                                             float r, float cr, float cg, float cb, float ca) {
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double a1 = (i * 2.0 * Math.PI) / sides;
            double a2 = ((i + 1) * 2.0 * Math.PI) / sides;

            float x1 = (float) (cx + Math.cos(a1) * r);
            float z1 = (float) (cz + Math.sin(a1) * r);
            float x2 = (float) (cx + Math.cos(a2) * r);
            float z2 = (float) (cz + Math.sin(a2) * r);

            consumer.vertex(matrix, x1, yMin, z1).color(cr, cg, cb, ca).endVertex();
            consumer.vertex(matrix, x2, yMin, z2).color(cr, cg, cb, ca).endVertex();
            consumer.vertex(matrix, x2, yMax, z2).color(cr, cg, cb, ca * 0.7f).endVertex();
            consumer.vertex(matrix, x1, yMax, z1).color(cr, cg, cb, ca * 0.7f).endVertex();
        }
    }

    private static void drawBranchingBolt(VertexConsumer consumer, Matrix4f matrix,
                                          Vec3 p0, Vec3 p1, Random rand,
                                          float width, float r, float g, float b, float a) {
        int segs = 5;
        Vec3 prev = p0;
        Vec3 dir = p1.subtract(p0);
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();

        for (int i = 1; i <= segs; i++) {
            double frac = (double) i / segs;
            Vec3 next = p0.lerp(p1, frac);
            if (i < segs) {
                double jSide = (rand.nextFloat() - 0.5) * 0.65;
                double jY = (rand.nextFloat() - 0.5) * 0.35;
                next = next.add(side.scale(jSide)).add(0, jY, 0);
            }
            draw3DQuadSegment(consumer, matrix, prev, next, width, r, g, b, a);
            prev = next;
        }
    }

    private static void draw3DQuadSegment(VertexConsumer consumer, Matrix4f matrix,
                                          Vec3 p0, Vec3 p1, float width,
                                          float r, float g, float b, float a) {
        Vec3 dir = p1.subtract(p0).normalize();
        Vec3 up = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp = dir.cross(up).normalize().scale(width / 2.0);

        consumer.vertex(matrix, (float) (p0.x - perp.x), (float) (p0.y - perp.y), (float) (p0.z - perp.z)).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) (p0.x + perp.x), (float) (p0.y + perp.y), (float) (p0.z + perp.z)).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) (p1.x + perp.x), (float) (p1.y + perp.y), (float) (p1.z + perp.z)).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) (p1.x - perp.x), (float) (p1.y - perp.y), (float) (p1.z - perp.z)).color(r, g, b, a).endVertex();
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
