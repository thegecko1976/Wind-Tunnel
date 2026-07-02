package io.github.wind_tunnel;

import com.badlogic.gdx.graphics.Color;
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
    private double[][][][] densities;
    private Integer[][] relativeDirections = {
        {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {1, -1, 0}, {-1, 0, 0}, {-1, 1, 0}, {-1, -1, 0}, {0, 1, 0}, {0, -1, 0},
        {0, 0, -1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, -1}, {0, -1, -1}, {0, 0, 1}, {1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1}
    };
    private ArrayList<String> barriers = new ArrayList<>(); // the xyz coords are stored as a String, separated by spaces, for example, 32 2 54
    private Integer neighbours;

    private Vector3 rotatedPoint = new Vector3();
    private Vector2 screenPos;
    private Vector3 origin;
    private Vector3 originX;
    private Vector3 originY;
    private Vector3 originZ;

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
    ArrayList<Color> colours = new ArrayList<>();

    public LatticeBoltzmannCFDSolver() {
        this.util = new MenuUtil();
        this.settings = Settings.getInstance();
        this.renderer = new ThreeDimensionalRenderer();
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

    public void render(ShapeRenderer sr) {
        if (settings.getSolver() == "2D LBM") {
            cellDimensions = 1920/settings.getResolution().x;
        } /*else {
            origin = renderer.rotate(-(settings.getResolution().x/2), -(settings.getResolution().x/2), -(settings.getResolution().x/2));
            originX = renderer.rotate(settings.getResolution().x/2, -(settings.getResolution().x/2), -(settings.getResolution().x/2));
            originY = renderer.rotate(-(settings.getResolution().x/2), settings.getResolution().x/2, -(settings.getResolution().x/2));
            originZ = renderer.rotate(-(settings.getResolution().x/2), -(settings.getResolution().x/2), settings.getResolution().x/2);
        }*/

        for (int x=0; x<settings.getResolution().x; x++) {
            for (int y=0; y<settings.getResolution().y; y++) {
                for (int z=0; z<settings.getResolution().z; z++) {
                    if (!(x == 0 || y == 0 || z == 0 || x == settings.getResolution().x-1 || y == settings.getResolution().y-1 || z == settings.getResolution().z-1)) {continue;}
                    //sr.setColor(colours.get(calculateColourIndex(cell's xVelocity)));
                    sr.setColor(1f, 1f, 1f, 1f);
                    if (settings.getSolver() == "2D LBM") {
                        sr.circle((x+0.5f)*cellDimensions, (y+0.5f)*cellDimensions, 1);
                        //sr.rect(x*cellDimensions, y*cellDimensions, cellDimensions, cellDimensions);
                    } else {
                        /*rotatedPoint.x = (originX.x*x + originY.y*x + originZ.z*x);
                        rotatedPoint.y = (originX.x*y + originY.y*y + originZ.z*y);
                        rotatedPoint.z = (originX.x*z + originY.y*z + originZ.z*z);*/
                        rotatedPoint = renderer.rotate(x-(settings.getResolution().x/2), y-(settings.getResolution().y/2), z-(settings.getResolution().z/2));
                        screenPos = renderer.pointProjection(rotatedPoint);
                        if (screenPos == null) continue;
                        sr.circle(screenPos.x, screenPos.y, 1);
                    }
                }
            }
        }
    }

    public ArrayList<Color> calculateColours(ArrayList<Color> colours, int numOfColours) {
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

