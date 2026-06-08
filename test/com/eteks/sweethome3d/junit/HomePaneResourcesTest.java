package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeView;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Guards against startup crashes caused by a menu or popup item that references a
 * missing localized resource key. Building the home view constructs every menu
 * and popup (including the furniture list popup), and
 * {@code UserPreferences.getLocalizedString} throws an
 * {@code IllegalArgumentException} for a missing key, so this test fails if any
 * menu/popup string is absent from the resource bundles - the failure mode that
 * left the "Visible On / Visible Off" build showing only a splash and no window.
 */
public class HomePaneResourcesTest {
  @Test
  public void testHomeViewBuildsWithAllMenuResources() {
    // Build without the Java 3D view so this runs in the headless/GUI suite
    // without a real OpenGL context; the menus and popups (the strings under test)
    // are built regardless.
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      UserPreferences preferences = new DefaultUserPreferences();
      ViewFactory viewFactory = new SwingViewFactory();
      HomeController homeController = new HomeController(new Home(), preferences, viewFactory);
      HomeView homeView = homeController.getView();
      assertNotNull(homeView);
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }
}
