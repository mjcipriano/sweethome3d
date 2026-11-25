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
    file = java.io.File.createTempFile("ground", ".laz");
    file.deleteOnExit();
    summary = run("{\"action\":\"set_terrain\",\"file\":\"" + file.getPath().replace("\\", "\\\\") + "\"}");
    assertTrue(summary, summary.contains("laszip"));
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
    // A new ground starts the count of earth again, once told to leave the grading of the old one
    summary = run("{\"action\":\"set_terrain\",\"points\":[[0,0,0],[4000,0,200],[4000,4000,200],[0,4000,0]],\"grading\":\"drop\"}");
    assertTrue(summary, summary.contains("recorded with the ground replaced were dropped"));
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
  public void testSiteReportExportAndCoordinates() throws Exception {
    run("{\"action\":\"add_property_line\",\"points\":[[0,0],[4000,0],[4000,3000],[0,3000]]}");
    run("{\"action\":\"add_rectangular_room\",\"name\":\"House\",\"x\":1000,\"y\":1000,\"width\":1000,\"depth\":800}");
    run("{\"action\":\"add_slab\",\"name\":\"Driveway\",\"points\":[[2200,1000],[2600,1000],[2600,3000],[2200,3000]],\"thickness\":10}");
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":500,\"y\":500,\"height\":1500,\"crown_radius\":400},"
        + "{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":3500,\"y\":500,\"height\":1700,\"crown_radius\":400},"
        + "{\"action\":\"add_shrub\",\"species\":\"azalea\",\"x\":900,\"y\":900}");
    run("{\"action\":\"add_fence\",\"style\":\"privacy\",\"points\":[[0,0],[4000,0]],\"owner\":\"Me\"}");
    String summary = run("{\"action\":\"site_report\"}");
    assertTrue(summary, summary.contains("Lot: 1200 m2 (0.3 acres)") && summary.contains("driveways 80 m2")
        && summary.contains("roofs or rooms 80 m2") && summary.contains("Generated trees: 2")
        && summary.contains("2 white_oak (16 m high on average)") && summary.contains("Generated shrubs: 1")
        && summary.contains("privacy_solid 40 m") && summary.contains("Ground: flat"));
    // About 100 m2 of crowns over 1200 m2
    assertTrue(summary, summary.contains("crowns covering 100.53 m2 in all") && summary.contains("2 of them stand on the lot")
        && summary.contains("m2 of the lot (8.37 % of the lot)"));

    // Coordinates both ways
    summary = run("{\"action\":\"geo_to_plan\",\"points\":[[33.749,-84.388]]}");
    assertTrue(summary, summary.contains("no georeference"));
    summary = run("{\"action\":\"set_georeference\",\"x\":0,\"y\":0,\"lat\":33.749,\"lon\":-84.388}");
    summary = run("{\"action\":\"geo_to_plan\",\"points\":[[33.749,-84.388],{\"lat\":33.7499,\"lon\":-84.388}]}");
    // 0.0009 degree of latitude is about 100 m to the north, up the plan
    assertTrue(summary, summary.contains("(33.7490000, -84.3880000) -> (0, 0)"));
    String list = summary.substring(summary.indexOf("As a list: [[") + 13);
    float y = Float.parseFloat(list.substring(list.indexOf("],[") + 3).split(",") [1].replace("]", "").replace(".", "x").split("x") [0]);
    assertEquals(-9980, y, 60);
    summary = run("{\"action\":\"geo_to_plan\",\"points\":[[-84.388,33.749]]}");
    assertTrue(summary, summary.contains("km from the home") && summary.contains("[latitude, longitude]"));
    // Three points of the plan and their places fit a georeference, and the answer tells the fit
    summary = run("{\"action\":\"set_georeference\",\"points\":[{\"lat\":33.749,\"lon\":-84.388,\"x\":0,\"y\":0},"
        + "{\"lat\":33.7499,\"lon\":-84.388,\"x\":0,\"y\":-9980},{\"lat\":33.749,\"lon\":-84.38692,\"x\":10000,\"y\":0}]}");
    assertTrue(summary, summary.contains("Fitted 3 points") && summary.contains("cm off on average"));

    // The site written for a GIS
    java.io.File file = java.io.File.createTempFile("site", ".geojson");
    file.delete();
    file.deleteOnExit();
    summary = run("{\"action\":\"export_site\",\"path\":\"" + file.getPath().replace("\\", "\\\\") + "\"}");
    assertTrue(summary, summary.contains("Wrote the site") && summary.contains("lot_line 1") && summary.contains("fence 1")
        && summary.contains("tree 2") && summary.contains("shrub 1"));
    String geojson = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
    assertTrue(geojson, geojson.startsWith("{\"type\":\"FeatureCollection\"") && geojson.contains("\"species\":\"white_oak\"")
        && geojson.contains("\"style\":\"privacy\"") && geojson.contains("\"owner\":\"Me\"") && geojson.contains("\"kind\":\"lot_line\"")
        && geojson.contains("[-84.38"));
    summary = run("{\"action\":\"export_site\",\"path\":\"" + file.getPath().replace("\\", "\\\\") + "\"}");
    assertTrue(summary, summary.contains("exists"));
  }

  /**
   * Returns a GeoTIFF of 32 bit floats, 2 m by pixel, its top left corner at (500000, 3700000 + 2 x rows).
   * @param compressed <code>true</code> for Deflate with the predictor of floats
   */
  private static byte [] createGeoTiff(int columns, int rows, float [] heights, boolean compressed) throws Exception {
    byte [] samples = new byte [columns * rows * 4];
    java.nio.ByteBuffer.wrap(samples).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(heights);
    if (compressed) {
      // The bytes of each row, the most significant ones first, then their differences
      byte [] predicted = new byte [samples.length];
      for (int row = 0; row < rows; row++) {
        int start = row * columns * 4;
        for (int column = 0; column < columns; column++) {
          for (int b = 0; b < 4; b++) {
            predicted [start + b * columns + column] = samples [start + column * 4 + 3 - b];
          }
        }
        for (int i = columns * 4 - 1; i >= 1; i--) {
          predicted [start + i] -= predicted [start + i - 1];
        }
      }
      java.util.zip.Deflater deflater = new java.util.zip.Deflater();
      deflater.setInput(predicted);
      deflater.finish();
      byte [] buffer = new byte [samples.length + 64];
      samples = java.util.Arrays.copyOf(buffer, deflater.deflate(buffer));
      deflater.end();
    }
    int entries = 12;
    int directoryLength = 2 + entries * 12 + 4;
    java.nio.ByteBuffer tiff = java.nio.ByteBuffer.allocate(8 + directoryLength + 24 + 48 + samples.length)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    tiff.put((byte)'I').put((byte)'I').putShort((short)42).putInt(8);
    int scaleOffset = 8 + directoryLength;
    int tieOffset = scaleOffset + 24;
    int dataOffset = tieOffset + 48;
    tiff.putShort((short)entries);
    int [][] tags = {{256, 4, 1, columns}, {257, 4, 1, rows}, {258, 3, 1, 32}, {259, 3, 1, compressed ? 8 : 1}, {273, 4, 1, dataOffset},
                     {277, 3, 1, 1}, {278, 4, 1, rows}, {279, 4, 1, samples.length}, {317, 3, 1, compressed ? 3 : 1}, {339, 3, 1, 3},
                     {33550, 12, 3, scaleOffset}, {33922, 12, 6, tieOffset}};
    for (int [] tag : tags) {
      tiff.putShort((short)tag [0]).putShort((short)tag [1]).putInt(tag [2]);
      if (tag [1] == 3) {
        tiff.putShort((short)tag [3]).putShort((short)0);
      } else {
        tiff.putInt(tag [3]);
      }
    }
    tiff.putInt(0);
    tiff.putDouble(2).putDouble(2).putDouble(0);
    tiff.putDouble(0).putDouble(0).putDouble(0).putDouble(500000).putDouble(3700000 + 2 * rows).putDouble(0);
    tiff.put(samples);
    return tiff.array();
  }

  @Test
  public void testGeoTiffAndLas() throws Exception {
    // 20 x 10 heights in meters: 1 m more by column
    float [] heights = new float [200];
    for (int i = 0; i < heights.length; i++) {
      heights [i] = i % 20 + (i / 20) * 0.5f;
    }
    for (boolean compressed : new boolean [] {false, true}) {
      java.io.File file = java.io.File.createTempFile("dem", ".tif");
      file.deleteOnExit();
      java.nio.file.Files.write(file.toPath(), createGeoTiff(20, 10, heights, compressed));
      String summary = run("{\"action\":\"set_terrain\",\"file\":\"" + file.getPath().replace("\\", "\\\\") + "\",\"x\":0,\"y\":2000,"
          + "\"max_error_cm\":1}");
      assertTrue(compressed + ": " + summary, summary.contains("Read from " + file.getName()) && summary.contains("heights from 0 to 2350"));
      // 40 x 20 m, the first height in the middle of its pixel at the top left
      assertEquals(3800, getTerrain().getWidth(), 1);
      assertEquals(0, getHeight(100, 100), 2);
      assertEquals(1900, getHeight(3900, 100), 2);
      assertEquals(450, getHeight(100, 1900), 2);
    }

    // A point cloud: a ground that rises to the east, and a tree 12 m high at (20, 30) m
    java.util.List<double []> points = new java.util.ArrayList<double []>();
    for (int y = 0; y <= 60; y += 2) {
      for (int x = 0; x <= 80; x += 2) {
        points.add(new double [] {x, y, x * 0.05, 2});
      }
    }
    for (int i = 0; i < 300; i++) {
      double angle = i * 0.7;
      double radius = (i % 10) * 0.35;
      double height = 12 - radius * radius * 0.4;
      points.add(new double [] {20 + Math.cos(angle) * radius, 30 + Math.sin(angle) * radius, 1 + height * (0.6 + 0.4 * ((i * 7) % 10) / 10.), 5});
      points.add(new double [] {20 + Math.cos(angle) * radius, 30 + Math.sin(angle) * radius, 1 + height, 5});
    }
    java.nio.ByteBuffer las = java.nio.ByteBuffer.allocate(227 + points.size() * 20).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    las.put((byte)'L').put((byte)'A').put((byte)'S').put((byte)'F');
    las.put(24, (byte)1).put(25, (byte)2);
    las.putShort(94, (short)227);
    las.putInt(96, 227);
    las.put(104, (byte)0);
    las.putShort(105, (short)20);
    las.putInt(107, points.size());
    las.putDouble(131, 0.01).putDouble(139, 0.01).putDouble(147, 0.01);
    las.putDouble(155, 500000).putDouble(163, 3700000).putDouble(171, 300);
    for (int i = 0; i < points.size(); i++) {
      int at = 227 + i * 20;
      las.putInt(at, (int)Math.round(points.get(i) [0] * 100));
      las.putInt(at + 4, (int)Math.round(points.get(i) [1] * 100));
      las.putInt(at + 8, (int)Math.round(points.get(i) [2] * 100));
      las.put(at + 15, (byte)points.get(i) [3]);
    }
    java.io.File file = java.io.File.createTempFile("cloud", ".las");
    file.deleteOnExit();
    java.nio.file.Files.write(file.toPath(), las.array());
    String path = file.getPath().replace("\\", "\\\\");
    // Its ground, without the tree
    String summary = run("{\"action\":\"set_terrain\",\"file\":\"" + path + "\",\"x\":0,\"y\":6000,\"pin\":{\"x\":100,\"y\":3000,\"height\":0}}");
    assertTrue(summary, summary.contains("Read from " + file.getName()));
    assertEquals(8000, getTerrain().getWidth(), 250);
    assertEquals(0, getHeight(100, 3000), 12);
    assertEquals(200, getHeight(4000, 3000), 25);
    // The ground under the tree isn't raised by it
    assertEquals(100, getHeight(2000, 3000), 25);
    // Its tree, 12 m high, at 20 m to the east and 30 m to the north of the corner of the file
    summary = run("{\"action\":\"trees_from_lidar\",\"file\":\"" + path + "\",\"x\":0,\"y\":6000}");
    assertTrue(summary, summary.contains("Found 1 treetops") && summary.contains("planted them with the tag \"lidar\""));
    HomePieceOfFurniture tree = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantPlant") != null) {
        tree = piece;
      }
    }
    assertNotNull(summary, tree);
    assertEquals(2000, tree.getX(), 120);
    assertEquals(3000, tree.getY(), 120);
    assertEquals(1200, tree.getHeight(), 120);
    assertTrue(tree.getProperty("tags"), tree.getProperty("tags").contains("lidar"));
    java.io.File laz = java.io.File.createTempFile("cloud", ".laz");
    laz.deleteOnExit();
    summary = run("{\"action\":\"trees_from_lidar\",\"file\":\"" + laz.getPath().replace("\\", "\\\\") + "\"}");
    assertTrue(summary, summary.contains("laszip"));
  }

  private float getBottom(HomePieceOfFurniture piece) {
    return piece.getElevation() + (piece.getLevel() != null ? piece.getLevel().getElevation() : 0);
  }

  private static String flatGround(int height) {
    return "[0,0," + height + "],[2000,0," + height + "],[4000,0," + height + "],[0,2000," + height + "],[2000,2000," + height
        + "],[4000,2000," + height + "],[0,4000," + height + "],[2000,4000," + height + "],[4000,4000," + height + "]";
  }

  /**
   * A ground that replaces another one keeps its areas, and what stood on the old one follows the new one.
   */
  @Test
  public void testGroundReplacedKeepsAreasAndReseats() {
    run("{\"action\":\"set_terrain\",\"points\":[" + flatGround(0) + "],\"areas\":[{\"name\":\"Driveway\",\"color\":\"#808080\","
        + "\"points\":[[100,100],[900,100],[900,600],[100,600]]}]}");
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":1000,\"y\":1000,\"height\":900},"
        + "{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":3000,\"y\":3000,\"height\":900,\"offset\":20},"
        + "{\"action\":\"add_fence\",\"style\":\"privacy\",\"points\":[[500,3500],[3500,3500]]},"
        + "{\"action\":\"add_shed\",\"x\":3000,\"y\":1000}");
    List<HomePieceOfFurniture> plants = getPlants();
    HomePieceOfFurniture fence = getPiece("Fence");
    HomePieceOfFurniture shed = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if ("shed".equals(piece.getProperty("assistantGarden"))) {
        shed = piece;
      }
    }
    assertNotNull(shed);
    assertEquals(0, getBottom(plants.get(0)), 1);
    assertEquals(20, getBottom(plants.get(1)), 1);
    float fenceBottom = getBottom(fence);
    float shedBottom = getBottom(shed);

    // The whole ground 100 cm higher: everything follows, and the driveway keeps its finish
    String summary = run("{\"action\":\"set_terrain\",\"points\":[" + flatGround(100) + "]}");
    assertTrue(summary, summary.contains("The 1 area of the ground replaced was kept"));
    assertTrue(summary, summary.contains("Put back on the ground: 3 plants and pieces, and 1 fence built again"));
    assertTrue(getTerrain().getProperty("assistantTerrainParameters").contains("Driveway"));
    plants = getPlants();
    assertEquals(100, getBottom(plants.get(0)), 1);
    assertEquals(120, getBottom(plants.get(1)), 1);
    assertEquals(fenceBottom + 100, getBottom(getPiece("Fence")), 2);
    assertEquals(shedBottom + 100, getBottom(shed), 2);
    // Only the tree planted 20 cm above the ground is above it
    summary = run("{\"action\":\"find_floating\"}");
    assertTrue(summary, summary.contains("1 piece outdoors isn't on the ground") && summary.contains("20 cm above the ground"));

    // A pad under the first tree only
    summary = run("{\"action\":\"add_pad\",\"points\":[[700,700],[1300,700],[1300,1300],[700,1300]],\"height\":160,\"blend_distance\":100}");
    assertTrue(summary, summary.contains("Put back on the ground: 1 plant or piece"));
    assertEquals(160, getBottom(getPlants().get(0)), 2);
    assertEquals(120, getBottom(getPlants().get(1)), 1);
    // Left where it is when asked, then put back by reseat_all
    summary = run("{\"action\":\"add_pad\",\"points\":[[2700,2700],[3300,2700],[3300,3300],[2700,3300]],\"height\":40,"
        + "\"blend_distance\":100,\"reseat\":false}");
    assertTrue(summary, summary.contains("keep their heights") && summary.contains("reseat_all"));
    assertEquals(120, getBottom(getPlants().get(1)), 1);
    summary = run("{\"action\":\"reseat_all\"}");
    assertTrue(summary, summary.contains("Put back on the ground: 1 plant or piece, and 1 fence built again"));
    assertEquals(60, getBottom(getPlants().get(1)), 2);
    summary = run("{\"action\":\"reseat_all\"}");
    // Every fence is built again, and nothing else has to move
    assertTrue(summary, summary.contains("Put back on the ground: 1 fence built again") && !summary.contains("plant"));

    // No area asked for: none kept
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + flatGround(100) + "],\"areas\":[]}");
    assertFalse(summary, summary.contains("kept with their finish"));
    assertFalse(getTerrain().getProperty("assistantTerrainParameters").contains("Driveway"));
    // The whole ground moved, by the parameter its help shows or by dz
    summary = run("{\"action\":\"shift_terrain\"}");
    assertTrue(summary, summary.contains("\"offset\":N"));
    summary = run("{\"action\":\"shift_terrain\",\"dz\":-30}");
    assertTrue(summary, summary.contains("Moved the ground by -30 cm"));
    assertEquals(70, getHeight(2000, 2000), 1);
    assertTrue(HomeAssistantContext.getCommandReference().contains("{\"action\":\"shift_terrain\",\"offset\":N}"));
  }

  private static float getGroundArea(String slopeMap) {
    return Float.parseFloat(slopeMap.substring(slopeMap.indexOf("Slopes of ") + 10, slopeMap.indexOf(" m2 of ground")));
  }

  /**
   * The long triangles that close the ragged edge of scattered points are left out, and smoothing adds no ground.
   */
  @Test
  public void testEdgeTrimClipAndResample() throws Exception {
    // 25 points every 5 m, and a point far away that the triangles would join with a steep sliver
    StringBuilder points = new StringBuilder();
    StringBuilder xyz = new StringBuilder();
    for (int y = 0; y <= 2000; y += 500) {
      for (int x = 0; x <= 2000; x += 500) {
        points.append("[").append(x).append(",").append(y).append(",").append(x / 50).append("],");
        xyz.append(x / 100.).append(" ").append((2000 - y) / 100.).append(" ").append(x / 5000.).append("\n");
      }
    }
    points.append("[10000,1000,3000]");
    xyz.append("100 10 30\n");
    String summary = run("{\"action\":\"set_terrain\",\"points\":[" + points + "]}");
    assertFalse(summary, summary.contains("left out"));
    summary = run("{\"action\":\"slope_map\"}");
    assertTrue(summary, getGroundArea(summary) > 1000);

    summary = run("{\"action\":\"set_terrain\",\"points\":[" + points + "],\"max_edge\":1000}");
    assertTrue(summary, summary.contains("long triangles of its edge left out"));
    summary = run("{\"action\":\"slope_map\"}");
    assertEquals(summary, 400, getGroundArea(summary), 0.5f);
    assertTrue(summary, summary.contains("Steepest: 2 %"));
    summary = run("{\"action\":\"spot_elevations\",\"points\":[[6000,1000]],\"labels\":false}");
    assertTrue(summary, summary.contains("out of the ground") || summary.contains("closest"));
    // Grading keeps the edge trimmed, and smoothing a polygon that goes out of the ground adds nothing to it
    summary = run("{\"action\":\"smooth_area\",\"points\":[[1500,500],[3500,500],[3500,1500],[1500,1500]],\"radius\":300}");
    assertTrue(summary, summary.contains("Smoothed"));
    summary = run("{\"action\":\"slope_map\"}");
    assertEquals(summary, 400, getGroundArea(summary), 0.5f);
    summary = run("{\"action\":\"smooth_area\",\"points\":[[5000,500],[6000,500],[6000,1500],[5000,1500]]}");
    assertTrue(summary, summary.contains("out of the ground"));

    // The same with the length found from the points, which is the default for the points of a file
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + points + "],\"max_edge\":\"auto\"}");
    assertTrue(summary, summary.contains("left out") && summary.contains("\"max_edge\":2500 cm"));
    java.io.File file = java.io.File.createTempFile("ground", ".xyz");
    file.deleteOnExit();
    java.nio.file.Files.write(file.toPath(), xyz.toString().getBytes("ISO-8859-1"));
    String path = file.getPath().replace("\\", "\\\\");
    summary = run("{\"action\":\"set_terrain\",\"file\":\"" + path + "\",\"x\":0,\"y\":2000}");
    assertTrue(summary, summary.contains("left out") && summary.contains("Read from"));
    summary = run("{\"action\":\"slope_map\"}");
    assertEquals(summary, 400, getGroundArea(summary), 0.5f);
    summary = run("{\"action\":\"set_terrain\",\"file\":\"" + path + "\",\"x\":0,\"y\":2000,\"max_edge\":0}");
    assertFalse(summary, summary.contains("left out"));

    // An outline the ground stays in
    // The area smoothed above is out of it: asked, the ground is rebuilt without that grading
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + points + "],\"clip\":[[0,0],[1000,0],[1000,2000],[0,2000]]}");
    assertTrue(summary, summary.contains("nothing was changed") && summary.contains("smooth_area"));
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + points + "],\"clip\":[[0,0],[1000,0],[1000,2000],[0,2000]],\"grading\":\"drop\"}");
    summary = run("{\"action\":\"slope_map\"}");
    assertEquals(summary, 200, getGroundArea(summary), 0.5f);
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + points + "],\"max_edge\":\"long\"}");
    assertTrue(summary, summary.contains("\"max_edge\" is a length"));

    // Points gridded before they are joined
    StringBuilder scattered = new StringBuilder();
    for (int i = 0; i < 400; i++) {
      scattered.append(i > 0 ? "," : "").append("[").append((i * 37) % 2000).append(",").append((i * 91) % 2000).append(",50]");
    }
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + scattered + "],\"resample_cm\":500}");
    assertTrue(summary, summary.contains("400 points were gridded every 500 cm: 16 points"));
    assertEquals(50, getHeight(1000, 1000), 0.5f);
    // An older home opens with the same ground: a ground without the property isn't trimmed
    assertNull(getTerrain().getProperty("assistantTerrainTrim"));
  }

  private static java.io.File createLas(java.util.List<double []> points) throws Exception {
    java.nio.ByteBuffer las = java.nio.ByteBuffer.allocate(227 + points.size() * 20).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    las.put((byte)'L').put((byte)'A').put((byte)'S').put((byte)'F');
    las.put(24, (byte)1).put(25, (byte)2);
    las.putShort(94, (short)227);
    las.putInt(96, 227);
    las.put(104, (byte)0);
    las.putShort(105, (short)20);
    las.putInt(107, points.size());
    las.putDouble(131, 0.01).putDouble(139, 0.01).putDouble(147, 0.01);
    las.putDouble(155, 500000).putDouble(163, 3700000).putDouble(171, 300);
    for (int i = 0; i < points.size(); i++) {
      int at = 227 + i * 20;
      las.putInt(at, (int)Math.round(points.get(i) [0] * 100));
      las.putInt(at + 4, (int)Math.round(points.get(i) [1] * 100));
      las.putInt(at + 8, (int)Math.round(points.get(i) [2] * 100));
      las.put(at + 15, (byte)points.get(i) [3]);
    }
    java.io.File file = java.io.File.createTempFile("cloud", ".las");
    file.deleteOnExit();
    java.nio.file.Files.write(file.toPath(), las.array());
    return file;
  }

  private static void addCrown(java.util.List<double []> points, double x, double y, double top, int pointClass) {
    for (int i = 0; i < 300; i++) {
      double angle = i * 0.7;
      double radius = (i % 10) * 0.35;
      points.add(new double [] {x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, top - radius * radius * 0.4, pointClass});
    }
  }

  /**
   * The trees of a classified cloud are its vegetation only, away from the buildings of the cloud and of the home.
   */
  @Test
  public void testTreesFromClassifiedLidar() throws Exception {
    java.util.List<double []> points = new java.util.ArrayList<double []>();
    for (int y = 0; y <= 60; y += 2) {
      for (int x = 0; x <= 80; x += 2) {
        points.add(new double [] {x, y, 0, 2});
      }
    }
    // A wide tree of 15 m with two bumps on its crown, 5 m apart
    for (double y = 22; y <= 38; y += 0.5) {
      for (double x = 12; x <= 28; x += 0.5) {
        if (Math.hypot(x - 20, y - 30) <= 8) {
          points.add(new double [] {x, y, 15 - 0.01 * ((x - 20) * (x - 20) + (y - 30) * (y - 30)), 5});
        }
      }
    }
    points.add(new double [] {20.5, 30.5, 15.5, 5});
    points.add(new double [] {25.5, 30.5, 15.3, 5});
    // The roof of a building, 8 m high, with unclassified points at its edge
    for (double y = 10; y <= 20; y += 0.5) {
      for (double x = 50; x <= 60; x += 0.5) {
        boolean edge = x == 50 || x == 60 || y == 10 || y == 20;
        points.add(new double [] {x, y, 8 - Math.abs(x - 55) * 0.5, edge ? 1 : 6});
      }
    }
    // A pole among the unclassified points
    points.add(new double [] {70, 50, 9, 1});
    // Vegetation over the house of the home: the branches of a tree above its roof
    addCrown(points, 40, 45, 10, 4);
    java.io.File file = createLas(points);
    String path = file.getPath().replace("\\", "\\\\");
    // The house, at 40 m to the east and 45 m to the north of the corner of the file: y = 6000 - 4500
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living room\",\"x\":3500,\"y\":1000,\"width\":1000,\"depth\":1000}");
    run("{\"action\":\"set_terrain\",\"points\":[[0,0,50],[8000,0,50],[8000,6000,50],[0,6000,50]]}");
    String summary = run("{\"action\":\"trees_from_lidar\",\"file\":\"" + path + "\",\"x\":0,\"y\":6000,\"min_height\":500}");
    assertTrue(summary, summary.contains("Found 1 treetops") && summary.contains("vegetation points (classes 3 to 5)")
        && summary.contains("tops over the buildings of the cloud or of the home were left out")
        && summary.contains("merged with it"));
    List<HomePieceOfFurniture> plants = getPlants();
    assertEquals(1, plants.size());
    // The middle of an irregular crown isn't over its trunk
    assertEquals(2000, plants.get(0).getX(), 300);
    assertEquals(3000, plants.get(0).getY(), 300);
    // On the ground, not on the floor of a room
    assertEquals(50, getBottom(plants.get(0)), 1);
  }

  /**
   * The rooms outdoors and the areas of the ground are what the report counts as paved, on the lot only,
   * and the export leaves the rooms of the house out.
   */
  @Test
  public void testSiteRoomsAreasAndNeighbors() throws Exception {
    run("{\"action\":\"add_property_line\",\"points\":[[0,0],[4000,0],[4000,3000],[0,3000]]}");
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Bedroom\",\"x\":1000,\"y\":1000,\"width\":1000,\"depth\":800}");
    // A driveway drawn as a room without walls, half of it out of the lot
    run("{\"action\":\"add_room\",\"name\":\"Driveway\",\"points\":[[3000,2000],[5000,2000],[5000,2500],[3000,2500]]}");
    run("{\"action\":\"set_terrain\",\"points\":[" + flatGround(0) + "],\"areas\":["
        + "{\"name\":\"Street\",\"color\":\"#555555\",\"points\":[[0,2800],[4000,2800],[4000,3400],[0,3400]]},"
        + "{\"name\":\"Lawn\",\"color\":\"#55AA55\",\"points\":[[0,0],[900,0],[900,900],[0,900]]},"
        + "{\"surface\":\"patio\",\"color\":\"#AAAAAA\",\"points\":[[100,1500],[600,1500],[600,2000],[100,2000]]}]}");
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":500,\"y\":500,\"height\":1500,\"crown_radius\":400},"
        + "{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":6000,\"y\":500,\"height\":1700,\"crown_radius\":400},"
        + "{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":-200,\"y\":1500,\"height\":1700,\"crown_radius\":400}");
    String summary = run("{\"action\":\"site_report\"}");
    // 10 x 5 m of the driveway and 40 x 2 m of the street are on the lot
    assertTrue(summary, summary.contains("driveways 130 m2") && summary.contains("patios and decks 25 m2")
        && summary.contains("roofs or rooms 80 m2") && summary.contains("1 room outdoors and 2 areas of the ground"));
    assertTrue(summary, summary.contains("Generated trees: 3") && summary.contains("1 of them stand on the lot"));
    // The crown of the tree on the lot, and a part of the one of the tree beside it: less than two crowns
    float canopy = Float.parseFloat(summary.substring(summary.indexOf("the crowns cover ") + 17, summary.indexOf(" m2 of the lot (")));
    assertTrue(summary, canopy > 51 && canopy < 75);

    run("{\"action\":\"set_georeference\",\"x\":0,\"y\":0,\"lat\":33.749,\"lon\":-84.388}");
    run("{\"action\":\"add_polyline\",\"name\":\"Neighbor lot\",\"points\":[[4000,0],[8000,0],[8000,3000],[4000,3000]],\"closed\":true}");
    java.io.File file = java.io.File.createTempFile("site", ".geojson");
    file.deleteOnExit();
    String path = file.getPath().replace("\\", "\\\\");
    summary = run("{\"action\":\"export_site\",\"path\":\"" + path + "\",\"replace\":true}");
    assertTrue(summary, summary.contains("lot_line 2") && summary.contains("room 1") && !summary.contains("room_interior 1")
        && summary.contains("The 1 room of the house isn't in it"));
    String geojson = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
    assertTrue(geojson, geojson.contains("\"role\":\"subject\"") && geojson.contains("\"role\":\"neighbor\"")
        && geojson.contains("\"name\":\"Driveway\"") && !geojson.contains("Bedroom"));
    summary = run("{\"action\":\"export_site\",\"path\":\"" + path + "\",\"replace\":true,\"interior_rooms\":true}");
    assertTrue(summary, summary.contains("room_interior 1"));
    geojson = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
    assertTrue(geojson, geojson.contains("\"kind\":\"room_interior\"") && geojson.contains("Bedroom"));
  }

  /**
   * The furniture list written for a program: ids, places and a unit in its header.
   */
  @Test
  public void testFurnitureCsv() throws Exception {
    run("{\"action\":\"add_tree\",\"species\":\"white_oak\",\"x\":1000,\"y\":500,\"height\":900,\"name\":\"Oak, \\\"old\\\"\"}");
    java.io.File file = java.io.File.createTempFile("furniture", ".csv");
    file.deleteOnExit();
    String result = com.eteks.sweethome3d.viewcontroller.HomeFurnitureCsvWriter.write(this.home, file, null, null);
    assertTrue(result, result.contains("1 piece") && result.contains("lengths in cm"));
    String [] lines = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8").split("\r\n");
    assertEquals("id,name,level,x_cm,y_cm,elevation_cm,angle_deg,width_cm,depth_cm,height_cm,visible,tags", lines [0]);
    HomePieceOfFurniture tree = getPlants().get(0);
    assertTrue(lines [1], lines [1].startsWith(HomeItemIds.getId(tree) + ",\"Oak, \"\"old\"\"\",")
        && lines [1].contains(",true,") && lines [1].contains("white_oak"));
    assertEquals(tree.getX(), Float.parseFloat(lines [1].split(",") [4]), 0.01f);
    result = com.eteks.sweethome3d.viewcontroller.HomeFurnitureCsvWriter.write(this.home, file,
        java.util.Arrays.asList("id", "x", "height", "created_by"), "m");
    lines = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8").split("\r\n");
    assertEquals("id,x_m,height_m,created_by", lines [0]);
    assertEquals(tree.getHeight() / 100, Float.parseFloat(lines [1].split(",") [2]), 0.01f);
    try {
      com.eteks.sweethome3d.viewcontroller.HomeFurnitureCsvWriter.write(this.home, file, java.util.Arrays.asList("price"), null);
      org.junit.Assert.fail();
    } catch (java.io.IOException ex) {
      assertTrue(ex.getMessage(), ex.getMessage().contains("isn't a column"));
    }
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
