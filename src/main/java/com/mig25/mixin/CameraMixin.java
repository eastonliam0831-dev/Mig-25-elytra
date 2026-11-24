package com.mig25.mixin;

import com.mig25.FreeLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds the free-look offset to the camera rotation. Vanilla's Camera has setRotation(float yRot, float xRot).
 * Targeted by name so no Camera import is needed; only applied if that method exists (see Mig25MixinPlugin).
 */
@Mixin(targets = "net.minecraft.client.Camera")
public class CameraMixin {
	@ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private float mig25$yaw(float yRot) {
		FreeLook.cameraHooked = true;
		return FreeLook.cameraActive() ? yRot + FreeLook.yaw : yRot;
	}

	@ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
	private float mig25$pitch(float xRot) {
		if (!FreeLook.cameraActive()) return xRot;
		return Math.max(-90f, Math.min(90f, xRot + FreeLook.pitch));
	}
}
