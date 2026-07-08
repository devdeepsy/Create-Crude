package com.deepu.create_crude.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class PumpjackHolderBlock extends Block implements IWrenchable {
    // 1. Core orientation property ("facing": handles up, down, north, south, east, west)
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    
    // 2. FIXED: Create a custom property named "rotation" to bypass the duplicate "facing" name crash
    public static final DirectionProperty HORIZONTAL_ROTATION = DirectionProperty.create("rotation", Direction.Plane.HORIZONTAL);
    
    public static final MapCodec<PumpjackHolderBlock> CODEC = simpleCodec(PumpjackHolderBlock::new);

    public PumpjackHolderBlock(Properties properties){
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec(){
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        Direction horizontalFacing = context.getHorizontalDirection().getOpposite();

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HORIZONTAL_ROTATION, horizontalFacing)
                .setValue(FORMED,false);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
        Direction current = state.getValue(HORIZONTAL_ROTATION);
        Direction next = current.getClockWise(Direction.Axis.Y);
        level.setBlock(pos, state.setValue(HORIZONTAL_ROTATION, next), 3);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Registering both properties safely under different string names now
        builder.add(FACING, HORIZONTAL_ROTATION,FORMED);
    }
    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 3. Dynamic visibility check!
        if (state.hasProperty(FORMED) && state.getValue(FORMED)) {
            return RenderShape.INVISIBLE; // Hide it when the machine is active
        }
        return RenderShape.MODEL; // Show it normally when building
    }
}