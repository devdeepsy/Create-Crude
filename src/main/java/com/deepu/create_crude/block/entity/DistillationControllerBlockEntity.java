package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModFluids;
import com.deepu.create_crude.SulfurFluids;
import com.deepu.create_crude.block.DistillationControllerBlock;
import com.deepu.create_crude.client.gui.DistillationContainerMenu;
import com.deepu.create_crude.gases.GasBlock;
import com.deepu.create_crude.gases.GasRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public class DistillationControllerBlockEntity extends BlockEntity implements MenuProvider {
    // Product order: 0=Heavy Oil/Bitumen, 1=Sulfur Diesel, 2=Sulfur Kerosene, 3=Sulfur Gasoline, 4=Sulfur Naphtha, 5=LPG
    private static final int PRODUCT_COUNT = 6;

    private int progress = 0;
    private int maxProgress = 200; // Default 10s

    // Structure data
    private boolean valid = false;
    private int towerHeight = 0;
    private int footprintArea = 1; // 1 (1x1), 4 (2x2), or 9 (3x3)
    private int[] productDistribution = new int[PRODUCT_COUNT];
    private int heatLevel = 0;
    
    // Base Layer separate from Output Layers
    private final List<BlockPos> baseTankPositions = new ArrayList<>();
    private final List<BlockPos> productTankPositions = new ArrayList<>();

    public enum Mode {
        NONE(0, 0),
        CRUDE_OIL(7, 6), // 1 Base + 6 Output Layers
        HEAVY_OIL(4, 3);  // 1 Base + 3 Output Layers

        public final int minHeight;
        public final int productCount;

        Mode(int minHeight, int productCount) {
            this.minHeight = minHeight;
            this.productCount = productCount;
        }
    }
    private Mode currentMode = Mode.NONE;

    public DistillationControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.DISTILLATION_CONTROLLER_BE.get(), pos, state);
    }
    private boolean isGasProduct(int index) {
        return index == 5; // Layer 6: LPG
    }

    // ---------- Server Tick ----------
    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillationControllerBlockEntity be) {
        be.validateStructure();
        if (!be.valid) {
            be.progress = 0;
            be.setActive(false);
            return;
        }

        int requiredInput = 1000 * be.footprintArea;
        int fluidInBase = be.getBaseFluidAmount();

        if (be.progress > 0) {
            be.progress++;
            be.setActive(true);
            if (be.progress >= be.maxProgress) {
                be.drainBaseFluid(requiredInput);
                be.completeDistillation();
                be.progress = 0;
                be.setActive(false);
            }
        } else if (fluidInBase >= requiredInput && be.hasSpaceForOutputs()) {
            be.progress = 1;
            be.setActive(true);
        } else {
            be.setActive(false);
        }
    }
    public enum DistillationType {
        CRUDE_OIL,
        HEAVY_OIL,
        NONE
    }

    private DistillationType distillationType = DistillationType.NONE;

    // ---------- Structure Validation ----------
    private void validateStructure() {
        if (level == null) return;

        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        SteelFluidTankBlockEntity adjacentTank = null;

        for (Direction dir : horizontal) {
            BlockPos adj = worldPosition.relative(dir);
            if (level.getBlockEntity(adj) instanceof SteelFluidTankBlockEntity tankBE) {
                adjacentTank = tankBE;
                break;
            }
        }

        if (adjacentTank == null) {
            valid = false;
            currentMode = Mode.NONE;
            return;
        }

        SteelFluidTankBlockEntity tankController = adjacentTank.getControllerBE();
        if (tankController == null) {
            valid = false;
            currentMode = Mode.NONE;
            return;
        }

        int w = tankController.width;
        int d = tankController.depth;
        int h = tankController.height;

        if (w != d || w < 1 || w > 3) {
            valid = false;
            currentMode = Mode.NONE;
            return;
        }

        if (!checkBurnersUnderBase(tankController)) {
            valid = false;
            currentMode = Mode.NONE;
            return;
        }

        BlockPos basePos = tankController.getBlockPos();
        if (this.worldPosition.getY() != basePos.getY()) {
            valid = false;
            currentMode = Mode.NONE;
            return;
        }

        // 1. Detect Base Fluid
        Fluid baseFluid = getBaseTankFluid(basePos);
        if (baseFluid == ModFluids.CRUDE_OIL_SOURCE.get()) {
            this.currentMode = Mode.CRUDE_OIL;
        } else if (baseFluid == ModFluids.HEAVY_OIL_SOURCE.get()) {
            this.currentMode = Mode.HEAVY_OIL;
        } else {
            valid = false;
            currentMode = Mode.NONE;
            return;
        }

        // 2. Check Height Requirement based on detected Mode
        if (h < currentMode.minHeight) {
            valid = false;
            return;
        }

        this.footprintArea = w * d;
        this.towerHeight = h;
        this.productDistribution = computeDistribution(h - 1, currentMode.productCount);
        this.valid = true;

        switch (heatLevel) {
            case 2 -> maxProgress = 100;
            case 3 -> maxProgress = 50;
            default -> maxProgress = 200;
        }

        baseTankPositions.clear();
        productTankPositions.clear();

        // Set Base Layer (Layer 0)
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                BlockPos p = basePos.offset(x, 0, z);
                baseTankPositions.add(p);
                if (level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity tBE) {
                    tBE.setProductIndex(-1);
                }
            }
        }

        // Set Output Layers
        int currentLayerY = 1;
        for (int product = 0; product < currentMode.productCount; product++) {
            int layerCount = productDistribution[product];
            for (int l = 0; l < layerCount; l++) {
                int yOffset = currentLayerY + l;
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        BlockPos p = basePos.offset(x, yOffset, z);
                        productTankPositions.add(p);
                        if (level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity tBE) {
                            tBE.setProductIndex(product);
                        }
                    }
                }
            }
            currentLayerY += layerCount;
        }
    }

    private Fluid getBaseTankFluid(BlockPos basePos) {
        if (level.getBlockEntity(basePos) instanceof SteelFluidTankBlockEntity tankBE) {
            return tankBE.getTank().getFluid().getFluid();
        }
        return Fluids.EMPTY;
    }
    public int getBaseFluidAmount() {
        if (level == null || baseTankPositions.isEmpty()) return 0;
        int total = 0;
        Fluid targetFluid = (currentMode == Mode.HEAVY_OIL) ? ModFluids.HEAVY_OIL_SOURCE.get() : ModFluids.CRUDE_OIL_SOURCE.get();

        for (BlockPos pos : baseTankPositions) {
            if (level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
                FluidStack stack = tankBE.getTank().getFluid();
                if (stack.getFluid() == targetFluid) {
                    total += stack.getAmount();
                }
            }
        }
        return total;
    }
    private void drainBaseFluid(int amountToDrain) {
        if (level == null || baseTankPositions.isEmpty()) return;
        int remainingToDrain = amountToDrain;
        Fluid targetFluid = (currentMode == Mode.HEAVY_OIL) ? ModFluids.HEAVY_OIL_SOURCE.get() : ModFluids.CRUDE_OIL_SOURCE.get();

        for (BlockPos pos : baseTankPositions) {
            if (level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
                FluidStack stack = tankBE.getTank().getFluid();
                if (stack.getFluid() == targetFluid) {
                    FluidStack drained = tankBE.getTank().drain(new FluidStack(targetFluid, remainingToDrain), IFluidHandler.FluidAction.EXECUTE);
                    remainingToDrain -= drained.getAmount();
                    if (remainingToDrain <= 0) break;
                }
            }
        }
    }

    private boolean checkBurnersUnderBase(SteelFluidTankBlockEntity tankController) {
        BlockPos basePos = tankController.getBlockPos();
        int w = tankController.width;
        int d = tankController.depth;
        int minHeat = 3;

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                BlockPos burnerPos = basePos.offset(x, -1, z);
                int heat = getHeatLevel(burnerPos);
                if (heat == 0) {
                    this.heatLevel = 0;
                    return false;
                }
                if (heat < minHeat) {
                    minHeat = heat;
                }
            }
        }
        this.heatLevel = minHeat;
        return true;
    }

    private int getHeatLevel(BlockPos pos) {
        if (level == null) return 0;
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        if (blockKey.getNamespace().equals("create") && blockKey.getPath().equals("blaze_burner")) {
            String stateStr = state.toString().toLowerCase();
            if (stateStr.contains("seething") || stateStr.contains("superheated")) {
                return 3;
            } else if (stateStr.contains("kindled") || stateStr.contains("heated")) {
                return 2;
            }
            return 1;
        }
        return 0;
    }
    private ResourceLocation getGasIdForIndex(int index) {
        return switch (index) {
            case 5 -> ResourceLocation.fromNamespaceAndPath(CreateCrude.MODID, "lpg_block");
            default -> null;
        };
    }

    private int[] computeDistribution(int availableLayers, int productCount) {
        int[] counts = new int[productCount];
        for (int i = 0; i < productCount; i++) {
            counts[i] = 1;
        }

        int extraLayers = availableLayers - productCount;
        int productIndex = 0;

        while (extraLayers > 0) {
            counts[productIndex]++;
            productIndex = (productIndex + 1) % productCount;
            extraLayers--;
        }

        return counts;
    }

    // ---------- Crude Oil Reading ----------
    public int getCrudeOilInBaseTanks() {
        if (level == null || baseTankPositions.isEmpty()) return 0;
        int totalCrude = 0;

        for (BlockPos pos : baseTankPositions) {
            if (level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
                FluidStack stack = tankBE.getTank().getFluid();
                if (stack.getFluid() == ModFluids.CRUDE_OIL_SOURCE.get()) {
                    totalCrude += stack.getAmount();
                }
            }
        }
        return totalCrude;
    }

    public int getCrudeOilCapacityInBaseTanks() {
        if (!valid) return 0;
        return baseTankPositions.size() * SteelFluidTankBlockEntity.CAPACITY;
    }

    private void drainCrudeOilFromBase(int amountToDrain) {
        if (level == null || baseTankPositions.isEmpty()) return;
        int remainingToDrain = amountToDrain;

        for (BlockPos pos : baseTankPositions) {
            if (level.getBlockEntity(pos) instanceof SteelFluidTankBlockEntity tankBE) {
                FluidStack stack = tankBE.getTank().getFluid();
                if (stack.getFluid() == ModFluids.CRUDE_OIL_SOURCE.get()) {
                    FluidStack drained = tankBE.getTank().drain(new FluidStack(ModFluids.CRUDE_OIL_SOURCE.get(), remainingToDrain), IFluidHandler.FluidAction.EXECUTE);
                    remainingToDrain -= drained.getAmount();
                    if (remainingToDrain <= 0) break;
                }
            }
        }
    }

    // ---------- Systematic Distillation Logic ----------
    private boolean hasSpaceForOutputs() {
        if (currentMode == Mode.NONE) return false;
        int blockOffset = 0;
        
        for (int product = 0; product < currentMode.productCount; product++) {
            int layers = productDistribution[product];
            if (layers == 0) continue;

            int totalProductAmount = getProductAmount(product) * footprintArea;
            int blocksForProduct = layers * footprintArea;
            int perBlockAmount = totalProductAmount / blocksForProduct;

            for (int i = 0; i < blocksForProduct; i++) {
                BlockPos pos = productTankPositions.get(blockOffset + i);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SteelFluidTankBlockEntity tankBE) {
                    if (currentMode == Mode.CRUDE_OIL && isGasProduct(product)) {
                        ResourceLocation gasId = getGasIdForIndex(product);
                        if (tankBE.fillGas(gasId, perBlockAmount, true) < perBlockAmount) return false;
                    } else {
                        FluidStack outputFluid = new FluidStack(getFluidForIndex(product), perBlockAmount);
                        if (tankBE.getFluidHandler(null).fill(outputFluid, IFluidHandler.FluidAction.SIMULATE) < perBlockAmount) return false;
                    }
                }
            }
            blockOffset += blocksForProduct;
        }
        return true;
    }

    private void completeDistillation() {
        if (currentMode == Mode.NONE) return;
        int blockOffset = 0;

        for (int product = 0; product < currentMode.productCount; product++) {
            int layers = productDistribution[product];
            if (layers == 0) continue;

            int totalProductAmount = getProductAmount(product) * footprintArea;
            int blocksForProduct = layers * footprintArea;
            int perBlockAmount = totalProductAmount / blocksForProduct;

            for (int i = 0; i < blocksForProduct; i++) {
                BlockPos pos = productTankPositions.get(blockOffset + i);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SteelFluidTankBlockEntity tankBE) {
                    if (currentMode == Mode.CRUDE_OIL && isGasProduct(product)) {
                        ResourceLocation gasId = getGasIdForIndex(product);
                        tankBE.fillGas(gasId, perBlockAmount, false);
                    } else {
                        FluidStack fluid = new FluidStack(getFluidForIndex(product), perBlockAmount);
                        tankBE.getFluidHandler(null).fill(fluid, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
            blockOffset += blocksForProduct;
        }
        setChanged();
    }

    // SYSTEMATIC YIELD Amounts (Sum = 1000mB per cycle)
    private int getProductAmount(int index) {
        if (currentMode == Mode.CRUDE_OIL) {
            return switch (index) {
                case 0 -> 300; // Layer 1: Heavy Oil
                case 1 -> 250; // Layer 2: Sulfur Diesel
                case 2 -> 200; // Layer 3: Sulfur Kerosene
                case 3 -> 120; // Layer 4: Sulfur Gasoline
                case 4 -> 80;  // Layer 5: Sulfur Naphtha
                case 5 -> 50;  // Layer 6: LPG
                default -> 0;
            };
        } else if (currentMode == Mode.HEAVY_OIL) {
            return switch (index) {
                case 0 -> 500; // Layer 1: Bitumen
                case 1 -> 300; // Layer 2: Lubricating Oil
                case 2 -> 200; // Layer 3: Fuel Oil / Heavy Diesel
                default -> 0;
            };
        }
        return 0;
    }

    // SYSTEMATIC FLUID Mapping
    private Fluid getFluidForIndex(int index) {
        if (currentMode == Mode.CRUDE_OIL) {
            return switch (index) {
                case 0 -> ModFluids.HEAVY_OIL_SOURCE.get();
                case 1 -> SulfurFluids.SULFUR_DIESEL_ENTRY.source.get();
                case 2 -> SulfurFluids.SULFUR_KEROSENE_ENTRY.source.get();
                case 3 -> SulfurFluids.SULFUR_GASOLINE_ENTRY.source.get();
                case 4 -> SulfurFluids.SULFUR_NAPHTHA_ENTRY.source.get();
                case 5 -> Fluids.EMPTY; // LPG Gas block
                default -> Fluids.EMPTY;
            };
        } else if (currentMode == Mode.HEAVY_OIL) {
            return switch (index) {
                case 0 -> ModFluids.BITUMEN_SOURCE.get();
                case 1 -> ModFluids.LUBRICATING_OIL_SOURCE.get();
                case 2 -> SulfurFluids.SULFUR_DIESEL_ENTRY.source.get();
                default -> Fluids.EMPTY;
            };
        }
        return Fluids.EMPTY;
    }

    // ---------- Public Accessors ----------
    public Fluid getProductFluid(int index) {
        return getFluidForIndex(index);
    }

    public int getProductTotalAmount(int product) {
        if (!valid || product < 0 || product >= PRODUCT_COUNT || productTankPositions.isEmpty()) return 0;
        int total = 0;
        int blockOffset = 0;
        for (int p = 0; p < PRODUCT_COUNT; p++) {
            int blocks = productDistribution[p] * footprintArea;
            if (p == product) {
                for (int i = 0; i < blocks; i++) {
                    BlockPos pos = productTankPositions.get(blockOffset + i);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof SteelFluidTankBlockEntity tankBE) {
                        // Check custom gas system if layer 5 (LPG), otherwise standard fluid handler
                        if (isGasProduct(product)) {
                            total += tankBE.getStoredGasAmount();
                        } else {
                            IFluidHandler handler = tankBE.getFluidHandler(null);
                            if (handler != null) {
                                total += handler.getFluidInTank(0).getAmount();
                            }
                        }
                    }
                }
                break;
            }
            blockOffset += blocks;
        }
        return total;
    }

    public int getProductTotalCapacity(int product) {
        if (!valid || product < 0 || product >= PRODUCT_COUNT) return 0;
        return productDistribution[product] * footprintArea * SteelFluidTankBlockEntity.CAPACITY;
    }

    public Fluid getInputFluid() {
        return ModFluids.CRUDE_OIL_SOURCE.get();
    }

    private void setActive(boolean active) {
        BlockState state = getBlockState();
        if (state.getValue(DistillationControllerBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(DistillationControllerBlock.ACTIVE, active), 3);
        }
    }
    

    public int getHeatLevel() { return heatLevel; }

    // ---------- Diagnostics ----------
    public void printDiagnostics(Player player) {
        if (level == null || level.isClientSide) return;
        player.sendSystemMessage(Component.literal("§6--- ⚙ Distillation Tower Diagnostics ⚙ ---"));

        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        SteelFluidTankBlockEntity adjacentTank = null;
        for (Direction dir : horizontal) {
            BlockPos adj = worldPosition.relative(dir);
            if (level.getBlockEntity(adj) instanceof SteelFluidTankBlockEntity tankBE) {
                adjacentTank = tankBE;
                break;
            }
        }
        if (adjacentTank == null) {
            player.sendSystemMessage(Component.literal("§c❌ Structural Error: No Steel Fluid Tank adjacent to controller."));
            return;
        }

        SteelFluidTankBlockEntity tankController = adjacentTank.getControllerBE();
        if (tankController == null) {
            player.sendSystemMessage(Component.literal("§c❌ Structural Error: Could not determine multiblock controller."));
            return;
        }

        int w = tankController.width;
        int d = tankController.depth;
        int h = tankController.height;

        player.sendSystemMessage(Component.literal("§eℹ Footprint dimensions: " + w + "x" + d + "."));
        if (w != d || w < 1 || w > 3) {
            player.sendSystemMessage(Component.literal("§c❌ Footprint shape invalid! Must be 1x1, 2x2, or 3x3."));
            return;
        }

        player.sendSystemMessage(Component.literal("§eℹ Tower height = " + h + " blocks (min 7)."));
        if (h < 7) {
            player.sendSystemMessage(Component.literal("§c❌ Tower too short! Minimum height required is 7 (1 base + 6 products)."));
            return;
        }

        boolean burnersValid = checkBurnersUnderBase(tankController);
        player.sendSystemMessage(Component.literal("§eℹ Heat Level: " + heatLevel));
        if (!burnersValid) {
            player.sendSystemMessage(Component.literal("§c❌ Base heat error: Missing or unlit Blaze Burners under base footprint."));
            return;
        }

        int requiredInput = 1000 * footprintArea;
        int crudeAmount = getCrudeOilInBaseTanks();
        player.sendSystemMessage(Component.literal("§eℹ Crude Oil in base layer (Layer 0) = " + crudeAmount + "mB / " + requiredInput + "mB needed."));

        if (crudeAmount < requiredInput) {
            player.sendSystemMessage(Component.literal("§c⚠ Insufficient crude oil in bottom tanks."));
        }

        if (hasSpaceForOutputs()) {
            player.sendSystemMessage(Component.literal("§a✔ Output space available in Layers 1 to " + (h-1) + "."));
        } else {
            player.sendSystemMessage(Component.literal("§c❌ Output space blocked or contaminated! Check pipes on upper layers."));
        }

        if (crudeAmount >= requiredInput && hasSpaceForOutputs()) {
            player.sendSystemMessage(Component.literal("§a⚙ Status: Running (" + (maxProgress / 20.0) + "s cycle time)!"));
        } else {
            player.sendSystemMessage(Component.literal("§e⚙ Status: STANDBY (Waiting on fluids or output space)"));
        }
    }

    // ---------- NBT ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("valid", valid);
        tag.putInt("towerHeight", towerHeight);
        tag.putInt("footprintArea", footprintArea);
        tag.putIntArray("distribution", productDistribution);
        tag.putInt("heatLevel", heatLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        valid = tag.getBoolean("valid");
        towerHeight = tag.getInt("towerHeight");
        footprintArea = tag.getInt("footprintArea");
        if (footprintArea < 1) footprintArea = 1;
        productDistribution = tag.getIntArray("distribution");
        if (productDistribution.length != PRODUCT_COUNT) productDistribution = new int[PRODUCT_COUNT];
        heatLevel = tag.getInt("heatLevel");
    }

    // ---------- Menu / GUI Data ----------
    protected final SimpleContainerData dataAccess = new SimpleContainerData(4) {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> DistillationControllerBlockEntity.this.progress;
                case 1 -> DistillationControllerBlockEntity.this.maxProgress;
                case 2 -> DistillationControllerBlockEntity.this.valid ? 1 : 0;
                case 3 -> DistillationControllerBlockEntity.this.heatLevel;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> DistillationControllerBlockEntity.this.progress = value;
                case 1 -> DistillationControllerBlockEntity.this.maxProgress = value;
                case 2 -> DistillationControllerBlockEntity.this.valid = (value == 1);
                case 3 -> DistillationControllerBlockEntity.this.heatLevel = value;
            }
        }
    };

    public SimpleContainerData getDataAccess() {
        return dataAccess;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.createcrude.distillation_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DistillationContainerMenu(id, inv, worldPosition);
    }
}