package com.deepu.create_crude.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.DistillerBlock;
import com.deepu.create_crude.gases.GasBlock;
import com.deepu.create_crude.gases.GasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

public class DistillerBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private BlockPos masterPos;

    public static final int MAX_MIXTURE_CAPACITY = 10000;
    public static final int MAX_GAS_CAPACITY = 10000;

    private int mixtureAmount = 0;
    private int propyleneAmount = 0;
    private int ethyleneAmount = 0;

    private static final int MIXTURE_CONSUMPTION = 10;
    private static final int PROPYLENE_PRODUCTION = 5;
    private static final int ETHYLENE_PRODUCTION = 5;

    private int timer = 0;
    private final int processTime = 20;

    public DistillerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.DISTILLER_BE.get(), pos, state);
    }

    public void setMasterPos(@Nullable BlockPos pos) {
        this.masterPos = pos;
        setChanged();
    }

    public boolean isMaster() {
        return masterPos != null && worldPosition.equals(masterPos);
    }

    public DistillerBlockEntity getMaster() {
        if (masterPos != null && level != null && level.getBlockEntity(masterPos) instanceof DistillerBlockEntity be) {
            return be;
        }
        return this;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillerBlockEntity be) {
        if (!be.isMaster() || !state.getValue(DistillerBlock.FORMED)) return;

        be.pullMixtureFromCracker();
        be.tickProcess();
    }

    private void pullMixtureFromCracker() {
        if (level == null || mixtureAmount >= MAX_MIXTURE_CAPACITY) return;

        BlockPos belowPos = worldPosition.below();
        if (level.getBlockEntity(belowPos) instanceof CrackerBlockEntity cracker) {
            CrackerBlockEntity masterCracker = cracker.getMaster();
            if (masterCracker != null) {
                int needed = MAX_MIXTURE_CAPACITY - mixtureAmount;
                int extracted = masterCracker.extractMixture(needed);
                if (extracted > 0) {
                    this.mixtureAmount += extracted;
                    notifyUpdate();
                }
            }
        }
    }

    private void tickProcess() {
        if (canProcess()) {
            timer++;
            if (timer >= processTime) {
                timer = 0;
                executeDistillation();
            }
        } else {
            timer = 0;
        }
    }

    private boolean canProcess() {
        return mixtureAmount >= MIXTURE_CONSUMPTION;
    }

    private void executeDistillation() {
        mixtureAmount -= MIXTURE_CONSUMPTION;

        this.propyleneAmount += PROPYLENE_PRODUCTION;
        this.ethyleneAmount += ETHYLENE_PRODUCTION;

        // Vent to world via GasRegistry if buffer capacity overflows
        if (this.propyleneAmount > MAX_GAS_CAPACITY) {
            this.propyleneAmount = MAX_GAS_CAPACITY;
            ventGasToWorld("propylene_block", worldPosition.above());
        }
        if (this.ethyleneAmount > MAX_GAS_CAPACITY) {
            this.ethyleneAmount = MAX_GAS_CAPACITY;
            ventGasToWorld("ethylene_block", worldPosition.above().east());
        }

        notifyUpdate();
    }

    private void ventGasToWorld(String gasName, BlockPos targetPos) {
        if (level == null || level.isClientSide) return;

        for (GasRegistry.GasEntry entry : GasRegistry.getAll()) {
            if (entry.block.getId().getPath().equals(gasName)) {
                if (level.isEmptyBlock(targetPos)) {
                    BlockState gasState = entry.block.get().defaultBlockState()
                        .setValue(GasBlock.SOURCE, true)
                        .setValue(GasBlock.ACTIVE, true);
                    level.setBlock(targetPos, gasState, 3);
                }
                break;
            }
        }
    }

    public int extractPropylene(int amount) {
        if (!isMaster()) return getMaster().extractPropylene(amount);
        int extracted = Math.min(amount, this.propyleneAmount);
        if (extracted > 0) {
            this.propyleneAmount -= extracted;
            notifyUpdate();
        }
        return extracted;
    }

    public int extractEthylene(int amount) {
        if (!isMaster()) return getMaster().extractEthylene(amount);
        int extracted = Math.min(amount, this.ethyleneAmount);
        if (extracted > 0) {
            this.ethyleneAmount -= extracted;
            notifyUpdate();
        }
        return extracted;
    }

    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (masterPos != null) tag.putLong("MasterPos", masterPos.asLong());
        tag.putInt("MixtureAmount", mixtureAmount);
        tag.putInt("PropyleneAmount", propyleneAmount);
        tag.putInt("EthyleneAmount", ethyleneAmount);
        tag.putInt("Timer", timer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MasterPos")) {
            masterPos = BlockPos.of(tag.getLong("MasterPos"));
        } else {
            masterPos = null;
        }
        if (tag.contains("MixtureAmount")) mixtureAmount = tag.getInt("MixtureAmount");
        if (tag.contains("PropyleneAmount")) propyleneAmount = tag.getInt("PropyleneAmount");
        if (tag.contains("EthyleneAmount")) ethyleneAmount = tag.getInt("EthyleneAmount");
        if (tag.contains("Timer")) timer = tag.getInt("Timer");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!getBlockState().getValue(DistillerBlock.FORMED)) {
            tooltip.add(Component.literal("  ").append(Component.literal("Structure Incomplete").withStyle(ChatFormatting.RED)));
            return true;
        }

        DistillerBlockEntity master = getMaster();
        tooltip.add(Component.literal("  ").append(Component.literal("Distillation Tower Overview").withStyle(ChatFormatting.GOLD)));

        String mixtureText = master.mixtureAmount + " / " + MAX_MIXTURE_CAPACITY + " Units";
        tooltip.add(Component.literal("  ").append(Component.literal("Inflow Mixture: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(mixtureText).withStyle(ChatFormatting.AQUA)));

        String propyleneText = master.propyleneAmount + " / " + MAX_GAS_CAPACITY + " Units";
        tooltip.add(Component.literal("  ").append(Component.literal("Propylene Yield: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(propyleneText).withStyle(ChatFormatting.GREEN)));

        String ethyleneText = master.ethyleneAmount + " / " + MAX_GAS_CAPACITY + " Units";
        tooltip.add(Component.literal("  ").append(Component.literal("Ethylene Yield: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(ethyleneText).withStyle(ChatFormatting.LIGHT_PURPLE)));

        return true;
    }
}