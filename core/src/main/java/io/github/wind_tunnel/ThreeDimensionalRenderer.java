package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class ThreeDimensionalRenderer {

    private Settings settings;

    private Integer cameraDistance;
    private Integer fov;
    private Vector3 rotationAngles;
    private Vector3 origin;

    private Vector2 screenDimensions;

    private Vector3 translated = new Vector3();
    private Vector3 rotatedXYZ = new Vector3();

    private Vector2 projectedPoint = new Vector2();

    // combined rotation matrix (Rz * Ry * Rx), recomputed once per frame in updateFrameState()
    private float r00, r01, r02, r10, r11, r12, r20, r21, r22;

    private float zFar = 1000f; // max distance the camera can see
    private float zNear = 0.1f; // min distance the camera can see
    private float aspectRatio;
    private float fovCalculation;
    private float zNormalisation = zFar/(zFar-zNear);

    public ThreeDimensionalRenderer() {
        this.settings = Settings.getInstance();

        this.rotationAngles = settings.getRotationAngles();
        this.cameraDistance = settings.getCameraDistance();
        this.fov = settings.getFov();

        this.screenDimensions = new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.aspectRatio = screenDimensions.x/screenDimensions.y;
    }

    // recomputes the combined rotation matrix and projection scalars once per frame,
    // since rotation angles/fov are constant across all points drawn in a given frame
    public void updateFrameState() {
        rotationAngles.x = settings.getRotationAnglesX();
        rotationAngles.y = settings.getRotationAnglesY();
        rotationAngles.z = settings.getRotationAnglesZ();
        origin = settings.getOrigin();

        float sinX = (float) Math.sin(rotationAngles.x);
        float cosX = (float) Math.cos(rotationAngles.x);
        float sinY = (float) Math.sin(rotationAngles.y);
        float cosY = (float) Math.cos(rotationAngles.y);
        float sinZ = (float) Math.sin(rotationAngles.z);
        float cosZ = (float) Math.cos(rotationAngles.z);

        // combined = Rz * Ry * Rx, algebraically expanded from the same per-axis
        // rotation matrices this replaces (x rotation, then y, then z)
        r00 = cosZ*cosY;
        r01 = cosZ*sinY*sinX - sinZ*cosX;
        r02 = cosZ*sinY*cosX + sinZ*sinX;
        r10 = sinZ*cosY;
        r11 = sinZ*sinY*sinX + cosZ*cosX;
        r12 = sinZ*sinY*cosX - cosZ*sinX;
        r20 = -sinY;
        r21 = cosY*sinX;
        r22 = cosY*cosX;

        fovCalculation = (float) (1/Math.tan(0.5*(settings.getFov()*(Math.PI/180))));
    }

    public Vector3 rotate(float x, float y, float z) {
        translated.x = x-origin.x;
        translated.y = y-origin.y;
        translated.z = z-origin.z;

        rotatedXYZ.x = r00*translated.x + r01*translated.y + r02*translated.z + origin.x;
        rotatedXYZ.y = r10*translated.x + r11*translated.y + r12*translated.z + origin.y;
        rotatedXYZ.z = r20*translated.x + r21*translated.y + r22*translated.z + origin.z;

        return rotatedXYZ;
    }

    public Vector2 pointProjection(Vector3 point) {
        // removes points that are too close to the camera to prevent the wrapping/clipping of points
        point.z += settings.getCameraDistance();
        if (point.z <= zNear) {return null;}

        // sparse projection matrix reduced to its 4 non-zero terms (no per-call allocation)
        float clipX = (fovCalculation/aspectRatio) * point.x;
        float clipY = fovCalculation * point.y;
        float clipZ = zNormalisation*point.z - zNear*zNormalisation;
        float clipW = point.z;

        // perspective divide
        point.x = clipX / clipW;
        point.y = clipY / clipW;
        point.z = clipZ / clipW;

        // convert 3d point to the 2d screen
        projectedPoint.x = (point.x+1)*(screenDimensions.x/2);
        projectedPoint.y = (1-point.y)*(screenDimensions.y/2);

        return projectedPoint;
    }
}
