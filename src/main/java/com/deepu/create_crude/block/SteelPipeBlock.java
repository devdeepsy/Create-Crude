package com.deepu.create_crude.block;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.gases.GasAwarePipeBlockEntity;
import com.deepu.create_crude.gases.network.GasPayload;
import com.deepu.create_crude.gases.GasBlock;
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
import net.minecraft.resources.ResourceLocation;

public class SteelPipeBlock extends FluidPipeBlock {

    public SteelPipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return CreateCrude.GAS_AWARE_PIPE_BE.get();
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moved);
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof GasAwarePipeBlockEntity pipeBE && !pipeBE.hasGas()) {
                if (!pipeBE.isPumpDrivingNetwork()) return;
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getBlock() instanceof GasBlock gasBlock) {
                    Direction dir = getDirectionFromPositions(pos, neighborPos);
                    if (dir == null) return;
                    BooleanProperty connProp = getConnectionProperty(dir);
                    if (connProp != null && state.getValue(connProp)) {
                        ResourceLocation gasId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(gasBlock);
                        int radius = neighborState.getValue(GasBlock.RADIUS);
                        level.removeBlock(neighborPos, false);
                        pipeBE.setGas(new GasPayload(gasId, radius), dir);
                        
                        // FIX: Dynamic delay based on pump speed when pulling gas into network
                        int delay = pipeBE.getTickDelay();
                        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                            level.scheduleTick(pos, this, delay);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof GasAwarePipeBlockEntity pipeBE) {
            GasAwarePipeBlockEntity.tick(level, pos, state, pipeBE);
        }
    }

    // Helper to get direction from pos to target (both adjacent)
    private Direction getDirectionFromPositions(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        // Since they are adjacent, exactly one of dx, dy, dz will be non-zero
        for (Direction dir : Direction.values()) {
            if (dir.getNormal().getX() == dx && dir.getNormal().getY() == dy && dir.getNormal().getZ() == dz) {
                return dir;
            }
        }
        return null;
    }

    // Helper to get the BooleanProperty for a given Direction (from FluidPipeBlock)
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