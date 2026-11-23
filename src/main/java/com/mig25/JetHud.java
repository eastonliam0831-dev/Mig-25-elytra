package com.mig25;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Fighter-jet style HUD, drawn only while the jet is active. */
public final class JetHud {
	private static int GREEN = 0xFF33FF66;
	private static int DIM = 0x8833FF66;
	private static final int RED = 0xFFFF4444;
	private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private JetHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (!JetState.active || !JetConfig.hud || p == null) return;
		GREEN = JetConfig.hudColor();
		DIM = (GREEN & 0x00FFFFFF) | 0x88000000;

		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		int cx = w / 2, cy = h / 2;

		Vec3 vel = p.getDeltaMovement();
		double speed = vel.length() * 20.0; // blocks per second
		double vs = vel.y * 20.0;
		float yaw = Mth.wrapDegrees(p.getYRot());
		float pitch = p.getXRot();
		float heading = Mth.positiveModulo(yaw + 180f, 360f);

		// --- gun-sight / pipper ---
		g.horizontalLine(cx - 14, cx - 5, cy, GREEN);
		g.horizontalLine(cx + 5, cx + 14, cy, GREEN);
		g.verticalLine(cx, cy - 14, cy - 5, GREEN);
		g.verticalLine(cx, cy + 5, cy + 14, GREEN);
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, GREEN);
		g.outline(cx - 28, cy - 28, 56, 56, DIM);

		// --- pitch ladder ---
		for (int d = -40; d <= 40; d += 10) {
			if (d == 0) continue;
			int y = cy - (int) ((d + pitch) * 3.0f);
			if (Math.abs(y - cy) > 90) continue;
			g.horizontalLine(cx - 40, cx - 18, y, DIM);
			g.horizontalLine(cx + 18, cx + 40, y, DIM);
			g.text(mc.font, String.valueOf(Math.abs(d)), cx - 56, y - 4, DIM, false);
			g.text(mc.font, String.valueOf(Math.abs(d)), cx + 44, y - 4, DIM, false);
		}
		int hy = cy - (int) (pitch * 3.0f);
		if (Math.abs(hy - cy) < 90) {
			g.horizontalLine(cx - 70, cx - 20, hy, GREEN);
			g.horizontalLine(cx + 20, cx + 70, hy, GREEN);
		}

		// --- heading tape (top) ---
		int tapeY = 14, tapeW = 220;
		g.horizontalLine(cx - tapeW / 2, cx + tapeW / 2, tapeY + 12, DIM);
		for (int deg = -60; deg <= 60; deg += 5) {
			float hd = Mth.positiveModulo(Math.round(heading / 5f) * 5f + deg, 360f);
			int x = cx + (int) ((deg - (heading - Math.round(heading / 5f) * 5f)) * (tapeW / 120f));
			if (Math.abs(x - cx) > tapeW / 2) continue;
			boolean major = ((int) hd) % 10 == 0;
			g.verticalLine(x, tapeY + (major ? 6 : 9), tapeY + 12, DIM);
			if (((int) hd) % 45 == 0) {
				String s = DIRS[((int) hd / 45) % 8];
				g.text(mc.font, s, x - mc.font.width(s) / 2, tapeY - 2, GREEN, false);
			}
		}
		g.verticalLine(cx, tapeY + 2, tapeY + 12, GREEN);
		String hdg = String.format("%03d", Math.round(heading) % 360);
		g.text(mc.font, hdg, cx - mc.font.width(hdg) / 2, tapeY + 16, GREEN, false);

		// --- speed (left) / altitude (right) ---
		int boxW = 54;
		g.outline(24, cy - 10, boxW, 20, GREEN);
		g.text(mc.font, String.format("%.0f", speed * 3.6), 30, cy - 4, GREEN, false);
		g.text(mc.font, "KM/H", 24, cy + 14, DIM, false);
		g.text(mc.font, String.format("M %.1f", speed), 24, cy + 26, DIM, false);

		g.outline(w - 24 - boxW, cy - 10, boxW, 20, GREEN);
		g.text(mc.font, String.format("%.0f", p.getY()), w - 18 - boxW, cy - 4, GREEN, false);
		g.text(mc.font, "ALT", w - 24 - boxW, cy + 14, DIM, false);
		int vsCol = vs < -12 ? RED : GREEN;
		g.text(mc.font, String.format("VS %+.0f", vs), w - 24 - boxW, cy + 26, vsCol, false);

		// --- bottom: fuel (elytra durability) + throttle (speed) ---
		ItemStack el = p.getItemBySlot(EquipmentSlot.CHEST);
		int max = Math.max(1, el.getMaxDamage());
		float fuel = Mth.clamp((max - el.getDamageValue()) / (float) max, 0f, 1f);
		int by = h - 52, bw = 90;
		g.text(mc.font, "FUEL", cx - bw - 46, by - 1, DIM, false);
		g.outline(cx - bw - 10, by - 2, bw, 10, fuel < 0.15f ? RED : GREEN);
		g.fill(cx - bw - 8, by, cx - bw - 8 + (int) ((bw - 4) * fuel), by + 6, fuel < 0.15f ? RED : GREEN);
		float thr = Mth.clamp((float) (speed / 40.0), 0f, 1f);
		g.text(mc.font, "THR", cx + 10 + bw + 4, by - 1, DIM, false);
		g.outline(cx + 10, by - 2, bw, 10, GREEN);
		g.fill(cx + 12, by, cx + 12 + (int) ((bw - 4) * thr), by + 6, thr > 0.9f ? 0xFFFFAA33 : GREEN);

		String mode = p.isFallFlying() ? "AIRBORNE" : "STANDBY";
		g.text(mc.font, "MiG-25  " + mode, cx - mc.font.width("MiG-25  " + mode) / 2, h - 36, GREEN, false);
		if (p.isFallFlying() && p.getY() < -44) {
			g.text(mc.font, "PULL UP", cx - mc.font.width("PULL UP") / 2, cy + 44, RED, false);
		}
	}
}
