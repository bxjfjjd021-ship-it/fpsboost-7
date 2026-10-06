package com.fpsmenu.mixin;

import com.fpsmenu.FpsMenuConfig;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Позволяет поднять FOV выше ванильных 110 (до 150). */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void fpsmenu$extendFov(Camera camera, float tickDelta, boolean changingFov,
                                   CallbackInfoReturnable<Double> cir) {
        if (!changingFov) return;
        int fov = FpsMenuConfig.get().fov;
        if (fov <= 110) return;
        double vanilla = net.minecraft.client.MinecraftClient.getInstance().options.getFov().getValue();
        if (vanilla != 110.0) return;
        cir.setReturnValue(Math.min(cir.getReturnValue() * (fov / 110.0), 170.0));
    }
}
