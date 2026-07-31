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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SteelBasinBlock extends BasinBlock {

    public SteelBasinBlock(Properties properties) {
        super(properties);
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