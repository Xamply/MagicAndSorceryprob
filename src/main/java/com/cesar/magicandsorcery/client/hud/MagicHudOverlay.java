package com.cesar.magicandsorcery.client.hud;

import com.cesar.magicandsorcery.client.ClientMagicData;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;

public class MagicHudOverlay {
    public static final IGuiOverlay HUD_MAGIC = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.options.hideGui) {
            return;
        }

        Font font = mc.font;
        float currentMana = ClientMagicData.getMana();
        float maxMana = ClientMagicData.getMaxMana();
        int selectedIndex = ClientMagicData.getSelectedSpellIndex();
        Spell currentSpell = ClientMagicData.getSelectedSpell();
        CastingMethod method = ClientMagicData.getCurrentCastingMethod();

        // Position: Bottom Left corner
        int baseX = 12;
        int baseY = screenHeight - 16;

        // --- 1. MANA BAR ---
        int barWidth = 100;
        int barHeight = 8;

        // Background / Border
        guiGraphics.fill(baseX - 1, baseY - 1, baseX + barWidth + 1, baseY + barHeight + 1, 0xAA000000);
        guiGraphics.fill(baseX, baseY, baseX + barWidth, baseY + barHeight, 0xFF141E28);

        // Filled mana portion
        float manaPercent = Math.max(0.0f, Math.min(1.0f, currentMana / (maxMana > 0 ? maxMana : 1.0f)));
        int filledWidth = (int) (barWidth * manaPercent);
        if (filledWidth > 0) {
            // Cyan-blue gradient fill
            guiGraphics.fillGradient(baseX, baseY, baseX + filledWidth, baseY + barHeight, 0xFF00B4D8, 0xFF0077B6);
        }

        // Mana text: "100 / 100"
        String manaText = String.format("%.0f / %.0f", currentMana, maxMana);
        int textX = baseX + (barWidth - font.width(manaText)) / 2;
        int textY = baseY;
        guiGraphics.drawString(font, manaText, textX, textY, 0xFFFFFFFF, true);

        // --- 2. ACTIVE SPELL AND CATALYST BADGE ---
        int spellInfoY = baseY - 22;

        if (currentSpell != null) {
            // Spell name with School color
            Component spellName = currentSpell.getName();
            ChatFormatting schoolColor = currentSpell.getSchool().getColor();
            MutableComponent title = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(spellName.copy().withStyle(schoolColor, ChatFormatting.BOLD))
                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

            guiGraphics.drawString(font, title, baseX, spellInfoY, 0xFFFFFFFF, true);

            // Spell stats preview line (Cost, Cooldown, Damage if applicable)
            boolean isCreative = mc.player != null && mc.player.isCreative();
            boolean bypassCooldown = isCreative && com.cesar.magicandsorcery.config.ModConfigs.NO_COOLDOWN_IN_CREATIVE.get();
            boolean bypassMana = isCreative && com.cesar.magicandsorcery.config.ModConfigs.INFINITE_MANA_IN_CREATIVE.get();

            float finalCost = bypassMana ? 0.0f : currentSpell.calculateFinalManaCost(method);
            int cooldownTicks = bypassCooldown ? 0 : ClientMagicData.getSpellCooldown(currentSpell.getId());

            MutableComponent statsLine;
            if (bypassMana) {
                statsLine = Component.literal("0 MP (Creativo)").withStyle(ChatFormatting.GREEN);
            } else {
                statsLine = Component.literal(String.format("%.0f MP", finalCost)).withStyle(ChatFormatting.AQUA);
            }

            if (currentSpell.getBaseDamage() > 0) {
                float finalDamage = currentSpell.calculateFinalDamage(method);
                statsLine.append(Component.literal(String.format(" | %.1f DMG", finalDamage)).withStyle(ChatFormatting.RED));
            }

            if (cooldownTicks > 0) {
                float cdSec = cooldownTicks / 20.0f;
                statsLine.append(Component.literal(String.format(" | CD: %.1fs", cdSec)).withStyle(ChatFormatting.YELLOW));
            }

            guiGraphics.drawString(font, statsLine, baseX, spellInfoY + 10, 0xFFCCCCCC, true);
        } else {
            guiGraphics.drawString(font, Component.translatable("hud.magic_and_sorcery.no_spell").withStyle(ChatFormatting.GRAY), baseX, spellInfoY, 0xFFAAAAAA, true);
        }

        // --- 3. COMPACT CHANNELING PROGRESS BAR (Above Hotbar & Status Bars) ---
        if (ClientMagicData.isChanneling()) {
            Spell chSpell = ClientMagicData.getChannelingSpell();
            if (chSpell != null) {
                float progress = ClientMagicData.getChannelProgress();
                boolean isReady = ClientMagicData.isReadyToCast();

                // Compact dimensions: 96px wide, 5px high
                int cBarWidth = 96;
                int cBarHeight = 5;
                int cBarX = (screenWidth - cBarWidth) / 2;
                // Positioned comfortably above vanilla hotbar, hearts, armor, and food
                int cBarY = screenHeight - 57;

                // 1px black outer shadow border
                guiGraphics.fill(cBarX - 1, cBarY - 1, cBarX + cBarWidth + 1, cBarY + cBarHeight + 1, 0xCC000000);
                // Dark semitransparent background
                guiGraphics.fill(cBarX, cBarY, cBarX + cBarWidth, cBarY + cBarHeight, 0xBB0B1018);

                // Filled progress (0% -> 100%)
                int cFilled = isReady ? cBarWidth : (int) (cBarWidth * progress);
                if (cFilled > 0) {
                    int startColor = 0xFFFFD700; // Gold
                    int endColor = 0xFFFF9900;   // Amber
                    if (isReady) {
                        startColor = 0xFF55FF55; // Vibrant emerald green
                        endColor = 0xFF00DD44;
                    } else if (chSpell.getSchool() == SpellSchool.ICE) {
                        startColor = 0xFF00E5FF; // Cyan
                        endColor = 0xFF0088CC;
                    } else if (chSpell.getSchool() == SpellSchool.TELEPORTATION) {
                        startColor = 0xFFE088FF; // Purple
                        endColor = 0xFFA020F0;
                    } else if (chSpell.getSchool() == SpellSchool.PHYSICAL) {
                        startColor = 0xFFFF3333; // Crimson red
                        endColor = 0xFF880000;
                    }
                    guiGraphics.fillGradient(cBarX, cBarY, cBarX + cFilled, cBarY + cBarHeight, startColor, endColor);
                }

                // If fully ready (100%), add a subtle ready outline rim
                if (isReady) {
                    guiGraphics.fill(cBarX - 1, cBarY - 1, cBarX + cBarWidth + 1, cBarY, 0xFF88FFAA);
                    guiGraphics.fill(cBarX - 1, cBarY + cBarHeight, cBarX + cBarWidth + 1, cBarY + cBarHeight + 1, 0xFF00AA33);
                }
            }
        }

        // --- 5. IN-GAME RADIAL SPELL SELECTION MENU & LIBRARY (Hold R) ---
        if (ClientMagicData.isRadialMenuOpen()) {
            java.util.List<net.minecraft.resources.ResourceLocation> preparedSpells = ClientMagicData.getPreparedSpells();
            java.util.List<net.minecraft.resources.ResourceLocation> learnedSpells = ClientMagicData.getLearnedSpells();
            int initialSelectedIndex = ClientMagicData.getSelectedSpellIndex();

            final int TOTAL_SLOTS = RadialMenuRenderer.TOTAL_SLOTS;
            final double sectorAngle = (2.0 * Math.PI) / TOTAL_SLOTS;

            float centerX = RadialMenuRenderer.getWheelCenterX(screenWidth);
            float centerY = RadialMenuRenderer.getWheelCenterY(screenHeight);
            float innerCancelRadius = RadialMenuRenderer.INNER_CANCEL_RADIUS;
            float outerRadius = RadialMenuRenderer.OUTER_RADIUS;

            // Semi-solid dark backdrop over the world for high contrast
            guiGraphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

            // Compute real-time mouse position in GUI coordinates
            double windowW = Math.max(1.0, (double) mc.getWindow().getWidth());
            double windowH = Math.max(1.0, (double) mc.getWindow().getHeight());
            double mouseX = mc.mouseHandler.xpos() * (double) screenWidth / windowW;
            double mouseY = mc.mouseHandler.ypos() * (double) screenHeight / windowH;

            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            // Detect hovered sector
            int hoveredIndex = RadialMenuRenderer.calculateHoveredSlot(mc);
            ClientMagicData.setRadialHoveredIndex(hoveredIndex);

            boolean isDragging = ClientMagicData.isDragging();
            ClientMagicData.DragSource dragSource = ClientMagicData.getDragSource();
            int draggedSlot = ClientMagicData.getDraggedSlotIndex();
            Spell draggedSpell = ClientMagicData.getDraggedSpell();

            Matrix4f matrix = guiGraphics.pose().last().pose();

            // =========================================================================
            // A. SPELL LIBRARY PANEL (LEFT SIDE)
            // =========================================================================
            int libX = RadialMenuRenderer.getLibraryX(screenWidth);
            int libY = RadialMenuRenderer.getLibraryY(screenHeight);
            int libWidth = RadialMenuRenderer.getLibraryWidth(screenWidth);
            int libHeight = RadialMenuRenderer.getLibraryHeight(screenHeight);

            // Outer Frame
            guiGraphics.fill(libX - 2, libY - 2, libX + libWidth + 2, libY + libHeight + 2, 0xFF4A3E2C);
            guiGraphics.fill(libX, libY, libX + libWidth, libY + libHeight, 0xF20A0E15);

            // Header Banner
            guiGraphics.fill(libX, libY, libX + libWidth, libY + 20, 0xF8151D2A);
            guiGraphics.fill(libX + 3, libY + 20, libX + libWidth - 3, libY + 21, 0x66FFD700);
            guiGraphics.drawString(font, "BIBLIOTECA", libX + 5, libY + 6, 0xFFFFD700, true);
            String countStr = String.valueOf(learnedSpells.size());
            guiGraphics.drawString(font, countStr, libX + libWidth - 5 - font.width(countStr), libY + 6, 0xFFA0B0C0, true);

            // Content Area geometry
            int listY = libY + 22;
            int listHeight = libHeight - 25;
            int listWidth = libWidth - 10;
            int itemHeight = RadialMenuRenderer.LIBRARY_ITEM_HEIGHT;
            int totalH = learnedSpells.size() * itemHeight;
            int maxScroll = Math.max(0, totalH - listHeight);

            // Scrollbar dragging logic
            if (ClientMagicData.isDraggingScrollBar() && maxScroll > 0) {
                int thumbH = Math.max(12, (int) ((float) listHeight / Math.max(listHeight, totalH) * listHeight));
                float progress = (float) (mouseY - listY - thumbH / 2.0) / Math.max(1, listHeight - thumbH);
                ClientMagicData.setLibraryScrollOffset(progress * maxScroll);
            }

            float scrollOffset = Math.max(0.0f, Math.min(maxScroll, ClientMagicData.getLibraryScrollOffset()));
            ClientMagicData.setLibraryScrollOffset(scrollOffset);

            // Scissored List of Spells
            guiGraphics.enableScissor(libX + 2, listY, libX + listWidth, listY + listHeight);
            net.minecraft.resources.ResourceLocation hoveredLibSpell = null;

            for (int i = 0; i < learnedSpells.size(); i++) {
                net.minecraft.resources.ResourceLocation id = learnedSpells.get(i);
                Spell s = ModSpells.getSpell(id);
                if (s == null) continue;

                int itemY = listY + (int) (i * itemHeight - scrollOffset);
                if (itemY + itemHeight < listY || itemY > listY + listHeight) continue;

                boolean itemHovered = (mouseX >= libX + 2 && mouseX < libX + listWidth && mouseY >= itemY && mouseY < itemY + itemHeight && mouseY >= listY && mouseY <= listY + listHeight);
                if (itemHovered && !isDragging) {
                    hoveredLibSpell = id;
                }

                boolean isEquipped = preparedSpells.contains(id);

                if (itemHovered) {
                    guiGraphics.fill(libX + 2, itemY + 1, libX + listWidth - 1, itemY + itemHeight - 1, 0x44FFFFFF);
                } else if (isEquipped) {
                    guiGraphics.fill(libX + 2, itemY + 1, libX + listWidth - 1, itemY + itemHeight - 1, 0x22FFD700);
                }

                String iconSymbol = "✦";
                if (s.getSchool() == SpellSchool.LIGHTNING) iconSymbol = "⚡";
                else if (s.getSchool() == SpellSchool.ICE) iconSymbol = "❄";
                else if (s.getSchool() == SpellSchool.TELEPORTATION) iconSymbol = "✨";
                else if (s.getSchool() == SpellSchool.HOLY) iconSymbol = "⚔";
                else if (s.getSchool() == SpellSchool.PHYSICAL) iconSymbol = "🗡";

                int schoolCol = s.getSchool().getColor().getColor() != null ? s.getSchool().getColor().getColor() : 0xFFFFFFFF;
                guiGraphics.drawString(font, iconSymbol, libX + 4, itemY + 4, schoolCol, true);

                String sName = s.getName().getString();
                int maxNameW = listWidth - 22;
                if (font.width(sName) > maxNameW) {
                    sName = font.plainSubstrByWidth(sName, maxNameW - 4) + "…";
                }
                int textColor = itemHovered ? 0xFFFFFFFF : (isEquipped ? 0xFFFFE082 : 0xFFCCD5E0);
                guiGraphics.drawString(font, sName, libX + 15, itemY + 4, textColor, true);

                if (isEquipped) {
                    guiGraphics.drawString(font, "•", libX + listWidth - 5, itemY + 2, 0xFFFFD700, true);
                }
            }
            guiGraphics.disableScissor();
            ClientMagicData.setLibraryHoveredSpellId(hoveredLibSpell);

            // Scrollbar Track & Thumb
            if (maxScroll > 0) {
                int sbX = libX + libWidth - 6;
                int sbY = listY;
                int sbW = 4;
                guiGraphics.fill(sbX, sbY, sbX + sbW, sbY + listHeight, 0xFF121720);
                int thumbH = Math.max(12, (int) ((float) listHeight / Math.max(listHeight, totalH) * listHeight));
                int thumbY = sbY + (int) (scrollOffset / Math.max(1, maxScroll) * (listHeight - thumbH));
                int thumbCol = ClientMagicData.isDraggingScrollBar() ? 0xFFFFD700 : (mouseX >= sbX - 2 && mouseX <= sbX + sbW + 2 ? 0xFFCCD5E0 : 0xFF708090);
                guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, thumbCol);
            }

            // =========================================================================
            // B. RADIAL SPELL WHEEL (CENTER/RIGHT)
            // =========================================================================
            // 1. Render All 9 Annular Sectors
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                double midAngle = -Math.PI / 2.0 + i * sectorAngle;
                double startAngle = midAngle - sectorAngle / 2.0;
                double endAngle = midAngle + sectorAngle / 2.0;

                boolean isOccupied = (i < preparedSpells.size() && preparedSpells.get(i) != null);
                boolean isHovered = (i == hoveredIndex);
                boolean isSelected = isOccupied && (i == initialSelectedIndex);
                boolean isDraggedSource = (isDragging && dragSource == ClientMagicData.DragSource.WHEEL && i == draggedSlot);
                Spell spell = isOccupied ? ModSpells.getSpell(preparedSpells.get(i)) : null;

                // Sector background color
                int sectorBgColor;
                if (isDragging) {
                    if (isDraggedSource) {
                        sectorBgColor = 0x88141C26;
                    } else if (isHovered) {
                        if (isOccupied) {
                            sectorBgColor = 0xF64A2F10; // Amber-gold for replace/swap
                        } else {
                            sectorBgColor = 0xF6143A3A; // Teal for place
                        }
                    } else {
                        sectorBgColor = isOccupied ? 0xF0141C26 : 0xD80D1218;
                    }
                } else {
                    if (isOccupied) {
                        if (isHovered) {
                            if (spell != null && spell.getSchool() == SpellSchool.LIGHTNING) {
                                sectorBgColor = 0xF63D2E14;
                            } else if (spell != null && spell.getSchool() == SpellSchool.ICE) {
                                sectorBgColor = 0xF6183446;
                            } else if (spell != null && spell.getSchool() == SpellSchool.TELEPORTATION) {
                                sectorBgColor = 0xF6341B44;
                            } else if (spell != null && spell.getSchool() == SpellSchool.HOLY) {
                                sectorBgColor = 0xF63F3818;
                            } else if (spell != null && spell.getSchool() == SpellSchool.PHYSICAL) {
                                sectorBgColor = 0xF6301A18;
                            } else {
                                sectorBgColor = 0xF6282038;
                            }
                        } else if (isSelected) {
                            sectorBgColor = 0xF020182C;
                        } else {
                            sectorBgColor = 0xF0141C26;
                        }
                    } else {
                        sectorBgColor = isHovered ? 0xF01C2533 : 0xC80B0F15;
                    }
                }

                RadialMenuRenderer.drawAnnularSector(guiGraphics, matrix, centerX, centerY, innerCancelRadius, outerRadius,
                        startAngle, endAngle, sectorBgColor, 16);

                // Sector Borders
                if (isHovered) {
                    int highlightColor = (isDragging && isOccupied) ? 0xFFFFB030 : 0xFFFFF070;
                    RadialMenuRenderer.drawAnnularSector(guiGraphics, matrix, centerX, centerY, outerRadius - 1.5f, outerRadius,
                            startAngle, endAngle, highlightColor, 16);
                } else if (isSelected && !isDragging) {
                    RadialMenuRenderer.drawAnnularSector(guiGraphics, matrix, centerX, centerY, outerRadius - 3.5f, outerRadius,
                            startAngle, endAngle, 0xFFFFD700, 16);
                    RadialMenuRenderer.drawAnnularSector(guiGraphics, matrix, centerX, centerY, innerCancelRadius, innerCancelRadius + 3.0f,
                            startAngle, endAngle, 0xFFFFD700, 16);
                } else if (isOccupied) {
                    int schoolCol = (spell != null && spell.getSchool().getColor().getColor() != null)
                            ? spell.getSchool().getColor().getColor() : 0xFFFFFF;
                    RadialMenuRenderer.drawAnnularSector(guiGraphics, matrix, centerX, centerY, outerRadius - 2.5f, outerRadius,
                            startAngle, endAngle, 0x88000000 | schoolCol, 16);
                } else {
                    RadialMenuRenderer.drawAnnularSector(guiGraphics, matrix, centerX, centerY, outerRadius - 1.5f, outerRadius,
                            startAngle, endAngle, 0x445A6B7C, 16);
                }

                // Dividing lines
                RadialMenuRenderer.drawRadialLine(guiGraphics, matrix, centerX, centerY, innerCancelRadius, outerRadius,
                        startAngle, 2.0f, 0xFF4A5568);
            }

            // Outer rings & Center hub
            RadialMenuRenderer.drawRing(guiGraphics, matrix, centerX, centerY, outerRadius, outerRadius + 2.5f, 0xFFFFD700, 64);
            RadialMenuRenderer.drawRing(guiGraphics, matrix, centerX, centerY, outerRadius + 2.5f, outerRadius + 4.0f, 0xAA000000, 64);
            RadialMenuRenderer.drawRing(guiGraphics, matrix, centerX, centerY, innerCancelRadius - 3.0f, innerCancelRadius, 0xFFFFD700, 48);
            RadialMenuRenderer.drawRing(guiGraphics, matrix, centerX, centerY, innerCancelRadius - 4.5f, innerCancelRadius - 3.0f, 0xFF6A5016, 48);

            int centerBgColor = (hoveredIndex == -1 && !RadialMenuRenderer.isMouseInsideLibrary(mouseX, mouseY, screenWidth, screenHeight))
                    ? 0xF8180A0E : 0xFA0B0F16;
            RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, centerX, centerY, innerCancelRadius - 4.0f, centerBgColor, 48);
            guiGraphics.flush();

            // 2. Sector Icons & Empty Numbers
            float rIcon = (innerCancelRadius + outerRadius) / 2.0f;
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                double midAngle = -Math.PI / 2.0 + i * sectorAngle;
                float iconX = centerX + (float) Math.cos(midAngle) * rIcon;
                float iconY = centerY + (float) Math.sin(midAngle) * rIcon;

                boolean isOccupied = (i < preparedSpells.size() && preparedSpells.get(i) != null);
                boolean isHovered = (i == hoveredIndex);
                boolean isSelected = isOccupied && (i == initialSelectedIndex);
                boolean isDraggedSource = (isDragging && dragSource == ClientMagicData.DragSource.WHEEL && i == draggedSlot);

                if (isOccupied) {
                    Spell spell = ModSpells.getSpell(preparedSpells.get(i));
                    if (spell != null) {
                        String iconSymbol = "✦";
                        if (spell.getSchool() == SpellSchool.LIGHTNING) iconSymbol = "⚡";
                        else if (spell.getSchool() == SpellSchool.ICE) iconSymbol = "❄";
                        else if (spell.getSchool() == SpellSchool.TELEPORTATION) iconSymbol = "✨";
                        else if (spell.getSchool() == SpellSchool.HOLY) iconSymbol = "⚔";
                        else if (spell.getSchool() == SpellSchool.PHYSICAL) iconSymbol = "🗡";

                        if (isDraggedSource) {
                            guiGraphics.drawString(font, iconSymbol, (int) iconX - font.width(iconSymbol) / 2, (int) (iconY - 5), 0x55FFFFFF, false);
                        } else if (isDragging && isHovered) {
                            RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, iconX, iconY - 1.0f, 12.0f, 0x66FF9900, 16);
                            guiGraphics.flush();
                            guiGraphics.drawString(font, iconSymbol, (int) iconX - font.width(iconSymbol) / 2, (int) (iconY - 5), 0xFFFFFFFF, true);
                            String actionIcon = (dragSource == ClientMagicData.DragSource.WHEEL) ? "⇄" : "↓";
                            guiGraphics.drawString(font, actionIcon, (int) (iconX - 3), (int) (iconY - 16), 0xFFFFB030, true);
                        } else if (isHovered) {
                            RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, iconX, iconY - 1.0f, 11.0f, 0x66FFFFFF, 16);
                            guiGraphics.flush();
                            guiGraphics.drawString(font, iconSymbol, (int) iconX - font.width(iconSymbol) / 2, (int) (iconY - 5), 0xFFFFFFFF, true);
                        } else {
                            guiGraphics.drawString(font, iconSymbol, (int) iconX - font.width(iconSymbol) / 2, (int) (iconY - 5), 0xFFE2E8F0, true);
                        }

                        if (isSelected && !isDraggedSource) {
                            guiGraphics.drawString(font, "★", (int) (iconX - 3), (int) (iconY - 15), 0xFFFFD700, true);
                        }
                    }
                } else {
                    if (isDragging && isHovered) {
                        RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, iconX, iconY - 1.0f, 11.0f, 0x6600E5FF, 16);
                        guiGraphics.flush();
                        String moveMarker = "↓";
                        guiGraphics.drawString(font, moveMarker, (int) iconX - font.width(moveMarker) / 2, (int) (iconY - 5), 0xFF00FFFF, true);
                    } else {
                        String slotNum = String.valueOf(i + 1);
                        int numColor = isHovered ? 0xFFFFFFFF : 0xFFA0B0C0;
                        guiGraphics.drawString(font, slotNum, (int) iconX - font.width(slotNum) / 2, (int) (iconY - 4), numColor, true);
                    }
                }
            }

            // 3. Center Hub Content
            if (hoveredIndex == -1 && !RadialMenuRenderer.isMouseInsideLibrary(mouseX, mouseY, screenWidth, screenHeight)) {
                RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, centerX, centerY - 6.0f, 9.0f, 0x66FF4444, 16);
                guiGraphics.flush();
                guiGraphics.drawCenteredString(font, "✕", (int) centerX, (int) (centerY - 10), 0xFFFF5555);
                guiGraphics.drawCenteredString(font, "CANCELAR", (int) centerX, (int) (centerY - 1), 0xFFFF6666);
                if (isDragging) {
                    guiGraphics.drawCenteredString(font, "Soltar p/ cancelar", (int) centerX, (int) (centerY + 9), 0xFFFFAAAA);
                } else {
                    guiGraphics.drawCenteredString(font, Component.translatable("gui.magic_and_sorcery.keep_current").getString(),
                            (int) centerX, (int) (centerY + 9), 0xFFE0E0E0);
                }
            } else {
                RadialMenuRenderer.drawRing(guiGraphics, matrix, centerX, centerY, 6.0f, 8.0f, 0x88FFD700, 24);
                guiGraphics.flush();
                guiGraphics.drawCenteredString(font, "✕", (int) centerX, (int) (centerY - 7), 0xFFA0B0C0);
                guiGraphics.drawCenteredString(font, "CANCEL", (int) centerX, (int) (centerY + 1), 0xFFCCD5E0);
            }

            // =========================================================================
            // C. SIDE PANEL: DRAG FEEDBACK OR DETAILED SPELL INFO CARD (RIGHT SIDE)
            // =========================================================================
            int minPanelX = (int) (centerX + outerRadius + 8);
            int availableRight = Math.max(100, screenWidth - minPanelX - 6);
            int panelW = Math.max(110, Math.min(136, availableRight));
            int panelX = screenWidth - panelW - 6;

            if (isDragging) {
                int panelH = 90;
                int panelY = (int) (centerY - panelH / 2.0f);

                boolean overLib = RadialMenuRenderer.isMouseInsideLibrary(mouseX, mouseY, screenWidth, screenHeight);

                if (overLib && dragSource == ClientMagicData.DragSource.WHEEL) {
                    // Hovering Library while dragging from wheel -> UNEQUIP
                    guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFFFF4444);
                    guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF8180A0E);
                    guiGraphics.drawString(font, "✕ DESEQUIPAR", panelX + 5, panelY + 5, 0xFFFF6666, true);
                    guiGraphics.fill(panelX + 4, panelY + 17, panelX + panelW - 4, panelY + 18, 0x44FFFFFF);
                    String s1 = (draggedSpell != null ? draggedSpell.getName().getString() : "Hechizo");
                    guiGraphics.drawString(font, s1, panelX + 5, panelY + 22, 0xFFFFFFFF, true);
                    guiGraphics.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of("Suelta el click para desequipar de la rueda. Seguirá en la biblioteca."), panelX + 5, panelY + 36, panelW - 10, 0xFFE0E8F0);
                } else if (hoveredIndex == -1) {
                    // Cancel zone
                    guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFFFF4444);
                    guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF8180A0E);
                    guiGraphics.drawString(font, "✕ CANCELAR", panelX + 5, panelY + 5, 0xFFFF6666, true);
                    guiGraphics.fill(panelX + 4, panelY + 17, panelX + panelW - 4, panelY + 18, 0x44FFFFFF);
                    guiGraphics.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of("Suelta el click en el centro para cancelar la acción sin realizar cambios."), panelX + 5, panelY + 22, panelW - 10, 0xFFE0E8F0);
                } else if (dragSource == ClientMagicData.DragSource.WHEEL && hoveredIndex == draggedSlot) {
                    // Origin slot
                    guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFFFFD700);
                    guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF80A0E15);
                    guiGraphics.drawString(font, "ORIGEN (SIN CAMBIO)", panelX + 5, panelY + 5, 0xFFFFD700, true);
                    guiGraphics.fill(panelX + 4, panelY + 17, panelX + panelW - 4, panelY + 18, 0x44FFFFFF);
                    guiGraphics.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of("El hechizo permanece in su espacio original #" + (draggedSlot + 1) + "."), panelX + 5, panelY + 22, panelW - 10, 0xFFCCD5E0);
                } else if (hoveredIndex >= 0 && hoveredIndex < preparedSpells.size() && preparedSpells.get(hoveredIndex) != null) {
                    // Target is occupied
                    Spell targetSpell = ModSpells.getSpell(preparedSpells.get(hoveredIndex));
                    guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFFFF9900);
                    guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF8150D06);
                    String headerTxt = (dragSource == ClientMagicData.DragSource.WHEEL) ? "⇄ INTERCAMBIAR" : "↓ REEMPLAZAR";
                    guiGraphics.drawString(font, headerTxt, panelX + 5, panelY + 5, 0xFFFFB030, true);
                    guiGraphics.fill(panelX + 4, panelY + 17, panelX + panelW - 4, panelY + 18, 0x44FFFFFF);
                    String s1 = (draggedSpell != null ? draggedSpell.getName().getString() : "Hechizo");
                    String s2 = (targetSpell != null ? targetSpell.getName().getString() : "Hechizo");
                    guiGraphics.drawString(font, s1, panelX + 5, panelY + 22, 0xFFFFFFFF, true);
                    guiGraphics.drawString(font, "→ #" + (hoveredIndex + 1) + " (" + s2 + ")", panelX + 5, panelY + 34, 0xFFFFE066, true);
                    guiGraphics.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of("Suelta el click para asignar este hechizo al espacio #" + (hoveredIndex + 1) + "."), panelX + 5, panelY + 48, panelW - 10, 0xFFCCD5E0);
                } else {
                    // Empty slot -> MOVE or EQUIP
                    guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF00E5FF);
                    guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF8061218);
                    String headerTxt = (dragSource == ClientMagicData.DragSource.WHEEL) ? "↓ MOVER" : "↓ EQUIPAR";
                    guiGraphics.drawString(font, headerTxt, panelX + 5, panelY + 5, 0xFF00FFFF, true);
                    guiGraphics.fill(panelX + 4, panelY + 17, panelX + panelW - 4, panelY + 18, 0x44FFFFFF);
                    String s1 = (draggedSpell != null ? draggedSpell.getName().getString() : "Hechizo");
                    guiGraphics.drawString(font, s1, panelX + 5, panelY + 22, 0xFFFFFFFF, true);
                    guiGraphics.drawString(font, "→ Espacio #" + (hoveredIndex + 1), panelX + 5, panelY + 34, 0xFF55FFFF, true);
                    guiGraphics.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of("Suelta el click izquierdo para colocarlo en este espacio."), panelX + 5, panelY + 48, panelW - 10, 0xFFCCD5E0);
                }
            } else {
                // Determine spell to inspect: hovered in library takes priority, otherwise hovered on wheel
                Spell inspectedSpell = null;
                if (hoveredLibSpell != null) {
                    inspectedSpell = ModSpells.getSpell(hoveredLibSpell);
                } else if (hoveredIndex >= 0 && hoveredIndex < preparedSpells.size() && preparedSpells.get(hoveredIndex) != null) {
                    inspectedSpell = ModSpells.getSpell(preparedSpells.get(hoveredIndex));
                }

                if (inspectedSpell != null) {
                    String desc = inspectedSpell.getDescription().getString();
                    int descWidth = panelW - 12;
                    int descLines = font.split(net.minecraft.network.chat.FormattedText.of(desc), descWidth).size();
                    int descH = Math.max(1, descLines) * font.lineHeight;
                    int panelH = 76 + descH + 8;
                    int panelY = (int) Math.max(6, Math.min(screenHeight - panelH - 6, centerY - panelH / 2.0f));

                    int schoolCol = inspectedSpell.getSchool().getColor().getColor() != null
                            ? inspectedSpell.getSchool().getColor().getColor() : 0xFFFFD700;

                    guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF000000 | schoolCol);
                    guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF80A0E15);

                    String iconPrefix = (inspectedSpell.getSchool() == SpellSchool.LIGHTNING ? "⚡ "
                            : (inspectedSpell.getSchool() == SpellSchool.ICE ? "❄ "
                            : (inspectedSpell.getSchool() == SpellSchool.TELEPORTATION ? "✨ "
                            : (inspectedSpell.getSchool() == SpellSchool.HOLY ? "⚔ "
                            : (inspectedSpell.getSchool() == SpellSchool.PHYSICAL ? "🗡 " : "✦ ")))));
                    String spellTitle = iconPrefix + inspectedSpell.getName().getString().toUpperCase();
                    if (font.width(spellTitle) > panelW - 10) {
                        spellTitle = font.plainSubstrByWidth(spellTitle, panelW - 16) + "…";
                    }
                    guiGraphics.drawString(font, spellTitle, panelX + 5, panelY + 5, schoolCol, true);

                    String typeStr = inspectedSpell.getSchool().getDisplayName().getString() + " • " + inspectedSpell.getType().getDisplayName().getString();
                    if (font.width(typeStr) > panelW - 10) {
                        typeStr = inspectedSpell.getSchool().getDisplayName().getString();
                    }
                    guiGraphics.drawString(font, typeStr, panelX + 5, panelY + 15, 0xFFB0C0D0, true);

                    guiGraphics.fill(panelX + 4, panelY + 25, panelX + panelW - 4, panelY + 26, 0x44FFFFFF);

                    float finalCost = inspectedSpell.calculateFinalManaCost(method);
                    String costStr = String.format("%.0f MP", finalCost);
                    guiGraphics.drawString(font, "Maná:", panelX + 5, panelY + 29, 0xFFD0D0D0, true);
                    guiGraphics.drawString(font, costStr, panelX + panelW - 5 - font.width(costStr), panelY + 29, 0xFF00FFFF, true);

                    int castTicks = inspectedSpell.calculateFinalCastTime(method);
                    String castStr = castTicks > 0 ? String.format("%.1fs", castTicks / 20.0f) : "Inst.";
                    guiGraphics.drawString(font, "Canal:", panelX + 5, panelY + 39, 0xFFD0D0D0, true);
                    guiGraphics.drawString(font, castStr, panelX + panelW - 5 - font.width(castStr), panelY + 39, 0xFFFF77FF, true);

                    int finalCd = inspectedSpell.calculateFinalCooldown(method);
                    String cdStr = String.format("%.1fs", finalCd / 20.0f);
                    guiGraphics.drawString(font, "Recarga:", panelX + 5, panelY + 49, 0xFFD0D0D0, true);
                    guiGraphics.drawString(font, cdStr, panelX + panelW - 5 - font.width(cdStr), panelY + 49, 0xFFFFD166, true);

                    if (inspectedSpell.getBaseDamage() > 0) {
                        String dmgStr = String.format("%.0f", inspectedSpell.calculateFinalDamage(method));
                        guiGraphics.drawString(font, "Daño:", panelX + 5, panelY + 59, 0xFFD0D0D0, true);
                        guiGraphics.drawString(font, dmgStr, panelX + panelW - 5 - font.width(dmgStr), panelY + 59, 0xFFFF5555, true);
                    } else {
                        String rngStr = String.format("%.0fm", inspectedSpell.getRange(method));
                        guiGraphics.drawString(font, "Alcance:", panelX + 5, panelY + 59, 0xFFD0D0D0, true);
                        guiGraphics.drawString(font, rngStr, panelX + panelW - 5 - font.width(rngStr), panelY + 59, 0xFF55FF55, true);
                    }

                    guiGraphics.fill(panelX + 4, panelY + 70, panelX + panelW - 4, panelY + 71, 0x44FFFFFF);
                    guiGraphics.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of(desc), panelX + 5, panelY + 74, panelW - 10, 0xFFCCD5E0);
                }
            }

            // =========================================================================
            // D. RETICLE & FLOATING DRAGGED GHOST
            // =========================================================================
            if (isDragging && draggedSpell != null) {
                float curX = (float) mouseX;
                float curY = (float) mouseY;

                int schoolCol = draggedSpell.getSchool().getColor().getColor() != null
                        ? draggedSpell.getSchool().getColor().getColor() : 0xFFFFD700;

                RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, curX, curY - 1.0f, 15.0f, 0x88000000 | (schoolCol & 0x00FFFFFF), 24);
                RadialMenuRenderer.drawRing(guiGraphics, matrix, curX, curY - 1.0f, 13.0f, 15.0f, 0xFFFFD700, 24);
                RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, curX, curY - 1.0f, 13.0f, 0xF8101824, 24);
                guiGraphics.flush();

                String dragSymbol = "✦";
                if (draggedSpell.getSchool() == SpellSchool.LIGHTNING) dragSymbol = "⚡";
                else if (draggedSpell.getSchool() == SpellSchool.ICE) dragSymbol = "❄";
                else if (draggedSpell.getSchool() == SpellSchool.TELEPORTATION) dragSymbol = "✨";
                else if (draggedSpell.getSchool() == SpellSchool.HOLY) dragSymbol = "⚔";
                else if (draggedSpell.getSchool() == SpellSchool.PHYSICAL) dragSymbol = "🗡";

                guiGraphics.drawString(font, dragSymbol, (int) curX - font.width(dragSymbol) / 2, (int) (curY - 5), 0xFFFFFFFF, true);

                String dragName = draggedSpell.getName().getString();
                int nameW = font.width(dragName) + 8;
                int badgeX = (int) curX - nameW / 2;
                int badgeY = (int) curY + 16;
                guiGraphics.fill(badgeX - 1, badgeY - 1, badgeX + nameW + 1, badgeY + 11, 0xFF000000 | schoolCol);
                guiGraphics.fill(badgeX, badgeY, badgeX + nameW, badgeY + 10, 0xEE0B101A);
                guiGraphics.drawString(font, dragName, badgeX + 4, badgeY + 1, 0xFFFFFFFF, true);
            } else {
                // Normal reticle
                boolean overLib = RadialMenuRenderer.isMouseInsideLibrary(mouseX, mouseY, screenWidth, screenHeight);
                if (overLib) {
                    // Small clean pointer dot when in library
                    RadialMenuRenderer.drawFilledCircle(guiGraphics, matrix, (float) mouseX, (float) mouseY, 2.5f, 0xFFFFFFFF, 12);
                } else {
                    double maxReticleDist = outerRadius + 6.0;
                    double reticleDist = Math.min(dist, maxReticleDist);
                    float pipX = (float) (centerX + (dist > 0.001 ? (dx / dist) * reticleDist : 0));
                    float pipY = (float) (centerY + (dist > 0.001 ? (dy / dist) * reticleDist : 0));

                    boolean isOccupied = (hoveredIndex >= 0 && hoveredIndex < preparedSpells.size() && preparedSpells.get(hoveredIndex) != null);
                    if (isOccupied) {
                        RadialMenuRenderer.drawReticle(guiGraphics, matrix, pipX, pipY, 4.5f, 0xFFFFF070, 0xFF6B541C);
                    } else if (hoveredIndex >= 0) {
                        RadialMenuRenderer.drawReticle(guiGraphics, matrix, pipX, pipY, 3.5f, 0xFFFFFFFF, 0xFF334455);
                    } else {
                        RadialMenuRenderer.drawReticle(guiGraphics, matrix, pipX, pipY, 4.5f, 0xFFFF4444, 0xFF7A1C22);
                    }
                }
            }
            guiGraphics.flush();
        }
    };
}
