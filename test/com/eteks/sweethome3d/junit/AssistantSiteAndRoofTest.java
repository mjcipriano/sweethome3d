/*
 * AssistantSiteAndRoofTest.java 21 nov. 2026
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.viewcontroller.HomeGeoreference;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.JSONParser;
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

/**
 * Tests the commands that change roofs and dormers, the ceilings under roofs, the site
 * (property lines, georeference, compass) and the tools around them.
 */
public class AssistantSiteAndRoofTest {
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
    this.preferences.getFurnitureCatalog().add(new com.eteks.sweethome3d.model.FurnitureCategory("Test openings"),
        new com.eteks.sweethome3d.model.CatalogDoorOrWindow(null, "Zz door", null, dummy, dummy, 90, 10, 205, 0, true,
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

  private List<HomePieceOfFurniture> getPieces(String name) {
    List<HomePieceOfFurniture> found = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (name.equals(piece.getName())) {
        found.add(piece);
      }
    }
    return found;
  }

  /**
   * A house of 10 m x 6 m with a gable roof whose ridge runs along x, eaves at 120 cm.
   */
  private HomePieceOfFurniture addHouse() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Attic\",\"x\":0,\"y\":0,\"width\":1000,\"depth\":600,\"thickness\":20,\"height\":250},"
        + "{\"action\":\"add_roof\",\"points\":[[-10,-10],[1010,-10],[1010,610],[-10,610]],\"eave_height\":120,\"pitch\":40,"
        + "\"overhang\":30,\"type\":\"gable\"}");
    return getPiece("Roof");
  }

  @Test
  public void testDormersAreChangedOneByOne() {
    HomePieceOfFurniture roof = addHouse();
    String roofId = HomeItemIds.getId(roof);
    // "position" beyond 1 is a distance, as "offset"
    String summary = run("{\"action\":\"add_dormer\",\"roof\":\"" + roofId + "\",\"side\":\"south\",\"position\":250,"
        + "\"width\":200,\"height\":150,\"window\":\"Zz roof window\",\"windows\":2}");
    assertTrue(summary, summary.contains("taken as a distance"));
    // The south edge goes from the east to the west: 250 cm from its west end is 770 cm from its start
    assertTrue(summary, summary.contains("its center 770 cm from"));
    List<HomePieceOfFurniture> windows = getPieces("Zz roof window");
    assertEquals(summary, 2, windows.size());
    // Two windows side by side in the front wall, 100 cm apart
    assertEquals(100, Math.abs(windows.get(0).getX() - windows.get(1).getX()), 0.5f);
    assertEquals(-10 + 250, (windows.get(0).getX() + windows.get(1).getX()) / 2, 0.5f);
    HomePieceOfFurniture firstWindow = windows.get(0);
    String firstWindowId = HomeItemIds.getId(firstWindow);
    // The user makes a window narrower
    firstWindow.setWidth(60);

    // A second dormer keeps the windows of the first one, with their ids and their size
    summary = run("{\"action\":\"add_dormer\",\"roof\":\"" + roofId + "\",\"side\":\"south\",\"offset\":750,"
        + "\"width\":160,\"height\":150,\"window\":\"Zz roof window\"}");
    assertTrue(summary, summary.contains("Added the dormer 2"));
    assertEquals(summary, 3, getPieces("Zz roof window").size());
    assertTrue(this.home.getFurniture().contains(firstWindow));
    assertEquals(firstWindowId, HomeItemIds.getId(firstWindow));
    assertEquals(60, firstWindow.getWidth(), 0.01f);

    // Moving the first dormer moves its windows with it
    float x = firstWindow.getX();
    summary = run("{\"action\":\"modify_dormer\",\"roof\":\"" + roofId + "\",\"index\":1,\"offset\":300,\"type\":\"shed\"}");
    assertTrue(summary, summary.contains("Modified the dormer 1"));
    assertTrue(summary, summary.contains("1: shed"));
    assertEquals(x + 50, firstWindow.getX(), 0.5f);
    assertEquals(3, getPieces("Zz roof window").size());
    this.homeController.undo();
    assertEquals(x, firstWindow.getX(), 0.5f);
    this.homeController.redo();

    // Removing the first dormer removes its two windows and keeps the one of the other dormer
    summary = run("{\"action\":\"remove_dormer\",\"roof\":\"" + roofId + "\",\"index\":1}");
    assertTrue(summary, summary.contains("Removed the dormer 1"));
    windows = getPieces("Zz roof window");
    assertEquals(summary, 1, windows.size());
    assertEquals(-10 + 750, windows.get(0).getX(), 0.5f);
    assertFalse(this.home.getFurniture().contains(firstWindow));
    this.homeController.undo();
    assertEquals(3, getPieces("Zz roof window").size());
    assertTrue(this.home.getFurniture().contains(firstWindow));
    this.homeController.redo();

    // A dormer that doesn't exist
    summary = run("{\"action\":\"remove_dormer\",\"roof\":\"" + roofId + "\",\"index\":5}");
    assertTrue(summary, summary.contains("from 1 to 1"));
    // describe_item tells the edges and the dormers of a roof
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + roofId + "\"}");
    assertTrue(summary, summary.contains("edges: 1 hip"));
    assertTrue(summary, summary.contains("its dormers: 1: gable, 160 cm wide"));
  }

  @Test
  public void testWallsAreFittedUnderDormers() {
    HomePieceOfFurniture roof = addHouse();
    String roofId = HomeItemIds.getId(roof);
    // A cross gable 300 cm wide over the south wall, its ridge as high as the one of the roof
    String summary = run("{\"action\":\"add_dormer\",\"roof\":\"" + roofId + "\",\"side\":\"south\",\"offset\":500,"
        + "\"width\":300,\"height\":130,\"setback\":0,\"ridge\":\"match\"}");
    assertTrue(summary, summary.contains("1: gable, 300 cm wide"));
    // A closet wall under it, from the south wall to the middle of the house, at the level of the roof
    Level upper = null;
    run("{\"action\":\"add_level\",\"name\":\"Other\"}");
    for (Level level : this.home.getLevels()) {
      if (!"Other".equals(level.getName())) {
        upper = level;
      }
    }
    assertNotNull(upper);
    // The level selected isn't the one of the walls
    assertEquals("Other", this.home.getSelectedLevel().getName());
    Wall closet = new Wall(500, 600, 500, 300, 10, 250);
    this.home.addWall(closet);
    closet.setLevel(upper);
    Wall side = new Wall(100, 600, 100, 0, 10, 250);
    this.home.addWall(side);
    side.setLevel(upper);
    // Two walls start at the end of the side wall, which will be cut under the ridge
    Wall branch = new Wall(100, 0, 300, 100, 10, 250);
    this.home.addWall(branch);
    branch.setLevel(upper);
    Wall otherBranch = new Wall(100, 0, 20, 100, 10, 250);
    this.home.addWall(otherBranch);
    otherBranch.setLevel(upper);
    side.setWallAtEnd(branch);
    branch.setWallAtStart(side);
    otherBranch.setWallAtStart(side);
    Wall front = null;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getYStart() == 610 && wall.getYEnd() == 610) {
        front = wall;
      }
    }
    int wallCount = this.home.getWalls().size();
    summary = run("{\"action\":\"fit_walls_to_roof\",\"roof\":\"" + roofId + "\",\"all\":true}");
    assertTrue(summary, summary.contains("Fitted"));
    // The wall along the front of the cross gable was cut in pieces that go up to its peak
    float frontTop = 0;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getYStart() == 610 && wall.getYEnd() == 610) {
        frontTop = Math.max(frontTop, Math.max(wall.getHeight(), wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : 0));
      }
    }
    assertNotNull(front);
    assertTrue("front " + frontTop, frontTop > 300);
    // Under the ridge of the cross gable the wall is much higher than under the slope of the roof beside it:
    // 120 + 300 tan 40 = 372 cm at the ridge of the roof, minus its thickness
    float closetHeight = Math.max(closet.getHeight(), closet.getHeightAtEnd() != null ? closet.getHeightAtEnd() : 0);
    assertTrue("closet " + closet.getHeight() + " " + closet.getHeightAtEnd(), closetHeight > 300);
    // At the south wall, under the eave of the roof, it would be 120 cm high without the cross gable
    assertTrue("closet start " + closet.getHeight(), closet.getHeight() > 200);
    // The wall away from the cross gable follows the slope of the roof, from its eave
    assertTrue("side " + side.getHeight(), side.getHeight() < 140);
    // The walls joined to the end of a cut wall are joined to its last piece
    Wall lastPiece = branch.getWallAtStart();
    assertNotNull(lastPiece);
    assertTrue(lastPiece != side);
    assertEquals(0, lastPiece.getYEnd(), 0.01f);
    assertSame(lastPiece, otherBranch.getWallAtStart());
    assertSame(branch, lastPiece.getWallAtEnd());
    // The walls added where a wall is cut are at the level of that wall
    assertTrue(summary, this.home.getWalls().size() > wallCount);
    for (Wall wall : this.home.getWalls()) {
      assertEquals(upper, wall.getLevel());
    }
  }

  @Test
  public void testRoofIsModified() {
    HomePieceOfFurniture roof = addHouse();
    String roofId = HomeItemIds.getId(roof);
    float height = roof.getHeight();
    Wall wall = this.home.getWalls().iterator().next();
    assertEquals(250, wall.getHeight(), 0.01f);
    String summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"eave_height\":200,\"raise_walls\":true}");
    assertTrue(summary, summary.contains("eaves at 200 cm"));
    assertTrue(summary, summary.contains("raised by 80 cm"));
    assertEquals(330, wall.getHeight(), 0.01f);
    assertEquals(HomeItemIds.getId(roof), roofId);
    this.homeController.undo();
    assertEquals(250, wall.getHeight(), 0.01f);
    assertEquals(height, roof.getHeight(), 0.01f);
    // The pitch
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"pitch\":20}");
    assertTrue(summary, roof.getHeight() < height - 50);
    // One edge: the west gable becomes a hip, the east one abuts a taller wall
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":[{\"index\":4,\"type\":\"hip\"},{\"index\":2,\"type\":\"abut\"}]}");
    assertTrue(summary, summary.contains("3 eaves and 1 gable"));
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + roofId + "\"}");
    assertTrue(summary, summary.contains("2 abut"));
    assertTrue(summary, summary.contains("4 hip"));
    // An abutting edge has no overhang: the roof ends at x = 1010
    assertEquals(1010, roof.getX() + roof.getWidth() / 2, 0.5f);
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\"}");
    assertTrue(summary, summary.contains("give what changes"));
  }

  private static double number(String text, String before) {
    int start = text.indexOf(before) + before.length();
    int end = start;
    while (end < text.length() && (Character.isDigit(text.charAt(end)) || text.charAt(end) == '.' || text.charAt(end) == '-')) {
      end++;
    }
    return Double.parseDouble(text.substring(start, end));
  }

  @Test
  public void testCeilingFollowsRoofAndHeadroom() {
    HomePieceOfFurniture roof = addHouse();
    Room attic = this.home.getRooms().get(0);
    String atticId = HomeItemIds.getId(attic);
    String summary = run("{\"action\":\"modify_room\",\"id\":\"" + atticId + "\",\"ceiling\":\"follow_roof\"}");
    HomePieceOfFurniture ceiling = getPiece("Attic ceiling");
    assertNotNull(summary, ceiling);
    assertFalse(attic.isCeilingVisible());
    // The plan doesn't draw the ceiling over the room
    assertEquals(FurniturePlanVisibility.HIDE, FurniturePlanVisibility.getAtLevel(ceiling, attic.getLevel()));
    // The underside of the roof is 120 cm above the floor at its edge, 10 cm outside the room,
    // and rises by tan 40 = 0.839 to the ridge, 310 cm from the edge
    assertTrue(summary, summary.contains("headroom from"));
    double lowest = number(summary, "headroom from ");
    double highest = number(summary.substring(summary.indexOf("headroom from")), " to ");
    assertEquals(summary, 120 + 10 * Math.tan(Math.toRadians(40)), lowest, 6);
    assertEquals(summary, 120 + 310 * Math.tan(Math.toRadians(40)), highest, 6);
    // At least 213 cm high from 101 cm inside the room on each side: 398 cm of its 600 cm
    assertTrue(summary, summary.contains("(66 %)") || summary.contains("(67 %)") || summary.contains("(65 %)"));
    // The ceiling is under the roof and as high as its slopes
    assertEquals(lowest, ceiling.getElevation(), 8);
    // Measured between samples a few centimeters inside the room
    assertEquals(highest - lowest, ceiling.getHeight(), 14);
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("ceiling"));

    // Flat from 240 cm, as under collar ties
    summary = run("{\"action\":\"modify_room\",\"id\":\"" + atticId + "\",\"flat_ceiling_at\":240}");
    assertTrue(summary, summary.contains("flat at 240 cm"));
    assertEquals(summary, 240, number(summary.substring(summary.indexOf("headroom from")), " to "), 0.5);
    assertEquals(1, getPieces("Attic ceiling").size());
    assertEquals(240 - lowest, getPiece("Attic ceiling").getHeight(), 8);
    summary = run("{\"action\":\"headroom\"}");
    assertTrue(summary, summary.contains(atticId + " \"Attic\": headroom from"));
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + atticId + "\"}");
    assertTrue(summary, summary.contains("headroom from"));

    // The ceiling follows the roof when it changes
    summary = run("{\"action\":\"modify_roof\",\"eave_height\":180}");
    assertTrue(summary, summary.contains("1 ceiling that follows the roof was built again"));
    assertEquals(lowest + 60, getPiece("Attic ceiling").getElevation(), 8);

    // A door under the eave goes through the roof
    run("{\"action\":\"modify_roof\",\"eave_height\":120}");
    run("{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":500,\"y\":610,\"angle\":0}");
    HomePieceOfFurniture door = getPiece("Zz door");
    assertNotNull(door);
    summary = run("{\"action\":\"check_home\"}");
    assertTrue(summary, summary.contains("error [door_through_roof] " + HomeItemIds.getId(door)));
    assertTrue(summary, summary.contains("1 error"));
    // Errors come first
    assertTrue(summary, summary.indexOf("- error") < summary.indexOf("- warning") || summary.indexOf("- warning") < 0);
    // Ignored on request, by id or by tag
    summary = run("{\"action\":\"check_home\",\"ignore\":[\"" + HomeItemIds.getId(door) + "\"]}");
    assertFalse(summary, summary.contains("door_through_roof"));
    assertTrue(summary, summary.contains("ignored on request"));
    run("{\"action\":\"add_tags\",\"ids\":[\"" + HomeItemIds.getId(door) + "\"],\"tags\":[\"marker\"]}");
    summary = run("{\"action\":\"check_home\",\"ignore_tags\":[\"marker\"],\"remember\":true}");
    assertFalse(summary, summary.contains("door_through_roof"));
    summary = run("{\"action\":\"check_home\"}");
    assertFalse("Remembered: " + summary, summary.contains("door_through_roof"));
    summary = run("{\"action\":\"check_home\",\"forget\":true,\"min_severity\":\"error\"}");
    assertTrue(summary, summary.contains("door_through_roof"));
    assertFalse(summary, summary.contains("- warning"));

    // Back to the flat ceiling of the level
    summary = run("{\"action\":\"modify_room\",\"id\":\"" + atticId + "\",\"ceiling\":\"flat\"}");
    assertNull(summary, getPiece("Attic ceiling"));
    assertTrue(attic.isCeilingVisible());
    assertNotNull(roof);
  }

  @Test
  public void testLinesSetbacksAndSelection() {
    addHouse();
    String summary = run("{\"action\":\"add_property_line\",\"points\":[[-1000,-1000],[2000,-1000],[2000,2000],[-1000,2000]],"
        + "\"label\":\"Lot 12\",\"stakes\":true}");
    assertEquals(summary, 1, this.home.getPolylines().size());
    Polyline line = this.home.getPolylines().get(0);
    String lineId = HomeItemIds.getId(line);
    assertEquals("P1", lineId);
    assertTrue(summary, summary.contains("Added the property line P1 \"Lot 12\""));
    assertTrue(summary, summary.contains("900 m2"));
    assertTrue(summary, summary.contains("Ids of what was created:") && summary.contains("P1 \"Lot 12\""));
    assertTrue(line.isClosedPath());
    assertEquals(Polyline.DashStyle.DASH_DOT, line.getDashStyle());
    assertEquals(1, this.home.getLabels().size());
    // Stakes and a string in 3D
    HomePieceOfFurniture stakes = getPiece("Lot 12 stakes");
    assertNotNull(summary, stakes);
    assertEquals(3000, stakes.getWidth(), 10);

    // A setback of 5 m leaves the house inside it
    summary = run("{\"action\":\"add_setback\",\"from\":\"P1\",\"distance\":500}");
    assertTrue(summary, summary.contains("Added the setback line P2"));
    assertTrue(summary, summary.contains("400 m2"));
    Polyline setback = this.home.getPolylines().get(1);
    float [][] points = setback.getPoints();
    assertEquals(4, points.length);
    for (float [] point : points) {
      assertTrue(Math.abs(point [0] + 500) < 0.1 || Math.abs(point [0] - 1500) < 0.1);
      assertTrue(Math.abs(point [1] + 500) < 0.1 || Math.abs(point [1] - 1500) < 0.1);
    }
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("beyond"));
    // One of 12 m doesn't: the house is 10 m wide in a strip of 6 m
    summary = run("{\"action\":\"add_setback\",\"from\":\"P1\",\"distance\":1200,\"name\":\"Side yards\"}");
    summary = run("{\"action\":\"check_home\"}");
    assertTrue(summary, summary.contains("error [beyond_setback]"));
    assertTrue(summary, summary.contains("beyond the setback line P3 \"Side yards\""));
    this.homeController.undo();
    assertEquals(2, this.home.getPolylines().size());

    // What is in an area
    run("{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":300,\"y\":300},"
        + "{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":800,\"y\":300}");
    summary = run("{\"action\":\"select\",\"where\":{\"bbox\":[200,200,400,400]}}");
    assertTrue(summary, summary.contains("Selected 1 item"));
    assertTrue(summary, summary.contains("\"Desk\" at (300, 300)"));
    assertEquals(1, this.home.getSelectedItems().size());
    summary = run("{\"action\":\"select\",\"where\":{\"type\":\"polylines\"}}");
    assertTrue(summary, summary.contains("Selected 2 items"));
    summary = run("{\"action\":\"select\",\"where\":{\"name\":\"zz|desk\",\"type\":\"furniture\"}}");
    assertTrue(summary, summary.contains("Selected 2 items"));
    summary = run("{\"action\":\"select\",\"where\":{\"bbox\":[5000,5000,6000,6000]}}");
    assertTrue(summary, summary.contains("found no item"));

    // Lines are changed and deleted by their id
    summary = run("{\"action\":\"modify_polyline\",\"id\":\"P2\",\"color\":\"#0000FF\",\"dash\":\"dotted\",\"name\":\"Front yard\"}");
    assertEquals(0xFF0000FF, setback.getColor());
    assertEquals(Polyline.DashStyle.DOT, setback.getDashStyle());
    this.homeController.undo();
    assertEquals(Polyline.DashStyle.DASH, setback.getDashStyle());
    summary = run("{\"action\":\"describe_item\",\"id\":\"P1\"}");
    assertTrue(summary, summary.contains("Line P1: 4 points [-1000, -1000]"));
    summary = run("{\"action\":\"add_polyline\",\"points\":[[0,700],[1000,700]],\"color\":\"#00AA00\",\"thickness\":3,"
        + "\"dash\":\"dashed\",\"name\":\"Sewer\"}");
    assertTrue(summary, summary.contains("Added the line P") && summary.contains("10 m long"));
    assertEquals(3, this.home.getPolylines().size());
    run("{\"action\":\"delete\",\"ids\":[\"P2\"]}");
    assertEquals(2, this.home.getPolylines().size());
    assertFalse(this.home.getPolylines().contains(setback));
    // The description of the home lists the lines
    String description = HomeAssistantContext.describeHome(this.home);
    assertTrue(description, description.contains("P1 \"Lot 12\" (property line): 4 points, closed"));
  }

  @Test
  public void testGeoreferenceAndParcels() throws Exception {
    addHouse();
    String summary = run("{\"action\":\"import_parcel\",\"geojson\":{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,1],[0,1]]]},\"srs\":\"EPSG:9999\"}");
    assertTrue(summary, summary.contains("isn't built in"));
    summary = run("{\"action\":\"get_coordinates\",\"points\":[[0,0]]}");
    assertTrue(summary, summary.contains("no georeference"));

    // The corner of the house at 33.9 N 84.3 W, the plan turned by 30 degrees
    summary = run("{\"action\":\"set_georeference\",\"lat\":33.9,\"lon\":-84.3,\"x\":0,\"y\":0,\"rotation\":30}");
    assertTrue(summary, summary.contains("Set the georeference"));
    assertEquals(33.9, Math.toDegrees(this.home.getCompass().getLatitude()), 1E-4);
    assertEquals(-84.3, Math.toDegrees(this.home.getCompass().getLongitude()), 1E-4);
    assertEquals(30, Math.toDegrees(this.home.getCompass().getNorthDirection()), 1E-3);
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    // 100 m to the north is up and to the right in the plan
    double [] north = georeference.toPlan(33.9 + 100 / 110922.4, -84.3);
    assertEquals(10000 * Math.sin(Math.toRadians(30)), north [0], 2);
    assertEquals(-10000 * Math.cos(Math.toRadians(30)), north [1], 2);
    double [] back = georeference.toLatitudeLongitude(north [0], north [1]);
    assertEquals(33.9 + 100 / 110922.4, back [0], 1E-9);
    assertEquals(-84.3, back [1], 1E-9);
    summary = run("{\"action\":\"get_coordinates\",\"points\":[[0,0]],\"srs\":\"EPSG:2240\"}");
    assertTrue(summary, summary.contains("33.9000000, -84.3000000"));
    this.homeController.undo();
    assertNull(HomeGeoreference.get(this.home));
    this.homeController.redo();

    // Two points known in the plan and on the ground give the direction of the north
    double [] east = new HomeGeoreference(33.9, -84.3, 0, 0, 0).toLatitudeLongitude(10000, 0);
    summary = run("{\"action\":\"set_georeference\",\"points\":[{\"lat\":33.9,\"lon\":-84.3,\"x\":100,\"y\":200},"
        + "{\"lat\":" + east [0] + ",\"lon\":" + east [1] + ",\"x\":100,\"y\":10200}]}");
    // What is 100 m to the east on the ground is 100 m down the plan: the north is at its right
    assertEquals(summary, 90, HomeGeoreference.get(this.home).getRotation(), 0.01);
    assertFalse(summary, summary.contains("isn't at the scale"));
    run("{\"action\":\"set_georeference\",\"lat\":33.9,\"lon\":-84.3,\"x\":0,\"y\":0,\"rotation\":0}");

    // A lot of 30 m x 20 m given in the state plane coordinates of the county, in US survey feet
    HomeGeoreference.Projection statePlane = HomeGeoreference.Projection.get("EPSG:2240");
    georeference = HomeGeoreference.get(this.home);
    StringBuilder ring = new StringBuilder();
    float [][] corners = {{-500, -400}, {2500, -400}, {2500, 1600}, {-500, 1600}, {-500, -400}};
    for (float [] corner : corners) {
      double [] place = georeference.toLatitudeLongitude(corner [0], corner [1]);
      double [] projected = statePlane.fromLatitudeLongitude(place [0], place [1]);
      ring.append(ring.length() > 0 ? "," : "").append("[").append(projected [0]).append(",").append(projected [1]).append("]");
    }
    summary = run("{\"action\":\"import_parcel\",\"esrijson\":{\"rings\":[[" + ring + "]],\"spatialReference\":{\"wkid\":102667,\"latestWkid\":2240}},"
        + "\"name\":\"Parcel\"}");
    assertTrue(summary, summary.contains("Imported 1 property line from coordinates in EPSG:2240"));
    assertTrue(summary, summary.contains("600 m2"));
    assertEquals(1, this.home.getPolylines().size());
    float [][] points = this.home.getPolylines().get(0).getPoints();
    assertEquals(4, points.length);
    for (int i = 0; i < 4; i++) {
      assertEquals(corners [i][0], points [i][0], 0.5f);
      assertEquals(corners [i][1], points [i][1], 0.5f);
    }
    // The same lot as GeoJSON in longitudes and latitudes, in a file
    StringBuilder geoRing = new StringBuilder();
    for (float [] corner : corners) {
      double [] place = georeference.toLatitudeLongitude(corner [0] + 5000, corner [1]);
      geoRing.append(geoRing.length() > 0 ? "," : "").append("[").append(place [1]).append(",").append(place [0]).append("]");
    }
    File file = File.createTempFile("parcel", ".geojson");
    java.io.FileWriter writer = new java.io.FileWriter(file);
    writer.write("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},"
        + "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[" + geoRing + "]]}}]}");
    writer.close();
    summary = run("{\"action\":\"import_parcel\",\"path\":\"" + file.getAbsolutePath().replace("\\", "\\\\") + "\"}");
    file.delete();
    assertTrue(summary, summary.contains("coordinates in EPSG:4326"));
    assertEquals(4500, this.home.getPolylines().get(1).getPoints() [0][0], 0.5f);

    // The compass alone
    summary = run("{\"action\":\"set_compass\",\"north_direction\":45,\"x\":-300,\"y\":-300,\"diameter\":150}");
    assertEquals(45, Math.toDegrees(this.home.getCompass().getNorthDirection()), 1E-3);
    assertEquals(-300, this.home.getCompass().getX(), 0.01f);
    assertEquals(150, this.home.getCompass().getDiameter(), 0.01f);
    this.homeController.undo();
    assertEquals(0, Math.toDegrees(this.home.getCompass().getNorthDirection()), 1E-3);
  }

  /**
   * Checks the projections with formulas that don't share their computation.
   */
  @Test
  public void testProjections() throws Exception {
    double a = 6378137;
    double f = 1 / 298.257222101;
    double e2 = f * (2 - f);
    double usFoot = 1200. / 3937;
    // The origin of the Georgia West zone
    HomeGeoreference.Projection west = HomeGeoreference.Projection.get("EPSG:2240");
    double [] origin = west.fromLatitudeLongitude(30, -(84 + 1 / 6.));
    assertEquals(700000 / usFoot, origin [0], 0.001);
    assertEquals(0, origin [1], 0.001);
    // Along the central meridian, the northing is the length of the meridian times the scale 0.9999
    double [] north = west.fromLatitudeLongitude(34, -(84 + 1 / 6.));
    assertEquals(0.9999 * (getMeridianArc(34, a, e2) - getMeridianArc(30, a, e2)) / usFoot, north [1], 0.005);
    assertEquals(700000 / usFoot, north [0], 0.001);
    // A thousandth of a degree to the east is the radius of the parallel times that angle
    double sin = Math.sin(Math.toRadians(34));
    double primeVertical = a / Math.sqrt(1 - e2 * sin * sin);
    double [] east = west.fromLatitudeLongitude(34, -(84 + 1 / 6.) + 0.001);
    assertEquals(0.9999 * primeVertical * Math.cos(Math.toRadians(34)) * Math.toRadians(0.001) / usFoot, east [0] - north [0], 0.002);
    // Back
    double [] place = west.toLatitudeLongitude(2229000, 1366000);
    double [] again = west.fromLatitudeLongitude(place [0], place [1]);
    assertEquals(2229000, again [0], 0.001);
    assertEquals(1366000, again [1], 0.001);
    assertEquals(33.75, place [0], 0.05);
    assertEquals(-84.39, place [1], 0.05);
    // The same with a PROJ text
    HomeGeoreference.Projection text = HomeGeoreference.Projection.get(
        "+proj=tmerc +lat_0=30 +lon_0=-84.16666666666667 +k=0.9999 +x_0=699999.9999999999 +y_0=0 +units=us-ft");
    assertEquals(again [0], text.fromLatitudeLongitude(place [0], place [1]) [0], 0.001);
    // The Eiffel tower in UTM zone 31
    double [] utm = HomeGeoreference.Projection.get("EPSG:32631").fromLatitudeLongitude(48.8582, 2.2945);
    assertEquals(448251.8, utm [0], 1);
    assertEquals(5411932.7, utm [1], 1);
    // Web maps
    double [] web = HomeGeoreference.Projection.get("EPSG:3857").fromLatitudeLongitude(0, 180);
    assertEquals(20037508.34, web [0], 0.01);
    double [] webBack = HomeGeoreference.Projection.get("EPSG:3857").toLatitudeLongitude(-9393970, 3994000);
    assertEquals(-84.388, webBack [1], 0.001);
    assertEquals(33.75, webBack [0], 0.05);
    // Lambert conformal conic: its origin, its scale on a standard parallel, and back
    HomeGeoreference.Projection lambert = HomeGeoreference.Projection.get(
        "+proj=lcc +lat_1=49 +lat_2=44 +lat_0=46.5 +lon_0=3 +x_0=700000 +y_0=6600000 +units=m");
    double [] lambertOrigin = lambert.fromLatitudeLongitude(46.5, 3);
    assertEquals(700000, lambertOrigin [0], 0.001);
    assertEquals(6600000, lambertOrigin [1], 0.001);
    double sin49 = Math.sin(Math.toRadians(49));
    double parallelRadius = a / Math.sqrt(1 - e2 * sin49 * sin49) * Math.cos(Math.toRadians(49));
    double [] onParallel = lambert.fromLatitudeLongitude(49, 3);
    double [] beside = lambert.fromLatitudeLongitude(49, 3.001);
    assertEquals(parallelRadius * Math.toRadians(0.001), Math.hypot(beside [0] - onParallel [0], beside [1] - onParallel [1]), 0.001);
    double [] lambertPlace = lambert.toLatitudeLongitude(648237, 6862272);
    assertEquals(48.858, lambertPlace [0], 0.01);
    assertEquals(2.2945, lambertPlace [1], 0.01);
    double [] lambertAgain = lambert.fromLatitudeLongitude(lambertPlace [0], lambertPlace [1]);
    assertEquals(648237, lambertAgain [0], 0.001);
    assertEquals(6862272, lambertAgain [1], 0.001);
  }

  /**
   * Returns the length of the meridian from the equator to a latitude, by its series in e2.
   */
  private static double getMeridianArc(double latitude, double a, double e2) {
    double phi = Math.toRadians(latitude);
    double e4 = e2 * e2;
    double e6 = e4 * e2;
    return a * ((1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
        - (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * phi)
        + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * phi)
        - 35 * e6 / 3072 * Math.sin(6 * phi));
  }

  @Test
  public void testGroupsFilesAndIds() throws Exception {
    // Several calls undone as one step
    String summary = run("{\"action\":\"begin_group\"}");
    assertTrue(summary, summary.contains("Started a group"));
    summary = run("{\"action\":\"add_rectangular_room\",\"name\":\"Shed\",\"x\":0,\"y\":0,\"width\":300,\"depth\":300}");
    // Every reply names what was created
    assertTrue(summary, summary.contains("Ids of what was created: R1 \"Shed\", W1, W2, W3, W4"));
    summary = run("{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":150,\"y\":150}");
    assertTrue(summary, summary.contains("Ids of what was created: F1 \"Desk\""));
    summary = run("{\"action\":\"end_group\"}");
    assertTrue(summary, summary.contains("Ended the group"));
    assertEquals(1, this.home.getFurniture().size());
    this.homeController.undo();
    assertEquals(0, this.home.getFurniture().size());
    assertEquals(0, this.home.getRooms().size());
    assertEquals(0, this.home.getWalls().size());
    this.homeController.redo();
    assertEquals(4, this.home.getWalls().size());
    assertTrue(run("{\"action\":\"end_group\"}").contains("No group was started"));
    // A group left open is ended after a while
    this.executor.beginGroup();
    assertFalse(this.executor.endIdleGroup(60000));
    assertTrue(this.executor.endIdleGroup(-1));
    assertFalse(this.executor.isGroupOpen());

    // Levels are deleted by their ids
    run("{\"action\":\"add_level\",\"name\":\"A\"},{\"action\":\"add_level\",\"name\":\"B\"}");
    assertEquals(3, this.home.getLevels().size());
    summary = run("{\"action\":\"delete_level\",\"ids\":[\"" + HomeItemIds.getId(this.home.getLevels().get(1)) + "\",\""
        + HomeItemIds.getId(this.home.getLevels().get(2)) + "\"]}");
    assertEquals(summary, 1, this.home.getLevels().size());

    // A part 45 m long, and one 150 m away
    summary = run("{\"action\":\"create_furniture\",\"name\":\"Line\",\"x\":0,\"y\":0,\"parts\":["
        + "{\"shape\":\"box\",\"width\":4572,\"depth\":5,\"height\":5},"
        + "{\"shape\":\"box\",\"x\":15000,\"width\":10,\"depth\":10,\"height\":100}]}");
    HomePieceOfFurniture line = getPiece("Line");
    assertNotNull(summary, line);
    assertEquals(15005 + 2286, line.getWidth(), 1);
    summary = run("{\"action\":\"create_furniture\",\"name\":\"Too far\",\"parts\":[{\"shape\":\"box\",\"x\":30000,\"width\":10,\"depth\":10,\"height\":10}]}");
    assertTrue(summary, summary.contains("no part could be built") && summary.contains("20000 cm"));

    // A string that follows the ground: 0 at x = 0, 200 cm at x = 2000
    run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,-100],[3000,-1000,300],[3000,2000,300],[-1000,2000,-100]]}");
    summary = run("{\"action\":\"create_furniture\",\"name\":\"String\",\"x\":0,\"y\":1000,\"on_terrain\":true,\"parts\":["
        + "{\"shape\":\"extrude\",\"path\":[[0,0,50],[2000,0,50]],\"diameter\":2}]}");
    HomePieceOfFurniture string = getPiece("String");
    assertNotNull(summary, string);
    assertEquals(200, string.getHeight(), 5);
    assertEquals(50, string.getElevation() + string.getLevel().getElevation(), 5);
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("String"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testToolsOfTheServer() throws Exception {
    addHouse();
    HomeMCPHandler handler = new HomeMCPHandler(this.home, this.preferences, this.homeController, "test");
    handler.setEditingAllowed(true);
    // An argument the tool doesn't have is told, not dropped
    String text = callTool(handler, "get_home", "{\"levle\":\"x\"}");
    assertTrue(text, text.contains("has no argument [levle]") && text.contains("where"));
    // The tools tell that their list may change
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"));
    Map<String, Object> capabilities = (Map<String, Object>)((Map<String, Object>)response.get("result")).get("capabilities");
    assertEquals(Boolean.TRUE, ((Map<String, Object>)capabilities.get("tools")).get("listChanged"));
    // The description of apply_commands lists the commands of this version
    text = handler.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
    for (String command : new String [] {"add_roof", "add_dormer", "modify_dormer", "remove_dormer", "modify_roof", "add_slab",
                                         "set_terrain", "modify_wall", "modify_level", "delete_level", "merge_levels",
                                         "set_plan_visibility", "describe_item", "add_polyline", "add_property_line",
                                         "add_setback", "set_compass", "set_georeference", "import_parcel", "begin_group",
                                         "headroom", "\\\"where\\\""}) {
      assertTrue(command, text.contains(command));
    }
    // A short list of the items of an area, with the fields asked for
    run("{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":300,\"y\":300}");
    text = callTool(handler, "get_home", "{\"where\":{\"bbox\":[0,0,1000,600],\"type\":\"furniture\"},\"fields\":[\"name\",\"position\"]}");
    // The desk and the roof, which is a piece of furniture
    assertTrue(text, text.startsWith("2 items matching the filter"));
    assertTrue(text, text.contains("\"Desk\" at (300, 300)"));
    assertFalse(text, text.contains("Level"));
    text = callTool(handler, "get_home", "{\"name\":\"roof|desk\",\"type\":\"furniture\"}");
    assertTrue(text, text.contains("\"Desk\"") && text.contains("\"Roof\""));
    // Commands read in a file
    File file = File.createTempFile("commands", ".json");
    java.io.FileWriter writer = new java.io.FileWriter(file);
    writer.write("{\"commands\":[{\"action\":\"add_furniture\",\"name\":\"Desk\",\"x\":600,\"y\":300},"
        + "{\"action\":\"add_label\",\"text\":\"From a file\",\"x\":0,\"y\":0}]}");
    writer.close();
    text = callTool(handler, "apply_commands", "{\"file\":\"" + file.getAbsolutePath().replace("\\", "\\\\") + "\"}");
    file.delete();
    assertTrue(text, text.contains("Ids of what was created: F"));
    assertEquals(2, getPieces("Desk").size());
    text = callTool(handler, "apply_commands", "{\"file\":\"/no/such/file.json\"}");
    assertTrue(text, text.contains("must be the absolute path of a JSON file that exists"));
    // check_home with its options
    text = callTool(handler, "check_home", "{\"min_severity\":\"error\"}");
    assertTrue(text, text.startsWith("Home check"));
    // The program between a client and the server reads the tools of an answer
    Object tools = com.eteks.sweethome3d.swing.HomeMCPBridge.getTools(
        handler.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}"));
    assertTrue(tools instanceof List && ((List<Object>)tools).size() > 10);
    assertNull(com.eteks.sweethome3d.swing.HomeMCPBridge.getTools("not json"));
    // A point of view stored from a camera, without moving the one of the user
    float x = this.home.getCamera().getX();
    text = callTool(handler, "apply_commands", "{\"commands\":[{\"action\":\"store_point_of_view\",\"name\":\"Photo 1\","
        + "\"camera\":{\"x\":-500,\"y\":1500,\"height\":170,\"look_at_x\":500,\"look_at_y\":300}}]}");
    assertTrue(text, text.contains("Stored the point of view \"Photo 1\""));
    assertEquals(1, this.home.getStoredCameras().size());
    assertEquals(-500, this.home.getStoredCameras().get(0).getX(), 1);
    assertEquals(x, this.home.getCamera().getX(), 0.01f);
    assertEquals(this.home.getTopCamera(), this.home.getCamera());
  }

  @SuppressWarnings("unchecked")
  private static String callTool(HomeMCPHandler handler, String name, String arguments) {
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}"));
    Map<String, Object> result = (Map<String, Object>)response.get("result");
    return (String)((Map<String, Object>)((List<Object>)result.get("content")).get(0)).get("text");
  }
}
