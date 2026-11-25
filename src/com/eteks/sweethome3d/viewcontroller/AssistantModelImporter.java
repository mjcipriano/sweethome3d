/*
 * AssistantModelImporter.java 25 nov. 2026
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.tools.OperatingSystem;
import com.eteks.sweethome3d.tools.TemporaryURLContent;

/**
 * A 3D model read from a glTF, GLB or OBJ file as triangles with their materials,
 * that can be cleaned from helper shapes, turned, simplified and recolored, then
 * written as an OBJ model with its materials and its images, the format every
 * renderer and every export of the application handles.
 * <br>Lengths are kept in the unit of the file, except for glTF and GLB files which
 * are in meters and are converted to centimeters. The vertical axis is y and the
 * front of a model is toward +z, in glTF as in the models of the application.
 * @author Sweet Home 3D contributors
 */
final class AssistantModelImporter {
  /**
   * A material of a model.
   */
  static final class Material {
    String    name;
    float []  color = {0.8f, 0.8f, 0.8f, 1};
    byte []   image;
    float     metallic;
    float     roughness = 1;
    boolean   blend;
    /** <code>false</code> for the material given to the shapes that have none in their file. */
    boolean   defined = true;
  }

  /**
   * Triangles which share a material.
   */
  static final class Mesh {
    String   name;
    Material material;
    float [] positions;
    float [] uvs;
    float [] normals;
    int []   indices;

    int getTriangleCount() {
      return this.indices.length / 3;
    }

    /**
     * Returns the lower x, y, z and the upper x, y, z of this mesh.
     */
    float [] getBounds() {
      float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      for (int i = 0; i < this.positions.length; i += 3) {
        for (int j = 0; j < 3; j++) {
          bounds [j] = Math.min(bounds [j], this.positions [i + j]);
          bounds [j + 3] = Math.max(bounds [j + 3], this.positions [i + j]);
        }
      }
      return bounds;
    }
  }

  private static final int GLB_MAGIC = 0x46546C67;
  private static final int GLB_JSON = 0x4E4F534A;
  private static final int GLB_BINARY = 0x004E4942;
  private static final long MAX_IMAGE_LENGTH = 32L * 1024 * 1024;

  private final List<Mesh>     meshes = new ArrayList<Mesh>();
  private final List<Material> materials = new ArrayList<Material>();
  private final List<String>   notes = new ArrayList<String>();
  private boolean              centimeters;

  private AssistantModelImporter() {
  }

  /**
   * Returns <code>true</code> if the name of the given file is the one of a format this class reads.
   */
  static boolean isReadable(String fileName) {
    String name = fileName.toLowerCase(Locale.ENGLISH);
    return name.endsWith(".glb") || name.endsWith(".gltf") || name.endsWith(".obj");
  }

  /**
   * Returns <code>true</code> if the name of the given file is the one of a glTF or a GLB file.
   */
  static boolean isGltf(String fileName) {
    String name = fileName.toLowerCase(Locale.ENGLISH);
    return name.endsWith(".glb") || name.endsWith(".gltf");
  }

  /**
   * Reads the model of a glTF, GLB or OBJ file.
   */
  static AssistantModelImporter read(File file, long maxLength) throws IOException {
    AssistantModelImporter model = new AssistantModelImporter();
    String name = file.getName().toLowerCase(Locale.ENGLISH);
    if (name.endsWith(".glb") || name.endsWith(".gltf")) {
      model.readGltf(file, maxLength);
      model.centimeters = true;
    } else if (name.endsWith(".obj")) {
      model.readObj(file, maxLength);
    } else {
      throw new IOException("only glTF, GLB and OBJ files are read this way");
    }
    // Materials named as the application will read them in the converted model
    for (Material material : model.materials) {
      material.name = getSafeName(material.name, "material");
    }
    // Shapes without any triangle
    for (int i = model.meshes.size() - 1; i >= 0; i--) {
      if (model.meshes.get(i).indices.length < 3) {
        model.meshes.remove(i);
      }
    }
    if (model.meshes.isEmpty()) {
      throw new IOException(file.getName() + " contains no 3D shape");
    }
    return model;
  }

  /**
   * Returns <code>true</code> if the lengths of this model are in centimeters,
   * and <code>false</code> if their unit is the unknown one of its file.
   */
  boolean isInCentimeters() {
    return this.centimeters;
  }

  /**
   * Returns what was found in the file and couldn't be read.
   */
  List<String> getNotes() {
    return this.notes;
  }

  List<Mesh> getMeshes() {
    return this.meshes;
  }

  int getTriangleCount() {
    int count = 0;
    for (Mesh mesh : this.meshes) {
      count += mesh.getTriangleCount();
    }
    return count;
  }

  /**
   * Returns the lower x, y, z and the upper x, y, z of this model.
   */
  float [] getBounds() {
    return getBounds(this.meshes, null);
  }

  private static float [] getBounds(List<Mesh> meshes, Mesh excludedMesh) {
    float [] bounds = null;
    for (Mesh mesh : meshes) {
      if (mesh != excludedMesh) {
        float [] meshBounds = mesh.getBounds();
        if (bounds == null) {
          bounds = meshBounds;
        } else {
          for (int i = 0; i < 3; i++) {
            bounds [i] = Math.min(bounds [i], meshBounds [i]);
            bounds [i + 3] = Math.max(bounds [i + 3], meshBounds [i + 3]);
          }
        }
      }
    }
    return bounds;
  }

  /**
   * Returns the width (along x), the depth (along z) and the height (along y) of this model.
   */
  float [] getSize() {
    float [] bounds = getBounds();
    return new float [] {bounds [3] - bounds [0], bounds [5] - bounds [2], bounds [4] - bounds [1]};
  }

  /**
   * Returns the names of the materials used by this model.
   */
  List<String> getMaterialNames() {
    List<String> names = new ArrayList<String>();
    for (Mesh mesh : this.meshes) {
      if (!names.contains(mesh.material.name)) {
        names.add(mesh.material.name);
      }
    }
    return names;
  }

  // ---- Changes ----

  /**
   * Removes the shapes that aren't a part of the object a model shows: the ones
   * without material in a model which has materials, the flat planes laid under it
   * as a ground or a shadow, and a sphere around it.
   * @return a description of each shape removed
   */
  List<String> dropHelpers() {
    List<String> dropped = new ArrayList<String>();
    boolean materialDefined = false;
    for (Mesh mesh : this.meshes) {
      materialDefined |= mesh.material.defined;
    }
    for (int i = this.meshes.size() - 1; i >= 0 && this.meshes.size() > 1; i--) {
      Mesh mesh = this.meshes.get(i);
      float [] rest = getBounds(this.meshes, mesh);
      float [] bounds = mesh.getBounds();
      float width = bounds [3] - bounds [0];
      float height = bounds [4] - bounds [1];
      float depth = bounds [5] - bounds [2];
      float restHeight = rest [4] - rest [1];
      float restLargest = Math.max(rest [3] - rest [0], Math.max(restHeight, rest [5] - rest [2]));
      String reason = null;
      if (isSphere(mesh, bounds)
          && Math.max(width, Math.max(height, depth)) >= 0.4f * restLargest
          && mesh.material.image == null
          && contains(bounds, (rest [0] + rest [3]) / 2, (rest [1] + rest [4]) / 2, (rest [2] + rest [5]) / 2)) {
        reason = "a sphere of " + formatLength(width) + " around the model";
      } else if (height <= Math.max(width, depth) * 0.01f
                 && mesh.getTriangleCount() <= 200
                 && bounds [4] <= rest [1] + restHeight * 0.02f
                 && Math.max(width, depth) >= 0.3f * restLargest) {
        reason = "a flat plane of " + formatLength(width) + " x " + formatLength(depth) + " under the model";
      } else if (materialDefined && !mesh.material.defined) {
        reason = "a shape without material";
      }
      if (reason != null) {
        dropped.add("\"" + mesh.name + "\" (" + reason + ", " + mesh.getTriangleCount() + " triangles)");
        this.meshes.remove(i);
      }
    }
    java.util.Collections.reverse(dropped);
    return dropped;
  }

  private String formatLength(float length) {
    return this.centimeters ? Math.round(length) + " cm" : String.valueOf(Math.round(length * 100) / 100f);
  }

  private static boolean contains(float [] bounds, float x, float y, float z) {
    return x >= bounds [0] && x <= bounds [3] && y >= bounds [1] && y <= bounds [4] && z >= bounds [2] && z <= bounds [5];
  }

  /**
   * Returns <code>true</code> if the vertices of <code>mesh</code> are all at the same distance of its center.
   */
  private static boolean isSphere(Mesh mesh, float [] bounds) {
    int count = mesh.positions.length / 3;
    if (count < 24) {
      return false;
    }
    float centerX = (bounds [0] + bounds [3]) / 2;
    float centerY = (bounds [1] + bounds [4]) / 2;
    float centerZ = (bounds [2] + bounds [5]) / 2;
    double sum = 0;
    double squareSum = 0;
    for (int i = 0; i < mesh.positions.length; i += 3) {
      double radius = Math.sqrt(Math.pow(mesh.positions [i] - centerX, 2) + Math.pow(mesh.positions [i + 1] - centerY, 2)
          + Math.pow(mesh.positions [i + 2] - centerZ, 2));
      sum += radius;
      squareSum += radius * radius;
    }
    double mean = sum / count;
    double deviation = Math.sqrt(Math.max(0, squareSum / count - mean * mean));
    return mean > 0 && deviation / mean < 0.03;
  }

  /**
   * Turns this model around the vertical so the side given as its front comes to the front.
   * @param front the side of the model as it is in its file which is its front: <code>+y</code>
   *          (its front, nothing changes), <code>-y</code> (its back), <code>+x</code> (its right)
   *          or <code>-x</code> (its left), x and y being the axes of the plan for a piece that isn't turned
   * @return <code>false</code> if <code>front</code> isn't one of these
   */
  boolean turnFront(String front) {
    String side = front.trim().toLowerCase(Locale.ENGLISH).replace(" ", "");
    if ("+y".equals(side) || "y".equals(side) || "front".equals(side)) {
      return true;
    }
    // The new x and z of a vertex from the ones it has
    float xx;
    float xz;
    float zx;
    float zz;
    if ("-y".equals(side) || "back".equals(side)) {
      xx = -1; xz = 0; zx = 0; zz = -1;
    } else if ("+x".equals(side) || "x".equals(side) || "right".equals(side)) {
      xx = 0; xz = -1; zx = 1; zz = 0;
    } else if ("-x".equals(side) || "left".equals(side)) {
      xx = 0; xz = 1; zx = -1; zz = 0;
    } else {
      return false;
    }
    for (Mesh mesh : this.meshes) {
      for (float [] vectors : new float [][] {mesh.positions, mesh.normals}) {
        if (vectors != null) {
          for (int i = 0; i < vectors.length; i += 3) {
            float x = vectors [i];
            float z = vectors [i + 2];
            vectors [i] = xx * x + xz * z;
            vectors [i + 2] = zx * x + zz * z;
          }
        }
      }
    }
    return true;
  }

  /**
   * Multiplies the lengths of this model.
   */
  void scale(float scale) {
    for (Mesh mesh : this.meshes) {
      for (int i = 0; i < mesh.positions.length; i++) {
        mesh.positions [i] *= scale;
      }
    }
  }

  /**
   * Gives a color to the materials whose name is or contains one of the given names,
   * in place of their color and of their image.
   * @param colors RGB colors by name of material
   * @return the names that match no material
   */
  List<String> recolor(Map<String, Integer> colors) {
    List<String> unknownNames = new ArrayList<String>();
    for (Map.Entry<String, Integer> entry : colors.entrySet()) {
      String name = getSafeName(entry.getKey(), entry.getKey().trim()).toLowerCase(Locale.ENGLISH);
      List<Material> matches = new ArrayList<Material>();
      for (Mesh mesh : this.meshes) {
        String materialName = mesh.material.name.toLowerCase(Locale.ENGLISH);
        if (materialName.equals(name)) {
          matches.clear();
          matches.add(mesh.material);
          break;
        } else if (materialName.contains(name) && !matches.contains(mesh.material)) {
          matches.add(mesh.material);
        }
      }
      if (matches.isEmpty()) {
        unknownNames.add(entry.getKey());
      }
      for (Material material : matches) {
        int color = entry.getValue().intValue();
        material.color [0] = ((color >> 16) & 0xFF) / 255f;
        material.color [1] = ((color >> 8) & 0xFF) / 255f;
        material.color [2] = (color & 0xFF) / 255f;
        material.image = null;
      }
    }
    return unknownNames;
  }

  /**
   * Reduces this model to <code>targetTriangles</code> triangles at most, by merging the
   * vertices which are in the same cell of a grid, as small as the target allows. The outline
   * of the model is kept; its thin details and the sharpness of its edges aren't.
   * @return the count of triangles left
   */
  int simplify(int targetTriangles) {
    targetTriangles = Math.max(4, targetTriangles);
    if (getTriangleCount() <= targetTriangles) {
      return getTriangleCount();
    }
    float [] bounds = getBounds();
    double diagonal = Math.sqrt(Math.pow(bounds [3] - bounds [0], 2) + Math.pow(bounds [4] - bounds [1], 2)
        + Math.pow(bounds [5] - bounds [2], 2));
    if (!(diagonal > 0)) {
      return getTriangleCount();
    }
    // The smallest cell which leaves few enough triangles
    double smallCell = diagonal / 100000;
    double largeCell = diagonal / 2;
    for (int i = 0; i < 24; i++) {
      double cell = Math.sqrt(smallCell * largeCell);
      if (countClusteredTriangles(bounds, cell) <= targetTriangles) {
        largeCell = cell;
      } else {
        smallCell = cell;
      }
    }
    for (Mesh mesh : this.meshes) {
      cluster(mesh, bounds, largeCell);
    }
    for (int i = this.meshes.size() - 1; i >= 0; i--) {
      if (this.meshes.get(i).indices.length < 3) {
        this.meshes.remove(i);
      }
    }
    return getTriangleCount();
  }

  private static long getCell(float [] positions, int vertex, float [] bounds, double cell) {
    long x = (long)((positions [vertex * 3] - bounds [0]) / cell);
    long y = (long)((positions [vertex * 3 + 1] - bounds [1]) / cell);
    long z = (long)((positions [vertex * 3 + 2] - bounds [2]) / cell);
    return x | (y << 21) | (z << 42);
  }

  private int countClusteredTriangles(float [] bounds, double cell) {
    int count = 0;
    for (Mesh mesh : this.meshes) {
      long [] cells = new long [mesh.positions.length / 3];
      for (int i = 0; i < cells.length; i++) {
        cells [i] = getCell(mesh.positions, i, bounds, cell);
      }
      for (int i = 0; i + 2 < mesh.indices.length; i += 3) {
        long cell1 = cells [mesh.indices [i]];
        long cell2 = cells [mesh.indices [i + 1]];
        long cell3 = cells [mesh.indices [i + 2]];
        if (cell1 != cell2 && cell2 != cell3 && cell1 != cell3) {
          count++;
        }
      }
    }
    return count;
  }

  private static void cluster(Mesh mesh, float [] bounds, double cell) {
    int vertexCount = mesh.positions.length / 3;
    Map<Long, Integer> clusters = new HashMap<Long, Integer>();
    int [] vertexClusters = new int [vertexCount];
    for (int i = 0; i < vertexCount; i++) {
      Long key = Long.valueOf(getCell(mesh.positions, i, bounds, cell));
      Integer cluster = clusters.get(key);
      if (cluster == null) {
        cluster = Integer.valueOf(clusters.size());
        clusters.put(key, cluster);
      }
      vertexClusters [i] = cluster.intValue();
    }
    int clusterCount = clusters.size();
    // Each cluster at the average of its vertices
    float [] positions = new float [clusterCount * 3];
    float [] uvs = mesh.uvs != null ? new float [clusterCount * 2] : null;
    float [] normals = mesh.normals != null ? new float [clusterCount * 3] : null;
    int [] counts = new int [clusterCount];
    for (int i = 0; i < vertexCount; i++) {
      int cluster = vertexClusters [i];
      counts [cluster]++;
      for (int j = 0; j < 3; j++) {
        positions [cluster * 3 + j] += mesh.positions [i * 3 + j];
        if (normals != null) {
          normals [cluster * 3 + j] += mesh.normals [i * 3 + j];
        }
      }
      if (uvs != null) {
        uvs [cluster * 2] += mesh.uvs [i * 2];
        uvs [cluster * 2 + 1] += mesh.uvs [i * 2 + 1];
      }
    }
    for (int i = 0; i < clusterCount; i++) {
      for (int j = 0; j < 3; j++) {
        positions [i * 3 + j] /= counts [i];
      }
      if (uvs != null) {
        uvs [i * 2] /= counts [i];
        uvs [i * 2 + 1] /= counts [i];
      }
      if (normals != null) {
        double length = Math.sqrt(normals [i * 3] * normals [i * 3] + normals [i * 3 + 1] * normals [i * 3 + 1]
            + normals [i * 3 + 2] * normals [i * 3 + 2]);
        if (length > 1E-6) {
          for (int j = 0; j < 3; j++) {
            normals [i * 3 + j] /= length;
          }
        } else {
          normals [i * 3 + 1] = 1;
        }
      }
    }
    // The triangles which still have three corners, once each
    int [] indices = new int [mesh.indices.length];
    int count = 0;
    Set<Long> triangles = clusterCount < (1 << 21) ? new HashSet<Long>() : null;
    for (int i = 0; i + 2 < mesh.indices.length; i += 3) {
      int index1 = vertexClusters [mesh.indices [i]];
      int index2 = vertexClusters [mesh.indices [i + 1]];
      int index3 = vertexClusters [mesh.indices [i + 2]];
      if (index1 != index2 && index2 != index3 && index1 != index3) {
        if (triangles != null) {
          int low = Math.min(index1, Math.min(index2, index3));
          int high = Math.max(index1, Math.max(index2, index3));
          long middle = (long)index1 + index2 + index3 - low - high;
          if (!triangles.add(Long.valueOf(low | (middle << 21) | ((long)high << 42)))) {
            continue;
          }
        }
        indices [count++] = index1;
        indices [count++] = index2;
        indices [count++] = index3;
      }
    }
    mesh.positions = positions;
    mesh.uvs = uvs;
    mesh.normals = normals;
    mesh.indices = Arrays.copyOf(indices, count);
  }

  // ---- OBJ output ----

  /**
   * Returns the files of this model at OBJ format: <code>model.obj</code>, <code>model.mtl</code>
   * and the images of its materials.
   */
  Map<String, byte []> createFiles() throws IOException {
    Map<String, byte []> files = new LinkedHashMap<String, byte []>();
    ByteArrayOutputStream objBytes = new ByteArrayOutputStream(1 << 16);
    Writer obj = new OutputStreamWriter(objBytes, "US-ASCII");
    obj.write("# Model converted by Sweet Home 3D\nmtllib model.mtl\n");
    StringBuilder mtl = new StringBuilder("# Materials converted by Sweet Home 3D\n");
    List<Material> writtenMaterials = new ArrayList<Material>();
    Map<Material, String> materialNames = new HashMap<Material, String>();
    Set<String> usedNames = new HashSet<String>();
    int vertexOffset = 1;
    int uvOffset = 1;
    int normalOffset = 1;
    int meshIndex = 0;
    for (Mesh mesh : this.meshes) {
      String materialName = materialNames.get(mesh.material);
      if (materialName == null) {
        materialName = getUniqueName(mesh.material.name, "material", usedNames);
        materialNames.put(mesh.material, materialName);
        writtenMaterials.add(mesh.material);
        writeMaterial(mtl, materialName, mesh.material, writtenMaterials.size(), files);
      }
      obj.write("g " + getSafeName(mesh.name, "shape") + "_" + (++meshIndex) + "\nusemtl " + materialName + "\n");
      int vertexCount = mesh.positions.length / 3;
      for (int i = 0; i < vertexCount; i++) {
        obj.write("v " + mesh.positions [i * 3] + " " + mesh.positions [i * 3 + 1] + " " + mesh.positions [i * 3 + 2] + "\n");
      }
      if (mesh.uvs != null) {
        for (int i = 0; i < vertexCount; i++) {
          obj.write("vt " + mesh.uvs [i * 2] + " " + mesh.uvs [i * 2 + 1] + "\n");
        }
      }
      if (mesh.normals != null) {
        for (int i = 0; i < vertexCount; i++) {
          obj.write("vn " + mesh.normals [i * 3] + " " + mesh.normals [i * 3 + 1] + " " + mesh.normals [i * 3 + 2] + "\n");
        }
      }
      for (int i = 0; i + 2 < mesh.indices.length; i += 3) {
        obj.write("f");
        for (int j = 0; j < 3; j++) {
          int index = mesh.indices [i + j];
          obj.write(" " + (vertexOffset + index));
          if (mesh.uvs != null || mesh.normals != null) {
            obj.write("/" + (mesh.uvs != null ? String.valueOf(uvOffset + index) : ""));
            if (mesh.normals != null) {
              obj.write("/" + (normalOffset + index));
            }
          }
        }
        obj.write("\n");
      }
      vertexOffset += vertexCount;
      if (mesh.uvs != null) {
        uvOffset += vertexCount;
      }
      if (mesh.normals != null) {
        normalOffset += vertexCount;
      }
    }
    obj.close();
    Map<String, byte []> orderedFiles = new LinkedHashMap<String, byte []>();
    orderedFiles.put("model.obj", objBytes.toByteArray());
    orderedFiles.put("model.mtl", mtl.toString().getBytes("US-ASCII"));
    orderedFiles.putAll(files);
    return orderedFiles;
  }

  /**
   * Returns the content of this model at OBJ format, stored in a temporary archive.
   */
  Content createContent() throws IOException {
    File file = OperatingSystem.createTemporaryFile("model", ".zip");
    AssistantGeneratedModel.writeZip(file, createFiles());
    return new TemporaryURLContent(new URL("jar:" + file.toURI().toURL() + "!/model.obj"));
  }

  private static void writeMaterial(StringBuilder mtl, String name, Material material, int index,
                                    Map<String, byte []> files) {
    float [] color = material.color;
    mtl.append("newmtl ").append(name).append('\n');
    mtl.append("Kd ").append(color [0]).append(' ').append(color [1]).append(' ').append(color [2]).append('\n');
    // A smooth material shines, and a metal reflects its own color
    float gloss = Math.max(0, Math.min(1, 1 - material.roughness));
    float metallic = Math.max(0, Math.min(1, material.metallic));
    if (gloss > 0.1f) {
      float specular = gloss * (0.25f + 0.75f * metallic);
      mtl.append("Ks ").append(specular * (1 - metallic + metallic * color [0])).append(' ')
          .append(specular * (1 - metallic + metallic * color [1])).append(' ')
          .append(specular * (1 - metallic + metallic * color [2])).append('\n');
      mtl.append("Ns ").append(Math.round(1 + gloss * gloss * 127)).append('\n');
      mtl.append("illum 2\n");
    } else {
      mtl.append("illum 1\n");
    }
    if (material.blend && color [3] < 0.999f) {
      mtl.append("d ").append(Math.max(0, color [3])).append('\n');
    }
    if (material.image != null) {
      String imageName = "texture_" + index + "." + (isPNG(material.image) ? "png" : "jpg");
      files.put(imageName, material.image);
      mtl.append("map_Kd ").append(imageName).append('\n');
    }
  }

  private static String getSafeName(String name, String defaultName) {
    String safeName = name != null ? name.trim().replaceAll("[^A-Za-z0-9_.-]+", "_") : "";
    return safeName.length() > 0 && !safeName.matches("_+") ? safeName : defaultName;
  }

  private static String getUniqueName(String name, String defaultName, Set<String> usedNames) {
    String safeName = getSafeName(name, defaultName);
    String uniqueName = safeName;
    for (int i = 2; !usedNames.add(uniqueName.toLowerCase(Locale.ENGLISH)); i++) {
      uniqueName = safeName + "_" + i;
    }
    return uniqueName;
  }

  private static boolean isPNG(byte [] image) {
    return image.length > 8 && (image [0] & 0xFF) == 0x89 && image [1] == 'P' && image [2] == 'N' && image [3] == 'G';
  }

  private static boolean isJPEG(byte [] image) {
    return image.length > 3 && (image [0] & 0xFF) == 0xFF && (image [1] & 0xFF) == 0xD8;
  }

  private void note(String note) {
    if (!this.notes.contains(note)) {
      this.notes.add(note);
    }
  }

  // ---- glTF and GLB input ----

  @SuppressWarnings("unchecked")
  private void readGltf(File file, long maxLength) throws IOException {
    byte [] data = AssistantGeneratedModel.readFile(file, maxLength);
    byte [] binaryChunk = null;
    String text;
    if (data.length >= 12 && ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
      ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
      if (buffer.getInt(4) != 2) {
        throw new IOException(file.getName() + " is a GLB file of version " + buffer.getInt(4) + ": only the version 2 is read");
      }
      text = null;
      int offset = 12;
      while (offset + 8 <= data.length) {
        int length = buffer.getInt(offset);
        int type = buffer.getInt(offset + 4);
        if (length < 0 || offset + 8 + (long)length > data.length) {
          throw new IOException(file.getName() + " is cut short");
        }
        if (type == GLB_JSON && text == null) {
          text = new String(data, offset + 8, length, "UTF-8");
        } else if (type == GLB_BINARY && binaryChunk == null) {
          binaryChunk = Arrays.copyOfRange(data, offset + 8, offset + 8 + length);
        }
        offset += 8 + length + ((4 - length % 4) % 4);
      }
      if (text == null) {
        throw new IOException(file.getName() + " holds no description of its content");
      }
    } else {
      text = new String(data, "UTF-8");
    }
    Object parsed;
    try {
      parsed = JSONParser.parse(text);
    } catch (RuntimeException ex) {
      throw new IOException(file.getName() + " isn't a glTF file (" + ex.getMessage() + ")");
    }
    if (!(parsed instanceof Map)) {
      throw new IOException(file.getName() + " isn't a glTF file");
    }
    Map<String, Object> gltf = (Map<String, Object>)parsed;
    for (Object extension : list(gltf.get("extensionsRequired"))) {
      String name = String.valueOf(extension);
      if (name.contains("draco") || name.contains("meshopt")) {
        throw new IOException(file.getName() + " is compressed with " + name
            + ", which isn't read: export it again without mesh compression");
      } else if (name.contains("basisu") || name.contains("webp")) {
        note("its images use " + name + ", which isn't read: the materials keep their colors only");
      } else if (!name.contains("texture_transform") && !name.contains("pbrSpecularGlossiness")) {
        note("the extension " + name + " isn't read");
      }
    }
    GltfReader reader = new GltfReader(gltf, binaryChunk, file.getParentFile(), maxLength);
    reader.readMaterials();
    // The shapes of the nodes of the default scene, or of every node in a file without scene
    List<Object> nodes = list(gltf.get("nodes"));
    List<Object> scenes = list(gltf.get("scenes"));
    List<Object> roots = new ArrayList<Object>();
    if (!scenes.isEmpty()) {
      int scene = gltf.get("scene") instanceof Number ? ((Number)gltf.get("scene")).intValue() : 0;
      Object sceneObject = scenes.get(Math.max(0, Math.min(scenes.size() - 1, scene)));
      if (sceneObject instanceof Map) {
        roots.addAll(list(((Map<String, Object>)sceneObject).get("nodes")));
      }
    }
    if (roots.isEmpty()) {
      Set<Integer> children = new HashSet<Integer>();
      for (Object node : nodes) {
        if (node instanceof Map) {
          for (Object child : list(((Map<String, Object>)node).get("children"))) {
            children.add(Integer.valueOf(((Number)child).intValue()));
          }
        }
      }
      for (int i = 0; i < nodes.size(); i++) {
        if (!children.contains(Integer.valueOf(i))) {
          roots.add(Double.valueOf(i));
        }
      }
    }
    // From meters to centimeters
    double [] toCentimeters = {100, 0, 0, 0, 0, 100, 0, 0, 0, 0, 100, 0, 0, 0, 0, 1};
    for (Object root : roots) {
      if (root instanceof Number) {
        reader.readNode(((Number)root).intValue(), toCentimeters, 0);
      }
    }
    if (!list(gltf.get("animations")).isEmpty()) {
      note("its animations aren't read: the model is in its rest pose");
    }
    if (!list(gltf.get("skins")).isEmpty()) {
      note("its skins aren't read: the shapes bound to a skeleton are in their rest pose");
    }
  }

  private static List<Object> list(Object value) {
    if (value instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>)value;
      return list;
    }
    return new ArrayList<Object>();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map ? (Map<String, Object>)value : new HashMap<String, Object>();
  }

  private static double number(Map<String, Object> object, String key, double defaultValue) {
    return object.get(key) instanceof Number ? ((Number)object.get(key)).doubleValue() : defaultValue;
  }

  private static int integer(Map<String, Object> object, String key, int defaultValue) {
    return object.get(key) instanceof Number ? ((Number)object.get(key)).intValue() : defaultValue;
  }

  /**
   * Reads the buffers, the materials and the nodes of a glTF description.
   */
  private final class GltfReader {
    private final Map<String, Object> gltf;
    private final byte []             binaryChunk;
    private final File                folder;
    private final long                maxLength;
    private final List<Object>        accessors;
    private final List<Object>        bufferViews;
    private final List<Object>        nodes;
    private final List<Object>        gltfMeshes;
    private final Map<Integer, byte []> buffers = new HashMap<Integer, byte []>();
    private final List<Material>      gltfMaterials = new ArrayList<Material>();
    private Material                  defaultMaterial;
    private long                      readLength;

    GltfReader(Map<String, Object> gltf, byte [] binaryChunk, File folder, long maxLength) {
      this.gltf = gltf;
      this.binaryChunk = binaryChunk;
      this.folder = folder;
      this.maxLength = maxLength;
      this.accessors = list(gltf.get("accessors"));
      this.bufferViews = list(gltf.get("bufferViews"));
      this.nodes = list(gltf.get("nodes"));
      this.gltfMeshes = list(gltf.get("meshes"));
    }

    /**
     * Returns the data of an embedded or sibling file.
     */
    private byte [] readUri(String uri, long maxFileLength) throws IOException {
      if (uri.startsWith("data:")) {
        int comma = uri.indexOf(',');
        if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) {
          throw new IOException("a data of the file isn't encoded in base 64");
        }
        try {
          return java.util.Base64.getDecoder().decode(uri.substring(comma + 1));
        } catch (IllegalArgumentException ex) {
          throw new IOException("a data of the file isn't encoded in base 64");
        }
      } else if (uri.contains("://") || uri.startsWith("file:")) {
        throw new IOException("the file refers to " + uri + ": only the files beside it are read");
      }
      String name = java.net.URLDecoder.decode(uri.replace("+", "%2B"), "UTF-8");
      if (this.folder == null) {
        throw new IOException("the file " + name + " isn't beside the model");
      }
      File sibling = new File(this.folder, name.replace('\\', '/'));
      // Nothing out of the folder of the model
      if (!sibling.isFile()
          || !sibling.getCanonicalPath().startsWith(this.folder.getCanonicalPath() + File.separator)) {
        throw new IOException("the file " + name + " isn't beside the model");
      }
      this.readLength += sibling.length();
      if (this.readLength > this.maxLength * 2) {
        throw new IOException("the files of the model are too large");
      }
      return AssistantGeneratedModel.readFile(sibling, maxFileLength);
    }

    private byte [] getBuffer(int index) throws IOException {
      byte [] buffer = this.buffers.get(Integer.valueOf(index));
      if (buffer == null) {
        List<Object> gltfBuffers = list(this.gltf.get("buffers"));
        if (index < 0 || index >= gltfBuffers.size()) {
          throw new IOException("the file refers to a buffer it doesn't have");
        }
        Object uri = map(gltfBuffers.get(index)).get("uri");
        if (uri instanceof String) {
          buffer = readUri((String)uri, this.maxLength);
        } else if (index == 0 && this.binaryChunk != null) {
          buffer = this.binaryChunk;
        } else {
          throw new IOException("a buffer of the file has no data");
        }
        this.buffers.put(Integer.valueOf(index), buffer);
      }
      return buffer;
    }

    /**
     * Returns the bytes of a buffer view.
     */
    private byte [] getViewData(int index) throws IOException {
      if (index < 0 || index >= this.bufferViews.size()) {
        throw new IOException("the file refers to a buffer view it doesn't have");
      }
      Map<String, Object> view = map(this.bufferViews.get(index));
      byte [] buffer = getBuffer(integer(view, "buffer", 0));
      int offset = integer(view, "byteOffset", 0);
      int length = integer(view, "byteLength", 0);
      if (offset < 0 || length < 0 || (long)offset + length > buffer.length) {
        throw new IOException("a buffer view of the file is out of its buffer");
      }
      return Arrays.copyOfRange(buffer, offset, offset + length);
    }

    /**
     * Returns the values of an accessor as floats, <code>components</code> per item,
     * or <code>null</code> if it doesn't have that many components.
     */
    private float [] readAccessor(Object accessorIndex, int components) throws IOException {
      if (!(accessorIndex instanceof Number)) {
        return null;
      }
      int index = ((Number)accessorIndex).intValue();
      if (index < 0 || index >= this.accessors.size()) {
        throw new IOException("the file refers to an accessor it doesn't have");
      }
      Map<String, Object> accessor = map(this.accessors.get(index));
      String type = String.valueOf(accessor.get("type"));
      int accessorComponents = "SCALAR".equals(type) ? 1 : "VEC2".equals(type) ? 2 : "VEC3".equals(type) ? 3
          : "VEC4".equals(type) ? 4 : "MAT4".equals(type) ? 16 : 0;
      int count = integer(accessor, "count", 0);
      if (accessorComponents < components || count < 0 || (long)count * components > 60000000L) {
        return null;
      }
      float [] values = new float [count * components];
      if (accessor.get("sparse") != null) {
        note("its sparse accessors aren't read");
      }
      if (!(accessor.get("bufferView") instanceof Number)) {
        return values;
      }
      Map<String, Object> view = map(this.bufferViews.get(Math.max(0, Math.min(this.bufferViews.size() - 1,
          integer(accessor, "bufferView", 0)))));
      byte [] buffer = getBuffer(integer(view, "buffer", 0));
      int componentType = integer(accessor, "componentType", 5126);
      int componentSize = componentType == 5120 || componentType == 5121 ? 1
          : componentType == 5122 || componentType == 5123 ? 2 : 4;
      int stride = integer(view, "byteStride", 0);
      if (stride <= 0) {
        stride = componentSize * accessorComponents;
      }
      long start = (long)integer(view, "byteOffset", 0) + integer(accessor, "byteOffset", 0);
      if (start < 0 || count > 0 && start + (long)(count - 1) * stride + (long)componentSize * components > buffer.length) {
        throw new IOException("an accessor of the file is out of its buffer");
      }
      boolean normalized = Boolean.TRUE.equals(accessor.get("normalized"));
      ByteBuffer data = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
      for (int i = 0; i < count; i++) {
        int offset = (int)(start + (long)i * stride);
        for (int j = 0; j < components; j++) {
          int position = offset + j * componentSize;
          float value;
          switch (componentType) {
            case 5120 :
              value = data.get(position);
              value = normalized ? Math.max(-1, value / 127f) : value;
              break;
            case 5121 :
              value = data.get(position) & 0xFF;
              value = normalized ? value / 255f : value;
              break;
            case 5122 :
              value = data.getShort(position);
              value = normalized ? Math.max(-1, value / 32767f) : value;
              break;
            case 5123 :
              value = data.getShort(position) & 0xFFFF;
              value = normalized ? value / 65535f : value;
              break;
            case 5125 :
              value = data.getInt(position) & 0xFFFFFFFFL;
              break;
            default :
              value = data.getFloat(position);
              break;
          }
          values [i * components + j] = value;
        }
      }
      return values;
    }

    /**
     * Returns the indices of an accessor.
     */
    private int [] readIndices(Object accessorIndex) throws IOException {
      if (!(accessorIndex instanceof Number)) {
        return null;
      }
      Map<String, Object> accessor = map(this.accessors.get(Math.max(0, Math.min(this.accessors.size() - 1,
          ((Number)accessorIndex).intValue()))));
      int count = integer(accessor, "count", 0);
      if (!(accessor.get("bufferView") instanceof Number) || count <= 0) {
        return new int [0];
      }
      Map<String, Object> view = map(this.bufferViews.get(Math.max(0, Math.min(this.bufferViews.size() - 1,
          integer(accessor, "bufferView", 0)))));
      byte [] buffer = getBuffer(integer(view, "buffer", 0));
      int componentType = integer(accessor, "componentType", 5125);
      int componentSize = componentType == 5121 ? 1 : componentType == 5123 ? 2 : 4;
      int stride = integer(view, "byteStride", 0);
      if (stride <= 0) {
        stride = componentSize;
      }
      long start = (long)integer(view, "byteOffset", 0) + integer(accessor, "byteOffset", 0);
      if (start < 0 || start + (long)(count - 1) * stride + componentSize > buffer.length) {
        throw new IOException("the indices of a shape are out of their buffer");
      }
      ByteBuffer data = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
      int [] indices = new int [count];
      for (int i = 0; i < count; i++) {
        int position = (int)(start + (long)i * stride);
        indices [i] = componentType == 5121 ? data.get(position) & 0xFF
            : componentType == 5123 ? data.getShort(position) & 0xFFFF : data.getInt(position);
      }
      return indices;
    }

    /**
     * Returns the image of a texture, or <code>null</code> if it has none that is read.
     */
    private byte [] readTextureImage(Object textureInfo) {
      if (!(textureInfo instanceof Map)) {
        return null;
      }
      Map<String, Object> info = map(textureInfo);
      if (integer(info, "texCoord", 0) != 0) {
        note("a texture uses its second set of coordinates, which isn't read");
        return null;
      }
      if (map(info.get("extensions")).get("KHR_texture_transform") != null) {
        note("the offset, the scale and the rotation of a texture (KHR_texture_transform) aren't applied");
      }
      List<Object> textures = list(this.gltf.get("textures"));
      int textureIndex = integer(info, "index", -1);
      if (textureIndex < 0 || textureIndex >= textures.size()) {
        return null;
      }
      List<Object> images = list(this.gltf.get("images"));
      int imageIndex = integer(map(textures.get(textureIndex)), "source", -1);
      if (imageIndex < 0 || imageIndex >= images.size()) {
        note("an image at an other format than PNG or JPEG isn't read");
        return null;
      }
      Map<String, Object> image = map(images.get(imageIndex));
      try {
        byte [] data = image.get("uri") instanceof String
            ? readUri((String)image.get("uri"), MAX_IMAGE_LENGTH)
            : image.get("bufferView") instanceof Number
                ? getViewData(((Number)image.get("bufferView")).intValue())
                : null;
        if (data != null && (isPNG(data) || isJPEG(data))) {
          return data;
        }
        note("an image at an other format than PNG or JPEG isn't read");
      } catch (IOException ex) {
        note("an image couldn't be read (" + ex.getMessage() + ")");
      }
      return null;
    }

    void readMaterials() {
      int index = 0;
      for (Object item : list(this.gltf.get("materials"))) {
        Map<String, Object> gltfMaterial = map(item);
        Material material = new Material();
        index++;
        material.name = gltfMaterial.get("name") instanceof String && ((String)gltfMaterial.get("name")).trim().length() > 0
            ? ((String)gltfMaterial.get("name")).trim() : "material_" + index;
        Map<String, Object> pbr = map(gltfMaterial.get("pbrMetallicRoughness"));
        Object colorFactor = pbr.get("baseColorFactor");
        Object colorTexture = pbr.get("baseColorTexture");
        material.metallic = (float)number(pbr, "metallicFactor", 1);
        material.roughness = (float)number(pbr, "roughnessFactor", 1);
        Map<String, Object> specularGlossiness = map(map(gltfMaterial.get("extensions")).get("KHR_materials_pbrSpecularGlossiness"));
        if (gltfMaterial.get("pbrMetallicRoughness") == null && !specularGlossiness.isEmpty()) {
          colorFactor = specularGlossiness.get("diffuseFactor");
          colorTexture = specularGlossiness.get("diffuseTexture");
          material.metallic = 0;
          material.roughness = 1 - (float)number(specularGlossiness, "glossinessFactor", 1);
        }
        List<Object> factor = list(colorFactor);
        for (int i = 0; i < 4 && i < factor.size(); i++) {
          if (factor.get(i) instanceof Number) {
            // Colors are linear in a glTF file and displayed as they are in a material
            float value = Math.max(0, Math.min(1, ((Number)factor.get(i)).floatValue()));
            material.color [i] = i < 3 ? (float)Math.pow(value, 1 / 2.2) : value;
          }
        }
        if (factor.isEmpty()) {
          material.color [0] = material.color [1] = material.color [2] = 1;
        }
        material.image = readTextureImage(colorTexture);
        if (material.image != null && !factor.isEmpty()
            && (material.color [0] < 0.98f || material.color [1] < 0.98f || material.color [2] < 0.98f)) {
          note("the color that tints the image of the material " + material.name + " isn't applied to it");
        }
        if (pbr.get("metallicRoughnessTexture") != null) {
          note("the images of roughness and metal aren't read: the factors of the materials are used");
          // A factor of 1 multiplies an image that isn't read
          if (material.metallic >= 0.999f && material.roughness >= 0.999f) {
            material.metallic = 0.5f;
            material.roughness = 0.5f;
          }
        }
        material.blend = "BLEND".equals(gltfMaterial.get("alphaMode"));
        if (!material.blend) {
          material.color [3] = 1;
        }
        this.gltfMaterials.add(material);
      }
    }

    private Material getMaterial(Object index) {
      if (index instanceof Number && ((Number)index).intValue() >= 0
          && ((Number)index).intValue() < this.gltfMaterials.size()) {
        Material material = this.gltfMaterials.get(((Number)index).intValue());
        if (!materials.contains(material)) {
          materials.add(material);
        }
        return material;
      }
      if (this.defaultMaterial == null) {
        this.defaultMaterial = new Material();
        this.defaultMaterial.name = "no_material";
        this.defaultMaterial.defined = false;
        materials.add(this.defaultMaterial);
      }
      return this.defaultMaterial;
    }

    /**
     * Returns the matrix of a node, in the order of its columns.
     */
    private double [] getNodeMatrix(Map<String, Object> node) {
      List<Object> matrix = list(node.get("matrix"));
      double [] result = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
      if (matrix.size() == 16) {
        for (int i = 0; i < 16; i++) {
          result [i] = matrix.get(i) instanceof Number ? ((Number)matrix.get(i)).doubleValue() : result [i];
        }
        return result;
      }
      double [] translation = getNumbers(node.get("translation"), new double [] {0, 0, 0});
      double [] rotation = getNumbers(node.get("rotation"), new double [] {0, 0, 0, 1});
      double [] scale = getNumbers(node.get("scale"), new double [] {1, 1, 1});
      double x = rotation [0];
      double y = rotation [1];
      double z = rotation [2];
      double w = rotation [3];
      double length = Math.sqrt(x * x + y * y + z * z + w * w);
      if (length > 1E-9) {
        x /= length;
        y /= length;
        z /= length;
        w /= length;
      } else {
        x = y = z = 0;
        w = 1;
      }
      result [0] = (1 - 2 * (y * y + z * z)) * scale [0];
      result [1] = 2 * (x * y + z * w) * scale [0];
      result [2] = 2 * (x * z - y * w) * scale [0];
      result [4] = 2 * (x * y - z * w) * scale [1];
      result [5] = (1 - 2 * (x * x + z * z)) * scale [1];
      result [6] = 2 * (y * z + x * w) * scale [1];
      result [8] = 2 * (x * z + y * w) * scale [2];
      result [9] = 2 * (y * z - x * w) * scale [2];
      result [10] = (1 - 2 * (x * x + y * y)) * scale [2];
      result [12] = translation [0];
      result [13] = translation [1];
      result [14] = translation [2];
      return result;
    }

    private double [] getNumbers(Object value, double [] defaultValues) {
      List<Object> numbers = list(value);
      if (numbers.size() == defaultValues.length) {
        for (int i = 0; i < defaultValues.length; i++) {
          if (numbers.get(i) instanceof Number) {
            defaultValues [i] = ((Number)numbers.get(i)).doubleValue();
          }
        }
      }
      return defaultValues;
    }

    private double [] multiply(double [] a, double [] b) {
      double [] result = new double [16];
      for (int column = 0; column < 4; column++) {
        for (int row = 0; row < 4; row++) {
          double sum = 0;
          for (int k = 0; k < 4; k++) {
            sum += a [k * 4 + row] * b [column * 4 + k];
          }
          result [column * 4 + row] = sum;
        }
      }
      return result;
    }

    void readNode(int index, double [] parentMatrix, int depth) throws IOException {
      if (index < 0 || index >= this.nodes.size() || depth > 64) {
        return;
      }
      Map<String, Object> node = map(this.nodes.get(index));
      double [] matrix = multiply(parentMatrix, getNodeMatrix(node));
      if (node.get("mesh") instanceof Number) {
        int meshIndex = ((Number)node.get("mesh")).intValue();
        if (meshIndex >= 0 && meshIndex < this.gltfMeshes.size()) {
          Map<String, Object> gltfMesh = map(this.gltfMeshes.get(meshIndex));
          String name = node.get("name") instanceof String ? (String)node.get("name")
              : gltfMesh.get("name") instanceof String ? (String)gltfMesh.get("name") : "mesh_" + (meshIndex + 1);
          for (Object primitive : list(gltfMesh.get("primitives"))) {
            readPrimitive(map(primitive), name, matrix);
          }
        }
      }
      for (Object child : list(node.get("children"))) {
        if (child instanceof Number) {
          readNode(((Number)child).intValue(), matrix, depth + 1);
        }
      }
    }

    private void readPrimitive(Map<String, Object> primitive, String name, double [] matrix) throws IOException {
      if (map(primitive.get("extensions")).get("KHR_draco_mesh_compression") != null) {
        throw new IOException("a shape is compressed with Draco, which isn't read: export the model again without mesh compression");
      }
      int mode = integer(primitive, "mode", 4);
      if (mode != 4 && mode != 5 && mode != 6) {
        note("its points and its lines aren't read");
        return;
      }
      Map<String, Object> attributes = map(primitive.get("attributes"));
      float [] positions = readAccessor(attributes.get("POSITION"), 3);
      if (positions == null || positions.length < 9) {
        return;
      }
      float [] normals = readAccessor(attributes.get("NORMAL"), 3);
      float [] uvs = readAccessor(attributes.get("TEXCOORD_0"), 2);
      int vertexCount = positions.length / 3;
      if (normals != null && normals.length != positions.length) {
        normals = null;
      }
      if (uvs != null && uvs.length != vertexCount * 2) {
        uvs = null;
      }
      if (attributes.get("COLOR_0") != null) {
        note("the colors of its vertices aren't read");
      }
      if (primitive.get("targets") != null) {
        note("its morph targets aren't read");
      }
      int [] indices = readIndices(primitive.get("indices"));
      if (indices == null) {
        indices = new int [vertexCount];
        for (int i = 0; i < vertexCount; i++) {
          indices [i] = i;
        }
      }
      if (mode != 4) {
        // Strips and fans as triangles
        int [] triangles = new int [Math.max(0, indices.length - 2) * 3];
        for (int i = 0; i + 2 < indices.length; i++) {
          if (mode == 5) {
            triangles [i * 3] = indices [i % 2 == 0 ? i : i + 1];
            triangles [i * 3 + 1] = indices [i % 2 == 0 ? i + 1 : i];
          } else {
            triangles [i * 3] = indices [0];
            triangles [i * 3 + 1] = indices [i + 1];
          }
          triangles [i * 3 + 2] = indices [i + 2];
        }
        indices = triangles;
      }
      indices = Arrays.copyOf(indices, indices.length / 3 * 3);
      for (int vertex : indices) {
        if (vertex < 0 || vertex >= vertexCount) {
          throw new IOException("a shape refers to a vertex it doesn't have");
        }
      }
      // A transformation that mirrors turns the triangles inside out
      double determinant = matrix [0] * (matrix [5] * matrix [10] - matrix [9] * matrix [6])
          - matrix [4] * (matrix [1] * matrix [10] - matrix [9] * matrix [2])
          + matrix [8] * (matrix [1] * matrix [6] - matrix [5] * matrix [2]);
      if (determinant < 0) {
        for (int i = 0; i + 2 < indices.length; i += 3) {
          int index = indices [i + 1];
          indices [i + 1] = indices [i + 2];
          indices [i + 2] = index;
        }
      }
      for (int i = 0; i < vertexCount; i++) {
        double x = positions [i * 3];
        double y = positions [i * 3 + 1];
        double z = positions [i * 3 + 2];
        positions [i * 3] = (float)(matrix [0] * x + matrix [4] * y + matrix [8] * z + matrix [12]);
        positions [i * 3 + 1] = (float)(matrix [1] * x + matrix [5] * y + matrix [9] * z + matrix [13]);
        positions [i * 3 + 2] = (float)(matrix [2] * x + matrix [6] * y + matrix [10] * z + matrix [14]);
        if (uvs != null) {
          // The origin of an image is at its top in a glTF file, at its bottom in an OBJ file
          uvs [i * 2 + 1] = 1 - uvs [i * 2 + 1];
        }
      }
      if (normals != null) {
        // Normals follow the inverse of the transposed matrix, the cofactors of its upper part
        double [] cofactors = {
            matrix [5] * matrix [10] - matrix [9] * matrix [6], matrix [9] * matrix [2] - matrix [1] * matrix [10], matrix [1] * matrix [6] - matrix [5] * matrix [2],
            matrix [8] * matrix [6] - matrix [4] * matrix [10], matrix [0] * matrix [10] - matrix [8] * matrix [2], matrix [4] * matrix [2] - matrix [0] * matrix [6],
            matrix [4] * matrix [9] - matrix [8] * matrix [5], matrix [8] * matrix [1] - matrix [0] * matrix [9], matrix [0] * matrix [5] - matrix [4] * matrix [1]};
        double sign = determinant < 0 ? -1 : 1;
        for (int i = 0; i < vertexCount; i++) {
          double x = normals [i * 3];
          double y = normals [i * 3 + 1];
          double z = normals [i * 3 + 2];
          double normalX = sign * (cofactors [0] * x + cofactors [1] * y + cofactors [2] * z);
          double normalY = sign * (cofactors [3] * x + cofactors [4] * y + cofactors [5] * z);
          double normalZ = sign * (cofactors [6] * x + cofactors [7] * y + cofactors [8] * z);
          double length = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
          if (length > 1E-12) {
            normals [i * 3] = (float)(normalX / length);
            normals [i * 3 + 1] = (float)(normalY / length);
            normals [i * 3 + 2] = (float)(normalZ / length);
          } else {
            normals [i * 3] = 0;
            normals [i * 3 + 1] = 1;
            normals [i * 3 + 2] = 0;
          }
        }
      }
      Mesh mesh = new Mesh();
      mesh.name = name;
      mesh.material = getMaterial(primitive.get("material"));
      mesh.positions = positions;
      mesh.normals = normals;
      mesh.uvs = mesh.material.image != null ? uvs : null;
      mesh.indices = indices;
      meshes.add(mesh);
    }
  }

  // ---- OBJ input ----

  private void readObj(File file, long maxLength) throws IOException {
    String text = new String(AssistantGeneratedModel.readFile(file, maxLength), "ISO-8859-1");
    File folder = file.getParentFile();
    Map<String, Material> objMaterials = new LinkedHashMap<String, Material>();
    FloatList vertices = new FloatList();
    FloatList textureCoordinates = new FloatList();
    FloatList vertexNormals = new FloatList();
    ObjMesh mesh = null;
    String group = "shape";
    Material material = null;
    int lineStart = 0;
    int length = text.length();
    while (lineStart < length) {
      int lineEnd = text.indexOf('\n', lineStart);
      if (lineEnd < 0) {
        lineEnd = length;
      }
      String line = text.substring(lineStart, lineEnd).trim();
      lineStart = lineEnd + 1;
      if (line.length() < 2 || line.charAt(0) == '#') {
        continue;
      }
      if (line.startsWith("v ")) {
        addNumbers(vertices, line, 2, 3);
      } else if (line.startsWith("vt ")) {
        addNumbers(textureCoordinates, line, 3, 2);
      } else if (line.startsWith("vn ")) {
        addNumbers(vertexNormals, line, 3, 3);
      } else if (line.startsWith("f ")) {
        if (mesh == null) {
          if (material == null) {
            material = objMaterials.get("");
            if (material == null) {
              material = new Material();
              material.name = "no_material";
              material.defined = false;
              objMaterials.put("", material);
            }
          }
          mesh = new ObjMesh(group, material);
        }
        String [] corners = line.substring(2).trim().split("\\s+");
        int [] face = new int [corners.length];
        for (int i = 0; i < corners.length; i++) {
          face [i] = mesh.getVertex(corners [i], vertices, textureCoordinates, vertexNormals);
        }
        for (int i = 1; i + 1 < face.length; i++) {
          mesh.indices.add(face [0]);
          mesh.indices.add(face [i]);
          mesh.indices.add(face [i + 1]);
        }
      } else if (line.startsWith("usemtl ")) {
        String name = line.substring(7).trim();
        material = objMaterials.get(name);
        if (material == null) {
          material = new Material();
          material.name = name;
          objMaterials.put(name, material);
        }
        mesh = closeMesh(mesh);
      } else if (line.startsWith("g ") || line.startsWith("o ")) {
        group = line.substring(2).trim();
        mesh = closeMesh(mesh);
      } else if (line.startsWith("mtllib ")) {
        String name = line.substring(7).trim();
        File library = new File(folder, name.replace('\\', '/'));
        if (library.isFile() && library.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator)) {
          readMtl(library, folder, objMaterials);
        } else {
          note("its file of materials " + name + " isn't beside it");
        }
      }
    }
    closeMesh(mesh);
  }

  private ObjMesh closeMesh(ObjMesh objMesh) {
    if (objMesh != null && objMesh.indices.size() >= 3) {
      Mesh mesh = new Mesh();
      mesh.name = objMesh.name;
      mesh.material = objMesh.material;
      mesh.positions = objMesh.positions.toArray();
      mesh.uvs = objMesh.textured && objMesh.material.image != null ? objMesh.uvs.toArray() : null;
      mesh.normals = objMesh.lit ? objMesh.normals.toArray() : null;
      mesh.indices = objMesh.indices.toArray();
      this.meshes.add(mesh);
      if (!this.materials.contains(mesh.material)) {
        this.materials.add(mesh.material);
      }
    }
    return null;
  }

  private static void addNumbers(FloatList list, String line, int start, int count) throws IOException {
    String [] values = line.substring(start).trim().split("\\s+");
    for (int i = 0; i < count; i++) {
      try {
        list.add(i < values.length ? Float.parseFloat(values [i]) : 0);
      } catch (NumberFormatException ex) {
        throw new IOException("the line \"" + line + "\" of the model can't be read");
      }
    }
  }

  private void readMtl(File library, File folder, Map<String, Material> objMaterials) throws IOException {
    String text = new String(AssistantGeneratedModel.readFile(library, MAX_IMAGE_LENGTH), "ISO-8859-1");
    Material material = null;
    for (String line : text.split("\r?\n")) {
      line = line.trim();
      String [] values = line.split("\\s+");
      try {
        if (line.startsWith("newmtl ")) {
          String name = line.substring(7).trim();
          material = objMaterials.get(name);
          if (material == null) {
            material = new Material();
            material.name = name;
            objMaterials.put(name, material);
          }
        } else if (material == null) {
          continue;
        } else if (values [0].equals("Kd") && values.length >= 4) {
          for (int i = 0; i < 3; i++) {
            material.color [i] = Float.parseFloat(values [i + 1]);
          }
        } else if (values [0].equals("d") && values.length >= 2) {
          material.color [3] = Float.parseFloat(values [1]);
          material.blend = material.color [3] < 0.999f;
        } else if (values [0].equals("Tr") && values.length >= 2) {
          material.color [3] = 1 - Float.parseFloat(values [1]);
          material.blend = material.color [3] < 0.999f;
        } else if (values [0].equals("Ns") && values.length >= 2) {
          material.roughness = 1 - (float)Math.sqrt(Math.max(0, Math.min(1, (Float.parseFloat(values [1]) - 1) / 127)));
        } else if (values [0].equals("map_Kd") && values.length >= 2) {
          String name = line.substring(6).trim();
          // The options of an image come before its name
          while (name.startsWith("-")) {
            String [] parts = name.split("\\s+", 3);
            name = parts.length == 3 ? parts [2] : "";
          }
          File image = new File(folder, name.replace('\\', '/'));
          if (name.length() > 0 && image.isFile()
              && image.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator)
              && image.length() <= MAX_IMAGE_LENGTH) {
            byte [] data = AssistantGeneratedModel.readFile(image, MAX_IMAGE_LENGTH);
            if (isPNG(data) || isJPEG(data)) {
              material.image = data;
            } else {
              note("an image at an other format than PNG or JPEG isn't read");
            }
          } else {
            note("the image " + name + " isn't beside the model");
          }
        }
      } catch (NumberFormatException ex) {
        // A value that isn't a number is left at its default
      }
    }
  }

  /**
   * The triangles of a group of an OBJ file.
   */
  private static final class ObjMesh {
    final String    name;
    final Material  material;
    final FloatList positions = new FloatList();
    final FloatList uvs = new FloatList();
    final FloatList normals = new FloatList();
    final IntList   indices = new IntList();
    final Map<String, Integer> corners = new HashMap<String, Integer>();
    boolean         textured = true;
    boolean         lit = true;

    ObjMesh(String name, Material material) {
      this.name = name;
      this.material = material;
    }

    int getVertex(String corner, FloatList vertices, FloatList textureCoordinates, FloatList vertexNormals) throws IOException {
      Integer index = this.corners.get(corner);
      if (index != null) {
        return index.intValue();
      }
      String [] parts = corner.split("/", -1);
      int vertex = getIndex(parts [0], vertices.size() / 3);
      if (vertex < 0) {
        throw new IOException("a face of the model refers to a vertex it doesn't have");
      }
      int textureCoordinate = parts.length > 1 ? getIndex(parts [1], textureCoordinates.size() / 2) : -1;
      int normal = parts.length > 2 ? getIndex(parts [2], vertexNormals.size() / 3) : -1;
      for (int i = 0; i < 3; i++) {
        this.positions.add(vertices.get(vertex * 3 + i));
        this.normals.add(normal >= 0 ? vertexNormals.get(normal * 3 + i) : 0);
      }
      this.uvs.add(textureCoordinate >= 0 ? textureCoordinates.get(textureCoordinate * 2) : 0);
      this.uvs.add(textureCoordinate >= 0 ? textureCoordinates.get(textureCoordinate * 2 + 1) : 0);
      this.textured &= textureCoordinate >= 0;
      this.lit &= normal >= 0;
      index = Integer.valueOf(this.positions.size() / 3 - 1);
      this.corners.put(corner, index);
      return index.intValue();
    }

    private static int getIndex(String text, int count) {
      if (text.length() == 0) {
        return -1;
      }
      try {
        int index = Integer.parseInt(text);
        index = index < 0 ? count + index : index - 1;
        return index >= 0 && index < count ? index : -1;
      } catch (NumberFormatException ex) {
        return -1;
      }
    }
  }

  private static final class FloatList {
    private float [] values = new float [1024];
    private int      size;

    void add(float value) {
      if (this.size == this.values.length) {
        this.values = Arrays.copyOf(this.values, this.size * 2);
      }
      this.values [this.size++] = value;
    }

    float get(int index) {
      return this.values [index];
    }

    int size() {
      return this.size;
    }

    float [] toArray() {
      return Arrays.copyOf(this.values, this.size);
    }
  }

  private static final class IntList {
    private int [] values = new int [1024];
    private int    size;

    void add(int value) {
      if (this.size == this.values.length) {
        this.values = Arrays.copyOf(this.values, this.size * 2);
      }
      this.values [this.size++] = value;
    }

    int size() {
      return this.size;
    }

    int [] toArray() {
      return Arrays.copyOf(this.values, this.size);
    }
  }
}
