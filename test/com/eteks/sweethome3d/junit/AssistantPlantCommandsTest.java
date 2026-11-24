/*
 * AssistantPlantCommandsTest.java 23 nov. 2026
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
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the trees and the shrubs built from a species, and the commands that change them.
 */
public class AssistantPlantCommandsTest {
  private String                   previousNo3D;
  private Locale                   previousLocale;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.previousLocale = Locale.getDefault();
    Locale.setDefault(Locale.UK);
    this.home = new Home(250);
    this.preferences = new DefaultUserPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
  }

  @After
  public void tearDown() {
    Locale.setDefault(this.previousLocale);
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private HomePieceOfFurniture getPiece(String name) {
    HomePieceOfFurniture found = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (name.equals(piece.getName())) {
        found = piece;
      }
    }
    return found;
  }

  private List<HomePieceOfFurniture> getPlants() {
    List<HomePieceOfFurniture> plants = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantPlant") != null) {
        plants.add(piece);
      }
    }
    return plants;
  }

  @Test
  public void testEverySpeciesWithinItsBudget() {
    List<String> trees = HomeAssistantContext.getPlantSpecies(false);
    List<String> shrubs = HomeAssistantContext.getPlantSpecies(true);
    assertTrue(trees.size() + " trees", trees.size() >= 90);
    assertTrue(shrubs.size() + " shrubs", shrubs.size() >= 30);
    List<String> all = new ArrayList<String>(trees);
    all.addAll(shrubs);
    for (String species : all) {
      for (String season : new String [] {"spring", "summer", "autumn", "winter"}) {
        int low = HomeAssistantContext.getPlantTriangleCount(species, "low", season);
        int medium = HomeAssistantContext.getPlantTriangleCount(species, "medium", season);
        assertTrue(species + " " + season + " low " + low, low > 0 && low <= 2000);
        assertTrue(species + " " + season + " medium " + medium, medium >= low && medium <= 10000);
      }
    }
    // Each one is planted at its usual size
    StringBuilder commands = new StringBuilder();
    for (int i = 0; i < all.size(); i++) {
      commands.append(i > 0 ? "," : "").append("{\"action\":\"").append(i < trees.size() ? "add_tree" : "add_shrub")
          .append("\",\"species\":\"").append(all.get(i)).append("\",\"x\":").append(i * 800).append(",\"y\":0}");
    }
    String summary = this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands(), 500);
    assertEquals(summary, all.size(), getPlants().size());
    assertFalse(summary, summary.contains("Couldn't") || summary.contains("Warning"));
    summary = run("{\"action\":\"list_species\"}");
    for (String species : all) {
      assertTrue(species, summary.contains(species + " ("));
    }
  }

  /**
   * Returns the OBJ file of the model of a piece, without what names it.
   */
  private static String getObj(HomePieceOfFurniture piece) throws java.io.IOException {
    java.io.InputStream in = piece.getModel().openStream();
    try {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
      return out.toString("ISO-8859-1").replaceAll("_[0-9a-f]{6,}", "");
    } finally {
      in.close();
    }
  }

  private static int count(String text, String start) {
    int count = 0;
    for (String line : text.split("\n")) {
      if (line.startsWith(start)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Trees grow branches and carry clusters of leaves, the same ones for the same seed.
   */
  @Test
  public void testNaturalTrees() throws Exception {
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":0,\"y\":0,\"height\":1500,\"seed\":1}");
    HomePieceOfFurniture oak = getPlants().get(0);
    String obj = getObj(oak);
    // Limbs with their bark, and cards with the normal that lights them as a part of the crown
    assertTrue(count(obj, "usemtl ") + " materials", count(obj, "usemtl ") >= 3);
    assertTrue(count(obj, "vn ") + " cards", count(obj, "vn ") >= 300);
    assertTrue(count(obj, "f ") + " faces", count(obj, "f ") >= 1500 && count(obj, "f ") <= 6000);
    assertTrue(count(obj, "vt ") > 0);
    // The crown has the size that was asked: 0.95 of its height for this species
    assertEquals(1500, oak.getHeight(), 0.5f);
    assertEquals(1425, oak.getWidth(), 0.5f);

    // The same tree in another home is the same one
    Home otherHome = new Home(250);
    HomeController otherController = new HomeController(otherHome, this.preferences, new SwingViewFactory());
    otherController.getView();
    AssistantCommandExecutor otherExecutor = new AssistantCommandExecutor(otherHome, this.preferences, otherController);
    otherExecutor.execute(AssistantCommandParser.parse(
        "{\"commands\":[{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":0,\"y\":0,\"height\":1500,\"seed\":1}]}").getCommands());
    assertEquals(obj, getObj(otherHome.getFurniture().get(0)));

    // Another seed, another tree, and no two outlines alike
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":3000,\"y\":0,\"height\":1500,\"seed\":2}");
    HomePieceOfFurniture oak2 = getPlants().get(1);
    assertFalse(obj.equals(getObj(oak2)));
    assertTrue(oak.getAngle() != oak2.getAngle());

    // In winter its branches show, with the dead leaves an oak keeps
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":6000,\"y\":0,\"height\":1500,\"seed\":1,\"season\":\"winter\"}");
    String winter = getObj(getPlants().get(2));
    assertTrue(count(winter, "vn ") > 0);
    assertFalse(winter.equals(obj));

    // The plain masses of the first versions are still there
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":9000,\"y\":0,\"height\":1500,\"style\":\"simple\"}");
    HomePieceOfFurniture simple = getPlants().get(3);
    assertEquals(0, count(getObj(simple), "vn "));
    assertEquals(0, simple.getAngle(), 0);
    assertTrue(simple.getProperty("assistantPlant"), simple.getProperty("assistantPlant").contains("\"style\":\"simple\""));

    // A species grows another way
    String summary = run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":12000,\"y\":0,\"height\":1500,\"seed\":1,"
        + "\"profile\":{\"droop\":0.9,\"leaf\":\"lance\",\"card\":0.2}}");
    assertFalse(summary, summary.contains("Couldn't"));
    assertFalse(obj.equals(getObj(getPlants().get(4))));
    summary = run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":15000,\"y\":0,\"profile\":{\"wind\":3}}");
    assertTrue(summary, summary.contains("\"profile\" has no \"wind\"") && summary.contains("gaps"));
    assertEquals(5, getPlants().size());

    // All the detail stays within its count of triangles
    for (String species : new String [] {"white_oak", "loblolly_pine", "norway_spruce", "eastern_red_cedar", "crape_myrtle", "azalea"}) {
      for (String season : new String [] {"summer", "winter"}) {
        int high = HomeAssistantContext.getPlantTriangleCount(species, "high", season);
        assertTrue(species + " " + season + " high " + high, high > 2000 && high <= 40000);
      }
    }

    // The whole home goes back to plain masses, and the plants added later too
    summary = run("{\"action\":\"set_plant_style\",\"style\":\"simple\"}");
    assertTrue(summary, summary.contains("the style to simple for 5 generated plants"));
    assertEquals(0, count(getObj(oak), "vn "));
    run("{\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":18000,\"y\":0}");
    assertEquals(0, count(getObj(getPlants().get(5)), "vn "));
  }

  /**
   * A plant shown by a piece of the catalog keeps what a plant has.
   */
  /**
   * A plant shown by pictures of it: 8 views from its sides on 4 crossed cards and its view from the top.
   */
  @Test
  public void testImpostors() throws Exception {
    String summary = run("{\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":0,\"y\":0,\"height\":1200,\"seed\":1,\"lod\":\"impostor\"}");
    assertFalse(summary, summary.contains("Couldn't"));
    HomePieceOfFurniture maple = getPlants().get(0);
    String obj = getObj(maple);
    assertEquals(9, count(obj, "f "));
    assertEquals(36, count(obj, "vt "));
    assertEquals(1, count(obj, "usemtl "));
    // No shadow of a card on the other ones
    assertTrue(obj, obj.contains("usemtl unshadowed_texture"));
    assertEquals(1200, maple.getHeight(), 0.5f);
    assertNotNull(maple.getPlanIcon());
    assertNotNull(maple.getIcon());
    assertTrue(maple.getProperty("assistantPlant"), maple.getProperty("assistantPlant").contains("\"lod\":\"impostor\""));
    // The plan shows a plant seen from the top, not its icon in a square
    assertTrue(com.eteks.sweethome3d.viewcontroller.FurniturePlanVisibility.isDrawnFromTop(maple));

    // The other plants become pictures too, palms and plain shapes included
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":3000,\"y\":0,\"height\":1500,\"seed\":1}");
    run("{\"action\":\"add_tree\",\"species\":\"sabal_palm\",\"x\":6000,\"y\":0,\"height\":900}");
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":9000,\"y\":0,\"height\":1500,\"style\":\"simple\"}");
    assertTrue(count(getObj(getPlants().get(1)), "f ") > 1000);
    summary = run("{\"action\":\"set_season\",\"lod\":\"impostor\"}");
    assertFalse(summary, summary.contains("Couldn't"));
    for (HomePieceOfFurniture plant : getPlants()) {
      assertEquals(plant.getName(), 9, count(getObj(plant), "f "));
    }
    assertEquals(1500, getPlants().get(1).getHeight(), 0.5f);
    assertEquals(1425, getPlants().get(1).getWidth(), 0.5f);

    summary = run("{\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":0,\"y\":3000,\"lod\":\"flat\"}");
    assertTrue(summary, summary.contains("impostor, low, medium or high"));
  }

  /**
   * The models of plants are kept in a folder, and found there by another home.
   */
  @Test
  public void testModelsKeptOnDisk() throws Exception {
    String property = "com.eteks.sweethome3d.assistantModelCache";
    String previousFolder = System.getProperty(property);
    java.io.File folder = java.io.File.createTempFile("models", "");
    folder.delete();
    try {
      System.setProperty(property, folder.getPath());
      String command = "{\"action\":\"add_tree\",\"species\":\"river_birch\",\"x\":0,\"y\":0,\"height\":1000,\"seed\":3}";
      run(command);
      String obj = getObj(getPlants().get(0));
      assertEquals(1, folder.listFiles().length);
      java.io.File file = folder.listFiles() [0];
      assertTrue(file.getName(), file.getName().matches("[0-9a-f]{40}\\.zip"));

      // Another home gets the model that was kept, with its names: built again they would differ
      java.io.InputStream in = getPlants().get(0).getModel().openStream();
      in.close();
      String name1 = getModelName(getPlants().get(0));
      String name2 = getModelName(addInOtherHome(command));
      assertEquals(name1, name2);
      assertEquals(1, folder.listFiles().length);
      // Another tree, another file
      run("{\"action\":\"add_tree\",\"species\":\"river_birch\",\"x\":3000,\"y\":0,\"height\":1000,\"seed\":4}");
      assertEquals(2, folder.listFiles().length);

      // A file that can't be read is replaced
      java.io.FileOutputStream out = new java.io.FileOutputStream(file);
      out.write(new byte [] {1, 2, 3});
      out.close();
      HomePieceOfFurniture rebuilt = addInOtherHome(command);
      assertEquals(obj, getObj(rebuilt));
      assertFalse(name1.equals(getModelName(rebuilt)));
      assertTrue(file.length() > 1000);

      // Nothing kept when it's refused
      System.setProperty(property, "false");
      run("{\"action\":\"add_tree\",\"species\":\"river_birch\",\"x\":6000,\"y\":0,\"height\":1000,\"seed\":5}");
      assertEquals(2, folder.listFiles().length);
    } finally {
      if (previousFolder == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, previousFolder);
      }
      java.io.File [] files = folder.listFiles();
      if (files != null) {
        for (java.io.File file : files) {
          file.delete();
        }
      }
      folder.delete();
    }
  }

  private HomePieceOfFurniture addInOtherHome(String command) {
    Home otherHome = new Home(250);
    HomeController otherController = new HomeController(otherHome, this.preferences, new SwingViewFactory());
    otherController.getView();
    new AssistantCommandExecutor(otherHome, this.preferences, otherController).execute(
        AssistantCommandParser.parse("{\"commands\":[" + command + "]}").getCommands());
    return otherHome.getFurniture().get(0);
  }

  /**
   * Returns the name of the OBJ file of a piece in its archive.
   */
  private static String getModelName(HomePieceOfFurniture piece) {
    String url = ((com.eteks.sweethome3d.tools.URLContent)piece.getModel()).getURL().toString();
    return url.substring(url.indexOf("!/") + 2);
  }

  @Test
  public void testTreesOfTheCatalog() throws Exception {
    // No tree in this catalog: the plant is generated and the answer tells it
    String summary = run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":0,\"y\":0,\"height\":1500,\"model\":\"catalog\"}");
    assertTrue(summary, summary.contains("The furniture catalog has no tree for [white_oak]") && summary.contains("Sycamore maple"));
    HomePieceOfFurniture oak = getPlants().get(0);
    assertTrue(count(getObj(oak), "vn ") > 0);

    // A piece of the catalog by its name
    summary = run("{\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":3000,\"y\":0,\"height\":1200,\"crown_radius\":400,"
        + "\"catalog_name\":\"Table\"}");
    assertFalse(summary, summary.contains("Couldn't") || summary.contains("no tree for"));
    HomePieceOfFurniture maple = getPlants().get(1);
    assertEquals(1200, maple.getHeight(), 0.5f);
    assertEquals(800, maple.getWidth(), 65);
    assertEquals(800, maple.getDepth(), 65);
    assertEquals(3000, maple.getX(), 0.5f);
    assertEquals("true", maple.getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
    assertTrue(maple.getProperty("assistantPlant"), maple.getProperty("assistantPlant").contains("\"model\":\"catalog\"")
        && maple.getProperty("assistantPlant").contains("\"catalog_name\":\"Table\""));
    assertTrue(com.eteks.sweethome3d.viewcontroller.HomeItemTags.getTags(maple).contains("red_maple"));
    Object model = maple.getModel();
    summary = run("{\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":4000,\"y\":0,\"catalog_name\":\"No such tree\"}");
    assertTrue(summary, summary.contains("no piece of the catalog is named exactly"));

    // It grows and changes species as a plant
    String id = HomeItemIds.getId(maple);
    summary = run("{\"action\":\"grow_trees\",\"ids\":[\"" + id + "\"],\"years\":5}");
    assertTrue(summary, maple.getHeight() > 1250);
    assertTrue(maple.getModel() == model);
    // In another season it keeps the leaves of its model
    summary = run("{\"action\":\"set_season\",\"season\":\"winter\"}");
    assertTrue(maple.getModel() == model);

    // Generated again
    summary = run("{\"action\":\"set_plant_style\",\"ids\":[\"" + id + "\"],\"model\":\"generated\"}");
    assertTrue(summary, summary.contains("the models to generated ones for 1 generated plant"));
    assertTrue(maple.getModel() != model);
    assertFalse(maple.getProperty("assistantPlant"), maple.getProperty("assistantPlant").contains("catalog_name"));
    assertTrue(getObj(maple).contains("usemtl "));
  }

  @Test
  public void testTreeSizesSeasonsAndGround() {
    run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,100],[3000,-1000,100],[3000,3000,100],[-1000,3000,100]]}");
    String summary = run("{\"action\":\"add_tree\",\"species\":\"Red maple\",\"x\":500,\"y\":600,\"height\":900,\"crown_radius\":250,"
        + "\"crown_base\":300,\"trunk_dbh\":30,\"tags\":[\"front\"]}");
    HomePieceOfFurniture maple = getPiece("Red maple");
    assertNotNull(summary, maple);
    assertTrue(summary, summary.contains("Added the tree " + HomeItemIds.getId(maple)) && summary.contains("(red_maple, deciduous): 9 m high, crown 5 m wide from 3 m up"));
    // On the ground, the trunk at the place asked for
    float floor = maple.getLevel() != null ? maple.getLevel().getElevation() : 0;
    assertEquals(100, maple.getElevation() + floor, 0.5f);
    assertEquals(500, maple.getX(), 40);
    assertEquals(600, maple.getY(), 40);
    assertEquals(900, maple.getHeight(), 40);
    assertEquals(500, maple.getWidth(), 90);
    assertEquals("true", maple.getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
    assertTrue(maple.getProperty("tags"), maple.getProperty("tags").contains("tree") && maple.getProperty("tags").contains("red_maple")
        && maple.getProperty("tags").contains("front"));
    // Not furniture out of its room
    assertFalse(run("{\"action\":\"check_home\"}").contains("Red maple"));
    // Bare in winter: another model, the same piece
    Object summerModel = maple.getModel();
    summary = run("{\"action\":\"set_season\",\"season\":\"winter\"}");
    assertTrue(summary, summary.contains("the season to winter for 1 generated plant"));
    assertTrue(maple.getModel() != summerModel);
    assertEquals(500, maple.getX(), 60);
    this.homeController.undo();
    assertTrue(maple.getModel() == summerModel);
    run("{\"action\":\"set_season\",\"date\":\"2026-10-20\"}");
    assertEquals("autumn", this.home.getProperty("assistantSeason"));
    // A tree planted later is in the season of the home; the same species and sizes share a model
    run("{\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":1500,\"y\":600,\"height\":900,\"crown_radius\":250,\"crown_base\":300,"
        + "\"trunk_dbh\":30,\"seed\":3,\"name\":\"Second\"},{\"action\":\"add_tree\",\"species\":\"red_maple\",\"x\":2200,\"y\":600,"
        + "\"height\":905,\"crown_radius\":250,\"crown_base\":300,\"trunk_dbh\":30,\"seed\":3,\"name\":\"Third\"}");
    assertTrue(getPiece("Second").getModel() == getPiece("Third").getModel());
    assertEquals(905, getPiece("Third").getHeight(), 40);
    // Mistakes are told
    summary = run("{\"action\":\"add_tree\",\"species\":\"baobab\",\"x\":0,\"y\":0}");
    assertTrue(summary, summary.contains("unknown \"species\" \"baobab\""));
    summary = run("{\"action\":\"add_tree\",\"species\":\"oak\",\"x\":0,\"y\":0,\"hieght\":500}");
    assertTrue(summary, summary.contains("add_tree ignored the key [hieght]"));
    summary = run("{\"action\":\"add_tree\",\"species\":\"oak\"}");
    assertTrue(summary, summary.contains("give the place"));
    summary = run("{\"action\":\"add_shrub\",\"species\":\"boxwood\",\"x\":100,\"y\":2000,\"pruned_shape\":\"box\",\"height\":80,\"crown_w\":300,\"crown_d\":60}");
    HomePieceOfFurniture boxwood = getPiece("Boxwood");
    assertEquals(300, boxwood.getWidth(), 5);
    assertEquals(60, boxwood.getDepth(), 5);
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + HomeItemIds.getId(boxwood) + "\"}");
    assertTrue(summary, summary.contains("generated shrub") && summary.contains("(boxwood, evergreen)"));
  }

  @Test
  public void testListsOfTrees() {
    String summary = run("{\"action\":\"add_trees\",\"tag\":\"lidar\",\"species\":\"loblolly_pine\",\"trees\":["
        + "{\"x\":0,\"y\":0,\"height\":2000,\"crown_radius\":300},{\"x\":800,\"y\":0,\"height\":1500,\"species\":\"white_oak\"},"
        + "{\"x\":1600,\"y\":0,\"height\":2500},{\"x\":2400,\"y\":0,\"species\":\"nothing\"}]}");
    assertTrue(summary, summary.contains("Added 3 trees on the ground: 2 loblolly_pine, 1 white_oak")
        && summary.contains("Couldn't add 1 of the 4 trees"));
    assertEquals(3, getPlants().size());
    for (HomePieceOfFurniture plant : getPlants()) {
      assertTrue(plant.getProperty("tags"), plant.getProperty("tags").contains("lidar"));
    }
    // One step of undo
    this.homeController.undo();
    assertEquals(0, getPlants().size());
    this.homeController.redo();
    // A newer survey: one tree cut, one new, one that grew
    summary = run("{\"action\":\"update_trees\",\"tag\":\"lidar\",\"removed_since\":\"2018\",\"trees\":["
        + "{\"x\":20,\"y\":10,\"height\":2300},{\"x\":1600,\"y\":0,\"height\":2500},{\"x\":4000,\"y\":0,\"height\":900,\"species\":\"red_maple\"}]}");
    assertTrue(summary, summary.contains("Compared 3 trees with the 3 trees tagged \"lidar\": 1 added")
        && summary.contains("1 not in the list anymore, kept as ghosts removed since 2018") && summary.contains("2 kept (1 with new sizes)"));
    assertEquals(4, getPlants().size());
    HomePieceOfFurniture oak = getPiece("White oak");
    assertTrue(oak.getProperty("assistantPlant"), oak.getProperty("assistantPlant").contains("\"removed_since\":\"2018\""));
    assertTrue(oak.getProperty("tags").contains("removed"));
    // Without a mark, the trees that are gone are deleted
    summary = run("{\"action\":\"update_trees\",\"tag\":\"lidar\",\"trees\":[{\"x\":20,\"y\":10,\"height\":2300}]}");
    assertTrue(summary, summary.contains("2 deleted") && summary.contains("1 kept"));
    // Another species at the same place
    summary = run("{\"action\":\"relabel_trees\",\"where\":{\"tag\":\"lidar\",\"bbox\":[-100,-100,100,100]},\"species\":\"shortleaf_pine\"}");
    assertTrue(summary, summary.contains("Relabeled 1 tree as shortleaf_pine"));
    HomePieceOfFurniture pine = getPiece("Shortleaf pine");
    assertNotNull(summary, pine);
    assertEquals(2300, pine.getHeight(), 100);
    assertTrue(pine.getProperty("tags"), pine.getProperty("tags").contains("shortleaf_pine") && !pine.getProperty("tags").contains("loblolly_pine"));
    // Years later
    summary = run("{\"action\":\"grow_trees\",\"years\":8}");
    assertTrue(summary, summary.contains("Grew") && summary.contains("by 8 years"));
    assertTrue(pine.getHeight() > 2350);
    summary = run("{\"action\":\"relabel_trees\",\"where\":{\"tag\":\"nobody\"},\"species\":\"shortleaf_pine\"}");
    assertTrue(summary, summary.contains("no generated tree matches"));
  }

  @Test
  public void testGrassesGrowthAndGhosts() {
    // A grass is a clump without a trunk
    String summary = run("{\"action\":\"add_shrub\",\"species\":\"muhly_grass\",\"x\":0,\"y\":0}");
    assertFalse(summary, summary.contains("trunk"));
    assertTrue(getPiece("Pink muhly grass").getProperty("assistantPlant"),
        getPiece("Pink muhly grass").getProperty("assistantPlant").contains("\"trunk_dbh\":0"));
    summary = run("{\"action\":\"add_shrub\",\"species\":\"boxwood\",\"x\":300,\"y\":0}");
    assertTrue(summary, summary.contains("trunk"));

    // Trees grow at the rate of their species: 30 to 60 cm a year for a black gum of 17 m
    run("{\"action\":\"add_tree\",\"species\":\"black_gum\",\"x\":2000,\"y\":0,\"tag\":\"young\"}");
    run("{\"action\":\"add_tree\",\"species\":\"tulip_poplar\",\"x\":4000,\"y\":0,\"height\":1000,\"tag\":\"young\"}");
    HomePieceOfFurniture gum = getPiece("Black gum");
    HomePieceOfFurniture poplar = getPiece("Tulip poplar");
    float gumHeight = gum.getHeight();
    float poplarHeight = poplar.getHeight();
    summary = run("{\"action\":\"grow_trees\",\"years\":8,\"where\":{\"tag\":\"young\"}}");
    assertTrue(summary, summary.contains("By species: black_gum +") && summary.contains("tulip_poplar +6 m (1)"));
    assertTrue(summary + " " + (gum.getHeight() - gumHeight), gum.getHeight() - gumHeight >= 240 && gum.getHeight() - gumHeight <= 480);
    assertEquals(summary, 600, poplar.getHeight() - poplarHeight, 30);
    // Slower near the largest size of the species, which it doesn't pass
    summary = run("{\"action\":\"grow_trees\",\"years\":100,\"where\":{\"tag\":\"young\"}}");
    assertEquals(summary, 1700 * 1.5f, gum.getHeight(), 60);

    // A tree missing from a new list is kept as a ghost with mark_removed
    run("{\"action\":\"add_trees\",\"tag\":\"survey\",\"trees\":[{\"x\":7000,\"y\":0,\"species\":\"red_maple\"},"
        + "{\"x\":8000,\"y\":0,\"species\":\"white_oak\"}]}");
    int count = getPlants().size();
    summary = run("{\"action\":\"update_trees\",\"tag\":\"survey\",\"mark_removed\":true,\"trees\":[{\"x\":7000,\"y\":0}]}");
    assertTrue(summary, summary.contains("1 not in the list anymore, kept as ghosts removed since 2") && !summary.contains("deleted"));
    assertEquals(count, getPlants().size());
    HomePieceOfFurniture oak = getPiece("White oak");
    assertTrue(oak.getProperty("tags"), oak.getProperty("tags").contains("removed"));
    assertTrue(oak.getProperty("assistantPlant"), oak.getProperty("assistantPlant").contains("\"removed_since\":\"2"));
  }

  @Test
  public void testForestDensityAndUnderstory() {
    // 0.5 tree per 100 m2 over 80 m2: one tree, said with the number expected
    String summary = run("{\"action\":\"forest_fill\",\"area\":[[0,0],[1000,0],[1000,800],[0,800]],\"species\":\"red_maple\",\"density\":0.5}");
    assertTrue(summary, summary.contains("1 tree for 1 expected (0.5 per 100 m2 over 80 m2)"));
    assertEquals(1, getPlants().size());
    // 4 per 100 m2 over 900 m2: 36 trees, and an understory with its own density
    summary = run("{\"action\":\"forest_fill\",\"area\":[[5000,0],[8000,0],[8000,3000],[5000,3000]],\"species\":\"loblolly_pine\","
        + "\"density\":4,\"understory\":{\"species\":[\"azalea\",\"flowering_dogwood\"],\"density\":8}}");
    assertTrue(summary, summary.contains("36 trees for 36 expected (4 per 100 m2 over 900 m2), and 72 plants of the understory for 72 expected"));
    assertFalse(summary, summary.contains("ignored"));
    assertEquals(1 + 36 + 72, getPlants().size());
    int azaleas = 0;
    for (HomePieceOfFurniture plant : getPlants()) {
      azaleas += plant.getProperty("tags").contains("azalea") ? 1 : 0;
    }
    assertTrue(summary, azaleas > 10);
    // Weights give as many plants under the trees as trees
    summary = run("{\"action\":\"forest_fill\",\"area\":[[10000,0],[12000,0],[12000,2000],[10000,2000]],\"species\":\"red_maple\","
        + "\"density\":1,\"understory\":{\"azalea\":2,\"boxwood\":1}}");
    assertTrue(summary, summary.contains("4 trees for 4 expected") && summary.contains("4 plants of the understory for 4 expected"));
    // What it can't read is said
    summary = run("{\"action\":\"forest_fill\",\"area\":[[10000,5000],[12000,5000],[12000,7000],[10000,7000]],\"species\":\"red_maple\","
        + "\"understory\":{\"density\":2}}");
    assertTrue(summary, summary.contains("Couldn't run forest_fill") && summary.contains("\"understory\" gives its species"));
  }

  @Test
  public void testImportForestAndReplace() {
    String summary = run("{\"action\":\"import_trees\",\"units\":\"m\",\"tag\":\"survey\","
        + "\"csv\":\"x,y,height,crown_radius,species\\n100,200,12,3,red_maple\\n900,200,20,4,Unknown tree\\n1700,200,8,2,\"}");
    assertTrue(summary, summary.contains("Added 3 trees on the ground: 2 generic_deciduous, 1 red_maple"));
    HomePieceOfFurniture maple = getPiece("Red maple");
    assertEquals(1200, maple.getHeight(), 60);
    assertEquals(100, maple.getX(), 40);
    // A species the program doesn't know is kept as a tag
    boolean tagged = false;
    for (HomePieceOfFurniture plant : getPlants()) {
      tagged |= plant.getProperty("tags").toLowerCase(Locale.ENGLISH).contains("unknown tree");
      assertTrue(plant.getProperty("tags"), plant.getProperty("tags").contains("survey"));
    }
    assertTrue(tagged);
    // Points of GeoJSON need the georeference
    String geojson = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\","
        + "\"coordinates\":[-84.3880,33.7490]},\"properties\":{\"height\":1500,\"species\":\"white_oak\"}}]}";
    summary = run("{\"action\":\"import_trees\",\"geojson\":" + geojson + "}");
    assertTrue(summary, summary.contains("need the georeference"));
    run("{\"action\":\"set_georeference\",\"x\":0,\"y\":0,\"lat\":33.7490,\"lon\":-84.3880}");
    summary = run("{\"action\":\"import_trees\",\"geojson\":" + geojson + "}");
    assertTrue(summary, summary.contains("Added 1 tree on the ground: 1 white_oak"));
    assertEquals(0, getPiece("White oak").getX(), 60);

    // A forest, the same at each call for the same seed, out of the house
    run("{\"action\":\"add_rectangular_room\",\"x\":2000,\"y\":2000,\"width\":500,\"depth\":400}");
    int count = getPlants().size();
    summary = run("{\"action\":\"forest_fill\",\"area\":[[1000,1000],[4000,1000],[4000,4000],[1000,4000]],"
        + "\"species\":{\"loblolly_pine\":3,\"sweetgum\":1},\"understory\":[\"flowering_dogwood\",\"azalea\"],\"spacing\":500,\"seed\":7}");
    assertTrue(summary, summary.contains("Planted") && summary.contains("loblolly_pine") && summary.contains("were skipped"));
    int planted = getPlants().size() - count;
    assertTrue(summary, planted > 30 && planted < 80);
    for (HomePieceOfFurniture plant : getPlants()) {
      assertFalse(plant.getName() + " " + plant.getX() + " " + plant.getY(),
          plant.getX() > 2050 && plant.getX() < 2450 && plant.getY() > 2050 && plant.getY() < 2350);
    }
    this.homeController.undo();
    assertEquals(count, getPlants().size());
    summary = run("{\"action\":\"add_trees\",\"along\":[[0,5000],[3000,5000]],\"species\":\"crape_myrtle\",\"spacing\":600}");
    assertTrue(summary, summary.contains("Planted 6 plants on the ground: 6 crape_myrtle"));

    // Catalog pieces replaced by trees of their size
    run("{\"action\":\"add_furniture\",\"name\":\"Plant\",\"x\":6000,\"y\":0,\"width\":300,\"depth\":300,\"height\":700,\"name_override\":\"Old tree\"}");
    HomePieceOfFurniture oldTree = getPiece("Old tree");
    summary = run("{\"action\":\"replace_furniture\",\"ids\":[\"" + HomeItemIds.getId(oldTree) + "\"],\"species\":\"southern_magnolia\"}");
    assertTrue(summary, summary.contains("Replaced 1 piece by southern_magnolia"));
    assertNull(getPiece("Old tree"));
    HomePieceOfFurniture magnolia = getPiece("Southern magnolia");
    assertEquals(700, magnolia.getHeight(), 40);
    assertEquals(6000, magnolia.getX(), 30);
  }
}
