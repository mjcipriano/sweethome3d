/*
 * AssistantTerrainCommands.java 23 nov. 2026
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

import java.awt.geom.Line2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands that read the ground from a raster or a file, grade it (pads, paths, berms, swales),
 * and measure it (cut and fill, contours, spot elevations, slopes).
 */
class AssistantTerrainCommands {
  /** The property of a home that keeps the points of its ground before it was graded, in JSON. */
  static final String BASELINE_PROPERTY = "assistantTerrainBaseline";

  private static final long MAX_FILE_LENGTH = 200L * 1024 * 1024;
  private static final int  DEFAULT_POINTS = 20000;

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantTerrainCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"set_terrain\",\"raster\":{\"x0\":N,\"y0\":N,\"cell\":N,\"cols\":N,\"rows\":N,\"heights\":[...]}}  "
        + "(the ground from a grid of heights in cm: x0, y0 = the place of the first height, at the top left; cell = the distance "
        + "between two heights in cm; heights = the rows from the top of the plan to its bottom, one after the other. "
        + "Or \"file\":\"<absolute path>\" of an Esri ASCII grid (.asc) or of a text of x y z lines (.xyz, .txt, .csv), with "
        + "\"units\":\"m|ft|cm\" (m) for its distances, \"z_units\" for its heights, and its place: \"srs\":\"EPSG:2240\" with the "
        + "georeference of the home, or \"x\",\"y\" for the plan place of its lower left corner. The heights are thinned to "
        + "\"max_points\" (20000, up to " + AssistantShapeCommands.getMaxTerrainPoints() + "), keeping more of them where the ground "
        + "changes: \"max_error_cm\" (5) is the error accepted. \"datum_offset_cm\":N is added to every height, or "
        + "\"pin\":{\"x\":N,\"y\":N,\"height\":N} moves the ground up or down for it to be at that height at that place, "
        + "like a doorstep. GeoTIFF, LAS and LAZ files aren't read: convert them with gdal_translate -of AAIGrid)\n"
        + "  {\"action\":\"shift_terrain\",\"pin\":{\"x\":N,\"y\":N,\"height\":N}}  (moves the whole ground up or down: \"offset\":N cm, "
        + "or \"pin\" for it to be at a height at a place)\n"
        + "  {\"action\":\"add_pad\",\"points\":[[x,y],...],\"height\":N,\"blend_distance\":200}  "
        + "(a flat pad cut or filled in the ground under a polygon, for a house or a driveway: \"height\" in cm (the mean height of "
        + "the ground there by default), \"slope\":{\"percent\":2,\"dir\":180} for a pad that drains (dir = the downhill direction, "
        + "0 up the plan, 90 its right), and around it the ground joins the pad over \"blend_distance\" cm with "
        + "\"blend_curve\":\"linear|smoothstep\". The answer gives the volumes cut and filled)\n"
        + "  {\"action\":\"flatten_area\",\"points\":[[x,y],...]}  (the same as add_pad at the mean height of the ground in the polygon)\n"
        + "  {\"action\":\"smooth_area\",\"points\":[[x,y],...],\"radius\":300}  (softens the bumps of the ground in a polygon: each "
        + "height becomes the mean of the heights around it; \"strength\" from 0 to 1)\n"
        + "  {\"action\":\"add_berm\",\"points\":[[x,y],[x,y],...],\"height\":60,\"width\":300}  (a rounded mound along a line; "
        + "add_swale digs a shallow channel the same way, with \"depth\")\n"
        + "  {\"action\":\"grade_path\",\"points\":[[x,y],[x,y],...],\"width\":300}  (the ground of a road, a driveway or a path "
        + "along a center line: it goes straight from the height of a point to the next one, or through \"heights\":[one per point]; "
        + "\"cross_slope\":2 (% towards its right), \"crown\":N cm in its middle, \"ditch\":{\"depth\":30,\"width\":100} on both sides, "
        + "\"blend_distance\":150. It shapes the ground only: add_path or add_slab lays the surface)\n"
        + "  {\"action\":\"extend_terrain\",\"margin\":500}  (extends the ground to cover every item of the home plus a margin, "
        + "at the height of its closest edge)\n"
        + "  {\"action\":\"cut_fill_report\"}  (the volumes cut and filled since the ground was first graded, in m3 and cubic "
        + "yards, in the whole ground or in \"area\":[[x,y],...]; \"reset\":true takes the ground as it is for the original one)\n"
        + "  {\"action\":\"contours\",\"interval\":50}  (draws the contour lines of the ground in the plan every interval cm, the ones "
        + "at 5 intervals thicker and labeled; \"area\":[[x,y],...] limits them; \"remove\":true deletes the contour lines)\n"
        + "  {\"action\":\"spot_elevations\",\"points\":[[x,y],...]}  (the height of the ground at points, with a label in the plan at "
        + "each one unless \"labels\":false)\n"
        + "  {\"action\":\"slope_map\"}  (the share of the ground by slope: flat, gentle, walkable, steep..., its steepest place, "
        + "and the places steeper than \"threshold\" % in \"area\":[[x,y],...] or the whole ground. A table: no overlay is drawn)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("set_terrain".equals(action) || "add_terrain".equals(action)) {
        // A new ground is the one to compare the next grading with
        if (command.getParameters().get("raster") == null && command.getParameters().get("file") == null
            && command.getParameters().get("pin") == null && command.getParameters().get("datum_offset_cm") == null) {
          this.home.setProperty(BASELINE_PROPERTY, null);
          return false;
        }
        setTerrainFromHeights(command, tally, notes);
      } else if ("shift_terrain".equals(action)) {
        shiftTerrain(command, tally, notes);
      } else if ("add_pad".equals(action) || "flatten_area".equals(action)) {
        addPad(action, command, tally, notes);
      } else if ("smooth_area".equals(action)) {
        smoothArea(command, tally, notes);
      } else if ("add_berm".equals(action) || "add_swale".equals(action)) {
        addBerm(action, command, tally, notes);
      } else if ("grade_path".equals(action)) {
        gradePath(command, tally, notes);
      } else if ("extend_terrain".equals(action)) {
        extendTerrain(command, tally, notes);
      } else if ("cut_fill_report".equals(action)) {
        cutFillReport(command, notes);
      } else if ("contours".equals(action)) {
        contours(command, tally, notes);
      } else if ("spot_elevations".equals(action)) {
        spotElevations(command, tally, notes);
      } else if ("slope_map".equals(action)) {
        slopeMap(command, notes);
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

  // ---- The ground as it is ----

  private HomePieceOfFurniture getTerrain() throws IOException {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (HomeTerrain.isTerrain(piece)) {
        return piece;
      }
    }
    throw new IOException("the home has no ground: set_terrain builds one from points, a raster or a file");
  }

  private static List<float []> getPoints(String spec) {
    try {
      Object parsed = JSONParser.parse(spec);
      if (parsed instanceof List) {
        return AssistantFurnitureBuilder.getPoints(Collections.singletonMap("points", parsed), "points", 3);
      }
    } catch (IllegalArgumentException ex) {
      // No points
    }
    return new ArrayList<float []>();
  }

  private float getHeight(float x, float y) {
    Float height = this.executor.getShapeCommands().getTerrainHeight(x, y);
    if (height == null) {
      float [] nearest = this.executor.getShapeCommands().getNearestTerrainHeight(x, y);
      return nearest != null ? nearest [0] : 0;
    }
    return height;
  }

  /**
   * Triangles looked for by place.
   */
  private static final class Surface {
    private final List<float [][]>       triangles;
    private final List<List<float [][]>> cells = new ArrayList<List<float [][]>>();
    private final float []               bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    private final int                    size;

    Surface(List<float [][]> triangles) {
      this.triangles = triangles;
      for (float [][] triangle : triangles) {
        for (float [] corner : triangle) {
          this.bounds [0] = Math.min(this.bounds [0], corner [0]);
          this.bounds [1] = Math.min(this.bounds [1], corner [1]);
          this.bounds [2] = Math.max(this.bounds [2], corner [0]);
          this.bounds [3] = Math.max(this.bounds [3], corner [1]);
        }
      }
      this.size = Math.max(1, Math.min(400, (int)Math.sqrt(triangles.size() / 2.0)));
      for (int i = 0; i < this.size * this.size; i++) {
        this.cells.add(null);
      }
      for (float [][] triangle : triangles) {
        int [] range = {this.size, this.size, 0, 0};
        for (float [] corner : triangle) {
          range [0] = Math.min(range [0], column(corner [0]));
          range [2] = Math.max(range [2], column(corner [0]));
          range [1] = Math.min(range [1], row(corner [1]));
          range [3] = Math.max(range [3], row(corner [1]));
        }
        for (int row = range [1]; row <= range [3]; row++) {
          for (int column = range [0]; column <= range [2]; column++) {
            List<float [][]> cell = this.cells.get(row * this.size + column);
            if (cell == null) {
              this.cells.set(row * this.size + column, cell = new ArrayList<float [][]>());
            }
            cell.add(triangle);
          }
        }
      }
    }

    private int column(float x) {
      return Math.max(0, Math.min(this.size - 1, (int)((x - this.bounds [0]) / Math.max(1E-3f, (this.bounds [2] - this.bounds [0]) / this.size))));
    }

    private int row(float y) {
      return Math.max(0, Math.min(this.size - 1, (int)((y - this.bounds [1]) / Math.max(1E-3f, (this.bounds [3] - this.bounds [1]) / this.size))));
    }

    /**
     * Returns the height at a point, or NaN out of the surface.
     */
    float getHeight(float x, float y) {
      if (this.triangles.isEmpty() || x < this.bounds [0] - 0.01f || x > this.bounds [2] + 0.01f
          || y < this.bounds [1] - 0.01f || y > this.bounds [3] + 0.01f) {
        return Float.NaN;
      }
      List<float [][]> cell = this.cells.get(row(y) * this.size + column(x));
      if (cell != null) {
        for (float [][] triangle : cell) {
          double determinant = (triangle [1][1] - triangle [2][1]) * (triangle [0][0] - triangle [2][0])
              + (triangle [2][0] - triangle [1][0]) * (triangle [0][1] - triangle [2][1]);
          double w1 = ((triangle [1][1] - triangle [2][1]) * (x - triangle [2][0])
              + (triangle [2][0] - triangle [1][0]) * (y - triangle [2][1])) / determinant;
          double w2 = ((triangle [2][1] - triangle [0][1]) * (x - triangle [2][0])
              + (triangle [0][0] - triangle [2][0]) * (y - triangle [2][1])) / determinant;
          double w3 = 1 - w1 - w2;
          if (w1 >= -1E-4 && w2 >= -1E-4 && w3 >= -1E-4) {
            return (float)(w1 * triangle [0][2] + w2 * triangle [1][2] + w3 * triangle [2][2]);
          }
        }
      }
      return Float.NaN;
    }
  }

  /**
   * Builds the ground again from new points, with the finishes it had.
   */
  @SuppressWarnings("unchecked")
  private String regrade(HomePieceOfFurniture terrain, List<float []> points, int [] tally, List<String> notes) throws IOException {
    if (points.size() > AssistantShapeCommands.getMaxTerrainPoints()) {
      throw new IOException("the ground would have " + points.size() + " points, more than "
          + AssistantShapeCommands.getMaxTerrainPoints());
    }
    if (this.home.getProperty(BASELINE_PROPERTY) == null) {
      // The ground before any grading, for the volumes cut and filled
      this.home.setProperty(BASELINE_PROPERTY, terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY));
    }
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    try {
      Object parsed = JSONParser.parse(terrain.getProperty(AssistantShapeCommands.TERRAIN_PARAMETERS_PROPERTY));
      if (parsed instanceof Map) {
        parameters.putAll((Map<String, Object>)parsed);
      }
    } catch (RuntimeException ex) {
      // Built with its defaults
    }
    for (String key : new String [] {"action", "points", "grid", "raster", "file", "pin", "datum_offset_cm", "max_points",
                                     "max_error_cm", "units", "z_units", "srs", "x", "y"}) {
      parameters.remove(key);
    }
    List<Object> list = new ArrayList<Object>(points.size());
    for (float [] point : points) {
      List<Object> coordinates = new ArrayList<Object>(3);
      coordinates.add(Double.valueOf(Math.round(point [0] * 10) / 10.));
      coordinates.add(Double.valueOf(Math.round(point [1] * 10) / 10.));
      coordinates.add(Double.valueOf(Math.round(point [2] * 10) / 10.));
      list.add(coordinates);
    }
    parameters.put("points", list);
    if (parameters.get("name") == null) {
      parameters.put("name", terrain.getName());
    }
    List<String> terrainNotes = new ArrayList<String>();
    int added = tally [0];
    this.executor.getShapeCommands().apply("set_terrain", new AssistantCommand("set_terrain", parameters), tally, terrainNotes);
    // The ground was changed rather than added
    tally [0] = added;
    tally [4]++;
    for (String note : terrainNotes) {
      if (note.startsWith("Couldn't")) {
        throw new IOException(note.substring(note.indexOf('(') + 1, note.lastIndexOf(')')));
      }
    }
    String id = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (HomeTerrain.isTerrain(piece)) {
        id = this.executor.getId(piece);
      }
    }
    return "the ground " + (id != null ? id + " " : "") + "now has " + points.size() + " points. Fences, trees and what stands "
        + "on it keep their heights: check_home and find_floating tell which ones to put back on the ground";
  }

  /**
   * Returns the points of the ground that a change keeps: the ones that aren't in the zone it shapes.
   */
  private static List<float []> keepPoints(List<float []> points, float [][] shape, boolean closed, float distance) {
    List<float []> kept = new ArrayList<float []>();
    for (float [] point : points) {
      if (closed && AssistantRoomFurnisher.contains(shape, point [0], point [1])) {
        continue;
      }
      double nearest = Double.MAX_VALUE;
      for (int i = 0; i < (closed ? shape.length : shape.length - 1); i++) {
        float [] next = shape [(i + 1) % shape.length];
        nearest = Math.min(nearest, Line2D.ptSegDist(shape [i][0], shape [i][1], next [0], next [1], point [0], point [1]));
      }
      if (nearest > distance) {
        kept.add(point);
      }
    }
    return kept;
  }

  /**
   * Returns points along a line, close enough to follow the ground.
   */
  private static List<float []> densify(float [][] line, boolean closed, float step) {
    List<float []> points = new ArrayList<float []>();
    int sides = closed ? line.length : line.length - 1;
    for (int i = 0; i < sides; i++) {
      float [] next = line [(i + 1) % line.length];
      int count = Math.max(1, (int)Math.ceil(Math.hypot(next [0] - line [i][0], next [1] - line [i][1]) / step));
      for (int k = 0; k < count; k++) {
        points.add(new float [] {line [i][0] + (next [0] - line [i][0]) * k / count, line [i][1] + (next [1] - line [i][1]) * k / count});
      }
    }
    if (!closed) {
      points.add(new float [] {line [line.length - 1][0], line [line.length - 1][1]});
    }
    return points;
  }

  private float [][] getShape(AssistantCommand command, String parameter, int minimum) throws IOException {
    List<float []> points = command.getPoints(parameter);
    if (points.size() < minimum) {
      throw new IOException("\"" + parameter + "\" must give " + minimum + " points [x,y] at least");
    }
    for (float [] point : points) {
      if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
        throw new IOException("points out of range");
      }
    }
    if (minimum >= 3 && points.size() > 3 && Math.hypot(points.get(0) [0] - points.get(points.size() - 1) [0],
            points.get(0) [1] - points.get(points.size() - 1) [1]) < 0.5) {
      points.remove(points.size() - 1);
    }
    return points.toArray(new float [points.size()][]);
  }

  private static double getArea(float [][] polygon) {
    double area = 0;
    for (int i = 0; i < polygon.length; i++) {
      float [] next = polygon [(i + 1) % polygon.length];
      area += polygon [i][0] * next [1] - next [0] * polygon [i][1];
    }
    return area / 2;
  }

  private static float [] getBounds(float [][] points) {
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    for (float [] point : points) {
      bounds [0] = Math.min(bounds [0], point [0]);
      bounds [1] = Math.min(bounds [1], point [1]);
      bounds [2] = Math.max(bounds [2], point [0]);
      bounds [3] = Math.max(bounds [3], point [1]);
    }
    return bounds;
  }

  /**
   * Returns the points of a grid inside a polygon.
   */
  private static List<float []> getInnerPoints(float [][] polygon, float spacing) {
    float [] bounds = getBounds(polygon);
    List<float []> points = new ArrayList<float []>();
    for (float y = bounds [1] + spacing / 2; y < bounds [3]; y += spacing) {
      for (float x = bounds [0] + spacing / 2; x < bounds [2]; x += spacing) {
        if (AssistantRoomFurnisher.contains(polygon, x, y)) {
          points.add(new float [] {x, y});
        }
      }
    }
    return points;
  }

  /**
   * Returns the volumes cut and filled between two surfaces in a zone, in m3: {cut, fill, area in m2}.
   */
  private static double [] getVolumes(Surface before, Surface after, float [] bounds, float [][] polygon) {
    float cell = (float)Math.max(20, Math.sqrt((double)(bounds [2] - bounds [0]) * (bounds [3] - bounds [1]) / 60000));
    double cut = 0;
    double fill = 0;
    double area = 0;
    for (float y = bounds [1] + cell / 2; y < bounds [3]; y += cell) {
      for (float x = bounds [0] + cell / 2; x < bounds [2]; x += cell) {
        if (polygon != null && !AssistantRoomFurnisher.contains(polygon, x, y)) {
          continue;
        }
        float old = before.getHeight(x, y);
        float now = after.getHeight(x, y);
        if (Float.isNaN(old) || Float.isNaN(now)) {
          continue;
        }
        area += cell * cell / 10000.;
        if (now < old) {
          cut += (old - now) * cell * cell / 1E6;
        } else {
          fill += (now - old) * cell * cell / 1E6;
        }
      }
    }
    return new double [] {cut, fill, area};
  }

  private static String describeVolumes(double [] volumes) {
    return format(volumes [0]) + " m3 cut (" + format(volumes [0] * 1.30795) + " cu yd) and " + format(volumes [1]) + " m3 filled ("
        + format(volumes [1] * 1.30795) + " cu yd), a net " + (volumes [1] >= volumes [0] ? "fill of " : "cut of ")
        + format(Math.abs(volumes [1] - volumes [0])) + " m3";
  }

  /**
   * Applies new points and tells the earth moved around a shape.
   */
  private String regradeAround(HomePieceOfFurniture terrain, List<float []> newPoints, float [][] shape, float reach, int [] tally,
                               List<String> notes) throws IOException {
    Surface before = new Surface(HomeTerrain.getTriangles(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY)));
    String result = regrade(terrain, newPoints, tally, notes);
    Surface after = new Surface(HomeTerrain.getTriangles(getTerrain().getProperty(AssistantShapeCommands.TERRAIN_PROPERTY)));
    float [] bounds = getBounds(shape);
    bounds = new float [] {bounds [0] - reach, bounds [1] - reach, bounds [2] + reach, bounds [3] + reach};
    return describeVolumes(getVolumes(before, after, bounds, null)) + "; " + result;
  }

  // ---- Heights from a raster or a file ----

  private void setTerrainFromHeights(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Map<String, Object> parameters = new LinkedHashMap<String, Object>(command.getParameters());
    int maxPoints = (int)Math.max(100, Math.min(AssistantShapeCommands.getMaxTerrainPoints(), command.getDouble("max_points", DEFAULT_POINTS)));
    float maxError = (float)Math.max(0, command.getDouble("max_error_cm", 5));
    List<float []> points;
    String source;
    if (parameters.get("raster") instanceof Map) {
      Map<?, ?> raster = (Map<?, ?>)parameters.get("raster");
      int columns = Math.round(AssistantFurnitureBuilder.getNumber(raster, "cols", 0));
      int rows = Math.round(AssistantFurnitureBuilder.getNumber(raster, "rows", 0));
      float cell = AssistantFurnitureBuilder.getNumber(raster, "cell", Float.NaN);
      List<?> values = raster.get("heights") instanceof List ? (List<?>)raster.get("heights") : null;
      if (values != null && !values.isEmpty() && values.get(0) instanceof List) {
        // Rows of heights
        List<Object> flat = new ArrayList<Object>();
        rows = values.size();
        columns = ((List<?>)values.get(0)).size();
        for (Object row : values) {
          if (row instanceof List) {
            flat.addAll((List<?>)row);
          }
        }
        values = flat;
      }
      if (values == null || columns < 2 || rows < 2 || !(cell > 0) || values.size() < columns * rows) {
        throw new IOException("\"raster\" needs x0, y0, cell (cm), cols, rows and \"heights\": cols x rows heights in cm, the rows from "
            + "the top of the plan to its bottom");
      }
      float [] heights = new float [columns * rows];
      for (int i = 0; i < heights.length; i++) {
        heights [i] = values.get(i) instanceof Number ? ((Number)values.get(i)).floatValue() : Float.NaN;
      }
      points = decimate(heights, columns, rows, AssistantFurnitureBuilder.getNumber(raster, "x0", 0),
          AssistantFurnitureBuilder.getNumber(raster, "y0", 0), cell, cell, maxPoints, maxError);
      source = "a raster of " + columns + " x " + rows + " heights";
    } else if (parameters.get("file") != null) {
      File file = new File(parameters.get("file").toString());
      if (!file.isAbsolute() || !file.isFile()) {
        throw new IOException("\"file\" must be the absolute path of a file that exists");
      }
      String lowerName = file.getName().toLowerCase(Locale.ENGLISH);
      if (lowerName.endsWith(".tif") || lowerName.endsWith(".tiff") || lowerName.endsWith(".las") || lowerName.endsWith(".laz")
          || lowerName.endsWith(".img")) {
        throw new IOException("GeoTIFF, LAS and LAZ files aren't read. Convert the raster with "
            + "\"gdal_translate -of AAIGrid in.tif out.asc\" (or \"gdal_translate -of XYZ\"), and a point cloud with a tool that "
            + "writes its ground points as x y z lines, then give that file");
      }
      points = readFile(file, command, maxPoints, maxError);
      source = file.getName();
    } else {
      // The ground as it is, moved
      shiftTerrain(command, tally, notes);
      return;
    }
    if (points.size() < 3) {
      throw new IOException("no height was read");
    }
    float offset = getDatumOffset(command, points);
    for (float [] point : points) {
      point [2] += offset;
    }
    for (String key : new String [] {"raster", "file", "pin", "datum_offset_cm", "max_points", "max_error_cm", "units", "z_units",
                                     "srs", "x", "y"}) {
      parameters.remove(key);
    }
    List<Object> list = new ArrayList<Object>(points.size());
    for (float [] point : points) {
      List<Object> coordinates = new ArrayList<Object>(3);
      coordinates.add(Double.valueOf(Math.round(point [0] * 10) / 10.));
      coordinates.add(Double.valueOf(Math.round(point [1] * 10) / 10.));
      coordinates.add(Double.valueOf(Math.round(point [2] * 10) / 10.));
      list.add(coordinates);
    }
    parameters.put("points", list);
    this.home.setProperty(BASELINE_PROPERTY, null);
    int noteCount = notes.size();
    this.executor.getShapeCommands().apply("set_terrain", new AssistantCommand("set_terrain", parameters), tally, notes);
    if (notes.size() > noteCount && !notes.get(noteCount).startsWith("Couldn't")) {
      notes.set(noteCount, notes.get(noteCount) + ". Read from " + source + ", thinned to " + points.size() + " points"
          + (offset != 0 ? ", moved by " + format(offset) + " cm" : ""));
    }
  }

  /**
   * Returns what is added to the heights: an offset, or what puts the ground at a height at a place.
   */
  private float getDatumOffset(AssistantCommand command, List<float []> points) throws IOException {
    float offset = (float)command.getDouble("datum_offset_cm", command.getDouble("offset", 0));
    if (command.getParameters().get("pin") instanceof Map) {
      Map<?, ?> pin = (Map<?, ?>)command.getParameters().get("pin");
      float x = AssistantFurnitureBuilder.getNumber(pin, "x", Float.NaN);
      float y = AssistantFurnitureBuilder.getNumber(pin, "y", Float.NaN);
      float height = AssistantFurnitureBuilder.getNumber(pin, "height", Float.NaN);
      if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(height)) {
        throw new IOException("\"pin\" is written {\"x\":N,\"y\":N,\"height\":N}: the ground is moved to be at that height at that place");
      }
      float current = new Surface(AssistantShapeCommands.triangulate(points)).getHeight(x, y);
      if (Float.isNaN(current)) {
        throw new IOException("the place of \"pin\" (" + format(x) + ", " + format(y) + ") is out of the ground");
      }
      offset += height - current;
    }
    return offset;
  }

  /**
   * Returns the points kept of a grid of heights: more of them where the ground isn't flat.
   */
  private static List<float []> decimate(float [] heights, int columns, int rows, float x0, float y0, float cellWidth,
                                         float cellHeight, int maxPoints, float maxError) {
    boolean [] kept = new boolean [columns * rows];
    int [] count = {0};
    // The whole grid cut in squares, each one cut again while its heights aren't on its plane
    int block = 1;
    while (block * 2 < Math.max(columns, rows) && (long)(columns / (block * 2) + 2) * (rows / (block * 2) + 2) > Math.max(16, maxPoints / 8)) {
      block *= 2;
    }
    while (block * 2 < Math.max(columns, rows) && block < 64) {
      block *= 2;
    }
    List<int []> queue = new ArrayList<int []>();
    for (int row = 0; row < rows - 1; row += block) {
      for (int column = 0; column < columns - 1; column += block) {
        queue.add(new int [] {column, row, Math.min(columns - 1, column + block), Math.min(rows - 1, row + block)});
      }
    }
    for (int pass = 0; pass < 12 && !queue.isEmpty(); pass++) {
      List<int []> next = new ArrayList<int []>();
      for (int [] square : queue) {
        int c1 = square [0];
        int r1 = square [1];
        int c2 = square [2];
        int r2 = square [3];
        for (int [] corner : new int [][] {{c1, r1}, {c2, r1}, {c1, r2}, {c2, r2}}) {
          int index = corner [1] * columns + corner [0];
          if (!kept [index] && !Float.isNaN(heights [index])) {
            kept [index] = true;
            count [0]++;
          }
        }
        if ((c2 - c1 <= 1 && r2 - r1 <= 1) || count [0] >= maxPoints) {
          continue;
        }
        // The largest gap between the heights and the surface of the corners
        float h11 = heights [r1 * columns + c1];
        float h21 = heights [r1 * columns + c2];
        float h12 = heights [r2 * columns + c1];
        float h22 = heights [r2 * columns + c2];
        float worst = 0;
        int stepColumn = Math.max(1, (c2 - c1) / 8);
        int stepRow = Math.max(1, (r2 - r1) / 8);
        for (int row = r1; row <= r2; row += stepRow) {
          for (int column = c1; column <= c2; column += stepColumn) {
            float u = c2 > c1 ? (float)(column - c1) / (c2 - c1) : 0;
            float v = r2 > r1 ? (float)(row - r1) / (r2 - r1) : 0;
            float plane = (h11 * (1 - u) + h21 * u) * (1 - v) + (h12 * (1 - u) + h22 * u) * v;
            float gap = Math.abs(heights [row * columns + column] - plane);
            if (gap > worst) {
              worst = gap;
            }
          }
        }
        if (worst > maxError || Float.isNaN(worst)) {
          int cm = (c1 + c2) / 2;
          int rm = (r1 + r2) / 2;
          if (c2 - c1 > 1 && r2 - r1 > 1) {
            next.add(new int [] {c1, r1, cm, rm});
            next.add(new int [] {cm, r1, c2, rm});
            next.add(new int [] {c1, rm, cm, r2});
            next.add(new int [] {cm, rm, c2, r2});
          } else if (c2 - c1 > 1) {
            next.add(new int [] {c1, r1, cm, r2});
            next.add(new int [] {cm, r1, c2, r2});
          } else {
            next.add(new int [] {c1, r1, c2, rm});
            next.add(new int [] {c1, rm, c2, r2});
          }
        }
      }
      queue = next;
    }
    List<float []> points = new ArrayList<float []>(count [0]);
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        if (kept [row * columns + column]) {
          points.add(new float [] {x0 + column * cellWidth, y0 + row * cellHeight, heights [row * columns + column]});
        }
      }
    }
    return points;
  }

  /**
   * Returns the points read from an Esri ASCII grid or from a text of x y z lines, in the plan.
   */
  private List<float []> readFile(File file, AssistantCommand command, int maxPoints, float maxError) throws IOException {
    String text = new String(AssistantGeneratedModel.readFile(file, MAX_FILE_LENGTH), "ISO-8859-1");
    String units = command.getString("units") != null ? command.getString("units").trim().toLowerCase(Locale.ENGLISH) : "m";
    String zUnits = command.getString("z_units") != null ? command.getString("z_units").trim().toLowerCase(Locale.ENGLISH) : units;
    final double scale = units.startsWith("cm") ? 1 : units.startsWith("f") || units.startsWith("us") ? 30.48 : 100;
    float zScale = zUnits.startsWith("cm") ? 1 : zUnits.startsWith("f") || zUnits.startsWith("us") ? 30.48f : 100;
    HomeGeoreference.Projection projection = null;
    HomeGeoreference georeference = null;
    if (command.getString("srs") != null) {
      projection = HomeGeoreference.Projection.get(command.getString("srs"));
      georeference = HomeGeoreference.get(this.home);
      if (georeference == null) {
        throw new IOException("\"srs\" needs the georeference of the home: set_georeference gives it. Without it, give the plan "
            + "place of the lower left corner of the file with \"x\" and \"y\"");
      }
    }
    double originX = command.getDouble("x", 0);
    double originY = command.getDouble("y", 0);
    String start = text.substring(0, Math.min(200, text.length())).trim().toLowerCase(Locale.ENGLISH);
    if (start.startsWith("ncols")) {
      // Esri ASCII grid: a header then the rows from the north to the south
      String [] tokens = text.trim().split("\\s+");
      Map<String, Double> header = new HashMap<String, Double>();
      int index = 0;
      while (index + 1 < tokens.length && Character.isLetter(tokens [index].charAt(0))) {
        try {
          header.put(tokens [index].toLowerCase(Locale.ENGLISH), Double.valueOf(tokens [index + 1]));
        } catch (NumberFormatException ex) {
          throw new IOException("the header of the grid can't be read at \"" + tokens [index] + "\"");
        }
        index += 2;
      }
      if (header.get("ncols") == null || header.get("nrows") == null || header.get("cellsize") == null) {
        throw new IOException("the grid has no ncols, nrows or cellsize");
      }
      int columns = header.get("ncols").intValue();
      int rows = header.get("nrows").intValue();
      double cell = header.get("cellsize");
      double left = header.get("xllcorner") != null ? header.get("xllcorner") + cell / 2
          : header.get("xllcenter") != null ? header.get("xllcenter") : 0;
      double bottom = header.get("yllcorner") != null ? header.get("yllcorner") + cell / 2
          : header.get("yllcenter") != null ? header.get("yllcenter") : 0;
      double noData = header.get("nodata_value") != null ? header.get("nodata_value") : -9999;
      if (columns < 2 || rows < 2 || (long)columns * rows > 40000000L || tokens.length - index < columns * rows) {
        throw new IOException("the grid gives " + (tokens.length - index) + " heights for " + columns + " x " + rows);
      }
      float [] heights = new float [columns * rows];
      for (int i = 0; i < heights.length; i++) {
        try {
          double value = Double.parseDouble(tokens [index + i]);
          heights [i] = value == noData ? Float.NaN : (float)(value * zScale);
        } catch (NumberFormatException ex) {
          heights [i] = Float.NaN;
        }
      }
      // Its place among the heights, then in the plan
      List<float []> gridPoints = decimate(heights, columns, rows, 0, 0, 1, 1, maxPoints, maxError);
      List<float []> points = new ArrayList<float []>(gridPoints.size());
      for (float [] point : gridPoints) {
        double east = left + point [0] * cell;
        double north = bottom + (rows - 1 - point [1]) * cell;
        points.add(toPlan(east, north, point [2], projection, georeference, scale, originX, originY, left - cell / 2, bottom - cell / 2));
      }
      return points;
    }
    // Lines of x y z
    List<double []> values = new ArrayList<double []>();
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (String line : text.split("\\r?\\n")) {
      String [] cells = line.trim().split("[\\s,;]+");
      if (cells.length < 3) {
        continue;
      }
      try {
        double [] value = {Double.parseDouble(cells [0]), Double.parseDouble(cells [1]), Double.parseDouble(cells [2])};
        values.add(value);
        minX = Math.min(minX, value [0]);
        minY = Math.min(minY, value [1]);
        maxX = Math.max(maxX, value [0]);
        maxY = Math.max(maxY, value [1]);
      } catch (NumberFormatException ex) {
        // A header line
      }
    }
    if (values.size() < 3) {
      throw new IOException("the file isn't an Esri ASCII grid (it doesn't start with ncols) and has no line of three numbers x y z");
    }
    // Thinned by the mean height of the points of each cell of a grid
    int side = (int)Math.max(2, Math.sqrt(maxPoints));
    Map<Long, double []> cells = new LinkedHashMap<Long, double []>();
    for (double [] value : values) {
      long column = (long)Math.min(side - 1, (value [0] - minX) / Math.max(1E-9, maxX - minX) * side);
      long row = (long)Math.min(side - 1, (value [1] - minY) / Math.max(1E-9, maxY - minY) * side);
      long key = values.size() <= maxPoints ? cells.size() : row * side + column;
      double [] sum = cells.get(key);
      if (sum == null) {
        cells.put(key, sum = new double [4]);
      }
      sum [0] += value [0];
      sum [1] += value [1];
      sum [2] += value [2];
      sum [3]++;
    }
    List<float []> points = new ArrayList<float []>(cells.size());
    for (double [] sum : cells.values()) {
      points.add(toPlan(sum [0] / sum [3], sum [1] / sum [3], (float)(sum [2] / sum [3] * zScale), projection, georeference, scale,
          originX, originY, minX, minY));
    }
    return points;
  }

  /**
   * Returns the point of the plan of a place of a file: east and north in its system, or in its units from its corner.
   */
  private static float [] toPlan(double east, double north, float height, HomeGeoreference.Projection projection,
                                 HomeGeoreference georeference, double scale, double originX, double originY,
                                 double left, double bottom) {
    if (projection != null) {
      double [] place = projection.toLatitudeLongitude(east, north);
      double [] plan = georeference.toPlan(place [0], place [1]);
      return new float [] {(float)plan [0], (float)plan [1], height};
    }
    // The north of a file is up the plan
    return new float [] {(float)(originX + (east - left) * scale), (float)(originY - (north - bottom) * scale), height};
  }

  // ---- Grading ----

  private void shiftTerrain(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    List<float []> points = getPoints(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY));
    float offset = getDatumOffset(command, points);
    if (offset == 0) {
      throw new IOException("give \"offset\":N cm, or \"pin\":{\"x\":N,\"y\":N,\"height\":N}");
    }
    for (float [] point : points) {
      point [2] += offset;
    }
    // A ground moved as a whole is the same ground
    String baseline = this.home.getProperty(BASELINE_PROPERTY);
    String result = regrade(terrain, points, tally, notes);
    if (baseline == null) {
      this.home.setProperty(BASELINE_PROPERTY, null);
    } else {
      List<float []> baselinePoints = getPoints(baseline);
      StringBuilder spec = new StringBuilder("[");
      for (float [] point : baselinePoints) {
        spec.append(spec.length() > 1 ? "," : "").append('[').append(format(point [0])).append(',').append(format(point [1]))
            .append(',').append(format(point [2] + offset)).append(']');
      }
      this.home.setProperty(BASELINE_PROPERTY, spec.append(']').toString());
    }
    notes.add("Moved the ground by " + format(offset) + " cm: " + result);
  }

  private void addPad(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    float [][] polygon = getShape(command, "points", 3);
    float blend = (float)Math.max(0, Math.min(5000, command.getDouble("blend_distance", 200)));
    boolean smooth = !"linear".equalsIgnoreCase(command.getString("blend_curve"));
    float [] bounds = getBounds(polygon);
    float centerX = (bounds [0] + bounds [2]) / 2;
    float centerY = (bounds [1] + bounds [3]) / 2;
    double area = Math.abs(getArea(polygon));
    if (area < 100) {
      throw new IOException("the points enclose no area");
    }
    float step = (float)Math.max(50, Math.min(400, Math.sqrt(area) / 12));
    List<float []> boundary = densify(polygon, true, step);
    // Its height: given, or the mean of the ground under it
    double mean = 0;
    List<float []> inner = getInnerPoints(polygon, (float)Math.max(80, Math.min(1000, Math.sqrt(area / 300))));
    List<float []> samples = new ArrayList<float []>(boundary);
    samples.addAll(inner);
    for (float [] sample : samples) {
      mean += getHeight(sample [0], sample [1]) / samples.size();
    }
    float height = (float)command.getDouble("height", command.getDouble("elevation", mean));
    float slopeX = 0;
    float slopeY = 0;
    if (command.getParameters().get("slope") instanceof Map && !"flatten_area".equals(action)) {
      Map<?, ?> slope = (Map<?, ?>)command.getParameters().get("slope");
      float percent = AssistantFurnitureBuilder.getNumber(slope, "percent", 0);
      double direction = Math.toRadians(AssistantFurnitureBuilder.getNumber(slope, "dir", AssistantFurnitureBuilder.getNumber(slope, "direction", 180)));
      // The height lost by cm towards the downhill direction: 0 is up the plan
      slopeX = (float)(Math.sin(direction) * percent / 100);
      slopeY = (float)(-Math.cos(direction) * percent / 100);
    }
    List<float []> points = keepPoints(getPoints(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY)), polygon, true, blend + 1);
    for (float [] sample : samples) {
      points.add(new float [] {sample [0], sample [1], height - (sample [0] - centerX) * slopeX - (sample [1] - centerY) * slopeY});
    }
    if (blend > 0) {
      // Around it, the ground comes back to what it was
      float outside = getArea(polygon) > 0 ? 1 : -1;
      for (float ratio : new float [] {0.34f, 0.67f, 1}) {
        float [][] ring = AssistantFenceCommands.offsetLine(polygon, true, blend * ratio * outside);
        for (float [] ringPoint : densify(ring, true, step)) {
          // The pad at the closest point of its side
          double nearest = Double.MAX_VALUE;
          float padHeight = height;
          for (int i = 0; i < polygon.length; i++) {
            float [] next = polygon [(i + 1) % polygon.length];
            double distance = Line2D.ptSegDist(polygon [i][0], polygon [i][1], next [0], next [1], ringPoint [0], ringPoint [1]);
            if (distance < nearest) {
              nearest = distance;
              double length = Math.hypot(next [0] - polygon [i][0], next [1] - polygon [i][1]);
              double along = length > 0 ? Math.max(0, Math.min(1, ((ringPoint [0] - polygon [i][0]) * (next [0] - polygon [i][0])
                  + (ringPoint [1] - polygon [i][1]) * (next [1] - polygon [i][1])) / (length * length))) : 0;
              float sideX = (float)(polygon [i][0] + (next [0] - polygon [i][0]) * along);
              float sideY = (float)(polygon [i][1] + (next [1] - polygon [i][1]) * along);
              padHeight = height - (sideX - centerX) * slopeX - (sideY - centerY) * slopeY;
            }
          }
          float t = (float)Math.max(0, Math.min(1, nearest / blend));
          float curve = smooth ? t * t * (3 - 2 * t) : t;
          points.add(new float [] {ringPoint [0], ringPoint [1],
              padHeight * (1 - curve) + getHeight(ringPoint [0], ringPoint [1]) * curve});
        }
      }
    }
    String result = regradeAround(terrain, points, polygon, blend + 50, tally, notes);
    notes.add(("flatten_area".equals(action) ? "Flattened " : "Added a pad of ") + format(area / 10000) + " m2 at the height "
        + format(height) + " cm" + (slopeX != 0 || slopeY != 0 ? ", sloped" : "") + ", the ground joining it over " + format(blend)
        + " cm: " + result);
  }

  private void smoothArea(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    float [][] polygon = getShape(command, "points", 3);
    float radius = (float)Math.max(20, Math.min(5000, command.getDouble("radius", 300)));
    float strength = (float)Math.max(0, Math.min(1, command.getDouble("strength", 1)));
    double area = Math.abs(getArea(polygon));
    List<float []> points = new ArrayList<float []>();
    List<float []> changed = new ArrayList<float []>();
    for (float [] point : getPoints(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY))) {
      if (AssistantRoomFurnisher.contains(polygon, point [0], point [1])) {
        changed.add(point);
      } else {
        points.add(point);
      }
    }
    for (float [] inner : getInnerPoints(polygon, (float)Math.max(80, Math.min(1000, Math.sqrt(area / 400))))) {
      changed.add(new float [] {inner [0], inner [1], getHeight(inner [0], inner [1])});
    }
    float worst = 0;
    for (float [] point : changed) {
      double sum = point [2];
      int count = 1;
      for (int ring = 1; ring <= 2; ring++) {
        for (int i = 0; i < 8; i++) {
          double angle = Math.PI * 2 * i / 8;
          sum += getHeight(point [0] + (float)Math.cos(angle) * radius * ring / 2, point [1] + (float)Math.sin(angle) * radius * ring / 2);
          count++;
        }
      }
      float smoothed = (float)(point [2] * (1 - strength) + sum / count * strength);
      worst = Math.max(worst, Math.abs(smoothed - point [2]));
      points.add(new float [] {point [0], point [1], smoothed});
    }
    String result = regradeAround(terrain, points, polygon, 50, tally, notes);
    notes.add("Smoothed " + format(area / 10000) + " m2 of ground, by up to " + format(worst) + " cm: " + result);
  }

  private void addBerm(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    float [][] line = getShape(command, "points", 2);
    boolean swale = "add_swale".equals(action);
    float size = (float)command.getDouble(swale ? "depth" : "height", swale ? 30 : 60);
    float width = (float)Math.max(20, Math.min(5000, command.getDouble("width", swale ? 200 : 300)));
    if (!(size > 0 && size <= 2000)) {
      throw new IOException("\"" + (swale ? "depth" : "height") + "\" goes from 0 to 2000 cm");
    }
    float step = Math.max(50, width / 2);
    List<float []> points = keepPoints(getPoints(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY)), line, false, width / 2 + 1);
    // Sections across the line: its middle moved, its edges left
    for (float [] offset : new float [][] {{0, 1}, {0.2f, 0.65f}, {-0.2f, 0.65f}, {0.35f, 0.2f}, {-0.35f, 0.2f}, {0.5f, 0}, {-0.5f, 0}}) {
      float [][] side = offset [0] == 0 ? line : AssistantFenceCommands.offsetLine(line, false, width * offset [0]);
      for (float [] point : densify(side, false, step)) {
        points.add(new float [] {point [0], point [1], getHeight(point [0], point [1]) + (swale ? -size : size) * offset [1]});
      }
    }
    // Its ends come back to the ground
    for (int end = 0; end < 2; end++) {
      float [] tip = end == 0 ? line [0] : line [line.length - 1];
      float [] neighbor = end == 0 ? line [1] : line [line.length - 2];
      double length = Math.hypot(tip [0] - neighbor [0], tip [1] - neighbor [1]);
      float x = (float)(tip [0] + (tip [0] - neighbor [0]) / length * width / 2);
      float y = (float)(tip [1] + (tip [1] - neighbor [1]) / length * width / 2);
      points.add(new float [] {x, y, getHeight(x, y)});
    }
    String result = regradeAround(terrain, points, line, width, tally, notes);
    double length = 0;
    for (int i = 0; i < line.length - 1; i++) {
      length += Math.hypot(line [i + 1][0] - line [i][0], line [i + 1][1] - line [i][1]);
    }
    notes.add((swale ? "Dug a swale " + format(size) + " cm deep" : "Raised a berm " + format(size) + " cm high") + " and "
        + format(width) + " cm wide along " + format(length / 100) + " m: " + result);
  }

  private void gradePath(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    float [][] line = getShape(command, "points", 2);
    float width = (float)Math.max(30, Math.min(5000, command.getDouble("width", 300)));
    float blend = (float)Math.max(0, Math.min(5000, command.getDouble("blend_distance", 150)));
    float crossSlope = (float)command.getDouble("cross_slope", 0) / 100;
    float crown = (float)command.getDouble("crown", 0);
    float ditchDepth = 0;
    float ditchWidth = 0;
    if (command.getParameters().get("ditch") instanceof Map) {
      ditchDepth = AssistantFurnitureBuilder.getNumber((Map<?, ?>)command.getParameters().get("ditch"), "depth", 30);
      ditchWidth = AssistantFurnitureBuilder.getNumber((Map<?, ?>)command.getParameters().get("ditch"), "width", 100);
    }
    String curb = command.getString("curb_type");
    // The height of the center line at its points
    float [] heights = new float [line.length];
    List<String> given = command.getStringList("heights");
    for (int i = 0; i < line.length; i++) {
      heights [i] = getHeight(line [i][0], line [i][1]);
      if (i < given.size()) {
        try {
          heights [i] = Float.parseFloat(given.get(i));
        } catch (NumberFormatException ex) {
          // The height of the ground
        }
      }
    }
    double [] distances = AssistantFenceBuilder.getDistances(line, false);
    double length = distances [distances.length - 1];
    float reach = width / 2 + ditchWidth + blend;
    List<float []> points = keepPoints(getPoints(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY)), line, false, reach + 1);
    float step = Math.max(50, Math.min(300, width));
    int stations = Math.max(1, (int)Math.ceil(length / step));
    float steepest = 0;
    for (int i = 0; i <= stations; i++) {
      double distance = length * i / stations;
      int side = AssistantFenceBuilder.getSide(distances, distance);
      double sideLength = distances [side + 1] - distances [side];
      double ratio = sideLength > 0 ? Math.max(0, Math.min(1, (distance - distances [side]) / sideLength)) : 0;
      float [] center = AssistantFenceBuilder.getPoint(line, distances, distance);
      float height = (float)(heights [side] + (heights [side + 1] - heights [side]) * ratio);
      if (sideLength > 0) {
        steepest = Math.max(steepest, (float)(Math.abs(heights [side + 1] - heights [side]) / sideLength * 100));
      }
      float dx = (float)((line [side + 1][0] - line [side][0]) / Math.max(1E-6, sideLength));
      float dy = (float)((line [side + 1][1] - line [side][1]) / Math.max(1E-6, sideLength));
      // To the right of the direction walked
      float nx = -dy;
      float ny = dx;
      points.add(new float [] {center [0], center [1], height + crown});
      for (int sign = -1; sign <= 1; sign += 2) {
        float edgeHeight = height - sign * crossSlope * width / 2;
        float ex = center [0] + nx * sign * width / 2;
        float ey = center [1] + ny * sign * width / 2;
        points.add(new float [] {ex, ey, edgeHeight});
        float beyond = width / 2;
        if (ditchDepth > 0 && ditchWidth > 0) {
          points.add(new float [] {center [0] + nx * sign * (beyond + ditchWidth / 2), center [1] + ny * sign * (beyond + ditchWidth / 2),
              edgeHeight - ditchDepth});
          beyond += ditchWidth;
          float bx = center [0] + nx * sign * beyond;
          float by = center [1] + ny * sign * beyond;
          points.add(new float [] {bx, by, blend > 0 ? edgeHeight : getHeight(bx, by)});
        }
        if (blend > 0) {
          float bx = center [0] + nx * sign * (beyond + blend);
          float by = center [1] + ny * sign * (beyond + blend);
          points.add(new float [] {bx, by, getHeight(bx, by)});
        }
      }
    }
    String result = regradeAround(terrain, points, line, reach, tally, notes);
    notes.add("Graded " + format(length / 100) + " m of ground " + format(width) + " cm wide, its steepest part at "
        + format(steepest) + " %" + (crossSlope != 0 ? ", with a cross slope of " + format(crossSlope * 100) + " %" : "")
        + (ditchDepth > 0 ? ", a ditch on both sides" : "") + ": " + result
        + (curb != null && !curb.equalsIgnoreCase("none") ? ". Curbs aren't built by grade_path: add_path with \"edging\":true lays them" : ""));
  }

  private void extendTerrain(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    float margin = (float)Math.max(0, Math.min(100000, command.getDouble("margin", 500)));
    List<float []> points = getPoints(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY));
    float [] ground = getBounds(points.toArray(new float [points.size()][]));
    float [] bounds = ground.clone();
    List<float [][]> shapes = new ArrayList<float [][]>();
    for (Wall wall : this.home.getWalls()) {
      shapes.add(wall.getPoints());
    }
    for (Room room : this.home.getRooms()) {
      shapes.add(room.getPoints());
    }
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (!HomeTerrain.isTerrain(piece)) {
        shapes.add(piece.getPoints());
      }
    }
    for (Polyline polyline : this.home.getPolylines()) {
      if (!"contour".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))) {
        shapes.add(polyline.getPoints());
      }
    }
    if (command.getParameters().get("area") instanceof List) {
      shapes.add(getShape(command, "area", 2));
    }
    for (float [][] shape : shapes) {
      for (float [] corner : shape) {
        bounds [0] = Math.min(bounds [0], corner [0] - margin);
        bounds [1] = Math.min(bounds [1], corner [1] - margin);
        bounds [2] = Math.max(bounds [2], corner [0] + margin);
        bounds [3] = Math.max(bounds [3], corner [1] + margin);
      }
    }
    if (bounds [0] >= ground [0] - 1 && bounds [1] >= ground [1] - 1 && bounds [2] <= ground [2] + 1 && bounds [3] <= ground [3] + 1) {
      notes.add("The ground already covers every item of the home with a margin of " + format(margin) + " cm: nothing to extend");
      return;
    }
    // Points around the new edge, at the height of the closest edge of the ground
    float step = Math.max(200, Math.max(bounds [2] - bounds [0], bounds [3] - bounds [1]) / 24);
    float [][] edge = {{bounds [0], bounds [1]}, {bounds [2], bounds [1]}, {bounds [2], bounds [3]}, {bounds [0], bounds [3]}};
    int added = 0;
    for (float [] point : densify(edge, true, step)) {
      if (this.executor.getShapeCommands().getTerrainHeight(point [0], point [1]) == null) {
        points.add(new float [] {point [0], point [1], getHeight(point [0], point [1])});
        added++;
      }
    }
    String result = regrade(terrain, points, tally, notes);
    notes.add("Extended the ground to " + format((bounds [2] - bounds [0]) / 100) + " x " + format((bounds [3] - bounds [1]) / 100)
        + " m, from (" + format(bounds [0]) + ", " + format(bounds [1]) + ") to (" + format(bounds [2]) + ", " + format(bounds [3])
        + "), with " + added + " points at the height of its closest edge - no height is fetched for the new part: " + result);
  }

  // ---- Measures ----

  private void cutFillReport(AssistantCommand command, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    if (command.getBoolean("reset", false)) {
      this.home.setProperty(BASELINE_PROPERTY, null);
      notes.add("The ground as it is now is the original one for the next cut_fill_report");
      return;
    }
    String baseline = this.home.getProperty(BASELINE_PROPERTY);
    if (baseline == null) {
      notes.add("The ground wasn't graded since it was built: nothing was cut or filled. add_pad, grade_path, add_berm, "
          + "add_swale, flatten_area and smooth_area grade it");
      return;
    }
    Surface before = new Surface(HomeTerrain.getTriangles(baseline));
    Surface after = new Surface(HomeTerrain.getTriangles(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY)));
    float [][] polygon = command.getParameters().get("area") instanceof List ? getShape(command, "area", 3) : null;
    float [] bounds = polygon != null ? getBounds(polygon)
        : new float [] {Math.min(before.bounds [0], after.bounds [0]), Math.min(before.bounds [1], after.bounds [1]),
                        Math.max(before.bounds [2], after.bounds [2]), Math.max(before.bounds [3], after.bounds [3])};
    double [] volumes = getVolumes(before, after, bounds, polygon);
    notes.add("Earth moved since the ground was first graded, over " + format(volumes [2]) + " m2"
        + (polygon != null ? " of the area given" : "") + ": " + describeVolumes(volumes)
        + ". {\"action\":\"cut_fill_report\",\"reset\":true} takes the ground as it is for the original one");
  }

  private void contours(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<Selectable> old = new ArrayList<Selectable>();
    for (Polyline polyline : this.home.getPolylines()) {
      if ("contour".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))) {
        old.add(polyline);
      }
    }
    for (Label label : this.home.getLabels()) {
      if ("contour".equals(label.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))) {
        old.add(label);
      }
    }
    if (command.getBoolean("remove", false)) {
      if (!old.isEmpty()) {
        this.executor.beginTurnUpdate();
        this.homeController.getPlanController().deleteItems(old);
        tally [5] += old.size();
      }
      notes.add(old.isEmpty() ? "The plan has no contour line" : "Removed the contour lines and their labels");
      return;
    }
    HomePieceOfFurniture terrain = getTerrain();
    List<float [][]> triangles = HomeTerrain.getTriangles(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY));
    float lowest = Float.MAX_VALUE;
    float highest = -Float.MAX_VALUE;
    for (float [][] triangle : triangles) {
      for (float [] corner : triangle) {
        lowest = Math.min(lowest, corner [2]);
        highest = Math.max(highest, corner [2]);
      }
    }
    float interval = (float)command.getDouble("interval", Math.max(10, Math.round((highest - lowest) / 12 / 10) * 10));
    if (!(interval >= 1) || (highest - lowest) / interval > 400) {
      throw new IOException("\"interval\" is in cm, large enough for 400 lines at most: the ground goes from " + format(lowest)
          + " to " + format(highest) + " cm");
    }
    float [][] polygon = command.getParameters().get("area") instanceof List ? getShape(command, "area", 3) : null;
    boolean labels = command.getBoolean("labels", true);
    Level level = this.executor.getShapeCommands().getLevel(command, "contours", notes);
    List<Polyline> polylines = new ArrayList<Polyline>();
    List<Label> newLabels = new ArrayList<Label>();
    int lineCount = 0;
    for (int n = (int)Math.ceil((lowest + 0.01f) / interval); n * interval < highest; n++) {
      // A little off the heights of the points, for a line not to follow the sides of triangles
      float height = n * interval + 0.013f;
      boolean index = n % 5 == 0;
      // The segment of each triangle at this height, joined end to end
      Map<String, List<float [][]>> ends = new HashMap<String, List<float [][]>>();
      List<float [][]> segments = new ArrayList<float [][]>();
      for (float [][] triangle : triangles) {
        List<float []> crossings = new ArrayList<float []>(2);
        for (int i = 0; i < 3; i++) {
          float [] a = triangle [i];
          float [] b = triangle [(i + 1) % 3];
          if (a [2] < height != b [2] < height) {
            float t = (height - a [2]) / (b [2] - a [2]);
            crossings.add(new float [] {a [0] + (b [0] - a [0]) * t, a [1] + (b [1] - a [1]) * t});
          }
        }
        if (crossings.size() == 2) {
          float [][] segment = {crossings.get(0), crossings.get(1)};
          float middleX = (segment [0][0] + segment [1][0]) / 2;
          float middleY = (segment [0][1] + segment [1][1]) / 2;
          if (polygon != null && !AssistantRoomFurnisher.contains(polygon, middleX, middleY)) {
            continue;
          }
          segments.add(segment);
          for (float [] end : segment) {
            String key = Math.round(end [0] * 10) + ":" + Math.round(end [1] * 10);
            List<float [][]> list = ends.get(key);
            if (list == null) {
              ends.put(key, list = new ArrayList<float [][]>(2));
            }
            list.add(segment);
          }
        }
      }
      java.util.Set<float [][]> used = new java.util.HashSet<float [][]>();
      for (float [][] first : segments) {
        if (used.contains(first)) {
          continue;
        }
        used.add(first);
        java.util.LinkedList<float []> chain = new java.util.LinkedList<float []>();
        chain.add(first [0]);
        chain.add(first [1]);
        for (int direction = 0; direction < 2; direction++) {
          boolean grown = true;
          while (grown) {
            grown = false;
            float [] tip = direction == 0 ? chain.getLast() : chain.getFirst();
            List<float [][]> candidates = ends.get(Math.round(tip [0] * 10) + ":" + Math.round(tip [1] * 10));
            if (candidates != null) {
              for (float [][] candidate : candidates) {
                if (!used.contains(candidate)) {
                  used.add(candidate);
                  boolean startAtTip = Math.round(candidate [0][0] * 10) == Math.round(tip [0] * 10)
                      && Math.round(candidate [0][1] * 10) == Math.round(tip [1] * 10);
                  float [] next = startAtTip ? candidate [1] : candidate [0];
                  if (direction == 0) {
                    chain.addLast(next);
                  } else {
                    chain.addFirst(next);
                  }
                  grown = true;
                  break;
                }
              }
            }
          }
        }
        // Without the points in line with their neighbors
        List<float []> simple = new ArrayList<float []>(chain);
        for (int i = simple.size() - 2; i >= 1; i--) {
          if (Line2D.ptSegDist(simple.get(i - 1) [0], simple.get(i - 1) [1], simple.get(i + 1) [0], simple.get(i + 1) [1],
                  simple.get(i) [0], simple.get(i) [1]) < 3) {
            simple.remove(i);
          }
        }
        double length = 0;
        for (int i = 0; i < simple.size() - 1; i++) {
          length += Math.hypot(simple.get(i + 1) [0] - simple.get(i) [0], simple.get(i + 1) [1] - simple.get(i) [1]);
        }
        if (length < 50 || polylines.size() >= 1500) {
          continue;
        }
        String name = "Contour " + format(n * interval);
        Polyline polyline = this.executor.getSiteCommands().createPolyline(simple.toArray(new float [simple.size()][]), false, level,
            name, "contour", command.getString("color") != null ? command.getString("color") : "#8A6A3A", index ? 2.5 : 1, null, false,
            Double.NaN);
        polylines.add(polyline);
        if (index && labels && length > 300 && newLabels.size() < 300) {
          float [] middle = simple.get(simple.size() / 2);
          Label label = new Label(format(n * interval), middle [0], middle [1]);
          label.setLevel(level);
          label.setProperty(AssistantSiteCommands.LINE_KIND_PROPERTY, "contour");
          newLabels.add(label);
        }
      }
      lineCount++;
    }
    this.executor.beginTurnUpdate();
    if (!old.isEmpty()) {
      this.homeController.getPlanController().deleteItems(old);
    }
    if (!polylines.isEmpty()) {
      this.executor.getSiteCommands().addPolylines(polylines, level);
    }
    if (!newLabels.isEmpty()) {
      this.homeController.getPlanController().addLabels(newLabels);
    }
    notes.add("Drew " + polylines.size() + " contour lines at " + lineCount + " heights, every " + format(interval) + " cm from "
        + format(lowest) + " to " + format(highest) + " cm" + (newLabels.isEmpty() ? "" : ", with " + newLabels.size() + " labels in cm")
        + (old.isEmpty() ? "" : ", in place of the ones before") + ". They don't follow the ground when it changes: run contours again. "
        + "{\"action\":\"contours\",\"remove\":true} deletes them");
  }

  private void spotElevations(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    getTerrain();
    float [][] places = getShape(command, "points", 1);
    boolean labels = command.getBoolean("labels", true);
    Level level = this.executor.getShapeCommands().getLevel(command, "spot_elevations", notes);
    List<Label> newLabels = new ArrayList<Label>();
    StringBuilder text = new StringBuilder("Height of the ground in cm: ");
    for (int i = 0; i < places.length && i < 500; i++) {
      Float height = this.executor.getShapeCommands().getTerrainHeight(places [i][0], places [i][1]);
      text.append(i > 0 ? "; " : "").append('(').append(format(places [i][0])).append(", ").append(format(places [i][1])).append(") ")
          .append(height != null ? format(height) : "out of the ground");
      if (height != null && labels) {
        Label label = new Label("+ " + format(height), places [i][0], places [i][1]);
        label.setLevel(level);
        label.setProperty(AssistantSiteCommands.LINE_KIND_PROPERTY, "spot_elevation");
        newLabels.add(label);
      }
    }
    if (!newLabels.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().addLabels(newLabels);
    }
    notes.add(text + (newLabels.isEmpty() ? "" : ". " + newLabels.size() + " labels were added to the plan"));
  }

  private void slopeMap(AssistantCommand command, List<String> notes) throws IOException {
    HomePieceOfFurniture terrain = getTerrain();
    List<float [][]> triangles = HomeTerrain.getTriangles(terrain.getProperty(AssistantShapeCommands.TERRAIN_PROPERTY));
    float [][] polygon = command.getParameters().get("area") instanceof List ? getShape(command, "area", 3) : null;
    float threshold = (float)command.getDouble("threshold", 25);
    float [] limits = {2, 5, 8.33f, 15, 25, Float.MAX_VALUE};
    String [] names = {"flat, under 2 % (water stands)", "2 to 5 % (drains, easy to walk and to mow)",
        "5 to 8.33 % (the steepest ramp of an accessible route)", "8.33 to 15 % (steep to walk, steps help)",
        "15 to 25 % (hard to mow, erodes)", "over 25 % (needs a wall or a planted bank)"};
    double [] areas = new double [limits.length];
    double total = 0;
    float steepest = 0;
    float [] steepestPlace = null;
    List<String> steepPlaces = new ArrayList<String>();
    for (float [][] triangle : triangles) {
      float x = (triangle [0][0] + triangle [1][0] + triangle [2][0]) / 3;
      float y = (triangle [0][1] + triangle [1][1] + triangle [2][1]) / 3;
      if (polygon != null && !AssistantRoomFurnisher.contains(polygon, x, y)) {
        continue;
      }
      // The normal of the triangle gives its slope
      double ux = triangle [1][0] - triangle [0][0];
      double uy = triangle [1][1] - triangle [0][1];
      double uz = triangle [1][2] - triangle [0][2];
      double vx = triangle [2][0] - triangle [0][0];
      double vy = triangle [2][1] - triangle [0][1];
      double vz = triangle [2][2] - triangle [0][2];
      double nx = uy * vz - uz * vy;
      double ny = uz * vx - ux * vz;
      double nz = ux * vy - uy * vx;
      if (Math.abs(nz) < 1E-6) {
        continue;
      }
      float slope = (float)(Math.hypot(nx, ny) / Math.abs(nz) * 100);
      double area = Math.abs(nz) / 2 / 10000;
      total += area;
      for (int i = 0; i < limits.length; i++) {
        if (slope < limits [i]) {
          areas [i] += area;
          break;
        }
      }
      if (slope > steepest && area > 0.25) {
        steepest = slope;
        steepestPlace = new float [] {x, y};
      }
      if (slope >= threshold && area > 0.2 && steepPlaces.size() < 12) {
        steepPlaces.add("(" + Math.round(x) + ", " + Math.round(y) + ") " + format(slope) + " %");
      }
    }
    if (total == 0) {
      throw new IOException("no ground in the area given");
    }
    StringBuilder text = new StringBuilder("Slopes of " + format(total) + " m2 of ground:");
    for (int i = 0; i < limits.length; i++) {
      if (areas [i] > 0) {
        text.append("\n- ").append(format(areas [i] / total * 100)).append(" % ").append(names [i]).append(": ")
            .append(format(areas [i])).append(" m2");
      }
    }
    if (steepestPlace != null) {
      text.append("\nSteepest: ").append(format(steepest)).append(" % near (").append(Math.round(steepestPlace [0])).append(", ")
          .append(Math.round(steepestPlace [1])).append(")");
    }
    if (!steepPlaces.isEmpty()) {
      text.append("\nPlaces at ").append(format(threshold)).append(" % or more: ").append(steepPlaces);
    }
    notes.add(text.toString());
  }
}
