/*
 * DollHouseView.java 16 Nov 2026
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Wall;

/**
 * The "doll house" display of the aerial view: the walls that stand between
 * the camera and the rooms are left out, with their doors and windows, so the
 * inside of the home is seen whatever the side it's looked at from.
 * @author Sweet Home 3D
 */
public final class DollHouseView {
  /** Above this pitch the home is seen from the top, and no wall hides a room. */
  private static final double TOP_VIEW_PITCH = Math.toRadians(75);

  private DollHouseView() {
  }

  /**
   * Returns the walls of <code>home</code> to leave out for the given camera:
   * those on the side of the home the camera is on, that face it. Empty for
   * the virtual visitor, and for a view from the top.
   */
  public static Set<Wall> getHiddenWalls(Home home, Camera camera) {
    Set<Wall> hiddenWalls = new HashSet<Wall>();
    if (camera != home.getTopCamera() || camera.getPitch() > TOP_VIEW_PITCH) {
      return hiddenWalls;
    }
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (Wall wall : home.getWalls()) {
      if (wall.getLevel() == null || wall.getLevel().isViewableAndVisible()) {
        minX = Math.min(minX, Math.min(wall.getXStart(), wall.getXEnd()));
        maxX = Math.max(maxX, Math.max(wall.getXStart(), wall.getXEnd()));
        minY = Math.min(minY, Math.min(wall.getYStart(), wall.getYEnd()));
        maxY = Math.max(maxY, Math.max(wall.getYStart(), wall.getYEnd()));
      }
    }
    if (minX > maxX) {
      return hiddenWalls;
    }
    float centerX = (minX + maxX) / 2;
    float centerY = (minY + maxY) / 2;
    // Horizontal direction from the center of the home to the camera
    double toCameraX = camera.getX() - centerX;
    double toCameraY = camera.getY() - centerY;
    double distance = Math.hypot(toCameraX, toCameraY);
    if (distance < 1) {
      return hiddenWalls;
    }
    toCameraX /= distance;
    toCameraY /= distance;
    for (Wall wall : home.getWalls()) {
      if (wall.getLevel() != null && !wall.getLevel().isViewableAndVisible()) {
        continue;
      }
      double length = Math.hypot(wall.getXEnd() - wall.getXStart(), wall.getYEnd() - wall.getYStart());
      if (length < 1) {
        continue;
      }
      double middleX = (wall.getXStart() + wall.getXEnd()) / 2. - centerX;
      double middleY = (wall.getYStart() + wall.getYEnd()) / 2. - centerY;
      // How far the wall is on the side of the camera, and how much it faces it
      double side = middleX * toCameraX + middleY * toCameraY;
      double normalX = -(wall.getYEnd() - wall.getYStart()) / length;
      double normalY = (wall.getXEnd() - wall.getXStart()) / length;
      double facing = Math.abs(normalX * toCameraX + normalY * toCameraY);
      if (side > 10 && facing > 0.35) {
        hiddenWalls.add(wall);
      }
    }
    return hiddenWalls;
  }

  /**
   * Adds to <code>hiddenFurniture</code> the doors and windows of
   * <code>furniture</code> that are in one of the walls left out.
   */
  public static void addHiddenDoorsAndWindows(List<HomePieceOfFurniture> furniture, Set<Wall> hiddenWalls,
                                              Set<HomePieceOfFurniture> hiddenFurniture) {
    if (hiddenWalls.isEmpty()) {
      return;
    }
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        addHiddenDoorsAndWindows(((HomeFurnitureGroup)piece).getFurniture(), hiddenWalls, hiddenFurniture);
      } else if (piece.isDoorOrWindow()) {
        for (Wall wall : hiddenWalls) {
          if ((wall.getLevel() == null || piece.getLevel() == null || piece.isAtLevel(wall.getLevel()))
              && wall.containsPoint(piece.getX(), piece.getY(), 5 + piece.getDepth() / 2)) {
            hiddenFurniture.add(piece);
            break;
          }
        }
      }
    }
  }
}
