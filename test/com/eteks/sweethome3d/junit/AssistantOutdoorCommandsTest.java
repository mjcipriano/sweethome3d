/*
 * AssistantOutdoorCommandsTest.java 23 nov. 2026
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
import static org.junit.Assert.assertNotSame;
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
public class AssistantOutdoorCommandsTest {
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

  @Test
  public void testEveryStructureBuilds() {
    String [][] commands = {
        {"add_deck", "\"x\":0,\"y\":0,\"width\":500,\"depth\":350,\"height\":90,\"skirting\":\"lattice\",\"benches\":[1]", "Deck"},
        {"add_patio", "\"x\":0,\"y\":0,\"width\":500,\"depth\":400,\"pattern\":\"herringbone\",\"border\":{\"width\":25}", "Patio"},
        {"add_pavers", "\"points\":[[0,0],[300,0],[300,300],[0,300]],\"pattern\":\"flagstone\",\"name\":\"Pavers\"", "Pavers"},
        {"add_pergola", "\"x\":0,\"y\":0,\"slats\":true", "Pergola"},
        {"add_arbor", "\"x\":0,\"y\":0,\"arch\":true", "Arbor"},
        {"add_carport", "\"x\":0,\"y\":0,\"roof\":\"gable\"", "Carport"},
        {"add_gazebo", "\"x\":0,\"y\":0,\"sides\":6", "Gazebo"},
        {"add_shed", "\"x\":0,\"y\":0,\"roof\":\"gambrel\",\"window\":true", "Shed"},
        {"add_greenhouse", "\"x\":0,\"y\":0", "Greenhouse"},
        {"add_playset", "\"x\":0,\"y\":0,\"swings\":3", "Playset"},
        {"add_trampoline", "\"x\":0,\"y\":0,\"net\":true", "Trampoline"},
        {"add_pool", "\"x\":0,\"y\":0,\"width\":800,\"depth\":400,\"deck_width\":150", "Pool"},
        {"add_pool", "\"x\":0,\"y\":0,\"diameter\":450,\"type\":\"above_ground\",\"name\":\"Round pool\"", "Round pool"},
        {"add_pond", "\"x\":0,\"y\":0,\"diameter\":300", "Pond"},
        {"add_firepit", "\"x\":0,\"y\":0,\"seat_wall\":true", "Fire pit"},
        {"add_outdoor_kitchen", "\"x\":0,\"y\":0", "Outdoor kitchen"},
        {"add_raised_bed", "\"x\":0,\"y\":0,\"material\":\"stone\"", "Raised bed"},
        {"add_context_vehicle", "\"x\":0,\"y\":0,\"type\":\"pickup\"", "Pickup"},
        {"add_context_person", "\"x\":0,\"y\":0", "Person"}};
    for (String [] command : commands) {
      String summary = run("{\"action\":\"" + command [0] + "\"," + command [1] + "}");
      HomePieceOfFurniture piece = getPiece(command [2]);
      assertNotNull(command [0] + ": " + summary, piece);
      assertFalse(command [0] + ": " + summary, summary.contains("Couldn't") || summary.contains("Warning"));
      assertEquals("site", piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY));
      assertTrue(command [0], piece.getWidth() > 20 && piece.getHeight() > 0.4f);
      // What stands outdoors isn't furniture out of its room
      assertFalse(run("{\"action\":\"check_home\"}").contains("\"" + command [2] + "\""));
      this.homeController.undo();
      assertNull(command [0], getPiece(command [2]));
    }
    for (String type : new String [] {"ac_condenser", "generator", "meter", "propane_tank", "septic_marker", "well_marker",
                                      "utility_pole", "transformer_box", "mailbox", "street_light", "street_sign", "fire_hydrant"}) {
      String summary = run("{\"action\":\"add_utility\",\"type\":\"" + type + "\",\"x\":0,\"y\":0}");
      assertTrue(type + ": " + summary, summary.contains("Added the " + type.replace('_', ' ')));
    }
    String summary = run("{\"action\":\"add_utility\",\"type\":\"antenna\",\"x\":0,\"y\":0}");
    assertTrue(summary, summary.contains("\"type\" is one of"));
    summary = run("{\"action\":\"add_shed\",\"x\":0,\"y\":0,\"rof\":\"gable\"}");
    assertTrue(summary, summary.contains("add_shed ignored the key [rof]"));
    summary = run("{\"action\":\"add_shed\",\"roof\":\"gable\"}");
    assertTrue(summary, summary.contains("give its place"));
  }

  @Test
  public void testPlacesAndSizes() {
    // A shed turned by 90 degrees keeps its middle where it was asked
    String summary = run("{\"action\":\"add_shed\",\"x\":1000,\"y\":2000,\"angle\":90,\"width\":300,\"depth\":200,\"height\":210}");
    HomePieceOfFurniture shed = getPiece("Shed");
    assertEquals(1000, shed.getX(), 8);
    assertEquals(2000, shed.getY(), 8);
    assertEquals(90, Math.toDegrees(shed.getAngle()), 0.01);
    assertTrue(summary, summary.contains("300 x 200 cm"));
    // A deck with its railing open at its stairs
    summary = run("{\"action\":\"add_deck\",\"points\":[[0,0],[500,0],[500,300],[0,300]],\"height\":80,\"railing\":\"picket\","
        + "\"stairs\":{\"edge\":3,\"width\":120}}");
    assertTrue(summary, summary.contains("15 m2, its boards at 80 cm") && summary.contains("5 steps of 16 cm")
        && summary.contains("with the railing"));
    HomePieceOfFurniture railing = getPiece("Deck railing");
    assertNotNull(summary, railing);
    // The railing stands on the boards
    assertEquals(80, railing.getElevation(), 6);
    assertTrue(railing.getProperty("assistantFence"), railing.getProperty("assistantFence").contains("\"type\":\"gap\""));
    // Stairs that hold together: risers, stringers and a pad, out of the deck from its rim to the ground
    assertTrue(summary, summary.contains("on a run of 28 cm down from its edge 3, with risers, stringers, handrails and a pad at their foot"));
    HomePieceOfFurniture deck = getPiece("Deck");
    // 4 treads of 28 cm and a pad of 90 cm beyond the edge at y = 300, and nothing beyond the other sides
    assertEquals(summary, 300 + 4 * 28 + 90, deck.getY() + deck.getDepth() / 2, 2);
    assertEquals(summary, 0, deck.getY() - deck.getDepth() / 2, 2);
    assertEquals(summary, 500, deck.getWidth(), 2);
    // Without stairs the deck is only its boards
    summary = run("{\"action\":\"add_deck\",\"points\":[[0,1000],[500,1000],[500,1300],[0,1300]],\"height\":80,\"name\":\"Bare deck\"}");
    assertEquals(summary, 300, getPiece("Bare deck").getDepth(), 2);
    assertTrue(getPiece("Bare deck").getModel() != deck.getModel());

    // A shed with its doors: leaves that come out of the wall when they are open
    summary = run("{\"action\":\"add_shed\",\"x\":-3000,\"y\":0,\"width\":300,\"depth\":200,\"doors\":2,\"name\":\"Closed\"}");
    assertTrue(summary, summary.contains("2 doors 90 x 190 cm each on its front"));
    summary = run("{\"action\":\"add_shed\",\"x\":-3000,\"y\":1000,\"width\":300,\"depth\":200,\"doors\":2,\"open\":90,\"name\":\"Open\"}");
    assertTrue(summary, summary.contains("open at 90 degrees"));
    // Leaves of 90 cm turned to the front
    assertTrue(getPiece("Open").getDepth() + " " + getPiece("Closed").getDepth(), getPiece("Open").getDepth() > getPiece("Closed").getDepth() + 70);
    summary = run("{\"action\":\"add_shed\",\"x\":-3000,\"y\":2000,\"doors\":0,\"name\":\"Blind\"}");
    assertTrue(summary, summary.contains("0 doors on its front"));
    summary = run("{\"action\":\"add_shed\",\"x\":-3000,\"y\":3000,\"open\":200}");
    assertTrue(summary, summary.contains("from 0 to 170 degrees"));

    // Steps between two points given apart
    run("{\"action\":\"set_terrain\",\"points\":[[-9000,-9000,0],[-5000,-9000,0],[-5000,-5000,200],[-9000,-5000,200]]}");
    summary = run("{\"action\":\"add_steps_on_slope\",\"from\":[-7000,-8000],\"to\":[-7000,-6000]}");
    assertTrue(summary, summary.contains("Added") && !summary.contains("Couldn't") && !summary.contains("ignored"));
    summary = run("{\"action\":\"add_steps_on_slope\",\"from\":[-7000,-8000]}");
    assertTrue(summary, summary.contains("or \"from\":[x,y] and \"to\":[x,y]"));

    // A pool in the ground cuts the ground flat under it; above the ground, it stands in its wall with a ladder
    summary = run("{\"action\":\"add_pool\",\"x\":-7000,\"y\":-7000,\"width\":600,\"depth\":300,\"name\":\"Dug pool\"}");
    assertTrue(summary, summary.contains("The ground was cut flat under it and its coping: added a pad"));
    HomePieceOfFurniture pool = getPiece("Dug pool");
    // The ground is flat under it, at the 100 cm it had at the middle of the pool
    assertEquals(summary, 100, pool.getElevation() + pool.getHeight(), 12);
    summary = run("{\"action\":\"add_pool\",\"x\":-7000,\"y\":-8300,\"width\":600,\"depth\":300,\"excavate\":false,\"name\":\"On top\"}");
    assertTrue(summary, summary.contains("The ground was left as it is"));
    summary = run("{\"action\":\"add_pool\",\"x\":3000,\"y\":-7000,\"diameter\":450,\"above_ground\":true,\"name\":\"Tank\"}");
    assertTrue(summary, summary.contains("in a wall 130 cm above the ground, with a ladder") && !summary.contains("ignored"));
    // Its ladder goes over the wall
    assertTrue(summary, getPiece("Tank").getHeight() > 180);
    summary = run("{\"action\":\"add_pool\",\"x\":3000,\"y\":-9000,\"type\":\"lagoon\"}");
    assertTrue(summary, summary.contains("\"type\" is in_ground or above_ground"));
    // A driveway with its apron, and a garden in its fence
    summary = run("{\"action\":\"add_driveway\",\"points\":[[2000,0],[2000,1200]],\"width\":350,\"material\":\"asphalt\",\"apron\":120}");
    assertTrue(summary, summary.contains("12 m long and 350 cm wide, of asphalt") && summary.contains("with the apron"));
    summary = run("{\"action\":\"add_sidewalk\",\"points\":[[2400,0],[2400,1200]]},{\"action\":\"add_curb\",\"points\":[[2600,0],[2600,1200]]}");
    assertTrue(summary, summary.contains("Added the sidewalk") && summary.contains("Added the curb"));
    summary = run("{\"action\":\"add_garden_enclosure\",\"x\":4000,\"y\":4000,\"beds\":{\"rows\":2,\"columns\":3}}");
    assertTrue(summary, summary.contains("6 raised beds") && summary.contains("garden_wire_tpost") && summary.contains("with a gate"));
    summary = run("{\"action\":\"add_drainage\",\"type\":\"french_drain\",\"points\":[[0,3000],[800,3000]]},"
        + "{\"action\":\"add_drainage\",\"type\":\"catch_basin\",\"x\":900,\"y\":3000}");
    assertTrue(summary, summary.contains("Added the french drain") && summary.contains("8 m long") && summary.contains("Added the catch basin"));
    summary = run("{\"action\":\"add_callout\",\"text\":\"Septic tank\",\"x\":100,\"y\":4000,\"to\":[400,4300]}");
    assertTrue(summary, summary.contains("Added the callout \"Septic tank\"") && summary.contains("with the line"));
    assertEquals(1, this.home.getLabels().size());
  }

  @Test
  public void testSlopeAndContext() {
    // A slope of 2 m over 40 m
    run("{\"action\":\"set_terrain\",\"points\":[[0,0,0],[4000,0,200],[4000,4000,200],[0,4000,0]]}");
    // Steps up the slope: 1 m of climb over 20 m asks for 6 steps of 17 cm at most
    String summary = run("{\"action\":\"add_steps_on_slope\",\"points\":[[1000,500],[3000,500]],\"width\":150}");
    assertTrue(summary, summary.contains("6 steps of 16.67 cm") && summary.contains("climbing 100 cm"));
    summary = run("{\"action\":\"add_steps_on_slope\",\"points\":[[1000,500],[1000,900]]}");
    assertTrue(summary, summary.contains("no step is needed"));
    // A wall that holds a step of the ground
    summary = run("{\"action\":\"add_retaining_wall\",\"points\":[[2000,1500],[2000,3500]],\"height\":80,\"low_side\":\"left\"}");
    assertTrue(summary, summary.contains("Added the retaining wall") && summary.contains("cut down by 80 cm"));
    assertNotNull(getPiece("Retaining wall"));
    summary = run("{\"action\":\"spot_elevations\",\"points\":[[1900,2500],[2100,2500]],\"labels\":false}");
    // Walking down the plan, the left side is the east one: it went down
    assertTrue(summary, summary.contains("(1900, 2500) 95") && summary.contains("(2100, 2500) 25"));
    // A shed stands on the ground at its place
    run("{\"action\":\"add_shed\",\"x\":3000,\"y\":3000}");
    HomePieceOfFurniture shed = getPiece("Shed");
    float floor = shed.getLevel() != null ? shed.getLevel().getElevation() : 0;
    assertEquals(150, shed.getElevation() + floor, 2);

    // A bench tilted as the slope under it: 5 % towards the east, its front to the west at the angle 90
    run("{\"action\":\"add_furniture\",\"name\":\"Table\",\"x\":1000,\"y\":3000,\"angle\":90,\"name_override\":\"Bench\"}");
    HomePieceOfFurniture bench = getPiece("Bench");
    summary = run("{\"action\":\"set_elevation\",\"id\":\"" + HomeItemIds.getId(bench) + "\",\"on_terrain\":true,\"align_to_slope\":true}");
    // Its front looks down the slope: it goes down by atan(0.05)
    assertEquals(360 - 2.86, (Math.toDegrees(bench.getPitch()) + 360) % 360, 0.2);
    assertEquals(0, bench.getRoll(), 0.001f);
    this.homeController.undo();
    assertEquals(0, bench.getPitch(), 0.001f);
    // A finer part of the ground
    summary = run("{\"action\":\"add_terrain_patch\",\"points\":[[3000,200,150],[3400,200,170],[3400,600,170],[3000,600,150],[3200,400,260]]}");
    assertTrue(summary, summary.contains("Refined the ground") && summary.contains("5 points in place of 0"));
    summary = run("{\"action\":\"spot_elevations\",\"points\":[[3200,400]],\"labels\":false}");
    assertTrue(summary, summary.contains("(3200, 400) 260"));
    assertTrue(run("{\"action\":\"cut_fill_report\"}").contains("m3 cut"));
    // A hedge of a species, and a bed of mulch with an edging
    summary = run("{\"action\":\"add_hedge\",\"points\":[[0,3800],[800,3800]],\"species\":\"boxwood\",\"pruned_shape\":\"box\",\"height\":90}");
    assertTrue(summary, summary.contains("Added the hedge") && !summary.contains("Warning"));
    summary = run("{\"action\":\"add_groundcover\",\"points\":[[0,3500],[600,3500],[600,3700],[0,3700]],\"material\":\"pine_straw\","
        + "\"edging\":\"steel\"}");
    assertTrue(summary, summary.contains("Added") && !summary.contains("Couldn't"));
    summary = run("{\"action\":\"add_groundcover\",\"points\":[[0,3500],[600,3500],[600,3700]],\"material\":\"lava\"}");
    assertTrue(summary, summary.contains("\"material\" is pine_straw"));

    // The house of a neighbor, hidden with the others by its tag
    summary = run("{\"action\":\"add_context_building\",\"points\":[[5000,0],[6200,0],[6200,900],[5000,900]],\"stories\":2,"
        + "\"roof\":\"hip\",\"siding\":\"brick\",\"owner\":\"1232 Fernwood\",\"garage_doors\":[{\"edge\":3,\"at\":0.3}]}");
    assertTrue(summary, summary.contains("108 m2 on the ground, walls 590 cm high") && summary.contains("under the hip roof")
        && summary.contains("1 garage door"));
    HomePieceOfFurniture house = getPiece("Neighbor house");
    assertTrue(house.getProperty("tags"), house.getProperty("tags").contains("neighbor"));
    assertNotNull(getPiece("Neighbor house roof"));
    run("{\"action\":\"hide_layer\",\"tag\":\"neighbor\"}");
    assertFalse(house.isVisible());
    assertFalse(getPiece("Neighbor house roof").isVisible());
    // The roof starts on the top of the walls, unless it's given eaves
    HomePieceOfFurniture houseRoof = getPiece("Neighbor house roof");
    assertEquals(summary, house.getElevation() + house.getHeight(), houseRoof.getElevation(), 1);
    assertTrue(summary, summary.contains("on the top of the walls"));
    summary = run("{\"action\":\"add_context_building\",\"points\":[[5000,3000],[6200,3000],[6200,3900],[5000,3900]],"
        + "\"name\":\"Eaves\",\"overhang\":40,\"detail\":\"detailed\"}");
    assertTrue(summary, summary.contains("its eaves 40 cm out hang under the top of the walls"));
    assertTrue(getPiece("Eaves roof").getElevation() < getPiece("Eaves").getElevation() + getPiece("Eaves").getHeight() - 5);

    // The detail of the home draws again the buildings that exist, at their place and with their id
    String houseId = HomeItemIds.getId(house);
    float houseX = house.getX();
    float houseHeight = house.getHeight();
    Object houseModel = house.getModel();
    int count = this.home.getFurniture().size();
    summary = run("{\"action\":\"set_context_detail\",\"detail\":\"massing\"}");
    assertTrue(summary, summary.contains("1 building of the home was drawn again without roof")
        && summary.contains("1 given its own \"detail\" kept it"));
    assertEquals(houseId, HomeItemIds.getId(getPiece("Neighbor house")));
    assertNotSame(houseModel, house.getModel());
    assertEquals(houseX, house.getX(), 1);
    assertEquals(houseHeight, house.getHeight(), 1);
    assertNull(getPiece("Neighbor house roof"));
    assertNotNull(getPiece("Eaves roof"));
    assertEquals(count - 1, this.home.getFurniture().size());
    summary = run("{\"action\":\"add_context_building\",\"x\":8000,\"y\":0,\"width\":1000,\"depth\":800,\"name\":\"Mass\"}");
    assertTrue(summary, summary.contains("(massing)") && summary.contains("under a flat roof"));
    // Back to more detail: the roofs come back
    summary = run("{\"action\":\"set_context_detail\",\"detail\":\"textured\"}");
    assertTrue(summary, summary.contains("2 buildings of the home were drawn again"));
    assertNotNull(getPiece("Neighbor house roof"));
    assertNotNull(getPiece("Mass roof"));
    summary = run("{\"action\":\"set_context_detail\",\"detail\":\"fancy\"}");
    assertTrue(summary, summary.contains("massing, textured or detailed"));
  }
}
