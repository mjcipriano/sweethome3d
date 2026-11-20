/*
 * AssistantRoofModel.java
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import com.eteks.sweethome3d.model.TextureImage;

/**
 * A roof described by the parameters of the <code>add_roof</code> command:
 * its shape, and its 3D model with its fascia, gable ends, dormers, gutters...
 * Heights are measured from the floor of the level of the roof.
 * @author Sweet Home 3D
 */
final class AssistantRoofModel {
  static final int   MAX_POINTS = 80;
  static final float DEFAULT_PITCH = 30;
  static final float DEFAULT_OVERHANG = 30;
  static final float DEFAULT_THICKNESS = 15;
  static final int   DEFAULT_COLOR = 0x8C4A3C;

  private final AssistantRoofGeometry geometry = new AssistantRoofGeometry();
  private final List<String>          notes = new ArrayList<String>();
  private final List<float []>        windows = new ArrayList<float []>();
  private double [][]                 points;
  private int []                      edgeTypes;
  private double []                   edgeSlopes;
  private double []                   edgeOverhangs;
  private double                      eaveHeight;
  private double                      thickness;
  private int                         dormerCount;

  private AssistantRoofModel() {
  }

  AssistantRoofGeometry getGeometry() {
    return this.geometry;
  }

  /**
   * Returns the remarks about the parameters that were ignored.
   */
  List<String> getNotes() {
    return this.notes;
  }

  /**
   * Returns the windows of the dormers, each one as {x, y, angle in radians,
   * height of its bottom, width, height}.
   */
  List<float []> getWindows() {
    return this.windows;
  }

  int getDormerCount() {
    return this.dormerCount;
  }

  double getEaveHeight() {
    return this.eaveHeight;
  }

  int getEdgeCount() {
    return this.points.length;
  }

  boolean isEdgeSloped(int edge) {
    return this.edgeTypes [edge] == AssistantRoofGeometry.HIP;
  }

  /**
   * Returns the shape of the roof described by <code>spec</code>.
   * @throws IOException if the description can't give a roof
   */
  static AssistantRoofModel create(Map<?, ?> spec) throws IOException {
    AssistantRoofModel roof = new AssistantRoofModel();
    roof.parse(spec);
    return roof;
  }

  private static double number(Map<?, ?> map, String key, double defaultValue) {
    return AssistantFurnitureBuilder.getNumber(map, key, (float)defaultValue);
  }

  private static String text(Map<?, ?> map, String key) {
    return map.get(key) != null ? map.get(key).toString().trim().toLowerCase(Locale.ENGLISH) : null;
  }

  private static boolean flag(Map<?, ?> map, String key, boolean defaultValue) {
    Object value = map.get(key);
    return value instanceof Boolean ? ((Boolean)value).booleanValue()
        : value != null ? "true".equalsIgnoreCase(value.toString()) : defaultValue;
  }

  private static int parseType(String type) {
    if (type == null) {
      return -1;
    } else if (type.equals("hip") || type.equals("hipped") || type.equals("eave") || type.equals("slope")) {
      return AssistantRoofGeometry.HIP;
    } else if (type.equals("gable") || type.equals("shed") || type.equals("flat") || type.equals("vertical")
               || type.equals("rake") || type.equals("none")) {
      return AssistantRoofGeometry.GABLE;
    } else {
      return -1;
    }
  }

  private void parse(Map<?, ?> spec) throws IOException {
    List<float []> floatPoints = AssistantFurnitureBuilder.getPoints(spec, "points", 2);
    List<double []> cleanPoints = new ArrayList<double []>();
    // The edge of the description each kept point starts
    List<Integer> edgeIndices = new ArrayList<Integer>();
    for (int i = 0; i < floatPoints.size(); i++) {
      float [] point = floatPoints.get(i);
      float [] next = floatPoints.get((i + 1) % floatPoints.size());
      if (Math.hypot(next [0] - point [0], next [1] - point [1]) > 0.5) {
        cleanPoints.add(new double [] {point [0], point [1]});
        edgeIndices.add(i);
      }
    }
    if (cleanPoints.size() < 3) {
      throw new IOException("\"points\" must list the 3 corners at least of the roof, like [[0,0],[800,0],[800,600],[0,600]]");
    } else if (cleanPoints.size() > MAX_POINTS) {
      throw new IOException("a roof has " + MAX_POINTS + " corners at most");
    }
    this.points = cleanPoints.toArray(new double [cleanPoints.size()][]);
    if (Math.abs(AssistantRoofGeometry.getSignedArea(this.points)) < 100) {
      throw new IOException("the corners of the roof enclose no area");
    }
    int count = this.points.length;
    double pitch = number(spec, "pitch", DEFAULT_PITCH);
    if (!(pitch >= 1 && pitch <= 80)) {
      this.notes.add("the pitch must be between 1 and 80 degrees: " + Math.round(DEFAULT_PITCH) + " was used");
      pitch = DEFAULT_PITCH;
    }
    double overhang = number(spec, "overhang", DEFAULT_OVERHANG);
    if (!(overhang >= 0 && overhang <= 300)) {
      overhang = DEFAULT_OVERHANG;
    }
    this.eaveHeight = number(spec, "eave_height", 250);
    this.thickness = Math.max(1, Math.min(100, number(spec, "thickness", DEFAULT_THICKNESS)));

    // The kind of each edge: the one of the description, or the one the type of the roof gives
    this.edgeTypes = getDefaultTypes(text(spec, "type"), text(spec, "ridge"), text(spec, "low_side"));
    this.edgeSlopes = new double [count];
    this.edgeOverhangs = new double [count];
    List<?> edges = spec.get("edges") instanceof List ? (List<?>)spec.get("edges") : null;
    for (int i = 0; i < count; i++) {
      double edgePitch = pitch;
      double edgeOverhang = overhang;
      Object edge = edges != null && edgeIndices.get(i) < edges.size() ? edges.get(edgeIndices.get(i)) : null;
      if (edge instanceof Map) {
        Map<?, ?> edgeSpec = (Map<?, ?>)edge;
        int type = parseType(text(edgeSpec, "type"));
        if (type >= 0) {
          this.edgeTypes [i] = type;
        } else if (edgeSpec.get("type") != null) {
          this.notes.add("unknown type \"" + edgeSpec.get("type") + "\" of the edge " + (edgeIndices.get(i) + 1)
              + " (hip, gable, shed or flat)");
        }
        edgePitch = number(edgeSpec, "pitch", pitch);
        if (!(edgePitch >= 1 && edgePitch <= 80)) {
          edgePitch = pitch;
        }
        edgeOverhang = number(edgeSpec, "overhang", overhang);
        if (!(edgeOverhang >= 0 && edgeOverhang <= 300)) {
          edgeOverhang = overhang;
        }
      } else if (edge instanceof String) {
        int type = parseType(((String)edge).trim().toLowerCase(Locale.ENGLISH));
        if (type >= 0) {
          this.edgeTypes [i] = type;
        }
      }
      this.edgeSlopes [i] = Math.tan(Math.toRadians(edgePitch));
      this.edgeOverhangs [i] = edgeOverhang;
    }
    if (edges != null && edges.size() != floatPoints.size()) {
      this.notes.add("\"edges\" has " + edges.size() + " items for " + floatPoints.size()
          + " edges (the edge 1 goes from the first point to the second one): the others got the default type");
    }

    // The parts of the roof
    boolean sloped = false;
    for (int type : this.edgeTypes) {
      sloped |= type == AssistantRoofGeometry.HIP;
    }
    double flatThickness = sloped ? this.thickness : Math.max(this.thickness, 20);
    boolean innerSides = false;
    for (double [][] polygon : AssistantRoofGeometry.getConvexParts(this.points)) {
      int sides = polygon.length;
      int [] types = new int [sides];
      double [] slopes = new double [sides];
      double [] overhangs = new double [sides];
      boolean partSloped = false;
      for (int side = 0; side < sides; side++) {
        int edge = getEdgeAlong(polygon [side], polygon [(side + 1) % sides]);
        if (edge < 0) {
          types [side] = AssistantRoofGeometry.INNER;
          innerSides = true;
        } else {
          types [side] = this.edgeTypes [edge];
          slopes [side] = this.edgeSlopes [edge];
          overhangs [side] = this.edgeOverhangs [edge];
          partSloped |= types [side] == AssistantRoofGeometry.HIP;
        }
      }
      this.geometry.addPart(new AssistantRoofGeometry.Part(polygon, types, slopes, overhangs,
          this.eaveHeight + (partSloped ? this.thickness : flatThickness),
          partSloped ? this.thickness : flatThickness, false));
    }
    if (this.geometry.getParts().isEmpty()) {
      throw new IOException("the corners of the roof enclose no area");
    }
    if (innerSides) {
      this.notes.add("the corners of this roof aren't all square: it was cut in parts that may meet with a step; "
          + "a roof made of one add_roof per wing gives a better result");
    }
    if (spec.get("dormers") instanceof List) {
      for (Object dormer : (List<?>)spec.get("dormers")) {
        if (dormer instanceof Map) {
          addDormer((Map<?, ?>)dormer);
        }
      }
    }
  }

  /**
   * Returns the kinds of the edges for a roof of the given type.
   */
  private int [] getDefaultTypes(String type, String ridge, String lowSide) throws IOException {
    int count = this.points.length;
    int [] types = new int [count];
    if (type == null || type.equals("hip") || type.equals("hipped") || type.equals("pyramid")) {
      return types;
    }
    double orientation = Math.signum(AssistantRoofGeometry.getSignedArea(this.points));
    double [] lengths = new double [count];
    boolean [] convex = new boolean [count];
    for (int i = 0; i < count; i++) {
      double [] previous = this.points [(i + count - 1) % count];
      double [] point = this.points [i];
      double [] next = this.points [(i + 1) % count];
      lengths [i] = Math.hypot(next [0] - point [0], next [1] - point [1]);
      convex [i] = orientation * ((point [0] - previous [0]) * (next [1] - point [1])
          - (point [1] - previous [1]) * (next [0] - point [0])) > 0;
    }
    if (type.equals("flat")) {
      java.util.Arrays.fill(types, AssistantRoofGeometry.GABLE);
    } else if (type.equals("shed") || type.equals("mono") || type.equals("lean_to") || type.equals("lean-to")) {
      // One slope rising from the low side
      int low = lowSide != null ? getEdgeFacing(lowSide, false) : -1;
      if (low < 0) {
        for (int i = 0; i < count; i++) {
          if (low < 0 || lengths [i] > lengths [low] + 0.5) {
            low = i;
          }
        }
      }
      java.util.Arrays.fill(types, AssistantRoofGeometry.GABLE);
      types [low] = AssistantRoofGeometry.HIP;
    } else if (type.equals("gable") || type.equals("pitched") || type.equals("saddle")) {
      // Gables at the ends of the wings: the edges that are a short side of one of the rectangles the roof
      // is made of, its ridge running along the long sides
      List<double [][]> parts = AssistantRoofGeometry.getConvexParts(this.points);
      boolean rectangles = true;
      for (double [][] part : parts) {
        rectangles &= part.length == 4
            && Math.abs((part [1][0] - part [0][0]) * (part [2][0] - part [1][0])
                + (part [1][1] - part [0][1]) * (part [2][1] - part [1][1])) < 1
            && Math.abs((part [3][0] - part [2][0]) * (part [0][0] - part [3][0])
                + (part [3][1] - part [2][1]) * (part [0][1] - part [3][1])) < 1;
      }
      for (int i = 0; i < count; i++) {
        double [] point = this.points [i];
        double [] next = this.points [(i + 1) % count];
        boolean end = convex [i] && convex [(i + 1) % count];
        if (ridge != null) {
          boolean alongX = Math.abs(next [0] - point [0]) >= Math.abs(next [1] - point [1]);
          end &= ridge.equals("x") ? !alongX : ridge.equals("y") ? alongX : false;
        } else if (rectangles) {
          boolean shortSide = false;
          for (double [][] part : parts) {
            for (int side = 0; side < 4; side++) {
              double [] start = part [side];
              double [] stop = part [(side + 1) % 4];
              double [] after = part [(side + 2) % 4];
              boolean same = Math.hypot(start [0] - point [0], start [1] - point [1]) < 0.6
                      && Math.hypot(stop [0] - next [0], stop [1] - next [1]) < 0.6
                  || Math.hypot(start [0] - next [0], start [1] - next [1]) < 0.6
                      && Math.hypot(stop [0] - point [0], stop [1] - point [1]) < 0.6;
              shortSide |= same && Math.hypot(after [0] - stop [0], after [1] - stop [1]) >= lengths [i] - 0.5;
            }
          }
          end &= shortSide;
        } else {
          end &= lengths [i] < lengths [(i + count - 1) % count] - 0.5 && lengths [i] < lengths [(i + 1) % count] - 0.5;
        }
        if (end) {
          types [i] = AssistantRoofGeometry.GABLE;
        }
      }
      // Two gables don't meet at a corner: the longest becomes an eave
      for (int i = 0; i < count; i++) {
        int next = (i + 1) % count;
        if (types [i] == AssistantRoofGeometry.GABLE && types [next] == AssistantRoofGeometry.GABLE) {
          types [lengths [i] > lengths [next] + 0.5 ? i : next] = AssistantRoofGeometry.HIP;
        }
      }
    } else {
      throw new IOException("the type of a roof is hip, gable, shed or flat");
    }
    return types;
  }

  /**
   * Returns the index of the edge of the roof along which the segment joining
   * two points lies, or -1.
   */
  private int getEdgeAlong(double [] start, double [] end) {
    int best = -1;
    double bestOverlap = 1;
    int count = this.points.length;
    for (int i = 0; i < count; i++) {
      double [] point = this.points [i];
      double [] next = this.points [(i + 1) % count];
      double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
      double ux = (next [0] - point [0]) / length;
      double uy = (next [1] - point [1]) / length;
      double startDistance = -uy * (start [0] - point [0]) + ux * (start [1] - point [1]);
      double endDistance = -uy * (end [0] - point [0]) + ux * (end [1] - point [1]);
      if (Math.abs(startDistance) < 0.6 && Math.abs(endDistance) < 0.6) {
        double t1 = ux * (start [0] - point [0]) + uy * (start [1] - point [1]);
        double t2 = ux * (end [0] - point [0]) + uy * (end [1] - point [1]);
        double overlap = Math.min(length, Math.max(t1, t2)) - Math.max(0, Math.min(t1, t2));
        if (overlap > bestOverlap) {
          bestOverlap = overlap;
          best = i;
        }
      }
    }
    return best;
  }

  /**
   * Returns the unit vector pointing to the inside of the roof from an edge.
   */
  private double [] getInwardNormal(int edge) {
    double [] point = this.points [edge];
    double [] next = this.points [(edge + 1) % this.points.length];
    double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
    double sign = Math.signum(AssistantRoofGeometry.getSignedArea(this.points));
    return new double [] {-sign * (next [1] - point [1]) / length, sign * (next [0] - point [0]) / length};
  }

  /**
   * Returns the edge that faces a cardinal point the most, or -1.
   * @param slopedOnly <code>true</code> to search among the edges under a slope
   */
  int getEdgeFacing(String side, boolean slopedOnly) {
    double dx = side.startsWith("e") ? 1 : side.startsWith("w") ? -1 : 0;
    double dy = side.startsWith("s") ? 1 : side.startsWith("n") ? -1 : 0;
    if (dx == 0 && dy == 0) {
      return -1;
    }
    int best = -1;
    double bestScore = 0;
    for (int i = 0; i < this.points.length; i++) {
      if (slopedOnly && this.edgeTypes [i] != AssistantRoofGeometry.HIP) {
        continue;
      }
      double [] normal = getInwardNormal(i);
      double [] next = this.points [(i + 1) % this.points.length];
      double facing = -normal [0] * dx - normal [1] * dy;
      double score = facing * Math.hypot(next [0] - this.points [i][0], next [1] - this.points [i][1]);
      if (facing > 0.7 && score > bestScore) {
        bestScore = score;
        best = i;
      }
    }
    return best;
  }

  // ---- Dormers ----

  private void addDormer(Map<?, ?> dormer) {
    int edge = -1;
    String side = text(dormer, "side");
    if (dormer.get("edge") instanceof Number) {
      edge = ((Number)dormer.get("edge")).intValue() - 1;
    } else if (side != null) {
      edge = getEdgeFacing(side, true);
    }
    if (edge < 0 || edge >= this.points.length || this.edgeTypes [edge] != AssistantRoofGeometry.HIP) {
      this.notes.add("a dormer was ignored: \"side\" must be north, south, east or west, or \"edge\" the number "
          + "of an edge, with a slope rising from it");
      return;
    }
    double [] start = this.points [edge];
    double [] end = this.points [(edge + 1) % this.points.length];
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    double ux = (end [0] - start [0]) / length;
    double uy = (end [1] - start [1]) / length;
    double [] normal = getInwardNormal(edge);
    double width = Math.max(40, Math.min(length - 20, number(dormer, "width", 120)));
    double height = Math.max(20, Math.min(400, number(dormer, "height", 110)));
    double setback = Math.max(0, number(dormer, "setback", 40));
    double position = dormer.get("offset") instanceof Number
        ? number(dormer, "offset", length / 2)
        : Math.max(0, Math.min(1, number(dormer, "position", 0.5))) * length;
    if (!(dormer.get("edge") instanceof Number) && (Math.abs(ux) >= Math.abs(uy) ? ux < 0 : uy < 0)) {
      // Along a side given by a cardinal point, places are measured from the west or from the north
      position = length - position;
    }
    position = Math.max(width / 2, Math.min(length - width / 2, position));
    String type = text(dormer, "type");
    if (type == null) {
      type = "gable";
    }
    double centerX = start [0] + ux * position + normal [0] * setback;
    double centerY = start [1] + uy * position + normal [1] * setback;
    double frontHeight = this.geometry.getHeight(centerX, centerY, false);
    if (Double.isNaN(frontHeight)) {
      this.notes.add("a dormer was ignored: its place isn't on the roof");
      return;
    }
    double dormerThickness = Math.min(this.thickness, 8);
    double base = frontHeight + height + dormerThickness;
    double mainSlope = this.edgeSlopes [edge];
    double pitch = number(dormer, "pitch", Double.NaN);
    boolean shed = type.equals("shed");
    double slope = pitch >= 1 && pitch <= 80 ? Math.tan(Math.toRadians(pitch))
        : shed ? Math.max(Math.tan(Math.toRadians(6)), mainSlope / 3) : mainSlope;
    if (shed && slope >= mainSlope) {
      slope = mainSlope / 3;
    }
    boolean flat = type.equals("flat");
    if (!type.equals("gable") && !type.equals("hip") && !shed && !flat) {
      this.notes.add("the type of a dormer is gable, shed, hip or flat: \"" + type + "\" was built as a gable");
      type = "gable";
    }
    // The dormer goes back until it meets the roof
    double depth = 0;
    double previousHeight = frontHeight;
    int falling = 0;
    for (double distance = 5; distance <= 1500; distance += 5) {
      double roofHeight = this.geometry.getHeight(centerX + normal [0] * distance, centerY + normal [1] * distance, false);
      double dormerHeight = flat ? base : shed ? base + slope * distance : base + slope * width / 2;
      depth = distance;
      if (Double.isNaN(roofHeight)) {
        depth = distance - 5;
        break;
      } else if (roofHeight >= dormerHeight) {
        depth = distance + 5;
        break;
      }
      falling = roofHeight < previousHeight - 0.01 ? falling + 1 : 0;
      if (falling >= 3) {
        break;
      }
      previousHeight = roofHeight;
    }
    if (depth < 20) {
      this.notes.add("a dormer was ignored: the roof is too small behind its place");
      return;
    }
    double overhang = Math.max(0, Math.min(60, number(dormer, "overhang", 10)));
    double [][] polygon = {
        {centerX - ux * width / 2, centerY - uy * width / 2},
        {centerX + ux * width / 2, centerY + uy * width / 2},
        {centerX + ux * width / 2 + normal [0] * depth, centerY + uy * width / 2 + normal [1] * depth},
        {centerX - ux * width / 2 + normal [0] * depth, centerY - uy * width / 2 + normal [1] * depth}};
    // Sides: front, a cheek, back, the other cheek
    int hip = AssistantRoofGeometry.HIP;
    int gable = AssistantRoofGeometry.GABLE;
    int [] types = flat ? new int [] {gable, gable, gable, gable}
        : shed ? new int [] {hip, gable, gable, gable}
        : type.equals("hip") ? new int [] {hip, hip, gable, hip}
        : new int [] {gable, hip, gable, hip};
    AssistantRoofGeometry.Part part = new AssistantRoofGeometry.Part(polygon, types,
        new double [] {slope, slope, slope, slope}, new double [] {overhang, overhang, 0, overhang},
        base, dormerThickness, true);
    this.geometry.addPart(part);
    this.dormerCount++;
    double windowWidth = number(dormer, "window_width", 0);
    double windowHeight = number(dormer, "window_height", 0);
    if (windowWidth > 0 && windowHeight > 0) {
      windowWidth = Math.min(windowWidth, width - 16);
      windowHeight = Math.min(windowHeight, height - 10);
      double sill = frontHeight + Math.max(4, (height - windowHeight) / 2);
      // The window faces the outside of the roof
      this.windows.add(new float [] {(float)centerX, (float)centerY, (float)Math.atan2(normal [0], -normal [1]),
          (float)sill, (float)windowWidth, (float)windowHeight, this.geometry.getParts().size() - 1});
    }
  }

  // ---- 3D model ----

  /**
   * Builds the 3D model of this roof.
   * @param groundHeight the height of the ground, where downspouts end
   */
  AssistantGeneratedModel build(Map<?, ?> spec, AssistantFurnitureBuilder.TextureFinder textureFinder,
                                float groundHeight) throws IOException {
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    Integer color = spec.get("color") != null ? AssistantCommandExecutor.parseColor(spec.get("color").toString()) : null;
    AssistantMeshBuilder.Material roofMaterial = mesh.getMaterial(color != null ? color : DEFAULT_COLOR);
    if ((spec.get("texture") != null || spec.get("texture_id") != null) && textureFinder != null) {
      TextureImage texture = textureFinder.findTexture(spec.get("texture") != null ? spec.get("texture").toString() : null,
          spec.get("texture_category") != null ? spec.get("texture_category").toString() : null,
          spec.get("texture_id") != null ? spec.get("texture_id").toString() : null);
      if (texture != null) {
        roofMaterial = mesh.getMaterial(texture, (float)number(spec, "texture_scale", 1),
            (float)Math.toRadians(number(spec, "texture_angle", 0)), 0, 0, color != null ? color : DEFAULT_COLOR);
      } else {
        this.notes.add("no texture \"" + spec.get("texture") + "\" was found: the color of the roof was used");
      }
    }
    AssistantMeshBuilder.Material gableMaterial = mesh.getMaterial(getColor(spec, "gable_color", 0xECE6DA));
    AssistantMeshBuilder.Material fasciaMaterial = flag(spec, "fascia", true)
        ? mesh.getMaterial(getColor(spec, "fascia_color", 0xF4F2EE)) : roofMaterial;
    AssistantMeshBuilder.Material undersideMaterial = mesh.getMaterial(getColor(spec, "underside_color", 0xE4E0D8));
    AssistantMeshBuilder.Material metalMaterial = mesh.getMaterial(getColor(spec, "gutter_color", 0x9A9C9E));

    // Top and underside
    List<AssistantRoofGeometry.Cell> cells = this.geometry.getCells(true);
    for (AssistantRoofGeometry.Cell cell : cells) {
      float [][] top = new float [cell.polygon.length][];
      float [][] bottom = new float [cell.polygon.length][];
      for (int i = 0; i < cell.polygon.length; i++) {
        double x = cell.polygon [i][0];
        double y = cell.polygon [i][1];
        double height = cell.getHeight(x, y);
        top [i] = new float [] {(float)x, (float)height, (float)y};
        bottom [i] = new float [] {(float)x, (float)(height - cell.part.getThickness()), (float)y};
      }
      mesh.addPolygon(top, roofMaterial, 0, 1, 0);
      mesh.addPolygon(bottom, undersideMaterial, 0, -1, 0);
    }

    // The band around the roof
    Area footprint = this.geometry.getFootprint();
    List<double []> gutters = new ArrayList<double []>();
    for (float [][] outline : AssistantMeshBuilder.getOutlines(footprint)) {
      for (int i = 0; i < outline.length; i++) {
        float [] point = outline [i];
        float [] next = outline [(i + 1) % outline.length];
        double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
        if (length < 0.05) {
          continue;
        }
        double nx = -(next [1] - point [1]) / length;
        double ny = (next [0] - point [0]) / length;
        if (!footprint.contains((point [0] + next [0]) / 2 + nx * 0.05, (point [1] + next [1]) / 2 + ny * 0.05)) {
          nx = -nx;
          ny = -ny;
        }
        // Measured a hair inside the roof
        double x1 = point [0] + nx * 0.02;
        double y1 = point [1] + ny * 0.02;
        double x2 = next [0] + nx * 0.02;
        double y2 = next [1] + ny * 0.02;
        for (AssistantRoofGeometry.Cell cell : cells) {
          double [] range = AssistantRoofGeometry.clipSegment(x1, y1, x2, y2, cell.polygon);
          if (range == null) {
            continue;
          }
          double ax = point [0] + (next [0] - point [0]) * range [0];
          double ay = point [1] + (next [1] - point [1]) * range [0];
          double bx = point [0] + (next [0] - point [0]) * range [1];
          double by = point [1] + (next [1] - point [1]) * range [1];
          double heightA = cell.getHeight(ax, ay);
          double heightB = cell.getHeight(bx, by);
          double cellThickness = cell.part.getThickness();
          mesh.addPolygon(new float [][] {
              {(float)ax, (float)(heightA - cellThickness), (float)ay}, {(float)bx, (float)(heightB - cellThickness), (float)by},
              {(float)bx, (float)heightB, (float)by}, {(float)ax, (float)heightA, (float)ay}},
              fasciaMaterial, (float)-nx, 0, (float)-ny);
          // An eave: a level edge the roof rises from
          if (!cell.part.isDormer() && Math.abs(heightA - heightB) < 0.3
              && cell.plane [1] * nx + cell.plane [2] * ny > 0.01 && Math.hypot(bx - ax, by - ay) > 30) {
            gutters.add(new double [] {ax, ay, bx, by, heightA - cellThickness, -nx, -ny});
          }
        }
      }
    }

    // The walls under gables
    if (flag(spec, "gable_walls", true)) {
      for (int edge = 0; edge < this.points.length; edge++) {
        if (this.edgeTypes [edge] != AssistantRoofGeometry.GABLE) {
          continue;
        }
        double [] point = this.points [edge];
        double [] next = this.points [(edge + 1) % this.points.length];
        double [] normal = getInwardNormal(edge);
        for (double [] piece : AssistantRoofGeometry.getProfile(point [0] + normal [0] * 0.02, point [1] + normal [1] * 0.02,
                 next [0] + normal [0] * 0.02, next [1] + normal [1] * 0.02, cells)) {
          addWall(mesh, point, next, piece [0], piece [1], this.eaveHeight, this.eaveHeight,
              piece [2] - piece [4], piece [3] - piece [4], normal, gableMaterial);
        }
      }
    }

    // The walls of dormers, from the roof up to their own roof
    List<AssistantRoofGeometry.Cell> mainCells = this.geometry.getCells(false);
    for (int partIndex = 0; partIndex < this.geometry.getParts().size(); partIndex++) {
      AssistantRoofGeometry.Part part = this.geometry.getParts().get(partIndex);
      if (!part.isDormer()) {
        continue;
      }
      List<AssistantRoofGeometry.Cell> dormerCells = part.getOwnCells();
      double [] center = AssistantRoofGeometry.getCenter(part.getPolygon());
      float [] window = null;
      for (float [] dormerWindow : this.windows) {
        if ((int)dormerWindow [6] == partIndex) {
          window = dormerWindow;
        }
      }
      double [][] polygon = part.getPolygon();
      for (int side = 0; side < polygon.length; side++) {
        double [] point = polygon [side];
        double [] next = polygon [(side + 1) % polygon.length];
        double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
        double [] normal = {-(next [1] - point [1]) / length, (next [0] - point [0]) / length};
        if (normal [0] * (center [0] - point [0]) + normal [1] * (center [1] - point [1]) < 0) {
          normal = new double [] {-normal [0], -normal [1]};
        }
        double x1 = point [0] + normal [0] * 0.02;
        double y1 = point [1] + normal [1] * 0.02;
        double x2 = next [0] + normal [0] * 0.02;
        double y2 = next [1] + normal [1] * 0.02;
        List<double []> bottoms = AssistantRoofGeometry.getProfile(x1, y1, x2, y2, mainCells);
        List<double []> tops = AssistantRoofGeometry.getProfile(x1, y1, x2, y2, dormerCells);
        // The side with the window is the one it stands in the middle of
        boolean front = window != null
            && Math.hypot((point [0] + next [0]) / 2 - window [0], (point [1] + next [1]) / 2 - window [1]) < 1;
        Area wall = front ? new Area() : null;
        TreeSet<Double> limits = new TreeSet<Double>();
        for (double [] piece : bottoms) {
          limits.add(piece [0]);
          limits.add(piece [1]);
        }
        for (double [] piece : tops) {
          limits.add(piece [0]);
          limits.add(piece [1]);
        }
        Double previous = null;
        for (Double limit : limits) {
          if (previous != null && (limit - previous) * length > 0.1) {
            double middle = (previous + limit) / 2;
            double [] bottom = getPiece(bottoms, middle);
            double [] top = getPiece(tops, middle);
            if (bottom != null && top != null) {
              double bottom1 = interpolate(bottom, previous) - bottom [4];
              double bottom2 = interpolate(bottom, limit) - bottom [4];
              double top1 = interpolate(top, previous) - top [4];
              double top2 = interpolate(top, limit) - top [4];
              if (wall != null) {
                if (top1 > bottom1 + 0.05 || top2 > bottom2 + 0.05) {
                  wall.add(AssistantMeshBuilder.getArea(new float [][] {
                      {(float)(previous * length), (float)bottom1}, {(float)(limit * length), (float)bottom2},
                      {(float)(limit * length), (float)Math.max(top2, bottom2)},
                      {(float)(previous * length), (float)Math.max(top1, bottom1)}}));
                }
              } else {
                addWall(mesh, point, next, previous, limit, bottom1, bottom2, top1, top2, normal, gableMaterial);
              }
            }
          }
          previous = limit;
        }
        if (wall != null) {
          // The wall around its window
          float middle = (float)(length / 2);
          wall.subtract(AssistantMeshBuilder.getArea(new float [][] {
              {middle - window [4] / 2, window [3]}, {middle + window [4] / 2, window [3]},
              {middle + window [4] / 2, window [3] + window [5]}, {middle - window [4] / 2, window [3] + window [5]}}));
          for (float [][] trapezoid : AssistantMeshBuilder.getTrapezoids(wall)) {
            float [][] wallPoints = new float [trapezoid.length][];
            for (int i = 0; i < trapezoid.length; i++) {
              double t = trapezoid [i][0] / length;
              wallPoints [i] = new float [] {(float)(point [0] + (next [0] - point [0]) * t), trapezoid [i][1],
                                             (float)(point [1] + (next [1] - point [1]) * t)};
            }
            mesh.addPolygon(wallPoints, gableMaterial, (float)-normal [0], 0, (float)-normal [1]);
            mesh.addPolygon(wallPoints, gableMaterial, (float)normal [0], 0, (float)normal [1]);
          }
        }
      }
    }

    if (flag(spec, "ridge_cap", false)) {
      addRidgeCaps(mesh, cells, roofMaterial);
    }
    if (flag(spec, "soffit", false)) {
      addSoffit(mesh, footprint, undersideMaterial);
    }
    boolean gutter = flag(spec, "gutters", false);
    if (gutter) {
      boolean downspouts = flag(spec, "downspouts", true);
      for (double [] eave : gutters) {
        // Hung under the edge of the roof
        double ox = eave [5] * 6;
        double oy = eave [6] * 6;
        float top = (float)eave [4] + 5;
        mesh.addBar(new float [] {(float)(eave [0] + ox), top, (float)(eave [1] + oy)},
            new float [] {(float)(eave [2] + ox), top, (float)(eave [3] + oy)}, 12, 10, metalMaterial);
        if (downspouts && Math.hypot(eave [2] - eave [0], eave [3] - eave [1]) > 100) {
          // A pipe from the end of the gutter back to the wall, then down to the ground
          double length = Math.hypot(eave [2] - eave [0], eave [3] - eave [1]);
          double ux = (eave [2] - eave [0]) / length;
          double uy = (eave [3] - eave [1]) / length;
          double [] wallPoint = getWallPoint(eave [0] + ux * 12, eave [1] + uy * 12, -eave [5], -eave [6]);
          float [] start = {(float)(eave [0] + ux * 12 + ox), top - 8, (float)(eave [1] + uy * 12 + oy)};
          float [] bend = {(float)(wallPoint [0] + eave [5] * 6), (float)Math.min(top - 12, this.eaveHeight - 10),
                           (float)(wallPoint [1] + eave [6] * 6)};
          mesh.addBar(start, bend, 7, 7, metalMaterial);
          mesh.addBar(bend, new float [] {bend [0], Math.min(groundHeight, bend [1] - 10), bend [2]}, 7, 7, metalMaterial);
        }
      }
    }
    return mesh.store("roof");
  }

  private static int getColor(Map<?, ?> spec, String key, int defaultColor) {
    Integer color = spec.get(key) != null ? AssistantCommandExecutor.parseColor(spec.get(key).toString()) : null;
    return color != null ? color.intValue() : defaultColor;
  }

  private static double [] getPiece(List<double []> profile, double t) {
    for (double [] piece : profile) {
      if (t >= piece [0] && t <= piece [1]) {
        return piece;
      }
    }
    return null;
  }

  private static double interpolate(double [] piece, double t) {
    return piece [1] - piece [0] < 1E-9 ? piece [2]
        : piece [2] + (piece [3] - piece [2]) * (t - piece [0]) / (piece [1] - piece [0]);
  }

  /**
   * Adds a vertical wall seen from both sides along a part of the segment
   * joining two points, where its top is above its bottom.
   */
  private static void addWall(AssistantMeshBuilder mesh, double [] start, double [] end, double t1, double t2,
                              double bottom1, double bottom2, double top1, double top2, double [] normal,
                              AssistantMeshBuilder.Material material) {
    if (top1 <= bottom1 + 0.05 && top2 <= bottom2 + 0.05) {
      return;
    }
    double gap1 = top1 - bottom1;
    double gap2 = top2 - bottom2;
    if (gap1 < 0) {
      // Starts where the top meets the bottom
      double t = t1 + (t2 - t1) * gap1 / (gap1 - gap2);
      bottom1 = top1 = bottom1 + (bottom2 - bottom1) * (t - t1) / (t2 - t1);
      t1 = t;
    } else if (gap2 < 0) {
      double t = t1 + (t2 - t1) * gap1 / (gap1 - gap2);
      bottom2 = top2 = bottom1 + (bottom2 - bottom1) * (t - t1) / (t2 - t1);
      t2 = t;
    }
    float x1 = (float)(start [0] + (end [0] - start [0]) * t1);
    float y1 = (float)(start [1] + (end [1] - start [1]) * t1);
    float x2 = (float)(start [0] + (end [0] - start [0]) * t2);
    float y2 = (float)(start [1] + (end [1] - start [1]) * t2);
    float [][] points = {{x1, (float)bottom1, y1}, {x2, (float)bottom2, y2}, {x2, (float)top2, y2}, {x1, (float)top1, y1}};
    mesh.addPolygon(points, material, (float)-normal [0], 0, (float)-normal [1]);
    mesh.addPolygon(points, material, (float)normal [0], 0, (float)normal [1]);
  }

  /**
   * Returns the point of the walls behind a point of the edge of the roof,
   * in the given direction.
   */
  private double [] getWallPoint(double x, double y, double dx, double dy) {
    for (double distance = 0; distance <= 320; distance += 2) {
      if (AssistantRoofGeometry.containsPoint(this.points, x + dx * distance, y + dy * distance)) {
        return new double [] {x + dx * distance, y + dy * distance};
      }
    }
    return new double [] {x, y};
  }

  /**
   * Adds a cap along the ridges and the hips of the roof.
   */
  private void addRidgeCaps(AssistantMeshBuilder mesh, List<AssistantRoofGeometry.Cell> cells,
                            AssistantMeshBuilder.Material material) {
    // The sides of the pieces of the roof where a slope meets a lower one going down the other way
    for (int i = 0; i < cells.size(); i++) {
      AssistantRoofGeometry.Cell cell = cells.get(i);
      for (int side = 0; side < cell.polygon.length; side++) {
        double [] point = cell.polygon [side];
        double [] next = cell.polygon [(side + 1) % cell.polygon.length];
        double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
        if (length < 5) {
          continue;
        }
        // Toward the outside of the piece
        double nx = (next [1] - point [1]) / length;
        double ny = -(next [0] - point [0]) / length;
        double middleX = (point [0] + next [0]) / 2;
        double middleY = (point [1] + next [1]) / 2;
        double height = cell.getHeight(middleX, middleY);
        for (int j = i + 1; j < cells.size(); j++) {
          AssistantRoofGeometry.Cell other = cells.get(j);
          if (other.plane != cell.plane
              && AssistantRoofGeometry.contains(other.polygon, middleX + nx * 0.05, middleY + ny * 0.05, 0)
              && Math.abs(other.getHeight(middleX, middleY) - height) < 0.2
              // Both slopes go down from the side
              && cell.plane [1] * nx + cell.plane [2] * ny > 0.01
              && other.plane [1] * nx + other.plane [2] * ny < -0.01) {
            mesh.addBar(new float [] {(float)point [0], (float)cell.getHeight(point [0], point [1]) + 3, (float)point [1]},
                new float [] {(float)next [0], (float)cell.getHeight(next [0], next [1]) + 3, (float)next [1]},
                18, 5, material);
            break;
          }
        }
      }
    }
  }

  /**
   * Adds a level board under the overhang of the eaves.
   */
  private void addSoffit(AssistantMeshBuilder mesh, Area footprint, AssistantMeshBuilder.Material material) {
    Area strips = new Area();
    double lowest = Double.POSITIVE_INFINITY;
    for (int edge = 0; edge < this.points.length; edge++) {
      double overhang = this.edgeOverhangs [edge];
      if (this.edgeTypes [edge] != AssistantRoofGeometry.HIP || overhang < 5) {
        continue;
      }
      double [] point = this.points [edge];
      double [] next = this.points [(edge + 1) % this.points.length];
      double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
      double ux = (next [0] - point [0]) / length;
      double uy = (next [1] - point [1]) / length;
      double [] normal = getInwardNormal(edge);
      strips.add(AssistantMeshBuilder.getArea(new float [][] {
          {(float)(point [0] - ux * overhang), (float)(point [1] - uy * overhang)},
          {(float)(next [0] + ux * overhang), (float)(next [1] + uy * overhang)},
          {(float)(next [0] + ux * overhang - normal [0] * overhang), (float)(next [1] + uy * overhang - normal [1] * overhang)},
          {(float)(point [0] - ux * overhang - normal [0] * overhang), (float)(point [1] - uy * overhang - normal [1] * overhang)}}));
      lowest = Math.min(lowest, this.eaveHeight - this.edgeSlopes [edge] * overhang);
    }
    if (lowest == Double.POSITIVE_INFINITY) {
      return;
    }
    strips.intersect(footprint);
    strips.subtract(AssistantMeshBuilder.getArea(AssistantRoofGeometry.toFloats(this.points)));
    for (float [][] trapezoid : AssistantMeshBuilder.getTrapezoids(strips)) {
      float [][] board = new float [trapezoid.length][];
      for (int i = 0; i < trapezoid.length; i++) {
        board [i] = new float [] {trapezoid [i][0], (float)lowest - 0.5f, trapezoid [i][1]};
      }
      mesh.addPolygon(board, material, 0, -1, 0);
    }
  }
}
