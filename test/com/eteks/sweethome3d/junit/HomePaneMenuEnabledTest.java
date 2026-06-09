package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;

import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeView;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Guards the drag-and-drop menu disable/re-enable cycle. A left-button press in
 * the plan view disables every menu (so a drag can't trigger a shortcut), and the
 * matching release re-enables them. The re-enable pass used to call
 * {@code item.getAction().isEnabled()} on every leaf item, which threw a
 * {@code NullPointerException} for items driven by a listener instead of an
 * {@code Action} (the "Display 3D view" toggle). That exception aborted the loop
 * inside its {@code invokeLater} runnable, leaving every menu after the 3D View
 * menu - the plugin Tools menu and the Help menu - stuck disabled. This test
 * fails if a press/release cycle ever leaves an item that should be enabled
 * disabled.
 */
public class HomePaneMenuEnabledTest {
  @Test
  public void testMenusReenabledAfterPlanDragCycle() throws Exception {
    String previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    try {
      final UserPreferences preferences = new DefaultUserPreferences();
      final ViewFactory viewFactory = new SwingViewFactory();
      final HomeController[] controllerHolder = new HomeController[1];
      EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            controllerHolder[0] = new HomeController(new Home(), preferences, viewFactory);
          }
        });
      HomeController homeController = controllerHolder[0];
      HomeView homeView = homeController.getView();
      JMenuBar menuBar = ((JRootPane)homeView).getJMenuBar();

      // The real "Display 3D view" toggle that triggered the bug is a
      // listener-driven JCheckBoxMenuItem with no Action, but it lives in the 3D
      // View menu, which is only added when the Java 3D view exists - so it is
      // absent from this headless no3D suite. Reproduce the exact structural
      // condition by inserting an equivalent actionless item at the top of the
      // first menu: every other menu then follows it, so a re-enable pass that
      // throws on the actionless item would leave all later menus disabled.
      JCheckBoxMenuItem actionlessItem = new JCheckBoxMenuItem("Listener-driven item");
      assertTrue("Injected item must have no Action to exercise the null path",
          actionlessItem.getAction() == null);
      menuBar.getMenu(0).insert(actionlessItem, 0);

      // The drag listener is attached to the plan view (a MultipleLevelsPlanPanel),
      // which forwards mouse events to its inner PlanComponent through wrapper
      // listeners it declares. Fire the cycle through those wrappers so we exercise
      // exactly the menu disable/re-enable path and nothing else.
      Component planPanel = (Component)homeController.getPlanController().getView();
      Component planComponent = findByClassName(planPanel, "com.eteks.sweethome3d.swing.PlanComponent");
      assertNotNull("Expected a PlanComponent in the plan view", planComponent);
      List<MouseListener> forwardingListeners = new ArrayList<MouseListener>();
      for (MouseListener listener : planComponent.getMouseListeners()) {
        if (listener.getClass().getName().startsWith(
            "com.eteks.sweethome3d.swing.MultipleLevelsPlanPanel")) {
          forwardingListeners.add(listener);
        }
      }
      assertTrue("Expected the plan view's forwarding mouse listener",
          !forwardingListeners.isEmpty());

      MouseEvent press = leftButtonEvent(planComponent, MouseEvent.MOUSE_PRESSED);
      MouseEvent release = leftButtonEvent(planComponent, MouseEvent.MOUSE_RELEASED);

      for (MouseListener listener : forwardingListeners) {
        listener.mousePressed(press);
      }
      flushEventQueue();
      assertTrue("Press should disable the menus", anyLeafItemDisabled(menuBar));

      for (MouseListener listener : forwardingListeners) {
        listener.mouseReleased(release);
      }
      flushEventQueue();

      // Every leaf item whose Action is enabled (or which has no Action) must be
      // enabled again - including the Tools and Help menus after the 3D View menu.
      List<JMenuItem> stuck = collectWronglyDisabledItems(menuBar);
      if (!stuck.isEmpty()) {
        StringBuilder message = new StringBuilder(
            "These menu items stayed disabled after the release re-enable pass: ");
        for (JMenuItem item : stuck) {
          message.append('"').append(item.getText()).append("\" ");
        }
        fail(message.toString().trim());
      }
    } finally {
      if (previousNo3D == null) {
        System.clearProperty("com.eteks.sweethome3d.no3D");
      } else {
        System.setProperty("com.eteks.sweethome3d.no3D", previousNo3D);
      }
    }
  }

  private static Component findByClassName(Component component, String className) {
    if (component.getClass().getName().equals(className)) {
      return component;
    }
    if (component instanceof Container) {
      for (Component child : ((Container)component).getComponents()) {
        Component match = findByClassName(child, className);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }

  private static MouseEvent leftButtonEvent(Component source, int id) {
    return new MouseEvent(source, id, System.currentTimeMillis(),
        InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON1_MASK,
        10, 10, 1, false, MouseEvent.BUTTON1);
  }

  private static void flushEventQueue() throws Exception {
    // setMenusEnabled posts its work with invokeLater, so drain the queue twice.
    EventQueue.invokeAndWait(new Runnable() {
        public void run() {
        }
      });
    EventQueue.invokeAndWait(new Runnable() {
        public void run() {
        }
      });
  }

  private static boolean anyLeafItemDisabled(JMenuBar menuBar) {
    for (int i = 0, n = menuBar.getMenuCount(); i < n; i++) {
      if (anyLeafItemDisabled(menuBar.getMenu(i))) {
        return true;
      }
    }
    return false;
  }

  private static boolean anyLeafItemDisabled(JMenu menu) {
    for (int i = 0, n = menu.getItemCount(); i < n; i++) {
      JMenuItem item = menu.getItem(i);
      if (item instanceof JMenu) {
        if (anyLeafItemDisabled((JMenu)item)) {
          return true;
        }
      } else if (item != null && !item.isEnabled()) {
        return true;
      }
    }
    return false;
  }

  private static List<JMenuItem> collectWronglyDisabledItems(JMenuBar menuBar) {
    List<JMenuItem> stuck = new ArrayList<JMenuItem>();
    for (int i = 0, n = menuBar.getMenuCount(); i < n; i++) {
      collectWronglyDisabledItems(menuBar.getMenu(i), stuck);
    }
    return stuck;
  }

  private static void collectWronglyDisabledItems(JMenu menu, List<JMenuItem> stuck) {
    for (int i = 0, n = menu.getItemCount(); i < n; i++) {
      JMenuItem item = menu.getItem(i);
      if (item instanceof JMenu) {
        collectWronglyDisabledItems((JMenu)item, stuck);
      } else if (item != null && !item.isEnabled()) {
        Action action = item.getAction();
        // An item should be enabled again unless its own Action is disabled.
        if (action == null || action.isEnabled()) {
          stuck.add(item);
        }
      }
    }
  }
}
