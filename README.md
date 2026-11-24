# MiG-25 Elytra (Fabric, 26.3, client-side only)
Wear an elytra -> you become a jet, third person, fighter HUD. Everything is client side.

## Keys (rebind in Controls)
- **J** - settings menu (tabs: Jet, Model, Sound, HUD, Camera, Flight)
- **Left Alt (hold)** - free look: orbit the camera around the jet without steering it

## Your own models
Model tab > **Import model...** (opens a normal file window) or copy files into `config/mig25/models`.
Reads: **OBJ, STL, GLB, glTF, DAE, 3MF, FBX (binary), USDZ (text .usda inside)**.
Colours come from the model's materials (textures are averaged into one colour). Up to 200,000 triangles.
STEP / IGES / BLEND cannot be read inside the game: export them as GLB/OBJ/STL first
(Blender: File > Export > glTF 2.0; FreeCAD/Fusion: export STL or OBJ).

## Editor (Model tab > Open editor)
- **Shape**: size in blocks, rotate X/Y/Z, move, Flip nose. The jet keeps rendering live behind the panel
  (Options > Video Settings > Menu Background Blur = 0 gives a sharp preview). `<` `>` `Up` `Dn` spin the preview.
- **Flames**: Add / Mirror / Remove afterburners, set position (percent of the jet's length), length, width, colour.
Every model has its own profile in `config/mig25/profiles`.

## Sounds (Sound tab)
Separate import buttons for **engine** and **flight** sounds (.wav or .ogg). Stored in `config/mig25/sounds`.

## Build
JDK 25, Gradle 9.8+. `gradle build` -> `build/libs`. Needs Fabric API for 26.3.
Model credit: see CREDITS.md (MiG-25 by abhinab.das000, CC-BY-4.0).
