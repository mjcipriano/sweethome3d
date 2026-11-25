/*
 * AssistantCeilingCommandsTest.java 25 nov. 2026
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.media.j3d.Geometry;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Shape3D;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.j3d.Room3D;
import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Tests the ceilings placed at their own height, the height of levels and the rooms open to the level above.
 * @author Sweet Home 3D contributors
 */
public class AssistantCeilingCommandsTest {
  private static final String CEILING_HEIGHT = "com.eteks.sweethome3d.ceilingHeight";

  private String                   previousNo3D;
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private Level                    ground;
  private Level                    upper;
  private Room                     family;
  private Room                     kitchen;
  private Room                     upperHall;
  private Room                     roomAboveFamily;
  private Wall                     southWall;
  private Wall                     westWall;
  private Wall                     overlookWall;
  private HomePieceOfFurniture     hatch;

  /**
   * A house of two levels 250 cm high. On the ground: a family room of 5 x 4 m between x 0 and 500,
   * y 0 and 400, a kitchen at its east and a hall at its north. Above: a hall at the north, a room
   * drawn over the family room, and a bedroom over the kitchen. The wall of the upper level between
   * the hall and the room over the family room holds a hatch.
   */
  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    Content icon = new URLContent(new File("dummy.png").toURI().toURL());
    Content model = new URLContent(new File("dummy.obj").toURI().toURL());
    FurnitureCategory stairs = new FurnitureCategory("Overlook test");
    preferences.getFurnitureCatalog().add(stairs,
        new CatalogPieceOfFurniture("Handrail", icon, model, 100, 8, 90, true, false));
    CatalogDoorOrWindow hatchPiece = new CatalogDoorOrWindow("Service hatch", icon, model, 80, 10, 100, 90, true,
        1, 0, new Sash [0], null, new float [][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, true, 0, true);
    preferences.getFurnitureCatalog().add(stairs, hatchPiece);

    this.home = new Home(250);
    this.homeController = new HomeController(this.home, preferences, new SwingViewFactory());
    this.ground = new Level("Ground", 0, 12, 250);
    this.upper = new Level("Level 1", 262, 12, 250);
    this.home.addLevel(this.ground);
    this.home.addLevel(this.upper);

    this.home.setSelectedLevel(this.ground);
    this.family = addRoom("Family", 0, 0, 500, 400);
    this.kitchen = addRoom("Kitchen", 500, 0, 900, 400);
    addRoom("Hall", 0, -200, 900, 0);
    addWall(0, -200, 900, -200);
    addWall(900, -200, 900, 400);
    this.southWall = addWall(0, 400, 500, 400);
    addWall(500, 400, 900, 400);
    this.westWall = addWall(0, 400, 0, 0);
    addWall(0, 0, 0, -200);
    addWall(500, 0, 500, 400);
    addWall(0, 0, 500, 0);
    addWall(500, 0, 900, 0);

    this.home.setSelectedLevel(this.upper);
    this.upperHall = addRoom("Upper hall", 0, -200, 900, 0);
    this.roomAboveFamily = addRoom("Above family", 0, 0, 500, 400);
    addRoom("Bedroom", 500, 0, 900, 400);
    addWall(0, -200, 900, -200);
    addWall(900, -200, 900, 400);
    addWall(500, 400, 900, 400);
    addWall(0, 400, 0, 0);
    addWall(0, 0, 0, -200);
    addWall(500, 0, 500, 400);
    this.overlookWall = addWall(0, 0, 500, 0);
    addWall(500, 0, 900, 0);
    this.hatch = new HomeDoorOrWindow(hatchPiece);
    this.hatch.setX(250);
    this.hatch.setY(0);
    this.home.addPieceOfFurniture(this.hatch);
    this.hatch.setLevel(this.upper);
    this.home.setSelectedLevel(this.ground);
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

  private Room addRoom(String name, float x1, float y1, float x2, float y2) {
    Room room = new Room(new float [][] {{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}});
    room.setName(name);
    this.home.addRoom(room);
    return room;
  }

  private Wall addWall(float x1, float y1, float x2, float y2) {
    Wall wall = new Wall(x1, y1, x2, y2, 10, 250);
    this.home.addWall(wall);
    return wall;
  }

  private String run(String command) {
    String summary = this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + command + "]}").getCommands());
    assertNotNull("An answer to " + command, summary);
    return summary;
  }

  /**
   * Returns the lowest and the highest elevation of the vertices of the ceiling of a room in the 3D scene.
   */
  private float [] getCeilingElevations(Room room) {
    Room3D room3D = new Room3D(room, this.home, false, true, true);
    float [] elevations = {Float.MAX_VALUE, -Float.MAX_VALUE, 0};
    // The second child is the ceiling
    readElevations(room3D.getChild(1), elevations);
    return elevations;
  }

  private static void readElevations(Node node, float [] elevations) {
    if (node instanceof Group) {
      for (Enumeration<?> children = ((Group)node).getAllChildren(); children.hasMoreElements(); ) {
        readElevations((Node)children.nextElement(), elevations);
      }
    } else if (node instanceof Shape3D) {
      for (Enumeration<?> geometries = ((Shape3D)node).getAllGeometries(); geometries.hasMoreElements(); ) {
        Geometry geometry = (Geometry)geometries.nextElement();
        if (geometry instanceof GeometryArray) {
          GeometryArray array = (GeometryArray)geometry;
          float [] coordinates = new float [array.getVertexCount() * 3];
          array.getCoordinates(0, coordinates);
          for (int i = 1; i < coordinates.length; i += 3) {
            elevations [0] = Math.min(elevations [0], coordinates [i]);
            elevations [1] = Math.max(elevations [1], coordinates [i]);
          }
          elevations [2] += array.getVertexCount();
        }
      }
    }
  }

  @Test
  public void testCeilingHeight() throws Exception {
    // The ceiling of the level
    float [] elevations = getCeilingElevations(this.family);
    assertEquals(250, elevations [0], 1E-3f);
    assertEquals(250, elevations [1], 1E-3f);
    float flatVertexCount = elevations [2];

    String summary = run("{\"action\":\"modify_room\",\"room\":\"Family\",\"ceiling_height\":243.84}");
    assertTrue(summary, summary.contains("its ceiling is 243.8"));
    assertTrue(summary, summary.contains("under the top of its level"));
    assertEquals(243.84, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    assertTrue(this.family.isCeilingVisible());
    // Drawn at that height, with a top 5 cm above it and borders between them
    elevations = getCeilingElevations(this.family);
    assertEquals(243.84f, elevations [0], 0.01f);
    assertEquals(248.84f, elevations [1], 0.01f);
    assertTrue("More vertices than a ceiling seen from under only", elevations [2] > flatVertexCount);
    // The other rooms keep the ceiling of the level
    elevations = getCeilingElevations(this.kitchen);
    assertEquals(250, elevations [0], 1E-3f);
    assertEquals(250, elevations [1], 1E-3f);
    // The walls didn't move
    assertEquals(250, this.southWall.getHeight(), 1E-3f);

    // Told by the descriptions and counted by the headroom
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + getId(this.family) + "\"}");
    assertTrue(summary, summary.contains("its ceiling is 243.8"));
    summary = run("{\"action\":\"headroom\",\"ids\":[\"" + getId(this.family) + "\"]}");
    assertTrue(summary, summary.contains("243.8"));
    assertFalse(summary, summary.contains("250"));

    // A thickness, and a sheet without thickness
    summary = run("{\"action\":\"modify_room\",\"room\":\"Family\",\"ceiling_thickness\":0}");
    elevations = getCeilingElevations(this.family);
    assertEquals(243.84f, elevations [0], 0.01f);
    assertEquals(243.84f, elevations [1], 0.01f);
    assertTrue("Drawn from both sides", elevations [2] >= 2 * flatVertexCount);

    // Kept by the file of the home
    File file = File.createTempFile("ceiling", ".sh3d");
    // Its model is a file which doesn't exist
    this.home.deletePieceOfFurniture(this.hatch);
    try {
      new HomeFileRecorder().writeHome(this.home, file.getPath());
      Home readHome = new HomeFileRecorder().readHome(file.getPath());
      Room readRoom = null;
      for (Room room : readHome.getRooms()) {
        if ("Family".equals(room.getName())) {
          readRoom = room;
        }
      }
      assertEquals(243.84, Double.parseDouble(readRoom.getProperty(CEILING_HEIGHT)), 0.01);
      Room3D room3D = new Room3D(readRoom, readHome, false, true, true);
      float [] readElevations = {Float.MAX_VALUE, -Float.MAX_VALUE, 0};
      readElevations(room3D.getChild(1), readElevations);
      assertEquals(243.84f, readElevations [0], 0.01f);
    } finally {
      file.delete();
    }

    // The ceiling of a level above
    summary = run("{\"action\":\"modify_room\",\"room\":\"Family\",\"ceiling_level\":\"Level 1\"}");
    assertTrue(summary, summary.contains("its ceiling is 512 cm above its floor"));
    assertTrue(summary, summary.contains("at the top of the level \"Level 1\""));
    assertTrue("The floor above is said to be in the room: " + summary, summary.contains("open_to_above"));
    elevations = getCeilingElevations(this.family);
    assertEquals(512, elevations [0], 0.01f);
    summary = run("{\"action\":\"modify_room\",\"room\":\"Family\",\"ceiling_level\":\"Level 1\",\"ceiling_height\":200}");
    assertEquals(462, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);

    // Values that can't be used change nothing
    summary = run("{\"action\":\"modify_room\",\"room\":\"Family\",\"ceiling_height\":10}");
    assertTrue(summary, summary.contains("its ceiling didn't change"));
    assertEquals(462, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    summary = run("{\"action\":\"modify_room\",\"room\":\"Family\",\"ceiling_level\":\"Attic\"}");
    assertTrue(summary, summary.contains("its ceiling didn't change"));

    // Back to the top of the level, then each step undone
    summary = run("{\"action\":\"modify_room\",\"room\":\"Family\",\"ceiling_height\":\"level\"}");
    assertTrue(summary, summary.contains("back at the top of its level"));
    assertNull(this.family.getProperty(CEILING_HEIGHT));
    elevations = getCeilingElevations(this.family);
    assertEquals(250, elevations [0], 1E-3f);
    assertEquals(250, elevations [1], 1E-3f);
    this.homeController.undo();
    assertEquals(462, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    // The two calls refused, then the one before them
    this.homeController.undo();
    this.homeController.undo();
    this.homeController.undo();
    assertEquals(512, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    this.homeController.undo();
    this.homeController.undo();
    assertEquals(243.84, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    this.homeController.undo();
    assertNull(this.family.getProperty(CEILING_HEIGHT));
    this.homeController.redo();
    assertEquals(243.84, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
  }

  private String getId(Object item) {
    // The ids of the items in the order of their kind
    if (item instanceof Room) {
      return "R" + (this.home.getRooms().indexOf(item) + 1);
    } else if (item instanceof Wall) {
      return "W" + (new ArrayList<Wall>(this.home.getWalls()).indexOf(item) + 1);
    } else {
      return "F" + (this.home.getFurniture().indexOf(item) + 1);
    }
  }

  @Test
  public void testLevelHeightAndWalls() {
    // A level gets lower: its walls stay, its ceilings follow
    String summary = run("{\"action\":\"modify_level\",\"name\":\"Ground\",\"height\":243.84}");
    assertEquals(243.84f, this.ground.getHeight(), 0.01f);
    assertEquals(250, this.southWall.getHeight(), 1E-3f);
    assertTrue(summary, summary.contains("9 walls keep their heights (250 cm)"));
    assertTrue(summary, summary.contains("ceilings of 3 rooms are now at 243.8"));
    assertEquals(243.84f, getCeilingElevations(this.family) [0], 0.01f);
    this.homeController.undo();
    assertEquals(250, this.ground.getHeight(), 1E-3f);

    summary = run("{\"action\":\"modify_level\",\"name\":\"Ground\",\"height\":243.84,\"keep_wall_heights\":true}");
    assertEquals(250, this.southWall.getHeight(), 1E-3f);
    assertTrue(summary, summary.contains("9 walls keep their heights"));
    this.homeController.undo();

    // The ceilings of the last level are at the top of its walls, whatever its height
    summary = run("{\"action\":\"modify_level\",\"name\":\"Level 1\",\"height\":243.84}");
    assertEquals(512, getCeilingElevations(this.upperHall) [0], 0.01f);
    assertTrue(summary, summary.contains("the top of its tallest wall"));
    assertTrue(summary, summary.contains("ceiling_height"));
    this.homeController.undo();

    // The walls as tall as the level follow it when asked, and are listed
    Wall lowWall = new Wall(100, -100, 300, -100, 10, 100);
    this.home.addWall(lowWall);
    lowWall.setLevel(this.upper);
    summary = run("{\"action\":\"modify_level\",\"name\":\"Level 1\",\"height\":300,\"keep_wall_heights\":false}");
    assertEquals(300, this.upper.getHeight(), 1E-3f);
    assertEquals(300, this.overlookWall.getHeight(), 1E-3f);
    assertEquals(100, lowWall.getHeight(), 1E-3f);
    assertEquals("The walls of the other levels stay", 250, this.southWall.getHeight(), 1E-3f);
    assertTrue(summary, summary.contains("8 walls as tall as the level went from 250 to 300 cm"));
    assertTrue(summary, summary.contains("W"));
    assertEquals(562, getCeilingElevations(this.upperHall) [0], 0.01f);
    // One step to undo
    this.homeController.undo();
    assertEquals(250, this.upper.getHeight(), 1E-3f);
    assertEquals(250, this.overlookWall.getHeight(), 1E-3f);

    // A room with its own ceiling height keeps it
    run("{\"action\":\"modify_room\",\"room\":\"Kitchen\",\"ceiling_height\":230}");
    summary = run("{\"action\":\"modify_level\",\"name\":\"Ground\",\"height\":240}");
    assertTrue(summary, summary.contains("1 room keeps the ceiling height set with ceiling_height"));
    assertTrue(summary, summary.contains("ceilings of 2 rooms are now at 240"));
    assertEquals(230, getCeilingElevations(this.kitchen) [0], 0.01f);
  }

  @Test
  public void testOpenToAbove() {
    // A lamp of the upper level over the family room
    HomePieceOfFurniture table = new HomePieceOfFurniture(this.executor.findCatalogPiece("Coffee table", false));
    table.setX(250);
    table.setY(200);
    this.home.addPieceOfFurniture(table);
    table.setLevel(this.upper);
    int wallCount = this.home.getWalls().size();
    int furnitureCount = this.home.getFurniture().size();

    String summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\",\"to_level\":\"Level 1\","
        + "\"overlook\":{\"along\":\"north\",\"railing\":\"Handrail\",\"height\":91}}");
    // One ceiling at the top of the upper level, the room above without floor nor ceiling
    assertEquals(512, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    assertTrue(this.family.isCeilingVisible());
    assertEquals(512, getCeilingElevations(this.family) [0], 0.01f);
    assertEquals("A sheet at the top of the walls", 512, getCeilingElevations(this.family) [1], 0.01f);
    assertTrue(summary, summary.contains("without thickness"));
    assertFalse(this.roomAboveFamily.isFloorVisible());
    assertFalse(this.roomAboveFamily.isCeilingVisible());
    assertTrue(this.upperHall.isFloorVisible());
    assertTrue(summary, summary.contains("Hid the floor and the ceiling of"));
    // The south side had no wall above it: its wall rises. The west one is closed by the wall of the upper level
    assertEquals(512, this.southWall.getHeight(), 1E-3f);
    assertEquals(250, this.westWall.getHeight(), 1E-3f);
    assertTrue(summary, summary.contains("Raised the wall"));
    // The overlook: the wall of the upper level and its hatch are gone, a railing stands there
    assertFalse(this.home.getWalls().contains(this.overlookWall));
    assertFalse(summary, this.home.getFurniture().contains(this.hatch));
    assertEquals(wallCount - 1, this.home.getWalls().size());
    List<HomePieceOfFurniture> railing = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if ("Handrail".equals(piece.getName())) {
        railing.add(piece);
      }
    }
    assertEquals("Repeated, not stretched", 5, railing.size());
    for (HomePieceOfFurniture piece : railing) {
      assertEquals(100, piece.getWidth(), 0.01f);
      assertEquals(91, piece.getHeight(), 0.01f);
      assertEquals(0, piece.getY(), 0.01f);
      assertEquals(this.upper, piece.getLevel());
    }
    assertEquals(50, railing.get(0).getX(), 0.01f);
    assertEquals(450, railing.get(4).getX(), 0.01f);
    assertTrue(summary, summary.contains("Service hatch"));
    assertTrue(summary, summary.contains("Railing: 5 pieces"));
    // What is left in the opened space
    assertTrue(summary, summary.contains("Still inside the opened space"));
    assertTrue(summary, summary.contains("Coffee table"));
    assertFalse("The railing isn't a thing left: " + summary, summary.substring(summary.indexOf("Still inside"), summary.indexOf("Ids of what")).contains("Handrail"));

    // Everything undone in one step
    this.homeController.undo();
    assertNull(this.family.getProperty(CEILING_HEIGHT));
    assertTrue(this.roomAboveFamily.isFloorVisible());
    assertEquals(250, this.southWall.getHeight(), 1E-3f);
    assertEquals(wallCount, this.home.getWalls().size());
    assertEquals(furnitureCount, this.home.getFurniture().size());
    assertTrue(this.home.getWalls().contains(this.overlookWall));
    assertTrue(this.home.getFurniture().contains(this.hatch));
  }

  @Test
  public void testOpenToAboveVariants() {
    // The room above goes over the kitchen too: it is cut, not hidden
    this.roomAboveFamily.setPoints(new float [][] {{0, 0}, {900, 0}, {900, 400}, {0, 400}});
    // No wall under the upper level along the west side of the room
    this.home.deleteWall(this.westWall);
    Wall upperWestWall = null;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == this.upper && wall.getXStart() == 0 && wall.getXEnd() == 0 && wall.getYStart() == 400) {
        upperWestWall = wall;
      }
    }
    this.home.deleteWall(upperWestWall);
    int wallCount = this.home.getWalls().size();

    // The overlook given by its wall, cut down to the height of a railing
    String summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\","
        + "\"overlook\":{\"wall\":\"" + getId(this.overlookWall) + "\",\"upper_wall\":\"low\",\"height\":100,\"color\":\"red\"}}");
    assertTrue("The unknown key of the overlook is reported: " + summary, summary.contains("[color]"));
    assertEquals(512, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    assertTrue(this.roomAboveFamily.isFloorVisible());
    float minX = Float.MAX_VALUE;
    for (float [] point : this.roomAboveFamily.getPoints()) {
      minX = Math.min(minX, point [0]);
    }
    assertEquals("The room above ends where the opened room starts", 500, minX, 0.01f);
    assertTrue(summary, summary.contains("Cut out of the rooms above it"));
    assertTrue(this.home.getWalls().contains(this.overlookWall));
    assertEquals(100, this.overlookWall.getHeight(), 1E-3f);
    assertFalse(this.home.getFurniture().contains(this.hatch));
    assertTrue(summary, summary.contains("cut down to 100 cm"));
    // A wall of the upper level closes the side open under it
    assertEquals(wallCount + 1, this.home.getWalls().size());
    Wall addedWall = new ArrayList<Wall>(this.home.getWalls()).get(wallCount);
    assertEquals(this.upper, addedWall.getLevel());
    assertEquals(0, addedWall.getXStart(), 0.01f);
    assertEquals(0, addedWall.getXEnd(), 0.01f);
    assertEquals(250, addedWall.getHeight(), 0.01f);
    assertTrue(summary, summary.contains("Added the wall"));
    this.homeController.undo();
    assertEquals(wallCount, this.home.getWalls().size());
    assertEquals(250, this.overlookWall.getHeight(), 1E-3f);

    // What can't be done is refused with its reason
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Garage\"}");
    assertTrue(summary, summary.contains("no room"));
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Above family\"}");
    assertTrue(summary, summary.contains("a level above"));
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\",\"overlook\":{\"along\":\"up\"}}");
    assertTrue(summary, summary.contains("no overlook"));
    assertEquals(512, Double.parseDouble(this.family.getProperty(CEILING_HEIGHT)), 0.01);
    // Without overlook, and without railing in the catalog
    this.homeController.undo();
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\",\"overlook\":{\"along\":\"north\",\"railing\":\"Glass guard\"}}");
    assertTrue(summary, summary.contains("the catalog has no piece named \"Glass guard\""));
  }

  /**
   * A wall of the upper level longer than the overlook is cut at the ends of the overlook.
   */
  @Test
  public void testOverlookInLongWall() {
    Wall otherWall = null;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == this.upper && wall.getXStart() == 500 && wall.getXEnd() == 900 && wall.getYStart() == 0) {
        otherWall = wall;
      }
    }
    this.home.deleteWall(otherWall);
    this.overlookWall.setXEnd(900);
    this.overlookWall.setLeftSideColor(0xAA3311);
    this.home.deletePieceOfFurniture(this.hatch);
    int wallCount = this.home.getWalls().size();

    String summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\",\"overlook\":{\"along\":\"north\",\"railing\":\"Banister\"}}");
    assertTrue(summary, summary.contains("beyond it stay as walls of their own"));
    // The part along the overlook is gone, the one along the bedroom stays with its color
    assertFalse(this.home.getWalls().contains(this.overlookWall));
    assertEquals(wallCount, this.home.getWalls().size());
    Wall part = new ArrayList<Wall>(this.home.getWalls()).get(wallCount - 1);
    assertEquals(this.upper, part.getLevel());
    assertEquals(500, part.getXStart(), 0.01f);
    assertEquals(900, part.getXEnd(), 0.01f);
    assertEquals(0, part.getYStart(), 0.01f);
    assertEquals(250, part.getHeight(), 0.01f);
    assertEquals(Integer.valueOf(0xAA3311), part.getLeftSideColor());
    assertTrue("An id of its own: " + summary, summary.contains("Ids of what was created: W"));
    // The railing of the catalog replaces the one asked, which doesn't exist
    assertTrue(summary, summary.contains("Railing: 5 pieces \"Railing\" (the catalog has no piece named \"Banister\")"));

    this.homeController.undo();
    assertEquals(wallCount, this.home.getWalls().size());
    assertTrue(this.home.getWalls().contains(this.overlookWall));
    assertEquals(0, this.overlookWall.getXStart(), 0.01f);
    assertEquals(900, this.overlookWall.getXEnd(), 0.01f);
    assertFalse(this.home.getWalls().contains(part));
  }
}
