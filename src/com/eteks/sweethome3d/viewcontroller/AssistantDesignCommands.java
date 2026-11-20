/*
 * AssistantDesignCommands.java 16 Nov 2026
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * Commands found in comparable interior design programs: replacing a piece
 * of furniture by another one in place, rooms drawn from a shape (L, T, U),
 * and decoration styles applied to rooms in one step.
 * @author Sweet Home 3D
 */
public class AssistantDesignCommands {
  /**
   * A decoration style: the colors of the walls, the floor and the ceiling
   * of a room, and the words that find a floor texture in the catalog.
   */
  public static final class Style {
    private final String name;
    private final String description;
    private final int    wallColor;
    private final int    floorColor;
    private final int    ceilingColor;
    private final String floorTextureQuery;

    Style(String name, String description, int wallColor, int floorColor, int ceilingColor,
          String floorTextureQuery) {
      this.name = name;
      this.description = description;
      this.wallColor = wallColor;
      this.floorColor = floorColor;
      this.ceilingColor = ceilingColor;
      this.floorTextureQuery = floorTextureQuery;
    }

    public String getName() {
      return this.name;
    }

    public String getDescription() {
      return this.description;
    }

    public int getWallColor() {
      return this.wallColor;
    }

    public int getFloorColor() {
      return this.floorColor;
    }

    @Override
    public String toString() {
      return this.name + " - " + this.description;
    }
  }

  private static final Style [] STYLES = {
      new Style("Scandinavian", "white walls, pale wood floor", 0xF5F3EE, 0xD9C3A0, 0xFFFFFF, "light wood parquet"),
      new Style("Modern", "light grey walls, grey floor", 0xE6E6E3, 0xA9A9A4, 0xFFFFFF, "grey tiles concrete"),
      new Style("Industrial", "concrete grey walls, dark floor", 0xB5B2AC, 0x5E5A55, 0xE8E6E1, "concrete"),
      new Style("Traditional", "cream walls, warm wood floor", 0xF1E6D0, 0x9A6B43, 0xFBF7EE, "wood parquet"),
      new Style("Coastal", "pale blue walls, sand floor", 0xDCEAF0, 0xE2D3B5, 0xFFFFFF, "light wood"),
      new Style("Mid-century", "warm white walls, walnut floor", 0xEFE8DA, 0x7A5235, 0xFAF6EC, "dark wood parquet"),
      new Style("Minimalist", "pure white walls, light grey floor", 0xFAFAFA, 0xD4D4D2, 0xFFFFFF, "white tiles"),
      new Style("Dark", "charcoal walls, dark wood floor", 0x3F4347, 0x4A3628, 0xDADADA, "dark wood"),
      new Style("Earthy", "sage green walls, natural wood floor", 0xC9D1BE, 0xB08D62, 0xF7F5EE, "wood parquet"),
      new Style("Mediterranean", "warm white walls, terracotta floor", 0xF6EEDD, 0xB8683F, 0xFFFBF2, "terracotta tiles")};

  /** The shapes of <code>add_room_shape</code>. */
  public static final String [] ROOM_SHAPES = {"rectangle", "L", "T", "U"};

  private final Home                      home;
  private final UserPreferences           preferences;
  private final HomeController            homeController;
  private final AssistantCommandExecutor  executor;
  private final AssistantBuildingCommands buildingCommands;

  AssistantDesignCommands(Home home, UserPreferences preferences, HomeController homeController,
                          AssistantCommandExecutor executor, AssistantBuildingCommands buildingCommands) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.executor = executor;
    this.buildingCommands = buildingCommands;
  }

  /**
   * Returns the decoration styles.
   */
  public static List<Style> getStyles() {
    List<Style> styles = new ArrayList<Style>();
    Collections.addAll(styles, STYLES);
    return styles;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    if ("replace_furniture".equals(action) || "swap_furniture".equals(action)) {
      replaceFurniture(command, tally, notes);
    } else if ("apply_style".equals(action) || "set_style".equals(action)) {
      applyStyle(command, tally, notes);
    } else if ("list_styles".equals(action)) {
      notes.add("Styles: " + describeStyles());
    } else if ("set_unit_prices".equals(action) || "set_budget".equals(action)) {
      int count = 0;
      for (String key : HomeReport.UNIT_PRICE_KEYS) {
        if (command.has(key)) {
          double price = command.getDouble(key, -1);
          HomeReport.setUnitPrice(this.home, key, price >= 0 && price < 1E9
              ? new java.math.BigDecimal(String.valueOf(price)) : null);
          count++;
        }
      }
      if (command.has("currency")) {
        HomeReport.setBudgetCurrency(this.home, command.getString("currency"));
        count++;
      }
      notes.add(count > 0 ? "Unit prices of the budget updated; home_report gives the budget"
          : "Ignored set_unit_prices: give \"paint\", \"flooring\", \"ceiling\" (per m2), \"baseboard\" (per m) or \"currency\"");
    } else if ("export_elevation".equals(action)) {
      exportElevation(command, notes);
    } else if ("add_room_shape".equals(action) || "add_shaped_room".equals(action)) {
      addRoomShape(command, tally, notes);
    } else {
      return false;
    }
    return true;
  }

  private static String describeStyles() {
    StringBuilder text = new StringBuilder();
    for (Style style : STYLES) {
      text.append(text.length() > 0 ? "; " : "").append(style);
    }
    return text.toString();
  }

  // ---- Replacing furniture ----

  /**
   * Replaces pieces of furniture by a piece of the catalog, at the same
   * place, angle, elevation and level, with the same tags and visibility.
   */
  private void replaceFurniture(AssistantCommand command, int [] tally, List<String> notes) {
    String name = command.getString("name");
    if (name == null) {
      name = command.getString("with");
    }
    if (name == null || name.trim().length() == 0) {
      notes.add("Ignored replace_furniture without the \"name\" of a catalog piece");
      return;
    }
    List<HomePieceOfFurniture> replaced = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture && !(item instanceof HomeFurnitureGroup)) {
        replaced.add((HomePieceOfFurniture)item);
      }
    }
    if (replaced.isEmpty()) {
      notes.add("No furniture to replace: give \"ids\" or \"target\":\"selection\" (groups can't be replaced)");
      return;
    }
    boolean doorOrWindow = replaced.get(0).isDoorOrWindow();
    CatalogPieceOfFurniture catalogPiece = this.executor.findCatalogPiece(name, doorOrWindow);
    if (catalogPiece == null) {
      catalogPiece = this.executor.findCatalogPiece(name, !doorOrWindow);
    }
    if (catalogPiece == null) {
      notes.add("No catalog piece is named \"" + name.trim() + "\": use search_catalog to find one");
      return;
    }
    boolean keepSize = command.getBoolean("keep_size", false);
    List<HomePieceOfFurniture> added = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture oldPiece : replaced) {
      HomePieceOfFurniture piece =
          this.homeController.getFurnitureController().createHomePieceOfFurniture(catalogPiece);
      piece.setX(oldPiece.getX());
      piece.setY(oldPiece.getY());
      piece.setAngle(oldPiece.getAngle());
      // A piece that was put on another one or hung on a wall stays at its height
      if (oldPiece.getElevation() != 0 || piece.getElevation() == 0) {
        piece.setElevation(oldPiece.getElevation());
      }
      if (keepSize && piece.isResizable()) {
        piece.setWidth(oldPiece.getWidth());
        piece.setDepth(oldPiece.getDepth());
        piece.setHeight(oldPiece.getHeight());
      }
      piece.setLevel(oldPiece.getLevel());
      piece.setVisible(oldPiece.isVisible());
      // The new piece takes the place of the old one, with its id
      piece.setProperty(HomeItemIds.ID_PROPERTY, oldPiece.getProperty(HomeItemIds.ID_PROPERTY));
      String tags = oldPiece.getProperty(HomeItemTags.TAGS_PROPERTY);
      if (tags != null) {
        piece.setProperty(HomeItemTags.TAGS_PROPERTY, tags);
      }
      added.add(piece);
    }
    this.executor.beginTurnUpdate();
    this.homeController.getPlanController().deleteItems(new ArrayList<Selectable>(replaced));
    this.homeController.getFurnitureController().addFurniture(added);
    tally [4] += added.size();
    notes.add("Replaced " + added.size() + (added.size() == 1 ? " piece" : " pieces") + " by \""
        + catalogPiece.getName() + "\" (" + Math.round(catalogPiece.getWidth()) + " x "
        + Math.round(catalogPiece.getDepth()) + " x " + Math.round(catalogPiece.getHeight()) + " cm)");
  }

  // ---- Styles ----

  static Style findStyle(String name) {
    if (name != null) {
      String key = name.trim().toLowerCase(Locale.ENGLISH).replace('_', '-').replace(' ', '-');
      for (Style style : STYLES) {
        String styleKey = style.name.toLowerCase(Locale.ENGLISH);
        if (styleKey.equals(key) || styleKey.replace("-", "").equals(key.replace("-", ""))) {
          return style;
        }
      }
    }
    return null;
  }

  /**
   * Applies a style to rooms: their floor and ceiling, and the sides of the
   * walls that face them.
   */
  private void applyStyle(AssistantCommand command, int [] tally, List<String> notes) {
    final Style style = findStyle(command.getString("style") != null
        ? command.getString("style") : command.getString("name"));
    if (style == null) {
      notes.add("Unknown style; styles: " + describeStyles());
      return;
    }
    final List<Room> rooms = new ArrayList<Room>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Room) {
        rooms.add((Room)item);
      }
    }
    if (rooms.isEmpty()) {
      for (Room room : this.home.getRooms()) {
        if (room.isAtLevel(this.home.getSelectedLevel())) {
          rooms.add(room);
        }
      }
    }
    if (rooms.isEmpty()) {
      notes.add("No room to apply the style \"" + style.name + "\" to");
      return;
    }
    // The sides of the walls that look into these rooms
    final List<Wall> leftSideWalls = new ArrayList<Wall>();
    final List<Wall> rightSideWalls = new ArrayList<Wall>();
    for (Wall wall : this.home.getWalls()) {
      float [][] points = wall.getPoints();
      if (points.length < 4) {
        continue;
      }
      // The first half of the points runs along the left side, the second half
      // comes back along the right side
      int count = points.length;
      int half = count / 2;
      float [] leftMiddle = getMiddle(points [(half - 1) / 2], points [half / 2]);
      float [] rightMiddle = getMiddle(points [count - 1 - (half - 1) / 2], points [count - 1 - half / 2]);
      float [] leftPoint = getPointBeside(leftMiddle, rightMiddle);
      float [] rightPoint = getPointBeside(rightMiddle, leftMiddle);
      for (Room room : rooms) {
        if (room.getLevel() == wall.getLevel() || wall.isAtLevel(room.getLevel())) {
          if (room.containsPoint(leftPoint [0], leftPoint [1], 0) && !leftSideWalls.contains(wall)) {
            leftSideWalls.add(wall);
          }
          if (room.containsPoint(rightPoint [0], rightPoint [1], 0) && !rightSideWalls.contains(wall)) {
            rightSideWalls.add(wall);
          }
        }
      }
    }
    final boolean floorTextureWanted = command.getBoolean("textures", true);
    CatalogTexture foundTexture = null;
    if (floorTextureWanted) {
      List<CatalogTexture> textures = this.buildingCommands.searchTextures(style.floorTextureQuery, 1);
      if (!textures.isEmpty()) {
        foundTexture = textures.get(0);
      }
    }
    final CatalogTexture floorTexture = foundTexture;
    List<Selectable> modified = new ArrayList<Selectable>(rooms);
    for (Wall wall : leftSideWalls) {
      modified.add(wall);
    }
    for (Wall wall : rightSideWalls) {
      if (!modified.contains(wall)) {
        modified.add(wall);
      }
    }
    this.executor.postModification(modified, new Runnable() {
        public void run() {
          for (Room room : rooms) {
            room.setFloorColor(style.floorColor);
            room.setFloorTexture(floorTexture != null ? new HomeTexture(floorTexture) : null);
            room.setCeilingColor(style.ceilingColor);
            room.setCeilingTexture(null);
          }
          for (Wall wall : leftSideWalls) {
            wall.setLeftSideColor(style.wallColor);
            wall.setLeftSideTexture(null);
          }
          for (Wall wall : rightSideWalls) {
            wall.setRightSideColor(style.wallColor);
            wall.setRightSideTexture(null);
          }
        }
      });
    tally [4] += modified.size();
    notes.add("Applied the style \"" + style.name + "\" (" + style.description + ") to " + rooms.size()
        + (rooms.size() == 1 ? " room" : " rooms") + " and " + (leftSideWalls.size() + rightSideWalls.size())
        + " wall sides" + (floorTexture != null ? ", floor texture \"" + floorTexture.getName() + "\"" : ""));
  }

  private static float [] getMiddle(float [] point1, float [] point2) {
    return new float [] {(point1 [0] + point2 [0]) / 2, (point1 [1] + point2 [1]) / 2};
  }

  /**
   * Returns the point 5 cm away from <code>point</code>, on the side
   * opposite to <code>opposite</code>.
   */
  private static float [] getPointBeside(float [] point, float [] opposite) {
    double dx = point [0] - opposite [0];
    double dy = point [1] - opposite [1];
    double length = Math.hypot(dx, dy);
    if (length < 1E-3) {
      return point;
    }
    return new float [] {(float)(point [0] + dx / length * 5), (float)(point [1] + dy / length * 5)};
  }

  // ---- Elevation drawings ----

  private void exportElevation(AssistantCommand command, List<String> notes) {
    String side = ElevationDrawing.parseSide(command.getString("side"));
    if (side == null) {
      notes.add("Ignored export_elevation: \"side\" must be south, north, east or west (the side the home is seen from)");
      return;
    }
    String path = command.getString("path");
    if (path == null || path.trim().length() == 0) {
      notes.add("Ignored export_elevation without the absolute \"path\" of the SVG file to write");
      return;
    }
    path = path.trim();
    if (path.startsWith("~/")) {
      path = System.getProperty("user.home") + path.substring(1);
    }
    if (!path.toLowerCase(Locale.ENGLISH).endsWith(".svg")) {
      path += ".svg";
    }
    java.io.File file = new java.io.File(path);
    if (!file.isAbsolute() || file.getParentFile() == null || !file.getParentFile().isDirectory()) {
      notes.add("Ignored export_elevation: the path must be absolute, in a folder that exists");
      return;
    }
    if (file.exists() && !command.getBoolean("replace", false)) {
      notes.add(file + " exists; give \"replace\":true to overwrite it");
      return;
    }
    double section = command.getDouble("section", 0);
    String svg = ElevationDrawing.createSVG(this.home, side,
        section > 0 && AssistantCommandExecutor.sizeSane(section) ? (float)section : 0);
    if (svg == null) {
      notes.add("Nothing to draw in the elevation seen from the " + side);
      return;
    }
    try {
      java.io.OutputStream out = new java.io.FileOutputStream(file);
      try {
        out.write(svg.getBytes("UTF-8"));
      } finally {
        out.close();
      }
      notes.add("Wrote the elevation seen from the " + side + " in " + file);
    } catch (java.io.IOException ex) {
      notes.add("Couldn't write " + file + " (" + ex.getMessage() + ")");
    }
  }

  // ---- Room shapes ----

  /**
   * Returns the corners of a room of the given shape in a box of
   * <code>width</code> x <code>depth</code> whose top left corner is at
   * (<code>x</code>, <code>y</code>), clockwise, or <code>null</code> if the
   * shape is unknown or the cut doesn't fit.
   * <ul><li>L: the top right corner of the box is cut by cutWidth x cutDepth</li>
   * <li>T: both bottom corners are cut, leaving a bar of depth - cutDepth at the top
   * and a leg of width - 2 x cutWidth</li>
   * <li>U: a notch of cutWidth x cutDepth is cut in the middle of the top side</li></ul>
   */
  public static float [][] getShapePoints(String shape, float x, float y, float width, float depth,
                                          float cutWidth, float cutDepth) {
    String key = shape != null ? shape.trim().toUpperCase(Locale.ENGLISH) : "";
    if (key.startsWith("RECT") || key.equals("SQUARE")) {
      return new float [][] {{x, y}, {x + width, y}, {x + width, y + depth}, {x, y + depth}};
    }
    if (!(cutWidth > 0) || !(cutDepth > 0) || cutDepth >= depth) {
      return null;
    }
    if (key.equals("L")) {
      if (cutWidth >= width) {
        return null;
      }
      return new float [][] {{x, y}, {x + width - cutWidth, y}, {x + width - cutWidth, y + cutDepth},
                             {x + width, y + cutDepth}, {x + width, y + depth}, {x, y + depth}};
    } else if (key.equals("T")) {
      if (2 * cutWidth >= width) {
        return null;
      }
      float barDepth = depth - cutDepth;
      return new float [][] {{x, y}, {x + width, y}, {x + width, y + barDepth},
                             {x + width - cutWidth, y + barDepth}, {x + width - cutWidth, y + depth},
                             {x + cutWidth, y + depth}, {x + cutWidth, y + barDepth}, {x, y + barDepth}};
    } else if (key.equals("U")) {
      if (cutWidth >= width) {
        return null;
      }
      float side = (width - cutWidth) / 2;
      return new float [][] {{x, y}, {x + side, y}, {x + side, y + cutDepth}, {x + width - side, y + cutDepth},
                             {x + width - side, y}, {x + width, y}, {x + width, y + depth}, {x, y + depth}};
    }
    return null;
  }

  /**
   * Adds a room of a given shape with the walls around it.
   */
  private void addRoomShape(AssistantCommand command, int [] tally, List<String> notes) {
    double width = command.getDouble("width", Double.NaN);
    double depth = command.getDouble("depth", Double.NaN);
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    if (Double.isNaN(x) || Double.isNaN(y)) {
      // To the right of what the level contains
      float right = Float.NaN;
      float top = 0;
      for (Wall wall : this.home.getWalls()) {
        if (wall.isAtLevel(this.home.getSelectedLevel())) {
          float wallRight = Math.max(wall.getXStart(), wall.getXEnd());
          if (Float.isNaN(right) || wallRight > right) {
            right = wallRight;
          }
          top = Float.isNaN(top) ? wall.getYStart() : Math.min(top, Math.min(wall.getYStart(), wall.getYEnd()));
        }
      }
      for (Room room : this.home.getRooms()) {
        if (room.isAtLevel(this.home.getSelectedLevel())) {
          for (float [] point : room.getPoints()) {
            if (Float.isNaN(right) || point [0] > right) {
              right = point [0];
            }
          }
        }
      }
      x = Float.isNaN(right) ? 100 : right + 150;
      y = Float.isNaN(right) ? 100 : top;
    }
    if (!(width > 0) || !(depth > 0) || !AssistantCommandExecutor.sizeSane(width)
        || !AssistantCommandExecutor.sizeSane(depth) || !AssistantCommandExecutor.coordinateSane(x)
        || !AssistantCommandExecutor.coordinateSane(y)) {
      notes.add("Ignored add_room_shape: width and depth are required and must be in range");
      return;
    }
    String shape = command.getString("shape");
    float [][] points = getShapePoints(shape, (float)x, (float)y, (float)width, (float)depth,
        (float)command.getDouble("cut_width", width / 2), (float)command.getDouble("cut_depth", depth / 2));
    if (points == null) {
      notes.add("Ignored add_room_shape: \"shape\" must be rectangle, L, T or U, with cut_width and cut_depth "
          + "smaller than the room");
      return;
    }
    Level level = this.home.getSelectedLevel();
    this.executor.beginTurnUpdate();
    int wallCount = 0;
    if (command.getBoolean("walls", true)) {
      float thickness = (float)command.getDouble("thickness", this.preferences.getNewWallThickness());
      if (!(thickness > 0) || !AssistantCommandExecutor.sizeSane(thickness)) {
        thickness = this.preferences.getNewWallThickness();
      }
      float height = (float)command.getDouble("height", this.preferences.getNewWallHeight());
      if (!(height > 0) || !AssistantCommandExecutor.sizeSane(height)) {
        height = this.preferences.getNewWallHeight();
      }
      // The middle lines of the walls run half a thickness outside the room.
      // Corners are right angles, and the points turn clockwise (y goes down)
      float h = thickness / 2;
      int count = points.length;
      float [][] wallPoints = new float [count][];
      for (int i = 0; i < count; i++) {
        float [] previous = points [(i + count - 1) % count];
        float [] point = points [i];
        float [] next = points [(i + 1) % count];
        // Outward normals of the sides before and after the corner
        float [] normal1 = getOutwardNormal(previous, point);
        float [] normal2 = getOutwardNormal(point, next);
        wallPoints [i] = new float [] {point [0] + (normal1 [0] + normal2 [0]) * h,
                                       point [1] + (normal1 [1] + normal2 [1]) * h};
      }
      Wall [] walls = new Wall [count];
      for (int i = 0; i < count; i++) {
        float [] start = wallPoints [i];
        float [] end = wallPoints [(i + 1) % count];
        if (!this.buildingCommands.isAlongExistingWall(
              new float [] {start [0], start [1], end [0], end [1]}, thickness, level)) {
          walls [i] = new Wall(start [0], start [1], end [0], end [1], thickness, height);
          walls [i].setLevel(level);
        }
      }
      List<Wall> addedWalls = new ArrayList<Wall>();
      for (int i = 0; i < count; i++) {
        Wall next = walls [(i + 1) % count];
        if (walls [i] != null) {
          if (next != null) {
            walls [i].setWallAtEnd(next);
            next.setWallAtStart(walls [i]);
          }
          addedWalls.add(walls [i]);
        }
      }
      if (!addedWalls.isEmpty()) {
        this.homeController.getPlanController().addWalls(addedWalls);
        wallCount = addedWalls.size();
        tally [3] += wallCount;
      }
    }
    Room room = new Room(points);
    String name = command.getString("name");
    if (name != null && name.trim().length() > 0) {
      room.setName(name.trim());
    }
    room.setLevel(level);
    this.homeController.getPlanController().addRooms(Collections.singletonList(room));
    tally [2]++;
    notes.add("Added a " + (shape != null ? shape.trim() : "rectangle") + " shaped room of "
        + String.format(Locale.US, "%.1f", room.getArea() / 10000) + " m2 with " + wallCount + " walls, its top left corner at ("
        + Math.round(x) + ", " + Math.round(y) + ")");
  }

  /**
   * Returns the unit normal of the side of a clockwise polygon (in a frame
   * where y goes down) that points out of it.
   */
  private static float [] getOutwardNormal(float [] start, float [] end) {
    double dx = end [0] - start [0];
    double dy = end [1] - start [1];
    double length = Math.hypot(dx, dy);
    return new float [] {(float)(dy / length), (float)(-dx / length)};
  }
}
