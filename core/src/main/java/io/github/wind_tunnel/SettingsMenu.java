package io.github.wind_tunnel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;

import java.text.DecimalFormat;

public class SettingsMenu implements Menu {
    private Settings settings;
    private MenuUtil util;
    private DecimalFormat decimalFormat;

    private Rectangle backButton;
    private Rectangle settingsButton;

    private Rectangle[] resolutionButtons = new Rectangle[2];
    private float resolutionTextX = 0;
    private Rectangle[] solverButtons = new Rectangle[2];
    private float solverTextX = 0;
    private Rectangle[] flowSpeedButtons = new Rectangle[2];
    private Rectangle[] viscosityButtons = new Rectangle[2];
    private Rectangle[] plotButtons = new Rectangle[2];
    private float plotTextX = 0;
    private Rectangle[] modeButtons = new Rectangle[2];
    private float modeTextX = 0;

    private Rectangle barrierShapesButton;
    private boolean renderDropdown = false;
    private String[] barrierShapeValues = {"short line", "long line", "diagonal", "shallow diagonal", "small circle", "large circle", "line with spoiler", "circle with spoiler", "right angle", "wedge", "airfoil"};
    private Rectangle[] barrierShapesDropdownButtons = new Rectangle[barrierShapeValues.length];

    private Rectangle clearBarrierButton;
    private Rectangle resetFluidButton;
    private Rectangle showFlowlinesButton;

    private Texture backIcon;
    private Texture settingsIcon;
    private Texture flowLinesCheckBoxTrue;
    private Texture flowLinesCheckBoxFalse;

    private String resolutionZValue;

    public SettingsMenu() {
        this.util = new MenuUtil();
        this.settings = Settings.getInstance();

        this.decimalFormat = new DecimalFormat("0.000");

        this.backIcon = util.loadIcon("back");
        this.settingsIcon = util.loadIcon("settings");
        this.flowLinesCheckBoxTrue = util.loadIcon("checkBoxTrue");
        this.flowLinesCheckBoxFalse = util.loadIcon("checkBoxFalse");
    }

    @Override
    public void render(ShapeRenderer sr, SpriteBatch batch) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
            backButton = util.getHitbox(57.5f, 1022.5f, 75, 75);
            settingsButton = util.getHitbox(1862.5f, 57.5f, 75, 75);

            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 742.5f, 600, 75, 16); // l1
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 642.5f, 600, 75, 16); // l2
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 542.5f, 600, 75, 16); // l3
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 442.5f, 600, 75, 16); // l4
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 342.5f, 600, 75, 16); // l5

            util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 742.5f, 600, 75, 16); // r1
            barrierShapesButton = util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 642.5f, 600, 75, 16); // r2
            clearBarrierButton = util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 542.5f, 600, 75, 16); // r3
            resetFluidButton = util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 442.5f, 600, 75, 16); // r4
            showFlowlinesButton = util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 342.5f, 600, 75, 16); // r5

            resolutionButtons[0] = util.renderRoundedTriangle(sr, Color.WHITE, 865, 742.5f, 12, 90);
            resolutionButtons[1] = util.renderRoundedTriangle(sr, Color.WHITE, resolutionTextX-40, 742.5f, 12, 270);

            solverButtons[0] = util.renderRoundedTriangle(sr, Color.WHITE, 1585, 742.5f, 12, 90);
            solverButtons[1] = util.renderRoundedTriangle(sr, Color.WHITE, solverTextX-40, 742.5f, 12, 270);

            flowSpeedButtons[0] = util.renderRoundedTriangle(sr, Color.WHITE, 865, 642.5f, 12, 90);
            flowSpeedButtons[1] = util.renderRoundedTriangle(sr, Color.WHITE, 665, 642.5f, 12, 270);

            viscosityButtons[0] = util.renderRoundedTriangle(sr, Color.WHITE, 865, 542.5f, 12, 90);
            viscosityButtons[1] = util.renderRoundedTriangle(sr, Color.WHITE, 665, 542.5f, 12, 270);

            plotButtons[0] = util.renderRoundedTriangle(sr, Color.WHITE, 865, 442.5f, 12, 90);
            plotButtons[1] = util.renderRoundedTriangle(sr, Color.WHITE, plotTextX-40, 442.5f, 12, 270);

            modeButtons[0] = util.renderRoundedTriangle(sr, Color.WHITE, 865, 342.5f, 12, 90);
            modeButtons[1] = util.renderRoundedTriangle(sr, Color.WHITE, modeTextX-40, 342.5f, 12, 270);

            if (renderDropdown) {
                util.renderRoundedTriangle(sr, Color.WHITE, 1585, 642.5f, 12, 0);
                util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 605-(0.5f*50*barrierShapeValues.length), 600, 50*barrierShapeValues.length, 16);
            } else {
                util.renderRoundedTriangle(sr, Color.WHITE, 1585, 642.5f, 12, 180);
            }
        sr.end();

        batch.begin();
            util.renderText(batch, "settings", Color.WHITE, util.getScreenDimensions().x/2, 953, 96, "centre");

            util.renderText(batch, "resolution", Color.WHITE, 320, 742.5f, 36, "left"); // l1
            util.renderText(batch, "flow speed", Color.WHITE, 320, 642.5f, 36, "left"); // l2
            util.renderText(batch, "viscosity", Color.WHITE, 320, 542.5f, 36, "left"); // l3
            util.renderText(batch, "plot", Color.WHITE, 320, 442.5f, 36, "left"); // l4
            util.renderText(batch, "mode", Color.WHITE, 320, 342.5f, 36, "left"); // l5

            util.renderText(batch, "solver", Color.WHITE, 1040, 742.5f, 36, "left"); // r1
            util.renderText(batch, "barrier shapes", Color.WHITE, 1040, 642.5f, 36, "left"); // r2
            if (!renderDropdown) {
                util.renderText(batch, "clear barriers", Color.WHITE, 1040, 542.5f, 36, "left"); // r3
                util.renderText(batch, "reset fluid", Color.WHITE, 1040, 442.5f, 36, "left"); // r4
                util.renderText(batch, "show flowlines", Color.WHITE, 1040, 342.5f, 36, "left"); // r5
            }

            if (settings.getSolver() == "2D LBM") {
                resolutionZValue = "";
                settings.setResolution(new Vector3(settings.getResolution().x, settings.getResolution().y, 0));
            } else {
                resolutionZValue = "x" + (int) settings.getResolution().z;
            }

            resolutionTextX = util.renderText(batch, (int) settings.getResolution().x + "x" + (int) settings.getResolution().y + resolutionZValue, Color.WHITE, 825, 742.5f, 36, "right"); // l1 value
            util.renderText(batch, decimalFormat.format(settings.getFlowSpeed()), Color.WHITE, 765, 642.5f, 36, "centre"); // l2 value
            util.renderText(batch, decimalFormat.format(settings.getViscosity()), Color.WHITE, 765, 542.5f, 36, "centre"); // l3 value
            plotTextX = util.renderText(batch, settings.getPlot(), Color.WHITE, 825, 442.5f, 36, "right"); // l4 value
            modeTextX = util.renderText(batch, settings.getMode(), Color.WHITE, 825, 342.5f, 36, "right"); // l5 value

            solverTextX = util.renderText(batch, settings.getSolver(), Color.WHITE, 1545, 742.5f, 36, "right"); // r1 value

            if (renderDropdown) {
                for (Integer count=0; count<barrierShapeValues.length; count++) {
                    util.renderText(batch, barrierShapeValues[count], Color.WHITE, 1040, 580-(50*count), 36, "left");
                    barrierShapesDropdownButtons[count] = new Rectangle(1020, 580-(50*count)-25, 600, 50);
                }
            }

            util.renderIcon(batch, backIcon, 57.5f, 1022.5f);
            util.renderIcon(batch, settingsIcon, 1862.5f, 57.5f);

            if (!renderDropdown) {
                if (settings.getShowFlowLines()) {
                    util.renderIcon(batch, flowLinesCheckBoxTrue, 1585, 342.5f);
                } else {
                    util.renderIcon(batch, flowLinesCheckBoxFalse, 1585, 342.5f);
                }
            }
        batch.end();
    }

    @Override
    public String checkIfButtonsClicked() {
        if (util.isButtonClicked(resolutionButtons[0])) {settings.cycleResolutionOptions(1);}
        if (util.isButtonClicked(resolutionButtons[1])) {settings.cycleResolutionOptions(-1);}

        if (util.isButtonClicked(solverButtons[0])) {settings.setSolver(settings.cycleOptions(1, settings.getSolver(), settings.getSolverValues()));}
        if (util.isButtonClicked(solverButtons[1])) {settings.setSolver(settings.cycleOptions(-1, settings.getSolver(), settings.getSolverValues()));}

        if (util.isButtonClicked(flowSpeedButtons[0]) && settings.getFlowSpeed() < 0.120) {settings.setFlowSpeed(settings.getFlowSpeed()+0.005f); settings.setFlowSpeed(Math.round(settings.getFlowSpeed()*1000)/1000f);}
        if (util.isButtonClicked(flowSpeedButtons[1]) && settings.getFlowSpeed() > 0.005) {settings.setFlowSpeed(settings.getFlowSpeed()-0.005f); settings.setFlowSpeed(Math.round(settings.getFlowSpeed()*1000)/1000f);}

        if (util.isButtonClicked(viscosityButtons[0]) && settings.getViscosity() < 0.200) {settings.setViscosity(settings.getViscosity()+0.005f); settings.setViscosity(Math.round(settings.getViscosity()*1000)/1000f);}
        if (util.isButtonClicked(viscosityButtons[1]) && settings.getViscosity() > 0.005) {settings.setViscosity(settings.getViscosity()-0.005f); settings.setViscosity(Math.round(settings.getViscosity()*1000)/1000f);}

        if (util.isButtonClicked(plotButtons[0])) {settings.setPlot(settings.cycleOptions(1, settings.getPlot(), settings.getPlotValues()));}
        if (util.isButtonClicked(plotButtons[1])) {settings.setPlot(settings.cycleOptions(-1, settings.getPlot(), settings.getPlotValues()));}

        if (util.isButtonClicked(modeButtons[0])) {settings.setMode(settings.cycleOptions(1, settings.getMode(), settings.getModeValues()));}
        if (util.isButtonClicked(modeButtons[1])) {settings.setMode(settings.cycleOptions(-1, settings.getMode(), settings.getModeValues()));}

        if (!renderDropdown && util.isButtonClicked(clearBarrierButton)) {System.out.println("clear barriers");}

        if (!renderDropdown && util.isButtonClicked(resetFluidButton)) {System.out.println("reset fluid");}

        if (!renderDropdown && util.isButtonClicked(showFlowlinesButton)) {settings.setShowFlowLines(!settings.getShowFlowLines());}

        if (renderDropdown) {
            for (Integer count=0; count<barrierShapeValues.length; count++) {
                if (util.isButtonClicked(barrierShapesDropdownButtons[count])) {
                    System.out.println(barrierShapeValues[count]);
                    renderDropdown = false;
                    // addShapeToCells(barrierShapeValues[count]);
                }
            }
        }
        if (util.isButtonClicked(barrierShapesButton)) {renderDropdown = !renderDropdown;}

        if (util.isButtonClicked(backButton)) {return "back";}
        if (util.isButtonClicked(settingsButton)) {return "settings";}
        return "settings";
    }
}
