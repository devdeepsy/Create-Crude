package com.deepu.create_crude.gases;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.SteelBasinBlockEntity;
import com.deepu.create_crude.block.entity.SteelFluidTankBlockEntity;
import com.deepu.create_crude.gases.network.GasPayload;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.deepu.create_crude.block.entity.ReactorBlockEntity;

public class SteelPumpBlockEntity extends PumpBlockEntity {

    private static final int TRANSFER_RATE = 20;      // mB/tick equivalent for tank<->tank
    private static final int PIPE_INJECT_AMOUNT = 1000; // matches GasAwarePipeBlockEntity's own convention
    private static final int PIPE_DEFAULT_RADIUS = 5;

    public SteelPumpBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.STEEL_PUMP_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (getSpeed() == 0 || level == null || level.isClientSide) return;

        Direction facing = getBlockState().getValue(PumpBlock.FACING);
        Direction back = facing.getOpposite();

        BlockPos backPos = getBlockPos().relative(back);
        BlockPos frontPos = getBlockPos().relative(facing);

        BlockEntity backBE = level.getBlockEntity(backPos);
        BlockEntity frontBE = level.getBlockEntity(frontPos);

        ResourceLocation gasId = extractSourceGasId(backBE, backPos);
        if (gasId == null) return;

        // --- try pushing into a target ---

        if (frontBE instanceof GasAwarePipeBlockEntity frontPipe) {
            if (frontPipe.hasGas()) return;
            frontPipe.setGas(new GasPayload(gasId, PIPE_DEFAULT_RADIUS), facing.getOpposite());
            int delay = getPipeDelay();
            if (!level.getBlockTicks().hasScheduledTick(frontPos, frontPipe.getBlockState().getBlock())) {
                level.scheduleTick(frontPos, frontPipe.getBlockState().getBlock(), delay);
            }
            drainSource(backBE, backPos, TRANSFER_RATE);
            return;
        }

        if (frontBE instanceof SteelFluidTankBlockEntity frontTank) {
            int filled = frontTank.fillGas(gasId, TRANSFER_RATE, true); // simulate first
            if (filled > 0) {
                frontTank.fillGas(gasId, filled, false);
                drainSource(backBE, backPos, filled);
            }
            return;
        }

        if (frontBE instanceof SteelBasinBlockEntity frontBasin) {
            if (frontBasin.canAcceptGas(gasId, TRANSFER_RATE)) {
                frontBasin.fillGas(gasId, TRANSFER_RATE);
                drainSource(backBE, backPos, TRANSFER_RATE);
            }
            return;
        }
        if (frontBE instanceof ReactorBlockEntity frontReactor) {
            if (frontReactor.canAcceptGas(gasId, TRANSFER_RATE)) {
                int filled = frontReactor.fillGas(gasId, TRANSFER_RATE);
                if (filled > 0) {
                    drainSource(backBE, backPos, filled);
                }
            }
            return;
        }

        if (frontBE == null && level.getBlockState(frontPos).canBeReplaced()) {
            Block gasBlock = BuiltInRegistries.BLOCK.get(gasId);
            if (gasBlock instanceof GasBlock) {
                level.setBlock(frontPos, gasBlock.defaultBlockState()
                        .setValue(GasBlock.RADIUS, Math.min(PIPE_DEFAULT_RADIUS, GasBlock.MAX_RADIUS))
                        .setValue(GasBlock.SOURCE, false), 3);
                drainSource(backBE, backPos, TRANSFER_RATE);
            }
        }
    }

    /** Reads a gas id from any recognized source: tank, basin, pipe payload, or a raw world GasBlock. */
    private ResourceLocation extractSourceGasId(BlockEntity backBE, BlockPos backPos) {
        if (backBE instanceof SteelFluidTankBlockEntity tank && tank.getStoredGasAmount() > 0) {
            return tank.getStoredGasId();
        }
        if (backBE instanceof SteelBasinBlockEntity basin && basin.getStoredGasAmount() > 0) {
            return basin.getStoredGasId();
        }
        if (backBE instanceof GasAwarePipeBlockEntity pipe && pipe.hasGas()) {
            return pipe.getGasPayload().gasBlockId();
        }
        // raw world gas block sitting behind the pump (source or expanding cloud)
        BlockState backState = level.getBlockState(backPos);
        if (backState.getBlock() instanceof GasBlock) {
            return BuiltInRegistries.BLOCK.getKey(backState.getBlock());
        }
        return null;
    }

    /** Removes the consumed gas from whichever source type it came from. */
    private void drainSource(BlockEntity backBE, BlockPos backPos, int amount) {
        if (backBE instanceof SteelFluidTankBlockEntity tank) {
            tank.drainGas(amount, false);
            return;
        }
        if (backBE instanceof SteelBasinBlockEntity basin) {
            basin.drainGas(amount, false);
            return;
        }
        if (backBE instanceof GasAwarePipeBlockEntity pipe) {
            pipe.clearGas();
            return;
        }
        // raw world gas block: consume it directly
        BlockState backState = level.getBlockState(backPos);
        if (backState.getBlock() instanceof GasBlock) {
            level.removeBlock(backPos, false);
        }
    }

    private int getPipeDelay() {
        int speed = Math.abs((int) getSpeed());
        return speed > 0 ? Math.max(1, Math.min(20, 256 / speed)) : 5;
    }
}