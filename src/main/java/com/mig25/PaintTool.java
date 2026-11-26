package com.mig25;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Paint state shared by the editor: current colour (HSV), brush options and the undo history. */
public final class PaintTool {
	public static final int[] PRESETS = {
		0xE53935, 0xFB8C00, 0xFDD835, 0x7CB342, 0x00897B, 0x1E88E5,
		0x3949AB, 0x8E24AA, 0xD81B60, 0x6D4C41, 0x546E7A, 0x263238,
		0xF5F5F5, 0xB0BEC5, 0x78909C, 0x000000
	};

	public static float hue = 0.58f, sat = 0.7f, val = 0.9f;
	/** 0 = brush, 1 = whole part, 2 = eyedropper */
	public static int mode = 0;
	public static int radius = 14;

	private static final Deque<List<int[]>> undo = new ArrayDeque<>();
	private static List<int[]> stroke;
	private static long lastRebuild;
	private static boolean dirtyRig;

	private PaintTool() {}

	public static int rgb() {
		return hsvToRgb(hue, sat, val);
	}

	public static void setRgb(int rgb) {
		float[] hsv = rgbToHsv(rgb);
		hue = hsv[0]; sat = hsv[1]; val = hsv[2];
	}

	public static int hsvToRgb(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float c = v * s;
		float x = c * (1 - Math.abs((h * 6) % 2 - 1));
		float m = v - c;
		float r, g, b;
		int sector = (int) (h * 6);
		switch (sector) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
		return (ri << 16) | (gi << 8) | bi;
	}

	public static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0) h = 0;
		else if (max == r) h = (((g - b) / d) % 6) / 6f;
		else if (max == g) h = (((b - r) / d) + 2) / 6f;
		else h = (((r - g) / d) + 4) / 6f;
		if (h < 0) h += 1;
		return new float[] {h, max == 0 ? 0 : d / max, max};
	}

	// ------------------------------------------------------------ painting
	public static void beginStroke() {
		if (stroke == null) stroke = new ArrayList<>();
	}

	public static void endStroke() {
		if (stroke != null && !stroke.isEmpty()) {
			undo.push(stroke);
			while (undo.size() > 40) undo.removeLast();
		}
		stroke = null;
		flushRig(true);
		ModelManager.markProfileDirty();
	}

	public static void apply(List<Integer> keys, int rgb) {
		Map<Integer, Integer> paint = ModelManager.profile.paint;
		if (stroke == null) beginStroke();
		for (int key : keys) {
			Integer old = paint.get(key);
			if (old != null && old == rgb) continue;
			stroke.add(new int[] {key, old == null ? -1 : old});
			paint.put(key, rgb);
		}
		dirtyRig = true;
		flushRig(false);
	}

	public static void undo() {
		if (undo.isEmpty()) return;
		Map<Integer, Integer> paint = ModelManager.profile.paint;
		List<int[]> s = undo.pop();
		for (int i = s.size() - 1; i >= 0; i--) {
			int[] e = s.get(i);
			if (e[1] < 0) paint.remove(e[0]);
			else paint.put(e[0], e[1]);
		}
		dirtyRig = true;
		flushRig(true);
		ModelManager.markProfileDirty();
	}

	public static void clearAll() {
		Map<Integer, Integer> paint = ModelManager.profile.paint;
		if (paint.isEmpty()) return;
		List<int[]> s = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : paint.entrySet()) s.add(new int[] {e.getKey(), e.getValue()});
		undo.push(s);
		paint.clear();
		dirtyRig = true;
		flushRig(true);
		ModelManager.markProfileDirty();
	}

	public static boolean canUndo() {
		return !undo.isEmpty();
	}

	/** Rebuilds the in-world model at most ~5 times a second while dragging. */
	public static void flushRig(boolean force) {
		long now = System.currentTimeMillis();
		if (dirtyRig && (force || now - lastRebuild > 200)) {
			dirtyRig = false;
			lastRebuild = now;
			ModelManager.rebuildRigPaintOnly();
		}
	}

	public static void resetHistory() {
		undo.clear();
		stroke = null;
	}
}
