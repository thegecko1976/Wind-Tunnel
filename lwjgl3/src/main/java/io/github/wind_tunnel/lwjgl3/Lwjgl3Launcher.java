package io.github.wind_tunnel.lwjgl3;

import com.jme3.system.AppSettings;
import io.github.wind_tunnel.WindTunnelApp;

/** Launches the desktop (jME3/LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static WindTunnelApp createApplication() {
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Wind-Tunnel");
        settings.setResolution(1920, 1080);
        settings.setVSync(true);
        settings.setUseInput(true);

        WindTunnelApp app = new WindTunnelApp();
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
        return app;
    }
}
