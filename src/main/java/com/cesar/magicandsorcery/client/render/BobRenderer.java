package com.cesar.magicandsorcery.client.render;

import com.cesar.magicandsorcery.entity.BobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class BobRenderer extends LivingEntityRenderer<BobEntity, PlayerModel<BobEntity>> {

    public BobRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
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
}
