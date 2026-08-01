package com.deepu.create_crude.block;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.SteelBasinBlockEntity;
import com.deepu.create_crude.gases.GasBlock;
import com.deepu.create_crude.gases.GasAwarePipeBlockEntity;
import com.deepu.create_crude.gases.network.GasPayload;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    // Full 16x16x16 cube minus the open interior cavity (the recess above the base,
    // from x/z 2..14 and y 2..16). This matches the actual concave model geometry,
    // so ambient occlusion / smooth lighting stops treating the interior walls as
    // "inside solid matter" (which was rendering them pure black).
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
            // Pull gas directly if an active GasBlock is placed directly adjacent
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof GasBlock gasBlock) {
                ResourceLocation gasId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(gasBlock);
                if (gasId.toString().equals("createcrude:hydrogen_block")) {
                    if (basinBE.canAcceptGas(1000)) {
                        level.removeBlock(neighborPos, false);
                        basinBE.fillGas(gasId, 1000);
                    }
                }
            }
        }
    }
}