package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;

// Everything in the game is laid out on a 1920x1080 grid of "game coordinates". This scales that grid to fit the window,
// whatever size it is or is resized to, keeping its 16:9 shape by adding black bars at the sides or at the top and bottom.
public class GameView {
    public static final float WIDTH = 1920;
    public static final float HEIGHT = 1080;

    private FitViewport viewport;
    private Vector2 mouse = new Vector2();

    private static GameView instance;

    public static GameView getInstance() {
        if (instance == null) {
            instance = new GameView();
        }
        return instance;
    }

    private GameView() {
        this.viewport = new FitViewport(WIDTH, HEIGHT);
    }

    // called by Main whenever the window changes size (and once when the game starts)
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    // makes the shape renderer and sprite batch draw in game coordinates, scaled to the window
    public void apply(ShapeRenderer sr, SpriteBatch batch) {
        viewport.apply();
        sr.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
    }

    // the mouse position in game coordinates, with y = 0 at the bottom (the window measures it in pixels from the top)
    public Vector2 getMouse() {
        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        return viewport.unproject(mouse);
    }
}
