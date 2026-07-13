package com.deepu.create_crude.client.gui;

import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.DistillationControllerBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public class DistillationScreen extends AbstractContainerScreen<DistillationContainerMenu> {
    private static final ResourceLocation GUI_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateCrude.MODID, "textures/gui/distillation_gui.png");

    private static final int BAR_X = 20;
    private static final int BAR_Y = 25;
    private static final int BAR_WIDTH = 16;
    private static final int BAR_HEIGHT = 70;
    private static final int BAR_SPACING = 22;

    public DistillationScreen(DistillationContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 256;
        this.imageHeight = 200;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Draw background
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        graphics.blit(GUI_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        DistillationControllerBlockEntity be = menu.getBlockEntity();
        if (be == null) return;

        // Draw input bar (crude oil)
        FluidStack inputFluid = be.getInputTank().getFluid();
        drawFluidBar(graphics, leftPos + BAR_X, topPos + BAR_Y,
                     inputFluid, menu.getInputAmount(), menu.getInputCapacity());

        // Draw output bars
        String[] labels = {"Bitumen", "Diesel", "Kerosene", "Gasoline", "Naphtha", "LPG"};
        for (int i = 0; i < 6; i++) {
            int x = leftPos + BAR_X + (i + 1) * BAR_SPACING + 4;
            FluidStack fluid = be.getOutputTanks()[i].getFluid();
            drawFluidBar(graphics, x, topPos + BAR_Y,
                         fluid, menu.getOutputAmount(i), menu.getOutputCapacity(i));
            // Label under bar
            graphics.drawString(font, labels[i], x, topPos + BAR_Y + BAR_HEIGHT + 4, 0xFFFFFF, false);
        }

        // Heat and active status
        String heat = menu.getHeatLevel() == 0 ? "No Heat" : (menu.getHeatLevel() == 1 ? "Heat: 1x" : "Heat: 2x");
        graphics.drawString(font, heat, leftPos + 10, topPos + 10, 0xFFFFFF, false);
        String active = menu.isActive() ? "§aActive" : "§cInactive";
        graphics.drawString(font, active, leftPos + 100, topPos + 10, 0xFFFFFF, false);
    }

    private void drawFluidBar(GuiGraphics graphics, int x, int y, FluidStack fluid, int amount, int capacity) {
        if (capacity <= 0) return;
        int fillHeight = (int) ((float) amount / capacity * BAR_HEIGHT);
        if (fillHeight <= 0) return;

        int color = 0xFFFFFF;
        if (!fluid.isEmpty()) {
            IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
            color = extensions.getTintColor(fluid);
        }
        // Draw filled rectangle – use the fluid colour
        graphics.fill(x, y + BAR_HEIGHT - fillHeight, x + BAR_WIDTH, y + BAR_HEIGHT, color | 0xFF000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}