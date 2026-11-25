package com.mig25.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Wavefront OBJ with optional MTL (diffuse colour only). */
final class ObjLoader {
	private ObjLoader() {}

	private static Map<String, Integer> loadMtl(Path mtl) {
		Map<String, Integer> out = new HashMap<>();
		try {
			String cur = null;
			for (String line : Files.readAllLines(mtl, StandardCharsets.UTF_8)) {
				String s = line.trim();
				if (s.startsWith("newmtl ")) {
					cur = s.substring(7).trim();
				} else if (cur != null && s.startsWith("Kd ")) {
					String[] t = s.split("\\s+");
					if (t.length >= 4) {
						out.put(cur, MeshData.rgb(Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3])));
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			// colours are optional
		}
		return out;
	}

	private static int index(String tok, int count) {
		int slash = tok.indexOf('/');
		String v = slash >= 0 ? tok.substring(0, slash) : tok;
		int i = Integer.parseInt(v);
		return i > 0 ? i - 1 : count + i;
	}

	static MeshData load(Path file) throws IOException, UnsupportedFormatException {
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		MeshData m = new MeshData();
		List<double[]> verts = new ArrayList<>();
		Map<String, Integer> mats = new HashMap<>();
		int color = MeshData.DEFAULT_GREY;
		for (String line : lines) {
			String s = line.trim();
			if (s.isEmpty() || s.charAt(0) == '#') continue;
			String[] t = s.split("\\s+");
			try {
				switch (t[0]) {
					case "v" -> verts.add(new double[] {Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3])});
					case "mtllib" -> {
						if (t.length > 1) {
							Path p = file.resolveSibling(s.substring(7).trim());
							if (Files.exists(p)) mats.putAll(loadMtl(p));
						}
					}
					case "o", "g" -> m.setPart(t.length > 1 ? s.substring(t[0].length()).trim() : "mesh");
					case "usemtl" -> color = t.length > 1 && mats.containsKey(t[1]) ? mats.get(t[1]) : MeshData.DEFAULT_GREY;
					case "f" -> {
						int cnt = t.length - 1;
						if (cnt < 3) break;
						double[][] p = new double[cnt][];
						for (int i = 0; i < cnt; i++) p[i] = verts.get(index(t[i + 1], verts.size()));
						for (int i = 1; i + 1 < cnt; i++) {
							m.tri(p[0][0], p[0][1], p[0][2], p[i][0], p[i][1], p[i][2], p[i + 1][0], p[i + 1][1], p[i + 1][2], color);
						}
					}
					default -> { }
				}
			} catch (RuntimeException e) {
				throw new UnsupportedFormatException("OBJ has a malformed line: " + s);
			}
		}
		if (m.isEmpty()) throw new UnsupportedFormatException("OBJ file has no faces");
		return m;
	}
}
