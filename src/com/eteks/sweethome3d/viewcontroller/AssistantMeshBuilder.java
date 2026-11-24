/*
 * AssistantMeshBuilder.java
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
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.TextureImage;

/**
 * Builds the 3D model of a generated piece (a slab, a roof, the ground, a
 * piece of furniture made of parts) from polygons with a color or a texture,
 * and stores it as an OBJ file with its materials, its images and its icon.
 * Vertices are given in the frame of OBJ files: <code>x</code> to the right,
 * <code>y</code> upward and <code>z</code> toward the front, which is the
 * <code>y</code> axis of the plan.
 * @author Sweet Home 3D
 */
final class AssistantMeshBuilder {
  private static final int ICON_SIZE = 48;
  private static final int PLAN_ICON_SIZE = 160;
  // Pictures are drawn three times larger then reduced, to keep thin parts and smooth edges
  private static final int ICON_SAMPLING = 3;
  private static final int TEXTURED_PLAN_ICON_SIZE = 256;
  /** Images larger than this aren't copied in a model. */
  private static final int MAX_IMAGE_LENGTH = 8 * 1024 * 1024;

  /**
   * A color or a texture applied to polygons.
   */
  static final class Material {
    private final String  name;
    private final int     color;
    private final byte [] image;
    private final String  imageName;
    private final float   textureWidth;
    private final float   textureHeight;
    private final float   angle;
    private final float   xOffset;
    private final float   yOffset;
    private float         opacity = 1;
    /** The ARGB pixels of a square image, kept to draw pictures of the model, or <code>null</code>. */
    private int []        pixels;
    private int           pixelsSize;
    /** <code>true</code> if its image lets see through parts of it. */
    private boolean       cutOut;

    private Material(String name, int color, byte [] image, String imageName,
                     float textureWidth, float textureHeight, float angle, float xOffset, float yOffset) {
      this.name = name;
      this.color = color;
      this.image = image;
      this.imageName = imageName;
      this.textureWidth = textureWidth;
      this.textureHeight = textureHeight;
      this.angle = angle;
      this.xOffset = xOffset;
      this.yOffset = yOffset;
    }

    int getColor() {
      return this.color;
    }

    boolean isTextured() {
      return this.image != null;
    }
  }

  private static final class Face {
    private final int []   indices;
    private final Material material;
    private final boolean  smooth;
    /** The texture coordinates of its vertices, or <code>null</code> if they come from its plane. */
    private float [][]     textureCoordinates;
    /** The normal given to all its vertices, or <code>null</code>. */
    private float []       normal;

    Face(int [] indices, Material material, boolean smooth) {
      this.indices = indices;
      this.material = material;
      this.smooth = smooth;
    }
  }

  private final List<float []>        vertices = new ArrayList<float []>();
  private final List<Face>            faces = new ArrayList<Face>();
  private final Map<String, Material> materials = new LinkedHashMap<String, Material>();
  private final Map<String, String>   imageNames = new LinkedHashMap<String, String>();
  private final String                suffix = Long.toHexString(System.nanoTime());
  /** The pictures stored with the model instead of the ones drawn from its faces, or <code>null</code>. */
  private byte []                     icon;
  private byte []                     planIcon;

  /**
   * Returns the material of the given color.
   */
  Material getMaterial(int color) {
    color &= 0xFFFFFF;
    String key = String.format("color_%06X", color);
    Material material = this.materials.get(key);
    if (material == null) {
      material = new Material(key, color, null, null, 0, 0, 0, 0, 0);
      this.materials.put(key, material);
    }
    return material;
  }

  /**
   * Returns the material of the given color, which lets see through it.
   * @param opacity from 0 (invisible) to 1 (opaque)
   */
  Material getMaterial(int color, float opacity) {
    color &= 0xFFFFFF;
    String key = String.format("glass_%06X_%d", color, Math.round(opacity * 100));
    Material material = this.materials.get(key);
    if (material == null) {
      material = new Material(key, color, null, null, 0, 0, 0, 0, 0);
      material.opacity = Math.max(0.05f, Math.min(1, opacity));
      this.materials.put(key, material);
    }
    return material;
  }

  /**
   * Returns the material showing a pattern of {@link AssistantPatterns}, repeated every
   * <code>width</code> x <code>height</code> cm.
   * @param color  the main color of the pattern, used too for the icon of the model
   * @param color2 its second color
   * @param ratio  the share of the pattern its lines or its holes take
   */
  Material getMaterial(String pattern, float width, float height, int color, int color2, float ratio) {
    color &= 0xFFFFFF;
    color2 &= 0xFFFFFF;
    String imageKey = String.format("%s_%06X_%06X_%d", pattern, color, color2, Math.round(ratio * 1000));
    String key = "pattern_" + imageKey + "_" + width + "_" + height;
    Material material = this.materials.get(key);
    if (material == null) {
      byte [] image;
      try {
        image = AssistantPatterns.create(pattern, color, color2, ratio);
      } catch (IOException ex) {
        return getMaterial(color);
      }
      String imageName = this.imageNames.get(imageKey);
      if (imageName == null) {
        imageName = "texture" + this.imageNames.size() + "_" + this.suffix + ".png";
        this.imageNames.put(imageKey, imageName);
      }
      material = new Material("texture" + this.materials.size() + "_" + this.suffix, color, image, imageName,
          Math.max(0.5f, width), Math.max(0.5f, height), 0, 0, 0);
      // Kept for the pictures drawn of the model
      material.pixels = AssistantPatterns.createPixels(pattern, color, color2, ratio);
      material.pixelsSize = AssistantPatterns.SIZE;
      this.materials.put(key, material);
    }
    return material;
  }

  /**
   * Returns the material showing a PNG image built for this model, laid by the texture coordinates of the faces.
   * @param key    what tells this image from the other ones of the model
   * @param image  the PNG image
   * @param color  the color used for the icon of the model
   */
  Material getImageMaterial(String key, byte [] image, int color, int [] pixels, int pixelsSize) {
    Material material = getImageMaterial(key, image, color);
    material.pixels = pixels;
    material.pixelsSize = pixelsSize;
    material.cutOut = true;
    return material;
  }

  /**
   * Returns the material showing a PNG image built for this model, laid by the texture coordinates of the faces.
   */
  Material getImageMaterial(String key, byte [] image, int color) {
    return getImageMaterial(key, image, color, "texture");
  }

  private Material getImageMaterial(String key, byte [] image, int color, String namePrefix) {
    Material material = this.materials.get("image_" + key);
    if (material == null) {
      String imageName = "texture" + this.imageNames.size() + "_" + this.suffix + ".png";
      this.imageNames.put("image_" + key, imageName);
      material = new Material(namePrefix + this.materials.size() + "_" + this.suffix, color & 0xFFFFFF, image, imageName, 1, 1, 0, 0, 0);
      this.materials.put("image_" + key, material);
    }
    return material;
  }

  /**
   * Adds a polygon with the texture coordinates of its vertices, and the normal they share if it isn't <code>null</code>.
   */
  void addFace(int [] indices, Material material, boolean smooth, float [][] textureCoordinates, float [] normal) {
    if (indices.length >= 3 && getNormal(indices) != null) {
      Face face = new Face(indices, material, smooth);
      face.textureCoordinates = textureCoordinates;
      face.normal = normal;
      this.faces.add(face);
    }
  }

  /**
   * Returns the material showing <code>texture</code>, repeated every
   * <code>scale</code> times its size.
   * @param angle   angle of the texture in radians
   * @param xOffset offset of the texture, as a fraction of its width
   * @param color   the color used for the icon of the model, and for the
   *                polygons if the image of the texture can't be read
   */
  Material getMaterial(TextureImage texture, float scale, float angle, float xOffset, float yOffset, int color) {
    byte [] image = null;
    String extension = null;
    try {
      image = read(texture.getImage());
      extension = AssistantGeneratedModel.getImageExtension(image);
    } catch (IOException ex) {
      // Use the color
    }
    if (image == null || extension == null || !(texture.getWidth() > 0) || !(texture.getHeight() > 0)) {
      return getMaterial(color);
    }
    scale = scale > 0 ? scale : 1;
    String imageKey = image.length + "_" + java.util.Arrays.hashCode(image);
    String key = "texture_" + imageKey + "_" + scale + "_" + angle + "_" + xOffset + "_" + yOffset;
    Material material = this.materials.get(key);
    if (material == null) {
      String imageName = this.imageNames.get(imageKey);
      if (imageName == null) {
        imageName = "texture" + this.imageNames.size() + "_" + this.suffix + "." + extension;
        this.imageNames.put(imageKey, imageName);
      }
      material = new Material("texture" + this.materials.size() + "_" + this.suffix, color & 0xFFFFFF, image, imageName,
          texture.getWidth() * scale, texture.getHeight() * scale, angle, xOffset, yOffset);
      this.materials.put(key, material);
    }
    return material;
  }

  /**
   * Returns a material that looks like <code>material</code> and is named after the part it shows
   * (<code>posts</code>, <code>rails</code>...), for <code>list_materials</code> and <code>set_material</code>.
   * A name already used by a material that looks different gets a number.
   */
  Material getNamedMaterial(String name, Material material) {
    String key = "named:" + name + ":" + material.name;
    Material named = this.materials.get(key);
    if (named == null) {
      int count = 1;
      for (String otherKey : this.materials.keySet()) {
        if (otherKey.startsWith("named:" + name + ":")) {
          count++;
        }
      }
      named = new Material(count == 1 ? name : name + "_" + count, material.color, material.image, material.imageName,
          material.textureWidth, material.textureHeight, material.angle, material.xOffset, material.yOffset);
      named.opacity = material.opacity;
      this.materials.put(key, named);
    }
    return named;
  }

  private static byte [] read(Content content) throws IOException {
    InputStream in = content.openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) > 0) {
        out.write(buffer, 0, count);
        if (out.size() > MAX_IMAGE_LENGTH) {
          throw new IOException("Image too large");
        }
      }
      return out.toByteArray();
    } finally {
      in.close();
    }
  }

  int getVertexCount() {
    return this.vertices.size();
  }

  int getFaceCount() {
    return this.faces.size();
  }

  float [] getVertex(int index) {
    return this.vertices.get(index);
  }

  /**
   * Adds a vertex and returns its index.
   * @param up    its height
   * @param front its ordinate in the plan
   */
  int addVertex(float x, float up, float front) {
    this.vertices.add(new float [] {x, up, front});
    return this.vertices.size() - 1;
  }

  /**
   * Adds a polygon, seen from the side where its vertices turn counter clockwise.
   */
  void addFace(int [] indices, Material material, boolean smooth) {
    if (indices.length >= 3 && getNormal(indices) != null) {
      this.faces.add(new Face(indices, material, smooth));
    }
  }

  /**
   * Adds a flat polygon given by its points (x, up, front), which is seen from
   * the side its normal has a positive product with (<code>nx</code>, <code>ny</code>, <code>nz</code>).
   */
  void addPolygon(float [][] points, Material material, float nx, float ny, float nz) {
    int [] indices = new int [points.length];
    for (int i = 0; i < points.length; i++) {
      indices [i] = addVertex(points [i][0], points [i][1], points [i][2]);
    }
    float [] normal = getNormal(indices);
    if (normal == null) {
      return;
    }
    if (normal [0] * nx + normal [1] * ny + normal [2] * nz < 0) {
      reverse(indices);
    }
    this.faces.add(new Face(indices, material, false));
  }

  static void reverse(int [] indices) {
    for (int i = 0; i < indices.length / 2; i++) {
      int swapped = indices [i];
      indices [i] = indices [indices.length - 1 - i];
      indices [indices.length - 1 - i] = swapped;
    }
  }

  /**
   * Returns the unit normal of a polygon (Newell's method), or
   * <code>null</code> if it has no area.
   */
  float [] getNormal(int [] indices) {
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
    if (length < 1E-7) {
      return null;
    }
    return new float [] {(float)(nx / length), (float)(ny / length), (float)(nz / length)};
  }

  float [] getCenter(int [] indices) {
    float [] center = new float [3];
    for (int index : indices) {
      float [] vertex = this.vertices.get(index);
      center [0] += vertex [0] / indices.length;
      center [1] += vertex [1] / indices.length;
      center [2] += vertex [2] / indices.length;
    }
    return center;
  }

  // ---- Shapes of the plan ----

  /**
   * Returns the shape of the polygon joining the given points (x, y) of the plan.
   */
  static Area getArea(float [][] points) {
    GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO);
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
   * Returns the closed outlines of <code>area</code>, each one a list of points (x, y).
   */
  static List<float [][]> getOutlines(Area area) {
    List<float [][]> outlines = new ArrayList<float [][]>();
    List<float []> points = new ArrayList<float []>();
    float [] coordinates = new float [6];
    for (PathIterator it = area.getPathIterator(null, 0.5); !it.isDone(); it.next()) {
      int type = it.currentSegment(coordinates);
      if (type == PathIterator.SEG_MOVETO) {
        addOutline(outlines, points);
        points.add(new float [] {coordinates [0], coordinates [1]});
      } else if (type == PathIterator.SEG_LINETO) {
        points.add(new float [] {coordinates [0], coordinates [1]});
      } else if (type == PathIterator.SEG_CLOSE) {
        addOutline(outlines, points);
      }
    }
    addOutline(outlines, points);
    return outlines;
  }

  private static void addOutline(List<float [][]> outlines, List<float []> points) {
    // Without repeated points
    for (int i = points.size() - 1; i >= 0 && points.size() > 1; i--) {
      float [] next = points.get((i + 1) % points.size());
      if (Math.abs(points.get(i) [0] - next [0]) < 1E-3f && Math.abs(points.get(i) [1] - next [1]) < 1E-3f) {
        points.remove(i);
      }
    }
    if (points.size() >= 3) {
      outlines.add(points.toArray(new float [points.size()][]));
    }
    points.clear();
  }

  /**
   * Returns trapezoids that cover <code>area</code> without overlapping,
   * each one an array of 3 or 4 points (x, y): the area is cut in horizontal
   * bands at the ordinate of each of its points.
   */
  static List<float [][]> getTrapezoids(Area area) {
    List<float [][]> trapezoids = new ArrayList<float [][]>();
    List<float []> edges = new ArrayList<float []>();
    TreeSet<Float> ordinates = new TreeSet<Float>();
    for (float [][] outline : getOutlines(area)) {
      for (int i = 0; i < outline.length; i++) {
        float [] point = outline [i];
        float [] next = outline [(i + 1) % outline.length];
        ordinates.add(point [1]);
        if (point [1] != next [1]) {
          edges.add(point [1] < next [1]
              ? new float [] {point [0], point [1], next [0], next [1]}
              : new float [] {next [0], next [1], point [0], point [1]});
        }
      }
    }
    Float previous = null;
    for (Float ordinate : ordinates) {
      if (previous != null && ordinate - previous > 1E-4f) {
        final float y1 = previous;
        final float y2 = ordinate;
        final float middle = (y1 + y2) / 2;
        // The edges that cross the band, from left to right
        List<float []> crossings = new ArrayList<float []>();
        for (float [] edge : edges) {
          if (edge [1] <= y1 + 1E-5f && edge [3] >= y2 - 1E-5f) {
            float slope = (edge [2] - edge [0]) / (edge [3] - edge [1]);
            crossings.add(new float [] {edge [0] + slope * (middle - edge [1]),
                                        edge [0] + slope * (y1 - edge [1]), edge [0] + slope * (y2 - edge [1])});
          }
        }
        Collections.sort(crossings, new java.util.Comparator<float []>() {
            public int compare(float [] crossing1, float [] crossing2) {
              return Float.compare(crossing1 [0], crossing2 [0]);
            }
          });
        for (int i = 0; i + 1 < crossings.size(); i += 2) {
          float [] left = crossings.get(i);
          float [] right = crossings.get(i + 1);
          List<float []> points = new ArrayList<float []>(4);
          points.add(new float [] {left [1], y1});
          if (right [1] - left [1] > 1E-4f) {
            points.add(new float [] {right [1], y1});
          }
          points.add(new float [] {right [2], y2});
          if (right [2] - left [2] > 1E-4f) {
            points.add(new float [] {left [2], y2});
          }
          if (points.size() >= 3) {
            trapezoids.add(points.toArray(new float [points.size()][]));
          }
        }
      }
      if (previous == null || ordinate - previous > 1E-4f) {
        previous = ordinate;
      }
    }
    return trapezoids;
  }

  /**
   * Adds the solid standing on <code>area</code> of the plan, between a flat
   * bottom and a top that may be tilted.
   * @param top   the coefficients {c, cx, cy} of the height c + cx * x + cy * y of its top
   */
  void addPrism(Area area, float bottom, float [] top, Material topMaterial, Material sideMaterial) {
    for (float [][] trapezoid : getTrapezoids(area)) {
      float [][] topPoints = new float [trapezoid.length][];
      float [][] bottomPoints = new float [trapezoid.length][];
      for (int i = 0; i < trapezoid.length; i++) {
        float x = trapezoid [i][0];
        float y = trapezoid [i][1];
        topPoints [i] = new float [] {x, top [0] + top [1] * x + top [2] * y, y};
        bottomPoints [i] = new float [] {x, bottom, y};
      }
      addPolygon(topPoints, topMaterial, 0, 1, 0);
      addPolygon(bottomPoints, sideMaterial, 0, -1, 0);
    }
    for (float [][] outline : getOutlines(area)) {
      for (int i = 0; i < outline.length; i++) {
        float [] point = outline [i];
        float [] next = outline [(i + 1) % outline.length];
        float dx = next [0] - point [0];
        float dy = next [1] - point [1];
        float length = (float)Math.hypot(dx, dy);
        if (length < 1E-3f) {
          continue;
        }
        // The side is seen from the outside of the area
        float nx = dy / length;
        float ny = -dx / length;
        if (area.contains((point [0] + next [0]) / 2 + nx * 0.05f, (point [1] + next [1]) / 2 + ny * 0.05f)) {
          nx = -nx;
          ny = -ny;
        }
        addPolygon(new float [][] {
            {point [0], bottom, point [1]}, {next [0], bottom, next [1]},
            {next [0], top [0] + top [1] * next [0] + top [2] * next [1], next [1]},
            {point [0], top [0] + top [1] * point [0] + top [2] * point [1], point [1]}}, sideMaterial, nx, 0, ny);
      }
    }
  }

  /**
   * Adds a box between two opposite corners.
   */
  void addBox(float x1, float bottom, float z1, float x2, float top, float z2, Material material) {
    addPrism(getArea(new float [][] {{x1, z1}, {x2, z1}, {x2, z2}, {x1, z2}}), bottom, new float [] {top, 0, 0},
        material, material);
  }

  /**
   * Adds a bar of rectangular section along the segment joining two points (x, up, front).
   * @param width  its horizontal size
   * @param height its vertical size, the segment running along the middle of its top
   */
  void addBar(float [] start, float [] end, float width, float height, Material material) {
    float dx = end [0] - start [0];
    float dz = end [2] - start [2];
    float length = (float)Math.hypot(dx, dz);
    float px;
    float pz;
    if (length < 1E-3f) {
      if (Math.abs(end [1] - start [1]) < 1E-3f) {
        return;
      }
      px = width / 2;
      pz = 0;
      // A vertical bar
      float [][] bottom = {{start [0] - px, start [1], start [2] - px}, {start [0] + px, start [1], start [2] - px},
                           {start [0] + px, start [1], start [2] + px}, {start [0] - px, start [1], start [2] + px}};
      addSolid(bottom, new float [] {0, end [1] - start [1], 0}, material);
      return;
    }
    px = -dz / length * width / 2;
    pz = dx / length * width / 2;
    float [][] section = {{start [0] - px, start [1] - height, start [2] - pz}, {start [0] + px, start [1] - height, start [2] + pz},
                          {start [0] + px, start [1], start [2] + pz}, {start [0] - px, start [1], start [2] - pz}};
    addSolid(section, new float [] {end [0] - start [0], end [1] - start [1], end [2] - start [2]}, material);
  }

  /**
   * Adds the solid swept by the flat convex polygon <code>section</code> moved by <code>vector</code>.
   */
  void addSolid(float [][] section, float [] vector, Material material) {
    int count = section.length;
    float [][] end = new float [count][];
    float [] center = new float [3];
    for (int i = 0; i < count; i++) {
      end [i] = new float [] {section [i][0] + vector [0], section [i][1] + vector [1], section [i][2] + vector [2]};
      for (int j = 0; j < 3; j++) {
        center [j] += (section [i][j] + end [i][j]) / (2 * count);
      }
    }
    addOutward(section, center, material);
    addOutward(end, center, material);
    for (int i = 0; i < count; i++) {
      int next = (i + 1) % count;
      addOutward(new float [][] {section [i], section [next], end [next], end [i]}, center, material);
    }
  }

  /**
   * Adds a polygon of a convex solid, turned away from the center of the solid.
   */
  void addOutward(float [][] points, float [] center, Material material) {
    float [] faceCenter = new float [3];
    for (float [] point : points) {
      for (int j = 0; j < 3; j++) {
        faceCenter [j] += point [j] / points.length;
      }
    }
    addPolygon(points, material, faceCenter [0] - center [0], faceCenter [1] - center [1], faceCenter [2] - center [2]);
  }

  // ---- Storage ----

  /**
   * Returns the bounds {minX, minUp, minFront, maxX, maxUp, maxFront} of the model.
   */
  float [] getBounds() {
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                       -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    for (float [] vertex : this.vertices) {
      for (int i = 0; i < 3; i++) {
        bounds [i] = Math.min(bounds [i], vertex [i]);
        bounds [3 + i] = Math.max(bounds [3 + i], vertex [i]);
      }
    }
    return bounds;
  }

  /**
   * Stores the model in a temporary file saved with the home, and returns it
   * with its icon and its size.
   */
  AssistantGeneratedModel store(String prefix) throws IOException {
    float [] bounds = getBounds();
    AssistantGeneratedModel model = AssistantGeneratedModel.store(prefix, getFiles(prefix),
        bounds [3] - bounds [0], bounds [5] - bounds [2], bounds [4] - bounds [1]);
    model.setBounds(bounds);
    return model;
  }

  /**
   * Returns the files of the model: its OBJ and MTL files, its images and its icons.
   */
  Map<String, byte []> getFiles(String prefix) throws IOException {
    if (this.faces.isEmpty()) {
      throw new IOException("nothing to build");
    }
    float [] bounds = getBounds();
    StringBuilder mtl = new StringBuilder();
    Map<String, byte []> files = new LinkedHashMap<String, byte []>();
    String name = prefix + "_" + this.suffix;
    List<Material> usedMaterials = new ArrayList<Material>();
    for (Face face : this.faces) {
      if (!usedMaterials.contains(face.material)) {
        usedMaterials.add(face.material);
      }
    }
    for (Material material : usedMaterials) {
      String description = AssistantGeneratedModel.material(material.name, material.isTextured() ? 0xFFFFFF : material.color,
          material.imageName);
      if (material.opacity < 1) {
        description = description.replace("\nd 1\n", String.format(Locale.US, "\nd %.2f\n", material.opacity));
      }
      mtl.append(description);
    }
    StringBuilder obj = new StringBuilder("# Generated by Sweet Home 3D\nmtllib " + name + ".mtl\n");
    for (float [] vertex : this.vertices) {
      obj.append(String.format(Locale.US, "v %.3f %.3f %.3f\n", vertex [0], vertex [1], vertex [2]));
    }
    int textureVertexCount = 0;
    int normalCount = 0;
    for (Material material : usedMaterials) {
      obj.append("g ").append(material.name).append("\nusemtl ").append(material.name).append('\n');
      for (int pass = 0; pass < 2; pass++) {
        boolean smooth = pass == 1;
        boolean started = false;
        for (Face face : this.faces) {
          if (face.material != material || face.smooth != smooth) {
            continue;
          }
          if (!started) {
            obj.append(smooth ? "s 1\n" : "s off\n");
            started = true;
          }
          if (material.isTextured()) {
            float [][] coordinates = face.textureCoordinates != null ? face.textureCoordinates : getTextureCoordinates(face);
            for (float [] coordinate : coordinates) {
              obj.append("vt ").append(Math.round(coordinate [0] * 10000) / 10000f).append(' ')
                  .append(Math.round(coordinate [1] * 10000) / 10000f).append('\n');
            }
            if (face.normal != null) {
              obj.append("vn ").append(Math.round(face.normal [0] * 1000) / 1000f).append(' ')
                  .append(Math.round(face.normal [1] * 1000) / 1000f).append(' ')
                  .append(Math.round(face.normal [2] * 1000) / 1000f).append('\n');
              normalCount++;
            }
            obj.append('f');
            for (int i = 0; i < face.indices.length; i++) {
              obj.append(' ').append(face.indices [i] + 1).append('/').append(++textureVertexCount);
              if (face.normal != null) {
                obj.append('/').append(normalCount);
              }
            }
          } else {
            obj.append('f');
            for (int index : face.indices) {
              obj.append(' ').append(index + 1);
            }
          }
          obj.append('\n');
        }
      }
      if (material.isTextured()) {
        files.put(material.imageName, material.image);
      }
    }
    files.put(name + ".obj", obj.toString().getBytes("ISO-8859-1"));
    files.put(name + ".mtl", mtl.toString().getBytes("ISO-8859-1"));
    files.put("icon_" + this.suffix + ".png", this.icon != null ? this.icon : createIcon(bounds));
    files.put("planicon_" + this.suffix + ".png", this.planIcon != null ? this.planIcon : createPlanIcon(bounds));
    return files;
  }

  /**
   * Returns <code>true</code> if some faces show pictures that let see through parts of them.
   */
  private boolean hasPictures() {
    for (Material material : this.materials.values()) {
      if (material.cutOut) {
        return true;
      }
    }
    return false;
  }

  // ---- Impostors ----

  /** The count of triangles of an impostor. */
  static final int IMPOSTOR_TRIANGLES = 18;
  /**
   * The start of the name of a material that shows no shadow cast on it, as
   * <code>ModelManager.UNSHADOWED_MATERIAL_PREFIX</code>: the shade of a plant is in the pictures of
   * its impostor already, and the shadow a card casts on the other ones would show them as flat faces.
   */
  static final String UNSHADOWED_MATERIAL_PREFIX = "unshadowed_";
  /** The half of the thickness of the slice of the plant a card shows, as a share of its half width. */
  private static final float IMPOSTOR_SLICE = 0.42f;
  /** Darkens the pictures, which the scene lights without the shade the leaves cast on each other. */
  private static final float IMPOSTOR_BRIGHTNESS = 0.8f;

  /**
   * Returns a model of nine flat faces that show pictures of this one: its views from 8 sides on 4 crossed
   * upright cards, each side of a card showing what is seen from there, and its view from the top on a
   * horizontal card. The pictures are tiles of one image, and the model keeps the icons of this one.
   * @param tileSize the size in pixels of each of the 9 pictures
   */
  AssistantMeshBuilder createImpostor(int tileSize) throws IOException {
    if (this.faces.isEmpty()) {
      throw new IOException("nothing to build");
    }
    float [] bounds = getBounds();
    float centerX = (bounds [0] + bounds [3]) / 2;
    float centerZ = (bounds [2] + bounds [5]) / 2;
    float bottom = bounds [1];
    float top = bounds [4];
    int margin = 2;
    int inner = tileSize - 2 * margin;
    int size = tileSize * 3;
    int [] atlas = new int [size * size];
    float [] up = {0, 1, 0};
    float [] center = {centerX, (bottom + top) / 2, centerZ};
    float [][] rights = new float [8][];
    float [][] towards = new float [8][];
    float [] extents = new float [8];
    for (int view = 0; view < 8; view++) {
      double angle = Math.PI / 4 * (view / 2);
      int side = view % 2 == 0 ? 1 : -1;
      float [] toward = {(float)-Math.sin(angle) * side, 0, (float)Math.cos(angle) * side};
      float [] right = {toward [2], 0, -toward [0]};
      float extent = 0;
      for (float [] vertex : this.vertices) {
        extent = Math.max(extent, Math.abs((vertex [0] - centerX) * right [0] + (vertex [2] - centerZ) * right [2]));
      }
      extent = Math.max(extent, 0.5f);
      rights [view] = right;
      towards [view] = toward;
      extents [view] = extent;
      // Each card shows the slice of the plant around it: the 4 of them seen together are as full as the plant
      copyTile(render(right, up, toward, center, extent, Math.max(0.5f, (top - bottom) / 2), inner,
              extent * IMPOSTOR_SLICE, IMPOSTOR_BRIGHTNESS), inner,
          atlas, size, (view % 3) * tileSize + margin, (view / 3) * tileSize + margin);
    }
    float halfWidth = Math.max(0.5f, (bounds [3] - bounds [0]) / 2);
    float halfDepth = Math.max(0.5f, (bounds [5] - bounds [2]) / 2);
    copyTile(render(new float [] {1, 0, 0}, new float [] {0, 0, -1}, up, center, halfWidth, halfDepth, inner,
            Float.MAX_VALUE, IMPOSTOR_BRIGHTNESS), inner,
        atlas, size, 2 * tileSize + margin, 2 * tileSize + margin);

    // Where nothing is drawn, the mean color of the picture, which the edges of the leaves blend with
    long red = 0;
    long green = 0;
    long blue = 0;
    int count = 0;
    for (int argb : atlas) {
      if (argb >>> 24 != 0) {
        red += (argb >> 16) & 0xFF;
        green += (argb >> 8) & 0xFF;
        blue += argb & 0xFF;
        count++;
      }
    }
    if (count == 0) {
      throw new IOException("nothing to draw");
    }
    int meanColor = (int)(red / count) << 16 | (int)(green / count) << 8 | (int)(blue / count);
    for (int i = 0; i < atlas.length; i++) {
      if (atlas [i] >>> 24 == 0) {
        atlas [i] = meanColor;
      }
    }

    AssistantMeshBuilder impostor = new AssistantMeshBuilder();
    Material material = impostor.getImageMaterial("impostor", AssistantLeafTextures.encode(atlas, size), meanColor,
        UNSHADOWED_MATERIAL_PREFIX + "texture");
    material.pixels = atlas;
    material.pixelsSize = size;
    for (int view = 0; view < 8; view++) {
      float [] right = rights [view];
      float [] toward = towards [view];
      float extent = extents [view];
      // Each side a little in front of the other one, for the renderers that show both
      float shift = extent * 0.002f;
      float x = centerX + toward [0] * shift;
      float z = centerZ + toward [2] * shift;
      int a = impostor.addVertex(x - right [0] * extent, bottom, z - right [2] * extent);
      int b = impostor.addVertex(x + right [0] * extent, bottom, z + right [2] * extent);
      int c = impostor.addVertex(x + right [0] * extent, top, z + right [2] * extent);
      int d = impostor.addVertex(x - right [0] * extent, top, z - right [2] * extent);
      // All the cards lit the same way, as from the top, whichever side the light comes from
      impostor.addFace(new int [] {a, b, c, d}, material, false, getTile(view % 3, view / 3, tileSize, margin), up);
    }
    // The view from the top where the plant is the widest
    int slabs = 12;
    float [] radii = new float [slabs];
    for (float [] vertex : this.vertices) {
      int slab = Math.max(0, Math.min(slabs - 1, (int)((vertex [1] - bottom) / Math.max(1E-3f, top - bottom) * slabs)));
      float dx = vertex [0] - centerX;
      float dz = vertex [2] - centerZ;
      radii [slab] = Math.max(radii [slab], dx * dx + dz * dz);
    }
    int widest = 0;
    for (int i = 1; i < slabs; i++) {
      if (radii [i] >= radii [widest] * 0.9f && radii [i] > radii [0] * 1.05f || radii [i] > radii [widest]) {
        widest = i;
      }
    }
    float y = bottom + (top - bottom) * (widest + 0.5f) / slabs;
    int a = impostor.addVertex(bounds [0], y, bounds [5]);
    int b = impostor.addVertex(bounds [3], y, bounds [5]);
    int c = impostor.addVertex(bounds [3], y, bounds [2]);
    int d = impostor.addVertex(bounds [0], y, bounds [2]);
    // Seen from above only: from the ground it would be a line across the plant
    impostor.addFace(new int [] {a, b, c, d}, material, false, getTile(2, 2, tileSize, margin), up);
    impostor.icon = createIcon(bounds);
    impostor.planIcon = createPlanIcon(bounds);
    return impostor;
  }

  /**
   * Returns the texture coordinates of the corners of a tile among 3 x 3, from its bottom left one
   * turning to its bottom right one.
   */
  private static float [][] getTile(int column, int row, int tileSize, int margin) {
    float size = tileSize * 3;
    float left = (column * tileSize + margin) / size;
    float right = ((column + 1) * tileSize - margin) / size;
    float low = 1 - ((row + 1) * tileSize - margin) / size;
    float high = 1 - (row * tileSize + margin) / size;
    return new float [][] {{left, low}, {right, low}, {right, high}, {left, high}};
  }

  private static void copyTile(int [] tile, int tileSize, int [] image, int imageSize, int x, int y) {
    for (int row = 0; row < tileSize; row++) {
      System.arraycopy(tile, row * tileSize, image, (y + row) * imageSize + x, tileSize);
    }
  }

  /**
   * Returns the <code>size</code> x <code>size</code> ARGB pixels of the model seen from the direction
   * <code>toward</code> without perspective, its textures drawn where they aren't seen through.
   * Pixels are either opaque or fully transparent.
   * @param right   the direction that goes to the right of the picture
   * @param up      the direction that goes to its top
   * @param toward  the direction that comes to the viewer
   * @param center  the point shown at the center of the picture
   * @param halfWidth  the half of the width shown
   * @param halfHeight the half of the height shown
   */
  int [] render(float [] right, float [] up, float [] toward, float [] center,
                float halfWidth, float halfHeight, int size) {
    return render(right, up, toward, center, halfWidth, halfHeight, size, Float.MAX_VALUE, 1);
  }

  /**
   * Returns the picture of the part of the model that is at less than <code>halfDepth</code> of
   * <code>center</code> in the direction of the viewer, its colors multiplied by <code>brightness</code>.
   */
  private int [] render(float [] right, float [] up, float [] toward, float [] center,
                        float halfWidth, float halfHeight, int size, float halfDepth, float brightness) {
    int sampling = 3;
    int width = size * sampling;
    float scaleX = width / 2f / halfWidth;
    float scaleY = width / 2f / halfHeight;
    float [][] view = new float [this.vertices.size()][];
    for (int i = 0; i < view.length; i++) {
      float [] vertex = this.vertices.get(i);
      float x = vertex [0] - center [0];
      float y = vertex [1] - center [1];
      float z = vertex [2] - center [2];
      view [i] = new float [] {width / 2f + (x * right [0] + y * right [1] + z * right [2]) * scaleX,
          width / 2f - (x * up [0] + y * up [1] + z * up [2]) * scaleY,
          x * toward [0] + y * toward [1] + z * toward [2]};
    }
    // Light from above the viewer
    float [] light = {toward [0] * 0.4f + up [0] * 0.3f, toward [1] * 0.4f + up [1] * 0.3f + 0.85f, toward [2] * 0.4f + up [2] * 0.3f};
    float lightLength = (float)Math.sqrt(light [0] * light [0] + light [1] * light [1] + light [2] * light [2]);
    float [] depths = new float [width * width];
    java.util.Arrays.fill(depths, -Float.MAX_VALUE);
    int [] colors = new int [width * width];
    for (Face face : this.faces) {
      Material material = face.material;
      int [] pixels = material.pixels;
      float [][] coordinates = pixels == null ? null
          : face.textureCoordinates != null ? face.textureCoordinates : getTextureCoordinates(face);
      float [] a = view [face.indices [0]];
      float [] normal = face.normal != null ? face.normal : getNormal(face.indices);
      float shade = 0.85f;
      if (normal != null) {
        float dot = (normal [0] * light [0] + normal [1] * light [1] + normal [2] * light [2]) / lightLength;
        shade = 0.66f + 0.34f * Math.max(0, dot);
      }
      shade *= brightness;
      int faceColor = material.color;
      for (int i = 1; i < face.indices.length - 1; i++) {
        float [] b = view [face.indices [i]];
        float [] c = view [face.indices [i + 1]];
        // In the picture y goes down: the faces turned to the viewer are the ones with a negative area
        float area = (b [0] - a [0]) * (c [1] - a [1]) - (b [1] - a [1]) * (c [0] - a [0]);
        if (area > -1E-6f) {
          continue;
        }
        int xMin = Math.max(0, (int)Math.floor(Math.min(a [0], Math.min(b [0], c [0]))));
        int xMax = Math.min(width - 1, (int)Math.ceil(Math.max(a [0], Math.max(b [0], c [0]))));
        int yMin = Math.max(0, (int)Math.floor(Math.min(a [1], Math.min(b [1], c [1]))));
        int yMax = Math.min(width - 1, (int)Math.ceil(Math.max(a [1], Math.max(b [1], c [1]))));
        float [] ta = pixels != null ? coordinates [0] : null;
        float [] tb = pixels != null ? coordinates [i] : null;
        float [] tc = pixels != null ? coordinates [i + 1] : null;
        for (int py = yMin; py <= yMax; py++) {
          for (int px = xMin; px <= xMax; px++) {
            float x = px + 0.5f;
            float y = py + 0.5f;
            float w1 = ((b [0] - x) * (c [1] - y) - (b [1] - y) * (c [0] - x)) / area;
            float w2 = ((c [0] - x) * (a [1] - y) - (c [1] - y) * (a [0] - x)) / area;
            float w3 = 1 - w1 - w2;
            if (w1 < 0 || w2 < 0 || w3 < 0) {
              continue;
            }
            int index = py * width + px;
            float depth = w1 * a [2] + w2 * b [2] + w3 * c [2];
            if (depth <= depths [index] || Math.abs(depth) > halfDepth) {
              continue;
            }
            int color = faceColor;
            if (pixels != null) {
              float u = w1 * ta [0] + w2 * tb [0] + w3 * tc [0];
              float v = w1 * ta [1] + w2 * tb [1] + w3 * tc [1];
              int tx = Math.max(0, Math.min(material.pixelsSize - 1, (int)((u - Math.floor(u)) * material.pixelsSize)));
              int ty = Math.max(0, Math.min(material.pixelsSize - 1, (int)((1 - (v - Math.floor(v))) * material.pixelsSize)));
              color = pixels [ty * material.pixelsSize + tx];
              if (color >>> 24 < 128) {
                continue;
              }
            }
            depths [index] = depth;
            colors [index] = 0xFF000000
                | Math.min(255, (int)(((color >> 16) & 0xFF) * shade)) << 16
                | Math.min(255, (int)(((color >> 8) & 0xFF) * shade)) << 8
                | Math.min(255, (int)((color & 0xFF) * shade));
          }
        }
      }
    }
    int [] picture = new int [size * size];
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int sy = 0; sy < sampling; sy++) {
          for (int sx = 0; sx < sampling; sx++) {
            int sample = colors [(y * sampling + sy) * width + x * sampling + sx];
            if (sample != 0) {
              red += (sample >> 16) & 0xFF;
              green += (sample >> 8) & 0xFF;
              blue += sample & 0xFF;
              count++;
            }
          }
        }
        if (count * 3 >= sampling * sampling) {
          // Colors rounded, for the image to take less room in the file of the home
          picture [y * size + x] = 0xFF000000 | (red / count & 0xF8) << 16 | (green / count & 0xF8) << 8 | blue / count & 0xF8;
        }
      }
    }
    return picture;
  }

  /**
   * Returns the texture coordinates of the vertices of a face: its texture is
   * laid in its plane, its rows horizontal, at the size of the texture.
   */
  private float [][] getTextureCoordinates(Face face) {
    float [] normal = getNormal(face.indices);
    float [] u;
    float [] v;
    double horizontal = Math.hypot(normal [0], normal [2]);
    if (horizontal < 0.05) {
      // As in the plan seen from above
      u = new float [] {1, 0, 0};
      v = new float [] {0, 0, -1};
    } else {
      // u horizontal, v going up the slope
      u = new float [] {(float)(normal [2] / horizontal), 0, (float)(-normal [0] / horizontal)};
      v = new float [] {normal [1] * u [2] - normal [2] * u [1], normal [2] * u [0] - normal [0] * u [2],
                        normal [0] * u [1] - normal [1] * u [0]};
      if (v [1] < 0) {
        v = new float [] {-v [0], -v [1], -v [2]};
      }
    }
    double cos = Math.cos(face.material.angle);
    double sin = Math.sin(face.material.angle);
    float [][] coordinates = new float [face.indices.length][];
    for (int i = 0; i < face.indices.length; i++) {
      float [] vertex = this.vertices.get(face.indices [i]);
      double s = (vertex [0] * u [0] + vertex [1] * u [1] + vertex [2] * u [2]) / face.material.textureWidth;
      double t = (vertex [0] * v [0] + vertex [1] * v [1] + vertex [2] * v [2]) / face.material.textureHeight;
      coordinates [i] = new float [] {(float)(s * cos + t * sin + face.material.xOffset),
                                      (float)(-s * sin + t * cos + face.material.yOffset)};
    }
    return coordinates;
  }

  /**
   * Returns a PNG picture of the model seen from its front, slightly from
   * above and from the side, drawn without the AWT toolkit this package doesn't use.
   */
  private byte [] createIcon(float [] bounds) throws IOException {
    int size = ICON_SIZE * ICON_SAMPLING;
    double yaw = Math.toRadians(-25);
    double pitch = Math.toRadians(20);
    double cosYaw = Math.cos(yaw);
    double sinYaw = Math.sin(yaw);
    double cosPitch = Math.cos(pitch);
    double sinPitch = Math.sin(pitch);
    float [] modelCenter = {(bounds [0] + bounds [3]) / 2, (bounds [1] + bounds [4]) / 2, (bounds [2] + bounds [5]) / 2};
    if (hasPictures()) {
      // Leaves drawn as they are cut in their pictures, not as the cards that carry them
      float [] right = {(float)cosYaw, 0, (float)sinYaw};
      float [] up = {(float)(sinYaw * sinPitch), (float)cosPitch, (float)(-cosYaw * sinPitch)};
      float [] toward = {(float)(-sinYaw * cosPitch), (float)sinPitch, (float)(cosYaw * cosPitch)};
      float half = 0;
      for (float [] vertex : this.vertices) {
        float x = vertex [0] - modelCenter [0];
        float y = vertex [1] - modelCenter [1];
        float z = vertex [2] - modelCenter [2];
        half = Math.max(half, Math.max(Math.abs(x * right [0] + z * right [2]), Math.abs(x * up [0] + y * up [1] + z * up [2])));
      }
      half = Math.max(0.5f, half * 1.06f);
      return AssistantLeafTextures.encode(render(right, up, toward, modelCenter, half, half, ICON_SIZE * 2), ICON_SIZE * 2);
    }
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
    float scale = extent > 0 ? (size / 2f - 3 * ICON_SAMPLING) / extent : 1;
    return rasterize(view, scale, scale, ICON_SIZE);
  }

  /**
   * Returns the picture of the model seen from the top, which the plan draws in the
   * rectangle of the piece: the width and the depth of the model fill the picture.
   */
  private byte [] createPlanIcon(float [] bounds) throws IOException {
    int size = PLAN_ICON_SIZE * ICON_SAMPLING;
    float centerX = (bounds [0] + bounds [3]) / 2;
    float centerZ = (bounds [2] + bounds [5]) / 2;
    if (hasPictures()) {
      return AssistantLeafTextures.encode(render(new float [] {1, 0, 0}, new float [] {0, 0, -1}, new float [] {0, 1, 0},
          new float [] {centerX, 0, centerZ}, Math.max(0.5f, (bounds [3] - bounds [0]) / 2),
          Math.max(0.5f, (bounds [5] - bounds [2]) / 2), TEXTURED_PLAN_ICON_SIZE), TEXTURED_PLAN_ICON_SIZE);
    }
    // View frame: sx to the right of the plan, sy to its top, sz upward
    float [][] view = new float [this.vertices.size()][];
    for (int i = 0; i < view.length; i++) {
      float [] vertex = this.vertices.get(i);
      view [i] = new float [] {vertex [0] - centerX, -(vertex [2] - centerZ), vertex [1]};
    }
    float width = bounds [3] - bounds [0];
    float depth = bounds [5] - bounds [2];
    return rasterize(view, width > 0 ? size / width : 1, depth > 0 ? size / depth : 1, PLAN_ICON_SIZE);
  }

  /**
   * Returns the PNG picture of the faces of this mesh, their vertices being given in a frame
   * where x goes to the right, y upward and z toward the viewer.
   */
  private byte [] rasterize(float [][] view, float scale, float scaleY, int iconSize) throws IOException {
    int sampling = ICON_SAMPLING;
    int size = iconSize * sampling;
    float [] depths = new float [size * size];
    java.util.Arrays.fill(depths, -Float.MAX_VALUE);
    int [] colors = new int [size * size];
    boolean [] drawn = new boolean [size * size];
    // A large model is drawn from a part of its faces only
    int step = Math.max(1, this.faces.size() / 20000);
    for (int faceIndex = 0; faceIndex < this.faces.size(); faceIndex += step) {
      Face face = this.faces.get(faceIndex);
      int faceColor = face.material.color;
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
        float ay = size / 2f - a [1] * scaleY;
        float bx = size / 2f + b [0] * scale;
        float by = size / 2f - b [1] * scaleY;
        float cx = size / 2f + c [0] * scale;
        float cy = size / 2f - c [1] * scaleY;
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
              colors [py * size + px] = ((int)(((faceColor >> 16) & 0xFF) * shade) << 16)
                  | ((int)(((faceColor >> 8) & 0xFF) * shade) << 8)
                  | (int)((faceColor & 0xFF) * shade);
            }
          }
        }
      }
    }
    byte [] pixels = new byte [iconSize * (1 + iconSize * 4)];
    for (int y = 0; y < iconSize; y++) {
      for (int x = 0; x < iconSize; x++) {
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
          int pixel = y * (1 + iconSize * 4) + 1 + x * 4;
          pixels [pixel] = (byte)(red / count);
          pixels [pixel + 1] = (byte)(green / count);
          pixels [pixel + 2] = (byte)(blue / count);
          pixels [pixel + 3] = (byte)Math.min(255, 255 * count * 2 / (sampling * sampling));
        }
      }
    }
    return AssistantGeneratedModel.encodePng(pixels, iconSize);
  }
}
