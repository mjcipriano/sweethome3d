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
}
