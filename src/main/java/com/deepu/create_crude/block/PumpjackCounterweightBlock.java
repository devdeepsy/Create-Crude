package com.deepu.create_crude.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public class PumpjackCounterweightBlock extends DirectionalBlock implements IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final MapCodec<PumpjackCounterweightBlock> CODEC = simpleCodec(PumpjackCounterweightBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public PumpjackCounterweightBlock(Properties properties){
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }
    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Hide it when the machine is active
        if (state.hasProperty(FORMED) && state.getValue(FORMED)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        // If placed on top/bottom face, fall back to horizontal look direction
        if (clicked == Direction.UP || clicked == Direction.DOWN) {
            clicked = context.getHorizontalDirection().getOpposite();
        }
        return this.defaultBlockState()
                .setValue(FACING, clicked)
                .setValue(FORMED, false);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            Direction next = state.getValue(FACING).getClockWise(Direction.Axis.Y);
            level.setBlock(pos, state.setValue(FACING, next), 3);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {

        builder.add(FACING,FORMED);
    } 
}