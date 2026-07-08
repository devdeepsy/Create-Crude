package com.deepu.create_crude.mixin;

import com.tterrag.registrate.util.entry.BlockEntry;
import com.simibubi.create.AllBlocks;
import com.deepu.create_crude.CreateCrude;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntry.class, remap = false)
public class BlockEntryMixin {

    @Inject(method = "has(Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private void onHasBlockState(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        BlockEntry<?> currentEntry = (BlockEntry<?>) (Object) this;

        // If Create checks if the neighbor is a standard Fluid Pipe, accept your Steel Pipe
        if (currentEntry == AllBlocks.FLUID_PIPE && state.is(CreateCrude.STEEL_PIPE.get())) {
            cir.setReturnValue(true);
        }
        if (currentEntry == AllBlocks.FLUID_PIPE && state.is(CreateCrude.HIGH_TENSILE_PIPE.get())) {
            cir.setReturnValue(true);
        }

        // If Create checks if the neighbor is a standard Mechanical Pump, accept your Steel Pump
        if (currentEntry == AllBlocks.MECHANICAL_PUMP && state.is(CreateCrude.STEEL_PUMP.get())) {
            cir.setReturnValue(true);
        }
    }
}