/*
 * HomeController3DZoomTest.java 16 Nov 2026
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
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;

/**
 * Tests the zoom of the 3D view.
 */
public class HomeController3DZoomTest {
  private String           previousNo3D;
  private Home             home;
  private HomeController3D controller;
  private HomeController   homeController;

  @Before
  public void setUp() {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    // A house of 10 m x 8 m
    this.home.addWall(new Wall(0, 0, 1000, 0, 10, 250));
    this.home.addWall(new Wall(1000, 0, 1000, 800, 10, 250));
    this.home.addWall(new Wall(1000, 800, 0, 800, 10, 250));
    this.home.addWall(new Wall(0, 800, 0, 0, 10, 250));
    HomeController homeController = this.homeController = new HomeController(this.home, new DefaultUserPreferences(), new SwingViewFactory());
    this.controller = homeController.getHomeController3D();
  }

  @After
  public void tearDown() {
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private double getDistanceToHomeCenter() {
    Camera camera = this.home.getCamera();
    return Math.sqrt(Math.pow(camera.getX() - 500, 2) + Math.pow(camera.getY() - 400, 2)
        + Math.pow(camera.getZ() - 125, 2));
  }

  @Test
  public void testAerialViewZoom() {
    this.controller.viewFromTop();
    assertEquals(this.home.getTopCamera(), this.home.getCamera());
    this.controller.zoomCameraToFit();
    double fitDistance = getDistanceToHomeCenter();
    // Half diagonal of the house is about 6.5 m: the whole house is in sight
    assertTrue("fit distance " + fitDistance, fitDistance > 900 && fitDistance < 2500);

    // Each step away multiplies the distance, where a move added 12.5 cm
    this.controller.zoomCamera(-1);
    assertEquals(fitDistance / 0.88, getDistanceToHomeCenter(), fitDistance * 0.01);
    this.controller.moveCamera(-2.5f);
    assertEquals(fitDistance / 0.88 + 12.5, getDistanceToHomeCenter(), 1);
    this.controller.zoomCameraToFit();
    this.controller.zoomCamera(-10);
    double farDistance = getDistanceToHomeCenter();
    assertEquals(fitDistance / Math.pow(0.88, 10), farDistance, fitDistance * 0.05);
    assertTrue(farDistance > 3 * fitDistance);

    // And back, to the closest point allowed, without ever crossing the home
    this.controller.zoomCamera(10);
    assertEquals(fitDistance, getDistanceToHomeCenter(), fitDistance * 0.05);
    this.controller.zoomCamera(200);
    double closestDistance = getDistanceToHomeCenter();
    assertTrue("closest distance " + closestDistance, closestDistance > 600 && closestDistance < fitDistance);
    // Far away, the distance is limited
    this.controller.zoomCamera(-200);
    assertTrue(getDistanceToHomeCenter() >= 10000);
    this.controller.zoomCamera(-1);
    assertTrue(getDistanceToHomeCenter() < 100000);
  }

  @Test
  public void testVirtualVisitZoom() {
    this.controller.viewFromObserver();
    Camera visitor = this.home.getObserverCamera();
    assertEquals(visitor, this.home.getCamera());
    visitor.setYaw(0);
    float x = visitor.getX();
    float y = visitor.getY();
    // The visitor walks 25 cm forward per step (toward +y at yaw 0)
    this.controller.zoomCamera(2);
    assertEquals(x, visitor.getX(), 0.01f);
    assertEquals(y + 50, visitor.getY(), 0.01f);
    this.controller.zoomCamera(-1);
    assertEquals(y + 25, visitor.getY(), 0.01f);
    // Showing the whole home is for the aerial view
    this.controller.zoomCameraToFit();
    assertEquals(y + 25, visitor.getY(), 0.01f);
  }

  @Test
  public void testStandardViews() {
    // From the top: the camera is above the center of the home
    this.controller.viewFromDirection((float)Math.PI, (float)Math.PI / 2);
    Camera camera = this.home.getCamera();
    assertEquals(this.home.getTopCamera(), camera);
    assertEquals(Math.PI / 2, camera.getPitch(), 0.01);
    assertEquals(500, camera.getX(), 5);
    assertEquals(400, camera.getY(), 5);
    assertTrue(camera.getZ() > 900);
    // Front: from the bottom of the plan, near the horizon
    this.controller.viewFromDirection((float)Math.PI, 0.02f);
    assertEquals(500, camera.getX(), 5);
    assertTrue("y " + camera.getY(), camera.getY() > 800 + 300);
    assertTrue("z " + camera.getZ(), camera.getZ() < 400);
    // Right side
    this.controller.viewFromDirection((float)Math.PI / 2, 0.02f);
    assertTrue("x " + camera.getX(), camera.getX() > 1000 + 300);
    assertEquals(400, camera.getY(), 5);
    // From the virtual visit too
    this.controller.viewFromObserver();
    this.controller.viewFromDirection(0, 0.02f);
    assertEquals(this.home.getTopCamera(), this.home.getCamera());
    assertTrue(this.home.getCamera().getY() < -500);
  }

  /**
   * The nine numbered views show the building from the direction of their key on a
   * numeric keypad, at the same distance whatever stands in the garden.
   */
  @Test
  public void testNumberedStandardViews() {
    Camera camera = this.home.getTopCamera();
    // 2: from the front, level, south of the house
    this.controller.viewFromStandardDirection(2, false);
    assertEquals(500, camera.getX(), 1);
    assertTrue("y " + camera.getY(), camera.getY() > 800);
    assertEquals(0.02f, camera.getPitch(), 1E-4f);
    double distance = getDistanceToHomeCenter();
    // Shift: from higher
    this.controller.viewFromStandardDirection(2, true);
    assertEquals(Math.toRadians(30), camera.getPitch(), 1E-4f);
    // 8 from the back, 4 from the left, 6 from the right
    this.controller.viewFromStandardDirection(8, false);
    assertTrue("y " + camera.getY(), camera.getY() < 0);
    this.controller.viewFromStandardDirection(4, false);
    assertTrue("x " + camera.getX(), camera.getX() < 0);
    assertEquals(400, camera.getY(), 1);
    this.controller.viewFromStandardDirection(6, false);
    assertTrue("x " + camera.getX(), camera.getX() > 1000);
    // 5 from the top
    this.controller.viewFromStandardDirection(5, false);
    assertEquals(Math.PI / 2, camera.getPitch(), 1E-4f);
    assertEquals(500, camera.getX(), 1);
    // 7 from the back left corner, 3 from the front right one
    this.controller.viewFromStandardDirection(7, false);
    assertTrue(camera.getX() < 0 && camera.getY() < 0);
    assertEquals(Math.toRadians(30), camera.getPitch(), 1E-4f);
    this.controller.viewFromStandardDirection(3, true);
    assertTrue("x " + camera.getX() + " y " + camera.getY(), camera.getX() > 900 && camera.getY() > 700);
    assertEquals(Math.toRadians(50), camera.getPitch(), 1E-4f);

    // A tree 30 m away in the garden doesn't move the views away from the house
    HomePieceOfFurniture tree = new HomePieceOfFurniture(new CatalogPieceOfFurniture(
        "Tree", null, null, 300, 300, 500, true, false));
    tree.setX(4000);
    tree.setY(400);
    this.home.addPieceOfFurniture(tree);
    this.controller.viewFromStandardDirection(2, false);
    assertEquals(500, camera.getX(), 1);
    assertEquals(distance, getDistanceToHomeCenter(), 1);
    // But it is in the view of the whole home
    this.controller.zoomCameraToFit();
    assertTrue(getDistanceToHomeCenter() > distance * 1.5);
    // What stands against the house is a part of the building
    this.home.deletePieceOfFurniture(tree);
    tree.setX(1100);
    this.home.addPieceOfFurniture(tree);
    this.controller.viewFromStandardDirection(2, false);
    assertTrue("x " + camera.getX(), camera.getX() > 550);
  }

  /**
   * The aerial view moves sideways and up, and stays there when the home changes.
   */
  @Test
  public void testAerialViewMovesSideways() {
    Camera camera = this.home.getTopCamera();
    // From the front: to the right is to the east
    this.controller.viewFromStandardDirection(2, false);
    float x = camera.getX();
    float y = camera.getY();
    float z = camera.getZ();
    double distance = getDistanceToHomeCenter();
    this.controller.moveCameraSideways(10);
    assertTrue("x " + camera.getX(), camera.getX() > x + 10);
    assertEquals(y, camera.getY(), 0.5f);
    float right = camera.getX() - x;
    // Turning then goes around the point the view moved to
    this.controller.rotateCameraYaw((float)Math.PI);
    assertEquals(500 + right, camera.getX(), 1);
    assertTrue(camera.getY() < 0);
    this.controller.rotateCameraYaw(-(float)Math.PI);
    // From the back, to the right is to the west
    this.controller.moveCameraSideways(-10);
    assertEquals(x, camera.getX(), 0.5f);
    // Up
    this.controller.elevateCamera(10);
    assertTrue(camera.getZ() > z + 10);
    float up = camera.getZ() - z;
    this.controller.moveCameraSideways(10);
    // A change in the home keeps the view where it was moved
    this.home.addWall(new Wall(500, 0, 500, 800, 10, 250));
    assertEquals(x + right, camera.getX(), 1);
    assertEquals(z + up, camera.getZ(), 1);
    // Showing the whole home comes back to its center
    this.controller.zoomCameraToFit();
    assertEquals(500, camera.getX(), 1);
    assertEquals(distance, getDistanceToHomeCenter(), distance * 0.1);
  }

  @Test
  public void testRoomDimensions() {
    com.eteks.sweethome3d.model.Room room = new com.eteks.sweethome3d.model.Room(
        new float [][] {{5, 5}, {995, 5}, {995, 795}, {5, 795}});
    this.home.addRoom(room);
    // An L shaped room: 6 sides, of which two repeat the size of another one
    com.eteks.sweethome3d.model.Room lRoom = new com.eteks.sweethome3d.model.Room(
        new float [][] {{2000, 0}, {2600, 0}, {2600, 300}, {2300, 300}, {2300, 600}, {2000, 600}});
    this.home.addRoom(lRoom);
    assertEquals(2 + 4, this.homeController.addRoomDimensions());
    java.util.List<com.eteks.sweethome3d.model.DimensionLine> lines =
        new java.util.ArrayList<com.eteks.sweethome3d.model.DimensionLine>(this.home.getDimensionLines());
    assertEquals(6, lines.size());
    int inRoom = 0;
    for (com.eteks.sweethome3d.model.DimensionLine line : lines) {
      // Each line is drawn inside its room, 25 cm from the side
      float [][] points = line.getPoints();
      float x = (points [1][0] + points [2][0]) / 2;
      float y = (points [1][1] + points [2][1]) / 2;
      assertTrue(room.containsPoint(x, y, 0) || lRoom.containsPoint(x, y, 0));
      assertEquals(25, Math.abs(line.getOffset()), 0.01f);
      if (room.containsPoint(x, y, 0)) {
        inRoom++;
        assertTrue(Math.abs(line.getLength() - 990) < 0.1 || Math.abs(line.getLength() - 790) < 0.1);
      }
    }
    assertEquals(2, inRoom);
    // Nothing is added twice, and the whole is one step of undo
    assertEquals(0, this.homeController.addRoomDimensions());
    this.homeController.undo();
    assertEquals(0, this.home.getDimensionLines().size());
    // Only the selected rooms when some are selected
    this.home.setSelectedItems(java.util.Arrays.asList(new com.eteks.sweethome3d.model.Selectable [] {room}));
    assertEquals(2, this.homeController.addRoomDimensions());
  }

  @Test
  public void testDollHouseView() {
    java.util.List<Wall> walls = new java.util.ArrayList<Wall>(this.home.getWalls());
    Wall north = walls.get(0);
    Wall east = walls.get(1);
    Wall south = walls.get(2);
    Wall west = walls.get(3);
    // Seen from the south (the bottom of the plan), only the south wall stands before the rooms
    this.controller.viewFromDirection((float)Math.PI, (float)Math.PI / 5);
    java.util.Set<Wall> hidden = com.eteks.sweethome3d.viewcontroller.DollHouseView.getHiddenWalls(this.home, this.home.getCamera());
    assertEquals(java.util.Collections.singleton(south), hidden);
    // A door in that wall goes with it, a door in another wall stays
    com.eteks.sweethome3d.model.HomePieceOfFurniture southDoor = new com.eteks.sweethome3d.model.HomeDoorOrWindow(
        new com.eteks.sweethome3d.model.CatalogDoorOrWindow("Door", null, null, 90, 10, 200, 0, true, 1, 0,
            new com.eteks.sweethome3d.model.Sash [0], null, null, false, 0f, true));
    assertTrue(southDoor.isDoorOrWindow());
    southDoor.setX(400);
    southDoor.setY(800);
    com.eteks.sweethome3d.model.HomePieceOfFurniture northDoor = southDoor.clone();
    northDoor.setY(0);
    this.home.addPieceOfFurniture(southDoor);
    this.home.addPieceOfFurniture(northDoor);
    java.util.Set<com.eteks.sweethome3d.model.HomePieceOfFurniture> hiddenFurniture =
        new java.util.HashSet<com.eteks.sweethome3d.model.HomePieceOfFurniture>();
    com.eteks.sweethome3d.viewcontroller.DollHouseView.addHiddenDoorsAndWindows(this.home.getFurniture(), hidden, hiddenFurniture);
    assertEquals(java.util.Collections.singleton(southDoor), hiddenFurniture);
    // From a corner, the two walls of that corner
    this.controller.viewFromDirection((float)Math.PI * 3 / 4, (float)Math.PI / 6);
    hidden = com.eteks.sweethome3d.viewcontroller.DollHouseView.getHiddenWalls(this.home, this.home.getCamera());
    assertEquals(new java.util.HashSet<Wall>(java.util.Arrays.asList(south, east)), hidden);
    this.controller.viewFromDirection((float)Math.PI * 7 / 4, (float)Math.PI / 6);
    hidden = com.eteks.sweethome3d.viewcontroller.DollHouseView.getHiddenWalls(this.home, this.home.getCamera());
    assertEquals(new java.util.HashSet<Wall>(java.util.Arrays.asList(north, west)), hidden);
    // From the top and in the virtual visit, every wall is shown
    this.controller.viewFromDirection((float)Math.PI, (float)Math.PI / 2);
    assertTrue(com.eteks.sweethome3d.viewcontroller.DollHouseView.getHiddenWalls(this.home, this.home.getCamera()).isEmpty());
    this.controller.viewFromObserver();
    assertTrue(com.eteks.sweethome3d.viewcontroller.DollHouseView.getHiddenWalls(this.home, this.home.getCamera()).isEmpty());
  }
}
