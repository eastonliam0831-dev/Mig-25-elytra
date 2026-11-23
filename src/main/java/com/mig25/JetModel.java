package com.mig25;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Loads the baked MiG-25 mesh (generated from model-source/mig-25.glb by tools/bake_glb.py). */
public final class JetModel {
	/** Material 0 = body, 1 = nozzle, 2 = canopy glass. */
	public static final class Group {
		public float[] v; // 8 floats per vertex: pos(3) normal(3) uv(2)
		public int[] idx; // triangles
	}

	public static Group[] groups = new Group[0];

	private JetModel() {}

	public static void load() {
		try (InputStream in = JetModel.class.getResourceAsStream("/assets/mig25/model/mig25.bin")) {
			if (in == null) throw new IllegalStateException("mig25.bin missing");
			ByteBuffer b = ByteBuffer.wrap(new DataInputStream(in).readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
			int n = b.getInt();
			Group[] g = new Group[n];
			for (int i = 0; i < n; i++) {
				int vc = b.getInt(), ic = b.getInt();
				Group gr = new Group();
				gr.v = new float[vc * 8];
				b.asFloatBuffer().get(gr.v);
				b.position(b.position() + gr.v.length * 4);
				gr.idx = new int[ic];
				b.asIntBuffer().get(gr.idx);
				b.position(b.position() + ic * 4);
				g[i] = gr;
			}
			groups = g;
		} catch (Throwable t) {
			System.err.println("[mig25] failed to load model: " + t);
			groups = new Group[0];
		}
	}
}
