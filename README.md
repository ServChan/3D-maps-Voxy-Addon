# VoxyMap LTS

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3+-DBD0B4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-E76F00)](https://adoptium.net/)
[![Client](https://img.shields.io/badge/side-client-4C8BF5)](#requirements)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

VoxyMap LTS is a maintained client-side fork of [3D Maps Voxy Addon](https://github.com/raben12paste/3D-maps-Voxy-Addon). It opens a full-screen 3D map backed by Voxy LOD data for Minecraft 26.2.

## Features

### Full-screen map

- Full-screen Voxy terrain view opened with a configurable key, `M` by default.
- Mouse dragging, right-button rotation, cursor-centered zoom, and smooth keyboard movement.
- Exact player centering plus projected markers and adaptive facing indicators for nearby active players.
- Your own view direction: a translucent wedge for your real horizontal field of view with a bright centre ray and arrowhead, so you can see where you are looking from the map.
- Stable near terrain: while the map is open, Voxy's cached LODs fill its normal player-centered hand-off zone instead of using Sodium's angle-dependent real-camera visibility set.
- Clear view: while the map is open, rain, thunder and clouds are hidden, the shader pack's fog can be removed, and the map lighting can follow the game clock or be fixed to morning, day, evening or night.
- Whole-quad Nether roof removal clears the upper bedrock/anti-xray shell without slicing decoy LODs into a checkerboard, with a zoom-aware inspection aperture that opens deeper around the current map center at close range.
- Ore masking: vanilla ores and ancient debris in Voxy's LODs are drawn plain grey, so distant terrain and the map cannot be used to spot ores.
- Searchable Objects panel for players, waypoints and VanillaSquad territories, with center/edit/delete actions and live follow mode for a selected player.
- Map screenshot (`F2` with the map open): one frame without panels, HUD and chat, optionally keeping the markers.
- Optional singleplayer pause and adjustable camera speed.
- Optional handoff to Xaero's World Map with a return button.

### Waypoints

- Double-click the actual visible Voxy surface to drop a named pin at that voxel, including caves and multi-level builds. Pick one of the palette colours in the name dialog.
- Select a pin for explicit edit/delete actions; `Ctrl+Z` undoes a deletion. Overlapping labels are de-cluttered, and every object remains available in the Objects panel.
- In the world, each waypoint within 512 blocks shows a beacon beam in its colour. Hiding waypoints in the settings hides the beams too.
- Death points: each death drops a "Death point" waypoint; the five newest are kept per world.
- Optional sync with a self-hosted server (see [Waypoint sync server](#waypoint-sync-server) below): give a world just an address to pull everyone's shared pins read-only, or an address and a password to add/edit them too. With both set, the new-waypoint dialog has a switch between "Local only" and "Shared to server"; local pins never leave your computer.

### Minimap

- Always-on minimap in the corner during normal gameplay, separate from the fullscreen map - nearby players, mobs and waypoints on top of terrain colored the same way vanilla's own map item is.
- Built entirely client-side from already-loaded chunks (no server involved at all). Outdoors it shows the top surface, like Xaero's minimap; with a solid roof over your head (caves, buildings) and always in the Nether it shows the layer at your height.
- The cached texture is rebuilt when you cross into a new chunk or switch between surface and cave view, and a few tiles are refreshed in the background every quarter second, so block changes show up within a few seconds.
- Hold `Z` (rebindable, "Enlarge minimap") to draw it 1.6 times larger. Hold the player-list key (`Tab` by default) to swap the dots for player and mob heads.
- Optional rotation with the player and a chunk grid.

### Multiplayer extras

- Map sharing: send another player your explored Voxy terrain over a one-time 6-digit code, relayed live through the sync server (nothing is stored server-side). The receiver only gets sections they don't already have - their own explored terrain is never overwritten.
- VanillaSquad: optional global player markers with compact skin heads and clustered territory outlines from flags, isolated between the public build, farm and art maps. These rows only appear while you are connected to VanillaSquad.

English, Russian, Spanish (Spain and Mexico), and Polish translations.

## Requirements

- Minecraft `26.2`.
- Java `25`.
- Fabric Loader `0.19.3` or newer.
- Fabric API.
- Voxy `0.2.18-beta` or newer for Minecraft 26.2. VoxyMap is compiled against `0.2.18-beta`. It stays on 26.2 until Voxy is released for 26.3. The Nether roof removal and ore mask shader patches target Voxy's internal shader layout; if a newer Voxy changes it, those two features switch themselves off instead of breaking the game.

Iris, Xaero's World Map and Mod Menu are optional integrations. The mod is client-only and does not need to be installed on the server.

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
| `F2` (screenshot key) | Save a screenshot of the map without the interface: for one frame every panel, the HUD and chat are hidden (and the markers too, unless the Map screenshot setting is "With markers"), then restored |
| `Esc` | Close the map |

During normal gameplay:

| Control | Action |
| --- | --- |
| `Z` (hold) | Enlarge the minimap; rebindable in Controls as "Enlarge minimap (hold)" |
| Player-list key (`Tab`, hold) | Show player and mob heads on the minimap instead of dots |

## Settings

Settings are grouped the same way in the map's **Settings** panel and in the Mod Menu screen (Mods -> VoxyMap LTS -> Configure):

| Group | Contents |
| --- | --- |
| General | Pause in singleplayer, camera speed, map lighting (auto, morning, day, evening, night), fog suppression (on, Nether only, off), map screenshot (no interface / with markers) |
| Map layers | Waypoints, death points, your view direction; VanillaSquad players / flags / map, shown only while connected to the VanillaSquad server |
| Minimap | Minimap, rotation, mobs, chunk grid |
| Sync and sharing | Sync address, password and status, share map, receive map |
| Diagnostics | Whether the Voxy shader patches loaded |

The Mod Menu screen scrolls and lists the global settings. Sync address and password and the VanillaSquad map are stored per world, so they appear there when the screen is opened from inside a world (Esc, Mods); map sharing needs the map's own area selection and is only in the map's Settings panel.

Fog suppression removes the shader pack's own fog while the map is open. It supports Complementary (Reimagined and Unbound), Rethinking Voxels, Solas, BSL, Mellow and Photon; with any other pack it does nothing. The pack must load one of the fog files listed in `ShaderPackFogPatcher`; a pack version with a changed layout is left untouched and logged once.

Settings are stored in `config/voxymap.properties`, local waypoints in `config/voxymap-waypoints.properties`, and the per-world sync address and password in `config/voxymap-sync.properties`. They write through a sibling temporary file and keep a `.bak` copy for recovery. Shared waypoints live only on the sync server.

## Build

The build targets Minecraft 26.2 and produces the distributable JAR:

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

VoxyMap LTS — поддерживаемый клиентский форк 3D Maps Voxy Addon для Minecraft 26.2. Он открывает полноэкранную трёхмерную карту на данных LOD из Voxy и добавляет миникарту в углу экрана. На сервер мод ставить не нужно.

### Требования

Minecraft 26.2, Java 25, Fabric Loader 0.19.3 или новее, Fabric API и Voxy 0.2.18-beta или новее для Minecraft 26.2. VoxyMap собран против 0.2.18-beta и останется на 26.2, пока Voxy не выйдет для 26.3. Iris, Xaero's World Map и Mod Menu необязательны.

### Полноэкранная карта

- Открывается клавишей `M`, её можно переназначить в стандартных настройках управления.
- В ближней зоне карта берёт уже сохранённые LOD Voxy, поэтому её геометрия не зависит от направления обычной камеры и не заставляет игру грузить чанки.
- Пока карта открыта, скрыты дождь, гроза и облака. Туман шейдерпака можно убрать, а освещение карты — привязать к игровому времени или зафиксировать на утре, дне, вечере или ночи.
- В Нижнем мире снимается бедроковая крыша. Убираются только целые полигоны выше порога, а не срез по плоскости, поэтому антиксрей-подделки сервера не превращаются в «шахматку» и высокие постройки под крышей остаются целыми. При сильном приближении вокруг центра карты открывается более глубокое окно.
- Ванильные руды и древние обломки в LOD Voxy закрашиваются серым, поэтому по дальней прорисовке и по карте руды не найти.
- Отмечены вы (с конусом направления взгляда по настоящему углу обзора) и загруженные игроки рядом. В плотной толпе конусы сворачиваются в короткие штрихи, а при наведении конус снова рисуется целиком.
- Панель объектов (`O`) ищет игроков, метки и территории VanillaSquad. Объект можно показать, изменить, удалить, а за игроком — следить (`F`).
- `F2` на открытой карте сохраняет скриншот без панелей, HUD и чата. Метки остаются на снимке, только если в настройках выбрано «С метками».
- Кнопка перехода в Xaero's World Map, если он установлен, и кнопка возврата обратно.

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
| `C` | Вернуться к игроку |
| `O` | Панель объектов |
| `F` | Следить за выбранным игроком; ручное движение отменяет слежение |
| `F2` | Скриншот карты без интерфейса |

Во время обычной игры: удерживайте `Z`, чтобы увеличить миникарту (клавиша «Увеличить миникарту (удерживать)» в настройках управления), и клавишу списка игроков (`Tab`), чтобы вместо точек видеть головы игроков и мобов.

### Метки

- Двойной клик по рельефу ставит именованную метку точно на видимый блок, в том числе в пещерах и на многоэтажных постройках. Цвет выбирается в окне ввода имени.
- В мире у каждой метки ближе 512 блоков светится луч маяка её цвета. Если скрыть метки в настройках, лучи тоже пропадут.
- После смерти ставится метка «Точка смерти». Для каждого мира хранятся пять последних.
- Метки хранятся отдельно для каждого мира Voxy.

### Миникарта

Работает полностью на клиенте по уже загруженным чанкам и раскрашена как ванильная карта. На поверхности показывает верхний слой, а под сплошной крышей (пещеры, постройки) и всегда в Нижнем мире — слой на высоте игрока. Текстура перестраивается при переходе в новый чанк, а в фоне несколько тайлов обновляются четыре раза в секунду, поэтому изменения блоков видны через пару секунд. Можно включить вращение вместе с игроком, мобов и сетку чанков. Моб, чья модель ещё не попадала на экран в этой сессии, остаётся точкой, пока его не увидите.

### Настройки

Группы одинаковы в панели «Настройки» на карте и в экране Mod Menu (Моды → VoxyMap LTS → Настроить):

| Группа | Что внутри |
| --- | --- |
| Общие | Пауза в одиночной игре, скорость камеры, освещение карты, подавление тумана (вкл., только Нижний мир, выкл.), скриншот карты |
| Слои карты | Метки, точки смерти, направление взгляда; игроки, флаги и карта VanillaSquad — только при подключении к VanillaSquad |
| Миникарта | Миникарта, вращение, мобы, сетка чанков |
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
