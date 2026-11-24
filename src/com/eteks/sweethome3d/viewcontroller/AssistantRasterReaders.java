/*
 * AssistantRasterReaders.java 23 nov. 2026
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads the heights of a GeoTIFF raster and the points of a LAS point cloud.
 */
final class AssistantRasterReaders {
  /**
   * A grid of heights with its place: the coordinates of the middle of its first cell, at its top left.
   */
  static final class Grid {
    int      columns;
    int      rows;
    double   left;
    double   top;
    double   cellWidth;
    double   cellHeight;
    float [] heights;
  }

  /**
   * The points of a cloud: x, y, z and the class of each one.
   */
  static final class Cloud {
    double [] x;
    double [] y;
    double [] z;
    byte []   classes;
    int       count;
    boolean   classified;
  }

  private AssistantRasterReaders() {
  }

  // ---- GeoTIFF ----

  /**
   * Returns the heights of a GeoTIFF with one sample by pixel, not compressed or compressed by Deflate or LZW.
   */
  static Grid readGeoTiff(byte [] data) throws IOException {
    if (data.length < 16 || !(data [0] == 'I' && data [1] == 'I' || data [0] == 'M' && data [1] == 'M')) {
      throw new IOException("the file isn't a TIFF image");
    }
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.order(data [0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    int magic = buffer.getShort(2) & 0xFFFF;
    if (magic == 43) {
      throw new IOException("BigTIFF files aren't read: write a classic one with gdal_translate -co BIGTIFF=NO, or an Esri ASCII grid "
          + "with gdal_translate -of AAIGrid");
    } else if (magic != 42) {
      throw new IOException("the file isn't a TIFF image");
    }
    int directory = buffer.getInt(4);
    if (directory < 8 || directory + 2 > data.length) {
      throw new IOException("the TIFF file is cut");
    }
    Map<Integer, double []> tags = new HashMap<Integer, double []>();
    Map<Integer, String> texts = new HashMap<Integer, String>();
    int count = buffer.getShort(directory) & 0xFFFF;
    int [] sizes = {0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8};
    for (int i = 0; i < count && directory + 2 + (i + 1) * 12 <= data.length; i++) {
      int entry = directory + 2 + i * 12;
      int tag = buffer.getShort(entry) & 0xFFFF;
      int type = buffer.getShort(entry + 2) & 0xFFFF;
      int valueCount = buffer.getInt(entry + 4);
      if (type >= sizes.length || valueCount <= 0 || valueCount > 20000000) {
        continue;
      }
      long size = (long)sizes [type] * valueCount;
      int offset = size <= 4 ? entry + 8 : buffer.getInt(entry + 8);
      if (offset < 0 || offset + size > data.length) {
        continue;
      }
      if (type == 2) {
        texts.put(tag, new String(data, offset, valueCount, "ISO-8859-1").trim());
        continue;
      }
      double [] values = new double [valueCount];
      for (int k = 0; k < valueCount; k++) {
        int at = offset + k * sizes [type];
        values [k] = type == 3 ? buffer.getShort(at) & 0xFFFF : type == 4 ? buffer.getInt(at) & 0xFFFFFFFFL
            : type == 8 ? buffer.getShort(at) : type == 9 ? buffer.getInt(at) : type == 11 ? buffer.getFloat(at)
            : type == 12 ? buffer.getDouble(at) : type == 1 || type == 6 || type == 7 ? data [at] & 0xFF : 0;
      }
      tags.put(tag, values);
    }
    Grid grid = new Grid();
    grid.columns = (int)get(tags, 256, 0);
    grid.rows = (int)get(tags, 257, 0);
    int bits = (int)get(tags, 258, 8);
    int compression = (int)get(tags, 259, 1);
    int samples = (int)get(tags, 277, 1);
    int format = (int)get(tags, 339, 1);
    int predictor = (int)get(tags, 317, 1);
    if (grid.columns < 2 || grid.rows < 2 || (long)grid.columns * grid.rows > 60000000L) {
      throw new IOException("the TIFF image is " + grid.columns + " x " + grid.rows + " pixels: 2 x 2 to 60 millions are read");
    } else if (samples != 1) {
      throw new IOException("the TIFF image has " + samples + " samples by pixel: it is a picture, not a raster of heights");
    } else if (!(bits == 16 || bits == 32 || bits == 64) || bits == 64 && format != 3) {
      throw new IOException("heights of " + bits + " bits aren't read: 16 or 32 bit integers, 32 or 64 bit floats");
    } else if (compression != 1 && compression != 5 && compression != 8 && compression != 32946) {
      throw new IOException("the compression " + compression + " of the TIFF isn't read: write it with gdal_translate -co COMPRESS=DEFLATE "
          + "(or LZW, or NONE)");
    }
    double [] scale = tags.get(33550);
    double [] tiepoint = tags.get(33922);
    if (scale == null || tiepoint == null || scale.length < 2 || tiepoint.length < 6) {
      throw new IOException("the TIFF has no place (no ModelPixelScale and ModelTiepoint tags): it isn't a GeoTIFF this program can place");
    }
    grid.cellWidth = scale [0];
    grid.cellHeight = scale [1];
    // The tie point is a corner of a pixel, unless the raster says its points are the middles
    grid.left = tiepoint [3] - tiepoint [0] * scale [0] + scale [0] / 2;
    grid.top = tiepoint [4] + tiepoint [1] * scale [1] - scale [1] / 2;
    double noData = Double.NaN;
    if (texts.get(42113) != null) {
      try {
        noData = Double.parseDouble(texts.get(42113).replace("\u0000", "").trim());
      } catch (NumberFormatException ex) {
        // No value marks the lack of data
      }
    }
    int bytes = bits / 8;
    grid.heights = new float [grid.columns * grid.rows];
    java.util.Arrays.fill(grid.heights, Float.NaN);
    boolean tiled = tags.get(322) != null && tags.get(324) != null;
    double [] offsets = tiled ? tags.get(324) : tags.get(273);
    double [] byteCounts = tiled ? tags.get(325) : tags.get(279);
    if (offsets == null) {
      throw new IOException("the TIFF has no strip and no tile");
    }
    int blockWidth = tiled ? (int)get(tags, 322, grid.columns) : grid.columns;
    int blockHeight = tiled ? (int)get(tags, 323, grid.rows) : (int)Math.min(grid.rows, get(tags, 278, grid.rows));
    int blocksAcross = tiled ? (grid.columns + blockWidth - 1) / blockWidth : 1;
    for (int block = 0; block < offsets.length; block++) {
      int offset = (int)offsets [block];
      int length = byteCounts != null && block < byteCounts.length ? (int)byteCounts [block]
          : Math.min(data.length - offset, blockWidth * blockHeight * bytes);
      if (offset < 0 || length <= 0 || offset + length > data.length) {
        continue;
      }
      byte [] raw;
      int expected = blockWidth * blockHeight * bytes;
      if (compression == 1) {
        raw = java.util.Arrays.copyOfRange(data, offset, offset + length);
      } else if (compression == 5) {
        raw = decodeLzw(data, offset, length, expected);
      } else {
        raw = inflate(data, offset, length, expected);
      }
      int blockRows = Math.min(blockHeight, raw.length / (blockWidth * bytes));
      if (predictor == 2) {
        // Each sample is the difference from the one before in its row
        ByteBuffer rawBuffer = ByteBuffer.wrap(raw).order(buffer.order());
        for (int row = 0; row < blockRows; row++) {
          for (int column = 1; column < blockWidth; column++) {
            int at = (row * blockWidth + column) * bytes;
            if (bytes == 2) {
              rawBuffer.putShort(at, (short)(rawBuffer.getShort(at) + rawBuffer.getShort(at - 2)));
            } else if (bytes == 4) {
              rawBuffer.putInt(at, rawBuffer.getInt(at) + rawBuffer.getInt(at - 4));
            }
          }
        }
      } else if (predictor == 3) {
        // The bytes of the floats of a row, the most significant ones first, each the difference from the one before
        byte [] row = new byte [blockWidth * bytes];
        for (int r = 0; r < blockRows; r++) {
          int start = r * blockWidth * bytes;
          for (int i = 1; i < row.length; i++) {
            raw [start + i] += raw [start + i - 1];
          }
          System.arraycopy(raw, start, row, 0, row.length);
          for (int column = 0; column < blockWidth; column++) {
            for (int b = 0; b < bytes; b++) {
              int target = buffer.order() == ByteOrder.LITTLE_ENDIAN ? bytes - 1 - b : b;
              raw [start + column * bytes + target] = row [b * blockWidth + column];
            }
          }
        }
      }
      ByteBuffer rawBuffer = ByteBuffer.wrap(raw).order(buffer.order());
      int originColumn = tiled ? (block % blocksAcross) * blockWidth : 0;
      int originRow = tiled ? (block / blocksAcross) * blockHeight : block * blockHeight;
      for (int row = 0; row < blockRows && originRow + row < grid.rows; row++) {
        for (int column = 0; column < blockWidth && originColumn + column < grid.columns; column++) {
          int at = (row * blockWidth + column) * bytes;
          double value = format == 3 ? (bytes == 4 ? rawBuffer.getFloat(at) : rawBuffer.getDouble(at))
              : bytes == 2 ? (format == 2 ? rawBuffer.getShort(at) : rawBuffer.getShort(at) & 0xFFFF)
              : (format == 2 ? rawBuffer.getInt(at) : rawBuffer.getInt(at) & 0xFFFFFFFFL);
          if (value != noData && value > -1E5 && value < 1E5) {
            grid.heights [(originRow + row) * grid.columns + originColumn + column] = (float)value;
          }
        }
      }
    }
    return grid;
  }

  private static double get(Map<Integer, double []> tags, int tag, double defaultValue) {
    return tags.get(tag) != null && tags.get(tag).length > 0 ? tags.get(tag) [0] : defaultValue;
  }

  private static byte [] inflate(byte [] data, int offset, int length, int expected) throws IOException {
    Inflater inflater = new Inflater();
    inflater.setInput(data, offset, length);
    byte [] result = new byte [expected];
    try {
      int size = 0;
      while (size < expected && !inflater.finished()) {
        int read = inflater.inflate(result, size, expected - size);
        if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          break;
        }
        size += read;
      }
    } catch (DataFormatException ex) {
      throw new IOException("a part of the TIFF can't be decompressed");
    } finally {
      inflater.end();
    }
    return result;
  }

  /**
   * Decodes the LZW of TIFF: codes of 9 to 12 bits, the most significant bit first.
   */
  private static byte [] decodeLzw(byte [] data, int offset, int length, int expected) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(expected);
    byte [][] table = new byte [4096][];
    for (int i = 0; i < 256; i++) {
      table [i] = new byte [] {(byte)i};
    }
    int tableSize = 258;
    int codeSize = 9;
    int bitBuffer = 0;
    int bitCount = 0;
    byte [] previous = null;
    int end = offset + length;
    int position = offset;
    while (out.size() < expected) {
      while (bitCount < codeSize && position < end) {
        bitBuffer = (bitBuffer << 8) | (data [position++] & 0xFF);
        bitCount += 8;
      }
      if (bitCount < codeSize) {
        break;
      }
      int code = (bitBuffer >> (bitCount - codeSize)) & ((1 << codeSize) - 1);
      bitCount -= codeSize;
      if (code == 257) {
        break;
      } else if (code == 256) {
        tableSize = 258;
        codeSize = 9;
        previous = null;
        continue;
      }
      byte [] entry;
      if (code < tableSize && table [code] != null) {
        entry = table [code];
      } else if (previous != null) {
        entry = java.util.Arrays.copyOf(previous, previous.length + 1);
        entry [previous.length] = previous [0];
      } else {
        break;
      }
      out.write(entry, 0, entry.length);
      if (previous != null && tableSize < 4096) {
        byte [] added = java.util.Arrays.copyOf(previous, previous.length + 1);
        added [previous.length] = entry [0];
        table [tableSize++] = added;
      }
      previous = entry;
      if (tableSize >= (1 << codeSize) - 1 && codeSize < 12) {
        codeSize++;
      }
    }
    return java.util.Arrays.copyOf(out.toByteArray(), expected);
  }

  // ---- LAS ----

  /**
   * Returns the points of a LAS file that isn't compressed.
   * @param maxPoints the count of points kept, taken evenly in the file
   */
  static Cloud readLas(byte [] data, int maxPoints) throws IOException {
    if (data.length < 227 || data [0] != 'L' || data [1] != 'A' || data [2] != 'S' || data [3] != 'F') {
      throw new IOException("the file isn't a LAS point cloud");
    }
    ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    int versionMinor = data [25] & 0xFF;
    long pointsOffset = buffer.getInt(96) & 0xFFFFFFFFL;
    int format = data [104] & 0xFF;
    int recordLength = buffer.getShort(105) & 0xFFFF;
    long pointCount = buffer.getInt(107) & 0xFFFFFFFFL;
    if (versionMinor >= 4 && data.length >= 255 && pointCount == 0) {
      pointCount = buffer.getLong(247);
    }
    if ((format & 0x80) != 0 || (format & 0x40) != 0) {
      throw new IOException("the point cloud is compressed (LAZ): decompress it with \"laszip -i in.laz -o out.las\" or "
          + "\"pdal translate in.laz out.las\"");
    }
    format &= 0x3F;
    if (format > 10 || recordLength < 20) {
      throw new IOException("the point format " + format + " of the LAS file isn't known");
    }
    double scaleX = buffer.getDouble(131);
    double scaleY = buffer.getDouble(139);
    double scaleZ = buffer.getDouble(147);
    double offsetX = buffer.getDouble(155);
    double offsetY = buffer.getDouble(163);
    double offsetZ = buffer.getDouble(171);
    pointCount = Math.min(pointCount, (data.length - pointsOffset) / recordLength);
    if (pointCount <= 0) {
      throw new IOException("the LAS file has no point");
    }
    int step = (int)Math.max(1, pointCount / Math.max(1, maxPoints));
    int kept = (int)((pointCount + step - 1) / step);
    Cloud cloud = new Cloud();
    cloud.x = new double [kept];
    cloud.y = new double [kept];
    cloud.z = new double [kept];
    cloud.classes = new byte [kept];
    int classOffset = format >= 6 ? 16 : 15;
    for (long i = 0; i < pointCount && cloud.count < kept; i += step) {
      int at = (int)(pointsOffset + i * recordLength);
      cloud.x [cloud.count] = buffer.getInt(at) * scaleX + offsetX;
      cloud.y [cloud.count] = buffer.getInt(at + 4) * scaleY + offsetY;
      cloud.z [cloud.count] = buffer.getInt(at + 8) * scaleZ + offsetZ;
      byte pointClass = (byte)(format >= 6 ? data [at + classOffset] & 0xFF : data [at + classOffset] & 0x1F);
      cloud.classes [cloud.count] = pointClass;
      cloud.classified |= pointClass == 2;
      cloud.count++;
    }
    return cloud;
  }
}
