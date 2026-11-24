/*
 * AssistantPatterns.java 23 nov. 2026
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
package com.eteks.sweethome3d.viewcontroller;

import java.io.IOException;

/**
 * Small repeating pictures drawn by computation, used as the textures of what the assistant builds:
 * meshes and lattices one sees through, boards, bricks, stones, leaves. They are drawn without
 * the AWT toolkit this package doesn't use.
 */
final class AssistantPatterns {
  /** The side of the pictures in pixels. */
  static final int SIZE = 64;

  private AssistantPatterns() {
  }

  /**
   * Returns <code>true</code> if one sees through the given pattern.
   */
  static boolean isOpen(String pattern) {
    return pattern.equals("diamond") || pattern.equals("grid") || pattern.equals("fine") || pattern.equals("hex")
        || pattern.equals("lattice_square") || pattern.equals("lattice_diagonal") || pattern.equals("holes")
        || pattern.equals("leaves") || pattern.equals("slats");
  }

  /**
   * Returns the PNG image of a pattern.
   * @param pattern  the name of the pattern
   * @param color    its main color
   * @param color2   the color of its joints, or of its second tone
   * @param ratio    the share of the picture its lines or its holes take, from 0 to 1
   */
  /**
   * Returns the ARGB pixels of a pattern, {@link #SIZE} x {@link #SIZE}, its first row at its top.
   */
  static int [] createPixels(String pattern, int color, int color2, float ratio) {
    int [] pixels = new int [SIZE * SIZE];
    int [][] seeds = getSeeds(pattern.equals("flagstone") ? 5 : 11, pattern.hashCode());
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        pixels [y * SIZE + x] = getPixel(pattern, x, y, color, color2, ratio, seeds);
      }
    }
    return pixels;
  }

  static byte [] create(String pattern, int color, int color2, float ratio) throws IOException {
    byte [] pixels = new byte [SIZE * (SIZE * 4 + 1)];
    int [][] seeds = getSeeds(pattern.equals("flagstone") ? 5 : 11, pattern.hashCode());
    for (int y = 0; y < SIZE; y++) {
      int row = y * (SIZE * 4 + 1);
      for (int x = 0; x < SIZE; x++) {
        int argb = getPixel(pattern, x, y, color, color2, ratio, seeds);
        int index = row + 1 + x * 4;
        pixels [index] = (byte)(argb >> 16);
        pixels [index + 1] = (byte)(argb >> 8);
        pixels [index + 2] = (byte)argb;
        pixels [index + 3] = (byte)(argb >>> 24);
      }
    }
    return AssistantGeneratedModel.encodePng(pixels, SIZE);
  }

  private static int [][] getSeeds(int count, int seed) {
    int [][] seeds = new int [count][2];
    long state = seed * 31L + 7;
    for (int i = 0; i < count; i++) {
      state = state * 6364136223846793005L + 1442695040888963407L;
      seeds [i][0] = (int)((state >>> 33) % SIZE);
      state = state * 6364136223846793005L + 1442695040888963407L;
      seeds [i][1] = (int)((state >>> 33) % SIZE);
    }
    return seeds;
  }

  /**
   * Returns a number from 0 to 1 that depends on a pixel and looks random.
   */
  private static float noise(int x, int y) {
    int hash = x * 374761393 + y * 668265263;
    hash = (hash ^ (hash >>> 13)) * 1274126177;
    return ((hash ^ (hash >>> 16)) & 0xFF) / 255f;
  }

  private static int shade(int color, float factor) {
    int red = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * factor)));
    int green = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * factor)));
    int blue = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
    return 0xFF000000 | red << 16 | green << 8 | blue;
  }

  /**
   * Returns the distance from <code>value</code> to the closest multiple of <code>period</code>.
   */
  private static float wrap(float value, float period) {
    float rest = value % period;
    if (rest < 0) {
      rest += period;
    }
    return Math.min(rest, period - rest);
  }

  private static int getPixel(String pattern, int x, int y, int color, int color2, float ratio, int [][] seeds) {
    float line = Math.max(1.5f, ratio * SIZE / 2);
    if (pattern.equals("diamond") || pattern.equals("lattice_diagonal")) {
      return wrap(x + y, SIZE) < line || wrap(x - y, SIZE) < line ? shade(color, 0.9f + 0.2f * noise(x, y)) : 0;
    } else if (pattern.equals("grid") || pattern.equals("lattice_square")) {
      return wrap(x, SIZE) < line || wrap(y, SIZE) < line ? shade(color, 0.9f + 0.2f * noise(x, y)) : 0;
    } else if (pattern.equals("fine")) {
      return wrap(x, SIZE / 4f) < line / 2 || wrap(y, SIZE / 2f) < line / 2 ? shade(color, 1) : 0;
    } else if (pattern.equals("hex")) {
      // Staggered cells
      boolean upperRow = y < SIZE / 2;
      float vertical = wrap(x + (upperRow ? 0 : SIZE / 2f), SIZE);
      return wrap(y, SIZE / 2f) < line || vertical < line ? shade(color, 1) : 0;
    } else if (pattern.equals("holes")) {
      float radius = (float)Math.sqrt(Math.max(0.02f, Math.min(0.7f, ratio)) / Math.PI) * SIZE;
      double distance = Math.hypot(x + 0.5 - SIZE / 2, y + 0.5 - SIZE / 2);
      return distance < radius ? 0 : shade(color, 0.95f + 0.1f * noise(x, y));
    } else if (pattern.equals("leaves")) {
      // Leaves that let some light through
      float value = noise(x / 3, y / 3) * 0.6f + noise(x, y) * 0.4f;
      return value < ratio ? 0 : shade(value > 0.7f ? color2 : color, 0.8f + 0.4f * noise(y, x));
    } else if (pattern.equals("slats")) {
      // Vertical slats with a gap
      return wrap(x, SIZE) < ratio * SIZE / 2 ? 0 : shade(color, 0.9f + 0.15f * noise(x, y / 8));
    } else if (pattern.equals("boards")) {
      return wrap(x, SIZE) < 1.5f ? shade(color2, 1) : shade(color, 0.93f + 0.12f * noise(x / 2, y / 16));
    } else if (pattern.equals("hboards")) {
      return wrap(y, SIZE) < 1.5f ? shade(color2, 1) : shade(color, 0.93f + 0.12f * noise(x / 16, y / 2));
    } else if (pattern.equals("corrugated")) {
      return shade(color, 0.8f + 0.3f * (float)(0.5 + 0.5 * Math.sin(x * Math.PI * 8 / SIZE)));
    } else if (pattern.startsWith("brick") || pattern.equals("block")) {
      int courses = pattern.equals("block") ? 2 : 4;
      int courseHeight = SIZE / courses;
      int course = y / courseHeight;
      boolean joint = y % courseHeight < 2;
      float brick = SIZE / 2f;
      float offset = course % 2 == 0 ? 0 : brick / 2;
      if (pattern.equals("brick_english") && course % 2 == 1) {
        brick = SIZE / 4f;
        offset = 0;
      } else if (pattern.equals("brick_flemish")) {
        // A long brick then a short one
        float position = ((x + (course % 2 == 0 ? 0 : SIZE * 3 / 8f)) % (SIZE * 3 / 4f) + SIZE) % (SIZE * 3 / 4f);
        joint |= position < 2 || Math.abs(position - SIZE / 2f) < 1;
        brick = 0;
      } else if (pattern.equals("block")) {
        brick = SIZE;
        offset = course % 2 == 0 ? 0 : SIZE / 2f;
      }
      if (brick > 0) {
        joint |= wrap(x + offset, brick) < 1.2f;
      }
      int brickIndex = brick > 0 ? (int)((x + offset) / brick) : x * 4 / (SIZE * 3);
      return joint ? shade(color2, 1) : shade(color, 0.85f + 0.25f * noise(brickIndex + course * 7, course) + 0.06f * noise(x, y));
    } else if (pattern.equals("fieldstone") || pattern.equals("flagstone") || pattern.equals("ashlar")) {
      if (pattern.equals("ashlar")) {
        // Courses of stones of various lengths
        int course = y * 4 / SIZE;
        boolean joint = y % (SIZE / 4) < 2;
        int [] lengths = {20, 28, 16, 24};
        int position = (x + course * 13) % SIZE;
        int stone = 0;
        for (int i = 0, start = 0; i < 4; start += lengths [(i + course) % 4], i++) {
          int end = start + lengths [(i + course) % 4];
          if (position >= start && position < end) {
            stone = i;
            joint |= position - start < 2;
          }
        }
        return joint ? shade(color2, 1) : shade(color, 0.82f + 0.3f * noise(stone + course * 5, course) + 0.06f * noise(x, y));
      }
      // The stone of the closest seed, the picture repeating on its sides
      int closest = 0;
      double best = Double.MAX_VALUE;
      double second = Double.MAX_VALUE;
      for (int i = 0; i < seeds.length; i++) {
        double dx = wrap(x - seeds [i][0], SIZE);
        double dy = wrap(y - seeds [i][1], SIZE);
        double distance = Math.hypot(dx, dy);
        if (distance < best) {
          second = best;
          best = distance;
          closest = i;
        } else if (distance < second) {
          second = distance;
        }
      }
      return second - best < 2.2 ? shade(color2, 1)
          : shade(color, 0.8f + 0.35f * noise(closest * 3, closest) + 0.07f * noise(x, y));
    } else if (pattern.equals("woven")) {
      // A weave of bands seen from the side
      boolean upperRow = (y * 4 / SIZE) % 2 == 0;
      boolean leftHalf = x < SIZE / 2;
      float factor = upperRow == leftHalf ? 1.05f : 0.82f;
      return y % (SIZE / 4) < 1 ? shade(color2, 1) : shade(color, factor + 0.08f * noise(x / 8, y));
    } else if (pattern.equals("reed")) {
      return shade(noise(x, 3) > 0.75f ? color2 : color, 0.85f + 0.3f * noise(x, y / 16));
    } else if (pattern.equals("bamboo")) {
      boolean joint = wrap(x, SIZE / 4f) < 1.2f || wrap(y + (x * 4 / SIZE) * 11, SIZE / 2f) < 1;
      return joint ? shade(color2, 1) : shade(color, 0.85f + 0.3f * (float)Math.sin((x % (SIZE / 4)) * Math.PI / (SIZE / 4)));
    } else if (pattern.equals("wattle")) {
      double wave = Math.sin(x * Math.PI * 4 / SIZE + (y * 8 / SIZE) * Math.PI);
      return wrap(y, SIZE / 8f) < 1 ? shade(color2, 1) : shade(color, 0.85f + 0.2f * (float)wave);
    } else if (pattern.equals("bark_plated")) {
      // Plates between dark cracks, longer than wide, as on old pines
      float nearest = Float.MAX_VALUE;
      float second = Float.MAX_VALUE;
      int plate = 0;
      for (int i = 0; i < seeds.length; i++) {
        float dx = wrap(x - seeds [i][0], SIZE);
        float dy = wrap(y - seeds [i][1], SIZE) * 0.45f;
        float distance = dx * dx + dy * dy;
        if (distance < nearest) {
          second = nearest;
          nearest = distance;
          plate = i;
        } else if (distance < second) {
          second = distance;
        }
      }
      return Math.sqrt(second) - Math.sqrt(nearest) < 1.6f ? shade(color2, 0.8f + 0.2f * noise(x, y))
          : shade(color, 0.82f + 0.3f * noise(plate, 7) + 0.1f * noise(x, y / 3));
    } else if (pattern.equals("bark_furrowed")) {
      // Ridges that run up the trunk and join here and there
      double wave = x + 5 * Math.sin(y * Math.PI * 2 / SIZE) + 3 * Math.sin(y * Math.PI * 6 / SIZE + 1.3);
      double ridge = 0.5 + 0.5 * Math.sin(wave * Math.PI * 2 * 9 / SIZE);
      return ridge < 0.22 && noise(x / 3, y / 20) > 0.25f ? shade(color2, 0.85f + 0.2f * noise(x, y / 4))
          : shade(color, 0.86f + 0.16f * (float)ridge + 0.12f * noise(x, y / 5));
    } else if (pattern.equals("bark_birch")) {
      // A pale bark with dark marks across and curls of peeling paper
      boolean mark = noise(x / 9, y / 2) > 0.86f && noise(x / 9 + 3, y / 2) > 0.3f;
      boolean curl = noise(x / 14, y / 5) > 0.9f;
      return mark ? shade(color2, 0.8f + 0.3f * noise(x, y)) : shade(color, (curl ? 0.84f : 0.95f) + 0.1f * noise(x / 2, y));
    } else if (pattern.equals("bark_smooth")) {
      // Patches of a few tones, as on a crape myrtle or a sycamore
      float nearest = Float.MAX_VALUE;
      int patch = 0;
      for (int i = 0; i < seeds.length; i++) {
        float dx = wrap(x - seeds [i][0], SIZE);
        float dy = wrap(y - seeds [i][1], SIZE) * 0.6f;
        if (dx * dx + dy * dy < nearest) {
          nearest = dx * dx + dy * dy;
          patch = i;
        }
      }
      return shade(patch % 3 == 0 ? color2 : color, (patch % 3 == 1 ? 0.88f : 1) + 0.08f * noise(x / 2, y / 2));
    } else if (pattern.equals("bark")) {
      return shade(noise(x / 3, y / 12) > 0.6f ? color2 : color, 0.85f + 0.3f * noise(x, y / 6));
    }
    if (pattern.equals("basket")) {
      // Pairs of pavers, turned from a square to the next one
      boolean across = ((x * 2 / SIZE) + (y * 2 / SIZE)) % 2 == 0;
      boolean joint = wrap(x, SIZE / 2f) < 1.2f || wrap(y, SIZE / 2f) < 1.2f || wrap(across ? y : x, SIZE / 4f) < 1;
      return joint ? shade(color2, 1) : shade(color, 0.86f + 0.22f * noise(x * 4 / SIZE + (across ? 7 : 0), y * 4 / SIZE));
    } else if (pattern.equals("herringbone")) {
      int fold = y % (SIZE / 2) < SIZE / 4 ? y % (SIZE / 4) : SIZE / 4 - 1 - y % (SIZE / 4);
      boolean joint = (x + fold) % (SIZE / 4) < 2 || y % (SIZE / 4) < 1;
      return joint ? shade(color2, 1) : shade(color, 0.86f + 0.22f * noise((x + fold) * 4 / SIZE, y * 4 / SIZE));
    } else if (pattern.equals("gravel") || pattern.equals("asphalt") || pattern.equals("mulch")) {
      float grain = pattern.equals("asphalt") ? 0.1f : 0.4f;
      return shade(noise(x / 2, y / 2) > 0.8f ? color2 : color, 1 - grain / 2 + grain * noise(x, y));
    } else if (pattern.equals("siding") || pattern.equals("windows")) {
      float lap = (y % (SIZE / 4)) / (float)(SIZE / 4);
      int siding = shade(color, 1.04f - 0.14f * lap);
      if (pattern.equals("windows")) {
        // A window in the middle of each tile
        boolean frame = x >= SIZE * 0.3f && x < SIZE * 0.7f && y >= SIZE * 0.22f && y < SIZE * 0.7f;
        boolean glass = x >= SIZE * 0.33f && x < SIZE * 0.67f && y >= SIZE * 0.25f && y < SIZE * 0.67f
            && Math.abs(x - SIZE / 2) > 0 && Math.abs(y - SIZE * 0.46f) > 0.6f;
        return glass ? shade(color2, 0.9f + 0.2f * (1 - y / (float)SIZE)) : frame ? 0xFFF2F2EE : siding;
      }
      return siding;
    } else if (pattern.equals("shingles")) {
      int row = y * 4 / SIZE;
      boolean joint = y % (SIZE / 4) < 1 || (x + row * 9) % (SIZE / 4) < 1;
      return joint ? shade(color2, 1) : shade(color, 0.85f + 0.22f * noise((x + row * 9) * 4 / SIZE, row));
    } else if (pattern.equals("water")) {
      return shade(color, 0.92f + 0.14f * (float)Math.sin((x + y * 0.5) * Math.PI * 4 / SIZE) * noise(x / 4, y / 4));
    }
    // Stucco, concrete: a slight grain
    return shade(color, 0.94f + 0.12f * noise(x, y));
  }
}
