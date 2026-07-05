package io.github.wind_tunnel;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.Panel;

// Bridges the app's existing "center point" coordinate convention (every element in the
// original code was positioned by its center) onto Lemur's convention of anchoring a Panel's
// local translation at its upper-left corner, growing down-and-right from there in the same
// y-up pixel space the app already used.
public final class LayoutHelper {

    public static void anchorCentered(Panel p, float centerX, float centerY) {
        Vector3f size = p.getPreferredSize();
        p.setLocalTranslation(centerX - size.x / 2f, centerY + size.y / 2f, 0);
    }

    public static void anchorCentered(Panel p, float centerX, float centerY, float width, float height) {
        p.setPreferredSize(new Vector3f(width, height, 0));
        anchorCentered(p, centerX, centerY);
    }

    private LayoutHelper() {}
}
