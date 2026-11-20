/*
 * AssistantStructureCommands.java
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;

import com.eteks.sweethome3d.model.Baseboard;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeMaterial;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands of the design assistant that manage the levels of a home
 * (<code>delete_level</code>, <code>modify_level</code>, <code>merge_levels</code>),
 * change its walls (<code>modify_wall</code>), choose how furniture is drawn
 * in the plan of upper levels (<code>set_plan_visibility</code>) and describe
 * one item completely (<code>describe_item</code>).
 * @author Sweet Home 3D
 */
class AssistantStructureCommands {
  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantStructureCommands(Home home, UserPreferences preferences, HomeController homeController,
                             AssistantCommandExecutor executor) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    if ("delete_level".equals(action) || "remove_level".equals(action)) {
      deleteLevel(command, tally, notes);
    } else if ("modify_level".equals(action) || "rename_level".equals(action)) {
      modifyLevel(command, notes);
    } else if ("merge_levels".equals(action) || "merge_level".equals(action)) {
      mergeLevels(command, tally, notes);
    } else if ("modify_wall".equals(action) || "modify_walls".equals(action)) {
      modifyWalls(command, tally, notes);
    } else if ("set_plan_visibility".equals(action) || "set_plan_on_upper_levels".equals(action)) {
      setPlanVisibility(command, tally, notes);
    } else if ("describe_item".equals(action) || "describe_items".equals(action)) {
      describeItems(command, notes);
    } else {
      return false;
    }
    return true;
  }

  // ---- Levels ----

  /**
   * Returns the level designated by the first of the given parameters of
   * <code>command</code> that has a value: an id <code>L#</code> or a name
   * only one level has. Adds a note and returns <code>null</code> otherwise.
   */
  Level getLevel(AssistantCommand command, String action, List<String> notes, String... parameters) {
    String token = null;
    for (String parameter : parameters) {
      if (command.getString(parameter) != null && command.getString(parameter).trim().length() > 0) {
        token = command.getString(parameter).trim();
        break;
      }
    }
    if (token == null) {
      notes.add("Ignored " + action + ": give the level with \"" + parameters [0] + "\" (its name or its id L#)");
      return null;
    }
    List<Level> levels = HomeItemIds.findLevels(this.home, token);
    if (levels.isEmpty()) {
      notes.add("Ignored " + action + ": there is no level \"" + token + "\"");
      return null;
    } else if (levels.size() > 1) {
      StringBuilder ids = new StringBuilder();
      for (Level level : levels) {
        ids.append(ids.length() > 0 ? ", " : "").append(this.executor.getId(level)).append(" at ")
            .append(format(level.getElevation())).append(" cm with ").append(describeContents(getContents(level)));
      }
      notes.add("Ignored " + action + ": " + levels.size() + " levels are named \"" + token + "\" (" + ids
          + "); give the id of one");
      return null;
    }
    return levels.get(0);
  }

  /**
   * Returns the furniture, walls, rooms, labels, dimension lines and polylines of <code>level</code>.
   */
  List<Selectable> getContents(Level level) {
    List<Selectable> items = new ArrayList<Selectable>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getLevel() == level) {
        items.add(piece);
      }
    }
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == level) {
        items.add(wall);
      }
    }
    for (Room room : this.home.getRooms()) {
      if (room.getLevel() == level) {
        items.add(room);
      }
    }
    for (Label label : this.home.getLabels()) {
      if (label.getLevel() == level) {
        items.add(label);
      }
    }
    for (DimensionLine dimensionLine : this.home.getDimensionLines()) {
      if (dimensionLine.getLevel() == level) {
        items.add(dimensionLine);
      }
    }
    for (Polyline polyline : this.home.getPolylines()) {
      if (polyline.getLevel() == level) {
        items.add(polyline);
      }
    }
    return items;
  }

  /**
   * Returns a text like <code>2 rooms, 8 walls, 5 pieces of furniture</code>, or <code>nothing</code>.
   */
  static String describeContents(List<Selectable> items) {
    int [] counts = new int [4];
    for (Selectable item : items) {
      counts [item instanceof Room ? 0 : item instanceof Wall ? 1 : item instanceof HomePieceOfFurniture ? 2 : 3]++;
    }
    String [][] names = {{"room", "rooms"}, {"wall", "walls"}, {"piece of furniture", "pieces of furniture"},
                         {"label or dimension", "labels and dimensions"}};
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < counts.length; i++) {
      if (counts [i] > 0) {
        text.append(text.length() > 0 ? ", " : "").append(counts [i]).append(' ').append(names [i][counts [i] == 1 ? 0 : 1]);
      }
    }
    return text.length() > 0 ? text.toString() : "nothing";
  }

  private void deleteLevel(AssistantCommand command, int [] tally, List<String> notes) {
    Level level = getLevel(command, "delete_level", notes, "name", "id", "level");
    if (level == null) {
      return;
    }
    if (this.home.getLevels().size() < 2) {
      notes.add("Ignored delete_level: \"" + level.getName() + "\" is the only level of the home");
      return;
    }
    List<Selectable> contents = getContents(level);
    String target = command.getString("move_contents_to");
    if (!contents.isEmpty() && !command.getBoolean("delete_contents", false)) {
      if (target == null) {
        StringBuilder names = new StringBuilder();
        int named = 0;
        for (Selectable item : contents) {
          String name = item instanceof HomePieceOfFurniture ? ((HomePieceOfFurniture)item).getName()
              : item instanceof Room ? ((Room)item).getName() : null;
          if (name != null && named < 8) {
            names.append(named++ > 0 ? ", " : "").append(this.executor.getId(item)).append(" \"").append(name).append('"');
          }
        }
        notes.add("Ignored delete_level: the level " + describe(level) + " isn't empty, it has "
            + describeContents(contents) + (names.length() > 0 ? " (" + names + (named < contents.size() ? "..." : "") + ")" : "")
            + ". Give \"move_contents_to\":\"<level>\" to keep them, or \"delete_contents\":true to delete them with the level");
        return;
      }
    }
    if (target != null && !contents.isEmpty() && !command.getBoolean("delete_contents", false)) {
      AssistantCommand targetCommand = new AssistantCommand("delete_level",
          java.util.Collections.<String, Object>singletonMap("move_contents_to", target));
      Level targetLevel = getLevel(targetCommand, "delete_level", notes, "move_contents_to");
      if (targetLevel == null) {
        return;
      } else if (targetLevel == level) {
        notes.add("Ignored delete_level: \"move_contents_to\" is the level to delete");
        return;
      }
      moveContents(level, targetLevel, contents, command.getBoolean("keep_heights", true), tally, notes);
      contents.clear();
    }
    String description = describe(level);
    delete(level);
    tally [5] += contents.size();
    notes.add("Deleted the level " + description
        + (contents.isEmpty() ? "" : " and what was on it: " + describeContents(contents)));
  }

  void delete(Level level) {
    Level selectedLevel = this.home.getSelectedLevel();
    this.executor.beginTurnUpdate();
    this.home.setSelectedLevel(level);
    this.homeController.getPlanController().deleteSelectedLevel();
    if (selectedLevel != level && this.home.getLevels().contains(selectedLevel)) {
      this.home.setSelectedLevel(selectedLevel);
    }
  }

  /**
   * Moves the items of a level to an other one. Furniture keeps its height
   * above the ground if <code>keepHeights</code> is <code>true</code>.
   */
  private void moveContents(final Level level, final Level targetLevel, final List<Selectable> contents,
                            final boolean keepHeights, int [] tally, List<String> notes) {
    final float offset = level.getElevation() - targetLevel.getElevation();
    // Walls and rooms stand on the floor of their level
    int shifted = 0;
    for (Selectable item : contents) {
      if (offset != 0 && (item instanceof Wall || item instanceof Room)) {
        shifted++;
      }
    }
    this.executor.postModification(contents, new Runnable() {
        public void run() {
          for (Selectable item : contents) {
            if (item instanceof HomePieceOfFurniture) {
              HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
              piece.setLevel(targetLevel);
              if (keepHeights && offset != 0) {
                piece.setElevation(piece.getElevation() + offset);
                if (piece.getElevation() < 0) {
                  piece.setProperty(AssistantCommandExecutor.BELOW_FLOOR_PROPERTY, "true");
                }
              }
            } else if (item instanceof Wall) {
              ((Wall)item).setLevel(targetLevel);
            } else if (item instanceof Room) {
              ((Room)item).setLevel(targetLevel);
            } else if (item instanceof Label) {
              ((Label)item).setLevel(targetLevel);
            } else if (item instanceof DimensionLine) {
              ((DimensionLine)item).setLevel(targetLevel);
            } else if (item instanceof Polyline) {
              ((Polyline)item).setLevel(targetLevel);
            }
          }
        }
      });
    tally [4] += contents.size();
    notes.add("Moved " + describeContents(contents) + " from the level " + describe(level) + " to " + describe(targetLevel)
        + (offset != 0 && keepHeights ? "; furniture keeps its height above the ground (elevation "
            + (offset > 0 ? "+" : "") + format(offset) + " cm)" : "")
        + (shifted > 0 ? "; " + shifted + (shifted == 1 ? " wall or room now stands" : " walls and rooms now stand")
            + " on the floor of the new level, " + format(Math.abs(offset)) + " cm " + (offset > 0 ? "lower" : "higher") : ""));
  }

  private void mergeLevels(AssistantCommand command, int [] tally, List<String> notes) {
    Level level = getLevel(command, "merge_levels", notes, "from", "source");
    if (level == null) {
      return;
    }
    Level targetLevel = getLevel(command, "merge_levels", notes, "into", "to", "target");
    if (targetLevel == null) {
      return;
    } else if (targetLevel == level) {
      notes.add("Ignored merge_levels: \"from\" and \"into\" are the same level");
      return;
    }
    List<Selectable> contents = getContents(level);
    if (!contents.isEmpty()) {
      moveContents(level, targetLevel, contents, command.getBoolean("keep_heights", true), tally, notes);
    }
    String description = describe(level);
    delete(level);
    notes.add("Deleted the level " + description);
  }

  private void modifyLevel(final AssistantCommand command, List<String> notes) {
    final Level level = getLevel(command, "modify_level", notes, "id", "level", "name");
    if (level == null) {
      return;
    }
    final String newName = command.getString("new_name");
    final double elevation = command.getDouble("elevation", Double.NaN);
    final double height = command.getDouble("height", Double.NaN);
    final double floorThickness = command.getDouble("floor_thickness", Double.NaN);
    final double elevationIndex = command.getDouble("elevation_index", Double.NaN);
    if (!AssistantCommandExecutor.sizeSane(elevation) || !AssistantCommandExecutor.sizeSane(height)
        || !AssistantCommandExecutor.sizeSane(floorThickness) || height <= 0 || floorThickness < 0) {
      notes.add("Ignored modify_level with out-of-range values");
      return;
    }
    if (newName == null && Double.isNaN(elevation) && Double.isNaN(height) && Double.isNaN(floorThickness)
        && Double.isNaN(elevationIndex) && !command.has("viewable") && !command.has("visible")) {
      notes.add("Ignored modify_level: give at least one of \"new_name\", \"elevation\", \"height\", "
          + "\"floor_thickness\", \"viewable\" and \"elevation_index\"");
      return;
    }
    final Object [] before = getState(level);
    if (newName != null && newName.trim().length() > 0) {
      level.setName(newName.trim());
    }
    if (!Double.isNaN(elevation)) {
      level.setElevation((float)elevation);
    }
    if (!Double.isNaN(height)) {
      level.setHeight((float)height);
    }
    if (!Double.isNaN(floorThickness)) {
      level.setFloorThickness((float)floorThickness);
    }
    if (command.has("viewable")) {
      level.setViewable(command.getBoolean("viewable", level.isViewable()));
    } else if (command.has("visible")) {
      level.setViewable(command.getBoolean("visible", level.isViewable()));
    }
    if (!Double.isNaN(elevationIndex) && elevationIndex >= 0) {
      level.setElevationIndex((int)elevationIndex);
    }
    final Object [] after = getState(level);
    if (!level.isViewable() && this.home.getSelectedLevel() == level) {
      // The plan can't display a level that isn't viewable
      for (Level other : this.home.getLevels()) {
        if (other.isViewable()) {
          this.home.setSelectedLevel(other);
          break;
        }
      }
    }
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() {
          super.undo();
          setState(level, before);
        }

        @Override
        public void redo() {
          super.redo();
          setState(level, after);
        }

        @Override
        public String getPresentationName() {
          return "AI assistant edit";
        }
      });
    int homonyms = 0;
    for (Level other : this.home.getLevels()) {
      if (other != level && level.getName() != null && level.getName().equalsIgnoreCase(other.getName())) {
        homonyms++;
      }
    }
    notes.add("Modified the level " + describe(level) + ": height " + format(level.getHeight()) + " cm, floor thickness "
        + format(level.getFloorThickness()) + " cm, " + (level.isViewable() ? "viewable" : "not viewable (hidden in the plan and in the 3D view)")
        + (homonyms > 0 ? "; " + homonyms + (homonyms == 1 ? " other level has" : " other levels have")
            + " the same name, use ids to tell them apart" : ""));
  }

  private static Object [] getState(Level level) {
    return new Object [] {level.getName(), level.getElevation(), level.getHeight(), level.getFloorThickness(),
                          level.isViewable(), level.getElevationIndex()};
  }

  private static void setState(Level level, Object [] state) {
    level.setName((String)state [0]);
    level.setElevation((Float)state [1]);
    level.setHeight((Float)state [2]);
    level.setFloorThickness((Float)state [3]);
    level.setViewable((Boolean)state [4]);
    level.setElevationIndex((Integer)state [5]);
  }

  private String describe(Level level) {
    return this.executor.getId(level) + " \"" + level.getName() + "\" at " + format(level.getElevation()) + " cm";
  }

  // ---- Walls ----

  private void modifyWalls(final AssistantCommand command, int [] tally, List<String> notes) {
    final List<Selectable> walls = new ArrayList<Selectable>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Wall) {
        walls.add(item);
      }
    }
    if (walls.isEmpty()) {
      notes.add("Ignored modify_wall: give walls with \"id\", \"ids\", \"where\" or \"target\":\"selection\"");
      return;
    }
    final double height = command.getDouble("height", Double.NaN);
    final double heightAtEnd = command.getDouble("height_at_end", Double.NaN);
    final double thickness = command.getDouble("thickness", Double.NaN);
    final double arcExtent = command.getDouble("arc_extent", Double.NaN);
    final double [] ends = {command.getDouble("x1", Double.NaN), command.getDouble("y1", Double.NaN),
                            command.getDouble("x2", Double.NaN), command.getDouble("y2", Double.NaN)};
    if (!AssistantCommandExecutor.sizeSane(height) || !AssistantCommandExecutor.sizeSane(heightAtEnd)
        || !AssistantCommandExecutor.sizeSane(thickness) || height <= 0 || heightAtEnd < 0 || thickness <= 0
        || Math.abs(arcExtent) > 340
        || !AssistantCommandExecutor.coordinateSane(ends [0]) || !AssistantCommandExecutor.coordinateSane(ends [1])
        || !AssistantCommandExecutor.coordinateSane(ends [2]) || !AssistantCommandExecutor.coordinateSane(ends [3])) {
      notes.add("Ignored modify_wall with out-of-range values (arc_extent is between -340 and 340 degrees)");
      return;
    }
    boolean moved = !Double.isNaN(ends [0]) || !Double.isNaN(ends [1]) || !Double.isNaN(ends [2]) || !Double.isNaN(ends [3]);
    if (moved && walls.size() > 1) {
      notes.add("Ignored modify_wall: \"x1\", \"y1\", \"x2\" and \"y2\" move one wall, not " + walls.size());
      return;
    }
    final Integer topColor = AssistantCommandExecutor.parseColor(command.getString("top_color"));
    final boolean removeTopColor = command.has("top_color") && topColor == null;
    // Baseboards: one description, or one for each side
    final Baseboard [] baseboards = new Baseboard [2];
    final boolean [] baseboardChanged = new boolean [2];
    String [] baseboardParameters = {"baseboard", "left_baseboard", "right_baseboard"};
    for (int i = 0; i < baseboardParameters.length; i++) {
      if (!command.has(baseboardParameters [i])) {
        continue;
      }
      Object value = command.getParameters().get(baseboardParameters [i]);
      Baseboard baseboard = null;
      String side = i == 1 ? "left" : i == 2 ? "right" : "both";
      if (value instanceof Map) {
        Map<?, ?> description = (Map<?, ?>)value;
        if (i == 0 && description.get("side") != null) {
          side = description.get("side").toString().trim().toLowerCase(Locale.ENGLISH);
        }
        double baseboardHeight = description.get("height") instanceof Number
            ? ((Number)description.get("height")).doubleValue() : this.preferences.getNewWallBaseboardHeight();
        double baseboardThickness = description.get("thickness") instanceof Number
            ? ((Number)description.get("thickness")).doubleValue() : this.preferences.getNewWallBaseboardThickness();
        if (!(baseboardHeight > 0) || !(baseboardThickness > 0) || baseboardHeight > 2000 || baseboardThickness > 100) {
          notes.add("Ignored modify_wall: the height and the thickness of a baseboard must be positive");
          return;
        }
        Integer color = description.get("color") != null
            ? AssistantCommandExecutor.parseColor(description.get("color").toString()) : null;
        HomeTexture texture = null;
        if (description.get("texture") != null || description.get("catalog_id") != null) {
          CatalogTexture catalogTexture = this.executor.getBuildingCommands().findTexture(
              description.get("texture") != null ? description.get("texture").toString() : null,
              description.get("category") != null ? description.get("category").toString() : null,
              description.get("catalog_id") != null ? description.get("catalog_id").toString() : null,
              "modify_wall", notes, null);
          if (catalogTexture == null) {
            return;
          }
          texture = AssistantBuildingCommands.createTexture(catalogTexture,
              AssistantBuildingCommands.getTexturePlacement(description));
        }
        baseboard = Baseboard.getInstance((float)baseboardThickness, (float)baseboardHeight, color, texture);
      } else if (value != null && !Boolean.FALSE.equals(value) && !"none".equalsIgnoreCase(value.toString())) {
        notes.add("Ignored modify_wall: \"" + baseboardParameters [i] + "\" must be {\"height\":N,\"thickness\":N,"
            + "\"color\":\"#RRGGBB\" or \"texture\":\"<name>\",\"side\":\"left|right|both\"} or false to remove it");
        return;
      }
      if (!"right".equals(side)) {
        baseboards [0] = baseboard;
        baseboardChanged [0] = true;
      }
      if (!"left".equals(side)) {
        baseboards [1] = baseboard;
        baseboardChanged [1] = true;
      }
    }
    if (Double.isNaN(height) && !command.has("height_at_end") && Double.isNaN(thickness) && !command.has("arc_extent")
        && !moved && !command.has("top_color") && !baseboardChanged [0] && !baseboardChanged [1]) {
      notes.add("Ignored modify_wall: give at least one of \"height\", \"height_at_end\", \"thickness\", \"arc_extent\", "
          + "\"top_color\", \"baseboard\", \"x1\", \"y1\", \"x2\" and \"y2\"");
      return;
    }
    this.executor.postModification(walls, new Runnable() {
        public void run() {
          for (Selectable item : walls) {
            Wall wall = (Wall)item;
            if (!Double.isNaN(ends [0])) {
              wall.setXStart((float)ends [0]);
            }
            if (!Double.isNaN(ends [1])) {
              wall.setYStart((float)ends [1]);
            }
            if (!Double.isNaN(ends [2])) {
              wall.setXEnd((float)ends [2]);
            }
            if (!Double.isNaN(ends [3])) {
              wall.setYEnd((float)ends [3]);
            }
            if (!Double.isNaN(height)) {
              wall.setHeight(Float.valueOf((float)height));
            }
            if (command.has("height_at_end")) {
              // No value, 0 or the height at start: a wall of constant height
              wall.setHeightAtEnd(heightAtEnd > 0 && (wall.getHeight() == null || heightAtEnd != wall.getHeight())
                  ? Float.valueOf((float)heightAtEnd) : null);
            }
            if (!Double.isNaN(thickness)) {
              wall.setThickness((float)thickness);
            }
            if (command.has("arc_extent")) {
              wall.setArcExtent(Double.isNaN(arcExtent) || arcExtent == 0
                  ? null : Float.valueOf((float)Math.toRadians(arcExtent)));
            }
            if (topColor != null) {
              wall.setTopColor(topColor);
            } else if (removeTopColor) {
              wall.setTopColor(null);
            }
            if (baseboardChanged [0]) {
              wall.setLeftSideBaseboard(baseboards [0]);
            }
            if (baseboardChanged [1]) {
              wall.setRightSideBaseboard(baseboards [1]);
            }
          }
        }
      });
    tally [4] += walls.size();
    StringBuilder note = new StringBuilder("Modified ");
    for (int i = 0; i < walls.size() && i < 10; i++) {
      note.append(i > 0 ? "; " : "").append(describeWall((Wall)walls.get(i), false));
    }
    if (walls.size() > 10) {
      note.append("; and ").append(walls.size() - 10).append(" more walls");
    }
    notes.add(note.toString());
  }

  private String describeWall(Wall wall, boolean complete) {
    float height = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
    StringBuilder text = new StringBuilder(String.valueOf(this.executor.getId(wall)));
    text.append(": (").append(format(wall.getXStart())).append(", ").append(format(wall.getYStart())).append(") to (")
        .append(format(wall.getXEnd())).append(", ").append(format(wall.getYEnd())).append("), ")
        .append(format(wall.getThickness())).append(" thick, height ").append(format(height));
    if (wall.getHeightAtEnd() != null) {
      text.append(" at start and ").append(format(wall.getHeightAtEnd())).append(" at end");
    }
    if (wall.getArcExtent() != null) {
      text.append(", arc of ").append(format((float)Math.toDegrees(wall.getArcExtent()))).append(" deg");
    }
    if (wall.getTopColor() != null) {
      text.append(", top color ").append(color(wall.getTopColor()));
    }
    for (int side = 0; side < 2; side++) {
      Baseboard baseboard = side == 0 ? wall.getLeftSideBaseboard() : wall.getRightSideBaseboard();
      if (baseboard != null) {
        text.append(", ").append(side == 0 ? "left" : "right").append(" baseboard ").append(format(baseboard.getHeight()))
            .append(" high and ").append(format(baseboard.getThickness())).append(" thick")
            .append(describeFinish(baseboard.getColor(), baseboard.getTexture()));
      }
    }
    if (complete) {
      text.append(", length ").append(format(wall.getLength())).append(" cm");
      text.append(", left side").append(describeFinish(wall.getLeftSideColor(), wall.getLeftSideTexture()));
      text.append(", right side").append(describeFinish(wall.getRightSideColor(), wall.getRightSideTexture()));
      if (wall.getWallAtStart() != null) {
        text.append(", joined at start to ").append(this.executor.getId(wall.getWallAtStart()));
      }
      if (wall.getWallAtEnd() != null) {
        text.append(", joined at end to ").append(this.executor.getId(wall.getWallAtEnd()));
      }
      text.append(", the left side is at the left of who walks from start to end");
    }
    return text.toString();
  }

  private static String describeFinish(Integer color, HomeTexture texture) {
    if (texture != null) {
      return " texture \"" + texture.getName() + "\""
          + (texture.getCatalogId() != null ? " (catalog_id " + texture.getCatalogId() + ")" : "")
          + (texture.getScale() != 1 ? " scale " + texture.getScale() : "")
          + (texture.getAngle() != 0 ? " angle " + Math.round(Math.toDegrees(texture.getAngle())) + " deg" : "");
    } else if (color != null) {
      return " color " + color(color);
    } else {
      return " default color";
    }
  }

  private static String color(int color) {
    return String.format("#%06X", color & 0xFFFFFF);
  }

  // ---- Plan ----

  private void setPlanVisibility(AssistantCommand command, int [] tally, List<String> notes) {
    String text = command.getString("value");
    if (text == null) {
      text = command.getString("plan_on_upper_levels");
    }
    final String value = FurniturePlanVisibility.parse(text);
    if (value == null) {
      notes.add("Ignored set_plan_visibility: \"value\" must be \"draw\", \"outline\" or \"hide\"");
      return;
    }
    final List<Selectable> furniture = new ArrayList<Selectable>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture) {
        furniture.add(item);
      }
    }
    if (command.has("tag") || command.has("level") || command.has("type")) {
      // The criteria may be given directly in the command
      Map<String, Object> where = new HashMap<String, Object>();
      for (String criterion : new String [] {"tag", "level", "type"}) {
        if (command.has(criterion)) {
          where.put(criterion, command.getParameters().get(criterion));
        }
      }
      for (Selectable item : this.executor.resolveWhere(where)) {
        if (item instanceof HomePieceOfFurniture && !furniture.contains(item)) {
          furniture.add(item);
        }
      }
    }
    if (furniture.isEmpty()) {
      notes.add("set_plan_visibility found no furniture: give \"ids\", \"tag\", \"level\" or \"where\"");
      return;
    }
    this.executor.postModification(furniture, new Runnable() {
        public void run() {
          for (Selectable item : furniture) {
            setPlanVisibility((HomePieceOfFurniture)item, value);
          }
        }
      });
    tally [4] += furniture.size();
    notes.add((FurniturePlanVisibility.DRAW.equals(value) ? "The plans of the levels above now draw "
            : FurniturePlanVisibility.OUTLINE.equals(value) ? "The plans of the levels above now show a dashed outline of "
            : "The plans of the levels above don't show anymore ")
        + furniture.size() + (furniture.size() == 1 ? " piece" : " pieces")
        + " (pieces are always drawn in the plan of their own level)");
  }

  static void setPlanVisibility(HomePieceOfFurniture piece, String value) {
    piece.setProperty(FurniturePlanVisibility.PROPERTY, value);
    if (piece instanceof HomeFurnitureGroup) {
      for (HomePieceOfFurniture child : ((HomeFurnitureGroup)piece).getFurniture()) {
        setPlanVisibility(child, value);
      }
    }
  }

  // ---- Description ----

  private void describeItems(AssistantCommand command, List<String> notes) {
    List<Object> items = new ArrayList<Object>(this.executor.resolveTargets(command));
    List<String> tokens = new ArrayList<String>(command.getStringList("ids"));
    if (command.getString("id") != null) {
      tokens.add(command.getString("id"));
    }
    for (String token : tokens) {
      HomeObject item = HomeItemIds.find(this.home, token);
      if (item instanceof Level) {
        items.add(item);
      } else if (item == null && token.trim().matches("[Ll]\\s*\\d+")) {
        notes.add("There is no level " + token.trim().toUpperCase(Locale.ENGLISH));
      }
    }
    if (items.isEmpty()) {
      if (this.executor.getTurnUnresolvedIdCount() == 0) {
        notes.add("Ignored describe_item: give \"id\" or \"ids\" (F#, W#, R# or L#)");
      }
      return;
    }
    int described = 0;
    for (Object item : items) {
      if (described++ >= 20) {
        notes.add("describe_item describes 20 items at most at a time");
        break;
      }
      if (item instanceof HomePieceOfFurniture) {
        notes.add(describePiece((HomePieceOfFurniture)item));
      } else if (item instanceof Wall) {
        Wall wall = (Wall)item;
        notes.add("Wall " + describeWall(wall, true) + describeLevel(wall.getLevel()) + describeProperties(wall));
      } else if (item instanceof Room) {
        notes.add(describeRoom((Room)item));
      } else if (item instanceof Level) {
        Level level = (Level)item;
        notes.add("Level " + describe(level) + ": height " + format(level.getHeight()) + " cm, floor thickness "
            + format(level.getFloorThickness()) + " cm, elevation index " + level.getElevationIndex() + ", "
            + (level.isViewable() ? "viewable" : "not viewable") + ", contains " + describeContents(getContents(level))
            + (level == this.home.getSelectedLevel() ? ", selected" : "")
            + (level.getBackgroundImage() != null ? ", with a background image" : ""));
      }
    }
  }

  private String describeLevel(Level level) {
    return level != null ? ", level " + describe(level) : "";
  }

  private static String describeProperties(HomeObject item) {
    StringBuilder text = new StringBuilder();
    List<String> names = new ArrayList<String>(item.getPropertyNames());
    java.util.Collections.sort(names);
    for (String name : names) {
      String value = item.getProperty(name);
      if (value != null && !HomeItemIds.ID_PROPERTY.equals(name) && !HomeItemTags.TAGS_PROPERTY.equals(name)) {
        text.append(text.length() == 0 ? ", properties: " : ", ").append(name).append('=')
            .append(value.length() > 60 ? value.substring(0, 60) + "..." : value);
      }
    }
    return text.append(", object id ").append(item.getId()).toString();
  }

  private String describePiece(HomePieceOfFurniture piece) {
    StringBuilder text = new StringBuilder(HomeItemTags.getType(piece).equals(HomeItemTags.TYPE_FURNITURE)
        ? "Piece of furniture " : "Piece (" + HomeItemTags.getType(piece) + ") ");
    Level level = piece.getLevel();
    text.append(this.executor.getId(piece)).append(" \"").append(piece.getName()).append("\": center (")
        .append(format(piece.getX())).append(", ").append(format(piece.getY())).append(") cm, size ")
        .append(format(piece.getWidth())).append(" x ").append(format(piece.getDepth())).append(" x ")
        .append(format(piece.getHeight())).append(" cm (width x depth x height), elevation ")
        .append(format(piece.getElevation())).append(" cm above the floor of its level");
    if (level != null) {
      text.append(" (bottom at ").append(format(level.getElevation() + piece.getElevation())).append(" cm and top at ")
          .append(format(level.getElevation() + piece.getElevation() + piece.getHeightInPlan())).append(" cm above the ground)");
    }
    text.append(", angle ").append(format((float)Math.toDegrees(piece.getAngle()))).append(" deg");
    if (piece.getPitch() != 0) {
      text.append(", pitch ").append(format((float)Math.toDegrees(piece.getPitch()))).append(" deg");
    }
    if (piece.getRoll() != 0) {
      text.append(", roll ").append(format((float)Math.toDegrees(piece.getRoll()))).append(" deg");
    }
    if (piece.isHorizontallyRotated()) {
      text.append(", size in plan ").append(format(piece.getWidthInPlan())).append(" x ").append(format(piece.getDepthInPlan()))
          .append(" x ").append(format(piece.getHeightInPlan())).append(" cm");
    }
    text.append(describeLevel(level));
    List<String> tags = HomeItemTags.getTags(piece);
    text.append(tags.isEmpty() ? ", no tags" : ", tags: " + HomeItemTags.formatTags(tags));
    if (!(piece instanceof HomeFurnitureGroup)) {
      text.append(piece.getTexture() != null || piece.getColor() != null
          ? "," + describeFinish(piece.getColor(), piece.getTexture()) : ", colors of its model");
    }
    HomeMaterial [] materials = piece.getModelMaterials();
    if (materials != null) {
      int modified = 0;
      for (HomeMaterial material : materials) {
        if (material != null) {
          modified++;
        }
      }
      text.append(", ").append(modified).append(" modified materials");
    }
    if (piece.getModelSize() != null) {
      text.append(", model file of ").append(format(piece.getModelSize() / 1024f)).append(" KB");
    }
    if (piece.isModelMirrored()) {
      text.append(", mirrored");
    }
    text.append(piece.isVisible() ? "" : ", hidden").append(piece.isMovable() ? "" : ", not movable")
        .append(piece.isResizable() ? "" : ", not resizable").append(piece.isTexturable() ? "" : ", not texturable");
    if (piece.getCatalogId() != null) {
      text.append(", catalog id ").append(piece.getCatalogId());
    }
    text.append(", in the plans of the levels above: ").append(FurniturePlanVisibility.get(piece));
    if (piece instanceof HomeFurnitureGroup) {
      text.append(", group of ").append(((HomeFurnitureGroup)piece).getFurniture().size()).append(" pieces");
    }
    if (piece.getDescription() != null) {
      text.append(", description: ").append(piece.getDescription());
    }
    return text.append(describeProperties(piece)).toString();
  }

  private String describeRoom(Room room) {
    StringBuilder text = new StringBuilder("Room ");
    text.append(this.executor.getId(room)).append(room.getName() != null ? " \"" + room.getName() + "\"" : " (unnamed)")
        .append(": ").append(format(room.getArea() / 10000)).append(" m2, points ");
    float [][] points = room.getPoints();
    for (int i = 0; i < points.length; i++) {
      text.append(i > 0 ? ", " : "").append('[').append(format(points [i][0])).append(", ")
          .append(format(points [i][1])).append(']');
    }
    text.append(describeLevel(room.getLevel()));
    text.append(", floor ").append(room.isFloorVisible() ? "visible" : "hidden")
        .append(describeFinish(room.getFloorColor(), room.getFloorTexture()));
    text.append(", ceiling ").append(room.isCeilingVisible() ? "visible" : "hidden")
        .append(describeFinish(room.getCeilingColor(), room.getCeilingTexture()));
    text.append(room.isAreaVisible() ? ", area displayed" : ", area not displayed");
    return text.append(describeProperties(room)).toString();
  }

  static String format(float value) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMaximumFractionDigits(2);
    format.setGroupingUsed(false);
    return format.format(value);
  }
}
