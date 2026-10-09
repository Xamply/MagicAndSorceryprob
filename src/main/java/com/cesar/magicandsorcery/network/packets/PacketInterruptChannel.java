package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.client.render.ClientDivineSwordRenderer;
import com.cesar.magicandsorcery.client.render.ClientFallingSwordRenderer;
import com.cesar.magicandsorcery.client.render.ClientThundajaRenderer;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from server to a client when their active spell casting is interrupted by Disrupt.
 */
public class PacketInterruptChannel {
    private final ResourceLocation spellId;
    private final int interrupterId;

    public PacketInterruptChannel(ResourceLocation spellId, int interrupterId) {
        this.spellId = spellId;
        this.interrupterId = interrupterId;
    }

    public PacketInterruptChannel(FriendlyByteBuf buf) {
        this.spellId = buf.readBoolean() ? buf.readResourceLocation() : null;
        this.interrupterId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(spellId != null);
        if (spellId != null) {
            buf.writeResourceLocation(spellId);
        }
        buf.writeInt(interrupterId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;

                // Cancel client-side channeling immediately
                ClientMagicData.cancelChanneling();

                // Specific renderer cleanup
                int myId = mc.player.getId();
                ClientThundajaRenderer.cancelStorm(myId);
                ClientFallingSwordRenderer.cancelChannel(myId);
                ClientDivineSwordRenderer.cancelChannel(myId);

                // Audio feedback: rupture sound on player
                mc.player.playSound(SoundEvents.BEACON_DEACTIVATE, 1.2f, 1.6f);
                mc.player.playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 0.8f);

                // Notification message
                Spell spell = spellId != null ? ModSpells.getSpell(spellId) : null;
                Component spellName = spell != null ? spell.getName() : Component.literal("Hechizo");

                mc.player.displayClientMessage(
                        Component.translatable("message.magic_and_sorcery.disrupt.interrupted_victim", spellName)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        true
                );
            });
        });
        return true;
    }
}
