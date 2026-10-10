package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.entity.BobEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.UUID;

public class BobRenderer extends LivingEntityRenderer<BobEntity, PlayerModel<BobEntity>> {

    public BobRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);

        // Armor layer (supports all vanilla and modded helmets, chestplates, leggings, boots)
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));

        // Held items layer (staffs/wands, books, rings, swords, shields, etc.)
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(BobEntity entity) {
        UUID ownerUuid = entity.getOwnerUUID();
        if (ownerUuid != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(ownerUuid);
                if (info != null) {
                    return info.getSkinLocation();
                }
            }
            return DefaultPlayerSkin.getDefaultSkin(ownerUuid);
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    protected void setupRotations(BobEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);

        // Adjust arm pose if Bob is holding a wand/staff or channeling
        ItemStack mainItem = entity.getMainHandItem();
        if (!mainItem.isEmpty() && mainItem.getItem() instanceof com.cesar.magicandsorcery.magic.catalyst.ICatalyst) {
            this.model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
        } else {
            this.model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }
    }

    @Override
    protected void renderNameTag(BobEntity entity, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        double distSq = this.entityRenderDispatcher.distanceToSqr(entity);
        if (distSq > 4096.0D) {
            return;
        }

        poseStack.pushPose();
        // Offset above entity head (vanilla nameplate height)
        poseStack.translate(0.0D, entity.getBbHeight() + 0.5F, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Font font = this.getFont();
        float opacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int bgAlpha = (int) (opacity * 255.0F) << 24;

        // Semi-transparent black 50% opacity background (0x80000000)
        int blackFiftyPercent = 0x80000000;

        // --- 1. RENDER NAME TAG ("Bob") ---
        float nameX = -font.width(displayName) / 2.0F;
        font.drawInBatch(displayName, nameX, 0, 0xFFFFFFFF, false,
                poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, bgAlpha, packedLight);

        // --- BARS GEOMETRY ---
        // Width: 52 units, Height: 7 units each
        float barW = 52.0F;
        float barH = 7.0F;
        float halfW = barW / 2.0F;

        boolean isChanneling = entity.isChanneling();

        // --- 2. HEALTH BAR ---
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        float healthPct = Math.max(0.0F, Math.min(1.0F, health / Math.max(1.0F, maxHealth)));

        float hpTop = isChanneling ? -25.0F : -16.0F;
        float hpBottom = hpTop + barH;

        // Black 50% backdrop behind health bar indicator
        drawColoredQuad(poseStack, bufferSource, -halfW - 2, hpTop - 2, halfW + 2, hpBottom + 2, blackFiftyPercent);

        // Inner dark border & background (#252525)
        drawColoredQuad(poseStack, bufferSource, -halfW - 1, hpTop - 1, halfW + 1, hpBottom + 1, 0xFF181818);
        drawColoredQuad(poseStack, bufferSource, -halfW, hpTop, halfW, hpBottom, 0xFF252525);

        // Filled Red Bar (#D93636)
        float hpFillW = barW * healthPct;
        if (hpFillW > 0.0F) {
            drawColoredQuad(poseStack, bufferSource, -halfW, hpTop, -halfW + hpFillW, hpBottom, 0xFFD93636);
        }

        // Text: "current/max" (e.g. "20/20")
        String hpText = String.format(Locale.ROOT, "%d/%d", Math.round(health), Math.round(maxHealth));
        float hpTextX = -font.width(hpText) / 2.0F;
        float hpTextY = hpTop + (barH - 8.0F) / 2.0F + 1.0F;
        font.drawInBatch(hpText, hpTextX, hpTextY, 0xFFFFFFFF, false,
                poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);

        // --- 3. CHANNELING BAR (only when channeling) ---
        if (isChanneling) {
            float chTop = -14.0F;
            float chBottom = chTop + barH;

            float channelProgress = entity.getChannelProgress();
            float chFillW = barW * channelProgress;

            // Black 50% backdrop behind channeling bar indicator
            drawColoredQuad(poseStack, bufferSource, -halfW - 2, chTop - 2, halfW + 2, chBottom + 2, blackFiftyPercent);

            // Inner dark border & background (#252525)
            drawColoredQuad(poseStack, bufferSource, -halfW - 1, chTop - 1, halfW + 1, chBottom + 1, 0xFF181818);
            drawColoredQuad(poseStack, bufferSource, -halfW, chTop, halfW, chBottom, 0xFF252525);

            // Filled Gray Bar (#929292)
            if (chFillW > 0.0F) {
                drawColoredQuad(poseStack, bufferSource, -halfW, chTop, -halfW + chFillW, chBottom, 0xFF929292);
            }

            // Text: remaining seconds (e.g. "7.0 s")
            float remainingSeconds = entity.getRemainingChannelSeconds();
            String chText = String.format(Locale.ROOT, "%.1f s", remainingSeconds);
            float chTextX = -font.width(chText) / 2.0F;
            float chTextY = chTop + (barH - 8.0F) / 2.0F + 1.0F;
            font.drawInBatch(chText, chTextX, chTextY, 0xFFFFFFFF, false,
                poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
        }

        poseStack.popPose();
    }

    private static void drawColoredQuad(PoseStack poseStack, MultiBufferSource bufferSource,
                                        float minX, float minY, float maxX, float maxY, int color) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.gui());

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        vc.vertex(matrix, minX, maxY, 0.01F).color(r, g, b, a).endVertex();
        vc.vertex(matrix, maxX, maxY, 0.01F).color(r, g, b, a).endVertex();
        vc.vertex(matrix, maxX, minY, 0.01F).color(r, g, b, a).endVertex();
        vc.vertex(matrix, minX, minY, 0.01F).color(r, g, b, a).endVertex();
    }
}
