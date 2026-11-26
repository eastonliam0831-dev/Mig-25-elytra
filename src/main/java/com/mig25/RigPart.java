package com.mig25;

/**
 * A separately editable landing-gear region of a model: chosen by mesh part, cutting box or whole group,
 * with its own deployed offset, hinge, retract rotation and retract movement.
 * Positions are percent of the model's longest dimension, in the model's own centred axes.
 */
public final class RigPart {
	public enum Mode { PART, BOX, GROUP }

	public String name = "Gear";
	public Mode mode = Mode.PART;
	public String part = "";
	public int group = 0;
	public double[] box = {-100, -100, -100, 100, 100, 100}; // min x,y,z then max x,y,z (percent)
	public double ox, oy, oz;      // where the region sits when deployed (moves it relative to the airframe)
	public double px, py, pz;      // hinge pivot (percent)
	public double ax = 1, ay = 0, az = 0; // hinge axis
	public double maxDeg = 0;      // rotation when fully retracted
	public double gain = 1;        // -1 reverses the rotation direction
	public double tx, ty, tz;      // translation when fully retracted (percent)
	public boolean enabled = true;

	public RigPart copy() {
		RigPart r = new RigPart();
		r.name = name; r.mode = mode; r.part = part; r.group = group;
		r.box = box.clone();
		r.ox = ox; r.oy = oy; r.oz = oz;
		r.px = px; r.py = py; r.pz = pz; r.ax = ax; r.ay = ay; r.az = az;
		r.maxDeg = maxDeg; r.gain = gain; r.tx = tx; r.ty = ty; r.tz = tz; r.enabled = enabled;
		return r;
	}
}
