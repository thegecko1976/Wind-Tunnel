package io.github.wind_tunnel;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public interface Menu {
    public void render(ShapeRenderer sr, SpriteBatch batch);
    public void checkIfButtonsClicked(MenuNavigator navigator);

    // lifecycle hooks, called by MenuNavigator
    public default void onEnter() {} // opened from another menu
    public default void onPause() {} // another menu opened on top of this one
    public default void onResume() {} // returned to from the menu on top of this one
    public default void onExit() {} // left by going back
}
