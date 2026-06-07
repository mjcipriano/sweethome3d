/*
 * Home3DRenderBenchmark.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.performance;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.j3d.Component3DManager;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.swing.HomeComponent3D;

/**
 * Measures synchronous Java 3D scene creation and off-screen rendering.
 */
public class Home3DRenderBenchmark {
  private static final int WIDTH = 1920;
  private static final int HEIGHT = 1080;

  public static void main(String [] args) throws Exception {
    if (args.length < 1 || args.length > 3) {
      System.err.println(
          "Usage: Home3DRenderBenchmark <home.sh3d> [scene|frame] [iterations]");
      System.exit(2);
    }

    File homeFile = new File(args[0]).getCanonicalFile();
    String mode = args.length >= 2 ? args[1] : "scene";
    int iterations = args.length == 3 ? Integer.parseInt(args[2]) : 5;
    if (!homeFile.isFile()) {
      throw new IllegalArgumentException("Home file not found: " + homeFile);
    }
    if (iterations < 1) {
      throw new IllegalArgumentException("Iterations must be positive");
    }
    if (!"scene".equals(mode) && !"frame".equals(mode)) {
      throw new IllegalArgumentException("Mode must be scene or frame");
    }

    Home home = new HomeFileRecorder(
        0, false, null, false, true).readHome(homeFile.getPath());
    Set<Content> uniqueModels = Collections.newSetFromMap(
        new IdentityHashMap<Content, Boolean>());
    int modelReferences = countModels(home.getFurniture(), uniqueModels);
    System.out.println("file=" + homeFile);
    System.out.println("mode=" + mode);
    System.out.println("viewport=" + WIDTH + "x" + HEIGHT);
    System.out.println("iterations=" + iterations);
    System.out.println("furniture=" + home.getFurniture().size()
        + " walls=" + home.getWalls().size()
        + " rooms=" + home.getRooms().size()
        + " levels=" + home.getLevels().size());
    System.out.println("model_references=" + modelReferences
        + " unique_models=" + uniqueModels.size());

    long supportStart = System.nanoTime();
    boolean offscreenSupported =
        Component3DManager.getInstance().isOffScreenImageSupported();
    System.out.println("offscreen_supported=" + offscreenSupported
        + " support_check_ms=" + elapsedMillis(supportStart));
    if (!offscreenSupported) {
      throw new IllegalStateException("Off-screen Java 3D rendering is unavailable");
    }

    HomeComponent3D component = new HomeComponent3D(
        home, new DefaultUserPreferences(), false);
    long sceneStart = System.nanoTime();
    component.startOffscreenImagesCreation();
    System.out.println("scene_creation_ms=" + elapsedMillis(sceneStart));

    if ("scene".equals(mode)) {
      component.endOffscreenImagesCreation();
      System.exit(0);
    }

    long [] frameMillis = new long [iterations];
    try {
      for (int i = 0; i < iterations; i++) {
        long frameStart = System.nanoTime();
        BufferedImage image = component.getOffScreenImage(WIDTH, HEIGHT);
        frameMillis [i] = elapsedMillis(frameStart);
        System.out.println("iteration=" + (i + 1)
            + " elapsed_ms=" + frameMillis [i]
            + " image=" + image.getWidth() + "x" + image.getHeight());
      }
    } finally {
      component.endOffscreenImagesCreation();
    }

    Arrays.sort(frameMillis);
    System.out.println("median_frame_ms=" + percentile(frameMillis, 50));
    System.out.println("p95_frame_ms=" + percentile(frameMillis, 95));
    System.exit(0);
  }

  private static int countModels(
      Iterable<HomePieceOfFurniture> furniture, Set<Content> uniqueModels) {
    int count = 0;
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        count += countModels(
            ((HomeFurnitureGroup)piece).getFurniture(), uniqueModels);
      } else if (piece.getModel() != null) {
        count++;
        uniqueModels.add(piece.getModel());
      }
    }
    return count;
  }

  private static long elapsedMillis(long startNanos) {
    return Math.round((System.nanoTime() - startNanos) / 1000000d);
  }

  private static long percentile(long [] sortedValues, int percentile) {
    int index = (int)Math.ceil(percentile / 100d * sortedValues.length) - 1;
    return sortedValues [Math.max(0, index)];
  }
}
