package com.mig25.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** USDZ containing a text USD layer (.usda). Binary .usdc crate files are not supported. */
final class UsdzLoader {
	private UsdzLoader() {}

	private static final Pattern POINTS = Pattern.compile("point3f\\[\\]\\s+points\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL);
	private static final Pattern COUNTS = Pattern.compile("int\\[\\]\\s+faceVertexCounts\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL);
	private static final Pattern INDICES = Pattern.compile("int\\[\\]\\s+faceVertexIndices\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL);
	private static final Pattern TUPLE = Pattern.compile("\\(([^)]*)\\)");

	static MeshData load(Path file) throws IOException, UnsupportedFormatException {
		String text = null;
		boolean sawCrate = false;
		try (ZipFile zf = new ZipFile(file.toFile())) {
			var en = zf.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				String n = e.getName().toLowerCase();
				if (n.endsWith(".usda")) {
					try (InputStream in = zf.getInputStream(e)) {
						text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
					}
					break;
				}
				if (n.endsWith(".usdc") || n.endsWith(".usd")) {
					try (InputStream in = zf.getInputStream(e)) {
						byte[] head = in.readNBytes(8);
						String h = new String(head, StandardCharsets.ISO_8859_1);
						if (h.startsWith("PXR-USDC")) sawCrate = true;
						else {
							text = h + new String(in.readAllBytes(), StandardCharsets.UTF_8);
							break;
						}
					}
				}
			}
		}
		if (text == null) {
			throw new UnsupportedFormatException(sawCrate
					? "This USDZ stores its geometry in binary .usdc, which cannot be read. Export it as GLB (Blender, Reality Converter) instead."
					: "No USD geometry found inside this USDZ.");
		}
		MeshData out = new MeshData();
		for (String block : text.split("def Mesh")) {
			Matcher mp = POINTS.matcher(block), mc = COUNTS.matcher(block), mi = INDICES.matcher(block);
			if (!mp.find() || !mc.find() || !mi.find()) continue;
			Matcher nm = Pattern.compile("^\\s*\"([^\"]*)\"").matcher(block);
			out.setPart(nm.find() ? nm.group(1) : "mesh");
			List<double[]> pts = new ArrayList<>();
			Matcher t = TUPLE.matcher(mp.group(1));
			while (t.find()) {
				String[] p = t.group(1).split(",");
				if (p.length >= 3) pts.add(new double[] {Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim())});
			}
			int[] counts = ints(mc.group(1)), idx = ints(mi.group(1));
			int cur = 0;
			for (int cnt : counts) {
				for (int i = 1; i + 1 < cnt && cur + i + 1 < idx.length; i++) {
					double[] a = pts.get(idx[cur]), b = pts.get(idx[cur + i]), c = pts.get(idx[cur + i + 1]);
					out.tri(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], MeshData.DEFAULT_GREY);
				}
				cur += cnt;
			}
		}
		if (out.isEmpty()) throw new UnsupportedFormatException("Could not find mesh data in this USDZ. Export it as GLB instead.");
		return out;
	}

	private static int[] ints(String s) {
		String[] t = s.replace("\n", " ").split(",");
		List<Integer> l = new ArrayList<>();
		for (String x : t) {
			String y = x.trim();
			if (!y.isEmpty()) l.add(Integer.parseInt(y));
		}
		int[] r = new int[l.size()];
		for (int i = 0; i < r.length; i++) r[i] = l.get(i);
		return r;
	}
}
