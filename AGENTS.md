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

## Vulkan backend

Minecraft 26.3 can run on Vulkan (`preferredGraphicsBackend:"vulkan"`), where there is no OpenGL context and any
`org.lwjgl.opengl` call aborts the JVM. Every raw GL path (`OreMaskTexture`/`GlClientTextureUpload` through
`OreMaskController.tick`, the uniforms in `MDICSectionRendererMixin`, the Voxy second view in `WindowMapRenderer`)
runs only when `map/GraphicsBackend.openGl()` is true; it reads `DeviceInfo.backendName()` of
`RenderSystem.tryGetDevice()`. On Vulkan those features are off with one log line and the diagnostics row shows
`screen.voxymap.diagnostics.vulkan`. Put any new raw GL call behind the same check.

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
  zone. The camera is `VoxyMapCameraController.pose(...)`, the same maths as the full map. Voxy picks a 0.1 block
  near plane whenever `disableSodiumChunkRender` is true; with the map camera at least 1024 blocks from its focus that
  leaves almost no depth precision, and shader packs that rebuild positions from Voxy depth (Solas shadows) draw
  screen-aligned stripes over the LODs, so `VoxyRenderSystemMixin` swaps it for `LOD_NEAR_PLANE` (16) while either map
  view renders. `CameraMixin` also raises the game's own `depthFar` to `DEPTH_FAR` on the big map: Solas keeps Voxy water
  only where it is nearer than the vanilla depth, which is the vanilla far plane over open sky, so water past that
  distance vanished along a straight line. The Nether roof cut works there too:
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
switches texture colours off for the session (only when it happens on the main thread) and falls back to `MapColor`;
2D tile workers sample sprites too, so `SpriteContentsMixin` makes `SpriteContents.close()` wait for
`SpriteReadLock` readers and samples are stored per `BlockStateModelSet`; `MinimapColors.CLASSIC` keeps the old
vanilla-map look. The minimap texture covers 33x33 chunks and computes at most `TILE_COMPUTE_BUDGET` new tiles
per frame, nearest first.

## Waypoints outside the map screen

B opens `WaypointEditScreen`, U opens `WaypointListScreen`. Adding and editing a waypoint always goes through
`WaypointForm` (name, symbol, coordinates, colour, display mode, temporary/shared), hosted by `WaypointEditScreen` and
embedded in `MapScreen`: there its widgets are added as screen children and rendered by hand (MapScreen does not call
`super.extractRenderState`), clicks inside the panel go to `super.mouseClicked`, a click on the map moves the pending
point, and an accidental click no longer saves. On the big map a right click without dragging opens `MapContextMenu`
(waypoint, temporary waypoint, copy coordinates, teleport for operators) and the add-waypoint key places one under the
cursor. Minecraft 26.3 delivers `charTyped` only after `TextInputManager.startTextInput` (SDL3): `EditBox` does it on
focus, but a hand-drawn input field must call `minecraft.textInputManager().startTextInput(owner)` itself
(`MapScreen.syncTextInput`), otherwise nothing can be typed into it. Temporary waypoints live only in memory
(`WaypointManager.temporaryByWorld`, ids start with `temp-`), are never synced, and are removed once the player
has been more than 12 blocks away and comes back within 6. Chat sharing uses Xaero's `xaero-waypoint:` format
(`WaypointText`) so Xaero users can import ours and we import theirs; `WaypointChatShare` answers a shared
waypoint with a client-side `[Add]` link that runs `/voxymap waypoint add <dimension> <x> <y> <z> <color> <name>`
and converts Nether/Overworld coordinates at click time. Keep the name as the last (greedy) argument.
In-world icons and labels (`WaypointLabels`) are HUD elements drawn at the head of `Hud.extractRenderState`, so
the hotbar and minimap stay on top. They project with `Camera.getViewRotationProjectionMatrix`, which follows
dynamic FOV and view bobbing; far points are scaled to 1000 blocks before projecting to keep float precision
(the matrix has no translation, so x/w and y/w do not change). Beacon beams are a separate toggle (`waypointBeams`).
The "Waypoint look" settings group drives their appearance: icon shape (`WaypointIcons.rowSpan`, applied to every
waypoint icon, including the minimap and big map), in-world icon size, label background, label scale (drawn through
`g.pose()`), the distance line, and a range that fades icons and labels over its last 20% (`WaypointLabels.fade`) and
also caps beam distance.

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

Where a waypoint is drawn and its 1-2 letter symbol are local preferences keyed by waypoint id
(`WaypointPrefs`, `config/voxymap-waypoint-prefs.properties`), so the waypoint format and the sync protocol stay
unchanged and shared waypoints can be hidden too. `WaypointDisplay` is everywhere / maps only (minimap and big map) /
big map only / hidden; legacy `hidden.<id>=true` entries load as hidden. New waypoints take the `newWaypointDisplay`
setting. Every renderer reads `WaypointManager.visibleForWorld(worldId, Surface)` with its own surface (`WORLD` for
labels and beams, `MINIMAP`, `MAP` for the big map, the window map and the web map) and filters foreign-dimension
waypoints the same way; only the list screen and the object panel see hidden ones. `WaypointImport` scans `xaero/minimap`, `XaeroWaypoints` and
`journeymap/data` off the main thread and imports into the current world as local waypoints, skipping ones with the
same name and block, keeping Xaero symbols and disabled flags and skipping deathpoints.

The big map (M) draws the chunk grid and slime chunk outlines with `draw3DLine` at the camera focus height and
limits both to what is on screen (`visibleGroundBounds`); G opens the go-to-coordinates input.

## Waypoint sync and map share relay

`WaypointSyncClient` keeps every shared add/edit/delete in `pendingPuts`/`pendingDeletes` and sends them one at a
time per world and address (`State.sending`), so two changes to one waypoint can never reach the server out of order.
The cached list is the server list with the pending changes laid over it. A 2xx (and 404 for a delete) drops the
pending entry and sends the next one; 400/403/404/413 can never succeed, so the entry is dropped, the error stays in
the status, and a rejected new waypoint is kept as a local one (`keepLocally`); 401, 429 and network errors stop the
queue until the 30 s retry. A GET can no longer make a failed change vanish with "OK". Timestamps start at
`Long.MIN_VALUE / 2`: `now - Long.MIN_VALUE` overflows to a negative number, which is why the first GET never ran
before. The GET body is capped at 4 MB and parsed with `WaypointJson.readListStrict`. ASCII passwords go in
`X-VoxyMap-Password`; anything else goes Base64(UTF-8) in `X-VoxyMap-Password-B64`, because the JDK HTTP/1.1 client
writes header values as US-ASCII and turns other characters into `?`.

The server prefers `X-VoxyMap-Password-B64`, compares bytes, reserves a failed-password slot atomically
(`RelayClaimRateLimiter.tryReserve`, 10 per address per minute, released on success) before comparing, saves a copy of
a world's list before swapping it in, moves an unparseable world file to `<id>.json.corrupt-<time>` (other read errors
keep the file and make that world refuse writes), and rejects names with lone surrogates. The relay keeps at most 4
waiting senders per address and cancels the oldest one (410) when a fifth arrives, since a client that stopped sharing
cannot cancel its server-side wait; a sender waits at most `DATA_CONNECT_TIMEOUT_SECONDS` for the receiver to start
downloading, and the pending entry is removed before the sender gets its answer. The client picks a new share code
only when the very first `/wait` of a share gets 409, and the code swap and start/stop are synchronized. Map share
protocol v5 carries the sender's dimension key; the receiver refuses a stream from another dimension instead of
writing it into its own Voxy world.

## Voxy engine references

Voxy frees a `WorldEngine` (and closes its RocksDB storage) once `isWorldUsed()` is false, which only counts
`acquireRef` and loaded sections. Every read of Voxy data off the render thread therefore holds a reference through
`VoxySectionBridge.holdRef`/`dropRef` for the duration of one short read: a 2D tile scan (`Map2dTiles.build`), a
cursor pick (`VoxyMapPicker.pick`), a padded section read (`VoxyWorldData.chunkSectionPadded`), one section export or
import, and the key scan of a map share. Never hold one while writing to a network pipe or waiting on another
thread, or disconnecting waits for it. Map share collects its section keys first (`exportedSectionKeys`), so no
RocksDB iterator stays open during a transfer, and stops when the engine is gone. When the client has no level,
`MinimapRenderer.forgetLevel()` drops the static references to the previous world.

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

## 2D map (Tab on the big map)

`MapScreen` switches between 3D and 2D with V or the 3D/2D buttons in the toolbar; the choice is saved as `map2d`.
In 2D the camera controller stays active, `MapViewMode.flat()` is true, and `projectToScreen`/`mapPointAt`/`draw3DLine`
switch to a north-up orthographic projection, so every overlay (waypoints, players, grid, flags, schematics) works in
both modes. Picking in 2D casts a vertical ray through `VoxyMapPicker` (from the top of the world, or from the cave
slice in dimensions with a ceiling). Tiles come from Voxy's own data, not from loaded chunks: `Map2dScanner` reads
level-L Voxy sections (L = 0..4, 8x8 section columns per 256 px tile) top-down and colours the first voxel through
`MinimapBlockColors` with a fake `Map2dTintGetter` for biome tint; levels 5..6 are 2x2 downsamples of the level below.
`Map2dTiles` builds tiles on two background threads, caches PNGs in `<game>/voxymap/map2d/<voxy world id>/<surface|caveY>/`,
shows a disk tile at once and rescans it once per session, and rescans tiles 4 s after `ClientLevelMixin` reports a block
change or chunk load. All tile textures are released when the map closes. The cave scan keeps the anti-xray rule: at
most 8 blocks (at least one voxel) of solid rock below the slice before the column is left empty. Never colour anything
with `Xm*`-style code copied from Xaero's (see the VoxyMap Bobby fork notes).

## Map weather, entity models, Voxy outside the map

- `mapWeather` (clear/rain/thunder/auto) drives `LevelMixin` while the map is open; `WeatherEffectRendererMixin` always hides vanilla rain on the map and
  `MapWeatherOverlay` draws rain streaks on the map because vanilla rain only falls around the far-away camera.
- `mapEntityModels`: `EntityRenderDispatcherMixin` and `LevelExtractorMixin` (which skips the compiled-section check
  of `LevelExtractor.isEntityVisible`, so players at the edge of the server view distance still render) force players
  and mobs to render on the 3D map without a frustum test: vanilla builds the cull frustum from the player's own eye
  in `Camera.update` before `CameraMixin` moves the camera, so testing against it hid every model behind the player.
  With a shader pack Voxy's LODs write no vanilla depth, so nothing on the map hides
  an entity behind terrain by itself: while the map is open both mixins drop every entity for which
  `MapEntityOcclusion.visible` finds no clear ray from its head or middle to the map camera within 96 blocks
  (`VoxelRayPicker` over the client level; a block blocks the ray when its occlusion shape fills at least half the
  cell, so fences, rails, trapdoors, glass, leaves and fluids do not; blocks above the Nether roof cut are open).
  Results are reused for 250 ms per entity while neither it nor the camera moved. Particles have the same depth
  problem and are far too many to ray-test, so `ParticleEngineMixin` skips particle extraction (campfire smoke,
  splashes) while the map is open. `SoundEngineMixin` keeps the sound listener at the player's eyes while the map
  camera is far away, and `OptionsMixin` makes `Options.save` write the player's own cloud setting while
  `MapRenderSettingsGuard` has clouds switched off for the map. Then
  `LivingEntityRendererMixin` enlarges them (`MapEntityScale`, at least 14 px for players and 8 px for mobs, at most
  8x; a model that would need more is left to its marker). Growth is computed from the standing height
  (`getDimensions(Pose.STANDING)`), never the current hitbox: gliding, swimming and sleeping shrink the hitbox and
  used to blow the model up threefold or more. Both follow `ServerRules.radarAllowed()` and the radar categories; the local player skips the radar rules but not the occlusion check.
- `voxyInWorld`: when off, `VoxyRenderGate` cancels Voxy's MDIC passes outside the 3D map (and always in 2D), runs
  `VoxyRenderSystem.renderOpaque` only every 30th frame, and `FogSetupBefore/AfterVoxyMixin` put the vanilla fog back.
- `.\gradlew.bat bundledJar` builds `voxy-map-<version>-bundled.jar` with `voxy_jar` nested in `META-INF/jars`. It is a
  personal build only (Voxy is All Rights Reserved: "Do not redistribute"); it is not part of `build`/`assemble`.
  The nested Voxy is repacked by `bundledVoxyJar` with its Sodium dependency widened to `bundled_sodium_range`
  (default `>=0.9.2- <0.10`); before widening further, diff `javap -p -s -c` of every Sodium class Voxy references
  between the old and the new Sodium jar (0.9.2 and 0.9.3-alpha.1 were identical for all 57 classes).

## Navigation (`nav`)

A route to a waypoint or a point starts from the big map (right click -> "Route here", or the "Route" button of a
waypoint in the object panel) or from `/voxymap route <x> <z>`, `/voxymap route waypoint <name>` and
`/voxymap route stop`. `Navigator` keeps the one active route and plans on its own background thread:
`NavArea.around` sizes the grid (1-block cells under 400 blocks, 2 under 1500, then 4..16, at most
`MAX_CELLS`), `NavTerrain` reads Voxy sections of that level top-down (holding an engine reference per section
column) and classifies the first surface voxel through `NavBlocks` (leaves and the logs under them, plants and other
blocks without collision are skipped; water, ice, dirt paths and rails, hazards and lava get their own kinds; columns
Voxy never saw stay `UNKNOWN`). In dimensions with a ceiling the scan starts 24 blocks above the player and looks for
the first floor below open air. `RoutePlanner` is an A* over cells with a second layer for "in a boat" when the player
carries a boat: seconds per block per kind, a climb limit of one voxel per cell, drops up to two voxels (any drop
into water), boarding and leaving cost a few seconds, and the result is cut into legs by mode (walk, swim, boat,
unknown) and simplified within one cell. `ElytraPlanner` adds a straight flight above the highest known terrain
plus 32 blocks when the player has an elytra and rockets, and `ElytraPlanner.prefer` picks it when it is faster; the
ground route is then drawn faintly. No path, no Voxy data or a route past the coarsest grid gives a straight line
(`Route.direct`). `RouteProgress` follows the player along the legs, `Navigator.tick` re-plans after 2 s more than
16 blocks off the route (96 in flight) or when a boat or rockets appear or run out, clears the route on arrival
(6 blocks) or when the waypoint world changes. `RouteHud` draws a card at the top of the screen (mode, distance,
time, the next change of travel, an arrow) and a marker on the route ahead; the big map and the minimap draw the
legs in `UiTheme.ROUTE_*` colours. Movement is never automated.

## Minimap smoothness

`MinimapRenderer` interpolates the player, mobs (`MobSnapshot.source`) and other players with the partial tick, shifts
the terrain blit by the sub-texel remainder and draws markers at fractional positions through `g.pose()`. Changed
chunks are queued by `ClientLevelMixin` (`blockChanged`/`markDirty`) and repainted up to 16 per frame.

## Interface components (`gui/ui`)

Every screen draws with the shared kit instead of hand-picked colours: `UiTheme` (colour, spacing and size tokens;
`UiThemeTest` checks WCAG contrast of text and controls), `UiDraw` (square panels and outlines, vanilla separator
pairs, focus ring, ellipsized text, key caps), `UiIcons` (9x9 pixel icons drawn with `fill`, no textures, so they work on Vulkan too; no
emoji or font glyphs as icons), `UiMotion` (eased hover/toggle/compass motion, switched off by `uiAnimations`). Widgets
extend vanilla ones so keyboard focus, Enter/Space and narration keep working: `UiButton` (primary, secondary, ghost,
danger; selected, loading), `UiToggle`, `UiCycler` (Left/Right or a click on the left third steps back), `UiSlider`,
`UiTextField` (an `EditBox` inside its own frame). The focus ring shows only after keyboard input.
The look is vanilla-native (since 2026-10-17): buttons, toggles, cyclers and sliders draw the vanilla `widget/button`
and `widget/slider` sprites, text fields `widget/text_field`, lists sit on `0x70000000` between vanilla header/footer
separators, text is white with `0xFFA0A0A0` for secondary; no navy palette, cyan accent bars or cut corners.

Settings are one `SettingsView` used by the big map drawer and the Mod Menu screen (`VoxyMapConfigScreen`): category
column (or a cycler when narrow), search across all groups, a "changed" mark and per-row reset, "Reset all", and a
description line read from `<label key>.desc` when it exists. `SettingsModel` maps each `SettingsCatalog.Entry` to a
control and to the `voxymap.properties` key used for reset; `VoxyMapSettings.isDefault/reset/batch` compare against a
snapshot of the field defaults taken at class load. A new setting needs its catalog entry, its control in
`SettingsModel.control`, and its property key (`SettingsModelTest` fails otherwise).

Big map chrome: info card top-left, toolbar top-right (3D/2D, settings, server worlds, Xaero, key help, then time and
weather cyclers), compass and scale bar bottom-right, four key hints in the footer plus H for the full list. V switches
3D/2D; Tab moves keyboard focus. Players can be hidden from the big map, the minimap radar and the 3D models with the eye
in the object panel (`VoxyMapSettings.hiddenPlayers`). The waypoint form and list keep their old look on purpose.

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

1. `MapScreen#projectToScreen` clamps `screenX`/`screenY` to `±1.0E6` before rounding to `int` for points
   (markers, labels). `Math.round(double)` returns a `long`; narrowing an unclamped astronomical value to
   `int` silently wraps around instead of clamping, producing a coordinate that can drive a per-pixel fill
   loop into hundreds of millions of iterations - a full render-thread freeze, not a crash.
2. Lines never go through the clamped point projection: `draw3DLine` clips the segment against the near
   plane in world space, projects both ends with `MapProjection.projectExact` (unclamped doubles) and
   `clipLineToViewport` clips them in double precision before anything is rounded. Clamping each end
   separately bent the line (x and y clamped independently change its direction), and a clamped end could
   streak across the whole screen; clipping the exact segment keeps both the direction and the length
   bounded by the viewport.

Any new screen-space overlay must reuse `projectToScreen` for points and `draw3DLine`/`drawSegment` for lines,
not reimplement projection or clipping math, and must not remove either guard. A route overlay (since removed) is
what first surfaced both, because it drew points thousands of blocks from the player - territory
outlines and player markers stay much closer together and rarely hit the edge case.

## Deploy target

Same as the workspace default: `D:/Modrinth App/profiles/Everything Voxy/mods/`.
