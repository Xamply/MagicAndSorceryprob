package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.ClientChannelTracker;
import com.cesar.magicandsorcery.entity.IInterruptibleProjectile;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicData;
import com.cesar.magicandsorcery.magic.capability.PlayerMagicProvider;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketDisruptVisual;
import com.cesar.magicandsorcery.network.packets.PacketInterruptChannel;
import com.cesar.magicandsorcery.network.packets.PacketPlayerChannelState;
import com.cesar.magicandsorcery.network.packets.PacketSyncMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DisruptSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "disrupt");

    public DisruptSpell() {
        super(ID, SpellSchool.INTERFERENCE, SpellType.UTILITY,
                50.0f, // 50 Mana cost
                40,    // 2.0 seconds cast/channel time (40 ticks)
                300,   // 15.0 seconds cooldown (300 ticks)
                0.0f   // 0 Damage
        );
    }

    @Override
    public double getRange() {
        return 28.0;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        return execute(player, level, method, null);
    }

    public boolean execute(ServerPlayer player, Level level, CastingMethod method, Vec3 clientAim) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        // 1. Resolve target: check client aim position first, or search directly from caster's eye ray
        Entity target = null;
        if (clientAim != null) {
            target = findTargetNear(serverLevel, clientAim, 3.0, player);
        }
        if (target == null) {
            target = findDisruptTarget(player, serverLevel, getRange(method));
        }

        if (target == null) {
            player.sendSystemMessage(
                    Component.translatable("message.magic_and_sorcery.disrupt.no_target")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
            return false;
        }

        // 2. Handle Case A: Player channeling a spell
        if (target instanceof ServerPlayer targetPlayer) {
            PlayerMagicData targetMagic = targetPlayer.getCapability(PlayerMagicProvider.PLAYER_MAGIC).orElse(null);
            if (targetMagic == null || !targetMagic.isChanneling()) {
                player.sendSystemMessage(
                        Component.translatable("message.magic_and_sorcery.disrupt.no_target")
                                .withStyle(ChatFormatting.GRAY),
                        true
                );
                return false;
            }

            ResourceLocation channelingSpellId = targetMagic.getChannelingSpellId();
            Spell interruptedSpell = ModSpells.getSpell(channelingSpellId);

            if (interruptedSpell == null || !interruptedSpell.isInterruptibleCast()) {
                player.sendSystemMessage(
                        Component.translatable("message.magic_and_sorcery.disrupt.cannot_interrupt")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                return false;
            }

            // Successfully interrupt the spell!
            // Force the interrupted spell into its full cooldown as if completed successfully
            int imposedCooldown = interruptedSpell.calculateFinalCooldown(targetMagic.getChannelingMethod());
            targetMagic.setSpellCooldown(interruptedSpell.getId(), imposedCooldown);
            targetMagic.stopChanneling();

            // Specific spell server cleanups
            if (channelingSpellId.equals(ThundajaSpell.ID)) {
                ThundajaSpell.onChannelEnd(serverLevel, targetPlayer.getUUID());
            } else if (channelingSpellId.equals(LaPollaCayendoSpell.ID)) {
                LaPollaCayendoSpell.clearPlayerChannelTarget(targetPlayer.getId());
            }

            // Sync and notify target
            ModNetwork.sendToPlayer(new PacketSyncMagicData(targetMagic), targetPlayer);
            ModNetwork.sendToPlayer(new PacketInterruptChannel(channelingSpellId, player.getId()), targetPlayer);

            // Broadcast channel cancel to nearby clients
            ModNetwork.sendToNearby(
                    new PacketPlayerChannelState(targetPlayer.getId(), null, PacketPlayerChannelState.ACTION_CANCEL),
                    serverLevel,
                    targetPlayer.position(),
                    64.0
            );

            // Audio & Visual Effects on victim
            Vec3 fxPos = targetPlayer.position().add(0, targetPlayer.getBbHeight() * 0.5, 0);
            playRuptureEffects(serverLevel, fxPos, targetPlayer.getId(), false);

            player.sendSystemMessage(
                    Component.translatable("message.magic_and_sorcery.disrupt.success_caster",
                            targetPlayer.getDisplayName(), interruptedSpell.getName())
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true
            );

            return true;
        }

        // 3. Handle Case B: Vanilla Illager casting spell (Evoker, Illusioner)
        if (target instanceof SpellcasterIllager illager && illager.isCastingSpell()) {
            illager.hurt(serverLevel.damageSources().indirectMagic(player, player), 0.0f);
            illager.setTarget(null);
            illager.getNavigation().stop();

            Vec3 fxPos = illager.position().add(0, illager.getBbHeight() * 0.5, 0);
            playRuptureEffects(serverLevel, fxPos, illager.getId(), false);

            player.sendSystemMessage(
                    Component.translatable("message.magic_and_sorcery.disrupt.success_caster",
                            illager.getDisplayName(), Component.literal("Magia"))
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true
            );
            return true;
        }

        // 4. Handle Case C: Projectile in flight
        if (target instanceof Projectile proj) {
            boolean handled = false;

            if (proj instanceof IInterruptibleProjectile interruptible) {
                ResourceLocation spellId = interruptible.getAssociatedSpellId();
                Spell spell = ModSpells.getSpell(spellId);
                if (spell != null && !spell.isInterruptibleProjectile()) {
                    player.sendSystemMessage(
                            Component.translatable("message.magic_and_sorcery.disrupt.cannot_interrupt")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                    return false;
                }

                // Neutralize projectile
                proj.addTag("disrupted");
                Vec3 pPos = proj.position();
                proj.discard();

                // Impose cooldown on launcher if not already in cooldown
                Entity owner = proj.getOwner();
                if (owner instanceof ServerPlayer ownerPlayer && spell != null) {
                    ownerPlayer.getCapability(PlayerMagicProvider.PLAYER_MAGIC).ifPresent(ownerMagic -> {
                        if (!ownerMagic.isSpellOnCooldown(spell.getId())) {
                            int cd = spell.calculateFinalCooldown(CastingMethod.BARE_HANDS);
                            ownerMagic.setSpellCooldown(spell.getId(), cd);
                            ModNetwork.sendToPlayer(new PacketSyncMagicData(ownerMagic), ownerPlayer);
                            ownerPlayer.sendSystemMessage(
                                    Component.translatable("message.magic_and_sorcery.disrupt.interrupted_victim", spell.getName())
                                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                                    true
                            );
                        }
                    });
                }

                playRuptureEffects(serverLevel, pPos, -1, true);
                player.sendSystemMessage(
                        Component.translatable("message.magic_and_sorcery.disrupt.success_projectile",
                                spell != null ? spell.getName() : Component.literal("Proyectil"))
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        true
                );
                return true;
            }

            // Vanilla magical projectiles (Fireballs, Wither Skulls, Shulker Bullets)
            if (proj instanceof Fireball || proj instanceof DragonFireball
                    || proj instanceof WitherSkull || proj instanceof ShulkerBullet) {
                proj.addTag("disrupted");
                Vec3 pPos = proj.position();
                proj.discard();

                playRuptureEffects(serverLevel, pPos, -1, true);
                player.sendSystemMessage(
                        Component.translatable("message.magic_and_sorcery.disrupt.success_projectile",
                                proj.getDisplayName())
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        true
                );
                return true;
            }
        }

        player.sendSystemMessage(
                Component.translatable("message.magic_and_sorcery.disrupt.no_target")
                        .withStyle(ChatFormatting.GRAY),
                true
        );
        return false;
    }

    private static void playRuptureEffects(ServerLevel level, Vec3 pos, int entityId, boolean isProjectile) {
        // Broadcast visual shockwave and particle dispersion
        ModNetwork.sendToNearby(
                new PacketDisruptVisual(pos, entityId, isProjectile),
                level,
                pos,
                64.0
        );

        // Sound chord of magical glass/crystalline rupture and collapse
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.4f, 0.75f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.3f, 1.7f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.2f, 0.65f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 1.5f);

        // Server-side fallback particles
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 20, 0.3, 0.3, 0.3, 0.15);
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 15, 0.25, 0.25, 0.25, 0.1);
    }

    public static Entity findTargetNear(ServerLevel level, Vec3 pos, double radius, Player caster) {
        AABB box = new AABB(pos, pos).inflate(radius);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box, e -> isValidDisruptTarget(e, caster));
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : entities) {
            double d = e.distanceToSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    public static Entity findDisruptTarget(Player caster, Level level, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f).normalize();
        AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(2.5);

        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, searchBox, e -> isValidDisruptTarget(e, caster));

        Entity best = null;
        double minRayDist = Double.MAX_VALUE;

        for (Entity e : candidates) {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 toCenter = center.subtract(eye);
            double dot = toCenter.dot(look);
            if (dot < 0.2 || dot > range) continue;

            Vec3 projected = eye.add(look.scale(dot));
            double rayDist = center.distanceTo(projected);

            // Generous aim tolerance for smooth targeting
            double tolerance = Math.max(1.8, Math.max(e.getBbWidth(), e.getBbHeight()) * 0.9);
            if (rayDist <= tolerance && rayDist < minRayDist) {
                minRayDist = rayDist;
                best = e;
            }
        }

        return best;
    }

    public static boolean isValidDisruptTarget(Entity e, Player caster) {
        if (e == caster || !e.isAlive() || e.isRemoved()) {
            return false;
        }

        // 1. Players channeling
        if (e instanceof Player otherPlayer) {
            if (caster.getTeam() != null && caster.isAlliedTo(otherPlayer)) {
                return false;
            }
            if (e.level().isClientSide()) {
                return ClientChannelTracker.isPlayerChanneling(otherPlayer.getId());
            } else if (otherPlayer instanceof ServerPlayer sp) {
                return sp.getCapability(PlayerMagicProvider.PLAYER_MAGIC)
                        .map(PlayerMagicData::isChanneling)
                        .orElse(false);
            }
        }

        // 2. Spellcaster illagers
        if (e instanceof SpellcasterIllager illager) {
            return illager.isCastingSpell();
        }

        // 3. Projectiles in flight
        if (e instanceof Projectile proj) {
            if (proj.getOwner() == caster) {
                return false;
            }
            if (proj.getTags().contains("disrupted")) {
                return false;
            }
            if (proj instanceof IInterruptibleProjectile) {
                return true;
            }
            if (proj instanceof Fireball || proj instanceof DragonFireball
                    || proj instanceof WitherSkull || proj instanceof ShulkerBullet) {
                return true;
            }
        }

        return false;
    }
}
