/*
 * GeneratedModelFaces.java 20 nov. 2026
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * The faces of the model of a piece built by the assistant commands (a roof, a slab,
 * a piece made of parts), at their place in the home. The box of its size describes
 * such a piece badly: an elevation drawing and a visitor who walks on a sloped slab
 * need its faces.
 */
public class GeneratedModelFaces {
  /**
   * A face: its points (x and y in the centimeters of the plan, height above the elevation 0),
   * counter clockwise seen from outside, and its color.
   */
  public static final class Face {
    public final float [][] points;
    public final int        color;

    Face(float [][] points, int color) {
      this.points = points;
      this.color = color;
    }
  }

  private GeneratedModelFaces() {
  }

  /**
   * Returns <code>true</code> if <code>piece</code> is a slab built by <code>add_slab</code>
   * or for the floor of a room.
   */
  public static boolean isSlab(HomePieceOfFurniture piece) {
    return "slab".equals(piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY));
  }

  /**
   * Returns the faces of the model of <code>piece</code>, or <code>null</code> if it isn't a piece
   * built by the assistant commands, if its model can't be read or has more than
   * <code>maxFaceCount</code> faces.
   */
  public static List<Face> getFaces(HomePieceOfFurniture piece, int maxFaceCount) {
    if (piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) == null
        && piece.getProperty(AssistantShapeCommands.PARTS_PROPERTY) == null
        || piece.isDoorOrWindow()
        || !(piece.getModel() instanceof URLContent)
        || piece.getPitch() != 0
        || piece.getRoll() != 0) {
      return null;
    }
    List<float []> vertices = new ArrayList<float []>();
    List<int []> faces = new ArrayList<int []>();
    List<Integer> faceColors = new ArrayList<Integer>();
    try {
      java.net.URL url = ((URLContent)piece.getModel()).getURL();
      if (!url.toString().toLowerCase(Locale.ENGLISH).endsWith(".obj")) {
        return null;
      }
      Map<String, Integer> materials = new HashMap<String, Integer>();
      try {
        String file = url.toString();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new java.net.URL(file.substring(0, file.length() - 4) + ".mtl").openStream(), "ISO-8859-1"));
        try {
          String material = null;
          for (String line; (line = reader.readLine()) != null; ) {
            String [] words = line.trim().split("\\s+");
            if (words [0].equals("newmtl") && words.length > 1) {
              material = words [1];
            } else if (words [0].equals("Kd") && words.length > 3 && material != null) {
              materials.put(material, (Math.round(Float.parseFloat(words [1]) * 255) << 16)
                  | (Math.round(Float.parseFloat(words [2]) * 255) << 8)
                  | Math.round(Float.parseFloat(words [3]) * 255));
            } else if (words [0].equals("map_Kd") && material != null) {
              // A textured material is white: an average color of roofs and bricks reads better
              materials.put(material, 0xB5654A);
            }
          }
        } finally {
          reader.close();
        }
      } catch (IOException ex) {
        // Faces keep the default color
      } catch (RuntimeException ex) {
        // Faces keep the default color
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(piece.getModel().openStream(), "ISO-8859-1"));
      try {
        int color = piece.getColor() != null ? piece.getColor() : 0xD8CDB8;
        for (String line; (line = reader.readLine()) != null; ) {
          String [] words = line.trim().split("\\s+");
          if (words [0].equals("v") && words.length > 3) {
            vertices.add(new float [] {Float.parseFloat(words [1]), Float.parseFloat(words [2]), Float.parseFloat(words [3])});
          } else if (words [0].equals("usemtl") && words.length > 1) {
            color = piece.getColor() != null ? piece.getColor()
                : materials.containsKey(words [1]) ? materials.get(words [1]) : 0xD8CDB8;
          } else if (words [0].equals("f") && words.length > 3) {
            int [] face = new int [words.length - 1];
            for (int i = 0; i < face.length; i++) {
              String index = words [i + 1];
              face [i] = Integer.parseInt(index.indexOf('/') >= 0 ? index.substring(0, index.indexOf('/')) : index) - 1;
            }
            faces.add(face);
            faceColors.add(color);
          }
        }
      } finally {
        reader.close();
      }
    } catch (IOException ex) {
      return null;
    } catch (RuntimeException ex) {
      return null;
    }
    if (vertices.isEmpty() || faces.isEmpty() || faces.size() > maxFaceCount) {
      return null;
    }
    // The model fills the box of the piece
    float [] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
    float [] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    for (float [] vertex : vertices) {
      for (int i = 0; i < 3; i++) {
        min [i] = Math.min(min [i], vertex [i]);
        max [i] = Math.max(max [i], vertex [i]);
      }
    }
    double cos = Math.cos(piece.getAngle());
    double sin = Math.sin(piece.getAngle());
    float bottom = piece.getGroundElevation();
    float [][] points = new float [vertices.size()][];
    for (int i = 0; i < points.length; i++) {
      float [] vertex = vertices.get(i);
      double x = max [0] > min [0] ? ((vertex [0] - min [0]) / (max [0] - min [0]) - 0.5) * piece.getWidth() : 0;
      double y = max [2] > min [2] ? ((vertex [2] - min [2]) / (max [2] - min [2]) - 0.5) * piece.getDepth() : 0;
      if (piece.isModelMirrored()) {
        x = -x;
      }
      float height = bottom + (max [1] > min [1] ? (vertex [1] - min [1]) / (max [1] - min [1]) * piece.getHeight() : 0);
      points [i] = new float [] {(float)(piece.getX() + x * cos - y * sin), (float)(piece.getY() + x * sin + y * cos), height};
    }
    List<Face> result = new ArrayList<Face>();
    for (int i = 0; i < faces.size(); i++) {
      int [] face = faces.get(i);
      float [][] facePoints = new float [face.length][];
      boolean valid = true;
      for (int j = 0; j < face.length && valid; j++) {
        valid = face [j] >= 0 && face [j] < points.length;
        if (valid) {
          // A mirrored model turns its faces over
          facePoints [piece.isModelMirrored() ? face.length - 1 - j : j] = points [face [j]];
        }
      }
      if (valid) {
        result.add(new Face(facePoints, faceColors.get(i)));
      }
    }
    return result;
  }
}
