package com.cesar.magicandsorcery.network.packets;

import com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell;
import com.cesar.magicandsorcery.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketFallingSwordChannel {
    public static final byte ACTION_START = 0;
    public static final byte ACTION_CANCEL = 1;
    public static final byte ACTION_UPDATE = 2;

    private final byte action;
    private final double targetX;
    private final double targetY;
    private final double targetZ;

    public PacketFallingSwordChannel(byte action, Vec3 targetPos) {
        this.action = action;
        if (targetPos != null) {
            this.targetX = targetPos.x;
            this.targetY = targetPos.y;
            this.targetZ = targetPos.z;
        } else {
            this.targetX = 0;
            this.targetY = 0;
            this.targetZ = 0;
        }
    }

    public PacketFallingSwordChannel(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.targetX = buf.readDouble();
        this.targetY = buf.readDouble();
        this.targetZ = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            Vec3 target = new Vec3(targetX, targetY, targetZ);
            if (action == ACTION_START) {
                LaPollaCayendoSpell.setPlayerChannelTarget(sender.getId(), target);
                ModNetwork.sendToNearby(
                        new PacketFallingSwordChannelState(sender.getId(), ACTION_START, target),
                        sender.serverLevel(),
                        target,
                        64.0
                );
            } else if (action == ACTION_UPDATE) {
                LaPollaCayendoSpell.setPlayerChannelTarget(sender.getId(), target);
                ModNetwork.sendToNearby(
                        new PacketFallingSwordChannelState(sender.getId(), ACTION_UPDATE, target),
                        sender.serverLevel(),
                        target,
                        64.0
                );
            } else if (action == ACTION_CANCEL) {
                LaPollaCayendoSpell.clearPlayerChannelTarget(sender.getId());
                ModNetwork.sendToNearby(
                        new PacketFallingSwordChannelState(sender.getId(), ACTION_CANCEL, Vec3.ZERO),
                        sender.serverLevel(),
                        sender.position(),
                        64.0
                );
            }
        });
        return true;
    }
}
