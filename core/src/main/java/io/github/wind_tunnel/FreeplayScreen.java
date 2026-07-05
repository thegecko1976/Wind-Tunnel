package io.github.wind_tunnel;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;

public class FreeplayScreen implements Screen, Scene3DScreen {
    private final Settings settings;
    private final LatticeBoltzmannCFDSolver cfdSolver;

    private final Node root = new Node("freeplay-root");
    private final Node sceneNode = new Node("freeplay-cuboid-node");
    private final Geometry cuboidGeometry;

    private final Button runButton;
    private final Button pauseButton;

    public FreeplayScreen(WindTunnelApp app) {
        this.settings = Settings.getInstance();
        this.cfdSolver = new LatticeBoltzmannCFDSolver();

        Material material = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", ColorRGBA.White);
        cuboidGeometry = new Geometry("freeplay-cuboid", cfdSolver.buildPointCloudMesh());
        cuboidGeometry.setMaterial(material);
        sceneNode.attachChild(cuboidGeometry);

        Button back = new Button("");
        back.setIcon(new IconComponent("icons/back.png"));
        back.setBackground(null);
        root.attachChild(back);
        LayoutHelper.anchorCentered(back, 57.5f, 1022.5f, 75, 75);
        back.addClickCommands(source -> app.navigateTo("back"));

        Button settingsButton = new Button("");
        settingsButton.setIcon(new IconComponent("icons/settings.png"));
        settingsButton.setBackground(null);
        root.attachChild(settingsButton);
        LayoutHelper.anchorCentered(settingsButton, 1862.5f, 57.5f, 75, 75);
        settingsButton.addClickCommands(source -> app.navigateTo("settings"));

        runButton = new Button("run");
        runButton.setColor(ColorRGBA.White);
        runButton.setTextHAlignment(HAlignment.Center);
        runButton.setBackground(new QuadBackgroundComponent(WindTunnelColors.RUN_BUTTON));
        root.attachChild(runButton);
        LayoutHelper.anchorCentered(runButton, 835, 62.5f, 200, 75);
        runButton.addClickCommands(source -> {
            settings.setSimulationRunning(true);
            updateRunPauseVisibility();
        });

        pauseButton = new Button("pause");
        pauseButton.setColor(ColorRGBA.White);
        pauseButton.setTextHAlignment(HAlignment.Center);
        pauseButton.setIcon(new IconComponent("icons/pause.png"));
        pauseButton.setBackground(new QuadBackgroundComponent(WindTunnelColors.PAUSE_BUTTON));
        root.attachChild(pauseButton);
        LayoutHelper.anchorCentered(pauseButton, 835, 62.5f, 200, 75);
        pauseButton.addClickCommands(source -> {
            settings.setSimulationRunning(false);
            updateRunPauseVisibility();
        });

        Button stepButton = new Button("step");
        stepButton.setColor(ColorRGBA.White);
        stepButton.setTextHAlignment(HAlignment.Center);
        stepButton.setIcon(new IconComponent("icons/stepArrow.png"));
        stepButton.setBackground(new QuadBackgroundComponent(WindTunnelColors.STEP_BUTTON));
        root.attachChild(stepButton);
        LayoutHelper.anchorCentered(stepButton, 1085, 62.5f, 200, 75);
        stepButton.addClickCommands(source -> {
            if (!settings.getSimulationRunning()) System.out.println("step simulation");
        });

        updateRunPauseVisibility();
    }

    private void updateRunPauseVisibility() {
        boolean running = settings.getSimulationRunning();
        runButton.setCullHint(running ? Spatial.CullHint.Always : Spatial.CullHint.Never);
        pauseButton.setCullHint(running ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }

    @Override
    public void onEnter() {
        cfdSolver.initialiseFluid();
        cuboidGeometry.setMesh(cfdSolver.buildPointCloudMesh());
        updateRunPauseVisibility();
    }

    @Override
    public Spatial getRoot() {
        return root;
    }

    @Override
    public Node getSceneNode() {
        return sceneNode;
    }
}
