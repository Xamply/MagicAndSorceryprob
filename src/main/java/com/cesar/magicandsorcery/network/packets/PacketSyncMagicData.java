package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PacketSyncMagicData {
    private final float mana;
    private final float maxMana;
    private final float manaRegen;
    private final int spellCapacity;
    private final int selectedSpellIndex;
    private final List<ResourceLocation> preparedSpells;
    private final List<ResourceLocation> learnedSpells;
    private final Map<ResourceLocation, Integer> cooldowns;

    public PacketSyncMagicData(PlayerMagicData data) {
        this.mana = data.getMana();
        this.maxMana = data.getMaxMana();
        this.manaRegen = data.getManaRegen();
        this.spellCapacity = data.getSpellCapacity();
        this.selectedSpellIndex = data.getSelectedSpellIndex();
        this.preparedSpells = new ArrayList<>(data.getPreparedSpells());
        this.learnedSpells = new ArrayList<>(data.getLearnedSpells());
        this.cooldowns = new HashMap<>(data.getCooldowns());
    }

    public PacketSyncMagicData(FriendlyByteBuf buf) {
        this.mana = buf.readFloat();
        this.maxMana = buf.readFloat();
        this.manaRegen = buf.readFloat();
        this.spellCapacity = buf.readInt();
        this.selectedSpellIndex = buf.readInt();

        int spellCount = buf.readInt();
        this.preparedSpells = new ArrayList<>(spellCount);
        for (int i = 0; i < spellCount; i++) {
            boolean hasSpell = buf.readBoolean();
            if (hasSpell) {
                this.preparedSpells.add(buf.readResourceLocation());
            } else {
                this.preparedSpells.add(null);
            }
        }

        int learnedCount = buf.readInt();
        this.learnedSpells = new ArrayList<>(learnedCount);
        for (int i = 0; i < learnedCount; i++) {
            this.learnedSpells.add(buf.readResourceLocation());
        }

        int cdCount = buf.readInt();
        this.cooldowns = new HashMap<>(cdCount);
        for (int i = 0; i < cdCount; i++) {
            this.cooldowns.put(buf.readResourceLocation(), buf.readInt());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(mana);
        buf.writeFloat(maxMana);
        buf.writeFloat(manaRegen);
        buf.writeInt(spellCapacity);
        buf.writeInt(selectedSpellIndex);

        buf.writeInt(preparedSpells.size());
        for (ResourceLocation id : preparedSpells) {
            boolean hasSpell = (id != null);
            buf.writeBoolean(hasSpell);
            if (hasSpell) {
                buf.writeResourceLocation(id);
            }
        }

        buf.writeInt(learnedSpells.size());
        for (ResourceLocation id : learnedSpells) {
            buf.writeResourceLocation(id);
        }

        buf.writeInt(cooldowns.size());
        for (Map.Entry<ResourceLocation, Integer> entry : cooldowns.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            // Client side handler
            ClientMagicData.setStats(mana, maxMana, manaRegen, spellCapacity, selectedSpellIndex, preparedSpells, learnedSpells, cooldowns);
        });
        return true;
    }
}
