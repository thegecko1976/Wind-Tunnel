package io.github.wind_tunnel;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public interface Menu {
    public void render(ShapeRenderer sr, SpriteBatch batch);
    public String checkIfButtonsClicked();
}
