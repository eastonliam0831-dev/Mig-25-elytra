package com.mig25;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Client-side state: is the jet active, plus smoothed roll and camera handling. */
public final class JetState {
	public static volatile boolean active = false;
	public static float roll = 0f, prevRoll = 0f;
	private static float lastYaw = 0f;
	private static Object savedCamera = null;

	private JetState() {}

	public static boolean elytraEquipped(LocalPlayer p) {
		ItemStack s = p.getItemBySlot(EquipmentSlot.CHEST);
		return s.is(Items.ELYTRA);
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		boolean want = JetConfig.enabled && p != null && mc.level != null && !p.isSpectator() && elytraEquipped(p)
				&& (!JetConfig.onlyGliding || p.isFallFlying());
		if (want && !active) {
			active = true;
			savedCamera = getCamera(mc);
			if (JetConfig.forceThirdPerson) setCamera(mc, "THIRD_PERSON_BACK");
			lastYaw = p.getYRot();
		} else if (!want && active) {
			active = false;
			restoreCamera(mc);
		}
		prevRoll = roll;
		if (active && p != null) {
			float dy = net.minecraft.util.Mth.wrapDegrees(p.getYRot() - lastYaw);
			lastYaw = p.getYRot();
			float target = net.minecraft.util.Mth.clamp(dy * 3.0f, -70f, 70f);
			if (!p.isFallFlying()) target = 0f;
			roll += (target - roll) * 0.25f;
		} else {
			roll = 0f;
		}
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
