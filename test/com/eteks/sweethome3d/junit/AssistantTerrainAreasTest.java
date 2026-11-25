/*
 * AssistantTerrainAreasTest.java 25 nov. 2026
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
import com.eteks.sweethome3d.viewcontroller.HomeTerrain;

/**
 * Tests the areas of the ground - their list, the change of their finish, their split - and the
 * grading recorded with the ground, run again when the ground is built from other points.
 * @author Sweet Home 3D contributors
 */
public class AssistantTerrainAreasTest {
  private String                   previousNo3D;
  private Locale                   previousLocale;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private String                   texture;

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
    this.texture = this.preferences.getTexturesCatalog().getCategories().get(0).getTextures().get(0).getName();
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

  private HomePieceOfFurniture getTerrain() {
    HomePieceOfFurniture terrain = null;
    int count = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (piece.getProperty("assistantTerrain") != null) {
        terrain = piece;
        count++;
      }
    }
    assertTrue(count + " grounds", count <= 1);
    return terrain;
  }

  /**
   * Returns the height of the ground at a point, computed from its triangles.
   */
  private float getHeight(float x, float y) {
    for (float [][] triangle : HomeTerrain.getTriangles(getTerrain())) {
      double determinant = (triangle [1][1] - triangle [2][1]) * (triangle [0][0] - triangle [2][0])
          + (triangle [2][0] - triangle [1][0]) * (triangle [0][1] - triangle [2][1]);
      if (Math.abs(determinant) < 1E-9) {
        continue;
      }
      double w1 = ((triangle [1][1] - triangle [2][1]) * (x - triangle [2][0]) + (triangle [2][0] - triangle [1][0]) * (y - triangle [2][1])) / determinant;
      double w2 = ((triangle [2][1] - triangle [0][1]) * (x - triangle [2][0]) + (triangle [0][0] - triangle [2][0]) * (y - triangle [2][1])) / determinant;
      double w3 = 1 - w1 - w2;
      if (w1 >= -1E-6 && w2 >= -1E-6 && w3 >= -1E-6) {
        return (float)(w1 * triangle [0][2] + w2 * triangle [1][2] + w3 * triangle [2][2]);
      }
    }
    return Float.NaN;
  }

  /**
   * Returns the points [x,y,z] of a slope that goes up by 2 m over <code>size</code> cm to the right,
   * every 250 cm, from the origin.
   */
  private static String getSlopePoints(int size, float offset) {
    StringBuilder points = new StringBuilder();
    for (int y = 0; y <= size; y += 250) {
      for (int x = 0; x <= size; x += 250) {
        points.append(points.length() > 0 ? "," : "").append('[').append(x).append(',').append(y).append(',')
            .append(x * 200f / 4000 + offset).append(']');
      }
    }
    return points.toString();
  }

  /**
   * Builds a ground of 40 x 40 m with a driveway and a lawn, a pad for a house and a graded path.
   */
  private void buildGround() {
    String summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, 0) + "],\"color\":\"#8A6A45\","
        + "\"areas\":[{\"name\":\"Driveway\",\"surface\":\"driveway\",\"points\":[[200,200],[800,200],[800,3000],[200,3000]],\"color\":\"#55555A\"},"
        + "{\"name\":\"Yard\",\"points\":[[1000,2400],[3800,2400],[3800,3800],[1000,3800]],\"color\":\"#7A5A3A\"}]}");
    assertNotNull(summary, getTerrain());
    summary = run("{\"action\":\"add_pad\",\"points\":[[1500,500],[2700,500],[2700,1500],[1500,1500]],\"height\":120,\"blend_distance\":200},"
        + "{\"action\":\"grade_path\",\"points\":[[500,3200],[500,1800],[1300,1000]],\"width\":300,\"heights\":[20,30,90]}");
    assertTrue(summary, summary.contains("Added a pad"));
    assertTrue(summary, summary.contains("Graded"));
  }

  /**
   * The areas of the ground are listed, and the finish of one of them changes while every height stays.
   */
  @Test
  public void testAreasAndFinish() {
    buildGround();
    HomePieceOfFurniture terrain = getTerrain();
    String points = terrain.getProperty("assistantTerrain");
    List<float [][]> triangles = HomeTerrain.getTriangles(terrain);
    float padHeight = getHeight(2100, 1000);
    float pathHeight = getHeight(500, 1800);
    assertEquals(120, padHeight, 0.01f);
    assertEquals(30, pathHeight, 1f);

    // The list of the areas, by its command and by describe_item
    String summary = run("{\"action\":\"terrain_areas\"}");
    assertTrue(summary, summary.contains("its base: color #8A6A45"));
    assertTrue(summary, summary.contains("A1 \"Driveway\" (driveway): color #55555A, from (200, 200) to (800, 3000), 168 m2"));
    assertTrue(summary, summary.contains("A2 \"Yard\": color #7A5A3A, from (1000, 2400) to (3800, 3800), 392 m2"));
    assertTrue(summary, summary.contains("1. add_pad from (1500, 500) to (2700, 1500) at 120 cm"));
    assertTrue(summary, summary.contains("2. grade_path from (500, 1000) to (1300, 3200)"));
    String id = summary.substring(summary.indexOf("ground F") + 7, summary.indexOf(" of ", summary.indexOf("ground F")));
    summary = run("{\"action\":\"describe_item\",\"id\":\"" + id + "\"}");
    assertTrue(summary, summary.contains("A2 \"Yard\": color #7A5A3A"));

    // The dirt of the yard becomes grass, by its name
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Yard\",\"color\":\"#4F8A3C\"}");
    assertTrue(summary, summary.contains("Changed the area A2 \"Yard\" from the color #7A5A3A to the color #4F8A3C"));
    assertTrue(summary, summary.contains("its heights, its grading and what stands on it didn't change"));
    assertFalse(summary, summary.contains("Couldn't"));
    terrain = getTerrain();
    assertEquals("The same points", points, terrain.getProperty("assistantTerrain"));
    assertTrue(terrain.getProperty("assistantTerrainParameters").contains("#4F8A3C"));
    assertFalse(terrain.getProperty("assistantTerrainParameters").contains("#7A5A3A"));
    // Every height to less than a millimeter
    List<float [][]> newTriangles = HomeTerrain.getTriangles(terrain);
    assertEquals(triangles.size(), newTriangles.size());
    for (int i = 0; i < triangles.size(); i++) {
      for (int corner = 0; corner < 3; corner++) {
        for (int k = 0; k < 3; k++) {
          assertEquals(triangles.get(i) [corner][k], newTriangles.get(i) [corner][k], 0.05f);
        }
      }
    }
    assertEquals(padHeight, getHeight(2100, 1000), 0.01f);
    assertEquals(pathHeight, getHeight(500, 1800), 0.01f);
    // The grading stays recorded, and the earth moved is still measured from the first ground
    assertTrue(terrain.getProperty("assistantTerrainOperations").contains("grade_path"));
    summary = run("{\"action\":\"cut_fill_report\"}");
    assertFalse(summary, summary.contains("0 m3 cut and 0 m3 filled"));

    // By a point in it, with a texture; then the ground around the areas
    summary = run("{\"action\":\"modify_terrain_area\",\"at\":[500,2500],\"texture\":\"" + this.texture + "\",\"texture_scale\":2}");
    assertTrue(summary, summary.contains("Changed the area A1 \"Driveway\" from the color #55555A to the texture \"" + this.texture + "\""));
    summary = run("{\"action\":\"modify_terrain_area\",\"at\":[3000,300],\"color\":\"#6E9A4C\"}");
    assertTrue(summary, summary.contains("Changed the ground around the areas from the color #8A6A45 to the color #6E9A4C"));
    terrain = getTerrain();
    assertEquals(points, terrain.getProperty("assistantTerrain"));
    assertTrue(terrain.getProperty("assistantTerrainParameters").contains(this.texture));

    // What can't be done is told and changes nothing
    String parameters = terrain.getProperty("assistantTerrainParameters");
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"A7\",\"color\":\"#FF0000\"}");
    assertTrue(summary, summary.contains("no area has the id A7") && summary.contains("A1 \"Driveway\""));
    summary = run("{\"action\":\"modify_terrain_area\",\"at\":[9000,9000],\"color\":\"#FF0000\"}");
    assertTrue(summary, summary.contains("out of the ground"));
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Yard\",\"texture\":\"no such texture 123\"}");
    assertTrue(summary, summary.contains("Couldn't run modify_terrain_area"));
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Yard\"}");
    assertTrue(summary, summary.contains("give the \"color\" or the \"texture\""));
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Yard\",\"colour\":\"#FF0000\",\"color\":\"#4F8A3D\"}");
    assertTrue(summary, summary.contains("ignored the key [colour]"));
    assertEquals(parameters.replace("#4F8A3C", "#4F8A3D"), getTerrain().getProperty("assistantTerrainParameters"));

    // Undone as one step
    this.homeController.undo();
    assertTrue(getTerrain().getProperty("assistantTerrainParameters").contains("#4F8A3C"));
  }

  /**
   * A part of an area gets a finish of its own, and an area is removed.
   */
  @Test
  public void testSplitAndRemove() {
    buildGround();
    String points = getTerrain().getProperty("assistantTerrain");
    // A strip across the yard, wider than it
    String summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"A2\",\"split\":[[2000,2000],[2600,2000],[2600,5000],[2000,5000]],"
        + "\"color\":\"#4F8A3C\",\"name\":\"Lawn strip\"}");
    assertTrue(summary, summary.contains("The part of the area A2 \"Yard\" inside the polygon, 84 m2, is now the area A3"));
    assertTrue(summary, summary.contains("A3 \"Lawn strip\": color #4F8A3C, from (2000, 2400) to (2600, 3800), 84 m2"));
    assertTrue(summary, summary.contains("A2 \"Yard\": color #7A5A3A"));
    assertEquals(points, getTerrain().getProperty("assistantTerrain"));
    // A polygon in the ground around the areas becomes an area
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"base\",\"split\":[[3000,200],[3800,200],[3800,1000],[3000,1000]],"
        + "\"texture\":\"" + this.texture + "\",\"surface\":\"patio\"}");
    assertTrue(summary, summary.contains("is now the area A1"));
    assertTrue(summary, summary.contains("A1 (patio): texture \"" + this.texture + "\", from (3000, 200) to (3800, 1000), 64 m2"));
    assertTrue(summary, summary.contains("A4 \"Lawn strip\""));
    // A polygon out of the area
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Lawn strip\",\"split\":[[0,0],[100,0],[100,100]],\"color\":\"#000000\"}");
    assertTrue(summary, summary.contains("doesn't cross the area A4"));
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Yard\",\"split\":[[2000,2000],[2600,2000],[2600,5000]]}");
    assertTrue(summary, summary.contains("\"split\" needs the \"color\" or the \"texture\""));

    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Lawn strip\",\"remove\":true}");
    assertTrue(summary, summary.contains("Removed the area A4 \"Lawn strip\""));
    summary = run("{\"action\":\"terrain_areas\"}");
    assertTrue(summary, summary.contains("3 areas"));
    assertFalse(summary, summary.contains("Lawn strip"));
    assertEquals(points, getTerrain().getProperty("assistantTerrain"));
    assertEquals(120, getHeight(2100, 1000), 0.01f);
  }

  /**
   * The ground built again from points keeps its areas, its pad and its path.
   */
  @Test
  public void testGradingReplayed() {
    buildGround();
    // The whole site 50 cm higher, with another finish
    String summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, 50) + "],\"color\":\"#4F8A3C\"}");
    assertTrue(summary, summary.contains("The grading recorded with the ground replaced was run again on the new one, in order: add_pad, grade_path"));
    assertTrue(summary, summary.contains("The 2 areas of the ground replaced were kept"));
    HomePieceOfFurniture terrain = getTerrain();
    assertTrue(terrain.getProperty("assistantTerrainParameters").contains("#4F8A3C"));
    // The pad at its height and the path through its heights, the rest of the ground higher
    assertEquals(120, getHeight(2100, 1000), 0.01f);
    assertEquals(30, getHeight(500, 1800), 1f);
    assertEquals(3500 * 200f / 4000 + 50, getHeight(3500, 3500), 0.5f);
    String operations = terrain.getProperty("assistantTerrainOperations");
    assertEquals("Recorded once each", 2, operations.split("\"action\"").length - 1);
    assertTrue(operations.indexOf("add_pad") < operations.indexOf("grade_path"));
    // The earth moved is measured from the new ground
    summary = run("{\"action\":\"cut_fill_report\"}");
    assertFalse(summary, summary.contains("Couldn't"));

    // A pad at the mean height of the ground is recorded at the height it got
    summary = run("{\"action\":\"flatten_area\",\"points\":[[3000,3000],[3600,3000],[3600,3600],[3000,3600]]}");
    float flat = getHeight(3300, 3300);
    assertEquals(3300 * 200f / 4000 + 50, flat, 3f);
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, -100) + "]}");
    assertTrue(summary, summary.contains("in order: add_pad, grade_path, flatten_area"));
    assertEquals(flat, getHeight(3300, 3300), 0.11f);
    assertEquals(120, getHeight(2100, 1000), 0.01f);

    // Rebuilt without its grading when asked
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, 0) + "],\"grading\":\"drop\"}");
    assertTrue(summary, summary.contains("The 3 grading operations recorded with the ground replaced were dropped"));
    assertEquals(2100 * 200f / 4000, getHeight(2100, 1000), 0.5f);
    assertEquals("[]", getTerrain().getProperty("assistantTerrainOperations"));
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, 0) + "],\"grading\":\"maybe\"}");
    assertTrue(summary, summary.contains("\"grading\" is replay, keep_possible or drop"));
  }

  /**
   * A ground that would lose a part of its grading isn't replaced unless asked.
   */
  @Test
  public void testGradingLostIsTold() {
    buildGround();
    HomePieceOfFurniture terrain = getTerrain();
    String points = terrain.getProperty("assistantTerrain");
    // A smaller ground: the pad is on it, the path isn't
    String smaller = "{\"action\":\"set_terrain\",\"points\":[[1000,0,50],[4000,0,200],[4000,4000,200],[1000,4000,50],[2500,2000,125]]";
    String summary = run(smaller + "}");
    assertTrue(summary, summary.contains("nothing was changed: the new ground would lose 1 of the 2 grading operations"));
    assertTrue(summary, summary.contains("2. grade_path from (500, 1000) to (1300, 3200): 2 of its 3 points are out of the new ground"));
    assertTrue(summary, summary.contains("\"grading\":\"keep_possible\""));
    assertEquals("Not replaced", points, getTerrain().getProperty("assistantTerrain"));
    assertTrue(getTerrain().getProperty("assistantTerrainOperations").contains("grade_path"));
    assertEquals(120, getHeight(2100, 1000), 0.01f);

    summary = run(smaller + ",\"grading\":\"keep_possible\"}");
    assertTrue(summary, summary.contains("was run again on the new one, in order: add_pad"));
    assertTrue(summary, summary.contains("Grading LOST with the ground replaced: 2. grade_path"));
    assertEquals(120, getHeight(2100, 1000), 0.01f);
    assertTrue(Float.isNaN(getHeight(500, 1800)));
    assertFalse(getTerrain().getProperty("assistantTerrainOperations").contains("grade_path"));
  }

  /**
   * A ground graded by a version that didn't record grading tells it before it is replaced.
   */
  @Test
  public void testGroundOfPreviousVersion() {
    buildGround();
    HomePieceOfFurniture terrain = getTerrain();
    // As saved by a previous version: graded, the ground before grading kept, no operation
    terrain.setProperty("assistantTerrainOperations", null);
    assertNotNull(this.home.getProperty("assistantTerrainBaseline"));
    String points = terrain.getProperty("assistantTerrain");

    String summary = run("{\"action\":\"terrain_areas\"}");
    assertTrue(summary, summary.contains("1. the grading done before Sweet Home 3D recorded it"));
    // The finish of its areas changes as for any ground
    summary = run("{\"action\":\"modify_terrain_area\",\"area\":\"Yard\",\"color\":\"#4F8A3C\"}");
    assertTrue(summary, summary.contains("Changed the area A2"));
    assertEquals(points, getTerrain().getProperty("assistantTerrain"));
    assertNull(getTerrain().getProperty("assistantTerrainOperations"));
    assertEquals(120, getHeight(2100, 1000), 0.01f);

    // New points would lose the pad and the path, which aren't recorded
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, 0) + "]}");
    assertTrue(summary, summary.contains("nothing was changed"));
    assertTrue(summary, summary.contains("the grading done before Sweet Home 3D recorded it (version 7.33.1 or earlier): it isn't recorded"));
    assertEquals(points, getTerrain().getProperty("assistantTerrain"));
    // What is graded from now on is recorded after it
    summary = run("{\"action\":\"add_berm\",\"points\":[[3000,500],[3600,500]],\"height\":40,\"width\":200}");
    assertTrue(summary, summary.contains("Raised a berm"));
    String operations = getTerrain().getProperty("assistantTerrainOperations");
    assertTrue(operations, operations.indexOf("unrecorded_grading") >= 0 && operations.indexOf("unrecorded_grading") < operations.indexOf("add_berm"));
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, 0) + "],\"grading\":\"keep_possible\"}");
    assertTrue(summary, summary.contains("in order: add_berm"));
    assertTrue(summary, summary.contains("Grading LOST with the ground replaced: 1. the grading done before"));
    assertEquals(2100 * 200f / 4000, getHeight(2100, 1000), 0.5f);

    // A ground never graded is replaced as before
    this.home.setProperty("assistantTerrainBaseline", null);
    getTerrain().setProperty("assistantTerrainOperations", null);
    summary = run("{\"action\":\"set_terrain\",\"points\":[" + getSlopePoints(4000, 10) + "]}");
    assertFalse(summary, summary.contains("nothing was changed"));
    assertFalse(summary, summary.contains("grading"));
    assertEquals(2100 * 200f / 4000 + 10, getHeight(2100, 1000), 0.5f);
  }
}
