package com.mig25;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

/**
 * Plays the imported engine / flight sounds straight through OpenAL (WAV and OGG). Never throws:
 * if anything goes wrong the sound is switched off and a [mig25] line is logged.
 */
public final class JetAudio {
	private static final class Clip {
		String loadedName = null;
		String failedName = null;
		int buffer = 0;
		int source = 0;
	}

	private static final Clip ENGINE = new Clip();
	private static final Clip FLIGHT = new Clip();
	private static boolean broken = false;
	private static Method masterVolume;
	private static Object masterEnum;
	private static boolean masterSearched;

	private JetAudio() {}

	/** Forces both clips to be re-read from disk (call after importing a new file). */
	public static void reload() {
		ENGINE.failedName = null;
		FLIGHT.failedName = null;
		release(ENGINE);
		release(FLIGHT);
	}

	public static void tick(Minecraft mc) {
		if (broken) return;
		try {
			LocalPlayer p = mc.player;
			boolean active = JetState.active && p != null;
			boolean gliding = active && p.isFallFlying();
			double speed = p == null ? 0 : p.getDeltaMovement().length() * 20.0;
			float thr = (float) Math.max(0.0, Math.min(1.0, speed / 45.0));
			float master = master(mc);
			float eGain = (float) JetConfig.VOLS[JetConfig.engineVol] * master * (gliding ? 0.45f + 0.55f * thr : 0.25f);
			float ePitch = gliding ? 0.8f + 0.5f * thr : 0.75f;
			float fGain = (float) JetConfig.VOLS[JetConfig.flightVol] * master * (0.2f + 0.8f * thr);
			float fPitch = 0.85f + 0.4f * thr;
			update(ENGINE, ModelManager.profile.engineSound, JetConfig.engineOn && active, eGain, ePitch);
			update(FLIGHT, ModelManager.profile.flightSound, JetConfig.flightOn && gliding, fGain, fPitch);
		} catch (Throwable t) {
			broken = true;
			System.err.println("[mig25] audio disabled: " + t);
		}
	}

	private static float master(Minecraft mc) {
		try {
			if (!masterSearched) {
				masterSearched = true;
				Class<?> src = Class.forName("net.minecraft.sounds.SoundSource");
				for (Object o : src.getEnumConstants()) {
					if (((Enum<?>) o).name().equals("MASTER")) masterEnum = o;
				}
				for (Method m : mc.options.getClass().getMethods()) {
					if (m.getName().equals("getSoundSourceVolume") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == src) {
						masterVolume = m;
					}
				}
			}
			if (masterVolume != null && masterEnum != null) return ((Number) masterVolume.invoke(mc.options, masterEnum)).floatValue();
		} catch (Throwable t) {
			masterVolume = null;
		}
		return 1.0f;
	}

	private static void update(Clip c, String file, boolean want, float gain, float pitch) {
		if (file == null || file.isEmpty()) {
			if (c.loadedName != null) release(c);
			return;
		}
		if (!file.equals(c.loadedName)) {
			if (file.equals(c.failedName)) return;
			release(c);
			if (!want) return; // load lazily, only when it is actually needed
			try {
				load(c, ModelManager.soundsDir().resolve(file));
				c.loadedName = file;
			} catch (Throwable t) {
				c.failedName = file;
				ModelManager.status = "Sound " + file + " failed: " + t.getMessage();
				System.err.println("[mig25] could not load sound " + file + ": " + t);
				return;
			}
		}
		if (c.source == 0) return;
		if (want) {
			AL10.alSourcef(c.source, AL10.AL_GAIN, Math.max(0f, Math.min(4f, gain)));
			AL10.alSourcef(c.source, AL10.AL_PITCH, Math.max(0.5f, Math.min(2f, pitch)));
			if (AL10.alGetSourcei(c.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(c.source);
		} else if (AL10.alGetSourcei(c.source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
			AL10.alSourceStop(c.source);
		}
	}

	private static void release(Clip c) {
		try {
			if (c.source != 0) {
				AL10.alSourceStop(c.source);
				AL10.alDeleteSources(c.source);
			}
			if (c.buffer != 0) AL10.alDeleteBuffers(c.buffer);
		} catch (Throwable ignored) { }
		c.source = 0;
		c.buffer = 0;
		c.loadedName = null;
	}

	private static void load(Clip c, Path path) throws Exception {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		byte[] bytes = Files.readAllBytes(path);
		int buffer = AL10.alGenBuffers();
		if (name.endsWith(".ogg")) {
			ByteBuffer mem = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
			mem.put(bytes).flip();
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer ch = stack.mallocInt(1);
				IntBuffer rate = stack.mallocInt(1);
				ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(mem, ch, rate);
				if (pcm == null) throw new IllegalStateException("could not decode OGG");
				int format = ch.get(0) == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
				AL10.alBufferData(buffer, format, pcm, rate.get(0));
				try {
					Class.forName("org.lwjgl.system.libc.LibCStdlib").getMethod("free", java.nio.Buffer.class).invoke(null, pcm);
				} catch (Throwable ignored) { }
			}
		} else if (name.endsWith(".wav")) {
			AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
			AudioFormat f = in.getFormat();
			AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16, f.getChannels(),
					f.getChannels() * 2, f.getSampleRate(), false);
			AudioInputStream pcmIn = AudioSystem.getAudioInputStream(target, in);
			byte[] pcm = pcmIn.readAllBytes();
			ByteBuffer data = ByteBuffer.allocateDirect(pcm.length).order(ByteOrder.nativeOrder());
			data.put(pcm).flip();
			int format = f.getChannels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
			AL10.alBufferData(buffer, format, data, (int) f.getSampleRate());
		} else {
			AL10.alDeleteBuffers(buffer);
			throw new IllegalStateException("only .wav and .ogg are supported (convert MP3 first)");
		}
		int source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
		AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
		AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
		c.buffer = buffer;
		c.source = source;
	}
}
