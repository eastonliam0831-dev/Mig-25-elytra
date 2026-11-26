package com.mig25;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;

/** Reads the mouse buttons by reflection (so slider dragging works whatever the input API looks like). */
public final class MouseState {
	private static boolean tried;
	private static Object handler;
	private static Method left, right;

	private MouseState() {}

	private static void init() {
		if (tried) return;
		tried = true;
		try {
			Minecraft mc = Minecraft.getInstance();
			for (Field f : Minecraft.class.getDeclaredFields()) {
				if (f.getType().getSimpleName().equals("MouseHandler")) {
					f.setAccessible(true);
					handler = f.get(mc);
				}
			}
			if (handler != null) {
				for (Method m : handler.getClass().getMethods()) {
					if (m.getParameterCount() != 0 || m.getReturnType() != boolean.class) continue;
					if (m.getName().equals("isLeftPressed")) left = m;
					if (m.getName().equals("isRightPressed")) right = m;
				}
			}
		} catch (Throwable t) {
			handler = null;
		}
	}

	private static boolean call(Method m) {
		init();
		try {
			return m != null && handler != null && (Boolean) m.invoke(handler);
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean left() {
		init();
		return call(left);
	}

	public static boolean right() {
		init();
		return call(right);
	}
}
