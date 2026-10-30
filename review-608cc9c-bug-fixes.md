# Review: bug fixes in commit `608cc9c`

**Branch:** `refactor/menu-navigator-objects` on [thegecko1976/Wind-Tunnel](https://github.com/thegecko1976/Wind-Tunnel/tree/refactor/menu-navigator-objects)
**Commit:** [`608cc9c`](https://github.com/thegecko1976/Wind-Tunnel/commit/608cc9c127a0a394b7ee2f9803d97006db539bff), "Fix import memory leak, level 11 and level-loading crashes, 3D collision and OBJ parsing"
**Builds on:** [`29a2b2e`](https://github.com/thegecko1976/Wind-Tunnel/commit/29a2b2e), the menu navigation refactor
**Made by:** Dale, using Claude Code (an AI coding assistant), on 30 October 2026
**Size:** 4 files, +104 / −52 lines

All five bugs are also on `master`: the menu refactor didn't cause any of them. If the project is being assessed, you may need to declare these changes as outside help.

## Summary

| # | Bug | What you'd see | Fixed in |
|---|---|---|---|
| 1 | Bitmap memory never freed | Memory climbs while a `.bmp` is selected in Import File, and each time a bitmap level loads | `ImportFile`, `ImportFileMenu` |
| 2 | Level 11 sets up the fluid as 2D | Level 11 (cube) crashes on Run or Step | `Levels` |
| 3 | 3D collision equations | The 3D simulation doesn't conserve mass or momentum | `LatticeBoltzmannCFDSolver` |
| 4 | OBJ face parsing | Selecting an `.obj` exported with Blender's default settings crashes the app | `ImportFile`, `ImportFileMenu` |
| 5 | Barrier rescaling uses a stale size | Freeplay on a small grid, then Levels, crashes | `LatticeBoltzmannCFDSolver` |

## 1. Bitmap memory never freed

**Problem.** libGDX only frees a `Pixmap`'s memory when `dispose()` is called; the garbage collector doesn't. `getModelResolution()` and `loadBitmap()` never disposed theirs. `ImportFileMenu.render()` called `getModelResolution()` twice per frame while a bitmap was selected, so memory grew about 120 times a second. The same menu also listed both folders four times per frame and re-read the selected file every frame.

**Fix.**
- [`ImportFile.java` L54, L66, L267](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/ImportFile.java#L50-L67): every `Pixmap` is disposed once it has been read.
- [`ImportFileMenu.onEnter()`](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/Menus/ImportFileMenu.java#L127-L132): reads the folder list once when the menu opens.
- [`ImportFileMenu.selectFile()`](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/Menus/ImportFileMenu.java#L134-L156): reads a file's details once when you click it, and `render()` just draws the saved lines.
- `getNumberOfFiles()` was no longer used, so it's removed.

**Measured:** 300 calls to `getModelResolution("f1_car.bmp")` grew native memory by **134 MB** before the fix and **9 MB** after (normal background noise).

## 2. Level 11 sets up the fluid as 2D

**Problem.** `loadSelectedLevel()` calls `settings.reset()`, which sets the solver to 2D. It then calls `initialiseFluid()`, which sizes each cell for 9 directions, and only afterwards switches level 11 to 3D. The next `collide()` reads 19 directions and throws `ArrayIndexOutOfBoundsException: Index 9 out of bounds for length 9`.

**To reproduce on `master`:** Levels → left arrow from level 1 (it wraps to level 11) → Run.

**Fix.** [`Levels.java` L50-L56](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/Levels.java#L50-L56) moves the 2D/3D choice above `initialiseFluid()`, with a comment explaining why the order matters.

## 3. 3D collision equations

**Problem.** In the 3D branch of `collide()`:
- **Velocity:** the x and y velocities only summed directions 1–8, but directions 10–18 also move in x or y. There was a `//cellVelocity.x +=` stub where this belonged.
- **Equations:** most of the 19 hand-written lines didn't match their direction:
  - direction 3 `{1,-1,0}` used the formula for `{1,1,0}`;
  - directions 9 and 14 (pure z) used the y-velocity;
  - directions 10–18 all shared one identical formula.

As a result, one collision step changed a test cell's mass from 1.017 to 1.098, and a cell already at equilibrium moved away from it.

**Fix.** [`LatticeBoltzmannCFDSolver.java` L184-L186](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L184-L186) adds the missing velocity terms. [L222-L228](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L222-L228) replaces the 19 lines with one loop over your existing `relativeDirections` table and a new weights table ([L52](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L51-L55)):

```java
// equilibrium for each direction e: weight*density*(1 + 3(e.u) + 4.5(e.u)^2 - 1.5(u.u))
float uu = cellVelocity.x*cellVelocity.x + cellVelocity.y*cellVelocity.y + cellVelocity.z*cellVelocity.z;
for (int d=0; d<19; d++) {
    float eu = relativeDirections[d][0]*cellVelocity.x + relativeDirections[d][1]*cellVelocity.y + relativeDirections[d][2]*cellVelocity.z;
    float equilibrium = weights3D[d]*cellDensity*(1 + 3*eu + 4.5f*eu*eu - 1.5f*uu);
    densities[x][y][z][d] += omega*(equilibrium-densities[x][y][z][d]);
}
```

This is the same equilibrium formula your 2D branch and `initialiseCell()` already use. For the starting flow it gives exactly the values `initialiseCell()` sets, which the checks confirm. **The 2D branch is unchanged.** The `one18thDensity` field was only used by the old 3D lines, so it's removed.

**Visible change:** 3D simulations will now look different from before.

## 4. OBJ face parsing

**Problem.** `getFacesFromOBJModel()` ran `Integer.parseInt()` on each face corner. Blender's default export writes corners as `1/1/1` (vertex/texture/normal), which throws `NumberFormatException`. Because the Import File menu read the file every frame, the app crashed as soon as such a file was selected. The comment in `loadOBJModel()` asks for UVs and normals to be turned off, but nothing checked that.

**Fix.** In [`ImportFile.java` L133-L201](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/ImportFile.java#L133-L201):
- **Face corners:** all four OBJ formats are accepted (`v`, `v/vt`, `v/vt/vn`, `v//vn`), and only the vertex index is used.
- **Negative indices:** these are part of the OBJ format and count back from the latest vertex; they're now handled.
- **Bad files:** a malformed number, a vertex with fewer than 3 coordinates, or a face pointing at a vertex that doesn't exist makes the method return `null` instead of crashing.
- **Import:** `importFile()` ([L37](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/ImportFile.java#L37)) returns `false` for an unreadable `.obj` before it clears barriers or changes the solver, so a bad file leaves your simulation alone.
- **Loading:** `loadOBJModel()` ([L94](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/ImportFile.java#L94)) gets the same `null` check as your "tidying up menus" commit, so the two branches won't conflict on that line.
- **Menu:** an unreadable file shows "file can't be read" in the Import File menu.

## 5. Barrier rescaling uses a stale size

This bug turned up while testing the others.

**Problem.** `scaleBarriersUsingResolution()` worked out the old grid size from `settings.getOldResolution()`. But `settings.reset()`, which runs on every level load, sets that value back to 128×72×1 without changing the solver's actual barrier array. When the array was smaller, the rescale read past its end and threw `ArrayIndexOutOfBoundsException: Index 36 out of bounds for length 36`.

**To reproduce on `master`:** Freeplay → Settings → lower resolution to 64×36 → back → back → Levels.

**Fix.** [`LatticeBoltzmannCFDSolver.java` L535](https://github.com/thegecko1976/Wind-Tunnel/blob/608cc9c127a0a394b7ee2f9803d97006db539bff/core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L533-L543) reads the old size from the barrier array itself, so it always matches the data being scaled. In normal Freeplay ↔ Settings use, the result is the same as before.

## Other behaviour changes

- **File list:** the Import File list is read when the menu opens, not live. A file added while the menu is open appears the next time you open it.
- **Page count:** it used to be `(files / 9) + 1`, which showed an empty last page when the number of files was an exact multiple of 9. It's now rounded up correctly, and is never less than 1.

## How it was tested

- **Headless checks:** a small program ran the game's real classes from the built jar without opening a window, one check per JVM because the singletons carry state between them. Every check failed before the fix and passes after.

  | Check | Before | After |
  |---|---|---|
  | 1. 300 × `getModelResolution` on a bitmap | +134 MB | +9 MB |
  | 2. Load level 11 and step | crash (index 9) | OK |
  | 3a. 3D collide on a cell at equilibrium | changed by 0.024 | changed by 6×10⁻⁸ |
  | 3b. 3D collide conserves mass and momentum | mass 1.017 → 1.098 | unchanged |
  | 3c/3d. Same two checks in 2D (regression) | crash (bug 5 in the test setup) | pass |
  | 4. OBJ with `v/vt/vn`, `v//vn`, negative indices; broken file rejected | crash | pass |
  | 5. Freeplay at 64×36, then Levels | crash (index 36) | OK |

- **Build:** `./gradlew lwjgl3:jar` compiles.
- **App launch:** the built app ran for 15 seconds on the main menu with no errors.
- **Not tested:** nobody clicked through the menus or watched a 3D simulation run.

## Please check in the app

1. Levels → go to level 11 → Run and Step. It should run in 3D without crashing.
2. Freeplay → Settings → resolution 64×36 → back → back → Levels. Level 1 should load.
3. Settings → Import File → select a `.bmp` and leave the menu open for a minute. Memory should stay flat in Activity Monitor or Task Manager.
4. Export any mesh from Blender with **default** OBJ settings into `assets/models/` → Import File → select it (vertices and faces should show) → Import. The model should appear.
5. Put a deliberately broken `.obj` in `assets/models/` → select it. It should say "file can't be read", and Import should do nothing.
6. Run a 3D simulation and check the flow looks sensible. It will look different from before.

## Questions for you

1. **3D physics:** does the D3Q19 equilibrium loop match what you researched? Is the old 3D behaviour something you wanted to keep for any reason?
2. **File list:** is refreshing the Import File list only when the menu opens OK, or do you want files to appear live?
3. **After an import:** the app still goes back two menus, past Settings, to the simulation, which is unchanged from before. Is that the flow you want?
4. **`oldResolution`:** `Settings.oldResolution` is no longer read anywhere; it's still written in `Settings.reset()`, `FreeplayMenu.onPause()`, `LevelsMenu.onPause()` and at the end of `scaleBarriersUsingResolution()`. Fine to remove it in a follow-up?

## Not in this commit

- **`oldResolution` clean-up** (question 4).
- **The coding-standards review:** enums instead of strings like `"2D LBM"`, named constants for locked-setting and keybind indices, splitting up `Settings`, a viewport instead of hard-coded 1920×1080, and dead code.
- **Unit tests:** the headless checks could become the project's first tests (`core/src/test`) if you want them.
