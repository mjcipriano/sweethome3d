/*
 * AssistantBuildingCommands.java 13 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.viewcontroller.HomeDiagnostics.Issue;

/**
 * The commands of the design assistant that build and repair the structure of
 * a home: rooms with their walls, levels, roofs, colors and textures of walls
 * and rooms, annotations, and the check and repair of plan defects. They are
 * run by {@link AssistantCommandExecutor} as part of its undoable turn.
 * @author Sweet Home 3D
 */
class AssistantBuildingCommands {
  private static final int   DEFAULT_ROOF_COLOR = 0x9C4A38;
  private static final float DEFAULT_ROOF_PITCH = 30;
  private static final float DEFAULT_ROOF_OVERHANG = 30;
  private static final String ROOF_LEVEL_NAME = "Roof";
  private static final int   MAX_COPIES = 20;
  private static final int   MAX_TEXTURE_RESULTS = 8;

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantBuildingCommands(Home home, UserPreferences preferences, HomeController homeController,
                            AssistantCommandExecutor executor) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    if ("add_rectangular_room".equals(action) || "add_room_with_walls".equals(action)) {
      addRectangularRoom(command, tally, notes);
    } else if ("modify_room".equals(action) || "modify_rooms".equals(action)) {
      modifyRooms(command, tally, notes);
    } else if ("add_level".equals(action)) {
      addLevel(command, notes);
    } else if ("select_level".equals(action)) {
      selectLevel(command, notes);
    } else if ("auto_roof".equals(action) || "add_roof".equals(action)) {
      autoRoof(command, tally, notes);
    } else if ("set_texture".equals(action)) {
      setTexture(command, tally, notes);
    } else if ("search_textures".equals(action)) {
      String query = command.getString("query");
      if (query != null && query.trim().length() > 0) {
        notes.add("Texture search \"" + query.trim() + "\": " + describeTextures(searchTextures(query, MAX_TEXTURE_RESULTS)));
      }
    } else if ("add_label".equals(action)) {
      addLabel(command, notes);
    } else if ("add_dimension".equals(action) || "add_dimension_line".equals(action)) {
      addDimensionLine(command, notes);
    } else if ("auto_dimensions".equals(action) || "add_room_dimensions".equals(action)) {
      List<Room> rooms = new ArrayList<Room>();
      for (Selectable item : this.executor.resolveTargets(command)) {
        if (item instanceof Room) {
          rooms.add((Room)item);
        }
      }
      if (rooms.isEmpty()) {
        for (Room room : this.home.getRooms()) {
          if (room.isAtLevel(this.home.getSelectedLevel())) {
            rooms.add(room);
          }
        }
      }
      List<DimensionLine> dimensionLines = RoomDimensions.createDimensionLines(this.home, rooms);
      if (!dimensionLines.isEmpty()) {
        this.executor.beginTurnUpdate();
        this.homeController.getPlanController().addDimensionLines(dimensionLines);
      }
      notes.add("Added " + dimensionLines.size() + " dimension lines to " + rooms.size()
          + (rooms.size() == 1 ? " room" : " rooms"));
    } else if ("duplicate".equals(action)) {
      duplicate(command, tally, notes);
    } else if ("set_camera".equals(action)) {
      setCamera(command, notes);
    } else if ("store_point_of_view".equals(action)) {
      String name = command.getString("name");
      if (name == null || name.trim().length() == 0) {
        notes.add("Ignored store_point_of_view without \"name\"");
      } else if (command.getParameters().get("camera") instanceof Map || command.has("view") || command.has("x")) {
        // A point of view given by the command: stored without moving the camera of the user
        Camera userCamera = this.home.getCamera();
        Camera [] cameras = {this.home.getTopCamera(), this.home.getObserverCamera()};
        float [][] states = new float [cameras.length][];
        for (int i = 0; i < cameras.length; i++) {
          states [i] = new float [] {cameras [i].getX(), cameras [i].getY(), cameras [i].getZ(), cameras [i].getYaw(),
                                     cameras [i].getPitch(), cameras [i].getFieldOfView()};
        }
        java.util.Map<String, Object> parameters = new java.util.LinkedHashMap<String, Object>(command.getParameters());
        if (parameters.get("camera") instanceof Map) {
          for (Map.Entry<?, ?> entry : ((Map<?, ?>)parameters.get("camera")).entrySet()) {
            parameters.put(String.valueOf(entry.getKey()), entry.getValue());
          }
        }
        parameters.remove("name");
        if (parameters.get("x") instanceof Number && parameters.get("view") == null && parameters.get("mode") == null) {
          // A place and a direction are the ones of the virtual visitor
          parameters.put("mode", "visitor");
        }
        if (parameters.get("look_at_height") instanceof Number && parameters.get("height") instanceof Number
            && parameters.get("look_at_x") instanceof Number && parameters.get("look_at_y") instanceof Number
            && parameters.get("x") instanceof Number && parameters.get("y") instanceof Number && parameters.get("pitch") == null) {
          double distance = Math.hypot(((Number)parameters.get("look_at_x")).doubleValue() - ((Number)parameters.get("x")).doubleValue(),
              ((Number)parameters.get("look_at_y")).doubleValue() - ((Number)parameters.get("y")).doubleValue());
          parameters.put("pitch", Double.valueOf(Math.toDegrees(Math.atan2(((Number)parameters.get("height")).doubleValue()
              - ((Number)parameters.get("look_at_height")).doubleValue(), distance))));
        }
        List<String> cameraNotes = new ArrayList<String>();
        setCamera(new AssistantCommand("set_camera", parameters), cameraNotes);
        String description = HomeViewpoints.describeCamera(this.home);
        this.homeController.getHomeController3D().storeCamera(name.trim());
        for (int i = 0; i < cameras.length; i++) {
          cameras [i].setX(states [i][0]);
          cameras [i].setY(states [i][1]);
          cameras [i].setZ(states [i][2]);
          cameras [i].setYaw(states [i][3]);
          cameras [i].setPitch(states [i][4]);
          cameras [i].setFieldOfView(states [i][5]);
        }
        if (this.home.getCamera() != userCamera) {
          this.home.setCamera(userCamera);
        }
        notes.add("Stored the point of view \"" + name.trim() + "\" (" + description + ") without moving the camera of the user. "
            + "get_3d_image {\"point_of_view\":\"" + name.trim() + "\"} draws it, and \"compare_with\":\"<photo>\" "
            + "sets a photo beside the picture");
      } else {
        this.homeController.getHomeController3D().storeCamera(name.trim());
        notes.add("Stored the point of view \"" + name.trim() + "\" (" + HomeViewpoints.describeCamera(this.home) + ")");
      }
    } else if ("check_home".equals(action)) {
      notes.add(this.executor.checkHome(command.getParameters()));
    } else if ("fix_home".equals(action)) {
      fixHome(command, tally, notes);
    } else {
      return false;
    }
    return true;
  }

  /**
   * Returns the issues of the home, with the ids of the items of the turn.
   */
  List<Issue> check() {
    List<Issue> issues = HomeDiagnostics.check(this.home, this.executor.getTurnFurniture(),
        this.executor.getTurnWalls(), this.executor.getTurnRooms());
    issues.addAll(this.executor.getFenceCommands().check());
    return issues;
  }

  // ---- Rooms ----

  /**
   * Adds a rectangular room and the walls around it. A side along which a wall
   * already stands (the wall shared with the next room) gets no second wall.
   */
  private void addRectangularRoom(AssistantCommand command, int [] tally, List<String> notes) {
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    double width = command.getDouble("width", Double.NaN);
    double depth = command.getDouble("depth", Double.NaN);
    String nextTo = command.getString("next_to");
    if (nextTo != null && width > 0 && depth > 0) {
      // Against a side of an existing room, sharing the wall between them
      AssistantCommand target = new AssistantCommand("target",
          Collections.<String, Object>singletonMap("id", nextTo));
      Room neighbor = null;
      for (Selectable item : this.executor.resolveTargets(target)) {
        if (item instanceof Room) {
          neighbor = (Room)item;
        }
      }
      if (neighbor == null) {
        // Or the name of a room, which may have been added earlier in the turn
        for (Room room : this.home.getRooms()) {
          if (nextTo.trim().equalsIgnoreCase(room.getName())) {
            neighbor = room;
          }
        }
      }
      String side = command.getString("side") != null
          ? command.getString("side").trim().toLowerCase(Locale.ENGLISH) : "east";
      if (neighbor == null) {
        notes.add("Ignored add_rectangular_room: \"next_to\" must be the id or the name of a room");
        return;
      }
      float [] bounds = getBounds(Collections.singletonList(neighbor));
      // The thickness of the wall between both rooms
      float gap = (float)command.getDouble("thickness", this.preferences.getNewWallThickness());
      for (Wall wall : this.home.getWalls()) {
        if (wall.getLevel() == neighbor.getLevel() && wall.getLength() > 1) {
          boolean vertical = Math.abs(wall.getXStart() - wall.getXEnd()) < 1;
          boolean horizontal = Math.abs(wall.getYStart() - wall.getYEnd()) < 1;
          float limit = wall.getThickness() + 2;
          if ("east".equals(side) && vertical && Math.abs(wall.getXStart() - bounds [2]) <= limit
                  && wall.getXStart() >= bounds [2] - 1
              || "west".equals(side) && vertical && Math.abs(wall.getXStart() - bounds [0]) <= limit
                  && wall.getXStart() <= bounds [0] + 1
              || "south".equals(side) && horizontal && Math.abs(wall.getYStart() - bounds [3]) <= limit
                  && wall.getYStart() >= bounds [3] - 1
              || "north".equals(side) && horizontal && Math.abs(wall.getYStart() - bounds [1]) <= limit
                  && wall.getYStart() <= bounds [1] + 1) {
            gap = wall.getThickness();
            break;
          }
        }
      }
      double offset = command.getDouble("offset", 0);
      if ("east".equals(side)) {
        x = bounds [2] + gap;
        y = bounds [1] + offset;
      } else if ("west".equals(side)) {
        x = bounds [0] - gap - width;
        y = bounds [1] + offset;
      } else if ("south".equals(side)) {
        x = bounds [0] + offset;
        y = bounds [3] + gap;
      } else if ("north".equals(side)) {
        x = bounds [0] + offset;
        y = bounds [1] - gap - depth;
      } else {
        notes.add("Ignored add_rectangular_room: \"side\" must be north, south, east or west");
        return;
      }
      if (!command.has("thickness")) {
        // Walls of the same thickness as the shared one
        command = withParameter(command, "thickness", Double.valueOf(gap));
      }
    }
    if (Double.isNaN(x) || Double.isNaN(y) || !(width > 0) || !(depth > 0)
        || !AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)
        || !AssistantCommandExecutor.sizeSane(width) || !AssistantCommandExecutor.sizeSane(depth)) {
      notes.add("Ignored add_rectangular_room: x, y, width and depth are required and must be in range");
      return;
    }
    float x1 = (float)x;
    float y1 = (float)y;
    float x2 = (float)(x + width);
    float y2 = (float)(y + depth);
    Level level = this.home.getSelectedLevel();
    this.executor.beginTurnUpdate();

    int wallCount = 0;
    int sharedCount = 0;
    if (command.getBoolean("walls", true)) {
      float thickness = (float)command.getDouble("thickness", this.preferences.getNewWallThickness());
      if (!(thickness > 0) || !AssistantCommandExecutor.sizeSane(thickness)) {
        thickness = this.preferences.getNewWallThickness();
      }
      float height = (float)command.getDouble("height", this.preferences.getNewWallHeight());
      if (!(height > 0) || !AssistantCommandExecutor.sizeSane(height)) {
        height = this.preferences.getNewWallHeight();
      }
      // The room is the space inside the walls: their middle lines run half a
      // thickness outside it, clockwise from the top left corner
      float h = thickness / 2;
      float [][] sides = {
          {x1 - h, y1 - h, x2 + h, y1 - h}, {x2 + h, y1 - h, x2 + h, y2 + h},
          {x2 + h, y2 + h, x1 - h, y2 + h}, {x1 - h, y2 + h, x1 - h, y1 - h}};
      Wall [] walls = new Wall [4];
      for (int i = 0; i < 4; i++) {
        if (isAlongExistingWall(sides [i], thickness, level)) {
          sharedCount++;
        } else {
          walls [i] = new Wall(sides [i][0], sides [i][1], sides [i][2], sides [i][3], thickness, height);
          walls [i].setLevel(level);
        }
      }
      List<Wall> addedWalls = new ArrayList<Wall>();
      for (int i = 0; i < 4; i++) {
        Wall next = walls [(i + 1) % 4];
        if (walls [i] != null) {
          if (next != null) {
            walls [i].setWallAtEnd(next);
            next.setWallAtStart(walls [i]);
          }
          addedWalls.add(walls [i]);
        }
      }
      if (!addedWalls.isEmpty()) {
        this.homeController.getPlanController().addWalls(addedWalls);
        wallCount = addedWalls.size();
        tally [3] += wallCount;
      }
    }

    Room room = new Room(new float [][] {{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}});
    String name = command.getString("name");
    if (name != null && name.trim().length() > 0) {
      room.setName(name.trim());
    }
    Integer floorColor = AssistantCommandExecutor.parseColor(command.getString("floor_color"));
    if (floorColor != null) {
      room.setFloorColor(floorColor);
    }
    room.setLevel(level);
    this.homeController.getPlanController().addRooms(Collections.singletonList(room));
    tally [2]++;
    if (sharedCount > 0) {
      notes.add("The new room shares " + sharedCount + (sharedCount == 1 ? " existing wall" : " existing walls")
          + " (no wall was added on " + (sharedCount == 1 ? "that side" : "those sides") + ")");
    }
  }

  /**
   * Changes the name, the shape and what is displayed of existing rooms.
   */
  private void modifyRooms(final AssistantCommand command, int [] tally, List<String> notes) {
    final List<Selectable> rooms = new ArrayList<Selectable>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Room) {
        rooms.add(item);
      }
    }
    // A room may also be designated by its name
    List<String> names = new ArrayList<String>();
    for (String field : new String [] {"room", "id", "target"}) {
      if (command.getString(field) != null) {
        names.add(command.getString(field).trim());
      }
    }
    for (String id : command.getStringList("ids")) {
      names.add(id.trim());
    }
    for (String name : names) {
      if (name.length() == 0 || name.matches("[FWRfwr]\\s*\\d+")
          || "selection".equalsIgnoreCase(name) || "selected".equalsIgnoreCase(name)) {
        continue;
      }
      List<Room> namedRooms = new ArrayList<Room>();
      for (Room room : this.home.getRooms()) {
        if (name.equalsIgnoreCase(room.getName())) {
          namedRooms.add(room);
        }
      }
      if (namedRooms.size() > 1) {
        StringBuilder ids = new StringBuilder();
        for (Room room : namedRooms) {
          String id = this.executor.getId(room);
          if (id != null) {
            ids.append(ids.length() > 0 ? ", " : "").append(id);
          }
        }
        notes.add("Ignored modify_room for \"" + name + "\": " + namedRooms.size() + " rooms have that name"
            + (ids.length() > 0 ? " (" + ids + ")" : "") + ", give the id of one");
      } else if (namedRooms.isEmpty()) {
        notes.add("Ignored modify_room for \"" + name + "\": no room has that id or name");
      } else if (!rooms.contains(namedRooms.get(0))) {
        rooms.add(namedRooms.get(0));
      }
    }
    if (rooms.isEmpty()) {
      if (names.isEmpty()) {
        notes.add("Ignored modify_room: give the room with \"id\", \"ids\", \"target\":\"selection\" or its name");
      }
      return;
    }

    final List<float []> points = command.getPoints("points");
    if (command.has("points")) {
      if (rooms.size() > 1) {
        notes.add("Ignored modify_room: \"points\" reshapes one room, not " + rooms.size());
        return;
      } else if (points.size() < 3) {
        notes.add("Ignored modify_room: \"points\" needs at least 3 points [x,y]");
        return;
      }
      for (float [] point : points) {
        if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
          notes.add("Ignored modify_room with out-of-range points");
          return;
        }
      }
    }
    final String name = command.getString("name");
    final boolean floorOffset = command.has("floor_offset");
    final boolean ceiling = command.has("ceiling") || command.has("flat_ceiling_at");
    final String ceilingMode = command.getString("ceiling") != null ? command.getString("ceiling") : "follow_roof";
    final boolean followRoof = ceiling && !ceilingMode.trim().toLowerCase(Locale.ENGLISH).matches("flat|level|none|default");
    final boolean ceilingHeight = AssistantCeilingCommands.hasCeilingHeight(command);
    // The color of the walls of the rooms, which the wall sides that face them get
    boolean wallFinish = this.executor.getWallFinishCommands().modifyRoomFinish(command, rooms, tally, notes);
    if (wallFinish && !command.has("points") && !command.has("ceiling_visible") && !command.has("floor_visible")
        && !command.has("area_visible") && name == null && !floorOffset && !ceiling) {
      return;
    }
    if (!command.has("points") && !command.has("ceiling_visible") && !command.has("floor_visible")
        && !command.has("area_visible") && name == null && !floorOffset && !ceiling && !ceilingHeight) {
      notes.add("Ignored modify_room: give at least one of \"ceiling_visible\", \"floor_visible\", "
          + "\"area_visible\", \"name\", \"points\", \"floor_offset\", \"ceiling\", \"flat_ceiling_at\", "
          + "\"ceiling_height\" and \"ceiling_level\"");
      return;
    }
    this.executor.postModification(rooms, new Runnable() {
        public void run() {
          for (Selectable item : rooms) {
            Room room = (Room)item;
            if (command.has("ceiling_visible")) {
              room.setCeilingVisible(command.getBoolean("ceiling_visible", room.isCeilingVisible()));
            } else if (ceiling) {
              // The ceiling under the roof replaces the flat one at the height of the level
              room.setCeilingVisible(!followRoof);
            } else if (ceilingHeight && !room.isCeilingVisible()
                       && !"level".equalsIgnoreCase(command.getString("ceiling_height"))
                       && room.getProperty(AssistantShapeCommands.ROOM_CEILING_PROPERTY) == null) {
              // A ceiling placed at a height is one to see
              room.setCeilingVisible(true);
            }
            if (command.has("floor_visible")) {
              room.setFloorVisible(command.getBoolean("floor_visible", room.isFloorVisible()));
            } else if (floorOffset) {
              // A slab replaces the floor at the height of the level
              room.setFloorVisible(false);
            }
            if (command.has("area_visible")) {
              room.setAreaVisible(command.getBoolean("area_visible", room.isAreaVisible()));
            }
            if (name != null) {
              room.setName(name.trim().length() > 0 ? name.trim() : null);
            }
            if (command.has("points")) {
              room.setPoints(points.toArray(new float [points.size()][]));
            }
          }
        }
      });
    tally [4] += rooms.size();
    List<String> slabs = new ArrayList<String>();
    if (floorOffset) {
      for (Selectable item : rooms) {
        try {
          slabs.add(this.executor.getShapeCommands().setRoomFloorOffset((Room)item,
              command.getDouble("floor_offset", 0), command.getDouble("slab_thickness", 10)));
        } catch (IOException ex) {
          slabs.add("no slab (" + ex.getMessage() + ")");
        }
      }
    }
    List<String> ceilings = new ArrayList<String>();
    if (ceiling) {
      for (Selectable item : rooms) {
        try {
          ceilings.add(this.executor.getShapeCommands().setRoomCeiling((Room)item, ceilingMode,
              command.getDouble("flat_ceiling_at", Double.NaN)));
        } catch (IOException ex) {
          ceilings.add("its ceiling doesn't follow a roof (" + ex.getMessage() + ")");
        }
      }
    }
    // The ceilings placed at their own height
    List<String> ceilingHeights = new ArrayList<String>();
    if (ceilingHeight) {
      for (Selectable item : rooms) {
        try {
          ceilingHeights.add(this.executor.getCeilingCommands().setCeilingHeight((Room)item, command));
        } catch (IOException ex) {
          ceilingHeights.add("its ceiling didn't change (" + ex.getMessage() + ")");
        }
      }
    }
    // What each room is now, to check the result without describing the home again
    StringBuilder note = new StringBuilder("Modified ");
    for (int i = 0; i < rooms.size(); i++) {
      Room room = (Room)rooms.get(i);
      String id = this.executor.getId(room);
      note.append(i > 0 ? "; " : "").append(id != null ? id + " " : "")
          .append(room.getName() != null ? "\"" + room.getName() + "\"" : "unnamed room")
          .append(": ceiling ").append(room.isCeilingVisible() ? "visible" : "hidden")
          .append(", floor ").append(room.isFloorVisible() ? "visible" : "hidden")
          .append(", area ").append(room.isAreaVisible() ? "displayed" : "not displayed")
          .append(", ").append(Math.round(room.getArea() / 100) / 100f).append(" m2");
      if (i < slabs.size()) {
        note.append(", ").append(slabs.get(i));
      }
      if (i < ceilingHeights.size()) {
        note.append(", ").append(ceilingHeights.get(i));
      }
      if (i < ceilings.size()) {
        note.append(", ").append(ceilings.get(i));
        String headroom = this.executor.getShapeCommands().describeHeadroom(room);
        if (headroom != null) {
          note.append(", ").append(headroom);
        }
      }
    }
    notes.add(note.toString());
  }

  private static AssistantCommand withParameter(AssistantCommand command, String name, Object value) {
    java.util.Map<String, Object> parameters = new java.util.HashMap<String, Object>(command.getParameters());
    parameters.put(name, value);
    return new AssistantCommand(command.getAction(), parameters);
  }

  /**
   * Returns <code>true</code> if an existing wall of <code>level</code> already
   * runs along the whole given segment.
   */
  boolean isAlongExistingWall(float [] side, float thickness, Level level) {
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == level && wall.getLength() > 1) {
        float tolerance = wall.getThickness() / 2 + thickness / 2 + 2;
        if (HomeDiagnostics.getDistanceToWall(side [0], side [1], wall) <= tolerance
            && HomeDiagnostics.getDistanceToWall(side [2], side [3], wall) <= tolerance) {
          return true;
        }
      }
    }
    return false;
  }

  // ---- Levels ----

  private void addLevel(AssistantCommand command, List<String> notes) {
    double elevation = command.getDouble("elevation", Double.NaN);
    double height = command.getDouble("height", Double.NaN);
    double floorThickness = command.getDouble("floor_thickness", Double.NaN);
    if (!AssistantCommandExecutor.sizeSane(elevation) || !AssistantCommandExecutor.sizeSane(height)
        || !AssistantCommandExecutor.sizeSane(floorThickness)) {
      notes.add("Ignored add_level with out-of-range values");
      return;
    }
    Level level = createLevel(command.getString("name"), elevation, height, floorThickness);
    notes.add("Added level \"" + level.getName() + "\" at elevation " + Math.round(level.getElevation())
        + " cm and selected it: new items go to this level");
  }

  /**
   * Adds a level above the others with the undoable edit of the plan
   * controller, then gives it the given characteristics (NaN keeps a default).
   */
  Level createLevel(String name, double elevation, double height, double floorThickness) {
    this.executor.beginTurnUpdate();
    this.homeController.getPlanController().addLevel();
    Level level = this.home.getSelectedLevel();
    if (name != null && name.trim().length() > 0) {
      level.setName(name.trim());
    }
    if (!Double.isNaN(elevation)) {
      level.setElevation((float)elevation);
    }
    if (height > 0) {
      level.setHeight((float)height);
    }
    if (floorThickness > 0) {
      level.setFloorThickness((float)floorThickness);
    }
    return level;
  }

  private void selectLevel(AssistantCommand command, List<String> notes) {
    Level level = findLevel(command.getString("name"), command.getDouble("index", Double.NaN));
    if (level == null) {
      notes.add("Ignored select_level: no such level");
    } else {
      this.home.setSelectedLevel(level);
      notes.add("Selected level \"" + level.getName() + "\": new items go to this level");
    }
  }

  Level findLevel(String name, double index) {
    List<Level> levels = this.home.getLevels();
    if (name != null) {
      // An id L# or a name, which several levels may share
      List<Level> namedLevels = HomeItemIds.findLevels(this.home, name);
      if (namedLevels.size() > 1) {
        StringBuilder ids = new StringBuilder();
        for (Level level : namedLevels) {
          ids.append(ids.length() > 0 ? ", " : "").append(this.executor.getId(level));
        }
        this.executor.warn(namedLevels.size() + " levels are named \"" + name.trim() + "\" (" + ids
            + "): the first one was used, give an id to choose an other one");
      }
      if (!namedLevels.isEmpty()) {
        return namedLevels.get(0);
      }
    }
    if (!Double.isNaN(index) && index >= 1 && index <= levels.size()) {
      return levels.get((int)index - 1);
    }
    return null;
  }

  // ---- Roof ----

  /**
   * Adds a roof over the walls of the selected level, or over the rooms and
   * walls the command targets.
   */
  private void autoRoof(AssistantCommand command, int [] tally, List<String> notes) {
    AssistantRoofBuilder.Type type = command.getString("type") != null
        ? AssistantRoofBuilder.Type.parse(command.getString("type"))
        : AssistantRoofBuilder.Type.GABLE;
    if (type == null) {
      notes.add("Ignored auto_roof: type must be gable, hip, shed or flat");
      return;
    }
    Level baseLevel = this.home.getSelectedLevel();
    List<Selectable> targets = new ArrayList<Selectable>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Room || item instanceof Wall) {
        targets.add(item);
      }
    }
    boolean explicitFootprint = !targets.isEmpty();
    if (!explicitFootprint) {
      for (Wall wall : this.home.getWalls()) {
        if (wall.getLevel() == baseLevel) {
          targets.add(wall);
        }
      }
      if (targets.isEmpty()) {
        for (Room room : this.home.getRooms()) {
          if (room.getLevel() == baseLevel) {
            targets.add(room);
          }
        }
      }
    } else {
      baseLevel = targets.get(0) instanceof Room
          ? ((Room)targets.get(0)).getLevel() : ((Wall)targets.get(0)).getLevel();
    }
    if (targets.isEmpty()) {
      notes.add("Ignored auto_roof: there are no walls or rooms to cover at this level");
      return;
    }

    double pitch = command.getDouble("pitch", DEFAULT_ROOF_PITCH);
    pitch = Math.max(1, Math.min(75, pitch));
    double overhang = command.getDouble("overhang", DEFAULT_ROOF_OVERHANG);
    if (!(overhang >= 0) || overhang > 500) {
      overhang = DEFAULT_ROOF_OVERHANG;
    }
    Integer color = AssistantCommandExecutor.parseColor(command.getString("color"));
    // The roof sits on top of the walls
    double wallTop = command.getDouble("elevation", Double.NaN);
    if (Double.isNaN(wallTop) || !AssistantCommandExecutor.sizeSane(wallTop)) {
      wallTop = 0;
      for (Wall wall : this.home.getWalls()) {
        if (wall.getLevel() == baseLevel) {
          float height = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
          if (wall.getHeightAtEnd() != null) {
            height = Math.max(height, wall.getHeightAtEnd());
          }
          wallTop = Math.max(wallTop, height);
        }
      }
      if (wallTop == 0) {
        wallTop = baseLevel != null ? baseLevel.getHeight() : this.home.getWallHeight();
      }
    }

    // One roof over everything, or one per targeted room
    List<float []> footprints = new ArrayList<float []>();
    if (command.getBoolean("per_room", false)) {
      for (Selectable item : targets) {
        if (item instanceof Room) {
          footprints.add(getBounds(Collections.singletonList(item)));
        }
      }
    }
    boolean roofPerRoom = false;
    if (footprints.isEmpty()) {
      float [] bounds = getBounds(targets);
      if (!explicitFootprint && !command.has("per_room")) {
        // A house that isn't a rectangle (L, T or U shaped) gets one roof per
        // room, so the roof doesn't cover the empty corners of its bounds
        List<float []> roomFootprints = new ArrayList<float []>();
        double roomsArea = 0;
        float wallThickness = 0;
        for (Wall wall : this.home.getWalls()) {
          if (wall.getLevel() == baseLevel) {
            wallThickness = Math.max(wallThickness, wall.getThickness());
          }
        }
        for (Room room : this.home.getRooms()) {
          if (room.getLevel() == baseLevel && room.getArea() >= 1) {
            float [] roomBounds = getBounds(Collections.singletonList(room));
            // Up to the outside of the walls around the room
            roomBounds [0] -= wallThickness;
            roomBounds [1] -= wallThickness;
            roomBounds [2] += wallThickness;
            roomBounds [3] += wallThickness;
            roomFootprints.add(roomBounds);
            roomsArea += room.getArea();
          }
        }
        double boundsArea = (double)(bounds [2] - bounds [0]) * (bounds [3] - bounds [1]);
        if (roomFootprints.size() > 1 && roomsArea < 0.8 * boundsArea) {
          footprints.addAll(roomFootprints);
          roofPerRoom = true;
        }
      }
      if (footprints.isEmpty()) {
        footprints.add(bounds);
      }
    }

    this.executor.beginTurnUpdate();
    float baseElevation = baseLevel != null ? baseLevel.getElevation() : 0;
    // A roof drawn at the level of the rooms would hide them in the plan: it
    // goes to a level of its own on top of the walls, as roofs usually do
    Level roofLevel = null;
    for (Level level : this.home.getLevels()) {
      if (ROOF_LEVEL_NAME.equalsIgnoreCase(level.getName())
          && Math.abs(level.getElevation() - (baseElevation + wallTop)) < 1) {
        roofLevel = level;
      }
    }
    if (roofLevel != null && !explicitFootprint && command.getBoolean("replace", true)) {
      // Replace the roof generated before
      List<Selectable> oldRoofs = new ArrayList<Selectable>();
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (piece.getLevel() == roofLevel && "true".equals(piece.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY))) {
          oldRoofs.add(piece);
        }
      }
      if (!oldRoofs.isEmpty()) {
        this.homeController.getPlanController().deleteItems(oldRoofs);
      }
    }
    if (roofLevel == null) {
      // Creating the first level of a home moves its items to a level 0
      roofLevel = createLevel(ROOF_LEVEL_NAME, Double.NaN, Double.NaN, Double.NaN);
      Level newBaseLevel = baseLevel != null ? baseLevel : this.home.getLevels().get(0);
      roofLevel.setElevation(newBaseLevel.getElevation() + (float)wallTop);
    }

    String ridge = command.getString("ridge");
    float maxHeight = 0;
    List<HomePieceOfFurniture> roofs = new ArrayList<HomePieceOfFurniture>();
    for (float [] bounds : footprints) {
      float width = bounds [2] - bounds [0] + 2 * (float)overhang;
      float depth = bounds [3] - bounds [1] + 2 * (float)overhang;
      if (width < 10 || depth < 10) {
        continue;
      }
      // The ridge runs along the longest side unless told otherwise
      boolean ridgeAlongWidth = "x".equalsIgnoreCase(ridge) ? true
          : "y".equalsIgnoreCase(ridge) ? false
          : width >= depth;
      try {
        AssistantRoofBuilder roof = AssistantRoofBuilder.build(type, width, depth, (float)pitch,
            ridgeAlongWidth, color != null ? color : DEFAULT_ROOF_COLOR);
        HomePieceOfFurniture piece = new HomePieceOfFurniture(new CatalogPieceOfFurniture(
            ROOF_LEVEL_NAME, roof.getIcon(), roof.getModel(), width, depth, roof.getHeight(), true, false));
        piece.setX((bounds [0] + bounds [2]) / 2);
        piece.setY((bounds [1] + bounds [3]) / 2);
        piece.setElevation(0);
        piece.setLevel(roofLevel);
        piece.setProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY, "true");
        piece.setProperty("roofType", type.name().toLowerCase(Locale.ENGLISH));
        roofs.add(piece);
        maxHeight = Math.max(maxHeight, roof.getHeight());
      } catch (IOException ex) {
        notes.add("Couldn't build the roof model (" + ex.getMessage() + ")");
        return;
      }
    }
    if (roofs.isEmpty()) {
      notes.add("Ignored auto_roof: the area to cover is too small");
      return;
    }
    roofLevel.setHeight(Math.max(maxHeight, 10));
    this.home.setSelectedLevel(roofLevel);
    this.homeController.getFurnitureController().addFurniture(roofs);
    tally [0] += roofs.size();
    // Go back to the level the user works at, and show every level in the 3D
    // view, where only the levels up to the selected one are displayed otherwise
    Level workLevel = baseLevel != null ? baseLevel : this.home.getLevels().get(0);
    this.home.setSelectedLevel(workLevel);
    boolean allLevelsShown = this.home.getEnvironment().isAllLevelsVisible();
    if (!allLevelsShown) {
      this.home.getEnvironment().setAllLevelsVisible(true);
    }
    HomePieceOfFurniture first = roofs.get(0);
    notes.add("Added " + (roofs.size() == 1 ? "a " : roofs.size() + " ")
        + type.name().toLowerCase(Locale.ENGLISH) + (roofs.size() == 1 ? " roof of " : " roofs, the first of ")
        + Math.round(first.getWidth()) + " x " + Math.round(first.getDepth()) + " cm rising "
        + Math.round(first.getHeight()) + " cm, on the level \"" + roofLevel.getName() + "\" at "
        + Math.round(roofLevel.getElevation()) + " cm"
        + (roofPerRoom ? "; the house isn't a rectangle, so each room got its own roof" : "")
        + (allLevelsShown ? "" : "; the 3D view now displays all levels so the roof shows")
        + ". The level \"" + workLevel.getName() + "\" is still the selected one");
  }

  /**
   * Returns the bounds {minX, minY, maxX, maxY} of the given rooms and walls.
   */
  private static float [] getBounds(List<? extends Selectable> items) {
    float [] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                       Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    for (Selectable item : items) {
      for (float [] point : item.getPoints()) {
        bounds [0] = Math.min(bounds [0], point [0]);
        bounds [1] = Math.min(bounds [1], point [1]);
        bounds [2] = Math.max(bounds [2], point [0]);
        bounds [3] = Math.max(bounds [3], point [1]);
      }
    }
    return bounds;
  }

  // ---- Textures ----

  /**
   * Returns the textures of the catalog matching <code>query</code> best.
   */
  List<CatalogTexture> searchTextures(String query, int maxResults) {
    final List<String> tokens = AssistantCommandExecutor.tokenize(query);
    List<CatalogTexture> textures = new ArrayList<CatalogTexture>();
    final List<Integer> scores = new ArrayList<Integer>();
    if (this.preferences.getTexturesCatalog() != null) {
      for (TexturesCategory category : this.preferences.getTexturesCatalog().getCategories()) {
        for (CatalogTexture texture : category.getTextures()) {
          if (texture.getName() == null) {
            continue;
          }
          // Names are compared in lower case, so the exact name comes first
          String name = texture.getName().toLowerCase(Locale.ENGLISH);
          String lowerQuery = query.trim().toLowerCase(Locale.ENGLISH);
          int score = Math.max(
              AssistantCommandExecutor.scoreMatch(name, lowerQuery, tokens),
              AssistantCommandExecutor.scoreMatch(category.getName().toLowerCase(Locale.ENGLISH) + " " + name,
                  lowerQuery, tokens) - 1);
          if (score > 0) {
            // Insert by decreasing score, keeping the catalog order between equal scores
            int index = 0;
            while (index < scores.size() && scores.get(index) >= score) {
              index++;
            }
            scores.add(index, score);
            textures.add(index, texture);
          }
        }
      }
    }
    return textures.size() > maxResults ? new ArrayList<CatalogTexture>(textures.subList(0, maxResults)) : textures;
  }

  static String describeTextures(List<CatalogTexture> textures) {
    if (textures.isEmpty()) {
      return "no matches";
    }
    StringBuilder text = new StringBuilder();
    for (CatalogTexture texture : textures) {
      if (text.length() > 0) {
        text.append(", ");
      }
      text.append('"').append(texture.getName()).append('"');
      // The catalog id tells apart the textures a name and a category don't
      boolean twin = false;
      for (CatalogTexture other : textures) {
        twin |= other != texture && texture.getName() != null && texture.getName().equalsIgnoreCase(other.getName())
            && (other.getCategory() == null || texture.getCategory() == null
                || other.getCategory().getName().equals(texture.getCategory().getName()));
      }
      if (texture.getCategory() != null || twin && texture.getId() != null) {
        text.append(" (").append(texture.getCategory() != null ? texture.getCategory().getName() : "")
            .append(twin && texture.getId() != null
                ? (texture.getCategory() != null ? ", " : "") + "catalog_id " + texture.getId() : "")
            .append(')');
      }
    }
    return text.toString();
  }

  private void setTexture(AssistantCommand command, int [] tally, List<String> notes) {
    String name = command.getString("texture");
    if (name == null) {
      name = command.getString("name");
    }
    final List<Selectable> targets = this.executor.resolveTargets(command);
    if (name == null && command.getString("catalog_id") == null || targets.isEmpty()) {
      return;
    }
    String [] ambiguityNote = new String [1];
    final CatalogTexture catalogTexture = findTexture(name, command.getString("category"),
        command.getString("catalog_id"), "set_texture", notes, ambiguityNote);
    if (catalogTexture == null) {
      return;
    }
    String ambiguity = ambiguityNote [0];
    final float [] placement = getTexturePlacement(command.getParameters());
    final String part = command.getString("part") != null
        ? command.getString("part").toLowerCase(Locale.ENGLISH) : "";
    final int [] changed = {0};
    this.executor.postModification(targets, new Runnable() {
        public void run() {
          for (Selectable item : targets) {
            HomeTexture texture = createTexture(catalogTexture, placement);
            if (item instanceof Room) {
              if ("ceiling".equals(part)) {
                ((Room)item).setCeilingTexture(texture);
              } else {
                ((Room)item).setFloorTexture(texture);
              }
              changed [0]++;
            } else if (item instanceof Wall) {
              if (!"right".equals(part)) {
                ((Wall)item).setLeftSideTexture(texture);
              }
              if (!"left".equals(part)) {
                ((Wall)item).setRightSideTexture(texture);
              }
              changed [0]++;
            } else if (item instanceof HomePieceOfFurniture && ((HomePieceOfFurniture)item).isTexturable()
                && !(item instanceof HomeFurnitureGroup)) {
              ((HomePieceOfFurniture)item).setTexture(texture);
              changed [0]++;
            }
          }
        }
      });
    tally [4] += changed [0];
    if (changed [0] > 0) {
      notes.add("Applied the texture " + describeTextures(Collections.singletonList(catalogTexture))
          + " to " + changed [0] + (changed [0] == 1 ? " item" : " items")
          + describePlacement(placement)
          + (ambiguity != null ? ". " + ambiguity : ""));
    }
  }

  /**
   * Returns the scale, the angle in radians and the x and y offsets of a
   * texture given by the parameters <code>scale</code>, <code>angle</code>
   * (degrees), <code>offset_x</code> and <code>offset_y</code> (fractions of
   * the size of the texture).
   */
  static float [] getTexturePlacement(java.util.Map<?, ?> parameters) {
    float [] placement = {1, 0, 0, 0};
    String [] names = {"scale", "angle", "offset_x", "offset_y"};
    for (int i = 0; i < names.length; i++) {
      Object value = parameters.get(i == 0 && parameters.get("scale") == null ? "texture_scale"
          : i == 1 && parameters.get("angle") == null ? "texture_angle" : names [i]);
      if (value instanceof Number && !Double.isNaN(((Number)value).doubleValue())) {
        placement [i] = ((Number)value).floatValue();
      }
    }
    placement [0] = placement [0] > 0 ? Math.max(0.01f, Math.min(100, placement [0])) : 1;
    placement [1] = (float)Math.toRadians(placement [1] % 360);
    placement [2] = Math.max(-1, Math.min(1, placement [2]));
    placement [3] = Math.max(-1, Math.min(1, placement [3]));
    return placement;
  }

  static HomeTexture createTexture(CatalogTexture catalogTexture, float [] placement) {
    return placement == null || placement [0] == 1 && placement [1] == 0 && placement [2] == 0 && placement [3] == 0
        ? new HomeTexture(catalogTexture)
        : new HomeTexture(catalogTexture, placement [2], placement [3], placement [1], placement [0], true);
  }

  static String describePlacement(float [] placement) {
    StringBuilder text = new StringBuilder();
    if (placement [0] != 1) {
      text.append(", scale ").append(placement [0]);
    }
    if (placement [1] != 0) {
      text.append(", turned by ").append(Math.round(Math.toDegrees(placement [1]))).append(" deg");
    }
    if (placement [2] != 0 || placement [3] != 0) {
      text.append(", offset ").append(placement [2]).append(", ").append(placement [3]);
    }
    return text.toString();
  }

  /**
   * Returns the texture of the catalog with the given name, category or id,
   * or <code>null</code> after adding a note that tells why none was found.
   * @param ambiguityNote receives a remark if several textures have this name
   */
  CatalogTexture findTexture(String name, String categoryName, String catalogId, String action,
                             List<String> notes, String [] ambiguityNote) {
    CatalogTexture chosenTexture = null;
    // The textures of the catalog with exactly this name, which several categories may share
    List<CatalogTexture> sameName = new ArrayList<CatalogTexture>();
    if (this.preferences.getTexturesCatalog() != null) {
      for (TexturesCategory category : this.preferences.getTexturesCatalog().getCategories()) {
        for (CatalogTexture texture : category.getTextures()) {
          if (catalogId != null && catalogId.trim().equals(texture.getId())) {
            chosenTexture = texture;
          }
          if (name != null && name.trim().equalsIgnoreCase(texture.getName())) {
            sameName.add(texture);
          }
        }
      }
    }
    if (catalogId != null && chosenTexture == null) {
      notes.add("Ignored " + action + ": no texture has the catalog id \"" + catalogId + "\"; use search_textures");
      return null;
    }
    if (chosenTexture == null && categoryName != null) {
      List<CatalogTexture> candidates = sameName.isEmpty() ? searchTextures(name, MAX_TEXTURE_RESULTS) : sameName;
      for (CatalogTexture texture : candidates) {
        if (texture.getCategory() != null && categoryName.trim().equalsIgnoreCase(texture.getCategory().getName())) {
          chosenTexture = texture;
          break;
        }
      }
      if (chosenTexture == null) {
        notes.add("Ignored " + action + ": no texture \"" + name + "\" in the category \"" + categoryName
            + "\". Matches: " + describeTextures(candidates));
        return null;
      }
    }
    String ambiguity = null;
    if (chosenTexture == null) {
      if (name == null) {
        return null;
      }
      if (!sameName.isEmpty()) {
        chosenTexture = sameName.get(0);
        if (sameName.size() > 1) {
          ambiguity = sameName.size() + " textures are named \"" + chosenTexture.getName() + "\": "
              + describeTextures(sameName) + "; give \"category\" or \"catalog_id\" to choose an other one";
        }
      } else {
        List<CatalogTexture> matches = searchTextures(name, 3);
        if (matches.isEmpty()) {
          notes.add("Couldn't find a texture named \"" + name + "\"; use search_textures");
          return null;
        }
        chosenTexture = matches.get(0);
      }
    }
    if (ambiguityNote != null) {
      ambiguityNote [0] = ambiguity;
    }
    return chosenTexture;
  }

  // ---- Annotations ----

  private void addLabel(AssistantCommand command, List<String> notes) {
    String text = command.getString("text");
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    if (text == null || text.trim().length() == 0 || Double.isNaN(x) || Double.isNaN(y)
        || !AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)) {
      notes.add("Ignored add_label: text, x and y are required");
      return;
    }
    Label label = new Label(text.trim(), (float)x, (float)y);
    label.setLevel(this.home.getSelectedLevel());
    this.executor.beginTurnUpdate();
    this.homeController.getPlanController().addLabels(Collections.singletonList(label));
    notes.add("Added the label \"" + text.trim() + "\"");
  }

  private void addDimensionLine(AssistantCommand command, List<String> notes) {
    double x1 = command.getDouble("x1", Double.NaN);
    double y1 = command.getDouble("y1", Double.NaN);
    double x2 = command.getDouble("x2", Double.NaN);
    double y2 = command.getDouble("y2", Double.NaN);
    double offset = command.getDouble("offset", 20);
    if (Double.isNaN(x1) || Double.isNaN(y1) || Double.isNaN(x2) || Double.isNaN(y2)
        || !AssistantCommandExecutor.coordinateSane(x1) || !AssistantCommandExecutor.coordinateSane(y1)
        || !AssistantCommandExecutor.coordinateSane(x2) || !AssistantCommandExecutor.coordinateSane(y2)
        || !AssistantCommandExecutor.sizeSane(offset)) {
      notes.add("Ignored add_dimension: x1, y1, x2 and y2 are required");
      return;
    }
    DimensionLine dimensionLine = new DimensionLine((float)x1, (float)y1, (float)x2, (float)y2, (float)offset);
    dimensionLine.setLevel(this.home.getSelectedLevel());
    this.executor.beginTurnUpdate();
    this.homeController.getPlanController().addDimensionLines(Collections.singletonList(dimensionLine));
    notes.add("Added a dimension line of " + Math.round(Math.hypot(x2 - x1, y2 - y1)) + " cm");
  }

  // ---- Furniture ----

  private void duplicate(AssistantCommand command, int [] tally, List<String> notes) {
    double dx = command.getDouble("dx", 50);
    double dy = command.getDouble("dy", 0);
    int count = (int)Math.max(1, Math.min(MAX_COPIES, command.getDouble("count", 1)));
    if (!AssistantCommandExecutor.coordinateSane(dx) || !AssistantCommandExecutor.coordinateSane(dy)) {
      notes.add("Ignored duplicate with out-of-range offsets");
      return;
    }
    List<HomePieceOfFurniture> copies = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture) {
        for (int i = 1; i <= count; i++) {
          HomePieceOfFurniture copy = (HomePieceOfFurniture)((HomePieceOfFurniture)item).duplicate();
          copy.move((float)(dx * i), (float)(dy * i));
          copies.add(copy);
        }
      }
    }
    if (!copies.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getFurnitureController().addFurniture(copies);
      tally [0] += copies.size();
    }
  }

  // ---- Camera ----

  /**
   * Moves the virtual visitor. Cameras aren't part of the undoable history of a home.
   */
  private void setCamera(AssistantCommand command, List<String> notes) {
    String pointOfView = command.getString("point_of_view");
    if (pointOfView != null) {
      // A point of view stored in the home
      for (Camera storedCamera : this.home.getStoredCameras()) {
        if (pointOfView.trim().equalsIgnoreCase(storedCamera.getName())) {
          this.homeController.getHomeController3D().goToCamera(storedCamera);
          notes.add("Went to the point of view \"" + storedCamera.getName() + "\"");
          return;
        }
      }
      notes.add("Ignored set_camera: no stored point of view named \"" + pointOfView + "\"");
      return;
    }
    String view = command.getString("view");
    if (view != null) {
      // A standard view of the home or of some items, or a view from the inside of a room
      try {
        double fieldOfView = command.getDouble("fov", 0);
        Level displayedLevel = this.home.getEnvironment().isAllLevelsVisible()
            ? null : this.home.getSelectedLevel();
        HomeViewpoints.Pose pose = HomeViewpoints.getPose(this.home, view, this.executor.resolveTargets(command),
            displayedLevel, 1.5f, fieldOfView >= 10 && fieldOfView <= 150 ? (float)Math.toRadians(fieldOfView) : 0);
        pose.applyTo(this.home);
        notes.add("Camera: " + pose.describe() + " (camera moves aren't undoable)");
      } catch (IllegalArgumentException ex) {
        notes.add("Ignored set_camera: " + ex.getMessage());
      }
      return;
    }
    Camera camera = this.home.getObserverCamera();
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    double height = command.getDouble("height", Double.NaN);
    if (!AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)
        || !AssistantCommandExecutor.sizeSane(height)) {
      notes.add("Ignored set_camera with out-of-range values");
      return;
    }
    if (!Double.isNaN(x)) {
      camera.setX((float)x);
    }
    if (!Double.isNaN(y)) {
      camera.setY((float)y);
    }
    if (height > 0) {
      Level level = this.home.getSelectedLevel();
      camera.setZ((float)height + (level != null ? level.getElevation() : 0));
    }
    double lookAtX = command.getDouble("look_at_x", Double.NaN);
    double lookAtY = command.getDouble("look_at_y", Double.NaN);
    if (!Double.isNaN(lookAtX) && !Double.isNaN(lookAtY)
        && (Math.abs(lookAtX - camera.getX()) > 0.01 || Math.abs(lookAtY - camera.getY()) > 0.01)) {
      // With a yaw of 0 the visitor looks toward +y, and turns clockwise in the plan
      camera.setYaw((float)Math.atan2(-(lookAtX - camera.getX()), lookAtY - camera.getY()));
    } else if (command.has("yaw")) {
      camera.setYaw((float)Math.toRadians(command.getDouble("yaw", 0)));
    }
    if (command.has("pitch")) {
      camera.setPitch((float)Math.toRadians(Math.max(-90, Math.min(90, command.getDouble("pitch", 0)))));
    }
    String mode = command.getString("mode");
    if ("visitor".equalsIgnoreCase(mode)) {
      this.home.setCamera(this.home.getObserverCamera());
    } else if ("aerial".equalsIgnoreCase(mode) || "top".equalsIgnoreCase(mode)) {
      this.home.setCamera(this.home.getTopCamera());
    }
    notes.add("Moved the virtual visitor to (" + Math.round(camera.getX()) + ", " + Math.round(camera.getY())
        + ") cm (camera moves aren't undoable)");
  }

  // ---- Repair ----

  /**
   * Fixes the issues found in the home that can be fixed without a decision.
   */
  private void fixHome(AssistantCommand command, int [] tally, List<String> notes) {
    List<String> types = new ArrayList<String>();
    for (String type : command.getStringList("types")) {
      types.add(type.trim().toLowerCase(Locale.ENGLISH));
    }
    final List<Issue> issues = new ArrayList<Issue>();
    for (Issue issue : check()) {
      if (issue.isFixable()
          && (types.isEmpty() || types.contains(issue.getType().name().toLowerCase(Locale.ENGLISH)))) {
        issues.add(issue);
      }
    }
    if (issues.isEmpty()) {
      notes.add("Nothing to fix");
      return;
    }
    // Items changed in place, then items removed
    final List<Selectable> modifiedItems = new ArrayList<Selectable>();
    final List<Selectable> deletedItems = new ArrayList<Selectable>();
    // The walls to fit again to each roof they pass
    final java.util.Map<Selectable, List<Wall>> roofWalls = new java.util.LinkedHashMap<Selectable, List<Wall>>();
    for (Issue issue : issues) {
      switch (issue.getType()) {
        case OVERLAPPING_WALL :
          // The wall kept may be the one to delete of another pair
          if (!deletedItems.contains(issue.getItem()) && !deletedItems.contains(issue.getOtherItem())) {
            deletedItems.add(issue.getItem());
            addOnce(modifiedItems, issue.getOtherItem());
            for (Wall wall : this.home.getWalls()) {
              if (wall.getWallAtStart() == issue.getItem() || wall.getWallAtEnd() == issue.getItem()
                  || wall.getWallAtStart() == issue.getOtherItem() || wall.getWallAtEnd() == issue.getOtherItem()) {
                addOnce(modifiedItems, wall);
              }
            }
          }
          break;
        case WALL_ABOVE_ROOF :
          if (roofWalls.get(issue.getOtherItem()) == null) {
            roofWalls.put(issue.getOtherItem(), new ArrayList<Wall>());
          }
          roofWalls.get(issue.getOtherItem()).add((Wall)issue.getItem());
          break;
        case TINY_WALL :
        case DUPLICATE_WALL :
        case EMPTY_ROOM :
        case DUPLICATE_FURNITURE :
          if (!deletedItems.contains(issue.getItem())) {
            deletedItems.add(issue.getItem());
          }
          break;
        case WALL_GAP :
          addOnce(modifiedItems, issue.getItem());
          addOnce(modifiedItems, issue.getOtherItem());
          break;
        case DOOR_OFF_WALL :
          if (issue.getOtherItem() != null) {
            addOnce(modifiedItems, issue.getItem());
          }
          break;
        default :
          addOnce(modifiedItems, issue.getItem());
          break;
      }
    }
    modifiedItems.removeAll(deletedItems);
    final int [] fixed = {0};
    if (!modifiedItems.isEmpty()) {
      this.executor.postModification(modifiedItems, new Runnable() {
          public void run() {
            for (Issue issue : issues) {
              if (deletedItems.contains(issue.getItem())
                  || issue.getOtherItem() != null && deletedItems.contains(issue.getOtherItem())) {
                continue;
              }
              if (issue.getType() == HomeDiagnostics.Type.WALL_GAP) {
                if (joinWalls((Wall)issue.getItem(), (Wall)issue.getOtherItem())) {
                  fixed [0]++;
                }
              } else if (issue.getType() == HomeDiagnostics.Type.DOOR_OFF_WALL && issue.getOtherItem() != null) {
                putInWall((HomePieceOfFurniture)issue.getItem(), (Wall)issue.getOtherItem());
                fixed [0]++;
              } else if (issue.getType() == HomeDiagnostics.Type.FURNITURE_BELOW_FLOOR) {
                ((HomePieceOfFurniture)issue.getItem()).setElevation(0);
                fixed [0]++;
              } else if (issue.getType() == HomeDiagnostics.Type.DOOR_MISALIGNED) {
                alignWithWall((HomePieceOfFurniture)issue.getItem(), (Wall)issue.getOtherItem());
                fixed [0]++;
              } else if (issue.getType() == HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM) {
                Wall wall = (Wall)issue.getItem();
                // Up to the ceiling of the tallest room it closes
                if (issue.getValue() > HomeDefects.getWallHeight(home, wall, 0)) {
                  wall.setHeight(Float.valueOf(issue.getValue()));
                }
                if (wall.getHeightAtEnd() != null && issue.getValue() > wall.getHeightAtEnd().floatValue()) {
                  wall.setHeightAtEnd(Float.valueOf(issue.getValue()));
                }
                fixed [0]++;
              }
            }
            // The wall kept of two walls drawn over each other takes the place of both
            for (Issue issue : issues) {
              if (issue.getType() == HomeDiagnostics.Type.OVERLAPPING_WALL
                  && deletedItems.contains(issue.getItem()) && modifiedItems.contains(issue.getOtherItem())
                  && !deletedItems.contains(issue.getOtherItem())) {
                replaceWall((Wall)issue.getItem(), (Wall)issue.getOtherItem());
              }
            }
          }
        });
      tally [4] += modifiedItems.size();
    }
    if (!deletedItems.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().deleteItems(deletedItems);
      tally [5] += deletedItems.size();
      fixed [0] += deletedItems.size();
    }
    // The walls that pass a roof get the height of its underside again
    for (java.util.Map.Entry<Selectable, List<Wall>> entry : roofWalls.entrySet()) {
      List<Object> ids = new ArrayList<Object>();
      for (Wall wall : entry.getValue()) {
        if (!deletedItems.contains(wall) && this.home.getWalls().contains(wall)) {
          ids.add(this.executor.getId(wall));
        }
      }
      if (!ids.isEmpty()) {
        java.util.Map<String, Object> parameters = new java.util.LinkedHashMap<String, Object>();
        parameters.put("action", "fit_walls_to_roof");
        parameters.put("roof", this.executor.getId(entry.getKey()));
        parameters.put("ids", ids);
        List<String> fitNotes = new ArrayList<String>();
        int [] fitTally = new int [tally.length];
        this.executor.applySubCommand(new AssistantCommand("fit_walls_to_roof", parameters), fitTally, fitNotes);
        int changes = 0;
        for (int i = 0; i < tally.length; i++) {
          tally [i] += fitTally [i];
          changes += fitTally [i];
        }
        if (changes > 0) {
          fixed [0] += ids.size();
        }
        notes.addAll(fitNotes);
      }
    }
    notes.add("Fixed " + fixed [0] + (fixed [0] == 1 ? " issue" : " issues"));
  }

  /**
   * Turns a door or a window parallel to its wall, facing the side it faced the most, and brings it
   * back on the middle line of the wall if it comes out of one of its faces.
   */
  private static void alignWithWall(HomePieceOfFurniture piece, Wall wall) {
    double wallAngle = Math.atan2(wall.getYEnd() - wall.getYStart(), wall.getXEnd() - wall.getXStart());
    double difference = Math.abs(Math.IEEEremainder(piece.getAngle() - wallAngle, 2 * Math.PI));
    piece.setAngle((float)(difference <= Math.PI / 2 ? wallAngle : wallAngle + Math.PI));
    double ux = Math.cos(wallAngle);
    double uy = Math.sin(wallAngle);
    double offset = (piece.getX() - wall.getXStart()) * -uy + (piece.getY() - wall.getYStart()) * ux;
    if (HomeDefects.isOffMiddleLine(piece, wall, offset)) {
      piece.setX((float)(piece.getX() + uy * offset));
      piece.setY((float)(piece.getY() - ux * offset));
    }
  }

  /**
   * Makes <code>kept</code> stand for <code>deleted</code> too, a wall drawn over it: it's put
   * on the line of the deleted wall if that one follows the axes of the plan better, it's made as long
   * as both, and it joins the walls the deleted one was joined to.
   */
  private void replaceWall(Wall deleted, Wall kept) {
    if (getAxisDeviation(deleted) < 0.05 && getAxisDeviation(kept) >= 0.05 && getAxisDeviation(kept) < 3) {
      // The wall kept is a little crooked: on the line of the straight one
      float [] start = getPointOnLine(deleted, kept.getXStart(), kept.getYStart());
      float [] end = getPointOnLine(deleted, kept.getXEnd(), kept.getYEnd());
      moveWallEnd(kept, true, start [0], start [1]);
      moveWallEnd(kept, false, end [0], end [1]);
    }
    double length = kept.getLength();
    double ux = (kept.getXEnd() - kept.getXStart()) / length;
    double uy = (kept.getYEnd() - kept.getYStart()) / length;
    double s1 = (deleted.getXStart() - kept.getXStart()) * ux + (deleted.getYStart() - kept.getYStart()) * uy;
    double s2 = (deleted.getXEnd() - kept.getXStart()) * ux + (deleted.getYEnd() - kept.getYStart()) * uy;
    double from = Math.min(s1, s2);
    double to = Math.max(s1, s2);
    float xStart = kept.getXStart();
    float yStart = kept.getYStart();
    if (from < -1 && kept.getWallAtStart() == null) {
      kept.setXStart((float)(xStart + ux * from));
      kept.setYStart((float)(yStart + uy * from));
    }
    if (to > length + 1 && kept.getWallAtEnd() == null) {
      kept.setXEnd((float)(xStart + ux * to));
      kept.setYEnd((float)(yStart + uy * to));
    }
    // The walls joined to the deleted one are joined to the one kept where they meet it
    for (Wall wall : this.home.getWalls()) {
      if (wall == deleted || wall == kept) {
        continue;
      }
      for (int end = 0; end < 2; end++) {
        if ((end == 0 ? wall.getWallAtStart() : wall.getWallAtEnd()) != deleted) {
          continue;
        }
        float x = end == 0 ? wall.getXStart() : wall.getXEnd();
        float y = end == 0 ? wall.getYStart() : wall.getYEnd();
        if (end == 0) {
          wall.setWallAtStart(null);
        } else {
          wall.setWallAtEnd(null);
        }
        for (int keptEnd = 0; keptEnd < 2; keptEnd++) {
          if ((keptEnd == 0 ? kept.getWallAtStart() : kept.getWallAtEnd()) == null
              && Math.hypot(x - (keptEnd == 0 ? kept.getXStart() : kept.getXEnd()),
                    y - (keptEnd == 0 ? kept.getYStart() : kept.getYEnd())) <= HomeDiagnostics.WALL_GAP_TOLERANCE) {
            if (end == 0) {
              wall.setXStart(keptEnd == 0 ? kept.getXStart() : kept.getXEnd());
              wall.setYStart(keptEnd == 0 ? kept.getYStart() : kept.getYEnd());
              wall.setWallAtStart(kept);
            } else {
              wall.setXEnd(keptEnd == 0 ? kept.getXStart() : kept.getXEnd());
              wall.setYEnd(keptEnd == 0 ? kept.getYStart() : kept.getYEnd());
              wall.setWallAtEnd(kept);
            }
            if (keptEnd == 0) {
              kept.setWallAtStart(wall);
            } else {
              kept.setWallAtEnd(wall);
            }
            break;
          }
        }
      }
    }
  }

  /**
   * Returns the angle in degrees between a wall and the closest axis of the plan.
   */
  private static double getAxisDeviation(Wall wall) {
    double angle = Math.toDegrees(Math.atan2(wall.getYEnd() - wall.getYStart(), wall.getXEnd() - wall.getXStart()));
    return Math.abs(Math.IEEEremainder(angle, 90));
  }

  private static float [] getPointOnLine(Wall wall, float x, float y) {
    double length = wall.getLength();
    double ux = (wall.getXEnd() - wall.getXStart()) / length;
    double uy = (wall.getYEnd() - wall.getYStart()) / length;
    double s = (x - wall.getXStart()) * ux + (y - wall.getYStart()) * uy;
    return new float [] {(float)(wall.getXStart() + ux * s), (float)(wall.getYStart() + uy * s)};
  }

  /**
   * Moves an end of a wall with the end of the wall joined to it there.
   */
  private static void moveWallEnd(Wall wall, boolean start, float x, float y) {
    Wall joined = start ? wall.getWallAtStart() : wall.getWallAtEnd();
    float oldX = start ? wall.getXStart() : wall.getXEnd();
    float oldY = start ? wall.getYStart() : wall.getYEnd();
    if (joined != null) {
      if (joined.getWallAtStart() == wall && Math.hypot(joined.getXStart() - oldX, joined.getYStart() - oldY) < 0.5) {
        joined.setXStart(x);
        joined.setYStart(y);
      } else if (joined.getWallAtEnd() == wall && Math.hypot(joined.getXEnd() - oldX, joined.getYEnd() - oldY) < 0.5) {
        joined.setXEnd(x);
        joined.setYEnd(y);
      }
    }
    if (start) {
      wall.setXStart(x);
      wall.setYStart(y);
    } else {
      wall.setXEnd(x);
      wall.setYEnd(y);
    }
  }

  private static void addOnce(List<Selectable> items, Selectable item) {
    if (item != null && !items.contains(item)) {
      items.add(item);
    }
  }

  /**
   * Moves the closest free end of <code>wall</code> to the closest free end of
   * <code>other</code> and joins both walls there.
   */
  private static boolean joinWalls(Wall wall, Wall other) {
    int bestEnd = -1;
    int bestOtherEnd = -1;
    float bestDistance = HomeDiagnostics.WALL_GAP_TOLERANCE + 0.01f;
    for (int end = 0; end < 2; end++) {
      if ((end == 0 ? wall.getWallAtStart() : wall.getWallAtEnd()) != null) {
        continue;
      }
      for (int otherEnd = 0; otherEnd < 2; otherEnd++) {
        if ((otherEnd == 0 ? other.getWallAtStart() : other.getWallAtEnd()) != null) {
          continue;
        }
        float distance = (float)Math.hypot(
            (end == 0 ? wall.getXStart() : wall.getXEnd()) - (otherEnd == 0 ? other.getXStart() : other.getXEnd()),
            (end == 0 ? wall.getYStart() : wall.getYEnd()) - (otherEnd == 0 ? other.getYStart() : other.getYEnd()));
        if (distance < bestDistance) {
          bestDistance = distance;
          bestEnd = end;
          bestOtherEnd = otherEnd;
        }
      }
    }
    if (bestEnd < 0) {
      return false;
    }
    float x = bestOtherEnd == 0 ? other.getXStart() : other.getXEnd();
    float y = bestOtherEnd == 0 ? other.getYStart() : other.getYEnd();
    if (bestEnd == 0) {
      wall.setXStart(x);
      wall.setYStart(y);
      wall.setWallAtStart(other);
    } else {
      wall.setXEnd(x);
      wall.setYEnd(y);
      wall.setWallAtEnd(other);
    }
    if (bestOtherEnd == 0) {
      other.setWallAtStart(wall);
    } else {
      other.setWallAtEnd(wall);
    }
    return true;
  }

  /**
   * Moves a door or a window to the closest point of the middle line of
   * <code>wall</code> and turns it parallel to the wall, facing the side it
   * faced the most.
   */
  private static void putInWall(HomePieceOfFurniture piece, Wall wall) {
    float [] point = HomeDiagnostics.getClosestPointOnWall(piece.getX(), piece.getY(), wall);
    piece.setX(point [0]);
    piece.setY(point [1]);
    double wallAngle = Math.atan2(wall.getYEnd() - wall.getYStart(), wall.getXEnd() - wall.getXStart());
    double difference = Math.abs(Math.IEEEremainder(piece.getAngle() - wallAngle, 2 * Math.PI));
    piece.setAngle((float)(difference <= Math.PI / 2 ? wallAngle : wallAngle + Math.PI));
  }
}
