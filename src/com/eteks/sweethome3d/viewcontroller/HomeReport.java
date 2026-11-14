/*
 * HomeReport.java
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * The report of the quantities of a home: floor areas by level and room,
 * length and surface of the walls, count and price of the furniture. It's
 * what someone needs to estimate flooring, paint and a furniture budget.
 * @author Sweet Home 3D
 */
public class HomeReport {
  private HomeReport() {
    // Utility class
  }

  /**
   * Returns the report of <code>home</code> as a text, with areas in square
   * meters and lengths in meters.
   */
  public static String describe(Home home) {
    StringBuilder report = new StringBuilder("Quantities of the home (m and m2):\n");
    List<Level> levels = new ArrayList<Level>(home.getLevels());
    if (levels.isEmpty()) {
      levels.add(null);
    }
    double totalFloor = 0;
    double totalWallLength = 0;
    double totalWallSurface = 0;
    for (Level level : levels) {
      double floor = 0;
      StringBuilder rooms = new StringBuilder();
      for (Room room : home.getRooms()) {
        if (room.getLevel() == level) {
          double area = room.getArea() / 10000.;
          floor += area;
          rooms.append("    - ").append(room.getName() != null && room.getName().length() > 0 ? room.getName() : "room")
              .append(": ").append(number(area)).append(" m2, perimeter ").append(number(getPerimeter(room) / 100.))
              .append(" m\n");
        }
      }
      double wallLength = 0;
      double wallSurface = 0;
      int wallCount = 0;
      for (Wall wall : home.getWalls()) {
        if (wall.getLevel() == level) {
          double length = wall.getLength() / 100.;
          double startHeight = wall.getHeight() != null ? wall.getHeight() : home.getWallHeight();
          double endHeight = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : startHeight;
          wallCount++;
          wallLength += length;
          // Both sides of the wall
          wallSurface += length * (startHeight + endHeight) / 2 / 100. * 2;
        }
      }
      if (level != null) {
        if (floor == 0 && wallCount == 0) {
          continue;
        }
        report.append("- Level \"").append(level.getName()).append("\": ");
      } else {
        report.append("- ");
      }
      report.append("floor ").append(number(floor)).append(" m2, ").append(wallCount).append(" walls, ")
          .append(number(wallLength)).append(" m long, ").append(number(wallSurface))
          .append(" m2 of wall surface on both sides\n").append(rooms);
      totalFloor += floor;
      totalWallLength += wallLength;
      totalWallSurface += wallSurface;
    }
    report.append("- Total: floor ").append(number(totalFloor)).append(" m2, walls ")
        .append(number(totalWallLength)).append(" m long, ").append(number(totalWallSurface))
        .append(" m2 of wall surface on both sides (doors and windows not deducted)\n");

    // Furniture grouped by name
    Map<String, int []> counts = new LinkedHashMap<String, int []>();
    Map<String, BigDecimal> prices = new LinkedHashMap<String, BigDecimal>();
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    collect(home.getFurniture(), furniture);
    int doorCount = 0;
    BigDecimal totalPrice = null;
    String currency = null;
    for (HomePieceOfFurniture piece : furniture) {
      if (piece.isDoorOrWindow()) {
        doorCount++;
      }
      String name = (piece.isDoorOrWindow() ? "door/window: " : "")
          + (piece.getName() != null ? piece.getName() : "?");
      int [] count = counts.get(name);
      if (count == null) {
        counts.put(name, count = new int [1]);
      }
      count [0]++;
      BigDecimal price = piece.getPrice();
      if (price != null) {
        prices.put(name, prices.containsKey(name) ? prices.get(name).add(price) : price);
        totalPrice = totalPrice != null ? totalPrice.add(price) : price;
        if (piece.getCurrency() != null) {
          currency = piece.getCurrency();
        }
      }
    }
    report.append("- Furniture: ").append(furniture.size() - doorCount).append(" pieces, ")
        .append(doorCount).append(" doors and windows\n");
    for (Map.Entry<String, int []> entry : counts.entrySet()) {
      report.append("    - ").append(entry.getValue() [0]).append(" x ").append(entry.getKey());
      BigDecimal price = prices.get(entry.getKey());
      if (price != null) {
        report.append(" (").append(price.toPlainString()).append(currency != null ? " " + currency : "").append(')');
      }
      report.append('\n');
    }
    if (totalPrice != null) {
      report.append("- Price of the furniture that has one: ").append(totalPrice.toPlainString())
          .append(currency != null ? " " + currency : "").append('\n');
    } else if (!furniture.isEmpty()) {
      report.append("- No piece of furniture has a price\n");
    }
    return report.toString().trim();
  }

  private static void collect(List<HomePieceOfFurniture> furniture, List<HomePieceOfFurniture> pieces) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collect(((HomeFurnitureGroup)piece).getFurniture(), pieces);
      } else {
        pieces.add(piece);
      }
    }
  }

  private static double getPerimeter(Room room) {
    float [][] points = room.getPoints();
    double perimeter = 0;
    for (int i = 0; i < points.length; i++) {
      float [] next = points [(i + 1) % points.length];
      perimeter += Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
    }
    return perimeter;
  }

  private static String number(double value) {
    return String.format(Locale.US, "%.1f", value);
  }
}
