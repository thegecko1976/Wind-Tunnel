package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;

import java.util.ArrayDeque;
import java.util.Deque;

public class MenuNavigator {
    private Settings settings;

    private Deque<Menu> menuHistory = new ArrayDeque<>();
    private Menu currentMenu;

    public MenuNavigator(Menu startMenu) {
        this.settings = Settings.getInstance();
        this.currentMenu = startMenu;
        currentMenu.onEnter();
    }

    public void open(Menu nextMenu) {
        settings.setPlaceBarriers(false);
        currentMenu.onPause();
        menuHistory.push(currentMenu);
        currentMenu = nextMenu;
        currentMenu.onEnter();
    }

    public void back() {
        if (menuHistory.isEmpty()) {return;}
        settings.setPlaceBarriers(false);
        currentMenu.onExit();
        currentMenu = menuHistory.pop();
        currentMenu.onResume();
    }

    public void quit() {Gdx.app.exit();}

    public Menu getCurrentMenu() {return currentMenu;}
}
