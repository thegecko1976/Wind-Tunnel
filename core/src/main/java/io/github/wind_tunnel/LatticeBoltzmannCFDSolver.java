package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

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

    // the size of the grid the arrays below were made for (the resolution setting can change before initialiseFluid() is called)
    private int gridX;
    private int gridY;
    private int gridZ;
    private int cellCount;

    // every cell's densities in one flat array: the cell at (x, y, z) has index i = (z*gridY + y)*gridX + x,
    // and its density in relative direction d is densities[i*neighbours + d], so a cell's densities sit next to each other in memory
    private float[] densities;
    private float[] nextDensities; // stream() writes the moved densities into this, then swaps it with densities
    private int[][] relativeDirections = {
        {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {1, -1, 0}, {-1, 0, 0}, {-1, 1, 0}, {-1, -1, 0}, {0, 1, 0}, {0, -1, 0},
        {0, 0, -1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, -1}, {0, -1, -1}, {0, 0, 1}, {1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1}
    };
    // the same directions split into their x, y and z parts, which are quicker to read inside the loops
    private int[] directionX = new int[19];
    private int[] directionY = new int[19];
    private int[] directionZ = new int[19];
    private boolean[] barriers; // barriers[i], using the same cell index as densities
    private int neighbours;

    // the index of every barrier cell, so bounce() and zeroBarriers() only visit barriers instead of the whole grid
    private int[] barrierCells = new int[0];
    private int barrierCount = 0;
    private boolean barrierCellsOutOfDate = true;

    // grids with at least this many cells are shared between all the CPU cores; smaller grids are quicker on one core
    private static final int PARALLEL_THRESHOLD = 20000;

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

    private int numOfColors = 600;
    private ArrayList<Color> colours = new ArrayList<>();
    private int[] packedColours; // the same colours as RGBA8888 ints, which can be written straight into the 2D picture
    private int white = Color.rgba8888(Color.WHITE);
    private int black = Color.rgba8888(Color.BLACK);

    private float[] velocities; // the x, y and z velocity of cell i are at velocities[i*3], [i*3+1] and [i*3+2], used for the colours

    // the 2D picture: one pixel per cell, drawn stretched across the screen in a single draw
    private Pixmap fieldPixmap;
    private Texture fieldTexture;
    private int[] fieldPixels;
    private boolean fieldChanged = true;
    private String drawnPlot;
    private float drawnFlowSpeed;
    private boolean drawnLoadedLevel;

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
        for (int d=0; d<19; d++) {
            directionX[d] = relativeDirections[d][0];
            directionY[d] = relativeDirections[d][1];
            directionZ[d] = relativeDirections[d][2];
        }
        initialiseFluid();
        this.colours = calculateColours(numOfColors);
        this.packedColours = new int[numOfColors];
        for (int c=0; c<numOfColors; c++) {
            packedColours[c] = Color.rgba8888(colours.get(c));
        }
    }

    public void initialiseFluid() {
        if (settings.getSolver().equals("2D LBM")) {
            neighbours = 9;
        } else {
            neighbours = 19;
        }
        boolean[] scaledBarriers = scaleBarriersUsingResolution(); // before the grid size below changes, as it needs the old size

        gridX = (int) settings.getResolution().x;
        gridY = (int) settings.getResolution().y;
        gridZ = (int) settings.getResolution().z;
        cellCount = gridX*gridY*gridZ;
        this.densities = new float[cellCount*neighbours];
        this.nextDensities = new float[cellCount*neighbours];
        this.velocities = new float[cellCount*3];
        this.barriers = scaledBarriers;
        barrierCellsOutOfDate = true;
        settings.changeBrushSizeAndCameraDistance();

        v = settings.getFlowSpeed();
        one15vv = 1-1.5f*v*v;
        one3v3vv = 1+3*v+3*v*v;
        one_3v3vv = 1-3*v+3*v*v;

        for (int i=0; i<cellCount; i++) {
            initialiseCellAt(i);
        }
        zeroBarriers();
        fieldChanged = true;
    }

    public void initialiseCell(int x, int y, int z) {
        initialiseCellAt((z*gridY + y)*gridX + x);
    }

    private void initialiseCellAt(int i) {
        // this moves the fluid towards an equilibruim/resting state based on the Maxwell-Boltzmann Distribution curve
        int b = i*neighbours;
        if (neighbours == 9) {
            densities[b] = four9ths * one15vv;
            densities[b+1] = one9th * one3v3vv;
            densities[b+4] = one9th * one_3v3vv;
            densities[b+7] = one9th * one15vv;
            densities[b+8] = one9th * one15vv;
            densities[b+2] = one36th * one3v3vv;
            densities[b+3] = one36th * one3v3vv;
            densities[b+5] = one36th * one_3v3vv;
            densities[b+6] = one36th * one_3v3vv;
        } else {
            densities[b] = one3rd * one15vv;
            densities[b+1] = one18th * one3v3vv;
            densities[b+4] = one18th * one_3v3vv;
            densities[b+7] = one18th * one15vv;
            densities[b+8] = one18th * one15vv;
            densities[b+9] = one18th * one15vv;
            densities[b+14] = one18th * one15vv;
            densities[b+2] = one36th * one3v3vv;
            densities[b+3] = one36th * one3v3vv;
            densities[b+5] = one36th * one_3v3vv;
            densities[b+6] = one36th * one_3v3vv;
            densities[b+10] = one36th * one3v3vv;
            densities[b+11] = one36th * one_3v3vv;
            densities[b+12] = one36th * one15vv;
            densities[b+13] = one36th * one15vv;
            densities[b+15] = one36th * one3v3vv;
            densities[b+16] = one36th * one_3v3vv;
            densities[b+17] = one36th * one15vv;
            densities[b+18] = one36th * one15vv;
        }
    }

    public void doStep() {
        collide();
        stream();
        bounce();
    }

    public void collide() {
        // each cell only reads and writes its own densities, so the cells can be shared between CPU cores;
        // the working values are local variables (not fields) so that each core has its own copy
        final float[] f = densities;
        final float[] velocity = velocities;
        final boolean[] barrier = barriers;
        final int q = neighbours;
        final int slabSize = slabSize();
        final float omega = 1/(3*settings.getViscosity()+0.5f);

        forEachSlab(slab -> {
            for (int i=slab*slabSize; i<(slab+1)*slabSize; i++) {
                if (barrier[i]) {continue;}
                int b = i*q;

                float cellDensity = 0;
                for (int count=0; count<q; count++) {
                    cellDensity += f[b+count];
                }
                // calculate the cell's x and y average velocity for calculating colours and relaxation time
                float velocityX = f[b+1]+f[b+2]+f[b+3]-f[b+4]-f[b+5]-f[b+6];
                float velocityY = f[b+2]-f[b+3]+f[b+5]-f[b+6]+f[b+7]-f[b+8];
                float velocityZ = 0;

                if (q == 19) {
                    // directions 10 to 18 move in z and also in x or y
                    velocityX += f[b+10]+f[b+15]-f[b+11]-f[b+16];
                    velocityY += f[b+12]+f[b+17]-f[b+13]-f[b+18];
                    velocityZ = f[b+14]-f[b+9]+f[b+15]+f[b+16]+f[b+17]+f[b+18]-f[b+10]-f[b+11]-f[b+12]-f[b+13];
                    velocityZ /= cellDensity;
                }

                velocityX /= cellDensity;
                velocityY /= cellDensity;

                // store the velocities to draw colours later
                velocity[i*3] = velocityX;
                velocity[i*3+1] = velocityY;
                velocity[i*3+2] = velocityZ;

                // relaxation equations based on the Maxwell-Boltzmann Distribution curve
                if (q == 9) {
                    // pre-calculate re-used values
                    float vx3 = 3*velocityX;
                    float vy3 = 3*velocityY;
                    float vxvx = velocityX*velocityX;
                    float vyvy = velocityY*velocityY;
                    float twovxvy = 2*velocityX*velocityY;
                    float vxvxvyvy = vxvx+vyvy;
                    float one5vxvxvyvy = 1.5f*vxvxvyvy;
                    float one9thDensity = one9th*cellDensity;
                    float one36thDensity = one36th*cellDensity;

                    f[b] += omega*(four9ths*cellDensity * (1-one5vxvxvyvy)-f[b]);
                    f[b+1] += omega*(one9thDensity*(1+vx3 + 4.5f*vxvx - one5vxvxvyvy)-f[b+1]);
                    f[b+4] += omega*(one9thDensity*(1-vx3 + 4.5f*vxvx - one5vxvxvyvy)-f[b+4]);
                    f[b+7] += omega*(one9thDensity*(1+vy3 + 4.5f*vyvy - one5vxvxvyvy)-f[b+7]);
                    f[b+8] += omega*(one9thDensity*(1-vy3 + 4.5f*vyvy - one5vxvxvyvy)-f[b+8]);
                    f[b+2] += omega*(one36thDensity*(1+vx3+vy3 + 4.5f*(vxvxvyvy+twovxvy) - one5vxvxvyvy)-f[b+2]);
                    f[b+5] += omega*(one36thDensity*(1-vx3+vy3 + 4.5f*(vxvxvyvy-twovxvy) - one5vxvxvyvy)-f[b+5]);
                    f[b+3] += omega*(one36thDensity*(1+vx3-vy3 + 4.5f*(vxvxvyvy-twovxvy) - one5vxvxvyvy)-f[b+3]);
                    f[b+6] += omega*(one36thDensity*(1-vx3-vy3 + 4.5f*(vxvxvyvy+twovxvy) - one5vxvxvyvy)-f[b+6]);
                } else {
                    // equilibrium for each direction e: weight*density*(1 + 3(e.u) + 4.5(e.u)^2 - 1.5(u.u)), where u is the cell's velocity
                    float uu = velocityX*velocityX + velocityY*velocityY + velocityZ*velocityZ;
                    for (int d=0; d<19; d++) {
                        float eu = directionX[d]*velocityX + directionY[d]*velocityY + directionZ[d]*velocityZ;
                        float equilibrium = weights3D[d]*cellDensity*(1 + 3*eu + 4.5f*eu*eu - 1.5f*uu);
                        f[b+d] += omega*(equilibrium-f[b+d]);
                    }
                }
            }
        });
        fieldChanged = true;
    }

    public void stream() {
        // move the fluid: each density is pulled from the neighbour it is moving away from, into a second array,
        // so every cell reads the values from before this step and the rows (2D) or planes (3D) can be shared between CPU cores
        final float[] from = densities;
        final float[] to = nextDensities;
        final int q = neighbours;
        final int nx = gridX;
        final int ny = gridY;
        final int nz = gridZ;

        forEachSlab(slab -> {
            int z = nz > 1 ? slab : 0;
            int firstY = nz > 1 ? 0 : slab;
            int lastY = nz > 1 ? ny-1 : slab;
            for (int y=firstY; y<=lastY; y++) {
                for (int x=0; x<nx; x++) {
                    int b = ((z*ny + y)*nx + x)*q;
                    for (int d=0; d<q; d++) {
                        int fromX = x-directionX[d];
                        int fromY = y-directionY[d];
                        int fromZ = z-directionZ[d];
                        if (fromX < 0 || fromX >= nx || fromY < 0 || fromY >= ny || fromZ < 0 || fromZ >= nz) {
                            to[b+d] = from[b+d]; // nothing upstream inside the grid, so keep the old value
                        } else {
                            to[b+d] = from[((fromZ*ny + fromY)*nx + fromX)*q + d];
                        }
                    }
                }
            }
        });
        densities = to;
        nextDensities = from;

        if (q == 9) {
            // set the left, right, top and bottom cells to be in equilibrium (aka inject new fluid)
            v = settings.getFlowSpeed();

            for (int y=0; y<ny; y++) {
                int left = y*nx;
                int right = y*nx + nx-1;
                if (!barriers[left]) { // left
                    densities[left*9+1] = one9th*(1 + 3*v + 3*v*v); // 100
                    densities[left*9+2] = one36th*(1 + 3*v + 3*v*v); // 110
                    densities[left*9+3] = one36th*(1 + 3*v + 3*v*v); // 1-10
                }
                if (!barriers[right]) { // right
                    densities[right*9+4] = one9th*(1 - 3*v + 3*v*v); // -100
                    densities[right*9+5] = one36th*(1 - 3*v + 3*v*v); // -110
                    densities[right*9+6] = one36th*(1 - 3*v + 3*v*v); // -1-10
                }
            }

            for (int x=0; x<nx; x++) {
                // top
                int top = x*9;
                densities[top] = four9ths*(1 - 1.5f*v*v); // 000
                densities[top+1] = one9th*(1 + 3*v + 3*v*v); // 100
                densities[top+4] = one9th*(1 - 3*v + 3*v*v); // -100
                densities[top+7] = one9th*(1 - 1.5f*v*v); // 010
                densities[top+8] = one9th*(1 - 1.5f*v*v); // 0-10
                densities[top+2] = one36th*(1 + 3*v + 3*v*v); // 110
                densities[top+3] = one36th*(1 + 3*v + 3*v*v); // 1-10
                densities[top+5] = one36th*(1 - 3*v + 3*v*v); // -110
                densities[top+6] = one36th*(1 - 3*v + 3*v*v); // -1-10
                // bottom
                int bottom = ((ny-1)*nx + x)*9;
                densities[bottom] = four9ths*(1 - 1.5f*v*v); // 000
                densities[bottom+1] = one9th*(1 + 3*v + 3*v*v); // 100
                densities[bottom+4] = one9th*(1 - 3*v + 3*v*v); // -100
                densities[bottom+7] = one9th*(1 - 1.5f*v*v); // 010
                densities[bottom+8] = one9th*(1 - 1.5f*v*v); // 0-10
                densities[bottom+2] = one36th*(1 + 3*v + 3*v*v); // 110
                densities[bottom+3] = one36th*(1 + 3*v + 3*v*v); // 1-10
                densities[bottom+5] = one36th*(1 - 3*v + 3*v*v); // -110
                densities[bottom+6] = one36th*(1 - 3*v + 3*v*v); // -1-10
            }
        } else {
            // set the inlet (left) and outlet (right) (directions pointing into the grid), top, bottom, front and back faces, to equilibrium (aka inject new fluid)
            v = settings.getFlowSpeed();
            one15vv = 1-1.5f*v*v;
            one3v3vv = 1+3*v+3*v*v;
            one_3v3vv = 1-3*v+3*v*v;

            for (int z=0; z<nz; z++) {
                for (int y=0; y<ny; y++) {
                    int inlet = (z*ny + y)*nx;
                    if (!barriers[inlet]) { // inlet (left) (directions pointing into the grid)
                        int b = inlet*19;
                        densities[b+1] = one18th * one3v3vv;
                        densities[b+2] = one36th * one3v3vv;
                        densities[b+3] = one36th * one3v3vv;
                        densities[b+10] = one36th * one3v3vv;
                        densities[b+15] = one36th * one3v3vv;
                    }
                    int outlet = inlet + nx-1;
                    if (!barriers[outlet]) { // outlet (right) (directions pointing into the grid)
                        int b = outlet*19;
                        densities[b+4] = one18th * one_3v3vv;
                        densities[b+5] = one36th * one_3v3vv;
                        densities[b+6] = one36th * one_3v3vv;
                        densities[b+11] = one36th * one_3v3vv;
                        densities[b+16] = one36th * one_3v3vv;
                    }
                    if (!(y == 0 || y == ny-1 || z == 0 || z == nz-1)) {continue;} // only the top, bottom, front and back faces from here
                    for (int x=1; x<nx-1; x++) {
                        int i = inlet + x;
                        if (barriers[i]) {continue;} // skip all barriers
                        int b = i*19;
                        densities[b] = one3rd * one15vv;
                        densities[b+1] = one18th * one3v3vv;
                        densities[b+4] = one18th * one_3v3vv;
                        densities[b+7] = one18th * one15vv;
                        densities[b+8] = one18th * one15vv;
                        densities[b+9] = one18th * one15vv;
                        densities[b+14] = one18th * one15vv;
                        densities[b+2] = one36th * one3v3vv;
                        densities[b+3] = one36th * one3v3vv;
                        densities[b+5] = one36th * one_3v3vv;
                        densities[b+6] = one36th * one_3v3vv;
                        densities[b+10] = one36th * one3v3vv;
                        densities[b+11] = one36th * one_3v3vv;
                        densities[b+12] = one36th * one15vv;
                        densities[b+13] = one36th * one15vv;
                        densities[b+15] = one36th * one3v3vv;
                        densities[b+16] = one36th * one_3v3vv;
                        densities[b+17] = one36th * one15vv;
                        densities[b+18] = one36th * one15vv;
                    }
                }
            }
        }
    }

    public void bounce() {
        // "bounce" the fluid off barriers
        updateBarrierCells();
        float[] f = densities;
        int q = neighbours;
        int row = gridX; // index difference between neighbours in y
        int plane = gridX*gridY; // index difference between neighbours in z

        for (int k=0; k<barrierCount; k++) {
            int i = barrierCells[k];
            int b = i*q;
            if (f[b+7] > 0) {f[(i-row)*q+8] += f[b+7];} // 010
            if (f[b+8] > 0) {f[(i+row)*q+7] += f[b+8];} // 0-10
            if (f[b+1] > 0) {f[(i-1)*q+4] += f[b+1];} // 100
            if (f[b+4] > 0) {f[(i+1)*q+1] += f[b+4];} // -100

            if (f[b+2] > 0) {f[(i-1-row)*q+6] += f[b+2];} // 110
            if (f[b+5] > 0) {f[(i+1-row)*q+3] += f[b+5];} // -110
            if (f[b+6] > 0) {f[(i+1+row)*q+2] += f[b+6];} // -1-10
            if (f[b+3] > 0) {f[(i-1+row)*q+5] += f[b+3];} // 1-10

            if (q == 19) {
                if (f[b+9] > 0) {f[(i+plane)*q+14] += f[b+9];} // 00-1
                if (f[b+10] > 0) {f[(i-1+plane)*q+16] += f[b+10];} // 10-1
                if (f[b+11] > 0) {f[(i+1+plane)*q+15] += f[b+11];} // -10-1
                if (f[b+12] > 0) {f[(i-row+plane)*q+18] += f[b+12];} // 01-1
                if (f[b+13] > 0) {f[(i+row+plane)*q+17] += f[b+13];} // 0-1-1
                if (f[b+14] > 0) {f[(i-plane)*q+9] += f[b+14];} // 001
                if (f[b+15] > 0) {f[(i-1-plane)*q+11] += f[b+15];} // 101
                if (f[b+16] > 0) {f[(i+1-plane)*q+10] += f[b+16];} // -101
                if (f[b+17] > 0) {f[(i-row-plane)*q+13] += f[b+17];} // 011
                if (f[b+18] > 0) {f[(i+row-plane)*q+12] += f[b+18];} // 0-11
            }
        }
        zeroBarriers();
    }

    public void render(ShapeRenderer sr, SpriteBatch batch) {
        // call this outside sr.begin()/batch.begin(), as it starts and ends its own drawing
        if (settings.getSolver().equals("2D LBM")) {
            cellDimensions = 1920/settings.getResolution().x;
        }

        drawBarriers();

        if (settings.getSolver().equals("2D LBM")) {
            renderField(batch);
        } else {
            renderPoints(sr);
        }
    }

    private void renderField(SpriteBatch batch) {
        if (fieldPixmap == null || fieldPixmap.getWidth() != gridX || fieldPixmap.getHeight() != gridY) {
            dispose();
            fieldPixmap = new Pixmap(gridX, gridY, Pixmap.Format.RGBA8888);
            fieldTexture = new Texture(fieldPixmap);
            fieldTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // keeps each cell a sharp square when stretched
            fieldPixels = new int[gridX*gridY];
            fieldChanged = true;
        }

        // the picture is only rebuilt when the fluid, the barriers or a setting that changes the colours has changed
        if (fieldChanged || !settings.getPlot().equals(drawnPlot) || settings.getFlowSpeed() != drawnFlowSpeed || settings.getLoadedLevel() != drawnLoadedLevel) {
            fillField();
            fieldChanged = false;
            drawnPlot = settings.getPlot();
            drawnFlowSpeed = settings.getFlowSpeed();
            drawnLoadedLevel = settings.getLoadedLevel();
        }

        batch.begin();
        batch.draw(fieldTexture, 0, 0, gridX*cellDimensions, gridY*cellDimensions);
        batch.end();
    }

    private void fillField() {
        // the plot, contrast and level don't change during a frame, so they are worked out once rather than for every cell
        final int component = colourComponent();
        final float contrast = 1/(3*settings.getFlowSpeed());
        final boolean loadedLevel = settings.getLoadedLevel();

        forEachSlab(y -> {
            int row = (gridY-1-y)*gridX; // a Pixmap's first row is the top of the picture, but y = 0 is the bottom of the simulation
            for (int x=0; x<gridX; x++) {
                int i = y*gridX + x;
                if (loadedLevel && settings.getFixedBarrierAt(x, y, 0)) {fieldPixels[row+x] = black;}
                else if (barriers[i]) {fieldPixels[row+x] = white;}
                else {fieldPixels[row+x] = packedColours[colourIndex(i, component, contrast)];}
            }
        });

        // RGBA8888 stores each pixel as the bytes red, green, blue, alpha, which is the big-endian order of an int
        ByteBuffer pixels = fieldPixmap.getPixels();
        pixels.order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(fieldPixels);
        fieldTexture.draw(fieldPixmap, 0, 0); // one upload to the graphics card
    }

    private void renderPoints(ShapeRenderer sr) {
        renderer.prepareFrame(); // the rotation and projection are the same for every point, so they are worked out once per frame
        int component = colourComponent();
        float contrast = 1/(3*settings.getFlowSpeed());
        boolean loadedLevel = settings.getLoadedLevel();

        sr.begin(ShapeRenderer.ShapeType.Filled);
        if (settings.getRenderOnlySelectedZPlane()) {
            int z = settings.getSelectedZPlane();
            if (z >= 0 && z < gridZ) {
                for (int x=0; x<gridX; x++) {
                    for (int y=0; y<gridY; y++) {
                        drawPoint(sr, x, y, z, component, contrast, loadedLevel);
                    }
                }
            }
        } else {
            // only the outside of the box and the barriers are drawn, so those cells are visited directly instead of searching the whole grid
            for (int z=0; z<gridZ; z++) {
                for (int y=0; y<gridY; y++) {
                    drawPoint(sr, 0, y, z, component, contrast, loadedLevel); // left face
                    drawPoint(sr, gridX-1, y, z, component, contrast, loadedLevel); // right face
                }
            }
            for (int z=0; z<gridZ; z++) {
                for (int x=1; x<gridX-1; x++) {
                    drawPoint(sr, x, 0, z, component, contrast, loadedLevel); // bottom face
                    drawPoint(sr, x, gridY-1, z, component, contrast, loadedLevel); // top face
                }
            }
            for (int y=1; y<gridY-1; y++) {
                for (int x=1; x<gridX-1; x++) {
                    drawPoint(sr, x, y, 0, component, contrast, loadedLevel); // front face
                    drawPoint(sr, x, y, gridZ-1, component, contrast, loadedLevel); // back face
                }
            }
            updateBarrierCells();
            for (int k=0; k<barrierCount; k++) {
                int i = barrierCells[k];
                int x = i%gridX;
                int y = (i/gridX)%gridY;
                int z = i/(gridX*gridY);
                if (x == 0 || y == 0 || z == 0 || x == gridX-1 || y == gridY-1 || z == gridZ-1) {continue;} // already drawn as part of a face
                drawPoint(sr, x, y, z, component, contrast, loadedLevel);
            }
        }
        sr.end();
    }

    private void drawPoint(ShapeRenderer sr, int x, int y, int z, int component, float contrast, boolean loadedLevel) {
        int i = (z*gridY + y)*gridX + x;
        if (loadedLevel && settings.getFixedBarrierAt(x, y, z)) {sr.setColor(Color.BLACK);}
        else if (barriers[i]) {sr.setColor(Color.WHITE);}
        else {sr.setColor(colours.get(colourIndex(i, component, contrast)));} // calculates colour based on plotMode

        rotatedPoint = renderer.rotate(x-(gridX/2f), y-(gridY/2f), z-(gridZ/2f));
        screenPos = renderer.pointProjection(rotatedPoint);
        if (screenPos == null) {return;}
        sr.rect(screenPos.x-1, screenPos.y-1, 2, 2); // about the same size as a radius 1 circle, with 6 corners instead of 18
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

    public boolean[] scaleBarriersUsingResolution() {
        int newX = (int) settings.getResolution().x;
        int newY = (int) settings.getResolution().y;
        int newZ = (int) settings.getResolution().z;
        boolean[] newBarriers = new boolean[newX*newY*newZ];

        if (barriers == null) {
            return newBarriers;
        }
        if (!settings.getSolver().equals(settings.getOldSolver())) {
            return newBarriers;
        }

        // the old size is the size the stored barriers were made for, as settings.reset() (used when loading a level) changes the old resolution setting but not the barriers
        Vector3 oldResolution = new Vector3(gridX, gridY, gridZ);

        if (settings.getResolution().equals(oldResolution)) {
            return barriers; // no change in resolution
        }
        Vector3 scale;

        if (settings.getResolution().x > oldResolution.x) {
            scale = new Vector3(settings.getResolution().x/oldResolution.x, settings.getResolution().y/oldResolution.y, settings.getResolution().z/oldResolution.z);
            for (int x=0; x<newX; x++) {
                for (int y=0; y<newY; y++) {
                    for (int z=0; z<newZ; z++) {
                        int oldX = (int) (x/scale.x);
                        int oldY = (int) (y/scale.y);
                        int oldZ = (int) (z/scale.z);
                        newBarriers[(z*newY + y)*newX + x] = barriers[(oldZ*gridY + oldY)*gridX + oldX];
                    }
                }
            }
        } else {
            scale = new Vector3(oldResolution.x/settings.getResolution().x, oldResolution.y/settings.getResolution().y, oldResolution.z/settings.getResolution().z);
            for (int x=0; x<newX; x++) {
                for (int y=0; y<newY; y++) {
                    for (int z=0; z<newZ; z++) {
                        int barrierCount = 0;
                        for (int dx=0; dx<scale.x; dx++) {
                            for (int dy=0; dy<scale.y; dy++) {
                                for (int dz=0; dz<scale.z; dz++) {
                                    int oldX = (int) ((x*scale.x)+dx);
                                    int oldY = (int) ((y*scale.y)+dy);
                                    int oldZ = (int) ((z*scale.z)+dz);
                                    if (barriers[(oldZ*gridY + oldY)*gridX + oldX]) {
                                        barrierCount += 1;
                                    }
                                }
                            }
                        }
                        if (barrierCount*2 >= scale.x*scale.y*scale.z) { // more than half the cells are barriers
                            newBarriers[(z*newY + y)*newX + x] = true;
                        }
                    }
                }
            }
        }
        settings.setOldResolution(new Vector3(settings.getResolution()));
        return newBarriers;
    }

    public ArrayList<Color> calculateColours(int numOfColours) {
        // the scale runs through these five colours, blending the red, green and blue amounts in a straight line between each pair
        float[][] keyColours = {
            {0, 0, 1}, // blue (slowest)
            {0, 1, 1}, // cyan
            {0, 1, 0}, // green
            {1, 1, 0}, // yellow
            {1, 0, 0}, // red (fastest)
        };

        colours = new ArrayList<>();
        for (int c=0; c<numOfColours; c++) {
            // how far along the scale this colour is, from 0 (blue) to 1 (red), nudged by a small sine wave so that
            // the shades around cyan and yellow take up a little more of the scale
            double position = (double) c/numOfColours + 0.045*Math.sin(4*Math.PI*c/numOfColours);
            position = Math.max(0, Math.min(1, position));

            int pair = Math.min((int) (position*4), 3); // which two key colours this position is between
            double blend = position*4 - pair; // 0 at the first of the two, 1 at the second
            float[] from = keyColours[pair];
            float[] to = keyColours[pair+1];

            float r = toEightBit(from[0] + blend*(to[0]-from[0]));
            float g = toEightBit(from[1] + blend*(to[1]-from[1]));
            float b = toEightBit(from[2] + blend*(to[2]-from[2]));
            colours.add(new Color(r, g, b, 1f));
        }
        return colours;
    }

    // rounds a 0 to 1 colour amount to the nearest of the 256 levels a pixel can show
    private float toEightBit(double amount) {
        return Math.round(amount*255)/255f;
    }

    // which velocity the colours show: 0 for x, 1 for y, or -1 for a plot that isn't drawn yet (speed, density and curl), which uses the first colour
    private int colourComponent() {
        if (settings.getPlot().equals("x velocity")) {return 0;}
        if (settings.getPlot().equals("y velocity")) {return 1;}
        return -1;
    }

    private int colourIndex(int i, int component, float contrast) {
        double index = 0;
        if (component >= 0) {
            index = numOfColors*(0.5+(velocities[i*3 + component]*contrast));
        }
        if (index < 0) {index = 0;}
        if (index >= numOfColors) {index = numOfColors-1;}
        return (int) index;
    }

    public void addBarrier(int x, int y, int z, boolean fixed) {
        if (x > 0 && x < gridX-1 && y > 0 && y < gridY-1) { // in simulation area
            if (neighbours == 19 && (z <= 0 || z >= gridZ-1)) { // 3D and outside
                return;
            }
            int i = (z*gridY + y)*gridX + x;
            if (!barriers[i]) {
                if (settings.getLoadedLevel()) {
                    if (settings.getAvailableBarriers() > 0) {
                        settings.setAvailableBarriers(settings.getAvailableBarriers()-1);
                        setBarrier(i, true);
                    }
                } else {
                    setBarrier(i, true);
                }
                if (fixed) {settings.addFixedBarrier(x, y, z);}
            }
        }
    }

    public void removeBarrier(int x, int y, int z) {
        int i = (z*gridY + y)*gridX + x;
        if (settings.getLoadedLevel()) {
            if (!settings.getFixedBarrierAt(x, y, z)) {
                settings.setAvailableBarriers(settings.getAvailableBarriers()+1);
                setBarrier(i, false);
                initialiseCellAt(i);
            }
        } else {
            setBarrier(i, false);
            initialiseCellAt(i);
        }
    }

    private void setBarrier(int i, boolean barrier) {
        barriers[i] = barrier;
        barrierCellsOutOfDate = true;
        fieldChanged = true;
    }

    public void removeAllBarriers() {
        updateBarrierCells();
        // removeBarrier() marks the list out of date but doesn't change it, so it is safe to keep reading it here
        int[] cells = barrierCells;
        int count = barrierCount;
        for (int k=0; k<count; k++) {
            int i = cells[k];
            removeBarrier(i%gridX, (i/gridX)%gridY, i/(gridX*gridY));
        }
    }

    public void zeroBarriers() {
        updateBarrierCells();
        for (int k=0; k<barrierCount; k++) {
            int b = barrierCells[k]*neighbours;
            Arrays.fill(densities, b, b+neighbours, 0);
        }
    }

    public boolean isBarrier(int x, int y, int z) {
        return barriers[(z*gridY + y)*gridX + x];
    }

    // rebuilds the list of barrier cells after barriers have been added or removed, by searching the grid once
    private void updateBarrierCells() {
        if (!barrierCellsOutOfDate) {return;}
        barrierCount = 0;
        for (int i=0; i<barriers.length; i++) {
            if (barriers[i]) {
                if (barrierCount == barrierCells.length) {
                    barrierCells = Arrays.copyOf(barrierCells, Math.max(64, barrierCount*2));
                }
                barrierCells[barrierCount] = i;
                barrierCount += 1;
            }
        }
        barrierCellsOutOfDate = false;
    }

    // a slab is one row of cells in 2D, or one z plane in 3D
    private int slabSize() {
        return gridZ > 1 ? gridX*gridY : gridX;
    }

    private void forEachSlab(IntConsumer action) {
        IntStream slabs = IntStream.range(0, gridZ > 1 ? gridZ : gridY);
        if (cellCount >= PARALLEL_THRESHOLD) {
            slabs = slabs.parallel(); // Java shares the slabs out between all the CPU cores
        }
        slabs.forEach(action);
    }

    public void dispose() {
        if (fieldPixmap != null) {
            fieldPixmap.dispose();
            fieldTexture.dispose();
            fieldPixmap = null;
            fieldTexture = null;
        }
    }
}
