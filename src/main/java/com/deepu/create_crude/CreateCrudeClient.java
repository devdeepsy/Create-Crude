package com.deepu.create_crude;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
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

import com.deepu.create_crude.client.particle.GasCloudParticle;
import com.deepu.create_crude.client.particle.SulfurSmokeParticle;
import com.deepu.create_crude.GasBlock;

@Mod(value = CreateCrude.MODID, dist = Dist.CLIENT)

@EventBusSubscriber(modid = CreateCrude.MODID, value = Dist.CLIENT)
public class CreateCrudeClient {
    public CreateCrudeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CreateCrude.LOGGER.info("HELLO FROM CLIENT SETUP");
        CreateCrude.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, world, pos, tintIndex) -> {
            int radius = state.getValue(GasBlock.RADIUS);
            int alpha = Math.max(0, 255 - radius * 50); // radius 0→255, 4→55
            return (alpha << 24) | 0x00FFFFFF;
        }, CreateCrude.GASBLOCK.get());
    }
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GAS_CLOUD.get(), GasCloudParticle.Provider::new);
    }
    
} 