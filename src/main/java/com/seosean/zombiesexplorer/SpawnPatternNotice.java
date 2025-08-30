package com.seosean.zombiesexplorer;

import com.seosean.showspawntime.ShowSpawnTime;
import com.seosean.showspawntime.utils.DebugUtils;
import com.seosean.showspawntime.utils.LanguageUtils;
import com.seosean.zombiesexplorer.utils.DelayedTask;
import com.seosean.zombiesexplorer.utils.Order;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.*;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;
import java.util.stream.Collectors;

public class SpawnPatternNotice {
    public boolean startCollecting = true;
    public List<EntityLivingBase> powerupEnsuredMobList = new ArrayList<>();
    public List<EntityLivingBase> powerupPredictMobList = new ArrayList<>();
    public List<EntityLivingBase> badhsMobList = new ArrayList<>();
    public List<EntityLivingBase> allEntities = new ArrayList<>();
    public List<Entity> entitiesOnLine = new ArrayList<>();

    // Store the last bad headshot mob separately to avoid delays
    public EntityLivingBase lastBadHeadShotMob = null;

    // Track dying entities and their original types
    public Map<EntityLivingBase, Integer> dyingEntities = new HashMap<>();
    public Map<EntityLivingBase, String> dyingEntityTypes = new HashMap<>();
    private static final int DEATH_ANIMATION_TIME = 40; // Ticks to keep entity in dying state

    // Constants to identify dying entity types
    private static final String TYPE_BAD_HEADSHOT = "BAD_HEADSHOT";
    private static final String TYPE_BAD_HEADSHOT_LINE = "BAD_HEADSHOT_LINE";
    private static final String TYPE_POWERUP_PREDICT = "POWERUP_PREDICT";
    private static final String TYPE_POWERUP_ENSURED = "POWERUP_ENSURED";
    private static final String TYPE_NORMAL = "NORMAL";

    @SubscribeEvent
    public void onZombiesSpawn(EntityJoinWorldEvent event) {
        Entity entity = event.entity;
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
        if (entity.equals(Minecraft.getMinecraft().thePlayer)) {
            powerupEnsuredMobList = new ArrayList<>();
            powerupPredictMobList = new ArrayList<>();
            badhsMobList = new ArrayList<>();
            allEntities = new ArrayList<>();
            dyingEntities = new HashMap<>();
            dyingEntityTypes = new HashMap<>();
            lastBadHeadShotMob = null;
            startCollecting = true;
            return;
        }

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

            if (ZombiesExplorer.PowerupDetector) {
                if (!(entityLivingBase instanceof EntitySquid) &&
                        !(entityLivingBase instanceof EntityChicken) &&
                        !(entityLivingBase instanceof EntityMooshroom) &&
                        !(entityLivingBase instanceof EntityWolf && LanguageUtils.getMap().equals(LanguageUtils.ZombiesMap.ALIEN_ARCADIUM) &&
                                entityLivingBase.getDistanceSq(-16.5, 72, -0.5) <= 36)) {

                    boolean flag = ShowSpawnTime.getPowerupDetect().insRounds.isEmpty() &&
                            ShowSpawnTime.getPowerupDetect().maxRounds.isEmpty() &&
                            ShowSpawnTime.getPowerupDetect().ssRounds.isEmpty();
                    int predict = flag ? (ShowSpawnTime.getSpawnTimes().currentRound == 1 ? 1 : 2) : ZombiesExplorer.PowerupPredictor;

                    int amount = PowerUpDetect.amountOfIncomingPowerups;

                    if (startCollecting) {
                        if (powerupEnsuredMobList.size() == amount && powerupPredictMobList.size() < predict) {
                            if (!powerupPredictMobList.contains(entityLivingBase) && !powerupEnsuredMobList.contains(entityLivingBase)) {
                                powerupPredictMobList.add(entityLivingBase);
                            }
                        }

                        if (powerupEnsuredMobList.size() < amount) {
                            if (!powerupEnsuredMobList.contains(entityLivingBase) && !powerupPredictMobList.contains(entityLivingBase)) {
                                powerupEnsuredMobList.add(entityLivingBase);
                            }
                        }

                        if (powerupEnsuredMobList.size() == amount && powerupPredictMobList.size() == predict) {
                            startCollecting = false;
                        }
                    }
                }
            }

            if (ZombiesExplorer.BadHeadShotDetector) {
                if (!badhsMobList.contains(entityLivingBase) && !isDyingOrDead(entityLivingBase)) {
                    badhsMobList.add(entityLivingBase);

                    // Important: Update the lastBadHeadShotMob immediately when adding a new entity
                    lastBadHeadShotMob = entityLivingBase;
                }
            }

            allEntities.add(entityLivingBase);
        }
    }

    // Helper method to check if an entity is dying or dead
    private boolean isDyingOrDead(EntityLivingBase entity) {
        return entity == null || entity.isDead || entity.getHealth() <= 0 || dyingEntities.containsKey(entity);
    }

    @SubscribeEvent
    public void setDisplayTime(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return;
        if (event.phase != TickEvent.Phase.START) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;

        // FIX: Check for bad headshot mob validity first
        if (lastBadHeadShotMob != null) {
            if (isDyingOrDead(lastBadHeadShotMob) && !dyingEntities.containsKey(lastBadHeadShotMob)) {
                // The last bad headshot mob is dead but not in our dying entities map
                // This happens if the entity died abruptly without proper handling
                String entityType = TYPE_BAD_HEADSHOT;
                dyingEntities.put(lastBadHeadShotMob, 0);
                dyingEntityTypes.put(lastBadHeadShotMob, entityType);
                badhsMobList.remove(lastBadHeadShotMob);
                updateLastBadHeadShotMob();
            } else if (!badhsMobList.contains(lastBadHeadShotMob) && !dyingEntities.containsKey(lastBadHeadShotMob)) {
                // The last bad headshot mob somehow got removed from the list but isn't dying
                // This is an edge case that might happen during wave transitions
                updateLastBadHeadShotMob();
            }
        } else if (!badhsMobList.isEmpty()) {
            // If lastBadHeadShotMob is null but we have mobs in the list, update it
            updateLastBadHeadShotMob();
        }

        // Check for newly dead entities
        for (EntityLivingBase entity : new ArrayList<>(allEntities)) {
            if ((entity.isDead || entity.getHealth() <= 0) && !dyingEntities.containsKey(entity)) {
                // Save entity's type before adding to dying entities
                String entityType = TYPE_NORMAL;

                if (entity.equals(lastBadHeadShotMob)) {
                    entityType = TYPE_BAD_HEADSHOT;
                    // If the last bad headshot mob died, find a new one
                    badhsMobList.remove(entity);
                    updateLastBadHeadShotMob();
                } else if (entitiesOnLine.contains(entity)) {
                    entityType = TYPE_BAD_HEADSHOT_LINE;
                    entitiesOnLine.remove(entity);
                } else if (powerupPredictMobList.contains(entity)) {
                    entityType = TYPE_POWERUP_PREDICT;
                    powerupPredictMobList.remove(entity);
                } else if (powerupEnsuredMobList.contains(entity)) {
                    entityType = TYPE_POWERUP_ENSURED;
                    powerupEnsuredMobList.remove(entity);
                } else if (badhsMobList.contains(entity)) {
                    entityType = TYPE_NORMAL;
                    badhsMobList.remove(entity);
                    // If a regular bad headshot mob died, check if we need to update lastBadHeadShotMob
                    if (lastBadHeadShotMob == entity) {
                        updateLastBadHeadShotMob();
                    }
                }

                // Add to dying entities with current tick count and type
                dyingEntities.put(entity, 0);
                dyingEntityTypes.put(entity, entityType);
            }
        }

        // Process dying entities
        Iterator<Map.Entry<EntityLivingBase, Integer>> iter = dyingEntities.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<EntityLivingBase, Integer> entry = iter.next();
            EntityLivingBase entity = entry.getKey();
            int deathTicks = entry.getValue() + 1;

            if (deathTicks >= DEATH_ANIMATION_TIME) {
                // Remove after death animation completes
                allEntities.remove(entity);
                dyingEntityTypes.remove(entity);
                iter.remove();
            } else {
                // Update death tick counter
                entry.setValue(deathTicks);
            }
        }

        // Remove any entities that disappeared without proper death state
        List<EntityLivingBase> invalidEntities = new ArrayList<>();
        for (EntityLivingBase entity : allEntities) {
            // Check if entity is null or not valid anymore
            if (entity == null || entity.isDead || entity.getHealth() <= 0) {
                if (!dyingEntities.containsKey(entity)) {
                    invalidEntities.add(entity);
                }
            }
        }

        // Clean up any invalid entities
        for (EntityLivingBase entity : invalidEntities) {
            if (entity != null && entity.equals(lastBadHeadShotMob)) {
                updateLastBadHeadShotMob();
            }

            allEntities.remove(entity);
            badhsMobList.remove(entity);
            powerupEnsuredMobList.remove(entity);
            powerupPredictMobList.remove(entity);
            entitiesOnLine.remove(entity);
        }
    }

    // Helper method to update the last bad headshot mob reference
    private void updateLastBadHeadShotMob() {
        if (!badhsMobList.isEmpty()) {
            lastBadHeadShotMob = badhsMobList.get(badhsMobList.size() - 1);
        } else {
            lastBadHeadShotMob = null;
        }
    }

    // Method to check if an entity is the current bad headshot mob
    public boolean isCurrentBadHeadShotMob(EntityLivingBase entity) {
        return entity != null && lastBadHeadShotMob != null && entity.equals(lastBadHeadShotMob);
    }

    // Method to check if a dying entity was the bad headshot mob
    public boolean wasBadHeadShotMob(EntityLivingBase entity) {
        return entity != null && dyingEntities.containsKey(entity) &&
                dyingEntityTypes.containsKey(entity) &&
                TYPE_BAD_HEADSHOT.equals(dyingEntityTypes.get(entity));
    }

    // Method to check if a dying entity was on the bad headshot line
    public boolean wasOnBadHeadShotLine(EntityLivingBase entity) {
        return entity != null && dyingEntities.containsKey(entity) &&
                dyingEntityTypes.containsKey(entity) &&
                TYPE_BAD_HEADSHOT_LINE.equals(dyingEntityTypes.get(entity));
    }

    // Method to check if a dying entity was a powerup predict mob
    public boolean wasPowerupPredictMob(EntityLivingBase entity) {
        return entity != null && dyingEntities.containsKey(entity) &&
                dyingEntityTypes.containsKey(entity) &&
                TYPE_POWERUP_PREDICT.equals(dyingEntityTypes.get(entity));
    }

    // Method to check if a dying entity was a powerup ensured mob
    public boolean wasPowerupEnsuredMob(EntityLivingBase entity) {
        return entity != null && dyingEntities.containsKey(entity) &&
                dyingEntityTypes.containsKey(entity) &&
                TYPE_POWERUP_ENSURED.equals(dyingEntityTypes.get(entity));
    }

    @SubscribeEvent
    public void reinitialize(EntityJoinWorldEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        if (!event.entity.equals(p)) return;

        PowerUpDetect.amountOfIncomingPowerups = 0;
    }

    public List<String> list = new ArrayList<>();
    public DelayedTask delayedTask;

    @SubscribeEvent
    public void onDetectSpawnOrder(EntityJoinWorldEvent event) {
        if (!ZombiesExplorer.MobSpawnOrder) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;

        if (!LanguageUtils.getMap().equals(LanguageUtils.ZombiesMap.ALIEN_ARCADIUM)) {
            return;
        }

        if (!(event.entity instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase entityLivingBase = (EntityLivingBase) event.entity;
        if (entityLivingBase instanceof EntityIronGolem ||
                entityLivingBase instanceof EntityGhast ||
                entityLivingBase instanceof EntityBlaze ||
                entityLivingBase instanceof EntitySlime ||
                entityLivingBase instanceof EntityMagmaCube ||
                entityLivingBase instanceof EntityGiantZombie) {
            String name = entityLivingBase.getName();

            if (entityLivingBase instanceof EntityIronGolem) {
                name = EnumChatFormatting.WHITE + name;
            } else if (entityLivingBase instanceof EntityGhast) {
                name = EnumChatFormatting.LIGHT_PURPLE + name;
            } else if (entityLivingBase instanceof EntityBlaze) {
                name = EnumChatFormatting.GOLD + name;
            } else if (entityLivingBase instanceof EntityMagmaCube) {
                name = EnumChatFormatting.RED + name;
            } else if (entityLivingBase instanceof EntitySlime) {
                name = EnumChatFormatting.GREEN + name;
            } else if (entityLivingBase instanceof EntityGiantZombie) {
                name = EnumChatFormatting.AQUA + name;
            }

            list.add(name);

            if (delayedTask != null) {
                delayedTask.cancel();
            }

            delayedTask = new DelayedTask() {
                @Override
                public void run() {
                    List<String> newOrderList = list.stream().distinct().collect(Collectors.toList());
                    Order.input(newOrderList);

                    list = new ArrayList<>();
                    delayedTask = null;
                }
            }.runTaskLater(19);
        }
    }
}
