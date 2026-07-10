package com.deepu.create_crude;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.particles.ColorParticleOption;

import java.util.function.Supplier;

public class SulfurFluids {

    // -----------------------------------------------------------------
    // Custom LiquidBlock for all sulfur‑variant fluids
    // -----------------------------------------------------------------
    public static class SulfurLiquidBlock extends LiquidBlock {
        public SulfurLiquidBlock(FlowingFluid fluid, Properties props) {
            super(fluid, props);
        }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(10) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();

            level.addParticle(ModParticles.SULFUR_SMOKE.get(), x, y, z, 0.0, 0.05, 0.0);
        }
    }
    }

    // -----------------------------------------------------------------
    // Register all sulfur variants
    // -----------------------------------------------------------------

    public static void register() {
    
    }
    // 1. Sulfur Diesel
    public static final ModFluids.FluidEntry SULFUR_DIESEL_ENTRY = registerSulfurFluid(
        "sulfur_diesel",
        2800, 3000, 0xFFC08000,  // tint: yellow‑orange
        2, 20, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow")
    );
    public static final ModFluids.FluidEntry SULFUR_KEROSENE_ENTRY = registerSulfurFluid(
        "sulfur_kerosene",
        2700, 2500, 0xFFADD8E6,  // tint: light blue
        2, 18, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow")
    );
    public static final ModFluids.FluidEntry SULFUR_GASOLINE_ENTRY = registerSulfurFluid(
        "sulfur_gasoline",
        2500, 2200, 0xFFFFD700,  // tint: gold
        2, 12, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow")
    );
    public static final ModFluids.FluidEntry SULFUR_NAPHTHA_ENTRY = registerSulfurFluid(
        "sulfur_naphtha",
        2600, 2400, 0xFFF5F5DC,  // tint: beige
        2, 15, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow")
    );

    // -----------------------------------------------------------------
    // Helper method – uses ModFluids.registerFluid with custom block
    // -----------------------------------------------------------------
    private static ModFluids.FluidEntry registerSulfurFluid(
        String name,
        int density,
        int viscosity,
        int tintColor,
        int levelDecreasePerBlock,
        int tickRate,
        int slopeFindDistance,
        ResourceLocation stillTexture,
        ResourceLocation flowingTexture
    ) {
        return ModFluids.registerFluid(
            name, density, viscosity, tintColor,
            levelDecreasePerBlock, tickRate, slopeFindDistance,
            stillTexture, flowingTexture,
            fluidSupplier -> new SulfurLiquidBlock(fluidSupplier.get(),
                BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                    .noCollission().strength(100.0F).noLootTable())
        );
    }

    // -----------------------------------------------------------------
    // Convenience static fields for quick access (buckets, etc.)
    // -----------------------------------------------------------------
    public static final DeferredHolder<Item, BucketItem> SULFUR_DIESEL_BUCKET = SULFUR_DIESEL_ENTRY.bucket;
    public static final DeferredHolder<Item, BucketItem> SULFUR_KEROSENE_BUCKET = SULFUR_KEROSENE_ENTRY.bucket;
    public static final DeferredHolder<Item, BucketItem> SULFUR_GASOLINE_BUCKET = SULFUR_GASOLINE_ENTRY.bucket;
    public static final DeferredHolder<Item, BucketItem> SULFUR_NAPHTHA_BUCKET = SULFUR_NAPHTHA_ENTRY.bucket;
}