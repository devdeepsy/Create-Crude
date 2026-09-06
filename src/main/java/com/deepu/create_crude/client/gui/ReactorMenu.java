package com.deepu.create_crude.client.gui;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.ReactorBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ReactorMenu extends AbstractContainerMenu {

    private final ReactorBlockEntity blockEntity;
    private final ContainerData data;

    // Client-side Constructor
    public ReactorMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, (ReactorBlockEntity) inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(4));
    }

    // Common/Server Constructor
    public ReactorMenu(int containerId, Inventory inv, ReactorBlockEntity entity, ContainerData data) {
        super(CreateCrude.REACTOR_MENU.get(), containerId);
        this.blockEntity = entity;
        this.data = data;

        addDataSlots(data);

        // Reactor Slots
        this.addSlot(new SlotItemHandler(entity.getItemHandler(null), 0, 56, 35)); // Catalyst Slot
        this.addSlot(new SlotItemHandler(entity.getItemHandler(null), 1, 116, 35)); // Output Slot

        // Player Inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Player Hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    public int getEthylene() { return data.get(0); }
    public int getPropylene() { return data.get(1); }
    public int getProgress() { return data.get(2); }
    public int getMaxProgress() { return data.get(3); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index < 2) {
                if (!this.moveItemStackTo(stackInSlot, 2, 38, true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, CreateCrude.REACTOR_BLOCK.get());
    }
}