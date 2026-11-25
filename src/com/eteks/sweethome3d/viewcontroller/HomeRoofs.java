/*
 * HomeRoofs.java 21 nov. 2026
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
import java.util.List;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * The roofs built by the <code>add_roof</code> command in a home, for what needs the height
 * of their underside: the ceiling of the rooms under them, the headroom, the doors that would go
 * through them.
 * @author Sweet Home 3D
 */
final class HomeRoofs {
  /** The ceiling height from which a place counts as a place to stand: 7 feet. */
  static final float STANDING_HEIGHT = 213.36f;

  /**
   * A roof with the elevation of the floor its heights are measured from.
   */
  static final class Roof {
    final HomePieceOfFurniture  piece;
    final AssistantRoofModel    model;
    final float                 elevation;

    Roof(HomePieceOfFurniture piece, AssistantRoofModel model, float elevation) {
      this.piece = piece;
      this.model = model;
      this.elevation = elevation;
    }
  }

  private HomeRoofs() {
  }

  /**
   * Returns the visible roofs of <code>home</code> whose description can be read.
   */
  static List<Roof> get(Home home) {
    List<Roof> roofs = new ArrayList<Roof>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      String spec = piece.getProperty(AssistantShapeCommands.ROOF_SPEC_PROPERTY);
      if (spec != null && piece.isVisible()) {
        try {
          Object parsed = JSONParser.parse(spec);
          if (parsed instanceof Map) {
            roofs.add(new Roof(piece, AssistantRoofModel.create((Map<?, ?>)parsed),
                piece.getLevel() != null ? piece.getLevel().getElevation() : 0));
          }
        } catch (IOException ex) {
          // Not a roof to measure
        } catch (IllegalArgumentException ex) {
          // Not a roof to measure
        }
      }
    }
    return roofs;
  }

  /**
   * Returns the elevation of the lowest underside of a roof above the elevation <code>from</code>
   * at a point of the plan, or NaN if no roof is above it.
   */
  static double getCeilingElevation(List<Roof> roofs, double x, double y, double from) {
    double ceiling = Double.NaN;
    for (Roof roof : roofs) {
      double height = roof.model.getGeometry().getCeilingHeight(x, y) + roof.elevation;
      if (!Double.isNaN(height) && height > from && !(height >= ceiling)) {
        ceiling = height;
      }
    }
    return ceiling;
  }

  /**
   * Returns the walls of <code>home</code> that come out of the top of a roof: their bottom is under the
   * roof and their top above it, as walls fitted to a roof that changed since. The walls along an edge
   * where the roof stops at a taller wall (<code>clip</code>, <code>abut</code>) and the ones in what
   * such walls cut out of the roof aren't counted.
   */
  static List<Wall> getWallsAbove(Home home, Roof roof) {
    List<Wall> walls = new ArrayList<Wall>();
    AssistantRoofGeometry geometry = roof.model.getGeometry();
    double [][] points = roof.model.getPoints();
    for (Wall wall : home.getWalls()) {
      if (wall.getArcExtent() != null && wall.getArcExtent() != 0 || wall.getLength() < 1) {
        continue;
      }
      float elevation = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
      float height = wall.getHeight() != null ? wall.getHeight() : home.getWallHeight();
      float heightAtEnd = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : height;
      int above = 0;
      for (int i = 1; i < 12; i++) {
        double t = i / 12.;
        double x = wall.getXStart() + (wall.getXEnd() - wall.getXStart()) * t;
        double y = wall.getYStart() + (wall.getYEnd() - wall.getYStart()) * t;
        double top = geometry.getHeight(x, y, true) + roof.elevation;
        if (Double.isNaN(top) || !(elevation < top) || !(elevation + height + (heightAtEnd - height) * t > top + 2)) {
          continue;
        }
        // Not the taller wall the roof runs up to
        boolean alongTallerWall = false;
        for (int edge = 0; edge < points.length && !alongTallerWall; edge++) {
          double [] next = points [(edge + 1) % points.length];
          alongTallerWall = roof.model.isEdgeClipped(edge)
              && java.awt.geom.Line2D.ptSegDist(points [edge][0], points [edge][1], next [0], next [1], x, y)
                  <= wall.getThickness() / 2 + 2;
        }
        if (!alongTallerWall) {
          above++;
        }
      }
      if (above >= 2) {
        walls.add(wall);
      }
    }
    return walls;
  }

  /**
   * Returns the height of the flat part of the ceiling a room got with <code>flat_ceiling_at</code>, or NaN.
   */
  static double getFlatCeilingHeight(Room room) {
    String ceiling = room.getProperty(AssistantShapeCommands.ROOM_CEILING_PROPERTY);
    if (ceiling != null && ceiling.indexOf(':') > 0) {
      try {
        return Double.parseDouble(ceiling.substring(ceiling.indexOf(':') + 1));
      } catch (NumberFormatException ex) {
        // No flat part
      }
    }
    return Double.NaN;
  }

  /**
   * Returns the headroom of a room: {lowest height of its ceiling above its floor, highest one,
   * area in cm2 where it is at least {@link #STANDING_HEIGHT} high, area measured, area of the room
   * with no ceiling above it}. The ceiling is the underside of the roofs above the room, the flat part
   * the room got with <code>flat_ceiling_at</code>, and the ceiling of the level when the room shows it.
   * @return the headroom, or <code>null</code> if the room has no area
   */
  static double [] getHeadroom(Home home, List<Roof> roofs, Room room) {
    float [][] points = room.getPoints();
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (float [] point : points) {
      minX = Math.min(minX, point [0]);
      minY = Math.min(minY, point [1]);
      maxX = Math.max(maxX, point [0]);
      maxY = Math.max(maxY, point [1]);
    }
    if (!(maxX > minX) || !(maxY > minY)) {
      return null;
    }
    float floor = room.getLevel() != null ? room.getLevel().getElevation() : 0;
    double levelCeiling = room.isCeilingVisible()
        ? (room.getLevel() != null && room.getLevel().getHeight() > 0 ? room.getLevel().getHeight() : home.getWallHeight())
        : Double.NaN;
    double flat = getFlatCeilingHeight(room);
    // About 80 samples along the longest side
    double step = Math.max(5, Math.max(maxX - minX, maxY - minY) / 80);
    double lowest = Double.POSITIVE_INFINITY;
    double highest = Double.NEGATIVE_INFINITY;
    double standingArea = 0;
    double measuredArea = 0;
    double openArea = 0;
    for (double x = minX + step / 2; x < maxX; x += step) {
      for (double y = minY + step / 2; y < maxY; y += step) {
        if (!room.containsPoint((float)x, (float)y, 0)) {
          continue;
        }
        double ceiling = getCeilingElevation(roofs, x, y, floor + 1) - floor;
        if (!Double.isNaN(flat) && !(ceiling <= flat)) {
          ceiling = flat;
        }
        if (!Double.isNaN(levelCeiling) && !(ceiling <= levelCeiling)) {
          ceiling = levelCeiling;
        }
        if (Double.isNaN(ceiling)) {
          openArea += step * step;
        } else {
          lowest = Math.min(lowest, ceiling);
          highest = Math.max(highest, ceiling);
          measuredArea += step * step;
          if (ceiling >= STANDING_HEIGHT) {
            standingArea += step * step;
          }
        }
      }
    }
    if (measuredArea == 0 && openArea == 0) {
      return null;
    }
    return new double [] {measuredArea > 0 ? lowest : Double.NaN, measuredArea > 0 ? highest : Double.NaN,
                          standingArea, measuredArea, openArea};
  }
}
