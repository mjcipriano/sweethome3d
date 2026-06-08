/*
 * PlanInteractionBenchmark.java
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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.PlanComponent;

/**
 * Measures the cost of common 2D interactions on a complex plan, rather than
 * only full-frame repaint throughput. For each interaction it reports the time
 * to apply the change, the size of the invalidated repaint rectangle the
 * {@link PlanComponent} requests, and the time to paint just that invalidated
 * rectangle. Shrinking the invalidated area (task C1) and the per-paint cost
 * (task C2) both show up directly here.
 *
 * <p>Interactions are synthesized through the model the same way a real
 * selection, drag, or zoom drives the view: {@code setSelectedItems} for
 * selection, {@link Selectable#move} for a drag result, and
 * {@link PlanComponent#setScale} for zoom. The benchmark runs off-screen and
 * needs no display when {@code -Djava.awt.headless=true} and
 * {@code -Dcom.eteks.sweethome3d.no3D=true} are set.
 */
public class PlanInteractionBenchmark {
  private static final int WIDTH = 1920;
  private static final int HEIGHT = 1080;
  private static final int WARMUP_SECONDS = 5;
  private static final float MOVE_DELTA = 10f;

  /**
   * A plan component that records the union of the repaint rectangles requested
   * since the last {@link #resetDirtyRegion()} call. All Swing repaint requests
   * funnel through {@code repaint(long, int, int, int, int)}, so capturing it
   * here works off-screen, where a real {@code RepaintManager} would discard
   * dirty regions for a component that is not showing.
   */
  private static class CapturingPlanComponent extends PlanComponent {
    private Rectangle dirtyRegion;

    public CapturingPlanComponent(Home home, UserPreferences preferences) {
      super(home, preferences, null);
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
      if (width > 0 && height > 0) {
        Rectangle region = new Rectangle(x, y, width, height);
        this.dirtyRegion = this.dirtyRegion == null
            ? region : this.dirtyRegion.union(region);
      }
      super.repaint(tm, x, y, width, height);
    }

    void resetDirtyRegion() {
      this.dirtyRegion = null;
    }

    Rectangle takeDirtyRegion() {
      Rectangle region = this.dirtyRegion;
      this.dirtyRegion = null;
      return region;
    }
  }

  public static void main(String [] args) throws Exception {
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: PlanInteractionBenchmark <home.sh3d> [iterations]");
      System.exit(2);
    }

    final File homeFile = new File(args[0]).getCanonicalFile();
    final int iterations = args.length == 2 ? Integer.parseInt(args[1]) : 20;
    if (!homeFile.isFile()) {
      throw new IllegalArgumentException("Home file not found: " + homeFile);
    }
    if (iterations < 1) {
      throw new IllegalArgumentException("Iterations must be positive");
    }

    final Home home = new HomeFileRecorder(
        0, false, null, false, true).readHome(homeFile.getPath());

    // Work on the level that carries the most furniture so interactions affect
    // visible items.
    final Level level = busiestLevel(home);
    final List<HomePieceOfFurniture> levelFurniture = furnitureOnLevel(home, level);
    if (levelFurniture.isEmpty()) {
      throw new IllegalStateException("No furniture found to interact with");
    }

    System.out.println("file=" + homeFile);
    System.out.println("viewport=" + WIDTH + "x" + HEIGHT);
    System.out.println("warmup_seconds=" + WARMUP_SECONDS);
    System.out.println("iterations=" + iterations);
    System.out.println("level=" + (level != null ? level.getName() : "<none>")
        + " level_furniture=" + levelFurniture.size()
        + " total_furniture=" + home.getFurniture().size()
        + " walls=" + home.getWalls().size());

    final CapturingPlanComponent [] planHolder = new CapturingPlanComponent [1];
    final BufferedImage [] imageHolder = new BufferedImage [1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        if (level != null) {
          home.setSelectedLevel(level);
        }
        planHolder [0] = new CapturingPlanComponent(home, new DefaultUserPreferences());
        planHolder [0].setSize(WIDTH, HEIGHT);
        imageHolder [0] = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
      }
    });
    final CapturingPlanComponent plan = planHolder [0];
    final BufferedImage image = imageHolder [0];
    final float baseScale = getScale(plan);

    // Drag a plain piece (not a door/window or staircase, which also repaint the
    // wall/room they cut), so the move measures the common furniture-drag case.
    HomePieceOfFurniture plainPiece = levelFurniture.get(0);
    for (HomePieceOfFurniture piece : levelFurniture) {
      if (!piece.isDoorOrWindow()
          && piece.getStaircaseCutOutShape() == null
          && !(piece instanceof HomeFurnitureGroup)) {
        plainPiece = piece;
        break;
      }
    }
    final HomePieceOfFurniture firstPiece = plainPiece;
    final List<Selectable> singleSelection =
        Collections.<Selectable>singletonList(firstPiece);
    final List<Selectable> allSelection = new ArrayList<Selectable>(levelFurniture);

    // Warm up class loading, caches, and the JIT with a representative mix.
    long warmupEnd = System.nanoTime() + WARMUP_SECONDS * 1000000000L;
    while (System.nanoTime() < warmupEnd) {
      runOnEdt(new Runnable() {
        public void run() {
          home.setSelectedItems(singleSelection);
          firstPiece.move(MOVE_DELTA, MOVE_DELTA);
          firstPiece.move(-MOVE_DELTA, -MOVE_DELTA);
          home.setSelectedItems(Collections.<Selectable>emptyList());
          plan.setScale(baseScale);
        }
      });
      paintFull(plan, image);
      Thread.sleep(25);
    }

    // Each interaction resets state, applies one change, and is measured for
    // both the apply cost and the cost to repaint only the invalidated region.
    measure("select_single", iterations, plan, image, new Runnable() {
      public void run() {
        home.setSelectedItems(Collections.<Selectable>emptyList());
        plan.takeDirtyRegion();
        home.setSelectedItems(singleSelection);
      }
    });
    measure("select_all", iterations, plan, image, new Runnable() {
      public void run() {
        home.setSelectedItems(Collections.<Selectable>emptyList());
        plan.takeDirtyRegion();
        home.setSelectedItems(allSelection);
      }
    });
    measure("move_piece", iterations, plan, image, new Runnable() {
      private boolean forward = true;
      public void run() {
        home.setSelectedItems(singleSelection);
        plan.takeDirtyRegion();
        float delta = this.forward ? MOVE_DELTA : -MOVE_DELTA;
        this.forward = !this.forward;
        firstPiece.move(delta, delta);
      }
    });
    measure("rotate_piece", iterations, plan, image, new Runnable() {
      private float angle = firstPiece.getAngle();
      public void run() {
        home.setSelectedItems(singleSelection);
        plan.takeDirtyRegion();
        this.angle += (float)(Math.PI / 12);
        firstPiece.setAngle(this.angle);
      }
    });
    measure("zoom", iterations, plan, image, new Runnable() {
      private boolean zoomIn = true;
      public void run() {
        plan.setScale(baseScale);
        plan.takeDirtyRegion();
        plan.setScale(this.zoomIn ? baseScale * 1.25f : baseScale * 0.8f);
        this.zoomIn = !this.zoomIn;
      }
    });
    measure("full_repaint", iterations, plan, image, new Runnable() {
      public void run() {
        plan.setScale(baseScale);
        plan.takeDirtyRegion();
        plan.repaint();
      }
    });

    System.exit(0);
  }

  /**
   * Resets the dirty region inside {@code interaction}, runs the interaction on
   * the EDT while timing it, then paints only the invalidated rectangle while
   * timing that. Prints per-iteration lines and a median/p95 summary.
   */
  private static void measure(String name, int iterations,
                              final CapturingPlanComponent plan,
                              final BufferedImage image,
                              final Runnable interaction) throws Exception {
    long [] applyMicros = new long [iterations];
    long [] paintMicros = new long [iterations];
    long [] dirtyArea = new long [iterations];
    for (int i = 0; i < iterations; i++) {
      final long [] applyNanos = new long [1];
      final Rectangle [] dirtyHolder = new Rectangle [1];
      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          long start = System.nanoTime();
          interaction.run();
          applyNanos [0] = System.nanoTime() - start;
          dirtyHolder [0] = plan.takeDirtyRegion();
        }
      });
      Rectangle dirty = dirtyHolder [0];
      applyMicros [i] = Math.round(applyNanos [0] / 1000d);
      paintMicros [i] = Math.round(paintRegion(plan, image, dirty) / 1000d);
      dirtyArea [i] = dirty == null ? 0L
          : (long)clamp(dirty.width, WIDTH) * clamp(dirty.height, HEIGHT);
    }

    long viewportArea = (long)WIDTH * HEIGHT;
    long medianDirty = median(dirtyArea);
    System.out.println(name
        + " apply_us_median=" + median(applyMicros)
        + " apply_us_p95=" + percentile(applyMicros, 95)
        + " paint_us_median=" + median(paintMicros)
        + " paint_us_p95=" + percentile(paintMicros, 95)
        + " dirty_area_median=" + medianDirty
        + " dirty_pct_median=" + Math.round(100d * medianDirty / viewportArea));
  }

  /**
   * Paints only the invalidated rectangle (clipped) and returns the elapsed
   * nanoseconds. A null region means nothing was invalidated.
   */
  private static long paintRegion(final PlanComponent plan,
                                  final BufferedImage image,
                                  final Rectangle region) throws Exception {
    if (region == null) {
      return 0L;
    }
    final Rectangle clip = region.intersection(new Rectangle(0, 0, WIDTH, HEIGHT));
    if (clip.isEmpty()) {
      return 0L;
    }
    final long [] elapsedNanos = new long [1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        Graphics2D graphics = image.createGraphics();
        graphics.setClip(clip.x, clip.y, clip.width, clip.height);
        long start = System.nanoTime();
        plan.paint(graphics);
        elapsedNanos [0] = System.nanoTime() - start;
        graphics.dispose();
      }
    });
    return elapsedNanos [0];
  }

  private static void paintFull(final PlanComponent plan,
                                final BufferedImage image) throws Exception {
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        Graphics2D graphics = image.createGraphics();
        plan.paint(graphics);
        graphics.dispose();
      }
    });
  }

  private static void runOnEdt(Runnable runnable) throws Exception {
    SwingUtilities.invokeAndWait(runnable);
  }

  private static float getScale(final PlanComponent plan) throws Exception {
    final float [] scaleHolder = new float [1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        scaleHolder [0] = plan.getScale();
      }
    });
    return scaleHolder [0];
  }

  private static Level busiestLevel(Home home) {
    List<Level> levels = home.getLevels();
    if (levels.isEmpty()) {
      return null;
    }
    Level busiest = levels.get(0);
    int bestCount = -1;
    for (Level level : levels) {
      int count = furnitureOnLevel(home, level).size();
      if (count > bestCount) {
        bestCount = count;
        busiest = level;
      }
    }
    return busiest;
  }

  private static List<HomePieceOfFurniture> furnitureOnLevel(Home home, Level level) {
    List<HomePieceOfFurniture> result = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (level == null || piece.getLevel() == level || piece.getLevel() == null) {
        result.add(piece);
      }
    }
    return result;
  }

  private static int clamp(int value, int max) {
    return Math.max(0, Math.min(value, max));
  }

  private static long median(long [] values) {
    return percentile(values, 50);
  }

  private static long percentile(long [] values, int percentile) {
    long [] sorted = values.clone();
    Arrays.sort(sorted);
    int index = (int)Math.ceil(percentile / 100d * sorted.length) - 1;
    return sorted [Math.max(0, index)];
  }
}
