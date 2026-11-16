/*
 * HomeReviewNotes.java 16 Nov 2026
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
 * The review notes of a home: remarks written while visiting it in the
 * walk-through or the game mode ("add an outlet here", "fix this model"),
 * each one pinned to a point of a wall, a piece of furniture, a door, a
 * window or a room. They are stored with the home, in its property
 * <code>reviewNotes</code>, as a JSON array, and given to the design
 * assistant and to MCP clients with the id of the item they are about and
 * the coordinates of their point, so the remark can be acted upon.
 * @author Sweet Home 3D
 */
public final class HomeReviewNotes {
  /** The name of the property of a home that stores its notes. */
  public static final String NOTES_PROPERTY = "reviewNotes";
  public static final String STATUS_OPEN = "open";
  public static final String STATUS_DONE = "done";

  private static final int MAX_NOTES = 500;
  private static final int MAX_TEXT_LENGTH = 2000;
  private static final int MAX_JSON_LENGTH = 512 * 1024;

  private HomeReviewNotes() {
  }

  /**
   * Returns the key that designates an item of a home in the notes and in
   * the files exported for the walk-through.
   */
  public static String getKey(HomeObject item) {
    return item.getId().replaceAll("[^A-Za-z0-9]", "");
  }

  /**
   * Returns the notes of <code>home</code> as a JSON array.
   */
  public static String getJSON(Home home) {
    String json = home.getProperty(NOTES_PROPERTY);
    return json != null ? json : "[]";
  }

  /**
   * Returns the notes of <code>home</code>, each one a map with the keys
   * <code>id</code>, <code>text</code>, <code>status</code>, <code>target</code>,
   * <code>key</code>, <code>name</code>, <code>x</code>, <code>y</code>, <code>z</code>,
   * <code>level</code> and <code>reply</code>.
   */
  public static List<Map<String, Object>> getNotes(Home home) {
    return parse(getJSON(home));
  }

  private static List<Map<String, Object>> parse(String json) {
    List<Map<String, Object>> notes = new ArrayList<Map<String, Object>>();
    try {
      Object value = JSONParser.parse(json);
      if (value instanceof List) {
        for (Object item : (List<?>)value) {
          if (item instanceof Map && notes.size() < MAX_NOTES) {
            Map<String, Object> note = clean((Map<?, ?>)item, notes.size() + 1);
            if (note != null) {
              notes.add(note);
            }
          }
        }
      }
    } catch (RuntimeException ex) {
      // Not notes
    }
    return notes;
  }

  /**
   * Returns a note with the fields this class knows, of the type and the
   * size it expects, or <code>null</code> if it has no text.
   */
  private static Map<String, Object> clean(Map<?, ?> source, int index) {
    Object text = source.get("text");
    if (!(text instanceof String) || ((String)text).trim().length() == 0) {
      return null;
    }
    Map<String, Object> note = new LinkedHashMap<String, Object>();
    Object id = source.get("id");
    note.put("id", id instanceof String && ((String)id).matches("[A-Za-z0-9_-]{1,40}") ? id : "n" + index);
    note.put("text", limit(((String)text).trim(), MAX_TEXT_LENGTH));
    note.put("status", STATUS_DONE.equals(source.get("status")) ? STATUS_DONE : STATUS_OPEN);
    for (String key : new String [] {"target", "key", "name", "level", "reply", "created"}) {
      if (source.get(key) instanceof String) {
        note.put(key, limit((String)source.get(key), key.equals("reply") ? MAX_TEXT_LENGTH : 200));
      }
    }
    for (String key : new String [] {"x", "y", "z"}) {
      Object number = source.get(key);
      if (number instanceof Number && !Double.isNaN(((Number)number).doubleValue())
          && Math.abs(((Number)number).doubleValue()) < 1E7) {
        note.put(key, Double.valueOf(Math.round(((Number)number).doubleValue() * 10) / 10.));
      }
    }
    return note;
  }

  private static String limit(String text, int length) {
    return text.length() > length ? text.substring(0, length) : text;
  }

  /**
   * Stores the notes given as a JSON array in <code>home</code>, keeping
   * only what a note may contain.
   * @return the count of notes stored
   * @throws IllegalArgumentException if <code>json</code> isn't a JSON array of a reasonable size
   */
  public static int setJSON(Home home, String json) {
    if (json == null || json.length() > MAX_JSON_LENGTH) {
      throw new IllegalArgumentException("Notes too long");
    }
    Object value;
    try {
      value = JSONParser.parse(json);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Notes aren't JSON");
    }
    if (!(value instanceof List)) {
      throw new IllegalArgumentException("Notes must be a JSON array");
    }
    List<Map<String, Object>> notes = parse(json);
    setNotes(home, notes);
    return notes.size();
  }

  private static void setNotes(Home home, List<Map<String, Object>> notes) {
    String json = notes.isEmpty() ? null : JSONParser.toJSON(notes);
    String oldJson = home.getProperty(NOTES_PROPERTY);
    if (json == null ? oldJson != null : !json.equals(oldJson)) {
      home.setProperty(NOTES_PROPERTY, json);
      home.setModified(true);
    }
  }

  /**
   * Marks a note as done or open again, with what was done about it, or
   * deletes it if <code>status</code> is <code>"deleted"</code>.
   * @return <code>false</code> if the home has no note with this id
   */
  public static boolean updateNote(Home home, String id, String status, String reply) {
    List<Map<String, Object>> notes = getNotes(home);
    for (int i = 0; i < notes.size(); i++) {
      Map<String, Object> note = notes.get(i);
      if (note.get("id").equals(id)) {
        if ("deleted".equals(status) || "delete".equals(status)) {
          notes.remove(i);
        } else {
          if (status != null) {
            note.put("status", STATUS_DONE.equals(status) ? STATUS_DONE : STATUS_OPEN);
          }
          if (reply != null) {
            note.put("reply", limit(reply.trim(), MAX_TEXT_LENGTH));
          }
        }
        setNotes(home, notes);
        return true;
      }
    }
    return false;
  }

  private static void collect(List<HomePieceOfFurniture> furniture, List<HomePieceOfFurniture> pieces) {
    for (HomePieceOfFurniture piece : furniture) {
      pieces.add(piece);
      if (piece instanceof HomeFurnitureGroup) {
        collect(((HomeFurnitureGroup)piece).getFurniture(), pieces);
      }
    }
  }

  /**
   * Returns the notes of <code>home</code> as a text for the assistant: what
   * each one asks, the item it's about with the id of the description of the
   * home (F# furniture, W# walls, R# rooms), and where its point is, in the
   * centimeters of the plan.
   * @param openOnly <code>true</code> to leave out the notes marked as done
   */
  public static String describe(Home home, boolean openOnly) {
    List<Map<String, Object>> notes = getNotes(home);
    StringBuilder text = new StringBuilder();
    int count = 0;
    List<Wall> walls = new ArrayList<Wall>(home.getWalls());
    List<HomePieceOfFurniture> furniture = home.getFurniture();
    List<HomePieceOfFurniture> allFurniture = new ArrayList<HomePieceOfFurniture>();
    collect(furniture, allFurniture);
    List<Room> rooms = home.getRooms();
    for (Map<String, Object> note : notes) {
      boolean done = STATUS_DONE.equals(note.get("status"));
      if (done && openOnly) {
        continue;
      }
      count++;
      text.append("- ").append(note.get("id")).append(done ? " [done] " : " [open] ")
          .append('"').append(note.get("text")).append('"');
      Object key = note.get("key");
      String item = null;
      Float floor = null;
      if (key != null) {
        for (int i = 0; i < walls.size() && item == null; i++) {
          Wall wall = walls.get(i);
          if (getKey(wall).equals(key)) {
            item = "wall W" + (i + 1) + " (from " + Math.round(wall.getXStart()) + "," + Math.round(wall.getYStart())
                + " to " + Math.round(wall.getXEnd()) + "," + Math.round(wall.getYEnd()) + ")";
            if (note.get("x") instanceof Number && note.get("y") instanceof Number) {
              double along = Math.hypot(((Number)note.get("x")).doubleValue() - wall.getXStart(),
                  ((Number)note.get("y")).doubleValue() - wall.getYStart());
              item += ", " + Math.round(along) + " cm from its start";
            }
            floor = getElevation(wall.getLevel());
          }
        }
        for (int i = 0; i < allFurniture.size() && item == null; i++) {
          HomePieceOfFurniture piece = allFurniture.get(i);
          if (getKey(piece).equals(key)) {
            int index = furniture.indexOf(piece);
            item = (piece.isDoorOrWindow() ? "door/window " : "furniture ")
                + (index >= 0 ? "F" + (index + 1) + " " : "") + '"' + piece.getName() + '"'
                + (index < 0 ? " (in a group)" : "");
            floor = getElevation(piece.getLevel());
          }
        }
        for (int i = 0; i < rooms.size() && item == null; i++) {
          if (getKey(rooms.get(i)).equals(key)) {
            item = "room R" + (i + 1) + (rooms.get(i).getName() != null ? " \"" + rooms.get(i).getName() + '"' : "");
            floor = getElevation(rooms.get(i).getLevel());
          }
        }
      }
      if (item == null) {
        // The item was deleted since, or the note is about the ground
        item = note.get("target") != null ? note.get("target") + (note.get("name") != null
            ? " \"" + note.get("name") + "\"" : "") + (key != null ? " (no longer in the home)" : "") : "the home";
      }
      text.append(" - on ").append(item);
      if (note.get("x") instanceof Number && note.get("y") instanceof Number) {
        text.append(", at x ").append(Math.round(((Number)note.get("x")).doubleValue()))
            .append(" y ").append(Math.round(((Number)note.get("y")).doubleValue())).append(" cm");
        if (note.get("z") instanceof Number) {
          double height = ((Number)note.get("z")).doubleValue() - (floor != null ? floor.floatValue() : 0);
          text.append(", ").append(Math.round(height)).append(" cm above the floor");
        }
      }
      if (note.get("level") != null && home.getLevels().size() > 1) {
        text.append(", level \"").append(note.get("level")).append('"');
      }
      if (note.get("reply") != null) {
        text.append(String.format(Locale.US, " - reply: %s", note.get("reply")));
      }
      text.append('\n');
    }
    if (count == 0) {
      return notes.isEmpty() ? "This home has no review note."
          : "Every review note of this home is done (" + notes.size() + ").";
    }
    return "Review notes of the user (" + count + "), written while visiting the home. Each one asks for "
        + "something at a point of an item; coordinates are those of the plan:\n" + text.toString().trim();
  }

  private static Float getElevation(Level level) {
    return level != null ? Float.valueOf(level.getElevation()) : null;
  }

  /**
   * Returns the count of the notes of <code>home</code> that aren't done.
   */
  public static int getOpenCount(Home home) {
    int count = 0;
    for (Map<String, Object> note : getNotes(home)) {
      if (!STATUS_DONE.equals(note.get("status"))) {
        count++;
      }
    }
    return count;
  }
}
