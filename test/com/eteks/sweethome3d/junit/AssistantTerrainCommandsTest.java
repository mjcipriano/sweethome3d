/*
 * AssistantTerrainCommandsTest.java 23 nov. 2026
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
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;

/**
 * Tests the trees and the shrubs built from a species, and the commands that change them.
 */
public class AssistantTerrainCommandsTest {
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

  private List<HomePieceOfFurniture> getPlants() {
    List<HomePieceOfFurniture> plants = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantPlant") != null) {
        plants.add(piece);
      }
    }
    return plants;
  }

  private HomePieceOfFurniture getTerrain() {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantTerrain") != null) {
        return piece;
      }
    }
    return null;
  }

  /**
   * Returns the height of the ground at a point, read from the answer of spot_elevations.
   */
  private float getHeight(float x, float y) {
    String summary = run("{\"action\":\"spot_elevations\",\"points\":[[" + x + "," + y + "]],\"labels\":false}");
    String text = summary.substring(summary.indexOf(") ") + 2);
    int end = 0;
    while (end < text.length() && (Character.isDigit(text.charAt(end)) || text.charAt(end) == '.' || text.charAt(end) == '-')) {
      end++;
    }
    while (end > 0 && text.charAt(end - 1) == '.') {
      end--;
    }
    return Float.parseFloat(text.substring(0, end));
  }

  @Test
  public void testRasterAndFile() throws Exception {
    // A slope of 1 m over 40 m with a bump, as 81 x 81 heights every 50 cm
    StringBuilder heights = new StringBuilder();
    for (int row = 0; row < 81; row++) {
      for (int column = 0; column < 81; column++) {
        double bump = Math.hypot(column - 40, row - 40) < 6 ? 60 * Math.cos(Math.hypot(column - 40, row - 40) / 6 * Math.PI / 2) : 0;
        heights.append(heights.length() > 0 ? "," : "").append(Math.round(column * 100 / 80. + bump));
      }
    }
    String summary = run("{\"action\":\"set_terrain\",\"raster\":{\"x0\":0,\"y0\":0,\"cell\":50,\"cols\":81,\"rows\":81,\"heights\":["
        + heights + "]},\"max_error_cm\":3}");
    assertNotNull(summary, getTerrain());
    assertTrue(summary, summary.contains("Read from a raster of 81 x 81 heights, thinned to"));
    // Far fewer points than heights, kept where the bump is
    String spec = getTerrain().getProperty("assistantTerrain");
    int points = spec.split("\\],\\[").length;
    assertTrue(points + " points", points > 20 && points < 2500);
    assertEquals(4000, getTerrain().getWidth(), 1);
    assertEquals(25, getHeight(1000, 500), 4);
    assertEquals(75, getHeight(3000, 3500), 4);
    assertEquals(50 + 60, getHeight(2000, 2000), 4);
    // Pinned to a height at a place
    summary = run("{\"action\":\"shift_terrain\",\"pin\":{\"x\":1000,\"y\":500,\"height\":0}}");
    assertTrue(summary, summary.contains("Moved the ground by -25"));
    assertEquals(0, getHeight(1000, 500), 4);
    assertEquals(50, getHeight(3000, 3500), 4);

    // An Esri ASCII grid in meters: 3 x 3 heights every 10 m, its lower left corner at the plan point (0, 2000)
    java.io.File file = java.io.File.createTempFile("ground", ".asc");
    file.deleteOnExit();
    java.io.FileWriter writer = new java.io.FileWriter(file);
    writer.write("ncols 3\nnrows 3\nxllcorner 500000\nyllcorner 3700000\ncellsize 10\nNODATA_value -9999\n"
        + "3 3 3\n2 2 2\n1 1 1\n");
    writer.close();
    summary = run("{\"action\":\"set_terrain\",\"file\":\"" + file.getPath().replace("\\", "\\\\") + "\",\"x\":0,\"y\":2000}");
    assertTrue(summary, summary.contains("Read from " + file.getName()) && summary.contains("heights from 100 to 300"));
    // The north of the file is up the plan: 3 m at the top, 1 m at the bottom
    assertEquals(2000, getTerrain().getWidth(), 1);
    assertEquals(300, getHeight(1000, -450), 12);
    assertEquals(200, getHeight(1000, 500), 5);
    assertEquals(100, getHeight(1000, 1450), 12);
    // Lines of x y z in feet
    file = java.io.File.createTempFile("ground", ".xyz");
    file.deleteOnExit();
    writer = new java.io.FileWriter(file);
    writer.write("x y z\n0 0 10\n100 0 10\n100 100 20\n0 100 20\n50 50 15\n");
    writer.close();
    summary = run("{\"action\":\"set_terrain\",\"file\":\"" + file.getPath().replace("\\", "\\\\") + "\",\"units\":\"ft\"}");
    assertTrue(summary, summary.contains("5 points") && summary.contains("heights from 304.8 to 609.6"));
    assertEquals(3048, getTerrain().getWidth(), 1);
    summary = run("{\"action\":\"set_terrain\",\"file\":\"/tmp/nothing.tif\"}");
    assertTrue(summary, summary.contains("Couldn't run set_terrain"));
    file = java.io.File.createTempFile("ground", ".tif");
    file.deleteOnExit();
    summary = run("{\"action\":\"set_terrain\",\"file\":\"" + file.getPath().replace("\\", "\\\\") + "\"}");
    assertTrue(summary, summary.contains("gdal_translate"));
  }

  @Test
  public void testGrading() {
    // A slope of 2 m over 40 m
    run("{\"action\":\"set_terrain\",\"points\":[[0,0,0],[4000,0,200],[4000,4000,200],[0,4000,0]]}");
    String summary = run("{\"action\":\"cut_fill_report\"}");
    assertTrue(summary, summary.contains("wasn't graded"));
    // A pad of 10 x 10 m at the height of its middle: as much cut as filled
    summary = run("{\"action\":\"add_pad\",\"points\":[[1500,1500],[2500,1500],[2500,2500],[1500,2500]],\"blend_distance\":300}");
    assertTrue(summary, summary.contains("Added a pad of 100 m2 at the height") && summary.contains("m3 cut"));
    assertEquals(100, getHeight(1600, 2000), 3);
    assertEquals(100, getHeight(2400, 2000), 3);
    assertEquals(100, getHeight(2000, 1550), 3);
    // Away from it the ground is what it was
    assertEquals(50, getHeight(1000, 2000), 3);
    assertEquals(160, getHeight(3200, 2000), 3);
    summary = run("{\"action\":\"cut_fill_report\"}");
    assertTrue(summary, summary.contains("m3 cut") && summary.contains("cu yd"));
    // About 100 m2 x 25 cm / 2 on each side, plus the banks
    float cut = Float.parseFloat(summary.substring(summary.indexOf(": ") + 2, summary.indexOf(" m3 cut")));
    assertTrue(summary, cut > 10 && cut < 30);
    // One step of undo gives the slope back
    this.homeController.undo();
    assertEquals(80, getHeight(1600, 2000), 2);
    this.homeController.redo();
    assertEquals(100, getHeight(1600, 2000), 3);

    // A sloped pad, a berm and a swale
    summary = run("{\"action\":\"add_pad\",\"points\":[[200,3000],[1000,3000],[1000,3800],[200,3800]],\"height\":50,"
        + "\"slope\":{\"percent\":5,\"dir\":90},\"blend_distance\":100}");
    assertEquals(50, getHeight(600, 3400), 2);
    assertEquals(50 - 15, getHeight(900, 3400), 3);
    float before = getHeight(3000, 500);
    summary = run("{\"action\":\"add_berm\",\"points\":[[3000,200],[3000,800]],\"height\":60,\"width\":300}");
    assertTrue(summary, summary.contains("Raised a berm 60 cm high"));
    assertEquals(before + 60, getHeight(3000, 500), 3);
    assertEquals(getHeight(3300, 500), 165, 4);
    summary = run("{\"action\":\"add_swale\",\"points\":[[500,300],[1200,300]],\"depth\":30,\"width\":200}");
    assertEquals(42.5f - 30, getHeight(850, 300), 3);
    // A driveway graded between two heights
    summary = run("{\"action\":\"grade_path\",\"points\":[[3500,1200],[3500,3600]],\"width\":300,\"heights\":[175,150],\"cross_slope\":2}");
    assertTrue(summary, summary.contains("Graded 24 m of ground 300 cm wide") && summary.contains("cross slope of 2 %"));
    assertEquals(162.5f, getHeight(3500, 2400), 3);
    // Walking down the plan, the right side is the west one
    assertEquals(162.5f - 2.5f, getHeight(3375, 2400), 3);
    assertEquals(162.5f + 2.5f, getHeight(3625, 2400), 3);
    summary = run("{\"action\":\"flatten_area\",\"points\":[[200,200],[400,200],[400,400],[200,400]]}");
    assertTrue(summary, summary.contains("Flattened 4 m2"));
    summary = run("{\"action\":\"smooth_area\",\"points\":[[2800,100],[3200,100],[3200,900],[2800,900]],\"radius\":200}");
    assertTrue(summary, summary.contains("Smoothed 32 m2"));
    assertTrue(getHeight(3000, 500) < before + 55);
    // The ground stretched to what stands out of it
    run("{\"action\":\"add_furniture\",\"name\":\"Table\",\"x\":5500,\"y\":2000,\"on_terrain\":false}");
    summary = run("{\"action\":\"extend_terrain\",\"margin\":300}");
    assertTrue(summary, summary.contains("Extended the ground"));
    assertEquals(200, getHeight(5500, 2000), 5);
    summary = run("{\"action\":\"extend_terrain\",\"margin\":300}");
    assertTrue(summary, summary.contains("nothing to extend"));
    // A new ground starts the count of earth again
    run("{\"action\":\"set_terrain\",\"points\":[[0,0,0],[4000,0,200],[4000,4000,200],[0,4000,0]]}");
    assertTrue(run("{\"action\":\"cut_fill_report\"}").contains("wasn't graded"));
  }

  @Test
  public void testContoursSpotsAndSlopes() {
    String summary = run("{\"action\":\"contours\"}");
    assertTrue(summary, summary.contains("the home has no ground"));
    run("{\"action\":\"set_terrain\",\"points\":[[0,0,0],[4000,0,200],[4000,4000,200],[0,4000,0]]}");
    summary = run("{\"action\":\"contours\",\"interval\":50}");
    // Lines at 50, 100 and 150 cm, each one across the 40 m of the ground at x = 1000, 2000 and 3000
    assertTrue(summary, summary.contains("Drew 3 contour lines at 3 heights"));
    assertEquals(3, this.home.getPolylines().size());
    float [] expected = {1000, 2000, 3000};
    int index = 0;
    for (com.eteks.sweethome3d.model.Polyline polyline : this.home.getPolylines()) {
      for (float [] point : polyline.getPoints()) {
        assertEquals(expected [index], point [0], 2);
      }
      index++;
    }
    // Drawn again, they replace the ones before
    summary = run("{\"action\":\"contours\",\"interval\":25}");
    assertEquals(7, this.home.getPolylines().size());
    assertTrue(summary, summary.contains("in place of the ones before"));
    summary = run("{\"action\":\"contours\",\"remove\":true}");
    assertEquals(0, this.home.getPolylines().size());
    assertEquals(0, this.home.getLabels().size());

    summary = run("{\"action\":\"spot_elevations\",\"points\":[[1000,500],[3000,3500],[9000,0]]}");
    assertTrue(summary, summary.contains("(1000, 500) 50") && summary.contains("(3000, 3500) 150")
        && summary.contains("(9000, 0) out of the ground") && summary.contains("2 labels"));
    assertEquals(2, this.home.getLabels().size());

    // 2 m over 40 m is 5 %
    summary = run("{\"action\":\"slope_map\"}");
    assertTrue(summary, summary.contains("Slopes of 1600 m2 of ground") && summary.contains("100 % 5 to 8.33 %")
        && summary.contains("Steepest: 5 %"));
    run("{\"action\":\"add_berm\",\"points\":[[2000,1000],[2000,3000]],\"height\":150,\"width\":400}");
    summary = run("{\"action\":\"slope_map\",\"threshold\":40}");
    assertTrue(summary, summary.contains("over 25 %") && summary.contains("Places at 40 % or more"));
  }

  @Test
  public void testLargeGround() {
    // 40 000 points: more than the old limit
    StringBuilder rows = new StringBuilder();
    for (int row = 0; row < 200; row++) {
      rows.append(row > 0 ? "," : "").append('[');
      for (int column = 0; column < 200; column++) {
        rows.append(column > 0 ? "," : "").append(Math.round(100 * Math.sin(row / 20.) * Math.cos(column / 15.)) + (row * 7 + column * 3) % 4);
      }
      rows.append(']');
    }
    long start = System.currentTimeMillis();
    String summary = run("{\"action\":\"set_terrain\",\"grid\":{\"x\":0,\"y\":0,\"width\":20000,\"depth\":20000,\"heights\":[" + rows + "]}}");
    assertTrue(summary, summary.contains("40000 points"));
    assertTrue("Built in " + (System.currentTimeMillis() - start) + " ms", System.currentTimeMillis() - start < 60000);
    // Each height is found at its point
    for (int [] place : new int [][] {{10, 10}, {57, 123}, {199, 0}, {100, 100}, {3, 190}}) {
      float expected = Math.round(100 * Math.sin(place [1] / 20.) * Math.cos(place [0] / 15.)) + (place [1] * 7 + place [0] * 3) % 4;
      assertEquals(expected, getHeight(place [0] * 20000f / 199, place [1] * 20000f / 199), 1.5f);
    }
    summary = run("{\"action\":\"add_fence\",\"points\":[[500,500],[19000,18000]]}");
    assertTrue(summary, summary.contains("following the ground"));
  }
}
