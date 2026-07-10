package com.deepu.create_crude.gases;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModGases;
import com.deepu.create_crude.gases.GasBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class GasBlockEntity extends BlockEntity {
    private final FluidTank tank = new FluidTank(1000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                updateBlockRadius();
            }
        }
    };

    private Fluid cachedFluid = null;

    public GasBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.GAS_BE.get(), pos, state);
        // Determine which fluid this block represents
        Fluid fluid = ModGases.BLOCK_TO_FLUID.get(state.getBlock());
        if (fluid != null) {
            tank.fill(new FluidStack(fluid, tank.getCapacity()), FluidAction.EXECUTE);
            cachedFluid = fluid;
        }
    }

    private void updateBlockRadius() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof GasBlock gasBlock)) return;

        int maxRadius = gasBlock.getProperties().maxRadius(); // must be public or add getter
        float fillRatio = (float) tank.getFluidAmount() / tank.getCapacity();
        int newRadius = Math.round(fillRatio * maxRadius);
        newRadius = Math.max(0, Math.min(maxRadius, newRadius));

        int currentRadius = state.getValue(GasBlock.RADIUS);
        if (currentRadius != newRadius) {
            level.setBlock(worldPosition, state.setValue(GasBlock.RADIUS, newRadius), 3);
        }
    }

    public IFluidHandler getTank() {
        return tank;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Save tank contents
        CompoundTag tankTag = new CompoundTag();
        tank.writeToNBT(registries, tankTag);
        tag.put("Tank", tankTag);

        // Save fluid type
        if (cachedFluid != null) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(cachedFluid);
            tag.putString("FluidType", fluidId.toString());
        }
    }

     @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Load tank contents
        if (tag.contains("Tank")) {
            tank.readFromNBT(registries, tag.getCompound("Tank"));
        }
        // Load fluid type
        if (tag.contains("FluidType")) {
            ResourceLocation id = ResourceLocation.parse(tag.getString("FluidType"));
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            if (fluid != null) cachedFluid = fluid;
        }
        // After loading, update radius to match tank level
        if (level != null && !level.isClientSide) {
            updateBlockRadius();
        }
    }
}