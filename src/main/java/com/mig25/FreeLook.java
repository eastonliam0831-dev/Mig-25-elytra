package com.mig25;

import net.minecraft.client.Minecraft;

/** Free-look state: hold the key and the mouse orbits the camera around the jet without steering it. */
public final class FreeLook {
	public static volatile boolean held = false;
	/** Set by the camera hook the first time it runs. Mouse capture only happens if the camera can follow it. */
	public static volatile boolean cameraHooked = false;
	public static volatile float yaw = 0f;   // degrees, added to camera yaw
	public static volatile float pitch = 0f; // degrees, added to camera pitch

	private FreeLook() {}

	/** True when mouse movement for this entity should be captured instead of steering. */
	public static boolean capturing(Object entity) {
		return held && cameraHooked && JetState.active && JetConfig.freeLook && entity == Minecraft.getInstance().player;
	}

	public static boolean cameraActive() {
		return JetState.active && JetConfig.freeLook && (held || yaw != 0f || pitch != 0f);
	}

	public static void addMouse(double dYaw, double dPitch) {
		float k = (float) JetConfig.LOOK_SENS[Math.floorMod(JetConfig.freeLookSens, JetConfig.LOOK_SENS.length)];
		yaw += (float) dYaw * k;
		float p = pitch + (float) dPitch * k;
		pitch = Math.max(-89f, Math.min(89f, p));
	}

	/** Called every client tick. keyDown = state of the free-look key. */
	public static void tick(boolean keyDown) {
		held = keyDown && JetState.active && JetConfig.freeLook;
		if (!held) {
			// smoothly swing the camera back behind the jet
			yaw *= 0.6f;
			pitch *= 0.6f;
			if (Math.abs(yaw) < 0.05f) yaw = 0f;
			if (Math.abs(pitch) < 0.05f) pitch = 0f;
		}
		if (!JetState.active) {
			yaw = 0f;
			pitch = 0f;
		}
	}
}
