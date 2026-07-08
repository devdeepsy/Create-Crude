package com.deepu.create_crude.util;

import com.deepu.create_crude.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public class PumpjackWrenchHelper {

    public static ItemInteractionResult handleWrenchInteraction(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 1. Check if the player is holding a wrench
        if (!stack.getItem().getDescriptionId().contains("wrench")) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 2. Shift + Crouch: Break the block instantly and drop items
        if (player.isCrouching()) {
            if (!level.isClientSide) {
                level.destroyBlock(pos, true, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 3. Simple Right-Click: Explicitly handle rotation per block class
        if (!level.isClientSide) {
            BlockState newState = null;

            // A. Holder Block custom rotation
            if (state.getBlock() instanceof PumpjackHolderBlock) {
                if (state.hasProperty(PumpjackHolderBlock.HORIZONTAL_ROTATION)) {
                    Direction currentHorizontal = state.getValue(PumpjackHolderBlock.HORIZONTAL_ROTATION);
                    Direction nextHorizontal = Direction.from2DDataValue(currentHorizontal.get2DDataValue() + 1);
                    newState = state.setValue(PumpjackHolderBlock.HORIZONTAL_ROTATION, nextHorizontal);
                }
            }
            // B. Rod Block Axis rotation
            else if (state.getBlock() instanceof PumpjackRodBlock) {
                if (state.hasProperty(BlockStateProperties.AXIS)) {
                    Direction.Axis currentAxis = state.getValue(BlockStateProperties.AXIS);
                    Direction.Axis nextAxis = switch (currentAxis) {
                        case X -> Direction.Axis.Y;
                        case Y -> Direction.Axis.Z;
                        case Z -> Direction.Axis.X;
                    };
                    newState = state.setValue(BlockStateProperties.AXIS, nextAxis);
                }
            }
            // C. Crank Block (Explicitly targets the Crank's HORIZONTAL_FACING property)
            else if (state.getBlock() instanceof PumpjackCrankBlock) {
                if (state.hasProperty(PumpjackCrankBlock.FACING)) {
                    Direction currentFacing = state.getValue(PumpjackCrankBlock.FACING);
                    Direction nextFacing = currentFacing.getClockWise(); // Rotates clockwise around Y-axis
                    newState = state.setValue(PumpjackCrankBlock.FACING, nextFacing);
                }
            }
            // D. Standard Facing Blocks (Hammers, Heads, Companions, Counterweights)
            else if (state.getBlock() instanceof PumpjackHammerBlock ||
                     state.getBlock() instanceof PumpjackHammerHeadBlock ||
                     state.getBlock() instanceof PumpjackHammerCompanionBlock ||
                     state.getBlock() instanceof PumpjackCounterweightBlock) {
                
                if (state.hasProperty(BlockStateProperties.FACING)) {
                    Direction currentFacing = state.getValue(BlockStateProperties.FACING);
                    
                    // Keep UP and DOWN locked static as requested
                    if (currentFacing != Direction.UP && currentFacing != Direction.DOWN) {
                        Direction nextFacing = currentFacing.getClockWise(Direction.Axis.Y);
                        newState = state.setValue(BlockStateProperties.FACING, nextFacing);
                    }
                }
            }

            // Apply updates and forcefully synchronize the client-side rendering engine
            if (newState != null) {
                level.setBlock(pos, newState, 3);
                level.sendBlockUpdated(pos, state, newState, 3);
            }
        }

        // Return sidedSuccess so both client swings hand and server executes successfully
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}