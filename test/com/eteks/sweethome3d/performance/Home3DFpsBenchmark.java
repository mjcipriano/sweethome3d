/*
 * Home3DFpsBenchmark.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.performance;

import java.awt.BorderLayout;
import java.io.File;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.HomeComponent3D;
import com.eteks.sweethome3d.tools.GraphicsEnvironmentConfiguration;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;

/**
 * Measures the interactive frame rate of the on-screen 3D view while the camera
 * rotates. Unlike the off-screen frame benchmark (which crashes on some Mesa
 * stacks), this drives the real on-screen pipeline, so it works wherever the
 * application's 3D view does. Frame rate is read from the rendering statistics
 * captured by {@link HomeComponent3D}. Requires a display.
 */
public class Home3DFpsBenchmark {
  private static final int WIDTH = 1280;
  private static final int HEIGHT = 800;

  public static void main(String [] args) throws Exception {
    GraphicsEnvironmentConfiguration.applyDefaults();
    if (args.length < 1 || args.length > 3) {
      System.err.println("Usage: Home3DFpsBenchmark <home.sh3d|--smoke> [seconds] [warmup-seconds]");
      System.exit(2);
    }
    final boolean smoke = "--smoke".equals(args[0]);
    final File homeFile = smoke ? null : new File(args[0]).getCanonicalFile();
    final int seconds = args.length >= 2 ? Integer.parseInt(args[1]) : 15;
    final int warmupSeconds = args.length == 3 ? Integer.parseInt(args[2]) : 0;

    final Home home = smoke
        ? createSmokeHome()
        : new HomeFileRecorder(
            0, false, null, false, true).readHome(homeFile.getPath());
    final UserPreferences preferences = new DefaultUserPreferences();
    System.out.println(smoke ? "scene=synthetic-smoke" : "file=" + homeFile);
    System.out.println("furniture=" + home.getFurniture().size()
        + " walls=" + home.getWalls().size() + " levels=" + home.getLevels().size());
    System.out.println("renderingQuality="
        + System.getProperty("com.eteks.sweethome3d.j3d.renderingQuality", "(default)")
        + " compileScene="
        + System.getProperty("com.eteks.sweethome3d.j3d.compileScene", "(default)"));
    System.out.println("warmup_seconds=" + warmupSeconds + " measure_seconds=" + seconds);

    final JFrame [] frameHolder = new JFrame [1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        HomeComponent3D component = new HomeComponent3D(home, preferences, (HomeController3D)null);
        JFrame frame = new JFrame("Sweet Home 3D - FPS benchmark");
        frame.getContentPane().add(component, BorderLayout.CENTER);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frameHolder [0] = frame;
      }
    });

    final Camera camera = home.getCamera();
    final float [] yaw = {camera.getYaw()};

    // Spin the camera until the first frame is rendered (statistics available)
    long firstFrameDeadline = System.nanoTime() + 30000000000L;
    while (HomeComponent3D.getRenderingStatistics() == null
           && System.nanoTime() < firstFrameDeadline) {
      rotate(camera, yaw);
      Thread.sleep(50);
    }
    HomeComponent3D.RenderingStatistics statistics = HomeComponent3D.getRenderingStatistics();
    if (statistics == null) {
      System.out.println("ERROR: the 3D view never rendered a frame");
      System.exit(1);
    }
    System.out.println("gpu=" + statistics.getOpenGLRenderer());

    long warmupEnd = System.nanoTime() + warmupSeconds * 1000000000L;
    while (System.nanoTime() < warmupEnd) {
      rotate(camera, yaw);
      Thread.sleep(16);
    }

    // Sweep the camera through full rotations at a fixed angular rate so every run
    // covers the same views, and measure the average frame rate from the swap
    // counter (deterministic, unlike sampling the rolling FPS value). Also track
    // the worst and best rolling FPS to show the view-dependent spread.
    float min = Float.MAX_VALUE;
    float max = 0;
    long startFrames = HomeComponent3D.getRenderedFrameCount();
    long startTime = System.nanoTime();
    long end = startTime + seconds * 1000000000L;
    while (System.nanoTime() < end) {
      rotate(camera, yaw);
      Thread.sleep(16);
      float fps = HomeComponent3D.getRenderingStatistics().getFramesPerSecond();
      if (fps > 0) {
        min = Math.min(min, fps);
        max = Math.max(max, fps);
      }
    }
    long renderedFrames = HomeComponent3D.getRenderedFrameCount() - startFrames;
    double elapsedSeconds = (System.nanoTime() - startTime) / 1.0E9;

    System.out.println("fps_avg=" + Math.round(renderedFrames / elapsedSeconds)
        + " fps_min=" + (min == Float.MAX_VALUE ? 0 : Math.round(min))
        + " fps_max=" + Math.round(max)
        + " frames=" + renderedFrames
        + " seconds=" + Math.round(elapsedSeconds));

    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        frameHolder [0].dispose();
      }
    });
    System.exit(0);
  }

  private static void rotate(final Camera camera, final float [] yaw) throws Exception {
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        yaw [0] += 0.03f;
        camera.setYaw(yaw [0]);
      }
    });
  }

  private static Home createSmokeHome() {
    Home home = new Home();
    float size = 500;
    float thickness = 15;
    float height = home.getWallHeight();
    home.addWall(new Wall(0, 0, size, 0, thickness, height));
    home.addWall(new Wall(size, 0, size, size, thickness, height));
    home.addWall(new Wall(size, size, 0, size, thickness, height));
    home.addWall(new Wall(0, size, 0, 0, thickness, height));
    home.addRoom(new Room(new float [][] {
        {0, 0}, {size, 0}, {size, size}, {0, size}
    }));
    return home;
  }
}
