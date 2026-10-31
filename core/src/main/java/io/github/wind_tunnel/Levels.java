package io.github.wind_tunnel;

import com.badlogic.gdx.math.Vector3;

public class Levels {
    private Settings settings;
    private LatticeBoltzmannCFDSolver cfdSolver;
    private ShapeDrawer shapeDrawer;
    private ImportFile fileImporter;

    private int levelNumber;
    private String levelsFolder;

    // lockedSettings: resolution, flowspeed, viscosity, plot, brushSize, solver, barrierShapes, clearBarriers, showFlowLines, renderUI, renderSelectedZPlane

    public Levels() {
        settings = Settings.getInstance();
        cfdSolver = LatticeBoltzmannCFDSolver.getInstance();
        shapeDrawer = new ShapeDrawer();
        fileImporter = new ImportFile();
        levelsFolder = fileImporter.getLevelsFolder();
    }

    public void loadSelectedLevel() {
        settings.reset();
        levelNumber = settings.getLevelNumber();
        if (levelNumber == 1) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[2][0], settings.getResolutionValues()[2][1], 1)); // 128x72x1
        } else if (levelNumber == 2) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[2][0], settings.getResolutionValues()[2][1], 1)); // 128x72x1
        } else if (levelNumber == 3) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[3][0], settings.getResolutionValues()[3][1], 1)); // 256x144x1
        } else if (levelNumber == 4) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[3][0], settings.getResolutionValues()[3][1], 1)); // 256x144x1
        } else if (levelNumber == 5) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[2][0], settings.getResolutionValues()[2][1], 1)); // 128x72x1
        } else if (levelNumber == 6) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[4][0], settings.getResolutionValues()[4][1], 1)); // 512x288x1
        } else if (levelNumber == 7) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[2][0], settings.getResolutionValues()[2][1], 1)); // 128x72x1
        } else if (levelNumber == 8) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[2][0], settings.getResolutionValues()[2][1], 1)); // 128x72x1
        } else if (levelNumber == 9) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[4][0], settings.getResolutionValues()[4][1], 1)); // 512x288x1
        } else if (levelNumber == 10) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[4][0], settings.getResolutionValues()[4][1], 1)); // 512x288x1
        } else if (levelNumber == 11) {
            settings.setResolution(new Vector3(settings.getResolutionValues()[2][0], settings.getResolutionValues()[2][1], settings.getResolutionValues()[2][1])); // 128x72x72
        }
        // the solver must be set before initialiseFluid(), which sizes the fluid for 2D (9 directions) or 3D (19)
        if (levelNumber >= 1 && levelNumber <= 10) {
            settings.setSolver("2D LBM");
        } else if (levelNumber >= 11 && levelNumber <= 20) {
            settings.setSolver("3D LBM");
        }
        cfdSolver.initialiseFluid();
        settings.resetLockedSettings();
        settings.changeBrushSizeAndCameraDistance();

        settings.setLoadedLevel(false);
        cfdSolver.removeAllBarriers();
        settings.setLoadedLevel(true);

        settings.setFlowSpeed(0.100f);
        settings.setViscosity(0.015f);
        settings.setLockedSettingAt(0, true);
        settings.setLockedSettingAt(5, true);
        settings.setLockedSettingAt(1, true);
        settings.setLockedSettingAt(2, true);
        settings.setLockedSettingAt(6, true);
        settings.setLockedSettingAt(11, true);

        if (levelNumber == 1) {
            settings.setAvailableBarriers(100);
            settings.setMaxBarriers(100);
        } else if (levelNumber == 2) {
            settings.setAvailableBarriers(100);
            settings.setMaxBarriers(100);
        } else if (levelNumber == 3) {
            settings.setAvailableBarriers(100);
            settings.setMaxBarriers(100);
        } else if (levelNumber == 4) {
            settings.setAvailableBarriers(100);
            settings.setMaxBarriers(100);
        } else if (levelNumber == 5) {
            settings.setAvailableBarriers(100);
            settings.setMaxBarriers(100);
        } else if (levelNumber == 6) {
            settings.setAvailableBarriers(200);
            settings.setMaxBarriers(200);
        } else if (levelNumber == 7) {
            settings.setAvailableBarriers(100);
            settings.setMaxBarriers(100);
        } else if (levelNumber == 8) {
            settings.setAvailableBarriers(100);
            settings.setMaxBarriers(100);
        } else if (levelNumber == 9) {
            settings.setAvailableBarriers(200);
            settings.setMaxBarriers(200);
        } else if (levelNumber == 10) {
            settings.setAvailableBarriers(200);
            settings.setMaxBarriers(200);
        } else if (levelNumber == 11) {
            settings.setAvailableBarriers(250);
            settings.setMaxBarriers(250);
        }

        settings.setFixedBarriers(new boolean[(int) settings.getResolution().x][(int) settings.getResolution().y][(int) settings.getResolution().z]);
        /*
        level ideas:
        1: vertical line
        2: circle
        3: airfoil
        4: pizza slice
        5: apple
        6: car
        7: stick man
        8: smiley face (verity)
        9: "hello world" as text
        10: troll face

        11: cube
        */
        settings.setLoadedLevel(false); // don't count the fixed barriers as part of the available barriers
        if (levelNumber == 1) {
            shapeDrawer.line(new Vector3(50, 30, 0), new Vector3(50, 42, 0), true);
        } else if (levelNumber == 2) {
            shapeDrawer.circle(48, 36, 0, 8, false, true);
        } else if (levelNumber == 3) {
            fileImporter.loadBitmap(levelsFolder + "airfoil.bmp", true);
        } else if (levelNumber == 4) {
            fileImporter.loadBitmap(levelsFolder + "pizza_slice.bmp", true);
        } else if (levelNumber == 5) {
            fileImporter.loadBitmap(levelsFolder + "apple.bmp", true);
        } else if (levelNumber == 6) {
            fileImporter.loadBitmap(levelsFolder + "f1_car.bmp", true);
        } else if (levelNumber == 7) {
            fileImporter.loadBitmap(levelsFolder + "stickman.bmp", true);
        } else if (levelNumber == 8) {
            fileImporter.loadBitmap(levelsFolder + "nerd_face.bmp", true);
        } else if (levelNumber == 9) {
            fileImporter.loadBitmap(levelsFolder + "hello_world.bmp", true);
        } else if (levelNumber == 10) {
            fileImporter.loadBitmap(levelsFolder + "trollface.bmp", true);
        } else if (levelNumber == 11) {
            fileImporter.loadOBJModel("assets/models/icosphere.obj", true); // fixed, like the shapes in the 2D levels, so the player can't erase it
        }
        settings.setLoadedLevel(true);
    }
}
