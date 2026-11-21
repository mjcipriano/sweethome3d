/*
 * HomeMCPFileToolsTest.java 21 nov. 2026
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeApplication;
import com.eteks.sweethome3d.model.HomeRecorder;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.HomeVersions;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the tools of the MCP server that save versions and copies of the home,
 * restore them, show the history and the changes made by hand, and add notes.
 */
public class HomeMCPFileToolsTest {
  private String          previousNo3D;
  private File            folder;
  private Home            home;
  private HomeRecorder    recorder;
  private HomeController  homeController;
  private HomeMCPHandler  handler;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.folder = File.createTempFile("mcp-files", "");
    this.folder.delete();
    assertTrue(this.folder.mkdir());
    this.home = new Home(250);
    final UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    this.recorder = new HomeFileRecorder();
    HomeApplication application = new HomeApplication() {
        @Override
        public UserPreferences getUserPreferences() {
          return preferences;
        }

        @Override
        public HomeRecorder getHomeRecorder() {
          return recorder;
        }
      };
    this.homeController = new HomeController(this.home, application, new SwingViewFactory());
    this.homeController.getView();
    this.handler = new HomeMCPHandler(this.home, preferences, this.homeController, "test");
  }

  @After
  public void tearDown() {
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> call(String name, String arguments) {
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(this.handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}"));
    return (Map<String, Object>)response.get("result");
  }

  @SuppressWarnings("unchecked")
  private String text(String name, String arguments) {
    StringBuilder text = new StringBuilder();
    for (Object content : (List<Object>)call(name, arguments).get("content")) {
      if ("text".equals(((Map<String, Object>)content).get("type"))) {
        text.append(((Map<String, Object>)content).get("text")).append('\n');
      }
    }
    return text.toString();
  }

  private boolean isError(String name, String arguments) {
    return Boolean.TRUE.equals(call(name, arguments).get("isError"));
  }

  private String path(String name) {
    return new File(this.folder, name).getPath().replace("\\", "\\\\");
  }

  private void saveHomeInFile() throws Exception {
    File file = new File(this.folder, "house.sh3d");
    this.recorder.writeHome(this.home, file.getPath());
    this.home.setName(file.getPath());
  }

  @Test
  public void testVersionsAreSavedComparedAndRestored() throws Exception {
    text("apply_commands", "{\"commands\":[{\"action\":\"add_rectangular_room\",\"name\":\"Study\",\"x\":0,\"y\":0,\"width\":400,\"depth\":300}]}");
    assertEquals(4, this.home.getWalls().size());
    // A home that was never saved has no folder for its versions
    assertTrue(isError("save_version", "{\"label\":\"first\"}"));
    assertTrue(text("list_versions", "{}"), text("list_versions", "{}").contains("never saved"));
    saveHomeInFile();
    String summary = text("save_version", "{\"label\":\"one room\"}");
    assertTrue(summary, summary.contains("one room"));
    assertEquals(1, HomeVersions.getVersions(this.home).size());
    // The file of the home isn't written by a version
    long length = new File(this.home.getName()).length();

    text("apply_commands", "{\"commands\":[{\"action\":\"add_rectangular_room\",\"name\":\"Kitchen\",\"x\":420,\"y\":0,\"width\":300,\"depth\":300},"
        + "{\"action\":\"modify_room\",\"room\":\"Study\",\"name\":\"Office\"}]}");
    int wallCount = this.home.getWalls().size();
    assertTrue(wallCount > 4);
    assertEquals(2, this.home.getRooms().size());
    summary = text("list_versions", "{}");
    assertTrue(summary, summary.contains("1. ") && summary.contains("one room"));

    summary = text("compare_versions", "{\"version\":\"one room\"}");
    assertTrue(summary, summary.contains("Rooms: 1 -> 2"));
    assertTrue(summary, summary.contains("Items added"));
    assertTrue(summary, summary.contains("Kitchen"));
    assertTrue(summary, summary.contains("renamed from \"Study\""));
    assertTrue(isError("compare_versions", "{\"version\":\"no such\"}"));

    summary = text("restore_version", "{\"version\":1}");
    assertTrue(summary, summary.contains("Restored the version"));
    assertEquals(4, this.home.getWalls().size());
    assertEquals(1, this.home.getRooms().size());
    assertEquals("Study", this.home.getRooms().get(0).getName());
    for (Wall wall : this.home.getWalls()) {
      // The walls of the version are joined as they were
      assertTrue(wall.getWallAtStart() != null && wall.getWallAtEnd() != null);
      assertTrue(this.home.getWalls().contains(wall.getWallAtStart()));
    }
    // The state before the restoration was kept as a version too
    assertEquals(2, HomeVersions.getVersions(this.home).size());
    assertEquals(length, new File(this.home.getName()).length());
    // The restored items can be edited by their ids
    summary = text("get_home", "{}");
    assertTrue(summary, summary.contains("Study"));
    assertFalse(summary, summary.contains("Kitchen"));

    summary = text("get_history", "{}");
    assertTrue(summary, summary.contains("1. Restore version"));
    text("undo", "{}");
    assertEquals(wallCount, this.home.getWalls().size());
    assertEquals(2, this.home.getRooms().size());
    for (Wall wall : this.home.getWalls()) {
      assertTrue(wall.getWallAtStart() == null || this.home.getWalls().contains(wall.getWallAtStart()));
    }
    text("redo", "{}");
    assertEquals(4, this.home.getWalls().size());
  }

  @Test
  public void testSavingNeedsItsPermission() throws Exception {
    text("apply_commands", "{\"commands\":[{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":300,\"y2\":0}]}");
    saveHomeInFile();
    this.home.setModified(true);
    long length = new File(this.home.getName()).length();
    text("apply_commands", "{\"commands\":[{\"action\":\"add_wall\",\"x1\":0,\"y1\":100,\"x2\":300,\"y2\":100}]}");
    // Not allowed by default
    String summary = text("save_home", "{}");
    assertTrue(summary, summary.contains("isn't allowed"));
    assertEquals(length, new File(this.home.getName()).length());
    // A copy in a new file is
    summary = text("save_home", "{\"copy_to\":\"" + path("copy.sh3d") + "\"}");
    assertTrue(summary, summary.contains("Wrote a copy"));
    assertTrue(new File(this.folder, "copy.sh3d").isFile());
    assertEquals(2, this.recorder.readHome(new File(this.folder, "copy.sh3d").getPath()).getWalls().size());
    assertTrue(this.home.getName().endsWith("house.sh3d"));
    // But not over a file that exists, nor with a relative path or another extension
    assertTrue(isError("save_home", "{\"copy_to\":\"" + path("copy.sh3d") + "\",\"replace\":true}"));
    assertTrue(isError("save_home", "{\"copy_to\":\"copy2.sh3d\"}"));
    assertTrue(isError("save_home", "{\"copy_to\":\"" + path("copy.txt") + "\"}"));

    this.handler.setSavingAllowed(true);
    assertFalse(isError("save_home", "{\"copy_to\":\"" + path("copy.sh3d") + "\",\"replace\":true}"));
    summary = text("save_home", "{}");
    assertTrue(summary, summary.contains("Saved the home"));
    assertEquals(2, this.recorder.readHome(this.home.getName()).getWalls().size());
    // Saving follows the permission to change things
    this.handler.setEditingAllowed(false);
    assertTrue(isError("save_home", "{}"));
    assertTrue(isError("save_version", "{}"));
    assertFalse(isError("list_versions", "{}"));
    assertFalse(isError("get_history", "{}"));
  }

  @Test
  public void testChangesMadeByHandAreReported() {
    String summary = text("apply_commands", "{\"commands\":[{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":300,\"y2\":0}]}");
    assertFalse(summary, summary.contains("by hand"));
    summary = text("get_changes", "{}");
    assertTrue(summary, summary.contains("changed nothing"));
    // The user draws a wall and moves the one of the client
    Wall handWall = new Wall(0, 200, 300, 200, 10, 250);
    this.home.addWall(handWall);
    this.home.getWalls().iterator().next().setXEnd(350);
    summary = text("get_home", "{}");
    assertTrue(summary, summary.contains("the user changed 2 items by hand"));
    summary = text("get_changes", "{}");
    assertTrue(summary, summary.contains("Added (1)"));
    assertTrue(summary, summary.contains("Modified (1)"));
    assertTrue(summary, summary.contains("W1 wall"));
    assertTrue(summary, summary.contains("W2 wall from (0, 200)"));
    summary = text("get_changes", "{}");
    assertTrue(summary, summary.contains("changed nothing"));
    // A wall added then deleted by hand isn't a change
    Wall other = new Wall(0, 300, 300, 300, 10, 250);
    this.home.addWall(other);
    this.home.deleteWall(other);
    assertTrue(text("get_changes", "{}").contains("changed nothing"));
  }

  @Test
  public void testHistoryStepsNotesAndHomes() {
    for (int i = 0; i < 3; i++) {
      text("apply_commands", "{\"commands\":[{\"action\":\"add_wall\",\"x1\":0,\"y1\":" + (i * 100) + ",\"x2\":300,\"y2\":" + (i * 100) + "}]}");
    }
    assertEquals(3, this.home.getWalls().size());
    String summary = text("get_history", "{}");
    assertTrue(summary, summary.contains("3. "));
    assertFalse(summary, summary.contains("4. "));
    summary = text("undo", "{\"steps\":2}");
    assertTrue(summary, summary.contains("Undid 2 changes"));
    assertEquals(1, this.home.getWalls().size());
    summary = text("get_history", "{}");
    assertTrue(summary, summary.contains("redo would apply again"));
    summary = text("redo", "{\"steps\":5}");
    assertTrue(summary, summary.contains("Redid 2 changes (there were no more)"));
    assertEquals(3, this.home.getWalls().size());
    assertTrue(isError("redo", "{}"));

    summary = text("add_note", "{\"text\":\"Check this wall on site\",\"id\":\"W2\"}");
    assertTrue(summary, summary.contains("Added the note n1 on W2"));
    summary = text("add_note", "{\"text\":\"A tree here?\",\"x\":500,\"y\":500}");
    assertTrue(summary, summary.contains("n2"));
    summary = text("get_notes", "{}");
    assertTrue(summary, summary.contains("Check this wall on site"));
    assertTrue(summary, summary.contains("A tree here?"));
    assertTrue(isError("add_note", "{\"text\":\"x\"}"));
    assertTrue(isError("add_note", "{\"text\":\"x\",\"id\":\"F99\"}"));

    summary = text("list_homes", "{}");
    assertTrue(summary, summary.contains("the home of this connection"));
    // Without the application around it, the handler can't export or open
    assertTrue(isError("export_home", "{\"format\":\"pdf\",\"path\":\"" + path("home.pdf") + "\"}"));
    this.handler.setHomeOperations(new HomeMCPHandler.HomeOperations() {
        public String export(String format, File target, Map<String, Object> options) {
          return "exported " + format + " to " + target.getName();
        }

        public String serve(Home otherHome) {
          return "serving";
        }

        public String startVisit(String mode) {
          return "visit " + mode;
        }
      });
    assertTrue(text("export_home", "{\"format\":\"pdf\",\"path\":\"" + path("home.pdf") + "\"}").contains("exported pdf to home.pdf"));
    assertTrue(isError("export_home", "{\"format\":\"pdf\",\"path\":\"" + path("home.svg") + "\"}"));
    assertTrue(isError("export_home", "{\"format\":\"doc\",\"path\":\"" + path("home.doc") + "\"}"));
    assertTrue(isError("export_home", "{\"format\":\"web_page\",\"path\":\"" + path("missing") + "\"}"));
    assertFalse(isError("export_home", "{\"format\":\"web_page\",\"path\":\"" + this.folder.getPath().replace("\\", "\\\\") + "\"}"));
    assertTrue(text("start_visit", "{\"mode\":\"game\"}").contains("visit game"));
    assertTrue(isError("start_visit", "{\"mode\":\"fly\"}"));
  }
}
