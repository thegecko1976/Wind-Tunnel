package io.github.wind_tunnel;

import com.jme3.scene.Spatial;

public interface Screen {
    Spatial getRoot();
    default void onEnter() {}
    default void onExit() {}
    default void update(float tpf) {}
}
