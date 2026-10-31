# Performance changes: what changed and why

**Branch:** `refactor/menu-navigator-objects`, the changes made after commit `a0c3264` (the performance review)
**Made by:** Dale, using Claude Code (an AI coding assistant), on 31 October 2026
**Files changed:**
- [`LatticeBoltzmannCFDSolver.java`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java) (most of the work)
- [`ThreeDimensionalRenderer.java`](core/src/main/java/io/github/wind_tunnel/ThreeDimensionalRenderer.java)
- [`Main.java`](core/src/main/java/io/github/wind_tunnel/Main.java)
- [`FreeplayMenu.java`](core/src/main/java/io/github/wind_tunnel/Menus/FreeplayMenu.java) and [`LevelsMenu.java`](core/src/main/java/io/github/wind_tunnel/Menus/LevelsMenu.java) (one line each)

This report goes with [`performance-review.md`](performance-review.md), which explains *why* the game was slow. This one explains *what was changed* to fix it, how each change works, and how we checked that nothing broke.

## Summary

The simulation runs and draws much faster at high resolutions, while producing **exactly the same numbers** as before.

Measured on an Apple M1 Pro with ANGLE, as the game ships. All times are milliseconds per frame:

| Grid | Before | After | Faster by | Paused, before | Paused, after |
|---|---|---|---|---|---|
| 128×72 | 2.4 | 1.7 | 1.4× | 1.4 | 1.0 |
| 256×144 | 3.8 | 2.3 | 1.7× | 2.5 | 0.8 |
| 512×288 | 14.3 | 2.7 | 5.3× | 6.9 | 0.8 |
| **1024×576** | **57.0** | **5.2** | **11×** | **25.0** | **0.7** |
| 64×36×36 (3D) | 13.3 | 3.2 | 4.1× | 3.6 | 1.4 |
| **128×72×72 (3D)** | **105.6** | **13.8** | **7.6×** | **15.6** | **3.8** |

A smooth 60 frames per second needs each frame done in 16.7 ms. 1024×576 used to take 57 ms (about 18 frames per second) and now takes 5 ms. Every resolution, 2D and 3D, now fits well inside that budget.

How the time splits at the two big sizes:

| | Step before | Step after | Drawing before | Drawing after |
|---|---|---|---|---|
| 1024×576 | 31.0 | 3.4 | 26.0 | 1.8 |
| 128×72×72 | 90.0 | 9.6 | 15.6 | 4.3 |

## What you'll notice in the game

- **Speed:** high resolutions run smoothly, and a paused simulation costs almost nothing to show.
- **2D picture:** exactly the same as before, pixel for pixel.
- **3D picture:** the same view, but the dots are slightly bolder. Each dot is now a 2×2 pixel square instead of a radius-1 circle (see change 6).
- **A crash is gone:** Main menu → Settings → raise the resolution → back used to crash the game (see "Fixed along the way").

---

## The changes, one by one

### 1. The fluid is stored in one flat array

**Before:** `float[][][][] densities`. In Java that isn't one block of memory. It's an array of references to arrays of references to arrays of references to small arrays of 9 (or 19) floats. At 1024×576 the three main arrays (`densities`, `cellAverageVelocities` and `barriers`) were made of about **3 million separate objects**. Reading one number meant following three references first.

**Now:** one flat `float[] densities`. Each cell has a number, its **index**:

```java
int i = (z*gridY + y)*gridX + x;      // which cell
float value = densities[i*neighbours + d];   // that cell's density in direction d
```

`velocities` (which replaces `cellAverageVelocities`) and `barriers` use the same index ([line 46](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L46)).

**Why it's faster.** The CPU keeps recently used memory in a small, very fast store called the **cache**. When the number it needs isn't there (a **cache miss**), it waits for main memory, which is roughly 100 times slower.
- **Old layout:** the three references, plus objects scattered around memory, meant lots of misses.
- **New layout:** a cell's 9 (or 19) values are next to each other, and neighbouring cells are next to each other. So when the CPU fetches one value, the ones it needs next usually come along with it.

**Why "a cell's values together" and not "one array per direction".** The review suggested trying both. We measured them:
- **2D:** "one array per direction" was slightly faster, by about 0.5 ms at 1024×576.
- **3D:** it was much slower: 15 ms against 4.8 ms for collide at 128×72×72. Its 19 equally sized arrays keep pushing each other out of the cache.

So both 2D and 3D use "a cell's values together". That's one layout and one set of code, and close to the best in both cases.

**Memory** (estimated from the array sizes, not measured):
- **2D 1024×576:** goes from about **100 MB to about 50 MB**, because the millions of small-object overheads are gone.
- **3D 128×72×72:** goes from about **91 MB to about 110 MB**, because of the second array in change 2.

### 2. Streaming writes into a second array ("double buffering")

**Before:** `stream()` moved the densities around inside the same array. That only worked because each loop ran in a carefully chosen direction, so a value was always read before it was overwritten. (The old code had separate loops for each pair of directions, plus two extra loops for the edge columns.)

**Now:** `stream()` reads from `densities` and writes into a second array, `nextDensities`, then swaps the two ([line 282](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L282)). Every density is **pulled** from the neighbour it's moving away from. If that neighbour would be outside the grid, the cell keeps its old value, exactly as before. The boundary conditions (inlet, outlet, top and bottom) are applied afterwards, unchanged.

**Why:**
- **Simpler:** one loop handles every direction, in 2D and 3D, and the order of the loops no longer matters.
- **Shareable:** because nothing is overwritten while it might still be read, different parts of the grid can be streamed at the same time on different cores (change 3).
- **Same result:** checked bit for bit; see "How we know nothing broke".

### 3. The solver uses all the CPU cores

**Before:** the whole step ran on one core while the other nine did nothing.

**Now:** `collide()` and `stream()` split the grid into **slabs** (rows in 2D, z planes in 3D), and Java's parallel streams share the slabs between the cores ([`forEachSlab`, line 842](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L842)):

```java
IntStream slabs = IntStream.range(0, gridZ > 1 ? gridZ : gridY);
if (cellCount >= PARALLEL_THRESHOLD) {
    slabs = slabs.parallel(); // Java shares the slabs out between all the CPU cores
}
slabs.forEach(action);
```

**Why it's safe:**
- **Collide** reads and writes only one cell's own values.
- **Stream** reads only the old array and writes only the new one (change 2).

So no two cores ever write to the same place, and each cell's arithmetic is unchanged. The results are therefore the same however the work is split.

**One important change made this possible.** The working values in `collide()` (`cellDensity`, `omega`, `vx3`, `vxvx` and so on) used to be **fields** of the class. A field has one copy, shared by everything. If two cores ran `collide()` at once, each would overwrite the other's `cellDensity` mid-calculation and produce nonsense. This is called a **race condition**, and it would show up as random, unrepeatable errors. They're now **local variables** inside the loop, so each core has its own copy. Local variables are also slightly faster on one core, because the CPU can keep them in its registers.

**Why there's a threshold.** Handing work out to cores has a small fixed cost. Grids under 20,000 cells (128×72 is 9,216) finish faster on a single core, so they stay single-threaded ([line 65](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L65)).

### 4. A list of barrier cells

**Before:** `bounce()` and `zeroBarriers()` checked every cell in the grid each step just to find the few that are barriers. `removeAllBarriers()` and the 3D drawing did the same.

**Now:** the solver keeps a list of the indexes of all barrier cells (`barrierCells`). It's rebuilt only after a barrier has been added or removed ([`updateBarrierCells`, line 822](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L822)). Every change to `barriers` goes through one small method, `setBarrier()`, which marks the list as out of date.

**Why:** the work is now proportional to the size of the obstacle rather than the size of the grid. `bounce()` at 1024×576 went from 2.75 ms to 0.14 ms.

**Same results.** `bounce()` adds each barrier's densities to the fluid cell they came from. Each fluid cell and direction receives from at most one barrier, so the order the barriers are visited in can't change the result. The equivalence checks confirmed this.

### 5. The 2D field is drawn as one picture

**Before:** every cell was drawn as its own rectangle with `ShapeRenderer`. At 1024×576 that's 590,000 rectangles and about 56 MB of corner data sent to the graphics card every frame (the review explains this in detail).

**Now** ([`renderField`, line 467](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L467), [`fillField`, line 491](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L491)):
1. **One pixel per cell.** A `Pixmap` (an image in memory) holds exactly one pixel per cell, so 1024×576 pixels at 1024×576.
2. **Colour each pixel** with the same rules as before: black for fixed level barriers, white for other barriers, otherwise the colour scale. The cores share the rows.
3. **Upload once.** The image goes to the graphics card as a `Texture` in one go (2.4 MB instead of 56 MB).
4. **Draw once.** The texture is drawn stretched across the screen with **nearest** filtering, which keeps every cell a sharp square instead of blurring it.

**Only redraw when something changed.** The picture is rebuilt only when the fluid, the barriers, the plot, the flow speed or the "level loaded" setting have changed. Otherwise the existing texture is drawn again. That's why a paused 1024×576 frame now costs 0.7 ms instead of 25 ms.

**Work done once per frame, not per cell.** Which plot is selected, the contrast (`1/(3*flowSpeed)`) and whether a level is loaded are looked up once before the loop. Before, they were recalculated 590,000 times.

**What the menus do differently.** `render()` now takes the `SpriteBatch` as well, and it starts and ends its own drawing. So `FreeplayMenu` and `LevelsMenu` call `cfdSolver.render(sr, batch)` *before* `sr.begin()` instead of inside it ([FreeplayMenu line 53](core/src/main/java/io/github/wind_tunnel/Menus/FreeplayMenu.java#L53)). The buttons are still drawn afterwards with `ShapeRenderer`, on top of the field.

### 6. The 3D view

**Matrices once per frame.** `ThreeDimensionalRenderer.rotate()` and `pointProjection()` used to work out six sines and cosines and build new rotation and projection matrices for every point. That was about 19 new objects per point, or 880,000 per frame at 128×72×72. All of that is now done once per frame in a new method, [`prepareFrame()`](core/src/main/java/io/github/wind_tunnel/ThreeDimensionalRenderer.java#L46):
- **The maths is unchanged:** the same matrices, multiplied in the same order, giving the same positions to the last bit.
- **No new objects per point:** `pointProjection()` now does the matrix multiplication itself, instead of creating a new `Vector4` and `Vector3` for every point.

**Only the cells that are drawn are visited.** Before, the drawing loop went through all 663,552 cells and skipped the 93% that aren't on the outside of the box. Now [`renderPoints`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L513) goes straight to the six faces and then the barrier list. The "only render the selected plane" option still draws just that plane.

**Smaller dots, in corner count.** Each point used to be `sr.circle(x, y, 1)`, which libGDX draws as 6 triangles (18 corners). Now it's a 2×2 pixel square (2 triangles, 6 corners) ([line 571](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L571)). That covers slightly more of the screen than the old circle (4 pixels against about 3.1), so the 3D view looks a little bolder. If you prefer the old look, try `sr.rect(screenPos.x-0.75f, screenPos.y-0.75f, 1.5f, 1.5f)`.

**Drawing order.** The faces are now drawn one after another, followed by the barriers. Before, everything was drawn in x-then-y-then-z order. Where two dots overlap on screen, a different one may end up on top. There was no depth sorting before either, so neither order is more "correct".

### 7. The window title is updated only when the FPS changes

**Before:** every frame the game built a new title string, asked the monitor for its refresh rate, and set the window title. Setting a title goes through the operating system, and it cost about 0.34 ms each time.

**Now:** the title is only set when the FPS value changes, which libGDX updates once a second ([Main.java line 41](core/src/main/java/io/github/wind_tunnel/Main.java#L41)).

### 8. The colour scale is built in RGB, without HSB

**Before:** `calculateColours()` worked out a **hue** for each of the 600 colours and converted it with `java.awt.Color.HSBtoRGB`. HSB means hue, saturation, brightness.

**Now** ([line 706](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L706)): the scale blends directly between five RGB colours, blue → cyan → green → yellow → red. Each of the red, green and blue amounts changes in a straight line between one key colour and the next.

**Same spacing.** The old hue had a small sine wave added to it, so some colours take up more of the scale. Measured as a position along the scale from blue (0) to red (1), the old formula works out to exactly:

```
position = c/600 + 0.045 * sin(4π * c/600)
```

The new code uses that directly. **All 600 colours are identical to before,** checked one by one. Using RGB also removes the game's only use of `java.awt`, which is part of desktop Java and isn't available if the game is ever built for Android, iOS or the web.

### 9. The texture is freed when the game closes

`Main.dispose()` now calls `LatticeBoltzmannCFDSolver.getInstance().dispose()`, which frees the 2D picture's texture. The solver also frees the old texture and makes a new one whenever the grid size changes.

---

## Fixed along the way: crash after changing resolution from the main menu

**To reproduce on the old code:** Main menu → Settings → raise the resolution → back. The game crashes with `ArrayIndexOutOfBoundsException`.

**Why it crashed.** Going back to the main menu runs `removeAllBarriers()`. The old version looped over the *settings* resolution (now the new, bigger size) while the arrays still had the old, smaller size. No fluid had been set up at the new size yet, because that only happens when Freeplay or Levels opens.

**Why it's fixed.** The solver now remembers the size its arrays were made for (`gridX`, `gridY`, `gridZ`, [line 39](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L39)) and uses that for all its own loops. The same mismatch could also have hit the "clear barriers" button or the step and clear keys while in Settings after a resolution change. Those are covered by the same fix.

---

## How we know nothing broke

1. **Fluid values: bit for bit identical.** Eight scenarios were run on the old code and the new code, and every density, velocity and barrier value was compared at the end: about 131 MB of numbers. **All eight were identical, to the last bit.** The scenarios:
   - 2D with an obstacle (300 steps);
   - barriers removed and drawn mid-run;
   - resolution raised, and lowered, with barriers rescaled;
   - level 3 with fixed barriers, then "clear barriers";
   - 3D with an obstacle;
   - level 11;
   - a 3D resolution change.

   This is possible because each calculation still does exactly the same arithmetic in exactly the same order. Only *where* the numbers are stored and *which core* does the work changed. Both the single-core path (small grids) and the multi-core path (large grids) were covered.

2. **The bug-fix checks from commit `608cc9c` still pass, 8 of 8.** Collide still conserves mass and momentum in 2D and 3D, level 11 still runs, the OBJ parser and bitmap memory fixes still hold, and the small-grid level crash stays fixed.

3. **Colours:** all 600 are identical to the old HSB version.

4. **Screenshots:** with the same simulation state, the old and new builds were screenshotted. The 2D x-velocity plot, the 2D y-velocity plot and level 3 with its fixed barriers were **byte-for-byte identical**. The 3D view showed the same shape, angle and colours, with the slightly bolder dots described in change 6.

5. **The game itself:** the built app started and ran on the main menu for 15 seconds with no errors.

**Not tested:** nobody clicked through every menu by hand or played a level, and it has only been run on this one Mac.

## Please try in the game

1. Freeplay at 1024×576: run, pause, step, draw and erase barriers. The FPS in the title should stay near your screen's refresh rate.
2. Change the plot between x velocity and y velocity while paused. The picture should update straight away.
3. Levels: play a 2D level (fixed barriers black, yours white) and level 11 (3D).
4. 3D Freeplay: rotate with W/A/S/D, zoom, scroll through planes, and turn "only render selected plane" on and off.
5. Main menu → Settings → raise resolution → back. This used to crash.
6. Import a `.bmp` and an `.obj` file.

## If you change this code later

- **Cell positions:** use `gridX`, `gridY` and `gridZ` inside the solver, not `settings.getResolution()`. The setting can change in the Settings menu before the arrays are rebuilt.
- **Changing barriers:** go through `addBarrier()`, `removeBarrier()` or `setBarrier()`. Setting `barriers[i]` directly would leave the barrier list and the 2D picture out of date.
- **Changing what the 2D picture shows:** if you add anything new that affects the picture (a new plot, for example), make sure it sets `fieldChanged = true` or is compared in `renderField()`. Otherwise the picture won't update while the simulation is paused.
- **Inside `forEachSlab(...)`:** keep the working values as local variables, not fields, or the cores will overwrite each other (change 3).
- **3D drawing:** call `renderer.prepareFrame()` before using `rotate()` or `pointProjection()` in a frame.
- **Where to call `render(sr, batch)`:** outside `sr.begin()`/`sr.end()` and `batch.begin()`/`batch.end()`, because it starts and ends its own drawing.

## Not changed, and why

- **Steps per frame** (Finding 9 of the review): there's still exactly one simulation step per drawn frame. Running more steps per frame, or a fixed number per second, would change how fast the flow appears to move, so it's your design choice. There's now plenty of spare time per frame if you want it.
- **The 1920×1080 window and a viewport** (the review's side finding): the window still doesn't fit smaller laptop screens. Fixing it means changing how every menu positions buttons and reads the mouse, so it's a separate piece of work.
- **`Settings.oldResolution`:** still not read anywhere since the earlier bug fix. It can be removed in a tidy-up.

## Words used in this report

| Word | Meaning |
|---|---|
| **Cache** | A small amount of very fast memory inside the CPU that holds recently used data. Reading from it is about 100× faster than main memory. |
| **Cache miss** | When the data the CPU needs isn't in the cache, so it has to wait for main memory. |
| **Core** | One of the CPU's independent processors. This Mac has 10. Each can run its own **thread** (sequence of instructions) at the same time as the others. |
| **Race condition** | A bug where two threads use the same variable at the same time and overwrite each other's values. The result then depends on timing. |
| **Slab** | One row of cells (2D) or one z plane (3D): the unit of work handed to each core. |
| **Double buffering** | Writing new values into a second array instead of overwriting the one being read, then swapping the two. |
| **Texture** | An image stored on the graphics card, which it can draw (and stretch) very quickly. |
| **Draw call** | One instruction to the graphics card to draw something. Each has a fixed cost, so a few big ones beat thousands of small ones. |
| **Bit for bit identical** | Every number is exactly the same down to its last binary digit, not just "very close". |
