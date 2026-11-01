# VoxyMap LTS — User Guide

Mod version: 1.0.9-LTS.01 · Minecraft 26.3 · Fabric · client-only

[Русская версия](USER_GUIDE_RU.md)

VoxyMap LTS is a maintained fork of [3D Maps Voxy Addon](https://github.com/raben12paste/3D-maps-Voxy-Addon) (original by raben12paste).

## Contents

1. [What the mod does](#what-the-mod-does)
2. [What you need](#what-you-need)
3. [Installation](#installation)
4. [Getting started](#getting-started)
5. [The big map](#the-big-map)
6. [2D map](#2d-map)
7. [Waypoints](#waypoints)
8. [Routes](#routes)
9. [Minimap](#minimap)
10. [Objects panel](#objects-panel)
11. [The Nether and the ore mask](#the-nether-and-the-ore-mask)
12. [Map screenshots](#map-screenshots)
13. [Settings](#settings)
14. [Waypoint sync and map sharing](#waypoint-sync-and-map-sharing)
15. [Compatibility with other mods](#compatibility-with-other-mods)
16. [Commands](#commands)
17. [Files](#files)
18. [FAQ](#faq)
19. [Building from source](#building-from-source)
20. [Feature reference](#feature-reference)

## What the mod does

[Voxy](https://modrinth.com/mod/voxy) keeps a simplified copy of everything you have ever seen and draws it far beyond the normal render distance. VoxyMap turns that data into a map:

- a **full-screen 3D map** of the whole explored world that you can rotate, tilt and zoom into any point;
- a **flat 2D map**, north up, from the same data;
- **waypoints** with colours, symbols and beacon beams in the world;
- **routes** to waypoints and points, with walking, swimming, boat and elytra legs;
- a **minimap** in the corner with a mob and player radar;
- waypoint and map sharing with friends through your own server.

The mod shows only what Voxy already has: it never loads chunks or reveals unexplored terrain. The server does not need it.

## What you need

| What | Version |
|---|---|
| Minecraft | 26.3 |
| Java | 25 |
| [Fabric Loader](https://fabricmc.net/use/installer/) | 0.19.5 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | for 26.3 |
| [Voxy](https://modrinth.com/mod/voxy) | 0.2.20-beta or newer for 26.3 |
| [Sodium](https://modrinth.com/mod/sodium) | required by Voxy |

Important about Voxy:

- **Back up your `.minecraft/.voxy` folder** before moving from an older Voxy.
- Voxy does not run on Minecraft's Vulkan backend: the game must run on OpenGL.
- For shaders Voxy supports Iris 1.11.7. Voxy does not work with Iris 1.12 or newer.

Optional: Iris, [Mod Menu](https://modrinth.com/mod/modmenu), Xaero's World Map, Litematica Plus LTS, Spatial GUI.

## Installation

1. Install Fabric Loader, Fabric API, Sodium and Voxy for 26.3.
2. Put one VoxyMap file into `mods` (see [Building](#building-from-source)):
   - `voxy-map-1.0.9-LTS.01.jar`, the main edition;
   - `voxy-map-1.0.9-LTS.01-player-markers.jar`, the same plus players from the VanillaSquad server's web map. Install only one of the two.
3. Start the game and walk around so Voxy records the terrain.

## Getting started

1. Press `M` to open the map. You are in the centre, and a wedge shows where you look.
2. Drag the map with the left mouse button, rotate with the right one, zoom with the wheel.
3. Double-click the terrain to place a waypoint. Type a name and press `Enter`.
4. Press `H` to see every map key.
5. `Esc` or `M` closes the map.

During normal play the minimap is in the corner. `N` places a waypoint where you stand, `U` opens the waypoint list.

## The big map

### Controls

| Key | Action |
|---|---|
| `M` / `Esc` | open and close the map |
| Left drag | move the map |
| Right drag | rotate and tilt the camera |
| Wheel, `+`, `-` | zoom toward the cursor |
| `W`, `A`, `S`, `D` or arrow keys | move the camera, faster with `Shift` |
| Double left click on terrain | place a waypoint |
| `N` | place a waypoint under the cursor |
| Left click a waypoint | select it; click again or `Enter` to rename |
| `Delete` / `Ctrl+Z` | delete the selected waypoint / undo the deletion |
| Right click | menu: waypoint, temporary waypoint, route here, copy coordinates, teleport |
| `C` | back to the player |
| `O` | objects panel |
| `F` | follow the selected player |
| `V` | switch 3D and 2D |
| `G` | go to typed coordinates |
| `H` | key help |
| `F2` | map screenshot without the interface |

Map keys do nothing while you type in the search, the waypoint form or the settings.

### What the map shows

- Terrain from Voxy data. Nearby it uses LODs Voxy already stored, so the picture does not depend on where the normal camera looks, and the game loads no extra chunks.
- You and loaded players nearby, with view wedges. In a crowd the wedges collapse to short ticks and come back in full on hover.
- Players and mobs on the 3D map are drawn as real models, enlarged with distance (at most 8x) and hidden when the map camera cannot see them.
- A toolbar at the top right, a compass and scale bar at the bottom right, an info card for the selected object at the top left.
- Layers: chunk grid, slime chunks, where mobs can spawn (light 0), biome colours.

### Weather and lighting on the map

By default the map is clear and its lighting follows the game clock. In the settings you can show constant rain, thunder or the real weather, and fix the lighting to morning, day, evening or night. Clouds are hidden on the map, and the shader pack's fog can be removed. The world's own weather never changes.

## 2D map

`V` or the 3D/2D toolbar buttons switch to a flat map, north up. It is built from Voxy sections; tiles are cached as PNGs per world and refreshed after block changes. Waypoints, players and layers work in both views. In dimensions with a ceiling (the Nether) the 2D map shows a slice at the player's height.

## Waypoints

### Placing

- Double-click terrain on the map, or press `N` with the cursor on it, for a waypoint on exactly the visible block, caves and multi-level builds included.
- `N` during play places one where you stand.
- "Temporary waypoint where I look" (unbound by default) places one that disappears once you reach it.
- A "Death point" appears when you die. The five newest are kept per world.

The waypoint form has the name, a 1–2 letter symbol, a colour, the coordinates and where to show it. `Enter` saves, `Esc` cancels.

### Where a waypoint shows

Per waypoint: everywhere, maps only, the big map only or nowhere (it stays in the `U` list). New waypoints follow their own setting.

In the world every waypoint within 512 blocks shows a beacon beam in its colour, and its icon and name are drawn over the world.

### The waypoint list (`U`)

- Every waypoint of the current world: show, edit, delete, route to it.
- Import from Xaero's Minimap and JourneyMap with a button in the list.
- Waypoints are stored separately for each Voxy world.

### Waypoints across dimensions

Overworld waypoints show in the Nether and back, with coordinates converted by 8.

### Sharing a waypoint

A waypoint can be sent to chat in the Xaero format. Recipients with VoxyMap or Xaero get an [Add] link.

## Routes

VoxyMap plans a route to a waypoint or a point from Voxy's data.

- **How to start:** right click on the map → "Route here", the "Route" button of a waypoint in the objects panel, or `/voxymap route <x> <z>` / `/voxymap route waypoint <name>`.
- **Takes into account:** walking, swimming, a boat (if you carry one) and elytra flight (if you have an elytra and rockets). It avoids lava and hazards and does not climb ledges higher than one block.
- **The card at the top of the screen:** the travel mode, distance, travel time and the next step ("In 40 blocks: get into a boat").
- **The route line** shows on the big map and the minimap.
- If you leave the path, the route is re-planned. It disappears on arrival (6 blocks).
- To stop: right click on the map → "Clear route", or `/voxymap route stop`.

The mod only shows the way; it never moves you. Where Voxy has not seen the world yet, the route is a straight line.

## Minimap

The minimap works from already loaded chunks, entirely on the client.

- On the surface it shows the top layer; under a solid roof (caves, houses) and always in the Nether it shows the layer at your height.
- Hold `Z` to make it 1.6 times larger; `=` and `-` change the zoom.
- Square or circle, four sizes, any screen corner or a free position (the "Minimap position" button opens a screen where you drag it).
- Colours from block textures or like the vanilla map item.
- Hides on F1 and, by setting, behind inventories or any screen.
- **Lines under the minimap:** coordinates, Nether / Overworld coordinates, biome, light level, day and time, chunk, facing.
- **Radar:** hostile, neutral and passive mobs, dropped items and players as separate categories. Icons are heads, heads only while `Tab` is held, or dots. Mobs more than 3 blocks above or below get an arrow.
- **Layers:** slime chunks (servers need the seed: `/voxymap seed <seed>`), where mobs can spawn, biome colours.

## Objects panel

`O` on the map opens a searchable panel of players, waypoints, VanillaSquad territories and Litematica Plus schematics.

- Center flies the camera to an object, Edit and Delete work on waypoints.
- `F` follows the selected player. Moving the camera by hand stops following.
- The eye icon hides a player from the map, the minimap and the 3D models.

## The Nether and the ore mask

- **The Nether roof** is removed on the map: only whole polygons above a threshold are dropped, never a slice through a plane, so the server's anti-xray decoys do not turn into a checkerboard. Zoomed in close, a deeper window opens around the map centre.
- **Ore mask.** Vanilla ores and ancient debris in Voxy's LODs are painted plain grey, so neither the distant terrain nor the map can be used to spot ores. It cannot be turned off.

If a newer Voxy changes its shaders, both features switch themselves off and the game keeps running. The Diagnostics settings group shows their state.

## Map screenshots

`F2` on the open map saves a screenshot without panels, HUD and chat. By default waypoints are hidden in the shot too; set Map screenshot to "With markers" to keep them.

## Settings

The settings are the same in the map's Settings panel and in **Mod Menu → VoxyMap LTS → Configure**. Both have a search field, categories, a hint for the setting under the cursor, a reset button on every row and Reset all. Changed settings are shown in italics.

| Group | Settings (defaults) |
|---|---|
| General | pause in singleplayer (on), camera speed (1.0), map lighting (auto), map weather (clear), Voxy LODs outside the map (on), interface animations (on), fog suppression (always), map screenshot (no interface), teleport command on servers (`tppos {x} {y} {z}`); with Spatial GUI: M opens the flat map, 3D view in the Spatial GUI window |
| Map layers | waypoints (on), Overworld waypoints in the Nether and back (on), death points (on), my view direction (on), models on the 3D map (players and mobs); on VanillaSquad: VanillaSquad players, flags and map (off); with Litematica Plus: schematics (on) |
| Waypoint look | show new waypoints (everywhere), icon shape (square), icon size in the world (medium), waypoints in the world (name when aimed at), label background (solid), label size (normal), distance in labels (on), show waypoints up to (unlimited), beacon beams (on) |
| Minimap | minimap (on), rotate (off), colours (textures), shape (square), size (medium), screen corner (top left), zoom, position, opacity (full), darker at night (on), cave view (auto), with a screen open (always), chunk grid (off) |
| Under the minimap | coordinates, Nether / Overworld coordinates, biome, light level, day and time, chunk, facing |
| Minimap radar | hostile, neutral, passive mobs, dropped items, players, icons (heads) |
| Map overlays | slime chunks, where mobs can spawn, biome colours |
| Sync and sharing | sync address, password and status, Share map, Get map |
| Diagnostics | whether the Voxy shader patches and integrations loaded |

A few settings explained:

- **Voxy LODs outside the map.** Turned off, Voxy draws far terrain only while the map is open, not in normal play. That saves FPS.
- **Fog suppression** removes the shader pack's fog while the map is open. It works with Complementary (Reimagined and Unbound), Rethinking Voxels, Solas, BSL, Mellow and Photon; with other packs it does nothing.
- **Teleport command on servers.** "Teleport here" on the map sends this command. An empty field hides teleporting on servers. You need permission for the command.
- The sync address and password and the VanillaSquad map are stored per world, so Mod Menu shows them only when opened from inside a world (Esc → Mods).

## Waypoint sync and map sharing

### The sync server

`voxymap-sync-server` is a small standalone program (no Minecraft, no dependencies) that stores shared waypoints for several players.

```powershell
.\gradlew.bat syncServerJar
java -jar build\libs\voxymap-sync-server-1.0.9-LTS.01.jar --bind 0.0.0.0 --port 8642 --password mysecret
```

| Option | Default | Meaning |
|---|---|---|
| `--bind` | `127.0.0.1` | address; `0.0.0.0` opens the server to the LAN |
| `--port` | `8642` | port |
| `--password` | none | password; without one the server is read-only |
| `--data` | `./voxymap-sync-data` | data folder, one file per Voxy world |

### Connecting

1. Open the map → Settings → Sync and sharing.
2. Set Sync address to `host:port`. Shared waypoints start loading.
3. To add and change shared waypoints, set Sync password to the server's password. The new-waypoint form then gets a "Local only" / "Shared to server" switch.

There is one password for the whole server. Traffic is plain HTTP without encryption: the server is meant for a LAN or a trusted group. To reach it over the internet, put a TLS reverse proxy with rate limits in front of it.

### Map sharing

Only the sync address is needed, no password.

1. **Sender:** Share map, then mark two opposite corners on the 3D map. A 6-digit code appears. Sharing runs while the map is open, until you click the row again or leave the world.
2. **Receiver:** Get map, type the code. An estimated size appears: left click to accept, right click to decline.
3. Not a single byte moves before the receiver confirms. The server only pipes data between you and stores nothing.
4. Only sections the receiver does not have yet are added; explored terrain is never overwritten.

Map sharing is the least tested part of the mod. Try it on a world you can afford to lose first.

## Compatibility with other mods

- **Iris**: Iris 1.11.7 is supported. With a shader pack there are no stripes on the map and far water is not cut off.
- **Xaero's World Map**: the map gets a button to open it and a button back.
- **Xaero's Minimap, JourneyMap**: waypoint import from the `U` list, waypoint sharing in the Xaero format.
- **Litematica Plus LTS**: schematic placements show on the map as outlined boxes with their name and build progress, in the objects panel and as a footprint on the minimap. Click a label to select a placement, click again to open its editor. Litematica Plus also reads Voxy's blocks through VoxyMap to verify schematics in unloaded chunks.
- **Spatial GUI**: `M` opens a map window that Spatial GUI shows as its 3D panel: an experimental Voxy 3D view or a flat view of loaded chunks. The 3D view does not work with an Iris shader pack.
- **VanillaSquad**: on that server a "Server worlds" button opens the web map of the build, farm and art worlds, plus optional players and territories from the web map.

## Commands

| Command | What it does |
|---|---|
| `/voxymap route <x> <z>` | route to a point |
| `/voxymap route waypoint <name>` | route to a waypoint |
| `/voxymap route stop` | stop the route |
| `/voxymap seed <seed>` | the world seed for slime chunks on a server |
| `/voxymap seed clear` | forget the seed |
| `/voxymap waypoint add <dimension> <x> <y> <z> <color> <name>` | add a waypoint; the [Add] chat link runs this |

## Files

| Path | Contents |
|---|---|
| `config/voxymap.properties` | settings |
| `config/voxymap-waypoints.properties` | local waypoints |
| `config/voxymap-waypoint-prefs.properties` | where waypoints show and their symbols |
| `config/voxymap-sync.properties` | sync address and password per world |
| `voxymap/map2d/` in the game folder | 2D map tile cache |

Files are written through a temporary file with a `.bak` copy next to them. Shared waypoints live only on the sync server.

## FAQ

**The map is empty or almost empty.** The map shows only what Voxy has already seen. Walk around with Voxy running.

**The game crashes or Voxy does not load.** Make sure the game runs on OpenGL, not Vulkan, and that you use Iris 1.11.7 if you use shaders.

**No Nether roof removal or ore mask.** Check Diagnostics: if the Voxy shader patches did not load, these features are off. Usually that means Voxy is newer than the mod supports.

**Slime chunks on a server are wrong.** The client does not know the server's seed. Enter it with `/voxymap seed <seed>`.

**Teleporting from the map does not work.** On a server you need permission for the command in "Teleport command on servers". An empty field hides teleporting.

**`B` opens the emote wheel instead of a waypoint.** Since 1.0.5 the waypoint key is `N`. If you still have `B`, bind `N` in **Controls → VoxyMap3D** or press Reset.

**Mobs on the minimap are dots, not heads.** A head appears once the mob's model has been on screen at least once this session.

## Building from source

JDK 25 is required.

```powershell
git clone https://github.com/ServChan/3D-maps-Voxy-Addon.git
cd 3D-maps-Voxy-Addon
.\gradlew.bat build
```

On Linux and macOS use `./gradlew`. `build/libs/` gets `voxy-map-1.0.9-LTS.01.jar` and `voxy-map-1.0.9-LTS.01-player-markers.jar`. Voxy comes from Modrinth; if you put your own Voxy jar at the `voxy_jar` path in `gradle.properties`, the build uses that.

Before releasing a new version, go through the [in-game regression checklist](runtime-regression-checklist.md): it covers shaders, the Nether, crowds of players, the minimap and two clients, which tests cannot prove.

## Feature reference

A detailed description of the features, moved here from the README. Step-by-step instructions are in the sections above.

### Full-screen map

- Full-screen Voxy terrain view opened with a configurable key, `M` by default.
- Mouse dragging, right-button rotation, cursor-centered zoom, and smooth keyboard movement.
- Exact player centering plus projected markers and adaptive facing indicators for nearby active players.
- Your own view direction: a translucent wedge for your real horizontal field of view with a bright centre ray and arrowhead, so you can see where you are looking from the map.
- Stable near terrain: while the map is open, Voxy's cached LODs fill its normal player-centered hand-off zone instead of using Sodium's angle-dependent real-camera visibility set.
- 2D map: `V` or the 3D/2D toolbar buttons switch to a north-up flat map built from Voxy LOD sections. Tiles are cached as PNGs per world and slice and rescanned after block changes; markers, waypoints and overlays work in both views.
- Map weather and time: rain and thunder are hidden by default, or the map shows always rain, always thunder or the real weather (rain and snow by biome); the map lighting follows the game clock or is fixed to morning, day, evening or night. Clouds are hidden, and the shader pack's fog can be removed. The world itself keeps its real weather.
- Players and mobs on the 3D map are drawn as real models, enlarged with distance (at most 8x) and hidden when nothing connects them to the map camera. Setting "Models on the 3D map": players and mobs, players only, or off.
- Map chrome: a toolbar, a compass, a scale bar, an info card for the selected object and a keyboard help overlay (`H`). `G` jumps to typed coordinates; a chunk grid and slime-chunk, spawn-light and biome overlays can be switched on.
- Right-click menu on the map: add a waypoint or a temporary waypoint there, copy the coordinates, or teleport (needs operator rights).
- Voxy outside the map can be switched off ("Voxy LODs outside the map"), so Voxy only draws far terrain while the map is open.
- Whole-quad Nether roof removal clears the upper bedrock/anti-xray shell without slicing decoy LODs into a checkerboard, with a zoom-aware inspection aperture that opens deeper around the current map center at close range.
- Ore masking: vanilla ores and ancient debris in Voxy's LODs are drawn plain grey, so distant terrain and the map cannot be used to spot ores.
- Searchable Objects panel for players, waypoints and VanillaSquad territories, with center/edit/delete actions and live follow mode for a selected player. Players can be hidden from the map.
- VanillaSquad server worlds: on VanillaSquad a Server worlds button opens the flat squaremap web map of the build, farm or art world, even one you have never visited; switch with the buttons or keys `1`-`9`. Tiles are only downloaded while that screen is open.
- Map screenshot (`F2` with the map open): one frame without panels, HUD and chat, optionally keeping the markers.
- Optional singleplayer pause and adjustable camera speed.
- Optional handoff to Xaero's World Map with a return button.
- With Litematica Plus LTS installed, schematic placements appear on the map as outlined boxes with their name and build progress, in the Objects panel (filter Schematics) and as a footprint on the minimap. Click a label to select the placement, click it again or press Edit to open its editor; Center flies the map camera to it. The Schematics (Litematica Plus) layer setting hides all of it, including the ghost blocks Litematica Plus draws on the map. Litematica Plus also reads the blocks Voxy has stored through this mod to verify schematic parts whose chunks are not loaded.

### Routes

- A route to a waypoint or any point: right click on the map → "Route here", the "Route" button of a waypoint, or `/voxymap route <x> <z>` / `/voxymap route waypoint <name>`.
- Walking, swimming, boat and elytra legs; a card at the top of the screen shows the mode, distance, travel time and the next step, and the line shows on the big map and the minimap. The route is re-planned when you leave it and disappears on arrival. Nothing is automated.

### Waypoints

- Double-click the actual visible Voxy surface to drop a named pin at that voxel, including caves and multi-level builds; the add-waypoint key (`N`) does the same under the cursor. Pick one of the palette colours and an optional 1-2 letter symbol in the waypoint form; Enter saves, Esc cancels.
- In the world: `N` drops a waypoint where you stand, `U` opens the waypoint list, and an unbound "Temporary waypoint where I look" key drops a temporary pin that disappears once you reach it.
- Each waypoint can be shown everywhere, only on maps, only on the big map or nowhere (it stays in the `U` list); new waypoints follow the "Show new waypoints" setting. Waypoint look: square, circle or diamond icons, four icon sizes, solid, see-through or no label background, label size 75-200 %, a distance line and a fade-out range.
- Overworld waypoints appear in the Nether and back, converted by 8. Waypoints can be shared in chat in the Xaero format with an [Add] link, and imported from Xaero's Minimap and JourneyMap (button in the `U` list).
- Select a pin for explicit edit/delete actions; `Ctrl+Z` undoes a deletion. Overlapping labels are de-cluttered, and every object remains available in the Objects panel.
- In the world, each waypoint within 512 blocks shows a beacon beam in its colour. Hiding waypoints in the settings hides the beams too.
- Death points: each death drops a "Death point" waypoint; the five newest are kept per world.
- Optional sync with a self-hosted server (see [Waypoint sync server](#waypoint-sync-server) below): give a world just an address to pull everyone's shared pins read-only, or an address and a password to add/edit them too. With both set, the new-waypoint dialog has a switch between "Local only" and "Shared to server"; local pins never leave your computer.

### Minimap

- Always-on minimap in the corner during normal gameplay, separate from the fullscreen map - nearby players, mobs and waypoints on top of terrain colored the same way vanilla's own map item is.
- Built entirely client-side from already-loaded chunks (no server involved at all). Outdoors it shows the top surface, like Xaero's minimap; with a solid roof over your head (caves, buildings) and always in the Nether it shows the layer at your height.
- The cached texture is rebuilt when you cross into a new chunk or switch between surface and cave view, and a few tiles are refreshed in the background every quarter second, so block changes show up within a few seconds.
- Hold `Z` (rebindable, "Enlarge minimap") to draw it 1.6 times larger; `=` and `-` change the zoom.
- Square or circle, four sizes, any screen corner or a free position dragged in the layout screen, opacity, darker at night, block-texture or classic map colours, cave view (automatic, never, always), optional rotation with the player and a chunk grid. It can hide behind inventories or behind any screen, and hides on F1.
- Lines under the minimap: coordinates, Nether / Overworld coordinates, biome, light level, day and time, chunk, facing.
- Radar: hostile, neutral and passive mobs, dropped items and players as separate categories, drawn as heads (or heads only while `Tab` is held, or dots). Overlays: slime chunks (servers need the seed: `/voxymap seed <seed>`), where mobs can spawn (light 0), biome colours.

### Multiplayer extras

- Map sharing: send another player your explored Voxy terrain over a one-time 6-digit code, relayed live through the sync server (nothing is stored server-side). The receiver only gets sections they don't already have - their own explored terrain is never overwritten.
- VanillaSquad: optional global player markers with compact skin heads and clustered territory outlines from flags, isolated between the public build, farm and art maps. These rows only appear while you are connected to VanillaSquad.

English, Russian, Spanish (Spain and Mexico), and Polish translations.

### Requirements and optional mods

- Minecraft `26.3`.
- Java `25`.
- Fabric Loader `0.19.5` or newer.
- Fabric API.
- Voxy `0.2.20-beta` or newer for Minecraft 26.3. Voxy 0.2.20-beta for 26.3 is on [Modrinth](https://modrinth.com/mod/voxy) and needs Sodium; VoxyMap is compiled against it (`tools/deps/voxy-0.2.20-beta-26.3.jar`). Back up `.minecraft/.voxy` before moving from an older Voxy. Voxy refuses Minecraft's Vulkan backend, so the game has to run on OpenGL, and with shaders it supports Iris 1.11.7 (Iris 1.12 and newer break it). The Nether roof removal and ore mask shader patches target Voxy's internal shader layout; if a newer Voxy changes it, those two features switch themselves off instead of breaking the game.

Iris, Xaero's World Map, Litematica Plus LTS, Spatial GUI and Mod Menu are optional integrations. With Spatial GUI, M opens a map window that Spatial GUI shows as its 3D panel: an experimental 3D view rendered by Voxy (WASD, drag, right-drag to rotate, wheel to zoom) or a flat top-down view of loaded chunks, plus a button to the full 3D map. Both settings appear only when Spatial GUI is installed. The 3D view does not work with an Iris shader pack and falls back to the flat view. The mod is client-only and does not need to be installed on the server.

### Build variants

The build targets Minecraft 26.3 and produces the distributable JAR:

```powershell
.\gradlew.bat clean build --warning-mode all
```

One build produces two mod JARs in `build/libs/`:

| File | Contents |
|---|---|
| `voxy-map-<version>.jar` | The main release build. Everything except the VanillaSquad web-map player markers: the code that downloads player positions from the VanillaSquad web map is not in the JAR at all |
| `voxy-map-<version>-player-markers.jar` | The same plus those player markers |

In the main build without player markers the "VanillaSquad players" setting is not shown, and nothing is fetched from `players.json`. Territory flags still work. Because automatic realm detection (build / farm / art) relies on those player positions, "Auto" in that build means the build map; pick another realm by hand in the settings. The two JARs share the mod ID, so install only one of them. The build fails if any player-marker class ends up in the main JAR.

Before publishing, complete the [runtime regression checklist](runtime-regression-checklist.md).
It covers shader, Nether, crowded-player, minimap and two-client paths that unit tests cannot prove.

### Waypoint sync server

`voxymap-sync-server` is a small standalone program (no Minecraft/Fabric, no other dependency) that lets several VoxyMap clients share one waypoint list per Voxy world over HTTP. It's a separate build from the mod itself:

```powershell
.\gradlew.bat syncServerJar
java -jar build\libs\voxymap-sync-server-<version>.jar --bind 0.0.0.0 --port 8642 --password mysecret
```

or run it directly from the Gradle build with `.\gradlew.bat runSyncServer --args="--port 8642 --password mysecret"`.

Arguments (all optional): `--bind <address>` (default `127.0.0.1`; use `0.0.0.0` deliberately for LAN access), `--port <int>` (default `8642`), `--password <secret>` (omit to run permanently read-only), `--data <dir>` (default `./voxymap-sync-data`, one JSON file per Voxy world).

In VoxyMap's map Settings panel, set **Sync address** to `host:port` for a world to pull that world's waypoints from the server read-only; also set **Sync password** to match the server's `--password` to add, rename, and delete through it too. With a password set, the new-waypoint dialog decides per pin: "Local only" keeps it in your own config file, "Shared to server" uploads it for everyone. Shared pins you cannot edit (no password) are still shown, and trying to change one explains why. There's exactly one password for the whole server - not a per-player account system - so "knows the address" and "knows the address and the password" are the only two access levels.

Traffic is plain HTTP with no encryption, by design: this is meant for a trusted LAN or a small friend group's own server, not a public deployment. It binds only to localhost by default. Put a TLS-terminating reverse proxy with request-rate limits in front of it if it needs to be reachable over the open internet. The built-in server bounds waypoint request bodies, the number of pending map shares and relay-code claim attempts, but it is not an internet-facing identity or authorization service.

### Map sharing

Once a **Sync address** is set for a world (a password isn't needed for this - the relay itself needs no password), the Settings panel gets two more rows:

- **Share map** - first asks for two opposite corners on the 3D map, then serves only explored Voxy sections intersecting that rectangle under a fresh 6-digit code. The selection always includes the complete vertical column, so caves and builds inside it are not cut away. Leave the map open; the code keeps working for as many people as connect until you click the row again to stop, disconnect, or join a different world (sharing is tied to the specific world it was started for - it never keeps running unattended after you've left it).
- **Get map** - prompts for a code someone shared with you, claims it, and shows an estimated size ("~N MB - click to accept, right-click to decline") before anything is downloaded. Confirming starts the actual import; declining frees the code immediately.

Not a single byte of real terrain data moves until the receiver has seen the size estimate and explicitly confirmed - the relay's handshake (register → claim → confirm/decline → data connection) keeps the sender's export from starting at all until then, so there's nothing to see or intercept without an explicit yes from the person receiving it. Only the receiver who claimed the code can confirm, decline or download (the claim hands it a one-time token), and only the sender can poll the share's status, so another client that guesses a code cannot take over the transfer. The relay server only pipes bytes between the two of you once that happens - it never stores or inspects the terrain data. Import only ever fills in 16-block chunk sections that are still empty on your side; it never overwrites what you already have. A transfer that breaks off midway is reported as an error rather than as finished. Voxy assigns each block/biome type a small per-world-database id in the order it's first encountered, so a naive byte-for-byte copy between two independently-explored copies of the same server could silently show the wrong blocks - VoxyMap avoids this by translating every voxel through Voxy's own id-mapping system on import, registering any block/biome type your world hasn't seen yet exactly as normal exploration would.



### Known limitations

- In the Nether, the global roof threshold removes complete Voxy quads wholly inside the upper roof band instead of clipping fragments at a horizontal plane. This avoids exposing artificial cross-sections through multiplayer anti-xray replacements.
- The Nether inspection aperture follows the panned map center. At close zoom it lowers the whole-quad threshold aggressively enough to expose player-height terrain; by zoom `1.00` it blends back to the architectural-preserving global threshold. Its radius is derived from the visible world footprint and fades into the surrounding map instead of producing a fixed rectangular hole.
- Active-player facing indicators collapse to short direction ticks in dense groups. Hovering a marker restores its complete cone and draws that marker last.
- VoxyMap only displays terrain already available to Voxy; it does not load chunks or reveal unexplored server data.
- Optional distant VanillaSquad markers come from that server's public web-map endpoints and are disabled by default. Auto mode resolves build/farm/art from the local player or another loaded player. Because hidden players are absent from squaremap, an unresolved session keeps the safe build fallback instead of blanking both overlays; an explicit source selection is stored independently for the current Voxy world.
- Map sharing writes directly into Voxy's live in-memory world storage (through its own public ingestion path, not a private/undocumented one) - it's the newest and least battle-tested part of VoxyMap. Try it on a world you can afford to be wrong about before trusting it on one with irreplaceable exploration progress.
- The minimap only shows chunks already loaded by the normal game, same as vanilla's own map item - it never reveals unexplored terrain. A mob whose face has not been rendered yet this session (its model variant was never on screen) keeps a dot while Tab is held, until it is seen.
- Build and package checks do not replace an in-game test with the exact Voxy, Iris, Sodium, and Xaero versions in the target profile.

### Settings notes

Settings are grouped the same way in the map's **Settings** panel and in the Mod Menu screen (Mods -> VoxyMap LTS -> Configure). Both have a search field, categories, a hint for the setting under the cursor, a reset button on every row and Reset all:


The Mod Menu screen scrolls and lists the global settings. Sync address and password and the VanillaSquad map are stored per world, so they appear there when the screen is opened from inside a world (Esc, Mods); map sharing needs the map's own area selection and is only in the map's Settings panel.


Fog suppression removes the shader pack's own fog while the map is open. It supports Complementary (Reimagined and Unbound), Rethinking Voxels, Solas, BSL, Mellow and Photon; with any other pack it does nothing. The pack must load one of the fog files listed in `ShaderPackFogPatcher`; a pack version with a changed layout is left untouched and logged once.


Settings are stored in `config/voxymap.properties`, local waypoints in `config/voxymap-waypoints.properties`, and the per-world sync address and password in `config/voxymap-sync.properties`. They write through a sibling temporary file and keep a `.bak` copy for recovery. Shared waypoints live only on the sync server.
