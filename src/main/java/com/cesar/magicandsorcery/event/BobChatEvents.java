package com.cesar.magicandsorcery.event;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.command.SpawnBobCommand;
import com.cesar.magicandsorcery.entity.BobEntity;
import com.cesar.magicandsorcery.entity.BobManager;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID)
public class BobChatEvents {

    // Regex for orders:
    // Format 1: Bob, cast <SpellName> on <PlayerName>
    // Format 2: Bob, cast <SpellName>
    // Case-insensitive on "bob, cast" and "on"
    private static final Pattern ORDER_DIRECTED = Pattern.compile(
            "^\\s*bob,\\s*cast\\s+(.+?)\\s+on\\s+([a-zA-Z0-9_]{3,16})\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ORDER_FRONTAL = Pattern.compile(
            "^\\s*bob,\\s*cast\\s+(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SpawnBobCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString().trim();

        // Check if message starts with "Bob," or "bob,"
        if (!message.toLowerCase().startsWith("bob,")) {
            return;
        }

        // Check if player has an active Bob
        BobEntity bob = BobManager.getBobForPlayer(player);
        if (bob == null || !bob.isAlive()) {
            // Player doesn't have an active Bob
            // Don't intercept chat if no Bob, or notify player?
            // If message matches command syntax, let player know they don't have Bob summoned:
            if (ORDER_FRONTAL.matcher(message).matches() || ORDER_DIRECTED.matcher(message).matches()) {
                player.sendSystemMessage(
                        Component.literal("§c[Bob] No tienes a Bob invocado. Usa /spawn Bob para invocarlo."),
                        false
                );
            }
            return;
        }

        // Try matching directed order first
        Matcher directedMatcher = ORDER_DIRECTED.matcher(message);
        if (directedMatcher.matches()) {
            String spellQuery = directedMatcher.group(1).trim();
            String targetPlayerName = directedMatcher.group(2).trim();

            handleOrder(player, bob, spellQuery, targetPlayerName);
            return;
        }

        // Try matching frontal order
        Matcher frontalMatcher = ORDER_FRONTAL.matcher(message);
        if (frontalMatcher.matches()) {
            String spellQuery = frontalMatcher.group(1).trim();

            handleOrder(player, bob, spellQuery, null);
        }
    }

    private static void handleOrder(ServerPlayer supervisor, BobEntity bob, String spellQuery, String targetPlayerName) {
        // 1. Resolve Spell
        Spell matchedSpell = findSpell(spellQuery);
        if (matchedSpell == null) {
            supervisor.sendSystemMessage(
                    Component.literal("§c[Bob] Hechizo desconocido: '" + spellQuery + "'. Revisa el nombre del hechizo."),
                    false
            );
            return;
        }

        // 2. Resolve Target (if specified)
        ServerPlayer targetPlayer = null;
        if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
            targetPlayer = supervisor.server.getPlayerList().getPlayerByName(targetPlayerName);
            if (targetPlayer == null || !targetPlayer.isAlive()) {
                supervisor.sendSystemMessage(
                        Component.literal("§c[Bob] Objetivo '" + targetPlayerName + "' no encontrado o desconectado."),
                        false
                );
                return;
            }
        }

        // 3. Dispatch order to Bob
        bob.receiveOrder(matchedSpell, targetPlayer);
    }

    private static Spell findSpell(String query) {
        String cleanQuery = query.trim().toLowerCase().replace(" ", "").replace("_", "");

        for (Spell spell : ModSpells.getAllSpells()) {
            String path = spell.getId().getPath().toLowerCase().replace("_", "");
            String rawName = spell.getName().getString().toLowerCase().replace(" ", "").replace("_", "");

            if (path.equalsIgnoreCase(cleanQuery) || rawName.equalsIgnoreCase(cleanQuery)) {
                return spell;
            }

            // Also check partial / relaxed match (e.g. "thundaja", "bolt", "deny", "iteratus", "praesidium", "disrupt", "lapollacayendo", "falling_sword", "divinesword", "redsha", "blizzard", "flash")
            if (path.contains(cleanQuery) || rawName.contains(cleanQuery)) {
                return spell;
            }
        }
        return null;
    }
}
