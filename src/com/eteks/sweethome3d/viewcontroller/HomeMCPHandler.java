/*
 * HomeMCPHandler.java 13 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor.CatalogMatch;

/**
 * Answers the JSON-RPC messages of the Model Context Protocol (MCP) for one
 * home, so an MCP client such as Claude can read the home, search the furniture
 * catalog and edit the home with the same undoable commands as the built-in
 * design assistant. This class only handles messages and is independent of
 * their transport; it reads and edits the home, so in the application its
 * methods must be called in the event dispatch thread.
 * @author Sweet Home 3D
 */
public class HomeMCPHandler {
  /** The most recent protocol revision this handler implements. */
  public static final String PROTOCOL_VERSION = "2025-06-18";
  private static final List<String> SUPPORTED_PROTOCOL_VERSIONS =
      Arrays.asList(PROTOCOL_VERSION, "2025-03-26", "2024-11-05");
  private static final int MAX_SEARCH_RESULTS = 12;
  private static final long MAX_COMMANDS_FILE_LENGTH = 20L * 1024 * 1024;

  private static final int PARSE_ERROR      = -32700;
  private static final int INVALID_REQUEST  = -32600;
  private static final int METHOD_NOT_FOUND = -32601;
  private static final int INVALID_PARAMS   = -32602;

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor commandExecutor;
  private final String                   serverVersion;
  private volatile boolean               editingAllowed = true;
  private volatile ImageProvider         imageProvider;
  // Tells this home from the other ones a client may have worked on
  private final String                   homeId = java.util.UUID.randomUUID().toString();
  private String                         expectedHomeId;

  /**
   * Puts two pictures in one for this handler, which can't draw any itself.
   */
  public interface ImageComposer {
    /**
     * Returns a PNG image showing <code>reference</code> (a photo) beside
     * <code>image</code>, or over it with the given opacity if <code>overlay</code>
     * is <code>true</code>, or <code>null</code> if one of them can't be read.
     */
    byte [] compose(byte [] image, byte [] reference, boolean overlay, float opacity);
  }

  private ImageComposer imageComposer;

  /**
   * Sets the object that puts a rendered picture and a photo in one picture.
   */
  public void setImageComposer(ImageComposer imageComposer) {
    this.imageComposer = imageComposer;
  }

  /**
   * Gives pictures of the home to this handler, which can't draw any itself.
   */
  public interface ImageProvider {
    /**
     * Returns a PNG image of the plan no larger than <code>maxSize</code> pixels,
     * or <code>null</code> if it can't be drawn.
     * @param level the level to draw, or <code>null</code> for the selected one
     * @param area  the part of the plan to draw as <code>{x, y, width, height}</code>
     *              in centimeters, or <code>null</code> for the whole plan
     */
    byte [] getPlanImage(int maxSize, Level level, float [] area);

    /**
     * Returns an image of the home in 3D, or <code>null</code> if it can't be rendered.
     */
    Image3D get3DImage(Image3DRequest request);

    /**
     * Returns a PNG image of the icon of the piece of the furniture catalog, or of the texture,
     * with the given name, or <code>null</code> if it doesn't exist.
     */
    byte [] getCatalogImage(String name, boolean texture);

    /**
     * Returns a description of the renderers which can create the images of the home.
     */
    String get3DInfo();
  }

  /**
   * What an image of the home in 3D should show and how it should be rendered.
   */
  public static class Image3DRequest {
    /** OpenGL when it can draw the image, otherwise the ray tracer. */
    public static final String AUTO = "auto";
    /** The OpenGL renderer: an image in a few seconds. */
    public static final String OPENGL = "opengl";
    /** The ray tracer: slower, with the light of the lamps and reflections. */
    public static final String RAYTRACE = "raytrace";

    private final int     width;
    private final int     height;
    private final String  renderer;
    private final boolean shadows;
    private final boolean ambientOcclusion;
    private final boolean highQuality;
    private final HomeViewpoints.Pose pose;
    private final Level   level;
    private final boolean allLevels;
    private final int     previewWidth;
    private final int     timeout;
    private Home          drawnHome;

    /**
     * Sets the home to draw in place of the home of the handler: one of its versions.
     */
    public void setDrawnHome(Home drawnHome) {
      this.drawnHome = drawnHome;
    }

    /**
     * Returns the home to draw in place of the home of the handler, or <code>null</code>.
     */
    public Home getDrawnHome() {
      return this.drawnHome;
    }

    public Image3DRequest(int width, int height, String renderer, boolean shadows, boolean ambientOcclusion,
                          boolean highQuality, HomeViewpoints.Pose pose, Level level, boolean allLevels,
                          int previewWidth) {
      this(width, height, renderer, shadows, ambientOcclusion, highQuality, pose, level, allLevels, previewWidth, 0);
    }

    public Image3DRequest(int width, int height, String renderer, boolean shadows, boolean ambientOcclusion,
                          boolean highQuality, HomeViewpoints.Pose pose, Level level, boolean allLevels,
                          int previewWidth, int timeout) {
      this.timeout = timeout;
      this.width = width;
      this.height = height;
      this.renderer = renderer;
      this.shadows = shadows;
      this.ambientOcclusion = ambientOcclusion;
      this.highQuality = highQuality;
      this.pose = pose;
      this.level = level;
      this.allLevels = allLevels;
      this.previewWidth = previewWidth;
    }

    public int getWidth() {
      return this.width;
    }

    public int getHeight() {
      return this.height;
    }

    /**
     * Returns {@link #AUTO}, {@link #OPENGL} or {@link #RAYTRACE}.
     */
    public String getRenderer() {
      return this.renderer;
    }

    /**
     * Returns <code>true</code> if the objects should cast shadows (OpenGL renderer).
     */
    public boolean isShadows() {
      return this.shadows;
    }

    /**
     * Returns <code>true</code> if the corners should be darkened (OpenGL renderer).
     */
    public boolean isAmbientOcclusion() {
      return this.ambientOcclusion;
    }

    /**
     * Returns <code>true</code> for the best quality of the ray tracer.
     */
    public boolean isHighQuality() {
      return this.highQuality;
    }

    /**
     * Returns the point of view, or <code>null</code> for the camera of the home.
     */
    public HomeViewpoints.Pose getPose() {
      return this.pose;
    }

    /**
     * Returns the level to select in the image, or <code>null</code> for the selected one.
     */
    public Level getLevel() {
      return this.level;
    }

    /**
     * Returns <code>true</code> if all the levels are shown, <code>false</code>
     * if the levels above the selected one are left out.
     */
    public boolean isAllLevels() {
      return this.allLevels;
    }

    /**
     * Returns the width of the smaller copy of the image to return with it, or 0 if none is needed.
     */
    public int getPreviewWidth() {
      return this.previewWidth;
    }

    /**
     * Returns the count of seconds after which the ray tracer should give up, or 0 for no limit.
     */
    public int getTimeout() {
      return this.timeout;
    }
  }

  /**
   * An image of the home in 3D.
   */
  public static class Image3D {
    private final byte [] image;
    private final byte [] preview;
    private final String  description;

    /**
     * @param image       the image in PNG format
     * @param preview     a smaller copy of the image in PNG format, or <code>null</code>
     * @param description how the image was rendered
     */
    public Image3D(byte [] image, byte [] preview, String description) {
      this.image = image;
      this.preview = preview;
      this.description = description;
    }

    public byte [] getImage() {
      return this.image;
    }

    public byte [] getPreview() {
      return this.preview;
    }

    public String getDescription() {
      return this.description;
    }
  }

  /**
   * Writes the home in files for this handler, which doesn't do it itself.
   */
  public interface SceneExporter {
    /**
     * Exports the home with a Blender scene script in <code>folder</code>
     * and returns the path of the script.
     */
    String exportForBlender(java.io.File folder) throws Exception;
  }

  private volatile SceneExporter sceneExporter;

  /**
   * What this handler asks the application to do with files and windows.
   */
  public interface HomeOperations {
    /**
     * Writes the home in the file or folder <code>target</code> in the given format
     * (pdf, svg, obj, csv, web_page or panorama) and returns what was written.
     */
    String export(String format, java.io.File target, Map<String, Object> options) throws Exception;

    /**
     * Makes the MCP server answer for <code>otherHome</code>, open in the application,
     * with the same permissions, and returns a sentence about it.
     */
    String serve(Home otherHome) throws Exception;

    /**
     * Starts a visit of the home in the browser of the user: <code>game</code> or <code>walk</code>.
     */
    String startVisit(String mode) throws Exception;
  }

  /**
   * What this handler asks the application to do with the window of the sun
   * and with the 3D view displayed on screen.
   */
  public interface SunStudyView {
    /**
     * Shows or closes the window "Sun and shadows" of the home.
     */
    void showWindow(boolean visible) throws Exception;

    /**
     * Plays in that window the <code>day</code> from sunrise to sunset or the days of the
     * <code>year</code>, or stops the animation if <code>animation</code> is <code>null</code>.
     */
    void play(String animation) throws Exception;

    /**
     * Chooses the renderer of the 3D view on screen and whether it shows shadows.
     * A <code>null</code> parameter leaves the choice as it is.
     */
    void setDisplay(Boolean openGL, Boolean shadows) throws Exception;

    /**
     * Returns a sentence about the window, its animation and the renderer of the 3D view.
     */
    String describe();

    /**
     * Returns a PNG image showing the given PNG images of the same size in a grid,
     * each with its caption under it, or <code>null</code> if they can't be read.
     */
    byte [] createContactSheet(byte [][] images, String [] captions, int columns);

    /**
     * Returns a PNG image which shows with false colors, from 0 to <code>maximumHours</code>, how long
     * the sun reaches each point of a picture. Each of the PNG images <code>masks</code> is red where the sun
     * reaches a surface at an instant, black where it doesn't and green where the sky shows; the instants
     * are <code>stepHours</code> apart. <code>shading</code> is the same picture with its usual colors.
     * The parts of the surfaces which get less than 2 hours, from 2 to 6 hours and more are stored
     * in <code>shares</code>. Returns <code>null</code> if the images can't be read.
     */
    byte [] createSunHoursImage(byte [][] masks, byte [] shading, double stepHours, double maximumHours,
                                String title, float [] shares);
  }

  private volatile SunStudyView sunStudyView;

  /**
   * Sets the object that shows the window of the sun and draws its pictures.
   */
  public void setSunStudyView(SunStudyView sunStudyView) {
    this.sunStudyView = sunStudyView;
  }

  /**
   * The instants of the pictures of a sun study.
   */
  private static class SunSweep {
    private long []   times;
    private String [] captions;
    private String    falseColors;
    private int       columns;
    private String    text;
    private boolean   hours;
    private double    stepHours;
    private double    maximumHours;
    private String    title;
  }

  /**
   * Draws lines and texts over a picture.
   */
  public interface ImageAnnotator {
    /**
     * Returns the PNG <code>image</code> with the given lines <code>{x1, y1, x2, y2}</code> and
     * texts drawn over it, in pixels. A text is centered on its place <code>{x, y}</code>.
     */
    byte [] annotate(byte [] image, float [][] lines, String [] texts, float [][] textPlaces);
  }

  private volatile ImageAnnotator imageAnnotator;

  /**
   * Draws shapes over a picture of the plan.
   */
  public interface PlanOverlayPainter {
    /**
     * Returns the PNG <code>image</code> with shapes drawn over it, in pixels. Each shape is
     * {kind, points, fill, stroke, stroke width, text}: the kind is <code>polygon</code>, <code>line</code>,
     * <code>circle</code> (its center then a point of its edge), <code>text</code> (centered on its point) or
     * <code>label</code> (starting at its point); the colors are ARGB or <code>null</code>.
     */
    byte [] paint(byte [] image, List<Object []> shapes);
  }

  private volatile PlanOverlayPainter planOverlayPainter;

  public void setPlanOverlayPainter(PlanOverlayPainter planOverlayPainter) {
    this.planOverlayPainter = planOverlayPainter;
  }

  /**
   * Sets the object that draws the dimensions of elevation pictures.
   */
  public void setImageAnnotator(ImageAnnotator imageAnnotator) {
    this.imageAnnotator = imageAnnotator;
  }

  private volatile HomeOperations homeOperations;
  private volatile boolean        savingAllowed;
  private HomeChangeJournal       changeJournal;

  /**
   * Sets the object that exports the home and switches between homes.
   */
  public void setHomeOperations(HomeOperations homeOperations) {
    this.homeOperations = homeOperations;
  }

  /**
   * Returns whether clients may save the home in its own file.
   */
  /**
   * Returns <code>true</code> if commands may ask web services for data about the place of the home.
   */
  public boolean isWebAccessAllowed() {
    return this.commandExecutor.isWebAccessAllowed();
  }

  public void setWebAccessAllowed(boolean webAccessAllowed) {
    this.commandExecutor.setWebAccessAllowed(webAccessAllowed);
  }

  /**
   * Sets what puts the tiles of a map together in one picture.
   */
  public void setTileComposer(AssistantGisCommands.TileComposer tileComposer) {
    this.commandExecutor.setTileComposer(tileComposer);
  }

  public boolean isSavingAllowed() {
    return this.savingAllowed && isEditingAllowed();
  }

  /**
   * Sets whether clients may save the home in its own file and replace files.
   * Without it they can only save versions and copies in new files.
   */
  public void setSavingAllowed(boolean savingAllowed) {
    this.savingAllowed = savingAllowed;
  }

  /**
   * Sets the object that exports the home for the <code>export_blender</code> tool.
   */
  public void setSceneExporter(SceneExporter sceneExporter) {
    this.sceneExporter = sceneExporter;
  }

  /**
   * Sets the object that measures the 3D models imported by <code>import_furniture</code>.
   */
  public void setModelSizeProvider(AssistantCommandExecutor.ModelSizeProvider modelSizeProvider) {
    this.commandExecutor.setModelSizeProvider(modelSizeProvider);
  }

  /**
   * Sets the object that reads the materials and the hinges of 3D models.
   */
  public void setModelInspector(AssistantCommandExecutor.ModelInspector modelInspector) {
    this.commandExecutor.setModelInspector(modelInspector);
  }

  /**
   * Sets the object that draws the pictures returned by <code>get_plan_image</code>.
   */
  public void setImageProvider(ImageProvider imageProvider) {
    this.imageProvider = imageProvider;
  }

  /**
   * Creates a handler for the given <code>home</code>.
   * @param homeController the controller used to edit the home, or
   *          <code>null</code> for a read-only handler
   * @param serverVersion  the version reported to clients
   */
  public HomeMCPHandler(Home home, UserPreferences preferences, HomeController homeController,
                        String serverVersion) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.commandExecutor = new AssistantCommandExecutor(home, preferences, homeController);
    this.serverVersion = serverVersion != null ? serverVersion : "";
    this.changeJournal = new HomeChangeJournal(home);
  }

  /**
   * Returns whether clients may modify the home.
   */
  public boolean isEditingAllowed() {
    return this.editingAllowed && this.homeController != null;
  }

  /**
   * Sets whether clients may modify the home. When <code>false</code>, only the
   * reading tools work.
   */
  public void setEditingAllowed(boolean editingAllowed) {
    this.editingAllowed = editingAllowed;
  }

  /**
   * Returns an identifier of the home of this handler, different for each home
   * and each time the application runs.
   */
  public String getHomeId() {
    return this.homeId;
  }

  /**
   * Handles a JSON-RPC message or batch sent by a client which last worked on the home
   * of the handler identified by <code>expectedHomeId</code>. If this handler is an other one,
   * the home of the client changed without a call of its own (the window of its home was
   * closed, or the application was started again): its tool calls aren't run, and are
   * answered with an error that tells which home it now works on, as the ids it knows
   * designate other items in this home.
   * @param expectedHomeId the {@linkplain #getHomeId() identifier} of the home the client
   *          expects, or <code>null</code> if it expects none
   */
  public String handle(String requestBody, String expectedHomeId) {
    this.expectedHomeId = expectedHomeId;
    try {
      return handle(requestBody);
    } finally {
      this.expectedHomeId = null;
    }
  }

  /**
   * Returns <code>true</code> if the given tool chooses the home of a connection or doesn't
   * depend on it: such a tool is run even when the home of the client changed without its knowing.
   */
  public static boolean isHomeIndependentTool(String name) {
    return "list_homes".equals(name) || isHomeChoiceTool(name)
        || "help".equals(name) || "get_capabilities".equals(name)
        || "list_catalog".equals(name) || "search_catalog".equals(name) || "search_textures".equals(name)
        || "get_catalog_image".equals(name);
  }

  /**
   * Returns <code>true</code> if the given tool makes a connection work on an other home.
   */
  public static boolean isHomeChoiceTool(String name) {
    return "open_home".equals(name) || "new_home".equals(name) || "switch_home".equals(name);
  }

  private String getHomeLabel() {
    return this.home.getName() != null ? "\"" + this.home.getName() + "\"" : "a home never saved";
  }

  /**
   * Handles a JSON-RPC message or batch and returns the JSON response, or
   * <code>null</code> if the message doesn't require any (notifications).
   */
  public String handle(String requestBody) {
    Object message;
    try {
      message = JSONParser.parse(requestBody);
    } catch (RuntimeException ex) {
      return JSONParser.toJSON(error(null, PARSE_ERROR, "Parse error"));
    }
    if (message instanceof List) {
      List<Object> responses = new ArrayList<Object>();
      for (Object item : (List<?>)message) {
        Map<String, Object> response = handleMessage(item);
        if (response != null) {
          responses.add(response);
        }
      }
      return responses.isEmpty() ? null : JSONParser.toJSON(responses);
    } else {
      Map<String, Object> response = handleMessage(message);
      return response != null ? JSONParser.toJSON(response) : null;
    }
  }

  /**
   * Answers a JSON-RPC message in place of a server that can't be reached: the
   * handshake and the list of tools are answered as the server would, so a client
   * started before Sweet Home 3D still knows the tools, and tool calls fail with
   * <code>reason</code>.
   * @return the JSON response, or <code>null</code> if the message doesn't require any
   */
  public static String handleOffline(String requestBody, String serverVersion, String reason) {
    Object message;
    try {
      message = JSONParser.parse(requestBody);
    } catch (RuntimeException ex) {
      return JSONParser.toJSON(error(null, PARSE_ERROR, "Parse error"));
    }
    if (message instanceof List) {
      List<Object> responses = new ArrayList<Object>();
      for (Object item : (List<?>)message) {
        Map<String, Object> response = handleOfflineMessage(item, serverVersion, reason);
        if (response != null) {
          responses.add(response);
        }
      }
      return responses.isEmpty() ? null : JSONParser.toJSON(responses);
    } else {
      Map<String, Object> response = handleOfflineMessage(message, serverVersion, reason);
      return response != null ? JSONParser.toJSON(response) : null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> handleOfflineMessage(Object message, String serverVersion, String reason) {
    if (!(message instanceof Map)) {
      return error(null, INVALID_REQUEST, "Invalid request");
    }
    Map<String, Object> request = (Map<String, Object>)message;
    Object id = request.get("id");
    Object method = request.get("method");
    if (!(method instanceof String) || !request.containsKey("id")) {
      return null;
    }
    if ("initialize".equals(method)) {
      return result(id, initialize(request.get("params") instanceof Map
          ? (Map<String, Object>)request.get("params")
          : new LinkedHashMap<String, Object>(), serverVersion != null ? serverVersion : ""));
    } else if ("ping".equals(method)) {
      return result(id, new LinkedHashMap<String, Object>());
    } else if ("tools/list".equals(method)) {
      Map<String, Object> tools = new LinkedHashMap<String, Object>();
      tools.put("tools", getTools());
      return result(id, tools);
    } else if ("tools/call".equals(method)) {
      return result(id, toolResult(reason, true));
    } else {
      return error(id, METHOD_NOT_FOUND, "Method not found: " + method);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> handleMessage(Object message) {
    if (!(message instanceof Map)) {
      return error(null, INVALID_REQUEST, "Invalid request");
    }
    Map<String, Object> request = (Map<String, Object>)message;
    Object id = request.get("id");
    Object method = request.get("method");
    if (!(method instanceof String)) {
      // A response to a request this server never sends, or a malformed message
      return request.containsKey("id") && !request.containsKey("result") && !request.containsKey("error")
          ? error(id, INVALID_REQUEST, "Invalid request")
          : null;
    }
    if (!request.containsKey("id")) {
      // Notifications (notifications/initialized, notifications/cancelled...) get no response
      return null;
    }
    Map<String, Object> params = request.get("params") instanceof Map
        ? (Map<String, Object>)request.get("params")
        : new LinkedHashMap<String, Object>();
    try {
      if ("initialize".equals(method)) {
        return result(id, initialize(params, this.serverVersion));
      } else if ("ping".equals(method)) {
        return result(id, new LinkedHashMap<String, Object>());
      } else if ("tools/list".equals(method)) {
        Map<String, Object> tools = new LinkedHashMap<String, Object>();
        tools.put("tools", getTools());
        return result(id, tools);
      } else if ("tools/call".equals(method)) {
        Object name = params.get("name");
        Map<String, Object> arguments = params.get("arguments") instanceof Map
            ? (Map<String, Object>)params.get("arguments")
            : new LinkedHashMap<String, Object>();
        if (!(name instanceof String) || !isTool((String)name)) {
          return error(id, INVALID_PARAMS, "Unknown tool: " + name);
        }
        String unknown = getUnknownArguments((String)name, arguments);
        if (unknown != null) {
          // Said rather than dropped in silence: a client that kept the tools of an older version sees it
          return result(id, toolResult(unknown, true));
        }
        if (this.expectedHomeId != null && !this.expectedHomeId.equals(this.homeId)
            && !isHomeIndependentTool((String)name)) {
          // Not run: the ids the client knows designate other items in this home
          return result(id, toolResult("Nothing was done: the home of this connection changed since your last call, "
              + "and not by a call of yours. The window of the home you were working on was closed, or Sweet Home 3D "
              + "was started again. This connection now reads and edits " + getHomeLabel() + ". "
              + "The ids you know belong to the other home and designate other items here: call get_home before "
              + "using any id, or list_homes, switch_home or open_home to work on another home. "
              + "Then repeat your call if it still applies.", true));
        }
        // A group of calls left open by a client that went away mustn't keep the changes of the user out of Undo
        this.commandExecutor.endIdleGroup(AssistantCommandExecutor.GROUP_IDLE_DELAY);
        // A plug-in may have registered a reader or a writer of images that fails
        com.eteks.sweethome3d.tools.ImageProviders.preferPlatformProviders();
        // What the tools change isn't a change made by the user by hand
        this.changeJournal.setPaused(true);
        try {
          Map<String, Object> toolResult;
          try {
            toolResult = callTool((String)name, arguments);
          } catch (LinkageError ex) {
            // Said to the client with its cause, rather than an answer without any reason
            toolResult = toolResult("The tool " + name + " failed in Sweet Home 3D: " + ex
                + ". A class or a library can't be loaded: it may be needed by a plug-in written for another version "
                + "of Sweet Home 3D (Help > About lists the plug-ins).", true);
          } catch (OutOfMemoryError ex) {
            toolResult = toolResult("The tool " + name + " failed in Sweet Home 3D: " + ex
                + ". There isn't enough memory left: ask for a smaller picture or a smaller part of the home, "
                + "or close the other homes.", true);
          } catch (StackOverflowError ex) {
            toolResult = toolResult("The tool " + name + " failed in Sweet Home 3D: " + ex + ".", true);
          }
          int handChanges = this.changeJournal.getChangeCount();
          if (handChanges > 0 && toolResult.get("content") instanceof List
              && ("apply_commands".equals(name) || "get_home".equals(name) || "check_home".equals(name)
                  || "undo".equals(name) || "redo".equals(name))) {
            ((List<Object>)toolResult.get("content")).add(textContent("Note: the user changed " + handChanges
                + (handChanges == 1 ? " item" : " items") + " by hand since your last get_changes: call get_changes to see which."));
          }
          return result(id, toolResult);
        } finally {
          this.changeJournal.setPaused(false);
        }
      } else {
        return error(id, METHOD_NOT_FOUND, "Method not found: " + method);
      }
    } catch (RuntimeException ex) {
      return error(id, -32603, "Internal error: " + ex);
    }
  }

  /**
   * Returns a message about the arguments of a call that the tool doesn't have, or <code>null</code>.
   */
  @SuppressWarnings("unchecked")
  static String getUnknownArguments(String toolName, Map<String, Object> arguments) {
    for (Object tool : getTools()) {
      Map<String, Object> description = (Map<String, Object>)tool;
      if (toolName.equals(description.get("name"))) {
        Map<String, Object> properties = (Map<String, Object>)((Map<String, Object>)description.get("inputSchema")).get("properties");
        List<String> unknown = new ArrayList<String>();
        for (String argument : arguments.keySet()) {
          if (!properties.containsKey(argument) && !argument.startsWith("_")) {
            unknown.add(argument);
          }
        }
        if (!unknown.isEmpty()) {
          return "The tool " + toolName + " has no argument " + unknown + ". Its arguments are "
              + new ArrayList<String>(properties.keySet()) + ". If these aren't the arguments you know, your list of "
              + "tools is the one of an other version of Sweet Home 3D: read the tools again (tools/list), or start "
              + "a new session.";
        }
      }
    }
    return null;
  }

  private static Map<String, Object> initialize(Map<String, Object> params, String serverVersion) {
    Object requestedVersion = params.get("protocolVersion");
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("protocolVersion", SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)
        ? requestedVersion : PROTOCOL_VERSION);
    Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
    // The program that links a client to this server tells it when the tools of an other version replace these
    Map<String, Object> toolsCapability = new LinkedHashMap<String, Object>();
    toolsCapability.put("listChanged", Boolean.TRUE);
    capabilities.put("tools", toolsCapability);
    result.put("capabilities", capabilities);
    Map<String, Object> serverInfo = new LinkedHashMap<String, Object>();
    serverInfo.put("name", "sweethome3d");
    serverInfo.put("title", "Sweet Home 3D");
    serverInfo.put("version", serverVersion);
    result.put("serverInfo", serverInfo);
    result.put("instructions",
        "Tools to read and edit the home open in Sweet Home 3D, an interior design application. "
        + "The help tool lists every command of apply_commands by area (rooms and walls, levels and stairs, furniture, roofs, "
        + "ground and site, fences and gates, garden, lights and camera, checks and schedules), gives the full syntax of one "
        + "and searches a word: use it rather than reading the files of the program. get_capabilities gives the version and the limits. "
        + "Call get_home first: it describes the levels, rooms, walls and furniture with the ids "
        + "(F# furniture, W# walls, R# rooms, L# levels, P# lines) used by apply_commands. An id belongs to its item for good: "
        + "it doesn't change when other items are added or deleted, the reply of a command names the ids of what it added, "
        + "and an id whose item was deleted is reported as an error. In a large home get_home takes the filters level, type and name. "
        + "To model a house without helper levels: add_roof with \"points\" builds one roof over any polygon with a hip or a gable "
        + "at each edge (add_dormer, fit_walls_to_roof), add_slab a raised, sunken or sloped slab, set_terrain the ground, "
        + "modify_wall, modify_level, delete_level and merge_levels change walls and levels, "
        + "modify_roof, modify_dormer and remove_dormer change a roof without building it again, modify_room with "
        + "\"ceiling\":\"follow_roof\" lines a room under the slopes and headroom measures it, add_polyline, add_property_line, "
        + "add_setback, set_compass, set_georeference and import_parcel draw the site and place the home on the Earth, "
        + "every reply lists the ids of what was created, apply_commands takes a \"file\" of commands, begin_group and end_group "
        + "make several calls one step of Undo, set_plan_visibility shows a roof "
        + "as a dashed outline in the plans of the levels above, describe_item tells everything about one item, "
        + "and \"where\" designates the items of a level, a tag, a name, a type or a box in any command. "
        + "Furniture can only be added by its exact catalog name: use search_catalog or "
        + "list_catalog to find one. To extend the house use add_rectangular_room (room and walls "
        + "at once) and auto_roof; modify_room renames or reshapes a room and hides its ceiling or its floor "
        + "(a ceiling under a sloped roof or over a terrace shows as a flat slab otherwise); check_home reports plan defects and fix_home repairs them; "
        + "get_plan_image shows the plan (a level, or a part of it enlarged) and get_3d_image the home in 3D: "
        + "it takes a \"view\" (top, south, north_east... of the whole home or of a \"target\" room or piece, or inside a room) "
        + "so you can look at your work from several sides without moving the camera of the user, "
        + "\"levels\":\"selected\" to see a floor without the roof and the floors above, and renders with OpenGL in a few seconds "
        + "or with the ray tracer, much slower; get_3d_info tells the camera, the stored points of view and the renderers; "
        + "get_sun_study_image shows the shadows of the sun at several hours of a day or dates of a year in one picture, "
        + "and sun_study opens the window of the sun of the user, plays its animations and chooses the renderer of the 3D view on screen; "
        + "get_catalog_image shows the icons of catalog pieces and textures to choose between search results; add_stairs joins two levels (straight, L or U shaped, with handrails); move_to_level moves items to another level; set_background_image displays the picture of a floor plan behind the plan to trace it (look at it with get_plan_image); get_report gives areas and quantities; add_picture, import_furniture and import_texture bring pictures, 3D model files and texture images of this computer into the home; export_blender prepares a high quality render. "
        + "When no catalog piece fits what is asked, make one: create_furniture builds a piece from simple parts "
        + "(boxes, cylinders, cones, spheres, each with its size, place and color), which you should then look at with "
        + "get_3d_image and correct; import_furniture brings a 3D model file you wrote or were given. "
        + "add_to_catalog (or \"catalog\" in create_furniture) keeps a piece in the user's furniture catalog for other homes, "
        + "and export_furniture_library writes a set of pieces as a .sh3f furniture library file to share. "
        + "Furniture can carry tags (add_tags, remove_tags, set_tags), and a layer - the pieces of a tag or of a type - "
        + "is hidden, shown or selected at once with hide_layer, show_layer and select_layer. "
        + "add_room_shape draws L, T and U shaped rooms, apply_style decorates rooms in a named style "
        + "and replace_furniture swaps a piece for another one in place. "
        + "The user may have left review notes while visiting the home (get_notes): requests pinned to a wall, "
        + "a piece of furniture or a room; do them and mark each one with update_note. "
        + "Every apply_commands call is one step of the application's "
        + "Undo, also available through the undo tool (\"steps\":N for several; get_history lists them). "
        + "Before a large change, save_version keeps a checkpoint that compare_versions and restore_version use; "
        + "save_home writes the file of the home only if the user allowed saving, and otherwise a copy in a new file (copy_to), "
        + "which open_home opens to work on it. get_changes tells what the user changed by hand between your calls. "
        + "export_home writes the home as PDF, SVG, OBJ, CSV, a 3D web page or a panorama, and get_elevation_image draws a side "
        + "of the building square on with its dimensions. Other commands of apply_commands: group, align, distribute, mirror "
        + "and measure (distances, the free space around a piece, what is at a point); set_light, set_sun, set_environment, "
        + "open_door and set_material (one material of a model); add_hedge, add_path, add_planting_bed and add_trees "
        + "for the garden; add_fence builds fences, garden walls and barriers in 63 styles with their gates (list_fence_styles), "
        + "along points, a lot line or between two places, and add_gate, modify_fence, extend_fence, join_fences, split_fence, "
        + "trim_fence, offset_fence, convert_to_fence and fence_report change and count them; add_tree and add_shrub build trees and shrubs from "
        + "a species (list_species) with seasons, add_trees with \"trees\", import_trees, update_trees and forest_fill plant many, "
        + "set_season, grow_trees and relabel_trees change them all; set_terrain also reads a raster or a file of heights, "
        + "add_pad, grade_path, add_berm, add_swale and flatten_area grade the ground, cut_fill_report, contours, spot_elevations "
        + "and slope_map measure it; add_deck, add_patio, add_pergola, add_shed, add_pool, add_driveway, add_retaining_wall, "
        + "add_utility and the other outdoor commands build what stands around a house, add_context_building the houses of neighbors; "
        + "import_geojson, import_kml, import_shapefile, import_dxf and import_osm draw GIS files in the plan, and with the "
        + "permission of the user fetch_elevation, import_osm, import_parcels and set_basemap ask web services (web_sources); furnish_room places the furniture of a kind of room by rules, as a starting point; "
        + "schedule, add_symbol, add_symbols and framing_estimate list doors, windows and rooms, add electrical and plumbing "
        + "symbols and estimate framing; set_unit_prices also takes a room or a wall.");
    return result;
  }

  private static boolean isTool(String name) {
    return "get_home".equals(name) || "list_catalog".equals(name) || "search_catalog".equals(name)
        || "search_textures".equals(name) || "check_home".equals(name) || "get_report".equals(name) || "get_plan_image".equals(name)
        || "get_3d_image".equals(name) || "get_3d_info".equals(name) || "get_catalog_image".equals(name)
        || "help".equals(name) || "get_capabilities".equals(name)
        || "export_blender".equals(name) || "get_sun_study_image".equals(name) || "sun_study".equals(name)
        || "get_notes".equals(name) || "update_note".equals(name)
        || "apply_commands".equals(name) || "undo".equals(name) || "redo".equals(name)
        || "save_version".equals(name) || "list_versions".equals(name) || "compare_versions".equals(name)
        || "restore_version".equals(name) || "save_home".equals(name) || "list_homes".equals(name)
        || "open_home".equals(name) || "new_home".equals(name) || "switch_home".equals(name)
        || "export_home".equals(name) || "get_history".equals(name) || "get_changes".equals(name)
        || "add_note".equals(name) || "start_visit".equals(name) || "get_elevation_image".equals(name);
  }

  /**
   * Returns the description of the tools of this server.
   */
  private static List<Object> getTools() {
    List<Object> tools = new ArrayList<Object>();
    Map<String, Object> homeProperties = new LinkedHashMap<String, Object>();
    homeProperties.put("level", property("string",
        "Only the items of the level with this id (L#), or of every level with this name"));
    homeProperties.put("type", property("string",
        "Only these items: rooms, walls, furniture (with doors and windows) or doors_and_windows; several separated by commas"));
    homeProperties.put("name", property("string",
        "Only the rooms and the furniture whose name contains this text, whatever the case; several texts "
        + "separated by | match any of them, like \"Pine|Oak\""));
    Map<String, Object> whereProperty = property("object",
        "A short list of the items matching every criterion given, as in apply_commands: "
        + "{\"level\":\"<name or L#>\",\"tag\":\"<tag>\",\"name\":\"<text, or Pine|Oak>\","
        + "\"type\":\"furniture|doors_and_windows|lights|stairs|groups|all_furniture|walls|rooms|polylines\","
        + "\"bbox\":[x1,y1,x2,y2]} (bbox in cm: what is inside that box). Also \"name_regex\":\"^Pine \\\\d+$\" (a regular expression, "
        + "whatever the case) and \"created_by\":\"agent|user\" (what commands created, or what was drawn by hand). "
        + "One line per item, up to 500; \"offset\" and \"limit\" read a longer list in pages");
    homeProperties.put("where", whereProperty);
    Map<String, Object> fieldsProperty = property("array",
        "With or without where: one short line per item with only these fields, among name, position, size, level, "
        + "elevation, angle, tags, type, area, points, color (default: name, position, size, level). The id is always given");
    fieldsProperty.put("items", property("string", "A field"));
    homeProperties.put("fields", fieldsProperty);
    homeProperties.put("offset", property("number", "With where or fields: skips this number of items, to read a long list in pages"));
    homeProperties.put("limit", property("number", "With where or fields: the number of items listed (500 at most)"));
    tools.add(tool("get_home", "Describe home",
        "Returns a text description of the home open in Sweet Home 3D: levels, rooms with their "
        + "areas, walls, furniture with positions and sizes, and the current selection. Each "
        + "item has an id (F# furniture, W# walls, R# rooms, L# levels) to use with apply_commands, which it keeps "
        + "whatever is added or deleted. Each level is listed with its height, its floor thickness and the count of "
        + "its rooms, walls and furniture, EMPTY if it has none. "
        + "Lengths in the description are in meters; command coordinates are in centimeters. "
        + "A room whose ceiling or floor isn't drawn is marked \"ceiling hidden\" or \"floor hidden\". "
        + "In a large home, the optional filters return a part of it with the same ids: without filter, "
        + "only the first 80 pieces of furniture are listed.",
        schema(homeProperties), true));
    tools.add(tool("list_catalog", "List furniture catalog",
        "Lists the categories and item names of the furniture catalog. Only these names can be "
        + "added to the home.",
        schema(null), true));
    Map<String, Object> searchProperties = new LinkedHashMap<String, Object>();
    searchProperties.put("query", property("string", "Words describing the wanted item, for example \"office chair\""));
    searchProperties.put("doors_and_windows", property("boolean",
        "true to search doors and windows rather than furniture (default false)"));
    tools.add(tool("search_catalog", "Search furniture catalog",
        "Searches the furniture catalog and returns the best matching exact names with their "
        + "category and size in centimeters.",
        schema(searchProperties, "query"), true));
    Map<String, Object> textureProperties = new LinkedHashMap<String, Object>();
    textureProperties.put("query", property("string", "Words describing the wanted texture, for example \"oak parquet\""));
    tools.add(tool("search_textures", "Search textures catalog",
        "Searches the textures catalog and returns the best matching exact names, to use with the "
        + "set_texture command on floors, ceilings, walls and furniture.",
        schema(textureProperties, "query"), true));
    Map<String, Object> checkProperties = new LinkedHashMap<String, Object>();
    Map<String, Object> ignoreProperty = property("array",
        "Ids of items whose issues aren't reported, like [\"F516\",\"W3\"]");
    ignoreProperty.put("items", property("string", "An id"));
    checkProperties.put("ignore", ignoreProperty);
    Map<String, Object> ignoreTagsProperty = property("array",
        "Tags of furniture whose issues aren't reported, like [\"marker\"] for survey markers that stand outside rooms on purpose");
    ignoreTagsProperty.put("items", property("string", "A tag"));
    checkProperties.put("ignore_tags", ignoreTagsProperty);
    checkProperties.put("min_severity", enumProperty("The least serious issues to report (default info: all)",
        "info", "warning", "error"));
    checkProperties.put("remember", property("boolean",
        "true to ignore these ids and tags in the next checks of this home too (kept with the home)"));
    checkProperties.put("forget", property("boolean", "true to stop ignoring what was remembered"));
    tools.add(tool("check_home", "Check home",
        "Looks for defects in the plan: walls that end close to each other without being joined, "
        + "duplicated or empty items, doors and windows that aren't in a wall, furniture under the "
        + "floor, through the ceiling or outside every room, heavy 3D models. Each issue names its "
        + "items by id; those marked fixable are repaired by the fix_home command of apply_commands. "
        + "Doors and windows higher than the roof above them and walls beyond a setback or a property line are errors. "
        + "Each issue is marked error, warning or info, the errors first. "
        + "Like get_home, it binds the ids to the current items.",
        schema(checkProperties), true));
    tools.add(tool("get_report", "Quantities of the home",
        "Returns the quantities of the home: floor area and perimeter of each room by level, length "
        + "and surface of the walls, count of the furniture by name with prices when they have one. "
        + "Use it to estimate flooring, paint or a budget.",
        schema(null), true));
    Map<String, Object> notesProperties = new LinkedHashMap<String, Object>();
    notesProperties.put("all", property("boolean", "true to include the notes already marked as done (default: open notes only)"));
    tools.add(tool("get_notes", "Review notes of the user",
        "Returns the notes the user wrote while visiting the home in the walk-through or the game mode "
        + "(for example \"add an outlet here\" on a wall, or \"fix this model\" on a piece of furniture). "
        + "Each note gives its id, its text, the item it is pinned to by id (W# wall, F# furniture, R# room), "
        + "and the point clicked: x and y in the centimeters of the plan and the height above the floor. "
        + "These are the user's requests: do what each one asks with apply_commands, then mark it with update_note. "
        + "Like get_home, it binds the ids to the current items.",
        schema(notesProperties), true));
    Map<String, Object> noteProperties = new LinkedHashMap<String, Object>();
    noteProperties.put("id", property("string", "The id of the note, as given by get_notes (n1, n2...)"));
    noteProperties.put("status", property("string", "done, open, or deleted to remove the note"));
    noteProperties.put("reply", property("string", "What was done about the note, or why it can't be done, for the user to read"));
    tools.add(tool("update_note", "Update a review note",
        "Marks a review note as done once what it asks was done (or open again), with a short reply the "
        + "user reads beside the note, or deletes it.",
        schema(noteProperties, "id"), false));
    Map<String, Object> imageProperties = new LinkedHashMap<String, Object>();
    imageProperties.put("max_size", property("number", "Largest side of the image in pixels (default 1000, at most 2000)"));
    Map<String, Object> layersProperty = property("array", "Drawn over this picture only: slope (the ground colored by its slope), "
        + "contours (contour lines every contour_interval cm), trees (the crowns of the generated trees with their species), "
        + "fences (the fences colored by style). Lot lines, setbacks, photos and the basemap are a part of the plan itself");
    layersProperty.put("items", property("string", "slope, contours, trees or fences"));
    imageProperties.put("layers", layersProperty);
    imageProperties.put("contour_interval", property("number", "With the layer contours: the height between two lines in cm"));
    imageProperties.put("legend", property("boolean", "true draws what the colors of the layers mean"));
    imageProperties.put("scale_bar", property("boolean", "true draws a scale bar at the bottom left"));
    imageProperties.put("north_arrow", property("boolean", "true draws an arrow to the north of the compass at the top right"));
    Map<String, Object> annotateProperty = property("array", "Marks drawn in this picture only, not kept in the home: "
        + "{\"text\":\"...\",\"x\":N,\"y\":N}, {\"marker\":[x,y],\"text\":\"...\"} or {\"line\":[[x,y],[x,y]],\"text\":\"...\"}, in cm of the plan");
    annotateProperty.put("items", property("object", "A mark"));
    imageProperties.put("annotate", annotateProperty);
    imageProperties.put("level", property("string", "Name of the level to draw (default: the selected level)"));
    Map<String, Object> areaProperty = property("object",
        "Part of the plan to draw, enlarged, in centimeters: {\"x\":N,\"y\":N,\"width\":N,\"height\":N} "
        + "(x, y = its top left corner). Default: the whole plan");
    imageProperties.put("area", areaProperty);
    imageProperties.put("target", property("string",
        "Instead of area: the id of a room, a piece or a wall (R1, F3...) to draw with what is around it, "
        + "or \"building\" for the walls of the home without the garden"));
    tools.add(tool("get_plan_image", "Picture of the plan",
        "Returns a PNG picture of the 2D plan of a level (walls, rooms, furniture, "
        + "dimensions), to see the layout or check the result of an edit. x grows to the right "
        + "and y downward, as in the commands. In a large home, ask for an \"area\" or a \"target\" "
        + "to read the details.",
        schema(imageProperties), true));
    Map<String, Object> image3DProperties = new LinkedHashMap<String, Object>();
    image3DProperties.put("width", property("number", "Width of the image in pixels (default 800, at most 1600, or 4096 with save_to)"));
    image3DProperties.put("height", property("number", "Height of the image in pixels (default 500, at most 1200, or 4096 with save_to)"));
    image3DProperties.put("view", enumProperty(
        "Point of view, used for this picture only (the camera of the user doesn't move). "
        + "current (default): the camera of the home. top, south, north, east, west, south_east, south_west, "
        + "north_east, north_west: the whole home, or the target, entirely in sight from that side "
        + "(south = the bottom of the plan). inside: standing in a corner of the target room, "
        + "or in front of the target piece of furniture or wall",
        "current", "top", "south", "north", "east", "west", "south_east", "south_west", "north_east", "north_west", "inside"));
    image3DProperties.put("target", property("string",
        "With view: the id of the room, piece of furniture or wall to look at (R1, F3, W2), "
        + "several ids separated by commas, \"selection\", or \"building\" for the walls of the home "
        + "without the garden around it. Default: the whole home"));
    image3DProperties.put("point_of_view", property("string",
        "Instead of view: the name of a point of view stored in the home (see get_3d_info)"));
    image3DProperties.put("camera", property("object",
        "Instead of view: a camera for this picture only, in centimeters: {\"x\":N,\"y\":N,\"height\":N,"
        + "\"look_at_x\":N,\"look_at_y\":N,\"look_at_height\":N}; heights are above the floor of the level. "
        + "Or its place and its angles: {\"x\":N,\"y\":N,\"z\":N,\"yaw\":N,\"pitch\":N,\"fov\":N} in degrees, yaw 0 looking down "
        + "the plan and 180 up the plan, pitch positive looking down, z from the elevation 0"));
    Map<String, Object> tagsProperty = property("array", "Leaves out of this picture the furniture with one of these tags, "
        + "like [\"tree\",\"shrub\"] to see a house past its trees. The home isn't changed");
    tagsProperty.put("items", property("string", "A tag"));
    image3DProperties.put("exclude_tags", tagsProperty);
    Map<String, Object> includeProperty = property("array", "Leaves out of this picture the furniture that has none of these "
        + "tags; the ground, roofs, slabs, fences and walls stay");
    includeProperty.put("items", property("string", "A tag"));
    image3DProperties.put("include_tags", includeProperty);
    image3DProperties.put("occluders", enumProperty("With \"view\" and \"target\", the trees and the furniture the camera "
        + "stands in or that are on its line to the target are left out of the picture and listed (hide, the default); "
        + "keep draws them", "hide", "keep"));
    Map<String, Object> hideProperty = property("array", "Leaves out of this picture the items with these ids (F#, W#, R#)");
    hideProperty.put("items", property("string", "An id"));
    image3DProperties.put("hide_ids", hideProperty);
    image3DProperties.put("xray", property("boolean", "true draws the trees and the shrubs built by add_tree and add_shrub pale "
        + "and seen through, to see what is behind them"));
    image3DProperties.put("season", enumProperty("Draws the generated trees and shrubs in this season for this picture, "
        + "without changing the home", "spring", "summer", "autumn", "winter"));
    image3DProperties.put("date", property("string", "Lights this picture by the sun of a date, YYYY-MM-DD, without changing the home"));
    image3DProperties.put("time", property("string", "Lights this picture by the sun of a local time, HH:MM"));
    image3DProperties.put("cutaway", property("number", "Leaves out the furniture, the roofs and the slabs whose bottom is at "
        + "this height in cm or above it (from the elevation 0). Walls aren't cut"));
    image3DProperties.put("photo", property("string", "The id of a photo placed by import_photos, like PH1: the picture is drawn "
        + "from where the photo was taken, looking the same way, beside the photo"));
    image3DProperties.put("fov", property("number", "Field of view in degrees, from 20 to 120 (default: 63 outside, 85 inside)"));
    image3DProperties.put("levels", enumProperty(
        "all (default): every level and the roof. selected: the selected level and the ones under it only, "
        + "to look into a floor from above", "all", "selected"));
    image3DProperties.put("level", property("string", "Name of the level to select for this picture (with levels selected, camera and inside views)"));
    image3DProperties.put("renderer", enumProperty(
        "auto (default): OpenGL when the graphics card can draw the picture, otherwise the ray tracer. "
        + "opengl: drawn by the graphics card in a few seconds, the best choice to check your work. "
        + "raytrace: much slower (a minute or more in a large home), with reflections",
        Image3DRequest.AUTO, Image3DRequest.OPENGL, Image3DRequest.RAYTRACE));
    image3DProperties.put("shadows", property("boolean", "OpenGL renderer: objects cast shadows (default true)"));
    image3DProperties.put("ambient_occlusion", property("boolean", "OpenGL renderer: corners are darkened (default true)"));
    image3DProperties.put("quality", enumProperty(
        "Ray tracer: low (default, seconds) or high (soft shadows and reflections, minutes; at most 1000 x 750 pixels)",
        "low", "high"));
    image3DProperties.put("timeout", property("number",
        "Ray tracer: seconds after which it gives up and an error is returned (default: no limit, at most 3600)"));
    image3DProperties.put("store_as", property("string",
        "Name under which the view of this picture is kept in the points of view of the home, to draw the same view "
        + "again after changes with point_of_view (before / after pictures). Needs the permission to change things"));
    image3DProperties.put("compare_with", property("string",
        "Absolute path of a photo (PNG or JPEG): the picture returned shows the home at the left and the photo at the right, "
        + "to check roof lines and heights against it"));
    image3DProperties.put("compare_mode", enumProperty(
        "side_by_side (default), or overlay to draw the photo over the picture, stretched to its size", "side_by_side", "overlay"));
    image3DProperties.put("compare_opacity", property("number", "Opacity of the photo drawn over the picture, from 0.05 to 0.95 (default 0.5)"));
    image3DProperties.put("save_to", property("string",
        "Absolute path of a .png file to write the picture to, at up to 4096 pixels; a smaller copy is returned. "
        + "Needs the permission to change things"));
    image3DProperties.put("replace", property("boolean", "true to overwrite the file of save_to if it exists (default false)"));
    tools.add(tool("get_3d_image", "Picture of the home in 3D",
        "Renders a PNG picture of the home in 3D. By default from its current camera with all levels "
        + "and the roof; \"view\" and \"target\" choose an other point of view for this picture without moving "
        + "the camera of the user (the set_camera command moves it). Typical uses: {\"view\":\"south_east\"} for "
        + "the whole home, {\"view\":\"top\",\"levels\":\"selected\"} for a floor seen from above without what is over it, "
        + "{\"view\":\"inside\",\"target\":\"R1\"} for a room, {\"view\":\"south_east\",\"target\":\"building\"} for the house without its garden, "
        + "{\"view\":\"inside\",\"target\":\"F3\"} for a piece seen from its room. Walls and ceilings hide what is indoors "
        + "from the outside views: for a room or a piece indoors use inside, or top with \"levels\":\"selected\". "
        + "The answer says which renderer drew the picture and from where.",
        schema(image3DProperties), true));
    tools.add(tool("get_3d_info", "3D view and renderers",
        "Returns the camera of the home (place, direction, field of view), the points of view stored in the home, "
        + "the levels, the views get_3d_image accepts, and the renderers available for its pictures with their options.",
        schema(null), true));
    Map<String, Object> helpProperties = new LinkedHashMap<String, Object>();
    helpProperties.put("command", property("string", "The name of a command of apply_commands, like add_fence: its syntax, "
        + "its parameters and the commands related to it"));
    helpProperties.put("search", property("string", "A word: the commands and the parameters that mention it"));
    helpProperties.put("style", property("string", "With \"command\":\"add_fence\": a style of fence, for its options"));
    tools.add(tool("help", "Help of the commands",
        "Without argument, lists every command of apply_commands by area with a line on each. \"command\" gives the full syntax "
        + "of one command, \"search\" finds a word in all of them. Use it instead of guessing a command or a parameter.",
        schema(helpProperties), true));
    tools.add(tool("get_capabilities", "Version, features and limits",
        "Returns the version of Sweet Home 3D, what this version has (styles of fences, types of gates, coordinate systems, "
        + "formats read and written, what isn't available) and its limits.",
        schema(null), true));
    Map<String, Object> sunImageProperties = new LinkedHashMap<String, Object>();
    sunImageProperties.put("sweep", enumProperty(
        "day (default): the hours of one day, from sunrise to sunset. year: dates of the year at the same local time. "
        + "hours: one picture colored by the number of hours the sun reaches each place during the day",
        "day", "year", "hours"));
    sunImageProperties.put("steps", property("number",
        "Number of pictures: from 2 to 12 for a day (default 6); 2, 3, 4, 6 or 12 for a year (default 4: "
        + "the equinoxes and the solstices; 12: the 21st of each month). Hours: the instants counted, from 4 to 96 "
        + "(default: one each 20 minutes)"));
    sunImageProperties.put("max_hours", property("number", "Hours sweep: the hours shown by the last color (default: the length of the day)"));
    sunImageProperties.put("date", property("string", "The day shown, or the year of a year sweep, as YYYY-MM-DD (default: the date of the sun of the home)"));
    sunImageProperties.put("time", property("string", "Year sweep: the local time shown, as HH:MM (default: the time of the sun of the home)"));
    sunImageProperties.put("from", property("string", "Day and hours sweeps: local time of the first picture, as HH:MM (default: sunrise)"));
    sunImageProperties.put("to", property("string", "Day and hours sweeps: local time of the last picture, as HH:MM (default: sunset)"));
    Map<String, Object> falseColorsProperty = property("string",
        "true to color the pictures by the light the surfaces receive, a number of lux for the light shown by the last color, "
        + "false for the real colors (default: as the 3D view of the home)");
    // A boolean or a number
    falseColorsProperty.remove("type");
    sunImageProperties.put("false_colors", falseColorsProperty);
    for (Map.Entry<String, Object> imageProperty : image3DProperties.entrySet()) {
      String propertyName = imageProperty.getKey();
      if (!"store_as".equals(propertyName) && !propertyName.startsWith("compare_")) {
        sunImageProperties.put(propertyName, imageProperty.getValue());
      }
    }
    sunImageProperties.put("width", property("number", "Width of each picture in pixels, from 160 to 800 (default 400); hours: up to 1600 (default 800)"));
    sunImageProperties.put("height", property("number", "Height of each picture in pixels, from 120 to 600 (default 260); hours: up to 1200 (default 520)"));
    tools.add(tool("get_sun_study_image", "Pictures of the sun through a day or a year",
        "Renders one PNG showing the home in 3D at several hours of a day, or at several dates of a year, each lit and shadowed "
        + "from where the sun is at the latitude, longitude, time zone and north of the compass (set_compass), with its date, "
        + "time and sun height written under it: where the shadows of trees and buildings fall, hour after hour or season "
        + "after season. The date and time of the home, and its 3D view, don't change (the set_sun command changes them). "
        + "The view is chosen as in get_3d_image: {\"sweep\":\"day\",\"date\":\"2026-06-21\",\"view\":\"top\"}, "
        + "{\"sweep\":\"year\",\"time\":\"12:00\",\"view\":\"south_east\",\"false_colors\":true}. "
        + "{\"sweep\":\"hours\",\"date\":\"2026-06-21\",\"view\":\"top\"} returns instead one picture colored by the hours of direct sun "
        + "each place gets that day, to choose where to plant, sit or put a pool, with the share of the surfaces under 2 hours and over 6. "
        + "The answer lists the height and azimuth of the sun, the length of shadows and the light on the ground of each picture.",
        schema(sunImageProperties), true));
    Map<String, Object> sunStudyProperties = new LinkedHashMap<String, Object>();
    sunStudyProperties.put("window", enumProperty("open or close the window \"Sun and shadows\" of the user", "open", "close"));
    sunStudyProperties.put("play", enumProperty(
        "day: animates the 3D view of the user from sunrise to sunset of the current date; year: day after day of the year "
        + "at the current time; stop: stops the animation. An animation runs until it is stopped", "day", "year", "stop"));
    sunStudyProperties.put("renderer", enumProperty(
        "The renderer of the 3D view on screen: opengl shows shadows and false colors, java3d doesn't. "
        + "It is a preference of the user kept for all homes", "opengl", "java3d"));
    sunStudyProperties.put("shadows", property("boolean", "Whether the 3D view on screen shows shadows (OpenGL renderer; a preference of the user)"));
    tools.add(tool("sun_study", "Window of the sun and 3D view on screen",
        "Controls what the user sees of the sun on screen: opens or closes the window \"Sun and shadows\" (place, calendar, "
        + "sliders of the day and of the year, false colors), plays or stops its animations of the day and of the year, and "
        + "chooses the renderer and the shadows of the 3D view. Without arguments, tells their state. The date, the time, "
        + "the false colors and the light from the sun are set by the set_sun command, the latitude, longitude, time zone "
        + "and north by set_compass; the pictures of get_3d_image and get_sun_study_image don't depend on this tool. "
        + "Only when the user asks to see or to animate the sun in the application.",
        schema(sunStudyProperties), false));
    Map<String, Object> catalogImageProperties = new LinkedHashMap<String, Object>();
    Map<String, Object> namesProperty = property("array", "Exact names of catalog pieces (or of textures), at most 8");
    namesProperty.put("items", property("string", "A name given by search_catalog, list_catalog or search_textures"));
    catalogImageProperties.put("names", namesProperty);
    catalogImageProperties.put("textures", property("boolean", "true if the names are textures (default false: furniture, doors and windows)"));
    tools.add(tool("get_catalog_image", "Pictures of catalog items",
        "Returns the pictures of pieces of the furniture catalog, or of textures, to see what they look like "
        + "and choose between the results of search_catalog or search_textures before adding one.",
        schema(catalogImageProperties, "names"), true));
    Map<String, Object> blenderProperties = new LinkedHashMap<String, Object>();
    blenderProperties.put("folder", property("string", "Absolute path of the folder to write to (created if needed)"));
    tools.add(tool("export_blender", "Export for Blender",
        "Exports the home for a high quality render in Blender: writes the 3D model (scene.obj with "
        + "its materials and textures) and render_blender.py in the folder, and returns the command "
        + "to run. The script sets up the model at real size, the camera of the home, the sun, "
        + "the lamps, glass materials and Cycles. Needs the permission to change things, as it writes files.",
        schema(blenderProperties, "folder"), false));
    Map<String, Object> commandsProperty = new LinkedHashMap<String, Object>();
    commandsProperty.put("type", "array");
    commandsProperty.put("description", "The commands to run in order, as objects with an \"action\" field");
    Map<String, Object> commandItems = new LinkedHashMap<String, Object>();
    commandItems.put("type", "object");
    commandsProperty.put("items", commandItems);
    Map<String, Object> commandsProperties = new LinkedHashMap<String, Object>();
    commandsProperties.put("commands", commandsProperty);
    commandsProperties.put("file", property("string",
        "Instead of commands: the absolute path of a JSON file holding the array of commands (or an object with a "
        + "\"commands\" array), for long lists written by a script; up to "
        + AssistantCommandExecutor.MAX_COMMANDS_PER_FILE + " commands, run as one undoable change"));
    commandsProperties.put("home", property("string",
        "Optional safeguard: a part of the file name of the home these commands are meant for, or \"unsaved\" for a home "
        + "never saved. When the home of this connection is an other one, nothing is run and the answer tells it"));
    commandsProperties.put("stop_on_error", property("boolean",
        "true stops at the first command that fails and tells which ones weren't run. By default a command that fails "
        + "doesn't stop the ones after it, and the answer names it by its number"));
    tools.add(tool("apply_commands", "Edit home",
        "Edits the home open in Sweet Home 3D by running a list of commands as a single "
        + "undoable change, and returns a summary of what was applied or skipped. "
        + HomeAssistantContext.getCommandReference(),
        schema(commandsProperties), false));
    Map<String, Object> elevationProperties = new LinkedHashMap<String, Object>();
    elevationProperties.put("side", enumProperty("The side the building is seen from", "south", "north", "east", "west"));
    elevationProperties.put("width", property("number", "Width of the image in pixels (default 1200, at most 2000)"));
    elevationProperties.put("height", property("number", "Height of the image in pixels (default 700, at most 1500)"));
    elevationProperties.put("dimensions", property("boolean", "Draws the width of the building and the heights of its levels and of its top (default true)"));
    elevationProperties.put("save_to", property("string", "Absolute path of a .png file to write the picture to"));
    elevationProperties.put("replace", property("boolean", "true to replace the file of save_to if it exists"));
    tools.add(tool("get_elevation_image", "Elevation picture",
        "Returns an elevation of the building: a picture of one of its sides drawn square on from the real 3D "
        + "models (roofs, dormers, doors, windows, textures), without perspective to within 2 %, with the width "
        + "of the building and the heights of its levels and of its top in meters. The export_elevation command "
        + "writes a schematic line drawing in SVG instead.",
        schema(elevationProperties, "side"), true));
    Map<String, Object> stepsProperties = new LinkedHashMap<String, Object>();
    stepsProperties.put("steps", property("number", "How many changes (default 1, at most 50); get_history lists them"));
    tools.add(tool("undo", "Undo", "Undoes the last change made to the home, or the last \"steps\" changes.",
        schema(stepsProperties), false));
    tools.add(tool("redo", "Redo", "Redoes the last undone change, or the last \"steps\" undone changes.",
        schema(stepsProperties), false));
    tools.add(tool("get_history", "Undo history",
        "Lists the changes that undo and redo would revert or apply again, the most recent first, "
        + "made by you or by the user.", schema(null), true));
    tools.add(tool("get_changes", "Changes made by hand",
        "Lists the items the user added, deleted or modified by hand in Sweet Home 3D since you last called "
        + "this tool, with their ids. Other answers end with a note when there are such changes.",
        schema(null), true));
    Map<String, Object> versionProperties = new LinkedHashMap<String, Object>();
    versionProperties.put("label", property("string", "A few words that describe this state of the home, e.g. \"before moving the kitchen\" "
        + "(\"name\" is read as the label too)"));
    versionProperties.put("name", property("string", "The label, under another name"));
    tools.add(tool("save_version", "Save a version",
        "Saves a copy of the home as a named version in the folder of versions beside its file "
        + "(File > Versions... in Sweet Home 3D). The home and its file don't change. Do it before a large "
        + "change, to be able to compare or to come back. The home must have been saved in a file once by the user.",
        schema(versionProperties), false));
    tools.add(tool("list_versions", "List versions", "Lists the versions saved for this home, the most recent first.",
        schema(null), true));
    Map<String, Object> compareProperties = new LinkedHashMap<String, Object>();
    compareProperties.put("version", property("string", "The version: its number in list_versions (1 = the most recent) or a part of its label"));
    compareProperties.put("with", property("string", "Another version to compare it with (default: the home as it is now)"));
    compareProperties.put("picture", property("boolean", "true to add a picture: the version at the left, the other state at the right"));
    compareProperties.put("view", property("string", "The view of that picture, as in get_3d_image (default south_east)"));
    tools.add(tool("compare_versions", "Compare versions",
        "Says what changed from a version to the home as it is now, or to another version: counts and areas, "
        + "then the items added, removed, moved or resized with their ids. Optionally with a side by side picture.",
        schema(compareProperties, "version"), true));
    Map<String, Object> restoreProperties = new LinkedHashMap<String, Object>();
    restoreProperties.put("version", property("string", "The version: its number in list_versions or a part of its label"));
    restoreProperties.put("keep_current", property("boolean", "Saves the current state as a version first (default true)"));
    tools.add(tool("restore_version", "Restore a version",
        "Replaces the levels and items of the home by the ones of a version, as one change that undo reverts. "
        + "The file of the home isn't written. The ids of the items are the ones they had in that version.",
        schema(restoreProperties, "version"), false));
    Map<String, Object> saveProperties = new LinkedHashMap<String, Object>();
    saveProperties.put("copy_to", property("string", "Absolute path of a .sh3d file to write a copy to, instead of saving the home in its own file"));
    saveProperties.put("replace", property("boolean", "With copy_to: true to replace a file that exists (needs the permission to save)"));
    tools.add(tool("save_home", "Save the home",
        "Saves the home in its own file, which needs the permission \"Allow saving\" of the user in Sweet Home 3D "
        + "(Tools > Claude MCP server...). With copy_to, writes a copy in a new file instead, which only needs the "
        + "permission to change things: the home keeps its file and its unsaved state. Prefer save_version for checkpoints.",
        schema(saveProperties), false));
    tools.add(tool("list_homes", "List open homes",
        "Lists the homes open in Sweet Home 3D with their files, and which one this connection reads and edits.",
        schema(null), true));
    Map<String, Object> openProperties = new LinkedHashMap<String, Object>();
    openProperties.put("path", property("string", "Absolute path of the .sh3d file"));
    tools.add(tool("open_home", "Open a home",
        "Opens a .sh3d file in a new window of Sweet Home 3D and makes this connection read and edit it "
        + "(the home open before stays open). To work on a copy: save_home with copy_to, then open_home on the copy.",
        schema(openProperties, "path"), false));
    tools.add(tool("new_home", "New home",
        "Creates an empty home in a new window of Sweet Home 3D and makes this connection read and edit it.",
        schema(null), false));
    Map<String, Object> switchProperties = new LinkedHashMap<String, Object>();
    switchProperties.put("home", property("string", "The number of the home in list_homes, or a part of its file name"));
    tools.add(tool("switch_home", "Switch home",
        "Makes this connection read and edit another home already open in Sweet Home 3D.",
        schema(switchProperties, "home"), false));
    Map<String, Object> exportProperties = new LinkedHashMap<String, Object>();
    exportProperties.put("format", enumProperty("pdf: the printed pages (plan, furniture list, 3D view); svg: the plan as a drawing; "
        + "obj: the 3D model with its materials; csv: the furniture list with the id, the level, the place, the elevation, "
        + "the angle and the size of each piece, in cm unless \"units\" is given; web_page: a folder with the 3D visit for a browser; "
        + "panorama: a 360 degree picture from the place of the virtual visitor, with its page",
        "pdf", "svg", "obj", "glb", "csv", "web_page", "panorama"));
    exportProperties.put("path", property("string", "Absolute path of the file to write (pdf, svg, obj, csv), or of an existing folder that will receive a new folder (web_page, panorama)"));
    exportProperties.put("replace", property("boolean", "true to replace a file that exists"));
    Map<String, Object> columnsProperty = property("array", "csv: the columns to write, among " + HomeFurnitureCsvWriter.COLUMNS
        + " (default: " + HomeFurnitureCsvWriter.DEFAULT_COLUMNS + ")");
    columnsProperty.put("items", property("string", "A column"));
    exportProperties.put("columns", columnsProperty);
    exportProperties.put("units", property("string", "csv: the unit of the lengths, cm (default), m, mm, in or ft; "
        + "the header tells it, as in x_cm"));
    exportProperties.put("width", property("number", "panorama: width of the picture in pixels (default 4096, from 1024 to 8192)"));
    tools.add(tool("export_home", "Export the home",
        "Writes the home in another format. Pictures are made by get_3d_image and get_plan_image (save_to), "
        + "elevation drawings by the export_elevation command, a Blender scene by export_blender.",
        schema(exportProperties, "format", "path"), false));
    Map<String, Object> addNoteProperties = new LinkedHashMap<String, Object>();
    addNoteProperties.put("text", property("string", "The note, for the user to read"));
    addNoteProperties.put("id", property("string", "The item the note is pinned to (F#, W#, R#); or give x and y"));
    addNoteProperties.put("x", property("number", "Abscissa of the place of the note in cm"));
    addNoteProperties.put("y", property("number", "Ordinate of the place of the note in cm"));
    addNoteProperties.put("height", property("number", "Height of the note above the floor of its level in cm (default 120)"));
    tools.add(tool("add_note", "Add a review note",
        "Pins a review note to an item or a place of the home, which the user reads in the walk-through, the game "
        + "mode and Tools > Review notes...: a question, something to check on site, why something was done.",
        schema(addNoteProperties, "text"), false));
    Map<String, Object> visitProperties = new LinkedHashMap<String, Object>();
    visitProperties.put("mode", enumProperty("game: walk, jump and fly as in a video game; walk: the walk-through for a screen or a VR headset",
        "game", "walk"));
    tools.add(tool("start_visit", "Start a visit",
        "Opens the visit of the home in the web browser of the user, from the place of the virtual visitor "
        + "(set_camera with mode visitor moves it). Only when the user asks to see or visit the home.",
        schema(visitProperties), false));
    return tools;
  }

  private static Map<String, Object> tool(String name, String title, String description,
                                          Map<String, Object> inputSchema, boolean readOnly) {
    Map<String, Object> tool = new LinkedHashMap<String, Object>();
    tool.put("name", name);
    tool.put("title", title);
    tool.put("description", description);
    tool.put("inputSchema", inputSchema);
    Map<String, Object> annotations = new LinkedHashMap<String, Object>();
    annotations.put("title", title);
    annotations.put("readOnlyHint", Boolean.valueOf(readOnly));
    annotations.put("openWorldHint", Boolean.FALSE);
    tool.put("annotations", annotations);
    return tool;
  }

  private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties != null ? properties : new LinkedHashMap<String, Object>());
    if (required.length > 0) {
      schema.put("required", new ArrayList<Object>(Arrays.asList(required)));
    }
    return schema;
  }

  /**
   * Returns the texts of a list, or of a text with commas.
   */
  private static List<String> strings(Object value) {
    List<String> texts = new ArrayList<String>();
    if (value instanceof List) {
      for (Object item : (List<?>)value) {
        if (item != null) {
          texts.add(item.toString().trim());
        }
      }
    } else if (value instanceof String) {
      for (String item : ((String)value).split(",")) {
        if (item.trim().length() > 0) {
          texts.add(item.trim());
        }
      }
    }
    return texts;
  }

  private static Map<String, Object> enumProperty(String description, String... values) {
    Map<String, Object> property = property("string", description);
    property.put("enum", new ArrayList<Object>(Arrays.asList(values)));
    return property;
  }

  private static Map<String, Object> property(String type, String description) {
    Map<String, Object> property = new LinkedHashMap<String, Object>();
    property.put("type", type);
    property.put("description", description);
    return property;
  }

  /**
   * Runs the tool <code>name</code> and returns its MCP result.
   */
  private Map<String, Object> callTool(String name, Map<String, Object> arguments) {
    if ("get_home".equals(name) && (arguments.get("where") instanceof Map || arguments.get("fields") != null)) {
      // A short list of the items of an area, a level, a tag...
      Map<String, Object> where = arguments.get("where") instanceof Map
          ? new LinkedHashMap<String, Object>((Map<String, Object>)arguments.get("where")) : new LinkedHashMap<String, Object>();
      for (String criterion : new String [] {"level", "type", "name"}) {
        if (arguments.get(criterion) != null && !where.containsKey(criterion)) {
          where.put(criterion, arguments.get(criterion));
        }
      }
      List<String> fields = new ArrayList<String>();
      if (arguments.get("fields") instanceof List) {
        for (Object field : (List<?>)arguments.get("fields")) {
          fields.add(String.valueOf(field));
        }
      } else if (arguments.get("fields") instanceof String) {
        fields.addAll(Arrays.asList(((String)arguments.get("fields")).split("[,\\s]+")));
      }
      this.commandExecutor.snapshotDescribedItems();
      return toolResult(this.commandExecutor.describeItems(where, fields,
          arguments.get("offset") instanceof Number ? ((Number)arguments.get("offset")).intValue() : 0,
          arguments.get("limit") instanceof Number ? ((Number)arguments.get("limit")).intValue() : 500), false);
    } else if ("get_home".equals(name)) {
      String levelName = string(arguments, "level");
      if (levelName != null && findLevel(levelName) == null) {
        return toolResult("No level is named \"" + levelName + "\". Levels: " + getLevelNames() + ".", true);
      }
      List<String> types = new ArrayList<String>();
      Object typeValue = arguments.get("type");
      if (typeValue instanceof List) {
        for (Object type : (List<?>)typeValue) {
          types.add(String.valueOf(type));
        }
      } else if (typeValue instanceof String) {
        types.addAll(Arrays.asList(((String)typeValue).split("[,\\s]+")));
      }
      for (int i = types.size() - 1; i >= 0; i--) {
        String type = types.get(i).trim().toLowerCase();
        type = "room".equals(type) ? "rooms" : "wall".equals(type) ? "walls"
            : "doors".equals(type) || "windows".equals(type) || "doors_windows".equals(type) ? "doors_and_windows" : type;
        if (type.length() == 0) {
          types.remove(i);
        } else if (!"rooms".equals(type) && !"walls".equals(type) && !"furniture".equals(type)
                   && !"doors_and_windows".equals(type)) {
          return toolResult("\"type\" must be rooms, walls, furniture or doors_and_windows.", true);
        } else {
          types.set(i, type);
        }
      }
      String description = HomeAssistantContext.describeHome(this.home, levelName, types, string(arguments, "name"));
      // The ids of this description stay valid for the following commands
      this.commandExecutor.snapshotDescribedItems();
      return toolResult(description
          + (isEditingAllowed() ? "" : "\nThis home is read-only: editing is disabled in Sweet Home 3D."),
          false);
    } else if ("list_catalog".equals(name)) {
      String catalog = HomeAssistantContext.describeCatalog(this.preferences);
      return toolResult(catalog.length() > 0 ? catalog : "The furniture catalog is empty.", false);
    } else if ("search_catalog".equals(name)) {
      return searchCatalog(arguments);
    } else if ("get_3d_image".equals(name)) {
      return get3DImage(arguments);
    } else if ("help".equals(name)) {
      return toolResult(AssistantHelp.getHelp(arguments.get("command") != null ? arguments.get("command").toString() : null,
          arguments.get("search") != null ? arguments.get("search").toString() : null,
          arguments.get("style") != null ? arguments.get("style").toString() : null), false);
    } else if ("get_capabilities".equals(name)) {
      return toolResult((this.serverVersion.length() > 0 ? "MCP server version " + this.serverVersion + ". " : "")
          + AssistantHelp.getCapabilities(), false);
    } else if ("get_3d_info".equals(name)) {
      return toolResult(get3DInfo(), false);
    } else if ("get_sun_study_image".equals(name)) {
      return getSunStudyImage(arguments);
    } else if ("sun_study".equals(name)) {
      return sunStudy(arguments);
    } else if ("get_catalog_image".equals(name)) {
      return getCatalogImages(arguments);
    } else if ("search_textures".equals(name)) {
      Object query = arguments.get("query");
      if (!(query instanceof String) || ((String)query).trim().length() == 0) {
        return toolResult("The \"query\" argument is required.", true);
      }
      return toolResult("Textures: " + this.commandExecutor.searchTextures((String)query, MAX_SEARCH_RESULTS), false);
    } else if ("check_home".equals(name)) {
      this.commandExecutor.snapshotDescribedItems();
      return toolResult(this.commandExecutor.checkHome(arguments), false);
    } else if ("get_report".equals(name)) {
      return toolResult(this.commandExecutor.reportHome(), false);
    } else if ("get_notes".equals(name)) {
      this.commandExecutor.snapshotDescribedItems();
      return toolResult(HomeReviewNotes.describe(this.home, !Boolean.TRUE.equals(arguments.get("all")))
          + (AssistantPhotoCommands.getPhotos(this.home).isEmpty() ? "" : "\n" + AssistantPhotoCommands.describePhotos(this.home)), false);
    } else if ("get_plan_image".equals(name)) {
      return getPlanImage(arguments);
    } else if ("get_elevation_image".equals(name)) {
      return getElevationImage(arguments);
    } else if ("get_history".equals(name)) {
      return toolResult(describeHistory(), false);
    } else if ("get_changes".equals(name)) {
      this.commandExecutor.snapshotDescribedItems();
      return toolResult(this.changeJournal.describe(true), false);
    } else if ("list_versions".equals(name)) {
      return toolResult(HomeVersions.describe(this.home), false);
    } else if ("compare_versions".equals(name)) {
      return compareVersions(arguments);
    } else if ("list_homes".equals(name)) {
      return toolResult(describeHomes(), false);
    } else if (!isEditingAllowed()) {
      return toolResult("Editing is disabled: the user didn't allow changes to this home. "
          + "Ask them to enable it in Sweet Home 3D (Tools > Claude MCP server).", true);
    } else if ("apply_commands".equals(name)) {
      return applyCommands(arguments);
    } else if ("update_note".equals(name)) {
      Object id = arguments.get("id");
      if (!(id instanceof String)) {
        return toolResult("The \"id\" argument is required.", true);
      }
      String status = arguments.get("status") instanceof String ? ((String)arguments.get("status")).trim().toLowerCase() : null;
      String reply = arguments.get("reply") instanceof String ? (String)arguments.get("reply") : null;
      if (!HomeReviewNotes.updateNote(this.home, ((String)id).trim(), status, reply)) {
        return toolResult("This home has no note \"" + id + "\".", true);
      }
      return toolResult("Note " + id + " updated; " + HomeReviewNotes.getOpenCount(this.home) + " still open.", false);
    } else if ("export_blender".equals(name)) {
      SceneExporter exporter = this.sceneExporter;
      Object folder = arguments.get("folder");
      if (exporter == null) {
        return toolResult("The home can't be exported here.", true);
      } else if (!(folder instanceof String) || !new java.io.File((String)folder).isAbsolute()) {
        return toolResult("The \"folder\" argument must be an absolute path.", true);
      }
      try {
        String script = exporter.exportForBlender(new java.io.File((String)folder));
        return toolResult("Exported the home to " + folder + ". Render it with:\n  blender -b -P \""
            + script + "\"\nThe picture is written to render.png in that folder (options after \"--\": "
            + "--samples N --width N --height N --output FILE --gpu --save FILE.blend).", false);
      } catch (Exception ex) {
        return toolResult("Couldn't export the home: " + ex.getMessage(), true);
      }
    } else if ("save_version".equals(name) || "restore_version".equals(name) || "save_home".equals(name)
               || "open_home".equals(name) || "new_home".equals(name) || "switch_home".equals(name)
               || "export_home".equals(name) || "add_note".equals(name) || "start_visit".equals(name)) {
      try {
        return callFileTool(name, arguments);
      } catch (Exception ex) {
        return toolResult("Couldn't run " + name + ": " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()), true);
      }
    } else if ("undo".equals(name) || "redo".equals(name)) {
      boolean undo = "undo".equals(name);
      int steps = (int)Math.max(1, Math.min(50, number(arguments, "steps", 1)));
      boolean groupEnded = undo && this.commandExecutor.endGroup();
      List<String> names = undo ? this.homeController.getUndoHistory(steps) : this.homeController.getRedoHistory(steps);
      int done = 0;
      try {
        for (; done < steps; done++) {
          if (undo) {
            this.homeController.undo();
          } else {
            this.homeController.redo();
          }
        }
      } catch (RuntimeException ex) {
        // CannotUndoException or CannotRedoException: nothing left
      }
      if (done == 0) {
        return toolResult(undo ? "Nothing to undo." : "Nothing to redo.", true);
      } else if (steps == 1) {
        return toolResult(undo ? (groupEnded ? "Ended the group of calls and undid it as one step." : "Undone.") : "Redone.", false);
      }
      StringBuilder text = new StringBuilder((undo ? "Undid " : "Redid ") + done + (done == 1 ? " change" : " changes")
          + (done < steps ? " (there were no more)" : "") + ":");
      for (int i = 0; i < done && i < names.size(); i++) {
        text.append("\n- ").append(names.get(i));
      }
      return toolResult(text.toString(), false);
    } else {
      return toolResult("Unknown tool: " + name, true);
    }
  }

  private static double number(Map<String, Object> arguments, String name, double defaultValue) {
    Object value = arguments.get(name);
    if (value instanceof Number) {
      double number = ((Number)value).doubleValue();
      return Double.isNaN(number) || Double.isInfinite(number) ? defaultValue : number;
    } else if (value instanceof String) {
      try {
        return Double.parseDouble(((String)value).trim());
      } catch (NumberFormatException ex) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private static String string(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    return value instanceof String && ((String)value).trim().length() > 0
        ? ((String)value).trim()
        : null;
  }

  private static boolean bool(Map<String, Object> arguments, String name, boolean defaultValue) {
    Object value = arguments.get(name);
    if (value instanceof Boolean) {
      return ((Boolean)value).booleanValue();
    } else if (value instanceof String) {
      return "true".equalsIgnoreCase(((String)value).trim());
    }
    return defaultValue;
  }

  /**
   * Returns the level of the home with the given name, or <code>null</code>.
   */
  private Level findLevel(String name) {
    // An id L#, or the first level with this name
    HomeItemIds.ensureIds(this.home);
    List<Level> levels = HomeItemIds.findLevels(this.home, name);
    return levels.isEmpty() ? null : levels.get(0);
  }

  private String getLevelNames() {
    StringBuilder names = new StringBuilder();
    for (Level level : this.home.getLevels()) {
      names.append(names.length() > 0 ? ", " : "").append(HomeItemIds.getId(level)).append(" \"")
          .append(level.getName()).append('"');
    }
    return names.length() > 0 ? names.toString() : "none, this home has a single floor";
  }

  /**
   * Returns the pieces that hide the targets from a point of view: the ones the camera is in, and the
   * ones on the line from the camera to the targets. The ground, the structures and the targets aren't returned.
   */
  private List<HomePieceOfFurniture> getOccluders(HomeViewpoints.Pose pose, List<Selectable> targets) {
    List<HomePieceOfFurniture> occluders = new ArrayList<HomePieceOfFurniture>();
    // The center of the targets and their size
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (Selectable target : targets) {
      for (float [] point : target.getPoints()) {
        minX = Math.min(minX, point [0]);
        minY = Math.min(minY, point [1]);
        maxX = Math.max(maxX, point [0]);
        maxY = Math.max(maxY, point [1]);
      }
    }
    if (minX > maxX) {
      return occluders;
    }
    float centerX = (minX + maxX) / 2;
    float centerY = (minY + maxY) / 2;
    double radius = Math.hypot(maxX - minX, maxY - minY) / 2;
    double distance = Math.hypot(centerX - pose.getX(), centerY - pose.getY());
    // The line of sight stops where the targets start
    double sightLength = Math.max(0, distance - radius);
    double directionX = distance > 1 ? (centerX - pose.getX()) / distance : 0;
    double directionY = distance > 1 ? (centerY - pose.getY()) / distance : 0;
    double slope = Math.tan(pose.getPitch());
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (!piece.isVisible() || targets.contains(piece) || HomeTerrain.isTerrain(piece)
          || piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) != null
          || piece.getProperty("assistantFence") != null
          || piece.getProperty("assistantOutdoor") != null && piece.getProperty("assistantOutdoor").startsWith("{")) {
        continue;
      }
      float bottom = piece.getElevation() + (piece.getLevel() != null ? piece.getLevel().getElevation() : 0);
      float top = bottom + piece.getHeight();
      double pieceRadius = Math.max(piece.getWidth(), piece.getDepth()) / 2 + 30;
      // Where the line of sight passes the closest to the piece
      double along = (piece.getX() - pose.getX()) * directionX + (piece.getY() - pose.getY()) * directionY;
      along = Math.max(0, Math.min(sightLength, along));
      double sightX = pose.getX() + directionX * along;
      double sightY = pose.getY() + directionY * along;
      double sightZ = pose.getZ() - slope * along;
      if (Math.hypot(piece.getX() - sightX, piece.getY() - sightY) <= pieceRadius
          && sightZ >= bottom - 30 && sightZ <= top + 30
          && Math.hypot(piece.getX() - centerX, piece.getY() - centerY) > radius) {
        occluders.add(piece);
      }
    }
    return occluders;
  }

  /**
   * Returns the items designated by a <code>target</code> argument: ids separated by commas or "selection".
   */
  private List<Selectable> resolveTarget(String target) {
    if ("building".equalsIgnoreCase(target) || "walls".equalsIgnoreCase(target)) {
      // The walls of the home, without the garden around it
      return new ArrayList<Selectable>(this.home.getWalls());
    }
    Map<String, Object> fields = new LinkedHashMap<String, Object>();
    List<Object> ids = new ArrayList<Object>();
    for (String id : target.split("[,;\\s]+")) {
      if (id.length() > 0) {
        ids.add(id);
      }
    }
    fields.put("ids", ids);
    return this.commandExecutor.resolveTargets(new AssistantCommand("target", fields));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getPlanImage(Map<String, Object> arguments) {
    ImageProvider provider = this.imageProvider;
    int maxSize = (int)Math.max(200, Math.min(2000, number(arguments, "max_size", 1000)));
    Level level = null;
    String levelName = string(arguments, "level");
    if (levelName != null) {
      level = findLevel(levelName);
      if (level == null) {
        return toolResult("No level is named \"" + levelName + "\". Levels: " + getLevelNames() + ".", true);
      }
    }
    float [] area = null;
    String target = string(arguments, "target");
    if (arguments.get("area") instanceof Map) {
      Map<String, Object> areaArguments = (Map<String, Object>)arguments.get("area");
      double width = number(areaArguments, "width", 0);
      double height = number(areaArguments, "height", 0);
      double x = number(areaArguments, "x", Double.NaN);
      double y = number(areaArguments, "y", Double.NaN);
      if (width < 10 || height < 10 || Double.isNaN(x) || Double.isNaN(y)
          || Math.abs(x) > 1E6 || Math.abs(y) > 1E6 || width > 1E6 || height > 1E6) {
        return toolResult("\"area\" needs x, y, width and height in centimeters, width and height of 10 at least.", true);
      }
      area = new float [] {(float)x, (float)y, (float)width, (float)height};
    } else if (target != null) {
      List<Selectable> targets = resolveTarget(target);
      float [] bounds = targets.isEmpty() ? null : HomeViewpoints.getBounds(this.home, targets, null);
      if (bounds == null) {
        return toolResult("No item matches the target \"" + target + "\" (ids are the ones of the last get_home).", true);
      }
      // The items with a margin around them
      float margin = Math.max(100, Math.max(bounds [3] - bounds [0], bounds [4] - bounds [1]) * 0.15f);
      area = new float [] {bounds [0] - margin, bounds [1] - margin,
          bounds [3] - bounds [0] + 2 * margin, bounds [4] - bounds [1] + 2 * margin};
      if (level == null) {
        level = getLevel(targets.get(0));
      }
    }
    List<String> layers = strings(arguments.get("layers"));
    boolean overlays = !layers.isEmpty() || bool(arguments, "legend", false) || bool(arguments, "scale_bar", false)
        || bool(arguments, "north_arrow", false) || arguments.get("annotate") instanceof List;
    if (overlays && area == null) {
      // The whole home, to know where each point of the plan is in the picture
      float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      List<float [][]> shapes = new ArrayList<float [][]>();
      for (Wall wall : this.home.getWalls()) {
        shapes.add(wall.getPoints());
      }
      for (Room room : this.home.getRooms()) {
        shapes.add(room.getPoints());
      }
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (piece.isVisible()) {
          shapes.add(piece.getPoints());
        }
      }
      for (com.eteks.sweethome3d.model.Polyline polyline : this.home.getPolylines()) {
        shapes.add(polyline.getPoints());
      }
      for (float [][] shape : shapes) {
        for (float [] point : shape) {
          bounds [0] = Math.min(bounds [0], point [0]);
          bounds [1] = Math.min(bounds [1], point [1]);
          bounds [2] = Math.max(bounds [2], point [0]);
          bounds [3] = Math.max(bounds [3], point [1]);
        }
      }
      if (bounds [0] < bounds [2] && bounds [1] < bounds [3]) {
        float margin = Math.max(100, Math.max(bounds [2] - bounds [0], bounds [3] - bounds [1]) * 0.05f);
        area = new float [] {bounds [0] - margin, bounds [1] - margin, bounds [2] - bounds [0] + 2 * margin,
                             bounds [3] - bounds [1] + 2 * margin};
      }
    }
    byte [] image = provider != null ? provider.getPlanImage(maxSize, level, area) : null;
    if (image == null) {
      return toolResult("No picture of the plan is available (the plan is empty or not displayed).", true);
    }
    String overlayText = "";
    if (overlays && area != null) {
      PlanOverlayPainter painter = this.planOverlayPainter;
      int [] size = AssistantGeneratedModel.getImageSize(image);
      if (painter == null || size == null) {
        overlayText = " The layers can't be drawn here.";
      } else {
        List<String> legend = new ArrayList<String>();
        byte [] painted = painter.paint(image, getPlanOverlay(arguments, layers, area, size, legend));
        if (painted != null) {
          image = painted;
          overlayText = " Drawn over it, in this picture only: " + (legend.isEmpty() ? "the marks asked" : legend) + ".";
        }
      }
    }
    Map<String, Object> result = imageResult(image);
    if (overlayText.length() > 0 && area == null) {
      ((List<Object>)result.get("content")).add(textContent(overlayText.trim()));
    }
    if (area != null) {
      ((List<Object>)result.get("content")).add(textContent("Plan from x " + Math.round(area [0]) + ", y " + Math.round(area [1])
          + " (top left) to x " + Math.round(area [0] + area [2]) + ", y " + Math.round(area [1] + area [3]) + " cm"
          + (level != null ? ", level \"" + level.getName() + "\"" : "") + "." + overlayText));
    }
    return result;
  }

  /**
   * Returns the shapes of the layers asked for a picture of the plan, in pixels.
   * @param legend filled with what the layers show
   */
  private List<Object []> getPlanOverlay(Map<String, Object> arguments, List<String> layers, float [] area, int [] size, List<String> legend) {
    List<Object []> shapes = new ArrayList<Object []>();
    final float scale = size [0] / area [2];
    final float left = area [0];
    final float top = area [1];
    List<Object []> legendEntries = new ArrayList<Object []>();
    List<float [][]> triangles = layers.contains("slope") || layers.contains("contours") ? HomeTerrain.getTriangles(this.home)
        : new ArrayList<float [][]>();
    if (layers.contains("slope")) {
      float [] limits = {2, 5, 8.33f, 15, 25, Float.MAX_VALUE};
      int [] colors = {0x6FA8DC, 0x93C47D, 0xFFD966, 0xF6B26B, 0xE06666, 0x990000};
      String [] names = {"under 2 %", "2 to 5 %", "5 to 8.33 %", "8.33 to 15 %", "15 to 25 %", "over 25 %"};
      boolean [] used = new boolean [limits.length];
      for (float [][] triangle : triangles) {
        double ux = triangle [1][0] - triangle [0][0];
        double uy = triangle [1][1] - triangle [0][1];
        double uz = triangle [1][2] - triangle [0][2];
        double vx = triangle [2][0] - triangle [0][0];
        double vy = triangle [2][1] - triangle [0][1];
        double vz = triangle [2][2] - triangle [0][2];
        double nz = ux * vy - uy * vx;
        if (Math.abs(nz) < 1E-6) {
          continue;
        }
        double slope = Math.hypot(uy * vz - uz * vy, uz * vx - ux * vz) / Math.abs(nz) * 100;
        int index = 0;
        while (slope >= limits [index]) {
          index++;
        }
        used [index] = true;
        shapes.add(new Object [] {"polygon", new float [] {(triangle [0][0] - left) * scale, (triangle [0][1] - top) * scale,
            (triangle [1][0] - left) * scale, (triangle [1][1] - top) * scale, (triangle [2][0] - left) * scale,
            (triangle [2][1] - top) * scale}, Integer.valueOf(0x80000000 | colors [index]), null, null, null});
      }
      for (int i = 0; i < limits.length; i++) {
        if (used [i]) {
          legendEntries.add(new Object [] {Integer.valueOf(0xC0000000 | colors [i]), "slope " + names [i]});
        }
      }
      legend.add("slopes by color");
    }
    if (layers.contains("contours") && !triangles.isEmpty()) {
      float lowest = Float.MAX_VALUE;
      float highest = -Float.MAX_VALUE;
      for (float [][] triangle : triangles) {
        for (float [] corner : triangle) {
          lowest = Math.min(lowest, corner [2]);
          highest = Math.max(highest, corner [2]);
        }
      }
      float interval = (float)number(arguments, "contour_interval", Math.max(10, Math.round((highest - lowest) / 12 / 10) * 10));
      interval = Math.max(interval, (highest - lowest) / 300);
      for (int n = (int)Math.ceil((lowest + 0.01f) / interval); n * interval < highest; n++) {
        float height = n * interval + 0.013f;
        float [] labelPlace = null;
        for (float [][] triangle : triangles) {
          float [] segment = new float [4];
          int count = 0;
          for (int i = 0; i < 3 && count < 2; i++) {
            float [] a = triangle [i];
            float [] b = triangle [(i + 1) % 3];
            if (a [2] < height != b [2] < height) {
              float t = (height - a [2]) / (b [2] - a [2]);
              segment [count * 2] = (a [0] + (b [0] - a [0]) * t - left) * scale;
              segment [count * 2 + 1] = (a [1] + (b [1] - a [1]) * t - top) * scale;
              count++;
            }
          }
          if (count == 2) {
            shapes.add(new Object [] {"line", segment, null, Integer.valueOf(0xE08A5A2B), Float.valueOf(n % 5 == 0 ? 2f : 1f), null});
            if (labelPlace == null && segment [0] > 30 && segment [0] < size [0] - 30 && segment [1] > 20 && segment [1] < size [1] - 20) {
              labelPlace = new float [] {segment [0], segment [1]};
            }
          }
        }
        if (labelPlace != null && n % 5 == 0) {
          shapes.add(new Object [] {"text", labelPlace, null, Integer.valueOf(0xFF6B4A1B), null,
              AssistantStructureCommands.format(n * interval)});
        }
      }
      legendEntries.add(new Object [] {Integer.valueOf(0xE08A5A2B), "contour lines every " + AssistantStructureCommands.format(interval) + " cm"});
      legend.add("contour lines every " + AssistantStructureCommands.format(interval) + " cm");
    }
    if (layers.contains("trees")) {
      int count = 0;
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        Map<String, Object> plant = AssistantPlantCommands.getSpec(piece);
        if (plant == null || !piece.isVisible()) {
          continue;
        }
        float x = (AssistantFurnitureBuilder.getNumber(plant, "x", piece.getX()) - left) * scale;
        float y = (AssistantFurnitureBuilder.getNumber(plant, "y", piece.getY()) - top) * scale;
        float radius = AssistantFurnitureBuilder.getNumber(plant, "crown_w", piece.getWidth()) / 2 * scale;
        boolean removed = plant.get("removed_since") != null;
        shapes.add(new Object [] {"circle", new float [] {x, y, x + radius, y}, Integer.valueOf(removed ? 0x30808080 : 0x5038761D),
            Integer.valueOf(removed ? 0xA0808080 : 0xFF274E13), Float.valueOf(1.2f), null});
        if (radius > 16) {
          String species = String.valueOf(plant.get("species")).replace('_', ' ');
          shapes.add(new Object [] {"text", new float [] {x, y}, null, Integer.valueOf(0xFF1B3A0B), null,
              species.length() > 16 ? species.substring(0, 15) + "." : species});
        }
        count++;
      }
      legendEntries.add(new Object [] {Integer.valueOf(0x9038761D), "crowns of " + count + " generated trees and shrubs"});
      legend.add("the crowns of " + count + " trees and shrubs");
    }
    if (layers.contains("fences")) {
      int [] colors = {0xCC0000, 0x1155CC, 0xBF9000, 0x7030A0, 0x38761D, 0x134F5C, 0xB45F06, 0x741B47};
      Map<String, Integer> styles = new LinkedHashMap<String, Integer>();
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        Map<String, Object> fence = AssistantFenceCommands.getSpec(piece);
        if (fence == null || !piece.isVisible()) {
          continue;
        }
        try {
          float [][] points = AssistantFenceBuilder.getPoints(fence);
          boolean closed = AssistantFenceBuilder.isClosed(fence, points);
          String style = fence.get("style") != null ? fence.get("style").toString() : "picket";
          if (!styles.containsKey(style)) {
            styles.put(style, Integer.valueOf(colors [styles.size() % colors.length]));
          }
          float [] line = new float [(points.length + (closed ? 1 : 0)) * 2];
          for (int i = 0; i < points.length + (closed ? 1 : 0); i++) {
            line [i * 2] = (points [i % points.length][0] - left) * scale;
            line [i * 2 + 1] = (points [i % points.length][1] - top) * scale;
          }
          shapes.add(new Object [] {"line", line, null, Integer.valueOf(0xFF000000 | styles.get(style)), Float.valueOf(3f), null});
        } catch (java.io.IOException ex) {
          // A fence that can't be read isn't drawn
        }
      }
      for (Map.Entry<String, Integer> style : styles.entrySet()) {
        legendEntries.add(new Object [] {Integer.valueOf(0xFF000000 | style.getValue()), "fence " + style.getKey()});
      }
      legend.add("the fences by style");
    }
    // Marks of this picture only
    if (arguments.get("annotate") instanceof List) {
      for (Object item : (List<?>)arguments.get("annotate")) {
        if (!(item instanceof Map)) {
          continue;
        }
        Map<?, ?> mark = (Map<?, ?>)item;
        String text = mark.get("text") != null ? mark.get("text").toString() : null;
        float [] place = null;
        if (mark.get("line") instanceof List) {
          List<?> points = (List<?>)mark.get("line");
          float [] line = new float [points.size() * 2];
          boolean valid = points.size() >= 2;
          for (int i = 0; i < points.size() && valid; i++) {
            valid = points.get(i) instanceof List && ((List<?>)points.get(i)).size() >= 2 && ((List<?>)points.get(i)).get(0) instanceof Number;
            if (valid) {
              line [i * 2] = (((Number)((List<?>)points.get(i)).get(0)).floatValue() - left) * scale;
              line [i * 2 + 1] = (((Number)((List<?>)points.get(i)).get(1)).floatValue() - top) * scale;
            }
          }
          if (valid) {
            shapes.add(new Object [] {"line", line, null, Integer.valueOf(0xFFD0006F), Float.valueOf(2.5f), null});
            place = new float [] {(line [0] + line [2]) / 2, (line [1] + line [3]) / 2 - 10};
          }
        } else {
          Object point = mark.get("marker") instanceof List ? mark.get("marker") : null;
          float x = point != null && ((List<?>)point).size() >= 2 ? ((Number)((List<?>)point).get(0)).floatValue()
              : AssistantFurnitureBuilder.getNumber(mark, "x", Float.NaN);
          float y = point != null && ((List<?>)point).size() >= 2 ? ((Number)((List<?>)point).get(1)).floatValue()
              : AssistantFurnitureBuilder.getNumber(mark, "y", Float.NaN);
          if (!Float.isNaN(x) && !Float.isNaN(y)) {
            float px = (x - left) * scale;
            float py = (y - top) * scale;
            if (point != null || text == null) {
              shapes.add(new Object [] {"circle", new float [] {px, py, px + 6, py}, Integer.valueOf(0xFFD0006F), Integer.valueOf(0xFFFFFFFF),
                  Float.valueOf(1.5f), null});
              place = new float [] {px, py - 14};
            } else {
              place = new float [] {px, py};
            }
          }
        }
        if (text != null && place != null) {
          shapes.add(new Object [] {"text", place, null, Integer.valueOf(0xFFA0004F), null, text});
        }
      }
      legend.add("the marks of \"annotate\"");
    }
    if (bool(arguments, "scale_bar", false)) {
      // A round length about a fifth of the picture wide
      double target = area [2] / 5;
      double length = Math.pow(10, Math.floor(Math.log10(target)));
      length = target / length >= 5 ? length * 5 : target / length >= 2 ? length * 2 : length;
      float x = 20;
      float y = size [1] - 22;
      float width = (float)(length * scale);
      shapes.add(new Object [] {"polygon", new float [] {x - 6, y - 20, x + width + 6, y - 20, x + width + 6, y + 10, x - 6, y + 10},
          Integer.valueOf(0xD0FFFFFF), null, null, null});
      shapes.add(new Object [] {"line", new float [] {x, y - 5, x, y, x + width, y, x + width, y - 5}, null, Integer.valueOf(0xFF000000),
          Float.valueOf(2f), null});
      shapes.add(new Object [] {"text", new float [] {x + width / 2, y - 10}, null, Integer.valueOf(0xFF000000), null,
          length >= 100 ? AssistantStructureCommands.format((float)(length / 100)) + " m" : AssistantStructureCommands.format((float)length) + " cm"});
      legend.add("a scale bar");
    }
    if (bool(arguments, "north_arrow", false)) {
      double north = this.home.getCompass().getNorthDirection();
      float x = size [0] - 40;
      float y = 45;
      float dx = (float)Math.sin(north);
      float dy = -(float)Math.cos(north);
      shapes.add(new Object [] {"circle", new float [] {x, y, x + 30, y}, Integer.valueOf(0xD0FFFFFF), Integer.valueOf(0xFF000000),
          Float.valueOf(1f), null});
      shapes.add(new Object [] {"polygon", new float [] {x + dx * 24, y + dy * 24, x - dx * 14 - dy * 9, y - dy * 14 + dx * 9,
          x - dx * 6, y - dy * 6, x - dx * 14 + dy * 9, y - dy * 14 - dx * 9}, Integer.valueOf(0xFF000000), null, null, null});
      shapes.add(new Object [] {"text", new float [] {x + dx * 12 + dy * 12, y + dy * 12 - dx * 12}, null, Integer.valueOf(0xFFCC0000), null, "N"});
      legend.add("a north arrow");
    }
    if (bool(arguments, "legend", false) && !legendEntries.isEmpty()) {
      float y = 14;
      shapes.add(new Object [] {"polygon", new float [] {6, 4, 300, 4, 300, 10 + legendEntries.size() * 18, 6, 10 + legendEntries.size() * 18},
          Integer.valueOf(0xD8FFFFFF), null, null, null});
      for (Object [] entry : legendEntries) {
        shapes.add(new Object [] {"polygon", new float [] {12, y - 6, 30, y - 6, 30, y + 6, 12, y + 6}, entry [0], Integer.valueOf(0xFF404040),
            Float.valueOf(0.8f), null});
        shapes.add(new Object [] {"label", new float [] {36, y}, null, Integer.valueOf(0xFF000000), null, entry [1]});
        y += 18;
      }
    }
    return shapes;
  }

  private static Level getLevel(Selectable item) {
    if (item instanceof com.eteks.sweethome3d.model.Elevatable) {
      return ((com.eteks.sweethome3d.model.Elevatable)item).getLevel();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> get3DImage(Map<String, Object> arguments) {
    return get3DImage(arguments, null);
  }

  /**
   * Returns the picture of the home at the instants of <code>sweep</code>,
   * seen as the arguments of <code>get_3d_image</code> tell.
   */
  private Map<String, Object> getSunStudyImage(Map<String, Object> arguments) {
    if (this.sunStudyView == null) {
      return toolResult("The pictures of the sun can't be drawn here.", true);
    }
    SunSweep sweep;
    try {
      sweep = createSunSweep(arguments);
    } catch (IllegalArgumentException ex) {
      return toolResult(ex.getMessage(), true);
    }
    Map<String, Object> imageArguments = new LinkedHashMap<String, Object>(arguments);
    imageArguments.remove("store_as");
    imageArguments.remove("compare_with");
    // One picture of the hours of sun, or small pictures of instants
    imageArguments.put("width", Integer.valueOf((int)Math.max(160, sweep.hours
        ? Math.min(1600, number(arguments, "width", 800)) : Math.min(800, number(arguments, "width", 400)))));
    imageArguments.put("height", Integer.valueOf((int)Math.max(120, sweep.hours
        ? Math.min(1200, number(arguments, "height", 520)) : Math.min(600, number(arguments, "height", 260)))));
    return get3DImage(imageArguments, sweep);
  }

  /**
   * Returns the minutes of a time written HH:MM, or -1 if <code>time</code> is <code>null</code>.
   */
  private static int parseMinutes(String time, String name) {
    if (time == null) {
      return -1;
    }
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(time.trim());
    if (!matcher.matches() || Integer.parseInt(matcher.group(1)) > 23 || Integer.parseInt(matcher.group(2)) > 59) {
      throw new IllegalArgumentException("\"" + name + "\" is written HH:MM, from 00:00 to 23:59.");
    }
    return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
  }

  /**
   * Returns the instants of the pictures a sun study asks for, with their captions.
   */
  private SunSweep createSunSweep(Map<String, Object> arguments) {
    String mode = string(arguments, "sweep") != null ? string(arguments, "sweep").toLowerCase() : "day";
    if (!"day".equals(mode) && !"year".equals(mode) && !"hours".equals(mode)) {
      throw new IllegalArgumentException("\"sweep\" is day, year or hours.");
    }
    java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
    // As the time of cameras, a calendar in UTC whose fields are the local date and time of the place
    java.util.Calendar calendar = new java.util.GregorianCalendar(utc);
    calendar.setTimeInMillis(this.home.getCamera().getTime());
    calendar.set(java.util.Calendar.SECOND, 0);
    calendar.set(java.util.Calendar.MILLISECOND, 0);
    String date = string(arguments, "date");
    if (date != null) {
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(date.trim());
      if (!matcher.matches() || Integer.parseInt(matcher.group(2)) < 1 || Integer.parseInt(matcher.group(2)) > 12
          || Integer.parseInt(matcher.group(3)) < 1 || Integer.parseInt(matcher.group(3)) > 31) {
        throw new IllegalArgumentException("\"date\" is written YYYY-MM-DD.");
      }
      calendar.set(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) - 1, Integer.parseInt(matcher.group(3)));
    }
    int minutes = parseMinutes(string(arguments, "time"), "time");
    if (minutes >= 0) {
      calendar.set(java.util.Calendar.HOUR_OF_DAY, minutes / 60);
      calendar.set(java.util.Calendar.MINUTE, minutes % 60);
    }
    com.eteks.sweethome3d.model.Compass compass = this.home.getCompass();
    java.util.TimeZone timeZone = java.util.TimeZone.getTimeZone(compass.getTimeZone());
    double latitude = Math.toDegrees(compass.getLatitude());
    double longitude = Math.toDegrees(compass.getLongitude());
    SunSweep sweep = new SunSweep();
    String title;
    if ("hours".equals(mode)) {
      // The instants of a day at which the sun is looked for, to count how long it reaches each place
      long [] instants = com.eteks.sweethome3d.model.SunPosition.getSunriseNoonSunset(
          Camera.convertTimeToTimeZone(calendar.getTimeInMillis(), compass.getTimeZone()), timeZone, latitude, longitude);
      String day = String.format(java.util.Locale.US, "%04d-%02d-%02d", calendar.get(java.util.Calendar.YEAR),
          calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH));
      double start = 0;
      double end = 24 * 60;
      String daylightText;
      if (instants [0] != Long.MIN_VALUE && instants [2] != Long.MIN_VALUE) {
        java.util.Calendar local = new java.util.GregorianCalendar(timeZone);
        local.setTimeInMillis(instants [0]);
        start = local.get(java.util.Calendar.HOUR_OF_DAY) * 60 + local.get(java.util.Calendar.MINUTE) + local.get(java.util.Calendar.SECOND) / 60.;
        local.setTimeInMillis(instants [2]);
        end = local.get(java.util.Calendar.HOUR_OF_DAY) * 60 + local.get(java.util.Calendar.MINUTE) + local.get(java.util.Calendar.SECOND) / 60.;
        if (end <= start) {
          end = 24 * 60;
        }
        daylightText = String.format(java.util.Locale.US, "sunrise at %02d:%02d and sunset at %02d:%02d",
            (int)Math.round(start) / 60, (int)Math.round(start) % 60, (int)Math.round(end) / 60, (int)Math.round(end) % 60);
      } else if (com.eteks.sweethome3d.model.SunPosition.getInstance(instants [1], latitude, longitude).getApparentElevation() > 0) {
        daylightText = "the sun doesn't set";
      } else {
        throw new IllegalArgumentException("The sun doesn't rise on " + day + " at this latitude.");
      }
      int from = parseMinutes(string(arguments, "from"), "from");
      int to = parseMinutes(string(arguments, "to"), "to");
      start = from >= 0 ? Math.max(start, from) : start;
      end = to >= 0 ? Math.min(end, to) : end;
      if (end - start < 10) {
        throw new IllegalArgumentException("The sun isn't up between \"from\" and \"to\" (" + daylightText + ").");
      }
      int steps = (int)number(arguments, "steps", Math.max(8, Math.min(48, Math.round((end - start) / 20))));
      if (steps < 4 || steps > 96) {
        throw new IllegalArgumentException("\"steps\" goes from 4 to 96 for the hours of sun.");
      }
      sweep.times = new long [steps];
      for (int i = 0; i < steps; i++) {
        int seconds = (int)Math.round((start + (end - start) * (i + 0.5) / steps) * 60);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, seconds / 3600);
        calendar.set(java.util.Calendar.MINUTE, seconds / 60 % 60);
        calendar.set(java.util.Calendar.SECOND, seconds % 60);
        sweep.times [i] = calendar.getTimeInMillis();
      }
      sweep.hours = true;
      sweep.stepHours = (end - start) / 60 / steps;
      sweep.maximumHours = number(arguments, "max_hours", Math.ceil((end - start) / 60));
      if (sweep.maximumHours < 1 || sweep.maximumHours > 24) {
        throw new IllegalArgumentException("\"max_hours\" goes from 1 to 24.");
      }
      sweep.title = "Hours of direct sun on " + day;
      sweep.text = String.format(java.util.Locale.US, "Hours of direct sun on %s (%s), latitude %.2f, longitude %.2f, %s: "
          + "counted between %02d:%02d and %02d:%02d from %d instants %.0f minutes apart, by clear sky. "
          + "Colors: dark blue 0 h, light blue, cyan, green, yellow, orange, then red %s h and more; the sky keeps its color. "
          + "A surface the sun meets from behind counts as shade.",
          day, compass.getTimeZone(), latitude, longitude, daylightText,
          (int)Math.round(start) / 60, (int)Math.round(start) % 60, (int)Math.round(end) / 60, (int)Math.round(end) % 60,
          steps, sweep.stepHours * 60,
          sweep.maximumHours == Math.rint(sweep.maximumHours) ? String.valueOf((int)sweep.maximumHours) : String.valueOf(sweep.maximumHours));
      return sweep;
    } else if ("day".equals(mode)) {
      int steps = (int)number(arguments, "steps", 6);
      if (steps < 2 || steps > 12) {
        throw new IllegalArgumentException("\"steps\" goes from 2 to 12 for a day.");
      }
      int from = parseMinutes(string(arguments, "from"), "from");
      int to = parseMinutes(string(arguments, "to"), "to");
      // Between sunrise and sunset by default, away from both where nothing is lit yet
      boolean daylight = from < 0 && to < 0;
      String daylightText = "";
      if (from < 0 || to < 0) {
        long [] instants = com.eteks.sweethome3d.model.SunPosition.getSunriseNoonSunset(
            Camera.convertTimeToTimeZone(calendar.getTimeInMillis(), compass.getTimeZone()), timeZone, latitude, longitude);
        int sunrise = 0;
        int sunset = 24 * 60;
        if (instants [0] != Long.MIN_VALUE && instants [2] != Long.MIN_VALUE) {
          java.util.Calendar local = new java.util.GregorianCalendar(timeZone);
          local.setTimeInMillis(instants [0] + 30000);
          sunrise = local.get(java.util.Calendar.HOUR_OF_DAY) * 60 + local.get(java.util.Calendar.MINUTE);
          local.setTimeInMillis(instants [2] + 30000);
          sunset = local.get(java.util.Calendar.HOUR_OF_DAY) * 60 + local.get(java.util.Calendar.MINUTE);
          daylightText = String.format(java.util.Locale.US, ", sunrise at %02d:%02d and sunset at %02d:%02d",
              sunrise / 60, sunrise % 60, sunset / 60, sunset % 60);
        } else {
          daylightText = ", a day when the sun doesn't cross the horizon";
        }
        from = from < 0 ? sunrise : from;
        to = to < 0 ? sunset : to;
      }
      if (to <= from) {
        throw new IllegalArgumentException("\"to\" must be later than \"from\" (" + String.format(java.util.Locale.US,
            "%02d:%02d", from / 60, from % 60) + ").");
      }
      sweep.times = new long [steps];
      for (int i = 0; i < steps; i++) {
        double stepMinutes = daylight
            ? from + (to - from) * (i + 0.5) / steps
            : from + (to - from) * (double)i / (steps - 1);
        int rounded = (int)Math.max(0, Math.min(24 * 60 - 5, Math.round(stepMinutes / 5) * 5));
        calendar.set(java.util.Calendar.HOUR_OF_DAY, rounded / 60);
        calendar.set(java.util.Calendar.MINUTE, rounded % 60);
        sweep.times [i] = calendar.getTimeInMillis();
      }
      title = String.format(java.util.Locale.US, "The day %04d-%02d-%02d", calendar.get(java.util.Calendar.YEAR),
          calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH)) + daylightText;
    } else {
      int steps = (int)number(arguments, "steps", 4);
      if (steps < 2 || steps > 12 || 12 % steps != 0) {
        throw new IllegalArgumentException("\"steps\" is 2, 3, 4, 6 or 12 for a year.");
      }
      int [][] seasons = {{2, 20}, {5, 21}, {8, 22}, {11, 21}};
      sweep.times = new long [steps];
      for (int i = 0; i < steps; i++) {
        // The equinoxes and the solstices, or the 21st of months
        int month = steps == 4 ? seasons [i][0] : (i + 1) * 12 / steps - 1;
        int day = steps == 4 ? seasons [i][1] : 21;
        calendar.set(calendar.get(java.util.Calendar.YEAR), month, day);
        sweep.times [i] = calendar.getTimeInMillis();
      }
      title = String.format(java.util.Locale.US, "The year %04d at %02d:%02d", calendar.get(java.util.Calendar.YEAR),
          calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE));
    }
    // False colors of the light level: as the home, true, false or the light of the last color
    sweep.falseColors = this.home.getProperty(AssistantSceneCommands.SUN_FALSE_COLORS_PROPERTY);
    Object falseColors = arguments.get("false_colors");
    if (falseColors instanceof Number
        || falseColors instanceof String && ((String)falseColors).trim().matches("\\d+(\\.\\d+)?")) {
      double maximum = falseColors instanceof Number
          ? ((Number)falseColors).doubleValue() : Double.parseDouble(((String)falseColors).trim());
      if (maximum < 100 || maximum > 200000) {
        throw new IllegalArgumentException("\"false_colors\" is true, false, or the light in lux shown by the last color, from 100 to 200000.");
      }
      sweep.falseColors = String.valueOf(Math.round(maximum));
    } else if (falseColors != null) {
      sweep.falseColors = bool(arguments, "false_colors", false) || "true".equalsIgnoreCase(String.valueOf(falseColors))
          ? "120000" : null;
    }
    int count = sweep.times.length;
    sweep.columns = count <= 4 ? count : count <= 6 ? 3 : 4;
    sweep.captions = new String [count];
    String [] directions = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
    String [] shortDirections = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    StringBuilder text = new StringBuilder(title + String.format(java.util.Locale.US,
        " (%s), latitude %.2f, longitude %.2f, from left to right then top to bottom:", compass.getTimeZone(), latitude, longitude));
    for (int i = 0; i < count; i++) {
      calendar.setTimeInMillis(sweep.times [i]);
      String when = String.format(java.util.Locale.US, "%04d-%02d-%02d %02d:%02d", calendar.get(java.util.Calendar.YEAR),
          calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH),
          calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE));
      com.eteks.sweethome3d.model.SunPosition sun = com.eteks.sweethome3d.model.SunPosition.getInstance(
          Camera.convertTimeToTimeZone(sweep.times [i], compass.getTimeZone()), latitude, longitude);
      double elevation = sun.getApparentElevation();
      int direction = (int)Math.round(sun.getAzimuth() / 45) % 8;
      text.append("\n").append(i + 1).append(". ").append(when).append(": ");
      if (elevation > 0) {
        sweep.captions [i] = when + String.format(java.util.Locale.US, "   sun %.0f\u00b0 %s", elevation, shortDirections [direction]);
        text.append(String.format(java.util.Locale.US, "sun %.1f degrees above the horizon, in the %s (azimuth %.1f), "
            + "shadows %.2f times as long as the things are high, %d lux on the ground in the sun and %d in the shade",
            elevation, directions [direction], sun.getAzimuth(), sun.getShadowLength(),
            Math.round((sun.getDirectNormalIlluminance() * Math.sin(Math.toRadians(elevation))
                + sun.getDiffuseHorizontalIlluminance()) / 100) * 100,
            Math.round(sun.getDiffuseHorizontalIlluminance() / 100) * 100));
      } else {
        sweep.captions [i] = when + "   sun down";
        text.append(String.format(java.util.Locale.US, "the sun is %.1f degrees under the horizon", -elevation));
      }
    }
    if (sweep.falseColors != null) {
      text.append("\nFalse colors of the light received by clear sky from the sun and the sky: dark blue 0 lux, light blue, cyan, "
          + "green, yellow, orange, then red ").append(sweep.falseColors).append(" lux and more (the sky isn't hidden by what is "
          + "over a surface, so the light under a roof is overestimated outside the sun patches).");
    }
    sweep.text = text.toString();
    return sweep;
  }

  /**
   * Returns the pictures of the home at the instants of <code>sweep</code> in one image.
   */
  private Image3D drawSunSweep(ImageProvider provider, SunSweep sweep, int width, int height, String renderer,
                               boolean shadows, boolean ambientOcclusion, boolean highQuality,
                               HomeViewpoints.Pose pose, Level level, boolean allLevels, int timeout) {
    SunStudyView view = this.sunStudyView;
    byte [][] images = new byte [sweep.times.length][];
    String description = null;
    for (int i = 0; i < images.length; i++) {
      // A copy of the home at that instant: the home of the user keeps its date and its time
      Home drawnHome = this.home.clone();
      for (Camera camera : new Camera [] {drawnHome.getTopCamera(), drawnHome.getObserverCamera(), drawnHome.getCamera()}) {
        camera.setTime(sweep.times [i]);
      }
      drawnHome.setProperty(AssistantSceneCommands.SUN_FOLLOWS_TIME_PROPERTY, "true");
      drawnHome.setProperty(AssistantSceneCommands.SUN_FALSE_COLORS_PROPERTY, sweep.falseColors);
      if (sweep.hours) {
        // Red where the sun reaches, black where it doesn't, and a green sky
        drawnHome.setProperty(AssistantSceneCommands.SUN_MASK_PROPERTY, "true");
        drawnHome.getEnvironment().setSkyColor(0x00FF00);
        drawnHome.getEnvironment().setSkyTexture(null);
      }
      int levelIndex = level != null ? this.home.getLevels().indexOf(level) : -1;
      if (levelIndex >= 0 && levelIndex < drawnHome.getLevels().size()) {
        drawnHome.setSelectedLevel(drawnHome.getLevels().get(levelIndex));
      }
      Image3DRequest request = sweep.hours
          ? new Image3DRequest(width, height, Image3DRequest.OPENGL, true, false, false, pose, null, allLevels, 0, timeout)
          : new Image3DRequest(width, height, renderer, shadows, ambientOcclusion, highQuality, pose, null, allLevels, 0, timeout);
      request.setDrawnHome(drawnHome);
      Image3D image = provider.get3DImage(request);
      if (image == null || image.getImage() == null) {
        return image;
      }
      images [i] = image.getImage();
      if (description == null) {
        description = image.getDescription();
      }
    }
    if (sweep.hours) {
      if (description == null || !description.contains("OpenGL renderer")) {
        return new Image3D(null, null, "The hours of sun need the OpenGL renderer, which can't draw here.");
      }
      // The same picture with its usual colors, which gives its relief to the map and its sky
      Home drawnHome = this.home.clone();
      drawnHome.setProperty(AssistantSceneCommands.SUN_FOLLOWS_TIME_PROPERTY, null);
      drawnHome.setProperty(AssistantSceneCommands.SUN_FALSE_COLORS_PROPERTY, null);
      int levelIndex = level != null ? this.home.getLevels().indexOf(level) : -1;
      if (levelIndex >= 0 && levelIndex < drawnHome.getLevels().size()) {
        drawnHome.setSelectedLevel(drawnHome.getLevels().get(levelIndex));
      }
      Image3DRequest request = new Image3DRequest(width, height, Image3DRequest.OPENGL, false, true, false,
          pose, null, allLevels, 0, timeout);
      request.setDrawnHome(drawnHome);
      Image3D shading = provider.get3DImage(request);
      if (shading == null || shading.getImage() == null) {
        return shading;
      }
      float [] shares = new float [3];
      byte [] map = view != null
          ? view.createSunHoursImage(images, shading.getImage(), sweep.stepHours, sweep.maximumHours, sweep.title, shares)
          : null;
      if (map == null) {
        return null;
      }
      sweep.text += String.format(java.util.Locale.US, " Of the surfaces in the picture, %.0f %% get less than 2 hours of sun, "
          + "%.0f %% from 2 to 6 hours and %.0f %% more.", shares [0] * 100, shares [1] * 100, shares [2] * 100);
      return new Image3D(map, null, "Drawn by the OpenGL renderer");
    }
    byte [] sheet = view != null ? view.createContactSheet(images, sweep.captions, sweep.columns) : null;
    return sheet != null ? new Image3D(sheet, null, description) : null;
  }

  /**
   * Shows the window of the sun, plays its animations and chooses the renderer of the 3D view.
   */
  private Map<String, Object> sunStudy(Map<String, Object> arguments) {
    SunStudyView view = this.sunStudyView;
    if (view == null) {
      return toolResult("The window of the sun can't be shown here.", true);
    }
    String window = string(arguments, "window") != null ? string(arguments, "window").toLowerCase() : null;
    String play = string(arguments, "play") != null ? string(arguments, "play").toLowerCase() : null;
    String renderer = string(arguments, "renderer") != null ? string(arguments, "renderer").toLowerCase() : null;
    Boolean shadows = arguments.get("shadows") != null ? Boolean.valueOf(bool(arguments, "shadows", true)) : null;
    if (window != null && !"open".equals(window) && !"close".equals(window)) {
      return toolResult("\"window\" is open or close.", true);
    } else if (play != null && !"day".equals(play) && !"year".equals(play) && !"stop".equals(play)) {
      return toolResult("\"play\" is day, year or stop.", true);
    } else if (renderer != null && !"opengl".equals(renderer) && !"java3d".equals(renderer)) {
      return toolResult("\"renderer\" is opengl or java3d.", true);
    } else if ("close".equals(window) && play != null && !"stop".equals(play)) {
      return toolResult("An animation plays in the window: it can't be closed in the same call.", true);
    }
    boolean playing = play != null && !"stop".equals(play);
    if ((playing || renderer != null || shadows != null) && !isEditingAllowed()) {
      return toolResult("Playing the sun changes the date and the time of the home, and the renderer and the shadows are "
          + "preferences of the user: these need the permission to change things, which the user didn't give "
          + "(Tools > Claude MCP server). \"window\" and \"play\":\"stop\" work without it.", true);
    }
    try {
      String note = "";
      if (renderer != null || shadows != null) {
        view.setDisplay(renderer != null ? Boolean.valueOf("opengl".equals(renderer)) : null, shadows);
      }
      if ("open".equals(window)) {
        view.showWindow(true);
      }
      if (playing) {
        if (this.home.getProperty(AssistantSceneCommands.SUN_FOLLOWS_TIME_PROPERTY) == null) {
          // Nothing would move in the 3D view otherwise
          this.home.setProperty(AssistantSceneCommands.SUN_FOLLOWS_TIME_PROPERTY, "true");
          this.home.setModified(true);
          note = " It was switched to the light of the sun for the animation (set_sun with \"follow\":false gives its own lights back).";
        }
        view.play(play);
        note += " The animation changes the " + ("day".equals(play) ? "time" : "date")
            + " of the sun of the home and runs until {\"play\":\"stop\"} or until the user stops it.";
      } else if (play != null) {
        view.play(null);
      }
      if ("close".equals(window)) {
        view.showWindow(false);
      }
      return toolResult(view.describe() + note, false);
    } catch (Exception ex) {
      return toolResult("Couldn't do it: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()), true);
    }
  }

  private Map<String, Object> get3DImage(Map<String, Object> arguments, SunSweep sweep) {
    ImageProvider provider = this.imageProvider;
    if (provider == null) {
      return toolResult("The home can't be rendered in 3D here.", true);
    }
    String saveTo = string(arguments, "save_to");
    java.io.File savedFile = null;
    if (saveTo != null) {
      savedFile = new java.io.File(saveTo);
      if (!isEditingAllowed()) {
        return toolResult("Saving a picture needs the permission to change things: the user didn't allow changes "
            + "to this home (Tools > Claude MCP server). Call get_3d_image without save_to.", true);
      } else if (!savedFile.isAbsolute() || !saveTo.toLowerCase().endsWith(".png")) {
        return toolResult("\"save_to\" must be the absolute path of a .png file.", true);
      } else if (savedFile.exists() && !bool(arguments, "replace", false)) {
        return toolResult("The file " + saveTo + " exists: pass \"replace\":true to overwrite it, or an other name.", true);
      } else if (savedFile.getParentFile() == null || !savedFile.getParentFile().isDirectory()) {
        return toolResult("The folder of " + saveTo + " doesn't exist.", true);
      }
    }
    int maxWidth = savedFile != null ? 4096 : 1600;
    int maxHeight = savedFile != null ? 4096 : 1200;
    int width = (int)Math.max(160, Math.min(maxWidth, number(arguments, "width", 800)));
    int height = (int)Math.max(120, Math.min(maxHeight, number(arguments, "height", 500)));

    String renderer = string(arguments, "renderer");
    renderer = renderer != null ? renderer.toLowerCase() : Image3DRequest.AUTO;
    if ("ray_trace".equals(renderer) || "raytracer".equals(renderer) || "photo".equals(renderer)) {
      renderer = Image3DRequest.RAYTRACE;
    }
    if (!Image3DRequest.AUTO.equals(renderer) && !Image3DRequest.OPENGL.equals(renderer)
        && !Image3DRequest.RAYTRACE.equals(renderer)) {
      return toolResult("\"renderer\" must be auto, opengl or raytrace.", true);
    }
    boolean highQuality = "high".equalsIgnoreCase(string(arguments, "quality"));
    if (highQuality) {
      if (Image3DRequest.OPENGL.equals(renderer)) {
        return toolResult("\"quality\":\"high\" is an option of the ray tracer: pass \"renderer\":\"raytrace\" with it, "
            + "or leave quality out.", true);
      }
      renderer = Image3DRequest.RAYTRACE;
      // Minutes of computation otherwise
      if (width > 1000 || height > 750) {
        float scale = Math.min(1000f / width, 750f / height);
        width = Math.round(width * scale);
        height = Math.round(height * scale);
      }
    }

    Level level = null;
    String levelName = string(arguments, "level");
    if (levelName != null) {
      level = findLevel(levelName);
      if (level == null) {
        return toolResult("No level is named \"" + levelName + "\". Levels: " + getLevelNames() + ".", true);
      }
    }
    String levels = string(arguments, "levels");
    if (levels != null && !"all".equalsIgnoreCase(levels) && !"selected".equalsIgnoreCase(levels)) {
      return toolResult("\"levels\" must be all or selected.", true);
    }
    boolean allLevels = !"selected".equalsIgnoreCase(levels);
    Level selectedLevel = level != null ? level : this.home.getSelectedLevel();
    Level displayedLevel = allLevels ? null : selectedLevel;

    double fieldOfViewDegrees = number(arguments, "fov", 0);
    if (fieldOfViewDegrees != 0 && (fieldOfViewDegrees < 20 || fieldOfViewDegrees > 120)) {
      return toolResult("\"fov\" must be between 20 and 120 degrees.", true);
    }
    float fieldOfView = (float)Math.toRadians(fieldOfViewDegrees);
    String view = string(arguments, "view");
    String pointOfView = string(arguments, "point_of_view");
    String target = string(arguments, "target");
    HomeViewpoints.Pose pose = null;
    String viewDescription;
    String photoPath = null;
    List<HomePieceOfFurniture> occludingPieces = new ArrayList<HomePieceOfFurniture>();
    if (string(arguments, "photo") != null) {
      // From where a photo was taken, beside it
      Map<String, Object> photo = AssistantPhotoCommands.getPhoto(this.home, string(arguments, "photo"));
      if (photo == null) {
        return toolResult("No photo " + string(arguments, "photo") + ": the list_photos command lists the photos of the home.", true);
      } else if (photo.get("heading") == null) {
        return toolResult("The photo " + photo.get("id") + " has no direction: the refine_photo_pose command finds it.", true);
      }
      float photoX = AssistantFurnitureBuilder.getNumber(photo, "x", 0);
      float photoY = AssistantFurnitureBuilder.getNumber(photo, "y", 0);
      float ground = this.commandExecutor.getShapeCommands().getStandingHeight(photoX, photoY);
      // The shape and the field of view of the photo seen upright
      float [] photoShape = AssistantPhotoCommands.getShape(photo);
      if (photoShape [0] > 0 && arguments.get("width") == null) {
        if (arguments.get("height") == null && photoShape [0] < 1) {
          height = Math.min(maxHeight, 800);
        }
        width = Math.round(height * photoShape [0]);
        if (width > maxWidth) {
          width = maxWidth;
          height = Math.max(120, Math.round(width / photoShape [0]));
        } else if (width < 160) {
          width = 160;
          height = Math.min(maxHeight, Math.round(width / photoShape [0]));
        }
      }
      pose = new HomeViewpoints.Pose(photoX, photoY, ground + 160,
          (float)Math.toRadians(AssistantFurnitureBuilder.getNumber(photo, "heading", 0) + 180), 0,
          (float)Math.toRadians(photoShape [1]), false);
      viewDescription = "photo " + photo.get("id") + ", " + pose.describe();
      photoPath = String.valueOf(photo.get("path"));
    } else if (arguments.get("camera") instanceof Map && ((Map<String, Object>)arguments.get("camera")).get("yaw") != null) {
      // A camera given by its place and its angles
      Map<String, Object> camera = (Map<String, Object>)arguments.get("camera");
      double x = number(camera, "x", Double.NaN);
      double y = number(camera, "y", Double.NaN);
      double z = number(camera, "z", number(camera, "height", 160));
      double yaw = number(camera, "yaw", 0);
      double pitch = number(camera, "pitch", 0);
      double cameraFieldOfView = number(camera, "fov", fieldOfView > 0 ? Math.toDegrees(fieldOfView) : 63);
      if (Double.isNaN(x) || Double.isNaN(y) || Math.abs(x) > 1E6 || Math.abs(y) > 1E6 || z < 0 || z > 1E5
          || Math.abs(pitch) > 90 || cameraFieldOfView < 20 || cameraFieldOfView > 120) {
        return toolResult("\"camera\" with \"yaw\" needs x, y in centimeters, z (its height, 160 by default), yaw in degrees "
            + "(0 looks down the plan, 90 to its left, 180 up the plan), pitch from -90 to 90 (positive looks down) and fov from 20 to 120.", true);
      }
      pose = new HomeViewpoints.Pose((float)x, (float)y, (float)Math.max(1, z), (float)Math.toRadians(yaw), (float)Math.toRadians(pitch),
          (float)Math.toRadians(cameraFieldOfView), z > 400);
      viewDescription = pose.describe();
    } else if (arguments.get("camera") instanceof Map) {
      Map<String, Object> camera = (Map<String, Object>)arguments.get("camera");
      double x = number(camera, "x", Double.NaN);
      double y = number(camera, "y", Double.NaN);
      double lookAtX = number(camera, "look_at_x", Double.NaN);
      double lookAtY = number(camera, "look_at_y", Double.NaN);
      double cameraHeight = number(camera, "height", 160);
      double lookAtHeight = number(camera, "look_at_height", 100);
      if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(lookAtX) || Double.isNaN(lookAtY)
          || Math.abs(x) > 1E6 || Math.abs(y) > 1E6 || Math.abs(lookAtX) > 1E6 || Math.abs(lookAtY) > 1E6
          || cameraHeight < 0 || cameraHeight > 1E5 || Math.abs(lookAtHeight) > 1E5) {
        return toolResult("\"camera\" needs x, y, look_at_x and look_at_y in centimeters "
            + "(and optionally height and look_at_height above the floor).", true);
      }
      float elevation = selectedLevel != null ? selectedLevel.getElevation() : 0;
      pose = HomeViewpoints.lookAt((float)x, (float)y, (float)(elevation + Math.max(1, cameraHeight)),
          (float)lookAtX, (float)lookAtY, (float)(elevation + lookAtHeight),
          fieldOfView > 0 ? fieldOfView : (float)Math.toRadians(63), cameraHeight > 400);
      viewDescription = pose.describe();
    } else if (pointOfView != null) {
      Camera storedCamera = null;
      StringBuilder names = new StringBuilder();
      for (Camera camera : this.home.getStoredCameras()) {
        names.append(names.length() > 0 ? ", " : "").append('"').append(camera.getName()).append('"');
        if (pointOfView.equalsIgnoreCase(camera.getName())) {
          storedCamera = camera;
        }
      }
      if (storedCamera == null) {
        return toolResult("No stored point of view is named \"" + pointOfView + "\". Stored: "
            + (names.length() > 0 ? names.toString() : "none") + ".", true);
      }
      pose = new HomeViewpoints.Pose(storedCamera.getX(), storedCamera.getY(), storedCamera.getZ(),
          storedCamera.getYaw(), storedCamera.getPitch(),
          fieldOfView > 0 ? fieldOfView : storedCamera.getFieldOfView(), !(storedCamera instanceof ObserverCamera));
      viewDescription = "point of view \"" + storedCamera.getName() + "\", " + pose.describe();
    } else if (view != null && !"current".equalsIgnoreCase(view)) {
      if (!HomeViewpoints.isView(view)) {
        return toolResult("Unknown view \"" + view + "\". Views: current, " + HomeViewpoints.AERIAL_VIEWS.toString()
            .replace("[", "").replace("]", "") + ", " + HomeViewpoints.INSIDE_VIEW + ".", true);
      }
      List<Selectable> targets = new ArrayList<Selectable>();
      if (target != null) {
        targets = resolveTarget(target);
        if (targets.isEmpty()) {
          return toolResult("No item matches the target \"" + target + "\" (ids are the ones of the last get_home).", true);
        }
      }
      try {
        pose = HomeViewpoints.getPose(this.home, view, targets, displayedLevel, (float)width / height, fieldOfView);
      } catch (IllegalArgumentException ex) {
        return toolResult(ex.getMessage() + ".", true);
      }
      if (level == null && !targets.isEmpty() && !pose.isAerial() && getLevel(targets.get(0)) != null) {
        // Inside a room: its level is the one to show
        level = getLevel(targets.get(0));
      }
      viewDescription = "view " + view.toLowerCase() + (target != null ? " of " + target : "") + ", " + pose.describe();
      String occluders = string(arguments, "occluders");
      if (occluders != null && !"hide".equalsIgnoreCase(occluders) && !"keep".equalsIgnoreCase(occluders)) {
        return toolResult("\"occluders\" is hide (the default with a target) or keep.", true);
      }
      if (!targets.isEmpty() && pose.isAerial() && !"keep".equalsIgnoreCase(occluders)) {
        occludingPieces = getOccluders(pose, targets);
      }
    } else {
      if (target != null) {
        return toolResult("\"target\" needs a \"view\" (top, south_east, inside...) to look at it.", true);
      }
      Camera camera = this.home.getCamera();
      if (fieldOfView > 0) {
        pose = new HomeViewpoints.Pose(camera.getX(), camera.getY(), camera.getZ(), camera.getYaw(), camera.getPitch(),
            fieldOfView, camera == this.home.getTopCamera());
      }
      viewDescription = "camera of the home, " + (pose != null ? pose.describe() : HomeViewpoints.describeCamera(this.home));
    }

    // What the picture leaves out or shows another way, in a copy of the home
    Home drawnHome = null;
    String hiddenDescription = "";
    if (sweep == null && (!occludingPieces.isEmpty()
                          || arguments.get("exclude_tags") != null || arguments.get("include_tags") != null
                          || arguments.get("hide_ids") != null || arguments.get("xray") != null
                          || arguments.get("season") != null || arguments.get("cutaway") != null
                          || arguments.get("date") != null || arguments.get("time") != null)) {
      List<String> excludedTags = strings(arguments.get("exclude_tags"));
      List<String> includedTags = strings(arguments.get("include_tags"));
      List<String> hiddenIds = strings(arguments.get("hide_ids"));
      boolean xray = Boolean.TRUE.equals(arguments.get("xray"));
      String season = string(arguments, "season");
      if (season != null) {
        season = season.trim().toLowerCase();
        if (season.equals("fall")) {
          season = "autumn";
        }
        if (!Arrays.asList("spring", "summer", "autumn", "winter").contains(season)) {
          return toolResult("\"season\" is spring, summer, autumn or winter.", true);
        }
      }
      double cutaway = number(arguments, "cutaway", Double.NaN);
      drawnHome = this.home.clone();
      String sunText = "";
      if (arguments.get("date") != null || arguments.get("time") != null) {
        // The sun at a date and a time, for this picture
        java.util.Calendar calendar = new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(this.home.getCamera().getTime());
        java.util.regex.Matcher dateMatcher = java.util.regex.Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(
            string(arguments, "date") != null ? string(arguments, "date").trim() : "");
        java.util.regex.Matcher timeMatcher = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(
            string(arguments, "time") != null ? string(arguments, "time").trim() : "");
        if (arguments.get("date") != null && !dateMatcher.matches() || arguments.get("time") != null && !timeMatcher.matches()) {
          return toolResult("\"date\" is written YYYY-MM-DD and \"time\" HH:MM.", true);
        }
        if (arguments.get("date") != null) {
          calendar.set(Integer.parseInt(dateMatcher.group(1)), Integer.parseInt(dateMatcher.group(2)) - 1, Integer.parseInt(dateMatcher.group(3)));
        }
        if (arguments.get("time") != null) {
          calendar.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(timeMatcher.group(1)));
          calendar.set(java.util.Calendar.MINUTE, Integer.parseInt(timeMatcher.group(2)));
          calendar.set(java.util.Calendar.SECOND, 0);
        }
        for (Camera camera : new Camera [] {drawnHome.getCamera(), drawnHome.getTopCamera(), drawnHome.getObserverCamera()}) {
          camera.setTime(calendar.getTimeInMillis());
        }
        drawnHome.setProperty(AssistantSceneCommands.SUN_FOLLOWS_TIME_PROPERTY, "true");
        sunText = " Lit by the sun of " + String.format("%tF at %tR", calendar, calendar) + ", local time.";
      }
      if (level != null) {
        int levelIndex = this.home.getLevels().indexOf(level);
        if (levelIndex >= 0 && levelIndex < drawnHome.getLevels().size()) {
          drawnHome.setSelectedLevel(drawnHome.getLevels().get(levelIndex));
        }
      }
      List<HomePieceOfFurniture> furniture = this.home.getFurniture();
      List<HomePieceOfFurniture> drawnFurniture = drawnHome.getFurniture();
      int hidden = 0;
      int changed = 0;
      for (int i = 0; i < furniture.size() && i < drawnFurniture.size(); i++) {
        HomePieceOfFurniture piece = furniture.get(i);
        HomePieceOfFurniture drawnPiece = drawnFurniture.get(i);
        if (!piece.isVisible()) {
          continue;
        }
        boolean structure = piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) != null;
        boolean hide = hiddenIds.contains(HomeItemIds.getId(piece)) || occludingPieces.contains(piece);
        for (String tag : excludedTags) {
          hide |= HomeItemTags.hasTag(piece, tag);
        }
        if (!includedTags.isEmpty() && !structure) {
          boolean included = false;
          for (String tag : includedTags) {
            included |= HomeItemTags.hasTag(piece, tag);
          }
          hide |= !included;
        }
        if (!Double.isNaN(cutaway) && !HomeTerrain.isTerrain(piece)
            && piece.getElevation() + (piece.getLevel() != null ? piece.getLevel().getElevation() : 0) >= cutaway) {
          hide = true;
        }
        if (hide) {
          drawnPiece.setVisible(false);
          hidden++;
        } else if ((xray || season != null) && this.commandExecutor.getPlantCommands() != null) {
          com.eteks.sweethome3d.model.Content model = this.commandExecutor.getPlantCommands().getPictureModel(piece, xray, season);
          if (model != null) {
            drawnPiece.setModel(model);
            changed++;
          }
        }
      }
      // Walls and rooms given by their ids
      int index = 0;
      List<Wall> drawnWalls = new ArrayList<Wall>(drawnHome.getWalls());
      for (Wall wall : this.home.getWalls()) {
        if (hiddenIds.contains(HomeItemIds.getId(wall)) && index < drawnWalls.size()) {
          drawnHome.deleteWall(drawnWalls.get(index));
          hidden++;
        }
        index++;
      }
      index = 0;
      List<Room> drawnRooms = new ArrayList<Room>(drawnHome.getRooms());
      for (Room room : this.home.getRooms()) {
        if (hiddenIds.contains(HomeItemIds.getId(room)) && index < drawnRooms.size()) {
          drawnHome.deleteRoom(drawnRooms.get(index));
          hidden++;
        }
        index++;
      }
      hiddenDescription = " " + hidden + (hidden == 1 ? " item" : " items") + " left out of this picture"
          + (changed > 0 ? ", " + changed + " generated plants drawn " + (xray ? "pale and seen through" : "in " + season) : "")
          + (xray && changed == 0 ? " (no generated plant to see through: catalog trees are left out with exclude_tags)" : "")
          + (!Double.isNaN(cutaway) ? "; the walls aren't cut by \"cutaway\"" : "") + "." + sunText;
      if (!occludingPieces.isEmpty()) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < occludingPieces.size() && i < 12; i++) {
          ids.append(i > 0 ? ", " : "").append(HomeItemIds.getId(occludingPieces.get(i)));
        }
        hiddenDescription += " " + occludingPieces.size() + (occludingPieces.size() == 1 ? " piece" : " pieces")
            + " between the camera and the target " + (occludingPieces.size() == 1 ? "is" : "are") + " among them (" + ids
            + (occludingPieces.size() > 12 ? "..." : "") + "): \"occluders\":\"keep\" draws them.";
      }
    }
    long start = System.currentTimeMillis();
    Image3DRequest imageRequest = sweep != null ? null : new Image3DRequest(width, height, renderer,
        bool(arguments, "shadows", true), bool(arguments, "ambient_occlusion", true), highQuality,
        pose, level, allLevels, savedFile != null && width > 1000 ? 1000 : 0,
        (int)Math.max(0, Math.min(3600, number(arguments, "timeout", 0))));
    if (imageRequest != null && drawnHome != null) {
      imageRequest.setDrawnHome(drawnHome);
    }
    Image3D image = sweep != null
        ? drawSunSweep(provider, sweep, width, height, renderer,
            bool(arguments, "shadows", true), bool(arguments, "ambient_occlusion", true), highQuality,
            pose, level, allLevels, (int)Math.max(0, Math.min(3600, number(arguments, "timeout", 0))))
        : provider.get3DImage(imageRequest);
    if (image != null && image.getImage() == null && image.getDescription() != null) {
      return toolResult(image.getDescription(), true);
    }
    if (image == null || image.getImage() == null) {
      return toolResult("The home can't be rendered in 3D here.", true);
    }
    String description = (image.getDescription() != null ? image.getDescription() + ", " : "")
        + (sweep != null && !sweep.hours ? sweep.times.length + " pictures of " : "")
        + width + " x " + height + " pixels, in " + Math.round((System.currentTimeMillis() - start) / 100.) / 10. + " s. "
        + "Seen from: " + viewDescription + ". "
        + (allLevels ? "All levels." : "Levels above "
            + (selectedLevel != null ? "\"" + selectedLevel.getName() + "\"" : "the selected one") + " left out.")
        + hiddenDescription;
    if (sweep != null) {
      description += "\n" + sweep.text;
    }
    String storeAs = string(arguments, "store_as");
    if (storeAs != null) {
      // Kept in the points of view of the home, to draw the same view again after changes
      if (!isEditingAllowed()) {
        description += " The point of view wasn't stored: editing is disabled in Sweet Home 3D.";
      } else {
        Camera homeCamera = this.home.getCamera();
        Camera storedCamera = (pose != null
            ? (pose.isAerial() ? this.home.getTopCamera() : this.home.getObserverCamera()) : homeCamera).clone();
        if (pose != null) {
          storedCamera.setX(pose.getX());
          storedCamera.setY(pose.getY());
          storedCamera.setZ(pose.getZ());
          storedCamera.setYaw(pose.getYaw());
          storedCamera.setPitch(pose.getPitch());
          if (pose.getFieldOfView() > 0) {
            storedCamera.setFieldOfView(pose.getFieldOfView());
          }
        }
        storedCamera.setName(storeAs);
        List<Camera> storedCameras = new ArrayList<Camera>(this.home.getStoredCameras());
        boolean replaced = false;
        for (int i = storedCameras.size() - 1; i >= 0; i--) {
          if (storeAs.equalsIgnoreCase(storedCameras.get(i).getName())) {
            storedCameras.remove(i);
            replaced = true;
          }
        }
        storedCameras.add(storedCamera);
        this.home.setStoredCameras(storedCameras);
        description += " Point of view " + (replaced ? "replaced" : "stored") + " as \"" + storeAs
            + "\": draw it again with \"point_of_view\":\"" + storeAs + "\".";
      }
    }
    byte [] composedImage = null;
    String compareWith = string(arguments, "compare_with") != null ? string(arguments, "compare_with") : photoPath;
    if (compareWith != null) {
      // The picture beside a photo, or under it, to check the home against it
      java.io.File photo = new java.io.File(compareWith.startsWith("~/")
          ? System.getProperty("user.home") + compareWith.substring(1) : compareWith);
      String mode = string(arguments, "compare_mode");
      boolean overlay = mode != null && (mode.toLowerCase().startsWith("over") || mode.toLowerCase().equals("blend"));
      if (!photo.isAbsolute() || !photo.isFile()) {
        return toolResult("\"compare_with\" must be the absolute path of a PNG or JPEG file: no file " + compareWith + ".", true);
      } else if (this.imageComposer == null) {
        return toolResult("Pictures can't be compared here.", true);
      }
      try {
        composedImage = this.imageComposer.compose(image.getImage(),
            AssistantGeneratedModel.readFile(photo, 30L * 1024 * 1024), overlay,
            (float)Math.max(0.05, Math.min(0.95, number(arguments, "compare_opacity", 0.5))));
      } catch (java.io.IOException ex) {
        return toolResult("Couldn't read " + compareWith + ": " + ex.getMessage(), true);
      }
      if (composedImage == null) {
        return toolResult(compareWith + " isn't a PNG or JPEG image that can be read.", true);
      }
      description += overlay
          ? " The photo " + photo.getName() + " is drawn over the picture, turned upright and stretched to its size."
          : " Left: the home. Right: the photo " + photo.getName() + ", turned upright as its camera held it.";
    }
    if (savedFile != null) {
      try {
        java.io.OutputStream out = new java.io.FileOutputStream(savedFile);
        try {
          out.write(composedImage != null ? composedImage : image.getImage());
        } finally {
          out.close();
        }
      } catch (java.io.IOException ex) {
        return toolResult("Couldn't write " + saveTo + ": " + ex.getMessage(), true);
      }
      description = "Saved to " + savedFile.getAbsolutePath() + ". " + description;
    }
    Map<String, Object> result = imageResult(composedImage != null ? composedImage
        : savedFile != null && image.getPreview() != null ? image.getPreview() : image.getImage());
    ((List<Object>)result.get("content")).add(textContent(description));
    return result;
  }

  private String get3DInfo() {
    StringBuilder info = new StringBuilder();
    info.append("Camera of the home: ").append(HomeViewpoints.describeCamera(this.home)).append(".\n");
    info.append("Coordinates are in centimeters, x to the right and y downward in the plan (south); a yaw of 0 looks toward +y.\n");
    List<Camera> storedCameras = this.home.getStoredCameras();
    if (storedCameras.isEmpty()) {
      info.append("Stored points of view: none (the store_point_of_view command keeps the current camera).\n");
    } else {
      info.append("Stored points of view (get_3d_image \"point_of_view\", or the set_camera command):\n");
      for (Camera camera : storedCameras) {
        info.append("- \"").append(camera.getName()).append("\": ")
            .append(camera instanceof ObserverCamera ? "virtual visitor" : "aerial view")
            .append(" at x ").append(Math.round(camera.getX())).append(", y ").append(Math.round(camera.getY()))
            .append(", height ").append(Math.round(camera.getZ())).append(" cm\n");
      }
    }
    List<Level> levels = this.home.getLevels();
    if (levels.isEmpty()) {
      info.append("Levels: a single floor.\n");
    } else {
      info.append("Levels (").append(this.home.getEnvironment().isAllLevelsVisible()
          ? "the 3D view of the user shows all of them"
          : "the 3D view of the user shows the selected one and the ones under it").append("):\n");
      for (Level level : levels) {
        info.append("- \"").append(level.getName()).append("\", floor at ").append(Math.round(level.getElevation()))
            .append(" cm, height ").append(Math.round(level.getHeight())).append(" cm")
            .append(level == this.home.getSelectedLevel() ? ", selected" : "")
            .append(level.isViewable() ? "" : ", not viewable").append('\n');
      }
    }
    info.append("Views of get_3d_image: current, ").append(HomeViewpoints.AERIAL_VIEWS.toString().replace("[", "").replace("]", ""))
        .append(" (the whole home or a target entirely in sight), ").append(HomeViewpoints.INSIDE_VIEW)
        .append(" (in a corner of a target room, in front of a target piece or wall).\n");
    ImageProvider provider = this.imageProvider;
    String rendererInfo = provider != null ? provider.get3DInfo() : null;
    info.append(rendererInfo != null ? rendererInfo : "Renderers: no 3D picture is available here.");
    return info.toString();
  }

  private Map<String, Object> getCatalogImages(Map<String, Object> arguments) {
    ImageProvider provider = this.imageProvider;
    List<String> names = new ArrayList<String>();
    if (arguments.get("names") instanceof List) {
      for (Object name : (List<?>)arguments.get("names")) {
        if (name instanceof String && ((String)name).trim().length() > 0) {
          names.add(((String)name).trim());
        }
      }
    } else if (string(arguments, "names") != null) {
      names.add(string(arguments, "names"));
    } else if (string(arguments, "name") != null) {
      names.add(string(arguments, "name"));
    }
    if (names.isEmpty()) {
      return toolResult("The \"names\" argument is required: an array of exact catalog names.", true);
    }
    boolean textures = bool(arguments, "textures", false);
    List<Object> contents = new ArrayList<Object>();
    List<String> missing = new ArrayList<String>();
    int count = 0;
    for (String name : names) {
      if (count == 8) {
        contents.add(textContent("Only the 8 first names were shown."));
        break;
      }
      byte [] image = provider != null ? provider.getCatalogImage(name, textures) : null;
      if (image == null) {
        missing.add(name);
      } else {
        contents.add(textContent((textures ? "Texture \"" : "\"") + name + "\":"));
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("type", "image");
        content.put("data", java.util.Base64.getEncoder().encodeToString(image));
        content.put("mimeType", "image/png");
        contents.add(content);
        count++;
      }
    }
    if (!missing.isEmpty()) {
      contents.add(textContent("No picture for " + missing + ": use the exact names given by "
          + (textures ? "search_textures" : "search_catalog or list_catalog") + "."));
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("content", contents);
    result.put("isError", Boolean.valueOf(count == 0));
    return result;
  }

  private static String formatMeters(double centimeters) {
    return String.format(java.util.Locale.US, "%.2f m", centimeters / 100);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getElevationImage(Map<String, Object> arguments) {
    ImageProvider provider = this.imageProvider;
    if (provider == null) {
      return toolResult("The home can't be rendered in 3D here.", true);
    }
    String side = string(arguments, "side") != null ? string(arguments, "side").toLowerCase() : "";
    if (!"south".equals(side) && !"north".equals(side) && !"east".equals(side) && !"west".equals(side)) {
      return toolResult("\"side\" is south, north, east or west: the side the building is seen from.", true);
    }
    int width = (int)Math.max(300, Math.min(2000, number(arguments, "width", 1200)));
    int height = (int)Math.max(200, Math.min(1500, number(arguments, "height", 700)));
    String saveTo = string(arguments, "save_to");
    java.io.File savedFile = null;
    if (saveTo != null) {
      savedFile = new java.io.File(saveTo);
      if (!isEditingAllowed()) {
        return toolResult("Writing a file needs the permission to change things.", true);
      } else if (!savedFile.isAbsolute() || !savedFile.getName().toLowerCase().endsWith(".png")
          || savedFile.getParentFile() == null || !savedFile.getParentFile().isDirectory()) {
        return toolResult("\"save_to\" must be the absolute path of a .png file in a folder that exists.", true);
      } else if (savedFile.exists() && !bool(arguments, "replace", false)) {
        return toolResult(savedFile + " exists: give \"replace\":true to replace it.", true);
      }
    }
    // The building: its walls, and its roofs and what was built on them
    List<Selectable> building = new ArrayList<Selectable>(this.home.getWalls());
    float [] wallBounds = HomeViewpoints.getBounds(this.home, building, null);
    if (wallBounds == null) {
      return toolResult("This home has no wall to draw an elevation of.", true);
    }
    for (com.eteks.sweethome3d.model.HomePieceOfFurniture piece : this.home.getFurniture()) {
      String structure = piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY);
      if (piece.isVisible() && ("roof".equals(structure) || "dormer".equals(structure) || "custom".equals(structure))) {
        building.add(piece);
      }
    }
    float [] bounds = HomeViewpoints.getBounds(this.home, building, null);
    boolean alongX = "south".equals(side) || "north".equals(side);
    double left = alongX ? bounds [0] : bounds [1];
    double right = alongX ? bounds [3] : bounds [4];
    double bottom = Math.min(0, bounds [2]);
    double top = bounds [5];
    double buildingWidth = right - left;
    double buildingHeight = top - bottom;
    // Centimeters for a pixel, with room around the building for its dimensions
    double scale = Math.max(buildingWidth * 1.3 / width, buildingHeight * 1.4 / height);
    double center = (left + right) / 2;
    double middle = (bottom + top) / 2 - 0.04 * height * scale;
    // Seen from so far that lines that go away from the eye stay parallel to within 2 %
    double depth = alongX ? bounds [4] - bounds [1] : bounds [3] - bounds [0];
    double distance = Math.max(depth, Math.max(buildingWidth, buildingHeight)) * 50;
    float fieldOfView = (float)(2 * Math.atan(scale * width / 2 / distance));
    float x = (float)("east".equals(side) ? bounds [3] + distance : "west".equals(side) ? bounds [0] - distance : center);
    float y = (float)("south".equals(side) ? bounds [4] + distance : "north".equals(side) ? bounds [1] - distance : center);
    float yaw = (float)Math.toRadians("south".equals(side) ? 180 : "north".equals(side) ? 0 : "east".equals(side) ? 90 : 270);
    HomeViewpoints.Pose pose = new HomeViewpoints.Pose(x, y, (float)middle, yaw, 0, fieldOfView, true);
    Image3D image = provider.get3DImage(new Image3DRequest(width, height, Image3DRequest.OPENGL, true, true, false,
        pose, null, true, 0));
    if (image == null || image.getImage() == null) {
      return toolResult(image != null && image.getDescription() != null ? image.getDescription()
          : "The elevation couldn't be drawn here.", true);
    }
    byte [] picture = image.getImage();
    // Where the walls of the building stand in the picture
    double wallLeft = alongX ? wallBounds [0] : wallBounds [1];
    double wallRight = alongX ? wallBounds [3] : wallBounds [4];
    // In the pictures from the north and from the east, the plan is seen mirrored
    boolean mirrored = "north".equals(side) || "east".equals(side);
    double pixelLeft = width / 2. + (mirrored ? center - wallRight : wallLeft - center) / scale;
    double pixelRight = width / 2. + (mirrored ? center - wallLeft : wallRight - center) / scale;
    StringBuilder text = new StringBuilder("Elevation seen from the " + side + ", " + width + " x " + height + " pixels, "
        + String.format(java.util.Locale.US, "%.2f", scale) + " cm for a pixel. The walls are " + formatMeters(wallRight - wallLeft)
        + " wide on this side, the building " + formatMeters(buildingWidth) + " with its roofs; its top is "
        + formatMeters(top) + " above the floor at the elevation 0.");
    ImageAnnotator annotator = this.imageAnnotator;
    if (bool(arguments, "dimensions", true) && annotator != null) {
      List<float []> lines = new ArrayList<float []>();
      List<String> texts = new ArrayList<String>();
      List<float []> places = new ArrayList<float []>();
      float groundY = (float)(height / 2. - (0 - middle) / scale);
      // The width of the walls, under the ground line
      float widthY = Math.min(height - 22, groundY + 34);
      lines.add(new float [] {(float)pixelLeft, widthY, (float)pixelRight, widthY});
      lines.add(new float [] {(float)pixelLeft, widthY - 7, (float)pixelLeft, widthY + 7});
      lines.add(new float [] {(float)pixelRight, widthY - 7, (float)pixelRight, widthY + 7});
      texts.add(formatMeters(wallRight - wallLeft));
      places.add(new float [] {(float)(pixelLeft + pixelRight) / 2, widthY + 13});
      // The heights, at the left of the building
      float axisX = (float)Math.max(46, width / 2. - buildingWidth / 2 / scale - 30);
      java.util.TreeMap<Float, String> heights = new java.util.TreeMap<Float, String>();
      heights.put(Float.valueOf(0), "0.00");
      for (Level level : this.home.getLevels()) {
        if (level.isViewable() && level.getElevation() > 1 && level.getElevation() < top - 1) {
          boolean used = false;
          for (com.eteks.sweethome3d.model.Wall wall : this.home.getWalls()) {
            used |= wall.getLevel() == level;
          }
          if (used) {
            heights.put(Float.valueOf(level.getElevation()), String.format(java.util.Locale.US, "+%.2f", level.getElevation() / 100));
          }
        }
      }
      heights.put(Float.valueOf((float)top), String.format(java.util.Locale.US, "+%.2f", top / 100));
      float topY = (float)(height / 2. - (top - middle) / scale);
      lines.add(new float [] {axisX, groundY, axisX, topY});
      text.append(" Heights drawn at the left, in meters above that floor:");
      for (Map.Entry<Float, String> entry : heights.entrySet()) {
        float markY = (float)(height / 2. - (entry.getKey() - middle) / scale);
        lines.add(new float [] {axisX - 6, markY, axisX + 6, markY});
        texts.add(entry.getValue());
        places.add(new float [] {axisX - 26, markY});
        text.append(' ').append(entry.getValue());
      }
      text.append('.');
      byte [] annotated = annotator.annotate(picture, lines.toArray(new float [lines.size()][]),
          texts.toArray(new String [texts.size()]), places.toArray(new float [places.size()][]));
      if (annotated != null) {
        picture = annotated;
      }
    }
    if (savedFile != null) {
      try {
        java.io.OutputStream out = new java.io.FileOutputStream(savedFile);
        try {
          out.write(picture);
        } finally {
          out.close();
        }
        text.insert(0, "Saved to " + savedFile.getAbsolutePath() + ". ");
      } catch (java.io.IOException ex) {
        return toolResult("Couldn't write " + saveTo + ": " + ex.getMessage(), true);
      }
    }
    Map<String, Object> result = imageResult(picture);
    ((List<Object>)result.get("content")).add(textContent(text.toString()));
    return result;
  }

  /**
   * Returns the text or the whole number given for an argument, or <code>null</code>.
   */
  private static String token(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (value instanceof Number) {
      return String.valueOf(((Number)value).longValue());
    }
    return value instanceof String && ((String)value).trim().length() > 0 ? ((String)value).trim() : null;
  }

  private String describeHistory() {
    if (this.homeController == null) {
      return "No history here.";
    }
    List<String> undoNames = this.homeController.getUndoHistory(30);
    List<String> redoNames = this.homeController.getRedoHistory(30);
    StringBuilder text = new StringBuilder();
    if (undoNames.isEmpty()) {
      text.append("Nothing to undo.");
    } else {
      text.append("Changes undo would revert, the most recent first (undo with \"steps\":N reverts the N first):");
      for (int i = 0; i < undoNames.size(); i++) {
        text.append('\n').append(i + 1).append(". ").append(undoNames.get(i));
      }
    }
    if (!redoNames.isEmpty()) {
      text.append("\nChanges redo would apply again, the next one first:");
      for (int i = 0; i < redoNames.size(); i++) {
        text.append('\n').append(i + 1).append(". ").append(redoNames.get(i));
      }
    }
    if (this.commandExecutor.isGroupOpen()) {
      text.append("\nA group of calls is open (begin_group)"
          + (this.commandExecutor.getGroupName() != null ? ", named \"" + this.commandExecutor.getGroupName() + "\"" : "")
          + ": its changes count as one step once ended.");
    }
    text.append(this.home.isModified() ? "\nThe home has changes that aren't saved in its file." : "\nThe home is saved.");
    return text.toString();
  }

  private String describeHomes() {
    if (this.homeController == null) {
      return "Only this home is known here.";
    }
    List<Home> homes = this.homeController.getOpenHomes();
    if (!homes.contains(this.home)) {
      homes.add(this.home);
    }
    StringBuilder text = new StringBuilder("Homes open in Sweet Home 3D:");
    for (int i = 0; i < homes.size(); i++) {
      Home openHome = homes.get(i);
      text.append('\n').append(i + 1).append(". ").append(openHome.getName() != null ? openHome.getName() : "(never saved)")
          .append(openHome.isModified() ? ", with unsaved changes" : "")
          .append(openHome == this.home ? "  <- the home of this connection" : "");
    }
    return text.toString();
  }

  private Home findOpenHome(String token) {
    List<Home> homes = this.homeController.getOpenHomes();
    try {
      int index = Integer.parseInt(token.trim());
      return index >= 1 && index <= homes.size() ? homes.get(index - 1) : null;
    } catch (NumberFormatException ex) {
      // A name
    }
    Home match = null;
    for (Home openHome : homes) {
      if (openHome.getName() != null && (openHome.getName().equals(token.trim())
          || new java.io.File(openHome.getName()).getName().toLowerCase().contains(token.trim().toLowerCase()))) {
        if (match != null) {
          return null;
        }
        match = openHome;
      }
    }
    return match;
  }

  private Map<String, Object> compareVersions(Map<String, Object> arguments) {
    if (this.homeController == null) {
      return toolResult("Versions aren't available here.", true);
    }
    java.io.File version = HomeVersions.find(this.home, token(arguments, "version"));
    if (version == null) {
      return toolResult("No version, or several, match \"" + token(arguments, "version") + "\". "
          + HomeVersions.describe(this.home), true);
    }
    java.io.File otherVersion = null;
    if (token(arguments, "with") != null) {
      otherVersion = HomeVersions.find(this.home, token(arguments, "with"));
      if (otherVersion == null) {
        return toolResult("No version, or several, match \"" + token(arguments, "with") + "\". "
            + HomeVersions.describe(this.home), true);
      }
    }
    try {
      Home oldHome = this.homeController.readHomeVersion(version);
      Home newHome = otherVersion != null ? this.homeController.readHomeVersion(otherVersion) : this.home;
      String text = "From the version \"" + HomeVersions.getLabel(version) + "\" to "
          + (otherVersion != null ? "the version \"" + HomeVersions.getLabel(otherVersion) + "\"" : "the home as it is now") + ":\n"
          + HomeVersions.compare(oldHome, newHome) + "\n" + HomeVersions.compareItems(oldHome, newHome, 40)
          + AssistantTerrainCommands.compareSites(oldHome, newHome);
      ImageProvider provider = this.imageProvider;
      ImageComposer composer = this.imageComposer;
      if (bool(arguments, "picture", false) && provider != null && composer != null) {
        String view = string(arguments, "view") != null ? string(arguments, "view") : "south_east";
        HomeViewpoints.Pose pose;
        try {
          pose = HomeViewpoints.getPose(this.home, view, new ArrayList<com.eteks.sweethome3d.model.Selectable>(), null, 700f / 440, 0);
        } catch (RuntimeException ex) {
          return toolResult(text + "\nNo picture: " + ex.getMessage(), false);
        }
        byte [][] images = new byte [2][];
        Home [] drawnHomes = {oldHome, otherVersion != null ? newHome : null};
        for (int i = 0; i < 2; i++) {
          Image3DRequest request = new Image3DRequest(700, 440, Image3DRequest.AUTO, true, true, false, pose, null, true, 0);
          request.setDrawnHome(drawnHomes [i]);
          Image3D image = provider.get3DImage(request);
          images [i] = image != null ? image.getImage() : null;
        }
        byte [] composed = images [0] != null && images [1] != null ? composer.compose(images [0], images [1], false, 1) : null;
        if (composed != null) {
          Map<String, Object> result = imageResult(composed);
          ((List<Object>)result.get("content")).add(textContent("Left: the version \"" + HomeVersions.getLabel(version)
              + "\". Right: " + (otherVersion != null ? "the version \"" + HomeVersions.getLabel(otherVersion) + "\"" : "the home now")
              + ".\n" + text));
          return result;
        }
        text += "\nThe picture couldn't be drawn here.";
      }
      return toolResult(text, false);
    } catch (Exception ex) {
      return toolResult("Couldn't read the version: " + ex.getMessage(), true);
    }
  }

  /**
   * Runs the tools that write files, open homes or start a visit.
   */
  private Map<String, Object> callFileTool(String name, Map<String, Object> arguments) throws Exception {
    HomeOperations operations = this.homeOperations;
    if ("save_version".equals(name)) {
      String label = string(arguments, "label");
      for (String alias : new String [] {"name", "title", "description", "message"}) {
        if (label == null || label.trim().length() == 0) {
          label = string(arguments, alias);
        }
      }
      java.io.File version = this.homeController.saveHomeVersion(label);
      return toolResult("Saved the version \"" + HomeVersions.getLabel(version) + "\" (" + version.getPath()
          + "). The home and its file didn't change. compare_versions and restore_version use it.", false);
    } else if ("restore_version".equals(name)) {
      java.io.File version = HomeVersions.find(this.home, token(arguments, "version"));
      if (version == null) {
        return toolResult("No version, or several, match \"" + token(arguments, "version") + "\". "
            + HomeVersions.describe(this.home), true);
      }
      this.commandExecutor.endGroup();
      String kept = "";
      if (bool(arguments, "keep_current", true)) {
        java.io.File current = this.homeController.saveHomeVersion("before restore");
        kept = " The state before it was saved as the version \"" + HomeVersions.getLabel(current) + "\".";
      }
      int count = this.homeController.restoreHomeVersion(version);
      this.commandExecutor.clearDescribedItems();
      return toolResult("Restored the version \"" + HomeVersions.getLabel(version) + "\": the home now holds its "
          + count + " items and its levels. Undo reverts it. The file of the home wasn't written." + kept
          + " Call get_home: ids are the ones of that version.", false);
    } else if ("save_home".equals(name)) {
      String copyTo = string(arguments, "copy_to");
      if (copyTo != null) {
        java.io.File file = new java.io.File(copyTo);
        if (!file.isAbsolute() || !file.getName().toLowerCase().endsWith(".sh3d")) {
          return toolResult("\"copy_to\" must be the absolute path of a .sh3d file.", true);
        } else if (file.getParentFile() == null || !file.getParentFile().isDirectory()) {
          return toolResult("The folder " + file.getParent() + " doesn't exist.", true);
        } else if (file.exists() && !(bool(arguments, "replace", false) && isSavingAllowed())) {
          return toolResult(file + " exists. Replacing a file needs \"replace\":true and the permission "
              + "\"Allow saving\" in Sweet Home 3D (Tools > Claude MCP server...); or give another name.", true);
        } else if (file.getPath().equals(this.home.getName())) {
          return toolResult("That is the file of the home: call save_home without copy_to.", true);
        }
        this.homeController.saveHomeCopy(file.getPath());
        return toolResult("Wrote a copy of the home to " + file.getPath() + ". The home keeps its own file"
            + (this.home.isModified() ? " and its unsaved changes" : "") + ". open_home opens the copy.", false);
      } else if (!isSavingAllowed()) {
        return toolResult("Saving the home in its file isn't allowed: the user didn't tick \"Allow saving\" in "
            + "Sweet Home 3D (Tools > Claude MCP server...). save_version keeps a checkpoint without it, and "
            + "save_home with copy_to writes a copy in a new file.", true);
      } else if (this.home.getName() == null) {
        return toolResult("This home has no file yet: the user must save it once (File > Save as...), "
            + "or call save_home with copy_to.", true);
      }
      this.commandExecutor.endGroup();
      this.homeController.saveHomeNow(null);
      return toolResult("Saved the home in " + this.home.getName() + ".", false);
    } else if ("open_home".equals(name) || "new_home".equals(name) || "switch_home".equals(name)) {
      if (operations == null) {
        return toolResult("Homes can't be opened here.", true);
      }
      Home otherHome;
      if ("new_home".equals(name)) {
        otherHome = this.homeController.createHomeNow();
      } else if ("open_home".equals(name)) {
        String path = string(arguments, "path");
        if (path == null || !new java.io.File(path).isAbsolute() || !new java.io.File(path).isFile()) {
          return toolResult("\"path\" must be the absolute path of a .sh3d file that exists.", true);
        }
        otherHome = this.homeController.openHomeNow(path);
      } else {
        otherHome = token(arguments, "home") != null ? findOpenHome(token(arguments, "home")) : null;
        if (otherHome == null) {
          return toolResult("No open home, or several, match \"" + token(arguments, "home") + "\". " + describeHomes(), true);
        }
      }
      if (otherHome == this.home) {
        return toolResult("This connection already reads and edits that home.", false);
      }
      this.commandExecutor.endGroup();
      String served = operations.serve(otherHome);
      return toolResult(("new_home".equals(name) ? "Created an empty home. " : "open_home".equals(name)
          ? "Opened " + otherHome.getName() + ". " : "") + served
          + " The ids you know belong to the other home: call get_home.", false);
    } else if ("export_home".equals(name)) {
      if (operations == null) {
        return toolResult("The home can't be exported here.", true);
      }
      String format = string(arguments, "format") != null ? string(arguments, "format").toLowerCase() : "";
      String path = string(arguments, "path");
      boolean folder = "web_page".equals(format) || "panorama".equals(format);
      if (!folder && !"pdf".equals(format) && !"svg".equals(format) && !"obj".equals(format) && !"csv".equals(format)
          && !"glb".equals(format)) {
        return toolResult("\"format\" is pdf, svg, obj, glb, csv, web_page or panorama.", true);
      } else if (path == null || !new java.io.File(path).isAbsolute()) {
        return toolResult("\"path\" must be an absolute path.", true);
      }
      java.io.File target = new java.io.File(path);
      if (folder) {
        if (!target.isDirectory()) {
          return toolResult("For " + format + ", \"path\" is an existing folder: a new folder is created in it.", true);
        }
      } else {
        if (!target.getName().toLowerCase().endsWith("." + format)) {
          return toolResult("The name of the file must end with ." + format + ".", true);
        } else if (target.getParentFile() == null || !target.getParentFile().isDirectory()) {
          return toolResult("The folder " + target.getParent() + " doesn't exist.", true);
        } else if (target.exists() && !bool(arguments, "replace", false)) {
          return toolResult(target + " exists: give \"replace\":true to replace it, or another name.", true);
        }
      }
      if ("csv".equals(format)) {
        // A table with ids, places and a unit of its own, whatever the furniture list of the window shows
        try {
          return toolResult(HomeFurnitureCsvWriter.write(this.home, target,
              arguments.get("columns") instanceof List ? (List<?>)arguments.get("columns") : null, string(arguments, "units")), false);
        } catch (java.io.IOException ex) {
          return toolResult("The furniture list couldn't be written: " + ex.getMessage(), true);
        }
      }
      if ("glb".equals(format)) {
        // The OBJ export written in a folder of its own, then turned into one binary glTF file
        java.io.File folderOfObj = java.nio.file.Files.createTempDirectory("sh3dglb").toFile();
        try {
          java.io.File obj = new java.io.File(folderOfObj, "home.obj");
          operations.export("obj", obj, arguments);
          HomeGeoreference georeference = HomeGeoreference.get(this.home);
          String extras = georeference != null
              ? "{\"georeference\":{\"latitude\":" + georeference.getLatitude() + ",\"longitude\":" + georeference.getLongitude()
                  + ",\"plan_x_cm\":" + georeference.getX() + ",\"plan_y_cm\":" + georeference.getY() + ",\"north_clockwise_from_plan_top_deg\":"
                  + georeference.getRotation() + ",\"note\":\"x of the model is the x of the plan and z its y, in meters\"}}"
              : null;
          int triangles = AssistantGltfWriter.convert(obj, target, extras);
          return toolResult("Wrote " + target + ": a binary glTF 2.0 file of " + triangles + " triangles, in meters, y up"
              + (georeference != null ? ", with the georeference of the home in the extras of its scene" : "") + ".", false);
        } finally {
          java.io.File [] files = folderOfObj.listFiles();
          if (files != null) {
            for (java.io.File file : files) {
              file.delete();
            }
          }
          folderOfObj.delete();
        }
      }
      return toolResult(operations.export(format, target, arguments), false);
    } else if ("add_note".equals(name)) {
      return addNote(arguments);
    } else {
      if (operations == null) {
        return toolResult("A visit can't be started here.", true);
      }
      String mode = string(arguments, "mode") != null ? string(arguments, "mode").toLowerCase() : "game";
      if (!"game".equals(mode) && !"walk".equals(mode)) {
        return toolResult("\"mode\" is game or walk.", true);
      }
      return toolResult(operations.startVisit(mode), false);
    }
  }

  private Map<String, Object> addNote(Map<String, Object> arguments) {
    String text = string(arguments, "text");
    if (text == null) {
      return toolResult("The \"text\" argument is required.", true);
    }
    String id = string(arguments, "id");
    com.eteks.sweethome3d.model.HomeObject item = null;
    String target = "ground";
    String itemName = null;
    Float x = arguments.get("x") instanceof Number ? Float.valueOf(((Number)arguments.get("x")).floatValue()) : null;
    Float y = arguments.get("y") instanceof Number ? Float.valueOf(((Number)arguments.get("y")).floatValue()) : null;
    Level level = this.home.getSelectedLevel();
    if (id != null) {
      item = HomeItemIds.find(this.home, id);
      if (item instanceof com.eteks.sweethome3d.model.HomePieceOfFurniture) {
        com.eteks.sweethome3d.model.HomePieceOfFurniture piece = (com.eteks.sweethome3d.model.HomePieceOfFurniture)item;
        target = piece instanceof com.eteks.sweethome3d.model.HomeDoorOrWindow ? "door" : "furniture";
        itemName = piece.getName();
        level = piece.getLevel();
        if (x == null || y == null) {
          x = piece.getX();
          y = piece.getY();
        }
      } else if (item instanceof com.eteks.sweethome3d.model.Wall) {
        com.eteks.sweethome3d.model.Wall wall = (com.eteks.sweethome3d.model.Wall)item;
        target = "wall";
        level = wall.getLevel();
        if (x == null || y == null) {
          x = (wall.getXStart() + wall.getXEnd()) / 2;
          y = (wall.getYStart() + wall.getYEnd()) / 2;
        }
      } else if (item instanceof com.eteks.sweethome3d.model.Room) {
        com.eteks.sweethome3d.model.Room room = (com.eteks.sweethome3d.model.Room)item;
        target = "room";
        itemName = room.getName();
        level = room.getLevel();
        if (x == null || y == null) {
          x = room.getXCenter();
          y = room.getYCenter();
        }
      } else {
        return toolResult("No piece of furniture, wall or room has the id " + id + ".", true);
      }
    } else if (x == null || y == null) {
      return toolResult("Give the item of the note with \"id\", or its place with \"x\" and \"y\".", true);
    }
    float height = (float)number(arguments, "height", 120) + (level != null ? level.getElevation() : 0);
    String noteId = HomeReviewNotes.addNote(this.home, text, target, item, itemName, x, y, Float.valueOf(height),
        level != null ? level.getName() : null);
    if (noteId == null) {
      return toolResult("The home can't hold more notes: delete some with update_note.", true);
    }
    return toolResult("Added the note " + noteId + " on " + (id != null ? id : "the place (" + Math.round(x) + ", " + Math.round(y) + ")")
        + "; " + HomeReviewNotes.getOpenCount(this.home) + " notes are open. The user reads it in Tools > Review notes..., "
        + "the walk-through and the game mode.", false);
  }

  private static Map<String, Object> textContent(String text) {
    Map<String, Object> content = new LinkedHashMap<String, Object>();
    content.put("type", "text");
    content.put("text", text);
    return content;
  }

  private Map<String, Object> searchCatalog(Map<String, Object> arguments) {
    Object query = arguments.get("query");
    if (!(query instanceof String) || ((String)query).trim().length() == 0) {
      return toolResult("The \"query\" argument is required.", true);
    }
    boolean doorsAndWindows = Boolean.TRUE.equals(arguments.get("doors_and_windows"));
    List<CatalogMatch> matches =
        this.commandExecutor.searchCatalog((String)query, doorsAndWindows, MAX_SEARCH_RESULTS);
    if (matches.isEmpty()) {
      return toolResult("No catalog item matches \"" + ((String)query).trim() + "\".", false);
    }
    StringBuilder text = new StringBuilder();
    for (CatalogMatch match : matches) {
      text.append("- \"").append(match.piece.getName()).append("\" (").append(match.categoryName);
      if (match.piece.isDoorOrWindow()) {
        text.append(", door/window");
      }
      text.append(", ").append(Math.round(match.piece.getWidth())).append(" x ")
          .append(Math.round(match.piece.getDepth())).append(" x ")
          .append(Math.round(match.piece.getHeight())).append(" cm)\n");
    }
    return toolResult(text.toString(), false);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> applyCommands(Map<String, Object> arguments) {
    String expectedHome = string(arguments, "home");
    if (expectedHome != null && expectedHome.trim().length() > 0 && !isHomeNamed(expectedHome.trim())) {
      return toolResult("Nothing was done: these commands are meant for the home \"" + expectedHome.trim()
          + "\" but this connection reads and edits " + getHomeLabel() + ". " + describeHomes()
          + "\nswitch_home or open_home chooses the home of this connection.", true);
    }
    Object commandsValue = arguments.get("commands");
    int maxCount = AssistantCommandExecutor.MAX_COMMANDS_PER_TURN;
    if (arguments.get("file") instanceof String && ((String)arguments.get("file")).trim().length() > 0) {
      // A long list of commands written in a file rather than pasted in the call
      if (commandsValue instanceof List && !((List<?>)commandsValue).isEmpty()) {
        return toolResult("Give \"commands\" or \"file\", not both.", true);
      }
      java.io.File file = new java.io.File(((String)arguments.get("file")).trim());
      if (!file.isAbsolute() || !file.isFile()) {
        return toolResult("\"file\" must be the absolute path of a JSON file that exists.", true);
      }
      try {
        Object parsed = JSONParser.parse(new String(AssistantGeneratedModel.readFile(file, MAX_COMMANDS_FILE_LENGTH), "UTF-8"));
        commandsValue = parsed instanceof Map ? ((Map<?, ?>)parsed).get("commands") : parsed;
      } catch (java.io.IOException ex) {
        return toolResult("Couldn't read the file of commands: " + ex.getMessage(), true);
      } catch (IllegalArgumentException ex) {
        return toolResult("The file of commands isn't written in JSON: " + ex.getMessage(), true);
      }
      if (!(commandsValue instanceof List) || ((List<?>)commandsValue).isEmpty()) {
        return toolResult("The file must hold an array of commands, or an object with a \"commands\" array.", true);
      }
      maxCount = AssistantCommandExecutor.MAX_COMMANDS_PER_FILE;
    }
    if (!(commandsValue instanceof List) || ((List<?>)commandsValue).isEmpty()) {
      return toolResult("The \"commands\" argument must be a non-empty array of command objects "
          + "(or give \"file\", the path of a JSON file of commands).", true);
    }
    List<AssistantCommand> commands = new ArrayList<AssistantCommand>();
    for (Object item : (List<?>)commandsValue) {
      if (!(item instanceof Map) || ((Map<?, ?>)item).get("action") == null) {
        return toolResult("Each command must be an object with an \"action\" field.", true);
      }
      Map<String, Object> command = (Map<String, Object>)item;
      commands.add(new AssistantCommand(command.get("action").toString(), command));
    }
    this.commandExecutor.setStopOnError(Boolean.TRUE.equals(arguments.get("stop_on_error")));
    String summary;
    try {
      summary = this.commandExecutor.execute(commands, maxCount);
    } finally {
      this.commandExecutor.setStopOnError(false);
    }
    // An id that designates nothing is an error, even if other commands were applied
    return toolResult((summary != null
        ? summary
        : "No change was applied (unknown actions, or ids that don't match the last get_home).")
        // Ids start again in each home: the client sees which one its commands reached
        + "\nHome edited: " + getHomeLabel() + ".",
        summary == null || !this.commandExecutor.getUnresolvedIds().isEmpty());
  }

  /**
   * Returns <code>true</code> if <code>name</code> designates the home of this handler:
   * a part of the name of its file, or <code>unsaved</code> for a home never saved.
   */
  private boolean isHomeNamed(String name) {
    String homeName = this.home.getName();
    if (homeName == null) {
      String lowerName = name.toLowerCase();
      return "unsaved".equals(lowerName) || "never saved".equals(lowerName) || "(never saved)".equals(lowerName)
          || "new".equals(lowerName);
    }
    // The whole path, or a part of the name of the file
    return homeName.equalsIgnoreCase(name)
        || new java.io.File(name).getParent() == null
           && new java.io.File(homeName).getName().toLowerCase().indexOf(name.toLowerCase()) >= 0;
  }

  private static Map<String, Object> imageResult(byte [] image) {
    Map<String, Object> content = new LinkedHashMap<String, Object>();
    content.put("type", "image");
    content.put("data", java.util.Base64.getEncoder().encodeToString(image));
    content.put("mimeType", "image/png");
    List<Object> contents = new ArrayList<Object>();
    contents.add(content);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("content", contents);
    result.put("isError", Boolean.FALSE);
    return result;
  }

  private static Map<String, Object> toolResult(String text, boolean error) {
    Map<String, Object> content = new LinkedHashMap<String, Object>();
    content.put("type", "text");
    content.put("text", text);
    List<Object> contents = new ArrayList<Object>();
    contents.add(content);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("content", contents);
    result.put("isError", Boolean.valueOf(error));
    return result;
  }

  private static Map<String, Object> result(Object id, Map<String, Object> result) {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("result", result);
    return response;
  }

  private static Map<String, Object> error(Object id, int code, String message) {
    Map<String, Object> error = new LinkedHashMap<String, Object>();
    error.put("code", Integer.valueOf(code));
    error.put("message", message);
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("error", error);
    return response;
  }
}
