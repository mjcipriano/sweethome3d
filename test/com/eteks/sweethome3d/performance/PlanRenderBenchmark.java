/*
 * PlanRenderBenchmark.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.performance;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.swing.PlanComponent;

/**
 * Measures repeated off-screen painting of a complex 2D plan.
 */
public class PlanRenderBenchmark {
  private static final int WIDTH = 1920;
  private static final int HEIGHT = 1080;

  public static void main(String [] args) throws Exception {
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: PlanRenderBenchmark <home.sh3d> [iterations]");
      System.exit(2);
    }

    final File homeFile = new File(args[0]).getCanonicalFile();
    final int iterations = args.length == 2 ? Integer.parseInt(args[1]) : 10;
    if (!homeFile.isFile()) {
      throw new IllegalArgumentException("Home file not found: " + homeFile);
    }
    if (iterations < 1) {
      throw new IllegalArgumentException("Iterations must be positive");
    }

    final Home home = new HomeFileRecorder(
        0, false, null, false, true).readHome(homeFile.getPath());
    System.out.println("file=" + homeFile);
    System.out.println("viewport=" + WIDTH + "x" + HEIGHT);
    System.out.println("iterations=" + iterations);
    System.out.println("furniture=" + home.getFurniture().size()
        + " walls=" + home.getWalls().size()
        + " rooms=" + home.getRooms().size()
        + " levels=" + home.getLevels().size());

    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        PlanComponent plan = new PlanComponent(
            home, new DefaultUserPreferences(), null);
        plan.setSize(WIDTH, HEIGHT);
        BufferedImage image = new BufferedImage(
            WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int i = 1; i <= iterations; i++) {
          Graphics2D graphics = image.createGraphics();
          long start = System.nanoTime();
          plan.paint(graphics);
          long elapsedNanos = System.nanoTime() - start;
          graphics.dispose();
          System.out.println("iteration=" + i
              + " elapsed_ms=" + Math.round(elapsedNanos / 1000000d));
        }
      }
    });
    System.exit(0);
  }
}
