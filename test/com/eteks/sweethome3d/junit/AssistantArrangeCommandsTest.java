/*
 * AssistantArrangeCommandsTest.java 21 nov. 2026
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the commands that group, align, distribute, mirror, lock and measure items.
 */
public class AssistantArrangeCommandsTest {
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

  /**
   * Adds a piece to the home, which gets the next id <code>F#</code>.
   */
  private HomePieceOfFurniture addPiece(String name, float x, float y, float width, float depth, float angleInDegrees) throws Exception {
    Content content = new URLContent(new File("dummy.png").toURI().toURL());
    HomePieceOfFurniture piece = new HomePieceOfFurniture(
        new CatalogPieceOfFurniture(name, content, content, width, depth, 80, true, false));
    piece.setX(x);
    piece.setY(y);
    piece.setAngle((float)Math.toRadians(angleInDegrees));
    this.home.addPieceOfFurniture(piece);
    HomeItemIds.ensureIds(this.home);
    return piece;
  }

  private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 20, 250);
    this.home.addWall(wall);
    HomeItemIds.ensureIds(this.home);
    return wall;
  }

  private static float getMinX(Selectable item) {
    float min = Float.MAX_VALUE;
    for (float [] point : item.getPoints()) {
      min = Math.min(min, point [0]);
    }
    return min;
  }

  private static float getMaxX(Selectable item) {
    float max = -Float.MAX_VALUE;
    for (float [] point : item.getPoints()) {
      max = Math.max(max, point [0]);
    }
    return max;
  }

  @Test
  public void testGroupAndUngroup() throws Exception {
    HomePieceOfFurniture table = addPiece("Table", 100, 100, 120, 80, 0);
    HomePieceOfFurniture chair = addPiece("Chair", 100, 180, 40, 40, 0);
    HomePieceOfFurniture lamp = addPiece("Lamp", 500, 500, 30, 30, 0);
    this.home.setSelectedItems(Arrays.asList(new Selectable [] {lamp}));

    String summary = run("{\"action\":\"group\",\"ids\":[\"F1\",\"F2\"],\"name\":\"Dining set\"}");
    assertEquals(summary, 2, this.home.getFurniture().size());
    HomePieceOfFurniture group = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece instanceof HomeFurnitureGroup) {
        group = piece;
      }
    }
    assertTrue(summary, group != null);
    assertEquals("Dining set", group.getName());
    assertEquals(2, ((HomeFurnitureGroup)group).getFurniture().size());
    String groupId = HomeItemIds.getId(group);
    assertEquals("F4", groupId);
    assertTrue(summary, summary.contains("Grouped F1 \"Table\", F2 \"Chair\" into F4 \"Dining set\""));
    // The selection of the user is back
    assertEquals(Arrays.asList(new Selectable [] {lamp}), this.home.getSelectedItems());

    // Undone in one step
    this.homeController.undo();
    assertEquals(3, this.home.getFurniture().size());
    assertTrue(this.home.getFurniture().contains(table));
    assertTrue(this.home.getFurniture().contains(chair));
    assertEquals("F1", HomeItemIds.getId(table));
    this.homeController.redo();
    assertEquals(2, this.home.getFurniture().size());

    // The group moves as one piece
    run("{\"action\":\"move\",\"id\":\"F4\",\"dx\":100}");
    assertEquals(200, table.getX(), 0.01f);
    assertEquals(200, chair.getX(), 0.01f);

    summary = run("{\"action\":\"ungroup\",\"id\":\"F4\"}");
    assertEquals(summary, 3, this.home.getFurniture().size());
    assertTrue(summary, summary.contains("Ungrouped F4 \"Dining set\": the freed pieces are F1 \"Table\", F2 \"Chair\""));
    assertEquals("F1", HomeItemIds.getId(table));
    assertEquals("F2", HomeItemIds.getId(chair));
    assertSame(table, HomeItemIds.find(this.home, "F1"));
    this.homeController.undo();
    assertEquals(2, this.home.getFurniture().size());
    assertTrue(this.home.getFurniture().contains(group));

    // Refusals say what to give
    summary = run("{\"action\":\"group\",\"ids\":[\"F3\"]}");
    assertTrue(summary, summary.contains("Ignored group: a group is made of 2 pieces at least"));
    summary = run("{\"action\":\"ungroup\",\"id\":\"F3\"}");
    assertTrue(summary, summary.contains("Ignored ungroup: F3 \"Lamp\" is not a group"));
    summary = run("{\"action\":\"group\"}");
    assertTrue(summary, summary.contains("Ignored group: it designates no furniture"));
  }

  @Test
  public void testAlign() throws Exception {
    HomePieceOfFurniture sofa = addPiece("Sofa", 100, 100, 200, 90, 0);
    HomePieceOfFurniture table = addPiece("Table", 300, 300, 100, 60, 0);
    // Turned by 90 degrees: 90 cm wide in the plan
    HomePieceOfFurniture bench = addPiece("Bench", 500, 500, 200, 90, 90);

    // The left edge of the set is the one of the sofa
    String summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F2\",\"F3\"],\"edge\":\"left\"}");
    assertEquals(summary, 0, getMinX(sofa), 0.01f);
    assertEquals(summary, 0, getMinX(table), 0.01f);
    assertEquals(summary, 0, getMinX(bench), 0.01f);
    assertEquals(50, table.getX(), 0.01f);
    assertEquals(45, bench.getX(), 0.01f);
    assertEquals(300, table.getY(), 0.01f);
    assertTrue(summary, summary.contains("Aligned 3 pieces on x = 0, the left edge of the set: F2 now at (50, 300), F3 now at (45, 500)"));
    this.homeController.undo();
    assertEquals(300, table.getX(), 0.01f);
    assertEquals(500, bench.getX(), 0.01f);

    // On the right edge of a reference piece, which stays in place
    summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F3\"],\"edge\":\"right\",\"to\":\"F2\"}");
    assertEquals(summary, 350, getMaxX(sofa), 0.01f);
    assertEquals(summary, 350, getMaxX(bench), 0.01f);
    assertEquals(300, table.getX(), 0.01f);
    this.homeController.undo();
    assertEquals(100, sofa.getX(), 0.01f);

    summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F2\"],\"edge\":\"center_y\"}");
    // The center of the box that goes from y = 55 to y = 330
    assertEquals(summary, 192.5f, sofa.getY(), 0.01f);
    assertEquals(summary, 192.5f, table.getY(), 0.01f);
    this.homeController.undo();
    assertEquals(100, sofa.getY(), 0.01f);

    // The front of the sofa is at y = 145
    summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F2\"],\"edge\":\"front\"}");
    assertEquals(summary, 115, table.getY(), 0.01f);
    assertEquals(summary, 300, table.getX(), 0.01f);
    assertEquals(summary, 100, sofa.getY(), 0.01f);
    assertTrue(summary, summary.contains("on the front of F1 \"Sofa\""));
    this.homeController.undo();

    // Against a wall whose faces are at y = -10 and y = 10
    Wall wall = addWall(0, 0, 600, 0);
    summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F3\"],\"wall\":\"W1\"}");
    assertEquals(summary, 55, sofa.getY(), 0.01f);
    assertEquals(summary, 110, bench.getY(), 0.01f);
    assertEquals(summary, 500, bench.getX(), 0.01f);
    assertTrue(summary, summary.contains("against the wall W1"));
    this.homeController.undo();
    assertEquals(100, sofa.getY(), 0.01f);
    assertEquals(0, wall.getYStart(), 0);

    // On the edge of the bounding box of a wall
    summary = run("{\"action\":\"align\",\"id\":\"F2\",\"edge\":\"top\",\"to\":\"W1\"}");
    assertEquals(summary, 20, table.getY(), 0.01f);
    this.homeController.undo();

    summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F2\"]}");
    assertTrue(summary, summary.contains("Ignored align: give \"edge\": left, right, top, bottom"));
    summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F2\"],\"edge\":\"diagonal\"}");
    assertTrue(summary, summary.contains("Ignored align: \"edge\" is left"));
    summary = run("{\"action\":\"align\",\"ids\":[\"F1\"],\"edge\":\"left\"}");
    assertTrue(summary, summary.contains("Ignored align: there is nothing to align"));
    summary = run("{\"action\":\"align\",\"ids\":[\"F1\"],\"edge\":\"left\",\"to\":\"F77\"}");
    assertTrue(summary, summary.contains("no item has the id \"F77\""));
    assertEquals(100, sofa.getX(), 0.01f);
  }

  @Test
  public void testDistribute() throws Exception {
    HomePieceOfFurniture first = addPiece("A", 50, 0, 100, 50, 0);
    HomePieceOfFurniture last = addPiece("C", 450, 40, 100, 50, 0);
    HomePieceOfFurniture middle = addPiece("B", 130, 20, 100, 50, 0);

    // 200 cm are free between x = 0 and x = 500
    String summary = run("{\"action\":\"distribute\",\"ids\":[\"F1\",\"F2\",\"F3\"],\"axis\":\"x\"}");
    assertEquals(summary, 50, first.getX(), 0.01f);
    assertEquals(summary, 250, middle.getX(), 0.01f);
    assertEquals(summary, 450, last.getX(), 0.01f);
    assertEquals(summary, 20, middle.getY(), 0.01f);
    assertTrue(summary, summary.contains("with a gap of 100 cm, in the order F1 \"A\", F3 \"B\", F2 \"C\": F3 now at (250, 20)"));
    this.homeController.undo();
    assertEquals(130, middle.getX(), 0.01f);

    summary = run("{\"action\":\"distribute\",\"ids\":[\"F1\",\"F2\",\"F3\"],\"axis\":\"x\",\"spacing\":20}");
    assertEquals(summary, 50, first.getX(), 0.01f);
    assertEquals(summary, 170, middle.getX(), 0.01f);
    assertEquals(summary, 290, last.getX(), 0.01f);
    this.homeController.undo();
    assertEquals(450, last.getX(), 0.01f);

    summary = run("{\"action\":\"distribute\",\"ids\":[\"F1\",\"F2\",\"F3\"],\"axis\":\"y\",\"spacing\":0}");
    assertEquals(summary, 0, first.getY(), 0.01f);
    assertEquals(summary, 50, middle.getY(), 0.01f);
    assertEquals(summary, 100, last.getY(), 0.01f);
    this.homeController.undo();

    summary = run("{\"action\":\"distribute\",\"ids\":[\"F1\",\"F2\",\"F3\"]}");
    assertTrue(summary, summary.contains("Ignored distribute: give \"axis\":\"x\""));
    summary = run("{\"action\":\"distribute\",\"ids\":[\"F1\",\"F2\"],\"axis\":\"x\"}");
    assertTrue(summary, summary.contains("Ignored distribute: give 3 pieces at least"));
  }

  @Test
  public void testMirror() throws Exception {
    HomePieceOfFurniture piece = addPiece("Desk", 100, 50, 140, 70, 30);
    boolean mirrored = piece.isModelMirrored();

    String summary = run("{\"action\":\"mirror\",\"id\":\"F1\",\"axis\":\"x\",\"about\":200}");
    assertEquals(summary, 300, piece.getX(), 0.01f);
    assertEquals(summary, 50, piece.getY(), 0.01f);
    assertEquals(summary, 330, Math.toDegrees(piece.getAngle()), 0.01);
    assertEquals(summary, !mirrored, piece.isModelMirrored());
    assertTrue(summary, summary.contains("Mirrored F1 \"Desk\" across the vertical line x = 200"));
    assertTrue(summary, summary.contains("now at (300, 50) with the angle 330"));
    this.homeController.undo();
    assertEquals(100, piece.getX(), 0.01f);
    assertEquals(30, Math.toDegrees(piece.getAngle()), 0.01);
    assertEquals(mirrored, piece.isModelMirrored());
    this.homeController.redo();
    assertEquals(300, piece.getX(), 0.01f);
    this.homeController.undo();

    // Without "about", a piece flips in place
    summary = run("{\"action\":\"mirror\",\"id\":\"F1\",\"axis\":\"y\"}");
    assertEquals(summary, 100, piece.getX(), 0.01f);
    assertEquals(summary, 50, piece.getY(), 0.01f);
    assertEquals(summary, 150, Math.toDegrees(piece.getAngle()), 0.01);
    this.homeController.undo();
    assertEquals(30, Math.toDegrees(piece.getAngle()), 0.01);

    // A round wall, a room and a line
    Wall wall = addWall(0, 0, 100, 40);
    wall.setArcExtent(1f);
    wall.setLeftSideColor(0xFF0000);
    wall.setRightSideColor(0x0000FF);
    Room room = new Room(new float [][] {{0, 0}, {300, 0}, {300, 200}});
    this.home.addRoom(room);
    Polyline line = new Polyline(new float [][] {{10, 10}, {60, 80}});
    this.home.addPolyline(line);
    HomeItemIds.ensureIds(this.home);

    summary = run("{\"action\":\"mirror\",\"ids\":[\"W1\",\"R1\",\"P1\"],\"axis\":\"x\",\"about\":0}");
    assertEquals(summary, 0, wall.getXStart(), 0.01f);
    assertEquals(summary, -100, wall.getXEnd(), 0.01f);
    assertEquals(summary, 40, wall.getYEnd(), 0.01f);
    assertEquals(summary, -1f, wall.getArcExtent(), 0.0001f);
    assertEquals(summary, Integer.valueOf(0x0000FF), wall.getLeftSideColor());
    assertEquals(summary, Integer.valueOf(0xFF0000), wall.getRightSideColor());
    assertEquals(summary, -300, room.getPoints() [1][0], 0.01f);
    assertEquals(summary, 200, room.getPoints() [2][1], 0.01f);
    assertEquals(summary, -60, line.getPoints() [1][0], 0.01f);
    this.homeController.undo();
    assertEquals(100, wall.getXEnd(), 0.01f);
    assertEquals(1f, wall.getArcExtent(), 0.0001f);
    assertEquals(Integer.valueOf(0xFF0000), wall.getLeftSideColor());
    assertEquals(300, room.getPoints() [1][0], 0.01f);
    assertEquals(60, line.getPoints() [1][0], 0.01f);

    summary = run("{\"action\":\"mirror\",\"ids\":[\"R1\"],\"axis\":\"y\",\"about\":100}");
    assertEquals(summary, 200, room.getPoints() [0][1], 0.01f);
    assertEquals(summary, 0, room.getPoints() [2][1], 0.01f);
    this.homeController.undo();
    assertEquals(0, room.getPoints() [0][1], 0.01f);

    summary = run("{\"action\":\"mirror\",\"id\":\"F1\"}");
    assertTrue(summary, summary.contains("Ignored mirror: give \"axis\":\"x\" to swap left and right"));
    summary = run("{\"action\":\"mirror\",\"axis\":\"x\"}");
    assertTrue(summary, summary.contains("Ignored mirror: it designates no item"));
    assertEquals(100, piece.getX(), 0.01f);
  }

  @Test
  public void testLock() throws Exception {
    HomePieceOfFurniture sofa = addPiece("Sofa", 100, 100, 200, 90, 0);
    HomePieceOfFurniture table = addPiece("Table", 300, 300, 100, 60, 0);
    addWall(0, 0, 600, 0);

    String summary = run("{\"action\":\"lock\",\"ids\":[\"F2\",\"W1\"]}");
    assertFalse(summary, table.isMovable());
    assertTrue(summary, sofa.isMovable());
    assertTrue(summary, summary.contains("Locked F2 \"Table\""));
    assertTrue(summary, summary.contains("lock changes furniture only"));
    this.homeController.undo();
    assertTrue(table.isMovable());
    this.homeController.redo();
    assertFalse(table.isMovable());

    // A locked piece stays where it is
    summary = run("{\"action\":\"align\",\"ids\":[\"F1\",\"F2\"],\"edge\":\"left\",\"to\":\"W1\"}");
    assertEquals(summary, 300, table.getX(), 0.01f);
    assertEquals(summary, 100, sofa.getX(), 0.01f);
    assertTrue(summary, summary.contains("align left F2 \"Table\" in place: it is locked"));
    summary = run("{\"action\":\"mirror\",\"ids\":[\"F2\"],\"axis\":\"x\",\"about\":0}");
    assertEquals(summary, 300, table.getX(), 0.01f);
    assertTrue(summary, summary.contains("mirror left F2 \"Table\" in place"));

    summary = run("{\"action\":\"lock\",\"id\":\"F2\"}");
    assertTrue(summary, summary.contains("Nothing changed; F2 \"Table\" was already locked"));
    summary = run("{\"action\":\"unlock\",\"id\":\"F2\"}");
    assertTrue(summary, table.isMovable());
    assertTrue(summary, summary.contains("Unlocked F2 \"Table\""));
    this.homeController.undo();
    assertFalse(table.isMovable());
    summary = run("{\"action\":\"lock\",\"id\":\"W1\"}");
    assertTrue(summary, summary.contains("Ignored lock: it changes furniture only"));

    assertFalse(this.home.isBasePlanLocked());
    summary = run("{\"action\":\"lock_base_plan\"}");
    assertTrue(summary, this.home.isBasePlanLocked());
    assertTrue(summary, summary.contains("Locked the base plan"));
    summary = run("{\"action\":\"lock_base_plan\"}");
    assertTrue(summary, summary.contains("The base plan was already locked"));
    this.homeController.undo();
    assertFalse(this.home.isBasePlanLocked());
    run("{\"action\":\"lock_base_plan\"}");
    summary = run("{\"action\":\"unlock_base_plan\"}");
    assertFalse(summary, this.home.isBasePlanLocked());
    this.homeController.undo();
    assertTrue(this.home.isBasePlanLocked());
  }

  @Test
  public void testMeasure() throws Exception {
    // A wall along the top, whose faces are at y = -10 and y = 10, and one along the left
    addWall(0, 0, 500, 0);
    addWall(0, 0, 0, 500);
    HomePieceOfFurniture bed = addPiece("Bed", 250, 60, 200, 100, 0);
    HomePieceOfFurniture table = addPiece("Table", 250, 300, 100, 100, 0);
    HomePieceOfFurniture rug = addPiece("Rug", 450, 60, 100, 100, 0);
    rug.setHeight(1);
    Room room = new Room(new float [][] {{0, 0}, {500, 0}, {500, 500}, {0, 500}});
    room.setName("Bedroom");
    this.home.addRoom(room);
    HomeItemIds.ensureIds(this.home);

    String summary = run("{\"action\":\"measure\",\"from\":\"F1\",\"to\":\"W2\"}");
    assertTrue(summary, summary.contains("From F1 \"Bed\" to W2: 140 cm between their outlines at the nearest"));
    assertTrue(summary, summary.contains("dx -250, dy 190"));
    assertFalse(summary, summary.contains("Applied changes"));
    summary = run("{\"action\":\"measure\",\"from\":\"F1\",\"to\":\"F2\"}");
    assertTrue(summary, summary.contains("140 cm between their outlines at the nearest"));
    assertTrue(summary, summary.contains("240 cm from center (250, 60) to center (250, 300), dx 0, dy 240"));
    // The bed touches the wall and stands in the room
    summary = run("{\"action\":\"measure\",\"from\":\"F1\",\"to\":\"W1\"}");
    assertTrue(summary, summary.contains("0 cm between their outlines, which overlap or touch"));
    summary = run("{\"action\":\"measure\",\"from\":\"R1\",\"to\":\"F1\"}");
    assertTrue(summary, summary.contains("0 cm between their outlines, which overlap or touch"));
    summary = run("{\"action\":\"measure\",\"from_point\":[0,0],\"to_point\":[300,400]}");
    assertTrue(summary, summary.contains("From the point (0, 0) to the point (300, 400): 500 cm, dx 300, dy 400"));
    summary = run("{\"action\":\"measure\",\"from_point\":[250,400],\"to\":\"F2\"}");
    assertTrue(summary, summary.contains("50 cm between their outlines at the nearest, from (250, 400) to (250, 350)"));

    summary = run("{\"action\":\"measure\",\"clearance\":\"F1\"}");
    assertTrue(summary, summary.contains("front 140 cm to F2 \"Table\""));
    assertTrue(summary, summary.contains("back 0 cm to the wall W1 (touching or overlapping)"));
    assertTrue(summary, summary.contains("left 140 cm to the wall W2"));
    // The rug at its right doesn't count
    assertTrue(summary, summary.contains("right more than 1000 cm (nothing)"));
    // Turned by 90 degrees, the front of the table looks at the left
    table.setAngle((float)Math.toRadians(90));
    summary = run("{\"action\":\"measure\",\"id\":\"F2\",\"clearance\":true}");
    assertTrue(summary, summary.contains("front 190 cm to the wall W2"));
    assertTrue(summary, summary.contains("left 140 cm to F1 \"Bed\""));
    assertTrue(summary, summary.contains("back more than 1000 cm"));

    summary = run("{\"action\":\"measure\",\"at\":[250,60]}");
    assertTrue(summary, summary.contains("The point (250, 60), floor at 0 cm, is in the room R1 \"Bedroom\"; "
        + "on F1 \"Bed\" (from 0 to 80 cm above the floor)"));
    assertFalse(summary, summary.contains("W1"));
    summary = run("{\"action\":\"measure\",\"at\":[250,12]}");
    assertTrue(summary, summary.contains("beside the wall W1"));
    summary = run("{\"action\":\"measure\",\"at\":[250,12],\"tolerance\":1}");
    assertFalse(summary, summary.contains("W1"));
    summary = run("{\"action\":\"measure\",\"at\":[2000,2000]}");
    assertTrue(summary, summary.contains("is in no room and has no wall, furniture or line within 5 cm"));

    summary = run("{\"action\":\"measure\",\"ids\":[\"F1\",\"F2\"]}");
    assertTrue(summary, summary.contains("Bounding box of F1 \"Bed\", F2 \"Table\": x from 150 to 350, y from 10 to 350, "
        + "200 x 340 cm, center (250, 180)"));

    summary = run("{\"action\":\"measure\"}");
    assertTrue(summary, summary.contains("Ignored measure: give \"from\" and \"to\""));
    summary = run("{\"action\":\"measure\",\"from\":\"F1\"}");
    assertTrue(summary, summary.contains("Ignored measure: give \"to\" (the id of an item) or \"to_point\":[x,y]"));
    summary = run("{\"action\":\"measure\",\"from\":\"F1\",\"to\":\"F99\"}");
    assertTrue(summary, summary.contains("no item has the id \"F99\" given by \"to\""));
    summary = run("{\"action\":\"measure\",\"clearance\":\"W1\"}");
    assertTrue(summary, summary.contains("\"W1\" is not the id of a piece of furniture"));
    summary = run("{\"action\":\"measure\",\"at\":\"here\"}");
    assertTrue(summary, summary.contains("\"at\" is a point [x,y] in cm"));

    // Nothing moved
    assertEquals(250, bed.getX(), 0);
    assertEquals(60, bed.getY(), 0);
  }

  @Test
  public void testReference() {
    String reference = HomeAssistantContext.getCommandReference();
    for (String action : new String [] {"group", "ungroup", "align", "distribute", "mirror", "lock", "unlock",
                                        "lock_base_plan", "unlock_base_plan", "measure"}) {
      assertTrue(action, reference.contains("{\"action\":\"" + action + "\""));
    }
  }
}
