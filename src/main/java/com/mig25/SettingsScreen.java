package com.mig25;

import java.util.function.Supplier;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple settings menu made of cycle buttons. Opened with the "Open MiG-25 Settings" key (default J). */
public class SettingsScreen extends Screen {
	private final Screen parent;
	private int row;

	public SettingsScreen(Screen parent) {
		super(Component.literal("MiG-25 Elytra Settings"));
		this.parent = parent;
	}

	private static String onOff(boolean b) {
		return b ? "ON" : "OFF";
	}

	private void add(Supplier<String> text, Runnable click) {
		int w = 240;
		int x = this.width / 2 - w / 2;
		int y = 28 + row * 22;
		row++;
		Button btn = Button.builder(Component.literal(text.get()), b -> {
			click.run();
			JetConfig.save();
			b.setMessage(Component.literal(text.get()));
		}).bounds(x, y, w, 20).build();
		this.addRenderableWidget(btn);
	}

	@Override
	protected void init() {
		row = 0;
		Button title = Button.builder(Component.literal("== MiG-25 Elytra Settings =="), b -> {})
				.bounds(this.width / 2 - 120, 4, 240, 20).build();
		title.active = false;
		this.addRenderableWidget(title);

		add(() -> "Jet model: " + onOff(JetConfig.enabled), () -> JetConfig.enabled = !JetConfig.enabled);
		add(() -> String.format("Size: %.1f blocks long (%d%%)", JetConfig.lengthBlocks(), Math.round(JetConfig.scale() * 100)),
				() -> JetConfig.sizeIndex = (JetConfig.sizeIndex + 1) % JetConfig.SIZES.length);
		add(() -> "Landing gear: " + JetConfig.GEAR_NAMES[JetConfig.gearMode],
				() -> JetConfig.gearMode = (JetConfig.gearMode + 1) % JetConfig.GEAR_NAMES.length);
		add(() -> "Fighter HUD: " + onOff(JetConfig.hud), () -> JetConfig.hud = !JetConfig.hud);
		add(() -> "HUD colour: " + JetConfig.COLOR_NAMES[JetConfig.colorIndex],
				() -> JetConfig.colorIndex = (JetConfig.colorIndex + 1) % JetConfig.COLORS.length);
		add(() -> "Trigger: " + (JetConfig.onlyGliding ? "only while gliding" : "whenever elytra is worn"),
				() -> JetConfig.onlyGliding = !JetConfig.onlyGliding);
		add(() -> "Auto third person: " + onOff(JetConfig.forceThirdPerson),
				() -> JetConfig.forceThirdPerson = !JetConfig.forceThirdPerson);
		add(() -> "Push jet ahead of camera: " + onOff(JetConfig.pushAhead),
				() -> JetConfig.pushAhead = !JetConfig.pushAhead);
		add(() -> "Do a Barrel Roll support: " + onOff(JetConfig.barrelRoll),
				() -> JetConfig.barrelRoll = !JetConfig.barrelRoll);
		add(() -> "Invert barrel roll direction: " + onOff(JetConfig.invertRoll),
				() -> JetConfig.invertRoll = !JetConfig.invertRoll);
		add(() -> "Bank when turning (no barrel roll mod): " + onOff(JetConfig.autoBank),
				() -> JetConfig.autoBank = !JetConfig.autoBank);

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.width / 2 - 120, 28 + row * 22 + 6, 240, 20).build());
	}

	@Override
	public void onClose() {
		JetConfig.save();
		this.minecraft.setScreen(parent);
	}
}
