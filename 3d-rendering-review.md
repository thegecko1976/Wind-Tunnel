# 3D review: what stopped the 3D mode working fully, and the fixes

**Branch:** `refactor/menu-navigator-objects`, the changes made after commit `6628c70`
**Made by:** Dale, using Claude Code (an AI coding assistant), on 31 October 2026
**Files changed:**
- [`LatticeBoltzmannCFDSolver.java`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java)
- [`ShapeDrawer.java`](core/src/main/java/io/github/wind_tunnel/ShapeDrawer.java)
- [`ImportFile.java`](core/src/main/java/io/github/wind_tunnel/ImportFile.java)
- [`Levels.java`](core/src/main/java/io/github/wind_tunnel/Levels.java)
- [`InputHandler.java`](core/src/main/java/io/github/wind_tunnel/InputHandler.java)
- [`Settings.java`](core/src/main/java/io/github/wind_tunnel/Settings.java)

## Summary

The 3D simulation itself (collide, stream and bounce) was already working after the earlier fixes. What stopped 3D mode working *fully* was everything around it:
- **What you could see:** the default view showed no flow, and three of the five plots drew nothing.
- **What you could build:** models were hollow, and the level 11 sphere could be erased.
- **How you interact:** clicks did nothing at first, trackpad scrolling was ignored, and the mouse picked the wrong cell.
- **Which sizes you could choose:** three of the 3D resolutions crashed the game.

All eight problems below were shown happening on the code before this change, then shown fixed:

| # | Problem | What you'd see before | After |
|---|---|---|---|
| 1 | The default 3D view shows no flow | A box of one flat colour, whatever the fluid is doing | The selected plane is drawn through the middle, showing the flow |
| 2 | 3D models are hollow wireframes | Fluid flows straight through imported models and the level 11 sphere | Models are solid |
| 3 | The level 11 sphere isn't fixed | The player can erase it and get extra barriers | It's fixed and drawn grey |
| 4 | The starting plane is the front edge | Clicking to draw barriers does nothing until you scroll | Starts on the middle plane |
| 5 | Trackpad scrolling is ignored | Gentle two-finger scrolling never changes the plane | Small scrolls add up |
| 6 | The mouse assumes a 1080-pixel-high window | On smaller screens, barriers appear far from the cursor (2D too) | Uses the real window height |
| 7 | 3D resolutions went up to 1024×576×576 | Choosing 512×288 or larger in 3D crashes the game | 3D stops at 256×144×144 |
| 8 | Speed, density and curl plots aren't drawn | Picking them shows a solid blue screen (2D too) | All five plots work |

**Nothing else changed:**
- **Fluid results:** in every 2D and 3D test except level 11 (whose sphere is now solid), the simulation gives bit-for-bit the same numbers as before.
- **2D pictures:** x velocity, y velocity and the 2D levels look exactly the same.

---

## 1. The default 3D view showed no flow

**What you'd see.** In 3D Freeplay, with "only render selected plane" off, the box is drawn in a single colour, even with an obstacle in the flow.

**Why.** The default view draws only the six outside faces of the grid and the barriers. Every one of those face cells is a **boundary cell**: at the end of each `stream()` the boundary conditions reset them to the incoming flow (the inlet, outlet, top, bottom, front and back). So their colour can never show what the fluid is doing.

**Evidence.** After 200 steps with an obstacle, the x velocity on the faces only varied between 0.0966 and 0.1011, while on the middle z plane it varied between −0.0115 and 0.1334. Everything interesting was hidden inside the box.

**Fix** ([`renderPoints`, line 560](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L560)). The default view now draws three things in order: the faces (to show the box), then **the selected z plane** (to show the flow inside), then the barriers. Scrolling moves the plane through the tunnel, and it also shows you which plane clicks will add barriers to. "Only render selected plane" still shows the plane on its own.

## 2. 3D models were hollow wireframes

**What you'd see.** An imported `.obj` model, or the level 11 sphere, looked like a white wire frame, and the fluid flowed straight through it.

**Why.** `loadOBJModel()` only drew the **edges** of each triangle (`shapeDrawer.line(...)`). The flat faces between the edges were left empty, so the "object" was a cage of thin lines with fluid all around and inside it.

**Evidence** (128×72×72, 60 steps):

| | Barrier cells | Fluid cells near the centre (of 4,169) | Speed of fluid there |
|---|---|---|---|
| icosphere before | 1,664 | 4,169 (all of them) | 0.078 (free stream 0.100) |
| icosphere after | 24,031 | 0 | – |
| cube before | 1,046 | 4,169 (all of them) | 0.099 |
| cube after | 50,653 | 0 | – |

These numbers check out geometrically. A sphere of radius 18 cells has a volume of about 24,400 cells, and the cube is 37×37×37 = 50,653 cells.

**Fix**, in two steps:
1. **Fill the faces.** A new [`ShapeDrawer.triangle()`](core/src/main/java/io/github/wind_tunnel/ShapeDrawer.java#L114) fills the whole surface of each triangle with barriers. It steps across the triangle in quarter-cell steps (using `a + s(b−a) + t(c−a)`), so no cell the surface passes through is missed. Faces with more than three corners are split into triangles.
2. **Fill the inside.** A new [`fillEnclosedSpaces()`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L856) does a **flood fill**, a breadth-first search like the ones on the A-level syllabus. It starts from every fluid cell on the edge of the tunnel and spreads through fluid cells that share a face. Any fluid cell it can't reach is sealed inside the model, so it becomes a barrier. This turns the closed surface into a solid object.

## 3. The level 11 sphere wasn't fixed

**What you'd see.** In level 11 you could erase parts of the sphere. Each erased cell *gave you* an extra barrier (the count went 250 → 251), and the sphere was drawn white, like the player's own barriers.

**Why.** [`Levels.java` line 146](core/src/main/java/io/github/wind_tunnel/Levels.java#L146) loaded the sphere with `fixedBarrier = false`, unlike the 2D levels, which all pass `true`.

**Fix:**
- **Fixed:** the sphere is now loaded as fixed. All 24,031 of its cells are fixed, and erasing one is refused.
- **Grey in 3D:** in 2D, fixed level barriers are drawn black on the coloured field. In 3D the background is black, so black dots would be invisible. Fixed barriers are therefore drawn **grey** in 3D ([line 586](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L586)), which also tells them apart from the player's white barriers.

## 4. The starting plane was the front edge

**What you'd see.** When 3D opened, clicking to draw barriers did nothing until you scrolled at least once.

**Why.** `Settings.reset()` sets the selected plane to z = 0, the front face. `addBarrier()` refuses barriers on the outside of the grid (they would break the boundary conditions), so every click on plane 0 was silently ignored. The evidence: a click in the middle of the screen added 0 barrier cells.

**Fix** ([line 151](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L151)). When the 3D fluid is set up and the selected plane is on the outside (or beyond the grid after a resolution change), it moves to the middle plane. The same click now adds 29 barrier cells, one brush-full.

## 5. Trackpad scrolling was ignored

**What you'd see.** On a MacBook trackpad, scrolling slowly never changed the selected plane. Only a hard flick did.

**Why.** A mouse wheel sends scroll amounts in whole steps (1, 2, …), but a trackpad sends many small fractions (like 0.3). `InputHandler` did `moveSelectedZPlane((int) -amountY)`, and converting 0.3 to an `int` gives 0. The evidence: ten scroll events of 0.4 left the plane where it was.

**Fix** ([`InputHandler.java` line 19](core/src/main/java/io/github/wind_tunnel/InputHandler.java#L19)). The amounts are added up, and the plane moves each time they make a whole step. The same ten events now move it 4 planes. A mouse wheel behaves exactly as before.

## 6. The mouse assumed a 1080-pixel-high window

**What you'd see.** On a screen where the game window is less than 1080 pixels high, barriers appear far from where you click. On this MacBook the window is 846 pixels high.

**Why.** `drawBarriers()` turned the mouse position into drawing coordinates with `mouse.y = 1080 - Gdx.input.getY()`. The mouse is measured from the top of the window and the drawing from the bottom, so the flip has to use the **real** window height. Using 1080 shifts every click by the difference.

**Evidence** (a window 846 pixels high):
- **3D:** with the mouse placed exactly over cell (90, 50, 36), the game picked **(85, 127, 36)**.
- **2D:** over cell (40, 20), it picked **(40, 36)**, 16 cells too high.

**Fix** ([line 600](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L600)): `Gdx.graphics.getHeight() - Gdx.input.getY()`. Both cases now pick the exact cell. A 1080-high window works the same as before.

## 7. 3D resolutions went up to 1024×576×576

**What you'd see.** In Settings with the 3D solver, you could pick 512×288×288 or 1024×576×576, or switch to 3D while the 2D resolution was that high. The game then crashed on returning to the simulation.

**Why.** Memory grows with the *cube* of the size. Each 3D cell needs about 170 bytes (19 densities in two arrays, plus velocity, density and barrier).

| 3D size | Cells | Memory needed | Result |
|---|---|---|---|
| 128×72×72 | 0.66 million | about 0.1 GB | fine |
| 256×144×144 | 5.3 million | about 0.9 GB | fine |
| 512×288×288 | 42.5 million | about 7 GB | **`OutOfMemoryError`** with a 4 GB Java heap |
| 1024×576×576 | 340 million | about 57 GB | **crash**: 340 million × 19 is bigger than the largest possible Java array |

Java's default maximum memory is a quarter of the computer's RAM, so on most laptops even 512×288×288 can't work. (This was already broken before the performance changes, which crashed with `OutOfMemoryError` instead.)

**Fix:**
- **Limit:** [`Settings.java`](core/src/main/java/io/github/wind_tunnel/Settings.java#L21) now has `largest3DResolution`, set to 256×144×144. In 3D, the resolution arrows cycle 32×18×18 → … → 256×144×144 → back to 32×18×18 ([line 117](core/src/main/java/io/github/wind_tunnel/Settings.java#L117)).
- **Switching to 3D:** switching from 2D to 3D at a larger 2D size uses 256×144×144 ([line 88](core/src/main/java/io/github/wind_tunnel/Settings.java#L88)).
- **Speed at the limit:** 256×144×144 works but is slow, about 88 ms per frame (about 11 frames per second) on an M1 Pro. 128×72×72 is about 14 ms.

## 8. Speed, density and curl plots weren't drawn

**What you'd see.** Choosing "speed", "density" or "curl" in Settings turned the whole simulation one flat blue, in 2D and 3D.

**Why.** `calculateColourIndex()` only had code for x velocity and y velocity. The other three were commented out, so they fell through to colour 0 (blue).

**Fix** ([`colourIndex`, line 773](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L773)). All five plots are now drawn. They use the same contrast as before (`1/(3 × flow speed)`), so the incoming flow looks the same at any flow speed:

| Plot | What it shows | Colour scale |
|---|---|---|
| x velocity, y velocity | unchanged | still fluid in the middle (green) |
| speed | how fast the fluid moves, `√(ux² + uy² + uz²)` | still = blue, incoming flow = middle, twice as fast = red |
| density | `ρ − 1`, how compressed the fluid is (like pressure) | rest density in the middle. Density changes grow with the *square* of the speed, so contrast is squared here |
| curl | how fast the fluid spins about the z axis, `(uy right − uy left) − (ux above − ux below)` | no spin in the middle, anticlockwise red, clockwise blue |

**Supporting changes:**
- **Density:** `collide()` now also stores each cell's total density, in `cellDensities` ([line 254](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L254)).
- **Curl:** [`curl()`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L792) reads the velocities of a cell's four neighbours. Barriers count as still, and edge cells as not spinning.

The pictures behave as the physics says they should:
- **Speed:** the fluid is fastest over the top and bottom of an obstacle and nearly still just in front of and behind it.
- **Density:** pressure builds up on the upstream side and drops where the flow speeds up.
- **Curl:** two thin layers of fluid spin in opposite directions along the obstacle's surface, which is where the wake's vortices come from.

The multipliers (1.5, 0.6 and 1.5) are chosen so a typical flow fills the colour scale. They're easy to change if you'd like stronger or softer colours.

---

## How it was tested

1. **Each problem, before and after.** A small program ran the game's real classes without a window, with a pretend window 1512×846 pixels (this MacBook's size). It checked every item above on the code before the change and after it. The numbers in each section come from that program.
2. **Nothing else changed.** The eight fluid scenarios from the performance work were rerun:
   - **Identical:** 2D with an obstacle, barrier edits, resolution up and down, level 3, 3D with an obstacle, and a 3D resolution change all still give **bit-for-bit the same numbers** as the original code.
   - **Different:** only level 11 differs, because its sphere is now solid.
3. **Earlier fixes still hold.** The eight bug-fix checks from commit `608cc9c` still pass, including mass and momentum conservation in 2D and 3D.
4. **Pictures.** Screenshots before and after:
   - **Unchanged:** 2D x velocity, 2D y velocity and level 3 are **byte-for-byte identical**.
   - **Changed as intended:** speed, density, curl, the 3D view and level 11 now show what's described above. Each was checked by eye.
5. **Speed.** Adding the plane to the 3D view takes 3D drawing from 4.4 ms to 4.8 ms at 128×72×72. The simulation step is unchanged.
6. **The game itself.** The built game started and ran for 15 seconds with no errors.

**Not tested by hand:** nobody played 3D Freeplay or level 11 with the mouse and keyboard.

One thing caught during testing that is **not** a game bug: screenshots taken while the game window was partly covered by another window came back with black patches. OpenGL doesn't promise to keep pixels you can't see, so those screenshots were retaken with the window uncovered.

## Please try in the game

1. 3D Freeplay at 128×72×72:
   - draw some barriers by clicking (they should appear under the cursor straight away, on the middle plane);
   - scroll with a trackpad and a mouse wheel to move the plane;
   - press run and watch the flow on the plane.
2. Switch between all five plots in 2D and 3D.
3. Import `cube.obj` and `icosphere.obj`. They should be solid, and the flow should go around them.
4. Level 11: the sphere should be grey, and right-clicking it shouldn't erase it.
5. In Settings with the 3D solver, cycle the resolution. It should never go past 256×144×144.

## Not changed, and why

- **If the window is resized, the 3D view doesn't follow.** `ThreeDimensionalRenderer` records the window size once when the game starts ([line 40](core/src/main/java/io/github/wind_tunnel/ThreeDimensionalRenderer.java#L40)), and so do the menus' button positions (`MenuUtil`). That's fine if the window isn't resized, as tested on this Mac, but dragging the window to a new size would put the 3D view and the buttons in the wrong place. The proper fix is a libGDX `Viewport` for the whole game (the side finding in [`performance-review.md`](performance-review.md)), which changes every menu, so it's a separate piece of work. A simple guard would be `configuration.setResizable(false)` in `Lwjgl3Launcher`.
- **Dots aren't sorted by depth.** Points are drawn in a fixed order (faces, then the plane, then barriers), not back to front. So a far dot can be drawn over a near one where they overlap. With the plane and barriers drawn last this is hard to notice. Proper depth sorting would cost a few milliseconds a frame.
- **Rotation and zoom speed depend on the frame rate.** W/A/S/D turn the view 1° per frame and the arrow keys zoom 1 unit per frame ([`Keybinds.java`](core/src/main/java/io/github/wind_tunnel/Keybinds.java#L36)). Now that frames are much faster, the view turns faster than before, for example twice as fast at 120 frames per second as at 60. Multiplying by `Gdx.graphics.getDeltaTime()` (the time since the last frame) would make them the same speed on every computer.
- **256×144×144 is slow** (about 11 frames per second). If you'd prefer 3D to stay smooth, set `largest3DResolution` to 2 (128×72×72).
