/*
 * HomeLoadBenchmark.java
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.performance;

import java.io.File;

import com.eteks.sweethome3d.io.ContentRecording;
import com.eteks.sweethome3d.io.DefaultHomeInputStream;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.io.HomeXMLHandler;
import com.eteks.sweethome3d.model.Home;

/**
 * Measures local .sh3d loading without initializing Swing or Java 3D.
 */
public class HomeLoadBenchmark {
  public static void main(String [] args) throws Exception {
    if (args.length < 1 || args.length > 3) {
      System.err.println("Usage: HomeLoadBenchmark <home.sh3d> [recorder|direct] [iterations]");
      System.exit(2);
    }

    File homeFile = new File(args[0]).getCanonicalFile();
    String mode = args.length >= 2 ? args[1] : "recorder";
    int iterations = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
    if (!homeFile.isFile()) {
      throw new IllegalArgumentException("Home file not found: " + homeFile);
    }
    if (!"recorder".equals(mode) && !"direct".equals(mode)) {
      throw new IllegalArgumentException("Mode must be recorder or direct");
    }
    if (iterations < 1) {
      throw new IllegalArgumentException("Iterations must be positive");
    }

    System.out.println("file=" + homeFile);
    System.out.println("bytes=" + homeFile.length());
    System.out.println("mode=" + mode);
    System.out.println("iterations=" + iterations);

    for (int i = 1; i <= iterations; i++) {
      forceGarbageCollection();
      long usedMemoryBefore = usedMemory();
      long start = System.nanoTime();
      Home home = "direct".equals(mode)
          ? readDirect(homeFile)
          : new HomeFileRecorder(0, false, null, false, true).readHome(homeFile.getPath());
      long elapsedNanos = System.nanoTime() - start;
      long usedMemoryAfter = usedMemory();

      System.out.println("iteration=" + i
          + " elapsed_ms=" + nanosToMillis(elapsedNanos)
          + " heap_delta_mb=" + bytesToMegabytes(usedMemoryAfter - usedMemoryBefore)
          + " furniture=" + home.getFurniture().size()
          + " walls=" + home.getWalls().size()
          + " rooms=" + home.getRooms().size()
          + " levels=" + home.getLevels().size());
    }
  }

  private static Home readDirect(File homeFile) throws Exception {
    DefaultHomeInputStream in = new DefaultHomeInputStream(homeFile,
        ContentRecording.INCLUDE_ALL_CONTENT, new HomeXMLHandler(), null, false);
    try {
      return in.readHome();
    } finally {
      in.close();
    }
  }

  private static void forceGarbageCollection() throws InterruptedException {
    System.gc();
    Thread.sleep(100);
  }

  private static long usedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  private static long nanosToMillis(long nanos) {
    return Math.round(nanos / 1000000d);
  }

  private static long bytesToMegabytes(long bytes) {
    return Math.round(bytes / (1024d * 1024d));
  }
}
