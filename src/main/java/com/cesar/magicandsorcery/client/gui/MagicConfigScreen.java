package com.cesar.magicandsorcery.client.gui;

import com.cesar.magicandsorcery.config.ModConfigs;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketUpdateCreativeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class MagicConfigScreen extends Screen {
    private final Screen parent;
    private boolean infiniteMana;
    private boolean noCooldown;

    private Button manaButton;
    private Button cooldownButton;

    public MagicConfigScreen(Screen parent) {
        super(Component.translatable("gui.magic_and_sorcery.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Load current config values
        this.infiniteMana = ModConfigs.INFINITE_MANA_IN_CREATIVE.get();
        this.noCooldown = ModConfigs.NO_COOLDOWN_IN_CREATIVE.get();

        int buttonWidth = 260;
        int buttonHeight = 20;
        int centerX = (this.width - buttonWidth) / 2;
        int startY = 62;

        // 1. Mana Consumption Toggle Button
        this.manaButton = Button.builder(getManaButtonText(), btn -> {
            this.infiniteMana = !this.infiniteMana;
            btn.setMessage(getManaButtonText());
        })
        .bounds(centerX, startY, buttonWidth, buttonHeight)
        .tooltip(Tooltip.create(Component.translatable("gui.magic_and_sorcery.config.infinite_mana.tooltip")))
        .build();
        this.addRenderableWidget(this.manaButton);

        // 2. Cooldown Toggle Button
        this.cooldownButton = Button.builder(getCooldownButtonText(), btn -> {
            this.noCooldown = !this.noCooldown;
            btn.setMessage(getCooldownButtonText());
        })
        .bounds(centerX, startY + 28, buttonWidth, buttonHeight)
        .tooltip(Tooltip.create(Component.translatable("gui.magic_and_sorcery.config.no_cooldown.tooltip")))
        .build();
        this.addRenderableWidget(this.cooldownButton);

        // 3. Action Buttons (Done / Cancel)
        int actionY = Math.min(this.height - 32, startY + 130);
        int actionBtnWidth = 126;

        Button doneButton = Button.builder(CommonComponents.GUI_DONE, btn -> saveAndClose())
                .bounds(centerX, actionY, actionBtnWidth, buttonHeight)
                .build();
        this.addRenderableWidget(doneButton);

        Button cancelButton = Button.builder(CommonComponents.GUI_CANCEL, btn -> cancelAndClose())
                .bounds(centerX + buttonWidth - actionBtnWidth, actionY, actionBtnWidth, buttonHeight)
                .build();
        this.addRenderableWidget(cancelButton);
    }

    private Component getManaButtonText() {
        Component state = this.infiniteMana
                ? Component.translatable("gui.magic_and_sorcery.config.status.no_consumption").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.magic_and_sorcery.config.status.normal").withStyle(ChatFormatting.RED);
        return Component.translatable("gui.magic_and_sorcery.config.infinite_mana.label").append(": ").append(state);
    }

    private Component getCooldownButtonText() {
        Component state = this.noCooldown
                ? Component.translatable("gui.magic_and_sorcery.config.status.disabled").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.magic_and_sorcery.config.status.normal").withStyle(ChatFormatting.RED);
        return Component.translatable("gui.magic_and_sorcery.config.no_cooldown.label").append(": ").append(state);
    }

    private void saveAndClose() {
        // Save to config file
        ModConfigs.INFINITE_MANA_IN_CREATIVE.set(this.infiniteMana);
        ModConfigs.NO_COOLDOWN_IN_CREATIVE.set(this.noCooldown);
        ModConfigs.SPEC.save();

        // Sync with server if currently in-game
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            ModNetwork.sendToServer(new PacketUpdateCreativeConfig(this.infiniteMana, this.noCooldown));
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void cancelAndClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        cancelAndClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Header Title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);

        // Category Subtitle
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.magic_and_sorcery.config.creative_section").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                this.width / 2, 45, 0xFFD700);

        // Informational Note Card
        int cardWidth = 280;
        int cardHeight = 44;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = 124;

        // Dark background and golden outline for the note card
        guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0x77000000);
        guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + 1, 0xFFFFAA00);
        guiGraphics.fill(cardX, cardY + cardHeight - 1, cardX + cardWidth, cardY + cardHeight, 0xFFFFAA00);
        guiGraphics.fill(cardX, cardY, cardX + 1, cardY + cardHeight, 0xFFFFAA00);
        guiGraphics.fill(cardX + cardWidth - 1, cardY, cardX + cardWidth, cardY + cardHeight, 0xFFFFAA00);

        // Note Card Text Lines
        Component noteHeader = Component.translatable("gui.magic_and_sorcery.config.note_header").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        Component noteLine1 = Component.translatable("gui.magic_and_sorcery.config.note_line1").withStyle(ChatFormatting.GRAY);
        Component noteLine2 = Component.translatable("gui.magic_and_sorcery.config.note_line2").withStyle(ChatFormatting.DARK_GRAY);

        guiGraphics.drawCenteredString(this.font, noteHeader, this.width / 2, cardY + 5, 0xFFFF55);
        guiGraphics.drawCenteredString(this.font, noteLine1, this.width / 2, cardY + 18, 0xDDDDDD);
        guiGraphics.drawCenteredString(this.font, noteLine2, this.width / 2, cardY + 29, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
