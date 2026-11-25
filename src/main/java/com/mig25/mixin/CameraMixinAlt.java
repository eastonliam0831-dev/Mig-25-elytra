package com.mig25.mixin;

import com.mig25.FreeLook;
import com.mig25.JetState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Same as CameraMixin for versions whose only rotation method is setRotation(float, float, float). */
@Mixin(targets = "net.minecraft.client.Camera")
public class CameraMixinAlt {
	@ModifyVariable(method = "setRotation(FFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private float mig25$yaw(float yRot) {
		FreeLook.cameraHooked = true;
		return JetState.active ? yRot + FreeLook.yawAdd() : yRot;
	}

	@ModifyVariable(method = "setRotation(FFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
	private float mig25$pitch(float xRot) {
		if (!JetState.active) return xRot;
		return Math.max(-90f, Math.min(90f, xRot + FreeLook.pitchAdd()));
	}
}
