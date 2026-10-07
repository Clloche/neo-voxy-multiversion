package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

@Mixin(value = FogRenderer.class, remap = true, priority = 2000)
public class MixinFogRenderer {
    @Inject(
        method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
        at = @At("TAIL")
    )
    private static void voxy$overrideFog(
        Camera camera,
        FogMode fogMode,
        float viewDistance,
        boolean thickFog,
        float tickDelta,
        CallbackInfo ci
    ) {
        var vrs = IGetVoxyRenderSystem.getNullable();
        if (vrs == null) return;

        if (fogMode == FogMode.FOG_TERRAIN
                && (camera.getFluidInCamera() != FogType.NONE
                    || (camera.getEntity() instanceof LivingEntity living
                        && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))))) {
            return;
        }

        if (RenderSystem.getShaderFogEnd() < 10.0f) return;
        
        // Another mod (e.g. Dynamic Surroundings biome/morning fog) pulled the fog in closer than
        // vanilla's fog. With Sable installed, Voxy extends the frame render distance, so that mod
        // scaled a far-away fog: re-apply the same ratio to the real chunk render distance.
        float curStart = RenderSystem.getShaderFogStart();
        float curEnd = RenderSystem.getShaderFogEnd();
        boolean externalFog = fogMode == FogMode.FOG_TERRAIN && !thickFog
                && curEnd < viewDistance * 0.97f;
        if (fogMode == FogMode.FOG_TERRAIN) {
            me.cortex.voxy.client.core.VoxyRenderSystem.setExternalFogActive(externalFog);
        }
        if (externalFog) {
            float realFar = Math.min(viewDistance,
                    net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f);
            float vanillaStart = viewDistance - net.minecraft.util.Mth.clamp(viewDistance / 10.0f, 4.0f, 64.0f);
            float realStart = realFar - net.minecraft.util.Mth.clamp(realFar / 10.0f, 4.0f, 64.0f);
            float endRatio = curEnd / viewDistance;
            float startRatio = vanillaStart > 0 ? curStart / vanillaStart : endRatio;
            float newEnd = realFar * endRatio;
            float newStart = Math.min(realStart * startRatio, newEnd - 1.0f);
            RenderSystem.setShaderFogStart(Math.max(0.0f, newStart));
            RenderSystem.setShaderFogEnd(newEnd);
            return;
        }
        
        // Adjust sky fog so it always looks smooth and doesn't change with render distance
        if (fogMode == FogMode.FOG_SKY) {
            RenderSystem.setShaderFogStart(0);
            RenderSystem.setShaderFogEnd(VoxyConfig.CONFIG.skyFogDistance);
        }

        if (fogMode == FogMode.FOG_TERRAIN) {
            // Do NOT override unique fog, it's always displayed close and meant for restricting vision
            boolean noFogType = camera.getFluidInCamera() == FogType.NONE;

            if (noFogType && !me.cortex.voxy.client.core.VoxyRenderSystem.restrictingMediumPresent()) {
                RenderSystem.setShaderFogStart(999999999);
                RenderSystem.setShaderFogEnd(999999999);
            }
        }
    }
}
