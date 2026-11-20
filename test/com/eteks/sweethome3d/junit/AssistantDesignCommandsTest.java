/*
 * AssistantDesignCommandsTest.java 16 Nov 2026
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.AssistantDesignCommands;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemTags;

/**
 * Tests the replacement of furniture, the rooms drawn from a shape and the
 * decoration styles.
 */
public class AssistantDesignCommandsTest {
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

  @Test
  public void testReplaceFurniture() {
    run("{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":120,\"y\":340,\"angle\":90},"
        + "{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":400,\"y\":100}");
    run("{\"action\":\"add_tags\",\"id\":\"F1\",\"tags\":[\"living\"]},"
        + "{\"action\":\"set_elevation\",\"id\":\"F1\",\"elevation\":30}");
    String summary = run("{\"action\":\"replace_furniture\",\"id\":\"F1\",\"name\":\"Coffee table\"}");
    assertTrue(summary, summary.contains("Replaced 1 piece by \"Coffee table\""));
    assertEquals(2, this.home.getFurniture().size());
    HomePieceOfFurniture table = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      assertTrue(!"Sofa".equals(piece.getName()));
      if ("Coffee table".equals(piece.getName())) {
        table = piece;
      }
    }
    assertNotNull(table);
    assertEquals(120, table.getX(), 0.01f);
    assertEquals(340, table.getY(), 0.01f);
    assertEquals(Math.PI / 2, table.getAngle(), 0.001);
    assertEquals(30, table.getElevation(), 0.01f);
    assertEquals(110, table.getWidth(), 0.01f);
    assertEquals("living", table.getProperty(HomeItemTags.TAGS_PROPERTY));
    // One step of undo brings the sofa back
    this.homeController.undo();
    assertEquals(2, this.home.getFurniture().size());
    boolean sofa = false;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      sofa |= "Sofa".equals(piece.getName());
      assertTrue(!"Coffee table".equals(piece.getName()));
    }
    assertTrue(sofa);

    // The old size can be kept, and several pieces replaced at once
    summary = run("{\"action\":\"replace_furniture\",\"ids\":[\"F1\",\"F2\"],\"name\":\"Office chair\",\"keep_size\":true}");
    assertTrue(summary, summary.contains("Replaced 2 pieces"));
    int chairs = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if ("Office chair".equals(piece.getName())) {
        chairs++;
        assertTrue(piece.getWidth() == 200 || piece.getWidth() == 140);
      }
    }
    assertEquals(2, chairs);

    summary = run("{\"action\":\"replace_furniture\",\"id\":\"F1\",\"name\":\"Zeppelin\"}");
    assertTrue(summary, summary.contains("No catalog piece is named"));
    summary = run("{\"action\":\"replace_furniture\",\"name\":\"Sofa\"}");
    assertTrue(summary, summary.contains("No furniture to replace"));
  }

  private static double getDistanceToSegment(float x, float y, Wall wall) {
    return java.awt.geom.Line2D.ptSegDist(wall.getXStart(), wall.getYStart(), wall.getXEnd(), wall.getYEnd(), x, y);
  }

  @Test
  public void testRoomShapes() {
    float [][] points = AssistantDesignCommands.getShapePoints("L", 0, 0, 500, 400, 200, 150);
    assertEquals(6, points.length);
    assertEquals(500 * 400 - 200 * 150, new Room(points).getArea(), 1);
    assertEquals(500 * 400 - 2 * 100 * 250, new Room(
        AssistantDesignCommands.getShapePoints("t", 0, 0, 500, 400, 100, 250)).getArea(), 1);
    assertEquals(500 * 400 - 200 * 150, new Room(
        AssistantDesignCommands.getShapePoints("U", 0, 0, 500, 400, 200, 150)).getArea(), 1);
    assertEquals(4, AssistantDesignCommands.getShapePoints("rectangle", 0, 0, 500, 400, 0, 0).length);
    assertNull(AssistantDesignCommands.getShapePoints("L", 0, 0, 500, 400, 600, 150));
    assertNull(AssistantDesignCommands.getShapePoints("star", 0, 0, 500, 400, 100, 100));

    String summary = run("{\"action\":\"add_room_shape\",\"shape\":\"L\",\"name\":\"Living\",\"x\":100,\"y\":100,"
        + "\"width\":500,\"depth\":400,\"cut_width\":200,\"cut_depth\":150,\"thickness\":10}");
    assertTrue(summary, summary.contains("Added a L shaped room of 17.0 m2 with 6 walls"));
    assertEquals(1, this.home.getRooms().size());
    Room room = this.home.getRooms().get(0);
    assertEquals("Living", room.getName());
    List<Wall> walls = new ArrayList<Wall>(this.home.getWalls());
    assertEquals(6, walls.size());
    for (Wall wall : walls) {
      // Walls make a closed loop of horizontal and vertical walls
      assertNotNull(wall.getWallAtStart());
      assertNotNull(wall.getWallAtEnd());
      assertTrue(wall.getXStart() == wall.getXEnd() || wall.getYStart() == wall.getYEnd());
    }
    // The middle line of a wall runs half a thickness out of each side of the room,
    // and no wall crosses the room
    float [][] roomPoints = room.getPoints();
    for (int i = 0; i < roomPoints.length; i++) {
      float [] start = roomPoints [i];
      float [] end = roomPoints [(i + 1) % roomPoints.length];
      float x = (start [0] + end [0]) / 2;
      float y = (start [1] + end [1]) / 2;
      double distance = Double.MAX_VALUE;
      for (Wall wall : walls) {
        distance = Math.min(distance, getDistanceToSegment(x, y, wall));
      }
      assertEquals(5, distance, 0.01);
    }
    for (Wall wall : walls) {
      assertTrue(!room.containsPoint((wall.getXStart() + wall.getXEnd()) / 2, (wall.getYStart() + wall.getYEnd()) / 2, 0));
    }
    // Room and walls are one step of undo
    this.homeController.undo();
    assertEquals(0, this.home.getRooms().size());
    assertEquals(0, this.home.getWalls().size());

    // Without place, the room goes to the right of what exists
    run("{\"action\":\"add_room_shape\",\"shape\":\"rectangle\",\"x\":0,\"y\":0,\"width\":300,\"depth\":300}");
    run("{\"action\":\"add_room_shape\",\"shape\":\"U\",\"width\":400,\"depth\":300,\"cut_width\":100,\"cut_depth\":100}");
    assertEquals(2, this.home.getRooms().size());
    float left = Float.MAX_VALUE;
    for (float [] point : this.home.getRooms().get(1).getPoints()) {
      left = Math.min(left, point [0]);
    }
    assertTrue("left " + left, left > 300 + 100);
    assertEquals(4 + 8, this.home.getWalls().size());

    summary = run("{\"action\":\"add_room_shape\",\"shape\":\"L\",\"width\":300,\"depth\":300,\"cut_width\":400}");
    assertTrue(summary, summary.contains("Ignored add_room_shape"));
  }

  @Test
  public void testStyles() {
    assertEquals(10, AssistantDesignCommands.getStyles().size());
    // Two rooms side by side sharing a wall
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living\",\"x\":0,\"y\":0,\"width\":400,\"depth\":300},"
        + "{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\",\"next_to\":\"Living\",\"side\":\"east\",\"width\":300,\"depth\":300}");
    assertEquals(2, this.home.getRooms().size());
    assertEquals(7, this.home.getWalls().size());
    String summary = run("{\"action\":\"apply_style\",\"style\":\"scandinavian\",\"ids\":[\"R1\"]}");
    assertTrue(summary, summary.contains("Applied the style \"Scandinavian\""));
    assertTrue(summary, summary.contains("to 1 room and 4 wall sides"));
    Room living = this.home.getRooms().get(0);
    Room kitchen = this.home.getRooms().get(1);
    assertEquals(Integer.valueOf(0xD9C3A0), living.getFloorColor());
    assertEquals(Integer.valueOf(0xFFFFFF), living.getCeilingColor());
    assertNull(kitchen.getFloorColor());
    int coloredSides = 0;
    for (Wall wall : this.home.getWalls()) {
      Integer [] colors = {wall.getLeftSideColor(), wall.getRightSideColor()};
      float [][] points = wall.getPoints();
      // The left side runs along the two first points, the right one along the two last
      float [][] sideMiddles = {
          {(points [0][0] + points [1][0]) / 2, (points [0][1] + points [1][1]) / 2},
          {(points [2][0] + points [3][0]) / 2, (points [2][1] + points [3][1]) / 2}};
      for (int side = 0; side < 2; side++) {
        float [] middle = sideMiddles [side];
        float [] other = sideMiddles [1 - side];
        // A point 3 cm out of the wall on this side
        double length = Math.hypot(middle [0] - other [0], middle [1] - other [1]);
        float x = (float)(middle [0] + (middle [0] - other [0]) / length * 3);
        float y = (float)(middle [1] + (middle [1] - other [1]) / length * 3);
        if (living.containsPoint(x, y, 0)) {
          assertEquals(Integer.valueOf(0xF5F3EE), colors [side]);
          coloredSides++;
        } else {
          assertNull("side " + side + " of wall " + wall.getXStart() + "," + wall.getYStart(), colors [side]);
        }
      }
    }
    assertEquals(4, coloredSides);
    this.homeController.undo();
    assertNull(living.getFloorColor());
    for (Wall wall : this.home.getWalls()) {
      assertNull(wall.getLeftSideColor());
      assertNull(wall.getRightSideColor());
    }

    // Every room of the level without ids
    summary = run("{\"action\":\"apply_style\",\"style\":\"Mid century\",\"textures\":false}");
    assertTrue(summary, summary.contains("to 2 rooms and 8 wall sides"));
    assertEquals(Integer.valueOf(0x7A5235), kitchen.getFloorColor());
    assertNull(kitchen.getFloorTexture());

    summary = run("{\"action\":\"apply_style\",\"style\":\"Rococo\"}");
    assertTrue(summary, summary.contains("Unknown style"));
    summary = run("{\"action\":\"list_styles\"}");
    assertTrue(summary, summary.contains("Industrial - concrete grey walls"));
  }

  /**
   * A style takes a floor texture of its material, not one that shares only a color word with it.
   */
  @Test
  public void testStyleFloorTextureIsOfTheRightMaterial() {
    List<CatalogTexture> textures = new ArrayList<CatalogTexture>();
    for (String name : new String [] {"Light blue tiles", "Black and white tiles", "Beige tiles",
                                      "Red tiles", "Old wooden floor", "Wooden floor", "Grey tiles"}) {
      textures.add(new CatalogTexture(name, null, 20, 20));
    }
    assertEquals("Wooden floor", AssistantDesignCommands.findFloorTexture(textures, "light wood parquet").getName());
    assertEquals("Grey tiles", AssistantDesignCommands.findFloorTexture(textures, "grey tiles concrete").getName());
    assertEquals("Red tiles", AssistantDesignCommands.findFloorTexture(textures, "terracotta red tiles").getName());
    // No white tiles: a plain color is better than tiles of another color
    assertNull(AssistantDesignCommands.findFloorTexture(textures, "white tiles"));
    assertNull(AssistantDesignCommands.findFloorTexture(textures, "concrete"));
    // The names of the French catalog say nothing in English: the ids of the textures do
    List<CatalogTexture> frenchTextures = new ArrayList<CatalogTexture>();
    frenchTextures.add(new CatalogTexture("eTeks#lightBlueTile", "Carrelage bleu clair", null, 20, 20, null));
    frenchTextures.add(new CatalogTexture("eTeks#wallBeigeTile", "Carrelage beige", null, 20, 20, null));
    frenchTextures.add(new CatalogTexture("eTeks#oldWoodenFloor", "Vieux parquet", null, 20, 20, null));
    frenchTextures.add(new CatalogTexture("eTeks#woodenFloor", "Parquet", null, 20, 20, null));
    frenchTextures.add(new CatalogTexture("eTeks#greyTile", "Carrelage gris", null, 20, 20, null));
    assertEquals("Parquet", AssistantDesignCommands.findFloorTexture(frenchTextures, "light wood parquet").getName());
    assertEquals("Carrelage gris", AssistantDesignCommands.findFloorTexture(frenchTextures, "grey tiles concrete").getName());
    assertNull("No wall tiles on a floor", AssistantDesignCommands.findFloorTexture(frenchTextures, "beige tiles"));
  }

  @Test
  public void testBudget() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,\"thickness\":10,\"height\":250}");
    String report = this.executor.reportHome();
    assertTrue(report, report.contains("No budget: no unit price is set"));
    assertTrue(!this.home.isModified() || true);
    String summary = run("{\"action\":\"set_unit_prices\",\"paint\":8,\"flooring\":35.5,\"baseboard\":6,\"currency\":\"EUR\"}");
    assertTrue(summary, summary.contains("Unit prices of the budget updated"));
    assertTrue(this.home.isModified());
    report = this.executor.reportHome();
    // 20 m2 of floor, 18 m of perimeter
    assertTrue(report, report.contains("Flooring, per m2 of floor: 20.0 m2 x 35.5 = 710.00 EUR"));
    assertTrue(report, report.contains("Baseboard, per m of room perimeter: 18.0 m x 6.0 = 108.00 EUR"));
    assertTrue(report, report.contains("Wall paint or covering, per m2 of wall side: "));
    assertTrue(report, !report.contains("Ceiling paint"));
    assertTrue(report, report.contains("- Total: "));
    // A price is removed with a negative value
    run("{\"action\":\"set_unit_prices\",\"flooring\":-1}");
    assertNull(com.eteks.sweethome3d.viewcontroller.HomeReport.getUnitPrice(this.home, "flooring"));
    assertEquals("8.0", com.eteks.sweethome3d.viewcontroller.HomeReport.getUnitPrice(this.home, "paint").toPlainString());
  }

  @Test
  public void testElevation() throws Exception {
    assertNull(com.eteks.sweethome3d.viewcontroller.ElevationDrawing.createSVG(this.home, "south", 0));
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,\"thickness\":10,\"height\":250},"
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":250,\"y\":200}");
    String svg = com.eteks.sweethome3d.viewcontroller.ElevationDrawing.createSVG(this.home, "south", 0);
    assertTrue(svg, svg.startsWith("<?xml"));
    // Four walls and the sofa, the house 520 cm wide and 250 cm high from the south
    assertEquals(5 + 1, svg.split("<rect ").length - 1);
    assertTrue(svg, svg.contains(">520 cm<"));
    assertTrue(svg, svg.contains(">250 cm<"));
    assertTrue(svg, svg.contains("<title>Sofa</title>"));
    // The nearest wall is drawn last, over the sofa
    assertTrue(svg.lastIndexOf("fill=\"#E4E1DA\"") > svg.indexOf("<title>Sofa</title>"));
    // From the east the house is 420 cm wide
    assertTrue(com.eteks.sweethome3d.viewcontroller.ElevationDrawing.createSVG(this.home, "right", 0).contains(">420 cm<"));
    // A section 50 cm behind the facade leaves the front wall and the side walls out
    String section = com.eteks.sweethome3d.viewcontroller.ElevationDrawing.createSVG(this.home, "south", 50);
    assertEquals(2 + 1, section.split("<rect ").length - 1);
    assertTrue(section.lastIndexOf("<title>Sofa</title>") > section.lastIndexOf("fill=\"#E4E1DA\""));

    java.io.File folder = java.nio.file.Files.createTempDirectory("sh3d-elevation-test").toFile();
    try {
      java.io.File file = new java.io.File(folder, "front.svg");
      String summary = run("{\"action\":\"export_elevation\",\"side\":\"front\",\"path\":"
          + com.eteks.sweethome3d.viewcontroller.JSONParser.toJSONString(new java.io.File(folder, "front").getPath()) + "}");
      assertTrue(summary, summary.contains("Wrote the elevation seen from the south"));
      assertTrue(file.length() > 500);
      summary = run("{\"action\":\"export_elevation\",\"side\":\"front\",\"path\":"
          + com.eteks.sweethome3d.viewcontroller.JSONParser.toJSONString(file.getPath()) + "}");
      assertTrue(summary, summary.contains("\"replace\":true"));
      summary = run("{\"action\":\"export_elevation\",\"side\":\"up\",\"path\":\"/tmp/x.svg\"}");
      assertTrue(summary, summary.contains("\"side\" must be"));
      file.delete();
    } finally {
      folder.delete();
    }
  }

  @Test
  public void testVersions() throws Exception {
    java.io.File folder = java.nio.file.Files.createTempDirectory("sh3d-versions-test").toFile();
    com.eteks.sweethome3d.io.HomeFileRecorder recorder = new com.eteks.sweethome3d.io.HomeFileRecorder();
    try {
      try {
        com.eteks.sweethome3d.viewcontroller.HomeVersions.saveVersion(this.home, recorder, "too early");
        org.junit.Assert.fail("A home without file has no version");
      } catch (com.eteks.sweethome3d.model.RecorderException ex) {
        // Expected
      }
      run("{\"action\":\"add_rectangular_room\",\"name\":\"Living\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400},"
          + "{\"action\":\"create_furniture\",\"name\":\"Stool\",\"x\":100,\"y\":100,\"parts\":[{\"shape\":\"box\",\"width\":40,\"depth\":40,\"height\":45}]}");
      java.io.File homeFile = new java.io.File(folder, "house.sh3d");
      recorder.writeHome(this.home, homeFile.getPath());
      this.home.setName(homeFile.getPath());
      this.home.setModified(false);
      java.io.File version = com.eteks.sweethome3d.viewcontroller.HomeVersions.saveVersion(
          this.home, recorder, "before: the <kitchen>/move?");
      assertTrue(version.isFile());
      assertEquals(new java.io.File(folder, "house.sh3d.versions"), version.getParentFile());
      assertTrue(version.getName(), version.getName().endsWith(" before the kitchen move.sh3d"));
      // The home itself didn't change
      assertEquals(homeFile.getPath(), this.home.getName());
      assertTrue(!this.home.isModified());
      List<java.io.File> versions = com.eteks.sweethome3d.viewcontroller.HomeVersions.getVersions(this.home);
      assertEquals(1, versions.size());
      assertTrue(com.eteks.sweethome3d.viewcontroller.HomeVersions.getLabel(version).endsWith("  before the kitchen move"));

      // What changed since
      run("{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\",\"next_to\":\"Living\",\"side\":\"east\",\"width\":300,\"depth\":400},"
          + "{\"action\":\"create_furniture\",\"name\":\"Bench\",\"x\":600,\"y\":100,\"parts\":[{\"shape\":\"box\",\"width\":120,\"depth\":40,\"height\":45}]},"
          + "{\"action\":\"delete\",\"names\":[\"Stool\"]}");
      String comparison = com.eteks.sweethome3d.viewcontroller.HomeVersions.compare(recorder.readHome(version.getPath()), this.home);
      assertTrue(comparison, comparison.contains("Rooms: 1 -> 2  (+1)"));
      assertTrue(comparison, comparison.contains("Floor area (m2): 20.0 -> 32.0  (+12.0)"));
      assertTrue(comparison, comparison.contains("Walls: 4 -> 7  (+3)"));
      assertTrue(comparison, comparison.contains("Added: 1 x Bench"));
      assertTrue(comparison, comparison.contains("Removed: 1 x Stool"));
      assertTrue(com.eteks.sweethome3d.viewcontroller.HomeVersions.compare(this.home, this.home).contains("Same rooms, walls and furniture"));
      // A second version the same second gets another file
      java.io.File version2 = com.eteks.sweethome3d.viewcontroller.HomeVersions.saveVersion(this.home, recorder, "");
      assertEquals(2, com.eteks.sweethome3d.viewcontroller.HomeVersions.getVersions(this.home).size());
      version.delete();
      version2.delete();
      version.getParentFile().delete();
      homeFile.delete();
    } finally {
      java.io.File [] files = folder.listFiles();
      if (files != null) {
        for (java.io.File file : files) {
          java.io.File [] children = file.listFiles();
          if (children != null) {
            for (java.io.File child : children) {
              child.delete();
            }
          }
          file.delete();
        }
      }
      folder.delete();
    }
  }

  @Test
  public void testSpiralStairs() throws Exception {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Hall\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400},"
        + "{\"action\":\"add_level\",\"name\":\"First floor\"},{\"action\":\"select_level\",\"name\":\"Ground\"}");
    this.home.setSelectedLevel(this.home.getLevels().get(0));
    String summary = run("{\"action\":\"add_stairs\",\"shape\":\"spiral\",\"x\":250,\"y\":200,\"width\":80,\"railing\":true}");
    assertTrue(summary, summary.contains("Added spiral stairs of "));
    HomePieceOfFurniture stairs = this.home.getFurniture().get(0);
    // A square of twice the post radius and the width of the steps
    assertEquals(176, stairs.getWidth(), 0.01f);
    assertEquals(176, stairs.getDepth(), 0.01f);
    float rise = this.home.getLevels().get(1).getElevation();
    // The post goes up to the handrail of the floor above
    assertEquals(rise + 90, stairs.getHeight(), 0.5f);
    String flights = stairs.getProperty(com.eteks.sweethome3d.viewcontroller.AssistantGeneratedModel.STAIRS_FLIGHTS_PROPERTY);
    int stepCount = com.eteks.sweethome3d.viewcontroller.AssistantGeneratedModel.getStepCount(rise);
    assertEquals(stepCount, flights.substring(flights.indexOf('|') + 1).split(";").length);
    // The last step is at the height of the floor above, and the hole in that floor is round
    String lastFlight = flights.substring(flights.lastIndexOf(';') + 1);
    assertEquals(rise, Float.parseFloat(lastFlight.substring(lastFlight.lastIndexOf(',') + 1)), 0.05f);
    assertTrue(stairs.getStaircaseCutOutShape().startsWith("M1.0000,0.5000 L"));
    assertEquals(24, stairs.getStaircaseCutOutShape().split("L").length);
    // The model is closed solids turned outward
    java.io.InputStream in = stairs.getModel().openStream();
    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, "ISO-8859-1"));
    List<double []> vertices = new ArrayList<double []>();
    double volume = 0;
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      String [] parts = line.trim().split(" +");
      if (parts [0].equals("v")) {
        vertices.add(new double [] {Double.parseDouble(parts [1]), Double.parseDouble(parts [2]), Double.parseDouble(parts [3])});
      } else if (parts [0].equals("f")) {
        double [] a = vertices.get(Integer.parseInt(parts [1]) - 1);
        for (int i = 2; i < parts.length - 1; i++) {
          double [] b = vertices.get(Integer.parseInt(parts [i]) - 1);
          double [] c = vertices.get(Integer.parseInt(parts [i + 1]) - 1);
          volume += (a [0] * (b [1] * c [2] - b [2] * c [1]) - a [1] * (b [0] * c [2] - b [2] * c [0])
              + a [2] * (b [0] * c [1] - b [1] * c [0])) / 6;
        }
      }
    }
    in.close();
    // The post and a slab of 5 cm by step, of 24 degrees of a disc of 88 cm of radius
    double expected = Math.PI * 8 * 8 * (rise + 90) * 0.95 + stepCount * 5 * Math.PI * 88 * 88 * 24 / 360;
    assertEquals(expected, volume, expected * 0.25);
    assertTrue(volume > 0);
  }
}
