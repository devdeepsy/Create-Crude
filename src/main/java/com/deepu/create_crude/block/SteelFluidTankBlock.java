package com.deepu.create_crude.block;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;

public class SteelFluidTankBlock extends FluidTankBlock {

    public SteelFluidTankBlock(BlockBehaviour.Properties properties) {
        super(properties, false); 
    }

    private static BlockEntityType<? extends FluidTankBlockEntity> cachedType = null;

    @Override
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
        if (cachedType == null) {
            cachedType = (BlockEntityType<? extends FluidTankBlockEntity>)
                net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "fluid_tank"));
            if (cachedType == null) throw new IllegalStateException("Could not find create:fluid_tank block entity type!");
        }
        return cachedType;
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