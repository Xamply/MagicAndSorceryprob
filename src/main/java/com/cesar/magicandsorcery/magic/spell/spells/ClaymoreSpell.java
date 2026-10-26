package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.item.ClaymoreItem;
import com.cesar.magicandsorcery.item.ModItems;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cast: Claymore
 * Type: Offensive / Summon
 * School: Conjuration
 * Mana cost: 80 MP
 * Cooldown: 15s (300 ticks)
 * Max Duration: 12s (240 ticks) or 3 hits.
 * Summons the Divine Sword greatsword.
 * Executes the 3-hit combo (right slash, left slash, stab).
 * On the 3rd hit (the stab / estocada), when the stab animation completes,
 * the sword dissolves with magical particles.
 */
public class ClaymoreSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "claymore");

    public static class ComboState {
        public int hitCount = 0;
        public long lastAttackTime = -1;
        public int dissolveDelay = 0;
    }

    private static final Map<UUID, ComboState> COMBO_STATES = new ConcurrentHashMap<>();

    public ClaymoreSpell() {
        super(ID, SpellSchool.CONJURATION, SpellType.SINGLE_TARGET,
                80.0f, // 80 Mana
                0,     // Instant summon
                300,   // 15 seconds cooldown (300 ticks)
                35.0f  // Potential damage
        );
    }

    @Override
    public double getRange() {
        return 5.0;
    }

    public static boolean isFinisherPlaying(UUID playerId) {
        ComboState state = COMBO_STATES.get(playerId);
        return state != null && state.dissolveDelay > 0;
    }

    public static void onClaymoreAttack(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof ClaymoreItem)) return;

        long gameTime = player.serverLevel().getGameTime();
        ComboState state = COMBO_STATES.computeIfAbsent(player.getUUID(), k -> new ComboState());

        if (state.dissolveDelay > 0) return; // Finisher already scheduled / executing

        // Reset combo count if player stopped attacking for more than 40 ticks (2.0s)
        if (state.lastAttackTime != -1 && (gameTime - state.lastAttackTime) > 40) {
            state.hitCount = 0;
        }

        // Debounce: attacks cannot happen closer than 10 ticks apart (1.6 attack speed = 12.5 ticks cooldown)
        if (state.lastAttackTime != -1 && (gameTime - state.lastAttackTime) < 10) {
            return;
        }

        state.lastAttackTime = gameTime;
        state.hitCount++;

        if (state.hitCount >= 3) {
            // 3rd hit (the stab / estocada) is executing!
            // Allow the ~14-tick stab animation to complete in full, then dissolve.
            state.dissolveDelay = 14;
        }
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        // 1. Check if player has at least 1 free slot in their hotbar (slots 0-8)
        int freeHotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                freeHotbarSlot = i;
                break;
            }
        }

        if (freeHotbarSlot == -1) {
            player.sendSystemMessage(
                    Component.translatable("message.magic_and_sorcery.claymore_no_slot")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return false;
        }

        // 2. Summon Claymore item into the free hotbar slot
        ItemStack claymore = ClaymoreItem.createSummonedClaymore(level.getGameTime());
        player.getInventory().setItem(freeHotbarSlot, claymore);
        player.getInventory().selected = freeHotbarSlot; // Automatically equip

        // Reset combo tracking for this player
        COMBO_STATES.put(player.getUUID(), new ComboState());

        // Visuals & Sound for summoning
        serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.4f, 0.9f);
        serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 1.6f);

        serverLevel.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
                25, 0.5, 0.6, 0.5, 0.15);
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.4, 0.5, 0.4, 0.1);

        return true;
    }

    public static void removeClaymore(ServerPlayer player) {
        boolean found = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(ModItems.CLAYMORE.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                found = true;
            }
        }
        if (found) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
                    25, 0.4, 0.5, 0.4, 0.15);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.4, 0.5, 0.4, 0.1);
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.9f, 1.6f);
        }
    }

    public static void tickServer(MinecraftServer server) {
        if (COMBO_STATES.isEmpty()) return;

        Iterator<Map.Entry<UUID, ComboState>> it = COMBO_STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ComboState> entry = it.next();
            UUID playerId = entry.getKey();
            ComboState state = entry.getValue();

            if (state.dissolveDelay > 0) {
                state.dissolveDelay--;
                if (state.dissolveDelay == 0) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && player.isAlive()) {
                        removeClaymore(player);
                    }
                    it.remove();
                    continue;
                }
            }

            // Cleanup idle states after 120 ticks
            long currentTime = server.overworld().getGameTime();
            if (state.lastAttackTime != -1 && (currentTime - state.lastAttackTime) > 120 && state.dissolveDelay == 0) {
                it.remove();
            }
        }
    }
}
