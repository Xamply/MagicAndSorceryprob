package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.BobClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncBobData {
    private final int bobEntityId;
    private final float health;
    private final float maxHealth;
    private final float mana;
    private final float maxMana;
    private final String currentSpell;
    private final String stateKey;
    private final float channelProgress;
    private final float remainingCooldown;
    private final String target;
    private final boolean alive;

    public PacketSyncBobData(int bobEntityId, float health, float maxHealth, float mana, float maxMana,
                             String currentSpell, String stateKey, float channelProgress,
                             float remainingCooldown, String target, boolean alive) {
        this.bobEntityId = bobEntityId;
        this.health = health;
        this.maxHealth = maxHealth;
        this.mana = mana;
        this.maxMana = maxMana;
        this.currentSpell = currentSpell != null ? currentSpell : "-";
        this.stateKey = stateKey != null ? stateKey : "idle";
        this.channelProgress = channelProgress;
        this.remainingCooldown = remainingCooldown;
        this.target = target != null ? target : "Frontal";
        this.alive = alive;
    }

    public PacketSyncBobData(FriendlyByteBuf buf) {
        this.bobEntityId = buf.readInt();
        this.health = buf.readFloat();
        this.maxHealth = buf.readFloat();
        this.mana = buf.readFloat();
        this.maxMana = buf.readFloat();
        this.currentSpell = buf.readUtf(128);
        this.stateKey = buf.readUtf(64);
        this.channelProgress = buf.readFloat();
        this.remainingCooldown = buf.readFloat();
        this.target = buf.readUtf(128);
        this.alive = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(bobEntityId);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeFloat(mana);
        buf.writeFloat(maxMana);
        buf.writeUtf(currentSpell, 128);
        buf.writeUtf(stateKey, 64);
        buf.writeFloat(channelProgress);
        buf.writeFloat(remainingCooldown);
        buf.writeUtf(target, 128);
        buf.writeBoolean(alive);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            BobClientData.update(bobEntityId, health, maxHealth, mana, maxMana,
                    currentSpell, stateKey, channelProgress, remainingCooldown, target, alive);
        });
        ctx.get().setPacketHandled(true);
    }
}
