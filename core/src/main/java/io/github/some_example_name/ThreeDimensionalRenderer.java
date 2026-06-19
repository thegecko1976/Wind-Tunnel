package io.github.some_example_name;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector4;

public class ThreeDimensionalRenderer {

    private Settings settings;

    private Integer cameraDistance;
    private Integer fov;
    private Vector3 rotationAngles;
    private Vector3 origin;

    private Vector2 screenDimensions;

    private Vector3 rotatedX = new Vector3();
    private Vector3 rotatedXY = new Vector3();
    private Vector3 rotatedXYZ = new Vector3();

    public ThreeDimensionalRenderer() {
        this.settings = Settings.getInstance();

        /*this.rotationAngles = settings.getRotationAngles();
        this.cameraDistance = settings.getCameraDistance();
        this.fov = settings.getFov();*/

        this.screenDimensions = new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public Vector3 rotate(float x, float y, float z) {
        rotationAngles = new Vector3(settings.getRotationAnglesX(), settings.getRotationAnglesY(), settings.getRotationAnglesZ());
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
        origin = settings.getOrigin();
        Vector3 translated = new Vector3(x-origin.x, y-origin.y, z-origin.z); // translated point

        // x rotation
        rotatedX.x = (float) (rotationX[0][0]*translated.x + rotationX[0][1]*translated.y + rotationX[0][2]*translated.z);
        rotatedX.y = (float) (rotationX[1][0]*translated.x + rotationX[1][1]*translated.y + rotationX[1][2]*translated.z);
        rotatedX.z = (float) (rotationX[2][0]*translated.x + rotationX[2][1]*translated.y + rotationX[2][2]*translated.z);
        // y rotation
        rotatedXY.x = (float) (rotationY[0][0]*rotatedX.x + rotationY[0][1]*rotatedX.y + rotationY[0][2]*rotatedX.z);
        rotatedXY.y = (float) (rotationY[1][0]*rotatedX.x + rotationY[1][1]*rotatedX.y + rotationY[1][2]*rotatedX.z);
        rotatedXY.z = (float) (rotationY[2][0]*rotatedX.x + rotationY[2][1]*rotatedX.y + rotationY[2][2]*rotatedX.z);
        // z rotation
        rotatedXYZ.x = (float) (rotationZ[0][0]*rotatedXY.x + rotationZ[0][1]*rotatedXY.y + rotationZ[0][2]*rotatedXY.z);
        rotatedXYZ.y = (float) (rotationZ[1][0]*rotatedXY.x + rotationZ[1][1]*rotatedXY.y + rotationZ[1][2]*rotatedXY.z);
        rotatedXYZ.z = (float) (rotationZ[2][0]*rotatedXY.x + rotationZ[2][1]*rotatedXY.y + rotationZ[2][2]*rotatedXY.z);

        // translate back
        rotatedXYZ.x += origin.x;
        rotatedXYZ.y += origin.y;
        rotatedXYZ.z += origin.z;

        return rotatedXYZ;
    }

    public Vector2 pointProjection(Vector3 point) {
        float zFar = 1000f; // max distance the camera can see
        float zNear = 0.1f; // min distance the camera can see
        float aspectRatio = screenDimensions.x/screenDimensions.y;
        float fovCalculation = (float) (1/Math.tan(0.5*Math.toRadians(settings.getFov())));
        float zNormalisation = zFar/(zFar-zNear);

        // removes points that are too close to the camera to prevent the wrapping/clipping of points
        point.z += settings.getCameraDistance();
        if (point.z <= zNear) {return null;}

        float[][] projectionMatrix = new float[][]{
            {fovCalculation/aspectRatio, 0, 0, 0},
            {0, fovCalculation, 0, 0},
            {0, 0, zNormalisation, -zNear*zNormalisation},
            {0, 0, 1, 0}
        };

        // apply the projection matrix
        Vector4 pos = new Vector4(point, 1); // format: (x,y,z,w) where w = 1 as this is a position not a direction

        // multiply the projection matrix by pos
        point.x = projectionMatrix[0][0]*pos.x + projectionMatrix[0][1]*pos.y + projectionMatrix[0][2]*pos.z + projectionMatrix[0][3]*pos.w;
        point.y = projectionMatrix[1][0]*pos.x + projectionMatrix[1][1]*pos.y + projectionMatrix[1][2]*pos.z + projectionMatrix[1][3]*pos.w;
        point.z = projectionMatrix[2][0]*pos.x + projectionMatrix[2][1]*pos.y + projectionMatrix[2][2]*pos.z + projectionMatrix[2][3]*pos.w;
        pos.w = projectionMatrix[3][0]*pos.x + projectionMatrix[3][1]*pos.y + projectionMatrix[3][2]*pos.z + projectionMatrix[3][3]*pos.w;

        // perspective divide
        point.x /= pos.w;
        point.y /= pos.w;
        point.z /= pos.w;

        // convert 3d point to the 2d screen
        point.x = (point.x+1)*(screenDimensions.x/2);
        point.y = (1-point.y)*(screenDimensions.y/2);

        return new Vector2(point.x, point.y);
    }
}
