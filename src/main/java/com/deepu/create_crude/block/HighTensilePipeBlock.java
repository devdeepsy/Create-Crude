package com.deepu.create_crude.block;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class HighTensilePipeBlock extends FluidPipeBlock {

    public HighTensilePipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static BlockEntityType<? extends FluidPipeBlockEntity> cachedType = null;

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        if (cachedType == null) {
            cachedType = (BlockEntityType<? extends FluidPipeBlockEntity>)
                net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "fluid_pipe"));
            if (cachedType == null) throw new IllegalStateException("Could not find create:fluid_pipe block entity type!");
        }
        return cachedType;
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
}