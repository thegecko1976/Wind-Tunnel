package io.github.wind_tunnel;

import com.jme3.scene.Node;

public interface Scene3DScreen {
    Node getSceneNode();

    default void attachScene(Node rootNode) {
        rootNode.attachChild(getSceneNode());
    }

    default void detachScene(Node rootNode) {
        getSceneNode().removeFromParent();
    }
}
