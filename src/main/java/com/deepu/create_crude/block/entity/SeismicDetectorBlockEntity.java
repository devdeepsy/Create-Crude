package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.block.SeismicDetectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

public class SeismicDetectorBlockEntity extends BlockEntity {
    private int fuelTicks = 0;
    private int scanCooldown = 0;
    private ItemStack slottedFilterItem = ItemStack.EMPTY;
    private int currentY = -64;
    private List<BlockPos> oilPositions = new ArrayList<>();

    public SeismicDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(com.deepu.create_crude.CreateCrude.SEISMIC_DETECTOR_BE.get(), pos, state);
    }

    // ========== GETTERS & SETTERS ==========
    
    public ItemStack getSlottedFilterItem() {
        return this.slottedFilterItem;
    }
    
    public List<BlockPos> getOilPositions() {
        return oilPositions;
    }

    public int getCurrentY() {
        return currentY;
    }

    public void setCurrentY(int y) {
        this.currentY = y;
        this.setChanged();
    }

    public int getFuelTicks() {
        return fuelTicks;
    }

    public int getOilCountAtLevel() {
        return (int) oilPositions.stream()
            .filter(pos -> pos.getY() == currentY)
            .count();
    }

    public void setSlottedFilterItem(ItemStack filterStack) {
        this.slottedFilterItem = filterStack.copyWithCount(1);
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition,
                this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void addFuel(int amount) {
        this.fuelTicks += amount;
        if (this.level != null && !this.getBlockState().getValue(SeismicDetectorBlock.IS_SCANNING)) {
            this.level.setBlockAndUpdate(this.worldPosition,
                this.getBlockState().setValue(SeismicDetectorBlock.IS_SCANNING, true));
        }
        this.setChanged();
    }

    // ========== SCANNING METHODS ==========
    
    public void scanAtYLevel(int yLevel) {
        if (level == null || level.isClientSide) return;
        
        if (slottedFilterItem.isEmpty()) {
            notifyNearbyPlayers(level, worldPosition, Component.literal("§cNo filter item!§r"));
            return;
        }
        
        this.currentY = yLevel;
        performSeismicScanAtLevel(level, worldPosition, yLevel);
    }

    public void scanForOilFull() {
        if (level == null || level.isClientSide) return;
        
        if (slottedFilterItem.isEmpty()) {
            notifyNearbyPlayers(level, worldPosition, Component.literal("§cNo filter item!§r"));
            return;
        }
        
        performSeismicScan(level, worldPosition);
    }

    private void performSeismicScanAtLevel(Level level, BlockPos centerPos, int yLevel) {
        int radius = 16;
        List<BlockPos> foundPositions = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos scanPos = new BlockPos(centerPos.getX() + x, yLevel, centerPos.getZ() + z);
                FluidState fluidState = level.getFluidState(scanPos);
                
                if (!fluidState.isEmpty() && fluidState.getFluidType().toString().contains("crude_oil")) {
                    if (checkIsIsolatedPocket(level, scanPos)) {
                        continue;
                    }
                    foundPositions.add(scanPos.immutable());
                }
            }
        }
        
        this.oilPositions = foundPositions;
        updateAttachedScreens(foundPositions, yLevel);
        this.setChanged();
        notifyNearbyPlayers(level, centerPos, Component.literal("§2Scan at Y=" + yLevel + " complete! Found " + foundPositions.size() + " deposits§r"));
    }

    private void performSeismicScan(Level level, BlockPos centerPos) {
        int radius = 16;
        List<BlockPos> foundPositions = new ArrayList<>();
        int shallowestY = centerPos.getY();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = centerPos.getY(); y > level.getMinBuildHeight(); y--) {
                    BlockPos scanPos = centerPos.offset(x, y - centerPos.getY(), z);
                    FluidState fluidState = level.getFluidState(scanPos);

                    if (!fluidState.isEmpty() && fluidState.getFluidType().toString().contains("crude_oil")) {
                        if (checkIsIsolatedPocket(level, scanPos)) {
                            continue;
                        }
                        foundPositions.add(scanPos.immutable());
                        if (y > shallowestY) shallowestY = y;
                        break;
                    }
                }
            }
        }
        
        this.oilPositions = foundPositions;
        updateAttachedScreens(foundPositions, shallowestY);
        notifyNearbyPlayers(level, centerPos, Component.literal("§2Full scan complete! Found " + foundPositions.size() + " oil deposits§r"));
    }

    private boolean checkIsIsolatedPocket(Level level, BlockPos pos) {
        int adjacentOilBlocks = 0;
        if (level.getFluidState(pos.east()).getFluidType().toString().contains("crude_oil"))  adjacentOilBlocks++;
        if (level.getFluidState(pos.west()).getFluidType().toString().contains("crude_oil"))  adjacentOilBlocks++;
        if (level.getFluidState(pos.north()).getFluidType().toString().contains("crude_oil")) adjacentOilBlocks++;
        if (level.getFluidState(pos.south()).getFluidType().toString().contains("crude_oil")) adjacentOilBlocks++;
        return adjacentOilBlocks < 2;
    }

    private void updateAttachedScreens(List<BlockPos> foundPositions, int depthY) {
    if (this.level == null) return;

    // Display screen functionality temporarily disabled
    // if (this.level.getBlockEntity(this.worldPosition.above()) instanceof DisplayScreenBlockEntity topScreen) {
    //     topScreen.setOilData(foundPositions, depthY, this.worldPosition);
    // }
    //
    // if (this.level.getBlockEntity(this.worldPosition.below()) instanceof DisplayScreenBlockEntity bottomScreen) {
    //     bottomScreen.setOilData(foundPositions, depthY, this.worldPosition);
    // }
    
    // Just log the results for now
    System.out.println("Found " + foundPositions.size() + " oil deposits at Y=" + depthY);
}

    private void notifyNearbyPlayers(Level level, BlockPos pos, Component message) {
        AABB searchArea = new AABB(pos).inflate(8.0);
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, searchArea);
        for (Player player : nearbyPlayers) {
            player.displayClientMessage(message, true);
        }
    }

    // ========== TICK METHOD ==========
    
    public static void tick(Level level, BlockPos pos, BlockState state, SeismicDetectorBlockEntity blockEntity) {
        if (blockEntity.scanCooldown > 0) {
            blockEntity.scanCooldown--;
        }

        if (blockEntity.fuelTicks > 0) {
            blockEntity.fuelTicks--;

            if (blockEntity.fuelTicks % 40 == 0 && blockEntity.scanCooldown == 0) {
                blockEntity.performSeismicScan(level, pos);
                blockEntity.scanCooldown = 100;
            }

            if (blockEntity.fuelTicks <= 0) {
                level.setBlockAndUpdate(pos, state.setValue(SeismicDetectorBlock.IS_SCANNING, false));
            }

            blockEntity.setChanged();
        }
    }

    // ========== NBT & NETWORKING ==========
    
    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FuelTicks", this.fuelTicks);
        tag.putInt("ScanCooldown", this.scanCooldown);
        tag.putInt("CurrentY", this.currentY);
        
        if (!this.slottedFilterItem.isEmpty()) {
            tag.put("FilterItem", this.slottedFilterItem.save(registries, new CompoundTag()));
        }
        
        long[] packed = oilPositions.stream().mapToLong(BlockPos::asLong).toArray();
        tag.putLongArray("OilPositions", packed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.fuelTicks = tag.getInt("FuelTicks");
        this.scanCooldown = tag.getInt("ScanCooldown");
        this.currentY = tag.getInt("CurrentY");
        
        if (tag.contains("FilterItem")) {
            this.slottedFilterItem = ItemStack.parse(registries, tag.getCompound("FilterItem"))
                .orElse(ItemStack.EMPTY);
        } else {
            this.slottedFilterItem = ItemStack.EMPTY;
        }
        
        oilPositions.clear();
        if (tag.contains("OilPositions")) {
            for (long p : tag.getLongArray("OilPositions"))
                oilPositions.add(BlockPos.of(p));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
}