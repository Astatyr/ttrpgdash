# TTRPG Dash — DM Board

A local JavaFX desktop app for running TTRPG sessions with a freeform map and token tracking.

## Requirements

- JDK 17 or higher
- Gradle (or use the included `./gradlew` wrapper once generated)
- VS Code with the **Extension Pack for Java** installed

## Setup

1. Open the `ttrpgdash/` folder in VS Code
2. Install the **Gradle for Java** extension if prompted
3. Run from terminal:

```bash
cd ttrpgdash
./gradlew run
```

Or on Windows:
```cmd
gradlew.bat run
```

> First run downloads JavaFX and Gson automatically via Gradle.

## Folder structure

```
assets/
  characters/
    Aria/
      Avatar.png     ← profile picture shown on token and sidebar
      Details.png    ← notes page, opens in popup when you click Details
    Goblin/
      Avatar.png
  maps/              ← put your map PNGs here for easy browsing
  music/             ← reserved for future music feature
data/
  state.json         ← auto-saved session (do not edit manually)
```

## How to use

### Loading a map
`Map → Load Map PNG…` — browse to any PNG in `assets/maps/`
`Map → Set Map Width in Feet…` — set how many feet wide the map is (controls token sizing)

### Adding entities
- Click **+ Add** under Players or Characters / NPCs in the sidebar
- Browse to the character's folder (e.g. `assets/characters/Aria/`)
- Enter their size in feet (5 = medium, 10 = large, 15 = huge)

### Placing tokens
- Click **Place** on an entity card — it highlights green
- Click anywhere on the map to place the token
- If a collision is detected, placement is cancelled

### Moving tokens
- Left-click and drag any token to move it freely (no grid snapping)
- Dragging onto another token **mounts** the dragged entity on it
- To move a mounted entity, you must move the rider first

### Token right-click menu
- **Buff / Debuff** — toggle status effects (poisoned, stunned, burning, etc.)
- **Remove from Map** — removes token but keeps entity in sidebar
- **View Details** — opens the Details.png popup

### Options menu
- **Clear Token Positions** — removes all tokens from map, keeps sidebar
- **Clear All** — resets the entire session

## Editing the code

Each file has a comment block at the top explaining its role.
The recommended edit order for new features:
1. Add fields to the relevant model (`model/`)
2. Update `JsonStateManager` if needed (new fields serialise automatically via Gson)
3. Update the UI (`sidebar/` or `map/`)
4. Wire new callbacks in `MainWindow`
