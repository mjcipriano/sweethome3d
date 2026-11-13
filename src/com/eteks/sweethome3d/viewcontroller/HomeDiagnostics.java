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
 * furniture under the floor or through the ceiling... Each {@link Issue issue}
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
    HEAVY_MODEL(false);

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
  }

  /**
   * An issue found in a home.
   */
  public static final class Issue {
    private final Type       type;
    private final String     message;
    private final Selectable item;
    private final Selectable otherItem;

    Issue(Type type, String message, Selectable item, Selectable otherItem) {
      this.type = type;
      this.message = message;
      this.item = item;
      this.otherItem = otherItem;
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
    return issues;
  }

  private static void checkWalls(Home home, List<Wall> walls, List<Issue> issues) {
    List<Wall> homeWalls = new ArrayList<Wall>(home.getWalls());
    for (int i = 0; i < walls.size(); i++) {
      Wall wall = walls.get(i);
      if (!homeWalls.contains(wall)) {
        continue;
      }
      if (wall.getLength() < TINY_WALL_LENGTH) {
        issues.add(new Issue(Type.TINY_WALL, "W" + (i + 1) + " is shorter than "
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
          issues.add(new Issue(Type.DUPLICATE_WALL, "W" + (i + 1) + " is drawn over W" + (j + 1),
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
          issues.add(new Issue(Type.WALL_GAP, "W" + (i + 1) + " and W" + (closestIndex + 1)
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
        issues.add(new Issue(Type.EMPTY_ROOM, "R" + (i + 1) + " has no area", room, null));
      } else {
        if (isClosedOff(home, room)) {
          issues.add(new Issue(Type.ROOM_WITHOUT_DOOR, "R" + (i + 1)
              + (room.getName() != null && room.getName().trim().length() > 0 ? " \"" + room.getName() + "\"" : "")
              + " has walls on every side and no door: nobody can enter it", room, null));
        }
        if (room.getName() == null || room.getName().trim().length() == 0) {
          if (++unnamedCount <= MAX_ISSUES_PER_TYPE) {
            issues.add(new Issue(Type.UNNAMED_ROOM, "R" + (i + 1) + " has no name", room, null));
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
    for (int i = 0; i < furniture.size(); i++) {
      HomePieceOfFurniture piece = furniture.get(i);
      if (!home.getFurniture().contains(piece) || piece instanceof HomeFurnitureGroup) {
        continue;
      }
      String id = "F" + (i + 1) + " \"" + (piece.getName() != null ? piece.getName() : "Unnamed") + "\"";
      Level level = piece.getLevel();
      if (piece.getElevation() < -0.5f) {
        add(issues, counts, new Issue(Type.FURNITURE_BELOW_FLOOR, id + " is "
            + format(-piece.getElevation()) + " cm under the floor", piece, null));
      } else {
        float ceiling = level != null ? level.getHeight() : home.getWallHeight();
        if (!piece.isDoorOrWindow() && ceiling > 0
            && piece.getElevation() + piece.getHeight() > ceiling + 5
            && !"true".equals(piece.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY))) {
          add(issues, counts, new Issue(Type.FURNITURE_ABOVE_CEILING, id + " reaches "
              + format(piece.getElevation() + piece.getHeight()) + " cm, above the ceiling at "
              + format(ceiling) + " cm", piece, null));
        }
      }
      if (piece.isDoorOrWindow()) {
        Wall wall = getClosestWall(piece, homeWalls, DOOR_SNAP_DISTANCE);
        if (wall == null) {
          if (!homeWalls.isEmpty()) {
            add(issues, counts, new Issue(Type.DOOR_OFF_WALL, id + " isn't in a wall and no wall is within "
                + Math.round(DOOR_SNAP_DISTANCE) + " cm", piece, null));
          }
        } else if (getDistanceToWall(piece.getX(), piece.getY(), wall)
            > wall.getThickness() / 2 + piece.getDepth() / 2 + 5) {
          add(issues, counts, new Issue(Type.DOOR_OFF_WALL, id + " isn't in a wall; the closest is W"
              + (walls.indexOf(wall) + 1) + ", " + format(getDistanceToWall(piece.getX(), piece.getY(), wall))
              + " cm away", piece, wall));
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
            add(issues, counts, new Issue(Type.DUPLICATE_FURNITURE, id + " is a copy of F" + (j + 1)
                + " at the same place", piece, other));
            break;
          }
        }
        if (piece.isVisible()
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
            && !com.eteks.sweethome3d.model.ModelLOD.isReducedDetailInView(piece)) {
          add(issues, counts, new Issue(Type.HEAVY_MODEL, id + " has a "
              + (modelSize.longValue() / 1024 / 1024) + " MB model displayed at full detail"
              + " (reduce_detail makes the 3D view faster)", piece, null));
        }
      }
    }
  }

  private static void add(List<Issue> issues, int [] counts, Issue issue) {
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
    if (issues.isEmpty()) {
      return "Home check: no issue found.";
    }
    int fixable = 0;
    StringBuilder text = new StringBuilder("Home check: ").append(issues.size())
        .append(issues.size() == 1 ? " issue" : " issues").append(":\n");
    for (Issue issue : issues) {
      text.append("- [").append(issue.getType().name().toLowerCase(java.util.Locale.ENGLISH)).append("] ")
          .append(issue.getMessage());
      if (issue.getType().isFixable()
          && (issue.getType() != Type.DOOR_OFF_WALL || issue.getOtherItem() != null)) {
        text.append(" (fixable)");
        fixable++;
      }
      text.append('\n');
    }
    if (fixable > 0) {
      text.append(fixable).append(fixable == 1 ? " issue" : " issues")
          .append(" can be fixed with {\"action\":\"fix_home\"}.");
    }
    return text.toString().trim();
  }
}
