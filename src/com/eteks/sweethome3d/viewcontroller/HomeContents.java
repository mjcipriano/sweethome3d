/*
 * HomeContents.java 16 Nov 2026
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eteks.sweethome3d.model.BackgroundImage;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * What a home holds at a given time: its levels, its items with the level of each one,
 * its properties and the image behind its plan. Used to replace the contents of a home
 * by the ones of one of its versions, and to undo it.
 * @author Sweet Home 3D
 */
final class HomeContents {
  /** The property that counts the ids given to items, which never goes back. */
  private static final String LAST_ID_PROPERTY = "assistantLastId";

  private final List<Level>                levels = new ArrayList<Level>();
  private final List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
  private final List<Wall>                 walls = new ArrayList<Wall>();
  private final List<Room>                 rooms = new ArrayList<Room>();
  private final List<DimensionLine>        dimensionLines = new ArrayList<DimensionLine>();
  private final List<Polyline>             polylines = new ArrayList<Polyline>();
  private final List<Label>                labels = new ArrayList<Label>();
  private final Map<Object, Level>         itemLevels = new IdentityHashMap<Object, Level>();
  private final Map<String, String>        properties = new LinkedHashMap<String, String>();
  private final BackgroundImage            backgroundImage;
  private final Level                      selectedLevel;

  private HomeContents(Home home) {
    this.levels.addAll(home.getLevels());
    this.furniture.addAll(home.getFurniture());
    this.walls.addAll(home.getWalls());
    this.rooms.addAll(home.getRooms());
    this.dimensionLines.addAll(home.getDimensionLines());
    this.polylines.addAll(home.getPolylines());
    this.labels.addAll(home.getLabels());
    for (HomePieceOfFurniture piece : this.furniture) {
      this.itemLevels.put(piece, piece.getLevel());
    }
    for (Wall wall : this.walls) {
      this.itemLevels.put(wall, wall.getLevel());
    }
    for (Room room : this.rooms) {
      this.itemLevels.put(room, room.getLevel());
    }
    for (DimensionLine dimensionLine : this.dimensionLines) {
      this.itemLevels.put(dimensionLine, dimensionLine.getLevel());
    }
    for (Polyline polyline : this.polylines) {
      this.itemLevels.put(polyline, polyline.getLevel());
    }
    for (Label label : this.labels) {
      this.itemLevels.put(label, label.getLevel());
    }
    for (String name : home.getPropertyNames()) {
      if (!LAST_ID_PROPERTY.equals(name)) {
        this.properties.put(name, home.getProperty(name));
      }
    }
    this.backgroundImage = home.getBackgroundImage();
    this.selectedLevel = home.getSelectedLevel();
  }

  /**
   * Returns the contents of <code>home</code>.
   */
  static HomeContents of(Home home) {
    return new HomeContents(home);
  }

  /**
   * Returns the count of items of these contents.
   */
  int getItemCount() {
    return this.furniture.size() + this.walls.size() + this.rooms.size() + this.dimensionLines.size()
        + this.polylines.size() + this.labels.size();
  }

  /**
   * Removes these contents from <code>home</code>.
   */
  void removeFrom(Home home) {
    List<Selectable> emptySelection = new ArrayList<Selectable>();
    home.setSelectedItems(emptySelection);
    for (HomePieceOfFurniture piece : this.furniture) {
      home.deletePieceOfFurniture(piece);
    }
    // Walls keep the walls they are joined to, which a deleted wall is detached from: they come back together
    Map<Wall, Wall []> joinedWalls = new IdentityHashMap<Wall, Wall []>();
    for (Wall wall : this.walls) {
      joinedWalls.put(wall, new Wall [] {wall.getWallAtStart(), wall.getWallAtEnd()});
    }
    for (Wall wall : this.walls) {
      home.deleteWall(wall);
    }
    for (Wall wall : this.walls) {
      wall.setWallAtStart(joinedWalls.get(wall) [0]);
      wall.setWallAtEnd(joinedWalls.get(wall) [1]);
    }
    for (Room room : this.rooms) {
      home.deleteRoom(room);
    }
    for (DimensionLine dimensionLine : this.dimensionLines) {
      home.deleteDimensionLine(dimensionLine);
    }
    for (Polyline polyline : this.polylines) {
      home.deletePolyline(polyline);
    }
    for (Label label : this.labels) {
      home.deleteLabel(label);
    }
    for (Level level : this.levels) {
      home.deleteLevel(level);
    }
    for (String name : new ArrayList<String>(home.getPropertyNames())) {
      if (!LAST_ID_PROPERTY.equals(name)) {
        home.setProperty(name, null);
      }
    }
  }

  /**
   * Adds these contents to <code>home</code>, which should hold no item.
   */
  void addTo(Home home) {
    for (Level level : this.levels) {
      home.addLevel(level);
    }
    for (HomePieceOfFurniture piece : this.furniture) {
      home.addPieceOfFurniture(piece);
      piece.setLevel(this.itemLevels.get(piece));
    }
    // Joined walls are detached by the walls deleted before them
    Map<Wall, Wall []> joinedWalls = new IdentityHashMap<Wall, Wall []>();
    for (Wall wall : this.walls) {
      joinedWalls.put(wall, new Wall [] {wall.getWallAtStart(), wall.getWallAtEnd()});
    }
    for (Wall wall : this.walls) {
      home.addWall(wall);
      wall.setLevel(this.itemLevels.get(wall));
    }
    for (Wall wall : this.walls) {
      Wall [] joined = joinedWalls.get(wall);
      if (wall.getWallAtStart() != joined [0] && (joined [0] == null || this.walls.contains(joined [0]))) {
        wall.setWallAtStart(joined [0]);
      }
      if (wall.getWallAtEnd() != joined [1] && (joined [1] == null || this.walls.contains(joined [1]))) {
        wall.setWallAtEnd(joined [1]);
      }
    }
    for (Room room : this.rooms) {
      home.addRoom(room);
      room.setLevel(this.itemLevels.get(room));
    }
    for (DimensionLine dimensionLine : this.dimensionLines) {
      home.addDimensionLine(dimensionLine);
      dimensionLine.setLevel(this.itemLevels.get(dimensionLine));
    }
    for (Polyline polyline : this.polylines) {
      home.addPolyline(polyline);
      polyline.setLevel(this.itemLevels.get(polyline));
    }
    for (Label label : this.labels) {
      home.addLabel(label);
      label.setLevel(this.itemLevels.get(label));
    }
    for (Map.Entry<String, String> property : this.properties.entrySet()) {
      home.setProperty(property.getKey(), property.getValue());
    }
    home.setBackgroundImage(this.backgroundImage);
    if (this.selectedLevel != null && this.levels.contains(this.selectedLevel)) {
      home.setSelectedLevel(this.selectedLevel);
    } else if (!this.levels.isEmpty()) {
      home.setSelectedLevel(this.levels.get(this.levels.size() - 1));
    }
  }
}
