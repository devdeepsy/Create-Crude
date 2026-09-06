package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.client.gui.ReactorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReactorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_GAS_CAPACITY = 10000;
    
    private int ethyleneAmount = 0;
    private int propyleneAmount = 0;
    private int progressTimer = 0;
    private final int maxProgress = 100;

    // Slot 0: Catalyst Input, Slot 1: Solid Output
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // ContainerData syncs integers to client side for GUI progress/fluid bars
    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> ethyleneAmount;
                case 1 -> propyleneAmount;
                case 2 -> progressTimer;
                case 3 -> maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> ethyleneAmount = value;
                case 1 -> propyleneAmount = value;
                case 2 -> progressTimer = value;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.REACTOR_BE.get(), pos, state);
    }

    public void serverTick() {
        if (canProcess()) {
            progressTimer++;
            if (progressTimer >= maxProgress) {
                progressTimer = 0;
                executeReaction();
            }
            setChanged();
        } else {
            if (progressTimer != 0) {
                progressTimer = 0;
                setChanged();
            }
        }
    }

    private boolean canProcess() {
        // Example requirement: 100 mB of both gases + catalyst item in slot 0
        boolean hasGases = ethyleneAmount >= 100 && propyleneAmount >= 100;
        ItemStack catalyst = inventory.getStackInSlot(0);
        boolean hasCatalyst = !catalyst.isEmpty() && catalyst.is(CreateCrude.SULFUR_POWDER_ITEM.get()); // Replace with your catalyst dust

        ItemStack output = inventory.getStackInSlot(1);
        boolean outputHasSpace = output.isEmpty() || (output.is(CreateCrude.ASPHALT_ITEM.get()) && output.getCount() < output.getMaxStackSize()); // Replace output item

        return hasGases && hasCatalyst && outputHasSpace;
    }

    private void executeReaction() {
        ethyleneAmount -= 100;
        propyleneAmount -= 100;

        inventory.getStackInSlot(0).shrink(1);

        ItemStack outputSlot = inventory.getStackInSlot(1);
        if (outputSlot.isEmpty()) {
            inventory.setStackInSlot(1, new ItemStack(CreateCrude.ASPHALT_ITEM.get(), 1));
        } else {
            outputSlot.grow(1);
        }
        notifyUpdate();
    }

    public boolean canAcceptGas(ResourceLocation gasId, int amount) {
        String path = gasId.getPath();
        if (path.contains("ethylene") && ethyleneAmount + amount <= MAX_GAS_CAPACITY) return true;
        return path.contains("propylene") && propyleneAmount + amount <= MAX_GAS_CAPACITY;
    }

    public int fillGas(ResourceLocation gasId, int amount) {
        String path = gasId.getPath();
        int filled = 0;
        if (path.contains("ethylene")) {
            filled = Math.min(amount, MAX_GAS_CAPACITY - ethyleneAmount);
            ethyleneAmount += filled;
        } else if (path.contains("propylene")) {
            filled = Math.min(amount, MAX_GAS_CAPACITY - propyleneAmount);
            propyleneAmount += filled;
        }
        if (filled > 0) notifyUpdate();
        return filled;
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return inventory;
    }

    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Polymerization Reactor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReactorMenu(containerId, playerInventory, this, this.dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Ethylene", ethyleneAmount);
        tag.putInt("Propylene", propyleneAmount);
        tag.putInt("Progress", progressTimer);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ethyleneAmount = tag.getInt("Ethylene");
        propyleneAmount = tag.getInt("Propylene");
        progressTimer = tag.getInt("Progress");
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}