/*
 * HomeItemIds.java
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * The short ids (<code>F12</code>, <code>W3</code>, <code>R5</code>, <code>L2</code>)
 * the design assistant and MCP clients use to designate the furniture, the
 * walls, the rooms and the levels of a home. The number of an item is given
 * once and stored in a property of the item, which is saved with the home: it
 * doesn't change when other items are added or deleted, and it isn't given to
 * an other item afterwards.
 * @author Sweet Home 3D
 */
public final class HomeItemIds {
  /** Property of an item storing the number of its id. */
  public static final String ID_PROPERTY = "assistantId";
  /** Prefix of the properties of the environment of a home storing the last number given to each kind of item. */
  private static final String LAST_ID_PROPERTY = "assistantLastId";

  private HomeItemIds() {
    // Utility class
  }

  /**
   * Gives an id to the top level furniture, the walls, the rooms and the levels
   * of <code>home</code> that have none yet, in the order of the home, and a
   * new one to the copies that share the id of an other item.
   */
  public static void ensureIds(Home home) {
    ensureIds(home, 'F', home.getFurniture());
    ensureIds(home, 'W', new ArrayList<Wall>(home.getWalls()));
    ensureIds(home, 'R', home.getRooms());
    ensureIds(home, 'L', home.getLevels());
  }

  private static void ensureIds(Home home, char type, List<? extends HomeObject> items) {
    if (items.isEmpty()) {
      return;
    }
    int last = getLastNumber(home, type);
    Set<Integer> used = new HashSet<Integer>();
    List<HomeObject> withoutId = new ArrayList<HomeObject>();
    for (HomeObject item : items) {
      int number = getNumber(item);
      if (number > 0 && used.add(number)) {
        last = Math.max(last, number);
      } else {
        withoutId.add(item);
      }
    }
    for (HomeObject item : withoutId) {
      item.setProperty(ID_PROPERTY, String.valueOf(++last));
    }
    if (last != getLastNumber(home, type)) {
      home.getEnvironment().setProperty(LAST_ID_PROPERTY + type, String.valueOf(last));
    }
  }

  private static int getLastNumber(Home home, char type) {
    return parseNumber(home.getEnvironment().getProperty(LAST_ID_PROPERTY + type));
  }

  private static int getNumber(HomeObject item) {
    return parseNumber(item.getProperty(ID_PROPERTY));
  }

  private static int parseNumber(String text) {
    if (text != null) {
      try {
        return Integer.parseInt(text.trim());
      } catch (NumberFormatException ex) {
        // No number
      }
    }
    return 0;
  }

  /**
   * Returns the id of <code>item</code> (<code>F12</code>, <code>W3</code>,
   * <code>R5</code> or <code>L2</code>), or <code>null</code> if it has none.
   */
  public static String getId(Object item) {
    char type;
    if (item instanceof HomePieceOfFurniture) {
      type = 'F';
    } else if (item instanceof Wall) {
      type = 'W';
    } else if (item instanceof Room) {
      type = 'R';
    } else if (item instanceof Level) {
      type = 'L';
    } else {
      return null;
    }
    int number = getNumber((HomeObject)item);
    return number > 0 ? String.valueOf(type) + number : null;
  }

  /**
   * Returns <code>true</code> if <code>token</code> is written like a short id.
   */
  public static boolean isId(String token) {
    return token != null && token.trim().matches("[FWRLfwrl]\\s*\\d+");
  }

  /**
   * Returns the item of <code>home</code> designated by <code>token</code>,
   * a short id like <code>F12</code> or the internal id of the object
   * (the one of {@link HomeObject#getId()}), or <code>null</code>.
   */
  public static HomeObject find(Home home, String token) {
    if (token == null) {
      return null;
    }
    token = token.trim();
    if (isId(token)) {
      char type = Character.toUpperCase(token.charAt(0));
      int number = parseNumber(token.substring(1));
      List<? extends HomeObject> items = type == 'F' ? home.getFurniture()
          : type == 'W' ? new ArrayList<Wall>(home.getWalls())
          : type == 'R' ? home.getRooms()
          : home.getLevels();
      for (HomeObject item : items) {
        if (getNumber(item) == number) {
          return item;
        }
      }
      return null;
    } else if (token.indexOf('-') > 0) {
      String lowerToken = token.toLowerCase(Locale.ENGLISH);
      for (int i = 0; i < 4; i++) {
        List<? extends HomeObject> items = i == 0 ? home.getFurniture()
            : i == 1 ? new ArrayList<Wall>(home.getWalls())
            : i == 2 ? home.getRooms()
            : home.getLevels();
        for (HomeObject item : items) {
          if (lowerToken.equals(item.getId().toLowerCase(Locale.ENGLISH))) {
            return item;
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns the levels of <code>home</code> designated by <code>token</code>:
   * the level with that id, or every level with that name.
   */
  public static List<Level> findLevels(Home home, String token) {
    List<Level> levels = new ArrayList<Level>();
    if (token != null) {
      HomeObject item = find(home, token);
      if (item instanceof Level) {
        levels.add((Level)item);
      } else {
        for (Level level : home.getLevels()) {
          if (token.trim().equalsIgnoreCase(level.getName())) {
            levels.add(level);
          }
        }
      }
    }
    return levels;
  }
}
