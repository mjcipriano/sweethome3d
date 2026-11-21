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
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * The report of the quantities of a home: floor areas by level and room,
 * length and surface of the walls with the openings of doors and windows
 * deducted, count and price of the furniture. It's what someone needs to
 * estimate flooring, paint and a furniture budget. Unit prices are set for
 * the whole home, and a room or a wall may have its own ones.
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
    HomeQuantities quantities = new HomeQuantities(home);
    double totalFloor = 0;
    double totalWallLength = 0;
    double totalWallSurface = 0;
    double totalOpenings = 0;
    for (Level level : levels) {
      double floor = 0;
      StringBuilder rooms = new StringBuilder();
      for (Room room : home.getRooms()) {
        if (room.getLevel() == level) {
          double area = room.getArea() / 10000.;
          floor += area;
          rooms.append("    - ").append(room.getName() != null && room.getName().length() > 0 ? room.getName() : "room")
              .append(": ").append(number(area)).append(" m2, perimeter ").append(number(getPerimeter(room) / 100.))
              .append(" m");
          double [] roomWalls = quantities.getWallSurface(room);
          if (roomWalls != null) {
            // The wall sides that face the room
            rooms.append(", walls ").append(number(roomWalls [2] / 10000)).append(" m2 net");
            if (roomWalls [1] > 0) {
              rooms.append(" (").append(number(roomWalls [1] / 10000)).append(" m2 of openings deducted)");
            }
          }
          String ownPrices = describeOwnPrices(room, UNIT_PRICE_KEYS);
          if (ownPrices.length() > 0) {
            rooms.append(" [own prices: ").append(ownPrices).append(']');
          }
          rooms.append('\n');
        }
      }
      double wallLength = 0;
      double wallSurface = 0;
      double openings = 0;
      int wallCount = 0;
      for (HomeQuantities.WallQuantity quantity : quantities.getWallQuantities()) {
        if (quantity.wall.getLevel() == level) {
          wallCount++;
          wallLength += quantity.length / 100.;
          // Both sides of the wall
          wallSurface += quantity.getGrossSide() / 10000. * 2;
          openings += quantity.getOpeningsSide() / 10000. * 2;
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
          .append(" m2 of wall surface on both sides, ").append(number(openings))
          .append(" m2 of openings, ").append(number(wallSurface - openings)).append(" m2 net\n").append(rooms);
      totalFloor += floor;
      totalWallLength += wallLength;
      totalWallSurface += wallSurface;
      totalOpenings += openings;
    }
    report.append("- Total: floor ").append(number(totalFloor)).append(" m2, walls ")
        .append(number(totalWallLength)).append(" m long, ").append(number(totalWallSurface))
        .append(" m2 of wall surface on both sides, ").append(number(totalOpenings))
        .append(" m2 of openings of doors and windows deducted, ")
        .append(number(totalWallSurface - totalOpenings)).append(" m2 net\n");
    StringBuilder wallPrices = new StringBuilder();
    for (Wall wall : home.getWalls()) {
      String ownPrices = describeOwnPrices(wall, WALL_PRICE_KEYS);
      if (ownPrices.length() > 0) {
        wallPrices.append(wallPrices.length() > 0 ? "; " : "").append(getLabel(wall)).append(": ").append(ownPrices);
      }
    }
    if (wallPrices.length() > 0) {
      report.append("- Walls with their own price: ").append(wallPrices).append('\n');
    }

    // Furniture grouped by name
    Map<String, int []> counts = new LinkedHashMap<String, int []>();
    Map<String, BigDecimal> prices = new LinkedHashMap<String, BigDecimal>();
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    collect(home.getFurniture(), furniture);
    int doorCount = 0;
    int structureCount = 0;
    Map<String, int []> symbolCounts = new java.util.TreeMap<String, int []>();
    int symbolCount = 0;
    Map<String, int []> structureCounts = new LinkedHashMap<String, int []>();
    BigDecimal totalPrice = null;
    String currency = null;
    for (HomePieceOfFurniture piece : furniture) {
      if (piece.isDoorOrWindow()) {
        doorCount++;
      } else if (piece.getProperty(SYMBOL_TYPE_PROPERTY) != null) {
        // An electrical or plumbing symbol is counted on its own
        String type = piece.getProperty(SYMBOL_TYPE_PROPERTY);
        int [] count = symbolCounts.get(type);
        if (count == null) {
          symbolCounts.put(type, count = new int [1]);
        }
        count [0]++;
        symbolCount++;
        continue;
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
    report.append("- Furniture: ").append(furniture.size() - doorCount - structureCount - symbolCount).append(" pieces, ")
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
    if (symbolCount > 0) {
      report.append("- Electrical and plumbing symbols: ").append(symbolCount).append(" (");
      boolean first = true;
      for (Map.Entry<String, int []> entry : symbolCounts.entrySet()) {
        report.append(first ? "" : ", ").append(entry.getValue() [0]).append(" x ").append(entry.getKey());
        first = false;
      }
      report.append("); symbol_schedule lists them\n");
    }
    if (totalPrice != null) {
      report.append("- Price of the furniture that has one: ").append(totalPrice.toPlainString())
          .append(currency != null ? " " + currency : "").append('\n');
    } else if (furniture.size() > symbolCount) {
      report.append("- No piece of furniture has a price\n");
    }
    appendBudget(report, home, quantities, totalPrice, currency);
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

  /** The property of a piece of furniture that is an electrical or plumbing symbol: its type. */
  static final String SYMBOL_TYPE_PROPERTY = "symbolType";

  /** The unit prices a wall may have: for its two sides, its left side or its right side. */
  static final String [] WALL_PRICE_KEYS = {"paint", "paint.left", "paint.right"};

  /**
   * Returns the unit price a room or a wall has of its own for the given key, or <code>null</code>
   * if it uses the price of the home. The key of a wall is <code>paint</code> for its two sides,
   * <code>paint.left</code> or <code>paint.right</code> for one of them.
   */
  public static BigDecimal getItemUnitPrice(HomeObject item, String key) {
    String value = item.getProperty(UNIT_PRICE_PROPERTY_PREFIX + key);
    if (value != null) {
      try {
        BigDecimal price = new BigDecimal(value);
        return price.signum() >= 0 ? price : null;
      } catch (NumberFormatException ex) {
        // No price
      }
    }
    return null;
  }

  /**
   * Returns the name of the property of a room or a wall that stores its own unit price.
   */
  static String getItemUnitPriceProperty(String key) {
    return UNIT_PRICE_PROPERTY_PREFIX + key;
  }

  private static String describeOwnPrices(HomeObject item, String [] keys) {
    StringBuilder text = new StringBuilder();
    for (String key : keys) {
      BigDecimal price = getItemUnitPrice(item, key);
      if (price != null) {
        text.append(text.length() > 0 ? ", " : "").append(key.replace('.', ' ')).append(' ').append(price.toPlainString());
      }
    }
    return text.toString();
  }

  private static String getLabel(HomeObject item) {
    String id = HomeItemIds.getId(item);
    if (item instanceof Room) {
      String name = ((Room)item).getName();
      return (name != null && name.length() > 0 ? name : "room") + (id != null ? " (" + id + ")" : "");
    }
    return id != null ? id : "wall";
  }

  /**
   * Returns the price of the paint of a side of a wall if the wall has its own one, or <code>null</code>.
   */
  private static BigDecimal getWallSidePrice(Wall wall, boolean left) {
    BigDecimal price = getItemUnitPrice(wall, left ? "paint.left" : "paint.right");
    return price != null ? price : getItemUnitPrice(wall, "paint");
  }

  /**
   * Appends the budget computed from the unit prices of the home and the ones
   * of its rooms and walls, if it has any.
   */
  private static void appendBudget(StringBuilder report, Home home, HomeQuantities quantities,
                                   BigDecimal furniturePrice, String furnitureCurrency) {
    String [] units = {"m2", "m2", "m2", "m"};
    String currency = getBudgetCurrency(home) != null ? getBudgetCurrency(home) : furnitureCurrency;
    String suffix = currency != null ? " " + currency : "";
    BigDecimal total = BigDecimal.ZERO;
    StringBuilder lines = new StringBuilder();
    for (int i = 0; i < UNIT_PRICE_KEYS.length; i++) {
      String key = UNIT_PRICE_KEYS [i];
      // What is paid at the price of the home, and what has its own price
      double quantity = 0;
      List<String> ownLabels = new ArrayList<String>();
      List<Double> ownQuantities = new ArrayList<Double>();
      List<BigDecimal> ownPrices = new ArrayList<BigDecimal>();
      if ("paint".equals(key)) {
        for (HomeQuantities.WallQuantity wallQuantity : quantities.getWallQuantities()) {
          double net = wallQuantity.getNetSide() / 10000.;
          BigDecimal leftPrice = getWallSidePrice(wallQuantity.wall, true);
          BigDecimal rightPrice = getWallSidePrice(wallQuantity.wall, false);
          if (leftPrice != null && rightPrice != null && leftPrice.compareTo(rightPrice) == 0) {
            ownLabels.add(getLabel(wallQuantity.wall) + ", both sides");
            ownQuantities.add(net * 2);
            ownPrices.add(leftPrice);
          } else {
            if (leftPrice != null) {
              ownLabels.add(getLabel(wallQuantity.wall) + ", left side");
              ownQuantities.add(net);
              ownPrices.add(leftPrice);
            }
            if (rightPrice != null) {
              ownLabels.add(getLabel(wallQuantity.wall) + ", right side");
              ownQuantities.add(net);
              ownPrices.add(rightPrice);
            }
          }
          quantity += net * ((leftPrice == null ? 1 : 0) + (rightPrice == null ? 1 : 0));
        }
        // The wall sides that face a room with its own price, unless the wall has its own one
        for (Room room : home.getRooms()) {
          BigDecimal roomPrice = getItemUnitPrice(room, key);
          if (roomPrice != null) {
            double roomQuantity = 0;
            for (HomeQuantities.Facing facing : quantities.getFacings(room)) {
              if (getWallSidePrice(facing.wallQuantity.wall, facing.left) == null) {
                roomQuantity += facing.getNet() / 10000.;
              }
            }
            if (roomQuantity > 0) {
              ownLabels.add(getLabel(room));
              ownQuantities.add(roomQuantity);
              ownPrices.add(roomPrice);
              quantity -= roomQuantity;
            }
          }
        }
        quantity = Math.max(0, quantity);
      } else {
        for (Room room : home.getRooms()) {
          double roomQuantity = "baseboard".equals(key) ? getPerimeter(room) / 100. : room.getArea() / 10000.;
          BigDecimal roomPrice = getItemUnitPrice(room, key);
          if (roomPrice != null) {
            ownLabels.add(getLabel(room));
            ownQuantities.add(roomQuantity);
            ownPrices.add(roomPrice);
          } else {
            quantity += roomQuantity;
          }
        }
      }
      BigDecimal unitPrice = getUnitPrice(home, key);
      if (unitPrice != null) {
        BigDecimal roundedQuantity = new BigDecimal(quantity).setScale(1, java.math.RoundingMode.HALF_UP);
        BigDecimal cost = roundedQuantity.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
        total = total.add(cost);
        lines.append("    - ").append(UNIT_PRICE_LABELS [i]).append(": ").append(roundedQuantity.toPlainString()).append(' ')
            .append(units [i]).append(" x ").append(unitPrice.toPlainString()).append(" = ")
            .append(cost.toPlainString()).append(suffix).append('\n');
      } else if (!ownLabels.isEmpty()) {
        lines.append("    - ").append(UNIT_PRICE_LABELS [i]).append(": no price for the whole home");
        if (quantity >= 0.05) {
          lines.append(", ").append(number(quantity)).append(' ').append(units [i]).append(" not counted");
        }
        lines.append('\n');
      }
      for (int j = 0; j < ownLabels.size(); j++) {
        BigDecimal roundedQuantity = new BigDecimal(ownQuantities.get(j)).setScale(1, java.math.RoundingMode.HALF_UP);
        BigDecimal cost = roundedQuantity.multiply(ownPrices.get(j)).setScale(2, java.math.RoundingMode.HALF_UP);
        total = total.add(cost);
        lines.append("        - ").append(ownLabels.get(j)).append(", own price: ").append(roundedQuantity.toPlainString())
            .append(' ').append(units [i]).append(" x ").append(ownPrices.get(j).toPlainString()).append(" = ")
            .append(cost.toPlainString()).append(suffix).append('\n');
      }
    }
    if (lines.length() == 0) {
      report.append("- No budget: no unit price is set (wall paint, flooring, ceiling, baseboard)\n");
      return;
    }
    report.append("- Budget from the unit prices (walls on both sides, openings of doors and windows deducted):\n").append(lines);
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
