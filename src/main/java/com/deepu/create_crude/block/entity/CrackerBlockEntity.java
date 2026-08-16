package com.deepu.create_crude.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.CrackerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

public class CrackerBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private BlockPos masterPos;
    
    // Resource Capacities
    public static final int MAX_STEAM_CAPACITY = 10000;
    public static final int MAX_MIXTURE_CAPACITY = 10000;

    private int steamAmount = 0;
    private int mixtureAmount = 0;

    // Reaction Rates per Cycle
    private static final int NAPHTHA_CONSUMPTION = 10; // mB
    private static final int STEAM_CONSUMPTION = 5;     // Units
    private static final int MIXTURE_PRODUCTION = 10;   // Units

    private int timer = 0;
    private final int processTime = 20; // 1 second per conversion cycle

    // Input Tank for Light Naphtha
    private final FluidTank naphthaTank = new FluidTank(10000, stack -> isNaphtha(stack)) {
        @Override
        protected void onContentsChanged() {
            notifyUpdate();
        }
    };

    public CrackerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCrude.CRACKER_BE.get(), pos, state);
    }

    public boolean isMaster() {
        return worldPosition.equals(masterPos);
    }

    public CrackerBlockEntity getMaster() {
        if (masterPos != null && level != null && level.getBlockEntity(masterPos) instanceof CrackerBlockEntity be) {
            return be;
        }
        return null;
    }

    public void setMasterPos(BlockPos pos) {
        this.masterPos = pos;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrackerBlockEntity be) {
        if (!be.isMaster() || !state.getValue(CrackerBlock.FORMED)) return;
        be.tickProcess();
    }

    private void tickProcess() {
        if (canProcess()) {
            timer++;
            if (timer >= processTime) {
                timer = 0;
                executeReaction();
            }
        } else {
            timer = 0;
        }
    }

    private boolean canProcess() {
        if (steamAmount < STEAM_CONSUMPTION) return false;
        if (naphthaTank.getFluidAmount() < NAPHTHA_CONSUMPTION) return false;
        return (mixtureAmount + MIXTURE_PRODUCTION) <= MAX_MIXTURE_CAPACITY;
    }

    private void executeReaction() {
        naphthaTank.drain(NAPHTHA_CONSUMPTION, IFluidHandler.FluidAction.EXECUTE);
        steamAmount -= STEAM_CONSUMPTION;
        mixtureAmount += MIXTURE_PRODUCTION;
        notifyUpdate();
    }

    // --- Steam Management ---
    public int getSteamAmount() {
        if (!isMaster()) {
            CrackerBlockEntity master = getMaster();
            return master != null ? master.getSteamAmount() : 0;
        }
        return this.steamAmount;
    }

    public int addSteam(int amount) {
        if (!isMaster()) {
            CrackerBlockEntity master = getMaster();
            return master != null ? master.addSteam(amount) : 0;
        }
        int inserted = Math.min(amount, MAX_STEAM_CAPACITY - this.steamAmount);
        if (inserted > 0) {
            this.steamAmount += inserted;
            notifyUpdate();
        }
        return inserted;
    }

    // --- Mixture Management ---
    public int getMixtureAmount() {
        if (!isMaster()) {
            CrackerBlockEntity master = getMaster();
            return master != null ? master.getMixtureAmount() : 0;
        }
        return this.mixtureAmount;
    }

    public int addMixture(int amount) {
        if (!isMaster()) {
            CrackerBlockEntity master = getMaster();
            return master != null ? master.addMixture(amount) : 0;
        }
        int inserted = Math.min(amount, MAX_MIXTURE_CAPACITY - this.mixtureAmount);
        if (inserted > 0) {
            this.mixtureAmount += inserted;
            notifyUpdate();
        }
        return inserted;
    }

    public int extractMixture(int amount) {
        if (!isMaster()) {
            CrackerBlockEntity master = getMaster();
            return master != null ? master.extractMixture(amount) : 0;
        }
        int extracted = Math.min(amount, this.mixtureAmount);
        if (extracted > 0) {
            this.mixtureAmount -= extracted;
            notifyUpdate();
        }
        return extracted;
    }

    // --- Naphtha & Fluid Capabilities ---
    public FluidTank getNaphthaTank() {
        if (!isMaster()) {
            CrackerBlockEntity master = getMaster();
            return master != null ? master.getNaphthaTank() : naphthaTank;
        }
        return naphthaTank;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return getNaphthaTank();
    }

    private static boolean isNaphtha(FluidStack stack) {
        return stack.getFluid().getFluidType().getDescriptionId().contains("naphtha");
    }

    // --- Serialization & Sync ---
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

    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (masterPos != null) tag.putLong("MasterPos", masterPos.asLong());
        tag.putInt("SteamAmount", steamAmount);
        tag.putInt("MixtureAmount", mixtureAmount);
        tag.putInt("Timer", timer);
        tag.put("NaphthaTank", naphthaTank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MasterPos")) masterPos = BlockPos.of(tag.getLong("MasterPos"));
        if (tag.contains("SteamAmount")) steamAmount = tag.getInt("SteamAmount");
        if (tag.contains("MixtureAmount")) mixtureAmount = tag.getInt("MixtureAmount");
        if (tag.contains("Timer")) timer = tag.getInt("Timer");
        if (tag.contains("NaphthaTank")) naphthaTank.readFromNBT(registries, tag.getCompound("NaphthaTank"));
    }

    // --- Overlay Rendering ---
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!getBlockState().getValue(CrackerBlock.FORMED)) {
            tooltip.add(Component.literal("  ").append(Component.literal("Structure Incomplete").withStyle(ChatFormatting.RED)));
            return true;
        }

        CrackerBlockEntity master = getMaster();
        if (master == null) return false;

        tooltip.add(Component.literal("  ").append(Component.literal("Thermal Cracker Overview").withStyle(ChatFormatting.GOLD)));
        
        FluidStack naphtha = master.getNaphthaTank().getFluid();
        tooltip.add(Component.literal("  ").append(Component.literal("Light Naphtha: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(naphtha.getAmount() + " / " + master.getNaphthaTank().getCapacity() + " mB").withStyle(ChatFormatting.YELLOW)));

        String steamText = master.getSteamAmount() + " / " + MAX_STEAM_CAPACITY + " Units";
        tooltip.add(Component.literal("  ").append(Component.literal("Steam Supply: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(steamText).withStyle(ChatFormatting.WHITE)));

        String mixtureText = master.getMixtureAmount() + " / " + MAX_MIXTURE_CAPACITY + " Units";
        tooltip.add(Component.literal("  ").append(Component.literal("Crude Mixture: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(mixtureText).withStyle(ChatFormatting.AQUA)));

        return true;
    }
}