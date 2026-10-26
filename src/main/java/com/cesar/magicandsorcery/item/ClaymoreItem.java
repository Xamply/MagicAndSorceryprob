package com.cesar.magicandsorcery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Temporary summoned weapon for Cast: Claymore.
 * Uses the Divine Sword model with majestic enlarged greatsword scale and holy glint.
 * Executes the 3-hit combo (slash right, slash left, stab) and cleanly dissolves upon finishing the stab.
 */
public class ClaymoreItem extends SwordItem {
    public static final String TAG_EXPIRE_AT = "ClaymoreExpireAt";
    public static final int DURATION_TICKS = 240; // 12 seconds backup lifespan

    public ClaymoreItem() {
        // Standard Netherite Sword stats: +3 damage bonus (8 total damage), -2.4F attack speed modifier (1.6 attack speed)
        super(Tiers.NETHERITE, 3, -2.4F, new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC));
    }

    public static ItemStack createSummonedClaymore(long currentLevelTime) {
        ItemStack stack = new ItemStack(ModItems.CLAYMORE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(TAG_EXPIRE_AT, currentLevelTime + DURATION_TICKS);
        return stack;
    }

    public static ItemStack createSummonedClaymore() {
        return new ItemStack(ModItems.CLAYMORE.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchanted divine glint matching Divine Sword
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide() && entity instanceof Player player) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains(TAG_EXPIRE_AT)) {
                long expireAt = tag.getLong(TAG_EXPIRE_AT);
                if (level.getGameTime() >= expireAt) {
                    player.getInventory().setItem(slotId, ItemStack.EMPTY);
                    if (level instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                                25, 0.4, 0.5, 0.4, 0.15);
                        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                            20, 0.4, 0.5, 0.4, 0.1);
                        sl.playSound(null, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.6f);
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.claymore_desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
