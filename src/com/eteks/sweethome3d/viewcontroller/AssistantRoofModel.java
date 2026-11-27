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
  private final List<String>          windowNames = new ArrayList<String>();
  private final List<String>          dormerDescriptions = new ArrayList<String>();
  private final List<Integer>         unbuiltDormers = new ArrayList<Integer>();
  // The middle of the front of the dormers that stand at a plane where the roof is clipped
  private final Map<AssistantRoofGeometry.Part, double []> clippedFronts =
      new java.util.IdentityHashMap<AssistantRoofGeometry.Part, double []>();
  // How far the front of a dormer goes back to stand at the face of a wall that clips the roof
  private static final double MAX_DORMER_SHIFT = 150;
  private final List<double []>       dormerPlaces = new ArrayList<double []>();
  private boolean []                  edgeAbuts;
  private boolean []                  edgeClips;
  private Boolean []                  edgeGutters;
  private Boolean []                  edgeFascias;
  private double [][][]               edgeGutterSpans;
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
   * height of its bottom, width, height, index of the part of its dormer,
   * index of its dormer in the description of the roof, index of the window in its dormer}.
   */
  List<float []> getWindows() {
    return this.windows;
  }

  /**
   * Returns the names of the catalog windows of {@link #getWindows()}, <code>null</code> for the ones without name.
   */
  List<String> getWindowNames() {
    return this.windowNames;
  }

  /**
   * Returns a line about each dormer of the description, built or not, with its number.
   */
  /**
   * Returns the numbers, from 1, of the dormers of the description that couldn't be built.
   */
  List<Integer> getUnbuiltDormers() {
    return this.unbuiltDormers;
  }

  List<String> getDormerDescriptions() {
    return this.dormerDescriptions;
  }

  /**
   * Returns the place of each dormer of the description, built or not: {x, y, nx, ny} where (x, y) is the
   * point of the edge of the roof in front of the center of the dormer and (nx, ny) the direction the
   * dormer looks at, or <code>null</code> for a dormer that has no place. A dormer keeps this place
   * when the corners or the edges of its roof change.
   */
  List<double []> getDormerPlaces() {
    return this.dormerPlaces;
  }

  /**
   * Returns the type of an edge as descriptions give it: hip, gable, abut or clip.
   */
  String getEdgeTypeName(int edge) {
    return this.edgeAbuts [edge] ? "abut" : this.edgeClips [edge] ? "clip"
        : this.edgeTypes [edge] == AssistantRoofGeometry.HIP ? "hip" : "gable";
  }

  /**
   * Returns the shape of the roof at an edge: hip under a slope rising from it, gable otherwise.
   */
  String getEdgeShapeName(int edge) {
    return this.edgeTypes [edge] == AssistantRoofGeometry.HIP ? "hip" : "gable";
  }

  /**
   * Returns <code>true</code> if the roof stops at the vertical plane of an edge, along a taller wall.
   */
  boolean isEdgeClipped(int edge) {
    return this.edgeClips [edge] || this.edgeAbuts [edge];
  }

  /**
   * Returns the corners of the roof.
   */
  double [][] getPoints() {
    return this.points;
  }

  /**
   * Returns the choice of the description for the gutter of an edge, or <code>null</code> if it has none.
   */
  Boolean getEdgeGutter(int edge) {
    return this.edgeGutters [edge];
  }

  /**
   * Returns the spans of the gutter of an edge, or <code>null</code> if it runs all along it.
   */
  double [][] getEdgeGutterSpans(int edge) {
    return this.edgeGutterSpans [edge];
  }

  double getEdgePitch(int edge) {
    return Math.toDegrees(Math.atan(this.edgeSlopes [edge]));
  }

  double getEdgeOverhang(int edge) {
    return this.edgeOverhangs [edge];
  }

  /**
   * Returns the length of an edge.
   */
  double getEdgeLength(int edge) {
    double [] point = this.points [edge];
    double [] next = this.points [(edge + 1) % this.points.length];
    return Math.hypot(next [0] - point [0], next [1] - point [1]);
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
    } else if (type.equals("hip") || type.equals("hipped") || type.equals("eave") || type.equals("slope")
               || isClip(type)) {
      return AssistantRoofGeometry.HIP;
    } else if (type.equals("gable") || type.equals("shed") || type.equals("flat") || type.equals("vertical")
               || type.equals("rake") || type.equals("none") || isAbut(type)) {
      return AssistantRoofGeometry.GABLE;
    } else {
      return -1;
    }
  }

  /**
   * Returns <code>true</code> for the type of an edge along the wall of a taller part of the building:
   * the roof runs up to that wall, without overhang and without a gable wall of its own.
   */
  private static boolean isAbut(String type) {
    return type.equals("abut") || type.equals("abutting") || type.equals("wall") || type.equals("against_wall");
  }

  /**
   * Returns <code>true</code> for the type of an edge where the roof is clipped by the wall of a taller
   * part of the building without changing shape: the roof is the one its type, or the shape given by
   * <code>clip_of</code> (hip or gable), gives at that edge, and it stops at the vertical plane of the edge,
   * without overhang, fascia, soffit, gutter nor gable wall there.
   */
  static boolean isClip(String type) {
    return type.equals("clip") || type.equals("clipped") || type.equals("trim") || type.equals("cut");
  }

  /**
   * Returns the spans {from, to} in cm of a list like [[0,320],[700,1447]], or <code>null</code>.
   */
  private static double [][] parseSpans(Object value) {
    if (!(value instanceof List)) {
      return null;
    }
    List<double []> spans = new ArrayList<double []>();
    for (Object span : (List<?>)value) {
      if (span instanceof List && ((List<?>)span).size() >= 2
          && ((List<?>)span).get(0) instanceof Number && ((List<?>)span).get(1) instanceof Number) {
        double from = ((Number)((List<?>)span).get(0)).doubleValue();
        double to = ((Number)((List<?>)span).get(1)).doubleValue();
        if (to > from) {
          spans.add(new double [] {from, to});
        }
      }
    }
    return spans.toArray(new double [spans.size()][]);
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
    this.edgeAbuts = new boolean [count];
    this.edgeClips = new boolean [count];
    this.edgeGutters = new Boolean [count];
    this.edgeFascias = new Boolean [count];
    this.edgeGutterSpans = new double [count][][];
    List<?> edges = spec.get("edges") instanceof List ? (List<?>)spec.get("edges") : null;
    for (int i = 0; i < count; i++) {
      double edgePitch = pitch;
      double edgeOverhang = overhang;
      Object edge = edges != null && edgeIndices.get(i) < edges.size() ? edges.get(edgeIndices.get(i)) : null;
      if (edge instanceof Map) {
        Map<?, ?> edgeSpec = (Map<?, ?>)edge;
        int type = parseType(text(edgeSpec, "type"));
        if (type >= 0 && isClip(text(edgeSpec, "type"))) {
          // The roof keeps the shape it has at that edge: the one "clip_of" tells, otherwise the one of the type of the roof
          this.edgeClips [i] = true;
          String shape = text(edgeSpec, "clip_of");
          if (shape != null && !isClip(shape) && !isAbut(shape) && parseType(shape) >= 0) {
            this.edgeTypes [i] = parseType(shape);
          } else if (shape != null) {
            this.notes.add("\"clip_of\":\"" + edgeSpec.get("clip_of") + "\" of the edge " + (edgeIndices.get(i) + 1)
                + " was ignored: it's hip or gable, the shape the roof has at an edge where it's clipped");
          }
        } else if (type >= 0) {
          this.edgeTypes [i] = type;
          this.edgeAbuts [i] = isAbut(text(edgeSpec, "type"));
        } else if (edgeSpec.get("type") != null) {
          this.notes.add("unknown type \"" + edgeSpec.get("type") + "\" of the edge " + (edgeIndices.get(i) + 1)
              + " (hip, gable, shed, flat, abut or clip)");
        }
        edgePitch = number(edgeSpec, "pitch", pitch);
        if (!(edgePitch >= 1 && edgePitch <= 80)) {
          this.notes.add("the pitch " + format(number(edgeSpec, "pitch", pitch)) + " of the edge " + (edgeIndices.get(i) + 1)
              + " isn't between 1 and 80 degrees: " + Math.round(pitch) + " was used");
          edgePitch = pitch;
        }
        edgeOverhang = number(edgeSpec, "overhang", this.edgeAbuts [i] ? 0 : overhang);
        if (!(edgeOverhang >= 0 && edgeOverhang <= 300)) {
          this.notes.add("the overhang " + format(number(edgeSpec, "overhang", overhang)) + " of the edge " + (edgeIndices.get(i) + 1)
              + " isn't between 0 and 300 cm: " + Math.round(overhang) + " was used");
          edgeOverhang = overhang;
        }
        if (this.edgeClips [i]) {
          // The roof stops at the plane of the edge
          edgeOverhang = 0;
        }
        if (edgeSpec.get("gutter") != null) {
          this.edgeGutters [i] = Boolean.valueOf(flag(edgeSpec, "gutter", true));
        }
        if (edgeSpec.get("fascia") != null) {
          this.edgeFascias [i] = Boolean.valueOf(flag(edgeSpec, "fascia", true));
        }
        this.edgeGutterSpans [i] = parseSpans(edgeSpec.get("gutter_spans"));
      } else if (edge instanceof String) {
        String edgeType = ((String)edge).trim().toLowerCase(Locale.ENGLISH);
        int type = parseType(edgeType);
        if (type >= 0 && isClip(edgeType)) {
          this.edgeClips [i] = true;
          edgeOverhang = 0;
        } else if (type >= 0) {
          this.edgeTypes [i] = type;
          this.edgeAbuts [i] = isAbut(edgeType);
          if (this.edgeAbuts [i]) {
            edgeOverhang = 0;
          }
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
    // What the walls of a taller part of the building cut out of the roof
    if (spec.get("clip_areas") instanceof List) {
      for (Object area : (List<?>)spec.get("clip_areas")) {
        if (area instanceof List) {
          List<double []> areaPoints = new ArrayList<double []>();
          for (Object point : (List<?>)area) {
            if (point instanceof List && ((List<?>)point).size() >= 2
                && ((List<?>)point).get(0) instanceof Number && ((List<?>)point).get(1) instanceof Number) {
              areaPoints.add(new double [] {((Number)((List<?>)point).get(0)).doubleValue(),
                                            ((Number)((List<?>)point).get(1)).doubleValue()});
            }
          }
          this.geometry.addCut(areaPoints.toArray(new double [areaPoints.size()][]));
        }
      }
      if (this.geometry.getCells(false).isEmpty()) {
        throw new IOException("nothing remains of the roof once clipped");
      }
    }
    if (spec.get("dormers") instanceof List) {
      int index = 0;
      for (Object dormer : (List<?>)spec.get("dormers")) {
        while (this.dormerPlaces.size() <= index) {
          this.dormerPlaces.add(null);
        }
        if (dormer instanceof Map) {
          int noteCount = this.notes.size();
          String description = addDormer((Map<?, ?>)dormer, index);
          if (description == null) {
            this.unbuiltDormers.add(Integer.valueOf(index + 1));
          }
          this.dormerDescriptions.add((index + 1) + ": " + (description != null ? description
              : "NOT BUILT - " + (this.notes.size() > noteCount ? this.notes.get(this.notes.size() - 1) : "")));
        }
        index++;
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

  /**
   * Adds a dormer, and returns its description or <code>null</code> if it can't be built.
   */
  private String addDormer(Map<?, ?> dormer, int dormerIndex) {
    int edge = -1;
    String side = text(dormer, "side");
    // The place the dormer had the last time its roof was built: it stays on that side, at that point
    double [] place = getPlace(dormer.get("at"));
    double placePosition = Double.NaN;
    if (place != null) {
      double bestDistance = Double.POSITIVE_INFINITY;
      for (int i = 0; i < this.points.length; i++) {
        double [] edgeNormal = getInwardNormal(i);
        double [] next = this.points [(i + 1) % this.points.length];
        double distance = java.awt.geom.Line2D.ptSegDist(this.points [i][0], this.points [i][1], next [0], next [1],
            place [0], place [1]);
        if (-edgeNormal [0] * place [2] - edgeNormal [1] * place [3] > 0.7 && distance < bestDistance) {
          bestDistance = distance;
          edge = i;
        }
      }
      if (edge >= 0) {
        double [] next = this.points [(edge + 1) % this.points.length];
        double edgeLength = Math.hypot(next [0] - this.points [edge][0], next [1] - this.points [edge][1]);
        placePosition = ((place [0] - this.points [edge][0]) * (next [0] - this.points [edge][0])
            + (place [1] - this.points [edge][1]) * (next [1] - this.points [edge][1])) / edgeLength;
        if (this.edgeTypes [edge] != AssistantRoofGeometry.HIP) {
          this.dormerPlaces.set(dormerIndex, place);
          this.notes.add("the dormer " + (dormerIndex + 1) + " wasn't built: the edge " + (edge + 1)
              + " it stands on is now a " + getEdgeTypeName(edge) + ", without a slope rising from an eave "
              + "(modify_dormer with \"side\" or \"edge\" moves it to another edge, remove_dormer removes it)");
          return null;
        }
      }
    }
    if (edge >= 0) {
      // Placed above
    } else if (dormer.get("edge") instanceof Number) {
      edge = ((Number)dormer.get("edge")).intValue() - 1;
    } else if (side != null) {
      edge = getEdgeFacing(side, true);
    }
    if (edge < 0 || edge >= this.points.length || this.edgeTypes [edge] != AssistantRoofGeometry.HIP) {
      this.notes.add("a dormer was ignored: \"side\" must be north, south, east or west, or \"edge\" the number "
          + "of an edge, with a slope rising from it");
      return null;
    }
    double [] start = this.points [edge];
    double [] end = this.points [(edge + 1) % this.points.length];
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    double ux = (end [0] - start [0]) / length;
    double uy = (end [1] - start [1]) / length;
    double [] normal = getInwardNormal(edge);
    double width = Math.max(40, Math.min(length - 20, number(dormer, "width", 120)));
    if (Math.abs(width - number(dormer, "width", 120)) > 0.01) {
      this.notes.add("the width " + format(number(dormer, "width", 120)) + " of the dormer " + (dormerIndex + 1)
          + " was changed to " + format(width) + ": a dormer is 40 cm wide at least and 20 cm shorter than its edge at most");
    }
    double height = Math.max(20, Math.min(400, number(dormer, "height", 110)));
    if (Math.abs(height - number(dormer, "height", 110)) > 0.01) {
      this.notes.add("the height " + format(number(dormer, "height", 110)) + " of the dormer " + (dormerIndex + 1)
          + " was changed to " + format(height) + ": the front wall of a dormer is 20 to 400 cm high");
    }
    double setback = Math.max(0, number(dormer, "setback", 40));
    if (number(dormer, "setback", 40) < 0) {
      this.notes.add("the setback " + format(number(dormer, "setback", 40)) + " of the dormer " + (dormerIndex + 1)
          + " was changed to 0: a dormer can't stand in front of its edge");
    }
    // "offset" is a distance in cm to the center of the dormer, "position" a part of the side from 0 to 1;
    // a position larger than 1 can only be a distance
    double position;
    if (dormer.get("offset") instanceof Number) {
      position = number(dormer, "offset", length / 2);
    } else if (dormer.get("position") instanceof Number && number(dormer, "position", 0.5) > 1) {
      position = number(dormer, "position", length / 2);
      this.notes.add("\"position\":" + Math.round(position) + " of a dormer was taken as a distance in cm, as \"offset\": "
          + "\"position\" goes from 0 to 1");
    } else {
      position = Math.max(0, Math.min(1, number(dormer, "position", 0.5))) * length;
    }
    if (!Double.isNaN(placePosition)) {
      position = placePosition;
    } else if (!(dormer.get("edge") instanceof Number) && (Math.abs(ux) >= Math.abs(uy) ? ux < 0 : uy < 0)) {
      // Along a side given by a cardinal point, places are measured from the west or from the north
      position = length - position;
    }
    double askedPosition = position;
    position = Math.max(width / 2, Math.min(length - width / 2, position));
    if (Math.abs(position - askedPosition) > 0.5) {
      this.notes.add("the dormer " + (dormerIndex + 1) + " was moved from " + format(askedPosition) + " to "
          + format(position) + " cm along its edge to stand entirely on it");
    }
    this.dormerPlaces.set(dormerIndex, new double [] {start [0] + ux * position, start [1] + uy * position,
        -normal [0], -normal [1]});
    String type = text(dormer, "type");
    if (type == null) {
      type = "gable";
    }
    double centerX = start [0] + ux * position + normal [0] * setback;
    double centerY = start [1] + uy * position + normal [1] * setback;
    // The front of a dormer may be at the plane where the roof is clipped: the wall that clips the roof closes it
    boolean clippedEdge = this.edgeClips [edge] && setback < 0.5;
    // On the line where the roof ends, its front stands a hair inside the roof
    double shift = 0;
    while (shift <= 1 && Double.isNaN(this.geometry.getUncutHeight(centerX + normal [0] * shift, centerY + normal [1] * shift))) {
      shift += 0.05;
    }
    if (shift > 1) {
      this.notes.add("a dormer was ignored: its place isn't on the roof");
      return null;
    }
    if (this.geometry.hasCut()) {
      // In what walls cut out of the roof, its front goes back to the face of the wall the closest to its edge:
      // behind the other ones, the dormer is cut with the roof
      double cutShift = Double.POSITIVE_INFINITY;
      int samples = (int)Math.max(3, Math.min(60, Math.ceil(width / 15)));
      for (int i = 0; i <= samples && cutShift > shift; i++) {
        double along = -width / 2 + 1 + (width - 2) * i / samples;
        double x = centerX + ux * along;
        double y = centerY + uy * along;
        double distance = shift;
        while (distance < cutShift && distance <= MAX_DORMER_SHIFT
               && Double.isNaN(this.geometry.getHeight(x + normal [0] * distance, y + normal [1] * distance, false))) {
          distance += 0.25;
        }
        if (distance <= MAX_DORMER_SHIFT) {
          cutShift = Math.min(cutShift, distance);
        }
      }
      if (cutShift == Double.POSITIVE_INFINITY) {
        this.notes.add("a dormer was ignored: its place is in what walls clip out of the roof, more than "
            + Math.round(MAX_DORMER_SHIFT) + " cm deep");
        return null;
      }
      if (cutShift > shift) {
        // Found within a quarter of centimeter: at the face itself
        double distance = cutShift - 0.25;
        while (distance < cutShift) {
          boolean covered = false;
          for (int i = 0; i <= samples && !covered; i++) {
            double along = -width / 2 + 1 + (width - 2) * i / samples;
            covered = !Double.isNaN(this.geometry.getHeight(centerX + ux * along + normal [0] * distance,
                centerY + uy * along + normal [1] * distance, false));
          }
          if (covered) {
            break;
          }
          distance += 0.01;
        }
        shift = Math.min(cutShift, distance);
        if (shift > 1) {
          this.notes.add("the front of the dormer " + (dormerIndex + 1) + " stands " + format(Math.round((setback + shift) * 10) / 10.)
              + " cm back from its edge, at the face of the wall that clips the roof there");
        }
      }
    }
    setback += shift;
    centerX += normal [0] * shift;
    centerY += normal [1] * shift;
    double frontHeight = this.geometry.getUncutHeight(centerX, centerY);
    if (Double.isNaN(frontHeight)) {
      this.notes.add("a dormer was ignored: its place isn't on the roof");
      return null;
    }
    // Where a wall that clips the roof stands in front of it
    boolean clippedFront = clippedEdge
        || this.geometry.isCut(centerX - normal [0] * 0.3, centerY - normal [1] * 0.3);
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
    if ("match".equals(text(dormer, "ridge")) && !flat && !shed) {
      // A cross gable: its ridge is as high as the top of the roof
      double rise = this.geometry.getMaxHeight() - base;
      if (rise > 1) {
        slope = Math.max(Math.tan(Math.toRadians(1)), Math.min(Math.tan(Math.toRadians(80)), rise / (width / 2)));
      } else {
        this.notes.add("\"ridge\":\"match\" was ignored for a dormer whose walls are as high as the top of the roof");
      }
    }
    if (!type.equals("gable") && !type.equals("hip") && !shed && !flat) {
      this.notes.add("the type of a dormer is gable, shed, hip or flat: \"" + type + "\" was built as a gable");
      type = "gable";
    }
    // The dormer goes back until it meets the roof
    double depth = 0;
    double previousHeight = frontHeight;
    int falling = 0;
    for (double distance = 5; distance <= 1500; distance += 5) {
      // Over the roof as it is under what walls cut out of it: the dormer is cut with it
      double roofHeight = this.geometry.getUncutHeight(centerX + normal [0] * distance, centerY + normal [1] * distance);
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
      return null;
    }
    double overhang = Math.max(0, Math.min(60, number(dormer, "overhang", 10)));
    if (Math.abs(overhang - number(dormer, "overhang", 10)) > 0.01) {
      this.notes.add("the overhang " + format(number(dormer, "overhang", 10)) + " of the dormer " + (dormerIndex + 1)
          + " was changed to " + format(overhang) + ": the roof of a dormer overhangs by 0 to 60 cm");
    }
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
        new double [] {slope, slope, slope, slope},
        // Nothing of a dormer goes beyond the plane where the roof is clipped
        new double [] {clippedFront ? 0 : overhang, overhang, 0, overhang},
        base, dormerThickness, true);
    this.geometry.addPart(part);
    this.dormerCount++;
    if (clippedEdge || this.geometry.hasCut()) {
      this.clippedFronts.put(part, new double [] {centerX, centerY, clippedEdge ? 1 : 0});
    }
    // Its windows, side by side in its front wall
    List<double []> sizes = new ArrayList<double []>();
    List<String> names = new ArrayList<String>();
    if (dormer.get("windows") instanceof List) {
      for (Object window : (List<?>)dormer.get("windows")) {
        if (window instanceof Map && number((Map<?, ?>)window, "width", 0) > 0 && number((Map<?, ?>)window, "height", 0) > 0) {
          sizes.add(new double [] {number((Map<?, ?>)window, "width", 0), number((Map<?, ?>)window, "height", 0),
              number((Map<?, ?>)window, "sill", Double.NaN)});
          Object name = ((Map<?, ?>)window).get("name");
          names.add(name != null ? name.toString() : null);
        }
      }
    } else if (number(dormer, "window_width", 0) > 0 && number(dormer, "window_height", 0) > 0) {
      int count = dormer.get("windows") instanceof Number
          ? Math.max(1, Math.min(6, ((Number)dormer.get("windows")).intValue())) : 1;
      for (int i = 0; i < count; i++) {
        sizes.add(new double [] {number(dormer, "window_width", 0), number(dormer, "window_height", 0), Double.NaN});
        names.add(dormer.get("window") != null ? dormer.get("window").toString() : null);
      }
    }
    if (clippedFront && !sizes.isEmpty()) {
      this.notes.add("the windows of the dormer " + (dormerIndex + 1) + " weren't placed: its front is at the wall "
          + "that clips the roof, which closes it");
      sizes.clear();
    }
    for (int i = 0; i < sizes.size(); i++) {
      double bay = width / sizes.size();
      double windowWidth = Math.min(sizes.get(i) [0], bay - (sizes.size() > 1 ? 10 : 16));
      double windowHeight = Math.min(sizes.get(i) [1], height - 10);
      if (windowWidth < 10 || windowHeight < 10) {
        continue;
      }
      double sill = frontHeight + (Double.isNaN(sizes.get(i) [2])
          ? Math.max(4, (height - windowHeight) / 2)
          : Math.max(2, Math.min(height - windowHeight - 2, sizes.get(i) [2])));
      double along = -width / 2 + bay * (i + 0.5);
      // The window faces the outside of the roof
      this.windows.add(new float [] {(float)(centerX + ux * along), (float)(centerY + uy * along),
          (float)Math.atan2(normal [0], -normal [1]),
          (float)sill, (float)windowWidth, (float)windowHeight, this.geometry.getParts().size() - 1, dormerIndex, i});
      this.windowNames.add(names.get(i));
    }
    return type + ", " + Math.round(width) + " cm wide, " + (clippedFront ? "front " : "front wall ") + Math.round(height)
        + " cm high" + (clippedFront ? " closed by the wall that clips the roof" : "") + ", on the edge " + (edge + 1)
        + ", its center " + Math.round(position) + " cm from the start of that edge (point " + (edge + 1) + ") and "
        + Math.round(setback) + " cm back from it, " + Math.round(depth) + " cm deep, top at "
        + Math.round(part.getMaxHeight()) + " cm"
        + (sizes.isEmpty() ? "" : ", " + sizes.size() + (sizes.size() == 1 ? " window" : " windows"));
  }

  private static String format(double value) {
    return Math.abs(value - Math.rint(value)) < 0.005 ? String.valueOf((long)Math.rint(value))
        : String.format(Locale.ENGLISH, "%.2f", value);
  }

  /**
   * Returns the place {x, y, nx, ny} kept in the description of a dormer, or <code>null</code>.
   */
  private static double [] getPlace(Object value) {
    if (value instanceof List && ((List<?>)value).size() >= 4) {
      double [] place = new double [4];
      for (int i = 0; i < 4; i++) {
        if (!(((List<?>)value).get(i) instanceof Number)) {
          return null;
        }
        place [i] = ((Number)((List<?>)value).get(i)).doubleValue();
      }
      return place;
    }
    return null;
  }

  /**
   * Returns the edge of the roof along which a side of its outline runs, with the distances in cm
   * from the start of that edge to the ends of the side, as {edge, from, to}, or <code>null</code>
   * if the side isn't the one of an edge (a step between two parts of the roof, a side cut by a wall).
   */
  private double [] getEdgeOfSide(double ax, double ay, double bx, double by) {
    double [] best = null;
    double bestOverlap = 0.1;
    for (int i = 0; i < this.points.length; i++) {
      double [] point = this.points [i];
      double [] next = this.points [(i + 1) % this.points.length];
      double length = Math.hypot(next [0] - point [0], next [1] - point [1]);
      double ux = (next [0] - point [0]) / length;
      double uy = (next [1] - point [1]) / length;
      double [] normal = getInwardNormal(i);
      // The side runs at the distance of the overhang, out of the edge
      double distanceA = -normal [0] * (ax - point [0]) - normal [1] * (ay - point [1]);
      double distanceB = -normal [0] * (bx - point [0]) - normal [1] * (by - point [1]);
      if (Math.abs(distanceA - this.edgeOverhangs [i]) < 1 && Math.abs(distanceB - this.edgeOverhangs [i]) < 1) {
        double tA = ux * (ax - point [0]) + uy * (ay - point [1]);
        double tB = ux * (bx - point [0]) + uy * (by - point [1]);
        double margin = 310;
        double overlap = Math.min(length + margin, Math.max(tA, tB)) - Math.max(-margin, Math.min(tA, tB));
        if (overlap > bestOverlap) {
          bestOverlap = overlap;
          best = new double [] {i, tA, tB};
        }
      }
    }
    return best;
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
    // The band of an edge that asks for one when the roof has none
    AssistantMeshBuilder.Material edgeFasciaMaterial = mesh.getMaterial(getColor(spec, "fascia_color", 0xF4F2EE));
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
        // Where a wall cut the roof, it has no band and no gutter: the wall closes it
        boolean cutSide = this.geometry.isCut((point [0] + next [0]) / 2 - nx * 0.05, (point [1] + next [1]) / 2 - ny * 0.05);
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
          double [] side = cell.part.isDormer() ? null : getEdgeOfSide(ax, ay, bx, by);
          int edge = side != null ? (int)side [0] : -1;
          if (cutSide || edge >= 0 && this.edgeClips [edge]) {
            continue;
          }
          mesh.addPolygon(new float [][] {
              {(float)ax, (float)(heightA - cellThickness), (float)ay}, {(float)bx, (float)(heightB - cellThickness), (float)by},
              {(float)bx, (float)heightB, (float)by}, {(float)ax, (float)heightA, (float)ay}},
              edge >= 0 && Boolean.FALSE.equals(this.edgeFascias [edge]) ? roofMaterial
                  : edge >= 0 && Boolean.TRUE.equals(this.edgeFascias [edge]) ? edgeFasciaMaterial : fasciaMaterial,
              (float)-nx, 0, (float)-ny);
          // An eave: a level edge the roof rises from
          if (!cell.part.isDormer() && Math.abs(heightA - heightB) < 0.3
              && cell.plane [1] * nx + cell.plane [2] * ny > 0.01 && Math.hypot(bx - ax, by - ay) > 30) {
            gutters.add(new double [] {ax, ay, bx, by, heightA - cellThickness, -nx, -ny,
                edge, side != null ? side [1] : 0, side != null ? side [2] : 0});
          }
        }
      }
    }

    // The walls under gables
    if (flag(spec, "gable_walls", true)) {
      for (int edge = 0; edge < this.points.length; edge++) {
        if (this.edgeTypes [edge] != AssistantRoofGeometry.GABLE || this.edgeAbuts [edge] || this.edgeClips [edge]) {
          // Along the wall of a taller part of the building, that wall closes the roof
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
      List<float []> partWindows = new ArrayList<float []>();
      for (float [] dormerWindow : this.windows) {
        if ((int)dormerWindow [6] == partIndex) {
          partWindows.add(dormerWindow);
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
        // The front of a dormer at a plane where the roof is clipped: the wall that clips the roof stands there
        double [] clippedFront = this.clippedFronts.get(part);
        boolean frontSide = clippedFront != null
            && Math.hypot((point [0] + next [0]) / 2 - clippedFront [0], (point [1] + next [1]) / 2 - clippedFront [1]) < 0.5;
        if (frontSide && clippedFront [2] != 0) {
          continue;
        }
        // Drawn a hair out of its line, to hide a wall of the house fitted up to the roof in the same plane
        double [] drawnPoint = {point [0] - normal [0] * 0.4, point [1] - normal [1] * 0.4};
        double [] drawnNext = {next [0] - normal [0] * 0.4, next [1] - normal [1] * 0.4};
        double x1 = point [0] + normal [0] * 0.02;
        double y1 = point [1] + normal [1] * 0.02;
        double x2 = next [0] + normal [0] * 0.02;
        double y2 = next [1] + normal [1] * 0.02;
        List<double []> bottoms = AssistantRoofGeometry.getProfile(x1, y1, x2, y2, mainCells);
        List<double []> tops = AssistantRoofGeometry.getProfile(x1, y1, x2, y2, dormerCells);
        // The side with the windows is the one they stand in
        boolean front = !partWindows.isEmpty()
            && java.awt.geom.Line2D.ptSegDist(point [0], point [1], next [0], next [1],
                   partWindows.get(0) [0], partWindows.get(0) [1]) < 1;
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
            if (frontSide && this.geometry.isCut(point [0] + (next [0] - point [0]) * middle - normal [0] * 0.3,
                    point [1] + (next [1] - point [1]) * middle - normal [1] * 0.3)) {
              // No wall of its own along the wall that cut the roof
              continue;
            }
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
                addWall(mesh, drawnPoint, drawnNext, previous, limit, bottom1, bottom2, top1, top2, normal, gableMaterial);
              }
            }
          }
          previous = limit;
        }
        if (wall != null) {
          // The wall around its windows
          for (float [] window : partWindows) {
            float middle = (float)(((window [0] - point [0]) * (next [0] - point [0])
                + (window [1] - point [1]) * (next [1] - point [1])) / length);
            wall.subtract(AssistantMeshBuilder.getArea(new float [][] {
                {middle - window [4] / 2, window [3]}, {middle + window [4] / 2, window [3]},
                {middle + window [4] / 2, window [3] + window [5]}, {middle - window [4] / 2, window [3] + window [5]}}));
          }
          for (float [][] trapezoid : AssistantMeshBuilder.getTrapezoids(wall)) {
            float [][] wallPoints = new float [trapezoid.length][];
            for (int i = 0; i < trapezoid.length; i++) {
              double t = trapezoid [i][0] / length;
              wallPoints [i] = new float [] {(float)(drawnPoint [0] + (drawnNext [0] - drawnPoint [0]) * t), trapezoid [i][1],
                                             (float)(drawnPoint [1] + (drawnNext [1] - drawnPoint [1]) * t)};
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
    // Downspouts at the start of each gutter, or where the description puts them
    boolean downspouts = !(spec.get("downspouts") instanceof List) && flag(spec, "downspouts", true);
    for (double [] eave : gutters) {
      int edge = (int)eave [7];
      boolean edgeGutter = edge >= 0 && this.edgeGutters [edge] != null ? this.edgeGutters [edge].booleanValue() : gutter;
      double [][] spans = edge >= 0 ? this.edgeGutterSpans [edge] : null;
      if (spans != null && this.edgeGutters [edge] == null) {
        // Spans ask for a gutter
        edgeGutter = true;
      }
      if (!edgeGutter) {
        continue;
      }
      double eaveLength = Math.hypot(eave [2] - eave [0], eave [3] - eave [1]);
      List<double []> pieces = new ArrayList<double []>();
      if (spans == null) {
        pieces.add(new double [] {0, 1});
      } else {
        // The parts of this eave in the spans, measured from the start of its edge
        double edgeLength = getEdgeLength(edge);
        for (double [] span : spans) {
          double from = span [0] <= 0.5 ? Double.NEGATIVE_INFINITY : span [0];
          double to = span [1] >= edgeLength - 0.5 ? Double.POSITIVE_INFINITY : span [1];
          double t1 = (from - eave [8]) / (eave [9] - eave [8]);
          double t2 = (to - eave [8]) / (eave [9] - eave [8]);
          double start = Math.max(0, Math.min(t1, t2));
          double end = Math.min(1, Math.max(t1, t2));
          if ((end - start) * eaveLength > 1) {
            pieces.add(new double [] {start, end});
          }
        }
      }
      // Hung under the edge of the roof
      double ox = eave [5] * 6;
      double oy = eave [6] * 6;
      float top = (float)eave [4] + 5;
      for (double [] piece : pieces) {
        double x1 = eave [0] + (eave [2] - eave [0]) * piece [0];
        double y1 = eave [1] + (eave [3] - eave [1]) * piece [0];
        double x2 = eave [0] + (eave [2] - eave [0]) * piece [1];
        double y2 = eave [1] + (eave [3] - eave [1]) * piece [1];
        mesh.addBar(new float [] {(float)(x1 + ox), top, (float)(y1 + oy)},
            new float [] {(float)(x2 + ox), top, (float)(y2 + oy)}, 12, 10, metalMaterial);
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (downspouts && length > 100) {
          double ux = (x2 - x1) / length;
          double uy = (y2 - y1) / length;
          addDownspout(mesh, x1 + ux * 12, y1 + uy * 12, eave [5], eave [6], top, groundHeight, metalMaterial);
        }
      }
    }
    if (spec.get("downspouts") instanceof List) {
      int index = 0;
      for (Object downspout : (List<?>)spec.get("downspouts")) {
        index++;
        if (!(downspout instanceof Map)) {
          continue;
        }
        int edge = (int)Math.round(number((Map<?, ?>)downspout, "edge", 0)) - 1;
        if (edge < 0 || edge >= this.points.length) {
          this.notes.add("the downspout " + index + " was ignored: \"edge\" is the number of an edge, from 1 to " + this.points.length);
          continue;
        }
        double [] point = this.points [edge];
        double [] next = this.points [(edge + 1) % this.points.length];
        double length = getEdgeLength(edge);
        double at = number((Map<?, ?>)downspout, "at", length / 2);
        if (at < 0 || at > length) {
          this.notes.add("the place " + format(at) + " of the downspout " + index + " was changed to "
              + format(Math.max(0, Math.min(length, at))) + ": the edge " + (edge + 1) + " is " + format(length) + " cm long");
          at = Math.max(0, Math.min(length, at));
        }
        double [] normal = getInwardNormal(edge);
        double x = point [0] + (next [0] - point [0]) * at / length - normal [0] * this.edgeOverhangs [edge];
        double y = point [1] + (next [1] - point [1]) * at / length - normal [1] * this.edgeOverhangs [edge];
        double underside = this.geometry.getUndersideHeight(x + normal [0] * 0.05, y + normal [1] * 0.05);
        if (Double.isNaN(underside) || this.edgeTypes [edge] != AssistantRoofGeometry.HIP || this.edgeClips [edge]) {
          this.notes.add("the downspout " + index + " was ignored: the roof has no eave at " + format(at)
              + " cm of the edge " + (edge + 1));
          continue;
        }
        addDownspout(mesh, x, y, -normal [0], -normal [1], (float)underside + 5, groundHeight, metalMaterial);
      }
    }
    return mesh.store("roof");
  }

  /**
   * Adds a pipe from a point of the eave back to the wall, then down to the ground.
   * @param ox the direction pointing out of the roof at that point
   * @param top the height of the top of the gutter
   */
  private void addDownspout(AssistantMeshBuilder mesh, double x, double y, double ox, double oy, float top,
                            float groundHeight, AssistantMeshBuilder.Material material) {
    double [] wallPoint = getWallPoint(x, y, -ox, -oy);
    float [] start = {(float)(x + ox * 6), top - 8, (float)(y + oy * 6)};
    float [] bend = {(float)(wallPoint [0] + ox * 6), (float)Math.min(top - 12, this.eaveHeight - 10),
                     (float)(wallPoint [1] + oy * 6)};
    mesh.addBar(start, bend, 7, 7, material);
    mesh.addBar(bend, new float [] {bend [0], Math.min(groundHeight, bend [1] - 10), bend [2]}, 7, 7, material);
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
