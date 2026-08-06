package com.deepu.create_crude.block;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.SteelBasinBlockEntity;
import com.deepu.create_crude.gases.GasBlock;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteelBasinBlock extends BasinBlock {

    private static final VoxelShape BASIN_SHAPE = Shapes.join(
        Shapes.block(),
        Shapes.box(2 / 16D, 2 / 16D, 2 / 16D, 14 / 16D, 16 / 16D, 14 / 16D),
        BooleanOp.ONLY_FIRST
    );

    public SteelBasinBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BASIN_SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return BASIN_SHAPE;
    }

    @Override
    public BlockEntityType<? extends BasinBlockEntity> getBlockEntityType() {
        return CreateCrude.STEEL_BASIN_BE.get();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moved);
        if (level.isClientSide) return;

        if (level.getBlockEntity(pos) instanceof SteelBasinBlockEntity basinBE) {
            BlockState neighborState = level.getBlockState(neighborPos);

            // Pull gas dynamically if any valid GasBlock is placed directly adjacent
            if (neighborState.getBlock() instanceof GasBlock) {
                ResourceLocation gasId = BuiltInRegistries.BLOCK.getKey(neighborState.getBlock());
                int intakeAmount = 1000;

                // Validates gas type matching and remaining capacity
                if (basinBE.canAcceptGas(gasId, intakeAmount)) {
                    level.removeBlock(neighborPos, false);
                    basinBE.fillGas(gasId, intakeAmount);
                }
            }
        }
    }
}