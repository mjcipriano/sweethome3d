/*
 * VRPreviewPlugin.java
 *
 * A lightweight plugin that opens the current home in a full-screen 3D-only
 * preview window, suitable for desktop streaming to a VR headset (e.g., Quest
 * via Steam desktop/Link). It reuses the standard Swing view to avoid touching
 * the existing Java3D pipeline.
 */
package com.eteks.sweethome3d.plugin.vr;

import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;
import com.eteks.sweethome3d.viewcontroller.HomeView;

public class VRPreviewPlugin extends Plugin {
  @Override
  public PluginAction[] getActions() {
    return new PluginAction [] {new OpenVRPreviewAction(getHome())};
  }

  private class OpenVRPreviewAction extends PluginAction {
    private final Home home;

    OpenVRPreviewAction(Home home) {
      this.home = home;
      putPropertyValue(Property.NAME, "VR Preview (Full Screen)");
      putPropertyValue(Property.MENU, "Tools");
    }

    @Override
    public void execute() {
      // Build a fresh controller/view so we don't disturb the main window layout.
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          SwingViewFactory viewFactory = new SwingViewFactory();
          HomeController controller = new HomeController(home, getUserPreferences(), viewFactory);
          HomeView homeView = controller.getView();
          if (!(homeView instanceof JRootPane)) {
            return;
          }
          JRootPane rootPane = (JRootPane)homeView;
          JFrame frame = new JFrame("Sweet Home 3D - VR Preview");
          frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
          frame.setContentPane(rootPane);
          frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
          frame.setVisible(true);
          home.setCamera(home.getObserverCamera());
        }
      });
    }
  }
}
