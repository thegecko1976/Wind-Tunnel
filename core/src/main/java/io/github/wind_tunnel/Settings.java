package io.github.wind_tunnel;

import com.badlogic.gdx.math.Vector3;
import java.util.Arrays;

public class Settings {

    private Vector3 resolution;
    private String solver;
    private float flowSpeed;
    private float viscosity;
    private String plot;
    private boolean showFlowLines;
    private boolean renderUI;
    private boolean renderOnlySelectedZPlane;
    private int brushSize;

    private String[] plotValues = {"speed", "x velocity", "y velocity", "density", "curl"};
    private int[][] resolutionValues = {{32, 18}, {64, 36}, {128, 72}, {256, 144}, {512, 288}, {1024, 576}}; // keep the 16:9 aspect ratio
    // 3D only goes up to 256x144x144 (about 0.9 GB); 512x288x288 would need about 7 GB and 1024x576x576 is too big for a Java array
    private int largest3DResolution = 3;
    private String[] solverValues = {"2D LBM", "3D LBM"};

    private boolean simulationRunning = false;
    private boolean placeBarriers = false;

    private Vector3 rotationAngles;
    private int cameraDistance;
    private int fov;
    private Vector3 origin;

    private int selectedZPlane;
    private Vector3 intersectionPoint;

    private boolean loadedLevel = false;
    private Integer levelNumber = 1;
    private boolean[] lockedSettings = new boolean[12]; // resolution, flowspeed, viscosity, plot, brushSize, solver, barrierShapes, clearBarriers, showFlowLines, renderUI, renderSelectedZPlane, importFileMenu
    private boolean[][][] fixedBarriers;
    private int availableBarriers = 0; // number of barriers left to use
    private int maxBarriers = 0; // total number of barriers allowed
    private float levelScore = 0;
    private String[] levelNames = {"line", "circle", "airfoil", "pizza slice", "apple", "car" ,"stick man", ":)", "hello world", "troll face", "cube"};

    private Vector3 oldResolution;
    private String oldSolver;

    private static Settings instance;

    public static Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }

    private Settings() {
        reset();
    }

    public void reset() {
        this.resolution = new Vector3(this.resolutionValues[2][0], this.resolutionValues[2][1], 1);
        this.solver = this.solverValues[0];
        this.flowSpeed = 0.100f;
        this.viscosity = 0.020f;
        this.plot = this.plotValues[1];
        this.showFlowLines = false;
        this.renderUI = true;
        this.renderOnlySelectedZPlane = false;
        this.brushSize = 4;

        this.rotationAngles = new Vector3(0, 0, 0);
        this.cameraDistance = 100;
        this.fov = 95;
        this.origin = new Vector3(0, 0, 0);
        this.selectedZPlane = 0;
        this.intersectionPoint = new Vector3(0, 0, 0);

        this.fixedBarriers = new boolean[(int) this.resolution.x][(int) this.resolution.y][(int) this.resolution.z];
        this.oldResolution = this.resolution;
        this.oldSolver = this.solver;
    }

    public String cycleOptions(int change, String currentValue, String[] possibleValues) {
        if (currentValue.equals("2D LBM")) {
            resolution.z = resolution.y;
            if (resolution.x > resolutionValues[largest3DResolution][0]) { // too big for 3D, so use the largest 3D size
                int[] largest = resolutionValues[largest3DResolution];
                resolution = new Vector3(largest[0], largest[1], largest[1]);
            }
        } else if (currentValue.equals("3D LBM")) {
            resolution.z = 1;
        }

        int index = 0;
        for (int count=0; count<possibleValues.length; count++) {
            if (possibleValues[count].equals(currentValue)) {
                index = count;
                break;
            }
        }
        if (change == 1) {
            if (index == possibleValues.length-1) {
                return possibleValues[0];
            }
            return possibleValues[index+1];
        } else if (change == -1) {
            if (index == 0) {
                return possibleValues[possibleValues.length-1];
            }
            return possibleValues[index-1];
        }

        return currentValue;
    }

    public void cycleResolutionOptions(int change) {
        int options = solver.equals("3D LBM") ? largest3DResolution+1 : resolutionValues.length; // 3D stops at 256x144x144
        int index = 0;
        for (int count=0; count<resolutionValues.length; count++) {
            if (resolutionValues[count][0] == this.resolution.x && resolutionValues[count][1] == this.resolution.y) {
                index = count;
                break;
            }
        }
        if (change == 1) {
            if (index >= options-1) {
                this.resolution = new Vector3(resolutionValues[0][0], resolutionValues[0][1], resolutionValues[0][1]);
            } else {
                this.resolution = new Vector3(resolutionValues[index+1][0], resolutionValues[index+1][1], resolutionValues[index+1][1]);
            }
        } else if (change == -1) {
            if (index == 0) {
                this.resolution = new Vector3(resolutionValues[options-1][0], resolutionValues[options-1][1], resolutionValues[options-1][1]);
            } else if (index > options-1) { // a 3D size above the 3D limit (only if set from elsewhere), so drop to the largest allowed
                this.resolution = new Vector3(resolutionValues[options-1][0], resolutionValues[options-1][1], resolutionValues[options-1][1]);
            } else {
                this.resolution = new Vector3(resolutionValues[index-1][0], resolutionValues[index-1][1], resolutionValues[index-1][1]);
            }
        }
        changeBrushSizeAndCameraDistance();
    }

    public void changeBrushSizeAndCameraDistance() {
        int index = 0;
        for (int count=0; count<resolutionValues.length; count++) {
            if (resolutionValues[count][0] == this.resolution.x && resolutionValues[count][1] == this.resolution.y) {
                index = count;
                break;
            }
        }
        this.brushSize = (int) Math.pow(2, index); // 2^index -> 1, 2, 4, 8, 16, 32
        this.cameraDistance =  25*(int) Math.pow(2, index); // 25* 2^index -> 25, 50, 100, 200, 400, 800
    }

    public Vector3 getOrigin() {return this.origin;}
    public void setOrigin(Vector3 origin) {this.origin = origin;}

    public int getSelectedZPlane() {return this.selectedZPlane;}
    public void setSelectedZPlane(int selectedZPlane) {this.selectedZPlane = selectedZPlane;}
    public void moveSelectedZPlane(int direction) {
        if (selectedZPlane+direction >= 0 && selectedZPlane+direction < resolution.z) {
            selectedZPlane += direction;
        }
        if (selectedZPlane >= resolution.z) {
            selectedZPlane = (int) (resolution.z-1);
        }
    }

    public String getOldSolver() {return this.oldSolver;}
    public void setOldSolver(String oldSolver) {this.oldSolver = oldSolver;}

    public Vector3 getOldResolution() {return this.oldResolution;}
    public void setOldResolution(Vector3 oldResolution) {this.oldResolution = oldResolution;}

    public String getLevelNameAt(int index) {return this.levelNames[index];}
    public String[] getLevelNames() {return this.levelNames;}
    public void setLevelNames(String[] levelNames) {this.levelNames = levelNames;}

    public float getLevelScore() {return this.levelScore;}
    public void setLevelScore(float levelScore) {this.levelScore = levelScore;}

    public boolean getLoadedLevel() {return this.loadedLevel;}
    public void setLoadedLevel(boolean loadedLevel) {this.loadedLevel = loadedLevel;}

    public int getMaxBarriers() {return this.maxBarriers;}
    public void setMaxBarriers(int maxBarriers) {this.maxBarriers = maxBarriers;}

    public int getAvailableBarriers() {return this.availableBarriers;}
    public void setAvailableBarriers(int availableBarriers) {this.availableBarriers = availableBarriers;}

    public void resetLockedSettings() {Arrays.fill(this.lockedSettings, false);}
    public boolean getLockedSettingAt(int index) {return this.lockedSettings[index];}
    public void setLockedSettingAt(int index, boolean locked) {this.lockedSettings[index] = locked;}

    public void addFixedBarrier(int x, int y, int z) {this.fixedBarriers[x][y][z] = true;}
    public void removeFixedBarrier(int x, int y, int z) {this.fixedBarriers[x][y][z] = false;}
    public boolean getFixedBarrierAt(int x, int y, int z) {return this.fixedBarriers[x][y][z];}
    public void setFixedBarriers(boolean[][][] fixedBarriers) {this.fixedBarriers = fixedBarriers;}

    public Integer getLevelNumber() {return this.levelNumber;}
    public void setLevelNumber(Integer levelNumber) {this.levelNumber = levelNumber;}

    public Vector3 getIntersectionPoint() {return this.intersectionPoint;}
    public void setIntersectionPoint(Vector3 intersectionPoint) {this.intersectionPoint = intersectionPoint;}

    public boolean getRenderUI() {return this.renderUI;}
    public void setRenderUI(boolean renderUI) {this.renderUI = renderUI;}

    public boolean getRenderOnlySelectedZPlane() {return this.renderOnlySelectedZPlane;}
    public void setRenderOnlySelectedZPlane(boolean renderOnlySelectedZPlane) {this.renderOnlySelectedZPlane = renderOnlySelectedZPlane;}

    public int getBrushSize() {return this.brushSize;}
    public void setBrushSize(int brushSize) {this.brushSize = brushSize;}

    public int getCameraDistance() {return this.cameraDistance;}
    public void setCameraDistance(int cameraDistance) {if (cameraDistance > 0) {this.cameraDistance = cameraDistance;}}

    public int getFov() {return this.fov;}
    public void setFov(int fov) {this.fov = fov;}

    public Vector3 getRotationAngles() {return this.rotationAngles;}
    public void setRotationAngles(Vector3 angles) {this.rotationAngles = angles;}

    public float getRotationAnglesX() {return this.rotationAngles.x;}
    public void setRotationAnglesX(float angle) {this.rotationAngles.x = angle;}

    public float getRotationAnglesY() {return this.rotationAngles.y;}
    public void setRotationAnglesY(float angle) {this.rotationAngles.y = angle;}

    public float getRotationAnglesZ() {return this.rotationAngles.z;}
    public void setRotationAnglesZ(float angle) {this.rotationAngles.z = angle;}

    public boolean getSimulationRunning() {return this.simulationRunning;}
    public void setSimulationRunning(boolean simulationRunning) {this.simulationRunning = simulationRunning;}

    public String getSolver() {return this.solver;}
    public void setSolver(String solver) {this.solver = solver;}

    public Vector3 getResolution() {return this.resolution;}
    public void setResolution(Vector3 resolution) {this.resolution = resolution;}

    public float getFlowSpeed() {return this.flowSpeed;}
    public void setFlowSpeed(float flowSpeed) {this.flowSpeed = flowSpeed;}

    public float getViscosity() {return this.viscosity;}
    public void setViscosity(float viscosity) {this.viscosity = viscosity;}

    public String getPlot() {return this.plot;}
    public void setPlot(String plot) {this.plot = plot;}

    public boolean getShowFlowLines() {return this.showFlowLines;}
    public void setShowFlowLines(boolean showFlowLines) {this.showFlowLines = showFlowLines;}

    public String[] getPlotValues() {return this.plotValues;}
    public void setPlotValues(String[] plotValues) {this.plotValues = plotValues;}

    public int[][] getResolutionValues() {return this.resolutionValues;}
    public void setResolutionValues(int[][] resolutionValues) {this.resolutionValues = resolutionValues;}

    public String[] getSolverValues() {return this.solverValues;}
    public void setSolverValues(String[] solverValues) {this.solverValues = solverValues;}

    public boolean getPlaceBarriers() {return this.placeBarriers;}
    public void setPlaceBarriers(boolean placeBarriers) {this.placeBarriers = placeBarriers;}
}
