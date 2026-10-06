package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.hud.MagicRenderTypes;
import com.cesar.magicandsorcery.entity.IteratusMissileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class IteratusMissileRenderer extends EntityRenderer<IteratusMissileEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicAndSorcery.MODID, "textures/entity/iteratus_missile.png");

    public IteratusMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(IteratusMissileEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float time = entity.tickCount + partialTick;

        // Orient along motion vector + continuous magical spin
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw + 90.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getXRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 16.0f));

        VertexConsumer vc = bufferSource.getBuffer(MagicRenderTypes.WORLD_GLOW);
        Matrix4f mat = poseStack.last().pose();

        // Outer arcane violet aura
        drawCrossPlanes(vc, mat, 0.28f, 0.72f, 0.35f, 1.0f, 0.55f);
        // Inner luminous white-purple core
        drawCrossPlanes(vc, mat, 0.16f, 0.95f, 0.85f, 1.0f, 0.90f);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void drawCrossPlanes(VertexConsumer vc, Matrix4f mat, float radius, float r, float g, float b, float a) {
        // XY plane quad
        drawQuad(vc, mat, -radius, -radius, 0, radius, -radius, 0, radius, radius, 0, -radius, radius, 0, r, g, b, a);
        // YZ plane quad
        drawQuad(vc, mat, 0, -radius, -radius, 0, radius, -radius, 0, radius, radius, 0, -radius, radius, r, g, b, a);
        // XZ plane quad
        drawQuad(vc, mat, -radius, 0, -radius, radius, 0, -radius, radius, 0, radius, -radius, 0, radius, r, g, b, a);
    }

    private void drawQuad(VertexConsumer vc, Matrix4f mat,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          float x4, float y4, float z4,
                          float r, float g, float b, float a) {
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a).endVertex();
        vc.vertex(mat, x4, y4, z4).color(r, g, b, a).endVertex();

        // Back-face
        vc.vertex(mat, x4, y4, z4).color(r, g, b, a).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(IteratusMissileEntity entity) {
        return TEXTURE;
    }
}
