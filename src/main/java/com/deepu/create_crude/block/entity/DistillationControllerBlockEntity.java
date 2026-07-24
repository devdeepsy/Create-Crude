package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModFluids;
import com.deepu.create_crude.SulfurFluids;
import com.deepu.create_crude.block.DistillationControllerBlock;
import com.deepu.create_crude.client.gui.DistillationContainerMenu;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DistillationControllerBlockEntity extends BlockEntity implements MenuProvider {
    // Product order: 0=Bitumen, 1=Diesel, 2=Kerosene, 3=Gasoline, 4=Naphtha, 5=LPG
    private static final int PRODUCT_COUNT = 6;

    private int progress = 0;
    private int maxProgress = 200; // Default 10s

    // Structure data
    private boolean valid = false;
    private int towerHeight = 0;
    private int footprintArea = 1; // 1 (1x1), 4 (2x2), or 9 (3x3)
    private int[] productDistribution = new int[PRODUCT_COUNT];
    private int heatLevel = 0;
    
    // Completely separate lists so Base Layer never mixes with Output Layers!
    private final List<BlockPos> baseTankPositions = new ArrayList<>();
    private final List<BlockPos> productTankPositions = new ArrayList<>();

    public DistillationControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.DISTILLATION_CONTROLLER_BE.get(), pos, state);
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
        int crudeInBase = be.getCrudeOilInBaseTanks();

        if (be.progress > 0) {
            be.progress++;
            be.setActive(true);
            if (be.progress >= be.maxProgress) {
                be.drainCrudeOilFromBase(requiredInput);
                be.completeDistillation();
                be.progress = 0;
                be.setActive(false);
            }
        } else if (crudeInBase >= requiredInput && be.hasSpaceForOutputs()) {
            be.progress = 1;
            be.setActive(true);
        } else {
            be.setActive(false);
        }
    }

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
            return;
        }

        SteelFluidTankBlockEntity tankController = adjacentTank.getControllerBE();
        if (tankController == null) {
            valid = false;
            return;
        }

        int w = tankController.width;
        int d = tankController.depth;
        int h = tankController.height;

        if (w != d || w < 1 || w > 3) {
            valid = false;
            return;
        }

        // 1 Base Layer + 6 Product Layers = Minimum 7 height required!
        if (h < 7) {
            valid = false;
            return;
        }

        if (!checkBurnersUnderBase(tankController)) {
            valid = false;
            return;
        }

        BlockPos basePos = tankController.getBlockPos();
        if (this.worldPosition.getY() != basePos.getY()) {
            valid = false;
            return;
        }

        this.footprintArea = w * d;
        this.towerHeight = h;
        // Distribute the remaining (h - 1) layers among our 6 products!
        this.productDistribution = computeDistribution(h - 1);
        this.valid = true;

        switch (heatLevel) {
            case 2 -> maxProgress = 100;
            case 3 -> maxProgress = 50;
            default -> maxProgress = 200;
        }

        baseTankPositions.clear();
        productTankPositions.clear();

        // 1. LAYER 0 (Base Layer): Exclusively for boiling Crude Oil (-1 index)
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                BlockPos p = basePos.offset(x, 0, z);
                baseTankPositions.add(p);
                if (level.getBlockEntity(p) instanceof SteelFluidTankBlockEntity tBE) {
                    tBE.setProductIndex(-1); // -1 = Crude Oil Base Layer
                }
            }
        }

        // 2. LAYERS 1+ (Upper Layers): Exclusively for Product Outputs (0 to 5 index)
        int currentLayerY = 1; // Start strictly ABOVE the base layer!
        for (int product = 0; product < PRODUCT_COUNT; product++) {
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

    private int[] computeDistribution(int availableLayers) {
        int[] counts = new int[PRODUCT_COUNT];
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            counts[i] = 1;
        }

        int extraLayers = availableLayers - PRODUCT_COUNT;
        int productIndex = 0;

        while (extraLayers > 0) {
            counts[productIndex]++;
            productIndex = (productIndex + 1) % PRODUCT_COUNT;
            extraLayers--;
        }

        return counts;
    }

    // ---------- Crude Oil Reading from Base Tanks ----------
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

    // ---------- Distillation Logic ----------
    private boolean hasSpaceForOutputs() {
        int blockOffset = 0;
        for (int product = 0; product < PRODUCT_COUNT; product++) {
            int layers = productDistribution[product];
            if (layers == 0) continue;

            int totalProductAmount = getProductAmount(product) * footprintArea;
            int blocksForProduct = layers * footprintArea;
            int perBlockAmount = totalProductAmount / blocksForProduct;

            FluidStack outputFluid = new FluidStack(getFluidForIndex(product), perBlockAmount);
            for (int i = 0; i < blocksForProduct; i++) {
                BlockPos pos = productTankPositions.get(blockOffset + i);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SteelFluidTankBlockEntity tankBE) {
                    // Check if the dedicated product layer block has room!
                    if (tankBE.fillTank(outputFluid, IFluidHandler.FluidAction.SIMULATE) < perBlockAmount) {
                        return false;
                    }
                }
            }
            blockOffset += blocksForProduct;
        }
        return true;
    }

    private void completeDistillation() {
        int blockOffset = 0;
        for (int product = 0; product < PRODUCT_COUNT; product++) {
            int layers = productDistribution[product];
            if (layers == 0) continue;

            int totalProductAmount = getProductAmount(product) * footprintArea;
            int blocksForProduct = layers * footprintArea;
            int perBlockAmount = totalProductAmount / blocksForProduct;

            FluidStack fluid = new FluidStack(getFluidForIndex(product), perBlockAmount);
            for (int i = 0; i < blocksForProduct; i++) {
                BlockPos pos = productTankPositions.get(blockOffset + i);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SteelFluidTankBlockEntity tankBE) {
                    tankBE.fillTank(fluid, IFluidHandler.FluidAction.EXECUTE);
                }
            }
            blockOffset += blocksForProduct;
        }
        setChanged();
    }

    private int getProductAmount(int index) {
        return switch (index) {
            case 0 -> 300; // Bitumen
            case 1 -> 200; // Diesel
            case 2 -> 250; // Kerosene
            case 3 -> 150; // Gasoline
            case 4 -> 100; // Naphtha
            case 5 -> 50;  // LPG
            default -> 0;
        };
    }

    private Fluid getFluidForIndex(int index) {
        return switch (index) {
            case 0 -> ModFluids.BITUMEN_SOURCE.get();
            case 1 -> SulfurFluids.SULFUR_DIESEL_ENTRY.source.get();
            case 2 -> SulfurFluids.SULFUR_KEROSENE_ENTRY.source.get();
            case 3 -> SulfurFluids.SULFUR_GASOLINE_ENTRY.source.get();
            case 4 -> SulfurFluids.SULFUR_NAPHTHA_ENTRY.source.get();
            case 5 -> ModFluids.DIESEL_SOURCE.get();
            default -> ModFluids.BITUMEN_SOURCE.get();
        };
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
                        IFluidHandler handler = tankBE.getFluidHandler(null);
                        total += handler.getFluidInTank(0).getAmount();
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
        player.sendSystemMessage(Component.literal("§a✔ Found adjacent Steel Fluid Tank structure."));

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
        player.sendSystemMessage(Component.literal("§a✔ Footprint dimensions valid."));

        player.sendSystemMessage(Component.literal("§eℹ Tower height = " + h + " blocks (min 7)."));
        if (h < 7) {
            player.sendSystemMessage(Component.literal("§c❌ Tower too short! Minimum height required is 7 (1 base + 6 products)."));
            return;
        }
        player.sendSystemMessage(Component.literal("§a✔ Tower height valid."));

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