package io.github.wind_tunnel.Menus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import io.github.wind_tunnel.ImportFile;
import io.github.wind_tunnel.Menu;
import io.github.wind_tunnel.MenuNavigator;
import io.github.wind_tunnel.MenuUtil;

import java.util.ArrayList;

public class ImportFileMenu implements Menu {
    private MenuUtil util;
    private ImportFile fileImporter;

    private Rectangle backButton;
    private Rectangle importButton;
    private Rectangle cancelButton;

    private Texture backIcon;
    private Texture importFileIcon;

    private String[] filePaths;
    private Rectangle[] fileButtons;
    private String selectedFile = null;
    private String[] selectedFileDetails = new String[0];

    private int pageNumber = 1;
    private int totalPages;
    private Rectangle[] pageArrows = new Rectangle[2];
    private int modelsPerPage = 9;

    public ImportFileMenu() {
        this.util = new MenuUtil();
        this.fileImporter = new ImportFile();

        this.backIcon = util.loadIcon("back");
        this.importFileIcon = util.loadIcon("folder");
    }

    @Override
    public void render(ShapeRenderer sr, SpriteBatch batch) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
            backButton = util.getHitbox(57.5f, 1022.5f, 75, 75);
            importButton = util.getHitbox(1163.75f, 242.5f, 287.5f, 75);
            cancelButton = util.getHitbox(1476.25f, 242.5f, 287.5f, 75);

            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 542.5f, 600, 475, 16); // file list

            util.renderRoundedRectangle(sr, util.getButtonColor(), 600, 242.5f, 600, 75, 16); // pages
            pageArrows[0] = util.renderRoundedTriangle(sr, Color.WHITE, 335, 242.5f, 12, 270); // left arrow
            pageArrows[1] = util.renderRoundedTriangle(sr, Color.WHITE, 865, 242.5f, 12, 90); // right arrow

            util.renderRoundedRectangle(sr, util.getButtonColor(), 1320, 542.5f, 600, 475, 16); // selected file

            util.renderRoundedRectangle(sr, util.getButtonColor(), 1163.75f, 242.5f, 287.5f, 75, 16); // import
            util.renderRoundedRectangle(sr, util.getButtonColor(), 1476.25f, 242.5f, 287.5f, 75, 16); // cancel
        sr.end();

        batch.begin();
            util.renderText(batch, "import a file", Color.WHITE, util.getScreenDimensions().x/2, 953, 96, "centre");

            fileButtons = new Rectangle[filePaths.length];

            if (filePaths.length == 0) {
                util.renderText(batch, "no files found in", Color.WHITE, 600, 517.5f, 36, "centre");
                util.renderText(batch, fileImporter.getModelsFolder(), Color.WHITE, 600, 467.5f, 36, "centre");
            } else {
                for (int count=0; count<filePaths.length; count++) {
                    if (Math.floorDiv(count, modelsPerPage) == pageNumber-1) {
                        util.renderText(batch, fileImporter.getFileNameAndExtension(filePaths[count]), Color.WHITE, 320, 742.5f-(50*(count-(Math.floorDiv(count, modelsPerPage)*modelsPerPage))), 36, "left");
                    }
                    fileButtons[count] = new Rectangle(300, 742.5f-(50*(count-(Math.floorDiv(count, modelsPerPage)*modelsPerPage)))-25, 600, 50);
                }
            }

            if (selectedFile == null) {
                util.renderText(batch, "no file selected", Color.WHITE, 1320, 542.5f, 36, "centre");
            } else {
                util.renderText(batch, fileImporter.getFileNameAndExtension(selectedFile), Color.WHITE, 1320, 742.5f, 36, "centre");
                for (int count=0; count<selectedFileDetails.length; count++) {
                    util.renderText(batch, selectedFileDetails[count], Color.WHITE, 1320, 667.5f-(50*count), 36, "centre");
                }
            }

            util.renderText(batch, "import", Color.WHITE, 1163.75f, 242.5f, 36, "centre"); // import
            util.renderText(batch, "cancel", Color.WHITE, 1476.25f, 242.5f, 36, "centre"); // cancel

            util.renderText(batch, "page " + pageNumber + "/" + totalPages, Color.WHITE, 600, 242.5f, 36, "centre"); // pages

            util.renderIcon(batch, backIcon, 57.5f, 1022.5f);
            util.renderIcon(batch, importFileIcon, 57.5f, 57.5f);
        batch.end();
    }

    @Override
    public void checkIfButtonsClicked(MenuNavigator navigator) {
        if (selectedFile != null && util.isButtonClicked(importButton)) {
            if (fileImporter.importFile(selectedFile)) {
                // go back past the settings menu to the menu that opened it
                navigator.back();
                navigator.back();
                return;
            }
        }
        if (util.isButtonClicked(cancelButton)) {
            selectedFile = null;
        }

        for (int count=0; count<filePaths.length; count++) {
            if (Math.floorDiv(count, modelsPerPage) == pageNumber-1 && util.isButtonClicked(fileButtons[count])) {
                selectFile(filePaths[count]);
                break;
            }
        }

        if (util.isButtonClicked(pageArrows[0]) && pageNumber > 1) {pageNumber -= 1;}
        if (util.isButtonClicked(pageArrows[1]) && pageNumber < totalPages) {pageNumber += 1;}

        if (util.isButtonClicked(backButton)) {navigator.back();}
    }

    @Override
    public void onEnter() {
        // the folders are read when the menu opens, rather than every frame
        filePaths = fileImporter.getAllFilePaths();
        totalPages = Math.max(1, (filePaths.length+modelsPerPage-1)/modelsPerPage);
    }

    private void selectFile(String filePath) {
        // the file is read once when it is selected, rather than every frame
        selectedFile = filePath;
        String modelType = fileImporter.getModelType(filePath);
        if ("2D".equals(modelType)) {
            Vector3 resolution = fileImporter.getModelResolution(filePath);
            selectedFileDetails = new String[] {
                "resolution: " + (int) resolution.x + "x" + (int) resolution.y,
                "type: " + modelType,
                "barriers: " + fileImporter.getModelBarriers(filePath)
            };
        } else if ("3D".equals(modelType)) {
            ArrayList<Vector3> vertices = fileImporter.getVerticesFromOBJModel(filePath);
            ArrayList<int[]> faces = fileImporter.getFacesFromOBJModel(filePath);
            if (vertices == null || faces == null) {
                selectedFileDetails = new String[] {"type: " + modelType, "file can't be read"};
            } else {
                selectedFileDetails = new String[] {"type: " + modelType, "vertices: " + vertices.size(), "faces: " + faces.size()};
            }
        } else {
            selectedFileDetails = new String[0];
        }
    }
}
