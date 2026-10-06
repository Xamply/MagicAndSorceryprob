package com.cesar.magicandsorcery.client;

import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.catalyst.ICatalyst;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketReorderSpells;
import com.cesar.magicandsorcery.network.packets.PacketSelectSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientMagicData {
    private static float mana = 100.0f;
    private static float maxMana = 100.0f;
    private static float manaRegen = 2.0f;
    private static int spellCapacity = 4;
    private static int selectedSpellIndex = 0;
    private static final List<ResourceLocation> learnedSpells = new ArrayList<>();
    private static final List<ResourceLocation> preparedSpells = new ArrayList<>();
    private static final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();

    public static void setStats(float currentMana, float maximumMana, float currentRegen, int capacity, int selectedIndex, List<ResourceLocation> spells, List<ResourceLocation> learned, Map<ResourceLocation, Integer> cds) {
        mana = currentMana;
        maxMana = maximumMana;
        manaRegen = currentRegen;
        spellCapacity = capacity;
        selectedSpellIndex = selectedIndex;
        preparedSpells.clear();
        preparedSpells.addAll(spells);
        while (preparedSpells.size() < 9) {
            preparedSpells.add(null);
        }
        learnedSpells.clear();
        if (learned != null) {
            learnedSpells.addAll(learned);
        }
        if (learnedSpells.isEmpty()) {
            for (Spell s : ModSpells.getAllSpells()) {
                if (!learnedSpells.contains(s.getId())) {
                    learnedSpells.add(s.getId());
                }
            }
        }
        cooldowns.clear();
        cooldowns.putAll(cds);
    }

    public static float getMana() {
        return mana;
    }

    public static float getMaxMana() {
        return maxMana;
    }

    public static float getManaRegen() {
        return manaRegen;
    }

    public static int getSpellCapacity() {
        return spellCapacity;
    }

    public static int getSelectedSpellIndex() {
        return selectedSpellIndex;
    }

    public static List<ResourceLocation> getPreparedSpells() {
        while (preparedSpells.size() < 9) {
            preparedSpells.add(null);
        }
        return preparedSpells;
    }

    public static List<ResourceLocation> getLearnedSpells() {
        if (learnedSpells.isEmpty()) {
            for (Spell s : ModSpells.getAllSpells()) {
                if (!learnedSpells.contains(s.getId())) {
                    learnedSpells.add(s.getId());
                }
            }
        }
        return learnedSpells;
    }

    public static Spell getSelectedSpell() {
        if (preparedSpells.isEmpty() || selectedSpellIndex < 0 || selectedSpellIndex >= preparedSpells.size()) {
            return null;
        }
        ResourceLocation id = preparedSpells.get(selectedSpellIndex);
        return id != null ? ModSpells.getSpell(id) : null;
    }

    public static Spell getSpellAtSlot(int slot) {
        if (slot >= 0 && slot < preparedSpells.size()) {
            ResourceLocation id = preparedSpells.get(slot);
            return id != null ? ModSpells.getSpell(id) : null;
        }
        return null;
    }

    public static int getSpellCooldown(ResourceLocation spellId) {
        return cooldowns.getOrDefault(spellId, 0);
    }

    public static boolean isSpellOnCooldown(ResourceLocation spellId) {
        return cooldowns.getOrDefault(spellId, 0) > 0;
    }

    /**
     * Determines current active catalyst method on client side for HUD previews.
     */
    public static CastingMethod getCurrentCastingMethod() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return CastingMethod.BARE_HANDS;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof ICatalyst catalyst) {
            return catalyst.getCastingMethod(mainHand);
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof ICatalyst catalyst) {
            return catalyst.getCastingMethod(offHand);
        }
        return CastingMethod.BARE_HANDS;
    }

    // --- CHANNELING STATE ---
    private static boolean isChanneling = false;
    private static boolean isReadyToCast = false;
    private static int channelTicks = 0;
    private static int totalChannelTicks = 0;
    private static Spell channelingSpell = null;
    private static CastingMethod channelingMethod = CastingMethod.BARE_HANDS;
    private static boolean channeledViaKey = false;
    private static int channelingSelectedSlot = -1;
    private static ItemStack channelingItemStack = ItemStack.EMPTY;

    public static void startChanneling(Spell spell, CastingMethod method, boolean viaKey) {
        channelingSpell = spell;
        channelingMethod = method;
        totalChannelTicks = Math.max(1, spell.calculateFinalCastTime(method));
        channelTicks = 0;
        isChanneling = true;
        isReadyToCast = false;
        channeledViaKey = viaKey;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            channelingSelectedSlot = mc.player.getInventory().selected;
            channelingItemStack = mc.player.getMainHandItem().copy();
            if (mc.player.getMainHandItem().getItem() instanceof ICatalyst) {
                mc.player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            } else if (mc.player.getOffhandItem().getItem() instanceof ICatalyst) {
                mc.player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            }
        }
    }

    public static void tickChanneling() {
        if (!isChanneling) return;
        if (channelTicks < totalChannelTicks) {
            channelTicks++;
            if (channelTicks >= totalChannelTicks) {
                isReadyToCast = true;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
                }
            }
        }
    }

    public static void finishChannelingSuccess() {
        com.cesar.magicandsorcery.client.render.WandAnimation.onCastReleased();
        if (channelingSpell != null && channelingSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell.ID)) {
            Minecraft mcInstance = Minecraft.getInstance();
            if (mcInstance.player != null) {
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketRedshaChannel(
                        com.cesar.magicandsorcery.network.packets.PacketRedshaChannel.ACTION_CANCEL
                ));
            }
        }
        isChanneling = false;
        isReadyToCast = false;
        channelTicks = 0;
        totalChannelTicks = 0;
        channelingSpell = null;
        channelingSelectedSlot = -1;
        channelingItemStack = ItemStack.EMPTY;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
    }

    public static void cancelChanneling() {
        if (channelingSpell != null && channelingSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.ID)) {
            Minecraft mcInstance = Minecraft.getInstance();
            if (mcInstance.player != null) {
                com.cesar.magicandsorcery.client.render.ClientThundajaRenderer.cancelStorm(mcInstance.player.getId());
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketThundajaChannel(
                        com.cesar.magicandsorcery.network.packets.PacketThundajaChannel.ACTION_CANCEL,
                        net.minecraft.world.phys.Vec3.ZERO
                ));
            }
        }
        if (channelingSpell != null && channelingSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.DivineSwordSpell.ID)) {
            Minecraft mcInstance = Minecraft.getInstance();
            if (mcInstance.player != null) {
                com.cesar.magicandsorcery.client.render.ClientDivineSwordRenderer.cancelChannel(mcInstance.player.getId());
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel(
                        com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel.ACTION_CANCEL
                ));
            }
        }
        if (channelingSpell != null && channelingSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell.ID)) {
            Minecraft mcInstance = Minecraft.getInstance();
            if (mcInstance.player != null) {
                com.cesar.magicandsorcery.client.render.ClientFallingSwordRenderer.cancelChannel(mcInstance.player.getId());
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel(
                        com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel.ACTION_CANCEL,
                        net.minecraft.world.phys.Vec3.ZERO
                ));
            }
        }
        if (channelingSpell != null && channelingSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell.ID)) {
            Minecraft mcInstance = Minecraft.getInstance();
            if (mcInstance.player != null) {
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketRedshaChannel(
                        com.cesar.magicandsorcery.network.packets.PacketRedshaChannel.ACTION_CANCEL
                ));
            }
        }
        if (channelingSpell != null && channelingSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.IteratusSpell.ID)) {
            if (isReadyToCast) {
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketIteratusEnd());
            }
        }

        isChanneling = false;
        isReadyToCast = false;
        channelTicks = 0;
        totalChannelTicks = 0;
        channelingSpell = null;
        channelingSelectedSlot = -1;
        channelingItemStack = ItemStack.EMPTY;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
    }

    public static int getChannelingSelectedSlot() {
        return channelingSelectedSlot;
    }

    public static ItemStack getChannelingItemStack() {
        return channelingItemStack;
    }

    // --- RADIAL MENU STATE ---
    private static boolean isRadialMenuOpen = false;
    private static float lockedYaw = 0.0f;
    private static float lockedPitch = 0.0f;
    private static double lastMouseX = 0.0;
    private static double lastMouseY = 0.0;
    private static float radialAimX = 0.0f;
    private static float radialAimY = 0.0f;
    private static int radialHoveredIndex = -1;
    private static long radialOpenedAtMs = 0L;

    // --- DRAG AND DROP DUAL-SOURCE STATE ---
    public enum DragSource {
        NONE,
        LIBRARY,
        WHEEL
    }

    private static DragSource dragSource = DragSource.NONE;
    private static ResourceLocation draggedSpellId = null;
    private static int draggedSlotIndex = -1;

    // --- LIBRARY SCROLL & HOVER STATE ---
    private static float libraryScrollOffset = 0.0f;
    private static boolean isDraggingScrollBar = false;
    private static ResourceLocation libraryHoveredSpellId = null;

    public static boolean isDragging() {
        return dragSource != DragSource.NONE;
    }

    public static DragSource getDragSource() {
        return dragSource;
    }

    public static int getDraggedSlotIndex() {
        return draggedSlotIndex;
    }

    public static ResourceLocation getDraggedSpellId() {
        return draggedSpellId;
    }

    public static Spell getDraggedSpell() {
        if (dragSource == DragSource.LIBRARY) {
            return draggedSpellId != null ? ModSpells.getSpell(draggedSpellId) : null;
        } else if (dragSource == DragSource.WHEEL) {
            if (draggedSlotIndex >= 0 && draggedSlotIndex < preparedSpells.size()) {
                ResourceLocation id = preparedSpells.get(draggedSlotIndex);
                return id != null ? ModSpells.getSpell(id) : null;
            }
        }
        return null;
    }

    public static void startDragging(int slot) {
        startDraggingFromWheel(slot);
    }

    public static void startDraggingFromWheel(int slot) {
        if (slot >= 0 && slot < 9 && getSpellAtSlot(slot) != null) {
            dragSource = DragSource.WHEEL;
            draggedSlotIndex = slot;
            draggedSpellId = preparedSpells.get(slot);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
            }
        }
    }

    public static void startDraggingFromLibrary(ResourceLocation spellId) {
        if (spellId != null && ModSpells.getSpell(spellId) != null) {
            dragSource = DragSource.LIBRARY;
            draggedSpellId = spellId;
            draggedSlotIndex = -1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
            }
        }
    }

    public static void cancelDragging() {
        if (dragSource != DragSource.NONE) {
            dragSource = DragSource.NONE;
            draggedSlotIndex = -1;
            draggedSpellId = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.8f);
            }
        }
    }

    public static void commitDrag(int targetSlot) {
        if (dragSource == DragSource.NONE) return;
        if (targetSlot < 0 || targetSlot >= 9) {
            cancelDragging();
            return;
        }

        while (preparedSpells.size() < 9) {
            preparedSpells.add(null);
        }

        if (dragSource == DragSource.WHEEL) {
            int from = draggedSlotIndex;
            dragSource = DragSource.NONE;
            draggedSlotIndex = -1;
            draggedSpellId = null;

            if (from < 0 || from >= 9 || from == targetSlot) {
                return;
            }

            ResourceLocation spellFrom = preparedSpells.get(from);
            if (spellFrom == null) return;
            ResourceLocation spellTo = preparedSpells.get(targetSlot);

            preparedSpells.set(targetSlot, spellFrom);
            preparedSpells.set(from, spellTo);

            if (selectedSpellIndex == from) {
                selectedSpellIndex = targetSlot;
            } else if (selectedSpellIndex == targetSlot && spellTo != null) {
                selectedSpellIndex = from;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, 0.7f, 1.1f);
            }

            ModNetwork.sendToServer(new PacketReorderSpells(from, targetSlot));
        } else if (dragSource == DragSource.LIBRARY) {
            ResourceLocation toAssign = draggedSpellId;
            dragSource = DragSource.NONE;
            draggedSlotIndex = -1;
            draggedSpellId = null;

            if (toAssign == null) return;

            // Clear from other slots to prevent duplicates on the wheel
            for (int i = 0; i < 9; i++) {
                if (i != targetSlot && toAssign.equals(preparedSpells.get(i))) {
                    preparedSpells.set(i, null);
                }
            }

            preparedSpells.set(targetSlot, toAssign);
            if (preparedSpells.get(selectedSpellIndex) == null) {
                selectedSpellIndex = targetSlot;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, 0.7f, 1.1f);
            }

            ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketAssignSpell(targetSlot, toAssign));
        }
    }

    public static void commitUnequip(int slot) {
        if (slot >= 0 && slot < 9 && slot < preparedSpells.size()) {
            preparedSpells.set(slot, null);
            fixSelectedSpellIndex();

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 0.8f);
            }

            ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketAssignSpell(slot, null));
        }
        dragSource = DragSource.NONE;
        draggedSlotIndex = -1;
        draggedSpellId = null;
    }

    public static void fixSelectedSpellIndex() {
        while (preparedSpells.size() < 9) {
            preparedSpells.add(null);
        }
        if (selectedSpellIndex >= 0 && selectedSpellIndex < 9 && preparedSpells.get(selectedSpellIndex) != null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (preparedSpells.get(i) != null) {
                selectedSpellIndex = i;
                return;
            }
        }
        selectedSpellIndex = 0;
    }

    // --- LIBRARY GETTERS / SETTERS ---
    public static float getLibraryScrollOffset() {
        return libraryScrollOffset;
    }

    public static void setLibraryScrollOffset(float offset) {
        libraryScrollOffset = Math.max(0.0f, offset);
    }

    public static void scrollLibrary(float delta, int maxScroll) {
        libraryScrollOffset = Math.max(0.0f, Math.min(maxScroll, libraryScrollOffset + delta));
    }

    public static boolean isDraggingScrollBar() {
        return isDraggingScrollBar;
    }

    public static void setDraggingScrollBar(boolean dragging) {
        isDraggingScrollBar = dragging;
    }

    public static ResourceLocation getLibraryHoveredSpellId() {
        return libraryHoveredSpellId;
    }

    public static void setLibraryHoveredSpellId(ResourceLocation id) {
        libraryHoveredSpellId = id;
    }

    public static void openRadialMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (isChanneling) {
            cancelChanneling();
        }
        isRadialMenuOpen = true;
        radialOpenedAtMs = net.minecraft.Util.getMillis();
        dragSource = DragSource.NONE;
        draggedSlotIndex = -1;
        draggedSpellId = null;
        isDraggingScrollBar = false;
        libraryHoveredSpellId = null;
        radialHoveredIndex = -1;
        lockedYaw = mc.player.getYRot();
        lockedPitch = mc.player.getXRot();

        // Release mouse to completely prevent turnPlayer() from running.
        mc.mouseHandler.releaseMouse();

        // Center mouse cursor in window so the radial selector starts cleanly at the center
        long windowHandle = mc.getWindow().getWindow();
        if (windowHandle != 0) {
            double winW = mc.getWindow().getWidth();
            double winH = mc.getWindow().getHeight();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(windowHandle, winW / 2.0, winH / 2.0);
        }
    }

    public static void setRadialHoveredIndex(int index) {
        radialHoveredIndex = index;
    }

    public static void cycleRadialHovered(boolean forward) {
        int spellCount = 9;
        if (radialHoveredIndex == -1) {
            radialHoveredIndex = selectedSpellIndex;
        } else {
            if (forward) {
                radialHoveredIndex = (radialHoveredIndex + 1) % spellCount;
            } else {
                radialHoveredIndex = (radialHoveredIndex - 1 + spellCount) % spellCount;
            }
        }
    }

    public static void closeAndConfirmRadialMenu() {
        if (!isRadialMenuOpen) return;
        isRadialMenuOpen = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Restore mouse grab and camera orientation
            mc.mouseHandler.grabMouse();
            mc.player.setYRot(lockedYaw);
            mc.player.setXRot(lockedPitch);
        }

        if (isDragging()) {
            cancelDragging();
        } else {
            // Normal selection
            if (radialHoveredIndex >= 0 && radialHoveredIndex < preparedSpells.size() && preparedSpells.get(radialHoveredIndex) != null) {
                ModNetwork.sendToServer(new PacketSelectSpell(radialHoveredIndex));
            }
        }
        radialHoveredIndex = -1;
    }

    public static void cancelRadialMenu() {
        if (!isRadialMenuOpen) return;
        isRadialMenuOpen = false;
        cancelDragging();
        radialHoveredIndex = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.mouseHandler.grabMouse();
            mc.player.setYRot(lockedYaw);
            mc.player.setXRot(lockedPitch);
        }
    }

    /**
     * Time the radial menu was opened, used for its opening animation.
     */
    public static long getRadialOpenedAtMs() {
        return radialOpenedAtMs;
    }

    public static boolean isRadialMenuOpen() {
        return isRadialMenuOpen;
    }

    public static float getLockedYaw() {
        return lockedYaw;
    }

    public static float getLockedPitch() {
        return lockedPitch;
    }

    public static float getRadialAimX() {
        return radialAimX;
    }

    public static float getRadialAimY() {
        return radialAimY;
    }

    public static int getRadialHoveredIndex() {
        return radialHoveredIndex;
    }

    public static boolean isChanneling() {
        return isChanneling;
    }

    public static boolean isReadyToCast() {
        return isReadyToCast;
    }

    public static int getChannelTicks() {
        return channelTicks;
    }

    public static int getTotalChannelTicks() {
        return totalChannelTicks;
    }

    public static float getChannelProgress() {
        if (totalChannelTicks <= 0) return 0.0f;
        return Math.min(1.0f, (float) channelTicks / (float) totalChannelTicks);
    }

    public static Spell getChannelingSpell() {
        return channelingSpell;
    }

    public static CastingMethod getChannelingMethod() {
        return channelingMethod;
    }

    public static boolean isChanneledViaKey() {
        return channeledViaKey;
    }
}
