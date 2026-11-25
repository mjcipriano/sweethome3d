/*
 * MouseLookCapture.java 25 nov. 2026
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
package com.eteks.sweethome3d.swing;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Captures the pointer of the mouse in a view, as the programs where the mouse turns
 * the head of a visitor do: the pointer is hidden, each of its moves is reported, then
 * it's brought back to the center of the view. The mouse can thus go on in a direction
 * without end, and its pointer never leaves the view.
 * <br>The pointer is read at regular intervals rather than at each mouse event: a pointer
 * that left the view wouldn't send it any event, and couldn't be brought back.
 * @author Sweet Home 3D contributors
 */
public class MouseLookCapture {
  /**
   * The pointer of the mouse.
   */
  public static interface Pointer {
    /**
     * Returns the location of the pointer on screen, or <code>null</code> if it's unknown.
     */
    Point getLocation();

    /**
     * Moves the pointer to the given location on screen, if the system allows it.
     */
    void moveTo(int x, int y);

    /**
     * Returns <code>false</code> if the pointer mustn't be kept in the given view anymore.
     */
    boolean isAvailableTo(Component view);
  }

  /**
   * The object told of the moves of the captured pointer.
   */
  public static interface Listener {
    /**
     * Called when the pointer moved by the given count of pixels.
     */
    void pointerMoved(int dx, int dy);

    /**
     * Called when the capture ended, whether it was {@linkplain MouseLookCapture#stop() stopped}
     * or the view couldn't keep the pointer.
     */
    void captureStopped();
  }

  // The tries to move a pointer that a system keeps where it is
  private static final int MAX_FAILED_MOVES = 20;

  private final Component view;
  private final Listener  listener;
  private final Timer     timer;
  private Pointer         pointer;
  private boolean         started;
  private Point           lastLocation;
  private Cursor          previousCursor;
  private int             failedMoves;

  /**
   * Creates the capture of the pointer in the given <code>view</code>.
   */
  public MouseLookCapture(Component view, Listener listener) {
    this.view = view;
    this.listener = listener;
    this.timer = new Timer(8, new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          update();
        }
      });
  }

  /**
   * Sets the pointer that is captured, the one of the system by default.
   */
  public void setPointer(Pointer pointer) {
    this.pointer = pointer;
  }

  /**
   * Returns <code>true</code> while the pointer is captured.
   */
  public boolean isStarted() {
    return this.started;
  }

  /**
   * Hides the pointer, brings it at the center of the view and starts reporting its moves.
   */
  public void start() {
    if (!this.started) {
      if (this.pointer == null) {
        this.pointer = new SystemPointer();
      }
      this.started = true;
      this.failedMoves = 0;
      this.previousCursor = this.view.isCursorSet() ? this.view.getCursor() : null;
      try {
        this.view.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "hidden"));
      } catch (RuntimeException ex) {
        // The pointer stays visible
      }
      // The moves are counted from the center of the view
      Point center = getCenter();
      if (center != null) {
        this.pointer.moveTo(center.x, center.y);
      }
      this.lastLocation = this.pointer.getLocation();
      this.timer.start();
    }
  }

  /**
   * Gives the pointer back.
   */
  public void stop() {
    if (this.started) {
      this.started = false;
      this.timer.stop();
      this.lastLocation = null;
      this.view.setCursor(this.previousCursor);
      this.listener.captureStopped();
    }
  }

  /**
   * Returns the center of the view on screen, or <code>null</code> if it isn't displayed.
   */
  private Point getCenter() {
    if (!this.view.isShowing()) {
      return null;
    }
    try {
      Point origin = this.view.getLocationOnScreen();
      return new Point(origin.x + this.view.getWidth() / 2, origin.y + this.view.getHeight() / 2);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /**
   * Reports the move of the pointer since the last call, then brings the pointer
   * back to the center of the view. Called at regular intervals during the capture.
   */
  public void update() {
    if (!this.started) {
      return;
    }
    Point center = getCenter();
    if (center == null || !this.pointer.isAvailableTo(this.view)) {
      // The pointer is given back as soon as the user works elsewhere
      stop();
      return;
    }
    Point location = this.pointer.getLocation();
    if (location == null) {
      return;
    }
    if (this.lastLocation != null
        && (location.x != this.lastLocation.x || location.y != this.lastLocation.y)) {
      this.listener.pointerMoved(location.x - this.lastLocation.x, location.y - this.lastLocation.y);
      if (!this.started) {
        return;
      }
    }
    this.lastLocation = location;
    if (this.failedMoves < MAX_FAILED_MOVES && !location.equals(center)) {
      this.pointer.moveTo(center.x, center.y);
      // Counted from where the pointer really is: a system may round its location
      Point moved = this.pointer.getLocation();
      if (moved != null) {
        this.lastLocation = moved;
        if (moved.equals(location) && location.distance(center) > 4) {
          // A system that doesn't let programs move the pointer: after some tries,
          // the mouse is followed as far as the pointer can go
          this.failedMoves++;
        } else {
          this.failedMoves = 0;
        }
      }
    }
  }

  /**
   * The pointer of the system, moved by a robot.
   */
  private static class SystemPointer implements Pointer {
    private Robot robot;

    public SystemPointer() {
      try {
        this.robot = new Robot();
      } catch (Exception ex) {
        // The pointer can't be moved
      }
    }

    public Point getLocation() {
      try {
        PointerInfo info = MouseInfo.getPointerInfo();
        return info != null ? info.getLocation() : null;
      } catch (RuntimeException ex) {
        return null;
      }
    }

    public void moveTo(int x, int y) {
      if (this.robot != null) {
        try {
          this.robot.mouseMove(x, y);
        } catch (RuntimeException ex) {
          this.robot = null;
        }
      }
    }

    public boolean isAvailableTo(Component view) {
      // Not while the user works in an other window
      Window window = SwingUtilities.getWindowAncestor(view);
      return window == null || window.isActive();
    }
  }
}
