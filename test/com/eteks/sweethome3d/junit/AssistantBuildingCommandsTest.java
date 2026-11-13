/*
 * AssistantBuildingCommandsTest.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
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

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.AssistantRoofBuilder;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Tests the commands of the assistant that build and repair a home: rooms
 * with walls, levels, roofs, colors, annotations, checks and fixes.
 */
public class AssistantBuildingCommandsTest {
  private String                   previousNo3D;
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.homeController = new HomeController(this.home, preferences, new SwingViewFactory());
    // Undo support is bound to the home once its view exists
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, preferences, this.homeController);
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

  @Test
  public void testRectangularRoomsShareWalls() {
    String summary = run("{\"action\":\"add_rectangular_room\",\"name\":\"Living room\","
        + "\"x\":0,\"y\":0,\"width\":500,\"depth\":400,\"thickness\":10}");
    assertNotNull(summary);
    assertEquals(1, this.home.getRooms().size());
    assertEquals("Living room", this.home.getRooms().get(0).getName());
    assertEquals(200000f, this.home.getRooms().get(0).getArea(), 1f);
    List<Wall> walls = new ArrayList<Wall>(this.home.getWalls());
    assertEquals(4, walls.size());
    for (Wall wall : walls) {
      assertNotNull("Walls are joined at both ends", wall.getWallAtStart());
      assertNotNull(wall.getWallAtEnd());
      assertEquals(10f, wall.getThickness(), 0);
    }
    String report = this.executor.checkHome();
    assertTrue("The only defect of the new room is its missing door: " + report,
        report.contains("1 issue") && report.contains("[room_without_door] R1 \"Living room\""));

    // A second room against the right side of the first one reuses its wall
    summary = run("{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\","
        + "\"x\":510,\"y\":0,\"width\":300,\"depth\":400,\"thickness\":10}");
    assertTrue(summary, summary.contains("shares 1 existing wall"));
    assertEquals(2, this.home.getRooms().size());
    assertEquals(7, this.home.getWalls().size());

    // Each command is undone with its turn
    this.homeController.undo();
    assertEquals(1, this.home.getRooms().size());
    assertEquals(4, this.home.getWalls().size());
    this.homeController.undo();
    assertTrue(this.home.getRooms().isEmpty());
    assertTrue(this.home.getWalls().isEmpty());
    assertFalse(this.home.isModified());

    assertTrue(run("{\"action\":\"add_rectangular_room\",\"x\":0,\"y\":0,\"width\":-5,\"depth\":400}")
        .contains("Ignored add_rectangular_room"));
    assertTrue(this.home.getRooms().isEmpty());
  }

  @Test
  public void testAutoRoof() throws Exception {
    run("{\"action\":\"add_rectangular_room\",\"x\":0,\"y\":0,\"width\":800,\"depth\":600,\"thickness\":20,\"height\":250}");
    String summary = run("{\"action\":\"auto_roof\",\"type\":\"gable\",\"pitch\":45,\"overhang\":30}");
    assertTrue(summary, summary.contains("gable roof"));

    // The roof is on a level of its own, on top of the walls
    assertEquals(2, this.home.getLevels().size());
    Level roofLevel = this.home.getLevels().get(1);
    assertEquals("Roof", roofLevel.getName());
    assertEquals(250f, roofLevel.getElevation(), 0.01f);
    assertEquals("The level of the rooms stays selected", this.home.getLevels().get(0), this.home.getSelectedLevel());
    assertTrue("The 3D view shows the roof", this.home.getEnvironment().isAllLevelsVisible());
    assertEquals(1, this.home.getFurniture().size());
    HomePieceOfFurniture roof = this.home.getFurniture().get(0);
    assertEquals(roofLevel, roof.getLevel());
    // Walls are 840 x 640 outside, plus the overhang on each side
    assertEquals(900f, roof.getWidth(), 0.01f);
    assertEquals(700f, roof.getDepth(), 0.01f);
    assertEquals("Ridge along the longest side: rises half the span at 45 degrees", 350f, roof.getHeight(), 0.01f);
    assertEquals(400f, roof.getX(), 0.01f);
    assertEquals(300f, roof.getY(), 0.01f);
    assertEquals("true", roof.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY));

    // The model is a readable OBJ file whose bounds are the size of the roof
    InputStream in = roof.getModel().openStream();
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
    float minY = Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    int faces = 0;
    for (String line; (line = reader.readLine()) != null; ) {
      if (line.startsWith("v ")) {
        String [] values = line.split(" ");
        maxX = Math.max(maxX, Float.parseFloat(values [1]));
        minY = Math.min(minY, Float.parseFloat(values [2]));
        maxY = Math.max(maxY, Float.parseFloat(values [2]));
      } else if (line.startsWith("f ")) {
        faces++;
      }
    }
    reader.close();
    assertEquals(450f, maxX, 0.01f);
    assertEquals(0f, minY, 0.01f);
    assertEquals(350f, maxY, 0.01f);
    assertEquals(5, faces);
    assertNotNull(roof.getIcon().openStream());

    // Asking again replaces the roof instead of stacking a second one
    this.home.setSelectedLevel(this.home.getLevels().get(0));
    summary = run("{\"action\":\"auto_roof\",\"type\":\"hip\",\"pitch\":30,\"ridge\":\"y\"}");
    assertTrue(summary, summary.contains("hip roof"));
    assertEquals(2, this.home.getLevels().size());
    assertEquals(1, this.home.getFurniture().size());
    assertEquals((float)(350 * Math.tan(Math.toRadians(30))), this.home.getFurniture().get(0).getHeight(), 0.01f);

    // The home with its roof can be saved and read again
    File file = File.createTempFile("roof", ".sh3d");
    try {
      new HomeFileRecorder().writeHome(this.home, file.getAbsolutePath());
      Home readHome = new HomeFileRecorder().readHome(file.getAbsolutePath());
      assertEquals(1, readHome.getFurniture().size());
      assertNotNull(readHome.getFurniture().get(0).getModel().openStream());
    } finally {
      file.delete();
    }

    this.homeController.undo();
    this.homeController.undo();
    assertTrue("Roofs undone", this.home.getFurniture().isEmpty());

    assertTrue(run("{\"action\":\"auto_roof\",\"type\":\"dome\"}").contains("type must be"));
    assertEquals(AssistantRoofBuilder.Type.SHED, AssistantRoofBuilder.Type.parse("lean-to"));
    assertEquals(20f, AssistantRoofBuilder.getRoofHeight(AssistantRoofBuilder.Type.FLAT, 500, 400, 30, true), 0);
    assertEquals(400f, AssistantRoofBuilder.getRoofHeight(AssistantRoofBuilder.Type.SHED, 500, 400, 45, true), 0.01f);
  }

  @Test
  public void testRoomsNextToEachOtherAndRoofOfAnLShapedHouse() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living room\",\"x\":0,\"y\":0,\"width\":600,\"depth\":450,\"thickness\":20}");
    // Against the east side, then against the north side with an offset
    String summary = run("{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\",\"next_to\":\"R1\",\"side\":\"east\",\"width\":350,\"depth\":450},"
        + "{\"action\":\"add_rectangular_room\",\"name\":\"Bedroom\",\"next_to\":\"living room\",\"side\":\"north\",\"offset\":100,\"width\":400,\"depth\":350}");
    assertTrue(summary, summary.contains("shares 1 existing wall"));
    assertEquals(3, this.home.getRooms().size());
    Room kitchen = this.home.getRooms().get(1);
    assertEquals("Kitchen starts after the shared wall", 620f, kitchen.getPoints() [0][0], 0.01f);
    assertEquals(0f, kitchen.getPoints() [0][1], 0.01f);
    Room bedroom = this.home.getRooms().get(2);
    assertEquals(100f, bedroom.getPoints() [0][0], 0.01f);
    assertEquals(-370f, bedroom.getPoints() [0][1], 0.01f);
    assertEquals("Two rooms share a wall with the first one", 10, this.home.getWalls().size());
    for (Wall wall : this.home.getWalls()) {
      assertEquals("New walls take the thickness of the shared wall", 20f, wall.getThickness(), 0);
    }
    assertTrue(run("{\"action\":\"add_rectangular_room\",\"next_to\":\"R9\",\"width\":300,\"depth\":300}")
        .contains("must be the id or the name of a room"));

    // The house is L shaped: each room gets its roof rather than one over the empty corner
    summary = run("{\"action\":\"auto_roof\",\"type\":\"hip\"}");
    assertTrue(summary, summary.contains("each room got its own roof"));
    assertEquals(3, this.home.getFurniture().size());
    // A single roof can still be asked for
    summary = run("{\"action\":\"auto_roof\",\"type\":\"hip\",\"per_room\":false}");
    assertEquals("The roofs were replaced", 1, this.home.getFurniture().size());
    assertEquals(970f + 2 * 20 + 2 * 30, this.home.getFurniture().get(0).getWidth(), 0.01f);
  }

  @Test
  public void testLevelsColorsAndAnnotations() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Hall\",\"x\":0,\"y\":0,\"width\":400,\"depth\":300}");
    Room room = this.home.getRooms().get(0);
    Wall wall = this.home.getWalls().iterator().next();

    String summary = run("{\"action\":\"set_color\",\"id\":\"R1\",\"color\":\"#336699\"},"
        + "{\"action\":\"set_color\",\"id\":\"R1\",\"color\":\"#FFFFFF\",\"part\":\"ceiling\"},"
        + "{\"action\":\"set_color\",\"id\":\"W1\",\"color\":\"#102030\",\"part\":\"left\"},"
        + "{\"action\":\"add_label\",\"text\":\"Entrance\",\"x\":200,\"y\":150},"
        + "{\"action\":\"add_dimension\",\"x1\":0,\"y1\":0,\"x2\":400,\"y2\":0}");
    assertNotNull(summary);
    assertEquals(Integer.valueOf(0x336699), room.getFloorColor());
    assertEquals(Integer.valueOf(0xFFFFFF), room.getCeilingColor());
    assertEquals(Integer.valueOf(0x102030), wall.getLeftSideColor());
    assertNull(wall.getRightSideColor());
    assertEquals(1, this.home.getLabels().size());
    assertEquals(1, this.home.getDimensionLines().size());
    assertEquals(400f, this.home.getDimensionLines().iterator().next().getLength(), 0.01f);
    this.homeController.undo();
    assertNull(room.getFloorColor());
    assertNull(wall.getLeftSideColor());
    assertTrue(this.home.getLabels().isEmpty());
    assertTrue(this.home.getDimensionLines().isEmpty());

    summary = run("{\"action\":\"add_level\",\"name\":\"First floor\",\"elevation\":262,\"height\":240},"
        + "{\"action\":\"add_rectangular_room\",\"name\":\"Bedroom\",\"x\":0,\"y\":0,\"width\":400,\"depth\":300}");
    assertTrue(summary, summary.contains("Added level \"First floor\""));
    assertEquals(2, this.home.getLevels().size());
    Level firstFloor = this.home.getLevels().get(1);
    assertEquals(262f, firstFloor.getElevation(), 0);
    assertEquals(240f, firstFloor.getHeight(), 0);
    assertEquals("The new room is on the new level", firstFloor, this.home.getRooms().get(1).getLevel());
    assertEquals("Walls of the level below aren't shared", 8, this.home.getWalls().size());

    summary = run("{\"action\":\"select_level\",\"index\":1}");
    assertEquals(this.home.getLevels().get(0), this.home.getSelectedLevel());
    assertTrue(run("{\"action\":\"select_level\",\"name\":\"Cellar\"}").contains("no such level"));

    // The visitor looks toward +y with a yaw of 0, and toward the point it's asked to look at
    run("{\"action\":\"set_camera\",\"x\":200,\"y\":150,\"height\":170,\"look_at_x\":200,\"look_at_y\":300}");
    assertEquals(200f, this.home.getObserverCamera().getX(), 0);
    assertEquals(170f, this.home.getObserverCamera().getZ(), 0.01f);
    assertEquals(0f, this.home.getObserverCamera().getYaw(), 1e-4f);
    run("{\"action\":\"set_camera\",\"look_at_x\":0,\"look_at_y\":150}");
    assertEquals((float)(Math.PI / 2), this.home.getObserverCamera().getYaw(), 1e-4f);
  }

  @Test
  public void testDuplicate() throws Exception {
    HomePieceOfFurniture sofa = new HomePieceOfFurniture(this.executor.findCatalogPiece("Sofa", false));
    sofa.setX(100);
    sofa.setY(100);
    this.home.addPieceOfFurniture(sofa);
    run("{\"action\":\"duplicate\",\"id\":\"F1\",\"dx\":250,\"dy\":0,\"count\":2}");
    assertEquals(3, this.home.getFurniture().size());
    assertEquals(350f, this.home.getFurniture().get(1).getX(), 0.01f);
    assertEquals(600f, this.home.getFurniture().get(2).getX(), 0.01f);
    assertEquals("Sofa", this.home.getFurniture().get(2).getName());
    this.homeController.undo();
    assertEquals(1, this.home.getFurniture().size());
  }

  @Test
  public void testCheckAndFixHome() throws Exception {
    // Two walls that almost meet, a wall drawn twice, a speck of wall and an empty room
    Wall wall1 = new Wall(0, 0, 400, 0, 10, 250);
    Wall wall2 = new Wall(405, 3, 405, 300, 10, 250);
    Wall wall3 = new Wall(400, 0, 0, 0, 10, 250);
    Wall wall4 = new Wall(100, 100, 100.2f, 100, 10, 250);
    this.home.addWall(wall1);
    this.home.addWall(wall2);
    this.home.addWall(wall3);
    this.home.addWall(wall4);
    this.home.addRoom(new Room(new float [][] {{0, 0}, {10, 0}, {20, 0}}));
    // A sofa under the floor and its exact copy
    HomePieceOfFurniture sofa = new HomePieceOfFurniture(this.executor.findCatalogPiece("Sofa", false));
    sofa.setX(200);
    sofa.setY(150);
    sofa.setElevation(-30);
    this.home.addPieceOfFurniture(sofa);
    HomePieceOfFurniture sofaCopy = new HomePieceOfFurniture(this.executor.findCatalogPiece("Sofa", false));
    sofaCopy.setX(200);
    sofaCopy.setY(150);
    sofaCopy.setElevation(-30);
    this.home.addPieceOfFurniture(sofaCopy);

    String report = this.executor.checkHome();
    assertTrue(report, report.contains("[wall_gap] W2 and W1 end"));
    assertTrue(report, report.contains("[duplicate_wall] W3 is drawn over W1"));
    assertTrue(report, report.contains("[tiny_wall] W4"));
    assertTrue(report, report.contains("[empty_room] R1"));
    assertTrue(report, report.contains("[furniture_below_floor] F1"));
    assertTrue(report, report.contains("[duplicate_furniture] F2 \"Sofa\" is a copy of F1"));
    assertTrue(report, report.contains("can be fixed"));
    assertTrue("The same report through a command", run("{\"action\":\"check_home\"}").contains("[wall_gap]"));
    assertFalse("Checking changes nothing", this.home.isModified());

    String summary = run("{\"action\":\"fix_home\"}");
    assertTrue(summary, summary.contains("Fixed"));
    assertEquals("Duplicate and tiny walls removed", 2, this.home.getWalls().size());
    assertEquals(wall1, wall2.getWallAtStart());
    assertEquals(wall2, wall1.getWallAtEnd());
    assertEquals("The end of the second wall moved to the end of the first", 400f, wall2.getXStart(), 0);
    assertEquals(0f, wall2.getYStart(), 0);
    assertTrue(this.home.getRooms().isEmpty());
    assertEquals(1, this.home.getFurniture().size());
    assertEquals(0f, sofa.getElevation(), 0);
    String fixedReport = this.executor.checkHome();
    assertFalse(fixedReport, fixedReport.contains("fixable"));

    // One undo brings every defect back
    this.homeController.undo();
    assertEquals(4, this.home.getWalls().size());
    assertNull(wall2.getWallAtStart());
    assertNull(wall1.getWallAtEnd());
    assertEquals(405f, wall2.getXStart(), 0);
    assertEquals(1, this.home.getRooms().size());
    assertEquals(2, this.home.getFurniture().size());
    assertEquals(-30f, sofa.getElevation(), 0);

    // Only the asked types are fixed
    run("{\"action\":\"fix_home\",\"types\":[\"tiny_wall\"]}");
    assertEquals(3, this.home.getWalls().size());
    assertEquals(-30f, sofa.getElevation(), 0);
    assertEquals("Nothing to fix.", run("{\"action\":\"fix_home\",\"types\":[\"tiny_wall\"]}"));
  }
}
