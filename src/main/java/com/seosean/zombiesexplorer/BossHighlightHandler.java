package com.seosean.zombiesexplorer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BossHighlightHandler {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Color BOSS_COLOR = new Color(255, 0, 0, 255);
    private static final Color BOSS_COLOR_THROUGH_WALL = new Color(255, 0, 0, 80); // More transparent

    // Hitbox expansion values - updated to 0.4, 0.2, 0.4
    private static final double HITBOX_EXPAND_X = 0.3;
    private static final double HITBOX_EXPAND_Y = 0;
    private static final double HITBOX_EXPAND_Z = 0.3;

    // Line thickness for hitbox and tracer - NOW FIXED
    private static final float LINE_THICKNESS = 1.3F;

    // Location detection radius (1 block as requested)
    private static final double DETECTION_RADIUS = 1.5;

    // Popup notification settings
    private static final int POPUP_DURATION_TICKS = 60; // 3 seconds at 20 TPS
    private static final int POPUP_OFFSET_Y = 46; // Pixels above crosshair
    private static final float POPUP_SCALE = 2.0F; // Scale multiplier for popup text

    // Store entities that should be tracked until death
    private final Set<Integer> trackedEntities = new HashSet<>();
    // Store entities in death animation
    private final Set<Integer> dyingEntities = new HashSet<>();
    // Store withers separately for chams only
    private final Set<Integer> witherEntities = new HashSet<>();

    // Location notification system
    private final Map<String, LocationData> bossLocations = new HashMap<>();
    private final Set<Integer> notifiedEntities = new HashSet<>();

    // Popup notification state
    private String currentPopupMessage = null;
    private long popupStartTime = 0;

    // Location data structure
    private static class LocationData {
        final double x, y, z;
        final String name;

        LocationData(double x, double y, double z, String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
        }

        boolean isNearby(Entity entity) {
            double dx = Math.abs(entity.posX - x);
            double dy = Math.abs(entity.posY - y);
            double dz = Math.abs(entity.posZ - z);
            return dx <= DETECTION_RADIUS && dy <= DETECTION_RADIUS && dz <= DETECTION_RADIUS;
        }

        double getDistanceToPlayer(Entity player) {
            double dx = player.posX - x;
            double dy = player.posY - y;
            double dz = player.posZ - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    // Class to store boss spawn information
    private static class BossSpawnInfo {
        final Entity entity;
        final int entityId;
        final LocationData location;
        final double distanceToPlayer;

        BossSpawnInfo(Entity entity, int entityId, LocationData location, double distanceToPlayer) {
            this.entity = entity;
            this.entityId = entityId;
            this.location = location;
            this.distanceToPlayer = distanceToPlayer;
        }
    }

    public BossHighlightHandler() {
        // Initialize boss spawn locations
        initializeBossLocations();
    }

    private void initializeBossLocations() {
        // Original Courtyard locations
        bossLocations.put("co1", new LocationData(19.507, 69, 20.362, "Courtyard Co1"));
        bossLocations.put("co2", new LocationData(30.450, 69, 10.359, "Courtyard Co2"));
        bossLocations.put("co3", new LocationData(24.503, 69, -18.622, "Courtyard Co3"));
        bossLocations.put("co4", new LocationData(12.510, 69, -17.493, "Courtyard Co4"));

        // Mansion locations
        bossLocations.put("m1", new LocationData(0.526, 74, -17.972, "Mansion M1"));
        bossLocations.put("m2", new LocationData(0.464, 74, 18.417, "Mansion M2"));
        bossLocations.put("cryptstair", new LocationData(-12.616, 73, 28.435, "Mansion CryptStair"));

        // Crypts locations
        bossLocations.put("cr1", new LocationData(-3.660, 68, -2.480, "Crypts Cr1"));
        bossLocations.put("cr2", new LocationData(-15.562, 68, 0.523, "Crypts Cr2"));
        bossLocations.put("cr3", new LocationData(-28.553, 68, 20.487, "Crypts Cr3"));

        // Graveyard locations
        bossLocations.put("gy1", new LocationData(-35.389, 68, 31.447, "Graveyard Gy1"));
        bossLocations.put("gy2", new LocationData(-16.548, 68, 50.526, "Graveyard Gy2"));
        bossLocations.put("gy3", new LocationData(-6.549, 68, 33.524, "Graveyard Gy3"));

        // Balcony locations
        bossLocations.put("b1", new LocationData(-32.531, 74, -18.540, "Balcony B1"));
        bossLocations.put("b2", new LocationData(-56.507, 74, 2.473, "Balcony B2"));
        bossLocations.put("b3", new LocationData(-53.605, 72, 12.440, "Balcony B3"));
        bossLocations.put("b4", new LocationData(-41.555, 68, 27.592, "Balcony B4"));

        // Library locations
        bossLocations.put("l1", new LocationData(1.424, 74, -44.581, "Library L1"));
        bossLocations.put("l2", new LocationData(-20.509, 74, -29.563, "Library L2"));
        bossLocations.put("l3", new LocationData(-40.524, 74, -30.513, "Library L3"));
        bossLocations.put("l4", new LocationData(-39.536, 74, -57.547, "Library L4"));
        bossLocations.put("l5", new LocationData(-54.566, 74, -46.343, "Library L5"));
        bossLocations.put("l6", new LocationData(-53.526, 74, -33.543, "Library L6"));

        // Dungeon locations
        bossLocations.put("d1", new LocationData(-10.487, 68, -49.544, "Dungeon D1"));
        bossLocations.put("d2", new LocationData(-28.561, 68, -34.493, "Dungeon D2"));
        bossLocations.put("d3", new LocationData(-9.484, 68, -22.571, "Dungeon D3"));
        bossLocations.put("d4", new LocationData(-9.505, 68, -18.527, "Dungeon D4"));
        bossLocations.put("d5", new LocationData(-36.482, 68, -11.499, "Dungeon D5"));

        // Great Hall locations
        bossLocations.put("gh1", new LocationData(-19.570, 74, -13.471, "Great Hall Gh1"));
        bossLocations.put("gh2", new LocationData(-27.473, 74, 15.492, "Great Hall Gh2"));
        bossLocations.put("gh3", new LocationData(-31.535, 76, 15.505, "Great Hall Gh3"));
        bossLocations.put("gh4", new LocationData(-31.536, 74, 0.483, "Great Hall"));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ZombiesExplorer.ENABLED || !ZombiesExplorer.BossHighlight) {
            return;
        }

        if (mc.theWorld == null || mc.thePlayer == null) {
            // Clear data if world or player is null
            trackedEntities.clear();
            dyingEntities.clear();
            witherEntities.clear();
            notifiedEntities.clear();
            clearPopup();
            return;
        }

        // Update popup timer
        updatePopupTimer();

        // Scan for entities to track
        scanForEntities();

        // Clean up entities that are no longer valid
        cleanupTrackedEntities();
    }

    private void updatePopupTimer() {
        if (currentPopupMessage != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - popupStartTime > 3000) { // 3 seconds in milliseconds
                clearPopup();
            }
        }
    }

    private void clearPopup() {
        currentPopupMessage = null;
        popupStartTime = 0;
    }

    private void cleanupTrackedEntities() {
        // Remove entities that no longer exist from all lists
        Set<Integer> toRemove = new HashSet<>();

        for (Integer entityId : trackedEntities) {
            Entity entity = mc.theWorld.getEntityByID(entityId);
            if (entity == null) {
                // Entity is completely gone from the world
                toRemove.add(entityId);
            } else if (!entity.isEntityAlive()) {
                // Entity is dead but still exists (death animation)
                // Move to dying entities list instead of removing
                dyingEntities.add(entityId);
                toRemove.add(entityId);
            }
        }

        trackedEntities.removeAll(toRemove);

        // Check for entities in dyingEntities that are completely gone
        toRemove.clear();
        for (Integer entityId : dyingEntities) {
            Entity entity = mc.theWorld.getEntityByID(entityId);
            if (entity == null) {
                // Entity is completely gone from the world
                toRemove.add(entityId);
            }
        }

        dyingEntities.removeAll(toRemove);

        // Clean up wither entities
        toRemove.clear();
        for (Integer entityId : witherEntities) {
            Entity entity = mc.theWorld.getEntityByID(entityId);
            if (entity == null) {
                // Entity is completely gone from the world
                toRemove.add(entityId);
            }
        }

        witherEntities.removeAll(toRemove);

        // Clean up notification tracking for dead entities
        notifiedEntities.removeAll(toRemove);
    }

    private void scanForEntities() {
        // Store potential boss spawns for this scan
        List<BossSpawnInfo> newBossSpawns = new ArrayList<>();

        // Scan for mobs that match our criteria
        for (Entity entity : mc.theWorld.loadedEntityList) {
            // Skip if not a living entity, is an armor stand, is a player, or is a golem
            if (!(entity instanceof EntityLivingBase) ||
                    entity instanceof EntityArmorStand ||
                    entity instanceof EntityPlayer ||
                    entity instanceof EntityIronGolem ||
                    !entity.isEntityAlive()) {
                continue;
            }

            int entityId = entity.getEntityId();

            // Handle withers separately - only for chams, no hitboxes
            if (entity instanceof EntityWither) {
                if (!witherEntities.contains(entityId)) {
                    witherEntities.add(entityId);
                }
                continue; // Skip regular boss processing for withers
            }

            // Skip if already tracked
            if (trackedEntities.contains(entityId) || dyingEntities.contains(entityId)) {
                continue;
            }

            // Check health
            EntityLivingBase living = (EntityLivingBase) entity;
            float health = living.getHealth();

            // Skip entities with exactly 300 HP (likely scoreboard)
            if (Math.abs(health - 300.0F) < 0.01F) {
                continue;
            }

            boolean shouldTrack = false;

            // Track entities with 80+ HP
            if (health >= 80.0F) {
                shouldTrack = true;
            }
            // Check for stone sword and chainmail armor
            else if (hasStoneSwordAndChainmail(living)) {
                shouldTrack = true;
            }

            if (shouldTrack) {
                trackedEntities.add(entityId);

                // Check for location notification and add to potential spawns
                checkForLocationNotification(entity, entityId, newBossSpawns);
            }
        }

        // Process boss spawns - closest one gets popup, all get chat sorted by distance
        processBossSpawns(newBossSpawns);
    }

    private void checkForLocationNotification(Entity entity, int entityId, List<BossSpawnInfo> newBossSpawns) {
        // Skip if we already notified for this entity
        if (notifiedEntities.contains(entityId)) {
            return;
        }

        // Check each boss location
        for (LocationData location : bossLocations.values()) {
            if (location.isNearby(entity)) {
                // Calculate distance to player
                double distanceToPlayer = location.getDistanceToPlayer(mc.thePlayer);

                // Add to potential spawns
                newBossSpawns.add(new BossSpawnInfo(entity, entityId, location, distanceToPlayer));

                // Mark this entity as notified
                notifiedEntities.add(entityId);
                break; // Only one location per entity
            }
        }
    }

    private void processBossSpawns(List<BossSpawnInfo> newBossSpawns) {
        if (newBossSpawns.isEmpty()) {
            return;
        }

        // Sort by distance (closest first)
        Collections.sort(newBossSpawns, new Comparator<BossSpawnInfo>() {
            @Override
            public int compare(BossSpawnInfo a, BossSpawnInfo b) {
                return Double.compare(a.distanceToPlayer, b.distanceToPlayer);
            }
        });

        // Send popup notification for the closest boss
        BossSpawnInfo closestSpawn = newBossSpawns.get(0);
        sendPopupNotification(closestSpawn.location.name);

        // Send chat notifications for ALL spawns (including closest) sorted by distance
        sendChatNotifications(newBossSpawns);
    }

    private void sendPopupNotification(String locationName) {
        // Set popup message and timer (for crosshair display)
        currentPopupMessage = "The Boss Spawned In: " + locationName;
        popupStartTime = System.currentTimeMillis();
    }

    private void sendChatNotifications(List<BossSpawnInfo> bossSpawns) {
        if (mc.thePlayer == null) return;

        // Send each boss spawn with the original format (green + red text)
        // Now they're already sorted by distance from processBossSpawns()
        for (BossSpawnInfo spawn : bossSpawns) {
            // Create the message with colors for chat (same format as before)
            ChatComponentText greenText = new ChatComponentText("The Boss Spawned In: ");
            greenText.getChatStyle().setColor(EnumChatFormatting.GREEN);

            ChatComponentText redText = new ChatComponentText(spawn.location.name);
            redText.getChatStyle().setColor(EnumChatFormatting.RED);

            // Combine the texts
            greenText.appendSibling(redText);

            // Send to chat
            mc.thePlayer.addChatMessage(greenText);
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.BossHighlight) {
            return;
        }

        // Only render on the crosshairs layer
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }

        // Only render if we have a popup message
        if (currentPopupMessage == null || mc.thePlayer == null) {
            return;
        }

        FontRenderer fontRenderer = mc.fontRendererObj;
        ScaledResolution scaledResolution = new ScaledResolution(mc);

        int screenWidth = scaledResolution.getScaledWidth();
        int screenHeight = scaledResolution.getScaledHeight();

        // Calculate position (center horizontally, above crosshair)
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int popupY = centerY - POPUP_OFFSET_Y;

        // Split the message into green and red parts
        String greenPart = "The Boss Spawned In: ";
        String redPart = currentPopupMessage.substring(greenPart.length());

        // Apply scaling
        GlStateManager.pushMatrix();
        GlStateManager.scale(POPUP_SCALE, POPUP_SCALE, 1.0F);

        // Adjust positions for scaling
        float scaledCenterX = centerX / POPUP_SCALE;
        float scaledPopupY = popupY / POPUP_SCALE;

        // Calculate text widths for centering (at scaled size)
        int greenWidth = fontRenderer.getStringWidth(greenPart);
        int redWidth = fontRenderer.getStringWidth(redPart);
        int totalWidth = greenWidth + redWidth;

        // Calculate starting X position to center the entire message
        float startX = scaledCenterX - (totalWidth / 2.0F);

        // Enable text rendering with shadow and blending
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Draw green text
        fontRenderer.drawStringWithShadow(greenPart, startX, scaledPopupY, 0x55FF55); // Green color

        // Draw red text right after green text
        fontRenderer.drawStringWithShadow(redPart, startX + greenWidth, scaledPopupY, 0xFF5555); // Red color

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private boolean hasStoneSwordAndChainmail(EntityLivingBase entity) {
        // Check held item for stone sword
        boolean hasStoneSword = false;

        // Check main hand (index 0 in equipment array)
        ItemStack heldItem = entity.getEquipmentInSlot(0);
        if (heldItem != null && heldItem.getItem() == Items.stone_sword) {
            hasStoneSword = true;
        }

        // Check for chainmail armor (any piece)
        boolean hasChainmail = false;
        for (int i = 1; i <= 4; i++) { // 1-4 are armor slots
            ItemStack armorItem = entity.getEquipmentInSlot(i);
            if (armorItem != null) {
                String itemName = armorItem.getItem().getUnlocalizedName().toLowerCase();
                if (itemName.contains("chainmail") || itemName.contains("chain")) {
                    hasChainmail = true;
                    break;
                }
            }
        }

        // Entity must have both stone sword and chainmail
        return hasStoneSword && hasChainmail;
    }

    // Add chams rendering to see entities through walls
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderLivingPre(RenderLivingEvent.Pre<?> event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.BossHighlight || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        Entity entity = event.entity;
        int entityId = entity.getEntityId();

        // Apply chams to tracked boss entities (alive and dying) AND withers
        if ((trackedEntities.contains(entityId) || dyingEntities.contains(entityId) || witherEntities.contains(entityId))) {
            // Enable polygon offset fill - this is what makes the entity visible through walls
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(1.0F, -1100000.0F);
        }
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<?> event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.BossHighlight || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        Entity entity = event.entity;
        int entityId = entity.getEntityId();

        // Apply chams to tracked boss entities (alive and dying) AND withers
        if ((trackedEntities.contains(entityId) || dyingEntities.contains(entityId) || witherEntities.contains(entityId))) {
            // Disable polygon offset fill and reset
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(1.0F, 1100000.0F);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.BossHighlight || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (trackedEntities.isEmpty()) {
            return; // Don't render hitboxes if no alive entities (dying entities don't get hitboxes)
        }

        // Additional rendering safety
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        try {
            // Draw hitboxes and tracers ONLY for ALIVE entities (not dying entities)
            for (Integer entityId : trackedEntities) {
                Entity entity = mc.theWorld.getEntityByID(entityId);
                if (entity != null) {
                    // Draw hitbox (now with see-through capability)
                    drawHitbox(entity, event.partialTicks);
                    // Draw tracer
                    drawTracer(entity, event.partialTicks);
                }
            }

            // NOTE: Dying entities get chams but NO hitboxes or tracers
            // NOTE: Withers get chams but NO hitboxes or tracers

        } catch (Exception e) {
            // Silently catch any rendering errors
        }

        // Restore GL state completely
        GL11.glPopAttrib();
    }

    private void drawHitbox(Entity entity, float partialTicks) {
        GlStateManager.pushMatrix();

        // Camera position
        double cameraX = mc.getRenderManager().viewerPosX;
        double cameraY = mc.getRenderManager().viewerPosY;
        double cameraZ = mc.getRenderManager().viewerPosZ;

        // Setup rendering
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth(); // This allows rendering through walls
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        // FIX START: Enable line smoothing for anti-aliasing
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        // FIX END

        // Set line width using the constant
        GL11.glLineWidth(LINE_THICKNESS);

        // Set color
        GlStateManager.color(BOSS_COLOR.getRed() / 255.0F, BOSS_COLOR.getGreen() / 255.0F,
                BOSS_COLOR.getBlue() / 255.0F, BOSS_COLOR.getAlpha() / 255.0F);

        // Calculate the interpolated position of the entity
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        // Create a properly interpolated bounding box
        double width = entity.width / 2.0;
        double height = entity.height;
        AxisAlignedBB interpolatedBox = new AxisAlignedBB(
                x - width, y, z - width,
                x + width, y + height, z + width
        );

        // Expand the hitbox using our constants
        AxisAlignedBB expandedBox = interpolatedBox
                .expand(HITBOX_EXPAND_X, HITBOX_EXPAND_Y, HITBOX_EXPAND_Z)
                .offset(-cameraX, -cameraY, -cameraZ);

        // Render the hitbox
        RenderGlobal.drawSelectionBoundingBox(expandedBox);

        // Reset GL state
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        // FIX START: Disable line smoothing after we're done
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        // FIX END

        // Reset line width
        GL11.glLineWidth(1.0F);

        GlStateManager.popMatrix();
    }

    private void drawTracer(Entity entity, float partialTicks) {
        // Player's eye position
        Entity player = mc.thePlayer;
        if (player == null) return;

        // Entity must be at least 3 blocks away to draw tracer
        double distance = entity.getDistanceToEntity(player);
        if (distance < 3.0) {
            return;
        }

        double playerX = player.prevPosX + (player.posX - player.prevPosX) * partialTicks;
        double playerY = player.prevPosY + (player.posY - player.prevPosY) * partialTicks + player.getEyeHeight();
        double playerZ = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks;

        // Camera position
        double cameraX = mc.getRenderManager().viewerPosX;
        double cameraY = mc.getRenderManager().viewerPosY;
        double cameraZ = mc.getRenderManager().viewerPosZ;

        // Calculate the interpolated position of the entity
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        // Create a properly interpolated bounding box
        double width = entity.width / 2.0;
        double height = entity.height;
        AxisAlignedBB interpolatedBox = new AxisAlignedBB(
                x - width, y, z - width,
                x + width, y + height, z + width
        );

        // Expand the hitbox using our constants
        AxisAlignedBB expandedBox = interpolatedBox
                .expand(HITBOX_EXPAND_X, HITBOX_EXPAND_Y, HITBOX_EXPAND_Z);

        // Get the bottom center of the EXPANDED hitbox
        double entityX = expandedBox.minX + (expandedBox.maxX - expandedBox.minX) / 2.0;
        double entityY = expandedBox.minY; // Bottom of expanded hitbox
        double entityZ = expandedBox.minZ + (expandedBox.maxZ - expandedBox.minZ) / 2.0;

        // Setup rendering
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // FIX START: Enable line smoothing for anti-aliasing
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        // FIX END

        // Set line width using the constant
        GL11.glLineWidth(LINE_THICKNESS);

        // Calculate color based on distance - fixed version
        // Further = darker, but never below 40% brightness
        float minBrightness = 0.4f;
        float maxBrightness = 1.0f;
        float distanceMax = 60.0f; // Maximum distance to consider (blocks)

        float distanceFactor = (float)Math.max(0, distanceMax - Math.min(distance, distanceMax)) / distanceMax;
        float brightness = minBrightness + (maxBrightness - minBrightness) * distanceFactor;

        // Set color with distance-based adjustment - fixed version to avoid completely dark colors
        GlStateManager.color(
                brightness, // Red stays high since BOSS_COLOR is red
                0.0F,       // Green stays at 0
                0.0F,       // Blue stays at 0
                1.0F        // Full alpha
        );

        // Draw line
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(playerX - cameraX, playerY - cameraY, playerZ - cameraZ);
        GL11.glVertex3d(entityX - cameraX, entityY - cameraY, entityZ - cameraZ);
        GL11.glEnd();

        // Reset GL state
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();

        // FIX START: Disable line smoothing after we're done
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        // FIX END

        // Reset line width
        GL11.glLineWidth(1.0F);

        GlStateManager.popMatrix();
    }
}
