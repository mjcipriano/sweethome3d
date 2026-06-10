package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
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
}
