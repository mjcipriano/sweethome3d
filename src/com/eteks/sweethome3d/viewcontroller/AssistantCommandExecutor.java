/*
 * AssistantCommandExecutor.java 10 June 2026
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
import java.util.Collections;
import java.util.List;

import javax.swing.undo.UndoableEditSupport;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * Applies the edit {@link AssistantCommand commands} produced by the AI design
 * assistant to a home. All the commands of one assistant turn are grouped into a
 * single undoable edit, so a single Edit &gt; Undo reverts the whole change.
 * Coordinates are in centimeters and angles in degrees, matching the project
 * description sent to the model.
 * @author Sweet Home 3D
 */
public class AssistantCommandExecutor {
  private final Home            home;
  private final UserPreferences preferences;
  private final HomeController  homeController;

  public AssistantCommandExecutor(Home home, UserPreferences preferences, HomeController homeController) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
  }

  /**
   * Runs the given <code>commands</code> as a single undoable change and returns
   * a short human-readable summary, or <code>null</code> if nothing was applied.
   */
  public String execute(List<AssistantCommand> commands) {
    if (commands == null || commands.isEmpty()) {
      return null;
    }
    int [] tally = new int [5]; // furniture, doors/windows, rooms, walls, not found
    List<String> notFound = new ArrayList<String>();
    UndoableEditSupport undoSupport = this.homeController.getUndoableEditSupport();
    undoSupport.beginUpdate();
    try {
      for (AssistantCommand command : commands) {
        applyCommand(command, tally, notFound);
      }
    } finally {
      undoSupport.endUpdate();
    }
    return buildSummary(tally, notFound);
  }

  private void applyCommand(AssistantCommand command, int [] tally, List<String> notFound) {
    String action = command.getAction();
    if ("add_furniture".equals(action)) {
      addFurniture(command, false, tally, notFound);
    } else if ("add_door_or_window".equals(action) || "add_door".equals(action)
        || "add_window".equals(action)) {
      addFurniture(command, true, tally, notFound);
    } else if ("add_room".equals(action)) {
      addRoom(command, tally);
    } else if ("add_wall".equals(action)) {
      addWall(command, tally);
    } else if ("select".equals(action)) {
      select(command);
    }
    // Unknown actions are ignored so a single bad command doesn't break a turn
  }

  private void addFurniture(AssistantCommand command, boolean doorOrWindow,
                            int [] tally, List<String> notFound) {
    String name = command.getString("name");
    if (name == null || name.trim().length() == 0) {
      return;
    }
    CatalogPieceOfFurniture catalogPiece = findCatalogPiece(name, doorOrWindow);
    if (catalogPiece == null) {
      notFound.add(name);
      return;
    }
    HomePieceOfFurniture piece =
        this.homeController.getFurnitureController().createHomePieceOfFurniture(catalogPiece);
    piece.setX((float)command.getDouble("x", piece.getX()));
    piece.setY((float)command.getDouble("y", piece.getY()));
    piece.setAngle((float)Math.toRadians(command.getDouble("angle", 0)));
    double width = command.getDouble("width", Double.NaN);
    if (!Double.isNaN(width) && width > 0) {
      piece.setWidth((float)width);
    }
    double depth = command.getDouble("depth", Double.NaN);
    if (!Double.isNaN(depth) && depth > 0) {
      piece.setDepth((float)depth);
    }
    double elevation = command.getDouble("elevation", Double.NaN);
    if (!Double.isNaN(elevation)) {
      piece.setElevation((float)elevation);
    }
    piece.setLevel(this.home.getSelectedLevel());
    this.homeController.getFurnitureController().addFurniture(Collections.singletonList(piece));
    if (doorOrWindow || catalogPiece.isDoorOrWindow()) {
      tally [1]++;
    } else {
      tally [0]++;
    }
  }

  private void addRoom(AssistantCommand command, int [] tally) {
    List<float []> points = command.getPoints("points");
    if (points.size() < 3) {
      return;
    }
    Room room = new Room(points.toArray(new float [points.size()][]));
    String name = command.getString("name");
    if (name != null && name.trim().length() > 0) {
      room.setName(name);
    }
    room.setLevel(this.home.getSelectedLevel());
    this.homeController.getPlanController().addRooms(Collections.singletonList(room));
    tally [2]++;
  }

  private void addWall(AssistantCommand command, int [] tally) {
    float xStart = (float)command.getDouble("x1", Double.NaN);
    float yStart = (float)command.getDouble("y1", Double.NaN);
    float xEnd = (float)command.getDouble("x2", Double.NaN);
    float yEnd = (float)command.getDouble("y2", Double.NaN);
    if (Float.isNaN(xStart) || Float.isNaN(yStart) || Float.isNaN(xEnd) || Float.isNaN(yEnd)) {
      return;
    }
    float thickness = (float)command.getDouble("thickness", this.preferences.getNewWallThickness());
    float height = (float)command.getDouble("height", this.preferences.getNewWallHeight());
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness, height);
    wall.setLevel(this.home.getSelectedLevel());
    this.homeController.getPlanController().addWalls(Collections.singletonList(wall));
    tally [3]++;
  }

  private void select(AssistantCommand command) {
    List<String> names = command.getStringList("names");
    if (names.isEmpty()) {
      return;
    }
    List<Selectable> selection = new ArrayList<Selectable>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getName() != null && containsName(names, piece.getName())) {
        selection.add(piece);
      }
    }
    for (Room room : this.home.getRooms()) {
      if (room.getName() != null && containsName(names, room.getName())) {
        selection.add(room);
      }
    }
    if (!selection.isEmpty()) {
      this.home.setSelectedItems(selection);
    }
  }

  private static boolean containsName(List<String> names, String candidate) {
    for (String name : names) {
      if (name.equalsIgnoreCase(candidate)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the first catalog piece whose name matches <code>query</code>
   * (exact match preferred, then a substring match), optionally restricted to
   * doors and windows, or <code>null</code>.
   */
  CatalogPieceOfFurniture findCatalogPiece(String query, boolean doorOrWindow) {
    String normalizedQuery = query.trim().toLowerCase();
    CatalogPieceOfFurniture substringMatch = null;
    for (FurnitureCategory category : this.preferences.getFurnitureCatalog().getCategories()) {
      for (CatalogPieceOfFurniture piece : category.getFurniture()) {
        if (doorOrWindow && !piece.isDoorOrWindow()) {
          continue;
        }
        String name = piece.getName();
        if (name == null) {
          continue;
        }
        String normalizedName = name.toLowerCase();
        if (normalizedName.equals(normalizedQuery)) {
          return piece;
        } else if (substringMatch == null
            && (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName))) {
          substringMatch = piece;
        }
      }
    }
    return substringMatch;
  }

  private String buildSummary(int [] tally, List<String> notFound) {
    List<String> parts = new ArrayList<String>();
    addPart(parts, tally [0], "piece of furniture", "pieces of furniture");
    addPart(parts, tally [1], "door/window", "doors/windows");
    addPart(parts, tally [2], "room", "rooms");
    addPart(parts, tally [3], "wall", "walls");
    StringBuilder summary = new StringBuilder();
    if (!parts.isEmpty()) {
      summary.append("Applied changes: added ");
      for (int i = 0; i < parts.size(); i++) {
        if (i > 0) {
          summary.append(i == parts.size() - 1 ? " and " : ", ");
        }
        summary.append(parts.get(i));
      }
      summary.append(". Use Edit > Undo to revert.");
    }
    if (!notFound.isEmpty()) {
      if (summary.length() > 0) {
        summary.append('\n');
      }
      summary.append("Couldn't find furniture named: ").append(join(notFound)).append('.');
    }
    return summary.length() > 0 ? summary.toString() : null;
  }

  private static void addPart(List<String> parts, int count, String singular, String plural) {
    if (count == 1) {
      parts.add("1 " + singular);
    } else if (count > 1) {
      parts.add(count + " " + plural);
    }
  }

  private static String join(List<String> values) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append('"').append(values.get(i)).append('"');
    }
    return builder.toString();
  }
}
