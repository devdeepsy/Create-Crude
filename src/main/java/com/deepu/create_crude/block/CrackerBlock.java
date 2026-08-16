package com.deepu.create_crude.block;

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

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.CrackerBlockEntity;

public class CrackerBlock extends Block implements EntityBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public CrackerBlock(Properties properties) {
        super(properties);
        // FIX: defaultInstanceState() -> defaultBlockState()
        this.registerDefaultState(this.defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            checkAndFormStructure(level, pos);
        }
    }

    private void checkAndFormStructure(Level level, BlockPos pos) {
        for (int dx = -2; dx <= 0; dx++) {
            for (int dz = -2; dz <= 0; dz++) {
                BlockPos minPos = pos.offset(dx, 0, dz);
                if (isValid3x3(level, minPos) && isBoilerLayerValid(level, minPos.below())) {
                    formStructure(level, minPos);
                    return;
                }
            }
        }
    }

    private boolean isValid3x3(Level level, BlockPos minPos) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                BlockState state = level.getBlockState(minPos.offset(x, 0, z));
                if (!state.is(this)) return false;
            }
        }
        return true;
    }

    private boolean isBoilerLayerValid(Level level, BlockPos boilerMinPos) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                BlockPos current = boilerMinPos.offset(x, 0, z);
                BlockState state = level.getBlockState(current);
                if (!state.hasProperty(BoilerBlock.FORMED) || !state.getValue(BoilerBlock.FORMED)) return false;
            }
        }
        return true;
    }

    private void formStructure(Level level, BlockPos minPos) {
        BlockPos masterPos = minPos.offset(1, 0, 1);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                BlockPos pos = minPos.offset(x, 0, z);
                level.setBlock(pos, level.getBlockState(pos).setValue(FORMED, true), 3);
                if (level.getBlockEntity(pos) instanceof CrackerBlockEntity be) {
                    be.setMasterPos(masterPos);
                    be.notifyUpdate();
                }
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrackerBlockEntity(pos, state);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 1. Ensure BlockEntityType matches
        if (type != CreateCrude.CRACKER_BE.get()) {
            return null;
        }

        // 2. Only execute ticking logic on the server side
        if (level.isClientSide()) {
            return null; // Return client ticker here if you add particle/sound FX later
        }

        // 3. Delegate to server ticker
        return (lvl, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof CrackerBlockEntity cracker) {
                CrackerBlockEntity.serverTick(lvl, pos, blockState, cracker);
            }
        };
    }
}