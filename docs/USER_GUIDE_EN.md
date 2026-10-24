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
