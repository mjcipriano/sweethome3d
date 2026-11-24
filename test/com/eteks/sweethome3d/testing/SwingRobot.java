/*
 * SwingRobot.java
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

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFileChooser;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * Plays the user in the tests of the user interface. The clicks, the moves of the mouse and
 * the keys are events posted to the components, as the toolkit posts the ones of the user:
 * the pointer of the screen doesn't move, and the windows of other programs change nothing.
 * Only a drag and drop uses the true pointer, with {@link Robot}. Each action returns
 * once the program handled it.
 */
public class SwingRobot {
  private static final int WINDOW_TIMEOUT = 10000;
  private static final int FOCUS_TIMEOUT = 2000;

  private static boolean pointerIgnored;
  private static boolean pointerFilterInstalled;

  private Robot     robot;
  private int       keyModifiers;
  private int       pressedButton;
  private Component pressedComponent;
  private Point     pressedPoint;
  private boolean   dragged;
  private Component lastComponent;

  public SwingRobot() {
    ignorePointer(true);
  }

  /**
   * Chooses whether the events of the true pointer of the screen are ignored. They are while
   * the tests post their own ones: the pointer may be above a window of the test, and a move
   * of the mouse of the user between two events of a test would change what the test does.
   */
  private static synchronized void ignorePointer(boolean ignored) {
    pointerIgnored = ignored;
    if (ignored && !pointerFilterInstalled) {
      pointerFilterInstalled = true;
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
          @Override
          protected void dispatchEvent(java.awt.AWTEvent event) {
            if (!(event instanceof MouseEvent)
                || event instanceof PostedMouseEvent
                || !isPointerIgnored()) {
              super.dispatchEvent(event);
            }
          }
        });
    }
  }

  private static synchronized boolean isPointerIgnored() {
    return pointerIgnored;
  }

  /**
   * An event of the mouse posted by a test.
   */
  private static class PostedMouseEvent extends MouseEvent {
    public PostedMouseEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int clickCount, int button) {
      super(source, id, when, modifiers, x, y, clickCount, false, button);
    }
  }

  // ---- Event dispatch thread

  /**
   * Runs <code>runnable</code> in the event dispatch thread and returns once it's done
   * and the events it posted are handled: if it closes a modal dialog, what follows the
   * display of this dialog in the program has run when this method returns.
   */
  public void invokeAndWait(Runnable runnable) {
    run(runnable);
    waitForIdle();
  }

  private void run(Runnable runnable) {
    if (EventQueue.isDispatchThread()) {
      runnable.run();
    } else {
      try {
        EventQueue.invokeAndWait(runnable);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(ex);
      } catch (InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException)cause;
        } else if (cause instanceof Error) {
          throw (Error)cause;
        } else {
          throw new IllegalStateException(cause);
        }
      }
    }
  }

  /**
   * Runs <code>runnable</code> later in the event dispatch thread, without waiting for it:
   * it may show a modal dialog the test will handle.
   */
  public void invokeLater(Runnable runnable) {
    EventQueue.invokeLater(runnable);
  }

  /**
   * Returns when the events waiting in the queue are handled, the ones they posted too.
   */
  public void waitForIdle() {
    if (!EventQueue.isDispatchThread()) {
      Runnable nothing = new Runnable() {
          public void run() {
          }
        };
      for (int i = 0; i < 3; i++) {
        Toolkit.getDefaultToolkit().sync();
        run(nothing);
        if (i == 0) {
          // The time for the window system to answer what the program asked it, as a change of focus
          sleep(20);
        }
      }
    }
  }

  /**
   * Asks the window system to activate the window of <code>component</code> if it isn't the active
   * one, and waits for it: a click of the user would activate it, and only the components of the
   * active window get the focus.
   */
  private void activateWindow(final Component component) {
    final Window window = component instanceof Window
        ? (Window)component
        : SwingUtilities.getWindowAncestor(component);
    if (window != null && !window.isActive() && window.isShowing()
        && (window instanceof java.awt.Frame || window instanceof java.awt.Dialog)) {
      run(new Runnable() {
          public void run() {
            window.toFront();
            window.requestFocus();
          }
        });
      long end = System.currentTimeMillis() + FOCUS_TIMEOUT;
      while (!window.isActive() && System.currentTimeMillis() < end) {
        sleep(10);
      }
    }
  }

  /**
   * Waits for a frame or a dialog with the given title to be displayed.
   * @throws IllegalStateException if none shows in time
   */
  public void waitForWindowShowing(String title) {
    WindowTitleMatcher matcher = new WindowTitleMatcher(title);
    long end = System.currentTimeMillis() + WINDOW_TIMEOUT;
    while (true) {
      for (Window window : Window.getWindows()) {
        if (matcher.matches(window)) {
          waitForIdle();
          return;
        }
      }
      if (System.currentTimeMillis() > end) {
        throw new IllegalStateException("No window titled " + title + " showing");
      }
      sleep(20);
    }
  }

  /**
   * Asks <code>window</code> to close, as its close box does.
   */
  public void close(final Window window) {
    post(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
    waitForIdle();
  }

  private static void sleep(int milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  private static void post(java.awt.AWTEvent event) {
    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(event);
  }

  // ---- Mouse

  /**
   * Clicks at the center of <code>component</code>.
   */
  public void actionClick(Component component) {
    actionClick(component, component.getWidth() / 2, component.getHeight() / 2);
  }

  public void actionClick(Component component, int x, int y) {
    actionClick(component, x, y, InputEvent.BUTTON1_DOWN_MASK, 1);
  }

  public void actionClick(Component component, ComponentPoint location) {
    actionClick(component, location, InputEvent.BUTTON1_DOWN_MASK, 1);
  }

  public void actionClick(Component component, ComponentPoint location, int buttons, int count) {
    Point point = location.getPoint();
    actionClick(component, point.x, point.y, buttons, count);
  }

  /**
   * Clicks <code>count</code> times at the point (<code>x</code>, <code>y</code>) of <code>component</code>.
   * @param buttons the button to press, given with a constant of {@link InputEvent}
   */
  public void actionClick(Component component, int x, int y, int buttons, int count) {
    int button = getButton(buttons);
    activateWindow(component);
    Object [] target = getTarget(component, x, y);
    Component targetComponent = (Component)target [0];
    Point point = (Point)target [1];
    postMouseEvent(targetComponent, MouseEvent.MOUSE_MOVED, this.keyModifiers, point, 0, MouseEvent.NOBUTTON);
    for (int i = 1; i <= count; i++) {
      postMouseEvent(targetComponent, MouseEvent.MOUSE_PRESSED,
          this.keyModifiers | getButtonMask(button), point, i, button);
      postMouseEvent(targetComponent, MouseEvent.MOUSE_RELEASED, this.keyModifiers, point, i, button);
      postMouseEvent(targetComponent, MouseEvent.MOUSE_CLICKED, this.keyModifiers, point, i, button);
    }
    this.lastComponent = targetComponent;
    waitForIdle();
    waitForFocus(targetComponent);
  }

  /**
   * Moves the pointer to a point of <code>component</code>: if a button is pressed,
   * the component where it was pressed is told that the pointer is dragged.
   */
  public void actionMouseMove(Component component, ComponentPoint location) {
    Point point = location.getPoint();
    if (this.pressedComponent != null) {
      Point draggedPoint = SwingUtilities.convertPoint(component, point, this.pressedComponent);
      postMouseEvent(this.pressedComponent, MouseEvent.MOUSE_DRAGGED,
          this.keyModifiers | getButtonMask(this.pressedButton), draggedPoint, 0, MouseEvent.NOBUTTON);
      this.pressedPoint = draggedPoint;
      this.dragged = true;
    } else {
      Object [] target = getTarget(component, point.x, point.y);
      postMouseEvent((Component)target [0], MouseEvent.MOUSE_MOVED, this.keyModifiers,
          (Point)target [1], 0, MouseEvent.NOBUTTON);
    }
    waitForIdle();
  }

  /**
   * Presses the left button at a point of <code>component</code> and keeps it pressed.
   */
  public void actionMousePress(Component component, ComponentPoint location) {
    Point point = location.getPoint();
    activateWindow(component);
    Object [] target = getTarget(component, point.x, point.y);
    this.pressedComponent = (Component)target [0];
    this.pressedPoint = (Point)target [1];
    this.pressedButton = MouseEvent.BUTTON1;
    this.dragged = false;
    postMouseEvent(this.pressedComponent, MouseEvent.MOUSE_MOVED, this.keyModifiers,
        this.pressedPoint, 0, MouseEvent.NOBUTTON);
    postMouseEvent(this.pressedComponent, MouseEvent.MOUSE_PRESSED,
        this.keyModifiers | getButtonMask(this.pressedButton), this.pressedPoint, 1, this.pressedButton);
    this.lastComponent = this.pressedComponent;
    waitForIdle();
    waitForFocus(this.pressedComponent);
  }

  /**
   * Releases the button pressed by {@link #actionMousePress(Component, ComponentPoint) actionMousePress}.
   */
  public void actionMouseRelease() {
    if (this.pressedComponent != null) {
      postMouseEvent(this.pressedComponent, MouseEvent.MOUSE_RELEASED, this.keyModifiers,
          this.pressedPoint, 1, this.pressedButton);
      if (!this.dragged) {
        postMouseEvent(this.pressedComponent, MouseEvent.MOUSE_CLICKED, this.keyModifiers,
            this.pressedPoint, 1, this.pressedButton);
      }
      this.pressedComponent = null;
    }
    waitForIdle();
  }

  /**
   * Gives a moment to the focus to come to a component that was clicked, if it may have it:
   * most components ask for it when they are clicked, and get it once the window system agreed.
   */
  private void waitForFocus(Component component) {
    if (component.isFocusable() && component.isShowing() && component.isEnabled()) {
      KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Component focusOwner = focusManager.getFocusOwner();
      long end = System.currentTimeMillis() + 300;
      while (focusManager.getFocusOwner() == focusOwner
             && focusOwner != component
             && System.currentTimeMillis() < end) {
        sleep(10);
      }
      waitForIdle();
    }
  }

  private void postMouseEvent(Component component, int id, int modifiers, Point point, int clickCount, int button) {
    post(new PostedMouseEvent(component, id, System.currentTimeMillis(), modifiers,
        point.x, point.y, clickCount, button));
  }

  /**
   * Returns the component that would receive the events of the mouse at the point
   * (<code>x</code>, <code>y</code>) of <code>component</code>, and this point in its coordinates.
   */
  private Object [] getTarget(final Component component, final int x, final int y) {
    waitForIdle();
    final Object [] target = {component, new Point(x, y)};
    run(new Runnable() {
        public void run() {
          if (component instanceof Container) {
            Component deepestComponent = SwingUtilities.getDeepestComponentAt(component, x, y);
            if (deepestComponent != null && deepestComponent != component) {
              target [0] = deepestComponent;
              target [1] = SwingUtilities.convertPoint(component, x, y, deepestComponent);
            }
          }
        }
      });
    return target;
  }

  private static int getButton(int buttons) {
    if ((buttons & (InputEvent.BUTTON3_MASK | InputEvent.BUTTON3_DOWN_MASK)) != 0) {
      return MouseEvent.BUTTON3;
    } else if ((buttons & (InputEvent.BUTTON2_MASK | InputEvent.BUTTON2_DOWN_MASK)) != 0) {
      return MouseEvent.BUTTON2;
    } else {
      return MouseEvent.BUTTON1;
    }
  }

  private static int getButtonMask(int button) {
    return button == MouseEvent.BUTTON3
        ? InputEvent.BUTTON3_DOWN_MASK
        : button == MouseEvent.BUTTON2
            ? InputEvent.BUTTON2_DOWN_MASK
            : InputEvent.BUTTON1_DOWN_MASK;
  }

  // ---- Drag and drop, with the true pointer: the toolkit handles it out of the queue of events

  private Robot getRobot() {
    if (this.robot == null) {
      try {
        this.robot = new Robot();
        this.robot.setAutoDelay(10);
      } catch (AWTException ex) {
        throw new IllegalStateException("No way to move the pointer at this screen", ex);
      }
    }
    return this.robot;
  }

  /**
   * Starts to drag what is at a point of <code>component</code>.
   */
  public void actionDrag(final Component component, ComponentPoint location) {
    final Point point = location.getPoint();
    waitForIdle();
    final Point [] screenPoint = new Point [1];
    run(new Runnable() {
        public void run() {
          Window window = SwingUtilities.getWindowAncestor(component);
          if (window != null) {
            window.toFront();
          }
          screenPoint [0] = new Point(point);
          SwingUtilities.convertPointToScreen(screenPoint [0], component);
        }
      });
    waitForIdle();
    // The toolkit follows the true pointer during a drag and drop
    ignorePointer(false);
    Robot robot = getRobot();
    robot.mouseMove(screenPoint [0].x - 1, screenPoint [0].y);
    robot.mouseMove(screenPoint [0].x, screenPoint [0].y);
    robot.waitForIdle();
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
    robot.waitForIdle();
    // Far enough for the gesture to be recognized as a drag
    for (int i = 1; i <= 12; i++) {
      robot.mouseMove(screenPoint [0].x + i, screenPoint [0].y + i);
      sleep(10);
    }
    robot.waitForIdle();
  }

  /**
   * Drops at a point of <code>component</code> what {@link #actionDrag(Component, ComponentPoint) actionDrag} took.
   */
  public void actionDrop(final Component component, ComponentPoint location) {
    final Point target = location.getPoint();
    run(new Runnable() {
        public void run() {
          SwingUtilities.convertPointToScreen(target, component);
        }
      });
    Robot robot = getRobot();
    Point start = MouseInfo.getPointerInfo().getLocation();
    int steps = 20;
    for (int i = 1; i <= steps; i++) {
      robot.mouseMove(start.x + (target.x - start.x) * i / steps, start.y + (target.y - start.y) * i / steps);
      sleep(15);
    }
    // A last move for the target to be told where the drop happens
    robot.mouseMove(target.x - 1, target.y);
    robot.mouseMove(target.x, target.y);
    sleep(100);
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    robot.waitForIdle();
    sleep(200);
    waitForIdle();
    ignorePointer(true);
  }

  // ---- Keyboard

  /**
   * Gives the focus to <code>component</code>.
   */
  public void actionFocus(final Component component) {
    waitForIdle();
    activateWindow(component);
    run(new Runnable() {
        public void run() {
          component.requestFocus();
        }
      });
    long end = System.currentTimeMillis() + FOCUS_TIMEOUT;
    while (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != component
           && System.currentTimeMillis() < end) {
      sleep(10);
    }
    waitForIdle();
    // The keys go to this component even if the window manager didn't activate its window
    this.lastComponent = component;
  }

  public void focus(Component component) {
    actionFocus(component);
  }

  /**
   * Presses and releases a key.
   * @param keyCode a constant of {@link KeyEvent}
   */
  public void actionKeyStroke(int keyCode) {
    actionKeyStroke(keyCode, 0);
  }

  /**
   * Presses and releases a key in <code>component</code>.
   */
  public void actionKeyStroke(Component component, int keyCode) {
    actionFocus(component);
    actionKeyStroke(keyCode, 0);
  }

  /**
   * Presses and releases a key with other ones pressed.
   * @param modifiers the keys kept pressed, given with the constants of {@link InputEvent}
   */
  public void actionKeyStroke(int keyCode, int modifiers) {
    int strokeModifiers = this.keyModifiers | getModifiers(modifiers);
    char keyChar = getKeyChar(keyCode, strokeModifiers);
    postKeyEvent(KeyEvent.KEY_PRESSED, strokeModifiers, keyCode, keyChar);
    if (keyChar != KeyEvent.CHAR_UNDEFINED) {
      postKeyEvent(KeyEvent.KEY_TYPED, strokeModifiers, KeyEvent.VK_UNDEFINED, keyChar);
    }
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    postKeyEvent(KeyEvent.KEY_RELEASED, strokeModifiers, keyCode, keyChar);
    waitForIdle();
    if (keyCode == KeyEvent.VK_TAB && focusOwner != null) {
      // The focus goes to another component once the window system agreed
      long end = System.currentTimeMillis() + 500;
      while (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == focusOwner
             && System.currentTimeMillis() < end) {
        sleep(10);
      }
      waitForIdle();
    }
  }

  /**
   * Presses a key and keeps it pressed: a key as Shift is then cited in the events that follow.
   */
  public void actionKeyPress(int keyCode) {
    this.keyModifiers |= getKeyModifier(keyCode);
    postKeyEvent(KeyEvent.KEY_PRESSED, this.keyModifiers, keyCode, getKeyChar(keyCode, this.keyModifiers));
    waitForIdle();
  }

  public void actionKeyRelease(int keyCode) {
    this.keyModifiers &= ~getKeyModifier(keyCode);
    postKeyEvent(KeyEvent.KEY_RELEASED, this.keyModifiers, keyCode, getKeyChar(keyCode, this.keyModifiers));
    waitForIdle();
  }

  /**
   * Types a text in the component which has the focus.
   */
  public void actionKeyString(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
      int modifiers = this.keyModifiers | (Character.isUpperCase(c) ? InputEvent.SHIFT_DOWN_MASK : 0);
      postKeyEvent(KeyEvent.KEY_PRESSED, modifiers, keyCode, c);
      postKeyEvent(KeyEvent.KEY_TYPED, modifiers, KeyEvent.VK_UNDEFINED, c);
      postKeyEvent(KeyEvent.KEY_RELEASED, modifiers, keyCode, c);
      waitForIdle();
    }
  }

  /**
   * Types a text in <code>component</code>.
   */
  public void actionKeyString(Component component, String text) {
    actionFocus(component);
    actionKeyString(text);
  }

  /**
   * Sends an event of the keyboard to the component that has the focus when the events
   * posted before it are handled, or to the last component used if no one has it.
   */
  private void postKeyEvent(final int id, final int modifiers, final int keyCode, final char keyChar) {
    EventQueue.invokeLater(new Runnable() {
        public void run() {
          Component target = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
          if (target == null) {
            target = lastComponent;
            if (target != null) {
              // The component of its window which would have the focus if this window was active
              Window window = target instanceof Window
                  ? (Window)target
                  : SwingUtilities.getWindowAncestor(target);
              if (window != null && window.getMostRecentFocusOwner() != null) {
                target = window.getMostRecentFocusOwner();
              }
            }
          }
          if (target != null) {
            // Dispatched and not posted, for the event to go to this component in any case
            target.dispatchEvent(new KeyEvent(target, id, System.currentTimeMillis(), modifiers, keyCode, keyChar));
          }
        }
      });
  }

  private static char getKeyChar(int keyCode, int modifiers) {
    switch (keyCode) {
      case KeyEvent.VK_ENTER :
        return '\n';
      case KeyEvent.VK_TAB :
        return '\t';
      case KeyEvent.VK_ESCAPE :
        return 27;
      case KeyEvent.VK_DELETE :
        return 127;
      case KeyEvent.VK_BACK_SPACE :
        return '\b';
      case KeyEvent.VK_SPACE :
        return ' ';
      default :
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
          return (char)keyCode;
        } else if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
          return (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0
              ? (char)keyCode
              : Character.toLowerCase((char)keyCode);
        } else {
          return KeyEvent.CHAR_UNDEFINED;
        }
    }
  }

  private static int getKeyModifier(int keyCode) {
    switch (keyCode) {
      case KeyEvent.VK_SHIFT :
        return InputEvent.SHIFT_DOWN_MASK;
      case KeyEvent.VK_CONTROL :
        return InputEvent.CTRL_DOWN_MASK;
      case KeyEvent.VK_ALT :
        return InputEvent.ALT_DOWN_MASK;
      case KeyEvent.VK_META :
        return InputEvent.META_DOWN_MASK;
      default :
        return 0;
    }
  }

  /**
   * Returns the keys of <code>modifiers</code> with the constants of today.
   */
  private static int getModifiers(int modifiers) {
    int downModifiers = modifiers & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
        | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK);
    if ((modifiers & InputEvent.SHIFT_MASK) != 0) {
      downModifiers |= InputEvent.SHIFT_DOWN_MASK;
    }
    if ((modifiers & InputEvent.CTRL_MASK) != 0) {
      downModifiers |= InputEvent.CTRL_DOWN_MASK;
    }
    if ((modifiers & InputEvent.ALT_MASK) != 0) {
      downModifiers |= InputEvent.ALT_DOWN_MASK;
    }
    if ((modifiers & InputEvent.META_MASK) != 0) {
      downModifiers |= InputEvent.META_DOWN_MASK;
    }
    return downModifiers;
  }

  // ---- Components changed through their model, where the place to click depends on the look and feel

  /**
   * Shows <code>directory</code> in a file chooser.
   */
  public void actionSetDirectory(final JFileChooser fileChooser, final String directory) {
    invokeAndWait(new Runnable() {
        public void run() {
          fileChooser.setCurrentDirectory(new File(directory));
        }
      });
  }

  /**
   * Chooses the file <code>fileName</code> of the directory a file chooser shows.
   */
  public void actionSetFilename(final JFileChooser fileChooser, final String fileName) {
    invokeAndWait(new Runnable() {
        public void run() {
          fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), fileName));
        }
      });
  }

  /**
   * Validates the choice made in a file chooser, which closes its dialog.
   */
  public void actionApprove(final JFileChooser fileChooser) {
    invokeAndWait(new Runnable() {
        public void run() {
          fileChooser.approveSelection();
        }
      });
  }

  public void actionSlide(final JSlider slider, final int value) {
    invokeAndWait(new Runnable() {
        public void run() {
          slider.setValue(value);
        }
      });
  }

  /**
   * Sets the next value of a spinner, as its upper arrow does.
   */
  public void actionIncrement(final JSpinner spinner) {
    invokeAndWait(new Runnable() {
        public void run() {
          Object value = spinner.getNextValue();
          if (value != null) {
            spinner.setValue(value);
          }
        }
      });
  }

  /**
   * Sets the previous value of a spinner, as its lower arrow does.
   */
  public void actionDecrement(final JSpinner spinner) {
    invokeAndWait(new Runnable() {
        public void run() {
          Object value = spinner.getPreviousValue();
          if (value != null) {
            spinner.setValue(value);
          }
        }
      });
  }

  /**
   * Moves the divider of a split pane.
   * @param proportionalLocation the place of the divider, from 0 to 1
   */
  public void actionMoveDivider(final Component splitPane, final float proportionalLocation) {
    invokeAndWait(new Runnable() {
        public void run() {
          ((JSplitPane)splitPane).setDividerLocation((double)proportionalLocation);
        }
      });
  }

  public void actionSelectTab(final JTabbedPane tabbedPane, final int index) {
    invokeAndWait(new Runnable() {
        public void run() {
          tabbedPane.setSelectedIndex(index);
        }
      });
  }
}
