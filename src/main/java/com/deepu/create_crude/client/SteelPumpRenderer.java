package com.deepu.create_crude.client;

import com.deepu.create_crude.gases.SteelPumpBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class SteelPumpRenderer implements BlockEntityRenderer<SteelPumpBlockEntity> {

    public SteelPumpRenderer(BlockEntityRendererProvider.Context context) {
        // Standard renderer constructor
    }

    @Override
    public void render(SteelPumpBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) return;

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof PumpBlock)) return;

        Direction facing = state.getValue(PumpBlock.FACING);

        ms.pushPose();
        
        // 1. Translate to the center of the block for rotation operations
        ms.translate(0.5, 0.5, 0.5);

        // 2. Align the model matrix to match your blockstate's facing properties
        switch (facing) {
            case NORTH -> ms.mulPose(Axis.XP.rotationDegrees(90));
            case SOUTH -> {
                ms.mulPose(Axis.XP.rotationDegrees(90));
                ms.mulPose(Axis.YP.rotationDegrees(180));
            }
            case EAST -> {
                ms.mulPose(Axis.XP.rotationDegrees(90));
                ms.mulPose(Axis.YP.rotationDegrees(90));
            }
            case WEST -> {
                ms.mulPose(Axis.XP.rotationDegrees(90));
                ms.mulPose(Axis.YP.rotationDegrees(270));
            }
            case UP -> {}
            case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(180));
        }

        // 3. Calculate and apply kinetic spinning along the cog's rotation axis
        float speed = be.getSpeed();
        if (speed != 0) {
            float time = be.getLevel().getGameTime() + partialTicks;
            // Standard Create speed multiplier ratio for smooth interpolation
            float angle = (time * speed * 0.1f) % 360; 
            ms.mulPose(Axis.ZP.rotationDegrees(angle));
        }

        // 4. Translate back from the center anchor point
        ms.translate(-0.5, -0.5, -0.5);

        // 5. Direct rendering pipeline call via standard Minecraft BakedModel tessellation
        BakedModel cogModel = CreateCrudePartialModels.STEEL_PUMP_COG.get();
        if (cogModel != null) {
            VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.cutout());
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                ms.last(),
                vertexConsumer,
                state,
                cogModel,
                1.0F, 1.0F, 1.0F,
                light,
                overlay,
                ModelData.EMPTY,
                RenderType.cutout()
            );
        }

        ms.popPose();
    }
}