package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Custom First-Person and Third-Person rendering transformations for the giant Claymore.
 * Gives the Claymore exceptional size and custom swing animations for each combo stage:
 * 1. Swing 1: Wide horizontal sweep from right to left (10 DMG).
 * 2. Swing 2: Wide horizontal sweep from left to right (10 DMG).
 * 3. Swing 3: Heavy windup and forward thrust/pierce (15 DMG).
 */
public final class ClaymoreFirstPersonRenderer {

    // Current local animation state
    private static int activeSwingStage = 0; // 0 = idle, 1 = right-to-left, 2 = left-to-right, 3 = thrust
    private static int swingTicks = 0;
    private static int maxSwingTicks = 0;

    public static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                               float partialTick, float equipProcess, float swingProcess) {
            applyFirstPersonTransform(poseStack, player, arm, partialTick, equipProcess, swingProcess);
            return true;
        }
    };

    public static void triggerSwingAnimation(int stage, int durationTicks) {
        activeSwingStage = stage;
        swingTicks = durationTicks;
        maxSwingTicks = durationTicks;
    }

    public static void tickClient() {
        if (swingTicks > 0) {
            swingTicks--;
            if (swingTicks <= 0) {
                activeSwingStage = 0;
            }
        }
    }

    private static void applyFirstPersonTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                                 float partialTick, float equipProcess, float vanillaSwing) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float time = player.tickCount + partialTick;

        // Base giant scale: Claymore is noticeably larger than normal swords
        float scale = 1.65f;

        // Idle rest position: 2-handed giant greatsword held angled diagonally across chest
        poseStack.translate(side * 0.42f, -0.42f - equipProcess * 0.4f, -0.68f);

        // Breathing / idle sway
        float sway = Mth.sin(time * 0.05f);
        poseStack.translate(sway * 0.015f, Mth.sin(time * 0.1f) * 0.008f, 0.0f);

        if (activeSwingStage == 0) {
            // Idle stance: heavy, held pointing upward-forward at ~35 degrees
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 35.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 15.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-25.0f));
            poseStack.scale(scale, scale, scale);
            return;
        }

        // Animated combo swings
        float progress = 1.0f - Math.max(0.0f, (swingTicks - partialTick) / (float) maxSwingTicks);
        progress = Mth.clamp(progress, 0.0f, 1.0f);

        if (activeSwingStage == 1) {
            // Swing 1: Sweep Right to Left
            // Ease out cubic
            float ease = 1.0f - (float) Math.pow(1.0f - progress, 3);
            float startAngle = 65.0f;
            float endAngle = -70.0f;
            float currentAngle = startAngle + (endAngle - startAngle) * ease;

            poseStack.translate(-0.1f * ease, -0.1f * Mth.sin(progress * (float) Math.PI), -0.25f * Mth.sin(progress * (float) Math.PI));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * currentAngle));
            poseStack.mulPose(Axis.XP.rotationDegrees(-45.0f - 20.0f * Mth.sin(progress * (float) Math.PI)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * (20.0f - ease * 40.0f)));

        } else if (activeSwingStage == 2) {
            // Swing 2: Sweep Left to Right
            float ease = 1.0f - (float) Math.pow(1.0f - progress, 3);
            float startAngle = -70.0f;
            float endAngle = 65.0f;
            float currentAngle = startAngle + (endAngle - startAngle) * ease;

            poseStack.translate(0.1f * ease, -0.08f * Mth.sin(progress * (float) Math.PI), -0.25f * Mth.sin(progress * (float) Math.PI));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * currentAngle));
            poseStack.mulPose(Axis.XP.rotationDegrees(-45.0f - 20.0f * Mth.sin(progress * (float) Math.PI)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * (-20.0f + ease * 40.0f)));

        } else if (activeSwingStage == 3) {
            // Swing 3: Heavy Thrust / Pierce
            // 0 -> 0.3 windup (pull back), 0.3 -> 0.7 thrust forward, 0.7 -> 1.0 recover
            float thrustZ;
            if (progress < 0.25f) {
                // Wind up backwards
                float p = progress / 0.25f;
                thrustZ = p * 0.25f;
                poseStack.translate(0, -0.05f * p, thrustZ);
                poseStack.mulPose(Axis.XP.rotationDegrees(-15.0f * p));
            } else if (progress < 0.70f) {
                // Forward lunge punch
                float p = (progress - 0.25f) / 0.45f;
                float easeThrust = 1.0f - (float) Math.pow(1.0f - p, 3);
                thrustZ = 0.25f - easeThrust * 0.95f; // deep forward thrust
                poseStack.translate(0, -0.05f - 0.08f * easeThrust, thrustZ);
                poseStack.mulPose(Axis.XP.rotationDegrees(-15.0f + 10.0f * easeThrust));
            } else {
                // Recovery back to rest
                float p = (progress - 0.70f) / 0.30f;
                thrustZ = -0.70f + p * 0.70f;
                poseStack.translate(0, -0.13f + p * 0.13f, thrustZ);
            }
            // Point straight forward
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 5.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0f));
        }

        poseStack.scale(scale, scale, scale);
    }
}
