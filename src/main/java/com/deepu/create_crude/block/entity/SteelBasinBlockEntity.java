package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.SulfurFluids;
import com.deepu.create_crude.gases.GasBlock;
import com.deepu.create_crude.gases.GasRegistry;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SteelBasinBlockEntity extends BasinBlockEntity implements IHaveGoggleInformation {

    private ResourceLocation storedGasId = null;
    private int storedGasAmount = 0;
    private static final int MAX_GAS_CAPACITY = 10000;

    private int processingTicks = 0;
    private static final int REQUIRED_TICKS = 40;
    private boolean isProcessing = false;

    // Animation variables
    public float mixerRotation = 0f;
    public float prevMixerRotation = 0f;

    private final GasTankHandler gasTankHandler = new GasTankHandler();

    public SteelBasinBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level != null) {
            if (level.isClientSide) {
                // Smooth frame-by-frame rotation tracking for renderer
                this.prevMixerRotation = this.mixerRotation;
                if (this.isProcessing) {
                    this.mixerRotation = (this.mixerRotation + 12.0f) % 360.0f;
                }
                spawnGasParticles();
            } else {
                siphonOverheadGasBlock();
                processHydrotreating();
            }
        }
    }

    /**
     * Pulls gas from a GasBlock placed in world space directly above the basin into storedGasAmount.
     */
    private void siphonOverheadGasBlock() {
        if (level == null || storedGasAmount >= MAX_GAS_CAPACITY) return;

        BlockPos abovePos = worldPosition.above();
        BlockState aboveState = level.getBlockState(abovePos);

        if (aboveState.getBlock() instanceof GasBlock) {
            ResourceLocation gasId = BuiltInRegistries.BLOCK.getKey(aboveState.getBlock());

            // 250 mB intake per siphon tick batch
            int intakeAmount = 250; 
            if (canAcceptGas(gasId, intakeAmount)) {
                fillGas(gasId, intakeAmount);

                // Reduce gas block pressure or clear it
                if (aboveState.hasProperty(GasBlock.PRESSURE) && aboveState.getValue(GasBlock.PRESSURE) > 0) {
                    int currentPressure = aboveState.getValue(GasBlock.PRESSURE);
                    level.setBlock(abovePos, aboveState.setValue(GasBlock.PRESSURE, currentPressure - 1), 3);
                } else {
                    level.removeBlock(abovePos, false);
                }
            }
        }
    }

    private void processHydrotreating() {
        if (inputTank == null || outputTank == null || level == null) return;

        // Verify Blaze Burner below is present and superheated (SEETHING)
        BlockState stateBelow = level.getBlockState(worldPosition.below());
        boolean isSuperheated = stateBelow.hasProperty(BlazeBurnerBlock.HEAT_LEVEL) &&
                stateBelow.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.SEETHING;

        if (!isSuperheated) {
            if (isProcessing) {
                isProcessing = false;
                processingTicks = 0;
                notifyUpdate();
            }
            return;
        }

        IFluidHandler inputHandler = inputTank.getPrimaryHandler();
        IFluidHandler outputHandler = outputTank.getPrimaryHandler();

        FluidStack sulfurDieselStack = FluidStack.EMPTY;
        for (int i = 0; i < inputHandler.getTanks(); i++) {
            FluidStack stack = inputHandler.getFluidInTank(i);
            if (!stack.isEmpty() && BuiltInRegistries.FLUID.getKey(stack.getFluid()).getPath().contains("sulfur_diesel")) {
                sulfurDieselStack = stack;
                break;
            }
        }

        boolean hasSulfurDiesel = !sulfurDieselStack.isEmpty() && sulfurDieselStack.getAmount() >= 2;
        boolean hasHydrogen = storedGasId != null && 
                storedGasId.getPath().contains("hydrogen") && storedGasAmount >= 2;

        if (hasSulfurDiesel && hasHydrogen) {
            Fluid hydrotreatedFluid = SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get();
            FluidStack outputStack = new FluidStack(hydrotreatedFluid, 2);

            int accepted = outputHandler.fill(outputStack, IFluidHandler.FluidAction.SIMULATE);

            if (accepted >= 2) {
                if (!isProcessing) {
                    isProcessing = true;
                    notifyUpdate();
                }

                processingTicks++;

                if (processingTicks >= REQUIRED_TICKS) {
                    processingTicks = 0;

                    inputHandler.drain(new FluidStack(sulfurDieselStack.getFluid(), 2), IFluidHandler.FluidAction.EXECUTE);
                    drainGas(2, false);

                    outputHandler.fill(outputStack, IFluidHandler.FluidAction.EXECUTE);
                }
                return;
            }
        }

        if (isProcessing) {
            isProcessing = false;
            processingTicks = 0;
            notifyUpdate();
        }
    }

    private void spawnGasParticles() {
        if (storedGasAmount > 0 && storedGasId != null && level != null) {
            if (level.random.nextInt(20) == 0) {
                SimpleParticleType particleType = getParticleForStoredGas();
                if (particleType != null) {
                    double x = worldPosition.getX() + 0.4D + level.random.nextDouble() * 0.2D;
                    double y = worldPosition.getY() + 0.7D;
                    double z = worldPosition.getZ() + 0.4D + level.random.nextDouble() * 0.2D;

                    level.addParticle(
                        particleType,
                        x, y, z,
                        0.0001D,
                        0.002D,
                        0.0001D
                    );
                }
            }
        }
    }

    @Nullable
    public SimpleParticleType getParticleForStoredGas() {
        if (storedGasId == null) return null;
        for (GasRegistry.GasEntry entry : GasRegistry.getAll()) {
            if (entry.block.getId().equals(storedGasId)) {
                return entry.particle.get();
            }
        }
        return null;
    }

    public boolean canAcceptGas(ResourceLocation gasId, int amount) {
        if (gasId == null) return false;
        if (this.storedGasId != null && this.storedGasAmount > 0 && !this.storedGasId.equals(gasId)) {
            return false;
        }
        return (this.storedGasAmount + amount) <= MAX_GAS_CAPACITY;
    }

    public boolean canAcceptGas(int amount) {
        return (storedGasAmount + amount) <= MAX_GAS_CAPACITY;
    }

    public void fillGas(ResourceLocation gasId, int amount) {
        if (this.storedGasId == null || this.storedGasAmount == 0) {
            this.storedGasId = gasId;
            this.storedGasAmount = amount;
        } else if (this.storedGasId.equals(gasId)) {
            this.storedGasAmount = Math.min(MAX_GAS_CAPACITY, this.storedGasAmount + amount);
        }
        notifyUpdate();
    }

    public int drainGas(int amount, boolean simulate) {
        int drained = Math.min(storedGasAmount, amount);
        if (!simulate && drained > 0) {
            storedGasAmount -= drained;
            if (storedGasAmount <= 0) {
                storedGasId = null;
            }
            notifyUpdate();
        }
        return drained;
    }

    public ResourceLocation getStoredGasId() {
        return storedGasId;
    }

    public int getStoredGasAmount() {
        return storedGasAmount;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public int getProcessingTicks() {
        return processingTicks;
    }

    public FluidStack getPrimaryFluidStack() {
        if (inputTank != null && !inputTank.getPrimaryHandler().getFluidInTank(0).isEmpty()) {
            return inputTank.getPrimaryHandler().getFluidInTank(0);
        }
        if (outputTank != null && !outputTank.getPrimaryHandler().getFluidInTank(0).isEmpty()) {
            return outputTank.getPrimaryHandler().getFluidInTank(0);
        }
        return FluidStack.EMPTY;
    }

    public int getMaxFluidCapacity() {
        if (inputTank != null) {
            return inputTank.getPrimaryHandler().getTankCapacity(0);
        }
        return 10000;
    }

    public ItemStack getPrimaryItemStack() {
        if (inputInventory != null) {
            for (int i = 0; i < inputInventory.getSlots(); i++) {
                ItemStack stack = inputInventory.getStackInSlot(i);
                if (!stack.isEmpty()) return stack;
            }
        }
        if (outputInventory != null) {
            for (int i = 0; i < outputInventory.getSlots(); i++) {
                ItemStack stack = outputInventory.getStackInSlot(i);
                if (!stack.isEmpty()) return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (inputTank == null || outputTank == null) return null;

        IFluidHandler baseHandler = (side == Direction.DOWN)
                ? outputTank.getCapability()
                : new CombinedTankWrapper(inputTank.getCapability(), outputTank.getCapability());

        return new CombinedTankWrapper(baseHandler, gasTankHandler);
    }

    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (inputInventory == null || outputInventory == null) return null;
        if (side == Direction.DOWN) {
            return outputInventory;
        }
        return new CombinedInvWrapper(inputInventory, outputInventory);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        tooltip.add(Component.literal("  ").append(Component.literal("Gas Storage:").withStyle(ChatFormatting.GRAY)));
        if (storedGasId != null && storedGasAmount > 0) {
            String gasName = storedGasId.getPath().replace("_block", "").replace("_", " ");
            gasName = gasName.substring(0, 1).toUpperCase() + gasName.substring(1);
            tooltip.add(Component.literal("    ").append(Component.literal(gasName + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(storedGasAmount + " / " + MAX_GAS_CAPACITY + " mB").withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("    ").append(Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY)));
        }
        return true;
    }

    @Override
    public void read(CompoundTag compound, HolderLookup.Provider registers, boolean clientPacket) {
        super.read(compound, registers, clientPacket);
        if (compound.contains("GasId")) {
            this.storedGasId = ResourceLocation.parse(compound.getString("GasId"));
        } else {
            this.storedGasId = null;
        }
        this.storedGasAmount = compound.getInt("GasAmount");
        this.isProcessing = compound.getBoolean("IsProcessing");
        this.processingTicks = compound.getInt("ProcessingTicks");
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registers, boolean clientPacket) {
        super.write(compound, registers, clientPacket);
        if (storedGasId != null) {
            compound.putString("GasId", storedGasId.toString());
        }
        compound.putInt("GasAmount", storedGasAmount);
        compound.putBoolean("IsProcessing", isProcessing);
        compound.putInt("ProcessingTicks", processingTicks);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registers) {
        CompoundTag tag = super.getUpdateTag(registers);
        write(tag, registers, true);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            read(tag, lookupProvider, true);
        }
    }

    private class GasTankHandler implements IFluidHandler {
        @Override
        public int getTanks() { return 1; }

        @Override
        public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }

        @Override
        public int getTankCapacity(int tank) { return MAX_GAS_CAPACITY; }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (stack.isEmpty()) return false;
            String path = BuiltInRegistries.FLUID.getKey(stack.getFluid()).getPath();
            for (GasRegistry.GasEntry entry : GasRegistry.getAll()) {
                String gasName = entry.block.getId().getPath().replace("_block", "");
                if (path.contains(gasName)) return true;
            }
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            String path = BuiltInRegistries.FLUID.getKey(resource.getFluid()).getPath();
            for (GasRegistry.GasEntry entry : GasRegistry.getAll()) {
                String gasName = entry.block.getId().getPath().replace("_block", "");
                if (path.contains(gasName)) {
                    ResourceLocation gasId = entry.block.getId();
                    if (canAcceptGas(gasId, resource.getAmount())) {
                        int accepted = Math.min(resource.getAmount(), MAX_GAS_CAPACITY - storedGasAmount);
                        if (action.execute() && accepted > 0) {
                            fillGas(gasId, accepted);
                            notifyUpdate();
                        }
                        return accepted;
                    }
                }
            }
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    public static class CombinedTankWrapper implements IFluidHandler {
        private final IFluidHandler[] handlers;

        public CombinedTankWrapper(IFluidHandler... handlers) {
            this.handlers = handlers;
        }

        @Override
        public int getTanks() {
            int total = 0;
            for (IFluidHandler handler : handlers) {
                if (handler != null) total += handler.getTanks();
            }
            return total;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            for (IFluidHandler handler : handlers) {
                if (handler == null) continue;
                int tanks = handler.getTanks();
                if (tank < tanks) return handler.getFluidInTank(tank);
                tank -= tanks;
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            for (IFluidHandler handler : handlers) {
                if (handler == null) continue;
                int tanks = handler.getTanks();
                if (tank < tanks) return handler.getTankCapacity(tank);
                tank -= tanks;
            }
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            for (IFluidHandler handler : handlers) {
                if (handler == null) continue;
                int tanks = handler.getTanks();
                if (tank < tanks) return handler.isFluidValid(tank, stack);
                tank -= tanks;
            }
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            int filledTotal = 0;
            FluidStack remaining = resource.copy();

            for (IFluidHandler handler : handlers) {
                if (handler == null) continue;
                int filled = handler.fill(remaining, action);
                filledTotal += filled;
                remaining.shrink(filled);
                if (remaining.isEmpty()) break;
            }
            return filledTotal;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            for (IFluidHandler handler : handlers) {
                if (handler == null) continue;
                FluidStack drained = handler.drain(resource, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            for (IFluidHandler handler : handlers) {
                if (handler == null) continue;
                FluidStack drained = handler.drain(maxDrain, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }
    }
}