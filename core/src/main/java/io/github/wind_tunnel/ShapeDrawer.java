package io.github.wind_tunnel;

import com.badlogic.gdx.math.Vector3;

public class ShapeDrawer {

    private Settings settings;
    private LatticeBoltzmannCFDSolver cfdSolver;

    private Vector3 rayDirection;
    private double rayDirectionLength;
    private Vector3 rayUnitStep = new Vector3();
    private Vector3 check;
    private Vector3 rayLength = new Vector3();
    private Vector3 step = new Vector3();

    public ShapeDrawer() {
        settings = Settings.getInstance();
        cfdSolver = LatticeBoltzmannCFDSolver.getInstance();
    }

    public void fill(Vector3 from, Vector3 to, boolean fixedBarrier) {
        for (float x=Math.min(from.x, to.x); x<=Math.max(from.x, to.x); x++) {
            for (float y=Math.min(from.y, to.y); y<=Math.max(from.y, to.y); y++) {
                for (float z=Math.min(from.z, to.z); z<=Math.max(from.z, to.z); z++) {
                    cfdSolver.addBarrier((int) x, (int) y, (int) z, fixedBarrier);
                }
            }
        }
    }

    public void line(Vector3 from, Vector3 to, boolean fixedBarrier) {

        // centre both from and to so that it raycasts from the centre of the cell
        from = new Vector3(from).add(0.5f, 0.5f, 0.5f);
        to = new Vector3(to).add(0.5f, 0.5f, 0.5f);

        // draw a line using the DDA algorithm
        rayDirection = new Vector3(to.x-from.x, to.y-from.y, to.z-from.z);
        rayDirectionLength = Math.sqrt(rayDirection.x*rayDirection.x + rayDirection.y*rayDirection.y + rayDirection.z*rayDirection.z);

        if (rayDirectionLength == 0) {
            cfdSolver.addBarrier((int) from.x, (int) from.y, (int) from.z, fixedBarrier);
            return; // from equals to, no direction to cast, so just place 1 barrier
        }
        rayDirection.x = (float) (rayDirection.x/rayDirectionLength);
        rayDirection.y = (float) (rayDirection.y/rayDirectionLength);
        rayDirection.z = (float) (rayDirection.z/rayDirectionLength);

        // ray distances needed to cross one cell on each axis
        if (rayDirection.x != 0) {
            rayUnitStep.x = (float) Math.sqrt(1 + (rayDirection.y/rayDirection.x)*(rayDirection.y/rayDirection.x) + (rayDirection.z/rayDirection.x)*(rayDirection.z/rayDirection.x));
        } else {
            rayUnitStep.x = (float) Double.POSITIVE_INFINITY;
        }

        if (rayDirection.y != 0) {
            rayUnitStep.y = (float) Math.sqrt(1 + (rayDirection.x/rayDirection.y)*(rayDirection.x/rayDirection.y) + (rayDirection.z/rayDirection.y)*(rayDirection.z/rayDirection.y));
        } else {
            rayUnitStep.y = (float) Double.POSITIVE_INFINITY;
        }

        if (rayDirection.z != 0) {
            rayUnitStep.z = (float) Math.sqrt(1 + (rayDirection.x/rayDirection.z)*(rayDirection.x/rayDirection.z) + (rayDirection.y/rayDirection.z)*(rayDirection.y/rayDirection.z));
        } else {
            rayUnitStep.z = (float) Double.POSITIVE_INFINITY;
        }

        check = new Vector3((int) Math.floor(from.x), (int) Math.floor(from.y), (int) Math.floor(from.z));

        if (rayDirection.x < 0) {
            step.x = -1;
            rayLength.x = (from.x-check.x)*rayUnitStep.x;
        } else {
            step.x = 1;
            rayLength.x = (check.x-from.x+1)*rayUnitStep.x;
        }

        if (rayDirection.y < 0) {
            step.y = -1;
            rayLength.y = (from.y-check.y)*rayUnitStep.y;
        } else {
            step.y = 1;
            rayLength.y = (check.y-from.y+1)*rayUnitStep.y;
        }

        if (rayDirection.z < 0) {
            step.z = -1;
            rayLength.z = (from.z-check.z)*rayUnitStep.z;
        } else {
            step.z = 1;
            rayLength.z = (check.z-from.z+1)*rayUnitStep.z;
        }

        // prevents indefinite iteration by finding the number of barriers apart from and to are in each axis
        int maxSteps = Math.abs((int) (Math.floor(to.x)-check.x)) + Math.abs((int) (Math.floor(to.y)-check.y)) + Math.abs((int) (Math.floor(to.z)-check.z));
        for (int steps=0; steps<=maxSteps; steps++) {
            cfdSolver.addBarrier((int) check.x, (int) check.y, (int) check.z, fixedBarrier);

            // move along the axis that has the shortest ray length
            if (rayLength.x < rayLength.y && rayLength.x < rayLength.z) {
                check.x += step.x;
                rayLength.x += rayUnitStep.x;
            } else if (rayLength.y < rayLength.z) {
                check.y += step.y;
                rayLength.y += rayUnitStep.y;
            } else {
                check.z += step.z;
                rayLength.z += rayUnitStep.z;
            }
        }
    }

    public void triangle(Vector3 a, Vector3 b, Vector3 c, boolean fixedBarrier) {
        // fill the whole surface of a 3D triangle with barriers by stepping across it in small steps,
        // using p = a + s*(b-a) + t*(c-a) for every s, t >= 0 with s + t <= 1
        float longestSide = Math.max(a.dst(b), Math.max(b.dst(c), c.dst(a)));
        int steps = Math.max(1, (int) Math.ceil(longestSide*4)); // a quarter of a cell apart, so no cell the triangle passes through is missed

        for (int i=0; i<=steps; i++) {
            for (int j=0; j<=steps-i; j++) {
                float s = (float) i/steps;
                float t = (float) j/steps;
                float x = a.x + s*(b.x-a.x) + t*(c.x-a.x);
                float y = a.y + s*(b.y-a.y) + t*(c.y-a.y);
                float z = a.z + s*(b.z-a.z) + t*(c.z-a.z);
                // + 0.5 rounds to the nearest cell, the same as line() does
                cfdSolver.addBarrier((int) Math.floor(x+0.5f), (int) Math.floor(y+0.5f), (int) Math.floor(z+0.5f), fixedBarrier);
            }
        }
    }

    public void circle(int x, int y, int z, int radius, boolean fill, boolean fixedBarrier) {
        for (int dx=-radius; dx<=radius; dx++) {
            for (int dy=-radius; dy<=radius; dy++) {
                if (dx*dx + dy*dy > radius*radius) { // if point is outside circle
                    continue;
                }
                if (!fill) { // skip filling the middle to make a hollow circle
                    if (dx*dx + dy*dy < (radius-1)*(radius-1)) {
                        continue;
                    }
                }
                int cellX = x+dx;
                int cellY = y+dy;

                // point outside simulation area
                if (cellX <= 0 || cellX >= settings.getResolution().x-1 || cellY <= 0 || cellY >= settings.getResolution().y-1) {
                    continue;
                }
                cfdSolver.addBarrier(cellX, cellY, z, fixedBarrier);
            }
        }
    }

    public void arc(int x, int y, int z, int radius, double startAngle, double endAngle, boolean fixedBarrier) {
        /*
        x(t) = x + r*cos(t)
        y(t) = y + r*sin(t)
        where t varies from start angle to end angle in radians
         */

        Vector3 point = new Vector3();
        startAngle = Math.toRadians(startAngle);
        endAngle = Math.toRadians(endAngle);

        for (double angle=startAngle; angle<endAngle; angle+=Math.toRadians(1)) {
            point.x = (float) (x + radius*Math.cos(angle));
            point.y = (float) (y + radius*Math.sin(angle));
            point.z = z;

            // point outside simulation area
            if (point.x <= 0 || point.x >= settings.getResolution().x-1 || point.y <= 0 || point.y >= settings.getResolution().y-1) {
                continue;
            }

            cfdSolver.addBarrier((int) point.x, (int) point.y, z, fixedBarrier);
        }
    }
}
