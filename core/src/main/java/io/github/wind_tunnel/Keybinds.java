package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public class Keybinds {

    private Settings settings;
    private LatticeBoltzmannCFDSolver cfdSolver;

    private int[] binds = {
        Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D, // rotation of the simulation area in 3D, forwards, backwards, left, right
        Input.Keys.UP, Input.Keys.DOWN, // zoom in 3D, zoom in, zoom out
        Input.Keys.SPACE, Input.Keys.RIGHT, // pause/run the simulation, step the simulation (when paused)
        Input.Keys.R, // clear all barriers
        Input.Buttons.LEFT, Input.Buttons.RIGHT, // draw/erase barriers
    };

    private static Keybinds instance;

    public static Keybinds getInstance() {
        if (instance == null) {
            instance = new Keybinds();
        }
        return instance;
    }

    private Keybinds() {
        settings = Settings.getInstance();
        cfdSolver = LatticeBoltzmannCFDSolver.getInstance();
    }

    public void checkForKeyPresses() {
        if (settings.getSolver().equals("3D LBM")) {
            // how long the last frame took, so the view turns and zooms at the same speed at any frame rate
            // (capped at a tenth of a second, so one slow frame can't make the view jump)
            float seconds = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
            float turn = (float) (Math.PI/180)*60*seconds; // 60 degrees a second, the same as the old 1 degree a frame at 60 frames per second
            float zoom = 60*seconds; // 60 units a second, the same as the old 1 unit a frame at 60 frames per second

            if (Gdx.input.isKeyPressed(binds[0])) {
                settings.setRotationAnglesX(settings.getRotationAnglesX()-turn);
            }
            if (Gdx.input.isKeyPressed(binds[1])) {
                settings.setRotationAnglesX(settings.getRotationAnglesX()+turn);
            }
            if (Gdx.input.isKeyPressed(binds[2])) {
                settings.setRotationAnglesY(settings.getRotationAnglesY()+turn);
            }
            if (Gdx.input.isKeyPressed(binds[3])) {
                settings.setRotationAnglesY(settings.getRotationAnglesY()-turn);
            }
            if (Gdx.input.isKeyPressed(binds[4])) {
                settings.setCameraDistance(settings.getCameraDistance()-zoom);
            }
            if (Gdx.input.isKeyPressed(binds[5])) {
                settings.setCameraDistance(settings.getCameraDistance()+zoom);
            }
        }
        if (Gdx.input.isKeyJustPressed(binds[6])) {
            settings.setSimulationRunning(!settings.getSimulationRunning());
        }
        if (Gdx.input.isKeyJustPressed(binds[7])) {
            if (!settings.getSimulationRunning()) {
                cfdSolver.doStep();
            }
        }
        if (Gdx.input.isKeyJustPressed(binds[8])) {
            cfdSolver.removeAllBarriers();
        }
    }

    public int[] getBinds() {return this.binds;}
    public int getBindAt(int index) {return this.binds[index];}
    public void setBindsAt(int index, int key) {this.binds[index] = key;}
}
