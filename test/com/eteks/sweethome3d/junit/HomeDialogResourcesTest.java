package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.CompassController;
import com.eteks.sweethome3d.viewcontroller.DimensionLineController;
import com.eteks.sweethome3d.viewcontroller.LabelController;
import com.eteks.sweethome3d.viewcontroller.LevelController;
import com.eteks.sweethome3d.viewcontroller.ObserverCameraController;
import com.eteks.sweethome3d.viewcontroller.PolylineController;
import com.eteks.sweethome3d.viewcontroller.RoomController;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Builds the editing dialogs that previously had no construction test, so a
 * missing localized resource key or a constructor error in one of these panels
 * fails the build instead of only crashing when a user opens that dialog. Each
 * panel resolves its strings through {@code getLocalizedString}, which throws on
 * a missing key. Built without the Java 3D preview ({@code no3D}) so it runs in
 * the GUI suite without a real OpenGL context.
 */
public class HomeDialogResourcesTest {
  @Test
  public void testEditingDialogsBuild() {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      UserPreferences preferences = new DefaultUserPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      Home home = new Home();

      Room room = new Room(new float [][] {{0, 0}, {100, 0}, {100, 100}});
      home.addRoom(room);
      Label label = new Label("Label", 0, 0);
      home.addLabel(label);
      DimensionLine dimensionLine = new DimensionLine(0, 0, 100, 0, 20);
      home.addDimensionLine(dimensionLine);
      Polyline polyline = new Polyline(new float [][] {{0, 0}, {100, 100}});
      home.addPolyline(polyline);

      home.setSelectedItems(Arrays.asList(room));
      assertNotNull(new RoomController(home, preferences, viewFactory, null, null).getView());
      home.setSelectedItems(Arrays.asList(label));
      assertNotNull(new LabelController(home, preferences, viewFactory, null).getView());
      home.setSelectedItems(Arrays.asList(dimensionLine));
      assertNotNull(new DimensionLineController(home, preferences, viewFactory, null).getView());
      home.setSelectedItems(Arrays.asList(polyline));
      assertNotNull(new PolylineController(home, preferences, viewFactory, null, null).getView());

      assertNotNull(new LevelController(home, preferences, viewFactory, null).getView());
      assertNotNull(new CompassController(home, preferences, viewFactory, null).getView());
      assertNotNull(new ObserverCameraController(home, preferences, viewFactory).getView());
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }
}
