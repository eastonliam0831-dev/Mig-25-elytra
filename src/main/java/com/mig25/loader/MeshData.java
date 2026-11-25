package com.mig25.loader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A bag of triangles (3 vertices each) with one colour per vertex. Loaders fill this, ModelBake consumes it. */
public final class MeshData {
	private float[] pos = new float[9 * 256];
	private int[] col = new int[3 * 256];
	private int n; // vertex count (always a multiple of 3)
	private int[] partOfTri = new int[256];
	private final List<String> partNames = new ArrayList<>();
	private int currentPart = -1;

	/** Starts a named sub-mesh ("part"); following triangles belong to it. Same name = same part. */
	public void setPart(String name) {
		String nm = (name == null || name.isBlank()) ? "mesh" : name.trim();
		int idx = partNames.indexOf(nm);
		if (idx < 0) {
			partNames.add(nm);
			idx = partNames.size() - 1;
		}
		currentPart = idx;
	}

	public List<String> partNames() {
		return partNames;
	}

	/** Part id for each triangle. */
	public int[] partIds() {
		return Arrays.copyOf(partOfTri, n / 3);
	}

	public int vertexCount() {
		return n;
	}

	public int triangleCount() {
		return n / 3;
	}

	public boolean isEmpty() {
		return n == 0;
	}

	public void tri(double ax, double ay, double az, double bx, double by, double bz,
			double cx, double cy, double cz, int argb) {
		if (n + 3 > col.length) {
			int cap = col.length * 2;
			pos = Arrays.copyOf(pos, cap * 3);
			col = Arrays.copyOf(col, cap);
		}
		if (currentPart < 0) setPart("mesh");
		if (n / 3 >= partOfTri.length) partOfTri = Arrays.copyOf(partOfTri, partOfTri.length * 2);
		partOfTri[n / 3] = currentPart;
		int p = n * 3;
		pos[p] = (float) ax; pos[p + 1] = (float) ay; pos[p + 2] = (float) az;
		pos[p + 3] = (float) bx; pos[p + 4] = (float) by; pos[p + 5] = (float) bz;
		pos[p + 6] = (float) cx; pos[p + 7] = (float) cy; pos[p + 8] = (float) cz;
		col[n] = argb; col[n + 1] = argb; col[n + 2] = argb;
		n += 3;
	}

	public float[] positions() {
		return Arrays.copyOf(pos, n * 3);
	}

	public int[] colors() {
		return Arrays.copyOf(col, n);
	}

	/** Rotates every vertex from Z-up to Y-up: (x, y, z) -> (x, z, -y). */
	public void zUpToYUp() {
		for (int i = 0; i < n; i++) {
			float y = pos[i * 3 + 1];
			float z = pos[i * 3 + 2];
			pos[i * 3 + 1] = z;
			pos[i * 3 + 2] = -y;
		}
	}

	public static int rgb(double r, double g, double b) {
		int ri = (int) Math.round(Math.max(0, Math.min(1, r)) * 255);
		int gi = (int) Math.round(Math.max(0, Math.min(1, g)) * 255);
		int bi = (int) Math.round(Math.max(0, Math.min(1, b)) * 255);
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	public static final int DEFAULT_GREY = 0xFFB8BCC4;
}
