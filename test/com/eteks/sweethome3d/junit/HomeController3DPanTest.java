package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import javax.swing.undo.UndoableEditSupport;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Verifies that panning the aerial (top) camera with a middle-button drag moves
 * the point the camera turns around and, crucially, that the camera stays there
 * when the home is updated. The aerial camera position is recomputed from the
 * home bounds center on every object/selection change, so before panning kept a
 * persistent offset the view always snapped back to the home center, making it
 * impossible to frame an off-center area while editing.
 */
public class HomeController3DPanTest {
  @Test
  public void testAerialViewPanPersistsAcrossHomeUpdates() {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      Home home = new Home();
      // Give the home a fixed footprint so the bounds center doesn't move when
      // we trigger a camera update below.
      Wall wall = new Wall(0, 0, 400, 0, 7.5f, 250);
      home.addWall(wall);
      home.addWall(new Wall(400, 0, 400, 300, 7.5f, 250));

      UserPreferences preferences = new DefaultUserPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      // The 3D view is created lazily, so no OpenGL context is needed here; the
      // camera math runs entirely in the controller. The default camera is the
      // top (aerial) camera.
      HomeController3D controller = new HomeController3D(home, preferences,
          viewFactory, null, new UndoableEditSupport());
      assertTrue("Expected the aerial top camera by default",
          home.getCamera() == home.getTopCamera());

      float xBeforePan = home.getCamera().getX();
      float yBeforePan = home.getCamera().getY();

      // Pan the view to the right and down with a middle-button drag.
      controller.panCamera(160, 90);

      float xAfterPan = home.getCamera().getX();
      float yAfterPan = home.getCamera().getY();
      double panShift = Math.hypot(xAfterPan - xBeforePan, yAfterPan - yBeforePan);
      assertTrue("Panning should move the aerial camera", panShift > 1f);

      // Trigger a camera update the same way editing does (a selection change
      // recomputes the aerial camera from the home bounds) without changing the
      // bounds, then check the camera stayed where the pan left it.
      home.setSelectedItems(Arrays.<Selectable>asList(wall));
      home.setSelectedItems(Arrays.<Selectable>asList());

      assertEquals("Pan must survive a home update along X (no snap back)",
          xAfterPan, home.getCamera().getX(), 1e-3);
      assertEquals("Pan must survive a home update along Y (no snap back)",
          yAfterPan, home.getCamera().getY(), 1e-3);
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }
}
