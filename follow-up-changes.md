# Follow-up changes: window resizing, 3D depth order, view speed and the 3D size limit

**Branch:** `refactor/menu-navigator-objects`
**Commits:** one per item, in this order:

| Commit | Change |
|---|---|
| `984d901` | Scale the game to any window size with a viewport |
| `12e6101` | Draw 3D points farthest first so nearer points cover farther ones |
| `ff00924` | Turn and zoom the 3D view at the same speed at any frame rate |
| `6a148b1` | Limit 3D to 128x72x72 so it stays smooth |

**Made by:** Dale, using Claude Code (an AI coding assistant), on 31 October 2026

These four changes deal with the items left in the "Not changed" section at the end of [`3d-rendering-review.md`](3d-rendering-review.md). Each section explains what you would have seen, why it happened, what was changed, and how we know it works.

## Summary

| # | Item | Before | After |
|---|---|---|---|
| 1 | **Window size** | The game only fitted a 1920×1080 window. On smaller screens parts were cut off, and resizing the window squashed the picture and made clicks miss the buttons | The game scales to fit any window and keeps its shape, with thin black bars if needed. Clicks land exactly where you point |
| 2 | **3D depth order** | 3D dots were drawn in a fixed order, so far dots could be drawn over near ones | Dots are drawn farthest first, so nearer dots cover farther ones |
| 3 | **Turn and zoom speed** | Holding a key turned the view 30° a second at 30 frames per second but 120° a second at 120 | 60° a second, and 60 zoom units a second, on any computer |
| 4 | **3D size limit** | 3D went up to 256×144×144, which only ran at about 10 frames a second | 3D stops at 128×72×72, which runs at about 60 frames a second |

**Nothing else changed:** the fluid simulation gives exactly the same numbers as before, and all the checks from the earlier reports still pass (see "How it was tested").

---

## 1. The game now fits any window

### What you saw
- **Smaller screens:** on a laptop whose screen is smaller than 1920×1080 (this MacBook gives the game a 1512×842 window), the title, the quit button, the settings button and the back button were off the edge of the window. Freeplay showed only part of the tunnel.
- **Resizing:** if you dragged the window to a new size, the picture was squashed into the new shape, but the game still worked out mouse clicks for the old size. Clicks then missed the buttons, and barriers appeared away from the cursor.

### Why it happened
Every position in the game is written for a 1920×1080 screen: the quit button at x = 1862.5, the title at y = 880, and the 2D tunnel 1920 units wide. Three things went wrong when the window wasn't exactly that size:
1. **Drawing:** libGDX starts with **one drawing unit = one window pixel**. Anything past the window's edge was simply off-screen.
2. **Resizing:** when the window changed size, the graphics card started drawing into the new size, but the game's drawing tools (`ShapeRenderer` and `SpriteBatch`) still used the sizes from start-up. So everything was stretched or squashed.
3. **The mouse:** it was read in window pixels, measured from the top of the window. That only matched the drawing when the window was exactly the start-up size.

### What changed
A new class, [`GameView`](core/src/main/java/io/github/wind_tunnel/GameView.java), keeps the game's own **game coordinates** (always 1920×1080, with y = 0 at the bottom) separate from the window's pixels. It uses libGDX's `FitViewport`, which:
- **scales** the 1920×1080 game to be as big as possible while still fitting the window;
- **centres** it, adding thin black bars at the sides (or top and bottom) if the window isn't 16:9, so circles stay round;
- **converts** the mouse from window pixels back into game coordinates (`unproject`), taking the scaling, the bars and the flipped y direction into account.

```java
public class GameView {
    public static final float WIDTH = 1920;
    public static final float HEIGHT = 1080;
    private FitViewport viewport = new FitViewport(WIDTH, HEIGHT);

    public void resize(int width, int height) {viewport.update(width, height, true);}  // the window changed size

    public void apply(ShapeRenderer sr, SpriteBatch batch) {                           // draw in game coordinates
        viewport.apply();
        sr.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
    }

    public Vector2 getMouse() {                                                        // the mouse in game coordinates
        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        return viewport.unproject(mouse);
    }
}
```

The rest of the game uses it in four places:
- **[`Main`](core/src/main/java/io/github/wind_tunnel/Main.java#L42):** calls `GameView.getInstance().apply(sr, batch)` at the start of every frame, and passes window resizes on in a new [`resize()`](core/src/main/java/io/github/wind_tunnel/Main.java#L51) method (libGDX calls this whenever the window changes size).
- **Button clicks:** [`MenuUtil.isButtonClicked()`](core/src/main/java/io/github/wind_tunnel/MenuUtil.java#L78) now reads the mouse through `GameView.getMouse()`. The buttons are in game coordinates, so the click is now in the same coordinates as the button.
- **Drawing barriers:** [`drawBarriers()`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L647) does the same, for 2D cells and 3D plane picking. It replaces the earlier "window height minus mouse y" fix, which only handled windows of a different height, not resizing or scaling.
- **3D projection:** [`ThreeDimensionalRenderer`](core/src/main/java/io/github/wind_tunnel/ThreeDimensionalRenderer.java#L39) projects 3D points into game coordinates (1920×1080), not the window size it saw at start-up. So the 3D view scales with everything else.

None of the menus' button positions needed changing, because they were already in 1920×1080 coordinates. On an exactly 1920×1080 window, one game unit is still one pixel, so the game looks exactly as it did.

### How we know it works
- **Screenshots of the real game.** These were taken in this MacBook's 1512×842 window, then again after resizing it to 1280×720:
  - **Before:** the title, quit, settings and back buttons were missing at start-up, and after resizing the menu was squashed and cut off.
  - **After:** everything is visible in both window sizes, scaled down, with thin black bars where the window isn't quite 16:9.
- **Mouse test.** For six window sizes, the mouse was placed exactly where a known point, 2D cell or 3D cell is drawn on screen, using the `FitViewport` maths worked out separately. The game then had to read back the same point or cell.

| Window | Game points read back within | 2D cell (40, 20) | 3D cell (90, 50, 36) |
|---|---|---|---|
| 1920×1080 | 0.5 pixel | (40, 20) | (90, 50, 36) |
| 1512×846 (this laptop) | 0.3 pixel | (40, 20) | (90, 50, 36) |
| 1280×720 | 0.3 pixel | (40, 20) | (90, 50, 36) |
| 2560×1440 | 0.3 pixel | (40, 20) | (90, 50, 36) |
| 1000×1000 (bars top and bottom) | 1.0 pixel | (40, 20) | (90, 50, 36) |
| 1600×600 (bars at the sides) | 1.1 pixel | (40, 20) | (90, 50, 36) |

The small differences in the second column are just the mouse being placed to the nearest whole pixel.

### If you change this code later
- Put any new buttons or text in **1920×1080 game coordinates**, like the existing ones.
- Read the mouse with `GameView.getInstance().getMouse()`, **never** `Gdx.input.getX()` / `getY()` directly. Those are window pixels and will be wrong whenever the window isn't 1920×1080.
- If you make a new `ShapeRenderer` or `SpriteBatch`, call `GameView.getInstance().apply(...)` on it before drawing.

---

## 2. 3D dots are drawn farthest first

### What you saw
In 3D, a dot farther away could be drawn on top of a nearer one. For example, in level 11 the grey sphere was pasted over the front face of the box, even though the front face is between the sphere and the camera.

### Why it happened
`ShapeRenderer` draws flat 2D shapes. It doesn't know how far away each dot is, so whatever is drawn **last** ends up on top. The 3D view always drew in the same order: faces, then the selected plane, then barriers. That meant barriers were always on top, and the back face was drawn after the front face, whatever angle the camera was at.

### What changed
The 3D view now uses the **painter's algorithm**, the way a painter does a landscape: paint the farthest things first and let nearer things cover them. In [`renderPoints()`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L593):
1. **Collect:** [`addPoint()`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L606) works out each dot's screen position, its colour and its **depth** (how far in front of the camera it is), and saves them instead of drawing straight away.
2. **Sort:** [`sortPointsByDepth()`](core/src/main/java/io/github/wind_tunnel/LatticeBoltzmannCFDSolver.java#L632) sorts all the dots by depth.
3. **Draw:** the dots are drawn from the farthest to the nearest.

**How the sort works.** Sorting objects is slow when there are 60,000 of them every frame. So each dot's depth and its number are packed into one `long`, which Java can sort very quickly:

```java
// every depth is positive, and the bits of a positive float sort in the same order as its value, so the depth goes
// in the top half of a long and the point's number in the bottom half: sorting the longs then sorts the points by depth
depthOrder[p] = ((long) Float.floatToIntBits(pointDepth[p]) << 32) | p;
...
Arrays.parallelSort(depthOrder, 0, pointCount);   // uses all the CPU cores for large numbers of points
```

After sorting, `(int) depthOrder[k]` gives back the number of the k-th nearest dot. This works because a positive `float` is stored with its exponent before its fraction, so a bigger number always has bigger bits.

### How we know it works
- **The sort itself.** A test called the game's own `sortPointsByDepth()` on random depths for 1, 2, 1,000, 60,000 and 250,000 points, including two points at exactly the same depth. Every time, every point came out exactly once, nearest first.
- **Level 11 screenshots.** Before, the sphere was pasted over the front face. After, the front face's dots are drawn over the sphere, so it correctly sits inside the tunnel.
- **Cost.** Drawing at 128×72×72 went from 4.9 ms to 6.6 ms a frame, so a whole frame takes about 16 ms. That's still within the 16.7 ms that 60 frames a second allows.

### Good to know
- **Dots, not solid surfaces.** The faces are grids of dots with gaps between them, so the selected plane and any model inside still show through.
- **Why not the graphics card's depth buffer?** It can do this sorting itself, but only for real 3D shapes drawn with a 3D camera. That would mean rewriting how the 3D view draws, which is much bigger than this change.

---

## 3. Turning and zooming at the same speed on every computer

### What you saw
Holding W, A, S or D to turn the view, or the arrow keys to zoom, moved faster on fast computers and slower on slow ones. After the performance changes, the game runs at many more frames a second, so the view turned much faster than before.

### Why it happened
The keys moved the view by a fixed amount **every frame**: 1° of turn and 1 unit of zoom. The number of frames per second (the **frame rate**) depends on the computer, the screen and the resolution, so the speed did too:

| Frame rate | Turn after holding W for 1 second | Zoom after holding Up for 1 second (from 100) |
|---|---|---|
| 30 | 30° | 70 |
| 60 | 60° | 40 |
| 120 | 120° | 1 (as close as allowed) |

### What changed
[`Keybinds.checkForKeyPresses()`](core/src/main/java/io/github/wind_tunnel/Keybinds.java#L37) now multiplies the movement by how long the last frame took. That's called **delta time**, and libGDX provides it as `Gdx.graphics.getDeltaTime()`:

```java
float seconds = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);   // how long the last frame took
float turn = (float) (Math.PI/180)*60*seconds;                 // 60 degrees a second
float zoom = 60*seconds;                                       // 60 units a second
```

- **Same speed at any frame rate:** at 30 frames a second each frame turns 2°, and at 120 each frame turns 0.5°. Both add up to 60° every second, which is the old speed at 60 frames a second.
- **The 0.1-second cap:** if one frame is slow (for example while a level loads), the view doesn't suddenly jump.
- **Fractional zoom:** zooming now moves by fractions of a unit each frame, so the camera distance in [`Settings`](core/src/main/java/io/github/wind_tunnel/Settings.java#L29) is now a `float` instead of an `int`. It still can't get closer than 1.

### How we know it works
| Frame rate | Turn after 1 second | Zoom after 1 second (from 100) |
|---|---|---|
| 30 | 60° | 40 |
| 60 | 60° | 40 |
| 120 | 60° | 40 |

Screenshots of the 3D view before and after the `float` change were byte-for-byte identical, so the picture itself didn't change.

### If you change this code later
Anything that moves a set amount while a key is held should be multiplied by `Gdx.graphics.getDeltaTime()`. One-off actions (like pressing Space to pause) don't need it.

---

## 4. 3D stops at 128×72×72

### What you saw
In 3D, the resolution arrows went up to 256×144×144, which worked but ran at only about 10 frames a second (about 95 ms a frame, including the depth sorting from item 2).

### Why it happened
A 3D grid has 8 times as many cells each time its size doubles, because width, height and depth all double. 256×144×144 has about 5.3 million cells against 0.66 million for 128×72×72. So each simulation step took about 68 ms instead of about 9.5 ms, on top of drawing about 4 times as many dots.

### What changed
[`Settings.largest3DResolution`](core/src/main/java/io/github/wind_tunnel/Settings.java#L22) is now 2, meaning 128×72×72:
- **The arrows:** in 3D they go 32×18×18 → 64×36×36 → 128×72×72 → back to 32×18×18.
- **Switching to 3D:** switching from 2D at a larger size starts 3D at 128×72×72.

### How we know it works
On the old build the arrows went up from 128×72×72 to 256×144×144. On the new build they wrap to 32×18×18, going down from 32×18×18 wraps to 128×72×72, and switching from 2D 256×144 to 3D gives 128×72×72.

### To change it back
Set `largest3DResolution` to 3 to allow 256×144×144 again, for example on a faster computer. The comment above it in `Settings.java` explains the trade-off.

---

## How it was tested

Each item was checked on its own before its commit (the evidence is in each section). After all four, everything was checked again on the final build:

1. **Fluid values:** the eight scenarios from the performance work were rerun and compared, bit for bit, with the original code from before the performance changes:
   - **Identical:** seven of them (2D, 3D, barrier edits, resolution changes, level 3).
   - **Different:** only level 11, because its sphere became solid in the 3D review. None of these four changes affect the simulation.
2. **Earlier bug fixes:** all eight checks from commit `608cc9c` still pass, including mass and momentum conservation in 2D and 3D.
3. **3D checks:** the checks from the 3D review still pass:
   - **Models:** the sphere and cube are solid, with 24,031 and 50,653 cells and no fluid inside.
   - **Level 11:** the sphere is fixed and can't be erased.
   - **Plane:** 3D starts on the middle plane, and trackpad scrolling moves it.
4. **The three new checks:** the mouse test at six window sizes, the depth sort test, and the turn and zoom test all pass, as shown in sections 1–3.
5. **The real game:** started and run with screenshots of the main menu and Freeplay, before and after resizing the window. Everything was visible and correctly placed.
6. **Speed** (Apple M1 Pro):
   - **2D:** unchanged by the viewport. 1024×576 still takes about 5.3 ms a frame.
   - **3D at 128×72×72:** about 16 ms a frame, including the depth sorting.

**Not tested:** nobody has yet played with the mouse and keyboard by hand, or run it on Windows. `FitViewport` is standard libGDX and should behave the same there.

## Please try in the game

1. Start the game. On a laptop the whole main menu should fit, with the title, quit (top right), settings (bottom right) and about (bottom left).
2. Drag the window to a different size and shape. The game should scale to fit, with black bars if the shape isn't 16:9. Click a few buttons to check they respond where you click.
3. In 2D Freeplay, in a resized window, draw barriers. They should appear exactly under the cursor.
4. In 3D Freeplay, hold W, A, S and D to turn, and Up and Down to zoom. The speed should feel the same as before at 60 frames a second, however fast your computer is.
5. Turn the 3D view so you look through the front of the box at a barrier or level 11's sphere. The front face's dots should be drawn over it.
6. In 3D settings, the resolution arrows should never go past 128×72×72.

## Words used in this report

| Word | Meaning |
|---|---|
| **Game coordinates** | The game's own 1920×1080 grid that every button, label and simulation cell is placed on, with y = 0 at the bottom. They stay the same whatever size the window is. |
| **Window pixels** | The actual dots of the window on screen. The mouse is reported in these, measured from the top-left. |
| **Viewport** | The rule for turning game coordinates into window pixels and back. A `FitViewport` scales the game to fit the window without stretching it. |
| **Black bars (letterboxing)** | The empty strips added at the sides or top and bottom when the window isn't the same shape as the game, so nothing is stretched. |
| **Unproject** | Turning a window-pixel position (like the mouse) back into game coordinates. |
| **Depth** | How far a 3D point is in front of the camera. |
| **Painter's algorithm** | Drawing the farthest things first so nearer things cover them, like painting a background before the foreground. |
| **Frame rate** | How many pictures (frames) the game draws each second. It varies between computers. |
| **Delta time** | How long the last frame took, in seconds. Multiplying movement by it makes the movement the same speed at any frame rate. |
