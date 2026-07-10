package com.deepu.create_crude.gases;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.ModParticles;

import java.util.ArrayList;
import java.util.List;

public class GasRegistry {

    // Define standalone registers for your gases
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateCrude.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateCrude.MODID);

    private static final List<GasEntry> ENTRIES = new ArrayList<>();

    public static void register(IEventBus modEventBus) {
        // Register each gas variant explicitly
        registerGas("lpg_block",
                new GasProperties(4, 100, 20, 5, 0, 0xAAE0FFFF),
                ModParticles.LPG_CLOUDS);
        registerGas("methane_block",
                new GasProperties(5, 120, 20, 5, 0, 0xAAC8FF80),
                ModParticles.METHANE_CLOUDS);
        registerGas("ethane_block",
                new GasProperties(4, 100, 20, 5, 0, 0xAAFFCC88),
                ModParticles.ETHANE_CLOUDS);
        registerGas("propane_block",
                new GasProperties(5, 110, 20, 5, 0, 0xAA88CCFF),
                ModParticles.PROPANE_CLOUDS);
        registerGas("butane_block",
                new GasProperties(6, 130, 20, 5, 0, 0xAAFF88FF),
                ModParticles.BUTANE_CLOUDS);
        registerGas("hydrogen_block",
                new GasProperties(8, 150, 20, 5, 0, 0x55FFFFFF),
                ModParticles.HYDROGEN_CLOUDS);

        // Make sure to register the Gas local event buses here!
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private static void registerGas(String name, GasProperties props,
                                    DeferredHolder<ParticleType<?>, SimpleParticleType> particle) {
        // FIX: Route through GasRegistry's local registers instead of CreateCrude
        DeferredBlock<GasBlock> block = BLOCKS.register(name, () -> new GasBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_LIGHT_GRAY)
                        .strength(0.5f)
                        .sound(SoundType.GRAVEL)
                        .noOcclusion()
                        .pushReaction(PushReaction.DESTROY),
                props,
                particle::get
        ));

        DeferredItem<BlockItem> item = ITEMS.register(name,
                () -> new BlockItem(block.get(), new Item.Properties()));

        ENTRIES.add(new GasEntry(block, item, particle, props));
    }

    public static List<GasEntry> getAll() {
        return new ArrayList<>(ENTRIES);
    }

    public static class GasEntry {
        public final DeferredBlock<GasBlock> block;
        public final DeferredItem<BlockItem> item;
        public final DeferredHolder<ParticleType<?>, SimpleParticleType> particle;
        public final GasProperties properties;

        public GasEntry(DeferredBlock<GasBlock> block, DeferredItem<BlockItem> item,
                        DeferredHolder<ParticleType<?>, SimpleParticleType> particle,
                        GasProperties properties) {
            this.block = block;
            this.item = item;
            this.particle = particle;
            this.properties = properties;
        }
    }
}