# AeroCraft Elytra (Fabric, Minecraft 26.3, client-side only)
Wear an elytra and fly a rigged 3D aircraft: third person, fighter HUD, flaps and landing gear that move,
afterburners, vapor trails, a Mach cone and camera shake. Nothing is sent to the server.

## Keys (rebind in Controls, category "AeroCraft Elytra")
- **J** - Control Panel (tabs: Aircraft, Hangar, Sound, HUD, Camera, Flight)
- **G** - landing gear lever (Retractable mode)
- **Left Alt (hold)** - free look

## Hangar
`config/aerocraft/models` holds your aircraft (the old `config/mig25` folder is copied there automatically).
Reads GLB, glTF, OBJ, STL, DAE, 3MF, binary FBX, USDZ (text). STEP/IGES/BLEND: export to GLB/STL first.
The Hangar tab lists every aircraft; click one to fly it right away. The hologram viewport lets you test the
flaps, rudder, airbrake and gear.

## Per-aircraft profiles
`config/aerocraft/profiles/<aircraft>.json` stores size, rotation, offset, afterburners, control surfaces, gear
and the aircraft's own engine / flight sounds. Switching aircraft swaps all of it.

## Editor (Hangar > Open editor)
Pages: **Shape**, **Flames**, **Flaps** (aileron / elevator / rudder / airbrake), **Gear**.
Pick surfaces by mesh part (named parts of the model), by cutting box, or by whole group; set the hinge point,
hinge axis and max deflection; the test buttons move them live on the aircraft behind the panel.

## Build
JDK 25, Gradle 9.8+. `gradle build` -> `build/libs/aerocraft-elytra-*.jar`. Needs Fabric API for 26.3.
The built-in default fighter model is credited in CREDITS.md (CC-BY-4.0).
