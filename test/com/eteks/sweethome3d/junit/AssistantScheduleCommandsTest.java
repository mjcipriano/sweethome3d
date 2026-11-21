/*
 * AssistantScheduleCommandsTest.java 21 nov. 2026
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;
import com.eteks.sweethome3d.viewcontroller.HomeItemTags;
import com.eteks.sweethome3d.viewcontroller.HomeReport;

/**
 * Tests the schedules of doors, windows, rooms and symbols, the electrical and plumbing symbols,
 * the framing estimate, and the report of a home with openings deducted and prices by room and wall.
 */
public class AssistantScheduleCommandsTest {
  private String                   previousNo3D;
  private Home                     home;
  private UserPreferences          preferences;
  private HomeController           homeController;
  private AssistantCommandExecutor executor;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.home = new Home(250);
    this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    Content dummy = new com.eteks.sweethome3d.tools.URLContent(new File("dummy.png").toURI().toURL());
    // A door of 90 x 205 cm and a window of 120 x 100 cm whose sill is 90 cm high
    this.preferences.getFurnitureCatalog().add(new com.eteks.sweethome3d.model.FurnitureCategory("Test openings"),
        new com.eteks.sweethome3d.model.CatalogDoorOrWindow(null, "Zz door", null, dummy, dummy, 90, 10, 205, 0, true,
            1, 0, new com.eteks.sweethome3d.model.Sash [0], null, null, true, null, null));
    this.preferences.getFurnitureCatalog().add(new com.eteks.sweethome3d.model.FurnitureCategory("Test openings"),
        new com.eteks.sweethome3d.model.CatalogDoorOrWindow(null, "Zz window", null, dummy, dummy, 120, 10, 100, 90, true,
            1, 0, new com.eteks.sweethome3d.model.Sash [0], null, null, true, null, null));
    this.homeController = new HomeController(this.home, this.preferences, new SwingViewFactory());
    this.homeController.getView();
    this.executor = new AssistantCommandExecutor(this.home, this.preferences, this.homeController);
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
    return this.executor.execute(AssistantCommandParser.parse("{\"commands\":[" + commands + "]}").getCommands());
  }

  /**
   * A room of 5 m x 4 m inside walls 10 cm thick and 250 cm high: the middle lines of its walls
   * are 510 and 410 cm long, the first one along its top side from (-5, -5) to (505, -5).
   */
  private Room addLiving() {
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Living\",\"x\":0,\"y\":0,\"width\":500,\"depth\":400,"
        + "\"thickness\":10,\"height\":250}");
    return this.home.getRooms().get(this.home.getRooms().size() - 1);
  }

  private String id(Object item) {
    HomeItemIds.ensureIds(this.home);
    return HomeItemIds.getId(item);
  }

  private List<HomePieceOfFurniture> getSymbols(String type) {
    List<HomePieceOfFurniture> symbols = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (type.equals(piece.getProperty("symbolType"))) {
        symbols.add(piece);
      }
    }
    return symbols;
  }

  @Test
  public void testReportDeductsOpenings() {
    addLiving();
    // A door of 90 x 205 cm in the middle of the top wall
    run("{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":250,\"y\":-5}");
    String report = HomeReport.describe(this.home);
    // 18.4 m of walls 2.5 m high: 92 m2 on both sides, less 2 x 0.9 x 2.05 = 3.69 m2
    assertTrue(report, report.contains("4 walls, 18.4 m long, 92.0 m2 of wall surface on both sides, "
        + "3.7 m2 of openings, 88.3 m2 net"));
    assertTrue(report, report.contains("- Total: floor 20.0 m2, walls 18.4 m long, 92.0 m2 of wall surface on both sides, "
        + "3.7 m2 of openings of doors and windows deducted, 88.3 m2 net"));
    // The four wall sides that face the room: 18 m x 2.5 m = 45 m2, less 1.845 m2
    assertTrue(report, report.contains("- Living: 20.0 m2, perimeter 18.0 m, walls 43.2 m2 net (1.8 m2 of openings deducted)"));
    // The paint is the one of the net surface
    run("{\"action\":\"set_unit_prices\",\"paint\":10}");
    report = HomeReport.describe(this.home);
    assertTrue(report, report.contains("Wall paint or covering, per m2 of wall side: 88.3 m2 x 10.0 = 883.00"));

    // A window whose top is above the wall is clipped to it, and a door away from any wall deducts nothing
    this.home.getWalls().iterator().next().setHeight(150f);
    run("{\"action\":\"add_door_or_window\",\"name\":\"Zz window\",\"x\":100,\"y\":-5},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":250,\"y\":200}");
    report = HomeReport.describe(this.home);
    // Top wall 5.1 m x 1.5 m, the others 2.5 m high: (7.65 + 10.25 + 12.75 + 10.25) x 2 = 81.8 m2;
    // door 0.9 x 1.5 and window 1.2 x (1.5 - 0.9) on both sides: 4.14 m2
    assertTrue(report, report.contains("81.8 m2 of wall surface on both sides, 4.1 m2 of openings, 77.7 m2 net"));
  }

  @Test
  public void testUnitPricesOfRoomsAndWalls() {
    Room living = addLiving();
    run("{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\",\"x\":600,\"y\":0,\"width\":300,\"depth\":400,"
        + "\"thickness\":10,\"height\":250}");
    run("{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":250,\"y\":-5}");
    Wall topWall = this.home.getWalls().iterator().next();
    String livingId = id(living);
    String wallId = id(topWall);
    run("{\"action\":\"set_unit_prices\",\"flooring\":20,\"paint\":10,\"currency\":\"EUR\"}");
    String report = HomeReport.describe(this.home);
    assertTrue(report, report.contains("Flooring, per m2 of floor: 32.0 m2 x 20.0 = 640.00 EUR"));

    // The living room gets its own flooring
    String summary = run("{\"action\":\"set_unit_prices\",\"room\":\"" + livingId + "\",\"flooring\":50}");
    assertTrue(summary, summary.contains(livingId + ": flooring 50.0"));
    assertEquals("50.0", HomeReport.getItemUnitPrice(living, "flooring").toPlainString());
    // The price of the home didn't change
    assertEquals("20.0", HomeReport.getUnitPrice(this.home, "flooring").toPlainString());
    report = HomeReport.describe(this.home);
    assertTrue(report, report.contains("Flooring, per m2 of floor: 12.0 m2 x 20.0 = 240.00 EUR"));
    assertTrue(report, report.contains("- Living (" + livingId + "), own price: 20.0 m2 x 50.0 = 1000.00 EUR"));
    assertTrue(report, report.contains("[own prices: flooring 50.0]"));
    // Undone as one step
    this.homeController.undo();
    assertNull(HomeReport.getItemUnitPrice(living, "flooring"));
    report = HomeReport.describe(this.home);
    assertTrue(report, report.contains("Flooring, per m2 of floor: 32.0 m2 x 20.0 = 640.00 EUR"));
    this.homeController.redo();
    assertEquals("50.0", HomeReport.getItemUnitPrice(living, "flooring").toPlainString());

    // The side of the top wall that faces the living room: 5.1 x 2.5 - 0.9 x 2.05 = 10.905 m2
    summary = run("{\"action\":\"set_unit_prices\",\"ids\":[\"" + wallId + "\"],\"paint\":30,\"side\":\"right\",\"flooring\":5}");
    assertTrue(summary, summary.contains(wallId + " right side: paint 30.0"));
    assertTrue(summary, summary.contains("flooring ignored for walls"));
    report = HomeReport.describe(this.home);
    assertTrue(report, report.contains("- " + wallId + ", right side, own price: 10.9 m2 x 30.0 = 327.00 EUR"));
    assertTrue(report, report.contains("- Walls with their own price: " + wallId + ": paint right 30.0"));
    // The paint of the living room: its wall sides less the one of the top wall, 10 + 12.5 + 10 = 32.5 m2
    run("{\"action\":\"set_unit_prices\",\"room\":\"Living\",\"paint\":12}");
    report = HomeReport.describe(this.home);
    assertTrue(report, report.contains("- Living (" + livingId + "), own price: 32.5 m2 x 12.0 = 390.00 EUR"));
    // Walls of the two rooms: (5.1 + 4.1) x 2 + (3.1 + 4.1) x 2 = 32.8 m, 164 m2 on both sides, less 3.69 m2 of door,
    // less 10.905 m2 and 32.5 m2 that have their own price
    assertTrue(report, report.contains("Wall paint or covering, per m2 of wall side: 116.9 m2 x 10.0 = 1169.00 EUR"));

    // Back to the prices of the home
    summary = run("{\"action\":\"set_unit_prices\",\"clear\":true}");
    assertTrue(summary, summary.contains("back to the prices of the home"));
    assertNull(HomeReport.getItemUnitPrice(living, "paint"));
    assertNull(HomeReport.getItemUnitPrice(topWall, "paint.right"));
    report = HomeReport.describe(this.home);
    assertFalse(report, report.contains("own price"));
    assertEquals("10.0", HomeReport.getUnitPrice(this.home, "paint").toPlainString());

    summary = run("{\"action\":\"set_unit_prices\",\"room\":\"R99\",\"paint\":12}");
    assertTrue(summary, summary.contains("no room and no wall is designated"));
  }

  @Test
  public void testDoorAndWindowSchedules() {
    addLiving();
    run("{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":250,\"y\":-5},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":250,\"y\":405},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":-5,\"y\":200,\"angle\":90,\"width\":80},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Zz window\",\"x\":505,\"y\":200,\"angle\":90}");
    List<HomePieceOfFurniture> furniture = this.home.getFurniture();
    String summary = run("{\"action\":\"door_schedule\"}");
    assertTrue(summary, summary.contains("Door schedule (cm):\nMark | Type | Name | Width | Height | Sill height | Count | Levels | Ids"));
    // The doors of the same name and size share a mark, the narrowest first
    assertTrue(summary, summary.contains("D1 | door | Zz door | 80 | 205 | 0 | 1 | - | " + id(furniture.get(2))));
    assertTrue(summary, summary.contains("D2 | door | Zz door | 90 | 205 | 0 | 2 | - | " + id(furniture.get(0)) + " " + id(furniture.get(1))));
    assertTrue(summary, summary.contains("Total: 3 doors of 2 types"));
    assertFalse(summary, summary.contains("Zz window"));
    summary = run("{\"action\":\"window_schedule\"}");
    assertTrue(summary, summary.contains("W1 | window | Zz window | 120 | 100 | 90 | 1 | - | " + id(furniture.get(3))));
    assertFalse(summary, summary.contains("Zz door"));
    summary = run("{\"action\":\"schedule\",\"type\":\"doors_and_windows\"}");
    assertTrue(summary, summary.contains("Total: 3 doors of 2 types, 1 window of 1 type"));

    // Marks written in the pieces and in the plan
    summary = run("{\"action\":\"schedule\",\"type\":\"doors_and_windows\",\"tag_marks\":true,\"labels\":true}");
    assertTrue(summary, summary.contains("Wrote the mark of 4 pieces"));
    assertEquals("D2", furniture.get(0).getProperty("scheduleMark"));
    assertEquals("D1", furniture.get(2).getProperty("scheduleMark"));
    assertEquals("W1", furniture.get(3).getProperty("scheduleMark"));
    assertEquals(4, this.home.getLabels().size());
    // The label of the door of the top wall is in front of it
    com.eteks.sweethome3d.model.Label doorLabel = null;
    for (com.eteks.sweethome3d.model.Label label : this.home.getLabels()) {
      if (id(furniture.get(0)).equals(label.getProperty("scheduleMarkOf"))) {
        doorLabel = label;
      }
    }
    assertEquals("D2", doorLabel.getText());
    assertEquals(250f, doorLabel.getX(), 0.01f);
    assertEquals(25f, doorLabel.getY(), 0.01f);
    this.homeController.undo();
    assertNull(furniture.get(0).getProperty("scheduleMark"));
    assertEquals(0, this.home.getLabels().size());
    // Written twice, the labels are replaced
    run("{\"action\":\"door_schedule\",\"tag_marks\":true,\"labels\":true}");
    run("{\"action\":\"door_schedule\",\"tag_marks\":true,\"labels\":true}");
    assertEquals(3, this.home.getLabels().size());

    summary = run("{\"action\":\"door_schedule\",\"level\":\"Nowhere\"}");
    assertTrue(summary, summary.contains("no level has the id or the name \"Nowhere\""));
    summary = run("{\"action\":\"schedule\",\"type\":\"cats\"}");
    assertTrue(summary, summary.contains("\"type\" is doors, windows"));
  }

  @Test
  public void testRoomScheduleAndCsv() throws Exception {
    Room living = addLiving();
    run("{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":250,\"y\":-5,\"width\":80}");
    String summary = run("{\"action\":\"room_schedule\"}");
    // 45 m2 of wall sides less 0.8 x 2.05 = 1.64 m2
    assertTrue(summary, summary.contains(id(living) + " | Living | - | 20 | 18 | 250 | default | 43.36"));
    assertTrue(summary, summary.contains("Total: 1 room, 20 m2"));

    living.setName("Bed, \"blue\"");
    living.setFloorColor(0xD9C3A0);
    File folder = Files.createTempDirectory("schedules").toFile();
    File file = new File(folder, "rooms.csv");
    try {
      summary = run("{\"action\":\"export_schedule\",\"type\":\"rooms\",\"path\":\"" + file.getPath().replace("\\", "\\\\") + "\"}");
      assertTrue(summary, summary.contains("Wrote the room schedule in " + file));
      assertEquals("Id,Name,Level,Area (m2),Perimeter (m),Ceiling height (cm),Floor finish,Net wall surface (m2)\r\n"
          + id(living) + ",\"Bed, \"\"blue\"\"\",-,20,18,250,#D9C3A0,43.36\r\n",
          new String(Files.readAllBytes(file.toPath()), "UTF-8"));
      // An existing file is kept
      summary = run("{\"action\":\"export_schedule\",\"type\":\"doors\",\"path\":\"" + file.getPath().replace("\\", "\\\\") + "\"}");
      assertTrue(summary, summary.contains("exists; give \"replace\":true"));
      assertTrue(new String(Files.readAllBytes(file.toPath()), "UTF-8").startsWith("Id,Name"));
      summary = run("{\"action\":\"export_schedule\",\"type\":\"doors\",\"replace\":true,\"path\":\""
          + file.getPath().replace("\\", "\\\\") + "\"}");
      assertTrue(summary, summary.contains("Wrote the door schedule"));
      assertEquals("Mark,Type,Name,Width,Height,Sill height,Count,Levels,Ids\r\n"
          + "D1,door,Zz door,80,205,0,1,-," + id(this.home.getFurniture().get(0)) + "\r\n",
          new String(Files.readAllBytes(file.toPath()), "UTF-8"));
      // A relative path and another kind of file are refused
      summary = run("{\"action\":\"export_schedule\",\"type\":\"doors\",\"path\":\"doors.csv\"}");
      assertTrue(summary, summary.contains("must be an absolute path"));
      assertFalse(new File("doors.csv").exists());
      summary = run("{\"action\":\"export_schedule\",\"type\":\"doors\",\"path\":\""
          + new File(folder, "doors.txt").getPath().replace("\\", "\\\\") + "\"}");
      assertTrue(summary, summary.contains("must be the path of a .csv file"));
      summary = run("{\"action\":\"export_schedule\",\"type\":\"doors\"}");
      assertTrue(summary, summary.contains("give the absolute \"path\""));
    } finally {
      file.delete();
      folder.delete();
    }
  }

  @Test
  public void testSymbols() {
    Room living = addLiving();
    List<Wall> walls = new ArrayList<Wall>(this.home.getWalls());
    // 105 cm from the start (-5, -5) of the top wall, on the side of the room, which is its right side
    String summary = run("{\"action\":\"add_symbol\",\"type\":\"outlet\",\"wall\":\"" + id(walls.get(0))
        + "\",\"offset\":105,\"circuit\":\"C1\"}");
    assertTrue(summary, summary.contains("on the right side of the wall " + id(walls.get(0)) + ", the side of a room, 105 cm from its start"));
    assertTrue(summary, summary.contains("its center 30 cm above the floor"));
    HomePieceOfFurniture outlet = getSymbols("outlet").get(0);
    assertEquals(100f, outlet.getX(), 0.01f);
    // Against the face of the wall at y = 0: a symbol is 12 cm deep in the plan
    assertEquals(6f, outlet.getY(), 0.01f);
    assertEquals(0f, outlet.getAngle(), 0.001f);
    assertEquals(16f, outlet.getWidth(), 0.01f);
    assertEquals(12f, outlet.getDepth(), 0.01f);
    assertEquals(30f, outlet.getElevation() + outlet.getHeight() / 2, 0.01f);
    assertEquals(11.5f, outlet.getHeight(), 0.01f);
    assertEquals("C1", outlet.getProperty("symbolCircuit"));
    assertTrue(HomeItemTags.hasTag(outlet, "electrical"));
    assertNotNull(outlet.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY));
    assertNotNull(outlet.getPlanIcon());
    assertEquals("Outlet", outlet.getName());

    // A switch on the left wall, which goes up from (-5, 405): its face is at x = 0 and its front looks at +x
    summary = run("{\"action\":\"add_symbol\",\"type\":\"switch\",\"wall\":\"" + id(walls.get(3)) + "\",\"offset\":205,\"label\":\"S1\"}");
    HomePieceOfFurniture lightSwitch = getSymbols("switch").get(0);
    assertEquals(summary, 6f, lightSwitch.getX(), 0.01f);
    assertEquals(200f, lightSwitch.getY(), 0.01f);
    assertEquals(1, -Math.sin(lightSwitch.getAngle()), 0.001);
    assertEquals(0, Math.cos(lightSwitch.getAngle()), 0.001);
    assertEquals(110f, lightSwitch.getElevation() + lightSwitch.getHeight() / 2, 0.01f);
    assertEquals("S1", lightSwitch.getName());
    assertTrue(lightSwitch.isNameVisible());
    // The other side when asked, at another height
    run("{\"action\":\"add_symbol\",\"type\":\"hose_bib\",\"wall\":\"" + id(walls.get(0)) + "\",\"offset\":255,\"side\":\"left\",\"height\":60}");
    HomePieceOfFurniture hoseBib = getSymbols("hose_bib").get(0);
    assertEquals(250f, hoseBib.getX(), 0.01f);
    assertEquals(-16f, hoseBib.getY(), 0.01f);
    assertEquals(-1, Math.cos(hoseBib.getAngle()), 0.001);
    assertEquals(60f, hoseBib.getElevation() + hoseBib.getHeight() / 2, 0.01f);
    assertTrue(HomeItemTags.hasTag(hoseBib, "plumbing"));
    // Under the ceiling and on the floor
    summary = run("{\"action\":\"add_symbol\",\"type\":\"ceiling_light\",\"x\":250,\"y\":200},"
        + "{\"action\":\"add_symbol\",\"type\":\"floor_drain\",\"x\":400,\"y\":300}");
    HomePieceOfFurniture light = getSymbols("ceiling_light").get(0);
    assertEquals(250f, light.getElevation() + light.getHeight(), 0.01f);
    assertEquals(250f, light.getX(), 0.01f);
    assertEquals(0f, getSymbols("floor_drain").get(0).getElevation(), 0.01f);
    assertTrue(summary, summary.contains("its top 250 cm above the floor"));

    // Symbols aren't furniture out of its place, and the report counts them on their own
    summary = run("{\"action\":\"check_home\"}");
    assertFalse(summary, summary.contains("Outlet"));
    assertFalse(summary, summary.contains("Hose bib"));
    String report = HomeReport.describe(this.home);
    assertTrue(report, report.contains("- Furniture: 0 pieces, 0 doors and windows"));
    assertTrue(report, report.contains("- Electrical and plumbing symbols: 5 (1 x ceiling_light, 1 x floor_drain, 1 x hose_bib, "
        + "1 x outlet, 1 x switch)"));
    assertFalse(report, report.contains("Building and site"));

    summary = run("{\"action\":\"symbol_schedule\"}");
    assertTrue(summary, summary.contains("Type | Category | Count | Rooms | Circuits | Levels | Ids"));
    assertTrue(summary, summary.contains("outlet | electrical | 1 | Living | C1 | - | " + id(outlet)));
    assertTrue(summary, summary.contains("hose_bib | plumbing | 1 | no room |  | - | " + id(hoseBib)));
    assertTrue(summary, summary.contains("By room: Living: 4 (1 outlet, 1 switch, 1 ceiling_light, 1 floor_drain); no room: 1 (1 hose_bib)"));
    assertTrue(summary, summary.contains("By circuit: C1: 1 (1 outlet); no circuit: 4 ("));
    assertTrue(summary, summary.contains("Total: 5 symbols"));
    assertTrue(run("{\"action\":\"schedule\",\"type\":\"symbols\"}").contains("Total: 5 symbols"));
    // Furniture schedules leave them out
    assertTrue(run("{\"action\":\"schedule\",\"type\":\"furniture\"}").contains("Furniture schedule (cm): none"));

    summary = run("{\"action\":\"add_symbol\",\"type\":\"jacuzzi\",\"x\":0,\"y\":0}");
    assertTrue(summary, summary.contains("unknown type \"jacuzzi\"; types: outlet, double_outlet, switch"));
    summary = run("{\"action\":\"add_symbol\",\"type\":\"outlet\"}");
    assertTrue(summary, summary.contains("give \"wall\":\"W#\" and \"offset\", or \"x\" and \"y\""));
    summary = run("{\"action\":\"add_symbol\",\"type\":\"outlet\",\"wall\":\"" + id(walls.get(0)) + "\",\"offset\":900}");
    assertTrue(summary, summary.contains("\"offset\" goes from 0 to 510 cm"));
    summary = run("{\"action\":\"list_symbols\"}");
    assertTrue(summary, summary.contains("- water_heater: Water heater, plumbing, on the floor"));
    assertTrue(summary, summary.contains("- thermostat: Thermostat, electrical, on a wall, center at 150"));
    assertEquals(5, this.home.getFurniture().size());
    assertTrue(living.containsPoint(outlet.getX(), outlet.getY(), 0));
  }

  @Test
  public void testSymbolsAlongTheWallsOfARoom() {
    Room living = addLiving();
    // A door from x = 95 to x = 185 in the top wall
    run("{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":140,\"y\":-5},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Zz window\",\"x\":375,\"y\":405}");
    String summary = run("{\"action\":\"add_symbols\",\"room\":\"" + id(living) + "\",\"type\":\"outlet\",\"circuit\":\"C2\"}");
    // Two outlets on each wall: the sides of 500 and 400 cm are longer than 360 cm
    List<HomePieceOfFurniture> outlets = getSymbols("outlet");
    assertEquals(summary, 8, outlets.size());
    assertTrue(summary, summary.contains("Added 8 electrical symbols outlet along the walls of the room " + id(living) + " \"Living\""));
    assertTrue(summary, summary.contains("1 moved beside a door or a window"));
    // The one at x = 125 was in the door: it stands 20 cm before it; the window above the one at x = 375 keeps it
    float [][] expected = {{75, 6}, {375, 6}, {494, 100}, {494, 300}, {375, 394}, {125, 394}, {6, 300}, {6, 100}};
    for (float [] point : expected) {
      boolean found = false;
      for (HomePieceOfFurniture outlet : outlets) {
        found |= Math.abs(outlet.getX() - point [0]) < 0.01f && Math.abs(outlet.getY() - point [1]) < 0.01f;
      }
      assertTrue("Outlet at " + point [0] + ", " + point [1] + " in " + summary, found);
    }
    for (HomePieceOfFurniture outlet : outlets) {
      assertEquals(30f, outlet.getElevation() + outlet.getHeight() / 2, 0.01f);
      assertEquals("C2", outlet.getProperty("symbolCircuit"));
      assertTrue(living.containsPoint(outlet.getX(), outlet.getY(), 0));
    }
    // Their fronts look at the room
    for (HomePieceOfFurniture outlet : outlets) {
      float frontX = outlet.getX() - (float)Math.sin(outlet.getAngle()) * 50;
      float frontY = outlet.getY() + (float)Math.cos(outlet.getAngle()) * 50;
      assertTrue(living.containsPoint(frontX, frontY, 0));
    }
    summary = run("{\"action\":\"symbol_schedule\"}");
    assertTrue(summary, summary.contains("outlet | electrical | 8 | Living | C2 | - | "));
    // One step of undo
    this.homeController.undo();
    assertEquals(0, getSymbols("outlet").size());
    assertEquals(2, this.home.getFurniture().size());

    // One every 2 m: 3 on the sides of 5 m, 2 on the ones of 4 m
    run("{\"action\":\"add_symbols\",\"room\":\"Living\",\"type\":\"data\",\"spacing\":200}");
    assertEquals(10, getSymbols("data").size());
    summary = run("{\"action\":\"add_symbols\",\"room\":\"Nowhere\"}");
    assertTrue(summary, summary.contains("no room has the id or the name \"Nowhere\""));
  }

  @Test
  public void testFramingEstimate() {
    // A wall of 4 m, 2.5 m high, with a door of 90 cm
    run("{\"action\":\"add_wall\",\"x1\":0,\"y1\":1000,\"x2\":400,\"y2\":1000,\"thickness\":10,\"height\":250},"
        + "{\"action\":\"add_door_or_window\",\"name\":\"Zz door\",\"x\":200,\"y\":1000}");
    Wall wall = this.home.getWalls().iterator().next();
    String summary = run("{\"action\":\"framing_estimate\"}");
    assertTrue(summary, summary.contains("not a structural design"));
    // ceil(400 / 40.64) + 1 = 11 studs, and 2 for the door
    assertTrue(summary, summary.contains("- Studs: 13 pieces of 2x4, 32.5 m (107 ft) in all"));
    assertTrue(summary, summary.contains("2 more for each of the 1 opening and for each of the 0 corners"));
    assertTrue(summary, summary.contains("- Plates: 12 m (39 ft) of 2x4 (3 along each wall)"));
    // 90 + 2 x 7.6 = 105.2 cm
    assertTrue(summary, summary.contains("- Headers: 1 header, 1.1 m (3 ft) long in all"));
    // (4 x 2.5 - 0.9 x 2.05) x 2 = 16.31 m2
    assertTrue(summary, summary.contains("- Sheathing and drywall: 16.31 m2 (176 sq ft) on the two sides of the walls"));
    // 2 x 4 / 12 = 0.667 board foot by foot: (32.5 + 12) m = 146 ft -> 97; headers 2 x 8 / 12 x 2 x 3.45 ft -> 9
    assertTrue(summary, summary.contains("- Board feet: 97 of 2x4 (studs and plates) and 9 of 2x8 (headers), 107 in all"));
    assertTrue(summary, summary.contains("With 10 % of waste: 15 studs"));
    assertTrue(summary, summary.contains("- " + id(wall) + ": 4 m long, 2.5 m high, 13 studs, 1 opening, 1.05 m of header"));

    // The four walls of a room, 510 and 410 cm long: 14 + 12 + 14 + 12 studs and 2 at each of the 4 corners
    addLiving();
    List<Wall> walls = new ArrayList<Wall>(this.home.getWalls());
    String ids = "\"" + id(walls.get(1)) + "\",\"" + id(walls.get(2)) + "\",\"" + id(walls.get(3)) + "\",\"" + id(walls.get(4)) + "\"";
    summary = run("{\"action\":\"framing_estimate\",\"ids\":[" + ids + "]}");
    assertTrue(summary, summary.contains("Framing estimate of 4 straight walls, 18.4 m (60 ft) long"));
    assertTrue(summary, summary.contains("- Studs: 60 pieces of 2x4, 150 m (492 ft) in all"));
    assertTrue(summary, summary.contains("for each of the 4 corners"));
    assertTrue(summary, summary.contains("- Plates: 55.2 m (181 ft) of 2x4"));
    // Every 24 in with 2x6 and two plates: 10 + 8 + 10 + 8 + 8
    summary = run("{\"action\":\"framing_estimate\",\"ids\":[" + ids + "],\"stud_spacing\":60.96,\"stud\":\"2x6\",\"plates\":2,\"waste\":0}");
    assertTrue(summary, summary.contains("- Studs: 44 pieces of 2x6, 110 m (361 ft) in all"));
    assertTrue(summary, summary.contains("- Plates: 36.8 m (121 ft) of 2x6 (2 along each wall)"));
    assertFalse(summary, summary.contains("of waste"));
    summary = run("{\"action\":\"framing_estimate\",\"stud\":\"big\"}");
    assertTrue(summary, summary.contains("\"stud\" is a size of lumber like 2x4 or 2x6"));
    summary = run("{\"action\":\"framing_estimate\",\"ids\":[\"R1\"]}");
    assertTrue(summary, summary.contains("no wall is designated"));
  }

  @Test
  public void testReferenceListsTheCommands() {
    String reference = HomeAssistantContext.getCommandReference();
    for (String action : new String [] {"door_schedule", "window_schedule", "room_schedule", "schedule", "export_schedule",
                                        "add_symbol", "add_symbols", "symbol_schedule", "list_symbols", "framing_estimate"}) {
      assertTrue(action, reference.contains("  {\"action\":\"" + action + "\""));
    }
  }
}
