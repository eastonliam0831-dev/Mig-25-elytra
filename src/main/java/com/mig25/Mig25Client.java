package com.mig25;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import org.joml.Matrix4f;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class Mig25Client implements ClientModInitializer {
	public static final String ID = "mig25";
	private static final float SCALE = 0.18f; // model is ~23.5 units long -> ~4.2 blocks
	private static final int FULL_BRIGHT = 0xF000F0;

	private static final Identifier TEX_BODY = Identifier.fromNamespaceAndPath(ID, "textures/entity/mig25_color.png");
	private static final Identifier TEX_NOZZLE = Identifier.fromNamespaceAndPath(ID, "textures/entity/mig25_nozzle.png");
	private static final Identifier TEX_GLASS = Identifier.fromNamespaceAndPath(ID, "textures/entity/mig25_glass.png");

	/** Immutable snapshot taken during extraction, used when submitting geometry. */
	private record Frame(double x, double y, double z, float yaw, float pitch, float roll) {}

	private static volatile Frame frame = null;

	@Override
	public void onInitializeClient() {
		JetModel.load();
		ClientTickEvents.END_CLIENT_TICK.register(JetState::tick);

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
			float roll = Mth.lerp(pt, JetState.prevRoll, JetState.roll);
			frame = new Frame(x, y, z, yaw, pitch, roll);
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
			pose.scale(SCALE, SCALE, SCALE);
			submitGroup(context, pose, 0, RenderTypes.entityTranslucent(TEX_BODY), 0xFFFFFFFF);
			submitGroup(context, pose, 1, RenderTypes.entityTranslucent(TEX_NOZZLE), 0xFFFFFFFF);
			submitGroup(context, pose, 2, RenderTypes.entityTranslucent(TEX_GLASS), 0x59B8D0E0);
			pose.popPose();
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ID, "jet_hud"), JetHud::render);
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
