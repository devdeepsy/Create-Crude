package com.deepu.create_crude.client;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom RenderType for "ghost" blocks. 
 * Essential for transparency (translucency) and disabling depth writing 
 * so it appears as an overlay.
 */
public class GhostRenderType extends RenderType {

    public GhostRenderType(String p_173178_, VertexFormat p_173179_, VertexFormat.Mode p_173180_, int p_173181_, boolean p_173182_, boolean p_173183_, Runnable p_173184_, Runnable p_173185_) {
        super(p_173178_, p_173179_, p_173180_, p_173181_, p_173182_, p_173183_, p_173184_, p_173185_);
    }

    // This creates a standard translucent type but tweaks depth to act as an overlay.
    // Replace "minecraft:textures/atlas/blocks.png" with the correct blocks atlas.
    public static final RenderType GHOST_BLOCK = create(
            "ghost_block",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            256,
            true, // useDelegate
            true, // needsSorting
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    // The standard blocks atlas location
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    // CRITICAL: Disable depth writing so the ghost block appears slightly in front/on top
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false)
    );
}