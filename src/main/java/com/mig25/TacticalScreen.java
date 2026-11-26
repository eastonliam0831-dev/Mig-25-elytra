package com.mig25;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
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
	public static final int STYLE_SLIDER = 9;  // click or drag to set a value
	public static final int STYLE_AREA = 10;   // invisible click/drag target (colour wheel, viewport)

	protected static final int TEXT = 0xFFE6EEF5;
	protected static final int DIM = 0xFF8A97A6;

	/** Called while the left mouse button is held after pressing inside the widget. */
	protected interface Drag {
		void drag(int mouseX, int mouseY);
	}

	protected static final class Ui {
		Drag drag;
		double min, max;
		DoubleSupplier getD;
		DoubleConsumer setD;
		String fmt = "%.1f";
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
		int x, y, color, maxW = 100000;
		boolean center;
		Supplier<String> text;
	}

	protected final List<Ui> ui = new ArrayList<>();
	private final List<Txt> texts = new ArrayList<>();
	protected final Screen parent;
	protected int lastMouseX, lastMouseY;
	private Ui dragging;
	private boolean wasDown;

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

	/** Slider: click or drag anywhere on it. Shows "label: value" inside the track. */
	protected Ui slider(int x, int y, int w, String label, double min, double max, DoubleSupplier get, DoubleConsumer set, String fmt) {
		Ui[] holder = new Ui[1];
		Ui u = add(x, y, w, 20, STYLE_SLIDER, () -> label, () -> setFromMouse(holder[0], lastMouseX));
		holder[0] = u;
		u.min = min; u.max = max; u.getD = get; u.setD = set; u.fmt = fmt;
		u.drag = (mx, my) -> setFromMouse(u, mx);
		return u;
	}

	private void setFromMouse(Ui u, int mx) {
		double t = (mx - (u.x + 2)) / (double) Math.max(1, u.w - 4);
		t = Math.max(0, Math.min(1, t));
		u.setD.accept(u.min + t * (u.max - u.min));
	}

	/** Invisible clickable/draggable area; click and drag callbacks receive GUI coordinates. */
	protected Ui area(int x, int y, int w, int h, Drag clickOrDrag) {
		Ui u = add(x, y, w, h, STYLE_AREA, () -> "", () -> clickOrDrag.drag(lastMouseX, lastMouseY));
		u.drag = clickOrDrag;
		return u;
	}

	/** Cuts text to fit a pixel width, adding "..." if it was shortened. */
	protected String fit(String s, int maxW) {
		var font = this.minecraft.font;
		if (font.width(s) <= maxW) return s;
		String t = s;
		while (t.length() > 1 && font.width(t + "...") > maxW) t = t.substring(0, t.length() - 1);
		return t + "...";
	}

	/** Called every frame with the mouse state, after widgets are drawn. */
	protected void frameInput(int mouseX, int mouseY, boolean left, boolean right, boolean leftJustPressed) {}

	protected void text(int x, int y, int color, boolean center, Supplier<String> t) {
		Txt tx = new Txt();
		tx.x = x; tx.y = y; tx.color = color; tx.center = center; tx.text = t;
		texts.add(tx);
	}

	protected void text(int x, int y, int color, String s) {
		text(x, y, color, false, () -> s);
	}

	/** Text that is cut to maxW pixels instead of running into neighbouring widgets. */
	protected void textFit(int x, int y, int color, int maxW, Supplier<String> t) {
		Txt tx = new Txt();
		tx.x = x; tx.y = y; tx.color = color; tx.maxW = maxW; tx.text = t;
		texts.add(tx);
	}

	// ------------------------------------------------------------ drawing
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		boolean down = MouseState.left();
		boolean right = MouseState.right();
		boolean justPressed = down && !wasDown;
		if (justPressed) {
			dragging = null;
			for (int i = ui.size() - 1; i >= 0; i--) {
				Ui u = ui.get(i);
				if (u.drag != null && u.enabled && mouseX >= u.x && mouseX < u.x + u.w && mouseY >= u.y && mouseY < u.y + u.h) {
					dragging = u;
					break;
				}
			}
		}
		if (down && dragging != null && !justPressed) dragging.drag.drag(mouseX, mouseY);
		if (!down) dragging = null;
		wasDown = down;
		drawBackdrop(g);
		drawChrome(g, mouseX, mouseY);
		for (Ui u : ui) drawUi(g, u, mouseX, mouseY);
		for (Txt t : texts) {
			String s = fit(t.text.get(), t.maxW);
			int w = this.minecraft.font.width(s);
			g.text(this.minecraft.font, s, t.center ? t.x - w / 2 : t.x, t.y, t.color, false);
		}
		drawExtras(g, mouseX, mouseY);
		frameInput(mouseX, mouseY, down, right, justPressed);
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
		if (u.style == STYLE_AREA) return;
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

		if (u.style == STYLE_SLIDER) {
			double val = u.getD.getAsDouble();
			float frac = (float) Math.max(0, Math.min(1, (val - u.min) / (u.max - u.min)));
			int fw = (int) ((u.w - 2) * frac);
			g.fill(u.x + 1, u.y + 1, u.x + 1 + fw, u.y + u.h - 1, alpha(ac, 0x55));
			int kx = u.x + 1 + fw;
			g.fill(Math.max(u.x + 1, kx - 1), u.y + 1, Math.min(u.x + u.w - 1, kx + 2), u.y + u.h - 1, 0xFFFFFFFF);
			var f2 = this.minecraft.font;
			String lb = u.label.get() + ": " + String.format(u.fmt, val);
			g.text(f2, lb, u.x + (u.w - f2.width(lb)) / 2, u.y + (u.h - 8) / 2, TEXT, false);
			return;
		}
		var font = this.minecraft.font;
		String label = u.label.get();
		int ty = u.y + (u.h - 8) / 2;
		int textColor = u.enabled ? TEXT : DIM;
		switch (u.style) {
			case STYLE_TOGGLE -> {
				g.text(font, fit(label, u.w - 46), u.x + 7, ty, textColor, false);
				int tw = 26, th = 10;
				int tx = u.x + u.w - tw - 6, tyy = u.y + (u.h - th) / 2;
				g.fill(tx, tyy, tx + tw, tyy + th, mix(0xFF252E39, alpha(ac, 0xFF), u.anim * 0.85f));
				g.outline(tx, tyy, tw, th, alpha(ac, 0x90));
				int kx = tx + 1 + (int) (u.anim * (tw - th));
				g.fill(kx, tyy + 1, kx + th - 2, tyy + th - 1, 0xFFFFFFFF);
			}
			case STYLE_VALUE -> {
				String v = u.value == null ? "" : u.value.get();
				int vw = font.width(v);
				g.text(font, fit(label, Math.max(10, u.w - vw - 20)), u.x + 7, ty, textColor, false);
				g.text(font, v, u.x + u.w - 7 - vw, ty, ac, false);
			}
			case STYLE_TAB, STYLE_ROW -> g.text(font, fit(label, u.w - 14), u.x + 9, ty, selected ? ac : textColor, false);
			case STYLE_READOUT -> {
				String fl = fit(label, u.w - 8);
				g.text(font, fl, u.x + (u.w - font.width(fl)) / 2, ty, TEXT, false);
			}
			case STYLE_PRIMARY -> g.text(font, label, u.x + (u.w - font.width(label)) / 2, ty, 0xFFFFFFFF, false);
			case STYLE_DANGER -> g.text(font, label, u.x + (u.w - font.width(label)) / 2, ty, 0xFFFF8080, false);
			default -> {
				String fl = fit(label, u.w - 8);
				g.text(font, fl, u.x + (u.w - font.width(fl)) / 2, ty, textColor, false);
			}
		}
	}
}
