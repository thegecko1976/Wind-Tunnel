package io.github.wind_tunnel;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins down the boundary-shell selection used by the 3D point-cloud renderer: it should select
 * exactly the cells on the cuboid's 6 boundary faces (each exactly once, including edges/corners)
 * instead of the full x*y*z volume, and the 2D case (resolution.z == 1) must be covered by the
 * exact same predicate without special-casing.
 *
 * The "expected" counts below are computed with a brute-force triple loop mirroring the original
 * pre-optimisation boundary predicate, independent of the implementation being tested, so a
 * regression in the face-loop bounds will show up as a mismatched count.
 */
class LatticeBoltzmannCFDSolverTest {

    @BeforeEach
    void setUp() {
        Settings.getInstance().reset();
    }

    @Test
    void collectBoundaryShellCells_returnsEachBoundaryShellCellExactlyOnce() {
        int[][] resolutions = {{2, 2, 2}, {4, 4, 4}, {6, 4, 4}, {8, 5, 5}};

        for (int[] r : resolutions) {
            int rx = r[0], ry = r[1], rz = r[2];

            LatticeBoltzmannCFDSolver solver = new LatticeBoltzmannCFDSolver();
            List<int[]> cells = solver.collectBoundaryShellCells(rx, ry, rz);

            assertEquals(bruteForceBoundaryCellCount(rx, ry, rz), cells.size());
        }
    }

    @Test
    void buildPointCloudMesh_atProductionScale_includesWholeShellWithNoDroppedPoints() {
        // Regression test: at production resolution (128x72x72, the shape you get after switching
        // from the 2D default), the un-normalised cuboid's bounding radius is ~82 units - far
        // bigger than the default cameraDistance=8 - which used to cause most of the far side to
        // fall behind the CPU renderer's near clipping plane and silently never draw. Now that
        // rotation/projection/culling happen on the GPU via jME's Camera, this test just confirms
        // buildPointCloudMesh() itself never drops any boundary-shell point regardless of
        // resolution - the mesh must always contain the whole shell.
        int rx = 128, ry = 72, rz = 72;

        Settings settings = Settings.getInstance();
        settings.setSolver("3D LBM");
        settings.setResolution(new Vector3f(rx, ry, rz));

        LatticeBoltzmannCFDSolver solver = new LatticeBoltzmannCFDSolver();
        Mesh mesh = solver.buildPointCloudMesh();

        int expected = bruteForceBoundaryCellCount(rx, ry, rz);
        assertEquals(expected, mesh.getVertexCount());
    }

    @Test
    void collectBoundaryShellCells_twoDimensionalDegenerateCase_matchesFullGridPredicate() {
        LatticeBoltzmannCFDSolver solver = new LatticeBoltzmannCFDSolver();
        List<int[]> cells = solver.collectBoundaryShellCells(8, 8, 1);

        assertEquals(bruteForceBoundaryCellCount(8, 8, 1), cells.size());
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
