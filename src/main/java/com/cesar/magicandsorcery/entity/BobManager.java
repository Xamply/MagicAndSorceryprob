package com.cesar.magicandsorcery.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BobManager {
    // Maps supervising player UUID -> active Bob entity ID
    private static final Map<UUID, Integer> ACTIVE_BOBS = new ConcurrentHashMap<>();

    public static void registerBob(UUID ownerUuid, BobEntity bob) {
        if (ownerUuid != null && bob != null) {
            ACTIVE_BOBS.put(ownerUuid, bob.getId());
        }
    }

    public static void unregisterBob(UUID ownerUuid) {
        if (ownerUuid != null) {
            ACTIVE_BOBS.remove(ownerUuid);
        }
    }

    public static BobEntity getBobForPlayer(ServerPlayer player) {
        if (player == null) return null;
        Integer entityId = ACTIVE_BOBS.get(player.getUUID());
        if (entityId == null) return null;

        if (player.level() instanceof ServerLevel sl) {
            Entity entity = sl.getEntity(entityId);
            if (entity instanceof BobEntity bob && bob.isAlive()) {
                return bob;
            }
        }
        ACTIVE_BOBS.remove(player.getUUID());
        return null;
    }
}
