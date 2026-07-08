package com.deepu.create_crude.client;

import com.deepu.create_crude.block.PumpjackRodBlock;
import com.deepu.create_crude.client.renderer.PumpjackRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.tank.FluidTankCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import com.deepu.create_crude.CreateCrude;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.resources.ResourceLocation;
import com.deepu.create_crude.ModFluids;

@EventBusSubscriber(modid = CreateCrude.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof PumpjackRodBlock rodBlock)) return;

        BlockHitResult blockHit = event.getTarget();
        BlockPos targetedPos = blockHit.getBlockPos();
        BlockState targetedState = mc.level.getBlockState(targetedPos);

        if (targetedState.getBlock() instanceof PumpjackRodBlock) {
            
            Direction.Axis parentAxis = targetedState.getValue(PumpjackRodBlock.AXIS);
            BlockPos placementPos = targetedPos.relative(blockHit.getDirection());
            
            if (mc.level.getBlockState(placementPos).canBeReplaced()) {
                PumpjackRodBlock.RodMaterial heldMaterial = rodBlock.getMaterialFromItem(heldItem);

                BlockState ghostState = rodBlock.defaultBlockState()
                        .setValue(PumpjackRodBlock.AXIS, parentAxis)
                        .setValue(PumpjackRodBlock.MATERIAL, heldMaterial);


                PoseStack poseStack = event.getPoseStack();
                Vec3 camera = event.getCamera().getPosition();
                
                poseStack.pushPose();
                poseStack.translate(placementPos.getX() - camera.x, placementPos.getY() - camera.y, placementPos.getZ() - camera.z);

                BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
                
                dispatcher.renderSingleBlock(
                    ghostState, 
                    poseStack, 
                    mc.renderBuffers().bufferSource(),
                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                    GhostRenderType.GHOST_BLOCK
            );

                poseStack.popPose();
            }
        }
    }

    // @SubscribeEvent
    // public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    //     // Binds your custom drawing loop directly to the block entity container type
    //     event.registerBlockEntityRenderer(CreateCrude.PUMPJACK_BE.get(), PumpjackRenderer::new);
    // }
    // @SubscribeEvent
    // public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
    //     event.registerFluidType(new IClientFluidTypeExtensions() {
    //         private static final ResourceLocation STILL =
    //             ResourceLocation.withDefaultNamespace("block/water_still");
    //         private static final ResourceLocation FLOW =
    //             ResourceLocation.withDefaultNamespace("block/water_flow");

    //         @Override
    //         public ResourceLocation getStillTexture() { return STILL; }

    //         @Override
    //         public ResourceLocation getFlowingTexture() { return FLOW; }

    //         @Override
    //         public int getTintColor() { return 0xFF1A1A1A; }
    //     }, ModFluids.CRUDE_OIL_TYPE.get());
    // }
}