package com.deepu.create_crude.client.gui;

import com.deepu.create_crude.CreateCrude;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateCrude.MODID, "textures/gui/reactor_gui.png");

    public ReactorScreen(ReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Progress Arrow animation
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int scaledWidth = (progress * 24) / maxProgress;
            graphics.blit(TEXTURE, x + 79, y + 34, 176, 0, scaledWidth, 17);
        }

        // Vertical Gas Fluid Bars
        int ethyleneScaled = (menu.getEthylene() * 50) / 10000;
        graphics.blit(TEXTURE, x + 20, y + 68 - ethyleneScaled, 176, 17, 12, ethyleneScaled);

        int propyleneScaled = (menu.getPropylene() * 50) / 10000;
        graphics.blit(TEXTURE, x + 36, y + 68 - propyleneScaled, 188, 17, 12, propyleneScaled);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Hover Tooltips for fluid meters
        if (mouseX >= x + 20 && mouseX <= x + 32 && mouseY >= y + 18 && mouseY <= y + 68) {
            graphics.renderTooltip(this.font, Component.literal("Ethylene: " + menu.getEthylene() + " / 10000 mB"), mouseX, mouseY);
        }
        if (mouseX >= x + 36 && mouseX <= x + 48 && mouseY >= y + 18 && mouseY <= y + 68) {
            graphics.renderTooltip(this.font, Component.literal("Propylene: " + menu.getPropylene() + " / 10000 mB"), mouseX, mouseY);
        }
    }
}