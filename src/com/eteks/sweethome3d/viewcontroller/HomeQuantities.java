/*
 * HomeQuantities.java 21 nov. 2026
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * The surfaces of the walls of a home, with the openings of the doors and windows that
 * stand in them, and the wall sides that face each room. Lengths are in cm and surfaces in cm2.
 * @author Sweet Home 3D
 */
final class HomeQuantities {
  /**
   * The opening a door or a window makes in a wall.
   */
  static final class Opening {
    final HomePieceOfFurniture piece;
    /** Where the opening starts and ends along the wall, from its start point, or NaN in a round wall. */
    final double start;
    final double end;
    final double width;
    final double bottom;
    final double top;

    Opening(HomePieceOfFurniture piece, double start, double end, double width, double bottom, double top) {
      this.piece = piece;
      this.start = start;
      this.end = end;
      this.width = width;
      this.bottom = bottom;
      this.top = top;
    }

    double getArea() {
      return Math.max(0, this.width) * Math.max(0, this.top - this.bottom);
    }
  }

  /**
   * The quantities of a wall.
   */
  static final class WallQuantity {
    final Wall          wall;
    final double        length;
    /** The mean height of the wall. */
    final double        height;
    final List<Opening> openings = new ArrayList<Opening>();

    WallQuantity(Wall wall, double length, double height) {
      this.wall = wall;
      this.length = length;
      this.height = height;
    }

    /** Returns the surface of one side of the wall, openings included. */
    double getGrossSide() {
      return this.length * this.height;
    }

    /** Returns the surface of the openings in the wall, on one of its sides. */
    double getOpeningsSide() {
      double area = 0;
      for (Opening opening : this.openings) {
        area += opening.getArea();
      }
      return Math.min(area, getGrossSide());
    }

    double getNetSide() {
      return getGrossSide() - getOpeningsSide();
    }
  }

  /**
   * The part of a side of a wall that faces a room.
   */
  static final class Facing {
    final WallQuantity wallQuantity;
    final boolean      left;
    /** The part of the wall that faces the room, measured along the wall from its start point. */
    final double       start;
    final double       end;
    final double       openings;
    /** A unit vector that goes from the wall to the inside of the room. */
    final double       normalX;
    final double       normalY;

    Facing(WallQuantity wallQuantity, boolean left, double start, double end, double openings,
           double normalX, double normalY) {
      this.wallQuantity = wallQuantity;
      this.left = left;
      this.start = start;
      this.end = end;
      this.openings = openings;
      this.normalX = normalX;
      this.normalY = normalY;
    }

    double getLength() {
      return this.end - this.start;
    }

    double getGross() {
      return getLength() * this.wallQuantity.height;
    }

    double getNet() {
      return Math.max(0, getGross() - this.openings);
    }
  }

  private final Home                     home;
  private final List<WallQuantity>       wallQuantities = new ArrayList<WallQuantity>();
  private final Map<Wall, WallQuantity>  quantitiesByWall = new IdentityHashMap<Wall, WallQuantity>();
  private final Map<Room, List<Facing>>  facingsByRoom = new IdentityHashMap<Room, List<Facing>>();

  HomeQuantities(Home home) {
    this.home = home;
    for (Wall wall : home.getWalls()) {
      WallQuantity quantity = new WallQuantity(wall, wall.getLength(), getHeight(home, wall));
      this.wallQuantities.add(quantity);
      this.quantitiesByWall.put(wall, quantity);
    }
    List<HomePieceOfFurniture> openings = new ArrayList<HomePieceOfFurniture>();
    collectDoorsAndWindows(home.getFurniture(), openings);
    for (HomePieceOfFurniture piece : openings) {
      addOpening(piece);
    }
  }

  /**
   * Returns the mean height of a wall.
   */
  static double getHeight(Home home, Wall wall) {
    double startHeight = wall.getHeight() != null ? wall.getHeight() : home.getWallHeight();
    double endHeight = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : startHeight;
    return (startHeight + endHeight) / 2;
  }

  static boolean isStraight(Wall wall) {
    return wall.getArcExtent() == null || wall.getArcExtent().floatValue() == 0;
  }

  private static void collectDoorsAndWindows(List<HomePieceOfFurniture> furniture, List<HomePieceOfFurniture> openings) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collectDoorsAndWindows(((HomeFurnitureGroup)piece).getFurniture(), openings);
      } else if (piece.isDoorOrWindow()) {
        openings.add(piece);
      }
    }
  }

  /**
   * Adds the opening of a door or a window to the wall it stands in: the wall of its level
   * that contains its center, and whose middle line is the closest to it.
   */
  private void addOpening(HomePieceOfFurniture piece) {
    WallQuantity best = null;
    double bestDistance = Double.MAX_VALUE;
    double bestPosition = Double.NaN;
    for (WallQuantity quantity : this.wallQuantities) {
      Wall wall = quantity.wall;
      if (wall.getLevel() != piece.getLevel() || quantity.length < 1E-3) {
        continue;
      }
      double distance;
      double position;
      if (isStraight(wall)) {
        double dx = (wall.getXEnd() - wall.getXStart()) / quantity.length;
        double dy = (wall.getYEnd() - wall.getYStart()) / quantity.length;
        position = (piece.getX() - wall.getXStart()) * dx + (piece.getY() - wall.getYStart()) * dy;
        distance = Math.abs((piece.getX() - wall.getXStart()) * dy - (piece.getY() - wall.getYStart()) * dx);
        if (position < 0 || position > quantity.length || distance > wall.getThickness() / 2 + 1) {
          continue;
        }
      } else if (wall.containsPoint(piece.getX(), piece.getY(), 1)) {
        position = Double.NaN;
        distance = wall.getThickness() / 4;
      } else {
        continue;
      }
      if (distance < bestDistance) {
        best = quantity;
        bestDistance = distance;
        bestPosition = position;
      }
    }
    if (best != null) {
      double start = Double.NaN;
      double end = Double.NaN;
      double width = Math.min(piece.getWidth(), best.length);
      if (!Double.isNaN(bestPosition)) {
        // Clipped to the wall
        start = Math.max(0, bestPosition - piece.getWidth() / 2);
        end = Math.min(best.length, bestPosition + piece.getWidth() / 2);
        width = end - start;
      }
      double bottom = Math.max(0, piece.getElevation());
      double top = Math.min(best.height, piece.getElevation() + piece.getHeight());
      if (width > 0 && top > bottom) {
        best.openings.add(new Opening(piece, start, end, width, bottom, top));
      }
    }
  }

  List<WallQuantity> getWallQuantities() {
    return this.wallQuantities;
  }

  WallQuantity get(Wall wall) {
    return this.quantitiesByWall.get(wall);
  }

  /**
   * Returns the parts of the sides of walls that face a room: the straight walls of its level that
   * run along its sides. The list is empty if no wall follows the room.
   */
  List<Facing> getFacings(Room room) {
    List<Facing> facings = this.facingsByRoom.get(room);
    if (facings == null) {
      facings = new ArrayList<Facing>();
      try {
        computeFacings(room, facings);
      } catch (RuntimeException ex) {
        // Odd geometry: no wall surface for this room
        facings.clear();
      }
      this.facingsByRoom.put(room, facings);
    }
    return facings;
  }

  private void computeFacings(Room room, List<Facing> facings) {
    float [][] points = room.getPoints();
    if (points.length < 3) {
      return;
    }
    for (int i = 0; i < points.length; i++) {
      float [] point = points [i];
      float [] next = points [(i + 1) % points.length];
      double edgeLength = Math.hypot(next [0] - point [0], next [1] - point [1]);
      if (edgeLength < 1) {
        continue;
      }
      double ex = (next [0] - point [0]) / edgeLength;
      double ey = (next [1] - point [1]) / edgeLength;
      for (WallQuantity quantity : this.wallQuantities) {
        Wall wall = quantity.wall;
        if (wall.getLevel() != room.getLevel() || !isStraight(wall) || quantity.length < 1) {
          continue;
        }
        double dx = (wall.getXEnd() - wall.getXStart()) / quantity.length;
        double dy = (wall.getYEnd() - wall.getYStart()) / quantity.length;
        if (Math.abs(ex * dy - ey * dx) > 0.02) {
          // Not parallel
          continue;
        }
        double distance = Math.abs((wall.getXStart() - point [0]) * ey - (wall.getYStart() - point [1]) * ex);
        if (distance > wall.getThickness() / 2 + 2) {
          continue;
        }
        // The part of the side the wall runs along
        double direction = ex * dx + ey * dy > 0 ? 1 : -1;
        double wallStart = (wall.getXStart() - point [0]) * ex + (wall.getYStart() - point [1]) * ey;
        double low = Math.max(0, Math.min(wallStart, wallStart + direction * quantity.length));
        double high = Math.min(edgeLength, Math.max(wallStart, wallStart + direction * quantity.length));
        if (high - low < 1) {
          continue;
        }
        // The side of the room
        double middle = (low + high) / 2;
        double middleX = point [0] + ex * middle;
        double middleY = point [1] + ey * middle;
        boolean inside1 = room.containsPoint((float)(middleX - ey * 3), (float)(middleY + ex * 3), 0);
        boolean inside2 = room.containsPoint((float)(middleX + ey * 3), (float)(middleY - ex * 3), 0);
        if (inside1 == inside2) {
          continue;
        }
        double normalX = inside1 ? -ey : ey;
        double normalY = inside1 ? ex : -ex;
        // The left side of a wall is at the left of someone who walks from its start to its end
        boolean left = normalX * dy - normalY * dx > 0;
        // Along the wall
        double start = direction > 0 ? low - wallStart : wallStart - high;
        double end = direction > 0 ? high - wallStart : wallStart - low;
        double openingsArea = 0;
        for (Opening opening : quantity.openings) {
          if (!Double.isNaN(opening.start)) {
            double overlap = Math.min(end, opening.end) - Math.max(start, opening.start);
            if (overlap > 0) {
              openingsArea += overlap * (opening.top - opening.bottom);
            }
          }
        }
        facings.add(new Facing(quantity, left, start, end,
            Math.min(openingsArea, (end - start) * quantity.height), normalX, normalY));
      }
    }
  }

  /**
   * Returns {gross surface, surface of the openings, net surface} of the wall sides that face a room,
   * or <code>null</code> if no wall follows the room.
   */
  double [] getWallSurface(Room room) {
    List<Facing> facings = getFacings(room);
    if (facings.isEmpty()) {
      return null;
    }
    double gross = 0;
    double openings = 0;
    for (Facing facing : facings) {
      gross += facing.getGross();
      openings += facing.openings;
    }
    return new double [] {gross, openings, Math.max(0, gross - openings)};
  }

  Home getHome() {
    return this.home;
  }
}
