package com.deepu.create_crude.client.gui;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.DistillationControllerBlock;
import com.deepu.create_crude.block.entity.DistillationControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DistillationContainerMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Level level;
    private final ContainerData data;
    private DistillationControllerBlockEntity be;

    // Data indices
    private static final int INPUT_AMOUNT = 0;
    private static final int INPUT_CAPACITY = 1;
    private static final int OUTPUT_START = 2; // 6 outputs * 2 = 12 slots
    private static final int HEAT_LEVEL = 14;
    private static final int ACTIVE = 15;

    public DistillationContainerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, extraData.readBlockPos());
    }

    public DistillationContainerMenu(int id, Inventory inv, BlockPos pos) {
        super(CreateCrude.DISTILLATION_CONTAINER.get(), id);
        this.pos = pos;
        this.level = inv.player.level();
        this.data = new SimpleContainerData(16);
        addDataSlots(data);

        if (!level.isClientSide) {
            be = (DistillationControllerBlockEntity) level.getBlockEntity(pos);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (level.isClientSide) return;
        if (be == null) {
            be = (DistillationControllerBlockEntity) level.getBlockEntity(pos);
            if (be == null) return;
        }

        data.set(INPUT_AMOUNT, be.getCrudeOilInBaseTanks());
        data.set(INPUT_CAPACITY, be.getCrudeOilCapacityInBaseTanks());

        for (int i = 0; i < 6; i++) {
            int idx = OUTPUT_START + i * 2;
            data.set(idx, be.getProductTotalAmount(i));
            data.set(idx + 1, be.getProductTotalCapacity(i));
        }

        data.set(HEAT_LEVEL, be.getHeatLevel());
        data.set(ACTIVE, be.getBlockState().getValue(DistillationControllerBlock.ACTIVE) ? 1 : 0);
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof DistillationControllerBlockEntity &&
               player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public DistillationControllerBlockEntity getBlockEntity() {
        if (level.isClientSide && be == null) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof DistillationControllerBlockEntity) {
                be = (DistillationControllerBlockEntity) entity;
            }
        }
        return be;
    }

    public int getInputAmount() { return data.get(INPUT_AMOUNT); }
    public int getInputCapacity() { return data.get(INPUT_CAPACITY); }
    public int getOutputAmount(int idx) { return data.get(OUTPUT_START + idx * 2); }
    public int getOutputCapacity(int idx) { return data.get(OUTPUT_START + idx * 2 + 1); }
    public int getHeatLevel() { return data.get(HEAT_LEVEL); }
    public boolean isActive() { return data.get(ACTIVE) == 1; }
}