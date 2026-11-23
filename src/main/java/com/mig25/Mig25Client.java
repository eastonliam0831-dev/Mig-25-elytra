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
	private record Frame(double x, double y, double z, float yaw, float pitch, float roll,
			float scale, float ahead, boolean gear) {}

	private static volatile Frame frame = null;
	private static KeyMapping settingsKey;

	@Override
	public void onInitializeClient() {
		JetConfig.load();
		JetModel.load();
		registerKey();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			JetState.tick(mc);
			if (settingsKey != null) {
				while (settingsKey.consumeClick()) {
					mc.setScreen(new SettingsScreen(null));
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

			float scale = JetConfig.scale();
			float ahead = 0f;
			if (JetConfig.pushAhead) {
				// vanilla third-person camera sits 4 blocks behind the player: keep the tail in front of it
				ahead = (float) Math.max(0.0, JetConfig.lengthBlocks() * 0.5 - 2.5);
			}
			boolean gear = JetConfig.gearMode == 1 || (JetConfig.gearMode == 0 && !p.isFallFlying());
			frame = new Frame(x, y, z, yaw, pitch, roll, scale, ahead, gear);
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Frame f = frame;
			if (f == null || JetModel.groups.length == 0) return;
			Vec3 cam = context.levelState().cameraRenderState.pos;
			PoseStack pose = context.poseStack();
			pose.pushPose();
			pose.translate(f.x() - cam.x, f.y() - cam.y, f.z() - cam.z);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-f.yaw())));
			pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(f.pitch())));
			pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(f.roll())));
			pose.translate(0.0, 0.0, f.ahead());
			pose.scale(f.scale(), f.scale(), f.scale());
			submitGroup(context, pose, 0, RenderTypes.entityTranslucent(TEX_BODY), 0xFFFFFFFF);
			submitGroup(context, pose, 1, RenderTypes.entityTranslucent(TEX_NOZZLE), 0xFFFFFFFF);
			if (f.gear()) {
				submitGroup(context, pose, 3, RenderTypes.entityTranslucent(TEX_BODY), 0xFFFFFFFF);
			}
			submitGroup(context, pose, 2, RenderTypes.entityTranslucent(TEX_GLASS), 0x59B8D0E0);
			pose.popPose();
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ID, "jet_hud"), JetHud::render);
	}

	/** Registers the settings key. Built by reflection so either 26.x KeyMapping constructor shape works. */
	private static void registerKey() {
		try {
			KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
			KeyMapping made = null;
			for (Constructor<?> c : KeyMapping.class.getConstructors()) {
				Class<?>[] p = c.getParameterTypes();
				if (p.length == 3 && p[0] == String.class && p[1] == int.class && p[2] == KeyMapping.Category.class) {
					made = (KeyMapping) c.newInstance("key.mig25.settings", 74, cat); // J
					break;
				}
				if (p.length == 4 && p[0] == String.class && p[1].isEnum() && p[2] == int.class
						&& p[3] == KeyMapping.Category.class) {
					Object type = null;
					for (Object o : p[1].getEnumConstants()) {
						if (((Enum<?>) o).name().equals("KEYSYM")) type = o;
					}
					made = (KeyMapping) c.newInstance("key.mig25.settings", type, 74, cat);
					break;
				}
			}
			if (made != null) settingsKey = KeyMappingHelper.registerKeyMapping(made);
			else System.err.println("[mig25] no usable KeyMapping constructor; settings key unavailable");
		} catch (Throwable t) {
			System.err.println("[mig25] could not register settings key: " + t);
		}
	}

	private static void submitGroup(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context,
			PoseStack pose, int group, RenderType type, int argb) {
		if (group >= JetModel.groups.length) return;
		JetModel.Group g = JetModel.groups[group];
		context.submitNodeCollector().submitCustomGeometry(pose, type, (p, buf) -> {
			float[] v = g.v;
			int[] idx = g.idx;
			for (int t = 0; t + 2 < idx.length; t += 3) {
				for (int k = 0; k < 4; k++) { // triangle emitted as a quad (last vertex repeated)
					int o = idx[t + Math.min(k, 2)] * 8;
					buf.addVertex(p, v[o], v[o + 1], v[o + 2])
							.setColor(argb)
							.setUv(v[o + 6], v[o + 7])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(FULL_BRIGHT)
							.setNormal(p, v[o + 3], v[o + 4], v[o + 5]);
				}
			}
		});
	}
}
