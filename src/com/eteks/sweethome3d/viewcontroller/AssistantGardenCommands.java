/*
 * AssistantGardenCommands.java 21 nov. 2026
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
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands that build the garden (fences, hedges, paths, planting beds) and the ones that furnish a room by themselves.
 */
class AssistantGardenCommands {
  /** Property of a piece built by these commands: <code>fence</code>, <code>hedge</code>, <code>path</code> or <code>planting_bed</code>. */
  static final String GARDEN_PROPERTY = "assistantGarden";

  private static final int   MAX_POINTS = 500;
  private static final int   MAX_STATIONS = 4000;
  private static final int   MAX_PICKETS = 3000;
  private static final int   MAX_SCATTERED = 400;
  private static final int   MAX_LISTED_IDS = 12;
  /** The length of the parts a line is cut in to follow the ground, in cm. */
  private static final float GROUND_STEP = 50;

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantGardenCommands(Home home, UserPreferences preferences, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class,
   * each one starting with two spaces and ending with a new line.
   */
  static String getReference() {
    return "  {\"action\":\"add_fence\",\"points\":[[x,y],[x,y],...],\"height\":120,\"style\":\"picket|panel|rail|wire\",\"post_spacing\":240}  "
        + "(a fence as one piece: posts at the corners and every post_spacing cm, rails, pickets, panels or wires between them, "
        + "each span following the slope of the ground built by set_terrain. \"closed\":true joins the last point to the first; "
        + "\"gate\":{\"at\":N,\"width\":N} leaves an opening centered N cm along the fence, between two posts; \"color\", \"name\". "
        + "The answer gives its length and its count of posts)\n"
        + "  {\"action\":\"add_hedge\",\"points\":[[x,y],[x,y],...],\"height\":150,\"width\":60}  "
        + "(a hedge with chamfered top edges along the line, mitred at its corners, which follows the ground; \"closed\", \"color\", \"name\")\n"
        + "  {\"action\":\"add_path\",\"points\":[[x,y],[x,y],...],\"width\":100}  "
        + "(a paved strip along a center line, mitred at its corners and laid on the ground: \"thickness\" (4), \"color\" or \"texture\" "
        + "with texture_scale, \"edging\":true for a curb on both sides, \"closed\", \"name\". "
        + "\"stepping_stones\":{\"length\":40,\"gap\":20} lays separate slabs instead)\n"
        + "  {\"action\":\"add_planting_bed\",\"points\":[[x,y],[x,y],[x,y],...],\"plants\":{\"name\":\"<catalog name>\",\"spacing\":80}}  "
        + "(an area of soil laid on the ground: \"color\" or \"texture\", \"thickness\" (6), \"border\":{\"height\":10,\"color\":\"#RRGGBB\"} "
        + "for an edging. \"plants\" scatters catalog pieces inside on a jittered grid, the same at each call for the same points: "
        + "{\"name\" or \"names\":[...],\"spacing\":N}, or a list of names)\n"
        + "  {\"action\":\"add_trees\",\"area\":[[x,y],[x,y],[x,y],...],\"names\":[\"<catalog name>\"],\"spacing\":300}  "
        + "(or \"scatter\": catalog pieces put on the ground in an area, or along a line with \"along\":[[x,y],...]; \"spacing\" or \"count\"; "
        + "the places closer than \"min_distance_from_buildings\" (150) to a room or a wall are skipped; \"jitter\":N cm, "
        + "\"rotate\":false to keep them all at the same angle. The default catalog has no tree: search_catalog or import_furniture first)\n"
        + "  {\"action\":\"furnish_room\",\"room\":\"R1\",\"type\":\"bedroom|living_room|dining_room|kitchen|office|bathroom|kids_room|entry\"}  "
        + "(furnishes a room with catalog pieces placed by rules: against the free parts of the walls, their back to the wall, "
        + "out of the zones doors need to open, tall pieces away from windows, nothing overlapping and a passage of 60 cm kept "
        + "from the doors to every piece. room = an id or a name; type is guessed from the name of the room if missing; "
        + "\"replace\":true removes first the furniture of the room; \"max_items\":N; \"style\":\"<word>\" prefers the catalog names with that word. "
        + "The answer lists each piece with its id and its place, what was skipped and why, and the free floor left. "
        + "It works with any catalog but only knows the usual names: bed, wardrobe, sofa, table, chair, desk...)\n"
        + "  {\"action\":\"suggest_layout\",\"room\":\"R1\",\"type\":\"bedroom\"}  "
        + "(the same computation as furnish_room, reported without changing the home)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("add_fence".equals(action)) {
        addFence(command, tally, notes);
      } else if ("add_hedge".equals(action)) {
        addHedge(command, tally, notes);
      } else if ("add_path".equals(action) || "add_garden_path".equals(action)) {
        addPath(command, tally, notes);
      } else if ("add_planting_bed".equals(action) || "add_flower_bed".equals(action)) {
        addPlantingBed(command, tally, notes);
      } else if ("add_trees".equals(action) || "scatter".equals(action)) {
        scatter(action, command, tally, notes);
      } else if ("furnish_room".equals(action)) {
        furnishRoom(action, command, true, tally, notes);
      } else if ("suggest_layout".equals(action)) {
        furnishRoom(action, command, false, tally, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    } catch (RuntimeException ex) {
      notes.add("Couldn't run " + action + " (" + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()) + ")");
    }
    return true;
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  // ---- Common ----

  /**
   * The height of the ground above the floor of a level.
   */
  private final class Ground {
    private final float   levelElevation;
    private final boolean terrain;
    private float         lowest = Float.MAX_VALUE;
    private float         highest = -Float.MAX_VALUE;

    Ground(Level level) {
      this.levelElevation = level != null ? level.getElevation() : 0;
      boolean terrain = false;
      for (HomePieceOfFurniture piece : home.getFurniture()) {
        terrain |= HomeTerrain.isTerrain(piece);
      }
      this.terrain = terrain;
    }

    float at(float x, float y) {
      float height = executor.getShapeCommands().getStandingHeight(x, y) - this.levelElevation;
      this.lowest = Math.min(this.lowest, height);
      this.highest = Math.max(this.highest, height);
      return height;
    }

    /**
     * Returns how far under the ground the bottom of what stands on it goes, to leave no gap on a slope.
     */
    float getSink() {
      return this.terrain ? 5 : 0;
    }

    String describe() {
      return this.highest - this.lowest > 1
          ? ", following the ground from " + format(this.lowest + this.levelElevation) + " to "
              + format(this.highest + this.levelElevation) + " cm"
          : "";
    }
  }

  /**
   * A line of the plan walked along.
   */
  private static final class Line {
    final float [][] points;
    final boolean    closed;
    final double []  distances;
    final double     length;

    Line(float [][] points, boolean closed) {
      this.points = points;
      this.closed = closed;
      int sides = closed ? points.length : points.length - 1;
      this.distances = new double [sides + 1];
      for (int i = 0; i < sides; i++) {
        float [] next = points [(i + 1) % points.length];
        this.distances [i + 1] = this.distances [i] + Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
      }
      this.length = this.distances [sides];
    }

    int getSideCount() {
      return this.distances.length - 1;
    }

    int getSide(double distance) {
      for (int i = 0; i < getSideCount(); i++) {
        if (distance <= this.distances [i + 1]) {
          return i;
        }
      }
      return getSideCount() - 1;
    }

    float [] getPoint(double distance) {
      int side = getSide(distance);
      float [] point = this.points [side];
      float [] next = this.points [(side + 1) % this.points.length];
      double ratio = (distance - this.distances [side]) / (this.distances [side + 1] - this.distances [side]);
      ratio = Math.max(0, Math.min(1, ratio));
      return new float [] {(float)(point [0] + (next [0] - point [0]) * ratio), (float)(point [1] + (next [1] - point [1]) * ratio)};
    }

    float [] getDirection(double distance) {
      int side = getSide(distance);
      float [] point = this.points [side];
      float [] next = this.points [(side + 1) % this.points.length];
      float length = (float)(this.distances [side + 1] - this.distances [side]);
      return new float [] {(next [0] - point [0]) / length, (next [1] - point [1]) / length};
    }
  }

  /**
   * A place along a line: a point and the direction across the line there, which at
   * a corner is longer than 1 for the two sides to keep their width.
   */
  private static final class Station {
    float x;
    float y;
    float nx;
    float ny;
    float unitX;
    float unitY;
  }

  /**
   * Returns the points of a command, without the ones that repeat the previous point.
   */
  private float [][] getPoints(AssistantCommand command, String parameter, int minimum, String example) throws IOException {
    List<float []> points = new ArrayList<float []>();
    for (float [] point : command.getPoints(parameter)) {
      if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
        throw new IOException("points out of range");
      }
      if (points.isEmpty() || Math.hypot(point [0] - points.get(points.size() - 1) [0],
              point [1] - points.get(points.size() - 1) [1]) > 0.5) {
        points.add(point);
      }
    }
    if (points.size() >= 4 && Math.hypot(points.get(0) [0] - points.get(points.size() - 1) [0],
            points.get(0) [1] - points.get(points.size() - 1) [1]) <= 0.5) {
      // A last point that repeats the first one closes the line
      points.remove(points.size() - 1);
    }
    if (points.size() < minimum) {
      throw new IOException("\"" + parameter + "\" must give " + minimum + " different points [x,y] at least, like " + example);
    } else if (points.size() > MAX_POINTS) {
      throw new IOException("\"" + parameter + "\" has " + MAX_POINTS + " points at most");
    }
    return points.toArray(new float [points.size()][]);
  }

  private static float getSize(AssistantCommand command, String parameter, float defaultValue, float minimum, float maximum)
      throws IOException {
    double value = command.getDouble(parameter, defaultValue);
    if (!(value >= minimum && value <= maximum)) {
      throw new IOException("\"" + parameter + "\" goes from " + format(minimum) + " to " + format(maximum) + " cm");
    }
    return (float)value;
  }

  private static float getSize(Map<?, ?> map, String parameter, float defaultValue, float minimum, float maximum)
      throws IOException {
    float value = AssistantFurnitureBuilder.getNumber(map, parameter, defaultValue);
    if (!(value >= minimum && value <= maximum)) {
      throw new IOException("\"" + parameter + "\" goes from " + format(minimum) + " to " + format(maximum) + " cm");
    }
    return value;
  }

  private static int getColor(Object value, int defaultColor) throws IOException {
    if (value == null) {
      return defaultColor;
    }
    Integer color = AssistantCommandExecutor.parseColor(value.toString());
    if (color == null) {
      throw new IOException("\"color\" must be written #RRGGBB");
    }
    return color & 0xFFFFFF;
  }

  private static String getName(AssistantCommand command, String defaultName) {
    String name = command.getString("name");
    return name != null && name.trim().length() > 0 ? name.trim() : defaultName;
  }

  /**
   * Returns the color or the texture a command gives with <code>color</code> and <code>texture</code>.
   */
  private AssistantMeshBuilder.Material getMaterial(AssistantMeshBuilder mesh, Map<?, ?> parameters,
                                                    int defaultColor, List<String> notes) throws IOException {
    int color = getColor(parameters.get("color"), defaultColor);
    if (parameters.get("texture") != null || parameters.get("catalog_id") != null) {
      String [] ambiguity = new String [1];
      CatalogTexture texture = this.executor.getBuildingCommands().findTexture(
          parameters.get("texture") != null ? parameters.get("texture").toString() : null,
          parameters.get("texture_category") != null ? parameters.get("texture_category").toString() : null,
          parameters.get("catalog_id") != null ? parameters.get("catalog_id").toString() : null,
          "texture", notes, ambiguity);
      if (texture != null) {
        if (ambiguity [0] != null) {
          notes.add(ambiguity [0]);
        }
        return mesh.getMaterial(texture, AssistantFurnitureBuilder.getNumber(parameters, "texture_scale", 1),
            (float)Math.toRadians(AssistantFurnitureBuilder.getNumber(parameters, "texture_angle", 0)), 0, 0, color);
      }
    }
    return mesh.getMaterial(color);
  }

  /**
   * Adds to the home the piece that shows a mesh, as a part of the site.
   */
  private HomePieceOfFurniture addPiece(AssistantMeshBuilder mesh, String name, String kind, Level level, int [] tally)
      throws IOException {
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    HomePieceOfFurniture piece = shapeCommands.createPiece(name, mesh.store(kind), level, false, "site");
    piece.setProperty(GARDEN_PROPERTY, kind);
    piece.setProperty(HomeItemTags.TAGS_PROPERTY, "garden");
    shapeCommands.add(piece);
    tally [0]++;
    return piece;
  }

  /**
   * Adds catalog pieces to the home, as one undoable step.
   */
  private void addFurniture(List<HomePieceOfFurniture> furniture, Level level) {
    if (furniture.isEmpty()) {
      return;
    }
    this.executor.beginTurnUpdate();
    Level selectedLevel = this.home.getSelectedLevel();
    if (level != null && level != selectedLevel) {
      this.home.setSelectedLevel(level);
    }
    this.homeController.getFurnitureController().addFurniture(furniture);
    if (this.home.getSelectedLevel() != selectedLevel && selectedLevel != null) {
      this.home.setSelectedLevel(selectedLevel);
    }
  }

  private String describeIds(List<HomePieceOfFurniture> furniture) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < furniture.size(); i++) {
      if (i == MAX_LISTED_IDS) {
        text.append(" and ").append(furniture.size() - i).append(" more");
        break;
      }
      text.append(i > 0 ? ", " : "").append(this.executor.getId(furniture.get(i)));
    }
    return text.toString();
  }

  /**
   * Returns the places along a line, close enough to each other for what is built on them to follow the ground.
   */
  private static List<Station> getStations(float [][] points, boolean closed, float step) {
    int count = points.length;
    int sides = closed ? count : count - 1;
    float [][] normals = new float [sides][];
    double length = 0;
    for (int i = 0; i < sides; i++) {
      float [] next = points [(i + 1) % count];
      float sideLength = (float)Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
      normals [i] = new float [] {-(next [1] - points [i][1]) / sideLength, (next [0] - points [i][0]) / sideLength};
      length += sideLength;
    }
    step = (float)Math.max(step, length / MAX_STATIONS);
    List<Station> stations = new ArrayList<Station>();
    for (int i = 0; i < count; i++) {
      float [] before = i > 0 ? normals [i - 1] : closed ? normals [sides - 1] : null;
      float [] after = i < sides ? normals [i] : null;
      Station corner = new Station();
      corner.x = points [i][0];
      corner.y = points [i][1];
      if (before == null || after == null) {
        float [] normal = before != null ? before : after;
        corner.nx = normal [0];
        corner.ny = normal [1];
      } else {
        float sumX = before [0] + after [0];
        float sumY = before [1] + after [1];
        float dot = before [0] * after [0] + before [1] * after [1];
        float sumLength = (float)Math.hypot(sumX, sumY);
        if (sumLength < 1E-3f) {
          // The line comes back on itself
          corner.nx = after [0];
          corner.ny = after [1];
        } else if (1 + dot < 0.3f) {
          // A sharp corner isn't thrown far away
          corner.nx = sumX / sumLength * 2.5f;
          corner.ny = sumY / sumLength * 2.5f;
        } else {
          corner.nx = sumX / (1 + dot);
          corner.ny = sumY / (1 + dot);
        }
      }
      float normalLength = (float)Math.hypot(corner.nx, corner.ny);
      corner.unitX = corner.nx / normalLength;
      corner.unitY = corner.ny / normalLength;
      stations.add(corner);
      if (i < sides) {
        float [] next = points [(i + 1) % count];
        int steps = Math.max(1, (int)Math.ceil(Math.hypot(next [0] - points [i][0], next [1] - points [i][1]) / step));
        for (int k = 1; k < steps; k++) {
          Station station = new Station();
          station.x = points [i][0] + (next [0] - points [i][0]) * k / steps;
          station.y = points [i][1] + (next [1] - points [i][1]) * k / steps;
          station.nx = station.unitX = normals [i][0];
          station.ny = station.unitY = normals [i][1];
          stations.add(station);
        }
      }
    }
    return stations;
  }

  private static Station getStation(float x, float y, float normalX, float normalY) {
    Station station = new Station();
    station.x = x;
    station.y = y;
    station.nx = station.unitX = normalX;
    station.ny = station.unitY = normalY;
    return station;
  }

  /**
   * Adds the solid swept by a section moved along stations. A point {u, v} of the section is
   * <code>u</code> cm across the line and <code>v</code> cm above the ground under itself, so the solid
   * follows the ground along and across the line.
   * @param materials the material of each side of the section, or one for all
   */
  private static void addSweep(AssistantMeshBuilder mesh, List<Station> stations, boolean closed, float [][] section,
                               Ground ground, AssistantMeshBuilder.Material... materials) {
    int count = stations.size();
    int size = section.length;
    if (count < 2) {
      return;
    }
    double sectionArea = 0;
    for (int k = 0; k < size; k++) {
      float [] next = section [(k + 1) % size];
      sectionArea += section [k][0] * next [1] - next [0] * section [k][1];
    }
    float orientation = sectionArea >= 0 ? 1 : -1;
    float [][][] rings = new float [count][size][];
    for (int i = 0; i < count; i++) {
      Station station = stations.get(i);
      for (int k = 0; k < size; k++) {
        float x = station.x + station.nx * section [k][0];
        float y = station.y + station.ny * section [k][0];
        rings [i][k] = new float [] {x, ground.at(x, y) + section [k][1], y};
      }
    }
    for (int i = 0; i < (closed ? count : count - 1); i++) {
      float [][] ring = rings [i];
      float [][] next = rings [(i + 1) % count];
      float unitX = (stations.get(i).unitX + stations.get((i + 1) % count).unitX) / 2;
      float unitY = (stations.get(i).unitY + stations.get((i + 1) % count).unitY) / 2;
      for (int k = 0; k < size; k++) {
        int k2 = (k + 1) % size;
        // The side of the section turned to the outside
        float outU = (section [k2][1] - section [k][1]) * orientation;
        float outV = -(section [k2][0] - section [k][0]) * orientation;
        AssistantMeshBuilder.Material material = materials [Math.min(k, materials.length - 1)];
        mesh.addPolygon(new float [][] {ring [k], ring [k2], next [k2]}, material, unitX * outU, outV, unitY * outU);
        mesh.addPolygon(new float [][] {ring [k], next [k2], next [k]}, material, unitX * outU, outV, unitY * outU);
      }
    }
    if (!closed) {
      float [] start = {rings [0][0][0] - rings [1][0][0], 0, rings [0][0][2] - rings [1][0][2]};
      float [] end = {rings [count - 1][0][0] - rings [count - 2][0][0], 0, rings [count - 1][0][2] - rings [count - 2][0][2]};
      mesh.addPolygon(rings [0], materials [0], start [0], start [1], start [2]);
      mesh.addPolygon(rings [count - 1], materials [0], end [0], end [1], end [2]);
    }
  }

  /**
   * Adds an upright box on a point of the ground.
   */
  private static void addPost(AssistantMeshBuilder mesh, float x, float y, float [] direction, float width, float thickness,
                              float bottom, float top, AssistantMeshBuilder.Material material) {
    float ax = direction [0] * width / 2;
    float ay = direction [1] * width / 2;
    float bx = -direction [1] * thickness / 2;
    float by = direction [0] * thickness / 2;
    mesh.addSolid(new float [][] {{x - ax - bx, bottom, y - ay - by}, {x + ax - bx, bottom, y + ay - by},
                                  {x + ax + bx, bottom, y + ay + by}, {x - ax + bx, bottom, y - ay + by}},
        new float [] {0, top - bottom, 0}, material);
  }

  private static long getSeed(float [][] points) {
    long seed = 17;
    for (float [] point : points) {
      seed = seed * 31 + Float.floatToIntBits(point [0]);
      seed = seed * 31 + Float.floatToIntBits(point [1]);
    }
    return seed;
  }

  private static double getDistanceToSides(float [][] polygon, float x, float y) {
    double distance = Double.MAX_VALUE;
    for (int i = 0; i < polygon.length; i++) {
      float [] next = polygon [(i + 1) % polygon.length];
      distance = Math.min(distance, Line2D.ptSegDist(polygon [i][0], polygon [i][1], next [0], next [1], x, y));
    }
    return distance;
  }

  /**
   * Returns the points of a grid over a polygon, each one moved at random by <code>jitter</code>
   * at most, that are inside it and <code>margin</code> away from its sides.
   */
  private static List<float []> getJitteredGrid(float [][] polygon, float spacing, float jitter, float margin, Random random) {
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    for (float [] point : polygon) {
      bounds [0] = Math.min(bounds [0], point [0]);
      bounds [1] = Math.min(bounds [1], point [1]);
      bounds [2] = Math.max(bounds [2], point [0]);
      bounds [3] = Math.max(bounds [3], point [1]);
    }
    // Centered in the bounds
    int columns = Math.max(1, (int)Math.floor((bounds [2] - bounds [0]) / spacing));
    int rows = Math.max(1, (int)Math.floor((bounds [3] - bounds [1]) / spacing));
    float startX = (bounds [0] + bounds [2]) / 2 - (columns - 1) * spacing / 2;
    float startY = (bounds [1] + bounds [3]) / 2 - (rows - 1) * spacing / 2;
    List<float []> points = new ArrayList<float []>();
    for (int row = 0; row < rows && points.size() < 20000; row++) {
      for (int column = 0; column < columns; column++) {
        float x = startX + column * spacing + (random.nextFloat() * 2 - 1) * jitter;
        float y = startY + row * spacing + (random.nextFloat() * 2 - 1) * jitter;
        if (AssistantRoomFurnisher.contains(polygon, x, y) && getDistanceToSides(polygon, x, y) >= margin) {
          points.add(new float [] {x, y});
        }
      }
    }
    return points;
  }

  /**
   * Returns the point of a side of a line at a distance from the start of the line: its ends are the points of the line.
   */
  private static float [] getSidePoint(Line line, int side, double distance) {
    float [] point = line.points [side];
    float [] next = line.points [(side + 1) % line.points.length];
    double length = line.distances [side + 1] - line.distances [side];
    double ratio = (distance - line.distances [side]) / length;
    if (ratio * length < 1E-3) {
      return point;
    } else if ((1 - ratio) * length < 1E-3) {
      return next;
    }
    return new float [] {(float)(point [0] + (next [0] - point [0]) * ratio), (float)(point [1] + (next [1] - point [1]) * ratio)};
  }

  /**
   * Returns <code>true</code> if a line is closed: it's asked for, or its last point repeats the first one.
   */
  private static boolean isClosed(AssistantCommand command, String parameter, float [][] points) {
    List<float []> given = command.getPoints(parameter);
    boolean loop = given.size() >= 4 && Math.hypot(given.get(0) [0] - given.get(given.size() - 1) [0],
        given.get(0) [1] - given.get(given.size() - 1) [1]) <= 0.5;
    return points.length >= 3 && command.getBoolean("closed", loop);
  }

  // ---- Fences ----

  private void addFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float [][] points = getPoints(command, "points", 2, "[[0,0],[600,0],[600,400]]");
    boolean closed = isClosed(command, "points", points);
    float height = getSize(command, "height", 120, 20, 400);
    float spacing = getSize(command, "post_spacing", 240, 30, 1000);
    String style = command.getString("style") != null ? command.getString("style").trim().toLowerCase(Locale.ENGLISH) : "picket";
    if (style.startsWith("picket")) {
      style = "picket";
    } else if (style.startsWith("panel") || style.equals("solid") || style.equals("privacy")) {
      style = "panel";
    } else if (style.startsWith("rail") || style.equals("ranch")) {
      style = "rail";
    } else if (style.startsWith("wire") || style.equals("mesh")) {
      style = "wire";
    } else {
      throw new IOException("\"style\" is picket, panel, rail or wire");
    }
    Line line = new Line(points, closed);
    // The opening of the gate
    double gateStart = Double.NaN;
    double gateEnd = Double.NaN;
    Object gate = command.getParameters().get("gate");
    if (gate instanceof Map) {
      float at = AssistantFurnitureBuilder.getNumber((Map<?, ?>)gate, "at", Float.NaN);
      float width = getSize((Map<?, ?>)gate, "width", 100, 30, 1000);
      if (Float.isNaN(at)) {
        throw new IOException("\"gate\" must give \"at\": the distance in cm from the first point to the middle of the gate, along the fence");
      } else if (at - width / 2 < -0.5f || at + width / 2 > line.length + 0.5f) {
        throw new IOException("the gate goes from " + format(at - width / 2) + " to " + format(at + width / 2)
            + " cm along a fence " + format(line.length) + " cm long");
      }
      gateStart = Math.max(0, at - width / 2);
      gateEnd = Math.min(line.length, at + width / 2);
    } else if (gate != null) {
      throw new IOException("\"gate\" is written {\"at\":N,\"width\":N}");
    }
    Level level = this.executor.getShapeCommands().getLevel(command, "add_fence", notes);
    Ground ground = new Ground(level);
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    boolean colored = command.getString("color") != null;
    int color = getColor(command.getString("color"),
        style.equals("picket") ? 0xF2EFE6 : style.equals("panel") ? 0x9A7B55 : style.equals("rail") ? 0x8B6B47 : 0x6F6B62);
    AssistantMeshBuilder.Material material = mesh.getMaterial(color);
    AssistantMeshBuilder.Material wireMaterial = mesh.getMaterial(colored ? color : 0x9AA0A0);
    float postSize = style.equals("wire") ? 6 : 9;

    // The spans between posts: the sides of the line without the gate, cut in equal parts
    List<double []> spans = new ArrayList<double []>();
    Map<String, double []> posts = new LinkedHashMap<String, double []>();
    for (int side = 0; side < line.getSideCount(); side++) {
      double start = line.distances [side];
      double end = line.distances [side + 1];
      double [][] parts = Double.isNaN(gateStart) || gateEnd <= start || gateStart >= end
          ? new double [][] {{start, end}}
          : new double [][] {{start, Math.max(start, gateStart)}, {Math.min(end, gateEnd), end}};
      for (double [] part : parts) {
        // A side that the gate takes whole still has a post at each end of the gate
        int count = part [1] - part [0] < 1 ? 0 : Math.max(1, (int)Math.ceil((part [1] - part [0]) / spacing - 1E-4));
        if (count == 0 && (Double.isNaN(gateStart) || part [1] < part [0])) {
          continue;
        }
        float [] previous = null;
        for (int i = 0; i <= count; i++) {
          double distance = count == 0 ? part [0] : part [0] + (part [1] - part [0]) * i / count;
          float [] point = getSidePoint(line, side, distance);
          boolean gatePost = !Double.isNaN(gateStart)
              && (Math.abs(distance - gateStart) < 1E-3 || Math.abs(distance - gateEnd) < 1E-3);
          String key = Math.round(point [0] * 10) + ":" + Math.round(point [1] * 10);
          double [] post = posts.get(key);
          if (post == null) {
            posts.put(key, new double [] {point [0], point [1], side, gatePost ? 1 : 0});
          } else if (gatePost) {
            post [3] = 1;
          }
          if (previous != null) {
            spans.add(new double [] {previous [0], previous [1], point [0], point [1]});
          }
          previous = point;
        }
      }
    }
    for (double [] post : posts.values()) {
      float x = (float)post [0];
      float y = (float)post [1];
      float height0 = ground.at(x, y);
      addPost(mesh, x, y, line.getDirection(line.distances [(int)post [2]] + 1E-3), postSize, postSize,
          height0 - ground.getSink(), height0 + height + (post [3] != 0 ? 16 : 6), material);
    }
    // What joins the posts follows the slope from one to the next
    double picketLength = 0;
    for (double [] span : spans) {
      picketLength += Math.hypot(span [2] - span [0], span [3] - span [1]);
    }
    float picketPitch = (float)Math.max(12, picketLength / MAX_PICKETS);
    for (double [] span : spans) {
      float x1 = (float)span [0];
      float y1 = (float)span [1];
      float x2 = (float)span [2];
      float y2 = (float)span [3];
      float length = (float)Math.hypot(x2 - x1, y2 - y1);
      if (length < 0.5f) {
        continue;
      }
      float [] direction = {(x2 - x1) / length, (y2 - y1) / length};
      float ground1 = ground.at(x1, y1);
      float ground2 = ground.at(x2, y2);
      if (style.equals("panel")) {
        float nx = -direction [1] * 1.25f;
        float ny = direction [0] * 1.25f;
        mesh.addSolid(new float [][] {{x1 - nx, ground1 + 5, y1 - ny}, {x1 + nx, ground1 + 5, y1 + ny},
                                      {x1 + nx, ground1 + height, y1 + ny}, {x1 - nx, ground1 + height, y1 - ny}},
            new float [] {x2 - x1, ground2 - ground1, y2 - y1}, material);
      } else {
        int rails = style.equals("picket") ? 2 : style.equals("wire") ? (height >= 100 ? 5 : 4) : height >= 100 ? 3 : 2;
        float lowest = style.equals("picket") ? 0.28f : 0.25f;
        float highest = style.equals("picket") ? 0.78f : style.equals("wire") ? 0.97f : 0.92f;
        float railWidth = style.equals("wire") ? 0.8f : style.equals("picket") ? 3 : 4;
        float railHeight = style.equals("wire") ? 0.8f : style.equals("picket") ? 7 : 9;
        for (int i = 0; i < rails; i++) {
          float top = height * (lowest + (highest - lowest) * i / (rails - 1));
          mesh.addBar(new float [] {x1, ground1 + top, y1}, new float [] {x2, ground2 + top, y2}, railWidth, railHeight,
              style.equals("wire") ? wireMaterial : material);
        }
        if (style.equals("picket")) {
          // The pickets stand on the same side of the rails
          int count = Math.max(1, Math.round(length / picketPitch));
          float pitch = length / count;
          float width = Math.min(7, pitch * 0.6f);
          for (int i = 0; i < count; i++) {
            float ratio = (i + 0.5f) / count;
            float x = x1 + (x2 - x1) * ratio - direction [1] * 2.5f;
            float y = y1 + (y2 - y1) * ratio + direction [0] * 2.5f;
            float base = ground1 + (ground2 - ground1) * ratio;
            addPost(mesh, x, y, direction, width, 2, base + 5, base + height, material);
          }
        }
      }
    }
    String name = getName(command, "Fence");
    HomePieceOfFurniture piece = addPiece(mesh, name, "fence", level, tally);
    double fenceLength = line.length - (Double.isNaN(gateStart) ? 0 : gateEnd - gateStart);
    notes.add("Added the fence " + this.executor.getId(piece) + " \"" + name + "\" (" + style + ", " + format(height)
        + " cm high): " + format(fenceLength / 100) + " m long, " + posts.size() + " posts, " + spans.size()
        + (spans.size() == 1 ? " span" : " spans") + (closed ? ", closed" : "")
        + (Double.isNaN(gateStart) ? ""
            : ", an opening of " + format(gateEnd - gateStart) + " cm for a gate from " + format(gateStart) + " to "
                + format(gateEnd) + " cm along it, between two taller posts")
        + ground.describe());
  }

  // ---- Hedges ----

  private void addHedge(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float [][] points = getPoints(command, "points", 2, "[[0,0],[600,0]]");
    boolean closed = isClosed(command, "points", points);
    float height = getSize(command, "height", 150, 10, 800);
    float width = getSize(command, "width", 60, 5, 600);
    Level level = this.executor.getShapeCommands().getLevel(command, "add_hedge", notes);
    Ground ground = new Ground(level);
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material material = mesh.getMaterial(getColor(command.getString("color"), 0x3F6B35));
    float chamfer = Math.min(width, height) * 0.25f;
    float [][] section = {{-width / 2, -ground.getSink()}, {width / 2, -ground.getSink()}, {width / 2, height - chamfer},
                          {width / 2 - chamfer, height}, {-width / 2 + chamfer, height}, {-width / 2, height - chamfer}};
    addSweep(mesh, getStations(points, closed, GROUND_STEP), closed, section, ground, material);
    String name = getName(command, "Hedge");
    HomePieceOfFurniture piece = addPiece(mesh, name, "hedge", level, tally);
    notes.add("Added the hedge " + this.executor.getId(piece) + " \"" + name + "\": "
        + format(new Line(points, closed).length / 100) + " m long, " + format(width) + " cm wide, " + format(height)
        + " cm high" + (closed ? ", closed" : "") + ground.describe());
  }

  // ---- Paths ----

  private void addPath(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float [][] points = getPoints(command, "points", 2, "[[0,0],[0,500],[300,800]]");
    boolean closed = isClosed(command, "points", points);
    float width = getSize(command, "width", 100, 5, 3000);
    float thickness = getSize(command, "thickness", 4, 0.5f, 100);
    Level level = this.executor.getShapeCommands().getLevel(command, "add_path", notes);
    Ground ground = new Ground(level);
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material topMaterial = getMaterial(mesh, command.getParameters(), 0xB9B4A8, notes);
    AssistantMeshBuilder.Material sideMaterial = topMaterial.isTextured() ? mesh.getMaterial(topMaterial.getColor()) : topMaterial;
    float sink = ground.getSink();
    Line line = new Line(points, closed);
    String description;
    Object stones = command.getParameters().get("stepping_stones");
    if (stones instanceof Map || Boolean.TRUE.equals(stones)) {
      Map<?, ?> map = stones instanceof Map ? (Map<?, ?>)stones : Collections.emptyMap();
      float length = getSize(map, "length", 40, 5, 1000);
      float gap = getSize(map, "gap", 20, 0, 1000);
      int count = (int)Math.floor((line.length + gap + 1E-3) / (length + gap));
      if (count < 1) {
        throw new IOException("the line is " + format(line.length) + " cm long, shorter than a stone of " + format(length) + " cm");
      } else if (count > 2000) {
        throw new IOException("that makes " + count + " stones: 2000 at most");
      }
      double start = (line.length - (count * length + (count - 1) * gap)) / 2;
      float [][] section = {{-width / 2, -sink}, {width / 2, -sink}, {width / 2, thickness}, {-width / 2, thickness}};
      for (int i = 0; i < count; i++) {
        double middle = start + i * (length + gap) + length / 2;
        float [] center = line.getPoint(middle);
        float [] direction = line.getDirection(middle);
        List<Station> stations = new ArrayList<Station>();
        stations.add(getStation(center [0] - direction [0] * length / 2, center [1] - direction [1] * length / 2, -direction [1], direction [0]));
        stations.add(getStation(center [0] + direction [0] * length / 2, center [1] + direction [1] * length / 2, -direction [1], direction [0]));
        addSweep(mesh, stations, false, section, ground, sideMaterial, sideMaterial, topMaterial, sideMaterial);
      }
      description = count + " stepping stones of " + format(length) + " x " + format(width) + " cm, " + format(gap)
          + " cm apart, along " + format(line.length / 100) + " m";
    } else {
      List<Station> stations = getStations(points, closed, GROUND_STEP);
      // Its top follows the ground in its middle too
      float [][] section = {{-width / 2, -sink}, {width / 2, -sink}, {width / 2, thickness}, {0, thickness}, {-width / 2, thickness}};
      addSweep(mesh, stations, closed, section, ground, sideMaterial, sideMaterial, topMaterial, topMaterial, sideMaterial);
      if (command.getBoolean("edging", false)) {
        AssistantMeshBuilder.Material edgingMaterial = mesh.getMaterial(getColor(command.getString("edging_color"), 0x8A8782));
        float edgingWidth = 5;
        float edgingTop = thickness + 3;
        for (int side = -1; side <= 1; side += 2) {
          float inner = side * width / 2;
          float outer = side * (width / 2 + edgingWidth);
          addSweep(mesh, stations, closed, new float [][] {{Math.min(inner, outer), -sink}, {Math.max(inner, outer), -sink},
              {Math.max(inner, outer), edgingTop}, {Math.min(inner, outer), edgingTop}}, ground, edgingMaterial);
        }
      }
      description = format(line.length / 100) + " m long, " + format(width) + " cm wide, "
          + format(line.length * width / 10000) + " m2" + (closed ? ", closed" : "")
          + (command.getBoolean("edging", false) ? ", with an edging on both sides" : "");
    }
    String name = getName(command, "Path");
    HomePieceOfFurniture piece = addPiece(mesh, name, "path", level, tally);
    notes.add("Added the path " + this.executor.getId(piece) + " \"" + name + "\": " + description + ", "
        + format(thickness) + " cm thick" + ground.describe());
  }

  // ---- Planting beds ----

  private void addPlantingBed(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float [][] points = getPoints(command, "points", 3, "[[0,0],[400,0],[400,150],[0,150]]");
    float thickness = getSize(command, "thickness", 6, 0.5f, 200);
    Area area = AssistantMeshBuilder.getArea(points);
    if (area.isEmpty()) {
      throw new IOException("the points enclose no area");
    }
    Level level = this.executor.getShapeCommands().getLevel(command, "add_planting_bed", notes);
    Ground ground = new Ground(level);
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material topMaterial = getMaterial(mesh, command.getParameters(), 0x4A3524, notes);
    AssistantMeshBuilder.Material sideMaterial = topMaterial.isTextured() ? mesh.getMaterial(topMaterial.getColor()) : topMaterial;
    float sink = ground.getSink();
    Rectangle2D bounds = area.getBounds2D();
    // Is the ground flat under it?
    float cell = (float)Math.max(50, Math.sqrt(bounds.getWidth() * bounds.getHeight() / 600));
    float first = ground.at(points [0][0], points [0][1]);
    boolean flat = true;
    for (float y = (float)bounds.getMinY(); y <= bounds.getMaxY() + cell && flat; y += cell) {
      for (float x = (float)bounds.getMinX(); x <= bounds.getMaxX() + cell && flat; x += cell) {
        flat = Math.abs(ground.at((float)Math.min(x, bounds.getMaxX()), (float)Math.min(y, bounds.getMaxY())) - first) < 0.1f;
      }
    }
    if (flat) {
      mesh.addPrism(area, first - sink, new float [] {first + thickness, 0, 0}, topMaterial, sideMaterial);
    } else {
      // Its top cut in cells, each corner at the height of the ground
      for (float y = (float)bounds.getMinY(); y < bounds.getMaxY(); y += cell) {
        for (float x = (float)bounds.getMinX(); x < bounds.getMaxX(); x += cell) {
          Area part = new Area(new Rectangle2D.Float(x, y, cell, cell));
          part.intersect(area);
          for (float [][] trapezoid : AssistantMeshBuilder.getTrapezoids(part)) {
            float [][] corners = new float [trapezoid.length][];
            for (int i = 0; i < trapezoid.length; i++) {
              corners [i] = new float [] {trapezoid [i][0], ground.at(trapezoid [i][0], trapezoid [i][1]) + thickness, trapezoid [i][1]};
            }
            for (int i = 1; i + 1 < corners.length; i++) {
              mesh.addPolygon(new float [][] {corners [0], corners [i], corners [i + 1]}, topMaterial, 0, 1, 0);
            }
          }
        }
      }
      for (float [][] outline : AssistantMeshBuilder.getOutlines(area)) {
        for (int i = 0; i < outline.length; i++) {
          float [] point = outline [i];
          float [] next = outline [(i + 1) % outline.length];
          float length = (float)Math.hypot(next [0] - point [0], next [1] - point [1]);
          if (length < 1E-3f) {
            continue;
          }
          float nx = (next [1] - point [1]) / length;
          float ny = -(next [0] - point [0]) / length;
          if (area.contains((point [0] + next [0]) / 2 + nx * 0.05f, (point [1] + next [1]) / 2 + ny * 0.05f)) {
            nx = -nx;
            ny = -ny;
          }
          int steps = Math.max(1, (int)Math.ceil(length / cell));
          for (int step = 0; step < steps; step++) {
            float xa = point [0] + (next [0] - point [0]) * step / steps;
            float ya = point [1] + (next [1] - point [1]) * step / steps;
            float xb = point [0] + (next [0] - point [0]) * (step + 1) / steps;
            float yb = point [1] + (next [1] - point [1]) * (step + 1) / steps;
            float groundA = ground.at(xa, ya);
            float groundB = ground.at(xb, yb);
            mesh.addPolygon(new float [][] {{xa, groundA - sink, ya}, {xb, groundB - sink, yb},
                                            {xb, groundB + thickness, yb}, {xa, groundA + thickness, ya}}, sideMaterial, nx, 0, ny);
          }
        }
      }
    }
    // Its edging
    Object border = command.getParameters().get("border");
    String borderDescription = "";
    if (border instanceof Map || Boolean.TRUE.equals(border)) {
      Map<?, ?> map = border instanceof Map ? (Map<?, ?>)border : Collections.emptyMap();
      float borderHeight = Math.max(getSize(map, "height", 10, 1, 200), thickness + 1);
      float borderWidth = getSize(map, "width", 5, 1, 100);
      AssistantMeshBuilder.Material borderMaterial = mesh.getMaterial(getColor(map.get("color"), 0x77736B));
      addSweep(mesh, getStations(points, true, cell), true, new float [][] {{-borderWidth / 2, -sink}, {borderWidth / 2, -sink},
          {borderWidth / 2, borderHeight}, {-borderWidth / 2, borderHeight}}, ground, borderMaterial);
      borderDescription = ", with an edging " + format(borderHeight) + " cm high";
    } else if (border != null && !Boolean.FALSE.equals(border)) {
      throw new IOException("\"border\" is written {\"height\":N,\"color\":\"#RRGGBB\"}");
    }
    String name = getName(command, "Planting bed");
    HomePieceOfFurniture bed = addPiece(mesh, name, "planting_bed", level, tally);
    StringBuilder note = new StringBuilder("Added the planting bed " + this.executor.getId(bed) + " \"" + name + "\": "
        + format(AssistantRoomFurnisher.getSurface(area) / 10000) + " m2" + borderDescription);

    // Its plants
    Object plants = command.getParameters().get("plants");
    if (plants != null) {
      List<String> names = new ArrayList<String>();
      float spacing = 80;
      if (plants instanceof Map) {
        Map<?, ?> map = (Map<?, ?>)plants;
        if (map.get("names") instanceof List) {
          for (Object plantName : (List<?>)map.get("names")) {
            names.add(String.valueOf(plantName));
          }
        } else if (map.get("name") != null) {
          names.add(map.get("name").toString());
        }
        spacing = getSize(map, "spacing", 80, 10, 2000);
      } else if (plants instanceof List) {
        for (Object plantName : (List<?>)plants) {
          names.add(String.valueOf(plantName));
        }
      } else {
        names.add(plants.toString());
      }
      List<CatalogPieceOfFurniture> catalogPieces = findCatalogPieces(names, notes);
      if (catalogPieces.isEmpty()) {
        note.append(". No plant was added: give \"plants\":{\"name\":\"<catalog name>\",\"spacing\":N} with a name of the catalog");
      } else {
        Random random = new Random(getSeed(points));
        List<float []> places = getJitteredGrid(points, spacing, spacing * 0.25f, Math.min(spacing / 3, 20), random);
        int ignored = Math.max(0, places.size() - MAX_SCATTERED);
        List<HomePieceOfFurniture> furniture = createScatteredFurniture(
            places.subList(0, places.size() - ignored), catalogPieces, random, true, level, ground, thickness / 2);
        addFurniture(furniture, level);
        tally [0] += furniture.size();
        note.append(", ").append(furniture.size()).append(furniture.size() == 1 ? " plant" : " plants")
            .append(" (").append(describeNames(furniture)).append(") every ").append(format(spacing)).append(" cm");
        if (!furniture.isEmpty()) {
          note.append(": ").append(describeIds(furniture));
        } else {
          note.append(": the bed is too small for that spacing");
        }
        if (ignored > 0) {
          note.append(". ").append(ignored).append(" more places were left empty: ").append(MAX_SCATTERED)
              .append(" plants at most, use a larger spacing");
        }
      }
    }
    notes.add(note.append(ground.describe()).toString());
  }

  // ---- Trees and other scattered pieces ----

  /**
   * Returns the pieces of the catalog with the given names, after noting the names that match none.
   */
  private List<CatalogPieceOfFurniture> findCatalogPieces(List<String> names, List<String> notes) {
    List<CatalogPieceOfFurniture> pieces = new ArrayList<CatalogPieceOfFurniture>();
    for (String name : names) {
      CatalogPieceOfFurniture piece = name.trim().length() > 0 ? this.executor.findCatalogPiece(name, false) : null;
      if (piece != null && !piece.isDoorOrWindow()) {
        pieces.add(piece);
      } else {
        StringBuilder note = new StringBuilder("No catalog piece is named \"" + name + "\"");
        List<AssistantCommandExecutor.CatalogMatch> matches = this.executor.searchCatalog("plant tree flowers shrub bush", false, 5);
        if (!matches.isEmpty()) {
          note.append("; plants of the catalog: ");
          for (int i = 0; i < matches.size(); i++) {
            note.append(i > 0 ? ", " : "").append('"').append(matches.get(i).piece.getName()).append('"');
          }
        }
        notes.add(note.append(". import_furniture adds the model of a tree to the home, and a furniture library adds some to the catalog").toString());
      }
    }
    return pieces;
  }

  /**
   * Returns pieces of the catalog standing on the ground at the given places, outdoors.
   */
  private List<HomePieceOfFurniture> createScatteredFurniture(List<float []> places, List<CatalogPieceOfFurniture> catalogPieces,
                                                              Random random, boolean rotate, Level level, Ground ground,
                                                              float elevation) {
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    for (float [] place : places) {
      CatalogPieceOfFurniture catalogPiece = catalogPieces.get(catalogPieces.size() == 1 ? 0 : random.nextInt(catalogPieces.size()));
      float angle = random.nextFloat() * 2 * (float)Math.PI;
      HomePieceOfFurniture piece = this.homeController.getFurnitureController().createHomePieceOfFurniture(catalogPiece);
      piece.setX(place [0]);
      piece.setY(place [1]);
      if (rotate) {
        piece.setAngle(angle);
      }
      piece.setElevation(ground.at(place [0], place [1]) + elevation);
      piece.setLevel(level);
      // A plant on the ground isn't furniture out of its room
      piece.setProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY, "true");
      piece.setProperty(HomeItemTags.TAGS_PROPERTY, "garden");
      furniture.add(piece);
    }
    return furniture;
  }

  private static String describeNames(List<HomePieceOfFurniture> furniture) {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    for (HomePieceOfFurniture piece : furniture) {
      Integer count = counts.get(piece.getName());
      counts.put(piece.getName(), count == null ? 1 : count + 1);
    }
    StringBuilder text = new StringBuilder();
    for (Map.Entry<String, Integer> count : counts.entrySet()) {
      text.append(text.length() > 0 ? ", " : "").append(count.getValue()).append(" \"").append(count.getKey()).append('"');
    }
    return text.toString();
  }

  private void scatter(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<String> names = command.getStringList("names");
    if (names.isEmpty()) {
      names = command.getStringList("name");
    }
    boolean along = command.has("along");
    String areaParameter = command.has("area") ? "area" : "points";
    if (names.isEmpty() || !along && !command.has(areaParameter)) {
      throw new IOException("give \"names\":[\"<catalog name>\",...] and \"area\":[[x,y],[x,y],[x,y],...] or \"along\":[[x,y],[x,y],...], "
          + "with \"spacing\":N or \"count\":N");
    }
    float [][] points = along ? getPoints(command, "along", 2, "[[0,0],[1000,0]]")
        : getPoints(command, areaParameter, 3, "[[0,0],[1000,0],[1000,800],[0,800]]");
    double spacing = command.getDouble("spacing", Double.NaN);
    int count = (int)command.getDouble("count", 0);
    if (!Double.isNaN(spacing) && !(spacing >= 10 && spacing <= 10000) || count < 0 || count > MAX_SCATTERED) {
      throw new IOException("\"spacing\" goes from 10 to 10000 cm and \"count\" from 1 to " + MAX_SCATTERED);
    }
    float minDistance = getSize(command, "min_distance_from_buildings", 150, 0, 5000);
    List<CatalogPieceOfFurniture> catalogPieces = findCatalogPieces(names, notes);
    if (catalogPieces.isEmpty()) {
      throw new IOException("none of the names is in the catalog: {\"action\":\"search_catalog\",\"query\":\"tree\"} lists what matches");
    }
    Level level = this.executor.getShapeCommands().getLevel(command, action, notes);
    Ground ground = new Ground(level);
    Random random = new Random(getSeed(points));
    List<float []> places = new ArrayList<float []>();
    String extent;
    if (along) {
      Line line = new Line(points, isClosed(command, "along", points));
      float jitter = getSize(command, "jitter", 0, 0, 1000);
      double step;
      double start;
      if (count > 0) {
        // The count asked for, from an end to the other
        step = count > 1 ? line.length / (line.closed ? count : count - 1) : 0;
        start = count > 1 ? 0 : line.length / 2;
      } else {
        step = Double.isNaN(spacing) ? 300 : spacing;
        count = (int)Math.floor(line.length / step + 1E-3) + (line.closed ? 0 : 1);
        count = Math.max(1, Math.min(MAX_SCATTERED, count));
        start = line.closed ? 0 : (line.length - (count - 1) * step) / 2;
      }
      for (int i = 0; i < count; i++) {
        float [] point = line.getPoint(start + i * step);
        places.add(new float [] {point [0] + (random.nextFloat() * 2 - 1) * jitter, point [1] + (random.nextFloat() * 2 - 1) * jitter});
      }
      extent = "along " + format(line.length / 100) + " m";
    } else {
      double surface = AssistantRoomFurnisher.getSurface(AssistantMeshBuilder.getArea(points));
      if (surface < 1) {
        throw new IOException("the points enclose no area");
      }
      if (Double.isNaN(spacing)) {
        spacing = count > 0 ? Math.sqrt(surface / count) : 300;
      }
      float jitter = getSize(command, "jitter", (float)spacing * 0.25f, 0, 1000);
      // A finer grid if it doesn't give the count asked for
      for (int attempt = 0; attempt < 4; attempt++) {
        places = getJitteredGrid(points, (float)spacing, jitter, 0, new Random(getSeed(points)));
        if (count == 0 || places.size() >= count || command.has("spacing")) {
          break;
        }
        spacing *= 0.85;
      }
      extent = "over " + format(surface / 10000) + " m2";
    }
    // Not in the buildings or against them
    int nearBuildings = 0;
    for (int i = places.size() - 1; i >= 0; i--) {
      float [] place = places.get(i);
      boolean near = false;
      for (Room room : this.home.getRooms()) {
        if (room.containsPoint(place [0], place [1], minDistance)) {
          near = true;
          break;
        }
      }
      if (!near) {
        for (Wall wall : this.home.getWalls()) {
          if (wall.containsPoint(place [0], place [1], minDistance)) {
            near = true;
            break;
          }
        }
      }
      if (near) {
        places.remove(i);
        nearBuildings++;
      }
    }
    int ignored = 0;
    if (!along && count > 0 && places.size() > count) {
      // The count asked for, taken all over the area
      List<float []> chosen = new ArrayList<float []>(places);
      Collections.shuffle(chosen, new Random(getSeed(points) + 1));
      chosen = chosen.subList(0, count);
      places.retainAll(chosen);
    } else if (places.size() > MAX_SCATTERED) {
      ignored = places.size() - MAX_SCATTERED;
      places = places.subList(0, MAX_SCATTERED);
    }
    List<HomePieceOfFurniture> furniture = createScatteredFurniture(places, catalogPieces, random,
        command.getBoolean("rotate", true), level, ground, 0);
    addFurniture(furniture, level);
    tally [0] += furniture.size();
    StringBuilder note = new StringBuilder();
    if (furniture.isEmpty()) {
      note.append("No piece was placed ").append(extent);
    } else {
      note.append("Placed ").append(furniture.size()).append(furniture.size() == 1 ? " piece (" : " pieces (")
          .append(describeNames(furniture)).append(") ").append(extent).append(", on the ground: ").append(describeIds(furniture));
    }
    if (nearBuildings > 0) {
      note.append(". ").append(nearBuildings).append(nearBuildings == 1 ? " place was" : " places were")
          .append(" skipped, in a room or closer than ").append(format(minDistance)).append(" cm to a room or a wall");
    }
    if (!along && count > 0 && furniture.size() < count) {
      note.append(". ").append(count).append(" were asked for: the area has no room for more at ")
          .append(format(spacing)).append(" cm from each other");
    }
    if (ignored > 0) {
      note.append(". ").append(ignored).append(" more places were left empty: ").append(MAX_SCATTERED).append(" pieces at most at a time");
    }
    notes.add(note.append(ground.describe()).toString());
  }

  // ---- Rooms furnished by rules ----

  private String describeRooms() {
    StringBuilder text = new StringBuilder();
    for (Room room : this.home.getRooms()) {
      text.append(text.length() > 0 ? ", " : "").append(this.executor.getId(room));
      if (room.getName() != null && room.getName().length() > 0) {
        text.append(" \"").append(room.getName()).append('"');
      }
    }
    return text.length() > 0 ? text.toString() : "none";
  }

  /**
   * Returns the room a command designates by its id or its name.
   */
  private Room getRoom(AssistantCommand command) throws IOException {
    String token = command.getString("room") != null ? command.getString("room") : command.getString("id");
    if (token == null || token.trim().length() == 0) {
      if (this.home.getRooms().size() == 1) {
        return this.home.getRooms().get(0);
      }
      throw new IOException("give the room with \"room\": its id or its name; rooms: " + describeRooms());
    }
    token = token.trim();
    HomeObject item = HomeItemIds.find(this.home, token);
    if (item instanceof Room) {
      return (Room)item;
    }
    Room found = null;
    for (Room room : this.home.getRooms()) {
      if (token.equalsIgnoreCase(room.getName())) {
        return room;
      } else if (found == null && room.getName() != null
                 && room.getName().toLowerCase(Locale.ENGLISH).contains(token.toLowerCase(Locale.ENGLISH))) {
        found = room;
      }
    }
    if (found == null) {
      throw new IOException("no room has the id or the name \"" + token + "\"; rooms: " + describeRooms());
    }
    return found;
  }

  private void furnishRoom(String action, AssistantCommand command, boolean apply, int [] tally, List<String> notes)
      throws IOException {
    final Room room = getRoom(command);
    if (room.getPointCount() < 3) {
      throw new IOException("the room has no area");
    }
    String roomDescription = this.executor.getId(room) + (room.getName() != null && room.getName().length() > 0
        ? " \"" + room.getName() + "\"" : "");
    String type = command.getString("type");
    boolean guessed = false;
    if (type != null) {
      type = type.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_').replace('-', '_');
      if (type.equals("living") || type.equals("lounge")) {
        type = "living_room";
      } else if (type.equals("dining")) {
        type = "dining_room";
      } else if (type.equals("kids") || type.equals("children_room") || type.equals("child_room") || type.equals("nursery")) {
        type = "kids_room";
      } else if (type.equals("study")) {
        type = "office";
      } else if (type.equals("entrance") || type.equals("hall") || type.equals("hallway")) {
        type = "entry";
      }
    } else {
      type = AssistantRoomFurnisher.guessType(room.getName());
      guessed = true;
    }
    if (type == null || !java.util.Arrays.asList(AssistantRoomFurnisher.TYPES).contains(type)) {
      throw new IOException((guessed ? "the name of the room " + roomDescription + " doesn't tell what it is: give " : "")
          + "\"type\": bedroom, living_room, dining_room, kitchen, office, bathroom, kids_room or entry");
    }
    int maxItems = (int)command.getDouble("max_items", 40);
    if (maxItems < 1 || maxItems > 100) {
      throw new IOException("\"max_items\" goes from 1 to 100");
    }
    // The furniture taken away first
    List<HomePieceOfFurniture> replaced = new ArrayList<HomePieceOfFurniture>();
    if (command.getBoolean("replace", false)) {
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (!piece.isDoorOrWindow() && piece.isAtLevel(room.getLevel())
            && piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) == null
            && room.containsPoint(piece.getX(), piece.getY(), 0)
            && this.homeController.getPlanController().isPieceOfFurnitureDeletable(piece)) {
          replaced.add(piece);
        }
      }
    }
    final String style = command.getString("style") != null ? command.getString("style").trim().toLowerCase(Locale.ENGLISH) : "";
    AssistantRoomFurnisher furnisher = new AssistantRoomFurnisher(this.home, room, replaced, new AssistantRoomFurnisher.Catalog() {
        public CatalogPieceOfFurniture find(String [] queries) {
          for (int pass = style.length() > 0 ? 0 : 1; pass < 2; pass++) {
            for (String query : queries) {
              for (AssistantCommandExecutor.CatalogMatch match : executor.searchCatalog(pass == 0 ? style + " " + query : query, false, 8)) {
                // A name with all the words asked for, not a name that shares one of them
                if (match.score >= 400 && !match.piece.isDoorOrWindow()) {
                  return match.piece;
                }
              }
            }
          }
          return null;
        }
      }, maxItems);
    furnisher.furnish(type);

    List<AssistantRoomFurnisher.Placement> placements = furnisher.getPlacements();
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    if (apply) {
      if (!replaced.isEmpty()) {
        this.executor.beginTurnUpdate();
        this.homeController.getPlanController().deleteItems(new ArrayList<Selectable>(replaced));
        tally [5] += replaced.size();
      }
      for (AssistantRoomFurnisher.Placement placement : placements) {
        HomePieceOfFurniture piece = this.homeController.getFurnitureController().createHomePieceOfFurniture(placement.piece);
        piece.setX(placement.x);
        piece.setY(placement.y);
        piece.setAngle(placement.angle);
        piece.setLevel(room.getLevel());
        furniture.add(piece);
      }
      addFurniture(furniture, room.getLevel());
      tally [0] += furniture.size();
    }
    StringBuilder note = new StringBuilder(apply ? "Furnished the room " : "Layout suggested for the room ");
    note.append(roomDescription).append(" as ").append(type.startsWith("e") || type.startsWith("o") ? "an " : "a ")
        .append(type.replace('_', ' ')).append(guessed ? " (guessed from its name)" : "").append(": ")
        .append(format(furnisher.getRoomArea() / 10000)).append(" m2, ")
        .append(furnisher.getDoorCount()).append(furnisher.getDoorCount() == 1 ? " door, " : " doors, ")
        .append(furnisher.getWindowCount()).append(furnisher.getWindowCount() == 1 ? " window" : " windows");
    if (!replaced.isEmpty()) {
      note.append(apply ? "; removed " : "; would remove ").append(replaced.size())
          .append(replaced.size() == 1 ? " piece that was in it" : " pieces that were in it");
    }
    if (placements.isEmpty()) {
      note.append(apply ? ". Nothing was placed" : ". Nothing would be placed");
    } else {
      note.append(apply ? ". Placed " : ". Would place ").append(placements.size())
          .append(placements.size() == 1 ? " piece:" : " pieces:");
      for (int i = 0; i < placements.size(); i++) {
        AssistantRoomFurnisher.Placement placement = placements.get(i);
        note.append("\n- ");
        if (apply) {
          note.append(this.executor.getId(furniture.get(i))).append(' ');
        }
        note.append('"').append(placement.piece.getName()).append("\" at (").append(format(placement.x)).append(", ")
            .append(format(placement.y)).append("), angle ").append(format(Math.toDegrees(placement.angle))).append(", ")
            .append(placement.where);
      }
    }
    if (!furnisher.getSkipped().isEmpty()) {
      note.append("\nSkipped:");
      for (String skipped : furnisher.getSkipped()) {
        note.append("\n- ").append(skipped);
      }
    }
    note.append("\nFree floor left: ").append(format(furnisher.getFreeArea() / 10000)).append(" m2 of ")
        .append(format(furnisher.getRoomArea() / 10000)).append(" m2");
    if (!apply && !placements.isEmpty()) {
      note.append(". {\"action\":\"furnish_room\"} with the same parameters places these pieces");
    }
    notes.add(note.toString());
  }
}
