package com.deepu.create_crude.client.renderer;

import com.deepu.create_crude.block.entity.SteelBasinBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

public class SteelBasinRenderer implements BlockEntityRenderer<SteelBasinBlockEntity> {

    // Correct 1.21+ standalone ModelResourceLocation usage
    public static final ModelResourceLocation MIXER_MODEL_LOC = 
        ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("createcrude", "block/steel_basin_mixer"));

    public SteelBasinRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SteelBasinBlockEntity basin, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        Level level = basin.getLevel();
        if (level == null) return;

        // Sample light from the block above so basin interior remains brightly lit
        int brightLight = LevelRenderer.getLightColor(level, basin.getBlockPos().above());

        // 1. Render Fluid inside Basin
        FluidStack fluidStack = basin.getPrimaryFluidStack();
        if (!fluidStack.isEmpty()) {
            renderFluid(basin, fluidStack, poseStack, bufferSource, brightLight);
        }

        // 2. Render Items inside Basin
        ItemStack itemStack = basin.getPrimaryItemStack();
        if (!itemStack.isEmpty()) {
            renderItem(itemStack, poseStack, bufferSource, brightLight, packedOverlay, level);
        }

        // 3. Render and Rotate Mixer
        renderSpinningMixer(basin, partialTick, poseStack, bufferSource, brightLight, packedOverlay);
    }

    private void renderSpinningMixer(SteelBasinBlockEntity basin, float partialTick, PoseStack poseStack,
                                      MultiBufferSource bufferSource, int light, int overlay) {
        poseStack.pushPose();

        poseStack.translate(0.5D, 0.0D, 0.5D);

        if (basin.isProcessing()) {
            float angle = (basin.getProcessingTicks() + partialTick) * 20.0F;
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        }

        poseStack.translate(-0.5D, 0.0D, -0.5D);

        BakedModel mixerModel = Minecraft.getInstance().getModelManager().getModel(MIXER_MODEL_LOC);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            poseStack.last(),
            bufferSource.getBuffer(RenderType.cutout()),
            null,
            mixerModel,
            1.0F, 1.0F, 1.0F,
            light,
            overlay
        );

        poseStack.popPose();
    }

    private void renderFluid(SteelBasinBlockEntity basin, FluidStack stack, PoseStack poseStack, 
                             MultiBufferSource buffer, int light) {
        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = props.getStillTexture(stack);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(stillTexture);

        int color = props.getTintColor(stack);
        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red   = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF)  / 255.0F;
        float blue  = (color & 0xFF)       / 255.0F;

        float minHeight = 0.130F;
        float maxHeight = 0.930F;
        float pct = (float) stack.getAmount() / (float) basin.getMaxFluidCapacity();
        float fluidY = minHeight + (maxHeight - minHeight) * Math.min(pct, 1.0F);

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());

        float x1 = 0.125F, x2 = 0.875F;
        float z1 = 0.125F, z2 = 0.875F;

        float u1 = sprite.getU0(), u2 = sprite.getU1();
        float v1 = sprite.getV0(), v2 = sprite.getV1();

        builder.addVertex(matrix, x1, fluidY, z2).setColor(red, green, blue, alpha).setUv(u1, v2).setLight(light);
        builder.addVertex(matrix, x2, fluidY, z2).setColor(red, green, blue, alpha).setUv(u2, v2).setLight(light);
        builder.addVertex(matrix, x2, fluidY, z1).setColor(red, green, blue, alpha).setUv(u2, v1).setLight(light);
        builder.addVertex(matrix, x1, fluidY, z1).setColor(red, green, blue, alpha).setUv(u1, v1).setLight(light);

        poseStack.popPose();
    }

    private void renderItem(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, 
                            int light, int overlay, Level level) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.15D, 0.5D);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, poseStack, buffer, level, 0);

        poseStack.popPose();
    }
}