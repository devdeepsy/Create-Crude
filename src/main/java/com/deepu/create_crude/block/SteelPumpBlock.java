package com.deepu.create_crude.block;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
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

public class SteelPumpBlock extends PumpBlock {

    public SteelPumpBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static BlockEntityType<? extends PumpBlockEntity> cachedType = null;

    @Override
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        if (cachedType == null) {
            cachedType = (BlockEntityType<? extends PumpBlockEntity>)
                net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "mechanical_pump"));
            if (cachedType == null) throw new IllegalStateException("Could not find create:mechanical_pump block entity type!");
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
        return super.onWrenched(state, context);
    }
}