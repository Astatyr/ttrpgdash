# Session Logs

Each logging session creates its own folder here:

```
logs/
  session_2026-06-20_23-02-59/
    session.log          ← the action log
    scene_<id>.json      ← one snapshot per scene (state at log-enable time)
  session_2026-06-21_10-15-04/
    ...
```

## Enabling logging

In the DM window: **Options → Enable Logging**. A new session folder is created immediately and the current state of every scene is snapshot-saved there. Logging stops when you toggle it off or close the app.

## Log format (v1)

Plain text, human-readable. Each entry is a named block:

```
# Switch Scene
Log Version: 1          ← only present in Start Log
Time: 2026-06-20 23:03:15
Previous Scene ID: scene_abc123
Scene ID: scene_def456

```

Blocks are separated by a blank line. The first entry is always `# Start Log` followed by one `# Scene Snapshot` per scene. The last entry (on clean exit) is `# End Log`.

`Log Version` is written in `Start Log` only. The parser is backwards-compatible and reads older logs best-effort; the version number is bumped only when a breaking format change makes silent misreading likely.

## Replay

**File → Replay Log…** opens a file browser. Navigate into any session folder and select `session.log`. The replay window shows the scene state at the moment logging was enabled and lets you step through every action.

- **▶ / ⏸** — play at one action per second / pause
- **Slider** — jump directly to any action step
- Multiple replay windows can be open at the same time from different sessions.

Map images are loaded from their original paths. If an asset has been moved or deleted, a pink/black checkerboard is shown in its place.

## Notes

- Music is not logged; replay is map and entity actions only.
- Undo/redo during a live session reads from `session.log` via in-memory byte offsets and does not affect the replay files.
- Deleting a session folder permanently removes that session's replay data.
