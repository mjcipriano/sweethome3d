/*
 * AssistantRailingCommands.java 25 nov. 2026
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;

/**
 * The commands of the design assistant that build railings: <code>add_railing</code>
 * and <code>modify_railing</code>. A railing runs along a line of points, each one with
 * the height of the floor there, so it follows the pitch of stairs. Its balusters are
 * repeated along it, whatever its length.
 * @author Sweet Home 3D contributors
 */
class AssistantRailingCommands {
  /** Property of a railing: its description, at JSON format. */
  static final String RAILING_PROPERTY = "com.eteks.sweethome3d.railing";

  static final List<String> STYLES = Arrays.asList("square", "turned_balusters", "cable", "glass");
  private static final List<String> KEYS = Arrays.asList("action", "id", "railing", "points", "closed", "level", "height", "style",
      "spacing", "top_rail", "bottom_rail", "color", "rail_color", "post_spacing", "post_size", "base", "stairs", "side",
      "name", "rebuild");
  private static final int   MAX_POINTS = 200;
  private static final float MAX_LENGTH = 20000;

  private final Home                     home;
  private final AssistantCommandExecutor executor;

  AssistantRailingCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.executor = executor;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("add_railing".equals(action) || "add_balustrade".equals(action)) {
        addRailing(command, tally, notes);
      } else if ("modify_railing".equals(action)) {
        modifyRailing(command, tally, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      if (!"see above".equals(ex.getMessage())) {
        notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
      }
    }
    return true;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"add_railing\",\"points\":[[x,y],[x,y],...],\"height\":91,\"style\":\"square|turned_balusters|cable|glass\","
        + "\"spacing\":12,\"top_rail\":\"square|round|flat|none\",\"color\":\"#RRGGBB\"}  "
        + "(a railing along a line of points, for an overlook, a balcony, a landing or a deck: posts at each point and every "
        + "\"post_spacing\" cm, a top rail of \"rail_color\", and balusters repeated every \"spacing\" cm at most, whatever the length, "
        + "or cables, or glass panels. A point [x,y,z] gives the height z of the floor under the railing, measured from the floor of "
        + "the level: the rails then follow the pitch between two points while the balusters stay upright; \"base\" is the height "
        + "of the first point. \"stairs\":\"F3\" with \"side\":\"left|right\" follows that side of stairs made by add_stairs from their "
        + "foot to their top, around their landing, then goes on along the \"points\" given, as one piece with one top rail: "
        + "build these stairs without \"railing\". \"closed\":true joins the last point to the first one. The reply gives the count "
        + "of balusters, the space between them and the triangles of the model)\n"
        + "  {\"action\":\"modify_railing\",\"id\":\"F9\",\"style\":\"glass\",\"height\":107}  "
        + "(builds a railing again with the options given, the other ones being kept; a null value removes an option)\n";
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  // ---- The model ----

  /**
   * The railing built from a description.
   */
  static final class Result {
    AssistantMeshBuilder mesh;
    float                length;
    int                  balusterCount;
    int                  postCount;
    int                  panelCount;
    int                  cableCount;
    String               style;
    float                height;
    float                clearGap;
    boolean              sloped;
  }

  private static float number(Map<?, ?> spec, String key, float defaultValue) {
    return AssistantFurnitureBuilder.getNumber(spec, key, defaultValue);
  }

  /**
   * Returns the points {x, y, z} of the line a railing follows, z being the height of the floor
   * under it above the floor of its level. A point given without height takes the one of the
   * point before it, the first one the parameter <code>base</code>.
   */
  static List<float []> getPath(Map<?, ?> spec) throws IOException {
    List<float []> path = new ArrayList<float []>();
    float base = number(spec, "base", 0);
    if (spec.get("points") instanceof List) {
      for (Object item : (List<?>)spec.get("points")) {
        if (!(item instanceof List) || ((List<?>)item).size() < 2) {
          throw new IOException("each point is [x,y], or [x,y,z] with z the height of the floor there");
        }
        List<?> coordinates = (List<?>)item;
        float [] point = new float [3];
        for (int i = 0; i < 3; i++) {
          if (i < coordinates.size()) {
            if (!(coordinates.get(i) instanceof Number)) {
              throw new IOException("each point is [x,y], or [x,y,z] with z the height of the floor there");
            }
            point [i] = ((Number)coordinates.get(i)).floatValue();
          } else {
            point [i] = path.isEmpty() ? base : path.get(path.size() - 1) [2];
          }
        }
        if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])
            || !AssistantCommandExecutor.sizeSane(point [2])) {
          throw new IOException("points out of range");
        }
        if (path.isEmpty() || Math.hypot(point [0] - path.get(path.size() - 1) [0], point [1] - path.get(path.size() - 1) [1]) > 0.5) {
          path.add(point);
        }
      }
    }
    return path;
  }

  /**
   * Builds the railing described by <code>spec</code>, where it stands.
   */
  static Result build(Map<?, ?> spec) throws IOException {
    List<float []> path = getPath(spec);
    if (path.size() < 2 || path.size() > MAX_POINTS) {
      throw new IOException("\"points\" must list the 2 points at least of the line the railing follows, like [[0,0],[400,0]]");
    }
    boolean closed = Boolean.TRUE.equals(spec.get("closed")) && path.size() > 2;
    if (closed) {
      path.add(path.get(0).clone());
    }
    Result result = new Result();
    String style = spec.get("style") != null ? spec.get("style").toString().trim().toLowerCase(Locale.ENGLISH).replace(' ', '_') : "square";
    if ("turned".equals(style) || "balusters".equals(style)) {
      style = "turned_balusters";
    } else if ("square_balusters".equals(style)) {
      style = "square";
    }
    if (!STYLES.contains(style)) {
      throw new IOException("\"style\" is one of " + STYLES);
    }
    result.style = style;
    float height = number(spec, "height", 91);
    float spacing = number(spec, "spacing", "cable".equals(style) ? 8 : 12);
    float postSize = number(spec, "post_size", "cable".equals(style) || "glass".equals(style) ? 5 : 7);
    float postSpacing = number(spec, "post_spacing", "square".equals(style) || "turned_balusters".equals(style) ? 180 : 120);
    if (!(height >= 20) || height > 300 || !(spacing >= 3) || spacing > 100 || !(postSize >= 2) || postSize > 30
        || !(postSpacing >= 30) || postSpacing > 1000) {
      throw new IOException("\"height\" is between 20 and 300 cm, \"spacing\" between 3 and 100 cm, \"post_size\" between "
          + "2 and 30 cm and \"post_spacing\" between 30 and 1000 cm");
    }
    result.height = height;
    Integer color = spec.get("color") != null ? AssistantCommandExecutor.parseColor(spec.get("color").toString()) : null;
    if (spec.get("color") != null && color == null) {
      throw new IOException("\"" + spec.get("color") + "\" isn't a color: give \"#RRGGBB\"");
    }
    Integer railColor = spec.get("rail_color") != null ? AssistantCommandExecutor.parseColor(spec.get("rail_color").toString()) : null;
    if (spec.get("rail_color") != null && railColor == null) {
      throw new IOException("\"" + spec.get("rail_color") + "\" isn't a color: give \"#RRGGBB\"");
    }
    int mainColor = color != null ? color.intValue()
        : "cable".equals(style) || "glass".equals(style) ? 0x3C3F43 : 0xF2F0EB;
    String topRail = spec.get("top_rail") != null ? spec.get("top_rail").toString().trim().toLowerCase(Locale.ENGLISH) : "";
    boolean noTopRail = "none".equals(topRail) || "false".equals(topRail);
    if (topRail.length() > 0 && !noTopRail && !"round".equals(topRail) && !"square".equals(topRail) && !"flat".equals(topRail)
        && !"true".equals(topRail)) {
      throw new IOException("\"top_rail\" is round, square, flat or none");
    }
    if (noTopRail && !"glass".equals(style)) {
      throw new IOException("only a glass railing stands without top rail");
    }
    boolean bottomRail = !Boolean.FALSE.equals(spec.get("bottom_rail")) && !"cable".equals(style);

    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material material = mesh.getMaterial(mainColor);
    AssistantMeshBuilder.Material railMaterial = mesh.getMaterial(railColor != null ? railColor.intValue()
        : "square".equals(style) || "turned_balusters".equals(style) ? 0x6B4A2F : mainColor);
    AssistantMeshBuilder.Material cableMaterial = mesh.getMaterial(0xA9ACB0);
    AssistantMeshBuilder.Material glassMaterial = mesh.getMaterial(0xBFD8E0, 0.3f);
    // The top rail: its width, its thickness, and the height of its top above the floor
    float railWidth = "flat".equals(topRail) ? 9 : "round".equals(topRail) ? 5 : 6;
    float railThickness = "flat".equals(topRail) ? 3 : "round".equals(topRail) ? 5 : 4;
    float bottomGap = 8;
    float bottomThickness = 3;
    float infillBottom = bottomRail ? bottomGap + bottomThickness : "cable".equals(style) ? 0 : 2;
    float infillTop = noTopRail ? height : height - railThickness;

    // The posts: at each point of the line, and between them where a run is long
    List<float []> posts = new ArrayList<float []>();
    for (int i = 0; i + 1 < path.size(); i++) {
      float [] start = path.get(i);
      float [] end = path.get(i + 1);
      double run = Math.hypot(end [0] - start [0], end [1] - start [1]);
      result.length += run;
      if (Math.abs(end [2] - start [2]) > 0.5f) {
        result.sloped = true;
      }
      int parts = Math.max(1, (int)Math.ceil(run / postSpacing - 1E-3));
      for (int j = 0; j < parts; j++) {
        float t = (float)j / parts;
        posts.add(new float [] {start [0] + (end [0] - start [0]) * t, start [1] + (end [1] - start [1]) * t,
                                start [2] + (end [2] - start [2]) * t});
      }
    }
    if (result.length > MAX_LENGTH) {
      throw new IOException("a railing is " + format(MAX_LENGTH / 100) + " m long at most");
    }
    if (!closed) {
      posts.add(path.get(path.size() - 1));
    } else {
      posts.add(posts.get(0));
    }
    result.postCount = closed ? posts.size() - 1 : posts.size();
    for (int i = 0; i < result.postCount; i++) {
      float [] post = posts.get(i);
      float half = postSize / 2;
      mesh.addBox(post [0] - half, post [2], post [1] - half, post [0] + half, post [2] + height + (noTopRail ? 0 : 1.5f),
          post [1] + half, material);
    }
    // Between two posts: the rails and what fills the railing
    for (int i = 0; i + 1 < posts.size(); i++) {
      float [] start = posts.get(i);
      float [] end = posts.get(i + 1);
      double run = Math.hypot(end [0] - start [0], end [1] - start [1]);
      if (run < 0.5) {
        continue;
      }
      double ux = (end [0] - start [0]) / run;
      double uy = (end [1] - start [1]) / run;
      double slope = (end [2] - start [2]) / run;
      if (!noTopRail) {
        if ("round".equals(topRail)) {
          addTube(mesh, new float [] {start [0], start [2] + height - railThickness / 2, start [1]},
              new float [] {end [0], end [2] + height - railThickness / 2, end [1]}, railThickness / 2, 8, railMaterial);
        } else {
          mesh.addBar(new float [] {start [0], start [2] + height, start [1]}, new float [] {end [0], end [2] + height, end [1]},
              railWidth, railThickness, railMaterial);
        }
      }
      if (bottomRail) {
        mesh.addBar(new float [] {start [0], start [2] + bottomGap + bottomThickness, start [1]},
            new float [] {end [0], end [2] + bottomGap + bottomThickness, end [1]}, 4, bottomThickness, material);
      }
      // The clear run between the posts
      double clear = run - postSize;
      if (clear <= 1) {
        continue;
      }
      if ("glass".equals(style)) {
        double gap = 2;
        float [] panelStart = getPoint(start, ux, uy, slope, postSize / 2 + gap);
        float [] panelEnd = getPoint(start, ux, uy, slope, run - postSize / 2 - gap);
        float px = (float)(-uy * 0.5);
        float pz = (float)(ux * 0.5);
        float top = noTopRail ? height - 2 : infillTop - 2;
        float [][] section = {
            {panelStart [0] - px, panelStart [2] + infillBottom + 2, panelStart [1] - pz},
            {panelStart [0] + px, panelStart [2] + infillBottom + 2, panelStart [1] + pz},
            {panelStart [0] + px, panelStart [2] + top, panelStart [1] + pz},
            {panelStart [0] - px, panelStart [2] + top, panelStart [1] - pz}};
        mesh.addSolid(section, new float [] {panelEnd [0] - panelStart [0], panelEnd [2] - panelStart [2],
            panelEnd [1] - panelStart [1]}, glassMaterial);
        result.panelCount++;
      } else if ("cable".equals(style)) {
        int cables = Math.max(1, (int)Math.floor((infillTop - 4) / spacing));
        for (int j = 1; j <= cables; j++) {
          float cableHeight = (infillTop - 2) * j / (cables + 1) + 1;
          addTube(mesh, new float [] {start [0], start [2] + cableHeight, start [1]},
              new float [] {end [0], end [2] + cableHeight, end [1]}, 0.35f, 4, cableMaterial);
        }
        result.cableCount = cables;
        result.clearGap = (infillTop - 2) / (cables + 1) - 0.7f;
      } else {
        float balusterSize = "turned_balusters".equals(style) ? 4.4f : 3;
        // As many balusters as needed for the space between two of them to stay under the spacing asked for
        int count = Math.max(0, (int)Math.ceil((clear + balusterSize) / spacing - 1E-3) - 1);
        double step = clear / (count + 1);
        result.clearGap = Math.max(result.clearGap, (float)(step - balusterSize));
        for (int j = 1; j <= count; j++) {
          float [] foot = getPoint(start, ux, uy, slope, postSize / 2 + step * j);
          if ("turned_balusters".equals(style)) {
            addTurnedBaluster(mesh, foot [0], foot [1], foot [2] + infillBottom, foot [2] + infillTop, material);
          } else {
            float half = balusterSize / 2;
            mesh.addBox(foot [0] - half, foot [2] + infillBottom - (float)Math.abs(slope) * half, foot [1] - half,
                foot [0] + half, foot [2] + infillTop + (float)Math.abs(slope) * half, foot [1] + half, material);
          }
          result.balusterCount++;
        }
      }
    }
    result.mesh = mesh;
    return result;
  }

  private static float [] getPoint(float [] start, double ux, double uy, double slope, double distance) {
    return new float [] {(float)(start [0] + ux * distance), (float)(start [1] + uy * distance),
                         (float)(start [2] + slope * distance)};
  }

  /**
   * Adds a bar of round section between two points (x, up, front).
   */
  private static void addTube(AssistantMeshBuilder mesh, float [] start, float [] end, float radius, int sides,
                              AssistantMeshBuilder.Material material) {
    double dx = end [0] - start [0];
    double dy = end [1] - start [1];
    double dz = end [2] - start [2];
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length < 1E-3) {
      return;
    }
    // Two directions perpendicular to the bar
    double ax;
    double ay;
    double az;
    double horizontal = Math.hypot(dx, dz);
    if (horizontal < 1E-4) {
      ax = 1;
      ay = 0;
      az = 0;
    } else {
      ax = -dz / horizontal;
      ay = 0;
      az = dx / horizontal;
    }
    double bx = (dy * az - dz * ay) / length;
    double by = (dz * ax - dx * az) / length;
    double bz = (dx * ay - dy * ax) / length;
    float [][] section = new float [sides][];
    for (int i = 0; i < sides; i++) {
      double angle = 2 * Math.PI * i / sides;
      double cos = Math.cos(angle) * radius;
      double sin = Math.sin(angle) * radius;
      section [i] = new float [] {(float)(start [0] + ax * cos + bx * sin), (float)(start [1] + ay * cos + by * sin),
                                  (float)(start [2] + az * cos + bz * sin)};
    }
    mesh.addSolid(section, new float [] {(float)dx, (float)dy, (float)dz}, material);
  }

  // The radius of a turned baluster along its height, from its foot (0) to its top (1)
  private static final float [][] BALUSTER_PROFILE = {
      {0, 2.2f}, {0.09f, 2.2f}, {0.12f, 1.3f}, {0.2f, 2.1f}, {0.32f, 1.6f}, {0.6f, 1.1f}, {0.82f, 1.5f},
      {0.87f, 1.1f}, {0.91f, 2.2f}, {1, 2.2f}};

  /**
   * Adds a baluster turned on a lathe, standing at (x, y) from <code>bottom</code> to <code>top</code>.
   */
  private static void addTurnedBaluster(AssistantMeshBuilder mesh, float x, float y, float bottom, float top,
                                        AssistantMeshBuilder.Material material) {
    int sides = 6;
    float [][][] rings = new float [BALUSTER_PROFILE.length][sides][];
    for (int i = 0; i < BALUSTER_PROFILE.length; i++) {
      float height = bottom + (top - bottom) * BALUSTER_PROFILE [i][0];
      for (int j = 0; j < sides; j++) {
        double angle = 2 * Math.PI * j / sides;
        rings [i][j] = new float [] {(float)(x + Math.cos(angle) * BALUSTER_PROFILE [i][1]), height,
                                     (float)(y + Math.sin(angle) * BALUSTER_PROFILE [i][1])};
      }
    }
    for (int i = 0; i + 1 < rings.length; i++) {
      float [] center = {x, (rings [i][0][1] + rings [i + 1][0][1]) / 2, y};
      for (int j = 0; j < sides; j++) {
        int next = (j + 1) % sides;
        mesh.addOutward(new float [][] {rings [i][j], rings [i][next], rings [i + 1][next], rings [i + 1][j]}, center, material);
      }
    }
  }

  // ---- The line along stairs ----

  /**
   * Returns the points {x, y, z} of the line that follows a side of the flights of <code>stairs</code>
   * from their foot to their top, z being measured from the floor of their level.
   */
  private List<float []> getStairsPath(HomePieceOfFurniture stairs, boolean leftSide) throws IOException {
    String description = stairs.getProperty(AssistantGeneratedModel.STAIRS_FLIGHTS_PROPERTY);
    if (description == null || description.indexOf('|') < 0) {
      throw new IOException(this.executor.getId(stairs) + " isn't a staircase made by add_stairs");
    }
    List<float []> flights = new ArrayList<float []>();
    for (String text : description.substring(description.indexOf('|') + 1).split(";")) {
      String [] values = text.split(",");
      if (values.length >= 8) {
        float [] flight = new float [8];
        try {
          for (int i = 0; i < 8; i++) {
            flight [i] = Float.parseFloat(values [i].trim());
          }
        } catch (NumberFormatException ex) {
          continue;
        }
        flights.add(flight);
      }
    }
    float inset = 3.5f;
    List<float []> path = new ArrayList<float []>();
    for (int i = 0; i < flights.size(); i++) {
      float [] flight = flights.get(i);
      float x1 = Math.min(flight [0], flight [2]) + inset;
      float x2 = Math.max(flight [0], flight [2]) - inset;
      float y1 = Math.min(flight [1], flight [3]) + inset;
      float y2 = Math.max(flight [1], flight [3]) - inset;
      float ux = flight [4];
      float uy = flight [5];
      if (ux == 0 && uy == 0) {
        // A landing, walked around on the side of the railing between the flights it joins
        if (!path.isEmpty() && i + 1 < flights.size()) {
          float [] from = path.get(path.size() - 1);
          float [] to = getFlightEnds(flights.get(i + 1), leftSide, inset) [0];
          addLandingPath(path, from, to, x1 - inset, y1 - inset, x2 + inset, y2 + inset, inset, flight [6], leftSide);
        }
        continue;
      }
      float [][] ends = getFlightEnds(flight, leftSide, inset);
      for (float [] end : ends) {
        if (path.isEmpty() || Math.hypot(end [0] - path.get(path.size() - 1) [0], end [1] - path.get(path.size() - 1) [1]) > 1
            || Math.abs(end [2] - path.get(path.size() - 1) [2]) > 1) {
          path.add(end);
        }
      }
    }
    if (path.size() < 2) {
      throw new IOException("the flights of " + this.executor.getId(stairs) + " are spiral or unknown: give the railing "
          + "its \"points\" as [x,y,z], z being the height of the steps there");
    }
    // From the frame of the piece to the plan
    double cos = Math.cos(stairs.getAngle());
    double sin = Math.sin(stairs.getAngle());
    List<float []> planPath = new ArrayList<float []>();
    for (float [] point : path) {
      planPath.add(new float [] {(float)(stairs.getX() + point [0] * cos - point [1] * sin),
                                 (float)(stairs.getY() + point [0] * sin + point [1] * cos),
                                 stairs.getElevation() + point [2]});
    }
    return planPath;
  }

  /**
   * Returns the foot and the top {x, y, z} of the side of a flight.
   */
  private static float [][] getFlightEnds(float [] flight, boolean leftSide, float inset) {
    float x1 = Math.min(flight [0], flight [2]);
    float x2 = Math.max(flight [0], flight [2]);
    float y1 = Math.min(flight [1], flight [3]);
    float y2 = Math.max(flight [1], flight [3]);
    float ux = flight [4];
    float uy = flight [5];
    // The left of who climbs, in a plan where y goes down
    float lx = leftSide ? uy : -uy;
    float ly = leftSide ? -ux : ux;
    float [] start = new float [3];
    float [] end = new float [3];
    if (ux != 0) {
      start [0] = ux > 0 ? x1 : x2;
      end [0] = ux > 0 ? x2 : x1;
      start [1] = end [1] = ly > 0 ? y2 - inset : y1 + inset;
    } else {
      start [1] = uy > 0 ? y1 : y2;
      end [1] = uy > 0 ? y2 : y1;
      start [0] = end [0] = lx > 0 ? x2 - inset : x1 + inset;
    }
    start [2] = flight [6];
    end [2] = flight [7];
    return new float [][] {start, end};
  }

  /**
   * Adds the points that go around a landing from the top of a flight to the foot of the next one,
   * along the border of the landing which is on the side of the railing.
   */
  private static void addLandingPath(List<float []> path, float [] from, float [] to,
                                     float x1, float y1, float x2, float y2, float inset, float height, boolean leftSide) {
    x1 += inset;
    y1 += inset;
    x2 -= inset;
    y2 -= inset;
    if (x2 - x1 < 1 || y2 - y1 < 1) {
      return;
    }
    // The corners in the order where the landing is at the right of who goes around it
    float [][] corners = {{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}};
    float perimeter = 2 * (x2 - x1 + y2 - y1);
    float fromPlace = getPlace(from, x1, y1, x2, y2);
    float toPlace = getPlace(to, x1, y1, x2, y2);
    float [] cornerPlaces = {0, x2 - x1, x2 - x1 + y2 - y1, 2 * (x2 - x1) + y2 - y1};
    // A railing at the left of who climbs has the landing at its right
    float distance = leftSide
        ? (toPlace - fromPlace + perimeter) % perimeter
        : (fromPlace - toPlace + perimeter) % perimeter;
    if (distance < 1 || distance > perimeter * 0.8f) {
      return;
    }
    List<float []> crossed = new ArrayList<float []>();
    for (int i = 0; i < 4; i++) {
      float cornerDistance = leftSide
          ? (cornerPlaces [i] - fromPlace + perimeter) % perimeter
          : (fromPlace - cornerPlaces [i] + perimeter) % perimeter;
      if (cornerDistance > 0.5f && cornerDistance < distance - 0.5f) {
        crossed.add(new float [] {corners [i][0], corners [i][1], height, cornerDistance});
      }
    }
    java.util.Collections.sort(crossed, new java.util.Comparator<float []>() {
        public int compare(float [] corner1, float [] corner2) {
          return Float.compare(corner1 [3], corner2 [3]);
        }
      });
    for (float [] corner : crossed) {
      path.add(new float [] {corner [0], corner [1], height});
    }
  }

  /**
   * Returns the distance along the border of a rectangle, from its first corner and
   * through (x2, y1), of the point of the border which is the closest to <code>point</code>.
   */
  private static float getPlace(float [] point, float x1, float y1, float x2, float y2) {
    float x = Math.max(x1, Math.min(x2, point [0]));
    float y = Math.max(y1, Math.min(y2, point [1]));
    float top = Math.abs(y - y1);
    float right = Math.abs(x - x2);
    float bottom = Math.abs(y - y2);
    float left = Math.abs(x - x1);
    float nearest = Math.min(Math.min(top, bottom), Math.min(left, right));
    if (nearest == top) {
      return x - x1;
    } else if (nearest == right) {
      return x2 - x1 + y - y1;
    } else if (nearest == bottom) {
      return x2 - x1 + y2 - y1 + x2 - x;
    } else {
      return 2 * (x2 - x1) + y2 - y1 + y2 - y;
    }
  }

  // ---- Commands ----

  private HomePieceOfFurniture getPiece(String token) {
    if (token != null) {
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (token.trim().equalsIgnoreCase(this.executor.getId(piece))) {
          return piece;
        }
      }
    }
    return null;
  }

  /**
   * Replaces the stairs of a description by the points along them, and returns their level.
   */
  private Level resolveStairs(Map<String, Object> spec, List<String> notes) throws IOException {
    if (spec.get("stairs") == null) {
      return null;
    }
    HomePieceOfFurniture stairs = getPiece(spec.get("stairs").toString());
    if (stairs == null) {
      throw new IOException("no piece has the id " + spec.get("stairs"));
    }
    String side = spec.get("side") != null ? spec.get("side").toString().trim().toLowerCase(Locale.ENGLISH) : "left";
    if (!"left".equals(side) && !"right".equals(side)) {
      throw new IOException("\"side\" is left or right, for who climbs the stairs: add a railing for each side");
    }
    List<Object> points = new ArrayList<Object>();
    for (float [] point : getStairsPath(stairs, "left".equals(side))) {
      points.add(Arrays.asList(new Object [] {Double.valueOf(round(point [0])), Double.valueOf(round(point [1])),
          Double.valueOf(round(point [2]))}));
    }
    // The points given go on from the top of the stairs
    boolean goesOn = spec.get("points") instanceof List && !((List<?>)spec.get("points")).isEmpty();
    if (goesOn) {
      points.addAll((List<?>)spec.get("points"));
    }
    spec.put("points", points);
    spec.remove("stairs");
    spec.remove("side");
    notes.add("The railing follows the " + side + " side of the stairs " + this.executor.getId(stairs)
        + " for who climbs them, from their foot to their top"
        + (goesOn ? ", then the points given, as one piece with one top rail" : "") + ". It doesn't move with the stairs");
    return stairs.getLevel();
  }

  private static double round(float value) {
    return Math.round(value * 100) / 100.;
  }

  private void addRailing(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Map<String, Object> spec = new LinkedHashMap<String, Object>(command.getParameters());
    for (String key : new String [] {"action", "level", "name", "id", "railing", "rebuild"}) {
      spec.remove(key);
    }
    Level level = resolveStairs(spec, notes);
    if (level == null || command.getString("level") != null) {
      level = this.executor.getShapeCommands().getLevel(command, "add_railing", notes);
    }
    Result result = build(spec);
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : "Railing";
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    HomePieceOfFurniture piece = shapeCommands.createPiece(name, result.mesh.store("railing"), level, true, "railing");
    piece.setProperty(RAILING_PROPERTY, AssistantShapeCommands.toJson(spec));
    shapeCommands.add(piece);
    tally [0]++;
    notes.add("Added the railing " + describe(piece, result));
    warnUnknownKeys("add_railing", command.getParameters(), notes);
  }

  private void modifyRailing(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String token = command.getString("id") != null ? command.getString("id") : command.getString("railing");
    HomePieceOfFurniture piece = getPiece(token);
    if (piece == null || piece.getProperty(RAILING_PROPERTY) == null) {
      throw new IOException("give the id of a railing made by add_railing with \"id\"");
    }
    Map<String, Object> spec = new LinkedHashMap<String, Object>();
    Object parsed = JSONParser.parse(piece.getProperty(RAILING_PROPERTY));
    if (parsed instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)parsed).entrySet()) {
        spec.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    if (command.has("stairs") && !command.has("points")) {
      // Along the stairs only, not along the points the railing had
      spec.remove("points");
    }
    int changes = 0;
    for (Map.Entry<String, Object> entry : command.getParameters().entrySet()) {
      String key = entry.getKey();
      if (key.equals("action") || key.equals("id") || key.equals("railing") || key.equals("name") || key.equals("level")
          || key.equals("rebuild")) {
        continue;
      }
      if (entry.getValue() == null) {
        spec.remove(key);
      } else {
        spec.put(key, entry.getValue());
      }
      changes++;
    }
    if (changes == 0 && command.getString("name") == null) {
      throw new IOException("give what changes: points, height, style, spacing, top_rail, color... A null value removes an option");
    }
    resolveStairs(spec, notes);
    Result result = build(spec);
    AssistantGeneratedModel model = result.mesh.store("railing");
    float [] bounds = model.getBounds();
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put(RAILING_PROPERTY, AssistantShapeCommands.toJson(spec));
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : null;
    this.executor.getShapeCommands().replaceModel(piece, model, (bounds [0] + bounds [3]) / 2, (bounds [2] + bounds [5]) / 2,
        bounds [1], model.getWidth(), model.getDepth(), model.getHeight(), name, properties);
    tally [4]++;
    notes.add("Modified the railing " + describe(piece, result));
    warnUnknownKeys("modify_railing", command.getParameters(), notes);
  }

  private String describe(HomePieceOfFurniture piece, Result result) {
    StringBuilder text = new StringBuilder(this.executor.getId(piece) + " \"" + piece.getName() + "\": "
        + format(result.length) + " cm long, " + format(result.height) + " cm high, style " + result.style + ", "
        + result.postCount + " posts");
    if (result.balusterCount > 0) {
      text.append(", ").append(result.balusterCount).append(" balusters ").append(format(result.clearGap))
          .append(" cm apart at most");
    } else if (result.cableCount > 0) {
      text.append(", ").append(result.cableCount).append(" cables ").append(format(result.clearGap)).append(" cm apart");
    } else if (result.panelCount > 0) {
      text.append(", ").append(result.panelCount).append(" glass panels");
    }
    if (result.sloped) {
      text.append(", following the heights of its points");
    }
    text.append(", ").append(result.mesh.getTriangleCount()).append(" triangles");
    if (result.clearGap > 10.2f && result.balusterCount > 0) {
      text.append(". The space between two balusters is more than the 10 cm (4 in) that building codes allow: lower \"spacing\"");
    }
    return text.toString();
  }

  private static void warnUnknownKeys(String action, Map<String, Object> parameters, List<String> notes) {
    List<String> unknown = new ArrayList<String>();
    for (String key : parameters.keySet()) {
      if (!KEYS.contains(key) && !key.startsWith("_")) {
        unknown.add(key);
      }
    }
    if (!unknown.isEmpty()) {
      notes.add(action + " doesn't know " + unknown + ", which had no effect: its keys are " + KEYS.subList(3, KEYS.size() - 1));
    }
  }
}
