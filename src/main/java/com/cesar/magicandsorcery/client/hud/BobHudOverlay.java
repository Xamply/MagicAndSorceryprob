package com.cesar.magicandsorcery.client.hud;

import com.cesar.magicandsorcery.client.BobClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class BobHudOverlay {

    public static final IGuiOverlay HUD_BOB = (gui, g, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.options.hideGui) {
            return;
        }

        if (!BobClientData.isVisible()) {
            return;
        }

        Font font = mc.font;
        float time = mc.player.tickCount + partialTick;

        // Position: Top-left corner (below status effects / top left corner)
        int x = 12;
        int y = 12;
        int width = 160;
        int height = 86;

        // Panel background
        MagicGui.panel(g, x, y, width, height, MagicGui.FRAME, time);

        // Header: "Bob [Pruebas Mágicas]"
        g.flush();
        Component title = Component.literal("§6§lBob §8[§bPruebas§8]");
        g.drawString(font, title, x + 8, y + 7, 0xFFFFFF, true);

        // State indicator on top right
        String stateText;
        int stateColor;
        switch (BobClientData.stateKey) {
            case "channeling":
                stateText = "§dCanalizando";
                stateColor = MagicGui.CHANNEL;
                break;
            case "pending":
                stateText = "§ePendiente";
                stateColor = 0xFFFFDD55;
                break;
            case "cooldown":
                stateText = "§cEn Enfriamiento";
                stateColor = MagicGui.COOLDOWN;
                break;
            case "no_mana":
                stateText = "§cSin Maná";
                stateColor = 0xFFFF5555;
                break;
            default:
                stateText = "§aInactivo";
                stateColor = 0xFF55FF55;
                break;
        }
        int stateW = font.width(stateText);
        g.drawString(font, stateText, x + width - 8 - stateW, y + 7, stateColor, true);

        // Health Bar (x + 8, y + 20, w = 144, h = 6)
        int barW = width - 16;
        int barH = 5;
        int hpY = y + 20;

        float hpPct = Math.max(0.0f, Math.min(1.0f, BobClientData.health / Math.max(1.0f, BobClientData.maxHealth)));
        MagicGui.rect(g, x + 8, hpY, barW, barH, 0x88200000);
        int hpFillW = (int) (barW * hpPct);
        if (hpFillW > 0) {
            MagicGui.rect(g, x + 8, hpY, hpFillW, barH, 0xFFFF3333);
        }
        g.flush();
        String hpStr = String.format("§cHP: %.1f / %.1f", BobClientData.health, BobClientData.maxHealth);
        g.drawString(font, hpStr, x + 8, hpY + barH + 2, 0xFFAAAAAA, false);

        // Mana Bar (x + 8, y + 36, w = 144, h = 5)
        int mpY = y + 36;
        float mpPct = Math.max(0.0f, Math.min(1.0f, BobClientData.mana / Math.max(1.0f, BobClientData.maxMana)));
        MagicGui.rect(g, x + 8, mpY, barW, barH, 0x88002033);
        int mpFillW = (int) (barW * mpPct);
        if (mpFillW > 0) {
            MagicGui.rect(g, x + 8, mpY, mpFillW, barH, MagicGui.MANA);
        }
        g.flush();
        String mpStr = String.format("§bMP: %.1f / %.1f", BobClientData.mana, BobClientData.maxMana);
        g.drawString(font, mpStr, x + 8, mpY + barH + 2, 0xFFAAAAAA, false);

        // Spell & Target line
        int infoY = y + 51;
        String spellStr = "§7Hechizo: §f" + BobClientData.currentSpell;
        String targetStr = "§7Objetivo: §f" + BobClientData.target;
        g.drawString(font, spellStr, x + 8, infoY, 0xFFFFFF, false);
        g.drawString(font, targetStr, x + 8, infoY + 10, 0xFFFFFF, false);

        // Channeling or Cooldown status bar / label
        int statusY = infoY + 22;
        if (BobClientData.stateKey.equals("channeling") && BobClientData.channelProgress > 0.0f) {
            MagicGui.rect(g, x + 8, statusY, barW, 3, 0x66440044);
            int chW = (int) (barW * BobClientData.channelProgress);
            if (chW > 0) {
                MagicGui.rect(g, x + 8, statusY, chW, 3, MagicGui.CHANNEL);
            }
        } else if (BobClientData.remainingCooldown > 0.0f) {
            String cdStr = String.format("§eEnfriamiento: %.1fs", BobClientData.remainingCooldown);
            g.drawString(font, cdStr, x + 8, statusY - 2, MagicGui.COOLDOWN, false);
        }
    };
}
