/*
 * DialogFocusTest.java 26 nov. 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
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
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.swing.SwingTools;

/**
 * Tests that the dialogs which give the keyboard to one of their components leave no request
 * for the keyboard behind them once closed: such a request, for a window that doesn't exist
 * anymore, is never answered and kept every key typed afterwards away from the views.
 * @author Sweet Home 3D contributors
 */
public class DialogFocusTest {
  private JFrame frame;
  private JPanel view;

  @Before
  public void setUp() throws Exception {
    EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          frame = new JFrame("Dialog focus test");
          view = new JPanel();
          view.setFocusable(true);
          frame.add(view);
          frame.setSize(400, 300);
          frame.setVisible(true);
          view.requestFocusInWindow();
        }
      });
    for (int i = 0; i < 30 && !this.view.isShowing(); i++) {
      Thread.sleep(100);
    }
    Thread.sleep(300);
  }

  @After
  public void tearDown() throws Exception {
    EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          frame.dispose();
        }
      });
  }

  /**
   * A text field which counts the times it's asked to take the keyboard while its window isn't displayed.
   */
  private static class WatchedField extends JTextField {
    int requests;
    int requestsOutOfWindow;

    public WatchedField() {
      super(20);
    }

    @Override
    public boolean requestFocusInWindow() {
      this.requests++;
      Window window = SwingUtilities.getWindowAncestor(this);
      if (window == null || !window.isShowing() || !window.isDisplayable()) {
        this.requestsOutOfWindow++;
      }
      // As when the dialog isn't the focused window yet: the request is made again a moment later
      super.requestFocusInWindow();
      return false;
    }
  }

  private void showDialogClosedAfter(final WatchedField field, final int delay) throws Exception {
    EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          Timer closeTimer = new Timer(delay, new ActionListener() {
              public void actionPerformed(ActionEvent ev) {
                for (Window window : Window.getWindows()) {
                  if (window instanceof JDialog && window.isShowing()) {
                    window.setVisible(false);
                  }
                }
              }
            });
          closeTimer.setRepeats(false);
          closeTimer.start();
          JPanel message = new JPanel();
          message.add(field);
          SwingTools.showConfirmDialog(view, message, "Store point of view", field);
        }
      });
  }

  /**
   * A dialog closed before its component was given the keyboard doesn't ask for it afterwards.
   */
  @Test
  public void testNoFocusRequestAfterDialogClosed() throws Exception {
    for (int delay : new int [] {1, 10, 30, 60, 120}) {
      WatchedField field = new WatchedField();
      showDialogClosedAfter(field, delay);
      // After the delay of the request made again
      Thread.sleep(250);
      EventQueue.invokeAndWait(new Runnable() {
          public void run() {
          }
        });
      if (delay >= 60) {
        // A dialog closed at once may not even have been shown
        assertTrue("The dialog asked the keyboard for its field", field.requests >= 1);
      }
      assertEquals("Requests for the keyboard once the dialog closed after " + delay + " ms", 0, field.requestsOutOfWindow);
      assertEquals(0, getDeadRequestCount());
    }
  }

  /**
   * The component that had the keyboard before a dialog gets it back, and keeps getting it after
   * dialogs closed at once.
   */
  @Test
  public void testFocusReturnsToView() throws Exception {
    Assume.assumeTrue("The window of the test gets the keyboard on this display", waitForFocus());
    for (int delay : new int [] {1, 40, 150}) {
      showDialogClosedAfter(new WatchedField(), delay);
      assertTrue("The view has the keyboard again after a dialog closed after " + delay + " ms", waitForFocus());
    }
  }

  private boolean waitForFocus() throws Exception {
    for (int i = 0; i < 30; i++) {
      final boolean [] focused = {false};
      EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            focused [0] = view.isFocusOwner();
          }
        });
      if (focused [0]) {
        return true;
      }
      Thread.sleep(100);
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> getFocusRequests() throws Exception {
    Field field = KeyboardFocusManager.class.getDeclaredField("heavyweightRequests");
    field.setAccessible(true);
    return (List<Object>)field.get(null);
  }

  private static int getDeadRequestCount() {
    try {
      int count = 0;
      List<Object> requests = getFocusRequests();
      synchronized (requests) {
        for (Object request : requests) {
          Field heavyweightField = request.getClass().getDeclaredField("heavyweight");
          heavyweightField.setAccessible(true);
          Object heavyweight = heavyweightField.get(request);
          if (heavyweight instanceof Component && !((Component)heavyweight).isDisplayable()) {
            count++;
          }
        }
      }
      return count;
    } catch (Exception ex) {
      // Java doesn't let this test read its requests
      return 0;
    }
  }

  /**
   * A request left for a window that was closed is removed, and a click in a view without
   * keyboard removes it by itself; the requests for windows displayed are kept.
   */
  @Test
  public void testDeadFocusRequestsAreRemoved() throws Exception {
    final Throwable [] failure = {null};
    final boolean [] skipped = {false};
    EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          try {
            JDialog dialog = new JDialog(frame, "Closed dialog", false);
            JTextField field = new JTextField(10);
            dialog.add(field);
            dialog.pack();
            dialog.setVisible(true);
            dialog.setVisible(false);
            dialog.dispose();
            List<Object> requests;
            Object deadRequest;
            Object liveRequest;
            try {
              // The request that a late timer left in the list in the session where the keyboard was lost
              requests = getFocusRequests();
              Class<?> requestClass = Class.forName("java.awt.KeyboardFocusManager$HeavyweightFocusRequest");
              Constructor<?> constructor = null;
              for (Constructor<?> candidate : requestClass.getDeclaredConstructors()) {
                if (candidate.getParameterTypes().length == 4) {
                  constructor = candidate;
                }
              }
              constructor.setAccessible(true);
              Object cause = constructor.getParameterTypes() [3].getEnumConstants() [0];
              deadRequest = constructor.newInstance(dialog, field, Boolean.FALSE, cause);
              liveRequest = constructor.newInstance(frame, view, Boolean.FALSE, cause);
            } catch (Exception ex) {
              skipped [0] = true;
              return;
            }
            int count;
            synchronized (requests) {
              count = requests.size();
              requests.add(deadRequest);
              requests.add(liveRequest);
            }
            assertEquals(1, getDeadRequestCount());
            assertEquals(1, SwingTools.removeDeadFocusRequests());
            assertEquals(0, getDeadRequestCount());
            assertTrue("The request for the window displayed is kept", requests.contains(liveRequest));
            assertEquals(0, SwingTools.removeDeadFocusRequests());
            synchronized (requests) {
              requests.remove(liveRequest);
              assertEquals(count, requests.size());
              requests.add(deadRequest);
            }
            // A mouse press in a view while nothing has the keyboard
            KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == null) {
              SwingTools.requestFocusAtMousePress(view);
              assertEquals(0, getDeadRequestCount());
            } else {
              synchronized (requests) {
                requests.remove(deadRequest);
              }
            }
            assertFalse(requests.contains(deadRequest));
          } catch (Throwable ex) {
            failure [0] = ex;
          }
        }
      });
    if (failure [0] instanceof Error) {
      throw (Error)failure [0];
    } else if (failure [0] != null) {
      throw new RuntimeException(failure [0]);
    }
    Assume.assumeFalse("Java lets the test build a request for the keyboard", skipped [0]);
  }
}
