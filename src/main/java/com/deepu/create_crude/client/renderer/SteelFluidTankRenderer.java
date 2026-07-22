package com.deepu.create_crude.client.renderer;

import com.deepu.create_crude.block.SteelFluidTankBlock;
import com.deepu.create_crude.block.entity.SteelFluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class SteelFluidTankRenderer implements BlockEntityRenderer<SteelFluidTankBlockEntity> {

    private static final float CAP_HEIGHT = 0.25f;   // Frame cap inset
    private static final float HULL_WIDTH = 0.0703125f; // Frame side inset

    public SteelFluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SteelFluidTankBlockEntity be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {

        // 1. MUST ONLY render from the controller (North-West Bottom origin)
        if (!be.isController()) return;

        Level level = be.getLevel();
        if (level == null) return;

        int width = be.width;
        int depth = be.depth;
        int height = be.height;

        // 2. Expand render bounds across full multiblock dimensions (e.g., 2x2 or 3x3)
        float xMin = HULL_WIDTH;
        float xMax = (float) width - HULL_WIDTH;
        float zMin = HULL_WIDTH;
        float zMax = (float) depth - HULL_WIDTH;

        BlockPos controllerPos = be.getBlockPos();

        // 3. Render fluid layer-by-layer from bottom (y=0) to top (y=height-1)
        for (int y = 0; y < height; y++) {
            boolean isBottomLayer = (y == 0);
            boolean isTopLayer = (y == height - 1);

            float capBottom = isBottomLayer ? CAP_HEIGHT : 0.0f;
            float capTop = isTopLayer ? CAP_HEIGHT : 0.0f;
            float availableLayerHeight = 1.0f - capBottom - capTop;

            // Aggregate fluid stacks for this horizontal layer
            List<FluidStack> layerFluids = new ArrayList<>();
            int totalCapacity = 0;
            int totalAmount = 0;

            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    BlockPos pos = controllerPos.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
                        FluidStack stack = tankBE.getTank().getFluid();
                        int capacity = tankBE.getTank().getCapacity();
                        totalCapacity += capacity;

                        if (!stack.isEmpty()) {
                            totalAmount += stack.getAmount();
                            boolean found = false;
                            for (FluidStack existing : layerFluids) {
                                if (FluidStack.isSameFluidSameComponents(existing, stack)) {
                                    existing.grow(stack.getAmount());
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                layerFluids.add(stack.copy());
                            }
                        }
                    }
                }
            }

            if (totalAmount <= 0 || totalCapacity <= 0 || layerFluids.isEmpty()) continue;

            // 4. Calculate exact contiguous Y positions for fluids in this layer
            float currentY = (float) y + capBottom;

            for (FluidStack fluidStack : layerFluids) {
                float fluidFraction = (float) fluidStack.getAmount() / (float) totalCapacity;
                float fluidBoxHeight = fluidFraction * availableLayerHeight;

                if (fluidBoxHeight <= 0.001f) continue;

                float yMin = currentY;
                float yMax = currentY + fluidBoxHeight;

                ms.pushPose();
                NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
                        fluidStack,
                        xMin, yMin, zMin,
                        xMax, yMax, zMax,
                        buffer, ms, light, false, true
                );
                ms.popPose();

                // Move bottom Y of next fluid stack directly to top Y of current fluid
                currentY = yMax;
            }
        }
    }
}