/*
 * AssistantLeafTextures.java
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
import java.util.Random;

/**
 * Draws the pictures the generated trees show on the cards of their crowns: a twig with its
 * leaves, its needles or its flowers on a background one sees through. The twig enters the
 * picture at the middle of its bottom side, or lies around its center for tufts of needles.
 * @author Sweet Home 3D
 */
final class AssistantLeafTextures {
  /** The side of the pictures in pixels. */
  static final int SIZE = 128;

  private final int [] pixels = new int [SIZE * SIZE];
  private final Random random;

  private final float  scale;

  private AssistantLeafTextures(long seed, float scale) {
    this.random = new Random(seed);
    this.scale = scale;
  }

  /**
   * Returns the PNG picture of a cluster of leaves.
   * @param kind    <code>ovate</code>, <code>lance</code>, <code>lobed</code>, <code>palmate</code>, <code>round</code>,
   *                <code>broad</code>, <code>small</code>, <code>compound</code>, <code>tuft</code> (long needles around
   *                the end of a twig), <code>spray</code> (short needles along twigs), <code>scale</code> (flat sprays
   *                of cedars and cypresses) or <code>twigs</code> (bare twigs)
   * @param colors  the colors of the leaves, taken at random with more of the first ones
   * @param bark    the color of the twigs
   * @param flower  the color of the flowers, or 0 for none
   * @param flowerShare  how much of the cluster flowers take, from 0 to 1
   * @param leafShare    how many of its leaves the cluster has, from 0 for bare twigs to 1
   * @param shade   a factor that darkens or lightens the whole picture
   * @param scale   the size of the leaves: 1 for a cluster seen from close, less for a larger part of a crown
   */
  static byte [] create(String kind, int [] colors, int bark, int flower, float flowerShare, float leafShare,
                        float shade, float scale, long seed) throws IOException {
    return encode(createPixels(kind, colors, bark, flower, flowerShare, leafShare, shade, scale, seed), SIZE);
  }

  /**
   * Returns the ARGB pixels of the picture of a cluster of leaves, its first row at its top.
   * @see #create(String, int[], int, int, float, float, float, float, long)
   */
  static int [] createPixels(String kind, int [] colors, int bark, int flower, float flowerShare, float leafShare,
                             float shade, float scale, long seed) {
    AssistantLeafTextures texture = new AssistantLeafTextures(seed, scale);
    texture.draw(kind, colors, bark, flower, flowerShare, leafShare, shade);
    return texture.pixels;
  }

  /**
   * Returns the PNG picture of <code>size</code> x <code>size</code> ARGB pixels.
   */
  static byte [] encode(int [] pixels, int size) throws IOException {
    byte [] bytes = new byte [size * (size * 4 + 1)];
    for (int y = 0; y < size; y++) {
      int row = y * (size * 4 + 1);
      for (int x = 0; x < size; x++) {
        int argb = pixels [y * size + x];
        int index = row + 1 + x * 4;
        bytes [index] = (byte)(argb >> 16);
        bytes [index + 1] = (byte)(argb >> 8);
        bytes [index + 2] = (byte)argb;
        bytes [index + 3] = (byte)(argb >>> 24);
      }
    }
    return AssistantGeneratedModel.encodePng(bytes, size);
  }

  /**
   * Returns the share of the pixels of a picture drawn by this class that aren't seen through.
   */
  static float getCover(String kind, long seed) {
    AssistantLeafTextures texture = new AssistantLeafTextures(seed, 1);
    texture.draw(kind, new int [] {0x408030}, 0x604030, 0, 0, 1, 1);
    int count = 0;
    for (int pixel : texture.pixels) {
      if (pixel != 0) {
        count++;
      }
    }
    return (float)count / texture.pixels.length;
  }

  private static int shade(int color, float factor) {
    if (factor > 1) {
      // Lighter without changing the hue: no part of the color goes over its top
      int top = Math.max((color >> 16) & 0xFF, Math.max((color >> 8) & 0xFF, color & 0xFF));
      factor = Math.max(1, Math.min(factor, 232f / Math.max(1, top)));
    }
    int red = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * factor)));
    int green = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * factor)));
    int blue = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
    return 0xFF000000 | red << 16 | green << 8 | blue;
  }

  private void set(int x, int y, int argb) {
    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
      this.pixels [y * SIZE + x] = argb;
    }
  }

  /**
   * Draws a line of the given width, in pixels of the picture with y going down.
   */
  private void line(float x1, float y1, float x2, float y2, float width, int argb) {
    float length = (float)Math.hypot(x2 - x1, y2 - y1);
    int steps = Math.max(1, Math.round(length * 2));
    for (int i = 0; i <= steps; i++) {
      float x = x1 + (x2 - x1) * i / steps;
      float y = y1 + (y2 - y1) * i / steps;
      if (width <= 1.2f) {
        set(Math.round(x), Math.round(y), argb);
      } else {
        int radius = (int)Math.ceil(width / 2);
        for (int dy = -radius; dy <= radius; dy++) {
          for (int dx = -radius; dx <= radius; dx++) {
            if (dx * dx + dy * dy <= width * width / 4 + 0.3f) {
              set(Math.round(x) + dx, Math.round(y) + dy, argb);
            }
          }
        }
      }
    }
  }

  /**
   * Draws a leaf from its base, toward an angle.
   */
  private void leaf(String kind, float baseX, float baseY, double angle, float length, float width, int color) {
    float cos = (float)Math.cos(angle);
    float sin = (float)Math.sin(angle);
    int reach = (int)Math.ceil(length + width) + 1;
    float centerX = baseX + cos * length / 2;
    float centerY = baseY + sin * length / 2;
    float tone = 0.9f + this.random.nextFloat() * 0.2f;
    boolean palmate = kind.equals("palmate");
    boolean lobed = kind.equals("lobed");
    boolean round = kind.equals("round");
    for (int y = Math.round(centerY) - reach; y <= Math.round(centerY) + reach; y++) {
      for (int x = Math.round(centerX) - reach; x <= Math.round(centerX) + reach; x++) {
        // Along and across the leaf
        float along = ((x - baseX) * cos + (y - baseY) * sin) / length;
        float across = (-(x - baseX) * sin + (y - baseY) * cos) / (width / 2);
        boolean inside;
        if (palmate) {
          // Five points around the middle of the leaf
          float dx = along - 0.45f;
          float dy = across * width / 2 / length;
          double direction = Math.atan2(dy, dx);
          double lobe = Math.abs(Math.cos(direction * 2.5));
          inside = Math.hypot(dx, dy) <= 0.2 + 0.36 * lobe * lobe;
        } else if (along < 0 || along > 1) {
          inside = false;
        } else if (round) {
          inside = Math.abs(across) <= Math.sqrt(Math.max(0, 1 - (2 * along - 0.95) * (2 * along - 0.95)));
        } else {
          float half = (float)Math.sin(Math.PI * Math.pow(along, 0.8));
          if (lobed) {
            half *= 0.55f + 0.45f * (float)Math.abs(Math.sin(along * Math.PI * 3.5));
          }
          inside = Math.abs(across) <= half;
        }
        if (inside) {
          // A lighter rib, a darker base
          float light = tone * (0.86f + 0.2f * Math.min(1, Math.max(0, along)))
              * (Math.abs(across) < 0.12f && !palmate ? 1.12f : 1);
          set(x, y, shade(color, light));
        }
      }
    }
  }

  private int pick(int [] colors, float shade) {
    // More of the first colors
    float value = this.random.nextFloat();
    int index = Math.min(colors.length - 1, (int)(value * value * colors.length));
    return shade(colors [index], shade) & 0xFFFFFF;
  }

  private void flowers(float x, float y, float spread, int flower, float flowerShare, float shade) {
    if (flower != 0 && this.random.nextFloat() < flowerShare) {
      float flowerX = x + (this.random.nextFloat() - 0.5f) * spread;
      float flowerY = y + (this.random.nextFloat() - 0.5f) * spread;
      float radius = 2.2f + this.random.nextFloat() * 1.6f;
      int color = shade(flower, shade * (0.9f + this.random.nextFloat() * 0.2f));
      for (int dy = -4; dy <= 4; dy++) {
        for (int dx = -4; dx <= 4; dx++) {
          if (dx * dx + dy * dy <= radius * radius) {
            set(Math.round(flowerX) + dx, Math.round(flowerY) + dy, dx * dx + dy * dy < 1.5f ? shade(flower, shade * 0.8f) : color);
          }
        }
      }
    }
  }

  private void draw(String kind, int [] colors, int bark, int flower, float flowerShare, float leafShare, float shade) {
    int twig = shade(bark, shade * 0.85f);
    if (kind.equals("tuft")) {
      // Long needles around the ends of a few twigs
      float [][] ends = {{SIZE * 0.5f, SIZE * 0.5f}, {SIZE * 0.3f, SIZE * 0.36f}, {SIZE * 0.7f, SIZE * 0.4f}, {SIZE * 0.42f, SIZE * 0.72f},
                         {SIZE * 0.68f, SIZE * 0.7f}};
      for (int i = 1; i < ends.length; i++) {
        line(ends [0][0], ends [0][1], ends [i][0], ends [i][1], 2, twig);
      }
      for (float [] end : ends) {
        int needles = 30 + this.random.nextInt(12);
        for (int i = 0; i < needles; i++) {
          double angle = Math.PI * 2 * i / needles + this.random.nextFloat() * 0.3;
          float length = SIZE * (0.15f + this.random.nextFloat() * 0.09f);
          line(end [0], end [1], end [0] + (float)Math.cos(angle) * length, end [1] + (float)Math.sin(angle) * length,
              this.random.nextFloat() < 0.35f ? 1.9f : 1, shade(pick(colors, shade), 0.8f + this.random.nextFloat() * 0.4f));
        }
      }
      return;
    }
    // Twigs from the bottom of the picture
    int twigCount = Math.round((kind.equals("twigs") ? 8 : 9) / Math.max(0.5f, this.scale));
    float [][] twigs = new float [twigCount * 3 + 1][];
    int count = 0;
    twigs [count++] = new float [] {SIZE * 0.5f, SIZE - 1, SIZE * (0.47f + this.random.nextFloat() * 0.06f), SIZE * 0.22f};
    for (int i = 0; i < twigCount; i++) {
      float t = 0.12f + 0.8f * i / twigCount;
      float [] stem = twigs [0];
      float startX = stem [0] + (stem [2] - stem [0]) * t;
      float startY = stem [1] + (stem [3] - stem [1]) * t;
      float side = i % 2 == 0 ? -1 : 1;
      float length = SIZE * (0.44f - 0.2f * t) * (0.8f + this.random.nextFloat() * 0.4f);
      double angle = -Math.PI / 2 + side * (0.5 + this.random.nextFloat() * 0.75);
      float endX = startX + (float)Math.cos(angle) * length;
      float endY = startY + (float)Math.sin(angle) * length;
      twigs [count++] = new float [] {startX, startY, endX, endY};
      // A smaller one from its middle
      double angle2 = angle - side * (0.5 + this.random.nextFloat() * 0.4);
      float middleX = (startX + endX) / 2;
      float middleY = (startY + endY) / 2;
      twigs [count++] = new float [] {middleX, middleY, middleX + (float)Math.cos(angle2) * length * 0.55f,
          middleY + (float)Math.sin(angle2) * length * 0.55f};
      if (kind.equals("twigs") || kind.equals("scale") || kind.equals("spray")) {
        double angle3 = angle + side * (0.45 + this.random.nextFloat() * 0.4);
        twigs [count++] = new float [] {middleX, middleY, middleX + (float)Math.cos(angle3) * length * 0.5f,
            middleY + (float)Math.sin(angle3) * length * 0.5f};
      }
    }
    if (kind.equals("scale")) {
      // Flat sprays: thick soft strokes with short ones along them
      for (int i = 0; i < count; i++) {
        float [] line = twigs [i];
        int color = shade(pick(colors, shade), 0.85f + this.random.nextFloat() * 0.3f);
        line(line [0], line [1], line [2], line [3], i == 0 ? 4 : 3.2f, color);
        double direction = Math.atan2(line [3] - line [1], line [2] - line [0]);
        int steps = (int)(Math.hypot(line [2] - line [0], line [3] - line [1]) / 4);
        for (int k = 1; k <= steps; k++) {
          float x = line [0] + (line [2] - line [0]) * k / steps;
          float y = line [1] + (line [3] - line [1]) * k / steps;
          double side = direction + (k % 2 == 0 ? 0.7 : -0.7);
          float length = SIZE * (0.05f + this.random.nextFloat() * 0.05f);
          line(x, y, x + (float)Math.cos(side) * length, y + (float)Math.sin(side) * length, 2.4f,
              shade(pick(colors, shade), 0.8f + this.random.nextFloat() * 0.35f));
        }
      }
      return;
    }
    for (int i = 0; i < count; i++) {
      line(twigs [i][0], twigs [i][1], twigs [i][2], twigs [i][3], i == 0 ? 2.4f : kind.equals("twigs") ? 1.2f : 1.4f, twig);
    }
    if (kind.equals("twigs")) {
      // Finer twigs at the ends, and the few leaves the winter left
      for (int i = 1; i < count; i++) {
        float [] line = twigs [i];
        double direction = Math.atan2(line [3] - line [1], line [2] - line [0]);
        for (int k = 0; k < 3; k++) {
          float t = 0.45f + 0.25f * k;
          float x = line [0] + (line [2] - line [0]) * t;
          float y = line [1] + (line [3] - line [1]) * t;
          double side = direction + (k % 2 == 0 ? 0.6 : -0.6) * (0.7 + this.random.nextFloat() * 0.6);
          float length = SIZE * (0.06f + this.random.nextFloat() * 0.07f);
          line(x, y, x + (float)Math.cos(side) * length, y + (float)Math.sin(side) * length, 1, twig);
          if (this.random.nextFloat() < leafShare) {
            leaf("ovate", x, y, Math.PI / 2 + (this.random.nextFloat() - 0.5f), SIZE * 0.07f, SIZE * 0.035f, pick(colors, shade));
          }
          flowers(x, y, SIZE * 0.08f, flower, flowerShare, shade);
        }
      }
      return;
    }
    if (kind.equals("spray")) {
      // Short needles on both sides of the twigs
      for (int i = 0; i < count; i++) {
        float [] line = twigs [i];
        double direction = Math.atan2(line [3] - line [1], line [2] - line [0]);
        int steps = (int)(Math.hypot(line [2] - line [0], line [3] - line [1]) / 1.6f);
        for (int k = 1; k <= steps; k++) {
          float x = line [0] + (line [2] - line [0]) * k / steps;
          float y = line [1] + (line [3] - line [1]) * k / steps;
          double side = direction + (k % 2 == 0 ? 1 : -1) * (0.9 + this.random.nextFloat() * 0.4);
          float length = SIZE * (0.045f + this.random.nextFloat() * 0.03f);
          line(x, y, x + (float)Math.cos(side) * length, y + (float)Math.sin(side) * length, 1.6f,
              shade(pick(colors, shade), 0.8f + this.random.nextFloat() * 0.4f));
        }
      }
      return;
    }
    // Leaves along the twigs, the ones under drawn first and darker
    float leafLength = SIZE * this.scale * 0.68f * (kind.equals("small") ? 0.085f : kind.equals("lance") ? 0.17f : kind.equals("broad") ? 0.19f
        : kind.equals("palmate") ? 0.2f : kind.equals("lobed") ? 0.18f : kind.equals("round") ? 0.12f
        : kind.equals("compound") ? 0.09f : 0.14f);
    float leafWidth = leafLength * (kind.equals("lance") ? 0.2f : kind.equals("broad") ? 0.42f : kind.equals("lobed") ? 0.62f
        : kind.equals("round") ? 0.95f : kind.equals("compound") ? 0.36f : kind.equals("palmate") ? 1 : 0.52f);
    int perTwig = Math.round((kind.equals("small") || kind.equals("compound") ? 13 : kind.equals("lance") ? 10 : 8)
        / Math.max(0.6f, this.scale));
    for (int layer = 0; layer < 2; layer++) {
      for (int i = count - 1; i >= 0; i--) {
        float [] line = twigs [i];
        double direction = Math.atan2(line [3] - line [1], line [2] - line [0]);
        for (int k = 0; k < perTwig; k++) {
          if (this.random.nextFloat() > leafShare) {
            continue;
          }
          float t = (k + 0.6f + this.random.nextFloat() * 0.5f) / perTwig;
          float x = line [0] + (line [2] - line [0]) * t;
          float y = line [1] + (line [3] - line [1]) * t;
          if (i == 0 && t < 0.3f) {
            continue;
          }
          double angle = direction + (k % 2 == 0 ? 1 : -1) * (0.5 + this.random.nextFloat() * 0.7);
          if (k == perTwig - 1) {
            angle = direction + (this.random.nextFloat() - 0.5) * 0.4;
          }
          float size = 0.75f + this.random.nextFloat() * 0.5f;
          leaf(kind, x, y, angle, leafLength * size, leafWidth * size, pick(colors, shade * (layer == 0 ? 0.74f : 1)));
          flowers(x, y, leafLength, flower, layer == 1 ? flowerShare : 0, shade);
        }
      }
    }
  }
}
