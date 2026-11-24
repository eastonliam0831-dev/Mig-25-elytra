package com.mig25.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Entry point: picks a loader by file extension. */
public final class ModelLoader {
	private ModelLoader() {}

	/** Extensions that can be read directly. */
	public static final List<String> SUPPORTED = List.of("obj", "stl", "glb", "gltf", "dae", "3mf", "fbx", "usdz");
	/** Extensions we recognise but cannot read (CAD / native Blender). */
	public static final List<String> CONVERT_FIRST = List.of("step", "stp", "iges", "igs", "blend");

	public static String extension(Path p) {
		String n = p.getFileName().toString();
		int dot = n.lastIndexOf('.');
		return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	public static boolean canRead(Path p) {
		return SUPPORTED.contains(extension(p));
	}

	public static MeshData load(Path p) throws IOException, UnsupportedFormatException {
		String ext = extension(p);
		switch (ext) {
			case "stl": return StlLoader.load(Files.readAllBytes(p));
			case "obj": return ObjLoader.load(p);
			case "glb": case "gltf": return GltfLoader.load(p);
			case "dae": return DaeLoader.load(p);
			case "3mf": return ThreeMfLoader.load(p);
			case "fbx": return FbxLoader.load(p);
			case "usdz": return UsdzLoader.load(p);
			case "step": case "stp": case "iges": case "igs":
				throw new UnsupportedFormatException(ext.toUpperCase(Locale.ROOT)
						+ " is a CAD (B-rep) format and cannot be turned into a mesh inside the game. "
						+ "Open it in FreeCAD/Fusion and export as STL, OBJ or GLB, then import that.");
			case "blend": case "blend1":
				throw new UnsupportedFormatException("BLEND is Blender's internal format. In Blender use File > Export > glTF 2.0 (.glb), then import that.");
			default:
				throw new UnsupportedFormatException("Unknown file type ." + ext + ". Supported: OBJ STL GLB GLTF DAE 3MF FBX USDZ.");
		}
	}
}
