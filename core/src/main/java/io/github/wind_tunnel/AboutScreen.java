package io.github.wind_tunnel;

import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.component.IconComponent;

public class AboutScreen implements Screen {
    private final Node root = new Node("about-root");

    public AboutScreen(WindTunnelApp app) {
        addLabel("about", 960, 953, 72);
        addLabel("This is a 2D and 3D incompressible Wind Tunnel made by Nathan Becker", 960, 850, 24);
        addLabel("for my A-Level Computer Science NEA.", 960, 800, 24);
        addLabel("The Wind Tunnel uses computational fluid dynamics such as the", 960, 700, 24);
        addLabel("Lattice-Boltzmann equations to simulate a fluid flowing around an object", 960, 650, 24);
        addLabel("based on variable flow speed and viscosity.", 960, 600, 24);
        addLabel("xxx lines of code", 960, 500, 24);
        addLabel("~ xxx hrs of coding", 960, 450, 24);
        addLabel("xxx words of documentation", 960, 400, 24);
        addLabel("xx / xx for documentation", 960, 300, 24);
        addLabel("xx / xx for coding", 960, 250, 24);

        Button back = new Button("");
        back.setIcon(new IconComponent("icons/back.png"));
        back.setBackground(null);
        root.attachChild(back);
        LayoutHelper.anchorCentered(back, 57.5f, 1022.5f, 75, 75);
        back.addClickCommands(source -> app.navigateTo("back"));

        Button about = new Button("");
        about.setIcon(new IconComponent("icons/about.png"));
        about.setBackground(null);
        root.attachChild(about);
        LayoutHelper.anchorCentered(about, 57.5f, 57.5f, 75, 75);
        about.addClickCommands(source -> app.navigateTo("about"));
    }

    private void addLabel(String text, float centerX, float centerY, float fontSize) {
        Label label = new Label(text);
        label.setColor(ColorRGBA.White);
        label.setFontSize(fontSize);
        label.setTextHAlignment(HAlignment.Center);
        root.attachChild(label);
        LayoutHelper.anchorCentered(label, centerX, centerY);
    }

    @Override
    public Spatial getRoot() {
        return root;
    }
}
