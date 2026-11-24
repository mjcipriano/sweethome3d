/*
 * AssistantGardenCommandsTest.java 21 nov. 2026
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

import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the commands that build the garden (fences, hedges, paths, planting beds, scattered plants)
 * and the ones that furnish a room by rules.
 */
public class AssistantGardenCommandsTest {
  private String                   previousNo3D;
  private Locale                   previousLocale;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    // The names of the default catalog depend on the language
    this.previousLocale = Locale.getDefault();
    Locale.setDefault(Locale.UK);
    this.home = new Home(250);
    this.preferences = new DefaultUserPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
  }

  @After
  public void tearDown() {
    Locale.setDefault(this.previousLocale);
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
   * Returns the height above the elevation 0 of the bottom of a piece.
   */
  private static float getBottom(HomePieceOfFurniture piece) {
    return piece.getElevation() + (piece.getLevel() != null ? piece.getLevel().getElevation() : 0);
  }

  private static Area getArea(float [][] points) {
    GeneralPath path = new GeneralPath();
    path.moveTo(points [0][0], points [0][1]);
    for (int i = 1; i < points.length; i++) {
      path.lineTo(points [i][0], points [i][1]);
    }
    path.closePath();
    return new Area(path);
  }

  /**
   * Returns the area two shapes share, in cm2.
   */
  private static double getSharedArea(Area area1, Area area2) {
    Area shared = new Area(area1);
    shared.intersect(area2);
    if (shared.isEmpty()) {
      return 0;
    }
    Rectangle2D bounds = shared.getBounds2D();
    return bounds.getWidth() * bounds.getHeight();
  }

  /**
   * Returns the direction the front of a piece faces: +y at the angle 0.
   */
  private static float [] getFront(HomePieceOfFurniture piece) {
    return new float [] {-(float)Math.sin(piece.getAngle()), (float)Math.cos(piece.getAngle())};
  }

  private List<HomePieceOfFurniture> getFurniture() {
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (!piece.isDoorOrWindow()) {
        furniture.add(piece);
      }
    }
    return furniture;
  }

  private HomePieceOfFurniture getPieceNamed(String word, String excludedWord) {
    for (HomePieceOfFurniture piece : getFurniture()) {
      String name = piece.getName().toLowerCase(Locale.ENGLISH);
      if (name.contains(word) && (excludedWord == null || !name.contains(excludedWord))) {
        return piece;
      }
    }
    return null;
  }

  /**
   * Checks that the furniture is inside a rectangular room, that no piece overlaps another one
   * or the given zones kept free in front of doors.
   */
  private void assertLayout(String summary, float x1, float y1, float x2, float y2, float [][]... freeZones) {
    List<HomePieceOfFurniture> furniture = getFurniture();
    for (int i = 0; i < furniture.size(); i++) {
      HomePieceOfFurniture piece = furniture.get(i);
      String message = piece.getName() + " at " + piece.getX() + ", " + piece.getY() + "\n" + summary;
      for (float [] point : piece.getPoints()) {
        assertTrue("Inside the room: " + message, point [0] >= x1 + 0.4f && point [0] <= x2 - 0.4f
            && point [1] >= y1 + 0.4f && point [1] <= y2 - 0.4f);
      }
      Area area = getArea(piece.getPoints());
      for (int j = i + 1; j < furniture.size(); j++) {
        assertTrue("No overlap with " + furniture.get(j).getName() + ": " + message,
            getSharedArea(area, getArea(furniture.get(j).getPoints())) < 1);
      }
      for (float [][] zone : freeZones) {
        assertTrue("Door zone free: " + message, getSharedArea(area, getArea(zone)) < 1);
      }
    }
  }

  // ---- Garden ----

  @Test
  public void testFencePostsSpansAndGate() {
    // Two sides of 480 cm with a post every 240 cm: 5 posts, the corner one being shared
    String summary = run("{\"action\":\"add_fence\",\"points\":[[0,0],[480,0],[480,480]],\"post_spacing\":240}");
    assertTrue(summary, summary.contains("5 posts") && summary.contains("4 spans") && summary.contains("9.6 m long"));
    HomePieceOfFurniture fence = getPiece("Fence");
    assertNotNull(summary, fence);
    assertEquals("site", fence.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY));
    // Posts of 9 cm stand 6 cm above the pickets, under a cap of 2 cm
    assertEquals(128, fence.getHeight(), 0.5f);
    assertEquals(0, fence.getElevation(), 0.01f);
    assertEquals(491, fence.getWidth(), 1);
    assertEquals(491, fence.getDepth(), 1);
    assertTrue(summary, summary.contains(HomeItemIds.getId(fence)));
    // A fence isn't furniture out of its room
    String report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("Fence"));
    this.homeController.undo();
    assertTrue(this.home.getFurniture().isEmpty());

    // A gate in the middle of 10 m: two runs of 450 cm, each one cut in 2 spans
    summary = run("{\"action\":\"add_fence\",\"name\":\"Front fence\",\"points\":[[0,0],[1000,0]],\"style\":\"rail\","
        + "\"gate\":{\"at\":500,\"width\":100}}");
    assertTrue(summary, summary.contains("6 posts") && summary.contains("4 spans") && summary.contains("10 m of line (9 m of fence, 1 m of gate)")
        && summary.contains("single_swing 100 cm wide at 500 cm"));
    // The posts of the gate are 10 cm taller
    assertEquals(138, getPiece("Front fence").getHeight(), 0.5f);

    // A closed square: as many posts as spans
    summary = run("{\"action\":\"add_fence\",\"name\":\"Pen\",\"points\":[[0,0],[480,0],[480,480],[0,480]],\"closed\":true,"
        + "\"style\":\"panel\",\"height\":180,\"color\":\"#806040\"}");
    assertTrue(summary, summary.contains("8 posts") && summary.contains("8 spans") && summary.contains("closed"));
    summary = run("{\"action\":\"add_fence\",\"name\":\"Wires\",\"points\":[[0,0],[300,0]],\"style\":\"wire\",\"post_spacing\":300}");
    assertTrue(summary, summary.contains("2 posts") && summary.contains("1 span"));
    assertNotNull(getPiece("Wires"));

    // Mistakes are told
    int count = this.home.getFurniture().size();
    summary = run("{\"action\":\"add_fence\",\"points\":[[0,0]]}");
    assertTrue(summary, summary.contains("Couldn't run add_fence") && summary.contains("2 different points"));
    summary = run("{\"action\":\"add_fence\",\"points\":[[0,0],[300,0]],\"style\":\"laser\"}");
    assertTrue(summary, summary.contains("unknown \"style\"") && summary.contains("chain_link"));
    summary = run("{\"action\":\"add_fence\",\"points\":[[0,0],[300,0]],\"gate\":{\"at\":290,\"width\":100}}");
    assertTrue(summary, summary.contains("the gate goes from 240 to 340 cm"));
    assertEquals(count, this.home.getFurniture().size());
  }

  @Test
  public void testFenceHedgeAndPathFollowTheGround() {
    // A ground at 0 cm at x = 0 and 200 cm at x = 2000
    run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,-100],[3000,-1000,300],[3000,2000,300],[-1000,2000,-100]]}");
    String summary = run("{\"action\":\"add_hedge\",\"points\":[[0,500],[2000,500]],\"height\":150,\"width\":60}");
    HomePieceOfFurniture hedge = getPiece("Hedge");
    assertNotNull(summary, hedge);
    assertTrue(summary, summary.contains("20 m long") && summary.contains("following the ground from 0 to 200 cm"));
    // From a little under the ground at its low end to 150 cm above the ground at its high end
    assertEquals(-5, getBottom(hedge), 1);
    assertEquals(350, getBottom(hedge) + hedge.getHeight(), 1);
    assertEquals(2000, hedge.getWidth(), 1);
    assertEquals(60, hedge.getDepth(), 1);
    assertEquals(1000, hedge.getX(), 1);

    // A hedge across the slope keeps its height
    summary = run("{\"action\":\"add_hedge\",\"name\":\"Level hedge\",\"points\":[[1000,0],[1000,800]],\"height\":100,\"width\":40}");
    HomePieceOfFurniture levelHedge = getPiece("Level hedge");
    // The ground is 98 cm high under its low side and 102 cm under its high side
    assertEquals(98 - 5, getBottom(levelHedge), 1);
    assertEquals(202, getBottom(levelHedge) + levelHedge.getHeight(), 1);

    // The posts of a fence go up with the ground
    summary = run("{\"action\":\"add_fence\",\"points\":[[0,0],[1000,0]],\"height\":100,\"post_spacing\":250}");
    HomePieceOfFurniture fence = getPiece("Fence");
    assertTrue(summary, summary.contains("5 posts") && summary.contains("following the ground from 0 to 100 cm"));
    assertEquals(-5, getBottom(fence), 1.5f);
    assertEquals(100 + 100 + 6 + 2, getBottom(fence) + fence.getHeight(), 1.5f);

    // A path laid on the slope
    summary = run("{\"action\":\"add_path\",\"points\":[[0,1000],[2000,1000]],\"width\":120,\"thickness\":4,\"edging\":true}");
    HomePieceOfFurniture path = getPiece("Path");
    assertNotNull(summary, path);
    assertTrue(summary, summary.contains("20 m long") && summary.contains("24 m2") && summary.contains("edging"));
    assertEquals(-5, getBottom(path), 1);
    // 4 cm of paving and 3 cm of edging above the ground
    assertEquals(207, getBottom(path) + path.getHeight(), 1);
    assertEquals(130, path.getDepth(), 1);

    // A planting bed on the slope
    summary = run("{\"action\":\"add_planting_bed\",\"points\":[[500,1200],[1500,1200],[1500,1500],[500,1500]],"
        + "\"border\":{\"height\":12},\"plants\":{\"name\":\"Plant\",\"spacing\":100}}");
    HomePieceOfFurniture bed = getPiece("Planting bed");
    assertNotNull(summary, bed);
    assertTrue(summary, summary.contains("30 m2"));
    assertEquals(50 - 5, getBottom(bed), 1.5f);
    assertEquals(150 + 12, getBottom(bed) + bed.getHeight(), 1.5f);
    List<HomePieceOfFurniture> plants = getPieces("Plant");
    assertFalse(summary, plants.isEmpty());
    for (HomePieceOfFurniture plant : plants) {
      // Each plant stands in the soil at its place
      assertEquals(summary, plant.getX() / 10 + 3, getBottom(plant), 1);
    }
    String report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("Hedge") || report.contains("Path") || report.contains("Planting bed")
        || report.contains("\"Plant\"") || report.contains("Fence"));
  }

  @Test
  public void testPathSteppingStonesAndPlantingBed() {
    // A path with a corner, mitred: 100 cm wide around its center line
    String summary = run("{\"action\":\"add_path\",\"points\":[[0,0],[0,500],[300,500]],\"width\":100,\"color\":\"#AAAAAA\"}");
    HomePieceOfFurniture path = getPiece("Path");
    assertNotNull(summary, path);
    assertTrue(summary, summary.contains("8 m long") && summary.contains("100 cm wide"));
    assertEquals(350, path.getWidth(), 0.5f);
    assertEquals(550, path.getDepth(), 0.5f);
    assertEquals(4, path.getHeight(), 0.1f);
    assertEquals(0, path.getElevation(), 0.01f);

    // Stones of 40 cm every 60 cm along 5 m
    summary = run("{\"action\":\"add_path\",\"name\":\"Stones\",\"points\":[[1000,0],[1500,0]],\"width\":50,"
        + "\"stepping_stones\":{\"length\":40,\"gap\":20}}");
    assertTrue(summary, summary.contains("8 stepping stones"));
    HomePieceOfFurniture stones = getPiece("Stones");
    // 8 stones and 7 gaps, centered on the line
    assertEquals(460, stones.getWidth(), 0.5f);
    assertEquals(50, stones.getDepth(), 0.5f);
    assertEquals(1250, stones.getX(), 0.5f);

    // A texture of the catalog on a path
    summary = run("{\"action\":\"add_path\",\"name\":\"Paved path\",\"points\":[[2000,0],[2000,300]],\"texture\":\"Pebbles\"}");
    assertNotNull(summary, getPiece("Paved path"));
    assertFalse(summary, summary.contains("Couldn't") || summary.contains("texture"));
    this.homeController.undo();

    // A bed with its edging and its plants
    String bedCommand = "{\"action\":\"add_planting_bed\",\"points\":[[0,1000],[400,1000],[400,1200],[0,1200]],"
        + "\"border\":{\"height\":10,\"color\":\"#555555\"},\"plants\":{\"names\":[\"Plant\"],\"spacing\":80}}";
    summary = run(bedCommand);
    HomePieceOfFurniture bed = getPiece("Planting bed");
    assertNotNull(summary, bed);
    assertTrue(summary, summary.contains("8 m2") && summary.contains("edging"));
    assertEquals(405, bed.getWidth(), 0.5f);
    assertEquals(10, bed.getHeight(), 0.1f);
    List<HomePieceOfFurniture> plants = getPieces("Plant");
    // 5 columns and 2 rows of 80 cm
    assertEquals(summary, 10, plants.size());
    assertTrue(summary, summary.contains("10 plants"));
    List<String> places = new ArrayList<String>();
    for (HomePieceOfFurniture plant : plants) {
      assertTrue(summary, plant.getX() > 10 && plant.getX() < 390 && plant.getY() > 1010 && plant.getY() < 1190);
      assertEquals("true", plant.getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
      places.add(plant.getX() + "," + plant.getY() + "," + plant.getAngle());
    }
    // One step undoes the bed and its plants, and the same command places the plants at the same places
    this.homeController.undo();
    assertTrue(getPieces("Plant").isEmpty());
    assertEquals(2, this.home.getFurniture().size());
    run(bedCommand);
    List<String> placesAgain = new ArrayList<String>();
    for (HomePieceOfFurniture plant : getPieces("Plant")) {
      placesAgain.add(plant.getX() + "," + plant.getY() + "," + plant.getAngle());
    }
    assertEquals(places, placesAgain);

    // A name that isn't in the catalog
    summary = run("{\"action\":\"add_planting_bed\",\"name\":\"Empty bed\",\"points\":[[0,2000],[400,2000],[400,2200]],\"plants\":[\"Zzyzx\"]}");
    assertTrue(summary, summary.contains("No catalog piece is named \"Zzyzx\"") && summary.contains("No plant was added"));
    assertNotNull(getPiece("Empty bed"));
  }

  @Test
  public void testScatteredPiecesKeepAwayFromBuildings() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"House\",\"x\":0,\"y\":0,\"width\":600,\"depth\":400,\"thickness\":20}");
    String command = "{\"action\":\"add_trees\",\"area\":[[-1000,-1000],[1600,-1000],[1600,1400],[-1000,1400]],"
        + "\"names\":[\"Plant\"],\"spacing\":300}";
    String summary = run(command);
    List<HomePieceOfFurniture> plants = getPieces("Plant");
    assertTrue(summary, plants.size() >= 30);
    assertTrue(summary, summary.contains("Placed " + plants.size() + " pieces") && summary.contains("skipped"));
    List<String> places = new ArrayList<String>();
    for (HomePieceOfFurniture plant : plants) {
      // 150 cm at least from the room and from its walls, 20 cm thick
      assertTrue(summary + "\n" + plant.getX() + ", " + plant.getY(), plant.getX() < -169 || plant.getX() > 769
          || plant.getY() < -169 || plant.getY() > 569);
      assertTrue(plant.getX() > -1000 && plant.getX() < 1600 && plant.getY() > -1000 && plant.getY() < 1400);
      assertEquals("true", plant.getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
      places.add(plant.getX() + "," + plant.getY());
    }
    String report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("\"Plant\""));
    this.homeController.undo();
    assertTrue(getPieces("Plant").isEmpty());
    // The same places each time
    run(command);
    List<String> placesAgain = new ArrayList<String>();
    for (HomePieceOfFurniture plant : getPieces("Plant")) {
      placesAgain.add(plant.getX() + "," + plant.getY());
    }
    assertEquals(places, placesAgain);
    this.homeController.undo();

    // A count of pieces along a line, from an end to the other
    summary = run("{\"action\":\"scatter\",\"along\":[[-500,-800],[-500,1200]],\"name\":\"Plant\",\"count\":5}");
    plants = getPieces("Plant");
    assertEquals(summary, 5, plants.size());
    for (int i = 0; i < 5; i++) {
      assertEquals(-500, plants.get(i).getX(), 0.01f);
      assertEquals(-800 + i * 500, plants.get(i).getY(), 0.01f);
    }
    this.homeController.undo();
    // A line through the house: the places in it are skipped
    summary = run("{\"action\":\"scatter\",\"along\":[[-600,200],[1200,200]],\"name\":\"Plant\",\"spacing\":200}");
    plants = getPieces("Plant");
    assertEquals(summary, 6, plants.size());
    assertTrue(summary, summary.contains("4 places were skipped"));
    for (HomePieceOfFurniture plant : plants) {
      assertTrue(summary, plant.getX() < -169 || plant.getX() > 769);
    }
    // A count in an area
    this.homeController.undo();
    summary = run("{\"action\":\"scatter\",\"area\":[[2000,0],[3000,0],[3000,1000],[2000,1000]],\"name\":\"Plant\",\"count\":7}");
    assertEquals(summary, 7, getPieces("Plant").size());

    summary = run("{\"action\":\"add_trees\",\"area\":[[0,0],[1000,0],[1000,1000]],\"names\":[\"Zzyzx\"]}");
    assertTrue(summary, summary.contains("No catalog piece is named \"Zzyzx\"") && summary.contains("Couldn't run add_trees"));
    summary = run("{\"action\":\"add_trees\",\"names\":[\"Plant\"]}");
    assertTrue(summary, summary.contains("Couldn't run add_trees") && summary.contains("\"area\""));
  }

  // ---- Rooms ----

  /**
   * A room of 420 x 360 cm inside walls 20 cm thick, with a door in its bottom wall and a window in its top wall.
   */
  private void addRoom(String name) {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"" + name + "\",\"x\":0,\"y\":0,\"width\":420,\"depth\":360,\"thickness\":20},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Door\",\"x\":70,\"y\":370,\"angle\":0},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Window\",\"x\":210,\"y\":-10,\"angle\":0}");
    assertEquals(2, this.home.getFurniture().size());
  }

  /** The zone the door of the room of {@link #addRoom(String)} needs: its width, 91.5 cm deep. */
  private static final float [][] DOOR_ZONE = {{70 - 45.75f, 360 - 91.5f}, {70 + 45.75f, 360 - 91.5f}, {70 + 45.75f, 360}, {70 - 45.75f, 360}};
  /** The zone in front of the window where nothing tall goes. */
  private static final float [][] WINDOW_ZONE = {{210 - 45.5f, 0}, {210 + 45.5f, 0}, {210 + 45.5f, 35}, {210 - 45.5f, 35}};

  @Test
  public void testFurnishBedroom() {
    addRoom("Bedroom");
    String roomId = HomeItemIds.getId(this.home.getRooms().get(0));
    // The layout is first told without changing anything
    String suggestion = run("{\"action\":\"suggest_layout\",\"room\":\"" + roomId + "\"}");
    assertTrue(suggestion, suggestion.contains("Layout suggested") && suggestion.contains("as a bedroom (guessed from its name)")
        && suggestion.contains("1 door, 1 window") && suggestion.contains("Would place"));
    assertEquals(2, this.home.getFurniture().size());

    String summary = run("{\"action\":\"furnish_room\",\"room\":\"Bedroom\"}");
    assertTrue(summary, summary.contains("Furnished the room " + roomId + " \"Bedroom\"") && summary.contains("Free floor left"));
    assertLayout(summary, 0, 0, 420, 360, DOOR_ZONE);
    HomePieceOfFurniture bed = getPieceNamed("bed", "bedside");
    assertNotNull(summary, bed);
    assertTrue(summary, summary.contains(HomeItemIds.getId(bed) + " \"" + bed.getName() + "\""));
    // The places told by suggest_layout are the ones used
    assertTrue(suggestion, suggestion.contains("\"" + bed.getName() + "\" at ("
        + String.valueOf(Math.round(bed.getX() * 100) / 100f).replaceAll("\\.0$", "") + ", "));
    // The head of the bed is against a wall and its foot in the room
    float [] front = getFront(bed);
    float backX = bed.getX() - front [0] * (bed.getDepth() / 2 + 3);
    float backY = bed.getY() - front [1] * (bed.getDepth() / 2 + 3);
    assertTrue(summary, backX < 0 || backX > 420 || backY < 0 || backY > 360);
    // Bedside tables on both sides, against the same wall
    List<HomePieceOfFurniture> bedsideTables = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : getFurniture()) {
      if (piece.getName().toLowerCase(Locale.ENGLISH).contains("bedside")) {
        bedsideTables.add(piece);
        assertEquals(summary, bed.getAngle(), piece.getAngle(), 1E-3f);
        assertEquals(summary, bed.getWidth() / 2 + piece.getWidth() / 2 + 1,
            Math.hypot(piece.getX() - bed.getX(), piece.getY() - bed.getY()) , 40);
      }
    }
    assertEquals(summary, 2, bedsideTables.size());
    // The wardrobe is tall: not in front of the window
    HomePieceOfFurniture wardrobe = getPieceNamed("wardrobe", null);
    assertNotNull(summary, wardrobe);
    assertTrue(summary, wardrobe.getHeight() > 100);
    for (HomePieceOfFurniture piece : getFurniture()) {
      if (piece.getHeight() > 100) {
        assertTrue(summary, getSharedArea(getArea(piece.getPoints()), getArea(WINDOW_ZONE)) < 1);
      }
      assertEquals(summary, 0, piece.getElevation(), 0.01f);
    }
    String report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("overlap"));

    // One step undoes it all
    int count = this.home.getFurniture().size();
    assertTrue(summary, count >= 6);
    this.homeController.undo();
    assertEquals(2, this.home.getFurniture().size());
    this.homeController.redo();
    assertEquals(count, this.home.getFurniture().size());

    // Furnishing again keeps away from what is there, and "replace" starts again from the empty room
    List<String> places = new ArrayList<String>();
    for (HomePieceOfFurniture piece : getFurniture()) {
      places.add(piece.getName() + " " + piece.getX() + "," + piece.getY() + "," + piece.getAngle());
    }
    summary = run("{\"action\":\"furnish_room\",\"room\":\"Bedroom\",\"replace\":true}");
    assertTrue(summary, summary.contains("removed " + (count - 2) + " pieces"));
    assertEquals(summary, count, this.home.getFurniture().size());
    List<String> placesAgain = new ArrayList<String>();
    for (HomePieceOfFurniture piece : getFurniture()) {
      placesAgain.add(piece.getName() + " " + piece.getX() + "," + piece.getY() + "," + piece.getAngle());
    }
    assertEquals(places, placesAgain);
    this.homeController.undo();
    assertEquals(count, this.home.getFurniture().size());
    summary = run("{\"action\":\"furnish_room\",\"room\":\"Bedroom\",\"max_items\":1}");
    assertEquals(summary, count + 1, this.home.getFurniture().size());
    assertLayout(summary, 0, 0, 420, 360, DOOR_ZONE);
  }

  @Test
  public void testFurnishEveryTypeOfRoom() {
    addRoom("Room");
    for (String type : new String [] {"living_room", "dining_room", "kitchen", "office", "bathroom", "kids_room", "entry", "bedroom"}) {
      String summary = run("{\"action\":\"furnish_room\",\"room\":\"Room\",\"type\":\"" + type + "\"}");
      assertTrue(summary, summary.contains("Furnished the room") && summary.contains(type.replace('_', ' ')));
      List<HomePieceOfFurniture> furniture = getFurniture();
      assertTrue(type + ": " + summary, furniture.size() >= 3);
      assertLayout(type + ": " + summary, 0, 0, 420, 360, DOOR_ZONE);
      for (HomePieceOfFurniture piece : furniture) {
        if (piece.getHeight() > 100) {
          assertTrue(type + ": " + summary, getSharedArea(getArea(piece.getPoints()), getArea(WINDOW_ZONE)) < 1);
        }
      }
      if ("living_room".equals(type)) {
        // The sofa and the TV unit face each other
        HomePieceOfFurniture sofa = getPieceNamed("sofa", null);
        HomePieceOfFurniture unit = getPieceNamed("tv", null);
        assertNotNull(summary, sofa);
        assertNotNull(summary, unit);
        float [] sofaFront = getFront(sofa);
        float [] unitFront = getFront(unit);
        assertEquals(summary, -1, sofaFront [0] * unitFront [0] + sofaFront [1] * unitFront [1], 1E-3f);
        // The unit is in front of the sofa
        assertTrue(summary, (unit.getX() - sofa.getX()) * sofaFront [0] + (unit.getY() - sofa.getY()) * sofaFront [1] > 150);
        assertEquals(summary, 0, (unit.getX() - sofa.getX()) * sofaFront [1] - (unit.getY() - sofa.getY()) * sofaFront [0], 1);
      } else if ("dining_room".equals(type)) {
        // Chairs around the table and turned to it
        HomePieceOfFurniture table = getPieceNamed("table", null);
        assertNotNull(summary, table);
        int chairs = 0;
        for (HomePieceOfFurniture piece : furniture) {
          if (piece.getName().toLowerCase(Locale.ENGLISH).contains("chair")) {
            chairs++;
            float [] front = getFront(piece);
            double distance = Math.hypot(table.getX() - piece.getX(), table.getY() - piece.getY());
            assertTrue(summary, ((table.getX() - piece.getX()) * front [0] + (table.getY() - piece.getY()) * front [1]) / distance > 0.7);
            assertTrue(summary, distance < Math.max(table.getWidth(), table.getDepth()) / 2 + piece.getDepth() + 10);
          }
        }
        assertTrue(summary, chairs >= 4);
      } else if ("office".equals(type)) {
        // The desk under the window of the top wall, its chair in front of it
        HomePieceOfFurniture desk = getPieceNamed("desk", null);
        assertNotNull(summary, desk);
        assertEquals(summary, 210, desk.getX(), 1);
        assertEquals(summary, 1 + desk.getDepth() / 2, desk.getY(), 0.5f);
        assertEquals(summary, 0, desk.getAngle(), 1E-3f);
        HomePieceOfFurniture chair = getPieceNamed("chair", "arm");
        assertNotNull(summary, chair);
        assertEquals(summary, desk.getX(), chair.getX(), 1);
        assertTrue(summary, chair.getY() > desk.getY());
        assertEquals(summary, Math.PI, chair.getAngle(), 1E-3f);
      } else if ("kitchen".equals(type)) {
        assertNotNull(summary, getPieceNamed("fridge", null));
        assertNotNull(summary, getPieceNamed("cooker", null));
        assertNotNull(summary, getPieceNamed("sink", null));
      } else if ("bathroom".equals(type)) {
        assertNotNull(summary, getPieceNamed("bath", null));
        assertNotNull(summary, getPieceNamed("toilet", null));
        assertNotNull(summary, getPieceNamed("washbasin", null));
      }
      this.homeController.undo();
      assertEquals(type, 2, this.home.getFurniture().size());
    }
  }

  @Test
  public void testFurnishSmallAndOddRooms() {
    // A room too small for a bed: nothing overlaps, and what is skipped is told
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Cupboard\",\"x\":0,\"y\":0,\"width\":150,\"depth\":120,\"thickness\":10},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Door\",\"x\":60,\"y\":125,\"angle\":0}");
    String summary = run("{\"action\":\"furnish_room\",\"room\":\"Cupboard\",\"type\":\"bedroom\"}");
    assertTrue(summary, summary.contains("Nothing was placed") && summary.contains("Skipped:")
        && summary.contains("no free wall long enough"));
    assertEquals(summary, 1, this.home.getFurniture().size());
    // Nothing changed: the last step to undo is the room
    this.homeController.undo();
    assertTrue(this.home.getFurniture().isEmpty() && this.home.getRooms().isEmpty());

    // An L shaped room drawn through the middle of its walls
    run("{\"action\":\"add_room\",\"name\":\"Living room\",\"points\":[[0,0],[600,0],[600,300],[350,300],[350,500],[0,500]]},"
        + "{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":600,\"y2\":0,\"thickness\":20},"
        + "{\"action\":\"add_wall\",\"x1\":600,\"y1\":0,\"x2\":600,\"y2\":300,\"thickness\":20},"
        + "{\"action\":\"add_wall\",\"x1\":600,\"y1\":300,\"x2\":350,\"y2\":300,\"thickness\":20},"
        + "{\"action\":\"add_wall\",\"x1\":350,\"y1\":300,\"x2\":350,\"y2\":500,\"thickness\":20},"
        + "{\"action\":\"add_wall\",\"x1\":350,\"y1\":500,\"x2\":0,\"y2\":500,\"thickness\":20},"
        + "{\"action\":\"add_wall\",\"x1\":0,\"y1\":500,\"x2\":0,\"y2\":0,\"thickness\":20},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Door\",\"x\":0,\"y\":250,\"angle\":90}");
    summary = run("{\"action\":\"furnish_room\",\"room\":\"Living room\"}");
    assertTrue(summary, summary.contains("as a living room (guessed from its name)") && summary.contains("1 door"));
    List<HomePieceOfFurniture> furniture = getFurniture();
    assertTrue(summary, furniture.size() >= 4);
    Area floor = getArea(new float [][] {{10, 10}, {590, 10}, {590, 290}, {340, 290}, {340, 490}, {10, 490}});
    Area doorZone = getArea(new float [][] {{10, 250 - 45.75f}, {101.5f, 250 - 45.75f}, {101.5f, 250 + 45.75f}, {10, 250 + 45.75f}});
    for (int i = 0; i < furniture.size(); i++) {
      Area area = getArea(furniture.get(i).getPoints());
      String message = furniture.get(i).getName() + "\n" + summary;
      // Between the faces of the walls
      Area outside = new Area(area);
      outside.subtract(floor);
      assertTrue(message, outside.isEmpty() || outside.getBounds2D().getWidth() * outside.getBounds2D().getHeight() < 1);
      assertTrue(message, getSharedArea(area, doorZone) < 1);
      for (int j = i + 1; j < furniture.size(); j++) {
        assertTrue(message, getSharedArea(area, getArea(furniture.get(j).getPoints())) < 1);
      }
    }

    // Mistakes are told
    summary = run("{\"action\":\"furnish_room\",\"room\":\"Garage\"}");
    assertTrue(summary, summary.contains("no room has the id or the name \"Garage\"") && summary.contains("\"Living room\""));
    summary = run("{\"action\":\"furnish_room\",\"room\":\"Living room\",\"type\":\"dungeon\"}");
    assertTrue(summary, summary.contains("bedroom, living_room, dining_room"));
    run("{\"action\":\"add_room\",\"name\":\"Zone 4\",\"points\":[[1000,0],[1400,0],[1400,400],[1000,400]]}");
    summary = run("{\"action\":\"suggest_layout\",\"room\":\"Zone 4\"}");
    assertTrue(summary, summary.contains("doesn't tell what it is"));
  }

  @Test
  public void testCommandsAreInTheReference() {
    String reference = HomeAssistantContext.getCommandReference();
    for (String action : new String [] {"add_fence", "add_hedge", "add_path", "add_planting_bed", "add_trees",
                                        "furnish_room", "suggest_layout"}) {
      assertTrue(action, reference.contains("  {\"action\":\"" + action + "\""));
    }
  }
}
