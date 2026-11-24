/*
 * AssistantGltfWriter.java 23 nov. 2026
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a binary glTF file (.glb) from an OBJ file and its materials.
 */
public final class AssistantGltfWriter {
  /**
   * The triangles of a material.
   */
  private static final class Group {
    final Map<String, Integer> indices = new HashMap<String, Integer>();
    final List<float []>       positions = new ArrayList<float []>();
    final List<float []>       normals = new ArrayList<float []>();
    final List<float []>       coordinates = new ArrayList<float []>();
    final List<Integer>        triangles = new ArrayList<Integer>();
    boolean                    withNormals = true;
    boolean                    withCoordinates = true;
  }

  private AssistantGltfWriter() {
  }

  private static void pad(ByteArrayOutputStream out) {
    while (out.size() % 4 != 0) {
      out.write(0);
    }
  }

  /**
   * Writes <code>glb</code> from the OBJ file <code>obj</code>, whose lengths are in centimeters.
   * @param extras a JSON object kept with the scene, or <code>null</code>
   * @return the count of triangles written
   */
  public static int convert(File obj, File glb, String extras) throws IOException {
    // Materials: color, opacity, picture
    Map<String, Object []> materials = new LinkedHashMap<String, Object []>();
    List<float []> vertices = new ArrayList<float []>();
    List<float []> normals = new ArrayList<float []>();
    List<float []> coordinates = new ArrayList<float []>();
    Map<String, Group> groups = new LinkedHashMap<String, Group>();
    Group group = null;
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(obj), "ISO-8859-1"));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("v ")) {
          String [] values = line.trim().split("\\s+");
          vertices.add(new float [] {Float.parseFloat(values [1]), Float.parseFloat(values [2]), Float.parseFloat(values [3])});
        } else if (line.startsWith("vn ")) {
          String [] values = line.trim().split("\\s+");
          normals.add(new float [] {Float.parseFloat(values [1]), Float.parseFloat(values [2]), Float.parseFloat(values [3])});
        } else if (line.startsWith("vt ")) {
          String [] values = line.trim().split("\\s+");
          coordinates.add(new float [] {Float.parseFloat(values [1]), values.length > 2 ? Float.parseFloat(values [2]) : 0});
        } else if (line.startsWith("usemtl ")) {
          String name = line.substring(7).trim();
          group = groups.get(name);
          if (group == null) {
            groups.put(name, group = new Group());
          }
        } else if (line.startsWith("mtllib ")) {
          File library = new File(obj.getParentFile(), line.substring(7).trim());
          if (library.isFile()) {
            readMaterials(library, materials);
          }
        } else if (line.startsWith("f ")) {
          if (group == null) {
            groups.put("default", group = new Group());
          }
          String [] corners = line.trim().split("\\s+");
          int [] cornerIndices = new int [corners.length - 1];
          for (int i = 1; i < corners.length; i++) {
            Integer index = group.indices.get(corners [i]);
            if (index == null) {
              String [] parts = corners [i].split("/");
              int vertex = Integer.parseInt(parts [0]);
              group.positions.add(vertices.get(vertex > 0 ? vertex - 1 : vertices.size() + vertex));
              if (parts.length > 1 && parts [1].length() > 0) {
                int coordinate = Integer.parseInt(parts [1]);
                group.coordinates.add(coordinates.get(coordinate > 0 ? coordinate - 1 : coordinates.size() + coordinate));
              } else {
                group.coordinates.add(new float [2]);
                group.withCoordinates = false;
              }
              if (parts.length > 2 && parts [2].length() > 0) {
                int normal = Integer.parseInt(parts [2]);
                group.normals.add(normals.get(normal > 0 ? normal - 1 : normals.size() + normal));
              } else {
                group.normals.add(new float [] {0, 1, 0});
                group.withNormals = false;
              }
              index = Integer.valueOf(group.positions.size() - 1);
              group.indices.put(corners [i], index);
            }
            cornerIndices [i - 1] = index.intValue();
          }
          for (int i = 1; i + 1 < cornerIndices.length; i++) {
            group.triangles.add(Integer.valueOf(cornerIndices [0]));
            group.triangles.add(Integer.valueOf(cornerIndices [i]));
            group.triangles.add(Integer.valueOf(cornerIndices [i + 1]));
          }
        }
      }
    } catch (RuntimeException ex) {
      throw new IOException("the OBJ file can't be read (" + ex + ")");
    } finally {
      reader.close();
    }
    ByteArrayOutputStream binary = new ByteArrayOutputStream();
    StringBuilder bufferViews = new StringBuilder();
    StringBuilder accessors = new StringBuilder();
    StringBuilder primitives = new StringBuilder();
    StringBuilder materialsJson = new StringBuilder();
    StringBuilder texturesJson = new StringBuilder();
    StringBuilder imagesJson = new StringBuilder();
    Map<String, Integer> imageIndices = new HashMap<String, Integer>();
    int viewCount = 0;
    int accessorCount = 0;
    int materialCount = 0;
    int triangleCount = 0;
    for (Map.Entry<String, Group> entry : groups.entrySet()) {
      Group triangles = entry.getValue();
      if (triangles.triangles.isEmpty()) {
        continue;
      }
      triangleCount += triangles.triangles.size() / 3;
      int count = triangles.positions.size();
      // Its material
      Object [] material = materials.get(entry.getKey());
      float [] color = material != null ? (float [])material [0] : new float [] {0.8f, 0.8f, 0.8f};
      float opacity = material != null ? ((Float)material [1]).floatValue() : 1;
      String picture = material != null ? (String)material [2] : null;
      Integer texture = null;
      if (picture != null && triangles.withCoordinates) {
        texture = imageIndices.get(picture);
        File image = new File(obj.getParentFile(), picture);
        if (texture == null && image.isFile() && image.length() < 30L * 1024 * 1024) {
          byte [] data = AssistantGeneratedModel.readFile(image, 30L * 1024 * 1024);
          String extension = AssistantGeneratedModel.getImageExtension(data);
          if ("png".equals(extension) || "jpg".equals(extension)) {
            pad(binary);
            int offset = binary.size();
            binary.write(data, 0, data.length);
            bufferViews.append(viewCount > 0 ? "," : "").append("{\"buffer\":0,\"byteOffset\":").append(offset).append(",\"byteLength\":")
                .append(data.length).append('}');
            texture = Integer.valueOf(imageIndices.size());
            imageIndices.put(picture, texture);
            imagesJson.append(texture > 0 ? "," : "").append("{\"bufferView\":").append(viewCount++).append(",\"mimeType\":\"image/")
                .append("png".equals(extension) ? "png" : "jpeg").append("\"}");
            texturesJson.append(texture > 0 ? "," : "").append("{\"source\":").append(texture).append('}');
          }
        }
      }
      materialsJson.append(materialCount > 0 ? "," : "").append("{\"name\":").append(JSONParser.toJSONString(entry.getKey()))
          .append(",\"pbrMetallicRoughness\":{\"baseColorFactor\":[").append(texture != null ? "1,1,1" : format(color [0]) + ","
              + format(color [1]) + "," + format(color [2])).append(',').append(format(opacity)).append("],\"metallicFactor\":0,"
              + "\"roughnessFactor\":0.9").append(texture != null ? ",\"baseColorTexture\":{\"index\":" + texture + "}" : "")
          .append("},\"doubleSided\":true").append(opacity < 1 ? ",\"alphaMode\":\"BLEND\"" : texture != null ? ",\"alphaMode\":\"MASK\"" : "")
          .append('}');
      // Positions
      pad(binary);
      int offset = binary.size();
      ByteBuffer buffer = ByteBuffer.allocate(count * 12).order(ByteOrder.LITTLE_ENDIAN);
      float [] minimum = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
      float [] maximum = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      for (float [] position : triangles.positions) {
        for (int i = 0; i < 3; i++) {
          buffer.putFloat(position [i]);
          minimum [i] = Math.min(minimum [i], position [i]);
          maximum [i] = Math.max(maximum [i], position [i]);
        }
      }
      binary.write(buffer.array(), 0, buffer.capacity());
      bufferViews.append(viewCount > 0 ? "," : "").append("{\"buffer\":0,\"byteOffset\":").append(offset).append(",\"byteLength\":")
          .append(count * 12).append(",\"target\":34962}");
      accessors.append(accessorCount > 0 ? "," : "").append("{\"bufferView\":").append(viewCount++).append(",\"componentType\":5126,\"count\":")
          .append(count).append(",\"type\":\"VEC3\",\"min\":[").append(format(minimum [0])).append(',').append(format(minimum [1]))
          .append(',').append(format(minimum [2])).append("],\"max\":[").append(format(maximum [0])).append(',').append(format(maximum [1]))
          .append(',').append(format(maximum [2])).append("]}");
      StringBuilder attributes = new StringBuilder("\"POSITION\":" + accessorCount++);
      if (triangles.withNormals) {
        offset = binary.size();
        buffer = ByteBuffer.allocate(count * 12).order(ByteOrder.LITTLE_ENDIAN);
        for (float [] normal : triangles.normals) {
          double length = Math.sqrt(normal [0] * normal [0] + normal [1] * normal [1] + normal [2] * normal [2]);
          for (int i = 0; i < 3; i++) {
            buffer.putFloat(length > 1E-6 ? (float)(normal [i] / length) : i == 1 ? 1 : 0);
          }
        }
        binary.write(buffer.array(), 0, buffer.capacity());
        bufferViews.append(",{\"buffer\":0,\"byteOffset\":").append(offset).append(",\"byteLength\":").append(count * 12)
            .append(",\"target\":34962}");
        accessors.append(",{\"bufferView\":").append(viewCount++).append(",\"componentType\":5126,\"count\":").append(count)
            .append(",\"type\":\"VEC3\"}");
        attributes.append(",\"NORMAL\":").append(accessorCount++);
      }
      if (triangles.withCoordinates && texture != null) {
        offset = binary.size();
        buffer = ByteBuffer.allocate(count * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (float [] coordinate : triangles.coordinates) {
          // The origin of a picture is its top in glTF
          buffer.putFloat(coordinate [0]);
          buffer.putFloat(1 - coordinate [1]);
        }
        binary.write(buffer.array(), 0, buffer.capacity());
        bufferViews.append(",{\"buffer\":0,\"byteOffset\":").append(offset).append(",\"byteLength\":").append(count * 8)
            .append(",\"target\":34962}");
        accessors.append(",{\"bufferView\":").append(viewCount++).append(",\"componentType\":5126,\"count\":").append(count)
            .append(",\"type\":\"VEC2\"}");
        attributes.append(",\"TEXCOORD_0\":").append(accessorCount++);
      }
      offset = binary.size();
      buffer = ByteBuffer.allocate(triangles.triangles.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
      for (Integer index : triangles.triangles) {
        buffer.putInt(index.intValue());
      }
      binary.write(buffer.array(), 0, buffer.capacity());
      bufferViews.append(",{\"buffer\":0,\"byteOffset\":").append(offset).append(",\"byteLength\":").append(triangles.triangles.size() * 4)
          .append(",\"target\":34963}");
      accessors.append(",{\"bufferView\":").append(viewCount++).append(",\"componentType\":5125,\"count\":")
          .append(triangles.triangles.size()).append(",\"type\":\"SCALAR\"}");
      primitives.append(primitives.length() > 0 ? "," : "").append("{\"attributes\":{").append(attributes).append("},\"indices\":")
          .append(accessorCount++).append(",\"material\":").append(materialCount++).append('}');
    }
    if (triangleCount == 0) {
      throw new IOException("the home has nothing to export in 3D");
    }
    pad(binary);
    // Centimeters to meters
    String json = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"Sweet Home 3D\"},\"scene\":0,\"scenes\":[{\"nodes\":[0]"
        + (extras != null ? ",\"extras\":" + extras : "") + "}],\"nodes\":[{\"name\":\"Home\",\"mesh\":0,\"scale\":[0.01,0.01,0.01]}],"
        + "\"meshes\":[{\"primitives\":[" + primitives + "]}],\"materials\":[" + materialsJson + "],"
        + (imagesJson.length() > 0 ? "\"textures\":[" + texturesJson + "],\"images\":[" + imagesJson + "]," : "")
        + "\"accessors\":[" + accessors + "],\"bufferViews\":[" + bufferViews + "],\"buffers\":[{\"byteLength\":" + binary.size() + "}]}";
    byte [] jsonBytes = json.getBytes("UTF-8");
    int jsonLength = (jsonBytes.length + 3) / 4 * 4;
    OutputStream out = new FileOutputStream(glb);
    try {
      ByteBuffer header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
      header.putInt(0x46546C67).putInt(2).putInt(12 + 8 + jsonLength + 8 + binary.size()).putInt(jsonLength).putInt(0x4E4F534A);
      out.write(header.array());
      out.write(jsonBytes);
      for (int i = jsonBytes.length; i < jsonLength; i++) {
        out.write(' ');
      }
      ByteBuffer chunk = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      chunk.putInt(binary.size()).putInt(0x004E4942);
      out.write(chunk.array());
      binary.writeTo(out);
    } finally {
      out.close();
    }
    return triangleCount;
  }

  private static String format(float value) {
    return String.format(Locale.US, "%.5f", value);
  }

  private static void readMaterials(File library, Map<String, Object []> materials) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(library), "ISO-8859-1"));
    try {
      String line;
      Object [] material = null;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        try {
          if (line.startsWith("newmtl ")) {
            material = new Object [] {new float [] {0.8f, 0.8f, 0.8f}, Float.valueOf(1), null};
            materials.put(line.substring(7).trim(), material);
          } else if (material != null && line.startsWith("Kd ")) {
            String [] values = line.split("\\s+");
            material [0] = new float [] {Float.parseFloat(values [1]), Float.parseFloat(values [2]), Float.parseFloat(values [3])};
          } else if (material != null && line.startsWith("d ")) {
            material [1] = Float.valueOf(Math.max(0, Math.min(1, Float.parseFloat(line.split("\\s+") [1]))));
          } else if (material != null && line.startsWith("map_Kd ")) {
            material [2] = line.substring(7).trim();
          }
        } catch (RuntimeException ex) {
          // A line that can't be read is left out
        }
      }
    } finally {
      reader.close();
    }
  }
}
