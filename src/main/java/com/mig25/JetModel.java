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
		public int[] triPart; // optional: part id per triangle (index into the model's part names)
		public int[] col; // optional ARGB per vertex (imported models); null = use the colour passed at draw time
	}

	public static Group[] groups = new Group[0];
	public static java.util.List<String> partNames = new java.util.ArrayList<>();

	private JetModel() {}

	public static void load() {
		try (InputStream in = JetModel.class.getResourceAsStream("/assets/mig25/model/mig25.bin")) {
			if (in == null) throw new IllegalStateException("mig25.bin missing");
			ByteBuffer b = ByteBuffer.wrap(new DataInputStream(in).readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
			int n = b.getInt();
			boolean v2 = n < 0;
			if (v2) n = -n;
			Group[] g = new Group[n];
			for (int i = 0; i < n; i++) {
				int vc = b.getInt(), tc = b.getInt(); // tc = triangle count
				Group gr = new Group();
				gr.v = new float[vc * 8];
				b.asFloatBuffer().get(gr.v);
				b.position(b.position() + gr.v.length * 4);
				gr.idx = new int[tc * 3];
				b.asIntBuffer().get(gr.idx);
				b.position(b.position() + tc * 12);
				if (v2) {
					gr.triPart = new int[tc];
					b.asIntBuffer().get(gr.triPart);
					b.position(b.position() + tc * 4);
				}
				g[i] = gr;
			}
			java.util.List<String> names = new java.util.ArrayList<>();
			if (v2) {
				int pc = b.getInt();
				for (int i = 0; i < pc; i++) {
					int len = b.getShort() & 0xFFFF;
					byte[] sb = new byte[len];
					b.get(sb);
					names.add(new String(sb, java.nio.charset.StandardCharsets.UTF_8));
				}
			}
			partNames = names;
			groups = g;
			System.out.println("[mig25] model loaded: " + n + " groups");
		} catch (Throwable t) {
			System.err.println("[mig25] failed to load model: " + t);
			groups = new Group[0];
		}
	}
}
