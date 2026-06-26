package io.github.wind_tunnel;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.Arrays;

public class LatticeBoltzmannCFDSolver {

    /*
    TO DO:
    - research how to initialise a cell with the correct starting densities
    - research why each relative internal cell direction gets a specific weight
    - create a collide function
    - create a stream function
    - create a bounce function
     */

    private MenuUtil util;
    private Settings settings;
    private ThreeDimensionalRenderer renderer;

    private float cellDimensions;

    // array of densities named by their relative offset to the cell (in 3D)
    private float[][][][] densities;
    private Integer[][] relativeDirections = {
        {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {1, -1, 0}, {-1, 0, 0}, {-1, 1, 0}, {-1, -1, 0}, {0, 1, 0}, {0, -1, 0},
        {0, 0, -1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, -1}, {0, -1, -1}, {0, 0, 1}, {1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1}
    };
    private ArrayList<String> barriers = new ArrayList<>(); // the xyz coords are stored as a String, separated by spaces, for example, 32 2 54
    private Integer neighbours;

    private Vector3 rotatedPoint;
    private Vector2 screenPos;

    public LatticeBoltzmannCFDSolver() {
        this.util = new MenuUtil();
        this.settings = Settings.getInstance();
        this.renderer = new ThreeDimensionalRenderer();
        initialiseFluid();
    }

    public void initialiseFluid() {
        if (settings.getSolver() == "2D LBM") {
            neighbours = 9;
        } else {
            neighbours = 19;
        }
        this.densities = new float[(int) settings.getResolution().x][(int) settings.getResolution().y][(int) settings.getResolution().z][neighbours];

        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    densities[x][y][z] = new float[neighbours];
                    for (int count=0; count<neighbours; count++) {
                        densities[x][y][z][count] = settings.getFlowSpeed()/neighbours;
                    }
                }
            }
        }
        densities = zeroBarriers();
    }

    public void render(ShapeRenderer sr) {
        if (settings.getSolver() == "2D LBM") {cellDimensions = 1920/settings.getResolution().x;}

        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    if (!(x == 0 || y == 0 || z == 0 || x == settings.getResolution().x-1 || y == settings.getResolution().y-1 || z == settings.getResolution().z-1)) {continue;}
                    // calculate colour here
                    sr.setColor(1f, 1f, 1f, 1f);
                    if (settings.getSolver() == "2D LBM") {
                        sr.circle((x+0.5f)*cellDimensions, (y+0.5f)*cellDimensions, 1); // sr.rect(x, y, cellDimensions, cellDimensions);
                    } else {
                        rotatedPoint = renderer.rotate(x-(settings.getResolution().x/2), y-(settings.getResolution().y/2), z-(settings.getResolution().z/2));
                        screenPos = renderer.pointProjection(rotatedPoint);
                        if (screenPos == null) continue;
                        sr.circle(screenPos.x, screenPos.y, 1);
                    }
                }
            }
        }
    }

    public void addBarrier(Integer x, Integer y, Integer z) {
        barriers.add(x + " " + y + " " + z);
    }

    public float[][][][] zeroBarriers() {
        String[] pos;
        for (String xyz : barriers) {
            pos = xyz.split(" ");
            Arrays.fill(densities[Integer.parseInt(pos[0])][Integer.parseInt(pos[1])][Integer.parseInt(pos[2])], 0);
        }
        return densities;
    }
}

