# VoxyMap LTS development notes

The workspace root `AGENTS.md` (`E:/Coding/Minecraft/AGENTS.md`) also applies. This file only lists
where this project deliberately deviates from that default, plus patterns specific to it.

## Deviations from the workspace default

- **Voxy comes from a local jar, not Modrinth.** No Voxy for Minecraft 26.3 is published yet, so `build.gradle`
  compiles against `voxy_jar` (`../tools/deps/voxy-0.2.20-beta-26.3.jar`, built from upstream Voxy's `263` branch,
  commit `2c5698e6`) and falls back to `maven.modrinth:voxy:${voxy_version}` only when that file is missing. When
  Voxy publishes a 26.3 release, point `voxy_version` at it and delete `voxy_jar`. Upstream marks the `263` branch
  as not release-ready ("CORRUPTS OLD WORLDS"): tell users to back up `.voxy` before trying it. It also throws on
  Minecraft's Vulkan backend, so test on OpenGL.
- **Package is `com.voxymap.client`**, not `org.lts.voxymap`. Keep using the existing package; do not
  "correct" it to match the workspace default.
- Mod ID `voxymap`, archives base name `voxy-map` (so the built jar is `voxy-map-<version>.jar`, not
  `voxymap-<version>.jar` - check `build/libs/` by that name).
- **Two mod jars per build.** The main `voxy-map-<version>.jar` is the release build and has **no**
  VanillaSquad web-map player markers: `com/voxymap/client/playermarkers/**`, the separate `src/playerMarkers`
  source set that fetches player positions from the VanillaSquad web map, is not in it.
  `voxy-map-<version>-player-markers.jar` (task `playerMarkersJar`) is the same jar plus that source set. Client code reaches
  that module only through `integration/RemotePlayerMarkers`, which loads it by class name and falls back to
  "no markers" when it is absent. Never import `com.voxymap.client.playermarkers` from `src/client`;
  `ProjectContractTest` pins this; the `jar` task fails if a player-marker class leaks into the main jar, and
  `playerMarkersJar` fails if they are missing from the variant.

## Voxy shader coupling (fragile - read before touching `VoxyShaderPatcher`)

`VoxyShaderPatcher` string-patches Voxy's internal `quads3.vert`/`quads.frag` source (marker strings,
uniform locations `30` and `33`-`38`). The mod declares `voxy >= 0.2.20-beta` in `fabric.mod.json` and is
compiled against the `voxy_jar` build of `0.2.20-beta` (26.3). `0.2.19-beta` and the `263` branch were checked by
diffing: the four shader files are byte-identical to `0.2.18-beta` and every Voxy class and member VoxyMap uses is
unchanged, except that `VoxyConfig.useEnvironmentalFog` (boolean) became `fogMode` (String), which `VoxyBridge` already handles.
A newer Voxy can still change that layout, so re-check it (see below) before widening the claim.

- The patcher fails loudly (`patch()` throws `IllegalStateException`) by design - `VoxyShaderPatcherTest`
  pins this. Do not make `patch()` itself swallow the mismatch.
- The mixin call site uses `patchSafely()` instead, which catches that exception, disables the
  Nether roof removal and ore mask (`VoxyShaderPatcher.isSupported()` flips to `false`, checked by
  `MDICSectionRendererMixin` before touching their uniforms), and logs a warning - so an
  incompatible Voxy update degrades gracefully instead of crashing shader compilation for the whole
  client. Keep both halves in sync if you touch either.
- Every `@Inject` into a Voxy class carries `require = 0`: the dependency range is open-ended (`>=0.2.18-beta`), and a
  renamed Voxy method must switch the feature off, not stop the game from starting.
- After bumping `voxy_version`, re-verify the marker strings in `VoxyShaderPatcher` against the
  new Voxy jar's actual shader source before assuming the patch still applies.

## Anti-xray invariant (Nether roof removal)

The roof band is removed by discarding whole quads (`voxymapQuadMinY >= voxymapRoofCutY` in
`VoxyShaderPatcher`), never by clipping fragments at a plane: a plane slices straight through the
server's anti-xray decoy LODs and shows them as a tiled cross-section that looks like real terrain.
`ProjectContractTest` pins this. Do not add a per-fragment height cut, and do not add any feature that
treats client data the server may have withheld (unloaded or obfuscated chunks) as verified terrain or
open air - the removed room cutaway was exactly that kind of guess.

The minimap's cave scan (`MinimapTileCache.caveFloorHeight`) follows the same rule: when the player's height is
inside solid rock it drops through at most 8 blocks to the next open space and otherwise leaves the column empty,
because Paper's layered anti-xray fills hidden rock with one random block per chunk layer and drawing it shows a
chunk-sized checkerboard.

The ore mask follows the same rule from the other side: `OreMaskController` marks every Voxy model whose
block is a vanilla `minecraft:*_ore` or `minecraft:ancient_debris`, and the patched fragment shader paints
those models grey everywhere Voxy draws them, not only on the map. It has no setting; it switches off only
together with the Voxy shader patch (`VoxyShaderPatcher.isSupported()`) or when Voxy's `modelService` field
cannot be read.

## Mixin gating

`VoxyMapMixinPlugin` applies a mixin whose class name starts with `Iris` only when Iris is loaded, so Iris
classes are never touched without Iris, and one starting with `SpatialGui` only when Spatial GUI (`spatial-gui`)
is loaded. `SpatialGuiClientMixin` makes `SpatialGUIClient.shouldHookScreen` return `false` for `MapScreen` and
`true` for `WindowMapScreen`; every other Spatial GUI hook is gated by that method. `MapScreen` moves the game camera
and Voxy draws it full screen, so it can never be a Spatial GUI panel. With Spatial GUI loaded and
`SPATIAL_GUI_FLAT_MAP` on (`spatialGuiFlatMap`), M opens `WindowMapScreen`, a plain GUI screen that Spatial GUI renders
as its 3D panel. It has two views, switched by its own button and `SPATIAL_GUI_3D` (`windowMap3d`); both settings
rows exist only when Spatial GUI is loaded (`SettingsCatalog.sections(..., spatialGuiInstalled)`). A new mixin that
targets Iris must use that prefix; any other optional target (Xaero) uses `@Pseudo` plus `require = 0`, as
`XaeroGuiMapMixin` does.

- Flat view: `FlatMapTerrain` scans loaded chunks with `MinimapTileCache.computeTile` (32 per frame, rescanned every
  4 s) into 128x128 region textures, at most `MAX_REGIONS`, least recently used evicted; `FlatMapRenderer` draws it.
- 3D view: `WindowMapRenderer` asks Voxy for a second view into its own `TextureTarget`, the way Voxy serves
  Vivecraft's second eye. While `WindowMapRenderer.rendering()` is true, `VoxyViewportSelectorMixin` hands Voxy an
  extra viewport (`ViewportSelector.getOrCreate(VIEWPORT_KEY)`, by reflection) so the player's viewport and its
  occlusion state stay untouched, `VoxyRenderSystemMixin` skips `RenderDistanceTracker.setCenterAndProcess` so LOD
  loading stays centred on the player, and `VoxyClientMixin` reports `disableSodiumChunkRender` so LODs fill the near
  zone. The camera is `VoxyMapCameraController.pose(...)`, the same maths as the full map. The Nether roof cut works there too:
  `NetherRoofController.isActive()` is also true while `WindowMapRenderer.rendering()`, and the screen feeds
  `NetherRoofController.tick/updateViewContext` like `MapScreen` does. Any exception turns the 3D
  view off for the session (`Status.FAILED`) and the screen falls back to the flat view with a footer note; with an
  Iris shader pack in use it stays flat (`Status.SHADER_PACK`). The colour target is blitted with V flipped (0,1,1,0),
  like vanilla picture-in-picture. Markers in 3D are projected with the stored view-projection matrix.

## Minimap heads

Mob icons are front views of the head, composed like Xaero's: `HeadGeometry.capture` (main thread) resets the
pose of the model's `head` part and its children, plus root-level face parts such as `hat`, `beak` or `nose`, and
collects every polygon facing -Z; `HeadIconComposer.compose` (worker thread, after reading the mob texture through
the resource manager) rasterises each one as a parallelogram (rotated ears and horns must not fill their bounding
box) back to front at one pixel per texel; `MobHeadIcons.tick` uploads the result as a
`DynamicTexture` holding the icon and a white silhouette mask side by side. The minimap draws the mask four times
as an outline (red for hostile, amber for neutral, dark otherwise) and the icon on top, keeping its aspect ratio,
at most 12 px. Icons are keyed by mob texture, learned from `LivingEntityRendererMixin` or, for renderers with a
single model, straight from the renderer; textures that are not plain resources (player skins) keep a dot.
`MinimapHeads` picks heads or dots from `VoxyMapSettings.radarIcons()` (always heads by default, only while the
player-list key is held, or dots); mobs and players more than 3 blocks above or below get a small arrow.

## Minimap colours

`MinimapTileCache.computeTile` colours each column from the block model's top quad: `MinimapBlockColors` averages
the sprite's first frame (`SpriteContentsAccessor.originalImage`), caches it per `BlockState` until the
`BlockStateModelSet` instance changes (resource reload), and multiplies biome tint through `BlockColors.getTintSource`.
Water is blended over the floor by depth, and slopes are lit from the north-west (`MapColorMath`). Any failure
switches texture colours off for the session and falls back to `MapColor`; `MinimapColors.CLASSIC` keeps the old
vanilla-map look. The minimap texture covers 33x33 chunks and computes at most `TILE_COMPUTE_BUDGET` new tiles
per frame, nearest first.

## Waypoints outside the map screen

B opens `WaypointEditScreen`, U opens `WaypointListScreen`. Temporary waypoints live only in memory
(`WaypointManager.temporaryByWorld`, ids start with `temp-`), are never synced, and are removed once the player
has been more than 12 blocks away and comes back within 6. Chat sharing uses Xaero's `xaero-waypoint:` format
(`WaypointText`) so Xaero users can import ours and we import theirs; `WaypointChatShare` answers a shared
waypoint with a client-side `[Add]` link that runs `/voxymap waypoint add <dimension> <x> <y> <z> <color> <name>`
and converts Nether/Overworld coordinates at click time. Keep the name as the last (greedy) argument.
In-world icons and labels (`WaypointLabels`) are HUD elements drawn at the head of `Hud.extractRenderState`, so
the hotbar and minimap stay on top. They project with `Camera.getViewRotationProjectionMatrix`, which follows
dynamic FOV and view bobbing; far points are scaled to 1000 blocks before projecting to keep float precision
(the matrix has no translation, so x/w and y/w do not change). Beacon beams are a separate toggle (`waypointBeams`).

Overworld waypoints show in the Nether (and Nether ones in the Overworld) at x/8 or x*8, faded, through
`DimensionLinks.foreignWaypoints`. Waypoints are stored per Voxy world id, which is SHA-256 of biome seed + level key,
so the paired id is first taken from a link learned when the player crosses between the two dimensions on one
connection (`VoxyMapSettings.netherLinks`, persisted), and only then computed from the current biome seed. Servers
with a different seed per world rely on the learned link. Editing, deleting or sharing a foreign waypoint acts on
its source world id and dimension.

## Map overlays

Slime chunks, spawn light and biome colours are baked into minimap tiles in `MinimapTileCache.applyOverlays`, so
the minimap clears its tile cache whenever the colour mode, the overlay set or the slime seed changes
(`MinimapRenderer.TileLook`). Slime chunks use `WorldgenRandom.seedSlimeChunk` with salt `987234911` (pinned against
the published formula in `MinimapOverlaysTest`); the seed comes from the integrated server in singleplayer and from
`/voxymap seed <seed>` (stored per Overworld world id) on servers. Spawn light marks floors that are a valid zombie
spawn with block light 0 above them.

## Minimap placement and visibility

`MinimapPlacement.place` is the only place that turns the corner preset or the dragged fractions
(`minimapX`/`minimapY`, share of the free space) into a screen position; `MinimapLayoutScreen` drags the live minimap
and saves only on release. The minimap and the in-world waypoint icons hide on F1 (`Hud.isHidden()`) and, by the
`minimapScreenVisibility` setting, behind inventories or any screen except chat (`MinimapRenderer.hiddenByScreen`).

## Waypoint display settings and import

Hiding a single waypoint and its 1-2 letter symbol are local preferences keyed by waypoint id
(`WaypointPrefs`, `config/voxymap-waypoint-prefs.properties`), so the waypoint format and the sync protocol stay
unchanged and shared waypoints can be hidden too. Every renderer reads `WaypointManager.visibleForWorld`; only the
list screen and the object panel see hidden ones. `WaypointImport` scans `xaero/minimap`, `XaeroWaypoints` and
`journeymap/data` off the main thread and imports into the current world as local waypoints, skipping ones with the
same name and block, keeping Xaero symbols and disabled flags and skipping deathpoints.

The big map (M) draws the chunk grid and slime chunk outlines with `draw3DLine` at the camera focus height and
limits both to what is on screen (`visibleGroundBounds`); G opens the go-to-coordinates input.

## Shader-pack fog patches (`ShaderPackFogPatcher`)

Fog suppression works by rewriting each supported pack's small fog include as Iris loads it
(`IrisIncludeProcessorMixin`), gated by the `voxymapMapView` uniform. Each pack is a `Flavor` of anchors
keyed by include path; a flavour applies only if every anchor is present. Two edit kinds: `Replace`
(scale a fog amount in front of where it is blended) and `EntryReturn` (return "no fog" as the first
statement of a fog function). When a pack updates, re-check its anchors against the new source; the
unit tests use excerpts, so they cannot notice a real layout change. A new pack means a new flavour,
its include path in the map, and a test excerpt.

## Other VanillaSquad worlds (web map)

On VanillaSquad (`VanillaSquadServer.isConnected()`, by server address) the big map shows a "Server worlds" button that
opens `WebMapScreen`: a flat view of the squaremap web map of any world of `build`, `farm` or `art`, switched by
buttons or keys 1-9; "3D map" goes back to `MapScreen`. It is not Voxy data, so it works for worlds never visited and
needs no player-markers module. `VanillaSquadWebMap` loads `tiles/settings.json` per map and `tiles/<world>/settings.json`
(max zoom, spawn) only while the screen is open; `WebMapTiles` downloads 512 px PNG tiles
(`tiles/<world>/<zoom>/<x>_<z>.png`, a tile covers `512 << (max - zoom)` blocks), at most 6 at a time, decodes them off
the main thread, keeps at most 64 textures and frees them all in `WebMapScreen.removed()`. A missing tile answers
200 with an empty HTML body, so tiles are checked for the PNG signature. While a tile loads, `WebMapRenderer` draws
the matching part of a coarser loaded tile. Waypoints of another world come from `webMapLink.<map>/<world>` in
`voxymap.properties`, learned while the player stands in that world and the map id is known
(`RemotePlayerMarkers.resolvedMapId`: the configured map, or the player-markers auto-detection once it resolved).
Keep tile math in `WebMapGeometry` (unit tested) and do not route these lines through `MapProjection.clipLine`: its
long-segment rejection is for perspective projection and would drop long flat territory edges.

## Optional-mod integration pattern

Xaero's World Map is not a compile-time dependency (it is not published anywhere this project's
Gradle can resolve) and is not required to be installed. Its integration (`XaeroWorldMapBridge`)
is the shape to copy for the next optional mod:

1. `FabricLoader.getInstance().isModLoaded("<mod_id>")` gates everything.
2. Resolve `Class.forName(...)` + `Method` handles **once**, in a static field initializer, not per
   call - these run every render frame while the map is open.
3. Wrap every reflective call site in `catch (ReflectiveOperationException | LinkageError |
   RuntimeException)` (not narrower) and fail to `null`/no-op - an incompatible or absent mod must
   never throw out of a render or mixin call site. `ClassCastException` is a `RuntimeException`;
   listing both in one multi-catch is a compile error.
4. Settings live in `SettingsCatalog`, which both the map panel and the Mod Menu screen render.
   To make a row depend on an optional mod, add a condition to `SettingsCatalog.sections(...)` (with a
   test) rather than hiding the row in one screen only.

## Screen projection precision

A world point near edge-on to the camera (small but valid `depth`, just past the near-plane cutoff)
projects to a technically finite but wildly wrong screen coordinate - projection is only meaningful
for points in front of the camera, and nothing downstream can recover a bad coordinate once produced.
This bit twice and needs both fixes kept:

1. `MapScreen#projectToScreen` clamps `screenX`/`screenY` to `±1.0E6` before rounding to `int`.
   `Math.round(double)` returns a `long`; narrowing an unclamped astronomical value to `int` silently
   wraps around instead of clamping, producing a coordinate that can still pass the line clipper's
   checks and drive a per-pixel fill loop into hundreds of millions of iterations - a full render-
   thread freeze, not a crash, hard to spot from a stack trace.
2. `MapScreen#clipLineToViewport` rejects (`return null`) any clipped segment longer than roughly two
   screen diagonals. Clamping alone is not enough: a bad-but-clamped coordinate is still a *valid*,
   finite point that can legitimately fall inside the padded viewport rectangle, so the clipper accepts
   it and returns a segment that streaks across (and past) the whole screen. One such line is a fill
   loop of a few thousand iterations, not hundreds of millions - not a freeze, but with enough of them
   in one frame (a long route or outline drawn across a wide area) it is a severe, silent
   frame-time hit that reads as "FPS drops to 1 whenever that overlay is on screen."

Any new screen-space overlay must reuse `projectToScreen`/`clipLineToViewport`, not reimplement
projection or clipping math, and must not remove either guard. A route overlay (since removed) is
what first surfaced both, because it drew points thousands of blocks from the player - territory
outlines and player markers stay much closer together and rarely hit the edge case.

## Deploy target

Same as the workspace default: `D:/Modrinth App/profiles/Everything Voxy/mods/`.
