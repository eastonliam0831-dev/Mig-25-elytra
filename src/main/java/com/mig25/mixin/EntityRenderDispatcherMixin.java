package com.mig25.mixin;

import com.mig25.JetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the vanilla player model (and its elytra) while the jet is shown. Optional: require = 0. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void mig25$hidePlayer(Entity entity, @Coerce Object frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (JetState.active && entity == Minecraft.getInstance().player) {
			cir.setReturnValue(false);
		}
	}
}
