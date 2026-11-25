package com.mig25;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for the "Tactical Glass" screens. Vanilla Buttons stay as invisible-to-the-eye click targets
 * (so input, focus and narration keep working); this class paints dark glass panels, glowing borders and
 * animated toggles on top of them from extractRenderState.
 */
public abstract class TacticalScreen extends Screen {
	public static final int STYLE_BUTTON = 0;  // centred label
	public static final int STYLE_TOGGLE = 1;  // label + sliding switch
	public static final int STYLE_VALUE = 2;   // label + value
	public static final int STYLE_TAB = 3;     // sidebar tab
	public static final int STYLE_ROW = 4;     // list row
	public static final int STYLE_SMALL = 5;   // square +/- button
	public static final int STYLE_DANGER = 6;
	public static final int STYLE_PRIMARY = 7;
	public static final int STYLE_READOUT = 8; // non-clickable text box

	protected static final int TEXT = 0xFFE6EEF5;
	protected static final int DIM = 0xFF8A97A6;

	protected static final class Ui {
		Button btn;
		int x, y, w, h, style;
		Supplier<String> label;
		Supplier<String> value;
		BooleanSupplier toggle;
		BooleanSupplier selected;
		boolean enabled = true;
		float hover = 0f, anim = 0f;
	}

	private static final class Txt {
		int x, y, color;
		boolean center;
		Supplier<String> text;
	}

	protected final List<Ui> ui = new ArrayList<>();
	private final List<Txt> texts = new ArrayList<>();
	protected final Screen parent;

	protected TacticalScreen(String title, Screen parent) {
		super(Component.literal(title));
		this.parent = parent;
	}

	// ------------------------------------------------------------ colour helpers
	protected static int accent() {
		return JetConfig.hudColor();
	}

	protected static int alpha(int rgb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0x00FFFFFF);
	}

	protected static int mix(int c1, int c2, float t) {
		int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
		int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
		int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
		int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	// ------------------------------------------------------------ widget creation
	protected Ui add(int x, int y, int w, int h, int style, Supplier<String> label, Runnable click) {
		Ui u = new Ui();
		u.x = x; u.y = y; u.w = w; u.h = h; u.style = style;
		u.label = label;
		if (style != STYLE_READOUT) {
			Button b = Button.builder(Component.literal(label.get()), btn -> {
				if (!u.enabled) return;
				click.run();
				JetConfig.save();
			}).bounds(x, y, w, h).build();
			u.btn = b;
			this.addRenderableWidget(b);
		}
		ui.add(u);
		return u;
	}

	protected Ui button(int x, int y, int w, int h, String label, Runnable click) {
		return add(x, y, w, h, STYLE_BUTTON, () -> label, click);
	}

	protected Ui toggle(int x, int y, int w, String label, BooleanSupplier state, Runnable flip) {
		Ui u = add(x, y, w, 20, STYLE_TOGGLE, () -> label, flip);
		u.toggle = state;
		u.anim = state.getAsBoolean() ? 1f : 0f;
		return u;
	}

	protected Ui value(int x, int y, int w, String label, Supplier<String> value, Runnable next) {
		Ui u = add(x, y, w, 20, STYLE_VALUE, () -> label, next);
		u.value = value;
		return u;
	}

	protected void text(int x, int y, int color, boolean center, Supplier<String> t) {
		Txt tx = new Txt();
		tx.x = x; tx.y = y; tx.color = color; tx.center = center; tx.text = t;
		texts.add(tx);
	}

	protected void text(int x, int y, int color, String s) {
		text(x, y, color, false, () -> s);
	}

	// ------------------------------------------------------------ drawing
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		drawBackdrop(g);
		drawChrome(g, mouseX, mouseY);
		for (Ui u : ui) drawUi(g, u, mouseX, mouseY);
		for (Txt t : texts) {
			String s = t.text.get();
			int w = this.minecraft.font.width(s);
			g.text(this.minecraft.font, s, t.center ? t.x - w / 2 : t.x, t.y, t.color, false);
		}
		drawExtras(g, mouseX, mouseY);
	}

	protected abstract void drawBackdrop(GuiGraphicsExtractor g);

	protected abstract void drawChrome(GuiGraphicsExtractor g, int mouseX, int mouseY);

	protected void drawExtras(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	protected void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, boolean glow) {
		int ac = accent();
		g.fill(x, y, x + w, y + h, fill);
		g.outline(x, y, w, h, alpha(ac, 0xB0));
		if (glow) g.outline(x - 1, y - 1, w + 2, h + 2, alpha(ac, 0x38));
		// small corner ticks for the cockpit look
		g.fill(x, y, x + 6, y + 1, ac);
		g.fill(x, y, x + 1, y + 6, ac);
		g.fill(x + w - 6, y + h - 1, x + w, y + h, ac);
		g.fill(x + w - 1, y + h - 6, x + w, y + h, ac);
	}

	private void drawUi(GuiGraphicsExtractor g, Ui u, int mx, int my) {
		int ac = accent();
		boolean inside = mx >= u.x && mx < u.x + u.w && my >= u.y && my < u.y + u.h;
		float target = inside && u.enabled && u.style != STYLE_READOUT ? 1f : 0f;
		u.hover += (target - u.hover) * 0.28f;
		boolean selected = u.selected != null && u.selected.getAsBoolean();
		if (u.toggle != null) u.anim += ((u.toggle.getAsBoolean() ? 1f : 0f) - u.anim) * 0.3f;

		int faceBase = switch (u.style) {
			case STYLE_DANGER -> 0xF0220F12;
			case STYLE_PRIMARY -> alpha(mix(0xFF101820, ac, 0.28f), 0xF2);
			case STYLE_READOUT -> 0xE00A0F15;
			default -> 0xF0111820;
		};
		int faceHover = switch (u.style) {
			case STYLE_DANGER -> 0xF0381A1E;
			default -> 0xF01B2733;
		};
		int face = mix(faceBase, faceHover, u.hover);
		if (selected) face = mix(face, alpha(ac, 0xF0), 0.18f);
		g.fill(u.x, u.y, u.x + u.w, u.y + u.h, face);

		int borderColor = u.style == STYLE_DANGER ? 0xFFFF5555 : ac;
		int bAlpha = (int) (0x50 + 0xB0 * Math.max(u.hover, selected ? 1f : 0f));
		if (u.style == STYLE_READOUT) bAlpha = 0x40;
		g.outline(u.x, u.y, u.w, u.h, alpha(borderColor, bAlpha));
		if (u.hover > 0.2f) g.outline(u.x - 1, u.y - 1, u.w + 2, u.h + 2, alpha(borderColor, (int) (0x40 * u.hover)));
		if (u.style == STYLE_TAB && (selected)) {
			g.fill(u.x, u.y, u.x + 3, u.y + u.h, ac);
		}

		var font = this.minecraft.font;
		String label = u.label.get();
		int ty = u.y + (u.h - 8) / 2;
		int textColor = u.enabled ? TEXT : DIM;
		switch (u.style) {
			case STYLE_TOGGLE -> {
				g.text(font, label, u.x + 7, ty, textColor, false);
				int tw = 26, th = 10;
				int tx = u.x + u.w - tw - 6, tyy = u.y + (u.h - th) / 2;
				g.fill(tx, tyy, tx + tw, tyy + th, mix(0xFF252E39, alpha(ac, 0xFF), u.anim * 0.85f));
				g.outline(tx, tyy, tw, th, alpha(ac, 0x90));
				int kx = tx + 1 + (int) (u.anim * (tw - th));
				g.fill(kx, tyy + 1, kx + th - 2, tyy + th - 1, 0xFFFFFFFF);
			}
			case STYLE_VALUE -> {
				g.text(font, label, u.x + 7, ty, textColor, false);
				String v = u.value == null ? "" : u.value.get();
				g.text(font, v, u.x + u.w - 7 - font.width(v), ty, ac, false);
			}
			case STYLE_TAB, STYLE_ROW -> g.text(font, label, u.x + 9, ty, selected ? ac : textColor, false);
			case STYLE_READOUT -> g.text(font, label, u.x + (u.w - font.width(label)) / 2, ty, TEXT, false);
			case STYLE_PRIMARY -> g.text(font, label, u.x + (u.w - font.width(label)) / 2, ty, 0xFFFFFFFF, false);
			case STYLE_DANGER -> g.text(font, label, u.x + (u.w - font.width(label)) / 2, ty, 0xFFFF8080, false);
			default -> g.text(font, label, u.x + (u.w - font.width(label)) / 2, ty, textColor, false);
		}
	}
}
