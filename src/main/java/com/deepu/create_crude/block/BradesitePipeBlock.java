package com.deepu.create_crude.block;

import com.deepu.create_crude.CreateCrude;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class BradesitePipeBlock extends FluidPipeBlock {

    public BradesitePipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return CreateCrude.BRADESITE_PIPE_BE.get();
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