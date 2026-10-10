package com.cesar.magicandsorcery.command;

import com.cesar.magicandsorcery.entity.BobEntity;
import com.cesar.magicandsorcery.entity.BobManager;
import com.cesar.magicandsorcery.entity.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class SpawnBobCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register /spawn Bob and /spawn bob
        dispatcher.register(
                Commands.literal("spawn")
                        .then(Commands.literal("Bob")
                                .executes(SpawnBobCommand::execute))
                        .then(Commands.literal("bob")
                                .executes(SpawnBobCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cEste comando solo puede ser ejecutado por un jugador."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();

        // Check solid collision: if player is on ground, spawn Bob right there or nearest non-solid block
        double spawnX = playerPos.x;
        double spawnY = playerPos.y;
        double spawnZ = playerPos.z;

        BlockPos blockPos = player.blockPosition();
        if (level.getBlockState(blockPos).isSolid()) {
            spawnY = Math.ceil(spawnY);
        }

        // If player already has an active Bob, remove previous one first
        BobEntity oldBob = BobManager.getBobForPlayer(player);
        if (oldBob != null && oldBob.isAlive()) {
            oldBob.discard();
        }

        BobEntity bob = new BobEntity(ModEntities.BOB.get(), level);
        bob.setPos(spawnX, spawnY, spawnZ);
        bob.setYRot(player.getYRot());
        bob.setXRot(0.0f);
        bob.yHeadRot = player.getYRot();
        bob.yBodyRot = player.getYRot();
        bob.setOwner(player);

        level.addFreshEntity(bob);
        BobManager.registerBob(player.getUUID(), bob);

        player.sendSystemMessage(
                Component.literal("§a[Bob] ¡Bob ha sido invocado exitosamente! Usa el chat para ordenarle hechizos."),
                false
        );

        return 1;
    }
}
