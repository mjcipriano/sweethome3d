/*
 * HomeAssistantContext.java 10 June 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * Builds a concise, plain-text description of a {@link Home} that can be given
 * to a language model so the design assistant can answer questions about the
 * current project. Lengths are reported in meters and areas in square meters
 * (Sweet Home 3D stores them in centimeters).
 * @author Sweet Home 3D
 */
public class HomeAssistantContext {
  private HomeAssistantContext() {
    // Utility class
  }

  /**
   * Returns a plain-text brief describing the levels, rooms, walls and furniture
   * of the given <code>home</code>.
   */
  public static String describeHome(Home home) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMaximumFractionDigits(2);
    format.setGroupingUsed(false);
    StringBuilder brief = new StringBuilder();
    brief.append("Sweet Home 3D project summary:\n");

    List<Level> levels = home.getLevels();
    if (!levels.isEmpty()) {
      brief.append("- Levels (").append(levels.size()).append("): ");
      for (int i = 0; i < levels.size(); i++) {
        if (i > 0) {
          brief.append(", ");
        }
        String name = levels.get(i).getName();
        brief.append(name != null && name.length() > 0 ? name : "Level " + (i + 1));
      }
      brief.append('\n');
    } else {
      brief.append("- Levels: 1 (single level)\n");
    }

    List<Room> rooms = home.getRooms();
    float totalRoomArea = 0;
    for (Room room : rooms) {
      totalRoomArea += room.getArea();
    }
    brief.append("- Rooms (").append(rooms.size()).append("), total floor area ")
        .append(format.format(totalRoomArea / 10000)).append(" m2");
    if (!rooms.isEmpty()) {
      brief.append(":\n");
      for (Room room : rooms) {
        String name = room.getName();
        brief.append("    - ").append(name != null && name.length() > 0 ? name : "Unnamed room")
            .append(": ").append(format.format(room.getArea() / 10000)).append(" m2\n");
      }
    } else {
      brief.append('\n');
    }

    Collection<Wall> walls = home.getWalls();
    float totalWallLength = 0;
    for (Wall wall : walls) {
      totalWallLength += wall.getLength();
    }
    brief.append("- Walls (").append(walls.size()).append("), total length ")
        .append(format.format(totalWallLength / 100)).append(" m\n");

    int[] counts = new int[3]; // total, visible, groups
    long[] heaviest = new long[1];
    String[] heaviestName = new String[1];
    countFurniture(home.getFurniture(), counts, heaviest, heaviestName);
    brief.append("- Furniture: ").append(counts[0]).append(" pieces (")
        .append(counts[1]).append(" visible");
    if (counts[2] > 0) {
      brief.append(", ").append(counts[2]).append(" groups");
    }
    brief.append(")\n");
    if (heaviestName[0] != null) {
      brief.append("- Largest 3D model by file size: ").append(heaviestName[0])
          .append(" (").append(format.format(heaviest[0] / 1024.0 / 1024.0)).append(" MB)\n");
    }
    return brief.toString();
  }

  private static void countFurniture(List<HomePieceOfFurniture> furniture, int[] counts,
                                     long[] heaviest, String[] heaviestName) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        counts[2]++;
        countFurniture(((HomeFurnitureGroup)piece).getFurniture(), counts, heaviest, heaviestName);
      } else {
        counts[0]++;
        if (piece.isVisible()) {
          counts[1]++;
        }
        Long modelSize = piece.getModelSize();
        if (modelSize != null && modelSize.longValue() > heaviest[0]) {
          heaviest[0] = modelSize.longValue();
          String name = piece.getName();
          heaviestName[0] = name != null ? name : "Unnamed piece";
        }
      }
    }
  }
}
