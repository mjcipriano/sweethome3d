package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.viewcontroller.AssistantCommand;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Verifies the assistant command executor applies add commands to a home and
 * that all the edits of one assistant turn are reverted by a single undo.
 */
public class AssistantCommandExecutorTest {
  @Test
  public void testAddCommandsApplyAndUndoAsOneStep() {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      UserPreferences preferences = new DefaultUserPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);

      // Two walls and a room added in one turn (furniture needs a populated
      // catalog, which isn't available in the headless test classpath)
      String response = "{\"reply\":\"Added a room and two walls.\",\"commands\":["
          + "{\"action\":\"add_room\",\"name\":\"Patio\",\"points\":[[0,0],[400,0],[400,300],[0,300]]},"
          + "{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":400,\"y2\":0},"
          + "{\"action\":\"add_wall\",\"x1\":400,\"y1\":0,\"x2\":400,\"y2\":300}]}";
      List<AssistantCommand> commands = AssistantCommandParser.parse(response).getCommands();
      assertEquals(3, commands.size());

      String summary = executor.execute(commands);
      assertTrue(summary, summary != null && summary.startsWith("Applied changes"));
      assertEquals("One room added", 1, home.getRooms().size());
      assertEquals("Two walls added", 2, home.getWalls().size());

      // A single Undo must revert the whole assistant turn
      homeController.undo();
      assertEquals("Room reverted", 0, home.getRooms().size());
      assertEquals("Walls reverted", 0, home.getWalls().size());
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }

  /**
   * Verifies the scored catalog matching used by add_furniture and the
   * search_catalog command: word matches beat substring matches, plural forms
   * match, and search results / closest-name suggestions reach the summary the
   * agentic loop feeds back to the model.
   */
  @Test
  public void testCatalogGroundingAndSearch() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      UserPreferences preferences = new DefaultUserPreferences();
      Content icon = new URLContent(new File("dummy.png").toURI().toURL());
      Content model = new URLContent(new File("dummy.obj").toURI().toURL());
      FurnitureCategory living = new FurnitureCategory("Living room");
      preferences.getFurnitureCatalog().add(living,
          new CatalogPieceOfFurniture("Sofa", icon, model, 200, 90, 80, true, false));
      preferences.getFurnitureCatalog().add(living,
          new CatalogPieceOfFurniture("Coffee table", icon, model, 110, 60, 45, true, false));
      preferences.getFurnitureCatalog().add(living,
          new CatalogPieceOfFurniture("Round coffee table", icon, model, 90, 90, 45, true, false));
      FurnitureCategory office = new FurnitureCategory("Office");
      preferences.getFurnitureCatalog().add(office,
          new CatalogPieceOfFurniture("Office chair", icon, model, 60, 60, 100, true, false));
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);

      // Exact and word-based matching (including plural). The default catalog
      // may also be populated, so queries use the synthetic names.
      assertEquals("Sofa", executor.findCatalogPiece("sofa", false).getName());
      assertEquals("Coffee table", executor.findCatalogPiece("coffee table", false).getName());
      assertEquals("A word match beats a longer name",
          "Coffee table", executor.findCatalogPiece("coffee", false).getName());
      assertEquals("Plural matches singular catalog name",
          "Office chair", executor.findCatalogPiece("office chairs", false).getName());
      assertNull("No shared word means no match",
          executor.findCatalogPiece("xyzzy frobnicator", false));

      // search_catalog results are reported in the summary for the agentic loop
      String response = "{\"commands\":[{\"action\":\"search_catalog\",\"query\":\"coffee table\"}]}";
      String summary = executor.execute(AssistantCommandParser.parse(response).getCommands());
      assertNotNull(summary);
      assertTrue(summary, summary.contains("Catalog search \"coffee table\""));
      assertTrue(summary, summary.contains("Coffee table"));
      assertTrue(summary, summary.contains("Round coffee table"));

      // A fuzzy add_furniture name is grounded to the best catalog match
      response = "{\"commands\":[{\"action\":\"add_furniture\",\"name\":\"round coffee tables\",\"x\":0,\"y\":0}]}";
      summary = executor.execute(AssistantCommandParser.parse(response).getCommands());
      assertTrue(summary, summary.contains("added 1 piece of furniture"));
      assertEquals(1, home.getFurniture().size());
      assertEquals("Round coffee table", home.getFurniture().get(0).getName());

      response = "{\"commands\":[{\"action\":\"add_furniture\",\"name\":\"xyzzy frobnicator\",\"x\":0,\"y\":0}]}";
      summary = executor.execute(AssistantCommandParser.parse(response).getCommands());
      assertNotNull(summary);
      assertTrue(summary, summary.contains("Couldn't find furniture named"));
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }

  /**
   * Verifies the per-turn safety limits: nonsense coordinates are rejected with
   * a note and at most 30 commands of a turn are applied.
   */
  @Test
  public void testSafetyLimits() {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      UserPreferences preferences = new DefaultUserPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);

      // A wall with a nonsense coordinate is ignored and noted
      String response = "{\"commands\":["
          + "{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":99999999,\"y2\":0},"
          + "{\"action\":\"add_wall\",\"x1\":0,\"y1\":0,\"x2\":400,\"y2\":0}]}";
      String summary = executor.execute(AssistantCommandParser.parse(response).getCommands());
      assertEquals("Only the sane wall is added", 1, home.getWalls().size());
      assertTrue(summary, summary.contains("out-of-range"));

      // A turn with more than 30 commands stops at the limit
      StringBuilder big = new StringBuilder("{\"commands\":[");
      for (int i = 0; i < 40; i++) {
        if (i > 0) {
          big.append(',');
        }
        big.append("{\"action\":\"add_wall\",\"x1\":0,\"y1\":").append(i * 10)
           .append(",\"x2\":100,\"y2\":").append(i * 10).append('}');
      }
      big.append("]}");
      home.getWalls();
      int wallsBefore = home.getWalls().size();
      summary = executor.execute(AssistantCommandParser.parse(big.toString()).getCommands());
      assertEquals("Turn stops at the 30-command limit",
          wallsBefore + 30, home.getWalls().size());
      assertTrue(summary, summary.contains("turn limit"));
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }

  @Test
  public void testModifyAndDeleteApplyAndUndoAsOneStep() {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      UserPreferences preferences = new DefaultUserPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(home, preferences, viewFactory);
      AssistantCommandExecutor executor =
          new AssistantCommandExecutor(home, preferences, homeController);

      // Items addressed by the F#/W#/R# ids exposed by HomeAssistantContext
      home.addRoom(new Room(new float[][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}})); // R1
      Wall wall1 = new Wall(0, 0, 400, 0, 7.5f, 250); // W1
      Wall wall2 = new Wall(400, 0, 400, 300, 7.5f, 250); // W2
      home.addWall(wall1);
      home.addWall(wall2);
      float wall1StartX = wall1.getXStart();

      // One turn that moves a wall, thickens another and deletes the room
      String response = "{\"commands\":["
          + "{\"action\":\"move\",\"id\":\"W1\",\"dx\":25,\"dy\":0},"
          + "{\"action\":\"resize\",\"id\":\"W2\",\"thickness\":20},"
          + "{\"action\":\"delete\",\"ids\":[\"R1\"]}]}";
      List<AssistantCommand> commands = AssistantCommandParser.parse(response).getCommands();
      assertEquals(3, commands.size());

      String summary = executor.execute(commands);
      assertTrue(summary, summary != null && summary.startsWith("Applied changes"));
      assertEquals("W1 moved", wall1StartX + 25, wall1.getXStart(), 1e-3);
      assertEquals("W2 thickened", 20f, wall2.getThickness(), 1e-3);
      assertEquals("Room deleted", 0, home.getRooms().size());

      // A single Undo must revert the move, the resize and the deletion together
      homeController.undo();
      assertEquals("W1 move reverted", wall1StartX, wall1.getXStart(), 1e-3);
      assertEquals("W2 thickness reverted", 7.5f, wall2.getThickness(), 1e-3);
      assertEquals("Room restored", 1, home.getRooms().size());
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }
}
