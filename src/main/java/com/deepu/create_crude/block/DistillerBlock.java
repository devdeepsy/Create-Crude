package com.deepu.create_crude.block;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.DistillerBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
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

public class DistillerBlock extends Block implements EntityBlock, IWrenchable {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public DistillerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistillerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != CreateCrude.DISTILLER_BE.get()) return null;
        if (level.isClientSide()) return null;

        return (lvl, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof DistillerBlockEntity distiller) {
                DistillerBlockEntity.serverTick(lvl, pos, blockState, distiller);
            }
        };
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            toggleStructureFormation(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(FORMED) && !level.isClientSide) {
                disbandStructure(level, pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    private void toggleStructureFormation(Level level, BlockPos clickedPos) {
        boolean currentlyFormed = level.getBlockState(clickedPos).getValue(FORMED);

        if (currentlyFormed) {
            disbandStructure(level, clickedPos);
        } else {
            BlockPos center = find3x3Center(level, clickedPos);
            if (center != null) {
                setStructureFormedState(level, center, true, center);
            }
        }
    }

    private BlockPos find3x3Center(Level level, BlockPos clickedPos) {
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                BlockPos candidateCenter = clickedPos.offset(cx, 0, cz);
                if (validate3x3(level, candidateCenter)) {
                    return candidateCenter;
                }
            }
        }
        return null;
    }

    private boolean validate3x3(Level level, BlockPos center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos target = center.offset(x, 0, z);
                if (!level.getBlockState(target).is(this)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void setStructureFormedState(Level level, BlockPos center, boolean formed, @Nullable BlockPos masterPos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos target = center.offset(x, 0, z);
                BlockState state = level.getBlockState(target);
                if (state.is(this)) {
                    level.setBlock(target, state.setValue(FORMED, formed), 3);
                    if (level.getBlockEntity(target) instanceof DistillerBlockEntity be) {
                        be.setMasterPos(formed ? masterPos : null);
                        be.notifyUpdate();
                    }
                }
            }
        }
    }

    private void disbandStructure(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DistillerBlockEntity be) {
            DistillerBlockEntity master = be.getMaster();
            BlockPos center = master != null ? master.getBlockPos() : pos;
            setStructureFormedState(level, center, false, null);
        }
    }
}