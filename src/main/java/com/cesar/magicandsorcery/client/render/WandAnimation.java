package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.client.SpellVisuals;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Staff animations:
 * - Idle: gentle left-to-right sway.
 * - Charging: the staff trembles and starts to lift.
 * - Spell ready: the staff is raised up high, pulsing with the spell's color.
 * - Release: the staff swings forward and comes back down.
 */
@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WandAnimation {

    private static final int CAST_FLICK_TICKS = 9;

    // Local player animation state (interpolated with partial ticks)
    private static float raise;
    private static float prevRaise;
    private static float tremble;
    private static float prevTremble;
    private static int flickTicks;

    private static HumanoidModel.ArmPose wandPose;
    private static boolean wandPoseSafe;

    public static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                               float partialTick, float equipProcess, float swingProcess) {
            applyFirstPerson(poseStack, player, arm, partialTick, equipProcess, swingProcess);
            return true;
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack itemStack) {
            return getWandPose();
        }
    };

    private WandAnimation() {
    }

    /**
     * Called when a charged spell is released: the staff swings forward and drops back down.
     */
    public static void onCastReleased() {
        flickTicks = CAST_FLICK_TICKS;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) return;

        prevRaise = raise;
        prevTremble = tremble;

        float targetRaise = 0.0f;
        float targetTremble = 0.0f;
        if (ClientMagicData.isChanneling()) {
            if (ClientMagicData.isReadyToCast()) {
                targetRaise = 1.0f;
            } else {
                float progress = ClientMagicData.getChannelProgress();
                targetRaise = 0.3f * progress;
                targetTremble = 0.35f + 0.65f * progress;
            }
        }

        // Rise fast, lower smoothly
        float speed = targetRaise > raise ? 0.32f : 0.24f;
        raise += (targetRaise - raise) * speed;
        tremble += (targetTremble - tremble) * 0.3f;
        if (flickTicks > 0) flickTicks--;

        if (raise > 0.85f && ClientMagicData.isReadyToCast()) {
            spawnTipSparkles(mc);
        }
    }

    private static void spawnTipSparkles(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 2 != 0) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof com.cesar.magicandsorcery.item.MagicWandItem)
                && !(mc.player.getOffhandItem().getItem() instanceof com.cesar.magicandsorcery.item.MagicWandItem)) {
            return;
        }
        Spell spell = ClientMagicData.getChannelingSpell();
        SpellVisuals.Style style = SpellVisuals.of(spell);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        int side = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 tip = mc.player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.25 * side)).add(0, 0.45, 0);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(style.r(), style.g(), style.b()), 0.7f);
        mc.level.addParticle(dust,
                tip.x + (mc.level.random.nextDouble() - 0.5) * 0.25,
                tip.y + (mc.level.random.nextDouble() - 0.5) * 0.25,
                tip.z + (mc.level.random.nextDouble() - 0.5) * 0.25,
                0, 0.02, 0);
    }

    // ------------------------------------------------------------------
    // FIRST PERSON
    // ------------------------------------------------------------------

    private static void applyFirstPerson(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                         float partialTick, float equipProcess, float swingProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float time = player.tickCount + partialTick;
        float lift = Mth.lerp(partialTick, prevRaise, raise);
        float shake = Mth.lerp(partialTick, prevTremble, tremble);
        float flick = flickTicks > 0 ? Math.max(0.0f, (flickTicks - partialTick) / CAST_FLICK_TICKS) : 0.0f;
        // 0 -> 1 -> 0 curve across the release swing
        float flickCurve = Mth.sin((1.0f - flick) * (float) Math.PI) * (flick > 0 ? 1.0f : 0.0f);

        // Vanilla attack-swing offset
        float sqrtSwing = Mth.sqrt(swingProcess);
        poseStack.translate(side * -0.4f * Mth.sin(sqrtSwing * (float) Math.PI),
                0.2f * Mth.sin(sqrtSwing * (float) Math.PI * 2.0f),
                -0.2f * Mth.sin(swingProcess * (float) Math.PI));

        // Vanilla resting hand position
        poseStack.translate(side * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f);

        // Idle sway: left-to-right wobble that fades out while the staff is raised
        float idle = 1.0f - lift;
        float sway = Mth.sin(time * 0.07f);
        poseStack.translate(sway * 0.045f * idle, Mth.sin(time * 0.14f) * 0.012f * idle, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-sway * 8.0f * idle));

        // Charging tremble
        if (shake > 0.001f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 2.1f) * 1.6f * shake));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(time * 2.7f) * 1.2f * shake));
        }

        // Raise the staff up high (towards the center of the view, tip pointing to the sky)
        if (lift > 0.001f) {
            float hover = Mth.sin(time * 0.25f) * 0.025f * lift;
            poseStack.translate(-side * 0.16f * lift, 0.40f * lift + hover, 0.10f * lift);
            poseStack.mulPose(Axis.XP.rotationDegrees(38.0f * lift));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0f * lift + Mth.sin(time * 0.18f) * 3.0f * lift));
        }

        // Release: swing forward and down
        if (flickCurve > 0.001f) {
            poseStack.translate(0.0f, -0.12f * flickCurve, -0.28f * flickCurve);
            poseStack.mulPose(Axis.XP.rotationDegrees(-55.0f * flickCurve));
        }

        // Vanilla attack-swing rotation
        float swingSq = Mth.sin(swingProcess * swingProcess * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0f + swingSq * -20.0f)));
        float swingRoot = Mth.sin(sqrtSwing * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * swingRoot * -20.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(swingRoot * -80.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0f));
    }

    // ------------------------------------------------------------------
    // THIRD PERSON
    // ------------------------------------------------------------------

    /**
     * Must run during client setup, before any humanoid model is rendered.
     * HumanoidModel caches the ArmPose values in a switch table the first time it poses an arm;
     * a pose created after that point is out of range and crashes the game.
     */
    public static void registerArmPose() {
        if (wandPose == null) {
            wandPose = HumanoidModel.ArmPose.create("MAGIC_AND_SORCERY_WAND", false, WandAnimation::applyThirdPerson);
            wandPoseSafe = switchTablesFit(wandPose.ordinal());
            if (!wandPoseSafe) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Magic and Sorcery: HumanoidModel arm poses were cached too early; using the vanilla staff pose");
            }
        }
    }

    private static HumanoidModel.ArmPose getWandPose() {
        // Never create the pose lazily here (see registerArmPose); fall back to the vanilla pose instead.
        return wandPose != null && wandPoseSafe ? wandPose : HumanoidModel.ArmPose.ITEM;
    }

    /**
     * Initializes HumanoidModel's enum switch tables now (so they include the new pose) and checks that
     * none of them was already built before it. A table built earlier is too short and would crash.
     */
    private static boolean switchTablesFit(int ordinal) {
        try {
            for (int i = 1; i <= 8; i++) {
                Class<?> table;
                try {
                    table = Class.forName(HumanoidModel.class.getName() + "$" + i, true, HumanoidModel.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    continue;
                }
                for (java.lang.reflect.Field field : table.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType() != int[].class) continue;
                    field.setAccessible(true);
                    int[] map = (int[]) field.get(null);
                    // Vanilla has 10 arm poses; smaller tables belong to other enums (e.g. HumanoidArm)
                    if (map != null && map.length >= 10 && map.length <= ordinal) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void applyThirdPerson(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Minecraft mc = Minecraft.getInstance();
        float partialTick = mc.getFrameTime();
        float time = entity.tickCount + partialTick;

        float lift;
        float flick = 0.0f;
        if (entity == mc.player) {
            lift = Mth.lerp(partialTick, prevRaise, raise);
            if (flickTicks > 0) {
                float f = Math.max(0.0f, (flickTicks - partialTick) / CAST_FLICK_TICKS);
                flick = Mth.sin((1.0f - f) * (float) Math.PI);
            }
        } else {
            // Other players: raise while they hold the use action
            lift = entity.isUsingItem() ? Math.min(1.0f, entity.getTicksUsingItem() / 20.0f) : 0.0f;
        }

        // Base "holding an item" pose
        armPart.xRot = armPart.xRot * 0.5f - (float) Math.PI / 10.0f;
        armPart.yRot = 0.0f;

        // Idle sway left-to-right
        float idle = 1.0f - lift;
        armPart.zRot += side * Mth.sin(time * 0.07f) * 0.10f * idle;
        armPart.yRot += Mth.sin(time * 0.07f) * 0.12f * idle;

        // Raised high above the head
        if (lift > 0.001f) {
            float raisedX = -2.85f + Mth.sin(time * 0.25f) * 0.05f;
            armPart.xRot = Mth.lerp(lift, armPart.xRot, raisedX);
            armPart.yRot = Mth.lerp(lift, armPart.yRot, side * -0.15f);
            armPart.zRot = Mth.lerp(lift, armPart.zRot, side * 0.12f);
        }

        // Release: swing forward and down
        if (flick > 0.001f) {
            armPart.xRot = Mth.lerp(flick, armPart.xRot, -1.35f);
        }
    }
}
