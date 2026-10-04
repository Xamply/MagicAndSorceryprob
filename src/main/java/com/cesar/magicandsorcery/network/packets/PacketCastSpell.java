package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.capability.PlayerMagicData;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.catalyst.ICatalyst;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketCastSpell {
    public PacketCastSpell() {
    }

    public PacketCastSpell(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            player.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(magicData -> {
                Spell spell = magicData.getSelectedSpell();
                if (spell == null) {
                    player.sendSystemMessage(Component.translatable("message.magic_and_sorcery.no_spell_selected").withStyle(ChatFormatting.RED), true);
                    return;
                }

                // Determine casting method from held items
                CastingMethod method = CastingMethod.BARE_HANDS;
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof ICatalyst catalyst) {
                    method = catalyst.getCastingMethod(mainHand);
                } else {
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof ICatalyst catalyst) {
                        method = catalyst.getCastingMethod(offHand);
                    }
                }

                boolean isCreative = player.isCreative();
                boolean bypassCooldown = isCreative && com.cesar.magicandsorcery.config.ModConfigs.NO_COOLDOWN_IN_CREATIVE.get();
                boolean bypassMana = isCreative && com.cesar.magicandsorcery.config.ModConfigs.INFINITE_MANA_IN_CREATIVE.get();

                // Check Cooldown
                if (!bypassCooldown && magicData.isSpellOnCooldown(spell.getId())) {
                    int cdTicks = magicData.getSpellCooldown(spell.getId());
                    float cdSeconds = cdTicks / 20.0f;
                    player.sendSystemMessage(Component.translatable("message.magic_and_sorcery.on_cooldown", String.format("%.1f", cdSeconds)).withStyle(ChatFormatting.RED), true);
                    return;
                }

                // Check Mana
                float finalCost = bypassMana ? 0.0f : spell.calculateFinalManaCost(method);
                if (!bypassMana && !magicData.canConsumeMana(finalCost)) {
                    player.sendSystemMessage(Component.translatable("message.magic_and_sorcery.not_enough_mana").withStyle(ChatFormatting.DARK_AQUA), true);
                    return;
                }

                // Execute spell
                boolean success = spell.execute(player, player.level(), method);
                if (success) {
                    if (!bypassMana) {
                        magicData.consumeMana(finalCost);
                    }
                    if (!bypassCooldown) {
                        int finalCooldown = spell.calculateFinalCooldown(method);
                        magicData.setSpellCooldown(spell.getId(), finalCooldown);
                    }

                    // Sync updated stats to client immediately
                    ModNetwork.sendToPlayer(new PacketSyncMagicData(magicData), player);
                }
            });
        });
        return true;
    }
}
