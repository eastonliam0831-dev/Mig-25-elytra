package com.mig25;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import com.mig25.loader.Mat4;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Live "hologram" of the selected plane inside the control panel: a rotating surface point cloud that follows the
 * plane's rig, so control surfaces and landing gear can be tested without leaving the menu.
 */
public final class HangarPreview {
	private static final int POINTS = 2600;

	// test inputs (-1..1) and gear lever, owned by the control panel buttons
	public static float tRoll, tPitch, tYaw, tBrake;
	public static boolean gearUp;

	private static float vRoll, vPitch, vYaw, vBrake, vGear;
	private static float spin = 20f;
	private static boolean paused = false;

	private static Rig builtFor;
	private static String builtName = "";
	private static float[] px = new float[0], py = new float[0], pz = new float[0];
	private static int[] part = new int[0];
	private static int[] base = new int[0];
	private static List<RigPart> partList = new ArrayList<>();
	private static double longest = 1;
	private static int count;

	private HangarPreview() {}

	public static void togglePause() {
		paused = !paused;
	}

	public static boolean isPaused() {
		return paused;
	}

	public static void resetTests() {
		tRoll = tPitch = tYaw = tBrake = 0f;
		gearUp = false;
	}

	private static void rebuild(LoadedModel m, Rig rig) {
		builtFor = rig;
		builtName = ModelManager.currentName;
		partList = new ArrayList<>();
		longest = Math.max(1e-6, m.longest);
		List<Rig.Piece> pieces = new ArrayList<>(rig.base);
		pieces.addAll(rig.moving);
		double total = 0;
		double[] areas = new double[pieces.size()];
		for (int i = 0; i < pieces.size(); i++) {
			areas[i] = area(pieces.get(i).g);
			total += areas[i];
		}
		px = new float[POINTS + pieces.size() * 4];
		py = new float[px.length];
		pz = new float[px.length];
		part = new int[px.length];
		base = new int[px.length];
		count = 0;
		SplittableRandom rnd = new SplittableRandom(1234);
		for (int i = 0; i < pieces.size(); i++) {
			Rig.Piece pc = pieces.get(i);
			int pi = -1;
			if (pc.part != null) {
				pi = partList.indexOf(pc.part);
				if (pi < 0) {
					partList.add(pc.part);
					pi = partList.size() - 1;
				}
			}
			JetModel.Group g = pc.g;
			int tris = g.idx.length / 3;
			if (tris == 0 || total <= 0) continue;
			double want = POINTS * areas[i] / total;
			double perTri = want / tris;
			for (int t = 0; t < tris && count < px.length; t++) {
				double ta = triArea(g, t);
				double k = perTri * (areas[i] > 0 ? ta * tris / areas[i] : 1);
				int n = (int) k + (rnd.nextDouble() < k - (int) k ? 1 : 0);
				for (int s = 0; s < n && count < px.length; s++) {
					double r1 = Math.sqrt(rnd.nextDouble()), r2 = rnd.nextDouble();
					double u = 1 - r1, v = r1 * (1 - r2), w = r1 * r2;
					int i0 = g.idx[t * 3] * 8, i1 = g.idx[t * 3 + 1] * 8, i2 = g.idx[t * 3 + 2] * 8;
					px[count] = (float) (g.v[i0] * u + g.v[i1] * v + g.v[i2] * w);
					py[count] = (float) (g.v[i0 + 1] * u + g.v[i1 + 1] * v + g.v[i2 + 1] * w);
					pz[count] = (float) (g.v[i0 + 2] * u + g.v[i1 + 2] * v + g.v[i2 + 2] * w);
					part[count] = pi;
					base[count] = m.builtin ? pc.src : -1;
					count++;
				}
			}
		}
	}

	private static double triArea(JetModel.Group g, int t) {
		int i0 = g.idx[t * 3] * 8, i1 = g.idx[t * 3 + 1] * 8, i2 = g.idx[t * 3 + 2] * 8;
		double ux = g.v[i1] - g.v[i0], uy = g.v[i1 + 1] - g.v[i0 + 1], uz = g.v[i1 + 2] - g.v[i0 + 2];
		double vx = g.v[i2] - g.v[i0], vy = g.v[i2 + 1] - g.v[i0 + 1], vz = g.v[i2 + 2] - g.v[i0 + 2];
		double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
		return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
	}

	private static double area(JetModel.Group g) {
		double a = 0;
		for (int t = 0; t < g.idx.length / 3; t++) a += triArea(g, t);
		return a;
	}

	private static float approach(float cur, float target, float rate) {
		return cur + (target - cur) * rate;
	}

	private static double input(RigPart rp) {
		return switch (rp.kind) {
			case AILERON -> vRoll;
			case ELEVATOR -> vPitch;
			case RUDDER -> vYaw;
			case AIRBRAKE -> vBrake;
			case GEAR -> vGear;
		};
	}

	/** Draws the hologram inside the given rectangle. */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
		LoadedModel m = ModelManager.model();
		Rig rig = ModelManager.rig;
		if (rig == null) return;
		if (rig != builtFor || !ModelManager.currentName.equals(builtName)) rebuild(m, rig);

		vRoll = approach(vRoll, tRoll, 0.14f);
		vPitch = approach(vPitch, tPitch, 0.14f);
		vYaw = approach(vYaw, tYaw, 0.14f);
		vBrake = approach(vBrake, tBrake, 0.14f);
		vGear += Math.max(-0.03f, Math.min(0.03f, (gearUp ? 1f : 0f) - vGear));
		if (!paused) spin += 0.7f;

		ModelProfile pr = ModelManager.profile;
		double[] rot = Mat4.mul(Mat4.rotY(pr.rotY), Mat4.mul(Mat4.rotX(pr.rotX), Mat4.rotZ(pr.rotZ)));

		// per-part transforms: p' = T(move*k) * T(pivot) * R(axis, angle) * T(-pivot)
		int np = partList.size();
		double[][] pm = new double[np][];
		double unit = longest / 100.0;
		for (int i = 0; i < np; i++) {
			RigPart rp = partList.get(i);
			double k = rp.isGear() ? vGear : 0;
			double ang = Math.toRadians(input(rp) * rp.gain * rp.maxDeg);
			double len = Math.sqrt(rp.ax * rp.ax + rp.ay * rp.ay + rp.az * rp.az);
			double[] mm = Mat4.identity();
			if (len > 1e-6 && Math.abs(ang) > 1e-4) {
				double ax = rp.ax / len, ay = rp.ay / len, az = rp.az / len;
				double c = Math.cos(ang), s = Math.sin(ang), t = 1 - c;
				double[] r = {
					t * ax * ax + c, t * ax * ay - s * az, t * ax * az + s * ay, 0,
					t * ax * ay + s * az, t * ay * ay + c, t * ay * az - s * ax, 0,
					t * ax * az - s * ay, t * ay * az + s * ax, t * az * az + c, 0,
					0, 0, 0, 1};
				mm = Mat4.mul(Mat4.translation(rp.px * unit, rp.py * unit, rp.pz * unit),
						Mat4.mul(r, Mat4.translation(-rp.px * unit, -rp.py * unit, -rp.pz * unit)));
			}
			mm = Mat4.mul(Mat4.translation(rp.tx * unit * k, rp.ty * unit * k, rp.tz * unit * k), mm);
			pm[i] = mm;
		}

		double yaw = Math.toRadians(spin), pitch = Math.toRadians(18);
		double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
		double scale = Math.min(w, h) * 0.5 / (longest * 0.62);
		int ox = x + w / 2, oy = y + h / 2;
		boolean gearHidden = !JetState.gearVisible();

		int[] sx = new int[count], sy2 = new int[count];
		float[] depth = new float[count];
		boolean[] skip = new boolean[count];
		long[] order = new long[count];
		for (int i = 0; i < count; i++) {
			double X = px[i], Y = py[i], Z = pz[i];
			if (part[i] >= 0) {
				RigPart rp = partList.get(part[i]);
				if (rp.isGear() && gearHidden) { skip[i] = true; order[i] = -1; continue; }
				double[] mm = pm[part[i]];
				double[] q = Mat4.apply(mm, X, Y, Z);
				X = q[0]; Y = q[1]; Z = q[2];
			} else if (base[i] == 3 && gearHidden) {
				skip[i] = true; order[i] = -1; continue;
			}
			double[] r = Mat4.apply(rot, X, Y, Z);
			double x1 = r[0] * cy + r[2] * sy;
			double z1 = -r[0] * sy + r[2] * cy;
			double y2 = r[1] * cp - z1 * sp;
			double z2 = r[1] * sp + z1 * cp;
			sx[i] = ox + (int) (-x1 * scale);
			sy2[i] = oy - (int) (y2 * scale);
			depth[i] = (float) z2;
			order[i] = ((long) ((z2 + longest) * 1000.0) << 24) | i;
		}
		Arrays.sort(order);

		float dMin = -(float) longest * 0.6f, dMax = (float) longest * 0.6f;
		for (int k = 0; k < count; k++) {
			long o = order[k];
			if (o < 0) continue;
			int i = (int) (o & 0xFFFFFF);
			if (skip[i]) continue;
			float t = Math.max(0f, Math.min(1f, (depth[i] - dMin) / (dMax - dMin)));
			int a = 90 + (int) (150 * (1f - t));
			boolean moving = part[i] >= 0;
			int col = moving ? ((accent & 0x00FFFFFF) | (Math.min(255, a + 20) << 24)) : (mixRgb(accent, 0xFFDDE8F0, 0.35f) & 0x00FFFFFF) | (a << 24);
			if (moving && part[i] >= 0 && partList.get(part[i]).isGear()) col = 0xFFFFC040 & 0x00FFFFFF | (Math.min(255, a + 20) << 24);
			g.fill(sx[i], sy2[i], sx[i] + 2, sy2[i] + 2, col);
		}

		// frame details
		g.horizontalLine(x + 4, x + w - 4, y + h - 14, (accent & 0x00FFFFFF) | 0x30000000);
		var font = Minecraft.getInstance().font;
		String info = ModelManager.displayName(ModelManager.currentName) + "   " + partList.size() + " moving parts";
		g.text(font, info, x + 6, y + 5, (accent & 0x00FFFFFF) | 0xE0000000, false);
		String st = String.format("ROLL %+.0f%%  PITCH %+.0f%%  YAW %+.0f%%  BRAKE %.0f%%  GEAR %s",
				vRoll * 100, vPitch * 100, vYaw * 100, vBrake * 100, vGear > 0.9f ? "UP" : vGear < 0.1f ? "DOWN" : "...");
		g.text(font, st, x + 6, y + h - 11, 0xFF8A97A6, false);
	}

	private static int mixRgb(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | (r << 16) | (gg << 8) | bl;
	}
}
