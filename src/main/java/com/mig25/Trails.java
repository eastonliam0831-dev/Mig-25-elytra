package com.mig25;

import java.util.ArrayList;
import java.util.List;

/** History of wingtip positions (one sample per client tick) used to draw vapor trails. Newest first. */
public final class Trails {
	public static final int MAX_AGE = 50;

	public static final class Sample {
		public final double[] tips;
		public final float intensity;
		public final long tickId;
		public int age;

		Sample(double[] tips, float intensity, long tickId) {
			this.tips = tips;
			this.intensity = intensity;
			this.tickId = tickId;
		}
	}

	private static final List<Sample> samples = new ArrayList<>();
	private static long counter = 0;

	private Trails() {}

	public static synchronized void tick(boolean push, float intensity, double[] tips) {
		counter++;
		for (Sample s : samples) s.age++;
		samples.removeIf(s -> s.age > MAX_AGE);
		if (push && tips != null && intensity > 0.02f) samples.add(0, new Sample(tips.clone(), intensity, counter));
	}

	public static synchronized List<Sample> snapshot() {
		return new ArrayList<>(samples);
	}

	public static synchronized long currentTick() {
		return counter;
	}
}
