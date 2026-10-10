package com.cesar.magicandsorcery.client;

public class BobClientData {
    public static boolean hasActiveBob = false;
    public static int bobEntityId = -1;
    public static float health = 20.0f;
    public static float maxHealth = 20.0f;
    public static float mana = 100.0f;
    public static float maxMana = 100.0f;
    public static String currentSpell = "-";
    public static String stateKey = "idle";
    public static float channelProgress = 0.0f;
    public static float remainingCooldown = 0.0f;
    public static String target = "Frontal";
    public static long lastUpdateTime = 0L;

    public static void update(int entityId, float hp, float maxHp, float mp, float maxMp,
                              String spell, String state, float progress, float cooldown,
                              String trgt, boolean alive) {
        if (!alive) {
            hasActiveBob = false;
            bobEntityId = -1;
            return;
        }
        hasActiveBob = true;
        bobEntityId = entityId;
        health = hp;
        maxHealth = maxHp;
        mana = mp;
        maxMana = maxMp;
        currentSpell = spell;
        stateKey = state;
        channelProgress = progress;
        remainingCooldown = cooldown;
        target = trgt;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static void clear() {
        hasActiveBob = false;
        bobEntityId = -1;
    }

    public static boolean isVisible() {
        // Disappear if dead or if no update received in 8 seconds
        if (!hasActiveBob) return false;
        return (System.currentTimeMillis() - lastUpdateTime) < 8000L;
    }
}
