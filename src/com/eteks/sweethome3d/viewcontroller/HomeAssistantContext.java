/*
 * HomeAssistantContext.java 10 June 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ModelLOD;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * Builds a concise, plain-text description of a {@link Home} that can be given
 * to a language model so the design assistant can answer questions about the
 * current project. Lengths are reported in meters and areas in square meters
 * (Sweet Home 3D stores them in centimeters).
 * @author Sweet Home 3D
 */
public class HomeAssistantContext {
  /** Upper bound on the number of furniture items listed individually in the brief. */
  private static final int MAX_LISTED_FURNITURE = 80;
  private static final int MAX_FILTERED_FURNITURE = 500;
  /** Upper bound on the catalog item names listed per category in the brief. */
  private static final int MAX_LISTED_CATALOG_PIECES_PER_CATEGORY = 60;

  /** The assistant's role, shared by the chat panel and the live protocol tests. */
  public static final String ROLE_PROMPT =
      "You are a helpful interior design assistant embedded in Sweet Home 3D. "
      + "Answer the user's questions about their home design project clearly and concisely. ";

  private HomeAssistantContext() {
    // Utility class
  }

  /**
   * Builds the complete system prompt for an assistant request: the role, the
   * edit command protocol and catalog vocabulary when <code>withCommands</code>
   * is <code>true</code>, and the current project brief. Reads the home, so call
   * it on the event dispatch thread in the application.
   */
  public static String buildSystemPrompt(Home home, UserPreferences preferences, boolean withCommands) {
    StringBuilder builder = new StringBuilder(ROLE_PROMPT);
    if (withCommands) {
      builder.append(getCommandProtocol());
      String catalog = describeCatalog(preferences);
      if (catalog.length() > 0) {
        builder.append("\n\n").append(catalog);
      }
    }
    builder.append("\n\nCurrent project:\n").append(describeHome(home));
    return builder.toString();
  }

  /**
   * Returns a plain-text list of the furniture catalog's categories and item
   * names, so the model only uses names that actually resolve, or an empty
   * string if no catalog is available.
   */
  public static String describeCatalog(UserPreferences preferences) {
    FurnitureCatalog catalog = preferences != null ? preferences.getFurnitureCatalog() : null;
    if (catalog == null) {
      return "";
    }
    StringBuilder brief = new StringBuilder();
    for (FurnitureCategory category : catalog.getCategories()) {
      List<CatalogPieceOfFurniture> pieces = category.getFurniture();
      if (pieces.isEmpty()) {
        continue;
      }
      if (brief.length() == 0) {
        brief.append("Furniture catalog (the only valid names for add_furniture and add_door_or_window):\n");
      }
      brief.append("- ").append(category.getName()).append(": ");
      int listed = 0;
      for (CatalogPieceOfFurniture piece : pieces) {
        String name = piece.getName();
        if (name == null || name.length() == 0) {
          continue;
        }
        if (listed >= MAX_LISTED_CATALOG_PIECES_PER_CATEGORY) {
          brief.append(", ... and more (use search_catalog)");
          break;
        }
        if (listed > 0) {
          brief.append(", ");
        }
        brief.append(name);
        listed++;
      }
      brief.append('\n');
    }
    return brief.toString();
  }

  /**
   * Returns a plain-text brief describing the levels, rooms, walls and furniture
   * of the given <code>home</code>.
   */
  public static String describeHome(Home home) {
    return describeHome(home, null, null, null);
  }

  /**
   * Returns the description of a part of <code>home</code>: the items of the
   * level named <code>levelName</code>, of the given <code>types</code>
   * (<code>rooms</code>, <code>walls</code>, <code>furniture</code>,
   * <code>doors_and_windows</code>) and whose name contains <code>nameFilter</code>.
   * A <code>null</code> parameter doesn't filter. Ids are the same as in the
   * complete description.
   */
  public static String describeHome(Home home, String levelName, List<String> types, String nameFilter) {
    HomeItemIds.ensureIds(home);
    // An id L# designates one level, a name every level with that name
    List<Level> filterLevel = levelName != null ? HomeItemIds.findLevels(home, levelName) : null;
    if (filterLevel != null && filterLevel.isEmpty()) {
      filterLevel = null;
    }
    boolean filtered = filterLevel != null || types != null && !types.isEmpty()
        || nameFilter != null && nameFilter.trim().length() > 0;
    boolean listRooms = types == null || types.isEmpty() || types.contains("rooms");
    boolean listWalls = (types == null || types.isEmpty() || types.contains("walls"))
        && (nameFilter == null || nameFilter.trim().length() == 0);
    boolean listFurniture = types == null || types.isEmpty() || types.contains("furniture");
    boolean listDoorsAndWindows = types == null || types.isEmpty() || types.contains("furniture")
        || types.contains("doors_and_windows");
    String filterName = nameFilter != null && nameFilter.trim().length() > 0
        ? nameFilter.trim().toLowerCase(Locale.ENGLISH) : null;
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMaximumFractionDigits(2);
    format.setGroupingUsed(false);
    StringBuilder brief = new StringBuilder();
    brief.append("Sweet Home 3D project summary:\n");
    if (filtered) {
      brief.append("- Only the items matching the filter are listed (counts and totals are those of the whole home; "
          + "ids are the same as without filter)\n");
    }

    List<Level> levels = home.getLevels();
    if (!levels.isEmpty()) {
      brief.append("- Levels (").append(levels.size()).append("), from the lowest:\n");
      for (int i = 0; i < levels.size(); i++) {
        Level level = levels.get(i);
        String name = level.getName();
        int roomCount = 0;
        int wallCount = 0;
        int furnitureCount = 0;
        for (Room room : home.getRooms()) {
          roomCount += room.getLevel() == level ? 1 : 0;
        }
        for (Wall wall : home.getWalls()) {
          wallCount += wall.getLevel() == level ? 1 : 0;
        }
        for (HomePieceOfFurniture piece : home.getFurniture()) {
          furnitureCount += piece.getLevel() == level ? 1 : 0;
        }
        int homonyms = 0;
        for (Level other : levels) {
          homonyms += other != level && name != null && name.equalsIgnoreCase(other.getName()) ? 1 : 0;
        }
        brief.append("    - ").append(HomeItemIds.getId(level)).append(" \"")
            .append(name != null && name.length() > 0 ? name : "Level " + (i + 1))
            .append("\" at ").append(format.format(level.getElevation())).append(" cm, height ")
            .append(format.format(level.getHeight())).append(", floor thickness ")
            .append(format.format(level.getFloorThickness())).append(": ");
        if (roomCount + wallCount + furnitureCount == 0) {
          brief.append("EMPTY");
        } else {
          brief.append(roomCount).append(roomCount == 1 ? " room, " : " rooms, ")
              .append(wallCount).append(wallCount == 1 ? " wall, " : " walls, ")
              .append(furnitureCount).append(furnitureCount == 1 ? " piece of furniture" : " pieces of furniture");
        }
        if (!level.isViewable()) {
          brief.append(", not viewable");
        }
        if (homonyms > 0) {
          brief.append(", name shared with ").append(homonyms).append(homonyms == 1 ? " other level" : " other levels")
              .append(" (designate it by its id)");
        }
        if (level == home.getSelectedLevel()) {
          brief.append(" (selected: new items go here)");
        }
        brief.append('\n');
      }
    } else {
      brief.append("- Levels: 1 (single level)\n");
    }

    List<Room> rooms = home.getRooms();
    float totalRoomArea = 0;
    for (Room room : rooms) {
      totalRoomArea += room.getArea();
    }
    brief.append("- Rooms (").append(rooms.size()).append("), total floor area ")
        .append(format.format(totalRoomArea / 10000)).append(" m2");
    if (!rooms.isEmpty() && listRooms) {
      brief.append(":\n");
      for (int i = 0; i < rooms.size(); i++) {
        Room room = rooms.get(i);
        String roomName = room.getName();
        if (filterLevel != null && !filterLevel.contains(room.getLevel())
            || filterName != null
                && !AssistantCommandExecutor.matchesName(roomName, filterName)) {
          continue;
        }
        brief.append("    - ").append(HomeItemIds.getId(room)).append(' ')
            .append(roomName != null && roomName.length() > 0 ? roomName : "Unnamed room")
            .append(": ").append(format.format(room.getArea() / 10000)).append(" m2");
        // Where the room is, so furniture can be placed inside it
        float [][] points = room.getPoints();
        if (points.length > 0) {
          float minX = Float.POSITIVE_INFINITY;
          float minY = Float.POSITIVE_INFINITY;
          float maxX = Float.NEGATIVE_INFINITY;
          float maxY = Float.NEGATIVE_INFINITY;
          for (float [] point : points) {
            minX = Math.min(minX, point [0]);
            minY = Math.min(minY, point [1]);
            maxX = Math.max(maxX, point [0]);
            maxY = Math.max(maxY, point [1]);
          }
          brief.append(", x ").append(format.format(minX)).append(" to ").append(format.format(maxX))
              .append(", y ").append(format.format(minY)).append(" to ").append(format.format(maxY))
              .append(" cm, center (").append(format.format(room.getXCenter())).append(", ")
              .append(format.format(room.getYCenter())).append(")");
        }
        appendLevel(brief, room.getLevel(), levels);
        // What isn't drawn in 3D, as under a sloped roof or for a paved area outdoors
        if (!room.isCeilingVisible()) {
          brief.append(", ceiling hidden");
        }
        if (!room.isFloorVisible()) {
          brief.append(", floor hidden");
        }
        brief.append('\n');
      }
    } else {
      brief.append('\n');
    }

    List<Wall> walls = new ArrayList<Wall>(home.getWalls());
    float totalWallLength = 0;
    for (Wall wall : walls) {
      totalWallLength += wall.getLength();
    }
    brief.append("- Walls (").append(walls.size()).append("), total length ")
        .append(format.format(totalWallLength / 100)).append(" m");
    if (!walls.isEmpty() && listWalls) {
      brief.append(":\n");
      for (int i = 0; i < walls.size(); i++) {
        Wall wall = walls.get(i);
        if (filterLevel != null && !filterLevel.contains(wall.getLevel())) {
          continue;
        }
        brief.append("    - ").append(HomeItemIds.getId(wall)).append(" (")
            .append(format.format(wall.getXStart())).append(", ").append(format.format(wall.getYStart()))
            .append(") to (").append(format.format(wall.getXEnd())).append(", ")
            .append(format.format(wall.getYEnd())).append(") cm, ")
            .append(format.format(wall.getThickness())).append(" thick");
        if (wall.getHeight() != null && wall.getHeightAtEnd() != null) {
          brief.append(", height ").append(format.format(wall.getHeight())).append(" to ")
              .append(format.format(wall.getHeightAtEnd()));
        } else if (wall.getHeight() != null && wall.getHeight() != home.getWallHeight()) {
          brief.append(", height ").append(format.format(wall.getHeight()));
        }
        appendLevel(brief, wall.getLevel(), levels);
        brief.append('\n');
      }
    } else {
      brief.append('\n');
    }

    int[] counts = new int[3]; // total, visible, groups
    long[] heaviest = new long[1];
    String[] heaviestName = new String[1];
    countFurniture(home.getFurniture(), counts, heaviest, heaviestName);
    brief.append("- Furniture: ").append(counts[0]).append(" pieces (")
        .append(counts[1]).append(" visible");
    if (counts[2] > 0) {
      brief.append(", ").append(counts[2]).append(" groups");
    }
    brief.append(")\n");
    if (heaviestName[0] != null) {
      brief.append("- Largest 3D model by file size: ").append(heaviestName[0])
          .append(" (").append(format.format(heaviest[0] / 1024.0 / 1024.0)).append(" MB)\n");
    }

    List<HomePieceOfFurniture> topFurniture = home.getFurniture();
    if (!topFurniture.isEmpty() && (listFurniture || listDoorsAndWindows)) {
      brief.append("- ").append(AssistantLayerCommands.describeLayers(home)).append('\n');
      brief.append("- Furniture items (id at x,y cm, facing deg, width x depth cm):\n");
      // A filter is how the pieces beyond the limit of the complete description are reached
      int limit = filtered ? MAX_FILTERED_FURNITURE : MAX_LISTED_FURNITURE;
      int listed = 0;
      int notListed = 0;
      for (int i = 0; i < topFurniture.size(); i++) {
        HomePieceOfFurniture piece = topFurniture.get(i);
        String pieceName = piece.getName() != null ? piece.getName() : "Unnamed";
        if (filterLevel != null && !filterLevel.contains(piece.getLevel())
            || !(piece.isDoorOrWindow() ? listDoorsAndWindows : listFurniture)
            || filterName != null && !AssistantCommandExecutor.matchesName(pieceName, filterName)) {
          continue;
        }
        if (listed >= limit) {
          notListed++;
          continue;
        }
        listed++;
        brief.append("    - ").append(HomeItemIds.getId(piece)).append(" \"").append(pieceName).append("\" at (")
            .append(format.format(piece.getX())).append(", ").append(format.format(piece.getY()))
            .append(") cm, facing ").append(Math.round(Math.toDegrees(piece.getAngle())))
            .append(" deg, ").append(format.format(piece.getWidth())).append(" x ")
            .append(format.format(piece.getDepth())).append(" cm");
        if (piece.getElevation() != 0) {
          brief.append(", elevation ").append(format.format(piece.getElevation()));
        }
        if (piece.getLevel() != null && piece.getLevel().getHeight() > 0
            && piece.getElevation() + piece.getHeightInPlan() > piece.getLevel().getHeight() + 1) {
          // What may show in the plan of the levels above
          brief.append(", height ").append(format.format(piece.getHeightInPlan()));
          if (FurniturePlanVisibility.get(piece) != FurniturePlanVisibility.DRAW) {
            brief.append(", ").append(FurniturePlanVisibility.get(piece) == FurniturePlanVisibility.OUTLINE
                ? "outlined" : "hidden").append(" in the plans above");
          }
        }
        if (piece.getPitch() != 0) {
          brief.append(", pitch ").append(Math.round(Math.toDegrees(piece.getPitch()))).append(" deg");
        }
        if (piece.getRoll() != 0) {
          brief.append(", roll ").append(Math.round(Math.toDegrees(piece.getRoll()))).append(" deg");
        }
        if (piece.isDoorOrWindow()) {
          brief.append(" (door/window)");
        }
        appendLevel(brief, piece.getLevel(), levels);
        if (!piece.isVisible()) {
          brief.append(" (hidden)");
        }
        String tags = piece.getProperty(HomeItemTags.TAGS_PROPERTY);
        if (tags != null) {
          brief.append(", tags: ").append(tags);
        }
        Long modelSize = piece.getModelSize();
        if (modelSize != null && modelSize.longValue() >= 1024 * 1024) {
          brief.append(", model ").append(format.format(modelSize.longValue() / 1024.0 / 1024.0))
              .append(" MB");
        }
        if (piece.getModel() != null && home.getModelLOD(piece.getModel()) != null) {
          brief.append(ModelLOD.isReducedDetailDisplayed(piece, home)
              ? ", reduced detail ON" : ", reduced model available");
        }
        brief.append('\n');
      }
      if (notListed > 0) {
        brief.append("    ... and ").append(notListed).append(" more")
            .append(filtered ? "" : " (list them with a filter: a level, a type or a name)").append('\n');
      }
    }

    // The lines drawn in the plan: property lines, setbacks, utilities
    if (!home.getPolylines().isEmpty() && (types == null || types.isEmpty())) {
      brief.append("- Lines (").append(home.getPolylines().size()).append("):\n");
      int listed = 0;
      for (com.eteks.sweethome3d.model.Polyline polyline : home.getPolylines()) {
        if (filterLevel != null && !filterLevel.contains(polyline.getLevel())) {
          continue;
        }
        if (listed++ == 40) {
          brief.append("    ... and more (describe_item gives the points of a line)\n");
          break;
        }
        float [][] points = polyline.getPoints();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float [] point : points) {
          minX = Math.min(minX, point [0]);
          minY = Math.min(minY, point [1]);
          maxX = Math.max(maxX, point [0]);
          maxY = Math.max(maxY, point [1]);
        }
        String lineName = polyline.getProperty(AssistantSiteCommands.LINE_NAME_PROPERTY);
        String kind = polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY);
        brief.append("    - ").append(HomeItemIds.getId(polyline))
            .append(lineName != null ? " \"" + lineName + "\"" : "")
            .append(kind != null ? " (" + kind + " line)" : "").append(": ").append(points.length).append(" points")
            .append(polyline.isClosedPath() ? ", closed" : "").append(", x ").append(format.format(minX)).append(" to ")
            .append(format.format(maxX)).append(", y ").append(format.format(minY)).append(" to ").append(format.format(maxY))
            .append(" cm");
        if (polyline.getLevel() != null && levels.size() > 1) {
          brief.append(" [").append(polyline.getLevel().getName()).append(']');
        }
        brief.append('\n');
      }
    }
    HomeGeoreference georeference = HomeGeoreference.get(home);
    if (georeference != null) {
      brief.append("- Georeference: the point (").append(format.format(georeference.getX())).append(", ")
          .append(format.format(georeference.getY())).append(") of the plan is at latitude ")
          .append(String.format(Locale.US, "%.6f", georeference.getLatitude())).append(", longitude ")
          .append(String.format(Locale.US, "%.6f", georeference.getLongitude())).append("; the true north is ")
          .append(format.format(georeference.getRotation())).append(" degrees clockwise from the top of the plan\n");
    }

    int openNotes = HomeReviewNotes.getOpenCount(home);
    if (openNotes > 0) {
      brief.append("- ").append(HomeReviewNotes.describe(home, true).replace("\n", "\n    ")).append('\n');
    }

    List<Selectable> selectedItems = home.getSelectedItems();
    if (!selectedItems.isEmpty()) {
      brief.append("- Current selection (").append(selectedItems.size()).append("):\n");
      for (Selectable item : selectedItems) {
        if (item instanceof HomePieceOfFurniture) {
          HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
          String name = piece.getName() != null ? piece.getName() : "Unnamed piece";
          brief.append("    - furniture \"").append(name).append("\" at (")
              .append(format.format(piece.getX())).append(", ").append(format.format(piece.getY()))
              .append(") cm, facing ").append(Math.round(Math.toDegrees(piece.getAngle())))
              .append(" deg, size ").append(format.format(piece.getWidth())).append(" x ")
              .append(format.format(piece.getDepth())).append(" cm\n");
        } else if (item instanceof Room) {
          Room room = (Room)item;
          String name = room.getName() != null ? room.getName() : "Unnamed room";
          brief.append("    - room \"").append(name).append("\", area ")
              .append(format.format(room.getArea() / 10000)).append(" m2\n");
        } else if (item instanceof Wall) {
          Wall wall = (Wall)item;
          brief.append("    - wall from (").append(format.format(wall.getXStart())).append(", ")
              .append(format.format(wall.getYStart())).append(") to (")
              .append(format.format(wall.getXEnd())).append(", ").append(format.format(wall.getYEnd()))
              .append(") cm\n");
        }
      }
    }
    return brief.toString();
  }

  /**
   * Returns the instructions describing how the assistant must reply to make
   * edits: a JSON command protocol with a centimeter coordinate system.
   */
  public static String getCommandProtocol() {
    return "To MODIFY the home, reply with ONLY a JSON object of the form "
        + "{\"reply\": \"<short message to the user>\", \"commands\": [ ... ]}. "
        + getCommandReference()
        + "\n- If the user only asks a question, reply normally in plain text without any JSON.";
  }

  /**
   * Returns the reference of the edit commands (coordinate system, commands and
   * their rules), shared by the chat protocol and the MCP server.
   */
  public static String getCommandReference() {
    return "Coordinates are in centimeters: x increases to the right of the plan, y increases "
        + "DOWNWARD (toward the bottom of the plan), so \"north\" means -y and \"south\" means +y. "
        + "Angles are in degrees clockwise; at angle 0 a piece's front faces +y (down). "
        + "Compute positions relative to the current selection "
        + "when the user refers to it. Supported commands (omit a field to use its default):\n"
        + "  {\"action\":\"search_catalog\",\"query\":\"<words>\"}  (matches are reported back to you in the next turn)\n"
        + "  {\"action\":\"add_furniture\",\"name\":\"<exact catalog name>\",\"x\":N,\"y\":N,\"angle\":N,\"width\":N,\"depth\":N,\"elevation\":N}\n"
        + "  {\"action\":\"add_door_or_window\",\"name\":\"<exact catalog name>\",\"x\":N,\"y\":N,\"angle\":N}\n"
        + "  {\"action\":\"add_room\",\"name\":\"<name>\",\"points\":[[x,y],[x,y],[x,y],...]}  "
        + "(optional \"floor_color\", \"ceiling_visible\":false, and \"floor_offset\":N with \"slab_thickness\":N for a floor "
        + "N cm above (or under, if negative) the floor of the level: a slab of the shape of the room replaces its floor)\n"
        + "  {\"action\":\"add_wall\",\"x1\":N,\"y1\":N,\"x2\":N,\"y2\":N,\"thickness\":N,\"height\":N}\n"
        + "  {\"action\":\"add_rectangular_room\",\"name\":\"<name>\",\"x\":N,\"y\":N,\"width\":N,\"depth\":N}  "
        + "(or instead of x,y: \"next_to\":\"R1\" (a room id or name),\"side\":\"north|south|east|west\",\"offset\":N to put it against "
        + "that side of an existing room, aligned on its top or left edge plus offset. "
        + "x,y = top left corner of the inside of the room; adds the room AND the walls around it, "
        + "reusing a wall that already stands along a side, so place a new room against an existing one "
        + "to extend the house. Optional: \"walls\":false, \"thickness\":N, \"height\":N, \"floor_color\":\"#RRGGBB\". "
        + "Prefer it to add_room + add_wall for rectangular rooms)\n"
        + "  {\"action\":\"auto_roof\",\"type\":\"gable|hip|shed|flat\",\"pitch\":N,\"overhang\":N,\"color\":\"#RRGGBB\"}  "
        + "(adds a roof over the walls of the selected level, on a level named Roof on top of them; "
        + "pitch in degrees (default 30), overhang in cm (default 30), \"ridge\":\"x\"|\"y\" to choose the ridge direction, "
        + "\"ids\":[\"R1\"] to cover only some rooms and \"per_room\":true|false to force one roof per room or a single one "
        + "(by default a house that isn't a rectangle gets one roof per room). Calling it again replaces the roof)\n"
        + "  {\"action\":\"add_roof\",\"points\":[[x,y],[x,y],...],\"eave_height\":N,\"overhang\":N,\"pitch\":N,"
        + "\"edges\":[{\"type\":\"hip|gable\",\"pitch\":N},...],\"color\":\"#RRGGBB\",\"level\":\"<level>\"}  "
        + "(ONE roof over any polygon, as one piece on the given level (default: the selected one): points = the outside "
        + "corners of the walls it covers, eave_height = height of the top of these walls above the floor of the level (default: "
        + "its walls). \"edges\" has one item per edge, the first from point 1 to point 2: \"hip\" = an eave with a slope rising "
        + "from it, \"gable\" (or shed, flat) = a vertical end; an edge may have its own pitch and overhang. Instead of edges, "
        + "\"type\":\"hip|gable|shed|flat\" chooses them: gable puts gables at the ends of the wings, shed one slope rising from "
        + "\"low_side\":\"north|south|east|west\". The wings of an L, T or U shaped house meet in valleys and a narrow wing "
        + "ends in the slope of a wider one. Options: \"texture\":\"<texture name>\" (with texture_scale, texture_angle), "
        + "\"gable_color\", \"fascia\":true|false with \"fascia_color\", \"underside_color\", \"soffit\":true, \"ridge_cap\":true, "
        + "\"gutters\":true with \"downspouts\":true|false, \"gable_walls\":false to leave the gables open, \"thickness\":N, "
        + "\"dormers\":[...]. The plans of the levels above show the roof as a dashed outline)\n"
        + "  {\"action\":\"add_dormer\",\"roof\":\"F1\",\"side\":\"north|south|east|west\",\"position\":0.5,\"width\":N,\"height\":N,"
        + "\"type\":\"gable|shed|hip|flat\",\"window\":\"<catalog window name>\"}  (a dormer on the slope of a roof built by add_roof, "
        + "which is cut open under it: side = the eave it looks over (or \"edge\":N), position = where along this side from 0 (its west or north end) to 1 "
        + "(or \"offset\":N cm from that end to the CENTER of the dormer; for an \"edge\":N both are measured from the start of that edge), "
        + "height = the one of its front wall, \"setback\":N cm from the eave, optional \"pitch\". \"windows\":N puts N windows side by side, "
        + "or \"windows\":[\"<name>\",{\"name\":\"<name>\",\"width\":N,\"height\":N,\"sill\":N}]. \"ridge\":\"match\" raises its ridge to the top of the roof, "
        + "for a cross gable (with \"setback\":0 its front is in line with the wall). The hole it cuts in the roof ends at its side walls. "
        + "The windows of the other dormers are kept)\n"
        + "  {\"action\":\"modify_dormer\",\"roof\":\"F1\",\"index\":1,\"width\":N,\"height\":N,\"offset\":N,\"setback\":N,\"type\":\"gable|shed|hip|flat\","
        + "\"pitch\":N}  (changes one dormer, numbered from 1 as describe_item lists them, without building the roof again; also "
        + "\"ridge\", \"side\", \"window\", \"windows\"; its windows move with it)\n"
        + "  {\"action\":\"remove_dormer\",\"roof\":\"F1\",\"index\":1}  (removes one dormer and its windows; the dormers after it are numbered again)\n"
        + "  {\"action\":\"modify_roof\",\"roof\":\"F1\",\"eave_height\":N,\"pitch\":N,\"overhang\":N,\"edge\":{\"index\":2,\"type\":\"hip|gable|abut\"}}  "
        + "(changes a roof built by add_roof without building it again: any of its parameters, or one edge (or a list of edges) by its number, "
        + "the edge 1 going from point 1 to point 2. \"raise_walls\":true moves the top of the walls under it with its eaves. "
        + "An \"abut\" edge runs along the wall of a taller part of the building: no overhang, no gable wall. Its dormers are kept)\n"
        + "  {\"action\":\"fit_walls_to_roof\",\"roof\":\"F1\"}  (gives the outside walls under a roof built by add_roof the height "
        + "of its underside, so gable ends close: a wall under a peak is cut in two walls that slope up to it. Under a dormer "
        + "or a cross gable the walls go up to its roof. The walls it adds stay at the level of the wall they continue. "
        + "\"all\":true for the inside walls too, or \"ids\":[\"W1\"]. Run it again after the roof or its dormers change)\n"
        + "  {\"action\":\"headroom\",\"ids\":[\"R1\"]}  (the lowest and the highest height of the ceiling of rooms under a roof, "
        + "and the area at least 213 cm (7 ft) high; without ids, every room under a roof)\n"
        + "  {\"action\":\"add_slab\",\"points\":[[x,y],[x,y],...],\"top\":N,\"thickness\":N,\"color\":\"#RRGGBB\",\"name\":\"<name>\"}  "
        + "(a flat slab of any shape as a piece of furniture, without the side effects of a room: a driveway, a garage floor, "
        + "paving, a lawn, a garden bed. top = height of its top above the floor of the level, which may be negative; "
        + "\"texture\":\"<texture name>\" with texture_scale and texture_angle, \"side_color\"; "
        + "\"top_at\":[[x,y,z],[x,y,z],[x,y,z]] tilts its top through three points, for a ramp or a sloped driveway)\n"
        + "  {\"action\":\"set_terrain\",\"points\":[[x,y,z],[x,y,z],...],\"color\":\"#RRGGBB\"}  (the ground as a surface through "
        + "points of known height z in cm, 0 being the floor of the level at the elevation 0 and slopes going under it if needed; or \"grid\":{\"x\":N,\"y\":N,\"width\":N,\"depth\":N,"
        + "\"heights\":[[z,z,...],...]}. \"texture\" for the whole, \"areas\":[{\"points\":[[x,y],...],\"texture\"|\"color\"}] for a lawn, "
        + "a path... Calling it again replaces the ground. It goes to a level under the others, added if needed, where it "
        + "replaces the flat ground of the 3D view. {\"action\":\"set_elevation\",\"id\":\"F1\",\"on_terrain\":true} puts a piece on the ground at its place, as a tree or a car outdoors that check_home doesn't report)\n"
        + "  {\"action\":\"add_stairs\",\"x\":N,\"y\":N,\"angle\":N,\"width\":N,\"shape\":\"straight|L|U|spiral\",\"turn\":\"left|right\",\"railing\":true}  "
        + "(stairs from the selected level up to the next one, which they cut open; x,y = their center, "
        + "the bottom step is at their front. L = quarter turn and U = half turn at a landing, which take less length; spiral = steps turning around a post, in a square of twice the width + 16 cm, x,y = the post; "
        + "\"railing\":true|\"left\"|\"right\" adds handrails; optional \"length\":N (straight), \"tread\":N, \"rise\":N and \"color\". "
        + "Add the upper level first; the note in reply gives the floor size they take)\n"
        + "  {\"action\":\"add_picture\",\"image\":\"<absolute file path, or attached>\",\"name\":\"<name>\",\"mode\":\"panel|box|floor\","
        + "\"x\":N,\"y\":N,\"angle\":N,\"width\":N,\"height\":N,\"depth\":N,\"elevation\":N}  "
        + "(furniture showing a picture: \"panel\" = a painting, poster, photo or screen, hang it with elevation; "
        + "\"box\" = a box with the picture on its front; \"floor\" = a rug showing it on top. "
        + "Missing sizes follow the proportions of the image)\n"
        + "  {\"action\":\"import_furniture\",\"path\":\"<absolute path of an OBJ, DAE, 3DS, KMZ or ZIP file>\",\"name\":\"<name>\","
        + "\"x\":N,\"y\":N,\"angle\":N,\"width\":N}  (a 3D model file as a new piece; give width or height in cm to size it)\n"
        + "  {\"action\":\"create_furniture\",\"name\":\"<name>\",\"parts\":[{\"shape\":\"box|cylinder|cone|sphere\",\"x\":N,\"y\":N,\"z\":N,"
        + "\"width\":N,\"depth\":N,\"height\":N,\"color\":\"#RRGGBB\"}],\"x\":N,\"y\":N,\"angle\":N,\"catalog\":\"<category>\"}  "
        + "(builds a piece that is in no catalog from simple parts, in cm in the frame of the piece: part x to its right, "
        + "y toward its front, z = height of the BOTTOM of the part above the floor, x,y = the CENTER of the part. "
        + "E.g. a table = a top box at z 72 and four leg boxes at z 0. Optional per part: \"rotation\":N degrees around the vertical, "
        + "\"axis\":\"x|y\" to lay a cylinder or cone down, \"diameter\":N, \"top_scale\":0..1 to taper it. "
        + "Other shapes: \"prism\" = a polygon \"points\":[[x,y],...] (relative to the x,y of the part) pushed up by height; "
        + "\"wedge\" = a box cut in a slope, with \"high_side\":\"back|front|left|right\" (its tallest side, back = -y) or "
        + "\"ridge\":\"x|y\" for a peak along its middle like a gable; \"extrude\" = a bar following \"path\":[[x,y,z],...] "
        + "with a section of width x height or a \"diameter\" (gutters, handrails, edging; \"closed\":true for a loop). "
        + "Any part takes \"texture\":\"<texture name>\" with texture_scale and texture_angle in place of a color, and "
        + "\"yaw\", \"pitch\" and \"roll\" in degrees to turn it around the vertical, its width and its depth. "
        + "Up to 150 parts. \"catalog\" also saves it in that category of the furniture catalog, with \"place\":false "
        + "to save it without adding it to the home; \"tags\":[...] tags it; \"structure\":true for a part of the building or "
        + "of its site (a chimney, a post, a fence), which check_home doesn't report as furniture out of its room; "
        + "\"on_terrain\":true measures the heights of the parts from the ground under each of them, a bar along a path following its slopes: "
        + "a string line, a fence. A part may be up to 200 m long or away)\n"
        + "  {\"action\":\"modify_furniture_parts\",\"id\":\"F1\",\"set\":[{\"index\":1,\"height\":N}],\"add\":[{...}],\"remove\":[3]}  "
        + "(changes the parts of a piece built by create_furniture without building it again: index 1 = its first part; "
        + "or \"parts\":[...] to replace them all. The parts that don't change stay at their place)\n"
        + "  {\"action\":\"add_to_catalog\",\"ids\":[\"F1\"],\"category\":\"<category>\"}  "
        + "(saves pieces of the home, e.g. imported or created ones, in the user's furniture catalog; or \"tag\":\"<tag>\")\n"
        + "  {\"action\":\"export_furniture_library\",\"path\":\"<absolute path>.sh3f\",\"name\":\"<library name>\",\"ids\":[\"F1\"]}  "
        + "(writes a furniture library file to share from pieces of the home (ids, selection or \"tag\"), or from "
        + "\"category\":\"<catalog category>\"; \"install\":true also installs it, \"replace\":true overwrites)\n"
        + "  {\"action\":\"import_furniture_library\",\"path\":\"<absolute path>.sh3f\"}  (installs a furniture library in the catalog)\n"
        + "  {\"action\":\"import_texture\",\"image\":\"<absolute file path, or attached>\",\"name\":\"<name>\",\"width\":N,\"id\":\"R1\",\"part\":\"floor\"}  "
        + "(adds an image to the textures catalog, width = size in cm of what it shows, and applies it to the ids given)\n"
        + "  {\"action\":\"add_level\",\"name\":\"<name>\",\"elevation\":N,\"height\":N}  (adds a floor above and selects it)\n"
        + "  {\"action\":\"select_level\",\"name\":\"<level name or id L#>\"}  (new items go to the selected level)\n"
        + "  {\"action\":\"modify_level\",\"id\":\"L1\",\"new_name\":\"<name>\",\"elevation\":N,\"height\":N,\"floor_thickness\":N,"
        + "\"viewable\":true|false,\"elevation_index\":N}  (renames, moves, resizes or hides a level, designated by its id or "
        + "by \"name\" if no other level has it)\n"
        + "  {\"action\":\"delete_level\",\"id\":\"L2\"}  (or \"ids\":[\"L2\",\"L3\"], or \"name\"; deletes an empty level; one that isn't empty is "
        + "refused with the list of what is on it, unless \"move_contents_to\":\"<level>\" or \"delete_contents\":true)\n"
        + "  {\"action\":\"merge_levels\",\"from\":\"<level>\",\"into\":\"<level>\"}  (moves everything of a level to an other one, "
        + "furniture keeping its height above the ground, then deletes it)\n"
        + "  {\"action\":\"move_to_level\",\"id\":\"F1\",\"level\":\"<level name>\"}  (moves furniture, walls or rooms to another level)\n"
        + "  {\"action\":\"set_background_image\",\"image\":\"<absolute file path, or attached>\",\"width\":N,\"x\":N,\"y\":N}  "
        + "(displays the picture of a floor plan behind the plan of the selected level to trace it; width = real width in cm "
        + "of what the whole image shows, x,y = where its top left corner goes; {\"action\":\"remove_background_image\"} removes it)\n"
        + "  {\"action\":\"home_report\"}  (floor areas, wall lengths and surfaces, furniture counts and prices, reported back in the next turn)\n"
        + "  {\"action\":\"add_label\",\"text\":\"<text>\",\"x\":N,\"y\":N}\n"
        + "  {\"action\":\"add_dimension\",\"x1\":N,\"y1\":N,\"x2\":N,\"y2\":N,\"offset\":N}  (a measurement line)\n"
        + "  {\"action\":\"add_room_shape\",\"shape\":\"L|T|U|rectangle\",\"name\":\"<name>\",\"x\":N,\"y\":N,\"width\":N,\"depth\":N,"
        + "\"cut_width\":N,\"cut_depth\":N}  (a room that isn't a rectangle, with its walls; x,y = top left corner of its bounding box, "
        + "width,depth = that box. L: the top right corner is cut out; T: both bottom corners, leaving a leg; "
        + "U: a notch in the middle of the top side. Without x,y it goes to the right of the plan)\n"
        + "  {\"action\":\"apply_style\",\"style\":\"Scandinavian|Modern|Industrial|Traditional|Coastal|Mid-century|Minimalist|Dark|Earthy|Mediterranean\","
        + "\"ids\":[\"R1\"]}  (colors the floor, the ceiling and the facing wall sides of the rooms given, or of every room "
        + "of the selected level; \"textures\":false keeps plain colors)\n"
        + "  {\"action\":\"replace_furniture\",\"ids\":[\"F1\"],\"name\":\"<exact catalog name>\"}  "
        + "(swaps pieces for another catalog piece at the same place, angle, level and tags; \"keep_size\":true keeps the old size)\n"
        + "  {\"action\":\"set_unit_prices\",\"paint\":N,\"flooring\":N,\"ceiling\":N,\"baseboard\":N,\"currency\":\"EUR\"}  "
        + "(prices per m2 of wall side, floor and ceiling and per m of baseboard; home_report then gives a budget)\n"
        + "  {\"action\":\"export_elevation\",\"side\":\"south|north|east|west\",\"path\":\"<absolute path>.svg\",\"section\":N}  "
        + "(writes a 2D elevation drawing of the home seen from that side; section = cm behind the facade where the home is cut "
        + "to show the inside, 0 for the outside)\n"
        + "  {\"action\":\"auto_dimensions\"}  (adds the dimension lines giving the size of every room of the selected level, "
        + "or of \"ids\":[\"R1\"]; lines that exist are kept)\n"
        + "  {\"action\":\"select\",\"where\":{\"bbox\":[x1,y1,x2,y2]}}  (selects items by \"where\", \"ids\" or \"names\" and lists them "
        + "with their ids: the way to see what is in an area)\n"
        + "  {\"action\":\"add_polyline\",\"points\":[[x,y],[x,y],...],\"color\":\"#RRGGBB\",\"thickness\":N,\"dash\":\"solid|dashed|dotted|dash_dot\","
        + "\"closed\":false,\"name\":\"<name>\",\"level\":\"<level>\"}  (a line drawn in the plan, not a piece of furniture: an easement, a utility "
        + "line, a fence line. It gets an id P#. \"arrows\":\"start|end|both\", \"elevation\":N to show it in 3D at that height, "
        + "\"geo\":[[lon,lat],...] in place of points in a georeferenced home)\n"
        + "  {\"action\":\"add_property_line\",\"points\":[[x,y],...],\"label\":\"<text>\"}  (the closed line of a lot, with its area; "
        + "\"stakes\":true adds stakes and a string in 3D that follow the ground; check_home reports the walls beyond it)\n"
        + "  {\"action\":\"add_setback\",\"from\":\"P1\",\"distance\":N,\"side\":\"inside|outside\"}  (the dashed line N cm inside a "
        + "property line, which buildings must keep behind: check_home reports the walls beyond it as errors)\n"
        + "  {\"action\":\"modify_polyline\",\"id\":\"P1\",\"points\":[[x,y],...],\"color\":\"#RRGGBB\",\"thickness\":N,\"dash\":\"dashed\",\"name\":\"<name>\"}  "
        + "(lines are deleted with delete, and listed by get_home and where type polylines)\n"
        + "  {\"action\":\"set_compass\",\"north_direction\":N,\"latitude\":N,\"longitude\":N,\"x\":N,\"y\":N,\"diameter\":N}  (north_direction = "
        + "degrees clockwise from the top of the plan to the north; the sun of the 3D view and of photos follows it. \"visible\":false hides the rose, "
        + "\"time_zone\":\"America/New_York\")\n"
        + "  {\"action\":\"set_georeference\",\"lat\":N,\"lon\":N,\"x\":N,\"y\":N,\"rotation\":N}  (the place on the Earth of the point x,y of the plan, "
        + "and the direction of the true north in degrees clockwise from the top of the plan; or \"points\":[{\"lat\":N,\"lon\":N,\"x\":N,\"y\":N},{...}] "
        + "two points known in both. Sets the compass to the true north too)\n"
        + "  {\"action\":\"import_parcel\",\"geojson\":{...},\"srs\":\"EPSG:2240\"}  (draws the lot lines of a survey as property lines: a GeoJSON "
        + "Polygon, MultiPolygon, Feature or FeatureCollection, or \"esrijson\" with rings, or \"path\" of such a file. srs = the system of its "
        + "coordinates: EPSG:4326 longitude/latitude (default), EPSG:3857, UTM zones, EPSG:2240 and 2239 (Georgia, US survey feet), or a PROJ text "
        + "like \"+proj=tmerc +lat_0=30 +lon_0=-84.1666666667 +k=0.9999 +x_0=700000 +y_0=0 +units=us-ft\" (tmerc, utm, lcc). In a home without "
        + "georeference, the middle of the parcel goes to \"x\",\"y\" with the north at the top)\n"
        + "  {\"action\":\"get_coordinates\",\"points\":[[x,y],...],\"srs\":\"EPSG:2240\"}  (the latitude and longitude of points of the plan or "
        + "of the items given by ids, and their coordinates in srs)\n"
        + "  {\"action\":\"begin_group\"} ... {\"action\":\"end_group\"}  (everything changed by the calls between them is undone as ONE step; "
        + "the undo tool ends the group and undoes it)\n"
        + AssistantArrangeCommands.getReference()
        + AssistantGardenCommands.getReference()
        + AssistantScheduleCommands.getReference()
        + AssistantSceneCommands.getReference()
        + "To MODIFY items that already exist, reference them by the id shown in the project "
        + "summary (F# furniture, W# walls, R# rooms, P# lines; an id never changes), by \"target\":\"selection\", by name, or in bulk by "
        + "\"where\":{\"level\":\"<level>\",\"tag\":\"<tag>\",\"name\":\"<part of a name, or Pine|Oak for either>\",\"type\":\"furniture|doors_and_windows|"
        + "lights|stairs|all_furniture|walls|rooms|polylines\",\"bbox\":[x1,y1,x2,y2]} (the items matching every criterion given; "
        + "e.g. {\"action\":\"move_to_level\",\"where\":{\"level\":\"Old\"},\"level\":\"Level 1\"}). "
        + "Editing commands (omit a field to leave it unchanged):\n"
        + "  {\"action\":\"move\",\"id\":\"F1\",\"dx\":N,\"dy\":N}  (relative; or \"x\":N,\"y\":N for an absolute position)\n"
        + "  {\"action\":\"rotate\",\"id\":\"F1\",\"angle\":N}  (absolute degrees; or \"by\":N to add to the current angle; "
        + "\"pitch\":N or \"roll\":N tilts a piece forward or sideways, one of them at a time, and \"pitch\":0,\"roll\":0 sets it upright again)\n"
        + "  {\"action\":\"resize\",\"id\":\"F1\",\"width\":N,\"depth\":N,\"height\":N}\n"
        + "  {\"action\":\"set_color\",\"id\":\"F1\",\"color\":\"#RRGGBB\"}  (furniture; a wall W#: both sides or "
        + "\"part\":\"left\"|\"right\"; a room R#: its floor or \"part\":\"ceiling\")\n"
        + "  {\"action\":\"set_texture\",\"id\":\"R1\",\"texture\":\"<texture name>\",\"part\":\"floor|ceiling|left|right\"}  "
        + "(rooms, walls and furniture; find names with {\"action\":\"search_textures\",\"query\":\"<words>\"}; "
        + "when several textures share a name add \"category\":\"<category>\" or \"catalog_id\":\"<id>\"; the reply names the texture applied. "
        + "\"scale\":N enlarges it, \"angle\":N turns it by degrees, \"offset_x\" and \"offset_y\" shift it by a fraction of its size)\n"
        + "  {\"action\":\"modify_wall\",\"id\":\"W1\",\"height\":N,\"height_at_end\":N,\"thickness\":N,\"arc_extent\":N,"
        + "\"top_color\":\"#RRGGBB\",\"baseboard\":{\"height\":N,\"thickness\":N,\"texture\":\"<texture name>\",\"side\":\"left|right|both\"}}  "
        + "(height_at_end slopes the top of the wall from its start to its end, 0 levels it again; arc_extent in degrees "
        + "rounds it, 0 straightens it; \"x1\",\"y1\",\"x2\",\"y2\" move its ends. A baseboard as high as needed, with a color or "
        + "a texture, gives a wall a second finish under the first one, e.g. a brick base under siding; \"baseboard\":false removes it)\n"
        + "  {\"action\":\"set_plan_visibility\",\"tag\":\"roof\",\"value\":\"draw|outline|hide\"}  (how the plans of the levels ABOVE "
        + "the one of a piece show it when it rises through them: drawn, as a thin dashed outline, or not at all. Roofs are "
        + "outlined by default. Pieces by \"ids\", \"tag\", \"level\" or \"where\")\n"
        + "  {\"action\":\"describe_item\",\"id\":\"F1\"}  (every property of a piece, a wall, a room or a level L#: level, elevation, "
        + "heights above the ground, angles, tags, color or texture, model size, plan visibility; the edges and the dormers of a roof; "
        + "the headroom of a room under a roof; the points of a line P#)\n"
        + "  {\"action\":\"modify_room\",\"id\":\"R1\",\"ceiling_visible\":false,\"floor_visible\":true,\"area_visible\":true,"
        + "\"name\":\"<name>\",\"points\":[[x,y],[x,y],[x,y],...]}  (changes existing rooms, designated by \"id\", \"ids\", "
        + "\"target\":\"selection\" or \"room\":\"<room name>\"; fields left out don't change. Hide the ceiling of a room under a sloped roof "
        + "or of a terrace, garden or paved area outdoors, which would otherwise show as a flat slab at the height of the level; "
        + "\"points\" reshapes one room. \"ceiling\":\"follow_roof\" gives a room under a roof built by add_roof a ceiling under its slopes, "
        + "dormers included, and \"flat_ceiling_at\":N makes it flat from the height N, as under collar ties; \"ceiling\":\"flat\" removes it. "
        + "The reply gives the headroom)\n"
        + "  {\"action\":\"duplicate\",\"id\":\"F1\",\"dx\":N,\"dy\":N,\"count\":N}  (copies of furniture, each shifted by dx,dy)\n"
        + "  {\"action\":\"set_camera\",\"x\":N,\"y\":N,\"height\":N,\"look_at_x\":N,\"look_at_y\":N,\"mode\":\"visitor|aerial\"}  "
        + "(moves the virtual visitor, also the starting point of the VR preview; or \"view\":\"top|south|north|east|west|"
        + "south_east|south_west|north_east|north_west\" for an aerial view of the whole home or of \"ids\":[\"R1\"] entirely in sight, "
        + "\"view\":\"inside\",\"id\":\"R1\" to stand in a corner of a room or in front of a piece F# or a wall W#, "
        + "or \"point_of_view\":\"<name>\" for a stored one)\n"
        + "  {\"action\":\"store_point_of_view\",\"name\":\"<name>\"}  (keeps the current camera in the points of view of the 3D view menu; "
        + "with \"camera\":{\"x\":N,\"y\":N,\"height\":N,\"look_at_x\":N,\"look_at_y\":N} or a \"view\" as in set_camera, keeps that one "
        + "without moving the camera of the user: get_3d_image draws it with \"point_of_view\", and \"compare_with\":\"<photo path>\" sets a photo beside it)\n"
        + "  {\"action\":\"check_home\"}  (reports plan defects: walls that don't meet, duplicates, rooms without door, doors out of walls, "
        + "furniture under the floor or outside rooms, doors and windows higher than the roof above them, walls beyond a setback or a "
        + "property line. Each issue is an error, a warning or an info. \"ignore\":[\"F516\"] and \"ignore_tags\":[\"marker\"] leave issues out, "
        + "\"min_severity\":\"warning|error\" too; \"remember\":true keeps them for the next checks of this home)\n"
        + "  {\"action\":\"fix_home\"}  (repairs the defects marked fixable; \"types\":[\"wall_gap\"] limits it)\n"
        + "  {\"action\":\"set_elevation\",\"id\":\"F1\",\"elevation\":N}  (above the floor of its level; negative = under it)\n"
        + "  {\"action\":\"set_visible\",\"id\":\"F1\",\"visible\":true}\n"
        + "  {\"action\":\"rename\",\"id\":\"F1\",\"name\":\"<new name>\"}\n"
        + "  {\"action\":\"add_tags\",\"ids\":[\"F1\",\"F2\"],\"tags\":[\"kitchen\",\"to buy\"]}  "
        + "(tags furniture, doors and windows; a piece may have several tags. Also remove_tags and set_tags (replaces them). "
        + "Instead of ids: \"type\":\"furniture|doors_and_windows|lights|stairs|groups\" and / or \"with_tag\":\"<tag>\")\n"
        + "  {\"action\":\"hide_layer\",\"tag\":\"<tag>\"}  (hides every piece with that tag; or \"type\":\"lights\"; "
        + "show_layer shows them again, select_layer selects them, \"tag\":\"untagged\" = the pieces without tag)\n"
        + "  {\"action\":\"delete\",\"ids\":[\"F1\",\"W2\"]}  (or \"target\":\"selection\")\n"
        + "  {\"action\":\"reduce_detail\",\"ids\":[\"F1\"]}  (or \"target\":\"selection\" or \"all\"; "
        + "shows the piece's reduced low-poly model in the 3D view to make large scenes faster. "
        + "Only pieces marked \"reduced model available\" or \"reduced detail ON\" can switch; "
        + "for others tell the user to run 3D view > Generate model LOD cache first)\n"
        + "  {\"action\":\"restore_detail\",\"ids\":[\"F1\"]}  (back to full detail; \"selection\"/\"all\" allowed)\n"
        + "Rules:\n"
        + "- add_furniture and add_door_or_window names MUST be names from the catalog list "
        + "below. If none of the listed names fits, issue search_catalog alone and wait for its "
        + "results before adding.\n"
        + "- Place doors and windows ON a wall: give them the x,y of a point on the wall's "
        + "center line and the wall's direction as their angle.\n"
        + "- When the user shows or describes a piece of furniture that the catalog doesn't have, use the "
        + "closest catalog piece and resize and recolor it to match; use add_picture for flat things "
        + "(paintings, posters, rugs, screens) and import_furniture when the user has a 3D model file.\n"
        + "- Use at most 100 commands per reply; coordinates beyond 1000000 cm and sizes beyond "
        + "100000 cm are rejected.";
  }

  /**
   * Appends the name of the level of an item when the home has more than one.
   */
  private static void appendLevel(StringBuilder brief, Level level, List<Level> levels) {
    if (level != null && levels.size() > 1) {
      String name = level.getName();
      boolean homonym = false;
      for (Level other : levels) {
        homonym |= other != level && name != null && name.equalsIgnoreCase(other.getName());
      }
      // The id tells apart the levels that share a name
      brief.append(" [").append(homonym ? HomeItemIds.getId(level) + " " : "")
          .append(name != null && name.length() > 0 ? name : "Level " + (levels.indexOf(level) + 1)).append(']');
    }
  }

  private static void countFurniture(List<HomePieceOfFurniture> furniture, int[] counts,
                                     long[] heaviest, String[] heaviestName) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        counts[2]++;
        countFurniture(((HomeFurnitureGroup)piece).getFurniture(), counts, heaviest, heaviestName);
      } else {
        counts[0]++;
        if (piece.isVisible()) {
          counts[1]++;
        }
        Long modelSize = piece.getModelSize();
        if (modelSize != null && modelSize.longValue() > heaviest[0]) {
          heaviest[0] = modelSize.longValue();
          String name = piece.getName();
          heaviestName[0] = name != null ? name : "Unnamed piece";
        }
      }
    }
  }
}
