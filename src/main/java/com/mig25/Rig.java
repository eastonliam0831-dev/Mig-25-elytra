package com.mig25;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A model split into a static base and moving landing-gear pieces, with painted triangles separated out so they
 * can be drawn with their own colour. Rebuilt when a gear selection or the paint changes.
 */
public final class Rig {
	public static final int KEY_SPAN = 1_000_000;

	public static final class Piece {
		public final JetModel.Group g;
		public final int src;        // index of the model group it came from
		public final RigPart part;   // null for the static base
		public final boolean painted; // true: drawn with a white texture and per-vertex paint colours
		public final int[] origTri;  // original triangle index for each triangle of g (null = same order)

		Piece(JetModel.Group g, int src, RigPart part, boolean painted, int[] origTri) {
			this.g = g;
			this.src = src;
			this.part = part;
			this.painted = painted;
			this.origTri = origTri;
		}

		public int originalTri(int t) {
			return origTri == null ? t : origTri[t];
		}
	}

	public final List<Piece> base = new ArrayList<>();
	public final List<Piece> moving = new ArrayList<>();

	public static Rig build(JetModel.Group[] groups, List<String> partNames, double longest, List<RigPart> parts,
			Map<Integer, Integer> paint) {
		Rig rig = new Rig();
		int np = parts.size();
		int[] partId = new int[np];
		for (int i = 0; i < np; i++) partId[i] = partNames.indexOf(parts.get(i).part);
		double inv = 100.0 / Math.max(1e-9, longest);
		boolean hasPaint = paint != null && !paint.isEmpty();
		for (int gi = 0; gi < groups.length; gi++) {
			JetModel.Group g = groups[gi];
			int tris = g.idx.length / 3;
			// bucket 0 = base, 1..np = claimed by rig part i-1; each split into [unpainted, painted]
			List<List<Integer>> plain = new ArrayList<>();
			List<List<Integer>> painted = new ArrayList<>();
			for (int i = 0; i <= np; i++) {
				plain.add(new ArrayList<>());
				painted.add(new ArrayList<>());
			}
			boolean anyClaimed = false;
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
				int bucket = owner + 1;
				if (owner >= 0) anyClaimed = true;
				boolean isPainted = hasPaint && paint.containsKey(gi * KEY_SPAN + t);
				(isPainted ? painted : plain).get(bucket).add(t);
			}
			for (int bkt = 0; bkt <= np; bkt++) {
				RigPart rp = bkt == 0 ? null : parts.get(bkt - 1);
				List<Rig.Piece> target = bkt == 0 ? rig.base : rig.moving;
				List<Integer> pl = plain.get(bkt), pt = painted.get(bkt);
				if (bkt == 0 && !anyClaimed && pt.isEmpty() && pl.size() == tris) {
					target.add(new Piece(g, gi, null, false, null)); // untouched group, no copy needed
					continue;
				}
				if (!pl.isEmpty()) target.add(new Piece(subset(g, pl, null, gi), gi, rp, false, toArray(pl)));
				if (!pt.isEmpty()) target.add(new Piece(subset(g, pt, paint, gi), gi, rp, true, toArray(pt)));
			}
		}
		return rig;
	}

	private static int[] toArray(List<Integer> l) {
		int[] a = new int[l.size()];
		for (int i = 0; i < a.length; i++) a[i] = l.get(i);
		return a;
	}

	private static JetModel.Group subset(JetModel.Group g, List<Integer> tris, Map<Integer, Integer> paint, int gi) {
		int n = tris.size();
		JetModel.Group s = new JetModel.Group();
		s.v = new float[n * 3 * 8];
		s.idx = new int[n * 3];
		if (paint != null || g.col != null) s.col = new int[n * 3];
		if (g.triPart != null) s.triPart = new int[n];
		for (int i = 0; i < n; i++) {
			int t = tris.get(i);
			int paintColor = 0;
			if (paint != null) {
				Integer pc = paint.get(gi * KEY_SPAN + t);
				paintColor = 0xFF000000 | (pc == null ? 0xB8BCC4 : pc);
			}
			for (int k = 0; k < 3; k++) {
				int src = g.idx[t * 3 + k];
				System.arraycopy(g.v, src * 8, s.v, (i * 3 + k) * 8, 8);
				s.idx[i * 3 + k] = i * 3 + k;
				if (s.col != null) s.col[i * 3 + k] = paint != null ? paintColor : g.col[src];
			}
			if (g.triPart != null) s.triPart[i] = g.triPart[t];
		}
		return s;
	}
}
