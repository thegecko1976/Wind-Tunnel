package io.github.some_example_name;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.*;

public class Main extends ApplicationAdapter {
    private ShapeRenderer sr;
    private SpriteBatch batch;

    private Vector2 screenDimensions;

    private String menu = "main";
    private String nextMenu;
    private Stack menuHistory = new Stack(10);
    private Settings settings;

    private MainMenu mainMenu;
    private AboutMenu aboutMenu;
    private SettingsMenu settingsMenu;
    private LevelsMenu levelsMenu;
    private FreeplayMenu freeplayMenu;

    @Override
    public void create() {
        screenDimensions = new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        sr = new ShapeRenderer();
        batch = new SpriteBatch();

        settings = Settings.getInstance();
        mainMenu = new MainMenu();
        aboutMenu = new AboutMenu();
        settingsMenu = new SettingsMenu();
        levelsMenu = new LevelsMenu();
        freeplayMenu = new FreeplayMenu();
    }

    @Override
    public void render() {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        Gdx.graphics.setTitle("Wind Tunnel FPS: " + Gdx.graphics.getFramesPerSecond());

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            settings.setRotationAnglesX((float) (settings.getRotationAnglesX()-(Math.PI/180)));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            settings.setRotationAnglesX((float) (settings.getRotationAnglesX()+(Math.PI/180)));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            settings.setRotationAnglesY((float) (settings.getRotationAnglesY()+(Math.PI/180)));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            settings.setRotationAnglesY((float) (settings.getRotationAnglesY()-(Math.PI/180)));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            settings.setRotationAnglesZ((float) (settings.getRotationAnglesZ()-(Math.PI/180)));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            settings.setRotationAnglesZ((float) (settings.getRotationAnglesZ()+(Math.PI/180)));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            settings.setCameraDistance(settings.getCameraDistance()-1);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            settings.setCameraDistance(settings.getCameraDistance()+1);
        }

        if (menu.equals("main")) {
            nextMenu = mainMenu.checkIfButtonsClicked();
        } else if (menu.equals("about")) {
            nextMenu = aboutMenu.checkIfButtonsClicked();
        } else if (menu.equals("settings")) {
            nextMenu = settingsMenu.checkIfButtonsClicked();
        } else if (menu.equals("levels")) {
            nextMenu = levelsMenu.checkIfButtonsClicked();
        } else if (menu.equals("freeplay")) {
            nextMenu = freeplayMenu.checkIfButtonsClicked();
        }

        if (!nextMenu.equals(menu)) {
            if (menu.equals("main") && (nextMenu.equals("levels") || nextMenu.equals("freeplay"))) {
                freeplayMenu.reinitialise();
                levelsMenu.reinitialise();
                settings.setSimulationRunning(false);
            }
            if ("back".equals(nextMenu)) {
                if (!menuHistory.isEmpty()) {
                    String previousMenu = menuHistory.pop();
                    if (menu.equals("settings") && (previousMenu.equals("freeplay") || previousMenu.equals("levels"))) {
                        freeplayMenu.reinitialise();
                        levelsMenu.reinitialise();
                    }
                    menu = previousMenu;
                }
            } else {
                menuHistory.push(menu);
                menu = nextMenu;
            }
        }

        if (menu.equals("main")) {
            mainMenu.render(sr, batch);
        } else if (menu.equals("about")) {
            aboutMenu.render(sr, batch);
        } else if (menu.equals("settings")) {
            settingsMenu.render(sr, batch);
        } else if (menu.equals("levels")) {
            levelsMenu.render(sr, batch);
        } else if (menu.equals("freeplay")) {
            freeplayMenu.render(sr, batch);
        }
    }

    @Override
    public void dispose() {
        sr.dispose();
        batch.dispose();
    }
}
