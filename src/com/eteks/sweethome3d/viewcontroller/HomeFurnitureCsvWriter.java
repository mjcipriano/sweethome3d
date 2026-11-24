/*
 * HomeFurnitureCsvWriter.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;

/**
 * Writes the furniture of a home as a table a program reads: one line by piece with its id, its place
 * and its size in a unit told by the header, whatever the columns and the unit of the furniture list of the user.
 */
public class HomeFurnitureCsvWriter {
  /** The columns written when none is asked for. */
  public static final List<String> DEFAULT_COLUMNS = Arrays.asList("id", "name", "level", "x", "y", "elevation", "angle",
      "width", "depth", "height", "visible", "tags");
  /** Every column. */
  public static final List<String> COLUMNS = Arrays.asList("id", "name", "level", "x", "y", "elevation", "angle",
      "width", "depth", "height", "visible", "tags", "catalog_id", "description", "movable", "door_or_window", "created_by");

  private static final List<String> LENGTHS = Arrays.asList("x", "y", "elevation", "width", "depth", "height");

  private HomeFurnitureCsvWriter() {
  }

  /**
   * Writes the furniture of <code>home</code> in <code>file</code>.
   * @param columns the names of the columns among {@link #COLUMNS}, or <code>null</code> for the default ones
   * @param units   cm, m, mm, in or ft for the lengths, or <code>null</code> for cm
   * @return what was written
   * @throws IOException if a column or the unit is unknown, or if the file can't be written
   */
  public static String write(Home home, File file, List<?> columns, String units) throws IOException {
    List<String> names = new ArrayList<String>();
    if (columns == null || columns.isEmpty()) {
      names.addAll(DEFAULT_COLUMNS);
    } else {
      for (Object column : columns) {
        String name = String.valueOf(column).trim().toLowerCase(Locale.ENGLISH);
        if (!COLUMNS.contains(name)) {
          throw new IOException("\"" + column + "\" isn't a column: " + COLUMNS);
        }
        names.add(name);
      }
    }
    String unit = units != null ? units.trim().toLowerCase(Locale.ENGLISH) : "cm";
    double scale;
    if (unit.equals("cm")) {
      scale = 1;
    } else if (unit.equals("m")) {
      scale = 0.01;
    } else if (unit.equals("mm")) {
      scale = 10;
    } else if (unit.equals("in") || unit.startsWith("inch")) {
      unit = "in";
      scale = 1 / 2.54;
    } else if (unit.equals("ft") || unit.equals("feet") || unit.equals("foot")) {
      unit = "ft";
      scale = 1 / 30.48;
    } else {
      throw new IOException("\"units\" is cm, m, mm, in or ft");
    }
    HomeItemIds.ensureIds(home);
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      text.append(i > 0 ? "," : "").append(name).append(LENGTHS.contains(name) ? "_" + unit : name.equals("angle") ? "_deg" : "");
    }
    text.append("\r\n");
    int count = 0;
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      for (int i = 0; i < names.size(); i++) {
        String name = names.get(i);
        Object value;
        if (name.equals("id")) {
          value = HomeItemIds.getId(piece);
        } else if (name.equals("name")) {
          value = piece.getName();
        } else if (name.equals("level")) {
          value = piece.getLevel() != null ? piece.getLevel().getName() : "";
        } else if (name.equals("x")) {
          value = number(piece.getX() * scale);
        } else if (name.equals("y")) {
          value = number(piece.getY() * scale);
        } else if (name.equals("elevation")) {
          value = number(piece.getElevation() * scale);
        } else if (name.equals("angle")) {
          value = number(((Math.toDegrees(piece.getAngle()) % 360) + 360) % 360);
        } else if (name.equals("width")) {
          value = number(piece.getWidth() * scale);
        } else if (name.equals("depth")) {
          value = number(piece.getDepth() * scale);
        } else if (name.equals("height")) {
          value = number(piece.getHeight() * scale);
        } else if (name.equals("visible")) {
          value = String.valueOf(piece.isVisible());
        } else if (name.equals("tags")) {
          value = piece.getProperty(HomeItemTags.TAGS_PROPERTY);
        } else if (name.equals("catalog_id")) {
          value = piece.getCatalogId();
        } else if (name.equals("description")) {
          value = piece.getDescription();
        } else if (name.equals("movable")) {
          value = String.valueOf(piece.isMovable());
        } else if (name.equals("door_or_window")) {
          value = String.valueOf(piece.isDoorOrWindow());
        } else {
          value = piece.getProperty(AssistantCommandExecutor.CREATED_BY_PROPERTY);
        }
        String cell = value != null ? value.toString() : "";
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
          cell = '"' + cell.replace("\"", "\"\"") + '"';
        }
        text.append(i > 0 ? "," : "").append(cell);
      }
      text.append("\r\n");
      count++;
    }
    Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
    try {
      writer.write(text.toString());
    } finally {
      writer.close();
    }
    return "Wrote the furniture list to " + file + ": " + count + (count == 1 ? " piece" : " pieces") + ", the columns "
        + names.toString().replace("[", "").replace("]", "") + ", lengths in " + unit + " and angles in degrees. "
        + "\"columns\" chooses among " + COLUMNS.toString().replace("[", "").replace("]", "")
        + ", and \"units\" gives cm, m, mm, in or ft.";
  }

  private static String number(double value) {
    double rounded = Math.round(value * 100) / 100.;
    return rounded == Math.rint(rounded) ? String.valueOf((long)rounded) : String.valueOf(rounded);
  }
}
