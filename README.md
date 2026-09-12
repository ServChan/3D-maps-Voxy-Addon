# VoxyMap LTS

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3+-DBD0B4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-E76F00)](https://adoptium.net/)
[![Client](https://img.shields.io/badge/side-client-4C8BF5)](#requirements)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

VoxyMap LTS is a maintained client-side fork of [3D Maps Voxy Addon](https://github.com/raben12paste/3D-maps-Voxy-Addon). It opens a full-screen 3D map backed by Voxy LOD data for Minecraft 26.2.

## Features

- Full-screen Voxy terrain view opened with a configurable key, `M` by default.
- Whole-quad Nether roof removal opens the bedrock shell without slicing anti-xray decoy LODs into a checkerboard.
- Mouse dragging, middle-button rotation, cursor-centered zoom, and smooth keyboard movement.
- Exact player centering plus projected markers and adaptive facing indicators for nearby active players.
- Optional VanillaSquad global player markers with compact skin heads and clustered territory outlines.
- Connectivity-aware room cutaway computed in bounded slices outside the render-state loop.
- Stable near terrain: while the map is open, Voxy's cached LODs fill its normal player-centered hand-off zone instead of using Sodium's angle-dependent real-camera visibility set.
- Optional singleplayer pause and adjustable camera speed.
- Optional handoff to Xaero's World Map with a return button.
- Optional HappyGhastScanner route overlay: while that mod is actively scanning, its expanding square-spiral path is drawn on the map with lap numbers, so you can tell exactly which lap to resume from without re-covering already-loaded terrain.
- English, Russian, Spanish (Spain and Mexico), and Polish translations.

## Requirements

- Minecraft `26.2`.
- Java `25`.
- Fabric Loader `0.19.3` or newer.
- Fabric API.
- Voxy `0.2.18-beta` exactly. The cutaway shader integration targets this internal shader layout.

Iris, Xaero's World Map, and HappyGhastScanner are optional integrations. The mod is client-only and does not need to be installed on the server.

## Controls

| Control | Action |
| --- | --- |
| `M` | Open or close the map; the opening binding can be changed in Controls |
| Left mouse drag | Move the map |
| Middle mouse drag | Rotate the camera |
| Mouse wheel / `+` / `-` | Zoom toward the cursor |
| `W/A/S/D` or arrow keys | Move the camera |
| `Shift` | Move faster |
| `C` | Center on the player |
| `Esc` | Close the map |

Settings are stored in `config/voxymap.properties`. Writes use a sibling temporary file and keep `voxymap.properties.bak` for recovery.

## Build

The build targets Minecraft 26.2 and produces the distributable JAR:

```powershell
.\gradlew.bat clean build --warning-mode all
```

The normal non-sources JAR is written to `build/libs/voxy-map-<version>.jar`.

## Known limitations

- Room cutaway removes only columns with a verified solid ceiling over connected client-visible air; unloaded or uncertain cells are never guessed.
- In the Nether, the global roof threshold removes complete Voxy quads wholly inside the upper roof band instead of clipping fragments at a horizontal plane. This avoids exposing artificial cross-sections through multiplayer anti-xray replacements. The incremental local mask still reveals only columns whose connected air and solid ceiling were verified from client-visible blocks.
- Active-player facing indicators collapse to short direction ticks in dense groups. Hovering a marker restores its complete cone and draws that marker last.
- VoxyMap only displays terrain already available to Voxy; it does not load chunks or reveal unexplored server data.
- Optional distant VanillaSquad markers come from that server's public web-map endpoints and are disabled by default.
- The HappyGhastScanner route overlay only draws while that mod reports an active or paused scan; it never appears if the mod is absent, idle, or finished, and it only shows a bounded window of laps around the scanner's current position, not the entire route from the start.
- Build and package checks do not replace an in-game test with the exact Voxy, Iris, Sodium, and Xaero versions in the target profile.

## Русский

VoxyMap LTS — поддерживаемый клиентский форк 3D Maps Voxy Addon для Minecraft 26.2. Он открывает полноэкранную трёхмерную карту на данных LOD из Voxy.

Карта открывается клавишей `M`; назначение можно изменить в стандартных настройках управления. ЛКМ перемещает карту, СКМ поворачивает камеру, колесо меняет масштаб, `W/A/S/D` двигают камеру, `Shift` ускоряет, `C` точно возвращает центр к игроку. На карте отображаются отдельная отметка текущего игрока и именные отметки других загруженных игроков. В ближней зоне карта использует уже кэшированные LOD Voxy, поэтому её геометрия не зависит от направления обычной камеры игрока и не вызывает дополнительную загрузку чанков. В Нижнем мире глобально скрывается только верхний слой крыши, поэтому высокие постройки под ней сохраняются целиком; локальная маска дополнительно вскрывает потолок текущего помещения. В настройках можно включить срез замкнутых помещений, удалённых игроков VanillaSquad и контуры территорий по флагам. Если установлен мод HappyGhastScanner и он сейчас активно сканирует местность, на карте появляется опция показа его маршрута — расширяющейся квадратной спирали с номерами витков, чтобы сразу понять, с какого витка продолжать облёт. Настройки сохраняются в `config/voxymap.properties`, рядом хранится резервная копия.

## Credits and license

Original project and implementation: [raben12paste](https://github.com/raben12paste). LTS maintenance and Minecraft 26.x compatibility: `LTS_Server`.

Licensed under the MIT License. See [LICENSE](LICENSE).
