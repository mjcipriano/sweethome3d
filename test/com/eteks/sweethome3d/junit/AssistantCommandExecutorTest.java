package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
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
