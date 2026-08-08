# CDDA

[中文文档](README_zh.md)

A lightweight Java 17 Swing-based 2D character-mode game engine for card/strategy/survival games.

## ✨ Features

- **Turn-based time system** — Time advances only on player actions; energy-based scheduling supports multiple entities (NPC/enemy ready)
- **Full calendar system** — Year/month/day/hour/minute, four seasons, European climate reference
- **Three-layer environment temperature** — Monthly average + sinusoidal daily fluctuation + smooth random drift
- **Human metabolism simulation** — Energy pool (calories), thermoregulation (buffer mechanism), basal metabolism + environmental compensation
- **Thirst/hydration system** — Base drain + temperature multiplier + action multiplier, coupled with thermoregulation
- **Infinite chunk map** — Perlin noise terrain generation, load/unload on demand, moddable tile types
- **Region system** — Logical chunk grouping, large-scale map rendering interface reserved

## 🏗️ Architecture

The project is split into two independent top-level packages: **Engine Layer** and **Game Layer**.

```
com.github.game.engine.core    ← Generic engine (game-agnostic)
com.github.game.cdda           ← CDDA game code
```

### Engine Layer (`engine.core`)

| Module | Core Classes | Responsibility |
|--------|-------------|----------------|
| Main Loop | `GameEngine`, `GamePanel` | 30 FPS Swing Timer, EDT-driven |
| Rendering | `Renderer`, `Graphics2DRenderer` | Rendering abstraction + Swing implementation, swappable backend |
| Screen | `Screen`, `ScreenManager` | State pattern, supports switch/push/pop |
| Scene | `Scene`, `Viewport` | Scene composition, split-screen layout |
| Input | `InputManager` | AWT event forwarding, screens receive clean coordinates |
| Resource | `ResourceManager` | Image loading & caching, classpath/external file support |
| Clock | `GameClock` | Generic game clock (totalSeconds + hour/minute/second) |

### Game Layer (`cdda`)

| Module | Core Classes | Responsibility |
|--------|-------------|----------------|
| World | `GameWorld` | Logic layer, creates and owns all subsystems |
| Calendar | `GameCalendar`, `Month`, `Season` | Extends GameClock, 30-day months, 12-month years, four seasons |
| Turn | `TurnManager` | Energy-based action scheduling (speed → action duration) |
| Temperature | `TemperatureManager` | Three-layer environment temperature model |
| Metabolism | `MetabolismManager` | Energy pool, thermoregulation, hunger |
| Thirst | `HydrationManager` | Water balance, temperature/action multipliers |
| Map | `TileMap`, `TileType`, `Chunk`, `Region` | Chunk-based map + Perlin noise terrain |
| Entity | `Entity`, `Player` | Turn-based entity base classes |
| Item | `ItemType`, `ItemStack`, `ItemRegistry` | Item system with mod support |
| Screen | `MainScreen`, `GameScene`, `HudScene` | Main screen + split-screen scenes |

### Data Flow

```
Input → GamePanel → InputManager → Screen → GameScene
                                         ↓
                              GameWorld (all game state)
                                         ↓
                              TurnManager → advance time
                              MetabolismManager / HydrationManager → update status
                                         ↓
                              GameScene → Renderer → Screen
```

## 🛠️ Tech Stack

- **Java 17** — Language version
- **Swing** — GUI framework (CPU-only rendering, character mode)
- **SLF4J + slf4j-simple** — Logging
- **Maven** — Build tool
- Window: 600×400, landscape, adjustable font size

## 🚀 Build & Run

```bash
# Compile
mvn compile

# Package
mvn package

# Run
java -cp target/cdda-1.0-SNAPSHOT.jar com.github.game.cdda.Game
```

## 🎮 Controls

| Key | Action |
|-----|--------|
| `W/A/S/D` or Arrow Keys | Move (one tile per action) |
| `5` | Wait one turn |
| `-` | Wait 10 turns |
| `E` | Inspect mode (view adjacent tile info) |
| `V` | Toggle log panel expanded/compact |
| `I` | Open inventory |
| `ESC` | In-game menu |

## 📁 Project Structure

```
cdda/
├── README.md                        # This file (English)
├── README_zh.md                     # Chinese documentation
├── CLAUDE.md                        # AI-assisted development guide
├── pom.xml                          # Maven configuration
├── src/main/java/com/github/game/
│   ├── engine/core/                 # Generic engine (13 files)
│   │   ├── GameEngine.java          # Main loop
│   │   ├── GamePanel.java           # Render panel
│   │   ├── Camera.java              # Camera
│   │   ├── input/                   # Input management
│   │   ├── render/                  # Rendering abstraction
│   │   ├── resource/                # Resource management
│   │   ├── scene/                   # Scene / Viewport
│   │   ├── screen/                  # Screen state machine
│   │   └── time/                    # Generic clock
│   └── cdda/                        # Game code (42 files)
│       ├── Game.java                # Entry point
│       ├── GameWorld.java           # Logic layer
│       ├── GameCalendar.java        # Calendar
│       ├── TurnManager.java         # Turn scheduling
│       ├── TemperatureManager.java  # Temperature
│       ├── MetabolismManager.java   # Metabolism
│       ├── HydrationManager.java    # Thirst
│       ├── screen/                  # UI screens
│       ├── world/                   # Map / chunks
│       ├── item/                    # Item system
│       ├── config/                  # Configuration
│       └── log/                     # Game log
└── src/main/resources/
    └── simplelogger.properties
```

## 📐 Design Philosophy

- **Engine/Game separation** — `engine.core` contains only generic infrastructure, reusable across projects
- **High cohesion, low coupling** — Subsystems are independent, injected via GameWorld; display layer never creates game state
- **Extensible** — TileType supports mod registration, Screen/Scene freely composable, item system extensible
- **Turn-based first** — All time advancement is player-action driven; NPC/enemy scheduling interfaces reserved for future expansion

## 📄 License

MIT
