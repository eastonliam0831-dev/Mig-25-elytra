package com.mig25;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Settings, saved to config/mig25.properties. Edited through SettingsScreen (default key: J). */
public final class JetConfig {
	public static boolean enabled = true;
	public static boolean hud = true;
	public static boolean onlyGliding = false;
	public static boolean forceThirdPerson = true;
	public static boolean barrelRoll = true;
	public static boolean invertRoll = false;
	public static boolean autoBank = true;
	public static boolean pushAhead = true;
	public static boolean freeLook = true;
	public static int freeLookSens = 1;
	public static int sizeIndex = 2;
	public static int gearMode = 0; // 0 auto, 1 always down, 2 removed
	public static int colorIndex = 0;

	public static final double MODEL_LENGTH = 23.58; // model units (1 unit ~ 1 metre)
	public static final double[] LOOK_SENS = {0.5, 1.0, 1.5, 2.0};
	public static final double[] SIZES = {0.25, 0.35, 0.5, 0.75, 1.0};
	public static final String[] GEAR_NAMES = {"Auto (down on ground only)", "Always down", "Removed"};
	public static final String[] COLOR_NAMES = {"Green", "Amber", "Cyan", "White", "Red"};
	public static final int[] COLORS = {0xFF33FF66, 0xFFFFB000, 0xFF33DDFF, 0xFFFFFFFF, 0xFFFF5555};

	private JetConfig() {}

	public static void reset() {
		enabled = true; hud = true; onlyGliding = false; forceThirdPerson = true; barrelRoll = true;
		invertRoll = false; autoBank = true; pushAhead = true; freeLook = true; freeLookSens = 1;
		sizeIndex = 2; gearMode = 0; colorIndex = 0;
		save();
	}

	public static float scale() {
		return (float) SIZES[Math.floorMod(sizeIndex, SIZES.length)];
	}

	public static double lengthBlocks() {
		return MODEL_LENGTH * scale();
	}

	public static int hudColor() {
		return COLORS[Math.floorMod(colorIndex, COLORS.length)];
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mig25.properties");
	}

	private static boolean b(Properties p, String k, boolean d) {
		return Boolean.parseBoolean(p.getProperty(k, String.valueOf(d)));
	}

	private static int i(Properties p, String k, int d) {
		try {
			return Integer.parseInt(p.getProperty(k, String.valueOf(d)));
		} catch (NumberFormatException e) {
			return d;
		}
	}

	public static void load() {
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			Properties p = new Properties();
			try (InputStream in = Files.newInputStream(f)) {
				p.load(in);
			}
			enabled = b(p, "enabled", enabled);
			hud = b(p, "hud", hud);
			onlyGliding = b(p, "onlyGliding", onlyGliding);
			forceThirdPerson = b(p, "forceThirdPerson", forceThirdPerson);
			barrelRoll = b(p, "barrelRoll", barrelRoll);
			invertRoll = b(p, "invertRoll", invertRoll);
			autoBank = b(p, "autoBank", autoBank);
			pushAhead = b(p, "pushAhead", pushAhead);
			freeLook = b(p, "freeLook", freeLook);
			freeLookSens = Math.floorMod(i(p, "freeLookSens", freeLookSens), LOOK_SENS.length);
			sizeIndex = Math.floorMod(i(p, "sizeIndex", sizeIndex), SIZES.length);
			gearMode = Math.floorMod(i(p, "gearMode", gearMode), GEAR_NAMES.length);
			colorIndex = Math.floorMod(i(p, "colorIndex", colorIndex), COLORS.length);
		} catch (Throwable t) {
			System.err.println("[mig25] could not load config: " + t);
		}
	}

	public static void save() {
		try {
			Properties p = new Properties();
			p.setProperty("enabled", String.valueOf(enabled));
			p.setProperty("hud", String.valueOf(hud));
			p.setProperty("onlyGliding", String.valueOf(onlyGliding));
			p.setProperty("forceThirdPerson", String.valueOf(forceThirdPerson));
			p.setProperty("barrelRoll", String.valueOf(barrelRoll));
			p.setProperty("invertRoll", String.valueOf(invertRoll));
			p.setProperty("autoBank", String.valueOf(autoBank));
			p.setProperty("pushAhead", String.valueOf(pushAhead));
			p.setProperty("freeLook", String.valueOf(freeLook));
			p.setProperty("freeLookSens", String.valueOf(freeLookSens));
			p.setProperty("sizeIndex", String.valueOf(sizeIndex));
			p.setProperty("gearMode", String.valueOf(gearMode));
			p.setProperty("colorIndex", String.valueOf(colorIndex));
			try (OutputStream out = Files.newOutputStream(file())) {
				p.store(out, "MiG-25 Elytra settings");
			}
		} catch (Throwable t) {
			System.err.println("[mig25] could not save config: " + t);
		}
	}
}
