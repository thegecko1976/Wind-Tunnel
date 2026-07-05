package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins down the render() surface-iteration rewrite: for the 3D branch it should draw exactly the
 * cells on the cube's 6 boundary faces (each exactly once, including edges/corners) instead of the
 * full x*y*z volume, and the 2D branch must be completely unaffected.
 *
 * The "expected" counts below are computed with a brute-force triple loop mirroring the original
 * pre-optimisation boundary predicate, independent of the optimised face-loop implementation being
 * tested, so a regression in the new loop bounds will show up as a mismatched call count.
 */
class LatticeBoltzmannCFDSolverTest {

    @BeforeEach
    void setUp() {
        Graphics graphics = mock(Graphics.class);
        when(graphics.getWidth()).thenReturn(800);
        when(graphics.getHeight()).thenReturn(600);
        Gdx.graphics = graphics;

        Settings.getInstance().reset();
    }

    @Test
    void render_threeDimensionalMode_drawsEachBoundaryShellCellExactlyOnce() {
        int[][] resolutions = {{2, 2, 2}, {4, 4, 4}, {6, 4, 4}, {8, 5, 5}};

        for (int[] r : resolutions) {
            int rx = r[0], ry = r[1], rz = r[2];

            Settings settings = Settings.getInstance();
            settings.setSolver("3D LBM");
            settings.setResolution(new Vector3(rx, ry, rz));

            LatticeBoltzmannCFDSolver solver = new LatticeBoltzmannCFDSolver();
            ShapeRenderer sr = mock(ShapeRenderer.class);

            solver.render(sr);

            int expected = bruteForceBoundaryCellCount(rx, ry, rz);
            verify(sr, times(expected)).circle(anyFloat(), anyFloat(), anyFloat());
        }
    }

    @Test
    void render_atProductionScaleResolution_drawsWholeShellWithoutNearPlaneCulling() {
        // Regression test: at production resolution (128x72x72, the shape you get after switching
        // from the 2D default) with the default cameraDistance=8, the un-normalised cuboid's
        // bounding radius is ~82 units - far bigger than cameraDistance - so most of the far side
        // used to fall behind the near clipping plane and silently never draw, regardless of
        // rotation. render() must scale world-space coordinates so the whole shell always draws.
        int rx = 128, ry = 72, rz = 72;

        Settings settings = Settings.getInstance();
        settings.setSolver("3D LBM");
        settings.setResolution(new Vector3(rx, ry, rz));

        float[][] rotationsToTry = {{0f, 0f, 0f}, {0.3f, 0.5f, 0.1f}, {1.5f, 2.1f, 0.7f}};
        int expected = bruteForceBoundaryCellCount(rx, ry, rz);

        for (float[] angles : rotationsToTry) {
            settings.setRotationAngles(new Vector3(angles[0], angles[1], angles[2]));

            LatticeBoltzmannCFDSolver solver = new LatticeBoltzmannCFDSolver();
            ShapeRenderer sr = mock(ShapeRenderer.class);

            solver.render(sr);

            verify(sr, times(expected)).circle(anyFloat(), anyFloat(), anyFloat());
        }
    }

    @Test
    void render_twoDimensionalMode_isUnaffectedByThreeDimensionalRewrite() {
        Settings settings = Settings.getInstance();
        settings.setSolver("2D LBM");
        settings.setResolution(new Vector3(8, 8, 1));

        LatticeBoltzmannCFDSolver solver = new LatticeBoltzmannCFDSolver();
        ShapeRenderer sr = mock(ShapeRenderer.class);

        solver.render(sr);

        int expected = bruteForceBoundaryCellCount(8, 8, 1);
        verify(sr, times(expected)).circle(anyFloat(), anyFloat(), anyFloat());
    }

    // independent oracle: reproduces the exact boundary predicate the pre-optimisation O(R^3)
    // loop used, so it doesn't share any code path with the implementation under test
    private int bruteForceBoundaryCellCount(int rx, int ry, int rz) {
        int count = 0;
        for (int x = 0; x < rx; x++) {
            for (int y = 0; y < ry; y++) {
                for (int z = 0; z < rz; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == rx-1 || y == ry-1 || z == rz-1) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
