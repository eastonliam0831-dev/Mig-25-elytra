package com.mig25;

import com.mig25.loader.Mat4;

/** Small measurements of the current plane used by editor helpers. */
public final class ModelMetrics {
	private ModelMetrics() {}

	/**
	 * Lowest point of the landing gear (or of the whole model if no gear is rigged) in the jet's frame, in blocks,
	 * after the profile rotation and size but before the profile offset.
	 */
	public static double gearBottomY(LoadedModel m, ModelProfile pr, Rig rig) {
		double[] rot = Mat4.mul(Mat4.rotY(pr.rotY), Mat4.mul(Mat4.rotX(pr.rotX), Mat4.rotZ(pr.rotZ)));
		double s = pr.size / Math.max(1e-6, m.longest);
		double unit = m.longest / 100.0;
		double min = Double.MAX_VALUE;
		if (rig != null) {
			for (Rig.Piece pc : rig.moving) {
				RigPart rp = pc.part;
				float[] v = pc.g.v;
				for (int o = 0; o + 2 < v.length; o += 8) {
					double[] q = Mat4.apply(rot, v[o] + rp.ox * unit, v[o + 1] + rp.oy * unit, v[o + 2] + rp.oz * unit);
					min = Math.min(min, q[1] * s);
				}
			}
		}
		if (min == Double.MAX_VALUE) {
			for (JetModel.Group g : m.groups) {
				for (int o = 0; o + 2 < g.v.length; o += 8) {
					double[] q = Mat4.apply(rot, g.v[o], g.v[o + 1], g.v[o + 2]);
					min = Math.min(min, q[1] * s);
				}
			}
		}
		return min == Double.MAX_VALUE ? 0 : min;
	}
}
