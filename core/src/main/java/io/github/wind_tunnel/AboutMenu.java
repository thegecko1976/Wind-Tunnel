package io.github.wind_tunnel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class AboutMenu implements Menu {
    private MenuUtil util;

    private Rectangle backButton;
    private Rectangle aboutButton;

    private Texture backIcon;
    private Texture aboutIcon;

    public AboutMenu() {
        this.util = new MenuUtil();

        this.backIcon = util.loadIcon("back");
        this.aboutIcon = util.loadIcon("about");
    }

    @Override
    public void render(ShapeRenderer sr, SpriteBatch batch) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
            backButton = util.getHitbox(57.5f, 1022.5f, 75, 75);
            aboutButton = util.getHitbox(57.5f, 57.5f, 75, 75);
        sr.end();

        batch.begin();
            util.renderText(batch, "about", Color.WHITE, util.getScreenDimensions().x/2, 953, 96, "centre");
            util.renderText(batch, "This is a 2D and 3D incompressible Wind Tunnel made by Nathan Becker", Color.WHITE, util.getScreenDimensions().x/2, 850, 32, "centre");
            util.renderText(batch, "for my A-Level Computer Science NEA.", Color.WHITE, util.getScreenDimensions().x/2, 800, 32, "centre");
            util.renderText(batch, "The Wind Tunnel uses computational fluid dynamics such as the", Color.WHITE, util.getScreenDimensions().x/2, 700, 32, "centre");
            util.renderText(batch, "Lattice-Boltzmann equations to simulate a fluid flowing around an object", Color.WHITE, util.getScreenDimensions().x/2, 650, 32, "centre");
            util.renderText(batch, "based on variable flow speed and viscosity.", Color.WHITE, util.getScreenDimensions().x/2, 600, 32, "centre");
            util.renderText(batch, "xxx lines of code", Color.WHITE, util.getScreenDimensions().x/2, 500, 32, "centre");
            util.renderText(batch, "~ xxx hrs of coding", Color.WHITE, util.getScreenDimensions().x/2, 450, 32, "centre");
            util.renderText(batch, "xxx words of documentation", Color.WHITE, util.getScreenDimensions().x/2, 400, 32, "centre");
            util.renderText(batch, "xx / xx for documentation", Color.WHITE, util.getScreenDimensions().x/2, 300, 32, "centre");
            util.renderText(batch, "xx / xx for coding", Color.WHITE, util.getScreenDimensions().x/2, 250, 32, "centre");
            util.renderIcon(batch, backIcon, 57.5f, 1022.5f);
            util.renderIcon(batch, aboutIcon, 57.5f, 57.5f);
        batch.end();
    }

    @Override
    public String checkIfButtonsClicked() {
        if (util.isButtonClicked(backButton)) {return "back";}
        if (util.isButtonClicked(aboutButton)) {return "about";}
        return "about";
    }
}
