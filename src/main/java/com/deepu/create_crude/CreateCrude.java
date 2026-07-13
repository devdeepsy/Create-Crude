package com.deepu.create_crude;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.deepu.create_crude.block.entity.DistillationCasingBlockEntity;
import com.deepu.create_crude.block.entity.DistillationControllerBlockEntity;
import com.deepu.create_crude.block.entity.SeismicDetectorBlockEntity;
import com.deepu.create_crude.client.renderer.SeismicDetectorRenderer;
import com.deepu.create_crude.gases.GasAwarePipeBlockEntity;
import com.deepu.create_crude.gases.GasBlock;
import com.deepu.create_crude.gases.GasRegistry;
import com.deepu.create_crude.gases.SteelPumpBlockEntity;
import com.deepu.create_crude.block.*;

import net.minecraft.world.level.block.SoundType;
import com.deepu.create_crude.block.*;
import net.minecraft.world.item.Item;
import net.minecraft.core.Direction;

import com.deepu.create_crude.client.SteelPumpRenderer;
import com.deepu.create_crude.client.particle.GasCloudParticle;
import com.deepu.create_crude.client.particle.SulfurSmokeParticle;
import com.deepu.create_crude.client.renderer.PumpjackRenderer;
import com.deepu.create_crude.client.gui.DistillationContainerMenu;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@Mod(CreateCrude.MODID)
public class CreateCrude {

    public static final String MODID = "createcrude";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);

    public static final DeferredBlock<Block> DISTILLATION_CONTROLLER = BLOCKS.register("distillation_controller",
        () -> new DistillationControllerBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(5.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> DISTILLATION_CASING = BLOCKS.register("distillation_casing",
        () -> new DistillationCasingBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(5.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistillationControllerBlockEntity>> DISTILLATION_CONTROLLER_BE =
        BLOCK_ENTITIES.register("distillation_controller", () ->
            BlockEntityType.Builder.of(DistillationControllerBlockEntity::new, DISTILLATION_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistillationCasingBlockEntity>> DISTILLATION_CASING_BE =
        BLOCK_ENTITIES.register("distillation_casing", () ->
            BlockEntityType.Builder.of(DistillationCasingBlockEntity::new, DISTILLATION_CASING.get()).build(null));
    
    public static final DeferredBlock<Block> SEISMIC_DETECTOR = BLOCKS.register("seismic_detector",
        () -> new SeismicDetectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> SEISMIC_DETECTOR_ITEM = ITEMS.registerSimpleBlockItem("seismic_detector", SEISMIC_DETECTOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeismicDetectorBlockEntity>> SEISMIC_DETECTOR_BE =
        BLOCK_ENTITIES.register("seismic_detector", () ->
            BlockEntityType.Builder.of(SeismicDetectorBlockEntity::new, SEISMIC_DETECTOR.get()).build(null));

    public static final DeferredBlock<Block> STEEL_PIPE = BLOCKS.register("steel_pipe",
        () -> new SteelPipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> HIGH_TENSILE_PIPE = BLOCKS.register("high_tensile_pipe",
        () -> new HighTensilePipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 4.0f).sound(SoundType.METAL).noOcclusion()));
        
    public static final DeferredBlock<Block> STEEL_PUMP = BLOCKS.register("steel_pump",
        () -> new SteelPumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> PUMPJACK = BLOCKS.register("pumpjack",
        () -> new PumpjackBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 5.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> PUMPJACK_ROD = BLOCKS.register("pumpjack_rod", 
        ()-> new PumpjackRodBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F,5.0F).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> PUMPJACK_HOLDER = BLOCKS.register("pumpjack_holder", 
        ()-> new PumpjackHolderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()));
    
    public static final DeferredBlock<Block> PUMPJACK_HAMMER = BLOCKS.register("pumpjack_hammer",
        () -> new PumpjackHammerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.5f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> PUMPJACK_COUNTERWEIGHT = BLOCKS.register("pumpjack_counterweight",
        () -> new PumpjackCounterweightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()));
    public static final DeferredBlock<Block> PUMPJACK_HAMMER_HEAD = BLOCKS.register("pumpjack_hammer_head",
        () -> new PumpjackHammerHeadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()));
    public static final DeferredBlock<Block> PUMPJACK_HAMMER_COMPANION = BLOCKS.register("pumpjack_hammer_companion",
        () -> new PumpjackHammerCompanionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()));
    public static final DeferredBlock<Block> PUMPJACK_CRANK = BLOCKS.register("pumpjack_crank", 
        () -> new PumpjackCrankBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()));
    public static final DeferredBlock<Block> STEEL_FLUID_TANK = BLOCKS.register("steel_fluid_tank", 
        () -> new SteelFluidTankBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F).sound(SoundType.NETHERITE_BLOCK)));
    public static final DeferredHolder<Block, Block> ASPHALT_BLOCK = BLOCKS.register("asphalt",
        () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).mapColor(MapColor.METAL).strength(2.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion()));
    
    public static final DeferredItem<Item> STEEL_PIPE_ITEM = ITEMS.register("steel_pipe", () -> new BlockItem(STEEL_PIPE.get(), new Item.Properties()));
    public static final DeferredItem<Item> HIGH_TENSILE_PIPE_ITEM = ITEMS.register("high_tensile_pipe", () -> new BlockItem(HIGH_TENSILE_PIPE.get(), new Item.Properties()));
    public static final DeferredItem<Item> STEEL_PUMP_ITEM = ITEMS.register("steel_pump", () -> new BlockItem(STEEL_PUMP.get(), new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_ITEM = ITEMS.register("pumpjack", () -> new BlockItem(PUMPJACK.get(), new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_HOLDER_ITEM = ITEMS.register("pumpjack_holder",()-> new BlockItem(PUMPJACK_HOLDER.get(),new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_HAMMER_ITEM = ITEMS.register("pumpjack_hammer", () -> new BlockItem(PUMPJACK_HAMMER.get(), new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_COUNTERWEIGHT_ITEM = ITEMS.register("pumpjack_counterweight", () -> new BlockItem(PUMPJACK_COUNTERWEIGHT.get(), new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_HAMMER_HEAD_ITEM = ITEMS.register("pumpjack_hammer_head", () -> new BlockItem(PUMPJACK_HAMMER_HEAD.get(), new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_HAMMER_COMPANION_ITEM = ITEMS.register("pumpjack_hammer_companion", () -> new BlockItem(PUMPJACK_HAMMER_COMPANION.get(), new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_CRANK_ITEM = ITEMS.register("pumpjack_crank", () -> new BlockItem(PUMPJACK_CRANK.get(), new Item.Properties()));
    public static final DeferredItem<Item> STEEL_FLUID_TANK_ITEM = ITEMS.register("steel_fluid_tank", () -> new BlockItem(STEEL_FLUID_TANK.get(), new Item.Properties()));
    public static final DeferredItem<Item> SULFUR_POWDER_ITEM = ITEMS.register("sulfur_powder",() -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> ASPHALT_ITEM = ITEMS.register("asphalt",() -> new BlockItem(ASPHALT_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<Item> DISTILLATION_CONTROLLER_ITEM = ITEMS.register("distillation_controller", () -> new BlockItem(DISTILLATION_CONTROLLER.get(), new Item.Properties()));
    public static final DeferredItem<Item> DISTILLATION_CASING_ITEM = ITEMS.register("distillation_casing", () -> new BlockItem(DISTILLATION_CASING.get(), new Item.Properties()));
    public static final DeferredItem<Item> PUMPJACK_ROD_IRON_ITEM = ITEMS.register("iron_rod",
        () -> new net.minecraft.world.item.BlockItem(PUMPJACK_ROD.get(), new Item.Properties()) {
            @Override
            protected BlockState getPlacementState(net.minecraft.world.item.context.BlockPlaceContext context) {
                BlockState state = super.getPlacementState(context);
                return state != null ? state.setValue(PumpjackRodBlock.MATERIAL, PumpjackRodBlock.RodMaterial.IRON) : null;
            }
        });

    public static final DeferredItem<Item> PUMPJACK_ROD_STEEL_ITEM = ITEMS.register("steel_rod",
        () -> new net.minecraft.world.item.BlockItem(PUMPJACK_ROD.get(), new Item.Properties()) {
            @Override
            protected BlockState getPlacementState(net.minecraft.world.item.context.BlockPlaceContext context) {
                BlockState state = super.getPlacementState(context);
                return state != null ? state.setValue(PumpjackRodBlock.MATERIAL, PumpjackRodBlock.RodMaterial.STEEL) : null;
            }
        });
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpjackBlockEntity>> PUMPJACK_BE = 
        BLOCK_ENTITIES.register("pumpjack", () -> BlockEntityType.Builder.of(PumpjackBlockEntity::new, PUMPJACK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasAwarePipeBlockEntity>> GAS_AWARE_PIPE_BE =
        BLOCK_ENTITIES.register("gas_aware_pipe", () ->
            BlockEntityType.Builder.of(GasAwarePipeBlockEntity::new,
                STEEL_PIPE.get(), HIGH_TENSILE_PIPE.get()).build(null));
    
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteelPumpBlockEntity>> STEEL_PUMP_BE =
        BLOCK_ENTITIES.register("steel_pump", () ->
            BlockEntityType.Builder.of(SteelPumpBlockEntity::new, STEEL_PUMP.get()).build(null)
    );
    public static final DeferredItem<Item> PUMPJACK_ROD_CAST_IRON_ITEM = ITEMS.register("cast_iron_rod",
        () -> new net.minecraft.world.item.BlockItem(PUMPJACK_ROD.get(), new Item.Properties()) {
            @Override
            protected BlockState getPlacementState(net.minecraft.world.item.context.BlockPlaceContext context) {
                BlockState state = super.getPlacementState(context);
                return state != null ? state.setValue(PumpjackRodBlock.MATERIAL, PumpjackRodBlock.RodMaterial.CAST_IRON) : null;
            }
        });
    //Menu 
    public static final DeferredHolder<MenuType<?>, MenuType<DistillationContainerMenu>> DISTILLATION_CONTAINER = MENU_TYPES.register("distillation_controller", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(DistillationContainerMenu::new));
    
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.createcrude"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .displayItems((parameters, output) -> {
                output.accept(ModFluids.CRUDE_OIL_BUCKET.get());
                output.accept(SEISMIC_DETECTOR_ITEM.get());
                output.accept(STEEL_PIPE_ITEM.get());
                output.accept(HIGH_TENSILE_PIPE_ITEM.get());
                output.accept(STEEL_PUMP_ITEM.get());
                output.accept(PUMPJACK_ITEM.get());
                output.accept(PUMPJACK_ROD_IRON_ITEM.get());
                output.accept(PUMPJACK_ROD_STEEL_ITEM.get());
                output.accept(PUMPJACK_ROD_CAST_IRON_ITEM.get());
                output.accept(PUMPJACK_HOLDER_ITEM.get());
                output.accept(PUMPJACK_HAMMER_ITEM.get());
                output.accept(PUMPJACK_COUNTERWEIGHT_ITEM.get());
                output.accept(PUMPJACK_HAMMER_HEAD_ITEM.get());
                output.accept(PUMPJACK_HAMMER_COMPANION_ITEM.get());
                output.accept(PUMPJACK_CRANK_ITEM.get());
                output.accept(STEEL_FLUID_TANK_ITEM.get());
                output.accept(ASPHALT_ITEM.get());
                output.accept(ModFluids.HEAVY_OIL_BUCKET.get());
                output.accept(ModFluids.DIESEL_BUCKET.get());
                output.accept(ModFluids.BITUMEN_BUCKET.get());
                output.accept(ModFluids.LUBRICATING_OIL_BUCKET.get());
                output.accept(SULFUR_POWDER_ITEM.get());
                output.accept(SulfurFluids.SULFUR_DIESEL_BUCKET.get());
                output.accept(ModFluids.KEROSENE_BUCKET.get());
                output.accept(SulfurFluids.SULFUR_KEROSENE_BUCKET.get());
                output.accept(SulfurFluids.SULFUR_GASOLINE_BUCKET.get());
                output.accept(SulfurFluids.SULFUR_NAPHTHA_BUCKET.get());
                output.accept(ModFluids.GASOLINE_BUCKET.get());
                output.accept(ModFluids.NAPHTHA_BUCKET.get());
                GasRegistry.getAll().forEach(entry -> output.accept(entry.item.get()));
                output.accept(DISTILLATION_CONTROLLER_ITEM.get());
            }).build());

    public CreateCrude(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerRenderers);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        ModFluids.register(modEventBus);
        ModParticles.register(modEventBus); 
        modEventBus.addListener(this::registerParticles);
        SulfurFluids.register();
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::onCommonSetup); 
        modEventBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        GasRegistry.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

    }

    public void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SEISMIC_DETECTOR_BE.get(), SeismicDetectorRenderer::new);
        event.registerBlockEntityRenderer(PUMPJACK_BE.get(), PumpjackRenderer::new);
    }
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModFluids.CRUDE_OIL_BUCKET.get());
            event.accept(SEISMIC_DETECTOR_ITEM.get());
            event.accept(ASPHALT_ITEM.get());
        }
    }

    private void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SULFUR_SMOKE.get(), SulfurSmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GAS_CLOUD.get(), GasCloudParticle.Provider::new);
        event.registerSpriteSet(ModParticles.LPG_CLOUDS.get(), spr -> new GasCloudParticle.Provider(spr, 0.88F, 1.0F, 1.0F));
        event.registerSpriteSet(ModParticles.METHANE_CLOUDS.get(), spr -> new GasCloudParticle.Provider(spr, 0.78F, 1.0F, 0.5F));
        event.registerSpriteSet(ModParticles.ETHANE_CLOUDS.get(), spr -> new GasCloudParticle.Provider(spr, 1.0F, 0.8F, 0.53F));
        event.registerSpriteSet(ModParticles.PROPANE_CLOUDS.get(), spr -> new GasCloudParticle.Provider(spr, 0.53F, 0.8F, 1.0F));
        event.registerSpriteSet(ModParticles.BUTANE_CLOUDS.get(), spr -> new GasCloudParticle.Provider(spr, 1.0F, 0.53F, 1.0F));
        event.registerSpriteSet(ModParticles.HYDROGEN_CLOUDS.get(), spr -> new GasCloudParticle.Provider(spr, 1.0F, 1.0F, 1.0F));
        }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
    private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
    // Expose the internal fluid tank capability to the world
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PUMPJACK_BE.get(), (be, side) -> {
            if (side == Direction.UP || side == Direction.DOWN) {
                return null; 
            }
            return be.getInternalTank();
        });
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DISTILLATION_CONTROLLER_BE.get(),
            (be, side) -> be.getInputCapability(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DISTILLATION_CASING_BE.get(),
            (be, side) -> ((DistillationCasingBlockEntity)be).getFluidCapability(side));
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                addBlockToCreateStructure(STEEL_PIPE.get(), "fluid_pipe");
                addBlockToCreateStructure(HIGH_TENSILE_PIPE.get(), "fluid_pipe");
                addBlockToCreateStructure(STEEL_PUMP.get(), "mechanical_pump");
                addBlockToCreateStructure(STEEL_FLUID_TANK.get(), "fluid_tank");
            } catch (Exception e) {
                LOGGER.error("Failed handling network layer reflection attachment: ", e);
            }
        });
    }
    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("pumpjack")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2 (Cheats enabled)
                .then(Commands.literal("test")
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal("This command must be executed by a player."));
                            return 0;
                        }

                        // Raycast 10 blocks out from where the player is looking
                        HitResult hitResult = player.pick(10.0D, 1.0F, false);
                        if (hitResult.getType() == HitResult.Type.BLOCK) {
                            BlockHitResult blockHit = (BlockHitResult) hitResult;
                            BlockPos lookPos = blockHit.getBlockPos();

                            // Safely verify if targeted block entity is our pumpjack
                            if (player.level().getBlockEntity(lookPos) instanceof PumpjackBlockEntity pumpjack) {
                                pumpjack.forceTestPump(player);
                                return 1;
                            }
                        }

                        player.sendSystemMessage(Component.literal("§eYou must be looking directly at a Pumpjack to test it!§r"));
                        return 0;
                    })
                )
        );
    }
    @SuppressWarnings("unchecked")
    private void addBlockToCreateStructure(Block block, String path) {
        try {
            BlockEntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                .get(ResourceLocation.fromNamespaceAndPath("create", path));
            if (type == null) return;

            java.lang.reflect.Field f = BlockEntityType.class.getDeclaredField("validBlocks");
            f.setAccessible(true);
            java.util.Set<Block> oldSet = (java.util.Set<Block>) f.get(type);
            java.util.Set<Block> newSet = new java.util.HashSet<>(oldSet);
            newSet.add(block);
            f.set(type, newSet);
        } catch (Exception e) {
            LOGGER.error("Structural registration failure: ", e);
        }
    }
}