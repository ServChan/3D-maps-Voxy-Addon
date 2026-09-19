# VoxyMap LTS

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3+-DBD0B4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-E76F00)](https://adoptium.net/)
[![Client](https://img.shields.io/badge/side-client-4C8BF5)](#requirements)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

VoxyMap LTS is a maintained client-side fork of [3D Maps Voxy Addon](https://github.com/raben12paste/3D-maps-Voxy-Addon). It opens a full-screen 3D map backed by Voxy LOD data for Minecraft 26.2.

## Features

- Full-screen Voxy terrain view opened with a configurable key, `M` by default.
- Whole-quad Nether roof removal clears the upper bedrock/anti-xray shell without slicing decoy LODs into a checkerboard, with a zoom-aware inspection aperture that opens deeper around the current map center at close range.
- Mouse dragging, right-button rotation, cursor-centered zoom, and smooth keyboard movement.
- Exact player centering plus projected markers and adaptive facing indicators for nearby active players.
- Optional VanillaSquad global player markers with compact skin heads and clustered territory outlines, isolated between the public build, farm, and art maps.
- Stable near terrain: while the map is open, Voxy's cached LODs fill its normal player-centered hand-off zone instead of using Sodium's angle-dependent real-camera visibility set.
- Optional singleplayer pause and adjustable camera speed.
- Optional handoff to Xaero's World Map with a return button.
- Waypoints: double-click the actual visible Voxy surface to drop a named pin at that voxel, including caves and multi-level builds. Select a pin for explicit edit/delete actions; overlapping labels are de-cluttered and every object remains available in the searchable Objects panel.
- Searchable Objects panel for players, waypoints and VanillaSquad territories, with center/edit/delete actions and live follow mode for a selected player.
- Optional waypoint sync with a self-hosted server (see [Waypoint sync server](#waypoint-sync-server) below): give a world just an address to pull everyone's pins read-only, or an address and a password to add/edit them too.
- Map sharing: send another player your explored Voxy terrain over a one-time 6-digit code, relayed live through the same sync server (nothing is stored server-side). The receiver only gets sections they don't already have - their own explored terrain is never overwritten.
- Always-on minimap in the corner during normal gameplay, separate from the fullscreen map - nearby players and waypoints on top of terrain colored the same way vanilla's own map item is. Built entirely client-side from already-loaded chunks (no server involved at all): a cached texture only rebuilds when you cross into a new chunk, not every frame.
- English, Russian, Spanish (Spain and Mexico), and Polish translations.

## Requirements

- Minecraft `26.2`.
- Java `25`.
- Fabric Loader `0.19.3` or newer.
- Fabric API.
- Voxy `0.2.18-beta` exactly. The Nether roof removal and ore mask shader patches target this internal shader layout.

Iris and Xaero's World Map are optional integrations. The mod is client-only and does not need to be installed on the server.

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
| `F2` (screenshot key) | Save a screenshot of the bare map: every panel, marker, the HUD and chat are hidden for one frame, then restored |
| `Esc` | Close the map |

## Settings

Settings are grouped the same way in the map's **Settings** panel and in the Mod Menu screen (Mods -> VoxyMap LTS -> Configure):

| Group | Contents |
| --- | --- |
| General | Pause in singleplayer, camera speed, map lighting (auto, morning, day, evening, night), fog suppression (on, Nether only, off) |
| Map layers | Waypoints, death points, VanillaSquad players / flags / map |
| Minimap | Minimap, rotation, mobs, chunk grid |
| Sync and sharing | Sync address, password and status, share map, receive map |
| Diagnostics | Whether the Voxy shader patches loaded |

The Mod Menu screen scrolls and lists the global settings. Sync, map sharing and the VanillaSquad map are stored per world, so they are only in the map's Settings panel.

Settings are stored in `config/voxymap.properties`, and waypoints in `config/voxymap-waypoints.properties`. Both write through a sibling temporary file and keep a `.bak` copy for recovery.

## Build

The build targets Minecraft 26.2 and produces the distributable JAR:

```powershell
.\gradlew.bat clean build --warning-mode all
```

The normal non-sources JAR is written to `build/libs/voxy-map-<version>.jar`.

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

In VoxyMap's map Settings panel, set **Sync address** to `host:port` for a world to pull that world's waypoints from the server read-only; also set **Sync password** to match the server's `--password` to add, rename, and delete through it too. There's exactly one password for the whole server - not a per-player account system - so "knows the address" and "knows the address and the password" are the only two access levels.

Traffic is plain HTTP with no encryption, by design: this is meant for a trusted LAN or a small friend group's own server, not a public deployment. It binds only to localhost by default. Put a TLS-terminating reverse proxy with request-rate limits in front of it if it needs to be reachable over the open internet. The built-in server bounds waypoint request bodies, worker concurrency and relay-code claim attempts, but it is not an internet-facing identity or authorization service.

### Map sharing

Once a **Sync address** is set for a world (a password isn't needed for this - the relay itself needs no password), the Settings panel gets two more rows:

- **Share map** - first asks for two opposite corners on the 3D map, then serves only explored Voxy sections intersecting that rectangle under a fresh 6-digit code. The selection always includes the complete vertical column, so caves and builds inside it are not cut away. Leave the map open; the code keeps working for as many people as connect until you click the row again to stop, disconnect, or join a different world (sharing is tied to the specific world it was started for - it never keeps running unattended after you've left it).
- **Get map** - prompts for a code someone shared with you, claims it, and shows an estimated size ("~N MB - click to accept, right-click to decline") before anything is downloaded. Confirming starts the actual import; declining frees the code immediately.

Not a single byte of real terrain data moves until the receiver has seen the size estimate and explicitly confirmed - the relay's handshake (register → claim → confirm/decline → data connection) keeps the sender's export from starting at all until then, so there's nothing to see or intercept without an explicit yes from the person receiving it. The relay server only pipes bytes between the two of you once that happens - it never stores or inspects the terrain data. Import only ever fills in sections you haven't explored yet; it never overwrites what you already have. Voxy assigns each block/biome type a small per-world-database id in the order it's first encountered, so a naive byte-for-byte copy between two independently-explored copies of the same server could silently show the wrong blocks - VoxyMap avoids this by translating every voxel through Voxy's own id-mapping system on import, registering any block/biome type your world hasn't seen yet exactly as normal exploration would.

## Known limitations

- In the Nether, the global roof threshold removes complete Voxy quads wholly inside the upper roof band instead of clipping fragments at a horizontal plane. This avoids exposing artificial cross-sections through multiplayer anti-xray replacements.
- The Nether inspection aperture follows the panned map center. At close zoom it lowers the whole-quad threshold aggressively enough to expose player-height terrain; by zoom `1.00` it blends back to the architectural-preserving global threshold. Its radius is derived from the visible world footprint and fades into the surrounding map instead of producing a fixed rectangular hole.
- Active-player facing indicators collapse to short direction ticks in dense groups. Hovering a marker restores its complete cone and draws that marker last.
- VoxyMap only displays terrain already available to Voxy; it does not load chunks or reveal unexplored server data.
- Optional distant VanillaSquad markers come from that server's public web-map endpoints and are disabled by default. Auto mode resolves build/farm/art from the local player or another loaded player. Because hidden players are absent from squaremap, an unresolved session keeps the safe build fallback instead of blanking both overlays; an explicit source selection is stored independently for the current Voxy world.
- Map sharing writes directly into Voxy's live in-memory world storage (through its own public ingestion path, not a private/undocumented one) - it's the newest and least battle-tested part of VoxyMap. Try it on a world you can afford to be wrong about before trusting it on one with irreplaceable exploration progress.
- The minimap only shows chunks already loaded by the normal game, same as vanilla's own map item - it never reveals unexplored terrain, and a tile computed once won't reflect edits made to that chunk afterward until the client restarts.
- Build and package checks do not replace an in-game test with the exact Voxy, Iris, Sodium, and Xaero versions in the target profile.

## Русский

VoxyMap LTS — поддерживаемый клиентский форк 3D Maps Voxy Addon для Minecraft 26.2. Он открывает полноэкранную трёхмерную карту на данных LOD из Voxy.

Карта открывается клавишей `M`; назначение можно изменить в стандартных настройках управления. ЛКМ перемещает карту, СКМ поворачивает камеру, колесо меняет масштаб, `W/A/S/D` двигают камеру, `Shift` ускоряет, `C` точно возвращает центр к игроку. На карте отображаются отдельная отметка текущего игрока и именные отметки других загруженных игроков. В ближней зоне карта использует уже кэшированные LOD Voxy, поэтому её геометрия не зависит от направления обычной камеры игрока и не вызывает дополнительную загрузку чанков. В Нижнем мире скрывается только верхний слой крыши, поэтому высокие постройки под ней сохраняются целиком. В настройках можно включить срез замкнутых помещений, удалённых игроков VanillaSquad и контуры территорий по флагам. Режим «Авто» различает карты построек, ферм и артов по текущему или другому загруженному игроку; если скрытый аккаунт отсутствует в публичном API, слой не исчезает, а источник можно назначить вручную. Этот выбор хранится отдельно для текущего Voxy-мира и не переносится на остальные. Правый клик по карте ставит именованную метку в этой точке; правый клик по уже существующей метке удаляет её; левый клик по метке позволяет её переименовать. Метки хранятся отдельно для каждого мира Voxy и могут быть скрыты в настройках. Метки можно синхронизировать через отдельный сервер `voxymap-sync-server` (собирается командой `gradlew syncServerJar`, запускается как `java -jar voxymap-sync-server-<версия>.jar --port 8642 --password пароль`): если в настройках карты указан только адрес — метки читаются с сервера; если указаны адрес и пароль, совпадающий с серверным, — метки можно ещё и добавлять, переименовывать и удалять. Пароль на сервере один общий, а не отдельный для каждого игрока. Трафик передаётся обычным HTTP без шифрования — сервер рассчитан на локальную сеть или доверенную компанию друзей, а не на публичный доступ из интернета. Если для мира задан адрес синхронизации, в настройках появляются ещё две строки: «Поделиться картой» — запускает раздачу вашей исследованной территории под одноразовым 6-значным кодом (работает, пока не нажать ещё раз, и может обслужить нескольких получателей подряд; раздача автоматически останавливается при выходе из мира или переходе в другой — она никогда не продолжает работать в фоне без вашего ведома); «Получить карту» — запрашивает код, после чего показывает примерный размер («~N МБ — ЛКМ принять, ПКМ отклонить») и ждёт явного подтверждения, прежде чем начать скачивание. Ни один байт реальных данных не передаётся, пока получатель не увидит размер и не подтвердит его — рукопожатие релея (регистрация → подключение получателя → подтверждение/отказ → соединение с данными) не даёт отправителю даже начать экспорт до этого момента. Сервер синхронизации только передаёт байты между двумя игроками, когда это уже произошло, и ничего не сохраняет. К себе добавляются только те участки, которых ещё нет — уже исследованное никогда не перезаписывается. Voxy присваивает каждому типу блока и биома свой номер отдельно для каждой базы мира, в порядке первого обнаружения, поэтому прямое копирование байтов между независимо исследованными копиями одного и того же сервера могло бы показать не те блоки — при получении карты VoxyMap переводит каждый воксель через собственную систему сопоставления id Voxy, регистрируя новые для этого мира блоки и биомы точно так же, как это происходит при обычном исследовании. Эта часть работает через внутреннее (хоть и открытое, официальное) API Voxy напрямую с загруженным миром, поэтому первый раз стоит попробовать на мире, который не жалко, а не на том, где исследование накапливалось долго. Отдельно от полноэкранной карты есть всегда включённая миникарта в углу экрана во время обычной игры — показывает соседей и метки поверх рельефа, раскрашенного так же, как обычная бумажная карта. Работает полностью на клиенте и никак не связана с сервером синхронизации: текстура миникарты пересчитывается только при переходе в новый чанк, а не каждый кадр, поэтому нагрузка минимальна. Миникарту можно выключить строкой «Миникарта» в настройках. Настройки сохраняются в `config/voxymap.properties`, метки — в `config/voxymap-waypoints.properties`, адрес и пароль синхронизации — в `config/voxymap-sync.properties`; рядом с каждым файлом хранится резервная копия.

## Credits and license

Original project and implementation: [raben12paste](https://github.com/raben12paste). LTS maintenance and Minecraft 26.x compatibility: `LTS_Server`.

Licensed under the MIT License. See [LICENSE](LICENSE).
