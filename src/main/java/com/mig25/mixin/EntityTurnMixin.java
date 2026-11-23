package com.mig25.mixin;

import com.mig25.FreeLook;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * While free-look is held, mouse movement is captured for the camera instead of turning the player.
 * Only applied if Entity.turn(double,double) exists (checked by Mig25MixinPlugin).
 */
@Mixin(Entity.class)
public class EntityTurnMixin {
	@ModifyVariable(method = "turn(DD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private double mig25$captureYaw(double yRot) {
		if (FreeLook.capturing((Object) this)) {
			FreeLook.addMouse(yRot * 0.15, 0.0);
			return 0.0;
		}
		return yRot;
	}

	@ModifyVariable(method = "turn(DD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
	private double mig25$capturePitch(double xRot) {
		if (FreeLook.capturing((Object) this)) {
			FreeLook.addMouse(0.0, xRot * 0.15);
			return 0.0;
		}
		return xRot;
	}
}
