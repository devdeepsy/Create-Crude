package com.deepu.create_crude;

import com.deepu.create_crude.client.CreateCrudePartialModels;
import com.deepu.create_crude.client.SteelPumpRenderer;
import com.deepu.create_crude.client.gui.DistillationScreen;
import com.deepu.create_crude.client.renderer.SteelBasinRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = CreateCrude.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class CreateCrudeClient {

    public CreateCrudeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CreateCrudePartialModels.init();
            BlockEntityRenderers.register(CreateCrude.STEEL_PUMP_BE.get(), SteelPumpRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(CreateCrude.DISTILLATION_CONTROLLER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateCrude.STEEL_FLUID_TANK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateCrude.STEEL_BASIN.get(), RenderType.cutout());
        });
    }

    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(SteelBasinRenderer.MIXER_MODEL_LOC);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CreateCrude.DISTILLATION_CONTAINER.get(), DistillationScreen::new);
    }
}