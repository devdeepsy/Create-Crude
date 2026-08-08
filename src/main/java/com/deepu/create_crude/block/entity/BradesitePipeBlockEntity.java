package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModFluids;
import com.deepu.create_crude.SulfurFluids;
import com.deepu.create_crude.gases.network.FluidConversionPayload;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class BradesitePipeBlockEntity extends FluidPipeBlockEntity {

    private static final int PACKET_SIZE = 1000; // 1 bucket per relay step

    @Nullable
    private FluidConversionPayload payload = null;
    @Nullable
    private Direction incomingDirection = null;

    public BradesitePipeBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.BRADESITE_PIPE_BE.get(), pos, state);
    }

    public boolean hasPayload() {
        return payload != null;
    }

    public void setPayload(FluidConversionPayload payload, Direction from) {
        this.payload = payload;
        this.incomingDirection = from;
        setChanged();
    }

    public void clearPayload() {
        this.payload = null;
        this.incomingDirection = null;
        setChanged();
    }

    // ========== driving pump detection (same approach as GasAwarePipeBlockEntity) ==========

    public boolean isPumpDrivingNetwork() {
        if (this.level == null || this.level.isClientSide) return false;
        return scanForActivePump(this.level, this.worldPosition, new HashSet<>(), 0);
    }

    public int getTickDelay() {
        int speed = findDrivingPumpSpeed(this.level, this.worldPosition, new HashSet<>(), 0);
        if (speed <= 0) return 5;
        return Math.max(1, Math.min(20, 256 / speed));
    }

    private static int findDrivingPumpSpeed(Level level, BlockPos pos, Set<BlockPos> visited, int depth) {
        if (depth > 48 || !visited.add(pos)) return 0;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FluidPipeBlock)) return 0;

        for (Direction dir : Direction.values()) {
            BooleanProperty prop = getPipeProperty(state.getBlock(), dir);
            if (prop == null || !state.getValue(prop)) continue;

            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            BlockEntity be = level.getBlockEntity(neighborPos);

            if (isDrivingPump(be, neighborState, dir)) {
                return Math.abs((int) getPumpSpeed(be));
            } else if (be instanceof BradesitePipeBlockEntity) {
                BooleanProperty neighborProp = getPipeProperty(neighborState.getBlock(), dir.getOpposite());
                if (neighborProp != null && neighborState.getValue(neighborProp)) {
                    int speed = findDrivingPumpSpeed(level, neighborPos, visited, depth + 1);
                    if (speed > 0) return speed;
                }
            }
        }
        return 0;
    }

    private static boolean scanForActivePump(Level level, BlockPos pos, Set<BlockPos> visited, int depth) {
        if (depth > 48 || !visited.add(pos)) return false;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FluidPipeBlock)) return false;

        for (Direction dir : Direction.values()) {
            BooleanProperty prop = getPipeProperty(state.getBlock(), dir);
            if (prop == null || !state.getValue(prop)) continue;

            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            BlockEntity be = level.getBlockEntity(neighborPos);

            if (isDrivingPump(be, neighborState, dir)) {
                return true;
            } else if (be instanceof BradesitePipeBlockEntity) {
                BooleanProperty neighborProp = getPipeProperty(neighborState.getBlock(), dir.getOpposite());
                if (neighborProp != null && neighborState.getValue(neighborProp)) {
                    if (scanForActivePump(level, neighborPos, visited, depth + 1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    public IFluidHandler getCapabilityHandler(@Nullable Direction side) {
        // Return your pipe's fluid handler or wrap your custom logic here
        // Example if ConvertingFluidHandler accepts the BlockEntity and side:
        return new ConvertingFluidHandler(this, side);
    }
    private static boolean isDrivingPump(@Nullable BlockEntity be, BlockState neighborState, Direction dir) {
        if (!(be instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity pumpBE)) return false;
        if (pumpBE.getSpeed() == 0) return false;
        if (!neighborState.hasProperty(PumpBlock.FACING)) return false;
        return neighborState.getValue(PumpBlock.FACING) == dir;
    }

    private static float getPumpSpeed(BlockEntity be) {
        return be instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity pumpBE ? pumpBE.getSpeed() : 0;
    }

    @Nullable
    private static BooleanProperty getPipeProperty(Block block, Direction dir) {
        if (!(block instanceof FluidPipeBlock)) return null;
        switch (dir) {
            case NORTH: return FluidPipeBlock.NORTH;
            case SOUTH: return FluidPipeBlock.SOUTH;
            case EAST:  return FluidPipeBlock.EAST;
            case WEST:  return FluidPipeBlock.WEST;
            case UP:    return FluidPipeBlock.UP;
            case DOWN:  return FluidPipeBlock.DOWN;
            default:    return null;
        }
    }

    // ========== pulling hydrotreated_diesel from a neighbor when the pipe is empty ==========

    public void tryPullFromNeighbor(Level level, BlockPos pos, BlockState state, BlockPos neighborPos, Direction dir) {
        if (level.isClientSide || hasPayload()) return;
        if (!isPumpDrivingNetwork()) return;

        IFluidHandler neighborHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, dir.getOpposite());
        if (neighborHandler == null) return;

        ResourceLocation hydrotreatedId = getHydrotreatedDieselId();
        if (hydrotreatedId == null) return;

        FluidStack toDrain = new FluidStack(SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get(), PACKET_SIZE);
        FluidStack drained = neighborHandler.drain(toDrain, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) return;

        FluidStack actuallyDrained = neighborHandler.drain(drained, IFluidHandler.FluidAction.EXECUTE);
        if (actuallyDrained.isEmpty()) return;

        // Convert immediately on pickup: hydrotreated_diesel -> diesel
        ResourceLocation dieselId = getDieselId();
        setPayload(new FluidConversionPayload(dieselId, actuallyDrained.getAmount()), dir);

        int delay = getTickDelay();
        if (!level.getBlockTicks().hasScheduledTick(pos, state.getBlock())) {
            level.scheduleTick(pos, state.getBlock(), delay);
        }
    }

    // ========== relay tick: hop to next Bradesite pipe or deliver ==========

    public static void tick(Level level, BlockPos pos, BlockState state, BradesitePipeBlockEntity be) {
        if (level.isClientSide || !be.hasPayload()) return;

        Direction nextDir = be.findNextDirection(level, pos, state);
        if (nextDir != null) {
            BlockPos nextPos = pos.relative(nextDir);
            if (level.getBlockEntity(nextPos) instanceof BradesitePipeBlockEntity nextBe) {
                if (nextBe.hasPayload()) return;
                Direction nextIncoming = nextDir.getOpposite();
                nextBe.setPayload(be.payload, nextIncoming);
                be.clearPayload();

                int delay = be.getTickDelay();
                if (!level.getBlockTicks().hasScheduledTick(nextPos, state.getBlock())) {
                    level.scheduleTick(nextPos, state.getBlock(), delay);
                }
                return;
            }
        }

        be.deliverPayload(level, pos);
    }

    private void deliverPayload(Level level, BlockPos pos) {
        if (payload == null) return;

        Direction primaryDir = incomingDirection != null ? incomingDirection.getOpposite() : Direction.UP;
        if (tryFillIntoNeighbor(level, pos.relative(primaryDir), primaryDir.getOpposite())) {
            clearPayload();
            return;
        }

        for (Direction dir : Direction.values()) {
            if (incomingDirection != null && dir == incomingDirection) continue;
            if (tryFillIntoNeighbor(level, pos.relative(dir), dir.getOpposite())) {
                clearPayload();
                return;
            }
        }

        // Nowhere to put it (dead end) — hold onto it and retry next scheduled tick
        int delay = getTickDelay();
        if (!level.getBlockTicks().hasScheduledTick(pos, getBlockState().getBlock())) {
            level.scheduleTick(pos, getBlockState().getBlock(), delay);
        }
    }

    private boolean tryFillIntoNeighbor(Level level, BlockPos targetPos, Direction sideOnTarget) {
        if (payload == null) return false;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, sideOnTarget);
        if (handler == null) return false;

        FluidStack toFill = new FluidStack(ModFluids.DIESEL_SOURCE.get(), payload.amount());
        int filled = handler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
        return filled > 0;
    }

    @Nullable
    private Direction findNextDirection(Level level, BlockPos currentPos, BlockState currentState) {
        Set<Direction> candidates = new HashSet<>();
        for (Direction dir : Direction.values()) {
            if (incomingDirection != null && dir == incomingDirection) continue;

            BlockPos neighbor = currentPos.relative(dir);
            if (level.getBlockEntity(neighbor) instanceof BradesitePipeBlockEntity) {
                BlockState neighborState = level.getBlockState(neighbor);
                if (isPipeConnected(currentState, neighborState, dir)) {
                    candidates.add(dir);
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private boolean isPipeConnected(BlockState fromState, BlockState toState, Direction dir) {
        BooleanProperty fromProp = getPipeProperty(fromState.getBlock(), dir);
        BooleanProperty toProp = getPipeProperty(toState.getBlock(), dir.getOpposite());
        if (fromProp == null || toProp == null) return false;
        if (!fromState.hasProperty(fromProp) || !toState.hasProperty(toProp)) return false;

        return fromState.getValue(fromProp) && toState.getValue(toProp);
    }

    @Nullable
    private static ResourceLocation getHydrotreatedDieselId() {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID
            .getKey(SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get());
    }

    private static ResourceLocation getDieselId() {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID
            .getKey(ModFluids.DIESEL_SOURCE.get());
    }

    // ========== NBT ==========

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean includeAll) {
        super.write(tag, registries, includeAll);
        if (payload != null) {
            CompoundTag payloadTag = new CompoundTag();
            payloadTag.putString("FluidId", payload.fluidId().toString());
            payloadTag.putInt("Amount", payload.amount());
            if (incomingDirection != null) {
                payloadTag.putInt("Incoming", incomingDirection.ordinal());
            }
            tag.put("FluidPayload", payloadTag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean includeAll) {
        super.read(tag, registries, includeAll);
        if (tag.contains("FluidPayload")) {
            CompoundTag payloadTag = tag.getCompound("FluidPayload");
            String id = payloadTag.getString("FluidId");
            int amount = payloadTag.getInt("Amount");
            this.payload = new FluidConversionPayload(ResourceLocation.parse(id), amount);
            if (payloadTag.contains("Incoming")) {
                this.incomingDirection = Direction.values()[payloadTag.getInt("Incoming")];
            }
        } else {
            this.payload = null;
            this.incomingDirection = null;
        }
    }
}