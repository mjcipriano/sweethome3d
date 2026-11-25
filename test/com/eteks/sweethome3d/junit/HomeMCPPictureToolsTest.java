/*
 * HomeMCPPictureToolsTest.java 25 nov. 2026
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeApplication;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeRecorder;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.HomeMCPBridge;
import com.eteks.sweethome3d.swing.HomeMCPServer;
import com.eteks.sweethome3d.swing.HomePane;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.HomeViewpoints;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the tools of the MCP server that help to find what is wrong in a picture: the items
 * identified at pixels and labeled, the pictures of some items alone and of saved versions,
 * the points of view which stay inside a room, <code>inspect_room</code>, and the scratch
 * homes which don't take over the connection of a client.
 * @author Sweet Home 3D contributors
 */
public class HomeMCPPictureToolsTest {
  private String          previousNo3D;
  private File            folder;
  private UserPreferences preferences;
  private HomeRecorder    recorder;
  private HomeApplication application;
  private Home            home;
  private HomeController  homeController;
  private HomeMCPHandler  handler;
  private Room            room;
  private Wall            wall;
  private HomePieceOfFurniture sofa;
  private HomePieceOfFurniture table;
  // What the stand-in for the renderer was asked
  private final List<HomeMCPHandler.Image3DRequest> requests = new ArrayList<HomeMCPHandler.Image3DRequest>();
  private final List<Home>                          planHomes = new ArrayList<Home>();
  private boolean                                   identifying = true;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.folder = File.createTempFile("mcp-pictures", "");
    this.folder.delete();
    assertTrue(this.folder.mkdir());
    this.preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.recorder = new HomeFileRecorder();
    this.application = new HomeApplication() {
        @Override
        public UserPreferences getUserPreferences() {
          return preferences;
        }

        @Override
        public HomeRecorder getHomeRecorder() {
          return recorder;
        }
      };
    this.home = new Home(250);
    this.application.addHome(this.home);
    this.homeController = new HomeController(this.home, this.application, new SwingViewFactory());
    this.handler = new HomeMCPHandler(this.home, this.preferences, this.homeController, "test");
    this.handler.setEditingAllowed(true);
    this.handler.setImageProvider(createImageProvider(this.home));
    assertFalse(Boolean.TRUE.equals(call("apply_commands", "{\"commands\":["
        + "{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":600,\"y2\":0,\"thickness\":10,\"height\":250},"
        + "{\"action\":\"add_wall\",\"x1\":600,\"y1\":0,\"x2\":600,\"y2\":400,\"thickness\":10,\"height\":250},"
        + "{\"action\":\"add_room\",\"points\":[[0,0],[600,0],[600,400],[0,400]],\"name\":\"Living\"},"
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":300,\"y\":320},"
        + "{\"action\":\"add_furniture\",\"name\":\"Table\",\"x\":300,\"y\":150}]}").get("isError")));
    this.room = this.home.getRooms().get(0);
    this.wall = (Wall)HomeItemIds.find(this.home, "W1");
    this.sofa = (HomePieceOfFurniture)HomeItemIds.find(this.home, "F1");
    this.table = (HomePieceOfFurniture)HomeItemIds.find(this.home, "F2");
    assertNotNull(this.wall);
    assertNotNull(this.table);
    this.requests.clear();
  }

  @After
  public void tearDown() {
    // No scratch home left to the other tests
    HomeMCPHandler.closeIdleScratchHomes(0);
    delete(this.folder);
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private static void delete(File file) {
    File [] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        delete(child);
      }
    }
    file.delete();
  }

  private static byte [] createPNG(int width, int height) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Returns the item of the same id as <code>item</code> in an other home.
   */
  private static Object find(Home drawnHome, Object item) {
    return HomeItemIds.find(drawnHome, HomeItemIds.getId(item));
  }

  /**
   * Returns a stand-in for the renderer of the pictures of <code>displayedHome</code>. It notes what it's asked,
   * and tells that the pixels with an abscissa of 10 show the first wall of the drawn home from its left side,
   * 20 its top, 30 the floor of its first room, 40 the ceiling of that room, 50 a material of its
   * first piece, 60 its last piece (a roof in some tests) and the other ones nothing.
   */
  private HomeMCPHandler.ImageProvider createImageProvider(final Home displayedHome) {
    return new HomeMCPHandler.ImageProvider() {
        public byte [] getPlanImage(int maxSize, Level level, float [] area) {
          planHomes.add(displayedHome);
          return createPNG(40, 30);
        }

        public byte [] getPlanImage(Home drawnHome, int maxSize, Level level, float [] area) {
          planHomes.add(drawnHome);
          return createPNG(40, 30);
        }

        public HomeMCPHandler.Image3D get3DImage(HomeMCPHandler.Image3DRequest request) {
          requests.add(request);
          Home drawnHome = request.getDrawnHome() != null ? request.getDrawnHome() : displayedHome;
          HomeMCPHandler.Image3D image = new HomeMCPHandler.Image3D(createPNG(request.getWidth(), request.getHeight()),
              null, "Drawn by the stand-in renderer");
          if (identifying) {
            List<HomeMCPHandler.IdentifiedItem> items = new ArrayList<HomeMCPHandler.IdentifiedItem>();
            if (request.getIdentifiedPixels() != null) {
              for (int [] pixel : request.getIdentifiedPixels()) {
                items.add(identify(drawnHome, pixel [0], pixel [1]));
              }
              image.setIdentifiedItems(items);
            }
            if (request.isLabels()) {
              List<HomeMCPHandler.IdentifiedItem> labeledItems = new ArrayList<HomeMCPHandler.IdentifiedItem>();
              for (int x = 10; x <= 60; x += 10) {
                HomeMCPHandler.IdentifiedItem item = identify(drawnHome, x, 5);
                if (item.getItem() != null && (x != 40 && x != 20)) {
                  labeledItems.add(item);
                }
              }
              image.setLabeledItems(labeledItems);
            }
          }
          return image;
        }

        private HomeMCPHandler.IdentifiedItem identify(Home drawnHome, int x, int y) {
          List<Wall> walls = new ArrayList<Wall>(drawnHome.getWalls());
          List<HomePieceOfFurniture> furniture = drawnHome.getFurniture();
          Wall firstWall = null;
          for (Wall homeWall : walls) {
            if ("W1".equals(HomeItemIds.getId(homeWall))) {
              firstWall = homeWall;
            }
          }
          switch (x) {
            case 10 :
              // A wall from (0, 0) to (600, 0), 10 cm thick: its left side is at y = -5
              return new HomeMCPHandler.IdentifiedItem(x, y, firstWall, null, new float [] {200, -5, 120}, 345);
            case 20 :
              return new HomeMCPHandler.IdentifiedItem(x, y, firstWall, null, new float [] {200, 1, 250}, 400);
            case 30 :
              return new HomeMCPHandler.IdentifiedItem(x, y, drawnHome.getRooms().isEmpty() ? null : drawnHome.getRooms().get(0),
                  null, new float [] {300, 200, 0}, 500);
            case 40 :
              return new HomeMCPHandler.IdentifiedItem(x, y, drawnHome.getRooms().isEmpty() ? null : drawnHome.getRooms().get(0),
                  null, new float [] {300, 200, 250}, 500);
            case 50 :
              return new HomeMCPHandler.IdentifiedItem(x, y, furniture.isEmpty() ? null : furniture.get(0), "Cushion", "fabric",
                  new float [] {300, 320, 45}, 250);
            case 60 :
              return new HomeMCPHandler.IdentifiedItem(x, y, furniture.isEmpty() ? null : furniture.get(furniture.size() - 1),
                  "color_8C4A3C", new float [] {300, 0, 300}, 900);
            default :
              return new HomeMCPHandler.IdentifiedItem(x, y, null, null, null, 0);
          }
        }

        public byte [] getCatalogImage(String name, boolean texture) {
          return null;
        }

        public String get3DInfo() {
          return "Renderers: stand-in.";
        }
      };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> call(HomeMCPHandler handler, String name, String arguments) {
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}"));
    assertNotNull("A tool result and no protocol error: " + response, response.get("result"));
    return (Map<String, Object>)response.get("result");
  }

  private Map<String, Object> call(String name, String arguments) {
    return call(this.handler, name, arguments);
  }

  @SuppressWarnings("unchecked")
  private static String text(Map<String, Object> toolResult) {
    StringBuilder text = new StringBuilder();
    for (Object content : (List<Object>)toolResult.get("content")) {
      if ("text".equals(((Map<String, Object>)content).get("type"))) {
        text.append(((Map<String, Object>)content).get("text")).append('\n');
      }
    }
    return text.toString();
  }

  @SuppressWarnings("unchecked")
  private static List<BufferedImage> images(Map<String, Object> toolResult) throws IOException {
    List<BufferedImage> images = new ArrayList<BufferedImage>();
    for (Object content : (List<Object>)toolResult.get("content")) {
      if ("image".equals(((Map<String, Object>)content).get("type"))) {
        images.add(ImageIO.read(new ByteArrayInputStream(
            java.util.Base64.getDecoder().decode((String)((Map<String, Object>)content).get("data")))));
      }
    }
    return images;
  }

  private String text(String name, String arguments) {
    return text(call(name, arguments));
  }

  private boolean isError(String name, String arguments) {
    return Boolean.TRUE.equals(call(name, arguments).get("isError"));
  }

  private String path(String name) {
    return new File(this.folder, name).getPath().replace("\\", "\\\\");
  }

  /**
   * The answer tells which item is drawn at a pixel, with its part.
   */
  @Test
  public void testIdentifyAndLabels() throws Exception {
    assertFalse(isError("apply_commands", "{\"commands\":["
        + "{\"action\":\"set_color\",\"id\":\"W1\",\"side\":\"left\",\"color\":\"#D5AC6B\"},"
        + "{\"action\":\"add_roof\",\"points\":[[-30,-30],[630,-30],[630,430],[-30,430]],\"type\":\"gable\",\"pitch\":30}]}"));
    Map<String, Object> result = call("get_3d_image",
        "{\"view\":\"south_east\",\"width\":400,\"height\":300,\"identify\":[[10,5],[20,5],[30,5],[40,5],[50,5],[60,5],[70,5]]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    String text = text(result);
    // The pixels are the ones asked, in the picture asked
    HomeMCPHandler.Image3DRequest request = this.requests.get(this.requests.size() - 1);
    assertEquals(7, request.getIdentifiedPixels().size());
    assertEquals(20, request.getIdentifiedPixels().get(1) [0]);
    assertFalse(request.isLabels());
    assertTrue(text, text.contains("[10,5]: W1 wall, its left side (color #D5AC6B), at x 200, y -5, elevation 120 cm, 3.45 m from the camera"));
    assertTrue(text, text.contains("[20,5]: W1 wall, its top"));
    assertTrue(text, text.contains("[30,5]: R1 room \"Living\", its floor"));
    assertTrue(text, text.contains("[40,5]: R1 room \"Living\", its ceiling"));
    assertTrue(text, text.contains("[50,5]: F1 \"Sofa\", its piece \"Cushion\", material \"fabric\""));
    // The edge of the outline of the roof the closest to the point (300, 0) is its first one, 30 cm away
    assertTrue(text, text.contains("[60,5]: F3 \"Roof\" (roof), material \"color_8C4A3C\", 30 cm from its edge 1"));
    assertTrue(text, text.contains("[70,5]: nothing of the home"));

    // The other side of the wall has no color; one pixel may be given without the brackets of a list
    this.wall.setLeftSideColor(null);
    text = text("get_3d_image", "{\"identify\":[10,5]}");
    assertTrue(text, text.contains("its left side (no color nor texture)"));
    text = text("get_3d_image", "{\"identify\":[{\"x\":10,\"y\":7}]}");
    assertTrue(text, text.contains("[10,7]: W1 wall"));

    // Labels
    text = text("get_3d_image", "{\"labels\":true}");
    assertTrue(this.requests.get(this.requests.size() - 1).isLabels());
    assertTrue(text, text.contains("4 items in sight labeled with their ids"));
    assertTrue(text, text.contains("W1 wall, R1 room \"Living\", F1 \"Sofa\", F3 \"Roof\""));

    // Pixels out of the picture, too many of them, or not written as pixels
    assertTrue(isError("get_3d_image", "{\"width\":400,\"height\":300,\"identify\":[[400,5]]}"));
    assertTrue(isError("get_3d_image", "{\"width\":400,\"height\":300,\"identify\":[[5,-1]]}"));
    assertTrue(isError("get_3d_image", "{\"identify\":\"F1\"}"));
    assertTrue(isError("get_3d_image", "{\"identify\":[[1,2,3]]}"));
    StringBuilder pixels = new StringBuilder();
    for (int i = 0; i < 61; i++) {
      pixels.append(i > 0 ? "," : "").append("[").append(i).append(",1]");
    }
    assertTrue(isError("get_3d_image", "{\"identify\":[" + pixels + "]}"));

    // A renderer which can't tell: said, with the picture
    this.identifying = false;
    result = call("get_3d_image", "{\"identify\":[[10,5]],\"labels\":true}");
    assertEquals(Boolean.FALSE, result.get("isError"));
    assertEquals(1, images(result).size());
    assertTrue(text(result), text(result).contains("can't be told"));
    assertTrue(text(result), text(result).contains("No label was drawn"));
    // An unknown argument is still refused
    assertTrue(isError("get_3d_image", "{\"identfy\":[[10,5]]}"));
  }

  /**
   * Some items are drawn alone, seen entirely.
   */
  @Test
  public void testOnlyIds() throws Exception {
    Map<String, Object> result = call("get_3d_image", "{\"only_ids\":[\"F2\"]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    HomeMCPHandler.Image3DRequest request = this.requests.get(this.requests.size() - 1);
    Home drawnHome = request.getDrawnHome();
    assertNotNull(drawnHome);
    assertTrue(drawnHome.getWalls().isEmpty());
    assertTrue(drawnHome.getRooms().isEmpty());
    assertFalse(((HomePieceOfFurniture)find(drawnHome, this.sofa)).isVisible());
    assertTrue(((HomePieceOfFurniture)find(drawnHome, this.table)).isVisible());
    // The home itself didn't change
    assertEquals(2, this.home.getWalls().size());
    assertTrue(this.sofa.isVisible());
    // From the south east, the table in the middle of the picture
    assertNotNull(request.getPose());
    assertTrue(request.getPose().isAerial());
    assertTrue(Math.hypot(request.getPose().getX() - this.table.getX(), request.getPose().getY() - this.table.getY()) < 500);
    assertTrue(text(result), text(result).contains("Only F2 is drawn (only_ids)"));
    assertTrue(text(result), text(result).contains("view south_east of F2"));

    // A wall and a room, from an other view; ids written in lower case
    result = call("get_3d_image", "{\"only_ids\":[\"w1\",\"R1\"],\"view\":\"top\"}");
    drawnHome = this.requests.get(this.requests.size() - 1).getDrawnHome();
    assertEquals(1, drawnHome.getWalls().size());
    assertEquals(1, drawnHome.getRooms().size());
    assertFalse(((HomePieceOfFurniture)find(drawnHome, this.table)).isVisible());
    assertTrue(text(result), text(result).contains("Only W1, R1 are drawn"));
    // With the camera asked
    result = call("get_3d_image", "{\"only_ids\":[\"F1\"],\"camera\":{\"x\":0,\"y\":0,\"z\":160,\"yaw\":0,\"pitch\":10}}");
    assertEquals(0, this.requests.get(this.requests.size() - 1).getPose().getX(), 0.01f);

    assertTrue(isError("get_3d_image", "{\"only_ids\":[\"F99\"]}"));
    assertTrue(isError("get_3d_image", "{\"only_ids\":[]}"));
    assertTrue(isError("get_3d_image", "{\"only_ids\":[\"L1\"]}"));
  }

  /**
   * A saved version is drawn without being restored.
   */
  @Test
  public void testVersionPictures() throws Exception {
    assertTrue("A home without file has no version", isError("get_3d_image", "{\"version\":\"1\"}"));
    // The pieces of the catalog of this test have no file to save
    this.home.deletePieceOfFurniture(this.sofa);
    this.home.deletePieceOfFurniture(this.table);
    this.recorder.writeHome(this.home, new File(this.folder, "house.sh3d").getPath());
    this.home.setName(new File(this.folder, "house.sh3d").getPath());
    assertFalse(isError("save_version", "{\"label\":\"two walls\"}"));
    assertFalse(isError("apply_commands", "{\"commands\":["
        + "{\"action\":\"add_wall\",\"x1\":600,\"y1\":400,\"x2\":0,\"y2\":400,\"thickness\":10,\"height\":250},"
        + "{\"action\":\"add_room\",\"points\":[[1000,0],[1400,0],[1400,300],[1000,300]],\"name\":\"Shed\"}]}"));
    assertEquals(3, this.home.getWalls().size());
    assertEquals(2, this.home.getRooms().size());
    this.requests.clear();

    // The home as it is now, then its version from the same camera
    Map<String, Object> result = call("get_3d_image", "{\"width\":320,\"height\":240}");
    assertNull(this.requests.get(0).getDrawnHome());
    result = call("get_3d_image", "{\"width\":320,\"height\":240,\"version\":\"two walls\",\"identify\":[[10,5]],\"labels\":true}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    Home drawnHome = this.requests.get(1).getDrawnHome();
    assertNotNull(drawnHome);
    assertEquals(2, drawnHome.getWalls().size());
    assertEquals(1, drawnHome.getRooms().size());
    assertEquals(this.home.getCamera().getX(), this.requests.get(1).getPose().getX(), 0.5f);
    assertEquals(this.home.getCamera().getYaw(), this.requests.get(1).getPose().getYaw(), 0.01f);
    String text = text(result);
    assertTrue(text, text.contains("two walls"));
    assertTrue(text, text.contains("wasn't restored"));
    assertTrue(text, text.contains("[10,5]: W1 wall"));
    // Nothing was restored
    assertEquals(3, this.home.getWalls().size());
    assertEquals(2, this.home.getRooms().size());
    assertFalse(text("get_history", "{}").contains("Restore"));

    // By its number, with a view on one of its items, and some items alone
    result = call("get_3d_image", "{\"version\":\"1\",\"view\":\"inside\",\"target\":\"R1\"}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertFalse(this.requests.get(2).getPose().isAerial());
    result = call("get_3d_image", "{\"version\":\"1\",\"only_ids\":[\"W1\"]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertTrue(this.requests.get(3).getDrawnHome().getRooms().isEmpty());
    assertEquals(1, this.requests.get(3).getDrawnHome().getWalls().size());
    // The room added since isn't in the version
    assertTrue(isError("get_3d_image", "{\"version\":\"1\",\"view\":\"inside\",\"target\":\"R2\"}"));
    assertFalse(isError("get_3d_image", "{\"view\":\"inside\",\"target\":\"R2\"}"));
    assertTrue(isError("get_3d_image", "{\"version\":\"missing\"}"));
    assertTrue("Points of view are stored in the home, not in a version",
        isError("get_3d_image", "{\"version\":\"1\",\"store_as\":\"before\"}"));

    // The plan of the version
    this.planHomes.clear();
    result = call("get_plan_image", "{\"version\":\"1\",\"area\":{\"x\":0,\"y\":0,\"width\":600,\"height\":400}}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertEquals(1, this.planHomes.size());
    assertEquals(2, this.planHomes.get(0).getWalls().size());
    assertTrue(text(result), text(result).contains("wasn't restored"));
    call("get_plan_image", "{\"area\":{\"x\":0,\"y\":0,\"width\":600,\"height\":400}}");
    assertEquals(this.home, this.planHomes.get(1));

    // A room of the version, looked at up to its ceiling
    this.requests.clear();
    result = call("inspect_room", "{\"room\":\"R1\",\"version\":\"1\"}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertEquals(6, this.requests.size());
    assertEquals(2, this.requests.get(0).getDrawnHome().getWalls().size());
    assertTrue(isError("inspect_room", "{\"room\":\"R2\",\"version\":\"1\"}"));

    // Before and after, side by side, when a view is given
    this.handler.setImageComposer(new HomeMCPHandler.ImageComposer() {
        public byte [] compose(byte [] image, byte [] reference, boolean overlay, float opacity) {
          return createPNG(1400, 440);
        }
      });
    this.requests.clear();
    result = call("compare_versions", "{\"version\":\"1\",\"view\":\"north_west\"}");
    assertEquals(1, images(result).size());
    assertEquals(1400, images(result).get(0).getWidth());
    assertEquals(2, this.requests.size());
    assertEquals(2, this.requests.get(0).getDrawnHome().getWalls().size());
    assertEquals(this.requests.get(0).getPose().getX(), this.requests.get(1).getPose().getX(), 0.01f);
    this.requests.clear();
    result = call("compare_versions", "{\"version\":\"1\",\"target\":\"R2\"}");
    assertEquals(1, images(result).size());
    assertTrue(Math.hypot(this.requests.get(0).getPose().getX() - 1200, this.requests.get(0).getPose().getY() - 150) < 1500);
    assertTrue(this.requests.get(0).getPose().getX() > 1200);
    // Without a view, the text alone as before
    assertEquals(0, images(call("compare_versions", "{\"version\":\"1\"}")).size());
  }

  /**
   * The camera of the view inside stays in the room, under its ceiling and under the roof.
   */
  @Test
  public void testInsideViewStaysInRoom() throws Exception {
    List<Selectable> target = new ArrayList<Selectable>();
    target.add(this.room);
    HomeViewpoints.Pose pose = HomeViewpoints.getPose(this.home, "inside", target, null, 1.6f, 0);
    assertTrue(this.room.containsPoint(pose.getX(), pose.getY(), 0));
    assertEquals(160, pose.getZ(), 0.01f);

    // A ceiling lower than the eyes of a visitor
    this.room.setProperty("com.eteks.sweethome3d.ceilingHeight", "150");
    pose = HomeViewpoints.getPose(this.home, "inside", target, null, 1.6f, 0);
    assertTrue(this.room.containsPoint(pose.getX(), pose.getY(), 0));
    assertEquals(125, pose.getZ(), 0.01f);
    this.room.setProperty("com.eteks.sweethome3d.ceilingHeight", null);

    // A room open to a roof whose eaves are 1 m above the floor: the camera goes where the roof is higher,
    // and stays under it
    this.room.setCeilingVisible(false);
    assertFalse(isError("apply_commands", "{\"commands\":[{\"action\":\"add_roof\",\"points\":[[0,0],[600,0],[600,400],[0,400]],"
        + "\"type\":\"gable\",\"pitch\":40,\"eave_height\":100,\"overhang\":0}]}"));
    pose = HomeViewpoints.getPose(this.home, "inside", target, null, 1.6f, 0);
    assertTrue(this.room.containsPoint(pose.getX(), pose.getY(), 0));
    HomeViewpoints.RoomInspection inspection = HomeViewpoints.inspectRoom(this.home, this.room);
    // The roof is the highest over the middle of the room
    assertTrue(inspection.getCeilingElevation() > 200);
    assertEquals(160, inspection.getEyeElevation(), 0.01f);
    float ridgeDistance = Math.min(Math.abs(pose.getX() - 300), Math.abs(pose.getY() - 200));
    // Under the slope: at most its height at that distance of the ridge, less the margin kept
    assertTrue("Camera at " + pose.getX() + ", " + pose.getY() + ", " + pose.getZ(),
        pose.getZ() <= inspection.getCeilingElevation() - ridgeDistance * Math.tan(Math.toRadians(40)) + 1 - 20
        || pose.getZ() <= 160.01f && ridgeDistance < 30);
    assertTrue(pose.getZ() >= 40);

    // The view of a piece and of a wall of the room stay under the roof too
    for (Selectable item : new Selectable [] {this.sofa, this.wall}) {
      pose = HomeViewpoints.getPose(this.home, "inside", Arrays.asList(new Selectable [] {item}), null, 1.6f, 0);
      assertTrue(pose.getZ() <= 160.01f);
      assertTrue(pose.getZ() >= 40);
    }

    // An L shaped room whose center of gravity is out of it
    Room corner = new Room(new float [][] {{1000, 0}, {1600, 0}, {1600, 100}, {1100, 100}, {1100, 600}, {1000, 600}});
    this.home.addRoom(corner);
    pose = HomeViewpoints.getPose(this.home, "inside", Arrays.asList(new Selectable [] {corner}), null, 1.6f, 0);
    assertTrue(pose.getX() + ", " + pose.getY(), corner.containsPoint(pose.getX(), pose.getY(), 0));
    inspection = HomeViewpoints.inspectRoom(this.home, corner);
    assertTrue(corner.containsPoint(inspection.getX(), inspection.getY(), 0));
  }

  /**
   * A room is shown from its middle toward its four sides, up to its ceiling, straight up and from a corner.
   */
  @Test
  public void testInspectRoom() throws Exception {
    HomeViewpoints.RoomInspection inspection = HomeViewpoints.inspectRoom(this.home, this.room);
    assertEquals(6, inspection.getPoses().length);
    assertEquals(300, inspection.getX(), 0.5f);
    assertEquals(200, inspection.getY(), 0.5f);
    assertEquals(250, inspection.getCeilingElevation(), 0.01f);
    for (int i = 0; i < 4; i++) {
      HomeViewpoints.Pose pose = inspection.getPoses() [i];
      assertEquals(160, pose.getZ(), 0.01f);
      // Tilted up, never level nor down
      assertTrue(pose.getPitch() < -Math.toRadians(7));
      assertTrue(pose.getPitch() > -Math.toRadians(56));
    }
    // The north is at the top of the plan: a yaw of 180 degrees
    assertEquals(Math.PI, inspection.getPoses() [0].getYaw(), 1E-4);
    assertEquals(0, inspection.getPoses() [2].getYaw(), 1E-4);
    // The sides of the room are closer at the north and at the south: the camera looks higher
    assertTrue(inspection.getPoses() [0].getPitch() < inspection.getPoses() [1].getPitch());
    assertEquals(-Math.PI / 2, inspection.getPoses() [4].getPitch(), 1E-4);
    assertTrue(this.room.containsPoint(inspection.getPoses() [5].getX(), inspection.getPoses() [5].getY(), 0));
    // A room open to the floor above: the pictures reach where its walls should
    this.room.setCeilingVisible(false);
    assertTrue(HomeViewpoints.inspectRoom(this.home, this.room).getPoses() [0].getPitch() < inspection.getPoses() [0].getPitch());
    this.room.setCeilingVisible(true);

    // The six pictures, one after the other without the object that puts them in one image
    Map<String, Object> result = call("inspect_room", "{\"room\":\"R1\",\"width\":320,\"hide_ids\":[\"F1\"]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertEquals(6, this.requests.size());
    List<BufferedImage> images = images(result);
    assertEquals(6, images.size());
    assertEquals(320, images.get(0).getWidth());
    assertEquals(240, images.get(0).getHeight());
    assertEquals(-Math.PI / 2, this.requests.get(4).getPose().getPitch(), 1E-3);
    assertFalse(((HomePieceOfFurniture)find(this.requests.get(0).getDrawnHome(), this.sofa)).isVisible());
    String text = text(result);
    assertTrue(text, text.contains("Room R1 \"Living\" seen from x 300, y 200, 160 cm above its floor"));
    assertTrue(text, text.contains("Drawn by the stand-in renderer."));
    assertTrue(text, text.contains("toward the north (top of the plan)"));
    assertTrue(text, text.contains("5. {\"x\":300,\"y\":200,\"z\":160,\"yaw\":180,\"pitch\":-90,\"fov\":90}"));

    // In one image with the object that draws contact sheets
    final String [][] sheetCaptions = {null};
    this.handler.setSunStudyView(new HomeMCPHandler.SunStudyView() {
        public void showWindow(boolean visible) {
        }

        public void play(String animation) {
        }

        public void setDisplay(Boolean openGL, Boolean shadows) {
        }

        public String describe() {
          return "";
        }

        public byte [] createContactSheet(byte [][] images, String [] captions, int columns) {
          sheetCaptions [0] = captions;
          assertEquals(3, columns);
          return createPNG(960, 520);
        }

        public byte [] createSunHoursImage(byte [][] masks, byte [] shading, double stepHours, double maximumHours,
                                           String title, float [] shares) {
          return null;
        }
      });
    result = call("inspect_room", "{\"room\":\"r1\"}");
    assertEquals(1, images(result).size());
    assertEquals(960, images(result).get(0).getWidth());
    assertEquals(6, sheetCaptions [0].length);
    assertEquals("Straight up (north at top)", sheetCaptions [0][4]);

    assertTrue(isError("inspect_room", "{\"room\":\"R9\"}"));
    assertTrue(isError("inspect_room", "{\"room\":\"F1\"}"));
    assertTrue(isError("inspect_room", "{}"));
  }

  /**
   * The pictures asked to the window of a home: without 3D in this test, the answers tell it without failing,
   * and the plan of a version is drawn by the code that draws the plan of the home.
   */
  @Test
  public void testPicturesOfDisplayedHome() throws Exception {
    Home displayedHome = new Home(250);
    displayedHome.addRoom(new Room(new float [][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
    UserPreferences displayedPreferences = new DefaultUserPreferences();
    final HomeRecorder displayedRecorder = new HomeFileRecorder();
    final UserPreferences applicationPreferences = displayedPreferences;
    HomeApplication displayedApplication = new HomeApplication() {
        @Override
        public UserPreferences getUserPreferences() {
          return applicationPreferences;
        }

        @Override
        public HomeRecorder getHomeRecorder() {
          return displayedRecorder;
        }
      };
    displayedApplication.addHome(displayedHome);
    HomeController displayedController = new HomeController(displayedHome, displayedApplication, new SwingViewFactory());
    HomePane homePane = (HomePane)displayedController.getView();
    java.lang.reflect.Method getMCPHandler = HomePane.class.getDeclaredMethod("getMCPHandler");
    getMCPHandler.setAccessible(true);
    HomeMCPHandler displayedHandler = (HomeMCPHandler)getMCPHandler.invoke(homePane);
    displayedHandler.setEditingAllowed(true);

    for (String arguments : new String [] {"{\"identify\":[[10,10]],\"labels\":true}", "{\"only_ids\":[\"R1\"]}"}) {
      Map<String, Object> result = call(displayedHandler, "get_3d_image", arguments);
      assertEquals(Boolean.TRUE, result.get("isError"));
      assertTrue(text(result), text(result).contains("can't be rendered in 3D"));
    }
    assertEquals(Boolean.TRUE, call(displayedHandler, "inspect_room", "{\"room\":\"R1\"}").get("isError"));

    // A version with one room, then a second room in the home
    String file = new File(this.folder, "displayed.sh3d").getPath();
    displayedRecorder.writeHome(displayedHome, file);
    displayedHome.setName(file);
    assertEquals(Boolean.FALSE, call(displayedHandler, "save_version", "{\"label\":\"one room\"}").get("isError"));
    displayedHome.addRoom(new Room(new float [][] {{1000, 0}, {1400, 0}, {1400, 300}, {1000, 300}}));
    String area = "\"area\":{\"x\":900,\"y\":-100,\"width\":600,\"height\":500},\"max_size\":300";
    Map<String, Object> result = call(displayedHandler, "get_plan_image", "{" + area + "}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    BufferedImage now = images(result).get(0);
    result = call(displayedHandler, "get_plan_image", "{\"version\":\"one room\"," + area + "}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    BufferedImage before = images(result).get(0);
    assertEquals(now.getWidth(), before.getWidth());
    // The second room is drawn in the plan of the home, not in the one of its version
    assertTrue(countDarkPixels(now) > countDarkPixels(before) + 50);
    assertEquals(2, displayedHome.getRooms().size());
  }

  private static int countDarkPixels(BufferedImage image) {
    int count = 0;
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if ((image.getRGB(x, y) & 0xFF) < 235) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Gives to <code>mainHandler</code> the handlers of the homes it creates, as the window of a home does.
   */
  private void setHomeOperations(HomeMCPHandler mainHandler, final Map<Home, HomeMCPHandler> handlers,
                                 final int [] scratchHomesCreated) {
    mainHandler.setHomeOperations(new HomeMCPHandler.HomeOperations() {
        public String export(String format, File target, Map<String, Object> options) {
          return null;
        }

        public String serve(Home otherHome) {
          throw new IllegalStateException("The connection mustn't go to an other home");
        }

        public String startVisit(String mode) {
          return null;
        }

        public HomeMCPHandler getHandler(Home otherHome) {
          HomeMCPHandler otherHandler = handlers.get(otherHome);
          if (otherHandler == null) {
            otherHandler = new HomeMCPHandler(otherHome, preferences,
                new HomeController(otherHome, application, new SwingViewFactory()), "test");
            otherHandler.setImageProvider(createImageProvider(otherHome));
            handlers.put(otherHome, otherHandler);
          }
          return otherHandler;
        }

        public void scratchHomeCreated() {
          scratchHomesCreated [0]++;
        }
      });
  }

  /**
   * A scratch home is edited only by the calls which name it, and the home of the connection by the other ones.
   */
  @Test
  public void testScratchHomes() throws Exception {
    Map<Home, HomeMCPHandler> handlers = new LinkedHashMap<Home, HomeMCPHandler>();
    int [] scratchHomesCreated = {0};
    assertTrue("No scratch home without the application around the handler",
        isError("new_home", "{\"scratch\":true}"));
    setHomeOperations(this.handler, handlers, scratchHomesCreated);
    this.handler.setEditingAllowed(false);
    assertTrue(isError("new_home", "{\"scratch\":true}"));
    this.handler.setEditingAllowed(true);

    Map<String, Object> result = call("new_home", "{\"scratch\":true}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("scratch home \"(scratch-\\d+)\"").matcher(text(result));
    assertTrue(text(result), matcher.find());
    String scratch = matcher.group(1);
    assertEquals(1, scratchHomesCreated [0]);
    assertEquals(2, this.application.getHomes().size());
    Home scratchHome = this.application.getHomes().get(1);
    assertEquals("true", scratchHome.getProperty(HomeController.SCRATCH_HOME_PROPERTY));
    HomeMCPHandler scratchHandler = handlers.get(scratchHome);
    assertEquals(scratch, scratchHandler.getScratchName());
    assertNull(this.handler.getScratchName());
    assertTrue(scratchHandler.isEditingAllowed());
    assertFalse(scratchHandler.isSavingAllowed());
    String homes = text("list_homes", "{}");
    assertTrue(homes, homes.contains("<- the home of this connection"));
    assertTrue(homes, homes.contains("\"home\":\"" + scratch + "\""));

    // The same ids in the two homes: each call reaches the home it names
    result = call("apply_commands", "{\"home\":\"" + scratch + "\",\"commands\":["
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100},"
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":300,\"y\":100},"
        + "{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":500,\"y\":100}]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertTrue(text(result), text(result).contains("F1 \"Sofa\", F2 \"Sofa\", F3 \"Sofa\""));
    assertTrue(text(result), text(result).contains("Home edited: the scratch home \"" + scratch + "\""));
    assertTrue(text(result), text(result).contains("Run in the scratch home \"" + scratch + "\""));
    assertEquals(3, scratchHome.getFurniture().size());
    assertEquals(2, this.home.getFurniture().size());
    result = call("apply_commands", "{\"commands\":[{\"action\":\"delete\",\"ids\":[\"F2\"]}]}");
    assertEquals(Boolean.FALSE, result.get("isError"));
    assertFalse(text(result), text(result).contains("scratch"));
    assertEquals(1, this.home.getFurniture().size());
    assertEquals(3, scratchHome.getFurniture().size());
    assertFalse(isError("apply_commands", "{\"home\":\"" + scratch.toUpperCase() + "\",\"commands\":[{\"action\":\"delete\",\"ids\":[\"F3\"]}]}"));
    assertEquals(2, scratchHome.getFurniture().size());
    assertEquals(1, this.home.getFurniture().size());

    // Reading, drawing and undoing in the scratch home
    String description = text("get_home", "{\"home\":\"" + scratch + "\"}");
    assertTrue(description, description.contains("Furniture: 2 pieces"));
    assertTrue(text("get_home", "{}"), text("get_home", "{}").contains("R1 Living"));
    this.requests.clear();
    result = call("get_3d_image", "{\"home\":\"" + scratch + "\",\"view\":\"south_east\",\"only_ids\":[\"F1\"]}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertEquals(2, this.requests.get(0).getDrawnHome().getFurniture().size());
    assertTrue(this.requests.get(0).getDrawnHome().getRooms().isEmpty());
    assertFalse(isError("undo", "{\"home\":\"" + scratch + "\"}"));
    assertEquals(3, scratchHome.getFurniture().size());
    assertEquals(1, this.home.getFurniture().size());

    // The home of the connection isn't chosen from a scratch home, nor the scratch home by mistake
    assertTrue(isError("list_homes", "{\"home\":\"" + scratch + "\"}"));
    result = call("get_home", "{\"home\":\"scratch-999\"}");
    assertEquals(Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("Scratch homes open: " + scratch));
    assertTrue(isError("apply_commands", "{\"home\":\"scratch-999\",\"commands\":[{\"action\":\"delete\",\"ids\":[\"F1\"]}]}"));
    assertEquals(1, this.home.getFurniture().size());
    // An other home than the one of the connection, named by its file
    result = call("get_home", "{\"home\":\"other-house.sh3d\"}");
    assertEquals(Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("Nothing was done"));
    assertFalse(isError("get_home", "{\"home\":\"unsaved\"}"));
    // An unknown argument of a tool run in a scratch home is told too
    assertTrue(isError("get_home", "{\"home\":\"" + scratch + "\",\"levle\":\"x\"}"));

    // Closed without being saved, at the request of the client
    assertTrue(scratchHome.isModified());
    assertTrue(isError("close_home", "{\"home\":\"house\"}"));
    assertTrue(isError("close_home", "{}"));
    result = call("close_home", "{\"home\":\"" + scratch + "\"}");
    assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    assertEquals(1, this.application.getHomes().size());
    assertEquals(this.home, this.application.getHomes().get(0));
    assertTrue(isError("get_home", "{\"home\":\"" + scratch + "\"}"));
    assertTrue(isError("close_home", "{\"home\":\"" + scratch + "\"}"));

    // Closed after a while without a call
    assertFalse(isError("new_home", "{\"scratch\":true}"));
    assertFalse(isError("new_home", "{\"scratch\":true}"));
    assertEquals(3, this.application.getHomes().size());
    assertEquals(2, HomeMCPHandler.closeIdleScratchHomes(60000));
    assertEquals(3, this.application.getHomes().size());
    assertEquals(0, HomeMCPHandler.closeIdleScratchHomes(0));
    assertEquals(1, this.application.getHomes().size());

    // Closed by the user: its name is forgotten
    result = call("new_home", "{\"scratch\":true}");
    matcher = java.util.regex.Pattern.compile("scratch home \"(scratch-\\d+)\"").matcher(text(result));
    assertTrue(matcher.find());
    assertFalse("A name is never given twice", scratch.equals(matcher.group(1)));
    this.application.deleteHome(this.application.getHomes().get(1));
    result = call("get_home", "{\"home\":\"" + matcher.group(1) + "\"}");
    assertEquals(Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("No scratch home is open"));

    // No more than a few of them
    for (int i = 0; i < 4; i++) {
      assertFalse(isError("new_home", "{\"scratch\":true}"));
    }
    assertTrue(isError("new_home", "{\"scratch\":true}"));
    assertFalse(isError("close_home", "{\"home\":\"all\"}"));
    assertEquals(1, this.application.getHomes().size());
    // new_home without scratch still asks for the connection
    assertTrue(text("new_home", "{}"), isError("new_home", "{}"));
  }

  private static String toolCall(int id, String name, String arguments) {
    return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + name
        + "\",\"arguments\":" + arguments + "}}\n";
  }

  /**
   * Through the program launched by clients: the connection stays on its home while a scratch home is used,
   * and the scratch homes of a session are closed when it ends.
   */
  @Test
  public void testScratchHomesOfSession() throws Exception {
    Map<Home, HomeMCPHandler> handlers = new LinkedHashMap<Home, HomeMCPHandler>();
    setHomeOperations(this.handler, handlers, new int [1]);
    HomeMCPServer server = new HomeMCPServer(this.handler, HomeMCPServer.getOrCreateToken(this.folder), 0);
    server.start();
    try {
      HomeMCPServer.writePort(this.folder, server.getPort());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      final int [] homeCounts = new int [2];
      java.io.InputStream in;
      // The name given by new_home isn't known before the session runs: a first session creates it
      out = new ByteArrayOutputStream();
      HomeMCPBridge.run(new ByteArrayInputStream(toolCall(1, "new_home", "{\"scratch\":true}").getBytes("UTF-8")), out, this.folder);
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("scratch home \\\\\"(scratch-\\d+)").matcher(out.toString("UTF-8"));
      assertTrue(out.toString("UTF-8"), matcher.find());
      // That session ended: its scratch home is closed, and the port of the server didn't change
      assertEquals(1, this.application.getHomes().size());
      assertEquals(server.getPort(), HomeMCPServer.readPort(this.folder));
      int number = Integer.parseInt(matcher.group(1).substring("scratch-".length()));
      final String scratch = "scratch-" + (number + 1);

      out = new ByteArrayOutputStream();
      final String [] parts = {
          toolCall(1, "new_home", "{\"scratch\":true}")
              + toolCall(2, "apply_commands", "{\"home\":\"" + scratch + "\",\"commands\":[{\"action\":\"add_furniture\",\"name\":\"Sofa\",\"x\":100,\"y\":100}]}")
              + toolCall(3, "apply_commands", "{\"commands\":[{\"action\":\"delete\",\"ids\":[\"F1\"]}]}")
              + toolCall(4, "get_home", "{\"home\":\"" + scratch + "\"}"),
          ""};
      in = new java.io.SequenceInputStream(new java.util.Enumeration<java.io.InputStream>() {
          private int index;

          public boolean hasMoreElements() {
            return this.index < parts.length;
          }

          public java.io.InputStream nextElement() {
            if (this.index == 1) {
              // Once the calls of the session are answered and before it ends
              homeCounts [0] = application.getHomes().size();
              homeCounts [1] = application.getHomes().get(1).getFurniture().size();
            }
            try {
              return new ByteArrayInputStream(parts [this.index++].getBytes("UTF-8"));
            } catch (java.io.UnsupportedEncodingException ex) {
              throw new IllegalStateException(ex);
            }
          }
        });
      HomeMCPBridge.run(in, out, this.folder);
      String [] lines = out.toString("UTF-8").split("\n");
      assertEquals(4, lines.length);
      assertTrue(lines [0], lines [0].contains(scratch));
      assertTrue(lines [1], lines [1].contains("\"isError\":false") && lines [1].contains("Home edited: the scratch home"));
      // The connection stayed on its home: its sofa is the one deleted, without any refusal
      assertTrue(lines [2], lines [2].contains("\"isError\":false"));
      assertFalse(lines [2], lines [2].contains("Nothing was done"));
      assertEquals(1, this.home.getFurniture().size());
      assertTrue(lines [3], lines [3].contains("Furniture: 1 piece"));
      assertEquals("Two homes during the session", 2, homeCounts [0]);
      assertEquals(1, homeCounts [1]);
      assertEquals(server.getPort(), HomeMCPServer.readPort(this.folder));
      // The session ended: its scratch home is closed without a question
      assertEquals(1, this.application.getHomes().size());
      assertEquals(this.home, this.application.getHomes().get(0));

      // The scratch home of an other session, or of a client without session, isn't closed by the end of this one
      String request = toolCall(1, "new_home", "{\"scratch\":true}");
      assertTrue(this.handler.handle(request, null, "session A").contains("Created the scratch home"));
      assertTrue(this.handler.handle(request, null, null).contains("Created the scratch home"));
      assertEquals(3, this.application.getHomes().size());
      String sessionEnd = "{\"jsonrpc\":\"2.0\",\"method\":\"" + HomeMCPHandler.SESSION_ENDED_METHOD + "\"}";
      assertNull(this.handler.handle(sessionEnd, null, "session B"));
      assertEquals(3, this.application.getHomes().size());
      assertNull(this.handler.handle(sessionEnd, null, "session A"));
      assertEquals(2, this.application.getHomes().size());
      assertNull(this.handler.handle(sessionEnd));
      assertEquals(1, this.application.getHomes().size());
    } finally {
      server.stop();
    }
  }

  /**
   * The tools are described with the arguments of this test.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testToolDescriptions() {
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(
        this.handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
    List<Object> tools = (List<Object>)((Map<String, Object>)response.get("result")).get("tools");
    Map<String, Map<String, Object>> properties = new LinkedHashMap<String, Map<String, Object>>();
    for (Object tool : tools) {
      properties.put((String)((Map<String, Object>)tool).get("name"),
          (Map<String, Object>)((Map<String, Object>)((Map<String, Object>)tool).get("inputSchema")).get("properties"));
    }
    for (String argument : new String [] {"identify", "labels", "only_ids", "version", "home"}) {
      assertTrue(argument, properties.get("get_3d_image").containsKey(argument));
    }
    assertTrue(properties.get("get_plan_image").containsKey("version"));
    assertTrue(properties.get("compare_versions").containsKey("target"));
    assertTrue(properties.get("inspect_room").containsKey("room"));
    assertTrue(properties.get("new_home").containsKey("scratch"));
    assertTrue(properties.containsKey("close_home"));
    // Any tool may be run in a scratch home, but the ones that choose the home of the connection
    assertTrue(properties.get("get_home").containsKey("home"));
    assertTrue(properties.get("undo").containsKey("home"));
    assertFalse(properties.get("list_homes").containsKey("home"));
    assertFalse(properties.get("open_home").containsKey("home"));
    assertFalse(properties.get("new_home").containsKey("home"));
  }
}
