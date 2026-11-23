package com.mig25.mixin;

import com.mig25.FreeLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds the free-look offset to the camera rotation. Targeted by name so no Camera import is needed;
 * only applied if Camera.setRotation(float,float,float) exists (checked by Mig25MixinPlugin).
 */
@Mixin(targets = "net.minecraft.client.Camera")
public class CameraMixin {
	@ModifyVariable(method = "setRotation(FFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private float mig25$yaw(float yRot) {
		return FreeLook.cameraActive() ? yRot + FreeLook.yaw : yRot;
	}

	@ModifyVariable(method = "setRotation(FFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
	private float mig25$pitch(float xRot) {
		if (!FreeLook.cameraActive()) return xRot;
		return Math.max(-90f, Math.min(90f, xRot + FreeLook.pitch));
	}
}
