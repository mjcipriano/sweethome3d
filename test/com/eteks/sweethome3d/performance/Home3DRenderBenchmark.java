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

import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.j3d.Component3DManager;
import com.eteks.sweethome3d.model.Camera;
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
    if (!"scene".equals(mode) && !"frame".equals(mode) && !"update".equals(mode)) {
      throw new IllegalArgumentException("Mode must be scene, frame or update");
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

    if ("update".equals(mode)) {
      try {
        measureSceneUpdates(home, iterations);
      } finally {
        component.endOffscreenImagesCreation();
      }
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

  /**
   * Measures how long the live Java 3D scene graph takes to react to repeated
   * model changes after the scene is built: moving a piece, rotating a piece,
   * and moving the camera. {@code HomeComponent3D} applies furniture updates
   * through {@code EventQueue.invokeLater}, so each measurement mutates the home
   * on the event dispatch thread and then posts an empty barrier; FIFO ordering
   * guarantees the deferred update runs before the barrier returns, so the
   * elapsed time covers the whole update cycle.
   */
  private static void measureSceneUpdates(Home home, int iterations) throws Exception {
    final HomePieceOfFurniture piece = firstMovablePiece(home);
    if (piece == null) {
      throw new IllegalStateException("No movable furniture found to update");
    }
    final Camera camera = home.getCamera();
    final float baseX = piece.getX();
    final float baseAngle = piece.getAngle();
    final float cameraBaseX = camera.getX();

    // Warm up the deferred-update path and JIT before recording.
    for (int i = 0; i < 5; i++) {
      timeSceneUpdate(new Runnable() {
        public void run() { piece.setX(piece.getX() + 5f); }
      });
    }
    runOnEdt(new Runnable() {
      public void run() { piece.setX(baseX); }
    });

    long [] moveMillis = new long [iterations];
    long [] rotateMillis = new long [iterations];
    long [] cameraMillis = new long [iterations];
    for (int i = 0; i < iterations; i++) {
      final float sign = (i % 2 == 0) ? 1f : -1f;
      moveMillis [i] = timeSceneUpdate(new Runnable() {
        public void run() { piece.setX(baseX + sign * 25f); }
      });
      rotateMillis [i] = timeSceneUpdate(new Runnable() {
        public void run() { piece.setAngle(baseAngle + sign * 0.2f); }
      });
      cameraMillis [i] = timeSceneUpdate(new Runnable() {
        public void run() { camera.setX(cameraBaseX + sign * 25f); }
      });
    }
    // Restore mutated state.
    runOnEdt(new Runnable() {
      public void run() {
        piece.setX(baseX);
        piece.setAngle(baseAngle);
        camera.setX(cameraBaseX);
      }
    });

    printUpdateSummary("move_piece", moveMillis);
    printUpdateSummary("rotate_piece", rotateMillis);
    printUpdateSummary("move_camera", cameraMillis);
  }

  /**
   * Mutates the home on the event dispatch thread and returns the elapsed
   * milliseconds once the deferred scene-graph update has also run.
   */
  private static long timeSceneUpdate(Runnable mutation) throws Exception {
    long start = System.nanoTime();
    SwingUtilities.invokeAndWait(mutation);
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        // Empty barrier: ensures the invokeLater scene update has executed.
      }
    });
    return elapsedMillis(start);
  }

  private static void runOnEdt(Runnable runnable) throws Exception {
    SwingUtilities.invokeAndWait(runnable);
  }

  private static HomePieceOfFurniture firstMovablePiece(Home home) {
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (!(piece instanceof HomeFurnitureGroup) && piece.isVisible()) {
        return piece;
      }
    }
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (!(piece instanceof HomeFurnitureGroup)) {
        return piece;
      }
    }
    return null;
  }

  private static void printUpdateSummary(String name, long [] values) {
    long [] sorted = values.clone();
    Arrays.sort(sorted);
    System.out.println(name + "_median_ms=" + percentile(sorted, 50)
        + " " + name + "_p95_ms=" + percentile(sorted, 95));
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
