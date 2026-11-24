package com.mig25.loader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** 3MF: the 3D/3dmodel.model part. Meshes, components, build items and base-material colours. Z-up. */
final class ThreeMfLoader {
	private final Map<String, Element> objects = new HashMap<>();
	private final Map<String, int[]> baseColors = new HashMap<>(); // basematerials id -> colours
	private final MeshData out = new MeshData();

	private ThreeMfLoader() {}

	static MeshData load(Path file) throws IOException, UnsupportedFormatException {
		byte[] xml = null;
		try (ZipFile zf = new ZipFile(file.toFile())) {
			var en = zf.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				if (e.getName().toLowerCase().endsWith("3dmodel.model")) {
					try (InputStream in = zf.getInputStream(e)) {
						xml = in.readAllBytes();
					}
					break;
				}
			}
		}
		if (xml == null) throw new UnsupportedFormatException("3MF has no 3D/3dmodel.model part");
		Document doc;
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setExpandEntityReferences(false);
			doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
		} catch (Exception e) {
			throw new UnsupportedFormatException("3MF model XML is invalid: " + e.getMessage());
		}
		ThreeMfLoader l = new ThreeMfLoader();
		l.run(doc);
		if (l.out.isEmpty()) throw new UnsupportedFormatException("3MF has no mesh triangles");
		l.out.zUpToYUp();
		return l.out;
	}

	private static double[] doubles(String s) {
		String[] t = s.trim().split("\\s+");
		double[] r = new double[t.length];
		for (int i = 0; i < t.length; i++) r[i] = Double.parseDouble(t[i]);
		return r;
	}

	private static int parseColor(String hex) {
		String h = hex.startsWith("#") ? hex.substring(1) : hex;
		if (h.length() >= 6) {
			int rgb = Integer.parseInt(h.substring(0, 6), 16);
			return 0xFF000000 | rgb;
		}
		return MeshData.DEFAULT_GREY;
	}

	private void run(Document doc) {
		NodeList bms = doc.getElementsByTagName("basematerials");
		for (int i = 0; i < bms.getLength(); i++) {
			Element bm = (Element) bms.item(i);
			NodeList bases = bm.getElementsByTagName("base");
			int[] cols = new int[bases.getLength()];
			for (int k = 0; k < cols.length; k++) {
				String dc = ((Element) bases.item(k)).getAttribute("displaycolor");
				cols[k] = dc.isEmpty() ? MeshData.DEFAULT_GREY : parseColor(dc);
			}
			baseColors.put(bm.getAttribute("id"), cols);
		}
		NodeList objs = doc.getElementsByTagName("object");
		for (int i = 0; i < objs.getLength(); i++) {
			Element o = (Element) objs.item(i);
			objects.put(o.getAttribute("id"), o);
		}
		NodeList items = doc.getElementsByTagName("item");
		if (items.getLength() == 0) {
			for (Element o : new ArrayList<>(objects.values())) emitObject(o, Mat4.identity(), 0);
		} else {
			for (int i = 0; i < items.getLength(); i++) {
				Element it = (Element) items.item(i);
				Element o = objects.get(it.getAttribute("objectid"));
				if (o != null) emitObject(o, transform(it.getAttribute("transform")), 0);
			}
		}
	}

	/** 3MF transform: 12 numbers, row-vector convention. */
	private static double[] transform(String s) {
		if (s == null || s.isBlank()) return Mat4.identity();
		double[] t = doubles(s);
		if (t.length != 12) return Mat4.identity();
		double[] m = Mat4.identity();
		m[0] = t[0]; m[4] = t[1]; m[8] = t[2];
		m[1] = t[3]; m[5] = t[4]; m[9] = t[5];
		m[2] = t[6]; m[6] = t[7]; m[10] = t[8];
		m[3] = t[9]; m[7] = t[10]; m[11] = t[11];
		return m;
	}

	private void emitObject(Element obj, double[] m, int depth) {
		if (depth > 16) return;
		for (Node n = obj.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (!(n instanceof Element e)) continue;
			if (e.getTagName().equals("mesh")) emitMesh(obj, e, m);
			else if (e.getTagName().equals("components")) {
				NodeList comps = e.getElementsByTagName("component");
				for (int i = 0; i < comps.getLength(); i++) {
					Element c = (Element) comps.item(i);
					Element sub = objects.get(c.getAttribute("objectid"));
					if (sub != null) emitObject(sub, Mat4.mul(m, transform(c.getAttribute("transform"))), depth + 1);
				}
			}
		}
	}

	private void emitMesh(Element obj, Element mesh, double[] m) {
		NodeList vs = mesh.getElementsByTagName("vertex");
		double[][] v = new double[vs.getLength()][];
		for (int i = 0; i < v.length; i++) {
			Element e = (Element) vs.item(i);
			v[i] = Mat4.apply(m, Double.parseDouble(e.getAttribute("x")), Double.parseDouble(e.getAttribute("y")),
					Double.parseDouble(e.getAttribute("z")));
		}
		String objPid = obj.getAttribute("pid");
		String objPindex = obj.getAttribute("pindex");
		NodeList ts = mesh.getElementsByTagName("triangle");
		for (int i = 0; i < ts.getLength(); i++) {
			Element t = (Element) ts.item(i);
			int a = Integer.parseInt(t.getAttribute("v1")), b = Integer.parseInt(t.getAttribute("v2")), c = Integer.parseInt(t.getAttribute("v3"));
			if (a >= v.length || b >= v.length || c >= v.length) continue;
			String pid = t.getAttribute("pid").isEmpty() ? objPid : t.getAttribute("pid");
			String p1 = t.getAttribute("p1").isEmpty() ? objPindex : t.getAttribute("p1");
			int color = MeshData.DEFAULT_GREY;
			int[] cols = baseColors.get(pid);
			if (cols != null) {
				int idx = p1.isEmpty() ? 0 : Integer.parseInt(p1);
				if (idx >= 0 && idx < cols.length) color = cols[idx];
			}
			out.tri(v[a][0], v[a][1], v[a][2], v[b][0], v[b][1], v[b][2], v[c][0], v[c][1], v[c][2], color);
		}
	}
}
