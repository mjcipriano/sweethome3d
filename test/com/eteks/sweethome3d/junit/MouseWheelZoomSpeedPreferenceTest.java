package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.UserPreferencesController;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Verifies the 3D mouse wheel zoom speed preference is wired through the model
 * and the preferences controller: it has a faster-than-historical default and
 * round-trips from the controller back into the preferences when applied. The
 * preferences view is created lazily, so this runs without an OpenGL context.
 */
public class MouseWheelZoomSpeedPreferenceTest {
  @Test
  public void testZoomSpeedDefaultAndControllerRoundTrip() {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      UserPreferences preferences = new DefaultUserPreferences();
      // The default should be faster than the historical fixed step (1.0).
      assertTrue("Default zoom speed should be faster than 1x",
          preferences.getMouseWheelZoomSpeed() > 1f);

      ViewFactory viewFactory = new SwingViewFactory();
      UserPreferencesController controller =
          new UserPreferencesController(preferences, viewFactory, null);
      assertEquals("Controller should read the current zoom speed",
          preferences.getMouseWheelZoomSpeed(), controller.getMouseWheelZoomSpeed(), 1e-6);

      // Edit the value in the controller and apply it to the preferences.
      controller.setMouseWheelZoomSpeed(4f);
      controller.modifyUserPreferences();
      assertEquals("Applying preferences should store the edited zoom speed",
          4f, preferences.getMouseWheelZoomSpeed(), 1e-6);
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }
}
