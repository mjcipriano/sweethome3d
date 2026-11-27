/*
 * HomeDefectsFalsePositivesTest.java 26 nov. 2026
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Level;
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
 * Tests the reports of <code>check_home</code> that were wrong in a real house: each test builds
 * a small home where a check told a defect that isn't one, checks that it doesn't anymore, and
 * that the defect of the same kind is still reported beside it.
 * @author Sweet Home 3D contributors
 */
public class HomeDefectsFalsePositivesTest {
  private static final String CEILING_HEIGHT = "com.eteks.sweethome3d.ceilingHeight";
  private static final String OUTDOOR = "com.eteks.sweethome3d.outdoor";

  private String                   previousNo3D;
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private HomeTexture              siding;
  private Integer                  paint = Integer.valueOf(0xD5AC6B);

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    Content dummy = new com.eteks.sweethome3d.tools.URLContent(new File("dummy.png").toURI().toURL());
    this.siding = new HomeTexture(new CatalogTexture("Vinyl siding white", dummy, 100, 100));
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

  private Wall addWall(float xStart, float yStart, float xEnd, float yEnd, float thickness, float height, Level level) {
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness, height);
    this.home.addWall(wall);
    wall.setLevel(level);
    return wall;
  }

  private Room addRoom(String name, Level level, float... coordinates) {
    float [][] points = new float [coordinates.length / 2][];
    for (int i = 0; i < points.length; i++) {
      points [i] = new float [] {coordinates [2 * i], coordinates [2 * i + 1]};
    }
    Room room = new Room(points);
    room.setName(name);
    this.home.addRoom(room);
    room.setLevel(level);
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

  /**
   * Returns the side of a wall, 0 for the left one and 1 for the right one, that faces a point.
   */
  private static int getSideFacing(Wall wall, float x, float y) {
    float [][] points = wall.getPoints();
    float middleX = (wall.getXStart() + wall.getXEnd()) / 2;
    float middleY = (wall.getYStart() + wall.getYEnd()) / 2;
    float leftX = (points [0][0] + points [1][0]) / 2 - middleX;
    float leftY = (points [0][1] + points [1][1]) / 2 - middleY;
    return leftX * (x - middleX) + leftY * (y - middleY) > 0 ? 0 : 1;
  }

  private static void setSide(Wall wall, int side, Integer color, HomeTexture texture) {
    if (side == 0) {
      wall.setLeftSideColor(color);
      wall.setLeftSideTexture(texture);
    } else {
      wall.setRightSideColor(color);
      wall.setRightSideTexture(texture);
    }
  }

  /**
   * Paints the side of each wall that faces the given point and puts siding on the other one.
   */
  private void finish(List<Wall> walls, float insideX, float insideY) {
    for (Wall wall : walls) {
      int inside = getSideFacing(wall, insideX, insideY);
      setSide(wall, inside, this.paint, null);
      setSide(wall, 1 - inside, null, this.siding);
    }
  }

  /**
   * A living room with paint inside and siding outside, 5 m x 4 m, its walls 20 cm thick.
   */
  private List<Wall> addLivingRoom() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living room\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":20,\"height\":250}");
    List<Wall> walls = new ArrayList<Wall>(this.home.getWalls());
    assertEquals(4, walls.size());
    finish(walls, 250, 200);
    return walls;
  }

  /**
   * The siding of a house is right where it faces a porch, a deck, a yard or a room drawn for a low roof.
   */
  @Test
  public void testSidingThatFacesRoomsOutdoors() {
    addLivingRoom();
    Level level = getRoom("Living room").getLevel();
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());

    // A room against the north wall, with its ceiling: a room indoors that faces siding
    Room porch = addRoom("Front room", level, 0, -220, 500, -220, 500, -20, 0, -20);
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(porch, issues.get(0).getOtherItem());
    assertFalse(issues.get(0).getMessage(), issues.get(0).getMessage().contains("outdoor"));
    // Without ceiling and open on three sides, it's a porch whatever its name
    porch.setCeilingVisible(false);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // Under its roof too, in a recess between two walls
    run("{\"action\":\"add_roof\",\"name\":\"Porch roof\",\"points\":[[-10,-230],[510,-230],[510,-20],[-10,-20]],"
        + "\"eave_height\":230,\"pitch\":15,\"overhang\":10,\"type\":\"hip\"}");
    assertNotNull(getPiece("Porch roof"));
    Wall west = addWall(-10, -220, -10, -20, 20, 250, level);
    Wall east = addWall(510, -220, 510, -20, 20, 250, level);
    for (Wall wall : new Wall [] {west, east}) {
      setSide(wall, 0, null, this.siding);
      setSide(wall, 1, null, this.siding);
    }
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // The client may tell that it's a room indoors, then let the check tell again
    String summary = run("{\"action\":\"modify_room\",\"room\":\"Front room\",\"outdoor\":false}");
    assertTrue(summary, summary.contains("is now indoors"));
    assertEquals("false", porch.getProperty(OUTDOOR));
    issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 3, issues.size());
    this.homeController.undo();
    assertEquals(null, porch.getProperty(OUTDOOR));
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    this.homeController.redo();
    assertEquals(describe(), 3, getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).size());
    run("{\"action\":\"modify_room\",\"room\":\"Front room\",\"outdoor\":null}");
    assertEquals(null, porch.getProperty(OUTDOOR));
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    summary = run("{\"action\":\"modify_room\",\"room\":\"Front room\",\"outdoor\":\"maybe\"}");
    assertTrue(summary, summary.contains("Ignored \"outdoor\""));

    // A yard on a level of its own, lower than the house: no wall, no floor, no ceiling
    Room yard = addRoom("Plot 2", level, 520, -600, 1500, -600, 1500, 900, 520, 900);
    yard.setCeilingVisible(false);
    yard.setFloorVisible(false);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());

    // A room closed by walls on every side, without ceiling, that no roof of the home covers: a courtyard
    Room court = addRoom("Space 3", level, 0, 420, 500, 420, 500, 700, 0, 700);
    court.setCeilingVisible(false);
    List<Wall> courtWalls = new ArrayList<Wall>();
    courtWalls.add(addWall(-10, 420, -10, 710, 20, 250, level));
    courtWalls.add(addWall(-10, 710, 510, 710, 20, 250, level));
    courtWalls.add(addWall(510, 710, 510, 420, 20, 250, level));
    for (Wall wall : courtWalls) {
      setSide(wall, 0, null, this.siding);
      setSide(wall, 1, null, this.siding);
    }
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // Under a roof where somebody stands, nothing tells that it's outdoors...
    run("{\"action\":\"add_roof\",\"name\":\"Court roof\",\"points\":[[-20,400],[520,400],[520,720],[-20,720]],"
        + "\"eave_height\":260,\"pitch\":20,\"overhang\":0,\"type\":\"hip\"}");
    assertNotNull(getPiece("Court roof"));
    issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 4, issues.size());
    for (HomeDiagnostics.Issue issue : issues) {
      assertEquals(court, issue.getOtherItem());
      assertTrue(issue.getMessage(), issue.getMessage().contains("modify_room with \"outdoor\":true"));
    }
    // ... but its name, or the client
    court.setName("Back patio");
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    court.setName("Space 3");
    assertEquals(describe(), 4, getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).size());
    summary = run("{\"action\":\"modify_room\",\"room\":\"Space 3\",\"outdoor\":true}");
    assertTrue(summary, summary.contains("is now outdoors"));
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // Siding in the living room is still told
    Wall north = null;
    for (Wall wall : this.home.getWalls()) {
      if (Math.abs(wall.getYStart() + 10) < 0.5 && Math.abs(wall.getYEnd() + 10) < 0.5) {
        north = wall;
      }
    }
    assertNotNull(north);
    setSide(north, getSideFacing(north, 250, 200), null, this.siding);
    issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(getRoom("Living room"), issues.get(0).getOtherItem());
  }

  /**
   * A room drawn at the place of a low roof is outdoors; an attic where somebody stands isn't, and
   * the sides that face an attic or a crawl space that shows no floor aren't seen.
   */
  @Test
  public void testRoomsUnderRoofs() {
    addLivingRoom();
    run("{\"action\":\"add_level\",\"name\":\"Upper\"}");
    Level upper = this.home.getLevels().get(this.home.getLevels().size() - 1);
    // A wall of the upper floor rises beside a bay roof 60 cm high, drawn with a room
    Wall wall = addWall(0, -10, 500, -10, 20, 250, upper);
    Room upperRoom = addRoom("Bedroom", upper, 0, 0, 500, 0, 500, 400, 0, 400);
    setSide(wall, getSideFacing(wall, 250, 200), this.paint, null);
    setSide(wall, getSideFacing(wall, 250, -200), null, this.siding);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    Room bayRoof = addRoom("Space 5", upper, 0, -150, 500, -150, 500, -20, 0, -20);
    bayRoof.setCeilingVisible(false);
    // Closed on its 3 other sides by low walls: only its roof tells what it is
    for (Wall lowWall : new Wall [] {addWall(-5, -150, -5, -20, 10, 20, upper),
                                     addWall(-5, -155, 505, -155, 10, 20, upper),
                                     addWall(505, -150, 505, -20, 10, 20, upper)}) {
      setSide(lowWall, 0, Integer.valueOf(0x808080), null);
      setSide(lowWall, 1, Integer.valueOf(0x808080), null);
    }
    assertEquals(describe(), 1, getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).size());
    run("{\"action\":\"add_roof\",\"name\":\"Bay roof\",\"level\":\"Upper\",\"points\":[[0,-150],[500,-150],[500,-20],[0,-20]],"
        + "\"eave_height\":5,\"pitch\":25,\"overhang\":0,\"type\":\"hip\"}");
    HomePieceOfFurniture roof = getPiece("Bay roof");
    assertNotNull(roof);
    assertTrue("The room of a roof where nobody stands: " + describe(),
        getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS).isEmpty());
    // Under a roof 3 m high it's an attic: a room indoors
    run("{\"action\":\"modify_roof\",\"roof\":\"" + HomeItemIds.getId(roof) + "\",\"eave_height\":150,\"pitch\":45}");
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(bayRoof, issues.get(0).getOtherItem());
    // An attic that shows no floor is a space nobody sees: the side that faces it isn't told as unpainted
    setSide(wall, getSideFacing(wall, 250, -200), null, null);
    issues = getIssues(HomeDiagnostics.Type.WALL_SIDE_UNPAINTED);
    assertEquals(describe(), 1, issues.size());
    assertEquals(bayRoof, issues.get(0).getOtherItem());
    bayRoof.setFloorVisible(false);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_SIDE_UNPAINTED).isEmpty());
    // The side of the bedroom is
    setSide(wall, getSideFacing(wall, 250, 200), null, null);
    issues = getIssues(HomeDiagnostics.Type.WALL_SIDE_UNPAINTED);
    assertEquals(describe(), 1, issues.size());
    assertEquals(upperRoom, issues.get(0).getOtherItem());
  }

  /**
   * The wall of a taller part of the building isn't a wall above the lower roof that runs into it,
   * and a wall is compared with the roof it stands under.
   */
  @Test
  public void testWallsBesideOrUnderAnotherRoof() {
    // A hall of two stories, and the roof of a wing 430 cm high where it meets the hall, 90 cm under its walls
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Hall\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":20,\"height\":520}");
    Room hall = getRoom("Hall");
    hall.setProperty(CEILING_HEIGHT, "520");
    run("{\"action\":\"add_roof\",\"name\":\"Wing roof\",\"points\":[[-600,0],[0,0],[0,400],[-600,400]],"
        + "\"eave_height\":430,\"pitch\":30,\"overhang\":0,\"type\":\"hip\"}");
    assertNotNull(getPiece("Wing roof"));
    assertTrue("The wall of the hall is the one of a taller room: " + describe(),
        getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF).isEmpty());
    // The same wall is reported if the room it closes is no taller than the roof over it:
    // a roof drawn over the hall, its walls 60 cm above its eaves
    run("{\"action\":\"delete\",\"ids\":[\"" + HomeItemIds.getId(getPiece("Wing roof")) + "\"]}");
    hall.setProperty(CEILING_HEIGHT, null);
    hall.setCeilingVisible(false);
    String summary = run("{\"action\":\"add_roof\",\"name\":\"Hall roof\",\"points\":[[-10,-10],[510,-10],[510,410],[-10,410]],"
        + "\"eave_height\":460,\"pitch\":30,\"overhang\":30,\"type\":\"hip\"}");
    assertNotNull(summary, getPiece("Hall roof"));
    assertEquals(summary + describe(), 4, getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF).size());
    run("{\"action\":\"delete\",\"ids\":[\"" + HomeItemIds.getId(getPiece("Hall roof")) + "\"]}");

    // The wall of a garage under its roof, which the roof of a porch crosses at one end, 60 cm lower
    Wall garageWall = addWall(1705, 0, 1705, 600, 10, 300, hall.getLevel());
    run("{\"action\":\"add_roof\",\"name\":\"Garage roof\",\"points\":[[1700,100],[2300,100],[2300,700],[1700,700]],"
        + "\"eave_height\":300,\"pitch\":30,\"overhang\":0,\"type\":\"hip\"},"
        + "{\"action\":\"add_roof\",\"name\":\"Porch roof\",\"points\":[[1500,-100],[1900,-100],[1900,90],[1500,90]],"
        + "\"eave_height\":230,\"pitch\":10,\"overhang\":0,\"type\":\"hip\"}");
    assertNotNull(getPiece("Garage roof"));
    assertNotNull(getPiece("Porch roof"));
    assertTrue("The wall stands under the roof of the garage: " + describe(),
        getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF).isEmpty());
    // Too high for its own roof, it's reported against that one
    garageWall.setHeight(340f);
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF);
    assertEquals(describe(), 1, issues.size());
    assertEquals(garageWall, issues.get(0).getItem());
    assertEquals(getPiece("Garage roof"), issues.get(0).getOtherItem());
    garageWall.setHeight(300f);
    // A wall under the roof of the porch alone is compared with it
    run("{\"action\":\"delete\",\"ids\":[\"" + HomeItemIds.getId(getPiece("Garage roof")) + "\"]}");
    issues = getIssues(HomeDiagnostics.Type.WALL_ABOVE_ROOF);
    assertEquals(describe(), 1, issues.size());
    assertEquals(getPiece("Porch roof"), issues.get(0).getOtherItem());
  }

  /**
   * The wall of an upper floor that looks into a room of two stories, or of a garage whose floor is lower,
   * faces that room: the paint of the rooms is right there.
   */
  @Test
  public void testPaintThatFacesRoomsOfOtherLevels() {
    List<Wall> walls = addLivingRoom();
    Room livingRoom = getRoom("Living room");
    livingRoom.setProperty(CEILING_HEIGHT, "512");
    assertEquals(4, walls.size());
    run("{\"action\":\"add_level\",\"name\":\"Upper\"}");
    Level upper = this.home.getLevels().get(this.home.getLevels().size() - 1);
    assertTrue(upper.getElevation() > 200);
    // A bedroom at the east of the open space, on the upper floor
    Room bedroom = addRoom("Bedroom", upper, 520, 0, 900, 0, 900, 400, 520, 400);
    List<Wall> upperWalls = new ArrayList<Wall>();
    Wall overlookWall = addWall(510, -10, 510, 410, 20, 250, upper);
    upperWalls.add(overlookWall);
    upperWalls.add(addWall(510, -10, 910, -10, 20, 250, upper));
    upperWalls.add(addWall(910, -10, 910, 410, 20, 250, upper));
    upperWalls.add(addWall(910, 410, 510, 410, 20, 250, upper));
    finish(upperWalls, 700, 200);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS).isEmpty());
    // The side of its wall that looks into the living room has the paint of the rooms
    setSide(overlookWall, getSideFacing(overlookWall, 250, 200), this.paint, null);
    assertTrue("That side faces the room of two stories: " + describe(),
        getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS).isEmpty());
    // Siding there is siding indoors
    setSide(overlookWall, getSideFacing(overlookWall, 250, 200), null, this.siding);
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_OUTSIDE_FINISH_INDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(overlookWall, issues.get(0).getItem());
    assertEquals(livingRoom, issues.get(0).getOtherItem());
    setSide(overlookWall, getSideFacing(overlookWall, 250, 200), this.paint, null);
    // Over a room of one story, the same side faces the outside above its roof
    livingRoom.setProperty(CEILING_HEIGHT, null);
    issues = getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(overlookWall, issues.get(0).getItem());
    livingRoom.setProperty(CEILING_HEIGHT, "512");
    // And the paint of the rooms on a side that faces the street is still told
    Wall streetWall = upperWalls.get(2);
    setSide(streetWall, getSideFacing(streetWall, 1500, 200), this.paint, null);
    issues = getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(streetWall, issues.get(0).getItem());
    assertNotNull(bedroom);
  }

  /**
   * The sides of two walls drawn a few centimeters apart, which face each other, aren't seen.
   */
  @Test
  public void testSidesInCavityBetweenWalls() {
    addLivingRoom();
    Level level = getRoom("Living room").getLevel();
    // A second room with its own walls, 20 cm away from the east wall of the first one
    addRoom("Den", level, 560, 0, 900, 0, 900, 400, 560, 400);
    List<Wall> denWalls = new ArrayList<Wall>();
    Wall denWest = addWall(550, -10, 550, 410, 20, 250, level);
    denWalls.add(denWest);
    denWalls.add(addWall(550, -10, 910, -10, 20, 250, level));
    denWalls.add(addWall(910, -10, 910, 410, 20, 250, level));
    denWalls.add(addWall(910, 410, 550, 410, 20, 250, level));
    finish(denWalls, 700, 200);
    Wall livingEast = null;
    for (Wall wall : this.home.getWalls()) {
      if (Math.abs(wall.getXStart() - 510) < 0.5 && Math.abs(wall.getXEnd() - 510) < 0.5) {
        livingEast = wall;
      }
    }
    assertNotNull(livingEast);
    // The sides in the cavity have no finish: nobody sees them
    setSide(livingEast, getSideFacing(livingEast, 700, 200), null, null);
    setSide(denWest, getSideFacing(denWest, 250, 200), null, null);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_SIDE_UNPAINTED).isEmpty());
    // Nor is the paint of the rooms reported there
    setSide(denWest, getSideFacing(denWest, 250, 200), this.paint, null);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS).isEmpty());
    // 80 cm apart, somebody walks between them
    this.home.deleteWall(livingEast);
    Wall farWall = addWall(450, -10, 450, 410, 20, 250, level);
    setSide(farWall, getSideFacing(farWall, 250, 200), this.paint, null);
    getRoom("Living room").setPoints(new float [][] {{0, 0}, {440, 0}, {440, 400}, {0, 400}});
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.WALL_SIDE_UNPAINTED);
    assertEquals(describe(), 1, issues.size());
    assertEquals(farWall, issues.get(0).getItem());
    assertTrue(issues.get(0).getMessage(), issues.get(0).getMessage().contains("faces the outside"));
    issues = getIssues(HomeDiagnostics.Type.WALL_INSIDE_FINISH_OUTDOORS);
    assertEquals(describe(), 1, issues.size());
    assertEquals(denWest, issues.get(0).getItem());
  }

  /**
   * The low walls that stand inside the space of rooms of two stories don't leave them open: the side wall
   * of stairs between two of them, and the closet under the stairs. The low wall of their outline does.
   */
  @Test
  public void testLowWallsInsideTwoStorySpace() {
    // A hall that turns around a closet, and a stairwell at its east, both 520 cm high
    Room hall = addRoom("Hall", null, 0, 0, 300, 0, 300, 250, 200, 250, 200, 400, 0, 400);
    Room stairwell = addRoom("Stairwell", null, 300, 0, 500, 0, 500, 400, 300, 400);
    hall.setProperty(CEILING_HEIGHT, "520");
    stairwell.setProperty(CEILING_HEIGHT, "520");
    Wall hallNorth = addWall(-5, -5, 300, -5, 10, 520, null);
    addWall(300, -5, 505, -5, 10, 520, null);
    addWall(505, -5, 505, 405, 10, 520, null);
    addWall(505, 405, -5, 405, 10, 520, null);
    addWall(-5, 405, -5, -5, 10, 520, null);
    assertTrue(describe(), getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM).isEmpty());
    // The side wall of the stairs between them, sloped from the floor
    Wall stairsWall = addWall(300, 0, 300, 250, 8, 3, null);
    stairsWall.setHeightAtEnd(300f);
    // The closet under the stairs, drawn without a room, its walls 244 cm high
    addWall(300, 250, 200, 250, 8, 244, null);
    addWall(200, 250, 200, 400, 8, 244, null);
    assertTrue("Low walls inside the open space: " + describe(),
        getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM).isEmpty());
    // The wall of the outline that stops at the height of the first story is still told
    hallNorth.setHeight(250f);
    List<HomeDiagnostics.Issue> issues = getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM);
    assertEquals(describe(), 1, issues.size());
    assertEquals(hallNorth, issues.get(0).getItem());
    assertEquals(hall, issues.get(0).getOtherItem());
    hallNorth.setHeight(520f);
    // As is a low wall between the hall and a room of one story
    stairwell.setProperty(CEILING_HEIGHT, null);
    issues = getIssues(HomeDiagnostics.Type.OPEN_TWO_STORY_ROOM);
    assertFalse(describe(), issues.isEmpty());
    for (HomeDiagnostics.Issue issue : issues) {
      assertEquals(hall, issue.getOtherItem());
    }
    boolean stairsWallTold = false;
    for (HomeDiagnostics.Issue issue : issues) {
      stairsWallTold |= issue.getItem() == stairsWall;
    }
    assertTrue(describe(), stairsWallTold);
  }
}
