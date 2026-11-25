package com.mig25;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * In-game aircraft editor. The panel sits on the left; the aircraft keeps rendering live in the world behind it.
 * Pages: Shape, Flames (afterburners), Flaps (control surfaces) and Gear (landing gear).
 */
public class ModelEditorScreen extends TacticalScreen {
	private static final double[] STEP_MUL = {0.1, 1.0, 5.0};
	private static final String[] STEP_NAMES = {"Fine x0.1", "Normal x1", "Coarse x5"};
	private static final String[] PAGES = {"Shape", "Flames", "Flaps", "Gear"};
	private static final String[] COLOR_NAMES = {"Blue", "Orange", "Red", "White", "Green", "Purple", "Cyan", "Yellow"};
	private static final int[][] COLORS = {
		{70, 140, 255}, {255, 140, 40}, {255, 60, 40}, {235, 240, 255},
		{80, 255, 120}, {180, 90, 255}, {60, 230, 255}, {255, 220, 60}
	};

	private static int page = 0;
	private static int stepIdx = 1;
	private static int selBurner = 0;
	private static int selSurface = 0;
	private static int selGear = 0;

	private static final int X0 = 8;
	private static final int W = 176;
	private static final int ROW = 22;

	private int y;
	private int column;
	private int top;
	private int[] colBottom = new int[3];

	public ModelEditorScreen(Screen parent) {
		super("AeroCraft Aircraft Editor", parent);
	}

	private static double mul() {
		return STEP_MUL[stepIdx];
	}

	private static void changed() {
		ModelManager.saveProfile();
	}

	private void reopen() {
		SettingsScreen.open(this.minecraft, new ModelEditorScreen(parent));
	}

	// ------------------------------------------------------------ flow layout (wraps into a second/third column)
	private int colX() {
		return X0 + column * (W + 8);
	}

	private int nextRow() {
		if (y + ROW > this.height - 8 && column < 2) {
			colBottom[column] = y;
			column++;
			y = top;
		}
		int r = y;
		y += ROW;
		colBottom[column] = y;
		return r;
	}

	private void section(String label) {
		int r = nextRow();
		Ui u = add(colX(), r, W, 20, STYLE_READOUT, () -> label, () -> {});
	}

	private void num(String name, DoubleSupplier get, DoubleConsumer set, double step, String fmt, boolean rebuild) {
		int r = nextRow();
		int x = colX();
		add(x, r, 22, 20, STYLE_SMALL, () -> "-", () -> {
			set.accept(get.getAsDouble() - step * mul());
			changed();
			if (rebuild) ModelManager.rebuildRig();
		});
		add(x + 24, r, W - 48, 20, STYLE_READOUT, () -> name + ": " + String.format(fmt, get.getAsDouble()), () -> {});
		add(x + W - 22, r, 22, 20, STYLE_SMALL, () -> "+", () -> {
			set.accept(get.getAsDouble() + step * mul());
			changed();
			if (rebuild) ModelManager.rebuildRig();
		});
	}

	private void value(String name, Supplier<String> val, Runnable next) {
		int r = nextRow();
		value(colX(), r, W, name, val, next);
	}

	private void wide(String label, int style, Runnable r) {
		int row = nextRow();
		add(colX(), row, W, 20, style, () -> label, r);
	}

	private void splitRow(String[] labels, int[] styles, Runnable[] actions) {
		int row = nextRow();
		int n = labels.length;
		int w = (W - (n - 1) * 4) / n;
		for (int i = 0; i < n; i++) {
			final int idx = i;
			add(colX() + i * (w + 4), row, w, 20, styles[i], () -> labels[idx], actions[i]);
		}
	}

	private static double wrap(double deg) {
		double d = deg % 360.0;
		if (d > 180) d -= 360;
		if (d <= -180) d += 360;
		return d;
	}

	// ------------------------------------------------------------ build
	@Override
	protected void init() {
		JetState.editorOpen = true;
		y = 6;
		column = 0;
		colBottom = new int[3];
		ModelProfile pr = ModelManager.profile;

		int r0 = nextRow();
		add(colX(), r0, W, 20, STYLE_READOUT, () -> "EDITING: " + ModelManager.displayName(ModelManager.currentName).toUpperCase(), () -> {});
		int tabRow = nextRow();
		int tw = (W - 3 * 3) / 4;
		for (int i = 0; i < PAGES.length; i++) {
			final int idx = i;
			Ui t = add(colX() + i * (tw + 3), tabRow, tw, 20, STYLE_TAB, () -> PAGES[idx], () -> {
				if (page != idx) {
					page = idx;
					reopen();
				}
			});
			t.selected = () -> page == idx;
		}
		value("Step", () -> STEP_NAMES[stepIdx], () -> stepIdx = (stepIdx + 1) % STEP_MUL.length);
		top = y;

		switch (page) {
			case 0 -> shapePage(pr);
			case 1 -> flamesPage(pr);
			case 2 -> rigPage(pr, false);
			default -> rigPage(pr, true);
		}

		splitRow(new String[] {"<", ">", "Up", "Dn"}, new int[] {STYLE_SMALL, STYLE_SMALL, STYLE_SMALL, STYLE_SMALL},
				new Runnable[] {
					() -> JetState.previewYaw -= 30f,
					() -> JetState.previewYaw += 30f,
					() -> JetState.previewPitch = Math.max(-80f, JetState.previewPitch - 20f),
					() -> JetState.previewPitch = Math.min(80f, JetState.previewPitch + 20f)});
		wide("Done", STYLE_PRIMARY, this::onClose);
	}

	private void shapePage(ModelProfile pr) {
		num("Size (blocks)", () -> pr.size, v -> pr.size = Math.max(0.5, Math.min(200, v)), 0.5, "%.1f", false);
		num("Rotate X", () -> pr.rotX, v -> pr.rotX = wrap(v), 15, "%.0f", false);
		num("Rotate Y", () -> pr.rotY, v -> pr.rotY = wrap(v), 15, "%.0f", false);
		num("Rotate Z", () -> pr.rotZ, v -> pr.rotZ = wrap(v), 15, "%.0f", false);
		num("Move side", () -> pr.offX, v -> pr.offX = Math.max(-50, Math.min(50, v)), 0.25, "%.2f", false);
		num("Move up", () -> pr.offY, v -> pr.offY = Math.max(-50, Math.min(50, v)), 0.25, "%.2f", false);
		num("Move forward", () -> pr.offZ, v -> pr.offZ = Math.max(-50, Math.min(50, v)), 0.25, "%.2f", false);
		splitRow(new String[] {"Flip nose", "Reset shape"}, new int[] {STYLE_BUTTON, STYLE_DANGER}, new Runnable[] {
				() -> { pr.rotY = wrap(pr.rotY + 180); changed(); },
				() -> { pr.resetTransform(); changed(); }});
	}

	private int presetIndex(ModelProfile.Afterburner a) {
		for (int i = 0; i < COLORS.length; i++) {
			if (COLORS[i][0] == a.r && COLORS[i][1] == a.g && COLORS[i][2] == a.b) return i;
		}
		return -1;
	}

	private void flamesPage(ModelProfile pr) {
		value("Flames", () -> JetConfig.FLAME_NAMES[JetConfig.flameMode],
				() -> JetConfig.flameMode = (JetConfig.flameMode + 1) % JetConfig.FLAME_NAMES.length);
		int n = pr.burners.size();
		if (selBurner >= n) selBurner = Math.max(0, n - 1);
		splitRow(new String[] {"Add", "Mirror", "Remove"}, new int[] {STYLE_PRIMARY, STYLE_BUTTON, STYLE_DANGER}, new Runnable[] {
				() -> { pr.burners.add(new ModelProfile.Afterburner()); selBurner = pr.burners.size() - 1; changed(); reopen(); },
				() -> {
					if (selBurner < pr.burners.size()) {
						ModelProfile.Afterburner c = pr.burners.get(selBurner).copy();
						c.x = -c.x;
						pr.burners.add(c);
						selBurner = pr.burners.size() - 1;
						changed();
						reopen();
					}
				},
				() -> {
					if (selBurner < pr.burners.size()) {
						pr.burners.remove(selBurner);
						selBurner = Math.max(0, selBurner - 1);
						changed();
						reopen();
					}
				}});
		if (n == 0) {
			section("No afterburners yet - press Add");
			return;
		}
		selector("Flame", n, () -> selBurner, i -> selBurner = i);
		ModelProfile.Afterburner a = pr.burners.get(selBurner);
		num("Side (+right)", () -> a.x, v -> a.x = Math.max(-60, Math.min(60, v)), 0.5, "%.1f%%", false);
		num("Height", () -> a.y, v -> a.y = Math.max(-60, Math.min(60, v)), 0.5, "%.1f%%", false);
		num("Front/back", () -> a.z, v -> a.z = Math.max(-80, Math.min(80, v)), 0.5, "%.1f%%", false);
		num("Length", () -> a.len, v -> a.len = Math.max(1, Math.min(100, v)), 1, "%.1f%%", false);
		num("Width", () -> a.rad, v -> a.rad = Math.max(0.3, Math.min(30, v)), 0.25, "%.2f%%", false);
		value("Colour", () -> presetIndex(a) < 0 ? "Custom" : COLOR_NAMES[presetIndex(a)], () -> {
			int idx = (presetIndex(a) + 1) % COLORS.length;
			a.r = COLORS[idx][0]; a.g = COLORS[idx][1]; a.b = COLORS[idx][2];
			changed();
		});
		num("Red", () -> a.r, v -> a.r = (int) Math.max(0, Math.min(255, Math.round(v))), 16, "%.0f", false);
		num("Green", () -> a.g, v -> a.g = (int) Math.max(0, Math.min(255, Math.round(v))), 16, "%.0f", false);
		num("Blue", () -> a.b, v -> a.b = (int) Math.max(0, Math.min(255, Math.round(v))), 16, "%.0f", false);
	}

	private interface IntSetter {
		void set(int i);
	}

	private interface IntGetter {
		int get();
	}

	private void selector(String noun, int n, IntGetter get, IntSetter set) {
		int r = nextRow();
		int x = colX();
		add(x, r, 22, 20, STYLE_SMALL, () -> "<", () -> { set.set((get.get() + n - 1) % n); reopen(); });
		add(x + 24, r, W - 48, 20, STYLE_READOUT, () -> noun + " " + (get.get() + 1) + " / " + n, () -> {});
		add(x + W - 22, r, 22, 20, STYLE_SMALL, () -> ">", () -> { set.set((get.get() + 1) % n); reopen(); });
	}

	// ------------------------------------------------------------ control surfaces + landing gear
	private static final RigPart.Kind[] SURFACE_KINDS = {
		RigPart.Kind.AILERON, RigPart.Kind.ELEVATOR, RigPart.Kind.RUDDER, RigPart.Kind.AIRBRAKE};

	private void rigPage(ModelProfile pr, boolean gear) {
		List<RigPart> list = new ArrayList<>();
		for (RigPart rp : pr.rig) if (rp.isGear() == gear) list.add(rp);
		int sel = gear ? selGear : selSurface;
		if (sel >= list.size()) sel = Math.max(0, list.size() - 1);
		final int selF = sel;
		if (gear) selGear = sel; else selSurface = sel;

		splitRow(new String[] {"Add", "Mirror", "Remove"}, new int[] {STYLE_PRIMARY, STYLE_BUTTON, STYLE_DANGER}, new Runnable[] {
				() -> {
					RigPart rp = new RigPart();
					rp.kind = gear ? RigPart.Kind.GEAR : RigPart.Kind.AILERON;
					rp.mode = ModelManager.model().partNames.isEmpty() ? RigPart.Mode.BOX : RigPart.Mode.PART;
					rp.name = (gear ? "Gear " : "Surface ") + (list.size() + 1);
					rp.maxDeg = gear ? 0 : 22;
					pr.rig.add(rp);
					if (gear) selGear = list.size(); else selSurface = list.size();
					changed();
					ModelManager.rebuildRig();
					reopen();
				},
				() -> {
					if (selF < list.size()) {
						RigPart c = list.get(selF).copy();
						c.name = mirrorName(c.name);
						c.part = mirrorName(c.part);
						c.px = -c.px; c.ax = -c.ax; c.tx = -c.tx;
						double mn = -c.box[3], mx = -c.box[0];
						c.box[0] = mn; c.box[3] = mx;
						if (c.kind == RigPart.Kind.ELEVATOR || c.kind == RigPart.Kind.AIRBRAKE) c.gain = -c.gain;
						pr.rig.add(c);
						if (gear) selGear = list.size(); else selSurface = list.size();
						changed();
						ModelManager.rebuildRig();
						reopen();
					}
				},
				() -> {
					if (selF < list.size()) {
						pr.rig.remove(list.get(selF));
						if (gear) selGear = Math.max(0, selF - 1); else selSurface = Math.max(0, selF - 1);
						changed();
						ModelManager.rebuildRig();
						reopen();
					}
				}});
		if (list.isEmpty()) {
			section(gear ? "No gear rigged - press Add" : "No surfaces yet - press Add");
			return;
		}
		selector(gear ? "Gear" : "Surface", list.size(), () -> gear ? selGear : selSurface, i -> { if (gear) selGear = i; else selSurface = i; });
		RigPart rp = list.get(selF);
		section(rp.name);

		if (!gear) {
			value("Controls", () -> RigPart.kindLabel(rp.kind), () -> {
				int i = 0;
				for (int k = 0; k < SURFACE_KINDS.length; k++) if (SURFACE_KINDS[k] == rp.kind) i = k;
				rp.kind = SURFACE_KINDS[(i + 1) % SURFACE_KINDS.length];
				changed();
			});
		}
		value("Pick by", () -> rp.mode == RigPart.Mode.PART ? "Mesh part" : rp.mode == RigPart.Mode.BOX ? "Cutting box" : "Whole group", () -> {
			int groups = ModelManager.model().groups.length;
			RigPart.Mode[] modes = groups > 1 ? RigPart.Mode.values() : new RigPart.Mode[] {RigPart.Mode.PART, RigPart.Mode.BOX};
			int i = 0;
			for (int k = 0; k < modes.length; k++) if (modes[k] == rp.mode) i = k;
			rp.mode = modes[(i + 1) % modes.length];
			changed();
			ModelManager.rebuildRig();
			reopen();
		});
		if (rp.mode == RigPart.Mode.PART) {
			List<String> names = ModelManager.model().partNames;
			if (names.isEmpty()) {
				section("No named parts: use a cutting box");
			} else {
				int r = nextRow();
				int x = colX();
				add(x, r, 22, 20, STYLE_SMALL, () -> "<", () -> cyclePart(rp, names, -1));
				add(x + 24, r, W - 48, 20, STYLE_READOUT, () -> rp.part.isEmpty() ? "(pick a part)" : cut(rp.part, 22), () -> {});
				add(x + W - 22, r, 22, 20, STYLE_SMALL, () -> ">", () -> cyclePart(rp, names, 1));
			}
		} else if (rp.mode == RigPart.Mode.BOX) {
			if (ModelManager.model().groups.length > 1) {
				num("Source group", () -> rp.group, v -> rp.group = (int) Math.max(0, Math.min(ModelManager.model().groups.length - 1, Math.round(v))), 1, "%.0f", true);
			}
			String[] ax = {"X min", "Y min", "Z min", "X max", "Y max", "Z max"};
			for (int i = 0; i < 6; i++) {
				final int k = i;
				num("Box " + ax[i], () -> rp.box[k], v -> rp.box[k] = Math.max(-150, Math.min(150, v)), 1, "%.1f%%", true);
			}
		} else {
			num("Group #", () -> rp.group, v -> rp.group = (int) Math.max(0, Math.min(ModelManager.model().groups.length - 1, Math.round(v))), 1, "%.0f", true);
		}

		num("Hinge X", () -> rp.px, v -> rp.px = Math.max(-150, Math.min(150, v)), 0.5, "%.1f%%", false);
		num("Hinge Y", () -> rp.py, v -> rp.py = Math.max(-150, Math.min(150, v)), 0.5, "%.1f%%", false);
		num("Hinge Z", () -> rp.pz, v -> rp.pz = Math.max(-150, Math.min(150, v)), 0.5, "%.1f%%", false);
		value("Hinge axis", () -> axisName(rp), () -> {
			if (rp.ax == 1 && rp.ay == 0 && rp.az == 0) { rp.ax = 0; rp.ay = 1; }
			else if (rp.ax == 0 && rp.ay == 1 && rp.az == 0) { rp.ay = 0; rp.az = 1; }
			else { rp.ax = 1; rp.ay = 0; rp.az = 0; }
			changed();
		});
		if (gear) {
			num("Retract angle", () -> rp.maxDeg, v -> rp.maxDeg = Math.max(-180, Math.min(180, v)), 5, "%.0f", false);
			num("Retract move X", () -> rp.tx, v -> rp.tx = Math.max(-100, Math.min(100, v)), 0.5, "%.1f%%", false);
			num("Retract move Y", () -> rp.ty, v -> rp.ty = Math.max(-100, Math.min(100, v)), 0.5, "%.1f%%", false);
			num("Retract move Z", () -> rp.tz, v -> rp.tz = Math.max(-100, Math.min(100, v)), 0.5, "%.1f%%", false);
		} else {
			num("Max deflection", () -> rp.maxDeg, v -> rp.maxDeg = Math.max(0, Math.min(90, v)), 1, "%.0f", false);
		}
		value("Direction", () -> rp.gain >= 0 ? "Normal" : "Reversed", () -> { rp.gain = -rp.gain; changed(); });

		if (gear) {
			value("Test gear", () -> JetState.testGearUp ? "Retracted" : "Deployed", () -> JetState.testGearUp = !JetState.testGearUp);
		} else {
			splitRow(new String[] {"Roll <", "Roll >", "Pitch^", "Pitchv"}, new int[] {STYLE_BUTTON, STYLE_BUTTON, STYLE_BUTTON, STYLE_BUTTON}, new Runnable[] {
					() -> JetState.testRoll = JetState.testRoll == -1f ? 0f : -1f,
					() -> JetState.testRoll = JetState.testRoll == 1f ? 0f : 1f,
					() -> JetState.testPitch = JetState.testPitch == 1f ? 0f : 1f,
					() -> JetState.testPitch = JetState.testPitch == -1f ? 0f : -1f});
			splitRow(new String[] {"Yaw <", "Yaw >", "Brake", "Center"}, new int[] {STYLE_BUTTON, STYLE_BUTTON, STYLE_BUTTON, STYLE_BUTTON}, new Runnable[] {
					() -> JetState.testYaw = JetState.testYaw == -1f ? 0f : -1f,
					() -> JetState.testYaw = JetState.testYaw == 1f ? 0f : 1f,
					() -> JetState.testBrake = JetState.testBrake > 0.5f ? 0f : 1f,
					() -> { JetState.testRoll = JetState.testPitch = JetState.testYaw = JetState.testBrake = 0f; }});
		}
	}

	private static String cut(String s, int n) {
		return s.length() <= n ? s : s.substring(0, n - 1) + "~";
	}

	private static String axisName(RigPart r) {
		if (r.ax == 1 && r.ay == 0 && r.az == 0) return "X";
		if (r.ax == 0 && r.ay == 1 && r.az == 0) return "Y";
		if (r.ax == 0 && r.ay == 0 && r.az == 1) return "Z";
		return String.format("custom %.2f,%.2f,%.2f", r.ax, r.ay, r.az);
	}

	private static String mirrorName(String n) {
		if (n == null || n.isEmpty()) return n;
		char last = n.charAt(n.length() - 1);
		if (last == 'L') return n.substring(0, n.length() - 1) + "R";
		if (last == 'R') return n.substring(0, n.length() - 1) + "L";
		return n;
	}

	private void cyclePart(RigPart rp, List<String> names, int dir) {
		int i = names.indexOf(rp.part);
		i = i < 0 ? 0 : Math.floorMod(i + dir, names.size());
		rp.part = names.get(i);
		rp.name = rp.part;
		changed();
		ModelManager.rebuildRig();
	}

	// ------------------------------------------------------------ drawing
	@Override
	protected void drawBackdrop(GuiGraphicsExtractor g) {
		// no dimming: the aircraft is previewed live in the world behind the panel
	}

	@Override
	protected void drawChrome(GuiGraphicsExtractor g, int mx, int my) {
		for (int c = 0; c <= column && c < 3; c++) {
			int x = X0 + c * (W + 8) - 4;
			int bottom = Math.max(colBottom[c], top) + 4;
			panel(g, x, 2, W + 8, bottom - 2, 0xB00A1017, true);
		}
	}

	@Override
	public void onClose() {
		JetState.previewYaw = 0f;
		JetState.previewPitch = 0f;
		JetState.testRoll = JetState.testPitch = JetState.testYaw = JetState.testBrake = 0f;
		JetState.testGearUp = false;
		ModelManager.saveProfile();
		SettingsScreen.open(this.minecraft, parent);
	}

	public void removed() {
		JetState.editorOpen = false;
		ModelManager.saveProfile();
	}
}
