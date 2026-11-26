package com.mig25;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * In-game aircraft editor, docked on the right so the aircraft stays big, sharp and unobstructed on the left.
 * Pages: Shape (rotate / move / scale), Flames (afterburners), Gear (separate landing-gear regions), Paint (colour wheel).
 * Drag with the left mouse button on empty space to orbit the aircraft.
 */
public class ModelEditorScreen extends TacticalScreen {
	private static final String[] PAGES = {"Shape", "Flames", "Gear", "Paint"};
	private static final String[] COLOR_NAMES = {"Blue", "Orange", "Red", "White", "Green", "Purple", "Cyan", "Yellow"};
	private static final int[][] COLORS = {
		{70, 140, 255}, {255, 140, 40}, {255, 60, 40}, {235, 240, 255},
		{80, 255, 120}, {180, 90, 255}, {60, 230, 255}, {255, 220, 60}
	};

	private static int page = 0;
	private static int selBurner = 0;
	private static int selGear = 0;

	private static final int W = 190;
	private static final int ROW = 22;
	private static final int WHEEL = 96;

	private int y;
	private int column;
	private int top;
	private int[] colBottom = new int[3];

	// paint viewport
	private int vpX, vpY, vpW, vpH;
	private int wheelX, wheelY;
	private boolean strokeActive;
	private int lastPartPainted = -2;
	private int lastDragX, lastDragY;
	private boolean pendingRig;
	private long lastRigRebuild;

	public ModelEditorScreen(Screen parent) {
		super("AeroCraft Aircraft Editor", parent);
	}

	private static void changed() {
		ModelManager.markProfileDirty();
	}

	private void reopen() {
		SettingsScreen.open(this.minecraft, new ModelEditorScreen(parent));
	}

	// ------------------------------------------------------------ flow layout, docked to the right and wrapping leftwards
	private int colX() {
		return this.width - 8 - (column + 1) * W - column * 8;
	}

	private int nextRow() {
		if (y + ROW > this.height - 6 && column < 2) {
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
		add(colX(), r, W, 20, STYLE_READOUT, () -> label, () -> {});
	}

	private void sliderRow(String name, double min, double max, double step, double snap, DoubleSupplier get,
			DoubleConsumer set, String fmt, Runnable after) {
		int r = nextRow();
		int x = colX();
		DoubleConsumer apply = v -> {
			double c = Math.max(min, Math.min(max, v));
			set.accept(Math.round(c / snap) * snap);
			changed();
			if (after != null) after.run();
		};
		add(x, r, 22, 20, STYLE_SMALL, () -> "-", () -> apply.accept(get.getAsDouble() - step));
		slider(x + 24, r, W - 48, name, min, max, get, apply, fmt);
		add(x + W - 22, r, 22, 20, STYLE_SMALL, () -> "+", () -> apply.accept(get.getAsDouble() + step));
	}

	private void valueRow(String name, Supplier<String> val, Runnable next) {
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

	private void rigChanged() {
		pendingRig = true;
	}

	// ------------------------------------------------------------ build
	@Override
	protected void init() {
		JetState.editorOpen = true;
		JetState.highlight = null;
		y = 6;
		column = 0;
		colBottom = new int[3];
		ModelProfile pr = ModelManager.profile;

		int r0 = nextRow();
		add(colX(), r0, W, 20, STYLE_READOUT, () -> "EDIT: " + ModelManager.displayName(ModelManager.currentName).toUpperCase(), () -> {});
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
		top = y;

		switch (page) {
			case 0 -> shapePage(pr);
			case 1 -> flamesPage(pr);
			case 2 -> gearPage(pr);
			default -> paintPage(pr);
		}

		splitRow(new String[] {"<", ">", "Up", "Dn"}, new int[] {STYLE_SMALL, STYLE_SMALL, STYLE_SMALL, STYLE_SMALL},
				new Runnable[] {
					() -> turn(-30f, 0f),
					() -> turn(30f, 0f),
					() -> turn(0f, -20f),
					() -> turn(0f, 20f)});
		toggleRowFull("Studio backdrop", () -> JetConfig.studio, () -> JetConfig.studio = !JetConfig.studio);
		wide("Done", STYLE_PRIMARY, this::onClose);
	}

	private void toggleRowFull(String name, java.util.function.BooleanSupplier get, Runnable flip) {
		int r = nextRow();
		toggle(colX(), r, W, name, get, flip);
	}

	private void turn(float dYaw, float dPitch) {
		if (page == 3) {
			HangarPreview.spin += dYaw;
			HangarPreview.tilt = Math.max(-80f, Math.min(80f, HangarPreview.tilt + dPitch));
		} else {
			JetState.previewYaw += dYaw;
			JetState.previewPitch = Math.max(-80f, Math.min(80f, JetState.previewPitch + dPitch));
		}
	}

	// ------------------------------------------------------------ Shape
	private void quickRow(DoubleSupplier get, DoubleConsumer set) {
		splitRow(new String[] {"-90", "-15", "+15", "+90"}, new int[] {STYLE_SMALL, STYLE_SMALL, STYLE_SMALL, STYLE_SMALL}, new Runnable[] {
				() -> { set.accept(wrap(get.getAsDouble() - 90)); changed(); },
				() -> { set.accept(wrap(get.getAsDouble() - 15)); changed(); },
				() -> { set.accept(wrap(get.getAsDouble() + 15)); changed(); },
				() -> { set.accept(wrap(get.getAsDouble() + 90)); changed(); }});
	}

	private void shapePage(ModelProfile pr) {
		section("ROTATION");
		sliderRow("Pitch X", -180, 180, 1, 1, () -> pr.rotX, v -> pr.rotX = v, "%.0f deg", null);
		quickRow(() -> pr.rotX, v -> pr.rotX = v);
		sliderRow("Yaw Y", -180, 180, 1, 1, () -> pr.rotY, v -> pr.rotY = v, "%.0f deg", null);
		quickRow(() -> pr.rotY, v -> pr.rotY = v);
		sliderRow("Roll Z", -180, 180, 1, 1, () -> pr.rotZ, v -> pr.rotZ = v, "%.0f deg", null);
		quickRow(() -> pr.rotZ, v -> pr.rotZ = v);
		splitRow(new String[] {"Flip 180", "Reset Airframe"}, new int[] {STYLE_BUTTON, STYLE_DANGER}, new Runnable[] {
				() -> { pr.rotY = wrap(pr.rotY + 180); changed(); },
				() -> { pr.resetTransform(); changed(); }});
		section("POSITION AND SCALE");
		sliderRow("Lateral X", -15, 15, 0.1, 0.05, () -> pr.offX, v -> pr.offX = v, "%.2f", null);
		sliderRow("Height Y", -15, 15, 0.1, 0.05, () -> pr.offY, v -> pr.offY = v, "%.2f", null);
		sliderRow("Fore/Aft Z", -15, 15, 0.1, 0.05, () -> pr.offZ, v -> pr.offZ = v, "%.2f", null);
		sliderRow("Scale", 0.5, 60, 0.25, 0.05, () -> pr.size, v -> pr.size = v, "%.2f", null);
		wide("Snap Gear to Y=0", STYLE_BUTTON, () -> {
			double bottom = ModelMetrics.gearBottomY(ModelManager.model(), pr, ModelManager.rig);
			pr.offY = Math.max(-15, Math.min(15, -0.9 - bottom));
			changed();
		});
	}

	// ------------------------------------------------------------ Flames
	private int presetIndex(ModelProfile.Afterburner a) {
		for (int i = 0; i < COLORS.length; i++) {
			if (COLORS[i][0] == a.r && COLORS[i][1] == a.g && COLORS[i][2] == a.b) return i;
		}
		return -1;
	}

	private void selector(String noun, int n, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
		int r = nextRow();
		int x = colX();
		add(x, r, 22, 20, STYLE_SMALL, () -> "<", () -> { set.accept((get.getAsInt() + n - 1) % n); reopen(); });
		add(x + 24, r, W - 48, 20, STYLE_READOUT, () -> noun + " " + (get.getAsInt() + 1) + " / " + n, () -> {});
		add(x + W - 22, r, 22, 20, STYLE_SMALL, () -> ">", () -> { set.accept((get.getAsInt() + 1) % n); reopen(); });
	}

	private void flamesPage(ModelProfile pr) {
		valueRow("Flames", () -> JetConfig.FLAME_NAMES[JetConfig.flameMode],
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
		sliderRow("Side (+right)", -60, 60, 0.5, 0.1, () -> a.x, v -> a.x = v, "%.1f%%", null);
		sliderRow("Height", -60, 60, 0.5, 0.1, () -> a.y, v -> a.y = v, "%.1f%%", null);
		sliderRow("Front/back", -80, 80, 0.5, 0.1, () -> a.z, v -> a.z = v, "%.1f%%", null);
		sliderRow("Length", 1, 100, 1, 0.5, () -> a.len, v -> a.len = v, "%.1f%%", null);
		sliderRow("Width", 0.3, 30, 0.25, 0.05, () -> a.rad, v -> a.rad = v, "%.2f%%", null);
		valueRow("Colour", () -> presetIndex(a) < 0 ? "Custom" : COLOR_NAMES[presetIndex(a)], () -> {
			int idx = (presetIndex(a) + 1) % COLORS.length;
			a.r = COLORS[idx][0]; a.g = COLORS[idx][1]; a.b = COLORS[idx][2];
			changed();
		});
		sliderRow("Red", 0, 255, 8, 1, () -> a.r, v -> a.r = (int) v, "%.0f", null);
		sliderRow("Green", 0, 255, 8, 1, () -> a.g, v -> a.g = (int) v, "%.0f", null);
		sliderRow("Blue", 0, 255, 8, 1, () -> a.b, v -> a.b = (int) v, "%.0f", null);
	}

	// ------------------------------------------------------------ Gear (separate, editable landing-gear regions)
	private void gearPage(ModelProfile pr) {
		List<RigPart> list = pr.rig;
		int n = list.size();
		if (selGear >= n) selGear = Math.max(0, n - 1);
		final int selF = selGear;
		splitRow(new String[] {"Add", "Mirror", "Remove"}, new int[] {STYLE_PRIMARY, STYLE_BUTTON, STYLE_DANGER}, new Runnable[] {
				() -> {
					RigPart rp = new RigPart();
					rp.mode = ModelManager.model().partNames.isEmpty() ? RigPart.Mode.BOX : RigPart.Mode.PART;
					rp.name = "Gear " + (list.size() + 1);
					if (rp.mode == RigPart.Mode.BOX) rp.box = new double[] {-100, -100, -100, 100, -20, 100};
					list.add(rp);
					selGear = list.size() - 1;
					changed();
					ModelManager.rebuildRig();
					reopen();
				},
				() -> {
					if (selF < list.size()) {
						RigPart c = list.get(selF).copy();
						c.name = mirrorName(c.name);
						c.part = mirrorName(c.part);
						c.ox = -c.ox; c.px = -c.px; c.ax = -c.ax; c.tx = -c.tx;
						double mn = -c.box[3], mx = -c.box[0];
						c.box[0] = mn; c.box[3] = mx;
						list.add(c);
						selGear = list.size() - 1;
						changed();
						ModelManager.rebuildRig();
						reopen();
					}
				},
				() -> {
					if (selF < list.size()) {
						list.remove(selF);
						selGear = Math.max(0, selF - 1);
						changed();
						ModelManager.rebuildRig();
						reopen();
					}
				}});
		if (list.isEmpty()) {
			section("No gear regions - press Add");
			return;
		}
		selector("Gear region", list.size(), () -> selGear, i -> selGear = i);
		RigPart rp = list.get(selF);
		JetState.highlight = rp;
		section(rp.name);

		valueRow("Pick by", () -> rp.mode == RigPart.Mode.PART ? "Mesh part" : rp.mode == RigPart.Mode.BOX ? "Region box" : "Whole group", () -> {
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
				section("No named parts: use a region box");
			} else {
				int r = nextRow();
				int x = colX();
				add(x, r, 22, 20, STYLE_SMALL, () -> "<", () -> cyclePart(rp, names, -1));
				add(x + 24, r, W - 48, 20, STYLE_READOUT, () -> rp.part.isEmpty() ? "(pick a part)" : rp.part, () -> {});
				add(x + W - 22, r, 22, 20, STYLE_SMALL, () -> ">", () -> cyclePart(rp, names, 1));
			}
		} else if (rp.mode == RigPart.Mode.BOX) {
			int groups = ModelManager.model().groups.length;
			if (groups > 1) {
				sliderRow("Source group", 0, groups - 1, 1, 1, () -> rp.group, v -> rp.group = (int) v, "%.0f", this::rigChanged);
			}
			String[] ax = {"X min", "Y min", "Z min", "X max", "Y max", "Z max"};
			for (int i = 0; i < 6; i++) {
				final int k = i;
				sliderRow("Box " + ax[i], -150, 150, 1, 0.5, () -> rp.box[k], v -> rp.box[k] = v, "%.1f%%", this::rigChanged);
			}
		} else {
			sliderRow("Group #", 0, Math.max(1, ModelManager.model().groups.length - 1), 1, 1, () -> rp.group, v -> rp.group = (int) v, "%.0f", this::rigChanged);
		}

		section("MOVE REGION (deployed)");
		sliderRow("Move X", -50, 50, 0.5, 0.1, () -> rp.ox, v -> rp.ox = v, "%.1f%%", null);
		sliderRow("Move Y", -50, 50, 0.5, 0.1, () -> rp.oy, v -> rp.oy = v, "%.1f%%", null);
		sliderRow("Move Z", -50, 50, 0.5, 0.1, () -> rp.oz, v -> rp.oz = v, "%.1f%%", null);
		section("HINGE AND RETRACT");
		sliderRow("Hinge X", -150, 150, 0.5, 0.1, () -> rp.px, v -> rp.px = v, "%.1f%%", null);
		sliderRow("Hinge Y", -150, 150, 0.5, 0.1, () -> rp.py, v -> rp.py = v, "%.1f%%", null);
		sliderRow("Hinge Z", -150, 150, 0.5, 0.1, () -> rp.pz, v -> rp.pz = v, "%.1f%%", null);
		valueRow("Hinge axis", () -> axisName(rp), () -> {
			if (rp.ax == 1 && rp.ay == 0 && rp.az == 0) { rp.ax = 0; rp.ay = 1; }
			else if (rp.ax == 0 && rp.ay == 1 && rp.az == 0) { rp.ay = 0; rp.az = 1; }
			else { rp.ax = 1; rp.ay = 0; rp.az = 0; }
			changed();
		});
		sliderRow("Retract angle", -180, 180, 5, 1, () -> rp.maxDeg, v -> rp.maxDeg = v, "%.0f deg", null);
		sliderRow("Retract X", -100, 100, 0.5, 0.1, () -> rp.tx, v -> rp.tx = v, "%.1f%%", null);
		sliderRow("Retract Y", -100, 100, 0.5, 0.1, () -> rp.ty, v -> rp.ty = v, "%.1f%%", null);
		sliderRow("Retract Z", -100, 100, 0.5, 0.1, () -> rp.tz, v -> rp.tz = v, "%.1f%%", null);
		valueRow("Direction", () -> rp.gain >= 0 ? "Normal" : "Reversed", () -> { rp.gain = -rp.gain; changed(); });
		valueRow("Test gear", () -> JetState.testGearUp ? "Retracted" : "Deployed", () -> JetState.testGearUp = !JetState.testGearUp);
	}

	private static String axisName(RigPart r) {
		if (r.ax == 1 && r.ay == 0 && r.az == 0) return "X";
		if (r.ax == 0 && r.ay == 1 && r.az == 0) return "Y";
		if (r.ax == 0 && r.ay == 0 && r.az == 1) return "Z";
		return String.format("%.2f,%.2f,%.2f", r.ax, r.ay, r.az);
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

	// ------------------------------------------------------------ Paint
	private void paintPage(ModelProfile pr) {
		// viewport on the left, wheel and tools on the right
		vpX = 8;
		vpY = 8;
		vpW = Math.max(120, Math.min(320, this.width - W - 40));
		vpH = Math.max(100, Math.min(260, this.height - 16));
		area(vpX, vpY, vpW, vpH, (mx, my) -> paintAt(mx, my));

		splitRow(new String[] {"Brush", "Part", "Pick"}, new int[] {STYLE_BUTTON, STYLE_BUTTON, STYLE_BUTTON}, new Runnable[] {
				() -> PaintTool.mode = 0, () -> PaintTool.mode = 1, () -> PaintTool.mode = 2});
		for (int i = 0; i < 3; i++) {
			final int idx = i;
			ui.get(ui.size() - 3 + i).selected = () -> PaintTool.mode == idx;
		}
		sliderRow("Brush size", 4, 48, 2, 1, () -> PaintTool.radius, v -> PaintTool.radius = (int) v, "%.0f px", null);

		int r = nextRow();
		int wy = r;
		y += WHEEL - ROW + 6; // reserve vertical space for the wheel
		colBottom[column] = y;
		wheelX = colX() + (W - WHEEL) / 2;
		wheelY = wy;
		area(wheelX, wheelY, WHEEL, WHEEL, (mx, my) -> {
			double cx = wheelX + WHEEL / 2.0, cy = wheelY + WHEEL / 2.0;
			double dx = mx - cx, dy = my - cy;
			double rr = Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / (WHEEL / 2.0));
			double ang = Math.atan2(dy, dx) / (2 * Math.PI);
			PaintTool.hue = (float) ((ang + 1.0) % 1.0);
			PaintTool.sat = (float) rr;
		});
		sliderRow("Hue", 0, 360, 5, 1, () -> PaintTool.hue * 360.0, v -> PaintTool.hue = (float) (v / 360.0), "%.0f", null);
		sliderRow("Saturation", 0, 100, 5, 1, () -> PaintTool.sat * 100.0, v -> PaintTool.sat = (float) (v / 100.0), "%.0f%%", null);
		sliderRow("Brightness", 0, 100, 5, 1, () -> PaintTool.val * 100.0, v -> PaintTool.val = (float) (v / 100.0), "%.0f%%", null);

		// preset swatches: 4 rows of 4
		int sw = (W - 3 * 4) / 4;
		for (int row = 0; row < 4; row++) {
			int rr = nextRow();
			for (int c = 0; c < 4; c++) {
				final int color = PaintTool.PRESETS[row * 4 + c];
				Ui u = add(colX() + c * (sw + 4), rr, sw, 20, STYLE_SMALL, () -> "", () -> PaintTool.setRgb(color));
				u.value = () -> String.valueOf(color);
			}
		}
		splitRow(new String[] {"Undo", "Clear all", "Fill all"}, new int[] {STYLE_BUTTON, STYLE_DANGER, STYLE_BUTTON}, new Runnable[] {
				PaintTool::undo,
				PaintTool::clearAll,
				() -> {
					List<Integer> keys = HangarPreview.allKeys();
					PaintTool.beginStroke();
					PaintTool.apply(keys, PaintTool.rgb());
					PaintTool.endStroke();
				}});
	}

	private void paintAt(int mx, int my) {
		if (page != 3) return;
		int rgb = PaintTool.rgb();
		switch (PaintTool.mode) {
			case 0 -> PaintTool.apply(HangarPreview.brush(mx, my, PaintTool.radius), rgb);
			case 1 -> {
				HangarPreview.Hit h = HangarPreview.pick(mx, my);
				if (h == null) return;
				if (h.partName >= 0) {
					if (h.partName == lastPartPainted) return;
					lastPartPainted = h.partName;
					PaintTool.apply(HangarPreview.keysOfPart(h.partName), rgb);
				} else {
					PaintTool.apply(java.util.List.of(h.key), rgb);
				}
			}
			default -> {
				HangarPreview.Hit h = HangarPreview.pick(mx, my);
				if (h == null) return;
				Integer p = ModelManager.profile.paint.get(h.key);
				PaintTool.setRgb(p != null ? p : (h.color & 0x00FFFFFF));
			}
		}
	}

	// ------------------------------------------------------------ input / drawing
	private boolean over(int mx, int my, int x, int yy, int w, int h) {
		return mx >= x && mx < x + w && my >= yy && my < yy + h;
	}

	private boolean overPanel(int mx, int my) {
		for (int c = 0; c <= column && c < 3; c++) {
			int x = this.width - 8 - (c + 1) * W - c * 8 - 4;
			int bottom = Math.max(colBottom[c], top) + 4;
			if (over(mx, my, x, 2, W + 8, bottom - 2)) return true;
		}
		return page == 3 && over(mx, my, vpX, vpY, vpW, vpH);
	}

	@Override
	protected void frameInput(int mx, int my, boolean left, boolean right, boolean justPressed) {
		int dx = mx - lastDragX, dy = my - lastDragY;
		if (page == 3) {
			boolean inVp = over(mx, my, vpX, vpY, vpW, vpH);
			if (justPressed && inVp) {
				PaintTool.beginStroke();
				strokeActive = true;
				lastPartPainted = -2;
			}
			if (!left && strokeActive) {
				PaintTool.endStroke();
				strokeActive = false;
			}
			if (right && inVp && (dx != 0 || dy != 0)) {
				HangarPreview.spin += dx * 0.8f;
				HangarPreview.tilt = Math.max(-80f, Math.min(80f, HangarPreview.tilt + dy * 0.5f));
			}
		} else if (left && !justPressed && !overPanel(mx, my) && (dx != 0 || dy != 0)) {
			JetState.previewYaw += dx * 0.6f;
			JetState.previewPitch = Math.max(-80f, Math.min(80f, JetState.previewPitch + dy * 0.4f));
		}
		lastDragX = mx;
		lastDragY = my;
		if (pendingRig && (!left || System.currentTimeMillis() - lastRigRebuild > 150)) {
			pendingRig = false;
			lastRigRebuild = System.currentTimeMillis();
			ModelManager.rebuildRig();
		}
	}

	@Override
	protected void drawBackdrop(GuiGraphicsExtractor g) {
		// no dimming and no blur: the aircraft is shown live, sharp and bright in the world on the left
	}

	@Override
	protected void drawChrome(GuiGraphicsExtractor g, int mx, int my) {
		for (int c = 0; c <= column && c < 3; c++) {
			int x = this.width - 8 - (c + 1) * W - c * 8 - 4;
			int bottom = Math.max(colBottom[c], top) + 4;
			panel(g, x, 2, W + 8, bottom - 2, 0xF40A1017, true);
		}
	}

	@Override
	protected void drawExtras(GuiGraphicsExtractor g, int mx, int my) {
		if (page != 3) return;
		int ac = accent();
		g.fill(vpX, vpY, vpX + vpW, vpY + vpH, 0xF2040810);
		g.outline(vpX, vpY, vpW, vpH, alpha(ac, 0xA0));
		HangarPreview.draw(g, vpX, vpY, vpW, vpH, ac, true);
		if (over(mx, my, vpX, vpY, vpW, vpH) && PaintTool.mode == 0) {
			int r = PaintTool.radius;
			g.outline(mx - r, my - r, r * 2, r * 2, alpha(0xFFFFFF, 0xC0));
		}
		g.text(this.minecraft.font, "Left-drag: paint   Right-drag: rotate", vpX + 6, vpY + vpH - 22, 0xFF8A97A6, false);

		// colour wheel
		double cx = wheelX + WHEEL / 2.0, cy = wheelY + WHEEL / 2.0, rad = WHEEL / 2.0;
		for (int py = 0; py < WHEEL; py += 3) {
			for (int px = 0; px < WHEEL; px += 3) {
				double dx = px + 1.5 - rad, dy = py + 1.5 - rad;
				double d = Math.sqrt(dx * dx + dy * dy);
				if (d > rad) continue;
				float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1.0) % 1.0);
				int rgb = PaintTool.hsvToRgb(hue, (float) (d / rad), PaintTool.val);
				g.fill(wheelX + px, wheelY + py, wheelX + px + 3, wheelY + py + 3, 0xFF000000 | rgb);
			}
		}
		g.outline(wheelX - 1, wheelY - 1, WHEEL + 2, WHEEL + 2, alpha(ac, 0x40));
		double ang = PaintTool.hue * 2 * Math.PI;
		int mxk = (int) (cx + Math.cos(ang) * PaintTool.sat * rad), myk = (int) (cy + Math.sin(ang) * PaintTool.sat * rad);
		g.outline(mxk - 3, myk - 3, 6, 6, 0xFFFFFFFF);
		// current colour + hex
		int rgb = PaintTool.rgb();
		g.fill(wheelX + WHEEL + 4 > this.width ? wheelX : wheelX, wheelY + WHEEL + 1, wheelX + 40, wheelY + WHEEL + 5, 0xFF000000 | rgb);
		g.text(this.minecraft.font, String.format("#%06X", rgb), wheelX + 44, wheelY + WHEEL - 1, 0xFFE6EEF5, false);
		// swatches drawn on top of their buttons
		for (Ui u : ui) {
			if (u.style == STYLE_SMALL && u.value != null && u.label.get().isEmpty()) {
				int c = Integer.parseInt(u.value.get());
				g.fill(u.x + 2, u.y + 2, u.x + u.w - 2, u.y + u.h - 2, 0xFF000000 | c);
			}
		}
	}

	@Override
	public void onClose() {
		JetState.previewYaw = 0f;
		JetState.previewPitch = 0f;
		JetState.testGearUp = false;
		JetState.highlight = null;
		if (strokeActive) PaintTool.endStroke();
		PaintTool.flushRig(true);
		ModelManager.saveProfile();
		SettingsScreen.open(this.minecraft, parent);
	}

	public void removed() {
		JetState.editorOpen = false;
		JetState.highlight = null;
		ModelManager.saveProfile();
	}
}
