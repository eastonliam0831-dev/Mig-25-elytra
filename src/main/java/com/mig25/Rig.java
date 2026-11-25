package com.mig25;

import java.util.ArrayList;
import java.util.List;

/** A model split into a static base and moving pieces according to its RigParts. Rebuilt when a selection changes. */
public final class Rig {
	public static final class Piece {
		public final JetModel.Group g;
		public final int src;       // index of the model group it came from (texture choice for the built-in model)
		public final RigPart part;  // null for the static base

		Piece(JetModel.Group g, int src, RigPart part) {
			this.g = g;
			this.src = src;
			this.part = part;
		}
	}

	public final List<Piece> base = new ArrayList<>();
	public final List<Piece> moving = new ArrayList<>();

	public static Rig build(JetModel.Group[] groups, List<String> partNames, double longest, List<RigPart> parts) {
		Rig rig = new Rig();
		int np = parts.size();
		int[] partId = new int[np];
		for (int i = 0; i < np; i++) partId[i] = partNames.indexOf(parts.get(i).part);
		double inv = 100.0 / Math.max(1e-9, longest);
		for (int gi = 0; gi < groups.length; gi++) {
			JetModel.Group g = groups[gi];
			int tris = g.idx.length / 3;
			List<List<Integer>> claimed = new ArrayList<>();
			for (int i = 0; i < np; i++) claimed.add(new ArrayList<>());
			List<Integer> rest = new ArrayList<>();
			for (int t = 0; t < tris; t++) {
				int owner = -1;
				double cx = 0, cy = 0, cz = 0;
				boolean haveC = false;
				for (int i = 0; i < np && owner < 0; i++) {
					RigPart rp = parts.get(i);
					if (!rp.enabled) continue;
					boolean hit = false;
					switch (rp.mode) {
						case GROUP -> hit = rp.group == gi;
						case PART -> hit = g.triPart != null && partId[i] >= 0 && g.triPart[t] == partId[i];
						case BOX -> {
							if (rp.group == gi) {
								if (!haveC) {
									for (int k = 0; k < 3; k++) {
										int o = g.idx[t * 3 + k] * 8;
										cx += g.v[o]; cy += g.v[o + 1]; cz += g.v[o + 2];
									}
									cx = cx / 3 * inv; cy = cy / 3 * inv; cz = cz / 3 * inv;
									haveC = true;
								}
								double[] b = rp.box;
								hit = cx >= b[0] && cx <= b[3] && cy >= b[1] && cy <= b[4] && cz >= b[2] && cz <= b[5];
							}
						}
					}
					if (hit) owner = i;
				}
				if (owner < 0) rest.add(t);
				else claimed.get(owner).add(t);
			}
			boolean any = rest.size() != tris;
			if (!any) {
				rig.base.add(new Piece(g, gi, null));
			} else if (!rest.isEmpty()) {
				rig.base.add(new Piece(subset(g, rest), gi, null));
			}
			for (int i = 0; i < np; i++) {
				if (!claimed.get(i).isEmpty()) rig.moving.add(new Piece(subset(g, claimed.get(i)), gi, parts.get(i)));
			}
		}
		return rig;
	}

	private static JetModel.Group subset(JetModel.Group g, List<Integer> tris) {
		int n = tris.size();
		JetModel.Group s = new JetModel.Group();
		s.v = new float[n * 3 * 8];
		s.idx = new int[n * 3];
		if (g.col != null) s.col = new int[n * 3];
		if (g.triPart != null) s.triPart = new int[n];
		for (int i = 0; i < n; i++) {
			int t = tris.get(i);
			for (int k = 0; k < 3; k++) {
				int src = g.idx[t * 3 + k];
				System.arraycopy(g.v, src * 8, s.v, (i * 3 + k) * 8, 8);
				s.idx[i * 3 + k] = i * 3 + k;
				if (g.col != null) s.col[i * 3 + k] = g.col[src];
			}
			if (g.triPart != null) s.triPart[i] = g.triPart[t];
		}
		return s;
	}
}
