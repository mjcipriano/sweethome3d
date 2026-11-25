/*
 * Home3DNavigationTest.java
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
package com.eteks.sweethome3d.junit;

import java.util.List;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.SelectionEvent;
import com.eteks.sweethome3d.model.SelectionListener;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.MouseLookCapture;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;

/**
 * Tests the moves of the camera of the 3D view: zoom at the mouse, moves in the plane
 * of the view, and what the camera is free to do.
 */
public class Home3DNavigationTest extends TestCase {
  private Home             home;
  private HomeController3D controller;

  private String           previousNo3D;

  @Override
  protected void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    UserPreferences preferences = new DefaultUserPreferences();
    this.home = new Home();
    this.home.addWall(new Wall(0, 0, 1000, 0, 10, 250));
    this.home.addWall(new Wall(1000, 0, 1000, 800, 10, 250));
    this.home.addWall(new Wall(1000, 800, 0, 800, 10, 250));
    this.home.addWall(new Wall(0, 800, 0, 0, 10, 250));
    HomeController homeController = new HomeController(this.home, preferences, new SwingViewFactory());
    this.controller = homeController.getHomeController3D();
    this.controller.viewFromTop();
    this.controller.zoomCameraToFit();
  }

  @Override
  protected void tearDown() throws Exception {
    // The other tests of the suite have their 3D view
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  private static float [] getRight(Camera camera) {
    return new float [] {-(float)Math.cos(camera.getYaw()), -(float)Math.sin(camera.getYaw())};
  }

  /**
   * In the aerial view, what is shown follows the mouse and the zoom keeps what is under it.
   */
  public void testPanAndZoomAtMouse() {
    Camera camera = this.home.getCamera();
    float x = camera.getX();
    float y = camera.getY();
    float z = camera.getZ();
    float yaw = camera.getYaw();
    float pitch = camera.getPitch();
    float [] right = getRight(camera);
    // The mouse drags the home to the right: the camera goes to the left, at the same height
    this.controller.panCamera(100, 0, 600);
    float move = (camera.getX() - x) * right [0] + (camera.getY() - y) * right [1];
    assertTrue("Camera moved of " + move, move < -50);
    assertEquals(z, camera.getZ(), 1E-3f);
    assertEquals(yaw, camera.getYaw(), 1E-6f);
    assertEquals(pitch, camera.getPitch(), 1E-6f);
    // Back, then down: the camera goes up or forward
    this.controller.panCamera(-100, 0, 600);
    assertEquals(x, camera.getX(), 0.5f);
    assertEquals(y, camera.getY(), 0.5f);
    this.controller.panCamera(0, 100, 600);
    assertTrue(camera.getZ() > z
        || Math.hypot(camera.getX() - x, camera.getY() - y) > 50);
    this.controller.panCamera(0, -100, 600);

    // Zoom at the center of the view, then at its right
    this.controller.zoomCamera(3);
    float centerX = camera.getX();
    float centerY = camera.getY();
    this.controller.zoomCamera(-3);
    assertEquals(x, camera.getX(), 1f);
    this.controller.zoomCameraAt(3, 250, 0, 600);
    float shift = (camera.getX() - centerX) * right [0] + (camera.getY() - centerY) * right [1];
    assertTrue("Camera shifted of " + shift, shift > 20);
    // Back from the same point of the view
    this.controller.zoomCameraAt(-3, 250, 0, 600);
    assertEquals(x, camera.getX(), 2f);
    assertEquals(y, camera.getY(), 2f);
    assertEquals(z, camera.getZ(), 2f);
  }

  /**
   * The camera comes close to a part of the home, and looks from under the point it turns around.
   */
  public void testFreeCamera() {
    Camera camera = this.home.getCamera();
    // Much closer than the size of the home
    for (int i = 0; i < 60; i++) {
      this.controller.zoomCamera(1);
    }
    double distance = Math.sqrt(Math.pow(camera.getX() - 500, 2) + Math.pow(camera.getY() - 400, 2) + Math.pow(camera.getZ() - 125, 2));
    assertTrue("Distance " + distance, distance < 100);
    for (int i = 0; i < 30; i++) {
      this.controller.zoomCamera(-1);
    }
    // Not under the horizon when the camera turns around a point near the ground
    this.controller.rotateCameraPitch(-(float)Math.PI);
    assertTrue(camera.getZ() >= 9.9f);
    float lowPitch = camera.getPitch();
    // The point goes up: the camera may look at it from below
    this.controller.elevateCamera(400);
    this.controller.rotateCameraPitch(-(float)Math.PI);
    assertTrue(camera.getPitch() + " < " + lowPitch, camera.getPitch() < lowPitch);
    assertTrue(camera.getPitch() < 0);
    assertTrue(camera.getZ() >= 9.9f);
    this.controller.rotateCameraPitch((float)Math.PI);
    assertEquals(Math.PI / 2, camera.getPitch(), 1E-5);
  }

  /**
   * The visitor selected once isn't selected again at each of its steps.
   */
  public void testVisitorSteps() {
    this.controller.viewFromObserver();
    final int [] selections = {0};
    this.home.addSelectionListener(new SelectionListener() {
        public void selectionChanged(SelectionEvent ev) {
          selections [0]++;
        }
      });
    Camera camera = this.home.getCamera();
    float x = camera.getX();
    float y = camera.getY();
    for (int i = 0; i < 20; i++) {
      this.controller.moveCamera(5);
      this.controller.rotateCameraYaw(0.01f);
    }
    assertTrue(Math.hypot(camera.getX() - x, camera.getY() - y) > 50);
    assertTrue(selections [0] + " selections", selections [0] <= 1);
    List<?> selectedItems = this.home.getSelectedItems();
    assertTrue(selectedItems.isEmpty() || selectedItems.get(0) == camera);
    // Dragging what is shown moves the visitor sideways and up
    float z = camera.getZ();
    this.controller.panCamera(40, 40, 600);
    assertTrue(camera.getZ() > z);
  }
  /**
   * A pointer moved by the test as the user and the application would.
   */
  private static class TestPointer implements MouseLookCapture.Pointer {
    private final java.awt.Point location = new java.awt.Point();
    private boolean movable = true;
    private boolean available = true;
    private int     moves;

    public java.awt.Point getLocation() {
      return new java.awt.Point(this.location);
    }

    public void moveTo(int x, int y) {
      this.moves++;
      if (this.movable) {
        this.location.setLocation(x, y);
      }
    }

    public boolean isAvailableTo(java.awt.Component view) {
      return this.available;
    }
  }

  /**
   * The pointer captured for the mouse look reports moves without end while it never
   * leaves the view, and is given back when the view can't keep it.
   */
  public void testMouseLookCapturesPointer() throws Exception {
    final javax.swing.JPanel view = new javax.swing.JPanel();
    final javax.swing.JFrame frame = new javax.swing.JFrame("Mouse look test");
    final TestPointer pointer = new TestPointer();
    final int [] moved = new int [2];
    final int [] stops = {0};
    final Throwable [] failure = {null};
    try {
      java.awt.EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            frame.add(view);
            frame.setSize(600, 400);
            frame.setVisible(true);
          }
        });
      for (int i = 0; i < 50 && !view.isShowing(); i++) {
        Thread.sleep(100);
      }
      assertTrue(view.isShowing());
      java.awt.EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            try {
              MouseLookCapture capture = new MouseLookCapture(view, new MouseLookCapture.Listener() {
                  public void pointerMoved(int dx, int dy) {
                    moved [0] += dx;
                    moved [1] += dy;
                  }

                  public void captureStopped() {
                    stops [0]++;
                  }
                });
              capture.setPointer(pointer);
              java.awt.Rectangle bounds = new java.awt.Rectangle(view.getLocationOnScreen(), view.getSize());
              java.awt.Point center = new java.awt.Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
              java.awt.Cursor cursor = view.getCursor();
              // The pointer is at a corner of the screen when the capture starts
              pointer.location.setLocation(0, 0);
              capture.update();
              assertEquals("Nothing before the start", 0, pointer.moves);
              capture.start();
              assertTrue(capture.isStarted());
              assertEquals("Brought to the center of the view", center, pointer.location);
              assertNotSame("Hidden", cursor, view.getCursor());
              assertEquals("Coming to the center isn't a move of the user", 0, moved [0]);

              // The mouse goes to the right far beyond the width of the view and of the screen
              int steps = 300;
              int stepLength = 45;
              for (int i = 0; i < steps; i++) {
                pointer.location.translate(stepLength, 0);
                assertTrue("The pointer stays in the view", bounds.contains(pointer.location));
                capture.update();
                assertEquals("Back to the center after each move", center, pointer.location);
              }
              assertEquals(steps * stepLength, moved [0]);
              assertEquals(0, moved [1]);
              // Then up, and a pointer that didn't move reports nothing
              pointer.location.translate(0, -60);
              capture.update();
              capture.update();
              assertEquals(-60, moved [1]);
              assertEquals(steps * stepLength, moved [0]);

              // A system that rounds the location where it puts the pointer
              pointer.location.setLocation(center.x + 1, center.y);
              moved [0] = 0;
              capture.update();
              assertEquals(1, moved [0]);
              capture.update();
              assertEquals("Counted from where the pointer really is", 1, moved [0]);

              // A system that doesn't let the pointer be moved: its moves are still reported,
              // once each, and the application stops trying after a while
              pointer.movable = false;
              pointer.moves = 0;
              moved [0] = 0;
              for (int i = 0; i < 40; i++) {
                pointer.location.translate(5, 0);
                capture.update();
              }
              assertEquals(40 * 5, moved [0]);
              assertTrue(pointer.moves + " tries", pointer.moves <= 20);
              pointer.movable = true;

              // The pointer is given back when asked
              assertEquals(0, stops [0]);
              capture.stop();
              assertFalse(capture.isStarted());
              assertEquals(1, stops [0]);
              assertSame(cursor, view.getCursor());
              moved [0] = 0;
              pointer.location.translate(50, 0);
              capture.update();
              assertEquals(0, moved [0]);

              // And when the user works in an other window
              capture.start();
              assertTrue(capture.isStarted());
              pointer.available = false;
              pointer.location.translate(50, 0);
              capture.update();
              assertFalse(capture.isStarted());
              assertEquals(2, stops [0]);
              assertEquals(0, moved [0]);
              assertSame(cursor, view.getCursor());
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
    } finally {
      java.awt.EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            frame.dispose();
          }
        });
    }
  }
}
