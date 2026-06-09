package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.UserPreferencesController;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Verifies the user-interface theme preference is wired through the model and
 * the preferences controller: it defaults to the modern light theme and
 * round-trips from the controller back into the preferences when applied. The
 * preferences view is created lazily, so this runs without an OpenGL context.
 */
public class LookAndFeelPreferenceTest {
  @Test
  public void testThemeDefaultAndControllerRoundTrip() {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      UserPreferences preferences = new DefaultUserPreferences();
      assertEquals("Default theme should be the modern light look",
          "light", preferences.getLookAndFeel());

      ViewFactory viewFactory = new SwingViewFactory();
      UserPreferencesController controller =
          new UserPreferencesController(preferences, viewFactory, null);
      assertEquals("Controller should read the current theme",
          "light", controller.getLookAndFeel());

      // Edit the theme in the controller and apply it to the preferences.
      controller.setLookAndFeel("dark");
      controller.modifyUserPreferences();
      assertEquals("Applying preferences should store the edited theme",
          "dark", preferences.getLookAndFeel());
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }
}
