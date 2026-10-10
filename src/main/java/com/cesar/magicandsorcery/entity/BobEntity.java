package com.cesar.magicandsorcery.entity;

import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.spells.DenySpell;
import com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell;
import com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel;
import com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannelState;
import com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel;
import com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannelState;
import com.cesar.magicandsorcery.network.packets.PacketPlayerChannelState;
import com.cesar.magicandsorcery.network.packets.PacketRedshaChannel;
import com.cesar.magicandsorcery.network.packets.PacketRedshaChannelState;
import com.cesar.magicandsorcery.network.packets.PacketThundajaChannel;
import com.cesar.magicandsorcery.network.packets.PacketThundajaStormState;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BobEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_OWNER_UUID =
            SynchedEntityData.defineId(BobEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(BobEntity.class, EntityDataSerializers.STRING);

    // Channeling synchronization for all players
    private static final EntityDataAccessor<Boolean> DATA_CHANNELING =
            SynchedEntityData.defineId(BobEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CHANNEL_TICKS =
            SynchedEntityData.defineId(BobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TOTAL_CHANNEL_TICKS =
            SynchedEntityData.defineId(BobEntity.class, EntityDataSerializers.INT);

    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();

    // Order & Channeling state
    private Spell pendingSpell = null;
    private UUID pendingTargetUuid = null;

    private Spell channelingSpell = null;
    private UUID channelingTargetUuid = null;
    private int channelingTicks = 0;
    private int totalChannelTicks = 0;

    // Iteratus continuous streaming state
    private boolean iteratusFiring = false;
    private int iteratusTimer = 0;

    // FakePlayer delegate for spell execution
    private FakePlayer fakePlayer = null;

    public BobEntity(EntityType<? extends BobEntity> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal("Bob"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER_UUID, "");
        this.entityData.define(DATA_OWNER_NAME, "");
        this.entityData.define(DATA_CHANNELING, false);
        this.entityData.define(DATA_CHANNEL_TICKS, 0);
        this.entityData.define(DATA_TOTAL_CHANNEL_TICKS, 0);
    }

    public void setOwner(Player player) {
        if (player != null) {
            this.entityData.set(DATA_OWNER_UUID, player.getUUID().toString());
            this.entityData.set(DATA_OWNER_NAME, player.getGameProfile().getName());
        }
    }

    public UUID getOwnerUUID() {
        String uuidStr = this.entityData.get(DATA_OWNER_UUID);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getOwnerName() {
        return this.entityData.get(DATA_OWNER_NAME);
    }

    public boolean isSpellOnCooldown(ResourceLocation id) {
        return cooldowns.getOrDefault(id, 0) > 0;
    }

    public int getSpellCooldown(ResourceLocation id) {
        return cooldowns.getOrDefault(id, 0);
    }

    public void setCooldown(ResourceLocation id, int ticks) {
        if (ticks > 0) {
            cooldowns.put(id, ticks);
        } else {
            cooldowns.remove(id);
        }
    }

    public boolean isChanneling() {
        if (this.level().isClientSide()) {
            return this.entityData.get(DATA_CHANNELING);
        }
        return channelingSpell != null;
    }

    public float getChannelProgress() {
        int total = this.level().isClientSide() ? this.entityData.get(DATA_TOTAL_CHANNEL_TICKS) : totalChannelTicks;
        int current = this.level().isClientSide() ? this.entityData.get(DATA_CHANNEL_TICKS) : channelingTicks;
        if (total <= 0) return 0.0f;
        return Mth.clamp((float) current / (float) total, 0.0f, 1.0f);
    }

    public float getRemainingChannelSeconds() {
        int total = this.level().isClientSide() ? this.entityData.get(DATA_TOTAL_CHANNEL_TICKS) : totalChannelTicks;
        int current = this.level().isClientSide() ? this.entityData.get(DATA_CHANNEL_TICKS) : channelingTicks;
        int remainingTicks = Math.max(0, total - current);
        return remainingTicks / 20.0f;
    }

    public Spell getChannelingSpell() {
        return channelingSpell;
    }

    public ResourceLocation getChannelingSpellId() {
        return channelingSpell != null ? channelingSpell.getId() : null;
    }

    public void receiveOrder(Spell spell, ServerPlayer targetPlayer) {
        if (spell == null) return;
        this.pendingSpell = spell;
        this.pendingTargetUuid = targetPlayer != null ? targetPlayer.getUUID() : null;

        if (this.level() instanceof ServerLevel sl) {
            ServerPlayer owner = getOwnerPlayer(sl);
            if (owner != null) {
                String targetDesc = targetPlayer != null ? targetPlayer.getName().getString() : "Frontal";
                owner.sendSystemMessage(
                        Component.literal("§e[Bob] Orden recibida: §f" + spell.getName().getString() + " §7(Objetivo: " + targetDesc + ")"),
                        false
                );
            }
        }

        // Try casting immediately if ready and not channeling
        if (!isChanneling()) {
            evaluatePendingOrder();
        }
    }

    private void evaluatePendingOrder() {
        if (pendingSpell == null || !(this.level() instanceof ServerLevel sl)) return;

        // Infinite mana: Bob only checks cooldown!
        boolean onCd = isSpellOnCooldown(pendingSpell.getId());

        if (!onCd) {
            // Ready to cast!
            Spell spellToCast = pendingSpell;
            UUID targetUuid = pendingTargetUuid;
            pendingSpell = null;
            pendingTargetUuid = null;

            startCasting(spellToCast, targetUuid, sl);
        }
    }

    private void startCasting(Spell spell, UUID targetUuid, ServerLevel level) {
        int castTime = spell.calculateFinalCastTime(CastingMethod.BARE_HANDS);

        // Turn towards target if applicable
        turnTowardsTarget(targetUuid, level);

        if (castTime <= 0) {
            // Instant spell (e.g. Deny, Bolt, Flash)
            executeSpellNow(spell, targetUuid, level);
        } else {
            // Channeled spell (e.g. Thundaja, Disrupt, Praesidium, Iteratus, Divine Sword, etc.)
            channelingSpell = spell;
            channelingTargetUuid = targetUuid;
            channelingTicks = 0;
            totalChannelTicks = castTime;
            iteratusFiring = false;
            iteratusTimer = 0;

            // Sync channeling data to all clients
            this.entityData.set(DATA_CHANNELING, true);
            this.entityData.set(DATA_CHANNEL_TICKS, 0);
            this.entityData.set(DATA_TOTAL_CHANNEL_TICKS, castTime);

            // Broadcast channel start to nearby clients
            ModNetwork.sendToNearby(
                    new PacketPlayerChannelState(this.getId(), spell.getId(), PacketPlayerChannelState.ACTION_START),
                    level, this.position(), 64.0
            );

            // Spell-specific channel start notifications
            Vec3 aimPos = resolveAimPosition(targetUuid, level, spell.getRange());
            if (spell.getId().equals(ThundajaSpell.ID)) {
                ThundajaSpell.setPlayerChannelTarget(this.getId(), aimPos);
                ThundajaSpell.onChannelStart(level, this.getUUID());
                ModNetwork.sendToNearby(
                        new PacketThundajaStormState(this.getId(), PacketThundajaChannel.ACTION_START, aimPos),
                        level, this.position(), 64.0
                );
            } else if (spell.getId().equals(LaPollaCayendoSpell.ID)) {
                LaPollaCayendoSpell.setPlayerChannelTarget(this.getId(), aimPos);
                ModNetwork.sendToNearby(
                        new PacketFallingSwordChannelState(this.getId(), PacketFallingSwordChannel.ACTION_START, aimPos),
                        level, this.position(), 64.0
                );
            } else if (spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.DivineSwordSpell.ID)) {
                ModNetwork.sendToNearby(
                        new PacketDivineSwordChannelState(this.getId(), PacketDivineSwordChannel.ACTION_START),
                        level, this.position(), 64.0
                );
            } else if (spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell.ID)) {
                ModNetwork.sendToNearby(
                        new PacketRedshaChannelState(this.getId(), PacketRedshaChannel.ACTION_START),
                        level, this.position(), 64.0
                );
            }
        }
    }

    public void interruptChannel(Player interrupter) {
        if (!isChanneling() || !(this.level() instanceof ServerLevel sl)) return;

        Spell interrupted = channelingSpell;
        int cd = interrupted != null ? interrupted.calculateFinalCooldown(CastingMethod.BARE_HANDS) : 20;
        if (interrupted != null) {
            setCooldown(interrupted.getId(), cd);
        }

        // Spell-specific channel cleanup
        if (interrupted != null && interrupted.getId().equals(ThundajaSpell.ID)) {
            ThundajaSpell.onChannelEnd(sl, this.getUUID());
        } else if (interrupted != null && interrupted.getId().equals(LaPollaCayendoSpell.ID)) {
            LaPollaCayendoSpell.clearPlayerChannelTarget(this.getId());
        }

        channelingSpell = null;
        channelingTargetUuid = null;
        channelingTicks = 0;
        totalChannelTicks = 0;
        iteratusFiring = false;
        iteratusTimer = 0;

        // Clear synched data
        this.entityData.set(DATA_CHANNELING, false);
        this.entityData.set(DATA_CHANNEL_TICKS, 0);
        this.entityData.set(DATA_TOTAL_CHANNEL_TICKS, 0);

        ModNetwork.sendToNearby(
                new PacketPlayerChannelState(this.getId(), null, PacketPlayerChannelState.ACTION_CANCEL),
                sl, this.position(), 64.0
        );

        ServerPlayer owner = getOwnerPlayer(sl);
        if (owner != null && interrupted != null) {
            owner.sendSystemMessage(
                    Component.literal("§c[Bob] ¡El casteo de " + interrupted.getName().getString() + " ha sido interrumpido por " + (interrupter != null ? interrupter.getName().getString() : "Disrupt") + "!"),
                    false
            );
        }
    }

    private void executeSpellNow(Spell spell, UUID targetUuid, ServerLevel level) {
        FakePlayer fp = getOrCreateFakePlayer(level);
        turnTowardsTarget(targetUuid, level);
        syncFakePlayer(fp);

        int cd = spell.calculateFinalCooldown(CastingMethod.BARE_HANDS);
        Vec3 aimPos = resolveAimPosition(targetUuid, level, spell.getRange());
        boolean success;

        if (spell instanceof com.cesar.magicandsorcery.magic.spell.spells.PraesidiumSpell praesidium) {
            boolean selfCast = (targetUuid == null);
            success = praesidium.execute(fp, level, CastingMethod.BARE_HANDS, aimPos, selfCast);
        } else if (spell instanceof com.cesar.magicandsorcery.magic.spell.spells.DisruptSpell disrupt) {
            success = disrupt.execute(fp, level, CastingMethod.BARE_HANDS, aimPos);
        } else {
            success = spell.execute(fp, level, CastingMethod.BARE_HANDS);
        }

        if (success) {
            // Infinite mana: no mana deduction, only cooldown
            setCooldown(spell.getId(), cd);
        }

        // Cleanup channel state
        channelingSpell = null;
        channelingTargetUuid = null;
        channelingTicks = 0;
        totalChannelTicks = 0;
        iteratusFiring = false;
        iteratusTimer = 0;

        this.entityData.set(DATA_CHANNELING, false);
        this.entityData.set(DATA_CHANNEL_TICKS, 0);
        this.entityData.set(DATA_TOTAL_CHANNEL_TICKS, 0);

        ModNetwork.sendToNearby(
                new PacketPlayerChannelState(this.getId(), null, PacketPlayerChannelState.ACTION_FINISH),
                level, this.position(), 64.0
        );
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) return;
        if (!(this.level() instanceof ServerLevel sl)) return;

        // 1. Tick Cooldowns
        if (!cooldowns.isEmpty()) {
            java.util.List<ResourceLocation> expired = new java.util.ArrayList<>();
            for (Map.Entry<ResourceLocation, Integer> entry : cooldowns.entrySet()) {
                int rem = entry.getValue() - 1;
                if (rem <= 0) {
                    expired.add(entry.getKey());
                } else {
                    entry.setValue(rem);
                }
            }
            for (ResourceLocation id : expired) {
                cooldowns.remove(id);
            }
        }

        // 2. Handle Active Channeling
        if (channelingSpell != null) {
            turnTowardsTarget(channelingTargetUuid, sl);

            // Channeling particles
            if (this.tickCount % 2 == 0) {
                double px = this.getX() + (Math.random() - 0.5) * 1.0;
                double py = this.getY() + Math.random() * 1.8;
                double pz = this.getZ() + (Math.random() - 0.5) * 1.0;
                sl.sendParticles(ParticleTypes.ENCHANT, px, py, pz, 1, 0, 0.05, 0, 0.02);
            }

            // Iteratus missile streaming
            if (channelingSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.IteratusSpell.ID)) {
                if (channelingTicks >= totalChannelTicks) {
                    if (!iteratusFiring) {
                        iteratusFiring = true;
                        fireIteratusMissile(sl);
                        iteratusTimer = 20; // 1 missile per second
                    } else {
                        iteratusTimer--;
                        if (iteratusTimer <= 0) {
                            fireIteratusMissile(sl);
                            iteratusTimer = 20;
                        }
                    }
                } else {
                    channelingTicks++;
                }
            } else {
                channelingTicks++;
                if (channelingTicks >= totalChannelTicks) {
                    finishChannelingSuccess(sl);
                }
            }

            // Update synched ticks for clients
            this.entityData.set(DATA_CHANNEL_TICKS, channelingTicks);
        } else if (pendingSpell != null) {
            // 3. Handle Pending Order
            evaluatePendingOrder();
        }
    }

    private void finishChannelingSuccess(ServerLevel sl) {
        if (channelingSpell != null) {
            executeSpellNow(channelingSpell, channelingTargetUuid, sl);
        }
    }

    private void fireIteratusMissile(ServerLevel sl) {
        Vec3 eyePos = this.getEyePosition();
        Vec3 lookVec = this.getViewVector(1.0f).normalize();
        Vec3 spawnPos = eyePos.add(lookVec.scale(0.5));

        IteratusMissileEntity missile = new IteratusMissileEntity(sl, this, 10.0f);
        missile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        missile.setDeltaMovement(lookVec.scale(1.15));
        sl.addFreshEntity(missile);

        sl.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 1.8f);
    }

    private void turnTowardsTarget(UUID targetUuid, ServerLevel level) {
        if (targetUuid != null) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetUuid);
            if (target != null && target.isAlive()) {
                Vec3 toTarget = target.getEyePosition().subtract(this.getEyePosition()).normalize();
                float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
                float pitch = (float) -(Mth.atan2(toTarget.y, Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)) * (180.0 / Math.PI));
                this.setYRot(yaw);
                this.setXRot(pitch);
                this.yHeadRot = yaw;
                this.yBodyRot = yaw;
                return;
            }
        }
        // Frontal: stay facing Bob's current rotation
        this.yHeadRot = this.getYRot();
        this.yBodyRot = this.getYRot();
    }

    private Vec3 resolveAimPosition(UUID targetUuid, ServerLevel level, double range) {
        if (targetUuid != null) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetUuid);
            if (target != null && target.isAlive()) {
                return target.position();
            }
        }
        return this.position().add(this.getViewVector(1.0f).scale(range));
    }

    public FakePlayer getOrCreateFakePlayer(ServerLevel sl) {
        if (fakePlayer == null) {
            GameProfile profile = new GameProfile(this.getUUID(), "Bob");
            fakePlayer = FakePlayerFactory.get(sl, profile);
        }
        return fakePlayer;
    }

    public void syncFakePlayer(FakePlayer fp) {
        fp.setPos(this.getX(), this.getY(), this.getZ());
        fp.setYRot(this.getYRot());
        fp.setXRot(this.getXRot());
        fp.yHeadRot = this.yHeadRot;
        fp.yBodyRot = this.yBodyRot;
        fp.setHealth(this.getHealth());
    }

    public ServerPlayer getOwnerPlayer(ServerLevel sl) {
        UUID ownerUuid = getOwnerUUID();
        if (ownerUuid == null) return null;
        return sl.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Check if Bob has an active Deny barrier protecting frontal attacks
        if (DenySpell.hasActiveBarrier(this.fakePlayer != null ? this.fakePlayer : null)) {
            if (DenySpell.tryBlockDamage(this.fakePlayer, source, amount)) {
                return false;
            }
        }

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel sl) {
            ServerPlayer owner = getOwnerPlayer(sl);
            if (owner != null) {
                owner.sendSystemMessage(
                        Component.literal("§c[Bob] Bob ha caído en combate."),
                        false
                );
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("OwnerUUID")) this.entityData.set(DATA_OWNER_UUID, nbt.getString("OwnerUUID"));
        if (nbt.contains("OwnerName")) this.entityData.set(DATA_OWNER_NAME, nbt.getString("OwnerName"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("OwnerUUID", this.entityData.get(DATA_OWNER_UUID));
        nbt.putString("OwnerName", this.entityData.get(DATA_OWNER_NAME));
    }
}
