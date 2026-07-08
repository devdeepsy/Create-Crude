package com.deepu.create_crude.client.renderer;

import com.deepu.create_crude.block.PumpjackBlock;
import com.deepu.create_crude.block.PumpjackBlockEntity;
import com.deepu.create_crude.CreateCrude;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.joml.Matrix4f;

public class PumpjackRenderer implements BlockEntityRenderer<PumpjackBlockEntity> {
    private static final ResourceLocation ROPE_TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateCrude.MODID, "textures/block/rope.png");

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PumpjackBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        if (!(blockEntity.getBlockState().getBlock() instanceof PumpjackBlock)) {
            return;
        }

        BlockPos holderPos = blockEntity.getHolderPos();
        if (holderPos == null) return;

        Level level = blockEntity.getLevel();
        if (level == null) return;

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(PumpjackBlock.FACING) ? state.getValue(PumpjackBlock.FACING) : Direction.NORTH;
        Direction back = facing.getOpposite();

        float relativeX = (float)(holderPos.getX() - blockEntity.getBlockPos().getX());
        float relativeY = (float)(holderPos.getY() - blockEntity.getBlockPos().getY());
        float relativeZ = (float)(holderPos.getZ() - blockEntity.getBlockPos().getZ());

        boolean isValid = blockEntity.isStructureValid();
        float crankRotationAngle = isValid ? blockEntity.visualRotationAngle : 0f;

        // --- SECTION 1: THE CRANK SPINNING ---
        float crankLocalX = relativeX + (back.getStepX() * 4);
        float crankLocalZ = relativeZ + (back.getStepZ() * 4);
        BlockPos crankWorldPos = blockEntity.getBlockPos().offset((int)crankLocalX, 1, (int)crankLocalZ);

        poseStack.pushPose();
        poseStack.translate(crankLocalX + 0.5f, 1.5f, crankLocalZ + 0.5f);
        if (facing.getAxis() == Direction.Axis.Z) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotation(crankRotationAngle));
        } else {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(crankRotationAngle));
        }
        renderComponent(blockRenderer, level, crankWorldPos, poseStack, bufferSource, packedLight, packedOverlay, 0, 0, 0);
        poseStack.popPose();


        // --- SECTION 2: THE WALKING BEAM ROCKING ---
        float beamRockingAngle = isValid ? ((float)Math.sin(crankRotationAngle * 2.0f) * 0.15f) : 0f;

        poseStack.pushPose();
        poseStack.translate(relativeX + 0.5f, relativeY + 0.5f, relativeZ + 0.5f);
        if (facing.getAxis() == Direction.Axis.Z) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotation(beamRockingAngle));
        } else {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(beamRockingAngle));
        }

        // Center Holder
        renderComponent(blockRenderer, level, holderPos, poseStack, bufferSource, packedLight, packedOverlay, 0, 0, 0);

        // Front rods
        for (int i = 1; i <= 3; i++) {
            renderComponent(blockRenderer, level, holderPos.relative(facing, i), poseStack, bufferSource, packedLight, packedOverlay,
                    facing.getStepX() * i, facing.getStepY() * i, facing.getStepZ() * i);
        }
        // Front head assembly
        BlockPos headPos = holderPos.relative(facing, 4);
        renderComponent(blockRenderer, level, headPos,        poseStack, bufferSource, packedLight, packedOverlay, facing.getStepX() * 4, facing.getStepY() * 4,     facing.getStepZ() * 4);
        renderComponent(blockRenderer, level, headPos.above(), poseStack, bufferSource, packedLight, packedOverlay, facing.getStepX() * 4, facing.getStepY() * 4 + 1, facing.getStepZ() * 4);
        renderComponent(blockRenderer, level, headPos.below(), poseStack, bufferSource, packedLight, packedOverlay, facing.getStepX() * 4, facing.getStepY() * 4 - 1, facing.getStepZ() * 4);

        // Rear rods
        for (int i = 1; i <= 3; i++) {
            renderComponent(blockRenderer, level, holderPos.relative(back, i), poseStack, bufferSource, packedLight, packedOverlay,
                    back.getStepX() * i, back.getStepY() * i, back.getStepZ() * i);
        }
        // Rear counterweight assembly
        BlockPos counterPos = holderPos.relative(back, 4);
        renderComponent(blockRenderer, level, counterPos,        poseStack, bufferSource, packedLight, packedOverlay, back.getStepX() * 4, back.getStepY() * 4,     back.getStepZ() * 4);
        renderComponent(blockRenderer, level, counterPos.above(), poseStack, bufferSource, packedLight, packedOverlay, back.getStepX() * 4, back.getStepY() * 4 + 1, back.getStepZ() * 4);
        renderComponent(blockRenderer, level, counterPos.below(), poseStack, bufferSource, packedLight, packedOverlay, back.getStepX() * 4, back.getStepY() * 4 - 1, back.getStepZ() * 4);

        poseStack.popPose();


        // --- SECTION 3: THE ROPE RENDERERS ---
        if (isValid) {
            float strokeOffset = (float)Math.sin(crankRotationAngle * 2.0f) * 0.5f;
            VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityCutoutNoCull(ROPE_TEXTURE));
            poseStack.pushPose();

            float frontX = relativeX + 0.5f + (facing.getStepX() * 4);
            float frontZ = relativeZ + 0.5f + (facing.getStepZ() * 4);
            float frontTopY = relativeY + strokeOffset;
            float frontBottomY = 0.8f;
            drawRopeSegment(poseStack, vertexBuilder, frontX, frontBottomY, frontZ, frontX, frontTopY, frontZ, packedLight, packedOverlay);

            float rearX = relativeX + 0.5f + (back.getStepX() * 4);
            float rearZ = relativeZ + 0.5f + (back.getStepZ() * 4);
            float rearTopY = relativeY - strokeOffset;
            float rearBottomY = 1.5f;
            drawRopeSegment(poseStack, vertexBuilder, rearX, rearBottomY, rearZ, rearX, rearTopY, rearZ, packedLight, packedOverlay);

            poseStack.popPose();
        }
    }

    /**
     * Renders a structure block at a local offset within the poseStack transform.
     *
     * The key fix: we strip the "formed" property from the block state before rendering.
     * The real block in the world has FORMED=true which sets RenderShape.INVISIBLE —
     * that hides the static block correctly. But we need the visible (FORMED=false) model
     * for the animated ghost copy, so we reset that property here before fetching the model.
     */
    private void renderComponent(BlockRenderDispatcher dispatcher, Level level, BlockPos worldPos,
            PoseStack poseStack, MultiBufferSource bufferSource,
            int light, int overlay,
            float localX, float localY, float localZ) {

        BlockState state = level.getBlockState(worldPos);
        if (state.isAir()) return;

        // Strip "formed=true" so we get the visible model, not the invisible one.
        // We use the property name so this works for ALL structure block types
        // (PumpjackRodBlock, PumpjackCrankBlock, PumpjackCounterweightBlock, etc.)
        // without needing to import or reference any specific block class here.
        for (var prop : state.getProperties()) {
            if (prop.getName().equals("formed") && prop instanceof BooleanProperty bp) {
                state = state.setValue(bp, false);
                break;
            }
        }

        poseStack.pushPose();
        poseStack.translate(localX - 0.5f, localY - 0.5f, localZ - 0.5f);

        net.minecraft.client.resources.model.BakedModel model = dispatcher.getBlockModel(state);
        RenderType renderType = ItemBlockRenderTypes.getMovingBlockRenderType(state);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        dispatcher.getModelRenderer().renderModel(
            poseStack.last(),
            consumer,
            state,
            model,
            1.0F, 1.0F, 1.0F,
            light,
            overlay,
            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
            renderType
        );

        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PumpjackBlockEntity blockEntity) {
        return new net.minecraft.world.phys.AABB(blockEntity.getBlockPos()).inflate(12.0, 10.0, 12.0);
    }

    private void drawRopeSegment(PoseStack poseStack, VertexConsumer builder,
            float x1, float y1, float z1, float x2, float y2, float z2, int light, int overlay) {
        Matrix4f matrix = poseStack.last().pose();
        float thickness = 0.04f;
        float height = Math.abs(y2 - y1);
        float maxV = height;

        addVertex(matrix, builder, x1 - thickness, y1, z1, 0.0f, maxV, light, overlay);
        addVertex(matrix, builder, x2 - thickness, y2, z2, 0.0f, 0.0f, light, overlay);
        addVertex(matrix, builder, x2 + thickness, y2, z2, 1.0f, 0.0f, light, overlay);
        addVertex(matrix, builder, x1 + thickness, y1, z1, 1.0f, maxV, light, overlay);

        addVertex(matrix, builder, x1, y1, z1 - thickness, 0.0f, maxV, light, overlay);
        addVertex(matrix, builder, x2, y2, z2 - thickness, 0.0f, 0.0f, light, overlay);
        addVertex(matrix, builder, x2, y2, z2 + thickness, 1.0f, 0.0f, light, overlay);
        addVertex(matrix, builder, x1, y1, z1 + thickness, 1.0f, maxV, light, overlay);
    }

    private void addVertex(Matrix4f matrix, VertexConsumer builder,
            float x, float y, float z, float u, float v, int light, int overlay) {
        builder.addVertex(matrix, x, y, z)
               .setColor(255, 255, 255, 255)
               .setUv(u, v)
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(0, 1, 0);
    }
}