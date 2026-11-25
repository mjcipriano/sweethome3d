/*
 * HomeDiagnostics.java 13 Nov 2026
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
import java.util.List;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * Looks for the usual defects of a home plan: walls that don't quite meet,
 * duplicated or empty items, doors and windows that aren't in a wall,
 * furniture under the floor or through the ceiling, and with {@link HomeDefects} what
 * shows in 3D: roofs in rooms, walls over each other or above the roof... Each {@link Issue issue}
 * names the items it's about with the ids of the assistant
 * (<code>F#</code>, <code>W#</code>, <code>R#</code>) and tells whether it can
 * be fixed automatically.
 * @author Sweet Home 3D
 */
public class HomeDiagnostics {
  /** The kinds of issues found. */
  public enum Type {
    /** Two wall ends are close to each other but the walls aren't joined. */
    WALL_GAP(true),
    /** A wall is too short to be seen. */
    TINY_WALL(true),
    /** Two walls are drawn at the same place. */
    DUPLICATE_WALL(true),
    /** A room has no area. */
    EMPTY_ROOM(true),
    /** A door or a window isn't in a wall. */
    DOOR_OFF_WALL(true),
    /** Two identical pieces of furniture are at the same place. */
    DUPLICATE_FURNITURE(true),
    /** A piece of furniture is under the floor. */
    FURNITURE_BELOW_FLOOR(true),
    /** A piece of furniture goes through the ceiling. */
    FURNITURE_ABOVE_CEILING(false),
    /** A piece of furniture is in no room. */
    FURNITURE_OUTSIDE_ROOMS(false),
    /** A room surrounded by walls has no door. */
    ROOM_WITHOUT_DOOR(false),
    /** A room has no name. */
    UNNAMED_ROOM(false),
    /** A model is heavy and has no reduced version displayed. */
    HEAVY_MODEL(false),
    /** A door or a window is higher than the roof above it. */
    DOOR_THROUGH_ROOF(false),
    /** A wall stands beyond a setback line. */
    BEYOND_SETBACK(false),
    /** A wall stands beyond a property line. */
    BEYOND_PROPERTY_LINE(false),
    /** A fence crosses a wall or runs inside a building. */
    FENCE_THROUGH_BUILDING(false),
    /** A fence crosses a driveway without a gate. */
    FENCE_ON_DRIVEWAY(false),
    /** A gate can't open. */
    GATE_BLOCKED(false),
    /** A pool fence doesn't follow the usual rules. */
    POOL_FENCE(false),
    /** A fence stands beyond the property lines. */
    FENCE_BEYOND_PROPERTY_LINE(false),
    /** The ground changed under the posts of a fence. */
    FENCE_OFF_GROUND(false),
    /** A roof, its overhang or its gutters come into a room under its ceiling. */
    ROOF_IN_ROOM(true),
    /** Two walls are drawn over each other along a part of their length. */
    OVERLAPPING_WALL(true),
    /** A door or a window stands in a wall without being lined up with it. */
    DOOR_MISALIGNED(true),
    /** The top of a wall passes the roof that covers it. */
    WALL_ABOVE_ROOF(true),
    /** A room higher than its level isn't closed by walls up to its ceiling. */
    OPEN_TWO_STORY_ROOM(true),
    /** The ceiling of a room or a slab passes the roof over it. */
    CEILING_ABOVE_ROOF(false),
    /** A roof is at the height of a ceiling: they show through each other. */
    ROOF_AT_CEILING(false),
    /** A side of a wall that faces a room has an outside material. */
    WALL_OUTSIDE_FINISH_INDOORS(false),
    /** A side of a wall that faces the outside has a finish of the rooms. */
    WALL_INSIDE_FINISH_OUTDOORS(false),
    /** A side of a wall that can be seen has no color and no texture. */
    WALL_SIDE_UNPAINTED(false);

    private final boolean fixable;

    private Type(boolean fixable) {
      this.fixable = fixable;
    }

    /**
     * Returns <code>true</code> if the assistant can fix issues of this type.
     */
    public boolean isFixable() {
      return this.fixable;
    }

    /**
     * Returns how serious issues of this type are: 2 for an error (what is built is wrong),
     * 1 for a warning (probably a mistake), 0 for an information.
     */
    public int getSeverity() {
      switch (this) {
        case DOOR_THROUGH_ROOF :
        case BEYOND_SETBACK :
        case BEYOND_PROPERTY_LINE :
        case DUPLICATE_WALL :
        case EMPTY_ROOM :
        case FENCE_THROUGH_BUILDING :
        case ROOF_IN_ROOM :
        case OVERLAPPING_WALL :
          return 2;
        case ROOF_AT_CEILING :
        case WALL_INSIDE_FINISH_OUTDOORS :
        case WALL_SIDE_UNPAINTED :
        case FURNITURE_OUTSIDE_ROOMS :
        case UNNAMED_ROOM :
        case HEAVY_MODEL :
          return 0;
        default :
          return 1;
      }
    }
  }

  /** The names of the severities, from the least serious. */
  public static final String [] SEVERITIES = {"info", "warning", "error"};

  /** The property of a home that keeps the ids and the tags its checks ignore. */
  public static final String IGNORE_PROPERTY = "assistantCheckIgnore";

  /**
   * An issue found in a home.
   */
  public static final class Issue {
    private final Type       type;
    private final String     message;
    private final Selectable item;
    private final Selectable otherItem;
    private final boolean    fixable;
    private final float      value;

    Issue(Type type, String message, Selectable item, Selectable otherItem) {
      this(type, message, item, otherItem,
          type.isFixable() && (type != Type.DOOR_OFF_WALL || otherItem != null), 0);
    }

    /**
     * Creates an issue.
     * @param fixable whether this issue can be repaired, for a type that can be
     * @param value   the measure its repair needs, if any
     */
    Issue(Type type, String message, Selectable item, Selectable otherItem, boolean fixable, float value) {
      this.type = type;
      this.message = message;
      this.item = item;
      this.otherItem = otherItem;
      this.fixable = fixable && type.isFixable();
      this.value = value;
    }

    /**
     * Returns <code>true</code> if the assistant can fix this issue: some issues of a
     * type that can be fixed need a decision.
     */
    public boolean isFixable() {
      return this.fixable;
    }

    /**
     * Returns the measure the repair of this issue needs (the height to give to a wall), or 0.
     */
    float getValue() {
      return this.value;
    }

    public Type getType() {
      return this.type;
    }

    /**
     * Returns a sentence describing this issue.
     */
    public String getMessage() {
      return this.message;
    }

    /**
     * Returns the item this issue is about.
     */
    public Selectable getItem() {
      return this.item;
    }

    /**
     * Returns the second item this issue is about (the wall to join, the
     * original of a duplicate, the wall a door should be in), or <code>null</code>.
     */
    public Selectable getOtherItem() {
      return this.otherItem;
    }
  }

  /** Wall ends closer than this are meant to meet. */
  static final float WALL_GAP_TOLERANCE = 20;
  /** Walls shorter than this are drawing accidents. */
  static final float TINY_WALL_LENGTH = 1;
  /** A door farther than this from any wall is left where it is. */
  static final float DOOR_SNAP_DISTANCE = 100;
  private static final long HEAVY_MODEL_SIZE = 20L * 1024 * 1024;
  /** Maximum count of issues of the same type reported individually. */
  private static final int MAX_ISSUES_PER_TYPE = 15;

  private HomeDiagnostics() {
    // Utility class
  }

  /**
   * Returns the issues of <code>home</code>. The given lists give the items
   * their ids: the item at index <code>i</code> of <code>furniture</code> is
   * <code>F(i+1)</code>, and so on.
   */
  public static List<Issue> check(Home home, List<HomePieceOfFurniture> furniture,
                                  List<Wall> walls, List<Room> rooms) {
    List<Issue> issues = new ArrayList<Issue>();
    checkWalls(home, walls, issues);
    checkRooms(home, rooms, issues);
    checkFurniture(home, furniture, walls, rooms, issues);
    checkRoofs(home, furniture, issues);
    checkLines(home, walls, issues);
    // What shows in 3D rather than in the plan
    int [] counts = new int [Type.values().length];
    HomeDefects.checkOverlappingWalls(home, walls, issues, counts);
    HomeDefects.checkRoofsAndCeilings(home, furniture, rooms, issues, counts);
    HomeDefects.checkWallsAboveRoof(home, walls, issues, counts);
    HomeDefects.checkTwoStoryRooms(home, walls, rooms, issues, counts);
    HomeDefects.checkWallFinishes(home, walls, rooms, issues, counts);
    return issues;
  }

  /**
   * Reports the doors and the windows that are higher than the roof above them.
   */
  private static void checkRoofs(Home home, List<HomePieceOfFurniture> furniture, List<Issue> issues) {
    List<HomeRoofs.Roof> roofs = HomeRoofs.get(home);
    if (roofs.isEmpty()) {
      return;
    }
    int [] counts = new int [Type.values().length];
    for (int i = 0; i < furniture.size(); i++) {
      HomePieceOfFurniture piece = furniture.get(i);
      if (!home.getFurniture().contains(piece) || !piece.isDoorOrWindow() || !piece.isVisible()
          || piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) != null) {
        continue;
      }
      float bottom = piece.getGroundElevation();
      float top = bottom + piece.getHeight();
      double lowest = Double.NaN;
      // At its middle and near its two ends
      for (int sample = -1; sample <= 1; sample++) {
        double along = sample * piece.getWidth() * 0.45;
        double ceiling = HomeRoofs.getCeilingElevation(roofs, piece.getX() + along * Math.cos(piece.getAngle()),
            piece.getY() + along * Math.sin(piece.getAngle()), bottom + 1);
        if (!Double.isNaN(ceiling) && !(ceiling >= lowest)) {
          lowest = ceiling;
        }
      }
      if (!Double.isNaN(lowest) && top > lowest + 2) {
        float floor = piece.getLevel() != null ? piece.getLevel().getElevation() : 0;
        add(issues, counts, new Issue(Type.DOOR_THROUGH_ROOF, id(piece, "F", i) + " \""
            + (piece.getName() != null ? piece.getName() : "Unnamed") + "\" goes through the roof: its top is "
            + format(top - floor) + " cm above the floor, where the underside of the roof is at " + format((float)(lowest - floor))
            + " cm (" + format((float)(top - lowest)) + " cm too high)", piece, null));
      }
    }
  }

  /**
   * Reports the walls that stand beyond the setback lines and the property lines.
   */
  private static void checkLines(Home home, List<Wall> walls, List<Issue> issues) {
    int [] counts = new int [Type.values().length];
    for (Object [] beyond : AssistantSiteCommands.getWallsBeyondLines(home)) {
      Wall wall = (Wall)beyond [0];
      com.eteks.sweethome3d.model.Polyline line = (com.eteks.sweethome3d.model.Polyline)beyond [1];
      boolean setback = "setback".equals(line.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY));
      String lineName = line.getProperty(AssistantSiteCommands.LINE_NAME_PROPERTY);
      add(issues, counts, new Issue(setback ? Type.BEYOND_SETBACK : Type.BEYOND_PROPERTY_LINE,
          id(wall, "W", walls.indexOf(wall)) + " stands " + format(((Double)beyond [2]).floatValue()) + " cm beyond the "
          + (setback ? "setback line " : "property line ") + HomeItemIds.getId(line)
          + (lineName != null ? " \"" + lineName + "\"" : ""), wall, line));
    }
  }

  /**
   * Returns the issues left once the ones to ignore are taken away.
   * @param ignoredIds  the ids of items whose issues are ignored
   * @param ignoredTags the tags of furniture whose issues are ignored
   * @param minSeverity the least severity to keep: 0 for all, 1 for warnings and errors, 2 for errors
   */
  public static List<Issue> filter(List<Issue> issues, java.util.Collection<String> ignoredIds,
                                   java.util.Collection<String> ignoredTags, int minSeverity) {
    List<Issue> kept = new ArrayList<Issue>();
    for (Issue issue : issues) {
      if (issue.getType().getSeverity() < minSeverity) {
        continue;
      }
      boolean ignored = false;
      String id = HomeItemIds.getId(issue.getItem());
      for (String ignoredId : ignoredIds) {
        ignored |= id != null && id.equalsIgnoreCase(ignoredId.trim());
      }
      if (issue.getItem() instanceof HomePieceOfFurniture) {
        for (String tag : ignoredTags) {
          ignored |= HomeItemTags.hasTag((HomePieceOfFurniture)issue.getItem(), tag.trim());
        }
      }
      if (!ignored) {
        kept.add(issue);
      }
    }
    return kept;
  }

  /**
   * Returns the number of the severity named <code>text</code> (info, warning or error), or 0.
   */
  public static int parseSeverity(String text) {
    if (text != null) {
      String lower = text.trim().toLowerCase(java.util.Locale.ENGLISH);
      for (int i = 0; i < SEVERITIES.length; i++) {
        if (lower.startsWith(SEVERITIES [i]) || lower.equals(String.valueOf(i))) {
          return i;
        }
      }
    }
    return 0;
  }

  private static void checkWalls(Home home, List<Wall> walls, List<Issue> issues) {
    List<Wall> homeWalls = new ArrayList<Wall>(home.getWalls());
    for (int i = 0; i < walls.size(); i++) {
      Wall wall = walls.get(i);
      if (!homeWalls.contains(wall)) {
        continue;
      }
      if (wall.getLength() < TINY_WALL_LENGTH) {
        issues.add(new Issue(Type.TINY_WALL, id(walls.get(i), "W", i) + " is shorter than "
            + Math.round(TINY_WALL_LENGTH) + " cm", wall, null));
        continue;
      }
      for (int j = 0; j < i; j++) {
        Wall other = walls.get(j);
        if (homeWalls.contains(other) && other.getLevel() == wall.getLevel()
            && (near(wall.getXStart(), wall.getYStart(), other.getXStart(), other.getYStart(), 1)
                    && near(wall.getXEnd(), wall.getYEnd(), other.getXEnd(), other.getYEnd(), 1)
                || near(wall.getXStart(), wall.getYStart(), other.getXEnd(), other.getYEnd(), 1)
                    && near(wall.getXEnd(), wall.getYEnd(), other.getXStart(), other.getYStart(), 1))) {
          issues.add(new Issue(Type.DUPLICATE_WALL, id(walls.get(i), "W", i) + " is drawn over " + id(walls.get(j), "W", j),
              wall, other));
          break;
        }
      }
    }
    // Free wall ends close to a free end of another wall
    for (int i = 0; i < walls.size(); i++) {
      Wall wall = walls.get(i);
      if (!homeWalls.contains(wall) || wall.getLength() < TINY_WALL_LENGTH) {
        continue;
      }
      for (int end = 0; end < 2; end++) {
        if ((end == 0 ? wall.getWallAtStart() : wall.getWallAtEnd()) != null) {
          continue;
        }
        float x = end == 0 ? wall.getXStart() : wall.getXEnd();
        float y = end == 0 ? wall.getYStart() : wall.getYEnd();
        Wall closest = null;
        float closestDistance = WALL_GAP_TOLERANCE;
        int closestIndex = -1;
        // Report each pair once, from the wall with the highest id
        for (int j = 0; j < i; j++) {
          Wall other = walls.get(j);
          if (!homeWalls.contains(other) || other.getLevel() != wall.getLevel()
              || other.getLength() < TINY_WALL_LENGTH) {
            continue;
          }
          for (int otherEnd = 0; otherEnd < 2; otherEnd++) {
            if ((otherEnd == 0 ? other.getWallAtStart() : other.getWallAtEnd()) != null) {
              continue;
            }
            float distance = (float)Math.hypot(
                x - (otherEnd == 0 ? other.getXStart() : other.getXEnd()),
                y - (otherEnd == 0 ? other.getYStart() : other.getYEnd()));
            if (distance <= closestDistance) {
              closestDistance = distance;
              closest = other;
              closestIndex = j;
            }
          }
        }
        if (closest != null) {
          issues.add(new Issue(Type.WALL_GAP, id(walls.get(i), "W", i) + " and " + id(walls.get(closestIndex), "W", closestIndex)
              + (closestDistance < 0.01f
                  ? " meet but aren't joined"
                  : " end " + format(closestDistance) + " cm apart without being joined"),
              wall, closest));
        }
      }
    }
  }

  private static void checkRooms(Home home, List<Room> rooms, List<Issue> issues) {
    int unnamedCount = 0;
    for (int i = 0; i < rooms.size(); i++) {
      Room room = rooms.get(i);
      if (!home.getRooms().contains(room)) {
        continue;
      }
      if (room.getPointCount() < 3 || room.getArea() < 1) {
        issues.add(new Issue(Type.EMPTY_ROOM, id(rooms.get(i), "R", i) + " has no area", room, null));
      } else {
        if (isClosedOff(home, room)) {
          issues.add(new Issue(Type.ROOM_WITHOUT_DOOR, id(rooms.get(i), "R", i)
              + (room.getName() != null && room.getName().trim().length() > 0 ? " \"" + room.getName() + "\"" : "")
              + " has walls on every side and no door: nobody can enter it", room, null));
        }
        if (room.getName() == null || room.getName().trim().length() == 0) {
          if (++unnamedCount <= MAX_ISSUES_PER_TYPE) {
            issues.add(new Issue(Type.UNNAMED_ROOM, id(rooms.get(i), "R", i) + " has no name", room, null));
          }
        }
      }
    }
  }

  /**
   * Returns <code>true</code> if a wall runs along every side of
   * <code>room</code> and no door stands in or at the edge of the room.
   */
  private static boolean isClosedOff(Home home, Room room) {
    float [][] points = room.getPoints();
    for (int i = 0; i < points.length; i++) {
      float [] start = points [i];
      float [] end = points [(i + 1) % points.length];
      float middleX = (start [0] + end [0]) / 2;
      float middleY = (start [1] + end [1]) / 2;
      boolean wallAlongSide = false;
      for (Wall wall : home.getWalls()) {
        if (wall.getLevel() == room.getLevel() && wall.getLength() >= TINY_WALL_LENGTH
            && getDistanceToWall(middleX, middleY, wall) <= wall.getThickness() + 5) {
          wallAlongSide = true;
          break;
        }
      }
      if (!wallAlongSide) {
        // An open side
        return false;
      }
    }
    return !hasDoor(home.getFurniture(), room);
  }

  private static boolean hasDoor(List<HomePieceOfFurniture> furniture, Room room) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        if (hasDoor(((HomeFurnitureGroup)piece).getFurniture(), room)) {
          return true;
        }
      } else if (piece.isDoorOrWindow() && piece.getLevel() == room.getLevel()
          // A door rather than a window: it starts at the floor and is high enough to walk through
          && piece.getElevation() <= 30 && piece.getHeight() >= 150
          && room.containsPoint(piece.getX(), piece.getY(), 40)) {
        return true;
      }
    }
    return false;
  }

  private static void checkFurniture(Home home, List<HomePieceOfFurniture> furniture,
                                     List<Wall> walls, List<Room> rooms, List<Issue> issues) {
    List<Wall> homeWalls = new ArrayList<Wall>(home.getWalls());
    int [] counts = new int [Type.values().length];
    List<float [][]> terrainTriangles = null;
    for (int i = 0; i < furniture.size(); i++) {
      HomePieceOfFurniture piece = furniture.get(i);
      if (!home.getFurniture().contains(piece) || piece instanceof HomeFurnitureGroup) {
        continue;
      }
      String id = id(furniture.get(i), "F", i) + " \"" + (piece.getName() != null ? piece.getName() : "Unnamed") + "\"";
      Level level = piece.getLevel();
      boolean structure = piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) != null;
      // What stands on the ground outdoors or is a part of the building isn't furniture out of its place
      boolean placedOnPurpose = structure
          || "true".equals(piece.getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
      if (!placedOnPurpose && !piece.isDoorOrWindow()
          && (piece.getElevation() < -0.5f || level == null || level.getHeight() <= 0
              || piece.getElevation() + piece.getHeight() > level.getHeight() + 5)) {
        // What stands on the ground built around the home is outdoors, whatever was told about it
        if (terrainTriangles == null) {
          terrainTriangles = HomeTerrain.getTriangles(home);
        }
        if (!terrainTriangles.isEmpty()) {
          float bottom = piece.getElevation() + (level != null ? level.getElevation() : 0);
          Float ground = getHeight(terrainTriangles, piece.getX(), piece.getY());
          placedOnPurpose = ground != null && Math.abs(bottom - ground.floatValue()) <= 5;
        }
      }
      if (piece.getElevation() < -0.5f) {
        // A piece put under the floor on purpose (a sunken slab, the ground around a house) is no defect
        if (!placedOnPurpose && !"true".equals(piece.getProperty(AssistantCommandExecutor.BELOW_FLOOR_PROPERTY)))
        add(issues, counts, new Issue(Type.FURNITURE_BELOW_FLOOR, id + " is "
            + format(-piece.getElevation()) + " cm under the floor", piece, null));
      } else {
        float ceiling = level != null ? level.getHeight() : home.getWallHeight();
        if (!piece.isDoorOrWindow() && ceiling > 0
            && piece.getElevation() + piece.getHeight() > ceiling + 5
            && !"true".equals(piece.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY))
            && !placedOnPurpose
            // Stairs go through the ceiling to the next level
            && piece.getStaircaseCutOutShape() == null) {
          add(issues, counts, new Issue(Type.FURNITURE_ABOVE_CEILING, id + " reaches "
              + format(piece.getElevation() + piece.getHeight()) + " cm, above the ceiling at "
              + format(ceiling) + " cm", piece, null));
        }
      }
      if (piece.isDoorOrWindow() && structure) {
        // The window of a dormer stands in the roof, not in a wall
        continue;
      } else if (piece.isDoorOrWindow()) {
        Wall wall = getClosestWall(piece, homeWalls, DOOR_SNAP_DISTANCE);
        if (wall == null) {
          if (!homeWalls.isEmpty()) {
            add(issues, counts, new Issue(Type.DOOR_OFF_WALL, id + " isn't in a wall and no wall is within "
                + Math.round(DOOR_SNAP_DISTANCE) + " cm", piece, null));
          }
        } else if (getDistanceToWall(piece.getX(), piece.getY(), wall)
            > wall.getThickness() / 2 + piece.getDepth() / 2 + 5) {
          add(issues, counts, new Issue(Type.DOOR_OFF_WALL, id + " isn't in a wall; the closest is "
              + id(wall, "W", walls.indexOf(wall)) + ", " + format(getDistanceToWall(piece.getX(), piece.getY(), wall))
              + " cm away", piece, wall));
        } else if (piece.isVisible()) {
          Issue misaligned = HomeDefects.getMisalignedDoorIssue(piece, id, wall, id(wall, "W", walls.indexOf(wall)));
          if (misaligned != null) {
            add(issues, counts, misaligned);
          }
        }
      } else {
        // Same piece twice at the same place
        for (int j = 0; j < i; j++) {
          HomePieceOfFurniture other = furniture.get(j);
          if (home.getFurniture().contains(other) && !(other instanceof HomeFurnitureGroup)
              && other.getLevel() == level
              && near(piece.getX(), piece.getY(), other.getX(), other.getY(), 1)
              && Math.abs(piece.getElevation() - other.getElevation()) < 1
              && Math.abs(piece.getWidth() - other.getWidth()) < 0.5f
              && Math.abs(piece.getDepth() - other.getDepth()) < 0.5f
              && (piece.getName() == null ? other.getName() == null : piece.getName().equals(other.getName()))) {
            add(issues, counts, new Issue(Type.DUPLICATE_FURNITURE, id + " is a copy of " + id(other, "F", j)
                + " at the same place", piece, other));
            break;
          }
        }
        if (piece.isVisible()
            && !placedOnPurpose
            && !"true".equals(piece.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY))) {
          boolean roomAtLevel = false;
          boolean inRoom = false;
          for (Room room : home.getRooms()) {
            if (room.getLevel() == level) {
              roomAtLevel = true;
              if (room.containsPoint(piece.getX(), piece.getY(), 5)) {
                inRoom = true;
                break;
              }
            }
          }
          if (roomAtLevel && !inRoom) {
            add(issues, counts, new Issue(Type.FURNITURE_OUTSIDE_ROOMS, id + " is in no room", piece, null));
          }
        }
        Long modelSize = piece.getModelSize();
        if (modelSize != null && modelSize.longValue() >= HEAVY_MODEL_SIZE
            && !com.eteks.sweethome3d.model.ModelLOD.isReducedDetailDisplayed(piece, home)) {
          add(issues, counts, new Issue(Type.HEAVY_MODEL, id + " has a "
              + (modelSize.longValue() / 1024 / 1024) + " MB model displayed at full detail"
              + " (reduce_detail makes the 3D view faster)", piece, null));
        }
      }
    }
  }

  /**
   * Returns the id of <code>item</code>, or the one of its place in the home if it has none.
   */
  private static String id(Object item, String type, int index) {
    String id = HomeItemIds.getId(item);
    return id != null ? id : type + (index + 1);
  }

  static void add(List<Issue> issues, int [] counts, Issue issue) {
    if (++counts [issue.getType().ordinal()] <= MAX_ISSUES_PER_TYPE) {
      issues.add(issue);
    }
  }

  /**
   * Returns the wall at the level of <code>piece</code> the closest to its
   * center, or <code>null</code> if none is closer than <code>maxDistance</code>.
   */
  static Wall getClosestWall(HomePieceOfFurniture piece, List<Wall> walls, float maxDistance) {
    Wall closest = null;
    float closestDistance = maxDistance;
    for (Wall wall : walls) {
      if (wall.getLevel() == piece.getLevel() && wall.getLength() >= TINY_WALL_LENGTH) {
        float distance = getDistanceToWall(piece.getX(), piece.getY(), wall);
        if (distance <= closestDistance) {
          closestDistance = distance;
          closest = wall;
        }
      }
    }
    return closest;
  }

  /**
   * Returns the distance from a point to the middle line of <code>wall</code>.
   */
  static float getDistanceToWall(float x, float y, Wall wall) {
    float [] point = getClosestPointOnWall(x, y, wall);
    return (float)Math.hypot(x - point [0], y - point [1]);
  }

  /**
   * Returns the point of the middle line of <code>wall</code> the closest to a point.
   */
  static float [] getClosestPointOnWall(float x, float y, Wall wall) {
    float dx = wall.getXEnd() - wall.getXStart();
    float dy = wall.getYEnd() - wall.getYStart();
    float lengthSquare = dx * dx + dy * dy;
    float t = lengthSquare == 0 ? 0
        : ((x - wall.getXStart()) * dx + (y - wall.getYStart()) * dy) / lengthSquare;
    t = Math.max(0, Math.min(1, t));
    return new float [] {wall.getXStart() + t * dx, wall.getYStart() + t * dy};
  }

  private static boolean near(float x1, float y1, float x2, float y2, float tolerance) {
    return Math.abs(x1 - x2) <= tolerance && Math.abs(y1 - y2) <= tolerance;
  }

  private static String format(float value) {
    return value == Math.rint(value) ? String.valueOf((int)value) : String.valueOf(Math.round(value * 10) / 10f);
  }

  /**
   * Returns a text listing <code>issues</code>, or saying there's none.
   */
  public static String describe(List<Issue> issues) {
    return describe(issues, 0);
  }

  /**
   * Returns a text listing <code>issues</code>, the most serious first, or saying there's none.
   * @param ignoredCount the count of issues that were ignored on request
   */
  public static String describe(List<Issue> issues, int ignoredCount) {
    String ignored = ignoredCount > 0
        ? " (" + ignoredCount + (ignoredCount == 1 ? " issue" : " issues") + " ignored on request)" : "";
    if (issues.isEmpty()) {
      return "Home check: no issue found" + (ignored.length() > 0 ? ignored : "") + ".";
    }
    int fixable = 0;
    int [] counts = new int [SEVERITIES.length];
    for (Issue issue : issues) {
      counts [issue.getType().getSeverity()]++;
    }
    StringBuilder text = new StringBuilder("Home check: ").append(issues.size())
        .append(issues.size() == 1 ? " issue" : " issues").append(" (").append(counts [2])
        .append(counts [2] == 1 ? " error, " : " errors, ").append(counts [1])
        .append(counts [1] == 1 ? " warning, " : " warnings, ").append(counts [0]).append(" info)").append(ignored).append(":\n");
    for (int severity = SEVERITIES.length - 1; severity >= 0; severity--) {
      for (Issue issue : issues) {
        if (issue.getType().getSeverity() != severity) {
          continue;
        }
        text.append("- ").append(SEVERITIES [severity]).append(" [")
            .append(issue.getType().name().toLowerCase(java.util.Locale.ENGLISH)).append("] ").append(issue.getMessage());
        if (issue.isFixable()) {
          text.append(" (fixable)");
          fixable++;
        }
        text.append('\n');
      }
    }
    if (fixable > 0) {
      text.append(fixable).append(fixable == 1 ? " issue" : " issues")
          .append(" can be fixed with {\"action\":\"fix_home\"}.");
    }
    return text.toString().trim();
  }

  /**
   * Returns the height of the ground at a point, or <code>null</code> if no triangle is under it.
   */
  private static Float getHeight(List<float [][]> triangles, float x, float y) {
    for (float [][] triangle : triangles) {
      float [] a = triangle [0];
      float [] b = triangle [1];
      float [] c = triangle [2];
      if (x < Math.min(a [0], Math.min(b [0], c [0])) || x > Math.max(a [0], Math.max(b [0], c [0]))
          || y < Math.min(a [1], Math.min(b [1], c [1])) || y > Math.max(a [1], Math.max(b [1], c [1]))) {
        continue;
      }
      double determinant = (b [1] - c [1]) * (a [0] - c [0]) + (c [0] - b [0]) * (a [1] - c [1]);
      if (Math.abs(determinant) < 1E-9) {
        continue;
      }
      double weightA = ((b [1] - c [1]) * (x - c [0]) + (c [0] - b [0]) * (y - c [1])) / determinant;
      double weightB = ((c [1] - a [1]) * (x - c [0]) + (a [0] - c [0]) * (y - c [1])) / determinant;
      double weightC = 1 - weightA - weightB;
      if (weightA >= -1E-4 && weightB >= -1E-4 && weightC >= -1E-4) {
        return Float.valueOf((float)(weightA * a [2] + weightB * b [2] + weightC * c [2]));
      }
    }
    return null;
  }
}
