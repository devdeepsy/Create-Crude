package com.deepu.create_crude.client.gui;

import com.deepu.create_crude.block.entity.SeismicDetectorBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DetectorUIScreen extends Screen {
    private final SeismicDetectorBlockEntity detector;
    private boolean isTopView = true;
    private int selectedYLevel = -64;
    
    public DetectorUIScreen(SeismicDetectorBlockEntity detector) {
        super(Component.literal("Seismic Detector"));
        this.detector = detector;
        this.selectedYLevel = detector.getCurrentY();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        Button topViewBtn = Button.builder(Component.literal("Top View"), button -> {
            isTopView = true;
            detector.scanForOilFull();
        }).pos(centerX - 85, centerY - 50).size(80, 20).build();
        
        Button layerViewBtn = Button.builder(Component.literal("Layer View"), button -> {
            isTopView = false;
        }).pos(centerX + 5, centerY - 50).size(80, 20).build();
        
        this.addRenderableWidget(topViewBtn);
        this.addRenderableWidget(layerViewBtn);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xD0101010, 0xD0101010);
        
        int frameWidth = 220;
        int frameHeight = 180;
        int frameX = centerX - (frameWidth / 2);
        int frameY = centerY - (frameHeight / 2);
        
        guiGraphics.fill(frameX - 2, frameY - 2, frameX + frameWidth + 2, frameY + frameHeight + 2, 0xFF444444);
        guiGraphics.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, 0xFF151515);

        guiGraphics.drawCenteredString(font, Component.literal("=== SEISMIC DIAGNOSTICS ==="), centerX, frameY + 12, 0x66FF66);

        // Track tooltips during render passes to overlay them cleanly over text buffers
        List<Component> tooltipToDraw = null;

        if (isTopView) {
            tooltipToDraw = renderTopViewAndGetTooltip(guiGraphics, centerX, centerY, mouseX, mouseY);
        } else {
            renderLayerView(guiGraphics, centerX, centerY);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Render tooltips at the very end so they clear buttons and frames safely
        if (tooltipToDraw != null) {
            guiGraphics.renderComponentTooltip(font, tooltipToDraw, mouseX, mouseY);
        }
        
        RenderSystem.disableBlend();
    }
    
    private List<Component> renderTopViewAndGetTooltip(GuiGraphics guiGraphics, int centerX, int centerY, int mouseX, int mouseY) {
        guiGraphics.drawCenteredString(font, Component.literal("SURFACE RADAR MAP"), centerX, centerY - 15, 0xFFAA00);
        
        int radarSize = 64;
        int radarX = centerX - (radarSize / 2);
        int radarY = centerY + 5;
        
        guiGraphics.fill(radarX - 1, radarY - 1, radarX + radarSize + 1, radarY + radarSize + 1, 0xFF555555);
        guiGraphics.fill(radarX, radarY, radarX + radarSize, radarY + radarSize, 0xFF051C05); 
        
        // Static background grid framing
        guiGraphics.fill(centerX, radarY, centerX + 1, radarY + radarSize, 0xFF0A330A);
        guiGraphics.fill(radarX, radarY + (radarSize / 2), radarX + radarSize, radarY + (radarSize / 2) + 1, 0xFF0A330A);
        
        // FIXED MACHINE POSITION: Draw the missing yellow center-dot representing this scanner unit
        int machineDotX = centerX;
        int machineDotY = radarY + (radarSize / 2);
        guiGraphics.fill(machineDotX, machineDotY, machineDotX + 2, machineDotY + 2, 0xFFFFCC00); // Yellow machine location marker
        
        List<Component> activeTooltip = null;

        // Check machine center hover coordinate bounds
        if (mouseX >= machineDotX && mouseX <= machineDotX + 2 && mouseY >= machineDotY && mouseY <= machineDotY + 2) {
            activeTooltip = new ArrayList<>();
            activeTooltip.add(Component.literal("§6[Seismic Detector Core]§r"));
            activeTooltip.add(Component.literal("Relative Base: X: 0, Z: 0"));
        }

        // Map tracked target locations dynamically
        for (BlockPos pos : detector.getOilPositions()) {
            int relX = pos.getX() - detector.getBlockPos().getX();
            int relZ = pos.getZ() - detector.getBlockPos().getZ();
            
            // Adjusted tracking ratio scales safely matching your block dimensions out from origin lines
            int dotX = centerX + (relX * 2);
            int dotY = (radarY + (radarSize / 2)) + (relZ * 2);
            
            // Check bounding viewport limits
            if (dotX >= radarX && dotX < radarX + radarSize && dotY >= radarY && dotY < radarY + radarSize) {
                guiGraphics.fill(dotX, dotY, dotX + 2, dotY + 2, 0xFFFF2222); // Draw red crude node blip
                
                // HOVER LOOKUP DETECTION: Check if the cursor is touching this specific red dot index
                if (mouseX >= dotX && mouseX <= dotX + 2 && mouseY >= dotY && mouseY <= dotY + 2) {
                    activeTooltip = new ArrayList<>();
                    activeTooltip.add(Component.literal("§c[Crude Oil Deposit Block]§r"));
                    activeTooltip.add(Component.literal("Relative Offset: X: " + relX + ", Z: " + relZ));
                    activeTooltip.add(Component.literal("World Coordinates: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
                }
            }
        }
        
        return activeTooltip;
    }
    
    private void renderLayerView(GuiGraphics guiGraphics, int centerX, int centerY) {
        guiGraphics.drawCenteredString(font, Component.literal("STRATA ANALYZER"), centerX, centerY - 15, 0x33BBFF);
        
        // Always display the true internal synced tracking heights directly
        guiGraphics.drawCenteredString(font, Component.literal("Scan-Depth: Y: " + selectedYLevel), centerX, centerY + 10, 0xFFFFFF);
        
        // Re-read current density metrics live from the cached chunk scanning pools
        int oilAtLevel = detector.getOilCountAtLevel();
        guiGraphics.drawCenteredString(font, Component.literal("Density: " + oilAtLevel + " Nodes"), centerX, centerY + 25, 0x55FF55);
        
        int barWidth = 160;
        int barHeight = 12;
        int barX = centerX - (barWidth / 2);
        int barY = centerY + 45;
        
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF555555);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);
        
        int maxOil = 24; 
        int fillWidth = Math.min(barWidth, (oilAtLevel * barWidth) / maxOil);
        if (fillWidth > 0) {
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00AAFF);
        }
    }
}