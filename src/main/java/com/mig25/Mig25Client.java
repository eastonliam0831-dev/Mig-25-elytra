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
			boolean gearVisible, float gearPos, LoadedModel model, ModelProfile prof, Rig rig,
			float cRoll, float cPitch, float cYaw, float cBrake,
			float thr, boolean flames, float mach, double[] headTips, float headIntensity,
			float pvYaw, float pvPitch) {}

	private interface TriSink {
		void tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int argb);
	}

	private static volatile Frame frame = null;
	private static KeyMapping settingsKey;
	private static KeyMapping freeLookKey;
	private static KeyMapping gearKey;
	private static KeyMapping.Category category;

	@Override
	public void onInitializeClient() {
		ModelManager.migrateOldFolder();
		JetConfig.load();
		JetModel.load();
		ModelManager.init();
		settingsKey = registerKey("key.mig25.settings", 74);     // J
		freeLookKey = registerKey("key.mig25.freelook", 342);    // Left Alt
		gearKey = registerKey("key.mig25.gear", 71);             // G

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			JetState.tick(mc);
			FreeLook.tick(freeLookKey != null && freeLookKey.isDown());
			JetAudio.tick(mc);
			if (gearKey != null) {
				while (gearKey.consumeClick()) JetState.toggleGear();
			}
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
			Rig rig = ModelManager.rig;
			float ahead = 0f;
			if (JetConfig.pushAhead) {
				// vanilla third-person camera sits 4 blocks behind the player: keep the tail in front of it
				ahead = (float) Math.max(0.0, prof.size * 0.5 - 2.5);
			}
			boolean gliding = p.isFallFlying();
			boolean editor = JetState.editorOpen;
			double speed = JetState.speed;
			float thr = editor ? 1f : gliding ? (float) Math.max(0.4, Math.min(1.0, 0.4 + speed / 45.0)) : 0.2f;
			boolean flames = JetConfig.flameMode == 0 || (JetConfig.flameMode == 1 && (gliding || editor));
			float mach = 0f;
			if (JetConfig.machCone && gliding) {
				mach = (float) Math.max(0.0, Math.min(1.0, (speed - JetConfig.MACH_SPEEDS[JetConfig.machIdx]) / 20.0));
			}
			double[] head = null;
			float headI = 0f;
			if (JetConfig.vaporTrails && gliding) {
				head = WingTips.toWorld(x, y, z, yaw, p.getViewXRot(pt), roll, ahead, WingTips.local(model, prof));
				java.util.List<Trails.Sample> ts = Trails.snapshot();
				if (!ts.isEmpty() && ts.get(0).age <= 1) headI = ts.get(0).intensity;
			}
			float gp = Mth.lerp(pt, JetState.prevGearPos, JetState.gearPos);
			frame = new Frame(x, y, z, yaw, pitch, roll, ahead, JetState.gearVisible(), gp, model, prof, rig,
					Mth.lerp(pt, JetState.pRoll, JetState.cRoll), Mth.lerp(pt, JetState.pPitch, JetState.cPitch),
					Mth.lerp(pt, JetState.pYaw, JetState.cYaw), Mth.lerp(pt, JetState.pBrake, JetState.cBrake),
					thr, flames, mach, head, headI,
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
			boolean flames = f.flames() && !pr.burners.isEmpty();
			if (flames || f.mach() > 0.02f) submitEffects(context, pose, f, flames);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(pr.rotY)));
			pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(pr.rotX)));
			pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(pr.rotZ)));
			float sc = (float) (pr.size / Math.max(1e-6, model.longest));
			pose.scale(sc, sc, sc);
			submitRig(context, pose, model, f);
			pose.popPose();

			if (f.headTips() != null || !Trails.snapshot().isEmpty()) {
				pose.pushPose();
				submitTrails(context, pose, f, cam);
				pose.popPose();
			}
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

	private static void submitPiece(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, LoadedModel m, Rig.Piece pc) {
		if (m.builtin) {
			switch (pc.src) {
				case 1 -> submitGroup(context, pose, pc.g, RenderTypes.entityTranslucent(TEX_NOZZLE), 0xFFFFFFFF);
				case 2 -> submitGroup(context, pose, pc.g, RenderTypes.entityTranslucent(TEX_GLASS), 0x59B8D0E0);
				default -> submitGroup(context, pose, pc.g, RenderTypes.entityTranslucent(TEX_BODY), 0xFFFFFFFF);
			}
		} else {
			submitGroup(context, pose, pc.g, RenderTypes.entityTranslucent(TEX_GLASS), 0xFFFFFFFF);
		}
	}

	private static float rigAngle(RigPart rp, Frame f) {
		double in = switch (rp.kind) {
			case AILERON -> f.cRoll();
			case ELEVATOR -> f.cPitch();
			case RUDDER -> f.cYaw();
			case AIRBRAKE -> f.cBrake();
			case GEAR -> f.gearPos();
		};
		return (float) (in * rp.gain * rp.maxDeg);
	}

	private static void submitRig(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, LoadedModel m, Frame f) {
		Rig rig = f.rig();
		if (rig == null) {
			for (JetModel.Group g : m.groups) {
				submitGroup(context, pose, g, RenderTypes.entityTranslucent(m.builtin ? TEX_BODY : TEX_GLASS), 0xFFFFFFFF);
			}
			return;
		}
		double unit = m.longest / 100.0; // percent -> model units
		// opaque pieces first, the translucent canopy last
		for (int pass = 0; pass < 2; pass++) {
			boolean glassPass = pass == 1;
			for (Rig.Piece pc : rig.base) {
				boolean glass = m.builtin && pc.src == 2;
				if (glass != glassPass) continue;
				if (m.builtin && pc.src == 3 && !f.gearVisible()) continue;
				submitPiece(context, pose, m, pc);
			}
			if (glassPass) break;
			for (Rig.Piece pc : rig.moving) {
				RigPart rp = pc.part;
				if (rp.isGear() && !f.gearVisible()) continue;
				pose.pushPose();
				if (rp.isGear()) {
					float k = f.gearPos();
					pose.translate(rp.tx * unit * k, rp.ty * unit * k, rp.tz * unit * k);
				}
				float ang = rigAngle(rp, f);
				if (Math.abs(ang) > 0.01f) {
					double len = Math.sqrt(rp.ax * rp.ax + rp.ay * rp.ay + rp.az * rp.az);
					if (len > 1e-6) {
						pose.translate(rp.px * unit, rp.py * unit, rp.pz * unit);
						pose.mulPose(new Matrix4f().rotation((float) Math.toRadians(ang),
								(float) (rp.ax / len), (float) (rp.ay / len), (float) (rp.az / len)));
						pose.translate(-rp.px * unit, -rp.py * unit, -rp.pz * unit);
					}
				}
				submitPiece(context, pose, m, pc);
				pose.popPose();
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
		return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
	}

	/** Afterburner flames and the supersonic Mach cone, drawn in the jet's own frame (before the model rotation). */
	private static void submitEffects(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, Frame f, boolean flames) {
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
			if (flames) {
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
			}
			if (f.mach() > 0.02f) {
				float nose = (float) (size * 0.5);
				float pulse = 0.85f + 0.15f * (float) Math.sin(t * 9f);
				float m = f.mach() * pulse;
				machCone(sink, nose, (float) (size * 1.4), (float) (size * 0.62), argb((int) (70 * m), 190, 225, 255));
				machCone(sink, nose, (float) (size * 1.1), (float) (size * 0.34), argb((int) (55 * m), 235, 245, 255));
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

	/** Open shock cone: tip at the nose, widening towards the tail. */
	private static void machCone(TriSink s, float noseZ, float len, float rad, int color) {
		final int n = 18;
		for (int i = 0; i < n; i++) {
			double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
			s.tri(0f, 0f, noseZ,
					rad * (float) Math.cos(a0), rad * (float) Math.sin(a0), noseZ - len,
					rad * (float) Math.cos(a1), rad * (float) Math.sin(a1), noseZ - len, color);
		}
	}

	/** Camera-facing ribbons behind both wingtips; thicker and brighter in hard turns. Coordinates are camera-relative. */
	private static void submitTrails(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, Frame f, Vec3 cam) {
		java.util.List<Trails.Sample> samples = Trails.snapshot();
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
			for (int side = 0; side < 2; side++) {
				double[][] pts = new double[samples.size() + 1][];
				float[] inten = new float[samples.size() + 1];
				int[] age = new int[samples.size() + 1];
				long[] tick = new long[samples.size() + 1];
				int n = 0;
				if (f.headTips() != null && !samples.isEmpty() && samples.get(0).age <= 1) {
					pts[n] = new double[] {f.headTips()[side * 3], f.headTips()[side * 3 + 1], f.headTips()[side * 3 + 2]};
					inten[n] = f.headIntensity();
					age[n] = 0;
					tick[n] = samples.get(0).tickId + 1;
					n++;
				}
				for (Trails.Sample sm : samples) {
					pts[n] = new double[] {sm.tips[side * 3], sm.tips[side * 3 + 1], sm.tips[side * 3 + 2]};
					inten[n] = sm.intensity;
					age[n] = sm.age;
					tick[n] = sm.tickId;
					n++;
				}
				for (int i = 0; i + 1 < n; i++) {
					if (Math.abs(tick[i] - tick[i + 1]) > 1) continue; // gap in the trail
					double[] a = pts[i], b = pts[i + 1];
					double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
					double tx = cam.x - a[0], ty = cam.y - a[1], tz = cam.z - a[2];
					double sx = dy * tz - dz * ty, sy = dz * tx - dx * tz, sz = dx * ty - dy * tx;
					double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
					if (sl < 1e-9) continue;
					float life = 1f - Math.min(age[i], age[i + 1]) / (float) Trails.MAX_AGE;
					float inn = Math.max(inten[i], inten[i + 1]);
					double w = (0.10 + 0.32 * inn) * 0.5;
					sx = sx / sl * w; sy = sy / sl * w; sz = sz / sl * w;
					float ax = (float) (a[0] - cam.x + sx), ay = (float) (a[1] - cam.y + sy), az = (float) (a[2] - cam.z + sz);
					float bx = (float) (a[0] - cam.x - sx), by = (float) (a[1] - cam.y - sy), bz = (float) (a[2] - cam.z - sz);
					float cx = (float) (b[0] - cam.x - sx), cy = (float) (b[1] - cam.y - sy), cz = (float) (b[2] - cam.z - sz);
					float dx2 = (float) (b[0] - cam.x + sx), dy2 = (float) (b[1] - cam.y + sy), dz2 = (float) (b[2] - cam.z + sz);
					int col = argb((int) (190 * life * (0.35f + 0.65f * inn)), 245, 250, 255);
					sink.tri(ax, ay, az, bx, by, bz, cx, cy, cz, col);
					sink.tri(ax, ay, az, cx, cy, cz, dx2, dy2, dz2, col);
				}
			}
		});
	}
}
