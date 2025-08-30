package com.seosean.zombiesexplorer.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NotificationRenderer {
    private static NotificationRenderer instance;
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    private String currentNotification = "";
    private long notificationStartTime = 0;
    private long notificationDuration = 0;
    
    private NotificationRenderer() {
        // Private constructor for singleton
    }
    
    public static NotificationRenderer getInstance() {
        if (instance == null) {
            instance = new NotificationRenderer();
        }
        return instance;
    }
    
    public void displayNotification(String message, long duration) {
        this.currentNotification = message;
        this.notificationStartTime = System.currentTimeMillis();
        this.notificationDuration = duration;
    }
    
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        
        // Check if notification should be displayed
        if (currentNotification.isEmpty() || notificationStartTime == 0) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - notificationStartTime > notificationDuration) {
            // Notification expired
            currentNotification = "";
            notificationStartTime = 0;
            return;
        }
        
        // Render notification
        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;
        
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();
        
        int textWidth = fr.getStringWidth(currentNotification);
        int x = (screenWidth - textWidth) / 2; // Center horizontally
        int y = screenHeight - 60; // Near bottom but above hotbar
        
        // Draw with shadow for better visibility
        fr.drawStringWithShadow(currentNotification, x, y, 0xFFFFFF);
    }
}