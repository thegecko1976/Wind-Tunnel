package io.github.some_example_name;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class FreeplayMenu implements Menu {
    private MenuUtil util;
    private LatticeBoltzmannCFDSolver cfdSolver;
    private Settings settings;

    private Rectangle backButton;
    private Rectangle settingsButton;

    private Rectangle runButton;
    private Rectangle pauseButton;
    private Rectangle stepButton;

    private Texture backIcon;
    private Texture settingsIcon;
    private Texture pauseIcon;
    private Texture stepIcon;

    public FreeplayMenu() {
        this.util = new MenuUtil();
        this.cfdSolver = new LatticeBoltzmannCFDSolver();
        this.settings = Settings.getInstance();

        this.backIcon = util.loadIcon("back");
        this.settingsIcon = util.loadIcon("settings");
        this.pauseIcon = util.loadIcon("pause");
        this.stepIcon = util.loadIcon("stepArrow");
    }

    @Override
    public void render(ShapeRenderer sr, SpriteBatch batch) {

        if (settings.getSimulationRunning()) {
            //cfdSolver.collision();
            //cfdSolver.movement();
            //cfdSolver.boundaries();
        }

        sr.begin(ShapeRenderer.ShapeType.Filled);
            cfdSolver.render(sr);

            backButton = util.getHitbox(57.5f, 1022.5f, 75, 75);
            settingsButton = util.getHitbox(1862.5f, 57.5f, 75, 75);

            if (!settings.getSimulationRunning()) {
                runButton = util.renderRoundedRectangle(sr, util.getRunButtonColor(), 835, 62.5f, 200, 75, 16);
                util.renderRoundedTriangle(sr, Color.WHITE, 790, 62.5f, 12, 90);
            } else {
                pauseButton = util.renderRoundedRectangle(sr, util.getPauseButtonColor(), 835, 62.5f, 200, 75, 16);
            }
            stepButton = util.renderRoundedRectangle(sr, util.getStepButtonColor(), 1085, 62.5f, 200, 75, 16);
        sr.end();

        batch.begin();
            if (!settings.getSimulationRunning()) {
                util.renderText(batch, "run", Color.WHITE, 857.5f, 62.5f, 36, "centre");
            } else {
                util.renderText(batch, "pause", Color.WHITE, 852.5f, 62.5f, 36, "centre");
                util.renderIcon(batch, pauseIcon, 770, 62.5f);
            }
            util.renderText(batch, "step", Color.WHITE, 1110, 62.5f, 36, "centre");
            util.renderIcon(batch, stepIcon, 1035, 62.5f);

            util.renderIcon(batch, backIcon, 57.5f, 1022.5f);
            util.renderIcon(batch, settingsIcon, 1862.5f, 57.5f);
        batch.end();
    }

    @Override
    public String checkIfButtonsClicked() {
        if (!settings.getSimulationRunning() && util.isButtonClicked(runButton)) {settings.setSimulationRunning(true);}
        else if (settings.getSimulationRunning() && util.isButtonClicked(pauseButton)) {settings.setSimulationRunning(false);}
        if (!settings.getSimulationRunning() && util.isButtonClicked(stepButton)) {System.out.println("step simulation");}

        if (util.isButtonClicked(backButton)) {return "back";}
        if (util.isButtonClicked(settingsButton)) {return "settings";}
        return "freeplay";
    }

    public void reinitialise() {cfdSolver.initialiseFluid();}
}
