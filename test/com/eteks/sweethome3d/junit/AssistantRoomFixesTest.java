/*
 * AssistantRoomFixesTest.java 26 nov. 2026
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

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;
import com.eteks.sweethome3d.viewcontroller.HomeViewpoints;

/**
 * Tests the defects of the commands of rooms found on a real house: a room opened to the level above
 * in a home with a level made for its roof and walls two stories tall, the values of
 * <code>modify_room</code> that were ignored in silence, headers that doubled a wall and the places
 * from which an irregular room is inspected.
 * @author Sweet Home 3D contributors
 */
public class AssistantRoomFixesTest {
  private static final String CEILING_HEIGHT = "com.eteks.sweethome3d.ceilingHeight";

  private String                   previousNo3D;
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.home = new Home(250);
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

  private Room addRoom(String name, Level level, float... coordinates) {
    float [][] points = new float [coordinates.length / 2][];
    for (int i = 0; i < points.length; i++) {
      points [i] = new float [] {coordinates [2 * i], coordinates [2 * i + 1]};
    }
    Room room = new Room(points);
    room.setName(name);
    // An item added to a home gets its selected level
    this.home.addRoom(room);
    room.setLevel(level);
    return room;
  }

  private Wall addWall(Level level, float xStart, float yStart, float xEnd, float yEnd, float height) {
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10, height);
    this.home.addWall(wall);
    wall.setLevel(level);
    return wall;
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
   * A family room of 5 x 4 m on the ground level, with a level made for the roof between the
   * ground level and the upper level, walls two stories tall along its east side, which goes
   * round a chimney breast, and rooms upstairs whose ceiling is lower than the top of their level.
   */
  private Room createTwoStoryHouse(Level [] levels) {
    Level ground = new Level("Level 0", 0, 12, 250);
    Level roof = new Level("Roof", 244, 1, 30);
    Level upper = new Level("Level 1", 262, 12, 274);
    this.home.addLevel(ground);
    this.home.addLevel(roof);
    this.home.addLevel(upper);
    levels [0] = ground;
    levels [1] = roof;
    levels [2] = upper;
    Room family = addRoom("Family", ground, 0, 0, 500, 0, 500, 400, 0, 400);
    addRoom("Hall", ground, 0, -200, 500, -200, 500, 0, 0, 0);
    // One story tall at the north, the south and the west
    addWall(ground, 0, 0, 500, 0, 250);
    addWall(ground, 500, 400, 0, 400, 250);
    addWall(ground, 0, 400, 0, 0, 250);
    // Two stories tall at the east, around a chimney breast 40 cm deep
    addWall(ground, 505, 0, 505, 150, 548);
    addWall(ground, 505, 150, 460, 150, 548);
    addWall(ground, 460, 150, 460, 270, 548);
    addWall(ground, 460, 270, 505, 270, 548);
    addWall(ground, 505, 270, 505, 400, 548);
    // Upstairs: a corridor at the north whose wall looks over the family room, and walls over the south and the west
    Room corridor = addRoom("Corridor", upper, 0, -200, 500, -200, 500, -5, 0, -5);
    corridor.setProperty(CEILING_HEIGHT, "243.84");
    Room bedroom = addRoom("Bedroom", upper, 520, 0, 900, 0, 900, 400, 520, 400);
    bedroom.setProperty(CEILING_HEIGHT, "243.84");
    addWall(upper, 0, 0, 500, 0, 274);
    addWall(upper, 500, 400, 0, 400, 274);
    addWall(upper, 0, 400, 0, 0, 274);
    this.home.setSelectedLevel(ground);
    return family;
  }

  /**
   * A room opened to the level asked for, not to the level of the roof between them, without
   * walls added beside the ones that already close it, under the ceiling of the rooms upstairs.
   */
  @Test
  public void testOpenToAboveWithRoofLevelAndTallWalls() {
    Level [] levels = new Level [3];
    Room family = createTwoStoryHouse(levels);
    int wallCount = this.home.getWalls().size();

    String summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\",\"to_level\":\"Level 1\","
        + "\"overlook\":{\"along\":\"north\",\"railing\":\"turned_balusters\",\"height\":91}}");
    assertTrue(summary, summary.contains("Opened " + HomeItemIds.getId(family) + " \"Family\" to the level \"Level 1\""));
    // The ceiling of the rooms upstairs, 243.84 cm above their floor at 262 cm
    assertEquals(505.84, Double.parseDouble(family.getProperty(CEILING_HEIGHT)), 0.01);
    assertTrue(summary, summary.contains("as the ceilings of the rooms of the level \"Level 1\", 243.84 cm above its floor"));
    // Nothing on the level of the roof, and no wall beside the tall ones of the east side
    for (Wall wall : this.home.getWalls()) {
      assertFalse("No wall on the level of the roof: " + summary, wall.getLevel() == levels [1]);
    }
    assertFalse(summary, summary.contains("Added the wall"));
    assertEquals("Only the wall of the overlook is gone: " + summary, wallCount - 1, this.home.getWalls().size());
    assertTrue(summary, summary.contains("Overlook along the side from (0, 0) to (500, 0) on \"Level 1\""));
    HomePieceOfFurniture railing = getPiece("Railing");
    assertNotNull(summary, railing);
    assertEquals("Level 1", railing.getLevel().getName());
    assertEquals(0, railing.getElevation(), 0.01f);

    // Without "to_level", the next level with rooms
    this.homeController.undo();
    assertNull(family.getProperty(CEILING_HEIGHT));
    assertEquals(wallCount, this.home.getWalls().size());
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\"}");
    assertTrue(summary, summary.contains("to the level \"Level 1\""));
    assertEquals(505.84, Double.parseDouble(family.getProperty(CEILING_HEIGHT)), 0.01);
    assertEquals("The north wall of the upper level stays without overlook, and nothing is added: " + summary,
        wallCount, this.home.getWalls().size());
    this.homeController.undo();

    // Rooms upstairs with their ceiling at the top of their level: the top of the level
    for (Room room : this.home.getRooms()) {
      room.setProperty(CEILING_HEIGHT, null);
    }
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\",\"to_level\":\"Level 1\"}");
    assertEquals(536, Double.parseDouble(family.getProperty(CEILING_HEIGHT)), 0.01);
    assertFalse(summary, summary.contains("as the ceilings of the rooms"));
    this.homeController.undo();

    // A side that nothing closes above the ground level still gets its wall, on the upper level only
    Wall southUpper = null;
    for (Wall wall : this.home.getWalls()) {
      if (wall.getLevel() == levels [2] && wall.getYStart() == 400 && wall.getYEnd() == 400) {
        southUpper = wall;
      }
    }
    assertNotNull(southUpper);
    this.home.deleteWall(southUpper);
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Family\",\"to_level\":\"Level 1\"}");
    assertTrue(summary, summary.contains("Raised the wall"));
    for (Wall wall : this.home.getWalls()) {
      assertFalse("No wall on the level of the roof: " + summary, wall.getLevel() == levels [1]);
    }
  }

  /**
   * <code>"ceiling":true</code> and the values that aren't understood are told, not ignored.
   */
  @Test
  public void testModifyRoomCeilingValues() {
    Room room = addRoom("Bath", null, 0, 0, 300, 0, 300, 200, 0, 200);
    room.setCeilingVisible(false);
    String summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"ceiling\":true}");
    assertTrue(summary, summary.contains("ceiling visible"));
    assertTrue(room.isCeilingVisible());
    summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"ceiling\":false}");
    assertTrue(summary, summary.contains("ceiling hidden"));
    assertFalse(room.isCeilingVisible());
    summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"ceiling\":\"visible\"}");
    assertTrue(room.isCeilingVisible());

    // A value that means nothing changes nothing, and says so
    summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"ceiling\":\"vaulted\",\"name\":\"Other\"}");
    assertTrue(summary, summary.contains("Ignored modify_room: \"ceiling\" is \"follow_roof\""));
    assertTrue(summary, summary.contains("true or false"));
    assertEquals("Bath", room.getName());
    assertTrue(room.isCeilingVisible());
    for (String key : new String [] {"ceiling_visible", "floor_visible", "area_visible"}) {
      summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"" + key + "\":\"hidden\"}");
      assertTrue(summary, summary.contains("Ignored modify_room: \"" + key + "\" is true or false, not hidden"));
    }
    assertTrue(room.isCeilingVisible());
    assertTrue(room.isFloorVisible());
    // The kinds of ceiling are still understood: this room is under no roof
    summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"ceiling\":\"flat\"}");
    assertFalse(summary, summary.contains("Ignored"));
    assertTrue(room.isCeilingVisible());
    summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"ceiling_visible\":\"false\"}");
    assertFalse(room.isCeilingVisible());
  }

  /**
   * The color of the walls of a room repaints every side by default, and only the
   * sides without finish with <code>"only":"unpainted"</code>.
   */
  @Test
  public void testWallColorOfRoomOnlyUnpainted() {
    Room room = addRoom("Bath", null, 0, 0, 300, 0, 300, 200, 0, 200);
    Wall north = addWall(null, 0, 0, 300, 0, 250);
    Wall east = addWall(null, 300, 0, 300, 200, 250);
    Wall south = addWall(null, 300, 200, 0, 200, 250);
    Wall west = addWall(null, 0, 200, 0, 0, 250);
    // The side of the north wall that faces the room has tiles of its own color
    north.setRightSideColor(0x336699);
    assertNotNull(room);

    String summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"wall_color\":\"#D5AC6B\",\"only\":\"unpainted\"}");
    assertTrue(summary, summary.contains("Painted 3 wall sides"));
    assertTrue(summary, summary.contains("1 side that had a color or a texture kept it"));
    assertEquals(Integer.valueOf(0x336699), north.getRightSideColor());
    assertEquals(Integer.valueOf(0xD5AC6B), east.getRightSideColor());
    assertEquals(Integer.valueOf(0xD5AC6B), south.getRightSideColor());
    assertEquals(Integer.valueOf(0xD5AC6B), west.getRightSideColor());
    assertEquals("D5AC6B", room.getProperty("com.eteks.sweethome3d.wallColor"));

    // By default every side, and the answer tells the ones that had a finish
    summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"wall_color\":\"#FFFFFF\"}");
    assertTrue(summary, summary.contains("Painted 4 wall sides"));
    assertTrue(summary, summary.contains("had a color or a texture of their own, now replaced (\"only\":\"unpainted\" keeps such sides)"));
    assertEquals(Integer.valueOf(0xFFFFFF), north.getRightSideColor());

    summary = run("{\"action\":\"modify_room\",\"room\":\"Bath\",\"wall_color\":\"#000000\",\"only\":\"some\"}");
    assertTrue(summary, summary.contains("\"only\" is \"all\""));
    assertEquals(Integer.valueOf(0xFFFFFF), north.getRightSideColor());
  }

  /**
   * A header isn't added where the wall already goes on above an opening.
   */
  @Test
  public void testHeaderOverWallThatContinues() {
    Level level = new Level("Level 0", 0, 12, 250);
    this.home.addLevel(level);
    this.home.setSelectedLevel(level);
    Wall wall = addWall(level, 0, 0, 400, 0, 250);
    String wallId = HomeItemIds.getId(wall) != null ? HomeItemIds.getId(wall) : "W1";
    String summary = run("{\"action\":\"add_opening\",\"wall\":\"" + wallId + "\",\"from\":100,\"to\":220,\"bottom\":0,\"top\":200}");
    assertTrue(summary, summary.contains("Opened"));
    int count = this.home.getFurniture().size();

    // The wall goes on from the top of the opening to the ceiling
    summary = run("{\"action\":\"add_header\",\"opening\":\"last\"}");
    assertEquals(summary, count, this.home.getFurniture().size());
    summary = run("{\"action\":\"add_header\",\"opening\":\"" + HomeItemIds.getId(this.home.getFurniture().get(0)) + "\"}");
    assertTrue(summary, summary.contains("already closes the space"));
    assertTrue(summary, summary.contains("nothing was added"));
    assertTrue(summary, summary.contains("\"force\":true"));
    assertEquals(count, this.home.getFurniture().size());
    summary = run("{\"action\":\"add_header\",\"wall\":\"" + wallId + "\",\"from\":100,\"to\":220,\"bottom\":200}");
    assertTrue(summary, summary.contains("already closes the space"));
    assertEquals(count, this.home.getFurniture().size());
    summary = run("{\"action\":\"add_header\",\"points\":[[100,0],[220,0]],\"bottom\":200,\"to_walls\":false,\"thickness\":10}");
    assertTrue(summary, summary.contains("already closes the space"));
    assertEquals(count, this.home.getFurniture().size());

    // A beam thicker than the wall shows, and the one that is asked for is built
    String openingId = HomeItemIds.getId(this.home.getFurniture().get(0));
    summary = run("{\"action\":\"add_header\",\"opening\":\"" + openingId + "\",\"thickness\":20,\"height\":30}");
    assertTrue(summary, summary.contains("Added the header"));
    assertEquals(count + 1, this.home.getFurniture().size());
    summary = run("{\"action\":\"add_header\",\"opening\":\"" + openingId + "\",\"force\":true}");
    assertTrue(summary, summary.contains("Added the header"));
    assertFalse(summary, summary.contains("ignored the key"));
    assertEquals(count + 2, this.home.getFurniture().size());
    HomePieceOfFurniture forced = this.home.getFurniture().get(count + 1);
    assertEquals(200, forced.getElevation(), 0.01f);
    assertEquals(50, forced.getHeight(), 0.01f);

    // A wall lower than the ceiling: only the void above it gets the header
    wall.setHeight(220f);
    summary = run("{\"action\":\"add_header\",\"opening\":\"" + openingId + "\"}");
    assertTrue(summary, summary.contains("already closes the space up to 220 cm above the floor: the header starts there rather than at 200 cm"));
    assertEquals(count + 3, this.home.getFurniture().size());
    HomePieceOfFurniture partial = this.home.getFurniture().get(count + 2);
    assertEquals(220, partial.getElevation(), 0.01f);
    assertEquals(30, partial.getHeight(), 0.01f);
    // It keeps that place when its wall moves
    run("{\"action\":\"modify_wall\",\"id\":\"" + wallId + "\",\"y1\":20,\"y2\":20}");
    assertEquals(220, partial.getElevation(), 0.01f);
    assertEquals(30, partial.getHeight(), 0.01f);
    assertEquals(20, partial.getY(), 0.5f);

    // Between two points where no wall stands, as over a bay
    summary = run("{\"action\":\"add_header\",\"points\":[[0,300],[400,300]],\"bottom\":210,\"to_walls\":false}");
    assertTrue(summary, summary.contains("Added the header"));
    assertEquals(count + 4, this.home.getFurniture().size());
  }

  private static double getClearance(Room room, List<Wall> walls, float x, float y) {
    float [][] points = room.getPoints();
    double clearance = Double.MAX_VALUE;
    for (int i = 0; i < points.length; i++) {
      float [] next = points [(i + 1) % points.length];
      clearance = Math.min(clearance, Line2D.ptSegDist(points [i][0], points [i][1], next [0], next [1], x, y));
    }
    for (Wall wall : walls) {
      clearance = Math.min(clearance, Line2D.ptSegDist(wall.getXStart(), wall.getYStart(), wall.getXEnd(), wall.getYEnd(), x, y)
          - wall.getThickness() / 2);
    }
    return clearance;
  }

  private static double getFreeDistance(Room room, List<Wall> walls, float x, float y, float yaw) {
    // The yaw of a camera is 0 when it looks toward the bottom of the plan, and turns clockwise
    double dx = -Math.sin(yaw);
    double dy = Math.cos(yaw);
    double distance = 0;
    while (distance < 5000) {
      float nextX = (float)(x + dx * (distance + 10));
      float nextY = (float)(y + dy * (distance + 10));
      boolean free = room.containsPoint(nextX, nextY, 0);
      for (Wall wall : walls) {
        free &= !wall.containsPoint(nextX, nextY, 0);
      }
      if (!free) {
        break;
      }
      distance += 10;
    }
    return distance;
  }

  /**
   * An L-shaped hallway and a room with a wall in it are inspected from places inside them,
   * away from their sides and from walls, each side seen from far enough.
   */
  @Test
  public void testInspectionOfIrregularRooms() {
    // A hallway 100 cm wide: 6 m along the north, then 5 m toward the south
    Room hallway = addRoom("Hallway", null, 0, 0, 600, 0, 600, 500, 500, 500, 500, 100, 0, 100);
    List<Wall> walls = new ArrayList<Wall>();
    float [][] points = hallway.getPoints();
    for (int i = 0; i < points.length; i++) {
      float [] next = points [(i + 1) % points.length];
      // Their middle line on the outline of the room, as walls drawn by hand often are
      walls.add(addWall(null, points [i][0], points [i][1], next [0], next [1], 250));
    }
    assertFalse("The center of the outline of an L is out of it, or beside its inner corner",
        hallway.containsPoint(hallway.getXCenter(), hallway.getYCenter(), 0)
            && getClearance(hallway, walls, hallway.getXCenter(), hallway.getYCenter()) > 30);

    HomeViewpoints.RoomInspection inspection = HomeViewpoints.inspectRoom(this.home, hallway);
    assertNotNull(inspection);
    assertTrue(hallway.containsPoint(inspection.getX(), inspection.getY(), 0));
    assertTrue("In the middle of the width of the hallway: " + inspection.getX() + ", " + inspection.getY(),
        getClearance(hallway, walls, inspection.getX(), inspection.getY()) >= 35);
    HomeViewpoints.Pose [] poses = inspection.getPoses();
    assertEquals(6, poses.length);
    for (int i = 0; i < poses.length; i++) {
      String place = "picture " + (i + 1) + " from " + poses [i].getX() + ", " + poses [i].getY() + ": " + inspection.getDescriptions() [i];
      assertTrue(place, hallway.containsPoint(poses [i].getX(), poses [i].getY(), 0));
      for (Wall wall : walls) {
        assertFalse(place, wall.containsPoint(poses [i].getX(), poses [i].getY(), 10));
      }
      assertTrue(place, getClearance(hallway, walls, poses [i].getX(), poses [i].getY()) >= 25);
      // Under the ceiling, at the height of the eyes
      assertEquals(place, 160, poses [i].getZ(), 0.5f);
      if (i < 4) {
        // Each side is seen from 1.5 m at least: along the hallway, not across it
        assertTrue(place, getFreeDistance(hallway, walls, poses [i].getX(), poses [i].getY(), poses [i].getYaw()) >= 150);
        assertTrue(place, poses [i].getPitch() < 0 && poses [i].getPitch() > -Math.toRadians(40));
      }
    }
    // The pictures toward the sides close to the middle tell where they are taken from
    int moved = 0;
    for (String description : inspection.getDescriptions()) {
      if (description.contains("as the middle of the room is too close to that side")) {
        moved++;
      }
    }
    assertTrue(moved + " pictures from another place", moved >= 2);
    // The camera of the corner sees the middle of the room
    assertTrue(getFreeDistance(hallway, walls, poses [5].getX(), poses [5].getY(), poses [5].getYaw()) >= 150);

    // A rectangular room is inspected from its center, every picture from there
    Room square = addRoom("Square", null, 1000, 0, 1500, 0, 1500, 400, 1000, 400);
    inspection = HomeViewpoints.inspectRoom(this.home, square);
    assertEquals(1250, inspection.getX(), 8f);
    assertEquals(200, inspection.getY(), 8f);
    for (int i = 0; i < 5; i++) {
      assertEquals(inspection.getX(), inspection.getPoses() [i].getX(), 0.01f);
      assertEquals(inspection.getY(), inspection.getPoses() [i].getY(), 0.01f);
    }
    // From its bottom left corner, away from its sides
    HomeViewpoints.Pose corner = inspection.getPoses() [5];
    assertTrue(corner.getX() + ", " + corner.getY(), corner.getX() < 1100 && corner.getY() > 300);
    assertTrue(getClearance(square, new ArrayList<Wall>(), corner.getX(), corner.getY()) >= 40);

    // A wall drawn through the middle of a room: nobody stands in it
    Wall partition = addWall(null, 1250, 0, 1250, 400, 250);
    List<Wall> partitions = new ArrayList<Wall>();
    partitions.add(partition);
    inspection = HomeViewpoints.inspectRoom(this.home, square);
    for (HomeViewpoints.Pose pose : inspection.getPoses()) {
      assertFalse(pose.getX() + ", " + pose.getY(), partition.containsPoint(pose.getX(), pose.getY(), 20));
      assertTrue(getClearance(square, partitions, pose.getX(), pose.getY()) >= 25);
    }
  }
}
