package com.deepu.create_crude.gases;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.SteelFluidTankBlockEntity;
import com.deepu.create_crude.gases.network.GasPayload;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SteelPumpBlockEntity extends PumpBlockEntity {

    public SteelPumpBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.STEEL_PUMP_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (getSpeed() == 0) return;
        if (level == null || level.isClientSide) return;

        Direction facing = getBlockState().getValue(PumpBlock.FACING);
        Direction back = facing.getOpposite();

        BlockPos backPos = getBlockPos().relative(back);
        BlockPos frontPos = getBlockPos().relative(facing);

        BlockEntity backBE = level.getBlockEntity(backPos);
        BlockEntity frontBE = level.getBlockEntity(frontPos);

        GasPayload payload = null;

        // 1. Extract Gas Payload from intake side (Pipe or Fluid Tank)
        if (backBE instanceof GasAwarePipeBlockEntity backPipe) {
            payload = backPipe.getGasPayload();
        } else if (backBE instanceof SteelFluidTankBlockEntity backTank) {
            if (backTank.getStoredGasAmount() >= 1000 && backTank.getStoredGasId() != null) {
                payload = new GasPayload(backTank.getStoredGasId(), 5);
            }
        }

        if (payload == null) return;

        int speed = Math.abs((int) getSpeed());
        int delay = speed > 0 ? Math.max(1, Math.min(20, 256 / speed)) : 5;

        // 2. Push Gas Payload to output side
        if (frontBE instanceof GasAwarePipeBlockEntity frontPipe) {
            if (frontPipe.hasGas()) return;

            frontPipe.setGas(payload, facing.getOpposite());
            clearGasFromSource(backBE);

            if (!level.getBlockTicks().hasScheduledTick(frontPos, frontPipe.getBlockState().getBlock())) {
                level.scheduleTick(frontPos, frontPipe.getBlockState().getBlock(), delay);
            }
        } else if (frontBE instanceof SteelFluidTankBlockEntity frontTank) {
            // Flow directly into tank!
            int filled = frontTank.fillGas(payload.gasBlockId(), 1000, false);
            if (filled > 0) {
                clearGasFromSource(backBE);
            }
        } else if (level.getBlockState(frontPos).canBeReplaced()) {
            Block gasBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(payload.gasBlockId());
            if (gasBlock instanceof GasBlock) {
                level.setBlock(frontPos, gasBlock.defaultBlockState()
                        .setValue(GasBlock.RADIUS, Math.min(payload.radius(), GasBlock.MAX_RADIUS))
                        .setValue(GasBlock.SOURCE, false), 3);
            }
            clearGasFromSource(backBE);
        }
    }

    private void clearGasFromSource(BlockEntity backBE) {
        if (backBE instanceof GasAwarePipeBlockEntity backPipe) {
            backPipe.clearGas();
        } else if (backBE instanceof SteelFluidTankBlockEntity backTank) {
            backTank.drainGas(1000, false);
        }
    }
}