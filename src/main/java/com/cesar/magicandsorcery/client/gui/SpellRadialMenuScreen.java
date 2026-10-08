package com.cesar.magicandsorcery.client.gui;

import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.client.KeyBindings;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketSelectSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SpellRadialMenuScreen extends Screen {
    private final List<ResourceLocation> preparedSpells = new ArrayList<>();
    private final int initialSelectedIndex;
    private int hoveredIndex = -1;

    public SpellRadialMenuScreen() {
        super(Component.translatable("gui.magic_and_sorcery.radial_menu"));
        this.preparedSpells.addAll(ClientMagicData.getPreparedSpells());
        this.initialSelectedIndex = ClientMagicData.getSelectedSpellIndex();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dark translucent background vignette
        guiGraphics.fill(0, 0, this.width, this.height, 0x6605050A);

        int centerX = this.width / 2;
        int centerY = this.height / 2 - 25;
        int innerCancelRadius = 30;
        int outerRadius = 88;
        int spellCount = preparedSpells.size();

        // Calculate mouse distance and angle
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < innerCancelRadius || spellCount == 0) {
            hoveredIndex = -1; // Center Cancel Zone
        } else {
            // Angle with 0 at the top (-PI/2)
            double mouseAngle = Math.atan2(dy, dx);
            double shiftedAngle = mouseAngle + Math.PI / 2.0;
            while (shiftedAngle < 0) shiftedAngle += 2.0 * Math.PI;
            while (shiftedAngle >= 2.0 * Math.PI) shiftedAngle -= 2.0 * Math.PI;

            double sectorSize = (2.0 * Math.PI) / spellCount;
            int calculatedIndex = (int) Math.floor((shiftedAngle + sectorSize / 2.0) / sectorSize) % spellCount;
            hoveredIndex = calculatedIndex;
        }

        // --- 1. CENTER CANCEL ZONE ---
        boolean isCancelHovered = (hoveredIndex == -1);
        int cancelBoxSize = 24;
        int cbX = centerX - cancelBoxSize;
        int cbY = centerY - cancelBoxSize;
        int cbX2 = centerX + cancelBoxSize;
        int cbY2 = centerY + cancelBoxSize;

        int cancelBorderColor = isCancelHovered ? 0xFFFF4444 : 0x88777788;
        int cancelBgColor = isCancelHovered ? 0xDD3A141A : 0xBB181822;

        guiGraphics.fill(cbX - 1, cbY - 1, cbX2 + 1, cbY2 + 1, cancelBorderColor);
        guiGraphics.fill(cbX, cbY, cbX2, cbY2, cancelBgColor);

        guiGraphics.drawCenteredString(this.font, "✕", centerX, centerY - 8, isCancelHovered ? 0xFFFF6666 : 0xFFAAAAAA);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.magic_and_sorcery.cancel").getString(), centerX, centerY + 3, isCancelHovered ? 0xFFFFFFFF : 0xFF888888);

        // --- 2. RADIAL SPELL SLOTS ---
        CastingMethod method = ClientMagicData.getCurrentCastingMethod();

        for (int i = 0; i < spellCount; i++) {
            double angle = -Math.PI / 2.0 + i * ((2.0 * Math.PI) / spellCount);
            int slotX = (int) (centerX + Math.cos(angle) * outerRadius);
            int slotY = (int) (centerY + Math.sin(angle) * outerRadius);

            boolean isHovered = (i == hoveredIndex);
            boolean isSelected = (i == initialSelectedIndex);

            Spell spell = ModSpells.getSpell(preparedSpells.get(i));
            if (spell == null) continue;

            int boxW = 54;
            int boxH = 34;
            int bx = slotX - boxW / 2;
            int by = slotY - boxH / 2;

            // Box styling
            int borderColor = isHovered ? 0xFFFFF070 : (isSelected ? 0xFFFFD700 : 0x885A5672);
            int bgColor = isHovered ? 0xEE2F2742 : (isSelected ? 0xDD241E34 : 0xCC141220);

            // Subtle connecting line from center to slot
            int lineColor = isHovered ? 0xAAFFF070 : (isSelected ? 0x88FFD700 : 0x445A5672);
            int lineStartX = (int) (centerX + Math.cos(angle) * (innerCancelRadius + 4));
            int lineStartY = (int) (centerY + Math.sin(angle) * (innerCancelRadius + 4));
            guiGraphics.fill(Math.min(lineStartX, slotX), Math.min(lineStartY, slotY),
                    Math.max(lineStartX, slotX) + 1, Math.max(lineStartY, slotY) + 1, lineColor);

            // Slot Background and border
            guiGraphics.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, borderColor);
            guiGraphics.fill(bx, by, bx + boxW, by + boxH, bgColor);

            // Icon / Rune
            String iconSymbol = "✦";
            if (spell.getSchool() == SpellSchool.LIGHTNING) iconSymbol = "⚡";
            else if (spell.getSchool() == SpellSchool.ICE) iconSymbol = "❄";
            else if (spell.getSchool() == SpellSchool.TELEPORTATION) iconSymbol = "✨";
            else if (spell.getSchool() == SpellSchool.HOLY) iconSymbol = "⚔";
            else if (spell.getSchool() == SpellSchool.PHYSICAL) iconSymbol = "🗡";

            guiGraphics.drawCenteredString(this.font, iconSymbol, slotX, by + 4, 0xFFFFFFFF);

            // Spell Name (truncated if long)
            String nameStr = spell.getName().getString();
            if (nameStr.length() > 8) {
                nameStr = nameStr.substring(0, 7) + "…";
            }
            guiGraphics.drawCenteredString(this.font, nameStr, slotX, by + 15, spell.getSchool().getColor().getColor() != null ? spell.getSchool().getColor().getColor() : 0xFFFFFFFF);

            // Active reference star
            if (isSelected) {
                guiGraphics.drawString(this.font, "★", bx + 3, by + 2, 0xFFFFD700, false);
            }
        }

        // --- 3. DETAILED INFO CARD (Bottom Center) ---
        int cardW = 280;
        int cardH = 68;
        int cardX = (this.width - cardW) / 2;
        int cardY = centerY + outerRadius + 26;

        if (hoveredIndex >= 0 && hoveredIndex < spellCount) {
            Spell selectedSpell = ModSpells.getSpell(preparedSpells.get(hoveredIndex));
            if (selectedSpell != null) {
                // Info Card Frame
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, 0xFF6C5CE7);
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE12111E);

                // Title Line: Name + School
                MutableComponent title = selectedSpell.getName().copy().withStyle(selectedSpell.getSchool().getColor(), ChatFormatting.BOLD)
                        .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(selectedSpell.getSchool().getDisplayName())
                        .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                        .append(selectedSpell.getType().getDisplayName().copy().withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));

                guiGraphics.drawString(this.font, title, cardX + 8, cardY + 7, 0xFFFFFFFF, true);

                // Stats Line
                float finalCost = selectedSpell.calculateFinalManaCost(method);
                int finalCdTicks = selectedSpell.calculateFinalCooldown(method);
                int finalCastTicks = selectedSpell.calculateFinalCastTime(method);

                MutableComponent stats = Component.literal("Coste: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.0f MP", finalCost)).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" | CD: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format("%.1fs", finalCdTicks / 20.0f)).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" | Cast: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(finalCastTicks > 0 ? String.format("%.1fs", finalCastTicks / 20.0f) : "Instant").withStyle(ChatFormatting.LIGHT_PURPLE));

                if (selectedSpell.getBaseDamage() > 0) {
                    float finalDmg = selectedSpell.calculateFinalDamage(method);
                    stats.append(Component.literal(" | Daño: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.format("%.1f", finalDmg)).withStyle(ChatFormatting.RED));
                }

                guiGraphics.drawString(this.font, stats, cardX + 8, cardY + 22, 0xFFE0E0E0, true);

                // Description Line
                Component desc = selectedSpell.getDescription();
                guiGraphics.drawString(this.font, desc, cardX + 8, cardY + 38, 0xFFAAAAAA, false);

                // Prompt line
                guiGraphics.drawString(this.font, Component.translatable("gui.magic_and_sorcery.release_to_select").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                        cardX + 8, cardY + 52, 0xFF888899, false);
            }
        } else {
            // Cancel Prompt
            guiGraphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, 0xFFE63946);
            guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE221015);

            guiGraphics.drawString(this.font, Component.translatable("gui.magic_and_sorcery.cancel_zone_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    cardX + 8, cardY + 12, 0xFFFF6666, true);
            guiGraphics.drawString(this.font, Component.translatable("gui.magic_and_sorcery.cancel_zone_desc").withStyle(ChatFormatting.GRAY),
                    cardX + 8, cardY + 28, 0xFFCCCCCC, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.magic_and_sorcery.cancel_zone_tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    cardX + 8, cardY + 44, 0xFF888888, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // Release R (or whatever KEY_SPELL_MENU is bound to) triggers selection confirmation
        int menuKey = KeyBindings.KEY_SPELL_MENU.getKey().getValue();
        if (keyCode == menuKey || keyCode == GLFW.GLFW_KEY_R) {
            confirmSelection();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirmSelection() {
        if (hoveredIndex >= 0 && hoveredIndex < preparedSpells.size()) {
            // User confirmed selection on a valid spell!
            ClientMagicData.setSelectedSpellIndex(hoveredIndex);
            ModNetwork.sendToServer(new PacketSelectSpell(hoveredIndex));
        }
        // If hoveredIndex == -1, user was in center cancel zone: no change made!
        this.onClose();
    }
}
