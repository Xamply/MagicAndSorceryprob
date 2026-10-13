package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.item.ClaymoreItem;
import com.cesar.magicandsorcery.item.ModItems;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellImpacts;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketClaymoreSwingVisual;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cast: Claymore
 * Type: Offensive / Summon
 * School: Conjuration
 * Mana cost: 80 MP
 * Cooldown: 15s (300 ticks)
 * Max Duration: 10s (200 ticks)
 * Max Hits: 3
 * Combo sequence:
 * - Swing 1: 10 base damage, wide sweep right-to-left
 * - Swing 2: 10 base damage, wide sweep left-to-right
 * - Swing 3: 15 base damage, deep forward thrust
 */
public class ClaymoreSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "claymore");

    public static final int COMBO_WINDOW_TICKS = 40; // 2.0s window to continue combo before reset to stage 1

    public static class ComboState {
        public int currentStage = 0; // 0 = ready for stage 1, 1 = completed stage 1, 2 = completed stage 2
        public int ticksSinceLastSwing = 0;
        public int recoveryTicks = 0; // brief delay between attacks to enforce heavy feel
    }

    private static final Map<UUID, ComboState> COMBO_STATES = new ConcurrentHashMap<>();

    public ClaymoreSpell() {
        super(ID, SpellSchool.CONJURATION, SpellType.SINGLE_TARGET,
                80.0f, // 80 Mana
                0,     // Instant summon
                300,   // 15 seconds cooldown (300 ticks)
                35.0f  // Total potential damage (10 + 10 + 15)
        );
    }

    @Override
    public double getRange() {
        return 5.0;
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
        ItemStack claymore = ClaymoreItem.createSummonedClaymore();
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

    /**
     * Executes one swing of the Claymore combo sequence.
     */
    public static void handlePlayerSwing(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof ClaymoreItem)) return;

        CompoundTag tag = held.getOrCreateTag();
        int hitsLeft = tag.getInt(ClaymoreItem.TAG_HITS_LEFT);
        if (hitsLeft <= 0) return;

        ComboState state = COMBO_STATES.computeIfAbsent(player.getUUID(), k -> new ComboState());

        // Enforce recovery / attack windup
        if (state.recoveryTicks > 0) return;

        // Determine which swing stage to execute
        int nextStage;
        if (state.currentStage == 1 && state.ticksSinceLastSwing <= COMBO_WINDOW_TICKS) {
            nextStage = 2; // Left to right
        } else if (state.currentStage == 2 && state.ticksSinceLastSwing <= COMBO_WINDOW_TICKS) {
            nextStage = 3; // Thrust
        } else {
            nextStage = 1; // Reset to right-to-left
        }

        // Deduct 1 hit use
        hitsLeft--;
        tag.putInt(ClaymoreItem.TAG_HITS_LEFT, hitsLeft);

        // Update combo state
        state.currentStage = nextStage;
        state.ticksSinceLastSwing = 0;
        int animDuration = (nextStage == 3) ? 14 : 10;
        state.recoveryTicks = animDuration; // can't spam faster than the animation

        ServerLevel level = player.serverLevel();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        Vec3 playerPos = player.position();

        // Broadcast animation packet to nearby clients
        ModNetwork.sendToNearby(new PacketClaymoreSwingVisual(player.getId(), nextStage, yaw, pitch, playerPos, animDuration),
                level, playerPos, 64.0);

        // Compute damage and hit detection according to stage
        if (nextStage == 1) {
            // Swing 1: 10 base damage, wide sweep right to left
            executeSweepHitbox(player, level, 10.0f, true);
        } else if (nextStage == 2) {
            // Swing 2: 10 base damage, wide sweep left to right
            executeSweepHitbox(player, level, 10.0f, false);
        } else {
            // Swing 3: 15 base damage, concentrated forward thrust
            executeThrustHitbox(player, level, 15.0f);
        }

        // If hits reached 0, dissolve sword immediately
        if (hitsLeft <= 0) {
            player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
            COMBO_STATES.remove(player.getUUID());
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.4, 0.5, 0.4, 0.15);
        }
    }

    private static void executeSweepHitbox(ServerPlayer player, ServerLevel level, float damage, boolean rightToLeft) {
        float yaw = player.getYRot();
        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();

        AABB box = player.getBoundingBox().inflate(5.0, 2.0, 5.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator());

        for (LivingEntity target : targets) {
            Vec3 diff = target.position().subtract(player.position());
            double distFwd = diff.dot(forward);
            double distSide = Math.abs(diff.dot(side));
            double distY = target.getY() - player.getY();

            // Broad 4.5m forward, 3.2m wide sweep
            if (distFwd >= 0.2 && distFwd <= 4.5 && distSide <= 3.2 && distY >= -1.0 && distY <= 2.5) {
                target.hurt(player.damageSources().playerAttack(player), damage);
                // Heavy pushback
                SpellImpacts.push(target, new Vec3(forward.x * 0.9, 0.35, forward.z * 0.9));

                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        10, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    private static void executeThrustHitbox(ServerPlayer player, ServerLevel level, float damage) {
        float yaw = player.getYRot();
        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();

        AABB box = player.getBoundingBox().inflate(6.0, 2.0, 6.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator());

        for (LivingEntity target : targets) {
            Vec3 diff = target.position().subtract(player.position());
            double distFwd = diff.dot(forward);
            double distSide = Math.abs(diff.dot(side));
            double distY = target.getY() - player.getY();

            // Narrow concentrated 5.2m depth, 1.4m width forward line
            if (distFwd >= 0.5 && distFwd <= 5.2 && distSide <= 1.4 && distY >= -0.8 && distY <= 2.2) {
                target.hurt(player.damageSources().playerAttack(player), damage);
                // Piercing knockback directly backwards
                SpellImpacts.push(target, new Vec3(forward.x * 1.3, 0.45, forward.z * 1.3));

                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        18, 0.3, 0.3, 0.3, 0.2);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        14, 0.3, 0.3, 0.3, 0.15);
            }
        }
    }

    public static void tickServer(net.minecraft.server.MinecraftServer server) {
        Iterator<Map.Entry<UUID, ComboState>> it = COMBO_STATES.entrySet().iterator();
        while (it.hasNext()) {
            ComboState state = it.next().getValue();
            state.ticksSinceLastSwing++;
            if (state.recoveryTicks > 0) {
                state.recoveryTicks--;
            }
            if (state.ticksSinceLastSwing > COMBO_WINDOW_TICKS && state.currentStage != 0) {
                // Combo window expired: reset to stage 0 so next attack starts fresh at swing 1
                state.currentStage = 0;
            }
        }
    }
}
