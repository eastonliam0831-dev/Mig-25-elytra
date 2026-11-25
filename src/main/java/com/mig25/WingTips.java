package com.mig25;

import com.mig25.loader.Mat4;

/** Finds the two wingtips of the current plane (widest points) and converts them to world positions. */
public final class WingTips {
	private static LoadedModel cachedModel;
	private static String cachedKey = "";
	private static double[] cached = new double[6];

	private WingTips() {}

	/** {x,y,z of one tip, x,y,z of the other}, in the jet's own frame, in blocks (profile offset included). */
	public static synchronized double[] local(LoadedModel m, ModelProfile pr) {
		String key = pr.rotX + "|" + pr.rotY + "|" + pr.rotZ + "|" + pr.size + "|" + pr.offX + "|" + pr.offY + "|" + pr.offZ;
		if (m == cachedModel && key.equals(cachedKey)) return cached;
		double[] rot = Mat4.mul(Mat4.rotY(pr.rotY), Mat4.mul(Mat4.rotX(pr.rotX), Mat4.rotZ(pr.rotZ)));
		double s = pr.size / Math.max(1e-6, m.longest);
		double[] a = null, b = null;
		double maxX = -Double.MAX_VALUE, minX = Double.MAX_VALUE;
		for (int gi = 0; gi < m.groups.length; gi++) {
			if (m.builtin && gi == 3) continue; // landing gear never defines the wingspan
			float[] v = m.groups[gi].v;
			for (int o = 0; o + 2 < v.length; o += 8) {
				double[] q = Mat4.apply(rot, v[o], v[o + 1], v[o + 2]);
				if (q[0] > maxX) { maxX = q[0]; a = q; }
				if (q[0] < minX) { minX = q[0]; b = q; }
			}
		}
		double[] out = new double[6];
		if (a != null && b != null) {
			out[0] = a[0] * s + pr.offX; out[1] = a[1] * s + pr.offY; out[2] = a[2] * s + pr.offZ;
			out[3] = b[0] * s + pr.offX; out[4] = b[1] * s + pr.offY; out[5] = b[2] * s + pr.offZ;
		}
		cachedModel = m;
		cachedKey = key;
		cached = out;
		return out;
	}

	/** Jet-frame points -> world: translate by the player, rotate by yaw/pitch/roll, add the "ahead" offset. */
	public static double[] toWorld(double px, double py, double pz, float yawDeg, float pitchDeg, float rollDeg,
			double ahead, double[] local) {
		double[] m = Mat4.mul(Mat4.rotY(-yawDeg), Mat4.mul(Mat4.rotX(pitchDeg), Mat4.rotZ(rollDeg)));
		double[] out = new double[6];
		for (int i = 0; i < 2; i++) {
			double[] w = Mat4.apply(m, local[i * 3], local[i * 3 + 1], local[i * 3 + 2] + ahead);
			out[i * 3] = px + w[0];
			out[i * 3 + 1] = py + w[1];
			out[i * 3 + 2] = pz + w[2];
		}
		return out;
	}
}
