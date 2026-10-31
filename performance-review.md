# Performance review: why the simulation lags at high resolution

**Code reviewed:** branch `refactor/menu-navigator-objects` at `ed7d085` (same game code as `608cc9c`)
**Measured on:** Apple M1 Pro (8 performance + 2 efficiency cores), 32 GB, macOS, Java 21
**Date:** 31 October 2026
**Prepared by:** Dale, using Claude Code (an AI coding assistant)

## Summary

At high resolutions, both halves of each frame are slow: **running the simulation step** and **drawing the result**. At 1024×576 they cost about the same. In 3D the step costs far more than the drawing.

| Grid | Simulation step | Drawing | Whole frame | Frames per second |
|---|---|---|---|---|
| 128×72 | 0.5 ms | 1.7 ms | 2.2 ms | limited by vsync |
| 256×144 | 1.6 ms | 2.5 ms | 4.1 ms | limited by vsync |
| 512×288 | 6.9 ms | 7.0 ms | 13.9 ms | about 60, borderline |
| **1024×576** | **30.8 ms** | **25.6 ms** | **56 ms** | **about 18** |
| 64×36×36 (3D) | 9.7 ms | 3.5 ms | 13.2 ms | about 60, borderline |
| **128×72×72 (3D)** | **93 ms** | **13.8 ms** | **107 ms** | **about 9** |

For smooth motion a frame has to finish in **16.7 ms** on a 60 Hz screen (8.3 ms at 120 Hz). Anything slower means frames are skipped, which is the lag you see.

Prototypes of the suggested changes, using the same maths, suggest a 1024×576 frame could take **about 6 ms instead of 56 ms**, and the 3D step **about 9 ms instead of 93 ms**. The four biggest causes, in order:

1. **Every 2D cell is drawn as its own rectangle**, rebuilt from scratch every frame. Drawing the field as one image is 3–7× faster and produces an identical picture.
2. **The fluid data is stored as millions of tiny arrays.** Following all those references to reach the numbers costs more than the maths itself. Flat arrays make streaming 2–20× faster, depending on how they're laid out.
3. **The solver uses one of the ten CPU cores.** Spreading it across all of them makes it another 4–7× faster.
4. **The 3D projection allocates about 19 objects and recomputes 6 sin/cos values for every point**, even though they're identical for the whole frame. Doing that once per frame is 5× faster.

## How this was measured

Two small benchmark programs were written outside the project. Nothing in the game was changed.

- **`PerfBench`** opens a window and runs the game's own `LatticeBoltzmannCFDSolver` and its `render()`. It times `collide()`, `stream()`, `bounce()` and `render()` separately at each resolution, with a circular obstacle in place. It calls `glFinish()` before stopping the clock, so the GPU's work is included. Each figure is the average of 20 frames after 8 warm-up frames, so the Java JIT compiler has finished optimising first.
- **`SolverBench`** runs prototype versions of collide and stream on flat arrays, without a window, on one thread and on all cores.

On the larger grids, results varied by under 5% between runs. The smallest grid varied more, because its times are so short.

**Prototypes vs the game.** The prototypes do the same maths as the game, but they aren't wired into it. Once the changes are made in the game, the gains may be somewhat smaller or larger. Every number in this report is labelled either "game" (measured on the real code) or "prototype".

**Not the cause: the graphics driver.** The game uses ANGLE, a layer that translates OpenGL to Apple's Metal. Running the same benchmark with the Mac's native OpenGL gave almost identical times (drawing at 1024×576: 25.6 ms with ANGLE, 24.8 ms without). So the time is going into the game's own code, not into translation.

## What happens in one frame

While a simulation is running, `FreeplayMenu.render()` calls:

1. [`cfdSolver.doStep()`](core/src/main/java/io/github/wind_tunnel/Menus/FreeplayMenu.java#L50), which runs `collide()`, then `stream()`, then `bounce()`, each looping over every cell;
2. [`cfdSolver.render(sr)`](core/src/main/java/io/github/wind_tunnel/Menus/FreeplayMenu.java#L54), which loops over every cell again to draw it.

At 1024×576 there are 589,824 cells, and each holds 9 numbers (19 in 3D). Any extra work done per cell is multiplied by roughly 600,000 per frame, so small inefficiencies become large in total.

---

## Finding 1: each cell is drawn as a separate rectangle

**Where:** [`LatticeBoltzmannCFDSolver.render()`, line 441](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L441)

**What happens.** For each cell, `render()` picks a colour and calls `sr.rect(...)`. `ShapeRenderer` turns each rectangle into two triangles (6 corners). For each corner it stores an x, y, z position and a colour in a buffer in main memory. Every 833 rectangles the buffer fills up, so it's copied to the graphics card and drawn, and then it starts filling again.

**Why it's slow.** At 1024×576 that's every frame:
- 3.5 million corners worked out one at a time by the CPU;
- about **56 MB of corner data** copied to the graphics card (3.5 million × 16 bytes);
- about **700 separate draw commands**, each with its own setup cost.

All of that rebuilds a picture that is really just a 1024×576 grid of coloured squares. It's also repeated even while the simulation is paused and nothing has changed.

**Suggestion: draw the field as one texture.** Give each cell one pixel in a `Pixmap` that's exactly the size of the grid (1024×576). Upload that to a `Texture` once per frame and draw it stretched across the screen with "nearest" filtering. Nearest filtering makes each pixel a sharp square instead of blurring it, which is exactly what the rectangles produce now.

```java
// when the resolution changes (dispose the old ones first)
fieldPixmap = new Pixmap(nx, ny, Pixmap.Format.RGBA8888);
fieldTexture = new Texture(fieldPixmap);
fieldTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

// each frame, or only after a step
ByteBuffer pixels = fieldPixmap.getPixels().order(ByteOrder.BIG_ENDIAN); // bytes are R, G, B, A
for (int y=0; y<ny; y++) {
    for (int x=0; x<nx; x++) {
        pixels.putInt(((ny-1-y)*nx + x)*4, cellColour(x, y)); // a Pixmap's row 0 is the top of the image
    }
}
fieldTexture.draw(fieldPixmap, 0, 0); // one upload of about 2.4 MB
batch.begin();
batch.draw(fieldTexture, 0, 0, 1920, 1080); // one draw command
batch.end();
```

**Why this is faster.** The CPU writes one 4-byte colour per cell instead of six 16-byte corners. The upload is 2.4 MB instead of 56 MB. There's one draw command instead of 700, and the GPU does the stretching, which it's built for.

**Evidence (prototype, using the game's own colour choice for each cell):**

| Grid | Game: rectangles | Prototype: texture | Prototype: texture + flat arrays (see Finding 3) |
|---|---|---|---|
| 512×288 | 7.0 ms | 2.3 ms | about 1.3 ms |
| 1024×576 | 25.6 ms | 9.1 ms | about 3.4 ms |

**Same picture.** Under native OpenGL, a screenshot of the game's renderer and a screenshot of the texture prototype were **byte-for-byte identical**.

**Extra gains:**
- **When paused,** only rebuild the texture after a step, a barrier edit or a plot change. Otherwise just draw the existing one, which costs well under a millisecond.
- **The menus' UI** (buttons, text) can stay on `ShapeRenderer`. Draw the field with the `SpriteBatch` first, then the UI on top. This means `FreeplayMenu` and `LevelsMenu` would call the field drawing before `sr.begin()` rather than inside it.

## Finding 2: per-cell colour work that could be done once per frame

**Where:** [`calculateColourIndex()`, line 591](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L591), called from `render()`

**What happens.** For every cell, `calculateColourIndex()`:
- compares the plot setting as text (`settings.getPlot().equals("x velocity")`, and possibly `"y velocity"`);
- recomputes `contrast = 1/(3*flowSpeed)`, a division whose answer is the same for every cell;
- reads the cell's velocity through three levels of arrays.

`render()` also asks `settings.getSolver().equals("2D LBM")` again for every cell.

**Why it's slow.** None of these answers change during a frame, but they're recalculated about 600,000 times. Each is small on its own, but in a loop this size they add up. The three-level array lookup is the larger part; Finding 3 explains why.

**Suggestion:** before the loop, work out which plot is selected (preferably as an `enum`, see the coding standards review), the contrast, and the solver. Keep the 600 colours as packed `int`s (`Color.rgba8888(colour)`) instead of `Color` objects, so a cell's colour is a single array read.

**Evidence (prototype, 1024×576):** filling the texture with the game's current per-cell code took **7.7 ms**. Working these values out once and reading flat arrays took **2.0 ms**.

## Finding 3: the fluid is stored as millions of tiny arrays

**Where:** [`densities`, line 32](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L32), `cellAverageVelocities` and `barriers`

**What happens.** In Java, `float[][][][] densities` isn't one block of numbers. It's an array of references to arrays of references to arrays of references to small arrays of 9 floats. In 2D the z level has size 1, so there's a whole layer of one-element arrays that do nothing. At 1024×576:

| Array | Separate objects |
|---|---|
| `densities` | about 1.18 million |
| `cellAverageVelocities` | about 1.18 million |
| `barriers` | about 0.59 million |
| **Total** | **about 3 million** |

**Why it's slow:**
- **Three lookups to reach one number.** Reading `densities[x][y][z][d]` means following three references before getting to the data. Each one can be a "cache miss", where the CPU waits for main memory, about 100 times slower than its fast on-chip cache.
- **Neighbours are scattered in memory.** Streaming copies values between neighbouring cells, and those are separate objects that may sit far apart. The CPU can't use its fast bulk-copy instructions.
- **Memory overhead.** Each small array carries a 16-byte header, so in 2D about half the memory is overhead rather than fluid data.

**Suggestion: use flat arrays and compute the index yourself.** There are two sensible layouts:

```java
// layout A: one array per direction. Good for streaming, used in the 2D prototype
float[][] f = new float[9][nx*ny];        // value for direction d at (x, y): f[d][y*nx + x]

// layout B: one array, a cell's values next to each other. Good for collide, used in the 3D prototype
float[] f = new float[nx*ny*nz*19];       // value for direction d at (x, y, z): f[((z*ny + y)*nx + x)*19 + d]
```

**Why the layout matters:**
- **Collide** reads all directions of one cell, so it's fastest when they sit next to each other (layout B).
- **Stream** moves one direction across the whole grid, so it's fastest when each direction is one continuous block (layout A). Then a whole row can move with a single `System.arraycopy`, which the JVM turns into a very fast memory copy.

The measurements show this trade-off clearly in 3D.

**Evidence (milliseconds per step):**

| | Game today | Prototype, 1 thread |
|---|---|---|
| 2D collide 1024×576 (layout A, written out per direction) | 16.0 | **8.7** |
| 2D stream 1024×576 (layout A) | 12.3 | **2.0** |
| 3D collide 128×72×72, layout B | 39.7 | **33.7** |
| 3D collide 128×72×72, layout A with a loop over directions | 39.7 | 104.5 (slower!) |
| 3D stream 128×72×72, layout B | 52.6 | **23.8** |
| 3D stream 128×72×72, layout A | 52.6 | **2.6** |

**Recommendations by mode:**
- **2D:** use layout A with the collide equations written out per direction, as the game already does. It was faster for both collide and stream.
- **3D:** layout B was the best all-rounder measured. Layout A might win if 3D collide were also written out per direction instead of looping, but that wasn't measured.

**Also worth doing.** Stream into a second array and then swap the two ("double buffering"). The current in-place streaming only works because each loop runs in a carefully chosen direction. A second array removes that constraint, makes the code simpler to reason about, and is needed for Finding 4.

## Finding 4: the solver uses one of ten cores

**Where:** `collide()`, `stream()` and `bounce()`

**What happens.** The whole step runs on one thread, so one core does all the work while the other nine are idle.

**Why it can be split up:**
- **Collide** treats each cell completely independently: it only reads and writes that cell's own values.
- **Stream,** with double buffering (Finding 3), only reads the old array and writes the new one, so different rows or slices can't interfere.

That means the grid can be divided into rows (2D) or z-slices (3D), and each core can work on its own share.

```java
IntStream.range(0, ny).parallel().forEach(y -> collideRow(y)); // the JVM shares the rows between cores
```

**Prerequisite: change the temporary fields to local variables.** The per-cell working values (`cellDensity`, `omega`, `vx3`, `vxvx`, and so on, from [line 61](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L61)) are **fields**, so there is only one copy, shared by everything. Two threads using them at once would overwrite each other's values and give wrong results. As local variables, each thread gets its own copy. They also run faster even on one thread, because the CPU can keep locals in its registers instead of writing them back to memory.

**Evidence (prototype, all 10 cores):**

| | Game today | Flat, 1 thread | Flat, 10 threads |
|---|---|---|---|
| 2D collide 1024×576 | 16.0 | 8.7 | **1.4** |
| 2D stream 1024×576 | 12.3 | 2.0 | **0.5** |
| 3D collide 128×72×72 (layout B) | 39.7 | 33.7 | **4.8** |
| 3D stream 128×72×72 (layout B) | 52.6 | 23.8 | **3.4** |

**Why the speed-up is less than 10×.**
- **Uneven cores:** two of the cores are slower "efficiency" cores.
- **Memory limit:** all the cores share the same connection to main memory, and streaming mostly moves memory around.
- **Coordination cost:** dividing the work up has a small fixed overhead. At 128×72, 10 threads were actually *slower* than 1 (0.18 vs 0.13 ms). Only split the work up for larger grids.

## Finding 5: the 3D projection repeats identical work for every point

**Where:** [`ThreeDimensionalRenderer.rotate()`, line 51](core/src/main/java/io/github/wind_tunnel/ThreeDimensionalRenderer.java#L51), and [`pointProjection()`, lines 103 and 111](core/src/main/java/io/github/wind_tunnel/ThreeDimensionalRenderer.java#L103)

**What happens.** For each of the 46,405 points drawn at 128×72×72:
- `rotate()` calls `sin` and `cos` six times and builds three new 3×3 matrices (12 objects);
- `pointProjection()` calls `tan`, builds a new 4×4 matrix (5 objects), a new `Vector4` and a new `Vector3`.

That's **19 new objects** and 7 trigonometry calls per point, about **880,000 objects per frame**.

**Why it's slow.**
- **Repeated maths:** the rotation angles, field of view and camera distance are the same for every point in a frame, so all of that trigonometry and matrix building gives the same answer 46,405 times.
- **Garbage collection:** creating hundreds of thousands of short-lived objects every frame makes the garbage collector run constantly to clean them up.

**Suggestion.** At the start of the frame, compute the sines and cosines once. Combine the three rotation matrices into one (`R = Rz × Ry × Rx`) and work out the projection constants. Then each point needs only a few multiplications and additions, with no new objects:

```java
// once per frame
float r00 = cz*cy, r01 = cz*sy*sx - sz*cx, r02 = cz*sy*cx + sz*sx; // combined rotation, row 1
// ... rows 2 and 3, focal length f, aspect ratio, camera distance

// per point
float rx = r00*px + r01*py + r02*pz;
float ry = r10*px + r11*py + r12*pz;
float rz = r20*px + r21*py + r22*pz + cameraDistance;
float screenX = (f/aspect*rx/rz + 1)*halfWidth, screenY = (f*ry/rz + 1)*halfHeight;
```

This keeps your own matrix maths, which matters if it's meant to show understanding of 3D projection. It just does it once per frame instead of once per point. libGDX's `PerspectiveCamera.project()` does the same job, if using the library is preferred.

**Evidence (prototype, 128×72×72, same 46,405 points):** **5.7 ms → 1.1 ms**.

## Finding 6: the 3D view scans the whole volume and draws each point as a circle

**Where:** [`render()`, lines 421–446](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L421)

**What happens:**
- **Scanning:** to find the points to draw (the outer faces and any barriers), `render()` loops over **every** cell. At 128×72×72 that's 663,552 cells, and 93% of them are skipped.
- **Drawing:** each drawn point is a `sr.circle(..., 1)`. For a radius of 1, libGDX uses 6 segments, which is 6 triangles and **18 corners** for a dot only one or two pixels across.

**Why it's slow.** Nearly all the loop's work is checking cells that will never be drawn. The circles produce about 835,000 corners per frame, the same problem as Finding 1 on a smaller scale.

**Suggestion:**
- **Faces:** loop over the six faces of the box directly, rather than the whole volume.
- **Barriers:** keep a list of barrier cells (see Finding 7) and draw those as well.
- **Dots:** draw each point as a single pixel (`ShapeRenderer.ShapeType.Point`, or a 1-pixel `rect`) instead of a circle.

**Evidence:** not prototyped. After the projection (Finding 5), about **8 ms** of the 13.8 ms 3D drawing time is left, which is the scan and the circles together.

## Finding 7: bounce and `zeroBarriers` check every cell to find the barriers

**Where:** [`bounce()`, line 385](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L385), and [`zeroBarriers()`, line 654](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L654)

**What happens.** Every step, both methods loop over the whole grid and only do anything at barrier cells, which are usually a few percent of the grid.

**Why it's slow.** It's two extra passes over all 590,000 cells per step, almost all of which do nothing.

**Suggestion.** Keep a list of barrier cell indices. Add to it in `addBarrier()`, remove from it in `removeBarrier()`, and rebuild it when the resolution changes. Then `bounce()` and `zeroBarriers()` only visit cells that are actually barriers.

**Evidence:** the game's `bounce()` (which includes `zeroBarriers()`) takes **2.6 ms** at 1024×576. Not prototyped. With a list, the cost depends on the size of the obstacle rather than the grid.

## Finding 8: the window title is set every frame

**Where:** [`Main.render()`, line 39](core/src/main/java/io/github/wind_tunnel/Main.java#L39)

**What happens.** Every frame, the game builds a new text string, asks the monitor for its refresh rate, and sets the window title.

**Why it's slow.** Setting a window title goes through the operating system's window manager (on macOS, AppKit), which isn't designed to be called 60+ times a second. And the FPS figure it shows only changes once a second anyway.

**Suggestion:** update the title only when the FPS value changes, or once a second.

**Evidence (game):** **0.34 ms per frame**. That's small, but it's 2% of a 60 Hz frame, gone for nothing, and the fix is a couple of lines.

## Finding 9: one simulation step per drawn frame

**Where:** [`FreeplayMenu.render()`, line 50](core/src/main/java/io/github/wind_tunnel/Menus/FreeplayMenu.java#L50) (`LevelsMenu` does the same)

**What happens.** Exactly one step runs per frame.

**Why it matters.** The speed of the fluid on screen depends on the frame rate. At 128×72 the flow advances 60 or more steps a second. At 1024×576 it advances about 18, so the high-resolution simulation looks sluggish on top of the choppy frames.

**Suggestion.** Once the step is fast, run several steps per frame at low resolutions, or aim for a fixed number of steps per second whatever the frame rate. Running the solver on a separate thread is also possible, but more complex: the drawing must never read a half-finished step, which needs a second copy of the data. This is a design choice rather than a fix.

---

## Side finding: the window doesn't fit this laptop's screen

The game asks for a 1920×1080 window ([`Lwjgl3Launcher`](lwjgl3/src/main/java/io/github/wind_tunnel/lwjgl3/Lwjgl3Launcher.java#L31)), and its drawing code assumes that size (for example `1920/resolution` in `render()`). The MacBook's built-in screen gives the window **1512×842**, so about a third of the field and parts of the menus are off-screen. Those cells are still calculated and drawn every frame.

Using a libGDX `FitViewport(1920, 1080)` would scale everything to fit any window, and avoid drawing what can't be seen. This is the same viewport change suggested in the coding standards review.

## Likely result if everything is applied

| | Today (game) | After (from prototype figures) |
|---|---|---|
| 2D 1024×576: step | 30.8 ms | about 2.4 ms (collide 1.4 + stream 0.5 + bounce with a barrier list, estimated under 0.5) |
| 2D 1024×576: drawing | 25.6 ms | about 3.4 ms (2.0 colour fill + 1.4 upload and draw), almost nothing while paused |
| **2D 1024×576: frame** | **56 ms (about 18 FPS)** | **about 6 ms (60+ FPS)** |
| 3D 128×72×72: step | 93 ms | about 9 ms (collide 4.8 + stream 3.4 + bounce 1.0) |
| 3D 128×72×72: drawing | 13.8 ms | 1.1 ms projection + the scan and points from Finding 6 (not measured) |

These figures are added up from separate prototypes, not measured on a finished game, so the real result will differ somewhat. Even if the real times turn out twice as long as these figures (about 12 ms), high-resolution 2D would still run at 60 FPS.

## Suggested order

| Order | Change | Effort | Gain | Risk |
|---|---|---|---|---|
| 1 | Finding 8: title once a second | a few minutes | small | none |
| 2 | Finding 1: draw the 2D field as a texture | a few hours | large (drawing 3×, up to 7× with Finding 3) | low: same picture, easy to compare |
| 3 | Finding 2: per-frame values outside the loop | under an hour | moderate | low |
| 4 | Finding 5: 3D projection once per frame | an hour or two | 5× on projection | low: compare a screenshot before and after |
| 5 | Finding 4 prerequisite: temporaries as local variables | under an hour | small on its own | low, and needed for 6 |
| 6 | Findings 3 and 4: flat arrays, double buffering, all cores | a day or more | very large (step 10× or more) | medium: touches every solver method. Rerun the mass and momentum checks from the bug-fix commit afterwards |
| 7 | Findings 6 and 7: face loop, point drawing, barrier list | a few hours | moderate | low |
| 8 | Finding 9 and the viewport | design decisions | smoother feel, fits any screen | low |

Steps 1–4 are independent of each other and together remove most of the 2D drawing cost. Step 6 is the big one for the simulation itself, and it's easiest after step 5.

## Reproducing the measurements

The two benchmark programs (`PerfBench.java` and `SolverBench.java`) and the bug-fix checks from the previous commit currently live outside the repository. If you'd like to rerun them, or turn them into a `benchmarks/` folder or unit tests, ask Dale and they can be added to the branch.
