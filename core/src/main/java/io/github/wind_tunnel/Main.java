package io.github.wind_tunnel;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.wind_tunnel.Menus.*;

public class Main extends ApplicationAdapter {
    private ShapeRenderer sr;
    private SpriteBatch batch;

    private Vector2 screenDimensions;

    private Keybinds keybinds;
    private MenuNavigator navigator;
    private int shownFps = -1;

    @Override
    public void create() {
        Gdx.input.setInputProcessor(new InputHandler());
        screenDimensions = new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        sr = new ShapeRenderer();
        batch = new SpriteBatch();

        keybinds = Keybinds.getInstance();

        // each menu is given the menus it can open, so these are built from the deepest menu up
        Menu settingsMenu = new SettingsMenu(new KeybindsMenu(), new ImportFileMenu());
        Menu mainMenu = new MainMenu(new LevelsMenu(settingsMenu), new FreeplayMenu(settingsMenu), settingsMenu, new AboutMenu());
        navigator = new MenuNavigator(mainMenu);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        // the FPS only changes once a second and setting the title goes through the operating system, so only update it when it changes
        if (Gdx.graphics.getFramesPerSecond() != shownFps) {
            shownFps = Gdx.graphics.getFramesPerSecond();
            Gdx.graphics.setTitle("Wind Tunnel FPS: " + shownFps + "/" + Gdx.graphics.getDisplayMode().refreshRate);
        }

        keybinds.checkForKeyPresses();

        navigator.getCurrentMenu().checkIfButtonsClicked(navigator);
        navigator.getCurrentMenu().render(sr, batch);
    }

    @Override
    public void dispose() {
        sr.dispose();
        batch.dispose();
        LatticeBoltzmannCFDSolver.getInstance().dispose(); // frees the 2D picture's texture
    }
}
