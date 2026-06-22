# TTRPG Dash — DM Board

A local JavaFX desktop app for running TTRPG sessions — freeform map, token tracking,
multi-scene management, session logging with undo/redo, replay, project save/load,
a second player-facing screen, and a Lua scripting engine for custom abilities.

---

## Requirements

- JDK 17 or higher
- Gradle (or use the included `./gradlew` wrapper)

## Running the app

```bash
./gradlew run
```

On Windows:
```cmd
gradlew.bat run
```

> First run downloads JavaFX, Gson, and LuaJ automatically via Gradle.

---

## Folder structure

```
assets/
  characters/
    Aria/
      Avatar.png        ← token portrait and sidebar card image
      Details.png       ← stat sheet, opens in a popup
      abilities.lua     ← optional Lua ability script (see Scripting)
  maps/                 ← map PNGs (loaded via Map menu)
  music/                ← audio tracks (mp3/wav)
data/
  scenes.json           ← scene list and active scene (auto-managed)
  scenes/               ← one JSON per scene (auto-managed)
  scripts/
    README.md           ← scripting guide
    project.lua         ← optional; loaded at startup as shared script context
    *.lua               ← any scripts browsable from the Scripts menu
logs/
  README.md             ← log format and replay guide
  session_*/            ← one folder per logging session
saves/                  ← default location for .ttrpg project archives
```

---

## Menu overview

### File
| Item | Description |
|---|---|
| Enable Logging | Toggle session logging on/off. A new `logs/session_*/` folder is created on enable. |
| Replay Log… | Open a past session log for step-by-step replay. |
| Save Project… | Bundle the current session into a `.ttrpg` archive (ZIP). |
| Load Project… | Extract a `.ttrpg` archive, replacing all current data. |
| Reset Project… | Delete all scenes, assets, and logs — start completely fresh. |
| Clear All… | Remove all entities and reset the map for the active scene only. |

### Map
| Item | Description |
|---|---|
| Load Map PNG… | Browse for a map image. Files outside `assets/maps/` are copied in automatically. |
| Set Map Width in Feet… | Sets the real-world scale for token sizing. |
| Fit Map to Window | Reset zoom and pan. |
| Clear Map… | Remove the map image and all token positions. |
| Clear Token Positions | Remove tokens from the map without deleting entities from the sidebar. |
| Show Names / Show Status Effects | Toggle map overlays for cinematic scenes. |

### Scripts
| Item | Description |
|---|---|
| Open Script Browser… | Lists all `.lua` files in `data/scripts/`. Click to run. |

### View
| Item | Description |
|---|---|
| Open Player View | Open the player-facing window (read-only map + player bar). |

### Undo / Redo
Direct menu items. Keyboard shortcuts: **Ctrl+Z** / **Ctrl+Y**.
Pressing either while a Lua script is running cancels the script immediately.

---

## How to use

### Maps
- **Map → Load Map PNG…** — supports any PNG, including files outside `assets/maps/`
  (they are copied in automatically)
- **Map → Set Map Width in Feet…** — controls how large tokens appear relative to the map

### Entities (sidebar)
- Click **+ Add** under Players or Characters / NPCs
- Browse to the character folder inside `assets/characters/`
  (folders outside this directory are rejected)
- Enter the token diameter in feet (5 = medium, 10 = large, 15 = huge)

### Placing and moving tokens
- Click **Place** on a sidebar card → click the map to drop the token
- Drag to move; collision detection prevents invalid placement
- Dragging one token onto another **mounts** it (rider moves with the mount)
- Right-click a token: **Buff/Debuff**, **Remove from Map**, **View Details**, **Run Script**

### Scenes
The scene panel is on the right. Use the **+** button to add scenes, drag to reorder,
and right-click a scene tab to rename or delete. Deleting a scene removes its data file.

### Music
The music panel is in the scene sidebar. **+ Add Music** accepts files from anywhere on
disk (copied into `assets/music/` automatically). Volume slider and loop toggle per track.

### Player view
**View → Open Player View** opens a second window for players to see the map and their
avatars. It follows scene switches automatically with a crossfade transition.

### Session logging
Enable with **File → Enable Logging**. Every action — moves, status effects, scene
switches, entity changes — is written to a timestamped log folder in `logs/`. Each session
folder also contains scene JSON snapshots for replay.

**Undo/Redo** (Ctrl+Z / Ctrl+Y) works by reading directly from the log file — no separate
undo stack in memory.

### Replay
**File → Replay Log…** opens a file chooser. Select a `session.log` file from inside any
`logs/session_*/` folder. The replay window shows the scene state at log-enable time and
lets you step through every action with a slider or the play button (1 action per second).
Multiple replay windows can be open simultaneously.

### Project save / load / reset
- **Save Project…** — bundles `data/`, `assets/`, and `logs/` into a `.ttrpg` file
  (ZIP archive). A progress dialog tracks the export; the save can be cancelled mid-way.
- **Load Project…** — extracts a `.ttrpg` archive and rebuilds the app with the loaded
  data. Shows a warning since this permanently replaces all current data.
- **Reset Project…** — deletes `data/`, `assets/`, and `logs/` and restarts clean.

---

## Scripting (Lua)

Scripts can automate abilities, encounter logic, dice rolls, and entity manipulation.

### Quick start

1. Create `data/scripts/my_encounter.lua`:
   ```lua
   dashboard.output("Round starts!")
   local roll = dashboard.roll("1d20")
   if roll >= 15 then
       dashboard.addStatus("Goblin", "stunned")
       dashboard.output("Goblin is stunned (rolled " .. roll .. ").")
   end
   ```
2. Open **Scripts → Open Script Browser…**, select the file, click **▶ Run**.

### Entity abilities

Create `assets/characters/{name}/abilities.lua`. Right-click the entity's token on the
map → **Run Script** → select an ability:

```lua
abilities = {
    {
        name = "Healing Word",
        desc = "Restore HP and bless self.",
        run  = function()
            local heal = dashboard.roll("1d4+2")
            dashboard.output(self.name .. " heals for " .. heal .. " HP.")
            dashboard.addStatus(self.name, "blessed")
        end
    }
}
```

`self` is automatically set to the entity that was right-clicked. Shared ability files
work correctly for entities from the same character folder (e.g. "Goblin" and "Goblin 2").

### Full API reference

See **`data/scripts/README.md`** for the complete `dashboard` API, examples, and notes on
undo behaviour.

---

## Output panel

A collapsible log at the bottom of the main window shows:
- `[SYSTEM]` — scene switches, map loads, undo/redo confirmations, logging state
- `[DICE]` — dice roll results with per-die breakdown
- `[SCRIPT]` — output from Lua scripts

Click the **▼ Output** header to collapse it; click again to expand.

---

## Character folder layout

```
assets/characters/Aria/
  Avatar.png       ← shown on token, sidebar card, and player bar (optional)
  Details.png      ← opens in a popup via right-click → View Details (optional)
  abilities.lua    ← Lua ability script (optional, see Scripting)
```

Missing images fall back gracefully (white circle for avatars, checkerboard for maps).
