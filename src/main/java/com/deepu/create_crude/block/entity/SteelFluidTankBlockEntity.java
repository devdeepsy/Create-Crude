package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModFluids;
import com.deepu.create_crude.SulfurFluids;
import com.deepu.create_crude.ModParticles;
import com.deepu.create_crude.block.SteelFluidTankBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    // Gas Storage Fields
    @Nullable
    private ResourceLocation storedGasId = null;
    private int storedGasAmount = 0;

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

    public FluidTank getTank() {
        return this.tank;
    }

    public void setProductIndex(int index) {
        this.productIndex = index;
    }

    public int getProductIndex() {
        return productIndex;
    }

    // ========== GAS STORAGE API ==========

    @Nullable
    public ResourceLocation getStoredGasId() {
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        return controllerBE != null && controllerBE != this ? controllerBE.getStoredGasId() : this.storedGasId;
    }

    public int getStoredGasAmount() {
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        return controllerBE != null && controllerBE != this ? controllerBE.getStoredGasAmount() : this.storedGasAmount;
    }

    public int getGasCapacity() {
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null) return CAPACITY;
        return controllerBE.width * controllerBE.height * controllerBE.depth * CAPACITY;
    }

    public int fillGas(ResourceLocation gasId, int amount, boolean simulate) {
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null && controllerBE != this) {
            return controllerBE.fillGas(gasId, amount, simulate);
        }
        if (amount <= 0 || gasId == null) return 0;
        if (this.storedGasId != null && !this.storedGasId.equals(gasId)) return 0;

        int maxCap = getGasCapacity();
        int space = maxCap - this.storedGasAmount;
        int toAdd = Math.min(space, amount);

        if (!simulate && toAdd > 0) {
            this.storedGasId = gasId;
            this.storedGasAmount += toAdd;
            setChanged();
            sendData();
        }
        return toAdd;
    }

    public int drainGas(int amount, boolean simulate) {
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null && controllerBE != this) {
            return controllerBE.drainGas(amount, simulate);
        }
        if (storedGasAmount <= 0 || storedGasId == null) return 0;

        int toDrain = Math.min(storedGasAmount, amount);
        if (!simulate && toDrain > 0) {
            storedGasAmount -= toDrain;
            if (storedGasAmount <= 0) {
                storedGasAmount = 0;
                storedGasId = null;
            }
            setChanged();
            sendData();
        }
        return toDrain;
    }

    // ========== MULTIBLOCK CONTROLLER LOGIC ==========

    public boolean isController() {
        return controller == null || controller.equals(worldPosition);
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

        tooltip.add(Component.literal("  ")
                .append(Component.translatable("block.create_crude.steel_fluid_tank"))
                .withStyle(ChatFormatting.GOLD));

        int totalCapacity = controllerBE.getGasCapacity();

        // Check stored gas first
        if (controllerBE.getStoredGasAmount() > 0 && controllerBE.getStoredGasId() != null) {
            ResourceLocation gasId = controllerBE.getStoredGasId();
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("block." + gasId.getNamespace() + "." + gasId.getPath()))
                    .withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.literal("    ")
                    .append(Component.literal(String.format("%,d / %,d mB (Gas)", controllerBE.getStoredGasAmount(), totalCapacity)))
                    .withStyle(ChatFormatting.GOLD));
            return true;
        }

        // Standard Fluid HUD Display
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

        if (fluidAmounts.isEmpty()) {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("gui.goggles.empty"))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("    ")
                    .append(Component.literal(String.format("0 / %,d mB", totalCapacity)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (fluidAmounts.size() == 1) {
            FluidStack stack = fluidRepresentations.values().iterator().next();
            int amount = fluidAmounts.values().iterator().next();

            tooltip.add(Component.literal("    ")
                    .append(stack.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("    ")
                    .append(Component.literal(String.format("%,d / %,d mB", amount, totalCapacity)))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            for (Map.Entry<Fluid, Integer> entry : fluidAmounts.entrySet()) {
                FluidStack stack = fluidRepresentations.get(entry.getKey());
                tooltip.add(Component.literal("    ")
                        .append(stack.getHoverName())
                        .append(": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%,d mB", entry.getValue()))
                                .withStyle(ChatFormatting.GOLD)));
            }
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

        if (layerBlocks.size() != expectedCount) {
            for (BlockPos p : layerBlocks) resetToSingleTank(p);
            return;
        }

        int minY = curY, maxY = curY;
        while (isLayerCompleteAndMatching(minY - 1, minX, maxX, minZ, maxZ)) minY--;
        while (isLayerCompleteAndMatching(maxY + 1, minX, maxX, minZ, maxZ)) maxY++;

        int height = maxY - minY + 1;
        BlockPos controllerPos = new BlockPos(minX, minY, minZ);

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
        int expectedCount = (maxX - minX + 1) * (maxZ - minZ + 1);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!(level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity)) return false;
            }
        }
        return getConnectedLayer(y, new BlockPos(minX, y, minZ)).size() == expectedCount;
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
            if (dx == 0 && dz == 0) shape = SteelFluidTankBlock.TankShape.WINDOW_NW;
            else if (dx == 0 && dz == d - 1) shape = SteelFluidTankBlock.TankShape.WINDOW_SW;
            else if (dx == w - 1 && dz == 0) shape = SteelFluidTankBlock.TankShape.WINDOW_NE;
            else shape = SteelFluidTankBlock.TankShape.WINDOW_SE;
        } else {
            boolean isCorner = (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1);
            boolean isInterior = dx > 0 && dx < w - 1 && dz > 0 && dz < d - 1;

            if (isInterior || isCorner) {
                shape = SteelFluidTankBlock.TankShape.PLAIN;
            } else {
                boolean isXCenter = (dx == w / 2) && (dz == 0 || dz == d - 1);
                boolean isZCenter = (dz == d / 2) && (dx == 0 || dx == w - 1);
                shape = (isXCenter || isZCenter) ? SteelFluidTankBlock.TankShape.WINDOW : SteelFluidTankBlock.TankShape.PLAIN;
            }
        }

        if (!controllerBE.window) shape = SteelFluidTankBlock.TankShape.PLAIN;

        level.setBlock(worldPosition, state
                .setValue(SteelFluidTankBlock.TOP, isTop)
                .setValue(SteelFluidTankBlock.BOTTOM, isBottom)
                .setValue(SteelFluidTankBlock.SHAPE, shape), 2);
    }

    public void updateAllShapes() {
        if (level == null || !isController()) return;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    BlockPos pos = worldPosition.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
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

    public List<SteelFluidTankBlockEntity> getAllTanks() {
        List<SteelFluidTankBlockEntity> list = new ArrayList<>();
        if (level == null) { list.add(this); return list; }
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null) { list.add(this); return list; }

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

    public List<SteelFluidTankBlockEntity> getSameLayerTanks() {
        List<SteelFluidTankBlockEntity> list = new ArrayList<>();
        if (level == null) { list.add(this); return list; }
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null) { list.add(this); return list; }

        for (int x = 0; x < controllerBE.width; x++) {
            for (int z = 0; z < controllerBE.depth; z++) {
                for (int y = 0; y < controllerBE.height; y++) {
                    BlockPos p = controllerBE.worldPosition.offset(x, y, z);
                    if (level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity tankBE) {
                        if (p.getY() == this.worldPosition.getY()) {
                            list.add(tankBE);
                        }
                    }
                }
            }
        }
        return list.isEmpty() ? List.of(this) : list;
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return new IFluidHandler() {
            private List<SteelFluidTankBlockEntity> getTankList() { return getSameLayerTanks(); }

            @Override public int getTanks() { return getTankList().size(); }

            @Override
            public FluidStack getFluidInTank(int tankIndex) {
                List<SteelFluidTankBlockEntity> tanks = getTankList();
                return (tankIndex >= 0 && tankIndex < tanks.size()) ? tanks.get(tankIndex).getTank().getFluidInTank(0) : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tankIndex) {
                List<SteelFluidTankBlockEntity> tanks = getTankList();
                return (tankIndex >= 0 && tankIndex < tanks.size()) ? tanks.get(tankIndex).getTank().getTankCapacity(0) : 0;
            }

            @Override
            public boolean isFluidValid(int tankIndex, FluidStack stack) {
                if (stack.isEmpty()) return false;
                Fluid fluid = stack.getFluid();
                switch (productIndex) {
                    case -1 -> { return fluid == ModFluids.CRUDE_OIL_SOURCE.get(); }
                    case 0 -> { return fluid == ModFluids.BITUMEN_SOURCE.get(); }
                    case 1 -> { return fluid == SulfurFluids.SULFUR_DIESEL_ENTRY.source.get(); }
                    case 2 -> { return fluid == SulfurFluids.SULFUR_KEROSENE_ENTRY.source.get(); }
                    case 3 -> { return fluid == SulfurFluids.SULFUR_GASOLINE_ENTRY.source.get(); }
                    case 4 -> { return fluid == SulfurFluids.SULFUR_NAPHTHA_ENTRY.source.get(); }
                    case 5 -> { return fluid == ModFluids.DIESEL_SOURCE.get(); }
                }
                return true;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return 0;
                int remaining = resource.getAmount();
                int filledTotal = 0;

                for (SteelFluidTankBlockEntity tankBE : getTankList()) {
                    FluidTank internal = tankBE.getTank();
                    if (!internal.isEmpty() && internal.getFluid().getFluid() == resource.getFluid()) {
                        int filled = internal.fill(resource.copyWithAmount(remaining), action);
                        filledTotal += filled;
                        remaining -= filled;
                        if (remaining <= 0) return filledTotal;
                    }
                }
                for (SteelFluidTankBlockEntity tankBE : getTankList()) {
                    FluidTank internal = tankBE.getTank();
                    if (internal.isEmpty()) {
                        int filled = internal.fill(resource.copyWithAmount(remaining), action);
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
                for (SteelFluidTankBlockEntity tankBE : getTankList()) {
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
                for (SteelFluidTankBlockEntity tankBE : getTankList()) {
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
        if (level == null) return;

        if (level.isClientSide) {
            tickClient();
            return;
        }

        if (updateConnectivity) {
            updateConnectivity = false;
            tryFormMultiblock();
        }
    }

    // ========== DENSE GAS PARTICLE RENDERER ==========

    private void tickClient() {
        SteelFluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null || !isController() || level == null) return;

        ResourceLocation gasId = controllerBE.getStoredGasId();
        int gasAmount = controllerBE.getStoredGasAmount();

        if (gasId == null || gasAmount <= 0) return;

        ParticleOptions particle = getGasParticleFromId(gasId);
        if (particle == null) return;

        float fillRatio = (float) gasAmount / controllerBE.getGasCapacity();
        if (fillRatio <= 0) return;

        BlockPos controllerPos = controllerBE.getBlockPos();

        // Render dense particles safely inside the inner glass bounds of the multiblock structure
        for (SteelFluidTankBlockEntity tankBE : getAllTanks()) {
            int particleCount = (int) (1 + fillRatio * 4);
            BlockPos bPos = tankBE.getBlockPos();

            boolean isTopLayer = (bPos.getY() == controllerPos.getY() + controllerBE.height - 1);
            boolean isBottomLayer = (bPos.getY() == controllerPos.getY());

            double minY = bPos.getY() + (isBottomLayer ? 0.25 : 0.08);
            double maxY = bPos.getY() + (isTopLayer ? 0.75 : 0.92);
            double currentGasMaxY = minY + (maxY - minY) * fillRatio;

            for (int i = 0; i < particleCount; i++) {
                if (level.random.nextFloat() < 0.6f * fillRatio) {
                    // Safe inset (0.18 -> 0.82) keeps particles well inside the glass frame
                    double x = bPos.getX() + 0.18 + level.random.nextDouble() * 0.64;
                    double y = minY + level.random.nextDouble() * Math.max(0.05, currentGasMaxY - minY);
                    double z = bPos.getZ() + 0.18 + level.random.nextDouble() * 0.64;

                    // Near-zero velocity stops particles from drifting through walls
                    double vx = (level.random.nextDouble() - 0.5) * 0.002;
                    double vy = (level.random.nextDouble() - 0.5) * 0.002;
                    double vz = (level.random.nextDouble() - 0.5) * 0.002;

                    level.addParticle(particle, x, y, z, vx, vy, vz);
                }
            }
        }
    }

    @Nullable
    private ParticleOptions getGasParticleFromId(ResourceLocation gasId) {
        String path = gasId.getPath().toLowerCase();

        if (path.contains("lpg")) return ModParticles.LPG_CLOUDS.get();
        if (path.contains("methane")) return ModParticles.METHANE_CLOUDS.get();
        if (path.contains("ethane")) return ModParticles.ETHANE_CLOUDS.get();
        if (path.contains("propane")) return ModParticles.PROPANE_CLOUDS.get();
        if (path.contains("butane")) return ModParticles.BUTANE_CLOUDS.get();
        if (path.contains("hydrogen")) return ModParticles.HYDROGEN_CLOUDS.get();

        return ModParticles.METHANE_CLOUDS.get();
    }

    // ========== NBT SERIALIZATION ==========

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

        // Save gas payload
        if (storedGasId != null) tag.putString("GasId", storedGasId.toString());
        tag.putInt("GasAmount", storedGasAmount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("fluid"));
        productIndex = tag.getInt("productIndex");
        if (tag.contains("Controller")) {
            controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
        } else {
            controller = null;
        }
        height = Math.max(1, tag.getInt("Height"));
        width = Math.max(1, tag.getInt("Width"));
        depth = Math.max(1, tag.getInt("Depth"));
        window = tag.getBoolean("Window");

        // Load gas payload
        if (tag.contains("GasId")) {
            storedGasId = ResourceLocation.parse(tag.getString("GasId"));
        } else {
            storedGasId = null;
        }
        storedGasAmount = tag.getInt("GasAmount");

        // FIX: DO NOT set updateConnectivity = true here!
        // Loading NBT packets on sync shouldn't break and reform multiblocks!
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