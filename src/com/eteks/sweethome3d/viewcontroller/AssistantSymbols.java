/*
 * AssistantSymbols.java 21 nov. 2026
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Content;

/**
 * The electrical and plumbing symbols of the design assistant: for each type, the parts of the
 * small model that shows it in 3D, and the drawing that shows it in the plan.
 * @author Sweet Home 3D
 */
final class AssistantSymbols {
  /** A symbol fixed on a wall, its back against it: its height is the one of its center. */
  static final int WALL = 0;
  /** A symbol under the ceiling: its height is the one of its top. */
  static final int CEILING = 1;
  /** A symbol on the floor: its height is the one of its bottom. */
  static final int FLOOR = 2;

  static final String ELECTRICAL = "electrical";
  static final String PLUMBING = "plumbing";

  private static final int ELECTRICAL_COLOR = 0xA93226;
  private static final int PLUMBING_COLOR = 0x1F618D;
  private static final int ICON_SIZE = 96;

  /**
   * A type of symbol.
   */
  static final class Type {
    final String name;
    final String label;
    final String category;
    final int    mount;
    /** The default height in cm, or NaN for the ceiling. */
    final float  height;
    /** The size of the symbol in the plan. */
    final float  width;
    final float  depth;

    private Type(String name, String label, String category, int mount, float height, float width, float depth) {
      this.name = name;
      this.label = label;
      this.category = category;
      this.mount = mount;
      this.height = height;
      this.width = width;
      this.depth = depth;
    }
  }

  private static final Type [] TYPES = {
      new Type("outlet", "Outlet", ELECTRICAL, WALL, 30, 16, 12),
      new Type("double_outlet", "Double outlet", ELECTRICAL, WALL, 30, 16, 12),
      new Type("switch", "Switch", ELECTRICAL, WALL, 110, 16, 12),
      new Type("three_way_switch", "Three-way switch", ELECTRICAL, WALL, 110, 16, 12),
      new Type("ceiling_light", "Ceiling light", ELECTRICAL, CEILING, Float.NaN, 22, 22),
      new Type("wall_light", "Wall light", ELECTRICAL, WALL, 180, 16, 12),
      new Type("fan", "Ceiling fan", ELECTRICAL, CEILING, Float.NaN, 122, 122),
      new Type("smoke_detector", "Smoke detector", ELECTRICAL, CEILING, Float.NaN, 14, 14),
      new Type("thermostat", "Thermostat", ELECTRICAL, WALL, 150, 16, 10),
      new Type("data", "Data outlet", ELECTRICAL, WALL, 30, 16, 12),
      new Type("tv", "TV outlet", ELECTRICAL, WALL, 30, 16, 12),
      new Type("panel", "Electrical panel", ELECTRICAL, WALL, 150, 36, 10),
      new Type("sink", "Sink rough-in", PLUMBING, WALL, 50, 40, 12),
      new Type("toilet_rough", "Toilet rough-in", PLUMBING, FLOOR, 0, 20, 20),
      new Type("shower_drain", "Shower drain", PLUMBING, FLOOR, 0, 14, 14),
      new Type("hose_bib", "Hose bib", PLUMBING, WALL, 45, 16, 12),
      new Type("water_heater", "Water heater", PLUMBING, FLOOR, 0, 55, 55),
      new Type("floor_drain", "Floor drain", PLUMBING, FLOOR, 0, 16, 16)};

  private AssistantSymbols() {
  }

  static Type [] getTypes() {
    return TYPES.clone();
  }

  /**
   * Returns the type with the given name, or <code>null</code>.
   */
  static Type getType(String name) {
    if (name == null) {
      return null;
    }
    String text = name.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
    if (text.equals("receptacle") || text.equals("socket") || text.equals("outlets")) {
      text = "outlet";
    } else if (text.equals("quad_outlet") || text.equals("double_receptacle")) {
      text = "double_outlet";
    } else if (text.equals("3_way_switch") || text.equals("two_way_switch") || text.equals("3way_switch")) {
      text = "three_way_switch";
    } else if (text.equals("light") || text.equals("ceiling_lamp")) {
      text = "ceiling_light";
    } else if (text.equals("sconce")) {
      text = "wall_light";
    } else if (text.equals("ceiling_fan")) {
      text = "fan";
    } else if (text.equals("smoke") || text.equals("smoke_alarm")) {
      text = "smoke_detector";
    } else if (text.equals("ethernet") || text.equals("network")) {
      text = "data";
    } else if (text.equals("breaker_panel") || text.equals("electrical_panel")) {
      text = "panel";
    } else if (text.equals("sink_rough") || text.equals("sink_rough_in")) {
      text = "sink";
    } else if (text.equals("toilet") || text.equals("toilet_rough_in")) {
      text = "toilet_rough";
    } else if (text.equals("hose_bibb") || text.equals("spigot")) {
      text = "hose_bib";
    }
    for (Type type : TYPES) {
      if (type.name.equals(text)) {
        return type;
      }
    }
    return null;
  }

  static String describeTypes() {
    StringBuilder text = new StringBuilder();
    for (Type type : TYPES) {
      text.append(text.length() > 0 ? ", " : "").append(type.name);
    }
    return text.toString();
  }

  private static Map<String, Object> part(String shape, double x, double y, double z,
                                          double width, double depth, double height, int color) {
    Map<String, Object> part = new LinkedHashMap<String, Object>();
    part.put("shape", shape);
    part.put("x", Double.valueOf(x));
    part.put("y", Double.valueOf(y));
    part.put("z", Double.valueOf(z));
    part.put("width", Double.valueOf(width));
    part.put("depth", Double.valueOf(depth));
    part.put("height", Double.valueOf(height));
    part.put("color", String.format("#%06X", color));
    return part;
  }

  private static Map<String, Object> pipe(double x, double y, double z, double diameter, double length, int color) {
    // A cylinder that comes out of the wall
    Map<String, Object> part = part("cylinder", x, y, z, diameter, length, diameter, color);
    part.put("axis", "y");
    return part;
  }

  /**
   * Returns the parts of the model of a type of symbol, in the frame of a piece of furniture:
   * x to its right, y toward its front and z upward, its back at <code>-depth / 2</code>.
   */
  static List<Map<String, Object>> getParts(Type type) {
    List<Map<String, Object>> parts = new ArrayList<Map<String, Object>>();
    String name = type.name;
    double back = -type.depth / 2;
    int plate = 0xF2EFE6;
    // The height where the size of the symbol in the plan is marked
    double middle = 0;
    if ("outlet".equals(name) || "data".equals(name) || "tv".equals(name)) {
      plate = "data".equals(name) ? 0xE8EEF5 : "tv".equals(name) ? 0xEDEDED : 0xF2EFE6;
      parts.add(part("box", 0, back + 0.6, 0, 7, 1.2, 11.5, plate));
      middle = 5.75;
    } else if ("double_outlet".equals(name)) {
      parts.add(part("box", 0, back + 0.6, 0, 14.5, 1.2, 11.5, plate));
      middle = 5.75;
    } else if ("switch".equals(name) || "three_way_switch".equals(name)) {
      parts.add(part("box", 0, back + 0.6, 0, 7, 1.2, 11.5, plate));
      parts.add(part("box", 0, back + 1.6, 4.25, 1.5, 0.8, 3, 0xDDDAD0));
      middle = 5.75;
    } else if ("wall_light".equals(name)) {
      plate = 0xD9D9D9;
      parts.add(part("box", 0, back + 0.75, 0, 10, 1.5, 10, plate));
      parts.add(part("sphere", 0, back + 5.5, 1, 8, 8, 8, 0xFFF6D5));
      middle = 5;
    } else if ("thermostat".equals(name)) {
      plate = 0xFAFAFA;
      parts.add(part("box", 0, back + 1.25, 0, 12, 2.5, 8, plate));
      middle = 4;
    } else if ("panel".equals(name)) {
      parts.add(part("box", 0, 0, 0, type.width, type.depth, 60, 0x9AA0A6));
      return parts;
    } else if ("sink".equals(name)) {
      plate = 0xDADADA;
      parts.add(part("box", 0, back + 0.5, 0, 30, 1, 16, plate));
      parts.add(pipe(-10, back + 3.5, 11, 2, 5, 0xC0392B));
      parts.add(pipe(10, back + 3.5, 11, 2, 5, 0x2E86C1));
      parts.add(pipe(0, back + 4, 2, 4, 6, 0xEEEEEE));
      middle = 8;
    } else if ("hose_bib".equals(name)) {
      plate = 0xB8B8B8;
      parts.add(part("box", 0, back + 0.5, 0, 6, 1, 6, plate));
      parts.add(pipe(0, back + 5, 1.5, 3, 8, 0xB08D3C));
      parts.add(part("box", 0, back + 6, 5, 6, 1, 1, 0xC0392B));
      middle = 3;
    } else if ("ceiling_light".equals(name)) {
      parts.add(part("cylinder", 0, 0, 0, type.width, type.depth, 5, 0xFFFBEA));
      return parts;
    } else if ("fan".equals(name)) {
      parts.add(part("cylinder", 0, 0, 0, 18, 18, 12, 0x8B8B8B));
      parts.add(part("cylinder", 0, 0, 12, 3, 3, 20, 0x8B8B8B));
      parts.add(part("box", 35, 0, 5, 52, 13, 1.2, 0x8B5E3C));
      parts.add(part("box", -35, 0, 5, 52, 13, 1.2, 0x8B5E3C));
      parts.add(part("box", 0, 35, 5, 13, 52, 1.2, 0x8B5E3C));
      parts.add(part("box", 0, -35, 5, 13, 52, 1.2, 0x8B5E3C));
      return parts;
    } else if ("smoke_detector".equals(name)) {
      parts.add(part("cylinder", 0, 0, 0, type.width, type.depth, 4, 0xFAFAFA));
      return parts;
    } else if ("toilet_rough".equals(name)) {
      parts.add(part("cylinder", 0, 0, 0, type.width, type.depth, 1, 0xF4F4F4));
      parts.add(part("cylinder", 0, 0, 0, 10, 10, 1.6, 0x333333));
      return parts;
    } else if ("shower_drain".equals(name)) {
      parts.add(part("cylinder", 0, 0, 0, type.width, type.depth, 0.6, 0xC8CCD0));
      parts.add(part("cylinder", 0, 0, 0, 5, 5, 0.8, 0x444444));
      return parts;
    } else if ("floor_drain".equals(name)) {
      parts.add(part("box", 0, 0, 0, type.width, type.depth, 0.6, 0xC8CCD0));
      parts.add(part("cylinder", 0, 0, 0, 10, 10, 0.8, 0x555555));
      return parts;
    } else if ("water_heater".equals(name)) {
      parts.add(part("cylinder", 0, 0, 0, type.width, type.depth, 150, 0xE4E7EA));
      parts.add(part("cylinder", -12, 0, 150, 3, 3, 6, 0x2E86C1));
      parts.add(part("cylinder", 12, 0, 150, 3, 3, 6, 0xC0392B));
      return parts;
    }
    // A plate on a wall is too thin to be read in the plan: four specks of half a millimeter
    // give the piece the size of its drawing
    double speck = 0.06;
    for (int i = 0; i < 4; i++) {
      parts.add(part("box", (i % 2 == 0 ? -1 : 1) * (type.width / 2 - speck / 2),
          (i < 2 ? -1 : 1) * (type.depth / 2 - speck / 2), middle, speck, speck, speck, plate));
    }
    return parts;
  }

  /**
   * Returns the picture that draws a type of symbol in the plan, seen from the top,
   * the back of the symbol at its top.
   */
  static Content createPlanIcon(Type type) throws IOException {
    Canvas canvas = new Canvas(type.width, type.depth, PLUMBING.equals(type.category) ? PLUMBING_COLOR : ELECTRICAL_COLOR);
    String name = type.name;
    float back = -type.depth / 2;
    if ("outlet".equals(name) || "double_outlet".equals(name)) {
      canvas.ring(0, 0, 5);
      canvas.line(-1.8f, back, -1.8f, 5.2f);
      canvas.line(1.8f, back, 1.8f, 5.2f);
      if ("double_outlet".equals(name)) {
        canvas.line(-4.6f, -1.8f, 4.6f, -1.8f);
        canvas.line(-4.6f, 1.8f, 4.6f, 1.8f);
      }
    } else if ("switch".equals(name) || "three_way_switch".equals(name)) {
      canvas.disc(0, -3.2f, 2.2f);
      canvas.line(0, -3.2f, 5, 4.5f);
      canvas.line(5, 4.5f, 7.2f, 3);
      if ("three_way_switch".equals(name)) {
        canvas.line(0, -3.2f, -5, 4.5f);
        canvas.line(-5, 4.5f, -7.2f, 3);
      }
    } else if ("wall_light".equals(name)) {
      canvas.line(0, back, 0, -4.5f);
      canvas.ring(0, 0, 4.5f);
      canvas.line(-3.2f, -3.2f, 3.2f, 3.2f);
      canvas.line(-3.2f, 3.2f, 3.2f, -3.2f);
    } else if ("thermostat".equals(name)) {
      canvas.rectangle(-6, back + 0.4f, 6, 3);
      canvas.line(-2.5f, -2.6f, 2.5f, -2.6f);
      canvas.line(0, -2.6f, 0, 1.4f);
    } else if ("data".equals(name)) {
      canvas.line(-5.5f, back + 0.4f, 5.5f, back + 0.4f);
      canvas.line(5.5f, back + 0.4f, 0, 4.5f);
      canvas.line(0, 4.5f, -5.5f, back + 0.4f);
    } else if ("tv".equals(name)) {
      canvas.rectangle(-5, back + 0.4f, 5, 1.5f);
      canvas.line(0, 1.5f, -3.5f, 5.5f);
      canvas.line(0, 1.5f, 3.5f, 5.5f);
    } else if ("panel".equals(name)) {
      canvas.triangle(-17, -4, 17, -4, -17, 4);
      canvas.rectangle(-17, -4, 17, 4);
    } else if ("sink".equals(name)) {
      canvas.line(-19, back + 0.6f, 19, back + 0.6f);
      canvas.ring(-10, -1, 2.5f);
      canvas.ring(10, -1, 2.5f);
      canvas.ring(0, 1, 3.5f);
      canvas.line(-2.5f, 1, 2.5f, 1);
      canvas.line(0, -1.5f, 0, 3.5f);
    } else if ("hose_bib".equals(name)) {
      canvas.line(0, back, 0, -2);
      canvas.ring(0, 1, 3);
      canvas.line(0, 4, 0, 5.2f);
      canvas.line(-4, 5.2f, 4, 5.2f);
    } else if ("ceiling_light".equals(name)) {
      canvas.ring(0, 0, 9.5f);
      canvas.line(-6.7f, -6.7f, 6.7f, 6.7f);
      canvas.line(-6.7f, 6.7f, 6.7f, -6.7f);
    } else if ("fan".equals(name)) {
      canvas.line(12, 0, 57, 0);
      canvas.line(-12, 0, -57, 0);
      canvas.line(0, 12, 0, 57);
      canvas.line(0, -12, 0, -57);
      canvas.ring(0, 0, 10);
    } else if ("smoke_detector".equals(name)) {
      canvas.ring(0, 0, 6.3f);
      canvas.disc(0, 0, 2);
    } else if ("toilet_rough".equals(name)) {
      canvas.ring(0, 0, 9);
      canvas.ring(0, 0, 4.5f);
      canvas.disc(-6.8f, 0, 1);
      canvas.disc(6.8f, 0, 1);
    } else if ("shower_drain".equals(name)) {
      canvas.ring(0, 0, 6.2f);
      canvas.line(-4.4f, -4.4f, 4.4f, 4.4f);
      canvas.line(-4.4f, 4.4f, 4.4f, -4.4f);
    } else if ("floor_drain".equals(name)) {
      canvas.rectangle(-7.4f, -7.4f, 7.4f, 7.4f);
      canvas.ring(0, 0, 5);
      canvas.disc(0, 0, 1.5f);
    } else if ("water_heater".equals(name)) {
      canvas.ring(0, 0, 26);
      canvas.ring(0, 0, 6);
      canvas.ring(-12, -14, 3);
      canvas.ring(12, -14, 3);
    }
    return AssistantGeneratedModel.storeFile("symbol", ".png", canvas.toPng());
  }

  /**
   * A drawing made of lines, circles and filled shapes, in the frame of a piece seen from the top.
   */
  private static final class Canvas {
    private final float          width;
    private final float          depth;
    private final float          stroke;
    private final int            color;
    private final List<float []> shapes = new ArrayList<float []>();

    Canvas(float width, float depth, int color) {
      this.width = width;
      this.depth = depth;
      this.color = color;
      this.stroke = Math.max(0.7f, Math.max(width, depth) * 0.055f);
    }

    void line(float x1, float y1, float x2, float y2) {
      this.shapes.add(new float [] {0, x1, y1, x2, y2});
    }

    void ring(float x, float y, float radius) {
      this.shapes.add(new float [] {1, x, y, radius});
    }

    void disc(float x, float y, float radius) {
      this.shapes.add(new float [] {2, x, y, radius});
    }

    void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
      this.shapes.add(new float [] {3, x1, y1, x2, y2, x3, y3});
    }

    void rectangle(float x1, float y1, float x2, float y2) {
      line(x1, y1, x2, y1);
      line(x2, y1, x2, y2);
      line(x2, y2, x1, y2);
      line(x1, y2, x1, y1);
    }

    private boolean contains(float [] shape, float x, float y) {
      float half = this.stroke / 2;
      switch ((int)shape [0]) {
        case 0 : {
          float dx = shape [3] - shape [1];
          float dy = shape [4] - shape [2];
          float lengthSquare = dx * dx + dy * dy;
          float t = lengthSquare > 0 ? ((x - shape [1]) * dx + (y - shape [2]) * dy) / lengthSquare : 0;
          t = Math.max(0, Math.min(1, t));
          float px = shape [1] + t * dx - x;
          float py = shape [2] + t * dy - y;
          return px * px + py * py <= half * half;
        }
        case 1 :
          return Math.abs(Math.hypot(x - shape [1], y - shape [2]) - shape [3]) <= half;
        case 2 :
          return Math.hypot(x - shape [1], y - shape [2]) <= shape [3];
        default : {
          float d1 = (x - shape [3]) * (shape [2] - shape [4]) - (shape [1] - shape [3]) * (y - shape [4]);
          float d2 = (x - shape [5]) * (shape [4] - shape [6]) - (shape [3] - shape [5]) * (y - shape [6]);
          float d3 = (x - shape [1]) * (shape [6] - shape [2]) - (shape [5] - shape [1]) * (y - shape [2]);
          boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
          boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
          return !(negative && positive);
        }
      }
    }

    byte [] toPng() throws IOException {
      int size = ICON_SIZE;
      int sampling = 3;
      byte [] pixels = new byte [size * (1 + 4 * size)];
      for (int row = 0; row < size; row++) {
        for (int column = 0; column < size; column++) {
          int covered = 0;
          for (int i = 0; i < sampling * sampling; i++) {
            float x = (column + (i % sampling + 0.5f) / sampling) / size * this.width - this.width / 2;
            float y = (row + (i / sampling + 0.5f) / sampling) / size * this.depth - this.depth / 2;
            for (float [] shape : this.shapes) {
              if (contains(shape, x, y)) {
                covered++;
                break;
              }
            }
          }
          int offset = row * (1 + 4 * size) + 1 + column * 4;
          pixels [offset] = (byte)((this.color >> 16) & 0xFF);
          pixels [offset + 1] = (byte)((this.color >> 8) & 0xFF);
          pixels [offset + 2] = (byte)(this.color & 0xFF);
          pixels [offset + 3] = (byte)(255 * covered / (sampling * sampling));
        }
      }
      return AssistantGeneratedModel.encodePng(pixels, size);
    }
  }
}
