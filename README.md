# MiG-25 Elytra (Fabric, 26.3, client-side only)
Equip an elytra -> third person + you become the MiG-25 + fighter-jet HUD. Unequip -> everything goes back.
Works on any server (nothing is sent to or required from the server).

## Build
Needs JDK 25. `gradle wrapper --gradle-version 9.6.0` once (or copy gradlew/gradle/wrapper from fabric-example-mod),
then `./gradlew build`. Jar: `build/libs/`. Needs Fabric API 0.166.2+26.3 (or newer 26.3) in your mods folder.
GitHub: push and the included Actions workflow builds the jar for you (no wrapper needed).

## Option
`JetState.ONLY_WHILE_GLIDING = true` -> jet only while actually flying instead of whenever the elytra is worn.
