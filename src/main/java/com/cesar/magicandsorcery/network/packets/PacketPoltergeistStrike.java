package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.client.render.ClientPoltergeistRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPoltergeistStrike {
    private final int attackerId;
    private final int victimId;
    private final ItemStack weaponStack;

    public PacketPoltergeistStrike(int attackerId, int victimId, ItemStack weaponStack) {
        this.attackerId = attackerId;
        this.victimId = victimId;
        this.weaponStack = weaponStack.copy();
    }

    public PacketPoltergeistStrike(FriendlyByteBuf buf) {
        this.attackerId = buf.readVarInt();
        this.victimId = buf.readVarInt();
        this.weaponStack = buf.readItem();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(attackerId);
        buf.writeVarInt(victimId);
        buf.writeItem(weaponStack);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPoltergeistRenderer.handleStrike(attackerId, victimId, weaponStack)));
        return true;
    }
}
