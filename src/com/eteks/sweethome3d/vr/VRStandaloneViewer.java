/*
 * VRStandaloneViewer.java
 *
 * A minimal “viewer” companion that opens a .sh3d file in a full-screen
 * 3D-first window. This is intended for desktop-to-HMD streaming (e.g., Quest
 * via Steam desktop/Link) rather than native OpenXR. It reuses the existing
 * Swing / Java3D pipeline.
 */
package com.eteks.sweethome3d.vr;

import java.io.File;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.io.FileUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;
import com.eteks.sweethome3d.viewcontroller.HomeView;

public class VRStandaloneViewer {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: VRStandaloneViewer <path-to-home.sh3d>");
      System.exit(1);
    }
    String path = args[0];
    File homeFile = new File(path);
    if (!homeFile.exists()) {
      System.err.println("File not found: " + path);
      System.exit(1);
    }

    FileUserPreferences preferences = new FileUserPreferences();
    HomeFileRecorder recorder = new HomeFileRecorder();
    final Home home = recorder.readHome(homeFile.getAbsolutePath());

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        try {
          SwingViewFactory viewFactory = new SwingViewFactory();
          HomeController controller = new HomeController(home, preferences, viewFactory);
          HomeView homeView = controller.getView();
          JRootPane rootPane = (JRootPane)homeView;
          JFrame frame = new JFrame("Sweet Home 3D - VR Viewer");
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          frame.setContentPane(rootPane);
          frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
          frame.setVisible(true);
          controller.getHomeController3D().setViewMode(HomeController3D.ViewMode.VIRTUAL_VISITOR);
        } catch (Exception ex) {
          ex.printStackTrace();
          System.exit(1);
        }
      }
    });
  }
}
