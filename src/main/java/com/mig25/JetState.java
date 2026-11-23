package com.mig25;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Client-side state: is the jet active, plus smoothed roll and camera handling. */
public final class JetState {
	/** false = jet whenever an elytra is EQUIPPED (default). true = only while actually gliding. */
	public static final boolean ONLY_WHILE_GLIDING = false;

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
		boolean want = p != null && mc.level != null && !p.isSpectator() && elytraEquipped(p)
				&& (!ONLY_WHILE_GLIDING || p.isFallFlying());
		if (want && !active) {
			active = true;
			savedCamera = getCamera(mc);
			setCamera(mc, "THIRD_PERSON_BACK");
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

	// Camera handled by reflection so a renamed method can never break compilation or crash the game.
	private static Object getCamera(Minecraft mc) {
		try {
			Method m = mc.options.getClass().getMethod("getCameraType");
			return m.invoke(mc.options);
		} catch (Throwable t) {
			return null;
		}
	}

	private static void setCamera(Minecraft mc, String name) {
		try {
			Class<?> type = Class.forName("net.minecraft.client.CameraType");
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object value = Enum.valueOf((Class) type, name);
			mc.options.getClass().getMethod("setCameraType", type).invoke(mc.options, value);
		} catch (Throwable t) {
			// ignore: view just stays as it was
		}
	}

	private static void restoreCamera(Minecraft mc) {
		try {
			if (savedCamera != null) {
				Class<?> type = Class.forName("net.minecraft.client.CameraType");
				mc.options.getClass().getMethod("setCameraType", type).invoke(mc.options, savedCamera);
			}
		} catch (Throwable t) {
			// ignore
		}
		savedCamera = null;
	}
}
