/*
 * HomeDefects.java 25 nov. 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.viewcontroller.HomeDiagnostics.Issue;
import com.eteks.sweethome3d.viewcontroller.HomeDiagnostics.Type;

/**
 * The defects of a home that show in 3D rather than in its plan, found by {@link HomeDiagnostics}:
 * a roof that comes into a room, walls drawn over each other, doors that aren't lined up
 * with their wall, walls that stick out of the roof, rooms of two stories that some walls
 * don't close, ceilings above the roof, and the finishes of the sides of the walls that
 * don't match what they face. It also lists the items that cross another item.
 * <br>The roofs are read from what the home stores (the description of each roof kept by
 * its piece, through {@link HomeRoofs}), not from the commands that build them.
 * @author Sweet Home 3D
 */
final class HomeDefects {
  /** The property of a room that gives the height of its ceiling above its floor, in cm. */
  static final String ROOM_CEILING_HEIGHT_PROPERTY = "com.eteks.sweethome3d.ceilingHeight";

  /** What a roof must come under a ceiling to be seen in the room. */
  private static final float ROOF_IN_ROOM_MARGIN = 8;
  /** The distance from the edge of a roof where its gutters and downspouts hang. */
  private static final float GUTTER_WIDTH = 16;
  /** A ceiling and a roof closer than this show through each other. */
  private static final float COPLANAR_DISTANCE = 3;
  /** A wall that passes the roof by more than this belongs to a taller part of the building. */
  private static final float MAX_WALL_EXCESS = 120;
  /** Walls whose directions differ by less than this are taken as parallel. */
  private static final double PARALLEL_SINE = Math.sin(Math.toRadians(10));
  /** Two walls drawn over each other for more than this length are reported. */
  private static final float MIN_WALL_OVERLAP = 10;
  /** The share of the shortest of two walls drawn over each other from which one of them is deleted. */
  private static final float DUPLICATE_OVERLAP_SHARE = 0.8f;
  /** The angle between a door and its wall from which it's reported, in degrees. */
  static final float DOOR_ANGLE_TOLERANCE = 1;
  /** The least height of a room above its level to be a room of two stories. */
  private static final float TWO_STORY_MARGIN = 50;

  /**
   * The words that tell a texture made for the outside of a building, searched in its name and in
   * its catalog id written in lower case. This list is the only place where a finish is said to
   * be an outside one; every other finish, a plain color included, is taken as an inside one.
   */
  static final String [] OUTSIDE_MATERIAL_WORDS = {"siding", "clapboard", "weatherboard", "cladding", "brick", "shingle",
      "stucco", "roughcast", "render", "facade", "façade", "exterior", "stone wall", "stonewall", "log wall", "bardage",
      "crepi", "crépi", "brique"};

  private HomeDefects() {
    // Utility class
  }

  // ---- Heights ----

  static float getElevation(Level level) {
    return level != null ? level.getElevation() : 0;
  }

  static float getLevelHeight(Home home, Level level) {
    return level != null && level.getHeight() > 0 ? level.getHeight() : home.getWallHeight();
  }

  /**
   * Returns the height of a wall at a place of its length between 0 and 1.
   */
  static float getWallHeight(Home home, Wall wall, float t) {
    float height = wall.getHeight() != null ? wall.getHeight().floatValue() : home.getWallHeight();
    float heightAtEnd = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd().floatValue() : height;
    return height + (heightAtEnd - height) * t;
  }

  /**
   * Returns the slabs of the home that may be the ceiling of a room: the ones built by
   * <code>add_slab</code> at 150 cm or more above the floor of their level.
   */
  private static List<HomePieceOfFurniture> getCeilingSlabs(Home home) {
    List<HomePieceOfFurniture> slabs = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (piece.isVisible() && "slab".equals(piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY))
          && piece.getProperty(AssistantShapeCommands.ROOM_SLAB_PROPERTY) == null
          && !HomeTerrain.isTerrain(piece)
          && piece.getElevation() >= 150) {
        slabs.add(piece);
      }
    }
    return slabs;
  }

  /**
   * Returns the elevation of the ceiling of a room, or NaN if it has none that is known: the height
   * the room was given, the top of its level if it shows its ceiling, otherwise the lowest slab above it.
   */
  static double getCeilingElevation(Home home, Room room, List<HomePieceOfFurniture> slabs) {
    float floor = getElevation(room.getLevel());
    String ceilingHeight = room.getProperty(ROOM_CEILING_HEIGHT_PROPERTY);
    if (ceilingHeight != null) {
      try {
        double height = Double.parseDouble(ceilingHeight);
        if (height > 0) {
          return floor + height;
        }
      } catch (NumberFormatException ex) {
        // The ceiling of the level
      }
    }
    if (room.isCeilingVisible()) {
      return floor + getLevelHeight(home, room.getLevel());
    }
    float [] center = getInsidePoint(room);
    double ceiling = Double.NaN;
    if (center != null) {
      for (HomePieceOfFurniture slab : slabs) {
        float bottom = slab.getGroundElevation();
        if (bottom >= floor + 150 && !(bottom >= ceiling) && contains(slab.getPoints(), center [0], center [1])) {
          ceiling = bottom;
        }
      }
    }
    return ceiling;
  }

  /**
   * Returns a point inside a room, or <code>null</code>.
   */
  private static float [] getInsidePoint(Room room) {
    float [][] points = room.getPoints();
    float x = 0;
    float y = 0;
    for (float [] point : points) {
      x += point [0] / points.length;
      y += point [1] / points.length;
    }
    if (room.containsPoint(x, y, 0)) {
      return new float [] {x, y};
    }
    // A room in L: a point near the middle of one of its sides
    for (int i = 0; i < points.length; i++) {
      float [] next = points [(i + 1) % points.length];
      float middleX = (points [i][0] + next [0]) / 2;
      float middleY = (points [i][1] + next [1]) / 2;
      double length = Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
      if (length > 1) {
        for (int side = -1; side <= 1; side += 2) {
          float px = middleX - side * (float)((next [1] - points [i][1]) / length) * 20;
          float py = middleY + side * (float)((next [0] - points [i][0]) / length) * 20;
          if (room.containsPoint(px, py, 0)) {
            return new float [] {px, py};
          }
        }
      }
    }
    return null;
  }

  static boolean contains(float [][] polygon, float x, float y) {
    boolean inside = false;
    for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      if ((polygon [i][1] > y) != (polygon [j][1] > y)
          && x < (polygon [j][0] - polygon [i][0]) * (y - polygon [i][1]) / (polygon [j][1] - polygon [i][1]) + polygon [i][0]) {
        inside = !inside;
      }
    }
    return inside;
  }

  private static Rectangle2D getBounds(float [][] points) {
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
    return new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
  }

  private static Area getArea(float [][] points) {
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

  /**
   * Returns the area of a shape made of straight lines.
   */
  private static double getSurface(Area area) {
    double surface = 0;
    float [] coordinates = new float [6];
    float startX = 0;
    float startY = 0;
    float x = 0;
    float y = 0;
    for (java.awt.geom.PathIterator it = area.getPathIterator(null, 0.5); !it.isDone(); it.next()) {
      switch (it.currentSegment(coordinates)) {
        case java.awt.geom.PathIterator.SEG_MOVETO :
          startX = x = coordinates [0];
          startY = y = coordinates [1];
          break;
        case java.awt.geom.PathIterator.SEG_LINETO :
          surface += (double)x * coordinates [1] - (double)coordinates [0] * y;
          x = coordinates [0];
          y = coordinates [1];
          break;
        case java.awt.geom.PathIterator.SEG_CLOSE :
          surface += (double)x * startY - (double)startX * y;
          x = startX;
          y = startY;
          break;
      }
    }
    return Math.abs(surface) / 2;
  }

  private static String name(String prefix, String name) {
    return name != null && name.trim().length() > 0 ? prefix + " \"" + name.trim() + "\"" : prefix;
  }

  private static String id(Object item, String type, int index) {
    String id = HomeItemIds.getId(item);
    return id != null ? id : type + (index + 1);
  }

  static String format(double value) {
    return value == Math.rint(value) ? String.valueOf((long)value) : String.valueOf(Math.round(value * 10) / 10f);
  }

  /**
   * Returns the side of a shape where a point stands, as a word of the compass of the plan
   * (north at the top of the plan).
   */
  private static String getSide(Rectangle2D bounds, double x, double y) {
    double dx = (x - bounds.getCenterX()) / Math.max(1, bounds.getWidth());
    double dy = (y - bounds.getCenterY()) / Math.max(1, bounds.getHeight());
    return Math.abs(dx) >= Math.abs(dy) ? (dx >= 0 ? "east" : "west") : (dy >= 0 ? "south" : "north");
  }

  // ---- Roofs, rooms and ceilings ----

  /**
   * A roof with what its description tells of its edges.
   */
  private static final class RoofData {
    final HomeRoofs.Roof roof;
    final Area           footprint;
    final Area           gutters;
    final Rectangle2D    bounds;
    final double [][]    points;
    final double         eave;

    RoofData(HomeRoofs.Roof roof) {
      this.roof = roof;
      this.footprint = roof.model.getGeometry().getFootprint();
      this.eave = roof.elevation + roof.model.getEaveHeight();
      double [][] points = null;
      boolean gutters = false;
      try {
        Object spec = JSONParser.parse(roof.piece.getProperty(AssistantShapeCommands.ROOF_SPEC_PROPERTY));
        if (spec instanceof Map) {
          gutters = Boolean.TRUE.equals(((Map<?, ?>)spec).get("gutters"));
          Object value = ((Map<?, ?>)spec).get("points");
          if (value instanceof List) {
            List<double []> list = new ArrayList<double []>();
            for (Object point : (List<?>)value) {
              if (point instanceof List && ((List<?>)point).size() >= 2
                  && ((List<?>)point).get(0) instanceof Number && ((List<?>)point).get(1) instanceof Number) {
                list.add(new double [] {((Number)((List<?>)point).get(0)).doubleValue(),
                    ((Number)((List<?>)point).get(1)).doubleValue()});
              }
            }
            points = list.toArray(new double [list.size()][]);
          }
        }
      } catch (IllegalArgumentException ex) {
        // A roof without edges to name
      }
      this.points = points;
      if (gutters && !this.footprint.isEmpty()) {
        // Gutters and downspouts hang along the edges, out of the roof
        Area around = new Area(new BasicStroke(2 * GUTTER_WIDTH, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
            .createStrokedShape(this.footprint));
        around.subtract(this.footprint);
        this.gutters = around;
      } else {
        this.gutters = null;
      }
      Rectangle2D bounds = this.footprint.getBounds2D();
      this.bounds = new Rectangle2D.Double(bounds.getX() - GUTTER_WIDTH, bounds.getY() - GUTTER_WIDTH,
          bounds.getWidth() + 2 * GUTTER_WIDTH, bounds.getHeight() + 2 * GUTTER_WIDTH);
    }

    /** Returns the elevation of the top of the roof at a point, or NaN. */
    double getTop(double x, double y) {
      return this.roof.model.getGeometry().getHeight(x, y, true) + this.roof.elevation;
    }

    /** Returns the elevation of the underside of the roof at a point, or NaN. */
    double getUnderside(double x, double y) {
      return this.roof.model.getGeometry().getCeilingHeight(x, y) + this.roof.elevation;
    }

    /** Returns the index of the edge of the roof the closest to a point, or -1. */
    int getClosestEdge(double x, double y) {
      int closest = -1;
      double closestDistance = Double.MAX_VALUE;
      if (this.points != null) {
        for (int i = 0; i < this.points.length; i++) {
          double [] next = this.points [(i + 1) % this.points.length];
          double distance = java.awt.geom.Line2D.ptSegDist(this.points [i][0], this.points [i][1], next [0], next [1], x, y);
          if (distance < closestDistance) {
            closestDistance = distance;
            closest = i;
          }
        }
      }
      return closest;
    }
  }

  private static List<RoofData> getRoofs(Home home) {
    List<RoofData> roofs = new ArrayList<RoofData>();
    for (HomeRoofs.Roof roof : HomeRoofs.get(home)) {
      try {
        roofs.add(new RoofData(roof));
      } catch (RuntimeException ex) {
        // A roof that can't be measured
      }
    }
    return roofs;
  }

  /**
   * Returns the points of a shape where a roof is looked for: a grid inside it, then points
   * along its sides, 4 and 14 cm inside, where an overhang or a gutter comes in.
   * @return the points {x, y, 1 for a point of the grid and 0 for a point along a side}
   */
  private static List<float []> getSamples(float [][] polygon, Rectangle2D bounds) {
    List<float []> samples = new ArrayList<float []>();
    double step = Math.max(10, Math.max(bounds.getWidth(), bounds.getHeight()) / 36);
    for (double x = bounds.getMinX() + step / 2; x < bounds.getMaxX(); x += step) {
      for (double y = bounds.getMinY() + step / 2; y < bounds.getMaxY(); y += step) {
        if (contains(polygon, (float)x, (float)y)) {
          samples.add(new float [] {(float)x, (float)y, 1});
        }
      }
    }
    for (int i = 0; i < polygon.length; i++) {
      float [] point = polygon [i];
      float [] next = polygon [(i + 1) % polygon.length];
      double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
      if (length < 20) {
        continue;
      }
      int count = (int)Math.min(60, Math.max(2, length / 12));
      for (int inset = 4; inset <= 14; inset += 10) {
        for (int j = 0; j < count; j++) {
          double t = (j + 0.5) / count;
          for (int side = -1; side <= 1; side += 2) {
            float x = (float)(point [0] + (next [0] - point [0]) * t - side * (next [1] - point [1]) / length * inset);
            float y = (float)(point [1] + (next [1] - point [1]) * t + side * (next [0] - point [0]) / length * inset);
            if (contains(polygon, x, y)) {
              samples.add(new float [] {x, y, 0});
              break;
            }
          }
        }
      }
    }
    return samples;
  }

  /**
   * Returns the distance from a point to the closest side of a polygon.
   */
  private static double getDistanceToSides(float [][] polygon, double x, double y) {
    double distance = Double.MAX_VALUE;
    for (int i = 0; i < polygon.length; i++) {
      float [] next = polygon [(i + 1) % polygon.length];
      distance = Math.min(distance, java.awt.geom.Line2D.ptSegDist(polygon [i][0], polygon [i][1], next [0], next [1], x, y));
    }
    return distance;
  }

  /**
   * Reports the roofs that come into a room under its ceiling, the ceilings of rooms and the
   * slabs that pass the roof above them, and the roofs at the height of a ceiling.
   */
  static void checkRoofsAndCeilings(Home home, List<HomePieceOfFurniture> furniture, List<Room> rooms,
                                    List<Issue> issues, int [] counts) {
    List<RoofData> roofs = getRoofs(home);
    if (roofs.isEmpty()) {
      return;
    }
    List<HomePieceOfFurniture> slabs = getCeilingSlabs(home);
    for (int i = 0; i < rooms.size(); i++) {
      Room room = rooms.get(i);
      if (!home.getRooms().contains(room) || room.getPointCount() < 3 || room.getArea() < 1) {
        continue;
      }
      double ceiling = getCeilingElevation(home, room, slabs);
      if (Double.isNaN(ceiling)) {
        continue;
      }
      boolean ownCeiling = room.isCeilingVisible() || room.getProperty(ROOM_CEILING_HEIGHT_PROPERTY) != null;
      String label = name(id(room, "R", i), room.getName());
      checkRoofsUnder(home, roofs, room.getPoints(), getElevation(room.getLevel()), ceiling, room, label,
          ownCeiling, true, furniture, issues, counts);
    }
    for (HomePieceOfFurniture slab : slabs) {
      checkRoofsUnder(home, roofs, slab.getPoints(), Double.NaN, slab.getGroundElevation(), slab,
          name(id(slab, "F", furniture.indexOf(slab)), slab.getName()), true, false, furniture, issues, counts);
    }
  }

  /**
   * Compares a ceiling with the roofs that cover a part of it.
   * @param floor  the elevation of the floor under the ceiling, or NaN for a slab
   * @param ownCeiling <code>true</code> if the ceiling is a surface that may show above a roof
   * @param isRoom <code>true</code> if the shape is a room a roof may come into
   */
  private static void checkRoofsUnder(Home home, List<RoofData> roofs, float [][] polygon, double floor, double ceiling,
                                      Selectable item, String label, boolean ownCeiling, boolean isRoom,
                                      List<HomePieceOfFurniture> furniture, List<Issue> issues, int [] counts) {
    Rectangle2D bounds = getBounds(polygon);
    List<float []> samples = null;
    for (RoofData roof : roofs) {
      if (!roof.bounds.intersects(bounds) || roof.roof.piece == item) {
        continue;
      }
      if (samples == null) {
        samples = getSamples(polygon, bounds);
      }
      int gridCount = 0;
      int covered = 0;
      int under = 0;
      int underOnGrid = 0;
      int coplanar = 0;
      int gutter = 0;
      double depth = 0;
      double lowest = Double.MAX_VALUE;
      double sumX = 0;
      double sumY = 0;
      for (float [] sample : samples) {
        boolean grid = sample [2] == 1;
        if (grid) {
          gridCount++;
        }
        double top = roof.getTop(sample [0], sample [1]);
        if (!Double.isNaN(top)) {
          if (grid) {
            covered++;
          }
          double underside = roof.getUnderside(sample [0], sample [1]);
          if (grid && (Math.abs(top - ceiling) <= COPLANAR_DISTANCE
                       || !Double.isNaN(underside) && Math.abs(underside - ceiling) <= COPLANAR_DISTANCE)) {
            coplanar++;
          }
          if (top < ceiling - ROOF_IN_ROOM_MARGIN && (Double.isNaN(floor) || top > floor + ROOF_IN_ROOM_MARGIN)) {
            under++;
            if (grid) {
              underOnGrid++;
            }
            depth = Math.max(depth, getDistanceToSides(polygon, sample [0], sample [1]));
            lowest = Math.min(lowest, top);
            sumX += sample [0];
            sumY += sample [1];
          }
        } else if (isRoom && roof.gutters != null && roof.eave < ceiling - ROOF_IN_ROOM_MARGIN
                   && roof.eave > floor + ROOF_IN_ROOM_MARGIN && roof.gutters.contains(sample [0], sample [1])) {
          gutter++;
          depth = Math.max(depth, getDistanceToSides(polygon, sample [0], sample [1]));
          lowest = Math.min(lowest, roof.eave);
          sumX += sample [0];
          sumY += sample [1];
        }
      }
      if (gridCount == 0) {
        continue;
      }
      String roofLabel = name(id(roof.roof.piece, "F", furniture.indexOf(roof.roof.piece)), roof.roof.piece.getName());
      boolean roofOverIt = covered >= 0.5 * gridCount;
      if (under + gutter >= 2) {
        double x = sumX / (under + gutter);
        double y = sumY / (under + gutter);
        if (roofOverIt && ownCeiling && underOnGrid >= 2) {
          // The roof of this room or of this slab: its ceiling comes out of it
          HomeDiagnostics.add(issues, counts, new Issue(Type.CEILING_ABOVE_ROOF, "The ceiling of " + label + ", "
              + (Double.isNaN(floor) ? "at " + format(ceiling) + " cm" : format(ceiling - floor) + " cm above its floor")
              + ", passes the roof " + roofLabel + " on its " + getSide(bounds, x, y) + " side, where the top of the roof is "
              + format(ceiling - lowest) + " cm lower: it shows from outside. Lower the ceiling"
              + (isRoom ? " of the room, or hide it with modify_room and let the roof close the room" : ""), item, roof.roof.piece));
        } else if (isRoom && !roofOverIt) {
          int edge = roof.getClosestEdge(x, y);
          HomeDiagnostics.add(issues, counts, new Issue(Type.ROOF_IN_ROOM, "The roof " + roofLabel + " comes into " + label
              + " on its " + getSide(bounds, x, y) + " side, " + format(Math.max(1, Math.round(depth))) + " cm into the room"
              + " and " + format(Math.round(lowest - floor)) + " cm above its floor, under its ceiling at "
              + format(Math.round(ceiling - floor)) + " cm"
              + (under == 0 ? " (its gutter and its downspouts)" : gutter > 0 ? " (with its gutter)" : "")
              + (edge >= 0 ? ". Its edge " + edge + " is the one to stop at the wall of the room (modify_roof)" : ""),
              roof.roof.piece, item));
        }
      }
      if (ownCeiling && coplanar >= Math.max(2, 0.25 * gridCount)) {
        HomeDiagnostics.add(issues, counts, new Issue(Type.ROOF_AT_CEILING, "The roof " + roofLabel
            + " is within " + format(COPLANAR_DISTANCE) + " cm of the ceiling of " + label
            + " over a quarter of it or more: they show through each other. Move one of them by a few centimeters",
            roof.roof.piece, item));
      }
    }
  }

  // ---- Walls ----

  /**
   * Reports the walls drawn over each other along a part of their length.
   */
  static void checkOverlappingWalls(Home home, List<Wall> walls, List<Issue> issues, int [] counts) {
    List<Wall> homeWalls = new ArrayList<Wall>(home.getWalls());
    for (int i = 0; i < walls.size(); i++) {
      Wall wall = walls.get(i);
      if (!homeWalls.contains(wall) || !isStraight(wall)) {
        continue;
      }
      for (int j = 0; j < i; j++) {
        Wall other = walls.get(j);
        if (!homeWalls.contains(other) || other.getLevel() != wall.getLevel() || !isStraight(other)) {
          continue;
        }
        double [] overlap = getOverlap(wall, other);
        if (overlap == null || isSamePlace(wall, other)) {
          continue;
        }
        float height = Math.max(getWallHeight(home, wall, 0), getWallHeight(home, wall, 1));
        float otherHeight = Math.max(getWallHeight(home, other, 0), getWallHeight(home, other, 1));
        // The taller wall is kept, or the longer one
        boolean keepOther = otherHeight > height + 0.5f
            || Math.abs(otherHeight - height) <= 0.5f && other.getLength() >= wall.getLength();
        Wall deleted = keepOther ? wall : other;
        Wall kept = keepOther ? other : wall;
        boolean fixable = overlap [0] >= DUPLICATE_OVERLAP_SHARE * Math.min(wall.getLength(), other.getLength());
        HomeDiagnostics.add(issues, counts, new Issue(Type.OVERLAPPING_WALL, id(deleted, "W", walls.indexOf(deleted))
            + " and " + id(kept, "W", walls.indexOf(kept)) + " are drawn over each other for " + format(Math.round(overlap [0]))
            + " cm, their middle lines " + format(overlap [1]) + " cm apart at most"
            + (Math.abs(height - otherHeight) > 0.5f
                ? ", with different heights (" + format(keepOther ? height : otherHeight) + " and "
                    + format(keepOther ? otherHeight : height) + " cm)" : "")
            + ": their faces show through each other"
            + (fixable ? "" : ". Shorten one of them where the other one stands"), deleted, kept, fixable, 0));
      }
    }
  }

  static boolean isStraight(Wall wall) {
    return (wall.getArcExtent() == null || wall.getArcExtent().floatValue() == 0)
        && wall.getLength() >= HomeDiagnostics.TINY_WALL_LENGTH;
  }

  private static boolean isSamePlace(Wall wall, Wall other) {
    // Reported as a duplicate wall
    return near(wall.getXStart(), wall.getYStart(), other.getXStart(), other.getYStart())
            && near(wall.getXEnd(), wall.getYEnd(), other.getXEnd(), other.getYEnd())
        || near(wall.getXStart(), wall.getYStart(), other.getXEnd(), other.getYEnd())
            && near(wall.getXEnd(), wall.getYEnd(), other.getXStart(), other.getYStart());
  }

  private static boolean near(float x1, float y1, float x2, float y2) {
    return Math.abs(x1 - x2) <= 1 && Math.abs(y1 - y2) <= 1;
  }

  /**
   * Returns {the length along which two walls are drawn over each other, the largest distance
   * between their middle lines there}, or <code>null</code> if they aren't.
   */
  static double [] getOverlap(Wall wall, Wall other) {
    double length = wall.getLength();
    double ux = (wall.getXEnd() - wall.getXStart()) / length;
    double uy = (wall.getYEnd() - wall.getYStart()) / length;
    double otherLength = other.getLength();
    double vx = (other.getXEnd() - other.getXStart()) / otherLength;
    double vy = (other.getYEnd() - other.getYStart()) / otherLength;
    if (Math.abs(ux * vy - uy * vx) > PARALLEL_SINE) {
      return null;
    }
    double s1 = (other.getXStart() - wall.getXStart()) * ux + (other.getYStart() - wall.getYStart()) * uy;
    double s2 = (other.getXEnd() - wall.getXStart()) * ux + (other.getYEnd() - wall.getYStart()) * uy;
    double from = Math.max(0, Math.min(s1, s2));
    double to = Math.min(length, Math.max(s1, s2));
    if (to - from <= MIN_WALL_OVERLAP) {
      return null;
    }
    // The distance from the middle line of the wall to the one of the other wall, at both ends of the common part
    double distance = 0;
    for (double s : new double [] {from, to}) {
      double x = wall.getXStart() + ux * s;
      double y = wall.getYStart() + uy * s;
      distance = Math.max(distance, Math.abs((x - other.getXStart()) * -vy + (y - other.getYStart()) * vx));
    }
    return distance <= Math.max(wall.getThickness(), other.getThickness()) / 2 ? new double [] {to - from, distance} : null;
  }

  /**
   * Reports the walls whose top passes the roof that covers them.
   */
  static void checkWallsAboveRoof(Home home, List<Wall> walls, List<Issue> issues, int [] counts) {
    List<RoofData> roofs = getRoofs(home);
    if (roofs.isEmpty()) {
      return;
    }
    List<Wall> homeWalls = new ArrayList<Wall>(home.getWalls());
    for (int i = 0; i < walls.size(); i++) {
      Wall wall = walls.get(i);
      if (!homeWalls.contains(wall) || !isStraight(wall)) {
        continue;
      }
      Object [] excess = getExcessAboveRoof(home, wall, roofs);
      if (excess != null) {
        RoofData roof = (RoofData)excess [1];
        double value = ((Double)excess [0]).doubleValue();
        HomeDiagnostics.add(issues, counts, new Issue(Type.WALL_ABOVE_ROOF, id(wall, "W", i) + " sticks out of the roof "
            + name(HomeItemIds.getId(roof.roof.piece) != null ? HomeItemIds.getId(roof.roof.piece) : "above it",
                roof.roof.piece.getName())
            + " by " + format(value) + " cm: its top shows above the roof", wall, roof.roof.piece));
      }
    }
  }

  /**
   * Returns {how much a wall passes the top of the roof over it, that roof}, or <code>null</code>
   * if it doesn't or if it belongs to a taller part of the building.
   */
  private static Object [] getExcessAboveRoof(Home home, Wall wall, List<RoofData> roofs) {
    float base = getElevation(wall.getLevel());
    double excess = 0;
    RoofData excessRoof = null;
    for (int sample = 0; sample < 9; sample++) {
      float t = 0.04f + sample * 0.115f;
      double x = wall.getXStart() + (wall.getXEnd() - wall.getXStart()) * t;
      double y = wall.getYStart() + (wall.getYEnd() - wall.getYStart()) * t;
      double roofTop = Double.NaN;
      RoofData topRoof = null;
      for (RoofData roof : roofs) {
        if (roof.bounds.contains(x, y)) {
          double top = roof.getTop(x, y);
          if (!Double.isNaN(top) && !(top <= roofTop)) {
            roofTop = top;
            topRoof = roof;
          }
        }
      }
      if (topRoof == null || base > roofTop - 50) {
        // No roof over this point, or a wall that stands on a floor above the roof
        continue;
      }
      double sampleExcess = base + getWallHeight(home, wall, t) - roofTop;
      if (sampleExcess > MAX_WALL_EXCESS) {
        // The wall of a taller part of the building, which a lower roof runs into
        return null;
      }
      if (sampleExcess > excess) {
        excess = sampleExcess;
        excessRoof = topRoof;
      }
    }
    return excess > 2 ? new Object [] {Double.valueOf(excess), excessRoof} : null;
  }

  /**
   * Reports the rooms higher than their level whose outline isn't closed by walls up to their ceiling.
   */
  static void checkTwoStoryRooms(Home home, List<Wall> walls, List<Room> rooms, List<Issue> issues, int [] counts) {
    List<HomePieceOfFurniture> slabs = null;
    List<Wall> homeWalls = new ArrayList<Wall>(home.getWalls());
    for (int i = 0; i < rooms.size(); i++) {
      Room room = rooms.get(i);
      if (!home.getRooms().contains(room) || room.getPointCount() < 3 || room.getArea() < 1
          || room.isCeilingVisible() && room.getProperty(ROOM_CEILING_HEIGHT_PROPERTY) == null) {
        continue;
      }
      if (slabs == null) {
        slabs = getCeilingSlabs(home);
      }
      double ceiling = getCeilingElevation(home, room, slabs);
      float floor = getElevation(room.getLevel());
      if (Double.isNaN(ceiling) || ceiling < floor + getLevelHeight(home, room.getLevel()) + TWO_STORY_MARGIN) {
        continue;
      }
      Rectangle2D bounds = getBounds(room.getPoints());
      Map<Wall, double []> gaps = new LinkedHashMap<Wall, double []>();
      float [][] points = room.getPoints();
      for (int side = 0; side < points.length; side++) {
        float [] start = points [side];
        float [] end = points [(side + 1) % points.length];
        if (Math.hypot(end [0] - start [0], end [1] - start [1]) < 30) {
          continue;
        }
        // The walls of the level of the room along this side, then the ones above that continue them
        Wall lowest = null;
        double top = Double.NaN;
        for (Wall wall : homeWalls) {
          if (wall.getLevel() == room.getLevel() && isAlong(wall, start, end)) {
            double wallTop = floor + Math.min(getWallHeight(home, wall, 0), getWallHeight(home, wall, 1));
            if (!(wallTop <= top)) {
              top = wallTop;
              lowest = wall;
            }
          }
        }
        if (lowest == null) {
          // A side open to the next room
          continue;
        }
        boolean raised = true;
        while (raised && top < ceiling - 5) {
          raised = false;
          for (Wall wall : homeWalls) {
            float base = getElevation(wall.getLevel());
            if (wall.getLevel() != room.getLevel() && base > floor && base <= top + 45 && isAlong(wall, start, end)) {
              double wallTop = base + Math.min(getWallHeight(home, wall, 0), getWallHeight(home, wall, 1));
              if (wallTop > top + 1) {
                top = wallTop;
                raised = true;
              }
            }
          }
        }
        if (top < ceiling - 5) {
          double [] gap = gaps.get(lowest);
          if (gap == null || gap [0] > top) {
            gaps.put(lowest, new double [] {top, (start [0] + end [0]) / 2, (start [1] + end [1]) / 2});
          }
        }
      }
      for (Map.Entry<Wall, double []> gap : gaps.entrySet()) {
        Wall wall = gap.getKey();
        HomeDiagnostics.add(issues, counts, new Issue(Type.OPEN_TWO_STORY_ROOM, name(id(room, "R", i), room.getName())
            + " is " + format(Math.round(ceiling - floor)) + " cm high, but on its "
            + getSide(bounds, gap.getValue() [1], gap.getValue() [2]) + " side " + id(wall, "W", walls.indexOf(wall))
            + " and the walls above it stop at " + format(Math.round(gap.getValue() [0] - floor))
            + " cm: the room is open above them", wall, room, true, (float)(ceiling - floor)));
      }
    }
  }

  /**
   * Returns <code>true</code> if a wall runs along the segment between two points, over half of it at least.
   */
  private static boolean isAlong(Wall wall, float [] start, float [] end) {
    if (!isStraight(wall)) {
      return false;
    }
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    double ux = (end [0] - start [0]) / length;
    double uy = (end [1] - start [1]) / length;
    double vx = (wall.getXEnd() - wall.getXStart()) / wall.getLength();
    double vy = (wall.getYEnd() - wall.getYStart()) / wall.getLength();
    if (Math.abs(ux * vy - uy * vx) > PARALLEL_SINE) {
      return false;
    }
    double s1 = (wall.getXStart() - start [0]) * ux + (wall.getYStart() - start [1]) * uy;
    double s2 = (wall.getXEnd() - start [0]) * ux + (wall.getYEnd() - start [1]) * uy;
    double from = Math.max(0, Math.min(s1, s2));
    double to = Math.min(length, Math.max(s1, s2));
    if (to - from < 0.5 * length) {
      return false;
    }
    double middle = (from + to) / 2;
    return java.awt.geom.Line2D.ptSegDist(wall.getXStart(), wall.getYStart(), wall.getXEnd(), wall.getYEnd(),
        start [0] + ux * middle, start [1] + uy * middle) <= wall.getThickness() / 2 + 8;
  }

  // ---- Doors and windows ----

  /**
   * Returns the issue of a door or a window that stands in <code>wall</code> without being
   * lined up with it, or <code>null</code>: its angle differs from the one of the wall by more than
   * {@link #DOOR_ANGLE_TOLERANCE} degree, or it's no thicker than the wall, a quarter of the
   * thickness of the wall away from its middle line, and comes out of one of its faces.
   */
  static Issue getMisalignedDoorIssue(HomePieceOfFurniture piece, String label, Wall wall, String wallId) {
    if (!isStraight(wall)) {
      return null;
    }
    double wallAngle = Math.atan2(wall.getYEnd() - wall.getYStart(), wall.getXEnd() - wall.getXStart());
    double difference = Math.toDegrees(Math.abs(Math.IEEEremainder(piece.getAngle() - wallAngle, Math.PI)));
    double ux = Math.cos(wallAngle);
    double uy = Math.sin(wallAngle);
    double offset = Math.abs((piece.getX() - wall.getXStart()) * -uy + (piece.getY() - wall.getYStart()) * ux);
    boolean offLine = isOffMiddleLine(piece, wall, offset);
    if (difference > DOOR_ANGLE_TOLERANCE || offLine) {
      return new Issue(Type.DOOR_MISALIGNED, label + " isn't lined up with " + wallId + ": "
          + (difference > DOOR_ANGLE_TOLERANCE
              ? "it's turned by " + String.format(Locale.ENGLISH, "%.2f", Double.valueOf(difference)) + " degrees from the wall" : "")
          + (difference > DOOR_ANGLE_TOLERANCE && offLine ? " and " : "")
          + (offLine ? "its center is " + format(Math.round(offset * 10) / 10.) + " cm off the middle line of the wall, "
              + format(wall.getThickness()) + " cm thick" : "")
          + ", so a part of it is hidden in the wall", piece, wall);
    }
    return null;
  }

  /**
   * Returns <code>true</code> if a door or a window at the given distance from the middle line of its wall
   * is out of its place: farther than a quarter of the thickness of the wall, and out of one of its faces.
   * A door thicker than its wall, or set against one of its faces, is where it was meant to be.
   */
  static boolean isOffMiddleLine(HomePieceOfFurniture piece, Wall wall, double offset) {
    return piece.getDepth() <= wall.getThickness() + 1
        && Math.abs(offset) > wall.getThickness() / 4
        && Math.abs(offset) + piece.getDepth() / 2 > wall.getThickness() / 2 + 0.5;
  }

  // ---- Finishes of the sides of walls ----

  /**
   * Returns <code>true</code> if a texture is one made for the outside of a building.
   * @see #OUTSIDE_MATERIAL_WORDS
   */
  static boolean isOutsideMaterial(HomeTexture texture) {
    if (texture == null) {
      return false;
    }
    String text = ((texture.getName() != null ? texture.getName() : "") + " "
        + (texture.getCatalogId() != null ? texture.getCatalogId() : "")).toLowerCase(Locale.ENGLISH);
    for (String word : OUTSIDE_MATERIAL_WORDS) {
      if (text.indexOf(word) >= 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the rooms that the left side and the right side of a wall face, <code>null</code> for a side
   * that faces none: points 12 cm away from each face, at a quarter, half and three quarters of
   * the length of the wall, are looked for in the rooms of the level of the wall.
   */
  static Room [] getFacedRooms(Home home, Wall wall) {
    Room [] rooms = new Room [2];
    float [][] points = wall.getPoints();
    if (!isStraight(wall) || points.length != 4) {
      return rooms;
    }
    float middleX = (wall.getXStart() + wall.getXEnd()) / 2;
    float middleY = (wall.getYStart() + wall.getYEnd()) / 2;
    // The first two points of a wall are the ones of its left side
    double nx = (points [0][0] + points [1][0]) / 2 - middleX;
    double ny = (points [0][1] + points [1][1]) / 2 - middleY;
    double length = Math.hypot(nx, ny);
    if (length < 1E-3) {
      return rooms;
    }
    nx /= length;
    ny /= length;
    double distance = wall.getThickness() / 2 + 12;
    for (int side = 0; side < 2; side++) {
      int sign = side == 0 ? 1 : -1;
      Map<Room, Integer> votes = new HashMap<Room, Integer>();
      for (float t = 0.25f; t <= 0.75f; t += 0.25f) {
        float x = (float)(wall.getXStart() + (wall.getXEnd() - wall.getXStart()) * t + sign * nx * distance);
        float y = (float)(wall.getYStart() + (wall.getYEnd() - wall.getYStart()) * t + sign * ny * distance);
        for (Room room : home.getRooms()) {
          if (room.getLevel() == wall.getLevel() && room.getPointCount() >= 3 && room.containsPoint(x, y, 0)) {
            Integer count = votes.get(room);
            votes.put(room, Integer.valueOf(count != null ? count.intValue() + 1 : 1));
            break;
          }
        }
      }
      int best = 0;
      for (Map.Entry<Room, Integer> vote : votes.entrySet()) {
        if (vote.getValue().intValue() > best) {
          best = vote.getValue().intValue();
          rooms [side] = vote.getKey();
        }
      }
    }
    return rooms;
  }

  /**
   * Reports the sides of walls whose finish doesn't match what they face: an outside material on a side
   * that faces a room, an inside finish on a side that faces the outside, and sides left unpainted.
   * A side faces the outside if it faces no room while the other side of its wall faces one.
   */
  static void checkWallFinishes(Home home, List<Wall> walls, List<Room> rooms, List<Issue> issues, int [] counts) {
    if (home.getRooms().isEmpty()) {
      return;
    }
    List<Wall> homeWalls = new ArrayList<Wall>(home.getWalls());
    // {wall, side, room or null}
    List<Object []> sides = new ArrayList<Object []>();
    int painted = 0;
    double outsideLength = 0;
    double outsideMaterialLength = 0;
    List<Object> insideFinishes = new ArrayList<Object>();
    for (Wall wall : walls) {
      if (!homeWalls.contains(wall)) {
        continue;
      }
      Room [] faced = getFacedRooms(home, wall);
      if (faced [0] == null && faced [1] == null) {
        // A garden wall, or a wall in a part of the plan without rooms
        continue;
      }
      for (int side = 0; side < 2; side++) {
        Integer color = side == 0 ? wall.getLeftSideColor() : wall.getRightSideColor();
        HomeTexture texture = side == 0 ? wall.getLeftSideTexture() : wall.getRightSideTexture();
        sides.add(new Object [] {wall, Integer.valueOf(side), faced [side]});
        if (color != null || texture != null) {
          painted++;
        }
        if (faced [side] == null) {
          outsideLength += wall.getLength();
          if (isOutsideMaterial(texture)) {
            outsideMaterialLength += wall.getLength();
          }
        } else if (texture != null && !isOutsideMaterial(texture)) {
          insideFinishes.add(texture.getName() != null ? texture.getName() : texture.getCatalogId());
        } else if (texture == null && color != null) {
          insideFinishes.add(color);
        }
      }
    }
    // The outside of this home has a material of its own
    boolean outsideHasMaterial = outsideLength > 0 && outsideMaterialLength >= 0.5 * outsideLength;
    // The home is being painted: the sides left with no finish are worth telling
    boolean homePainted = painted >= 0.3 * sides.size();
    for (Object [] sideData : sides) {
      Wall wall = (Wall)sideData [0];
      int side = ((Integer)sideData [1]).intValue();
      Room room = (Room)sideData [2];
      Integer color = side == 0 ? wall.getLeftSideColor() : wall.getRightSideColor();
      HomeTexture texture = side == 0 ? wall.getLeftSideTexture() : wall.getRightSideTexture();
      String label = "The " + (side == 0 ? "left" : "right") + " side of " + id(wall, "W", walls.indexOf(wall));
      if (room != null && isOutsideMaterial(texture)) {
        HomeDiagnostics.add(issues, counts, new Issue(Type.WALL_OUTSIDE_FINISH_INDOORS, label + " faces "
            + name(id(room, "R", rooms.indexOf(room)), room.getName()) + " with the outside material \""
            + (texture.getName() != null ? texture.getName() : texture.getCatalogId()) + "\"", wall, room));
      } else if (room == null && outsideHasMaterial && !isOutsideMaterial(texture) && (color != null || texture != null)
                 && insideFinishes.contains(texture != null
                        ? (Object)(texture.getName() != null ? texture.getName() : texture.getCatalogId()) : (Object)color)) {
        HomeDiagnostics.add(issues, counts, new Issue(Type.WALL_INSIDE_FINISH_OUTDOORS, label + " faces the outside with "
            + (texture != null ? "the texture \"" + (texture.getName() != null ? texture.getName() : texture.getCatalogId()) + "\""
                : String.format("the color #%06X", Integer.valueOf(color.intValue() & 0xFFFFFF)))
            + ", a finish of the rooms, where most of the outside of the home has a material of its own", wall, null));
      } else if (homePainted && color == null && texture == null) {
        HomeDiagnostics.add(issues, counts, new Issue(Type.WALL_SIDE_UNPAINTED, label + ", which faces "
            + (room != null ? name(id(room, "R", rooms.indexOf(room)), room.getName()) : "the outside")
            + ", has no color and no texture", wall, room));
      }
    }
  }

  // ---- Items that cross another item ----

  /**
   * Returns the list of the items that cross another item, level by level, or an empty text: furniture in
   * a wall or in another piece, doors and windows across a wall, walls that cross each other, and the
   * issues of <code>issues</code> about roofs that cross rooms, walls, doors and ceilings.
   * @param maxCount the largest count of lines returned
   */
  static String describeCrossings(Home home, List<Issue> issues, int maxCount) {
    Map<String, List<String>> crossings = new LinkedHashMap<String, List<String>>();
    for (Level level : home.getLevels()) {
      crossings.put(getLevelLabel(level), new ArrayList<String>());
    }
    List<Wall> walls = new ArrayList<Wall>(home.getWalls());
    List<Area> wallAreas = new ArrayList<Area>();
    List<Rectangle2D> wallBounds = new ArrayList<Rectangle2D>();
    for (Wall wall : walls) {
      float [][] points = wall.getPoints();
      wallAreas.add(null);
      wallBounds.add(getBounds(points));
    }
    // Walls that cross each other away from their ends
    for (int i = 0; i < walls.size(); i++) {
      Wall wall = walls.get(i);
      for (int j = 0; j < i; j++) {
        Wall other = walls.get(j);
        if (other.getLevel() == wall.getLevel() && isStraight(wall) && isStraight(other)
            && wallBounds.get(i).intersects(wallBounds.get(j)) && isCrossing(wall, other)) {
          add(crossings, wall.getLevel(), id(wall, "W", i) + " crosses " + id(other, "W", j) + " away from their ends");
        }
      }
    }
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    List<Area> furnitureAreas = new ArrayList<Area>();
    List<Rectangle2D> furnitureBounds = new ArrayList<Rectangle2D>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (piece.isVisible() && !(piece instanceof HomeFurnitureGroup) && !HomeTerrain.isTerrain(piece)
          && piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) == null
          && piece.getProperty(AssistantShapeCommands.ROOF_SPEC_PROPERTY) == null
          && piece.getWidth() > 0 && piece.getDepth() > 0) {
        furniture.add(piece);
        furnitureAreas.add(null);
        furnitureBounds.add(getBounds(piece.getPoints()));
      }
    }
    for (int i = 0; i < furniture.size(); i++) {
      HomePieceOfFurniture piece = furniture.get(i);
      String label = name(id(piece, "F", home.getFurniture().indexOf(piece)), piece.getName());
      float bottom = piece.getGroundElevation();
      float top = bottom + piece.getHeight();
      double surface = piece.getWidth() * piece.getDepth();
      Wall hostWall = piece.isDoorOrWindow() ? HomeDiagnostics.getClosestWall(piece, walls, HomeDiagnostics.DOOR_SNAP_DISTANCE) : null;
      for (int j = 0; j < walls.size(); j++) {
        Wall wall = walls.get(j);
        float wallBase = getElevation(wall.getLevel());
        if (wall.getLevel() != piece.getLevel() || !furnitureBounds.get(i).intersects(wallBounds.get(j))
            || top <= wallBase + 2 || bottom >= wallBase + Math.max(getWallHeight(home, wall, 0), getWallHeight(home, wall, 1)) - 2) {
          continue;
        }
        if (wall == hostWall) {
          // A door in its wall: reported if it's turned across it
          if (isStraight(wall)) {
            double wallAngle = Math.atan2(wall.getYEnd() - wall.getYStart(), wall.getXEnd() - wall.getXStart());
            double difference = Math.toDegrees(Math.abs(Math.IEEEremainder(piece.getAngle() - wallAngle, Math.PI)));
            if (difference > DOOR_ANGLE_TOLERANCE) {
              add(crossings, piece.getLevel(), label + " crosses its wall " + id(wall, "W", j) + " at "
                  + format(Math.round(difference * 10) / 10.) + " degrees");
            }
          }
          continue;
        }
        if (furnitureAreas.get(i) == null) {
          furnitureAreas.set(i, getArea(piece.getPoints()));
        }
        if (wallAreas.get(j) == null) {
          wallAreas.set(j, getArea(wall.getPoints()));
        }
        Area common = new Area(furnitureAreas.get(i));
        common.intersect(wallAreas.get(j));
        if (!common.isEmpty()) {
          double commonSurface = getSurface(common);
          if (commonSurface > Math.max(100, (piece.isDoorOrWindow() ? 0.3 : 0.08) * surface)) {
            add(crossings, piece.getLevel(), label + " is in the wall " + id(wall, "W", j) + " over "
                + format(Math.round(commonSurface)) + " cm2 of the plan");
          }
        }
      }
      if (piece.isDoorOrWindow()) {
        continue;
      }
      for (int j = 0; j < i; j++) {
        HomePieceOfFurniture other = furniture.get(j);
        if (other.isDoorOrWindow() || other.getLevel() != piece.getLevel()
            || !furnitureBounds.get(i).intersects(furnitureBounds.get(j))) {
          continue;
        }
        float otherBottom = other.getGroundElevation();
        float otherTop = otherBottom + other.getHeight();
        if (Math.min(top, otherTop) - Math.max(bottom, otherBottom) <= 5) {
          continue;
        }
        if (furnitureAreas.get(i) == null) {
          furnitureAreas.set(i, getArea(piece.getPoints()));
        }
        if (furnitureAreas.get(j) == null) {
          furnitureAreas.set(j, getArea(other.getPoints()));
        }
        Area common = new Area(furnitureAreas.get(i));
        common.intersect(furnitureAreas.get(j));
        // A chair pushed under a table shares less than this with it
        if (!common.isEmpty()
            && getSurface(common) > 0.6 * Math.min(surface, other.getWidth() * other.getDepth())) {
          add(crossings, piece.getLevel(), label + " stands in "
              + name(id(other, "F", home.getFurniture().indexOf(other)), other.getName()));
        }
      }
    }
    for (Issue issue : issues) {
      switch (issue.getType()) {
        case ROOF_IN_ROOM :
        case WALL_ABOVE_ROOF :
        case CEILING_ABOVE_ROOF :
        case ROOF_AT_CEILING :
        case DOOR_THROUGH_ROOF :
          Selectable item = issue.getType() == Type.ROOF_IN_ROOM || issue.getType() == Type.ROOF_AT_CEILING
              ? issue.getOtherItem() : issue.getItem();
          add(crossings, item instanceof Room ? ((Room)item).getLevel()
              : item instanceof Wall ? ((Wall)item).getLevel()
              : item instanceof HomePieceOfFurniture ? ((HomePieceOfFurniture)item).getLevel() : null, issue.getMessage());
          break;
        default :
          break;
      }
    }
    int count = 0;
    for (List<String> lines : crossings.values()) {
      count += lines.size();
    }
    if (count == 0) {
      return "";
    }
    StringBuilder text = new StringBuilder("Items that cross another item (").append(count).append("):");
    int written = 0;
    for (Map.Entry<String, List<String>> entry : crossings.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      text.append('\n').append(entry.getKey()).append(':');
      for (String line : entry.getValue()) {
        if (written++ < maxCount) {
          text.append("\n- ").append(line);
        }
      }
    }
    if (written > maxCount) {
      text.append("\n... and ").append(written - maxCount).append(" more");
    }
    return text.toString();
  }

  /**
   * Returns the count of the items that cross another item.
   */
  static int countCrossings(String crossings) {
    int start = crossings.indexOf('(');
    int end = crossings.indexOf(')');
    try {
      return start >= 0 && end > start ? Integer.parseInt(crossings.substring(start + 1, end)) : 0;
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private static String getLevelLabel(Level level) {
    return level == null ? "Home" : "Level \"" + level.getName() + "\"";
  }

  private static void add(Map<String, List<String>> crossings, Level level, String line) {
    String label = getLevelLabel(level);
    List<String> lines = crossings.get(label);
    if (lines == null) {
      lines = new ArrayList<String>();
      crossings.put(label, lines);
    }
    lines.add(line);
  }

  /**
   * Returns <code>true</code> if the middle lines of two walls cross away from the ends of both.
   */
  private static boolean isCrossing(Wall wall, Wall other) {
    double x1 = wall.getXStart();
    double y1 = wall.getYStart();
    double dx1 = wall.getXEnd() - x1;
    double dy1 = wall.getYEnd() - y1;
    double x2 = other.getXStart();
    double y2 = other.getYStart();
    double dx2 = other.getXEnd() - x2;
    double dy2 = other.getYEnd() - y2;
    double determinant = dx1 * dy2 - dy1 * dx2;
    if (Math.abs(determinant) < 1E-6 * wall.getLength() * other.getLength()
        || Math.abs(determinant) < PARALLEL_SINE * wall.getLength() * other.getLength()) {
      return false;
    }
    double t = ((x2 - x1) * dy2 - (y2 - y1) * dx2) / determinant;
    double u = ((x2 - x1) * dy1 - (y2 - y1) * dx1) / determinant;
    double margin = Math.max(wall.getThickness(), other.getThickness()) + 2;
    return t * wall.getLength() > margin && (1 - t) * wall.getLength() > margin
        && u * other.getLength() > margin && (1 - u) * other.getLength() > margin;
  }
}
