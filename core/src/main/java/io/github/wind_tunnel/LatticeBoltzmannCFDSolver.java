package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.Arrays;

public class LatticeBoltzmannCFDSolver {

    /*
    TO DO:
    - research how to initialise a cell with the correct starting densities - done
    - research why each relative internal cell direction gets a specific weight - done
    - create a collide function - 100% - done
    - create a stream function - 100% - done
    - create a bounce function - 100% - done
    - research how to get an intersection point between the selected plane and the mouse ray - 100% - done
    - find how to use the scroll wheel to select a plane in 3D - 100% - done
    - find out how to keep barriers when going back to settings (2D -> 3D or change in resolution) - 100% - done
     */

    private Settings settings;
    private ThreeDimensionalRenderer renderer;
    private Keybinds keybinds;

    private float cellDimensions;

    // array of densities named by their relative offset to the cell (in 3D)
    private float[][][][] densities;
    private int[][] relativeDirections = {
        {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {1, -1, 0}, {-1, 0, 0}, {-1, 1, 0}, {-1, -1, 0}, {0, 1, 0}, {0, -1, 0},
        {0, 0, -1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, -1}, {0, -1, -1}, {0, 0, 1}, {1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1}
    };
    private boolean[][][] barriers;
    private int neighbours;
    private boolean[][][] storedBarriers;

    private Vector3 rotatedPoint = new Vector3();
    private Vector2 screenPos;
    private Vector2 mouse = new Vector2();
    private Vector3 cellPosition;

    private float four9ths = 4/9f;
    private float one9th = 1/9f;
    private float one36th = 1/36f;
    private float one3rd = 1/3f;
    private float one18th = 1/18f;
    // D3Q19 weight of each relative direction: 1/3 at rest, 1/18 along an axis, 1/36 along a diagonal
    private float[] weights3D = {
        one3rd, one18th, one36th, one36th, one18th, one36th, one36th, one18th, one18th,
        one18th, one36th, one36th, one36th, one36th, one18th, one36th, one36th, one36th, one36th
    };
    private float v;
    private float one15vv;
    private float one3v3vv;
    private float one_3v3vv;

    private float cellDensity;
    private Vector3 cellVelocity = new Vector3();
    private float omega;
    private float vx3;
    private float vy3;
    private float vxvx;
    private float vyvy;
    private float twovxvy;
    private float vxvxvyvy;
    private float one5vxvxvyvy;
    private float one9thDensity;
    private float one36thDensity;

    private int numOfColors = 600;
    private ArrayList<Color> colours = new ArrayList<>();

    private float[][][][] cellAverageVelocities;

    private static LatticeBoltzmannCFDSolver instance;

    public static LatticeBoltzmannCFDSolver getInstance() {
        if (instance == null) {
            instance = new LatticeBoltzmannCFDSolver();
        }
        return instance;
    }

    private LatticeBoltzmannCFDSolver() {
        this.settings = Settings.getInstance();
        this.renderer = new ThreeDimensionalRenderer();
        initialiseFluid();
        this.colours = calculateColours(numOfColors);
    }

    public void initialiseFluid() {
        if (settings.getSolver().equals("2D LBM")) {
            neighbours = 9;
        } else {
            neighbours = 19;
        }
        this.densities = new float[(int) settings.getResolution().x][(int) settings.getResolution().y][(int) settings.getResolution().z][neighbours];
        this.cellAverageVelocities = new float[(int) settings.getResolution().x][(int) settings.getResolution().y][(int) settings.getResolution().z][3];
        this.barriers = scaleBarriersUsingResolution();
        settings.changeBrushSizeAndCameraDistance();

        v = settings.getFlowSpeed();
        one15vv = 1-1.5f*v*v;
        one3v3vv = 1+3*v+3*v*v;
        one_3v3vv = 1-3*v+3*v*v;

        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    initialiseCell(x, y, z);
                }
            }
        }
        zeroBarriers();
    }

    public void initialiseCell(int x, int y, int z) {
        // this moves the fluid towards an equilibruim/resting state based on the Maxwell-Boltzmann Distribution curve
        if (settings.getSolver().equals("2D LBM")) {
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
            densities[x][y][z][11] = one36th * one_3v3vv;
            densities[x][y][z][12] = one36th * one15vv;
            densities[x][y][z][13] = one36th * one15vv;
            densities[x][y][z][15] = one36th * one3v3vv;
            densities[x][y][z][16] = one36th * one_3v3vv;
            densities[x][y][z][17] = one36th * one15vv;
            densities[x][y][z][18] = one36th * one15vv;
        }
    }

    public void doStep() {
        collide();
        stream();
        bounce();
    }

    public void collide() {
        if (settings.getSolver().equals("2D LBM")) {
            neighbours = 9;
        } else {
            neighbours = 19;
        }
        omega = 1/(3*settings.getViscosity()+0.5f);
        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    if (isBarrier(x, y, z)) {continue;}

                    cellDensity = 0;
                    for (int count=0; count<neighbours; count++) {
                        cellDensity += densities[x][y][z][count];
                    }
                    // calculate the cell's x and y average velocity for calculating colours and relaxation time
                    cellVelocity.x = densities[x][y][z][1]+densities[x][y][z][2]+densities[x][y][z][3]-densities[x][y][z][4]-densities[x][y][z][5]-densities[x][y][z][6];
                    cellVelocity.y = densities[x][y][z][2]-densities[x][y][z][3]+densities[x][y][z][5]-densities[x][y][z][6]+densities[x][y][z][7]-densities[x][y][z][8];


                    if (settings.getSolver().equals("3D LBM")) {
                        // directions 10 to 18 move in z and also in x or y
                        cellVelocity.x += densities[x][y][z][10]+densities[x][y][z][15]-densities[x][y][z][11]-densities[x][y][z][16];
                        cellVelocity.y += densities[x][y][z][12]+densities[x][y][z][17]-densities[x][y][z][13]-densities[x][y][z][18];
                        cellVelocity.z = densities[x][y][z][14]-densities[x][y][z][9]+densities[x][y][z][15]+densities[x][y][z][16]+densities[x][y][z][17]+densities[x][y][z][18]-densities[x][y][z][10]-densities[x][y][z][11]-densities[x][y][z][12]-densities[x][y][z][13];
                        cellVelocity.z /= cellDensity;
                    }

                    cellVelocity.x /= cellDensity;
                    cellVelocity.y /= cellDensity;

                    // store the x and y velocities to draw colours later
                    cellAverageVelocities[x][y][z][0] = cellVelocity.x;
                    cellAverageVelocities[x][y][z][1] = cellVelocity.y;
                    cellAverageVelocities[x][y][z][2] = cellVelocity.z;

                    // pre-calculate re-used values
                    vx3 = 3*cellVelocity.x;
                    vy3 = 3*cellVelocity.y;
                    vxvx = cellVelocity.x*cellVelocity.x;
                    vyvy = cellVelocity.y*cellVelocity.y;
                    twovxvy = 2*cellVelocity.x*cellVelocity.y;
                    vxvxvyvy = vxvx+vyvy;
                    one5vxvxvyvy = 1.5f*vxvxvyvy;
                    one9thDensity = one9th*cellDensity;
                    one36thDensity = one36th*cellDensity;

                    // relaxation equations based on the Maxwell-Boltzmann Distribution curve
                    if (settings.getSolver().equals("2D LBM")){
                        densities[x][y][z][0] += omega*(four9ths*cellDensity * (1-one5vxvxvyvy)-densities[x][y][z][0]);
                        densities[x][y][z][1] += omega*(one9thDensity*(1+vx3 + 4.5f*vxvx - one5vxvxvyvy)-densities[x][y][z][1]);
                        densities[x][y][z][4] += omega*(one9thDensity*(1-vx3 + 4.5f*vxvx - one5vxvxvyvy)-densities[x][y][z][4]);
                        densities[x][y][z][7] += omega*(one9thDensity*(1+vy3 + 4.5f*vyvy - one5vxvxvyvy)-densities[x][y][z][7]);
                        densities[x][y][z][8] += omega*(one9thDensity*(1-vy3 + 4.5f*vyvy - one5vxvxvyvy)-densities[x][y][z][8]);
                        densities[x][y][z][2] += omega*(one36thDensity*(1+vx3+vy3 + 4.5f*(vxvxvyvy+twovxvy) - one5vxvxvyvy)-densities[x][y][z][2]);
                        densities[x][y][z][5] += omega*(one36thDensity*(1-vx3+vy3 + 4.5f*(vxvxvyvy-twovxvy) - one5vxvxvyvy)-densities[x][y][z][5]);
                        densities[x][y][z][3] += omega*(one36thDensity*(1+vx3-vy3 + 4.5f*(vxvxvyvy-twovxvy) - one5vxvxvyvy)-densities[x][y][z][3]);
                        densities[x][y][z][6] += omega*(one36thDensity*(1-vx3-vy3 + 4.5f*(vxvxvyvy+twovxvy) - one5vxvxvyvy)-densities[x][y][z][6]);
                    } else {
                        // equilibrium for each direction e: weight*density*(1 + 3(e.u) + 4.5(e.u)^2 - 1.5(u.u)), where u is the cell's velocity
                        float uu = cellVelocity.x*cellVelocity.x + cellVelocity.y*cellVelocity.y + cellVelocity.z*cellVelocity.z;
                        for (int d=0; d<19; d++) {
                            float eu = relativeDirections[d][0]*cellVelocity.x + relativeDirections[d][1]*cellVelocity.y + relativeDirections[d][2]*cellVelocity.z;
                            float equilibrium = weights3D[d]*cellDensity*(1 + 3*eu + 4.5f*eu*eu - 1.5f*uu);
                            densities[x][y][z][d] += omega*(equilibrium-densities[x][y][z][d]);
                        }
                    }
                }
            }
        }
    }

    public void stream() {
        // move fluid using the LBM optimization Esoteric Pull

        if (settings.getSolver().equals("2D LBM")) {
            for (int x=0; x<settings.getResolution().x-1; x++) {
                for (int y = (int) (settings.getResolution().y-1); y>0; y--) {
                    densities[x][y][0][7] = densities[x][y-1][0][7]; // 010
                    densities[x][y][0][5] = densities[x+1][y-1][0][5]; // -110
                }
            }
            for (int x=(int) (settings.getResolution().x-1); x>0; x--) {
                for (int y = (int) (settings.getResolution().y-1); y>0; y--) {
                    densities[x][y][0][1] = densities[x-1][y][0][1]; // 100
                    densities[x][y][0][2] = densities[x-1][y-1][0][2]; // 110
                }
            }
            for (int x=(int) (settings.getResolution().x-1); x>0; x--) {
                for (int y=0; y<settings.getResolution().y-1; y++) {
                    densities[x][y][0][8] = densities[x][y+1][0][8]; // 0-10
                    densities[x][y][0][3] = densities[x-1][y+1][0][3]; // 1-10
                }
            }
            for (int x=0; x<settings.getResolution().x-1; x++) {
                for (int y=0; y<settings.getResolution().y-1; y++) {
                    densities[x][y][0][4] = densities[x+1][y][0][4]; // -100
                    densities[x][y][0][6] = densities[x+1][y+1][0][6]; // -1-10
                }
            }

            for (int y=0; y<settings.getResolution().y-1; y++) {
                densities[0][y][0][8] = densities[0][y+1][0][8]; // 0-10
            }
            for (int y=(int) (settings.getResolution().y-1); y>0; y--) {
                densities[(int) (settings.getResolution().x-1)][y][0][7] = densities[(int) (settings.getResolution().x-1)][y-1][0][7]; // 010
            }

            // set the left, right, top and bottom cells to be in equilibrium (aka inject new fluid)
            v = settings.getFlowSpeed();

            for (int y=0; y<settings.getResolution().y; y++) {
                if (!isBarrier(0, y, 0)) { // left
                    densities[0][y][0][1] = one9th*(1 + 3*v + 3*v*v); // 100
                    densities[0][y][0][2] = one36th*(1 + 3*v + 3*v*v); // 110
                    densities[0][y][0][3] = one36th*(1 + 3*v + 3*v*v); // 1-10
                }
                if (!isBarrier((int) (settings.getResolution().x-1), y, 0)) { // right
                    densities[(int) (settings.getResolution().x-1)][y][0][4] = one9th*(1 - 3*v + 3*v*v); // -100
                    densities[(int) (settings.getResolution().x-1)][y][0][5] = one36th*(1 - 3*v + 3*v*v); // -110
                    densities[(int) (settings.getResolution().x-1)][y][0][6] = one36th*(1 - 3*v + 3*v*v); // -1-10
                }
            }

            for (int x=0; x<settings.getResolution().x; x++) {
                // top
                densities[x][0][0][0] = four9ths*(1 - 1.5f*v*v); // 000
                densities[x][0][0][1] = one9th*(1 + 3*v + 3*v*v); // 100
                densities[x][0][0][4] = one9th*(1 - 3*v + 3*v*v); // -100
                densities[x][0][0][7] = one9th*(1 - 1.5f*v*v); // 010
                densities[x][0][0][8] = one9th*(1 - 1.5f*v*v); // 0-10
                densities[x][0][0][2] = one36th*(1 + 3*v + 3*v*v); // 110
                densities[x][0][0][3] = one36th*(1 + 3*v + 3*v*v); // 1-10
                densities[x][0][0][5] = one36th*(1 - 3*v + 3*v*v); // -110
                densities[x][0][0][6] = one36th*(1 - 3*v + 3*v*v); // -1-10
                // bottom
                densities[x][(int) (settings.getResolution().y-1)][0][0] = four9ths*(1 - 1.5f*v*v); // 000
                densities[x][(int) (settings.getResolution().y-1)][0][1] = one9th*(1 + 3*v + 3*v*v); // 100
                densities[x][(int) (settings.getResolution().y-1)][0][4] = one9th*(1 - 3*v + 3*v*v); // -100
                densities[x][(int) (settings.getResolution().y-1)][0][7] = one9th*(1 - 1.5f*v*v); // 010
                densities[x][(int) (settings.getResolution().y-1)][0][8] = one9th*(1 - 1.5f*v*v); // 0-10
                densities[x][(int) (settings.getResolution().y-1)][0][2] = one36th*(1 + 3*v + 3*v*v); // 110
                densities[x][(int) (settings.getResolution().y-1)][0][3] = one36th*(1 + 3*v + 3*v*v); // 1-10
                densities[x][(int) (settings.getResolution().y-1)][0][5] = one36th*(1 - 3*v + 3*v*v); // -110
                densities[x][(int) (settings.getResolution().y-1)][0][6] = one36th*(1 - 3*v + 3*v*v); // -1-10
            }
        } else { // 3D streaming

            for (int d=1; d<19; d++) {
                int cx = relativeDirections[d][0];
                int cy = relativeDirections[d][1];
                int cz = relativeDirections[d][2];

                for (int i=0; i<settings.getResolution().x-Math.abs(cx); i++) {
                    int x;
                    if (cx > 0) {x = (int) settings.getResolution().x-1-i;}
                    else {x = i;}

                    for (int j=0; j<settings.getResolution().y-Math.abs(cy); j++) {
                        int y;
                        if (cy > 0) {y = (int) settings.getResolution().y-1-j;}
                        else {y = j;}

                        for (int k=0; k<settings.getResolution().z-Math.abs(cz); k++) {
                            int z;
                            if (cz > 0) {z = (int) settings.getResolution().z-1-k;}
                            else {z = k;}
                            densities[x][y][z][d] = densities[x-cx][y-cy][z-cz][d];
                        }
                    }
                }
            }

            // set the inlet (left) and outlet (right) (directions pointing into the grid), top, bottom, front and back faces, to equilibrium (aka inject new fluid)
            v = settings.getFlowSpeed();
            one15vv = 1-1.5f*v*v;
            one3v3vv = 1+3*v+3*v*v;
            one_3v3vv = 1-3*v+3*v*v;

            for (int x=0; x<settings.getResolution().x; x++) {
                for (int y=0; y<settings.getResolution().y; y++) {
                    for (int z=0; z<settings.getResolution().z; z++) {
                        if (isBarrier(x, y, z)) {continue;} // skip all barriers
                        if (x == 0) { // inlet (left) (directions pointing into the grid)
                            densities[x][y][z][1] = one18th * one3v3vv;
                            densities[x][y][z][2] = one36th * one3v3vv;
                            densities[x][y][z][3] = one36th * one3v3vv;
                            densities[x][y][z][10] = one36th * one3v3vv;
                            densities[x][y][z][15] = one36th * one3v3vv;
                        } else if (x == settings.getResolution().x-1) { // outlet (right) (directions pointing into the grid)
                            densities[x][y][z][4] = one18th * one_3v3vv;
                            densities[x][y][z][5] = one36th * one_3v3vv;
                            densities[x][y][z][6] = one36th * one_3v3vv;
                            densities[x][y][z][11] = one36th * one_3v3vv;
                            densities[x][y][z][16] = one36th * one_3v3vv;
                        } else if (y == 0 || y == settings.getResolution().y-1 || z == 0 || z == settings.getResolution().z-1) { // top, bottom, front and back
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
                            densities[x][y][z][11] = one36th * one_3v3vv;
                            densities[x][y][z][12] = one36th * one15vv;
                            densities[x][y][z][13] = one36th * one15vv;
                            densities[x][y][z][15] = one36th * one3v3vv;
                            densities[x][y][z][16] = one36th * one_3v3vv;
                            densities[x][y][z][17] = one36th * one15vv;
                            densities[x][y][z][18] = one36th * one15vv;
                        }
                    }
                }
            }
        }
    }

    public void bounce() {
        // "bounce" the fluid off barriers

        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    if (isBarrier(x, y, z)) {
                        if (densities[x][y][z][7] > 0) {densities[x][y-1][z][8] += densities[x][y][z][7];} // 010
                        if (densities[x][y][z][8] > 0) {densities[x][y+1][z][7] += densities[x][y][z][8];} // 0-10
                        if (densities[x][y][z][1] > 0) {densities[x-1][y][z][4] += densities[x][y][z][1];} // 100
                        if (densities[x][y][z][4] > 0) {densities[x+1][y][z][1] += densities[x][y][z][4];} // -100

                        if (densities[x][y][z][2] > 0) {densities[x-1][y-1][z][6] += densities[x][y][z][2];} // 110
                        if (densities[x][y][z][5] > 0) {densities[x+1][y-1][z][3] += densities[x][y][z][5];} // -110
                        if (densities[x][y][z][6] > 0) {densities[x+1][y+1][z][2] += densities[x][y][z][6];} // -1-10
                        if (densities[x][y][z][3] > 0) {densities[x-1][y+1][z][5] += densities[x][y][z][3];} // 1-10

                        if (settings.getSolver().equals("3D LBM")) {
                            if (densities[x][y][z][9] > 0) {densities[x][y][z+1][14] += densities[x][y][z][9];} // 00-1
                            if (densities[x][y][z][10] > 0) {densities[x-1][y][z+1][16] += densities[x][y][z][10];} // 10-1
                            if (densities[x][y][z][11] > 0) {densities[x+1][y][z+1][15] += densities[x][y][z][11];} // -10-1
                            if (densities[x][y][z][12] > 0) {densities[x][y-1][z+1][18] += densities[x][y][z][12];} // 01-1
                            if (densities[x][y][z][13] > 0) {densities[x][y+1][z+1][17] += densities[x][y][z][13];} // 0-1-1
                            if (densities[x][y][z][14] > 0) {densities[x][y][z-1][9] += densities[x][y][z][14];} // 001
                            if (densities[x][y][z][15] > 0) {densities[x-1][y][z-1][11] += densities[x][y][z][15];} // 101
                            if (densities[x][y][z][16] > 0) {densities[x+1][y][z-1][10] += densities[x][y][z][16];} // -101
                            if (densities[x][y][z][17] > 0) {densities[x][y-1][z-1][13] += densities[x][y][z][17];} // 011
                            if (densities[x][y][z][18] > 0) {densities[x][y+1][z-1][12] += densities[x][y][z][18];} // 0-11
                        }
                    }
                }
            }
        }
        zeroBarriers();
    }

    public void render(ShapeRenderer sr) {
        if (settings.getSolver().equals("2D LBM")) {
            cellDimensions = 1920/settings.getResolution().x;
        }

        drawBarriers();

        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {

                    if (settings.getRenderOnlySelectedZPlane()) {
                        if (z != settings.getSelectedZPlane()) {continue;}
                    } else if (isBarrier(x, y, z)) {
                    } else if (!(x == 0 || y == 0 || z == 0 || x == settings.getResolution().x-1 || y == settings.getResolution().y-1 || z == settings.getResolution().z-1)) {continue;}

                    if (settings.getLoadedLevel() && settings.getFixedBarrierAt(x, y, z)) {sr.setColor(Color.BLACK);}
                    else if (isBarrier(x, y, z)) {sr.setColor(Color.WHITE);}
                    else {sr.setColor(colours.get(calculateColourIndex(x, y, z)));} // calculates colour based on plotMode
                    if (settings.getSolver().equals("2D LBM")) {
                        sr.rect(x*cellDimensions, y*cellDimensions, cellDimensions, cellDimensions);
                    } else {
                        rotatedPoint = renderer.rotate(x-(settings.getResolution().x/2), y-(settings.getResolution().y/2), z-(settings.getResolution().z/2));
                        screenPos = renderer.pointProjection(rotatedPoint);
                        if (screenPos == null) {continue;}
                        sr.circle(screenPos.x, screenPos.y, 1);
                    }
                }
            }
        }
    }

    public void drawBarriers() {
        keybinds = Keybinds.getInstance();

        mouse.x = Gdx.input.getX();
        mouse.y = 1080-Gdx.input.getY();
        if (!settings.getPlaceBarriers()) {
            if (!Gdx.input.isButtonPressed(keybinds.getBindAt(9)) && !Gdx.input.isButtonPressed(keybinds.getBindAt(10))) {
                settings.setPlaceBarriers(true);
            }
        }
        cellPosition = new Vector3();
        if (settings.getSolver().equals("2D LBM")) {
            cellPosition.x = Math.round(((mouse.x-(cellDimensions/2))/1920)*settings.getResolution().x);
            cellPosition.y = Math.round(((mouse.y-(cellDimensions/2))/1080)*settings.getResolution().y);
            cellPosition.z = 0;
            settings.setIntersectionPoint(cellPosition);

            if (settings.getPlaceBarriers() && !(cellPosition.x <= 0 || cellPosition.x >= settings.getResolution().x-1 || cellPosition.y <= 0 || cellPosition.y >= settings.getResolution().y-1)) {
                if (Gdx.input.isButtonPressed(keybinds.getBindAt(9))) {
                    drawOrEraseBarriersUsingBrushSize((int) cellPosition.x, (int) cellPosition.y, 0, true);
                }
                if (Gdx.input.isButtonPressed(keybinds.getBindAt(10))) {
                    drawOrEraseBarriersUsingBrushSize((int) cellPosition.x, (int) cellPosition.y, 0, false);
                }
            }
        } else { // 3D
            cellPosition = renderer.getIntersectionBetweenPlaneAnd(mouse); // gets intersection point between selected plane and ray from mouse
            if (cellPosition == null) { // ray (almost) parallel to the plane
                return;
            }
            cellPosition.x = Math.round(cellPosition.x+settings.getResolution().x/2);
            cellPosition.y = Math.round(cellPosition.y+settings.getResolution().y/2);
            cellPosition.z = Math.round(cellPosition.z+settings.getResolution().z/2);
            settings.setIntersectionPoint(cellPosition);

            if (settings.getPlaceBarriers() && !(cellPosition.x <= 0 || cellPosition.x >= settings.getResolution().x-1 || cellPosition.y <= 0 || cellPosition.y >= settings.getResolution().y-1 || cellPosition.z <= 0 || cellPosition.z >= settings.getResolution().z-1)) {
                if (Gdx.input.isButtonPressed(keybinds.getBindAt(9))) {
                    drawOrEraseBarriersUsingBrushSize((int) cellPosition.x, (int) cellPosition.y, (int) cellPosition.z, true);
                }
                if (Gdx.input.isButtonPressed(keybinds.getBindAt(10))) {
                    drawOrEraseBarriersUsingBrushSize((int) cellPosition.x, (int) cellPosition.y, (int) cellPosition.z, false);
                }
            }
        }
    }

    public void drawOrEraseBarriersUsingBrushSize(int x, int y, int z, boolean draw) {
        int radius = settings.getBrushSize()-1;

        for (int dx=-radius; dx<=radius; dx++) {
            for (int dy=-radius; dy<=radius; dy++) {
                if (dx*dx + dy*dy > radius*radius) { // if point is outside circle
                    continue;
                }
                int cellX = x+dx;
                int cellY = y+dy;

                // point outside simulation area
                if (cellX <= 0 || cellX >= settings.getResolution().x-1 || cellY <= 0 || cellY >= settings.getResolution().y-1) {
                    continue;
                }
                if (draw) {
                    addBarrier(cellX, cellY, z, false);
                } else if (isBarrier(cellX, cellY, z)) {
                    removeBarrier(cellX, cellY, z);
                }
            }
        }
    }

    public boolean[][][] scaleBarriersUsingResolution() {
        boolean[][][] newBarriers = new boolean[(int) settings.getResolution().x][(int) settings.getResolution().y][(int) settings.getResolution().z];

        if (barriers == null) {
            return newBarriers;
        }
        if (!settings.getSolver().equals(settings.getOldSolver())) {
            return newBarriers;
        }

        storedBarriers = barriers;
        // read the old size from the stored barriers themselves, as settings.reset() (used when loading a level) changes the old resolution setting but not the barriers
        Vector3 oldResolution = new Vector3(storedBarriers.length, storedBarriers[0].length, storedBarriers[0][0].length);

        if (settings.getResolution().equals(oldResolution)) {
            return barriers; // no change in resolution
        }
        Vector3 scale;

        if (settings.getResolution().x > oldResolution.x) {
            scale = new Vector3(settings.getResolution().x/oldResolution.x, settings.getResolution().y/oldResolution.y, settings.getResolution().z/oldResolution.z);
            for (int x=0; x<settings.getResolution().x; x++) {
                for (int y=0; y<settings.getResolution().y; y++) {
                    for (int z=0; z<settings.getResolution().z; z++) {
                        newBarriers[x][y][z] = storedBarriers[(int) (x/scale.x)][(int) (y/scale.y)][(int) (z/scale.z)];
                    }
                }
            }
        } else {
            scale = new Vector3(oldResolution.x/settings.getResolution().x, oldResolution.y/settings.getResolution().y, oldResolution.z/settings.getResolution().z);
            for (int x=0; x<settings.getResolution().x; x++) {
                for (int y=0; y<settings.getResolution().y; y++) {
                    for (int z=0; z<settings.getResolution().z; z++) {
                        int barrierCount = 0;
                        for (int dx=0; dx<scale.x; dx++) {
                            for (int dy=0; dy<scale.y; dy++) {
                                for (int dz=0; dz<scale.z; dz++) {
                                    if (storedBarriers[(int) ((x*scale.x)+dx)][(int) ((y*scale.y)+dy)][(int) ((z*scale.z)+dz)]) {
                                        barrierCount += 1;
                                    }
                                }
                            }
                        }
                        if (barrierCount*2 >= scale.x*scale.y*scale.z) { // more than half the cells are barriers
                            newBarriers[x][y][z] = true;
                        }
                    }
                }
            }
        }
        settings.setOldResolution(new Vector3(settings.getResolution()));
        return newBarriers;
    }

    public ArrayList<Color> calculateColours(int numOfColours) {
        colours = new ArrayList<>();
        for (int c=0; c<numOfColours; c++) {
            double h = (2.0/3)*(1 - c*1.0/numOfColours);
            h += 0.03 * Math.sin(6*Math.PI*h);
            java.awt.Color awtColour = new java.awt.Color(java.awt.Color.HSBtoRGB((float) h, 1, 1));
            float r = awtColour.getRed()/255f;
            float g = awtColour.getGreen()/255f;
            float b = awtColour.getBlue()/255f;
            colours.add(new Color(r, g, b, 1f));
        }
        return colours;
    }

    public int calculateColourIndex(int x, int y, int z) {
        double index = 0;
        float contrast = 1/(3*settings.getFlowSpeed());
        /*if (settings.getPlot().equals("speed")) {
            index = ;
        } else */if (settings.getPlot().equals("x velocity")) {
            index = numOfColors*(0.5+(cellAverageVelocities[x][y][z][0]*contrast));
        } else if (settings.getPlot().equals("y velocity")) {
            index = numOfColors*(0.5+(cellAverageVelocities[x][y][z][1]*contrast));
        } /*else if (settings.getPlot().equals("density")) {
            index = ;
        } else if (settings.getPlot().equals("curl")) {
            index = ;
        }*/
        if (index < 0) {index = 0;}
        if (index >= numOfColors) {index = numOfColors-1;}
        return (int) index;
    }

    public void addBarrier(int x, int y, int z, boolean fixed) {
        if (x > 0 && x < settings.getResolution().x-1 && y > 0 && y < settings.getResolution().y-1) { // in simulation area
            if (settings.getSolver().equals("3D LBM") && (z <= 0 || z >= settings.getResolution().z-1)) { // 3D and outside
                return;
            }
            if (!isBarrier(x, y, z)) {
                if (settings.getLoadedLevel()) {
                    if (settings.getAvailableBarriers() > 0) {
                        settings.setAvailableBarriers(settings.getAvailableBarriers()-1);
                        barriers[x][y][z] = true;
                    }
                } else {
                    barriers[x][y][z] = true;
                }
                if (fixed) {settings.addFixedBarrier(x, y, z);}
            }
        }
    }

    public void removeBarrier(int x, int y, int z) {
        if (settings.getLoadedLevel()) {
            if (!settings.getFixedBarrierAt(x, y, z)) {
                settings.setAvailableBarriers(settings.getAvailableBarriers()+1);
                barriers[x][y][z] = false;
                initialiseCell(x, y, z);
            }
        } else {
            barriers[x][y][z] = false;
            initialiseCell(x, y, z);
        }
    }

    public void removeAllBarriers() {
        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    if (isBarrier(x, y, z)) {
                        removeBarrier(x, y, z);
                    }
                }
            }
        }
    }

    public void zeroBarriers() {
        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    if (barriers[x][y][z]) {
                        Arrays.fill(densities[x][y][z], 0);
                    }
                }
            }
        }
    }

    public boolean isBarrier(int x, int y, int z) {
        return barriers[x][y][z];
    }
}

