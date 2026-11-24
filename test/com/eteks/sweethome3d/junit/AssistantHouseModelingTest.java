/*
 * AssistantHouseModelingTest.java
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
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.FurniturePlanVisibility;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;
import com.eteks.sweethome3d.viewcontroller.HomeReport;

/**
 * Tests the commands that model a whole house: ids that don't change, levels,
 * walls, slabs, the ground, roofs over any polygon and furniture made of parts.
 */
public class AssistantHouseModelingTest {
  private String                   previousNo3D;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    Content dummy = new com.eteks.sweethome3d.tools.URLContent(new File("dummy.png").toURI().toURL());
    this.preferences.getFurnitureCatalog().add(new com.eteks.sweethome3d.model.FurnitureCategory("Test openings"),
        new com.eteks.sweethome3d.model.CatalogDoorOrWindow(null, "Zz roof window", null, dummy, dummy, 90, 20, 120, 80, true,
            1, 0, new com.eteks.sweethome3d.model.Sash [0], null, null, true, null, null));
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
  }

  @After
  public void tearDown() {
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

  private Level getLevel(String name) {
    for (Level level : this.home.getLevels()) {
      if (name.equals(level.getName())) {
        return level;
      }
    }
    return null;
  }

  @Test
  public void testIdsDontChange() throws Exception {
    run("{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100},"
        + "{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":300,\"y\":100},"
        + "{\"action\":\"add_furniture\",\"name\":\"Office chair\",\"x\":500,\"y\":100}");
    HomePieceOfFurniture chair = getPiece("Office chair");
    assertEquals("F3", HomeItemIds.getId(chair));
    // Deleting a piece doesn't shift the ids of the following ones
    String summary = run("{\"action\":\"delete\",\"id\":\"F2\"}");
    assertEquals(2, this.home.getFurniture().size());
    assertEquals("F3", HomeItemIds.getId(chair));
    summary = run("{\"action\":\"move\",\"id\":\"F3\",\"x\":520,\"y\":140}");
    assertEquals(520, chair.getX(), 0.01f);
    // The id of a deleted piece designates nothing, and isn't given again
    summary = run("{\"action\":\"move\",\"id\":\"F2\",\"dx\":10}");
    assertTrue(summary, summary.contains("No item has the id F2"));
    run("{\"action\":\"add_furniture\",\"name\":\"Coffee table\",\"x\":300,\"y\":300}");
    assertEquals("F4", HomeItemIds.getId(getPiece("Coffee table")));
    String description = HomeAssistantContext.describeHome(this.home);
    assertTrue(description, description.contains("- F3 \"Office chair\""));
    assertTrue(description, description.contains("- F4 \"Coffee table\""));
    assertFalse(description, description.contains("- F2 "));
    // The internal id of an object designates it too
    run("{\"action\":\"move\",\"id\":\"" + chair.getId() + "\",\"x\":600}");
    assertEquals(600, chair.getX(), 0.01f);

    // Ids are saved with the home, as the last one given
    Home wallsHome = new Home(250);
    Wall [] walls = {new Wall(0, 0, 100, 0, 10, 250), new Wall(0, 100, 100, 100, 10, 250), new Wall(0, 200, 100, 200, 10, 250)};
    for (Wall wall : walls) {
      wallsHome.addWall(wall);
    }
    HomeItemIds.ensureIds(wallsHome);
    assertEquals("W3", HomeItemIds.getId(walls [2]));
    wallsHome.deleteWall(walls [2]);
    File file = File.createTempFile("ids", ".sh3d");
    file.delete();
    file.deleteOnExit();
    new com.eteks.sweethome3d.io.HomeFileRecorder().writeHome(wallsHome, file.getPath());
    Home savedHome = new com.eteks.sweethome3d.io.HomeFileRecorder().readHome(file.getPath());
    assertEquals(2, savedHome.getWalls().size());
    assertNotNull(HomeItemIds.find(savedHome, "W2"));
    assertNull(HomeItemIds.find(savedHome, "W3"));
    Wall copy = (Wall)savedHome.getWalls().iterator().next().duplicate();
    savedHome.addWall(copy);
    HomeItemIds.ensureIds(savedHome);
    assertEquals("A copy gets an id of its own, that no other item had", "W4", HomeItemIds.getId(copy));

    // Items designated by criteria
    run("{\"action\":\"add_tags\",\"id\":\"F1\",\"tags\":[\"keep\"]}");
    run("{\"action\":\"set_elevation\",\"where\":{\"tag\":\"keep\"},\"elevation\":-20}");
    assertEquals(-20, getPiece("Sofa").getElevation(), 0.01f);
    summary = run("{\"action\":\"check_home\"}");
    assertFalse("A piece put under the floor on purpose is no defect: " + summary, summary.contains("under the floor"));
    run("{\"action\":\"move\",\"where\":{\"bbox\":[400,0,700,200],\"type\":\"furniture\"},\"dy\":50}");
    assertEquals(190, chair.getY(), 0.01f);
    assertEquals(100, getPiece("Sofa").getY(), 0.01f);
    summary = run("{\"action\":\"delete\",\"where\":{}}");
    assertTrue(summary, summary.contains("give at least one of"));
    assertEquals(3, this.home.getFurniture().size());
    summary = run("{\"action\":\"describe_item\",\"id\":\"F3\"}");
    assertTrue(summary, summary.contains("\"Office chair\"") && summary.contains("center (600, 190)")
        && summary.contains("in the plans of the levels above: draw"));
  }

  @Test
  public void testLevels() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Hall\",\"x\":0,\"y\":0,\"width\":400,\"depth\":300},"
        + "{\"action\":\"add_level\",\"name\":\"Helper\",\"elevation\":20,\"height\":250},"
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100,\"elevation\":5},"
        + "{\"action\":\"add_wall\",\"x1\":0,\"y1\":500,\"x2\":300,\"y2\":500},"
        + "{\"action\":\"add_level\",\"name\":\"Helper\",\"elevation\":300},"
        + "{\"action\":\"add_level\",\"name\":\"Attic\",\"elevation\":550}");
    assertEquals(4, this.home.getLevels().size());
    String description = HomeAssistantContext.describeHome(this.home);
    assertTrue(description, description.contains("L2 \"Helper\" at 20 cm, height 250, floor thickness"));
    assertTrue(description, description.contains("0 rooms, 1 wall, 1 piece of furniture"));
    assertTrue(description, description.contains("EMPTY"));
    assertTrue(description, description.contains("name shared with 1 other level"));
    // The filter of a description takes every level with a name, or the one with an id
    description = HomeAssistantContext.describeHome(this.home, "L2", null, null);
    assertTrue(description, description.contains("\"Sofa\""));
    assertFalse(description, description.contains("Hall:"));

    // A name two levels share doesn't designate one
    String summary = run("{\"action\":\"delete_level\",\"name\":\"Helper\"}");
    assertTrue(summary, summary.contains("2 levels are named \"Helper\"") && summary.contains("L2") && summary.contains("L3"));
    assertEquals(4, this.home.getLevels().size());
    // A level that isn't empty isn't deleted without saying where its contents go
    summary = run("{\"action\":\"delete_level\",\"id\":\"L2\"}");
    assertTrue(summary, summary.contains("isn't empty") && summary.contains("1 wall, 1 piece of furniture")
        && summary.contains("\"Sofa\""));
    assertEquals(4, this.home.getLevels().size());
    summary = run("{\"action\":\"delete_level\",\"id\":\"L3\"}");
    assertTrue(summary, summary.contains("Deleted the level L3"));
    assertEquals(3, this.home.getLevels().size());
    assertNotNull(HomeItemIds.find(this.home, "L4"));

    // Merging moves the contents, furniture keeping its height above the ground
    HomePieceOfFurniture sofa = getPiece("Sofa");
    Level ground = this.home.getLevels().get(0);
    summary = run("{\"action\":\"merge_levels\",\"from\":\"Helper\",\"into\":\"L1\"}");
    assertTrue(summary, summary.contains("Moved 1 wall, 1 piece of furniture"));
    assertEquals(2, this.home.getLevels().size());
    assertEquals(ground, sofa.getLevel());
    assertEquals(25, sofa.getElevation(), 0.01f);
    assertEquals(5, this.home.getWalls().size());
    for (Wall wall : this.home.getWalls()) {
      assertEquals(ground, wall.getLevel());
    }
    // One step of undo brings the level and its contents back
    this.homeController.undo();
    assertEquals(3, this.home.getLevels().size());
    assertEquals("Helper", sofa.getLevel().getName());
    assertEquals(5, sofa.getElevation(), 0.01f);
    this.homeController.redo();
    assertEquals(2, this.home.getLevels().size());
    assertEquals(25, sofa.getElevation(), 0.01f);

    summary = run("{\"action\":\"modify_level\",\"name\":\"Attic\",\"new_name\":\"Level 1\",\"elevation\":262,"
        + "\"height\":240,\"floor_thickness\":12,\"viewable\":false}");
    Level level = getLevel("Level 1");
    assertNotNull(summary, level);
    assertEquals(262, level.getElevation(), 0.01f);
    assertEquals(240, level.getHeight(), 0.01f);
    assertEquals(12, level.getFloorThickness(), 0.01f);
    assertFalse(level.isViewable());
    assertTrue(HomeAssistantContext.describeHome(this.home).contains("not viewable"));
    this.homeController.undo();
    assertEquals("Attic", level.getName());
    assertEquals(550, level.getElevation(), 0.01f);
    assertTrue(level.isViewable());

    // Everything of a level moved in one command
    run("{\"action\":\"move_to_level\",\"where\":{\"level\":\"L1\",\"type\":\"furniture\"},\"level\":\"Attic\"}");
    assertEquals("Attic", sofa.getLevel().getName());
  }

  @Test
  public void testWallsAndTextures() {
    run("{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":400,\"y2\":0,\"thickness\":20,\"height\":250},"
        + "{\"action\":\"add_room\",\"name\":\"Yard\",\"points\":[[0,0],[400,0],[400,300],[0,300]]}");
    Wall wall = this.home.getWalls().iterator().next();
    String summary = run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"height\":260,\"height_at_end\":420,\"thickness\":25,"
        + "\"top_color\":\"#102030\",\"baseboard\":{\"height\":90,\"thickness\":2,\"color\":\"#884422\",\"side\":\"left\"}}");
    assertEquals(260, wall.getHeight(), 0.01f);
    assertEquals(420, wall.getHeightAtEnd(), 0.01f);
    assertEquals(25, wall.getThickness(), 0.01f);
    assertEquals(Integer.valueOf(0x102030), wall.getTopColor());
    assertNotNull(wall.getLeftSideBaseboard());
    assertEquals(90, wall.getLeftSideBaseboard().getHeight(), 0.01f);
    assertNull(wall.getRightSideBaseboard());
    assertTrue(summary, summary.contains("height 260 at start and 420 at end") && summary.contains("left baseboard 90 high"));
    this.homeController.undo();
    assertEquals(250, wall.getHeight(), 0.01f);
    assertNull(wall.getHeightAtEnd());
    assertNull(wall.getLeftSideBaseboard());
    assertNull(wall.getTopColor());
    this.homeController.redo();
    run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"height_at_end\":0,\"arc_extent\":90,\"baseboard\":false}");
    assertNull(wall.getHeightAtEnd());
    assertEquals(Math.PI / 2, wall.getArcExtent(), 0.001f);
    assertNull(wall.getLeftSideBaseboard());
    summary = run("{\"action\":\"describe_item\",\"id\":\"W1\"}");
    assertTrue(summary, summary.contains("arc of 90 deg") && summary.contains("top color #102030"));

    // A texture with a scale and an angle
    String textureName = this.preferences.getTexturesCatalog().getCategories().get(0).getTextures().get(0).getName();
    summary = run("{\"action\":\"set_texture\",\"id\":\"R1\",\"texture\":\"" + textureName + "\",\"category\":\""
        + this.preferences.getTexturesCatalog().getCategories().get(0).getName() + "\",\"scale\":2,\"angle\":45}");
    Room room = this.home.getRooms().get(0);
    assertNotNull(summary, room.getFloorTexture());
    assertEquals(2, room.getFloorTexture().getScale(), 0.001f);
    assertEquals(Math.PI / 4, room.getFloorTexture().getAngle(), 0.001f);
    assertTrue(summary, summary.contains("scale 2") && summary.contains("turned by 45 deg"));
  }

  @Test
  public void testSlabsAndTerrain() throws Exception {
    // A slab under the floor of the level
    String summary = run("{\"action\":\"add_slab\",\"name\":\"Driveway\",\"points\":[[0,0],[600,0],[600,300],[300,300],[300,500],[0,500]],"
        + "\"top\":-5,\"thickness\":15,\"color\":\"#777777\"}");
    HomePieceOfFurniture slab = getPiece("Driveway");
    assertNotNull(summary, slab);
    assertEquals(-20, slab.getElevation(), 0.01f);
    assertEquals(15, slab.getHeight(), 0.01f);
    assertEquals(300, slab.getX(), 0.01f);
    assertEquals(250, slab.getY(), 0.01f);
    assertEquals(600, slab.getWidth(), 0.01f);
    assertEquals(500, slab.getDepth(), 0.01f);
    double area = 600 * 300 + 300 * 200;
    assertEquals(area * 15, getVolume(slab.getModel()), area * 15 * 0.001);
    assertTrue(summary, summary.contains("24 m2"));
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("Driveway"));

    // A ramp: the top goes through three points
    summary = run("{\"action\":\"add_slab\",\"name\":\"Ramp\",\"points\":[[0,600],[400,600],[400,800],[0,800]],"
        + "\"thickness\":10,\"top_at\":[[0,600,0],[400,600,40],[0,800,0]]}");
    HomePieceOfFurniture ramp = getPiece("Ramp");
    assertNotNull(summary, ramp);
    assertEquals(-10, ramp.getElevation(), 0.01f);
    assertEquals(50, ramp.getHeight(), 0.01f);
    assertEquals(400 * 200 * 30, getVolume(ramp.getModel()), 400 * 200 * 30 * 0.001);
    // The plan draws it seen from the top, and a visitor walks on the triangles of its top
    assertNotNull(ramp.getPlanIcon());
    assertTrue(FurniturePlanVisibility.isDrawnFromTop(ramp));
    String rampScene = com.eteks.sweethome3d.plugin.webxr.WebXRSceneDescription.toJSON(this.home);
    assertTrue(rampScene, rampScene.contains("\"ramps\":[["));
    assertTrue(rampScene, rampScene.contains("0.4000"));

    // A room with a raised floor
    summary = run("{\"action\":\"add_room\",\"name\":\"Stage\",\"points\":[[1000,0],[1300,0],[1300,300],[1000,300]],"
        + "\"floor_offset\":40,\"slab_thickness\":40}");
    Room stage = this.home.getRooms().get(0);
    assertFalse(summary, stage.isFloorVisible());
    HomePieceOfFurniture floor = getPiece("Stage floor");
    assertNotNull(summary, floor);
    assertEquals(0, floor.getElevation(), 0.01f);
    assertEquals(40, floor.getHeight(), 0.01f);
    run("{\"action\":\"modify_room\",\"room\":\"Stage\",\"floor_offset\":-30,\"slab_thickness\":10}");
    assertEquals("The slab of the room is replaced", 3, this.home.getFurniture().size());
    assertEquals(-40, getPiece("Stage floor").getElevation(), 0.01f);

    // The ground through points of known height
    summary = run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,0],[2000,-1000,0],[2000,2000,300],[-1000,2000,300]],"
        + "\"areas\":[{\"points\":[[0,0],[500,0],[500,500],[0,500]],\"color\":\"#999999\"}]}");
    HomePieceOfFurniture terrain = getPiece("Terrain");
    assertNotNull(summary, terrain);
    assertEquals(3000, terrain.getWidth(), 0.5f);
    assertEquals(330, terrain.getHeight(), 0.5f);
    // The ground stands on a level of its own under the others, where the 3D view draws no flat ground
    assertEquals(2, this.home.getLevels().size());
    Level terrainLevel = this.home.getLevels().get(0);
    assertEquals(terrain.getLevel(), terrainLevel);
    assertEquals(-50, terrainLevel.getElevation(), 0.01f);
    assertEquals(-30, terrainLevel.getElevation() + terrain.getElevation(), 0.5f);
    assertTrue(this.home.getSelectedLevel() != terrainLevel);
    assertEquals(FurniturePlanVisibility.HIDE, FurniturePlanVisibility.get(terrain));
    assertTrue(summary, summary.contains("heights from 0 to 300"));
    run("{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":500,\"y\":1000,\"on_terrain\":false}");
    run("{\"action\":\"set_elevation\",\"id\":\"" + HomeItemIds.getId(getPiece("Desk")) + "\",\"on_terrain\":true}");
    assertEquals(200, getPiece("Desk").getElevation(), 0.5f);
    // Undo takes the piece off the ground, and redo puts it back, with what tells it stands outdoors
    assertEquals("true", getPiece("Desk").getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
    this.homeController.undo();
    assertEquals(0, getPiece("Desk").getElevation(), 0.5f);
    assertNull(getPiece("Desk").getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
    this.homeController.redo();
    assertEquals(200, getPiece("Desk").getElevation(), 0.5f);
    assertEquals("true", getPiece("Desk").getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
    // A second ground replaces the first one
    run("{\"action\":\"set_terrain\",\"grid\":{\"x\":0,\"y\":0,\"width\":1000,\"depth\":1000,\"heights\":[[0,0,0],[50,60,50],[100,100,100]]}}");
    int terrains = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      terrains += piece.getName().equals("Terrain") ? 1 : 0;
    }
    assertEquals(1, terrains);
    assertEquals("The level of the ground is used again", 2, this.home.getLevels().size());
    // The ground is left open under a room whose floor it reaches, not under one far above it
    run("{\"action\":\"add_room\",\"name\":\"Hall\",\"points\":[[200,200],[600,200],[600,600],[200,600]]}");
    summary = run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,0],[2000,-1000,0],[2000,2000,0],[-1000,2000,0]]}");
    assertTrue(summary, summary.contains("left open under 1 room"));
    // The ground follows the rooms: its model is built again when a room is added, and after undo
    com.eteks.sweethome3d.model.Content openModel = getPiece("Terrain").getModel();
    run("{\"action\":\"add_room\",\"name\":\"Shed\",\"points\":[[900,900],[1200,900],[1200,1200],[900,1200]]}");
    com.eteks.sweethome3d.model.Content widerModel = getPiece("Terrain").getModel();
    assertFalse(openModel.equals(widerModel));
    this.homeController.undo();
    assertFalse(widerModel.equals(getPiece("Terrain").getModel()));
    // A change that moves no room leaves it alone
    com.eteks.sweethome3d.model.Content model = getPiece("Terrain").getModel();
    run("{\"action\":\"add_label\",\"text\":\"Garden\",\"x\":0,\"y\":0}");
    assertTrue(model == getPiece("Terrain").getModel());
    summary = run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,-200],[2000,-1000,-200],[2000,2000,-200],[-1000,2000,-200]]}");
    assertFalse(summary, summary.contains("left open"));
    assertEquals("The level of the higher ground isn't kept empty", 2, this.home.getLevels().size());
    run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,0],[2000,-1000,0],[2000,2000,300],[-1000,2000,300]]}");
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("Terrain"));
    // What was put on the ground stands outdoors on purpose, and so does a part of the building
    assertFalse(summary, summary.contains("Desk"));
    run("{\"action\":\"create_furniture\",\"name\":\"Chimney\",\"x\":100,\"y\":100,\"elevation\":300,\"structure\":true,"
        + "\"parts\":[{\"shape\":\"box\",\"width\":60,\"depth\":60,\"height\":200}]}");
    run("{\"action\":\"create_furniture\",\"name\":\"Kite\",\"x\":100,\"y\":300,\"elevation\":300,"
        + "\"parts\":[{\"shape\":\"box\",\"width\":60,\"depth\":60,\"height\":200}]}");
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("Chimney"));
    assertTrue(summary, summary.contains("Kite"));
    // The ground is described to the walk-through and the game mode by its triangles
    String scene = com.eteks.sweethome3d.plugin.webxr.WebXRSceneDescription.toJSON(this.home);
    assertTrue(scene, scene.contains("\"terrain\":[[-10.0000,-10.0000,0.0000,"));
    // An elevation draws the faces of the chimney and the ground rather than their boxes
    String elevation = com.eteks.sweethome3d.viewcontroller.ElevationDrawing.createSVG(this.home, "south", 0);
    assertTrue(elevation, elevation.contains("<polygon points="));
    assertTrue(elevation, elevation.contains("clip-path=\"url(#site)\""));
    // The report lists the ground and the chimney with the building, not with the furniture
    String report = HomeReport.describe(this.home);
    assertTrue(report, report.indexOf("1 x Kite") < report.indexOf("Building and site"));
    assertTrue(report, report.indexOf("1 x Chimney") > report.indexOf("Building and site"));
    assertTrue(report, report.indexOf("1 x Terrain") > report.indexOf("Building and site"));
    // A piece given an elevation again is checked like any other
    run("{\"action\":\"set_elevation\",\"id\":\"" + HomeItemIds.getId(getPiece("Desk")) + "\",\"elevation\":400}");
    summary = run("{\"action\":\"check_home\"}");
    assertTrue(summary, summary.contains("Desk"));
  }

  @Test
  public void testFurnitureParts() throws Exception {
    String summary = run("{\"action\":\"create_furniture\",\"name\":\"Shapes\",\"x\":0,\"y\":0,\"parts\":["
        + "{\"shape\":\"prism\",\"x\":0,\"y\":0,\"z\":0,\"height\":10,\"points\":[[0,0],[100,0],[100,50],[50,50],[50,100],[0,100]]},"
        + "{\"shape\":\"wedge\",\"x\":300,\"y\":0,\"z\":0,\"width\":100,\"depth\":60,\"height\":40,\"high_side\":\"left\"},"
        + "{\"shape\":\"wedge\",\"x\":500,\"y\":0,\"z\":0,\"width\":100,\"depth\":60,\"height\":40,\"ridge\":\"x\"}]}");
    HomePieceOfFurniture shapes = getPiece("Shapes");
    assertNotNull(summary, shapes);
    double volume = 7500 * 10 + 100 * 60 * 40 / 2. + 100 * 60 * 40 / 2.;
    assertEquals(volume, getVolume(shapes.getModel()), volume * 0.001);
    assertEquals(550, shapes.getWidth(), 0.01f);
    assertEquals(40, shapes.getHeight(), 0.01f);

    // A bar following a path, and a part turned around its width
    summary = run("{\"action\":\"create_furniture\",\"name\":\"Rail\",\"x\":0,\"y\":500,\"parts\":["
        + "{\"shape\":\"extrude\",\"z\":90,\"width\":4,\"height\":4,\"path\":[[0,0,0],[200,0,0],[200,100,0]]},"
        + "{\"shape\":\"box\",\"x\":0,\"y\":300,\"z\":0,\"width\":100,\"depth\":10,\"height\":100,\"pitch\":90}]}");
    HomePieceOfFurniture rail = getPiece("Rail");
    assertNotNull(summary, rail);
    // The box laid down by its pitch is 10 high and 100 deep, 45 cm above the floor
    assertEquals(47, rail.getHeight(), 0.01f);
    assertEquals(45, rail.getElevation(), 0.01f);
    assertEquals(352, rail.getDepth(), 0.01f);

    // The parts of a piece can be changed afterwards
    float x = shapes.getX();
    summary = run("{\"action\":\"modify_furniture_parts\",\"id\":\"" + HomeItemIds.getId(shapes)
        + "\",\"set\":[{\"index\":1,\"height\":20}],\"remove\":[3],"
        + "\"add\":[{\"shape\":\"box\",\"x\":-100,\"y\":50,\"z\":0,\"width\":50,\"depth\":100,\"height\":5}]}");
    assertTrue(summary, summary.contains("Rebuilt") && summary.contains("3 parts"));
    volume = 7500 * 20 + 100 * 60 * 40 / 2. + 50 * 100 * 5;
    assertEquals(volume, getVolume(shapes.getModel()), volume * 0.001);
    assertEquals(475, shapes.getWidth(), 0.01f);
    // The first part is still where it was in the home
    assertEquals(x - 275 + (-125 + 350) / 2f, shapes.getX(), 0.01f);
    this.homeController.undo();
    assertEquals(550, shapes.getWidth(), 0.01f);
    assertEquals(x, shapes.getX(), 0.01f);
    summary = run("{\"action\":\"modify_furniture_parts\",\"id\":\"" + HomeItemIds.getId(shapes) + "\",\"remove\":[9]}");
    assertTrue(summary, summary.contains("between 1 and 3"));
  }

  @Test
  public void testRoofs() throws Exception {
    // A hip roof over a rectangle: its ridge is as high as half its smallest side allows
    String summary = run("{\"action\":\"add_roof\",\"name\":\"Hip\",\"points\":[[0,0],[800,0],[800,600],[0,600]],"
        + "\"eave_height\":250,\"overhang\":0,\"pitch\":30,\"type\":\"hip\"}");
    HomePieceOfFurniture hip = getPiece("Hip");
    assertNotNull(summary, hip);
    double rise = 300 * Math.tan(Math.toRadians(30));
    assertEquals(250, hip.getElevation(), 0.05f);
    assertEquals(rise + 15, hip.getHeight(), 0.05f);
    assertEquals(400, hip.getX(), 0.05f);
    assertEquals(800, hip.getWidth(), 0.05f);
    assertTrue(summary, summary.contains("4 eaves and 0 gables"));
    assertEquals(FurniturePlanVisibility.OUTLINE, FurniturePlanVisibility.get(hip));
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("Hip"));

    // With an overhang, the eaves go lower and further than the walls
    run("{\"action\":\"add_roof\",\"name\":\"Gable\",\"points\":[[0,1000],[800,1000],[800,1600],[0,1600]],"
        + "\"eave_height\":250,\"overhang\":30,\"pitch\":30,\"type\":\"gable\",\"gutters\":true,\"ridge_cap\":true,\"soffit\":true}");
    HomePieceOfFurniture gable = getPiece("Gable");
    assertNotNull(gable);
    assertEquals(860, gable.getWidth(), 1f);
    // With the gutters hung at both eaves
    assertEquals(684, gable.getDepth(), 1f);
    // The downspouts go down to the ground
    assertEquals(0, gable.getElevation(), 0.05f);
    assertEquals(250 + rise + 15 + 3, gable.getHeight(), 0.1f);

    // The wings of an L shaped house are two parts that meet
    summary = run("{\"action\":\"add_roof\",\"name\":\"Wings\",\"points\":[[2000,0],[3000,0],[3000,400],[2400,400],[2400,1000],[2000,1000]],"
        + "\"eave_height\":250,\"overhang\":0,\"edges\":[{\"type\":\"hip\"},{\"type\":\"gable\"},{\"type\":\"hip\"},"
        + "{\"type\":\"hip\"},{\"type\":\"gable\",\"pitch\":30},{\"type\":\"hip\"}]}");
    HomePieceOfFurniture wings = getPiece("Wings");
    assertNotNull(summary, wings);
    assertTrue(summary, summary.contains("4 eaves and 2 gables") && summary.contains("in 2 parts"));
    assertEquals(200 * Math.tan(Math.toRadians(30)) + 15, wings.getHeight(), 0.05f);

    // A dormer raises the roof where it stands
    summary = run("{\"action\":\"add_dormer\",\"roof\":\"" + HomeItemIds.getId(hip) + "\",\"side\":\"south\",\"width\":150,"
        + "\"height\":80,\"type\":\"gable\",\"window\":\"Zz roof window\"}");
    assertTrue(summary, summary.contains("Added the dormer 1") && summary.contains("1 window"));
    assertEquals(rise + 15, hip.getHeight(), 0.05f);
    HomePieceOfFurniture window = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.isDoorOrWindow()) {
        window = piece;
      }
    }
    assertNotNull(window);
    assertEquals(400, window.getX(), 0.05f);
    assertEquals(560, window.getY(), 0.05f);
    // The window looks south
    assertEquals(0, Math.sin(window.getAngle()), 0.001);
    assertEquals(1, Math.cos(window.getAngle()), 0.001);
    assertTrue(window.getElevation() > 250 + 15);
    this.homeController.undo();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      assertFalse(piece.isDoorOrWindow());
    }
    summary = run("{\"action\":\"add_dormer\",\"roof\":\"" + HomeItemIds.getId(gable) + "\",\"side\":\"west\"}");
    assertTrue(summary, summary.contains("with a slope rising from it"));
  }

  @Test
  public void testWallsFittedToRoof() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"House\",\"x\":10,\"y\":10,\"width\":780,\"depth\":580,"
        + "\"thickness\":20,\"height\":250}");
    assertEquals(4, this.home.getWalls().size());
    String summary = run("{\"action\":\"add_roof\",\"points\":[[-10,-10],[810,-10],[810,610],[-10,610]],"
        + "\"overhang\":40,\"pitch\":30,\"type\":\"gable\",\"gable_walls\":false}");
    assertTrue(summary, summary.contains("eaves at 250 cm"));
    summary = run("{\"action\":\"fit_walls_to_roof\"}");
    assertTrue(summary, summary.contains("Fitted 4 walls") && summary.contains("2 walls were cut"));
    assertEquals(6, this.home.getWalls().size());
    double eave = 250 + 10 * Math.tan(Math.toRadians(30));
    double peak = 250 + 310 * Math.tan(Math.toRadians(30));
    int slopedWalls = 0;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getHeightAtEnd() != null) {
        slopedWalls++;
        assertEquals(eave, Math.min(wall.getHeight(), wall.getHeightAtEnd()), 0.1);
        assertEquals(peak, Math.max(wall.getHeight(), wall.getHeightAtEnd()), 0.1);
        assertEquals(300, wall.getLength(), 0.1f);
        assertTrue(wall.getWallAtStart() != null && wall.getWallAtEnd() != null);
      } else {
        // Measured half a centimeter inside the roof
        assertEquals(eave, wall.getHeight(), 0.5);
        assertEquals(800, wall.getLength(), 0.1f);
      }
    }
    assertEquals(4, slopedWalls);
    // One step of undo brings the four walls back
    this.homeController.undo();
    assertEquals(4, this.home.getWalls().size());
    for (Wall wall : this.home.getWalls()) {
      assertEquals(250, wall.getHeight(), 0.01f);
      assertNull(wall.getHeightAtEnd());
      assertTrue(wall.getWallAtStart() != null && wall.getWallAtEnd() != null);
    }
    this.homeController.redo();
    assertEquals(6, this.home.getWalls().size());
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("don't meet"));
  }

  @Test
  public void testPlanVisibility() {
    run("{\"action\":\"add_level\",\"name\":\"Level 1\",\"elevation\":262},"
        + "{\"action\":\"select_level\",\"name\":\"L1\"},"
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100},"
        + "{\"action\":\"add_tags\",\"id\":\"F1\",\"tags\":[\"roof\"]}");
    HomePieceOfFurniture sofa = getPiece("Sofa");
    Level level0 = this.home.getLevels().get(0);
    Level level1 = this.home.getLevels().get(1);
    assertEquals(level0, sofa.getLevel());
    // What is tagged roof is outlined at the levels above, and at its own level, where it's above the rooms
    assertEquals(FurniturePlanVisibility.OUTLINE, FurniturePlanVisibility.get(sofa));
    assertEquals(FurniturePlanVisibility.OUTLINE, FurniturePlanVisibility.getAtLevel(sofa, level0));
    run("{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":300,\"y\":100,\"elevation\":20}");
    HomePieceOfFurniture desk = getPiece("Desk");
    run("{\"action\":\"resize\",\"id\":\"F2\",\"height\":400},"
        + "{\"action\":\"set_plan_visibility\",\"ids\":[\"F2\"],\"value\":\"hide\"}");
    assertEquals("A piece is drawn in the plan of its level", FurniturePlanVisibility.DRAW, FurniturePlanVisibility.getAtLevel(desk, level0));
    assertEquals(FurniturePlanVisibility.HIDE, FurniturePlanVisibility.getAtLevel(desk, level1));
    assertEquals(FurniturePlanVisibility.OUTLINE, FurniturePlanVisibility.getAtLevel(sofa, level1));
    String summary = run("{\"action\":\"set_plan_visibility\",\"tag\":\"roof\",\"value\":\"hide\"}");
    assertTrue(summary, summary.contains("don't show anymore 1 piece"));
    assertEquals(FurniturePlanVisibility.HIDE, FurniturePlanVisibility.getAtLevel(sofa, level1));
    this.homeController.undo();
    assertEquals(FurniturePlanVisibility.OUTLINE, FurniturePlanVisibility.getAtLevel(sofa, level1));
    run("{\"action\":\"set_plan_visibility\",\"ids\":[\"F1\"],\"value\":\"draw\"}");
    assertEquals(FurniturePlanVisibility.DRAW, FurniturePlanVisibility.getAtLevel(sofa, level1));
    summary = run("{\"action\":\"set_plan_visibility\",\"ids\":[\"F1\"],\"value\":\"blue\"}");
    assertTrue(summary, summary.contains("must be \"draw\", \"outline\" or \"hide\""));
  }

  /**
   * Returns the volume enclosed by the faces of an OBJ model, positive if
   * they are turned outward.
   */
  private static double getVolume(Content model) throws Exception {
    List<double []> vertices = new ArrayList<double []>();
    double volume = 0;
    InputStream in = model.openStream();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        String [] parts = line.trim().split(" +");
        if (parts [0].equals("v")) {
          vertices.add(new double [] {Double.parseDouble(parts [1]), Double.parseDouble(parts [2]),
                                      Double.parseDouble(parts [3])});
        } else if (parts [0].equals("f")) {
          double [] a = vertices.get(Integer.parseInt(parts [1].split("/") [0]) - 1);
          for (int i = 2; i < parts.length - 1; i++) {
            double [] b = vertices.get(Integer.parseInt(parts [i].split("/") [0]) - 1);
            double [] c = vertices.get(Integer.parseInt(parts [i + 1].split("/") [0]) - 1);
            volume += (a [0] * (b [1] * c [2] - b [2] * c [1])
                - a [1] * (b [0] * c [2] - b [2] * c [0])
                + a [2] * (b [0] * c [1] - b [1] * c [0])) / 6;
          }
        }
      }
    } finally {
      in.close();
    }
    return volume;
  }
}
