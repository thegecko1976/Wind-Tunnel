package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector4;

public class ThreeDimensionalRenderer {

    private Settings settings;

    private Vector3 rotationAngles;
    private Vector3 origin;

    private Vector2 screenDimensions;

    private Vector3 translated = new Vector3();
    private Vector3 rotatedX = new Vector3();
    private Vector3 rotatedXY = new Vector3();
    private Vector3 rotatedXYZ = new Vector3();

    private Vector2 projectedPoint = new Vector2();
    private Vector4 pos = new Vector4();
    private float zFar = 1000f; // max distance the camera can see
    private float zNear = 0.1f; // min distance the camera can see
    private float aspectRatio;
    private float fovCalculation;
    private float zNormalisation = zFar/(zFar-zNear);

    // built once per frame by prepareFrame(), then used by rotate() and pointProjection() for every point
    private float[][] rotationX;
    private float[][] rotationY;
    private float[][] rotationZ;
    private float[][] projectionMatrix;

    public ThreeDimensionalRenderer() {
        this.settings = Settings.getInstance();

        this.rotationAngles = settings.getRotationAngles();
        this.screenDimensions = new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.aspectRatio = screenDimensions.x/screenDimensions.y;
    }

    // the angles, field of view and origin are the same for every point in a frame, so the matrices are built here once per frame
    // instead of in rotate() and pointProjection() for every point; call this before drawing the points
    public void prepareFrame() {
        rotationAngles.x = settings.getRotationAnglesX();
        rotationAngles.y = settings.getRotationAnglesY();
        rotationAngles.z = settings.getRotationAnglesZ();

        float sinX = (float) Math.sin(rotationAngles.x);
        float cosX = (float) Math.cos(rotationAngles.x);
        float sinY = (float) Math.sin(rotationAngles.y);
        float cosY = (float) Math.cos(rotationAngles.y);
        float sinZ = (float) Math.sin(rotationAngles.z);
        float cosZ = (float) Math.cos(rotationAngles.z);

        // 3x3 rotation matrices in x y z
        rotationX = new float[][]{
            {1, 0, 0},
            {0, cosX, -sinX},
            {0, sinX, cosX},
        };

        rotationY = new float[][]{
            {cosY, 0, sinY},
            {0, 1, 0},
            {-sinY, 0, cosY},
        };

        rotationZ = new float[][]{
            {cosZ, -sinZ, 0},
            {sinZ, cosZ, 0},
            {0, 0, 1},
        };

        origin = settings.getOrigin();

        fovCalculation = (float) (1/Math.tan(0.5*(settings.getFov()*(Math.PI/180))));
        projectionMatrix = new float[][]{
            {fovCalculation/aspectRatio, 0, 0, 0},
            {0, fovCalculation, 0, 0},
            {0, 0, zNormalisation, -zNear*zNormalisation},
            {0, 0, 1, 0}
        };
    }

    public Vector3 rotate(float x, float y, float z) {
        // this calculates the dot product of all the rotation vectors above with a point
        translated.x = x-origin.x;
        translated.y = y-origin.y;
        translated.z = z-origin.z;

        // x rotation
        rotatedX.x = rotationX[0][0]*translated.x + rotationX[0][1]*translated.y + rotationX[0][2]*translated.z;
        rotatedX.y = rotationX[1][0]*translated.x + rotationX[1][1]*translated.y + rotationX[1][2]*translated.z;
        rotatedX.z = rotationX[2][0]*translated.x + rotationX[2][1]*translated.y + rotationX[2][2]*translated.z;
        // y rotation
        rotatedXY.x = rotationY[0][0]*rotatedX.x + rotationY[0][1]*rotatedX.y + rotationY[0][2]*rotatedX.z;
        rotatedXY.y = rotationY[1][0]*rotatedX.x + rotationY[1][1]*rotatedX.y + rotationY[1][2]*rotatedX.z;
        rotatedXY.z = rotationY[2][0]*rotatedX.x + rotationY[2][1]*rotatedX.y + rotationY[2][2]*rotatedX.z;
        // z rotation
        rotatedXYZ.x = rotationZ[0][0]*rotatedXY.x + rotationZ[0][1]*rotatedXY.y + rotationZ[0][2]*rotatedXY.z;
        rotatedXYZ.y = rotationZ[1][0]*rotatedXY.x + rotationZ[1][1]*rotatedXY.y + rotationZ[1][2]*rotatedXY.z;
        rotatedXYZ.z = rotationZ[2][0]*rotatedXY.x + rotationZ[2][1]*rotatedXY.y + rotationZ[2][2]*rotatedXY.z;

        // translate back
        rotatedXYZ.x += origin.x;
        rotatedXYZ.y += origin.y;
        rotatedXYZ.z += origin.z;

        return rotatedXYZ;
    }

    public Vector2 pointProjection(Vector3 point) {
        // removes points that are too close to the camera to prevent the wrapping/clipping of points
        point.z += settings.getCameraDistance();
        if (point.z <= zNear) {return null;}

        // multiply the point by the projection matrix, with w = 1 as this is a position not a direction;
        // done here rather than with multiplyVectorWithMatrix() so no new vectors are made for every point
        float w = 1;
        float projectedX = projectionMatrix[0][0]*point.x + projectionMatrix[0][1]*point.y + projectionMatrix[0][2]*point.z + projectionMatrix[0][3]*w;
        float projectedY = projectionMatrix[1][0]*point.x + projectionMatrix[1][1]*point.y + projectionMatrix[1][2]*point.z + projectionMatrix[1][3]*w;
        w = projectionMatrix[3][0]*point.x + projectionMatrix[3][1]*point.y + projectionMatrix[3][2]*point.z + projectionMatrix[3][3]*w;

        // perspective divide
        projectedX /= w;
        projectedY /= w;

        // convert 3D point to the 2D screen
        projectedPoint.x = (projectedX+1)*(screenDimensions.x/2);
        projectedPoint.y = (projectedY+1)*(screenDimensions.y/2);

        return projectedPoint;
    }

    public Vector3 getIntersectionBetweenPlaneAnd(Vector2 mouse) {
        // map mouse screen coordinates to normalised device coordinates (NDC)
        // between -1 and 1 where 0 is the middle of the screen
        float xNDC = ((2*mouse.x)/screenDimensions.x)-1;
        float yNDC = ((2*mouse.y)/screenDimensions.y)-1;

        fovCalculation = (float) (1/Math.tan(0.5*(settings.getFov()*(Math.PI/180))));
        float[][] inverseProjectionMatrix = new float[][]{
            {aspectRatio/fovCalculation, 0, 0, 0},
            {0, 1/fovCalculation, 0, 0},
            {0, 0, 0, 1},
            {0, 0, -1/(zNear*zNormalisation), 1/zNear}
        };

        Vector3 near = multiplyVectorWithMatrix(new Vector4(xNDC, yNDC, 0, 1), inverseProjectionMatrix);
        Vector3 far = multiplyVectorWithMatrix(new Vector4(xNDC, yNDC, 1, 1), inverseProjectionMatrix);

        // camera space to world space
        near = screenToWorldPoint(near);
        far = screenToWorldPoint(far);

        /*
        Plane Equation:
        P(t) = near+t*(far-near) = plane
        at t = 0 -> near
        at t = 1 -> far
        */

        float dz = far.z-near.z;
        if (Math.abs(dz) < 1e-6f) { // ray (almost) parallel to the plane
            return null;
        }

        float plane = settings.getSelectedZPlane()-settings.getResolution().z/2;
        float t = (plane-near.z)/dz; // rearranged for t from the plane equation

        if (t < 0) { // plane intersects behind the camera
            return null;
        }

        return new Vector3(
            near.x+t*(far.x-near.x), // sub t back into plane equation for x
            near.y+t*(far.y-near.y), // sub t back into plane equation for y
            plane                    // the plane is in the z
        );
    }

    public Vector3 multiplyVectorWithMatrix(Vector4 point, float[][] matrix) {
        // apply the matrix
        pos.x = point.x;
        pos.y = point.y;
        pos.z = point.z;
        pos.w = point.w;

        // multiply the matrix by pos
        point.x = matrix[0][0]*pos.x + matrix[0][1]*pos.y + matrix[0][2]*pos.z + matrix[0][3]*pos.w;
        point.y = matrix[1][0]*pos.x + matrix[1][1]*pos.y + matrix[1][2]*pos.z + matrix[1][3]*pos.w;
        point.z = matrix[2][0]*pos.x + matrix[2][1]*pos.y + matrix[2][2]*pos.z + matrix[2][3]*pos.w;
        pos.w = matrix[3][0]*pos.x + matrix[3][1]*pos.y + matrix[3][2]*pos.z + matrix[3][3]*pos.w;

        // perspective divide
        point.x /= pos.w;
        point.y /= pos.w;
        point.z /= pos.w;

        return new Vector3(point.x, point.y, point.z);
    }

    private Vector3 screenToWorldPoint(Vector3 point) {
        origin = settings.getOrigin();

        // undo camera offset and origin translation
        translated.x = point.x-origin.x;
        translated.y = point.y-origin.y;
        translated.z = point.z-origin.z-settings.getCameraDistance();

        rotationAngles.x = settings.getRotationAnglesX();
        rotationAngles.y = settings.getRotationAnglesY();
        rotationAngles.z = settings.getRotationAnglesZ();

        float sinX = (float) Math.sin(rotationAngles.x);
        float cosX = (float) Math.cos(rotationAngles.x);
        float sinY = (float) Math.sin(rotationAngles.y);
        float cosY = (float) Math.cos(rotationAngles.y);
        float sinZ = (float) Math.sin(rotationAngles.z);
        float cosZ = (float) Math.cos(rotationAngles.z);

        // inverse of 3x3 rotation matrices in x y z
        float[][] rotationX = {
            {1, 0, 0},
            {0, cosX, sinX},
            {0, -sinX, cosX},
        };

        float[][] rotationY = {
            {cosY, 0, -sinY},
            {0, 1, 0},
            {sinY, 0, cosY},
        };

        float[][] rotationZ = {
            {cosZ, sinZ, 0},
            {-sinZ, cosZ, 0},
            {0, 0, 1},
        };

        // rotate point back to 3D world coordinates in reverse order of pointProjection (Z -> Y -> X) using the 3 inverse rotation matrices
        // z rotation
        rotatedX.x = rotationZ[0][0]*translated.x + rotationZ[0][1]*translated.y + rotationZ[0][2]*translated.z;
        rotatedX.y = rotationZ[1][0]*translated.x + rotationZ[1][1]*translated.y + rotationZ[1][2]*translated.z;
        rotatedX.z = rotationZ[2][0]*translated.x + rotationZ[2][1]*translated.y + rotationZ[2][2]*translated.z;
        // y rotation
        rotatedXY.x = rotationY[0][0]*rotatedX.x + rotationY[0][1]*rotatedX.y + rotationY[0][2]*rotatedX.z;
        rotatedXY.y = rotationY[1][0]*rotatedX.x + rotationY[1][1]*rotatedX.y + rotationY[1][2]*rotatedX.z;
        rotatedXY.z = rotationY[2][0]*rotatedX.x + rotationY[2][1]*rotatedX.y + rotationY[2][2]*rotatedX.z;
        // x rotation
        rotatedXYZ.x = rotationX[0][0]*rotatedXY.x + rotationX[0][1]*rotatedXY.y + rotationX[0][2]*rotatedXY.z;
        rotatedXYZ.y = rotationX[1][0]*rotatedXY.x + rotationX[1][1]*rotatedXY.y + rotationX[1][2]*rotatedXY.z;
        rotatedXYZ.z = rotationX[2][0]*rotatedXY.x + rotationX[2][1]*rotatedXY.y + rotationX[2][2]*rotatedXY.z;

        // translate back
        return new Vector3(rotatedXYZ.x+origin.x, rotatedXYZ.y+origin.y, rotatedXYZ.z+origin.z);
    }
}
