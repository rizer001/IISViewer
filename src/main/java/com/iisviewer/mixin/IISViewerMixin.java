package com.iisviewer.mixin;

import com.iisviewer.IntegrityHudOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the IIS HUD overlay rendering into the game's HUD render cycle.
 * This replaces the removed HudRenderCallback from Fabric API 0.152.2+26.2.
 */
@Mixin(InGameHud.class)
public class IISViewerMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void iisviewer$onHudRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        IntegrityHudOverlay.render(context);
    }
}
