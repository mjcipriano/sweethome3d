/*
 * AssistantGisCommandsTest.java 23 nov. 2026
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
public class AssistantGisCommandsTest {
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

  private static java.io.File write(String suffix, byte [] content) throws Exception {
    java.io.File file = java.io.File.createTempFile("gis", suffix);
    file.deleteOnExit();
    java.io.FileOutputStream out = new java.io.FileOutputStream(file);
    out.write(content);
    out.close();
    return file;
  }

  private static String path(java.io.File file) {
    return file.getPath().replace("\\", "\\\\");
  }

  private int count(String garden) {
    int count = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (garden.equals(piece.getProperty("assistantGarden"))) {
        count++;
      }
    }
    return count;
  }

  @Test
  public void testGeoJsonRulesAndKml() throws Exception {
    // About 100 m is 0.0009 degree of latitude and 0.00108 degree of longitude at this place
    String geojson = "{\"type\":\"FeatureCollection\",\"features\":["
        + "{\"type\":\"Feature\",\"properties\":{\"kind\":\"house\",\"HEIGHT\":7.5,\"name\":\"1232 Fernwood\"},\"geometry\":{\"type\":\"Polygon\","
        + "\"coordinates\":[[[-84.3880,33.7490],[-84.3879,33.7490],[-84.3879,33.7491],[-84.3880,33.7491],[-84.3880,33.7490]]]}},"
        + "{\"type\":\"Feature\",\"properties\":{\"kind\":\"fence\",\"material\":\"chain\"},\"geometry\":{\"type\":\"LineString\","
        + "\"coordinates\":[[-84.3882,33.7489],[-84.3877,33.7489]]}},"
        + "{\"type\":\"Feature\",\"properties\":{\"kind\":\"tree\",\"species\":\"White oak\",\"h\":18},\"geometry\":{\"type\":\"Point\","
        + "\"coordinates\":[-84.3881,33.7492]}},"
        + "{\"type\":\"Feature\",\"properties\":{\"name\":\"Well\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[-84.3878,33.7492]}}]}";
    String summary = run("{\"action\":\"import_geojson\",\"geojson\":" + geojson + "}");
    assertTrue(summary, summary.contains("needs the georeference"));
    run("{\"action\":\"set_georeference\",\"x\":0,\"y\":0,\"lat\":33.7490,\"lon\":-84.3880}");
    // Without rule: lines of the plan and a label
    summary = run("{\"action\":\"import_geojson\",\"geojson\":" + geojson + "}");
    assertTrue(summary, summary.contains("Imported 3 of the 4 features") && summary.contains("1 label") && summary.contains("2 polyline"));
    assertEquals(2, this.home.getPolylines().size());
    assertEquals(1, this.home.getLabels().size());
    this.homeController.undo();
    assertEquals(0, this.home.getPolylines().size());
    // With rules: a building with its height, a fence, a tree of the species the file names
    summary = run("{\"action\":\"import_geojson\",\"geojson\":" + geojson + ",\"rules\":["
        + "{\"where\":{\"kind\":\"house\"},\"as\":\"building\",\"height_from\":\"HEIGHT\",\"roof\":\"gable\"},"
        + "{\"where\":{\"kind\":\"fence\"},\"as\":\"fence\",\"style\":\"chain_link\"},"
        + "{\"where\":{\"kind\":\"tree\"},\"as\":\"tree\",\"species_from\":\"species\"},"
        + "{\"geometry\":\"point\",\"as\":\"skip\"}]}");
    assertTrue(summary, summary.contains("Imported 3 of the 4 features") && summary.contains("1 building") && summary.contains("1 fence")
        && summary.contains("1 tree"));
    HomePieceOfFurniture house = getPiece("1232 Fernwood");
    assertNotNull(summary, house);
    // 0.0001 degree is about 9.2 m of longitude and 11.1 m of latitude; its north is up the plan
    assertEquals(925, house.getWidth(), 25);
    assertEquals(1110, house.getDepth(), 25);
    assertEquals(460, house.getX(), 25);
    assertEquals(-555, house.getY(), 25);
    assertTrue(house.getProperty("tags"), house.getProperty("tags").contains("imported") && house.getProperty("tags").contains("neighbor"));
    assertNotNull(getPiece("White oak"));
    assertEquals(1, count("fence"));

    // The same lot in KML
    String kml = "<?xml version=\"1.0\"?><kml><Document><Placemark><name>Lot &amp; house</name><ExtendedData>"
        + "<Data name=\"OWNER\"><value>Smith</value></Data></ExtendedData><Polygon><outerBoundaryIs><LinearRing><coordinates>"
        + "-84.3882,33.7488,0 -84.3876,33.7488,0 -84.3876,33.7493,0 -84.3882,33.7493,0 -84.3882,33.7488,0"
        + "</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>"
        + "<Placemark><name>Gate</name><Point><coordinates>-84.3879,33.7488,0</coordinates></Point></Placemark></Document></kml>";
    java.io.File file = write(".kml", kml.getBytes("UTF-8"));
    summary = run("{\"action\":\"import_parcels\",\"path\":\"" + path(file) + "\"}");
    assertTrue(summary, summary.contains("Imported 1 of the 2 features") && summary.contains("1 property_line"));
    boolean lot = false;
    for (com.eteks.sweethome3d.model.Polyline polyline : this.home.getPolylines()) {
      lot |= "property".equals(polyline.getProperty("assistantLineKind"))
          && String.valueOf(polyline.getProperty("assistantLineName")).startsWith("Smith (");
    }
    assertTrue(summary, lot);
    summary = run("{\"action\":\"import_kml\",\"path\":\"" + path(file) + "\",\"rules\":[{\"geometry\":\"point\",\"as\":\"utility\",\"type\":\"mailbox\"}]}");
    assertTrue(summary, summary.contains("1 utility"));
  }

  @Test
  public void testDxfShapefileAndOsm() throws Exception {
    // A drawing in meters: a closed outline on the layer BLDG, a line on the layer FENCE, a text
    String dxf = "0\nSECTION\n2\nENTITIES\n"
        + "0\nLWPOLYLINE\n8\nBLDG\n90\n4\n70\n1\n10\n0\n20\n0\n10\n12\n20\n0\n10\n12\n20\n9\n10\n0\n20\n9\n"
        + "0\nLINE\n8\nFENCE\n10\n-5\n20\n-3\n11\n20\n21\n-3\n"
        + "0\nTEXT\n8\nNOTES\n10\n6\n20\n4\n40\n1\n1\nShop\n"
        + "0\nENDSEC\n0\nEOF\n";
    java.io.File file = write(".dxf", dxf.getBytes("UTF-8"));
    String summary = run("{\"action\":\"import_dxf\",\"path\":\"" + path(file) + "\",\"units\":\"m\",\"x\":1000,\"y\":2000,\"rules\":["
        + "{\"where\":{\"layer\":\"BLDG\"},\"as\":\"building\",\"roof\":\"flat\",\"name\":\"Shop\"},"
        + "{\"where\":{\"layer\":\"FENCE\"},\"as\":\"fence\",\"style\":\"privacy\"},{\"geometry\":\"point\",\"as\":\"label\"}]}");
    assertTrue(summary, summary.contains("Imported 3 of the 3 features") && summary.contains("1 building") && summary.contains("1 fence")
        && summary.contains("1 label"));
    HomePieceOfFurniture shop = getPiece("Shop");
    // 12 x 9 m, its origin at (1000, 2000) and its north up the plan
    assertEquals(1200, shop.getWidth(), 2);
    assertEquals(900, shop.getDepth(), 2);
    assertEquals(1600, shop.getX(), 2);
    assertEquals(2000 - 450, shop.getY(), 2);

    // A shapefile of one polygon with a .dbf table: 10 x 20 m
    java.nio.ByteBuffer shp = java.nio.ByteBuffer.allocate(100 + 8 + 4 + 32 + 8 + 4 + 5 * 16);
    shp.putInt(0, 9994);
    shp.order(java.nio.ByteOrder.LITTLE_ENDIAN);
    shp.putInt(28, 1000);
    shp.putInt(32, 5);
    shp.order(java.nio.ByteOrder.BIG_ENDIAN);
    shp.putInt(100, 1);
    shp.putInt(104, (4 + 32 + 8 + 4 + 5 * 16) / 2);
    shp.order(java.nio.ByteOrder.LITTLE_ENDIAN);
    shp.putInt(108, 5);
    shp.putInt(108 + 36, 1);
    shp.putInt(108 + 40, 5);
    shp.putInt(108 + 44, 0);
    double [][] ring = {{0, 0}, {0, 20}, {10, 20}, {10, 0}, {0, 0}};
    for (int i = 0; i < 5; i++) {
      shp.putDouble(108 + 48 + i * 16, ring [i][0]);
      shp.putDouble(108 + 56 + i * 16, ring [i][1]);
    }
    file = write(".shp", shp.array());
    java.nio.ByteBuffer dbf = java.nio.ByteBuffer.allocate(32 + 32 + 1 + 1 + 10).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    dbf.put(0, (byte)3);
    dbf.putInt(4, 1);
    dbf.putShort(8, (short)65);
    dbf.putShort(10, (short)11);
    byte [] fieldName = "OWNER".getBytes("ISO-8859-1");
    for (int i = 0; i < fieldName.length; i++) {
      dbf.put(32 + i, fieldName [i]);
    }
    dbf.put(32 + 11, (byte)'C');
    dbf.put(32 + 16, (byte)10);
    dbf.put(64, (byte)0x0D);
    dbf.put(65, (byte)' ');
    byte [] value = "Jones     ".getBytes("ISO-8859-1");
    for (int i = 0; i < value.length; i++) {
      dbf.put(66 + i, value [i]);
    }
    java.io.File table = new java.io.File(file.getPath().substring(0, file.getPath().length() - 4) + ".dbf");
    table.deleteOnExit();
    java.io.FileOutputStream out = new java.io.FileOutputStream(table);
    out.write(dbf.array());
    out.close();
    summary = run("{\"action\":\"import_shapefile\",\"path\":\"" + path(file) + "\",\"units\":\"m\",\"x\":5000,\"y\":5000,"
        + "\"rules\":[{\"geometry\":\"polygon\",\"as\":\"patio\",\"name_from\":\"OWNER\",\"pattern\":\"concrete\"}]}");
    assertTrue(summary, summary.contains("Imported 1 of the 1 features") && summary.contains("1 patio"));
    HomePieceOfFurniture patio = getPiece("Jones");
    assertNotNull(summary, patio);
    assertEquals(1000, patio.getWidth(), 2);
    assertEquals(2000, patio.getDepth(), 2);

    // OpenStreetMap data: a house of two levels, a road, a tree and a chain link fence
    run("{\"action\":\"set_georeference\",\"x\":0,\"y\":0,\"lat\":33.7490,\"lon\":-84.3880}");
    String osm = "<?xml version='1.0'?><osm version='0.6'>"
        + "<node id='1' lat='33.7490' lon='-84.3880'/><node id='2' lat='33.7490' lon='-84.3879'/>"
        + "<node id='3' lat='33.7491' lon='-84.3879'/><node id='4' lat='33.7491' lon='-84.3880'/>"
        + "<node id='5' lat='33.7488' lon='-84.3885'/><node id='6' lat='33.7488' lon='-84.3875'/>"
        + "<node id='7' lat='33.7492' lon='-84.3881'><tag k='natural' v='tree'/><tag k='leaf_type' v='needleleaved'/></node>"
        + "<way id='10'><nd ref='1'/><nd ref='2'/><nd ref='3'/><nd ref='4'/><nd ref='1'/><tag k='building' v='house'/>"
        + "<tag k='building:levels' v='2'/></way>"
        + "<way id='11'><nd ref='5'/><nd ref='6'/><tag k='highway' v='residential'/><tag k='name' v='Fernwood Dr'/></way>"
        + "<way id='12'><nd ref='1'/><nd ref='5'/><tag k='barrier' v='fence'/><tag k='fence_type' v='chain_link'/></way>"
        + "<way id='13'><nd ref='2'/><nd ref='6'/><tag k='landuse' v='residential'/></way></osm>";
    file = write(".osm", osm.getBytes("UTF-8"));
    int fences = count("fence");
    summary = run("{\"action\":\"import_osm\",\"path\":\"" + path(file) + "\"}");
    assertTrue(summary, summary.contains("1 building") && summary.contains("1 driveway") && summary.contains("1 fence")
        && summary.contains("1 tree") && summary.contains("1 were left out"));
    assertEquals(fences + 1, count("fence"));
    assertNotNull(getPiece("Conifer"));
    boolean road = false;
    for (com.eteks.sweethome3d.model.Label label : this.home.getLabels()) {
      road |= "Fernwood Dr".equals(label.getText());
    }
    assertTrue(road);
    summary = run("{\"action\":\"import_osm\",\"path\":\"" + path(file) + "\",\"only\":[\"trees\"]}");
    assertTrue(summary, summary.contains("Imported 1 of") && summary.contains("1 tree"));
  }

  @Test
  public void testWebServicesNeedThePermission() {
    run("{\"action\":\"set_georeference\",\"x\":0,\"y\":0,\"lat\":33.7490,\"lon\":-84.3880}");
    for (String command : new String [] {"{\"action\":\"fetch_elevation\"}", "{\"action\":\"import_osm\"}",
        "{\"action\":\"set_basemap\"}", "{\"action\":\"import_parcels\",\"url\":\"https://example.com/FeatureServer/0\"}"}) {
      String summary = run(command);
      assertTrue(command + ": " + summary, summary.contains("the user didn't allow") && summary.contains("Allow web services"));
    }
    assertFalse(this.executor.isWebAccessAllowed());
    String summary = run("{\"action\":\"web_sources\"}");
    assertTrue(summary, summary.contains("Web services aren't allowed") && summary.contains("elevation.nationalmap.gov"));
    summary = run("{\"action\":\"fetch_point_cloud\"}");
    assertTrue(summary, summary.contains("point clouds aren't read"));
    summary = run("{\"action\":\"set_basemap\",\"source\":\"google\"}");
    assertTrue(summary, summary.contains("Bing and Google"));
  }
}
