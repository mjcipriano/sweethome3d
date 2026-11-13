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

import com.eteks.sweethome3d.model.Home;
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
   * Gives pictures of the home to this handler, which can't draw any itself.
   */
  public interface ImageProvider {
    /**
     * Returns a PNG image of the plan of the selected level no larger than
     * <code>maxSize</code> pixels, or <code>null</code> if it can't be drawn.
     */
    byte [] getPlanImage(int maxSize);
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
        return result(id, initialize(params));
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
        return result(id, callTool((String)name, arguments));
      } else {
        return error(id, METHOD_NOT_FOUND, "Method not found: " + method);
      }
    } catch (RuntimeException ex) {
      return error(id, -32603, "Internal error: " + ex);
    }
  }

  private Map<String, Object> initialize(Map<String, Object> params) {
    Object requestedVersion = params.get("protocolVersion");
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("protocolVersion", SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)
        ? requestedVersion : PROTOCOL_VERSION);
    Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
    capabilities.put("tools", new LinkedHashMap<String, Object>());
    result.put("capabilities", capabilities);
    Map<String, Object> serverInfo = new LinkedHashMap<String, Object>();
    serverInfo.put("name", "sweethome3d");
    serverInfo.put("title", "Sweet Home 3D");
    serverInfo.put("version", this.serverVersion);
    result.put("serverInfo", serverInfo);
    result.put("instructions",
        "Tools to read and edit the home open in Sweet Home 3D, an interior design application. "
        + "Call get_home first: it describes the levels, rooms, walls and furniture with the ids "
        + "(F# furniture, W# walls, R# rooms) used by apply_commands. Ids stay bound to the items "
        + "of the last get_home call, so call it again to get ids for items you added. "
        + "Furniture can only be added by its exact catalog name: use search_catalog or "
        + "list_catalog to find one. To extend the house use add_rectangular_room (room and walls "
        + "at once) and auto_roof; check_home reports plan defects and fix_home repairs them; "
        + "get_plan_image shows the plan. Every apply_commands call is one step of the application's "
        + "Undo, also available through the undo tool.");
    return result;
  }

  private static boolean isTool(String name) {
    return "get_home".equals(name) || "list_catalog".equals(name) || "search_catalog".equals(name)
        || "search_textures".equals(name) || "check_home".equals(name) || "get_plan_image".equals(name)
        || "apply_commands".equals(name) || "undo".equals(name) || "redo".equals(name);
  }

  /**
   * Returns the description of the tools of this server.
   */
  private List<Object> getTools() {
    List<Object> tools = new ArrayList<Object>();
    tools.add(tool("get_home", "Describe home",
        "Returns a text description of the home open in Sweet Home 3D: levels, rooms with their "
        + "areas, walls, furniture with positions and sizes, and the current selection. Each "
        + "item has an id (F# furniture, W# walls, R# rooms) to use with apply_commands. "
        + "Lengths in the description are in meters; command coordinates are in centimeters.",
        schema(null), true));
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
    tools.add(tool("check_home", "Check home",
        "Looks for defects in the plan: walls that end close to each other without being joined, "
        + "duplicated or empty items, doors and windows that aren't in a wall, furniture under the "
        + "floor, through the ceiling or outside every room, heavy 3D models. Each issue names its "
        + "items by id; those marked fixable are repaired by the fix_home command of apply_commands. "
        + "Like get_home, it binds the ids to the current items.",
        schema(null), true));
    Map<String, Object> imageProperties = new LinkedHashMap<String, Object>();
    imageProperties.put("max_size", property("number", "Largest side of the image in pixels (default 1000, at most 2000)"));
    tools.add(tool("get_plan_image", "Picture of the plan",
        "Returns a PNG picture of the 2D plan of the selected level (walls, rooms, furniture, "
        + "dimensions), to see the layout or check the result of an edit. x grows to the right "
        + "and y downward, as in the commands.",
        schema(imageProperties), true));
    Map<String, Object> commandsProperty = new LinkedHashMap<String, Object>();
    commandsProperty.put("type", "array");
    commandsProperty.put("description", "The commands to run in order, as objects with an \"action\" field");
    Map<String, Object> commandItems = new LinkedHashMap<String, Object>();
    commandItems.put("type", "object");
    commandsProperty.put("items", commandItems);
    Map<String, Object> commandsProperties = new LinkedHashMap<String, Object>();
    commandsProperties.put("commands", commandsProperty);
    tools.add(tool("apply_commands", "Edit home",
        "Edits the home open in Sweet Home 3D by running a list of commands as a single "
        + "undoable change, and returns a summary of what was applied or skipped. "
        + HomeAssistantContext.getCommandReference(),
        schema(commandsProperties, "commands"), false));
    tools.add(tool("undo", "Undo", "Undoes the last change made to the home.", schema(null), false));
    tools.add(tool("redo", "Redo", "Redoes the last undone change.", schema(null), false));
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
    if ("get_home".equals(name)) {
      String description = HomeAssistantContext.describeHome(this.home);
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
    } else if ("search_textures".equals(name)) {
      Object query = arguments.get("query");
      if (!(query instanceof String) || ((String)query).trim().length() == 0) {
        return toolResult("The \"query\" argument is required.", true);
      }
      return toolResult("Textures: " + this.commandExecutor.searchTextures((String)query, MAX_SEARCH_RESULTS), false);
    } else if ("check_home".equals(name)) {
      this.commandExecutor.snapshotDescribedItems();
      return toolResult(this.commandExecutor.checkHome(), false);
    } else if ("get_plan_image".equals(name)) {
      ImageProvider provider = this.imageProvider;
      int maxSize = arguments.get("max_size") instanceof Number
          ? Math.max(200, Math.min(2000, ((Number)arguments.get("max_size")).intValue())) : 1000;
      byte [] image = provider != null ? provider.getPlanImage(maxSize) : null;
      if (image == null) {
        return toolResult("No picture of the plan is available (the plan is empty or not displayed).", true);
      }
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
    } else if (!isEditingAllowed()) {
      return toolResult("Editing is disabled: the user didn't allow changes to this home. "
          + "Ask them to enable it in Sweet Home 3D (Help > Claude MCP server).", true);
    } else if ("apply_commands".equals(name)) {
      return applyCommands(arguments);
    } else if ("undo".equals(name)) {
      try {
        this.homeController.undo();
        return toolResult("Undone.", false);
      } catch (CannotUndoException ex) {
        return toolResult("Nothing to undo.", true);
      }
    } else {
      try {
        this.homeController.redo();
        return toolResult("Redone.", false);
      } catch (CannotRedoException ex) {
        return toolResult("Nothing to redo.", true);
      }
    }
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
    if (!(commandsValue instanceof List) || ((List<?>)commandsValue).isEmpty()) {
      return toolResult("The \"commands\" argument must be a non-empty array of command objects.", true);
    }
    List<AssistantCommand> commands = new ArrayList<AssistantCommand>();
    for (Object item : (List<?>)commandsValue) {
      if (!(item instanceof Map) || ((Map<?, ?>)item).get("action") == null) {
        return toolResult("Each command must be an object with an \"action\" field.", true);
      }
      Map<String, Object> command = (Map<String, Object>)item;
      commands.add(new AssistantCommand(command.get("action").toString(), command));
    }
    String summary = this.commandExecutor.execute(commands);
    return toolResult(summary != null
        ? summary
        : "No change was applied (unknown actions, or ids that don't match the last get_home).",
        summary == null);
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
