package com.mig25.loader;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/** glTF 2.0 (.gltf with .bin / data URIs, and .glb). Triangles only; materials become flat colours. */
final class GltfLoader {
	private final Map<String, Object> json;
	private final byte[][] buffers;
	private final MeshData mesh = new MeshData();

	private GltfLoader(Map<String, Object> json, byte[][] buffers) {
		this.json = json;
		this.buffers = buffers;
	}

	static MeshData load(Path file) throws IOException, UnsupportedFormatException {
		byte[] data = Files.readAllBytes(file);
		String jsonText;
		byte[] glbBin = null;
		if (data.length > 12 && data[0] == 'g' && data[1] == 'l' && data[2] == 'T' && data[3] == 'F') {
			ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
			int off = 12;
			String js = null;
			while (off + 8 <= data.length) {
				int len = bb.getInt(off);
				int type = bb.getInt(off + 4);
				if (type == 0x4E4F534A) js = new String(data, off + 8, len, StandardCharsets.UTF_8);
				else if (type == 0x004E4942 && glbBin == null) {
					glbBin = new byte[len];
					System.arraycopy(data, off + 8, glbBin, 0, len);
				}
				off += 8 + len;
			}
			if (js == null) throw new UnsupportedFormatException("GLB has no JSON chunk");
			jsonText = js;
		} else {
			jsonText = new String(data, StandardCharsets.UTF_8);
		}
		Map<String, Object> json = Json.obj(Json.parse(jsonText));
		if (json == null) throw new UnsupportedFormatException("Not a valid glTF file");
		List<Object> bl = Json.arr(json.get("buffers"));
		byte[][] bufs = new byte[bl == null ? 0 : bl.size()][];
		for (int i = 0; i < bufs.length; i++) {
			Map<String, Object> b = Json.obj(bl.get(i));
			Object uri = b.get("uri");
			if (uri == null) bufs[i] = glbBin;
			else bufs[i] = readUri((String) uri, file);
		}
		GltfLoader g = new GltfLoader(json, bufs);
		g.run();
		if (g.mesh.isEmpty()) {
			throw new UnsupportedFormatException("glTF has no triangle meshes (Draco/meshopt compression is not supported - export without compression)");
		}
		return g.mesh;
	}

	private static byte[] readUri(String uri, Path file) throws IOException {
		if (uri.startsWith("data:")) {
			int comma = uri.indexOf(',');
			return Base64.getDecoder().decode(uri.substring(comma + 1));
		}
		return Files.readAllBytes(file.resolveSibling(URLDecoder.decode(uri, StandardCharsets.UTF_8)));
	}

	private void run() throws UnsupportedFormatException {
		List<Object> scenes = Json.arr(json.get("scenes"));
		List<Object> nodes = Json.arr(json.get("nodes"));
		if (nodes == null) throw new UnsupportedFormatException("glTF has no nodes");
		List<Object> roots = null;
		if (scenes != null && !scenes.isEmpty()) {
			Map<String, Object> sc = Json.obj(scenes.get(Math.min(scenes.size() - 1, Json.intv(json.get("scene"), 0))));
			roots = Json.arr(sc.get("nodes"));
		}
		if (roots == null) {
			for (int i = 0; i < nodes.size(); i++) walk(i, Mat4.identity(), 0, "mesh");
		} else {
			for (Object r : roots) walk(Json.intv(r, 0), Mat4.identity(), 0, "mesh");
		}
	}

	private static final java.util.regex.Pattern GENERIC = java.util.regex.Pattern.compile("^(Object|Mesh|Node|Cube|Sketchfab_model|root)[_.]?\\d*$");

	private static String clean(String name) {
		return name.replaceAll("_\\d+$", "");
	}

	private void walk(int idx, double[] parent, int depth, String inherited) throws UnsupportedFormatException {
		if (depth > 64) return;
		List<Object> nodes = Json.arr(json.get("nodes"));
		Map<String, Object> n = Json.obj(nodes.get(idx));
		double[] local;
		double[] mtx = Json.nums(n.get("matrix"));
		if (mtx != null && mtx.length == 16) {
			local = Mat4.fromColumnMajor(mtx);
		} else {
			double[] t = Json.nums(n.get("translation"));
			double[] q = Json.nums(n.get("rotation"));
			double[] s = Json.nums(n.get("scale"));
			local = Mat4.fromQuatTRS(t != null ? t : new double[] {0, 0, 0}, q != null ? q : new double[] {0, 0, 0, 1},
					s != null ? s : new double[] {1, 1, 1});
		}
		double[] world = Mat4.mul(parent, local);
		String own = n.get("name") instanceof String nn ? nn : "";
		String eff = own.isBlank() || GENERIC.matcher(own).matches() ? inherited : clean(own);
		if (n.get("mesh") != null) addMesh(Json.intv(n.get("mesh"), 0), world, eff);
		List<Object> kids = Json.arr(n.get("children"));
		if (kids != null) for (Object k : kids) walk(Json.intv(k, 0), world, depth + 1, eff);
	}

	private void addMesh(int meshIdx, double[] world, String nodeName) throws UnsupportedFormatException {
		Map<String, Object> mesh = Json.obj(Json.arr(json.get("meshes")).get(meshIdx));
		List<Object> prims = Json.arr(mesh.get("primitives"));
		if (prims == null) return;
		int primNo = 0;
		for (Object po : prims) {
			Map<String, Object> p = Json.obj(po);
			if (Json.intv(p.get("mode"), 4) != 4) continue;
			Map<String, Object> attrs = Json.obj(p.get("attributes"));
			if (attrs == null || attrs.get("POSITION") == null) continue;
			float[] pos = readFloats(Json.intv(attrs.get("POSITION"), 0));
			if (pos == null) continue;
			int[] idx;
			if (p.get("indices") != null) {
				idx = readIndices(Json.intv(p.get("indices"), 0));
				if (idx == null) continue;
			} else {
				idx = new int[pos.length / 3];
				for (int i = 0; i < idx.length; i++) idx[i] = i;
			}
			int color = materialColor(p.get("material"));
			String base = nodeName != null && !nodeName.isBlank() ? nodeName
					: (mesh.get("name") instanceof String mn && !mn.isBlank() ? mn : "mesh" + meshIdx);
			this.mesh.setPart(prims.size() > 1 ? base + "#" + primNo : base);
			primNo++;
			double[] a, b, c;
			for (int i = 0; i + 2 < idx.length; i += 3) {
				int i0 = idx[i] * 3, i1 = idx[i + 1] * 3, i2 = idx[i + 2] * 3;
				if (i0 + 2 >= pos.length || i1 + 2 >= pos.length || i2 + 2 >= pos.length) continue;
				a = Mat4.apply(world, pos[i0], pos[i0 + 1], pos[i0 + 2]);
				b = Mat4.apply(world, pos[i1], pos[i1 + 1], pos[i1 + 2]);
				c = Mat4.apply(world, pos[i2], pos[i2 + 1], pos[i2 + 2]);
				this.mesh.tri(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], color);
			}
		}
	}

	private int materialColor(Object matIdx) {
		try {
			if (matIdx == null) return MeshData.DEFAULT_GREY;
			Map<String, Object> mat = Json.obj(Json.arr(json.get("materials")).get(Json.intv(matIdx, 0)));
			Map<String, Object> pbr = Json.obj(mat.get("pbrMetallicRoughness"));
			double[] f = pbr == null ? null : Json.nums(pbr.get("baseColorFactor"));
			double r = f != null ? f[0] : 1, g = f != null ? f[1] : 1, b = f != null ? f[2] : 1;
			if (pbr != null && pbr.get("baseColorTexture") != null) {
				double[] avg = averageTexture(Json.intv(Json.obj(pbr.get("baseColorTexture")).get("index"), 0));
				if (avg != null) { r *= avg[0]; g *= avg[1]; b *= avg[2]; }
			} else if (pbr == null) {
				r = g = b = 0.72;
			}
			return MeshData.rgb(r, g, b);
		} catch (RuntimeException e) {
			return MeshData.DEFAULT_GREY;
		}
	}

	/** Average colour of the texture (we do not upload textures, only tint with their average). */
	private double[] averageTexture(int texIdx) {
		try {
			Map<String, Object> tex = Json.obj(Json.arr(json.get("textures")).get(texIdx));
			Map<String, Object> img = Json.obj(Json.arr(json.get("images")).get(Json.intv(tex.get("source"), 0)));
			byte[] bytes;
			if (img.get("bufferView") != null) {
				Map<String, Object> bv = Json.obj(Json.arr(json.get("bufferViews")).get(Json.intv(img.get("bufferView"), 0)));
				byte[] buf = buffers[Json.intv(bv.get("buffer"), 0)];
				int off = Json.intv(bv.get("byteOffset"), 0), len = Json.intv(bv.get("byteLength"), 0);
				bytes = java.util.Arrays.copyOfRange(buf, off, off + len);
			} else if (img.get("uri") instanceof String u && u.startsWith("data:")) {
				bytes = Base64.getDecoder().decode(u.substring(u.indexOf(',') + 1));
			} else {
				return null;
			}
			BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
			if (bi == null) return null;
			long r = 0, g = 0, b = 0, cnt = 0;
			int sx = Math.max(1, bi.getWidth() / 48), sy = Math.max(1, bi.getHeight() / 48);
			for (int y = 0; y < bi.getHeight(); y += sy) {
				for (int x = 0; x < bi.getWidth(); x += sx) {
					int p = bi.getRGB(x, y);
					if (((p >>> 24) & 0xFF) < 16) continue;
					r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; cnt++;
				}
			}
			if (cnt == 0) return null;
			return new double[] {r / (255.0 * cnt), g / (255.0 * cnt), b / (255.0 * cnt)};
		} catch (Exception | LinkageError e) {
			return null;
		}
	}

	private float[] readFloats(int accIdx) {
		Map<String, Object> acc = Json.obj(Json.arr(json.get("accessors")).get(accIdx));
		if (acc.get("bufferView") == null || acc.get("sparse") != null) return null;
		int nc = comps(String.valueOf(acc.get("type")));
		int count = Json.intv(acc.get("count"), 0);
		int ct = Json.intv(acc.get("componentType"), 5126);
		boolean norm = Boolean.TRUE.equals(acc.get("normalized"));
		float[] out = new float[count * nc];
		ByteBuffer bb = view(acc);
		int stride = stride(acc, nc, ct);
		int base = Json.intv(acc.get("byteOffset"), 0) + viewOffset(acc);
		for (int i = 0; i < count; i++) {
			for (int k = 0; k < nc; k++) {
				int o = base + i * stride + k * size(ct);
				out[i * nc + k] = (float) read(bb, o, ct, norm);
			}
		}
		return out;
	}

	private int[] readIndices(int accIdx) {
		Map<String, Object> acc = Json.obj(Json.arr(json.get("accessors")).get(accIdx));
		if (acc.get("bufferView") == null) return null;
		int count = Json.intv(acc.get("count"), 0);
		int ct = Json.intv(acc.get("componentType"), 5125);
		int[] out = new int[count];
		ByteBuffer bb = view(acc);
		int stride = stride(acc, 1, ct);
		int base = Json.intv(acc.get("byteOffset"), 0) + viewOffset(acc);
		for (int i = 0; i < count; i++) out[i] = (int) read(bb, base + i * stride, ct, false);
		return out;
	}

	private Map<String, Object> bufferView(Map<String, Object> acc) {
		return Json.obj(Json.arr(json.get("bufferViews")).get(Json.intv(acc.get("bufferView"), 0)));
	}

	private int viewOffset(Map<String, Object> acc) {
		return Json.intv(bufferView(acc).get("byteOffset"), 0);
	}

	private ByteBuffer view(Map<String, Object> acc) {
		byte[] buf = buffers[Json.intv(bufferView(acc).get("buffer"), 0)];
		return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
	}

	private int stride(Map<String, Object> acc, int nc, int ct) {
		int s = Json.intv(bufferView(acc).get("byteStride"), 0);
		return s > 0 ? s : nc * size(ct);
	}

	private static int comps(String t) {
		return switch (t) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4" -> 4;
			case "MAT4" -> 16;
			default -> 1;
		};
	}

	private static int size(int ct) {
		return switch (ct) {
			case 5120, 5121 -> 1;
			case 5122, 5123 -> 2;
			default -> 4;
		};
	}

	private static double read(ByteBuffer bb, int o, int ct, boolean norm) {
		switch (ct) {
			case 5126: return bb.getFloat(o);
			case 5125: return bb.getInt(o) & 0xFFFFFFFFL;
			case 5123: { int v = bb.getShort(o) & 0xFFFF; return norm ? v / 65535.0 : v; }
			case 5122: { int v = bb.getShort(o); return norm ? Math.max(v / 32767.0, -1) : v; }
			case 5121: { int v = bb.get(o) & 0xFF; return norm ? v / 255.0 : v; }
			case 5120: { int v = bb.get(o); return norm ? Math.max(v / 127.0, -1) : v; }
			default: return 0;
		}
	}
}
