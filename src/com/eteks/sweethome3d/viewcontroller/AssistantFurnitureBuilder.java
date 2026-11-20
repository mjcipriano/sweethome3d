/*
 * AssistantFurnitureBuilder.java 16 Nov 2026
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

import com.eteks.sweethome3d.model.TextureImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the 3D model of a piece of furniture from a list of simple parts
 * (boxes, cylinders, cones, spheres), so the design assistant or an MCP
 * client can create furniture that exists in no catalog: a table is a top
 * and four legs, a bookcase is two sides, a back and shelves.
 * <p>Parts are described in centimeters in the frame of the piece seen from
 * above like the plan: <code>x</code> to the right, <code>y</code> toward its
 * front, <code>z</code> upward from the floor.
 * @author Sweet Home 3D
 */
final class AssistantFurnitureBuilder {
  static final int MAX_PARTS = 150;

  private static final int   DEFAULT_COLOR = 0xC8B090;
  private static final int   CYLINDER_SIDES = 24;
  private static final int   SPHERE_SLICES = 16;
  private static final int   SPHERE_STACKS = 10;
  private static final float MIN_SIZE = 0.05f;
  private static final float MAX_SIZE = 2000;

  /**
   * Finds the textures the parts of a piece designate.
   */
  interface TextureFinder {
    /**
     * Returns the texture with the given name, category or id, or <code>null</code>.
     */
    TextureImage findTexture(String name, String category, String catalogId);
  }

  private final AssistantMeshBuilder mesh = new AssistantMeshBuilder();
  private final List<String>         notes = new ArrayList<String>();
  private final TextureFinder        textureFinder;
  private int                        partCount;

  private AssistantFurnitureBuilder(TextureFinder textureFinder) {
    this.textureFinder = textureFinder;
  }

  /**
   * Returns the model made of the given parts, each one a map with the keys
   * <code>shape</code> (box, cylinder, cone, sphere, wedge, prism, extrude),
   * <code>x</code>, <code>y</code> (its center), <code>z</code> (its bottom),
   * <code>width</code>, <code>depth</code>, <code>height</code>,
   * <code>rotation</code> or <code>yaw</code> (degrees around the vertical),
   * <code>pitch</code> and <code>roll</code> (degrees around its width and
   * its depth), <code>axis</code> (x, y or z, for cylinders and cones),
   * <code>top_scale</code> (0 to 1, to taper a box or a cylinder),
   * <code>color</code> and <code>texture</code>.
   * @param notes receives the remarks about the parts that were ignored
   * @throws IOException if no part can be built
   */
  static AssistantGeneratedModel build(List<?> parts, List<String> notes) throws IOException {
    return build(parts, notes, null);
  }

  static AssistantGeneratedModel build(List<?> parts, List<String> notes, TextureFinder textureFinder) throws IOException {
    AssistantFurnitureBuilder builder = new AssistantFurnitureBuilder(textureFinder);
    if (parts != null) {
      for (Object part : parts) {
        if (builder.partCount >= MAX_PARTS) {
          builder.notes.add("only the first " + MAX_PARTS + " parts were used");
          break;
        }
        if (part instanceof Map) {
          builder.addPart((Map<?, ?>)part);
        }
      }
    }
    notes.addAll(builder.notes);
    if (builder.partCount == 0) {
      throw new IOException("\"parts\" must list at least one part like "
          + "{\"shape\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"width\":100,\"depth\":50,\"height\":4}");
    }
    return builder.mesh.store("furniture");
  }

  static float getNumber(Map<?, ?> part, String key, float defaultValue) {
    Object value = part.get(key);
    double number = Double.NaN;
    if (value instanceof Number) {
      number = ((Number)value).doubleValue();
    } else if (value instanceof String) {
      try {
        number = Double.parseDouble(((String)value).trim());
      } catch (NumberFormatException ex) {
        // Use default value
      }
    }
    return Double.isNaN(number) || Double.isInfinite(number) ? defaultValue : (float)number;
  }

  /**
   * Returns the points of the parameter <code>key</code>, each one an array
   * of <code>size</code> numbers, the missing ones being 0.
   */
  static List<float []> getPoints(Map<?, ?> part, String key, int size) {
    List<float []> points = new ArrayList<float []>();
    if (part.get(key) instanceof List) {
      for (Object item : (List<?>)part.get(key)) {
        if (item instanceof List && ((List<?>)item).size() >= 2) {
          float [] point = new float [size];
          boolean valid = true;
          for (int i = 0; i < size && i < ((List<?>)item).size(); i++) {
            Object coordinate = ((List<?>)item).get(i);
            if (coordinate instanceof Number && Math.abs(((Number)coordinate).doubleValue()) <= 1E6) {
              point [i] = ((Number)coordinate).floatValue();
            } else {
              valid = false;
            }
          }
          if (valid) {
            points.add(point);
          }
        }
      }
    }
    return points;
  }

  private AssistantMeshBuilder.Material getMaterial(Map<?, ?> part) {
    Integer parsedColor = part.get("color") != null
        ? AssistantCommandExecutor.parseColor(part.get("color").toString()) : null;
    int color = parsedColor != null ? parsedColor.intValue() & 0xFFFFFF : DEFAULT_COLOR;
    if ((part.get("texture") != null || part.get("texture_id") != null) && this.textureFinder != null) {
      TextureImage texture = this.textureFinder.findTexture(
          part.get("texture") != null ? part.get("texture").toString() : null,
          part.get("texture_category") != null ? part.get("texture_category").toString() : null,
          part.get("texture_id") != null ? part.get("texture_id").toString() : null);
      if (texture != null) {
        return this.mesh.getMaterial(texture, getNumber(part, "texture_scale", 1),
            (float)Math.toRadians(getNumber(part, "texture_angle", 0)),
            getNumber(part, "texture_offset_x", 0), getNumber(part, "texture_offset_y", 0),
            parsedColor != null ? color : 0xB4B0A8);
      }
      this.notes.add("no texture \"" + part.get("texture") + "\" was found, the color of the part was used");
    }
    return this.mesh.getMaterial(color);
  }

  private void addPart(Map<?, ?> part) {
    String shape = part.get("shape") != null
        ? part.get("shape").toString().trim().toLowerCase(Locale.ENGLISH) : "box";
    float x = getNumber(part, "x", 0);
    float y = getNumber(part, "y", 0);
    float z = getNumber(part, "z", 0);
    if (Math.abs(x) > MAX_SIZE || Math.abs(y) > MAX_SIZE || Math.abs(z) > MAX_SIZE) {
      this.notes.add("a " + shape + " placed out of range was ignored");
      return;
    }
    int firstVertex = this.mesh.getVertexCount();
    int firstFace = this.mesh.getFaceCount();
    // Where the part turns around: its center, or its origin if it's given by points
    float pivotHeight;
    if ("prism".equals(shape) || "polygon".equals(shape) || "extrusion".equals(shape) && part.get("points") != null) {
      pivotHeight = addPrism(part);
    } else if ("extrude".equals(shape) || "extrusion".equals(shape) || "sweep".equals(shape) || "path".equals(shape)) {
      pivotHeight = addExtrusion(part);
    } else {
      pivotHeight = addSimpleShape(part, shape);
    }
    if (Float.isNaN(pivotHeight) || this.mesh.getFaceCount() == firstFace) {
      return;
    }
    // Angles turn clockwise in the plan, where y goes down
    double yaw = Math.toRadians(getNumber(part, "yaw", getNumber(part, "rotation", getNumber(part, "angle", 0))));
    double pitch = Math.toRadians(getNumber(part, "pitch", 0));
    double roll = Math.toRadians(getNumber(part, "roll", 0));
    double cosYaw = Math.cos(yaw);
    double sinYaw = Math.sin(yaw);
    double cosPitch = Math.cos(pitch);
    double sinPitch = Math.sin(pitch);
    double cosRoll = Math.cos(roll);
    double sinRoll = Math.sin(roll);
    for (int i = firstVertex; i < this.mesh.getVertexCount(); i++) {
      float [] vertex = this.mesh.getVertex(i);
      // In the frame of the part: px to its right, py to its front, pz upward
      double px = vertex [0];
      double py = vertex [2];
      double pz = vertex [1];
      // Roll around its depth: its right side goes down
      double rx = px * cosRoll + pz * sinRoll;
      double rz = -px * sinRoll + pz * cosRoll;
      px = rx;
      pz = rz;
      // Pitch around its width: its front goes down
      double ry = py * cosPitch + pz * sinPitch;
      rz = -py * sinPitch + pz * cosPitch;
      py = ry;
      pz = rz;
      vertex [0] = x + (float)(px * cosYaw - py * sinYaw);
      vertex [2] = y + (float)(px * sinYaw + py * cosYaw);
      vertex [1] = z + pivotHeight + (float)pz;
    }
    this.partCount++;
  }

  /**
   * Adds a box, a cylinder, a cone, a sphere or a wedge centered at the
   * origin, and returns the height of its center above its bottom, or NaN if it wasn't added.
   */
  private float addSimpleShape(Map<?, ?> part, String shape) {
    float width = getNumber(part, "width", Float.NaN);
    float depth = getNumber(part, "depth", Float.NaN);
    float height = getNumber(part, "height", Float.NaN);
    float diameter = getNumber(part, "diameter", Float.NaN);
    String axis = part.get("axis") != null ? part.get("axis").toString().trim().toLowerCase(Locale.ENGLISH) : "z";
    if (!"x".equals(axis) && !"y".equals(axis)) {
      axis = "z";
    }
    if (!Float.isNaN(diameter)) {
      // A diameter gives the two sizes across the axis, or the three of a sphere
      if (!"x".equals(axis) && Float.isNaN(width)) {
        width = diameter;
      }
      if (!"y".equals(axis) && Float.isNaN(depth)) {
        depth = diameter;
      }
      if ((!"z".equals(axis) || "sphere".equals(shape) || "ball".equals(shape)) && Float.isNaN(height)) {
        height = diameter;
      }
      if (("sphere".equals(shape) || "ball".equals(shape))) {
        width = Float.isNaN(width) ? diameter : width;
        depth = Float.isNaN(depth) ? diameter : depth;
      }
    }
    if (!(width >= MIN_SIZE && width <= MAX_SIZE) || !(depth >= MIN_SIZE && depth <= MAX_SIZE)
        || !(height >= MIN_SIZE && height <= MAX_SIZE)) {
      this.notes.add("a " + shape + " without valid width, depth and height was ignored");
      return Float.NaN;
    }
    float topScale = Math.max(0, Math.min(1, getNumber(part, "top_scale", 1)));
    AssistantMeshBuilder.Material material = getMaterial(part);

    // Shape in a cube of side 1 centered at the origin, its axis along t
    List<float []> unitVertices = new ArrayList<float []>();
    List<int []> unitFaces = new ArrayList<int []>();
    List<Boolean> smoothFaces = new ArrayList<Boolean>();
    if ("cylinder".equals(shape) || "tube".equals(shape) || "disc".equals(shape) || "disk".equals(shape)) {
      addCylinder(unitVertices, unitFaces, smoothFaces, topScale);
    } else if ("cone".equals(shape)) {
      addCylinder(unitVertices, unitFaces, smoothFaces, part.get("top_scale") != null ? topScale : 0);
    } else if ("sphere".equals(shape) || "ball".equals(shape) || "ellipsoid".equals(shape)) {
      addSphere(unitVertices, unitFaces, smoothFaces);
      axis = "z";
    } else if ("wedge".equals(shape) || "ramp".equals(shape) || "gable".equals(shape)) {
      String ridge = part.get("ridge") != null ? part.get("ridge").toString().trim().toLowerCase(Locale.ENGLISH) : null;
      String highSide = part.get("high_side") != null
          ? part.get("high_side").toString().trim().toLowerCase(Locale.ENGLISH) : "back";
      if ("gable".equals(shape) && ridge == null) {
        ridge = "x";
      }
      if (ridge != null && !"x".equals(ridge) && !"y".equals(ridge)
          || ridge == null && !"back".equals(highSide) && !"front".equals(highSide)
              && !"left".equals(highSide) && !"right".equals(highSide)) {
        this.notes.add("a wedge needs \"high_side\":\"back|front|left|right\" (its tallest side) or \"ridge\":\"x|y\" "
            + "(a peak along its middle, like a gable): its high side was put at its back");
        ridge = null;
        highSide = "back";
      }
      addWedge(unitVertices, unitFaces, smoothFaces, ridge, highSide);
      axis = "z";
    } else {
      if (!"box".equals(shape) && !"cube".equals(shape) && !"board".equals(shape) && !"panel".equals(shape)) {
        this.notes.add("unknown shape \"" + shape + "\" was built as a box");
      }
      addBox(unitVertices, unitFaces, smoothFaces, topScale);
      if (!"x".equals(axis) && !"y".equals(axis) || topScale == 1) {
        axis = "z";
      }
    }

    int firstVertex = this.mesh.getVertexCount();
    for (float [] unit : unitVertices) {
      // u, v across the axis, t along it
      float px;
      float py;
      float pz;
      if ("x".equals(axis)) {
        px = unit [2] * width;
        py = unit [0] * depth;
        pz = unit [1] * height;
      } else if ("y".equals(axis)) {
        px = unit [0] * width;
        py = unit [2] * depth;
        pz = unit [1] * height;
      } else {
        px = unit [0] * width;
        py = unit [1] * depth;
        pz = unit [2] * height;
      }
      this.mesh.addVertex(px, pz, py);
    }
    // A point inside the part, which is convex
    float [] inside = new float [3];
    for (int i = firstVertex; i < this.mesh.getVertexCount(); i++) {
      for (int j = 0; j < 3; j++) {
        inside [j] += this.mesh.getVertex(i) [j] / unitVertices.size();
      }
    }
    for (int i = 0; i < unitFaces.size(); i++) {
      int [] indices = unitFaces.get(i).clone();
      for (int j = 0; j < indices.length; j++) {
        indices [j] += firstVertex;
      }
      float [] normal = this.mesh.getNormal(indices);
      if (normal == null) {
        continue;
      }
      // Turn the face outward
      float [] faceCenter = this.mesh.getCenter(indices);
      if (normal [0] * (faceCenter [0] - inside [0]) + normal [1] * (faceCenter [1] - inside [1])
          + normal [2] * (faceCenter [2] - inside [2]) < 0) {
        AssistantMeshBuilder.reverse(indices);
      }
      this.mesh.addFace(indices, material, smoothFaces.get(i).booleanValue());
    }
    return height / 2;
  }

  /**
   * Adds the solid standing on a polygon of points relative to the origin of
   * the part, which may be concave. Returns 0, or NaN if it wasn't added.
   */
  private float addPrism(Map<?, ?> part) {
    List<float []> points = getPoints(part, "points", 2);
    float height = getNumber(part, "height", Float.NaN);
    if (points.size() < 3 || !(height >= MIN_SIZE && height <= MAX_SIZE)) {
      this.notes.add("a prism needs \"points\":[[x,y],[x,y],[x,y],...] (3 points at least, relative to its x,y) "
          + "and a height: it was ignored");
      return Float.NaN;
    }
    for (float [] point : points) {
      if (Math.abs(point [0]) > MAX_SIZE || Math.abs(point [1]) > MAX_SIZE) {
        this.notes.add("a prism with points out of range was ignored");
        return Float.NaN;
      }
    }
    AssistantMeshBuilder.Material material = getMaterial(part);
    this.mesh.addPrism(AssistantMeshBuilder.getArea(points.toArray(new float [points.size()][])),
        0, new float [] {height, 0, 0}, material, material);
    return 0;
  }

  /**
   * Adds a bar following a path of points (x, y, z) relative to the origin of
   * the part. Returns 0, or NaN if it wasn't added.
   */
  private float addExtrusion(Map<?, ?> part) {
    List<float []> path = getPoints(part, "path", 3);
    float diameter = getNumber(part, "diameter", Float.NaN);
    float width = getNumber(part, "width", diameter);
    float height = getNumber(part, "height", diameter);
    if (path.size() < 2 || !(width >= MIN_SIZE && width <= MAX_SIZE) || !(height >= MIN_SIZE && height <= MAX_SIZE)) {
      this.notes.add("an extrude part needs \"path\":[[x,y,z],[x,y,z],...] (2 points at least, relative to its x,y,z) "
          + "and the \"width\" and \"height\" of its section, or its \"diameter\": it was ignored");
      return Float.NaN;
    }
    for (float [] point : path) {
      if (Math.abs(point [0]) > MAX_SIZE || Math.abs(point [1]) > MAX_SIZE || Math.abs(point [2]) > MAX_SIZE) {
        this.notes.add("an extrude part with a path out of range was ignored");
        return Float.NaN;
      }
    }
    boolean closed = Boolean.TRUE.equals(part.get("closed")) && path.size() > 2;
    boolean round = !Float.isNaN(diameter) && part.get("width") == null && part.get("height") == null;
    AssistantMeshBuilder.Material material = getMaterial(part);
    int count = closed ? path.size() : path.size() - 1;
    for (int i = 0; i < count; i++) {
      float [] start = path.get(i);
      float [] end = path.get((i + 1) % path.size());
      // In the frame of OBJ files: x, up, front
      double [] direction = {end [0] - start [0], end [2] - start [2], end [1] - start [1]};
      double length = Math.sqrt(direction [0] * direction [0] + direction [1] * direction [1] + direction [2] * direction [2]);
      if (length < 0.01) {
        continue;
      }
      for (int j = 0; j < 3; j++) {
        direction [j] /= length;
      }
      // Corners are filled by making each segment longer by half its section
      double before = closed || i > 0 ? Math.min(width, height) / 2 : 0;
      double after = closed || i < count - 1 ? Math.min(width, height) / 2 : 0;
      double [] side;
      if (Math.hypot(direction [0], direction [2]) < 1E-4) {
        side = new double [] {1, 0, 0};
      } else {
        // Horizontal, across the path
        double horizontal = Math.hypot(direction [0], direction [2]);
        side = new double [] {-direction [2] / horizontal, 0, direction [0] / horizontal};
      }
      double [] above = {direction [1] * side [2] - direction [2] * side [1],
                         direction [2] * side [0] - direction [0] * side [2],
                         direction [0] * side [1] - direction [1] * side [0]};
      int sides = round ? 10 : 4;
      float [][] section = new float [sides][];
      for (int j = 0; j < sides; j++) {
        double u;
        double v;
        if (round) {
          double angle = 2 * Math.PI * j / sides;
          u = Math.cos(angle) * width / 2;
          v = Math.sin(angle) * height / 2;
        } else {
          u = (j == 0 || j == 3 ? -1 : 1) * width / 2;
          v = (j < 2 ? -1 : 1) * height / 2;
        }
        section [j] = new float [] {
            (float)(start [0] - direction [0] * before + side [0] * u + above [0] * v),
            (float)(start [2] - direction [1] * before + side [1] * u + above [1] * v),
            (float)(start [1] - direction [2] * before + side [2] * u + above [2] * v)};
      }
      double total = length + before + after;
      this.mesh.addSolid(section, new float [] {(float)(direction [0] * total), (float)(direction [1] * total),
          (float)(direction [2] * total)}, material);
    }
    return 0;
  }

  private static void addWedge(List<float []> vertices, List<int []> faces, List<Boolean> smooth,
                               String ridge, String highSide) {
    vertices.add(new float [] {-0.5f, -0.5f, -0.5f});
    vertices.add(new float [] {0.5f, -0.5f, -0.5f});
    vertices.add(new float [] {0.5f, 0.5f, -0.5f});
    vertices.add(new float [] {-0.5f, 0.5f, -0.5f});
    int [][] wedgeFaces;
    if ("x".equals(ridge)) {
      // A peak along the width
      vertices.add(new float [] {-0.5f, 0, 0.5f});
      vertices.add(new float [] {0.5f, 0, 0.5f});
      wedgeFaces = new int [][] {{0, 1, 2, 3}, {0, 1, 5, 4}, {3, 2, 5, 4}, {0, 3, 4}, {1, 2, 5}};
    } else if ("y".equals(ridge)) {
      vertices.add(new float [] {0, -0.5f, 0.5f});
      vertices.add(new float [] {0, 0.5f, 0.5f});
      wedgeFaces = new int [][] {{0, 1, 2, 3}, {0, 3, 5, 4}, {1, 2, 5, 4}, {0, 1, 4}, {3, 2, 5}};
    } else if ("front".equals(highSide)) {
      vertices.add(new float [] {-0.5f, 0.5f, 0.5f});
      vertices.add(new float [] {0.5f, 0.5f, 0.5f});
      wedgeFaces = new int [][] {{0, 1, 2, 3}, {3, 2, 5, 4}, {0, 1, 5, 4}, {0, 3, 4}, {1, 2, 5}};
    } else if ("left".equals(highSide)) {
      vertices.add(new float [] {-0.5f, -0.5f, 0.5f});
      vertices.add(new float [] {-0.5f, 0.5f, 0.5f});
      wedgeFaces = new int [][] {{0, 1, 2, 3}, {0, 3, 5, 4}, {1, 2, 5, 4}, {0, 1, 4}, {3, 2, 5}};
    } else if ("right".equals(highSide)) {
      vertices.add(new float [] {0.5f, -0.5f, 0.5f});
      vertices.add(new float [] {0.5f, 0.5f, 0.5f});
      wedgeFaces = new int [][] {{0, 1, 2, 3}, {1, 2, 5, 4}, {0, 3, 5, 4}, {0, 1, 4}, {3, 2, 5}};
    } else {
      vertices.add(new float [] {-0.5f, -0.5f, 0.5f});
      vertices.add(new float [] {0.5f, -0.5f, 0.5f});
      wedgeFaces = new int [][] {{0, 1, 2, 3}, {0, 1, 5, 4}, {3, 2, 5, 4}, {0, 3, 4}, {1, 2, 5}};
    }
    for (int [] face : wedgeFaces) {
      faces.add(face);
      smooth.add(Boolean.FALSE);
    }
  }

  private static void addBox(List<float []> vertices, List<int []> faces, List<Boolean> smooth, float topScale) {
    float top = 0.5f * Math.max(topScale, 0.001f);
    vertices.add(new float [] {-0.5f, -0.5f, -0.5f});
    vertices.add(new float [] {0.5f, -0.5f, -0.5f});
    vertices.add(new float [] {0.5f, 0.5f, -0.5f});
    vertices.add(new float [] {-0.5f, 0.5f, -0.5f});
    vertices.add(new float [] {-top, -top, 0.5f});
    vertices.add(new float [] {top, -top, 0.5f});
    vertices.add(new float [] {top, top, 0.5f});
    vertices.add(new float [] {-top, top, 0.5f});
    int [][] boxFaces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
    for (int [] face : boxFaces) {
      faces.add(face);
      smooth.add(Boolean.FALSE);
    }
  }

  private static void addCylinder(List<float []> vertices, List<int []> faces, List<Boolean> smooth, float topScale) {
    boolean apex = topScale < 0.001f;
    for (int i = 0; i < CYLINDER_SIDES; i++) {
      double angle = 2 * Math.PI * i / CYLINDER_SIDES;
      vertices.add(new float [] {(float)(0.5 * Math.cos(angle)), (float)(0.5 * Math.sin(angle)), -0.5f});
    }
    if (apex) {
      vertices.add(new float [] {0, 0, 0.5f});
    } else {
      for (int i = 0; i < CYLINDER_SIDES; i++) {
        double angle = 2 * Math.PI * i / CYLINDER_SIDES;
        vertices.add(new float [] {(float)(0.5 * topScale * Math.cos(angle)),
                                   (float)(0.5 * topScale * Math.sin(angle)), 0.5f});
      }
    }
    int [] bottom = new int [CYLINDER_SIDES];
    int [] top = new int [CYLINDER_SIDES];
    for (int i = 0; i < CYLINDER_SIDES; i++) {
      int next = (i + 1) % CYLINDER_SIDES;
      bottom [i] = i;
      top [i] = CYLINDER_SIDES + i;
      faces.add(apex
          ? new int [] {i, next, CYLINDER_SIDES}
          : new int [] {i, next, CYLINDER_SIDES + next, CYLINDER_SIDES + i});
      smooth.add(Boolean.TRUE);
    }
    faces.add(bottom);
    smooth.add(Boolean.FALSE);
    if (!apex) {
      faces.add(top);
      smooth.add(Boolean.FALSE);
    }
  }

  private static void addSphere(List<float []> vertices, List<int []> faces, List<Boolean> smooth) {
    // Bottom pole, rings, top pole
    vertices.add(new float [] {0, 0, -0.5f});
    for (int stack = 1; stack < SPHERE_STACKS; stack++) {
      double latitude = Math.PI * stack / SPHERE_STACKS - Math.PI / 2;
      for (int slice = 0; slice < SPHERE_SLICES; slice++) {
        double longitude = 2 * Math.PI * slice / SPHERE_SLICES;
        vertices.add(new float [] {(float)(0.5 * Math.cos(latitude) * Math.cos(longitude)),
                                   (float)(0.5 * Math.cos(latitude) * Math.sin(longitude)),
                                   (float)(0.5 * Math.sin(latitude))});
      }
    }
    int topPole = vertices.size();
    vertices.add(new float [] {0, 0, 0.5f});
    for (int slice = 0; slice < SPHERE_SLICES; slice++) {
      int next = (slice + 1) % SPHERE_SLICES;
      faces.add(new int [] {0, 1 + slice, 1 + next});
      smooth.add(Boolean.TRUE);
      for (int stack = 0; stack < SPHERE_STACKS - 2; stack++) {
        int ring = 1 + stack * SPHERE_SLICES;
        faces.add(new int [] {ring + slice, ring + next, ring + SPHERE_SLICES + next, ring + SPHERE_SLICES + slice});
        smooth.add(Boolean.TRUE);
      }
      int lastRing = 1 + (SPHERE_STACKS - 2) * SPHERE_SLICES;
      faces.add(new int [] {lastRing + slice, lastRing + next, topPole});
      smooth.add(Boolean.TRUE);
    }
  }

  /**
   * Returns the elevation of the lowest point of the parts.
   */
  static float getLowestPoint(List<?> parts) {
    float lowest = Float.MAX_VALUE;
    if (parts != null) {
      for (Object part : parts) {
        if (part instanceof Map) {
          float z = getNumber((Map<?, ?>)part, "z", 0);
          float lowestPathPoint = 0;
          for (float [] point : getPoints((Map<?, ?>)part, "path", 3)) {
            lowestPathPoint = Math.min(lowestPathPoint, point [2]);
          }
          lowest = Math.min(lowest, z + lowestPathPoint);
        }
      }
    }
    return lowest == Float.MAX_VALUE ? 0 : lowest;
  }
}
