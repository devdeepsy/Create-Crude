package com.deepu.create_crude.client.renderer;

import com.deepu.create_crude.block.SeismicDetectorBlock;
import com.deepu.create_crude.block.entity.SeismicDetectorBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class SeismicDetectorRenderer implements BlockEntityRenderer<SeismicDetectorBlockEntity> {

    public SeismicDetectorRenderer(BlockEntityRendererProvider.Context context) {
        // Constructor required by Minecraft's BlockEntityRenderDispatcher
    }

    @Override
    public void render(SeismicDetectorBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        Direction facing = blockEntity.getBlockState().getValue(SeismicDetectorBlock.FACING);
        ItemStack filter = blockEntity.getSlottedFilterItem();

        // -------------------------------------------------------------
        // 1. RENDERING THE FILTER ITEM ON THE RED ZONE
        // -------------------------------------------------------------
        if (!filter.isEmpty()) {
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            poseStack.pushPose();
            
            // Move coordinate origin to the center of the block
            poseStack.translate(0.5, 0.5, 0.5);
            
            // Rotate based on the block's orientation facing direction
            float degrees = -facing.toYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(degrees));
            
            // Exact offsets derived from your 16x16 pixel layout math
            double itemX = 0.28125;  // Shift right to reach the center of the red square
            double itemY = 0.28125;  // Shift up to reach the center of the red square
            double itemZ = 0.505;    // Push item flat against the front face texture
            poseStack.translate(itemX, itemY, itemZ); 
            
            // Scale item down to 0.2x size so it fits perfectly inside the red quadrant box
            poseStack.scale(0.2f, 0.2f, 0.2f);
            
            itemRenderer.renderStatic(filter, ItemDisplayContext.FIXED, combinedLight, combinedOverlay, poseStack, bufferSource, blockEntity.getLevel(), 0);
            poseStack.popPose();
        }

        // -------------------------------------------------------------
        // 2. RENDERING THE CREATE-STYLE WHITE BOUNDING BOX
        // -------------------------------------------------------------
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // Show white box highlight only if the slot is empty and player holds an item
        if (filter.isEmpty() && !player.getMainHandItem().isEmpty()) {
            HitResult hit = Minecraft.getInstance().hitResult;
            
            // Check if player is targeted directly at this specific block entity face
            if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(blockEntity.getBlockPos()) && blockHit.getDirection() == facing) {
                poseStack.pushPose();
                
                // Align matrix grid coordinate math to the block's face rotation
                poseStack.translate(0.5, 0.5, 0.5);
                float degrees = -facing.toYRot();
                poseStack.mulPose(Axis.YP.rotationDegrees(degrees));
                
                // Outer edges matching your red 4x4 pixel bounding footprint exactly
                float minX = 0.125f;   // Pixel index 10 from center left
                float maxX = 0.4375f;  // Pixel index 15 near right margin edge
                float minY = 0.125f;   // Pixel index 10 from center bottom
                float maxY = 0.4375f;  // Pixel index 15 near top margin edge
                float minZ = 0.495f;   // Thin bounding selection volume profile depths
                float maxZ = 0.505f;

                VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
                LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, minY, minZ, maxX, maxY, maxZ, 1.0f, 1.0f, 1.0f, 1.0f);
                
                poseStack.popPose();
            }
        }
    }
}