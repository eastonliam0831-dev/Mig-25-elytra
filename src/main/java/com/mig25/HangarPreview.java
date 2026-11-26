package com.mig25;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import com.mig25.loader.Mat4;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Live "hologram" of the selected plane: a surface point cloud with the landing gear animation, painted colours
 * and (for the paint editor) picking of triangles under the cursor. Everything is drawn with plain GUI fills.
 */
public final class HangarPreview {
	private static final int POINTS = 2600;

	public static boolean gearUp; // landing gear test lever
	private static float vGear;

	public static float spin = 25f;
	public static float tilt = 18f;
	public static boolean autoSpin = true;

	private static Rig builtFor;
	private static int builtVersion = -1;
	private static String builtName = "";
	private static float[] px = new float[0], py = new float[0], pz = new float[0];
	private static int[] part = new int[0], base = new int[0], keyOf = new int[0], srcCol = new int[0];
	private static List<RigPart> partList = new ArrayList<>();
	private static double longest = 1;
	private static int count;

	// triangle centroids for picking
	private static float[] cx = new float[0], cy = new float[0], cz = new float[0];
	private static int[] cKey = new int[0], cPart = new int[0], cPartName = new int[0], cCol = new int[0];
	private static int cCount;

	// projection of the last draw (used by pick)
	private static double vScale = 1, vSin, vCos, vSinP, vCosP;
	private static int vOx, vOy;
	private static double[] vRot = Mat4.identity();
	private static double[][] vPm = new double[0][];

	private HangarPreview() {}

	public static void resetTests() {
		gearUp = false;
	}

	public static void forceRebuild() {
		builtFor = null;
		builtVersion = -1;
	}

	// ------------------------------------------------------------------ build
	private static double triArea(JetModel.Group g, int t) {
		int i0 = g.idx[t * 3] * 8, i1 = g.idx[t * 3 + 1] * 8, i2 = g.idx[t * 3 + 2] * 8;
		double ux = g.v[i1] - g.v[i0], uy = g.v[i1 + 1] - g.v[i0 + 1], uz = g.v[i1 + 2] - g.v[i0 + 2];
		double vx = g.v[i2] - g.v[i0], vy = g.v[i2 + 1] - g.v[i0 + 1], vz = g.v[i2 + 2] - g.v[i0 + 2];
		double qx = uy * vz - uz * vy, qy = uz * vx - ux * vz, qz = ux * vy - uy * vx;
		return 0.5 * Math.sqrt(qx * qx + qy * qy + qz * qz);
	}

	private static void rebuild(LoadedModel m, Rig rig) {
		builtFor = rig;
		builtVersion = ModelManager.rigVersion;
		builtName = ModelManager.currentName;
		partList = new ArrayList<>();
		longest = Math.max(1e-6, m.longest);
		List<Rig.Piece> pieces = new ArrayList<>(rig.base);
		pieces.addAll(rig.moving);
		double total = 0;
		double[] areas = new double[pieces.size()];
		int totalTris = 0;
		for (int i = 0; i < pieces.size(); i++) {
			JetModel.Group g = pieces.get(i).g;
			double a = 0;
			for (int t = 0; t < g.idx.length / 3; t++) a += triArea(g, t);
			areas[i] = a;
			total += a;
			totalTris += g.idx.length / 3;
		}
		px = new float[POINTS + pieces.size() * 4];
		py = new float[px.length];
		pz = new float[px.length];
		part = new int[px.length];
		base = new int[px.length];
		keyOf = new int[px.length];
		srcCol = new int[px.length];
		count = 0;
		cx = new float[totalTris]; cy = new float[totalTris]; cz = new float[totalTris];
		cKey = new int[totalTris]; cPart = new int[totalTris]; cPartName = new int[totalTris]; cCol = new int[totalTris];
		cCount = 0;
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
			if (tris == 0) continue;
			double want = total <= 0 ? 0 : POINTS * areas[i] / total;
			for (int t = 0; t < tris; t++) {
				int i0 = g.idx[t * 3] * 8, i1 = g.idx[t * 3 + 1] * 8, i2 = g.idx[t * 3 + 2] * 8;
				int key = pc.src * Rig.KEY_SPAN + pc.originalTri(t);
				int col = g.col != null ? g.col[g.idx[t * 3]] : 0xFFB8BCC4;
				// centroid for picking
				cx[cCount] = (g.v[i0] + g.v[i1] + g.v[i2]) / 3f;
				cy[cCount] = (g.v[i0 + 1] + g.v[i1 + 1] + g.v[i2 + 1]) / 3f;
				cz[cCount] = (g.v[i0 + 2] + g.v[i1 + 2] + g.v[i2 + 2]) / 3f;
				cKey[cCount] = key;
				cPart[cCount] = pi;
				cPartName[cCount] = g.triPart != null ? g.triPart[t] : -1;
				cCol[cCount] = col;
				cCount++;
				// surface samples for display
				double k = areas[i] > 0 ? want * triArea(g, t) / areas[i] : 0;
				int n = (int) k + (rnd.nextDouble() < k - (int) k ? 1 : 0);
				for (int s = 0; s < n && count < px.length; s++) {
					double r1 = Math.sqrt(rnd.nextDouble()), r2 = rnd.nextDouble();
					double u = 1 - r1, v = r1 * (1 - r2), w = r1 * r2;
					px[count] = (float) (g.v[i0] * u + g.v[i1] * v + g.v[i2] * w);
					py[count] = (float) (g.v[i0 + 1] * u + g.v[i1 + 1] * v + g.v[i2 + 1] * w);
					pz[count] = (float) (g.v[i0 + 2] * u + g.v[i1 + 2] * v + g.v[i2 + 2] * w);
					part[count] = pi;
					base[count] = m.builtin ? pc.src : -1;
					keyOf[count] = key;
					srcCol[count] = col;
					count++;
				}
			}
		}
	}

	private static double[][] partTransforms() {
		int np = partList.size();
		double[][] pm = new double[np][];
		double unit = longest / 100.0;
		for (int i = 0; i < np; i++) {
			RigPart rp = partList.get(i);
			double ang = Math.toRadians(vGear * rp.gain * rp.maxDeg);
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
			mm = Mat4.mul(Mat4.translation(rp.tx * unit * vGear, rp.ty * unit * vGear, rp.tz * unit * vGear), mm);
			mm = Mat4.mul(Mat4.translation(rp.ox * unit, rp.oy * unit, rp.oz * unit), mm);
			pm[i] = mm;
		}
		return pm;
	}

	private static void project(double[] pt, int[] out, float[] depth) {
		double[] r = Mat4.apply(vRot, pt[0], pt[1], pt[2]);
		double x1 = r[0] * vCos + r[2] * vSin;
		double z1 = -r[0] * vSin + r[2] * vCos;
		double y2 = r[1] * vCosP - z1 * vSinP;
		double z2 = r[1] * vSinP + z1 * vCosP;
		out[0] = vOx + (int) (-x1 * vScale);
		out[1] = vOy - (int) (y2 * vScale);
		depth[0] = (float) z2;
	}

	// ------------------------------------------------------------------ draw
	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, boolean paintMode) {
		LoadedModel m = ModelManager.model();
		Rig rig = ModelManager.rig;
		if (rig == null) return;
		if (builtFor == null || builtVersion != ModelManager.rigVersion || !ModelManager.currentName.equals(builtName)) rebuild(m, rig);

		vGear += Math.max(-0.03f, Math.min(0.03f, (gearUp ? 1f : 0f) - vGear));
		if (autoSpin && !paintMode) spin += 0.7f;

		ModelProfile pr = ModelManager.profile;
		vRot = Mat4.mul(Mat4.rotY(pr.rotY), Mat4.mul(Mat4.rotX(pr.rotX), Mat4.rotZ(pr.rotZ)));
		vPm = partTransforms();
		double yaw = Math.toRadians(spin), pitch = Math.toRadians(tilt);
		vSin = Math.sin(yaw); vCos = Math.cos(yaw); vSinP = Math.sin(pitch); vCosP = Math.cos(pitch);
		vScale = Math.min(w, h) * 0.5 / (longest * 0.62);
		vOx = x + w / 2;
		vOy = y + h / 2;
		boolean gearHidden = !JetState.gearVisible();

		int[] sx = new int[count], sy = new int[count];
		float[] depth = new float[count];
		long[] order = new long[count];
		int[] o2 = new int[2];
		float[] d1 = new float[1];
		double[] pt = new double[3];
		for (int i = 0; i < count; i++) {
			pt[0] = px[i]; pt[1] = py[i]; pt[2] = pz[i];
			if (part[i] >= 0) {
				if (gearHidden) { order[i] = -1; continue; }
				double[] q = Mat4.apply(vPm[part[i]], pt[0], pt[1], pt[2]);
				pt[0] = q[0]; pt[1] = q[1]; pt[2] = q[2];
			} else if (base[i] == 3 && gearHidden) {
				order[i] = -1; continue;
			}
			project(pt, o2, d1);
			sx[i] = o2[0]; sy[i] = o2[1]; depth[i] = d1[0];
			order[i] = ((long) ((d1[0] + longest) * 1000.0) << 24) | i;
		}
		Arrays.sort(order);

		float dMin = -(float) longest * 0.6f, dMax = (float) longest * 0.6f;
		java.util.Map<Integer, Integer> paint = pr.paint;
		for (int k = 0; k < count; k++) {
			long o = order[k];
			if (o < 0) continue;
			int i = (int) (o & 0xFFFFFF);
			float t = Math.max(0f, Math.min(1f, (depth[i] - dMin) / (dMax - dMin)));
			int a = 110 + (int) (140 * t); // larger depth = nearer = brighter
			Integer pc = paint.get(keyOf[i]);
			int rgb;
			if (pc != null) rgb = pc;
			else if (part[i] >= 0) rgb = 0xFFC040;
			else rgb = mixRgb(accent, srcCol[i], 0.55f) & 0x00FFFFFF;
			int sh = Math.max(0, Math.min(255, a));
			int col = (sh << 24) | (rgb & 0x00FFFFFF);
			g.fill(sx[i], sy[i], sx[i] + 2, sy[i] + 2, col);
		}

		g.horizontalLine(x + 4, x + w - 4, y + h - 14, (accent & 0x00FFFFFF) | 0x30000000);
		var font = Minecraft.getInstance().font;
		String gearInfo = vGear > 0.9f ? "GEAR UP" : vGear < 0.1f ? "GEAR DOWN" : "GEAR MOVING";
		String info = ModelManager.displayName(ModelManager.currentName);
		g.text(font, fitText(font::width, info, w - 12), x + 6, y + 5, (accent & 0x00FFFFFF) | 0xE0000000, false);
		g.text(font, fitText(font::width, gearInfo, w - 12), x + 6, y + h - 11, 0xFF8A97A6, false);
	}

	private static String fitText(java.util.function.ToIntFunction<String> width, String s, int maxW) {
		if (width.applyAsInt(s) <= maxW) return s;
		String t = s;
		while (t.length() > 1 && width.applyAsInt(t + "...") > maxW) t = t.substring(0, t.length() - 1);
		return t + "...";
	}

	private static int mixRgb(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | (r << 16) | (gg << 8) | bl;
	}

	// ------------------------------------------------------------------ picking (uses the projection of the last draw)
	public static final class Hit {
		public final int key, partName, color;

		Hit(int key, int partName, int color) {
			this.key = key;
			this.partName = partName;
			this.color = color;
		}
	}

	/** Triangle keys inside the brush circle that belong to the front surface (screen coordinates). */
	public static List<Integer> brush(int mx, int my, int radius) {
		List<Integer> out = new ArrayList<>();
		if (cCount == 0) return out;
		boolean gearHidden = !JetState.gearVisible();
		int[] o2 = new int[2];
		float[] d1 = new float[1];
		double[] pt = new double[3];
		float[] depth = new float[cCount];
		boolean[] in = new boolean[cCount];
		float best = -Float.MAX_VALUE;
		long r2 = (long) radius * radius;
		for (int i = 0; i < cCount; i++) {
			pt[0] = cx[i]; pt[1] = cy[i]; pt[2] = cz[i];
			if (cPart[i] >= 0) {
				if (gearHidden) continue;
				double[] q = Mat4.apply(vPm[Math.min(cPart[i], vPm.length - 1)], pt[0], pt[1], pt[2]);
				pt[0] = q[0]; pt[1] = q[1]; pt[2] = q[2];
			}
			project(pt, o2, d1);
			long dx = o2[0] - mx, dy = o2[1] - my;
			if (dx * dx + dy * dy <= r2) {
				in[i] = true;
				depth[i] = d1[0];
				if (d1[0] > best) best = d1[0];
			}
		}
		// larger projected z = nearer to the viewer (the draw order paints larger z last)
		float tol = (float) (longest * 0.07);
		for (int i = 0; i < cCount; i++) {
			if (in[i] && depth[i] >= best - tol) out.add(cKey[i]);
		}
		return out;
	}

	/** The single front-most triangle under the cursor, or null. */
	public static Hit pick(int mx, int my) {
		if (cCount == 0) return null;
		boolean gearHidden = !JetState.gearVisible();
		int[] o2 = new int[2];
		float[] d1 = new float[1];
		double[] pt = new double[3];
		int bestI = -1;
		float bestD = -Float.MAX_VALUE;
		long limit = 36; // 6 px
		for (int i = 0; i < cCount; i++) {
			pt[0] = cx[i]; pt[1] = cy[i]; pt[2] = cz[i];
			if (cPart[i] >= 0) {
				if (gearHidden) continue;
				double[] q = Mat4.apply(vPm[Math.min(cPart[i], vPm.length - 1)], pt[0], pt[1], pt[2]);
				pt[0] = q[0]; pt[1] = q[1]; pt[2] = q[2];
			}
			project(pt, o2, d1);
			long dx = o2[0] - mx, dy = o2[1] - my;
			if (dx * dx + dy * dy <= limit && d1[0] > bestD) {
				bestD = d1[0];
				bestI = i;
			}
		}
		if (bestI < 0) return null;
		return new Hit(cKey[bestI], cPartName[bestI], cCol[bestI]);
	}

	/** Every triangle key that belongs to the given part name id (for "paint whole part"). */
	public static List<Integer> keysOfPart(int partNameId) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < cCount; i++) if (cPartName[i] == partNameId) out.add(cKey[i]);
		return out;
	}

	public static List<Integer> allKeys() {
		List<Integer> out = new ArrayList<>(cCount);
		for (int i = 0; i < cCount; i++) out.add(cKey[i]);
		return out;
	}
}
