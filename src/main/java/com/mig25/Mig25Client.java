package com.mig25;

import java.lang.reflect.Constructor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class Mig25Client implements ClientModInitializer {
	public static final String ID = "mig25";
	private static final int FULL_BRIGHT = 0xF000F0;

	private static final Identifier TEX_BODY = Identifier.fromNamespaceAndPath(ID, "textures/entity/mig25_color.png");
	private static final Identifier TEX_NOZZLE = Identifier.fromNamespaceAndPath(ID, "textures/entity/mig25_nozzle.png");
	private static final Identifier TEX_GLASS = Identifier.fromNamespaceAndPath(ID, "textures/entity/mig25_glass.png");

	/** Immutable snapshot taken during extraction, used when submitting geometry. */
	private record Frame(double x, double y, double z, float yaw, float pitch, float roll, float ahead,
			boolean gear, LoadedModel model, ModelProfile prof, float thr, boolean flames, float pvYaw, float pvPitch) {}

	private interface TriSink {
		void tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int argb);
	}

	private static volatile Frame frame = null;
	private static KeyMapping settingsKey;
	private static KeyMapping freeLookKey;
	private static KeyMapping.Category category;

	@Override
	public void onInitializeClient() {
		JetConfig.load();
		JetModel.load();
		ModelManager.init();
		settingsKey = registerKey("key.mig25.settings", 74);     // J
		freeLookKey = registerKey("key.mig25.freelook", 342);    // Left Alt

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			JetState.tick(mc);
			FreeLook.tick(freeLookKey != null && freeLookKey.isDown());
			JetAudio.tick(mc);
			if (settingsKey != null) {
				while (settingsKey.consumeClick()) {
					SettingsScreen.open(mc, new SettingsScreen(null));
				}
			}
		});

		LevelExtractionEvents.END_EXTRACTION.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (!JetState.active || p == null) {
				frame = null;
				return;
			}
			float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			double x = Mth.lerp((double) pt, p.xo, p.getX());
			double y = Mth.lerp((double) pt, p.yo, p.getY()) + p.getBbHeight() * 0.5;
			double z = Mth.lerp((double) pt, p.zo, p.getZ());
			float yaw = p.getViewYRot(pt);
			float pitch = p.isFallFlying() ? p.getViewXRot(pt) : 0f;

			float roll;
			Float dabr = JetConfig.barrelRoll ? BarrelRollCompat.roll(p, pt) : null;
			if (dabr != null) {
				roll = JetConfig.invertRoll ? -dabr : dabr;
				pitch = p.getViewXRot(pt); // with Do a Barrel Roll the pitch is meaningful even off the ground
			} else if (JetConfig.autoBank) {
				roll = Mth.lerp(pt, JetState.prevRoll, JetState.roll);
			} else {
				roll = 0f;
			}

			LoadedModel model = ModelManager.model();
			ModelProfile prof = ModelManager.profile;
			float ahead = 0f;
			if (JetConfig.pushAhead) {
				// vanilla third-person camera sits 4 blocks behind the player: keep the tail in front of it
				ahead = (float) Math.max(0.0, prof.size * 0.5 - 2.5);
			}
			boolean gliding = p.isFallFlying();
			boolean editor = JetState.editorOpen;
			boolean gear = JetConfig.gearMode == 1 || (JetConfig.gearMode == 0 && !gliding);
			double speed = p.getDeltaMovement().length() * 20.0;
			float thr = editor ? 1f : gliding ? (float) Math.max(0.4, Math.min(1.0, 0.4 + speed / 45.0)) : 0.2f;
			boolean flames = JetConfig.flameMode == 0 || (JetConfig.flameMode == 1 && (gliding || editor));
			frame = new Frame(x, y, z, yaw, pitch, roll, ahead, gear, model, prof, thr, flames,
					editor ? JetState.previewYaw : 0f, editor ? JetState.previewPitch : 0f);
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Frame f = frame;
			if (f == null || f.model() == null) return;
			ModelProfile pr = f.prof();
			LoadedModel model = f.model();
			if (model.builtin && JetModel.groups.length == 0) return;
			Vec3 cam = context.levelState().cameraRenderState.pos;
			PoseStack pose = context.poseStack();
			pose.pushPose();
			pose.translate(f.x() - cam.x, f.y() - cam.y, f.z() - cam.z);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-f.yaw())));
			pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(f.pitch())));
			pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(f.roll())));
			pose.translate(0.0, 0.0, f.ahead());
			if (f.pvYaw() != 0f) pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(f.pvYaw())));
			if (f.pvPitch() != 0f) pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(f.pvPitch())));
			pose.translate(pr.offX, pr.offY, pr.offZ);
			if (f.flames() && !pr.burners.isEmpty()) submitFlames(context, pose, f);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(pr.rotY)));
			pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(pr.rotX)));
			pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(pr.rotZ)));
			float sc = (float) (pr.size / Math.max(1e-6, model.longest));
			pose.scale(sc, sc, sc);
			submitModel(context, pose, model, f.gear());
			pose.popPose();
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ID, "jet_hud"), JetHud::render);
	}

	/** Registers a key. Built by reflection so either 26.x KeyMapping constructor shape works. */
	private static KeyMapping registerKey(String translationKey, int code) {
		try {
			if (category == null) category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
			KeyMapping made = null;
			for (Constructor<?> c : KeyMapping.class.getConstructors()) {
				Class<?>[] p = c.getParameterTypes();
				if (p.length == 3 && p[0] == String.class && p[1] == int.class && p[2] == KeyMapping.Category.class) {
					made = (KeyMapping) c.newInstance(translationKey, code, category);
					break;
				}
				if (p.length == 4 && p[0] == String.class && p[1].isEnum() && p[2] == int.class
						&& p[3] == KeyMapping.Category.class) {
					Object type = null;
					for (Object o : p[1].getEnumConstants()) {
						if (((Enum<?>) o).name().equals("KEYSYM")) type = o;
					}
					made = (KeyMapping) c.newInstance(translationKey, type, code, category);
					break;
				}
			}
			if (made != null) return KeyMappingHelper.registerKeyMapping(made);
			System.err.println("[mig25] no usable KeyMapping constructor; key unavailable: " + translationKey);
		} catch (Throwable t) {
			System.err.println("[mig25] could not register key " + translationKey + ": " + t);
		}
		return null;
	}

	private static void submitModel(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, LoadedModel m, boolean gear) {
		if (m.builtin) {
			JetModel.Group[] g = m.groups;
			if (g.length > 0) submitGroup(context, pose, g[0], RenderTypes.entityTranslucent(TEX_BODY), 0xFFFFFFFF);
			if (g.length > 1) submitGroup(context, pose, g[1], RenderTypes.entityTranslucent(TEX_NOZZLE), 0xFFFFFFFF);
			if (gear && g.length > 3) submitGroup(context, pose, g[3], RenderTypes.entityTranslucent(TEX_BODY), 0xFFFFFFFF);
			if (g.length > 2) submitGroup(context, pose, g[2], RenderTypes.entityTranslucent(TEX_GLASS), 0x59B8D0E0);
		} else {
			for (JetModel.Group g : m.groups) {
				submitGroup(context, pose, g, RenderTypes.entityTranslucent(TEX_GLASS), 0xFFFFFFFF);
			}
		}
	}

	private static void submitGroup(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, JetModel.Group g, RenderType type, int argb) {
		context.submitNodeCollector().submitCustomGeometry(pose, type, (p, buf) -> {
			float[] v = g.v;
			int[] idx = g.idx;
			int[] col = g.col;
			for (int t = 0; t + 2 < idx.length; t += 3) {
				for (int k = 0; k < 4; k++) { // triangle emitted as a quad (last vertex repeated)
					int vi = idx[t + Math.min(k, 2)];
					int o = vi * 8;
					buf.addVertex(p, v[o], v[o + 1], v[o + 2])
							.setColor(col != null ? col[vi] : argb)
							.setUv(v[o + 6], v[o + 7])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(FULL_BRIGHT)
							.setNormal(p, v[o + 3], v[o + 4], v[o + 5]);
				}
			}
		});
	}

	private static int argb(int a, int r, int g, int b) {
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Glowing cones behind each configured afterburner (drawn in the jet's own frame, before the model rotation). */
	private static void submitFlames(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, Frame f) {
		ModelProfile pr = f.prof();
		double size = pr.size;
		float t = (float) ((System.nanoTime() / 1.0e9) % 10000.0);
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEX_GLASS), (p, buf) -> {
			int[] front = {0, 1, 2, 2};
			int[] back = {0, 2, 1, 1};
			TriSink sink = (ax, ay, az, bx, by, bz, cx, cy, cz, col) -> {
				float[] vv = {ax, ay, az, bx, by, bz, cx, cy, cz};
				for (int[] order : new int[][] {front, back}) {
					for (int k = 0; k < 4; k++) {
						int o = order[k] * 3;
						buf.addVertex(p, vv[o], vv[o + 1], vv[o + 2])
								.setColor(col)
								.setUv(0.5f, 0.5f)
								.setOverlay(OverlayTexture.NO_OVERLAY)
								.setLight(FULL_BRIGHT)
								.setNormal(p, 0f, 1f, 0f);
					}
				}
			};
			int i = 0;
			for (ModelProfile.Afterburner b : pr.burners) {
				float bx = (float) (-b.x / 100.0 * size); // x is right-positive in the editor, model +X is left
				float by = (float) (b.y / 100.0 * size);
				float bz = (float) (b.z / 100.0 * size);
				float flick = 0.9f + 0.1f * (float) Math.sin(t * 38f + i * 1.7f) + 0.05f * (float) Math.sin(t * 83f + i);
				float len = (float) (b.len / 100.0 * size) * (0.35f + 0.65f * f.thr()) * flick;
				float rad = (float) (b.rad / 100.0 * size);
				cone(sink, bx, by, bz, rad, len, argb(120, b.r, b.g, b.b), argb(150, b.r, b.g, b.b));
				int cr = b.r + (255 - b.r) * 6 / 10, cg = b.g + (255 - b.g) * 6 / 10, cb = b.b + (255 - b.b) * 6 / 10;
				cone(sink, bx, by, bz, rad * 0.55f, len * 0.7f, argb(215, cr, cg, cb), argb(235, cr, cg, cb));
				i++;
			}
		});
	}

	private static void cone(TriSink s, float cx, float cy, float cz, float rad, float len, int sideColor, int baseColor) {
		final int n = 10;
		for (int i = 0; i < n; i++) {
			double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
			float x0 = cx + rad * (float) Math.cos(a0), y0 = cy + rad * (float) Math.sin(a0);
			float x1 = cx + rad * (float) Math.cos(a1), y1 = cy + rad * (float) Math.sin(a1);
			s.tri(cx, cy, cz - len, x0, y0, cz, x1, y1, cz, sideColor); // side, tip points backwards (-Z)
			s.tri(cx, cy, cz, x0, y0, cz, x1, y1, cz, baseColor);       // glowing disc at the nozzle
		}
	}
}
