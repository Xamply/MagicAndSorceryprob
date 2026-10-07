package com.cesar.magicandsorcery.item;

import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.catalyst.ICatalyst;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MagicWandItem extends Item implements ICatalyst {
    public MagicWandItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public CastingMethod getCastingMethod(ItemStack stack) {
        return CastingMethod.WAND;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        // Custom staff animation (see WandAnimation) replaces the bow pose
        return net.minecraft.world.item.UseAnim.NONE;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.cesar.magicandsorcery.client.render.WandAnimation.ITEM_EXTENSIONS);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        return InteractionResultHolder.consume(itemstack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.catalyst_type", Component.translatable("catalyst.magic_and_sorcery.wand")).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.wand_desc").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.damage_mod").getString() + ": 1.60x").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.cast_speed_mod").getString() + ": -30%").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.cooldown_mod").getString() + ": Normal").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.mana_cost_mod").getString() + ": Normal").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, level, tooltip, isAdvanced);
    }
}
