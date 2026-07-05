package io.github.wind_tunnel;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;

public class WindTunnelApp extends SimpleApplication implements ActionListener {
    private final Settings settings = Settings.getInstance();
    private final Stack menuHistory = new Stack(10);

    private Screen mainScreen;
    private Screen aboutScreen;
    private Screen settingsScreen;
    private Screen levelsScreen;
    private Screen freeplayScreen;

    private Screen currentScreen;
    private String currentScreenName;

    private boolean rotateXNeg, rotateXPos, rotateYPos, rotateYNeg, zoomIn, zoomOut;

    @Override
    public void simpleInitApp() {
        GuiGlobals.initialize(this);
        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        flyCam.setEnabled(false);
        viewPort.setBackgroundColor(ColorRGBA.Black);

        mainScreen = new MainScreen(this);
        aboutScreen = new AboutScreen(this);
        settingsScreen = new SettingsScreen(this);
        levelsScreen = new LevelsScreen(this);
        freeplayScreen = new FreeplayScreen(this);

        registerInput();
        navigateTo("main");
    }

    public void navigateTo(String screenName) {
        if ("quit".equals(screenName)) {
            stop();
            return;
        }
        if ("back".equals(screenName)) {
            String previous = menuHistory.pop();
            if (previous == null) return;
            if ("settings".equals(currentScreenName) && ("freeplay".equals(previous) || "levels".equals(previous))) {
                levelsScreen.onEnter();
                freeplayScreen.onEnter();
            }
            switchTo(previous);
            return;
        }

        if ("main".equals(currentScreenName) && ("levels".equals(screenName) || "freeplay".equals(screenName))) {
            levelsScreen.onEnter();
            freeplayScreen.onEnter();
            settings.setSimulationRunning(false);
        }
        if (currentScreenName != null && !currentScreenName.equals(screenName)) {
            menuHistory.push(currentScreenName);
        }
        switchTo(screenName);
    }

    private void switchTo(String screenName) {
        Screen next = resolveScreen(screenName);
        if (next == currentScreen) return;

        if (currentScreen != null) {
            guiNode.detachChild(currentScreen.getRoot());
            if (currentScreen instanceof Scene3DScreen scene3D) {
                scene3D.detachScene(rootNode);
            }
            currentScreen.onExit();
        }

        guiNode.attachChild(next.getRoot());
        if (next instanceof Scene3DScreen scene3D) {
            scene3D.attachScene(rootNode);
        }
        next.onEnter();

        currentScreen = next;
        currentScreenName = screenName;
    }

    private Screen resolveScreen(String screenName) {
        return switch (screenName) {
            case "main" -> mainScreen;
            case "about" -> aboutScreen;
            case "settings" -> settingsScreen;
            case "levels" -> levelsScreen;
            case "freeplay" -> freeplayScreen;
            default -> mainScreen;
        };
    }

    @Override
    public void simpleUpdate(float tpf) {
        applyRotationAndZoomInput();

        if (currentScreen instanceof Scene3DScreen scene3D) {
            scene3D.getSceneNode().setLocalRotation(new Quaternion().fromAngles(
                settings.getRotationAnglesX(), settings.getRotationAnglesY(), settings.getRotationAnglesZ()));

            cam.setLocation(new com.jme3.math.Vector3f(0, 0, settings.getCameraDistance()));
            cam.lookAt(com.jme3.math.Vector3f.ZERO, com.jme3.math.Vector3f.UNIT_Y);
            cam.setFrustumPerspective(settings.getFov(), (float) cam.getWidth() / cam.getHeight(), 0.1f, 1000f);
        }

        if (currentScreen != null) {
            currentScreen.update(tpf);
        }
    }

    private void registerInput() {
        inputManager.addMapping("RotateXNeg", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("RotateXPos", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("RotateYPos", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("RotateYNeg", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("ZoomIn", new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("ZoomOut", new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addListener(this, "RotateXNeg", "RotateXPos", "RotateYPos", "RotateYNeg", "ZoomIn", "ZoomOut");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "RotateXNeg" -> rotateXNeg = isPressed;
            case "RotateXPos" -> rotateXPos = isPressed;
            case "RotateYPos" -> rotateYPos = isPressed;
            case "RotateYNeg" -> rotateYNeg = isPressed;
            case "ZoomIn" -> zoomIn = isPressed;
            case "ZoomOut" -> zoomOut = isPressed;
            default -> {}
        }
    }

    private void applyRotationAndZoomInput() {
        float step = (float) (Math.PI / 180);
        if (rotateXNeg) settings.setRotationAnglesX(settings.getRotationAnglesX() - step);
        if (rotateXPos) settings.setRotationAnglesX(settings.getRotationAnglesX() + step);
        if (rotateYPos) settings.setRotationAnglesY(settings.getRotationAnglesY() + step);
        if (rotateYNeg) settings.setRotationAnglesY(settings.getRotationAnglesY() - step);
        if (zoomIn) settings.setCameraDistance(settings.getCameraDistance() - 1);
        if (zoomOut) settings.setCameraDistance(settings.getCameraDistance() + 1);
    }
}
