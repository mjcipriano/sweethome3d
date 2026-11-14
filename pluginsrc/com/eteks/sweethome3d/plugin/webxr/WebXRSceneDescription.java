/*
 * WebXRSceneDescription.java 13 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.plugin.webxr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.viewcontroller.AssistantGeneratedModel;

/**
 * Describes what the WebXR viewer needs to let a visitor walk in a home as in
 * a real one: its levels, its walls and the doors and windows standing in them,
 * and where the visitor starts. The exported 3D model only gives shapes; this
 * description gives the plan, so the viewer can stop the visitor at walls, let
 * them through doorways and put them on the floor of the right level.
 * <p>Lengths are in meters and angles in radians. <code>x</code> and
 * <code>z</code> are the plan axes (<code>z</code> is the plan <code>y</code>),
 * matching the axes of the exported model once scaled from centimeters.
 * @author Sweet Home 3D
 */
public class WebXRSceneDescription {
  private WebXRSceneDescription() {
    // Utility class
  }

  /**
   * Returns the JSON description of the given <code>home</code>.
   */
  public static String toJSON(Home home) {
    // Levels displayed in the 3D view, or one implicit level for a home without levels
    List<Level> levels = new ArrayList<Level>();
    for (Level level : home.getLevels()) {
      if (level.isViewableAndVisible()) {
        levels.add(level);
      }
    }
    boolean implicitLevel = home.getLevels().isEmpty();

    StringBuilder json = new StringBuilder("{\"version\":1,\"levels\":[");
    if (implicitLevel) {
      json.append("{\"name\":\"\",\"elevation\":0,\"height\":")
          .append(meters(home.getWallHeight())).append(",\"floor\":true}");
    } else {
      for (int i = 0; i < levels.size(); i++) {
        Level level = levels.get(i);
        if (i > 0) {
          json.append(',');
        }
        json.append("{\"name\":").append(quote(level.getName()))
            .append(",\"elevation\":").append(meters(level.getElevation()))
            .append(",\"height\":").append(meters(level.getHeight()))
            // Levels without any room (roofs for example) have no floor to walk on
            .append(",\"floor\":").append(hasRoom(home, level)).append('}');
      }
    }

    json.append("],\"walls\":[");
    boolean first = true;
    for (Wall wall : home.getWalls()) {
      int levelIndex = getLevelIndex(wall.getLevel(), levels, implicitLevel);
      if (levelIndex < 0) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      Float arcExtent = wall.getArcExtent();
      json.append("{\"x1\":").append(meters(wall.getXStart()))
          .append(",\"z1\":").append(meters(wall.getYStart()))
          .append(",\"x2\":").append(meters(wall.getXEnd()))
          .append(",\"z2\":").append(meters(wall.getYEnd()))
          .append(",\"thickness\":").append(meters(wall.getThickness()))
          .append(",\"arcExtent\":").append(number(arcExtent != null ? arcExtent.floatValue() : 0))
          .append(",\"level\":").append(levelIndex).append('}');
    }

    json.append("],\"openings\":[");
    first = true;
    List<HomePieceOfFurniture> doorsAndWindows = new ArrayList<HomePieceOfFurniture>();
    collectDoorsAndWindows(home.getFurniture(), doorsAndWindows);
    for (HomePieceOfFurniture piece : doorsAndWindows) {
      int levelIndex = getLevelIndex(piece.getLevel(), levels, implicitLevel);
      if (levelIndex < 0) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"x\":").append(meters(piece.getX()))
          .append(",\"z\":").append(meters(piece.getY()))
          .append(",\"width\":").append(meters(piece.getWidth()))
          .append(",\"depth\":").append(meters(piece.getDepth()))
          .append(",\"angle\":").append(number(piece.getAngle()))
          .append(",\"sill\":").append(meters(piece.getElevation()))
          .append(",\"height\":").append(meters(piece.getHeight()))
          .append(",\"level\":").append(levelIndex).append('}');
    }

    // Stairs the visitor can climb: the ones which describe their flights
    json.append("],\"stairs\":[");
    first = true;
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      String stairs = piece.isVisible() ? getStairs(piece, levels, implicitLevel) : null;
      if (stairs != null) {
        if (!first) {
          json.append(',');
        }
        first = false;
        json.append(stairs);
      }
    }

    // The visitor starts where the virtual visitor of the home stands
    Camera observer = home.getObserverCamera();
    int startLevel = getLevelIndex(home.getSelectedLevel(), levels, implicitLevel);
    if (startLevel < 0) {
      startLevel = 0;
    }
    float levelElevation = implicitLevel || levels.isEmpty() ? 0 : levels.get(startLevel).getElevation();
    float eyeHeight = observer.getZ() - levelElevation;
    if (eyeHeight < 100 || eyeHeight > 200) {
      eyeHeight = 160;
    }
    json.append("],\"start\":{\"x\":").append(meters(observer.getX()))
        .append(",\"z\":").append(meters(observer.getY()))
        .append(",\"yaw\":").append(number(observer.getYaw()))
        .append(",\"eyeHeight\":").append(meters(eyeHeight))
        .append(",\"level\":").append(startLevel).append("}}");
    return json.toString();
  }

  /**
   * Returns the index of <code>level</code> among the exported levels, or -1
   * if the items of this level aren't exported.
   */
  private static int getLevelIndex(Level level, List<Level> levels, boolean implicitLevel) {
    if (implicitLevel) {
      return 0;
    }
    return level != null ? levels.indexOf(level) : -1;
  }

  /**
   * Returns the description of the flights of <code>piece</code> if it's a staircase
   * generated with them, or <code>null</code>.
   */
  private static String getStairs(HomePieceOfFurniture piece, List<Level> levels, boolean implicitLevel) {
    String description = piece.getProperty(AssistantGeneratedModel.STAIRS_FLIGHTS_PROPERTY);
    int levelIndex = getLevelIndex(piece.getLevel(), levels, implicitLevel);
    if (description == null || levelIndex < 0) {
      return null;
    }
    try {
      int separator = description.indexOf('|');
      String [] size = description.substring(0, separator).split(",");
      // The piece may have been resized or mirrored since it was generated
      float scaleX = piece.getWidth() / Float.parseFloat(size [0]);
      float scaleZ = piece.getDepth() / Float.parseFloat(size [1]);
      float scaleY = piece.getHeight() / Float.parseFloat(size [2]);
      int mirror = piece.isModelMirrored() ? -1 : 1;
      StringBuilder flights = new StringBuilder();
      float rise = 0;
      for (String flight : description.substring(separator + 1).split(";")) {
        String [] values = flight.split(",");
        float x1 = Float.parseFloat(values [0]) * scaleX * mirror;
        float x2 = Float.parseFloat(values [2]) * scaleX * mirror;
        float endHeight = Float.parseFloat(values [7]) * scaleY;
        rise = Math.max(rise, endHeight);
        if (flights.length() > 0) {
          flights.append(',');
        }
        flights.append("{\"x1\":").append(meters(Math.min(x1, x2)))
            .append(",\"z1\":").append(meters(Float.parseFloat(values [1]) * scaleZ))
            .append(",\"x2\":").append(meters(Math.max(x1, x2)))
            .append(",\"z2\":").append(meters(Float.parseFloat(values [3]) * scaleZ))
            .append(",\"ux\":").append(Integer.parseInt(values [4]) * mirror)
            .append(",\"uz\":").append(Integer.parseInt(values [5]))
            .append(",\"h0\":").append(meters(Float.parseFloat(values [6]) * scaleY))
            .append(",\"h1\":").append(meters(endHeight)).append('}');
      }
      // The level the top of the stairs leads to
      int topLevel = -1;
      if (!implicitLevel) {
        float top = levels.get(levelIndex).getElevation() + piece.getElevation() + rise;
        for (int i = 0; i < levels.size(); i++) {
          if (i != levelIndex && Math.abs(levels.get(i).getElevation() - top) < 30) {
            topLevel = i;
          }
        }
      }
      return "{\"level\":" + levelIndex + ",\"topLevel\":" + topLevel
          + ",\"x\":" + meters(piece.getX()) + ",\"z\":" + meters(piece.getY())
          + ",\"angle\":" + number(piece.getAngle()) + ",\"base\":" + meters(piece.getElevation())
          + ",\"rise\":" + meters(rise) + ",\"flights\":[" + flights + "]}";
    } catch (RuntimeException ex) {
      // A description edited by hand
      return null;
    }
  }

  private static boolean hasRoom(Home home, Level level) {
    for (Room room : home.getRooms()) {
      if (room.getLevel() == level) {
        return true;
      }
    }
    return false;
  }

  private static void collectDoorsAndWindows(List<HomePieceOfFurniture> furniture,
                                             List<HomePieceOfFurniture> doorsAndWindows) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collectDoorsAndWindows(((HomeFurnitureGroup)piece).getFurniture(), doorsAndWindows);
      } else if (piece.isDoorOrWindow() && piece.isVisible()) {
        doorsAndWindows.add(piece);
      }
    }
  }

  private static String meters(float centimeters) {
    return number(centimeters / 100);
  }

  private static String number(float value) {
    if (Float.isNaN(value) || Float.isInfinite(value)) {
      return "0";
    }
    return String.format(Locale.US, "%.4f", value);
  }

  private static String quote(String text) {
    StringBuilder quoted = new StringBuilder("\"");
    if (text != null) {
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '"' || c == '\\') {
          quoted.append('\\').append(c);
        } else if (c < 0x20 || c > 0x7E) {
          quoted.append(String.format("\\u%04x", (int)c));
        } else {
          quoted.append(c);
        }
      }
    }
    return quoted.append('"').toString();
  }
}
