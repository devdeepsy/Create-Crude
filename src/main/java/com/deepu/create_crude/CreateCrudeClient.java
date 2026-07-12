package com.deepu.create_crude;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterNamedRenderTypesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.client.color.item.ItemColor;
import com.deepu.create_crude.CreateCrude;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import com.deepu.create_crude.client.CreateCrudePartialModels;
import com.deepu.create_crude.client.SteelPumpRenderer;
import com.deepu.create_crude.client.particle.GasCloudParticle;
import com.deepu.create_crude.client.particle.SulfurSmokeParticle;
import com.deepu.create_crude.gases.GasBlock;
import com.deepu.create_crude.gases.GasRegistry;
import com.deepu.create_crude.gases.GasRegistry.GasEntry;
import net.minecraft.world.level.block.Block;
import com.deepu.create_crude.client.SteelPumpRenderer;
@EventBusSubscriber(modid = CreateCrude.MODID, value = Dist.CLIENT)
public class CreateCrudeClient {
    public CreateCrudeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CreateCrude.LOGGER.info("HELLO FROM CLIENT SETUP");
        CreateCrude.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        event.enqueueWork(() -> {
        CreateCrudePartialModels.init();
        BlockEntityRenderers.register(CreateCrude.STEEL_PUMP_BE.get(), SteelPumpRenderer::new);
    });
    }
    // @SubscribeEvent
    // public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
    //         // Keep old generic gas block if desired
    //         event.register((state, world, pos, tintIndex) -> {
    //             int radius = state.getValue(GasBlock.RADIUS);
    //             int alpha = Math.max(0, 255 - radius * 50);
    //             return (alpha << 24) | 0x00FFFFFF;
    //         }, GasRegistry.getAll().stream().map(entry -> entry.block.get()).toArray(Block[]::new));

    //         // Register each gas block with its own tint
    //         GasRegistry.getAll().forEach(entry -> {
    //             event.register((state, world, pos, tintIndex) -> {
    //                 if (state.getBlock() instanceof GasBlock gasBlock) {
    //                     int radius = state.getValue(GasBlock.RADIUS);
    //                     int maxRadius = gasBlock.getProperties().maxRadius();
    //                     int alpha = (int) (255 * (1 - (float) radius / maxRadius));
    //                     alpha = Math.max(0, Math.min(255, alpha));
    //                     int tint = gasBlock.getProperties().tintColor(); // 0xFFRRGGBB
    //                     return (alpha << 24) | (tint & 0x00FFFFFF);
    //                 }
    //                 return 0xFFFFFFFF;
    //             }, entry.block.get());
    //         });
        
    //     }
    
} 