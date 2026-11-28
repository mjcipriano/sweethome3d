/*
 * HomeDefectsReviewTest.java 27 nov. 2026
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Baseboard;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeDiagnostics;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests what the review of the reports of <code>check_home</code> in a real house found: a baseboard
 * or finishes as high as a room are what the room sees of a wall side, the room that a side faces is
 * the part of it that its walls bound, the neighbors of a wall deleted by a repair are joined, and
 * the walls of a taller part of the building aren't fitted to the roof beside them.
 * @author Sweet Home 3D contributors
 */
public class HomeDefectsReviewTest {
  private static final String CEILING_HEIGHT = "com.eteks.sweethome3d.ceilingHeight";

  private String                   previousNo3D;
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private HomeTexture              siding;
  private HomeTexture              bricks;
  private Integer                  paint = Integer.valueOf(0xD5AC6B);

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    Content dummy = new com.eteks.sweethome3d.tools.URLContent(new File("dummy.png").toURI().toURL());
    this.siding = new HomeTexture(new CatalogTexture("Vinyl siding white", dummy, 100, 100));
    this.bricks = new HomeTexture(new CatalogTexture("Bricks 007", dummy, 100, 100));
    this.homeController = new HomeController(this.home, preferences, new SwingViewFactory());
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
    String summary = this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
    assertNotNull(commands, summary);
    return summary;
  }

  private List<HomeDiagnostics.Issue> getIssues(HomeDiagnostics.Type type) {
    HomeItemIds.ensureIds(this.home);
    List<HomeDiagnostics.Issue> issues = new ArrayList<HomeDiagnostics.Issue>();
    for (HomeDiagnostics.Issue issue : HomeDiagnostics.check(this.home, this.home.getFurniture(),
             new ArrayList<Wall>(this.home.getWalls()), this.home.getRooms())) {
      if (issue.getType() == type) {
        issues.add(issue);
      }
    }
    return issues;
  }

  private String describe() {
    HomeItemIds.ensureIds(this.home);
    return HomeDiagnostics.describe(HomeDiagnostics.check(this.home, this.home.getFurniture(),
        new ArrayList<Wall>(this.home.getWalls()), this.home.getRooms()));
  }

  private Wall addWall(float xStart, float yStart, float xEnd, float yEnd, float thickness, float height) {
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness, height);
    this.home.addWall(wall);
    return wall;
  }

  private Room addRoom(String name, float... coordinates) {
    float [][] points = new float [coordinates.length / 2][];
    for (int i = 0; i < points.length; i++) {
      points [i] = new float [] {coordinates [2 * i], coordinates [2 * i + 1]};
    }
    Room room = new Room(points);
    room.setName(name);
    this.home.addRoom(room);
    return room;
  }

  private Room getRoom(String name) {
    for (Room room : this.home.getRooms()) {
      if (name.equals(room.getName())) {
        return room;
      }
    }
    return null;
  }

  private HomePieceOfFurniture getPiece(String name) {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (name.equals(piece.getName())) {
        return piece;
      }
    }
    return null;
  }

  private Wall getWall(float xStart, float yStart, float xEnd, float yEnd) {
    for (Wall wall : this.home.getWalls()) {
      if (Math.abs(wall.getXStart() - xStart) < 1 && Math.abs(wall.getYStart() - yStart) < 1
          && Math.abs(wall.getXEnd() - xEnd) < 1 && Math.abs(wall.getYEnd() - yEnd) < 1
          || Math.abs(wall.getXStart() - xEnd) < 1 && Math.abs(wall.getYStart() - yEnd) < 1
              && Math.abs(wall.getXEnd() - xStart) < 1 && Math.abs(wall.getYEnd() - yStart) < 1) {
        return wall;
      }
    }
    return null;
  }

  /**
   * A room of 400 x 300 cm with its four walls, 250 cm high, and the wall at its north.
   */
  private Wall addDen() {
    addRoom("Den", 0, 0, 400, 0, 400, 300, 0, 300);
    Wall north = addWall(-5, -5, 405, -5, 10, 250);
    addWall(405, -5, 405, 305, 10, 250);
    addWall(405, 305, -5, 305, 10, 250);
    addWall(-5, 305, -5, -5, 10, 250);
    return north;
  }

  /**
   * A baseboard, or finishes given from the floor up, are what a room sees of a side up to their top:
   * siding behind them or above the ceiling of the room isn't siding indoors.
   */
  @Test
  public void testBaseboardAndFinishesSeenByRoom() {
    Wall north = addDen();
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // Siding on both sides: the side toward the room is reported
    north.setLeftSideTexture(this.siding);
    north.setRightSideTexture(this.siding);
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertSame(north, issues.get(0).getItem());
    assertFalse(issues.get(0).getMessage(), issues.get(0).getMessage().contains("above its floor"));

    // The paint of the room as a baseboard as high as the wall, the way walls got two finishes before
    Baseboard fullBaseboard = Baseboard.getInstance(0.5f, 250, this.paint, null);
    north.setLeftSideBaseboard(fullBaseboard);
    north.setRightSideBaseboard(fullBaseboard);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // A real baseboard leaves the siding above it in the room
    Baseboard lowBaseboard = Baseboard.getInstance(1, 10, this.paint, null);
    north.setLeftSideBaseboard(lowBaseboard);
    north.setRightSideBaseboard(lowBaseboard);
    issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("from 10 cm above its floor"));
    // A baseboard without color nor texture of its own shows the finish of the side
    Baseboard plainBaseboard = Baseboard.getInstance(1, 250, null, null);
    north.setLeftSideBaseboard(plainBaseboard);
    north.setRightSideBaseboard(plainBaseboard);
    assertEquals(describe(), 1, getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).size());
    north.setLeftSideBaseboard(null);
    north.setRightSideBaseboard(null);

    // Finishes from the floor up, as high as the ceiling of the room, then lower
    String id = HomeItemIds.getId(north);
    run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"left_finish\":[{\"to\":250,\"color\":\"#D5AC6B\"}],"
        + "\"right_finish\":[{\"to\":250,\"color\":\"#D5AC6B\"}]}");
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"left_finish\":[{\"to\":120,\"color\":\"#D5AC6B\"}],"
        + "\"right_finish\":[{\"to\":120,\"color\":\"#D5AC6B\"}]}");
    issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("from 120 cm above its floor"));
    // The room sees the side up to its ceiling only
    getRoom("Den").setProperty(CEILING_HEIGHT, "118");
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // Siding as the first finish from the floor is seen, whatever is above it
    getRoom("Den").setProperty(CEILING_HEIGHT, null);
    north.setLeftSideTexture(null);
    north.setRightSideTexture(null);
    north.setLeftSideColor(this.paint);
    north.setRightSideColor(this.paint);
    run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"left_finish\":false,\"right_finish\":false}");
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    Baseboard sidingBaseboard = Baseboard.getInstance(1, 90, null, this.siding);
    north.setLeftSideBaseboard(sidingBaseboard);
    north.setRightSideBaseboard(sidingBaseboard);
    assertEquals(describe(), 1, getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).size());
  }

  /**
   * A room drawn larger than the walls around it isn't faced by the outer side of these walls,
   * while a short return wall that stands in a room is.
   */
  @Test
  public void testRoomDrawnBeyondItsWalls() {
    addDen();
    // A bay at the east of the den, its room drawn 40 cm beyond its three walls
    getWall(405, -5, 405, 305).setYEnd(95);
    Wall eastEnd = addWall(405, 205, 405, 305, 10, 250);
    Wall bayNorth = addWall(405, 100, 485, 100, 10, 250);
    Wall bayEast = addWall(485, 100, 485, 200, 10, 250);
    Wall baySouth = addWall(485, 200, 405, 200, 10, 250);
    addRoom("Bay", 400, 60, 525, 60, 525, 240, 400, 240);
    // Siding out of the bay, paint in it
    for (Wall wall : new Wall [] {bayNorth, bayEast, baySouth}) {
      wall.setLeftSideTexture(this.siding);
      wall.setRightSideColor(this.paint);
    }
    assertNotNull(eastEnd);
    assertTrue("The siding of the bay is on its outside: " + describe(),
        getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // The other way round, it is in the bay
    for (Wall wall : new Wall [] {bayNorth, bayEast, baySouth}) {
      wall.setLeftSideTexture(null);
      wall.setLeftSideColor(this.paint);
      wall.setRightSideColor(null);
      wall.setRightSideTexture(this.siding);
    }
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 3, issues.size());
    for (HomeDiagnostics.Issue issue : issues) {
      assertSame(getRoom("Bay"), issue.getOtherItem());
    }
    for (Wall wall : new Wall [] {bayNorth, bayEast, baySouth}) {
      wall.setRightSideTexture(null);
      wall.setRightSideColor(this.paint);
    }
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());

    // A return wall of 30 cm at a jog, in the den: its brick side is in the room, however short it is
    Wall returnWall = addWall(200, 0, 200, 30, 10, 250);
    returnWall.setLeftSideTexture(this.bricks);
    returnWall.setRightSideColor(Integer.valueOf(0xFFFFFF));
    issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertSame(returnWall, issues.get(0).getItem());
    assertSame(getRoom("Den"), issues.get(0).getOtherItem());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("Bricks 007"));
    returnWall.setLeftSideTexture(null);
    returnWall.setLeftSideColor(Integer.valueOf(0xFFFFFF));
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());

    // A room drawn over two spaces that a door joins is the room of both
    addRoom("Suite", 0, 400, 600, 400, 600, 700, 0, 700);
    addWall(-5, 395, 605, 395, 10, 250);
    addWall(605, 395, 605, 705, 10, 250);
    Wall suiteSouth = addWall(605, 705, -5, 705, 10, 250);
    addWall(-5, 705, -5, 395, 10, 250);
    Wall partition = addWall(300, 400, 300, 700, 10, 250);
    // In the space where the middle of the room isn't: the middle is under the partition, the larger space is the east one
    partition.setXStart(250);
    partition.setXEnd(250);
    Wall closetWall = addWall(100, 400, 100, 500, 10, 250);
    closetWall.setLeftSideTexture(this.siding);
    closetWall.setRightSideTexture(this.siding);
    assertNotNull(suiteSouth);
    assertTrue("A space behind a wall without door isn't known to be the room: " + describe(),
        getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    run("{\"action\":\"add_opening\",\"wall\":\"" + HomeItemIds.getId(partition) + "\",\"center\":200,\"width\":90,\"bottom\":0,\"top\":210}");
    issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 2, issues.size());
    assertSame(closetWall, issues.get(0).getItem());
    assertSame(getRoom("Suite"), issues.get(0).getOtherItem());
  }

  /**
   * The neighbors of a wall that <code>fix_home</code> deletes are joined to each other in the same step.
   */
  @Test
  public void testNeighborsOfDeletedWallAreJoined() {
    Wall first = addWall(0, 0, 300, 0, 10, 250);
    Wall tiny = addWall(300, 0, 300, 0.4f, 10, 250);
    Wall second = addWall(300, 0.4f, 300, 300, 10, 250);
    first.setWallAtEnd(tiny);
    tiny.setWallAtStart(first);
    tiny.setWallAtEnd(second);
    second.setWallAtStart(tiny);
    assertEquals(describe(), 1, getIssues(HomeDiagnostics.Type.TINY_WALL).size());
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_GAP).isEmpty());

    String summary = run("{\"action\":\"fix_home\",\"types\":[\"tiny_wall\"]}");
    assertTrue(summary, summary.contains("Fixed 1 issue"));
    assertFalse(this.home.getWalls().contains(tiny));
    assertSame(second, first.getWallAtEnd());
    assertSame(first, second.getWallAtStart());
    assertEquals(first.getXEnd(), second.getXStart(), 0.001f);
    assertEquals(first.getYEnd(), second.getYStart(), 0.001f);
    assertTrue("No gap is left for a second repair: " + describe(), getIssues(HomeDiagnostics.Type.WALL_GAP).isEmpty());
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.TINY_WALL).isEmpty());
    // One step to undo
    this.homeController.undo();
    assertTrue(this.home.getWalls().contains(tiny));
    assertSame(tiny, first.getWallAtEnd());
    assertSame(tiny, second.getWallAtStart());
    this.homeController.redo();
    assertSame(second, first.getWallAtEnd());

    // A wall drawn twice, the wall that follows joined to the one that is deleted
    Wall duplicate = addWall(300, 0, 300, 300, 10, 250);
    Wall third = addWall(300, 300, 600, 300, 10, 250);
    duplicate.setWallAtEnd(third);
    third.setWallAtStart(duplicate);
    assertEquals(describe(), 1, getIssues(HomeDiagnostics.Type.DUPLICATE_WALL).size());
    run("{\"action\":\"fix_home\",\"types\":[\"duplicate_wall\"]}");
    assertFalse(this.home.getWalls().contains(duplicate));
    assertSame(second, third.getWallAtStart());
    assertSame(third, second.getWallAtEnd());
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_GAP).isEmpty());
  }

  /**
   * The answers of the roof commands don't offer to fit the walls of a taller part of the building
   * to the roof beside it, and <code>fit_walls_to_roof</code> leaves such walls as they are.
   */
  @Test
  public void testWallsOfTallerPartAreNotFittedToRoof() {
    // A hall of two stories and the roof of a wing that stops at the inner face of its west wall
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Hall\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":20,\"height\":520}");
    getRoom("Hall").setProperty(CEILING_HEIGHT, "520");
    Wall hallWall = getWall(-10, -10, -10, 410);
    assertNotNull(hallWall);
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Wing\",\"x\":-600,\"y\":0,\"width\":580,\"depth\":400,"
        + "\"thickness\":20,\"height\":250}");
    String summary = run("{\"action\":\"add_roof\",\"name\":\"Wing roof\",\"points\":[[-620,-10],[0,-10],[0,410],[-620,410]],"
        + "\"eave_height\":250,\"pitch\":40,\"overhang\":0,\"type\":\"gable\",\"gable_walls\":false}");
    HomePieceOfFurniture roof = getPiece("Wing roof");
    assertNotNull(summary, roof);
    String roofId = HomeItemIds.getId(roof);
    String hallWallId = HomeItemIds.getId(hallWall);
    run("{\"action\":\"fit_walls_to_roof\",\"roof\":\"" + roofId + "\"}");
    assertEquals("The wall of the hall isn't one of the walls under the roof", 520, hallWall.getHeight(), 0.01f);

    // A lower pitch leaves the walls under the gables above the roof: they are named, not the wall of the hall
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"pitch\":20}");
    assertTrue(summary, summary.contains("above this roof: "));
    String named = summary.substring(summary.indexOf("above this roof: "));
    assertFalse(summary, named.matches("(?s).*\\b" + hallWallId + "\\b.*"));
    // Named by the client, it is left as it is with the reason
    summary = run("{\"action\":\"fit_walls_to_roof\",\"roof\":\"" + roofId + "\",\"ids\":[\"" + hallWallId + "\"]}");
    assertTrue(summary, summary.contains("left [" + hallWallId + "] as it is"));
    assertTrue(summary, summary.contains("\"force\":true"));
    assertEquals(520, hallWall.getHeight(), 0.01f);
    assertTrue(this.home.getWalls().contains(hallWall));
    // The command of the answer fits the other ones, and nothing remains above the roof
    String fit = named.substring(named.indexOf("{\"action\":\"fit_walls_to_roof\""));
    fit = fit.substring(0, fit.indexOf('}') + 1);
    run(fit);
    assertEquals(520, hallWall.getHeight(), 0.01f);
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"pitch\":21}");
    assertFalse(summary, summary.contains("above this roof"));
    // Asked with force, the wall of the hall is cut down to the roof
    summary = run("{\"action\":\"fit_walls_to_roof\",\"roof\":\"" + roofId + "\",\"ids\":[\"" + hallWallId + "\"],\"force\":true}");
    assertFalse(summary, summary.contains("as it is"));
    assertTrue(summary, hallWall.getHeight() < 500 || !this.home.getWalls().contains(hallWall)
        || hallWall.getHeightAtEnd() != null && hallWall.getHeightAtEnd() < 500);
  }
}
