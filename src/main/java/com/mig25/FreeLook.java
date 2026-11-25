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

	/** Camera-shake offset in degrees from speed and throttle (adds to the camera only, never to your aim). */
	private static float shake(float phase) {
		if (!JetConfig.shake || !JetState.active || !JetState.gliding) return 0f;
		double over = Math.max(0.0, Math.min(1.0, (JetState.speed - 18.0) / 50.0));
		double amp = JetConfig.SHAKE_AMP[JetConfig.shakeIdx] * over * over;
		double t = (System.nanoTime() / 1.0e9) % 1000.0;
		return (float) (amp * (Math.sin(t * 47.0 + phase) + 0.6 * Math.sin(t * 91.0 + phase * 1.7)) * 0.5);
	}

	/** Total yaw added to the camera: free-look orbit plus shake. */
	public static float yawAdd() {
		return (cameraActive() ? yaw : 0f) + shake(0f);
	}

	/** Total pitch added to the camera: free-look orbit plus shake. */
	public static float pitchAdd() {
		return (cameraActive() ? pitch : 0f) + shake(2.1f);
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
