package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;

public class DistillationCasingBlockEntity extends BlockEntity {
    private DistillationControllerBlockEntity controller;
    private int productIndex = -1;

    public DistillationCasingBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.DISTILLATION_CASING_BE.get(), pos, state);
    }

    private void findController() {
        if (controller != null && !controller.isRemoved()) return;
        if (level == null) return;
        // Search downward until we find the controller (or the bottom)
        BlockPos.MutableBlockPos pos = worldPosition.below().mutable();
        while (pos.getY() >= 0) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DistillationControllerBlockEntity ctrl) {
                controller = ctrl;
                productIndex = ctrl.getProductIndexForPos(worldPosition);
                break;
            }
            pos.move(Direction.DOWN);
        }
    }

    @Nullable
    public IFluidHandler getFluidCapability(Direction side) {
        findController();
        if (controller == null) return null;
        FluidTank tank = controller.getOutputTank(productIndex);
        if (tank == null) return null;
        // Return a wrapper that only allows draining (extraction)
        return new IFluidHandler() {
            @Override
            public int getTanks() { return 1; }

            @Override
            public FluidStack getFluidInTank(int tankIndex) {
                return tank.getFluidInTank(0);
            }

            @Override
            public int getTankCapacity(int tankIndex) {
                return tank.getTankCapacity(0);
            }

            @Override
            public boolean isFluidValid(int tankIndex, FluidStack stack) {
                return tank.isFluidValid(0, stack);
            }

            @Override
            public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
                return 0; // no input
            }

            @Override
            public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
                return tank.drain(resource, action);
            }

            @Override
            public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
                return tank.drain(maxDrain, action);
            }
        };
    }
}