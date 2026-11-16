/*
 * RoomDimensions.java 16 Nov 2026
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
import java.util.Collection;
import java.util.List;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;

/**
 * Computes the dimension lines that give the size of rooms, as floor plans
 * usually show them: one line inside the room along each of its sides,
 * except the sides that only repeat a size already given (the opposite side
 * of a rectangle).
 * @author Sweet Home 3D
 */
public final class RoomDimensions {
  /** The distance between a side of a room and its dimension line. */
  public static final float OFFSET = 25;

  private static final float MIN_SIDE_LENGTH = 40;
  private static final float TOLERANCE = 1;

  private RoomDimensions() {
  }

  /**
   * Returns the dimension lines of the given rooms that <code>home</code>
   * doesn't have yet. They aren't added to the home.
   */
  public static List<DimensionLine> createDimensionLines(Home home, Collection<Room> rooms) {
    List<DimensionLine> dimensionLines = new ArrayList<DimensionLine>();
    for (Room room : rooms) {
      float [][] points = room.getPoints();
      if (points.length < 3) {
        continue;
      }
      // Sizes already given for this room, as {angle modulo 180 degrees, length}
      List<double []> givenSizes = new ArrayList<double []>();
      for (int i = 0; i < points.length; i++) {
        float [] start = points [i];
        float [] end = points [(i + 1) % points.length];
        double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
        if (length < MIN_SIDE_LENGTH) {
          continue;
        }
        double angle = Math.atan2(end [1] - start [1], end [0] - start [0]);
        double direction = ((angle % Math.PI) + Math.PI) % Math.PI;
        boolean given = false;
        for (double [] size : givenSizes) {
          double directionDifference = Math.abs(size [0] - direction);
          if ((directionDifference < 0.01 || Math.abs(directionDifference - Math.PI) < 0.01)
              && Math.abs(size [1] - length) < TOLERANCE) {
            given = true;
            break;
          }
        }
        if (given) {
          continue;
        }
        givenSizes.add(new double [] {direction, length});
        // Put the line on the side of the room
        DimensionLine dimensionLine = new DimensionLine(start [0], start [1], end [0], end [1], OFFSET);
        float [][] linePoints = dimensionLine.getPoints();
        float middleX = (linePoints [1][0] + linePoints [2][0]) / 2;
        float middleY = (linePoints [1][1] + linePoints [2][1]) / 2;
        if (!room.containsPoint(middleX, middleY, 0)) {
          dimensionLine = new DimensionLine(start [0], start [1], end [0], end [1], -OFFSET);
        }
        dimensionLine.setLevel(room.getLevel());
        if (!exists(home.getDimensionLines(), dimensionLine, room.getLevel())
            && !exists(dimensionLines, dimensionLine, room.getLevel())) {
          dimensionLines.add(dimensionLine);
        }
      }
    }
    return dimensionLines;
  }

  /**
   * Returns <code>true</code> if a dimension line between the same points
   * is among <code>dimensionLines</code>.
   */
  private static boolean exists(Collection<DimensionLine> dimensionLines, DimensionLine dimensionLine, Level level) {
    for (DimensionLine line : dimensionLines) {
      if (line.getLevel() == level && !line.isElevationDimensionLine()
          && (near(line.getXStart(), line.getYStart(), dimensionLine.getXStart(), dimensionLine.getYStart())
                && near(line.getXEnd(), line.getYEnd(), dimensionLine.getXEnd(), dimensionLine.getYEnd())
              || near(line.getXStart(), line.getYStart(), dimensionLine.getXEnd(), dimensionLine.getYEnd())
                && near(line.getXEnd(), line.getYEnd(), dimensionLine.getXStart(), dimensionLine.getYStart()))) {
        return true;
      }
    }
    return false;
  }

  private static boolean near(float x1, float y1, float x2, float y2) {
    return Math.abs(x1 - x2) < TOLERANCE && Math.abs(y1 - y2) < TOLERANCE;
  }
}
