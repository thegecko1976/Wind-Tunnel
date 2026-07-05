package io.github.wind_tunnel;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LatticeBoltzmannCFDSolver {

    /*
    TO DO:
    - research how to initialise a cell with the correct starting densities
    - research why each relative internal cell direction gets a specific weight
    - create a collide function
    - create a stream function
    - create a bounce function
     */

    private Settings settings;

    // array of densities named by their relative offset to the cell (in 3D)
    private double[][][][] densities;
    private Integer[][] relativeDirections = {
        {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {1, -1, 0}, {-1, 0, 0}, {-1, 1, 0}, {-1, -1, 0}, {0, 1, 0}, {0, -1, 0},
        {0, 0, -1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, -1}, {0, -1, -1}, {0, 0, 1}, {1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1}
    };
    private ArrayList<String> barriers = new ArrayList<>(); // the xyz coords are stored as a String, separated by spaces, for example, 32 2 54
    private Integer neighbours;

    private float four9ths = 4/9f;
    private float one9th = 1/9f;
    private float one36th = 1/36f;
    private float one3rd = 1/3f;
    private float one18th = 1/18f;
    private float v;
    private double one15vv;
    private double one3v3vv;
    private double one_3v3vv;

    int numOfColors = 600;
    ArrayList<ColorRGBA> colours = new ArrayList<>();

    // the point cloud is always scaled to this bounding radius, regardless of simulation
    // resolution - otherwise a large grid (e.g. 128x72x72) ends up far bigger than
    // cameraDistance and most of its far side gets clipped by the near plane at every rotation
    private static final float TARGET_RENDER_RADIUS = 5f;

    public LatticeBoltzmannCFDSolver() {
        this.settings = Settings.getInstance();
        initialiseFluid();
        colours = calculateColours(colours, numOfColors);
    }

    /*public void collide() {
        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {

                }
            }
        }
    }*/

    public void initialiseFluid() {
        if (settings.getSolver() == "2D LBM") {
            neighbours = 9;
        } else {
            neighbours = 19;
        }
        this.densities = new double[(int) settings.getResolution().x][(int) settings.getResolution().y][(int) settings.getResolution().z][neighbours];

        v = settings.getFlowSpeed();
        one15vv = 1-1.5*v*v;
        one3v3vv = 1+3*v+3*v*v;
        one_3v3vv = 1-3*v+3*v*v;

        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    /*densities[x][y][z] = new float[neighbours];
                    for (int count=0; count<neighbours; count++) {
                        densities[x][y][z][count] = settings.getFlowSpeed()/neighbours;
                    }*/
                    if (settings.getSolver() == "2D LBM") {
                        densities[x][y][z][0] = four9ths * one15vv;
                        densities[x][y][z][1] = one9th * one3v3vv;
                        densities[x][y][z][4] = one9th * one_3v3vv;
                        densities[x][y][z][7] = one9th * one15vv;
                        densities[x][y][z][8] = one9th * one15vv;
                        densities[x][y][z][2] = one36th * one3v3vv;
                        densities[x][y][z][3] = one36th * one3v3vv;
                        densities[x][y][z][5] = one36th * one_3v3vv;
                        densities[x][y][z][6] = one36th * one_3v3vv;
                    } else {
                        densities[x][y][z][0] = one3rd * one15vv;
                        densities[x][y][z][1] = one18th * one3v3vv;
                        densities[x][y][z][4] = one18th * one_3v3vv;
                        densities[x][y][z][7] = one18th * one15vv;
                        densities[x][y][z][8] = one18th * one15vv;
                        densities[x][y][z][9] = one18th * one15vv;
                        densities[x][y][z][14] = one18th * one15vv;
                        densities[x][y][z][2] = one36th * one3v3vv;
                        densities[x][y][z][3] = one36th * one3v3vv;
                        densities[x][y][z][5] = one36th * one_3v3vv;
                        densities[x][y][z][6] = one36th * one_3v3vv;
                        densities[x][y][z][10] = one36th * one3v3vv;
                        densities[x][y][z][11] = one36th * one3v3vv;
                        densities[x][y][z][12] = one36th * one_3v3vv;
                        densities[x][y][z][13] = one36th * one_3v3vv;
                        densities[x][y][z][15] = one36th * one3v3vv;
                        densities[x][y][z][16] = one36th * one3v3vv;
                        densities[x][y][z][17] = one36th * one_3v3vv;
                        densities[x][y][z][18] = one36th * one_3v3vv;
                    }
                }
            }
        }
        densities = zeroBarriers();
    }

    // pure-data boundary-shell selection, independent of any rendering API - the same
    // predicate already correctly covers the 2D case too, since resolution.z == 1 there
    // makes z==0 and z==rz-1 the same plane - guarded below so that plane isn't double-counted.
    public List<int[]> collectBoundaryShellCells(int rx, int ry, int rz) {
        List<int[]> cells = new ArrayList<>();

        // z==0 / z==rz-1 faces: full x,y range (owns all edges/corners on these two planes)
        for (int x=0; x<rx; x++) {
            for (int y=0; y<ry; y++) {
                cells.add(new int[]{x, y, 0});
                if (rz > 1) cells.add(new int[]{x, y, rz-1});
            }
        }
        // y==0 / y==ry-1 faces: skip z rows already added above
        for (int x=0; x<rx; x++) {
            for (int z=1; z<rz-1; z++) {
                cells.add(new int[]{x, 0, z});
                if (ry > 1) cells.add(new int[]{x, ry-1, z});
            }
        }
        // x==0 / x==rx-1 faces: skip y,z rows already added above
        for (int y=1; y<ry-1; y++) {
            for (int z=1; z<rz-1; z++) {
                cells.add(new int[]{0, y, z});
                if (rx > 1) cells.add(new int[]{rx-1, y, z});
            }
        }

        return cells;
    }

    // builds the boundary-shell point cloud once (called from initialiseFluid()'s caller on
    // resolution/barrier changes, not every frame) - rotation/projection/culling are handled
    // by jME's own Camera + scene graph once this mesh is attached to a Geometry/Node.
    public Mesh buildPointCloudMesh() {
        int rx = (int) settings.getResolution().x;
        int ry = (int) settings.getResolution().y;
        int rz = (int) settings.getResolution().z;

        List<int[]> cells = collectBoundaryShellCells(rx, ry, rz);
        float boundingRadius = (float) Math.sqrt((rx/2f)*(rx/2f) + (ry/2f)*(ry/2f) + (rz/2f)*(rz/2f));
        float scale = TARGET_RENDER_RADIUS / boundingRadius;

        FloatBuffer positions = BufferUtils.createFloatBuffer(cells.size() * 3);
        for (int[] c : cells) {
            positions.put((c[0] - rx/2f) * scale)
                     .put((c[1] - ry/2f) * scale)
                     .put((c[2] - rz/2f) * scale);
        }
        positions.flip();

        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Points);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.updateBound();
        mesh.setStatic();
        return mesh;
    }

    public ArrayList<ColorRGBA> calculateColours(ArrayList<ColorRGBA> colours, int numOfColours) {
        colours = new ArrayList<>();
        for (int c=0; c<numOfColours; c++) {
            double h = (2.0/3)*(1 - c*1.0/numOfColours);
            h += 0.03 * Math.sin(6*Math.PI*h);
            java.awt.Color awtColour = new java.awt.Color(java.awt.Color.HSBtoRGB((float) h, 1, 1));
            float r = awtColour.getRed()/255f;
            float g = awtColour.getGreen()/255f;
            float b = awtColour.getBlue()/255f;
            colours.add(new ColorRGBA(r, g, b, 1f));
        }
        return colours;
    }

    public int calculateColourIndex(float xVelocity) {
        int index = (int) (numOfColors*(0.5 + xVelocity*0.2));
        if (index < 0) {index = 0;}
        if (index >= numOfColors) {index = numOfColors-1;}
        return index;
    }

    public void addBarrier(Integer x, Integer y, Integer z) {
        barriers.add(x + " " + y + " " + z);
    }

    public double[][][][] zeroBarriers() {
        String[] pos;
        for (String xyz : barriers) {
            pos = xyz.split(" ");
            Arrays.fill(densities[Integer.parseInt(pos[0])][Integer.parseInt(pos[1])][Integer.parseInt(pos[2])], 0);
        }
        return densities;
    }
}
