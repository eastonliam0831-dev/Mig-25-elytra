package com.mig25;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * In-game model editor. The panel sits on the left; the jet keeps rendering live in the world behind it
 * (set Options > Video > Menu Background Blur to 0 for a sharp preview).
 */
public class ModelEditorScreen extends Screen {
	private static final double[] STEP_MUL = {0.1, 1.0, 5.0};
	private static final String[] STEP_NAMES = {"Fine x0.1", "Normal x1", "Coarse x5"};
	private static final String[] COLOR_NAMES = {"Blue", "Orange", "Red", "White", "Green", "Purple", "Cyan", "Yellow"};
	private static final int[][] COLORS = {
		{70, 140, 255}, {255, 140, 40}, {255, 60, 40}, {235, 240, 255},
		{80, 255, 120}, {180, 90, 255}, {60, 230, 255}, {255, 220, 60}
	};

	private static int page = 0;
	private static int stepIdx = 1;
	private static int selected = 0;

	private static final int X0 = 8;
	private static final int W = 172;
	private static final int ROW = 21;

	private final Screen parent;
	private int y;

	public ModelEditorScreen(Screen parent) {
		super(Component.literal("MiG-25 Model Editor"));
		this.parent = parent;
	}

	private static double mul() {
		return STEP_MUL[stepIdx];
	}

	private static void changed() {
		ModelManager.saveProfile();
	}

	private static MutableComponent text(String name, double v, String fmt) {
		return Component.literal(name + ": ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(String.format(fmt, v)).withStyle(ChatFormatting.YELLOW));
	}

	private void title(String s, ChatFormatting color) {
		Button b = Button.builder(Component.literal(s).withStyle(color), x -> {}).bounds(X0, y, W, 20).build();
		b.active = false;
		this.addRenderableWidget(b);
		y += ROW;
	}

	private Button button(String label, ChatFormatting color, int x, int w, Runnable r) {
		Button b = Button.builder(Component.literal(label).withStyle(color), x0 -> r.run()).bounds(x, y, w, 20).build();
		this.addRenderableWidget(b);
		return b;
	}

	private void num(String name, DoubleSupplier get, DoubleConsumer set, double step, String fmt) {
		int yy = y;
		y += ROW;
		Button label = Button.builder(text(name, get.getAsDouble(), fmt), b -> {}).bounds(X0 + 24, yy, W - 48, 20).build();
		label.active = false;
		Button minus = Button.builder(Component.literal("-"), b -> {
			set.accept(get.getAsDouble() - step * mul());
			changed();
			label.setMessage(text(name, get.getAsDouble(), fmt));
		}).bounds(X0, yy, 22, 20).build();
		Button plus = Button.builder(Component.literal("+"), b -> {
			set.accept(get.getAsDouble() + step * mul());
			changed();
			label.setMessage(text(name, get.getAsDouble(), fmt));
		}).bounds(X0 + W - 22, yy, 22, 20).build();
		this.addRenderableWidget(label);
		this.addRenderableWidget(minus);
		this.addRenderableWidget(plus);
	}

	private static double wrap(double deg) {
		double d = deg % 360.0;
		if (d > 180) d -= 360;
		if (d <= -180) d += 360;
		return d;
	}

	private void reopen() {
		SettingsScreen.open(this.minecraft, new ModelEditorScreen(parent));
	}

	@Override
	protected void init() {
		JetState.editorOpen = true;
		y = 6;
		ModelProfile pr = ModelManager.profile;

		title("Model Editor: " + ModelManager.currentName, ChatFormatting.GOLD);
		int half = (W - 4) / 2;
		Button pt = button(page == 0 ? "> Shape <" : "Shape", page == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY, X0, half, () -> {
			if (page != 0) { page = 0; reopen(); }
		});
		Button pa = button(page == 1 ? "> Flames <" : "Flames", page == 1 ? ChatFormatting.YELLOW : ChatFormatting.GRAY, X0 + half + 4, half, () -> {
			if (page != 1) { page = 1; reopen(); }
		});
		y += ROW;

		Button stepBtn = Button.builder(Component.literal("Step: " + STEP_NAMES[stepIdx]).withStyle(ChatFormatting.AQUA), b -> {
			stepIdx = (stepIdx + 1) % STEP_MUL.length;
			b.setMessage(Component.literal("Step: " + STEP_NAMES[stepIdx]).withStyle(ChatFormatting.AQUA));
		}).bounds(X0, y, W, 20).build();
		this.addRenderableWidget(stepBtn);
		y += ROW;

		if (page == 0) {
			num("Size (blocks)", () -> pr.size, v -> pr.size = Math.max(0.5, Math.min(200, v)), 0.5, "%.1f");
			num("Rotate X", () -> pr.rotX, v -> pr.rotX = wrap(v), 15, "%.0f");
			num("Rotate Y", () -> pr.rotY, v -> pr.rotY = wrap(v), 15, "%.0f");
			num("Rotate Z", () -> pr.rotZ, v -> pr.rotZ = wrap(v), 15, "%.0f");
			num("Move side", () -> pr.offX, v -> pr.offX = Math.max(-50, Math.min(50, v)), 0.25, "%.2f");
			num("Move up", () -> pr.offY, v -> pr.offY = Math.max(-50, Math.min(50, v)), 0.25, "%.2f");
			num("Move fwd", () -> pr.offZ, v -> pr.offZ = Math.max(-50, Math.min(50, v)), 0.25, "%.2f");
			int w2 = (W - 4) / 2;
			button("Flip nose", ChatFormatting.WHITE, X0, w2, () -> { pr.rotY = wrap(pr.rotY + 180); changed(); reopen(); });
			button("Reset shape", ChatFormatting.RED, X0 + w2 + 4, w2, () -> { pr.resetTransform(); changed(); reopen(); });
			y += ROW;
		} else {
			Button mode = Button.builder(Component.literal("Flames: " + JetConfig.FLAME_NAMES[JetConfig.flameMode]).withStyle(ChatFormatting.WHITE), b -> {
				JetConfig.flameMode = (JetConfig.flameMode + 1) % JetConfig.FLAME_NAMES.length;
				JetConfig.save();
				b.setMessage(Component.literal("Flames: " + JetConfig.FLAME_NAMES[JetConfig.flameMode]).withStyle(ChatFormatting.WHITE));
			}).bounds(X0, y, W, 20).build();
			this.addRenderableWidget(mode);
			y += ROW;

			int n = pr.burners.size();
			if (selected >= n) selected = Math.max(0, n - 1);
			int w3 = (W - 8) / 3;
			button("Add", ChatFormatting.GREEN, X0, w3, () -> {
				pr.burners.add(new ModelProfile.Afterburner());
				selected = pr.burners.size() - 1;
				changed();
				reopen();
			});
			button("Mirror", ChatFormatting.AQUA, X0 + w3 + 4, w3, () -> {
				if (selected < pr.burners.size()) {
					ModelProfile.Afterburner c = pr.burners.get(selected).copy();
					c.x = -c.x;
					pr.burners.add(c);
					selected = pr.burners.size() - 1;
					changed();
					reopen();
				}
			});
			button("Remove", ChatFormatting.RED, X0 + 2 * (w3 + 4), w3, () -> {
				if (selected < pr.burners.size()) {
					pr.burners.remove(selected);
					selected = Math.max(0, selected - 1);
					changed();
					reopen();
				}
			});
			y += ROW;

			if (n == 0) {
				title("No afterburners yet - press Add", ChatFormatting.GRAY);
			} else {
				int yy = y;
				y += ROW;
				Button prev = Button.builder(Component.literal("<"), b -> {
					selected = (selected + n - 1) % n;
					reopen();
				}).bounds(X0, yy, 22, 20).build();
				Button lab = Button.builder(Component.literal("Flame " + (selected + 1) + " / " + n).withStyle(ChatFormatting.YELLOW), b -> {})
						.bounds(X0 + 24, yy, W - 48, 20).build();
				lab.active = false;
				Button next = Button.builder(Component.literal(">"), b -> {
					selected = (selected + 1) % n;
					reopen();
				}).bounds(X0 + W - 22, yy, 22, 20).build();
				this.addRenderableWidget(prev);
				this.addRenderableWidget(lab);
				this.addRenderableWidget(next);

				ModelProfile.Afterburner a = pr.burners.get(selected);
				num("Side (+right)", () -> a.x, v -> a.x = Math.max(-60, Math.min(60, v)), 0.5, "%.1f%%");
				num("Height", () -> a.y, v -> a.y = Math.max(-60, Math.min(60, v)), 0.5, "%.1f%%");
				num("Front/back", () -> a.z, v -> a.z = Math.max(-80, Math.min(80, v)), 0.5, "%.1f%%");
				num("Length", () -> a.len, v -> a.len = Math.max(1, Math.min(100, v)), 1, "%.1f%%");
				num("Width", () -> a.rad, v -> a.rad = Math.max(0.3, Math.min(30, v)), 0.25, "%.2f%%");
				Button col = Button.builder(Component.literal("Colour preset: " + presetName(a)).withStyle(ChatFormatting.WHITE), b -> {
					int idx = (presetIndex(a) + 1) % COLORS.length;
					a.r = COLORS[idx][0]; a.g = COLORS[idx][1]; a.b = COLORS[idx][2];
					changed();
					reopen();
				}).bounds(X0, y, W, 20).build();
				this.addRenderableWidget(col);
				y += ROW;
				num("Red", () -> a.r, v -> a.r = (int) Math.max(0, Math.min(255, Math.round(v))), 16, "%.0f");
				num("Green", () -> a.g, v -> a.g = (int) Math.max(0, Math.min(255, Math.round(v))), 16, "%.0f");
				num("Blue", () -> a.b, v -> a.b = (int) Math.max(0, Math.min(255, Math.round(v))), 16, "%.0f");
			}
		}

		// view controls + done
		int q = (W - 12) / 4;
		button("<", ChatFormatting.WHITE, X0, q, () -> JetState.previewYaw -= 30f);
		button(">", ChatFormatting.WHITE, X0 + (q + 4), q, () -> JetState.previewYaw += 30f);
		button("Up", ChatFormatting.WHITE, X0 + 2 * (q + 4), q, () -> JetState.previewPitch = Math.max(-80f, JetState.previewPitch - 20f));
		button("Dn", ChatFormatting.WHITE, X0 + 3 * (q + 4), q, () -> JetState.previewPitch = Math.min(80f, JetState.previewPitch + 20f));
		y += ROW;
		button("Done", ChatFormatting.GREEN, X0, W, this::onClose);
	}

	private static int presetIndex(ModelProfile.Afterburner a) {
		for (int i = 0; i < COLORS.length; i++) {
			if (COLORS[i][0] == a.r && COLORS[i][1] == a.g && COLORS[i][2] == a.b) return i;
		}
		return -1;
	}

	private static String presetName(ModelProfile.Afterburner a) {
		int i = presetIndex(a);
		return i < 0 ? "Custom" : COLOR_NAMES[i];
	}

	public void removed() {
		JetState.editorOpen = false;
		ModelManager.saveProfile();
	}

	@Override
	public void onClose() {
		JetState.previewYaw = 0f;
		JetState.previewPitch = 0f;
		ModelManager.saveProfile();
		SettingsScreen.open(this.minecraft, parent);
	}
}
