package com.deepu.create_crude.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
 
public class PumpjackCrankBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<PumpjackCrankBlock> CODEC = simpleCodec(PumpjackCrankBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public PumpjackCrankBlock(Properties properties) {
        super(properties);
        // Changed default to NORTH since UP/DOWN are no longer valid states
        this.registerDefaultState(this.stateDefinition.any()
        .setValue(FACING, Direction.NORTH)
        .setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Places the block facing the player (standard horizontal block behavior)
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return com.deepu.create_crude.util.PumpjackWrenchHelper.handleWrenchInteraction(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
       builder.add(FACING, FORMED);
    }
    
   @Override
    public InteractionResult onWrenched(BlockState state,UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            if (state.hasProperty(FACING)) {
                Direction currentFacing = state.getValue(FACING);
                Direction nextFacing = currentFacing.getClockWise();
                level.setBlock(pos, state.setValue(FACING, nextFacing), 3);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

}