/*
 * SwingTestCase.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.testing;

import java.awt.EventQueue;
import java.awt.Window;
import java.util.IdentityHashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * A test that displays windows: the ones it opened are hidden when it ends, whether it
 * succeeded or not.
 */
public abstract class SwingTestCase extends TestCase {
  public SwingTestCase() {
  }

  public SwingTestCase(String name) {
    super(name);
  }

  /**
   * Gives <code>window</code> its preferred size, displays it and returns once it's at screen.
   */
  protected void showWindow(final Window window) {
    try {
      EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            window.pack();
            window.setVisible(true);
            window.toFront();
          }
        });
      long end = System.currentTimeMillis() + 10000;
      while (!window.isShowing() && System.currentTimeMillis() < end) {
        Thread.sleep(20);
      }
      // The components of a window get the focus once the window system activated it, which
      // it may do late, or undo if another window closes at the same time: ask again then
      end = System.currentTimeMillis() + 8000;
      long nextRequest = System.currentTimeMillis() + 500;
      int requests = 0;
      while (System.currentTimeMillis() < end) {
        if (window.isActive()) {
          // Then the component that has the focus by default
          long focusEnd = System.currentTimeMillis() + 1000;
          while (window.isActive()
                 && (window.getFocusOwner() == null || window.getFocusOwner() == window)
                 && System.currentTimeMillis() < focusEnd) {
            Thread.sleep(20);
          }
          // Still active a moment later
          Thread.sleep(150);
          if (window.isActive()) {
            break;
          }
        }
        Thread.sleep(20);
        if (System.currentTimeMillis() > nextRequest) {
          nextRequest = System.currentTimeMillis() + 500;
          final boolean shownAgain = ++requests % 4 == 0;
          EventQueue.invokeAndWait(new Runnable() {
              public void run() {
                if (shownAgain) {
                  // A window that appears is more often activated than one that asks for it
                  window.setVisible(false);
                  window.setVisible(true);
                }
                window.toFront();
                window.requestFocus();
              }
            });
        }
      }
      new SwingRobot().waitForIdle();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    } catch (java.lang.reflect.InvocationTargetException ex) {
      throw new IllegalStateException(ex.getCause());
    }
  }

  @Override
  public void runBare() throws Throwable {
    final Map<Window, Boolean> previousWindows = new IdentityHashMap<Window, Boolean>();
    for (Window window : Window.getWindows()) {
      previousWindows.put(window, Boolean.TRUE);
    }
    try {
      super.runBare();
    } catch (Throwable ex) {
      // Shown at once: if a following test waits forever, JUnit never reports this failure
      System.err.println("Failure in " + getClass().getName() + "." + getName());
      ex.printStackTrace();
      // What often explains it: which window is active, and where the focus is
      java.awt.KeyboardFocusManager focusManager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Window activeWindow = focusManager.getActiveWindow();
      java.awt.Component focusOwner = focusManager.getFocusOwner();
      System.err.println("Active window: " + (activeWindow != null ? activeWindow.getClass().getName() + " " + activeWindow.getName() : "none")
          + ", focus owner: " + (focusOwner != null ? focusOwner.getClass().getName() : "none"));
      for (Window window : Window.getWindows()) {
        if (window.isVisible()) {
          System.err.println("Visible window: " + window.getClass().getName() + " " + window.getName()
              + (window instanceof java.awt.Frame ? " \"" + ((java.awt.Frame)window).getTitle() + "\"" : "")
              + (window instanceof java.awt.Dialog ? " \"" + ((java.awt.Dialog)window).getTitle() + "\" modal=" + ((java.awt.Dialog)window).isModal() : "")
              + (previousWindows.containsKey(window) ? " (opened before this test)" : ""));
        }
      }
      throw ex;
    } finally {
      EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            for (Window window : Window.getWindows()) {
              // Hidden and not disposed: disposing a window with a 3D view waits for
              // Java 3D to release its context, which may never end under some graphics drivers
              if (!previousWindows.containsKey(window) && window.isVisible()) {
                window.setVisible(false);
              }
            }
          }
        });
      // The time for the window system to choose the window that follows the hidden ones,
      // before the next test shows its own one
      java.awt.Toolkit.getDefaultToolkit().sync();
      Thread.sleep(150);
    }
  }
}
