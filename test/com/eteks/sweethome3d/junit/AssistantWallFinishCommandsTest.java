/*
 * AssistantWallFinishCommandsTest.java 25 nov. 2026
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;
import com.eteks.sweethome3d.viewcontroller.WallFinishBands;

/**
 * Tests the commands that finish and open walls: walls painted from the rooms they face,
 * finishes that change with the height of a wall side, openings without door, headers and soffits.
 */
public class AssistantWallFinishCommandsTest {
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

  private Room getRoom(String name) {
    for (Room room : this.home.getRooms()) {
      if (name.equals(room.getName())) {
        return room;
      }
    }
    return null;
  }

  private Wall getWall(String id) {
    for (Wall wall : this.home.getWalls()) {
      if (id.equals(HomeItemIds.getId(wall))) {
        return wall;
      }
    }
    return null;
  }

  private HomePieceOfFurniture getPiece(String id) {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (id.equals(HomeItemIds.getId(piece))) {
        return piece;
      }
    }
    return null;
  }

  /**
   * Returns the room in front of the middle of a side of a wall, or <code>null</code>.
   */
  private Room getRoomInFrontOf(Wall wall, boolean leftSide) {
    float [][] points = wall.getPoints();
    float [] start = leftSide ? points [0] : points [points.length - 1];
    float [] end = leftSide ? points [points.length / 2 - 1] : points [points.length / 2];
    float [] otherStart = leftSide ? points [points.length - 1] : points [0];
    float x = (start [0] + end [0]) / 2;
    float y = (start [1] + end [1]) / 2;
    double dx = start [0] - otherStart [0];
    double dy = start [1] - otherStart [1];
    double length = Math.hypot(dx, dy);
    x += dx / length * 8;
    y += dy / length * 8;
    for (Room room : this.home.getRooms()) {
      if (room.containsPoint(x, y, 0)) {
        return room;
      }
    }
    return null;
  }

  private void addTwoRooms() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living room\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,\"thickness\":10},"
        + "{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\",\"x\":510,\"y\":0,\"width\":300,\"depth\":400,\"thickness\":10}");
    assertEquals(2, this.home.getRooms().size());
    assertEquals(7, this.home.getWalls().size());
  }

  /**
   * Counts the wall sides painted with <code>color</code>, checked to face <code>room</code>.
   */
  private int countSides(int color, Room room) {
    int count = 0;
    for (Wall wall : this.home.getWalls()) {
      for (int side = 0; side < 2; side++) {
        Integer sideColor = side == 0 ? wall.getLeftSideColor() : wall.getRightSideColor();
        if (sideColor != null && (sideColor.intValue() & 0xFFFFFF) == color) {
          count++;
          assertEquals("The side painted faces the room", room, getRoomInFrontOf(wall, side == 0));
        }
      }
    }
    return count;
  }

  @Test
  public void testWallsPaintedFromTheirRooms() {
    addTwoRooms();
    Room living = getRoom("Living room");
    Room kitchen = getRoom("Kitchen");
    // The color of the walls is stored in the room, and the sides that face it get it
    String summary = run("{\"action\":\"modify_room\",\"room\":\"Living room\",\"wall_color\":\"#D5AC6B\"}");
    assertTrue(summary, summary.contains("Painted 4 wall sides"));
    assertEquals("D5AC6B", living.getProperty("com.eteks.sweethome3d.wallColor"));
    assertEquals(4, countSides(0xD5AC6B, living));
    assertNull(kitchen.getProperty("com.eteks.sweethome3d.wallColor"));
    this.homeController.undo();
    assertNull(living.getProperty("com.eteks.sweethome3d.wallColor"));
    assertEquals(0, countSides(0xD5AC6B, living));
    this.homeController.redo();
    assertEquals(4, countSides(0xD5AC6B, living));

    // The rooms without color aren't painted, and are named
    summary = run("{\"action\":\"paint_rooms\"}");
    assertTrue(summary, summary.contains("Painted 0 wall sides"));
    assertTrue(summary, summary.contains("4 sides that had a color or a texture kept it"));
    assertTrue(summary, summary.contains("\"Kitchen\""));
    assertTrue(summary, summary.contains("have no wall color"));
    assertTrue("The 6 sides of the outside are named: " + summary, summary.contains("6 sides face no room"));

    // A color for the rooms that have none: only the unpainted sides get it
    summary = run("{\"action\":\"paint_rooms\",\"color\":\"#FFFFFF\",\"colour\":\"red\"}");
    assertTrue(summary, summary.contains("Painted 4 wall sides"));
    assertTrue(summary, summary.contains("doesn't know [colour]"));
    assertEquals(4, countSides(0xFFFFFF, kitchen));
    assertEquals(4, countSides(0xD5AC6B, living));
    for (Wall wall : this.home.getWalls()) {
      for (int side = 0; side < 2; side++) {
        if (getRoomInFrontOf(wall, side == 0) == null) {
          assertNull("Outside sides keep their finish", side == 0 ? wall.getLeftSideColor() : wall.getRightSideColor());
        }
      }
    }

    // All the sides of one room, with its own color changed
    kitchen.setProperty("com.eteks.sweethome3d.wallColor", "336699");
    summary = run("{\"action\":\"paint_rooms\",\"rooms\":[\"Kitchen\"],\"only\":\"all\"}");
    assertTrue(summary, summary.contains("Painted 4 wall sides"));
    assertEquals(4, countSides(0x336699, kitchen));
    assertEquals(4, countSides(0xD5AC6B, living));
    this.homeController.undo();
    assertEquals(4, countSides(0xFFFFFF, kitchen));

    // A texture of the catalog
    String texture = this.preferences.getTexturesCatalog().getCategories().get(0).getTextures().get(0).getName();
    summary = run("{\"action\":\"modify_room\",\"id\":\"" + HomeItemIds.getId(kitchen) + "\",\"wall_texture\":\"" + texture + "\"}");
    assertEquals(texture, kitchen.getProperty("com.eteks.sweethome3d.wallTexture"));
    assertNull(kitchen.getProperty("com.eteks.sweethome3d.wallColor"));
    int textured = 0;
    for (Wall wall : this.home.getWalls()) {
      textured += (wall.getLeftSideTexture() != null ? 1 : 0) + (wall.getRightSideTexture() != null ? 1 : 0);
    }
    assertEquals(summary, 4, textured);

    assertTrue(run("{\"action\":\"paint_rooms\",\"rooms\":[\"Garage\"]}").contains("no room has the id or the name \"Garage\""));
    assertTrue(run("{\"action\":\"paint_rooms\",\"only\":\"some\"}").contains("\"only\" is"));
    assertTrue(run("{\"action\":\"modify_room\",\"room\":\"Kitchen\",\"wall_color\":\"notacolor\"}").contains("isn't a color"));
    // Removed from the room: the walls keep their finish
    summary = run("{\"action\":\"modify_room\",\"room\":\"Living room\",\"wall_color\":\"none\"}");
    assertNull(living.getProperty("com.eteks.sweethome3d.wallColor"));
    assertEquals(4, countSides(0xD5AC6B, living));
  }

  /**
   * A wall of the level above a room open to it is painted as that room.
   */
  @Test
  public void testWallsAboveARoomOpenToAbove() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Foyer\",\"x\":0,\"y\":0,\"width\":400,\"depth\":400,\"thickness\":10}");
    Room foyer = getRoom("Foyer");
    run("{\"action\":\"add_level\",\"name\":\"Upstairs\",\"elevation\":262,\"height\":250},"
        + "{\"action\":\"add_wall\",\"x1\":-5,\"y1\":-5,\"x2\":405,\"y2\":-5,\"thickness\":10}");
    assertEquals(5, this.home.getWalls().size());
    Wall upperWall = null;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() != foyer.getLevel()) {
        upperWall = wall;
      }
    }
    assertNotNull(upperWall);
    run("{\"action\":\"modify_room\",\"room\":\"Foyer\",\"wall_color\":\"#AABBCC\"}");
    assertNull("A room with its ceiling isn't open to the level above", upperWall.getLeftSideColor());
    assertNull(upperWall.getRightSideColor());
    // Without ceiling, the room is open to the level above
    foyer.setCeilingVisible(false);
    String summary = run("{\"action\":\"paint_rooms\"}");
    assertTrue(summary, summary.contains("Painted 1 wall side"));
    assertTrue((upperWall.getLeftSideColor() != null) != (upperWall.getRightSideColor() != null));
    // With a ceiling lower than the floor of the level above, it isn't
    upperWall.setLeftSideColor(null);
    upperWall.setRightSideColor(null);
    foyer.setProperty("com.eteks.sweethome3d.ceilingHeight", "244");
    summary = run("{\"action\":\"paint_rooms\"}");
    assertTrue(summary, summary.contains("Painted 0 wall sides"));
    foyer.setProperty("com.eteks.sweethome3d.ceilingHeight", "512");
    summary = run("{\"action\":\"paint_rooms\"}");
    assertTrue(summary, summary.contains("Painted 1 wall side"));
  }

  @Test
  public void testFinishesChangingWithHeight() throws Exception {
    addTwoRooms();
    Wall wall = getWall("W1");
    String texture = this.preferences.getTexturesCatalog().getCategories().get(0).getTextures().get(0).getName();
    String summary = run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"left_finish\":[{\"to\":120,\"color\":\"#FF0000\"},"
        + "{\"to\":200,\"to_at_end\":230,\"texture\":\"" + texture + "\",\"scale\":2},{\"color\":\"#00FF00\"}]}");
    assertTrue(summary, summary.contains("The left side of W1 shows from its floor #FF0000 up to 120 cm"));
    List<WallFinishBands.Band> bands = WallFinishBands.getBands(wall, true);
    assertEquals(2, bands.size());
    assertEquals(120, bands.get(0).getTop(), 0);
    assertEquals(120, bands.get(0).getTopAtEnd(), 0);
    assertEquals(0xFF0000, bands.get(0).getColor().intValue() & 0xFFFFFF);
    assertNull(bands.get(0).getTexture());
    assertEquals(200, bands.get(1).getTop(), 0);
    assertEquals(230, bands.get(1).getTopAtEnd(), 0);
    assertNotNull(bands.get(1).getTexture());
    assertEquals(texture, bands.get(1).getTexture().getName());
    assertEquals(2, bands.get(1).getTexture().getScale(), 0);
    assertNotNull(bands.get(1).getTexture().getImage());
    assertEquals("The finish of the top is the one of the wall side", 0x00FF00, wall.getLeftSideColor().intValue() & 0xFFFFFF);
    assertTrue(WallFinishBands.getBands(wall, false).isEmpty());
    assertNull(wall.getLeftSideBaseboard());
    summary = run("{\"action\":\"describe_item\",\"id\":\"W1\"}");
    assertTrue(summary, summary.contains("left side finishes from the floor: color #FF0000 up to 120, texture \"" + texture
        + "\" up to 200 at start and 230 at end"));

    // Saved with the home and read again
    File file = File.createTempFile("finishes", ".sh3d");
    try {
      HomeFileRecorder recorder = new HomeFileRecorder();
      recorder.writeHome(this.home, file.getPath());
      Home readHome = recorder.readHome(file.getPath());
      Wall readWall = null;
      for (Wall other : readHome.getWalls()) {
        if ("W1".equals(HomeItemIds.getId(other))) {
          readWall = other;
        }
      }
      List<WallFinishBands.Band> readBands = WallFinishBands.getBands(readWall, true);
      assertEquals(2, readBands.size());
      assertEquals(texture, readBands.get(1).getTexture().getName());
      assertNotNull(readBands.get(1).getTexture().getImage());
      assertEquals(230, readBands.get(1).getTopAtEnd(), 0);
    } finally {
      file.delete();
    }

    // Undone as one step, with the color of the top
    this.homeController.undo();
    assertTrue(WallFinishBands.getBands(wall, true).isEmpty());
    assertNull(wall.getLeftSideColor());
    this.homeController.redo();
    assertEquals(2, WallFinishBands.getBands(wall, true).size());

    // The other side, without changing the finish of its top; unknown keys are told
    wall.setRightSideColor(Integer.valueOf(0x112233));
    summary = run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"right_finish\":[{\"to\":90,\"color\":\"#0000FF\",\"upto\":3}]}");
    assertTrue(summary, summary.contains("don't know [upto]"));
    assertEquals(1, WallFinishBands.getBands(wall, false).size());
    assertEquals(0x112233, wall.getRightSideColor().intValue());
    assertEquals(2, WallFinishBands.getBands(wall, true).size());

    // Refused
    assertTrue(run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"left_finish\":[{\"to\":200,\"color\":\"#FF0000\"},{\"to\":100,\"color\":\"#00FF00\"}]}")
        .contains("each \"to\" must be higher"));
    assertTrue(run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"left_finish\":[{\"to\":10,\"color\":\"#111111\"},{\"to\":20,\"color\":\"#111111\"},"
        + "{\"to\":30,\"color\":\"#111111\"},{\"to\":40,\"color\":\"#111111\"},{\"to\":50,\"color\":\"#111111\"}]}").contains("4 finishes at most"));
    assertTrue(run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"left_finish\":[{\"to\":100}]}").contains("needs a \"color\" or a \"texture\""));
    assertTrue(run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"left_finish\":[{\"to\":\"roof\",\"color\":\"#111111\"}]}").contains("no roof"));
    assertEquals("Nothing changed", 2, WallFinishBands.getBands(wall, true).size());

    // Removed
    summary = run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"left_finish\":false}");
    assertTrue(summary, summary.contains("Removed the finishes"));
    assertTrue(WallFinishBands.getBands(wall, true).isEmpty());
    assertNull(wall.getProperty(WallFinishBands.LEFT_SIDE_PROPERTY));
    assertEquals(0x00FF00, wall.getLeftSideColor().intValue() & 0xFFFFFF);
  }

  /**
   * A two-story wall next to a one-story wing is painted under the roof of the wing.
   */
  @Test
  public void testFinishUnderLowerRoof() {
    run("{\"action\":\"add_wall\",\"x1\":405,\"y1\":0,\"x2\":405,\"y2\":300,\"thickness\":10,\"height\":520}");
    Wall wall = this.home.getWalls().iterator().next();
    for (String type : new String [] {"hip", "gable"}) {
      String summary = run("{\"action\":\"add_roof\",\"points\":[[0,0],[400,0],[400,300],[0,300]],\"eave_height\":250,"
          + "\"overhang\":0,\"pitch\":30,\"type\":\"" + type + "\",\"gutters\":false}");
      assertEquals(summary, 1, this.home.getFurniture().size());
      // The side that looks at the wing
      float [][] points = wall.getPoints();
      boolean leftSide = points [0][0] < 405;
      String side = leftSide ? "left" : "right";
      summary = run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"" + side + "_finish\":[{\"to\":\"roof\",\"color\":\"#D5AC6B\"}]}");
      assertTrue(summary, summary.contains("under the roof"));
      List<WallFinishBands.Band> bands = WallFinishBands.getBands(wall, leftSide);
      assertEquals(summary, 1, bands.size());
      assertTrue(type + ": " + bands.get(0).getTop(), bands.get(0).getTop() > 200 && bands.get(0).getTop() <= 256);
      assertEquals(bands.get(0).getTop(), bands.get(0).getTopAtEnd(), 3);
      // The other side looks at no roof
      summary = run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"" + (leftSide ? "right" : "left")
          + "_finish\":[{\"to\":\"roof\",\"color\":\"#D5AC6B\"}]}");
      assertTrue(summary, summary.contains("no roof stands in front of"));
      run("{\"action\":\"modify_wall\",\"id\":\"W1\",\"" + side + "_finish\":false},{\"action\":\"delete\",\"ids\":[\""
          + HomeItemIds.getId(this.home.getFurniture().get(0)) + "\"]}");
      assertTrue(this.home.getFurniture().isEmpty());
    }
  }

  @Test
  public void testOpeningsHeadersAndSoffits() {
    addTwoRooms();
    // The wall between both rooms
    Wall shared = null;
    for (Wall wall : this.home.getWalls()) {
      if (getRoomInFrontOf(wall, true) != null && getRoomInFrontOf(wall, false) != null) {
        shared = wall;
      }
    }
    assertNotNull(shared);
    String wallId = HomeItemIds.getId(shared);
    float length = shared.getLength();
    String summary = run("{\"action\":\"add_opening\",\"wall\":\"" + wallId + "\",\"from\":100,\"to\":280,\"top\":210,\"hinge\":\"left\"}");
    assertTrue(summary, summary.contains("Opened " + wallId + " without door nor window: 180 cm wide"));
    assertTrue(summary, summary.contains("doesn't know [hinge]"));
    assertEquals(1, this.home.getFurniture().size());
    HomePieceOfFurniture opening = this.home.getFurniture().get(0);
    assertTrue(opening instanceof HomeDoorOrWindow);
    assertEquals("Opening", opening.getName());
    assertEquals(180, opening.getWidth(), 0.01f);
    assertEquals(210, opening.getHeight(), 0.01f);
    assertEquals(0, opening.getElevation(), 0.01f);
    assertTrue("As deep as the wall", opening.getDepth() >= shared.getThickness());
    assertEquals("The whole piece cuts the wall", "M0,0 v1 h1 v-1 z", ((HomeDoorOrWindow)opening).getCutOutShape());
    assertEquals(0, ((HomeDoorOrWindow)opening).getSashes().length);
    // In the wall, 190 cm from its start
    double ux = (shared.getXEnd() - shared.getXStart()) / length;
    double uy = (shared.getYEnd() - shared.getYStart()) / length;
    assertEquals(shared.getXStart() + ux * 190, opening.getX(), 0.01);
    assertEquals(shared.getYStart() + uy * 190, opening.getY(), 0.01);
    assertTrue(shared.containsPoint(opening.getX(), opening.getY(), 0));
    assertEquals(shared.getLevel(), opening.getLevel());
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + wallId + "\"}");
    assertTrue(summary, summary.contains("openings without door: " + HomeItemIds.getId(opening) + " from 100 to 280 cm"));
    assertFalse("Not a door of the schedules", run("{\"action\":\"door_schedule\"}").contains("Opening"));

    // A header over it, up to the ceiling
    String openingId = HomeItemIds.getId(opening);
    summary = run("{\"action\":\"add_header\",\"opening\":\"" + openingId + "\",\"bottom\":200,\"color\":\"#FFFFFF\"}");
    assertTrue(summary, summary.contains("Added the header"));
    assertTrue(summary, summary.contains("180 cm long"));
    assertEquals(2, this.home.getFurniture().size());
    HomePieceOfFurniture header = this.home.getFurniture().get(1);
    assertEquals(200, header.getElevation(), 0.01f);
    assertEquals(50, header.getHeight(), 0.01f);
    assertEquals(opening.getX(), header.getX(), 0.01f);
    assertEquals(opening.getY(), header.getY(), 0.01f);
    assertEquals(180, Math.max(header.getWidth(), header.getDepth()), 0.5f);

    // Both follow the wall when it moves
    float x = opening.getX();
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + wallId + "\",\"x1\":" + (shared.getXStart() + 40)
        + ",\"x2\":" + (shared.getXEnd() + 40) + "}");
    assertTrue(summary, summary.contains("Moved with their wall: 1 opening and 1 header"));
    assertEquals(x + 40, opening.getX(), 0.01f);
    assertEquals(x + 40, header.getX(), 0.01f);
    this.homeController.undo();
    assertEquals(x, opening.getX(), 0.01f);
    assertEquals(x, header.getX(), 0.01f);

    // The openings of a wall replaced at once, then removed
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + wallId + "\",\"openings\":[{\"center\":100,\"width\":80,\"bottom\":90,\"height\":100},"
        + "{\"from\":250,\"to\":350}]}");
    assertTrue(summary, summary.contains("has 2 openings without door nor window"));
    assertTrue(summary, summary.contains("They replace the 1 it had"));
    int openings = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece instanceof HomeDoorOrWindow) {
        openings++;
        assertTrue(piece.getElevation() == 0 || piece.getElevation() == 90);
      }
    }
    assertEquals(2, openings);
    assertNull("The first opening was deleted", getPiece(openingId) != null && this.home.getFurniture().contains(opening) ? opening : null);
    this.homeController.undo();
    assertTrue(this.home.getFurniture().contains(opening));
    this.homeController.redo();
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + wallId + "\",\"openings\":[]}");
    assertTrue(summary, summary.contains("Removed the 2 openings"));
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      assertFalse(piece instanceof HomeDoorOrWindow);
    }

    // Refused
    assertTrue(run("{\"action\":\"add_opening\",\"wall\":\"" + wallId + "\",\"from\":100,\"to\":" + (length + 50) + "}")
        .contains("the length of the wall"));
    assertTrue(run("{\"action\":\"add_opening\",\"wall\":\"" + wallId + "\",\"from\":100,\"to\":200,\"top\":900}")
        .contains("the height of the wall"));
    assertTrue(run("{\"action\":\"add_opening\",\"wall\":\"W99\",\"from\":100,\"to\":200}").contains("no wall has the id W99"));
    assertTrue(run("{\"action\":\"add_opening\",\"wall\":\"" + wallId + "\"}").contains("give \"from\" and \"to\""));

    // A beam across a room, from wall to wall, and a lowered ceiling
    int count = this.home.getFurniture().size();
    summary = run("{\"action\":\"add_header\",\"points\":[[20,200],[480,200]],\"bottom\":220,\"height\":20,\"thickness\":15}");
    assertTrue(summary, summary.contains("from wall to wall"));
    HomePieceOfFurniture beam = this.home.getFurniture().get(count);
    assertEquals("The ends reach the faces of the walls", 500, beam.getWidth(), 0.5f);
    assertEquals(15, beam.getDepth(), 0.01f);
    assertEquals(220, beam.getElevation(), 0.01f);
    assertEquals(20, beam.getHeight(), 0.01f);
    summary = run("{\"action\":\"add_soffit\",\"points\":[[0,0],[500,0],[500,60],[0,60]],\"bottom\":215}");
    assertTrue(summary, summary.contains("the ceiling is lowered to 215 cm"));
    HomePieceOfFurniture soffit = this.home.getFurniture().get(count + 1);
    assertEquals(215, soffit.getElevation(), 0.01f);
    assertEquals(35, soffit.getHeight(), 0.01f);
    assertEquals(500, soffit.getWidth(), 0.01f);
    assertEquals(60, soffit.getDepth(), 0.01f);
    assertTrue(run("{\"action\":\"add_header\",\"bottom\":220}").contains("give \"opening\""));
    assertTrue(run("{\"action\":\"add_soffit\",\"points\":[[0,0],[500,0],[500,60]],\"bottom\":300}").contains("must be under \"top\""));

    // The commands are in the reference and in the help
    List<String> commands = new ArrayList<String>(java.util.Arrays.asList(
        "paint_rooms", "add_opening", "add_header", "add_soffit", "add_railing", "modify_railing"));
    String help = run("{\"action\":\"help\"}");
    for (String command : commands) {
      assertTrue(command + " in " + help, help.contains(command));
      assertTrue(run("{\"action\":\"help\",\"command\":\"" + command + "\"}").contains("\"action\":\"" + command + "\""));
    }
    assertTrue(run("{\"action\":\"help\",\"search\":\"left_finish\"}").contains("modify_wall"));
  }
}
