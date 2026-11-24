package com.mig25.loader;

/** Tiny row-major 4x4 matrix helpers: p' = M * [x y z 1]. */
public final class Mat4 {
	private Mat4() {}

	public static double[] identity() {
		return new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	public static double[] mul(double[] a, double[] b) {
		double[] r = new double[16];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double s = 0;
				for (int k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
				r[i * 4 + j] = s;
			}
		}
		return r;
	}

	public static double[] translation(double x, double y, double z) {
		double[] m = identity();
		m[3] = x; m[7] = y; m[11] = z;
		return m;
	}

	public static double[] scale(double x, double y, double z) {
		double[] m = identity();
		m[0] = x; m[5] = y; m[10] = z;
		return m;
	}

	public static double[] rotX(double deg) {
		double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
		double[] m = identity();
		m[5] = c; m[6] = -s; m[9] = s; m[10] = c;
		return m;
	}

	public static double[] rotY(double deg) {
		double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
		double[] m = identity();
		m[0] = c; m[2] = s; m[8] = -s; m[10] = c;
		return m;
	}

	public static double[] rotZ(double deg) {
		double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
		double[] m = identity();
		m[0] = c; m[1] = -s; m[4] = s; m[5] = c;
		return m;
	}

	/** From a column-major array of 16 (glTF / Collada style are column-major / row-major respectively). */
	public static double[] fromColumnMajor(double[] c) {
		double[] m = new double[16];
		for (int r = 0; r < 4; r++) for (int col = 0; col < 4; col++) m[r * 4 + col] = c[col * 4 + r];
		return m;
	}

	public static double[] fromQuatTRS(double[] t, double[] q, double[] s) {
		double x = q[0], y = q[1], z = q[2], w = q[3];
		double[] m = identity();
		m[0] = (1 - 2 * (y * y + z * z)) * s[0]; m[1] = (2 * (x * y - z * w)) * s[1]; m[2] = (2 * (x * z + y * w)) * s[2];
		m[4] = (2 * (x * y + z * w)) * s[0]; m[5] = (1 - 2 * (x * x + z * z)) * s[1]; m[6] = (2 * (y * z - x * w)) * s[2];
		m[8] = (2 * (x * z - y * w)) * s[0]; m[9] = (2 * (y * z + x * w)) * s[1]; m[10] = (1 - 2 * (x * x + y * y)) * s[2];
		m[3] = t[0]; m[7] = t[1]; m[11] = t[2];
		return m;
	}

	public static double[] apply(double[] m, double x, double y, double z) {
		return new double[] {
			m[0] * x + m[1] * y + m[2] * z + m[3],
			m[4] * x + m[5] * y + m[6] * z + m[7],
			m[8] * x + m[9] * y + m[10] * z + m[11]
		};
	}
}
