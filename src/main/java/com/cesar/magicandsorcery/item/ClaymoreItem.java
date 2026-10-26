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
 * Behaves like a normal sword with a slight delay between swings (1.2 attack speed).
 * - Max 3 hits or 10 seconds lifespan (200 ticks).
 * - Disappears when exhausted or expired.
 */
public class ClaymoreItem extends SwordItem {
    public static final String TAG_TICKS_LEFT = "ClaymoreTicksLeft";
    public static final String TAG_HITS_LEFT = "ClaymoreHitsLeft";
    public static final int MAX_DURATION_TICKS = 200; // 10 seconds

    public ClaymoreItem() {
        // Tiers.NETHERITE has +4 attack damage. 4 + 5 = 9 bonus (+ 1 base player = 10 total damage).
        // Attack speed modifier of -2.8f gives 4.0 - 2.8 = 1.2 attack speed (slight delay between swings).
        super(Tiers.NETHERITE, 5, -2.8f, new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC));
    }

    public static ItemStack createSummonedClaymore() {
        ItemStack stack = new ItemStack(ModItems.CLAYMORE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_TICKS_LEFT, MAX_DURATION_TICKS);
        tag.putInt(TAG_HITS_LEFT, 3);
        tag.putString("weapon_attributes", "{\"parent\":\"bettercombat:sword\"}");
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide() && entity instanceof Player player) {
            CompoundTag tag = stack.getOrCreateTag();
            int ticksLeft = tag.getInt(TAG_TICKS_LEFT);
            int hitsLeft = tag.getInt(TAG_HITS_LEFT);

            ticksLeft--;
            tag.putInt(TAG_TICKS_LEFT, ticksLeft);

            if (ticksLeft <= 0 || (hitsLeft <= 0 && !com.cesar.magicandsorcery.magic.spell.spells.ClaymoreSpell.isFinisherPlaying(player.getUUID()))) {
                // Time up or hits exhausted: dissolve Claymore with magic particles
                player.getInventory().setItem(slotId, ItemStack.EMPTY);
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                            20, 0.4, 0.5, 0.4, 0.15);
                    sl.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                            15, 0.4, 0.5, 0.4, 0.1);
                    sl.playSound(null, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.6f);
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
        CompoundTag tag = stack.getTag();
        int ticksLeft = tag != null ? tag.getInt(TAG_TICKS_LEFT) : MAX_DURATION_TICKS;
        int hitsLeft = tag != null ? tag.getInt(TAG_HITS_LEFT) : 3;
        float secLeft = Math.max(0.0f, ticksLeft / 20.0f);

        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.claymore_desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.claymore_hits", hitsLeft).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.claymore_time", String.format(java.util.Locale.ROOT, "%.1f", secLeft)).withStyle(ChatFormatting.AQUA));
    }
}
