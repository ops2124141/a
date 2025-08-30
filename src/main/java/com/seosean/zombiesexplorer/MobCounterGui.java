package com.seosean.zombiesexplorer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Mouse;

public class MobCounterGui {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    // Position and size settings
    private int posX = 10; // Default position
    private int posY = 10;
    private int width = 120; // Default size
    private int height = 40;
    
    // Minimum size constraints
    private static final int MIN_WIDTH = 80;
    private static final int MIN_HEIGHT = 25;
    
    // Maximum size constraints
    private static final int MAX_WIDTH = 300;
    private static final int MAX_HEIGHT = 100;
    
    // Dragging and resizing state
    private boolean isDragging = false;
    private boolean isResizing = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int resizeStartX = 0;
    private int resizeStartY = 0;
    private int initialWidth = 0;
    private int initialHeight = 0;
    
    // Reference to mob counter handler
    private MobCounterHandler mobCounterHandler;
    
    // Colors
    private static final int COLOR_GREEN = 0xFF00FF00; // Green when mobs present
    private static final int COLOR_RED = 0xFFFF0000;   // Red when no mobs
    private static final int COLOR_BACKGROUND = 0x90000000; // Semi-transparent black (more opaque)
    private static final int COLOR_BORDER = 0xFF808080;     // Gray border
    private static final int COLOR_RESIZE_HANDLE = 0xFFFFFFFF; // White resize handle
    private static final int COLOR_WAVE_TEXT = 0xFFFFFF99;     // Light yellow for wave text
    
    public MobCounterGui(MobCounterHandler handler) {
        this.mobCounterHandler = handler;
        loadPosition(); // Load saved position from config
    }
    
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.MobCounter || event.type != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        
        if (mc.theWorld == null || mc.thePlayer == null || mc.gameSettings.showDebugInfo) {
            return;
        }
        
        renderMobCounter();
        
        // Optional: Render tooltip when hovering
        if (isMouseOverCounter(getMouseX(), getMouseY())) {
            renderTooltip();
        }
    }
    
    private int getMouseX() {
        ScaledResolution sr = new ScaledResolution(mc);
        return Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
    }
    
    private int getMouseY() {
        ScaledResolution sr = new ScaledResolution(mc);
        return sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
    }
    
    private void renderTooltip() {
        if (isDragging || isResizing) return; // Don't show tooltip while interacting
        
        FontRenderer fr = mc.fontRendererObj;
        String tooltip = "Drag to move • Resize from corner";
        int tooltipWidth = fr.getStringWidth(tooltip);
        
        int tooltipX = posX + width + 5; // To the right of the counter
        int tooltipY = posY;
        
        // Draw tooltip background
        drawRect(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth + 2, tooltipY + 10, 0xC0000000);
        
        // Draw tooltip text
        fr.drawStringWithShadow(tooltip, tooltipX, tooltipY, 0xFFFFFFFF);
    }
    
    private void renderMobCounter() {
        ScaledResolution sr = new ScaledResolution(mc);
        
        // Ensure position is within screen bounds
        ensureWithinBounds(sr);
        
        // Get mob count and determine color
        int mobCount = mobCounterHandler.getCurrentMobCount();
        boolean hasMobs = mobCounterHandler.hasMobs();
        int textColor = hasMobs ? COLOR_GREEN : COLOR_RED;
        
        // Prepare display text
        String countText = String.valueOf(mobCount);
        String waveText = "Wave " + mobCounterHandler.getCurrentWave();
        
        FontRenderer fr = mc.fontRendererObj;
        
        // Calculate text dimensions
        int countTextWidth = fr.getStringWidth(countText);
        int waveTextWidth = fr.getStringWidth(waveText);
        int maxTextWidth = Math.max(countTextWidth, waveTextWidth);
        
        // Adjust width to fit content if needed
        int minRequiredWidth = maxTextWidth + 20; // 10px padding on each side
        if (width < minRequiredWidth) {
            width = Math.min(minRequiredWidth, MAX_WIDTH);
        }
        
        // Draw background
        drawRect(posX, posY, posX + width, posY + height, COLOR_BACKGROUND);
        
        // Draw border
        drawHollowRect(posX, posY, posX + width, posY + height, COLOR_BORDER);
        
        // Draw resize handle (small square in bottom-right corner)
        int handleSize = 8;
        drawRect(posX + width - handleSize, posY + height - handleSize, 
                posX + width, posY + height, COLOR_RESIZE_HANDLE);
        
        // Draw text centered with better formatting
        int textY = posY + 5;
        int countTextX = posX + (width - countTextWidth) / 2;
        int waveTextX = posX + (width - waveTextWidth) / 2;
        
        // Draw mob count with appropriate color and larger font effect
        fr.drawStringWithShadow(countText, countTextX, textY, textColor);
        
        // Draw wave text in smaller, lighter color
        fr.drawStringWithShadow(waveText, waveTextX, textY + 12, COLOR_WAVE_TEXT);
        
        // Add visual indicator when dragging or resizing
        if (isDragging || isResizing) {
            drawHollowRect(posX - 1, posY - 1, posX + width + 1, posY + height + 1, 0xFFFFFF00); // Yellow highlight
        }
    }
    
    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.MobCounter || mc.currentScreen != null) {
            return;
        }
        
        handleMouseInput();
    }
    
    private void handleMouseInput() {
        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = getMouseX();
        int mouseY = getMouseY();
        
        boolean leftButton = Mouse.isButtonDown(0);
        
        if (leftButton) {
            if (!isDragging && !isResizing) {
                // Check if mouse is over resize handle
                int handleSize = 8;
                if (isMouseOverResizeHandle(mouseX, mouseY, handleSize)) {
                    // Start resizing
                    isResizing = true;
                    resizeStartX = mouseX;
                    resizeStartY = mouseY;
                    initialWidth = width;
                    initialHeight = height;
                } else if (isMouseOverCounter(mouseX, mouseY)) {
                    // Start dragging
                    isDragging = true;
                    dragStartX = mouseX - posX;
                    dragStartY = mouseY - posY;
                }
            }
            
            if (isDragging) {
                // Update position with bounds checking
                int newX = mouseX - dragStartX;
                int newY = mouseY - dragStartY;
                
                // Ensure we don't drag outside screen bounds
                posX = Math.max(0, Math.min(newX, sr.getScaledWidth() - width));
                posY = Math.max(0, Math.min(newY, sr.getScaledHeight() - height));
            } else if (isResizing) {
                // Update size with constraints
                int deltaX = mouseX - resizeStartX;
                int deltaY = mouseY - resizeStartY;
                
                int newWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, initialWidth + deltaX));
                int newHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, initialHeight + deltaY));
                
                // Ensure resizing doesn't push counter off screen
                if (posX + newWidth <= sr.getScaledWidth() && posY + newHeight <= sr.getScaledHeight()) {
                    width = newWidth;
                    height = newHeight;
                }
            }
        } else {
            // Release mouse - save position if anything changed
            if (isDragging || isResizing) {
                savePosition(); // Save position/size to config
            }
            isDragging = false;
            isResizing = false;
        }
    }
    
    private boolean isMouseOverCounter(int mouseX, int mouseY) {
        return mouseX >= posX && mouseX <= posX + width && 
               mouseY >= posY && mouseY <= posY + height;
    }
    
    private boolean isMouseOverResizeHandle(int mouseX, int mouseY, int handleSize) {
        return mouseX >= posX + width - handleSize && mouseX <= posX + width &&
               mouseY >= posY + height - handleSize && mouseY <= posY + height;
    }
    
    private void ensureWithinBounds(ScaledResolution sr) {
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();
        
        // Ensure position is within screen bounds
        posX = Math.max(0, Math.min(posX, screenWidth - width));
        posY = Math.max(0, Math.min(posY, screenHeight - height));
    }
    
    private void loadPosition() {
        // Load position and size from ZombiesExplorer config
        ZombiesExplorer instance = ZombiesExplorer.getInstance();
        if (instance != null && instance.getConfig() != null) {
            posX = instance.getConfig().getInt("mobCounterX", "gui", 10, 0, 10000, "Mob counter X position");
            posY = instance.getConfig().getInt("mobCounterY", "gui", 10, 0, 10000, "Mob counter Y position");
            width = instance.getConfig().getInt("mobCounterWidth", "gui", 120, MIN_WIDTH, MAX_WIDTH, "Mob counter width");
            height = instance.getConfig().getInt("mobCounterHeight", "gui", 40, MIN_HEIGHT, MAX_HEIGHT, "Mob counter height");
        }
    }
    
    private void savePosition() {
        // Save position and size to ZombiesExplorer config
        ZombiesExplorer instance = ZombiesExplorer.getInstance();
        if (instance != null && instance.getConfig() != null) {
            instance.getConfig().get("gui", "mobCounterX", 10).set(posX);
            instance.getConfig().get("gui", "mobCounterY", 10).set(posY);
            instance.getConfig().get("gui", "mobCounterWidth", 120).set(width);
            instance.getConfig().get("gui", "mobCounterHeight", 40).set(height);
            instance.getConfig().save();
        }
    }
    
    // Helper method to draw filled rectangle
    private void drawRect(int left, int top, int right, int bottom, int color) {
        if (left < right) {
            int temp = left;
            left = right;
            right = temp;
        }
        
        if (top < bottom) {
            int temp = top;
            top = bottom;
            bottom = temp;
        }
        
        float alpha = (float)(color >> 24 & 255) / 255.0F;
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;
        
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableAlpha();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.GlStateManager.color(red, green, blue, alpha);
        
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos((double)right, (double)top, 0.0D).endVertex();
        worldrenderer.pos((double)left, (double)top, 0.0D).endVertex();
        worldrenderer.pos((double)left, (double)bottom, 0.0D).endVertex();
        worldrenderer.pos((double)right, (double)bottom, 0.0D).endVertex();
        tessellator.draw();
        
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
    }
    
    // Helper method to draw hollow rectangle (border)
    private void drawHollowRect(int left, int top, int right, int bottom, int color) {
        drawRect(left, top, right, top + 1, color); // Top
        drawRect(left, bottom - 1, right, bottom, color); // Bottom
        drawRect(left, top, left + 1, bottom, color); // Left
        drawRect(right - 1, top, right, bottom, color); // Right
    }
}