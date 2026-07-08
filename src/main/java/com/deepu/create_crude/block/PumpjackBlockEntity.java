package com.deepu.create_crude.block;

import com.deepu.create_crude.CreateCrude;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class PumpjackBlockEntity extends BlockEntity {
    private int tickCounter = 0;
    private int scanCooldown = 0;
    private boolean isInfiniteCached = false;
    private boolean isCommandTesting = false;
    private boolean isStructureValid = false;
    public float rotationSpeed = 0.0f;
    public float visualRotationAngle = 0.0f;
    private int validationCooldown = 0;


    private final FluidTank internalTank = new FluidTank(16000);

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.PUMPJACK_BE.get(), pos, state);
    }
    
    public FluidTank getInternalTank(){
        return this.internalTank;
    }

    public boolean isStructureValid() {
        return this.isStructureValid;
    }
    
    public @Nullable BlockPos getHolderPos() {
        if (this.level == null) return null;
        Direction facing = getFacingDirection();
        Direction back = facing.getOpposite();
        BlockPos pillarBase = this.worldPosition.relative(back, 4);

        for (int y = 1; y <= 6; y++) {
            if (this.level.getBlockState(pillarBase.above(y)).is(CreateCrude.PUMPJACK_HOLDER.get())) {
                return pillarBase.above(y);
            }
        }
        return null;
    }

    private Direction getFacingDirection() {
        BlockState state = this.getBlockState();
        if (state.hasProperty(PumpjackBlock.FACING)) {
            return state.getValue(PumpjackBlock.FACING);
        }
        return Direction.NORTH; 
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        tickCounter++;

        // ----- SERVER: structure validation and power -----
        if (!level.isClientSide && tickCounter >= 20) {
            tickCounter = 0;
            if (--validationCooldown <= 0) {
                boolean currentlyValid = verifyStructureLayout(level, pos, state);

                Direction back = getFacingDirection().getOpposite();
                BlockPos powerPos = pos.relative(back, 9);
                BlockEntity powerBE = level.getBlockEntity(powerPos);

                float realSpeed = 0.0f;
                if (powerBE instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kbe) {
                    realSpeed = Math.abs(kbe.getSpeed());
                } else if (!level.isEmptyBlock(powerPos)) {
                    realSpeed = 16.0f; // fallback for testing
                }

                // Only update if something changed
                if (currentlyValid != this.isStructureValid || realSpeed != this.rotationSpeed) {
                    this.isStructureValid = currentlyValid;
                    this.rotationSpeed = realSpeed;

                    updateStructureBlockStates(level, currentlyValid);
                    setChanged();
                    level.sendBlockUpdated(pos, state, state, 3); // sync to client
                }

                validationCooldown = 5; // re-check every 5 seconds
            }
            // Do NOT overwrite isStructureValid here – it's already set above.
        }

        if (this.isStructureValid && Math.abs(this.rotationSpeed) > 0.01f) {
            this.visualRotationAngle += this.rotationSpeed * 0.0015f;
            if (this.visualRotationAngle > Math.PI * 2) {
                this.visualRotationAngle -= (float) (Math.PI * 2);
            }

            // Fluid extraction (server only)
            if (!level.isClientSide) {
                // Try to extract from the reservoir below
                int extracted = executeExtraction(level, pos, null);
                if (extracted > 0) {
                    setChanged();
                }
                // If no fluid, do nothing. You could optionally add a message or log.
            }

            // Particles (client only)
            if (level.isClientSide && level.random.nextFloat() < 0.15F) {
                level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.SMOKE,
                        pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                        pos.getY() + 1.0,
                        pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                        0.0, 0.05, 0.0
                );
            }
        }
    }
    private void processRefiningPhase() {
    // Drain crude fluid from your internal tank, and convert it into items or an output tank
    if (this.internalTank.getFluidAmount() >= 100) {
        this.internalTank.drain(100, IFluidHandler.FluidAction.EXECUTE);
        
        // E.g., Output refined canisters, items, or push to a secondary refined fuel tank here
        // CreateCrude.LOGGER.info("Refining crude materials into processed fuel...");
        setChanged();
    }
}
    private void updateStructureBlockStates(Level level, boolean isFormed) {
        BlockPos holderPos = getHolderPos();
        if (holderPos == null) return;

        Direction facing = getFacingDirection();
        Direction front = facing;
        Direction back = facing.getOpposite();

        List<BlockPos> components = new ArrayList<>();

        // 1. The central holder block itself (since it tilts in your renderer loop)
        components.add(holderPos);

        // 2. Front Pieces (Rods)
        for (int i = 1; i <= 3; i++) {
            components.add(holderPos.relative(front, i));
        }
        // Front Head Sandwich
        BlockPos headPos = holderPos.relative(front, 4);
        components.add(headPos);
        components.add(headPos.above());
        components.add(headPos.below());

        // 3. Rear Pieces (Rods)
        for (int i = 1; i <= 3; i++) {
            components.add(holderPos.relative(back, i));
        }
        // Rear Counterweight Sandwich
        BlockPos cwPos = holderPos.relative(back, 4);
        components.add(cwPos);
        components.add(cwPos.above());
        components.add(cwPos.below());

        // 4. The Crank Block (Calculated exactly to match your PumpjackRenderer world offset)
        BlockPos crankWorldPos = new BlockPos(
            holderPos.getX() + (back.getStepX() * 4),
            this.worldPosition.getY() + 1,
            holderPos.getZ() + (back.getStepZ() * 4)
        );
        components.add(crankWorldPos);

        // Sweeps across all block positions and sets "formed" safely
        for (BlockPos p : components) {
            BlockState originalState = level.getBlockState(p);
            
            for (var prop : originalState.getProperties()) {
                if (prop.getName().equals("formed") && prop instanceof net.minecraft.world.level.block.state.properties.BooleanProperty boolProp) {
                    if (originalState.getValue(boolProp) != isFormed) {
                        level.setBlock(p, originalState.setValue(boolProp, isFormed), 3);
                    }
                    break;
                }
            }
        }
    }

    public boolean verifyStructureLayout(Level level, BlockPos pos, BlockState state) {
        Direction facing = getFacingDirection();
        Direction front = facing;
        Direction back = facing.getOpposite();

        BlockPos holderPos = getHolderPos();
        if (holderPos == null) return false;

        // 1. FRONT BEAM
        for (int i = 1; i <= 3; i++) {
            if (!level.getBlockState(holderPos.relative(front, i)).is(CreateCrude.PUMPJACK_ROD.get())) return false;
        }

        // 2. FRONT HEAD ASSEMBLY
        BlockPos headPos = holderPos.relative(front, 4);
        if (!level.getBlockState(headPos).is(CreateCrude.PUMPJACK_HAMMER_HEAD.get())) return false;
        if (!level.getBlockState(headPos.above()).is(CreateCrude.PUMPJACK_HAMMER_COMPANION.get())) return false;
        if (!level.getBlockState(headPos.below()).is(CreateCrude.PUMPJACK_HAMMER_COMPANION.get())) return false;

        // 3. BACK BEAM
        for (int i = 1; i <= 3; i++) {
            if (!level.getBlockState(holderPos.relative(back, i)).is(CreateCrude.PUMPJACK_ROD.get())) return false;
        }

        // 4. REAR COUNTERWEIGHT ASSEMBLY
        BlockPos cwPos = holderPos.relative(back, 4);
        if (!level.getBlockState(cwPos).is(CreateCrude.PUMPJACK_COUNTERWEIGHT.get())) return false;
        if (!level.getBlockState(cwPos.above()).is(CreateCrude.PUMPJACK_HAMMER.get())) return false;
        if (!level.getBlockState(cwPos.below()).is(CreateCrude.PUMPJACK_HAMMER.get())) return false;

        // 5. STATIC INPUT CHECK
        BlockPos crankPos = pos.relative(back, 8);
        boolean hasCrank = level.getBlockState(crankPos).is(CreateCrude.PUMPJACK_CRANK.get()) ||
                level.getBlockState(crankPos.above()).is(CreateCrude.PUMPJACK_CRANK.get());
        if (!hasCrank) return false;

        return true;
    }
    
    private boolean isFaceGlued(Level level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        AABB faceBox = new AABB(
                Math.min(pos.getX(), neighborPos.getX()), Math.min(pos.getY(), neighborPos.getY()), Math.min(pos.getZ(), neighborPos.getZ()),
                Math.max(pos.getX(), neighborPos.getX()) + 1, Math.max(pos.getY(), neighborPos.getY()) + 1, Math.max(pos.getZ(), neighborPos.getZ()) + 1
        ).inflate(0.05);

        List<com.simibubi.create.content.contraptions.glue.SuperGlueEntity> glues =
                level.getEntitiesOfClass(com.simibubi.create.content.contraptions.glue.SuperGlueEntity.class, faceBox);

        for (var glue : glues) {
            if (glue.isAlive()) return true;
        }
        return false;
    }

       public void forceTestPump(Player player) {
        if (this.level == null) return;
        
        player.sendSystemMessage(Component.literal("§7============ PUMPJACK DIAGNOSTICS ============§r"));
        boolean allPassed = true;

        Direction facing = getFacingDirection();
        Direction front = facing;
        Direction back = facing.getOpposite();

        BlockPos holderPos = getHolderPos();
        if (holderPos == null) {
            BlockPos pillarBase = this.worldPosition.relative(back, 4);
            player.sendSystemMessage(Component.literal("§c[FAIL] Central Holder block not found!§r"));
            player.sendSystemMessage(Component.literal("§eScanned Column Location: " + pillarBase.getX() + ", " + pillarBase.getZ() + "§r"));
            
            for (int y = 1; y <= 6; y++) {
                BlockPos checkPos = pillarBase.above(y);
                String blockName = level.getBlockState(checkPos).getBlock().getName().getString();
                player.sendSystemMessage(Component.literal("  §7└─ Y=" + checkPos.getY() + ": §f" + blockName));
            }
            player.sendSystemMessage(Component.literal("§4==============================================§r"));
            return; 
        } else {
            player.sendSystemMessage(Component.literal("§a[PASS] Central Holder found at " + holderPos.toShortString() + "§r"));
        }

        for (int i = 1; i <= 3; i++) {
            BlockPos rodPos = holderPos.relative(front, i);
            BlockState rodState = level.getBlockState(rodPos);
            if (!rodState.is(CreateCrude.PUMPJACK_ROD.get())) {
                player.sendSystemMessage(Component.literal("§c[FAIL] Front Rod #" + i + " missing at " + rodPos.toShortString() + "§r"));
                allPassed = false;
            }
            BlockPos prevPos = holderPos.relative(front, i - 1);
            if (!isFaceGlued(level, prevPos, front)) {
                player.sendSystemMessage(Component.literal("§c[FAIL] Missing Super Glue between " + prevPos.toShortString() + " ➔ " + rodPos.toShortString() + "§r"));
                allPassed = false;
            }
        }

        BlockPos headPos = holderPos.relative(front, 4);
        BlockState headState = level.getBlockState(headPos);
        if (!headState.is(CreateCrude.PUMPJACK_HAMMER_HEAD.get())) {
            player.sendSystemMessage(Component.literal("§c[FAIL] Hammer Head missing at " + headPos.toShortString() + "§r"));
            allPassed = false;
        }
        if (!isFaceGlued(level, holderPos.relative(front, 3), front)) {
            player.sendSystemMessage(Component.literal("§c[FAIL] Missing Super Glue between Front Rod #3 and the Hammer Head!§r"));
            allPassed = false;
        }

        BlockPos compAbovePos = headPos.above();
        if (!level.getBlockState(compAbovePos).is(CreateCrude.PUMPJACK_HAMMER_COMPANION.get())) {
            allPassed = false;
        } else if (!isFaceGlued(level, headPos, Direction.UP)) {
            allPassed = false;
        }

        BlockPos compBelowPos = headPos.below();
        if (!level.getBlockState(compBelowPos).is(CreateCrude.PUMPJACK_HAMMER_COMPANION.get())) {
            allPassed = false;
        } else if (!isFaceGlued(level, headPos, Direction.DOWN)) {
            allPassed = false;
        }

        for (int i = 1; i <= 3; i++) {
            BlockPos rodPos = holderPos.relative(back, i);
            if (!level.getBlockState(rodPos).is(CreateCrude.PUMPJACK_ROD.get())) {
                allPassed = false;
            }
            BlockPos prevPos = holderPos.relative(back, i - 1);
            if (!isFaceGlued(level, prevPos, back)) {
                allPassed = false;
            }
        }

        BlockPos cwPos = holderPos.relative(back, 4);
        if (!level.getBlockState(cwPos).is(CreateCrude.PUMPJACK_COUNTERWEIGHT.get())) {
            allPassed = false;
        }
        if (!isFaceGlued(level, holderPos.relative(back, 3), back)) {
            allPassed = false;
        }

        BlockPos hamAbovePos = cwPos.above();
        if (!level.getBlockState(hamAbovePos).is(CreateCrude.PUMPJACK_HAMMER.get())) {
            allPassed = false;
        } else if (!isFaceGlued(level, cwPos, Direction.UP)) {
            allPassed = false;
        }

        BlockPos hamBelowPos = cwPos.below();
        if (!level.getBlockState(hamBelowPos).is(CreateCrude.PUMPJACK_HAMMER.get())) {
            allPassed = false;
        } else if (!isFaceGlued(level, cwPos, Direction.DOWN)) {
            allPassed = false;
        }

        BlockPos crankPos = this.worldPosition.relative(back, 8);
        boolean hasCrank = level.getBlockState(crankPos).is(CreateCrude.PUMPJACK_CRANK.get()) || 
                           level.getBlockState(crankPos.above()).is(CreateCrude.PUMPJACK_CRANK.get());
        if (!hasCrank) allPassed = false;

        if (allPassed) {
            player.sendSystemMessage(Component.literal("§2==============================================§r"));
            player.sendSystemMessage(Component.literal("§a✔ STRUCTURE COMPLETELY VALID! Ready for assembly.§r"));
            player.sendSystemMessage(Component.literal("§2==============================================§r"));
        } else {
            player.sendSystemMessage(Component.literal("§4==============================================§r"));
            player.sendSystemMessage(Component.literal("§c❌ LAYOUT INVALID. Resolve the issues listed above!§r"));
            player.sendSystemMessage(Component.literal("§4==============================================§r"));
        }
    }

    private @Nullable List<BlockPos> scanPool(Level level, BlockPos startPos, Fluid targetFluid) {
        if (targetFluid == Fluids.EMPTY) return new ArrayList<>();
        List<BlockPos> sourceBlocks = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(startPos);
        visited.add(startPos);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            FluidState currentState = level.getFluidState(current);
            if (currentState.isSource()) sourceBlocks.add(current);
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!visited.contains(next)) {
                    FluidState nextState = level.getFluidState(next);
                    if (!nextState.isEmpty() && nextState.getType().isSame(targetFluid)) {
                        visited.add(next);
                        queue.add(next);
                        if (visited.size() >= 500) return null;
                    }
                }
            }
        }
        return sourceBlocks;
    }

    @Override
    public void handleUpdateTag(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        this.isStructureValid = tag.getBoolean("IsStructureValid"); 
        this.rotationSpeed    = tag.getFloat("RotationSpeed");     
        this.visualRotationAngle = tag.getFloat("VisualRotation");  
    }

    private @Nullable BlockPos findFluidEntry(Level level, BlockPos pos) {
        BlockPos directlyBelow = pos.below();
        if (!(level.getBlockState(directlyBelow).getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock)) return null;
        BlockPos.MutableBlockPos cur = directlyBelow.mutable();
        while (level.getBlockState(cur).getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock) {
            cur.move(Direction.DOWN);
        }
        BlockPos tipPos = cur.immutable();
        FluidState tipFluid = level.getFluidState(tipPos);
        if (!tipFluid.isEmpty()) return tipPos;
        BlockPos.MutableBlockPos lookDown = tipPos.mutable();
        for (int i = 0; i < 64; i++) {
            FluidState fs = level.getFluidState(lookDown);
            if (!fs.isEmpty()) return lookDown.immutable();
            if (!level.getBlockState(lookDown).isAir()) break;
            lookDown.move(Direction.DOWN);
        }
        return null;
    }

    public int executeExtraction(Level level, BlockPos pos, @Nullable Player debugPlayer) {
        if (!this.isStructureValid && !isCommandTesting) return 0;
        if (internalTank.getFluidAmount() >= internalTank.getCapacity()) return 0;
        BlockPos entryPos = findFluidEntry(level, pos);
        if (entryPos == null) return 0;

        var reservoirHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, entryPos, Direction.UP);
        if (reservoirHandler != null) {
            FluidStack drained = reservoirHandler.drain(200, IFluidHandler.FluidAction.SIMULATE);
            int accepted = internalTank.fill(drained, IFluidHandler.FluidAction.SIMULATE);
            if (accepted > 0) {
                FluidStack realDrain = reservoirHandler.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                internalTank.fill(realDrain, IFluidHandler.FluidAction.EXECUTE);
                setChanged();
                return accepted;
            }
            return 0;
        }

        FluidState entryFluidState = level.getFluidState(entryPos);
        Fluid fluidType = entryFluidState.getType();
        if (fluidType instanceof FlowingFluid flowingFluid) fluidType = flowingFluid.getSource();
        if (fluidType == Fluids.EMPTY) return 0;

        scanCooldown--;
        boolean needsScan = !isInfiniteCached || scanCooldown <= 0 || isCommandTesting;
        List<BlockPos> finitePool = null;
        if (needsScan) {
            finitePool = scanPool(level, entryPos, fluidType);
            if (finitePool == null) {
                this.isInfiniteCached = true;
                this.scanCooldown = 20;
            } else {
                this.isInfiniteCached = false;
            }
        }

        if (this.isInfiniteCached) {
            FluidStack fluidToPump = new FluidStack(fluidType, 200);
            int accepted = internalTank.fill(fluidToPump, IFluidHandler.FluidAction.EXECUTE);
            setChanged();
            return accepted;
        } else {
            if (finitePool == null || finitePool.isEmpty()) return 0;
            FluidStack fullBucketStack = new FluidStack(fluidType, 1000);
            int accepted = internalTank.fill(fullBucketStack, IFluidHandler.FluidAction.SIMULATE);
            if (accepted >= 1000) {
                BlockPos targetBlock = null;
                for (BlockPos p : finitePool) {
                    if (targetBlock == null || p.getY() < targetBlock.getY()) targetBlock = p;
                }
                if (targetBlock != null) {
                    internalTank.fill(fullBucketStack, IFluidHandler.FluidAction.EXECUTE);
                    level.setBlock(targetBlock, Blocks.AIR.defaultBlockState(), 3);
                    setChanged();
                    return 1000;
                }
            }
        }
        return 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        internalTank.writeToNBT(registries, tag);
        tag.putBoolean("IsInfiniteCached", isInfiniteCached);
        tag.putBoolean("IsStructureValid", isStructureValid);
        tag.putFloat("RotationSpeed", rotationSpeed);          
        tag.putFloat("VisualRotation", visualRotationAngle);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        internalTank.readFromNBT(registries, tag);
        this.isInfiniteCached = tag.getBoolean("IsInfiniteCached");
        this.isStructureValid = tag.getBoolean("IsStructureValid");
        this.rotationSpeed = tag.getFloat("RotationSpeed");    
    this.visualRotationAngle = tag.getFloat("VisualRotation");

    }
    @Override
public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
}

@Override
public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    CompoundTag tag = new CompoundTag();
    saveAdditional(tag, registries);
    return tag;
}

@Override
public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
    CompoundTag tag = pkt.getTag();
    if (tag != null) {
        loadAdditional(tag, registries);
        // Forces the renderer to wake up and apply the new values
        if (this.level != null && this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
}