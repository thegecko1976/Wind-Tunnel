package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class MainMenu implements Menu {
    private MenuUtil util;

    private Rectangle quitButton;
    private Rectangle settingsButton;
    private Rectangle levelsButton;
    private Rectangle freeplayButton;
    private Rectangle aboutButton;

    private Texture quitIcon;
    private Texture settingsIcon;
    private Texture aboutIcon;

    public MainMenu() {
        this.util = new MenuUtil();

        this.quitIcon = util.loadIcon("quit");
        this.settingsIcon = util.loadIcon("settings");
        this.aboutIcon = util.loadIcon("about");
    }

    @Override
    public void render(ShapeRenderer sr, SpriteBatch batch) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
            quitButton = util.renderButton(sr, util.getQuitButtonColor(), null, 1862.5f, 1022.5f, 75, 75, 16);
            settingsButton = util.getHitbox(1862.5f, 57.5f, 75, 75);
            levelsButton = util.renderButton(sr, Color.WHITE, Color.BLACK, util.getScreenDimensions().x/2, 580, 700, 150, 16);
            freeplayButton = util.renderButton(sr, Color.WHITE, Color.BLACK, util.getScreenDimensions().x/2, 380, 700, 150, 16);
            aboutButton = util.getHitbox(57.5f, 57.5f, 75, 75);
        sr.end();

        batch.begin();
            util.renderText(batch, "wind tunnel", Color.WHITE, util.getScreenDimensions().x/2, 880, 128, "centre");
            util.renderText(batch, "levels", Color.WHITE, util.getScreenDimensions().x/2, 580, 64, "centre");
            util.renderText(batch, "freeplay", Color.WHITE, util.getScreenDimensions().x/2, 380, 64, "centre");
            util.renderIcon(batch, quitIcon, 1862.5f, 1022.5f);
            util.renderIcon(batch, settingsIcon, 1862.5f, 57.5f);
            util.renderIcon(batch, aboutIcon, 57.5f, 57.5f);
        batch.end();
    }

    @Override
    public String checkIfButtonsClicked() {
        if (util.isButtonClicked(quitButton)) {Gdx.app.exit();}
        if (util.isButtonClicked(settingsButton)) {return "settings";}
        if (util.isButtonClicked(levelsButton)) {return "levels";}
        if (util.isButtonClicked(freeplayButton)) {return "freeplay";}
        if (util.isButtonClicked(aboutButton)) {return "about";}
        return "main";
    }
}
