/*
 * AssistantTwoStoryHouseTest.java 25 nov. 2026
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

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Builds the house of the issue that asked for these commands, with the commands of several
 * families used together: a two-story hall with an overlook, beside a one-story wing whose roof
 * runs into the hall, checked and repaired, then finished and furnished from outside.
 * @author Sweet Home 3D contributors
 */
public class AssistantTwoStoryHouseTest {
  private String                   previousNo3D;
  private Home                     home;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.homeController = new HomeController(this.home, preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, preferences, this.homeController);
    // The size of the models read, which the window of the application gives otherwise
    this.executor.setModelSizeProvider(new AssistantCommandExecutor.ModelSizeProvider() {
        public float [] getModelSize(com.eteks.sweethome3d.model.Content content) {
          return new float [] {180, 450, 140};
        }
      });
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

  private Room getRoom(String name) {
    for (Room room : this.home.getRooms()) {
      if (name.equals(room.getName())) {
        return room;
      }
    }
    return null;
  }

  private HomePieceOfFurniture getPiece(String name) {
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (name.equals(piece.getName())) {
        return piece;
      }
    }
    return null;
  }

  @Test
  public void testTwoStoryHallWithWingRoof() throws Exception {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Hall\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400},"
        + "{\"action\":\"add_rectangular_room\",\"name\":\"Wing\",\"next_to\":\"Hall\",\"side\":\"west\",\"width\":600,\"depth\":400},"
        + "{\"action\":\"add_rectangular_room\",\"name\":\"Back\",\"next_to\":\"Hall\",\"side\":\"north\",\"width\":500,\"depth\":250},"
        + "{\"action\":\"add_level\",\"name\":\"Level 1\"}");
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Upper hall\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,\"level\":\"Level 1\"},"
        + "{\"action\":\"add_rectangular_room\",\"name\":\"Corridor\",\"x\":0,\"y\":-257.5,\"width\":500,\"depth\":250,\"level\":\"Level 1\"}");

    // The first level is named in the language of the computer
    String groundLevel = this.home.getLevels().get(0).getName();
    // The roof of the wing drawn 20 cm into the hall, an edge given by its number with a key that doesn't exist
    String summary = run("{\"action\":\"add_roof\",\"name\":\"Wing roof\",\"level\":\"" + groundLevel + "\","
        + "\"points\":[[-630,-20],[20,-20],[20,420],[-630,420]],\"eave_height\":250,\"pitch\":30,\"overhang\":30,\"type\":\"hip\","
        + "\"gutters\":true,\"edges\":[{\"index\":2,\"type\":\"hip\",\"guter\":false}]}");
    assertTrue(summary, summary.contains("ignored the key [guter] of the edge 2"));
    assertFalse("The number of an edge is one of its keys: " + summary, summary.contains("index"));
    float roofHeight = getPiece("Wing roof").getHeight();

    // The hall opened to the level above, with an overlook from the corridor
    summary = run("{\"action\":\"open_to_above\",\"room\":\"Hall\",\"to_level\":\"Level 1\","
        + "\"overlook\":{\"along\":\"north\",\"railing\":\"turned_balusters\",\"height\":91}}");
    assertTrue(summary, summary.contains("A railing of style turned_balusters was built along the overlook"));
    assertTrue(summary, summary.contains("Still inside the opened space: the roof"));
    HomePieceOfFurniture railing = getPiece("Railing");
    assertNotNull(summary, railing);
    assertEquals("Level 1", railing.getLevel().getName());
    assertEquals(91, railing.getHeight(), 3f);
    // The floor and the ceiling of the corridor reach the side of the overlook, where the wall stood
    float maxY = -Float.MAX_VALUE;
    for (float [] point : getRoom("Corridor").getPoints()) {
      maxY = Math.max(maxY, point [1]);
    }
    assertEquals(0, maxY, 0.01f);

    // The roof in the hall is the only defect of the two-story room: its overlook isn't one
    summary = run("{\"action\":\"check_home\"}");
    assertTrue(summary, summary.contains("[roof_in_room]"));
    assertTrue(summary, summary.contains("\"edge\":{\"index\":2,\"type\":\"clip\"}"));
    assertFalse(summary, summary.contains("[open_two_story_room]"));
    summary = run("{\"action\":\"fix_home\"}");
    assertTrue(summary, summary.contains("clipped at the face of"));
    summary = run("{\"action\":\"check_home\",\"min_severity\":\"error\"}");
    assertFalse("Neither the roof nor its gutters are in the hall anymore: " + summary, summary.contains("[roof_in_room]"));
    assertEquals("The roof kept its shape", roofHeight, getPiece("Wing roof").getHeight(), 1f);

    // Finishes from the rooms, an opening and its header named by "last"
    summary = run("{\"action\":\"modify_room\",\"id\":\"R1\",\"wall_color\":\"#D5AC6B\"},"
        // A ceiling height and a wall color in the same command
        + "{\"action\":\"modify_room\",\"id\":\"R2\",\"ceiling_height\":220,\"wall_color\":\"#9FB8C8\"},"
        + "{\"action\":\"paint_rooms\",\"only\":\"unpainted\",\"color\":\"#EEEEEE\"},"
        + "{\"action\":\"add_opening\",\"wall\":\"W4\",\"center\":200,\"width\":120,\"bottom\":0,\"top\":210},"
        + "{\"action\":\"add_header\",\"opening\":\"last\",\"height\":25,\"thickness\":20}");
    assertTrue(summary, summary.contains("Added the header"));
    assertFalse(summary, summary.contains("failed"));
    assertNotNull(getPiece("Header"));
    assertEquals(220, Float.parseFloat(getRoom("Wing").getProperty("com.eteks.sweethome3d.ceilingHeight")), 0.01f);
    assertEquals("9FB8C8", getRoom("Wing").getProperty("com.eteks.sweethome3d.wallColor").replace("#", "").toUpperCase());
    assertEquals("D5AC6B", getRoom("Hall").getProperty("com.eteks.sweethome3d.wallColor").replace("#", "").toUpperCase());

    // A model imported outdoors while the upper level is selected stands on the lowest level
    assertEquals("Level 1", this.home.getSelectedLevel().getName());
    File model = new File("test/com/eteks/sweethome3d/junit/resources/test.obj");
    assertTrue(model.isFile());
    summary = run("{\"action\":\"import_furniture\",\"path\":\"" + model.getAbsolutePath().replace("\\", "\\\\")
        + "\",\"name\":\"Car\",\"x\":900,\"y\":200,\"length\":450,\"author\":\"Example Maker\",\"license\":\"CC BY\"}");
    HomePieceOfFurniture car = getPiece("Car");
    assertNotNull(summary, car);
    assertEquals(summary, groundLevel, car.getLevel().getName());
    assertEquals(0, car.getElevation(), 0.01f);
    summary = run("{\"action\":\"model_credits\"}");
    assertTrue(summary, summary.contains("\"Car\" by Example Maker, license CC BY"));

    // Everything since the roof is undone step by step
    for (int i = 0; i < 3; i++) {
      this.homeController.undo();
    }
    assertNotNull(getPiece("Wing roof"));
  }
}
