/*
 * AssistantChecksAndIdsTest.java
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
import static org.junit.Assert.assertTrue;

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
import com.eteks.sweethome3d.viewcontroller.AssistantHelp;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the checks of duplicates and of pieces off the ground, the size of a piece in the plan,
 * the id <code>last</code> and the help about tools.
 */
public class AssistantChecksAndIdsTest {
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

  /**
   * Two pieces one above the other aren't duplicates.
   */
  @Test
  public void testDuplicatesNeedTheSameHeight() {
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":2000,\"y\":2000,\"elevation\":20}");
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":2000,\"y\":2000,\"elevation\":330}");
    String summary = run("{\"action\":\"find_duplicates\"}");
    assertTrue(summary, summary.contains("No piece repeats another one"));
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":2001,\"y\":2000,\"elevation\":22}");
    summary = run("{\"action\":\"find_duplicates\"}");
    assertTrue(summary, summary.contains("1 piece is repeated") && summary.contains("(1 extra pieces)"));
    // The same place of two levels isn't the same place
    run("{\"action\":\"delete\",\"ids\":[\"" + HomeItemIds.getId(this.home.getFurniture().get(2)) + "\"]}");
    run("{\"action\":\"add_level\",\"name\":\"Upper\",\"elevation\":300}");
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":2000,\"y\":2000,\"elevation\":20}");
    summary = run("{\"action\":\"find_duplicates\"}");
    assertTrue(summary, summary.contains("No piece repeats another one"));
  }

  /**
   * A piece within the outline of the walls isn't outdoors, with or without a room under it.
   */
  @Test
  public void testFloatingIgnoresPiecesWithinTheWalls() {
    run("{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":800,\"y2\":0},{\"action\":\"add_wall\",\"x1\":800,\"y1\":0,\"x2\":800,\"y2\":600},"
        + "{\"action\":\"add_wall\",\"x1\":800,\"y1\":600,\"x2\":0,\"y2\":600},{\"action\":\"add_wall\",\"x1\":0,\"y1\":600,\"x2\":0,\"y2\":0}");
    run("{\"action\":\"set_terrain\",\"points\":[[-2000,-2000,0],[3000,-2000,0],[3000,3000,0],[-2000,3000,0]]}");
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":400,\"y\":300,\"elevation\":376,\"name_override\":\"Balcony chair\"}");
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":2000,\"y\":2000,\"elevation\":376,\"on_terrain\":false,\"name_override\":\"Flying chair\"}");
    String summary = run("{\"action\":\"find_floating\"}");
    assertTrue(summary, summary.contains("1 piece outdoors isn't") && summary.contains("Flying chair"));
    assertFalse(summary, summary.contains("Balcony chair"));
  }

  /**
   * The height given to a piece is its height in the plan too, so its top is where it is told.
   */
  @Test
  public void testSizeInPlanFollowsSize() {
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":100,\"y\":100,\"width\":50,\"depth\":60,\"height\":150,\"elevation\":10}");
    HomePieceOfFurniture piece = this.home.getFurniture().get(0);
    assertEquals(150, piece.getHeight(), 0.01f);
    assertEquals(150, piece.getHeightInPlan(), 0.01f);
    assertEquals(50, piece.getWidthInPlan(), 0.01f);
    assertEquals(60, piece.getDepthInPlan(), 0.01f);
    run("{\"action\":\"resize\",\"id\":\"" + HomeItemIds.getId(piece) + "\",\"width\":80,\"depth\":90,\"height\":40}");
    assertEquals(40, piece.getHeightInPlan(), 0.01f);
    assertEquals(80, piece.getWidthInPlan(), 0.01f);
    assertEquals(90, piece.getDepthInPlan(), 0.01f);
    // The shape of the piece in the plan has its new size
    float [][] points = piece.getPoints();
    assertEquals(80, Math.hypot(points [1][0] - points [0][0], points [1][1] - points [0][1]), 0.01);
    this.homeController.undo();
    assertEquals(150, piece.getHeightInPlan(), 0.01f);
    assertEquals(50, piece.getWidthInPlan(), 0.01f);
  }

  /**
   * "last" is the id of the item the commands before it created.
   */
  @Test
  public void testLastIsTheItemJustCreated() {
    String summary = run("{\"action\":\"add_fence\",\"style\":\"picket\",\"points\":[[0,0],[1000,0]]},"
        + "{\"action\":\"add_gate\",\"fence\":\"last\",\"at\":500,\"width\":100},"
        + "{\"action\":\"extend_fence\",\"fence\":\"$last\",\"to\":[1000,600]}");
    assertFalse(summary, summary.contains("no item last") || summary.contains("failed"));
    assertEquals(1, this.home.getFurniture().size());
    HomePieceOfFurniture fence = this.home.getFurniture().get(0);
    assertTrue(fence.getProperty("assistantFence"), fence.getProperty("assistantFence").contains("openings"));
    assertTrue(fence.getProperty("assistantFence"), fence.getProperty("assistantFence").contains("600"));
    summary = run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":300,\"y\":300},"
        + "{\"action\":\"rename\",\"id\":\"last\",\"name\":\"The last chair\"}");
    assertTrue(summary, getPiece("The last chair") != null);
    // Nothing created before
    summary = run("{\"action\":\"rename\",\"id\":\"last\",\"name\":\"None\"}");
    assertTrue(summary, summary.contains("none created one"));
  }

  /**
   * A piece that replaces one standing on the ground outdoors stands outdoors too.
   */
  @Test
  public void testReplacedPieceStaysOutdoors() {
    run("{\"action\":\"set_terrain\",\"grid\":{\"x\":-1000,\"y\":-1000,\"width\":3000,\"depth\":3000,\"heights\":[[-50,-50],[-50,-50]]}}");
    run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":1500,\"y\":-500,\"width\":300,\"depth\":300,\"height\":900,"
        + "\"on_terrain\":true,\"tags\":[\"tree\"],\"name_override\":\"Tall one\"}");
    HomePieceOfFurniture piece = getPiece("Tall one");
    piece.setProperty("price", "12");
    String id = HomeItemIds.getId(piece);
    String summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("below_floor") || summary.contains("above_ceiling"));
    run("{\"action\":\"replace_furniture\",\"ids\":[\"" + id + "\"],\"name\":\"Table\",\"keep_size\":true}");
    HomePieceOfFurniture replaced = null;
    for (HomePieceOfFurniture other : this.home.getFurniture()) {
      if (id.equals(HomeItemIds.getId(other))) {
        replaced = other;
      }
    }
    assertTrue(replaced != null && replaced != piece);
    assertEquals("true", replaced.getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
    assertEquals("12", replaced.getProperty("price"));
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("below_floor") || summary.contains("above_ceiling"));
    // A generated tree replaced by a piece of the catalog
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":500,\"y\":500,\"height\":1500}");
    HomePieceOfFurniture tree = this.home.getFurniture().get(this.home.getFurniture().size() - 1);
    String treeId = HomeItemIds.getId(tree);
    run("{\"action\":\"replace_furniture\",\"ids\":[\"" + treeId + "\"],\"name\":\"Table\",\"keep_size\":true}");
    for (HomePieceOfFurniture other : this.home.getFurniture()) {
      if (treeId.equals(HomeItemIds.getId(other))) {
        assertEquals("true", other.getProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY));
        assertTrue(other.getProperty("assistantPlant") == null);
      }
    }
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("below_floor") || summary.contains("above_ceiling"));
    // What stands on the ground without having been told outdoors isn't under the floor either
    replaced.setProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY, null);
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("below_floor") || summary.contains("above_ceiling"));
  }

  /**
   * A gate of a closed fence opens in to what the fence encloses, whatever the way its points turn,
   * and a tree of the catalog stops it by its trunk only.
   */
  @Test
  public void testGateSwingsToTheInsideAndUnderTrees() {
    String [] pens = {"[[0,0],[1000,0],[1000,1000],[0,1000]]", "[[0,0],[0,1000],[1000,1000],[1000,0]]"};
    for (String pen : pens) {
      run("{\"action\":\"add_fence\",\"name\":\"Pen\",\"points\":" + pen + ",\"closed\":true,\"style\":\"chain_link\",\"height\":120},"
          + "{\"action\":\"add_gate\",\"fence\":\"last\",\"near\":[200,0],\"width\":100,\"type\":\"single_swing\",\"swing\":\"in\"}");
      String fence = HomeItemIds.getId(this.home.getFurniture().get(this.home.getFurniture().size() - 1));
      // A piece inside the pen, behind the gate
      run("{\"action\":\"add_furniture\",\"name\":\"Table\",\"x\":200,\"y\":70,\"width\":100,\"depth\":60,\"height\":70}");
      String table = HomeItemIds.getId(this.home.getFurniture().get(this.home.getFurniture().size() - 1));
      String summary = run("{\"action\":\"check_home\"}");
      assertTrue(pen + summary, summary.contains("gate_blocked") && summary.contains("swings in"));
      run("{\"action\":\"modify_gate\",\"fence\":\"" + fence + "\",\"gate\":1,\"swing\":\"out\"}");
      summary = run("{\"action\":\"check_home\"}");
      assertFalse(pen + summary, summary.contains("gate_blocked"));
      run("{\"action\":\"delete\",\"ids\":[\"" + table + "\"]}");
      // A tree of the catalog 4 m out of the gate, with leaves above it
      run("{\"action\":\"add_furniture\",\"name\":\"Chair\",\"x\":200,\"y\":-400,\"width\":800,\"depth\":800,\"height\":1400,"
          + "\"tags\":[\"tree\"]}");
      String tree = HomeItemIds.getId(this.home.getFurniture().get(this.home.getFurniture().size() - 1));
      summary = run("{\"action\":\"check_home\"}");
      assertFalse(pen + summary, summary.contains("gate_blocked"));
      // Its trunk where the leaf goes
      run("{\"action\":\"move\",\"id\":\"" + tree + "\",\"x\":200,\"y\":-60}");
      summary = run("{\"action\":\"check_home\"}");
      assertTrue(pen + summary, summary.contains("gate_blocked") && summary.contains("swings out"));
      run("{\"action\":\"delete\",\"ids\":[\"" + tree + "\",\"" + fence + "\"]}");
    }
    // An open fence opens in to its side
    run("{\"action\":\"add_fence\",\"points\":[[0,0],[1000,0]],\"style\":\"chain_link\",\"height\":120,\"side\":\"right\"},"
        + "{\"action\":\"add_gate\",\"fence\":\"last\",\"near\":[200,0],\"width\":100,\"type\":\"single_swing\",\"swing\":\"in\"}");
    run("{\"action\":\"add_furniture\",\"name\":\"Table\",\"x\":200,\"y\":70,\"width\":100,\"depth\":60,\"height\":70}");
    String summary = run("{\"action\":\"check_home\"}");
    assertTrue(summary, summary.contains("gate_blocked"));
  }

  /**
   * The help tells a tool from a command.
   */
  @Test
  public void testHelpAboutATool() {
    String help = AssistantHelp.getHelp("export_home", null, null);
    assertTrue(help, help.contains("is a tool of the MCP server") && !help.startsWith("No command"));
    help = AssistantHelp.getHelp("save_version", null, null);
    assertTrue(help, help.contains("is a tool of the MCP server"));
    assertTrue(AssistantHelp.getHelp("no_such_thing_at_all", null, null).startsWith("No command"));
  }
}
