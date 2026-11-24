package com.mig25.loader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Binary and ASCII STL. STL is usually Z-up, so the result is rotated to Y-up. */
final class StlLoader {
	private StlLoader() {}

	static MeshData load(byte[] b) throws UnsupportedFormatException {
		MeshData m = new MeshData();
		boolean binary = false;
		if (b.length >= 84) {
			long cnt = ByteBuffer.wrap(b, 80, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
			binary = 84L + 50L * cnt == b.length;
		}
		int color = MeshData.DEFAULT_GREY;
		if (binary) {
			ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
			int cnt = bb.getInt(80);
			int o = 84;
			for (int i = 0; i < cnt; i++) {
				o += 12; // normal
				float[] v = new float[9];
				for (int k = 0; k < 9; k++) {
					v[k] = bb.getFloat(o);
					o += 4;
				}
				o += 2; // attribute bytes
				m.tri(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], color);
			}
		} else {
			String text = new String(b, StandardCharsets.UTF_8);
			double[] v = new double[9];
			int k = 0;
			for (String line : text.split("\\R")) {
				String s = line.trim();
				if (!s.startsWith("vertex")) continue;
				String[] t = s.split("\\s+");
				if (t.length < 4) continue;
				try {
					v[k * 3] = Double.parseDouble(t[1]);
					v[k * 3 + 1] = Double.parseDouble(t[2]);
					v[k * 3 + 2] = Double.parseDouble(t[3]);
				} catch (NumberFormatException e) {
					throw new UnsupportedFormatException("STL contains an unreadable number");
				}
				k++;
				if (k == 3) {
					m.tri(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], color);
					k = 0;
				}
			}
		}
		if (m.isEmpty()) throw new UnsupportedFormatException("STL file has no triangles");
		m.zUpToYUp();
		return m;
	}
}
