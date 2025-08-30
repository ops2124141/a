package com.seosean.zombiesexplorer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashSet;
import java.util.Set;

public class MobCounterHandler {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    // Store living mob entities that should be counted
    private final Set<Integer> countedMobs = new HashSet<>();
    
    // Current mob count
    private int currentMobCount = 0;
    
    // Wave tracking - we'll sync this with MobHighlightHandler if available
    private int currentWave = 1;
    private long waveStartTime = 0;
    
    // Reference to MobHighlightHandler for syncing round information
    private MobHighlightHandler mobHighlightHandler;
    
    public MobCounterHandler() {
        // Initialize
    }
    
    // Allow setting reference to MobHighlightHandler for wave syncing
    public void setMobHighlightHandler(MobHighlightHandler handler) {
        this.mobHighlightHandler = handler;
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ZombiesExplorer.ENABLED || !ZombiesExplorer.MobCounter) {
            return;
        }
        
        if (mc.theWorld == null || mc.thePlayer == null) {
            resetCounterState();
            return;
        }
        
        try {
            // Update mob count every tick for real-time accuracy
            scanAndCountMobs();
            cleanupDeadMobs();
            
            // Sync wave information with MobHighlightHandler if available
            syncWaveInfo();
        } catch (Exception e) {
            // Graceful error handling - reset counter on error
            System.err.println("[ZombiesExplorer] Error in MobCounterHandler: " + e.getMessage());
            resetCounterState();
        }
    }
    
    private void resetCounterState() {
        countedMobs.clear();
        currentMobCount = 0;
        currentWave = 1;
        waveStartTime = 0;
    }
    
    private void syncWaveInfo() {
        if (mobHighlightHandler != null) {
            int detectedRound = mobHighlightHandler.getCurrentRound();
            if (detectedRound != currentWave) {
                setCurrentWave(detectedRound);
            }
        }
    }
    
    private void scanAndCountMobs() {
        countedMobs.clear();
        
        // Scan for hostile mobs that should be counted
        for (Entity entity : mc.theWorld.loadedEntityList) {
            // Skip if not a living entity, is an armor stand, is a player, is a wither, or not alive
            if (!(entity instanceof EntityLivingBase) ||
                    entity instanceof EntityArmorStand ||
                    entity instanceof EntityPlayer ||
                    entity instanceof EntityWither ||
                    !entity.isEntityAlive()) {
                continue;
            }
            
            EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
            int entityId = entity.getEntityId();
            
            // Skip entities with exactly 300 HP (likely scoreboard)
            float health = entityLivingBase.getHealth();
            if (Math.abs(health - 300.0F) < 0.01F) {
                continue;
            }
            
            // Skip boss entities (same logic as MobHighlightHandler)
            if (isBoss(entityLivingBase)) {
                continue;
            }
            
            // Count hostile mobs (using same logic as MobHighlightHandler)
            if (isHostileMob(entityLivingBase)) {
                countedMobs.add(entityId);
            }
        }
        
        // Update current count
        currentMobCount = countedMobs.size();
    }
    
    private boolean isBoss(EntityLivingBase entity) {
        // Use same boss detection logic as MobHighlightHandler
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        
        if (health >= 80.0F || maxHealth >= 80.0F) {
            return true;
        }
        
        return hasStoneSwordAndChainmail(entity);
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
    
    private boolean isHostileMob(EntityLivingBase entity) {
        // Use the same mob filtering logic as MobHighlightHandler for consistency
        return entity instanceof EntityZombie ||
               entity instanceof EntitySlime ||
               entity instanceof EntityWolf ||
               entity instanceof EntityWitch ||
               entity instanceof EntityEndermite ||
               entity instanceof EntityCreeper ||
               entity instanceof EntityBlaze ||
               entity instanceof EntitySkeleton ||
               entity instanceof EntityGhast ||
               entity instanceof EntityGolem ||
               entity instanceof EntitySquid ||
               entity instanceof EntitySilverfish ||
               entity instanceof EntityGiantZombie ||
               entity instanceof EntityCaveSpider ||
               entity instanceof EntityMooshroom ||
               entity instanceof EntityOcelot ||
               (entity instanceof EntityGuardian && entity.getMaxHealth() > 30) ||
               (entity instanceof EntityChicken && !entity.isInvisible());
    }
    
    private void cleanupDeadMobs() {
        // Remove entities that no longer exist or are dead
        Set<Integer> toRemove = new HashSet<>();
        
        for (Integer entityId : countedMobs) {
            Entity entity = mc.theWorld.getEntityByID(entityId);
            if (entity == null || !entity.isEntityAlive()) {
                toRemove.add(entityId);
            }
        }
        
        countedMobs.removeAll(toRemove);
        currentMobCount = countedMobs.size();
    }
    
    // Public getters for the GUI
    public int getCurrentMobCount() {
        return currentMobCount;
    }
    
    public int getCurrentWave() {
        return currentWave;
    }
    
    public void setCurrentWave(int wave) {
        if (wave != currentWave) {
            currentWave = wave;
            waveStartTime = System.currentTimeMillis();
        }
    }
    
    public long getWaveStartTime() {
        return waveStartTime;
    }
    
    // Check if there are any mobs alive
    public boolean hasMobs() {
        return currentMobCount > 0;
    }
    
    // Debug method to get detailed mob information
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Mob Counter Debug Info:\n");
        info.append("- Current Count: ").append(currentMobCount).append("\n");
        info.append("- Current Wave: ").append(currentWave).append("\n");
        info.append("- Tracked Entities: ").append(countedMobs.size()).append("\n");
        info.append("- Wave Start Time: ").append(waveStartTime).append("\n");
        info.append("- Has Mobs: ").append(hasMobs()).append("\n");
        
        if (mobHighlightHandler != null) {
            info.append("- Synced with MobHighlightHandler: true\n");
            info.append("- Synced Round: ").append(mobHighlightHandler.getCurrentRound()).append("\n");
        } else {
            info.append("- Synced with MobHighlightHandler: false\n");
        }
        
        return info.toString();
    }
}