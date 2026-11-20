/*
 * HomeViewpoints.java 19 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.viewcontroller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * Computes points of view on a home or on some of its items: the standard views
 * (from the top, from a side, from a corner) at the distance where the items are
 * entirely in sight, and views from the inside of a room. Lengths are in
 * centimeters and angles in radians, as in {@link Camera}.
 * @author Sweet Home 3D
 */
public class HomeViewpoints {
  /** The names of the views seen from the outside. */
  public static final List<String> AERIAL_VIEWS = Collections.unmodifiableList(Arrays.asList(
      "top", "south", "north", "east", "west", "south_east", "south_west", "north_east", "north_west"));
  /** The name of the view from the inside of a room, or in front of a piece of furniture. */
  public static final String INSIDE_VIEW = "inside";

  // The yaw and pitch of the aerial views: the south is at the bottom of the plan
  private static final float [][] AERIAL_ANGLES = {
      {(float)Math.PI, (float)Math.PI / 2}, {(float)Math.PI, 0.02f}, {0, 0.02f},
      {(float)Math.PI / 2, 0.02f}, {(float)Math.PI * 3 / 2, 0.02f},
      {(float)Math.PI * 3 / 4, (float)Math.PI / 6}, {(float)Math.PI * 5 / 4, (float)Math.PI / 6},
      {(float)Math.PI / 4, (float)Math.PI / 6}, {(float)Math.PI * 7 / 4, (float)Math.PI / 6}};

  private static final float EYE_HEIGHT = 160;

  /**
   * The location and the direction of a camera.
   */
  public static class Pose {
    private final float   x;
    private final float   y;
    private final float   z;
    private final float   yaw;
    private final float   pitch;
    private final float   fieldOfView;
    private final boolean aerial;

    public Pose(float x, float y, float z, float yaw, float pitch, float fieldOfView, boolean aerial) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
      this.fieldOfView = fieldOfView;
      this.aerial = aerial;
    }

    public float getX() {
      return this.x;
    }

    public float getY() {
      return this.y;
    }

    public float getZ() {
      return this.z;
    }

    public float getYaw() {
      return this.yaw;
    }

    public float getPitch() {
      return this.pitch;
    }

    public float getFieldOfView() {
      return this.fieldOfView;
    }

    /**
     * Returns <code>true</code> if this pose is for the camera of the aerial view,
     * <code>false</code> if it's for the virtual visitor.
     */
    public boolean isAerial() {
      return this.aerial;
    }

    /**
     * Moves the camera of the aerial view or the virtual visitor of <code>home</code>
     * to this pose, and makes it the camera of the home.
     */
    public void applyTo(Home home) {
      Camera camera = this.aerial ? home.getTopCamera() : home.getObserverCamera();
      camera.setX(this.x);
      camera.setY(this.y);
      camera.setZ(this.z);
      camera.setYaw(this.yaw);
      camera.setPitch(this.pitch);
      camera.setFieldOfView(this.fieldOfView);
      if (home.getCamera() != camera) {
        home.setCamera(camera);
      }
    }

    /**
     * Returns a description of this pose in the units of the edit commands.
     */
    public String describe() {
      return describePose(this.x, this.y, this.z, this.yaw, this.pitch, this.fieldOfView, this.aerial);
    }
  }

  private HomeViewpoints() {
  }

  /**
   * Returns <code>true</code> if <code>view</code> is the name of a view of this class.
   */
  public static boolean isView(String view) {
    return view != null
        && (INSIDE_VIEW.equals(normalize(view)) || AERIAL_VIEWS.contains(normalize(view)));
  }

  private static String normalize(String view) {
    return view.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
  }

  /**
   * Returns the description of the camera of <code>home</code>.
   */
  public static String describeCamera(Home home) {
    Camera camera = home.getCamera();
    return describePose(camera.getX(), camera.getY(), camera.getZ(), camera.getYaw(), camera.getPitch(),
        camera.getFieldOfView(), camera == home.getTopCamera());
  }

  private static String describePose(float x, float y, float z, float yaw, float pitch,
                                     float fieldOfView, boolean aerial) {
    // An angle between 0 and 360 degrees
    double yawDegrees = Math.toDegrees(yaw) % 360;
    if (yawDegrees < 0) {
      yawDegrees += 360;
    }
    return (aerial ? "aerial view" : "virtual visitor") + " at x " + Math.round(x) + ", y " + Math.round(y)
        + ", height " + Math.round(z) + " cm, yaw " + Math.round(yawDegrees) % 360 + " degrees"
        + ", pitch " + Math.round(Math.toDegrees(pitch)) + " degrees (positive looks down)"
        + ", field of view " + Math.round(Math.toDegrees(fieldOfView)) + " degrees";
  }

  /**
   * Returns the pose of a camera placed at a point and looking at an other one.
   * @param fieldOfView the field of view in radians
   * @param aerial      <code>true</code> for the camera of the aerial view
   */
  public static Pose lookAt(float x, float y, float z, float lookAtX, float lookAtY, float lookAtZ,
                            float fieldOfView, boolean aerial) {
    double dx = lookAtX - x;
    double dy = lookAtY - y;
    double distance = Math.sqrt(dx * dx + dy * dy);
    // With a yaw of 0 a camera looks toward +y, and turns clockwise in the plan
    float yaw = distance > 0.01 ? (float)Math.atan2(-dx, dy) : 0;
    float pitch = distance > 0.01 || Math.abs(lookAtZ - z) > 0.01
        ? (float)Math.atan2(z - lookAtZ, distance)
        : 0;
    return new Pose(x, y, z, yaw, pitch, fieldOfView, aerial);
  }

  /**
   * Returns the pose of the given view on some items of <code>home</code>.
   * @param view    one of {@link #AERIAL_VIEWS} or {@link #INSIDE_VIEW}
   * @param targets the items to look at, or an empty list for the whole home
   * @param level   the level displayed with the levels under it, or <code>null</code>
   *                if all the levels are displayed
   * @param aspectRatio the width of the picture divided by its height
   * @param fieldOfView the field of view in radians, or 0 for the one of the camera of the home
   * @throws IllegalArgumentException if the view is unknown or can't be computed
   */
  public static Pose getPose(Home home, String view, List<? extends Selectable> targets, Level level,
                             float aspectRatio, float fieldOfView) {
    if (view == null) {
      throw new IllegalArgumentException("No view");
    }
    String viewName = normalize(view);
    if (INSIDE_VIEW.equals(viewName)) {
      return getInsidePose(home, targets, level, fieldOfView);
    }
    int viewIndex = AERIAL_VIEWS.indexOf(viewName);
    if (viewIndex < 0) {
      throw new IllegalArgumentException("Unknown view \"" + view + "\"");
    }
    float [] bounds = getBounds(home, targets, level);
    if (bounds == null) {
      throw new IllegalArgumentException(targets.isEmpty() ? "The home is empty" : "Nothing to look at");
    }
    float yaw = AERIAL_ANGLES [viewIndex][0];
    float pitch = AERIAL_ANGLES [viewIndex][1];
    if (fieldOfView <= 0) {
      fieldOfView = home.getTopCamera().getFieldOfView();
    }
    if (fieldOfView <= 0 || fieldOfView >= Math.PI) {
      fieldOfView = (float)Math.toRadians(63);
    }
    float centerX = (bounds [0] + bounds [3]) / 2;
    float centerY = (bounds [1] + bounds [4]) / 2;
    float centerZ = (bounds [2] + bounds [5]) / 2;
    // The direction the camera looks at, and the two other axes of its frame
    double [] forward = {-Math.sin(yaw) * Math.cos(pitch), Math.cos(yaw) * Math.cos(pitch), -Math.sin(pitch)};
    double [] right = {-Math.cos(yaw), -Math.sin(yaw), 0};
    double [] up = {-Math.sin(yaw) * Math.sin(pitch), Math.cos(yaw) * Math.sin(pitch), Math.cos(pitch)};
    double horizontalTangent = Math.tan(fieldOfView / 2);
    double verticalTangent = horizontalTangent / Math.max(0.1f, aspectRatio);
    // The distance where each corner of the box is in the field of view
    double distance = 0;
    for (int i = 0; i < 8; i++) {
      double dx = ((i & 1) == 0 ? bounds [0] : bounds [3]) - centerX;
      double dy = ((i & 2) == 0 ? bounds [1] : bounds [4]) - centerY;
      double dz = ((i & 4) == 0 ? bounds [2] : bounds [5]) - centerZ;
      double depth = dx * forward [0] + dy * forward [1] + dz * forward [2];
      double horizontal = Math.abs(dx * right [0] + dy * right [1] + dz * right [2]);
      double vertical = Math.abs(dx * up [0] + dy * up [1] + dz * up [2]);
      distance = Math.max(distance, Math.max(horizontal / horizontalTangent, vertical / verticalTangent) - depth);
    }
    distance = Math.max(distance * 1.06, 50);
    float x = (float)(centerX - forward [0] * distance);
    float y = (float)(centerY - forward [1] * distance);
    float z = (float)(centerZ - forward [2] * distance);
    return new Pose(x, y, Math.max(z, 10), yaw, pitch, fieldOfView, true);
  }

  /**
   * Returns the pose of a visitor in a corner of a room, or in front of a piece of furniture.
   */
  private static Pose getInsidePose(Home home, List<? extends Selectable> targets, Level level, float fieldOfView) {
    if (fieldOfView <= 0) {
      fieldOfView = (float)Math.toRadians(85);
    }
    Selectable target = targets.isEmpty() ? null : targets.get(0);
    if (target instanceof Room) {
      Room room = (Room)target;
      float [][] points = room.getPoints();
      float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
      for (float [] point : points) {
        minX = Math.min(minX, point [0]);
        minY = Math.min(minY, point [1]);
        maxX = Math.max(maxX, point [0]);
        maxY = Math.max(maxY, point [1]);
      }
      float centerX = room.getXCenter();
      float centerY = room.getYCenter();
      if (!room.containsPoint(centerX, centerY, 0)) {
        centerX = (minX + maxX) / 2;
        centerY = (minY + maxY) / 2;
      }
      float elevation = room.getLevel() != null ? room.getLevel().getElevation() : 0;
      // The corner of the room the farthest from its center where a visitor can stand
      float [][] corners = {{minX, maxY}, {maxX, maxY}, {maxX, minY}, {minX, minY}};
      float bestX = centerX;
      float bestY = centerY;
      double bestDistance = -1;
      for (float [] corner : corners) {
        for (float inset = 0.12f; inset < 0.5f; inset += 0.08f) {
          float x = corner [0] + (centerX - corner [0]) * inset * 2;
          float y = corner [1] + (centerY - corner [1]) * inset * 2;
          if (room.containsPoint(x, y, 0)) {
            double distance = Math.hypot(x - centerX, y - centerY);
            if (distance > bestDistance) {
              bestDistance = distance;
              bestX = x;
              bestY = y;
            }
            break;
          }
        }
      }
      if (bestDistance < 1) {
        // A room where no corner suits: look at its center from one of its sides
        bestX = centerX;
        bestY = Math.min(maxY, centerY + (maxY - minY) * 0.4f);
        if (Math.abs(bestY - centerY) < 1) {
          bestY = centerY + 50;
        }
      }
      // Look a little down, whatever the size of the room
      double distance = Math.hypot(bestX - centerX, bestY - centerY);
      return lookAt(bestX, bestY, elevation + EYE_HEIGHT, centerX, centerY,
          elevation + EYE_HEIGHT - (float)Math.min(45, distance * 0.2), fieldOfView, false);
    } else if (target instanceof HomePieceOfFurniture) {
      HomePieceOfFurniture piece = (HomePieceOfFurniture)target;
      float elevation = piece.getGroundElevation();
      float size = Math.max(piece.getWidth(), Math.max(piece.getDepth(), piece.getHeight()));
      double distance = Math.max(150, size * 1.8);
      // The front of a piece is along +y when its angle is 0
      float x = (float)(piece.getX() - Math.sin(piece.getAngle()) * distance);
      float y = (float)(piece.getY() + Math.cos(piece.getAngle()) * distance);
      Room room = getRoomAt(home, piece.getX(), piece.getY(), piece.getLevel());
      if (room != null) {
        // A place in the room of the piece, in front of it if possible, as far as wished if possible
        double [] turns = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2,
                           Math.PI * 3 / 4, -Math.PI * 3 / 4, Math.PI};
        boolean found = false;
        for (double ratio = 1; ratio > 0.3 && !found; ratio -= 0.2) {
          for (int i = 0; i < turns.length && !found; i++) {
            double angle = piece.getAngle() + turns [i];
            float candidateX = (float)(piece.getX() - Math.sin(angle) * distance * ratio);
            float candidateY = (float)(piece.getY() + Math.cos(angle) * distance * ratio);
            if (room.containsPoint(candidateX, candidateY, 0)
                && !piece.containsPoint(candidateX, candidateY, 20)) {
              x = candidateX;
              y = candidateY;
              found = true;
            }
          }
        }
      }
      float pieceLevelElevation = piece.getLevel() != null ? piece.getLevel().getElevation() : 0;
      return lookAt(x, y, pieceLevelElevation + EYE_HEIGHT,
          piece.getX(), piece.getY(), elevation + piece.getHeight() / 2, fieldOfView, false);
    } else if (target instanceof Wall) {
      Wall wall = (Wall)target;
      float centerX = (wall.getXStart() + wall.getXEnd()) / 2;
      float centerY = (wall.getYStart() + wall.getYEnd()) / 2;
      double length = Math.max(1, Math.hypot(wall.getXEnd() - wall.getXStart(), wall.getYEnd() - wall.getYStart()));
      double distance = Math.max(200, length * 0.7);
      // In front of a side of the wall which is in a room, as far as the room lets it, or of its left side
      double normalX = (wall.getYEnd() - wall.getYStart()) / length;
      double normalY = -(wall.getXEnd() - wall.getXStart()) / length;
      float x = (float)(centerX + normalX * distance);
      float y = (float)(centerY + normalY * distance);
      boolean found = false;
      for (double ratio = 1; ratio > 0.2 && !found; ratio -= 0.2) {
        for (int side = 1; side >= -1 && !found; side -= 2) {
          float candidateX = (float)(centerX + side * normalX * distance * ratio);
          float candidateY = (float)(centerY + side * normalY * distance * ratio);
          Room room = getRoomAt(home, candidateX, candidateY, wall.getLevel());
          // The room should reach the wall
          if (room != null
              && room.containsPoint((float)(centerX + side * normalX * (wall.getThickness() / 2 + 15)),
                    (float)(centerY + side * normalY * (wall.getThickness() / 2 + 15)), 10)) {
            x = candidateX;
            y = candidateY;
            found = true;
          }
        }
      }
      float elevation = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
      return lookAt(x, y, elevation + EYE_HEIGHT, centerX, centerY, elevation + 120, fieldOfView, false);
    }
    throw new IllegalArgumentException("The view \"inside\" needs the id of a room, a piece of furniture or a wall as target");
  }

  /**
   * Returns the smallest room of a level which contains a point, or <code>null</code>.
   */
  private static Room getRoomAt(Home home, float x, float y, Level level) {
    Room smallestRoom = null;
    for (Room room : home.getRooms()) {
      if (room.getLevel() == level
          && room.containsPoint(x, y, 0)
          && (smallestRoom == null || room.getArea() < smallestRoom.getArea())) {
        smallestRoom = room;
      }
    }
    return smallestRoom;
  }

  /**
   * Returns the box around the given items, or around the displayed items of the home
   * if <code>targets</code> is empty, as <code>{xMin, yMin, zMin, xMax, yMax, zMax}</code>,
   * or <code>null</code> if there's nothing.
   */
  static float [] getBounds(Home home, List<? extends Selectable> targets, Level level) {
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                       -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    boolean all = targets.isEmpty();
    for (Wall wall : home.getWalls()) {
      if (all ? isDisplayed(wall.getLevel(), level) : targets.contains(wall)) {
        float elevation = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
        Float height = wall.getHeight();
        float top = elevation + (height != null ? height.floatValue() : home.getWallHeight());
        if (wall.getHeightAtEnd() != null) {
          top = Math.max(top, elevation + wall.getHeightAtEnd().floatValue());
        }
        add(bounds, wall.getPoints(), elevation, top);
      }
    }
    for (Room room : home.getRooms()) {
      if (all ? isDisplayed(room.getLevel(), level) : targets.contains(room)) {
        float elevation = room.getLevel() != null ? room.getLevel().getElevation() : 0;
        float height = all
            ? 0
            : (room.getLevel() != null ? room.getLevel().getHeight() : home.getWallHeight());
        add(bounds, room.getPoints(), elevation, elevation + height);
      }
    }
    for (int pass = 0; pass < 2; pass++) {
      for (HomePieceOfFurniture piece : home.getFurniture()) {
        // The ground around a house isn't what a view of the whole home has to show: it's kept for a home without anything else
        boolean terrain = "terrain".equals(piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY));
        if (all ? piece.isVisible() && (terrain ? pass == 1 : pass == 0 && isDisplayed(piece.getLevel(), level))
                : pass == 0 && targets.contains(piece)) {
          float elevation = piece.getGroundElevation();
          add(bounds, piece.getPoints(), elevation, elevation + piece.getHeight());
        }
      }
      if (bounds [0] <= bounds [3]) {
        break;
      }
    }
    if (bounds [0] > bounds [3]) {
      return null;
    }
    return bounds;
  }

  private static boolean isDisplayed(Level itemLevel, Level level) {
    if (itemLevel == null) {
      return true;
    } else if (!itemLevel.isViewable()) {
      return false;
    } else {
      return level == null || itemLevel.getElevation() <= level.getElevation();
    }
  }

  private static void add(float [] bounds, float [][] points, float bottom, float top) {
    for (float [] point : points) {
      bounds [0] = Math.min(bounds [0], point [0]);
      bounds [1] = Math.min(bounds [1], point [1]);
      bounds [3] = Math.max(bounds [3], point [0]);
      bounds [4] = Math.max(bounds [4], point [1]);
    }
    if (points.length > 0) {
      bounds [2] = Math.min(bounds [2], bottom);
      bounds [5] = Math.max(bounds [5], top);
    }
  }
}
