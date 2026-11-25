package com.mig25.loader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Collada (.dae): geometry only (triangles / polylist / polygons), diffuse colours, up-axis. Node transforms are ignored. */
final class DaeLoader {
	private DaeLoader() {}

	private static double[] doubles(String s) {
		String[] t = s.trim().split("\\s+");
		double[] r = new double[t.length];
		for (int i = 0; i < t.length; i++) r[i] = t[i].isEmpty() ? 0 : Double.parseDouble(t[i]);
		return r;
	}

	private static int[] ints(String s) {
		if (s == null || s.isBlank()) return new int[0];
		String[] t = s.trim().split("\\s+");
		int[] r = new int[t.length];
		for (int i = 0; i < t.length; i++) r[i] = Integer.parseInt(t[i]);
		return r;
	}

	private static String id(String ref) {
		return ref != null && ref.startsWith("#") ? ref.substring(1) : ref;
	}

	private static Element child(Element e, String name) {
		for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element c && c.getTagName().equals(name)) return c;
		}
		return null;
	}

	static MeshData load(Path file) throws IOException, UnsupportedFormatException {
		Document doc;
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setExpandEntityReferences(false);
			try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) { }
			DocumentBuilder b = f.newDocumentBuilder();
			doc = b.parse(new ByteArrayInputStream(Files.readAllBytes(file)));
		} catch (Exception e) {
			throw new UnsupportedFormatException("DAE is not valid XML: " + e.getMessage());
		}
		boolean zUp = false;
		NodeList up = doc.getElementsByTagName("up_axis");
		if (up.getLength() > 0 && up.item(0).getTextContent().trim().equalsIgnoreCase("Z_UP")) zUp = true;

		// effect id -> colour
		Map<String, Integer> effectColor = new HashMap<>();
		NodeList effects = doc.getElementsByTagName("effect");
		for (int i = 0; i < effects.getLength(); i++) {
			Element e = (Element) effects.item(i);
			NodeList diff = e.getElementsByTagName("diffuse");
			if (diff.getLength() > 0) {
				NodeList col = ((Element) diff.item(0)).getElementsByTagName("color");
				if (col.getLength() > 0) {
					double[] c = doubles(col.item(0).getTextContent());
					if (c.length >= 3) effectColor.put(e.getAttribute("id"), MeshData.rgb(c[0], c[1], c[2]));
				}
			}
		}
		Map<String, Integer> matColor = new HashMap<>();
		NodeList mats = doc.getElementsByTagName("material");
		for (int i = 0; i < mats.getLength(); i++) {
			Element m = (Element) mats.item(i);
			NodeList ie = m.getElementsByTagName("instance_effect");
			if (ie.getLength() == 0) continue;
			Integer c = effectColor.get(id(((Element) ie.item(0)).getAttribute("url")));
			if (c != null) {
				matColor.put(m.getAttribute("id"), c);
				if (!m.getAttribute("name").isEmpty()) matColor.put(m.getAttribute("name"), c);
			}
		}

		MeshData out = new MeshData();
		NodeList meshes = doc.getElementsByTagName("mesh");
		for (int mi = 0; mi < meshes.getLength(); mi++) {
			Element mesh = (Element) meshes.item(mi);
			if (mesh.getParentNode() instanceof Element ge) {
				String gn = ge.getAttribute("name").isEmpty() ? ge.getAttribute("id") : ge.getAttribute("name");
				out.setPart(gn);
			}
			Map<String, double[]> sources = new HashMap<>();
			Map<String, Integer> strides = new HashMap<>();
			for (Node n = mesh.getFirstChild(); n != null; n = n.getNextSibling()) {
				if (n instanceof Element s && s.getTagName().equals("source")) {
					Element fa = child(s, "float_array");
					if (fa == null) continue;
					sources.put(s.getAttribute("id"), doubles(fa.getTextContent()));
					int stride = 3;
					NodeList acc = s.getElementsByTagName("accessor");
					if (acc.getLength() > 0 && !((Element) acc.item(0)).getAttribute("stride").isEmpty()) {
						stride = Integer.parseInt(((Element) acc.item(0)).getAttribute("stride"));
					}
					strides.put(s.getAttribute("id"), stride);
				}
			}
			// <vertices id=..><input semantic="POSITION" source="#..."/>
			Map<String, String> vertexToPos = new HashMap<>();
			Element vertices = child(mesh, "vertices");
			if (vertices != null) {
				NodeList ins = vertices.getElementsByTagName("input");
				for (int i = 0; i < ins.getLength(); i++) {
					Element in = (Element) ins.item(i);
					if (in.getAttribute("semantic").equals("POSITION")) vertexToPos.put(vertices.getAttribute("id"), id(in.getAttribute("source")));
				}
			}
			for (Node n = mesh.getFirstChild(); n != null; n = n.getNextSibling()) {
				if (!(n instanceof Element prim)) continue;
				String tag = prim.getTagName();
				if (!tag.equals("triangles") && !tag.equals("polylist") && !tag.equals("polygons")) continue;
				int vOff = 0, maxOff = 0;
				String posSource = null;
				NodeList ins = prim.getElementsByTagName("input");
				for (int i = 0; i < ins.getLength(); i++) {
					Element in = (Element) ins.item(i);
					int off = in.getAttribute("offset").isEmpty() ? 0 : Integer.parseInt(in.getAttribute("offset"));
					maxOff = Math.max(maxOff, off);
					if (in.getAttribute("semantic").equals("VERTEX")) {
						vOff = off;
						String src = id(in.getAttribute("source"));
						posSource = vertexToPos.getOrDefault(src, src);
					}
				}
				double[] pos = posSource == null ? null : sources.get(posSource);
				if (pos == null) continue;
				int pStride = strides.getOrDefault(posSource, 3);
				int stride = maxOff + 1;
				String matSym = prim.getAttribute("material");
				int color = matColor.containsKey(matSym) ? matColor.get(matSym)
						: (matColor.size() == 1 ? matColor.values().iterator().next() : MeshData.DEFAULT_GREY);

				if (tag.equals("triangles")) {
					Element pe = child(prim, "p");
					if (pe == null) continue;
					int[] p = ints(pe.getTextContent());
					for (int i = 0; i + 3 * stride <= p.length; i += 3 * stride) {
						emit(out, pos, pStride, color, p[i + vOff], p[i + stride + vOff], p[i + 2 * stride + vOff]);
					}
				} else if (tag.equals("polylist")) {
					Element vc = child(prim, "vcount");
					Element pe = child(prim, "p");
					if (vc == null || pe == null) continue;
					int[] counts = ints(vc.getTextContent());
					int[] p = ints(pe.getTextContent());
					int cur = 0;
					for (int cnt : counts) {
						polygon(out, pos, pStride, color, p, cur, cnt, stride, vOff);
						cur += cnt * stride;
					}
				} else {
					NodeList ps = prim.getElementsByTagName("p");
					for (int i = 0; i < ps.getLength(); i++) {
						int[] p = ints(ps.item(i).getTextContent());
						polygon(out, pos, pStride, color, p, 0, p.length / stride, stride, vOff);
					}
				}
			}
		}
		if (out.isEmpty()) throw new UnsupportedFormatException("DAE has no triangle geometry");
		if (zUp) out.zUpToYUp();
		return out;
	}

	private static void polygon(MeshData out, double[] pos, int pStride, int color, int[] p, int start, int cnt, int stride, int vOff) {
		for (int i = 1; i + 1 < cnt; i++) {
			int a = start + vOff, b = start + i * stride + vOff, c = start + (i + 1) * stride + vOff;
			if (c >= p.length) return;
			emit(out, pos, pStride, color, p[a], p[b], p[c]);
		}
	}

	private static void emit(MeshData out, double[] pos, int s, int color, int a, int b, int c) {
		if (a * s + 2 >= pos.length || b * s + 2 >= pos.length || c * s + 2 >= pos.length || a < 0 || b < 0 || c < 0) return;
		out.tri(pos[a * s], pos[a * s + 1], pos[a * s + 2], pos[b * s], pos[b * s + 1], pos[b * s + 2],
				pos[c * s], pos[c * s + 1], pos[c * s + 2], color);
	}
}
