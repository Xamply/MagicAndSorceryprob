package com.cesar.magicandsorcery.item;

import com.cesar.magicandsorcery.client.render.ClaymoreFirstPersonRenderer;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Temporary summoned weapon for Cast: Claymore.
 * - Max 3 hits or 10 seconds lifespan (200 ticks).
 * - Disappears when exhausted or expired.
 */
public class ClaymoreItem extends Item {
    public static final String TAG_TICKS_LEFT = "ClaymoreTicksLeft";
    public static final String TAG_HITS_LEFT = "ClaymoreHitsLeft";
    public static final int MAX_DURATION_TICKS = 200; // 10 seconds

    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public ClaymoreItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC));

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 9.0, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.4, AttributeModifier.Operation.ADDITION));
        this.defaultModifiers = builder.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(slot);
    }

    public static ItemStack createSummonedClaymore() {
        ItemStack stack = new ItemStack(ModItems.CLAYMORE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_TICKS_LEFT, MAX_DURATION_TICKS);
        tag.putInt(TAG_HITS_LEFT, 3);
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
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        if (!ModList.get().isLoaded("bettercombat")) {
            consumer.accept(ClaymoreFirstPersonRenderer.ITEM_EXTENSIONS);
        }
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
