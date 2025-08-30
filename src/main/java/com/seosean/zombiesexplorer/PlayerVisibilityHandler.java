package com.seosean.zombiesexplorer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class PlayerVisibilityHandler {

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        // Skip if the feature is disabled or we're not in a game
        if (!ZombiesExplorer.ENABLED || Minecraft.getMinecraft().theWorld == null) {
            return;
        }

        EntityPlayer player = event.entityPlayer;

        // Don't hide yourself or players who are sleeping
        if (player != Minecraft.getMinecraft().thePlayer && !player.isPlayerSleeping()) {
            // REVERSED LOGIC: Hide players when the setting is OFF and within distance
            if (!ZombiesExplorer.HideOtherPlayers && withinDistance(player)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Checks if a player is within a certain distance to be hidden
     * Using the logic from the original mod's DistanceUtils
     */
    private boolean withinDistance(EntityPlayer other) {
        // Using original distance of 2.0D (approximately 1.4 blocks)
        return getDistanceSquared(other) < 2.0D;
    }

    private double getDistanceSquared(EntityPlayer other) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return Math.pow(player.posX - other.posX, 2.0D) + Math.pow(player.posZ - other.posZ, 2.0D);
    }
}
