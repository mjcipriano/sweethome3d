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
        // A group of calls left open by a client that went away mustn't keep the changes of the user out of Undo
        this.commandExecutor.endIdleGroup(AssistantCommandExecutor.GROUP_IDLE_DELAY);
        // What the tools change isn't a change made by the user by hand
        this.changeJournal.setPaused(true);
        try {
          Map<String, Object> toolResult = callTool((String)name, arguments);
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
        + "Undo, also available through the undo tool.");
    return result;
  }

  private static boolean isTool(String name) {
    return "get_home".equals(name) || "list_catalog".equals(name) || "search_catalog".equals(name)
        || "search_textures".equals(name) || "check_home".equals(name) || "get_report".equals(name) || "get_plan_image".equals(name)
        || "get_3d_image".equals(name) || "get_3d_info".equals(name) || "get_catalog_image".equals(name)
        || "export_blender".equals(name)
        || "get_notes".equals(name) || "update_note".equals(name)
        || "apply_commands".equals(name) || "undo".equals(name) || "redo".equals(name)
        || "save_version".equals(name) || "list_versions".equals(name) || "compare_versions".equals(name)
        || "restore_version".equals(name) || "save_home".equals(name) || "list_homes".equals(name)
        || "open_home".equals(name) || "new_home".equals(name) || "switch_home".equals(name)
        || "export_home".equals(name) || "get_history".equals(name) || "get_changes".equals(name)
        || "add_note".equals(name) || "start_visit".equals(name);
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
        + "\"bbox\":[x1,y1,x2,y2]} (bbox in cm: what is inside that box). One line per item, up to 500");
    homeProperties.put("where", whereProperty);
    Map<String, Object> fieldsProperty = property("array",
        "With or without where: one short line per item with only these fields, among name, position, size, level, "
        + "elevation, angle, tags, type, area, points, color (default: name, position, size, level). The id is always given");
    fieldsProperty.put("items", property("string", "A field"));
    homeProperties.put("fields", fieldsProperty);
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
        + "\"look_at_x\":N,\"look_at_y\":N,\"look_at_height\":N}; heights are above the floor of the level"));
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
    tools.add(tool("apply_commands", "Edit home",
        "Edits the home open in Sweet Home 3D by running a list of commands as a single "
        + "undoable change, and returns a summary of what was applied or skipped. "
        + HomeAssistantContext.getCommandReference(),
        schema(commandsProperties), false));
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
    versionProperties.put("label", property("string", "A few words that describe this state of the home, e.g. \"before moving the kitchen\""));
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
        + "(Help > Claude MCP server...). With copy_to, writes a copy in a new file instead, which only needs the "
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
        + "obj: the 3D model with its materials; csv: the furniture list; web_page: a folder with the 3D visit for a browser; "
        + "panorama: a 360 degree picture from the place of the virtual visitor, with its page",
        "pdf", "svg", "obj", "csv", "web_page", "panorama"));
    exportProperties.put("path", property("string", "Absolute path of the file to write (pdf, svg, obj, csv), or of an existing folder that will receive a new folder (web_page, panorama)"));
    exportProperties.put("replace", property("boolean", "true to replace a file that exists"));
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
      return toolResult(this.commandExecutor.describeItems(where, fields), false);
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
    } else if ("get_3d_info".equals(name)) {
      return toolResult(get3DInfo(), false);
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
      return toolResult(HomeReviewNotes.describe(this.home, !Boolean.TRUE.equals(arguments.get("all"))), false);
    } else if ("get_plan_image".equals(name)) {
      return getPlanImage(arguments);
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
          + "Ask them to enable it in Sweet Home 3D (Help > Claude MCP server).", true);
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
    byte [] image = provider != null ? provider.getPlanImage(maxSize, level, area) : null;
    if (image == null) {
      return toolResult("No picture of the plan is available (the plan is empty or not displayed).", true);
    }
    Map<String, Object> result = imageResult(image);
    if (area != null) {
      ((List<Object>)result.get("content")).add(textContent("Plan from x " + Math.round(area [0]) + ", y " + Math.round(area [1])
          + " (top left) to x " + Math.round(area [0] + area [2]) + ", y " + Math.round(area [1] + area [3]) + " cm"
          + (level != null ? ", level \"" + level.getName() + "\"" : "") + "."));
    }
    return result;
  }

  private static Level getLevel(Selectable item) {
    if (item instanceof com.eteks.sweethome3d.model.Elevatable) {
      return ((com.eteks.sweethome3d.model.Elevatable)item).getLevel();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> get3DImage(Map<String, Object> arguments) {
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
            + "to this home (Help > Claude MCP server). Call get_3d_image without save_to.", true);
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
    if (arguments.get("camera") instanceof Map) {
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

    long start = System.currentTimeMillis();
    Image3D image = provider.get3DImage(new Image3DRequest(width, height, renderer,
        bool(arguments, "shadows", true), bool(arguments, "ambient_occlusion", true), highQuality,
        pose, level, allLevels, savedFile != null && width > 1000 ? 1000 : 0,
        (int)Math.max(0, Math.min(3600, number(arguments, "timeout", 0)))));
    if (image != null && image.getImage() == null && image.getDescription() != null) {
      return toolResult(image.getDescription(), true);
    }
    if (image == null || image.getImage() == null) {
      return toolResult("The home can't be rendered in 3D here.", true);
    }
    String description = (image.getDescription() != null ? image.getDescription() + ", " : "")
        + width + " x " + height + " pixels, in " + Math.round((System.currentTimeMillis() - start) / 100.) / 10. + " s. "
        + "Seen from: " + viewDescription + ". "
        + (allLevels ? "All levels." : "Levels above "
            + (selectedLevel != null ? "\"" + selectedLevel.getName() + "\"" : "the selected one") + " left out.");
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
    String compareWith = string(arguments, "compare_with");
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
          ? " The photo " + photo.getName() + " is drawn over the picture, stretched to its size."
          : " Left: the home. Right: the photo " + photo.getName() + ".";
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
      text.append("\nA group of calls is open (begin_group): its changes count as one step once ended.");
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
          + HomeVersions.compare(oldHome, newHome) + "\n" + HomeVersions.compareItems(oldHome, newHome, 40);
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
      java.io.File version = this.homeController.saveHomeVersion(string(arguments, "label"));
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
              + "\"Allow saving\" in Sweet Home 3D (Help > Claude MCP server...); or give another name.", true);
        } else if (file.getPath().equals(this.home.getName())) {
          return toolResult("That is the file of the home: call save_home without copy_to.", true);
        }
        this.homeController.saveHomeCopy(file.getPath());
        return toolResult("Wrote a copy of the home to " + file.getPath() + ". The home keeps its own file"
            + (this.home.isModified() ? " and its unsaved changes" : "") + ". open_home opens the copy.", false);
      } else if (!isSavingAllowed()) {
        return toolResult("Saving the home in its file isn't allowed: the user didn't tick \"Allow saving\" in "
            + "Sweet Home 3D (Help > Claude MCP server...). save_version keeps a checkpoint without it, and "
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
      if (!folder && !"pdf".equals(format) && !"svg".equals(format) && !"obj".equals(format) && !"csv".equals(format)) {
        return toolResult("\"format\" is pdf, svg, obj, csv, web_page or panorama.", true);
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
    String summary = this.commandExecutor.execute(commands, maxCount);
    // An id that designates nothing is an error, even if other commands were applied
    return toolResult(summary != null
        ? summary
        : "No change was applied (unknown actions, or ids that don't match the last get_home).",
        summary == null || !this.commandExecutor.getUnresolvedIds().isEmpty());
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
