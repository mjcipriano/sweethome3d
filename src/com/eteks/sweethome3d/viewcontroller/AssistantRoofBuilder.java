/*
 * AssistantRoofBuilder.java 13 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.tools.OperatingSystem;
import com.eteks.sweethome3d.tools.TemporaryURLContent;

/**
 * Builds the 3D model of a roof covering a rectangle, as Sweet Home 3D has no
 * roof object: the roof is a piece of furniture with a generated model, like
 * the roofs users import by hand.
 * @author Sweet Home 3D
 */
public class AssistantRoofBuilder {
  /** The shapes of roof this builder knows. */
  public enum Type {
    /** Two slopes meeting at a ridge, with a vertical triangle at both ends. */
    GABLE,
    /** Four slopes. */
    HIP,
    /** One slope. */
    SHED,
    /** A slab. */
    FLAT;

    /**
     * Returns the type matching <code>name</code>, or <code>null</code>.
     */
    public static Type parse(String name) {
      if (name != null) {
        String normalized = name.trim().toUpperCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
        if ("PITCHED".equals(normalized) || "SADDLE".equals(normalized)) {
          return GABLE;
        } else if ("HIPPED".equals(normalized) || "PYRAMID".equals(normalized)) {
          return HIP;
        } else if ("MONO".equals(normalized) || "LEAN_TO".equals(normalized) || "SKILLION".equals(normalized)) {
          return SHED;
        }
        for (Type type : values()) {
          if (type.name().equals(normalized)) {
            return type;
          }
        }
      }
      return null;
    }
  }

  /** Thickness of a flat roof in centimeters. */
  public static final float FLAT_ROOF_THICKNESS = 20;

  private final Content model;
  private final Content icon;
  private final float   height;

  private AssistantRoofBuilder(Content model, Content icon, float height) {
    this.model = model;
    this.icon = icon;
    this.height = height;
  }

  /**
   * Returns the 3D model of the roof.
   */
  public Content getModel() {
    return this.model;
  }

  /**
   * Returns the icon of the roof.
   */
  public Content getIcon() {
    return this.icon;
  }

  /**
   * Returns the height of the roof in centimeters, from its base to its ridge.
   */
  public float getHeight() {
    return this.height;
  }

  /**
   * Returns the height of a roof of the given type.
   * @param width  size of the roof along x in centimeters
   * @param depth  size of the roof along y in centimeters
   * @param pitch  angle of the slopes in degrees
   * @param ridgeAlongWidth <code>true</code> if the ridge is parallel to x
   */
  public static float getRoofHeight(Type type, float width, float depth, float pitch,
                                    boolean ridgeAlongWidth) {
    float span = ridgeAlongWidth ? depth : width;
    double slope = Math.tan(Math.toRadians(pitch));
    switch (type) {
      case FLAT :
        return FLAT_ROOF_THICKNESS;
      case SHED :
        return (float)Math.max(1, span * slope);
      case HIP :
        return (float)Math.max(1, Math.min(width, depth) / 2 * slope);
      default :
        return (float)Math.max(1, span / 2 * slope);
    }
  }

  /**
   * Builds a roof covering a rectangle of <code>width</code> x <code>depth</code>
   * centimeters. Its model is stored in a temporary file saved with the home.
   * @param pitch  angle of the slopes in degrees
   * @param ridgeAlongWidth <code>true</code> if the ridge is parallel to x
   *          (for a shed roof, the high side is then the one with the smallest y)
   * @param color  the RGB color of the roof
   */
  public static AssistantRoofBuilder build(Type type, float width, float depth, float pitch,
                                           boolean ridgeAlongWidth, int color) throws IOException {
    float height = getRoofHeight(type, width, depth, pitch, ridgeAlongWidth);
    // The roof is built with its ridge along x, then turned if needed
    float length = ridgeAlongWidth ? width : depth;
    float span = ridgeAlongWidth ? depth : width;
    float l = length / 2;
    float s = span / 2;
    float [][] vertices;
    int [][] faces;
    if (type == Type.FLAT) {
      vertices = new float [][] {
          {-l, 0, -s}, {l, 0, -s}, {l, 0, s}, {-l, 0, s},
          {-l, height, -s}, {l, height, -s}, {l, height, s}, {-l, height, s}};
      faces = new int [][] {
          {8, 7, 6, 5}, {1, 2, 3, 4}, {4, 3, 7, 8}, {2, 1, 5, 6}, {1, 4, 8, 5}, {3, 2, 6, 7}};
    } else if (type == Type.SHED) {
      vertices = new float [][] {
          {-l, 0, -s}, {l, 0, -s}, {l, 0, s}, {-l, 0, s}, {-l, height, -s}, {l, height, -s}};
      faces = new int [][] {
          {4, 3, 6, 5}, {2, 1, 5, 6}, {1, 4, 5}, {3, 2, 6}, {1, 2, 3, 4}};
    } else {
      // The ridge of a hip roof is shorter at both ends by half the span
      float inset = type == Type.HIP ? Math.min(s, l) : 0;
      vertices = new float [][] {
          {-l, 0, -s}, {l, 0, -s}, {l, 0, s}, {-l, 0, s},
          {-l + inset, height, 0}, {l - inset, height, 0}};
      faces = new int [][] {
          {4, 3, 6, 5}, {2, 1, 5, 6}, {1, 4, 5}, {3, 2, 6}, {1, 2, 3, 4}};
    }

    StringBuilder obj = new StringBuilder("# Roof generated by Sweet Home 3D\nmtllib roof.mtl\ng roof\nusemtl roof\n");
    for (float [] vertex : vertices) {
      float x = vertex [0];
      float z = vertex [2];
      if (!ridgeAlongWidth) {
        // Quarter turn around the vertical axis
        x = -vertex [2];
        z = vertex [0];
      }
      obj.append(String.format(Locale.US, "v %.3f %.3f %.3f\n", x, vertex [1], z));
    }
    for (int [] face : faces) {
      obj.append('f');
      for (int index : face) {
        obj.append(' ').append(index);
      }
      obj.append('\n');
    }
    String mtl = String.format(Locale.US, "newmtl roof\nKa 0.2 0.2 0.2\nKd %.4f %.4f %.4f\nKs 0 0 0\nd 1\nillum 1\n",
        ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);

    File file = OperatingSystem.createTemporaryFile("roof", ".zip");
    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
    try {
      zip.putNextEntry(new ZipEntry("roof.obj"));
      zip.write(obj.toString().getBytes("ISO-8859-1"));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("roof.mtl"));
      zip.write(mtl.getBytes("ISO-8859-1"));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("roof.png"));
      zip.write(createIcon(color));
      zip.closeEntry();
    } finally {
      zip.close();
    }
    String zipUrl = "jar:" + file.toURI().toURL() + "!/";
    return new AssistantRoofBuilder(new TemporaryURLContent(new URL(zipUrl + "roof.obj")),
        new TemporaryURLContent(new URL(zipUrl + "roof.png")), height);
  }

  /**
   * Returns a PNG image of a roof seen from its gable, drawn without the AWT
   * toolkit this package doesn't use.
   */
  private static byte [] createIcon(int color) throws IOException {
    int size = 48;
    // Each row starts with its filter type followed by RGBA pixels
    byte [] pixels = new byte [size * (1 + size * 4)];
    for (int y = 0; y < size; y++) {
      int row = y * (1 + size * 4);
      for (int x = 0; x < size; x++) {
        // A triangle over a low band
        int top = 8 + Math.abs(x - size / 2) * 26 / (size / 2);
        boolean roof = y >= top && y < 36 && x >= 2 && x < size - 2;
        boolean band = y >= 36 && y < 40 && x >= 2 && x < size - 2;
        int pixel = row + 1 + x * 4;
        if (roof || band) {
          float shade = band ? 0.7f : 1;
          pixels [pixel] = (byte)(((color >> 16) & 0xFF) * shade);
          pixels [pixel + 1] = (byte)(((color >> 8) & 0xFF) * shade);
          pixels [pixel + 2] = (byte)((color & 0xFF) * shade);
          pixels [pixel + 3] = (byte)0xFF;
        }
      }
    }
    Deflater deflater = new Deflater();
    deflater.setInput(pixels);
    deflater.finish();
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    byte [] buffer = new byte [4096];
    while (!deflater.finished()) {
      compressed.write(buffer, 0, deflater.deflate(buffer));
    }
    deflater.end();

    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.write(new byte [] {(byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
    writeChunk(png, "IHDR", new byte [] {0, 0, 0, (byte)size, 0, 0, 0, (byte)size, 8, 6, 0, 0, 0});
    writeChunk(png, "IDAT", compressed.toByteArray());
    writeChunk(png, "IEND", new byte [0]);
    return png.toByteArray();
  }

  private static void writeChunk(ByteArrayOutputStream out, String type, byte [] data) throws IOException {
    byte [] typeBytes = type.getBytes("ISO-8859-1");
    writeInt(out, data.length);
    out.write(typeBytes);
    out.write(data);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    writeInt(out, (int)crc.getValue());
  }

  private static void writeInt(ByteArrayOutputStream out, int value) {
    out.write(value >>> 24);
    out.write(value >>> 16);
    out.write(value >>> 8);
    out.write(value);
  }
}
