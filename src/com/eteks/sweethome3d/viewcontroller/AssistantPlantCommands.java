/*
 * AssistantPlantCommands.java 23 nov. 2026
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands that plant trees and shrubs built from a species, one by one or by lists,
 * and change them all at once: their season, their growth, their species.
 */
class AssistantPlantCommands {
  /** The property of a piece that keeps the description of the plant it shows, in JSON. */
  static final String PLANT_PROPERTY = "assistantPlant";
  /** The property of a home that keeps the season its plants show. */
  static final String SEASON_PROPERTY = "assistantSeason";
  /** The property of a home that keeps the level of detail of its new plants. */
  static final String LOD_PROPERTY = "assistantPlantLod";

  private static final int          MAX_PLANTS = 3000;
  private static final long         MAX_FILE_LENGTH = 20L * 1024 * 1024;
  private static final List<String> SEASONS = Arrays.asList("spring", "summer", "autumn", "winter");
  private static final List<String> TREE_KEYS = Arrays.asList("action", "species", "x", "y", "height", "crown_radius", "crown_w",
      "crown_d", "crown_base", "trunk_dbh", "trunk_lean", "multi_trunk", "season", "lod", "seed", "name", "tags", "tag", "level",
      "pruned_shape", "angle", "offset", "removed_since", "lat", "lon", "id");

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;
  private final Map<String, Object []>   models = new LinkedHashMap<String, Object []>();

  AssistantPlantCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":N,\"y\":N,\"height\":N}  "
        + "(a tree built from its species, standing on the ground: a trunk, branches and masses of leaves with the silhouette of the "
        + "species. Sizes in cm, the usual ones of the species by default: \"height\", \"crown_radius\" (or \"crown_w\" and \"crown_d\"), "
        + "\"crown_base\" (the height under the crown), \"trunk_dbh\" (the diameter of the trunk), "
        + "\"trunk_lean\":{\"angle\":N,\"dir\":N} (degrees from upright, towards a compass direction of the plan: 0 = up the plan), "
        + "\"multi_trunk\":N, \"season\":\"spring|summer|autumn|winter\" (the season of the home by default: a deciduous tree is bare in "
        + "winter, colored in autumn, in flower in spring if it blooms), \"lod\":\"low|medium|high\" (at most 2000, 10000 and more "
        + "triangles), \"seed\":N for another shape of the same species, \"name\", \"tags\", \"level\". "
        + "{\"action\":\"list_species\"} gives the species: " + AssistantPlantBuilder.describeSpecies(false, false) + ")\n"
        + "  {\"action\":\"add_shrub\",\"species\":\"boxwood\",\"x\":N,\"y\":N,\"pruned_shape\":\"natural|ball|box|cone|hedge_section\"}  "
        + "(a shrub, an ornamental grass or a groundcover plant, with the keys of add_tree. The species: "
        + AssistantPlantBuilder.describeSpecies(true, false) + ")\n"
        + "  {\"action\":\"add_trees\",\"trees\":[{\"species\":\"loblolly_pine\",\"x\":N,\"y\":N,\"height\":N,\"crown_radius\":N,\"tag\":\"lidar\"},...]}  "
        + "(many trees at once, each with its own keys of add_tree, set on the ground in one step of undo; keys given beside "
        + "\"trees\" are the ones of every tree that doesn't give them. With \"species\" instead of \"names\", add_trees and forest_fill "
        + "scatter generated trees in an \"area\" or \"along\" a line)\n"
        + "  {\"action\":\"import_trees\",\"path\":\"<absolute path of a CSV or GeoJSON file>\"}  "
        + "(or \"csv\":\"<text>\" / \"geojson\":{...}: trees listed with x, y in cm of the plan, or lat, lon with the georeference of the "
        + "home, and height, crown_radius, species, tag. \"units\":\"m|ft|cm\" for the sizes of the file (cm), \"species\" for the trees "
        + "without one, \"tag\" for all)\n"
        + "  {\"action\":\"update_trees\",\"tag\":\"lidar\",\"trees\":[...]}  "
        + "(compares a new list with the trees that have a tag: a tree of the list with no tree within \"tolerance\" cm (150) is added, "
        + "a tree of the home that isn't in the list anymore is deleted, or with \"removed_since\":\"2018\" kept as a pale ghost with "
        + "that mark. The answer counts what was added, removed and kept)\n"
        + "  {\"action\":\"relabel_trees\",\"where\":{\"tag\":\"lidar\",\"bbox\":[x1,y1,x2,y2]},\"species\":\"white_oak\"}  "
        + "(gives another species to generated trees, at the same place and with the same sizes)\n"
        + "  {\"action\":\"grow_trees\",\"years\":8}  "
        + "(makes the generated trees older, or younger with a negative number: each one grows at the rate of its species, up to the "
        + "size it reaches; \"where\" or \"ids\" limits it)\n"
        + "  {\"action\":\"set_season\",\"season\":\"autumn\"}  "
        + "(the season of every generated plant of the home, and of the ones added later; or \"date\":\"2026-10-20\", read with the "
        + "hemisphere of the home. \"lod\":\"low|medium|high\" changes their detail the same way)\n"
        + "  {\"action\":\"forest_fill\",\"area\":[[x,y],...],\"species\":{\"loblolly_pine\":3,\"sweetgum\":1},\"density\":4}  "
        + "(fills a polygon with trees: \"density\" trees per 100 m2, or \"spacing\" in cm; \"species\" is a name, a list, or weights; "
        + "\"understory\":{\"flowering_dogwood\":1,\"azalea\":2} adds smaller plants between them; heights vary by \"variation\" (0.25); "
        + "the same \"seed\" gives the same forest. Places in a room or near a building are skipped)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("add_tree".equals(action) || "add_shrub".equals(action)) {
        Map<String, Object> spec = new LinkedHashMap<String, Object>(command.getParameters());
        List<String> ignored = new ArrayList<String>();
        HomePieceOfFurniture piece = addPlant(spec, "add_shrub".equals(action), getLevel(command, action, notes), tally, ignored);
        notes.add("Added the " + describe(piece));
        warnIgnored(action, ignored, notes);
      } else if ("add_trees".equals(action) && command.getParameters().get("trees") instanceof List) {
        addPlants(action, command, (List<?>)command.getParameters().get("trees"), tally, notes);
      } else if (("add_trees".equals(action) || "scatter".equals(action)) && command.has("species") && !command.has("names")
                 && !command.has("name")) {
        forestFill(action, command, tally, notes);
      } else if ("forest_fill".equals(action)) {
        forestFill(action, command, tally, notes);
      } else if ("import_trees".equals(action)) {
        importTrees(command, tally, notes);
      } else if ("update_trees".equals(action)) {
        updateTrees(command, tally, notes);
      } else if ("relabel_trees".equals(action)) {
        relabelTrees(command, tally, notes);
      } else if ("grow_trees".equals(action)) {
        growTrees(command, tally, notes);
      } else if ("set_season".equals(action) || "set_date".equals(action) && command.has("date") && !command.has("time")) {
        setSeason(command, tally, notes);
      } else if ("replace_furniture".equals(action) && command.has("species")) {
        replaceWithPlants(command, tally, notes);
      } else if ("list_species".equals(action)) {
        notes.add("Species of trees (name, common name, evergreen or deciduous, usual height).\n"
            + AssistantPlantBuilder.describeSpecies(false, true) + "\nSpecies of shrubs, grasses and groundcovers.\n"
            + AssistantPlantBuilder.describeSpecies(true, true));
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

  private Level getLevel(AssistantCommand command, String action, List<String> notes) throws IOException {
    return this.executor.getShapeCommands().getLevel(command, action, notes);
  }

  private static void warnIgnored(String action, List<String> ignored, List<String> notes) {
    if (!ignored.isEmpty()) {
      List<String> keys = new ArrayList<String>(TREE_KEYS);
      keys.removeAll(Arrays.asList("action", "id", "lat", "lon", "removed_since"));
      notes.add("Warning: " + action + " ignored " + (ignored.size() == 1 ? "the key " : "the keys ") + ignored + ". Its keys: " + keys);
    }
  }

  /**
   * Returns the description of the plant a piece shows, or <code>null</code> if it isn't one.
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> getSpec(HomePieceOfFurniture piece) {
    String property = piece.getProperty(PLANT_PROPERTY);
    if (property == null) {
      return null;
    }
    try {
      Object parsed = JSONParser.parse(property);
      return parsed instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>)parsed) : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static float number(Map<?, ?> map, String key, float defaultValue) {
    return AssistantFurnitureBuilder.getNumber(map, key, defaultValue);
  }

  private String getSeason() {
    String season = this.home.getProperty(SEASON_PROPERTY);
    return season != null && SEASONS.contains(season) ? season : "summer";
  }

  /**
   * Returns the plant a description gives, with the sizes of its species for the ones it doesn't give.
   */
  private AssistantPlantBuilder.Plant getPlant(Map<String, Object> spec, boolean shrub) throws IOException {
    Object speciesName = spec.get("species");
    AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(
        speciesName != null ? speciesName.toString() : shrub ? "boxwood" : "generic_deciduous");
    if (species == null) {
      throw new IOException("unknown \"species\" \"" + speciesName + "\". {\"action\":\"list_species\"} gives the species; "
          + "generic_deciduous, generic_conifer and generic_broadleaf_evergreen stand for any tree");
    }
    AssistantPlantBuilder.Plant plant = new AssistantPlantBuilder.Plant();
    plant.species = species;
    plant.height = number(spec, "height", species.height);
    if (!(plant.height >= 5 && plant.height <= 12000)) {
      throw new IOException("\"height\" goes from 5 to 12000 cm");
    }
    float usualWidth = plant.height * species.crownRatio;
    float radius = number(spec, "crown_radius", Float.NaN);
    plant.crownWidth = number(spec, "crown_w", !Float.isNaN(radius) ? radius * 2 : usualWidth);
    plant.crownDepth = number(spec, "crown_d", !Float.isNaN(radius) ? radius * 2 : plant.crownWidth);
    if (!(plant.crownWidth >= 3 && plant.crownWidth <= 6000) || !(plant.crownDepth >= 3 && plant.crownDepth <= 6000)) {
      throw new IOException("the crown is 3 to 6000 cm wide: \"crown_radius\", or \"crown_w\" and \"crown_d\"");
    }
    plant.crownBase = Math.max(0, Math.min(plant.height * 0.92f, number(spec, "crown_base", plant.height * species.baseRatio)));
    plant.trunkDiameter = Math.max(1, Math.min(600, number(spec, "trunk_dbh", plant.height * species.trunkRatio)));
    if (spec.get("trunk_lean") instanceof Map) {
      Map<?, ?> lean = (Map<?, ?>)spec.get("trunk_lean");
      plant.leanAngle = Math.max(0, Math.min(45, number(lean, "angle", 0)));
      plant.leanDirection = number(lean, "dir", number(lean, "direction", 0));
    } else if (spec.get("trunk_lean") instanceof Number) {
      plant.leanAngle = Math.max(0, Math.min(45, ((Number)spec.get("trunk_lean")).floatValue()));
    }
    plant.trunks = Math.max(1, Math.min(8, Math.round(number(spec, "multi_trunk", species.trunks))));
    String season = spec.get("season") != null ? spec.get("season").toString().trim().toLowerCase(Locale.ENGLISH) : getSeason();
    if (season.equals("fall")) {
      season = "autumn";
    }
    if (!SEASONS.contains(season)) {
      throw new IOException("\"season\" is spring, summer, autumn or winter");
    }
    plant.season = season;
    String lod = spec.get("lod") != null ? spec.get("lod").toString().trim().toLowerCase(Locale.ENGLISH)
        : this.home.getProperty(LOD_PROPERTY) != null ? this.home.getProperty(LOD_PROPERTY) : "medium";
    if (!lod.equals("low") && !lod.equals("medium") && !lod.equals("high")) {
      throw new IOException("\"lod\" is low, medium or high");
    }
    plant.lod = lod;
    String pruned = spec.get("pruned_shape") != null ? spec.get("pruned_shape").toString().trim().toLowerCase(Locale.ENGLISH) : "natural";
    if (!Arrays.asList("natural", "ball", "box", "cone", "hedge_section").contains(pruned)) {
      throw new IOException("\"pruned_shape\" is natural, ball, box, cone or hedge_section");
    }
    plant.prunedShape = pruned;
    plant.seed = (long)number(spec, "seed", 0);
    plant.ghost = spec.get("removed_since") != null;
    return plant;
  }

  /**
   * Returns the model of a plant and the sizes its piece has: {model, width, depth, height, x offset, y offset}.
   * Plants of the same species with close proportions share a model, scaled to the size of each.
   */
  private Object [] getModel(AssistantPlantBuilder.Plant plant) throws IOException {
    // Built 10 m high, with proportions rounded
    float nominal = 1000;
    float widthRatio = Math.max(0.05f, Math.round(plant.crownWidth / plant.height * 20) / 20f);
    float depthRatio = Math.max(0.05f, Math.round(plant.crownDepth / plant.height * 20) / 20f);
    float baseRatio = Math.round(plant.crownBase / plant.height * 20) / 20f;
    float trunkRatio = Math.max(0.005f, Math.round(plant.trunkDiameter / plant.height * 200) / 200f);
    String key = plant.species.name + "|" + plant.season + "|" + plant.lod + "|" + plant.seed + "|" + plant.trunks + "|"
        + Math.round(plant.leanAngle) + "|" + Math.round(plant.leanDirection) + "|" + plant.prunedShape + "|" + plant.ghost + "|"
        + widthRatio + "|" + depthRatio + "|" + baseRatio + "|" + trunkRatio;
    Object [] cached = this.models.get(key);
    if (cached == null) {
      AssistantPlantBuilder.Plant shape = new AssistantPlantBuilder.Plant();
      shape.species = plant.species;
      shape.height = nominal;
      shape.crownWidth = nominal * widthRatio;
      shape.crownDepth = nominal * depthRatio;
      shape.crownBase = nominal * baseRatio;
      shape.trunkDiameter = nominal * trunkRatio;
      shape.leanAngle = Math.round(plant.leanAngle);
      shape.leanDirection = Math.round(plant.leanDirection);
      shape.trunks = plant.trunks;
      shape.season = plant.season;
      shape.lod = plant.lod;
      shape.prunedShape = plant.prunedShape;
      shape.seed = plant.seed;
      shape.ghost = plant.ghost;
      AssistantGeneratedModel model = AssistantPlantBuilder.build(shape).store("plant");
      if (this.models.size() > 400) {
        this.models.clear();
      }
      cached = new Object [] {model, Float.valueOf(nominal * widthRatio), Float.valueOf(nominal * depthRatio)};
      this.models.put(key, cached);
    }
    AssistantGeneratedModel model = (AssistantGeneratedModel)cached [0];
    float [] bounds = model.getBounds();
    float scaleX = plant.crownWidth / ((Float)cached [1]).floatValue();
    float scaleZ = plant.crownDepth / ((Float)cached [2]).floatValue();
    float scaleY = plant.height / nominal;
    return new Object [] {model, Float.valueOf(model.getWidth() * scaleX), Float.valueOf(model.getDepth() * scaleZ),
        Float.valueOf(model.getHeight() * scaleY), Float.valueOf((bounds [0] + bounds [3]) / 2 * scaleX),
        Float.valueOf((bounds [2] + bounds [5]) / 2 * scaleZ)};
  }

  /**
   * Returns the description kept with a plant: what the command gave and the sizes it got.
   */
  private static Map<String, Object> getStoredSpec(Map<String, Object> spec, AssistantPlantBuilder.Plant plant, float x, float y) {
    Map<String, Object> stored = new LinkedHashMap<String, Object>();
    stored.put("species", plant.species.name);
    stored.put("x", Double.valueOf(Math.round(x * 10) / 10.));
    stored.put("y", Double.valueOf(Math.round(y * 10) / 10.));
    stored.put("height", Double.valueOf(Math.round(plant.height)));
    stored.put("crown_w", Double.valueOf(Math.round(plant.crownWidth)));
    stored.put("crown_d", Double.valueOf(Math.round(plant.crownDepth)));
    stored.put("crown_base", Double.valueOf(Math.round(plant.crownBase)));
    stored.put("trunk_dbh", Double.valueOf(Math.round(plant.trunkDiameter * 10) / 10.));
    if (plant.leanAngle != 0) {
      Map<String, Object> lean = new LinkedHashMap<String, Object>();
      lean.put("angle", Double.valueOf(plant.leanAngle));
      lean.put("dir", Double.valueOf(plant.leanDirection));
      stored.put("trunk_lean", lean);
    }
    stored.put("multi_trunk", Double.valueOf(plant.trunks));
    // The season and the detail of the home, unless the plant was given its own
    for (String key : new String [] {"season", "lod", "pruned_shape", "removed_since", "offset"}) {
      if (spec.get(key) != null) {
        stored.put(key, spec.get(key));
      }
    }
    stored.put("seed", Double.valueOf(plant.seed));
    return stored;
  }

  private float getGround(float x, float y, Level level) {
    return this.executor.getShapeCommands().getStandingHeight(x, y) - (level != null ? level.getElevation() : 0);
  }

  /**
   * Creates the piece that shows a plant, without adding it to the home.
   */
  private HomePieceOfFurniture createPlant(Map<String, Object> spec, boolean shrub, Level level, List<String> ignored) throws IOException {
    for (String key : spec.keySet()) {
      if (!TREE_KEYS.contains(key) && !ignored.contains(key)) {
        ignored.add(key);
      }
    }
    float x = number(spec, "x", Float.NaN);
    float y = number(spec, "y", Float.NaN);
    if (Float.isNaN(x) || Float.isNaN(y)) {
      if (spec.get("lat") instanceof Number && spec.get("lon") instanceof Number) {
        HomeGeoreference georeference = HomeGeoreference.get(this.home);
        if (georeference == null) {
          throw new IOException("\"lat\" and \"lon\" need the georeference of the home: set_georeference gives it");
        }
        double [] point = georeference.toPlan(((Number)spec.get("lat")).doubleValue(), ((Number)spec.get("lon")).doubleValue());
        x = (float)point [0];
        y = (float)point [1];
      } else {
        throw new IOException("give the place of the plant: \"x\" and \"y\" in cm");
      }
    }
    if (!AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)) {
      throw new IOException("\"x\" and \"y\" are out of range");
    }
    if (spec.get("seed") == null) {
      // The same place gives the same tree; a few shapes by species keep the home light
      spec.put("seed", Double.valueOf(Math.abs((Math.round(x) * 31 + Math.round(y) * 17) % 5)));
    }
    AssistantPlantBuilder.Plant plant = getPlant(spec, shrub);
    Object [] model = getModel(plant);
    String name = spec.get("name") != null && spec.get("name").toString().trim().length() > 0
        ? spec.get("name").toString().trim() : plant.species.commonName;
    HomePieceOfFurniture piece = this.executor.getShapeCommands().createPiece(name, (AssistantGeneratedModel)model [0], level, true, null);
    piece.setWidth(((Float)model [1]).floatValue());
    piece.setDepth(((Float)model [2]).floatValue());
    piece.setHeight(((Float)model [3]).floatValue());
    piece.setX(x + ((Float)model [4]).floatValue());
    piece.setY(y + ((Float)model [5]).floatValue());
    piece.setElevation(getGround(x, y, level) + number(spec, "offset", 0));
    piece.setProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY, "true");
    piece.setProperty(PLANT_PROPERTY, AssistantShapeCommands.toJson(getStoredSpec(spec, plant, x, y)));
    Set<String> tags = new LinkedHashSet<String>();
    tags.add(plant.species.shrub ? "shrub" : "tree");
    tags.add(plant.species.name);
    for (String key : new String [] {"tags", "tag"}) {
      Object value = spec.get(key);
      if (value instanceof List) {
        for (Object tag : (List<?>)value) {
          tags.addAll(HomeItemTags.parseTags(String.valueOf(tag)));
        }
      } else if (value != null) {
        tags.addAll(HomeItemTags.parseTags(value.toString()));
      }
    }
    if (plant.ghost) {
      tags.add("removed");
    }
    piece.setProperty(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(tags));
    return piece;
  }

  private HomePieceOfFurniture addPlant(Map<String, Object> spec, boolean shrub, Level level, int [] tally,
                                        List<String> ignored) throws IOException {
    HomePieceOfFurniture piece = createPlant(spec, shrub, level, ignored);
    add(Collections.singletonList(piece), level);
    tally [0]++;
    return piece;
  }

  /**
   * Adds pieces to the home as one undoable step.
   */
  private void add(List<HomePieceOfFurniture> pieces, Level level) {
    if (pieces.isEmpty()) {
      return;
    }
    this.executor.beginTurnUpdate();
    Level selectedLevel = this.home.getSelectedLevel();
    if (level != null && level != selectedLevel) {
      this.home.setSelectedLevel(level);
    }
    this.homeController.getFurnitureController().addFurniture(pieces);
    if (this.home.getSelectedLevel() != selectedLevel && selectedLevel != null) {
      this.home.setSelectedLevel(selectedLevel);
    }
  }

  private String describe(HomePieceOfFurniture piece) {
    Map<String, Object> spec = getSpec(piece);
    AssistantPlantBuilder.Species species = spec != null ? AssistantPlantBuilder.getSpecies(String.valueOf(spec.get("species"))) : null;
    if (species == null) {
      return this.executor.getId(piece) + " \"" + piece.getName() + "\"";
    }
    return (species.shrub ? "shrub " : "tree ") + this.executor.getId(piece) + " \"" + piece.getName() + "\" (" + species.name + ", "
        + (species.evergreen ? "evergreen" : "deciduous") + "): " + format(number(spec, "height", 0) / 100) + " m high, crown "
        + format(number(spec, "crown_w", 0) / 100) + " m wide from " + format(number(spec, "crown_base", 0) / 100)
        + " m up, trunk " + format(number(spec, "trunk_dbh", 0)) + " cm, at (" + format(number(spec, "x", 0)) + ", "
        + format(number(spec, "y", 0)) + "), its foot at " + format(piece.getElevation()) + " cm"
        + (spec.get("removed_since") != null ? ", removed since " + spec.get("removed_since") : "");
  }

  /**
   * Returns the description of a plant for <code>describe_item</code>, or <code>null</code> if <code>piece</code> isn't one.
   */
  String describePlant(HomePieceOfFurniture piece) {
    return getSpec(piece) != null ? "generated " + describe(piece) : null;
  }

  @SuppressWarnings("unchecked")
  private void addPlants(String action, AssistantCommand command, List<?> trees, int [] tally, List<String> notes) throws IOException {
    if (trees.isEmpty()) {
      throw new IOException("\"trees\" is empty");
    } else if (trees.size() > MAX_PLANTS) {
      throw new IOException("\"trees\" has " + MAX_PLANTS + " trees at most: send the others in another call");
    }
    Level level = getLevel(command, action, notes);
    Map<String, Object> shared = new LinkedHashMap<String, Object>(command.getParameters());
    for (String key : new String [] {"action", "trees", "level", "x", "y", "name"}) {
      shared.remove(key);
    }
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    List<String> ignored = new ArrayList<String>();
    List<String> failures = new ArrayList<String>();
    Map<String, Integer> counts = new TreeMap<String, Integer>();
    for (int i = 0; i < trees.size(); i++) {
      if (!(trees.get(i) instanceof Map)) {
        failures.add((i + 1) + " isn't an object");
        continue;
      }
      Map<String, Object> spec = new LinkedHashMap<String, Object>(shared);
      spec.putAll((Map<String, Object>)trees.get(i));
      // The tags of the list and the ones of the tree
      List<Object> tags = new ArrayList<Object>();
      for (Map<?, ?> source : Arrays.asList(shared, (Map<?, ?>)trees.get(i))) {
        for (String key : new String [] {"tag", "tags"}) {
          if (source.get(key) instanceof List) {
            tags.addAll((List<?>)source.get(key));
          } else if (source.get(key) != null) {
            tags.add(source.get(key));
          }
        }
      }
      spec.remove("tag");
      if (!tags.isEmpty()) {
        spec.put("tags", tags);
      }
      try {
        AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(
            spec.get("species") != null ? spec.get("species").toString() : "generic_deciduous");
        HomePieceOfFurniture piece = createPlant(spec, species != null && species.shrub, level, ignored);
        pieces.add(piece);
        String name = getSpec(piece).get("species").toString();
        counts.put(name, Integer.valueOf(counts.containsKey(name) ? counts.get(name).intValue() + 1 : 1));
      } catch (IOException ex) {
        if (failures.size() < 10) {
          failures.add((i + 1) + ": " + ex.getMessage());
        }
      }
    }
    add(pieces, level);
    tally [0] += pieces.size();
    notes.add("Added " + pieces.size() + (pieces.size() == 1 ? " tree" : " trees") + " on the ground: " + describeCounts(counts)
        + describeIds(pieces));
    if (!failures.isEmpty()) {
      notes.add("Couldn't add " + (trees.size() - pieces.size()) + " of the " + trees.size() + " trees. " + failures);
    }
    warnIgnored(action, ignored, notes);
  }

  private static String describeCounts(Map<String, Integer> counts) {
    StringBuilder text = new StringBuilder();
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      text.append(text.length() > 0 ? ", " : "").append(entry.getValue()).append(' ').append(entry.getKey());
    }
    return text.toString();
  }

  private String describeIds(List<HomePieceOfFurniture> pieces) {
    if (pieces.isEmpty()) {
      return "";
    }
    return ". Ids " + this.executor.getId(pieces.get(0))
        + (pieces.size() > 1 ? " to " + this.executor.getId(pieces.get(pieces.size() - 1)) : "");
  }

  /**
   * Returns the species a parameter gives, each one as many times as its weight.
   */
  private static List<AssistantPlantBuilder.Species> getMix(Object value, String parameter) throws IOException {
    List<AssistantPlantBuilder.Species> mix = new ArrayList<AssistantPlantBuilder.Species>();
    Map<String, Number> weights = new LinkedHashMap<String, Number>();
    if (value instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)value).entrySet()) {
        weights.put(String.valueOf(entry.getKey()), entry.getValue() instanceof Number ? (Number)entry.getValue() : Integer.valueOf(1));
      }
    } else if (value instanceof List) {
      for (Object name : (List<?>)value) {
        weights.put(String.valueOf(name), Integer.valueOf(1));
      }
    } else if (value != null) {
      weights.put(value.toString(), Integer.valueOf(1));
    }
    for (Map.Entry<String, Number> entry : weights.entrySet()) {
      AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(entry.getKey());
      if (species == null) {
        throw new IOException("unknown species \"" + entry.getKey() + "\" in \"" + parameter + "\": {\"action\":\"list_species\"} gives the species");
      }
      for (int i = 0; i < Math.max(1, Math.min(50, Math.round(entry.getValue().floatValue()))); i++) {
        mix.add(species);
      }
    }
    return mix;
  }

  private void forestFill(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<float []> area = command.getPoints("area");
    List<float []> line = command.getPoints("along");
    if (area.size() < 3 && line.size() < 2) {
      throw new IOException("give the polygon \"area\":[[x,y],[x,y],[x,y],...], or the line \"along\":[[x,y],[x,y],...]");
    }
    List<AssistantPlantBuilder.Species> mix = getMix(command.getParameters().get("species"), "species");
    if (mix.isEmpty()) {
      mix.add(AssistantPlantBuilder.getSpecies("generic_deciduous"));
    }
    List<AssistantPlantBuilder.Species> understory = getMix(command.getParameters().get("understory"), "understory");
    double density = command.getDouble("density", Double.NaN);
    double spacing = command.getDouble("spacing", !Double.isNaN(density) && density > 0 ? Math.sqrt(1000000 / density) : 500);
    if (!(spacing >= 30 && spacing <= 10000)) {
      throw new IOException("\"spacing\" goes from 30 to 10000 cm (or \"density\" from 0.01 to 1000 trees per 100 m2)");
    }
    float variation = (float)Math.max(0, Math.min(0.8, command.getDouble("variation", 0.25)));
    float margin = (float)command.getDouble("min_distance_from_buildings", 150);
    long seed = (long)command.getDouble("seed", 1);
    Random random = new Random(seed * 1000003 + Math.round(spacing));
    float [][] polygon = area.toArray(new float [area.size()][]);
    // Places on a grid moved at random
    List<float []> places = new ArrayList<float []>();
    if (area.size() >= 3) {
      float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      for (float [] point : polygon) {
        bounds [0] = Math.min(bounds [0], point [0]);
        bounds [1] = Math.min(bounds [1], point [1]);
        bounds [2] = Math.max(bounds [2], point [0]);
        bounds [3] = Math.max(bounds [3], point [1]);
      }
      for (float y = bounds [1] + (float)spacing / 2; y < bounds [3]; y += spacing) {
        for (float x = bounds [0] + (float)spacing / 2; x < bounds [2]; x += spacing) {
          float px = x + (random.nextFloat() - 0.5f) * (float)spacing * 0.8f;
          float py = y + (random.nextFloat() - 0.5f) * (float)spacing * 0.8f;
          if (AssistantRoomFurnisher.contains(polygon, px, py)) {
            places.add(new float [] {px, py});
          }
        }
      }
    } else {
      float [][] path = line.toArray(new float [line.size()][]);
      double [] distances = AssistantFenceBuilder.getDistances(path, false);
      double length = distances [distances.length - 1];
      int count = !Double.isNaN(command.getDouble("count", Double.NaN)) ? (int)command.getDouble("count", 1)
          : Math.max(1, (int)Math.round(length / spacing) + 1);
      for (int i = 0; i < count; i++) {
        places.add(AssistantFenceBuilder.getPoint(path, distances, count == 1 ? length / 2 : length * i / (count - 1)));
      }
    }
    if (places.size() > MAX_PLANTS) {
      throw new IOException(places.size() + " trees is more than " + MAX_PLANTS + ": a larger \"spacing\", or a smaller \"density\"");
    }
    Level level = getLevel(command, action, notes);
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    List<String> ignored = new ArrayList<String>();
    Map<String, Integer> counts = new TreeMap<String, Integer>();
    int skipped = 0;
    List<float []> kept = new ArrayList<float []>();
    for (float [] place : places) {
      if (isNearBuilding(place [0], place [1], margin)) {
        skipped++;
        continue;
      }
      kept.add(place);
      AssistantPlantBuilder.Species species = mix.get(random.nextInt(mix.size()));
      pieces.add(createScattered(command, species, place, random, variation, level, ignored, counts));
    }
    if (!understory.isEmpty()) {
      // Smaller plants between the trees
      for (float [] place : kept) {
        float px = place [0] + (float)spacing * (0.3f + 0.3f * random.nextFloat()) * (random.nextBoolean() ? 1 : -1);
        float py = place [1] + (float)spacing * (0.3f + 0.3f * random.nextFloat()) * (random.nextBoolean() ? 1 : -1);
        if ((area.size() < 3 || AssistantRoomFurnisher.contains(polygon, px, py)) && !isNearBuilding(px, py, margin)
            && pieces.size() < MAX_PLANTS) {
          pieces.add(createScattered(command, understory.get(random.nextInt(understory.size())), new float [] {px, py}, random,
              variation, level, ignored, counts));
        }
      }
    }
    if (pieces.isEmpty()) {
      throw new IOException("no place for a tree: the area is smaller than \"spacing\", or all of it is in a room or near a building");
    }
    add(pieces, level);
    tally [0] += pieces.size();
    notes.add("Planted " + pieces.size() + (pieces.size() == 1 ? " plant" : " plants") + " on the ground: " + describeCounts(counts)
        + describeIds(pieces) + (skipped > 0 ? ". " + skipped + " places in a room or closer than " + format(margin)
            + " cm to a building were skipped" : ""));
    ignored.removeAll(Arrays.asList("area", "along", "density", "spacing", "understory", "variation", "min_distance_from_buildings",
        "count", "jitter", "rotate"));
    warnIgnored(action, ignored, notes);
  }

  private HomePieceOfFurniture createScattered(AssistantCommand command, AssistantPlantBuilder.Species species, float [] place,
                                               Random random, float variation, Level level, List<String> ignored,
                                               Map<String, Integer> counts) throws IOException {
    Map<String, Object> spec = new LinkedHashMap<String, Object>(command.getParameters());
    for (String key : new String [] {"action", "level", "name", "understory", "seed"}) {
      spec.remove(key);
    }
    spec.put("species", species.name);
    spec.put("x", Double.valueOf(place [0]));
    spec.put("y", Double.valueOf(place [1]));
    float height = number(command.getParameters(), "height", species.height * 0.8f) * (1 + (random.nextFloat() * 2 - 1) * variation);
    spec.put("height", Double.valueOf(Math.max(10, Math.round(height))));
    counts.put(species.name, Integer.valueOf(counts.containsKey(species.name) ? counts.get(species.name).intValue() + 1 : 1));
    return createPlant(spec, species.shrub, level, ignored);
  }

  private boolean isNearBuilding(float x, float y, float margin) {
    for (Room room : this.home.getRooms()) {
      if (room.containsPoint(x, y, margin)) {
        return true;
      }
    }
    for (Wall wall : this.home.getWalls()) {
      if (wall.containsPoint(x, y, margin)) {
        return true;
      }
    }
    return false;
  }

  // ---- Lists of trees ----

  /**
   * Returns the trees a command gives with <code>trees</code>, a CSV text, a GeoJSON object or a file.
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> readTrees(AssistantCommand command) throws IOException {
    List<Map<String, Object>> trees = new ArrayList<Map<String, Object>>();
    Object value = command.getParameters().get("trees");
    if (value instanceof List) {
      for (Object tree : (List<?>)value) {
        if (tree instanceof Map) {
          trees.add(new LinkedHashMap<String, Object>((Map<String, Object>)tree));
        }
      }
      return trees;
    }
    Object geojson = command.getParameters().get("geojson");
    String csv = command.getString("csv");
    String path = command.getString("path") != null ? command.getString("path") : command.getString("file");
    if (geojson == null && csv == null && path != null) {
      File file = new File(path);
      if (!file.isAbsolute() || !file.isFile()) {
        throw new IOException("\"path\" must be the absolute path of a CSV or GeoJSON file");
      }
      String text = new String(AssistantGeneratedModel.readFile(file, MAX_FILE_LENGTH), "UTF-8").trim();
      if (text.startsWith("{") || text.startsWith("[")) {
        geojson = text;
      } else {
        csv = text;
      }
    }
    float scale = 1;
    String units = command.getString("units");
    if (units != null) {
      String unit = units.trim().toLowerCase(Locale.ENGLISH);
      scale = unit.startsWith("m") ? 100 : unit.startsWith("f") ? 30.48f : unit.startsWith("i") ? 2.54f : 1;
    }
    if (geojson instanceof String) {
      try {
        geojson = JSONParser.parse((String)geojson);
      } catch (IllegalArgumentException ex) {
        throw new IOException("the trees aren't written in JSON (" + ex.getMessage() + ")");
      }
    }
    if (geojson != null) {
      List<Object> features = new ArrayList<Object>();
      if (geojson instanceof Map && ((Map<?, ?>)geojson).get("features") instanceof List) {
        features.addAll((List<?>)((Map<?, ?>)geojson).get("features"));
      } else if (geojson instanceof List) {
        features.addAll((List<?>)geojson);
      } else {
        features.add(geojson);
      }
      for (Object feature : features) {
        if (!(feature instanceof Map)) {
          continue;
        }
        Map<?, ?> geometry = ((Map<?, ?>)feature).get("geometry") instanceof Map ? (Map<?, ?>)((Map<?, ?>)feature).get("geometry")
            : (Map<?, ?>)feature;
        if (!(geometry.get("coordinates") instanceof List) || ((List<?>)geometry.get("coordinates")).size() < 2
            || !(((List<?>)geometry.get("coordinates")).get(0) instanceof Number)) {
          continue;
        }
        Map<String, Object> tree = new LinkedHashMap<String, Object>();
        if (((Map<?, ?>)feature).get("properties") instanceof Map) {
          for (Map.Entry<?, ?> entry : ((Map<?, ?>)((Map<?, ?>)feature).get("properties")).entrySet()) {
            putTreeValue(tree, String.valueOf(entry.getKey()), entry.getValue(), scale);
          }
        }
        // A point of GeoJSON is a longitude then a latitude
        tree.put("lon", ((List<?>)geometry.get("coordinates")).get(0));
        tree.put("lat", ((List<?>)geometry.get("coordinates")).get(1));
        trees.add(tree);
      }
    } else if (csv != null) {
      String [] lines = csv.split("\\r?\\n");
      String separator = lines [0].indexOf('\t') >= 0 ? "\t" : lines [0].indexOf(';') >= 0 ? ";" : ",";
      String [] header = lines [0].split(separator, -1);
      boolean plan = false;
      for (String column : header) {
        plan |= column.trim().equalsIgnoreCase("x");
      }
      for (int i = 1; i < lines.length; i++) {
        if (lines [i].trim().length() == 0) {
          continue;
        }
        String [] cells = lines [i].split(separator, -1);
        Map<String, Object> tree = new LinkedHashMap<String, Object>();
        for (int k = 0; k < header.length && k < cells.length; k++) {
          String cell = cells [k].trim().replace("\"", "");
          if (cell.length() == 0) {
            continue;
          }
          Object cellValue = cell;
          try {
            cellValue = Double.valueOf(cell);
          } catch (NumberFormatException ex) {
            // A text
          }
          String column = header [k].trim().replace("\"", "").toLowerCase(Locale.ENGLISH);
          if (plan && (column.equals("x") || column.equals("y"))) {
            tree.put(column, cellValue);
          } else {
            putTreeValue(tree, column, cellValue, scale);
          }
        }
        trees.add(tree);
      }
    } else {
      throw new IOException("give the trees with \"trees\":[...], \"csv\", \"geojson\" or the \"path\" of a file");
    }
    return trees;
  }

  /**
   * Puts in a tree the value of a column of a file, under the key of add_tree it stands for.
   */
  private static void putTreeValue(Map<String, Object> tree, String column, Object value, float scale) {
    String key = column.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_');
    boolean size = false;
    if (key.equals("lat") || key.equals("latitude")) {
      key = "lat";
    } else if (key.equals("lon") || key.equals("lng") || key.equals("long") || key.equals("longitude")) {
      key = "lon";
    } else if (key.equals("height") || key.equals("h") || key.equals("tree_height") || key.equals("ht") || key.equals("z")) {
      key = "height";
      size = true;
    } else if (key.equals("crown_radius") || key.equals("radius") || key.equals("crown_r")) {
      key = "crown_radius";
      size = true;
    } else if (key.equals("crown_diameter") || key.equals("crown_width") || key.equals("crown_w") || key.equals("canopy")) {
      key = "crown_w";
      size = true;
    } else if (key.equals("crown_base") || key.equals("cbh") || key.equals("crown_base_height")) {
      key = "crown_base";
      size = true;
    } else if (key.equals("dbh") || key.equals("trunk_dbh")) {
      // A diameter stays in cm
      key = "trunk_dbh";
    } else if (key.equals("species") || key.equals("type") || key.equals("common_name")) {
      key = "species";
    } else if (key.equals("tag") || key.equals("tags") || key.equals("name") || key.equals("season") || key.equals("seed")
               || key.equals("removed_since")) {
      // As it is
    } else {
      return;
    }
    if (key.equals("species") && AssistantPlantBuilder.getSpecies(String.valueOf(value)) == null) {
      // A species this program doesn't know is kept as a tag
      tree.put("tag", HomeItemTags.cleanTag(String.valueOf(value)));
      return;
    }
    tree.put(key, size && value instanceof Number ? (Object)Double.valueOf(((Number)value).doubleValue() * scale) : value);
  }

  private void importTrees(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<Map<String, Object>> trees = readTrees(command);
    if (trees.isEmpty()) {
      throw new IOException("no tree was read: a CSV file starts with a line of column names among x, y (or lat, lon), height, "
          + "crown_radius, species, tag; a GeoJSON file holds points");
    }
    Map<String, Object> parameters = new LinkedHashMap<String, Object>(command.getParameters());
    for (String key : new String [] {"path", "file", "csv", "geojson", "units"}) {
      parameters.remove(key);
    }
    addPlants("import_trees", new AssistantCommand("import_trees", parameters), trees, tally, notes);
  }

  private List<HomePieceOfFurniture> getPlants(AssistantCommand command, boolean all) {
    List<HomePieceOfFurniture> plants = new ArrayList<HomePieceOfFurniture>();
    if (command.has("ids") || command.has("id") || command.has("where") || command.has("names") || command.has("target")) {
      for (Selectable item : this.executor.resolveTargets(command)) {
        if (item instanceof HomePieceOfFurniture && getSpec((HomePieceOfFurniture)item) != null) {
          plants.add((HomePieceOfFurniture)item);
        }
      }
    } else if (all) {
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (getSpec(piece) != null) {
          plants.add(piece);
        }
      }
    }
    return plants;
  }

  /**
   * Builds a plant again from a new description, at its place, in an undoable way.
   */
  private void rebuild(HomePieceOfFurniture piece, Map<String, Object> spec, boolean rename) throws IOException {
    AssistantPlantBuilder.Species oldSpecies = AssistantPlantBuilder.getSpecies(String.valueOf(getSpec(piece).get("species")));
    AssistantPlantBuilder.Plant plant = getPlant(spec, false);
    Object [] model = getModel(plant);
    float x = number(spec, "x", piece.getX());
    float y = number(spec, "y", piece.getY());
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put(PLANT_PROPERTY, AssistantShapeCommands.toJson(getStoredSpec(spec, plant, x, y)));
    if (rename || plant.ghost) {
      // The tag of its species
      List<String> tags = new ArrayList<String>(HomeItemTags.getTags(piece));
      if (oldSpecies != null) {
        tags.remove(oldSpecies.name);
      }
      if (!tags.contains(plant.species.name)) {
        tags.add(plant.species.name);
      }
      if (plant.ghost && !tags.contains("removed")) {
        tags.add("removed");
      }
      properties.put(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(tags));
    }
    String name = rename && oldSpecies != null && oldSpecies.commonName.equals(piece.getName()) ? plant.species.commonName : null;
    this.executor.getShapeCommands().replaceModel(piece, (AssistantGeneratedModel)model [0], x + ((Float)model [4]).floatValue(),
        y + ((Float)model [5]).floatValue(), piece.getElevation(), ((Float)model [1]).floatValue(), ((Float)model [2]).floatValue(),
        ((Float)model [3]).floatValue(), name, properties);
  }

  private void relabelTrees(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(command.getString("species"));
    if (species == null) {
      throw new IOException("give the \"species\" these trees become: {\"action\":\"list_species\"} gives the species");
    }
    List<HomePieceOfFurniture> plants = getPlants(command, false);
    if (plants.isEmpty()) {
      throw new IOException("no generated tree matches: give them with \"ids\" or \"where\", like {\"tag\":\"lidar\",\"bbox\":[x1,y1,x2,y2]}");
    }
    for (HomePieceOfFurniture piece : plants) {
      Map<String, Object> spec = getSpec(piece);
      spec.put("species", species.name);
      // Its sizes are kept, but not the trunks of its old species
      spec.remove("multi_trunk");
      rebuild(piece, spec, true);
    }
    tally [4] += plants.size();
    notes.add("Relabeled " + plants.size() + (plants.size() == 1 ? " tree" : " trees") + " as " + species.name + " ("
        + species.commonName + "), at the same places and with the same sizes");
  }

  private void growTrees(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    double years = command.getDouble("years", Double.NaN);
    if (Double.isNaN(years) || years == 0 || Math.abs(years) > 300) {
      throw new IOException("give \"years\": the number of years the trees get older (negative: younger)");
    }
    List<HomePieceOfFurniture> plants = getPlants(command, true);
    if (plants.isEmpty()) {
      throw new IOException("the home has no generated tree: add_tree plants one");
    }
    float grown = 0;
    int changed = 0;
    for (HomePieceOfFurniture piece : plants) {
      Map<String, Object> spec = getSpec(piece);
      AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(String.valueOf(spec.get("species")));
      if (species == null || species.growth == 0 || spec.get("removed_since") != null) {
        continue;
      }
      float height = number(spec, "height", species.height);
      // A tree grows slower as it gets close to the size of its species
      float limit = species.height * 1.5f;
      float newHeight = height;
      for (int year = 0; year < Math.abs(Math.round(years)); year++) {
        newHeight += years > 0 ? species.growth * Math.max(0.05f, 1 - newHeight / limit)
            : -species.growth * Math.max(0.05f, 1 - newHeight / limit);
      }
      newHeight = Math.max(species.shrub ? 15 : 60, Math.min(limit, newHeight));
      if (Math.abs(newHeight - height) < 1) {
        continue;
      }
      float ratio = newHeight / height;
      spec.put("height", Double.valueOf(Math.round(newHeight)));
      for (String key : new String [] {"crown_w", "crown_d", "crown_base", "trunk_dbh"}) {
        spec.put(key, Double.valueOf(number(spec, key, 0) * ratio));
      }
      rebuild(piece, spec, false);
      grown += newHeight - height;
      changed++;
    }
    tally [4] += changed;
    notes.add((years > 0 ? "Grew " : "Made younger ") + changed + (changed == 1 ? " plant" : " plants") + " by " + format(Math.abs(years))
        + " years: " + (changed > 0 ? format(Math.abs(grown) / changed / 100) + " m " + (years > 0 ? "higher" : "lower") + " on average" : "none changed")
        + (changed < plants.size() ? " (" + (plants.size() - changed) + " at the size of their species, or removed, didn't change)" : ""));
  }

  private void setSeason(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String season = command.getString("season");
    String lod = command.getString("lod");
    if (season == null && command.getString("date") != null) {
      // The month, with the hemisphere of the home
      String date = command.getString("date").trim();
      int month;
      try {
        month = Integer.parseInt(date.split("-") [1]);
      } catch (RuntimeException ex) {
        throw new IOException("\"date\" is written YYYY-MM-DD");
      }
      boolean south = this.home.getCompass() != null && this.home.getCompass().getLatitude() < 0;
      int index = month >= 3 && month <= 5 ? 0 : month >= 6 && month <= 8 ? 1 : month >= 9 && month <= 11 ? 2 : 3;
      season = SEASONS.get(south ? (index + 2) % 4 : index);
    }
    if (season != null) {
      season = season.trim().toLowerCase(Locale.ENGLISH);
      if (season.equals("fall")) {
        season = "autumn";
      }
      if (!SEASONS.contains(season)) {
        throw new IOException("\"season\" is spring, summer, autumn or winter");
      }
    }
    if (lod != null) {
      lod = lod.trim().toLowerCase(Locale.ENGLISH);
      if (!lod.equals("low") && !lod.equals("medium") && !lod.equals("high")) {
        throw new IOException("\"lod\" is low, medium or high");
      }
    }
    if (season == null && lod == null) {
      throw new IOException("give \"season\":\"spring|summer|autumn|winter\", a \"date\", or \"lod\":\"low|medium|high\"");
    }
    List<HomePieceOfFurniture> plants = getPlants(command, true);
    int changed = 0;
    for (HomePieceOfFurniture piece : plants) {
      Map<String, Object> spec = getSpec(piece);
      if (spec.get("removed_since") != null) {
        continue;
      }
      // The plants follow the home from now on
      spec.remove(season != null ? "season" : "lod");
      Map<String, Object> rebuilt = new LinkedHashMap<String, Object>(spec);
      if (season != null) {
        rebuilt.put("season", season);
      }
      if (lod != null) {
        rebuilt.put("lod", lod);
      }
      rebuild(piece, rebuilt, false);
      changed++;
    }
    boolean wholeHome = !(command.has("ids") || command.has("id") || command.has("where"));
    if (wholeHome) {
      if (season != null) {
        this.home.setProperty(SEASON_PROPERTY, season);
      }
      if (lod != null) {
        this.home.setProperty(LOD_PROPERTY, lod);
      }
    }
    tally [4] += changed;
    notes.add("Set " + (season != null ? "the season to " + season : "") + (season != null && lod != null ? " and " : "")
        + (lod != null ? "the detail to " + lod : "") + " for " + changed + (changed == 1 ? " generated plant" : " generated plants")
        + (wholeHome ? ", and for the ones added later" : "")
        + (season != null ? ". Catalog pieces and hedges don't change with the season" : ""));
  }

  private void updateTrees(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<Map<String, Object>> trees = readTrees(command);
    String tag = command.getString("tag");
    if (tag == null || tag.trim().length() == 0) {
      throw new IOException("give the \"tag\" of the trees of the home this list replaces, like \"lidar\"");
    }
    tag = HomeItemTags.cleanTag(tag);
    float tolerance = (float)command.getDouble("tolerance", 150);
    String removedSince = command.getString("removed_since");
    Level level = getLevel(command, "update_trees", notes);
    List<HomePieceOfFurniture> existing = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      Map<String, Object> spec = getSpec(piece);
      if (spec != null && HomeItemTags.hasTag(piece, tag) && spec.get("removed_since") == null) {
        existing.add(piece);
      }
    }
    // Each tree of the list with the closest tree of the home that is still free
    List<HomePieceOfFurniture> free = new ArrayList<HomePieceOfFurniture>(existing);
    List<HomePieceOfFurniture> added = new ArrayList<HomePieceOfFurniture>();
    List<String> ignored = new ArrayList<String>();
    int kept = 0;
    int resized = 0;
    for (Map<String, Object> tree : trees) {
      Map<String, Object> spec = new LinkedHashMap<String, Object>(tree);
      if (spec.get("tag") == null && spec.get("tags") == null) {
        spec.put("tag", tag);
      } else {
        spec.put("tags", Arrays.asList((Object)tag, String.valueOf(spec.get("tag") != null ? spec.get("tag") : "")));
      }
      float x = number(spec, "x", Float.NaN);
      float y = number(spec, "y", Float.NaN);
      if ((Float.isNaN(x) || Float.isNaN(y)) && spec.get("lat") instanceof Number && spec.get("lon") instanceof Number) {
        HomeGeoreference georeference = HomeGeoreference.get(this.home);
        if (georeference == null) {
          throw new IOException("\"lat\" and \"lon\" need the georeference of the home: set_georeference gives it");
        }
        double [] point = georeference.toPlan(((Number)spec.get("lat")).doubleValue(), ((Number)spec.get("lon")).doubleValue());
        x = (float)point [0];
        y = (float)point [1];
        spec.put("x", Double.valueOf(x));
        spec.put("y", Double.valueOf(y));
      }
      HomePieceOfFurniture closest = null;
      double best = tolerance;
      for (HomePieceOfFurniture piece : free) {
        Map<String, Object> pieceSpec = getSpec(piece);
        double distance = Math.hypot(number(pieceSpec, "x", piece.getX()) - x, number(pieceSpec, "y", piece.getY()) - y);
        if (distance <= best) {
          best = distance;
          closest = piece;
        }
      }
      if (closest != null) {
        free.remove(closest);
        kept++;
        // A tree that grew takes its new sizes
        Map<String, Object> pieceSpec = getSpec(closest);
        float height = number(spec, "height", Float.NaN);
        if (!Float.isNaN(height) && Math.abs(height - number(pieceSpec, "height", height)) > 50) {
          float ratio = height / number(pieceSpec, "height", height);
          pieceSpec.put("height", Double.valueOf(height));
          float radius = number(spec, "crown_radius", Float.NaN);
          for (String key : new String [] {"crown_w", "crown_d", "crown_base", "trunk_dbh"}) {
            pieceSpec.put(key, Double.valueOf(key.startsWith("crown_") && !key.equals("crown_base") && !Float.isNaN(radius)
                ? radius * 2 : number(pieceSpec, key, 0) * ratio));
          }
          rebuild(closest, pieceSpec, false);
          resized++;
        }
      } else {
        AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(
            spec.get("species") != null ? spec.get("species").toString() : "generic_deciduous");
        added.add(createPlant(spec, species != null && species.shrub, level, ignored));
      }
    }
    add(added, level);
    tally [0] += added.size();
    if (removedSince != null && removedSince.trim().length() > 0) {
      for (HomePieceOfFurniture piece : free) {
        Map<String, Object> spec = getSpec(piece);
        spec.put("removed_since", removedSince.trim());
        rebuild(piece, spec, false);
      }
      tally [4] += free.size();
    } else if (!free.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().deleteItems(free);
      tally [5] += free.size();
    }
    tally [4] += resized;
    notes.add("Compared " + trees.size() + " trees with the " + existing.size() + " trees tagged \"" + tag + "\": " + added.size()
        + " added" + describeIds(added).replace(". Ids", " (ids") + (added.isEmpty() ? "" : ")") + ", " + free.size()
        + (removedSince != null ? " not in the list anymore, kept as ghosts removed since " + removedSince.trim() : " deleted")
        + ", " + kept + " kept" + (resized > 0 ? " (" + resized + " with new sizes)" : ""));
    warnIgnored("update_trees", ignored, notes);
  }

  /**
   * Replaces pieces of furniture by plants of a species, at their places and with their sizes.
   */
  private void replaceWithPlants(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(command.getString("species"));
    if (species == null) {
      throw new IOException("unknown \"species\" \"" + command.getString("species") + "\": {\"action\":\"list_species\"} gives the species");
    }
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture && !HomeTerrain.isTerrain((HomePieceOfFurniture)item)
          && ((HomePieceOfFurniture)item).getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) == null) {
        pieces.add((HomePieceOfFurniture)item);
      }
    }
    if (pieces.isEmpty()) {
      throw new IOException("give the pieces to replace with \"ids\" or \"where\"");
    }
    boolean keepSize = command.getBoolean("keep_size", true);
    List<String> ignored = new ArrayList<String>();
    Map<Level, List<HomePieceOfFurniture>> byLevel = new LinkedHashMap<Level, List<HomePieceOfFurniture>>();
    for (HomePieceOfFurniture piece : pieces) {
      Map<String, Object> oldSpec = getSpec(piece);
      if (oldSpec != null) {
        // A generated plant changes of species in place
        oldSpec.put("species", species.name);
        oldSpec.remove("multi_trunk");
        rebuild(piece, oldSpec, true);
        tally [4]++;
        continue;
      }
      Map<String, Object> spec = new LinkedHashMap<String, Object>();
      spec.put("species", species.name);
      spec.put("x", Double.valueOf(piece.getX()));
      spec.put("y", Double.valueOf(piece.getY()));
      if (keepSize) {
        spec.put("height", Double.valueOf(piece.getHeight()));
        spec.put("crown_w", Double.valueOf(Math.max(piece.getWidth(), piece.getDepth())));
      }
      for (String key : new String [] {"season", "lod", "seed", "pruned_shape"}) {
        if (command.getParameters().get(key) != null) {
          spec.put(key, command.getParameters().get(key));
        }
      }
      List<String> tags = new ArrayList<String>(HomeItemTags.getTags(piece));
      if (!tags.isEmpty()) {
        spec.put("tags", new ArrayList<Object>(tags));
      }
      HomePieceOfFurniture plant = createPlant(spec, species.shrub, piece.getLevel(), ignored);
      List<HomePieceOfFurniture> plants = byLevel.get(piece.getLevel());
      if (plants == null) {
        byLevel.put(piece.getLevel(), plants = new ArrayList<HomePieceOfFurniture>());
      }
      plants.add(plant);
    }
    int replaced = 0;
    List<HomePieceOfFurniture> deleted = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : pieces) {
      if (getSpec(piece) == null) {
        deleted.add(piece);
      }
    }
    for (Map.Entry<Level, List<HomePieceOfFurniture>> entry : byLevel.entrySet()) {
      add(entry.getValue(), entry.getKey());
      replaced += entry.getValue().size();
    }
    if (!deleted.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().deleteItems(deleted);
    }
    tally [4] += replaced;
    notes.add("Replaced " + pieces.size() + (pieces.size() == 1 ? " piece" : " pieces") + " by " + species.name + " ("
        + species.commonName + ")" + (keepSize ? ", with their heights and their widths" : ", at the usual size of the species")
        + ", on the ground");
  }

  /**
   * Returns the model that shows a generated plant in another way, for a picture: pale and seen through
   * (<code>xray</code>), or at another season. Returns <code>null</code> if <code>piece</code> isn't a generated plant.
   */
  com.eteks.sweethome3d.model.Content getPictureModel(HomePieceOfFurniture piece, boolean xray, String season) {
    Map<String, Object> spec = getSpec(piece);
    if (spec == null) {
      return null;
    }
    try {
      if (season != null) {
        spec.put("season", season);
      }
      if (xray) {
        spec.put("removed_since", "picture");
      }
      return ((AssistantGeneratedModel)getModel(getPlant(spec, false)) [0]).getModel();
    } catch (IOException ex) {
      return null;
    }
  }

  /**
   * Returns the color of the leaves of a species, for hedges.
   */
  static Integer getLeafColor(String speciesName) {
    AssistantPlantBuilder.Species species = AssistantPlantBuilder.getSpecies(speciesName);
    return species != null ? Integer.valueOf(species.leaf) : null;
  }
}
