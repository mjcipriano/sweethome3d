/*
 * GraphicsEnvironmentConfiguration.java 7 juin 2026
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
package com.eteks.sweethome3d.tools;

/**
 * Applies platform-specific graphics defaults before any AWT, Java 2D or Java 3D
 * class is loaded. All detection and tuning is kept here so the rest of the code
 * stays free of scattered platform checks, and every change is opt-out:
 * <ul>
 *   <li>{@code com.eteks.sweethome3d.graphics.autoTune=false} disables all tuning;</li>
 *   <li>{@code com.eteks.sweethome3d.graphics.autoTune=verbose} logs each decision;</li>
 *   <li>a property already set by the user (for example with {@code -D...}) is never overridden.</li>
 * </ul>
 * The current tuning targets Windows, where the bundled Java 3D pipeline requests
 * scene and implicit antialiasing by default. Antialiasing is the largest
 * per-frame cost on integrated GPUs (common on switchable-graphics laptops), so
 * on Windows the renderer defaults to the faster, no-antialiasing path unless the
 * user asks for quality. It is a no-op on macOS and Linux, where the current
 * defaults are kept.
 * @author Sweet Home 3D performance work
 */
public class GraphicsEnvironmentConfiguration {
  /**
   * Property read by {@code Component3DManager} to choose the Java 3D rendering
   * trade-off. Supported values are {@code speed} and {@code quality}.
   */
  public static final String RENDERING_QUALITY_PROPERTY = "com.eteks.sweethome3d.j3d.renderingQuality";

  private static final String AUTO_TUNE_PROPERTY = "com.eteks.sweethome3d.graphics.autoTune";

  private GraphicsEnvironmentConfiguration() {
    // This class only provides a static method
  }

  /**
   * Sets graphics system properties suited to the current platform. Safe to call
   * once at startup before AWT/Java 3D initialization; does nothing when tuning
   * is disabled or when the platform needs no change.
   */
  public static void applyDefaults() {
    String autoTune = System.getProperty(AUTO_TUNE_PROPERTY);
    if ("false".equalsIgnoreCase(autoTune)) {
      return;
    }
    boolean verbose = "verbose".equalsIgnoreCase(autoTune);

    String operatingSystemName = System.getProperty("os.name", "");
    boolean windows = operatingSystemName.toLowerCase().startsWith("windows");
    if (!windows) {
      if (verbose) {
        log("non-Windows platform (" + operatingSystemName + "), keeping default graphics settings");
      }
      return;
    }

    // Bias the Java 3D pipeline toward speed on Windows (helps integrated GPUs on
    // switchable-graphics laptops the most). Component3DManager turns this into a
    // no-antialiasing configuration.
    setIfAbsent(RENDERING_QUALITY_PROPERTY, "speed", verbose);
    if (verbose) {
      log("applied Windows graphics speed defaults");
    }
  }

  private static void setIfAbsent(String key, String value, boolean verbose) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
      if (verbose) {
        log("set " + key + "=" + value);
      }
    } else if (verbose) {
      log("kept user-provided " + key + "=" + System.getProperty(key));
    }
  }

  private static void log(String message) {
    System.out.println("[SweetHome3D graphics] " + message);
  }
}
