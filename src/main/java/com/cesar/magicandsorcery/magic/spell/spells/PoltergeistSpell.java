package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.catalyst.ICatalyst;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketPoltergeistApplied;
import com.cesar.magicandsorcery.network.packets.PacketPoltergeistStrike;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchantment: Poltergeist
 * Temporarily enchants a damage-dealing weapon/tool on self or ally for 60 seconds.
 * Every valid attack that connects creates a ghost replica that strikes the same target,
 * duplicating the attack damage.
 */
public class PoltergeistSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "poltergeist");

    public static final int DURATION_TICKS = 1200; // 60 seconds (1200 ticks)

    public static class ActiveEnchantment {
        public final UUID targetEntityUuid;
        public final String originalItemRegistryName;
        public int remainingTicks;
        public final boolean onOffHand;

        public ActiveEnchantment(UUID targetEntityUuid, String originalItemRegistryName, int remainingTicks, boolean onOffHand) {
            this.targetEntityUuid = targetEntityUuid;
            this.originalItemRegistryName = originalItemRegistryName;
            this.remainingTicks = remainingTicks;
            this.onOffHand = onOffHand;
        }
    }

    // Active enchantments per target entity UUID
    private static final Map<UUID, ActiveEnchantment> ACTIVE_ENCHANTMENTS = new ConcurrentHashMap<>();

    // Re-entrancy guard to prevent infinite echo loops
    private static final ThreadLocal<Boolean> IS_POLTERGEIST_STRIKING = ThreadLocal.withInitial(() -> false);

    // Delayed strike queue for replica visuals & secondary damage
    public static class PendingEchoStrike {
        public final LivingEntity attacker;
        public final LivingEntity target;
        public final ItemStack weaponStack;
        public final float damage;
        public int delayTicks; // ticks until impact

        public PendingEchoStrike(LivingEntity attacker, LivingEntity target, ItemStack weaponStack, float damage, int delayTicks) {
            this.attacker = attacker;
            this.target = target;
            this.weaponStack = weaponStack.copy();
            this.damage = damage;
            this.delayTicks = delayTicks;
        }
    }

    private static final java.util.List<PendingEchoStrike> PENDING_STRIKES = new java.util.concurrent.CopyOnWriteArrayList<>();

    public PoltergeistSpell() {
        super(ID, SpellSchool.ENCHANTMENT, SpellType.UTILITY,
                50.0f, // 50 Mana
                20,    // 1.0 second cast time (20 ticks base)
                1300,  // 65.0 seconds cooldown (1300 ticks base)
                0.0f   // 0 Direct damage
        );
    }

    @Override
    public double getRange() {
        return 16.0;
    }

    @Override
    public boolean allowsSelfCast() {
        return true;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        return execute(player, level, method, null, false);
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method, boolean selfCast) {
        return execute(player, level, method, null, selfCast);
    }

    public boolean execute(ServerPlayer player, Level level, CastingMethod method, Vec3 clientTargetPos, boolean selfCast) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        LivingEntity target;
        if (selfCast) {
            target = player;
        } else {
            target = null;
            if (clientTargetPos != null) {
                target = findEntityNear(serverLevel, clientTargetPos, 2.5, player);
            }
            if (target == null) {
                target = findTarget(player, serverLevel, getRange());
            }
            if (target == null) {
                target = player;
            }
        }

        return applyPoltergeist(target, serverLevel, player);
    }

    /**
     * Apply poltergeist to the target entity's held weapon.
     */
    public static boolean applyPoltergeist(LivingEntity target, ServerLevel serverLevel, Player caster) {
        InteractionHand chosenHand = selectWeaponHand(target);
        if (chosenHand == null) {
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.magic_and_sorcery.poltergeist_no_weapon")
                                .withStyle(net.minecraft.ChatFormatting.RED),
                        true
                );
            }
            return false;
        }

        ItemStack weaponStack = target.getItemInHand(chosenHand);
        String regName = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(weaponStack.getItem()).toString();
        boolean isOffHand = (chosenHand == InteractionHand.OFF_HAND);

        ACTIVE_ENCHANTMENTS.put(target.getUUID(), new ActiveEnchantment(target.getUUID(), regName, DURATION_TICKS, isOffHand));

        // Network visual confirmation
        ModNetwork.sendToNearby(new PacketPoltergeistApplied(target.getId(), isOffHand),
                serverLevel, target.position(), 64.0);

        // Server sound & particle effects
        serverLevel.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.2f, 1.3f);
        serverLevel.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 1.4f);

        serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0, target.getZ(),
                20, 0.4, 0.5, 0.4, 0.04);
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + 1.0, target.getZ(),
                25, 0.5, 0.6, 0.5, 0.1);

        return true;
    }

    /**
     * Inspect hands of target to find which hand holds a valid weapon/tool.
     * Prioritizes the hand opposite to the catalyst, or mainhand if both or bare hands.
     */
    public static InteractionHand selectWeaponHand(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();

        boolean mainIsCatalyst = !main.isEmpty() && main.getItem() instanceof ICatalyst;
        boolean offIsCatalyst = !off.isEmpty() && off.getItem() instanceof ICatalyst;

        boolean mainIsWeapon = isValidWeapon(main);
        boolean offIsWeapon = isValidWeapon(off);

        // If catalyst in main hand and weapon in off hand:
        if (mainIsCatalyst && offIsWeapon) return InteractionHand.OFF_HAND;
        // If catalyst in off hand and weapon in main hand:
        if (offIsCatalyst && mainIsWeapon) return InteractionHand.MAIN_HAND;

        // Otherwise prioritize main hand if weapon, then offhand
        if (mainIsWeapon) return InteractionHand.MAIN_HAND;
        if (offIsWeapon) return InteractionHand.OFF_HAND;

        return null;
    }

    /**
     * Determines whether an item qualifies as a damage-dealing weapon or offensive tool.
     */
    public static boolean isValidWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof ICatalyst) return false;

        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof DiggerItem) {
            return true;
        }

        // Generic check: does it grant attack damage attribute modifier > 0?
        try {
            var modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
            for (var mod : modifiers) {
                if (mod.getAmount() > 0.0) return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Called on LivingDamageEvent. When an attacker with an active Poltergeist enchantment
     * successfully deals damage with their enchanted weapon, queue the replica strike.
     */
    public static void onEntityDamaged(LivingEntity victim, DamageSource source, float amount) {
        if (IS_POLTERGEIST_STRIKING.get()) return;
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;
        if (victim.isRemoved() || !victim.isAlive()) return;

        if (!(source.getEntity() instanceof LivingEntity attacker)) return;

        ActiveEnchantment ench = ACTIVE_ENCHANTMENTS.get(attacker.getUUID());
        if (ench == null || ench.remainingTicks <= 0) return;

        // Check if attacker currently holds the enchanted weapon
        ItemStack weapon = ench.onOffHand ? attacker.getOffhandItem() : attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        String regName = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(weapon.getItem()).toString();
        if (!regName.equals(ench.originalItemRegistryName)) return;

        // Valid attack that connected! Queue the echo strike (1 tick delay for visual synchronization)
        PENDING_STRIKES.add(new PendingEchoStrike(attacker, victim, weapon, amount, 2));

        // Send visual packet to clients so ghost weapon animation starts immediately
        ModNetwork.sendToNearby(new PacketPoltergeistStrike(attacker.getId(), victim.getId(), weapon),
                serverLevel, victim.position(), 64.0);
    }

    /**
     * Tick method called every server tick from PlayerMagicEvents.onServerTick
     */
    public static void tickServer(net.minecraft.server.MinecraftServer server) {
        // 1. Tick and prune active enchantments
        Iterator<Map.Entry<UUID, ActiveEnchantment>> it = ACTIVE_ENCHANTMENTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveEnchantment> entry = it.next();
            ActiveEnchantment ench = entry.getValue();
            ench.remainingTicks--;

            if (ench.remainingTicks <= 0) {
                it.remove();
                // Send expire particles if entity is online/loaded
                ServerLevel level = server.overworld();
                net.minecraft.world.entity.Entity e = level.getEntity(ench.targetEntityUuid);
                if (e != null) {
                    level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 1.0, e.getZ(),
                            10, 0.3, 0.4, 0.3, 0.02);
                }
            }
        }

        // 2. Process pending echo strikes
        if (!PENDING_STRIKES.isEmpty()) {
            for (PendingEchoStrike strike : PENDING_STRIKES) {
                strike.delayTicks--;
                if (strike.delayTicks <= 0) {
                    PENDING_STRIKES.remove(strike);
                    executeEchoStrike(strike);
                }
            }
        }
    }

    private static void executeEchoStrike(PendingEchoStrike strike) {
        LivingEntity attacker = strike.attacker;
        LivingEntity victim = strike.target;

        if (attacker == null || victim == null) return;
        if (!attacker.isAlive() || !victim.isAlive() || victim.isRemoved()) return;
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;

        try {
            IS_POLTERGEIST_STRIKING.set(true);

            // Bypass i-frames for the second spectral impact
            int prevInvul = victim.invulnerableTime;
            victim.invulnerableTime = 0;

            // Deal replicated damage attributed directly to the attacker
            DamageSource echoSource = attacker instanceof Player p
                    ? serverLevel.damageSources().playerAttack(p)
                    : serverLevel.damageSources().mobAttack(attacker);

            boolean damaged = victim.hurt(echoSource, strike.damage);
            if (!damaged) {
                victim.invulnerableTime = prevInvul;
            }

            // Impact audio & particles
            serverLevel.playSound(null, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.1f, 1.4f);
            serverLevel.playSound(null, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.7f, 1.6f);

            serverLevel.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    15, 0.4, 0.4, 0.4, 0.15);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    12, 0.3, 0.3, 0.3, 0.05);

        } finally {
            IS_POLTERGEIST_STRIKING.set(false);
        }
    }

    public static boolean hasActiveEnchantment(LivingEntity entity) {
        if (entity == null) return false;
        ActiveEnchantment ench = ACTIVE_ENCHANTMENTS.get(entity.getUUID());
        return ench != null && ench.remainingTicks > 0;
    }

    // --- TARGETING HELPERS (Consistent with Praesidium) ---

    public static LivingEntity findTarget(Player caster, Level level, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 reach = eye.add(look.scale(range));

        HitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double maxDistSq = (hit.getType() != HitResult.Type.MISS) ? hit.getLocation().distanceToSqr(eye) : range * range;

        AABB box = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);
        LivingEntity best = null;
        double bestDistSq = maxDistSq;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ent -> ent != caster && ent.isAlive())) {
            if (!isPotentialAllyTarget(caster, e)) continue;

            AABB bb = e.getBoundingBox().inflate(0.5);
            var opt = bb.clip(eye, reach);
            if (opt.isPresent()) {
                double d = eye.distanceToSqr(opt.get());
                if (d < bestDistSq) {
                    bestDistSq = d;
                    best = e;
                }
            }
        }
        return best;
    }

    public static LivingEntity findEntityNear(ServerLevel level, Vec3 pos, double radius, Player caster) {
        AABB box = new AABB(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ent -> ent != caster && ent.isAlive())) {
            if (!isPotentialAllyTarget(caster, e)) continue;
            double d = e.distanceToSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    public static boolean isPotentialAllyTarget(Player caster, LivingEntity e) {
        if (e == caster) return true;
        if (e instanceof Enemy || e instanceof net.minecraft.world.entity.monster.Monster) return false;
        if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == caster) return false;
        if (e instanceof Player otherPlayer && caster.getTeam() != null && !caster.isAlliedTo(otherPlayer)) return false;
        return true;
    }
}
