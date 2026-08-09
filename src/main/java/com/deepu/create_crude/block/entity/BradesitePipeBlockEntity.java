package com.deepu.create_crude.block.entity;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;

public class BradesitePipeBlockEntity extends FluidPipeBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();


    public BradesitePipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

}