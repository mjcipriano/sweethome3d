package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.HomeComponent3D;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;

/**
 * Renders the 3D view off-screen, hides a piece of furniture <em>while the scene
 * is live</em>, and renders again, asserting the image changed. This guards the
 * regression where compiling the interactive scene made runtime edits (a piece's
 * Visible toggle, the detached window) stop affecting the 3D view: that bug left
 * the two renders identical. It exercises the persistent, home-listening
 * off-screen universe (the same live, uncompiled scene the on-screen view uses),
 * so it catches a silently non-updating scene that the construction-only tests
 * cannot. Needs an OpenGL context, so it runs in the Java 3D suite (test-local),
 * not the headless GUI suite.
 */
public class FurnitureVisibility3DTest {
  private static final int WIDTH = 320;
  private static final int HEIGHT = 240;

  @Test
  public void testFurnitureVisibilityAffectsLiveRendering() throws Exception {
    final UserPreferences preferences = new DefaultUserPreferences();
    CatalogPieceOfFurniture catalogPiece = firstCatalogPiece(preferences.getFurnitureCatalog());
    assertNotNull("No catalog piece available to render", catalogPiece);

    final Home home = new Home();
    // Give the scene bounds so the top camera frames it, then drop a large piece
    // in the middle so toggling it changes a clearly visible block of pixels.
    home.addWall(new Wall(0, 0, 400, 0, 8, 250));
    home.addWall(new Wall(400, 0, 400, 400, 8, 250));
    home.addWall(new Wall(400, 400, 0, 400, 8, 250));
    home.addWall(new Wall(0, 400, 0, 0, 8, 250));
    final HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    piece.setX(200);
    piece.setY(200);
    piece.setWidth(200);
    piece.setDepth(200);
    piece.setHeight(200);
    home.addPieceOfFurniture(piece);
    home.setCamera(home.getTopCamera());

    final HomeComponent3D [] componentHolder = new HomeComponent3D [1];
    SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          HomeComponent3D component = new HomeComponent3D(home, preferences, (HomeController3D)null);
          component.startOffscreenImagesCreation();
          componentHolder [0] = component;
        }
      });
    final HomeComponent3D component = componentHolder [0];
    try {
      BufferedImage withPiece = component.getOffScreenImage(WIDTH, HEIGHT);
      // Hide the piece on the live scene and let the deferred scene-graph update run.
      SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            piece.setVisible(false);
          }
        });
      flushEventQueue();
      BufferedImage withoutPiece = component.getOffScreenImage(WIDTH, HEIGHT);

      int changedPixels = differingPixels(withPiece, withoutPiece);
      assertTrue("Hiding a piece must change the live 3D rendering, but the image was "
          + "unchanged (changed pixels=" + changedPixels + "); the interactive scene is "
          + "not reflecting model edits", changedPixels > 300);
    } finally {
      SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            component.endOffscreenImagesCreation();
          }
        });
    }
  }

  private static CatalogPieceOfFurniture firstCatalogPiece(FurnitureCatalog catalog) {
    for (FurnitureCategory category : catalog.getCategories()) {
      if (!category.getFurniture().isEmpty()) {
        return category.getFurniture().get(0);
      }
    }
    return null;
  }

  /** Runs the deferred scene-graph updates queued on the AWT event thread. */
  private static void flushEventQueue() throws Exception {
    for (int i = 0; i < 3; i++) {
      SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
          }
        });
    }
  }

  private static int differingPixels(BufferedImage a, BufferedImage b) {
    int count = 0;
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        if (a.getRGB(x, y) != b.getRGB(x, y)) {
          count++;
        }
      }
    }
    return count;
  }
}
