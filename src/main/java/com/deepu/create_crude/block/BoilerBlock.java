package com.deepu.create_crude.block;

import com.deepu.create_crude.block.entity.BoilerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class BoilerBlock extends Block implements EntityBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public BoilerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide && !state.getValue(FORMED)) {
            checkAndFormStructure(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof BoilerBlockEntity be) {
                be.onBlockBroken();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public void checkAndFormStructure(Level level, BlockPos currentPos) {
        // Evaluate all 9 possible 3x3 origin coordinates containing currentPos
        for (int dx = -2; dx <= 0; dx++) {
            for (int dz = -2; dz <= 0; dz++) {
                BlockPos minPos = currentPos.offset(dx, 0, dz);
                if (isValid3x3Grid(level, minPos)) {
                    formStructure(level, minPos);
                    return;
                }
            }
        }
    }

    private boolean isValid3x3Grid(Level level, BlockPos minPos) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                BlockPos checkPos = minPos.offset(x, 0, z);
                BlockState bs = level.getBlockState(checkPos);
                if (!(bs.getBlock() instanceof BoilerBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void formStructure(Level level, BlockPos minPos) {
        BlockPos masterPos = minPos.offset(1, 0, 1);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                BlockPos pos = minPos.offset(x, 0, z);
                BlockState currentState = level.getBlockState(pos);
                level.setBlock(pos, currentState.setValue(FORMED, true), 3);
                if (level.getBlockEntity(pos) instanceof BoilerBlockEntity be) {
                    be.setMasterPos(masterPos);
                    be.notifyUpdate(); // Syncs state to client immediately
                }
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof BoilerBlockEntity boiler) {
                boiler.tickServer();
            }
        };
    }
}