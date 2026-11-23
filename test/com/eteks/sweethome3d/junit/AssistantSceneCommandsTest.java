/*
 * AssistantSceneCommandsTest.java 21 nov. 2026
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.CatalogLight;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.LightSource;
import com.eteks.sweethome3d.model.Transformation;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Tests the commands that set the lights, the sun, the 3D view, the doors and the materials.
 */
public class AssistantSceneCommandsTest {
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;
  private Content                  content;

  @Before
  public void setUp() throws Exception {
    this.home = new Home(250);
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.homeController = new HomeController(this.home, preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, preferences, this.homeController);
    this.content = new com.eteks.sweethome3d.tools.URLContent(new File("dummy.png").toURI().toURL());
    // A model with the materials wood and fabric, and one leaf on hinges 80 cm wide which turns around its left side
    this.executor.setModelInspector(new AssistantCommandExecutor.ModelInspector() {
        public List<String> getMaterialNames(Content model) {
          return Arrays.asList("wood", "fabric_seat", "fabric_back");
        }

        public AssistantCommandExecutor.ModelOpening getOpening(Content model, float [][] modelRotation,
                                                                 Transformation [] currentTransformations, float angle) {
          AssistantCommandExecutor.ModelOpening opening = new AssistantCommandExecutor.ModelOpening();
          opening.hingeCount = 1;
          boolean open = currentTransformations != null;
          opening.currentBounds = open ? new float [] {0, 0, 0, 80, 200, 80} : new float [] {0, 0, 0, 80, 200, 10};
          if (Math.abs(angle) > 0.01f) {
            opening.transformations = new Transformation [] {new Transformation("sweethome3d_hinge_1",
                new float [][] {{0, 0, 1, 0}, {0, 1, 0, 0}, {-1, 0, 0, 0}})};
            opening.newBounds = new float [] {0, 0, 0, 80, 200, 80};
          } else {
            opening.newBounds = new float [] {0, 0, 0, 80, 200, 10};
          }
          return opening;
        }
      });
  }

  private String run(String commands) {
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  private HomePieceOfFurniture addPiece(String name, boolean light) {
    HomePieceOfFurniture piece = light
        ? new HomeLight(new CatalogLight(null, name, null, this.content, this.content, 30f, 30f, 30f, 200f, true, new LightSource [0], null, null, false, null, null))
        : new HomePieceOfFurniture(new CatalogPieceOfFurniture(name, null, this.content, 80, 10, 200, true, false));
    this.home.addPieceOfFurniture(piece);
    return piece;
  }

  @Test
  public void testSunAndEnvironment() {
    String summary = run("{\"action\":\"set_compass\",\"latitude\":48.85,\"longitude\":2.35,\"time_zone\":\"Europe/Paris\",\"north_direction\":0}");
    long oldTime = this.home.getCamera().getTime();
    summary = run("{\"action\":\"set_sun\",\"date\":\"2026-06-21\",\"time\":\"13:50\",\"follow\":true}");
    // Around the solar noon of Paris in summer the sun is high in the south
    assertTrue(summary, summary.contains("in the south (azimuth 1"));
    assertTrue(summary, summary.contains("64.6 degrees above the horizon"));
    assertTrue(summary, summary.contains("sunrise 05:47"));
    assertTrue(summary, summary.contains("sunset 21:58"));
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(this.home.getTopCamera().getTime());
    assertEquals(13, calendar.get(Calendar.HOUR_OF_DAY));
    assertEquals(50, calendar.get(Calendar.MINUTE));
    assertEquals(21, calendar.get(Calendar.DAY_OF_MONTH));
    assertEquals(this.home.getTopCamera().getTime(), this.home.getObserverCamera().getTime());
    assertEquals("true", this.home.getProperty("sunFollowsTime"));
    // False colors of the light level
    summary = run("{\"action\":\"set_sun\",\"false_colors\":true}");
    assertTrue(summary, summary.contains("false colors"));
    assertEquals("120000", this.home.getProperty("sunFalseColorsMaximum"));
    summary = run("{\"action\":\"set_sun\",\"false_colors\":30000}");
    assertEquals("30000", this.home.getProperty("sunFalseColorsMaximum"));
    summary = run("{\"action\":\"set_sun\",\"false_colors\":5}");
    assertTrue(summary, summary.contains("from 100 to 200000"));
    summary = run("{\"action\":\"set_sun\",\"false_colors\":false}");
    assertNull(this.home.getProperty("sunFalseColorsMaximum"));
    this.homeController.undo();
    assertEquals("30000", this.home.getProperty("sunFalseColorsMaximum"));
    this.homeController.undo();
    this.homeController.undo();
    assertNull(this.home.getProperty("sunFalseColorsMaximum"));
    summary = run("{\"action\":\"set_sun\",\"time\":\"02:00\"}");
    assertTrue(summary, summary.contains("under the horizon"));
    summary = run("{\"action\":\"set_sun\",\"time\":\"25:00\"}");
    assertTrue(summary, summary.contains("HH:MM"));
    this.homeController.undo();
    this.homeController.undo();
    assertEquals(oldTime, this.home.getTopCamera().getTime());
    assertNull(this.home.getProperty("sunFollowsTime"));

    summary = run("{\"action\":\"set_environment\",\"sky_color\":\"#102030\",\"walls_transparency\":50,\"all_levels_visible\":false}");
    assertTrue(summary, summary.contains("sky color"));
    assertEquals(0x102030, this.home.getEnvironment().getSkyColor() & 0xFFFFFF);
    assertEquals(0.5f, this.home.getEnvironment().getWallsAlpha(), 0.001f);
    this.homeController.undo();
    assertEquals(0, this.home.getEnvironment().getWallsAlpha(), 0.001f);
    summary = run("{\"action\":\"set_environment\",\"walls_transparency\":150}");
    assertTrue(summary, summary.contains("from 0"));
  }

  @Test
  public void testLightsDoorsAndMaterials() {
    HomeLight lamp = (HomeLight)addPiece("Lamp", true);
    HomePieceOfFurniture door = addPiece("Door", false);
    door.setX(100);
    door.setY(200);
    float power = lamp.getPower();
    String summary = run("{\"action\":\"set_light\",\"all\":true,\"power\":80}");
    assertTrue(summary, summary.contains("1 light to 80 %"));
    assertEquals(0.8f, lamp.getPower(), 0.001f);
    run("{\"action\":\"set_light\",\"names\":[\"Lamp\"],\"on\":false}");
    assertEquals(0, lamp.getPower(), 0.001f);
    this.homeController.undo();
    this.homeController.undo();
    assertEquals(power, lamp.getPower(), 0.001f);
    summary = run("{\"action\":\"set_light\",\"names\":[\"Door\"],\"power\":10}");
    assertTrue(summary, summary.contains("is a light"));

    // An open door takes the room its leaf sweeps
    summary = run("{\"action\":\"open_door\",\"names\":[\"Door\"],\"angle\":90}");
    assertTrue(summary, summary.contains("Opened by 90 degrees"));
    assertNotNull(door.getModelTransformations());
    assertEquals(80, door.getDepth(), 0.01f);
    assertEquals(80, door.getWidth(), 0.01f);
    assertEquals(100, door.getX(), 0.01f);
    assertEquals(235, door.getY(), 0.01f);
    summary = run("{\"action\":\"open_door\",\"names\":[\"Door\"],\"open\":false}");
    assertTrue(summary, summary.contains("Closed"));
    assertNull(door.getModelTransformations());
    assertEquals(10, door.getDepth(), 0.01f);
    assertEquals(200, door.getY(), 0.01f);
    this.homeController.undo();
    assertEquals(80, door.getDepth(), 0.01f);
    this.homeController.undo();
    assertNull(door.getModelTransformations());
    assertEquals(200, door.getY(), 0.01f);

    summary = run("{\"action\":\"list_materials\",\"names\":[\"Door\"]}");
    assertTrue(summary, summary.contains("\"wood\", \"fabric_seat\", \"fabric_back\""));
    door.setColor(0xFF00FF00);
    summary = run("{\"action\":\"set_material\",\"names\":[\"Door\"],\"material\":\"fabric\",\"color\":\"#336699\"}");
    assertTrue(summary, summary.contains("\"fabric_seat\", \"fabric_back\""));
    assertEquals(3, door.getModelMaterials().length);
    assertNull(door.getModelMaterials() [0]);
    assertEquals(0x336699, door.getModelMaterials() [1].getColor() & 0xFFFFFF);
    assertEquals(0x336699, door.getModelMaterials() [2].getColor() & 0xFFFFFF);
    // The color of the whole piece would hide its materials
    assertNull(door.getColor());
    summary = run("{\"action\":\"set_material\",\"names\":[\"Door\"],\"material\":\"wood\",\"shininess\":50}");
    assertEquals(0.5f, door.getModelMaterials() [0].getShininess(), 0.001f);
    assertEquals(0x336699, door.getModelMaterials() [1].getColor() & 0xFFFFFF);
    summary = run("{\"action\":\"set_material\",\"names\":[\"Door\"],\"material\":\"steel\",\"color\":\"#000000\"}");
    assertTrue(summary, summary.contains("no material named"));
    summary = run("{\"action\":\"set_material\",\"names\":[\"Door\"],\"reset\":true}");
    assertNull(door.getModelMaterials());
    this.homeController.undo();
    this.homeController.undo();
    this.homeController.undo();
    assertNull(door.getModelMaterials());
    assertEquals(0xFF00FF00, door.getColor().intValue());
  }
}
