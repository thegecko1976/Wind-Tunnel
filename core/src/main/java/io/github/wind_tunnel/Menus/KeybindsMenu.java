package io.github.wind_tunnel.Menus;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import io.github.wind_tunnel.Keybinds;
import io.github.wind_tunnel.Menu;
import io.github.wind_tunnel.MenuNavigator;
import io.github.wind_tunnel.MenuUtil;

public class KeybindsMenu implements Menu {
    private MenuUtil util;
    private Keybinds keybinds;

    private Rectangle backButton;
    private Rectangle keybindsButton;

    private Texture backIcon;
    private Texture keybindsIcon;

    public KeybindsMenu() {
        this.util = new MenuUtil();
        this.keybinds = Keybinds.getInstance();

        this.backIcon = util.loadIcon("back");
        this.keybindsIcon = util.loadIcon("keybinds");
    }

    @Override
    public void render(ShapeRenderer sr, SpriteBatch batch) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
            backButton = util.getHitbox(57.5f, 1022.5f, 75, 75);
            keybindsButton = util.getHitbox(57.5f, 57.5f, 75, 75);

            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 742.5f, 600, 75, 16); // l1
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 642.5f, 600, 75, 16); // l2
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 542.5f, 600, 75, 16); // l3
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 442.5f, 600, 75, 16); // l4
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 342.5f, 600, 75, 16); // l5
            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 242.5f, 600, 75, 16); // l6

            util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 742.5f, 600, 75, 16); // r1
            util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 642.5f, 600, 75, 16); // r2
            util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 542.5f, 600, 75, 16); // r3
            util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 442.5f, 600, 75, 16); // r4
            util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 342.5f, 600, 75, 16); // r5
        sr.end();

        batch.begin();
            util.renderText(batch, "keybinds", Color.WHITE, util.getScreenDimensions().x/2, 953, 96, "centre");

            util.renderText(batch, "rotate forwards", Color.WHITE, 320, 742.5f, 36, "left"); // l1
            util.renderText(batch, "rotate backwards", Color.WHITE, 320, 642.5f, 36, "left"); // l2
            util.renderText(batch, "rotate left", Color.WHITE, 320, 542.5f, 36, "left"); // l3
            util.renderText(batch, "rotate right", Color.WHITE, 320, 442.5f, 36, "left"); // l4
            util.renderText(batch, "zoom in", Color.WHITE, 320, 342.5f, 36, "left"); // l5
            util.renderText(batch, "zoom out", Color.WHITE, 320, 242.5f, 36, "left"); // l6

            util.renderText(batch, "pause/run", Color.WHITE, 1040, 742.5f, 36, "left"); // r1
            util.renderText(batch, "step simulation", Color.WHITE, 1040, 642.5f, 36, "left"); // r2
            util.renderText(batch, "draw barriers", Color.WHITE, 1040, 542.5f, 36, "left"); // r3
            util.renderText(batch, "erase barriers", Color.WHITE, 1040, 442.5f, 36, "left"); // r4
            util.renderText(batch, "clear all barriers", Color.WHITE, 1040, 342.5f, 36, "left"); // r5

            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(0)).toLowerCase(), Color.WHITE, 865, 742.5f, 36, "right"); // l1 value
            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(1)).toLowerCase(), Color.WHITE, 865, 642.5f, 36, "right"); // l2 value
            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(2)).toLowerCase(), Color.WHITE, 865, 542.5f, 36, "right"); // l3 value
            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(3)).toLowerCase(), Color.WHITE, 865, 442.5f, 36, "right"); // l4 value
            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(4)).toLowerCase(), Color.WHITE, 865, 342.5f, 36, "right"); // l5 value
            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(5)).toLowerCase(), Color.WHITE, 865, 242.5f, 36, "right"); // l6 value

            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(6)).toLowerCase(), Color.WHITE, 1585, 742.5f, 36, "right"); // r1 value
            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(7)).toLowerCase(), Color.WHITE, 1585, 642.5f, 36, "right"); // r2 value
            util.renderText(batch, "left click", Color.WHITE, 1585, 542.5f, 36, "right"); // r3 value
            util.renderText(batch, "right click", Color.WHITE, 1585, 442.5f, 36, "right"); // r4 value
            util.renderText(batch, Input.Keys.toString(keybinds.getBindAt(8)).toLowerCase(), Color.WHITE, 1585, 342.5f, 36, "right"); // r5 value

            util.renderIcon(batch, backIcon, 57.5f, 1022.5f);
            util.renderIcon(batch, keybindsIcon, 57.5f, 57.f);
        batch.end();
    }

    @Override
    public void checkIfButtonsClicked(MenuNavigator navigator) {
        if (util.isButtonClicked(backButton)) {navigator.back();}
    }
}
