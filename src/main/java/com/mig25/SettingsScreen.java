package com.mig25;

import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Tabbed control panel: Jet / HUD / Camera / Flight. Opened with the settings key (default J). */
public class SettingsScreen extends Screen {
	private static final String[] TABS = {"Jet", "HUD", "Camera", "Flight"};
	private static int tab = 0;

	private final Screen parent;
	private int slot;
	private int cx;

	public SettingsScreen(Screen parent) {
		super(Component.literal("MiG-25 Elytra Settings"));
		this.parent = parent;
	}

	// ---- label helpers ----
	private static MutableComponent onOff(String name, boolean on) {
		return Component.literal(name + ": ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
	}

	private static MutableComponent value(String name, String v) {
		return Component.literal(name + ": ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(v).withStyle(ChatFormatting.YELLOW));
	}

	private void place(Supplier<MutableComponent> label, Runnable click) {
		int col = slot % 2;
		int row = slot / 2;
		slot++;
		int x = col == 0 ? cx - 150 : cx + 4;
		int y = 64 + row * 24;
		Button btn = Button.builder(label.get(), b -> {
			click.run();
			JetConfig.save();
			b.setMessage(label.get());
		}).bounds(x, y, 146, 20).build();
		this.addRenderableWidget(btn);
	}

	private void toggle(String name, Supplier<Boolean> get, Runnable flip) {
		place(() -> onOff(name, get.get()), flip);
	}

	private void cycle(String name, Supplier<String> get, Runnable next) {
		place(() -> value(name, get.get()), next);
	}

	private void label(String text, int y, ChatFormatting color) {
		Button b = Button.builder(Component.literal(text).withStyle(color), x -> {})
				.bounds(cx - 150, y, 300, 20).build();
		b.active = false;
		this.addRenderableWidget(b);
	}

	@Override
	protected void init() {
		cx = this.width / 2;
		slot = 0;

		label("MiG-25 Elytra  -  Control Panel", 10, ChatFormatting.GOLD);

		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			MutableComponent text = Component.literal(i == tab ? "> " + TABS[i] + " <" : TABS[i])
					.withStyle(i == tab ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
			this.addRenderableWidget(Button.builder(text, b -> {
				if (idx != tab) {
					tab = idx;
					open(this.minecraft, new SettingsScreen(parent));
				}
			}).bounds(cx - 150 + i * 76, 34, 72, 20).build());
		}

		switch (tab) {
			case 0 -> {
				toggle("Jet model", () -> JetConfig.enabled, () -> JetConfig.enabled = !JetConfig.enabled);
				cycle("Size", () -> String.format("%.1f blocks", JetConfig.lengthBlocks()),
						() -> JetConfig.sizeIndex = (JetConfig.sizeIndex + 1) % JetConfig.SIZES.length);
				cycle("Landing gear", () -> JetConfig.GEAR_NAMES[JetConfig.gearMode],
						() -> JetConfig.gearMode = (JetConfig.gearMode + 1) % JetConfig.GEAR_NAMES.length);
				cycle("Trigger", () -> JetConfig.onlyGliding ? "while gliding" : "elytra worn",
						() -> JetConfig.onlyGliding = !JetConfig.onlyGliding);
			}
			case 1 -> {
				toggle("Fighter HUD", () -> JetConfig.hud, () -> JetConfig.hud = !JetConfig.hud);
				cycle("HUD colour", () -> JetConfig.COLOR_NAMES[JetConfig.colorIndex],
						() -> JetConfig.colorIndex = (JetConfig.colorIndex + 1) % JetConfig.COLORS.length);
			}
			case 2 -> {
				toggle("Auto third person", () -> JetConfig.forceThirdPerson,
						() -> JetConfig.forceThirdPerson = !JetConfig.forceThirdPerson);
				toggle("Jet ahead of camera", () -> JetConfig.pushAhead, () -> JetConfig.pushAhead = !JetConfig.pushAhead);
				toggle("Free look", () -> JetConfig.freeLook, () -> JetConfig.freeLook = !JetConfig.freeLook);
				cycle("Look speed", () -> String.format("%d%%", Math.round(JetConfig.LOOK_SENS[JetConfig.freeLookSens] * 100)),
						() -> JetConfig.freeLookSens = (JetConfig.freeLookSens + 1) % JetConfig.LOOK_SENS.length);
				label("Hold the Free Look key (default Left Alt) and move the mouse", 64 + 2 * 24 + 4, ChatFormatting.AQUA);
			}
			default -> {
				toggle("Barrel Roll mod", () -> JetConfig.barrelRoll, () -> JetConfig.barrelRoll = !JetConfig.barrelRoll);
				toggle("Invert roll", () -> JetConfig.invertRoll, () -> JetConfig.invertRoll = !JetConfig.invertRoll);
				toggle("Bank when turning", () -> JetConfig.autoBank, () -> JetConfig.autoBank = !JetConfig.autoBank);
			}
		}

		label("Settings key: J    |    Free look: hold Left Alt    (rebind in Controls)", this.height - 56, ChatFormatting.DARK_GRAY);

		this.addRenderableWidget(Button.builder(Component.literal("Reset all").withStyle(ChatFormatting.RED), b -> {
			JetConfig.reset();
			open(this.minecraft, new SettingsScreen(parent));
		}).bounds(cx - 150, this.height - 30, 146, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done").withStyle(ChatFormatting.GREEN), b -> this.onClose())
				.bounds(cx + 4, this.height - 30, 146, 20).build());
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
