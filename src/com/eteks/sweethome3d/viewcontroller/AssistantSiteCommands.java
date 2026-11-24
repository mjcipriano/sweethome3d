/*
 * AssistantSiteCommands.java 21 nov. 2026
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands of the design assistant about the site of a home: lines drawn in the plan
 * (property lines, setbacks, easements, utilities), the compass, the place of the home on
 * the Earth and the parcels of a survey.
 * @author Sweet Home 3D
 */
class AssistantSiteCommands {
  /** The name of a line drawn by these commands. */
  static final String LINE_NAME_PROPERTY = "assistantLineName";
  /** <code>property</code> for a property line, <code>setback</code> for a setback line. */
  static final String LINE_KIND_PROPERTY = "assistantLineKind";
  /** The distance in cm a setback line keeps from the line it follows. */
  static final String SETBACK_DISTANCE_PROPERTY = "assistantSetbackDistance";
  /** The id of the line a setback line follows. */
  static final String SETBACK_FROM_PROPERTY = "assistantSetbackFrom";

  private static final long MAX_FILE_LENGTH = 20L * 1024 * 1024;
  private static final int  MAX_LINE_POINTS = 2000;

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantSiteCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
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
      if ("add_polyline".equals(action) || "add_line".equals(action)) {
        addPolyline(command, null, notes);
      } else if ("add_property_line".equals(action)) {
        addPolyline(command, "property", notes);
      } else if ("add_setback".equals(action)) {
        addSetback(command, notes);
      } else if ("modify_polyline".equals(action) || "modify_line".equals(action)) {
        modifyPolyline(command, tally, notes);
      } else if ("set_compass".equals(action)) {
        setCompass(command, notes);
      } else if ("set_georeference".equals(action)) {
        setGeoreference(command, notes);
      } else if ("import_parcel".equals(action)) {
        importParcel(command, notes);
      } else if ("get_coordinates".equals(action) || "plan_to_geo".equals(action) || "export_coordinates".equals(action)) {
        getCoordinates(command, notes);
      } else if ("geo_to_plan".equals(action)) {
        geoToPlan(command, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    }
    return true;
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  // ---- Lines ----

  private static Polyline.DashStyle parseDash(String dash) throws IOException {
    if (dash == null) {
      return null;
    }
    String text = dash.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
    if (text.equals("solid") || text.equals("none")) {
      return Polyline.DashStyle.SOLID;
    } else if (text.equals("dashed") || text.equals("dash")) {
      return Polyline.DashStyle.DASH;
    } else if (text.equals("dotted") || text.equals("dot")) {
      return Polyline.DashStyle.DOT;
    } else if (text.equals("dash_dot")) {
      return Polyline.DashStyle.DASH_DOT;
    } else if (text.equals("dash_dot_dot")) {
      return Polyline.DashStyle.DASH_DOT_DOT;
    }
    throw new IOException("\"dash\" is solid, dashed, dotted, dash_dot or dash_dot_dot");
  }

  /**
   * Returns the points of a command: the ones of the plan in <code>points</code>, or the places
   * given by their longitude and latitude in <code>geo</code> when the home has a georeference.
   */
  private float [][] getPoints(AssistantCommand command) throws IOException {
    List<float []> points = AssistantFurnitureBuilder.getPoints(command.getParameters(), "points", 2);
    if (points.isEmpty() && command.getParameters().get("geo") instanceof List) {
      HomeGeoreference georeference = HomeGeoreference.get(this.home);
      if (georeference == null) {
        throw new IOException("\"geo\" gives longitudes and latitudes, which need {\"action\":\"set_georeference\"} first");
      }
      for (Object point : (List<?>)command.getParameters().get("geo")) {
        if (point instanceof List && ((List<?>)point).size() >= 2 && ((List<?>)point).get(0) instanceof Number
            && ((List<?>)point).get(1) instanceof Number) {
          double [] planPoint = georeference.toPlan(((Number)((List<?>)point).get(1)).doubleValue(),
              ((Number)((List<?>)point).get(0)).doubleValue());
          points.add(new float [] {(float)planPoint [0], (float)planPoint [1]});
        }
      }
    }
    if (points.size() < 2) {
      throw new IOException("\"points\" must give 2 points [x,y] at least (or \"geo\" 2 places [longitude,latitude])");
    } else if (points.size() > MAX_LINE_POINTS) {
      throw new IOException("a line has " + MAX_LINE_POINTS + " points at most");
    }
    for (float [] point : points) {
      if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
        throw new IOException("points out of range");
      }
    }
    return points.toArray(new float [points.size()][]);
  }

  /**
   * Adds a line to the plan: a polyline of Sweet Home 3D, not a piece of furniture.
   * @param kind <code>property</code> for a property line, or <code>null</code>
   */
  private Polyline addPolyline(AssistantCommand command, String kind, List<String> notes) throws IOException {
    float [][] points = getPoints(command);
    boolean closed = command.getBoolean("closed", kind != null && points.length >= 3);
    Level level = this.executor.getShapeCommands().getLevel(command, "property".equals(kind) ? "add_property_line" : "add_polyline", notes);
    String name = command.getString("name") != null ? command.getString("name")
        : command.getString("label") != null ? command.getString("label")
        : "property".equals(kind) ? "Property line" : null;
    Polyline polyline = createPolyline(points, closed, level, name, kind,
        command.getString("color"), command.getDouble("thickness", Double.NaN), command.getString("dash"),
        command.getBoolean("visible_in_3d", false), command.getDouble("elevation", Double.NaN));
    String arrows = command.getString("arrows");
    if (arrows != null) {
      String lowerArrows = arrows.toLowerCase(Locale.ENGLISH);
      if (lowerArrows.equals("start") || lowerArrows.equals("both")) {
        polyline.setStartArrowStyle(Polyline.ArrowStyle.DELTA);
      }
      if (lowerArrows.equals("end") || lowerArrows.equals("both")) {
        polyline.setEndArrowStyle(Polyline.ArrowStyle.DELTA);
      }
    }
    addPolylines(Collections.singletonList(polyline), level);
    String id = this.executor.getId(polyline);
    StringBuilder note = new StringBuilder("Added the " + ("property".equals(kind) ? "property line " : "line ") + id
        + (name != null ? " \"" + name + "\"" : "") + ": " + points.length + " points, "
        + format(getLength(points, closed) / 100) + " m long");
    if (closed && points.length >= 3) {
      note.append(", closed, ").append(describeArea(Math.abs(getSignedArea(points))));
    }
    // Its name written in the plan
    if (command.getString("label") != null && command.getString("label").trim().length() > 0) {
      float [] middle = {(points [0][0] + points [1][0]) / 2, (points [0][1] + points [1][1]) / 2};
      Label label = new Label(command.getString("label").trim(), middle [0], middle [1] - 20);
      label.setLevel(level);
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().addLabels(Collections.singletonList(label));
      note.append(", with its label");
    }
    if (command.getBoolean("stakes", false) || command.getBoolean("string_line", false)) {
      HomePieceOfFurniture stakes = addStakes(points, closed, level, name != null ? name : "Line",
          polyline.getColor(), (float)command.getDouble("stake_height", 90));
      note.append(", shown in 3D by the stakes and the string ").append(this.executor.getId(stakes))
          .append(", which follow the ground");
    }
    if ("property".equals(kind)) {
      note.append(". {\"action\":\"add_setback\",\"from\":\"").append(id)
          .append("\",\"distance\":N} draws the line buildings must keep behind, which check_home then verifies");
    }
    notes.add(note.toString());
    return polyline;
  }

  Polyline createPolyline(float [][] points, boolean closed, Level level, String name, String kind,
                                  String colorText, double thickness, String dash, boolean visibleIn3D,
                                  double elevation) throws IOException {
    Integer color = colorText != null ? AssistantCommandExecutor.parseColor(colorText) : null;
    if (colorText != null && color == null) {
      throw new IOException("\"color\" must be written #RRGGBB");
    }
    Polyline.DashStyle dashStyle = parseDash(dash);
    if (dashStyle == null) {
      dashStyle = "property".equals(kind) ? Polyline.DashStyle.DASH_DOT
          : "setback".equals(kind) ? Polyline.DashStyle.DASH : Polyline.DashStyle.SOLID;
    }
    if (color == null) {
      color = "property".equals(kind) ? 0xC0392B : "setback".equals(kind) ? 0xE67E22 : 0x000000;
    }
    if (Double.isNaN(thickness)) {
      thickness = kind != null ? 4 : 2;
    }
    if (!(thickness >= 0.1 && thickness <= 200)) {
      throw new IOException("\"thickness\" goes from 0.1 to 200 cm");
    }
    Polyline polyline = new Polyline(points, (float)thickness, Polyline.CapStyle.BUTT, Polyline.JoinStyle.MITER,
        dashStyle, Polyline.ArrowStyle.NONE, Polyline.ArrowStyle.NONE, closed, 0xFF000000 | color);
    if (visibleIn3D || !Double.isNaN(elevation)) {
      polyline.setVisibleIn3D(true);
      polyline.setElevation(Double.isNaN(elevation) ? 0 : (float)elevation);
    }
    polyline.setLevel(level);
    if (name != null && name.trim().length() > 0) {
      polyline.setProperty(LINE_NAME_PROPERTY, name.trim());
    }
    if (kind != null) {
      polyline.setProperty(LINE_KIND_PROPERTY, kind);
    }
    return polyline;
  }

  void addPolylines(List<Polyline> polylines, Level level) {
    this.executor.beginTurnUpdate();
    Level selectedLevel = this.home.getSelectedLevel();
    if (level != null && level != selectedLevel) {
      this.home.setSelectedLevel(level);
    }
    this.homeController.getPlanController().addPolylines(polylines);
    for (Polyline polyline : polylines) {
      if (polyline.getLevel() != level) {
        polyline.setLevel(level);
      }
    }
    if (this.home.getSelectedLevel() != selectedLevel && selectedLevel != null) {
      this.home.setSelectedLevel(selectedLevel);
    }
  }

  /**
   * Adds stakes at the points of a line and a string between them, which follow the ground.
   */
  private HomePieceOfFurniture addStakes(float [][] points, boolean closed, Level level, String name, int color,
                                         float stakeHeight) throws IOException {
    stakeHeight = Math.max(10, Math.min(500, stakeHeight));
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    float levelElevation = level != null ? level.getElevation() : 0;
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material stakeMaterial = mesh.getMaterial(0xB8894F);
    AssistantMeshBuilder.Material stringMaterial = mesh.getMaterial(color & 0xFFFFFF);
    int count = closed ? points.length : points.length - 1;
    for (int i = 0; i < points.length; i++) {
      float ground = shapeCommands.getStandingHeight(points [i][0], points [i][1]) - levelElevation;
      mesh.addBox(points [i][0] - 2.5f, ground, points [i][1] - 2.5f, points [i][0] + 2.5f, ground + stakeHeight + 8,
          points [i][1] + 2.5f, stakeMaterial);
    }
    for (int i = 0; i < count; i++) {
      float [] point = points [i];
      float [] next = points [(i + 1) % points.length];
      // By steps of a meter, to follow the slopes
      int steps = Math.max(1, Math.min(400, (int)Math.ceil(Math.hypot(next [0] - point [0], next [1] - point [1]) / 100)));
      float [] previous = null;
      for (int step = 0; step <= steps; step++) {
        float x = point [0] + (next [0] - point [0]) * step / steps;
        float y = point [1] + (next [1] - point [1]) * step / steps;
        float [] current = {x, shapeCommands.getStandingHeight(x, y) - levelElevation + stakeHeight, y};
        if (previous != null) {
          mesh.addBar(previous, current, 1.5f, 1.5f, stringMaterial);
        }
        previous = current;
      }
    }
    HomePieceOfFurniture piece = shapeCommands.createPiece(name + " stakes", mesh.store("stakes"), level, false, "site");
    piece.setProperty(HomeItemTags.TAGS_PROPERTY, "site");
    shapeCommands.add(piece);
    return piece;
  }

  /**
   * Returns the line a command designates by a parameter, or the only line of the given kind.
   */
  private Polyline getLine(AssistantCommand command, String kind, String... parameters) throws IOException {
    String token = null;
    for (String parameter : parameters) {
      if (command.getString(parameter) != null) {
        token = command.getString(parameter).trim();
        break;
      }
    }
    List<Polyline> candidates = new ArrayList<Polyline>();
    for (Polyline polyline : this.home.getPolylines()) {
      if (kind == null || kind.equals(polyline.getProperty(LINE_KIND_PROPERTY))) {
        candidates.add(polyline);
      }
    }
    if (token != null) {
      HomeObject item = HomeItemIds.find(this.home, token);
      if (item instanceof Polyline) {
        return (Polyline)item;
      }
      for (Polyline polyline : this.home.getPolylines()) {
        if (token.equalsIgnoreCase(polyline.getProperty(LINE_NAME_PROPERTY))) {
          return polyline;
        }
      }
      throw new IOException("no line has the id or the name \"" + token + "\"; lines: " + describeLines(this.home.getPolylines()));
    } else if (candidates.size() == 1) {
      return candidates.get(0);
    }
    throw new IOException(candidates.isEmpty()
        ? "there is no " + (kind != null ? kind + " " : "") + "line in this home"
        : "give the line with \"" + parameters [0] + "\":\"P#\"; lines: " + describeLines(candidates));
  }

  private String describeLines(List<Polyline> polylines) {
    StringBuilder text = new StringBuilder();
    for (Polyline polyline : polylines) {
      text.append(text.length() > 0 ? ", " : "").append(this.executor.getId(polyline));
      if (polyline.getProperty(LINE_NAME_PROPERTY) != null) {
        text.append(" \"").append(polyline.getProperty(LINE_NAME_PROPERTY)).append('"');
      }
    }
    return text.length() > 0 ? text.toString() : "none";
  }

  /**
   * Draws the line that keeps a distance from a closed line, inside or outside it.
   */
  private void addSetback(AssistantCommand command, List<String> notes) throws IOException {
    Polyline line = getLine(command, "property", "from", "line", "id");
    double distance = command.getDouble("distance", Double.NaN);
    if (!(distance > 0) || !AssistantCommandExecutor.sizeSane(distance)) {
      throw new IOException("\"distance\" must give the setback in cm");
    }
    if (!line.isClosedPath() || line.getPointCount() < 3) {
      throw new IOException("a setback is measured from a closed line of 3 points at least");
    }
    String side = command.getString("side");
    boolean inside = side == null || !side.trim().toLowerCase(Locale.ENGLISH).startsWith("out");
    float [][] points = getOffsetPolygon(line.getPoints(), inside ? distance : -distance);
    if (points == null) {
      throw new IOException("the line is too small for a setback of " + format(distance) + " cm");
    }
    String lineId = this.executor.getId(line);
    String name = command.getString("name") != null ? command.getString("name")
        : command.getString("label") != null ? command.getString("label")
        : "Setback " + format(distance / 100) + " m";
    Polyline setback = createPolyline(points, true, line.getLevel(), name, "setback",
        command.getString("color"), command.getDouble("thickness", Double.NaN), command.getString("dash"), false, Double.NaN);
    setback.setProperty(SETBACK_DISTANCE_PROPERTY, format(distance));
    setback.setProperty(SETBACK_FROM_PROPERTY, lineId);
    if (!inside) {
      setback.setProperty(LINE_KIND_PROPERTY, "offset");
    }
    addPolylines(Collections.singletonList(setback), line.getLevel());
    notes.add("Added the " + (inside ? "setback line " : "line ") + this.executor.getId(setback) + " \"" + name + "\", "
        + format(distance) + " cm " + (inside ? "inside" : "outside") + " the line " + lineId + ": "
        + describeArea(Math.abs(getSignedArea(points)))
        + (inside ? " left to build on. check_home reports the walls that stand beyond it" : ""));
  }

  /**
   * Returns the polygon whose sides are at <code>distance</code> inside the given one
   * (outside it if the distance is negative), or <code>null</code> if nothing is left.
   */
  static float [][] getOffsetPolygon(float [][] points, double distance) {
    int count = points.length;
    double orientation = Math.signum(getSignedArea(points));
    if (orientation == 0) {
      return null;
    }
    float [][] offset = new float [count][];
    for (int i = 0; i < count; i++) {
      float [] previous = points [(i + count - 1) % count];
      float [] point = points [i];
      float [] next = points [(i + 1) % count];
      double length1 = Math.hypot(point [0] - previous [0], point [1] - previous [1]);
      double length2 = Math.hypot(next [0] - point [0], next [1] - point [1]);
      if (length1 < 1E-3 || length2 < 1E-3) {
        return null;
      }
      // The normals of the two sides, toward the inside
      double n1x = -orientation * (point [1] - previous [1]) / length1;
      double n1y = orientation * (point [0] - previous [0]) / length1;
      double n2x = -orientation * (next [1] - point [1]) / length2;
      double n2y = orientation * (next [0] - point [0]) / length2;
      double cross = (point [0] - previous [0]) / length1 * (next [1] - point [1]) / length2
          - (point [1] - previous [1]) / length1 * (next [0] - point [0]) / length2;
      if (Math.abs(cross) < 1E-6) {
        // Sides in line
        offset [i] = new float [] {(float)(point [0] + n1x * distance), (float)(point [1] + n1y * distance)};
      } else {
        // Where the two shifted sides meet
        double bisectorX = n1x + n2x;
        double bisectorY = n1y + n2y;
        double scale = distance / (1 + n1x * n2x + n1y * n2y);
        // A sharp corner isn't thrown far away
        double limit = Math.abs(distance) * 4;
        double dx = bisectorX * scale;
        double dy = bisectorY * scale;
        double shift = Math.hypot(dx, dy);
        if (shift > limit) {
          dx *= limit / shift;
          dy *= limit / shift;
        }
        offset [i] = new float [] {(float)(point [0] + dx), (float)(point [1] + dy)};
      }
    }
    // Shrunk to nothing, or turned inside out
    double area = getSignedArea(offset);
    if (distance > 0 && (Math.signum(area) != orientation || Math.abs(area) < 1
        || Math.abs(area) >= Math.abs(getSignedArea(points)))) {
      return null;
    }
    return offset;
  }

  static double getSignedArea(float [][] points) {
    double area = 0;
    for (int i = 0; i < points.length; i++) {
      float [] next = points [(i + 1) % points.length];
      area += (double)points [i][0] * next [1] - (double)next [0] * points [i][1];
    }
    return area / 2;
  }

  private static double getLength(float [][] points, boolean closed) {
    double length = 0;
    for (int i = 0; i < (closed ? points.length : points.length - 1); i++) {
      float [] next = points [(i + 1) % points.length];
      length += Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
    }
    return length;
  }

  /**
   * Returns an area given in cm2, in m2, in square feet and in acres.
   */
  private static String describeArea(double area) {
    double squareMeters = area / 10000;
    return format(squareMeters) + " m2 (" + Math.round(squareMeters * 10.7639) + " sq ft, "
        + String.format(Locale.US, "%.3f", squareMeters / 4046.8564224) + " acre)";
  }

  /**
   * Changes lines: their points, their color, their thickness, their dashes or their name.
   */
  private void modifyPolyline(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    final List<Polyline> polylines = new ArrayList<Polyline>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Polyline) {
        polylines.add((Polyline)item);
      }
    }
    if (polylines.isEmpty()) {
      throw new IOException("give the lines with \"id\" or \"ids\" (P#); lines: " + describeLines(this.home.getPolylines()));
    }
    final float [][] points = command.has("points") || command.has("geo") ? getPoints(command) : null;
    if (points != null && polylines.size() > 1) {
      throw new IOException("\"points\" changes one line at a time");
    }
    final Integer color = command.getString("color") != null ? AssistantCommandExecutor.parseColor(command.getString("color")) : null;
    final Polyline.DashStyle dashStyle = parseDash(command.getString("dash"));
    final double thickness = command.getDouble("thickness", Double.NaN);
    final String name = command.getString("name");
    final Boolean closed = command.has("closed") ? Boolean.valueOf(command.getBoolean("closed", true)) : null;
    if (points == null && color == null && dashStyle == null && Double.isNaN(thickness) && name == null && closed == null) {
      throw new IOException("give what changes: points, color, thickness, dash, closed or name");
    }
    final List<Object []> before = new ArrayList<Object []>();
    for (Polyline polyline : polylines) {
      before.add(getState(polyline));
    }
    for (Polyline polyline : polylines) {
      if (points != null) {
        polyline.setPoints(points);
      }
      if (color != null) {
        polyline.setColor(0xFF000000 | color);
      }
      if (dashStyle != null) {
        polyline.setDashStyle(dashStyle);
      }
      if (thickness >= 0.1 && thickness <= 200) {
        polyline.setThickness((float)thickness);
      }
      if (closed != null) {
        polyline.setClosedPath(closed);
      }
      if (name != null) {
        polyline.setProperty(LINE_NAME_PROPERTY, name.trim().length() > 0 ? name.trim() : null);
      }
    }
    final List<Object []> after = new ArrayList<Object []>();
    for (Polyline polyline : polylines) {
      after.add(getState(polyline));
    }
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() throws CannotUndoException {
          super.undo();
          for (int i = 0; i < polylines.size(); i++) {
            setState(polylines.get(i), before.get(i));
          }
        }

        @Override
        public void redo() throws CannotRedoException {
          super.redo();
          for (int i = 0; i < polylines.size(); i++) {
            setState(polylines.get(i), after.get(i));
          }
        }

        @Override
        public String getPresentationName() {
          return "AI assistant edit";
        }
      });
    tally [4] += polylines.size();
    notes.add("Modified " + (polylines.size() == 1 ? "the line " : polylines.size() + " lines: ") + describeLines(polylines));
  }

  private static Object [] getState(Polyline polyline) {
    return new Object [] {polyline.getPoints(), polyline.getColor(), polyline.getDashStyle(), polyline.getThickness(),
                          polyline.isClosedPath(), polyline.getProperty(LINE_NAME_PROPERTY)};
  }

  private static void setState(Polyline polyline, Object [] state) {
    polyline.setPoints((float [][])state [0]);
    polyline.setColor((Integer)state [1]);
    polyline.setDashStyle((Polyline.DashStyle)state [2]);
    polyline.setThickness((Float)state [3]);
    polyline.setClosedPath((Boolean)state [4]);
    polyline.setProperty(LINE_NAME_PROPERTY, (String)state [5]);
  }

  // ---- Compass and georeference ----

  private static float [] getState(Compass compass) {
    return new float [] {compass.getX(), compass.getY(), compass.getDiameter(), compass.getNorthDirection(),
                         compass.getLatitude(), compass.getLongitude(), compass.isVisible() ? 1 : 0};
  }

  private static void setState(Compass compass, float [] state) {
    compass.setX(state [0]);
    compass.setY(state [1]);
    compass.setDiameter(state [2]);
    compass.setNorthDirection(state [3]);
    compass.setLatitude(state [4]);
    compass.setLongitude(state [5]);
    compass.setVisible(state [6] != 0);
  }

  /**
   * Posts the edit that undoes a change of the compass and of the georeference of the home.
   */
  private void postSiteEdit(final float [] compassBefore, final String georeferenceBefore) {
    final Compass compass = this.home.getCompass();
    final float [] compassAfter = getState(compass);
    final String georeferenceAfter = this.home.getProperty(HomeGeoreference.PROPERTY);
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() throws CannotUndoException {
          super.undo();
          setState(compass, compassBefore);
          home.setProperty(HomeGeoreference.PROPERTY, georeferenceBefore);
        }

        @Override
        public void redo() throws CannotRedoException {
          super.redo();
          setState(compass, compassAfter);
          home.setProperty(HomeGeoreference.PROPERTY, georeferenceAfter);
        }

        @Override
        public String getPresentationName() {
          return "AI assistant edit";
        }
      });
  }

  private String describeCompass() {
    Compass compass = this.home.getCompass();
    return "north at " + format(Math.toDegrees(compass.getNorthDirection())) + " degrees clockwise from the top of the plan, latitude "
        + String.format(Locale.US, "%.5f", Math.toDegrees(compass.getLatitude())) + ", longitude "
        + String.format(Locale.US, "%.5f", Math.toDegrees(compass.getLongitude())) + ", drawn at ("
        + format(compass.getX()) + ", " + format(compass.getY()) + ") " + format(compass.getDiameter()) + " cm wide"
        + (compass.isVisible() ? "" : ", hidden");
  }

  /**
   * Changes the compass of the home: the direction of the north, used for the light of the sun,
   * the place of the home and the place of the rose in the plan.
   */
  private void setCompass(AssistantCommand command, List<String> notes) throws IOException {
    Compass compass = this.home.getCompass();
    float [] before = getState(compass);
    String georeferenceBefore = this.home.getProperty(HomeGeoreference.PROPERTY);
    double northDirection = command.getDouble("north_direction", command.getDouble("north", Double.NaN));
    double latitude = command.getDouble("latitude", command.getDouble("lat", Double.NaN));
    double longitude = command.getDouble("longitude", command.getDouble("lon", Double.NaN));
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    double diameter = command.getDouble("diameter", Double.NaN);
    if (!Double.isNaN(latitude) && Math.abs(latitude) > 90 || !Double.isNaN(longitude) && Math.abs(longitude) > 180) {
      throw new IOException("the latitude goes from -90 to 90 degrees and the longitude from -180 to 180");
    }
    if (command.getBoolean("georeference", false)) {
      // The place and the true north of the georeference of the home
      HomeGeoreference georeference = HomeGeoreference.get(this.home);
      if (georeference == null) {
        throw new IOException("the home has no georeference: give latitude, longitude and north_direction, or call set_georeference");
      }
      georeference.applyTo(compass);
    }
    if (!Double.isNaN(northDirection)) {
      compass.setNorthDirection((float)Math.toRadians(((northDirection % 360) + 360) % 360));
    }
    if (!Double.isNaN(latitude)) {
      compass.setLatitude((float)Math.toRadians(latitude));
    }
    if (!Double.isNaN(longitude)) {
      compass.setLongitude((float)Math.toRadians(longitude));
    }
    if (!Double.isNaN(x) && AssistantCommandExecutor.coordinateSane(x)) {
      compass.setX((float)x);
    }
    if (!Double.isNaN(y) && AssistantCommandExecutor.coordinateSane(y)) {
      compass.setY((float)y);
    }
    if (diameter >= 10 && diameter <= 5000) {
      compass.setDiameter((float)diameter);
    }
    if (command.has("visible")) {
      compass.setVisible(command.getBoolean("visible", true));
    }
    if (command.getString("time_zone") != null) {
      try {
        compass.setTimeZone(command.getString("time_zone"));
      } catch (IllegalArgumentException ex) {
        notes.add("Unknown time zone \"" + command.getString("time_zone") + "\": like Europe/Paris or America/New_York");
      }
    }
    if (java.util.Arrays.equals(before, getState(compass)) && command.getString("time_zone") == null) {
      notes.add("Compass: " + describeCompass() + ". Give north_direction (degrees clockwise from the top of the plan), "
          + "latitude, longitude, x, y, diameter or visible to change it");
      return;
    }
    postSiteEdit(before, georeferenceBefore);
    notes.add("Changed the compass: " + describeCompass() + ". The sun of the 3D view and of photos follows it");
  }

  /**
   * Places the home on the Earth.
   */
  private void setGeoreference(AssistantCommand command, List<String> notes) throws IOException {
    HomeGeoreference georeference;
    Object controlPoints = command.getParameters().get("points");
    if (controlPoints instanceof List && ((List<?>)controlPoints).size() >= 3) {
      // The turn and the place that fit best several points
      List<?> list = (List<?>)controlPoints;
      int count = list.size();
      double [][] values = new double [count][];
      double meanLatitude = 0;
      double meanLongitude = 0;
      for (int i = 0; i < count; i++) {
        if (!(list.get(i) instanceof Map)) {
          throw new IOException("\"points\" must give points like {\"lat\":33.9,\"lon\":-84.3,\"x\":0,\"y\":0}");
        }
        Map<?, ?> map = (Map<?, ?>)list.get(i);
        values [i] = new double [] {getNumber(map, "lat", "latitude"), getNumber(map, "lon", "longitude"),
                                    getNumber(map, "x", "x"), getNumber(map, "y", "y")};
        meanLatitude += values [i][0] / count;
        meanLongitude += values [i][1] / count;
      }
      HomeGeoreference north = new HomeGeoreference(meanLatitude, meanLongitude, 0, 0, 0);
      double [][] ground = new double [count][];
      double [] groundCenter = new double [2];
      double [] planCenter = new double [2];
      for (int i = 0; i < count; i++) {
        ground [i] = north.toPlan(values [i][0], values [i][1]);
        groundCenter [0] += ground [i][0] / count;
        groundCenter [1] += ground [i][1] / count;
        planCenter [0] += values [i][2] / count;
        planCenter [1] += values [i][3] / count;
      }
      double cross = 0;
      double dot = 0;
      double groundSize = 0;
      double planSize = 0;
      for (int i = 0; i < count; i++) {
        double gx = ground [i][0] - groundCenter [0];
        double gy = ground [i][1] - groundCenter [1];
        double px = values [i][2] - planCenter [0];
        double py = values [i][3] - planCenter [1];
        cross += gx * py - gy * px;
        dot += gx * px + gy * py;
        groundSize += gx * gx + gy * gy;
        planSize += px * px + py * py;
      }
      if (groundSize < 1 || planSize < 1) {
        throw new IOException("the points must be different places and different points of the plan");
      }
      double rotation = Math.toDegrees(Math.atan2(cross, dot));
      double scale = Math.sqrt(planSize / groundSize);
      // The middle of the places is at the middle of the points
      double [] center = north.toLatitudeLongitude(groundCenter [0], groundCenter [1]);
      georeference = new HomeGeoreference(center [0], center [1], planCenter [0], planCenter [1], rotation);
      double worst = 0;
      double squares = 0;
      int worstIndex = 0;
      for (int i = 0; i < count; i++) {
        double [] place = georeference.toPlan(values [i][0], values [i][1]);
        double gap = Math.hypot(place [0] - values [i][2], place [1] - values [i][3]);
        squares += gap * gap / count;
        if (gap > worst) {
          worst = gap;
          worstIndex = i;
        }
      }
      notes.add("Fitted " + count + " points: they are " + format(Math.sqrt(squares)) + " cm off on average and the point "
          + (worstIndex + 1) + " is the farthest, " + format(worst) + " cm off. The plan is " + String.format(Locale.US, "%.4f", scale)
          + " times the size of the ground between them" + (Math.abs(scale - 1) > 0.01
              ? ": it isn't at the scale of the ground, or a point is wrong. The plan keeps its scale" : ""));
    } else if (controlPoints instanceof List && ((List<?>)controlPoints).size() >= 2) {
      double [][] values = new double [2][];
      for (int i = 0; i < 2; i++) {
        Object point = ((List<?>)controlPoints).get(i);
        if (!(point instanceof Map)) {
          throw new IOException("\"points\" must give two points like {\"lat\":33.9,\"lon\":-84.3,\"x\":0,\"y\":0}");
        }
        Map<?, ?> map = (Map<?, ?>)point;
        values [i] = new double [] {getNumber(map, "lat", "latitude"), getNumber(map, "lon", "longitude"),
                                    getNumber(map, "x", "x"), getNumber(map, "y", "y")};
      }
      georeference = HomeGeoreference.fromControlPoints(values [0][0], values [0][1], values [0][2], values [0][3],
          values [1][0], values [1][1], values [1][2], values [1][3]);
      if (georeference == null) {
        throw new IOException("the two points must be two different places and two different points of the plan");
      }
      double ground = HomeGeoreference.getDistance(values [0][0], values [0][1], values [1][0], values [1][1]);
      double plan = Math.hypot(values [1][2] - values [0][2], values [1][3] - values [0][3]);
      if (Math.abs(ground - plan) > Math.max(20, ground * 0.01)) {
        notes.add("The two points are " + format(ground) + " cm apart on the ground and " + format(plan)
            + " cm apart in the plan: the plan isn't at the scale of the ground, or a point is wrong. "
            + "The first point and the direction to the second one were used");
      }
    } else {
      double latitude = command.getDouble("lat", command.getDouble("latitude", Double.NaN));
      double longitude = command.getDouble("lon", command.getDouble("longitude", Double.NaN));
      if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
        HomeGeoreference current = HomeGeoreference.get(this.home);
        notes.add(current == null
            ? "This home has no georeference. Give {\"lat\":N,\"lon\":N,\"x\":N,\"y\":N,\"rotation\":N}: the place of the "
                + "point (x, y) of the plan and the direction of the true north in degrees clockwise from the top of the plan; "
                + "or \"points\": two points {\"lat\",\"lon\",\"x\",\"y\"}"
            : "Georeference: " + describe(current));
        return;
      }
      georeference = new HomeGeoreference(latitude, longitude, command.getDouble("x", 0), command.getDouble("y", 0),
          command.getDouble("rotation", command.getDouble("north_direction",
              Math.toDegrees(this.home.getCompass().getNorthDirection()))));
    }
    if (Math.abs(georeference.getLatitude()) > 89 || Math.abs(georeference.getLongitude()) > 180) {
      throw new IOException("the latitude goes from -89 to 89 degrees and the longitude from -180 to 180");
    }
    applyGeoreference(georeference, command.getBoolean("compass", true));
    notes.add("Set the georeference: " + describe(georeference)
        + (command.getBoolean("compass", true) ? ". The compass shows the true north and has this latitude and longitude" : "")
        + ". import_parcel and \"geo\" points are now placed in the plan, and get_coordinates gives the coordinates of its points");
  }

  /**
   * Tells the points of the plan of places given by their latitude and longitude, or by their coordinates in a system.
   */
  private void geoToPlan(AssistantCommand command, List<String> notes) throws IOException {
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    if (georeference == null) {
      throw new IOException("this home has no georeference: {\"action\":\"set_georeference\",\"lat\":N,\"lon\":N,\"x\":N,\"y\":N} first");
    }
    HomeGeoreference.Projection projection = command.getString("srs") != null
        ? HomeGeoreference.Projection.get(command.getString("srs")) : null;
    Object points = command.getParameters().get("points");
    if (!(points instanceof List) || ((List<?>)points).isEmpty()) {
      throw new IOException("give \"points\":[[lat,lon],...], or with \"srs\":\"EPSG:2240\" [[x,y],...] in that system "
          + "(for EPSG:4326 too: [longitude, latitude])");
    }
    StringBuilder text = new StringBuilder("Points of the plan in cm"
        + (projection != null ? ", from " + projection.getName() : ", from [latitude, longitude]") + ": ");
    StringBuilder list = new StringBuilder("[");
    int count = 0;
    for (Object point : (List<?>)points) {
      double latitude;
      double longitude;
      String name;
      if (point instanceof Map) {
        latitude = getNumber((Map<?, ?>)point, "lat", "latitude");
        longitude = getNumber((Map<?, ?>)point, "lon", "longitude");
        name = String.format(Locale.US, "%.7f, %.7f", latitude, longitude);
      } else if (point instanceof List && ((List<?>)point).size() >= 2 && ((List<?>)point).get(0) instanceof Number
                 && ((List<?>)point).get(1) instanceof Number) {
        double first = ((Number)((List<?>)point).get(0)).doubleValue();
        double second = ((Number)((List<?>)point).get(1)).doubleValue();
        if (projection != null) {
          double [] place = projection.toLatitudeLongitude(first, second);
          latitude = place [0];
          longitude = place [1];
          name = first + ", " + second;
        } else {
          latitude = first;
          longitude = second;
          name = String.format(Locale.US, "%.7f, %.7f", latitude, longitude);
        }
      } else {
        throw new IOException("a point is written [lat,lon], {\"lat\":N,\"lon\":N}, or [x,y] with \"srs\"");
      }
      if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
        throw new IOException("the point " + name + " isn't a latitude from -90 to 90 and a longitude from -180 to 180: "
            + "a point is written [latitude, longitude]");
      }
      double [] plan = georeference.toPlan(latitude, longitude);
      if (Math.hypot(plan [0] - georeference.getX(), plan [1] - georeference.getY()) > 1E7) {
        throw new IOException("the point " + name + " is " + format(Math.hypot(plan [0] - georeference.getX(),
            plan [1] - georeference.getY()) / 100000) + " km from the home: a point is written [latitude, longitude], "
            + "in this order");
      }
      if (count < 200) {
        text.append(count > 0 ? "; " : "").append('(').append(name).append(") -> (").append(format(plan [0])).append(", ")
            .append(format(plan [1])).append(')');
      }
      list.append(count > 0 ? "," : "").append('[').append(format(plan [0])).append(',').append(format(plan [1])).append(']');
      count++;
    }
    notes.add(text + (count > 200 ? "; ... " + (count - 200) + " more" : "") + ". As a list: " + list + "]");
  }

  private static double getNumber(Map<?, ?> map, String key, String otherKey) throws IOException {
    Object value = map.get(key) != null ? map.get(key) : map.get(otherKey);
    if (!(value instanceof Number)) {
      throw new IOException("a point misses \"" + key + "\"");
    }
    return ((Number)value).doubleValue();
  }

  private String describe(HomeGeoreference georeference) {
    return "the point (" + format(georeference.getX()) + ", " + format(georeference.getY()) + ") of the plan is at latitude "
        + String.format(Locale.US, "%.7f", georeference.getLatitude()) + ", longitude "
        + String.format(Locale.US, "%.7f", georeference.getLongitude()) + ", and the true north is "
        + format(georeference.getRotation()) + " degrees clockwise from the top of the plan";
  }

  private void applyGeoreference(HomeGeoreference georeference, boolean compassToo) {
    Compass compass = this.home.getCompass();
    float [] compassBefore = getState(compass);
    String georeferenceBefore = this.home.getProperty(HomeGeoreference.PROPERTY);
    this.home.setProperty(HomeGeoreference.PROPERTY, georeference.toJSON());
    if (compassToo) {
      compass.setNorthDirection((float)Math.toRadians(((georeference.getRotation() % 360) + 360) % 360));
      compass.setLatitude((float)Math.toRadians(georeference.getLatitude()));
      compass.setLongitude((float)Math.toRadians(georeference.getLongitude()));
    }
    postSiteEdit(compassBefore, georeferenceBefore);
  }

  /**
   * Draws the lines of the parcels of a survey, given as GeoJSON or Esri JSON.
   */
  private void importParcel(AssistantCommand command, List<String> notes) throws IOException {
    Object data = command.getParameters().get("geojson");
    if (data == null) {
      data = command.getParameters().get("esrijson");
    }
    if (data == null) {
      data = command.getParameters().get("json");
    }
    String path = command.getString("path") != null ? command.getString("path") : command.getString("file");
    if (data == null && path != null) {
      File file = new File(path);
      if (!file.isAbsolute() || !file.isFile()) {
        throw new IOException("\"path\" must be the absolute path of a GeoJSON or Esri JSON file");
      }
      data = new String(AssistantGeneratedModel.readFile(file, MAX_FILE_LENGTH), "UTF-8");
    }
    if (data instanceof String) {
      try {
        data = JSONParser.parse((String)data);
      } catch (IllegalArgumentException ex) {
        throw new IOException("the parcel isn't written in JSON (" + ex.getMessage() + ")");
      }
    }
    if (!(data instanceof Map)) {
      throw new IOException("give the parcel with \"geojson\" or \"esrijson\" (an object), or \"path\" (a file)");
    }
    // The rings of the parcels and the coordinate system they name
    List<List<double []>> rings = new ArrayList<List<double []>>();
    String [] srs = {command.getString("srs") != null ? command.getString("srs") : command.getString("crs")};
    collectRings((Map<?, ?>)data, rings, srs, command.getString("srs") == null && command.getString("crs") == null);
    if (rings.isEmpty()) {
      throw new IOException("no ring of 3 points at least was found: a GeoJSON Polygon, MultiPolygon, Feature or "
          + "FeatureCollection, or an Esri JSON object with \"rings\", is expected");
    }
    HomeGeoreference.Projection projection = HomeGeoreference.Projection.get(srs [0]);
    // Their places
    List<List<double []>> places = new ArrayList<List<double []>>();
    double latitudeSum = 0;
    double longitudeSum = 0;
    int count = 0;
    for (List<double []> ring : rings) {
      List<double []> ringPlaces = new ArrayList<double []>();
      for (double [] point : ring) {
        double [] place = projection.toLatitudeLongitude(point [0], point [1]);
        if (Double.isNaN(place [0]) || Double.isNaN(place [1]) || Math.abs(place [0]) > 89.9 || Math.abs(place [1]) > 360) {
          throw new IOException("the coordinates " + point [0] + ", " + point [1] + " aren't a place in the system "
              + projection.getName() + ": give the right \"srs\"");
        }
        ringPlaces.add(place);
        latitudeSum += place [0];
        longitudeSum += place [1];
        count++;
      }
      places.add(ringPlaces);
    }
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    boolean georeferenced = georeference != null;
    if (!georeferenced) {
      // The middle of the parcel at the given point of the plan, the true north at its top
      georeference = new HomeGeoreference(latitudeSum / count, longitudeSum / count,
          command.getDouble("x", 0), command.getDouble("y", 0),
          command.getDouble("rotation", 0));
      applyGeoreference(georeference, command.getBoolean("compass", true));
    }
    Level level = this.executor.getShapeCommands().getLevel(command, "import_parcel", notes);
    String name = command.getString("name") != null ? command.getString("name")
        : command.getString("label") != null ? command.getString("label") : "Property line";
    List<Polyline> polylines = new ArrayList<Polyline>();
    StringBuilder description = new StringBuilder();
    for (int i = 0; i < places.size(); i++) {
      List<double []> ringPlaces = places.get(i);
      float [][] points = new float [ringPlaces.size()][];
      for (int j = 0; j < points.length; j++) {
        double [] point = georeference.toPlan(ringPlaces.get(j) [0], ringPlaces.get(j) [1]);
        if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
          throw new IOException("the parcel is " + format(Math.hypot(point [0], point [1]) / 100)
              + " m away from the reference point of the home: check the georeference and \"srs\"");
        }
        points [j] = new float [] {(float)point [0], (float)point [1]};
      }
      Polyline polyline = createPolyline(points, true, level, places.size() > 1 ? name + " " + (i + 1) : name, "property",
          command.getString("color"), command.getDouble("thickness", Double.NaN), command.getString("dash"), false, Double.NaN);
      polylines.add(polyline);
    }
    addPolylines(polylines, level);
    for (Polyline polyline : polylines) {
      float [][] points = polyline.getPoints();
      float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      for (float [] point : points) {
        bounds [0] = Math.min(bounds [0], point [0]);
        bounds [1] = Math.min(bounds [1], point [1]);
        bounds [2] = Math.max(bounds [2], point [0]);
        bounds [3] = Math.max(bounds [3], point [1]);
      }
      description.append(description.length() > 0 ? "; " : "").append(this.executor.getId(polyline)).append(": ")
          .append(points.length).append(" corners, ").append(describeArea(Math.abs(getSignedArea(points))))
          .append(", perimeter ").append(format(getLength(points, true) / 100)).append(" m, x ")
          .append(format(bounds [0])).append(" to ").append(format(bounds [2])).append(", y ")
          .append(format(bounds [1])).append(" to ").append(format(bounds [3])).append(" cm");
      if (command.getBoolean("stakes", false)) {
        addStakes(points, true, level, polyline.getProperty(LINE_NAME_PROPERTY), polyline.getColor(),
            (float)command.getDouble("stake_height", 90));
      }
    }
    notes.add("Imported " + polylines.size() + (polylines.size() == 1 ? " property line" : " property lines")
        + " from coordinates in " + projection.getName()
        + (projection.getUnit() > 0 ? " (1 unit = " + String.format(Locale.US, "%.6f", projection.getUnit()) + " m)" : "")
        + ": " + description
        + (georeferenced ? ", placed by the georeference of the home"
            : ". The home had no georeference: the middle of the parcel was put at ("
                + format(georeference.getX()) + ", " + format(georeference.getY())
                + ") with the true north at the top of the plan, and that became its georeference. If the house is "
                + "already drawn, move the parcel around it with set_georeference (two points known in both) and import again"));
  }

  /**
   * Collects the outer rings of the polygons of a GeoJSON or Esri JSON object.
   */
  private static void collectRings(Map<?, ?> object, List<List<double []>> rings, String [] srs, boolean readSrs)
      throws IOException {
    if (readSrs) {
      // The coordinate system the data names
      Object reference = object.get("spatialReference");
      if (reference instanceof Map) {
        Object code = ((Map<?, ?>)reference).get("latestWkid") != null
            ? ((Map<?, ?>)reference).get("latestWkid") : ((Map<?, ?>)reference).get("wkid");
        if (code instanceof Number) {
          srs [0] = "EPSG:" + ((Number)code).intValue();
        }
      }
      Object crs = object.get("crs");
      if (crs instanceof Map && ((Map<?, ?>)crs).get("properties") instanceof Map
          && ((Map<?, ?>)((Map<?, ?>)crs).get("properties")).get("name") != null) {
        srs [0] = ((Map<?, ?>)((Map<?, ?>)crs).get("properties")).get("name").toString();
      }
    }
    Object type = object.get("type");
    if (object.get("rings") instanceof List) {
      for (Object ring : (List<?>)object.get("rings")) {
        addRing(ring, rings);
      }
    } else if (object.get("features") instanceof List) {
      for (Object feature : (List<?>)object.get("features")) {
        if (feature instanceof Map) {
          collectRings((Map<?, ?>)feature, rings, srs, readSrs);
        }
      }
    } else if (object.get("geometry") instanceof Map) {
      collectRings((Map<?, ?>)object.get("geometry"), rings, srs, readSrs);
    } else if (object.get("geometries") instanceof List) {
      for (Object geometry : (List<?>)object.get("geometries")) {
        if (geometry instanceof Map) {
          collectRings((Map<?, ?>)geometry, rings, srs, readSrs);
        }
      }
    } else if ("Polygon".equals(type) && object.get("coordinates") instanceof List) {
      List<?> polygon = (List<?>)object.get("coordinates");
      if (!polygon.isEmpty()) {
        // Without the holes
        addRing(polygon.get(0), rings);
      }
    } else if ("MultiPolygon".equals(type) && object.get("coordinates") instanceof List) {
      for (Object polygon : (List<?>)object.get("coordinates")) {
        if (polygon instanceof List && !((List<?>)polygon).isEmpty()) {
          addRing(((List<?>)polygon).get(0), rings);
        }
      }
    } else if (("LineString".equals(type) || "MultiPoint".equals(type)) && object.get("coordinates") instanceof List) {
      addRing(object.get("coordinates"), rings);
    }
  }

  private static void addRing(Object ring, List<List<double []>> rings) throws IOException {
    if (!(ring instanceof List)) {
      return;
    }
    List<double []> points = new ArrayList<double []>();
    for (Object point : (List<?>)ring) {
      if (point instanceof List && ((List<?>)point).size() >= 2 && ((List<?>)point).get(0) instanceof Number
          && ((List<?>)point).get(1) instanceof Number) {
        points.add(new double [] {((Number)((List<?>)point).get(0)).doubleValue(), ((Number)((List<?>)point).get(1)).doubleValue()});
      }
    }
    // The last point of a ring repeats the first one
    if (points.size() > 1 && Math.abs(points.get(0) [0] - points.get(points.size() - 1) [0]) < 1E-9
        && Math.abs(points.get(0) [1] - points.get(points.size() - 1) [1]) < 1E-9) {
      points.remove(points.size() - 1);
    }
    if (points.size() > MAX_LINE_POINTS) {
      throw new IOException("a parcel has " + MAX_LINE_POINTS + " corners at most");
    }
    if (points.size() >= 3) {
      rings.add(points);
    }
  }

  /**
   * Tells the latitude and the longitude of points of the plan, and their coordinates in a system.
   */
  private void getCoordinates(AssistantCommand command, List<String> notes) throws IOException {
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    if (georeference == null) {
      throw new IOException("this home has no georeference: {\"action\":\"set_georeference\",\"lat\":N,\"lon\":N,\"x\":N,\"y\":N} first");
    }
    HomeGeoreference.Projection projection = command.getString("srs") != null
        ? HomeGeoreference.Projection.get(command.getString("srs")) : null;
    List<String> names = new ArrayList<String>();
    List<float []> points = new ArrayList<float []>();
    for (float [] point : AssistantFurnitureBuilder.getPoints(command.getParameters(), "points", 2)) {
      names.add("(" + format(point [0]) + ", " + format(point [1]) + ")");
      points.add(point);
    }
    if (command.has("x") && command.has("y")) {
      points.add(new float [] {(float)command.getDouble("x", 0), (float)command.getDouble("y", 0)});
      names.add("(" + format(command.getDouble("x", 0)) + ", " + format(command.getDouble("y", 0)) + ")");
    }
    for (Selectable item : this.executor.resolveTargets(command)) {
      String id = this.executor.getId(item);
      if (item instanceof HomePieceOfFurniture) {
        points.add(new float [] {((HomePieceOfFurniture)item).getX(), ((HomePieceOfFurniture)item).getY()});
        names.add(id + " \"" + ((HomePieceOfFurniture)item).getName() + "\"");
      } else {
        float [][] itemPoints = item instanceof Wall
            ? new float [][] {{((Wall)item).getXStart(), ((Wall)item).getYStart()}, {((Wall)item).getXEnd(), ((Wall)item).getYEnd()}}
            : item instanceof Room || item instanceof Polyline ? item.getPoints() : new float [0][];
        for (int i = 0; i < itemPoints.length && i < 200; i++) {
          points.add(itemPoints [i]);
          names.add(id + " point " + (i + 1));
        }
      }
    }
    if (points.isEmpty()) {
      throw new IOException("give \"points\":[[x,y],...] of the plan, or the ids of items");
    }
    StringBuilder text = new StringBuilder("Coordinates (latitude, longitude"
        + (projection != null && !projection.isGeographic() ? "; x, y in " + projection.getName() : "") + "):");
    for (int i = 0; i < points.size() && i < 400; i++) {
      double [] place = georeference.toLatitudeLongitude(points.get(i) [0], points.get(i) [1]);
      text.append("\n- ").append(names.get(i)).append(": ")
          .append(String.format(Locale.US, "%.7f, %.7f", place [0], place [1]));
      if (projection != null && !projection.isGeographic()) {
        double [] projected = projection.fromLatitudeLongitude(place [0], place [1]);
        text.append(String.format(Locale.US, "; %.3f, %.3f", projected [0], projected [1]));
      }
    }
    notes.add(text.toString());
  }

  /**
   * Returns the walls that stand beyond the setback lines and the property lines of the home, each one
   * as {wall, line, the distance in cm it goes beyond the line}.
   */
  static List<Object []> getWallsBeyondLines(Home home) {
    List<Object []> result = new ArrayList<Object []>();
    for (Polyline polyline : home.getPolylines()) {
      String kind = polyline.getProperty(LINE_KIND_PROPERTY);
      if (!("setback".equals(kind) || "property".equals(kind)) || !polyline.isClosedPath() || polyline.getPointCount() < 3) {
        continue;
      }
      float [][] polygon = polyline.getPoints();
      Room area = new Room(polygon);
      for (Wall wall : home.getWalls()) {
        double beyond = 0;
        for (float [] point : wall.getPoints()) {
          if (!area.containsPoint(point [0], point [1], 0)) {
            double distance = Double.MAX_VALUE;
            for (int i = 0; i < polygon.length; i++) {
              float [] next = polygon [(i + 1) % polygon.length];
              distance = Math.min(distance, java.awt.geom.Line2D.ptSegDist(polygon [i][0], polygon [i][1], next [0], next [1],
                  point [0], point [1]));
            }
            beyond = Math.max(beyond, distance);
          }
        }
        if (beyond > 1) {
          result.add(new Object [] {wall, polyline, beyond});
        }
      }
    }
    return result;
  }
}
