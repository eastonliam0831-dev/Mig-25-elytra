# AeroCraft Elytra (Fabric, Minecraft 26.3, client-side only)
Wear an elytra and fly a 3D aircraft: third person, fighter HUD, animated landing gear, afterburners,
vapor trails, a Mach cone and camera shake. Nothing is sent to the server.

## Keys (rebind in Controls, category "AeroCraft Elytra")
- **J** - Control Panel (tabs: Aircraft, Hangar, Sound, HUD, Camera, Flight)
- **G** - landing gear lever (Retractable mode)
- **Left Alt (hold)** - free look

## Hangar
`config/aerocraft/models` holds your aircraft (the old `config/mig25` folder is copied there automatically).
Reads GLB, glTF, OBJ, STL, DAE, 3MF, binary FBX, USDZ (text). STEP/IGES/BLEND: export to GLB/STL first.
The Hangar tab lists every aircraft; click one to fly it right away. The hologram viewport lets you test the gear.

## Per-aircraft profiles
`config/aerocraft/profiles/<aircraft>.json` stores size, rotation, offset, afterburners, gear regions, paint
and the aircraft's own engine / flight sounds. Switching aircraft swaps all of it.

## Editor (Hangar > Open editor)
The panel is docked on the right so the aircraft stays big and clear on the left. Drag with the left mouse
button on empty space to orbit the aircraft. Pages:
- **Shape**: pitch / yaw / roll sliders with -90 / -15 / +15 / +90 quick steps, Flip 180, Reset Airframe,
  lateral / height / fore-aft / scale sliders, and Snap Gear to Y=0.
- **Flames**: add, mirror and remove afterburners; position, length, width and colour.
- **Gear**: each gear region is its own editable part (pick by mesh part, region box or whole group), with its own
  deployed position, hinge, retract rotation and retract movement. The selected region glows amber on the aircraft.
- **Paint**: colour wheel, hue / saturation / brightness sliders, presets, eyedropper, brush / whole-part modes,
  undo, clear and fill. Paint is saved per aircraft.
"Studio backdrop" puts a dark room with a floor grid around the aircraft while you edit.

## Build
JDK 25, Gradle 9.8+. `gradle build` -> `build/libs/aerocraft-elytra-*.jar`. Needs Fabric API for 26.3.
The built-in default fighter model is credited in CREDITS.md (CC-BY-4.0).
