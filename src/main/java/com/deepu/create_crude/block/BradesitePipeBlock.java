package com.deepu.create_crude.block;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.BradesitePipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class BradesitePipeBlock extends FluidPipeBlock {

    public BradesitePipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return CreateCrude.BRADESITE_PIPE_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                level.destroyBlock(context.getClickedPos(), !player.isCreative());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BradesitePipeBlockEntity pipeBE) {
            if (pipeBE.hasPayload()) {
                BradesitePipeBlockEntity.tick(level, pos, state, pipeBE);
            } else {
                boolean pulled = false;
                for (Direction dir : Direction.values()) {
                    BooleanProperty connProp = getConnectionProperty(dir);
                    if (connProp != null && state.getValue(connProp)) {
                        BlockPos neighborPos = pos.relative(dir);
                        pipeBE.tryPullFromNeighbor(level, pos, state, neighborPos, dir);
                        if (pipeBE.hasPayload()) {
                            pulled = true;
                            break;
                        }
                    }
                }
                if (!pulled && pipeBE.isPumpDrivingNetwork()) {
                    int delay = pipeBE.getTickDelay();
                    if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                        level.scheduleTick(pos, this, delay);
                    }
                }
            }
        }
    }

    private Direction getDirectionFromPositions(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        for (Direction dir : Direction.values()) {
            if (dir.getNormal().getX() == dx && dir.getNormal().getY() == dy && dir.getNormal().getZ() == dz) {
                return dir;
            }
        }
        return null;
    }

    private BooleanProperty getConnectionProperty(Direction dir) {
        switch (dir) {
            case NORTH: return FluidPipeBlock.NORTH;
            case SOUTH: return FluidPipeBlock.SOUTH;
            case EAST:  return FluidPipeBlock.EAST;
            case WEST:  return FluidPipeBlock.WEST;
            case UP:    return FluidPipeBlock.UP;
            case DOWN:  return FluidPipeBlock.DOWN;
            default:    return null;
        }
    }
}