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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.deepu.create_crude.block.entity.SeismicDetectorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import com.deepu.create_crude.client.gui.DetectorUIScreen;

public class SeismicDetectorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty IS_SCANNING = BooleanProperty.create("scanning");
    public static final BooleanProperty HAS_FILTER = BooleanProperty.create("has_filter");

    public static final MapCodec<SeismicDetectorBlock> CODEC = simpleCodec(SeismicDetectorBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public SeismicDetectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(IS_SCANNING, false)
                .setValue(HAS_FILTER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, IS_SCANNING, HAS_FILTER);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeismicDetectorBlockEntity(pos, state); 
    }

    @Override
protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, 
                                         Player player, InteractionHand hand, BlockHitResult hit) {
    if (level.getBlockEntity(pos) instanceof SeismicDetectorBlockEntity detector) {
        
        // Sneak + right-click opens the UI
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                // Directly open the UI screen
                net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.deepu.create_crude.client.gui.DetectorUIScreen(detector)
                );
            }
            return ItemInteractionResult.SUCCESS;
        }
        
        // 1. UNIVERSAL FUEL ENGINE
        int burnTime = heldItem.getBurnTime(null); 
        
        if (burnTime > 0) {
            if (!level.isClientSide) {
                detector.addFuel(burnTime);
                
                if (!player.isCreative()) {
                    if (heldItem.hasCraftingRemainingItem()) {
                        player.setItemInHand(hand, heldItem.getCraftingRemainingItem());
                    } else {
                        heldItem.shrink(1);
                    }
                }
                player.displayClientMessage(Component.literal("§6Engine fueled up! Running...§r"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // 2. Clear current filter from machine slot
        if (heldItem.isEmpty() && !detector.getSlottedFilterItem().isEmpty()) {
            if (!level.isClientSide) {
                player.setItemInHand(hand, detector.getSlottedFilterItem().copy());
                detector.setSlottedFilterItem(ItemStack.EMPTY);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // 3. Insert filter item into machine slot
        if (!heldItem.isEmpty() && detector.getSlottedFilterItem().isEmpty()) {
            if (!level.isClientSide) {
                detector.setSlottedFilterItem(heldItem.copy());
                if (!player.isCreative()) heldItem.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
    }
    
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
}

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        
        return createTickerHelper(type, com.deepu.create_crude.CreateCrude.SEISMIC_DETECTOR_BE.get(), 
                SeismicDetectorBlockEntity::tick);
    }
}