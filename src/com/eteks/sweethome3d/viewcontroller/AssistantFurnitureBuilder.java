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
  private static final int   ICON_SIZE = 48;
  private static final float MIN_SIZE = 0.05f;
  private static final float MAX_SIZE = 2000;

  private final List<float []> vertices = new ArrayList<float []>(); // OBJ frame: x, up, front
  private final List<Face>     faces = new ArrayList<Face>();
  private final List<Integer>  colors = new ArrayList<Integer>();
  private final List<String>   notes = new ArrayList<String>();
  private int                  partCount;

  /**
   * A polygon of the model.
   */
  private static final class Face {
    private final int []  indices;
    private final int     color;
    private final boolean smooth;

    Face(int [] indices, int color, boolean smooth) {
      this.indices = indices;
      this.color = color;
      this.smooth = smooth;
    }
  }

  /**
   * Returns the model made of the given parts, each one a map with the keys
   * <code>shape</code> (box, cylinder, cone, sphere), <code>x</code>,
   * <code>y</code> (its center), <code>z</code> (its bottom),
   * <code>width</code>, <code>depth</code>, <code>height</code>,
   * <code>rotation</code> (degrees around the vertical), <code>axis</code>
   * (x, y or z, for cylinders and cones), <code>top_scale</code> (0 to 1, to
   * taper a box or a cylinder) and <code>color</code>.
   * @param notes receives the remarks about the parts that were ignored
   * @throws IOException if no part can be built
   */
  static AssistantGeneratedModel build(List<?> parts, List<String> notes) throws IOException {
    AssistantFurnitureBuilder builder = new AssistantFurnitureBuilder();
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
    return builder.store();
  }

  private static float getNumber(Map<?, ?> part, String key, float defaultValue) {
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

  private void addPart(Map<?, ?> part) {
    String shape = part.get("shape") != null
        ? part.get("shape").toString().trim().toLowerCase(Locale.ENGLISH) : "box";
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
      return;
    }
    float x = getNumber(part, "x", 0);
    float y = getNumber(part, "y", 0);
    float z = getNumber(part, "z", 0);
    if (Math.abs(x) > MAX_SIZE || Math.abs(y) > MAX_SIZE || Math.abs(z) > MAX_SIZE) {
      this.notes.add("a " + shape + " placed out of range was ignored");
      return;
    }
    double rotation = Math.toRadians(getNumber(part, "rotation", getNumber(part, "angle", 0)));
    float topScale = Math.max(0, Math.min(1, getNumber(part, "top_scale", 1)));
    Integer parsedColor = part.get("color") != null
        ? AssistantCommandExecutor.parseColor(part.get("color").toString()) : null;
    int color = parsedColor != null ? parsedColor.intValue() & 0xFFFFFF : DEFAULT_COLOR;

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
    } else {
      if (!"box".equals(shape) && !"cube".equals(shape) && !"board".equals(shape) && !"panel".equals(shape)) {
        this.notes.add("unknown shape \"" + shape + "\" was built as a box");
      }
      addBox(unitVertices, unitFaces, smoothFaces, topScale);
      if (!"x".equals(axis) && !"y".equals(axis) || topScale == 1) {
        axis = "z";
      }
    }

    int firstVertex = this.vertices.size();
    double cos = Math.cos(rotation);
    double sin = Math.sin(rotation);
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
      // Angles turn clockwise in the plan, where y goes down
      float rx = (float)(px * cos - py * sin);
      float ry = (float)(px * sin + py * cos);
      this.vertices.add(new float [] {x + rx, z + height / 2 + pz, y + ry});
    }
    float [] center = {x, z + height / 2, y};
    for (int i = 0; i < unitFaces.size(); i++) {
      int [] indices = unitFaces.get(i).clone();
      for (int j = 0; j < indices.length; j++) {
        indices [j] += firstVertex;
      }
      float [] normal = getNormal(indices);
      if (normal == null) {
        continue;
      }
      // Turn the face outward: parts are convex
      float [] faceCenter = getCenter(indices);
      if (normal [0] * (faceCenter [0] - center [0]) + normal [1] * (faceCenter [1] - center [1])
          + normal [2] * (faceCenter [2] - center [2]) < 0) {
        for (int j = 0; j < indices.length / 2; j++) {
          int swapped = indices [j];
          indices [j] = indices [indices.length - 1 - j];
          indices [indices.length - 1 - j] = swapped;
        }
      }
      this.faces.add(new Face(indices, color, smoothFaces.get(i).booleanValue()));
    }
    if (!this.colors.contains(color)) {
      this.colors.add(color);
    }
    this.partCount++;
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

  private float [] getCenter(int [] indices) {
    float [] center = new float [3];
    for (int index : indices) {
      float [] vertex = this.vertices.get(index);
      center [0] += vertex [0] / indices.length;
      center [1] += vertex [1] / indices.length;
      center [2] += vertex [2] / indices.length;
    }
    return center;
  }

  /**
   * Returns the unit normal of a polygon (Newell's method), or
   * <code>null</code> if it has no area.
   */
  private float [] getNormal(int [] indices) {
    double nx = 0;
    double ny = 0;
    double nz = 0;
    for (int i = 0; i < indices.length; i++) {
      float [] current = this.vertices.get(indices [i]);
      float [] next = this.vertices.get(indices [(i + 1) % indices.length]);
      nx += (current [1] - next [1]) * (current [2] + next [2]);
      ny += (current [2] - next [2]) * (current [0] + next [0]);
      nz += (current [0] - next [0]) * (current [1] + next [1]);
    }
    double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
    if (length < 1E-9) {
      return null;
    }
    return new float [] {(float)(nx / length), (float)(ny / length), (float)(nz / length)};
  }

  private AssistantGeneratedModel store() throws IOException {
    float [] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
    float [] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    for (float [] vertex : this.vertices) {
      for (int i = 0; i < 3; i++) {
        min [i] = Math.min(min [i], vertex [i]);
        max [i] = Math.max(max [i], vertex [i]);
      }
    }
    String suffix = Long.toHexString(System.nanoTime());
    StringBuilder mtl = new StringBuilder();
    for (Integer color : this.colors) {
      mtl.append(AssistantGeneratedModel.material("color_" + String.format("%06X", color), color, null));
    }
    StringBuilder obj = new StringBuilder("mtllib furniture_" + suffix + ".mtl\n");
    for (float [] vertex : this.vertices) {
      obj.append(String.format(Locale.US, "v %.3f %.3f %.3f\n", vertex [0], vertex [1], vertex [2]));
    }
    // One group per color, flat faces before the smooth ones
    for (Integer color : this.colors) {
      obj.append("g color_").append(String.format("%06X", color)).append('\n')
         .append("usemtl color_").append(String.format("%06X", color)).append('\n');
      for (int pass = 0; pass < 2; pass++) {
        boolean smooth = pass == 1;
        boolean started = false;
        for (Face face : this.faces) {
          if (face.color == color.intValue() && face.smooth == smooth) {
            if (!started) {
              obj.append(smooth ? "s 1\n" : "s off\n");
              started = true;
            }
            obj.append('f');
            for (int index : face.indices) {
              obj.append(' ').append(index + 1);
            }
            obj.append('\n');
          }
        }
      }
    }
    Map<String, byte []> files = new LinkedHashMap<String, byte []>();
    files.put("furniture_" + suffix + ".obj", obj.toString().getBytes("ISO-8859-1"));
    files.put("furniture_" + suffix + ".mtl", mtl.toString().getBytes("ISO-8859-1"));
    files.put("icon_" + suffix + ".png", createIcon(min, max));
    return AssistantGeneratedModel.store("furniture", files,
        max [0] - min [0], max [2] - min [2], max [1] - min [1]);
  }

  /**
   * Returns the elevation of the lowest point of the parts.
   */
  static float getLowestPoint(List<?> parts) {
    float lowest = Float.MAX_VALUE;
    if (parts != null) {
      for (Object part : parts) {
        if (part instanceof Map) {
          lowest = Math.min(lowest, getNumber((Map<?, ?>)part, "z", 0));
        }
      }
    }
    return lowest == Float.MAX_VALUE ? 0 : lowest;
  }

  /**
   * Returns a PNG picture of the model seen from its front, slightly from
   * above and from the side, drawn without the AWT toolkit this package doesn't use.
   */
  private byte [] createIcon(float [] min, float [] max) throws IOException {
    // Drawn three times larger then reduced, to keep thin parts and smooth edges
    int sampling = 3;
    int size = ICON_SIZE * sampling;
    double yaw = Math.toRadians(-25);
    double pitch = Math.toRadians(20);
    double cosYaw = Math.cos(yaw);
    double sinYaw = Math.sin(yaw);
    double cosPitch = Math.cos(pitch);
    double sinPitch = Math.sin(pitch);
    float [] modelCenter = {(min [0] + max [0]) / 2, (min [1] + max [1]) / 2, (min [2] + max [2]) / 2};
    // View frame: sx to the right, sy upward, sz toward the viewer
    float [][] view = new float [this.vertices.size()][];
    float extent = 0;
    for (int i = 0; i < view.length; i++) {
      float [] vertex = this.vertices.get(i);
      double x = vertex [0] - modelCenter [0];
      double y = vertex [1] - modelCenter [1];
      double z = vertex [2] - modelCenter [2];
      double x1 = x * cosYaw + z * sinYaw;
      double z1 = -x * sinYaw + z * cosYaw;
      double y2 = y * cosPitch - z1 * sinPitch;
      double z2 = y * sinPitch + z1 * cosPitch;
      view [i] = new float [] {(float)x1, (float)y2, (float)z2};
      extent = Math.max(extent, Math.max(Math.abs((float)x1), Math.abs((float)y2)));
    }
    float scale = extent > 0 ? (size / 2f - 3 * sampling) / extent : 1;
    float [] depths = new float [size * size];
    java.util.Arrays.fill(depths, -Float.MAX_VALUE);
    int [] colors = new int [size * size];
    boolean [] drawn = new boolean [size * size];
    for (Face face : this.faces) {
      float [] a = view [face.indices [0]];
      for (int i = 1; i < face.indices.length - 1; i++) {
        float [] b = view [face.indices [i]];
        float [] c = view [face.indices [i + 1]];
        // Normal in the view frame, to shade the triangle
        float nx = (b [1] - a [1]) * (c [2] - a [2]) - (b [2] - a [2]) * (c [1] - a [1]);
        float ny = (b [2] - a [2]) * (c [0] - a [0]) - (b [0] - a [0]) * (c [2] - a [2]);
        float nz = (b [0] - a [0]) * (c [1] - a [1]) - (b [1] - a [1]) * (c [0] - a [0]);
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1E-6f || nz <= 0) {
          // Without area, or turned away from the viewer
          continue;
        }
        float light = Math.max(0, (nx * -0.35f + ny * 0.6f + nz * 0.72f) / length);
        float shade = 0.5f + 0.5f * light;
        float ax = size / 2f + a [0] * scale;
        float ay = size / 2f - a [1] * scale;
        float bx = size / 2f + b [0] * scale;
        float by = size / 2f - b [1] * scale;
        float cx = size / 2f + c [0] * scale;
        float cy = size / 2f - c [1] * scale;
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1E-6f) {
          continue;
        }
        int xMin = Math.max(0, (int)Math.floor(Math.min(ax, Math.min(bx, cx))));
        int xMax = Math.min(size - 1, (int)Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int yMin = Math.max(0, (int)Math.floor(Math.min(ay, Math.min(by, cy))));
        int yMax = Math.min(size - 1, (int)Math.ceil(Math.max(ay, Math.max(by, cy))));
        for (int py = yMin; py <= yMax; py++) {
          for (int px = xMin; px <= xMax; px++) {
            float x = px + 0.5f;
            float y = py + 0.5f;
            float w1 = ((bx - x) * (cy - y) - (by - y) * (cx - x)) / area;
            float w2 = ((cx - x) * (ay - y) - (cy - y) * (ax - x)) / area;
            float w3 = 1 - w1 - w2;
            if (w1 < -0.01f || w2 < -0.01f || w3 < -0.01f) {
              continue;
            }
            float depth = w1 * a [2] + w2 * b [2] + w3 * c [2];
            if (depth > depths [py * size + px]) {
              depths [py * size + px] = depth;
              drawn [py * size + px] = true;
              colors [py * size + px] = ((int)(((face.color >> 16) & 0xFF) * shade) << 16)
                  | ((int)(((face.color >> 8) & 0xFF) * shade) << 8)
                  | (int)((face.color & 0xFF) * shade);
            }
          }
        }
      }
    }
    byte [] pixels = new byte [ICON_SIZE * (1 + ICON_SIZE * 4)];
    for (int y = 0; y < ICON_SIZE; y++) {
      for (int x = 0; x < ICON_SIZE; x++) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int sy = 0; sy < sampling; sy++) {
          for (int sx = 0; sx < sampling; sx++) {
            int sample = (y * sampling + sy) * size + x * sampling + sx;
            if (drawn [sample]) {
              red += (colors [sample] >> 16) & 0xFF;
              green += (colors [sample] >> 8) & 0xFF;
              blue += colors [sample] & 0xFF;
              count++;
            }
          }
        }
        if (count > 0) {
          int pixel = y * (1 + ICON_SIZE * 4) + 1 + x * 4;
          pixels [pixel] = (byte)(red / count);
          pixels [pixel + 1] = (byte)(green / count);
          pixels [pixel + 2] = (byte)(blue / count);
          pixels [pixel + 3] = (byte)Math.min(255, 255 * count * 2 / (sampling * sampling));
        }
      }
    }
    return AssistantGeneratedModel.encodePng(pixels, ICON_SIZE);
  }
}
