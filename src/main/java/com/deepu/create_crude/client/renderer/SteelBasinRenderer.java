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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Matrix4f;

public class SteelBasinRenderer implements BlockEntityRenderer<SteelBasinBlockEntity> {

    public static final ModelResourceLocation MIXER_MODEL_LOC =
        ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("createcrude", "block/steel_basin/steel_basin_mixer"));

    public SteelBasinRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SteelBasinBlockEntity basin, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        Level level = basin.getLevel();
        if (level == null) return;

        int brightLight = LevelRenderer.getLightColor(level, basin.getBlockPos().above());

        // 1. Render Mixer First (Cutout layer)
        renderSpinningMixer(basin, partialTick, poseStack, bufferSource, brightLight, packedOverlay);

        // 2. Render Floating Items
        float fluidLevel = getFluidRenderLevel(basin);
        renderFloatingItems(basin, partialTick, poseStack, bufferSource, brightLight, packedOverlay, fluidLevel);

        // 3. Render Fluid LAST (Translucent layer)
        renderFluid(basin, poseStack, bufferSource, brightLight, fluidLevel);
    }

    private void renderSpinningMixer(SteelBasinBlockEntity basin, float partialTick, PoseStack poseStack,
                                     MultiBufferSource bufferSource, int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);

        // Frame-interpolated rotation prevents ticks-reset rotational snapping
        float interpolatedAngle = Mth.lerp(partialTick, basin.prevMixerRotation, basin.mixerRotation);
        poseStack.mulPose(Axis.YP.rotationDegrees(interpolatedAngle));

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

    private float getFluidRenderLevel(SteelBasinBlockEntity basin) {
        FluidStack stack = basin.getPrimaryFluidStack();
        if (stack.isEmpty() || stack.getAmount() < 1) return 0;
        float fluidLevel = Mth.clamp((float) stack.getAmount() / Math.max(1, basin.getMaxFluidCapacity()), 0F, 1F);
        return 1 - ((1 - fluidLevel) * (1 - fluidLevel));
    }

    private void renderFluid(SteelBasinBlockEntity basin, PoseStack poseStack, MultiBufferSource buffer, int light, float fluidLevel) {
        if (fluidLevel <= 0) return;

        FluidStack stack = basin.getPrimaryFluidStack();
        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = props.getStillTexture(stack);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(stillTexture);

        int color = props.getTintColor(stack);
        if (color == 0) color = 0xFFFFFFFF;

        float alpha = (color >> 24 & 0xFF) / 255.0F;
        if (alpha == 0.0F) alpha = 1.0F;
        float red   = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF)  / 255.0F;
        float blue  = (color & 0xFF)       / 255.0F;

        final float xMin = 2 / 16f;
        final float xMax = 14 / 16f;
        final float yMin = 2 / 16f;
        final float yMax = yMin + (12 / 16f * fluidLevel);
        final float zMin = 2 / 16f;
        final float zMax = 14 / 16f;

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());

        float u1 = sprite.getU0(), u2 = sprite.getU1();
        float v1 = sprite.getV0(), v2 = sprite.getV1();

        // Top face
        builder.addVertex(matrix, xMin, yMax, zMax).setColor(red, green, blue, alpha).setUv(u1, v2).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        builder.addVertex(matrix, xMax, yMax, zMax).setColor(red, green, blue, alpha).setUv(u2, v2).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        builder.addVertex(matrix, xMax, yMax, zMin).setColor(red, green, blue, alpha).setUv(u2, v1).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        builder.addVertex(matrix, xMin, yMax, zMin).setColor(red, green, blue, alpha).setUv(u1, v1).setLight(light).setNormal(0.0F, 1.0F, 0.0F);

        // North face (-Z)
        builder.addVertex(matrix, xMin, yMin, zMin).setColor(red, green, blue, alpha).setUv(u1, v2).setLight(light).setNormal(0.0F, 0.0F, -1.0F);
        builder.addVertex(matrix, xMin, yMax, zMin).setColor(red, green, blue, alpha).setUv(u1, v1).setLight(light).setNormal(0.0F, 0.0F, -1.0F);
        builder.addVertex(matrix, xMax, yMax, zMin).setColor(red, green, blue, alpha).setUv(u2, v1).setLight(light).setNormal(0.0F, 0.0F, -1.0F);
        builder.addVertex(matrix, xMax, yMin, zMin).setColor(red, green, blue, alpha).setUv(u2, v2).setLight(light).setNormal(0.0F, 0.0F, -1.0F);

        // South face (+Z)
        builder.addVertex(matrix, xMax, yMin, zMax).setColor(red, green, blue, alpha).setUv(u2, v2).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        builder.addVertex(matrix, xMax, yMax, zMax).setColor(red, green, blue, alpha).setUv(u2, v1).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        builder.addVertex(matrix, xMin, yMax, zMax).setColor(red, green, blue, alpha).setUv(u1, v1).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        builder.addVertex(matrix, xMin, yMin, zMax).setColor(red, green, blue, alpha).setUv(u1, v2).setLight(light).setNormal(0.0F, 0.0F, 1.0F);

        // East face (+X)
        builder.addVertex(matrix, xMax, yMin, zMin).setColor(red, green, blue, alpha).setUv(u1, v2).setLight(light).setNormal(1.0F, 0.0F, 0.0F);
        builder.addVertex(matrix, xMax, yMax, zMin).setColor(red, green, blue, alpha).setUv(u1, v1).setLight(light).setNormal(1.0F, 0.0F, 0.0F);
        builder.addVertex(matrix, xMax, yMax, zMax).setColor(red, green, blue, alpha).setUv(u2, v1).setLight(light).setNormal(1.0F, 0.0F, 0.0F);
        builder.addVertex(matrix, xMax, yMin, zMax).setColor(red, green, blue, alpha).setUv(u2, v2).setLight(light).setNormal(1.0F, 0.0F, 0.0F);

        // West face (-X)
        builder.addVertex(matrix, xMin, yMin, zMax).setColor(red, green, blue, alpha).setUv(u1, v2).setLight(light).setNormal(-1.0F, 0.0F, 0.0F);
        builder.addVertex(matrix, xMin, yMax, zMax).setColor(red, green, blue, alpha).setUv(u1, v1).setLight(light).setNormal(-1.0F, 0.0F, 0.0F);
        builder.addVertex(matrix, xMin, yMax, zMin).setColor(red, green, blue, alpha).setUv(u2, v1).setLight(light).setNormal(-1.0F, 0.0F, 0.0F);
        builder.addVertex(matrix, xMin, yMin, zMin).setColor(red, green, blue, alpha).setUv(u2, v2).setLight(light).setNormal(-1.0F, 0.0F, 0.0F);

        poseStack.popPose();
    }

    private void renderFloatingItems(SteelBasinBlockEntity basin, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffer, int light, int overlay, float fluidLevel) {
        IItemHandler inv = basin.getItemHandler(null);
        if (inv == null) return;

        int itemCount = 0;
        for (int slot = 0; slot < inv.getSlots(); slot++)
            if (!inv.getStackInSlot(slot).isEmpty())
                itemCount++;

        if (itemCount == 0) return;

        float restLevel = Mth.clamp(fluidLevel - 0.3F, 0.125F, 0.6F);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.2D, 0.5D);

        // Smooth rotation matching mixer movement
        float interpolatedAngle = Mth.lerp(partialTick, basin.prevMixerRotation, basin.mixerRotation);
        poseStack.mulPose(Axis.YP.rotationDegrees(interpolatedAngle));

        RandomSource random = RandomSource.create(basin.getBlockPos().hashCode());
        Vec3 baseVector = new Vec3(0.125, restLevel, 0);
        if (itemCount == 1) baseVector = new Vec3(0, restLevel, 0);

        float anglePartition = 360f / itemCount;
        int index = 0;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            poseStack.pushPose();

            float bobAngle = anglePartition * index;
            if (fluidLevel > 0 && basin.getLevel() != null) {
                float time = (basin.getLevel().getGameTime() + partialTick);
                float bob = (Mth.sin(time / 12f + bobAngle) + 1.5f) * (1f / 32f);
                poseStack.translate(0, bob, 0);
            }

            Vec3 itemPosition = rotateY(baseVector, bobAngle);
            poseStack.translate(itemPosition.x, itemPosition.y, itemPosition.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(bobAngle + 35));
            poseStack.mulPose(Axis.XP.rotationDegrees(65));

            int stackRenderCount = 1 + stack.getCount() / 8;
            for (int i = 0; i < stackRenderCount; i++) {
                poseStack.pushPose();
                Vec3 jitter = offsetRandomly(random, 1 / 16f);
                poseStack.translate(jitter.x, jitter.y, jitter.z);
                renderItem(stack, poseStack, buffer, light, overlay, basin.getLevel());
                poseStack.popPose();
            }

            poseStack.popPose();
            index++;
        }

        poseStack.popPose();
    }

    private void renderItem(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                           int light, int overlay, Level level) {
        Minecraft.getInstance().getItemRenderer()
            .renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, poseStack, buffer, level, 0);
    }

    private static Vec3 rotateY(Vec3 vec, float degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(vec.x * cos + vec.z * sin, vec.y, vec.z * cos - vec.x * sin);
    }

    private static Vec3 offsetRandomly(RandomSource random, float maxOffset) {
        return new Vec3(
            (random.nextFloat() - 0.5F) * maxOffset,
            (random.nextFloat() - 0.5F) * maxOffset,
            (random.nextFloat() - 0.5F) * maxOffset
        );
    }
}