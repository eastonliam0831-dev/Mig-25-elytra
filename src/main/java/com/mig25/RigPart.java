package com.mig25;

/**
 * One animated piece of a model: a control surface (aileron / elevator / rudder / airbrake) or landing gear.
 * Positions are percent of the model's longest dimension, in the model's own centred axes.
 */
public final class RigPart {
	public enum Kind { AILERON, ELEVATOR, RUDDER, AIRBRAKE, GEAR }

	public enum Mode { PART, BOX, GROUP }

	public String name = "Surface";
	public Kind kind = Kind.AILERON;
	public Mode mode = Mode.PART;
	public String part = "";
	public int group = 0;
	public double[] box = {-100, -100, -100, 100, 100, 100}; // min x,y,z then max x,y,z (percent)
	public double px, py, pz;      // hinge pivot (percent)
	public double ax = 1, ay = 0, az = 0; // hinge axis
	public double maxDeg = 25;     // control: max deflection. gear: rotation when fully retracted
	public double gain = 1;        // multiplies the input (use -1 to flip direction)
	public double tx, ty, tz;      // gear: translation when fully retracted (percent)
	public boolean enabled = true;

	public RigPart copy() {
		RigPart r = new RigPart();
		r.name = name; r.kind = kind; r.mode = mode; r.part = part; r.group = group;
		r.box = box.clone();
		r.px = px; r.py = py; r.pz = pz; r.ax = ax; r.ay = ay; r.az = az;
		r.maxDeg = maxDeg; r.gain = gain; r.tx = tx; r.ty = ty; r.tz = tz; r.enabled = enabled;
		return r;
	}

	public boolean isGear() {
		return kind == Kind.GEAR;
	}

	public static String kindLabel(Kind k) {
		return switch (k) {
			case AILERON -> "Aileron (roll)";
			case ELEVATOR -> "Elevator (pitch)";
			case RUDDER -> "Rudder (yaw)";
			case AIRBRAKE -> "Airbrake / flap";
			case GEAR -> "Landing gear";
		};
	}
}
