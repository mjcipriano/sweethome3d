/*
 * AssistantRoofClipDormerTest.java 26 nov. 2026
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Content;
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

/**
 * Tests the dormers of a roof clipped where they stand, the repair of a roof in a room that would
 * lose a dormer, the gable roofs clipped by a room and the edges of type <code>clip</code> between gables.
 * @author Sweet Home 3D contributors
 */
public class AssistantRoofClipDormerTest {
  private static final String CEILING_HEIGHT = "com.eteks.sweethome3d.ceilingHeight";

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
    String summary = this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
    assertNotNull(commands, summary);
    return summary;
  }

  private HomePieceOfFurniture getRoof() {
    HomePieceOfFurniture roof = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantRoofSpec") != null) {
        roof = piece;
      }
    }
    assertNotNull(roof);
    return roof;
  }

  private List<HomeDoorOrWindow> getDormerWindows() {
    List<HomeDoorOrWindow> windows = new ArrayList<HomeDoorOrWindow>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece instanceof HomeDoorOrWindow) {
        windows.add((HomeDoorOrWindow)piece);
      }
    }
    return windows;
  }

  /**
   * Returns the vertices {x, height, y} used by the faces of the model of a piece, in the coordinates of the plan.
   */
  private static List<float []> getModelVertices(HomePieceOfFurniture piece) throws IOException {
    InputStream in = piece.getModel().openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
      List<float []> vertices = new ArrayList<float []>();
      List<float []> used = new ArrayList<float []>();
      for (String line : out.toString("ISO-8859-1").split("\n")) {
        String [] fields = line.trim().split("\\s+");
        if (fields [0].equals("v")) {
          vertices.add(new float [] {Float.parseFloat(fields [1]), Float.parseFloat(fields [2]), Float.parseFloat(fields [3])});
        } else if (fields [0].equals("f")) {
          for (int i = 1; i < fields.length; i++) {
            String index = fields [i].indexOf('/') >= 0 ? fields [i].substring(0, fields [i].indexOf('/')) : fields [i];
            used.add(vertices.get(Integer.parseInt(index) - 1));
          }
        }
      }
      return used;
    } finally {
      in.close();
    }
  }

  /**
   * Returns the faces of the model of a piece, each one as its vertices {x, height, y} in the coordinates of the plan.
   */
  private static List<float [][]> getModelFaces(HomePieceOfFurniture piece) throws IOException {
    InputStream in = piece.getModel().openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
      List<float []> vertices = new ArrayList<float []>();
      List<float [][]> faces = new ArrayList<float [][]>();
      for (String line : out.toString("ISO-8859-1").split("\n")) {
        String [] fields = line.trim().split("\\s+");
        if (fields [0].equals("v")) {
          vertices.add(new float [] {Float.parseFloat(fields [1]), Float.parseFloat(fields [2]), Float.parseFloat(fields [3])});
        } else if (fields [0].equals("f")) {
          float [][] face = new float [fields.length - 1][];
          for (int i = 1; i < fields.length; i++) {
            String index = fields [i].indexOf('/') >= 0 ? fields [i].substring(0, fields [i].indexOf('/')) : fields [i];
            face [i - 1] = vertices.get(Integer.parseInt(index) - 1);
          }
          faces.add(face);
        }
      }
      return faces;
    } finally {
      in.close();
    }
  }

  /**
   * Returns <code>true</code> if a face of a model stands over a rectangle of the plan, by its inside or by a side.
   */
  private static boolean isOverRectangle(List<float [][]> faces, float x1, float y1, float x2, float y2) {
    java.awt.geom.Rectangle2D rectangle = new java.awt.geom.Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1);
    for (float [][] face : faces) {
      java.awt.geom.Path2D outline = new java.awt.geom.Path2D.Float();
      for (int i = 0; i < face.length; i++) {
        float [] next = face [(i + 1) % face.length];
        if (rectangle.intersectsLine(face [i][0], face [i][2], next [0], next [2])) {
          return true;
        }
        if (i == 0) {
          outline.moveTo(face [i][0], face [i][2]);
        } else {
          outline.lineTo(face [i][0], face [i][2]);
        }
      }
      outline.closePath();
      if (outline.contains(rectangle.getCenterX(), rectangle.getCenterY())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the largest x of the vertices of a model whose y is between two values.
   */
  private static float getMaxX(List<float []> vertices, float minY, float maxY) {
    float maxX = -Float.MAX_VALUE;
    for (float [] vertex : vertices) {
      if (vertex [2] >= minY && vertex [2] <= maxY) {
        maxX = Math.max(maxX, vertex [0]);
      }
    }
    return maxX;
  }

  private static float getMaxHeight(List<float []> vertices) {
    float maxHeight = -Float.MAX_VALUE;
    for (float [] vertex : vertices) {
      maxHeight = Math.max(maxHeight, vertex [1]);
    }
    return maxHeight;
  }

  private Wall addWall(float xStart, float yStart, float xEnd, float yEnd, float thickness, float height) {
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness, height);
    this.home.addWall(wall);
    HomeItemIds.ensureIds(this.home);
    return wall;
  }

  private Room addRoom(String name, float [][] points, float ceilingHeight) {
    Room room = new Room(points);
    room.setName(name);
    room.setProperty(CEILING_HEIGHT, String.valueOf(ceilingHeight));
    this.home.addRoom(room);
    HomeItemIds.ensureIds(this.home);
    return room;
  }

  private static double getTop(String summary) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("top at ([0-9.]+) cm above").matcher(summary);
    assertTrue(summary, matcher.find());
    return Double.parseDouble(matcher.group(1));
  }

  private static final String WING_ROOF = "{\"action\":\"add_roof\",\"points\":[[0,0],[1000,0],[1000,600],[0,600]],"
      + "\"eave_height\":250,\"pitch\":30,\"overhang\":30,\"type\":\"hip\",\"gutters\":true,";

  /**
   * A cross-gable dormer on the edge of a roof that runs along the taller part of the building is still
   * built when rooms or walls clip the roof along that edge: its front stands at the face of the wall.
   */
  @Test
  public void testDormerOnEdgeClippedByRoomsAndWalls() throws Exception {
    // The taller part at the east of the wing: its west side runs along two walls, one thicker than the other
    Wall thinWall = addWall(1000, 0, 1000, 300, 20, 550);
    Wall thickWall = addWall(1000, 300, 1000, 600, 30, 550);
    Room tallRoom = addRoom("Family room", new float [][] {{1010, 0}, {1400, 0}, {1400, 600}, {1010, 600}}, 520);
    // A cross gable against the taller part, and a dormer with a window on the other side
    String summary = run(WING_ROOF
        + "\"dormers\":[{\"edge\":2,\"offset\":300,\"width\":300,\"height\":40,\"setback\":0,\"ridge\":\"match\"},"
        + "{\"side\":\"west\",\"offset\":300,\"width\":150,\"height\":110,\"window\":\"Zz roof window\"}]}");
    assertTrue(summary, summary.contains("2 dormers"));
    assertFalse(summary, summary.contains("NOT BUILT"));
    double top = getTop(summary);
    HomePieceOfFurniture roof = getRoof();
    String roofId = HomeItemIds.getId(roof);
    assertTrue("The roof goes through the walls of the taller part", getMaxX(getModelVertices(roof), 0, 600) > 1020);
    assertEquals(1, getDormerWindows().size());
    float windowX = getDormerWindows().get(0).getX();
    float windowY = getDormerWindows().get(0).getY();
    float windowElevation = getDormerWindows().get(0).getElevation();
    float dormerTop = getMaxHeight(getModelVertices(roof));

    // Clipped by the room: the dormer stays, flush with the face of the closest wall
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"clip_to_rooms\":[\""
        + HomeItemIds.getId(tallRoom) + "\"]}");
    assertTrue(summary, summary.contains("2 dormers"));
    assertFalse(summary, summary.contains("NOT BUILT"));
    assertFalse(summary, summary.contains("ignored"));
    assertTrue(summary, summary.contains("stands 10 cm back from its edge, at the face of the wall that clips the roof"));
    // By their sides or by their inside, the faces of the model stop at these walls
    assertFalse(isOverRectangle(getModelFaces(roof), 990.1f, 1, 1500, 299));
    assertFalse(isOverRectangle(getModelFaces(roof), 985.1f, 301, 1500, 599));
    assertTrue(isOverRectangle(getModelFaces(roof), 980, 301, 984.9f, 599));
    assertTrue(summary, summary.contains("closed by the wall that clips the roof, on the edge 2"));
    assertEquals("The top of the roof didn't move", top, getTop(summary), 0.01);
    List<float []> vertices = getModelVertices(roof);
    // Nothing of the roof nor of its dormer beyond the face of each wall of the side of the room
    assertTrue("x = " + getMaxX(vertices, 1, 299), getMaxX(vertices, 1, 299) <= 990.01f);
    assertTrue("x = " + getMaxX(vertices, 301, 599), getMaxX(vertices, 301, 599) <= 985.01f);
    assertEquals(990, getMaxX(vertices, 1, 299), 0.02f);
    assertEquals(985, getMaxX(vertices, 301, 599), 0.02f);
    // The cross gable still rises to the top of the roof
    assertEquals(dormerTop, getMaxHeight(vertices), 12f);
    assertTrue(getMaxHeight(vertices) > 250 + 100);
    // The other dormer and its window are where they were
    assertEquals(1, getDormerWindows().size());
    assertEquals(windowX, getDormerWindows().get(0).getX(), 0.01f);
    assertEquals(windowY, getDormerWindows().get(0).getY(), 0.01f);
    assertEquals(windowElevation, getDormerWindows().get(0).getElevation(), 0.01f);
    String description = run("{\"action\":\"describe_item\",\"id\":\"" + roofId + "\"}");
    assertTrue(description, description.contains("1: gable, 300 cm wide"));
    assertFalse(description, description.contains("NOT BUILT"));

    // Clipped by the walls instead
    this.homeController.undo();
    assertTrue(getMaxX(getModelVertices(roof), 0, 600) > 1020);
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"clip_to_walls\":[\""
        + HomeItemIds.getId(thinWall) + "\",\"" + HomeItemIds.getId(thickWall) + "\"]}");
    assertTrue(summary, summary.contains("2 dormers"));
    assertFalse(summary, summary.contains("NOT BUILT"));
    assertEquals(top, getTop(summary), 0.01);
    vertices = getModelVertices(roof);
    assertTrue("x = " + getMaxX(vertices, 1, 299), getMaxX(vertices, 1, 299) <= 990.01f);
    assertTrue("x = " + getMaxX(vertices, 301, 599), getMaxX(vertices, 301, 599) <= 985.01f);
    assertEquals(1, getDormerWindows().size());
    assertEquals(windowX, getDormerWindows().get(0).getX(), 0.01f);

    // A wall along a part of the front only: the dormer stays at its edge and is cut behind that wall
    this.homeController.undo();
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"overhang\":0,\"clip_to_walls\":[\""
        + HomeItemIds.getId(thickWall) + "\"]}");
    assertTrue(summary, summary.contains("2 dormers"));
    assertFalse(summary, summary.contains("NOT BUILT"));
    vertices = getModelVertices(roof);
    assertTrue("x = " + getMaxX(vertices, 301, 599), getMaxX(vertices, 301, 599) <= 985.01f);
    assertEquals(1000, getMaxX(vertices, 151, 299), 0.1f);
  }

  /**
   * A dormer stands on an edge without overhang with no setback, and on an edge of type clip.
   */
  @Test
  public void testDormerOnEdgeWithoutOverhangAndOnClipEdge() throws Exception {
    String summary = run(WING_ROOF
        + "\"dormers\":[{\"edge\":2,\"offset\":300,\"width\":300,\"height\":40,\"setback\":0,\"window\":\"Zz roof window\"}]}");
    assertTrue(summary, summary.contains("1 dormer"));
    HomePieceOfFurniture roof = getRoof();
    String roofId = HomeItemIds.getId(roof);
    double top = getTop(summary);
    // No overhang at its edge: the dormer was left out in 7.34.0, its place being on the line where the roof ends
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":2,\"overhang\":0}}");
    assertTrue(summary, summary.contains("1 dormer"));
    assertFalse(summary, summary.contains("NOT BUILT"));
    assertTrue(summary, summary.contains("front wall 40 cm high, on the edge 2"));
    assertEquals(1, getDormerWindows().size());
    assertEquals(1000, getDormerWindows().get(0).getX(), 0.2f);
    assertEquals(300, getDormerWindows().get(0).getY(), 0.2f);
    List<float []> vertices = getModelVertices(roof);
    // The overhang of the dormer itself goes on beyond the edge
    assertTrue("x = " + getMaxX(vertices, 0, 600), getMaxX(vertices, 0, 600) > 1009);

    // Its edge clipped: the dormer stays, without front wall, window nor overhang beyond the plane of the edge
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":2,\"type\":\"clip\"}}");
    assertTrue(summary, summary.contains("1 dormer"));
    assertFalse(summary, summary.contains("NOT BUILT"));
    assertTrue(summary, summary.contains("closed by the wall that clips the roof, on the edge 2"));
    assertTrue(summary, summary.contains("the windows of the dormer 1 weren't placed"));
    assertEquals(top, getTop(summary), 0.01);
    assertTrue(getDormerWindows().isEmpty());
    vertices = getModelVertices(roof);
    assertTrue("x = " + getMaxX(vertices, 0, 600), getMaxX(vertices, 0, 600) <= 1000.06f);
    // It still stands above the slope
    float highest = 0;
    for (float [] vertex : vertices) {
      if (vertex [0] > 990 && vertex [2] > 200 && vertex [2] < 400) {
        highest = Math.max(highest, vertex [1]);
      }
    }
    assertTrue("Height at the plane of the edge: " + highest, highest > 250 + 40);
  }

  /**
   * <code>fix_home</code> doesn't cut a roof along a room when that would leave a dormer out, and tells it.
   */
  @Test
  public void testFixHomeKeepsDormers() throws Exception {
    // A taller part that covers the east third of the wing: nothing would remain of the roof under a dormer there
    Room deepRoom = addRoom("Deep room", new float [][] {{700, -100}, {1500, -100}, {1500, 700}, {700, 700}}, 520);
    String summary = run(WING_ROOF
        + "\"dormers\":[{\"edge\":2,\"offset\":300,\"width\":200,\"height\":60,\"setback\":40}]}");
    assertTrue(summary, summary.contains("1 dormer"));
    HomePieceOfFurniture roof = getRoof();
    String roofId = HomeItemIds.getId(roof);
    summary = run("{\"action\":\"check_home\"}");
    assertTrue(summary, summary.contains("[roof_in_room]"));
    summary = run("{\"action\":\"fix_home\",\"types\":[\"roof_in_room\"]}");
    assertTrue(summary, summary.contains("The roof " + roofId + " wasn't cut along the room " + HomeItemIds.getId(deepRoom)
        + " \"Deep room\": its dormer 1 couldn't be built anymore"));
    assertTrue(summary, summary.contains("Fixed 0 issues"));
    assertFalse(roof.getProperty("assistantRoofSpec"), roof.getProperty("assistantRoofSpec").contains("clip_to_rooms"));
    String description = run("{\"action\":\"describe_item\",\"id\":\"" + roofId + "\"}");
    assertTrue(description, description.contains("1: gable, 200 cm wide"));
    // The defect is still reported
    assertTrue(run("{\"action\":\"check_home\"}").contains("[roof_in_room]"));
    // Asked by its command, the clip tells what happens to the dormer
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"clip_to_rooms\":[\"" + HomeItemIds.getId(deepRoom) + "\"]}");
    assertTrue(summary, summary.contains("1: NOT BUILT"));
    this.homeController.undo();

    // A taller part beside the wing, that the roof enters by its overhang: the cut keeps the dormer, and tells it
    this.home.deleteRoom(deepRoom);
    addWall(1000, 0, 1000, 600, 20, 550);
    Room tallRoom = addRoom("Family room", new float [][] {{1010, 0}, {1400, 0}, {1400, 600}, {1010, 600}}, 520);
    summary = run("{\"action\":\"modify_dormer\",\"roof\":\"" + roofId + "\",\"dormer\":1,\"setback\":0}");
    assertFalse(summary, summary.contains("NOT BUILT"));
    assertTrue(run("{\"action\":\"check_home\"}").contains("[roof_in_room]"));
    summary = run("{\"action\":\"fix_home\",\"types\":[\"roof_in_room\"]}");
    assertTrue(summary, summary.contains("Fixed 1 issue"));
    assertTrue(summary, summary.contains("clipped at the face of [" + HomeItemIds.getId(tallRoom) + "]"));
    assertTrue("The dormers of the roof are told: " + summary, summary.contains("its dormers: 1: gable, 200 cm wide"));
    assertFalse(summary, summary.contains("NOT BUILT"));
    assertFalse(run("{\"action\":\"check_home\"}").contains("[roof_in_room]"));
    assertTrue(getMaxX(getModelVertices(roof), 1, 599) <= 990.01f);
  }

  private static final String PORCH_ROOF = "{\"action\":\"add_roof\",\"points\":[[0,0],[300,0],[300,170],[0,170]],"
      + "\"eave_height\":255,\"pitch\":25,\"overhang\":25,"
      + "\"edges\":[{\"type\":\"hip\"},{\"type\":\"gable\"},{\"type\":\"gable\"},{\"type\":\"gable\"}]}";

  /**
   * A roof with gables clipped by the room beside one of them has nothing in that room: the overhang of
   * the gable is cut with the roof, and the roof keeps its top.
   */
  @Test
  public void testGableRoofClippedByRoom() throws Exception {
    // The room at the east of the porch, its west side a hair inside the roof, with its wall
    addWall(296.5f, 10, 296.5f, 350, 7, 550);
    Room room = addRoom("Play room", new float [][] {{299.64f, 10}, {700, 10}, {700, 350}, {299.64f, 350}}, 520);
    String summary = run(PORCH_ROOF);
    assertTrue(summary, summary.contains("1 eave and 3 gables"));
    double top = getTop(summary);
    HomePieceOfFurniture roof = getRoof();
    String roofId = HomeItemIds.getId(roof);
    List<float []> vertices = getModelVertices(roof);
    assertEquals("The overhang of the gable", 325, getMaxX(vertices, -30, 200), 0.01f);
    assertTrue("The overhang of the gable stands in the room", isOverRectangle(getModelFaces(roof), 300, 11, 700, 349));
    float roofTop = getMaxHeight(vertices);

    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"clip_to_rooms\":[\"" + HomeItemIds.getId(room) + "\"]}");
    assertTrue(summary, summary.contains("clipped at the face of"));
    assertTrue(summary, summary.contains("1 eave and 3 gables"));
    assertEquals(top, getTop(summary), 0.01);
    vertices = getModelVertices(roof);
    // No vertex of any material in the room nor in its wall
    for (float [] vertex : vertices) {
      assertFalse("A vertex of the roof at x " + vertex [0] + ", y " + vertex [2] + " stands in the room",
          vertex [0] > 293.01f && vertex [2] > 10.01f && vertex [2] < 349.99f);
    }
    assertFalse(isOverRectangle(getModelFaces(roof), 293.1f, 10.1f, 700, 349.9f));
    assertTrue("The roof goes up to the wall", isOverRectangle(getModelFaces(roof), 280, 50, 292.9f, 150));
    assertEquals(roofTop, getMaxHeight(vertices), 0.01f);
    // Out of the side of the room, the overhang of the gable remains
    assertEquals(325, getMaxX(vertices, -30, 9.99f), 0.01f);
    assertTrue(isOverRectangle(getModelFaces(roof), 301, -20, 324, 9));
    this.homeController.undo();
    assertTrue(isOverRectangle(getModelFaces(roof), 300, 11, 700, 349));
  }

  /**
   * An edge of type clip between gables keeps the gables, the ridge and the top of the roof.
   */
  @Test
  public void testClipEdgeKeepsShapeBetweenGables() throws Exception {
    String summary = run(PORCH_ROOF);
    double top = getTop(summary);
    HomePieceOfFurniture roof = getRoof();
    String roofId = HomeItemIds.getId(roof);
    float roofTop = getMaxHeight(getModelVertices(roof));

    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":2,\"type\":\"clip\"}}");
    assertTrue("The sides stay gables: " + summary, summary.contains("1 eave and 3 gables"));
    assertEquals("The top of the roof didn't move", top, getTop(summary), 0.01);
    List<float []> vertices = getModelVertices(roof);
    assertEquals(roofTop, getMaxHeight(vertices), 0.01f);
    // Stopped at the plane of the edge, the overhang of the other edges kept
    assertEquals(300, getMaxX(vertices, -30, 200), 0.01f);
    float minX = Float.MAX_VALUE;
    for (float [] vertex : vertices) {
      minX = Math.min(minX, vertex [0]);
    }
    assertEquals(-25, minX, 0.01f);
    String description = run("{\"action\":\"describe_item\",\"id\":\"" + roofId + "\"}");
    assertTrue(description, description.contains("edges: 1 hip 25 deg, 2 clip of a gable, 3 gable, 4 gable"));
    assertTrue(roof.getProperty("assistantRoofSpec"), roof.getProperty("assistantRoofSpec").contains("\"clip_of\":\"gable\""));

    // An other change of the roof keeps it
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":4,\"overhang\":10}}");
    assertTrue(summary, summary.contains("1 eave and 3 gables"));
    assertEquals(top, getTop(summary), 0.01);
    // A gable again, with its overhang
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":2,\"type\":\"gable\"}}");
    assertEquals(325, getMaxX(getModelVertices(roof), -30, 200), 0.01f);
    assertFalse(roof.getProperty("assistantRoofSpec"), roof.getProperty("assistantRoofSpec").contains("clip_of"));

    // The eave clipped keeps the slope that rises from it
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":1,\"type\":\"clip\"}}");
    assertTrue(summary, summary.contains("1 eave and 3 gables"));
    assertEquals(top, getTop(summary), 0.01);
    description = run("{\"action\":\"describe_item\",\"id\":\"" + roofId + "\"}");
    assertTrue(description, description.contains("1 clip of a hip 25 deg"));
    // The shape given with the edge
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":1,\"type\":\"hip\"}},"
        + "{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edge\":{\"index\":3,\"type\":\"clip\",\"clip_of\":\"hip\"}}");
    assertTrue(summary, summary.contains("2 eaves and 2 gables"));

    // A roof of type gable built with a clipped end
    this.home.deletePieceOfFurniture(roof);
    summary = run("{\"action\":\"add_roof\",\"points\":[[0,0],[600,0],[600,300],[0,300]],\"eave_height\":250,\"pitch\":30,"
        + "\"type\":\"gable\",\"edges\":[{\"index\":2,\"type\":\"clip\"}]}");
    assertTrue(summary, summary.contains("2 eaves and 2 gables"));
    double gableTop = getTop(summary);
    this.home.deletePieceOfFurniture(getRoof());
    summary = run("{\"action\":\"add_roof\",\"points\":[[0,0],[600,0],[600,300],[0,300]],\"eave_height\":250,\"pitch\":30,"
        + "\"type\":\"gable\"}");
    assertEquals(gableTop, getTop(summary), 0.01);
  }

  /**
   * In <code>modify_roof</code>, edges that all give their number change these edges only.
   */
  @Test
  public void testNumberedEdgesChangeTheseEdgesOnly() throws Exception {
    String summary = run(PORCH_ROOF);
    double top = getTop(summary);
    String roofId = HomeItemIds.getId(getRoof());
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edges\":[{\"index\":2,\"overhang\":0},{\"index\":4,\"overhang\":0}]}");
    assertTrue("The other edges keep their type: " + summary, summary.contains("1 eave and 3 gables"));
    assertEquals(top, getTop(summary), 0.01);
    assertEquals(300, getMaxX(getModelVertices(getRoof()), -30, 200), 0.01f);
    // A list without numbers gives every edge again
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"" + roofId + "\",\"edges\":[{\"type\":\"hip\"},{\"type\":\"hip\"},"
        + "{\"type\":\"hip\"},{\"type\":\"hip\"}]}");
    assertTrue(summary, summary.contains("4 eaves and 0 gables"));
  }
}
