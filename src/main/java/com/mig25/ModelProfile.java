package com.mig25;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Per-model editor settings: size, rotation, offset and afterburners. Saved in config/mig25/profiles. */
public final class ModelProfile {
	/** Positions/sizes are in percent of the jet's length; x is positive to the RIGHT, z positive forward. */
	public static final class Afterburner {
		public double x, y, z = -45, len = 14, rad = 2.5;
		public int r = 70, g = 140, b = 255;

		public Afterburner copy() {
			Afterburner a = new Afterburner();
			a.x = x; a.y = y; a.z = z; a.len = len; a.rad = rad; a.r = r; a.g = g; a.b = b;
			return a;
		}
	}

	public double size = 8.3; // blocks, longest dimension
	public double rotX, rotY, rotZ; // degrees
	public double offX, offY, offZ; // blocks
	public final List<Afterburner> burners = new ArrayList<>();

	public static ModelProfile defaultFor(boolean builtin) {
		ModelProfile p = new ModelProfile();
		if (builtin) {
			for (int side = -1; side <= 1; side += 2) {
				Afterburner a = new Afterburner();
				a.x = 3.3 * side;
				a.y = -5.5;
				a.z = -46;
				a.len = 16;
				a.rad = 2.8;
				p.burners.add(a);
			}
		}
		return p;
	}

	public void resetTransform() {
		rotX = rotY = rotZ = 0;
		offX = offY = offZ = 0;
	}

	public static String safe(String name) {
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static Path file(Path dir, String modelName) {
		return dir.resolve(safe(modelName) + ".properties");
	}

	private static double d(Properties pr, String k, double def) {
		try {
			return Double.parseDouble(pr.getProperty(k, String.valueOf(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static int i(Properties pr, String k, int def) {
		try {
			return Integer.parseInt(pr.getProperty(k, String.valueOf(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static ModelProfile load(Path dir, String modelName, boolean builtin) {
		ModelProfile p = defaultFor(builtin);
		try {
			Path f = file(dir, modelName);
			if (!Files.exists(f)) return p;
			Properties pr = new Properties();
			try (InputStream in = Files.newInputStream(f)) {
				pr.load(in);
			}
			p.size = d(pr, "size", p.size);
			p.rotX = d(pr, "rotX", 0); p.rotY = d(pr, "rotY", 0); p.rotZ = d(pr, "rotZ", 0);
			p.offX = d(pr, "offX", 0); p.offY = d(pr, "offY", 0); p.offZ = d(pr, "offZ", 0);
			int n = i(pr, "burners", -1);
			if (n >= 0) {
				p.burners.clear();
				for (int k = 0; k < Math.min(n, 16); k++) {
					Afterburner a = new Afterburner();
					String s = "burner." + k + ".";
					a.x = d(pr, s + "x", 0); a.y = d(pr, s + "y", 0); a.z = d(pr, s + "z", -45);
					a.len = d(pr, s + "len", 14); a.rad = d(pr, s + "rad", 2.5);
					a.r = i(pr, s + "r", 70); a.g = i(pr, s + "g", 140); a.b = i(pr, s + "b", 255);
					p.burners.add(a);
				}
			}
		} catch (Throwable t) {
			System.err.println("[mig25] could not load profile: " + t);
		}
		return p;
	}

	public void save(Path dir, String modelName) {
		try {
			Files.createDirectories(dir);
			Properties pr = new Properties();
			pr.setProperty("size", String.valueOf(size));
			pr.setProperty("rotX", String.valueOf(rotX)); pr.setProperty("rotY", String.valueOf(rotY)); pr.setProperty("rotZ", String.valueOf(rotZ));
			pr.setProperty("offX", String.valueOf(offX)); pr.setProperty("offY", String.valueOf(offY)); pr.setProperty("offZ", String.valueOf(offZ));
			pr.setProperty("burners", String.valueOf(burners.size()));
			for (int k = 0; k < burners.size(); k++) {
				Afterburner a = burners.get(k);
				String s = "burner." + k + ".";
				pr.setProperty(s + "x", String.valueOf(a.x)); pr.setProperty(s + "y", String.valueOf(a.y)); pr.setProperty(s + "z", String.valueOf(a.z));
				pr.setProperty(s + "len", String.valueOf(a.len)); pr.setProperty(s + "rad", String.valueOf(a.rad));
				pr.setProperty(s + "r", String.valueOf(a.r)); pr.setProperty(s + "g", String.valueOf(a.g)); pr.setProperty(s + "b", String.valueOf(a.b));
			}
			try (OutputStream out = Files.newOutputStream(file(dir, modelName))) {
				pr.store(out, "MiG-25 Elytra model profile: " + modelName);
			}
		} catch (Throwable t) {
			System.err.println("[mig25] could not save profile: " + t);
		}
	}

	public static void delete(Path dir, String modelName) {
		try {
			Files.deleteIfExists(file(dir, modelName));
		} catch (Throwable ignored) { }
	}
}
