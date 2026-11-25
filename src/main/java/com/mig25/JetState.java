package com.mig25;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Client-side state: is the aircraft active, its flight-control inputs, gear animation, G-force and trails. */
public final class JetState {
	public static volatile boolean active = false;
	/** True while the model editor is open: the jet is shown (and flames lit) even without an elytra. */
	public static volatile boolean editorOpen = false;
	public static float previewYaw = 0f, previewPitch = 0f;

	// bank angle (used when Do a Barrel Roll is not present)
	public static float roll = 0f, prevRoll = 0f;

	// flight-control inputs, -1..1 (roll: +right bank, pitch: +pull up, yaw: +turn right, brake 0..1), current and previous tick
	public static float cRoll, cPitch, cYaw, cBrake;
	public static float pRoll, pPitch, pYaw, pBrake;
	/** Test values set by the editor buttons; used while the editor is open. */
	public static float testRoll, testPitch, testYaw, testBrake;
	public static boolean testGearUp = false;

	// landing gear: 0 = fully deployed, 1 = fully retracted
	public static float gearPos = 0f, prevGearPos = 0f;
	public static boolean gearUp = false; // manual state for the Retractable mode

	public static double speed = 0.0; // blocks per second
	public static float gForce = 0f;
	public static boolean gliding = false;

	private static float lastYaw = 0f, lastPitch = 0f, lastRollSrc = 0f;
	private static double lastSpeed = 0.0;
	private static Object savedCamera = null;

	private JetState() {}

	public static boolean elytraEquipped(LocalPlayer p) {
		ItemStack s = p.getItemBySlot(EquipmentSlot.CHEST);
		return s.is(Items.ELYTRA);
	}

	/** Landing gear is drawn unless the mode is "Removed". */
	public static boolean gearVisible() {
		return JetConfig.gearMode != 2;
	}

	/** Toggles the manual gear lever (key bind). Only does something in Retractable mode. */
	public static void toggleGear() {
		if (JetConfig.gearMode == 3) gearUp = !gearUp;
	}

	private static int groundDistance(Minecraft mc, LocalPlayer p) {
		try {
			for (int i = 1; i <= 12; i++) {
				if (!mc.level.getBlockState(p.blockPosition().below(i)).isAir()) return i - 1;
			}
		} catch (Throwable ignored) { }
		return 99;
	}

	private static float approach(float cur, float target, float rate) {
		return cur + (target - cur) * rate;
	}

	private static float clamp1(float v) {
		return Math.max(-1f, Math.min(1f, v));
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		boolean want = JetConfig.enabled && p != null && mc.level != null && !p.isSpectator()
				&& (editorOpen || (elytraEquipped(p) && (!JetConfig.onlyGliding || p.isFallFlying())));
		if (want && !active) {
			active = true;
			savedCamera = getCamera(mc);
			if (JetConfig.forceThirdPerson) setCamera(mc, "THIRD_PERSON_BACK");
			lastYaw = p.getYRot();
			lastPitch = p.getXRot();
			lastRollSrc = 0f;
		} else if (!want && active) {
			active = false;
			restoreCamera(mc);
		}

		prevRoll = roll;
		pRoll = cRoll; pPitch = cPitch; pYaw = cYaw; pBrake = cBrake;
		prevGearPos = gearPos;

		if (!active || p == null) {
			roll = 0f;
			cRoll = cPitch = cYaw = cBrake = 0f;
			pRoll = pPitch = pYaw = pBrake = 0f;
			gForce = 0f;
			gliding = false;
			Trails.tick(false, 0f, null);
			return;
		}

		gliding = p.isFallFlying();
		speed = p.getDeltaMovement().length() * 20.0;
		float yawNow = p.getYRot();
		float pitchNow = p.getXRot();
		float dYaw = Mth.wrapDegrees(yawNow - lastYaw);
		float dPitch = pitchNow - lastPitch;
		lastYaw = yawNow;
		lastPitch = pitchNow;

		// bank angle from turning
		float bankTarget = Mth.clamp(dYaw * 3.0f, -70f, 70f);
		if (!gliding) bankTarget = 0f;
		roll += (bankTarget - roll) * 0.25f;

		// roll source: Do a Barrel Roll when present, otherwise the bank angle
		Float dabr = JetConfig.barrelRoll ? BarrelRollCompat.roll(p, 1f) : null;
		float rollSrc = dabr != null ? (JetConfig.invertRoll ? -dabr : dabr) : roll;
		float dRoll = rollSrc - lastRollSrc;
		lastRollSrc = rollSrc;

		float tRoll, tPitch, tYaw, tBrake;
		if (editorOpen) {
			tRoll = testRoll; tPitch = testPitch; tYaw = testYaw; tBrake = testBrake;
		} else if (gliding) {
			tRoll = clamp1(dRoll * 0.30f);
			tPitch = clamp1(-dPitch * 0.40f);
			tYaw = clamp1(dYaw * 0.35f);
			float decel = (float) Math.max(0.0, (lastSpeed - speed) * 0.15);
			tBrake = Math.max(p.isShiftKeyDown() ? 1f : 0f, Math.min(1f, decel));
		} else {
			tRoll = tPitch = tYaw = tBrake = 0f;
		}
		lastSpeed = speed;
		cRoll = approach(cRoll, tRoll, 0.35f);
		cPitch = approach(cPitch, tPitch, 0.35f);
		cYaw = approach(cYaw, tYaw, 0.35f);
		cBrake = approach(cBrake, tBrake, 0.30f);

		// G-force estimate from turn rate and speed
		float omegaDegPerTick = (float) Math.hypot(dYaw * Math.cos(Math.toRadians(pitchNow)), dPitch);
		double omega = omegaDegPerTick * 20.0 * Math.PI / 180.0;
		float g = gliding ? (float) (speed * omega / 32.0) : 0f;
		gForce = approach(gForce, g, 0.3f);

		// landing gear
		float gearTarget;
		if (editorOpen) {
			gearTarget = testGearUp ? 1f : 0f;
		} else {
			switch (JetConfig.gearMode) {
				case 0 -> gearTarget = gliding ? 1f : 0f;
				case 1 -> gearTarget = 0f;
				case 2 -> gearTarget = 1f;
				default -> {
					if (!gliding) gearUp = false;
					else if (JetConfig.autoDeploy && speed < 14.0 && groundDistance(mc, p) <= 6) gearUp = false;
					gearTarget = gearUp ? 1f : 0f;
				}
			}
		}
		gearPos += Math.max(-0.05f, Math.min(0.05f, gearTarget - gearPos));

		// wingtip trails
		double[] tips = null;
		if (JetConfig.vaporTrails && gliding) {
			LoadedModel model = ModelManager.model();
			ModelProfile prof = ModelManager.profile;
			float ahead = JetConfig.pushAhead ? (float) Math.max(0.0, prof.size * 0.5 - 2.5) : 0f;
			double[] local = WingTips.local(model, prof);
			tips = WingTips.toWorld(p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), yawNow, p.getXRot(), rollSrc, ahead, local);
		}
		float inten = (float) Math.max(0.0, Math.min(1.0, (gForce - JetConfig.TRAIL_G[JetConfig.trailSens]) / 1.5));
		if (speed < 12.0) inten = 0f;
		Trails.tick(tips != null, inten, tips);
	}

	// Camera handled by reflection, found by TYPE (not name), so renamed methods cannot break it.
	private static Class<?> cameraTypeClass() throws Exception {
		return Class.forName("net.minecraft.client.CameraType");
	}

	private static Object getCamera(Minecraft mc) {
		try {
			Class<?> type = cameraTypeClass();
			for (Method m : mc.options.getClass().getMethods()) {
				if (m.getParameterCount() == 0 && m.getReturnType() == type) return m.invoke(mc.options);
			}
			for (java.lang.reflect.Field f : mc.options.getClass().getDeclaredFields()) {
				if (f.getType() == type && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					return f.get(mc.options);
				}
			}
		} catch (Throwable t) {
			System.err.println("[mig25] could not read camera type: " + t);
		}
		return null;
	}

	private static boolean applyCamera(Minecraft mc, Object value) {
		try {
			Class<?> type = cameraTypeClass();
			for (Method m : mc.options.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == type) {
					m.invoke(mc.options, value);
					return true;
				}
			}
			for (java.lang.reflect.Field f : mc.options.getClass().getDeclaredFields()) {
				if (f.getType() == type && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					f.set(mc.options, value);
					return true;
				}
			}
			System.err.println("[mig25] no camera type setter found on Options");
		} catch (Throwable t) {
			System.err.println("[mig25] could not set camera type: " + t);
		}
		return false;
	}

	private static void setCamera(Minecraft mc, String name) {
		try {
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object value = Enum.valueOf((Class) cameraTypeClass(), name);
			applyCamera(mc, value);
		} catch (Throwable t) {
			System.err.println("[mig25] camera enum lookup failed: " + t);
		}
	}

	private static void restoreCamera(Minecraft mc) {
		if (savedCamera != null) applyCamera(mc, savedCamera);
		savedCamera = null;
	}
}
