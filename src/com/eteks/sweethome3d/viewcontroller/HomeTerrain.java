/*
 * HomeTerrain.java 20 nov. 2026
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;

/**
 * The ground built by the <code>set_terrain</code> command, for what needs
 * its heights: a visitor who walks on it, an elevation drawing.
 */
public class HomeTerrain {
  private HomeTerrain() {
  }

  /**
   * Returns <code>true</code> if <code>piece</code> is the ground built by <code>set_terrain</code>.
   */
  public static boolean isTerrain(HomePieceOfFurniture piece) {
    return piece.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY) != null;
  }

  /**
   * Returns the triangles of the grounds of <code>home</code> that are visible: three points
   * (x, y, z) each, x and y in the centimeters of the plan and z the height above the elevation 0.
   */
  public static List<float [][]> getTriangles(Home home) {
    List<float [][]> triangles = new ArrayList<float [][]>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (piece.isVisible() && isTerrain(piece)) {
        triangles.addAll(getTriangles(piece));
      }
    }
    return triangles;
  }

  /**
   * Returns the triangles of a ground, without the ones its long edges or its outline trim.
   */
  public static List<float [][]> getTriangles(HomePieceOfFurniture piece) {
    return getTriangles(piece.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY),
        piece.getProperty(AssistantShapeCommands.TERRAIN_TRIM_PROPERTY));
  }

  /**
   * Returns the triangles described by the properties of a ground: its points, and what trims its edge.
   */
  static List<float [][]> getTriangles(String spec, String trim) {
    return getTriangles(spec, trim, null);
  }

  /**
   * Returns the triangles of a ground once trimmed: the given ones, or the ones of its points.
   */
  static List<float [][]> getTriangles(String spec, String trim, List<float [][]> triangles) {
    if (triangles == null) {
      triangles = getTriangles(spec);
    }
    if (trim != null) {
      try {
        Object parsed = JSONParser.parse(trim);
        if (parsed instanceof java.util.Map) {
          java.util.Map<?, ?> map = (java.util.Map<?, ?>)parsed;
          List<float []> clip = AssistantFurnitureBuilder.getPoints(map, "clip", 2);
          triangles = AssistantShapeCommands.trimTriangles(triangles, AssistantFurnitureBuilder.getNumber(map, "max_edge", 0),
              clip.size() >= 3 ? clip.toArray(new float [clip.size()][]) : null);
        }
      } catch (RuntimeException ex) {
        // Not trimmed
      }
    }
    return triangles;
  }

  /**
   * Returns the triangles described by the points of a ground.
   */
  static List<float [][]> getTriangles(String spec) {
    List<float []> points = new ArrayList<float []>();
    try {
      Object parsed = JSONParser.parse(spec);
      if (parsed instanceof List) {
        points = AssistantFurnitureBuilder.getPoints(Collections.singletonMap("points", parsed), "points", 3);
      }
    } catch (IllegalArgumentException ex) {
      // No points
    }
    return AssistantShapeCommands.triangulate(points);
  }
}
