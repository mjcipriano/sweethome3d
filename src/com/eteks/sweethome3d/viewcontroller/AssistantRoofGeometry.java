/*
 * AssistantRoofGeometry.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The shape of a roof over a polygon of the plan, each edge of which is an
 * eave under a slope (hip) or a vertical end (gable).
 * <p>The polygon is covered by convex parts whose sides lie along its edges:
 * the polygon itself if it's convex, its largest rectangles if its edges are
 * perpendicular, like the wings of a house. Over each part, a plane rises
 * from each eave and the roof is the lowest of these planes, which is the
 * roof a straight skeleton gives for a convex polygon. Where parts overlap,
 * the highest one shows: wings meet in valleys and the ridge of a narrow wing
 * ends in the slope of a wider one, as roofs are framed. Dormers are small
 * parts standing on the slopes.
 * <p>Points are (x, y) in the plan and heights are measured upward.
 * @author Sweet Home 3D
 */
final class AssistantRoofGeometry {
  /** Kinds of edges. */
  static final int HIP = 0;
  static final int GABLE = 1;
  /** A side of a part that isn't an edge of the roof. */
  static final int INNER = 2;

  private static final double EPSILON = 1E-6;

  /**
   * A convex part of the roof.
   */
  static final class Part {
    private final double [][] polygon;
    private final int []      types;
    private final double []   slopes;
    private final double []   overhangs;
    private final double      base;
    private final double      thickness;
    private final boolean     dormer;
    private double [][]       footprint;
    private double [][]       planes;
    private List<double [][]> faces = new ArrayList<double [][]>();
    private List<double []>   facePlanes = new ArrayList<double []>();

    /**
     * @param polygon   the points of the part along the walls, in any order of rotation
     * @param types     the kind of the side starting at each point
     * @param slopes    the tangent of the pitch of the slope rising from each side (ignored for sides that aren't hips)
     * @param overhangs the distance the roof goes beyond each side
     * @param base      the height of the top of the roof above the sides it rises from
     * @param thickness the thickness of the roof, measured vertically
     */
    Part(double [][] polygon, int [] types, double [] slopes, double [] overhangs,
         double base, double thickness, boolean dormer) {
      if (getSignedArea(polygon) < 0) {
        // Turned so the inside is at the left of each side
        int count = polygon.length;
        double [][] reversed = new double [count][];
        int [] reversedTypes = new int [count];
        double [] reversedSlopes = new double [count];
        double [] reversedOverhangs = new double [count];
        for (int i = 0; i < count; i++) {
          reversed [i] = polygon [count - 1 - i];
          // The side starting at the point i was the one ending at it
          int side = (2 * count - 2 - i) % count;
          reversedTypes [i] = types [side];
          reversedSlopes [i] = slopes [side];
          reversedOverhangs [i] = overhangs [side];
        }
        polygon = reversed;
        types = reversedTypes;
        slopes = reversedSlopes;
        overhangs = reversedOverhangs;
      }
      this.polygon = polygon;
      this.types = types;
      this.slopes = slopes;
      this.overhangs = overhangs;
      this.base = base;
      this.thickness = thickness;
      this.dormer = dormer;
      compute();
    }

    private void compute() {
      int count = this.polygon.length;
      double [][] normals = new double [count][];
      double size = 0;
      for (int i = 0; i < count; i++) {
        double [] point = this.polygon [i];
        double [] next = this.polygon [(i + 1) % count];
        double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
        normals [i] = new double [] {-(next [1] - point [1]) / length, (next [0] - point [0]) / length};
        size = Math.max(size, Math.max(Math.abs(point [0]), Math.abs(point [1])));
      }
      // The part with its overhangs
      size = size * 2 + 100000;
      double [][] footprint = {{-size, -size}, {size, -size}, {size, size}, {-size, size}};
      this.planes = new double [count][];
      boolean sloped = false;
      for (int i = 0; i < count && footprint.length >= 3; i++) {
        double [] point = this.polygon [i];
        footprint = clip(footprint, normals [i][0], normals [i][1],
            normals [i][0] * point [0] + normals [i][1] * point [1] - this.overhangs [i]);
        if (this.types [i] == HIP && this.slopes [i] > 0) {
          double slope = this.slopes [i];
          this.planes [i] = new double [] {
              this.base - slope * (normals [i][0] * point [0] + normals [i][1] * point [1]),
              slope * normals [i][0], slope * normals [i][1]};
          sloped = true;
        }
      }
      this.footprint = footprint;
      if (footprint.length < 3) {
        return;
      }
      if (!sloped) {
        // A flat roof
        this.faces.add(footprint);
        this.facePlanes.add(new double [] {this.base, 0, 0});
        return;
      }
      for (int i = 0; i < count; i++) {
        if (this.planes [i] == null) {
          continue;
        }
        // Where this plane is the lowest one
        double [][] face = footprint;
        for (int j = 0; j < count && face.length >= 3; j++) {
          if (j != i && this.planes [j] != null) {
            face = clipLower(face, this.planes [i], this.planes [j], j < i);
          }
        }
        if (face.length >= 3 && Math.abs(getSignedArea(face)) > 0.01) {
          this.faces.add(face);
          this.facePlanes.add(this.planes [i]);
        }
      }
    }

    boolean isDormer() {
      return this.dormer;
    }

    double getThickness() {
      return this.thickness;
    }

    /**
     * Returns the slopes of this part alone, whatever the other parts hide of them.
     */
    List<Cell> getOwnCells() {
      List<Cell> cells = new ArrayList<Cell>();
      for (int i = 0; i < this.faces.size(); i++) {
        cells.add(new Cell(this.faces.get(i), this.facePlanes.get(i), this));
      }
      return cells;
    }

    double [][] getPolygon() {
      return this.polygon;
    }

    double [][] getFootprint() {
      return this.footprint;
    }

    /**
     * Returns the height of the top of this part at a point, or NaN out of it.
     */
    double getHeight(double x, double y) {
      if (this.footprint.length < 3 || !contains(this.footprint, x, y, 1E-4)) {
        return Double.NaN;
      }
      return getPlanesHeight(x, y);
    }

    private double getPlanesHeight(double x, double y) {
      double height = Double.POSITIVE_INFINITY;
      for (double [] plane : this.planes) {
        if (plane != null) {
          height = Math.min(height, plane [0] + plane [1] * x + plane [2] * y);
        }
      }
      return height == Double.POSITIVE_INFINITY ? this.base : height;
    }

    /**
     * Returns the highest point of this part.
     */
    double getMaxHeight() {
      double height = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < this.faces.size(); i++) {
        double [] plane = this.facePlanes.get(i);
        for (double [] point : this.faces.get(i)) {
          height = Math.max(height, plane [0] + plane [1] * point [0] + plane [2] * point [1]);
        }
      }
      return height;
    }
  }

  /**
   * A convex piece of the surface of the roof, seen from above.
   */
  static final class Cell {
    final double [][] polygon;
    final double []   plane;
    final Part        part;

    Cell(double [][] polygon, double [] plane, Part part) {
      this.polygon = getSignedArea(polygon) < 0 ? reverse(polygon) : polygon;
      this.plane = plane;
      this.part = part;
    }

    double getHeight(double x, double y) {
      return this.plane [0] + this.plane [1] * x + this.plane [2] * y;
    }
  }

  private final List<Part> parts = new ArrayList<Part>();
  private List<Cell>       cells;
  private List<Cell>       mainCells;

  void addPart(Part part) {
    if (part.footprint.length >= 3) {
      this.parts.add(part);
      this.cells = null;
      this.mainCells = null;
    }
  }

  List<Part> getParts() {
    return this.parts;
  }

  /**
   * Returns the height of the top of the roof at a point, or NaN if the roof doesn't cover it.
   * @param withDormers <code>false</code> to ignore dormers
   */
  double getHeight(double x, double y, boolean withDormers) {
    double height = Double.NaN;
    for (Part part : this.parts) {
      if (withDormers || !part.dormer) {
        double partHeight = part.getHeight(x, y);
        if (!Double.isNaN(partHeight) && !(partHeight <= height)) {
          height = partHeight;
        }
      }
    }
    return height;
  }

  /**
   * Returns the height of the underside of the roof without its dormers at a
   * point, or NaN if the roof doesn't cover it.
   */
  double getUndersideHeight(double x, double y) {
    double height = Double.NaN;
    for (Part part : this.parts) {
      if (!part.dormer) {
        double partHeight = part.getHeight(x, y) - part.thickness;
        if (!Double.isNaN(partHeight) && !(partHeight <= height)) {
          height = partHeight;
        }
      }
    }
    return height;
  }

  double getMaxHeight() {
    double height = Double.NEGATIVE_INFINITY;
    for (Part part : this.parts) {
      height = Math.max(height, part.getMaxHeight());
    }
    return height;
  }

  /**
   * Returns the pieces of the surface of the roof seen from above.
   * @param withDormers <code>false</code> for the roof as it would be without its dormers
   */
  List<Cell> getCells(boolean withDormers) {
    if (withDormers) {
      if (this.cells == null) {
        this.cells = computeCells(true);
      }
      return this.cells;
    } else {
      if (this.mainCells == null) {
        boolean dormers = false;
        for (Part part : this.parts) {
          dormers |= part.dormer;
        }
        this.mainCells = dormers ? computeCells(false) : getCells(true);
      }
      return this.mainCells;
    }
  }

  private List<Cell> computeCells(boolean withDormers) {
    List<Cell> cells = new ArrayList<Cell>();
    List<Part> parts = new ArrayList<Part>();
    for (Part part : this.parts) {
      if (withDormers || !part.dormer) {
        parts.add(part);
      }
    }
    for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
      Part part = parts.get(partIndex);
      for (int faceIndex = 0; faceIndex < part.faces.size(); faceIndex++) {
        double [][] face = part.faces.get(faceIndex);
        double [] plane = part.facePlanes.get(faceIndex);
        Area visible = null;
        Rectangle2D bounds = getBounds(face);
        for (int otherIndex = 0; otherIndex < parts.size(); otherIndex++) {
          Part other = parts.get(otherIndex);
          if (other == part || !bounds.intersects(getBounds(other.footprint))) {
            continue;
          }
          // Where the other part is above this face
          double [][] hidden = other.footprint;
          boolean sloped = false;
          for (int i = 0; i < other.planes.length && hidden.length >= 3; i++) {
            if (other.planes [i] != null) {
              hidden = clipLower(hidden, plane, other.planes [i], otherIndex < partIndex);
              sloped = true;
            }
          }
          if (!sloped) {
            hidden = clipLower(hidden, plane, new double [] {other.base, 0, 0}, otherIndex < partIndex);
          }
          if (hidden.length >= 3 && Math.abs(getSignedArea(hidden)) > 0.01) {
            if (visible == null) {
              visible = AssistantMeshBuilder.getArea(toFloats(face));
            }
            visible.subtract(AssistantMeshBuilder.getArea(toFloats(hidden)));
          }
        }
        if (visible == null) {
          cells.add(new Cell(face, plane, part));
        } else {
          for (float [][] trapezoid : AssistantMeshBuilder.getTrapezoids(visible)) {
            double [][] polygon = toDoubles(trapezoid);
            if (Math.abs(getSignedArea(polygon)) > 0.005) {
              cells.add(new Cell(polygon, plane, part));
            }
          }
        }
      }
    }
    return cells;
  }

  /**
   * Returns the shape covered by the roof in the plan.
   */
  Area getFootprint() {
    Area footprint = new Area();
    for (Part part : this.parts) {
      footprint.add(AssistantMeshBuilder.getArea(toFloats(part.footprint)));
    }
    return footprint;
  }

  /**
   * Returns the pieces of the segment joining two points that the given cells
   * cover, each one as {t1, t2, height1, height2, thickness of the roof} where t1 and t2 are between
   * 0 at the start of the segment and 1 at its end, sorted along the segment.
   */
  static List<double []> getProfile(double x1, double y1, double x2, double y2, List<Cell> cells) {
    List<double []> profile = new ArrayList<double []>();
    for (Cell cell : cells) {
      double [] range = clipSegment(x1, y1, x2, y2, cell.polygon);
      if (range != null) {
        profile.add(new double [] {range [0], range [1],
            cell.getHeight(x1 + (x2 - x1) * range [0], y1 + (y2 - y1) * range [0]),
            cell.getHeight(x1 + (x2 - x1) * range [1], y1 + (y2 - y1) * range [1]), cell.part.thickness});
      }
    }
    Collections.sort(profile, new Comparator<double []>() {
        public int compare(double [] piece1, double [] piece2) {
          return Double.compare(piece1 [0], piece2 [0]);
        }
      });
    return profile;
  }

  /**
   * Returns the part {t1, t2} of a segment inside a convex polygon whose inside
   * is at the left of its sides, or <code>null</code> if it's shorter than a millimeter.
   */
  static double [] clipSegment(double x1, double y1, double x2, double y2, double [][] polygon) {
    double start = 0;
    double end = 1;
    double length = Math.hypot(x2 - x1, y2 - y1);
    for (int i = 0; i < polygon.length; i++) {
      double [] point = polygon [i];
      double [] next = polygon [(i + 1) % polygon.length];
      double nx = -(next [1] - point [1]);
      double ny = next [0] - point [0];
      double distance1 = nx * (x1 - point [0]) + ny * (y1 - point [1]);
      double distance2 = nx * (x2 - point [0]) + ny * (y2 - point [1]);
      if (distance1 < 0 && distance2 < 0) {
        return null;
      } else if (distance1 < 0) {
        start = Math.max(start, distance1 / (distance1 - distance2));
      } else if (distance2 < 0) {
        end = Math.min(end, distance1 / (distance1 - distance2));
      }
    }
    return (end - start) * length > 0.1 ? new double [] {start, end} : null;
  }

  // ---- Decomposition of a polygon ----

  /**
   * Returns convex polygons that cover <code>polygon</code> and whose sides
   * lie along its edges: itself if it's convex, its largest rectangles if all
   * its edges are perpendicular or parallel, otherwise convex pieces that
   * don't overlap, some sides of which cross it.
   */
  static List<double [][]> getConvexParts(double [][] polygon) {
    List<double [][]> parts = new ArrayList<double [][]>();
    if (getSignedArea(polygon) < 0) {
      polygon = reverse(polygon);
    }
    if (isConvex(polygon)) {
      parts.add(polygon);
      return parts;
    }
    // In a frame where the first edge is horizontal
    double angle = Math.atan2(polygon [1][1] - polygon [0][1], polygon [1][0] - polygon [0][0]);
    double cos = Math.cos(-angle);
    double sin = Math.sin(-angle);
    double [][] turned = new double [polygon.length][];
    boolean rectilinear = true;
    for (int i = 0; i < polygon.length; i++) {
      turned [i] = new double [] {polygon [i][0] * cos - polygon [i][1] * sin, polygon [i][0] * sin + polygon [i][1] * cos};
    }
    for (int i = 0; i < turned.length; i++) {
      double [] next = turned [(i + 1) % turned.length];
      rectilinear &= Math.abs(next [0] - turned [i][0]) < 0.5 || Math.abs(next [1] - turned [i][1]) < 0.5;
    }
    List<double [][]> turnedParts = rectilinear ? getLargestRectangles(turned) : getConvexCells(turned);
    for (double [][] part : turnedParts) {
      double [][] points = new double [part.length][];
      for (int i = 0; i < part.length; i++) {
        points [i] = new double [] {part [i][0] * cos + part [i][1] * sin, -part [i][0] * sin + part [i][1] * cos};
      }
      parts.add(points);
    }
    return parts;
  }

  private static List<double [][]> getLargestRectangles(double [][] polygon) {
    double [] xs = getDistinct(polygon, 0);
    double [] ys = getDistinct(polygon, 1);
    int columns = xs.length - 1;
    int rows = ys.length - 1;
    boolean [][] inside = new boolean [columns][rows];
    for (int i = 0; i < columns; i++) {
      for (int j = 0; j < rows; j++) {
        inside [i][j] = containsPoint(polygon, (xs [i] + xs [i + 1]) / 2, (ys [j] + ys [j + 1]) / 2);
      }
    }
    List<double [][]> rectangles = new ArrayList<double [][]>();
    for (int i1 = 0; i1 < columns; i1++) {
      for (int j1 = 0; j1 < rows; j1++) {
        for (int i2 = i1; i2 < columns; i2++) {
          for (int j2 = j1; j2 < rows; j2++) {
            if (isInside(inside, i1, j1, i2, j2)
                && (i1 == 0 || !isInside(inside, i1 - 1, j1, i1 - 1, j2))
                && (i2 == columns - 1 || !isInside(inside, i2 + 1, j1, i2 + 1, j2))
                && (j1 == 0 || !isInside(inside, i1, j1 - 1, i2, j1 - 1))
                && (j2 == rows - 1 || !isInside(inside, i1, j2 + 1, i2, j2 + 1))) {
              rectangles.add(new double [][] {{xs [i1], ys [j1]}, {xs [i2 + 1], ys [j1]},
                                              {xs [i2 + 1], ys [j2 + 1]}, {xs [i1], ys [j2 + 1]}});
            }
          }
        }
      }
    }
    return rectangles;
  }

  private static boolean isInside(boolean [][] inside, int i1, int j1, int i2, int j2) {
    for (int i = i1; i <= i2; i++) {
      for (int j = j1; j <= j2; j++) {
        if (!inside [i][j]) {
          return false;
        }
      }
    }
    return true;
  }

  private static double [] getDistinct(double [][] polygon, int coordinate) {
    double [] values = new double [polygon.length];
    for (int i = 0; i < polygon.length; i++) {
      values [i] = polygon [i][coordinate];
    }
    Arrays.sort(values);
    List<Double> distinct = new ArrayList<Double>();
    for (double value : values) {
      if (distinct.isEmpty() || value - distinct.get(distinct.size() - 1) > 0.5) {
        distinct.add(value);
      }
    }
    double [] result = new double [distinct.size()];
    for (int i = 0; i < result.length; i++) {
      result [i] = distinct.get(i);
    }
    return result;
  }

  /**
   * Cuts a polygon along the lines of the edges at its concave corners and
   * returns the convex pieces, merged with their neighbors while they stay convex.
   */
  private static List<double [][]> getConvexCells(double [][] polygon) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (double [] point : polygon) {
      minX = Math.min(minX, point [0]);
      minY = Math.min(minY, point [1]);
      maxX = Math.max(maxX, point [0]);
      maxY = Math.max(maxY, point [1]);
    }
    List<double [][]> cells = new ArrayList<double [][]>();
    cells.add(new double [][] {{minX - 1, minY - 1}, {maxX + 1, minY - 1}, {maxX + 1, maxY + 1}, {minX - 1, maxY + 1}});
    int count = polygon.length;
    for (int i = 0; i < count && cells.size() < 2000; i++) {
      double [] point = polygon [i];
      double [] next = polygon [(i + 1) % count];
      double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
      if (length < 0.01) {
        continue;
      }
      double nx = -(next [1] - point [1]) / length;
      double ny = (next [0] - point [0]) / length;
      double c = nx * point [0] + ny * point [1];
      List<double [][]> cut = new ArrayList<double [][]>();
      for (double [][] cell : cells) {
        double [][] left = clip(cell, nx, ny, c);
        double [][] right = clip(cell, -nx, -ny, -c);
        if (left.length >= 3 && Math.abs(getSignedArea(left)) > 0.01) {
          cut.add(left);
        }
        if (right.length >= 3 && Math.abs(getSignedArea(right)) > 0.01) {
          cut.add(right);
        }
      }
      cells = cut;
    }
    List<double [][]> insideCells = new ArrayList<double [][]>();
    for (double [][] cell : cells) {
      double [] center = getCenter(cell);
      if (containsPoint(polygon, center [0], center [1])) {
        insideCells.add(cell);
      }
    }
    // Merge cells while their union stays convex
    boolean merged = true;
    while (merged && insideCells.size() > 1) {
      merged = false;
      for (int i = 0; i < insideCells.size() && !merged; i++) {
        for (int j = i + 1; j < insideCells.size() && !merged; j++) {
          double [][] hull = getConvexHull(insideCells.get(i), insideCells.get(j));
          double area = Math.abs(getSignedArea(insideCells.get(i))) + Math.abs(getSignedArea(insideCells.get(j)));
          if (Math.abs(Math.abs(getSignedArea(hull)) - area) < 1E-4 * area + 0.01) {
            insideCells.set(i, hull);
            insideCells.remove(j);
            merged = true;
          }
        }
      }
    }
    return insideCells;
  }

  private static double [][] getConvexHull(double [][] polygon1, double [][] polygon2) {
    List<double []> points = new ArrayList<double []>();
    points.addAll(Arrays.asList(polygon1));
    points.addAll(Arrays.asList(polygon2));
    Collections.sort(points, new Comparator<double []>() {
        public int compare(double [] point1, double [] point2) {
          return point1 [0] != point2 [0] ? Double.compare(point1 [0], point2 [0]) : Double.compare(point1 [1], point2 [1]);
        }
      });
    double [][] hull = new double [2 * points.size()][];
    int size = 0;
    for (int pass = 0; pass < 2; pass++) {
      int start = size;
      for (int i = 0; i < points.size(); i++) {
        double [] point = points.get(pass == 0 ? i : points.size() - 1 - i);
        while (size - start >= 2 && cross(hull [size - 2], hull [size - 1], point) <= 1E-7) {
          size--;
        }
        hull [size++] = point;
      }
      size--;
    }
    return Arrays.copyOf(hull, size);
  }

  private static double cross(double [] origin, double [] point1, double [] point2) {
    return (point1 [0] - origin [0]) * (point2 [1] - origin [1]) - (point1 [1] - origin [1]) * (point2 [0] - origin [0]);
  }

  // ---- Geometry ----

  static double getSignedArea(double [][] polygon) {
    double area = 0;
    for (int i = 0; i < polygon.length; i++) {
      double [] point = polygon [i];
      double [] next = polygon [(i + 1) % polygon.length];
      area += point [0] * next [1] - next [0] * point [1];
    }
    return area / 2;
  }

  static double [][] reverse(double [][] polygon) {
    double [][] reversed = new double [polygon.length][];
    for (int i = 0; i < polygon.length; i++) {
      reversed [i] = polygon [polygon.length - 1 - i];
    }
    return reversed;
  }

  static boolean isConvex(double [][] polygon) {
    int count = polygon.length;
    for (int i = 0; i < count; i++) {
      if (cross(polygon [i], polygon [(i + 1) % count], polygon [(i + 2) % count]) < -1E-6) {
        return false;
      }
    }
    return true;
  }

  static double [] getCenter(double [][] polygon) {
    double [] center = new double [2];
    for (double [] point : polygon) {
      center [0] += point [0] / polygon.length;
      center [1] += point [1] / polygon.length;
    }
    return center;
  }

  /**
   * Returns <code>true</code> if a point is in a convex polygon whose inside is at the left of its sides.
   */
  static boolean contains(double [][] polygon, double x, double y, double tolerance) {
    for (int i = 0; i < polygon.length; i++) {
      double [] point = polygon [i];
      double [] next = polygon [(i + 1) % polygon.length];
      double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
      if (length > 0
          && (-(next [1] - point [1]) * (x - point [0]) + (next [0] - point [0]) * (y - point [1])) / length < -tolerance) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns <code>true</code> if a point is in a polygon, convex or not.
   */
  static boolean containsPoint(double [][] polygon, double x, double y) {
    boolean inside = false;
    for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      if ((polygon [i][1] > y) != (polygon [j][1] > y)
          && x < (polygon [j][0] - polygon [i][0]) * (y - polygon [i][1]) / (polygon [j][1] - polygon [i][1]) + polygon [i][0]) {
        inside = !inside;
      }
    }
    return inside;
  }

  /**
   * Returns the part of a convex polygon where <code>nx * x + ny * y >= c</code>.
   */
  static double [][] clip(double [][] polygon, double nx, double ny, double c) {
    List<double []> points = new ArrayList<double []>(polygon.length + 2);
    for (int i = 0; i < polygon.length; i++) {
      double [] point = polygon [i];
      double [] next = polygon [(i + 1) % polygon.length];
      double distance = nx * point [0] + ny * point [1] - c;
      double nextDistance = nx * next [0] + ny * next [1] - c;
      if (distance >= 0) {
        points.add(point);
      }
      if (distance > 0 && nextDistance < 0 || distance < 0 && nextDistance > 0) {
        double t = distance / (distance - nextDistance);
        points.add(new double [] {point [0] + t * (next [0] - point [0]), point [1] + t * (next [1] - point [1])});
      }
    }
    // Without repeated points
    for (int i = points.size() - 1; i >= 0 && points.size() > 1; i--) {
      double [] next = points.get((i + 1) % points.size());
      if (Math.abs(points.get(i) [0] - next [0]) < 1E-7 && Math.abs(points.get(i) [1] - next [1]) < 1E-7) {
        points.remove(i);
      }
    }
    return points.toArray(new double [points.size()][]);
  }

  /**
   * Returns the part of a convex polygon where <code>plane</code> is lower
   * than <code>other</code>, both given as {c, cx, cy}.
   * @param lowerIfSame <code>true</code> if a plane equal to the other one is considered as lower
   */
  static double [][] clipLower(double [][] polygon, double [] plane, double [] other, boolean lowerIfSame) {
    double nx = other [1] - plane [1];
    double ny = other [2] - plane [2];
    double c = plane [0] - other [0];
    if (Math.abs(nx) < EPSILON && Math.abs(ny) < EPSILON) {
      // Parallel planes
      if (c < -1E-4 || Math.abs(c) <= 1E-4 && lowerIfSame) {
        return polygon;
      } else {
        return new double [0][];
      }
    }
    return clip(polygon, nx, ny, c);
  }

  static Rectangle2D getBounds(double [][] polygon) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (double [] point : polygon) {
      minX = Math.min(minX, point [0]);
      minY = Math.min(minY, point [1]);
      maxX = Math.max(maxX, point [0]);
      maxY = Math.max(maxY, point [1]);
    }
    return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
  }

  static float [][] toFloats(double [][] polygon) {
    float [][] points = new float [polygon.length][];
    for (int i = 0; i < polygon.length; i++) {
      points [i] = new float [] {(float)polygon [i][0], (float)polygon [i][1]};
    }
    return points;
  }

  static double [][] toDoubles(float [][] polygon) {
    double [][] points = new double [polygon.length][];
    for (int i = 0; i < polygon.length; i++) {
      points [i] = new double [] {polygon [i][0], polygon [i][1]};
    }
    return points;
  }
}
