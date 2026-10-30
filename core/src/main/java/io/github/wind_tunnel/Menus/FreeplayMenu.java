package io.github.wind_tunnel.Menus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import io.github.wind_tunnel.LatticeBoltzmannCFDSolver;
import io.github.wind_tunnel.Menu;
import io.github.wind_tunnel.MenuNavigator;
import io.github.wind_tunnel.MenuUtil;
import io.github.wind_tunnel.Settings;

public class FreeplayMenu implements Menu {
    private MenuUtil util;
    private LatticeBoltzmannCFDSolver cfdSolver;
    private Settings settings;

    private Menu settingsMenu;

    private Rectangle backButton;
    private Rectangle settingsButton;

    private Rectangle runButton;
    private Rectangle pauseButton;
    private Rectangle stepButton;

    private Texture backIcon;
    private Texture settingsIcon;
    private Texture pauseIcon;
    private Texture stepIcon;

    public FreeplayMenu(Menu settingsMenu) {
        this.util = new MenuUtil();
        this.cfdSolver = LatticeBoltzmannCFDSolver.getInstance();
        this.settings = Settings.getInstance();

        this.settingsMenu = settingsMenu;

        this.backIcon = util.loadIcon("back");
        this.settingsIcon = util.loadIcon("settings");
        this.pauseIcon = util.loadIcon("pause");
        this.stepIcon = util.loadIcon("stepArrow");
    }

    @Override
    public void render(ShapeRenderer sr, SpriteBatch batch) {

        if (settings.getSimulationRunning()) {
            cfdSolver.doStep();
        }

        sr.begin(ShapeRenderer.ShapeType.Filled);
            cfdSolver.render(sr);

            backButton = util.getHitbox(57.5f, 1022.5f, 75, 75);
            settingsButton = util.getHitbox(1862.5f, 57.5f, 75, 75);

            if (settings.getRenderUI()) {
                if (!settings.getSimulationRunning()) {
                    runButton = util.renderRoundedRectangle(sr, util.getRunButtonColor(), 835, 62.5f, 200, 75, 16);
                    util.renderRoundedTriangle(sr, Color.WHITE, 790, 62.5f, 12, 90);
                } else {
                    pauseButton = util.renderRoundedRectangle(sr, util.getPauseButtonColor(), 835, 62.5f, 200, 75, 16);
                }
                stepButton = util.renderRoundedRectangle(sr, util.getStepButtonColor(), 1085, 62.5f, 200, 75, 16);
            }
        sr.end();

        batch.begin();
            if (settings.getRenderUI()) {
                if (!settings.getSimulationRunning()) {
                    util.renderText(batch, "run", Color.WHITE, 857.5f, 62.5f, 36, "centre");
                } else {
                    util.renderText(batch, "pause", Color.WHITE, 852.5f, 62.5f, 36, "centre");
                    util.renderIcon(batch, pauseIcon, 770, 62.5f);
                }
                util.renderText(batch, "step", Color.WHITE, 1110, 62.5f, 36, "centre");
                util.renderIcon(batch, stepIcon, 1035, 62.5f);
                util.renderText(batch, "(" + (int) settings.getIntersectionPoint().x + ", " + (int) settings.getIntersectionPoint().y + ", " + (int) settings.getIntersectionPoint().z + ")", Color.WHITE, 57.5f, 57.5f, 36, "left");
            }

            util.renderIcon(batch, backIcon, 57.5f, 1022.5f);
            util.renderIcon(batch, settingsIcon, 1862.5f, 57.5f);
        batch.end();
    }

    @Override
    public void checkIfButtonsClicked(MenuNavigator navigator) {
        if (settings.getRenderUI()) {
            if (!settings.getSimulationRunning() && util.isButtonClicked(runButton)) {
                settings.setSimulationRunning(true);
                settings.setPlaceBarriers(false);
            }
            else if (settings.getSimulationRunning() && util.isButtonClicked(pauseButton)) {
                settings.setSimulationRunning(false);
                settings.setPlaceBarriers(false);
            }
            if (util.isButtonClicked(stepButton)) {
                settings.setPlaceBarriers(false);
                if (!settings.getSimulationRunning()) {
                    cfdSolver.doStep();
                }
            }
        }

        if (util.isButtonClicked(backButton)) {navigator.back(); return;}
        if (util.isButtonClicked(settingsButton)) {navigator.open(settingsMenu);}
    }

    @Override
    public void onEnter() {
        settings.setLoadedLevel(false);
        cfdSolver.initialiseFluid();
        settings.setSimulationRunning(false);
    }

    @Override
    public void onPause() {
        // remembered so the barriers can be rescaled if the settings menu changes them
        settings.setOldResolution(settings.getResolution());
        settings.setOldSolver(settings.getSolver());
    }

    @Override
    public void onResume() {cfdSolver.initialiseFluid();}

    public LatticeBoltzmannCFDSolver getCfdSolver() {return cfdSolver;}
}
