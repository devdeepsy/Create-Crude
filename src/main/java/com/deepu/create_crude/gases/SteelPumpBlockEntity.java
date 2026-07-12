package com.deepu.create_crude.gases;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.gases.GasAwarePipeBlockEntity;
import com.deepu.create_crude.gases.network.GasPayload;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

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
        if (!(backBE instanceof GasAwarePipeBlockEntity backPipe)) return;
        GasPayload payload = backPipe.getGasPayload();
        if (payload == null) return; 

        BlockEntity frontBE = level.getBlockEntity(frontPos);
        
        if (frontBE instanceof GasAwarePipeBlockEntity frontPipe) {
            if (frontPipe.hasGas()) return; 

            frontPipe.setGas(payload, facing.getOpposite()); 
            backPipe.clearGas();

            // FIX: Directly use pump speed metrics to schedule output pipe tick rates
            int speed = Math.abs((int) getSpeed());
            int delay = speed > 0 ? Math.max(1, Math.min(20, 256 / speed)) : 5;

            if (!level.getBlockTicks().hasScheduledTick(frontPos, frontPipe.getBlockState().getBlock())) {
                level.scheduleTick(frontPos, frontPipe.getBlockState().getBlock(), delay);
            }
        } else if (level.getBlockState(frontPos).canBeReplaced()) {
            Block gasBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(payload.gasBlockId());
            if (gasBlock instanceof GasBlock) {
                level.setBlock(frontPos, gasBlock.defaultBlockState()
                        .setValue(GasBlock.RADIUS, Math.min(payload.radius(), GasBlock.MAX_RADIUS))
                        .setValue(GasBlock.SOURCE, false), 3);
            }
            backPipe.clearGas();
        }
    }
}