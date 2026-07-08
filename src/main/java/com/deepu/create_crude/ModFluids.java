package com.deepu.create_crude;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.particles.ParticleTypes;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
public class ModFluids {
    public static final String MOD_ID = CreateCrude.MODID;

    // Registries
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(BuiltInRegistries.FLUID, MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(BuiltInRegistries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);

    // -----------------------------------------------------------------
    // FluidEntry – holds all parts of a fluid registration
    // -----------------------------------------------------------------
     public static class FluidEntry {
        public final DeferredHolder<FluidType, ? extends FluidType> type;
        public final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> flowing;
        public final DeferredHolder<Block, ? extends LiquidBlock> block;
        public final DeferredHolder<Item, BucketItem> bucket;

        public FluidEntry(
            DeferredHolder<FluidType, ? extends FluidType> type,
            DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> source,
            DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> flowing,
            DeferredHolder<Block, ? extends LiquidBlock> block,           // wildcard
            DeferredHolder<Item, BucketItem> bucket
        ) {
            this.type = type;
            this.source = source;
            this.flowing = flowing;
            this.block = block;
            this.bucket = bucket;
        }
    }

    // -----------------------------------------------------------------
    // Factory method – default block (LiquidBlock)
    // -----------------------------------------------------------------
    public static FluidEntry registerFluid(
        String name,
        int density,
        int viscosity,
        int tintColor,
        int levelDecreasePerBlock,
        int tickRate,
        int slopeFindDistance,
        ResourceLocation stillTexture,
        ResourceLocation flowingTexture,
        boolean flammable,
        float explosionRadius,
        ParticleOptions particle
    ) {
        return registerFluid(
            name, density, viscosity, tintColor,
            levelDecreasePerBlock, tickRate, slopeFindDistance,
            stillTexture, flowingTexture,
            // FIX: pass the supplier itself, not fluidSupplier.get()
            fluidSupplier -> new LiquidBlock(fluidSupplier.get(),
                BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                    .noCollission().strength(100.0F).noLootTable())
        );
    }
    // -----------------------------------------------------------------
    // Factory method – custom block (allows any LiquidBlock subclass)
    // -----------------------------------------------------------------
    public static FluidEntry registerFluid(
        String name,
        int density,
        int viscosity,
        int tintColor,
        int levelDecreasePerBlock,
        int tickRate,
        int slopeFindDistance,
        ResourceLocation stillTexture,
        ResourceLocation flowingTexture,
        Function<Supplier<? extends BaseFlowingFluid>, ? extends LiquidBlock> blockFactory
    ) {
        // 1. Register FluidType
        var type = FLUID_TYPES.register(name,
            () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid." + MOD_ID + "." + name)
                .density(density)
                .viscosity(viscosity)
                .supportsBoating(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)) {

                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        @Override
                        public ResourceLocation getStillTexture() { return stillTexture; }
                        @Override
                        public ResourceLocation getFlowingTexture() { return flowingTexture; }
                        @Override
                        public int getTintColor() { return tintColor; }
                    });
                }
            }
        );

        // 2. Holder for properties
        var propHolder = new java.util.concurrent.atomic.AtomicReference<BaseFlowingFluid.Properties>();

        // 3. Register Source and Flowing
        var source = FLUIDS.register(name,
            () -> new BaseFlowingFluid.Source(propHolder.get())
        );
        var flowing = FLUIDS.register(name + "_flowing",
            () -> new BaseFlowingFluid.Flowing(propHolder.get())
        );

        // 4. Register the LiquidBlock using the factory
        var block = BLOCKS.register(name + "_block",
            () -> blockFactory.apply(() -> source.get())
        );

        // 5. Register the BucketItem
        var bucket = ITEMS.register(name + "_bucket",
            () -> new BucketItem(source.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
        );

        // 6. Build properties
        var props = new BaseFlowingFluid.Properties(type, source, flowing)
            .bucket(bucket)
            .block(block)
            .slopeFindDistance(slopeFindDistance)
            .levelDecreasePerBlock(levelDecreasePerBlock)
            .tickRate(tickRate);
        propHolder.set(props);

        return new FluidEntry(type, source, flowing, block, bucket);
    }

    // -----------------------------------------------------------------
    // Register the standard fluids (using default block)
    // -----------------------------------------------------------------
    public static final FluidEntry CRUDE_OIL_ENTRY = registerFluid(
        "crude_oil", 3000, 5000, 0xFF221E1B, 2, 20, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow"),false,0.0f,ParticleTypes.SMOKE
    );
    public static final FluidEntry HEAVY_OIL_ENTRY = registerFluid(
        "heavy_oil", 4000, 9500, 0xFF111111, 3, 35, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow"),
        false,0.0f,ParticleTypes.SMOKE
    );
    public static final FluidEntry DIESEL_ENTRY = registerFluid(
        "diesel", 2800, 3000, 0xFFC08000, 2, 20, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow"),
        false,0.0f,ParticleTypes.SMOKE
    );
    public static final FluidEntry BITUMEN_ENTRY = registerFluid(
        "bitumen", 4500, 8000, 0xFF000000, 2, 40, 1,
        ResourceLocation.parse("minecraft:block/lava_still"),
        ResourceLocation.parse("minecraft:block/lava_flow"),
        false,0.0f,ParticleTypes.SMOKE
    );
    public static final FluidEntry LUBRICATING_OIL_ENTRY = registerFluid(
        "lubricating_oil", 2500, 2000, 0xFFD4A017, 2, 15, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow"),
        false,0.0f,ParticleTypes.SMOKE
    );
    public static final FluidEntry KEROSENE_ENTRY = registerFluid(
        "kerosene", 2700, 2500, 0xFFADD8E6, 2, 18, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow"),
        false,0.0f,ParticleTypes.SMOKE
    );
    public static final FluidEntry GASOLINE_ENTRY = registerFluid(
        "gasoline", 2500, 2200, 0xFFFFD700, 2, 12, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow"),
        false,0.0f,ParticleTypes.SMOKE
    );
    public static final FluidEntry NAPHTHA_ENTRY = registerFluid(
        "naphtha", 2600, 2400, 0xFFF5F5DC, 2, 15, 2,
        ResourceLocation.parse("minecraft:block/water_still"),
        ResourceLocation.parse("minecraft:block/water_flow"),
        false,0.0f,ParticleTypes.SMOKE
    );



    // -----------------------------------------------------------------
    // Backward‑compatible static fields (now using wildcard for block)
    // -----------------------------------------------------------------
    public static final DeferredHolder<FluidType, ? extends FluidType> CRUDE_OIL_TYPE = CRUDE_OIL_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> CRUDE_OIL_SOURCE = CRUDE_OIL_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> CRUDE_OIL_FLOWING = CRUDE_OIL_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> CRUDE_OIL_BLOCK = CRUDE_OIL_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> CRUDE_OIL_BUCKET = CRUDE_OIL_ENTRY.bucket;

    public static final DeferredHolder<FluidType, ? extends FluidType> HEAVY_OIL_TYPE = HEAVY_OIL_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> HEAVY_OIL_SOURCE = HEAVY_OIL_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> HEAVY_OIL_FLOWING = HEAVY_OIL_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> HEAVY_OIL_BLOCK = HEAVY_OIL_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> HEAVY_OIL_BUCKET = HEAVY_OIL_ENTRY.bucket;

    public static final DeferredHolder<FluidType, ? extends FluidType> DIESEL_TYPE = DIESEL_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> DIESEL_SOURCE = DIESEL_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> DIESEL_FLOWING = DIESEL_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> DIESEL_BLOCK = DIESEL_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> DIESEL_BUCKET = DIESEL_ENTRY.bucket;

    public static final DeferredHolder<FluidType, ? extends FluidType> BITUMEN_TYPE = BITUMEN_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> BITUMEN_SOURCE = BITUMEN_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> BITUMEN_FLOWING = BITUMEN_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> BITUMEN_BLOCK = BITUMEN_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> BITUMEN_BUCKET = BITUMEN_ENTRY.bucket;

    public static final DeferredHolder<FluidType, ? extends FluidType> LUBRICATING_OIL_TYPE = LUBRICATING_OIL_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> LUBRICATING_OIL_SOURCE = LUBRICATING_OIL_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> LUBRICATING_OIL_FLOWING = LUBRICATING_OIL_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> LUBRICATING_OIL_BLOCK = LUBRICATING_OIL_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> LUBRICATING_OIL_BUCKET = LUBRICATING_OIL_ENTRY.bucket;


    public static final DeferredHolder<FluidType, ? extends FluidType> KEROSENE_TYPE = KEROSENE_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> KEROSENE_SOURCE = KEROSENE_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> KEROSENE_FLOWING = KEROSENE_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> KEROSENE_BLOCK = KEROSENE_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> KEROSENE_BUCKET = KEROSENE_ENTRY.bucket;

    public static final DeferredHolder<FluidType, ? extends FluidType> GASOLINE_TYPE = GASOLINE_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> GASOLINE_SOURCE = GASOLINE_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> GASOLINE_FLOWING = GASOLINE_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> GASOLINE_BLOCK = GASOLINE_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> GASOLINE_BUCKET = GASOLINE_ENTRY.bucket;

    public static final DeferredHolder<FluidType, ? extends FluidType> NAPHTHA_TYPE = NAPHTHA_ENTRY.type;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Source> NAPHTHA_SOURCE = NAPHTHA_ENTRY.source;
    public static final DeferredHolder<Fluid, ? extends BaseFlowingFluid.Flowing> NAPHTHA_FLOWING = NAPHTHA_ENTRY.flowing;
    public static final DeferredHolder<Block, ? extends LiquidBlock> NAPHTHA_BLOCK = NAPHTHA_ENTRY.block;
    public static final DeferredHolder<Item, BucketItem> NAPHTHA_BUCKET = NAPHTHA_ENTRY.bucket;

    // -----------------------------------------------------------------
    // Registration method
    // -----------------------------------------------------------------
    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}