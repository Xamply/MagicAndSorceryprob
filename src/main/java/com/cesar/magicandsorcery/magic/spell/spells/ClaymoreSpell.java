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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Cast: Claymore
 * Type: Offensive / Summon
 * School: Conjuration
 * Mana cost: 80 MP
 * Cooldown: 15s (300 ticks)
 * Lifespan: 12s (240 ticks)
 * Summons a magical Claymore sword with custom texture.
 * Behaves and animates identically to a standard sword in vanilla and Better Combat.
 */
public class ClaymoreSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "claymore");

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
        return false;
    }

    public static void onClaymoreAttack(ServerPlayer player) {
        // No-op: standard weapon attacks are handled by Minecraft / Better Combat natively
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

    public static void tickServer(net.minecraft.server.MinecraftServer server) {
        // No-op: inventoryTick handles clean expiration
    }
}
