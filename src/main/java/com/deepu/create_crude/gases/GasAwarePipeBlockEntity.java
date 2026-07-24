package com.deepu.create_crude.gases;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.SteelFluidTankBlockEntity;
import com.deepu.create_crude.gases.network.GasPayload;
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

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class GasAwarePipeBlockEntity extends FluidPipeBlockEntity {

    @Nullable
    private GasPayload gasPayload = null;
    @Nullable
    private Direction incomingDirection = null;

    public GasAwarePipeBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.GAS_AWARE_PIPE_BE.get(), pos, state);
    }

    public boolean hasGas() {
        return gasPayload != null;
    }

    @Nullable
    public GasPayload getGasPayload() {
        return gasPayload;
    }

    public void setGas(GasPayload payload, Direction from) {
        this.gasPayload = payload;
        this.incomingDirection = from;
        setChanged();
    }

    public void clearGas() {
        this.gasPayload = null;
        this.incomingDirection = null;
        setChanged();
    }

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

            if (be instanceof SteelPumpBlockEntity pumpBE) {
                if (pumpBE.getSpeed() != 0) {
                    Direction pumpFacing = neighborState.getValue(PumpBlock.FACING);
                    if (pumpFacing == dir) {
                        return Math.abs((int) pumpBE.getSpeed());
                    }
                }
            } else if (be instanceof GasAwarePipeBlockEntity) {
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

            if (be instanceof SteelPumpBlockEntity pumpBE) {
                if (pumpBE.getSpeed() != 0) {
                    Direction pumpFacing = neighborState.getValue(PumpBlock.FACING);
                    if (pumpFacing == dir) {
                        return true;
                    }
                }
            } else if (be instanceof GasAwarePipeBlockEntity) {
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

    public static void tick(Level level, BlockPos pos, BlockState state, GasAwarePipeBlockEntity be) {
        if (level.isClientSide || !be.hasGas()) return;

        // 1. Check if sitting directly against an active SteelPump input face
        for (Direction dir : Direction.values()) {
            BooleanProperty prop = getPipeProperty(state.getBlock(), dir);
            if (prop != null && state.getValue(prop)) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof SteelPumpBlockEntity) {
                    BlockState pumpState = level.getBlockState(neighborPos);
                    Direction pumpFacing = pumpState.getValue(PumpBlock.FACING);
                    if (pumpFacing == dir) {
                        return;
                    }
                }
            }
        }

        // 2. Propagate to onward connected pipe
        Direction nextDir = be.findNextDirection(level, pos, state);
        if (nextDir != null) {
            BlockPos nextPos = pos.relative(nextDir);
            if (level.getBlockEntity(nextPos) instanceof GasAwarePipeBlockEntity nextBe) {
                Direction nextIncoming = nextDir.getOpposite();
                nextBe.setGas(be.gasPayload, nextIncoming);
                be.clearGas();

                int delay = be.getTickDelay();
                if (!level.getBlockTicks().hasScheduledTick(nextPos, state.getBlock())) {
                    level.scheduleTick(nextPos, state.getBlock(), delay);
                }
                return;
            }
        }

        // 3. Terminal pipe reached -> Vent into Tank or World
        be.ventGasToWorld(level, pos, state);
    }

    private void ventGasToWorld(Level level, BlockPos pos, BlockState state) {
        if (gasPayload == null) return;
        Block gasBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(gasPayload.gasBlockId());

        Direction primaryDir = incomingDirection != null ? incomingDirection.getOpposite() : Direction.UP;
        BlockPos primaryPos = pos.relative(primaryDir);

        // Check if primary exit face is a SteelFluidTankBlockEntity
        if (level.getBlockEntity(primaryPos) instanceof SteelFluidTankBlockEntity tankBE) {
            int filled = tankBE.fillGas(gasPayload.gasBlockId(), 1000, false);
            if (filled > 0) {
                clearGas();
                return;
            }
        }

        if (level.getBlockState(primaryPos).canBeReplaced() && !(level.getBlockEntity(primaryPos) instanceof GasAwarePipeBlockEntity)) {
            if (gasBlock instanceof GasBlock) {
                level.setBlock(primaryPos, gasBlock.defaultBlockState()
                        .setValue(GasBlock.RADIUS, Math.min(gasPayload.radius(), GasBlock.MAX_RADIUS))
                        .setValue(GasBlock.SOURCE, false), 3);
            }
            clearGas();
            return;
        }

        // Fallback Scanner: Look in all alternative directions
        for (Direction dir : Direction.values()) {
            if (incomingDirection != null && dir == incomingDirection) continue;

            BlockPos fallbackPos = pos.relative(dir);

            if (level.getBlockEntity(fallbackPos) instanceof SteelFluidTankBlockEntity tankBE) {
                int filled = tankBE.fillGas(gasPayload.gasBlockId(), 1000, false);
                if (filled > 0) {
                    clearGas();
                    return;
                }
            }

            if (level.getBlockState(fallbackPos).canBeReplaced() && !(level.getBlockEntity(fallbackPos) instanceof GasAwarePipeBlockEntity)) {
                if (gasBlock instanceof GasBlock) {
                    level.setBlock(fallbackPos, gasBlock.defaultBlockState()
                            .setValue(GasBlock.RADIUS, Math.min(gasPayload.radius(), GasBlock.MAX_RADIUS))
                            .setValue(GasBlock.SOURCE, false), 3);
                }
                clearGas();
                return;
            }
        }

        clearGas();
    }

    @Nullable
    private Direction findNextDirection(Level level, BlockPos currentPos, BlockState currentState) {
        Set<Direction> candidates = new HashSet<>();
        for (Direction dir : Direction.values()) {
            if (incomingDirection != null && dir == incomingDirection) continue;

            BlockPos neighbor = currentPos.relative(dir);
            if (level.getBlockEntity(neighbor) instanceof GasAwarePipeBlockEntity) {
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

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean includeAll) {
        super.write(tag, registries, includeAll);
        if (gasPayload != null) {
            CompoundTag gasTag = new CompoundTag();
            gasTag.putString("GasId", gasPayload.gasBlockId().toString());
            gasTag.putInt("Radius", gasPayload.radius());
            if (incomingDirection != null) {
                gasTag.putInt("Incoming", incomingDirection.ordinal());
            }
            tag.put("GasPayload", gasTag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean includeAll) {
        super.read(tag, registries, includeAll);
        if (tag.contains("GasPayload")) {
            CompoundTag gasTag = tag.getCompound("GasPayload");
            String id = gasTag.getString("GasId");
            int radius = gasTag.getInt("Radius");
            this.gasPayload = new GasPayload(ResourceLocation.parse(id), radius);
            if (gasTag.contains("Incoming")) {
                this.incomingDirection = Direction.values()[gasTag.getInt("Incoming")];
            }
        } else {
            this.gasPayload = null;
            this.incomingDirection = null;
        }
    }
}