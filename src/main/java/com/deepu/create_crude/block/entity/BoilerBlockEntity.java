package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.BoilerBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BoilerBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final int TANK_CAPACITY = 16000;
    
    private final FluidTank fluidTank = new FluidTank(TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private BlockPos masterPos = null;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.BOILER_BLOCK_BE.get(), pos, state);
    }

    public boolean isMaster() {
        return worldPosition.equals(masterPos);
    }

    public void setMasterPos(@Nullable BlockPos pos) {
        this.masterPos = pos;
        setChanged();
    }

    @Nullable
    public BoilerBlockEntity getMaster() {
        if (masterPos != null && level != null && level.isLoaded(masterPos)) {
            if (level.getBlockEntity(masterPos) instanceof BoilerBlockEntity master) {
                return master;
            }
        }
        return null;
    }

    public FluidTank getFluidTank() {
        if (!isMaster()) {
            BoilerBlockEntity master = getMaster();
            if (master != null) {
                return master.getFluidTank();
            }
        }
        return fluidTank;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (side == Direction.DOWN) {
            return null;
        }
        return getFluidTank();
    }

    public void tickServer() {
        if (level == null || !getBlockState().getValue(BoilerBlock.FORMED) || !isMaster()) {
            return;
        }

        BlockState stateBelow = level.getBlockState(worldPosition.below());
        if (!stateBelow.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) return;

        BlazeBurnerBlock.HeatLevel heat = stateBelow.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        int consumptionRate = switch (heat) {
            case SMOULDERING -> 5;
            case KINDLED -> 20;
            case SEETHING -> 50;
            default -> 0;
        };

        if (consumptionRate == 0) return;

        FluidStack stored = fluidTank.getFluid();
        if (!stored.isEmpty() && stored.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER)) {
            int drainAmount = Math.min(stored.getAmount(), consumptionRate);
            if (drainAmount > 0) {
                fluidTank.drain(drainAmount, IFluidHandler.FluidAction.EXECUTE);
                
                // Direct BlockEntity integer transfer
                BlockPos abovePos = worldPosition.above();
                if (level.getBlockEntity(abovePos) instanceof CrackerBlockEntity cracker) {
                    int steamGenerated = drainAmount * 10;
                    cracker.addSteam(steamGenerated);
                }
                notifyUpdate();
            }
        }
    }

    public void onBlockBroken() {
        if (level == null || level.isClientSide) return;

        BlockPos searchCenter = masterPos != null ? masterPos : worldPosition;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = searchCenter.offset(x, 0, z);
                BlockState state = level.getBlockState(p);
                if (state.getBlock() instanceof BoilerBlock && state.getValue(BoilerBlock.FORMED)) {
                    level.setBlock(p, state.setValue(BoilerBlock.FORMED, false), 3);
                    if (level.getBlockEntity(p) instanceof BoilerBlockEntity be) {
                        be.setMasterPos(null);
                    }
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!getBlockState().getValue(BoilerBlock.FORMED)) {
            tooltip.add(Component.literal("  ").append(Component.literal("Structure Incomplete (Requires 3x3x1)").withStyle(ChatFormatting.RED)));
            return true;
        }

        BoilerBlockEntity master = isMaster() ? this : getMaster();
        if (master == null) return false;

        tooltip.add(Component.literal("  ").append(Component.literal("3x3 Boiler Structure").withStyle(ChatFormatting.GOLD)));

        FluidTank tank = master.fluidTank;
        FluidStack fluid = tank.getFluid();
        String fluidName = fluid.isEmpty() ? "Empty" : fluid.getHoverName().getString();
        int amount = fluid.getAmount();

        tooltip.add(Component.literal("  ").append(Component.literal("Fluid Stored: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(fluidName + " (" + amount + " / " + TANK_CAPACITY + " mB)").withStyle(ChatFormatting.AQUA)));

        BlockState stateBelow = master.level != null ? master.level.getBlockState(master.worldPosition.below()) : null;
        int pseudoSteamProduction = 0;
        String heatStatus = "Unheated";
        ChatFormatting heatColor = ChatFormatting.DARK_GRAY;

        if (stateBelow != null && stateBelow.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            BlazeBurnerBlock.HeatLevel heat = stateBelow.getValue(BlazeBurnerBlock.HEAT_LEVEL);
            switch (heat) {
                case SMOULDERING -> {
                    pseudoSteamProduction = 250;
                    heatStatus = "Passive Heat";
                    heatColor = ChatFormatting.YELLOW;
                }
                case KINDLED -> {
                    pseudoSteamProduction = 1000;
                    heatStatus = "Heated";
                    heatColor = ChatFormatting.GOLD;
                }
                case SEETHING -> {
                    pseudoSteamProduction = 2500;
                    heatStatus = "Superheated";
                    heatColor = ChatFormatting.LIGHT_PURPLE;
                }
                default -> {
                    pseudoSteamProduction = 0;
                    heatStatus = "Unheated";
                    heatColor = ChatFormatting.RED;
                }
            }
        }

        tooltip.add(Component.literal("  ").append(Component.literal("Heat Level: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(heatStatus).withStyle(heatColor)));

        tooltip.add(Component.literal("  ").append(Component.literal("Steam Output: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(pseudoSteamProduction + " Units/t").withStyle(pseudoSteamProduction > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));

        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("FluidTank", fluidTank.writeToNBT(registries, new CompoundTag()));
        if (masterPos != null) {
            tag.putInt("MasterX", masterPos.getX());
            tag.putInt("MasterY", masterPos.getY());
            tag.putInt("MasterZ", masterPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("FluidTank")) {
            fluidTank.readFromNBT(registries, tag.getCompound("FluidTank"));
        }
        if (tag.contains("MasterX")) {
            masterPos = new BlockPos(tag.getInt("MasterX"), tag.getInt("MasterY"), tag.getInt("MasterZ"));
        } else {
            masterPos = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}