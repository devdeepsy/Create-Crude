package com.deepu.create_crude.block;

import com.deepu.create_crude.block.entity.SteelFluidTankBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SteelFluidTankBlock extends BaseEntityBlock {
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final EnumProperty<TankShape> SHAPE = EnumProperty.create("shape", TankShape.class);

    public SteelFluidTankBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BOTTOM, true)
                .setValue(TOP, true)
                .setValue(SHAPE, TankShape.WINDOW));
    }

    // ========== MULTIBLOCK & PLACEMENT ==========

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean isTop = !level.getBlockState(pos.above()).is(this);
        boolean isBottom = !level.getBlockState(pos.below()).is(this);
        // shape will be corrected later by the BE
        return this.defaultBlockState()
                .setValue(TOP, isTop)
                .setValue(BOTTOM, isBottom)
                .setValue(SHAPE, TankShape.WINDOW);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                    LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            state = state.setValue(TOP, !neighborState.is(this));
        } else if (direction == Direction.DOWN) {
            state = state.setValue(BOTTOM, !neighborState.is(this));
        }
        if (neighborState.is(this) && level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
            tankBE.updateConnectivity = true;
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!oldState.is(this)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SteelFluidTankBlockEntity tankBE) {
                tankBE.updateConnectivity = true;
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SteelFluidTankBlockEntity tankBE) {
                tankBE.controller = null;
                tankBE.height = 1;
                tankBE.width = 1;
                tankBE.depth = 1;
                tankBE.window = true;
                for (Direction d : Direction.values()) {
                    BlockPos neighbor = pos.relative(d);
                    if (level.getBlockEntity(neighbor) instanceof SteelFluidTankBlockEntity other) {
                        other.updateConnectivity = true;
                    }
                }
                level.removeBlockEntity(pos);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    // ========== INTERACTIONS ==========

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SteelFluidTankBlockEntity tankBE) {
                IFluidHandler handler = tankBE.getFluidHandler(hitResult.getDirection());
                if (handler != null && FluidUtil.interactWithFluidHandler(player, hand, handler)) {
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        if (!level.isClientSide && stack.is(Tags.Items.TOOLS_WRENCH)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SteelFluidTankBlockEntity tankBE) {
                tankBE.toggleWindows();
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ========== COMPARATOR ==========

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SteelFluidTankBlockEntity tankBE) {
            SteelFluidTankBlockEntity controller = tankBE.getControllerBE();
            if (controller != null) {
                long totalCapacity = 0;
                long totalFilled = 0;
                for (int y = 0; y < controller.height; y++) {
                    for (int x = 0; x < controller.width; x++) {
                        for (int z = 0; z < controller.depth; z++) {
                            BlockPos p = controller.getBlockPos().offset(x, y, z);
                            if (level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity layer) {
                                totalCapacity += SteelFluidTankBlockEntity.CAPACITY;
                                totalFilled += layer.getTank().getFluidAmount();
                            }
                        }
                    }
                }
                if (totalCapacity == 0) return 0;
                float fill = (float) totalFilled / totalCapacity;
                return Math.round(fill * 14.0f) + (fill > 0 ? 1 : 0);
            }
        }
        return 0;
    }

    // ========== BLOCK ENTITY & RENDER ==========

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteelFluidTankBlockEntity(pos, state);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof SteelFluidTankBlockEntity tankBE) {
                tankBE.tick();
            }
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(SteelFluidTankBlock::new);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOTTOM, TOP, SHAPE);
    }
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        if (adjacentBlockState.getBlock() instanceof SteelFluidTankBlock) {
            return true; // Hides shared faces between adjacent tank blocks
        }
        return super.skipRendering(state, adjacentBlockState, side);
    }

    // ========== SHAPE ENUM (same as Create) ==========

    public enum TankShape implements StringRepresentable {
        PLAIN("plain"),
        WINDOW("window"),
        WINDOW_NW("window_nw"),
        WINDOW_SW("window_sw"),
        WINDOW_NE("window_ne"),
        WINDOW_SE("window_se");

        private final String name;
        TankShape(String name) { this.name = name; }
        @Override
        public String getSerializedName() { return this.name; }
    }
}