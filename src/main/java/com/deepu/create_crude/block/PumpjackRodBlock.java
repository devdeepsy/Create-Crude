package com.deepu.create_crude.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

public class PumpjackRodBlock extends Block implements IWrenchable {
    // 1. Swapped FACING for AXIS to behave like a Create Shaft
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<RodMaterial> MATERIAL = EnumProperty.create("material", RodMaterial.class);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public static final MapCodec<PumpjackRodBlock> CODEC = simpleCodec(PumpjackRodBlock::new);

    public enum RodMaterial implements StringRepresentable {
        IRON("iron"),
        STEEL("steel"),
        CAST_IRON("cast_iron");

        private final String name;
        RodMaterial(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return this.name; }
    }

    public PumpjackRodBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(MATERIAL, RodMaterial.IRON)
                .setValue(FORMED,false));
    }
    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockState clickedState = level.getBlockState(clickedPos);

        // If the block we are clicking ON is also a PumpjackRodBlock, inherit its axis!
        if (clickedState.getBlock() instanceof PumpjackRodBlock) {
            return this.defaultBlockState().setValue(AXIS, clickedState.getValue(AXIS));
        }

        // Otherwise, fall back to standard pillar placement based on the clicked face
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
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
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            Direction.Axis currentAxis = state.getValue(AXIS);
            Direction.Axis nextAxis = switch (currentAxis) {
                case X -> Direction.Axis.Y;
                case Y -> Direction.Axis.Z;
                case Z -> Direction.Axis.X;
            };

            BlockState newState = state.setValue(AXIS, nextAxis);
            level.setBlock(pos, newState, 3);
            level.sendBlockUpdated(pos, state, newState, 3);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, MATERIAL,FORMED);
    }
    public RodMaterial getMaterialFromItem(ItemStack stack) {
        String itemId = stack.getItem().getDescriptionId();
        if (itemId.contains("steel_rod")) return RodMaterial.STEEL;
        if (itemId.contains("cast_iron_rod")) return RodMaterial.CAST_IRON;
        
        return RodMaterial.IRON;
    }
}