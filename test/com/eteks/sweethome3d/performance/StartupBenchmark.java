/*
 * StartupBenchmark.java
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
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.PlanComponent;

/**
 * Measures the cold-start phases a user waits through before a home is usable:
 * user-preferences initialization, home load, plan-component creation, and the
 * first 2D paint. The first iteration is the genuine cold pass because it pays
 * one-time class-loading and JIT warm-up costs; later iterations show the warm
 * cost of the same phases. This benchmark does not start the full Swing
 * application or Java 3D, so it runs without a display when
 * {@code -Djava.awt.headless=true} and {@code -Dcom.eteks.sweethome3d.no3D=true}
 * are set (the way {@code scripts/profile-startup.sh} runs it). The first usable
 * 3D frame is measured separately by {@code benchmark-home-3d}.
 */
public class StartupBenchmark {
  private static final int WIDTH = 1920;
  private static final int HEIGHT = 1080;

  public static void main(String [] args) throws Exception {
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: StartupBenchmark <home.sh3d> [iterations]");
      System.exit(2);
    }

    final File homeFile = new File(args[0]).getCanonicalFile();
    final int iterations = args.length == 2 ? Integer.parseInt(args[1]) : 5;
    if (!homeFile.isFile()) {
      throw new IllegalArgumentException("Home file not found: " + homeFile);
    }
    if (iterations < 1) {
      throw new IllegalArgumentException("Iterations must be positive");
    }

    System.out.println("file=" + homeFile);
    System.out.println("bytes=" + homeFile.length());
    System.out.println("viewport=" + WIDTH + "x" + HEIGHT);
    System.out.println("iterations=" + iterations);
    System.out.println("note=iteration_1_is_cold");

    long [] prefsMillis = new long [iterations];
    long [] loadMillis = new long [iterations];
    long [] createMillis = new long [iterations];
    long [] paintMillis = new long [iterations];
    long [] totalMillis = new long [iterations];

    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();

      long prefsStart = System.nanoTime();
      final UserPreferences preferences = new DefaultUserPreferences();
      prefsMillis [i] = nanosToMillis(System.nanoTime() - prefsStart);

      long loadStart = System.nanoTime();
      final Home home = new HomeFileRecorder(
          0, false, null, false, true).readHome(homeFile.getPath());
      loadMillis [i] = nanosToMillis(System.nanoTime() - loadStart);

      final PlanComponent [] planHolder = new PlanComponent [1];
      final BufferedImage [] imageHolder = new BufferedImage [1];
      long createStart = System.nanoTime();
      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          planHolder [0] = new PlanComponent(home, preferences, null);
          planHolder [0].setSize(WIDTH, HEIGHT);
          imageHolder [0] = new BufferedImage(
              WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        }
      });
      createMillis [i] = nanosToMillis(System.nanoTime() - createStart);

      paintMillis [i] = nanosToMillis(firstPaint(planHolder [0], imageHolder [0]));

      totalMillis [i] = nanosToMillis(System.nanoTime() - start);

      System.out.println("iteration=" + (i + 1)
          + " prefs_ms=" + prefsMillis [i]
          + " load_ms=" + loadMillis [i]
          + " plan_create_ms=" + createMillis [i]
          + " first_paint_ms=" + paintMillis [i]
          + " total_ms=" + totalMillis [i]);
    }

    System.out.println("cold_total_ms=" + totalMillis [0]);
    printPhaseSummary("prefs", prefsMillis);
    printPhaseSummary("load", loadMillis);
    printPhaseSummary("plan_create", createMillis);
    printPhaseSummary("first_paint", paintMillis);
    printPhaseSummary("total", totalMillis);
    System.exit(0);
  }

  private static long firstPaint(final PlanComponent plan,
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

  private static void printPhaseSummary(String phase, long [] values) {
    long [] sorted = values.clone();
    Arrays.sort(sorted);
    System.out.println(phase + "_median_ms=" + percentile(sorted, 50)
        + " " + phase + "_p95_ms=" + percentile(sorted, 95));
  }

  private static long percentile(long [] sortedValues, int percentile) {
    int index = (int)Math.ceil(percentile / 100d * sortedValues.length) - 1;
    return sortedValues [Math.max(0, index)];
  }

  private static long nanosToMillis(long nanos) {
    return Math.round(nanos / 1000000d);
  }
}
