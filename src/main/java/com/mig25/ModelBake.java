package com.mig25;

import com.mig25.loader.MeshData;
import com.mig25.loader.UnsupportedFormatException;

/** Turns a loaded triangle soup into a renderable group: centred, flat normals, per-vertex colours. */
public final class ModelBake {
	public static final int MAX_TRIANGLES = 200_000;

	private ModelBake() {}

	public static LoadedModel bake(String name, MeshData m) throws UnsupportedFormatException {
		int n = m.vertexCount();
		if (n == 0) throw new UnsupportedFormatException("The model has no triangles");
		if (m.triangleCount() > MAX_TRIANGLES) {
			throw new UnsupportedFormatException("Too many triangles (" + m.triangleCount() + "). Limit is " + MAX_TRIANGLES
					+ ". Decimate the model in Blender first.");
		}
		float[] p = m.positions();
		int[] c = m.colors();
		float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
		float[] mx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int i = 0; i < n; i++) {
			for (int k = 0; k < 3; k++) {
				float v = p[i * 3 + k];
				if (Float.isNaN(v) || Float.isInfinite(v)) throw new UnsupportedFormatException("The model contains invalid numbers");
				mn[k] = Math.min(mn[k], v);
				mx[k] = Math.max(mx[k], v);
			}
		}
		double longest = Math.max(mx[0] - mn[0], Math.max(mx[1] - mn[1], mx[2] - mn[2]));
		if (longest <= 1e-9) throw new UnsupportedFormatException("The model has no size");
		float cx = (mn[0] + mx[0]) / 2, cy = (mn[1] + mx[1]) / 2, cz = (mn[2] + mx[2]) / 2;

		JetModel.Group g = new JetModel.Group();
		g.v = new float[n * 8];
		g.idx = new int[n];
		g.col = c;
		g.triPart = m.partIds();
		for (int t = 0; t < n; t += 3) {
			float ax = p[t * 3] - cx, ay = p[t * 3 + 1] - cy, az = p[t * 3 + 2] - cz;
			float bx = p[t * 3 + 3] - cx, by = p[t * 3 + 4] - cy, bz = p[t * 3 + 5] - cz;
			float qx = p[t * 3 + 6] - cx, qy = p[t * 3 + 7] - cy, qz = p[t * 3 + 8] - cz;
			float ux = bx - ax, uy = by - ay, uz = bz - az;
			float vx = qx - ax, vy = qy - ay, vz = qz - az;
			float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-12f) { nx = 0; ny = 1; nz = 0; } else { nx /= len; ny /= len; nz /= len; }
			float[][] vs = {{ax, ay, az}, {bx, by, bz}, {qx, qy, qz}};
			for (int k = 0; k < 3; k++) {
				int o = (t + k) * 8;
				g.v[o] = vs[k][0]; g.v[o + 1] = vs[k][1]; g.v[o + 2] = vs[k][2];
				g.v[o + 3] = nx; g.v[o + 4] = ny; g.v[o + 5] = nz;
				g.v[o + 6] = 0.5f; g.v[o + 7] = 0.5f;
				g.idx[t + k] = t + k;
			}
		}
		return new LoadedModel(name, false, new JetModel.Group[] {g}, new java.util.ArrayList<>(m.partNames()), longest);
	}
}
