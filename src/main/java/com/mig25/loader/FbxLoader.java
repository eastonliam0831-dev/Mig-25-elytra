package com.mig25.loader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Binary FBX (v7.x). Reads Geometry (vertices + polygons), Model transforms (Lcl Translation / Rotation / Scaling),
 * the Model hierarchy and diffuse colours. ASCII FBX is not supported (re-export as binary FBX or GLB).
 */
final class FbxLoader {
	private static final class Node {
		String name;
		List<Object> props = new ArrayList<>();
		List<Node> kids = new ArrayList<>();

		Node kid(String n) {
			for (Node k : kids) if (k.name.equals(n)) return k;
			return null;
		}
	}

	private final ByteBuffer b;
	private final boolean wide;

	private FbxLoader(byte[] data, boolean wide) {
		this.b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		this.wide = wide;
	}

	static MeshData load(Path file) throws IOException, UnsupportedFormatException {
		byte[] data = Files.readAllBytes(file);
		String head = data.length > 20 ? new String(data, 0, 20, StandardCharsets.ISO_8859_1) : "";
		if (!head.startsWith("Kaydara FBX Binary")) {
			throw new UnsupportedFormatException("ASCII FBX is not supported. Re-export as Binary FBX, or as GLB/OBJ.");
		}
		int version = ByteBuffer.wrap(data, 23, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
		FbxLoader l = new FbxLoader(data, version >= 7500);
		try {
			return l.run();
		} catch (RuntimeException | DataFormatException e) {
			throw new UnsupportedFormatException("Could not read this FBX (" + e.getClass().getSimpleName() + ")");
		}
	}

	private MeshData run() throws UnsupportedFormatException, DataFormatException {
		b.position(27);
		List<Node> top = new ArrayList<>();
		while (b.remaining() > 13) {
			Node n = readNode();
			if (n == null) break;
			top.add(n);
		}
		Node objects = null, conns = null;
		for (Node n : top) {
			if (n.name.equals("Objects")) objects = n;
			else if (n.name.equals("Connections")) conns = n;
		}
		if (objects == null) throw new UnsupportedFormatException("FBX has no Objects section");

		Map<Long, Node> geoms = new HashMap<>();
		Map<Long, Node> models = new HashMap<>();
		Map<Long, Integer> matColors = new HashMap<>();
		for (Node n : objects.kids) {
			if (n.props.isEmpty() || !(n.props.get(0) instanceof Long id)) continue;
			switch (n.name) {
				case "Geometry" -> geoms.put(id, n);
				case "Model" -> models.put(id, n);
				case "Material" -> {
					Node p70 = n.kid("Properties70");
					if (p70 != null) {
						for (Node p : p70.kids) {
							if (p.props.size() >= 7 && "DiffuseColor".equals(p.props.get(0))) {
								matColors.put(id, MeshData.rgb(num(p.props.get(4)), num(p.props.get(5)), num(p.props.get(6))));
							}
						}
					}
				}
				default -> { }
			}
		}
		Map<Long, Long> modelParent = new HashMap<>();
		Map<Long, Long> geomOwner = new HashMap<>();
		Map<Long, Integer> modelColor = new HashMap<>();
		if (conns != null) {
			for (Node c : conns.kids) {
				if (c.props.size() < 3 || !(c.props.get(1) instanceof Long child) || !(c.props.get(2) instanceof Long parent)) continue;
				if (!"OO".equals(c.props.get(0))) continue;
				if (models.containsKey(child) && models.containsKey(parent)) modelParent.put(child, parent);
				else if (geoms.containsKey(child) && models.containsKey(parent)) geomOwner.put(child, parent);
				else if (matColors.containsKey(child) && models.containsKey(parent)) modelColor.put(parent, matColors.get(child));
			}
		}
		MeshData out = new MeshData();
		for (Map.Entry<Long, Node> g : geoms.entrySet()) {
			Node verts = g.getValue().kid("Vertices");
			Node idx = g.getValue().kid("PolygonVertexIndex");
			if (verts == null || idx == null || verts.props.isEmpty() || idx.props.isEmpty()) continue;
			double[] v = toDoubles(verts.props.get(0));
			int[] pi = toInts(idx.props.get(0));
			Long owner = geomOwner.get(g.getKey());
			double[] world = owner == null ? Mat4.identity() : world(owner, models, modelParent, 0);
			int color = owner != null && modelColor.containsKey(owner) ? modelColor.get(owner) : MeshData.DEFAULT_GREY;
			List<Integer> poly = new ArrayList<>();
			for (int raw : pi) {
				boolean end = raw < 0;
				poly.add(end ? -raw - 1 : raw);
				if (end) {
					for (int i = 1; i + 1 < poly.size(); i++) emit(out, v, world, color, poly.get(0), poly.get(i), poly.get(i + 1));
					poly.clear();
				}
			}
		}
		if (out.isEmpty()) throw new UnsupportedFormatException("FBX has no polygon geometry");
		return out;
	}

	private static void emit(MeshData out, double[] v, double[] w, int color, int a, int b, int c) {
		if (a * 3 + 2 >= v.length || b * 3 + 2 >= v.length || c * 3 + 2 >= v.length) return;
		double[] pa = Mat4.apply(w, v[a * 3], v[a * 3 + 1], v[a * 3 + 2]);
		double[] pb = Mat4.apply(w, v[b * 3], v[b * 3 + 1], v[b * 3 + 2]);
		double[] pc = Mat4.apply(w, v[c * 3], v[c * 3 + 1], v[c * 3 + 2]);
		out.tri(pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], pc[0], pc[1], pc[2], color);
	}

	private static double num(Object o) {
		return o instanceof Number n ? n.doubleValue() : 0;
	}

	private double[] world(long id, Map<Long, Node> models, Map<Long, Long> parent, int depth) {
		Node m = models.get(id);
		double[] t = {0, 0, 0}, r = {0, 0, 0}, s = {1, 1, 1};
		Node p70 = m == null ? null : m.kid("Properties70");
		if (p70 != null) {
			for (Node p : p70.kids) {
				if (p.props.size() < 7) continue;
				Object name = p.props.get(0);
				double[] val = {num(p.props.get(4)), num(p.props.get(5)), num(p.props.get(6))};
				if ("Lcl Translation".equals(name)) t = val;
				else if ("Lcl Rotation".equals(name)) r = val;
				else if ("Lcl Scaling".equals(name)) s = val;
			}
		}
		double[] local = Mat4.mul(Mat4.translation(t[0], t[1], t[2]),
				Mat4.mul(Mat4.rotZ(r[2]), Mat4.mul(Mat4.rotY(r[1]), Mat4.mul(Mat4.rotX(r[0]), Mat4.scale(s[0], s[1], s[2])))));
		Long par = parent.get(id);
		if (par == null || depth > 64) return local;
		return Mat4.mul(world(par, models, parent, depth + 1), local);
	}

	// ---- binary reading ----
	private Node readNode() throws DataFormatException {
		long end, numProps;
		if (wide) {
			end = b.getLong();
			numProps = b.getLong();
			b.getLong();
		} else {
			end = b.getInt() & 0xFFFFFFFFL;
			numProps = b.getInt() & 0xFFFFFFFFL;
			b.getInt();
		}
		int nameLen = b.get() & 0xFF;
		if (end == 0) return null;
		byte[] nb = new byte[nameLen];
		b.get(nb);
		Node n = new Node();
		n.name = new String(nb, StandardCharsets.ISO_8859_1);
		for (long i = 0; i < numProps; i++) n.props.add(readProp());
		while (b.position() < end) {
			Node k = readNode();
			if (k == null) break;
			n.kids.add(k);
		}
		b.position((int) end);
		return n;
	}

	private Object readProp() throws DataFormatException {
		char t = (char) (b.get() & 0xFF);
		switch (t) {
			case 'Y': return (long) b.getShort();
			case 'C': return (long) b.get();
			case 'I': return (long) b.getInt();
			case 'F': return (double) b.getFloat();
			case 'D': return b.getDouble();
			case 'L': return b.getLong();
			case 'S': {
				byte[] s = new byte[b.getInt()];
				b.get(s);
				return new String(s, StandardCharsets.ISO_8859_1);
			}
			case 'R': {
				byte[] s = new byte[b.getInt()];
				b.get(s);
				return s;
			}
			case 'f': case 'd': case 'l': case 'i': case 'b': {
				int len = b.getInt(), enc = b.getInt(), comp = b.getInt();
				int es = switch (t) { case 'd', 'l' -> 8; case 'b' -> 1; default -> 4; };
				byte[] raw;
				if (enc == 1) {
					byte[] z = new byte[comp];
					b.get(z);
					raw = new byte[len * es];
					Inflater inf = new Inflater();
					inf.setInput(z);
					int off = 0;
					while (off < raw.length && !inf.finished()) {
						int r = inf.inflate(raw, off, raw.length - off);
						if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
						off += r;
					}
					inf.end();
				} else {
					raw = new byte[len * es];
					b.get(raw);
				}
				ByteBuffer rb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
				switch (t) {
					case 'd': { double[] a = new double[len]; for (int i = 0; i < len; i++) a[i] = rb.getDouble(i * 8); return a; }
					case 'f': { double[] a = new double[len]; for (int i = 0; i < len; i++) a[i] = rb.getFloat(i * 4); return a; }
					case 'l': { int[] a = new int[len]; for (int i = 0; i < len; i++) a[i] = (int) rb.getLong(i * 8); return a; }
					case 'i': { int[] a = new int[len]; for (int i = 0; i < len; i++) a[i] = rb.getInt(i * 4); return a; }
					default: return new int[0];
				}
			}
			default:
				throw new DataFormatException("unknown FBX property type " + t);
		}
	}

	private static double[] toDoubles(Object o) {
		return o instanceof double[] d ? d : new double[0];
	}

	private static int[] toInts(Object o) {
		return o instanceof int[] i ? i : new int[0];
	}
}
