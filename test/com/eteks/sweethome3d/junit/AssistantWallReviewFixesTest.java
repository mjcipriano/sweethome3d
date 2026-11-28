/*
 * AssistantWallReviewFixesTest.java 27 nov. 2026
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
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Baseboard;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeApplication;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeRecorder;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.HomeVersions;
import com.eteks.sweethome3d.viewcontroller.JSONParser;
import com.eteks.sweethome3d.viewcontroller.WallFinishBands;

/**
 * Tests what a review of a real house in 3D found about the commands that finish walls:
 * a command that fails changes nothing, a finish stops under a roof whatever built that roof,
 * the color of a side is a key of <code>modify_wall</code>, a point of a picture names the
 * finish drawn there, and the label of a version is kept whole.
 * @author Sweet Home 3D contributors
 */
public class AssistantWallReviewFixesTest {
  private String                   previousNo3D;
  private File                     folder;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.folder = File.createTempFile("wall-review", "");
    this.folder.delete();
    assertTrue(this.folder.mkdir());
    this.home = new Home(250);
    this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
  }

  @After
  public void tearDown() {
    delete(this.folder);
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private static void delete(File file) {
    File [] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        delete(child);
      }
    }
    file.delete();
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  /**
   * Adds a wall from (0, 0) to (400, 0), 10 cm thick and 500 cm high, and returns it.
   */
  private Wall addWall() {
    Wall wall = new Wall(0, 0, 400, 0, 10, 250);
    wall.setHeight(500f);
    this.home.addWall(wall);
    HomeItemIds.ensureIds(this.home);
    return wall;
  }

  /**
   * Adds a piece that shows a roof and wasn't built by a command, with or without the tag of roofs.
   */
  private HomePieceOfFurniture addRoofPiece(String name, float x, float y, float width, float depth, float height,
                                            float elevation, boolean tagged) {
    CatalogPieceOfFurniture catalogPiece = this.executor.findCatalogPiece("Sofa", false);
    assertNotNull(catalogPiece);
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    piece.setName(name);
    piece.setX(x);
    piece.setY(y);
    piece.setWidth(width);
    piece.setDepth(depth);
    piece.setHeight(height);
    piece.setElevation(elevation);
    if (tagged) {
      piece.setProperty("tags", "roof");
    }
    this.home.addPieceOfFurniture(piece);
    HomeItemIds.ensureIds(this.home);
    return piece;
  }

  /**
   * A command of walls that can't be applied for one of its keys applies none of them.
   */
  @Test
  public void testFailedModifyWallChangesNothing() {
    Wall wall = addWall();
    String id = HomeItemIds.getId(wall);
    wall.setRightSideBaseboard(Baseboard.getInstance(1, 275, 0xD5AC6B, null));
    wall.setRightSideColor(0x808080);

    // No roof in this home: the baseboard that the same command removes stays
    String summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"right_finish\":[{\"to\":\"roof\",\"color\":\"#D5AC6B\"}],"
        + "\"right_baseboard\":false,\"height\":300,\"left_color\":\"#112233\"}");
    assertTrue(summary, summary == null || summary.contains("nothing was changed"));
    assertNotNull("The baseboard is kept", wall.getRightSideBaseboard());
    assertEquals(500f, wall.getHeight(), 0);
    assertNull(wall.getLeftSideColor());
    assertTrue(WallFinishBands.getBands(wall, false).isEmpty());
    assertEquals("Nothing to undo", 0, this.home.getFurniture().size());

    // An opening out of the wall, with a color and a finish that could be applied
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"left_color\":\"#112233\","
        + "\"right_finish\":[{\"to\":120,\"color\":\"#D5AC6B\"}],\"openings\":[{\"from\":300,\"to\":900}]}");
    assertTrue(summary, summary == null || summary.contains("nothing was changed"));
    assertNull(wall.getLeftSideColor());
    assertTrue(WallFinishBands.getBands(wall, false).isEmpty());
    assertTrue(this.home.getFurniture().isEmpty());

    // A color that isn't one, a finish given twice
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"left_color\":\"greenish\",\"height\":300}");
    assertTrue(summary, summary == null || summary.contains("nothing was changed"));
    assertEquals(500f, wall.getHeight(), 0);
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"right_color\":\"#FFFFFF\","
        + "\"right_finish\":[{\"to\":120,\"color\":\"#D5AC6B\"},{\"color\":\"#000000\"}]}");
    assertTrue(summary, summary == null || summary.contains("both give the finish of the top of the side"));
    assertEquals(Integer.valueOf(0x808080), wall.getRightSideColor());

    // The same command with keys that can all be applied
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"right_finish\":[{\"to\":275,\"color\":\"#D5AC6B\"}],"
        + "\"right_baseboard\":false,\"height\":300,\"left_color\":\"#112233\"}");
    assertNotNull(summary);
    assertFalse(summary, summary.contains("Ignored"));
    assertNull(wall.getRightSideBaseboard());
    assertEquals(300f, wall.getHeight(), 0);
    assertEquals(Integer.valueOf(0x112233), wall.getLeftSideColor());
    assertEquals(1, WallFinishBands.getBands(wall, false).size());
    // One step to undo
    this.homeController.undo();
    assertNotNull(wall.getRightSideBaseboard());
    assertEquals(500f, wall.getHeight(), 0);
    assertNull(wall.getLeftSideColor());
    assertTrue(WallFinishBands.getBands(wall, false).isEmpty());

    // The color of the walls of a room given with a name: neither is applied when the color can't be
    Room room = new Room(new float [][] {{0, 5}, {400, 5}, {400, 300}, {0, 300}});
    room.setName("Kitchen");
    this.home.addRoom(room);
    HomeItemIds.ensureIds(this.home);
    String roomId = HomeItemIds.getId(room);
    summary = run("{\"action\":\"modify_room\",\"id\":\"" + roomId + "\",\"wall_color\":\"greenish\",\"name\":\"Pantry\"}");
    assertTrue(summary, summary == null || summary.contains("nothing was changed"));
    assertEquals("Kitchen", room.getName());
    assertNull(room.getProperty("com.eteks.sweethome3d.wallColor"));
    summary = run("{\"action\":\"modify_room\",\"id\":\"" + roomId + "\",\"wall_color\":\"#AABBCC\",\"only\":\"some\",\"name\":\"Pantry\"}");
    assertTrue(summary, summary == null || summary.contains("nothing was changed"));
    assertEquals("Kitchen", room.getName());
    assertNull("Not stored without the walls painted", room.getProperty("com.eteks.sweethome3d.wallColor"));
    summary = run("{\"action\":\"modify_room\",\"id\":\"" + roomId + "\",\"wall_color\":\"#AABBCC\",\"name\":\"Pantry\"}");
    assertEquals(summary, "Pantry", room.getName());
    assertEquals("AABBCC", room.getProperty("com.eteks.sweethome3d.wallColor"));
  }

  /**
   * A finish stops under the roof in front of a side whatever built that roof, read in the
   * triangles of its 3D model.
   */
  @Test
  public void testFinishUnderRoofOfAnyKind() {
    Wall wall = addWall();
    String id = HomeItemIds.getId(wall);
    // A roof made by hand in front of the right side of the wall (y from 5 to 305), 80 cm beyond each of its ends:
    // one slope that rises along the wall, from 250 cm at x = -80 to 400 cm at x = 480
    final HomePieceOfFurniture roof = addRoofPiece("Lean-to made by hand", 200, 155, 560, 300, 150, 250, true);
    // A shelf in the room, which isn't a roof
    final HomePieceOfFurniture shelf = addRoofPiece("Shelf", 200, 25, 300, 40, 5, 180, false);
    // A roof elsewhere
    HomePieceOfFurniture otherRoof = addRoofPiece("Shed roof", 2000, 2000, 300, 300, 100, 250, true);
    final int [] reads = {0};
    this.executor.setModelShapeProvider(new AssistantCommandExecutor.ModelShapeProvider() {
        public float [][] getTriangles(Content model) {
          reads [0]++;
          // A slope in a file of any unit and place: x from 10 to 12, z from -1 to 1, y from 0 at x = 10 to 3 at x = 12
          return new float [][] {{10, 0, -1, 12, 3, -1, 12, 3, 1}, {10, 0, -1, 12, 3, 1, 10, 0, 1}};
        }
      });
    // Which side of the wall the roof is in front of
    boolean rightSide = wall.getPoints() [wall.getPoints().length - 1][1] > 0;
    String side = rightSide ? "right" : "left";
    String otherSide = rightSide ? "left" : "right";

    String summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + side + "_finish\":[{\"to\":\"roof\",\"color\":\"#D5AC6B\"}]}");
    assertNotNull(summary);
    assertTrue(summary, summary.contains("under the roof " + HomeItemIds.getId(roof) + " \"Lean-to made by hand\""));
    assertFalse("Its real shape, not its box: " + summary, summary.contains("box"));
    List<WallFinishBands.Band> bands = WallFinishBands.getBands(wall, rightSide ? false : true);
    assertEquals(summary, 1, bands.size());
    // Under the slope: 250 + 150 * 80 / 560 at the start of the wall, 250 + 150 * 480 / 560 at its end
    assertEquals(summary, 271.4f, bands.get(0).getTop(), 3f);
    assertEquals(summary, 378.6f, bands.get(0).getTopAtEnd(), 3f);
    assertTrue(reads [0] >= 1);

    // The roof named, of any kind: the shelf, which has no tag of roof
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + side + "_finish\":[{\"to\":\"roof\",\"roof\":\""
        + HomeItemIds.getId(shelf) + "\",\"color\":\"#D5AC6B\"}]}");
    assertNotNull(summary);
    assertTrue(summary, summary.contains("under the roof " + HomeItemIds.getId(shelf) + " \"Shelf\""));
    bands = WallFinishBands.getBands(wall, rightSide ? false : true);
    assertTrue(summary, bands.get(0).getTop() >= 179 && bands.get(0).getTop() <= 186);

    // No roof in front of the other side: the answer tells which roofs were looked at and what to give
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + otherSide + "_finish\":[{\"to\":\"roof\",\"color\":\"#D5AC6B\"}]}");
    assertTrue(summary, summary == null || summary.contains("nothing was changed"));
    assertTrue(summary, summary.contains("no roof stands in front of the " + otherSide + " side of " + id));
    assertTrue(summary, summary.contains(HomeItemIds.getId(roof) + " \"Lean-to made by hand\""));
    assertTrue(summary, summary.contains(HomeItemIds.getId(otherRoof) + " \"Shed roof\" isn't over that side"));
    assertTrue(summary, summary.contains("\"roof\":\"<id>\""));
    assertFalse("The shelf isn't a roof: " + summary, summary.contains("\"Shelf\""));
    assertTrue(WallFinishBands.getBands(wall, rightSide).isEmpty());
    // A roof named that isn't there
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + side + "_finish\":[{\"to\":\"roof\",\"roof\":\""
        + HomeItemIds.getId(otherRoof) + "\",\"color\":\"#D5AC6B\"}]}");
    assertTrue(summary, summary.contains("\"Shed roof\" isn't over the " + side + " side of " + id));

    // Where models can't be read, the bottom of the box of the roof, and the answer says so
    this.executor.setModelShapeProvider(null);
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + side + "_finish\":[{\"to\":\"roof\",\"color\":\"#D5AC6B\"}]}");
    assertNotNull(summary);
    assertTrue(summary, summary.contains("the bottom of its box"));
    bands = WallFinishBands.getBands(wall, rightSide ? false : true);
    assertEquals(summary, 250f, bands.get(0).getTop(), 1f);
    assertEquals(summary, 250f, bands.get(0).getTopAtEnd(), 1f);
  }

  /**
   * The color and the texture of a side are keys of <code>modify_wall</code>, and a key it
   * doesn't have is reported with the key or the command that does what it suggests.
   */
  @Test
  public void testSideColorsOfModifyWall() {
    Wall wall = addWall();
    String id = HomeItemIds.getId(wall);
    String texture = this.preferences.getTexturesCatalog().getCategories().get(0).getTextures().get(0).getName();
    String summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"left_color\":\"#FFFFFF\",\"right_texture\":\"" + texture + "\"}");
    assertNotNull(summary);
    assertEquals(summary, Integer.valueOf(0xFFFFFF), wall.getLeftSideColor());
    assertNotNull(summary, wall.getRightSideTexture());
    assertEquals(texture, wall.getRightSideTexture().getName());
    assertTrue(summary, summary.contains("The left side of " + id + " shows #FFFFFF"));
    // Both sides, then none
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"color\":\"#102030\"}");
    assertEquals(summary, Integer.valueOf(0x102030), wall.getLeftSideColor());
    assertEquals(summary, Integer.valueOf(0x102030), wall.getRightSideColor());
    assertNull("A color takes the place of the texture", wall.getRightSideTexture());
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"right_color\":null,\"left_color\":\"none\"}");
    assertNull(summary, wall.getRightSideColor());
    assertNull(summary, wall.getLeftSideColor());
    this.homeController.undo();
    assertEquals(Integer.valueOf(0x102030), wall.getLeftSideColor());
    // The same side by two keys
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"color\":\"#102030\",\"left_color\":\"#FFFFFF\"}");
    assertTrue(summary, summary == null || summary.contains("nothing was changed"));

    // Keys it doesn't have
    summary = run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"height\":260,\"left_side_colour\":\"#FFFFFF\",\"band\":3}");
    assertEquals(260f, wall.getHeight(), 0);
    assertTrue(summary, summary.contains("modify_wall ignored the keys [left_side_colour, band]"));
    assertTrue(summary, summary.contains("\"left_color\" or \"right_color\" (or set_color with \"part\":\"left|right\")"));
    assertTrue(summary, summary.contains("\"left_finish\" and \"right_finish\""));
    assertTrue(summary, summary.contains("Its keys: [id, ids, target, where, height"));
  }

  private static String identify(Home home, Wall wall, float x, float y, float elevation) {
    return HomeMCPHandler.describeIdentifiedItem(home,
        new HomeMCPHandler.IdentifiedItem(10, 10, wall, null, new float [] {x, y, elevation}, 300));
  }

  /**
   * A point of a picture on a wall names the finish drawn there: the baseboard, one of the
   * finishes that change with the height, or the finish of the side above them.
   */
  @Test
  public void testIdentifiedFinishOfWallSide() {
    Wall wall = addWall();
    String id = HomeItemIds.getId(wall);
    String texture = this.preferences.getTexturesCatalog().getCategories().get(0).getTextures().get(0).getName();
    boolean rightAtPositiveY = wall.getPoints() [wall.getPoints().length - 1][1] > 0;
    String side = rightAtPositiveY ? "right" : "left";
    // A side with the texture of the outside, as the walls with siding
    run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + side + "_texture\":\"" + texture + "\"}");
    // A point 0.4 cm in front of the side, where its finishes are drawn
    float y = 5.4f;
    String text = identify(this.home, wall, 200, y, 100);
    assertTrue(text, text.contains("its " + side + " side (texture \"" + texture + "\")"));

    // The paint of the room up to 275 cm, as a tall baseboard
    run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + side + "_baseboard\":{\"height\":275,\"thickness\":1,\"color\":\"#D5AC6B\"}}");
    text = identify(this.home, wall, 200, 6, 100);
    assertTrue(text, text.contains("its " + side + " side at its baseboard, 275 cm high (color #D5AC6B)"));
    assertFalse("Not the texture of the side behind the baseboard: " + text, text.contains(texture));
    text = identify(this.home, wall, 200, y, 300);
    assertTrue(text, text.contains("its " + side + " side (texture \"" + texture + "\")"));

    // The same as finishes by height, the second one with a sloped top
    run("{\"action\":\"modify_wall\",\"id\":\"" + id + "\",\"" + side + "_baseboard\":false,\"" + side + "_finish\":["
        + "{\"to\":100,\"color\":\"#112233\"},{\"to\":200,\"to_at_end\":400,\"color\":\"#D5AC6B\"}]}");
    text = identify(this.home, wall, 200, y, 50);
    assertTrue(text, text.contains("its " + side + " side, finish 1 of 2 from the floor (color #112233, up to 100 cm"));
    assertTrue(text, text.contains("\"" + side + "_finish\""));
    text = identify(this.home, wall, 40, y, 150);
    assertTrue(text, text.contains("finish 2 of 2 from the floor (color #D5AC6B, up to 200 cm at the start of the wall and 400 cm at its end"));
    // Above the second finish near the start of the wall, under it near its end
    text = identify(this.home, wall, 40, y, 250);
    assertTrue(text, text.contains("its " + side + " side, above its 2 finishes from the floor (texture \"" + texture + "\")"));
    text = identify(this.home, wall, 380, y, 250);
    assertTrue(text, text.contains("finish 2 of 2"));
    // The other side has none
    text = identify(this.home, wall, 200, -5.4f, 50);
    assertTrue(text, text.contains("its " + (rightAtPositiveY ? "left" : "right") + " side (no color nor texture)"));
    // A baseboard without color in front of the finishes shows the finish of the side
    wall.setLeftSideBaseboard(Baseboard.getInstance(1, 10, null, null));
    wall.setRightSideBaseboard(Baseboard.getInstance(1, 10, null, null));
    text = identify(this.home, wall, 200, 6, 5);
    assertTrue(text, text.contains("at its baseboard, 10 cm high (without color nor texture of its own"));
  }

  /**
   * The label of a version is kept whole in its name, commas included, and designates it.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testLabelOfVersionIsKept() throws Exception {
    final UserPreferences applicationPreferences = this.preferences;
    final HomeRecorder recorder = new HomeFileRecorder();
    HomeApplication application = new HomeApplication() {
        @Override
        public UserPreferences getUserPreferences() {
          return applicationPreferences;
        }

        @Override
        public HomeRecorder getHomeRecorder() {
          return recorder;
        }
      };
    Home savedHome = new Home(250);
    File file = new File(this.folder, "house.sh3d");
    HomeController controller = new HomeController(savedHome, application, new SwingViewFactory());
    controller.getView();
    recorder.writeHome(savedHome, file.getPath());
    savedHome.setName(file.getPath());
    HomeMCPHandler handler = new HomeMCPHandler(savedHome, applicationPreferences, controller, "test");
    handler.setEditingAllowed(true);

    // The label of the review: 77 characters, cut at 60 before, just after its first comma
    String label = "After the review of check_home (W8 white in garage, W62 removed, W176 finish band)";
    Map<String, Object> result = call(handler, "save_version", "{\"label\":\"" + label + "\"}");
    String text = text(result);
    assertEquals(text, Boolean.FALSE, result.get("isError"));
    assertTrue(text, text.contains(label));
    assertFalse(text, text.contains("shortened"));
    List<File> versions = HomeVersions.getVersions(savedHome);
    assertEquals(1, versions.size());
    assertEquals(label, HomeVersions.getLabelText(versions.get(0)));
    assertTrue(versions.get(0).getName(), versions.get(0).getName().endsWith(label + ".sh3d"));
    // Found by its whole label, by a part of it, by a part after its commas
    assertEquals(versions.get(0), HomeVersions.find(savedHome, label));
    assertEquals(versions.get(0), HomeVersions.find(savedHome, "W176 finish band"));
    assertEquals(versions.get(0), HomeVersions.find(savedHome, "white in garage, W62"));
    assertTrue(text(call(handler, "list_versions", "{}")).contains(label));

    // What the name of a file can't hold is replaced, and the label given still designates the version
    String otherLabel = "Before: walls 12\\\" thick / roof <new>? *draft*...";
    result = call(handler, "save_version", "{\"label\":\"" + otherLabel + "\"}");
    text = text(result);
    assertEquals(text, Boolean.FALSE, result.get("isError"));
    assertTrue(text, text.contains("were replaced"));
    versions = HomeVersions.getVersions(savedHome);
    assertEquals(2, versions.size());
    File other = HomeVersions.find(savedHome, "Before: walls 12\" thick / roof <new>? *draft*...");
    assertNotNull(other);
    String otherName = other.getName();
    for (char c : "<>:\"/\\|?*".toCharArray()) {
      assertTrue(otherName, otherName.indexOf(c) < 0);
    }
    assertFalse("No dot nor space at the end of the label: " + otherName, otherName.endsWith("..sh3d") || otherName.endsWith(" .sh3d"));
    assertTrue(other.isFile());

    // A label longer than the name of a file may be is shortened, and the answer says so
    StringBuilder longLabel = new StringBuilder("Long");
    for (int i = 0; longLabel.length() < 400; i++) {
      longLabel.append(", step ").append(i);
    }
    result = call(handler, "save_version", "{\"label\":\"" + longLabel + "\"}");
    text = text(result);
    assertEquals(text, Boolean.FALSE, result.get("isError"));
    assertTrue(text, text.contains("shortened"));
    File longVersion = HomeVersions.find(savedHome, longLabel.toString());
    assertNotNull("Found by the label that was given", longVersion);
    assertTrue(longVersion.isFile());
    String kept = HomeVersions.getLabelText(longVersion);
    assertTrue(kept, kept.length() >= 40 && kept.length() <= 150);
    assertTrue(longLabel.toString().startsWith(kept));
    assertTrue(longVersion.getPath().length() < 260);
    assertEquals(3, HomeVersions.getVersions(savedHome).size());
    // The other tools find the versions by their labels
    result = call(handler, "compare_versions", "{\"version\":\"W176 finish band\"}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    result = call(handler, "restore_version", "{\"version\":\"" + label + "\",\"keep_current\":false}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> call(HomeMCPHandler handler, String name, String arguments) {
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}"));
    assertNotNull("A tool result: " + response, response.get("result"));
    return (Map<String, Object>)response.get("result");
  }

  @SuppressWarnings("unchecked")
  private static String text(Map<String, Object> toolResult) {
    StringBuilder text = new StringBuilder();
    for (Object content : (List<Object>)toolResult.get("content")) {
      if ("text".equals(((Map<String, Object>)content).get("type"))) {
        text.append(((Map<String, Object>)content).get("text")).append('\n');
      }
    }
    return text.toString();
  }
}
