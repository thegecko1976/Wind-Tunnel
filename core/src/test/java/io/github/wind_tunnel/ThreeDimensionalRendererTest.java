package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * These tests pin down two things that were introduced when the renderer was optimised to avoid
 * per-point allocation: (1) rotate()/pointProjection() must still produce the same numbers as the
 * original per-axis-sequential / 4x4-matrix algorithms, and (2) both methods must only pick up new
 * settings when updateFrameState() is called (the whole point of the optimisation), never mid-frame.
 */
class ThreeDimensionalRendererTest {

    private static final float DELTA = 1e-3f;
    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 600;

    @BeforeEach
    void setUp() {
        Graphics graphics = mock(Graphics.class);
        when(graphics.getWidth()).thenReturn(SCREEN_WIDTH);
        when(graphics.getHeight()).thenReturn(SCREEN_HEIGHT);
        Gdx.graphics = graphics;

        Settings.getInstance().reset();
    }

    // --- rotate() -----------------------------------------------------------------------------

    @Test
    void rotate_withZeroRotationAndZeroOrigin_returnsUnchangedPoint() {
        Settings settings = Settings.getInstance();
        settings.setRotationAngles(new Vector3(0, 0, 0));
        settings.setOrigin(new Vector3(0, 0, 0));

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();

        Vector3 result = renderer.rotate(3f, -4f, 5f);

        assertEquals(3f, result.x, DELTA);
        assertEquals(-4f, result.y, DELTA);
        assertEquals(5f, result.z, DELTA);
    }

    @Test
    void rotate_ninetyDegreesAboutEachSingleAxis_matchesHandComputedResult() {
        Settings settings = Settings.getInstance();
        settings.setOrigin(new Vector3(0, 0, 0));
        float ninety = (float) (Math.PI / 2);

        settings.setRotationAngles(new Vector3(ninety, 0f, 0f));
        ThreeDimensionalRenderer rendererX = new ThreeDimensionalRenderer();
        rendererX.updateFrameState();
        assertVectorEquals(new Vector3(0f, 0f, 1f), rendererX.rotate(0f, 1f, 0f));

        settings.setRotationAngles(new Vector3(0f, ninety, 0f));
        ThreeDimensionalRenderer rendererY = new ThreeDimensionalRenderer();
        rendererY.updateFrameState();
        assertVectorEquals(new Vector3(1f, 0f, 0f), rendererY.rotate(0f, 0f, 1f));

        settings.setRotationAngles(new Vector3(0f, 0f, ninety));
        ThreeDimensionalRenderer rendererZ = new ThreeDimensionalRenderer();
        rendererZ.updateFrameState();
        assertVectorEquals(new Vector3(0f, 1f, 0f), rendererZ.rotate(1f, 0f, 0f));
    }

    @Test
    void rotate_combinedAngles_matchesOriginalSequentialAlgorithm() {
        Settings settings = Settings.getInstance();
        Vector3 origin = new Vector3(2f, -1f, 0.5f);
        Vector3 angles = new Vector3(0.3f, 0.5f, -0.7f);
        settings.setOrigin(origin);
        settings.setRotationAngles(angles);

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();

        float[][] points = {{2f, -1f, 3f}, {0f, 0f, 0f}, {-5f, 10f, -2.5f}};
        for (float[] p : points) {
            Vector3 expected = referenceSequentialRotate(p[0], p[1], p[2], angles, origin);
            Vector3 actual = renderer.rotate(p[0], p[1], p[2]);
            assertVectorEquals(expected, actual);
        }
    }

    @Test
    void rotate_ignoresSettingsChangesUntilUpdateFrameStateCalledAgain() {
        Settings settings = Settings.getInstance();
        settings.setOrigin(new Vector3(0, 0, 0));
        settings.setRotationAngles(new Vector3(0, 0, 0));

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();
        assertVectorEquals(new Vector3(1f, 0f, 0f), renderer.rotate(1f, 0f, 0f));

        // change the angle without refreshing frame state: rotate() must still use the stale matrix
        settings.setRotationAngles(new Vector3(0f, 0f, (float) (Math.PI / 2)));
        assertVectorEquals(new Vector3(1f, 0f, 0f), renderer.rotate(1f, 0f, 0f));

        // now refresh: the new angle should take effect
        renderer.updateFrameState();
        assertVectorEquals(new Vector3(0f, 1f, 0f), renderer.rotate(1f, 0f, 0f));
    }

    // --- pointProjection() ---------------------------------------------------------------------

    @Test
    void pointProjection_pointBehindOrTooCloseToCamera_returnsNull() {
        Settings settings = Settings.getInstance();
        settings.setCameraDistance(5);

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();

        assertNull(renderer.pointProjection(new Vector3(0f, 0f, -100f)));
    }

    @Test
    void pointProjection_pointOnCameraAxis_projectsToScreenCenter() {
        Settings settings = Settings.getInstance();
        settings.setCameraDistance(5);

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();

        Vector2 result = renderer.pointProjection(new Vector3(0f, 0f, 10f));

        assertNotNull(result);
        assertEquals(SCREEN_WIDTH / 2f, result.x, DELTA);
        assertEquals(SCREEN_HEIGHT / 2f, result.y, DELTA);
    }

    @Test
    void pointProjection_offAxisPoint_matchesOriginalMatrixAlgorithm() {
        Settings settings = Settings.getInstance();
        settings.setCameraDistance(6);
        settings.setFov(75);

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();

        float[][] points = {{1.5f, -2f, 3f}, {-4f, 4f, 8f}, {0.1f, 0.1f, 1f}};
        for (float[] p : points) {
            Vector2 expected = referenceMatrixProjection(p[0], p[1], p[2], 6, 75, SCREEN_WIDTH, SCREEN_HEIGHT);
            Vector2 actual = renderer.pointProjection(new Vector3(p[0], p[1], p[2]));
            assertNotNull(expected);
            assertNotNull(actual);
            assertEquals(expected.x, actual.x, DELTA);
            assertEquals(expected.y, actual.y, DELTA);
        }
    }

    @Test
    void pointProjection_ignoresFovChangeUntilUpdateFrameStateCalledAgain() {
        Settings settings = Settings.getInstance();
        settings.setCameraDistance(5);
        settings.setFov(60);

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();
        Vector2 before = new Vector2(renderer.pointProjection(new Vector3(2f, 0f, 5f)));

        // change fov without refreshing: projection must stay based on the stale fovCalculation
        settings.setFov(120);
        Vector2 stillStale = renderer.pointProjection(new Vector3(2f, 0f, 5f));
        assertEquals(before.x, stillStale.x, DELTA);

        // refresh: now the new fov should change the projected x
        renderer.updateFrameState();
        Vector2 refreshed = renderer.pointProjection(new Vector3(2f, 0f, 5f));
        assertNotEqualsDelta(before.x, refreshed.x);
    }

    @Test
    void pointProjection_reusesSameVector2InstanceAcrossCalls() {
        Settings settings = Settings.getInstance();
        settings.setCameraDistance(5);

        ThreeDimensionalRenderer renderer = new ThreeDimensionalRenderer();
        renderer.updateFrameState();

        Vector2 first = renderer.pointProjection(new Vector3(1f, 1f, 5f));
        Vector2 second = renderer.pointProjection(new Vector3(2f, 2f, 5f));

        assertSame(first, second);
    }

    // --- reference oracles: independent re-implementations of the pre-optimisation algorithms ---

    private Vector3 referenceSequentialRotate(float x, float y, float z, Vector3 angles, Vector3 origin) {
        float sinX = (float) Math.sin(angles.x), cosX = (float) Math.cos(angles.x);
        float sinY = (float) Math.sin(angles.y), cosY = (float) Math.cos(angles.y);
        float sinZ = (float) Math.sin(angles.z), cosZ = (float) Math.cos(angles.z);

        float[][] rotationX = {{1, 0, 0}, {0, cosX, -sinX}, {0, sinX, cosX}};
        float[][] rotationY = {{cosY, 0, sinY}, {0, 1, 0}, {-sinY, 0, cosY}};
        float[][] rotationZ = {{cosZ, -sinZ, 0}, {sinZ, cosZ, 0}, {0, 0, 1}};

        float tx = x - origin.x, ty = y - origin.y, tz = z - origin.z;

        float rx = rotationX[0][0]*tx + rotationX[0][1]*ty + rotationX[0][2]*tz;
        float ry = rotationX[1][0]*tx + rotationX[1][1]*ty + rotationX[1][2]*tz;
        float rz = rotationX[2][0]*tx + rotationX[2][1]*ty + rotationX[2][2]*tz;

        float rxy = rotationY[0][0]*rx + rotationY[0][1]*ry + rotationY[0][2]*rz;
        float ryy = rotationY[1][0]*rx + rotationY[1][1]*ry + rotationY[1][2]*rz;
        float rzy = rotationY[2][0]*rx + rotationY[2][1]*ry + rotationY[2][2]*rz;

        float rxyz = rotationZ[0][0]*rxy + rotationZ[0][1]*ryy + rotationZ[0][2]*rzy;
        float ryxyz = rotationZ[1][0]*rxy + rotationZ[1][1]*ryy + rotationZ[1][2]*rzy;
        float rzxyz = rotationZ[2][0]*rxy + rotationZ[2][1]*ryy + rotationZ[2][2]*rzy;

        return new Vector3(rxyz + origin.x, ryxyz + origin.y, rzxyz + origin.z);
    }

    private Vector2 referenceMatrixProjection(float x, float y, float z, int cameraDistance, int fov, int width, int height) {
        float zNear = 0.1f, zFar = 1000f;
        float aspectRatio = width / (float) height;
        float zNormalisation = zFar / (zFar - zNear);
        float fovCalculation = (float) (1 / Math.tan(0.5 * (fov * (Math.PI / 180))));

        float pz = z + cameraDistance;
        if (pz <= zNear) return null;

        float[][] projectionMatrix = {
            {fovCalculation/aspectRatio, 0, 0, 0},
            {0, fovCalculation, 0, 0},
            {0, 0, zNormalisation, -zNear*zNormalisation},
            {0, 0, 1, 0}
        };

        float px = x, py = y, pzz = pz, pw = 1;
        float rx = projectionMatrix[0][0]*px + projectionMatrix[0][1]*py + projectionMatrix[0][2]*pzz + projectionMatrix[0][3]*pw;
        float ry = projectionMatrix[1][0]*px + projectionMatrix[1][1]*py + projectionMatrix[1][2]*pzz + projectionMatrix[1][3]*pw;
        float rw = projectionMatrix[3][0]*px + projectionMatrix[3][1]*py + projectionMatrix[3][2]*pzz + projectionMatrix[3][3]*pw;

        rx /= rw;
        ry /= rw;

        return new Vector2((rx+1)*(width/2f), (1-ry)*(height/2f));
    }

    private void assertVectorEquals(Vector3 expected, Vector3 actual) {
        assertEquals(expected.x, actual.x, DELTA, "x");
        assertEquals(expected.y, actual.y, DELTA, "y");
        assertEquals(expected.z, actual.z, DELTA, "z");
    }

    private void assertNotEqualsDelta(float a, float b) {
        assertEquals(false, Math.abs(a - b) < DELTA, "expected values to differ but both were " + a);
    }
}
