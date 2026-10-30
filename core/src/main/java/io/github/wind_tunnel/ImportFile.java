package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;

public class ImportFile {

    private Settings settings;
    private LatticeBoltzmannCFDSolver cfdSolver;
    private ShapeDrawer shapeDrawer;

    private String modelsFolder = "assets/models/";
    private String levelsFolder = "assets/levels/";

    public ImportFile() {
        settings = Settings.getInstance();
        cfdSolver = LatticeBoltzmannCFDSolver.getInstance();
        shapeDrawer = new ShapeDrawer();
    }

    public boolean importFile(String filePath) {
        if (getFileExtension(filePath).equals("bmp")) {
            for (int[] resolution : settings.getResolutionValues()) {
                if (getModelResolution(filePath).x <= resolution[0] && getModelResolution(filePath).y <= resolution[1]) {
                    cfdSolver.removeAllBarriers();
                    settings.setSolver(settings.getSolverValues()[0]);
                    settings.setResolution(new Vector3(resolution[0], resolution[1], 1));
                    cfdSolver.initialiseFluid();
                    loadBitmap(filePath, false);
                    return true;
                }
            }
        } else if (getFileExtension(filePath).equals("obj")) {
            if (getVerticesFromOBJModel(filePath) == null || getFacesFromOBJModel(filePath) == null) {return false;} // can't be read, so leave the simulation as it is
            cfdSolver.removeAllBarriers();
            settings.setResolution(new Vector3(settings.getResolutionValues()[2][0], settings.getResolutionValues()[2][1], settings.getResolutionValues()[2][1]));
            settings.setSolver(settings.getSolverValues()[1]);
            cfdSolver.initialiseFluid();
            loadOBJModel(filePath, false);
            return true;
        }
        return false;
    }

    public void loadBitmap(String filePath, boolean fixedBarrier) {
        FileHandle file = Gdx.files.internal(filePath);
        if (!file.exists()) {return;}

        Pixmap image = new Pixmap(file);
        if (image.getWidth() > settings.getResolution().x || image.getHeight() > settings.getResolution().y || settings.getResolution().z != 1) { // too big
            image.dispose();
            return;
        }

        for (int x=0; x<image.getWidth(); x++) {
            for (int y=0; y<image.getHeight(); y++) {
                int colour = image.getPixel(x, image.getHeight()-y-1);
                if (colour == 0x000000FF) { // black
                    cfdSolver.addBarrier(x, y, 0, fixedBarrier);
                }
            }
        }
        image.dispose();
    }

    public void loadOBJModel(String filePath, boolean fixedBarrier) {
        /*
        blender export as .obj rules

        turn off:
        UV coordinates
        normals
        colours
        curves as NURBS
        materials
        animation

        turn on:
        triangulated mesh
        apply modifiers
        apply transform

        ensure:
        forward axis = y
        up axis = z
        */

        ArrayList<Vector3> vertices = getVerticesFromOBJModel(filePath);
        ArrayList<int[]> faces = getFacesFromOBJModel(filePath);

        if (vertices == null || faces == null) {return;}

        // draw lines (using shapeDrawer.line()) from each vertex to its neighbour according to the faces from the loaded .obj

        // bounding box of the model, where min is the front-bottom-left corner and max is the back-top-right corner
        Vector3 min = new Vector3(vertices.get(0));
        Vector3 max = new Vector3(vertices.get(0));
        for (Vector3 vertex : vertices) {
            min.x = Math.min(min.x, vertex.x);
            min.y = Math.min(min.y, vertex.y);
            min.z = Math.min(min.z, vertex.z);
            max.x = Math.max(max.x, vertex.x);
            max.y = Math.max(max.y, vertex.y);
            max.z = Math.max(max.z, vertex.z);
        }

        Vector3 centre = new Vector3((min.x+max.x)/2, (min.y+max.y)/2, (min.z+max.z)/2); // centre of the bounding box
        float size = Math.max(max.x-min.x, Math.max(max.y-min.y, max.z-min.z)); // finds the longest side as if it fits everything does
        if (size == 0) {return;}

        // scale the model so that it takes up half the height
        float scale = settings.getResolution().y/(2*size);

        for (Vector3 vertex : vertices) {
            // shifts the entire model so its bounding box centre is at the origin, scales it to be half the simulation height, and move it to the centre of the simulation area
            vertex.x = (vertex.x-centre.x)*scale + settings.getResolution().x/2;
            vertex.y = (vertex.y-centre.y)*scale + settings.getResolution().y/2;
            vertex.z = (vertex.z-centre.z)*scale + settings.getResolution().z/2;
        }

        for (int[] face : faces) {
            for (int count=0; count<face.length; count++) {
                Vector3 vertex1 = vertices.get(face[count]);
                Vector3 vertex2 = vertices.get(face[(count+1)%face.length]);
                shapeDrawer.line(vertex1, vertex2, fixedBarrier);
            }
        }
    }

    public ArrayList<Vector3> getVerticesFromOBJModel(String filePath) {
        FileHandle file = Gdx.files.internal(filePath);
        if (!file.exists()) {return null;}

        String[] contents = file.readString().split("\\r?\\n"); // split at every new line
        ArrayList<Vector3> vertices = new ArrayList<>();

        String[] lineList;
        for (String line : contents) {
            if (line.isEmpty()) {continue;}

            lineList = line.split("\\s+");
            if (line.startsWith("v ")) {
                if (lineList.length < 4) {return null;} // a vertex needs x, y and z
                try {
                    vertices.add(new Vector3(Float.parseFloat(lineList[1]), Float.parseFloat(lineList[2]), Float.parseFloat(lineList[3])));
                } catch (NumberFormatException e) {
                    return null; // not a valid .obj file
                }
            }
        }
        if (vertices.isEmpty()) {return null;}

        return vertices;
    }

    public ArrayList<int[]> getFacesFromOBJModel(String filePath) {
        FileHandle file = Gdx.files.internal(filePath);
        if (!file.exists()) {return null;}

        String[] contents = file.readString().split("\\r?\\n"); // split at every new line
        ArrayList<int[]> faces = new ArrayList<>();
        int numberOfVertices = 0;

        String[] lineList;
        for (String line : contents) {
            if (line.isEmpty()) {continue;}

            lineList = line.split("\\s+");
            if (line.startsWith("v ")) {
                numberOfVertices += 1; // counted so negative (relative) indices can be worked out
            } else if (line.startsWith("f ")) {
                int[] face = new int[lineList.length-1];
                for (int count=1; count<lineList.length; count++) {
                    // each corner is written as v, v/vt, v/vt/vn or v//vn, and only v (the vertex index) is needed
                    int index;
                    try {
                        index = Integer.parseInt(lineList[count].split("/")[0]);
                    } catch (NumberFormatException e) {
                        return null; // not a valid .obj file
                    }
                    if (index < 0) {
                        face[count-1] = numberOfVertices+index; // negative indices count back from the latest vertex
                    } else {
                        face[count-1] = index-1; // positive indices start at 1
                    }
                }
                faces.add(face);
            }
        }
        if (faces.isEmpty()) {return null;}

        for (int[] face : faces) {
            for (int index : face) {
                if (index < 0 || index >= numberOfVertices) {return null;} // refers to a vertex that doesn't exist
            }
        }

        return faces;
    }

    public String[] getAllFilePaths() {
        FileHandle[] modelFiles = Gdx.files.internal(modelsFolder).list();
        FileHandle[] levelFiles = Gdx.files.internal(levelsFolder).list();
        String[] filePaths = new String[modelFiles.length+levelFiles.length];
        for (int count=0; count<modelFiles.length; count++) {
            filePaths[count] = modelFiles[count].path();
        }
        for (int count=modelFiles.length; count<modelFiles.length+levelFiles.length; count++) {
            filePaths[count] = levelFiles[count-modelFiles.length].path();
        }
        return filePaths;
    }

    public String getFileNameAndExtension(String filePath) {
        return Gdx.files.internal(filePath).name();
    }

    public String getFullFilePath(String filePath) {
        return Gdx.files.internal(filePath).path();
    }

    public String getFileExtension(String filePath) {
        return Gdx.files.internal(filePath).extension();
    }

    public String getModelType(String filePath) {
        if (getFileExtension(filePath).equals("bmp")) {
            return "2D";
        } else if (getFileExtension(filePath).equals("obj")) {
            return "3D";
        }
        return null;
    }

    public Integer getModelBarriers(String filePath) {
        FileHandle file = Gdx.files.internal(filePath);
        if (!file.exists()) {return null;}

        int barriers = 0;
        if (file.extension().equals("bmp")) { // 2D file
            Pixmap image = new Pixmap(file);
            for (int x=0; x<image.getWidth(); x++) {
                for (int y=0; y<image.getHeight(); y++) {
                    int colour = image.getPixel(x, image.getHeight()-y-1);
                    if (colour == 0x000000FF) { // black
                        barriers += 1;
                    }
                }
            }
            image.dispose();
            return barriers;
        }

        return null;
    }

    public Vector3 getModelResolution(String filePath) {
        FileHandle file = Gdx.files.internal(filePath);
        if (!file.exists()) {return new Vector3(0, 0, 0);}

        if (file.extension().equals("bmp")) { // 2D file
            Pixmap image = new Pixmap(file);
            Vector3 resolution = new Vector3(image.getWidth(), image.getHeight(), 0);
            image.dispose(); // a Pixmap's memory is only freed by dispose(), not by the garbage collector
            return resolution;
        } else if (file.extension().equals("obj")) { // 3D file
            System.out.println("3D file");
        }
        return new Vector3(0, 0, 0);
    }

    public String getModelsFolder() {return this.modelsFolder;}
    public String getLevelsFolder() {return this.levelsFolder;}
}
