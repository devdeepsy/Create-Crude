package com.deepu.create_crude.gases;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.SteelBasinBlockEntity;
import com.deepu.create_crude.block.entity.SteelFluidTankBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SteelPumpBlockEntity extends PumpBlockEntity {

    public SteelPumpBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.STEEL_PUMP_BE.get(), pos, state);
    }
    
    private static final int TRANSFER_RATE = 20;

    @Override
    public void tick() {
        // ALWAYS let Create execute its standard liquid/pipe ticking logic first
        super.tick();

        if (getSpeed() == 0 || level == null || level.isClientSide) return;

        Direction facing = getBlockState().getValue(PumpBlock.FACING);
        Direction back = facing.getOpposite();

        BlockPos backPos = getBlockPos().relative(back);
        BlockPos frontPos = getBlockPos().relative(facing);

        BlockEntity backBE = level.getBlockEntity(backPos);
        BlockEntity frontBE = level.getBlockEntity(frontPos);

        // ONLY attempt gas transfer if BOTH adjacent blocks exist and contain gas payloads
        if (backBE != null && frontBE != null) {
            ResourceLocation sourceGasId = getSourceGasId(backBE);
            int sourceAmount = getSourceGasAmount(backBE);

            if (sourceGasId != null && sourceAmount > 0) {
                int toTransfer = Math.min(sourceAmount, TRANSFER_RATE);

                if (canTargetAcceptGas(frontBE, sourceGasId, toTransfer)) {
                    int actualDrained = drainGasFromSource(backBE, toTransfer);
                    if (actualDrained > 0) {
                        fillGasToTarget(frontBE, sourceGasId, actualDrained);
                    }
                }
            }
        }
    }

    private ResourceLocation getSourceGasId(BlockEntity be) {
        if (be instanceof SteelFluidTankBlockEntity tank) return tank.getStoredGasId();
        if (be instanceof SteelBasinBlockEntity basin) return basin.getStoredGasId();
        return null;
    }

    private int getSourceGasAmount(BlockEntity be) {
        if (be instanceof SteelFluidTankBlockEntity tank) return tank.getStoredGasAmount();
        if (be instanceof SteelBasinBlockEntity basin) return basin.getStoredGasAmount();
        return 0;
    }

    private boolean canTargetAcceptGas(BlockEntity be, ResourceLocation gasId, int amount) {
        if (be instanceof SteelFluidTankBlockEntity tank) return tank.fillGas(gasId, amount, true) > 0;
        if (be instanceof SteelBasinBlockEntity basin) return basin.canAcceptGas(gasId, amount);
        return false;
    }

    private int drainGasFromSource(BlockEntity be, int amount) {
        if (be instanceof SteelFluidTankBlockEntity tank) return tank.drainGas(amount, false);
        if (be instanceof SteelBasinBlockEntity basin) return basin.drainGas(amount, false);
        return 0;
    }

    private void fillGasToTarget(BlockEntity be, ResourceLocation gasId, int amount) {
        if (be instanceof SteelFluidTankBlockEntity tank) tank.fillGas(gasId, amount, false);
        if (be instanceof SteelBasinBlockEntity basin) basin.fillGas(gasId, amount);
    }
}