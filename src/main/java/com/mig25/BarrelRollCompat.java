package com.mig25;

import java.lang.reflect.Method;

import net.minecraft.world.entity.Entity;

/**
 * Optional "Do a Barrel Roll" support. Reads the roll the mod stores on the player, by reflection,
 * so there is no hard dependency: if the mod is missing, roll() just returns null.
 */
public final class BarrelRollCompat {
	private static Method method;
	private static boolean takesPartial;
	private static boolean searched;

	private BarrelRollCompat() {}

	public static boolean available(Entity e) {
		find(e);
		return method != null;
	}

	private static void find(Entity e) {
		if (searched) return;
		searched = true;
		try {
			Method zeroArg = null;
			for (Method m : e.getClass().getMethods()) {
				String n = m.getName();
				if (!n.contains("getRoll") || m.getReturnType() != float.class) continue;
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == float.class) {
					method = m;
					takesPartial = true;
					return;
				}
				if (m.getParameterCount() == 0) zeroArg = m;
			}
			if (zeroArg != null) {
				method = zeroArg;
				takesPartial = false;
			}
		} catch (Throwable t) {
			method = null;
		}
	}

	/** Roll in degrees, or null if Do a Barrel Roll is not present. */
	public static Float roll(Entity e, float partial) {
		find(e);
		if (method == null) return null;
		try {
			Object r = takesPartial ? method.invoke(e, partial) : method.invoke(e);
			return (Float) r;
		} catch (Throwable t) {
			method = null;
			return null;
		}
	}
}
