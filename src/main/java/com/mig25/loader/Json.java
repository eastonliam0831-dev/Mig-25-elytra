package com.mig25.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser (objects -> Map, arrays -> List, numbers -> Double). No dependencies. */
public final class Json {
	private final String s;
	private int i;

	private Json(String s) {
		this.s = s;
	}

	public static Object parse(String text) {
		Json j = new Json(text);
		j.ws();
		Object v = j.value();
		return v;
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
	}

	private Object value() {
		ws();
		char c = s.charAt(i);
		if (c == '{') return object();
		if (c == '[') return array();
		if (c == '"') return string();
		if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
		if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
		if (s.startsWith("null", i)) { i += 4; return null; }
		int st = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
		return Double.valueOf(s.substring(st, i));
	}

	private Map<String, Object> object() {
		Map<String, Object> m = new LinkedHashMap<>();
		i++; ws();
		if (s.charAt(i) == '}') { i++; return m; }
		while (true) {
			ws();
			String k = string();
			ws(); i++; // ':'
			m.put(k, value());
			ws();
			char c = s.charAt(i++);
			if (c == '}') return m;
		}
	}

	private List<Object> array() {
		List<Object> l = new ArrayList<>();
		i++; ws();
		if (s.charAt(i) == ']') { i++; return l; }
		while (true) {
			l.add(value());
			ws();
			char c = s.charAt(i++);
			if (c == ']') return l;
		}
	}

	private String string() {
		StringBuilder b = new StringBuilder();
		i++; // opening quote
		while (true) {
			char c = s.charAt(i++);
			if (c == '"') return b.toString();
			if (c == '\\') {
				char e = s.charAt(i++);
				switch (e) {
					case 'n' -> b.append('\n');
					case 't' -> b.append('\t');
					case 'r' -> b.append('\r');
					case 'b' -> b.append('\b');
					case 'f' -> b.append('\f');
					case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
					default -> b.append(e);
				}
			} else {
				b.append(c);
			}
		}
	}

	// ---- typed helpers ----
	@SuppressWarnings("unchecked")
	public static Map<String, Object> obj(Object o) {
		return o instanceof Map ? (Map<String, Object>) o : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> arr(Object o) {
		return o instanceof List ? (List<Object>) o : null;
	}

	public static int intv(Object o, int def) {
		return o instanceof Double d ? d.intValue() : def;
	}

	public static double num(Object o, double def) {
		return o instanceof Double d ? d : def;
	}

	public static double[] nums(Object o) {
		List<Object> l = arr(o);
		if (l == null) return null;
		double[] r = new double[l.size()];
		for (int k = 0; k < r.length; k++) r[k] = num(l.get(k), 0);
		return r;
	}
}
