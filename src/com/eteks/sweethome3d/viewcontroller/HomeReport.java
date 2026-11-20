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
    int structureCount = 0;
    Map<String, int []> structureCounts = new LinkedHashMap<String, int []>();
    BigDecimal totalPrice = null;
    String currency = null;
    for (HomePieceOfFurniture piece : furniture) {
      if (piece.isDoorOrWindow()) {
        doorCount++;
      }
      // A roof, a slab or the ground is a piece of furniture in the home, not in a list of furniture
      boolean structure = !piece.isDoorOrWindow()
          && (piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) != null
              || "true".equals(piece.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY)));
      String name = (piece.isDoorOrWindow() ? "door/window: " : "")
          + (piece.getName() != null ? piece.getName() : "?");
      Map<String, int []> pieceCounts = structure ? structureCounts : counts;
      int [] count = pieceCounts.get(name);
      if (count == null) {
        pieceCounts.put(name, count = new int [1]);
      }
      count [0]++;
      if (structure) {
        structureCount++;
      }
      BigDecimal price = piece.getPrice();
      if (price != null) {
        prices.put(name, prices.containsKey(name) ? prices.get(name).add(price) : price);
        totalPrice = totalPrice != null ? totalPrice.add(price) : price;
        if (piece.getCurrency() != null) {
          currency = piece.getCurrency();
        }
      }
    }
    report.append("- Furniture: ").append(furniture.size() - doorCount - structureCount).append(" pieces, ")
        .append(doorCount).append(" doors and windows\n");
    for (Map.Entry<String, int []> entry : counts.entrySet()) {
      report.append("    - ").append(entry.getValue() [0]).append(" x ").append(entry.getKey());
      BigDecimal price = prices.get(entry.getKey());
      if (price != null) {
        report.append(" (").append(price.toPlainString()).append(currency != null ? " " + currency : "").append(')');
      }
      report.append('\n');
    }
    if (structureCount > 0) {
      report.append("- Building and site (roofs, slabs, ground, other parts built for the house): ")
          .append(structureCount).append(structureCount == 1 ? " piece\n" : " pieces\n");
      for (Map.Entry<String, int []> entry : structureCounts.entrySet()) {
        report.append("    - ").append(entry.getValue() [0]).append(" x ").append(entry.getKey());
        BigDecimal price = prices.get(entry.getKey());
        if (price != null) {
          report.append(" (").append(price.toPlainString()).append(currency != null ? " " + currency : "").append(')');
        }
        report.append('\n');
      }
    }
    if (totalPrice != null) {
      report.append("- Price of the furniture that has one: ").append(totalPrice.toPlainString())
          .append(currency != null ? " " + currency : "").append('\n');
    } else if (!furniture.isEmpty()) {
      report.append("- No piece of furniture has a price\n");
    }
    appendBudget(report, home, totalFloor, totalWallSurface, totalPrice, currency);
    return report.toString().trim();
  }

  /** The unit prices of a budget: their keys, and what each one is the price of. */
  public static final String [] UNIT_PRICE_KEYS = {"paint", "flooring", "ceiling", "baseboard"};
  private static final String [] UNIT_PRICE_LABELS = {
      "Wall paint or covering, per m2 of wall side", "Flooring, per m2 of floor",
      "Ceiling paint, per m2 of ceiling", "Baseboard, per m of room perimeter"};
  private static final String UNIT_PRICE_PROPERTY_PREFIX = "budgetUnitPrice.";
  private static final String BUDGET_CURRENCY_PROPERTY = "budgetCurrency";

  /**
   * Returns what the unit price of the given key is the price of.
   */
  public static String getUnitPriceLabel(String key) {
    for (int i = 0; i < UNIT_PRICE_KEYS.length; i++) {
      if (UNIT_PRICE_KEYS [i].equals(key)) {
        return UNIT_PRICE_LABELS [i];
      }
    }
    return null;
  }

  /**
   * Returns the unit price stored in <code>home</code> for the given key, or <code>null</code>.
   */
  public static BigDecimal getUnitPrice(Home home, String key) {
    String value = home.getProperty(UNIT_PRICE_PROPERTY_PREFIX + key);
    if (value != null) {
      try {
        return new BigDecimal(value);
      } catch (NumberFormatException ex) {
        // No price
      }
    }
    return null;
  }

  /**
   * Stores a unit price in <code>home</code>, saved with it, or removes it
   * if <code>price</code> is <code>null</code> or negative.
   */
  public static void setUnitPrice(Home home, String key, BigDecimal price) {
    if (getUnitPriceLabel(key) != null) {
      String value = price != null && price.signum() >= 0 ? price.toPlainString() : null;
      String oldValue = home.getProperty(UNIT_PRICE_PROPERTY_PREFIX + key);
      if (value == null ? oldValue != null : !value.equals(oldValue)) {
        home.setProperty(UNIT_PRICE_PROPERTY_PREFIX + key, value);
        home.setModified(true);
      }
    }
  }

  /**
   * Returns the currency of the budget of <code>home</code>, or <code>null</code>.
   */
  public static String getBudgetCurrency(Home home) {
    return home.getProperty(BUDGET_CURRENCY_PROPERTY);
  }

  public static void setBudgetCurrency(Home home, String currency) {
    String value = currency != null && currency.trim().length() > 0
        ? currency.trim().substring(0, Math.min(8, currency.trim().length())) : null;
    String oldValue = home.getProperty(BUDGET_CURRENCY_PROPERTY);
    if (value == null ? oldValue != null : !value.equals(oldValue)) {
      home.setProperty(BUDGET_CURRENCY_PROPERTY, value);
      home.setModified(true);
    }
  }

  /**
   * Appends the budget computed from the unit prices of the home, if it has any.
   */
  private static void appendBudget(StringBuilder report, Home home, double floor, double wallSurface,
                                   BigDecimal furniturePrice, String furnitureCurrency) {
    double perimeter = 0;
    for (Room room : home.getRooms()) {
      perimeter += getPerimeter(room) / 100.;
    }
    double [] quantities = {wallSurface, floor, floor, perimeter};
    String [] units = {"m2", "m2", "m2", "m"};
    String currency = getBudgetCurrency(home) != null ? getBudgetCurrency(home) : furnitureCurrency;
    String suffix = currency != null ? " " + currency : "";
    BigDecimal total = BigDecimal.ZERO;
    StringBuilder lines = new StringBuilder();
    for (int i = 0; i < UNIT_PRICE_KEYS.length; i++) {
      BigDecimal unitPrice = getUnitPrice(home, UNIT_PRICE_KEYS [i]);
      if (unitPrice != null) {
        BigDecimal quantity = new BigDecimal(quantities [i]).setScale(1, java.math.RoundingMode.HALF_UP);
        BigDecimal cost = quantity.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
        total = total.add(cost);
        lines.append("    - ").append(UNIT_PRICE_LABELS [i]).append(": ").append(quantity.toPlainString()).append(' ')
            .append(units [i]).append(" x ").append(unitPrice.toPlainString()).append(" = ")
            .append(cost.toPlainString()).append(suffix).append('\n');
      }
    }
    if (lines.length() == 0) {
      report.append("- No budget: no unit price is set (wall paint, flooring, ceiling, baseboard)\n");
      return;
    }
    report.append("- Budget from the unit prices (walls on both sides, doors and windows not deducted):\n").append(lines);
    if (furniturePrice != null) {
      total = total.add(furniturePrice);
      report.append("    - Furniture that has a price: ").append(furniturePrice.toPlainString()).append(suffix).append('\n');
    }
    report.append("    - Total: ").append(total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
        .append(suffix).append('\n');
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
