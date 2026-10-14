package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Custom First-Person renderer for the giant Claymore.
 * Implements the deliberate 3-stage heavy combo sequence:
 * 1. Swing 1: Wide horizontal sweep from right to left (10 DMG), finishes in Left Guard.
 * 2. Swing 2: Wide horizontal sweep from left to right (10 DMG), starts from Left Guard, finishes in Right Guard.
 * 3. Swing 3: 5-Phase Finisher:
 *    - Phase 1: High chamber retraction towards body/head (samurai reference pose).
 *    - Phase 2: Anticipation pause with gathering magic energy.
 *    - Phase 3: Explosive forward straight thrust (15 DMG).
 *    - Phase 4: Sword suspended in mid-air in full forward extension.
 *    - Phase 5: Gradual magical disintegration into shimmering particles and dissolution.
 */
public final class ClaymoreFirstPersonRenderer {

    public enum PoseState {
        IDLE,
        SWING_1,
        GUARD_LEFT,
        SWING_2,
        GUARD_RIGHT,
        SWING_3,
        RETURNING_TO_IDLE
    }

    private static PoseState currentState = PoseState.IDLE;
    private static int stateTicks = 0;
    private static int maxStateTicks = 0;
    private static int guardIdleTicks = 0;
    private static final int GUARD_TIMEOUT = 40; // 2.0s combo window before returning to idle

    // Cached return-to-idle starting values
    private static float returnStartX = 0, returnStartY = 0, returnStartZ = 0;
    private static float returnStartYaw = 0, returnStartPitch = 0, returnStartRoll = 0;

    public static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                               float partialTick, float equipProcess, float swingProcess) {
            applyFirstPersonTransform(poseStack, player, arm, partialTick, equipProcess, swingProcess);
            return true;
        }
    };

    public static void triggerSwingAnimation(int stage, int durationTicks) {
        if (stage == 1) {
            currentState = PoseState.SWING_1;
            stateTicks = durationTicks;
            maxStateTicks = durationTicks;
            guardIdleTicks = 0;
        } else if (stage == 2) {
            currentState = PoseState.SWING_2;
            stateTicks = durationTicks;
            maxStateTicks = durationTicks;
            guardIdleTicks = 0;
        } else if (stage == 3) {
            currentState = PoseState.SWING_3;
            stateTicks = durationTicks;
            maxStateTicks = durationTicks;
            guardIdleTicks = 0;
        }
    }

    public static void tickClient() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // If player switched away from Claymore, reset state immediately
        if (player == null || player.getMainHandItem().getItem() != ModItems.CLAYMORE.get()) {
            currentState = PoseState.IDLE;
            stateTicks = 0;
            maxStateTicks = 0;
            guardIdleTicks = 0;
            return;
        }

        if (stateTicks > 0) {
            stateTicks--;

            // Particle effects during active swings
            spawnSwingParticles(player);

            if (stateTicks <= 0) {
                if (currentState == PoseState.SWING_1) {
                    currentState = PoseState.GUARD_LEFT;
                    guardIdleTicks = 0;
                } else if (currentState == PoseState.SWING_2) {
                    currentState = PoseState.GUARD_RIGHT;
                    guardIdleTicks = 0;
                } else if (currentState == PoseState.SWING_3 || currentState == PoseState.RETURNING_TO_IDLE) {
                    currentState = PoseState.IDLE;
                    guardIdleTicks = 0;
                }
            }
        } else if (currentState == PoseState.GUARD_LEFT || currentState == PoseState.GUARD_RIGHT) {
            guardIdleTicks++;
            if (guardIdleTicks > GUARD_TIMEOUT) {
                // Timeout: smoothly return back to IDLE
                startReturnToIdle();
            }
        }
    }

    private static void startReturnToIdle() {
        if (currentState == PoseState.GUARD_LEFT) {
            returnStartX = -0.42f;
            returnStartY = -0.36f;
            returnStartZ = -0.64f;
            returnStartYaw = -45.0f;
            returnStartPitch = -12.0f;
            returnStartRoll = -20.0f;
        } else {
            returnStartX = 0.40f;
            returnStartY = -0.34f;
            returnStartZ = -0.60f;
            returnStartYaw = 35.0f;
            returnStartPitch = -15.0f;
            returnStartRoll = 12.0f;
        }
        currentState = PoseState.RETURNING_TO_IDLE;
        stateTicks = 10;
        maxStateTicks = 10;
        guardIdleTicks = 0;
    }

    private static void spawnSwingParticles(LocalPlayer player) {
        if (player.level() == null) return;

        if (currentState == PoseState.SWING_1) {
            float p = 1.0f - (float) stateTicks / (float) maxStateTicks;
            if (p >= 0.20f && p <= 0.80f) {
                float sideOff = 0.6f - p * 1.2f;
                spawnParticleInFront(player, 1.8f, sideOff, 0.1f, ParticleTypes.CRIT);
            }
        } else if (currentState == PoseState.SWING_2) {
            float p = 1.0f - (float) stateTicks / (float) maxStateTicks;
            if (p >= 0.20f && p <= 0.80f) {
                float sideOff = -0.6f + p * 1.2f;
                spawnParticleInFront(player, 1.8f, sideOff, 0.1f, ParticleTypes.CRIT);
            }
        } else if (currentState == PoseState.SWING_3) {
            float p = 1.0f - (float) stateTicks / (float) maxStateTicks;
            if (p >= 0.16f && p < 0.26f) {
                // Phase 2: Anticipation magic gathering at tip
                spawnParticleInFront(player, 1.2f, 0.2f, 0.25f, ParticleTypes.ENCHANT);
            } else if (p >= 0.26f && p < 0.36f) {
                // Phase 3: Explosive thrust burst
                spawnParticleInFront(player, 2.5f, 0.05f, 0.1f, ParticleTypes.CRIT);
                spawnParticleInFront(player, 3.2f, 0.05f, 0.1f, ParticleTypes.ENCHANTED_HIT);
            } else if (p >= 0.36f && p < 0.65f) {
                // Phase 4: Suspension magic glow
                if (stateTicks % 2 == 0) {
                    spawnParticleInFront(player, 2.2f + (float) Math.random() * 0.8f,
                            ((float) Math.random() - 0.5f) * 0.3f, 0.1f + ((float) Math.random() - 0.5f) * 0.2f,
                            ParticleTypes.ENCHANT);
                }
            } else if (p >= 0.65f) {
                // Phase 5: Disintegration particles peeling off
                for (int i = 0; i < 3; i++) {
                    float dist = 1.2f + (float) Math.random() * 2.0f;
                    spawnParticleInFront(player, dist,
                            ((float) Math.random() - 0.5f) * 0.4f, 0.1f + ((float) Math.random() - 0.5f) * 0.3f,
                            ParticleTypes.ENCHANTED_HIT);
                }
            }
        }
    }

    private static void spawnParticleInFront(LocalPlayer player, float forward, float right, float up, ParticleOptions particle) {
        Vec3 eye = player.getEyePosition();
        float yawRad = (float) Math.toRadians(player.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3 rgt = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();

        Vec3 pos = eye.add(fwd.scale(forward)).add(rgt.scale(right)).add(0, up, 0);
        player.level().addParticle(particle, pos.x, pos.y, pos.z,
                (Math.random() - 0.5) * 0.04, Math.random() * 0.04, (Math.random() - 0.5) * 0.04);
    }

    private static void applyFirstPersonTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                                  float partialTick, float equipProcess, float vanillaSwing) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float time = player.tickCount + partialTick;

        // Base giant scale: Claymore is a colossal two-handed greatsword
        float scale = 1.6f;

        float posX, posY, posZ;
        float rotYaw, rotPitch, rotRoll;

        // Idle base coordinates
        float idleX = 0.38f;
        float idleY = -0.38f - equipProcess * 0.3f;
        float idleZ = -0.62f;
        float idleYaw = 28.0f;
        float idlePitch = -20.0f;
        float idleRoll = 18.0f;

        if (currentState == PoseState.IDLE) {
            float sway = Mth.sin(time * 0.05f) * 0.012f;
            posX = idleX + sway;
            posY = idleY + Mth.sin(time * 0.1f) * 0.008f;
            posZ = idleZ;
            rotYaw = idleYaw;
            rotPitch = idlePitch;
            rotRoll = idleRoll;

        } else if (currentState == PoseState.SWING_1) {
            float p = 1.0f - Math.max(0.0f, (stateTicks - partialTick) / (float) maxStateTicks);
            p = Mth.clamp(p, 0.0f, 1.0f);

            if (p < 0.20f) {
                // Phase 1A: Windup to the right, leveling blade horizontally
                float t = p / 0.20f;
                float ease = t * t;
                posX = Mth.lerp(ease, idleX, 0.52f);
                posY = Mth.lerp(ease, idleY, -0.32f);
                posZ = Mth.lerp(ease, idleZ, -0.55f);
                rotYaw = Mth.lerp(ease, idleYaw, 48.0f);
                rotPitch = Mth.lerp(ease, idlePitch, -10.0f);
                rotRoll = Mth.lerp(ease, idleRoll, -5.0f);
            } else if (p < 0.80f) {
                // Phase 1B: Powerful horizontal slash from Right to Left
                float t = (p - 0.20f) / 0.60f;
                float ease = (float) Math.sin(t * Math.PI * 0.5);
                posX = Mth.lerp(ease, 0.52f, -0.46f);
                posY = Mth.lerp(ease, -0.32f, -0.36f) - (float) Math.sin(t * Math.PI) * 0.05f;
                posZ = Mth.lerp(ease, -0.55f, -0.64f) - (float) Math.sin(t * Math.PI) * 0.18f;
                rotYaw = Mth.lerp(ease, 48.0f, -48.0f);
                rotPitch = Mth.lerp(ease, -10.0f, -12.0f);
                rotRoll = Mth.lerp(ease, -5.0f, -22.0f);
            } else {
                // Phase 1C: Decelerate and settle into Left Guard
                float t = (p - 0.80f) / 0.20f;
                float ease = 1.0f - (float) Math.pow(1.0f - t, 2);
                posX = Mth.lerp(ease, -0.46f, -0.42f);
                posY = -0.36f;
                posZ = -0.64f;
                rotYaw = Mth.lerp(ease, -48.0f, -45.0f);
                rotPitch = -12.0f;
                rotRoll = Mth.lerp(ease, -22.0f, -20.0f);
            }

        } else if (currentState == PoseState.GUARD_LEFT) {
            // Resting on the left side, ready to return-cut
            float sway = Mth.sin(time * 0.05f) * 0.008f;
            posX = -0.42f + sway;
            posY = -0.36f;
            posZ = -0.64f;
            rotYaw = -45.0f;
            rotPitch = -12.0f;
            rotRoll = -20.0f;

        } else if (currentState == PoseState.SWING_2) {
            float p = 1.0f - Math.max(0.0f, (stateTicks - partialTick) / (float) maxStateTicks);
            p = Mth.clamp(p, 0.0f, 1.0f);

            if (p < 0.18f) {
                // Phase 2A: Tension / pullback on left
                float t = p / 0.18f;
                float ease = t * t;
                posX = Mth.lerp(ease, -0.42f, -0.50f);
                posY = Mth.lerp(ease, -0.36f, -0.32f);
                posZ = Mth.lerp(ease, -0.64f, -0.58f);
                rotYaw = Mth.lerp(ease, -45.0f, -52.0f);
                rotPitch = Mth.lerp(ease, -12.0f, -10.0f);
                rotRoll = Mth.lerp(ease, -20.0f, -15.0f);
            } else if (p < 0.80f) {
                // Phase 2B: Powerful return sweep from Left to Right
                float t = (p - 0.18f) / 0.62f;
                float ease = (float) Math.sin(t * Math.PI * 0.5);
                posX = Mth.lerp(ease, -0.50f, 0.44f);
                posY = Mth.lerp(ease, -0.32f, -0.34f) - (float) Math.sin(t * Math.PI) * 0.04f;
                posZ = Mth.lerp(ease, -0.58f, -0.60f) - (float) Math.sin(t * Math.PI) * 0.16f;
                rotYaw = Mth.lerp(ease, -52.0f, 40.0f);
                rotPitch = Mth.lerp(ease, -10.0f, -15.0f);
                rotRoll = Mth.lerp(ease, -15.0f, 12.0f);
            } else {
                // Phase 2C: Decelerate and settle into Right Guard
                float t = (p - 0.80f) / 0.20f;
                float ease = 1.0f - (float) Math.pow(1.0f - t, 2);
                posX = Mth.lerp(ease, 0.44f, 0.40f);
                posY = -0.34f;
                posZ = -0.60f;
                rotYaw = Mth.lerp(ease, 40.0f, 35.0f);
                rotPitch = -15.0f;
                rotRoll = 12.0f;
            }

        } else if (currentState == PoseState.GUARD_RIGHT) {
            // Resting on the right side, ready for third attack transition
            float sway = Mth.sin(time * 0.05f) * 0.008f;
            posX = 0.40f + sway;
            posY = -0.34f;
            posZ = -0.60f;
            rotYaw = 35.0f;
            rotPitch = -15.0f;
            rotRoll = 12.0f;

        } else if (currentState == PoseState.SWING_3) {
            float p = 1.0f - Math.max(0.0f, (stateTicks - partialTick) / (float) maxStateTicks);
            p = Mth.clamp(p, 0.0f, 1.0f);

            if (p < 0.16f) {
                // Phase 1: High chamber retraction towards body/head (samurai reference)
                float t = p / 0.16f;
                float ease = 1.0f - (float) Math.pow(1.0f - t, 3);
                posX = Mth.lerp(ease, 0.40f, 0.22f);
                posY = Mth.lerp(ease, -0.34f, -0.14f); // raised high
                posZ = Mth.lerp(ease, -0.60f, -0.26f); // tight to body
                rotYaw = Mth.lerp(ease, 35.0f, 4.0f);
                rotPitch = Mth.lerp(ease, -15.0f, -5.0f);
                rotRoll = Mth.lerp(ease, 12.0f, 4.0f);

            } else if (p < 0.26f) {
                // Phase 2: Brief anticipation pause with tense bowstring vibration
                float tension = Mth.sin(time * 0.9f) * 0.003f;
                posX = 0.22f + tension;
                posY = -0.14f + tension;
                posZ = -0.26f;
                rotYaw = 4.0f;
                rotPitch = -5.0f;
                rotRoll = 4.0f;

            } else if (p < 0.36f) {
                // Phase 3: Explosive forward thrust punch straight into crosshair
                float t = (p - 0.26f) / 0.10f;
                float ease = 1.0f - (float) Math.pow(1.0f - t, 3);
                posX = Mth.lerp(ease, 0.22f, 0.04f);
                posY = Mth.lerp(ease, -0.14f, -0.22f);
                posZ = Mth.lerp(ease, -0.26f, -1.25f); // full greatsword forward reach
                rotYaw = Mth.lerp(ease, 4.0f, 2.0f);
                rotPitch = -5.0f;
                rotRoll = Mth.lerp(ease, 4.0f, 2.0f);

            } else if (p < 0.65f) {
                // Phase 4: Sword suspended in mid-air in full forward extension
                float floatY = Mth.sin(time * 0.1f) * 0.006f;
                posX = 0.04f;
                posY = -0.22f + floatY;
                posZ = -1.25f;
                rotYaw = 2.0f;
                rotPitch = -5.0f;
                rotRoll = 2.0f;

            } else {
                // Phase 5: Gradual magical disintegration and dissolution
                float t = (p - 0.65f) / 0.35f;
                float jitter = ((float) Math.random() - 0.5f) * 0.008f * t;
                posX = 0.04f + jitter;
                posY = -0.22f + jitter;
                posZ = -1.25f;
                rotYaw = 2.0f;
                rotPitch = -5.0f;
                rotRoll = 2.0f;

                // Scale gently down as it dissolves
                scale *= (1.0f - t * 0.28f);
            }

        } else if (currentState == PoseState.RETURNING_TO_IDLE) {
            // Smooth interpolation from guard back to idle
            float p = 1.0f - Math.max(0.0f, (stateTicks - partialTick) / (float) maxStateTicks);
            p = Mth.clamp(p, 0.0f, 1.0f);
            float ease = 1.0f - (float) Math.pow(1.0f - p, 2);

            posX = Mth.lerp(ease, returnStartX, idleX);
            posY = Mth.lerp(ease, returnStartY, idleY);
            posZ = Mth.lerp(ease, returnStartZ, idleZ);
            rotYaw = Mth.lerp(ease, returnStartYaw, idleYaw);
            rotPitch = Mth.lerp(ease, returnStartPitch, idlePitch);
            rotRoll = Mth.lerp(ease, returnStartRoll, idleRoll);

        } else {
            posX = idleX;
            posY = idleY;
            posZ = idleZ;
            rotYaw = idleYaw;
            rotPitch = idlePitch;
            rotRoll = idleRoll;
        }

        // Apply transformations
        poseStack.translate(posX * side, posY, posZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw * side));
        poseStack.mulPose(Axis.XP.rotationDegrees(rotPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotRoll * side));
        poseStack.scale(scale, scale, scale);
    }
}
