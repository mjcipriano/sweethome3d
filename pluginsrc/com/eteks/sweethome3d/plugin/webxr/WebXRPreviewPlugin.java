package com.eteks.sweethome3d.plugin.webxr;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.j3d.Ground3D;
import com.eteks.sweethome3d.j3d.ModelLODGenerator;
import com.eteks.sweethome3d.j3d.ModelManager;
import com.eteks.sweethome3d.j3d.OBJWriter;
import com.eteks.sweethome3d.j3d.Object3DBranchFactory;
import com.eteks.sweethome3d.viewcontroller.Object3DFactory;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ModelLOD;
import com.eteks.sweethome3d.model.InterruptedRecorderException;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.viewcontroller.View;
import javax.media.j3d.Node;

/**
 * WebXR preview plugin: exports the current home to OBJ/MTL, serves a local
 * WebXR page, and opens it in the default browser.
 */
public class WebXRPreviewPlugin extends Plugin {
  private WebXRPreviewServer previewServer;
  private File               previewDir;

  /**
   * The count of furniture triangles a standalone headset draws at a comfortable
   * frame rate and loads in its browser's memory.
   */
  static final long TRIANGLE_BUDGET =
      Long.getLong("com.eteks.sweethome3d.webxr.triangleBudget", 900000L).longValue();
  /** Models with fewer drawn vertices than this are never reduced. */
  private static final int HEAVY_MODEL_VERTICES = 6000;
  /** The geometric error accepted to reach the budget, relative to a mesh size. */
  private static final float HEADSET_TARGET_ERROR = 0.05f;
  /** Models are never reduced beyond this ratio. */
  private static final float MIN_REDUCTION_RATIO = 0.01f;

  /** The files of the viewer copied from the plugin resources to each export. */
  private static final String [] VIEWER_RESOURCES = {"webxr-viewer.js", "webxr-walk.js"};

  private final PluginAction[] actions;

  public WebXRPreviewPlugin() {
    this.actions = new PluginAction[] { new WebXRPreviewAction() };
  }

  @Override
  public PluginAction [] getActions() {
    return this.actions;
  }

  private class WebXRPreviewAction extends PluginAction {
    WebXRPreviewAction() {
      putPropertyValue(Property.NAME, "WebXR Preview...");
      putPropertyValue(Property.SHORT_DESCRIPTION, "Export current home to OBJ and open a WebXR viewer");
      putPropertyValue(Property.MENU, "Tools");
      setEnabled(true);
    }

    @Override
    public void execute() {
      final WebXRPreviewAction self = this;
      self.setEnabled(false);
      Thread worker = new Thread(new Runnable() {
        public void run() {
          try {
            launchPreview();
          } catch (Exception ex) {
            showError("WebXR Preview failed: " + ex.getMessage(), ex);
          } finally {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                self.setEnabled(true);
              }
            });
          }
        }
      }, "WebXRPreview");
      worker.setDaemon(true);
      worker.start();
    }
  }

  private void launchPreview() throws Exception {
    Home home = getHome();
    if (home == null) {
      throw new IllegalStateException("No home is loaded.");
    }

    File exportDir = createExportDir();
    WebXRPreviewServer server;
    try {
      Home exportedHome = exportHome(exportDir, home);
      writeViewer(exportDir, exportedHome);
      server = new WebXRPreviewServer(exportDir);
      server.start();
    } catch (Exception ex) {
      deleteDirectory(exportDir);
      throw ex;
    }
    File indexFile = new File(exportDir, "index.html");
    // Only the latest preview stays served: stop the previous one and delete its export
    stopPreview();
    synchronized (this) {
      this.previewServer = server;
      this.previewDir = exportDir;
    }
    openInBrowser(new URL(server.getLocalUrl()), indexFile);
    showConnectInstructions(server);
  }

  /**
   * Shows how to reach the preview from a headset on the local network
   * (Meta Quest 2/3, or any WebXR browser).
   */
  private void showConnectInstructions(WebXRPreviewServer server) {
    StringBuilder message = new StringBuilder("WebXR preview is running.\n\n")
        .append("Desktop browser: ").append(server.getLocalUrl()).append('\n');
    List<String> lanUrls = server.getLanUrls();
    if (lanUrls.isEmpty()) {
      message.append("\nNo LAN address with https is available, so a headset can't connect.\n")
          .append("Check that this computer is on the same network as the headset.");
    } else {
      message.append("\nOn your headset (Quest 2/3: open the Browser app), go to:\n");
      for (String url : lanUrls) {
        message.append("    ").append(url).append('\n');
      }
      message.append("\nThe browser will warn about the self-signed certificate once -\n")
          .append("choose Advanced > Proceed. Then press \"Enter VR\".\n\n")
          .append("You start where the virtual visitor stands, at life size.\n")
          .append("Left stick: walk. Right stick: turn, or push forward to aim and\n")
          .append("release to teleport. A / B: level above / below. Y: back to start.\n")
          .append("Walls stop you and doorways let you through (X switches this off).\n\n")
          .append("SteamVR/PC headsets: open the desktop URL above in Chrome or Edge\n")
          .append("with the headset connected (WebXR uses the active OpenXR runtime).");
    }
    showInfo(message.toString());
  }

  /**
   * Stops the running preview server, if any, and deletes its exported files.
   */
  private void stopPreview() {
    WebXRPreviewServer server;
    File directory;
    synchronized (this) {
      server = this.previewServer;
      directory = this.previewDir;
      this.previewServer = null;
      this.previewDir = null;
    }
    if (server != null) {
      server.stop();
    }
    if (directory != null) {
      deleteDirectory(directory);
    }
  }

  /**
   * Stops serving the home on the network when its window is closed.
   */
  @Override
  public void destroy() {
    stopPreview();
  }

  private static void deleteDirectory(File directory) {
    File [] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory() && !java.nio.file.Files.isSymbolicLink(file.toPath())) {
          deleteDirectory(file);
        } else {
          file.delete();
        }
      }
    }
    directory.delete();
  }

  private File createExportDir() throws IOException {
    // A new directory with an unpredictable name, private to the user where supported
    return java.nio.file.Files.createTempDirectory("sh3d-webxr-").toFile();
  }

  /**
   * Exports <code>home</code> to OBJ and returns the copy of the home that was exported.
   */
  private Home exportHome(File exportDir, Home home) throws RecorderException {
    // A factory of its own, which displays the reduced models chosen for the headset
    Object3DFactory factory = new Object3DBranchFactory();
    Home clonedHome = cloneHomeInEDT(home);
    // The plan is described from a second copy, as the export changes the selection of the first
    Home describedHome = cloneHomeInEDT(home);
    exportModel(exportDir, clonedHome, factory);
    showAllLevels(describedHome);
    return describedHome;
  }

  /**
   * Exports to OBJ every viewable level of <code>home</code>, so the visitor
   * can go to each floor whatever level is selected in the application.
   * Caution: the selection and the visible levels of <code>home</code> are modified.
   */
  public static void exportModel(File exportDir, Home home, Object3DFactory factory) throws RecorderException {
    File objFile = new File(exportDir, "scene.obj");
    String header = "Exported " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    showAllLevels(home);
    reduceModelsForHeadset(home, TRIANGLE_BUDGET);
    OBJExportHelper.exportHomeToFile(home, objFile.getAbsolutePath(), header, true, factory);
  }

  /**
   * Switches the furniture of <code>home</code> to reduced models when its
   * models have more triangles altogether than a standalone headset can draw
   * and load. Every heavy model is reduced by the same ratio, so the furniture
   * keeps its relative level of detail. Does nothing if the scene is light
   * enough or if the model simplification library isn't available.
   * @return the estimated count of triangles of the furniture after reduction
   */
  static long reduceModelsForHeadset(Home home, long triangleBudget) {
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    collectDisplayedPieces(home.getFurniture(), pieces);
    // Count how many drawn vertices each model brings to the scene
    final java.util.Map<Content, Integer> pieceCounts = new java.util.LinkedHashMap<Content, Integer>();
    for (HomePieceOfFurniture piece : pieces) {
      Integer count = pieceCounts.get(piece.getModel());
      pieceCounts.put(piece.getModel(), count == null ? 1 : count + 1);
    }
    java.util.Map<Content, Integer> vertexCounts = new java.util.HashMap<Content, Integer>();
    long lightVertices = 0;
    long heavyVertices = 0;
    List<Content> heavyModels = new ArrayList<Content>();
    for (java.util.Map.Entry<Content, Integer> entry : pieceCounts.entrySet()) {
      Content model = entry.getKey();
      // Loading is synchronous here; the export needs every model anyway
      ModelManager.getInstance().loadModel(model, true, new ModelManager.ModelObserver() {
          public void modelUpdated(javax.media.j3d.BranchGroup modelRoot) {
          }

          public void modelError(Exception ex) {
          }
        });
      int vertexCount = ModelManager.getInstance().getModelVertexCount(model);
      vertexCounts.put(model, vertexCount);
      long vertices = (long)vertexCount * entry.getValue();
      if (vertexCount > HEAVY_MODEL_VERTICES) {
        heavyModels.add(model);
        heavyVertices += vertices;
      } else {
        lightVertices += vertices;
      }
    }
    long vertexBudget = triangleBudget * 3;
    if (lightVertices + heavyVertices <= vertexBudget
        || heavyModels.isEmpty()
        || !ModelLODGenerator.isAvailable()) {
      return (lightVertices + heavyVertices) / 3;
    }
    float ratio = Math.max(MIN_REDUCTION_RATIO,
        (float)Math.max(0, vertexBudget - lightVertices) / heavyVertices);
    long reducedVertices = lightVertices;
    ModelLODGenerator generator = new ModelLODGenerator();
    for (Content model : heavyModels) {
      long vertices = (long)vertexCounts.get(model) * pieceCounts.get(model);
      try {
        ModelLOD modelLOD = generator.generate(model, ratio, HEADSET_TARGET_ERROR);
        if (modelLOD != null && modelLOD.getSourceVertexCount() > 0) {
          home.setModelLOD(model, modelLOD);
          for (HomePieceOfFurniture piece : pieces) {
            if (model.equals(piece.getModel())) {
              piece.setProperty(ModelLOD.LOW_POLY_PROPERTY, "true");
            }
          }
          vertices = vertices * modelLOD.getVertexCount() / modelLOD.getSourceVertexCount();
        }
      } catch (IOException ex) {
        // Keep the original model
      } catch (RuntimeException ex) {
        // Keep the original model
      } catch (OutOfMemoryError ex) {
        // Keep the original model
      }
      reducedVertices += vertices;
    }
    return reducedVertices / 3;
  }

  private static void collectDisplayedPieces(List<HomePieceOfFurniture> furniture,
                                             List<HomePieceOfFurniture> pieces) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collectDisplayedPieces(((HomeFurnitureGroup)piece).getFurniture(), pieces);
      } else if (piece.getModel() != null && piece.isVisible()
          && (piece.getLevel() == null || piece.getLevel().isViewable())) {
        // Pieces keep the full model unless the budget asks to reduce it
        piece.setProperty(ModelLOD.LOW_POLY_PROPERTY, null);
        pieces.add(piece);
      }
    }
  }

  private static void showAllLevels(Home home) {
    for (Level level : home.getLevels()) {
      if (level.isViewable()) {
        level.setVisible(true);
      }
    }
  }

  /**
   * Writes the viewer page, its scripts and the description of the plan of
   * <code>home</code> in <code>exportDir</code>, and returns the page.
   */
  public static File writeViewer(File exportDir, Home home) throws IOException {
    copyResource("webxr-viewer.html", new File(exportDir, "index.html"));
    for (String resource : VIEWER_RESOURCES) {
      copyResource(resource, new File(exportDir, resource));
    }
    OutputStream out = new java.io.FileOutputStream(new File(exportDir, "scene.json"));
    try {
      out.write(WebXRSceneDescription.toJSON(home).getBytes(StandardCharsets.UTF_8));
    } finally {
      out.close();
    }
    return new File(exportDir, "index.html");
  }

  private static void copyResource(String resource, File file) throws IOException {
    java.io.InputStream in = WebXRPreviewPlugin.class.getResourceAsStream(resource);
    if (in == null) {
      throw new IOException("Missing plugin resource " + resource);
    }
    try {
      java.nio.file.Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } finally {
      in.close();
    }
  }

  private void openInBrowser(URL url, File indexFile) {
    boolean opened = false;
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        try {
          desktop.browse(URI.create(url.toString()));
          opened = true;
        } catch (Exception ex) {
          // Fallback below
        }
      }
    }
    if (!opened) {
      showInfo("Open this URL in a WebXR-capable browser:\n" + url.toString()
          + "\n\nFiles are in " + indexFile.getParent());
    }
  }

  private void showError(final String message, Exception ex) {
    ex.printStackTrace();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JOptionPane.showMessageDialog(null, message, "WebXR Preview", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private void showInfo(final String message) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JOptionPane.showMessageDialog(null, message, "WebXR Preview", JOptionPane.INFORMATION_MESSAGE);
      }
    });
  }

  private Home cloneHomeInEDT(final Home home) throws RecorderException {
    if (EventQueue.isDispatchThread()) {
      return home.clone();
    }
    try {
      final AtomicReference<Home> cloned = new AtomicReference<Home>();
      EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          cloned.set(home.clone());
        }
      });
      return cloned.get();
    } catch (InterruptedException ex) {
      throw new InterruptedRecorderException(ex.getMessage());
    } catch (InvocationTargetException ex) {
      throw new RecorderException("Couldn't clone home", ex.getCause());
    }
  }

  /**
   * Helper that mirrors HomePane OBJ export logic.
   */
  private static class OBJExportHelper {
    private static void exportHomeToFile(Home home, String objFile, String header,
                                         boolean exportAllToOBJ, Object3DFactory object3dFactory) throws RecorderException {
      OBJWriter writer = null;
      boolean exportInterrupted = false;
      try {
        writer = new OBJWriter(objFile, header, -1);

        List<Selectable> exportedItems = new ArrayList<Selectable>(exportAllToOBJ
            ? home.getSelectableViewableItems()
            : home.getSelectedItems());
        List<Selectable> furnitureInGroups = new ArrayList<Selectable>();
        for (java.util.Iterator<Selectable> it = exportedItems.iterator(); it.hasNext();) {
          Selectable selectable = it.next();
          if (selectable instanceof HomeFurnitureGroup) {
            it.remove();
            for (HomePieceOfFurniture piece : ((HomeFurnitureGroup)selectable).getAllFurniture()) {
              if (!(piece instanceof HomeFurnitureGroup)) {
                furnitureInGroups.add(piece);
              }
            }
          }
        }
        exportedItems.addAll(furnitureInGroups);

        List<Selectable> emptySelection = java.util.Collections.emptyList();
        home.setSelectedItems(emptySelection);
        if (exportAllToOBJ) {
          java.awt.geom.Rectangle2D homeBounds = getExportedHomeBounds(home);
          if (homeBounds != null) {
            Ground3D groundNode = new Ground3D(home,
                (float)homeBounds.getX(), (float)homeBounds.getY(),
                (float)homeBounds.getWidth(), (float)homeBounds.getHeight(), true);
            writer.writeNode(groundNode, "ground");
          }
        } else if (home.isAllLevelsSelection()) {
          for (Level level : home.getLevels()) {
            if (level.isViewable()) {
              level.setVisible(true);
            }
          }
        }

        int i = 0;
        for (Selectable item : exportedItems) {
          Node node = (Node)object3dFactory.createObject3D(home, item, true);
          if (node != null) {
            if (item instanceof HomePieceOfFurniture) {
              writer.writeNode(node);
            } else if (!(item instanceof DimensionLine)) {
              writer.writeNode(node, item.getClass().getSimpleName().toLowerCase(Locale.US) + "_" + ++i);
            }
          }
        }
      } catch (java.io.InterruptedIOException ex) {
        exportInterrupted = true;
        throw new InterruptedRecorderException("Export to " + objFile + " interrupted");
      } catch (IOException ex) {
        throw new RecorderException("Couldn't export to OBJ in " + objFile, ex);
      } finally {
        if (writer != null) {
          try {
            writer.close();
            if (exportInterrupted) {
              new File(objFile).delete();
            }
          } catch (IOException ex) {
            throw new RecorderException("Couldn't export to OBJ in " + objFile, ex);
          }
        }
      }
    }

    private static java.awt.geom.Rectangle2D getExportedHomeBounds(Home home) {
      java.awt.geom.Rectangle2D homeBounds = updateObjectsBounds(null, home.getWalls());
      for (HomePieceOfFurniture piece : getVisibleFurniture(home.getFurniture())) {
        for (float [] point : piece.getPoints()) {
          if (homeBounds == null) {
            homeBounds = new java.awt.geom.Rectangle2D.Float(point [0], point [1], 0, 0);
          } else {
            homeBounds.add(point [0], point [1]);
          }
        }
      }
      return updateObjectsBounds(homeBounds, home.getRooms());
    }

    private static List<HomePieceOfFurniture> getVisibleFurniture(List<HomePieceOfFurniture> furniture) {
      List<HomePieceOfFurniture> visibleFurniture = new ArrayList<HomePieceOfFurniture>(furniture.size());
      for (HomePieceOfFurniture piece : furniture) {
        if (piece.isVisible()
            && (piece.getLevel() == null
                || piece.getLevel().isViewable())) {
          if (piece instanceof HomeFurnitureGroup) {
            visibleFurniture.addAll(getVisibleFurniture(((HomeFurnitureGroup)piece).getFurniture()));
          } else {
            visibleFurniture.add(piece);
          }
        }
      }
      return visibleFurniture;
    }

    private static java.awt.geom.Rectangle2D updateObjectsBounds(java.awt.geom.Rectangle2D objectBounds,
                                                                 Collection<? extends Selectable> items) {
      for (Selectable item : items) {
        if (!(item instanceof Elevatable)
            || ((Elevatable)item).getLevel() == null
            || ((Elevatable)item).getLevel().isViewableAndVisible()) {
          for (float [] point : item.getPoints()) {
            if (objectBounds == null) {
              objectBounds = new java.awt.geom.Rectangle2D.Float(point [0], point [1], 0, 0);
            } else {
              objectBounds.add(point [0], point [1]);
            }
          }
        }
      }
      return objectBounds;
    }
  }
}
