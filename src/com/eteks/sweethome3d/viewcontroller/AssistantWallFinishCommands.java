/*
 * AssistantWallFinishCommands.java 25 nov. 2026
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

import java.awt.geom.Area;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands of the design assistant that finish walls and open them:
 * <code>paint_rooms</code> and the <code>wall_color</code> of <code>modify_room</code>
 * give wall sides the finish of the room they face, the <code>left_finish</code> and
 * <code>right_finish</code> of <code>modify_wall</code> give a side finishes that change
 * with the height, <code>add_opening</code> opens a wall without a door or a window,
 * <code>add_header</code> and <code>add_soffit</code> add a beam over an opening or a bay
 * and a lowered part of a ceiling.
 * @author Sweet Home 3D contributors
 */
class AssistantWallFinishCommands {
  /** Property of a room: the color of its walls as <code>RRGGBB</code>. */
  static final String ROOM_WALL_COLOR_PROPERTY = "com.eteks.sweethome3d.wallColor";
  /** Property of a room: the name of the texture of the catalog shown by its walls. */
  static final String ROOM_WALL_TEXTURE_PROPERTY = "com.eteks.sweethome3d.wallTexture";
  /** Property of a room: the height of its ceiling above its floor. */
  static final String ROOM_CEILING_HEIGHT_PROPERTY = "com.eteks.sweethome3d.ceilingHeight";
  /** Property of the piece that opens a wall: its wall and its place along it, at JSON format. */
  static final String OPENING_PROPERTY = "com.eteks.sweethome3d.wallOpening";
  /** Property of a header: its description, at JSON format. */
  static final String HEADER_PROPERTY = "com.eteks.sweethome3d.header";

  private static final String CUT_OUT_SHAPE = "M0,0 v1 h1 v-1 z";
  private static final int    DEFAULT_TRIM_COLOR = 0xF2F0EB;
  private static final List<String> BAND_KEYS = Arrays.asList("to", "to_at_end", "color", "texture", "category",
      "catalog_id", "scale", "angle", "offset_x", "offset_y", "texture_scale", "texture_angle", "roof");
  private static final List<String> OPENING_KEYS = Arrays.asList("action", "wall", "id", "from", "to", "center", "width",
      "bottom", "top", "height", "lining", "lining_color", "color", "name");
  private static final List<String> HEADER_KEYS = Arrays.asList("action", "opening", "wall", "from", "to", "points",
      "bottom", "top", "height", "thickness", "bearing", "to_walls", "color", "texture", "category", "catalog_id",
      "texture_scale", "texture_angle", "level", "name", "force");
  private static final List<String> SOFFIT_KEYS = Arrays.asList("action", "points", "room", "bottom", "top", "color",
      "texture", "category", "catalog_id", "texture_scale", "texture_angle", "level", "name");
  private static final List<String> PAINT_KEYS = Arrays.asList("action", "rooms", "room", "id", "ids", "target", "only",
      "color", "texture", "category", "catalog_id", "scale", "angle", "offset_x", "offset_y", "texture_scale",
      "texture_angle", "level");

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantWallFinishCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("paint_rooms".equals(action) || "paint_room".equals(action)) {
        paintRooms(command, tally, notes);
      } else if ("add_opening".equals(action) || "add_wall_opening".equals(action)) {
        addOpening(command, tally, notes);
      } else if ("add_header".equals(action) || "add_beam".equals(action)) {
        addHeader(command, tally, notes);
      } else if ("add_soffit".equals(action)) {
        addSoffit(command, tally, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      if (!"see above".equals(ex.getMessage())) {
        notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
      }
    }
    return true;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"paint_rooms\",\"rooms\":[\"R1\",\"Kitchen\"],\"only\":\"unpainted|all\",\"color\":\"#RRGGBB\"}  "
        + "(gives each wall side the color or the texture of the room it faces: the \"wall_color\" or \"wall_texture\" that "
        + "modify_room stored in the room, or else the \"color\" or \"texture\" of this command. Without \"rooms\", every room, "
        + "or the ones of a \"level\". By default only the sides that have neither color nor texture are painted; \"only\":\"all\" "
        + "paints them all. A side of an upper level that looks into a room open to above gets the finish of that room. "
        + "The reply counts the sides painted by room and names the sides that face no room, which are the outside ones)\n"
        + "  {\"action\":\"modify_wall\",\"id\":\"W1\",\"left_finish\":[{\"to\":274,\"color\":\"#D5AC6B\"},{\"texture\":\"<texture name>\"}]}  "
        + "(finishes of a side that change with the height, listed from the floor up: each one goes up to its \"to\", in cm above the "
        + "floor of the wall, and the last one, without \"to\", is the finish of the side up to its top; " + WallFinishBands.MAX_BANDS
        + " finishes at most under it. \"to_at_end\" gives an other height at the end of the wall, for a sloped line. \"to\":\"roof\" stops "
        + "a finish where a lower roof meets that side of the wall, so a two-story wall is painted as its room under the roof of a "
        + "one-story wing and keeps its siding above; \"roof\":\"F12\" names that roof. Same for \"right_finish\"; false removes them. "
        + "The left side is at the left of who walks from the start of the wall to its end. Doors and windows cut these finishes as they "
        + "cut the wall. A baseboard still comes over them)\n"
        + "  {\"action\":\"add_opening\",\"wall\":\"W1\",\"from\":120,\"to\":300,\"bottom\":0,\"top\":210}  "
        + "(opens a straight wall without door nor window, for a pass-through, a cased opening or an overlook: \"from\" and \"to\" in cm "
        + "from the start of the wall, or \"center\" and \"width\"; \"bottom\" and \"top\" above the floor, or \"height\". The opening is "
        + "a piece that shows only a thin lining around the hole, of \"lining\" cm and \"lining_color\": delete removes it, and it follows "
        + "its wall when modify_wall moves it. modify_wall with \"openings\":[{\"from\":N,\"to\":N,\"bottom\":N,\"top\":N}] replaces all the "
        + "openings of a wall, and [] removes them)\n"
        + "  {\"action\":\"add_header\",\"opening\":\"F12\",\"height\":30,\"color\":\"#RRGGBB\"}  "
        + "(a beam over an opening or a bay: over the piece \"opening\" (an opening, a door or a window), along \"wall\" between \"from\" "
        + "and \"to\", or between \"points\":[[x1,y1],[x2,y2]], whose ends go on to the walls they nearly reach unless \"to_walls\":false. "
        + "Its underside is at \"bottom\" above the floor, the top of the opening by default, and it rises to the ceiling of the level, "
        + "or to \"top\", or by \"height\"; \"thickness\" is the one of the wall by default, \"bearing\" extends it at each end. "
        + "Described by an opening or a wall, it follows them when modify_wall moves the wall. Where a wall as thick as "
        + "the header already stands along it, as above a door or an opening, the header would only double the wall: the "
        + "part that the wall closes is left out, and nothing is added if it closes all of it. A \"thickness\" larger than "
        + "the one of the wall makes a beam that shows; \"force\":true builds it anyway)\n"
        + "  {\"action\":\"add_soffit\",\"points\":[[x,y],[x,y],[x,y],...],\"bottom\":220}  "
        + "(a lowered part of a ceiling, as over a dining bay or kitchen cabinets: a box from \"bottom\" above the floor up to the ceiling "
        + "of the level or to \"top\", with a \"color\" or a \"texture\"; \"room\":\"R1\" lowers the whole ceiling of a room)\n";
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  private static String color(int color) {
    return String.format("#%06X", color & 0xFFFFFF);
  }

  private static void warnUnknownKeys(String action, Map<String, Object> parameters, List<String> knownKeys,
                                      List<String> notes) {
    List<String> unknown = new ArrayList<String>();
    for (String key : parameters.keySet()) {
      if (!knownKeys.contains(key) && !key.startsWith("_")) {
        unknown.add(key);
      }
    }
    if (!unknown.isEmpty()) {
      notes.add(action + " doesn't know " + unknown + ", which had no effect: its keys are "
          + knownKeys.subList(1, knownKeys.size()));
    }
  }

  // ---- Properties changed in an undoable way ----

  /**
   * Sets properties of <code>object</code> and posts the edit that undoes it.
   * @param values the strings or contents of the properties, <code>null</code> removing one
   */
  private void setProperties(final HomeObject object, Map<String, Object> values) {
    final Map<String, Object> before = new LinkedHashMap<String, Object>();
    final Map<String, Object> after = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String name = entry.getKey();
      Object oldValue = object.isContentProperty(name) ? object.getContentProperty(name) : object.getProperty(name);
      if (oldValue == null ? entry.getValue() != null : !oldValue.equals(entry.getValue())) {
        before.put(name, oldValue);
        after.put(name, entry.getValue());
      }
    }
    if (after.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Object> entry : after.entrySet()) {
      object.setProperty(entry.getKey(), entry.getValue());
    }
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() {
          super.undo();
          for (Map.Entry<String, Object> entry : before.entrySet()) {
            object.setProperty(entry.getKey(), entry.getValue());
          }
        }

        @Override
        public void redo() {
          super.redo();
          for (Map.Entry<String, Object> entry : after.entrySet()) {
            object.setProperty(entry.getKey(), entry.getValue());
          }
        }

        @Override
        public String getPresentationName() {
          return "AI assistant edit";
        }
      });
  }

  // ---- Walls painted from their rooms ----

  /**
   * A color or a texture.
   */
  private static final class Finish {
    private final Integer     color;
    private final HomeTexture texture;

    Finish(Integer color, HomeTexture texture) {
      this.color = color;
      this.texture = texture;
    }

    String describe() {
      return this.texture != null ? "the texture \"" + this.texture.getName() + "\"" : color(this.color);
    }
  }

  /**
   * Returns the finish described by the keys <code>color</code>, <code>texture</code>,
   * <code>category</code> and <code>catalog_id</code> of <code>parameters</code>, or <code>null</code>
   * if it has none.
   * @throws IOException if the texture isn't found or the color can't be read
   */
  private Finish getFinish(Map<?, ?> parameters, String action, List<String> notes) throws IOException {
    if (parameters.get("texture") != null || parameters.get("catalog_id") != null) {
      String [] ambiguity = new String [1];
      CatalogTexture catalogTexture = this.executor.getBuildingCommands().findTexture(
          parameters.get("texture") != null ? parameters.get("texture").toString() : null,
          parameters.get("category") != null ? parameters.get("category").toString() : null,
          parameters.get("catalog_id") != null ? parameters.get("catalog_id").toString() : null,
          action, notes, ambiguity);
      if (catalogTexture == null) {
        throw new IOException("see above");
      }
      if (ambiguity [0] != null && !notes.contains(ambiguity [0])) {
        notes.add(ambiguity [0]);
      }
      return new Finish(null, AssistantBuildingCommands.createTexture(catalogTexture,
          AssistantBuildingCommands.getTexturePlacement(parameters)));
    } else if (parameters.get("color") != null) {
      Integer color = AssistantCommandExecutor.parseColor(parameters.get("color").toString());
      if (color == null) {
        throw new IOException("\"" + parameters.get("color") + "\" isn't a color: give \"#RRGGBB\"");
      }
      return new Finish(color, null);
    }
    return null;
  }

  /**
   * Returns the finish stored in the properties of <code>room</code>, or <code>null</code>.
   */
  private Finish getRoomFinish(Room room, List<String> notes) {
    String texture = room.getProperty(ROOM_WALL_TEXTURE_PROPERTY);
    if (texture != null && texture.length() > 0) {
      CatalogTexture catalogTexture = this.executor.getBuildingCommands().findTexture(texture, null, null,
          "paint_rooms", new ArrayList<String>(), new String [1]);
      if (catalogTexture != null) {
        return new Finish(null, new HomeTexture(catalogTexture));
      } else if (notes != null) {
        notes.add("The texture \"" + texture + "\" of the walls of " + describeRoom(room) + " isn't in the textures catalog");
      }
    }
    Integer color = room.getProperty(ROOM_WALL_COLOR_PROPERTY) != null
        ? WallFinishBands.parseColor(room.getProperty(ROOM_WALL_COLOR_PROPERTY)) : null;
    return color != null ? new Finish(color, null) : null;
  }

  private String describeRoom(Room room) {
    String id = this.executor.getId(room);
    return (id != null ? id : "a room") + (room.getName() != null && room.getName().length() > 0
        ? " \"" + room.getName() + "\"" : "");
  }

  /**
   * Returns the points of a side of <code>wall</code> from its start to its end.
   */
  private static float [][] getSidePoints(Wall wall, boolean leftSide) {
    float [][] points = wall.getPoints();
    int count = points.length / 2;
    float [][] side = new float [count][];
    for (int i = 0; i < count; i++) {
      side [i] = leftSide ? points [i] : points [points.length - 1 - i];
    }
    return side;
  }

  /**
   * Returns the point at the share <code>t</code> of the length of a line of points.
   */
  private static float [] getPointAlong(float [][] points, double t) {
    double length = 0;
    for (int i = 1; i < points.length; i++) {
      length += Math.hypot(points [i][0] - points [i - 1][0], points [i][1] - points [i - 1][1]);
    }
    double distance = t * length;
    for (int i = 1; i < points.length; i++) {
      double segment = Math.hypot(points [i][0] - points [i - 1][0], points [i][1] - points [i - 1][1]);
      if (distance <= segment || i == points.length - 1) {
        double share = segment > 0 ? Math.min(1, distance / segment) : 0;
        return new float [] {(float)(points [i - 1][0] + (points [i][0] - points [i - 1][0]) * share),
                             (float)(points [i - 1][1] + (points [i][1] - points [i - 1][1]) * share)};
      }
      distance -= segment;
    }
    return points [0];
  }

  /**
   * Returns the points 8 cm in front of a side of <code>wall</code>, at 9 places along it.
   */
  static float [][] getPointsInFrontOfSide(Wall wall, boolean leftSide) {
    float [][] side = getSidePoints(wall, leftSide);
    float [][] otherSide = getSidePoints(wall, !leftSide);
    float [][] samples = new float [9][];
    for (int i = 0; i < samples.length; i++) {
      double t = (i + 1) / 10.;
      float [] point = getPointAlong(side, t);
      float [] opposite = getPointAlong(otherSide, t);
      double dx = point [0] - opposite [0];
      double dy = point [1] - opposite [1];
      double length = Math.hypot(dx, dy);
      samples [i] = length > 1E-3
          ? new float [] {(float)(point [0] + dx / length * 8), (float)(point [1] + dy / length * 8)}
          : point;
    }
    return samples;
  }

  private static float getElevation(Level level) {
    return level != null ? level.getElevation() : 0;
  }

  /**
   * Returns <code>true</code> if <code>room</code>, which stands at a lower level than
   * <code>level</code>, is open up to that level: its ceiling is higher than the floor of
   * that level, or it has no ceiling and nothing else is known.
   */
  private boolean isOpenUpTo(Room room, Level level) {
    float roomElevation = getElevation(room.getLevel());
    float elevation = getElevation(level);
    if (roomElevation >= elevation - 1) {
      return false;
    }
    String ceilingHeight = room.getProperty(ROOM_CEILING_HEIGHT_PROPERTY);
    if (ceilingHeight != null) {
      try {
        return roomElevation + Float.parseFloat(ceilingHeight) > elevation + 10;
      } catch (NumberFormatException ex) {
        // As a room without that property
      }
    }
    if (room.isCeilingVisible()) {
      return false;
    }
    // The level just above the one of the room
    for (Level other : this.home.getLevels()) {
      if (other.getElevation() > roomElevation + 1 && other.getElevation() < elevation - 1) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the rooms faced by a side of <code>wall</code> with the count of the places along
   * the side where each one is, the room faced the most coming first. A side faces the rooms
   * of its level, or the rooms of a lower level which are open up to it where its own level has none.
   */
  Map<Room, Integer> getFacedRooms(Wall wall, boolean leftSide) {
    final Map<Room, Integer> counts = new LinkedHashMap<Room, Integer>();
    Level level = wall.getLevel();
    for (float [] point : getPointsInFrontOfSide(wall, leftSide)) {
      Room faced = null;
      for (Room room : this.home.getRooms()) {
        if (room.getLevel() == level && room.containsPoint(point [0], point [1], 0)) {
          faced = room;
          break;
        }
      }
      if (faced == null && level != null) {
        for (Room room : this.home.getRooms()) {
          if (room.getLevel() != level && room.containsPoint(point [0], point [1], 0)
              && isOpenUpTo(room, level)
              && (faced == null || getElevation(room.getLevel()) > getElevation(faced.getLevel()))) {
            faced = room;
          }
        }
      }
      if (faced != null) {
        Integer count = counts.get(faced);
        counts.put(faced, Integer.valueOf(count != null ? count.intValue() + 1 : 1));
      }
    }
    List<Room> rooms = new ArrayList<Room>(counts.keySet());
    Collections.sort(rooms, new java.util.Comparator<Room>() {
        public int compare(Room room1, Room room2) {
          return counts.get(room2).intValue() - counts.get(room1).intValue();
        }
      });
    Map<Room, Integer> sorted = new LinkedHashMap<Room, Integer>();
    for (Room room : rooms) {
      sorted.put(room, counts.get(room));
    }
    return sorted;
  }

  /**
   * Returns the room that a side of <code>wall</code> faces over a third of its length
   * at least, or <code>null</code> if it faces none.
   */
  Room getFacedRoom(Wall wall, boolean leftSide) {
    Map<Room, Integer> rooms = getFacedRooms(wall, leftSide);
    if (!rooms.isEmpty()) {
      Map.Entry<Room, Integer> first = rooms.entrySet().iterator().next();
      if (first.getValue().intValue() >= 3) {
        return first.getKey();
      }
    }
    return null;
  }

  private static boolean isPainted(Wall wall, boolean leftSide) {
    return leftSide
        ? wall.getLeftSideColor() != null || wall.getLeftSideTexture() != null
        : wall.getRightSideColor() != null || wall.getRightSideTexture() != null;
  }

  private static void paint(Wall wall, boolean leftSide, Finish finish) {
    if (leftSide) {
      wall.setLeftSideColor(finish.color);
      wall.setLeftSideTexture(finish.texture);
    } else {
      wall.setRightSideColor(finish.color);
      wall.setRightSideTexture(finish.texture);
    }
  }

  /**
   * Returns the rooms designated by the parameters of a command, or all the rooms
   * of the home if it designates none.
   */
  private List<Room> getRooms(AssistantCommand command, String action, List<String> notes) throws IOException {
    List<Room> rooms = new ArrayList<Room>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Room && !rooms.contains(item)) {
        rooms.add((Room)item);
      }
    }
    List<String> tokens = new ArrayList<String>(command.getStringList("rooms"));
    if (command.getString("room") != null) {
      tokens.add(command.getString("room"));
    }
    for (String token : tokens) {
      token = token.trim();
      boolean found = false;
      for (Room room : this.home.getRooms()) {
        if (token.equalsIgnoreCase(this.executor.getId(room)) || token.equalsIgnoreCase(room.getName())) {
          found = true;
          if (!rooms.contains(room)) {
            rooms.add(room);
          }
        }
      }
      if (!found) {
        throw new IOException("no room has the id or the name \"" + token + "\"");
      }
    }
    if (rooms.isEmpty()) {
      if (!tokens.isEmpty() || command.has("id") || command.has("ids") || command.has("target")) {
        throw new IOException("the ids given aren't rooms");
      }
      if (command.getString("level") != null) {
        Level level = this.executor.getStructureCommands().getLevel(command, action, notes, "level");
        if (level == null) {
          throw new IOException("see above");
        }
        for (Room room : this.home.getRooms()) {
          if (room.getLevel() == level) {
            rooms.add(room);
          }
        }
      } else {
        rooms.addAll(this.home.getRooms());
      }
    }
    return rooms;
  }

  private void paintRooms(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<Room> rooms = getRooms(command, "paint_rooms", notes);
    String only = command.getString("only") != null ? command.getString("only").trim().toLowerCase(Locale.ENGLISH) : "unpainted";
    if (!"unpainted".equals(only) && !"all".equals(only)) {
      throw new IOException("\"only\" is \"unpainted\" (the default: the sides without color nor texture) or \"all\"");
    }
    // The finish of the rooms that have none of their own
    Finish defaultFinish = getFinish(command.getParameters(), "paint_rooms", notes);
    Map<Room, Finish> finishes = new LinkedHashMap<Room, Finish>();
    List<String> roomsWithoutFinish = new ArrayList<String>();
    for (Room room : rooms) {
      Finish finish = getRoomFinish(room, notes);
      if (finish == null) {
        finish = defaultFinish;
      }
      if (finish != null) {
        finishes.put(room, finish);
      } else {
        roomsWithoutFinish.add(describeRoom(room));
      }
    }
    paintSides(rooms, finishes, "all".equals(only), tally, notes, true);
    if (!roomsWithoutFinish.isEmpty()) {
      notes.add("These rooms have no wall color, and their walls weren't painted: " + list(roomsWithoutFinish, 20)
          + ". Give them one with modify_room {\"wall_color\":\"#RRGGBB\"} or \"wall_texture\", or give paint_rooms "
          + "a \"color\" or a \"texture\" for the rooms that have none");
    }
    warnUnknownKeys("paint_rooms", command.getParameters(), PAINT_KEYS, notes);
  }

  private static String list(List<String> items, int limit) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < items.size() && i < limit; i++) {
      text.append(i > 0 ? ", " : "").append(items.get(i));
    }
    if (items.size() > limit) {
      text.append(" and ").append(items.size() - limit).append(" more");
    }
    return text.toString();
  }

  /**
   * Gives the sides of the walls that face <code>rooms</code> the finish of their room.
   * @param all      <code>false</code> to leave the sides which have a color or a texture
   * @param complete <code>true</code> to tell the sides that face no room
   * @return the count of sides painted
   */
  private int paintSides(List<Room> rooms, final Map<Room, Finish> finishes, boolean all, int [] tally,
                         List<String> notes, boolean complete) {
    final List<Object []> sides = new ArrayList<Object []>();
    List<Selectable> walls = new ArrayList<Selectable>();
    Map<Room, Integer> counts = new LinkedHashMap<Room, Integer>();
    List<String> noRoom = new ArrayList<String>();
    List<String> shared = new ArrayList<String>();
    int alreadyPainted = 0;
    int repainted = 0;
    for (Wall wall : this.home.getWalls()) {
      for (int side = 0; side < 2; side++) {
        boolean leftSide = side == 0;
        Map<Room, Integer> faced = getFacedRooms(wall, leftSide);
        Room room = getFacedRoom(wall, leftSide);
        String sideName = this.executor.getId(wall) + (leftSide ? " left" : " right");
        if (room == null) {
          noRoom.add(sideName);
        } else if (finishes.containsKey(room)) {
          if (!all && isPainted(wall, leftSide)) {
            alreadyPainted++;
          } else {
            if (isPainted(wall, leftSide)) {
              repainted++;
            }
            sides.add(new Object [] {wall, Boolean.valueOf(leftSide), finishes.get(room)});
            if (!walls.contains(wall)) {
              walls.add(wall);
            }
            Integer count = counts.get(room);
            counts.put(room, Integer.valueOf(count != null ? count.intValue() + 1 : 1));
            if (faced.size() > 1) {
              StringBuilder others = new StringBuilder();
              for (Room other : faced.keySet()) {
                if (other != room) {
                  others.append(others.length() > 0 ? ", " : "").append(describeRoom(other));
                }
              }
              shared.add(sideName + " (painted as " + describeRoom(room) + ", also faces " + others + ")");
            }
          }
        }
      }
    }
    if (!sides.isEmpty()) {
      this.executor.postModification(walls, new Runnable() {
          public void run() {
            for (Object [] side : sides) {
              paint((Wall)side [0], ((Boolean)side [1]).booleanValue(), (Finish)side [2]);
            }
          }
        });
      tally [4] += walls.size();
    }
    StringBuilder note = new StringBuilder("Painted " + sides.size() + (sides.size() == 1 ? " wall side" : " wall sides")
        + " from the room each one faces");
    if (!counts.isEmpty()) {
      note.append(": ");
      int index = 0;
      for (Map.Entry<Room, Integer> entry : counts.entrySet()) {
        if (index++ == 12) {
          note.append(" and ").append(counts.size() - 12).append(" more rooms");
          break;
        }
        note.append(index > 1 ? ", " : "").append(entry.getValue()).append(" as ").append(describeRoom(entry.getKey()))
            .append(" (").append(finishes.get(entry.getKey()).describe()).append(")");
      }
    }
    if (alreadyPainted > 0) {
      note.append(". ").append(alreadyPainted).append(alreadyPainted == 1 ? " side that had" : " sides that had")
          .append(" a color or a texture kept it (\"only\":\"all\" paints them too)");
    }
    if (repainted > 0) {
      note.append(". ").append(repainted).append(repainted == 1 ? " of them had" : " of them had")
          .append(" a color or a texture of ").append(repainted == 1 ? "its" : "their")
          .append(" own, now replaced (\"only\":\"unpainted\" keeps such sides)");
    }
    notes.add(note.toString());
    if (!shared.isEmpty()) {
      notes.add("A wall side has one finish for its whole length. These sides run along several rooms: " + list(shared, 12));
    }
    if (complete && !noRoom.isEmpty()) {
      notes.add(noRoom.size() + (noRoom.size() == 1 ? " side faces" : " sides face")
          + " no room (the outside, or a space without a room) and weren't painted: " + list(noRoom, 40));
    }
    return sides.size();
  }

  /**
   * Handles the parameters <code>wall_color</code> and <code>wall_texture</code> of <code>modify_room</code>:
   * they are stored in the rooms, and the wall sides that face these rooms get that finish.
   * @return <code>true</code> if the command had one of these parameters
   */
  boolean modifyRoomFinish(AssistantCommand command, List<Selectable> rooms, int [] tally, List<String> notes) {
    if (!command.has("wall_color") && !command.has("wall_texture")) {
      return false;
    }
    try {
      Map<String, Object> parameters = new LinkedHashMap<String, Object>();
      String color = command.getString("wall_color");
      String texture = command.getString("wall_texture");
      boolean remove = (color == null || isNone(color)) && (texture == null || isNone(texture));
      Finish finish = null;
      if (!remove) {
        if (texture != null && !isNone(texture)) {
          parameters.put("texture", texture);
          parameters.put("category", command.getString("wall_texture_category"));
        } else {
          parameters.put("color", color);
        }
        finish = getFinish(parameters, "modify_room", notes);
      }
      List<Room> changedRooms = new ArrayList<Room>();
      Map<Room, Finish> finishes = new LinkedHashMap<Room, Finish>();
      for (Selectable item : rooms) {
        Room room = (Room)item;
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put(ROOM_WALL_COLOR_PROPERTY, finish != null && finish.color != null
            ? String.format("%06X", finish.color.intValue() & 0xFFFFFF) : null);
        properties.put(ROOM_WALL_TEXTURE_PROPERTY, finish != null && finish.texture != null
            ? finish.texture.getName() : null);
        setProperties(room, properties);
        changedRooms.add(room);
        if (finish != null) {
          finishes.put(room, finish);
        }
      }
      if (finish == null) {
        notes.add("Removed the wall color of " + changedRooms.size() + (changedRooms.size() == 1 ? " room" : " rooms")
            + ": the walls keep the finish they have");
      } else if (command.getBoolean("paint_walls", true)) {
        // Every side that faces the room by default, or only the ones without color nor texture
        String only = command.getString("only") != null ? command.getString("only").trim().toLowerCase(Locale.ENGLISH) : "all";
        if (!"unpainted".equals(only) && !"all".equals(only)) {
          notes.add("modify_room: \"only\" is \"all\" (the default: every wall side that faces the room is painted) or "
              + "\"unpainted\" (the sides without color nor texture); no wall was painted");
        } else {
          paintSides(changedRooms, finishes, "all".equals(only), tally, notes, false);
        }
      } else {
        notes.add("Stored " + finish.describe() + " as the wall finish of " + changedRooms.size()
            + (changedRooms.size() == 1 ? " room" : " rooms") + ": paint_rooms gives it to the walls");
      }
    } catch (IOException ex) {
      if (!"see above".equals(ex.getMessage())) {
        notes.add("Ignored the wall color of modify_room (" + ex.getMessage() + ")");
      }
    }
    return true;
  }

  private static boolean isNone(String text) {
    String value = text.trim().toLowerCase(Locale.ENGLISH);
    return value.length() == 0 || "none".equals(value) || "false".equals(value) || "null".equals(value);
  }

  // ---- Finishes that change with the height ----

  /**
   * Handles the parameters <code>left_finish</code>, <code>right_finish</code> and <code>openings</code>
   * of <code>modify_wall</code>.
   * @return <code>true</code> if the command had one of these parameters
   */
  boolean modifyWalls(AssistantCommand command, List<Selectable> walls, int [] tally, List<String> notes) {
    boolean handled = false;
    try {
      for (int side = 0; side < 2; side++) {
        String parameter = side == 0 ? "left_finish" : "right_finish";
        if (command.has(parameter)) {
          handled = true;
          setFinishBands(command.getParameters().get(parameter), parameter, side == 0, walls, tally, notes);
        }
      }
      if (command.has("openings")) {
        handled = true;
        setOpenings(command.getParameters().get("openings"), walls, tally, notes);
      }
    } catch (IOException ex) {
      if (!"see above".equals(ex.getMessage())) {
        notes.add("Ignored a part of modify_wall (" + ex.getMessage() + ")");
      }
    }
    return handled;
  }

  private void setFinishBands(Object value, String parameter, final boolean leftSide, List<Selectable> walls,
                              int [] tally, List<String> notes) throws IOException {
    List<Map<?, ?>> items = new ArrayList<Map<?, ?>>();
    if (value instanceof List) {
      for (Object item : (List<?>)value) {
        if (!(item instanceof Map)) {
          throw new IOException("\"" + parameter + "\" lists finishes from the floor up, like [{\"to\":274,\"color\":\"#D5AC6B\"},"
              + "{\"texture\":\"<name>\"}], the last one without \"to\" being the finish up to the top");
        }
        items.add((Map<?, ?>)item);
      }
    } else if (value instanceof Map) {
      items.add((Map<?, ?>)value);
    } else if (value != null && !Boolean.FALSE.equals(value) && !isNone(value.toString())) {
      throw new IOException("\"" + parameter + "\" lists finishes from the floor up, like [{\"to\":274,\"color\":\"#D5AC6B\"},"
          + "{\"texture\":\"<name>\"}], or is false to remove them");
    }
    // The finishes, with the one of the top of the side
    List<Object []> bands = new ArrayList<Object []>();
    Finish topFinish = null;
    List<String> unknown = new ArrayList<String>();
    for (int i = 0; i < items.size(); i++) {
      Map<?, ?> item = items.get(i);
      for (Object key : item.keySet()) {
        if (!BAND_KEYS.contains(key) && !unknown.contains(String.valueOf(key))) {
          unknown.add(String.valueOf(key));
        }
      }
      Finish finish = getFinish(item, "modify_wall", notes);
      if (finish == null) {
        throw new IOException("each finish of \"" + parameter + "\" needs a \"color\" or a \"texture\"");
      }
      if (item.get("to") == null) {
        if (i != items.size() - 1) {
          throw new IOException("only the last finish of \"" + parameter + "\", the one up to the top of the wall, has no \"to\"");
        }
        topFinish = finish;
      } else {
        bands.add(new Object [] {item, finish});
      }
    }
    if (bands.size() > WallFinishBands.MAX_BANDS) {
      throw new IOException("a wall side has " + WallFinishBands.MAX_BANDS + " finishes at most under the one of its top");
    }
    if (!unknown.isEmpty()) {
      notes.add("The finishes of \"" + parameter + "\" don't know " + unknown + ", which had no effect: their keys are " + BAND_KEYS);
    }
    List<HomeRoofs.Roof> roofs = null;
    int changed = 0;
    for (Selectable selectable : walls) {
      final Wall wall = (Wall)selectable;
      float wallHeight = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
      List<Object> descriptions = new ArrayList<Object>();
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      float previousTop = 0;
      float previousTopAtEnd = 0;
      List<String> texts = new ArrayList<String>();
      for (int i = 0; i < bands.size(); i++) {
        Map<?, ?> item = (Map<?, ?>)bands.get(i) [0];
        Finish finish = (Finish)bands.get(i) [1];
        float top;
        float topAtEnd;
        String origin = "";
        if ("roof".equalsIgnoreCase(String.valueOf(item.get("to")))) {
          if (roofs == null) {
            roofs = HomeRoofs.get(this.home);
          }
          float [] heights = getRoofLine(wall, leftSide, roofs,
              item.get("roof") != null ? item.get("roof").toString() : null);
          top = heights [0];
          topAtEnd = heights [1];
          origin = " under the roof " + this.executor.getId(roofs.get((int)heights [2]).piece);
        } else if (item.get("to") instanceof Number) {
          top = ((Number)item.get("to")).floatValue();
          topAtEnd = item.get("to_at_end") instanceof Number ? ((Number)item.get("to_at_end")).floatValue() : top;
        } else {
          throw new IOException("\"to\" is a height in cm above the floor of the wall, or \"roof\" for the line "
              + "where a lower roof meets the wall");
        }
        if (!(top > 0) || !(topAtEnd > 0) || !AssistantCommandExecutor.sizeSane(top) || !AssistantCommandExecutor.sizeSane(topAtEnd)) {
          throw new IOException("the heights of the finishes must be positive");
        }
        if (top <= previousTop || topAtEnd <= previousTopAtEnd) {
          throw new IOException("the finishes of \"" + parameter + "\" go from the floor up: each \"to\" must be higher than the one before");
        }
        previousTop = top;
        previousTopAtEnd = topAtEnd;
        descriptions.add(WallFinishBands.describe(top, topAtEnd, finish.color, finish.texture));
        properties.put(WallFinishBands.getImageProperty(leftSide, i), finish.texture != null ? finish.texture.getImage() : null);
        texts.add(finish.describe() + " up to " + format(top) + (topAtEnd != top ? " cm at its start and " + format(topAtEnd) + " cm at its end" : " cm")
            + origin + (top > wallHeight ? " (the wall is " + format(wallHeight) + " cm high)" : ""));
      }
      for (int i = bands.size(); i < WallFinishBands.MAX_BANDS; i++) {
        properties.put(WallFinishBands.getImageProperty(leftSide, i), null);
      }
      properties.put(WallFinishBands.getProperty(leftSide),
          descriptions.isEmpty() ? null : AssistantShapeCommands.toJson(descriptions));
      if (topFinish != null) {
        final Finish finish = topFinish;
        this.executor.postModification(Collections.<Selectable>singletonList(wall), new Runnable() {
            public void run() {
              paint(wall, leftSide, finish);
            }
          });
      }
      setProperties(wall, properties);
      changed++;
      notes.add(texts.isEmpty()
          ? "Removed the finishes that changed with the height of the " + (leftSide ? "left" : "right") + " side of "
              + this.executor.getId(wall) + (topFinish != null ? ", which shows " + topFinish.describe() : "")
          : "The " + (leftSide ? "left" : "right") + " side of " + this.executor.getId(wall) + " shows from its floor "
              + list(texts, 10) + ", then " + (topFinish != null ? topFinish.describe()
                  : "its own finish (" + (isPainted(wall, leftSide) ? "kept" : "the default color: give a last finish without \"to\" to set it") + ")")
              + " above");
    }
    tally [4] += changed;
  }

  /**
   * Returns the heights above the floor of <code>wall</code>, at its start and at its end, of a line
   * that stays under the lowest roof standing in front of the given side, and the index of that roof.
   */
  private float [] getRoofLine(Wall wall, boolean leftSide, List<HomeRoofs.Roof> roofs, String roofId) throws IOException {
    float [][] side = getSidePoints(wall, leftSide);
    float [][] otherSide = getSidePoints(wall, !leftSide);
    float floor = getElevation(wall.getLevel());
    int samples = 21;
    int bestRoof = -1;
    double [] bestHeights = null;
    for (int r = 0; r < roofs.size(); r++) {
      HomeRoofs.Roof roof = roofs.get(r);
      if (roofId != null && !roofId.trim().equalsIgnoreCase(this.executor.getId(roof.piece))) {
        continue;
      }
      double [] heights = new double [samples];
      int count = 0;
      for (int i = 0; i < samples; i++) {
        double t = i / (samples - 1.);
        float [] point = getPointAlong(side, t);
        float [] opposite = getPointAlong(otherSide, t);
        double dx = point [0] - opposite [0];
        double dy = point [1] - opposite [1];
        double length = Math.max(1E-3, Math.hypot(dx, dy));
        // The underside of the roof a little in front of the side
        heights [i] = roof.model.getGeometry().getCeilingHeight(point [0] + dx / length * 3, point [1] + dy / length * 3)
            + roof.elevation - floor;
        if (!Double.isNaN(heights [i]) && heights [i] > 0) {
          count++;
        } else {
          heights [i] = Double.NaN;
        }
      }
      if (count >= 2) {
        double lowest = Double.MAX_VALUE;
        for (double height : heights) {
          if (!Double.isNaN(height)) {
            lowest = Math.min(lowest, height);
          }
        }
        if (bestHeights == null || lowest < bestHeights [samples]) {
          bestHeights = Arrays.copyOf(heights, samples + 1);
          bestHeights [samples] = lowest;
          bestRoof = r;
        }
      }
    }
    if (bestHeights == null) {
      throw new IOException("no roof" + (roofId != null ? " " + roofId : "") + " stands in front of the "
          + (leftSide ? "left" : "right") + " side of " + this.executor.getId(wall) + ": give \"to\" as a height");
    }
    // The line through the heights at both ends of the part under the roof, lowered to stay under the roof
    int first = 0;
    int last = samples - 1;
    while (Double.isNaN(bestHeights [first])) {
      first++;
    }
    while (Double.isNaN(bestHeights [last])) {
      last--;
    }
    double slope = last > first ? (bestHeights [last] - bestHeights [first]) / (last - first) : 0;
    double start = bestHeights [first] - slope * first;
    double shift = 0;
    for (int i = first; i <= last; i++) {
      if (!Double.isNaN(bestHeights [i])) {
        shift = Math.min(shift, bestHeights [i] - (start + slope * i));
      }
    }
    float top = (float)(start + shift);
    float topAtEnd = (float)(start + shift + slope * (samples - 1));
    if (!(top > 1) || !(topAtEnd > 1)) {
      // A roof that goes down under the floor before the end of the wall
      top = topAtEnd = (float)Math.max(1, bestHeights [samples]);
    }
    return new float [] {top, topAtEnd, bestRoof};
  }

  /**
   * Returns the description of the finishes that change with the height and of the openings
   * of <code>wall</code>, or an empty text.
   */
  String describeWall(Wall wall) {
    StringBuilder text = new StringBuilder();
    for (int side = 0; side < 2; side++) {
      List<WallFinishBands.Band> bands = WallFinishBands.getBands(wall, side == 0);
      if (!bands.isEmpty()) {
        text.append(", ").append(side == 0 ? "left" : "right").append(" side finishes from the floor: ");
        for (int i = 0; i < bands.size(); i++) {
          WallFinishBands.Band band = bands.get(i);
          text.append(i > 0 ? ", " : "")
              .append(band.getTexture() != null ? "texture \"" + band.getTexture().getName() + "\"" : "color " + color(band.getColor()))
              .append(" up to ").append(format(band.getTop()));
          if (band.getTopAtEnd() != band.getTop()) {
            text.append(" at start and ").append(format(band.getTopAtEnd())).append(" at end");
          }
        }
        text.append(", then the finish of the side above");
      }
    }
    List<HomePieceOfFurniture> openings = getOpenings(wall);
    if (!openings.isEmpty()) {
      text.append(", openings without door: ");
      for (int i = 0; i < openings.size(); i++) {
        HomePieceOfFurniture opening = openings.get(i);
        Map<?, ?> spec = getSpec(opening, OPENING_PROPERTY);
        text.append(i > 0 ? "; " : "").append(this.executor.getId(opening)).append(" from ")
            .append(format(number(spec, "from", 0))).append(" to ").append(format(number(spec, "to", 0)))
            .append(" cm from the start of the wall, ").append(format(opening.getElevation())).append(" to ")
            .append(format(opening.getElevation() + opening.getHeight())).append(" cm above the floor");
      }
    }
    return text.toString();
  }

  // ---- Openings ----

  private static float number(Map<?, ?> map, String key, float defaultValue) {
    Object value = map != null ? map.get(key) : null;
    return value instanceof Number && !Float.isNaN(((Number)value).floatValue())
        ? ((Number)value).floatValue() : defaultValue;
  }

  private static Map<?, ?> getSpec(HomePieceOfFurniture piece, String property) {
    String spec = piece.getProperty(property);
    if (spec != null) {
      try {
        Object parsed = JSONParser.parse(spec);
        if (parsed instanceof Map) {
          return (Map<?, ?>)parsed;
        }
      } catch (RuntimeException ex) {
        // Not a description to read
      }
    }
    return null;
  }

  /**
   * Returns the pieces that open <code>wall</code> without a door or a window.
   */
  List<HomePieceOfFurniture> getOpenings(Wall wall) {
    List<HomePieceOfFurniture> openings = new ArrayList<HomePieceOfFurniture>();
    String id = this.executor.getId(wall);
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      Map<?, ?> spec = getSpec(piece, OPENING_PROPERTY);
      if (spec != null && id != null && id.equals(spec.get("wall"))) {
        openings.add(piece);
      }
    }
    return openings;
  }

  private Wall getWall(String token, String action) throws IOException {
    if (token == null || token.trim().length() == 0) {
      throw new IOException("give the wall with \"wall\":\"W#\"");
    }
    for (Wall wall : this.home.getWalls()) {
      if (token.trim().equalsIgnoreCase(this.executor.getId(wall))) {
        if (wall.getArcExtent() != null && wall.getArcExtent().floatValue() != 0) {
          throw new IOException(action + " works on straight walls, and " + token.trim() + " is round");
        }
        return wall;
      }
    }
    throw new IOException("no wall has the id " + token.trim());
  }

  private float getHeight(Wall wall) {
    float height = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
    return wall.getHeightAtEnd() != null ? Math.max(height, wall.getHeightAtEnd()) : height;
  }

  private float getCeilingHeight(Level level) {
    return level != null ? level.getHeight() : this.home.getWallHeight();
  }

  /**
   * Returns the place {from, to, bottom, top} of an opening described by <code>parameters</code>.
   */
  private float [] getOpeningPlace(Map<?, ?> parameters, Wall wall) throws IOException {
    float length = wall.getLength();
    float from = number(parameters, "from", Float.NaN);
    float to = number(parameters, "to", Float.NaN);
    if (Float.isNaN(from) || Float.isNaN(to)) {
      float center = number(parameters, "center", Float.NaN);
      float width = number(parameters, "width", Float.NaN);
      if (Float.isNaN(center) || Float.isNaN(width)) {
        throw new IOException("give \"from\" and \"to\", or \"center\" and \"width\", in cm from the start of the wall "
            + "(its point x1,y1; the wall is " + format(length) + " cm long)");
      }
      from = center - width / 2;
      to = center + width / 2;
    }
    if (from > to) {
      float swap = from;
      from = to;
      to = swap;
    }
    if (from < -0.5f || to > length + 0.5f || to - from < 5) {
      throw new IOException("the opening must be 5 cm wide at least and stay between 0 and " + format(length)
          + " cm, the length of the wall measured from its start");
    }
    float wallHeight = getHeight(wall);
    float bottom = number(parameters, "bottom", 0);
    float top = parameters.get("height") instanceof Number
        ? bottom + ((Number)parameters.get("height")).floatValue()
        : number(parameters, "top", Math.min(210, wallHeight));
    if (bottom < 0 || top - bottom < 5 || top > wallHeight + 0.5f) {
      throw new IOException("\"bottom\" and \"top\" are heights above the floor, between 0 and " + format(wallHeight)
          + " cm, the height of the wall, with 5 cm at least between them");
    }
    return new float [] {Math.max(0, from), Math.min(length, to), bottom, Math.min(top, wallHeight)};
  }

  /**
   * Returns the piece that opens <code>wall</code> at the given place: a door without leaf nor frame,
   * which shows a thin lining around the hole it cuts in the wall.
   */
  private HomeDoorOrWindow createOpening(Wall wall, float [] place, Map<?, ?> parameters) throws IOException {
    float width = place [1] - place [0];
    float height = place [3] - place [2];
    float depth = wall.getThickness() + 0.6f;
    float lining = Math.max(0.2f, Math.min(10, number(parameters, "lining", 1)));
    lining = Math.min(lining, Math.min(width, height) / 4);
    Object colorValue = parameters.get("lining_color") != null ? parameters.get("lining_color") : parameters.get("color");
    Integer color = colorValue != null ? AssistantCommandExecutor.parseColor(colorValue.toString()) : null;
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material material = mesh.getMaterial(color != null ? color.intValue() : DEFAULT_TRIM_COLOR);
    // The jambs, the head and the sill of the opening
    mesh.addBox(-width / 2, 0, -depth / 2, -width / 2 + lining, height, depth / 2, material);
    mesh.addBox(width / 2 - lining, 0, -depth / 2, width / 2, height, depth / 2, material);
    mesh.addBox(-width / 2 + lining, height - lining, -depth / 2, width / 2 - lining, height, depth / 2, material);
    if (place [2] > 0.5f) {
      mesh.addBox(-width / 2 + lining, 0, -depth / 2, width / 2 - lining, lining, depth / 2, material);
    }
    AssistantGeneratedModel model = mesh.store("opening");
    String name = parameters.get("name") != null && parameters.get("name").toString().trim().length() > 0
        ? parameters.get("name").toString().trim() : "Opening";
    CatalogDoorOrWindow catalogPiece = new CatalogDoorOrWindow(null, name, null, null, null, null, null,
        model.getIcon(), model.getPlanIcon(), model.getModel(), width, depth, height, place [2], 1f, true,
        CUT_OUT_SHAPE, 1, 0, new Sash [0], null, false, null, true, true, false, null, null, null);
    HomeDoorOrWindow opening = new HomeDoorOrWindow(catalogPiece);
    placeOpening(opening, wall, place);
    opening.setLevel(wall.getLevel());
    opening.setBoundToWall(true);
    Map<String, Object> spec = new LinkedHashMap<String, Object>();
    spec.put("wall", this.executor.getId(wall));
    spec.put("from", Double.valueOf(place [0]));
    spec.put("to", Double.valueOf(place [1]));
    opening.setProperty(OPENING_PROPERTY, AssistantShapeCommands.toJson(spec));
    opening.setProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY, "opening");
    return opening;
  }

  /**
   * Puts a piece in <code>wall</code> between the distances <code>place [0]</code>
   * and <code>place [1]</code> from its start.
   */
  private static void placeOpening(HomePieceOfFurniture piece, Wall wall, float [] place) {
    double length = Math.max(1E-3, wall.getLength());
    double ux = (wall.getXEnd() - wall.getXStart()) / length;
    double uy = (wall.getYEnd() - wall.getYStart()) / length;
    double center = (place [0] + place [1]) / 2;
    piece.setX((float)(wall.getXStart() + ux * center));
    piece.setY((float)(wall.getYStart() + uy * center));
    piece.setAngle((float)Math.atan2(uy, ux));
  }

  private void addOpening(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Wall wall = getWall(command.getString("wall") != null ? command.getString("wall") : command.getString("id"), "add_opening");
    float [] place = getOpeningPlace(command.getParameters(), wall);
    HomeDoorOrWindow opening = createOpening(wall, place, command.getParameters());
    checkOverlap(wall, opening, null, notes);
    this.executor.getShapeCommands().add(opening);
    tally [1]++;
    notes.add("Opened " + this.executor.getId(wall) + " without door nor window: " + describeOpening(opening, place)
        + ". It's the piece " + this.executor.getId(opening) + " \"" + opening.getName() + "\": delete removes the opening, "
        + "resize and move change it, and add_header puts a beam over it");
    warnUnknownKeys("add_opening", command.getParameters(), OPENING_KEYS, notes);
  }

  private String describeOpening(HomePieceOfFurniture opening, float [] place) {
    return format(place [1] - place [0]) + " cm wide from " + format(place [0]) + " to " + format(place [1])
        + " cm from the start of the wall, from " + format(place [2]) + " to " + format(place [3]) + " cm above the floor";
  }

  /**
   * Notes the doors, windows and openings of the wall that the new opening overlaps.
   */
  private void checkOverlap(Wall wall, HomePieceOfFurniture opening, List<HomePieceOfFurniture> ignored, List<String> notes) {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.isDoorOrWindow() && piece.getLevel() == wall.getLevel() && piece != opening
          && (ignored == null || !ignored.contains(piece))
          && Math.hypot(piece.getX() - opening.getX(), piece.getY() - opening.getY()) < (piece.getWidth() + opening.getWidth()) / 2 - 1
          && wall.containsPoint(piece.getX(), piece.getY(), 5)
          && piece.getElevation() < opening.getElevation() + opening.getHeight()
          && opening.getElevation() < piece.getElevation() + piece.getHeight()) {
        notes.add("The opening overlaps " + this.executor.getId(piece) + " \"" + piece.getName() + "\" in the same wall");
      }
    }
  }

  /**
   * Replaces the openings of walls by the ones described by <code>value</code>.
   */
  private void setOpenings(Object value, List<Selectable> walls, int [] tally, List<String> notes) throws IOException {
    if (walls.size() != 1) {
      throw new IOException("\"openings\" describes the openings of one wall, not of " + walls.size());
    }
    Wall wall = getWall(this.executor.getId(walls.get(0)), "modify_wall");
    List<Map<?, ?>> items = new ArrayList<Map<?, ?>>();
    if (value instanceof List) {
      for (Object item : (List<?>)value) {
        if (!(item instanceof Map)) {
          throw new IOException("\"openings\" lists the openings of the wall, like [{\"from\":120,\"to\":300,\"bottom\":0,\"top\":210}]");
        }
        items.add((Map<?, ?>)item);
      }
    } else if (value != null && !Boolean.FALSE.equals(value) && !isNone(value.toString())) {
      throw new IOException("\"openings\" lists the openings of the wall, like [{\"from\":120,\"to\":300,\"bottom\":0,\"top\":210}], "
          + "or is [] to remove them");
    }
    List<HomeDoorOrWindow> openings = new ArrayList<HomeDoorOrWindow>();
    List<String> unknown = new ArrayList<String>();
    List<String> texts = new ArrayList<String>();
    for (Map<?, ?> item : items) {
      for (Object key : item.keySet()) {
        if (!OPENING_KEYS.contains(key) && !unknown.contains(String.valueOf(key))) {
          unknown.add(String.valueOf(key));
        }
      }
      float [] place = getOpeningPlace(item, wall);
      HomeDoorOrWindow opening = createOpening(wall, place, item);
      openings.add(opening);
      texts.add(describeOpening(opening, place));
    }
    List<HomePieceOfFurniture> previous = getOpenings(wall);
    if (!previous.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().deleteItems(new ArrayList<Selectable>(previous));
      tally [5] += previous.size();
    }
    for (HomeDoorOrWindow opening : openings) {
      checkOverlap(wall, opening, previous, notes);
      this.executor.getShapeCommands().add(opening);
      tally [1]++;
    }
    StringBuilder ids = new StringBuilder();
    for (HomeDoorOrWindow opening : openings) {
      ids.append(ids.length() > 0 ? ", " : "").append(this.executor.getId(opening));
    }
    notes.add(openings.isEmpty()
        ? "Removed the " + previous.size() + " openings without door of " + this.executor.getId(wall)
        : this.executor.getId(wall) + " has " + openings.size() + (openings.size() == 1 ? " opening" : " openings")
            + " without door nor window (" + ids + "): " + list(texts, 10)
            + (previous.isEmpty() ? "" : ". They replace the " + previous.size() + " it had"));
    if (!unknown.isEmpty()) {
      notes.add("The openings of modify_wall don't know " + unknown + ", which had no effect: their keys are "
          + OPENING_KEYS.subList(3, OPENING_KEYS.size()));
    }
  }

  /**
   * Puts the openings and the headers bound to walls back in them, once the walls moved.
   */
  void wallsChanged(List<Selectable> walls, List<String> notes) {
    final List<Object []> places = new ArrayList<Object []>();
    List<Selectable> pieces = new ArrayList<Selectable>();
    List<HomePieceOfFurniture> headers = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : walls) {
      if (!(item instanceof Wall)) {
        continue;
      }
      Wall wall = (Wall)item;
      String id = this.executor.getId(wall);
      for (HomePieceOfFurniture opening : getOpenings(wall)) {
        Map<?, ?> spec = getSpec(opening, OPENING_PROPERTY);
        float [] place = {number(spec, "from", 0), number(spec, "to", opening.getWidth())};
        float length = wall.getLength();
        if (place [1] > length) {
          // Kept in a wall that got shorter
          place [0] = Math.max(0, place [0] - (place [1] - length));
          place [1] = length;
        }
        places.add(new Object [] {opening, wall, place});
        pieces.add(opening);
      }
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        Map<?, ?> spec = getSpec(piece, HEADER_PROPERTY);
        if (spec != null && id != null && id.equals(spec.get("wall")) && !headers.contains(piece)) {
          headers.add(piece);
        }
      }
    }
    if (!places.isEmpty()) {
      this.executor.postModification(pieces, new Runnable() {
          public void run() {
            for (Object [] place : places) {
              HomePieceOfFurniture opening = (HomePieceOfFurniture)place [0];
              Wall wall = (Wall)place [1];
              placeOpening(opening, wall, (float [])place [2]);
              if (Math.abs(opening.getDepth() - (wall.getThickness() + 0.6f)) > 0.01f && opening.isResizable()) {
                opening.setDepth(wall.getThickness() + 0.6f);
              }
            }
          }
        });
    }
    // The headers over the openings of these walls, or along them
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      Map<?, ?> spec = getSpec(piece, HEADER_PROPERTY);
      if (spec != null && spec.get("opening") != null && !headers.contains(piece)) {
        for (Selectable opening : pieces) {
          if (spec.get("opening").equals(this.executor.getId(opening))) {
            headers.add(piece);
          }
        }
      }
    }
    int rebuilt = 0;
    for (HomePieceOfFurniture header : headers) {
      try {
        Map<String, Object> spec = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : getSpec(header, HEADER_PROPERTY).entrySet()) {
          spec.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        AssistantGeneratedModel model = buildHeader(spec, header.getLevel(), new ArrayList<String>(), new String [1]);
        float [] bounds = model.getBounds();
        this.executor.getShapeCommands().replaceModel(header, model, (bounds [0] + bounds [3]) / 2,
            (bounds [2] + bounds [5]) / 2, bounds [1], new LinkedHashMap<String, String>());
        rebuilt++;
      } catch (IOException ex) {
        notes.add("The header " + this.executor.getId(header) + " couldn't follow its wall (" + ex.getMessage() + ")");
      }
    }
    if (!places.isEmpty() || rebuilt > 0) {
      notes.add("Moved with their wall: " + places.size() + (places.size() == 1 ? " opening" : " openings")
          + " and " + rebuilt + (rebuilt == 1 ? " header" : " headers"));
    }
  }

  // ---- Headers and soffits ----

  private AssistantMeshBuilder.Material getMaterial(AssistantMeshBuilder mesh, Map<?, ?> parameters, List<String> notes,
                                                    String action) throws IOException {
    Integer color = parameters.get("color") != null
        ? AssistantCommandExecutor.parseColor(parameters.get("color").toString()) : null;
    if (parameters.get("color") != null && color == null) {
      throw new IOException("\"" + parameters.get("color") + "\" isn't a color: give \"#RRGGBB\"");
    }
    if (parameters.get("texture") != null || parameters.get("catalog_id") != null) {
      String [] ambiguity = new String [1];
      CatalogTexture texture = this.executor.getBuildingCommands().findTexture(
          parameters.get("texture") != null ? parameters.get("texture").toString() : null,
          parameters.get("category") != null ? parameters.get("category").toString() : null,
          parameters.get("catalog_id") != null ? parameters.get("catalog_id").toString() : null,
          action, notes, ambiguity);
      if (texture == null) {
        throw new IOException("see above");
      }
      return mesh.getMaterial(texture, AssistantFurnitureBuilder.getNumber(parameters, "texture_scale", 1),
          (float)Math.toRadians(AssistantFurnitureBuilder.getNumber(parameters, "texture_angle", 0)), 0, 0,
          color != null ? color.intValue() : DEFAULT_TRIM_COLOR);
    }
    return mesh.getMaterial(color != null ? color.intValue() : DEFAULT_TRIM_COLOR);
  }

  private HomePieceOfFurniture getPiece(String token) {
    if (token != null) {
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (token.trim().equalsIgnoreCase(this.executor.getId(piece))) {
          return piece;
        }
      }
    }
    return null;
  }

  /**
   * Returns the model of the header described by <code>spec</code>, built where it stands.
   * @param description receives the text that describes it
   */
  private AssistantGeneratedModel buildHeader(Map<String, Object> spec, Level level, List<String> notes,
                                              String [] description) throws IOException {
    return buildHeader(spec, level, notes, description, false);
  }

  /**
   * Returns the model of a header.
   * @param skipWall <code>true</code> to leave out what a wall of the level already closes along the header:
   *          a beam as thick as the wall that continues above an opening would only double that wall.
   *          The bottom kept is then stored in <code>spec</code>.
   */
  private AssistantGeneratedModel buildHeader(Map<String, Object> spec, Level level, List<String> notes,
                                              String [] description, boolean skipWall) throws IOException {
    double [] start;
    double [] end;
    float thickness = number(spec, "thickness", Float.NaN);
    float bottom = number(spec, "bottom", Float.NaN);
    float bearing = Math.max(0, Math.min(100, number(spec, "bearing", 0)));
    String over;
    if (spec.get("opening") != null) {
      HomePieceOfFurniture opening = getPiece(spec.get("opening").toString());
      if (opening == null) {
        throw new IOException("no piece has the id " + spec.get("opening") + ": \"opening\" is the id of an opening, a door or a window");
      }
      double half = opening.getWidth() / 2 + bearing;
      double ux = Math.cos(opening.getAngle());
      double uy = Math.sin(opening.getAngle());
      start = new double [] {opening.getX() - ux * half, opening.getY() - uy * half};
      end = new double [] {opening.getX() + ux * half, opening.getY() + uy * half};
      if (Float.isNaN(thickness)) {
        thickness = opening.getDepth();
      }
      if (Float.isNaN(bottom)) {
        bottom = opening.getElevation() + opening.getHeight();
      }
      over = "over " + this.executor.getId(opening) + " \"" + opening.getName() + "\"";
    } else if (spec.get("wall") != null) {
      Wall wall = getWall(spec.get("wall").toString(), "add_header");
      float from = number(spec, "from", 0) - bearing;
      float to = number(spec, "to", wall.getLength()) + bearing;
      if (!(to - from >= 5)) {
        throw new IOException("\"from\" and \"to\" are distances in cm from the start of the wall, 5 cm apart at least");
      }
      double length = Math.max(1E-3, wall.getLength());
      double ux = (wall.getXEnd() - wall.getXStart()) / length;
      double uy = (wall.getYEnd() - wall.getYStart()) / length;
      start = new double [] {wall.getXStart() + ux * from, wall.getYStart() + uy * from};
      end = new double [] {wall.getXStart() + ux * to, wall.getYStart() + uy * to};
      if (Float.isNaN(thickness)) {
        thickness = wall.getThickness();
      }
      over = "along " + this.executor.getId(wall) + " from " + format(from) + " to " + format(to) + " cm from its start";
    } else {
      List<float []> points = AssistantFurnitureBuilder.getPoints(spec, "points", 2);
      if (points.size() != 2) {
        throw new IOException("give \"opening\" (the id of an opening, a door or a window), \"wall\" with \"from\" and \"to\", "
            + "or \"points\":[[x1,y1],[x2,y2]], the ends of the beam");
      }
      start = new double [] {points.get(0) [0], points.get(0) [1]};
      end = new double [] {points.get(1) [0], points.get(1) [1]};
      if (!AssistantCommandExecutor.coordinateSane(start [0]) || !AssistantCommandExecutor.coordinateSane(start [1])
          || !AssistantCommandExecutor.coordinateSane(end [0]) || !AssistantCommandExecutor.coordinateSane(end [1])
          || Math.hypot(end [0] - start [0], end [1] - start [1]) < 5) {
        throw new IOException("the ends of the beam are out of range or less than 5 cm apart");
      }
      over = "";
      if (!Boolean.FALSE.equals(spec.get("to_walls"))) {
        // Each end goes on to the wall it nearly reaches
        int reached = (extendToWall(end, start, level) ? 1 : 0) + (extendToWall(start, end, level) ? 1 : 0);
        over = reached == 2 ? "from wall to wall" : reached == 1 ? "with one end at a wall" : "";
      }
      if (Float.isNaN(thickness)) {
        thickness = 12;
      }
    }
    float ceiling = getCeilingHeight(level);
    if (Float.isNaN(bottom)) {
      bottom = Math.min(210, ceiling - 20);
    }
    float top = spec.get("height") instanceof Number
        ? bottom + ((Number)spec.get("height")).floatValue()
        : number(spec, "top", ceiling);
    if (!(thickness >= 0.5f) || thickness > 200 || !AssistantCommandExecutor.sizeSane(bottom) || !AssistantCommandExecutor.sizeSane(top)
        || !(top - bottom >= 1)) {
      throw new IOException("\"bottom\" (" + format(bottom) + ") must be under \"top\" (" + format(top)
          + "), which is the ceiling of the level by default, and \"thickness\" between 0.5 and 200 cm");
    }
    if (skipWall && !Boolean.TRUE.equals(spec.get("force"))) {
      Wall [] closingWall = new Wall [1];
      float closedTo = getWallTopAlong(start, end, level, thickness, closingWall);
      if (closedTo >= top - 1) {
        throw new IOException("the wall " + this.executor.getId(closingWall [0]) + ", " + format(closingWall [0].getThickness())
            + " cm thick, already closes the space " + (over.length() > 0 ? over + " " : "") + "from " + format(bottom) + " to "
            + format(top) + " cm above the floor: a header there would only double it, and nothing was added. "
            + "For a beam that shows, give a \"thickness\" larger than the one of the wall; \"force\":true builds it anyway");
      } else if (closedTo > bottom + 0.5f) {
        notes.add("The wall " + this.executor.getId(closingWall [0]) + " already closes the space up to " + format(closedTo)
            + " cm above the floor: the header starts there rather than at " + format(bottom)
            + " cm (\"force\":true builds all of it)");
        bottom = closedTo;
        spec.put("bottom", Double.valueOf(closedTo));
        spec.remove("height");
        spec.put("top", Double.valueOf(top));
      }
    }
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material material = getMaterial(mesh, spec, notes, "add_header");
    mesh.addBar(new float [] {(float)start [0], top, (float)start [1]}, new float [] {(float)end [0], top, (float)end [1]},
        thickness, top - bottom, material);
    description [0] = format(Math.hypot(end [0] - start [0], end [1] - start [1])) + " cm long, " + format(thickness)
        + " cm thick, from " + format(bottom) + " to " + format(top) + " cm above the floor"
        + (over.length() > 0 ? ", " + over : "");
    return mesh.store("header");
  }

  /**
   * Returns the height above the floor of <code>level</code> up to which a wall of that level, as thick
   * as a header of the given <code>thickness</code> or thicker, stands along the line from <code>start</code>
   * to <code>end</code>, or 0 if no wall runs along it.
   * @param closingWall receives the wall
   */
  private float getWallTopAlong(double [] start, double [] end, Level level, float thickness, Wall [] closingWall) {
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    if (length < 1) {
      return 0;
    }
    double ux = (end [0] - start [0]) / length;
    double uy = (end [1] - start [1]) / length;
    float highest = 0;
    for (Wall wall : this.home.getWalls()) {
      double wallLength = wall.getLength();
      if (wall.getLevel() != level || wallLength < 1 || wall.getArcExtent() != null && wall.getArcExtent().floatValue() != 0
          // A beam thicker than the wall shows on its sides
          || wall.getThickness() < thickness - 1.5f) {
        continue;
      }
      double wx = (wall.getXEnd() - wall.getXStart()) / wallLength;
      double wy = (wall.getYEnd() - wall.getYStart()) / wallLength;
      if (Math.abs(ux * wy - uy * wx) > 0.05) {
        continue;
      }
      // The line of the header in the thickness of the wall, and the wall along all of it but 5 cm at each end
      double across = Math.abs((start [0] - wall.getXStart()) * -wy + (start [1] - wall.getYStart()) * wx);
      double t1 = (start [0] - wall.getXStart()) * wx + (start [1] - wall.getYStart()) * wy;
      double t2 = (end [0] - wall.getXStart()) * wx + (end [1] - wall.getYStart()) * wy;
      if (across > wall.getThickness() / 2 + 1 || Math.min(t1, t2) < -wall.getThickness() / 2 - 5
          || Math.max(t1, t2) > wallLength + wall.getThickness() / 2 + 5) {
        continue;
      }
      float height = wall.getHeight() != null ? wall.getHeight().floatValue() : this.home.getWallHeight();
      if (wall.getHeightAtEnd() != null) {
        height = Math.min(height, wall.getHeightAtEnd().floatValue());
      }
      if (height > highest) {
        highest = height;
        closingWall [0] = wall;
      }
    }
    return highest;
  }

  /**
   * Moves <code>end</code> away from <code>start</code> until the face of a wall of <code>level</code>,
   * if one is within 60 cm, and returns <code>true</code> if the end is at a wall.
   */
  private boolean extendToWall(double [] end, double [] start, Level level) {
    double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
    double ux = (end [0] - start [0]) / length;
    double uy = (end [1] - start [1]) / length;
    for (int distance = 0; distance <= 60; distance++) {
      float x = (float)(end [0] + ux * distance);
      float y = (float)(end [1] + uy * distance);
      for (Wall wall : this.home.getWalls()) {
        if (wall.getLevel() == level && wall.containsPoint(x, y, 0.1f)) {
          end [0] = x;
          end [1] = y;
          return true;
        }
      }
    }
    return false;
  }

  private void addHeader(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Map<String, Object> spec = new LinkedHashMap<String, Object>(command.getParameters());
    spec.remove("action");
    spec.remove("level");
    spec.remove("name");
    Level level = null;
    if (spec.get("opening") != null && getPiece(spec.get("opening").toString()) != null) {
      level = getPiece(spec.get("opening").toString()).getLevel();
    } else if (spec.get("wall") != null) {
      level = getWall(spec.get("wall").toString(), "add_header").getLevel();
    }
    if (level == null || command.getString("level") != null) {
      level = this.executor.getShapeCommands().getLevel(command, "add_header", notes);
    }
    String [] description = new String [1];
    AssistantGeneratedModel model = buildHeader(spec, level, notes, description, true);
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : "Header";
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    HomePieceOfFurniture header = shapeCommands.createPiece(name, model, level, true, "header");
    header.setProperty(HEADER_PROPERTY, AssistantShapeCommands.toJson(spec));
    shapeCommands.add(header);
    tally [0]++;
    notes.add("Added the header " + this.executor.getId(header) + " \"" + name + "\": " + description [0]
        + (spec.get("opening") != null || spec.get("wall") != null
            ? ". It follows its wall when modify_wall moves it" : ""));
    warnUnknownKeys("add_header", command.getParameters(), HEADER_KEYS, notes);
  }

  private void addSoffit(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<float []> points = command.getPoints("points");
    Level level = this.executor.getShapeCommands().getLevel(command, "add_soffit", notes);
    if (points.isEmpty() && command.getString("room") != null) {
      // The whole ceiling of a room
      for (Room room : this.home.getRooms()) {
        if (command.getString("room").trim().equalsIgnoreCase(this.executor.getId(room))
            || command.getString("room").trim().equalsIgnoreCase(room.getName())) {
          points = Arrays.asList(room.getPoints());
          if (command.getString("level") == null) {
            level = room.getLevel();
          }
        }
      }
    }
    if (points.size() < 3 || points.size() > 200) {
      throw new IOException("\"points\" must list the 3 corners at least of the lowered part of the ceiling, "
          + "like [[0,0],[400,0],[400,60],[0,60]], or give \"room\" for the whole ceiling of a room");
    }
    for (float [] point : points) {
      if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
        throw new IOException("points out of range");
      }
    }
    float ceiling = getCeilingHeight(level);
    float bottom = (float)command.getDouble("bottom", Math.min(220, ceiling - 20));
    float top = (float)command.getDouble("top", ceiling);
    if (!AssistantCommandExecutor.sizeSane(bottom) || !AssistantCommandExecutor.sizeSane(top) || !(top - bottom >= 0.5f)) {
      throw new IOException("\"bottom\" (" + format(bottom) + ") must be under \"top\" (" + format(top)
          + "), which is the ceiling of the level by default");
    }
    Area area = AssistantMeshBuilder.getArea(points.toArray(new float [points.size()][]));
    if (area.isEmpty()) {
      throw new IOException("the points enclose no area");
    }
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material material = getMaterial(mesh, command.getParameters(), notes, "add_soffit");
    mesh.addPrism(area, bottom, new float [] {top, 0, 0}, material, material);
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : "Soffit";
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    HomePieceOfFurniture soffit = shapeCommands.createPiece(name, mesh.store("soffit"), level, true, "soffit");
    shapeCommands.add(soffit);
    tally [0]++;
    notes.add("Added the soffit " + this.executor.getId(soffit) + " \"" + name + "\": the ceiling is lowered to "
        + format(bottom) + " cm above the floor there, up to " + format(top) + " cm");
    warnUnknownKeys("add_soffit", command.getParameters(), SOFFIT_KEYS, notes);
  }
}
