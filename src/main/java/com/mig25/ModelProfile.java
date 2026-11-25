package com.mig25;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.mig25.loader.Json;

/**
 * Everything that is saved per plane: size, rotation, offset, afterburners, control surfaces, landing gear and
 * the plane's own engine / flight sounds. Stored as config/aerocraft/profiles/&lt;name&gt;.json.
 */
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
	public String engineSound = "";
	public String flightSound = "";
	public final List<Afterburner> burners = new ArrayList<>();
	public final List<RigPart> rig = new ArrayList<>();

	// ---------------------------------------------------------------- defaults
	private static RigPart surface(String part, RigPart.Kind kind, double px, double py, double pz,
			double ax, double ay, double az, double maxDeg, double gain) {
		RigPart r = new RigPart();
		r.name = part;
		r.kind = kind;
		r.mode = RigPart.Mode.PART;
		r.part = part;
		r.px = px; r.py = py; r.pz = pz;
		r.ax = ax; r.ay = ay; r.az = az;
		r.maxDeg = maxDeg;
		r.gain = gain;
		return r;
	}

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
			// hinge positions and axes come from the hinge nodes of the original model
			p.rig.add(surface("AileronL", RigPart.Kind.AILERON, 16.87, -3.3, -26.53, 0.96, -0.08, -0.28, 22, -1));
			p.rig.add(surface("AileronR", RigPart.Kind.AILERON, -16.87, -3.3, -26.53, -0.96, -0.08, -0.28, 22, -1));
			p.rig.add(surface("ElevatorL", RigPart.Kind.ELEVATOR, 6.78, -6.19, -38.94, 0.76, -0.02, -0.64, 22, 1));
			p.rig.add(surface("ElevatorR", RigPart.Kind.ELEVATOR, -6.78, -6.19, -38.94, -0.76, -0.02, -0.64, 22, -1));
			p.rig.add(surface("RudderL", RigPart.Kind.RUDDER, 6.17, -0.93, -39.15, 0.13, 0.90, -0.42, 25, 1));
			p.rig.add(surface("RudderR", RigPart.Kind.RUDDER, -6.17, -0.93, -39.15, -0.13, 0.90, -0.42, 25, 1));
			p.rig.add(surface("FlapL", RigPart.Kind.AIRBRAKE, 7.57, -2.04, -23.47, 0.95, -0.09, -0.28, 30, -1));
			p.rig.add(surface("FlapR", RigPart.Kind.AIRBRAKE, -7.57, -2.04, -23.47, -0.95, -0.09, -0.28, 30, 1));
			RigPart gear = new RigPart();
			gear.name = "Landing gear";
			gear.kind = RigPart.Kind.GEAR;
			gear.mode = RigPart.Mode.GROUP;
			gear.group = 3;
			gear.maxDeg = 0;
			gear.ty = 9.5; // slides up into the fuselage
			p.rig.add(gear);
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

	private static Path jsonFile(Path dir, String modelName) {
		return dir.resolve(safe(modelName) + ".json");
	}

	private static Path legacyFile(Path dir, String modelName) {
		return dir.resolve(safe(modelName) + ".properties");
	}

	// ---------------------------------------------------------------- load
	public static ModelProfile load(Path dir, String modelName, boolean builtin) {
		ModelProfile p = defaultFor(builtin);
		try {
			Path f = jsonFile(dir, modelName);
			if (Files.exists(f)) {
				readJson(p, Files.readString(f, StandardCharsets.UTF_8));
			} else if (Files.exists(legacyFile(dir, modelName))) {
				readLegacy(p, legacyFile(dir, modelName));
			}
		} catch (Throwable t) {
			System.err.println("[mig25] could not load profile " + modelName + ": " + t);
		}
		return p;
	}

	private static void readLegacy(ModelProfile p, Path f) throws Exception {
		Properties pr = new Properties();
		try (InputStream in = Files.newInputStream(f)) {
			pr.load(in);
		}
		p.size = dbl(pr, "size", p.size);
		p.rotX = dbl(pr, "rotX", 0); p.rotY = dbl(pr, "rotY", 0); p.rotZ = dbl(pr, "rotZ", 0);
		p.offX = dbl(pr, "offX", 0); p.offY = dbl(pr, "offY", 0); p.offZ = dbl(pr, "offZ", 0);
		int n = (int) dbl(pr, "burners", -1);
		if (n >= 0) {
			p.burners.clear();
			for (int k = 0; k < Math.min(n, 16); k++) {
				Afterburner a = new Afterburner();
				String s = "burner." + k + ".";
				a.x = dbl(pr, s + "x", 0); a.y = dbl(pr, s + "y", 0); a.z = dbl(pr, s + "z", -45);
				a.len = dbl(pr, s + "len", 14); a.rad = dbl(pr, s + "rad", 2.5);
				a.r = (int) dbl(pr, s + "r", 70); a.g = (int) dbl(pr, s + "g", 140); a.b = (int) dbl(pr, s + "b", 255);
				p.burners.add(a);
			}
		}
	}

	private static double dbl(Properties pr, String k, double def) {
		try {
			return Double.parseDouble(pr.getProperty(k, String.valueOf(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static void readJson(ModelProfile p, String text) {
		Map<String, Object> o = Json.obj(Json.parse(text));
		if (o == null) return;
		p.size = Json.num(o.get("size"), p.size);
		double[] rot = Json.nums(o.get("rot"));
		if (rot != null && rot.length >= 3) { p.rotX = rot[0]; p.rotY = rot[1]; p.rotZ = rot[2]; }
		double[] off = Json.nums(o.get("offset"));
		if (off != null && off.length >= 3) { p.offX = off[0]; p.offY = off[1]; p.offZ = off[2]; }
		if (o.get("engineSound") instanceof String s) p.engineSound = s;
		if (o.get("flightSound") instanceof String s) p.flightSound = s;
		List<Object> bl = Json.arr(o.get("burners"));
		if (bl != null) {
			p.burners.clear();
			for (Object bo : bl) {
				Map<String, Object> b = Json.obj(bo);
				if (b == null || p.burners.size() >= 16) continue;
				Afterburner a = new Afterburner();
				a.x = Json.num(b.get("x"), 0); a.y = Json.num(b.get("y"), 0); a.z = Json.num(b.get("z"), -45);
				a.len = Json.num(b.get("len"), 14); a.rad = Json.num(b.get("rad"), 2.5);
				double[] c = Json.nums(b.get("color"));
				if (c != null && c.length >= 3) { a.r = (int) c[0]; a.g = (int) c[1]; a.b = (int) c[2]; }
				p.burners.add(a);
			}
		}
		List<Object> rl = Json.arr(o.get("rig"));
		if (rl != null) {
			p.rig.clear();
			for (Object ro : rl) {
				Map<String, Object> r = Json.obj(ro);
				if (r == null || p.rig.size() >= 64) continue;
				RigPart rp = new RigPart();
				if (r.get("name") instanceof String s) rp.name = s;
				rp.kind = enumOf(RigPart.Kind.values(), r.get("kind"), RigPart.Kind.AILERON);
				rp.mode = enumOf(RigPart.Mode.values(), r.get("mode"), RigPart.Mode.PART);
				if (r.get("part") instanceof String s) rp.part = s;
				rp.group = Json.intv(r.get("group"), 0);
				double[] box = Json.nums(r.get("box"));
				if (box != null && box.length == 6) rp.box = box;
				double[] pv = Json.nums(r.get("pivot"));
				if (pv != null && pv.length >= 3) { rp.px = pv[0]; rp.py = pv[1]; rp.pz = pv[2]; }
				double[] ax = Json.nums(r.get("axis"));
				if (ax != null && ax.length >= 3) { rp.ax = ax[0]; rp.ay = ax[1]; rp.az = ax[2]; }
				rp.maxDeg = Json.num(r.get("maxDeg"), 25);
				rp.gain = Json.num(r.get("gain"), 1);
				double[] mv = Json.nums(r.get("move"));
				if (mv != null && mv.length >= 3) { rp.tx = mv[0]; rp.ty = mv[1]; rp.tz = mv[2]; }
				rp.enabled = !Boolean.FALSE.equals(r.get("enabled"));
				p.rig.add(rp);
			}
		}
	}

	private static <E extends Enum<E>> E enumOf(E[] values, Object name, E def) {
		if (name instanceof String s) {
			for (E e : values) if (e.name().equals(s)) return e;
		}
		return def;
	}

	// ---------------------------------------------------------------- save
	private static String q(String s) {
		StringBuilder b = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			if (c == '"' || c == '\\') b.append('\\').append(c);
			else if (c < 32) b.append(' ');
			else b.append(c);
		}
		return b.append('"').toString();
	}

	private static String arr(double... v) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < v.length; i++) {
			if (i > 0) b.append(", ");
			b.append(v[i]);
		}
		return b.append(']').toString();
	}

	public String toJson() {
		StringBuilder j = new StringBuilder("{\n");
		j.append("  \"version\": 2,\n");
		j.append("  \"size\": ").append(size).append(",\n");
		j.append("  \"rot\": ").append(arr(rotX, rotY, rotZ)).append(",\n");
		j.append("  \"offset\": ").append(arr(offX, offY, offZ)).append(",\n");
		j.append("  \"engineSound\": ").append(q(engineSound)).append(",\n");
		j.append("  \"flightSound\": ").append(q(flightSound)).append(",\n");
		j.append("  \"burners\": [\n");
		for (int i = 0; i < burners.size(); i++) {
			Afterburner a = burners.get(i);
			j.append("    {\"x\": ").append(a.x).append(", \"y\": ").append(a.y).append(", \"z\": ").append(a.z)
					.append(", \"len\": ").append(a.len).append(", \"rad\": ").append(a.rad)
					.append(", \"color\": ").append(arr(a.r, a.g, a.b)).append('}')
					.append(i + 1 < burners.size() ? ",\n" : "\n");
		}
		j.append("  ],\n  \"rig\": [\n");
		for (int i = 0; i < rig.size(); i++) {
			RigPart r = rig.get(i);
			j.append("    {\"name\": ").append(q(r.name)).append(", \"kind\": ").append(q(r.kind.name()))
					.append(", \"mode\": ").append(q(r.mode.name())).append(", \"part\": ").append(q(r.part))
					.append(", \"group\": ").append(r.group).append(", \"box\": ").append(arr(r.box))
					.append(", \"pivot\": ").append(arr(r.px, r.py, r.pz)).append(", \"axis\": ").append(arr(r.ax, r.ay, r.az))
					.append(", \"maxDeg\": ").append(r.maxDeg).append(", \"gain\": ").append(r.gain)
					.append(", \"move\": ").append(arr(r.tx, r.ty, r.tz)).append(", \"enabled\": ").append(r.enabled).append('}')
					.append(i + 1 < rig.size() ? ",\n" : "\n");
		}
		j.append("  ]\n}\n");
		return j.toString();
	}

	public void save(Path dir, String modelName) {
		try {
			Files.createDirectories(dir);
			Files.writeString(jsonFile(dir, modelName), toJson(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			System.err.println("[mig25] could not save profile: " + t);
		}
	}

	public static void delete(Path dir, String modelName) {
		try {
			Files.deleteIfExists(jsonFile(dir, modelName));
			Files.deleteIfExists(legacyFile(dir, modelName));
		} catch (Throwable ignored) { }
	}
}
