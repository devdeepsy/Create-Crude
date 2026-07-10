package com.deepu.create_crude;

import java.util.HashMap;
import java.util.Map;

import com.deepu.create_crude.gases.GasRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModGases {

    // 1. Deferred registers – separate from ModFluids, but you could reuse the same ones if you prefer.
    //    For clarity, we keep them separate.
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(BuiltInRegistries.FLUID, CreateCrude.MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, CreateCrude.MODID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, CreateCrude.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, CreateCrude.MODID);
    public static final Map<Block, Fluid> BLOCK_TO_FLUID = new HashMap<>();

    // 2. Define each gas as a FluidEntry (using your existing ModFluids.registerFluid)
    //    Note: we pass the matching particle type (from ModParticles) so that when the fluid is
    //    spawned (e.g., by a pipe leak) it uses the right particles – but you can also pass null.

    public static final ModFluids.FluidEntry METHANE = registerGas(
            "methane",
            0xFFC8E6C9,                 // tint: light greenish (matches your methane particle: 0.78,1.0,0.5 → ~0xFFC8E6C9)
            ModParticles.METHANE_CLOUDS.get()
    );

    public static final ModFluids.FluidEntry ETHANE = registerGas(
            "ethane",
            0xFFFFCC88,                 // warm yellow-orange (matches your ethane particle: 1.0,0.8,0.53)
            ModParticles.ETHANE_CLOUDS.get()
    );

    public static final ModFluids.FluidEntry PROPANE = registerGas(
            "propane",
            0xFF87CEEB,                 // sky blue (matches your propane particle: 0.53,0.8,1.0)
            ModParticles.PROPANE_CLOUDS.get()
    );

    public static final ModFluids.FluidEntry BUTANE = registerGas(
            "butane",
            0xFFFF99FF,                 // light magenta (matches butane particle: 1.0,0.53,1.0)
            ModParticles.BUTANE_CLOUDS.get()
    );

    public static final ModFluids.FluidEntry LPG = registerGas(
            "lpg",
            0xFFE0E0E0,                 // very light grey (LPG particle is white-ish? you used 0.88,1.0,1.0)
            ModParticles.LPG_CLOUDS.get()
    );

    public static final ModFluids.FluidEntry HYDROGEN = registerGas(
            "hydrogen",
            0xFFFFFFFF,                 // white (matches hydrogen: 1.0,1.0,1.0)
            ModParticles.HYDROGEN_CLOUDS.get()
    );

    // 3. Helper method to register a gas fluid with low density/viscosity
    private static ModFluids.FluidEntry registerGas(String name, int tintColor, net.minecraft.core.particles.ParticleOptions particle) {
        // Use your existing ModFluids.registerFluid – it handles all the boilerplate.
        // We set density=10 (very light), viscosity=100 (flows easily).
        // Still/flowing textures: we use water's for now; you can replace with custom gas textures later.
        return ModFluids.registerFluid(
                name,
                10,                         // density (gases are light)
                100,                        // viscosity (flows well)
                tintColor,
                2,                          // levelDecreasePerBlock (standard for fluids)
                10,                         // tickRate (fast)
                2,                          // slopeFindDistance
                ResourceLocation.parse("minecraft:block/water_still"),
                ResourceLocation.parse("minecraft:block/water_flow"),
                false,                      // not flammable (set true if you want)
                0.0f,                       // explosion radius
                particle                    // particle for splash/effect (can be null)
        );
    }

    // 4. Register method – call this from your main mod class
    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    public static void linkBlocksToFluids() {
        GasRegistry.getAll().forEach(entry -> {
            Block block = entry.block.get();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            String path = blockId.getPath();
            // Strip all known suffixes
            String fluidName = path.replace("_block", "")   // <-- ADD THIS
                                .replace("_cloud", "")
                                .replace("_gas", "");
            Fluid fluid = BuiltInRegistries.FLUID.get(
                ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), fluidName)
            );
            if (fluid != null && fluid != BuiltInRegistries.FLUID.get(ResourceLocation.withDefaultNamespace("empty"))) {
                BLOCK_TO_FLUID.put(block, fluid);
            } else {
                CreateCrude.LOGGER.warn("No matching fluid for gas block: " + blockId);
            }
        });
    }
}