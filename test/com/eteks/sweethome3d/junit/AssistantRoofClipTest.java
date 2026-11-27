/*
 * AssistantRoofClipTest.java
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the roofs that stop at the wall of a taller part of the building without changing shape,
 * their gutters, downspouts and fascia by edge and by span, their dormers that stay where they are,
 * the walls reported above a roof, and the keys of the objects nested in the commands of roofs.
 */
public class AssistantRoofClipTest {
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
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
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

  private HomeDoorOrWindow getDormerWindow() {
    HomeDoorOrWindow window = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece instanceof HomeDoorOrWindow) {
        window = (HomeDoorOrWindow)piece;
      }
    }
    assertNotNull(window);
    return window;
  }

  /**
   * Returns the bounds {min x, min height, min y, max x, max height, max y} of the faces of the model of
   * a piece for each of its materials, in the coordinates of the plan, with the bounds of the whole
   * model under the key <code>all</code>. The first material of a roof is the one of its slopes,
   * the last one the metal of its gutters when it has some.
   */
  private static Map<String, float []> getModelBounds(HomePieceOfFurniture piece) throws IOException {
    InputStream in = piece.getModel().openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte [] buffer = new byte [8192];
      int count;
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
      List<float []> vertices = new ArrayList<float []>();
      Map<String, float []> bounds = new LinkedHashMap<String, float []>();
      String material = "";
      for (String line : out.toString("ISO-8859-1").split("\n")) {
        String [] fields = line.trim().split("\\s+");
        if (fields [0].equals("v")) {
          vertices.add(new float [] {Float.parseFloat(fields [1]), Float.parseFloat(fields [2]), Float.parseFloat(fields [3])});
        } else if (fields [0].equals("usemtl")) {
          material = fields [1];
        } else if (fields [0].equals("f")) {
          for (int i = 1; i < fields.length; i++) {
            String index = fields [i].indexOf('/') >= 0 ? fields [i].substring(0, fields [i].indexOf('/')) : fields [i];
            float [] vertex = vertices.get(Integer.parseInt(index) - 1);
            for (String key : new String [] {material, "all"}) {
              float [] materialBounds = bounds.get(key);
              if (materialBounds == null) {
                materialBounds = new float [] {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                                               -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                bounds.put(key, materialBounds);
              }
              for (int c = 0; c < 3; c++) {
                materialBounds [c] = Math.min(materialBounds [c], vertex [c]);
                materialBounds [c + 3] = Math.max(materialBounds [c + 3], vertex [c]);
              }
            }
          }
        }
      }
      return bounds;
    } finally {
      in.close();
    }
  }

  private static final String ROOF_COLOR = "color_8C4A3C";
  private static final String FASCIA_COLOR = "color_F4F2EE";
  private static final String GUTTER_COLOR = "color_9A9C9E";

  private Wall addWall(float xStart, float yStart, float xEnd, float yEnd, float height) {
    Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 20, height);
    this.home.addWall(wall);
    HomeItemIds.ensureIds(this.home);
    return wall;
  }

  private static double getTop(String summary) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("top at ([0-9.]+) cm above").matcher(summary);
    assertTrue(summary, matcher.find());
    return Double.parseDouble(matcher.group(1));
  }

  /**
   * The repro of the issue: a one-story L of walls whose east edge runs along a two-story wall, a roof
   * with hips on all its edges, then the edge along the tall wall stopped at that wall.
   */
  @Test
  public void testEdgeClippedAtTallerWallKeepsRoofAndDormer() throws Exception {
    // The one-story wing, an L, and the west wall of the two-story part along x = 1000
    addWall(0, 0, 1000, 0, 250);
    addWall(1000, 500, 600, 500, 250);
    addWall(600, 500, 600, 900, 250);
    addWall(600, 900, 0, 900, 250);
    addWall(0, 900, 0, 0, 250);
    Wall tallWall = addWall(1000, 0, 1000, 500, 550);
    String summary = run("{\"action\":\"add_roof\",\"points\":[[0,0],[1000,0],[1000,500],[600,500],[600,900],[0,900]],"
        + "\"eave_height\":250,\"pitch\":30,\"overhang\":30,\"type\":\"hip\",\"gutters\":true,"
        + "\"dormers\":[{\"side\":\"north\",\"offset\":300,\"width\":150,\"height\":110,\"window\":\"Zz roof window\"}]}");
    assertTrue(summary, summary.contains("6 eaves and 0 gables"));
    HomePieceOfFurniture roof = getRoof();
    double top = getTop(summary);
    Map<String, float []> bounds = getModelBounds(roof);
    // With its overhang and its gutter, the roof goes through the tall wall
    assertEquals(1042, bounds.get("all") [3], 0.01f);
    assertEquals(1030, bounds.get(ROOF_COLOR) [3], 0.01f);
    HomeDoorOrWindow window = getDormerWindow();
    float windowX = window.getX();
    float windowY = window.getY();
    float windowElevation = window.getElevation();
    assertTrue(run("{\"action\":\"describe_item\",\"id\":\"F1\"}"), run("{\"action\":\"describe_item\",\"id\":\"F1\"}")
        .contains("on the edge 1, its center 300 cm from the start of that edge"));

    // The edge 2 runs along the tall wall
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"edge\":{\"index\":2,\"type\":\"clip\"}}");
    assertTrue(summary, summary.contains("Modified the roof F1"));
    assertEquals("The top of the roof didn't move", top, getTop(summary), 0.01);
    assertTrue(summary, summary.contains("6 eaves and 0 gables"));
    bounds = getModelBounds(roof);
    // Nothing of the roof beyond the plane of the wall: slopes, underside, fascia, gutter, downspouts
    for (Map.Entry<String, float []> entry : bounds.entrySet()) {
      assertTrue(entry.getKey() + " goes to x = " + entry.getValue() [3], entry.getValue() [3] <= 1000.01f);
    }
    assertEquals(1000, bounds.get(ROOF_COLOR) [3], 0.01f);
    // The other edges keep their overhang, their fascia and their gutter
    assertEquals(-42, bounds.get("all") [0], 0.01f);
    assertEquals(-42, bounds.get("all") [2], 0.01f);
    assertEquals(942, bounds.get("all") [5], 0.01f);
    assertEquals(-30, bounds.get(FASCIA_COLOR) [0], 0.01f);
    // The dormer and its window are where they were
    assertTrue(summary, summary.contains("on the edge 1, its center 300 cm from the start of that edge"));
    assertEquals(windowX, getDormerWindow().getX(), 0.01f);
    assertEquals(windowY, getDormerWindow().getY(), 0.01f);
    assertEquals(windowElevation, getDormerWindow().getElevation(), 0.01f);
    // The tall wall isn't taken as a wall left above the roof
    assertFalse(summary, summary.contains("above this roof"));
    assertTrue(run("{\"action\":\"describe_item\",\"id\":\"F1\"}"), run("{\"action\":\"describe_item\",\"id\":\"F1\"}").contains("2 clip"));

    // The same shape as a hip without overhang would give: only what hangs at the edge differs
    run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"edge\":{\"index\":2,\"type\":\"hip\",\"overhang\":0}}");
    Map<String, float []> hipBounds = getModelBounds(roof);
    assertEquals(bounds.get(ROOF_COLOR) [4], hipBounds.get(ROOF_COLOR) [4], 0.01f);
    assertEquals(1000, hipBounds.get(ROOF_COLOR) [3], 0.01f);
    assertTrue("The gutter of a hip hangs beyond the wall", hipBounds.get(GUTTER_COLOR) [3] > 1005);

    // Undone in one step each
    this.homeController.undo();
    assertEquals(1000, getModelBounds(roof).get("all") [3], 0.01f);
    this.homeController.undo();
    assertEquals(1042, getModelBounds(roof).get("all") [3], 0.01f);
    assertEquals(tallWall.getHeight(), Float.valueOf(550));
  }

  /**
   * A roof drawn over the taller part of the building is cut at the face of its walls.
   */
  @Test
  public void testRoofClippedToWallsAndRooms() throws Exception {
    // A two-story part of 400 x 500 cm at the east of a one-story wing
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Family room\",\"x\":1000,\"y\":0,\"width\":400,\"depth\":500,"
        + "\"thickness\":20,\"height\":550}");
    Wall westWall = null;
    for (Wall wall : this.home.getWalls()) {
      if (Math.abs(wall.getXStart() - wall.getXEnd()) < 1 && Math.abs(wall.getXStart() - 1000) < 15) {
        westWall = wall;
      }
    }
    assertNotNull(westWall);
    float wallFace = Math.min(westWall.getPoints() [0][0], westWall.getPoints() [1][0]);
    for (float [] point : westWall.getPoints()) {
      wallFace = Math.min(wallFace, point [0]);
    }
    // The roof of the wing was drawn 150 cm into the two-story part
    String roofCommand = "{\"action\":\"add_roof\",\"points\":[[0,0],[1150,0],[1150,500],[0,500]],"
        + "\"eave_height\":250,\"pitch\":30,\"overhang\":30,\"gutters\":true";
    String summary = run(roofCommand + "}");
    HomePieceOfFurniture roof = getRoof();
    double top = getTop(summary);
    assertTrue(getModelBounds(roof).get("all") [3] > 1150);

    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"clip_to_walls\":[\"" + HomeItemIds.getId(westWall) + "\"]}");
    assertTrue(summary, summary.contains("clipped at the face of [" + HomeItemIds.getId(westWall) + "]"));
    Map<String, float []> bounds = getModelBounds(roof);
    for (Map.Entry<String, float []> entry : bounds.entrySet()) {
      assertTrue(entry.getKey() + " goes to x = " + entry.getValue() [3], entry.getValue() [3] <= wallFace + 0.02f);
    }
    assertEquals(wallFace, bounds.get(ROOF_COLOR) [3], 0.02f);
    assertEquals("The slopes keep their planes", top, getTop(summary), 0.01);
    assertEquals(-42, bounds.get("all") [0], 0.01f);
    assertTrue(run("{\"action\":\"describe_item\",\"id\":\"F1\"}"), run("{\"action\":\"describe_item\",\"id\":\"F1\"}").contains("clipped by walls in 1 place"));
    // What was cut isn't under the roof anymore
    assertTrue(summary, !summary.contains("above this roof"));

    // The cut is removed, then found from the rooms with taller walls
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"clip\":false}");
    assertTrue(summary, summary.contains("isn't clipped by walls anymore"));
    assertTrue(getModelBounds(roof).get("all") [3] > 1150);
    assertTrue("The walls of the two-story part go through the roof: " + summary, summary.contains("above this roof"));
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"clip\":\"taller_walls\"}");
    assertTrue(summary, summary.contains("clipped at the face of [R1]"));
    assertEquals(wallFace, getModelBounds(roof).get(ROOF_COLOR) [3], 0.02f);
    assertFalse(summary, summary.contains("above this roof"));

    // And asked when the roof is built, with a room
    this.homeController.undo();
    this.homeController.undo();
    this.homeController.undo();
    this.homeController.undo();
    assertEquals(0, this.home.getFurniture().size());
    summary = run(roofCommand + ",\"clip_to_rooms\":[\"R1\"]}");
    assertTrue(summary, summary.contains("clipped at the face of [R1]"));
    assertEquals(wallFace, getModelBounds(getRoof()).get(ROOF_COLOR) [3], 0.02f);
    // Ids that aren't walls or rooms are refused
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F2\",\"clip_to_walls\":[\"R1\"]}");
    assertTrue(summary, summary.contains("isn't a wall"));
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F2\",\"clip\":\"everything\"}");
    assertTrue(summary, summary.contains("\"clip\" is \"taller_walls\""));
  }

  /**
   * Gutters, downspouts and fascia are chosen for the whole roof, for an edge or for spans of an edge,
   * and changed without changing the shape of the roof.
   */
  @Test
  public void testGuttersByEdgeAndSpan() throws Exception {
    // The edges: 1 north, 2 east, 3 south, 4 west
    String summary = run("{\"action\":\"add_roof\",\"points\":[[0,0],[1000,0],[1000,600],[0,600]],\"eave_height\":250,\"pitch\":30,"
        + "\"overhang\":30,\"edges\":[{\"type\":\"hip\",\"gutter\":true},{\"type\":\"hip\"},{\"type\":\"hip\"},{\"type\":\"hip\"}]}");
    HomePieceOfFurniture roof = getRoof();
    double top = getTop(summary);
    float [] gutter = getModelBounds(roof).get(GUTTER_COLOR);
    // Only along the north edge, with its downspout
    assertEquals(-42, gutter [2], 0.01f);
    assertTrue(gutter [5] < 0);
    assertEquals(0, gutter [1], 0.01f);

    // Along every edge but the east one
    summary = run("{\"action\":\"set_gutters\",\"roof\":\"F1\",\"gutters\":true,\"downspouts\":false,"
        + "\"edges\":[{\"index\":2,\"gutter\":false}]}");
    assertTrue(summary, summary.contains("Changed the gutters of the roof F1, whose shape didn't change: "
        + "gutters along the edges 1, 3, 4, none along the edges 2; no downspout"));
    gutter = getModelBounds(roof).get(GUTTER_COLOR);
    assertEquals(-42, gutter [0], 0.01f);
    assertEquals(642, gutter [5], 0.01f);
    assertTrue("No gutter at the east: " + gutter [3], gutter [3] <= 1030.01f);
    assertTrue("No downspout: " + gutter [1], gutter [1] > 200);
    assertEquals(top, getModelBounds(roof).get(ROOF_COLOR) [4], 0.01f);
    assertEquals("Without the gutter of the east edge", 1072, roof.getWidth(), 0.01f);

    // Along a part of the south edge, which starts at its east end, and downspouts where they are
    summary = run("{\"action\":\"set_gutters\",\"roof\":\"F1\",\"gutters\":false,"
        + "\"edges\":[{\"index\":3,\"gutter_spans\":[[200,500]]},{\"index\":2,\"gutter\":null}],"
        + "\"downspouts\":[{\"edge\":3,\"at\":220},{\"edge\":4,\"at\":300,\"size\":9},{\"edge\":9,\"at\":0}]}");
    assertTrue(summary, summary.contains("gutters along the edges 1, 3 (200 to 500 cm), none along the edges 2, 4"));
    assertTrue(summary, summary.contains("ignored the key [size] of the downspout 2. Its keys: [edge, at]"));
    assertTrue(summary, summary.contains("the downspout 3 was ignored"));
    gutter = getModelBounds(roof).get(GUTTER_COLOR);
    // The edge 1 keeps the gutter it asked for; the one of the edge 3 runs from x = 800 to x = 500
    assertEquals(-42, gutter [2], 0.01f);
    assertEquals(642, gutter [5], 0.01f);
    assertEquals("A downspout stands along the west edge", -36f, gutter [0], 0.5f);
    assertEquals(0, gutter [1], 0.01f);
    // Without the gutter of the north edge, the metal is where the spans and the downspouts put it
    summary = run("{\"action\":\"modify_gutters\",\"roof\":\"F1\",\"edges\":[{\"index\":1,\"gutter\":false}],"
        + "\"downspouts\":[{\"edge\":3,\"at\":220}]}");
    gutter = getModelBounds(roof).get(GUTTER_COLOR);
    assertEquals(500, gutter [0], 0.01f);
    assertEquals(800, gutter [3], 0.01f);
    assertTrue(gutter [2] > 590);

    // A fascia for one edge, and an edge without it
    run("{\"action\":\"set_gutters\",\"roof\":\"F1\",\"fascia\":false,\"edges\":[{\"index\":4,\"fascia\":true}]}");
    float [] fascia = getModelBounds(roof).get(FASCIA_COLOR);
    assertEquals(-30, fascia [0], 0.01f);
    assertEquals(-30, fascia [3], 0.01f);

    // Unknown keys and edges are told
    summary = run("{\"action\":\"set_gutters\",\"roof\":\"F1\",\"edges\":[{\"index\":2,\"gutter\":true,\"pitch\":40}]}");
    assertTrue(summary, summary.contains("ignored the key [pitch] of the edge 2. Its keys: [gutter, gutter_spans, fascia]"));
    assertEquals("Its slopes didn't change", top, getModelBounds(roof).get(ROOF_COLOR) [4], 0.01f);
    summary = run("{\"action\":\"set_gutters\",\"roof\":\"F1\",\"edges\":[{\"index\":7,\"gutter\":true}]}");
    assertTrue(summary, summary.contains("numbered from 1 to 4"));
    summary = run("{\"action\":\"set_gutters\",\"roof\":\"F1\"}");
    assertTrue(summary, summary.contains("give what changes"));
    // An edge changed by modify_roof keeps its gutter
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"edge\":{\"index\":1,\"pitch\":35}}");
    assertTrue(run("{\"action\":\"describe_item\",\"id\":\"F1\"}"), run("{\"action\":\"describe_item\",\"id\":\"F1\"}")
        .contains("gutters along the edges 2, 3 (200 to 500 cm), none along the edges 1, 4"));
  }

  /**
   * The keys of the objects nested in the commands of roofs are checked as the ones of the commands,
   * and the values that are changed are told.
   */
  @Test
  public void testNestedKeysAndClampedValuesAreReported() throws Exception {
    String summary = run("{\"action\":\"add_roof\",\"points\":[[0,0],[1000,0],[1000,600],[0,600]],\"eave_height\":250,\"pitch\":30,"
        + "\"colour\":\"#112233\","
        + "\"edges\":[{\"type\":\"hip\",\"gutters\":true},{\"type\":\"hip\",\"pitch\":95},{\"type\":\"hip\"},{\"type\":\"hip\",\"overhang\":-5}],"
        + "\"dormers\":[{\"side\":\"north\",\"width\":150,\"height\":110,\"depth\":90,"
        + "\"windows\":[{\"name\":\"Zz roof window\",\"sil\":20}]}]}");
    assertTrue(summary, summary.contains("add_roof ignored the key [colour]"));
    assertTrue(summary, summary.contains("add_roof ignored the key [gutters] of the edge 1. "
        + "Its keys: [type, pitch, overhang, gutter, gutter_spans, fascia, clip_of]"));
    assertTrue(summary, summary.contains("ignored the key [depth] of the dormer"));
    assertTrue(summary, summary.contains("ignored the key [sil] of a window of the dormer. Its keys: [name, width, height, sill]"));
    assertTrue(summary, summary.contains("the pitch 95 of the edge 2 isn't between 1 and 80 degrees: 30 was used"));
    assertTrue(summary, summary.contains("the overhang -5 of the edge 4 isn't between 0 and 300 cm: 30 was used"));
    assertFalse("The key that was dropped isn't kept with the roof", getRoof().getProperty("assistantRoofSpec").contains("gutters"));

    // The case of the issue: a key that doesn't exist in an edge of modify_roof
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"edge\":[{\"index\":3,\"type\":\"hip\",\"overhang\":0,\"gutters\":false}]}");
    assertTrue(summary, summary.contains("modify_roof ignored the key [gutters] of the edge 3"));
    assertTrue(summary, summary.contains("Modified the roof F1"));
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"edge\":[{\"index\":3,\"gutter\":false}],\"eaves\":260}");
    assertFalse(summary, summary.contains("of the edge 3"));
    assertTrue(summary, summary.contains("modify_roof ignored the key [eaves]"));

    // A negative setback is refused aloud
    summary = run("{\"action\":\"modify_dormer\",\"roof\":\"F1\",\"index\":1,\"setback\":-18,\"with\":200}");
    assertTrue(summary, summary.contains("the setback -18 of the dormer 1 was changed to 0: a dormer can't stand in front of its edge"));
    assertTrue(summary, summary.contains("modify_dormer ignored the key [with] of the dormer"));
    assertTrue(summary, summary.contains("0 cm back from it"));
    summary = run("{\"action\":\"modify_dormer\",\"roof\":\"F1\",\"index\":1,\"setback\":40,\"width\":2000,\"height\":5}");
    assertTrue(summary, summary.contains("the width 2000 of the dormer 1 was changed to 980"));
    assertTrue(summary, summary.contains("the height 5 of the dormer 1 was changed to 20"));
    summary = run("{\"action\":\"add_dormer\",\"roof\":\"F1\",\"side\":\"south\",\"width\":120,\"hieght\":100}");
    assertTrue(summary, summary.contains("add_dormer ignored the key [hieght] of the dormer"));
  }

  /**
   * A dormer stays on its side, at its place in the plan, when the edges or the corners of its roof change.
   */
  @Test
  public void testDormersStayWhereTheyAre() throws Exception {
    // Two eaves look to the east: the edge 2 of 500 cm and the edge 4 of 400 cm
    String summary = run("{\"action\":\"add_roof\",\"points\":[[0,0],[1000,0],[1000,500],[600,500],[600,900],[0,900]],"
        + "\"eave_height\":250,\"pitch\":30,\"overhang\":30,"
        + "\"dormers\":[{\"side\":\"east\",\"position\":0.3,\"width\":150,\"height\":110,\"window\":\"Zz roof window\"}]}");
    assertTrue(summary, summary.contains("1 dormer"));
    String description = run("{\"action\":\"describe_item\",\"id\":\"F1\"}");
    assertTrue(description, description.contains("on the edge 2, its center 150 cm from the start of that edge"));
    float windowX = getDormerWindow().getX();
    float windowY = getDormerWindow().getY();
    assertEquals(150, windowY, 0.5f);

    // Its edge becomes a gable: the dormer isn't moved to the other eave that looks to the east
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"edge\":{\"index\":2,\"type\":\"gable\"}}");
    assertTrue(summary, summary.contains("the dormer 1 wasn't built: the edge 2 it stands on is now a gable"));
    assertTrue(summary, summary.contains("1: NOT BUILT"));
    assertFalse(summary, summary.contains("on the edge 4"));
    // Back to a hip, it is where it was
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"edge\":{\"index\":2,\"type\":\"hip\"}}");
    assertTrue(summary, summary.contains("on the edge 2, its center 150 cm from the start of that edge"));
    assertEquals(windowX, getDormerWindow().getX(), 0.01f);
    assertEquals(windowY, getDormerWindow().getY(), 0.01f);

    // The corner at the start of its edge moves to the north and a corner is added before it: the dormer
    // stays at the same point of the plan, on the edge that now looks to the east
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\","
        + "\"points\":[[0,0],[500,0],[500,-200],[1000,-200],[1000,500],[600,500],[600,900],[0,900]]}");
    assertTrue(summary, summary.contains("on the edge 4, its center 350 cm from the start of that edge"));
    assertEquals(windowX, getDormerWindow().getX(), 0.01f);
    assertEquals(windowY, getDormerWindow().getY(), 0.01f);

    // A new place is measured on the edge it stands on
    summary = run("{\"action\":\"modify_dormer\",\"roof\":\"F1\",\"index\":1,\"offset\":500}");
    assertTrue(summary, summary.contains("on the edge 4, its center 500 cm from the start of that edge"));
    assertEquals(300, getDormerWindow().getY(), 0.5f);
    // And a side given again chooses the edge
    summary = run("{\"action\":\"modify_dormer\",\"roof\":\"F1\",\"index\":1,\"side\":\"south\",\"position\":0.5}");
    assertTrue(summary, summary.contains("on the edge 7"));
  }

  /**
   * The answer of a command that changes a roof names the walls it leaves above the roof.
   */
  @Test
  public void testWallsLeftAboveRoofAreNamed() throws Exception {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Attic\",\"x\":0,\"y\":0,\"width\":1000,\"depth\":600,\"thickness\":20,\"height\":250},"
        + "{\"action\":\"add_roof\",\"points\":[[-10,-10],[1010,-10],[1010,610],[-10,610]],\"eave_height\":250,\"pitch\":40,"
        + "\"overhang\":30,\"type\":\"gable\",\"gable_walls\":false},"
        + "{\"action\":\"fit_walls_to_roof\",\"roof\":\"F1\"}");
    int walls = this.home.getWalls().size();
    assertTrue(walls > 4);
    // A lower pitch: the walls under the gables rise above the roof
    String summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"pitch\":20}");
    assertTrue(summary, summary.contains("walls now rise above this roof: "));
    assertTrue(summary, summary.contains("{\"action\":\"fit_walls_to_roof\",\"roof\":\"F1\",\"ids\":[\"W"));
    assertFalse(summary, summary.contains("Run fit_walls_to_roof again if walls were fitted"));
    // The command of the answer fits them
    String fit = summary.substring(summary.indexOf("{\"action\":\"fit_walls_to_roof\""));
    fit = fit.substring(0, fit.indexOf('}') + 1);
    run(fit);
    // A steeper pitch leaves no wall above the roof
    summary = run("{\"action\":\"modify_roof\",\"roof\":\"F1\",\"pitch\":21}");
    assertFalse(summary, summary.contains("above this roof"));
    assertTrue(summary, summary.contains("Run fit_walls_to_roof again if walls were fitted"));
  }
}
