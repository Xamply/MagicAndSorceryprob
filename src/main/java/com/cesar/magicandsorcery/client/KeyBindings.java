package com.cesar.magicandsorcery.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY_MAGIC = "key.category.magic_and_sorcery";

    public static final KeyMapping KEY_SPELL_MENU = new KeyMapping(
            "key.magic_and_sorcery.spell_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY_MAGIC
    );

    public static final KeyMapping KEY_CAST_SPELL = new KeyMapping(
            "key.magic_and_sorcery.cast",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            KEY_CATEGORY_MAGIC
    );

    public static final KeyMapping KEY_NEXT_SPELL = new KeyMapping(
            "key.magic_and_sorcery.next_spell",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY_MAGIC
    );

    public static final KeyMapping KEY_PREV_SPELL = new KeyMapping(
            "key.magic_and_sorcery.prev_spell",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY_MAGIC
    );
}
