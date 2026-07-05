package io.github.wind_tunnel;

import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;

public class MainScreen implements Screen {
    private final Node root = new Node("main-root");

    public MainScreen(WindTunnelApp app) {
        Label title = new Label("wind tunnel");
        title.setColor(ColorRGBA.White);
        title.setFontSize(96f);
        title.setTextHAlignment(HAlignment.Center);
        root.attachChild(title);
        LayoutHelper.anchorCentered(title, 960, 880);

        Button levels = new Button("levels");
        levels.setColor(ColorRGBA.Black);
        levels.setBackground(new QuadBackgroundComponent(ColorRGBA.White));
        levels.setTextHAlignment(HAlignment.Center);
        root.attachChild(levels);
        LayoutHelper.anchorCentered(levels, 960, 580, 700, 150);
        levels.addClickCommands(source -> app.navigateTo("levels"));

        Button freeplay = new Button("freeplay");
        freeplay.setColor(ColorRGBA.Black);
        freeplay.setBackground(new QuadBackgroundComponent(ColorRGBA.White));
        freeplay.setTextHAlignment(HAlignment.Center);
        root.attachChild(freeplay);
        LayoutHelper.anchorCentered(freeplay, 960, 380, 700, 150);
        freeplay.addClickCommands(source -> app.navigateTo("freeplay"));

        Button quit = new Button("");
        quit.setIcon(new IconComponent("icons/quit.png"));
        quit.setBackground(new QuadBackgroundComponent(WindTunnelColors.QUIT_BUTTON));
        root.attachChild(quit);
        LayoutHelper.anchorCentered(quit, 1862.5f, 1022.5f, 75, 75);
        quit.addClickCommands(source -> app.navigateTo("quit"));

        Button settingsButton = new Button("");
        settingsButton.setIcon(new IconComponent("icons/settings.png"));
        settingsButton.setBackground(null);
        root.attachChild(settingsButton);
        LayoutHelper.anchorCentered(settingsButton, 1862.5f, 57.5f, 75, 75);
        settingsButton.addClickCommands(source -> app.navigateTo("settings"));

        Button about = new Button("");
        about.setIcon(new IconComponent("icons/about.png"));
        about.setBackground(null);
        root.attachChild(about);
        LayoutHelper.anchorCentered(about, 57.5f, 57.5f, 75, 75);
        about.addClickCommands(source -> app.navigateTo("about"));
    }

    @Override
    public Spatial getRoot() {
        return root;
    }
}
