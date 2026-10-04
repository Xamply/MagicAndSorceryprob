package com.cesar.magicandsorcery.item;

import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.catalyst.ICatalyst;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpellBookItem extends Item implements ICatalyst {
    public SpellBookItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public CastingMethod getCastingMethod(ItemStack stack) {
        return CastingMethod.BOOK;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return net.minecraft.world.InteractionResultHolder.consume(itemstack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.catalyst_type", Component.translatable("catalyst.magic_and_sorcery.book")).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.magic_and_sorcery.book_desc").withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.damage_mod").getString() + ": 1.30x").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.cast_speed_mod").getString() + ": Normal").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.cooldown_mod").getString() + ": -5%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("▶ " + Component.translatable("tooltip.magic_and_sorcery.mana_cost_mod").getString() + ": Normal").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, level, tooltip, isAdvanced);
    }
}
