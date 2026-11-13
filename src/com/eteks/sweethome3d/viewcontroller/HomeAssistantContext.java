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
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMaximumFractionDigits(2);
    format.setGroupingUsed(false);
    StringBuilder brief = new StringBuilder();
    brief.append("Sweet Home 3D project summary:\n");

    List<Level> levels = home.getLevels();
    if (!levels.isEmpty()) {
      brief.append("- Levels (").append(levels.size()).append("): ");
      for (int i = 0; i < levels.size(); i++) {
        if (i > 0) {
          brief.append(", ");
        }
        String name = levels.get(i).getName();
        brief.append(name != null && name.length() > 0 ? name : "Level " + (i + 1))
            .append(" at ").append(format.format(levels.get(i).getElevation())).append(" cm");
        if (levels.get(i) == home.getSelectedLevel()) {
          brief.append(" (selected: new items go here)");
        }
      }
      brief.append('\n');
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
    if (!rooms.isEmpty()) {
      brief.append(":\n");
      for (int i = 0; i < rooms.size(); i++) {
        Room room = rooms.get(i);
        String name = room.getName();
        brief.append("    - R").append(i + 1).append(' ')
            .append(name != null && name.length() > 0 ? name : "Unnamed room")
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
    if (!walls.isEmpty()) {
      brief.append(":\n");
      for (int i = 0; i < walls.size(); i++) {
        Wall wall = walls.get(i);
        brief.append("    - W").append(i + 1).append(" (")
            .append(format.format(wall.getXStart())).append(", ").append(format.format(wall.getYStart()))
            .append(") to (").append(format.format(wall.getXEnd())).append(", ")
            .append(format.format(wall.getYEnd())).append(") cm, ")
            .append(format.format(wall.getThickness())).append(" thick");
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
    if (!topFurniture.isEmpty()) {
      brief.append("- Furniture items (id at x,y cm, facing deg, width x depth cm):\n");
      int limit = Math.min(topFurniture.size(), MAX_LISTED_FURNITURE);
      for (int i = 0; i < limit; i++) {
        HomePieceOfFurniture piece = topFurniture.get(i);
        String name = piece.getName() != null ? piece.getName() : "Unnamed";
        brief.append("    - F").append(i + 1).append(" \"").append(name).append("\" at (")
            .append(format.format(piece.getX())).append(", ").append(format.format(piece.getY()))
            .append(") cm, facing ").append(Math.round(Math.toDegrees(piece.getAngle())))
            .append(" deg, ").append(format.format(piece.getWidth())).append(" x ")
            .append(format.format(piece.getDepth())).append(" cm");
        if (piece.getElevation() != 0) {
          brief.append(", elevation ").append(format.format(piece.getElevation()));
        }
        if (piece.isDoorOrWindow()) {
          brief.append(" (door/window)");
        }
        appendLevel(brief, piece.getLevel(), levels);
        if (!piece.isVisible()) {
          brief.append(" (hidden)");
        }
        Long modelSize = piece.getModelSize();
        if (modelSize != null && modelSize.longValue() >= 1024 * 1024) {
          brief.append(", model ").append(format.format(modelSize.longValue() / 1024.0 / 1024.0))
              .append(" MB");
        }
        if (piece.getModel() != null && home.getModelLOD(piece.getModel()) != null) {
          brief.append(ModelLOD.isReducedDetailInView(piece)
              ? ", reduced detail ON" : ", reduced model available");
        }
        brief.append('\n');
      }
      if (topFurniture.size() > limit) {
        brief.append("    ... and ").append(topFurniture.size() - limit).append(" more\n");
      }
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
        + "  {\"action\":\"add_room\",\"name\":\"<name>\",\"points\":[[x,y],[x,y],[x,y],...]}\n"
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
        + "  {\"action\":\"add_level\",\"name\":\"<name>\",\"elevation\":N,\"height\":N}  (adds a floor above and selects it)\n"
        + "  {\"action\":\"select_level\",\"name\":\"<level name>\"}  (new items go to the selected level)\n"
        + "  {\"action\":\"add_label\",\"text\":\"<text>\",\"x\":N,\"y\":N}\n"
        + "  {\"action\":\"add_dimension\",\"x1\":N,\"y1\":N,\"x2\":N,\"y2\":N,\"offset\":N}  (a measurement line)\n"
        + "  {\"action\":\"select\",\"names\":[\"<item name>\"]}\n"
        + "To MODIFY items that already exist, reference them by the id shown in the project "
        + "summary (F# furniture, W# walls, R# rooms), by \"target\":\"selection\", or by name. "
        + "Editing commands (omit a field to leave it unchanged):\n"
        + "  {\"action\":\"move\",\"id\":\"F1\",\"dx\":N,\"dy\":N}  (relative; or \"x\":N,\"y\":N for an absolute position)\n"
        + "  {\"action\":\"rotate\",\"id\":\"F1\",\"angle\":N}  (absolute degrees; or \"by\":N to add to the current angle)\n"
        + "  {\"action\":\"resize\",\"id\":\"F1\",\"width\":N,\"depth\":N,\"height\":N}\n"
        + "  {\"action\":\"set_color\",\"id\":\"F1\",\"color\":\"#RRGGBB\"}  (furniture; a wall W#: both sides or "
        + "\"part\":\"left\"|\"right\"; a room R#: its floor or \"part\":\"ceiling\")\n"
        + "  {\"action\":\"set_texture\",\"id\":\"R1\",\"texture\":\"<texture name>\",\"part\":\"floor|ceiling|left|right\"}  "
        + "(rooms, walls and furniture; find names with {\"action\":\"search_textures\",\"query\":\"<words>\"})\n"
        + "  {\"action\":\"duplicate\",\"id\":\"F1\",\"dx\":N,\"dy\":N,\"count\":N}  (copies of furniture, each shifted by dx,dy)\n"
        + "  {\"action\":\"set_camera\",\"x\":N,\"y\":N,\"height\":N,\"look_at_x\":N,\"look_at_y\":N,\"mode\":\"visitor|aerial\"}  "
        + "(moves the virtual visitor, also the starting point of the VR preview)\n"
        + "  {\"action\":\"check_home\"}  (reports plan defects: walls that don't meet, duplicates, rooms without door, doors out of walls, "
        + "furniture under the floor or outside rooms... the report comes back in the next turn)\n"
        + "  {\"action\":\"fix_home\"}  (repairs the defects marked fixable; \"types\":[\"wall_gap\"] limits it)\n"
        + "  {\"action\":\"set_elevation\",\"id\":\"F1\",\"elevation\":N}\n"
        + "  {\"action\":\"set_visible\",\"id\":\"F1\",\"visible\":true}\n"
        + "  {\"action\":\"rename\",\"id\":\"F1\",\"name\":\"<new name>\"}\n"
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
        + "- Use at most 30 commands per reply; coordinates beyond 1000000 cm and sizes beyond "
        + "100000 cm are rejected.";
  }

  /**
   * Appends the name of the level of an item when the home has more than one.
   */
  private static void appendLevel(StringBuilder brief, Level level, List<Level> levels) {
    if (level != null && levels.size() > 1) {
      String name = level.getName();
      brief.append(" [").append(name != null && name.length() > 0
          ? name : "Level " + (levels.indexOf(level) + 1)).append(']');
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
