/*
 * AssistantFenceCommandsTest.java 23 nov. 2026
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
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the styles of fences, their gates and the commands that change, join, cut and count them.
 */
public class AssistantFenceCommandsTest {
  private static final String [] STYLES = {"chain_link", "welded_wire", "hog_panel", "field_fence", "deer_fence",
      "garden_wire_tpost", "poultry_netting", "electric", "barbed_wire", "high_tensile", "cable_rail", "security_mesh",
      "picket", "privacy_solid", "board_on_board", "shadowbox", "horizontal_slat", "louvered", "basketweave", "lattice_top",
      "lattice_panel", "split_rail", "post_and_rail", "post_and_board", "crossbuck", "zigzag_rail", "wattle", "bamboo",
      "reed", "log_palisade", "snow_fence", "ornamental_steel", "aluminum_ornamental", "pool_code", "corrugated_metal",
      "steel_panel", "perforated_metal", "gabion", "vinyl_privacy", "vinyl_picket", "vinyl_rail", "vinyl_semi_privacy",
      "composite_privacy", "composite_slat", "glass_panel", "brick_wall", "stone_wall", "stucco_wall", "cmu_wall",
      "concrete_panel", "pier_and_panel", "retaining_wall", "espalier", "living_willow", "temporary_construction",
      "silt_fence", "orange_safety_mesh", "dog_run_panel", "pipe_fence", "guardrail", "ha_ha", "bollards"};
  private static final String [] ALIASES = {"stockade", "wrought_iron", "cattle_panel", "woven_wire", "hexagonal",
      "358_anti_climb", "ranch_rail", "x_rail", "worm_fence", "woven_branch", "brush", "heather_screen", "laser_cut",
      "dry_stack", "split_face_block", "precast", "paddock_vinyl_rail", "panel", "rail", "wire"};
  private static final String [] GATES = {"single_swing", "double_swing", "sliding", "rolling", "cantilever", "bifold",
      "vertical_lift", "arm_barrier", "pedestrian_turnstile", "arched_top", "drive_arch", "arbor_gate",
      "walk_through_kissing_gate", "cattle_guard"};

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

  private List<HomePieceOfFurniture> getFences() {
    List<HomePieceOfFurniture> fences = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantFence") != null) {
        fences.add(piece);
      }
    }
    return fences;
  }

  @Test
  public void testEveryStyleBuilds() {
    // On a slope, with a corner
    run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,-100],[3000,-1000,300],[3000,2000,300],[-1000,2000,-100]]}");
    for (String style : STYLES) {
      String summary = run("{\"action\":\"add_fence\",\"name\":\"" + style + "\",\"style\":\"" + style
          + "\",\"points\":[[0,0],[900,0],[900,500]]}");
      HomePieceOfFurniture fence = getPiece(style);
      assertNotNull(style + ": " + summary, fence);
      assertTrue(style + ": " + summary, summary.contains("(" + style + ", ")
          && (summary.contains("14 m long") || style.equals("zigzag_rail")));
      assertFalse(style + ": " + summary, summary.contains("Warning"));
      assertTrue(style + ": " + fence.getWidth() + " x " + fence.getDepth(), fence.getWidth() > 880 && fence.getDepth() > 480);
      assertEquals("site", fence.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY));
    }
    assertEquals(STYLES.length, getFences().size());
    for (String alias : ALIASES) {
      String summary = run("{\"action\":\"add_fence\",\"name\":\"alias\",\"style\":\"" + alias + "\",\"points\":[[0,800],[600,800]]}");
      assertTrue(alias + ": " + summary, summary.contains("Added the fence"));
    }
    // A hedge is planted by add_hedge
    String summary = run("{\"action\":\"add_fence\",\"style\":\"hedge\",\"points\":[[0,1200],[600,1200]]}");
    assertTrue(summary, summary.contains("Added the hedge"));
    // The list of styles names them all
    summary = run("{\"action\":\"list_fence_styles\"}");
    for (String style : STYLES) {
      assertTrue(style, summary.contains(style + " ("));
    }
  }

  @Test
  public void testOptionsAndUnknownKeys() {
    String summary = run("{\"action\":\"add_fence\",\"style\":\"picket\",\"points\":[[0,0],[720,0]],\"height\":100,"
        + "\"top\":\"gothic\",\"scallop\":\"concave\",\"gap\":4,\"picket_width\":8,"
        + "\"post\":{\"spacing\":180,\"size\":10,\"shape\":\"round\",\"cap\":\"ball\",\"set_in\":\"concrete\",\"embed_depth\":60},"
        + "\"rails\":{\"count\":3},\"color\":\"#FFFFFF\",\"post_color\":\"#333333\",\"rail_color\":\"#888888\",\"side\":\"right\","
        + "\"kickboard\":15,\"owner\":\"1230 Fernwood\",\"ownership_side\":\"north\",\"tags\":[\"front\"]}");
    assertTrue(summary, summary.contains("5 posts") && summary.contains("4 spans") && summary.contains("Owner: 1230 Fernwood"));
    assertFalse(summary, summary.contains("Warning"));
    HomePieceOfFurniture fence = getFences().get(0);
    assertTrue(fence.getProperty("tags"), fence.getProperty("tags").contains("front") && fence.getProperty("tags").contains("fence"));
    // A key that the style doesn't read is told
    summary = run("{\"action\":\"add_fence\",\"style\":\"chain_link\",\"points\":[[0,300],[720,300]],\"scallop\":\"concave\",\"colour\":\"#FF0000\"}");
    assertTrue(summary, summary.contains("Warning") && summary.contains("scallop") && summary.contains("colour"));
    summary = run("{\"action\":\"add_fence\",\"style\":\"chain_link\",\"points\":[[0,400],[720,400]],\"finish\":\"black_vinyl\","
        + "\"privacy_slats\":\"vertical\",\"barbed_top\":3,\"bottom_rail\":true}");
    assertFalse(summary, summary.contains("Warning"));
    // A height at each point, stepped panels and a flat top
    run("{\"action\":\"set_terrain\",\"points\":[[-1000,-1000,-100],[3000,-1000,300],[3000,2000,300],[-1000,2000,-100]]}");
    summary = run("{\"action\":\"add_fence\",\"name\":\"Profile\",\"style\":\"privacy\",\"points\":[[0,600],[600,600],[1200,600]],"
        + "\"height_profile\":[120,180,120],\"follow_ground\":\"step\",\"max_step\":10,\"fill_gaps\":\"kickboard\"}");
    assertTrue(summary, summary.contains("steps down by") && summary.contains("max_step"));
    summary = run("{\"action\":\"add_fence\",\"name\":\"Flat\",\"style\":\"privacy\",\"points\":[[0,700],[1000,700]],\"height\":180,"
        + "\"follow_ground\":\"level\"}");
    HomePieceOfFurniture flat = getPiece("Flat");
    // Its top is level: 180 cm above the ground of its high end, at 100 cm
    assertEquals(100 + 180 + 6 + 2, flat.getElevation() + flat.getHeight(), 1.5f);
    summary = run("{\"action\":\"add_fence\",\"points\":[[0,0],[300,0]],\"follow_ground\":\"float\"}");
    assertTrue(summary, summary.contains("rake, step or level"));
  }

  @Test
  public void testGates() {
    String summary = run("{\"action\":\"add_fence\",\"name\":\"Yard\",\"style\":\"picket\",\"points\":[[0,0],[2000,0]]}");
    HomePieceOfFurniture fence = getPiece("Yard");
    String id = HomeItemIds.getId(fence);
    int at = 150;
    for (String type : GATES) {
      summary = run("{\"action\":\"add_gate\",\"fence\":\"" + id + "\",\"at\":" + at + ",\"width\":120,\"type\":\"" + type + "\"}");
      assertTrue(type + ": " + summary, summary.contains("Added a gate") && summary.contains(type + " 120 cm wide at " + at));
      at += 130;
    }
    assertEquals(1, getFences().size());
    assertTrue(summary, summary.contains("14 ") && summary.contains("swings in"));
    // The gates are counted along the fence
    summary = run("{\"action\":\"schedule\",\"type\":\"gates\"}");
    assertTrue(summary, summary.contains("gate 14: cattle_guard") && summary.contains("gate 1: single_swing")
        && summary.contains("needs 120 cm free to open"));
    summary = run("{\"action\":\"modify_gate\",\"fence\":\"" + id + "\",\"gate\":1,\"type\":\"double_swing\",\"swing\":\"out\","
        + "\"open_angle\":60,\"latch\":\"lockable\",\"operator\":\"swing_arm\",\"self_closing\":true}");
    assertTrue(summary, summary.contains("1 double_swing 120 cm wide at 150 cm, swings out")
        && summary.contains("swing_arm operator") && summary.contains("lockable latch") && summary.contains("self-closing"));
    summary = run("{\"action\":\"remove_gate\",\"fence\":\"" + id + "\",\"gate\":14}");
    assertTrue(summary, summary.contains("Removed a gate") && !summary.contains("cattle_guard"));
    // A gate near a point, and two gates at the same place
    summary = run("{\"action\":\"add_gate\",\"fence\":\"" + id + "\",\"near\":[1950,40],\"width\":90}");
    assertTrue(summary, summary.contains("single_swing 90 cm wide at 1950 cm"));
    summary = run("{\"action\":\"add_gate\",\"fence\":\"" + id + "\",\"at\":1940,\"width\":90}");
    assertTrue(summary, summary.contains("two gates overlap"));
    summary = run("{\"action\":\"add_gate\",\"fence\":\"" + id + "\",\"at\":1800,\"width\":90,\"type\":\"portcullis\"}");
    assertTrue(summary, summary.contains("unknown gate \"type\""));
    // Undo gives back the fence without its last gate
    float width = fence.getWidth();
    this.homeController.undo();
    assertTrue(run("{\"action\":\"schedule\",\"type\":\"gates\"}"), !run("{\"action\":\"schedule\",\"type\":\"gates\"}").contains("at 1950 cm"));
    assertEquals(width, fence.getWidth(), 12);

    // A pool fence tells what a pool needs
    summary = run("{\"action\":\"add_fence\",\"name\":\"Pool\",\"style\":\"pool_code\",\"points\":[[0,500],[1000,500]],\"height\":110,"
        + "\"openings\":[{\"at\":500,\"width\":100}]}");
    assertTrue(summary, summary.contains("122 cm at least") && summary.contains("doesn't close by itself")
        && summary.contains("opens to the pool") && summary.contains("magna_latch_pool"));
    summary = run("{\"action\":\"add_fence\",\"name\":\"Pool 2\",\"style\":\"pool_code\",\"points\":[[0,600],[1000,600]],"
        + "\"openings\":[{\"at\":500,\"width\":100,\"swing\":\"out\",\"self_closing\":true,\"latch\":\"magna_latch_pool\"}]}");
    assertFalse(summary, summary.contains("Pool code"));
  }

  @Test
  public void testPlacement() {
    run("{\"action\":\"add_rectangular_room\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400}");
    String summary = run("{\"action\":\"add_property_line\",\"points\":[[-1000,-1000],[2000,-1000],[2000,1500],[-1000,1500]]}");
    // Along the lot line, 50 cm inside, with the driveway left open
    summary = run("{\"action\":\"add_fence\",\"name\":\"Lot\",\"style\":\"chain_link\",\"along\":\"lot_line\",\"offset\":50,"
        + "\"skip\":[[1000,1400]]}");
    HomePieceOfFurniture lot = getPiece("Lot");
    assertNotNull(summary, lot);
    // 2900 x 2400 cm around, less the 4 m of the driveway
    assertTrue(summary, summary.contains("closed") && summary.contains("102 m long") && summary.contains("gap 400 cm wide at 1200 cm"));
    assertEquals(500, lot.getX(), 3);
    assertEquals(250, lot.getY(), 3);
    assertEquals(2900, lot.getWidth(), 10);
    // A part of it
    summary = run("{\"action\":\"add_fence\",\"name\":\"Side\",\"along\":\"lot_line\",\"from\":[2000,-500],\"to\":[2000,1000]}");
    assertTrue(summary, summary.contains("15 m long"));
    // Between the end of a wall and a point of the lot line
    HomePieceOfFurniture side = getPiece("Side");
    summary = run("{\"action\":\"add_fence\",\"name\":\"Link\",\"between\":[\"W1:start\",\"" + HomeItemIds.getId(side) + ":start\"]}");
    assertTrue(summary, summary.contains("Added the fence"));
    summary = run("{\"action\":\"add_fence\",\"between\":[\"W99:start\",[0,0]]}");
    assertTrue(summary, summary.contains("no item W99"));
    summary = run("{\"action\":\"add_fence\",\"style\":\"picket\"}");
    assertTrue(summary, summary.contains("give \"points\""));
  }

  @Test
  public void testChangeJoinSplitTrimOffsetExtend() {
    run("{\"action\":\"add_fence\",\"name\":\"A\",\"style\":\"picket\",\"points\":[[0,0],[600,0]],\"openings\":[{\"at\":300,\"width\":100}]}");
    run("{\"action\":\"add_fence\",\"name\":\"B\",\"style\":\"privacy\",\"points\":[[610,0],[610,600]]}");
    HomePieceOfFurniture fenceA = getPiece("A");
    String idA = HomeItemIds.getId(fenceA);
    String idB = HomeItemIds.getId(getPiece("B"));
    // A new style and a new height, the gate kept
    String summary = run("{\"action\":\"modify_fence\",\"id\":\"" + idA + "\",\"style\":\"shadowbox\",\"height\":180}");
    assertTrue(summary, summary.contains("(shadowbox, 180 cm high)") && summary.contains("single_swing 100 cm wide at 300 cm"));
    assertEquals(180 + 10 + 6 + 2, fenceA.getHeight(), 1);
    this.homeController.undo();
    assertEquals(120 + 10 + 6 + 2, fenceA.getHeight(), 1);
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + idA + "\"}");
    assertTrue(summary, summary.contains("picket") && summary.contains("Points: [[0"));

    // Joined at their ends
    summary = run("{\"action\":\"join_fences\",\"ids\":[\"" + idA + "\",\"" + idB + "\"]}");
    assertTrue(summary, summary.contains("Joined 2 fences") && summary.contains("11 m long"));
    assertEquals(1, getFences().size());
    assertNull(getPiece("B"));
    // Cut in two, each part with what it had
    summary = run("{\"action\":\"split_fence\",\"id\":\"" + idA + "\",\"at\":800}");
    assertTrue(summary, summary.contains("Cut the fence at 800 cm") && summary.contains("7 m long") && summary.contains("4 m long"));
    assertEquals(2, getFences().size());
    // Extended straight, then to a point
    summary = run("{\"action\":\"extend_fence\",\"id\":\"" + idA + "\",\"by\":200}");
    assertTrue(summary, summary.contains("Extended by 2 m"));
    summary = run("{\"action\":\"extend_fence\",\"id\":\"" + idA + "\",\"to\":[1200,400]}");
    assertTrue(summary, summary.contains("Extended by"));
    summary = run("{\"action\":\"extend_fence\",\"id\":\"" + idA + "\",\"to\":\"terrain_edge\"}");
    assertTrue(summary, summary.contains("the home has none"));

    // Moved sideways, and copied
    run("{\"action\":\"add_fence\",\"name\":\"C\",\"points\":[[0,2000],[1000,2000]]}");
    HomePieceOfFurniture fenceC = getPiece("C");
    String idC = HomeItemIds.getId(fenceC);
    float y = fenceC.getY();
    summary = run("{\"action\":\"offset_fence\",\"id\":\"" + idC + "\",\"distance\":100}");
    assertEquals(100, Math.abs(fenceC.getY() - y), 2);
    int count = getFences().size();
    run("{\"action\":\"offset_fence\",\"id\":\"" + idC + "\",\"distance\":50,\"copy\":true}");
    assertEquals(count + 1, getFences().size());
    // Trimmed to a polygon: the part inside is kept
    summary = run("{\"action\":\"trim_fence\",\"id\":\"" + idC + "\",\"polygon\":[[200,1500],[700,1500],[700,2500],[200,2500]]}");
    assertTrue(summary, summary.contains("Trimmed the fence") && summary.contains("5 m long"));
    summary = run("{\"action\":\"trim_fence\",\"id\":\"" + idC + "\",\"polygon\":[[200,1500],[700,1500],[700,2500],[200,2500]],\"keep\":\"outside\"}");
    assertTrue(summary, summary.contains("nothing of the fence is outside"));
    summary = run("{\"action\":\"modify_fence\",\"id\":\"W1\",\"height\":100}");
    assertTrue(summary, summary.contains("Couldn't run modify_fence"));
  }

  @Test
  public void testChecks() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"House\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400}");
    run("{\"action\":\"add_property_line\",\"points\":[[-1000,-1000],[2000,-1000],[2000,1500],[-1000,1500]]}");
    run("{\"action\":\"add_slab\",\"name\":\"Driveway\",\"points\":[[800,400],[1100,400],[1100,1500],[800,1500]],\"thickness\":10}");
    String report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("fence"));
    // Through the house, over the driveway and beyond the lot
    run("{\"action\":\"add_fence\",\"name\":\"Wrong\",\"points\":[[-500,200],[2300,200]]}");
    run("{\"action\":\"add_fence\",\"name\":\"Front\",\"points\":[[600,900],[1500,900]]}");
    report = run("{\"action\":\"check_home\"}");
    assertTrue(report, report.contains("\"Wrong\" crosses the wall") && report.contains("\"Wrong\" stands up to 300 cm beyond the property line")
        && report.contains("\"Front\" crosses the driveway"));
    // A gate over the driveway settles it
    HomePieceOfFurniture front = getPiece("Front");
    String id = HomeItemIds.getId(front);
    run("{\"action\":\"add_gate\",\"fence\":\"" + id + "\",\"at\":350,\"width\":320,\"type\":\"double_swing\"}");
    report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("\"Front\" crosses the driveway"));
    assertFalse(report, report.contains("The gate 1"));
    // Something where the gate swings
    run("{\"action\":\"create_furniture\",\"name\":\"Planter\",\"x\":870,\"y\":820,\"parts\":[{\"shape\":\"box\",\"width\":60,\"depth\":60,\"height\":50}]}");
    report = run("{\"action\":\"check_home\"}");
    assertTrue(report, report.contains("The gate 1 of the fence " + id) && report.contains("\"Planter\" when it swings in"));
    run("{\"action\":\"modify_gate\",\"fence\":\"" + id + "\",\"gate\":1,\"swing\":\"out\"}");
    report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("The gate 1"));
    // The ground raised after the fence was built
    run("{\"action\":\"set_terrain\",\"points\":[[-3000,-3000,50],[3000,-3000,50],[3000,3000,50],[-3000,3000,50]]}");
    report = run("{\"action\":\"check_home\"}");
    assertTrue(report, report.contains("The ground changed under the fence " + id) && report.contains("50 cm under it"));
    run("{\"action\":\"modify_fence\",\"id\":\"" + id + "\",\"rebuild\":true}");
    report = run("{\"action\":\"check_home\"}");
    assertFalse(report, report.contains("The ground changed under the fence " + id));
    // A pool fence too low
    run("{\"action\":\"add_fence\",\"name\":\"Pool\",\"style\":\"pool_code\",\"points\":[[1200,-500],[1800,-500]],\"height\":100}");
    report = run("{\"action\":\"check_home\"}");
    assertTrue(report, report.contains("The pool fence") && report.contains("122 cm at least"));
  }

  @Test
  public void testConvertAndReport() {
    // A row of pieces put one by one
    StringBuilder commands = new StringBuilder();
    for (int i = 0; i < 5; i++) {
      commands.append(i > 0 ? "," : "").append("{\"action\":\"create_furniture\",\"name\":\"Panel\",\"x\":").append(100 + i * 200)
          .append(",\"y\":300,\"parts\":[{\"shape\":\"box\",\"width\":200,\"depth\":5,\"height\":150}]}");
    }
    String summary = run(commands.toString());
    assertEquals(summary, 5, this.home.getFurniture().size());
    List<String> ids = new ArrayList<String>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      ids.add("\"" + HomeItemIds.getId(piece) + "\"");
    }
    summary = run("{\"action\":\"convert_to_fence\",\"ids\":" + ids + ",\"style\":\"chain_link\",\"owner\":\"Neighbor\"}");
    assertTrue(summary, summary.contains("Replaced 5 pieces by the fence") && summary.contains("(chain_link, 150 cm high)")
        && summary.contains("10 m long"));
    assertEquals(1, this.home.getFurniture().size());
    run("{\"action\":\"add_fence\",\"style\":\"privacy\",\"points\":[[0,800],[1220,800]],\"owner\":\"Me\","
        + "\"openings\":[{\"at\":600,\"width\":100}]}");
    summary = run("{\"action\":\"fence_report\"}");
    assertTrue(summary, summary.contains("By style: chain_link 10 m") && summary.contains("privacy_solid 11.2 m")
        && summary.contains("By owner: Neighbor 10 m") && summary.contains("Me 11.2 m") && summary.contains("bags of concrete")
        && summary.contains("Total: 21.2 m") && summary.contains("1 gates"));
    // A fence left out of the report
    run("{\"action\":\"add_fence\",\"points\":[[0,1200],[500,1200]],\"report\":false}");
    assertTrue(run("{\"action\":\"fence_report\"}").contains("Total: 21.2 m"));
  }
}
