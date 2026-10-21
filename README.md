# VoxyMap LTS

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5+-DBD0B4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-E76F00)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/version-1.0.8--LTS.01-4C8BF5)](gradle.properties)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

VoxyMap LTS is a maintained client-side fork of [3D Maps Voxy Addon](https://github.com/raben12paste/3D-maps-Voxy-Addon). It opens a full-screen 3D map backed by Voxy LOD data for Minecraft 26.3.

## Features

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

## Requirements

- Minecraft `26.3`.
- Java `25`.
- Fabric Loader `0.19.5` or newer.
- Fabric API.
- Voxy `0.2.20-beta` or newer for Minecraft 26.3. No Voxy for 26.3 is published yet; VoxyMap is compiled against a build of upstream Voxy's `263` branch (`tools/deps/voxy-0.2.20-beta-26.3.jar`). Upstream marks that branch as not release-ready and warns that it can corrupt Voxy worlds saved by older versions, so back up `.minecraft/.voxy` first. It also refuses Minecraft's Vulkan backend, so the game has to run on OpenGL. The Nether roof removal and ore mask shader patches target Voxy's internal shader layout; if a newer Voxy changes it, those two features switch themselves off instead of breaking the game.

Iris, Xaero's World Map, Litematica Plus LTS, Spatial GUI and Mod Menu are optional integrations. With Spatial GUI, M opens a map window that Spatial GUI shows as its 3D panel: an experimental 3D view rendered by Voxy (WASD, drag, right-drag to rotate, wheel to zoom) or a flat top-down view of loaded chunks, plus a button to the full 3D map. Both settings appear only when Spatial GUI is installed. The 3D view does not work with an Iris shader pack and falls back to the flat view. The mod is client-only and does not need to be installed on the server.

## Controls

| Control | Action |
| --- | --- |
| `M` | Open or close the map; the opening binding can be changed in Controls |
| Left mouse drag | Move the map |
| Double left click terrain | Add a waypoint on the exact visible Voxy surface |
| Left click a waypoint | Select it; click again or press `Enter` to rename |
| `Delete` / `Ctrl+Z` | Delete the selected waypoint / undo deletion |
| Right mouse drag | Rotate and tilt the camera |
| Mouse wheel / `+` / `-` | Zoom toward the cursor |
| `W/A/S/D` or arrow keys | Move the camera |
| `Shift` | Move faster |
| `C` | Center on the player |
| `O` | Open or close the Objects panel |
| `F` | Follow or stop following the selected player; manual movement stops following |
| `V` | Switch between the 3D and the 2D map |
| `G` | Go to typed coordinates |
| `N` (add-waypoint key) | Add a waypoint under the cursor |
| Right click | Context menu: waypoint, temporary waypoint, copy coordinates, teleport |
| `H` | Keyboard shortcuts overlay |
| `F2` (screenshot key) | Save a screenshot of the map without the interface: for one frame every panel, the HUD and chat are hidden (and the markers too, unless the Map screenshot setting is "With markers"), then restored |
| `Esc` | Close the map |

During normal gameplay:

| Control | Action |
| --- | --- |
| `Z` (hold) | Enlarge the minimap; rebindable in Controls as "Enlarge minimap (hold)" |
| `=` / `-` | Minimap zoom in / out |
| `N` | Add a waypoint where you stand |
| `U` | Waypoint list |
| unbound | Temporary waypoint where you look |
| Player-list key (`Tab`, hold) | Show player and mob heads on the minimap when the radar icons setting is "Heads while Tab is held" |

## Settings

Settings are grouped the same way in the map's **Settings** panel and in the Mod Menu screen (Mods -> VoxyMap LTS -> Configure). Both have a search field, categories, a hint for the setting under the cursor, a reset button on every row and Reset all:

| Group | Contents |
| --- | --- |
| General | Pause in singleplayer, camera speed, map lighting (auto, morning, day, evening, night), map weather (clear, rain, thunder, auto), Voxy LODs outside the map, interface animations, fog suppression (on, Nether only, off), map screenshot (no interface / with markers); with Spatial GUI installed: M opens the map window, 3D view in that window |
| Map layers | Waypoints, Overworld waypoints in the Nether and back, death points, your view direction, models on the 3D map; VanillaSquad players / flags / map, shown only while connected to the VanillaSquad server; schematics, shown only with Litematica Plus LTS installed |
| Waypoint look | Where new waypoints show, icon shape and size, waypoints in the world (off, icons, name when aimed at, always), label background and size, distance, range, beacon beams |
| Minimap | Minimap, rotation, colours, shape, size, corner, zoom, position, opacity, darker at night, cave view, with a screen open, chunk grid |
| Under the minimap | Coordinates, Nether / Overworld coordinates, biome, light level, day and time, chunk, facing |
| Minimap radar | Hostile, neutral, passive mobs, dropped items, players, icons |
| Map overlays | Slime chunks, where mobs can spawn, biome colours |
| Sync and sharing | Sync address, password and status, share map, receive map |
| Diagnostics | Whether the Voxy shader patches loaded |

The Mod Menu screen scrolls and lists the global settings. Sync address and password and the VanillaSquad map are stored per world, so they appear there when the screen is opened from inside a world (Esc, Mods); map sharing needs the map's own area selection and is only in the map's Settings panel.

Fog suppression removes the shader pack's own fog while the map is open. It supports Complementary (Reimagined and Unbound), Rethinking Voxels, Solas, BSL, Mellow and Photon; with any other pack it does nothing. The pack must load one of the fog files listed in `ShaderPackFogPatcher`; a pack version with a changed layout is left untouched and logged once.

Settings are stored in `config/voxymap.properties`, local waypoints in `config/voxymap-waypoints.properties`, and the per-world sync address and password in `config/voxymap-sync.properties`. They write through a sibling temporary file and keep a `.bak` copy for recovery. Shared waypoints live only on the sync server.

## Build

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

Before publishing, complete the [runtime regression checklist](docs/runtime-regression-checklist.md).
It covers shader, Nether, crowded-player, minimap and two-client paths that unit tests cannot prove.

## Waypoint sync server

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

## Known limitations

- In the Nether, the global roof threshold removes complete Voxy quads wholly inside the upper roof band instead of clipping fragments at a horizontal plane. This avoids exposing artificial cross-sections through multiplayer anti-xray replacements.
- The Nether inspection aperture follows the panned map center. At close zoom it lowers the whole-quad threshold aggressively enough to expose player-height terrain; by zoom `1.00` it blends back to the architectural-preserving global threshold. Its radius is derived from the visible world footprint and fades into the surrounding map instead of producing a fixed rectangular hole.
- Active-player facing indicators collapse to short direction ticks in dense groups. Hovering a marker restores its complete cone and draws that marker last.
- VoxyMap only displays terrain already available to Voxy; it does not load chunks or reveal unexplored server data.
- Optional distant VanillaSquad markers come from that server's public web-map endpoints and are disabled by default. Auto mode resolves build/farm/art from the local player or another loaded player. Because hidden players are absent from squaremap, an unresolved session keeps the safe build fallback instead of blanking both overlays; an explicit source selection is stored independently for the current Voxy world.
- Map sharing writes directly into Voxy's live in-memory world storage (through its own public ingestion path, not a private/undocumented one) - it's the newest and least battle-tested part of VoxyMap. Try it on a world you can afford to be wrong about before trusting it on one with irreplaceable exploration progress.
- The minimap only shows chunks already loaded by the normal game, same as vanilla's own map item - it never reveals unexplored terrain. A mob whose face has not been rendered yet this session (its model variant was never on screen) keeps a dot while Tab is held, until it is seen.
- Build and package checks do not replace an in-game test with the exact Voxy, Iris, Sodium, and Xaero versions in the target profile.

## Русский

VoxyMap LTS — поддерживаемый клиентский форк 3D Maps Voxy Addon для Minecraft 26.3. Он открывает полноэкранную трёхмерную карту на данных LOD из Voxy и добавляет миникарту в углу экрана. На сервер мод ставить не нужно.

### Требования

Minecraft 26.3, Java 25, Fabric Loader 0.19.5 или новее, Fabric API и Voxy 0.2.20-beta или новее для Minecraft 26.3. Опубликованного Voxy под 26.3 пока нет: VoxyMap собран против сборки ветки `263` из upstream Voxy (`tools/deps/voxy-0.2.20-beta-26.3.jar`). Автор Voxy помечает её как незаконченную и предупреждает, что она может испортить миры Voxy, сохранённые старыми версиями, поэтому сначала сделайте копию `.minecraft/.voxy`. Эта сборка не запускается на Vulkan-бэкенде, игра должна работать на OpenGL. Iris, Xaero's World Map, Litematica Plus LTS, Spatial GUI и Mod Menu необязательны. Со Spatial GUI клавиша M открывает окно карты, которое Spatial GUI показывает своей 3D-панелью: экспериментальный 3D-вид от Voxy (WASD, перетаскивание, поворот правой кнопкой, колесо — зум) или плоский вид сверху по загруженным чанкам, и кнопка перехода к полной 3D-карте. Обе настройки видны только при установленном Spatial GUI. С шейдерпаком Iris 3D-вид не работает и окно показывает плоский вид.

### Полноэкранная карта

- Открывается клавишей `M`, её можно переназначить в стандартных настройках управления.
- В ближней зоне карта берёт уже сохранённые LOD Voxy, поэтому её геометрия не зависит от направления обычной камеры и не заставляет игру грузить чанки.
- 2D-карта: `V` или кнопки 3D/2D на панели переключают на плоскую карту севером вверх из секций LOD Voxy. Тайлы кешируются в PNG для каждого мира и среза и пересканируются после изменения блоков; метки, игроки и слои работают в обоих видах.
- Погода и время на карте: по умолчанию дождь и гроза скрыты, но можно показывать всегда дождь, всегда грозу или настоящую погоду (дождь и снег по биому); освещение карты привязано к игровому времени или зафиксировано на утре, дне, вечере или ночи. Облака скрыты, туман шейдерпака можно убрать. В самом мире погода не меняется.
- Игроки и мобы на 3D-карте рисуются настоящими моделями, увеличенными с расстоянием (не больше чем в 8 раз), и скрываются, если между ними и камерой карты ничего их не связывает. Настройка «Модели на 3D-карте»: игроки и мобы, только игроки или выкл.
- Панель инструментов, компас, линейка масштаба, карточка выбранного объекта и подсказка по клавишам (`H`). `G` переносит камеру к введённым координатам; включаются сетка чанков и слои слайм-чанков, освещённости для спавна мобов и биомов.
- Меню по ПКМ на карте: поставить метку или временную метку, скопировать координаты, телепортироваться (нужны права оператора).
- Voxy вне карты можно выключить («LOD Voxy вне карты»): тогда Voxy рисует дальний рельеф только на открытой карте.
- В Нижнем мире снимается бедроковая крыша. Убираются только целые полигоны выше порога, а не срез по плоскости, поэтому антиксрей-подделки сервера не превращаются в «шахматку» и высокие постройки под крышей остаются целыми. При сильном приближении вокруг центра карты открывается более глубокое окно.
- Ванильные руды и древние обломки в LOD Voxy закрашиваются серым, поэтому по дальней прорисовке и по карте руды не найти.
- Отмечены вы (с конусом направления взгляда по настоящему углу обзора) и загруженные игроки рядом. В плотной толпе конусы сворачиваются в короткие штрихи, а при наведении конус снова рисуется целиком.
- Панель объектов (`O`) ищет игроков, метки и территории VanillaSquad. Объект можно показать, изменить, удалить, а за игроком — следить (`F`). Игроков можно скрыть с карты.
- Миры VanillaSquad: на сервере VanillaSquad кнопка «Миры сервера» открывает плоскую веб-карту squaremap мира build, farm или art, даже если вы там не были; переключение кнопками или клавишами `1`–`9`. Тайлы грузятся, только пока открыт этот экран.
- `F2` на открытой карте сохраняет скриншот без панелей, HUD и чата. Метки остаются на снимке, только если в настройках выбрано «С метками».
- Кнопка перехода в Xaero's World Map, если он установлен, и кнопка возврата обратно.
- Если установлен Litematica Plus LTS, размещения схем видны на карте рамкой с названием и процентом готовности, в панели «Объекты» (фильтр «Схемы») и контуром на миникарте. Клик по подписи выбирает размещение, повторный клик или «Править» открывает его редактор, «Центр» переносит камеру карты к нему. Слой «Схемы (Litematica Plus)» в настройках прячет всё это вместе с блоками-призраками, которые Litematica Plus рисует на карте. Litematica Plus также читает через этот мод блоки, сохранённые Voxy, чтобы сверять части схемы в непрогруженных чанках.

### Управление на карте

| Клавиша | Действие |
| --- | --- |
| `M` / `Esc` | Закрыть карту |
| ЛКМ, перетаскивание | Двигать карту |
| Двойной ЛКМ по рельефу | Поставить метку на видимую поверхность |
| ЛКМ по метке | Выбрать; повторный клик или `Enter` — переименовать |
| `Delete` / `Ctrl+Z` | Удалить выбранную метку / вернуть удалённую |
| ПКМ, перетаскивание | Поворот и наклон камеры |
| Колесо / `+` / `-` | Масштаб к курсору |
| `W/A/S/D` или стрелки | Двигать камеру, `Shift` — быстрее |
| `V` | Переключить 3D- и 2D-карту |
| `G` | Перейти к введённым координатам |
| `N` (клавиша метки) | Поставить метку под курсором |
| ПКМ, клик | Меню: метка, временная метка, копировать координаты, телепорт |
| `H` | Подсказка по клавишам |
| `C` | Вернуться к игроку |
| `O` | Панель объектов |
| `F` | Следить за выбранным игроком; ручное движение отменяет слежение |
| `F2` | Скриншот карты без интерфейса |

Во время обычной игры: удерживайте `Z`, чтобы увеличить миникарту (клавиша «Увеличить миникарту (удерживать)» в настройках управления), `=` и `-` меняют масштаб миникарты, `N` ставит метку на месте игрока, `U` открывает список меток, а клавиша «Временная метка туда, куда смотрю» (по умолчанию не назначена) ставит временную метку, которая исчезает, когда до неё дошёл. Головы игроков и мобов на миникарте по умолчанию видны всегда; в настройках их можно показывать только при зажатом `Tab` или заменить точками.

### Метки

- Двойной клик по рельефу (или клавиша метки `N`) ставит именованную метку точно на видимый блок под курсором, в том числе в пещерах и на многоэтажных постройках. Цвет и символ из 1–2 букв выбираются в форме метки; Enter сохраняет, Esc отменяет.
- Для каждой метки выбирается, где её показывать: везде, только на картах, только на большой карте или нигде (она остаётся в списке `U`); для новых меток есть своя настройка. Вид меток: значок квадратом, кругом или ромбом, четыре размера, фон подписи сплошной, прозрачный или без фона, размер подписи 75–200 %, строка с расстоянием и дальность, на которой метки плавно исчезают.
- Метки Верхнего мира видны в Незере и наоборот (координаты пересчитываются в 8 раз). Меткой можно поделиться в чате в формате Xaero со ссылкой [Добавить], метки Xaero's Minimap и JourneyMap импортируются кнопкой в списке `U`.
- В мире у каждой метки ближе 512 блоков светится луч маяка её цвета. Если скрыть метки в настройках, лучи тоже пропадут.
- После смерти ставится метка «Точка смерти». Для каждого мира хранятся пять последних.
- Метки хранятся отдельно для каждого мира Voxy.

### Миникарта

Работает полностью на клиенте по уже загруженным чанкам и раскрашена по текстурам блоков (или как ванильная карта). Квадратная или круглая, четыре размера, любой угол экрана или своё место перетаскиванием, прозрачность, затемнение ночью, режим пещер (автоматически, никогда, всегда); прячется на F1 и, по выбору, за инвентарём или любым экраном. Под миникартой — строки на выбор: координаты, координаты Незера и Верхнего мира, биом, освещённость, время, чанк, направление. Радар показывает враждебных, нейтральных и мирных мобов, предметы и игроков отдельными категориями; слои слайм-чанков (на сервере нужен сид: `/voxymap seed <сид>`), мест спавна мобов и биомов. На поверхности показывает верхний слой, а под сплошной крышей (пещеры, постройки) и всегда в Нижнем мире — слой на высоте игрока. Текстура перестраивается при переходе в новый чанк, а в фоне несколько тайлов обновляются четыре раза в секунду, поэтому изменения блоков видны через пару секунд. Можно включить вращение вместе с игроком, мобов и сетку чанков. Моб, чья модель ещё не попадала на экран в этой сессии, остаётся точкой, пока его не увидите.

### Настройки

Группы одинаковы в панели «Настройки» на карте и в экране Mod Menu (Моды → VoxyMap LTS → Настроить). В обоих есть поиск, категории, подсказка к настройке под курсором, сброс у каждой строки и «Сбросить всё»:

| Группа | Что внутри |
| --- | --- |
| Общие | Пауза в одиночной игре, скорость камеры, освещение карты, погода на карте, LOD Voxy вне карты, анимации интерфейса, подавление тумана (вкл., только Нижний мир, выкл.), скриншот карты; при установленном Spatial GUI: M открывает окно карты, 3D-вид в этом окне |
| Слои карты | Метки, метки Верхнего мира в Незере и обратно, точки смерти, направление взгляда, модели на 3D-карте; игроки, флаги и карта VanillaSquad — только при подключении к VanillaSquad; схемы — только при установленном Litematica Plus LTS |
| Вид меток | Где показывать новые метки, форма и размер значка, метки в мире, фон и размер подписи, расстояние, дальность, лучи маяка |
| Миникарта | Миникарта, вращение, цвета, форма, размер, угол, масштаб, положение, прозрачность, затемнение ночью, режим пещер, при открытом экране, сетка чанков |
| Под миникартой | Координаты, координаты Незера / Верхнего мира, биом, освещённость, время, чанк, направление |
| Радар миникарты | Враждебные, нейтральные, мирные мобы, предметы, игроки, значки |
| Наложения на карту | Слайм-чанки, места спавна мобов, цвета биомов |
| Синхронизация и обмен | Адрес, пароль и статус синхронизации, «Поделиться картой», «Получить карту» |
| Диагностика | Загрузились ли патчи шейдеров Voxy |

Адрес и пароль синхронизации и карта VanillaSquad хранятся для каждого мира отдельно, поэтому в экране Mod Menu они видны, только если открыть его из мира (Esc → Моды). Обмен картой есть только в панели настроек на самой карте.

Подавление тумана поддерживает Complementary (Reimagined и Unbound), Rethinking Voxels, Solas, BSL, Mellow и Photon. С другими шейдерпаками, а также с версией пака, где изменились файлы тумана, оно ничего не делает.

Файлы: настройки — `config/voxymap.properties`, локальные метки — `config/voxymap-waypoints.properties`, адрес и пароль синхронизации — `config/voxymap-sync.properties`. Запись идёт через временный файл, рядом лежит резервная копия `.bak`. Общие метки хранятся только на сервере синхронизации.

### Сервер синхронизации и обмен картой

`voxymap-sync-server` — отдельная программа без Minecraft и зависимостей. Собирается командой `gradlew syncServerJar`, запускается так:

```powershell
java -jar build\libs\voxymap-sync-server-<версия>.jar --bind 0.0.0.0 --port 8642 --password пароль
```

Параметры: `--bind` (по умолчанию `127.0.0.1`), `--port` (по умолчанию `8642`), `--password` (без него сервер только для чтения), `--data` (по умолчанию `./voxymap-sync-data`).

- Если для мира указан только адрес, общие метки читаются с сервера. Если указаны адрес и пароль, совпадающий с серверным, их можно добавлять, переименовывать и удалять, а в окне новой метки появляется переключатель «Только локально» / «Шарится на сервер». Локальные метки никуда не отправляются.
- Пароль на сервере один на всех, а не отдельный для каждого игрока. Трафик идёт по HTTP без шифрования: сервер рассчитан на локальную сеть или доверенную компанию, а не на открытый интернет.
- «Поделиться картой» сначала просит отметить два угла области, затем раздаёт исследованные участки этой области (на всю высоту) под одноразовым 6-значным кодом. Раздача работает, пока карта открыта, пока вы не нажмёте строку ещё раз или не выйдете из мира.
- «Получить карту» просит код и показывает примерный размер («~N МБ — ЛКМ принять, ПКМ отклонить»). Ни один байт данных не передаётся, пока получатель не подтвердит. Сервер только передаёт байты между игроками и ничего не хранит.
- Добавляются только участки, которых у получателя ещё нет; исследованное никогда не перезаписывается. Каждый воксель переводится через собственную таблицу id Voxy, поэтому блоки не путаются между независимо исследованными копиями мира.
- Обмен картой пишет прямо в загруженный мир Voxy и проверен меньше всего остального. Сначала попробуйте его на мире, который не жалко.

### VanillaSquad

Игроки с веб-карты VanillaSquad и контуры территорий по флагам по умолчанию выключены и показываются только при подключении к VanillaSquad. Режим «Авто» определяет карту построек, ферм или артов по вашему или другому загруженному игроку. Если скрытого аккаунта нет в публичном API, слой не пропадает, а остаётся карта построек; источник можно выбрать вручную, и этот выбор хранится отдельно для каждого мира Voxy.

### Сборка

`gradlew build` создаёт два jar, ставить нужно только один:

- `voxy-map-<версия>.jar` — основная версия, без меток игроков с веб-карты VanillaSquad. Кода загрузки их позиций в нём нет, строка настройки не показывается, а «Авто» выбирает карту построек. Флаги территорий работают;
- `voxy-map-<версия>-player-markers.jar` — то же самое плюс метки игроков с веб-карты VanillaSquad.

### Ограничения

- Карта показывает только то, что уже есть у Voxy: чанки не загружаются, неисследованное не раскрывается. Миникарта так же видит только загруженные чанки.
- Если новая версия Voxy изменит устройство своих шейдеров, снятие крыши Нижнего мира и маска руд отключатся сами, а игра продолжит работать. Состояние видно в группе «Диагностика».
- Сборка и тесты не заменяют проверку в игре с теми же версиями Voxy, Iris, Sodium и Xaero, что в целевом профиле.

## Credits and license

Original project and implementation: [raben12paste](https://github.com/raben12paste). LTS maintenance and Minecraft 26.x compatibility: `LTS_Server`.

Licensed under the MIT License. See [LICENSE](LICENSE).
