package com.seosean.zombiesexplorer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MobHighlightHandler {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Color constants
    private static final Color WAVE_1_COLOR = new Color(0, 255, 0, 255); // Green for round 1 / early spawns
    private static final Color WAVE_2_PLUS_COLOR = new Color(255, 255, 0, 255); // Yellow for round 2+ / later spawns
    private static final Color MOB_COLOR = new Color(255, 255, 255, 255); // Default white

    // Using exact same hitbox expansion values from BossHighlightHandler
    private static final double HITBOX_EXPAND_X = 0.3;
    private static final double HITBOX_EXPAND_Y = 0;
    private static final double HITBOX_EXPAND_Z = 0.3;

    // Line thickness - SAME AS BossHighlightHandler
    private static final float LINE_THICKNESS = 1.3F; // You can now use 1.5F or other values without a jump

    // Store entities that should be tracked
    private final Set<Integer> trackedEntities = new HashSet<>();
    // Store entities in death animation
    private final Set<Integer> dyingEntities = new HashSet<>();

    // Round detection and timing
    private int currentRound = 1; // Start with round 1
    private long roundStartTime = 0;
    private long lastTitleCheck = 0;
    private long gameStartTime = 0; // Track when the game starts
    private boolean round1Detected = false; // Flag to track if we've seen round 1
    private static final long GREEN_PERIOD_MS = 12000; // 12 seconds for green hitboxes
    private static final Pattern ROUND_PATTERN = Pattern.compile("round\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    // Store entities spawned during green period
    private final Set<Integer> greenPeriodEntities = new HashSet<>();

    // Reflection fields for accessing title text
    private Field titleField;
    private Field subtitleField;
    private Field titleTimerField;
    private boolean reflectionInitialized = false;

    public MobHighlightHandler() {
        initReflection();
    }

    private void initReflection() {
        try {
            // Try to get the fields for title display
            // These field names might need adjustment based on your MC version/mappings
            titleField = ReflectionHelper.findField(GuiIngame.class, "displayedTitle", "field_175201_x");
            subtitleField = ReflectionHelper.findField(GuiIngame.class, "displayedSubTitle", "field_175200_y");
            titleTimerField = ReflectionHelper.findField(GuiIngame.class, "titleTimer", "field_175199_z");

            titleField.setAccessible(true);
            subtitleField.setAccessible(true);
            titleTimerField.setAccessible(true);

            reflectionInitialized = true;
            System.out.println("[ZombiesExplorer] Title reflection initialized successfully");
        } catch (Exception e) {
            System.out.println("[ZombiesExplorer] Failed to initialize title reflection: " + e.getMessage());
            reflectionInitialized = false;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ZombiesExplorer.ENABLED) {
            return;
        }

        if (mc.theWorld == null || mc.thePlayer == null) {
            trackedEntities.clear();
            dyingEntities.clear();
            greenPeriodEntities.clear();
            // Reset round detection when leaving world
            currentRound = 1;
            round1Detected = false;
            gameStartTime = 0;
            return;
        }

        // Check for title text every few ticks
        if (System.currentTimeMillis() - lastTitleCheck > 100) { // Check every 0.1 seconds (more frequent)
            checkForRoundTitle();
            lastTitleCheck = System.currentTimeMillis();
        }

        // Auto-detect Round 1 if no round has been detected and we have mobs spawning
        if (!round1Detected && gameStartTime == 0 && !trackedEntities.isEmpty()) {
            // If we see mobs but haven't detected any round yet, assume it's round 1
            gameStartTime = System.currentTimeMillis();
            setCurrentRound(1);
            System.out.println("[ZombiesExplorer] Auto-detected Round 1 based on mob presence");
        }

        // Only scan if at least one feature is enabled
        if (ZombiesExplorer.MobChams || ZombiesExplorer.MobESP) {
            scanForMobs();
            cleanupTrackedEntities();
        } else {
            trackedEntities.clear();
            dyingEntities.clear();
            greenPeriodEntities.clear();
        }
    }

    private void checkForRoundTitle() {
        if (!reflectionInitialized || mc.ingameGUI == null) return;

        try {
            String title = (String) titleField.get(mc.ingameGUI);
            String subtitle = (String) subtitleField.get(mc.ingameGUI);
            Integer titleTimer = (Integer) titleTimerField.get(mc.ingameGUI);

            // Only check if there's currently a title being displayed
            if (titleTimer != null && titleTimer > 0) {
                // Check both title and subtitle for round information
                String textToCheck = "";
                if (title != null) textToCheck += title + " ";
                if (subtitle != null) textToCheck += subtitle;

                if (!textToCheck.isEmpty()) {
                    detectRoundFromText(textToCheck);
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    private void detectRoundFromText(String text) {
        if (text == null) return;

        // Remove color codes and convert to lowercase
        String cleanText = text.replaceAll("§[0-9a-fk-or]", "").toLowerCase().trim();

        // Look for "Round X" pattern
        Matcher matcher = ROUND_PATTERN.matcher(cleanText);
        if (matcher.find()) {
            try {
                int detectedRound = Integer.parseInt(matcher.group(1));
                if (detectedRound > 0 && detectedRound <= 30) { // Valid round range
                    if (detectedRound != currentRound) { // Only trigger if it's a new round
                        setCurrentRound(detectedRound);
                        System.out.println("[ZombiesExplorer] Round " + detectedRound + " detected via title text");
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void setCurrentRound(int round) {
        currentRound = round;
        roundStartTime = System.currentTimeMillis();

        // Mark that we've detected round 1 if this is round 1
        if (round == 1) {
            round1Detected = true;
            gameStartTime = System.currentTimeMillis();
        }

        // Clear previous green period entities when new round starts
        greenPeriodEntities.clear();

        System.out.println("[ZombiesExplorer] Round " + round + " set! Green period started for 12 seconds.");
    }

    private boolean isInGreenPeriod() {
        // Check if we're in the 12-second window after round start
        if (roundStartTime == 0) return false;
        long timeSinceRoundStart = System.currentTimeMillis() - roundStartTime;
        return timeSinceRoundStart <= GREEN_PERIOD_MS;
    }

    // FIXED: Simplified color logic - same for all rounds
    private Color getMobColor(int entityId) {
        // For ALL rounds (including Round 1), check if entity spawned during green period
        if (greenPeriodEntities.contains(entityId)) {
            return WAVE_1_COLOR; // Green for entities spawned in first 12 seconds
        }

        // For ALL rounds, entities spawned after green period get yellow
        return WAVE_2_PLUS_COLOR; // Yellow for entities spawned after 12 seconds
    }

    // Public method to manually trigger round 1 (for testing)
    public void forceRound1() {
        setCurrentRound(1);
    }

    private void scanForMobs() {
        boolean inGreenPeriod = isInGreenPeriod();

        // Scan for mobs that match your criteria
        for (Entity entity : mc.theWorld.loadedEntityList) {
            // Skip if not a living entity, is an armor stand, is a player, is a wither
            if (!(entity instanceof EntityLivingBase) ||
                    entity instanceof EntityArmorStand ||
                    entity instanceof EntityPlayer ||
                    entity instanceof EntityWither ||
                    !entity.isEntityAlive()) {
                continue;
            }

            EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
            int entityId = entity.getEntityId();

            // Skip if already tracked
            if (trackedEntities.contains(entityId) || dyingEntities.contains(entityId)) {
                continue;
            }

            // Check if this entity is a boss (using same logic as BossHighlightHandler)
            boolean isBoss = false;
            float health = entityLivingBase.getHealth();
            float maxHealth = entityLivingBase.getMaxHealth();

            // Skip entities with exactly 300 HP (likely scoreboard)
            if (Math.abs(health - 300.0F) < 0.01F) {
                continue;
            }

            // Check if this is a boss (80+ HP or 80+ max HP or stone sword + chainmail)
            if (health >= 80.0F || maxHealth >= 80.0F) {
                isBoss = true;
            } else if (hasStoneSwordAndChainmail(entityLivingBase)) {
                isBoss = true;
            }

            // Skip bosses - let BossHighlightHandler handle them COMPLETELY
            if (isBoss) {
                continue;
            }

            // Your exact mob filtering logic (only for non-bosses)
            if (entityLivingBase instanceof EntityZombie ||
                    entityLivingBase instanceof EntitySlime ||
                    entityLivingBase instanceof EntityWolf ||
                    entityLivingBase instanceof EntityWitch ||
                    entityLivingBase instanceof EntityEndermite ||
                    entityLivingBase instanceof EntityCreeper ||
                    entityLivingBase instanceof EntityBlaze ||
                    entityLivingBase instanceof EntitySkeleton ||
                    entityLivingBase instanceof EntityGhast ||
                    entityLivingBase instanceof EntityGolem ||
                    entityLivingBase instanceof EntitySquid ||
                    entityLivingBase instanceof EntitySilverfish ||
                    entityLivingBase instanceof EntityGiantZombie ||
                    entityLivingBase instanceof EntityCaveSpider ||
                    entityLivingBase instanceof EntityMooshroom ||
                    entityLivingBase instanceof EntityOcelot ||
                    (entityLivingBase instanceof EntityGuardian && entityLivingBase.getMaxHealth() > 30) ||
                    (entityLivingBase instanceof EntityChicken && !entityLivingBase.isInvisible())) {

                // Add to tracked entities
                trackedEntities.add(entityId);

                // If we're in the green period, mark this entity for green color
                if (inGreenPeriod) {
                    greenPeriodEntities.add(entityId);
                }

                // If this is the first mob we see and we haven't detected round 1 yet, assume round 1
                if (!round1Detected && gameStartTime == 0) {
                    setCurrentRound(1);
                    System.out.println("[ZombiesExplorer] First mob detected - assuming Round 1");
                }
            }
        }
    }

    private boolean hasStoneSwordAndChainmail(EntityLivingBase entity) {
        // Check held item for stone sword
        boolean hasStoneSword = false;

        // Check main hand (index 0 in equipment array)
        net.minecraft.item.ItemStack heldItem = entity.getEquipmentInSlot(0);
        if (heldItem != null && heldItem.getItem() == net.minecraft.init.Items.stone_sword) {
            hasStoneSword = true;
        }

        // Check for chainmail armor (any piece)
        boolean hasChainmail = false;
        for (int i = 1; i <= 4; i++) { // 1-4 are armor slots
            net.minecraft.item.ItemStack armorItem = entity.getEquipmentInSlot(i);
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

    private void cleanupTrackedEntities() {
        // Remove entities that no longer exist from both lists
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
            } else {
                // Double-check: if this entity became a boss somehow, remove it from mob tracking
                EntityLivingBase living = (EntityLivingBase) entity;
                float health = living.getHealth();
                float maxHealth = living.getMaxHealth();

                if (health >= 80.0F || maxHealth >= 80.0F || hasStoneSwordAndChainmail(living)) {
                    toRemove.add(entityId);
                }
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

        // Clean up green period entities that are gone
        greenPeriodEntities.removeAll(toRemove);
    }

    // Chams rendering - includes death animation
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderLivingPre(RenderLivingEvent.Pre<?> event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.MobChams || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        Entity entity = event.entity;
        int entityId = entity.getEntityId();

        // Apply chams to our tracked mob entities (both alive and dying) but not players
        if ((trackedEntities.contains(entityId) || dyingEntities.contains(entityId)) && !(entity instanceof EntityPlayer)) {
            // Enable polygon offset fill - this is what makes the entity visible through walls
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(1.0F, -1100000.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderLivingPost(RenderLivingEvent.Post<?> event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.MobChams || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        Entity entity = event.entity;
        int entityId = entity.getEntityId();

        // Apply chams to our tracked mob entities (both alive and dying) but not players
        if ((trackedEntities.contains(entityId) || dyingEntities.contains(entityId)) && !(entity instanceof EntityPlayer)) {
            // Disable polygon offset fill and reset
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(1.0F, 1100000.0F);
        }
    }

    // ESP rendering - only for ALIVE entities with round-based colors
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!ZombiesExplorer.ENABLED || !ZombiesExplorer.MobESP || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (trackedEntities.isEmpty()) {
            return;
        }

        // Render hitboxes ONLY for ALIVE entities with appropriate colors
        for (Integer entityId : trackedEntities) {
            Entity entity = mc.theWorld.getEntityByID(entityId);
            if (entity != null && entity instanceof EntityLivingBase) {
                // Final boss check before rendering
                EntityLivingBase living = (EntityLivingBase) entity;
                float health = living.getHealth();
                float maxHealth = living.getMaxHealth();

                // Skip if this became a boss
                if (health >= 80.0F || maxHealth >= 80.0F || hasStoneSwordAndChainmail(living)) {
                    continue;
                }

                // Get color based on when entity spawned (FIXED VERSION)
                Color mobColor = getMobColor(entityId);

                drawHitbox(entity, event.partialTicks, mobColor);
            }
        }
    }

    // CLEAN SIMPLE HITBOX RENDERING - Same as BossHighlightHandler
    private void drawHitbox(Entity entity, float partialTicks, Color color) {
        GlStateManager.pushMatrix();

        // Camera position
        double cameraX = mc.getRenderManager().viewerPosX;
        double cameraY = mc.getRenderManager().viewerPosY;
        double cameraZ = mc.getRenderManager().viewerPosZ;

        // Setup rendering - EXACT SAME as BossHighlightHandler
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

        // Set line width using the constant - SAME AS BOSS
        GL11.glLineWidth(LINE_THICKNESS);

        // Set color - SIMPLE, no complex alpha calculations
        GlStateManager.color(color.getRed() / 255.0F, color.getGreen() / 255.0F,
                color.getBlue() / 255.0F, color.getAlpha() / 255.0F);

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

        // Render the hitbox - SIMPLE, ONE CALL ONLY
        RenderGlobal.drawSelectionBoundingBox(expandedBox);

        // Reset GL state - EXACT SAME as BossHighlightHandler
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
}
