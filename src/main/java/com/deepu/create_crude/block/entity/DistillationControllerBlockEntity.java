package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModFluids;
import com.deepu.create_crude.SulfurFluids;
import com.deepu.create_crude.block.DistillationControllerBlock;
import com.deepu.create_crude.block.SteelFluidTankBlock;
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
    private static final int INPUT_CAPACITY = 32000;

    private int progress = 0;
    private int maxProgress = 200; // 10 seconds

    private final FluidTank inputTank = new FluidTank(INPUT_CAPACITY) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == ModFluids.CRUDE_OIL_SOURCE.get();
        }
    };

    // Structure data
    private boolean valid = false;
    private int towerHeight = 0;
    private int[] productDistribution = new int[PRODUCT_COUNT];
    private int heatLevel = 0;
    private List<BlockPos> tankPositions = new ArrayList<>();

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

        if (be.progress > 0) {
            be.progress++;
            be.setActive(true);
            if (be.progress >= be.maxProgress) {
                be.inputTank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
                be.completeDistillation();
                be.progress = 0;
                be.setActive(false);
            }
        } else if (be.inputTank.getFluidAmount() >= 1000 && be.hasSpaceForOutputs()) {
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
        BlockPos tankPos = null;

        for (Direction dir : horizontal) {
            BlockPos adj = worldPosition.relative(dir);
            if (level.getBlockState(adj).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
                tankPos = adj;
                break;
            }
        }
        if (tankPos == null) {
            valid = false;
            return;
        }

        BlockPos.MutableBlockPos bottomPos = tankPos.mutable();
        while (level.getBlockState(bottomPos.below()).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
            bottomPos.move(Direction.DOWN);
        }

        BlockPos.MutableBlockPos scanPos = bottomPos.mutable();
        tankPositions.clear();
        while (level.getBlockState(scanPos).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
            tankPositions.add(scanPos.immutable());
            scanPos.move(Direction.UP);
        }

        int productLayers = tankPositions.size();
        if (productLayers < PRODUCT_COUNT) {
            valid = false;
            return;
        }

        BlockPos burnerPos = bottomPos.below();
        heatLevel = getHeatLevel(burnerPos);
        if (heatLevel == 0) {
            valid = false;
            return;
        }

        towerHeight = productLayers + 1;
        productDistribution = computeDistribution(productLayers);
        valid = true;

        int offset = 0;
        for (int product = 0; product < PRODUCT_COUNT; product++) {
            for (int i = 0; i < productDistribution[product]; i++) {
                BlockPos pos = tankPositions.get(offset + i);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SteelFluidTankBlockEntity tankBE) {
                    tankBE.setProductIndex(product);
                }
            }
            offset += productDistribution[product];
        }
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

    private int[] computeDistribution(int productLayers) {
        int[] counts = new int[PRODUCT_COUNT];
        for (int i = 0; i < PRODUCT_COUNT; i++) counts[i] = 1;
        int extra = productLayers - PRODUCT_COUNT;
        boolean bottom = true;
        while (extra > 0) {
            if (bottom) {
                counts[0]++;
                bottom = false;
            } else {
                counts[PRODUCT_COUNT - 1]++;
                bottom = true;
            }
            extra--;
        }
        return counts;
    }

    // ---------- Distillation Logic ----------
    private boolean hasSpaceForOutputs() {
        int offset = 0;
        for (int product = 0; product < PRODUCT_COUNT; product++) {
            int layers = productDistribution[product];
            if (layers == 0) continue;
            int totalAmount = getProductAmount(product);
            int perLayer = totalAmount / layers;
            FluidStack fluid = new FluidStack(getFluidForIndex(product), perLayer);
            for (int i = 0; i < layers; i++) {
                BlockPos pos = tankPositions.get(offset + i);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SteelFluidTankBlockEntity tankBE) {
                    if (tankBE.fillTank(fluid, IFluidHandler.FluidAction.SIMULATE) < perLayer) {
                        return false;
                    }
                }
            }
            offset += layers;
        }
        return true;
    }

    private void completeDistillation() {
        int offset = 0;
        for (int product = 0; product < PRODUCT_COUNT; product++) {
            int layers = productDistribution[product];
            if (layers == 0) continue;
            int totalAmount = getProductAmount(product);
            int perLayer = totalAmount / layers;
            FluidStack fluid = new FluidStack(getFluidForIndex(product), perLayer);
            for (int i = 0; i < layers; i++) {
                BlockPos pos = tankPositions.get(offset + i);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SteelFluidTankBlockEntity tankBE) {
                    tankBE.fillTank(fluid, IFluidHandler.FluidAction.EXECUTE);
                }
            }
            offset += layers;
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
            case 5 -> ModFluids.DIESEL_SOURCE.get(); // placeholder
            default -> ModFluids.BITUMEN_SOURCE.get();
        };
    }

    // ---------- Public Access for GUI ----------
    public Fluid getProductFluid(int index) {
        return getFluidForIndex(index);
    }

    public int getProductTotalAmount(int product) {
        if (!valid || product < 0 || product >= PRODUCT_COUNT) return 0;
        int total = 0;
        int offset = 0;
        for (int p = 0; p < PRODUCT_COUNT; p++) {
            int layers = productDistribution[p];
            if (p == product) {
                for (int i = 0; i < layers; i++) {
                    BlockPos pos = tankPositions.get(offset + i);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof SteelFluidTankBlockEntity tankBE) {
                        IFluidHandler handler = tankBE.getFluidHandler(null);
                        total += handler.getFluidInTank(0).getAmount();
                    }
                }
                break;
            }
            offset += layers;
        }
        return total;
    }

    public int getProductTotalCapacity(int product) {
        if (!valid || product < 0 || product >= PRODUCT_COUNT) return 0;
        return productDistribution[product] * SteelFluidTankBlockEntity.CAPACITY;
    }

    public Fluid getInputFluid() {
        return ModFluids.CRUDE_OIL_SOURCE.get();
    }

    // ---------- Capability Exposure ----------
    public IFluidHandler getInputCapability(@Nullable Direction side) {
        if (side == Direction.DOWN) {
            return inputTank;
        }
        return null;
    }

    // ---------- Misc ----------
    private void setActive(boolean active) {
        BlockState state = getBlockState();
        if (state.getValue(DistillationControllerBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(DistillationControllerBlock.ACTIVE, active), 3);
        }
    }

    public int getHeatLevel() { return heatLevel; }
    public FluidTank getInputTank() { return inputTank; }

    // ---------- Diagnostics ----------
    public void printDiagnostics(Player player) {
        if (level == null || level.isClientSide) return;
        player.sendSystemMessage(Component.literal("§6--- ⚙ Distillation Tower Diagnostics ⚙ ---"));

        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        BlockPos tankPos = null;
        for (Direction dir : horizontal) {
            BlockPos adj = worldPosition.relative(dir);
            if (level.getBlockState(adj).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
                tankPos = adj;
                break;
            }
        }
        if (tankPos == null) {
            player.sendSystemMessage(Component.literal("§c❌ Structural Error: No Steel Fluid Tank adjacent to controller."));
            return;
        }
        player.sendSystemMessage(Component.literal("§a✔ Found adjacent Steel Fluid Tank."));

        BlockPos.MutableBlockPos bottomPos = tankPos.mutable();
        while (level.getBlockState(bottomPos.below()).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
            bottomPos.move(Direction.DOWN);
        }

        BlockPos.MutableBlockPos scanPos = bottomPos.mutable();
        int productLayers = 0;
        while (level.getBlockState(scanPos).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
            productLayers++;
            scanPos.move(Direction.UP);
        }
        player.sendSystemMessage(Component.literal("§eℹ Column height = " + productLayers + " blocks (min 6)."));
        if (productLayers < 6) {
            player.sendSystemMessage(Component.literal("§c❌ Column too short!"));
            return;
        }
        player.sendSystemMessage(Component.literal("§a✔ Column height valid."));

        BlockPos burnerPos = bottomPos.below();
        int currentHeat = getHeatLevel(burnerPos);
        player.sendSystemMessage(Component.literal("§eℹ Heat level = " + currentHeat));
        if (currentHeat == 0) {
            player.sendSystemMessage(Component.literal("§c❌ Missing or unlit Blaze Burner below bottom tank."));
            return;
        }
        player.sendSystemMessage(Component.literal("§a✔ Heat source active."));

        int fluidAmount = inputTank.getFluidAmount();
        player.sendSystemMessage(Component.literal("§eℹ Crude Oil in input = " + fluidAmount + "mB / 1000mB"));
        if (fluidAmount < 1000) {
            player.sendSystemMessage(Component.literal("§c⚠ Not enough crude oil (pump through bottom face)."));
        }

        for (BlockPos pos : tankPositions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SteelFluidTankBlockEntity tankBE) {
                IFluidHandler handler = tankBE.getFluidHandler(null);
                FluidStack fs = handler.getFluidInTank(0);
                int prodIdx = tankBE.getProductIndex();
                String prodName = switch (prodIdx) {
                    case 0 -> "Bitumen";
                    case 1 -> "Diesel";
                    case 2 -> "Kerosene";
                    case 3 -> "Gasoline";
                    case 4 -> "Naphtha";
                    case 5 -> "LPG";
                    default -> "Unknown";
                };
                player.sendSystemMessage(Component.literal("§7  - " + prodName + " tank @ " +
                        pos.getX() + "," + pos.getY() + "," + pos.getZ() + " : " + fs.getAmount() + "mB"));
            }
        }

        if (fluidAmount >= 1000 && hasSpaceForOutputs()) {
            player.sendSystemMessage(Component.literal("§a⚙ Status: Running perfectly!"));
        } else {
            player.sendSystemMessage(Component.literal("§e⚙ Status: STANDBY (waiting on fluids or space)"));
        }
    }

    // ---------- NBT ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("input", inputTank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("valid", valid);
        tag.putInt("towerHeight", towerHeight);
        tag.putIntArray("distribution", productDistribution);
        tag.putInt("heatLevel", heatLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputTank.readFromNBT(registries, tag.getCompound("input"));
        valid = tag.getBoolean("valid");
        towerHeight = tag.getInt("towerHeight");
        productDistribution = tag.getIntArray("distribution");
        if (productDistribution.length != PRODUCT_COUNT) productDistribution = new int[PRODUCT_COUNT];
        heatLevel = tag.getInt("heatLevel");
    }

    // ---------- Menu / Data Access ----------
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

    // ---------- MenuProvider ----------
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