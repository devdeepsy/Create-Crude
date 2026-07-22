package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.SteelFluidTankBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import java.util.Map;

public class SteelFluidTankBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int CAPACITY = 16000;

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                SteelFluidTankBlockEntity controller = getControllerBE();
                if (controller != null && controller.isController()) {
                    level.updateNeighbourForOutputSignal(controller.worldPosition, getBlockState().getBlock());
                }
            }
        }
    };

    private int productIndex = -1;

    // Multiblock data
    public BlockPos controller;
    public int height = 1;
    public int width = 1;
    public int depth = 1;
    public boolean window = true;
    public boolean updateConnectivity = false;

    public SteelFluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.STEEL_FLUID_TANK_BE.get(), pos, state);
    }

    // ========== BASIC ACCESSORS ==========

    public FluidTank getTank() {
        return this.tank;
    }

    public void setProductIndex(int index) {
        this.productIndex = index;
    }

    public int getProductIndex() {
        return productIndex;
    }

    public int fillTank(FluidStack resource, IFluidHandler.FluidAction action) {
        return tank.fill(resource, action);
    }

    // ========== MULTIBLOCK CONTROLLER LOGIC ==========

    public boolean isController() {
        return controller == null || controller.equals(worldPosition);
    }

    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Nullable
    public SteelFluidTankBlockEntity getControllerBE() {
        if (isController()) return this;
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(controller);
        return be instanceof SteelFluidTankBlockEntity ? (SteelFluidTankBlockEntity) be : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null) return false;

        // 1. Header Title
        tooltip.add(Component.literal("  ")
                .append(Component.translatable("block.create_crude.steel_fluid_tank"))
                .withStyle(ChatFormatting.GOLD));

        int totalCapacity = controllerBE.width * controllerBE.height * controllerBE.depth * CAPACITY;

        // 2. Aggregate fluid amounts by unique fluid type across all connected sub-tanks
        Map<Fluid, Integer> fluidAmounts = new LinkedHashMap<>();
        Map<Fluid, FluidStack> fluidRepresentations = new LinkedHashMap<>();
        int combinedFluidAmount = 0;

        for (SteelFluidTankBlockEntity tankBE : controllerBE.getAllTanks()) {
            FluidStack stack = tankBE.getTank().getFluid();
            if (!stack.isEmpty()) {
                Fluid fluid = stack.getFluid();
                fluidAmounts.put(fluid, fluidAmounts.getOrDefault(fluid, 0) + stack.getAmount());
                fluidRepresentations.putIfAbsent(fluid, stack);
                combinedFluidAmount += stack.getAmount();
            }
        }

        // 3. Render Tooltip
        if (fluidAmounts.isEmpty()) {
            // Empty State
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("gui.goggles.empty"))
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.literal("    ")
                    .append(Component.literal(String.format("0 / %,d mB", totalCapacity)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (fluidAmounts.size() == 1) {
            // Single Fluid State (Classic Create look)
            FluidStack stack = fluidRepresentations.values().iterator().next();
            int amount = fluidAmounts.values().iterator().next();

            tooltip.add(Component.literal("    ")
                    .append(stack.getHoverName())
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.literal("    ")
                    .append(Component.literal(String.format("%,d / %,d mB", amount, totalCapacity)))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            // Multi-Fluid State: Lists each stored fluid and its amount
            for (Map.Entry<Fluid, Integer> entry : fluidAmounts.entrySet()) {
                FluidStack stack = fluidRepresentations.get(entry.getKey());
                int amount = entry.getValue();

                tooltip.add(Component.literal("    ")
                        .append(stack.getHoverName())
                        .append(": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%,d mB", amount))
                                .withStyle(ChatFormatting.GOLD)));
            }

            // Total overall tank capacity line
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Total: "))
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(String.format("%,d / %,d mB", combinedFluidAmount, totalCapacity)))
                    .withStyle(ChatFormatting.GOLD));
        }

        return true;
    }

   public void tryFormMultiblock() {
        if (level == null || level.isClientSide) return;

        int curY = worldPosition.getY();
        List<BlockPos> layerBlocks = getConnectedLayer(curY, worldPosition);

        // Calculate bounding box of the current horizontal layer
        int minX = worldPosition.getX(), maxX = worldPosition.getX();
        int minZ = worldPosition.getZ(), maxZ = worldPosition.getZ();

        for (BlockPos p : layerBlocks) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int expectedCount = width * depth;

        // Check if current layer forms a complete solid rectangle
        boolean isCompleteLayer = (layerBlocks.size() == expectedCount);

        if (!isCompleteLayer) {
            // Incomplete layer (e.g. 3 blocks of a 2x2) -> revert connected layer to 1x1x1 tanks
            for (BlockPos p : layerBlocks) {
                resetToSingleTank(p);
            }
            return;
        }

        // Expand DOWN and UP ONLY where the horizontal footprint matches 100%
        int minY = curY;
        int maxY = curY;

        while (isLayerCompleteAndMatching(minY - 1, minX, maxX, minZ, maxZ)) {
            minY--;
        }

        while (isLayerCompleteAndMatching(maxY + 1, minX, maxX, minZ, maxZ)) {
            maxY++;
        }

        int height = maxY - minY + 1;
        BlockPos controllerPos = new BlockPos(minX, minY, minZ);

        // Form the multiblock cuboid across all valid matching layers
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity tankBE) {
                        tankBE.controller = controllerPos;
                        tankBE.width = width;
                        tankBE.height = height;
                        tankBE.depth = depth;
                        tankBE.updateConnectivity = false;
                        tankBE.updateShape();
                        tankBE.setChanged();
                        tankBE.sendData();
                    }
                }
            }
        }
    }
    private List<BlockPos> getConnectedLayer(int y, BlockPos startPos) {
        List<BlockPos> visited = new ArrayList<>();
        List<BlockPos> toVisit = new ArrayList<>();
        toVisit.add(startPos);

        while (!toVisit.isEmpty()) {
            BlockPos pos = toVisit.remove(0);
            if (visited.contains(pos)) continue;
            visited.add(pos);

            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(d);
                if (neighbor.getY() == y && level.getBlockEntity(neighbor) instanceof SteelFluidTankBlockEntity) {
                    if (!visited.contains(neighbor) && !toVisit.contains(neighbor)) {
                        toVisit.add(neighbor);
                    }
                }
            }
        }
        return visited;
    }

    private boolean isLayerCompleteAndMatching(int y, int minX, int maxX, int minZ, int maxZ) {
        int expectedWidth = maxX - minX + 1;
        int expectedDepth = maxZ - minZ + 1;
        int expectedCount = expectedWidth * expectedDepth;

        // Check that every position in the rectangle contains a tank block entity
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!(level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity)) {
                    return false;
                }
            }
        }

        // Verify there are no extra attached tank blocks outside the rectangle on layer Y
        List<BlockPos> layer = getConnectedLayer(y, new BlockPos(minX, y, minZ));
        return layer.size() == expectedCount;
    }

    private void resetToSingleTank(BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
            tankBE.controller = pos;
            tankBE.width = 1;
            tankBE.height = 1;
            tankBE.depth = 1;
            tankBE.updateConnectivity = false;
            tankBE.updateShape();
            tankBE.setChanged();
            tankBE.sendData();
        }
    }

    public void updateShape() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SteelFluidTankBlock)) return;

        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null) return;

        boolean isTop = worldPosition.getY() == controllerBE.worldPosition.getY() + controllerBE.height - 1;
        boolean isBottom = worldPosition.getY() == controllerBE.worldPosition.getY();

        int dx = worldPosition.getX() - controllerBE.worldPosition.getX();
        int dz = worldPosition.getZ() - controllerBE.worldPosition.getZ();
        int w = controllerBE.width;
        int d = controllerBE.depth;

        SteelFluidTankBlock.TankShape shape;
        if (w == 1 && d == 1) {
            shape = SteelFluidTankBlock.TankShape.WINDOW;
        } else if (w == 2 && d == 2) {
            // 2x2 Tank: 4 corner glass windows meeting in the center
            if (dx == 0 && dz == 0) shape = SteelFluidTankBlock.TankShape.WINDOW_NW;
            else if (dx == 0 && dz == d - 1) shape = SteelFluidTankBlock.TankShape.WINDOW_SW;
            else if (dx == w - 1 && dz == 0) shape = SteelFluidTankBlock.TankShape.WINDOW_NE;
            else shape = SteelFluidTankBlock.TankShape.WINDOW_SE;
        } else {
            // 3x3 or larger tanks: Solid steel corners + single center window on each wall
            boolean isCorner = (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1);
            boolean isInterior = dx > 0 && dx < w - 1 && dz > 0 && dz < d - 1;

            if (isInterior || isCorner) {
                shape = SteelFluidTankBlock.TankShape.PLAIN;
            } else {
                // Center block of each outer wall gets the window
                boolean isXCenter = (dx == w / 2) && (dz == 0 || dz == d - 1);
                boolean isZCenter = (dz == d / 2) && (dx == 0 || dx == w - 1);

                if (isXCenter || isZCenter) {
                    shape = SteelFluidTankBlock.TankShape.WINDOW;
                } else {
                    shape = SteelFluidTankBlock.TankShape.PLAIN;
                }
            }
        }

        if (!controllerBE.window) {
            shape = SteelFluidTankBlock.TankShape.PLAIN;
        }

        BlockState newState = state
                .setValue(SteelFluidTankBlock.TOP, isTop)
                .setValue(SteelFluidTankBlock.BOTTOM, isBottom)
                .setValue(SteelFluidTankBlock.SHAPE, shape);

        level.setBlock(worldPosition, newState, 2);
    }

    public void updateAllShapes() {
        if (level == null || !isController()) return;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    BlockPos pos = worldPosition.offset(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof SteelFluidTankBlockEntity tankBE) {
                        tankBE.updateShape();
                    }
                }
            }
        }
    }

    public void toggleWindows() {
        if (!isController()) {
            SteelFluidTankBlockEntity controllerBE = getControllerBE();
            if (controllerBE != null) controllerBE.toggleWindows();
            return;
        }
        window = !window;
        updateAllShapes();
        setChanged();
        sendData();
    }

    private void sendData() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ========== FLUID HANDLER ==========

    private List<SteelFluidTankBlockEntity> getAllTanks() {
        List<SteelFluidTankBlockEntity> list = new ArrayList<>();
        if (level == null) {
            list.add(this);
            return list;
        }
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null) {
            list.add(this);
            return list;
        }
        for (int x = 0; x < controllerBE.width; x++) {
            for (int z = 0; z < controllerBE.depth; z++) {
                for (int y = 0; y < controllerBE.height; y++) {
                    BlockPos p = controllerBE.worldPosition.offset(x, y, z);
                    if (level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity tankBE) {
                        list.add(tankBE);
                    }
                }
            }
        }
        return list;
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return new IFluidHandler() {
            private List<SteelFluidTankBlockEntity> getTankList() {
                return getAllTanks();
            }
            @Override
            public int getTanks() {
                return getTankList().size();
            }
            @Override
            public FluidStack getFluidInTank(int tankIndex) {
                List<SteelFluidTankBlockEntity> tanks = getTankList();
                if (tankIndex >= 0 && tankIndex < tanks.size()) {
                    return tanks.get(tankIndex).getTank().getFluidInTank(0);
                }
                return FluidStack.EMPTY;
            }
            @Override
            public int getTankCapacity(int tankIndex) {
                List<SteelFluidTankBlockEntity> tanks = getTankList();
                if (tankIndex >= 0 && tankIndex < tanks.size()) {
                    return tanks.get(tankIndex).getTank().getTankCapacity(0);
                }
                return 0;
            }
            @Override
            public boolean isFluidValid(int tankIndex, FluidStack stack) {
                List<SteelFluidTankBlockEntity> tanks = getTankList();
                if (tankIndex >= 0 && tankIndex < tanks.size()) {
                    return tanks.get(tankIndex).getTank().isFluidValid(stack);
                }
                return false;
            }
            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return 0;
                int remaining = resource.getAmount();
                int filledTotal = 0;

                for (SteelFluidTankBlockEntity tankBE : getTankList()) {
                    FluidTank internal = tankBE.getTank();
                    if (!internal.isEmpty() && internal.getFluid().getFluid() == resource.getFluid()) {
                        FluidStack partial = resource.copyWithAmount(remaining);
                        int filled = internal.fill(partial, action);
                        filledTotal += filled;
                        remaining -= filled;
                        if (remaining <= 0) return filledTotal;
                    }
                }
                for (SteelFluidTankBlockEntity tankBE : getTankList()) {
                    FluidTank internal = tankBE.getTank();
                    if (internal.isEmpty()) {
                        FluidStack partial = resource.copyWithAmount(remaining);
                        int filled = internal.fill(partial, action);
                        filledTotal += filled;
                        remaining -= filled;
                        if (remaining <= 0) return filledTotal;
                    }
                }
                return filledTotal;
            }
            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return FluidStack.EMPTY;
                List<SteelFluidTankBlockEntity> tanks = getTankList();
                for (SteelFluidTankBlockEntity tankBE : tanks) {
                    FluidTank internal = tankBE.getTank();
                    if (internal.getFluid().getFluid() == resource.getFluid()) {
                        return internal.drain(resource, action);
                    }
                }
                return FluidStack.EMPTY;
            }
            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                if (maxDrain <= 0) return FluidStack.EMPTY;
                List<SteelFluidTankBlockEntity> tanks = getTankList();
                for (SteelFluidTankBlockEntity tankBE : tanks) {
                    FluidTank internal = tankBE.getTank();
                    if (!internal.isEmpty()) {
                        return internal.drain(maxDrain, action);
                    }
                }
                return FluidStack.EMPTY;
            }
        };
    }

    public void tick() {
        if (level == null || level.isClientSide) return;
        if (updateConnectivity) {
            updateConnectivity = false;
            tryFormMultiblock();
        }
    }

    // ========== NBT ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fluid", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("productIndex", productIndex);
        if (controller != null) tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.putInt("Height", height);
        tag.putInt("Width", width);
        tag.putInt("Depth", depth);
        tag.putBoolean("Window", window);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("fluid"));
        productIndex = tag.getInt("productIndex");
        if (tag.contains("Controller")) {
            controller = NbtUtils.readBlockPos(tag, "Controller").get();
        } else {
            controller = null;
        }
        height = tag.getInt("Height");
        if (height < 1) height = 1;
        width = tag.getInt("Width");
        if (width < 1) width = 1;
        depth = tag.getInt("Depth");
        if (depth < 1) depth = 1;
        window = tag.getBoolean("Window");
        updateConnectivity = true;
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
}