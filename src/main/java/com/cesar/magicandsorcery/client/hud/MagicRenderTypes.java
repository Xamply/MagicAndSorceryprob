package com.cesar.magicandsorcery.client.hud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Extra render types for the magic interface.
 * Extends RenderType only to reach its protected render-state shards.
 */
public final class MagicRenderTypes extends RenderType {

    /**
     * Additive GUI quads: colors add light on top of what is behind them, giving a soft magical glow.
     */
    public static final RenderType GUI_GLOW = create("magic_and_sorcery_gui_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_GUI_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /**
     * Regular translucent GUI quads without face culling, so shapes render regardless of winding.
     */
    public static final RenderType GUI_FLAT = create("magic_and_sorcery_gui_flat",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_GUI_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /**
     * In-world additive light (spell beams, glows, sparks). No depth writes, so overlapping light adds up.
     */
    public static final RenderType WORLD_GLOW = create("magic_and_sorcery_world_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    /**
     * In-world translucent surfaces (ice crystals, frost) that should read as solid-ish matter rather than light.
     */
    public static final RenderType WORLD_TRANSLUCENT = create("magic_and_sorcery_world_translucent",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 131072, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private MagicRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                             boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
