package com.cesar.magicandsorcery.client;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.client.gui.SpellRadialMenuScreen;
import com.cesar.magicandsorcery.client.hud.MagicHudOverlay;
import com.cesar.magicandsorcery.client.hud.RadialMenuRenderer;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.catalyst.ICatalyst;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.network.ModNetwork;
import com.cesar.magicandsorcery.network.packets.PacketCastSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientMagicHandler {
    private static int iteratusFireTimer = 0;
    private static boolean iteratusActiveFiring = false;

    @Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.KEY_SPELL_MENU);
            event.register(KeyBindings.KEY_CAST_SPELL);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("magic_hud", MagicHudOverlay.HUD_MAGIC);
        }
    }

    @Mod.EventBusSubscriber(modid = MagicAndSorcery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBusEvents {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Spell Radial Menu (R)
            int menuKey = KeyBindings.KEY_SPELL_MENU.getKey().getValue();
            if (event.getKey() == menuKey) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    if (mc.screen == null) {
                        ClientMagicData.openRadialMenu();
                    }
                } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                    if (ClientMagicData.isRadialMenuOpen()) {
                        ClientMagicData.closeAndConfirmRadialMenu();
                    }
                }
                return;
            }

            if (mc.screen != null) {
                if (ClientMagicData.isRadialMenuOpen()) {
                    ClientMagicData.cancelRadialMenu();
                }
                return;
            }

            // Cast Spell Key (F)
            int castKey = KeyBindings.KEY_CAST_SPELL.getKey().getValue();
            if (event.getKey() == castKey) {
                while (mc.options.keySwapOffhand.consumeClick()) {}

                if (event.getAction() == GLFW.GLFW_PRESS) {
                    if (!ClientMagicData.isChanneling() && !ClientMagicData.isRadialMenuOpen()) {
                        tryStartCasting(true);
                    }
                } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                    if (ClientMagicData.isChanneling() && ClientMagicData.isChanneledViaKey()) {
                        handleRelease();
                    }
                }
            }

            // Check hotbar keys 1-9 while channeling (interrupt channeling)
            if (ClientMagicData.isChanneling() && event.getAction() == GLFW.GLFW_PRESS) {
                for (net.minecraft.client.KeyMapping hotbarKey : mc.options.keyHotbarSlots) {
                    if (hotbarKey.matches(event.getKey(), event.getScanCode())) {
                        ClientMagicData.cancelChanneling();
                        mc.player.displayClientMessage(
                                Component.translatable("message.magic_and_sorcery.channeling_canceled").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC),
                                true
                        );
                        break;
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton event) {
            if (ClientMagicData.isRadialMenuOpen()) {
                // Prevent mouse clicks from punching/breaking blocks while radial menu is open
                event.setCanceled(true);

                Minecraft mc = Minecraft.getInstance();
                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                double windowW = Math.max(1.0, (double) mc.getWindow().getWidth());
                double windowH = Math.max(1.0, (double) mc.getWindow().getHeight());
                double mouseX = mc.mouseHandler.xpos() * (double) screenWidth / windowW;
                double mouseY = mc.mouseHandler.ypos() * (double) screenHeight / windowH;

                if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (event.getAction() == GLFW.GLFW_PRESS) {
                        // 1. Check Scrollbar click in Library
                        int libX = RadialMenuRenderer.getLibraryX(screenWidth);
                        int listY = RadialMenuRenderer.getLibraryListY(screenHeight);
                        int listHeight = RadialMenuRenderer.getLibraryListHeight(screenHeight);
                        int sbX = RadialMenuRenderer.getLibraryScrollBarX(screenWidth);
                        int sbWidth = RadialMenuRenderer.LIBRARY_SCROLLBAR_WIDTH;

                        if (mouseX >= sbX - 2 && mouseX <= sbX + sbWidth + 2 && mouseY >= listY && mouseY <= listY + listHeight) {
                            ClientMagicData.setDraggingScrollBar(true);
                            return;
                        }

                        // 2. Check Library Item click to start dragging from library
                        if (mouseX >= libX + 2 && mouseX < sbX - 2 && mouseY >= listY && mouseY < listY + listHeight) {
                            java.util.List<net.minecraft.resources.ResourceLocation> learned = ClientMagicData.getLearnedSpells();
                            int itemHeight = RadialMenuRenderer.LIBRARY_ITEM_HEIGHT;
                            int relY = (int) (mouseY - listY + ClientMagicData.getLibraryScrollOffset());
                            int clickedIndex = relY / itemHeight;
                            if (clickedIndex >= 0 && clickedIndex < learned.size()) {
                                ClientMagicData.startDraggingFromLibrary(learned.get(clickedIndex));
                                return;
                            }
                        }

                        // 3. Check Wheel Slot click to start dragging from wheel
                        int hovered = RadialMenuRenderer.calculateHoveredSlot(mc);
                        if (hovered >= 0 && hovered < 9 && ClientMagicData.getSpellAtSlot(hovered) != null) {
                            ClientMagicData.startDraggingFromWheel(hovered);
                            return;
                        }
                    } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                        if (ClientMagicData.isDraggingScrollBar()) {
                            ClientMagicData.setDraggingScrollBar(false);
                            return;
                        }

                        if (ClientMagicData.isDragging()) {
                            int hovered = RadialMenuRenderer.calculateHoveredSlot(mc);
                            if (hovered >= 0 && hovered < 9) {
                                // Dropped onto wheel slot -> commit assign or swap
                                ClientMagicData.commitDrag(hovered);
                            } else if (ClientMagicData.getDragSource() == ClientMagicData.DragSource.WHEEL) {
                                // Dropped outside wheel or over library -> unequip from wheel
                                ClientMagicData.commitUnequip(ClientMagicData.getDraggedSlotIndex());
                            } else {
                                ClientMagicData.cancelDragging();
                            }
                        }
                    }
                } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (event.getAction() == GLFW.GLFW_PRESS) {
                        // Quick unequip via Right-Click on a wheel slot
                        int hovered = RadialMenuRenderer.calculateHoveredSlot(mc);
                        if (hovered >= 0 && hovered < 9 && ClientMagicData.getSpellAtSlot(hovered) != null) {
                            ClientMagicData.commitUnequip(hovered);
                        }
                    }
                }
                return;
            }

            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (event.getAction() == GLFW.GLFW_RELEASE) {
                    if (ClientMagicData.isChanneling() && !ClientMagicData.isChanneledViaKey()) {
                        handleRelease();
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            if (ClientMagicData.isRadialMenuOpen()) {
                event.setCanceled(true);
                double delta = event.getScrollDelta();
                if (delta != 0) {
                    Minecraft mc = Minecraft.getInstance();
                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();
                    double windowW = Math.max(1.0, (double) mc.getWindow().getWidth());
                    double windowH = Math.max(1.0, (double) mc.getWindow().getHeight());
                    double mouseX = mc.mouseHandler.xpos() * (double) screenWidth / windowW;
                    double mouseY = mc.mouseHandler.ypos() * (double) screenHeight / windowH;

                    if (RadialMenuRenderer.isMouseInsideLibrary(mouseX, mouseY, screenWidth, screenHeight)) {
                        // Scroll Library list
                        int itemHeight = RadialMenuRenderer.LIBRARY_ITEM_HEIGHT;
                        int listHeight = RadialMenuRenderer.getLibraryListHeight(screenHeight);
                        int totalH = ClientMagicData.getLearnedSpells().size() * itemHeight;
                        int maxScroll = Math.max(0, totalH - listHeight);
                        ClientMagicData.scrollLibrary((float) (-delta * itemHeight), maxScroll);
                    } else {
                        // Cycle radial wheel hovered slot
                        ClientMagicData.cycleRadialHovered(delta > 0);
                    }
                }
            } else if (ClientMagicData.isChanneling()) {
                // Interrupt channeling immediately on mouse scroll slot switch
                ClientMagicData.cancelChanneling();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.magic_and_sorcery.channeling_canceled").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC),
                            true
                    );
                }
            }
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (event.getLevel().isClientSide() && event.getItemStack().getItem() instanceof ICatalyst) {
                if (!ClientMagicData.isChanneling() && !ClientMagicData.isRadialMenuOpen()) {
                    tryStartCasting(false);
                }
            }
        }

        @SubscribeEvent
        public static void onComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
            if (ClientMagicData.isRadialMenuOpen()) {
                // Lock rendered camera angle completely while radial menu is held
                event.setYaw(ClientMagicData.getLockedYaw());
                event.setPitch(ClientMagicData.getLockedPitch());
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                if (ClientMagicData.isChanneling()) {
                    ClientMagicData.cancelChanneling();
                }
                if (ClientMagicData.isRadialMenuOpen()) {
                    ClientMagicData.cancelRadialMenu();
                }
                iteratusActiveFiring = false;
                iteratusFireTimer = 0;
                return;
            }

            if (mc.screen != null) {
                if (ClientMagicData.isChanneling()) {
                    ClientMagicData.cancelChanneling();
                }
                if (ClientMagicData.isRadialMenuOpen()) {
                    ClientMagicData.cancelRadialMenu();
                }
                iteratusActiveFiring = false;
                iteratusFireTimer = 0;
                return;
            }

            // Radial menu logic while R is held down
            if (ClientMagicData.isRadialMenuOpen()) {
                if (!KeyBindings.KEY_SPELL_MENU.isDown()) {
                    ClientMagicData.closeAndConfirmRadialMenu();
                } else {
                    // Lock player entity orientation so player moves in the fixed direction
                    mc.player.setYRot(ClientMagicData.getLockedYaw());
                    mc.player.setXRot(ClientMagicData.getLockedPitch());
                    mc.player.yRotO = ClientMagicData.getLockedYaw();
                    mc.player.xRotO = ClientMagicData.getLockedPitch();
                    mc.player.yHeadRot = ClientMagicData.getLockedYaw();
                    mc.player.yHeadRotO = ClientMagicData.getLockedYaw();
                    mc.player.yBodyRot = ClientMagicData.getLockedYaw();
                    mc.player.yBodyRotO = ClientMagicData.getLockedYaw();
                }
            }

            if (ClientMagicData.isChanneling()) {
                // Check if player changed selected slot or held item: interrupt channeling!
                int currentSlot = mc.player.getInventory().selected;
                net.minecraft.world.item.ItemStack currentItem = mc.player.getMainHandItem();
                if (currentSlot != ClientMagicData.getChannelingSelectedSlot() ||
                        !net.minecraft.world.item.ItemStack.matches(currentItem, ClientMagicData.getChannelingItemStack())) {
                    ClientMagicData.cancelChanneling();
                    iteratusActiveFiring = false;
                    iteratusFireTimer = 0;
                    mc.player.displayClientMessage(
                            Component.translatable("message.magic_and_sorcery.channeling_canceled").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC),
                            true
                    );
                    return;
                }

                // Advance channel progress tick independently of movement
                ClientMagicData.tickChanneling();

                // Iteratus continuous streaming logic
                Spell chSpell = ClientMagicData.getChannelingSpell();
                if (chSpell != null && chSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.IteratusSpell.ID)) {
                    if (ClientMagicData.isReadyToCast()) {
                        boolean isCreative = mc.player.isCreative();
                        boolean bypassMana = isCreative && com.cesar.magicandsorcery.config.ModConfigs.INFINITE_MANA_IN_CREATIVE.get();

                        if (!iteratusActiveFiring) {
                            // Initial 1.5s preparation complete! Fire missile 1 immediately!
                            iteratusActiveFiring = true;
                            if (!bypassMana && ClientMagicData.getMana() < 10.0f) {
                                mc.player.displayClientMessage(
                                        Component.translatable("message.magic_and_sorcery.not_enough_mana").withStyle(ChatFormatting.DARK_AQUA),
                                        true
                                );
                                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketIteratusEnd());
                                ClientMagicData.cancelChanneling();
                                iteratusActiveFiring = false;
                                return;
                            }
                            ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketIteratusFire());
                            iteratusFireTimer = 20; // 1 second (20 ticks) until next missile
                        } else {
                            // Continuous stream: 1 missile per second
                            iteratusFireTimer--;
                            if (iteratusFireTimer <= 0) {
                                if (!bypassMana && ClientMagicData.getMana() < 10.0f) {
                                    mc.player.displayClientMessage(
                                            Component.translatable("message.magic_and_sorcery.not_enough_mana").withStyle(ChatFormatting.DARK_AQUA),
                                            true
                                    );
                                    ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketIteratusEnd());
                                    ClientMagicData.cancelChanneling();
                                    iteratusActiveFiring = false;
                                    return;
                                }
                                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketIteratusFire());
                                iteratusFireTimer = 20;
                            }
                        }
                    }
                }

                // Continuous particles around player while charging
                if (mc.player.tickCount % 2 == 0) {
                    double px = mc.player.getX() + (Math.random() - 0.5) * 1.2;
                    double py = mc.player.getY() + Math.random() * 1.8;
                    double pz = mc.player.getZ() + (Math.random() - 0.5) * 1.2;
                    if (ClientMagicData.isReadyToCast()) {
                        mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0, 0.04, 0);
                    } else {
                        mc.level.addParticle(ParticleTypes.ENCHANT, px, py, pz, (Math.random() - 0.5) * 0.2, 0.1, (Math.random() - 0.5) * 0.2);
                    }
                }
            }
        }
    }

    public static void tryStartCasting(boolean viaKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Spell spell = ClientMagicData.getSelectedSpell();
        if (spell == null) {
            mc.player.displayClientMessage(
                    Component.translatable("message.magic_and_sorcery.no_spell_selected").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        CastingMethod method = ClientMagicData.getCurrentCastingMethod();

        boolean isCreative = mc.player != null && mc.player.isCreative();
        boolean bypassCooldown = isCreative && com.cesar.magicandsorcery.config.ModConfigs.NO_COOLDOWN_IN_CREATIVE.get();
        boolean bypassMana = isCreative && com.cesar.magicandsorcery.config.ModConfigs.INFINITE_MANA_IN_CREATIVE.get();

        // Check Cooldown
        if (!bypassCooldown && ClientMagicData.isSpellOnCooldown(spell.getId())) {
            int cdTicks = ClientMagicData.getSpellCooldown(spell.getId());
            float cdSec = cdTicks / 20.0f;
            mc.player.displayClientMessage(
                    Component.translatable("message.magic_and_sorcery.on_cooldown", String.format("%.1f", cdSec)).withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        // Check Mana
        float finalCost = bypassMana ? 0.0f : spell.calculateFinalManaCost(method);
        if (!bypassMana && ClientMagicData.getMana() < finalCost) {
            mc.player.displayClientMessage(
                    Component.translatable("message.magic_and_sorcery.not_enough_mana").withStyle(ChatFormatting.DARK_AQUA),
                    true
            );
            return;
        }

        // Start channeling
        iteratusActiveFiring = false;
        iteratusFireTimer = 0;
        ClientMagicData.startChanneling(spell, method, viaKey);

        if (spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.ID) && mc.player != null && mc.level != null) {
            Vec3 target = com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.findThundajaGroundTarget(
                    mc.level, mc.player, spell.getRange());

            // Notify local renderer immediately
            com.cesar.magicandsorcery.client.render.ClientThundajaRenderer.startStorm(mc.player.getId(), target);
            // Notify server so other players see the storm and server knows the target
            ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketThundajaChannel(
                    com.cesar.magicandsorcery.network.packets.PacketThundajaChannel.ACTION_START,
                    target
            ));
        }

        if (spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.DivineSwordSpell.ID) && mc.player != null) {
            com.cesar.magicandsorcery.client.render.ClientDivineSwordRenderer.startChannel(mc.player.getId());
            ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel(
                    com.cesar.magicandsorcery.network.packets.PacketDivineSwordChannel.ACTION_START
            ));
        }

        if (spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell.ID) && mc.player != null && mc.level != null) {
            Vec3 target = com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell.findGroundTarget(
                    mc.level, mc.player, spell.getRange());
            com.cesar.magicandsorcery.client.render.ClientFallingSwordRenderer.startChannel(mc.player.getId(), target);
            ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel(
                    com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel.ACTION_START,
                    target
            ));
        }

        if (spell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell.ID) && mc.player != null) {
            ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketRedshaChannel(
                    com.cesar.magicandsorcery.network.packets.PacketRedshaChannel.ACTION_START
            ));
        }

        int castTime = spell.calculateFinalCastTime(method);
        if (castTime <= 0) {
            // Instant spell triggers immediately on press!
            ClientMagicData.tickChanneling();
            ModNetwork.sendToServer(new PacketCastSpell());
            ClientMagicData.finishChannelingSuccess();
        }
    }

    public static void handleRelease() {
        if (!ClientMagicData.isChanneling()) return;
        Minecraft mc = Minecraft.getInstance();

        if (ClientMagicData.isReadyToCast()) {
            Spell chSpell = ClientMagicData.getChannelingSpell();
            if (chSpell != null && chSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.IteratusSpell.ID)) {
                // Key released: stop continuous missile stream and start 10s cooldown
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketIteratusEnd());
                iteratusActiveFiring = false;
                iteratusFireTimer = 0;
                ClientMagicData.finishChannelingSuccess();
                return;
            }

            if (chSpell != null && chSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell.ID) && mc.player != null && mc.level != null) {
                Vec3 finalTarget = com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell.findGroundTarget(
                        mc.level, mc.player, chSpell.getRange());
                com.cesar.magicandsorcery.client.render.ClientFallingSwordRenderer.updateChannel(mc.player.getId(), finalTarget);
                com.cesar.magicandsorcery.client.render.ClientFallingSwordRenderer.markReleased(mc.player.getId());
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel(
                        com.cesar.magicandsorcery.network.packets.PacketFallingSwordChannel.ACTION_UPDATE,
                        finalTarget
                ));
            }

            if (chSpell != null && chSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.ID) && mc.player != null && mc.level != null) {
                Vec3 finalTarget = com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell.findThundajaGroundTarget(
                        mc.level, mc.player, chSpell.getRange());
                com.cesar.magicandsorcery.client.render.ClientThundajaRenderer.updateStorm(mc.player.getId(), finalTarget);
                ModNetwork.sendToServer(new com.cesar.magicandsorcery.network.packets.PacketThundajaChannel(
                        com.cesar.magicandsorcery.network.packets.PacketThundajaChannel.ACTION_UPDATE,
                        finalTarget
                ));
            }

            // Successfully prepared (100%)! Cast spell upon releasing key!
            // Flash sends the destination the player was shown, so they land exactly where it was marked
            Vec3 aim = null;
            if (chSpell != null && chSpell.getId().equals(com.cesar.magicandsorcery.magic.spell.spells.FlashSpell.ID)
                    && mc.player != null && mc.level != null) {
                aim = com.cesar.magicandsorcery.magic.spell.spells.FlashSpell.findDestination(mc.level, mc.player,
                        chSpell.getRange(ClientMagicData.getChannelingMethod()));
            }
            ModNetwork.sendToServer(aim != null ? new PacketCastSpell(aim) : new PacketCastSpell());
            ClientMagicData.finishChannelingSuccess();
        } else {
            // Released early (< 100%)! Cancel cast!
            ClientMagicData.cancelChanneling();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.magic_and_sorcery.channeling_canceled").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }
}
