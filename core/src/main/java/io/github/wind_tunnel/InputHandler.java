package io.github.wind_tunnel;

import com.badlogic.gdx.InputAdapter;

public class InputHandler extends InputAdapter {

    private Settings settings;
    private float scrollAmount = 0; // scrolling not yet turned into a whole plane

    public InputHandler() {
        this.settings = Settings.getInstance();
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (settings.getSolver().equals("3D LBM")) {
            // a mouse wheel scrolls in whole steps, but a trackpad sends many small fractions (like 0.3),
            // so they are added up and the plane only moves once they make a whole step
            scrollAmount -= amountY;
            int planes = (int) scrollAmount;
            if (planes != 0) {
                settings.moveSelectedZPlane(planes);
                scrollAmount -= planes;
            }
            return true;
        }
        return false;
    }
}
