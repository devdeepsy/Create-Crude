package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModFluids;
import com.deepu.create_crude.SulfurFluids;
import com.deepu.create_crude.block.DistillationCasingBlock;
import com.deepu.create_crude.block.DistillationControllerBlock;
import com.deepu.create_crude.block.SteelFluidTankBlock;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import com.deepu.create_crude.client.gui.DistillationContainerMenu;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DistillationControllerBlockEntity extends BlockEntity implements MenuProvider {
    // Product order: 0=Bitumen, 1=Diesel, 2=Kerosene, 3=Gasoline, 4=Naphtha, 5=LPG
    private static final int PRODUCT_COUNT = 6;
    private static final int INPUT_CAPACITY = 32000;
    private static final int OUTPUT_CAPACITY = 16000;
    private int progress = 0;
    private int maxProgress = 0;

    private final FluidTank inputTank = new FluidTank(INPUT_CAPACITY) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == ModFluids.CRUDE_OIL_SOURCE.get();
        }
    };

    private final FluidTank[] outputTanks = new FluidTank[PRODUCT_COUNT];
    {
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            outputTanks[i] = new FluidTank(OUTPUT_CAPACITY);
        }
    }

    // Structure data
    private boolean valid = false;
    private int towerHeight = 0;               // total height including controller
    private int[] productDistribution = new int[PRODUCT_COUNT]; // number of casing blocks per product
    private int heatLevel = 0;                 // 0=none, 1=one burner, 2=two burners
    private BlockPos controllerPos;            // cached
    private List<BlockPos> casingPositions = new ArrayList<>();

    public DistillationControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.DISTILLATION_CONTROLLER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillationControllerBlockEntity be) {
        // 1. Always ensure the structure is valid first
        be.validateStructure();
        if (!be.valid) {
            be.progress = 0;
            be.setActive(false);
            return;
        }

        int maxProcessTime = 200; // 200 ticks = 10 seconds of distillation time

        // 2. If we are already processing, keep going!
        if (be.progress > 0) {
            be.progress++;
            be.setActive(true);

            // Has enough time passed?
            if (be.progress >= maxProcessTime) {
                // FINISH THE PROCESS
                be.inputTank.drain(1000, FluidAction.EXECUTE); // Consume the fluid NOW
                be.distributeOutputs();                       // Add the products to output tanks
                be.progress = 0;                              // Reset timer for next batch
                be.setActive(false);
            }
        } 
        // 3. If we aren't processing, check if we CAN start a new batch
        else if (be.inputTank.getFluidAmount() >= 1000 && be.hasSpaceForOutputs()) {
            be.progress = 1; // Start the timer loop!
            be.setActive(true);
        } 
        // 4. Nothing to do, stay idle
        else {
            be.setActive(false);
        }
    }
    private void distributeOutputs() {
    this.outputTanks[1].fill(new FluidStack(ModFluids.HEAVY_OIL_ENTRY.source.get(), 300), FluidAction.EXECUTE);
    this.outputTanks[2].fill(new FluidStack(SulfurFluids.SULFUR_DIESEL_ENTRY.source.get(), 200), FluidAction.EXECUTE);
    this.outputTanks[3].fill(new FluidStack(SulfurFluids.SULFUR_KEROSENE_ENTRY.source.get(), 250), FluidAction.EXECUTE);
    this.outputTanks[4].fill(new FluidStack(SulfurFluids.SULFUR_GASOLINE_ENTRY.source.get(), 150), FluidAction.EXECUTE);
    this.outputTanks[5].fill(new FluidStack(SulfurFluids.SULFUR_NAPHTHA_ENTRY.source.get(), 100), FluidAction.EXECUTE);
}
    private boolean hasSpaceForOutputs() {
    // Ensure all 6 output tanks have room for their designated fractions
        for (int i = 0; i < 6; i++) {
            int amountToAdd = productDistribution[i];
            if (outputTanks[i].fill(new FluidStack(getFluidForIndex(i), amountToAdd), IFluidHandler.FluidAction.SIMULATE) < amountToAdd) {
                return false; // Not enough room in at least one internal tank
            }
        }
        return true;
    }

    private void completeDistillation() {
        // Distribute the fluids permanently into the output buffers
        for (int i = 0; i < 6; i++) {
            int amountToAdd = productDistribution[i];
            outputTanks[i].fill(new FluidStack(getFluidForIndex(i), amountToAdd), IFluidHandler.FluidAction.EXECUTE);
        }
        setChanged(); // Removed "be." prefix since we are inside the class instance context
    }

    private Fluid getFluidForIndex(int index) {
        return switch (index) {
            case 0 -> ModFluids.DIESEL_SOURCE.get(); // Using your fallback workaround for LPG
            case 1 -> SulfurFluids.SULFUR_GASOLINE_ENTRY.source.get();
            case 2 -> SulfurFluids.SULFUR_NAPHTHA_ENTRY.source.get();
            case 3 -> SulfurFluids.SULFUR_KEROSENE_ENTRY.source.get();
            case 4 -> SulfurFluids.SULFUR_DIESEL_ENTRY.source.get();
            default -> ModFluids.BITUMEN_SOURCE.get();
        };
    }

    private FluidStack getProductFluid(int index, int amount) {
        // Returns the fluid for the given product index
        switch (index) {
            case 0: return new FluidStack(ModFluids.BITUMEN_SOURCE.get(), amount);      // Bitumen
            case 1: return new FluidStack(SulfurFluids.SULFUR_DIESEL_ENTRY.source.get(), amount);
            case 2: return new FluidStack(SulfurFluids.SULFUR_KEROSENE_ENTRY.source.get(), amount);
            case 3: return new FluidStack(SulfurFluids.SULFUR_GASOLINE_ENTRY.source.get(), amount);
            case 4: return new FluidStack(SulfurFluids.SULFUR_NAPHTHA_ENTRY.source.get(), amount);
            case 5: return new FluidStack(createLPGFluid(), amount); // LPG as fluid (see below)
            default: return FluidStack.EMPTY;
        }
    }

    // TODO: Define LPG fluid in ModFluids or SulfurFluids, then use it here.
    // For now, we'll create a placeholder fluid.
    private net.minecraft.world.level.material.Fluid createLPGFluid() {
        // You need to register a fluid for LPG. I'll assume you will do that.
        // For now, we'll return null – you'll need to implement.
        // As a temporary workaround, you can use an existing fluid like diesel.
        return ModFluids.DIESEL_SOURCE.get(); // FIXME: replace with real LPG fluid
    }

    private void setActive(boolean active) {
        BlockState state = getBlockState();
        if (state.getValue(DistillationControllerBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(DistillationControllerBlock.ACTIVE, active), 3);
        }
    }

    // ---------- Structure Validation ----------
    private void validateStructure() {
        if (level == null) return;

        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        BlockPos tankPos = null;
        
        // 1. Find an adjacent fluid tank block
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

        // NEW: Travel down the column to find the absolute bottom tank block
        BlockPos.MutableBlockPos bottomPos = tankPos.mutable();
        while (level.getBlockState(bottomPos.below()).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
            bottomPos.move(Direction.DOWN);
        }

        // 2. Scan upward starting from the absolute bottom to calculate total layers
        BlockPos.MutableBlockPos scanPos = bottomPos.mutable();
        int productLayers = 0;
        casingPositions.clear();
        
        while (level.getBlockState(scanPos).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
            casingPositions.add(scanPos.immutable());
            productLayers++;
            scanPos.move(Direction.UP);
        }

        if (productLayers < 6) {
            valid = false;
            return;
        }

        // 3. Verify the Blaze Burner underneath the absolute bottom of the column
        BlockPos burnerPos = bottomPos.below();
        heatLevel = getHeatLevel(burnerPos);
        if (heatLevel == 0) {
            valid = false;
            return;
        }

        towerHeight = productLayers + 1;
        productDistribution = computeDistribution(productLayers);
        valid = true;
    }

    private int getHeatLevel(BlockPos pos) {
        if (level == null) return 0;
        
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        
        // Check if the block belongs to Create and is a blaze burner
        if (blockKey.getNamespace().equals("create") && blockKey.getPath().equals("blaze_burner")) {
            // Look inside the block state to see if it's active or superheated
            // Create uses a property called "heat_level" for Blaze Burners. Let's adapt smoothly:
            String stateStr = state.toString().toLowerCase();
            
            if (stateStr.contains("seething") || stateStr.contains("superheated")) {
                return 3; // Superheated (Blaze cake)
            } else if (stateStr.contains("kindled") || stateStr.contains("heated")) {
                return 2; // Heated (Coal/Logs)
            }
            return 1; // Smoldering / Passive
        }
        return 0; // Not a heat source
    }

    private int[] computeDistribution(int productLayers) {
        int[] counts = new int[PRODUCT_COUNT];
        // Give one layer to each product
        for (int i = 0; i < PRODUCT_COUNT; i++) counts[i] = 1;
        int extra = productLayers - PRODUCT_COUNT;
        boolean bottom = true;
        while (extra > 0) {
            if (bottom) {
                counts[0]++; // Bitumen (bottom)
                bottom = false;
            } else {
                counts[PRODUCT_COUNT - 1]++; // LPG (top)
                bottom = true;
            }
            extra--;
        }
        return counts;
    }

    // Called from casing block entity to get the product index for a given casing position
    public int getProductIndexForPos(BlockPos casingPos) {
        int offset = casingPos.getY() - (worldPosition.getY() - 1); // relative to bottom casing
        // offset should be between 0 and towerHeight-2
        int cum = 0;
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            cum += productDistribution[i];
            if (offset < cum) return i;
        }
        return PRODUCT_COUNT - 1;
    }

    public FluidTank getOutputTank(int productIndex) {
        if (productIndex < 0 || productIndex >= PRODUCT_COUNT) return null;
        return outputTanks[productIndex];
    }

    public int getHeatLevel() { return heatLevel; }

    // ---------- Capability Exposure ----------
    public IFluidHandler getInputCapability(@Nullable Direction side) {
        if (side == null) {
            return inputTank;
        }
        
        // Distribute output fluid handlers depending on the side attached to the controller block
        return switch (side) {
        case UP    -> outputTanks[5]; // LPG
        case NORTH -> outputTanks[3]; // Gasoline
        case SOUTH -> outputTanks[4]; // Naphtha
        case WEST  -> outputTanks[2]; // Kerosene
        case EAST  -> outputTanks[1]; // Diesel
        case DOWN  -> inputTank;      // Bottom can accept input crude oil
    };
    }
        // Add this field inside DistillationControllerBlockEntity.java
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

// Accessor method for your Container Menu registration layer
public SimpleContainerData getDataAccess() {
    return this.dataAccess;
}

    // ---------- NBT ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("input", inputTank.writeToNBT(registries, new CompoundTag()));
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            tag.put("output_" + i, outputTanks[i].writeToNBT(registries, new CompoundTag()));
        }
        tag.putBoolean("valid", valid);
        tag.putInt("towerHeight", towerHeight);
        tag.putIntArray("distribution", productDistribution);
        tag.putInt("heatLevel", heatLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputTank.readFromNBT(registries, tag.getCompound("input"));
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            outputTanks[i].readFromNBT(registries, tag.getCompound("output_" + i));
        }
        valid = tag.getBoolean("valid");
        towerHeight = tag.getInt("towerHeight");
        productDistribution = tag.getIntArray("distribution");
        if (productDistribution.length != PRODUCT_COUNT) productDistribution = new int[PRODUCT_COUNT];
        heatLevel = tag.getInt("heatLevel");
    }
    public void printDiagnostics(Player player) {
    if (level == null || level.isClientSide) return;

    // Send a header to the player's chat
    player.sendSystemMessage(Component.literal("§6--- ⚙ Distillation Tower Diagnostics ⚙ ---"));

    // 1. Check for Adjacent Tank
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
        player.sendSystemMessage(Component.literal("§c❌ Structural Error: No Steel Fluid Tank detected adjacent to the Controller block. Make sure the controller touches one of the four sides of the tank column."));
        return;
    }
    player.sendSystemMessage(Component.literal("§a✔ Structure: Found adjacent Steel Fluid Tank."));

    // 2. Trace to the bottom of the column (using the dynamic fix from earlier)
    BlockPos.MutableBlockPos bottomPos = tankPos.mutable();
    while (level.getBlockState(bottomPos.below()).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
        bottomPos.move(Direction.DOWN);
    }

    // 3. Measure Tower Height
    BlockPos.MutableBlockPos scanPos = bottomPos.mutable();
    int productLayers = 0;
    while (level.getBlockState(scanPos).getBlock() == CreateCrude.STEEL_FLUID_TANK.get()) {
        productLayers++;
        scanPos.move(Direction.UP);
    }

    player.sendSystemMessage(Component.literal("§eℹ Structure: Column height detected = " + productLayers + " blocks. (Requires minimum 6)"));
    if (productLayers < 6) {
        player.sendSystemMessage(Component.literal("§c❌ Structural Error: The Steel Fluid Tank column is too short! Build it higher."));
        return;
    }
    player.sendSystemMessage(Component.literal("§a✔ Structure: Column height is valid."));

    // 4. Check for Heat Source / Blaze Burner
    BlockPos burnerPos = bottomPos.below();
    int currentHeat = getHeatLevel(burnerPos); // Calls your existing method
    
    player.sendSystemMessage(Component.literal("§eℹ Heat: Blaze Burner heat level = " + currentHeat));
    if (currentHeat == 0) {
        player.sendSystemMessage(Component.literal("§c❌ Heat Error: Missing a Blaze Burner directly below the bottom-most tank block, or the burner is not currently lit/fueled."));
        return;
    }
    player.sendSystemMessage(Component.literal("§a✔ Heat: Heat source is active."));
    player.sendSystemMessage(Component.literal("§2✔ MULTIBLOCK STRUCTURE IS VALID!"));

    // 5. Check Operational Requirements (Fluids and Outputs)
    int fluidAmount = this.inputTank.getFluidAmount();
    player.sendSystemMessage(Component.literal("§eℹ Fluids: Crude Oil in Input Tank = " + fluidAmount + "mB / 1000mB"));
    
    if (fluidAmount < 1000) {
        player.sendSystemMessage(Component.literal("§c❌ Operation Warning: Lacking Crude Oil. Note: Input oil MUST be pumped strictly through the BOTTOM face of this controller."));
    }

    if (!hasSpaceForOutputs()) {
        player.sendSystemMessage(Component.literal("§c❌ Operation Warning: Output tanks are full! Empty your output fluids to resume distillation."));
    }

    // Final working state check
    if (fluidAmount >= 1000 && hasSpaceForOutputs()) {
        player.sendSystemMessage(Component.literal("§a⚙ Status: Tower is fully functional and running perfectly!"));
    } else {
        player.sendSystemMessage(Component.literal("§e⚙ Status: STANDBY (Structure is correct, waiting on fluids/space)"));
    }
}

    // ---------- GUI (MenuProvider) ----------
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.createcrude.distillation_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DistillationContainerMenu(id, inv, worldPosition);
    }

    // For menu access
    public FluidTank getInputTank() { return inputTank; }
    public FluidTank[] getOutputTanks() { return outputTanks; }
}