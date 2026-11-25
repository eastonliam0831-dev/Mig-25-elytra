package com.mig25;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.mig25.loader.MeshData;
import com.mig25.loader.ModelLoader;
import com.mig25.loader.UnsupportedFormatException;

import net.fabricmc.loader.api.FabricLoader;

/** Plane library: config/aerocraft/models holds the aircraft files, config/aerocraft/profiles their saved setups. */
public final class ModelManager {
	public static final String BUILTIN = "builtin";

	public static volatile LoadedModel current;
	public static volatile ModelProfile profile = ModelProfile.defaultFor(true);
	public static volatile Rig rig;
	public static volatile String currentName = BUILTIN;
	public static volatile String status = "";
	public static volatile boolean loading = false;

	private ModelManager() {}

	public static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve("aerocraft");
	}

	public static Path modelsDir() {
		return root().resolve("models");
	}

	public static Path soundsDir() {
		return root().resolve("sounds");
	}

	public static Path profilesDir() {
		return root().resolve("profiles");
	}

	public static LoadedModel model() {
		LoadedModel m = current;
		return m != null ? m : LoadedModel.builtin();
	}

	/** Name shown in menus and on the HUD: no folder, no extension. */
	public static String displayName(String name) {
		if (name == null || name.equals(BUILTIN)) return "Default Fighter";
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/** Rebuilds the moving-parts split after a model, profile or selection change. */
	public static void rebuildRig() {
		try {
			LoadedModel m = model();
			rig = Rig.build(m.groups, m.partNames, m.longest, profile.rig);
		} catch (Throwable t) {
			System.err.println("[mig25] could not build rig: " + t);
		}
	}

	/** One-time, non-destructive copy of config/mig25 (the old folder name) into config/aerocraft. */
	public static void migrateOldFolder() {
		try {
			Path cfg = FabricLoader.getInstance().getConfigDir();
			Path old = cfg.resolve("mig25");
			Files.createDirectories(root());
			if (Files.isDirectory(old)) {
				for (String sub : new String[] {"models", "sounds", "profiles"}) copyMissing(old.resolve(sub), root().resolve(sub));
			}
			Path oldProps = cfg.resolve("mig25.properties");
			Path newProps = JetConfig.file();
			if (Files.exists(oldProps) && !Files.exists(newProps)) Files.copy(oldProps, newProps);
		} catch (Throwable t) {
			System.err.println("[mig25] migration failed: " + t);
		}
	}

	private static void copyMissing(Path from, Path to) throws IOException {
		if (!Files.isDirectory(from)) return;
		Files.createDirectories(to);
		try (Stream<Path> s = Files.list(from)) {
			for (Path f : (Iterable<Path>) s::iterator) {
				if (!Files.isRegularFile(f)) continue;
				Path dst = to.resolve(f.getFileName().toString());
				if (!Files.exists(dst)) Files.copy(f, dst);
			}
		}
	}

	public static void init() {
		try {
			Files.createDirectories(modelsDir());
			Files.createDirectories(soundsDir());
			Files.createDirectories(profilesDir());
		} catch (IOException e) {
			System.err.println("[mig25] could not create config folders: " + e);
		}
		select(JetConfig.activeModel, null);
	}

	/** BUILTIN first, then every readable file in the models folder. */
	public static List<String> list() {
		List<String> out = new ArrayList<>();
		out.add(BUILTIN);
		try (Stream<Path> s = Files.list(modelsDir())) {
			s.filter(p -> Files.isRegularFile(p) && ModelLoader.canRead(p))
					.map(p -> p.getFileName().toString())
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.forEach(out::add);
		} catch (IOException ignored) { }
		return out;
	}

	/** Older versions kept one global engine/flight sound; give it to the first plane that loads. */
	private static void adoptLegacySounds() {
		boolean changed = false;
		if (!JetConfig.engineSound.isEmpty() && profile.engineSound.isEmpty()) {
			profile.engineSound = JetConfig.engineSound;
			JetConfig.engineSound = "";
			changed = true;
		}
		if (!JetConfig.flightSound.isEmpty() && profile.flightSound.isEmpty()) {
			profile.flightSound = JetConfig.flightSound;
			JetConfig.flightSound = "";
			changed = true;
		}
		if (changed) profile.save(profilesDir(), currentName);
	}

	public static void saveProfile() {
		profile.save(profilesDir(), currentName);
	}

	/** Selects a model by name. Imported models load on a background thread; onDone runs on the game thread. */
	public static void select(String name, Runnable onDone) {
		if (name == null || name.equals(BUILTIN)) {
			current = LoadedModel.builtin();
			currentName = BUILTIN;
			profile = ModelProfile.load(profilesDir(), BUILTIN, true);
			adoptLegacySounds();
			rebuildRig();
			JetConfig.activeModel = BUILTIN;
			JetConfig.save();
			if (onDone != null) onDone.run();
			return;
		}
		loading = true;
		status = "Loading " + name + " ...";
		Thread t = new Thread(() -> {
			try {
				Path p = modelsDir().resolve(name);
				MeshData mesh = ModelLoader.load(p);
				LoadedModel lm = ModelBake.bake(name, mesh);
				ModelProfile pr = ModelProfile.load(profilesDir(), name, false);
				current = lm;
				currentName = name;
				profile = pr;
				adoptLegacySounds();
				rebuildRig();
				JetConfig.activeModel = name;
				JetConfig.save();
				status = "Loaded " + displayName(name) + " (" + mesh.triangleCount() + " triangles)";
			} catch (UnsupportedFormatException e) {
				status = e.getMessage();
			} catch (Throwable e) {
				status = "Could not load " + name + ": " + e;
			} finally {
				loading = false;
				if (onDone != null) net.minecraft.client.Minecraft.getInstance().execute(onDone);
			}
		}, "mig25-model-loader");
		t.setDaemon(true);
		t.start();
	}

	/** Copies a file (and same-name side files such as .mtl/.bin) into the library and selects it. */
	public static void importFile(Path src, Runnable onDone) {
		Thread t = new Thread(() -> {
			try {
				String ext = ModelLoader.extension(src);
				if (!ModelLoader.canRead(src)) {
					// run the loader just to get the helpful explanation
					try {
						ModelLoader.load(src);
					} catch (UnsupportedFormatException e) {
						status = e.getMessage();
					}
					return;
				}
				Files.createDirectories(modelsDir());
				String fileName = src.getFileName().toString();
				Path dst = modelsDir().resolve(fileName);
				if (!src.toAbsolutePath().normalize().equals(dst.toAbsolutePath().normalize())) {
					Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
					String stem = fileName.substring(0, fileName.length() - ext.length() - 1);
					Path dir = src.toAbsolutePath().getParent();
					if (dir != null) {
						try (Stream<Path> s = Files.list(dir)) {
							for (Path sib : (Iterable<Path>) s::iterator) {
								String n = sib.getFileName().toString();
								String low = n.toLowerCase(Locale.ROOT);
								boolean side = (low.endsWith(".mtl") || low.endsWith(".bin") || low.endsWith(".png")
										|| low.endsWith(".jpg") || low.endsWith(".jpeg")) && n.startsWith(stem);
								if (side && !n.equals(fileName)) {
									Files.copy(sib, modelsDir().resolve(n), StandardCopyOption.REPLACE_EXISTING);
								}
							}
						}
					}
				}
				select(fileName, onDone);
			} catch (Throwable e) {
				status = "Import failed: " + e;
			}
		}, "mig25-model-import");
		t.setDaemon(true);
		t.start();
	}

	public static void removeCurrent() {
		String name = currentName;
		if (name.equals(BUILTIN)) return;
		try {
			Files.deleteIfExists(modelsDir().resolve(name));
		} catch (IOException ignored) { }
		ModelProfile.delete(profilesDir(), name);
		select(BUILTIN, null);
		status = "Removed " + displayName(name);
	}
}
