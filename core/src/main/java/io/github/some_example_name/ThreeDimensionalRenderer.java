package io.github.some_example_name;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class ThreeDimensionalRenderer {

    private Settings settings;

    private Vector3 lookAt;
    private Integer fov;
    private Integer cameraDistance;
    private Vector3 rotationAngles;

    private Vector2 screenDimensions;

    public ThreeDimensionalRenderer() {
        this.settings = Settings.getInstance();

        this.lookAt = new Vector3(0, 0, 0);
        this.fov = 95;
        this.cameraDistance = settings.getCameraDistance();
        this.rotationAngles = new Vector3(settings.getRotationAnglesX(), settings.getRotationAnglesY(), settings.getRotationAnglesZ());

        this.screenDimensions = new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public Vector3 rotate(int x, int y, int z) {
        this.rotationAngles = new Vector3(settings.getRotationAnglesX(), settings.getRotationAnglesY(), settings.getRotationAnglesZ());
        // 3x3 rotation matrices in each x y z dimension
        double[][] rotationX = {
            {1, 0, 0},
            {0, Math.cos(rotationAngles.x), -Math.sin(rotationAngles.x)},
            {0, Math.sin(rotationAngles.x), Math.cos(rotationAngles.x)},
        };

        double[][] rotationY = {
            {Math.cos(rotationAngles.y), 0, Math.sin(rotationAngles.y)},
            {0, 1, 0},
            {-Math.sin(rotationAngles.y), 0, Math.cos(rotationAngles.y)},
        };

        double[][] rotationZ = {
            {Math.cos(rotationAngles.z), -Math.sin(rotationAngles.z), 0},
            {Math.sin(rotationAngles.z), Math.cos(rotationAngles.z), 0},
            {0, 0, 1},
        };

        // this calculates the dot product of all the rotation vectors above with a point
        Vector3 rotated = new Vector3(x-lookAt.x, y-lookAt.y, z-lookAt.z); // translated point

        // x rotation
        rotated.x = (float) (rotationX[0][0]*rotated.x + rotationX[0][1]*rotated.y + rotationX[0][2]*rotated.z);
        rotated.y = (float) (rotationX[1][0]*rotated.x + rotationX[1][1]*rotated.y + rotationX[1][2]*rotated.z);
        rotated.z = (float) (rotationX[2][0]*rotated.x + rotationX[2][1]*rotated.y + rotationX[2][2]*rotated.z);
        // y rotation
        rotated.x = (float) (rotationY[0][0]*rotated.x + rotationY[0][1]*rotated.y + rotationY[0][2]*rotated.z);
        rotated.y = (float) (rotationY[1][0]*rotated.x + rotationY[1][1]*rotated.y + rotationY[1][2]*rotated.z);
        rotated.z = (float) (rotationY[2][0]*rotated.x + rotationY[2][1]*rotated.y + rotationY[2][2]*rotated.z);
        // z rotation
        rotated.x = (float) (rotationZ[0][0]*rotated.x + rotationZ[0][1]*rotated.y + rotationZ[0][2]*rotated.z);
        rotated.y = (float) (rotationZ[1][0]*rotated.x + rotationZ[1][1]*rotated.y + rotationZ[1][2]*rotated.z);
        rotated.z = (float) (rotationZ[2][0]*rotated.x + rotationZ[2][1]*rotated.y + rotationZ[2][2]*rotated.z);

        // translate back
        rotated.x = rotated.x+lookAt.x;
        rotated.y = rotated.y+lookAt.y;
        rotated.z = rotated.z+lookAt.z;

        return rotated;
    }

    public Vector3 pointProjection(float x, float y, float z) {
        Vector3 projectedPoint = new Vector3(x, y, z);
        float viewFar = 1000f;
        float viewNear = 1f;

        double[][] matrixProjection = {
            {(screenDimensions.y/screenDimensions.x)*Math.toRadians(fov), 0, 0, 0},
            {0, Math.toRadians(fov), 0, 0},
            {0, viewFar/(viewFar-viewNear), 0, 1},
            {0, 0, (-viewFar*viewNear)/(viewFar-viewNear), 0}
        };

        projectedPoint = multiplyMatrixWithVector(matrixProjection, projectedPoint);

        projectedPoint.x += 1.0f;
        projectedPoint.y += 1.0f;
        projectedPoint.x *= 0.5f*screenDimensions.x;
        projectedPoint.y *= 0.5f*screenDimensions.y;

        return projectedPoint;
    }

    public Vector3 multiplyMatrixWithVector(double[][] m, Vector3 v) {
        v.x = (float) (v.x * m[0][0] + v.y * m[1][0] + v.z * m[2][0] + m[3][0]);
        v.y = (float) (v.x * m[0][1] + v.y * m[1][1] + v.z * m[2][1] + m[3][1]);
        v.z = (float) (v.x * m[0][2] + v.y * m[1][2] + v.z * m[2][2] + m[3][2]);
        float w = (float) (v.x * m[0][3] + v.y * m[1][3] + v.z * m[2][3] + m[3][3]);

        if (w != 0) {
            v.x /= w;
            v.y /= w;
            v.z /= w;
        }
        return v;
    }
}
