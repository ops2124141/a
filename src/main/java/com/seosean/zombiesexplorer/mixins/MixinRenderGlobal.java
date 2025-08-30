package com.seosean.zombiesexplorer.mixins;

import com.seosean.zombiesexplorer.ZombiesExplorer;
import com.seosean.zombiesexplorer.mixinsinterface.IMixinRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {
    @Shadow
    private WorldClient theWorld;
    @Final
    @Shadow
    private RenderManager renderManager;
    @Final
    @Shadow
    private Minecraft mc;
    @Shadow
    private Framebuffer entityOutlineFramebuffer;
    @Shadow
    private ShaderGroup entityOutlineShader;

    @Shadow
    protected abstract boolean isRenderEntityOutlines();

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z", ordinal = 2))
    private boolean renderEntities(RenderManager instance, Entity entityIn, float partialTicks) {
        ZombiesExplorer.RenderType renderType = null;
        if (ZombiesExplorer.ENABLED) {
            if (entityIn instanceof EntityLivingBase) {
                EntityLivingBase livingBase = (EntityLivingBase) entityIn;

                // Use a more direct and reliable check for bad headshot mobs
                if (ZombiesExplorer.PowerupDetector && ZombiesExplorer.getInstance().getSpawnPatternNotice().powerupPredictMobList.contains(livingBase)) {
                    renderType = ZombiesExplorer.RenderType.POWERUP_PREDICT;
                } else if (ZombiesExplorer.PowerupDetector && ZombiesExplorer.getInstance().getSpawnPatternNotice().powerupEnsuredMobList.contains(livingBase)) {
                    renderType = ZombiesExplorer.RenderType.POWERUP_ENSURED;
                } else if (ZombiesExplorer.BadHeadShotDetector && ZombiesExplorer.getInstance().getSpawnPatternNotice().isCurrentBadHeadShotMob(livingBase)) {
                    renderType = ZombiesExplorer.RenderType.BAD_HEADSHOT;
                } else if (ZombiesExplorer.BadHeadShotDetector && ZombiesExplorer.getInstance().getSpawnPatternNotice().entitiesOnLine.contains(livingBase)) {
                    renderType = ZombiesExplorer.RenderType.DERIVED_BAD_HEADSHOT;
                }
            }
        }
        return renderType != null ?
                ((IMixinRenderManager) instance).zombiesExplorer$renderEntityStaticWithMixin(entityIn, partialTicks, false, renderType) :
                instance.renderEntitySimple(entityIn, partialTicks);
    }

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;isRenderEntityOutlines()Z", ordinal = 0))
    private boolean onRenderEntities(RenderGlobal renderGlobal) {
        return true;
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", shift = At.Shift.BEFORE, ordinal = 2, args = {"ldc=entities"}), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void renderEntities(Entity renderViewEntity, ICamera camera, float partialTicks, CallbackInfo ci, int pass, double d0, double d1, double d2, Entity entity, double d3, double d4, double d5, List<Entity> list) {
        if (ZombiesExplorer.ENABLED) {
            GlStateManager.depthFunc(519);
            GlStateManager.disableFog();
            this.entityOutlineFramebuffer.framebufferClear();
            this.entityOutlineFramebuffer.bindFramebuffer(false);
            this.theWorld.theProfiler.endStartSection("entityOutlines");
            RenderHelper.disableStandardItemLighting();
            this.renderManager.setRenderOutlines(true);

            for (Entity ent : list) {
                if (ent instanceof EntityLivingBase && !(ent instanceof EntityPlayer)) {
                    EntityLivingBase livingBase = (EntityLivingBase) ent;

                    // Only render outlines for tracked entities
                    boolean isTrackedEntity = ZombiesExplorer.getInstance().getSpawnPatternNotice().allEntities.contains(livingBase);

                    if (!isTrackedEntity) {
                        continue; // Skip entities we're not tracking
                    }

                    // Check if entity is dying
                    boolean isDying = livingBase.isDead || livingBase.getHealth() <= 0;

                    // Check if it's a special entity (powerup or bad headshot)
                    boolean isPowerupEntity = ZombiesExplorer.PowerupDetector && (
                            ZombiesExplorer.getInstance().getSpawnPatternNotice().powerupPredictMobList.contains(livingBase) ||
                                    ZombiesExplorer.getInstance().getSpawnPatternNotice().powerupEnsuredMobList.contains(livingBase) ||
                                    ZombiesExplorer.getInstance().getSpawnPatternNotice().wasPowerupPredictMob(livingBase) ||
                                    ZombiesExplorer.getInstance().getSpawnPatternNotice().wasPowerupEnsuredMob(livingBase)
                    );

                    boolean isBadHsEntity = ZombiesExplorer.BadHeadShotDetector && (
                            ZombiesExplorer.getInstance().getSpawnPatternNotice().isCurrentBadHeadShotMob(livingBase) ||
                                    ZombiesExplorer.getInstance().getSpawnPatternNotice().entitiesOnLine.contains(livingBase) ||
                                    ZombiesExplorer.getInstance().getSpawnPatternNotice().wasBadHeadShotMob(livingBase) ||
                                    ZombiesExplorer.getInstance().getSpawnPatternNotice().wasOnBadHeadShotLine(livingBase)
                    );

                    boolean isSpecialEntity = isPowerupEntity || isBadHsEntity;

                    // Determine if we should render an outline:
                    // 1. If the entity is dying AND (it was special OR white outlines are enabled)
                    // 2. OR if it's a special entity
                    // 3. OR if white outlines for all mobs is enabled
                    boolean shouldRender = (isDying && (isSpecialEntity || ZombiesExplorer.WhiteOutlineAllMobs)) ||
                            isSpecialEntity ||
                            ZombiesExplorer.WhiteOutlineAllMobs;

                    if (shouldRender && livingBase.isInRangeToRender3d(d0, d1, d2)) {
                        GlStateManager.disableLighting();
                        GlStateManager.disableTexture2D();
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(770, 771);

                        this.renderManager.renderEntityStatic(ent, partialTicks, false);

                        GlStateManager.enableTexture2D();
                        GlStateManager.enableLighting();
                        GlStateManager.disableBlend();
                    }
                }
            }

            // Reset render state
            this.renderManager.setRenderOutlines(false);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.depthMask(false);
            this.entityOutlineShader.loadShaderGroup(partialTicks);
            this.mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.enableFog();
            GlStateManager.enableBlend();
            GlStateManager.enableColorMaterial();
            GlStateManager.depthFunc(515);
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
        }
    }

    @Inject(method = "setScoreTeamColor", at = @At("HEAD"), cancellable = true)
    private void onSetScoreTeamColor(EntityLivingBase entityLivingBaseIn, CallbackInfoReturnable<Boolean> cir) {
        if (!ZombiesExplorer.ENABLED || entityLivingBaseIn == null) {
            return;
        }

        // Only apply outlines to tracked entities
        boolean isTrackedEntity = ZombiesExplorer.getInstance().getSpawnPatternNotice().allEntities.contains(entityLivingBaseIn);
        if (!isTrackedEntity) {
            return;
        }

        boolean isDying = entityLivingBaseIn.isDead || entityLivingBaseIn.getHealth() <= 0;

        // For dying entities
        if (isDying) {
            // Check if this dying entity was a special entity
            boolean wasSpecial = ZombiesExplorer.getInstance().getSpawnPatternNotice().wasBadHeadShotMob(entityLivingBaseIn) ||
                    ZombiesExplorer.getInstance().getSpawnPatternNotice().wasOnBadHeadShotLine(entityLivingBaseIn) ||
                    ZombiesExplorer.getInstance().getSpawnPatternNotice().wasPowerupPredictMob(entityLivingBaseIn) ||
                    ZombiesExplorer.getInstance().getSpawnPatternNotice().wasPowerupEnsuredMob(entityLivingBaseIn);

            // Only apply white outline if:
            // 1. It was a special entity (always get death outlines)
            // 2. OR white outlines are enabled
            if (wasSpecial || ZombiesExplorer.WhiteOutlineAllMobs) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F); // White for death
                cir.setReturnValue(true);
            }
            return;
        }

        // For living entities

        // Handle powerup entities
        if (ZombiesExplorer.PowerupDetector) {
            if (ZombiesExplorer.getInstance().getSpawnPatternNotice().powerupPredictMobList.contains(entityLivingBaseIn)) {
                GlStateManager.color(1.0F, 0.0F, 0.0F, 1.0F); // Red
                cir.setReturnValue(true);
                return;
            } else if (ZombiesExplorer.getInstance().getSpawnPatternNotice().powerupEnsuredMobList.contains(entityLivingBaseIn)) {
                GlStateManager.color(0.5F, 0.0F, 0.0F, 1.0F); // Dark red
                cir.setReturnValue(true);
                return;
            }
        }

        // Handle bad headshot entities - using our direct reference to lastBadHeadShotMob
        if (ZombiesExplorer.BadHeadShotDetector) {
            if (ZombiesExplorer.getInstance().getSpawnPatternNotice().isCurrentBadHeadShotMob(entityLivingBaseIn)) {
                GlStateManager.color(0.0F, 1.0F, 0.0F, 1.0F); // Green
                cir.setReturnValue(true);
                return;
            } else if (ZombiesExplorer.getInstance().getSpawnPatternNotice().entitiesOnLine.contains(entityLivingBaseIn)) {
                GlStateManager.color(1.0F, 1.0F, 0.0F, 1.0F); // Yellow
                cir.setReturnValue(true);
                return;
            }
        }

        // Handle white outline toggle for normal entities
        if (ZombiesExplorer.WhiteOutlineAllMobs) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F); // White
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "isRenderEntityOutlines", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isSpectator()Z", ordinal = 0))
    private boolean isSpectatorDisableCheck(EntityPlayerSP entityPlayerSP) {
        return true;
    }

    @Redirect(method = "isRenderEntityOutlines", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z", ordinal = 0))
    private boolean isKeyDownDisableCheck(KeyBinding keyBinding) {
        return true;
    }
}
