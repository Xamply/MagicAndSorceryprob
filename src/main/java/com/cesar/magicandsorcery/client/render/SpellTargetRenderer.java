package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SpellTargetRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        if (ShaderCompatHelper.isInvalidRenderPass()) {
            return;
        }

        if (!ClientMagicData.isChanneling()) {
            return;
        }

        Spell spell = ClientMagicData.getChannelingSpell();
        if (spell == null) {
            return;
        }

        // Area spells (like Thundaja) have their own ground indicators;
        // do not render the single-target bounding box next to the crosshair.
        if (spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.ID)
                || spell.getType() == com.cesar.magicandsorcery.magic.spell.SpellType.AREA) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        float partialTick = event.getPartialTick();
        CastingMethod method = ClientMagicData.getCurrentCastingMethod();
        double reach = spell.getRange(method);
        Vec3 eyePos = mc.player.getEyePosition(partialTick);
        Vec3 viewVec = mc.player.getViewVector(partialTick);
        Vec3 endPos = eyePos.add(viewVec.scale(reach));

        // 1. Check for block collisions
        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        Vec3 hitPos = endPos;
        AABB targetBox;

        if (blockHit.getType() != HitResult.Type.MISS) {
            hitPos = blockHit.getLocation();
        }

        // 2. Check for entity collisions up to block hit or reach
        AABB searchBox = mc.player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.player,
                eyePos,
                hitPos,
                searchBox,
                e -> !e.isSpectator() && e.isPickable(),
                eyePos.distanceToSqr(hitPos)
        );

        if (entityHit != null && entityHit.getEntity() != null) {
            // Target is an entity
            targetBox = entityHit.getEntity().getBoundingBox().inflate(0.1);
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            // Target is a block: snap to block grid
            targetBox = new AABB(blockHit.getBlockPos()).inflate(0.002);
        } else {
            // Max reach in air: 1x1x1 cube centered on max reach endpoint
            targetBox = new AABB(
                    endPos.x - 0.5, endPos.y - 0.5, endPos.z - 0.5,
                    endPos.x + 0.5, endPos.y + 0.5, endPos.z + 0.5
            );
        }

        // Colors based on spell school & readiness
        float r = 0.3f, g = 0.8f, b = 1.0f; // Default cyan
        switch (spell.getSchool()) {
            case LIGHTNING -> { r = 0.2f; g = 0.9f; b = 1.0f; } // Electric cyan
            case ICE -> { r = 0.4f; g = 0.7f; b = 1.0f; }       // Ice frost blue
            case TELEPORTATION -> { r = 0.9f; g = 0.3f; b = 1.0f; } // Ethereal purple
            default -> {}
        }

        boolean isReady = ClientMagicData.isReadyToCast();
        float alphaFill = isReady ? 0.45f : 0.25f;
        float alphaLine = isReady ? 1.0f : 0.75f;
        if (isReady) {
            // Pulsing / glowing ready highlight
            r = Math.min(1.0f, r + 0.2f);
            g = Math.min(1.0f, g + 0.2f);
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // 1. Draw translucent filled faces
        VertexConsumer filledConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack, filledConsumer,
                targetBox.minX, targetBox.minY, targetBox.minZ,
                targetBox.maxX, targetBox.maxY, targetBox.maxZ,
                r, g, b, alphaFill
        );
        bufferSource.endBatch(RenderType.debugFilledBox());

        // 2. Draw wireframe outline
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
                poseStack, lineConsumer,
                targetBox.minX, targetBox.minY, targetBox.minZ,
                targetBox.maxX, targetBox.maxY, targetBox.maxZ,
                r, g, b, alphaLine
        );
        bufferSource.endBatch(RenderType.lines());

        poseStack.popPose();
    }
}
