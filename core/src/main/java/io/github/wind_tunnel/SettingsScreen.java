package io.github.wind_tunnel;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Checkbox;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.ListBox;
import com.jme3.scene.Node;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.VersionedReference;

import java.text.DecimalFormat;
import java.util.Set;

public class SettingsScreen implements Screen {
    private final Settings settings = Settings.getInstance();
    private final DecimalFormat decimalFormat = new DecimalFormat("0.000");

    private final String[] barrierShapeValues = {"short line", "long line", "diagonal", "shallow diagonal",
        "small circle", "large circle", "line with spoiler", "circle with spoiler", "right angle", "wedge", "airfoil"};

    private final Node root = new Node("settings-root");

    private final Label resolutionValueLabel;
    private final Label solverValueLabel;
    private final Label flowSpeedValueLabel;
    private final Label viscosityValueLabel;
    private final Label plotValueLabel;
    private final Label modeValueLabel;

    private final ListBox<String> barrierShapesListBox = new ListBox<>();
    private boolean dropdownOpen = false;
    private VersionedReference<Set<Integer>> barrierSelectionRef;

    private final Panel clearBarrierButton;
    private final Panel resetFluidButton;
    private final Checkbox showFlowlinesCheckbox;

    public SettingsScreen(WindTunnelApp app) {
        Button back = new Button("");
        back.setIcon(new IconComponent("icons/back.png"));
        back.setBackground(null);
        root.attachChild(back);
        LayoutHelper.anchorCentered(back, 57.5f, 1022.5f, 75, 75);
        back.addClickCommands(source -> app.navigateTo("back"));

        Button settingsIconButton = new Button("");
        settingsIconButton.setIcon(new IconComponent("icons/settings.png"));
        settingsIconButton.setBackground(null);
        root.attachChild(settingsIconButton);
        LayoutHelper.anchorCentered(settingsIconButton, 1862.5f, 57.5f, 75, 75);
        settingsIconButton.addClickCommands(source -> app.navigateTo("settings"));

        addRowLabel("resolution", 320, 742.5f);
        addRowLabel("flow speed", 320, 642.5f);
        addRowLabel("viscosity", 320, 542.5f);
        addRowLabel("plot", 320, 442.5f);
        addRowLabel("mode", 320, 342.5f);
        addRowLabel("solver", 1040, 742.5f);
        addRowLabel("barrier shapes", 1040, 642.5f);

        resolutionValueLabel = addValueLabel(resolutionText(), 825, 742.5f);
        addCycleButtons(865, 742.5f, () -> { settings.cycleResolutionOptions(1); refreshValues(); },
            665, 742.5f, () -> { settings.cycleResolutionOptions(-1); refreshValues(); });

        solverValueLabel = addValueLabel(settings.getSolver(), 1545, 742.5f);
        addCycleButtons(1585, 742.5f, () -> { settings.setSolver(settings.cycleOptions(1, settings.getSolver(), settings.getSolverValues())); refreshValues(); },
            1385, 742.5f, () -> { settings.setSolver(settings.cycleOptions(-1, settings.getSolver(), settings.getSolverValues())); refreshValues(); });

        flowSpeedValueLabel = addValueLabel(decimalFormat.format(settings.getFlowSpeed()), 765, 642.5f);
        addCycleButtons(865, 642.5f, () -> { adjustFlowSpeed(0.005f); }, 665, 642.5f, () -> { adjustFlowSpeed(-0.005f); });

        viscosityValueLabel = addValueLabel(decimalFormat.format(settings.getViscosity()), 765, 542.5f);
        addCycleButtons(865, 542.5f, () -> { adjustViscosity(0.005f); }, 665, 542.5f, () -> { adjustViscosity(-0.005f); });

        plotValueLabel = addValueLabel(settings.getPlot(), 825, 442.5f);
        addCycleButtons(865, 442.5f, () -> { settings.setPlot(settings.cycleOptions(1, settings.getPlot(), settings.getPlotValues())); refreshValues(); },
            665, 442.5f, () -> { settings.setPlot(settings.cycleOptions(-1, settings.getPlot(), settings.getPlotValues())); refreshValues(); });

        modeValueLabel = addValueLabel(settings.getMode(), 825, 342.5f);
        addCycleButtons(865, 342.5f, () -> { settings.setMode(settings.cycleOptions(1, settings.getMode(), settings.getModeValues())); refreshValues(); },
            665, 342.5f, () -> { settings.setMode(settings.cycleOptions(-1, settings.getMode(), settings.getModeValues())); refreshValues(); });

        Button barrierShapesToggle = new Button("barrier shapes");
        barrierShapesToggle.setColor(ColorRGBA.White);
        barrierShapesToggle.setBackground(new QuadBackgroundComponent(WindTunnelColors.BUTTON));
        root.attachChild(barrierShapesToggle);
        LayoutHelper.anchorCentered(barrierShapesToggle, 1320, 642.5f, 600, 75);
        barrierShapesToggle.addClickCommands(source -> toggleDropdown());

        clearBarrierButton = addActionButton("clear barriers", 1320, 542.5f, () -> System.out.println("clear barriers"));
        resetFluidButton = addActionButton("reset fluid", 1320, 442.5f, () -> System.out.println("reset fluid"));

        showFlowlinesCheckbox = new Checkbox("show flowlines");
        showFlowlinesCheckbox.setColor(ColorRGBA.White);
        showFlowlinesCheckbox.setChecked(settings.getShowFlowLines());
        showFlowlinesCheckbox.setBackground(new QuadBackgroundComponent(WindTunnelColors.BUTTON));
        root.attachChild(showFlowlinesCheckbox);
        LayoutHelper.anchorCentered(showFlowlinesCheckbox, 1320, 342.5f, 600, 75);
        showFlowlinesCheckbox.addClickCommands(source -> settings.setShowFlowLines(showFlowlinesCheckbox.isChecked()));

        for (String shape : barrierShapeValues) {
            barrierShapesListBox.getModel().add(shape);
        }
        barrierShapesListBox.setVisibleItems(barrierShapeValues.length);
        LayoutHelper.anchorCentered(barrierShapesListBox, 1320, 605 - (0.5f * 50 * barrierShapeValues.length), 600, 50 * barrierShapeValues.length);
        barrierSelectionRef = barrierShapesListBox.getSelectionModel().createReference();
    }

    private void addRowLabel(String text, float centerX, float centerY) {
        Label label = new Label(text);
        label.setColor(ColorRGBA.White);
        label.setFontSize(24f);
        label.setTextHAlignment(HAlignment.Left);
        root.attachChild(label);
        LayoutHelper.anchorCentered(label, centerX, centerY, 600, 75);
    }

    private Label addValueLabel(String text, float centerX, float centerY) {
        Label label = new Label(text);
        label.setColor(ColorRGBA.White);
        label.setFontSize(24f);
        label.setTextHAlignment(HAlignment.Right);
        root.attachChild(label);
        LayoutHelper.anchorCentered(label, centerX, centerY);
        return label;
    }

    private void addCycleButtons(float nextX, float nextY, Runnable onNext, float prevX, float prevY, Runnable onPrev) {
        Button next = new Button(">");
        root.attachChild(next);
        LayoutHelper.anchorCentered(next, nextX, nextY, 30, 30);
        next.addClickCommands(source -> onNext.run());

        Button prev = new Button("<");
        root.attachChild(prev);
        LayoutHelper.anchorCentered(prev, prevX, prevY, 30, 30);
        prev.addClickCommands(source -> onPrev.run());
    }

    private Button addActionButton(String text, float centerX, float centerY, Runnable onClick) {
        Button button = new Button(text);
        button.setColor(ColorRGBA.White);
        button.setTextHAlignment(HAlignment.Left);
        button.setBackground(new QuadBackgroundComponent(WindTunnelColors.BUTTON));
        root.attachChild(button);
        LayoutHelper.anchorCentered(button, centerX, centerY, 600, 75);
        button.addClickCommands(source -> onClick.run());
        return button;
    }

    private void adjustFlowSpeed(float delta) {
        float newValue = settings.getFlowSpeed() + delta;
        if (delta > 0 && settings.getFlowSpeed() < 0.120) {
            settings.setFlowSpeed(Math.round(newValue * 1000) / 1000f);
        } else if (delta < 0 && settings.getFlowSpeed() > 0.005) {
            settings.setFlowSpeed(Math.round(newValue * 1000) / 1000f);
        }
        refreshValues();
    }

    private void adjustViscosity(float delta) {
        float newValue = settings.getViscosity() + delta;
        if (delta > 0 && settings.getViscosity() < 0.200) {
            settings.setViscosity(Math.round(newValue * 1000) / 1000f);
        } else if (delta < 0 && settings.getViscosity() > 0.005) {
            settings.setViscosity(Math.round(newValue * 1000) / 1000f);
        }
        refreshValues();
    }

    private void toggleDropdown() {
        dropdownOpen = !dropdownOpen;
        if (dropdownOpen) {
            root.attachChild(barrierShapesListBox);
            clearBarrierButton.setCullHint(Spatial.CullHint.Always);
            resetFluidButton.setCullHint(Spatial.CullHint.Always);
            showFlowlinesCheckbox.setCullHint(Spatial.CullHint.Always);
        } else {
            barrierShapesListBox.removeFromParent();
            clearBarrierButton.setCullHint(Spatial.CullHint.Never);
            resetFluidButton.setCullHint(Spatial.CullHint.Never);
            showFlowlinesCheckbox.setCullHint(Spatial.CullHint.Never);
        }
    }

    private String resolutionText() {
        String resolutionZValue;
        if (settings.getSolver() == "2D LBM") {
            resolutionZValue = "";
            settings.setResolution(new Vector3f(settings.getResolution().x, settings.getResolution().y, 1));
        } else {
            resolutionZValue = "x" + (int) settings.getResolution().z;
        }
        return (int) settings.getResolution().x + "x" + (int) settings.getResolution().y + resolutionZValue;
    }

    private void refreshValues() {
        resolutionValueLabel.setText(resolutionText());
        solverValueLabel.setText(settings.getSolver());
        flowSpeedValueLabel.setText(decimalFormat.format(settings.getFlowSpeed()));
        viscosityValueLabel.setText(decimalFormat.format(settings.getViscosity()));
        plotValueLabel.setText(settings.getPlot());
        modeValueLabel.setText(settings.getMode());
    }

    @Override
    public void onEnter() {
        refreshValues();
        showFlowlinesCheckbox.setChecked(settings.getShowFlowLines());
    }

    @Override
    public void update(float tpf) {
        if (dropdownOpen && barrierSelectionRef.update()) {
            Set<Integer> selection = barrierSelectionRef.get();
            if (selection != null && !selection.isEmpty()) {
                int index = selection.iterator().next();
                System.out.println(barrierShapeValues[index]);
                toggleDropdown();
            }
        }
    }

    @Override
    public Spatial getRoot() {
        return root;
    }
}
