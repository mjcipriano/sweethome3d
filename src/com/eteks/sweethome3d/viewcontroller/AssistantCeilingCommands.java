/*
 * AssistantCeilingCommands.java 25 nov. 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.viewcontroller;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands about the ceilings of rooms: a ceiling at its own height
 * (<code>modify_room</code> with <code>ceiling_height</code>), and a room open to the level
 * above it with its overlook (<code>open_to_above</code>).
 * <br>The height of a ceiling is kept in a property of its room, read by the 3D view:
 * a version of the application which doesn't know it draws the ceiling at the top of the level.
 * @author Sweet Home 3D contributors
 */
class AssistantCeilingCommands {
  /** The height in cm of the ceiling of a room above its floor, when it isn't at the top of its level. */
  static final String CEILING_HEIGHT_PROPERTY = "com.eteks.sweethome3d.ceilingHeight";
  /** The thickness in cm of a ceiling placed at its own height. */
  static final String CEILING_THICKNESS_PROPERTY = "com.eteks.sweethome3d.ceilingThickness";

  private static final float MIN_CEILING_HEIGHT = 50;
  private static final float MAX_CEILING_HEIGHT = 3000;
  private static final float DEFAULT_RAILING_HEIGHT = 91;
  private static final List<String> OPEN_TO_ABOVE_KEYS = Arrays.asList("action", "room", "id", "to_level", "level",
      "overlook", "ceiling", "ceiling_thickness");
  private static final List<String> OVERLOOK_KEYS = Arrays.asList("along", "wall", "railing", "height", "upper_wall");

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantCeilingCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    if ("open_to_above".equals(action) || "open_room_to_above".equals(action)) {
      openToAbove(command, tally, notes);
      return true;
    }
    return false;
  }

  /**
   * Returns the height above its floor of the ceiling a room got with <code>ceiling_height</code>,
   * or NaN if its ceiling is at the top of its level.
   */
  static double getCeilingHeight(Room room) {
    String height = room.getProperty(CEILING_HEIGHT_PROPERTY);
    if (height != null) {
      try {
        double value = Double.parseDouble(height);
        if (value > 0 && value <= 10000) {
          return value;
        }
      } catch (NumberFormatException ex) {
        // The ceiling of the level
      }
    }
    return Double.NaN;
  }

  /**
   * Returns <code>true</code> if <code>command</code> places the ceiling of rooms at a height.
   */
  static boolean hasCeilingHeight(AssistantCommand command) {
    return command.has("ceiling_height") || command.has("ceiling_level") || command.has("ceiling_thickness");
  }

  /**
   * Places the ceiling of a room as the keys <code>ceiling_height</code>, <code>ceiling_level</code>
   * and <code>ceiling_thickness</code> of <code>command</code> tell.
   * @return a description of the ceiling
   * @throws IOException if the values can't be used
   */
  String setCeilingHeight(Room room, AssistantCommand command) throws IOException {
    Level roomLevel = room.getLevel();
    float floor = roomLevel != null ? roomLevel.getElevation() : 0;
    String heightText = command.getString("ceiling_height");
    boolean remove = heightText != null
        && heightText.trim().toLowerCase(Locale.ENGLISH).matches("level|default|none|0|0\\.0*");
    double height = command.getDouble("ceiling_height", Double.NaN);
    Level ceilingLevel = null;
    if (command.has("ceiling_level") && !remove) {
      ceilingLevel = findLevel(command.getString("ceiling_level"));
      if (ceilingLevel == null) {
        throw new IOException("no level, or several, are named \"" + command.getString("ceiling_level") + "\"");
      } else if (ceilingLevel.getElevation() < floor - 0.5f) {
        throw new IOException("the level \"" + ceilingLevel.getName() + "\" is under the room");
      }
      if (Double.isNaN(height)) {
        // The ceiling of that level
        height = ceilingLevel.getHeight();
      }
      height += ceilingLevel.getElevation() - floor;
    }
    String thicknessValue = room.getProperty(CEILING_THICKNESS_PROPERTY);
    if (command.has("ceiling_thickness")) {
      double thickness = command.getDouble("ceiling_thickness", Double.NaN);
      if (!(thickness >= 0 && thickness <= 100)) {
        throw new IOException("\"ceiling_thickness\" is from 0 to 100 cm");
      }
      thicknessValue = format(thickness);
    }
    String heightValue = room.getProperty(CEILING_HEIGHT_PROPERTY);
    if (remove) {
      heightValue = null;
      thicknessValue = null;
    } else if (command.has("ceiling_height") || ceilingLevel != null) {
      if (!(height >= MIN_CEILING_HEIGHT && height <= MAX_CEILING_HEIGHT)) {
        throw new IOException("\"ceiling_height\" is the height of the ceiling above the floor of the room, from "
            + Math.round(MIN_CEILING_HEIGHT) + " to " + Math.round(MAX_CEILING_HEIGHT) + " cm, or \"level\" to put the "
            + "ceiling back at the top of the level"
            + (Double.isNaN(height) ? "" : " (" + format(height) + " was asked)"));
      }
      heightValue = format(height);
    } else if (heightValue == null) {
      throw new IOException("\"ceiling_thickness\" needs \"ceiling_height\": the ceiling at the top of a level has no thickness");
    }
    setProperties(room, new String [] {CEILING_HEIGHT_PROPERTY, CEILING_THICKNESS_PROPERTY},
        new String [] {heightValue, thicknessValue});
    if (remove) {
      return "its ceiling is back at the top of its level";
    }
    return describeCeiling(room) + describeWhatIsInside(room, floor + (float)height);
  }

  /**
   * Returns a sentence about the ceiling of a room placed at its own height, or <code>null</code>.
   */
  String describeCeiling(Room room) {
    double height = getCeilingHeight(room);
    if (Double.isNaN(height)) {
      return null;
    }
    Level roomLevel = room.getLevel();
    float floor = roomLevel != null ? roomLevel.getElevation() : 0;
    StringBuilder text = new StringBuilder("its ceiling is " + format(height) + " cm above its floor");
    if (roomLevel != null) {
      double difference = height - roomLevel.getHeight();
      if (difference < -0.5) {
        text.append(", ").append(format(-difference)).append(" cm under the top of its level");
      } else if (difference > 0.5) {
        // The level whose top it reaches
        Level reached = null;
        for (Level level : this.home.getLevels()) {
          if (Math.abs(level.getElevation() + level.getHeight() - floor - height) < 0.5 && level != roomLevel) {
            reached = level;
          }
        }
        text.append(reached != null
            ? ", at the top of the level \"" + reached.getName() + "\""
            : ", " + format(difference) + " cm above the top of its level");
      }
    }
    String thickness = room.getProperty(CEILING_THICKNESS_PROPERTY);
    text.append(" (drawn from both sides, ").append("0".equals(thickness) ? "without thickness"
        : (thickness != null ? thickness : "5") + " cm thick").append(")");
    if (!room.isCeilingVisible()) {
      text.append(", but hidden: \"ceiling_visible\":true shows it");
    }
    return text.toString();
  }

  /**
   * Returns a note about the floors of the rooms above <code>room</code> that a ceiling at the given
   * elevation leaves inside the room.
   */
  private String describeWhatIsInside(Room room, float ceilingElevation) {
    Level roomLevel = room.getLevel();
    if (roomLevel == null) {
      return "";
    }
    Area roomArea = getArea(room.getPoints());
    List<String> floors = new ArrayList<String>();
    for (Room other : this.home.getRooms()) {
      Level level = other.getLevel();
      if (other != room && level != null && other.isFloorVisible()
          && level.getElevation() > roomLevel.getElevation() + 1 && level.getElevation() < ceilingElevation - 1
          && getSurface(intersect(roomArea, getArea(other.getPoints()))) > 2500) {
        floors.add(this.executor.getId(other));
      }
    }
    return floors.isEmpty() ? ""
        : "; the floor of " + floors + " on the level above is inside the room, under that ceiling: open_to_above "
            + "opens a room to the level above with its walls and its overlook";
  }

  private Level findLevel(String token) {
    if (token == null) {
      return null;
    }
    token = token.trim();
    Level found = null;
    int count = 0;
    for (Level level : this.home.getLevels()) {
      if (token.equalsIgnoreCase(this.executor.getId(level))) {
        return level;
      } else if (token.equalsIgnoreCase(level.getName())) {
        found = level;
        count++;
      }
    }
    return count == 1 ? found : null;
  }

  /**
   * Sets properties of an item in an undoable way.
   */
  private void setProperties(final Room room, final String [] names, final String [] values) {
    final String [] oldValues = new String [names.length];
    boolean changed = false;
    for (int i = 0; i < names.length; i++) {
      oldValues [i] = room.getProperty(names [i]);
      changed |= oldValues [i] == null ? values [i] != null : !oldValues [i].equals(values [i]);
    }
    if (!changed) {
      return;
    }
    for (int i = 0; i < names.length; i++) {
      room.setProperty(names [i], values [i]);
    }
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() {
          super.undo();
          for (int i = 0; i < names.length; i++) {
            room.setProperty(names [i], oldValues [i]);
          }
        }

        @Override
        public void redo() {
          super.redo();
          for (int i = 0; i < names.length; i++) {
            room.setProperty(names [i], values [i]);
          }
        }

        @Override
        public String getPresentationName() {
          return "AI assistant edit";
        }
      });
  }

  // ---- Level heights ----

  /**
   * Returns the walls of <code>level</code> which are as tall as it, the ones that follow
   * its height when it changes without <code>keep_wall_heights</code>.
   */
  List<Wall> getWallsAsTallAsLevel(Level level, float levelHeight) {
    List<Wall> walls = new ArrayList<Wall>();
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == level && wall.getHeight() != null
          && Math.abs(wall.getHeight() - levelHeight) < 0.5f
          && (wall.getHeightAtEnd() == null || Math.abs(wall.getHeightAtEnd() - levelHeight) < 0.5f)) {
        walls.add(wall);
      }
    }
    return walls;
  }

  /**
   * Returns the sentence of the answer of <code>modify_level</code> about the walls and the ceilings
   * of a level whose height changed from <code>oldHeight</code>.
   * @param movedWalls the walls which got the new height
   */
  String describeLevelHeightChange(Level level, float oldHeight, List<Wall> movedWalls, boolean keepWallHeights) {
    StringBuilder text = new StringBuilder();
    int wallCount = 0;
    float lowest = Float.MAX_VALUE;
    float highest = 0;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == level) {
        wallCount++;
        float height = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
        float heightAtEnd = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : height;
        lowest = Math.min(lowest, Math.min(height, heightAtEnd));
        highest = Math.max(highest, Math.max(height, heightAtEnd));
      }
    }
    if (!movedWalls.isEmpty()) {
      List<String> ids = new ArrayList<String>();
      for (Wall wall : movedWalls) {
        ids.add(this.executor.getId(wall));
      }
      text.append(". ").append(movedWalls.size()).append(movedWalls.size() == 1 ? " wall" : " walls")
          .append(" as tall as the level went from ").append(format(oldHeight)).append(" to ")
          .append(format(level.getHeight())).append(" cm: ").append(ids.size() > 40 ? ids.subList(0, 40) + "..." : ids)
          .append(" (\"keep_wall_heights\":true changes the level and leaves every wall as it is)");
    } else if (wallCount > 0) {
      text.append(". Its ").append(wallCount).append(wallCount == 1 ? " wall keeps its height" : " walls keep their heights")
          .append(" (").append(Math.abs(highest - lowest) < 0.5f ? format(lowest) : "from " + format(lowest) + " to " + format(highest))
          .append(" cm)").append(keepWallHeights ? "" : ": none was as tall as the level");
    }
    // The ceilings at the top of the level follow it
    int followingCeilings = 0;
    int ownCeilings = 0;
    for (Room room : this.home.getRooms()) {
      if (room.getLevel() == level && room.isCeilingVisible()) {
        if (Double.isNaN(getCeilingHeight(room))) {
          followingCeilings++;
        } else {
          ownCeilings++;
        }
      }
    }
    List<Level> levels = this.home.getLevels();
    if (followingCeilings > 0 && levels.indexOf(level) == levels.size() - 1) {
      // The ceilings of the last level are drawn at the top of its walls
      text.append(". The ").append(followingCeilings == 1 ? "ceiling of 1 room stays" : "ceilings of " + followingCeilings + " rooms stay")
          .append(" at the top of its tallest wall").append(wallCount > 0 ? " (" + format(highest) + " cm)" : "")
          .append(", as on every last level: modify_room with \"ceiling_height\" puts a ceiling lower than the walls");
    } else if (followingCeilings > 0) {
      text.append(". The ").append(followingCeilings == 1 ? "ceiling of 1 room is" : "ceilings of " + followingCeilings + " rooms are")
          .append(" now at ").append(format(level.getHeight())).append(" cm");
    }
    if (ownCeilings > 0) {
      text.append(ownCeilings == 1 ? "; 1 room keeps" : "; " + ownCeilings + " rooms keep").append(" the ceiling height set with ceiling_height");
    }
    return text.toString();
  }

  // ---- Rooms open to the level above ----

  private void openToAbove(AssistantCommand command, int [] tally, List<String> notes) {
    this.executor.warnUnknownKeys(command, OPEN_TO_ABOVE_KEYS);
    String token = command.getString("room") != null ? command.getString("room") : command.getString("id");
    Room room = findRoom(token);
    if (room == null) {
      notes.add("Ignored open_to_above: " + (token == null ? "give the room with \"room\": its id R# or its name"
          : "no room, or several, have the id or the name \"" + token + "\""));
      return;
    }
    Level roomLevel = room.getLevel();
    if (roomLevel == null) {
      notes.add("Ignored open_to_above: the home has no levels, add one above with add_level first");
      return;
    }
    // The level whose ceiling becomes the one of the room: the next one above by default
    Level toLevel;
    String levelToken = command.getString("to_level") != null ? command.getString("to_level") : command.getString("level");
    if (levelToken != null) {
      toLevel = findLevel(levelToken);
      if (toLevel == null) {
        notes.add("Ignored open_to_above: no level, or several, are named \"" + levelToken + "\"");
        return;
      }
    } else {
      // The next level with rooms: a level made for a roof or for helpers isn't a floor of the building
      toLevel = getLevelAbove(roomLevel, true);
      if (toLevel == null) {
        toLevel = getLevelAbove(roomLevel, false);
      }
    }
    if (toLevel == null || toLevel.getElevation() <= roomLevel.getElevation() + 1) {
      notes.add("Ignored open_to_above: \"to_level\" must be a level above the one of the room (\""
          + roomLevel.getName() + "\")");
      return;
    }
    Map<?, ?> overlook = command.getParameters().get("overlook") instanceof Map
        ? (Map<?, ?>)command.getParameters().get("overlook") : null;
    if (command.has("overlook") && overlook == null) {
      notes.add("Ignored open_to_above: \"overlook\" is an object such as {\"along\":\"north\",\"railing\":\"<catalog name>\",\"height\":91}");
      return;
    }
    if (overlook != null) {
      List<String> unknown = new ArrayList<String>();
      for (Object key : overlook.keySet()) {
        if (!OVERLOOK_KEYS.contains(key)) {
          unknown.add(String.valueOf(key));
        }
      }
      if (!unknown.isEmpty()) {
        this.executor.warn("Warning: open_to_above ignored " + (unknown.size() == 1 ? "the key " : "the keys ") + unknown
            + " of \"overlook\". Its keys: " + OVERLOOK_KEYS);
      }
    }
    String ceilingMode = command.getString("ceiling") != null
        ? command.getString("ceiling").trim().toLowerCase(Locale.ENGLISH).replace('-', '_') : "level";
    boolean followRoof = ceilingMode.equals("follow_roof") || ceilingMode.equals("roof");
    if (!followRoof && !ceilingMode.equals("level") && !ceilingMode.equals("flat") && !ceilingMode.equals("none")) {
      notes.add("Ignored open_to_above: \"ceiling\" is level (the ceiling of the upper level, by default), follow_roof or none");
      return;
    }
    float [][] outline = room.getPoints();
    final float floor = roomLevel.getElevation();
    Area roomArea = getArea(outline);
    // The ceiling of the rooms of the upper level, when they have one of their own, rather than the top of that level
    double upperCeiling = followRoof ? Double.NaN : getCeilingHeightOfLevel(toLevel, room, roomArea);
    final float top = toLevel.getElevation() + (Double.isNaN(upperCeiling) ? toLevel.getHeight() : (float)upperCeiling);
    StringBuilder answer = new StringBuilder("Opened " + this.executor.getId(room)
        + (room.getName() != null ? " \"" + room.getName() + "\"" : "") + " to the level \"" + toLevel.getName() + "\"");

    // 1. The ceiling
    try {
      if (followRoof) {
        final Room ceilingRoom = room;
        this.executor.postModification(Collections.<Selectable>singletonList(room), new Runnable() {
            public void run() {
              ceilingRoom.setCeilingVisible(false);
            }
          });
        setProperties(room, new String [] {CEILING_HEIGHT_PROPERTY, CEILING_THICKNESS_PROPERTY}, new String [] {null, null});
        answer.append(": ").append(this.executor.getShapeCommands().setRoomCeiling(room, "follow_roof", Double.NaN));
      } else {
        final boolean visible = !ceilingMode.equals("none");
        final Room ceilingRoom = room;
        this.executor.postModification(Collections.<Selectable>singletonList(room), new Runnable() {
            public void run() {
              ceilingRoom.setCeilingVisible(visible);
            }
          });
        String thickness = room.getProperty(CEILING_THICKNESS_PROPERTY);
        if (thickness == null) {
          // At the top of the walls, a ceiling with a thickness would rise above them and into the roof they bear
          thickness = "0";
        }
        if (command.has("ceiling_thickness")) {
          double value = command.getDouble("ceiling_thickness", Double.NaN);
          if (value >= 0 && value <= 100) {
            thickness = format(value);
          } else {
            notes.add("open_to_above: \"ceiling_thickness\" is from 0 to 100 cm, the ceiling keeps its thickness");
          }
        }
        setProperties(room, new String [] {CEILING_HEIGHT_PROPERTY, CEILING_THICKNESS_PROPERTY},
            new String [] {format(top - floor), thickness});
        answer.append(": ").append(visible ? describeCeiling(room) : "its ceiling is hidden");
        if (!Double.isNaN(upperCeiling)) {
          answer.append(", as the ceilings of the rooms of the level \"").append(toLevel.getName()).append("\", ")
              .append(format(upperCeiling)).append(" cm above its floor");
        }
      }
    } catch (IOException ex) {
      answer.append(": its ceiling doesn't follow a roof (").append(ex.getMessage())
          .append("), give \"ceiling\":\"level\" or build the roof first");
    }
    tally [4]++;

    // 2. The floors and the ceilings of the rooms of the levels above, in the opened space
    List<String> hiddenFloors = new ArrayList<String>();
    List<String> reshapedRooms = new ArrayList<String>();
    List<String> floorsLeft = new ArrayList<String>();
    for (final Room other : new ArrayList<Room>(this.home.getRooms())) {
      Level level = other.getLevel();
      if (other == room || level == null || level.getElevation() <= floor + 1 || level.getElevation() >= top - 1) {
        continue;
      }
      Area otherArea = getArea(other.getPoints());
      double otherSurface = getSurface(otherArea);
      double common = getSurface(intersect(roomArea, otherArea));
      if (common < 100 || otherSurface <= 0) {
        continue;
      }
      String id = this.executor.getId(other) + (other.getName() != null ? " \"" + other.getName() + "\"" : "");
      if (common >= otherSurface * 0.95) {
        // A room of the upper level drawn over the opened one
        if (other.isFloorVisible() || other.isCeilingVisible()) {
          this.executor.postModification(Collections.<Selectable>singletonList(other), new Runnable() {
              public void run() {
                other.setFloorVisible(false);
                other.setCeilingVisible(false);
              }
            });
          hiddenFloors.add(id);
          tally [4]++;
        }
      } else {
        // A room which goes over the opened one: its part above it is removed
        Area rest = new Area(otherArea);
        rest.subtract(roomArea);
        final List<float [][]> polygons = getPolygons(rest);
        if (polygons.size() == 1 && polygons.get(0).length >= 3) {
          this.executor.postModification(Collections.<Selectable>singletonList(other), new Runnable() {
              public void run() {
                other.setPoints(polygons.get(0));
              }
            });
          reshapedRooms.add(id + " (" + format(common / 10000) + " m2 less)");
          tally [4]++;
        } else if (other.isFloorVisible()) {
          floorsLeft.add(id + " (" + format(common / 10000) + " m2, in several parts once cut: reshape it with modify_room)");
        }
      }
    }
    if (!hiddenFloors.isEmpty()) {
      answer.append(". Hid the floor and the ceiling of ").append(hiddenFloors).append(" above it");
    }
    if (!reshapedRooms.isEmpty()) {
      answer.append(". Cut out of the rooms above it: ").append(reshapedRooms);
    }

    // 3. The side of the overlook
    int overlookSide = -1;
    String overlookNote = null;
    if (overlook != null) {
      overlookSide = getOverlookSide(outline, overlook);
      if (overlookSide < 0) {
        overlookNote = "no overlook: \"along\" is north, south, east or west (the side of the room the upper level looks from), "
            + "or \"wall\" the id of a wall of the upper level along a side of the room";
      }
    }

    // 4. The walls around the room, closed up to the ceiling
    // The overlook is on the first level with rooms above the room, up to the level it is opened to
    Level overlookLevel = getLevelAbove(roomLevel, true);
    if (overlookLevel == null || overlookLevel.getElevation() > toLevel.getElevation()) {
      overlookLevel = toLevel;
    }
    List<Level> floorLevels = new ArrayList<Level>();
    for (Level level : this.home.getLevels()) {
      if (level.getElevation() > floor + 1 && level.getElevation() < top - 1 && (level == toLevel || hasRooms(level))) {
        floorLevels.add(level);
      }
    }
    List<String> raisedWalls = new ArrayList<String>();
    List<String> addedWalls = new ArrayList<String>();
    List<String> longWalls = new ArrayList<String>();
    List<Wall> handledWalls = new ArrayList<Wall>();
    float thickness = 0;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == roomLevel) {
        thickness = Math.max(thickness, wall.getThickness());
      }
    }
    if (thickness == 0) {
      thickness = 10;
    }
    for (int i = 0; i < outline.length; i++) {
      if (i == overlookSide) {
        continue;
      }
      float [] start = outline [i];
      float [] end = outline [(i + 1) % outline.length];
      double sideLength = Math.hypot(end [0] - start [0], end [1] - start [1]);
      if (sideLength < 5) {
        continue;
      }
      // Walls of any level, tall enough or one above the other, may already close this side up to the ceiling:
      // nothing is raised nor added beside them
      if (getClosedElevation(start, end, floor, top) >= top - 1) {
        continue;
      }
      // Up to where walls close this side: the walls of the level of the room, then the ones of each level above
      float closedTo = floor;
      List<Wall> lowerWalls = getWallsAlong(start, end, roomLevel);
      float lowerCover = getCover(lowerWalls, start, end);
      if (lowerCover >= 0.8f) {
        closedTo = floor + getLowestTop(lowerWalls);
      }
      boolean upperWallsCloseIt = true;
      for (Level level : floorLevels) {
        {
          List<Wall> upperWalls = getWallsAlong(start, end, level);
          if (getCover(upperWalls, start, end) < 0.8f
              || level.getElevation() + getLowestTop(upperWalls) < Math.min(top, getNextElevation(level, top)) - 1) {
            upperWallsCloseIt = false;
          }
        }
      }
      if (upperWallsCloseIt && lowerCover >= 0.8f) {
        // Closed by the walls of each level, if the lower ones reach the upper ones
        float nextElevation = getNextElevation(roomLevel, top);
        if (closedTo >= nextElevation - roomLevel.getFloorThickness() - 21) {
          continue;
        }
      }
      if (lowerCover >= 0.8f) {
        // The walls of the level of the room rise to the ceiling
        for (final Wall wall : lowerWalls) {
          if (handledWalls.contains(wall)) {
            continue;
          }
          handledWalls.add(wall);
          final float height = top - floor;
          float wallHeight = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
          float wallHeightAtEnd = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : wallHeight;
          if (Math.min(wallHeight, wallHeightAtEnd) < height - 0.5f) {
            this.executor.postModification(Collections.<Selectable>singletonList(wall), new Runnable() {
                public void run() {
                  wall.setHeight(height);
                  if (wall.getHeightAtEnd() != null && wall.getHeightAtEnd() < height) {
                    wall.setHeightAtEnd(null);
                  }
                }
              });
            raisedWalls.add(this.executor.getId(wall));
            tally [4]++;
            if (wall.getLength() > sideLength + 50 && getCover(Collections.singletonList(wall), start, end) > 0
                && !isWallAlongOutline(wall, outline)) {
              longWalls.add(this.executor.getId(wall));
            }
          }
        }
      } else {
        // A side open on the level of the room: a wall of the upper levels closes it above
        for (Level level : floorLevels) {
          if (getCover(getWallsAlong(start, end, level), start, end) < 0.8f) {
            Wall wall = new Wall(start [0], start [1], end [0], end [1], thickness,
                Math.min(top, getNextElevation(level, top)) - level.getElevation());
            addWall(wall, level);
            addedWalls.add(this.executor.getId(wall) + " on \"" + level.getName() + "\"");
            tally [3]++;
          }
        }
      }
    }
    if (!raisedWalls.isEmpty()) {
      answer.append(". Raised ").append(raisedWalls.size() == 1 ? "the wall " : "the walls ").append(raisedWalls)
          .append(" to ").append(format(top - floor)).append(" cm");
    }
    if (!addedWalls.isEmpty()) {
      answer.append(". Added ").append(addedWalls.size() == 1 ? "the wall " : "the walls ").append(addedWalls)
          .append(" above the sides open on the level of the room");
    }
    if (raisedWalls.isEmpty() && addedWalls.isEmpty()) {
      answer.append(". The walls already close its outline up to that ceiling");
    }
    if (!longWalls.isEmpty()) {
      answer.append(". ").append(longWalls).append(longWalls.size() == 1 ? " runs" : " run")
          .append(" beyond the room and ").append(longWalls.size() == 1 ? "is" : "are")
          .append(" now as tall along the rooms beside it: split a wall with modify_wall if only a part should rise");
    }

    // 5. The overlook: the wall of the upper level cut down or removed, and a railing
    if (overlookSide >= 0 && overlookLevel != null) {
      float [] start = outline [overlookSide];
      float [] end = outline [(overlookSide + 1) % outline.length];
      double railingHeight = overlook.get("height") instanceof Number
          ? ((Number)overlook.get("height")).doubleValue() : DEFAULT_RAILING_HEIGHT;
      if (!(railingHeight >= 30 && railingHeight <= 200)) {
        this.executor.warn("Warning: open_to_above changed the height of the railing from " + format(railingHeight)
            + " to " + Math.round(DEFAULT_RAILING_HEIGHT) + " cm: it is from 30 to 200 cm");
        railingHeight = DEFAULT_RAILING_HEIGHT;
      }
      String upperWallMode = overlook.get("upper_wall") != null
          ? overlook.get("upper_wall").toString().trim().toLowerCase(Locale.ENGLISH) : "remove";
      boolean lowWall = upperWallMode.equals("low") || upperWallMode.equals("cut") || upperWallMode.equals("half");
      List<Wall> upperWalls = getWallsAlong(start, end, overlookLevel);
      List<String> changedWalls = new ArrayList<String>();
      List<String> deletedOpenings = new ArrayList<String>();
      List<String> wallsLeft = new ArrayList<String>();
      List<String> splitWalls = new ArrayList<String>();
      float removedThickness = 0;
      for (final Wall wall : upperWalls) {
        String wallId = this.executor.getId(wall);
        if (!isWithinSide(wall, start, end)) {
          // A wall longer than the overlook keeps its parts beyond it, as walls of their own
          int partCount = splitWall(wall, start, end, overlookLevel);
          if (partCount < 0) {
            wallsLeft.add(wallId);
            continue;
          }
          tally [3] += partCount;
          splitWalls.add(wallId);
        }
        // The doors and the windows of that wall, as the hatch used as an opening
        List<Selectable> openings = new ArrayList<Selectable>();
        for (HomePieceOfFurniture piece : this.home.getFurniture()) {
          if (piece.isDoorOrWindow() && piece.getLevel() == overlookLevel && !(piece instanceof HomeFurnitureGroup)
              && HomeDiagnostics.getDistanceToWall(piece.getX(), piece.getY(), wall) <= wall.getThickness() / 2 + piece.getDepth() / 2 + 5) {
            openings.add(piece);
            deletedOpenings.add(this.executor.getId(piece) + " \"" + piece.getName() + "\"");
          }
        }
        this.executor.beginTurnUpdate();
        if (!openings.isEmpty()) {
          this.homeController.getPlanController().deleteItems(openings);
          tally [5] += openings.size();
        }
        if (lowWall) {
          final float height = (float)railingHeight;
          this.executor.postModification(Collections.<Selectable>singletonList(wall), new Runnable() {
              public void run() {
                wall.setHeight(height);
                wall.setHeightAtEnd(null);
              }
            });
          tally [4]++;
        } else {
          removedThickness = Math.max(removedThickness, wall.getThickness());
          this.homeController.getPlanController().deleteItems(Collections.<Selectable>singletonList(wall));
          tally [5]++;
        }
        changedWalls.add(wallId);
      }
      if (removedThickness > 0) {
        // The rooms of the upper level that stopped at the other face of the wall removed go on to the
        // side of the overlook: their floor and their ceiling would otherwise leave a strip open to the sky
        tally [4] += extendRoomsToSide(room, start, end, overlookLevel, removedThickness);
      }
      answer.append(". Overlook along the side from (").append(format(start [0])).append(", ").append(format(start [1]))
          .append(") to (").append(format(end [0])).append(", ").append(format(end [1])).append(") on \"")
          .append(overlookLevel.getName()).append("\"");
      if (!changedWalls.isEmpty()) {
        answer.append(lowWall ? ": cut down to " + format(railingHeight) + " cm " : ": removed ")
            .append(changedWalls.size() == 1 ? "the wall " : "the walls ").append(changedWalls);
      } else {
        answer.append(": no wall of that level stood along it");
      }
      if (!deletedOpenings.isEmpty()) {
        answer.append(", deleted ").append(deletedOpenings).append(" that stood in ").append(changedWalls.size() == 1 ? "it" : "them");
      }
      if (!splitWalls.isEmpty()) {
        answer.append("; ").append(splitWalls).append(splitWalls.size() == 1 ? " was" : " were")
            .append(" longer than that side: ").append(splitWalls.size() == 1 ? "its parts" : "their parts")
            .append(" beyond it stay as walls of their own");
      }
      if (!wallsLeft.isEmpty()) {
        answer.append("; ").append(wallsLeft).append(wallsLeft.size() == 1 ? " is" : " are")
            .append(" round and still ").append(wallsLeft.size() == 1 ? "stands" : "stand")
            .append(" there: reshape with modify_wall, then call open_to_above again");
      }
      if (!lowWall || overlook.get("railing") != null) {
        Object railingName = overlook.get("railing");
        answer.append(". ").append(addOverlookRailing(start, end, overlookLevel,
            railingName != null ? railingName.toString() : null, (float)railingHeight, tally));
      }
    } else if (overlook != null) {
      answer.append(". ").append(overlookNote != null ? overlookNote : "No overlook: no level is between the room and its ceiling");
    }

    // 6. What is left in the opened space
    List<String> inside = getItemsInside(room, roomArea, floor, top, overlookSide);
    inside.addAll(0, floorsLeft);
    if (inside.isEmpty()) {
      answer.append(". Nothing else is inside the opened space");
    } else {
      answer.append(". Still inside the opened space: ");
      for (int i = 0; i < inside.size(); i++) {
        answer.append(i > 0 ? "; " : "").append(inside.get(i));
      }
    }
    notes.add(answer.toString());
  }

  /**
   * Moves to the side from <code>start</code> to <code>end</code> the points of the rooms of a level
   * which stand along it, a wall thickness away at most, on the other side than the opened room.
   * @return the count of rooms changed
   */
  private int extendRoomsToSide(Room openedRoom, float [] start, float [] end, Level level, float thickness) {
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    if (length < 1) {
      return 0;
    }
    final double ux = (end [0] - start [0]) / length;
    final double uy = (end [1] - start [1]) / length;
    int count = 0;
    for (final Room other : new ArrayList<Room>(this.home.getRooms())) {
      if (other == openedRoom || other.getLevel() != level) {
        continue;
      }
      final float [][] points = other.getPoints();
      boolean moved = false;
      for (float [] point : points) {
        double along = (point [0] - start [0]) * ux + (point [1] - start [1]) * uy;
        double across = (point [0] - start [0]) * -uy + (point [1] - start [1]) * ux;
        if (Math.abs(across) > 0.05 && Math.abs(across) <= thickness + 1
            && along >= -thickness - 1 && along <= length + thickness + 1
            // Not the rooms over the opened room itself
            && !openedRoom.containsPoint(point [0], point [1], 0.5f)) {
          point [0] = (float)(point [0] + uy * across);
          point [1] = (float)(point [1] - ux * across);
          moved = true;
        }
      }
      if (moved) {
        this.executor.postModification(Collections.<Selectable>singletonList(other), new Runnable() {
            public void run() {
              other.setPoints(points);
            }
          });
        count++;
      }
    }
    return count;
  }

  /**
   * Shortens a straight wall to its part along the side from <code>start</code> to <code>end</code>,
   * and adds walls like it for its parts beyond the ends of that side.
   * @return the count of walls added, or -1 if the wall can't be cut
   */
  private int splitWall(final Wall wall, float [] start, float [] end, Level level) {
    if (wall.getArcExtent() != null && wall.getArcExtent() != 0) {
      return -1;
    }
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    double dx = (end [0] - start [0]) / length;
    double dy = (end [1] - start [1]) / length;
    final float xStart = wall.getXStart();
    final float yStart = wall.getYStart();
    final float xEnd = wall.getXEnd();
    final float yEnd = wall.getYEnd();
    double wallLength = Math.hypot(xEnd - xStart, yEnd - yStart);
    // The place of the ends of the side along the wall, from 0 to 1
    double t1 = ((start [0] - xStart) * (xEnd - xStart) + (start [1] - yStart) * (yEnd - yStart)) / (wallLength * wallLength);
    double t2 = ((end [0] - xStart) * (xEnd - xStart) + (end [1] - yStart) * (yEnd - yStart)) / (wallLength * wallLength);
    final double from = Math.max(0, Math.min(t1, t2));
    final double to = Math.min(1, Math.max(t1, t2));
    if (!(to - from > 0.01) || dx * dx + dy * dy < 0.5) {
      return -1;
    }
    Float height = wall.getHeight();
    Float heightAtEnd = wall.getHeightAtEnd();
    int count = 0;
    for (double [] part : new double [][] {{0, from}, {to, 1}}) {
      if ((part [1] - part [0]) * wallLength > 5) {
        Wall partWall = (Wall)wall.duplicate();
        // An id of its own
        partWall.setProperty(HomeItemIds.ID_PROPERTY, null);
        partWall.setWallAtStart(null);
        partWall.setWallAtEnd(null);
        partWall.setXStart((float)(xStart + (xEnd - xStart) * part [0]));
        partWall.setYStart((float)(yStart + (yEnd - yStart) * part [0]));
        partWall.setXEnd((float)(xStart + (xEnd - xStart) * part [1]));
        partWall.setYEnd((float)(yStart + (yEnd - yStart) * part [1]));
        if (height != null && heightAtEnd != null) {
          // A wall whose top slopes keeps its slope
          partWall.setHeight((float)(height + (heightAtEnd - height) * part [0]));
          partWall.setHeightAtEnd((float)(height + (heightAtEnd - height) * part [1]));
        }
        addWall(partWall, level);
        count++;
      }
    }
    this.executor.postModification(Collections.<Selectable>singletonList(wall), new Runnable() {
        public void run() {
          wall.setWallAtStart(null);
          wall.setWallAtEnd(null);
          wall.setXStart((float)(xStart + (xEnd - xStart) * from));
          wall.setYStart((float)(yStart + (yEnd - yStart) * from));
          wall.setXEnd((float)(xStart + (xEnd - xStart) * to));
          wall.setYEnd((float)(yStart + (yEnd - yStart) * to));
        }
      });
    return count;
  }

  /**
   * Builds a railing along the side of an overlook with the command <code>add_railing</code>.
   * @return a sentence about the railing, or <code>null</code> if it couldn't be built
   */
  private String addBuiltRailing(float [] start, float [] end, Level level, String style, float height, int [] tally) {
    java.util.Map<String, Object> parameters = new java.util.LinkedHashMap<String, Object>();
    parameters.put("action", "add_railing");
    List<Object> points = new ArrayList<Object>();
    for (float [] point : new float [][] {start, end}) {
      List<Object> coordinates = new ArrayList<Object>();
      coordinates.add(Double.valueOf(point [0]));
      coordinates.add(Double.valueOf(point [1]));
      points.add(coordinates);
    }
    parameters.put("points", points);
    parameters.put("height", Double.valueOf(height));
    parameters.put("style", style);
    if (level != null) {
      parameters.put("level", level.getName());
    }
    List<String> railingNotes = new ArrayList<String>();
    int [] railingTally = new int [tally.length];
    this.executor.applySubCommand(new AssistantCommand("add_railing", parameters), railingTally, railingNotes);
    int changes = 0;
    for (int i = 0; i < tally.length; i++) {
      tally [i] += railingTally [i];
      changes += railingTally [i];
    }
    if (changes == 0) {
      return null;
    }
    StringBuilder text = new StringBuilder("A railing of style " + style + " was built along the overlook (add_railing)");
    for (String note : railingNotes) {
      text.append(": ").append(note);
    }
    return text.toString();
  }

  /**
   * Places a railing along the side of an overlook: the catalog piece of the given name,
   * repeated along the side rather than stretched. The only place where the railing of an
   * overlook is built, to be replaced by the command which builds railings along any line.
   * @return a sentence about the railing
   */
  private String addOverlookRailing(float [] start, float [] end, Level level, String railingName,
                                    float height, int [] tally) {
    String query = railingName != null && railingName.trim().length() > 0 ? railingName.trim() : null;
    String style = query != null ? query.toLowerCase(Locale.ENGLISH).replace(' ', '_').replace('-', '_') : null;
    if ("square".equals(style) || "turned_balusters".equals(style) || "cable".equals(style) || "glass".equals(style)) {
      // A railing built along the side, its balusters repeated
      String built = addBuiltRailing(start, end, level, style, height, tally);
      if (built != null) {
        return built;
      }
    }
    CatalogPieceOfFurniture catalogPiece = null;
    String [] defaultNames = {"Railing", "Handrail", "Balustrade", "Guardrail"};
    String [] names = defaultNames;
    if (query != null) {
      // The piece asked, otherwise a railing of the catalog
      names = new String [defaultNames.length + 1];
      names [0] = query;
      System.arraycopy(defaultNames, 0, names, 1, defaultNames.length);
    }
    for (String name : names) {
      catalogPiece = this.executor.findCatalogPiece(name, false);
      if (catalogPiece != null && !containsWords(catalogPiece.getName(), name)) {
        // The closest piece of the catalog isn't the one asked
        catalogPiece = null;
      }
      if (catalogPiece != null) {
        break;
      }
    }
    if (catalogPiece == null) {
      String built = addBuiltRailing(start, end, level, "square", height, tally);
      if (built != null) {
        return (query != null ? "The catalog has no piece named \"" + query + "\" and no railing: " : "") + built;
      }
      return "No railing: " + (query != null ? "the catalog has no piece named \"" + query + "\" and no railing"
          : "the catalog has no railing, give the name of a piece with \"railing\" (search_catalog finds one)")
          + ". The overlook is open and unguarded";
    }
    String replaced = query != null && !containsWords(catalogPiece.getName(), query)
        ? " (the catalog has no piece named \"" + query + "\")" : "";
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    float pieceWidth = Math.max(10, catalogPiece.getWidth());
    int count = (int)Math.max(1, Math.round(length / pieceWidth));
    count = Math.min(count, 200);
    float width = (float)(length / count);
    float angle = (float)Math.atan2(end [1] - start [1], end [0] - start [0]);
    List<String> ids = new ArrayList<String>();
    for (int i = 0; i < count; i++) {
      HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
      float t = (i + 0.5f) / count;
      piece.setX(start [0] + (end [0] - start [0]) * t);
      piece.setY(start [1] + (end [1] - start [1]) * t);
      piece.setAngle(angle);
      piece.setWidth(width);
      piece.setHeight(height);
      piece.setElevation(0);
      piece.setLevel(level);
      piece.setProperty(HomeItemTags.TAGS_PROPERTY, "railing");
      this.executor.getShapeCommands().add(piece);
      ids.add(this.executor.getId(piece));
      tally [0]++;
    }
    return "Railing: " + count + (count == 1 ? " piece" : " pieces") + " \"" + catalogPiece.getName() + "\"" + replaced + " of "
        + format(width) + " cm (" + format(pieceWidth) + " cm in the catalog), " + format(height) + " cm high, "
        + (count == 1 ? ids.get(0) : ids.get(0) + " to " + ids.get(ids.size() - 1));
  }

  /**
   * Returns <code>true</code> if <code>name</code> holds every word of <code>query</code>.
   */
  private static boolean containsWords(String name, String query) {
    String lowerName = name.toLowerCase(Locale.ENGLISH);
    for (String word : query.toLowerCase(Locale.ENGLISH).split("[^\\p{L}\\p{N}]+")) {
      if (word.length() > 0 && lowerName.indexOf(word) < 0) {
        return false;
      }
    }
    return true;
  }

  private void addWall(Wall wall, Level level) {
    this.executor.beginTurnUpdate();
    // Walls are added to the selected level
    Level selectedLevel = this.home.getSelectedLevel();
    if (level != selectedLevel) {
      this.home.setSelectedLevel(level);
    }
    this.homeController.getPlanController().addWalls(Collections.singletonList(wall));
    if (this.home.getSelectedLevel() != selectedLevel && selectedLevel != null) {
      this.home.setSelectedLevel(selectedLevel);
    }
  }

  /**
   * Returns what is inside the space of a room between its floor and <code>top</code>, apart from
   * what the room is made of: the roofs, the slabs and the furniture of the levels above.
   */
  private List<String> getItemsInside(Room room, Area roomArea, float floor, float top, int overlookSide) {
    List<String> inside = new ArrayList<String>();
    Level roomLevel = room.getLevel();
    Rectangle2D roomBounds = roomArea.getBounds2D();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (!piece.isVisible() || piece.getLevel() == null) {
        continue;
      }
      String structure = piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY);
      float bottom = piece.getLevel().getElevation() + piece.getElevation();
      float pieceTop = bottom + piece.getHeight();
      boolean upperLevel = piece.getLevel().getElevation() > floor + 1;
      if (pieceTop <= floor + 1 || bottom >= top - 1) {
        continue;
      }
      // The outline of the piece in the plan
      Area pieceArea = getArea(piece.getPoints());
      if (!pieceArea.getBounds2D().intersects(roomBounds)) {
        continue;
      }
      double common = getSurface(intersect(roomArea, pieceArea));
      if (common < 100) {
        continue;
      }
      String id = this.executor.getId(piece) + " \"" + piece.getName() + "\"";
      String heights = "from " + format(bottom - floor) + " to " + format(pieceTop - floor) + " cm above the floor";
      if ("roof".equals(structure) || "dormer".equals(structure)
          || piece.getProperty(AssistantShapeCommands.ROOF_SPEC_PROPERTY) != null) {
        // A roof of an other part of the house, with its overhang and its gutters; the one above the room is its cover
        if (bottom < top - 30) {
          inside.add("the roof " + id + " with its overhang, gutters and downspouts, over " + format(common / 10000)
              + " m2 of the room, " + heights);
        }
      } else if (room.getId().equals(piece.getProperty(AssistantShapeCommands.ROOM_CEILING_PIECE_PROPERTY))) {
        continue;
      } else if ("slab".equals(structure) || "ceiling".equals(structure)) {
        if (bottom > floor + 30) {
          inside.add("the " + structure + " " + id + ", over " + format(common / 10000) + " m2, " + heights);
        }
      } else if (upperLevel && structure == null && !"railing".equals(piece.getProperty(HomeItemTags.TAGS_PROPERTY))
                 && roomArea.contains(piece.getX(), piece.getY())) {
        inside.add(id + " of the level \"" + piece.getLevel().getName() + "\", without floor under it");
      }
    }
    // The walls of the levels above which cross the room
    for (Wall wall : this.home.getWalls()) {
      Level level = wall.getLevel();
      if (level != null && level != roomLevel && level.getElevation() > floor + 1 && level.getElevation() < top - 1) {
        float middleX = (wall.getXStart() + wall.getXEnd()) / 2;
        float middleY = (wall.getYStart() + wall.getYEnd()) / 2;
        Area shrunk = shrink(roomArea, wall.getThickness() + 5);
        if (shrunk.contains(middleX, middleY)) {
          inside.add("the wall " + this.executor.getId(wall) + " of the level \"" + level.getName() + "\" crosses it");
        }
      }
    }
    return inside;
  }

  // ---- Geometry ----

  private Room findRoom(String token) {
    if (token == null || token.trim().length() == 0) {
      return null;
    }
    token = token.trim();
    Room found = null;
    int count = 0;
    for (Room room : this.home.getRooms()) {
      if (token.equalsIgnoreCase(this.executor.getId(room))) {
        return room;
      } else if (token.equalsIgnoreCase(room.getName())) {
        found = room;
        count++;
      }
    }
    return count == 1 ? found : null;
  }

  private Level getLevelAbove(Level level) {
    // The floors of the building, if there are some above
    Level above = getLevelAbove(level, true);
    return above != null ? above : getLevelAbove(level, false);
  }

  /**
   * Returns the lowest level above <code>level</code>.
   * @param withRooms <code>true</code> to ignore the levels which hold no room: the ones made for
   *          a roof, for the ground or to place helpers aren't floors of the building
   */
  private Level getLevelAbove(Level level, boolean withRooms) {
    Level above = null;
    for (Level other : this.home.getLevels()) {
      if (other.getElevation() > level.getElevation() + 1
          && (above == null || other.getElevation() < above.getElevation())
          && (!withRooms || hasRooms(other))) {
        above = other;
      }
    }
    return above;
  }

  private boolean hasRooms(Level level) {
    for (Room room : this.home.getRooms()) {
      if (room.getLevel() == level) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the height above the floor of <code>level</code> of the ceilings that its rooms got with
   * <code>ceiling_height</code>: the one of the rooms over <code>roomArea</code>, otherwise the one that
   * most of the surface of the rooms of the level has, or NaN if their ceiling is the top of the level.
   */
  private double getCeilingHeightOfLevel(Level level, Room openedRoom, Area roomArea) {
    Map<Double, Double> over = new java.util.LinkedHashMap<Double, Double>();
    Map<Double, Double> others = new java.util.LinkedHashMap<Double, Double>();
    double total = 0;
    for (Room room : this.home.getRooms()) {
      if (room.getLevel() != level || room == openedRoom) {
        continue;
      }
      Area area = getArea(room.getPoints());
      double surface = getSurface(area);
      double height = getCeilingHeight(room);
      // The rooms drawn over the opened one have no ceiling left
      if (room.isCeilingVisible() || !Double.isNaN(height)) {
        total += surface;
      }
      if (Double.isNaN(height)) {
        continue;
      }
      Double key = Double.valueOf(Math.round(height * 100) / 100.);
      double common = getSurface(intersect(roomArea, area));
      Map<Double, Double> surfaces = common >= 100 ? over : others;
      surfaces.put(key, Double.valueOf((surfaces.containsKey(key) ? surfaces.get(key).doubleValue() : 0) + surface));
    }
    Map<Double, Double> surfaces = !over.isEmpty() ? over : others;
    double best = Double.NaN;
    double bestSurface = 0;
    for (Map.Entry<Double, Double> entry : surfaces.entrySet()) {
      if (entry.getValue().doubleValue() > bestSurface) {
        bestSurface = entry.getValue().doubleValue();
        best = entry.getKey().doubleValue();
      }
    }
    // Not the ceiling of a few rooms lowered among others at the top of the level
    return !over.isEmpty() || bestSurface >= 0.5 * total ? best : Double.NaN;
  }

  /**
   * Returns the elevation up to which walls close the side from <code>start</code> to <code>end</code>
   * above <code>floor</code>: walls of any level, one above the other or tall enough by themselves,
   * along the side or parallel to it a short way from it, as around a chimney breast.
   */
  private float getClosedElevation(float [] start, float [] end, float floor, float top) {
    List<Wall> walls = new ArrayList<Wall>();
    for (Wall wall : this.home.getWalls()) {
      if (isAlong(wall, start, end, 60) && getCover(Collections.singletonList(wall), start, end) > 0.02f) {
        walls.add(wall);
      }
    }
    // What a floor or the gap left between two walls one above the other may leave open
    final float gap = 36;
    float closedTo = floor;
    while (closedTo < top - 1) {
      // The highest elevation that the walls standing at this height reach along most of the side
      List<Float> tops = new ArrayList<Float>();
      for (Wall wall : walls) {
        float base = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
        float wallTop = base + getLowestTop(Collections.singletonList(wall));
        if (base <= closedTo + gap && wallTop > closedTo + 1) {
          tops.add(Float.valueOf(wallTop));
        }
      }
      Collections.sort(tops, Collections.reverseOrder());
      float reached = closedTo;
      for (Float candidate : tops) {
        List<Wall> tallWalls = new ArrayList<Wall>();
        for (Wall wall : walls) {
          float base = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
          if (base <= closedTo + gap && base + getLowestTop(Collections.singletonList(wall)) >= candidate.floatValue() - 0.5f) {
            tallWalls.add(wall);
          }
        }
        if (getCover(tallWalls, start, end) >= 0.8f) {
          reached = candidate.floatValue();
          break;
        }
      }
      if (reached <= closedTo + 1) {
        break;
      }
      closedTo = reached;
    }
    return closedTo;
  }

  /**
   * Returns the elevation of the floor of the level above <code>level</code>, or <code>top</code> if there's none under it.
   */
  private float getNextElevation(Level level, float top) {
    Level above = getLevelAbove(level);
    return above != null && above.getElevation() < top ? above.getElevation() : top;
  }

  /**
   * Returns the index of the side of <code>outline</code> where the overlook is: the one which
   * faces the given cardinal point, or the one the given wall runs along.
   */
  private int getOverlookSide(float [][] outline, Map<?, ?> overlook) {
    if (overlook.get("wall") != null) {
      String token = overlook.get("wall").toString().trim();
      for (Wall wall : this.home.getWalls()) {
        if (token.equalsIgnoreCase(this.executor.getId(wall))) {
          int best = -1;
          float bestCover = 0.3f;
          for (int i = 0; i < outline.length; i++) {
            float cover = getCover(Collections.singletonList(wall), outline [i], outline [(i + 1) % outline.length]);
            if (cover > bestCover && isAlong(wall, outline [i], outline [(i + 1) % outline.length])) {
              bestCover = cover;
              best = i;
            }
          }
          return best;
        }
      }
      return -1;
    }
    String along = overlook.get("along") != null ? overlook.get("along").toString().trim().toLowerCase(Locale.ENGLISH) : "";
    double directionX = along.equals("east") ? 1 : along.equals("west") ? -1 : 0;
    double directionY = along.equals("south") ? 1 : along.equals("north") ? -1 : 0;
    if (directionX == 0 && directionY == 0) {
      return -1;
    }
    // The longest side among the ones the farthest in that direction, across it
    int best = -1;
    double bestScore = Double.NEGATIVE_INFINITY;
    double extreme = Double.NEGATIVE_INFINITY;
    for (float [] point : outline) {
      extreme = Math.max(extreme, point [0] * directionX + point [1] * directionY);
    }
    for (int i = 0; i < outline.length; i++) {
      float [] start = outline [i];
      float [] end = outline [(i + 1) % outline.length];
      double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
      if (length < 5) {
        continue;
      }
      // How much the side lies across the direction
      double across = Math.abs((end [0] - start [0]) * directionY - (end [1] - start [1]) * directionX) / length;
      double place = ((start [0] + end [0]) * directionX + (start [1] + end [1]) * directionY) / 2;
      if (across > 0.7 && extreme - place < 30) {
        double score = length * across;
        if (score > bestScore) {
          bestScore = score;
          best = i;
        }
      }
    }
    return best;
  }

  private static boolean isAlong(Wall wall, float [] start, float [] end) {
    return isAlong(wall, start, end, 8);
  }

  /**
   * Returns <code>true</code> if a straight wall is parallel to the side from <code>start</code> to
   * <code>end</code>, its middle at <code>distance</code> cm at most from the line of the side, beyond half its thickness.
   */
  private static boolean isAlong(Wall wall, float [] start, float [] end, float distance) {
    double sideLength = Math.hypot(end [0] - start [0], end [1] - start [1]);
    double wallLength = wall.getLength();
    if (sideLength < 1 || wallLength < 1 || wall.getArcExtent() != null && wall.getArcExtent() != 0) {
      return false;
    }
    double cross = Math.abs((end [0] - start [0]) * (wall.getYEnd() - wall.getYStart())
        - (end [1] - start [1]) * (wall.getXEnd() - wall.getXStart())) / (sideLength * wallLength);
    // Less than 6 degrees between them
    if (cross > 0.1) {
      return false;
    }
    float tolerance = wall.getThickness() / 2 + distance;
    float middleX = (wall.getXStart() + wall.getXEnd()) / 2;
    float middleY = (wall.getYStart() + wall.getYEnd()) / 2;
    return getDistanceToLine(middleX, middleY, start, end) <= tolerance;
  }

  private static double getDistanceToLine(float x, float y, float [] start, float [] end) {
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    return Math.abs((end [0] - start [0]) * (y - start [1]) - (end [1] - start [1]) * (x - start [0])) / length;
  }

  /**
   * Returns the walls of a level which run along the side from <code>start</code> to <code>end</code>.
   */
  private List<Wall> getWallsAlong(float [] start, float [] end, Level level) {
    List<Wall> walls = new ArrayList<Wall>();
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == level && isAlong(wall, start, end)
          && getCover(Collections.singletonList(wall), start, end) > 0.02f) {
        walls.add(wall);
      }
    }
    return walls;
  }

  /**
   * Returns the share of the side from <code>start</code> to <code>end</code> that the given walls run along.
   */
  private static float getCover(List<Wall> walls, float [] start, float [] end) {
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    if (length < 1) {
      return 0;
    }
    double dx = (end [0] - start [0]) / length;
    double dy = (end [1] - start [1]) / length;
    // The parts of the side covered, on 200 samples
    boolean [] covered = new boolean [200];
    for (Wall wall : walls) {
      double t1 = ((wall.getXStart() - start [0]) * dx + (wall.getYStart() - start [1]) * dy) / length;
      double t2 = ((wall.getXEnd() - start [0]) * dx + (wall.getYEnd() - start [1]) * dy) / length;
      // A wall goes half its thickness beyond its ends at corners
      double margin = wall.getThickness() / 2 / length;
      double from = Math.min(t1, t2) - margin;
      double to = Math.max(t1, t2) + margin;
      for (int i = 0; i < covered.length; i++) {
        double t = (i + 0.5) / covered.length;
        if (t >= from && t <= to) {
          covered [i] = true;
        }
      }
    }
    int count = 0;
    for (boolean sample : covered) {
      if (sample) {
        count++;
      }
    }
    return (float)count / covered.length;
  }

  private float getLowestTop(List<Wall> walls) {
    float lowest = Float.MAX_VALUE;
    for (Wall wall : walls) {
      float height = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
      float heightAtEnd = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : height;
      lowest = Math.min(lowest, Math.min(height, heightAtEnd));
    }
    return walls.isEmpty() ? 0 : lowest;
  }

  /**
   * Returns <code>true</code> if a wall doesn't run more than 15 cm beyond the ends of a side.
   */
  private static boolean isWithinSide(Wall wall, float [] start, float [] end) {
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    double dx = (end [0] - start [0]) / length;
    double dy = (end [1] - start [1]) / length;
    double t1 = (wall.getXStart() - start [0]) * dx + (wall.getYStart() - start [1]) * dy;
    double t2 = (wall.getXEnd() - start [0]) * dx + (wall.getYEnd() - start [1]) * dy;
    double margin = wall.getThickness() / 2 + 15;
    return Math.min(t1, t2) >= -margin && Math.max(t1, t2) <= length + margin;
  }

  /**
   * Returns <code>true</code> if a wall runs along sides of the outline over its whole length.
   */
  private static boolean isWallAlongOutline(Wall wall, float [][] outline) {
    for (int i = 0; i < outline.length; i++) {
      float [] start = outline [i];
      float [] end = outline [(i + 1) % outline.length];
      if (Math.hypot(end [0] - start [0], end [1] - start [1]) >= 5 && isAlong(wall, start, end)
          && isWithinSide(wall, start, end)) {
        return true;
      }
    }
    return false;
  }

  static Area getArea(float [][] points) {
    Path2D.Float path = new Path2D.Float();
    for (int i = 0; i < points.length; i++) {
      if (i == 0) {
        path.moveTo(points [i][0], points [i][1]);
      } else {
        path.lineTo(points [i][0], points [i][1]);
      }
    }
    path.closePath();
    return new Area(path);
  }

  private static Area intersect(Area area1, Area area2) {
    Area area = new Area(area1);
    area.intersect(area2);
    return area;
  }

  /**
   * Returns <code>area</code> without a band of the given width along its border, by its bounds:
   * enough to tell what is well inside a room from what stands along its walls.
   */
  private static Area shrink(Area area, float margin) {
    Area shrunk = new Area(area);
    for (float [] shift : new float [][] {{margin, 0}, {-margin, 0}, {0, margin}, {0, -margin}}) {
      Area shifted = new Area(area);
      shifted.transform(java.awt.geom.AffineTransform.getTranslateInstance(shift [0], shift [1]));
      shrunk.intersect(shifted);
    }
    return shrunk;
  }

  /**
   * Returns the polygons of an area, the parts smaller than 100 cm2 left out.
   */
  private static List<float [][]> getPolygons(Area area) {
    List<float [][]> polygons = new ArrayList<float [][]>();
    List<float []> points = new ArrayList<float []>();
    float [] coords = new float [6];
    for (PathIterator it = area.getPathIterator(null, 1); !it.isDone(); it.next()) {
      int type = it.currentSegment(coords);
      if (type == PathIterator.SEG_MOVETO) {
        points = new ArrayList<float []>();
        points.add(new float [] {coords [0], coords [1]});
      } else if (type == PathIterator.SEG_LINETO) {
        float [] last = points.get(points.size() - 1);
        if (Math.abs(last [0] - coords [0]) > 0.01f || Math.abs(last [1] - coords [1]) > 0.01f) {
          points.add(new float [] {coords [0], coords [1]});
        }
      } else if (type == PathIterator.SEG_CLOSE) {
        if (points.size() > 1) {
          float [] first = points.get(0);
          float [] last = points.get(points.size() - 1);
          if (Math.abs(last [0] - first [0]) <= 0.01f && Math.abs(last [1] - first [1]) <= 0.01f) {
            points.remove(points.size() - 1);
          }
        }
        float [][] polygon = points.toArray(new float [points.size()][]);
        if (polygon.length >= 3 && Math.abs(getSignedSurface(polygon)) >= 100) {
          polygons.add(polygon);
        }
      }
    }
    return polygons;
  }

  private static double getSignedSurface(float [][] polygon) {
    double surface = 0;
    for (int i = 0; i < polygon.length; i++) {
      float [] point = polygon [i];
      float [] next = polygon [(i + 1) % polygon.length];
      surface += (double)point [0] * next [1] - (double)next [0] * point [1];
    }
    return surface / 2;
  }

  /**
   * Returns the surface of an area in cm2.
   */
  static double getSurface(Area area) {
    double surface = 0;
    List<float []> points = new ArrayList<float []>();
    float [] coords = new float [6];
    for (PathIterator it = area.getPathIterator(null, 1); !it.isDone(); it.next()) {
      int type = it.currentSegment(coords);
      if (type == PathIterator.SEG_MOVETO) {
        points = new ArrayList<float []>();
        points.add(new float [] {coords [0], coords [1]});
      } else if (type == PathIterator.SEG_LINETO) {
        points.add(new float [] {coords [0], coords [1]});
      } else if (type == PathIterator.SEG_CLOSE) {
        // Holes turn the other way and are subtracted
        surface += getSignedSurface(points.toArray(new float [points.size()][]));
      }
    }
    return Math.abs(surface);
  }

  static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }
}
