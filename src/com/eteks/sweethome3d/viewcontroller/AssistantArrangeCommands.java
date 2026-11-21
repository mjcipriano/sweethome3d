/*
 * AssistantArrangeCommands.java 21 nov. 2026
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

import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands that group, align, distribute, mirror and lock items, and the ones that measure them.
 */
class AssistantArrangeCommands {
  /** The distance in cm beyond which <code>measure</code> looks for nothing around a piece. */
  private static final float  MAX_CLEARANCE = 1000;
  private static final String EDGES = "left, right, top, bottom, center_x, center_y (in the plan), front or back (of a piece)";
  private static final String NO_FURNITURE = "it designates no furniture; give pieces with \"ids\":[\"F1\",\"F2\"], \"names\", "
      + "\"where\" or \"target\":\"selection\"";

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantArrangeCommands(Home home, UserPreferences preferences, HomeController homeController, AssistantCommandExecutor executor) {
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
    return "  {\"action\":\"group\",\"ids\":[\"F1\",\"F2\"],\"name\":\"Dining set\"}  (makes ONE piece of furniture of several, moved and rotated "
        + "together; the answer gives the id of the group. {\"action\":\"ungroup\",\"id\":\"F9\"} frees its pieces, which get their ids back)\n"
        + "  {\"action\":\"align\",\"ids\":[\"F1\",\"F2\",\"F3\"],\"edge\":\"left|right|top|bottom|center_x|center_y|front|back\",\"to\":\"F1\"}  "
        + "(moves furniture so the given edge of their bounding boxes is the same: the one of the item \"to\" (a piece, a wall, a room), "
        + "or without \"to\" the outermost one of the set. front and back are the sides of the piece \"to\" (the first id by default). "
        + "{\"action\":\"align\",\"ids\":[...],\"wall\":\"W3\"} without \"edge\" pushes each piece against the face of the wall on its side)\n"
        + "  {\"action\":\"distribute\",\"ids\":[\"F1\",\"F2\",\"F3\"],\"axis\":\"x|y\",\"spacing\":N}  (equal gaps between the pieces along the axis, "
        + "the two outermost ones staying in place; with \"spacing\" the first one stays and each next one comes N cm after the previous)\n"
        + "  {\"action\":\"mirror\",\"ids\":[...],\"axis\":\"x|y\",\"about\":N}  (flips furniture, walls, rooms and lines: \"axis\":\"x\" changes "
        + "the x of the items across the vertical line x = about, \"axis\":\"y\" their y across the horizontal line y = about; "
        + "without \"about\" the line goes through the center of their bounding box, so they flip in place)\n"
        + "  {\"action\":\"lock\",\"ids\":[...]}  (furniture that can't be moved in the plan with the mouse any more, and that align, distribute "
        + "and mirror leave in place; {\"action\":\"unlock\",\"ids\":[...]} ends it. {\"action\":\"lock_base_plan\"} and "
        + "{\"action\":\"unlock_base_plan\"} lock the walls, the rooms, the doors and the windows of the plan as Plan > Lock base plan)\n"
        + "  {\"action\":\"measure\",\"from\":\"F1\",\"to\":\"W3\"}  (changes nothing. The shortest distance between two outlines, 0 if they "
        + "overlap, the distance of their centers and dx, dy; \"from_point\":[x,y] or \"to_point\":[x,y] gives a point instead of an item. "
        + "{\"action\":\"measure\",\"clearance\":\"F1\"}: the free distance at the front, the back, the left and the right of a piece up to the "
        + "nearest wall or piece of its level. {\"action\":\"measure\",\"at\":[x,y],\"level\":\"L1\",\"tolerance\":5}: the rooms, walls, "
        + "furniture and lines at a point. {\"action\":\"measure\",\"ids\":[...]}: the bounding box of the items)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("group".equals(action) || "group_furniture".equals(action)) {
        group(command, tally, notes);
      } else if ("ungroup".equals(action) || "ungroup_furniture".equals(action)) {
        ungroup(command, tally, notes);
      } else if ("align".equals(action) || "align_furniture".equals(action)) {
        align(command, tally, notes);
      } else if ("distribute".equals(action) || "distribute_furniture".equals(action)) {
        distribute(command, tally, notes);
      } else if ("mirror".equals(action) || "flip".equals(action)) {
        mirror(command, tally, notes);
      } else if ("lock".equals(action) || "unlock".equals(action)) {
        lock(command, "lock".equals(action), tally, notes);
      } else if ("lock_base_plan".equals(action) || "unlock_base_plan".equals(action)) {
        lockBasePlan("lock_base_plan".equals(action), notes);
      } else if ("measure".equals(action)) {
        measure(command, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Ignored " + action + ": " + ex.getMessage());
    }
    return true;
  }

  private static String format(double value) {
    // -0 would be written "-0"
    return AssistantStructureCommands.format(Math.abs(value) < 0.005 ? 0 : (float)value);
  }

  /**
   * Returns the id of an item followed by its name.
   */
  private String describe(Object item) {
    String id = this.executor.getId(item);
    String name = item instanceof HomePieceOfFurniture ? ((HomePieceOfFurniture)item).getName()
        : item instanceof Room ? ((Room)item).getName()
        : item instanceof Polyline ? ((Polyline)item).getProperty(AssistantSiteCommands.LINE_NAME_PROPERTY) : null;
    String text = id != null ? id
        : item instanceof DimensionLine ? "a dimension line" : item instanceof Label ? "a label" : "an item";
    return name != null && name.length() > 0 ? text + " \"" + name + "\"" : text;
  }

  private String describeAll(List<?> items) {
    StringBuilder text = new StringBuilder();
    for (Object item : items) {
      text.append(text.length() > 0 ? ", " : "").append(describe(item));
    }
    return text.toString();
  }

  /**
   * Returns the pieces of furniture of the home a command designates.
   */
  private List<HomePieceOfFurniture> getFurniture(AssistantCommand command) throws IOException {
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    List<HomePieceOfFurniture> homeFurniture = this.home.getFurniture();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture && homeFurniture.contains(item)) {
        furniture.add((HomePieceOfFurniture)item);
      }
    }
    if (furniture.isEmpty()) {
      throw new IOException(NO_FURNITURE);
    }
    return furniture;
  }

  private static double [] newBounds() {
    return new double [] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
  }

  /**
   * Returns <code>minX, minY, maxX, maxY</code> of points.
   */
  private static double [] getBounds(float [][] points) {
    double [] bounds = newBounds();
    addBounds(bounds, points);
    return bounds;
  }

  private static void addBounds(double [] bounds, float [][] points) {
    for (float [] point : points) {
      bounds [0] = Math.min(bounds [0], point [0]);
      bounds [1] = Math.min(bounds [1], point [1]);
      bounds [2] = Math.max(bounds [2], point [0]);
      bounds [3] = Math.max(bounds [3], point [1]);
    }
  }

  /**
   * Selects again the items of <code>selection</code> that are still in the home.
   */
  private void restoreSelection(List<Selectable> selection, boolean allLevelsSelection) {
    List<Selectable> kept = new ArrayList<Selectable>();
    List<HomePieceOfFurniture> homeFurniture = this.home.getFurniture();
    for (Selectable item : selection) {
      if (!(item instanceof HomePieceOfFurniture) || homeFurniture.contains(item)) {
        kept.add(item);
      }
    }
    this.home.setSelectedItems(kept);
    this.home.setAllLevelsSelection(allLevelsSelection);
  }

  // ---- Groups ----

  private void group(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> pieces = getFurniture(command);
    if (pieces.size() < 2) {
      throw new IOException("a group is made of 2 pieces at least and the command designates only " + describeAll(pieces));
    }
    // Their ids, read before they leave the list of the furniture of the home
    String members = describeAll(pieces);
    List<Selectable> selection = this.home.getSelectedItems();
    boolean allLevelsSelection = this.home.isAllLevelsSelection();
    List<HomePieceOfFurniture> furnitureBefore = new ArrayList<HomePieceOfFurniture>(this.home.getFurniture());
    this.executor.beginTurnUpdate();
    this.home.setSelectedItems(new ArrayList<Selectable>(pieces));
    this.homeController.getFurnitureController().groupSelectedFurniture();
    HomeFurnitureGroup group = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece instanceof HomeFurnitureGroup && !furnitureBefore.contains(piece)) {
        group = (HomeFurnitureGroup)piece;
      }
    }
    restoreSelection(selection, allLevelsSelection);
    if (group == null) {
      throw new IOException("the pieces couldn't be grouped");
    }
    String name = command.getString("name");
    if (name != null && name.trim().length() > 0) {
      group.setName(name.trim());
    }
    tally [4] += pieces.size();
    notes.add("Grouped " + members + " into " + describe(group) + ", " + format(group.getWidth()) + " x "
        + format(group.getDepth()) + " cm at (" + format(group.getX()) + ", " + format(group.getY())
        + "): move, rotate or delete it by its id; {\"action\":\"ungroup\",\"id\":\"" + this.executor.getId(group)
        + "\"} frees its pieces");
  }

  private void ungroup(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<HomeFurnitureGroup> groups = new ArrayList<HomeFurnitureGroup>();
    List<HomePieceOfFurniture> others = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : getFurniture(command)) {
      if (piece instanceof HomeFurnitureGroup) {
        groups.add((HomeFurnitureGroup)piece);
      } else {
        others.add(piece);
      }
    }
    String notGroups = others.isEmpty() ? "" : describeAll(others) + (others.size() == 1 ? " is not a group" : " are not groups");
    if (groups.isEmpty()) {
      throw new IOException(notGroups + "; give the id of a group made by {\"action\":\"group\"}");
    }
    String groupNames = describeAll(groups);
    List<Selectable> selection = this.home.getSelectedItems();
    boolean allLevelsSelection = this.home.isAllLevelsSelection();
    List<HomePieceOfFurniture> furnitureBefore = new ArrayList<HomePieceOfFurniture>(this.home.getFurniture());
    this.executor.beginTurnUpdate();
    this.home.setSelectedItems(new ArrayList<Selectable>(groups));
    this.homeController.getFurnitureController().ungroupSelectedFurniture();
    List<HomePieceOfFurniture> freed = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (!furnitureBefore.contains(piece)) {
        freed.add(piece);
      }
    }
    restoreSelection(selection, allLevelsSelection);
    if (freed.isEmpty()) {
      throw new IOException(groupNames + " couldn't be ungrouped");
    }
    tally [4] += groups.size();
    notes.add("Ungrouped " + groupNames + ": the freed pieces are " + describeAll(freed)
        + (others.isEmpty() ? "" : " (" + notGroups + ")"));
  }

  // ---- Alignment ----

  /**
   * Removes the locked pieces from <code>items</code> and tells which ones were left in place.
   */
  private void removeLocked(List<? extends Selectable> items, String action, List<String> notes) {
    List<HomePieceOfFurniture> locked = new ArrayList<HomePieceOfFurniture>();
    for (int i = items.size() - 1; i >= 0; i--) {
      if (items.get(i) instanceof HomePieceOfFurniture && !((HomePieceOfFurniture)items.get(i)).isMovable()) {
        locked.add(0, (HomePieceOfFurniture)items.remove(i));
      }
    }
    noteLocked(locked, action, notes);
  }

  private void noteLocked(List<HomePieceOfFurniture> locked, String action, List<String> notes) {
    if (!locked.isEmpty()) {
      notes.add(action + " left " + describeAll(locked) + " in place: " + (locked.size() == 1 ? "it is" : "they are")
          + " locked ({\"action\":\"unlock\"} frees " + (locked.size() == 1 ? "it" : "them") + ")");
    }
  }

  /**
   * Moves pieces by the given shifts as an undoable change and returns the description of the moved ones.
   */
  private String move(List<HomePieceOfFurniture> pieces, double [][] shifts, int [] tally) {
    final List<Selectable> moved = new ArrayList<Selectable>();
    final List<double []> movedShifts = new ArrayList<double []>();
    for (int i = 0; i < pieces.size(); i++) {
      if (Math.abs(shifts [i][0]) >= 0.005 || Math.abs(shifts [i][1]) >= 0.005) {
        moved.add(pieces.get(i));
        movedShifts.add(shifts [i]);
      }
    }
    if (moved.isEmpty()) {
      return null;
    }
    this.executor.postModification(moved, new Runnable() {
        public void run() {
          for (int i = 0; i < moved.size(); i++) {
            HomePieceOfFurniture piece = (HomePieceOfFurniture)moved.get(i);
            piece.setX((float)(piece.getX() + movedShifts.get(i) [0]));
            piece.setY((float)(piece.getY() + movedShifts.get(i) [1]));
          }
        }
      });
    StringBuilder text = new StringBuilder();
    for (Selectable item : moved) {
      HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
      text.append(text.length() > 0 ? ", " : "").append(this.executor.getId(piece)).append(" now at (")
          .append(format(piece.getX())).append(", ").append(format(piece.getY())).append(")");
    }
    tally [4] += moved.size();
    return text.toString();
  }

  private void align(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> pieces = getFurniture(command);
    String edge = command.getString("edge") != null ? command.getString("edge") : command.getString("side");
    if (edge != null) {
      edge = edge.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
      if (edge.equals("center") || edge.equals("centre") || edge.equals("middle")) {
        throw new IOException("\"edge\":\"center_x\" puts the centers on a vertical line and \"center_y\" on a horizontal one: give one");
      } else if (!Arrays.asList("left", "right", "top", "bottom", "center_x", "center_y", "front", "back").contains(edge)) {
        throw new IOException("\"edge\" is " + EDGES);
      }
    }
    // The item the pieces are aligned to
    Selectable reference = null;
    String token = command.getString("wall") != null ? command.getString("wall") : command.getString("to");
    if (token != null) {
      HomeObject item = HomeItemIds.find(this.home, token.trim());
      if (command.getString("wall") != null && !(item instanceof Wall)) {
        throw new IOException("\"wall\":\"" + token + "\" is not the id of a wall (W#)");
      } else if (!(item instanceof HomePieceOfFurniture || item instanceof Wall || item instanceof Room || item instanceof Polyline)) {
        throw new IOException("no item has the id \"" + token + "\" given by \"to\" (a piece F#, a wall W#, a room R# or a line P#)");
      }
      reference = (Selectable)item;
      pieces.remove(reference);
    }
    if (edge == null && !(reference instanceof Wall)) {
      throw new IOException("give \"edge\": " + EDGES + "; or \"wall\":\"W#\" alone to push the pieces against a wall");
    }
    boolean pieceSide = "front".equals(edge) || "back".equals(edge);
    if (pieceSide && reference == null) {
      // The first piece gives the direction and stays in place
      reference = pieces.remove(0);
    }
    if (pieceSide && !(reference instanceof HomePieceOfFurniture)) {
      throw new IOException("\"" + edge + "\" is a side of a piece of furniture: \"to\" must be the id of a piece");
    }
    removeLocked(pieces, "align", notes);
    if (pieces.isEmpty() || reference == null && pieces.size() < 2) {
      throw new IOException("there is nothing to align: give 2 pieces at least, or the item to align them to with \"to\"");
    }

    double [][] shifts = new double [pieces.size()][2];
    String done;
    if (edge == null) {
      // Against the face of the wall
      Wall wall = (Wall)reference;
      if (wall.getArcExtent() != null && wall.getArcExtent().floatValue() != 0) {
        throw new IOException("the wall " + describe(wall) + " is round; give \"edge\" to align on its bounding box");
      }
      double length = Math.hypot(wall.getXEnd() - wall.getXStart(), wall.getYEnd() - wall.getYStart());
      if (length < 0.01) {
        throw new IOException("the wall " + describe(wall) + " has no length");
      }
      double normalX = -(wall.getYEnd() - wall.getYStart()) / length;
      double normalY = (wall.getXEnd() - wall.getXStart()) / length;
      for (int i = 0; i < pieces.size(); i++) {
        HomePieceOfFurniture piece = pieces.get(i);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (float [] point : piece.getPoints()) {
          double distance = (point [0] - wall.getXStart()) * normalX + (point [1] - wall.getYStart()) * normalY;
          min = Math.min(min, distance);
          max = Math.max(max, distance);
        }
        double center = (piece.getX() - wall.getXStart()) * normalX + (piece.getY() - wall.getYStart()) * normalY;
        double shift = center >= 0 ? wall.getThickness() / 2 - min : -wall.getThickness() / 2 - max;
        shifts [i][0] = shift * normalX;
        shifts [i][1] = shift * normalY;
      }
      done = "against the wall " + describe(wall) + " (each on its side of the wall, without turning)";
    } else if (pieceSide) {
      HomePieceOfFurniture lead = (HomePieceOfFurniture)reference;
      // The direction the wanted side of the lead piece looks at
      double sign = "front".equals(edge) ? 1 : -1;
      double directionX = -Math.sin(lead.getAngle()) * sign;
      double directionY = Math.cos(lead.getAngle()) * sign;
      double target = getExtent(lead, directionX, directionY);
      for (int i = 0; i < pieces.size(); i++) {
        double shift = target - getExtent(pieces.get(i), directionX, directionY);
        shifts [i][0] = shift * directionX;
        shifts [i][1] = shift * directionY;
      }
      done = "on the " + edge + " of " + describe(lead);
    } else {
      boolean alongX = edge.equals("left") || edge.equals("right") || edge.equals("center_x");
      double [] referenceBounds;
      if (reference != null) {
        referenceBounds = getBounds(reference.getPoints());
      } else {
        referenceBounds = newBounds();
        for (HomePieceOfFurniture piece : pieces) {
          addBounds(referenceBounds, piece.getPoints());
        }
      }
      double target = getEdge(referenceBounds, edge);
      for (int i = 0; i < pieces.size(); i++) {
        shifts [i][alongX ? 0 : 1] = target - getEdge(getBounds(pieces.get(i).getPoints()), edge);
      }
      done = "on " + (alongX ? "x" : "y") + " = " + format(target) + ", the "
          + (edge.startsWith("center") ? "center" : edge + " edge") + " of "
          + (reference != null ? describe(reference) : "the set");
    }
    String moved = move(pieces, shifts, tally);
    notes.add(moved != null
        ? "Aligned " + pieces.size() + (pieces.size() == 1 ? " piece " : " pieces ") + done + ": " + moved
        : "Nothing moved: " + describeAll(pieces) + (pieces.size() == 1 ? " is" : " are") + " already aligned " + done);
  }

  /**
   * Returns the largest coordinate of the points of a piece along a direction.
   */
  private static double getExtent(HomePieceOfFurniture piece, double directionX, double directionY) {
    double extent = -Double.MAX_VALUE;
    for (float [] point : piece.getPoints()) {
      extent = Math.max(extent, point [0] * directionX + point [1] * directionY);
    }
    return extent;
  }

  private static double getEdge(double [] bounds, String edge) {
    return edge.equals("left") ? bounds [0]
        : edge.equals("top") ? bounds [1]
        : edge.equals("right") ? bounds [2]
        : edge.equals("bottom") ? bounds [3]
        : edge.equals("center_x") ? (bounds [0] + bounds [2]) / 2
        : (bounds [1] + bounds [3]) / 2;
  }

  private void distribute(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> pieces = getFurniture(command);
    String axis = command.getString("axis");
    axis = axis != null ? axis.trim().toLowerCase(Locale.ENGLISH) : "";
    if (!axis.equals("x") && !axis.equals("y")) {
      throw new IOException("give \"axis\":\"x\" to spread the pieces from left to right or \"axis\":\"y\" from top to bottom");
    }
    final int index = axis.equals("x") ? 0 : 1;
    String spacingParameter = command.has("spacing") ? "spacing" : "gap";
    boolean fixedSpacing = command.has(spacingParameter);
    double spacing = command.getDouble(spacingParameter, Double.NaN);
    if (fixedSpacing && (Double.isNaN(spacing) || !AssistantCommandExecutor.coordinateSane(spacing))) {
      throw new IOException("\"spacing\" is the gap between two pieces in cm");
    }
    if (pieces.size() < (fixedSpacing ? 2 : 3)) {
      throw new IOException(fixedSpacing ? "give 2 pieces at least"
          : "give 3 pieces at least (the two outermost ones stay in place), or \"spacing\":N for a fixed gap from the first one");
    }
    // From the left to the right, or from the top to the bottom
    Collections.sort(pieces, new Comparator<HomePieceOfFurniture>() {
        public int compare(HomePieceOfFurniture piece1, HomePieceOfFurniture piece2) {
          double [] bounds1 = getBounds(piece1.getPoints());
          double [] bounds2 = getBounds(piece2.getPoints());
          int order = Double.compare(bounds1 [index] + bounds1 [index + 2], bounds2 [index] + bounds2 [index + 2]);
          return order != 0 ? order : Double.compare(bounds1 [index], bounds2 [index]);
        }
      });
    double [][] bounds = new double [pieces.size()][];
    double sizes = 0;
    for (int i = 0; i < bounds.length; i++) {
      bounds [i] = getBounds(pieces.get(i).getPoints());
      sizes += bounds [i][index + 2] - bounds [i][index];
    }
    double gap = fixedSpacing ? spacing
        : (bounds [bounds.length - 1][index + 2] - bounds [0][index] - sizes) / (bounds.length - 1);
    double [][] shifts = new double [pieces.size()][2];
    double next = bounds [0][index + 2] + gap;
    for (int i = 1; i < bounds.length - (fixedSpacing ? 0 : 1); i++) {
      shifts [i][index] = next - bounds [i][index];
      next += bounds [i][index + 2] - bounds [i][index] + gap;
    }
    // The locked pieces stay where they are
    List<HomePieceOfFurniture> locked = new ArrayList<HomePieceOfFurniture>();
    for (int i = 0; i < shifts.length; i++) {
      if (!pieces.get(i).isMovable() && Math.abs(shifts [i][index]) >= 0.005) {
        shifts [i][index] = 0;
        locked.add(pieces.get(i));
      }
    }
    noteLocked(locked, "distribute", notes);
    String moved = move(pieces, shifts, tally);
    String order = describeAll(pieces);
    notes.add((moved != null
        ? "Distributed " + pieces.size() + " pieces along " + axis + " with a gap of " + format(gap) + " cm, in the order "
            + order + ": " + moved
        : "Nothing moved: " + order + " already have a gap of " + format(gap) + " cm along " + axis)
        + (gap < -0.005 ? ". The gap is negative: the pieces overlap, as they are wider together than the space between the outermost ones" : ""));
  }

  // ---- Mirror ----

  private void mirror(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<Selectable> items = new ArrayList<Selectable>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture || item instanceof Wall || item instanceof Room
          || item instanceof Polyline || item instanceof DimensionLine || item instanceof Label) {
        items.add(item);
      }
    }
    if (items.isEmpty()) {
      throw new IOException("it designates no item; give furniture, walls, rooms or lines with \"ids\", \"where\" or \"target\":\"selection\"");
    }
    String axis = command.getString("axis");
    axis = axis != null ? axis.trim().toLowerCase(Locale.ENGLISH) : "";
    boolean horizontal;
    if (axis.equals("x") || axis.equals("horizontal")) {
      horizontal = true;
    } else if (axis.equals("y") || axis.equals("vertical")) {
      horizontal = false;
    } else {
      throw new IOException("give \"axis\":\"x\" to swap left and right (the x of the items change) or \"axis\":\"y\" to swap top and bottom");
    }
    double about = command.getDouble("about", Double.NaN);
    if (command.has("about") && (Double.isNaN(about) || !AssistantCommandExecutor.coordinateSane(about))) {
      throw new IOException("\"about\" is the " + (horizontal ? "x" : "y") + " of the line the items are flipped across, in cm");
    }
    removeLocked(items, "mirror", notes);
    if (items.isEmpty()) {
      return;
    }
    if (Double.isNaN(about)) {
      double [] bounds = newBounds();
      for (Selectable item : items) {
        addBounds(bounds, item.getPoints());
      }
      about = horizontal ? (bounds [0] + bounds [2]) / 2 : (bounds [1] + bounds [3]) / 2;
    }
    FlipUndoableEdit edit = new FlipUndoableEdit(this.homeController.getPlanController(),
        items.toArray(new Selectable [items.size()]), (float)about, horizontal);
    edit.flip();
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(edit);
    tally [4] += items.size();
    HomePieceOfFurniture piece = items.size() == 1 && items.get(0) instanceof HomePieceOfFurniture
        ? (HomePieceOfFurniture)items.get(0) : null;
    notes.add("Mirrored " + describeAll(items) + " across the " + (horizontal ? "vertical line x = " : "horizontal line y = ")
        + format(about) + ": " + (horizontal ? "left and right" : "top and bottom") + " are swapped"
        + (piece != null
            ? "; it is now at (" + format(piece.getX()) + ", " + format(piece.getY()) + ") with the angle " + format(getAngle(piece))
            : ""));
  }

  /**
   * Returns the angle of a piece in degrees, from 0 to 360.
   */
  private static double getAngle(HomePieceOfFurniture piece) {
    double angle = Math.toDegrees(piece.getAngle()) % 360;
    return angle < -0.005 ? angle + 360 : angle;
  }

  /**
   * The flip of items across a line, undone by the same flip.
   */
  private static final class FlipUndoableEdit extends AbstractUndoableEdit {
    private final PlanController planController;
    private final Selectable []  items;
    private final float          axisCoordinate;
    private final boolean        horizontal;

    FlipUndoableEdit(PlanController planController, Selectable [] items, float axisCoordinate, boolean horizontal) {
      this.planController = planController;
      this.items = items;
      this.axisCoordinate = axisCoordinate;
      this.horizontal = horizontal;
    }

    void flip() {
      List<Selectable> flippedItems = Arrays.asList(this.items);
      for (Selectable item : this.items) {
        // The offsets of the texts along their height are left as they are
        float [] textOffsets = new float [item instanceof HomeFurnitureGroup
            ? ((HomeFurnitureGroup)item).getAllFurniture().size() + 1 : 2];
        this.planController.flipItem(item, textOffsets, 0, this.axisCoordinate, this.horizontal, flippedItems);
      }
    }

    @Override
    public void undo() throws CannotUndoException {
      super.undo();
      flip();
    }

    @Override
    public void redo() throws CannotRedoException {
      super.redo();
      flip();
    }

    @Override
    public String getPresentationName() {
      return "Mirror";
    }
  }

  // ---- Locks ----

  private void lock(AssistantCommand command, boolean locked, int [] tally, List<String> notes) throws IOException {
    String action = locked ? "lock" : "unlock";
    List<HomePieceOfFurniture> changed = new ArrayList<HomePieceOfFurniture>();
    List<HomePieceOfFurniture> unchanged = new ArrayList<HomePieceOfFurniture>();
    int others = 0;
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (!(item instanceof HomePieceOfFurniture)) {
        others++;
      } else if (((HomePieceOfFurniture)item).isMovable() == locked) {
        changed.add((HomePieceOfFurniture)item);
      } else {
        unchanged.add((HomePieceOfFurniture)item);
      }
    }
    String basePlan = "walls and rooms are locked all together by {\"action\":\"lock_base_plan\"}";
    if (changed.isEmpty() && unchanged.isEmpty()) {
      throw new IOException(others > 0 ? "it changes furniture only; " + basePlan : NO_FURNITURE);
    }
    if (!changed.isEmpty()) {
      MovableUndoableEdit edit = new MovableUndoableEdit(changed.toArray(new HomePieceOfFurniture [changed.size()]), !locked);
      edit.setMovable(!locked);
      this.executor.beginTurnUpdate();
      this.homeController.getUndoableEditSupport().postEdit(edit);
      tally [4] += changed.size();
    }
    notes.add((changed.isEmpty() ? "Nothing changed"
        : (locked ? "Locked " : "Unlocked ") + describeAll(changed)
            + (locked ? ": a locked piece can't be moved in the plan with the mouse, and align, distribute and mirror leave it in place "
                + "(move, rotate and resize still change it); {\"action\":\"unlock\"} ends it"
                : ": the mouse, align, distribute and mirror move " + (changed.size() == 1 ? "it" : "them") + " again"))
        + (unchanged.isEmpty() ? ""
            : "; " + describeAll(unchanged) + (unchanged.size() == 1 ? " was " : " were ") + (locked ? "already locked" : "not locked"))
        + (others > 0 ? "; " + action + " changes furniture only: " + basePlan : ""));
  }

  /**
   * The change of the pieces that can be moved or not.
   */
  private static final class MovableUndoableEdit extends AbstractUndoableEdit {
    private final HomePieceOfFurniture [] pieces;
    private final boolean                 movable;

    MovableUndoableEdit(HomePieceOfFurniture [] pieces, boolean movable) {
      this.pieces = pieces;
      this.movable = movable;
    }

    void setMovable(boolean movable) {
      for (HomePieceOfFurniture piece : this.pieces) {
        piece.setMovable(movable);
      }
    }

    @Override
    public void undo() throws CannotUndoException {
      super.undo();
      setMovable(!this.movable);
    }

    @Override
    public void redo() throws CannotRedoException {
      super.redo();
      setMovable(this.movable);
    }

    @Override
    public String getPresentationName() {
      return this.movable ? "Unlock" : "Lock";
    }
  }

  private void lockBasePlan(boolean locked, List<String> notes) {
    if (this.home.isBasePlanLocked() == locked) {
      notes.add("The base plan was already " + (locked ? "locked" : "unlocked"));
      return;
    }
    this.executor.beginTurnUpdate();
    if (locked) {
      this.homeController.getPlanController().lockBasePlan();
    } else {
      this.homeController.getPlanController().unlockBasePlan();
    }
    notes.add(locked
        ? "Locked the base plan: the walls, the rooms, the doors, the windows and the other pieces that don't move can't be selected "
            + "or changed with the mouse in the plan any more (commands still change them); {\"action\":\"unlock_base_plan\"} ends it"
        : "Unlocked the base plan: its walls, rooms, doors and windows can be changed with the mouse again");
  }

  // ---- Measures ----

  private void measure(AssistantCommand command, List<String> notes) throws IOException {
    Object clearance = command.getParameters().get("clearance");
    if (clearance != null && !Boolean.FALSE.equals(clearance)) {
      measureClearance(command, clearance, notes);
    } else if (command.has("at")) {
      measureAt(command, notes);
    } else if (command.has("from") || command.has("to") || command.has("from_point") || command.has("to_point")) {
      measureDistance(command, notes);
    } else {
      List<Selectable> items = this.executor.resolveTargets(command);
      if (items.isEmpty()) {
        if (this.executor.getTurnUnresolvedIdCount() > 0 && (command.has("id") || command.has("ids"))) {
          // Said by the executor
          return;
        }
        throw new IOException("give \"from\" and \"to\" (ids, or \"from_point\":[x,y] and \"to_point\":[x,y]) for a distance, "
            + "\"clearance\":\"F#\" for the free space around a piece, \"at\":[x,y] for what is at a point, "
            + "or \"ids\":[...] for a bounding box");
      }
      double [] bounds = newBounds();
      for (Selectable item : items) {
        addBounds(bounds, item.getPoints());
      }
      notes.add("Bounding box of " + (items.size() <= 12 ? describeAll(items) : items.size() + " items") + ": x from "
          + format(bounds [0]) + " to " + format(bounds [2]) + ", y from " + format(bounds [1]) + " to " + format(bounds [3])
          + ", " + format(bounds [2] - bounds [0]) + " x " + format(bounds [3] - bounds [1]) + " cm, center ("
          + format((bounds [0] + bounds [2]) / 2) + ", " + format((bounds [1] + bounds [3]) / 2) + ")");
    }
  }

  /**
   * The outline of an item or a point.
   */
  private static final class Outline {
    private float [][] points;
    private boolean    closed;
    private String     description;
    private double     centerX;
    private double     centerY;
    private Level      level;
    private boolean    item;

    /**
     * Returns the segments of this outline as <code>x1, y1, x2, y2</code>.
     */
    double [][] getSegments() {
      int count = this.points.length == 1 ? 1 : this.closed ? this.points.length : this.points.length - 1;
      double [][] segments = new double [count][];
      for (int i = 0; i < count; i++) {
        float [] next = this.points [(i + 1) % this.points.length];
        segments [i] = new double [] {this.points [i][0], this.points [i][1], next [0], next [1]};
      }
      return segments;
    }

    boolean contains(double x, double y) {
      return this.closed && this.points.length >= 3 && getPath(this.points).contains(x, y);
    }
  }

  private static GeneralPath getPath(float [][] points) {
    GeneralPath path = new GeneralPath();
    path.moveTo(points [0][0], points [0][1]);
    for (int i = 1; i < points.length; i++) {
      path.lineTo(points [i][0], points [i][1]);
    }
    path.closePath();
    return path;
  }

  private static float [] getPoint(Object value) {
    if (value instanceof List && ((List<?>)value).size() >= 2 && ((List<?>)value).get(0) instanceof Number
        && ((List<?>)value).get(1) instanceof Number) {
      float [] point = {((Number)((List<?>)value).get(0)).floatValue(), ((Number)((List<?>)value).get(1)).floatValue()};
      if (!Float.isNaN(point [0]) && !Float.isNaN(point [1])
          && AssistantCommandExecutor.coordinateSane(point [0]) && AssistantCommandExecutor.coordinateSane(point [1])) {
        return point;
      }
    }
    return null;
  }

  /**
   * Returns the outline given by the parameter <code>name</code> (an id or a point) or <code>name_point</code>.
   */
  private Outline getOutline(AssistantCommand command, String name) throws IOException {
    Object value = command.getParameters().get(name + "_point");
    if (value == null) {
      value = command.getParameters().get(name);
    }
    Outline outline = new Outline();
    if (value instanceof List) {
      float [] point = getPoint(value);
      if (point == null) {
        throw new IOException("\"" + name + "_point\" is a point [x,y] in cm");
      }
      outline.points = new float [][] {point};
      outline.centerX = point [0];
      outline.centerY = point [1];
      outline.description = "the point (" + format(point [0]) + ", " + format(point [1]) + ")";
      return outline;
    } else if (value == null) {
      throw new IOException("give \"" + name + "\" (the id of an item) or \"" + name + "_point\":[x,y]");
    }
    HomeObject item = HomeItemIds.find(this.home, value.toString().trim());
    if (item instanceof HomePieceOfFurniture) {
      outline.centerX = ((HomePieceOfFurniture)item).getX();
      outline.centerY = ((HomePieceOfFurniture)item).getY();
      outline.closed = true;
    } else if (item instanceof Wall) {
      outline.centerX = (((Wall)item).getXStart() + ((Wall)item).getXEnd()) / 2;
      outline.centerY = (((Wall)item).getYStart() + ((Wall)item).getYEnd()) / 2;
      outline.closed = true;
    } else if (item instanceof Room) {
      outline.centerX = ((Room)item).getXCenter();
      outline.centerY = ((Room)item).getYCenter();
      outline.closed = true;
    } else if (item instanceof Polyline) {
      double [] bounds = getBounds(((Polyline)item).getPoints());
      outline.centerX = (bounds [0] + bounds [2]) / 2;
      outline.centerY = (bounds [1] + bounds [3]) / 2;
      outline.closed = ((Polyline)item).isClosedPath();
    } else {
      throw new IOException("no item has the id \"" + value + "\" given by \"" + name
          + "\" (a piece F#, a wall W#, a room R# or a line P#; \"" + name + "_point\":[x,y] gives a point)");
    }
    outline.points = ((Selectable)item).getPoints();
    if (outline.points.length == 0) {
      throw new IOException(describe(item) + " has no point");
    }
    outline.description = describe(item);
    outline.level = ((Elevatable)item).getLevel();
    outline.item = true;
    return outline;
  }

  private void measureDistance(AssistantCommand command, List<String> notes) throws IOException {
    Outline from = getOutline(command, "from");
    Outline to = getOutline(command, "to");
    double [] nearest = getNearestPoints(from, to);
    double centerDistance = Math.hypot(to.centerX - from.centerX, to.centerY - from.centerY);
    StringBuilder note = new StringBuilder("From " + from.description + " to " + to.description + ": ");
    boolean points = !from.item && !to.item;
    if (points) {
      note.append(format(centerDistance)).append(" cm");
    } else if (nearest [0] == 0) {
      note.append("0 cm between their outlines, which overlap or touch");
    } else {
      note.append(format(nearest [0])).append(" cm between their outlines at the nearest, from (").append(format(nearest [1]))
          .append(", ").append(format(nearest [2])).append(") to (").append(format(nearest [3])).append(", ")
          .append(format(nearest [4])).append(")");
    }
    if (!points) {
      note.append("; ").append(format(centerDistance)).append(" cm from center (").append(format(from.centerX)).append(", ")
          .append(format(from.centerY)).append(") to center (").append(format(to.centerX)).append(", ")
          .append(format(to.centerY)).append(")");
    }
    note.append(", dx ").append(format(to.centerX - from.centerX)).append(", dy ").append(format(to.centerY - from.centerY));
    if (from.item && to.item && from.level != to.level) {
      note.append(". They are on different levels (").append(from.level != null ? from.level.getName() : "none").append(" and ")
          .append(to.level != null ? to.level.getName() : "none").append("): these distances are the ones seen in the plan");
    }
    notes.add(note.toString());
  }

  /**
   * Returns the shortest distance between two outlines, 0 if they overlap, followed by the nearest point of each.
   */
  private static double [] getNearestPoints(Outline outline1, Outline outline2) {
    float [] point1 = outline1.points [0];
    float [] point2 = outline2.points [0];
    if (outline1.contains(point2 [0], point2 [1]) || outline2.contains(point1 [0], point1 [1])) {
      return new double [] {0, point2 [0], point2 [1], point2 [0], point2 [1]};
    }
    double [] nearest = {Double.MAX_VALUE, 0, 0, 0, 0};
    for (double [] segment1 : outline1.getSegments()) {
      for (double [] segment2 : outline2.getSegments()) {
        if (Line2D.linesIntersect(segment1 [0], segment1 [1], segment1 [2], segment1 [3],
                segment2 [0], segment2 [1], segment2 [2], segment2 [3])) {
          return new double [] {0, segment1 [0], segment1 [1], segment1 [0], segment1 [1]};
        }
        // Without intersection, the shortest distance starts from an end of one of the segments
        for (int i = 0; i < 4; i++) {
          double [] segment = i < 2 ? segment2 : segment1;
          double [] other = i < 2 ? segment1 : segment2;
          double x = other [i % 2 * 2];
          double y = other [i % 2 * 2 + 1];
          double [] projection = getNearestPoint(x, y, segment);
          double distance = Math.hypot(projection [0] - x, projection [1] - y);
          if (distance < nearest [0]) {
            nearest [0] = distance;
            nearest [i < 2 ? 1 : 3] = x;
            nearest [i < 2 ? 2 : 4] = y;
            nearest [i < 2 ? 3 : 1] = projection [0];
            nearest [i < 2 ? 4 : 2] = projection [1];
          }
        }
      }
    }
    if (nearest [0] < 0.005) {
      nearest [0] = 0;
    }
    return nearest;
  }

  /**
   * Returns the point of a segment the nearest to (x, y).
   */
  private static double [] getNearestPoint(double x, double y, double [] segment) {
    double dx = segment [2] - segment [0];
    double dy = segment [3] - segment [1];
    double squareLength = dx * dx + dy * dy;
    double position = squareLength == 0 ? 0
        : Math.max(0, Math.min(1, ((x - segment [0]) * dx + (y - segment [1]) * dy) / squareLength));
    return new double [] {segment [0] + position * dx, segment [1] + position * dy};
  }

  private void measureClearance(AssistantCommand command, Object clearance, List<String> notes) throws IOException {
    String token = clearance instanceof String && !"true".equalsIgnoreCase((String)clearance)
        ? (String)clearance : command.getString("id");
    HomeObject item = token != null ? HomeItemIds.find(this.home, token.trim()) : null;
    if (!(item instanceof HomePieceOfFurniture)) {
      throw new IOException(token == null ? "give the piece with \"clearance\":\"F#\""
          : "\"" + token + "\" is not the id of a piece of furniture (F#)");
    }
    HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
    float [][] points = piece.getPoints();
    if (points.length != 4) {
      throw new IOException(describe(piece) + " has no rectangular outline");
    }
    // The walls and the pieces that stand at the height of the piece
    List<Selectable> obstacles = new ArrayList<Selectable>();
    for (Wall wall : this.home.getWalls()) {
      if (wall.isAtLevel(piece.getLevel())) {
        obstacles.add(wall);
      }
    }
    float margin = Math.min(3, piece.getHeight() / 2);
    for (HomePieceOfFurniture other : this.home.getFurniture()) {
      if (other != piece && other.isVisible() && other.isAtLevel(piece.getLevel()) && !other.isDoorOrWindow()
          && other.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) == null
          && other.getElevation() + other.getHeight() > piece.getElevation() + margin
          && other.getElevation() < piece.getElevation() + piece.getHeight() - margin) {
        obstacles.add(other);
      }
    }
    // The points of a piece go around it from its back left corner
    String [] sides = {"back", "right", "front", "left"};
    String [] texts = new String [4];
    for (int side = 0; side < 4; side++) {
      float [] start = points [side];
      float [] end = points [(side + 1) % 4];
      double length = Math.hypot(end [0] - start [0], end [1] - start [1]);
      double best = Double.MAX_VALUE;
      Selectable nearest = null;
      if (length > 0.01) {
        double alongX = (end [0] - start [0]) / length;
        double alongY = (end [1] - start [1]) / length;
        // Away from the center of the piece
        double outX = alongY;
        double outY = -alongX;
        if ((piece.getX() - start [0]) * outX + (piece.getY() - start [1]) * outY > 0) {
          outX = -outX;
          outY = -outY;
        }
        // Items that only run along the ends of the side aren't in front of it
        double inset = Math.min(0.5, length / 4);
        for (Selectable obstacle : obstacles) {
          float [][] obstaclePoints = obstacle.getPoints();
          if (obstaclePoints.length < 3) {
            continue;
          }
          double distance = Double.MAX_VALUE;
          if (getPath(obstaclePoints).contains((start [0] + end [0]) / 2 + outX * 0.1, (start [1] + end [1]) / 2 + outY * 0.1)) {
            distance = 0;
          }
          for (int i = 0; i < obstaclePoints.length && distance > 0; i++) {
            float [] point1 = obstaclePoints [i];
            float [] point2 = obstaclePoints [(i + 1) % obstaclePoints.length];
            // The edge in the frame of the side: u along it, v away from the piece
            double u1 = (point1 [0] - start [0]) * alongX + (point1 [1] - start [1]) * alongY;
            double v1 = (point1 [0] - start [0]) * outX + (point1 [1] - start [1]) * outY;
            double u2 = (point2 [0] - start [0]) * alongX + (point2 [1] - start [1]) * alongY;
            double v2 = (point2 [0] - start [0]) * outX + (point2 [1] - start [1]) * outY;
            double [] clipped = clip(u1, v1, u2, v2, inset, length - inset);
            if (clipped != null) {
              // Swapped, to keep the part beyond the side, with 1 cm for the items that touch it
              clipped = clip(clipped [1], clipped [0], clipped [3], clipped [2], -1, Double.MAX_VALUE);
            }
            if (clipped != null) {
              distance = Math.min(distance, Math.max(0, Math.min(clipped [0], clipped [2])));
            }
          }
          if (distance < best) {
            best = distance;
            nearest = obstacle;
          }
        }
      }
      texts [side] = sides [side] + " " + (nearest == null || best > MAX_CLEARANCE
          ? "more than " + format(MAX_CLEARANCE) + " cm (nothing)"
          : format(best) + " cm to " + (nearest instanceof Wall ? "the wall " : "") + describe(nearest)
              + (best < 0.005 ? " (touching or overlapping)" : ""));
    }
    notes.add("Clearance of " + describe(piece) + " at (" + format(piece.getX()) + ", " + format(piece.getY()) + "), angle "
        + format(getAngle(piece)) + ": " + texts [2] + "; " + texts [0] + "; " + texts [3] + "; " + texts [1]
        + ". Its front is the side at the bottom of the plan when its angle is 0, and its left the side at the left then; "
        + "walls and pieces of its level standing at its height are counted, not rugs, doors or windows");
  }

  /**
   * Returns the part of the segment <code>(a1, b1) (a2, b2)</code> where <code>a</code> is between
   * <code>min</code> and <code>max</code>, or <code>null</code> if there is none.
   */
  private static double [] clip(double a1, double b1, double a2, double b2, double min, double max) {
    if (a1 > a2) {
      double [] clipped = clip(a2, b2, a1, b1, min, max);
      return clipped == null ? null : new double [] {clipped [2], clipped [3], clipped [0], clipped [1]};
    }
    if (a2 < min || a1 > max) {
      return null;
    } else if (a2 == a1) {
      return new double [] {a1, b1, a2, b2};
    }
    double start = Math.max(a1, min);
    double end = Math.min(a2, max);
    double slope = (b2 - b1) / (a2 - a1);
    return new double [] {start, b1 + (start - a1) * slope, end, b1 + (end - a1) * slope};
  }

  private void measureAt(AssistantCommand command, List<String> notes) throws IOException {
    float [] point = getPoint(command.getParameters().get("at"));
    if (point == null) {
      throw new IOException("\"at\" is a point [x,y] in cm");
    }
    Level level = this.home.getSelectedLevel();
    if (command.getString("level") != null) {
      level = this.executor.getStructureCommands().getLevel(command, "measure", notes, "level");
      if (level == null) {
        return;
      }
    }
    float tolerance = (float)command.getDouble("tolerance", 5);
    if (!(tolerance >= 0 && tolerance <= 1000)) {
      throw new IOException("\"tolerance\" goes from 0 to 1000 cm");
    }
    List<String> found = new ArrayList<String>();
    for (Room room : this.home.getRooms()) {
      if (room.isAtLevel(level)) {
        if (room.containsPoint(point [0], point [1], 0)) {
          found.add("in the room " + describe(room));
        } else if (room.containsPoint(point [0], point [1], tolerance)) {
          found.add("at the edge of the room " + describe(room));
        }
      }
    }
    for (Wall wall : this.home.getWalls()) {
      if (wall.isAtLevel(level)) {
        if (wall.containsPoint(point [0], point [1], 0)) {
          found.add("in the wall " + describe(wall));
        } else if (wall.containsPoint(point [0], point [1], tolerance)) {
          found.add("beside the wall " + describe(wall));
        }
      }
    }
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.isAtLevel(level)) {
        boolean inside = piece.containsPoint(point [0], point [1], 0);
        if (inside || piece.containsPoint(point [0], point [1], tolerance)) {
          found.add((inside ? "on " : "beside ") + describe(piece) + " (from " + format(piece.getElevation()) + " to "
              + format(piece.getElevation() + piece.getHeight()) + " cm above the floor" + (piece.isVisible() ? "" : ", hidden") + ")");
        }
      }
    }
    for (Polyline polyline : this.home.getPolylines()) {
      if (polyline.isAtLevel(level) && polyline.containsPoint(point [0], point [1], tolerance)) {
        found.add("on the line " + describe(polyline));
      }
    }
    int dimensionLines = 0;
    for (DimensionLine dimensionLine : this.home.getDimensionLines()) {
      if (dimensionLine.isAtLevel(level) && dimensionLine.containsPoint(point [0], point [1], tolerance)) {
        dimensionLines++;
      }
    }
    if (dimensionLines > 0) {
      found.add("on " + dimensionLines + (dimensionLines == 1 ? " dimension line" : " dimension lines"));
    }
    StringBuilder note = new StringBuilder("The point (" + format(point [0]) + ", " + format(point [1]) + ")");
    if (level != null) {
      note.append(" of the level ").append(this.executor.getId(level)).append(" \"").append(level.getName())
          .append("\", whose floor is at ").append(format(level.getElevation())).append(" cm,");
    } else {
      note.append(", floor at 0 cm,");
    }
    if (found.isEmpty()) {
      note.append(" is in no room and has no wall, furniture or line within ").append(format(tolerance)).append(" cm");
    } else {
      note.append(" is ");
      for (int i = 0; i < found.size(); i++) {
        note.append(i > 0 ? "; " : "").append(found.get(i));
      }
      note.append(" (items within ").append(format(tolerance)).append(" cm are counted)");
    }
    notes.add(note.toString());
  }
}
