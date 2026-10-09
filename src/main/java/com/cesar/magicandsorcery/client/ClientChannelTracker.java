package com.cesar.magicandsorcery.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players in render distance are currently channeling spells on the client side.
 */
public final class ClientChannelTracker {
    private static final Map<Integer, ResourceLocation> ACTIVE_CHANNELS = new ConcurrentHashMap<>();

    private ClientChannelTracker() {
    }

    public static void setChanneling(int entityId, ResourceLocation spellId) {
        if (spellId != null) {
            ACTIVE_CHANNELS.put(entityId, spellId);
        } else {
            ACTIVE_CHANNELS.remove(entityId);
        }
    }

    public static void clearChanneling(int entityId) {
        ACTIVE_CHANNELS.remove(entityId);
    }

    public static boolean isPlayerChanneling(int entityId) {
        return ACTIVE_CHANNELS.containsKey(entityId);
    }

    public static ResourceLocation getChannelingSpell(int entityId) {
        return ACTIVE_CHANNELS.get(entityId);
    }

    public static void clearAll() {
        ACTIVE_CHANNELS.clear();
    }
}
