package com.mig25;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** AeroCraft Elytra control panel: Tactical Glass sidebar UI with a live hangar preview. */
public class SettingsScreen extends TacticalScreen {
	private static final String[] TABS = {"Aircraft", "Hangar", "Sound", "HUD", "Camera", "Flight"};
	private static int tab = 0;
	private static int listScroll = 0;

	private int px, py, pw, ph;
	private int cx0, cy0, cw, ch;
	private int viewX, viewY, viewW, viewH;
	private int slot;

	public SettingsScreen(Screen parent) {
		super("AeroCraft Elytra Control Panel", parent);
	}

	private void reopen() {
		open(this.minecraft, new SettingsScreen(parent));
	}

	// ------------------------------------------------------------ layout helpers
	private int colW() {
		return (cw - 8) / 2;
	}

	private int nextX() {
		return cx0 + (slot % 2) * (colW() + 8);
	}

	private int nextY() {
		return cy0 + (slot / 2) * 24;
	}

	private void toggleRow(String name, java.util.function.BooleanSupplier get, Runnable flip) {
		toggle(nextX(), nextY(), colW(), name, get, flip);
		slot++;
	}

	private void valueRow(String name, java.util.function.Supplier<String> val, Runnable next) {
		value(nextX(), nextY(), colW(), name, val, next);
		slot++;
	}

	private void buttonRow(String name, Runnable r) {
		button(nextX(), nextY(), colW(), 20, name, r);
		slot++;
	}

	/** Y just below the rows added so far (so hint texts never overlap the buttons). */
	private int afterRowsY() {
		return cy0 + ((slot + 1) / 2) * 24 + 4;
	}

	private void hint(int line, int color, String s) {
		textFit(cx0, afterRowsY() + line * 12, color, cw, () -> s);
	}

	private void skipToNewRow() {
		if (slot % 2 == 1) slot++;
	}

	// ------------------------------------------------------------ build
	@Override
	protected void init() {
		pw = Math.min(this.width - 24, 620);
		ph = Math.min(this.height - 24, 340);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		cx0 = px + 110;
		cy0 = py + 38;
		cw = pw - 118;
		ch = ph - 38 - 40;
		slot = 0;

		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			Ui t = add(px + 8, py + 38 + i * 26, 92, 22, STYLE_TAB, () -> TABS[idx], () -> {
				if (tab != idx) {
					tab = idx;
					reopen();
				}
			});
			t.selected = () -> tab == idx;
		}
		add(px + pw - 24, py + 5, 18, 16, STYLE_DANGER, () -> "x", this::onClose);

		switch (tab) {
			case 0 -> aircraftTab();
			case 1 -> hangarTab();
			case 2 -> soundTab();
			case 3 -> hudTab();
			case 4 -> cameraTab();
			default -> flightTab();
		}

		add(cx0, py + ph - 30, 110, 20, STYLE_DANGER, () -> "Reset all", () -> {
			JetConfig.reset();
			reopen();
		});
		add(cx0 + cw - 110, py + ph - 30, 110, 20, STYLE_PRIMARY, () -> "Done", this::onClose);
	}

	private void aircraftTab() {
		toggleRow("Aircraft model", () -> JetConfig.enabled, () -> JetConfig.enabled = !JetConfig.enabled);
		valueRow("Trigger", () -> JetConfig.onlyGliding ? "while gliding" : "elytra worn", () -> JetConfig.onlyGliding = !JetConfig.onlyGliding);
		valueRow("Landing gear", () -> JetConfig.GEAR_NAMES[JetConfig.gearMode],
				() -> JetConfig.gearMode = (JetConfig.gearMode + 1) % JetConfig.GEAR_NAMES.length);
		toggleRow("Auto-deploy near ground", () -> JetConfig.autoDeploy, () -> JetConfig.autoDeploy = !JetConfig.autoDeploy);
		valueRow("Afterburners", () -> JetConfig.FLAME_NAMES[JetConfig.flameMode],
				() -> JetConfig.flameMode = (JetConfig.flameMode + 1) % JetConfig.FLAME_NAMES.length);
		toggleRow("Wingtip vapor trails", () -> JetConfig.vaporTrails, () -> JetConfig.vaporTrails = !JetConfig.vaporTrails);
		valueRow("Trail sensitivity", () -> JetConfig.SENS_NAMES[JetConfig.trailSens],
				() -> JetConfig.trailSens = (JetConfig.trailSens + 1) % JetConfig.TRAIL_G.length);
		toggleRow("Supersonic Mach cone", () -> JetConfig.machCone, () -> JetConfig.machCone = !JetConfig.machCone);
		valueRow("Mach cone speed", () -> Math.round(JetConfig.MACH_SPEEDS[JetConfig.machIdx]) + " blocks/s",
				() -> JetConfig.machIdx = (JetConfig.machIdx + 1) % JetConfig.MACH_SPEEDS.length);
		hint(0, DIM, "Gear lever: G (rebind in Controls) - works in Retractable mode");
		hint(1, DIM, "Every aircraft keeps its own size, flames, gear, paint and sounds.");
	}

	private void hangarTab() {
		int listW = 150;
		textFit(cx0, cy0, accent(), 150, () -> "AIRCRAFT LIBRARY");
		List<String> names = ModelManager.list();
		int rowH = 20;
		int fixedBottom = 22 + 3 * 22 + 4; // scroll row + three action buttons
		int visible = Math.max(1, (ch - 14 - fixedBottom) / (rowH + 2));
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, names.size() - visible)));
		for (int i = 0; i < visible; i++) {
			int idx = listScroll + i;
			if (idx >= names.size()) break;
			final String name = names.get(idx);
			Ui u = add(cx0, cy0 + 14 + i * (rowH + 2), listW, rowH, STYLE_ROW, () -> ModelManager.displayName(name), () -> {
				HangarPreview.resetTests();
				ModelManager.select(name, this::reopen);
			});
			u.selected = () -> name.equals(ModelManager.currentName);
		}
		int by = cy0 + 14 + visible * (rowH + 2) + 2;
		int half = (listW - 4) / 2;
		button(cx0, by, half, 20, "Up", () -> { listScroll = Math.max(0, listScroll - visible); reopen(); });
		button(cx0 + half + 4, by, half, 20, "Down", () -> { listScroll = Math.min(Math.max(0, names.size() - visible), listScroll + visible); reopen(); });
		add(cx0, by + 22, listW, 20, STYLE_BUTTON, () -> "Import aircraft...", () ->
				FilePicker.pick("Import a 3D aircraft (GLB OBJ STL GLTF DAE 3MF FBX USDZ)", path ->
						ModelManager.importFile(path, this::reopen)));
		add(cx0, by + 44, listW, 20, STYLE_PRIMARY, () -> "Open editor", () ->
				open(this.minecraft, new ModelEditorScreen(new SettingsScreen(parent))));
		Ui rm = add(cx0, by + 66, listW, 20, STYLE_DANGER, () -> "Remove aircraft", () -> {
			ModelManager.removeCurrent();
			reopen();
		});
		rm.enabled = !ModelManager.BUILTIN.equals(ModelManager.currentName);

		// preview viewport + gear test
		viewX = cx0 + listW + 8;
		viewY = cy0;
		viewW = cw - listW - 8;
		viewH = Math.max(60, ch - 26);
		int ty = viewY + viewH + 4;
		int bw = (viewW - 4) / 2;
		testBtn(viewX, ty, bw, "Test gear", () -> HangarPreview.gearUp, () -> HangarPreview.gearUp = !HangarPreview.gearUp);
		testBtn(viewX + bw + 4, ty, bw, "Spin", () -> HangarPreview.autoSpin, () -> HangarPreview.autoSpin = !HangarPreview.autoSpin);
		String st = ModelManager.status == null ? "" : ModelManager.status;
		if (!st.isEmpty()) textFit(cx0 + 118, py + ph - 25, DIM, Math.max(10, cw - 236), () -> ModelManager.status == null ? "" : ModelManager.status);
	}

	private void testBtn(int x, int y, int w, String label, java.util.function.BooleanSupplier on, Runnable r) {
		Ui u = add(x, y, w, 20, STYLE_BUTTON, () -> label, r);
		u.selected = on;
	}

	private void soundTab() {
		ModelProfile pr = ModelManager.profile;
		textFit(cx0, cy0, accent(), cw, () -> "SOUNDS FOR: " + ModelManager.displayName(ModelManager.currentName).toUpperCase());
		slot = 2; // leave the first row for the heading
		toggleRow("Engine sound", () -> JetConfig.engineOn, () -> JetConfig.engineOn = !JetConfig.engineOn);
		valueRow("Engine volume", () -> Math.round(JetConfig.VOLS[JetConfig.engineVol] * 100) + "%",
				() -> JetConfig.engineVol = (JetConfig.engineVol + 1) % JetConfig.VOLS.length);
		buttonRow("Import engine sound...", () -> FilePicker.pick("Import ENGINE sound (WAV or OGG)", path -> importSound(path, true)));
		buttonRow("Clear engine sound", () -> { pr.engineSound = ""; ModelManager.saveProfile(); JetAudio.reload(); });
		toggleRow("Flight sound", () -> JetConfig.flightOn, () -> JetConfig.flightOn = !JetConfig.flightOn);
		valueRow("Flight volume", () -> Math.round(JetConfig.VOLS[JetConfig.flightVol] * 100) + "%",
				() -> JetConfig.flightVol = (JetConfig.flightVol + 1) % JetConfig.VOLS.length);
		buttonRow("Import flight sound...", () -> FilePicker.pick("Import FLIGHT / wind sound (WAV or OGG)", path -> importSound(path, false)));
		buttonRow("Clear flight sound", () -> { pr.flightSound = ""; ModelManager.saveProfile(); JetAudio.reload(); });
		textFit(cx0, afterRowsY(), TEXT, cw, () -> "Engine: " + (pr.engineSound.isEmpty() ? "(none)" : pr.engineSound)
				+ "    Flight: " + (pr.flightSound.isEmpty() ? "(none)" : pr.flightSound));
		hint(1, DIM, "WAV and OGG only. Saved per aircraft in its profile.");
		textFit(cx0, afterRowsY() + 24, DIM, cw, () -> ModelManager.status == null ? "" : ModelManager.status);
	}

	private void hudTab() {
		toggleRow("Fighter HUD", () -> JetConfig.hud, () -> JetConfig.hud = !JetConfig.hud);
		valueRow("HUD colour", () -> JetConfig.COLOR_NAMES[JetConfig.colorIndex],
				() -> JetConfig.colorIndex = (JetConfig.colorIndex + 1) % JetConfig.COLORS.length);
		hint(0, DIM, "The HUD colour also tints this whole panel.");
	}

	private void cameraTab() {
		toggleRow("Auto third person", () -> JetConfig.forceThirdPerson, () -> JetConfig.forceThirdPerson = !JetConfig.forceThirdPerson);
		toggleRow("Aircraft ahead of camera", () -> JetConfig.pushAhead, () -> JetConfig.pushAhead = !JetConfig.pushAhead);
		toggleRow("Free look", () -> JetConfig.freeLook, () -> JetConfig.freeLook = !JetConfig.freeLook);
		valueRow("Look speed", () -> Math.round(JetConfig.LOOK_SENS[JetConfig.freeLookSens] * 100) + "%",
				() -> JetConfig.freeLookSens = (JetConfig.freeLookSens + 1) % JetConfig.LOOK_SENS.length);
		toggleRow("Speed shake", () -> JetConfig.shake, () -> JetConfig.shake = !JetConfig.shake);
		valueRow("Shake strength", () -> JetConfig.SENS_NAMES[JetConfig.shakeIdx],
				() -> JetConfig.shakeIdx = (JetConfig.shakeIdx + 1) % JetConfig.SHAKE_AMP.length);
		hint(0, DIM, "Hold Free Look (default Left Alt) and move the mouse to orbit the camera.");
	}

	private void flightTab() {
		toggleRow("Barrel Roll mod support", () -> JetConfig.barrelRoll, () -> JetConfig.barrelRoll = !JetConfig.barrelRoll);
		toggleRow("Invert roll", () -> JetConfig.invertRoll, () -> JetConfig.invertRoll = !JetConfig.invertRoll);
		toggleRow("Bank when turning", () -> JetConfig.autoBank, () -> JetConfig.autoBank = !JetConfig.autoBank);
	}

	/** Copies an imported sound into config/aerocraft/sounds for the active aircraft. */
	private void importSound(java.nio.file.Path src, boolean engine) {
		try {
			String n = src.getFileName().toString();
			int dot = n.lastIndexOf('.');
			String ext = dot < 0 ? "" : n.substring(dot).toLowerCase(java.util.Locale.ROOT);
			if (!ext.equals(".wav") && !ext.equals(".ogg")) {
				ModelManager.status = "Sounds must be .wav or .ogg (convert MP3 first)";
			} else {
				java.nio.file.Files.createDirectories(ModelManager.soundsDir());
				String target = ModelProfile.safe(ModelManager.currentName) + (engine ? "-engine" : "-flight") + ext;
				java.nio.file.Files.copy(src, ModelManager.soundsDir().resolve(target), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				if (engine) ModelManager.profile.engineSound = target; else ModelManager.profile.flightSound = target;
				ModelManager.saveProfile();
				JetAudio.reload();
				ModelManager.status = "Imported " + n;
			}
		} catch (Exception e) {
			ModelManager.status = "Sound import failed: " + e;
		}
		reopen();
	}

	// ------------------------------------------------------------ drawing
	@Override
	protected void drawBackdrop(GuiGraphicsExtractor g) {
		g.fill(0, 0, this.width, this.height, 0xA0050810);
	}

	@Override
	protected void drawChrome(GuiGraphicsExtractor g, int mx, int my) {
		int ac = accent();
		panel(g, px, py, pw, ph, 0xE00A1017, true);
		g.fill(px + 1, py + 1, px + pw - 1, py + 28, 0xF0070B11);
		g.horizontalLine(px + 1, px + pw - 2, py + 28, alpha(ac, 0x90));
		var font = this.minecraft.font;
		g.text(font, "AEROCRAFT ELYTRA", px + 10, py + 10, ac, false);
		g.text(font, "CONTROL PANEL", px + 20 + font.width("AEROCRAFT ELYTRA"), py + 10, DIM, false);
		// sidebar and content wells
		g.fill(px + 4, py + 34, px + 104, py + ph - 4, 0x90050A10);
		g.outline(px + 4, py + 34, 100, ph - 38, alpha(ac, 0x30));
		g.fill(cx0 - 4, cy0 - 4, cx0 + cw + 4, py + ph - 36, 0x60060A10);
		g.outline(cx0 - 4, cy0 - 4, cw + 8, py + ph - 36 - cy0 + 4, alpha(ac, 0x28));
	}

	@Override
	protected void drawExtras(GuiGraphicsExtractor g, int mx, int my) {
		if (tab == 1) {
			int ac = accent();
			g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xE0040810);
			g.outline(viewX, viewY, viewW, viewH, alpha(ac, 0x90));
			HangarPreview.draw(g, viewX, viewY, viewW, viewH, ac, false);
		}
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == 1) {
			int before = listScroll;
			listScroll = Math.max(0, listScroll - (int) Math.signum(scrollY));
			if (before != listScroll) reopen();
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		JetConfig.save();
		open(this.minecraft, parent);
	}

	/** Opens a screen. Found by parameter type because Minecraft.setScreen was renamed in 26.x. */
	public static void open(net.minecraft.client.Minecraft mc, Screen screen) {
		try {
			java.lang.reflect.Method best = null;
			for (java.lang.reflect.Method m : net.minecraft.client.Minecraft.class.getMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Screen.class) {
					if (best == null || m.getName().equals("setScreen")
							|| (m.getName().startsWith("setScreen") && !best.getName().equals("setScreen"))) {
						best = m;
					}
				}
			}
			if (best == null) {
				for (java.lang.reflect.Method m : net.minecraft.client.Minecraft.class.getDeclaredMethods()) {
					if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Screen.class
							&& m.getName().startsWith("setScreen")) {
						m.setAccessible(true);
						best = m;
						break;
					}
				}
			}
			if (best != null) best.invoke(mc, screen);
			else System.err.println("[mig25] no method to open a screen was found");
		} catch (Throwable t) {
			System.err.println("[mig25] could not open screen: " + t);
		}
	}
}
