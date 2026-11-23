/*
 * AssistantShapeCommands.java
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

import java.awt.geom.Area;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.TextureImage;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands of the design assistant that build parts of a house as
 * generated models: slabs (<code>add_slab</code>), the ground
 * (<code>set_terrain</code>), roofs over any polygon (<code>add_roof</code>)
 * with their dormers (<code>add_dormer</code>) and the walls under them
 * (<code>fit_walls_to_roof</code>), and the change of the parts of a piece
 * built by <code>create_furniture</code> (<code>modify_furniture_parts</code>).
 * @author Sweet Home 3D
 */
class AssistantShapeCommands {
  /** Property of a roof storing the parameters it was built from, in JSON. */
  static final String ROOF_SPEC_PROPERTY = "assistantRoofSpec";
  /** Property of the window of a dormer storing the id of its roof. */
  static final String ROOF_WINDOW_PROPERTY = "assistantRoofWindow";
  /** Property of a room whose ceiling follows the roof: <code>follow_roof</code>, or <code>follow_roof:240</code> with a flat part. */
  static final String ROOM_CEILING_PROPERTY = "assistantRoomCeiling";
  /** Property of the piece that is the ceiling of a room: the id of the room. */
  static final String ROOM_CEILING_PIECE_PROPERTY = "assistantRoomCeilingOf";
  /** Property of the ground storing its points, in JSON. */
  /** The ceiling that follows a roof is this far under it, in cm. */
  private static final float CEILING_GAP = 1;
  static final String TERRAIN_PROPERTY = "assistantTerrain";
  static final String TERRAIN_PARAMETERS_PROPERTY = "assistantTerrainParameters";
  static final String TERRAIN_FOOTPRINT_PROPERTY = "assistantTerrainFootprint";
  /** Property of a slab storing the id of the room it's the floor of. */
  static final String ROOM_SLAB_PROPERTY = "assistantRoomSlab";
  /** Property of a piece made of parts storing them, in JSON. */
  static final String PARTS_PROPERTY = "assistantParts";
  /** Property of a piece made of parts storing the bounds of its parts. */
  static final String PARTS_BOUNDS_PROPERTY = "assistantPartsBounds";

  private static final int MAX_TERRAIN_POINTS = 3000;
  private static final int MAX_SLAB_POINTS = 200;

  static int getMaxTerrainPoints() {
    return MAX_TERRAIN_POINTS;
  }

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;
  // The triangles of the last ground whose height was asked
  /** The ground is left open under a floor it comes closer than this under, in cm. */
  private static final float             FOOTPRINT_MARGIN = 30;

  private String                         terrainSpec;
  private List<float [][]>               terrainTriangles;

  AssistantShapeCommands(Home home, UserPreferences preferences, HomeController homeController,
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
    try {
      if ("add_slab".equals(action)) {
        addSlab(command, tally, notes);
      } else if ("set_terrain".equals(action) || "add_terrain".equals(action)) {
        setTerrain(command, tally, notes);
      } else if ("add_roof".equals(action) && (command.has("points") || command.has("edges"))) {
        addRoof(command, tally, notes);
      } else if ("add_dormer".equals(action)) {
        addDormer(command, tally, notes);
      } else if ("remove_dormer".equals(action) || "delete_dormer".equals(action)) {
        modifyDormer(command, true, tally, notes);
      } else if ("modify_dormer".equals(action)) {
        modifyDormer(command, false, tally, notes);
      } else if ("modify_roof".equals(action)) {
        modifyRoof(command, tally, notes);
      } else if ("headroom".equals(action) || "room_headroom".equals(action)) {
        reportHeadroom(command, notes);
      } else if ("fit_walls_to_roof".equals(action)) {
        fitWallsToRoof(command, tally, notes);
      } else if ("modify_furniture_parts".equals(action) || "modify_parts".equals(action)) {
        modifyFurnitureParts(command, tally, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    }
    return true;
  }

  // ---- Common ----

  /**
   * Returns an object that finds the textures of the catalog.
   */
  AssistantFurnitureBuilder.TextureFinder getTextureFinder() {
    return new AssistantFurnitureBuilder.TextureFinder() {
        public TextureImage findTexture(String name, String category, String catalogId) {
          return executor.getBuildingCommands().findTexture(name, category, catalogId, "texture",
              new ArrayList<String>(), null);
        }
      };
  }

  /**
   * Returns the level named by the parameter <code>level</code> of a command, or the selected one.
   */
  Level getLevel(AssistantCommand command, String action, List<String> notes) throws IOException {
    if (command.getString("level") != null) {
      Level level = this.executor.getStructureCommands().getLevel(command, action, notes, "level");
      if (level == null) {
        throw new IOException("see above");
      }
      return level;
    }
    return this.home.getSelectedLevel();
  }

  /**
   * Returns a piece showing <code>model</code> at the place where it was
   * built, the heights of the model being measured from the floor of <code>level</code>.
   */
  HomePieceOfFurniture createPiece(String name, AssistantGeneratedModel model, Level level,
                                   boolean movable, String structure) {
    float [] bounds = model.getBounds();
    CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(name, model.getIcon(), model.getModel(),
        model.getWidth(), model.getDepth(), model.getHeight(), bounds [1], movable, null, null,
        null, 0, null, null, (float)Math.PI / 8, -(float)Math.PI / 16, 1, true);
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    piece.setX((bounds [0] + bounds [3]) / 2);
    piece.setY((bounds [2] + bounds [5]) / 2);
    piece.setElevation(bounds [1]);
    piece.setLevel(level);
    piece.setPlanIcon(model.getPlanIcon());
    piece.setProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY, structure);
    return piece;
  }

  void add(HomePieceOfFurniture piece) {
    this.executor.beginTurnUpdate();
    // Furniture is added to the selected level
    Level selectedLevel = this.home.getSelectedLevel();
    if (piece.getLevel() != null && piece.getLevel() != selectedLevel) {
      this.home.setSelectedLevel(piece.getLevel());
    }
    this.homeController.getFurnitureController().addFurniture(Collections.singletonList(piece));
    if (this.home.getSelectedLevel() != selectedLevel && selectedLevel != null) {
      this.home.setSelectedLevel(selectedLevel);
    }
  }

  /**
   * Replaces the model, the size and the place of a piece, in an undoable way.
   */
  void replaceModel(final HomePieceOfFurniture piece, AssistantGeneratedModel model,
                            float x, float y, float elevation, Map<String, String> properties) {
    final Object [] before = getModelState(piece, properties.keySet());
    piece.setModel(model.getModel());
    piece.setIcon(model.getIcon());
    piece.setPlanIcon(model.getPlanIcon());
    piece.setWidth(model.getWidth());
    piece.setDepth(model.getDepth());
    piece.setHeight(model.getHeight());
    piece.setX(x);
    piece.setY(y);
    piece.setElevation(elevation);
    for (Map.Entry<String, String> property : properties.entrySet()) {
      piece.setProperty(property.getKey(), property.getValue());
    }
    final Object [] after = getModelState(piece, properties.keySet());
    this.executor.beginTurnUpdate();
    this.homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() {
          super.undo();
          setModelState(piece, before);
        }

        @Override
        public void redo() {
          super.redo();
          setModelState(piece, after);
        }

        @Override
        public String getPresentationName() {
          return "AI assistant edit";
        }
      });
  }

  private static Object [] getModelState(HomePieceOfFurniture piece, java.util.Collection<String> propertyNames) {
    Map<String, String> properties = new LinkedHashMap<String, String>();
    for (String name : propertyNames) {
      properties.put(name, piece.getProperty(name));
    }
    return new Object [] {piece.getModel(), piece.getIcon(), piece.getPlanIcon(), piece.getWidth(), piece.getDepth(),
                          piece.getHeight(), piece.getX(), piece.getY(), piece.getElevation(), properties};
  }

  @SuppressWarnings("unchecked")
  private static void setModelState(HomePieceOfFurniture piece, Object [] state) {
    piece.setModel((Content)state [0]);
    piece.setIcon((Content)state [1]);
    piece.setPlanIcon((Content)state [2]);
    piece.setWidth((Float)state [3]);
    piece.setDepth((Float)state [4]);
    piece.setHeight((Float)state [5]);
    piece.setX((Float)state [6]);
    piece.setY((Float)state [7]);
    piece.setElevation((Float)state [8]);
    for (Map.Entry<String, String> property : ((Map<String, String>)state [9]).entrySet()) {
      piece.setProperty(property.getKey(), property.getValue());
    }
  }

  /**
   * Returns the color or the texture a command gives with <code>color</code>, <code>texture</code>...
   */
  private AssistantMeshBuilder.Material getMaterial(AssistantMeshBuilder mesh, Map<?, ?> parameters,
                                                    int defaultColor, List<String> notes) {
    Integer color = parameters.get("color") != null
        ? AssistantCommandExecutor.parseColor(parameters.get("color").toString()) : null;
    if (parameters.get("texture") != null || parameters.get("catalog_id") != null) {
      String [] ambiguity = new String [1];
      CatalogTexture texture = this.executor.getBuildingCommands().findTexture(
          parameters.get("texture") != null ? parameters.get("texture").toString() : null,
          parameters.get("category") != null ? parameters.get("category").toString()
              : parameters.get("texture_category") != null ? parameters.get("texture_category").toString() : null,
          parameters.get("catalog_id") != null ? parameters.get("catalog_id").toString() : null,
          "texture", notes, ambiguity);
      if (texture != null) {
        if (ambiguity [0] != null) {
          notes.add(ambiguity [0]);
        }
        return mesh.getMaterial(texture, AssistantFurnitureBuilder.getNumber(parameters, "texture_scale", 1),
            (float)Math.toRadians(AssistantFurnitureBuilder.getNumber(parameters, "texture_angle", 0)), 0, 0,
            color != null ? color : defaultColor);
      }
    }
    return mesh.getMaterial(color != null ? color : defaultColor);
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  // ---- Slabs ----

  private void addSlab(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<float []> points = command.getPoints("points");
    if (points.size() < 3 || points.size() > MAX_SLAB_POINTS) {
      throw new IOException("\"points\" must list the 3 corners at least of the slab, like [[0,0],[400,0],[400,300],[0,300]]");
    }
    for (float [] point : points) {
      if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
        throw new IOException("points out of range");
      }
    }
    Level level = getLevel(command, "add_slab", notes);
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : "Slab";
    HomePieceOfFurniture slab = createSlab(points.toArray(new float [points.size()][]), command.getParameters(),
        name, level, notes);
    add(slab);
    tally [0]++;
    notes.add("Added the slab " + this.executor.getId(slab) + " \"" + name + "\": "
        + format(Math.abs(AssistantRoofGeometry.getSignedArea(AssistantRoofGeometry.toDoubles(
            points.toArray(new float [points.size()][]))) / 10000))
        + " m2, from " + format(slab.getElevation()) + " to " + format(slab.getElevation() + slab.getHeight())
        + " cm above the floor of its level" + (level != null ? " \"" + level.getName() + "\"" : "")
        + ((level == null || level.getElevation() >= 0)
              && (level != null ? level.getElevation() : 0) + slab.getElevation() + slab.getHeight() <= 0.5f
              && getTerrainHeight(slab.getX(), slab.getY()) == null
            ? ". Its top isn't above the height 0: the flat ground of the 3D view hides it, unless set_terrain replaces the ground there"
            : ""));
  }

  /**
   * Returns a slab over the given points, with the parameters <code>top</code>,
   * <code>thickness</code>, <code>top_at</code>, <code>color</code>, <code>texture</code>
   * and <code>side_color</code>.
   */
  private HomePieceOfFurniture createSlab(float [][] points, Map<String, Object> parameters, String name,
                                          Level level, List<String> notes) throws IOException {
    float top = AssistantFurnitureBuilder.getNumber(parameters, "top", 0);
    float thickness = AssistantFurnitureBuilder.getNumber(parameters, "thickness", 10);
    if (!AssistantCommandExecutor.sizeSane(top) || !(thickness >= 0.1f) || thickness > 5000) {
      throw new IOException("\"top\" and \"thickness\" are out of range");
    }
    float [] plane = {top, 0, 0};
    List<float []> topPoints = AssistantFurnitureBuilder.getPoints(parameters, "top_at", 3);
    if (parameters.get("top_at") != null) {
      // The plane through three points
      float [] tilted = topPoints.size() == 3 ? getPlane(topPoints.get(0), topPoints.get(1), topPoints.get(2)) : null;
      if (tilted == null) {
        throw new IOException("\"top_at\" must give three points [x,y,z] that aren't aligned, z being the height of the top there");
      }
      plane = tilted;
    }
    float lowest = Float.MAX_VALUE;
    for (float [] point : points) {
      float height = plane [0] + plane [1] * point [0] + plane [2] * point [1];
      if (!AssistantCommandExecutor.sizeSane(height)) {
        throw new IOException("the slope given by \"top_at\" is too steep");
      }
      lowest = Math.min(lowest, height);
    }
    Area area = AssistantMeshBuilder.getArea(points);
    if (area.isEmpty()) {
      throw new IOException("the points enclose no area");
    }
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material topMaterial = getMaterial(mesh, parameters, 0xBEBCB6, notes);
    Integer sideColor = parameters.get("side_color") != null
        ? AssistantCommandExecutor.parseColor(parameters.get("side_color").toString()) : null;
    AssistantMeshBuilder.Material sideMaterial = sideColor != null ? mesh.getMaterial(sideColor)
        : topMaterial.isTextured() ? mesh.getMaterial(topMaterial.getColor()) : topMaterial;
    mesh.addPrism(area, lowest - thickness, plane, topMaterial, sideMaterial);
    HomePieceOfFurniture slab = createPiece(name, mesh.store("slab"), level, true, "slab");
    return slab;
  }

  /**
   * Returns the coefficients {c, cx, cy} of the plane z = c + cx * x + cy * y
   * through three points, or <code>null</code> if it's vertical.
   */
  static float [] getPlane(float [] point1, float [] point2, float [] point3) {
    double ux = point2 [0] - point1 [0];
    double uy = point2 [1] - point1 [1];
    double uz = point2 [2] - point1 [2];
    double vx = point3 [0] - point1 [0];
    double vy = point3 [1] - point1 [1];
    double vz = point3 [2] - point1 [2];
    double determinant = ux * vy - uy * vx;
    if (Math.abs(determinant) < 1E-3) {
      return null;
    }
    double cx = (uz * vy - uy * vz) / determinant;
    double cy = (ux * vz - uz * vx) / determinant;
    return new float [] {(float)(point1 [2] - cx * point1 [0] - cy * point1 [1]), (float)cx, (float)cy};
  }

  /**
   * Gives a raised or a sunken floor to a room: a slab of its shape with the
   * color or the texture of its floor, in place of its floor at the height of the level.
   * @return a description of the slab
   */
  String setRoomFloorOffset(Room room, double offset, double thickness) throws IOException {
    if (!AssistantCommandExecutor.sizeSane(offset) || !(thickness >= 0.1) || thickness > 5000) {
      throw new IOException("\"floor_offset\" and \"slab_thickness\" are out of range");
    }
    // The slab that was the floor of this room is replaced
    List<Selectable> oldSlabs = new ArrayList<Selectable>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (room.getId().equals(piece.getProperty(ROOM_SLAB_PROPERTY))) {
        oldSlabs.add(piece);
      }
    }
    this.executor.beginTurnUpdate();
    if (!oldSlabs.isEmpty()) {
      this.homeController.getPlanController().deleteItems(oldSlabs);
    }
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    HomeTexture texture = room.getFloorTexture();
    int color = room.getFloorColor() != null ? room.getFloorColor() : 0xC8C4BC;
    AssistantMeshBuilder.Material topMaterial = texture != null
        ? mesh.getMaterial(texture, texture.getScale(), texture.getAngle(), texture.getXOffset(), texture.getYOffset(), color)
        : mesh.getMaterial(color);
    mesh.addPrism(AssistantMeshBuilder.getArea(room.getPoints()), (float)(offset - thickness),
        new float [] {(float)offset, 0, 0}, topMaterial, mesh.getMaterial(topMaterial.getColor()));
    HomePieceOfFurniture slab = createPiece((room.getName() != null ? room.getName() : "Room") + " floor",
        mesh.store("slab"), room.getLevel(), false, "slab");
    slab.setProperty(ROOM_SLAB_PROPERTY, room.getId());
    add(slab);
    return "the slab " + this.executor.getId(slab) + " \"" + slab.getName() + "\" is its floor, from "
        + format(offset - thickness) + " to " + format(offset) + " cm";
  }

  // ---- Ground ----

  private void setTerrain(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Map<String, Object> parameters = command.getParameters();
    List<float []> points = getTerrainPoints(parameters);
    if (points.size() < 3) {
      throw new IOException("\"points\" must give 3 points [x,y,z] at least, z being the height of the ground there");
    } else if (points.size() > MAX_TERRAIN_POINTS) {
      throw new IOException("the ground has " + MAX_TERRAIN_POINTS + " points at most");
    }
    for (float [] point : points) {
      if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])
          || !AssistantCommandExecutor.sizeSane(point [2])) {
        throw new IOException("points out of range");
      }
    }
    List<float [][]> triangles = triangulate(points);
    if (triangles.isEmpty()) {
      throw new IOException("the points are aligned: they give no ground");
    }
    float lowest = Float.MAX_VALUE;
    float highest = -Float.MAX_VALUE;
    for (float [] point : points) {
      lowest = Math.min(lowest, point [2]);
      highest = Math.max(highest, point [2]);
    }
    float skirt = Math.max(1, Math.min(2000, AssistantFurnitureBuilder.getNumber(parameters, "thickness", 30)));
    // The 3D view draws a flat ground at the height 0, except over what stands on a level under it:
    // the ground goes to such a level, lower than its lowest point
    Level level = null;
    boolean levelAdded = false;
    if (command.getString("level") != null) {
      level = getLevel(command, "set_terrain", notes);
    } else {
      for (Level homeLevel : this.home.getLevels()) {
        if (homeLevel.getElevation() < 0 && homeLevel.getElevation() <= lowest - skirt
            && (level == null || homeLevel.getElevation() > level.getElevation())) {
          level = homeLevel;
        }
      }
      if (level == null) {
        Level selectedLevel = this.home.getSelectedLevel();
        float elevation = (float)Math.floor(Math.min(-50, lowest - skirt) / 10) * 10;
        float nextElevation = 0;
        for (Level homeLevel : this.home.getLevels()) {
          nextElevation = Math.min(nextElevation, homeLevel.getElevation());
        }
        level = this.executor.getBuildingCommands().createLevel("Terrain", elevation,
            Math.max(10, nextElevation - elevation - 1), 1);
        levelAdded = true;
        if (selectedLevel == null) {
          // A home without level got two of them: the one of its items is the one to work at
          for (Level homeLevel : this.home.getLevels()) {
            if (homeLevel != level) {
              selectedLevel = homeLevel;
              break;
            }
          }
        }
        if (selectedLevel != null) {
          this.home.setSelectedLevel(selectedLevel);
        }
      }
    }
    float levelElevation = level != null ? level.getElevation() : 0;

    int [] counts = new int [2];
    AssistantGeneratedModel terrainModel = buildTerrainModel(parameters, triangles, level, lowest, skirt, notes, counts);
    int openRooms = counts [0];
    int areaCount = counts [1];
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : "Terrain";
    HomePieceOfFurniture terrain = createPiece(name, terrainModel, level, false, "terrain");
    StringBuilder spec = new StringBuilder("[");
    for (float [] point : points) {
      spec.append(spec.length() > 1 ? "," : "").append('[').append(format(point [0])).append(',')
          .append(format(point [1])).append(',').append(format(point [2])).append(']');
    }
    terrain.setProperty(TERRAIN_PROPERTY, spec.append(']').toString());
    // What is needed to build the ground again when the rooms it is open under change
    terrain.setProperty(TERRAIN_PARAMETERS_PROPERTY, toJson(parameters));
    terrain.setProperty(TERRAIN_FOOTPRINT_PROPERTY, getFootprintSignature(level));
    terrain.setProperty(HomeItemTags.TAGS_PROPERTY, "terrain");
    // One ground at a time, unless told otherwise
    this.executor.beginTurnUpdate();
    int replaced = 0;
    if (command.getBoolean("replace", true)) {
      List<Selectable> oldTerrains = new ArrayList<Selectable>();
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (piece.getProperty(TERRAIN_PROPERTY) != null) {
          oldTerrains.add(piece);
        }
      }
      if (!oldTerrains.isEmpty()) {
        // A deleted piece has no level any more
        List<Level> oldLevels = new ArrayList<Level>();
        for (Selectable oldTerrain : oldTerrains) {
          oldLevels.add(((HomePieceOfFurniture)oldTerrain).getLevel());
        }
        this.homeController.getPlanController().deleteItems(oldTerrains);
        replaced = oldTerrains.size();
        // A level that was added for the ground replaced and holds nothing else isn't kept
        for (Level oldLevel : oldLevels) {
          if (oldLevel != null && oldLevel != level && oldLevel.getElevation() < 0
              && "Terrain".equals(oldLevel.getName())
              && this.home.getLevels().contains(oldLevel)
              && this.home.getLevels().size() > 2
              && this.executor.getStructureCommands().getContents(oldLevel).isEmpty()) {
            this.executor.getStructureCommands().delete(oldLevel);
          }
        }
      }
    }
    add(terrain);
    tally [0]++;
    notes.add((replaced > 0 ? "Replaced the ground by " : "Added the ground ") + this.executor.getId(terrain) + " \"" + name
        + "\": " + points.size() + " points, " + triangles.size() + " triangles, heights from " + format(lowest) + " to "
        + format(highest) + " cm (0 = the floor of the level at the elevation 0)"
        + (areaCount == 0 ? "" : ", " + areaCount + (areaCount == 1 ? " area" : " areas") + " with a finish of their own")
        + (level != null ? ", on the level " + this.executor.getId(level) + " \"" + level.getName() + "\" at "
            + format(level.getElevation()) + " cm" + (levelAdded ? ", added for it under the others: the 3D view draws no flat "
              + "ground over what stands on a level under the height 0, so the slopes show; this level is the place of the ground only" : "") : "")
        + (level != null && (level.getElevation() >= 0 || level.getElevation() > lowest - skirt)
            ? ". This level isn't under the lowest point of the ground: the flat ground of the 3D view may hide a part of it" : "")
        + (openRooms > 0 ? ". It is left open under " + openRooms + (openRooms == 1 ? " room" : " rooms")
            + " it reaches and their walls, so their floor shows; it follows the rooms when they are added, moved or deleted" : "")
        + ". {\"action\":\"set_elevation\",\"id\":\"F#\",\"on_terrain\":true} puts a piece on it");
  }

  /**
   * Returns the points (x, y, z) of the ground described by the parameters of <code>set_terrain</code>.
   */
  private static List<float []> getTerrainPoints(Map<String, Object> parameters) throws IOException {
    List<float []> points = AssistantFurnitureBuilder.getPoints(parameters, "points", 3);
    if (parameters.get("grid") instanceof Map) {
      // Heights at the crossings of a grid
      Map<?, ?> grid = (Map<?, ?>)parameters.get("grid");
      float x = AssistantFurnitureBuilder.getNumber(grid, "x", 0);
      float y = AssistantFurnitureBuilder.getNumber(grid, "y", 0);
      float width = AssistantFurnitureBuilder.getNumber(grid, "width", Float.NaN);
      float depth = AssistantFurnitureBuilder.getNumber(grid, "depth", Float.NaN);
      List<?> rows = grid.get("heights") instanceof List ? (List<?>)grid.get("heights") : null;
      if (rows == null || rows.size() < 2 || !(width > 0) || !(depth > 0)) {
        throw new IOException("\"grid\" needs x, y, width, depth and \"heights\": the rows of heights from the north "
            + "to the south, each one from the west to the east, like [[0,0,0],[20,30,20]]");
      }
      for (int row = 0; row < rows.size(); row++) {
        List<?> heights = rows.get(row) instanceof List ? (List<?>)rows.get(row) : null;
        if (heights == null || heights.size() < 2) {
          throw new IOException("each row of \"heights\" must give two heights at least");
        }
        for (int column = 0; column < heights.size(); column++) {
          if (heights.get(column) instanceof Number) {
            points.add(new float [] {x + width * column / (heights.size() - 1), y + depth * row / (rows.size() - 1),
                ((Number)heights.get(column)).floatValue()});
          }
        }
      }
    }
    return points;
  }

  /**
   * Returns the model of the ground through the given triangles, open under the rooms and the walls
   * it reaches. <code>counts</code> receives the count of these rooms and the count of areas
   * with a finish of their own.
   */
  private AssistantGeneratedModel buildTerrainModel(Map<String, Object> parameters, List<float [][]> triangles,
                                                    Level level, float lowest, float skirt,
                                                    List<String> notes, int [] counts) throws IOException {
    float levelElevation = level != null ? level.getElevation() : 0;
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material material = getMaterial(mesh, parameters, 0x6E9A4C, notes);
    // The areas with a color or a texture of their own, the last one above the others
    List<Area> areas = new ArrayList<Area>();
    List<AssistantMeshBuilder.Material> areaMaterials = new ArrayList<AssistantMeshBuilder.Material>();
    if (parameters.get("areas") instanceof List) {
      for (Object item : (List<?>)parameters.get("areas")) {
        if (item instanceof Map) {
          List<float []> areaPoints = AssistantFurnitureBuilder.getPoints((Map<?, ?>)item, "points", 2);
          if (areaPoints.size() >= 3) {
            areas.add(AssistantMeshBuilder.getArea(areaPoints.toArray(new float [areaPoints.size()][])));
            areaMaterials.add(getMaterial(mesh, (Map<?, ?>)item, 0xA8A49C, notes));
          } else {
            notes.add("An area of the ground without 3 points [x,y] at least was ignored");
          }
        }
      }
    }
    // The ground is left open under the rooms and the walls it reaches: at the same height
    // as a floor, it would show through it, and higher it would bury the room
    List<Area> footprints = new ArrayList<Area>();
    List<Float> footprintElevations = new ArrayList<Float>();
    int openRooms = 0;
    for (Room room : this.home.getRooms()) {
      if (room.isFloorVisible() && room.getLevel() != level && room.getPointCount() >= 3) {
        footprints.add(AssistantMeshBuilder.getArea(room.getPoints()));
        footprintElevations.add(room.getLevel() != null ? room.getLevel().getElevation() : 0f);
      }
    }
    int roomFootprints = footprints.size();
    boolean [] roomOpen = new boolean [roomFootprints];
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() != level) {
        footprints.add(AssistantMeshBuilder.getArea(wall.getPoints()));
        footprintElevations.add(wall.getLevel() != null ? wall.getLevel().getElevation() : 0f);
      }
    }
    Map<String, int []> sides = new HashMap<String, int []>();
    List<float [][]> sidePoints = new ArrayList<float [][]>();
    for (float [][] triangle : triangles) {
      float [] plane = getPlane(triangle [0], triangle [1], triangle [2]);
      if (plane == null) {
        continue;
      }
      float triangleTop = Math.max(triangle [0][2], Math.max(triangle [1][2], triangle [2][2]));
      // Heights are measured from the floor of the level of the ground
      plane [0] -= levelElevation;
      float [][] corners = {{triangle [0][0], triangle [0][1]}, {triangle [1][0], triangle [1][1]},
                            {triangle [2][0], triangle [2][1]}};
      Area remaining = null;
      java.awt.geom.Rectangle2D triangleBounds = AssistantMeshBuilder.getArea(corners).getBounds2D();
      for (int i = 0; i < footprints.size(); i++) {
        if (footprintElevations.get(i) <= triangleTop + FOOTPRINT_MARGIN
            && footprints.get(i).intersects(triangleBounds)) {
          if (remaining == null) {
            remaining = AssistantMeshBuilder.getArea(corners);
          }
          Area before = i < roomFootprints ? new Area(remaining) : null;
          remaining.subtract(footprints.get(i));
          if (before != null && !roomOpen [i] && !before.equals(remaining)) {
            roomOpen [i] = true;
            openRooms++;
          }
        }
      }
      for (int i = areas.size() - 1; i >= 0; i--) {
        if (areas.get(i).intersects(triangleBounds)) {
          if (remaining == null) {
            remaining = AssistantMeshBuilder.getArea(corners);
          }
          Area part = new Area(remaining);
          part.intersect(areas.get(i));
          if (!part.isEmpty()) {
            addGround(mesh, AssistantMeshBuilder.getTrapezoids(part), plane, areaMaterials.get(i));
            remaining.subtract(areas.get(i));
          }
        }
      }
      if (remaining == null) {
        addGround(mesh, Collections.singletonList(corners), plane, material);
      } else if (!remaining.isEmpty()) {
        addGround(mesh, AssistantMeshBuilder.getTrapezoids(remaining), plane, material);
      }
      // The sides of the triangles that have no neighbor are the border of the ground
      for (int i = 0; i < 3; i++) {
        float [] point = triangle [i];
        float [] next = triangle [(i + 1) % 3];
        String key = point [0] + "," + point [1] + ";" + next [0] + "," + next [1];
        String reverseKey = next [0] + "," + next [1] + ";" + point [0] + "," + point [1];
        if (sides.containsKey(reverseKey)) {
          sides.get(reverseKey) [0]++;
        } else {
          sides.put(key, new int [] {1, sidePoints.size()});
          sidePoints.add(new float [][] {point, next, triangle [(i + 2) % 3]});
        }
      }
    }
    AssistantMeshBuilder.Material sideMaterial = mesh.getMaterial(0x7A6A58);
    for (int [] side : sides.values()) {
      if (side [0] == 1) {
        float [][] border = sidePoints.get(side [1]);
        float [] point = border [0];
        float [] next = border [1];
        // Seen from the outside, away from the third corner of the triangle
        float nx = next [1] - point [1];
        float ny = -(next [0] - point [0]);
        if (nx * (border [2][0] - point [0]) + ny * (border [2][1] - point [1]) > 0) {
          nx = -nx;
          ny = -ny;
        }
        mesh.addPolygon(new float [][] {
            {point [0], lowest - skirt - levelElevation, point [1]}, {next [0], lowest - skirt - levelElevation, next [1]},
            {next [0], next [2] - levelElevation, next [1]}, {point [0], point [2] - levelElevation, point [1]}},
            sideMaterial, nx, 0, ny);
      }
    }
    counts [0] = openRooms;
    counts [1] = areas.size();
    return mesh.store("terrain");
  }

  /**
   * Returns a text that changes when the rooms and the walls the ground may be open under change.
   */
  private String getFootprintSignature(Level terrainLevel) {
    StringBuilder signature = new StringBuilder();
    for (Room room : this.home.getRooms()) {
      if (room.isFloorVisible() && room.getLevel() != terrainLevel) {
        signature.append('R').append(room.getLevel() != null ? room.getLevel().getElevation() : 0f);
        for (float [] point : room.getPoints()) {
          signature.append(',').append(point [0]).append(',').append(point [1]);
        }
      }
    }
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() != terrainLevel) {
        signature.append('W').append(wall.getLevel() != null ? wall.getLevel().getElevation() : 0f);
        for (float [] point : wall.getPoints()) {
          signature.append(',').append(point [0]).append(',').append(point [1]);
        }
      }
    }
    return Integer.toHexString(signature.toString().hashCode()) + "-" + signature.length();
  }

  /**
   * Builds again the model of the grounds of the home if the rooms or the walls they are
   * open under changed since they were built. The model of a ground follows from its
   * description and from the rooms: replacing it isn't a change to undo.
   * @return the count of grounds built again
   */
  @SuppressWarnings("unchecked")
  int refreshTerrain() {
    int count = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      String spec = piece.getProperty(TERRAIN_PARAMETERS_PROPERTY);
      if (spec == null || piece.getProperty(TERRAIN_PROPERTY) == null) {
        continue;
      }
      String signature = getFootprintSignature(piece.getLevel());
      if (signature.equals(piece.getProperty(TERRAIN_FOOTPRINT_PROPERTY))) {
        continue;
      }
      try {
        Object parsed = JSONParser.parse(spec);
        if (!(parsed instanceof Map)) {
          continue;
        }
        Map<String, Object> parameters = (Map<String, Object>)parsed;
        List<float []> points = getTerrainPoints(parameters);
        List<float [][]> triangles = triangulate(points);
        if (triangles.isEmpty()) {
          continue;
        }
        float lowest = Float.MAX_VALUE;
        for (float [] point : points) {
          lowest = Math.min(lowest, point [2]);
        }
        float skirt = Math.max(1, Math.min(2000, AssistantFurnitureBuilder.getNumber(parameters, "thickness", 30)));
        AssistantGeneratedModel model = buildTerrainModel(parameters, triangles, piece.getLevel(), lowest, skirt,
            new ArrayList<String>(), new int [2]);
        piece.setModel(model.getModel());
        piece.setIcon(model.getIcon());
        piece.setPlanIcon(model.getPlanIcon());
        piece.setProperty(TERRAIN_FOOTPRINT_PROPERTY, signature);
        count++;
      } catch (IOException ex) {
        // The ground keeps its model
      } catch (RuntimeException ex) {
        // The ground keeps its model
      }
    }
    return count;
  }

  private static void addGround(AssistantMeshBuilder mesh, List<float [][]> polygons, float [] plane,
                                AssistantMeshBuilder.Material material) {
    for (float [][] polygon : polygons) {
      float [][] points = new float [polygon.length][];
      for (int i = 0; i < polygon.length; i++) {
        points [i] = new float [] {polygon [i][0], plane [0] + plane [1] * polygon [i][0] + plane [2] * polygon [i][1],
                                   polygon [i][1]};
      }
      mesh.addPolygon(points, material, 0, 1, 0);
    }
  }

  /**
   * Returns the triangles (Delaunay) joining the given points (x, y, z),
   * each one an array of three points.
   */
  static List<float [][]> triangulate(List<float []> points) {
    // Without the points at the same place
    List<float []> vertices = new ArrayList<float []>();
    Map<String, Integer> places = new HashMap<String, Integer>();
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (float [] point : points) {
      String place = Math.round(point [0]) + "," + Math.round(point [1]);
      Integer index = places.get(place);
      if (index != null) {
        vertices.set(index, point);
      } else {
        places.put(place, vertices.size());
        vertices.add(point);
      }
      minX = Math.min(minX, point [0]);
      minY = Math.min(minY, point [1]);
      maxX = Math.max(maxX, point [0]);
      maxY = Math.max(maxY, point [1]);
    }
    List<float [][]> result = new ArrayList<float [][]>();
    if (vertices.size() < 3) {
      return result;
    }
    // Bowyer-Watson: a triangle around all the points, then each point added in turn
    double size = Math.max(maxX - minX, maxY - minY) + 1;
    double centerX = (minX + maxX) / 2.0;
    double centerY = (minY + maxY) / 2.0;
    int count = vertices.size();
    double [][] coordinates = new double [count + 3][];
    for (int i = 0; i < count; i++) {
      coordinates [i] = new double [] {vertices.get(i) [0], vertices.get(i) [1]};
    }
    coordinates [count] = new double [] {centerX - 30 * size, centerY - 10 * size};
    coordinates [count + 1] = new double [] {centerX + 30 * size, centerY - 10 * size};
    coordinates [count + 2] = new double [] {centerX, centerY + 30 * size};
    List<int []> triangles = new ArrayList<int []>();
    triangles.add(new int [] {count, count + 1, count + 2});
    for (int i = 0; i < count; i++) {
      double [] point = coordinates [i];
      List<int []> edges = new ArrayList<int []>();
      for (int t = triangles.size() - 1; t >= 0; t--) {
        int [] triangle = triangles.get(t);
        if (isInCircle(coordinates [triangle [0]], coordinates [triangle [1]], coordinates [triangle [2]], point)) {
          triangles.remove(t);
          for (int side = 0; side < 3; side++) {
            int start = triangle [side];
            int end = triangle [(side + 1) % 3];
            boolean shared = false;
            for (int e = 0; e < edges.size(); e++) {
              if (edges.get(e) [0] == end && edges.get(e) [1] == start
                  || edges.get(e) [0] == start && edges.get(e) [1] == end) {
                edges.remove(e);
                shared = true;
                break;
              }
            }
            if (!shared) {
              edges.add(new int [] {start, end});
            }
          }
        }
      }
      for (int [] edge : edges) {
        triangles.add(new int [] {edge [0], edge [1], i});
      }
    }
    for (int [] triangle : triangles) {
      if (triangle [0] < count && triangle [1] < count && triangle [2] < count) {
        double [] a = coordinates [triangle [0]];
        double [] b = coordinates [triangle [1]];
        double [] c = coordinates [triangle [2]];
        if (Math.abs((b [0] - a [0]) * (c [1] - a [1]) - (b [1] - a [1]) * (c [0] - a [0])) > 0.01) {
          result.add(new float [][] {vertices.get(triangle [0]), vertices.get(triangle [1]), vertices.get(triangle [2])});
        }
      }
    }
    return result;
  }

  private static boolean isInCircle(double [] a, double [] b, double [] c, double [] point) {
    double ax = a [0] - point [0];
    double ay = a [1] - point [1];
    double bx = b [0] - point [0];
    double by = b [1] - point [1];
    double cx = c [0] - point [0];
    double cy = c [1] - point [1];
    double determinant = (ax * ax + ay * ay) * (bx * cy - cx * by)
        - (bx * bx + by * by) * (ax * cy - cx * ay)
        + (cx * cx + cy * cy) * (ax * by - bx * ay);
    double orientation = (b [0] - a [0]) * (c [1] - a [1]) - (b [1] - a [1]) * (c [0] - a [0]);
    return orientation > 0 ? determinant > 0 : determinant < 0;
  }

  /**
   * Returns the height above the elevation 0 of the terrain at a point of
   * the plan, or <code>null</code> if no terrain covers it.
   */
  Float getTerrainHeight(float x, float y) {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      String spec = piece.getProperty(TERRAIN_PROPERTY);
      if (spec == null) {
        continue;
      }
      if (!spec.equals(this.terrainSpec)) {
        this.terrainTriangles = HomeTerrain.getTriangles(spec);
        this.terrainSpec = spec;
      }
      for (float [][] triangle : this.terrainTriangles) {
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
    return null;
  }

  // ---- Roofs ----

  private static final String [] ROOF_PARAMETERS = {"points", "eave_height", "overhang", "pitch", "thickness", "type",
      "ridge", "low_side", "edges", "color", "texture", "texture_id", "texture_category", "texture_scale", "texture_angle",
      "gable_color", "fascia_color", "underside_color", "gutter_color", "fascia", "soffit", "ridge_cap", "gutters",
      "downspouts", "gable_walls", "dormers"};

  private void addRoof(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Level level = getLevel(command, "add_roof", notes);
    Map<String, Object> spec = new LinkedHashMap<String, Object>();
    for (String parameter : ROOF_PARAMETERS) {
      if (command.getParameters().get(parameter) != null) {
        spec.put(parameter, command.getParameters().get(parameter));
      }
    }
    if (!(spec.get("eave_height") instanceof Number)) {
      // On top of the walls of the level
      float wallTop = 0;
      for (Wall wall : this.home.getWalls()) {
        if (wall.getLevel() == level) {
          wallTop = Math.max(wallTop, wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight());
        }
      }
      spec.put("eave_height", Double.valueOf(wallTop > 0 ? wallTop : level != null ? level.getHeight() : this.home.getWallHeight()));
    }
    if (spec.get("dormers") instanceof List) {
      List<Object> dormers = new ArrayList<Object>();
      for (Object dormer : (List<?>)spec.get("dormers")) {
        if (dormer instanceof Map) {
          dormers.add(resolveDormer((Map<?, ?>)dormer, notes));
        }
      }
      spec.put("dormers", dormers);
    }
    AssistantRoofModel roof = AssistantRoofModel.create(spec);
    AssistantGeneratedModel model = roof.build(spec, getTextureFinder(), getGroundHeight(level));
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : "Roof";
    HomePieceOfFurniture piece = createPiece(name, model, level, true, "roof");
    piece.setProperty(ROOF_SPEC_PROPERTY, toJson(spec));
    piece.setProperty(HomeItemTags.TAGS_PROPERTY, "roof");
    add(piece);
    tally [0]++;
    int windows = syncDormerWindows(piece, null, roof, level, -1, tally);
    boolean allLevelsShown = this.home.getEnvironment().isAllLevelsVisible();
    if (!allLevelsShown && this.home.getLevels().size() > 1) {
      this.home.getEnvironment().setAllLevelsVisible(true);
    }
    notes.add("Added the roof " + this.executor.getId(piece) + " \"" + name + "\": " + describe(roof, piece, level)
        + (windows > 0 ? ", with " + windows + (windows == 1 ? " dormer window" : " dormer windows") : "")
        + (allLevelsShown || this.home.getLevels().size() < 2 ? "" : "; the 3D view now displays all levels so the roof shows")
        + ". The plans of the levels above show it as a dashed outline. Hide the ceiling of the rooms under its slopes "
        + "with modify_room if they are open to it, and close the walls under its gables with "
        + "{\"action\":\"fit_walls_to_roof\",\"roof\":\"" + this.executor.getId(piece) + "\"}");
    for (String note : roof.getNotes()) {
      notes.add("Roof: " + note);
    }
  }

  private String describe(AssistantRoofModel roof, HomePieceOfFurniture piece, Level level) {
    int hips = 0;
    int gables = 0;
    for (int i = 0; i < roof.getEdgeCount(); i++) {
      if (roof.isEdgeSloped(i)) {
        hips++;
      } else {
        gables++;
      }
    }
    int parts = roof.getGeometry().getParts().size() - roof.getDormerCount();
    return hips + (hips == 1 ? " eave" : " eaves") + " and " + gables + (gables == 1 ? " gable or vertical end" : " gables or vertical ends")
        + (parts > 1 ? ", in " + parts + " parts that meet" : "") + ", eaves at " + format(roof.getEaveHeight())
        + " cm and top at " + format(roof.getGeometry().getMaxHeight()) + " cm above the floor"
        + (level != null ? " of the level \"" + level.getName() + "\"" : "") + ", "
        + format(piece.getWidth()) + " x " + format(piece.getDepth()) + " cm with its overhangs"
        + (roof.getDormerCount() > 0 ? ", " + roof.getDormerCount() + (roof.getDormerCount() == 1 ? " dormer" : " dormers") : "");
  }

  /**
   * Returns the height of the ground, measured from the floor of <code>level</code>.
   */
  private float getGroundHeight(Level level) {
    return level != null ? -level.getElevation() : 0;
  }

  /**
   * Returns a copy of the description of a dormer, with the size of its window.
   */
  private Map<String, Object> resolveDormer(Map<?, ?> dormer, List<String> notes) {
    Map<String, Object> resolved = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : dormer.entrySet()) {
      resolved.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    resolved.remove("action");
    resolved.remove("roof");
    // Its windows: "window":"<name>" with an optional "windows":N for N of them side by side,
    // or "windows":[...] with a name or {"name","width","height","sill"} for each one
    List<Object> windows = new ArrayList<Object>();
    Object window = resolved.get("window");
    Object list = resolved.get("windows");
    if (list instanceof List) {
      for (Object item : (List<?>)list) {
        Map<?, ?> itemMap = item instanceof Map ? (Map<?, ?>)item : null;
        Object name = itemMap != null ? itemMap.get("name") : item;
        if (name == null) {
          name = window;
        }
        Map<String, Object> resolvedWindow = resolveWindow(name, notes);
        if (resolvedWindow != null) {
          if (itemMap != null) {
            for (String size : new String [] {"width", "height", "sill"}) {
              if (itemMap.get(size) instanceof Number) {
                resolvedWindow.put(size, itemMap.get(size));
              }
            }
          }
          windows.add(resolvedWindow);
        }
      }
    } else if (window instanceof String && ((String)window).trim().length() > 0) {
      int count = list instanceof Number ? Math.max(1, Math.min(6, ((Number)list).intValue())) : 1;
      Map<String, Object> resolvedWindow = resolveWindow(window, notes);
      for (int i = 0; i < count && resolvedWindow != null; i++) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>(resolvedWindow);
        if (resolved.get("window_width") instanceof Number && resolved.get("window_height") instanceof Number) {
          copy.put("width", resolved.get("window_width"));
          copy.put("height", resolved.get("window_height"));
        }
        windows.add(copy);
      }
    }
    if (windows.isEmpty() && window == null
        && (list instanceof List && !((List<?>)list).isEmpty()
            || list instanceof Number && ((Number)list).intValue() > 0)) {
      notes.add("The dormer was built without window: name the window of the catalog with \"window\":\"<name>\" "
          + "beside \"windows\":N, or with \"name\" in each item of \"windows\" (use search_catalog)");
    }
    resolved.remove("window");
    resolved.remove("window_width");
    resolved.remove("window_height");
    if (windows.isEmpty()) {
      resolved.remove("windows");
    } else {
      resolved.put("windows", windows);
    }
    return resolved;
  }

  /**
   * Returns the name and the size of the catalog window of the given name, or <code>null</code>.
   */
  private Map<String, Object> resolveWindow(Object name, List<String> notes) {
    if (!(name instanceof String) || ((String)name).trim().length() == 0) {
      return null;
    }
    CatalogPieceOfFurniture catalogPiece = this.executor.findCatalogPiece((String)name, true);
    if (catalogPiece == null) {
      notes.add("No window of the catalog is named \"" + name + "\": the dormer was built without it (use search_catalog)");
      return null;
    }
    Map<String, Object> window = new LinkedHashMap<String, Object>();
    window.put("name", catalogPiece.getName());
    window.put("width", Double.valueOf(catalogPiece.getWidth()));
    window.put("height", Double.valueOf(catalogPiece.getHeight()));
    return window;
  }

  /**
   * Puts the windows of the dormers of a roof at their place. The windows the roof had before
   * are kept, moved with their dormer, and deleted if their dormer no longer exists; windows
   * added or copied by the user are left alone.
   * @param oldRoof       the roof before its change, or <code>null</code> for a new roof
   * @param removedDormer the index of the dormer removed from the description of the roof, or -1
   * @return the count of windows of the dormers
   */
  private int syncDormerWindows(HomePieceOfFurniture roofPiece, AssistantRoofModel oldRoof, AssistantRoofModel roof,
                                Level level, int removedDormer, int [] tally) {
    List<HomePieceOfFurniture> tagged = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (roofPiece.getId().equals(piece.getProperty(ROOF_WINDOW_PROPERTY))) {
        tagged.add(piece);
      }
    }
    float levelElevation = 0;
    // The window that stands at each place of the roof as it was
    List<float []> oldSlots = oldRoof != null ? oldRoof.getWindows() : new ArrayList<float []>();
    final HomePieceOfFurniture [] oldPieces = new HomePieceOfFurniture [oldSlots.size()];
    for (int i = 0; i < oldSlots.size(); i++) {
      float [] slot = oldSlots.get(i);
      double bestDistance = 40;
      for (HomePieceOfFurniture piece : tagged) {
        double distance = Math.hypot(piece.getX() - slot [0], piece.getY() - slot [1]);
        boolean used = false;
        for (HomePieceOfFurniture oldPiece : oldPieces) {
          used |= oldPiece == piece;
        }
        if (!used && distance < bestDistance && Math.abs(piece.getElevation() - levelElevation - slot [3]) < 80) {
          bestDistance = distance;
          oldPieces [i] = piece;
        }
      }
    }
    final List<float []> slots = roof.getWindows();
    final List<HomePieceOfFurniture> moved = new ArrayList<HomePieceOfFurniture>();
    final List<float []> movedSlots = new ArrayList<float []>();
    final List<boolean []> movedResized = new ArrayList<boolean []>();
    List<HomePieceOfFurniture> added = new ArrayList<HomePieceOfFurniture>();
    boolean [] reused = new boolean [oldSlots.size()];
    for (int i = 0; i < slots.size(); i++) {
      float [] slot = slots.get(i);
      HomePieceOfFurniture existing = null;
      for (int j = 0; j < oldSlots.size() && existing == null; j++) {
        float [] oldSlot = oldSlots.get(j);
        int oldDormer = (int)oldSlot [7];
        int dormer = removedDormer < 0 || oldDormer < removedDormer ? oldDormer
            : oldDormer == removedDormer ? -1 : oldDormer - 1;
        if (!reused [j] && oldPieces [j] != null && dormer == (int)slot [7] && (int)oldSlot [8] == (int)slot [8]) {
          existing = oldPieces [j];
          reused [j] = true;
          // A window the user resized keeps its size
          movedResized.add(new boolean [] {Math.abs(existing.getWidth() - oldSlot [4]) < 0.5f
              && Math.abs(existing.getHeight() - oldSlot [5]) < 0.5f});
        }
      }
      if (existing != null) {
        moved.add(existing);
        movedSlots.add(slot);
      } else if (roof.getWindowNames().get(i) != null) {
        CatalogPieceOfFurniture catalogPiece = this.executor.findCatalogPiece(roof.getWindowNames().get(i), true);
        if (catalogPiece != null) {
          HomePieceOfFurniture piece = this.homeController.getFurnitureController().createHomePieceOfFurniture(catalogPiece);
          piece.setX(slot [0]);
          piece.setY(slot [1]);
          piece.setAngle(slot [2]);
          piece.setElevation(slot [3]);
          if (piece.isResizable()) {
            piece.setWidth(slot [4]);
            piece.setHeight(slot [5]);
          }
          piece.setLevel(level);
          piece.setProperty(ROOF_WINDOW_PROPERTY, roofPiece.getId());
          piece.setProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY, "dormer");
          added.add(piece);
        }
      }
    }
    List<Selectable> deleted = new ArrayList<Selectable>();
    for (int j = 0; j < oldSlots.size(); j++) {
      if (!reused [j] && oldPieces [j] != null) {
        deleted.add(oldPieces [j]);
      }
    }
    this.executor.beginTurnUpdate();
    if (!moved.isEmpty()) {
      this.executor.postModification(new ArrayList<Selectable>(moved), new Runnable() {
          public void run() {
            for (int i = 0; i < moved.size(); i++) {
              HomePieceOfFurniture piece = moved.get(i);
              float [] slot = movedSlots.get(i);
              piece.setX(slot [0]);
              piece.setY(slot [1]);
              piece.setAngle(slot [2]);
              piece.setElevation(slot [3]);
              if (piece.isResizable() && movedResized.get(i) [0]) {
                piece.setWidth(slot [4]);
                piece.setHeight(slot [5]);
              }
            }
          }
        });
    }
    if (!deleted.isEmpty()) {
      this.homeController.getPlanController().deleteItems(deleted);
      tally [5] += deleted.size();
    }
    if (!added.isEmpty()) {
      Level selectedLevel = this.home.getSelectedLevel();
      if (level != null && level != selectedLevel) {
        this.home.setSelectedLevel(level);
      }
      this.homeController.getFurnitureController().addFurniture(added);
      if (this.home.getSelectedLevel() != selectedLevel && selectedLevel != null) {
        this.home.setSelectedLevel(selectedLevel);
      }
      tally [1] += added.size();
    }
    return moved.size() + added.size();
  }

  /**
   * Returns the description of a roof kept in its piece.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> getRoofSpec(HomePieceOfFurniture roofPiece) throws IOException {
    Object parsed;
    try {
      parsed = JSONParser.parse(roofPiece.getProperty(ROOF_SPEC_PROPERTY));
    } catch (IllegalArgumentException ex) {
      parsed = null;
    }
    if (!(parsed instanceof Map)) {
      throw new IOException("the description of this roof can't be read");
    }
    return new LinkedHashMap<String, Object>((Map<String, Object>)parsed);
  }

  /**
   * Builds a roof again from a new description, in an undoable way, and returns its shape.
   */
  private AssistantRoofModel rebuildRoof(HomePieceOfFurniture roofPiece, Map<String, Object> oldSpec,
                                         Map<String, Object> spec, int removedDormer,
                                         int [] tally, List<String> notes) throws IOException {
    AssistantRoofModel oldRoof = AssistantRoofModel.create(oldSpec);
    AssistantRoofModel roof = AssistantRoofModel.create(spec);
    Level level = roofPiece.getLevel();
    AssistantGeneratedModel model = roof.build(spec, getTextureFinder(), getGroundHeight(level));
    float [] bounds = model.getBounds();
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put(ROOF_SPEC_PROPERTY, toJson(spec));
    replaceModel(roofPiece, model, (bounds [0] + bounds [3]) / 2, (bounds [2] + bounds [5]) / 2, bounds [1], properties);
    tally [4]++;
    syncDormerWindows(roofPiece, oldRoof, roof, level, removedDormer, tally);
    for (String note : roof.getNotes()) {
      notes.add("Roof: " + note);
    }
    return roof;
  }

  private String describeDormers(AssistantRoofModel roof) {
    if (roof.getDormerDescriptions().isEmpty()) {
      return "it has no dormer";
    }
    StringBuilder text = new StringBuilder("its dormers: ");
    for (String description : roof.getDormerDescriptions()) {
      text.append(text.charAt(text.length() - 2) == ':' ? "" : "; ").append(description);
    }
    return text.toString();
  }

  /**
   * Returns the description of the edges and of the dormers of a roof, for <code>describe_item</code>,
   * or <code>null</code> if <code>piece</code> isn't a roof built by <code>add_roof</code>.
   */
  String describeRoof(HomePieceOfFurniture piece) {
    if (piece.getProperty(ROOF_SPEC_PROPERTY) == null) {
      return null;
    }
    try {
      AssistantRoofModel roof = AssistantRoofModel.create(getRoofSpec(piece));
      StringBuilder text = new StringBuilder("roof with eaves at " + format(roof.getEaveHeight()) + " cm, edges: ");
      for (int i = 0; i < roof.getEdgeCount(); i++) {
        text.append(i > 0 ? ", " : "").append(i + 1).append(' ').append(roof.getEdgeTypeName(i));
        if (roof.isEdgeSloped(i)) {
          text.append(' ').append(format(roof.getEdgePitch(i))).append(" deg");
        }
      }
      return text.append("; ").append(describeDormers(roof)).toString();
    } catch (IOException ex) {
      return null;
    }
  }

  /**
   * Removes a dormer of a roof, or changes it.
   */
  @SuppressWarnings("unchecked")
  private void modifyDormer(AssistantCommand command, boolean remove, int [] tally, List<String> notes) throws IOException {
    String action = remove ? "remove_dormer" : "modify_dormer";
    HomePieceOfFurniture roofPiece = getRoof(command, action);
    Map<String, Object> oldSpec = getRoofSpec(roofPiece);
    Map<String, Object> spec = new LinkedHashMap<String, Object>(oldSpec);
    List<Object> dormers = new ArrayList<Object>();
    if (spec.get("dormers") instanceof List) {
      dormers.addAll((List<?>)spec.get("dormers"));
    }
    AssistantRoofModel oldRoof = AssistantRoofModel.create(oldSpec);
    double indexValue = command.getDouble("index", command.getDouble("dormer", dormers.size() == 1 ? 1 : Double.NaN));
    int index = Double.isNaN(indexValue) ? -1 : (int)Math.round(indexValue) - 1;
    if (index < 0 || index >= dormers.size() || !(dormers.get(index) instanceof Map)) {
      throw new IOException(dormers.isEmpty() ? "the roof " + this.executor.getId(roofPiece) + " has no dormer"
          : "give the dormer with \"index\" from 1 to " + dormers.size() + "; " + describeDormers(oldRoof));
    }
    if (remove) {
      dormers.remove(index);
    } else {
      Map<String, Object> dormer = new LinkedHashMap<String, Object>((Map<String, Object>)dormers.get(index));
      Map<String, Object> changes = new LinkedHashMap<String, Object>(command.getParameters());
      for (String key : new String [] {"action", "roof", "id", "index", "dormer"}) {
        changes.remove(key);
      }
      if (changes.isEmpty()) {
        throw new IOException("give what changes: width, height, offset or position, setback, type, pitch, ridge, "
            + "side or edge, overhang, window, windows");
      }
      // A place given one way replaces the place given the other way
      if (changes.containsKey("offset")) {
        dormer.remove("position");
      } else if (changes.containsKey("position")) {
        dormer.remove("offset");
      }
      if (changes.containsKey("side")) {
        dormer.remove("edge");
      } else if (changes.containsKey("edge")) {
        dormer.remove("side");
      }
      if (changes.containsKey("pitch") && !changes.containsKey("ridge")) {
        dormer.remove("ridge");
      }
      boolean windowsChanged = changes.containsKey("window") || changes.containsKey("windows");
      Object oldWindows = dormer.get("windows");
      dormer.putAll(changes);
      if (windowsChanged) {
        if (!changes.containsKey("window") && changes.get("windows") instanceof Number
            && oldWindows instanceof List && !((List<?>)oldWindows).isEmpty()
            && ((List<?>)oldWindows).get(0) instanceof Map) {
          // Only their count changes
          dormer.put("window", ((Map<?, ?>)((List<?>)oldWindows).get(0)).get("name"));
        }
        if (Boolean.FALSE.equals(changes.get("window")) || Boolean.FALSE.equals(changes.get("windows"))) {
          dormer.remove("window");
          dormer.remove("windows");
        } else {
          dormer = resolveDormer(dormer, notes);
        }
      }
      dormers.set(index, dormer);
    }
    if (dormers.isEmpty()) {
      spec.remove("dormers");
    } else {
      spec.put("dormers", dormers);
    }
    AssistantRoofModel roof = rebuildRoof(roofPiece, oldSpec, spec, remove ? index : -1, tally, notes);
    notes.add((remove ? "Removed the dormer " : "Modified the dormer ") + (index + 1) + " of the roof "
        + this.executor.getId(roofPiece) + "; " + describeDormers(roof)
        + (remove && !roof.getDormerDescriptions().isEmpty() ? " (the dormers after the removed one were numbered again)" : "")
        + ". Run fit_walls_to_roof again if walls were fitted to this roof");
    refreshCeilings(notes);
  }

  /**
   * Changes the description of a roof without building it from scratch.
   */
  @SuppressWarnings("unchecked")
  private void modifyRoof(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    final HomePieceOfFurniture roofPiece = getRoof(command, "modify_roof");
    Map<String, Object> oldSpec = getRoofSpec(roofPiece);
    AssistantRoofModel oldRoof = AssistantRoofModel.create(oldSpec);
    Map<String, Object> spec = new LinkedHashMap<String, Object>(oldSpec);
    int changes = 0;
    for (String parameter : ROOF_PARAMETERS) {
      Object value = command.getParameters().get(parameter);
      if (value != null && !parameter.equals("dormers")) {
        spec.put(parameter, value);
        changes++;
      }
    }
    if (command.has("type") && !command.has("edges")) {
      // The type gives the kind of every edge
      spec.remove("edges");
    }
    // One edge: {"edge":{"index":2,"type":"gable","pitch":35,"overhang":20}}, or a list of such edges
    List<Object> edgeChanges = new ArrayList<Object>();
    if (command.getParameters().get("edge") instanceof Map) {
      edgeChanges.add(command.getParameters().get("edge"));
    } else if (command.getParameters().get("edge") instanceof List) {
      edgeChanges.addAll((List<?>)command.getParameters().get("edge"));
    }
    if (!edgeChanges.isEmpty()) {
      // The edges as they are, by their number
      AssistantRoofModel current = AssistantRoofModel.create(spec);
      List<Object> edges = new ArrayList<Object>();
      for (int i = 0; i < current.getEdgeCount(); i++) {
        Map<String, Object> edge = new LinkedHashMap<String, Object>();
        edge.put("type", current.getEdgeTypeName(i));
        edge.put("pitch", Double.valueOf(current.getEdgePitch(i)));
        edge.put("overhang", Double.valueOf(current.getEdgeOverhang(i)));
        edges.add(edge);
      }
      for (Object edgeChange : edgeChanges) {
        if (edgeChange instanceof Map && ((Map<?, ?>)edgeChange).get("index") instanceof Number) {
          int index = ((Number)((Map<?, ?>)edgeChange).get("index")).intValue() - 1;
          if (index < 0 || index >= edges.size()) {
            throw new IOException("the edges of this roof are numbered from 1 to " + edges.size()
                + " (the edge 1 goes from its first point to the second one)");
          }
          Map<String, Object> edge = (Map<String, Object>)edges.get(index);
          for (Map.Entry<?, ?> entry : ((Map<?, ?>)edgeChange).entrySet()) {
            if (!"index".equals(entry.getKey())) {
              edge.put(String.valueOf(entry.getKey()), entry.getValue());
            }
          }
          if (((Map<?, ?>)edgeChange).get("type") != null && ((Map<?, ?>)edgeChange).get("overhang") == null
              && "abut".equals(String.valueOf(((Map<?, ?>)edgeChange).get("type")))) {
            edge.put("overhang", Double.valueOf(0));
          }
          changes++;
        } else {
          throw new IOException("\"edge\" must give the \"index\" of the edge to change, from 1");
        }
      }
      spec.put("edges", edges);
    }
    if (changes == 0) {
      throw new IOException("give what changes: eave_height, pitch, overhang, type, edges, \"edge\":{\"index\":N,\"type\":\"hip|gable|abut\"}, "
          + "points, thickness, color, texture, gutters, soffit, ridge_cap, gable_walls...");
    }
    if (spec.get("dormers") instanceof List) {
      // The windows of dormers keep their size
      spec.put("dormers", new ArrayList<Object>((List<?>)spec.get("dormers")));
    }
    AssistantRoofModel roof = rebuildRoof(roofPiece, oldSpec, spec, -1, tally, notes);
    final float raise = (float)(roof.getEaveHeight() - oldRoof.getEaveHeight());
    int raised = 0;
    if (command.getBoolean("raise_walls", false) && Math.abs(raise) > 0.01f) {
      // The walls under the roof follow its eaves
      final List<Selectable> walls = new ArrayList<Selectable>();
      for (Wall wall : this.home.getWalls()) {
        if (wall.getLevel() == roofPiece.getLevel()
            && !Double.isNaN(roof.getGeometry().getUndersideHeight((wall.getXStart() + wall.getXEnd()) / 2,
                   (wall.getYStart() + wall.getYEnd()) / 2))) {
          float height = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
          if (height + raise >= 1 && (wall.getHeightAtEnd() == null || wall.getHeightAtEnd() + raise >= 1)) {
            walls.add(wall);
          }
        }
      }
      if (!walls.isEmpty()) {
        this.executor.postModification(walls, new Runnable() {
            public void run() {
              for (Selectable item : walls) {
                Wall wall = (Wall)item;
                wall.setHeight((wall.getHeight() != null ? wall.getHeight() : home.getWallHeight()) + raise);
                if (wall.getHeightAtEnd() != null) {
                  wall.setHeightAtEnd(wall.getHeightAtEnd() + raise);
                }
              }
            }
          });
        raised = walls.size();
        tally [4] += raised;
      }
    }
    notes.add("Modified the roof " + this.executor.getId(roofPiece) + ": " + describe(roof, roofPiece, roofPiece.getLevel())
        + (raised > 0 ? "; " + raised + " walls under it were " + (raise > 0 ? "raised" : "lowered") + " by "
            + format(Math.abs(raise)) + " cm" : Math.abs(raise) > 0.01f
            ? "; its eaves moved by " + format(raise) + " cm: the walls under it didn't (\"raise_walls\":true moves them too)" : "")
        + ". Run fit_walls_to_roof again if walls were fitted to this roof");
    refreshCeilings(notes);
  }

  /**
   * Returns the roof a command designates with its parameter <code>roof</code>
   * or <code>id</code>, or the only roof of the home.
   */
  private HomePieceOfFurniture getRoof(AssistantCommand command, String action) throws IOException {
    String token = command.getString("roof") != null ? command.getString("roof") : command.getString("id");
    List<HomePieceOfFurniture> roofs = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty(ROOF_SPEC_PROPERTY) != null) {
        roofs.add(piece);
      }
    }
    if (token != null) {
      HomeObject item = HomeItemIds.find(this.home, token);
      if (item == null) {
        for (HomePieceOfFurniture roof : roofs) {
          if (token.trim().equalsIgnoreCase(roof.getName())) {
            item = roof;
          }
        }
      }
      if (!(item instanceof HomePieceOfFurniture) || !roofs.contains(item)) {
        throw new IOException("\"" + token + "\" isn't a roof built by add_roof with \"points\""
            + (roofs.isEmpty() ? "; there is none in this home" : "; roofs: " + describeIds(roofs)));
      }
      return (HomePieceOfFurniture)item;
    } else if (roofs.size() == 1) {
      return roofs.get(0);
    } else {
      throw new IOException(roofs.isEmpty()
          ? "there is no roof built by add_roof with \"points\" in this home"
          : "give the roof with \"roof\":\"F#\"; roofs: " + describeIds(roofs));
    }
  }

  private String describeIds(List<HomePieceOfFurniture> furniture) {
    StringBuilder ids = new StringBuilder();
    for (HomePieceOfFurniture piece : furniture) {
      ids.append(ids.length() > 0 ? ", " : "").append(this.executor.getId(piece)).append(" \"").append(piece.getName()).append('"');
    }
    return ids.toString();
  }

  private void addDormer(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture roofPiece = getRoof(command, "add_dormer");
    Map<String, Object> oldSpec = getRoofSpec(roofPiece);
    Map<String, Object> spec = new LinkedHashMap<String, Object>(oldSpec);
    List<Object> dormers = new ArrayList<Object>();
    if (spec.get("dormers") instanceof List) {
      dormers.addAll((List<?>)spec.get("dormers"));
    }
    Map<String, Object> dormer = resolveDormer(command.getParameters(), notes);
    dormer.remove("id");
    dormers.add(dormer);
    spec.put("dormers", dormers);
    AssistantRoofModel check = AssistantRoofModel.create(spec);
    if (check.getDormerCount() <= countDormers(roofPiece)) {
      for (String note : check.getNotes()) {
        notes.add("Roof: " + note);
      }
      throw new IOException("the dormer couldn't be built");
    }
    AssistantRoofModel roof = rebuildRoof(roofPiece, oldSpec, spec, -1, tally, notes);
    notes.add("Added the dormer " + dormers.size() + " to the roof " + this.executor.getId(roofPiece) + "; "
        + describeDormers(roof) + ". The windows the other dormers had are kept. \"offset\" is measured in cm along the "
        + "side, from its west or north end for a \"side\" given by a cardinal point and from the start of the edge for "
        + "an \"edge\" number, to the center of the dormer; \"position\" is the same from 0 to 1");
    refreshCeilings(notes);
  }

  /**
   * Builds again the ceilings that follow a roof, after roofs changed.
   */
  private void refreshCeilings(List<String> notes) {
    int count = 0;
    for (Room room : this.home.getRooms()) {
      String ceiling = room.getProperty(ROOM_CEILING_PROPERTY);
      if (ceiling != null) {
        try {
          setRoomCeiling(room, "follow_roof", HomeRoofs.getFlatCeilingHeight(room));
          count++;
        } catch (IOException ex) {
          notes.add("The ceiling of the room " + this.executor.getId(room) + " no longer follows a roof (" + ex.getMessage() + ")");
        }
      }
    }
    if (count > 0) {
      notes.add(count + (count == 1 ? " ceiling that follows" : " ceilings that follow") + " the roof "
          + (count == 1 ? "was" : "were") + " built again");
    }
  }

  private HomePieceOfFurniture getRoomCeiling(Room room) {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (room.getId().equals(piece.getProperty(ROOM_CEILING_PIECE_PROPERTY))) {
        return piece;
      }
    }
    return null;
  }

  /**
   * Gives a room the ceiling of the roof above it: a lining under its slopes, flat from the height
   * <code>flatHeight</code> if it's given. The flat ceiling the room has at the height of the level must be hidden.
   * @param mode <code>follow_roof</code>, or <code>flat</code> to remove such a ceiling
   * @return a description of the ceiling
   */
  String setRoomCeiling(Room room, String mode, double flatHeight) throws IOException {
    HomePieceOfFurniture oldCeiling = getRoomCeiling(room);
    String normalizedMode = mode != null ? mode.trim().toLowerCase(Locale.ENGLISH).replace('-', '_') : "follow_roof";
    if (normalizedMode.equals("flat") || normalizedMode.equals("level") || normalizedMode.equals("none")
        || normalizedMode.equals("default")) {
      this.executor.beginTurnUpdate();
      if (oldCeiling != null) {
        this.homeController.getPlanController().deleteItems(Collections.singletonList(oldCeiling));
      }
      room.setProperty(ROOM_CEILING_PROPERTY, null);
      return "its ceiling no longer follows the roof";
    } else if (!normalizedMode.equals("follow_roof") && !normalizedMode.equals("roof") && !normalizedMode.equals("sloped")) {
      throw new IOException("\"ceiling\" is follow_roof or flat");
    }
    if (!Double.isNaN(flatHeight) && !(flatHeight >= 50 && flatHeight <= 2000)) {
      throw new IOException("\"flat_ceiling_at\" is the height of the flat part of the ceiling above the floor, from 50 to 2000 cm");
    }
    float roomElevation = room.getLevel() != null ? room.getLevel().getElevation() : 0;
    Area roomArea = AssistantMeshBuilder.getArea(room.getPoints());
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    HomeTexture texture = room.getCeilingTexture();
    int color = room.getCeilingColor() != null ? room.getCeilingColor() : 0xFAFAF7;
    AssistantMeshBuilder.Material material = texture != null
        ? mesh.getMaterial(texture, texture.getScale(), texture.getAngle(), texture.getXOffset(), texture.getYOffset(), color)
        : mesh.getMaterial(color);
    Area covered = new Area();
    double slopedArea = 0;
    double flatArea = 0;
    double [] flatPlane = {flatHeight, 0, 0};
    for (HomeRoofs.Roof roof : HomeRoofs.get(this.home)) {
      if (roof.elevation + roof.model.getGeometry().getMaxHeight() <= roomElevation + 1) {
        // A roof under the room
        continue;
      }
      for (AssistantRoofGeometry.Cell cell : roof.model.getGeometry().getCeilingCells()) {
        Area piece = AssistantMeshBuilder.getArea(AssistantRoofGeometry.toFloats(cell.polygon));
        piece.intersect(roomArea);
        // What an other roof already covers keeps its ceiling
        piece.subtract(covered);
        if (piece.isEmpty()) {
          continue;
        }
        covered.add(piece);
        // The underside of the roof above the floor of the room, a centimeter under it
        double [] plane = {cell.plane [0] - cell.part.getThickness() + roof.elevation - roomElevation - CEILING_GAP,
                           cell.plane [1], cell.plane [2]};
        for (float [][] trapezoid : AssistantMeshBuilder.getTrapezoids(piece)) {
          double [][] polygon = AssistantRoofGeometry.toDoubles(trapezoid);
          if (AssistantRoofGeometry.getSignedArea(polygon) < 0) {
            polygon = AssistantRoofGeometry.reverse(polygon);
          }
          double [][] sloped = Double.isNaN(flatHeight) ? polygon
              : AssistantRoofGeometry.clipLower(polygon, plane, flatPlane, true);
          if (sloped.length >= 3) {
            slopedArea += Math.abs(AssistantRoofGeometry.getSignedArea(sloped));
            addCeiling(mesh, sloped, plane, material);
          }
          if (!Double.isNaN(flatHeight)) {
            double [][] flat = AssistantRoofGeometry.clipLower(polygon, flatPlane, plane, false);
            if (flat.length >= 3) {
              flatArea += Math.abs(AssistantRoofGeometry.getSignedArea(flat));
              addCeiling(mesh, flat, flatPlane, material);
            }
          }
        }
      }
    }
    if (slopedArea + flatArea < 1) {
      throw new IOException("no roof built by add_roof with \"points\" is above this room");
    }
    HomePieceOfFurniture ceiling;
    AssistantGeneratedModel model = mesh.store("ceiling");
    String name = (room.getName() != null ? room.getName() : "Room") + " ceiling";
    if (oldCeiling != null) {
      float [] bounds = model.getBounds();
      replaceModel(oldCeiling, model, (bounds [0] + bounds [3]) / 2, (bounds [2] + bounds [5]) / 2, bounds [1],
          new LinkedHashMap<String, String>());
      ceiling = oldCeiling;
    } else {
      ceiling = createPiece(name, model, room.getLevel(), false, "ceiling");
      ceiling.setProperty(ROOM_CEILING_PIECE_PROPERTY, room.getId());
      add(ceiling);
    }
    room.setProperty(ROOM_CEILING_PROPERTY, Double.isNaN(flatHeight) ? "follow_roof" : "follow_roof:" + format(flatHeight));
    double roomSurface = Math.abs(AssistantRoofGeometry.getSignedArea(AssistantRoofGeometry.toDoubles(room.getPoints())));
    return "the piece " + this.executor.getId(ceiling) + " \"" + ceiling.getName() + "\" is its ceiling under the roof: "
        + format(slopedArea / 10000) + " m2 under the slopes"
        + (flatArea > 0 ? " and " + format(flatArea / 10000) + " m2 flat at " + format(flatHeight) + " cm" : "")
        + (roomSurface - slopedArea - flatArea > 100
            ? "; " + format((roomSurface - slopedArea - flatArea) / 10000) + " m2 of the room have no roof above them" : "");
  }

  private static void addCeiling(AssistantMeshBuilder mesh, double [][] polygon, double [] plane,
                                 AssistantMeshBuilder.Material material) {
    float [][] points = new float [polygon.length][];
    for (int i = 0; i < polygon.length; i++) {
      points [i] = new float [] {(float)polygon [i][0],
          (float)(plane [0] + plane [1] * polygon [i][0] + plane [2] * polygon [i][1]), (float)polygon [i][1]};
    }
    mesh.addPolygon(points, material, 0, -1, 0);
  }

  /**
   * Returns a sentence about the headroom of a room, or <code>null</code> if no ceiling is above it.
   */
  String describeHeadroom(Room room) {
    double [] headroom = HomeRoofs.getHeadroom(this.home, HomeRoofs.get(this.home), room);
    if (headroom == null || headroom [3] <= 0) {
      return null;
    }
    double total = headroom [3] + headroom [4];
    return "headroom from " + format(headroom [0]) + " to " + format(headroom [1]) + " cm, "
        + format(headroom [2] / 10000) + " m2 of " + format(total / 10000) + " m2 ("
        + Math.round(headroom [2] * 100 / total) + " %) at least " + Math.round(HomeRoofs.STANDING_HEIGHT)
        + " cm high (7 ft)"
        + (headroom [4] > total * 0.02 ? ", " + format(headroom [4] / 10000) + " m2 without any ceiling" : "");
  }

  /**
   * Reports the headroom of rooms.
   */
  private void reportHeadroom(AssistantCommand command, List<String> notes) {
    List<Room> rooms = new ArrayList<Room>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Room) {
        rooms.add((Room)item);
      }
    }
    boolean all = rooms.isEmpty();
    if (all) {
      rooms.addAll(this.home.getRooms());
    }
    StringBuilder text = new StringBuilder("Headroom (height of the ceiling above the floor: the underside of the roofs, "
        + "the flat part of a ceiling, the ceiling of the level):");
    int count = 0;
    List<HomeRoofs.Roof> roofs = HomeRoofs.get(this.home);
    for (Room room : rooms) {
      double [] headroom = HomeRoofs.getHeadroom(this.home, roofs, room);
      if (headroom == null || headroom [3] <= 0) {
        if (!all) {
          text.append("\n- ").append(this.executor.getId(room)).append(": no ceiling above it");
          count++;
        }
        continue;
      }
      // Without roof above, a room has the height of its level: said only if asked
      if (all && Math.abs(headroom [1] - headroom [0]) < 0.5 && Double.isNaN(HomeRoofs.getCeilingElevation(roofs,
              room.getXCenter(), room.getYCenter(), room.getLevel() != null ? room.getLevel().getElevation() : 0))) {
        continue;
      }
      text.append("\n- ").append(this.executor.getId(room))
          .append(room.getName() != null ? " \"" + room.getName() + "\"" : "").append(": ").append(describeHeadroom(room));
      count++;
    }
    notes.add(count > 0 ? text.toString() : "Headroom: no room is under a roof built by add_roof; every room has the height of its level");
  }

  private static int countDormers(HomePieceOfFurniture roofPiece) {
    try {
      Object spec = JSONParser.parse(roofPiece.getProperty(ROOF_SPEC_PROPERTY));
      return spec instanceof Map ? AssistantRoofModel.create((Map<?, ?>)spec).getDormerCount() : 0;
    } catch (IOException ex) {
      return 0;
    } catch (IllegalArgumentException ex) {
      return 0;
    }
  }

  /**
   * Gives the walls under a roof the height of its underside, cutting them
   * where their top changes of slope, as under the peak of a gable.
   */
  private void fitWallsToRoof(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture roofPiece = getRoof(command, "fit_walls_to_roof");
    Object parsed = JSONParser.parse(roofPiece.getProperty(ROOF_SPEC_PROPERTY));
    if (!(parsed instanceof Map)) {
      throw new IOException("the description of this roof can't be read");
    }
    final AssistantRoofModel roof = AssistantRoofModel.create((Map<?, ?>)parsed);
    AssistantRoofGeometry geometry = roof.getGeometry();
    Level roofLevel = roofPiece.getLevel();
    float roofElevation = roofLevel != null ? roofLevel.getElevation() : 0;
    double [][] polygon = AssistantRoofGeometry.toDoubles(
        AssistantFurnitureBuilder.getPoints((Map<?, ?>)parsed, "points", 2).toArray(new float [0][]));

    // The walls given, or the outside walls of the highest level under the roof
    List<Wall> walls = new ArrayList<Wall>();
    boolean given = false;
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Wall) {
        walls.add((Wall)item);
        given = true;
      }
    }
    if (!given) {
      boolean all = command.getBoolean("all", false);
      Level wallsLevel = null;
      for (int pass = 0; pass < 2 && walls.isEmpty(); pass++) {
        for (Wall wall : this.home.getWalls()) {
          Level level = wall.getLevel();
          float elevation = level != null ? level.getElevation() : 0;
          double middleX = (wall.getXStart() + wall.getXEnd()) / 2;
          double middleY = (wall.getYStart() + wall.getYEnd()) / 2;
          if (elevation > roofElevation + 1 || Double.isNaN(geometry.getUndersideHeight(middleX, middleY))) {
            continue;
          }
          if (pass == 0) {
            // The highest level that has walls under the roof
            if (wallsLevel == null || elevation > wallsLevel.getElevation()) {
              wallsLevel = level;
            }
          } else if (level == wallsLevel
                     && (all || getDistanceToEdges(polygon, middleX, middleY) <= wall.getThickness() / 2 + 6
                            && getDistanceToEdges(polygon, wall.getXStart(), wall.getYStart()) <= wall.getThickness() + 12
                            && getDistanceToEdges(polygon, wall.getXEnd(), wall.getYEnd()) <= wall.getThickness() + 12)) {
            walls.add(wall);
          }
        }
      }
    }
    if (walls.isEmpty()) {
      notes.add("fit_walls_to_roof found no wall under the roof " + this.executor.getId(roofPiece)
          + ": give them with \"ids\", or \"all\":true for the inside walls too");
      return;
    }

    // The roof as seen from under it, with its dormers and cross gables
    List<AssistantRoofGeometry.Cell> cells = geometry.getCeilingCells();
    // The pieces each wall is cut in: {t1, t2, height at t1, height at t2}
    final List<Wall> fittedWalls = new ArrayList<Wall>();
    final List<List<double []>> wallPieces = new ArrayList<List<double []>>();
    int skipped = 0;
    for (Wall wall : walls) {
      if (wall.getArcExtent() != null && wall.getArcExtent() != 0 || wall.getLength() < 1) {
        skipped++;
        continue;
      }
      float wallElevation = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
      float currentHeight = wall.getHeight() != null ? wall.getHeight() : this.home.getWallHeight();
      float currentHeightAtEnd = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : currentHeight;
      // Measured a hair toward the inside of the roof: a wall along the front of a dormer or of a
      // cross gable stands on the line where the roof changes, and goes up to the gable
      double [] roofCenter = AssistantRoofGeometry.getCenter(polygon);
      double nudgeX = -(wall.getYEnd() - wall.getYStart()) / wall.getLength() * 0.5;
      double nudgeY = (wall.getXEnd() - wall.getXStart()) / wall.getLength() * 0.5;
      if (nudgeX * (roofCenter [0] - wall.getXStart()) + nudgeY * (roofCenter [1] - wall.getYStart()) < 0) {
        nudgeX = -nudgeX;
        nudgeY = -nudgeY;
      }
      List<double []> profile = AssistantRoofGeometry.getProfile(wall.getXStart() + nudgeX, wall.getYStart() + nudgeY,
          wall.getXEnd() + nudgeX, wall.getYEnd() + nudgeY, cells);
      List<double []> pieces = new ArrayList<double []>();
      double covered = 0;
      for (double [] piece : profile) {
        if (piece [0] > covered + 0.01) {
          // Out of the roof, the wall keeps its height
          pieces.add(new double [] {covered, piece [0], currentHeight + (currentHeightAtEnd - currentHeight) * covered,
              currentHeight + (currentHeightAtEnd - currentHeight) * piece [0]});
        }
        if (piece [1] > covered + 1E-6) {
          double start = Math.max(covered, piece [0]);
          double height1 = piece [2] + (piece [3] - piece [2]) * (start - piece [0]) / (piece [1] - piece [0]) - piece [4];
          double height2 = piece [3] - piece [4];
          pieces.add(new double [] {start, piece [1], height1 + roofElevation - wallElevation,
              height2 + roofElevation - wallElevation});
          covered = piece [1];
        }
      }
      if (pieces.isEmpty()) {
        skipped++;
        continue;
      }
      if (covered < 0.99) {
        pieces.add(new double [] {covered, 1, currentHeight + (currentHeightAtEnd - currentHeight) * covered, currentHeightAtEnd});
      } else {
        pieces.get(pieces.size() - 1) [1] = 1;
      }
      pieces.get(0) [0] = 0;
      // Pieces in line make one piece, and a short piece joins the one before
      for (int i = pieces.size() - 1; i > 0; i--) {
        double [] previous = pieces.get(i - 1);
        double [] piece = pieces.get(i);
        double length = (piece [1] - piece [0]) * wall.getLength();
        double previousLength = (previous [1] - previous [0]) * wall.getLength();
        double slope = (piece [3] - piece [2]) / Math.max(length, 1E-6);
        double previousSlope = (previous [3] - previous [2]) / Math.max(previousLength, 1E-6);
        if (length < 3 || previousLength < 3
            || Math.abs(piece [2] - previous [3]) < 0.5 && Math.abs(slope - previousSlope) < 0.002) {
          previous [1] = piece [1];
          previous [3] = piece [3];
          pieces.remove(i);
        }
      }
      boolean valid = pieces.size() <= 24;
      for (double [] piece : pieces) {
        valid &= piece [2] >= 1 && piece [3] >= 1 && AssistantCommandExecutor.sizeSane(piece [2])
            && AssistantCommandExecutor.sizeSane(piece [3]);
      }
      if (!valid) {
        skipped++;
        continue;
      }
      fittedWalls.add(wall);
      wallPieces.add(pieces);
    }
    if (fittedWalls.isEmpty()) {
      notes.add("fit_walls_to_roof changed no wall: " + skipped + " round or uncovered walls were left as they are");
      return;
    }

    // The new walls continue the cut ones
    final List<Wall> newWalls = new ArrayList<Wall>();
    final List<Wall []> chains = new ArrayList<Wall []>();
    final List<Selectable> modified = new ArrayList<Selectable>();
    for (int i = 0; i < fittedWalls.size(); i++) {
      Wall wall = fittedWalls.get(i);
      List<double []> pieces = wallPieces.get(i);
      Wall [] chain = new Wall [pieces.size()];
      chain [0] = wall;
      for (int j = 1; j < pieces.size(); j++) {
        chain [j] = (Wall)wall.duplicate();
        chain [j].setProperty(HomeItemIds.ID_PROPERTY, null);
        chain [j].setLevel(wall.getLevel());
        newWalls.add(chain [j]);
      }
      chains.add(chain);
      modified.add(wall);
      if (wall.getWallAtEnd() != null && !modified.contains(wall.getWallAtEnd())) {
        modified.add(wall.getWallAtEnd());
      }
    }
    // The walls joined to a cut wall will be joined to its last piece
    for (Wall other : this.home.getWalls()) {
      if (!modified.contains(other)
          && (fittedWalls.contains(other.getWallAtStart()) || fittedWalls.contains(other.getWallAtEnd()))) {
        modified.add(other);
      }
    }
    final List<Wall> homeWalls = new ArrayList<Wall>(this.home.getWalls());
    this.executor.postModification(modified, new Runnable() {
        public void run() {
          for (int i = 0; i < fittedWalls.size(); i++) {
            Wall wall = fittedWalls.get(i);
            List<double []> pieces = wallPieces.get(i);
            Wall [] chain = chains.get(i);
            float xStart = wall.getXStart();
            float yStart = wall.getYStart();
            float xEnd = wall.getXEnd();
            float yEnd = wall.getYEnd();
            Wall wallAtEnd = wall.getWallAtEnd();
            // The walls that join the end of this wall, with the end they join it by
            List<Wall> joinedByStart = new ArrayList<Wall>();
            List<Wall> joinedByEnd = new ArrayList<Wall>();
            if (chain.length > 1) {
              for (Wall other : homeWalls) {
                if (other != wall) {
                  if (other.getWallAtStart() == wall
                      && Math.hypot(other.getXStart() - xEnd, other.getYStart() - yEnd) < 1) {
                    joinedByStart.add(other);
                  }
                  if (other.getWallAtEnd() == wall
                      && Math.hypot(other.getXEnd() - xEnd, other.getYEnd() - yEnd) < 1) {
                    joinedByEnd.add(other);
                  }
                }
              }
            }
            for (int j = 0; j < chain.length; j++) {
              double [] piece = pieces.get(j);
              Wall part = chain [j];
              part.setXStart((float)(xStart + (xEnd - xStart) * piece [0]));
              part.setYStart((float)(yStart + (yEnd - yStart) * piece [0]));
              part.setXEnd((float)(xStart + (xEnd - xStart) * piece [1]));
              part.setYEnd((float)(yStart + (yEnd - yStart) * piece [1]));
              part.setHeight((float)piece [2]);
              part.setHeightAtEnd(Math.abs(piece [3] - piece [2]) < 0.05 ? null : Float.valueOf((float)piece [3]));
              if (j > 0) {
                part.setWallAtStart(chain [j - 1]);
                chain [j - 1].setWallAtEnd(part);
              }
            }
            if (chain.length > 1) {
              Wall last = chain [chain.length - 1];
              for (Wall other : joinedByStart) {
                other.setWallAtStart(last);
              }
              for (Wall other : joinedByEnd) {
                other.setWallAtEnd(last);
              }
              last.setWallAtEnd(wallAtEnd);
            }
          }
        }
      });
    if (!newWalls.isEmpty()) {
      // New walls go to the selected level: each one is added at the level of the wall it continues
      Level selectedLevel = this.home.getSelectedLevel();
      List<Level> wallLevels = new ArrayList<Level>();
      for (Wall wall : newWalls) {
        if (!wallLevels.contains(wall.getLevel())) {
          wallLevels.add(wall.getLevel());
        }
      }
      for (Level wallLevel : wallLevels) {
        List<Wall> levelWalls = new ArrayList<Wall>();
        for (Wall wall : newWalls) {
          if (wall.getLevel() == wallLevel) {
            levelWalls.add(wall);
          }
        }
        if (wallLevel != null && wallLevel != this.home.getSelectedLevel()) {
          this.home.setSelectedLevel(wallLevel);
        }
        this.homeController.getPlanController().addWalls(levelWalls);
        for (Wall wall : levelWalls) {
          if (wall.getLevel() != wallLevel) {
            wall.setLevel(wallLevel);
          }
        }
      }
      if (this.home.getSelectedLevel() != selectedLevel && selectedLevel != null) {
        this.home.setSelectedLevel(selectedLevel);
      }
      tally [3] += newWalls.size();
    }
    tally [4] += fittedWalls.size();
    int sloped = 0;
    int cut = 0;
    StringBuilder ids = new StringBuilder();
    for (int i = 0; i < fittedWalls.size(); i++) {
      Wall [] chain = chains.get(i);
      cut += chain.length > 1 ? 1 : 0;
      for (Wall wall : chain) {
        sloped += wall.getHeightAtEnd() != null ? 1 : 0;
      }
      if (i < 12) {
        ids.append(ids.length() > 0 ? ", " : "").append(this.executor.getId(chain [0]));
        for (int j = 1; j < chain.length; j++) {
          ids.append('+').append(this.executor.getId(chain [j]));
        }
      }
    }
    notes.add("Fitted " + fittedWalls.size() + (fittedWalls.size() == 1 ? " wall" : " walls") + " to the underside of the roof "
        + this.executor.getId(roofPiece) + " (" + ids + (fittedWalls.size() > 12 ? "..." : "") + "): "
        + sloped + (sloped == 1 ? " wall has" : " walls have") + " a sloped top"
        + (cut > 0 ? ", " + cut + (cut == 1 ? " wall was" : " walls were") + " cut where the slope changes, as under the peak of a gable ("
            + newWalls.size() + (newWalls.size() == 1 ? " wall" : " walls") + " added)" : "")
        + (skipped > 0 ? "; " + skipped + " round or uncovered walls were left as they are" : "")
        + ". Walls under a dormer or a cross gable go up to its roof. The roof was built with gable walls of its own: build it with \"gable_walls\":false to show these walls instead");
  }

  private static double getDistanceToEdges(double [][] polygon, double x, double y) {
    double distance = Double.MAX_VALUE;
    for (int i = 0; i < polygon.length; i++) {
      double [] point = polygon [i];
      double [] next = polygon [(i + 1) % polygon.length];
      distance = Math.min(distance, java.awt.geom.Line2D.ptSegDist(point [0], point [1], next [0], next [1], x, y));
    }
    return distance;
  }

  /**
   * Returns the height above the elevation 0 of what someone stands on outdoors at a point of
   * the plan: the ground built by <code>set_terrain</code>, or the floor of a room that isn't much
   * higher, or 0.
   */
  float getStandingHeight(float x, float y) {
    Float terrain = getTerrainHeight(x, y);
    float height = terrain != null ? terrain : 0;
    Float floor = null;
    for (Room room : this.home.getRooms()) {
      float elevation = room.getLevel() != null ? room.getLevel().getElevation() : 0;
      // A terrace or a porch, not the floor of the storey above
      if (room.isFloorVisible() && elevation <= height + 150 && (floor == null || elevation < floor)
          && room.containsPoint(x, y, 0)) {
        floor = elevation;
      }
    }
    return floor != null ? Math.max(floor, terrain != null ? Math.min(terrain, floor) : floor) : height;
  }

  // ---- Furniture made of parts ----

  @SuppressWarnings("unchecked")
  private void modifyFurnitureParts(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture piece = null;
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture && piece == null) {
        piece = (HomePieceOfFurniture)item;
      }
    }
    if (piece == null) {
      throw new IOException("give the piece with \"id\":\"F#\"");
    }
    String partsProperty = piece.getProperty(PARTS_PROPERTY);
    Object parsed = partsProperty != null ? JSONParser.parse(partsProperty) : null;
    if (!(parsed instanceof List)) {
      throw new IOException(this.executor.getId(piece) + " \"" + piece.getName()
          + "\" wasn't built by create_furniture, or was built by a version that didn't keep its parts");
    }
    List<Object> parts = new ArrayList<Object>((List<Object>)parsed);
    Map<String, Object> parameters = command.getParameters();
    if (parameters.get("parts") instanceof List) {
      parts = new ArrayList<Object>((List<Object>)parameters.get("parts"));
    }
    int changed = 0;
    if (parameters.get("set") instanceof List) {
      for (Object item : (List<?>)parameters.get("set")) {
        if (!(item instanceof Map) || !(((Map<?, ?>)item).get("index") instanceof Number)) {
          throw new IOException("each item of \"set\" needs the \"index\" of a part (1 for the first one) and the values to change");
        }
        int index = ((Number)((Map<?, ?>)item).get("index")).intValue();
        if (index < 1 || index > parts.size() || !(parts.get(index - 1) instanceof Map)) {
          throw new IOException("there is no part " + index + ": the piece has " + parts.size() + " parts");
        }
        Map<String, Object> part = new LinkedHashMap<String, Object>((Map<String, Object>)parts.get(index - 1));
        for (Map.Entry<?, ?> entry : ((Map<?, ?>)item).entrySet()) {
          if (!"index".equals(entry.getKey())) {
            if (entry.getValue() == null) {
              part.remove(String.valueOf(entry.getKey()));
            } else {
              part.put(String.valueOf(entry.getKey()), entry.getValue());
            }
          }
        }
        parts.set(index - 1, part);
        changed++;
      }
    }
    int removed = 0;
    if (parameters.get("remove") instanceof List) {
      List<Integer> indices = new ArrayList<Integer>();
      for (Object index : (List<?>)parameters.get("remove")) {
        if (!(index instanceof Number) || ((Number)index).intValue() < 1 || ((Number)index).intValue() > parts.size()) {
          throw new IOException("\"remove\" lists the indices of parts between 1 and " + parts.size());
        }
        if (!indices.contains(((Number)index).intValue())) {
          indices.add(((Number)index).intValue());
        }
      }
      Collections.sort(indices);
      for (int i = indices.size() - 1; i >= 0; i--) {
        parts.remove(indices.get(i) - 1);
        removed++;
      }
    }
    int added = 0;
    if (parameters.get("add") instanceof List) {
      for (Object part : (List<?>)parameters.get("add")) {
        if (part instanceof Map) {
          parts.add(part);
          added++;
        }
      }
    }
    if (changed + removed + added == 0 && !(parameters.get("parts") instanceof List)) {
      throw new IOException("give \"set\":[{\"index\":1,...}], \"add\":[{...}], \"remove\":[2] or all the \"parts\"");
    }
    List<String> partNotes = new ArrayList<String>();
    AssistantGeneratedModel model = AssistantFurnitureBuilder.build(parts, partNotes, getTextureFinder());
    float [] bounds = model.getBounds();
    // The parts that didn't change stay at their place in the home
    float x = piece.getX();
    float y = piece.getY();
    float elevation = piece.getElevation();
    String [] oldBounds = piece.getProperty(PARTS_BOUNDS_PROPERTY) != null
        ? piece.getProperty(PARTS_BOUNDS_PROPERTY).split(",") : new String [0];
    if (oldBounds.length == 6) {
      try {
        double dx = (bounds [0] + bounds [3]) / 2 - (Float.parseFloat(oldBounds [0]) + Float.parseFloat(oldBounds [3])) / 2;
        double dy = (bounds [2] + bounds [5]) / 2 - (Float.parseFloat(oldBounds [2]) + Float.parseFloat(oldBounds [5])) / 2;
        if (piece.isModelMirrored()) {
          dx = -dx;
        }
        double cos = Math.cos(piece.getAngle());
        double sin = Math.sin(piece.getAngle());
        x += (float)(dx * cos - dy * sin);
        y += (float)(dx * sin + dy * cos);
        elevation += bounds [1] - Float.parseFloat(oldBounds [1]);
      } catch (NumberFormatException ex) {
        // Keep the place of the piece
      }
    }
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put(PARTS_PROPERTY, toJson(parts));
    properties.put(PARTS_BOUNDS_PROPERTY, formatBounds(bounds));
    replaceModel(piece, model, x, y, elevation, properties);
    tally [4]++;
    StringBuilder note = new StringBuilder("Rebuilt " + this.executor.getId(piece) + " \"" + piece.getName() + "\" with "
        + parts.size() + (parts.size() == 1 ? " part" : " parts") + " (" + changed + " changed, " + added + " added, "
        + removed + " removed): " + format(model.getWidth()) + " x " + format(model.getDepth()) + " x "
        + format(model.getHeight()) + " cm (width x depth x height)");
    for (String partNote : partNotes) {
      note.append("; ").append(partNote);
    }
    notes.add(note.toString());
  }

  static String formatBounds(float [] bounds) {
    StringBuilder text = new StringBuilder();
    for (float bound : bounds) {
      text.append(text.length() > 0 ? "," : "").append(bound);
    }
    return text.toString();
  }

  // ---- JSON ----

  /**
   * Returns the JSON text of maps, lists, strings, numbers and booleans.
   */
  static String toJson(Object value) {
    StringBuilder json = new StringBuilder();
    writeJson(json, value);
    return json.toString();
  }

  private static void writeJson(StringBuilder json, Object value) {
    if (value instanceof Map) {
      json.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)value).entrySet()) {
        if (!first) {
          json.append(',');
        }
        first = false;
        writeJson(json, String.valueOf(entry.getKey()));
        json.append(':');
        writeJson(json, entry.getValue());
      }
      json.append('}');
    } else if (value instanceof List) {
      json.append('[');
      boolean first = true;
      for (Object item : (List<?>)value) {
        if (!first) {
          json.append(',');
        }
        first = false;
        writeJson(json, item);
      }
      json.append(']');
    } else if (value instanceof Number) {
      double number = ((Number)value).doubleValue();
      if (Double.isNaN(number) || Double.isInfinite(number)) {
        json.append("null");
      } else if (number == Math.rint(number) && Math.abs(number) < 1E15) {
        json.append((long)number);
      } else {
        json.append(String.format(Locale.US, "%.4f", number).replaceAll("0+$", "").replaceAll("\\.$", ""));
      }
    } else if (value instanceof Boolean) {
      json.append(value);
    } else if (value == null) {
      json.append("null");
    } else {
      json.append('"');
      String text = value.toString();
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '"' || c == '\\') {
          json.append('\\').append(c);
        } else if (c == '\n') {
          json.append("\\n");
        } else if (c == '\r') {
          json.append("\\r");
        } else if (c == '\t') {
          json.append("\\t");
        } else if (c < 0x20 || c > 0x7E) {
          json.append(String.format("\\u%04x", (int)c));
        } else {
          json.append(c);
        }
      }
      json.append('"');
    }
  }
}
