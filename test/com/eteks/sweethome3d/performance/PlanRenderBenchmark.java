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
import java.util.Arrays;

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
  private static final int WARMUP_SECONDS = 5;

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
    System.out.println("warmup_seconds=" + WARMUP_SECONDS);
    System.out.println("iterations=" + iterations);
    System.out.println("furniture=" + home.getFurniture().size()
        + " walls=" + home.getWalls().size()
        + " rooms=" + home.getRooms().size()
        + " levels=" + home.getLevels().size());

    final PlanComponent [] planHolder = new PlanComponent [1];
    final BufferedImage [] imageHolder = new BufferedImage [1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        planHolder [0] = new PlanComponent(
            home, new DefaultUserPreferences(), null);
        planHolder [0].setSize(WIDTH, HEIGHT);
        imageHolder [0] = new BufferedImage(
            WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
      }
    });

    long warmupEnd = System.nanoTime() + WARMUP_SECONDS * 1000000000L;
    int warmupIterations = 0;
    while (System.nanoTime() < warmupEnd) {
      paint(planHolder [0], imageHolder [0]);
      warmupIterations++;
      Thread.sleep(25);
    }
    System.out.println("warmup_iterations=" + warmupIterations);

    long [] elapsedMillis = new long [iterations];
    for (int i = 0; i < iterations; i++) {
      elapsedMillis [i] = Math.round(
          paint(planHolder [0], imageHolder [0]) / 1000000d);
      System.out.println("iteration=" + (i + 1)
          + " elapsed_ms=" + elapsedMillis [i]);
    }
    long [] sortedElapsedMillis = elapsedMillis.clone();
    Arrays.sort(sortedElapsedMillis);
    System.out.println("median_ms=" + percentile(sortedElapsedMillis, 50));
    System.out.println("p95_ms=" + percentile(sortedElapsedMillis, 95));
    System.exit(0);
  }

  private static long paint(final PlanComponent plan,
                            final BufferedImage image) throws Exception {
    final long [] elapsedNanos = new long [1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        Graphics2D graphics = image.createGraphics();
        long start = System.nanoTime();
        plan.paint(graphics);
        elapsedNanos [0] = System.nanoTime() - start;
        graphics.dispose();
      }
    });
    return elapsedNanos [0];
  }

  private static long percentile(long [] sortedValues, int percentile) {
    int index = (int)Math.ceil(percentile / 100d * sortedValues.length) - 1;
    return sortedValues [Math.max(0, index)];
  }
}
