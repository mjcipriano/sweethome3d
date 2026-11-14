/*
 * AssistantPlanCommands.java
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.eteks.sweethome3d.model.BackgroundImage;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands of the design assistant about the plan as a whole: moving
 * items to another level, the image of a floor plan displayed behind the plan
 * to trace it, and the report of the quantities of the home.
 * @author Sweet Home 3D
 */
class AssistantPlanCommands {
  private static final long MAX_IMAGE_LENGTH = 30L * 1024 * 1024;

  private final Home                      home;
  private final HomeController            homeController;
  private final AssistantCommandExecutor  executor;
  private final AssistantBuildingCommands buildingCommands;
  private final AssistantObjectCommands   objectCommands;

  AssistantPlanCommands(Home home, HomeController homeController, AssistantCommandExecutor executor,
                        AssistantBuildingCommands buildingCommands, AssistantObjectCommands objectCommands) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
    this.buildingCommands = buildingCommands;
    this.objectCommands = objectCommands;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("move_to_level".equals(action) || "set_level".equals(action)) {
        moveToLevel(command, tally, notes);
      } else if ("set_background_image".equals(action) || "import_floor_plan".equals(action)) {
        setBackgroundImage(command, notes);
      } else if ("remove_background_image".equals(action)) {
        changeBackgroundImage(null, notes, "Removed the background image");
      } else if ("home_report".equals(action) || "report".equals(action)) {
        notes.add(HomeReport.describe(this.home));
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    }
    return true;
  }

  /**
   * Moves furniture, walls and rooms to another level.
   */
  private void moveToLevel(AssistantCommand command, int [] tally, List<String> notes) {
    final List<Selectable> targets = this.executor.resolveTargets(command);
    String levelName = command.getString("level");
    if (levelName == null) {
      levelName = command.getString("name");
    }
    final Level level = this.buildingCommands.findLevel(levelName, command.getDouble("index", Double.NaN));
    if (level == null) {
      notes.add("Ignored move_to_level: there is no level \"" + levelName + "\"");
      return;
    }
    final List<Selectable> moved = new ArrayList<Selectable>();
    for (Selectable item : targets) {
      if (item instanceof HomePieceOfFurniture && ((HomePieceOfFurniture)item).getLevel() != level
          || item instanceof Wall && ((Wall)item).getLevel() != level
          || item instanceof Room && ((Room)item).getLevel() != level) {
        moved.add(item);
      }
    }
    if (moved.isEmpty()) {
      return;
    }
    // Walls joined to the moved walls are captured too, as the walls left behind are detached
    final List<Selectable> changed = new ArrayList<Selectable>(moved);
    for (Selectable item : moved) {
      if (item instanceof Wall) {
        for (Wall joined : new Wall [] {((Wall)item).getWallAtStart(), ((Wall)item).getWallAtEnd()}) {
          if (joined != null && !changed.contains(joined)) {
            changed.add(joined);
          }
        }
      }
    }
    this.executor.postModification(changed, new Runnable() {
        public void run() {
          for (Selectable item : moved) {
            if (item instanceof HomePieceOfFurniture) {
              ((HomePieceOfFurniture)item).setLevel(level);
            } else if (item instanceof Room) {
              ((Room)item).setLevel(level);
            } else {
              Wall wall = (Wall)item;
              Wall wallAtStart = wall.getWallAtStart();
              if (wallAtStart != null && !moved.contains(wallAtStart)) {
                detach(wallAtStart, wall);
                wall.setWallAtStart(null);
              }
              Wall wallAtEnd = wall.getWallAtEnd();
              if (wallAtEnd != null && !moved.contains(wallAtEnd)) {
                detach(wallAtEnd, wall);
                wall.setWallAtEnd(null);
              }
              wall.setLevel(level);
            }
          }
        }
      });
    tally [4] += moved.size();
    notes.add("Moved " + moved.size() + (moved.size() == 1 ? " item" : " items")
        + " to the level \"" + level.getName() + "\"");
  }

  private static void detach(Wall wall, Wall other) {
    if (wall.getWallAtStart() == other) {
      wall.setWallAtStart(null);
    }
    if (wall.getWallAtEnd() == other) {
      wall.setWallAtEnd(null);
    }
  }

  /**
   * Displays the image of a floor plan behind the plan of the selected level,
   * at the scale given by the width it shows.
   */
  private void setBackgroundImage(AssistantCommand command, List<String> notes) throws IOException {
    File file = this.objectCommands.getFile(command, "image");
    byte [] data = AssistantGeneratedModel.readFile(file, MAX_IMAGE_LENGTH);
    String extension = AssistantGeneratedModel.getImageExtension(data);
    int [] size = AssistantGeneratedModel.getImageSize(data);
    if (extension == null || size == null || size [0] <= 0 || size [1] <= 0) {
      throw new IOException(file.getName() + " isn't a PNG, JPEG or GIF image");
    }
    double width = command.getDouble("width", Double.NaN);
    double height = command.getDouble("height", Double.NaN);
    if (!(width > 0) && height > 0) {
      width = height * size [0] / size [1];
    }
    boolean guessedWidth = !(width > 0);
    if (guessedWidth) {
      width = 1000;
    }
    if (width < 10 || width > 100000) {
      notes.add("Ignored set_background_image: width must be between 10 and 100000 cm");
      return;
    }
    double x = command.getDouble("x", 0);
    double y = command.getDouble("y", 0);
    if (!AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)) {
      x = 0;
      y = 0;
    }
    // The image is copied to a file saved with the home
    Content image = AssistantGeneratedModel.storeFile("plan", "." + extension, data);
    // The scale is given by the width of the image, and its origin is the point
    // of the scaled image where the origin of the plan is
    BackgroundImage backgroundImage = new BackgroundImage(image, (float)width, 0, 0, size [0], 0,
        (float)-x, (float)-y, true);
    changeBackgroundImage(backgroundImage, notes, "Displayed " + file.getName() + " behind the plan"
        + (this.home.getSelectedLevel() != null ? " of the level \"" + this.home.getSelectedLevel().getName() + "\"" : "")
        + ": its top left corner is at " + Math.round(x) + "," + Math.round(y) + " and it covers "
        + Math.round(width) + " x " + Math.round(width * size [1] / size [0]) + " cm"
        + (guessedWidth ? " (no width was given: call it again with the real width in cm of what the image shows)" : "")
        + ". Trace it with add_rectangular_room and add_wall, and check the result with a picture of the plan");
  }

  private void changeBackgroundImage(final BackgroundImage backgroundImage, List<String> notes, String note) {
    final Level level = this.home.getSelectedLevel();
    final BackgroundImage oldImage = level != null ? level.getBackgroundImage() : this.home.getBackgroundImage();
    if (oldImage == null && backgroundImage == null) {
      return;
    }
    final Home home = this.home;
    apply(home, level, backgroundImage);
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() throws CannotUndoException {
          super.undo();
          apply(home, level, oldImage);
        }

        @Override
        public void redo() throws CannotRedoException {
          super.redo();
          apply(home, level, backgroundImage);
        }
      });
    notes.add(note);
  }

  private static void apply(Home home, Level level, BackgroundImage backgroundImage) {
    if (level != null) {
      level.setBackgroundImage(backgroundImage);
    } else {
      home.setBackgroundImage(backgroundImage);
    }
  }
}
