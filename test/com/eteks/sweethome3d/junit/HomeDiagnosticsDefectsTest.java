/*
 * HomeDiagnosticsDefectsTest.java 25 nov. 2026
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeDiagnostics;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the defects of a home that show in 3D rather than in its plan: each test builds a home
 * with one of them, checks that <code>check_home</code> reports it and reports nothing of the
 * kind in the home built right, and that <code>fix_home</code> repairs the ones it can.
 * @author Sweet Home 3D contributors
 */
public class HomeDiagnosticsDefectsTest {
  private static final String CEILING_HEIGHT = "com.eteks.sweethome3d.ceilingHeight";

  private String                   previousNo3D;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private Content                  dummy;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.dummy = new com.eteks.sweethome3d.tools.URLContent(new File("dummy.png").toURI().toURL());
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

  private List<HomeDiagnostics.Issue> check() {
    HomeItemIds.ensureIds(this.home);
    return HomeDiagnostics.check(this.home, this.home.getFurniture(), new ArrayList<Wall>(this.home.getWalls()),
        this.home.getRooms());
  }

  private List<HomeDiagnostics.Issue> getIssues(HomeDiagnostics.Type type) {
    List<HomeDiagnostics.Issue> issues = new ArrayList<HomeDiagnostics.Issue>();
    for (HomeDiagnostics.Issue issue : check()) {
      if (issue.getType() == type) {
        issues.add(issue);
      }
    }
    return issues;
  }

  private String describe() {
    return HomeDiagnostics.describe(check());
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

  private Wall addWall(float xStart, float yStart, float xEnd, float yEnd, float thickness, float height) {
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness, height);
    this.home.addWall(wall);
    return wall;
  }

  /**
   * A hall of two stories, 5 m x 4 m, its walls and its ceiling 520 cm high.
   */
  private Room addHall() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Hall\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":20,\"height\":520}");
    Room hall = this.home.getRooms().get(0);
    hall.setProperty(CEILING_HEIGHT, "520");
    return hall;
  }

  /**
   * The roof of a wing of one story, its overhang and its gutters, come into a hall of two stories.
   */
  @Test
  public void testRoofInRoom() {
    addHall();
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.ROOF_IN_ROOM).isEmpty());
    // The roof of the wing at the west of the hall stops at the outside face of the wall of the hall
    String summary = run("{\"action\":\"add_roof\",\"name\":\"Wing roof\",\"points\":[[-600,0],[-20,0],[-20,400],[-600,400]],"
        + "\"eave_height\":250,\"pitch\":30,\"overhang\":0,\"type\":\"hip\"}");
    assertNotNull(summary, getPiece("Wing roof"));
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.ROOF_IN_ROOM).isEmpty());
    assertTrue("The wall of the taller part isn't a wall above the roof: " + describe(),
        getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF).isEmpty());
    run("{\"action\":\"delete\",\"ids\":[\"" + HomeItemIds.getId(getPiece("Wing roof")) + "\"]}");

    // The same roof drawn to the inside of the hall, with an overhang: 50 cm into the room
    run("{\"action\":\"add_roof\",\"name\":\"Wing roof\",\"points\":[[-600,0],[20,0],[20,400],[-600,400]],"
        + "\"eave_height\":250,\"pitch\":30,\"overhang\":30,\"type\":\"hip\"}");
    HomePieceOfFurniture roof = getPiece("Wing roof");
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.ROOF_IN_ROOM);
    assertEquals(describe(), 1, issues.size());
    HomeDiagnostics.Issue issue = issues.get(0);
    assertEquals(roof, issue.getItem());
    assertEquals(this.home.getRooms().get(0), issue.getOtherItem());
    assertEquals(2, issue.getType().getSeverity());
    assertFalse(issue.isFixable());
    String message = issue.getMessage();
    assertTrue(message, message.contains("comes into " + HomeItemIds.getId(this.home.getRooms().get(0)) + " \"Hall\""));
    assertTrue(message, message.contains("west side"));
    assertTrue(message, message.contains("ceiling at 520 cm"));
    // The edge along the hall is named
    assertTrue(message, message.contains("Its edge 1 is the one to stop"));
    // Listed with the items that cross another item
    Map<String, Object> options = new LinkedHashMap<String, Object>();
    String report = this.executor.checkHome(options);
    assertTrue(report, report.contains("1 item crosses another item: {\"crossings\":true} lists them"));
    options.put("crossings", Boolean.TRUE);
    report = this.executor.checkHome(options);
    assertTrue(report, report.contains("Items that cross another item ("));
    assertTrue(report, report.contains("- The roof " + HomeItemIds.getId(roof) + " \"Wing roof\" comes into"));
    run("{\"action\":\"delete\",\"ids\":[\"" + HomeItemIds.getId(roof) + "\"]}");

    // A roof that stops at the wall but whose gutter hangs in the room
    run("{\"action\":\"add_roof\",\"name\":\"Wing roof\",\"points\":[[-600,0],[5,0],[5,400],[-600,400]],"
        + "\"eave_height\":250,\"pitch\":30,\"overhang\":0,\"type\":\"hip\",\"gutters\":true}");
    issues = getIssues(HomeDiagnostics.Type.ROOF_IN_ROOM);
    assertEquals(describe(), 1, issues.size());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("gutter"));

    // A room of one story under its own roof has no roof in it
    this.home.getRooms().get(0).setProperty(CEILING_HEIGHT, null);
    run("{\"action\":\"delete\",\"ids\":[\"" + HomeItemIds.getId(getPiece("Wing roof")) + "\"]}");
    for (Wall wall : this.home.getWalls()) {
      wall.setHeight(250f);
    }
    run("{\"action\":\"add_roof\",\"name\":\"Hall roof\",\"points\":[[-20,-20],[520,-20],[520,420],[-20,420]],"
        + "\"eave_height\":250,\"pitch\":30,\"overhang\":30,\"type\":\"hip\",\"gutters\":true}");
    String description = describe();
    assertFalse(description, description.contains("roof_in_room"));
    assertFalse(description, description.contains("ceiling_above_roof"));
    assertFalse(description, description.contains("wall_above_roof"));
    assertFalse(description, description.contains("roof_at_ceiling"));
  }

  /**
   * Two walls drawn over each other, one a little crooked and taller.
   */
  @Test
  public void testOverlappingWalls() {
    Wall straight = addWall(0, 0, 400, 0, 10, 250);
    Wall crooked = addWall(2, 3, 398, -1, 10, 300);
    // A corner, a wall that continues another one and a wall that ends against another one are no defects
    Wall corner = addWall(400, 0, 400, 300, 10, 250);
    straight.setWallAtEnd(corner);
    corner.setWallAtStart(straight);
    addWall(400, 300, 400, 600, 10, 250);
    addWall(200, 300, 400, 300, 10, 250);
    // Walls that share a part of their length only
    Wall long1 = addWall(0, 800, 350, 802, 10, 250);
    Wall long2 = addWall(300, 800, 700, 800, 10, 250);

    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.OVERLAPPING_WALL);
    assertEquals(describe(), 2, issues.size());
    HomeDiagnostics.Issue duplicate = issues.get(0).getItem() == straight ? issues.get(0) : issues.get(1);
    HomeDiagnostics.Issue partial = issues.get(0) == duplicate ? issues.get(1) : issues.get(0);
    // The taller wall is the one kept
    assertEquals(straight, duplicate.getItem());
    assertEquals(crooked, duplicate.getOtherItem());
    assertTrue(duplicate.isFixable());
    assertTrue(duplicate.getMessage(), duplicate.getMessage().contains("different heights (250 and 300 cm)"));
    assertTrue(partial.getItem() == long1 || partial.getItem() == long2);
    assertFalse("A decision is needed: " + partial.getMessage(), partial.isFixable());
    assertTrue(partial.getMessage(), partial.getMessage().contains("for 50 cm"));
    String description = describe();
    assertTrue(description, description.contains("error [overlapping_wall]"));

    String summary = run("{\"action\":\"fix_home\",\"types\":[\"overlapping_wall\"]}");
    assertTrue(summary, summary.contains("Fixed 1 issue"));
    assertFalse(this.home.getWalls().contains(straight));
    assertTrue(this.home.getWalls().contains(crooked));
    // The wall kept is straightened on the line of the other one and joined to the corner
    assertEquals(0, crooked.getYStart(), 1E-3f);
    assertEquals(0, crooked.getYEnd(), 1E-3f);
    assertEquals(0, crooked.getXStart(), 1E-3f);
    assertEquals(400, crooked.getXEnd(), 1E-3f);
    assertEquals(300, crooked.getHeight(), 1E-3f);
    assertEquals(corner, crooked.getWallAtEnd());
    assertEquals(crooked, corner.getWallAtStart());
    issues = getIssues(HomeDiagnostics.Type.OVERLAPPING_WALL);
    assertEquals(1, issues.size());
    assertFalse(issues.get(0).isFixable());
    // Undone as one step
    this.homeController.getView();
    run("{\"action\":\"undo\"}");
  }

  private HomeDoorOrWindow addDoor(float x, float y, float angle) {
    CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(null, "Zz door", null, this.dummy, this.dummy, 90, 10, 205, 0, true,
        1, 0, new Sash [0], null, null, true, null, null);
    HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
    door.setX(x);
    door.setY(y);
    door.setAngle(angle);
    this.home.addPieceOfFurniture(door);
    return door;
  }

  /**
   * A door at 90 degrees in a wall at 80.17 degrees, 6 cm off its middle line.
   */
  @Test
  public void testMisalignedDoor() {
    double wallAngle = Math.toRadians(80.17);
    Wall wall = addWall(0, 0, (float)(400 * Math.cos(wallAngle)), (float)(400 * Math.sin(wallAngle)), 20, 250);
    // A door lined up with its wall
    float middleX = (float)(100 * Math.cos(wallAngle));
    float middleY = (float)(100 * Math.sin(wallAngle));
    HomeDoorOrWindow aligned = addDoor(middleX, middleY, (float)wallAngle);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.DOOR_MISALIGNED).isEmpty());
    // Lined up the other way round too, and against a face of a thick wall
    aligned.setAngle((float)(wallAngle + Math.PI));
    aligned.setX(middleX - (float)Math.sin(wallAngle) * 4);
    aligned.setY(middleY + (float)Math.cos(wallAngle) * 4);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.DOOR_MISALIGNED).isEmpty());

    HomeDoorOrWindow door = addDoor((float)(300 * Math.cos(wallAngle) - Math.sin(wallAngle) * 6),
        (float)(300 * Math.sin(wallAngle) + Math.cos(wallAngle) * 6), (float)(Math.PI / 2));
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.DOOR_MISALIGNED);
    assertEquals(describe(), 1, issues.size());
    assertEquals(door, issues.get(0).getItem());
    assertEquals(wall, issues.get(0).getOtherItem());
    assertTrue(issues.get(0).isFixable());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("turned by 9.83 degrees"));
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("6 cm off the middle line"));
    // The door crosses its wall
    Map<String, Object> options = new LinkedHashMap<String, Object>();
    options.put("crossings", Boolean.TRUE);
    String report = this.executor.checkHome(options);
    assertTrue(report, report.contains("crosses its wall " + HomeItemIds.getId(wall) + " at 9.8 degrees"));

    // Off the middle line only: a quarter of the thickness of the wall and out of its face
    aligned.setAngle((float)wallAngle);
    aligned.setX(middleX - (float)Math.sin(wallAngle) * 7);
    aligned.setY(middleY + (float)Math.cos(wallAngle) * 7);
    assertEquals(2, getIssues(HomeDiagnostics.Type.DOOR_MISALIGNED).size());

    String summary = run("{\"action\":\"fix_home\",\"types\":[\"door_misaligned\"]}");
    assertTrue(summary, summary.contains("Fixed 2 issues"));
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.DOOR_MISALIGNED).isEmpty());
    assertEquals(wallAngle, door.getAngle(), 1E-4);
    // On the middle line, at the same place along the wall
    assertEquals(300 * Math.cos(wallAngle), door.getX(), 0.01);
    assertEquals(300 * Math.sin(wallAngle), door.getY(), 0.01);
    assertEquals(middleX, aligned.getX(), 0.01f);
    assertEquals(middleY, aligned.getY(), 0.01f);
  }

  /**
   * Walls higher than the eaves of the roof built on them stick out of it, and the ceiling of their room too.
   */
  @Test
  public void testWallsAndCeilingAboveRoof() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Attic\",\"x\":0,\"y\":0,\"width\":1000,\"depth\":600,\"thickness\":20,\"height\":250},"
        + "{\"action\":\"add_roof\",\"points\":[[-10,-10],[1010,-10],[1010,610],[-10,610]],\"eave_height\":200,\"pitch\":40,"
        + "\"overhang\":30,\"type\":\"gable\"}");
    HomePieceOfFurniture roof = getPiece("Roof");
    Room room = this.home.getRooms().get(0);
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF);
    assertEquals(describe(), 4, issues.size());
    for (HomeDiagnostics.Issue issue : issues) {
      assertTrue(issue.getItem() instanceof Wall);
      assertEquals(roof, issue.getOtherItem());
      assertTrue(issue.isFixable());
      assertTrue(issue.getMessage(), issue.getMessage().contains("sticks out of the roof " + HomeItemIds.getId(roof)));
    }
    // The ceiling of the room, at the top of its level, passes the roof along its eaves
    issues = getIssues(HomeDiagnostics.Type.CEILING_ABOVE_ROOF);
    assertEquals(describe(), 1, issues.size());
    assertEquals(room, issues.get(0).getItem());
    assertEquals(roof, issues.get(0).getOtherItem());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("250 cm above its floor"));
    // A ceiling at the height of the eaves doesn't
    room.setProperty(CEILING_HEIGHT, "195");
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.CEILING_ABOVE_ROOF).isEmpty());
    room.setProperty(CEILING_HEIGHT, null);
    // Nor a hidden ceiling
    room.setCeilingVisible(false);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.CEILING_ABOVE_ROOF).isEmpty());

    String summary = run("{\"action\":\"fix_home\",\"types\":[\"wall_above_roof\"]}");
    assertTrue(summary, summary.contains("Fixed 4 issues"));
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF).isEmpty());
    // The walls under the gables were cut at the ridge
    assertTrue(this.home.getWalls().size() > 4);

    // A slab used as a ceiling above the roof
    run("{\"action\":\"add_slab\",\"name\":\"Ceiling slab\",\"points\":[[0,0],[1000,0],[1000,600],[0,600]],\"top\":260,\"thickness\":10}");
    HomePieceOfFurniture slab = getPiece("Ceiling slab");
    assertNotNull(slab);
    issues = getIssues(HomeDiagnostics.Type.CEILING_ABOVE_ROOF);
    assertEquals(describe(), 1, issues.size());
    assertEquals(slab, issues.get(0).getItem());
  }

  /**
   * A roof almost flat at the height of the ceiling of the room under it shows through it.
   */
  @Test
  public void testRoofAtCeiling() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Porch\",\"x\":0,\"y\":0,\"width\":400,\"depth\":300,\"thickness\":20,\"height\":250},"
        + "{\"action\":\"add_roof\",\"name\":\"Porch roof\",\"points\":[[-10,-10],[410,-10],[410,310],[-10,310]],\"eave_height\":251,\"pitch\":1,"
        + "\"overhang\":0,\"type\":\"hip\",\"thickness\":10}");
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.ROOF_AT_CEILING);
    assertEquals(describe(), 1, issues.size());
    assertEquals(getPiece("Porch roof"), issues.get(0).getItem());
    assertEquals(0, issues.get(0).getType().getSeverity());
    // 25 cm above the ceiling, it doesn't
    this.home.getRooms().get(0).setProperty(CEILING_HEIGHT, "225");
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.ROOF_AT_CEILING).isEmpty());
  }

  /**
   * A room of two stories whose walls stop at the height of the first one.
   */
  @Test
  public void testOpenTwoStoryRoom() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Family room\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":20,\"height\":250}");
    Room room = this.home.getRooms().get(0);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM).isEmpty());
    // Its ceiling is hidden and a slab is its ceiling, at the height of the ceiling of the floor above
    room.setCeilingVisible(false);
    assertTrue("Open to the roof, as far as the home tells: " + describe(),
        getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM).isEmpty());
    run("{\"action\":\"add_slab\",\"name\":\"High ceiling\",\"points\":[[0,0],[500,0],[500,400],[0,400]],\"top\":530,\"thickness\":10}");
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM);
    assertEquals(describe(), 4, issues.size());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("is 520 cm high"));
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("stop at 250 cm"));
    assertTrue(issues.get(0).isFixable());
    assertEquals(room, issues.get(0).getOtherItem());

    // Walls of the floor above continue three of them
    Level upperLevel = new Level("Upper floor", 262, 12, 258);
    List<Wall> lowerWalls = new ArrayList<Wall>(this.home.getWalls());
    this.home.addLevel(upperLevel);
    Wall openWall = null;
    // The walls drawn before any level are the ones of the first one
    Level lowerLevel = lowerWalls.get(0).getLevel();
    for (int i = 0; i < lowerWalls.size(); i++) {
      Wall wall = lowerWalls.get(i);
      if (i == 0) {
        openWall = wall;
      } else {
        Wall upperWall = new Wall(wall.getXStart(), wall.getYStart(), wall.getXEnd(), wall.getYEnd(), wall.getThickness(), 258);
        this.home.addWall(upperWall);
        upperWall.setLevel(upperLevel);
      }
    }
    assertEquals(lowerLevel, room.getLevel());
    issues = getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM);
    assertEquals(describe(), 1, issues.size());
    assertEquals(openWall, issues.get(0).getItem());

    String summary = run("{\"action\":\"fix_home\",\"types\":[\"open_two_story_room\"]}");
    assertTrue(summary, summary.contains("Fixed 1 issue"));
    assertEquals(520, openWall.getHeight(), 0.01f);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM).isEmpty());

    // The same with the height of the ceiling given to the room
    getPiece("High ceiling").setVisible(false);
    openWall.setHeight(250f);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM).isEmpty());
    room.setProperty(CEILING_HEIGHT, "520");
    assertEquals(describe(), 1, getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM).size());
  }

  /**
   * Returns the side of a wall, 0 for the left one and 1 for the right one, that faces a room.
   */
  private int getSideFacing(Wall wall, Room room) {
    float [][] points = wall.getPoints();
    float middleX = (wall.getXStart() + wall.getXEnd()) / 2;
    float middleY = (wall.getYStart() + wall.getYEnd()) / 2;
    float leftX = (points [0][0] + points [1][0]) / 2;
    float leftY = (points [0][1] + points [1][1]) / 2;
    return room.containsPoint(middleX + (leftX - middleX) * 3, middleY + (leftY - middleY) * 3, 0) ? 0 : 1;
  }

  private void setSide(Wall wall, int side, Integer color, HomeTexture texture) {
    if (side == 0) {
      wall.setLeftSideColor(color);
      wall.setLeftSideTexture(texture);
    } else {
      wall.setRightSideColor(color);
      wall.setRightSideTexture(texture);
    }
  }

  /**
   * The finishes of the sides of the walls and what they face.
   */
  @Test
  public void testWallFinishes() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":20,\"height\":250}");
    Room room = this.home.getRooms().get(0);
    List<Wall> walls = new ArrayList<Wall>(this.home.getWalls());
    assertEquals(4, walls.size());
    // A home that isn't painted yet has nothing to tell
    String description = describe();
    assertFalse(description, description.contains("wall_"));

    HomeTexture siding = new HomeTexture(new CatalogTexture("Vinyl siding white", this.dummy, 100, 100));
    HomeTexture wallpaper = new HomeTexture(new CatalogTexture("Striped wallpaper", this.dummy, 100, 100));
    Integer paint = Integer.valueOf(0xD5AC6B);
    for (Wall wall : walls) {
      int inside = getSideFacing(wall, room);
      setSide(wall, inside, paint, null);
      setSide(wall, 1 - inside, null, siding);
    }
    description = describe();
    assertFalse("Paint inside and siding outside: " + description, description.contains("wall_"));

    // Siding on a side that faces the room
    int inside = getSideFacing(walls.get(0), room);
    setSide(walls.get(0), inside, null, siding);
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(walls.get(0), issues.get(0).getItem());
    assertEquals(room, issues.get(0).getOtherItem());
    assertEquals(1, issues.get(0).getType().getSeverity());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("The " + (inside == 0 ? "left" : "right") + " side of "
        + HomeItemIds.getId(walls.get(0)) + " faces " + HomeItemIds.getId(room) + " \"Kitchen\" with the outside material \"Vinyl siding white\""));
    setSide(walls.get(0), inside, null, wallpaper);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());

    // The paint of the rooms on a side that faces the outside
    inside = getSideFacing(walls.get(1), room);
    setSide(walls.get(1), 1 - inside, paint, null);
    issues = getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(walls.get(1), issues.get(0).getItem());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("the color #D5AC6B"));
    // Another color outside may be a choice
    setSide(walls.get(1), 1 - inside, Integer.valueOf(0x336699), null);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS).isEmpty());

    // A side left without finish
    inside = getSideFacing(walls.get(2), room);
    setSide(walls.get(2), inside, null, null);
    issues = getIssues(HomeDiagnostics.Type.WALL_SIDE_UNPAINTED);
    assertEquals(describe(), 1, issues.size());
    assertEquals(walls.get(2), issues.get(0).getItem());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("which faces " + HomeItemIds.getId(room)));
    assertEquals(0, issues.get(0).getType().getSeverity());
    // A wall in the garden faces no room
    addWall(2000, 0, 2400, 0, 10, 100);
    assertEquals(1, getIssues(HomeDiagnostics.Type.WALL_SIDE_UNPAINTED).size());
  }

  /**
   * The items that cross another item are listed level by level.
   */
  @Test
  public void testCrossings() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living room\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":20,\"height\":250},"
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":250,\"y\":200}");
    Map<String, Object> options = new LinkedHashMap<String, Object>();
    options.put("crossings", Boolean.TRUE);
    String report = this.executor.checkHome(options);
    assertFalse(report, report.contains("cross another item"));
    assertFalse(this.executor.checkHome(Collections.<String, Object>emptyMap()).contains("cross"));

    HomePieceOfFurniture sofa = getPiece("Sofa");
    // The sofa through the north wall, a second one at the same place a little turned, and a wall across the room
    sofa.setY(-5);
    run("{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":255,\"y\":0}");
    Wall crossing = addWall(250, -200, 250, 200, 10, 250);
    report = this.executor.checkHome(options);
    assertTrue(report, report.contains("Items that cross another item ("));
    assertTrue(report, report.contains(HomeItemIds.getId(sofa) + " \"Sofa\" is in the wall "));
    assertTrue(report, report.contains(" \"Sofa\" stands in " + HomeItemIds.getId(sofa) + " \"Sofa\""));
    assertTrue(report, report.contains(HomeItemIds.getId(crossing) + " crosses "));
    assertTrue(report, report.contains("away from their ends"));
    // Counted in the report by default
    report = this.executor.checkHome(Collections.<String, Object>emptyMap());
    assertTrue(report, report.contains("items cross another item: {\"crossings\":true} lists them level by level."));
    assertFalse(report, report.contains("Items that cross another item ("));
    // And through the command
    String summary = run("{\"action\":\"check_home\",\"crossings\":true}");
    assertTrue(summary, summary.contains("Items that cross another item ("));
  }

  /**
   * The checks of a home of 150 walls, 30 rooms, 500 pieces and 3 roofs take a moment.
   */
  @Test
  public void testChecksOfLargeHomeAreFast() {
    StringBuilder commands = new StringBuilder();
    // 30 rooms of 4 m x 4 m in 5 rows, sharing their walls
    for (int row = 0; row < 5; row++) {
      for (int column = 0; column < 6; column++) {
        commands.append(commands.length() > 0 ? "," : "").append("{\"action\":\"add_rectangular_room\",\"name\":\"Room ")
            .append(row * 6 + column).append("\",\"x\":").append(column * 420).append(",\"y\":").append(row * 420)
            .append(",\"width\":400,\"depth\":400,\"thickness\":20,\"height\":250}");
      }
    }
    run(commands.toString());
    for (int i = 0; i < 3; i++) {
      run("{\"action\":\"add_roof\",\"name\":\"Roof " + i + "\",\"points\":[[" + (i * 840 - 10) + ",-10],[" + (i * 840 + 830) + ",-10],["
          + (i * 840 + 830) + ",2090],[" + (i * 840 - 10) + ",2090]],\"eave_height\":250,\"pitch\":30,\"overhang\":30,\"type\":\"hip\",\"gutters\":true}");
    }
    HomePieceOfFurniture sofa = null;
    run("{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100}");
    sofa = getPiece("Sofa");
    for (int i = this.home.getFurniture().size(); i < 500; i++) {
      HomePieceOfFurniture piece = sofa.clone();
      piece.setX(60 + (i % 36) * 68);
      piece.setY(60 + (i / 36) * 150);
      this.home.addPieceOfFurniture(piece);
    }
    while (this.home.getWalls().size() < 150) {
      int i = this.home.getWalls().size();
      addWall(3000 + i * 30, 0, 3000 + i * 30, 300, 10, 250);
    }
    assertTrue(this.home.getWalls().size() >= 150);
    assertEquals(500, this.home.getFurniture().size());
    assertEquals(30, this.home.getRooms().size());
    HomeItemIds.ensureIds(this.home);
    Map<String, Object> options = new LinkedHashMap<String, Object>();
    options.put("crossings", Boolean.TRUE);
    // Once to load the classes, then measured
    this.executor.checkHome(options);
    long start = System.nanoTime();
    String report = this.executor.checkHome(options);
    long time = (System.nanoTime() - start) / 1000000;
    System.out.println("check_home of " + this.home.getWalls().size() + " walls, " + this.home.getRooms().size() + " rooms, "
        + this.home.getFurniture().size() + " pieces and 3 roofs: " + time + " ms");
    assertTrue(report.substring(0, Math.min(300, report.length())), report.startsWith("Home check:"));
    assertTrue("check_home took " + time + " ms", time < 5000);
  }
}
