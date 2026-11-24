/*
 * AssistantFenceCommands.java 23 nov. 2026
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

import java.awt.geom.Line2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands that build fences, garden walls and barriers, open gates in them, and change,
 * join, cut and count them. A fence is one piece that keeps its description, from which it is built again.
 */
class AssistantFenceCommands {
  /** The property of a piece that keeps the description of the fence it shows, in JSON. */
  static final String FENCE_PROPERTY = "assistantFence";
  /** The property of a fence that keeps the heights of the ground its posts were built on. */
  static final String FENCE_GROUND_PROPERTY = "assistantFenceGround";

  private static final List<String> PLACEMENT_KEYS = Arrays.asList("along", "offset", "from", "to", "skip", "between", "snap");
  private static final List<String> GATE_KEYS = Arrays.asList("at", "near", "width", "type", "swing", "hinge", "hinge_side",
      "open_angle", "open", "style", "latch", "operator", "self_closing", "name", "height", "slide_direction", "arch",
      "arch_height", "depth", "color", "infill_color", "post_color", "rail_color");

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;
  private String                         adoptionNote;

  AssistantFenceCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"add_fence\",\"points\":[[x,y],[x,y],...],\"style\":\"picket\",\"height\":120}  "
        + "(a fence, a garden wall or a barrier as one piece: posts, rails and what fills its spans, each span following the ground "
        + "built by set_terrain. \"style\" is one of: " + AssistantFenceBuilder.describeStyles(false) + ". "
        + "{\"action\":\"list_fence_styles\"} describes each style and the options it reads. "
        + "Instead of points: \"along\":\"P5\" (a line, a room R#, a fence, or \"lot_line\" / \"lot_line:P5\") with \"offset\" in cm "
        + "(+ to the inside of a closed line), \"from\" and \"to\" (a distance in cm or a point [x,y]) and \"skip\":[[from,to],...] "
        + "(parts left open, like a driveway); or \"between\":[a,b] where a and b are a point [x,y], \"W3:start\", \"W3:end\", \"R2:1\" "
        + "(a corner of a room) or \"F7:end\", and \"snap\":N moves a point to a wall end or a fence end closer than N cm. "
        + "For every style: \"closed\", \"height\" or \"height_profile\":[one height per point], "
        + "\"follow_ground\":\"rake|step|level\" (with \"max_step\" and \"fill_gaps\":\"none|kickboard|soil\"), "
        + "\"post\":{\"spacing\",\"size\",\"shape\":\"square|round|t_post|u_channel|i_beam\",\"cap\":\"flat|pyramid|ball|solar_light|copper|none\","
        + "\"embed_depth\",\"set_in\":\"concrete|driven|gravel\"}, \"rails\":{\"count\"}, \"color\", \"texture\", \"post_color\", \"rail_color\", "
        + "\"infill_color\", \"side\":\"left|right\" (the finished side, seen from the first point), \"kickboard\":N, "
        + "\"lighting\":{\"type\":\"post_cap|strip\",\"spacing\":N}, \"owner\", \"ownership_side\", \"report\":false, \"name\", \"tags\", \"level\", "
        + "and \"openings\":[{\"at\":N or \"near\":[x,y],\"width\":N,\"type\":\"single_swing\"},...] for its gates. "
        + "A key the style doesn't read is reported on its own line; \"spacing\" is read as \"post_spacing\". "
        + "The answer gives the length of its line, with the share of its gates, its posts and its gates. Its materials are "
        + "named after its parts for set_material: posts, caps, rails, pickets, boards, mesh, slats, gate_rails...)\n"
        + "  {\"action\":\"add_gate\",\"fence\":\"F7\",\"near\":[x,y],\"width\":100,\"type\":\"single_swing\"}  "
        + "(opens a gate in a fence, which stays one piece: \"at\":N cm along it or \"near\":[x,y], a point less than 100 cm "
        + "from the fence. A gate that slides needs a run of fence as long as itself on the side of \"slide_direction\":\"start|end\", "
        + "or \"force\":true. \"type\" is one of "
        + join(Arrays.asList(AssistantFenceBuilder.GATE_TYPES)) + "; \"swing\":\"in|out|both\", \"hinge\":\"start|end\", \"open_angle\" "
        + "(or \"open\" from 0 to 1 for a gate that slides or lifts), \"style\" (what fills it, the style of the fence by default), "
        + "\"latch\":\"gravity|magna_latch_pool|drop_rod|lockable|keypad|electric_strike\", \"operator\":\"none|swing_arm|slide_motor\", "
        + "\"self_closing\":true, \"height\", \"name\". modify_gate and remove_gate take \"fence\" and \"gate\":N, its number in the answer)\n"
        + "  {\"action\":\"modify_fence\",\"id\":\"F7\",\"style\":\"shadowbox\",\"height\":180}  "
        + "(changes any option of a fence, or its \"points\", and builds it again; null removes an option. A fence of a previous "
        + "version that isn't straight needs its \"points\")\n"
        + "  {\"action\":\"extend_fence\",\"id\":\"F7\",\"to\":[x,y]}  "
        + "(continues the last side of a fence to a point, or its direction with \"to\":\"terrain_edge\" or \"model_edge\"; "
        + "\"end\":\"start\" extends its first side)\n"
        + "  {\"action\":\"join_fences\",\"ids\":[\"F7\",\"F9\"]}  "
        + "(makes one fence of fences whose ends meet within \"tolerance\" cm (30); it keeps the style of the first one)\n"
        + "  {\"action\":\"split_fence\",\"id\":\"F7\",\"at\":600}  (cuts a fence in two at a distance along it, or \"near\":[x,y])\n"
        + "  {\"action\":\"trim_fence\",\"id\":\"F7\",\"polygon\":[[x,y],...],\"keep\":\"inside\"}  "
        + "(keeps the parts of a fence inside a polygon, or outside it; \"polygon\" may be the id of a room or of a closed line)\n"
        + "  {\"action\":\"offset_fence\",\"id\":\"F7\",\"distance\":50}  (moves a fence sideways, or adds a copy with \"copy\":true)\n"
        + "  {\"action\":\"convert_to_fence\",\"ids\":[\"F3\",\"F4\",\"F5\"],\"style\":\"chain_link\"}  "
        + "(replaces a row of furniture, like fence panels put one by one, by one fence through their centers, with their height; "
        + "\"keep\":true keeps the pieces)\n"
        + "  {\"action\":\"fence_report\"}  "
        + "(the fences and their quantities: length of the line and of its gates, posts by type, panels, gates, bags of concrete, fasteners, "
        + "with totals by style and by owner. \"schedule\" with \"type\":\"gates\" lists the gates, how they swing and the room they need)\n";
  }

  private static String join(List<String> values) {
    StringBuilder text = new StringBuilder();
    for (String value : values) {
      text.append(text.length() > 0 ? "|" : "").append(value);
    }
    return text.toString();
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    this.adoptionNote = null;
    try {
      if ("add_fence".equals(action)) {
        addFence(command, tally, notes);
      } else if ("modify_fence".equals(action)) {
        modifyFence(command, tally, notes);
      } else if ("add_gate".equals(action) || "modify_gate".equals(action) || "remove_gate".equals(action)) {
        changeGate(action, command, tally, notes);
      } else if ("extend_fence".equals(action)) {
        extendFence(command, tally, notes);
      } else if ("join_fences".equals(action)) {
        joinFences(command, tally, notes);
      } else if ("split_fence".equals(action)) {
        splitFence(command, tally, notes);
      } else if ("trim_fence".equals(action)) {
        trimFence(command, tally, notes);
      } else if ("offset_fence".equals(action)) {
        offsetFence(command, tally, notes);
      } else if ("convert_to_fence".equals(action)) {
        convertToFence(command, tally, notes);
      } else if ("fence_report".equals(action)
                 || "measure".equals(action) && command.getBoolean("fences", false)) {
        notes.add(report(false));
      } else if ("schedule".equals(action) && command.getString("type") != null
                 && command.getString("type").toLowerCase(Locale.ENGLISH).startsWith("gate")) {
        notes.add(report(true));
      } else if ("list_fence_styles".equals(action)) {
        notes.add("Fence styles.\n" + AssistantFenceBuilder.describeStyles(true)
            + "\nGate types: " + join(Arrays.asList(AssistantFenceBuilder.GATE_TYPES)));
      } else {
        return false;
      }
      if (this.adoptionNote != null) {
        notes.add(this.adoptionNote);
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    } catch (RuntimeException ex) {
      notes.add("Couldn't run " + action + " (" + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()) + ")");
    }
    return true;
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  // ---- Descriptions ----

  /**
   * Returns the description of the fence a piece shows, or <code>null</code> if it isn't one.
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> getSpec(HomePieceOfFurniture piece) {
    String property = piece.getProperty(FENCE_PROPERTY);
    if (property == null) {
      return null;
    }
    try {
      Object parsed = JSONParser.parse(property);
      return parsed instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>)parsed) : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static List<Object> toList(float [][] points) {
    List<Object> list = new ArrayList<Object>();
    for (float [] point : points) {
      list.add(Arrays.asList((Object)Double.valueOf(Math.round(point [0] * 100) / 100.), Double.valueOf(Math.round(point [1] * 100) / 100.)));
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getOpenings(Map<String, Object> spec) {
    List<Map<String, Object>> openings = new ArrayList<Map<String, Object>>();
    if (spec.get("openings") instanceof List) {
      for (Object opening : (List<?>)spec.get("openings")) {
        if (opening instanceof Map) {
          openings.add(new LinkedHashMap<String, Object>((Map<String, Object>)opening));
        }
      }
    }
    if (spec.get("gate") instanceof Map) {
      openings.add(new LinkedHashMap<String, Object>((Map<String, Object>)spec.get("gate")));
    }
    return openings;
  }

  private static void setOpenings(Map<String, Object> spec, List<Map<String, Object>> openings) {
    spec.remove("gate");
    if (openings.isEmpty()) {
      spec.remove("openings");
    } else {
      spec.put("openings", new ArrayList<Object>(openings));
    }
  }

  private HomePieceOfFurniture getFence(AssistantCommand command, String action) throws IOException {
    String id = command.getString("fence") != null ? command.getString("fence") : command.getString("id");
    HomePieceOfFurniture fence = null;
    if (id != null) {
      HomeObject item = HomeItemIds.find(this.home, id.trim());
      if (item == null && HomeItemIds.isId(id.trim())) {
        HomeItemIds.ensureIds(this.home);
        item = HomeItemIds.find(this.home, id.trim());
      }
      if (item instanceof HomePieceOfFurniture && ((HomePieceOfFurniture)item).getProperty(FENCE_PROPERTY) != null) {
        fence = (HomePieceOfFurniture)item;
      } else if (item instanceof HomePieceOfFurniture
                 && "fence".equals(((HomePieceOfFurniture)item).getProperty(AssistantGardenCommands.GARDEN_PROPERTY))) {
        // A fence of a previous version, which kept no description
        fence = (HomePieceOfFurniture)item;
        adoptFence(fence, command);
      } else {
        throw new IOException(item == null ? "no item " + id : id + " isn't a fence built by add_fence"
            + (item instanceof HomePieceOfFurniture ? ": convert_to_fence makes one of a row of pieces" : ""));
      }
    } else {
      // The only fence of the home
      for (HomePieceOfFurniture piece : this.home.getFurniture()) {
        if (piece.getProperty(FENCE_PROPERTY) != null) {
          if (fence != null) {
            throw new IOException(action + " needs the id of the fence: \"fence\":\"F#\"");
          }
          fence = piece;
        }
      }
      if (fence == null) {
        throw new IOException("the home has no fence built by add_fence");
      }
    }
    if (getSpec(fence) == null) {
      throw new IOException("the description of the fence " + this.executor.getId(fence) + " can't be read");
    }
    return fence;
  }

  /**
   * Gives a description to a fence built by a version that kept none (7.29 and before), from the points of the command
   * or from the place of the piece when it is a straight fence along an axis of the plan.
   */
  private void adoptFence(HomePieceOfFurniture piece, AssistantCommand command) throws IOException {
    String id = this.executor.getId(piece);
    Map<String, Object> spec = new LinkedHashMap<String, Object>();
    String from;
    if (command.getParameters().get("points") instanceof List) {
      spec.put("points", command.getParameters().get("points"));
      from = "the points of the command";
    } else if (Math.min(piece.getWidth(), piece.getDepth()) <= 40 && Math.max(piece.getWidth(), piece.getDepth()) >= 60
               && Math.abs(Math.sin(2 * piece.getAngle())) < 0.01) {
      boolean alongX = piece.getWidth() >= piece.getDepth() ^ Math.abs(Math.sin(piece.getAngle())) > 0.5;
      float half = Math.max(piece.getWidth(), piece.getDepth()) / 2 - Math.min(piece.getWidth(), piece.getDepth()) / 2;
      float [][] points = alongX
          ? new float [][] {{piece.getX() - half, piece.getY()}, {piece.getX() + half, piece.getY()}}
          : new float [][] {{piece.getX(), piece.getY() - half}, {piece.getX(), piece.getY() + half}};
      spec.put("points", toList(points));
      from = "the place of the piece, a straight line from [" + format(points [0][0]) + "," + format(points [0][1]) + "] to ["
          + format(points [1][0]) + "," + format(points [1][1]) + "]";
    } else {
      throw new IOException("the fence " + id + " was built by a previous version, which didn't keep its line, and it isn't straight "
          + "along an axis of the plan: {\"action\":\"modify_fence\",\"id\":\"" + id + "\",\"points\":[[x,y],[x,y],...],\"style\":\"picket\"} "
          + "gives its line and builds it again as a fence of this version");
    }
    String name = piece.getName() != null ? piece.getName().toLowerCase(Locale.ENGLISH) : "";
    spec.put("style", command.getString("style") != null && !"add_gate".equals(command.getAction())
        && !"modify_gate".equals(command.getAction()) ? command.getString("style") : name.contains("wire") ? "welded_wire"
            : name.contains("rail") ? "split_rail" : name.contains("panel") ? "privacy" : "picket");
    if (AssistantFenceBuilder.getStyle(spec.get("style").toString()) == null) {
      spec.put("style", "picket");
    }
    spec.put("height", Double.valueOf(Math.max(5, Math.min(800, Math.round(piece.getHeight() - 6)))));
    // Checked before the piece is changed
    AssistantFenceBuilder.build(spec, getSite(piece.getLevel(), null));
    piece.setProperty(FENCE_PROPERTY, AssistantShapeCommands.toJson(spec));
    this.adoptionNote = "The fence " + id + " was built by a previous version, which kept no description of it: it is now a "
        + spec.get("style") + " fence " + format(((Double)spec.get("height")).doubleValue()) + " cm high along " + from
        + ". Its gates, if it had some, are to add again with add_gate; modify_fence changes its style, its height or its points";
  }

  private AssistantFenceBuilder.Site getSite(final Level level, final List<String> notes) {
    final float levelElevation = level != null ? level.getElevation() : 0;
    boolean terrain = false;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      terrain |= HomeTerrain.isTerrain(piece);
    }
    final boolean hasTerrain = terrain;
    return new AssistantFenceBuilder.Site() {
        public float getGroundHeight(float x, float y) {
          return executor.getShapeCommands().getStandingHeight(x, y) - levelElevation;
        }

        public boolean hasTerrain() {
          return hasTerrain;
        }

        public AssistantMeshBuilder.Material getTexture(AssistantMeshBuilder mesh, String texture, float scale, int color) {
          String [] ambiguity = new String [1];
          CatalogTexture catalogTexture = executor.getBuildingCommands().findTexture(texture, null, null, "texture",
              new ArrayList<String>(), ambiguity);
          return catalogTexture != null ? mesh.getMaterial(catalogTexture, scale, 0, 0, 0, color) : null;
        }
      };
  }

  /**
   * Returns the length of the line of a fence, with the share of its gates when it has some.
   */
  private static String describeLength(AssistantFenceBuilder.Result result) {
    if (result.gateLength < 0.5) {
      return format(result.length / 100) + " m long";
    }
    return format(result.length / 100) + " m of line (" + format((result.length - result.gateLength) / 100) + " m of fence, "
        + format(result.gateLength / 100) + " m of " + (result.gates.size() == 1 ? "gate" : "gates") + ")";
  }

  /**
   * Returns the sentence that describes what was built.
   */
  private String describe(HomePieceOfFurniture piece, AssistantFenceBuilder.Result result, Map<String, Object> spec) {
    StringBuilder text = new StringBuilder(this.executor.getId(piece) + " \"" + piece.getName() + "\" (" + result.style.name + ", "
        + format(result.height) + " cm high): " + describeLength(result));
    if (result.getPostCount() > 0) {
      text.append(", ").append(result.getPostCount()).append(result.getPostCount() == 1 ? " post" : " posts");
    }
    text.append(", ").append(result.spans).append(result.spans == 1 ? " span" : " spans");
    if (result.closed) {
      text.append(", closed");
    }
    if (result.highestGround - result.lowestGround > 1) {
      text.append(", following the ground from ").append(format(result.lowestGround)).append(" to ")
          .append(format(result.highestGround)).append(" cm");
    }
    if (result.retained > 1) {
      text.append(", holding up to ").append(format(result.retained)).append(" cm of ground (the terrain itself isn't changed)");
    }
    for (int i = 0; i < result.gates.size(); i++) {
      text.append(i == 0 ? ". Gates: " : "; ").append(i + 1).append(' ').append(result.gates.get(i));
    }
    if (spec.get("owner") != null) {
      text.append(". Owner: ").append(spec.get("owner"));
    }
    for (String violation : result.violations) {
      text.append(". Pool code: ").append(violation);
    }
    for (String note : result.notes) {
      text.append(". Note: ").append(note);
    }
    for (String problem : result.slideProblems) {
      text.append(". Warning: ").append(problem);
    }
    return text.toString();
  }

  /**
   * Returns the description of a fence for <code>describe_item</code>, or <code>null</code> if <code>piece</code> isn't one.
   */
  String describeFence(HomePieceOfFurniture piece) {
    Map<String, Object> spec = getSpec(piece);
    if (spec == null) {
      return null;
    }
    try {
      AssistantFenceBuilder.Result result = AssistantFenceBuilder.build(spec, getSite(piece.getLevel(), null));
      StringBuilder options = new StringBuilder();
      for (Map.Entry<String, Object> entry : spec.entrySet()) {
        if (!entry.getKey().equals("points") && !entry.getKey().equals("openings") && !entry.getKey().equals("style")) {
          options.append(options.length() > 0 ? ", " : "").append(entry.getKey()).append('=').append(entry.getValue());
        }
      }
      return "fence " + describe(piece, result, spec) + ". Points: " + AssistantShapeCommands.toJson(spec.get("points"))
          + (options.length() > 0 ? ". Options: " + options : "");
    } catch (IOException ex) {
      return "fence whose description can't be built (" + ex.getMessage() + ")";
    }
  }

  /**
   * Reports the keys of a command that nothing read.
   */
  private void warnUnknownKeys(String action, Map<String, Object> parameters, AssistantFenceBuilder.Result result,
                               List<String> extraKeys, List<String> notes) {
    List<String> unknown = new ArrayList<String>();
    for (String key : parameters.keySet()) {
      if (!result.usedKeys.contains(key) && !PLACEMENT_KEYS.contains(key) && !extraKeys.contains(key)
          && !key.equals("action") && !key.equals("id") && !key.equals("fence")) {
        unknown.add(key);
      }
    }
    if (parameters.get("openings") instanceof List) {
      for (Object opening : (List<?>)parameters.get("openings")) {
        if (opening instanceof Map) {
          warnUnknownGateKeys((Map<?, ?>)opening, unknown);
        }
      }
    }
    if (!unknown.isEmpty()) {
      notes.add("Warning: " + action + " ignored " + (unknown.size() == 1 ? "the key " : "the keys ") + unknown
          + ", which the style " + result.style.name + " doesn't read. {\"action\":\"list_fence_styles\"} gives the styles");
    }
  }

  private static void warnUnknownGateKeys(Map<?, ?> opening, List<String> unknown) {
    for (Object key : opening.keySet()) {
      if (!GATE_KEYS.contains(key) && !unknown.contains("openings." + key)) {
        boolean option = false;
        for (AssistantFenceBuilder.Style style : AssistantFenceBuilder.getStyles()) {
          option |= style.defaults.containsKey(key);
        }
        if (!option) {
          unknown.add("openings." + key);
        }
      }
    }
  }

  // ---- Placement ----

  /**
   * Returns the points of the item with the given id: a line, a room, a wall or a fence.
   * @param closed set to <code>true</code> at index 0 if the line is closed
   */
  private float [][] getItemPoints(String id, boolean [] closed) throws IOException {
    String token = id.trim();
    if (token.toLowerCase(Locale.ENGLISH).startsWith("lot_line")) {
      String lineId = token.indexOf(':') > 0 ? token.substring(token.indexOf(':') + 1).trim() : null;
      for (Polyline polyline : this.home.getPolylines()) {
        if ("property".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))
            && (lineId == null || lineId.equalsIgnoreCase(HomeItemIds.getId(polyline))
                || lineId.equalsIgnoreCase(polyline.getProperty(AssistantSiteCommands.LINE_NAME_PROPERTY)))) {
          closed [0] = polyline.isClosedPath();
          return polyline.getPoints();
        }
      }
      throw new IOException(lineId == null ? "the home has no property line: add_property_line or import_parcel draws one"
          : "no property line " + lineId);
    }
    HomeObject item = HomeItemIds.find(this.home, token);
    if (item == null && HomeItemIds.isId(token)) {
      HomeItemIds.ensureIds(this.home);
      item = HomeItemIds.find(this.home, token);
    }
    if (item instanceof Polyline) {
      closed [0] = ((Polyline)item).isClosedPath();
      return ((Polyline)item).getPoints();
    } else if (item instanceof Room) {
      closed [0] = true;
      return ((Room)item).getPoints();
    } else if (item instanceof Wall) {
      Wall wall = (Wall)item;
      return new float [][] {{wall.getXStart(), wall.getYStart()}, {wall.getXEnd(), wall.getYEnd()}};
    } else if (item instanceof HomePieceOfFurniture && getSpec((HomePieceOfFurniture)item) != null) {
      Map<String, Object> spec = getSpec((HomePieceOfFurniture)item);
      float [][] points = AssistantFenceBuilder.getPoints(spec);
      closed [0] = AssistantFenceBuilder.isClosed(spec, points);
      return points;
    }
    throw new IOException(item == null ? "no item " + id : id + " isn't a line, a room, a wall or a fence");
  }

  /**
   * Returns the point a reference gives: [x,y], "W3:start", "R2:1", "P5:end", "F7:end".
   */
  private float [] getReferencePoint(Object reference, float snap) throws IOException {
    float [] point;
    if (reference instanceof List && ((List<?>)reference).size() >= 2 && ((List<?>)reference).get(0) instanceof Number
        && ((List<?>)reference).get(1) instanceof Number) {
      point = new float [] {((Number)((List<?>)reference).get(0)).floatValue(), ((Number)((List<?>)reference).get(1)).floatValue()};
      if (snap > 0) {
        // The closest end of a wall or of a fence
        float [] best = null;
        double bestDistance = snap;
        List<float []> candidates = new ArrayList<float []>();
        for (Wall wall : this.home.getWalls()) {
          candidates.add(new float [] {wall.getXStart(), wall.getYStart()});
          candidates.add(new float [] {wall.getXEnd(), wall.getYEnd()});
        }
        for (HomePieceOfFurniture piece : this.home.getFurniture()) {
          Map<String, Object> spec = getSpec(piece);
          if (spec != null) {
            candidates.addAll(Arrays.asList(AssistantFenceBuilder.getPoints(spec)));
          }
        }
        for (float [] candidate : candidates) {
          double distance = Math.hypot(candidate [0] - point [0], candidate [1] - point [1]);
          if (distance <= bestDistance) {
            bestDistance = distance;
            best = candidate;
          }
        }
        if (best != null) {
          point = best.clone();
        }
      }
      return point;
    } else if (reference instanceof String) {
      String text = ((String)reference).trim();
      int colon = text.lastIndexOf(':');
      String id = colon > 0 ? text.substring(0, colon) : text;
      String part = colon > 0 ? text.substring(colon + 1).trim().toLowerCase(Locale.ENGLISH) : "end";
      float [][] points = getItemPoints(id, new boolean [1]);
      if (part.equals("start") || part.equals("first")) {
        return points [0];
      } else if (part.equals("end") || part.equals("last")) {
        return points [points.length - 1];
      }
      try {
        int index = Integer.parseInt(part.replace("corner", "").trim());
        if (index >= 1 && index <= points.length) {
          return points [index - 1];
        }
      } catch (NumberFormatException ex) {
        // Reported below
      }
      throw new IOException("\"" + text + "\": after the id, write :start, :end or the number of a point from 1 to " + points.length);
    }
    throw new IOException("a point is written [x,y], \"W3:start\", \"W3:end\", \"R2:1\" or \"F7:end\"");
  }

  /**
   * Returns the line at a distance of the given one, on its left when the distance is positive.
   */
  static float [][] offsetLine(float [][] points, boolean closed, float offset) {
    int count = points.length;
    int sides = closed ? count : count - 1;
    float [][] normals = new float [sides][];
    for (int i = 0; i < sides; i++) {
      float [] next = points [(i + 1) % count];
      float length = (float)Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
      normals [i] = length > 0 ? new float [] {(next [1] - points [i][1]) / length, -(next [0] - points [i][0]) / length}
          : new float [] {0, 0};
    }
    float [][] result = new float [count][];
    for (int i = 0; i < count; i++) {
      float [] before = i > 0 ? normals [i - 1] : closed ? normals [sides - 1] : null;
      float [] after = i < sides ? normals [i] : null;
      float nx;
      float ny;
      if (before == null || after == null) {
        float [] normal = before != null ? before : after;
        nx = normal [0];
        ny = normal [1];
      } else {
        float dot = before [0] * after [0] + before [1] * after [1];
        if (1 + dot < 0.2f) {
          nx = after [0];
          ny = after [1];
        } else {
          nx = (before [0] + after [0]) / (1 + dot);
          ny = (before [1] + after [1]) / (1 + dot);
        }
      }
      result [i] = new float [] {points [i][0] + nx * offset, points [i][1] + ny * offset};
    }
    return result;
  }

  private static double getSignedArea(float [][] points) {
    double area = 0;
    for (int i = 0; i < points.length; i++) {
      float [] next = points [(i + 1) % points.length];
      area += points [i][0] * next [1] - next [0] * points [i][1];
    }
    return area / 2;
  }

  /**
   * Returns the distance along a line that a number or a point gives.
   */
  private static double getDistance(Object value, float [][] points, boolean closed, double defaultValue) throws IOException {
    if (value == null) {
      return defaultValue;
    } else if (value instanceof Number) {
      return ((Number)value).doubleValue();
    } else if (value instanceof List && ((List<?>)value).size() >= 2 && ((List<?>)value).get(0) instanceof Number
               && ((List<?>)value).get(1) instanceof Number) {
      return AssistantFenceBuilder.getClosestDistance(points, closed, ((Number)((List<?>)value).get(0)).floatValue(),
          ((Number)((List<?>)value).get(1)).floatValue());
    }
    throw new IOException("a place along a line is a distance in cm from its first point, or a point [x,y]");
  }

  /**
   * Replaces the placement keys of a description by its points.
   */
  @SuppressWarnings("unchecked")
  private void resolvePlacement(Map<String, Object> spec, List<String> notes) throws IOException {
    float snap = AssistantFurnitureBuilder.getNumber(spec, "snap", 0);
    if (spec.get("along") != null) {
      boolean [] closedLine = new boolean [1];
      float [][] line;
      if (spec.get("along") instanceof List) {
        line = AssistantFenceBuilder.getPoints(Collections.singletonMap("points", spec.get("along")));
        closedLine [0] = AssistantFenceBuilder.isClosed(Collections.singletonMap("points", spec.get("along")), line);
      } else {
        line = getItemPoints(spec.get("along").toString(), closedLine);
      }
      if (line.length < 2) {
        throw new IOException("\"along\" gives a line of 2 points at least");
      }
      float offset = AssistantFurnitureBuilder.getNumber(spec, "offset", 0);
      if (offset != 0) {
        // Positive to the inside of a closed line, to the left of an open one
        float sign = closedLine [0] ? (getSignedArea(line) > 0 ? -1 : 1) : -1;
        line = offsetLine(line, closedLine [0], offset * sign);
      }
      double [] distances = AssistantFenceBuilder.getDistances(line, closedLine [0]);
      double length = distances [distances.length - 1];
      boolean cut = spec.get("from") != null || spec.get("to") != null;
      double from = Math.max(0, Math.min(length, getDistance(spec.get("from"), line, closedLine [0], 0)));
      double to = Math.max(0, Math.min(length, getDistance(spec.get("to"), line, closedLine [0], length)));
      List<float []> points = new ArrayList<float []>();
      boolean closed = closedLine [0] && !cut;
      if (cut) {
        if (to < from && !closedLine [0]) {
          double swap = from;
          from = to;
          to = swap;
        }
        double end = to < from ? to + length : to;
        points.add(AssistantFenceBuilder.getPoint(line, distances, from));
        for (int turn = 0; turn < 2; turn++) {
          for (int i = 0; i < line.length; i++) {
            double distance = distances [i] + turn * length;
            if (distance > from + 0.5 && distance < end - 0.5) {
              points.add(line [i]);
            }
          }
        }
        points.add(AssistantFenceBuilder.getPoint(line, distances, to));
      } else {
        points.addAll(Arrays.asList(line));
      }
      float [][] path = points.toArray(new float [points.size()][]);
      spec.put("points", toList(path));
      spec.put("closed", Boolean.valueOf(closed));
      // The parts left open
      if (spec.get("skip") instanceof List) {
        List<Map<String, Object>> openings = getOpenings(spec);
        List<?> skips = (List<?>)spec.get("skip");
        if (!skips.isEmpty() && !(skips.get(0) instanceof List || skips.get(0) instanceof Map)) {
          skips = Collections.singletonList(skips);
        }
        for (Object skip : skips) {
          Object start = skip instanceof Map ? ((Map<?, ?>)skip).get("from") : skip instanceof List && ((List<?>)skip).size() == 2
              ? ((List<?>)skip).get(0) : null;
          Object end = skip instanceof Map ? ((Map<?, ?>)skip).get("to") : skip instanceof List && ((List<?>)skip).size() == 2
              ? ((List<?>)skip).get(1) : null;
          if (start == null || end == null) {
            throw new IOException("\"skip\" is written [[from,to],...], each place a distance in cm along the fence or a point [x,y]");
          }
          double skipStart = getDistance(start, path, closed, 0);
          double skipEnd = getDistance(end, path, closed, 0);
          Map<String, Object> opening = new LinkedHashMap<String, Object>();
          opening.put("at", Double.valueOf((skipStart + skipEnd) / 2));
          opening.put("width", Double.valueOf(Math.max(30, Math.abs(skipEnd - skipStart))));
          opening.put("type", "gap");
          openings.add(opening);
        }
        setOpenings(spec, openings);
      }
    } else if (spec.get("between") != null) {
      if (!(spec.get("between") instanceof List) || ((List<?>)spec.get("between")).size() < 2) {
        throw new IOException("\"between\" is written [a,b], each one a point [x,y], \"W3:end\", \"R2:1\" or \"F7:end\"");
      }
      List<float []> points = new ArrayList<float []>();
      for (Object reference : (List<?>)spec.get("between")) {
        points.add(getReferencePoint(reference, snap));
      }
      spec.put("points", toList(points.toArray(new float [points.size()][])));
    } else if (snap > 0 && spec.get("points") instanceof List) {
      List<float []> points = new ArrayList<float []>();
      for (Object point : (List<?>)spec.get("points")) {
        points.add(getReferencePoint(point, snap));
      }
      spec.put("points", toList(points.toArray(new float [points.size()][])));
    } else if (spec.get("points") == null) {
      throw new IOException("give \"points\":[[x,y],...], or \"along\":\"<the id of a line, of a room or of a fence, or lot_line>\", "
          + "or \"between\":[a,b]");
    }
    for (String key : PLACEMENT_KEYS) {
      spec.remove(key);
    }
    // The gates given by a point
    float [][] points = AssistantFenceBuilder.getPoints(spec);
    boolean closed = AssistantFenceBuilder.isClosed(spec, points);
    List<Map<String, Object>> openings = getOpenings(spec);
    boolean changed = spec.get("gate") != null;
    for (Map<String, Object> opening : openings) {
      changed |= resolveGatePlace(opening, points, closed, true);
    }
    if (changed) {
      setOpenings(spec, openings);
    }
  }

  private static boolean resolveGatePlace(Map<String, Object> opening, float [][] points, boolean closed,
                                          boolean strict) throws IOException {
    Object near = opening.get("near");
    if (near == null) {
      return false;
    } else if (!(near instanceof List) || ((List<?>)near).size() < 2 || !(((List<?>)near).get(0) instanceof Number)
               || !(((List<?>)near).get(1) instanceof Number)) {
      throw new IOException("\"near\" is a point [x,y]: the gate is put at the place of the fence the closest to it");
    }
    float nearX = ((Number)((List<?>)near).get(0)).floatValue();
    float nearY = ((Number)((List<?>)near).get(1)).floatValue();
    double at = AssistantFenceBuilder.getClosestDistance(points, closed, nearX, nearY);
    double [] distances = AssistantFenceBuilder.getDistances(points, closed);
    double length = distances [closed ? points.length : points.length - 1];
    float width = AssistantFurnitureBuilder.getNumber(opening, "width", 100);
    // A point far from the fence is a mistake, not a gate at its end
    float [] closest = AssistantFenceBuilder.getPoint(points, distances, at);
    double gap = Math.hypot(closest [0] - nearX, closest [1] - nearY);
    double tolerance = Math.max(100, width / 2);
    if (strict && gap > tolerance) {
      throw new IOException("the point \"near\" [" + format(nearX) + "," + format(nearY) + "] is " + format(gap / 100)
          + " m from the fence, whose closest place is [" + format(closest [0]) + "," + format(closest [1]) + "] at "
          + format(at) + " cm along it: give a point less than " + format(tolerance) + " cm from the fence, or \"at\":N");
    }
    // A gate near an end stays in the fence
    at = Math.max(width / 2, Math.min(length - width / 2, at));
    opening.put("at", Double.valueOf(Math.round(at * 10) / 10.));
    opening.remove("near");
    return true;
  }

  // ---- Commands ----

  private void addFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Map<String, Object> parameters = command.getParameters();
    Map<String, Object> spec = new LinkedHashMap<String, Object>(parameters);
    spec.remove("action");
    spec.remove("id");
    AssistantFenceBuilder.Style style = AssistantFenceBuilder.getStyle(command.getString("style"));
    if (style == null) {
      throw new IOException("unknown \"style\" \"" + command.getString("style") + "\". The styles: "
          + AssistantFenceBuilder.describeStyles(false));
    }
    resolvePlacement(spec, notes);
    if (style.isHedge()) {
      // A hedge is planted along the same line
      Map<String, Object> hedge = new LinkedHashMap<String, Object>(spec);
      hedge.remove("style");
      hedge.remove("openings");
      this.executor.getGardenCommands().apply("add_hedge", new AssistantCommand("add_hedge", hedge), tally, notes);
      return;
    }
    Level level = this.executor.getShapeCommands().getLevel(command, "add_fence", notes);
    // The result first, then the warnings on their own lines
    int index = notes.size();
    addPiece(spec, level, command.getString("name"), parameters.get("tags"), tally, notes, parameters, "add_fence");
    if (index < notes.size()) {
      notes.set(index, "Added the fence " + notes.get(index));
    }
  }

  /**
   * Adds the piece that shows the fence described by <code>spec</code>, and the note that describes it.
   */
  private HomePieceOfFurniture addPiece(Map<String, Object> spec, Level level, String name, Object tags, int [] tally,
                                        List<String> notes, Map<String, Object> parameters, String action) throws IOException {
    spec.remove("level");
    spec.remove("name");
    spec.remove("tags");
    AssistantFenceBuilder.Result result = AssistantFenceBuilder.build(spec, getSite(level, notes));
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    String pieceName = name != null && name.trim().length() > 0 ? name.trim()
        : result.style.family.startsWith("Masonry") ? "Garden wall" : "Fence";
    HomePieceOfFurniture piece = shapeCommands.createPiece(pieceName, result.mesh.store("fence"), level, false, "site");
    piece.setProperty(AssistantGardenCommands.GARDEN_PROPERTY, "fence");
    Set<String> allTags = new LinkedHashSet<String>();
    allTags.add("garden");
    allTags.add("fence");
    if (tags instanceof List) {
      for (Object tag : (List<?>)tags) {
        allTags.add(String.valueOf(tag));
      }
    } else if (tags != null) {
      allTags.addAll(HomeItemTags.parseTags(tags.toString()));
    }
    piece.setProperty(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(allTags));
    piece.setProperty(FENCE_PROPERTY, AssistantShapeCommands.toJson(spec));
    piece.setProperty(FENCE_GROUND_PROPERTY, getGroundSignature(result));
    shapeCommands.add(piece);
    tally [0]++;
    notes.add(describe(piece, result, spec));
    if (parameters != null) {
      warnUnknownKeys(action, parameters, result, Arrays.asList("level", "name", "tags"), notes);
    }
    return piece;
  }

  /**
   * Builds a fence again from a new description, in an undoable way.
   */
  private AssistantFenceBuilder.Result rebuild(HomePieceOfFurniture piece, Map<String, Object> spec, int [] tally,
                                               List<String> notes) throws IOException {
    AssistantFenceBuilder.Result result = AssistantFenceBuilder.build(spec, getSite(piece.getLevel(), notes));
    AssistantGeneratedModel model = result.mesh.store("fence");
    float [] bounds = model.getBounds();
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put(FENCE_PROPERTY, AssistantShapeCommands.toJson(spec));
    properties.put(FENCE_GROUND_PROPERTY, getGroundSignature(result));
    this.executor.getShapeCommands().replaceModel(piece, model, (bounds [0] + bounds [3]) / 2, (bounds [2] + bounds [5]) / 2,
        bounds [1], properties);
    tally [4]++;
    return result;
  }

  private void modifyFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture piece = getFence(command, "modify_fence");
    Map<String, Object> spec = getSpec(piece);
    int changes = 0;
    Map<String, Object> changed = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : command.getParameters().entrySet()) {
      String key = entry.getKey();
      if (key.equals("action") || key.equals("id") || key.equals("fence") || key.equals("name")) {
        continue;
      } else if (key.equals("rebuild")) {
        // Built again on the ground as it is now
        changes++;
        continue;
      }
      if (entry.getValue() == null) {
        spec.remove(key);
      } else {
        spec.put(key, entry.getValue());
      }
      changed.put(key, entry.getValue());
      changes++;
    }
    if (changes == 0) {
      throw new IOException("give what changes: style, height, points, color, post, openings... A null value removes an option");
    }
    if (command.has("along") || command.has("between") || command.has("snap") || changed.containsKey("openings")
        || changed.containsKey("gate") || changed.containsKey("points")) {
      resolvePlacement(spec, notes);
    }
    AssistantFenceBuilder.Result result = rebuild(piece, spec, tally, notes);
    notes.add("Modified the fence " + describe(piece, result, spec));
    warnUnknownKeys("modify_fence", changed, result, Collections.<String>emptyList(), notes);
  }

  private void changeGate(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture piece = getFence(command, action);
    Map<String, Object> spec = getSpec(piece);
    float [][] points = AssistantFenceBuilder.getPoints(spec);
    boolean closed = AssistantFenceBuilder.isClosed(spec, points);
    List<Map<String, Object>> openings = getOpenings(spec);
    Map<String, Object> gate = new LinkedHashMap<String, Object>(command.getParameters());
    for (String key : new String [] {"action", "id", "fence", "gate", "index", "force"}) {
      gate.remove(key);
    }
    List<String> slideProblems = AssistantFenceBuilder.build(spec, getSite(piece.getLevel(), null)).slideProblems;
    List<String> unknown = new ArrayList<String>();
    warnUnknownGateKeys(gate, unknown);
    String done;
    if ("add_gate".equals(action)) {
      if (gate.get("at") == null && gate.get("near") == null) {
        throw new IOException("give the place of the gate: \"at\":N cm along the fence from its first point, or \"near\":[x,y]");
      }
      resolveGatePlace(gate, points, closed, true);
      openings.add(gate);
      done = "Added a gate to the fence ";
    } else {
      if (openings.isEmpty()) {
        throw new IOException("the fence " + this.executor.getId(piece) + " has no gate");
      }
      // The gates are numbered along the fence
      List<Map<String, Object>> sorted = new ArrayList<Map<String, Object>>(openings);
      Collections.sort(sorted, new java.util.Comparator<Map<String, Object>>() {
          public int compare(Map<String, Object> gate1, Map<String, Object> gate2) {
            return Float.compare(AssistantFurnitureBuilder.getNumber(gate1, "at", 0), AssistantFurnitureBuilder.getNumber(gate2, "at", 0));
          }
        });
      double number = command.getDouble("gate", command.getDouble("index", sorted.size() == 1 ? 1 : Double.NaN));
      Map<String, Object> target = null;
      if (!Double.isNaN(number) && number >= 1 && number <= sorted.size()) {
        target = sorted.get((int)Math.round(number) - 1);
      } else if (Double.isNaN(number) && gate.get("near") instanceof List && "remove_gate".equals(action)) {
        Map<String, Object> place = new LinkedHashMap<String, Object>(gate);
        resolveGatePlace(place, points, closed, false);
        float at = AssistantFurnitureBuilder.getNumber(place, "at", 0);
        for (Map<String, Object> candidate : sorted) {
          if (target == null || Math.abs(AssistantFurnitureBuilder.getNumber(candidate, "at", 0) - at)
                  < Math.abs(AssistantFurnitureBuilder.getNumber(target, "at", 0) - at)) {
            target = candidate;
          }
        }
      }
      if (target == null) {
        throw new IOException("give the gate with \"gate\":N, from 1 to " + sorted.size() + " along the fence");
      }
      int index = openings.indexOf(target);
      if ("remove_gate".equals(action)) {
        openings.remove(index);
        done = "Removed a gate of the fence ";
      } else {
        if (gate.isEmpty()) {
          throw new IOException("give what changes: width, type, swing, hinge, open_angle, style, latch, at...");
        }
        Map<String, Object> modified = new LinkedHashMap<String, Object>(target);
        for (Map.Entry<String, Object> entry : gate.entrySet()) {
          if (entry.getValue() == null) {
            modified.remove(entry.getKey());
          } else {
            modified.put(entry.getKey(), entry.getValue());
          }
        }
        resolveGatePlace(modified, points, closed, true);
        openings.set(index, modified);
        done = "Modified a gate of the fence ";
      }
    }
    setOpenings(spec, openings);
    if (!"remove_gate".equals(action) && !command.getBoolean("force", false)) {
      // A gate that slides needs a run of fence to slide along
      List<String> problems = new ArrayList<String>(AssistantFenceBuilder.build(spec, getSite(piece.getLevel(), null)).slideProblems);
      problems.removeAll(slideProblems);
      if (!problems.isEmpty()) {
        throw new IOException(problems.get(0) + ". \"force\":true builds it anyway");
      }
    }
    AssistantFenceBuilder.Result result = rebuild(piece, spec, tally, notes);
    notes.add(done + describe(piece, result, spec));
    if (!unknown.isEmpty()) {
      List<String> keys = new ArrayList<String>();
      for (String key : unknown) {
        keys.add(key.substring("openings.".length()));
      }
      notes.add("Warning: " + action + " kept " + (keys.size() == 1 ? "the key " : "the keys ") + keys + " that no gate reads");
    }
  }

  private void extendFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture piece = getFence(command, "extend_fence");
    Map<String, Object> spec = getSpec(piece);
    float [][] points = AssistantFenceBuilder.getPoints(spec);
    if (AssistantFenceBuilder.isClosed(spec, points)) {
      throw new IOException("a closed fence has no end to extend");
    }
    boolean atStart = "start".equalsIgnoreCase(command.getString("end"));
    float [] last = atStart ? points [0] : points [points.length - 1];
    float [] previous = atStart ? points [1] : points [points.length - 2];
    float length = (float)Math.hypot(last [0] - previous [0], last [1] - previous [1]);
    float dx = (last [0] - previous [0]) / length;
    float dy = (last [1] - previous [1]) / length;
    Object to = command.getParameters().get("to");
    float [] target;
    boolean straight = false;
    if (to instanceof String && (((String)to).toLowerCase(Locale.ENGLISH).contains("terrain")
                                 || ((String)to).toLowerCase(Locale.ENGLISH).contains("model"))) {
      boolean terrain = ((String)to).toLowerCase(Locale.ENGLISH).contains("terrain");
      float reach = terrain ? getTerrainReach(last, dx, dy) : getModelReach(last, dx, dy);
      if (reach < 1) {
        throw new IOException(terrain ? "the fence already ends at the edge of the terrain, or the home has none"
            : "the fence already ends at the edge of the model");
      }
      target = new float [] {last [0] + dx * reach, last [1] + dy * reach};
      straight = true;
    } else if (to != null) {
      target = getReferencePoint(to, (float)command.getDouble("snap", 0));
    } else if (!Double.isNaN(command.getDouble("by", Double.NaN))) {
      float by = (float)command.getDouble("by", 0);
      target = new float [] {last [0] + dx * by, last [1] + dy * by};
      straight = true;
    } else {
      throw new IOException("give \"to\":[x,y], \"to\":\"terrain_edge\", \"to\":\"model_edge\" or \"by\":N cm");
    }
    List<float []> extended = new ArrayList<float []>(Arrays.asList(points));
    float added = (float)Math.hypot(target [0] - last [0], target [1] - last [1]);
    if (straight) {
      // The last side gets longer
      extended.set(atStart ? 0 : extended.size() - 1, target);
    } else if (atStart) {
      extended.add(0, target);
    } else {
      extended.add(target);
    }
    spec.put("points", toList(extended.toArray(new float [extended.size()][])));
    if (atStart) {
      // The gates keep their place
      List<Map<String, Object>> openings = getOpenings(spec);
      for (Map<String, Object> opening : openings) {
        opening.put("at", Double.valueOf(AssistantFurnitureBuilder.getNumber(opening, "at", 0) + added));
      }
      setOpenings(spec, openings);
    }
    AssistantFenceBuilder.Result result = rebuild(piece, spec, tally, notes);
    notes.add("Extended by " + format(added / 100) + " m the fence " + describe(piece, result, spec));
  }

  /**
   * Returns how far the terrain goes from a point in a direction.
   */
  private float getTerrainReach(float [] point, float dx, float dy) {
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    float reach = 0;
    for (float distance = 10; distance <= 100000; distance += 10) {
      if (shapeCommands.getTerrainHeight(point [0] + dx * distance, point [1] + dy * distance) == null) {
        break;
      }
      reach = distance;
    }
    return reach;
  }

  /**
   * Returns how far the box around the items of the home goes from a point in a direction.
   */
  private float getModelReach(float [] point, float dx, float dy) {
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    List<float [][]> shapes = new ArrayList<float [][]>();
    for (Wall wall : this.home.getWalls()) {
      shapes.add(wall.getPoints());
    }
    for (Room room : this.home.getRooms()) {
      shapes.add(room.getPoints());
    }
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      shapes.add(piece.getPoints());
    }
    for (Polyline polyline : this.home.getPolylines()) {
      shapes.add(polyline.getPoints());
    }
    for (float [][] shape : shapes) {
      for (float [] corner : shape) {
        bounds [0] = Math.min(bounds [0], corner [0]);
        bounds [1] = Math.min(bounds [1], corner [1]);
        bounds [2] = Math.max(bounds [2], corner [0]);
        bounds [3] = Math.max(bounds [3], corner [1]);
      }
    }
    double reach = Double.MAX_VALUE;
    if (dx > 1E-4) {
      reach = Math.min(reach, (bounds [2] - point [0]) / dx);
    } else if (dx < -1E-4) {
      reach = Math.min(reach, (bounds [0] - point [0]) / dx);
    }
    if (dy > 1E-4) {
      reach = Math.min(reach, (bounds [3] - point [1]) / dy);
    } else if (dy < -1E-4) {
      reach = Math.min(reach, (bounds [1] - point [1]) / dy);
    }
    return reach == Double.MAX_VALUE ? 0 : (float)Math.max(0, reach);
  }

  private List<HomePieceOfFurniture> getFences(AssistantCommand command) {
    List<HomePieceOfFurniture> fences = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture && getSpec((HomePieceOfFurniture)item) != null) {
        fences.add((HomePieceOfFurniture)item);
      }
    }
    return fences;
  }

  private void deletePieces(List<HomePieceOfFurniture> pieces, int [] tally) {
    if (!pieces.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().deleteItems(pieces);
      tally [5] += pieces.size();
    }
  }

  private void joinFences(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> fences = getFences(command);
    if (fences.size() < 2) {
      throw new IOException("give the ids of 2 fences at least, built by add_fence: \"ids\":[\"F7\",\"F9\"]");
    }
    float tolerance = (float)command.getDouble("tolerance", 30);
    HomePieceOfFurniture first = fences.get(0);
    Map<String, Object> spec = getSpec(first);
    List<float []> path = new ArrayList<float []>(Arrays.asList(AssistantFenceBuilder.getPoints(spec)));
    List<Map<String, Object>> openings = getOpenings(spec);
    List<HomePieceOfFurniture> left = new ArrayList<HomePieceOfFurniture>(fences.subList(1, fences.size()));
    List<HomePieceOfFurniture> joined = new ArrayList<HomePieceOfFurniture>();
    boolean found = true;
    while (found && !left.isEmpty()) {
      found = false;
      for (HomePieceOfFurniture other : left) {
        Map<String, Object> otherSpec = getSpec(other);
        float [][] otherPoints = AssistantFenceBuilder.getPoints(otherSpec);
        double otherLength = AssistantFenceBuilder.getDistances(otherPoints, false) [otherPoints.length - 1];
        double pathLength = AssistantFenceBuilder.getDistances(path.toArray(new float [path.size()][]), false) [path.size() - 1];
        float [] start = path.get(0);
        float [] end = path.get(path.size() - 1);
        float [] otherStart = otherPoints [0];
        float [] otherEnd = otherPoints [otherPoints.length - 1];
        List<Map<String, Object>> otherOpenings = getOpenings(otherSpec);
        List<float []> added = new ArrayList<float []>(Arrays.asList(otherPoints));
        boolean reversed;
        boolean before;
        if (Math.hypot(end [0] - otherStart [0], end [1] - otherStart [1]) <= tolerance) {
          reversed = false;
          before = false;
        } else if (Math.hypot(end [0] - otherEnd [0], end [1] - otherEnd [1]) <= tolerance) {
          reversed = true;
          before = false;
        } else if (Math.hypot(start [0] - otherEnd [0], start [1] - otherEnd [1]) <= tolerance) {
          reversed = false;
          before = true;
        } else if (Math.hypot(start [0] - otherStart [0], start [1] - otherStart [1]) <= tolerance) {
          reversed = true;
          before = true;
        } else {
          continue;
        }
        if (reversed) {
          Collections.reverse(added);
          for (Map<String, Object> opening : otherOpenings) {
            opening.put("at", Double.valueOf(otherLength - AssistantFurnitureBuilder.getNumber(opening, "at", 0)));
          }
        }
        if (before) {
          // The shared post is the one of the first fence
          added.remove(added.size() - 1);
          double shift = AssistantFenceBuilder.getDistances(joinPoints(added, path), false) [added.size()];
          for (Map<String, Object> opening : openings) {
            opening.put("at", Double.valueOf(AssistantFurnitureBuilder.getNumber(opening, "at", 0) + shift));
          }
          path.addAll(0, added);
        } else {
          added.remove(0);
          for (Map<String, Object> opening : otherOpenings) {
            opening.put("at", Double.valueOf(AssistantFurnitureBuilder.getNumber(opening, "at", 0) + pathLength));
          }
          path.addAll(added);
        }
        openings.addAll(otherOpenings);
        joined.add(other);
        left.remove(other);
        found = true;
        break;
      }
    }
    if (joined.isEmpty()) {
      throw new IOException("the ends of these fences are more than " + format(tolerance) + " cm apart: \"tolerance\":N joins them from farther");
    }
    float [][] points = path.toArray(new float [path.size()][]);
    boolean closes = points.length >= 4 && Math.hypot(points [0][0] - points [points.length - 1][0],
        points [0][1] - points [points.length - 1][1]) <= tolerance;
    if (closes) {
      points = Arrays.copyOf(points, points.length - 1);
      spec.put("closed", Boolean.TRUE);
    }
    spec.put("points", toList(points));
    setOpenings(spec, openings);
    AssistantFenceBuilder.Result result = rebuild(first, spec, tally, notes);
    deletePieces(joined, tally);
    notes.add("Joined " + (joined.size() + 1) + " fences in " + describe(first, result, spec)
        + (left.isEmpty() ? "" : ". " + left.size() + " of the fences given didn't meet the others and stayed apart"));
  }

  private static float [][] joinPoints(List<float []> added, List<float []> path) {
    List<float []> all = new ArrayList<float []>(added);
    all.addAll(path);
    return all.toArray(new float [all.size()][]);
  }

  /**
   * Returns the description of the part of a fence between two distances along it.
   */
  private static Map<String, Object> getPart(Map<String, Object> spec, float [][] points, double [] distances, double from, double to) {
    List<float []> part = new ArrayList<float []>();
    part.add(AssistantFenceBuilder.getPoint(points, distances, from));
    for (int i = 0; i < points.length; i++) {
      if (distances [Math.min(i, distances.length - 1)] > from + 0.5 && distances [Math.min(i, distances.length - 1)] < to - 0.5
          && i < distances.length) {
        part.add(points [i]);
      }
    }
    part.add(AssistantFenceBuilder.getPoint(points, distances, to));
    Map<String, Object> partSpec = new LinkedHashMap<String, Object>(spec);
    partSpec.put("points", toList(part.toArray(new float [part.size()][])));
    partSpec.remove("closed");
    partSpec.remove("height_profile");
    List<Map<String, Object>> openings = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> opening : getOpenings(spec)) {
      float at = AssistantFurnitureBuilder.getNumber(opening, "at", 0);
      float width = AssistantFurnitureBuilder.getNumber(opening, "width", 100);
      if (at - width / 2 >= from - 0.5 && at + width / 2 <= to + 0.5) {
        opening.put("at", Double.valueOf(at - from));
        openings.add(opening);
      }
    }
    setOpenings(partSpec, openings);
    return partSpec;
  }

  private void splitFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture piece = getFence(command, "split_fence");
    Map<String, Object> spec = getSpec(piece);
    float [][] points = AssistantFenceBuilder.getPoints(spec);
    if (AssistantFenceBuilder.isClosed(spec, points)) {
      // A closed fence is opened at the cut
      List<float []> open = new ArrayList<float []>(Arrays.asList(points));
      open.add(points [0]);
      points = open.toArray(new float [open.size()][]);
    }
    double [] distances = AssistantFenceBuilder.getDistances(points, false);
    double length = distances [distances.length - 1];
    Object place = command.getParameters().get("at") != null ? command.getParameters().get("at") : command.getParameters().get("near");
    if (place == null) {
      throw new IOException("give where to cut: \"at\":N cm along the fence, or \"near\":[x,y]");
    }
    double at = getDistance(place, points, false, 0);
    if (at < 5 || at > length - 5) {
      throw new IOException("the cut at " + format(at) + " cm is at an end of a fence " + format(length) + " cm long");
    }
    Map<String, Object> firstSpec = getPart(spec, points, distances, 0, at);
    Map<String, Object> secondSpec = getPart(spec, points, distances, at, length);
    AssistantFenceBuilder.Result result = rebuild(piece, firstSpec, tally, notes);
    notes.add("Cut the fence at " + format(at) + " cm: " + describe(piece, result, firstSpec));
    addPiece(secondSpec, piece.getLevel(), piece.getName(), HomeItemTags.getTags(piece), tally, notes, null, "split_fence");
    notes.set(notes.size() - 1, "and " + notes.get(notes.size() - 1));
  }

  private void trimFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture piece = getFence(command, "trim_fence");
    Map<String, Object> spec = getSpec(piece);
    float [][] points = AssistantFenceBuilder.getPoints(spec);
    if (AssistantFenceBuilder.isClosed(spec, points)) {
      List<float []> open = new ArrayList<float []>(Arrays.asList(points));
      open.add(points [0]);
      points = open.toArray(new float [open.size()][]);
    }
    Object polygonValue = command.getParameters().get("polygon");
    float [][] polygon;
    if (polygonValue instanceof List) {
      polygon = AssistantFenceBuilder.getPoints(Collections.singletonMap("points", polygonValue));
    } else if (polygonValue != null) {
      polygon = getItemPoints(polygonValue.toString(), new boolean [1]);
    } else {
      throw new IOException("give \"polygon\":[[x,y],...] or the id of a room or of a closed line");
    }
    if (polygon.length < 3) {
      throw new IOException("\"polygon\" has 3 points at least");
    }
    boolean keepInside = !"outside".equalsIgnoreCase(command.getString("keep"));
    double [] distances = AssistantFenceBuilder.getDistances(points, false);
    double length = distances [distances.length - 1];
    // The places where the fence crosses the polygon
    TreeMap<Double, Boolean> cuts = new TreeMap<Double, Boolean>();
    cuts.put(0., Boolean.TRUE);
    cuts.put(length, Boolean.TRUE);
    for (int i = 0; i < points.length - 1; i++) {
      for (int k = 0; k < polygon.length; k++) {
        float [] next = polygon [(k + 1) % polygon.length];
        double [] crossing = intersect(points [i], points [i + 1], polygon [k], next);
        if (crossing != null) {
          cuts.put(distances [i] + crossing [0] * (distances [i + 1] - distances [i]), Boolean.TRUE);
        }
      }
    }
    List<double []> kept = new ArrayList<double []>();
    Double previous = null;
    for (Double cut : cuts.keySet()) {
      if (previous != null && cut - previous > 1) {
        float [] middle = AssistantFenceBuilder.getPoint(points, distances, (previous + cut) / 2);
        if (AssistantRoomFurnisher.contains(polygon, middle [0], middle [1]) == keepInside) {
          if (!kept.isEmpty() && Math.abs(kept.get(kept.size() - 1) [1] - previous) < 0.5) {
            kept.get(kept.size() - 1) [1] = cut;
          } else {
            kept.add(new double [] {previous, cut});
          }
        }
      }
      previous = cut;
    }
    if (kept.isEmpty()) {
      throw new IOException("nothing of the fence is " + (keepInside ? "inside" : "outside")
          + " the polygon: delete it, or keep the other side with \"keep\":\"" + (keepInside ? "outside" : "inside") + "\"");
    } else if (kept.size() == 1 && kept.get(0) [0] < 0.5 && kept.get(0) [1] > length - 0.5) {
      notes.add("The fence " + this.executor.getId(piece) + " is all " + (keepInside ? "inside" : "outside") + " the polygon: nothing to trim");
      return;
    }
    for (int i = 0; i < kept.size(); i++) {
      Map<String, Object> partSpec = getPart(spec, points, distances, kept.get(i) [0], kept.get(i) [1]);
      if (i == 0) {
        AssistantFenceBuilder.Result result = rebuild(piece, partSpec, tally, notes);
        notes.add("Trimmed the fence: " + describe(piece, result, partSpec));
      } else {
        addPiece(partSpec, piece.getLevel(), piece.getName(), HomeItemTags.getTags(piece), tally, notes, null, "trim_fence");
        notes.set(notes.size() - 1, "and " + notes.get(notes.size() - 1));
      }
    }
  }

  /**
   * Returns the ratios along two segments of the point where they cross, or <code>null</code>.
   */
  private static double [] intersect(float [] a1, float [] a2, float [] b1, float [] b2) {
    double dx1 = a2 [0] - a1 [0];
    double dy1 = a2 [1] - a1 [1];
    double dx2 = b2 [0] - b1 [0];
    double dy2 = b2 [1] - b1 [1];
    double determinant = dx1 * dy2 - dy1 * dx2;
    if (Math.abs(determinant) < 1E-9) {
      return null;
    }
    double ratio1 = ((b1 [0] - a1 [0]) * dy2 - (b1 [1] - a1 [1]) * dx2) / determinant;
    double ratio2 = ((b1 [0] - a1 [0]) * dy1 - (b1 [1] - a1 [1]) * dx1) / determinant;
    return ratio1 >= 0 && ratio1 <= 1 && ratio2 >= 0 && ratio2 <= 1 ? new double [] {ratio1, ratio2} : null;
  }

  private void offsetFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    HomePieceOfFurniture piece = getFence(command, "offset_fence");
    Map<String, Object> spec = getSpec(piece);
    float [][] points = AssistantFenceBuilder.getPoints(spec);
    boolean closed = AssistantFenceBuilder.isClosed(spec, points);
    double distance = command.getDouble("distance", command.getDouble("offset", Double.NaN));
    if (Double.isNaN(distance) || distance == 0) {
      throw new IOException("give \"distance\":N cm, positive to the inside of a closed fence or to the left of an open one");
    }
    float sign = closed ? (getSignedArea(points) > 0 ? -1 : 1) : -1;
    spec.put("points", toList(offsetLine(points, closed, (float)distance * sign)));
    if (command.getBoolean("copy", false)) {
      addPiece(spec, piece.getLevel(), command.getString("name") != null ? command.getString("name") : piece.getName(),
          HomeItemTags.getTags(piece), tally, notes, null, "offset_fence");
      notes.set(notes.size() - 1, "Added " + format(distance) + " cm from " + this.executor.getId(piece) + " the fence "
          + notes.get(notes.size() - 1));
    } else {
      AssistantFenceBuilder.Result result = rebuild(piece, spec, tally, notes);
      notes.add("Moved by " + format(distance) + " cm the fence " + describe(piece, result, spec));
    }
  }

  private void convertToFence(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture && getSpec((HomePieceOfFurniture)item) == null
          && !HomeTerrain.isTerrain((HomePieceOfFurniture)item)) {
        pieces.add((HomePieceOfFurniture)item);
      }
    }
    if (pieces.size() < 2) {
      throw new IOException("give 2 pieces of furniture at least, with \"ids\" or \"where\": the fence goes through their centers");
    }
    // A chain from an end: each piece then the closest one left
    HomePieceOfFurniture start = pieces.get(0);
    double farthest = -1;
    for (HomePieceOfFurniture piece : pieces) {
      double sum = 0;
      for (HomePieceOfFurniture other : pieces) {
        sum = Math.max(sum, Math.hypot(piece.getX() - other.getX(), piece.getY() - other.getY()));
      }
      if (sum > farthest) {
        farthest = sum;
        start = piece;
      }
    }
    List<HomePieceOfFurniture> chain = new ArrayList<HomePieceOfFurniture>();
    List<HomePieceOfFurniture> left = new ArrayList<HomePieceOfFurniture>(pieces);
    chain.add(start);
    left.remove(start);
    while (!left.isEmpty()) {
      HomePieceOfFurniture last = chain.get(chain.size() - 1);
      HomePieceOfFurniture closest = left.get(0);
      for (HomePieceOfFurniture piece : left) {
        if (Math.hypot(piece.getX() - last.getX(), piece.getY() - last.getY())
            < Math.hypot(closest.getX() - last.getX(), closest.getY() - last.getY())) {
          closest = piece;
        }
      }
      chain.add(closest);
      left.remove(closest);
    }
    // The ends of the first and of the last pieces, then the centers that turn the line
    List<float []> path = new ArrayList<float []>();
    float height = 0;
    double elevation = 0;
    Level level = start.getLevel();
    for (HomePieceOfFurniture piece : chain) {
      path.add(new float [] {piece.getX(), piece.getY()});
      height = Math.max(height, piece.getHeight());
      float ground = this.executor.getShapeCommands().getStandingHeight(piece.getX(), piece.getY())
          - (piece.getLevel() != null ? piece.getLevel().getElevation() : 0);
      elevation += (piece.getElevation() - ground) / chain.size();
    }
    for (int end = 0; end < 2; end++) {
      HomePieceOfFurniture piece = end == 0 ? chain.get(0) : chain.get(chain.size() - 1);
      float [] center = end == 0 ? path.get(0) : path.get(path.size() - 1);
      float [] neighbor = end == 0 ? path.get(1) : path.get(path.size() - 2);
      double distance = Math.hypot(center [0] - neighbor [0], center [1] - neighbor [1]);
      if (distance > 1) {
        float half = Math.max(piece.getWidth(), piece.getDepth()) / 2;
        float [] tip = {(float)(center [0] + (center [0] - neighbor [0]) / distance * half),
                        (float)(center [1] + (center [1] - neighbor [1]) / distance * half)};
        path.set(end == 0 ? 0 : path.size() - 1, tip);
      }
    }
    for (int i = path.size() - 2; i >= 1; i--) {
      if (Line2D.ptSegDist(path.get(i - 1) [0], path.get(i - 1) [1], path.get(i + 1) [0], path.get(i + 1) [1],
              path.get(i) [0], path.get(i) [1]) < 3) {
        path.remove(i);
      }
    }
    Map<String, Object> spec = new LinkedHashMap<String, Object>(command.getParameters());
    for (String key : new String [] {"action", "id", "ids", "where", "names", "target", "keep"}) {
      spec.remove(key);
    }
    spec.put("points", toList(path.toArray(new float [path.size()][])));
    if (spec.get("height") == null && spec.get("height_profile") == null) {
      spec.put("height", Double.valueOf(Math.round(height)));
    }
    if (Math.abs(elevation) > 5 && spec.get("elevation") == null) {
      spec.put("elevation", Double.valueOf(Math.round(elevation)));
    }
    if (AssistantFenceBuilder.getStyle(command.getString("style")) == null) {
      throw new IOException("unknown \"style\" \"" + command.getString("style") + "\". The styles: "
          + AssistantFenceBuilder.describeStyles(false));
    }
    Map<String, Object> parameters = new LinkedHashMap<String, Object>(spec);
    HomePieceOfFurniture fence = addPiece(spec, level, command.getString("name"), command.getParameters().get("tags"), tally, notes,
        parameters, "convert_to_fence");
    boolean keep = command.getBoolean("keep", false);
    if (!keep) {
      deletePieces(chain, tally);
    }
    notes.set(notes.size() - (notes.get(notes.size() - 1).startsWith("Warning") ? 2 : 1),
        "Replaced " + chain.size() + " pieces by the fence " + describe(fence,
            AssistantFenceBuilder.build(getSpec(fence), getSite(level, notes)), getSpec(fence))
        + (keep ? " (the pieces were kept)" : ""));
  }

  // ---- Reports and checks ----

  private static String getGroundSignature(AssistantFenceBuilder.Result result) {
    StringBuilder text = new StringBuilder();
    for (float [] post : result.postPlaces) {
      text.append(text.length() > 0 ? "," : "").append(Math.round(post [2]));
    }
    return text.toString();
  }

  private static boolean isBuilding(Room room, java.util.Collection<Wall> walls) {
    if (!room.isCeilingVisible()) {
      return false;
    }
    float [][] points = room.getPoints();
    int count = 0;
    for (Wall wall : walls) {
      if (wall.getLevel() != room.getLevel()) {
        continue;
      }
      float x = (wall.getXStart() + wall.getXEnd()) / 2;
      float y = (wall.getYStart() + wall.getYEnd()) / 2;
      for (int i = 0; i < points.length; i++) {
        float [] next = points [(i + 1) % points.length];
        if (Line2D.ptSegDist(points [i][0], points [i][1], next [0], next [1], x, y) < 25) {
          count++;
          break;
        }
      }
    }
    return count >= 3;
  }

  /**
   * Returns <code>true</code> if a piece stands where the leaf of a gate goes through the point (x, y): the trunk of a tree
   * and not the leaves above the gate, a shrub where its leaves are, and any other piece between the ground and the top of the gate.
   */
  private boolean isGateObstacle(HomePieceOfFurniture other, Level level, float x, float y, float gateGround, float gateHeight) {
    String structure = other.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY);
    if (!other.isVisible() || other.getLevel() != level || HomeTerrain.isTerrain(other) || "slab".equals(structure)
        || "roof".equals(structure) || other.getProperty(AssistantGardenCommands.GARDEN_PROPERTY) != null) {
      return false;
    }
    Map<String, Object> plant = AssistantPlantCommands.getSpec(other);
    if (plant != null) {
      if (HomeItemTags.getTags(other).contains("removed")) {
        return false;
      }
      float plantX = other.getX();
      float plantY = other.getY();
      float crownWidth = AssistantFurnitureBuilder.getNumber(plant, "crown_w", other.getWidth());
      float crownDepth = AssistantFurnitureBuilder.getNumber(plant, "crown_d", other.getDepth());
      float crownBase = AssistantFurnitureBuilder.getNumber(plant, "crown_base", 0);
      float trunk = AssistantFurnitureBuilder.getNumber(plant, "trunk_dbh", 10);
      // Leaves that start above the gate let it open under them
      float radius = crownBase >= gateHeight - 20 ? trunk / 2 + 5 : Math.min(crownWidth, crownDepth) / 2 * 0.85f;
      return Math.hypot(x - plantX, y - plantY) <= radius;
    }
    // What lies on the ground or hangs above the gate doesn't stop it
    float bottom = other.getElevation();
    float top = bottom + other.getHeight();
    return bottom < gateGround + gateHeight - 5 && top > gateGround + 12 && other.containsPoint(x, y, 2);
  }

  /**
   * Returns what is wrong with the fences of the home: one that crosses a building or a driveway, a gate
   * that can't open, a pool fence a child gets through, a fence beyond the lot, posts the ground left.
   */
  List<HomeDiagnostics.Issue> check() {
    List<HomeDiagnostics.Issue> issues = new ArrayList<HomeDiagnostics.Issue>();
    List<float [][]> lots = new ArrayList<float [][]>();
    for (Polyline polyline : this.home.getPolylines()) {
      if ("property".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY)) && polyline.isClosedPath()
          && polyline.getPointCount() >= 3) {
        lots.add(polyline.getPoints());
      }
    }
    List<Room> buildings = new ArrayList<Room>();
    for (Room room : this.home.getRooms()) {
      if (isBuilding(room, this.home.getWalls())) {
        buildings.add(room);
      }
    }
    int listed = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      Map<String, Object> spec = getSpec(piece);
      if (spec == null || !piece.isVisible() || listed >= 40) {
        continue;
      }
      listed++;
      String label = this.executor.getId(piece) + " \"" + piece.getName() + "\"";
      try {
        float [][] points = AssistantFenceBuilder.getPoints(spec);
        boolean closed = AssistantFenceBuilder.isClosed(spec, points);
        double [] distances = AssistantFenceBuilder.getDistances(points, closed);
        double length = distances [distances.length - 1];
        List<Map<String, Object>> openings = getOpenings(spec);
        AssistantFenceBuilder.Result result = AssistantFenceBuilder.build(spec, getSite(piece.getLevel(), null));
        boolean sunk = result.style.name.equals("ha_ha") || result.style.name.equals("retaining_wall");
        // The fence of a neighbor stands on the lot of the neighbor
        String owner = spec.get("owner") != null ? spec.get("owner").toString().trim().toLowerCase(Locale.ENGLISH) : "";
        boolean ownedByOther = owner.length() > 0 && !Arrays.asList("me", "mine", "us", "ours", "self", "owner", "home", "homeowner",
            "subject", "shared").contains(owner);
        // A railing stands on a deck or on a structure, not on the ground
        boolean onStructure = Math.abs(AssistantFurnitureBuilder.getNumber(spec, "elevation", 0)) > 0.5f;
        // Along the fence, out of its gates
        Wall crossedWall = null;
        Room crossedRoom = null;
        HomePieceOfFurniture driveway = null;
        double beyond = 0;
        for (double distance = 0; distance <= length; distance += 20) {
          boolean inOpening = false;
          for (Map<String, Object> opening : openings) {
            float at = AssistantFurnitureBuilder.getNumber(opening, "at", 0);
            float width = AssistantFurnitureBuilder.getNumber(opening, "width", 100);
            inOpening |= distance >= at - width / 2 && distance <= at + width / 2;
          }
          float [] point = AssistantFenceBuilder.getPoint(points, distances, distance);
          if (!lots.isEmpty() && !ownedByOther) {
            double nearest = Double.MAX_VALUE;
            boolean inside = false;
            for (float [][] lot : lots) {
              inside |= AssistantRoomFurnisher.contains(lot, point [0], point [1]);
              for (int i = 0; i < lot.length; i++) {
                float [] next = lot [(i + 1) % lot.length];
                nearest = Math.min(nearest, Line2D.ptSegDist(lot [i][0], lot [i][1], next [0], next [1], point [0], point [1]));
              }
            }
            if (!inside) {
              beyond = Math.max(beyond, nearest);
            }
          }
          if (inOpening) {
            continue;
          }
          for (Wall wall : this.home.getWalls()) {
            if (crossedWall == null && wall.getLevel() == piece.getLevel() && wall.containsPoint(point [0], point [1], 0)
                && distance > 25 && distance < length - 25) {
              crossedWall = wall;
            }
          }
          for (Room room : buildings) {
            if (crossedRoom == null && room.getLevel() == piece.getLevel() && room.containsPoint(point [0], point [1], 0)) {
              boolean nearSide = false;
              float [][] roomPoints = room.getPoints();
              for (int i = 0; i < roomPoints.length; i++) {
                float [] next = roomPoints [(i + 1) % roomPoints.length];
                nearSide |= Line2D.ptSegDist(roomPoints [i][0], roomPoints [i][1], next [0], next [1], point [0], point [1]) < 30;
              }
              if (!nearSide) {
                crossedRoom = room;
              }
            }
          }
          for (HomePieceOfFurniture other : this.home.getFurniture()) {
            if (driveway == null && other != piece && other.isVisible() && other.getName() != null
                && "slab".equals(other.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY))
                && other.getName().toLowerCase(Locale.ENGLISH).matches(".*(drive|road|street|parking).*")
                && other.containsPoint(point [0], point [1], 0)) {
              driveway = other;
            }
          }
        }
        if (!sunk && crossedWall != null) {
          issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.FENCE_THROUGH_BUILDING, "The fence " + label + " crosses the wall "
              + this.executor.getId(crossedWall) + ": trim_fence cuts it, or end it at the wall with \"between\"", piece, crossedWall));
        } else if (!sunk && crossedRoom != null) {
          issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.FENCE_THROUGH_BUILDING, "The fence " + label + " runs inside the room "
              + this.executor.getId(crossedRoom) + (crossedRoom.getName() != null ? " \"" + crossedRoom.getName() + "\"" : ""), piece, crossedRoom));
        }
        if (driveway != null) {
          issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.FENCE_ON_DRIVEWAY, "The fence " + label + " crosses the driveway "
              + this.executor.getId(driveway) + " \"" + driveway.getName() + "\" without a gate: add_gate opens one", piece, driveway));
        }
        if (beyond > 5) {
          issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.FENCE_BEYOND_PROPERTY_LINE, "The fence " + label + " stands up to "
              + format(beyond) + " cm beyond the property line", piece, null));
        }
        for (String violation : result.violations) {
          issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.POOL_FENCE, "The pool fence " + label + ": " + violation, piece, null));
        }
        // The posts and the ground
        String built = piece.getProperty(FENCE_GROUND_PROPERTY);
        if (built != null && built.length() > 0 && !onStructure) {
          String [] heights = built.split(",");
          float worst = 0;
          for (int i = 0; i < heights.length && i < result.postPlaces.size(); i++) {
            try {
              float difference = Float.parseFloat(heights [i]) - result.postPlaces.get(i) [2];
              if (Math.abs(difference) > Math.abs(worst)) {
                worst = difference;
              }
            } catch (NumberFormatException ex) {
              // Not compared
            }
          }
          if (Math.abs(worst) > 8) {
            issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.FENCE_OFF_GROUND, "The ground changed under the fence " + label
                + ": a post is " + format(Math.abs(worst)) + " cm " + (worst > 0 ? "above it" : "under it")
                + ". {\"action\":\"modify_fence\",\"id\":\"" + this.executor.getId(piece) + "\",\"rebuild\":true} builds it again on the ground",
                piece, null));
          }
        }
        // The room the gates need to open
        float side = spec.get("side") != null && spec.get("side").toString().toLowerCase(Locale.ENGLISH).startsWith("r") ? -1 : 1;
        int number = 0;
        List<Map<String, Object>> sorted = new ArrayList<Map<String, Object>>(openings);
        Collections.sort(sorted, new java.util.Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> gate1, Map<String, Object> gate2) {
              return Float.compare(AssistantFurnitureBuilder.getNumber(gate1, "at", 0), AssistantFurnitureBuilder.getNumber(gate2, "at", 0));
            }
          });
        for (Map<String, Object> opening : sorted) {
          number++;
          String type = opening.get("type") != null ? opening.get("type").toString().toLowerCase(Locale.ENGLISH) : "single_swing";
          boolean twoLeaves = type.startsWith("double") || type.equals("drive_arch") || type.equals("bifold");
          if (!(type.contains("swing") || type.contains("arch") || type.equals("arbor_gate") || type.equals("bifold")
                || type.equals("single") || type.equals("double") || type.contains("kissing"))) {
            continue;
          }
          float at = AssistantFurnitureBuilder.getNumber(opening, "at", 0);
          float width = AssistantFurnitureBuilder.getNumber(opening, "width", 100);
          float [] start = AssistantFenceBuilder.getPoint(points, distances, at - width / 2);
          float [] end = AssistantFenceBuilder.getPoint(points, distances, at + width / 2);
          double gateLength = Math.hypot(end [0] - start [0], end [1] - start [1]);
          if (gateLength < 1) {
            continue;
          }
          float dx = (float)((end [0] - start [0]) / gateLength);
          float dy = (float)((end [1] - start [1]) / gateLength);
          String swing = opening.get("swing") != null ? opening.get("swing").toString().toLowerCase(Locale.ENGLISH) : "in";
          float turn = (swing.startsWith("out") ? 1 : -1) * side;
          boolean hingeAtEnd = "end".equals(String.valueOf(opening.get("hinge")));
          float leaf = (float)(twoLeaves ? gateLength / 2 : gateLength);
          Selectable obstacle = null;
          AssistantFenceBuilder.Site site = getSite(piece.getLevel(), null);
          float gateGround = Math.max(site.getGroundHeight(start [0], start [1]), site.getGroundHeight(end [0], end [1]));
          float gateHeight = Math.min(200, AssistantFurnitureBuilder.getNumber(opening, "height", result.height));
          for (int leafIndex = 0; leafIndex < (twoLeaves ? 2 : 1) && obstacle == null; leafIndex++) {
            boolean fromEnd = twoLeaves ? leafIndex == 1 : hingeAtEnd;
            float [] hinge = fromEnd ? end : start;
            float sign = fromEnd ? -1 : 1;
            for (int angle = 20; angle <= 90 && obstacle == null; angle += 10) {
              double radians = Math.toRadians(angle) * turn * sign;
              double leafX = sign * (dx * Math.cos(radians) - dy * Math.sin(radians));
              double leafY = sign * (dx * Math.sin(radians) + dy * Math.cos(radians));
              for (float reach = leaf * 0.4f; reach <= leaf + 0.5f && obstacle == null; reach += leaf * 0.3f) {
                float x = (float)(hinge [0] + leafX * reach);
                float y = (float)(hinge [1] + leafY * reach);
                for (Wall wall : this.home.getWalls()) {
                  if (obstacle == null && wall.getLevel() == piece.getLevel() && wall.containsPoint(x, y, 2)) {
                    obstacle = wall;
                  }
                }
                for (HomePieceOfFurniture other : this.home.getFurniture()) {
                  if (obstacle == null && other != piece && isGateObstacle(other, piece.getLevel(), x, y, gateGround, gateHeight)) {
                    obstacle = other;
                  }
                }
              }
            }
          }
          if (obstacle != null) {
            issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.GATE_BLOCKED, "The gate " + number + " of the fence " + label
                + " hits " + this.executor.getId(obstacle)
                + (obstacle instanceof HomePieceOfFurniture ? " \"" + ((HomePieceOfFurniture)obstacle).getName() + "\"" : "")
                + " when it swings " + (swing.startsWith("out") ? "out" : "in") + ": modify_gate changes its \"swing\", its \"hinge\" or its place",
                piece, obstacle));
          }
        }
      } catch (IOException ex) {
        // A fence that can't be built is reported by its commands
      }
    }
    checkPoolBarriers(issues);
    return issues;
  }

  /**
   * Adds an issue for each pool in the ground that no closed barrier surrounds: fences of the style <code>pool_code</code>
   * with their gates, and the walls of the home they end at.
   */
  private void checkPoolBarriers(List<HomeDiagnostics.Issue> issues) {
    List<HomePieceOfFurniture> pools = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if ("pool".equals(piece.getProperty(AssistantGardenCommands.GARDEN_PROPERTY)) && piece.isVisible()) {
        String outdoor = piece.getProperty(AssistantOutdoorCommands.OUTDOOR_SPEC_PROPERTY);
        boolean above = false;
        try {
          Object parsed = outdoor != null ? JSONParser.parse(outdoor) : null;
          if (parsed instanceof Map) {
            Map<?, ?> map = (Map<?, ?>)parsed;
            above = map.get("type") != null && map.get("type").toString().trim().toLowerCase(Locale.ENGLISH).startsWith("above")
                || Boolean.TRUE.equals(map.get("above_ground"));
            // The wall of a pool above the ground is a barrier when it is high enough
            above &= AssistantFurnitureBuilder.getNumber(map, "height", 130) >= 122;
          }
        } catch (IllegalArgumentException ex) {
          // Checked as a pool in the ground
        }
        if (!above && pools.size() < 20) {
          pools.add(piece);
        }
      }
    }
    if (pools.isEmpty()) {
      return;
    }
    // The barriers: lines of points 5 cm apart
    List<float [][]> lines = new ArrayList<float [][]>();
    List<Level> lineLevels = new ArrayList<Level>();
    List<float []> ends = new ArrayList<float []>();
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      Map<String, Object> spec = getSpec(piece);
      if (spec == null || !piece.isVisible()) {
        continue;
      }
      try {
        AssistantFenceBuilder.Style style = AssistantFenceBuilder.getStyle(String.valueOf(spec.get("style")));
        if (style == null || !style.name.equals("pool_code")) {
          continue;
        }
        float [][] points = AssistantFenceBuilder.getPoints(spec);
        boolean closed = AssistantFenceBuilder.isClosed(spec, points);
        double [] distances = AssistantFenceBuilder.getDistances(points, closed);
        double length = distances [distances.length - 1];
        // The fence without its openings left without gate
        List<double []> gaps = new ArrayList<double []>();
        for (Map<String, Object> opening : getOpenings(spec)) {
          String type = opening.get("type") != null ? opening.get("type").toString().toLowerCase(Locale.ENGLISH) : "";
          if (type.equals("gap") || type.equals("none") || type.equals("opening")) {
            float at = AssistantFurnitureBuilder.getNumber(opening, "at", 0);
            float width = AssistantFurnitureBuilder.getNumber(opening, "width", 100);
            gaps.add(new double [] {at - width / 2, at + width / 2});
          }
        }
        List<float []> run = new ArrayList<float []>();
        for (double distance = 0; ; distance += 5) {
          double place = Math.min(distance, length);
          boolean inGap = false;
          for (double [] gap : gaps) {
            inGap |= place > gap [0] && place < gap [1];
          }
          if (!inGap) {
            run.add(AssistantFenceBuilder.getPoint(points, distances, place));
          }
          if (distance >= length) {
            break;
          }
        }
        lines.add(run.toArray(new float [run.size()][]));
        lineLevels.add(piece.getLevel());
        if (!closed) {
          ends.add(points [0]);
          ends.add(points [points.length - 1]);
        }
      } catch (IOException ex) {
        // Not a barrier
      }
    }
    for (HomePieceOfFurniture pool : pools) {
      String label = this.executor.getId(pool) + " \"" + pool.getName() + "\"";
      String advice = ": a fence with \"style\":\"pool_code\" closed around it, or that ends at the walls of the house, "
          + "with gates that have \"self_closing\":true, keeps children away from the water";
      List<float [][]> barriers = new ArrayList<float [][]>();
      for (int i = 0; i < lines.size(); i++) {
        if (lineLevels.get(i) == pool.getLevel()) {
          barriers.add(lines.get(i));
        }
      }
      if (barriers.isEmpty()) {
        issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.POOL_FENCE, "The pool " + label + " has no barrier" + advice, pool, null));
        continue;
      }
      for (Wall wall : this.home.getWalls()) {
        if (wall.getLevel() == pool.getLevel()) {
          // The sides of the wall
          float [][] sides = wall.getPoints();
          List<float []> outline = new ArrayList<float []>();
          for (int i = 0; i < sides.length; i++) {
            float [] point = sides [i];
            float [] next = sides [(i + 1) % sides.length];
            int steps = Math.max(1, (int)Math.ceil(Math.hypot(next [0] - point [0], next [1] - point [1]) / 5));
            for (int step = 0; step < steps; step++) {
              outline.add(new float [] {point [0] + (next [0] - point [0]) * step / steps, point [1] + (next [1] - point [1]) * step / steps});
            }
          }
          barriers.add(outline.toArray(new float [outline.size()][]));
        }
      }
      float minX = pool.getX();
      float minY = pool.getY();
      float maxX = minX;
      float maxY = minY;
      for (float [][] barrier : barriers) {
        for (float [] point : barrier) {
          minX = Math.min(minX, point [0]);
          minY = Math.min(minY, point [1]);
          maxX = Math.max(maxX, point [0]);
          maxY = Math.max(maxY, point [1]);
        }
      }
      // Water that spreads from the pool on a grid: the pool is enclosed if it doesn't reach the edge
      float cell = Math.max(20, Math.max(maxX - minX, maxY - minY) / 500);
      int columns = (int)Math.ceil((maxX - minX) / cell) + 5;
      int rows = (int)Math.ceil((maxY - minY) / cell) + 5;
      float originX = minX - 2 * cell;
      float originY = minY - 2 * cell;
      boolean [] blocked = new boolean [columns * rows];
      for (float [][] barrier : barriers) {
        for (float [] point : barrier) {
          blocked [(int)((point [1] - originY) / cell) * columns + (int)((point [0] - originX) / cell)] = true;
        }
      }
      // A fence that stops a few cm from a wall is closed
      for (float [] end : ends) {
        int column = (int)((end [0] - originX) / cell);
        int row = (int)((end [1] - originY) / cell);
        if (column >= 1 && row >= 1 && column < columns - 1 && row < rows - 1) {
          for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
              blocked [(row + dy) * columns + column + dx] = true;
            }
          }
        }
      }
      boolean [] reached = new boolean [columns * rows];
      java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<Integer>();
      int startCell = (int)((pool.getY() - originY) / cell) * columns + (int)((pool.getX() - originX) / cell);
      queue.add(Integer.valueOf(startCell));
      reached [startCell] = true;
      boolean escaped = false;
      while (!queue.isEmpty()) {
        int current = queue.poll().intValue();
        int column = current % columns;
        int row = current / columns;
        if (column == 0 || row == 0 || column == columns - 1 || row == rows - 1) {
          escaped = true;
          break;
        }
        for (int neighbor : new int [] {current - 1, current + 1, current - columns, current + columns}) {
          if (!reached [neighbor] && !blocked [neighbor]) {
            reached [neighbor] = true;
            queue.add(Integer.valueOf(neighbor));
          }
        }
      }
      if (escaped) {
        issues.add(new HomeDiagnostics.Issue(HomeDiagnostics.Type.POOL_FENCE, "The pool " + label
            + " has no closed barrier: its pool_code fence leaves a way to the water" + advice, pool, null));
      }
    }
  }

  /**
   * Returns the table of the fences and of what they need, or the one of their gates.
   */
  String report(boolean gatesOnly) {
    StringBuilder text = new StringBuilder(gatesOnly ? "Gates of the fences."
        : "Fences and their quantities. The line of a fence includes its gates; posts, panels, concrete and fasteners count the fenced part.");
    Map<String, double []> byStyle = new LinkedHashMap<String, double []>();
    Map<String, double []> byOwner = new LinkedHashMap<String, double []>();
    double [] totals = new double [8];
    int count = 0;
    int gateCount = 0;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      Map<String, Object> spec = getSpec(piece);
      if (spec == null || Boolean.FALSE.equals(spec.get("report"))) {
        continue;
      }
      AssistantFenceBuilder.Result result;
      try {
        result = AssistantFenceBuilder.build(spec, getSite(piece.getLevel(), null));
      } catch (IOException ex) {
        text.append('\n').append(this.executor.getId(piece)).append(": can't be read (").append(ex.getMessage()).append(')');
        continue;
      }
      count++;
      double length = (result.length - result.gateLength) / 100;
      if (gatesOnly) {
        for (int i = 0; i < result.gates.size(); i++) {
          gateCount++;
          text.append('\n').append(this.executor.getId(piece)).append(" \"").append(piece.getName()).append("\" gate ").append(i + 1)
              .append(": ").append(result.gates.get(i));
        }
        continue;
      }
      text.append('\n').append(this.executor.getId(piece)).append(" \"").append(piece.getName()).append("\": ")
          .append(result.style.name).append(", ").append(format(result.height)).append(" cm high, ")
          .append(format(result.length / 100)).append(" m of line (").append(format(result.length / 100 / 0.3048)).append(" ft): ")
          .append(format(length)).append(" m of fence")
          .append(result.gateLength >= 0.5 ? ", " + format(result.gateLength / 100) + " m of gates" : "").append(", ")
          .append(result.getPostCount()).append(" posts (").append(result.linePosts).append(" line, ")
          .append(result.terminalPosts).append(" end or corner, ").append(result.gatePosts).append(" gate), ")
          .append(result.spans).append(" panels, ").append(result.gates.size()).append(result.gates.size() == 1 ? " gate" : " gates");
      if (result.getConcreteBags() > 0) {
        text.append(", ").append(result.getConcreteBags()).append(" bags of concrete (50 lb)");
      }
      if (result.getFasteners() > 0) {
        text.append(", about ").append(result.getFasteners()).append(" fasteners");
      }
      String owner = spec.get("owner") != null ? spec.get("owner").toString() : null;
      if (owner != null) {
        text.append(", owner ").append(owner);
        if (spec.get("ownership_side") != null) {
          text.append(" (").append(spec.get("ownership_side")).append(" side)");
        }
      }
      double [] values = {length, result.getPostCount(), result.spans, result.gates.size(), result.getConcreteBags(), result.getFasteners(),
          result.length / 100, result.gateLength / 100};
      for (Map<String, double []> sums : Arrays.asList(byStyle, byOwner)) {
        String key = sums == byStyle ? result.style.name : owner != null ? owner : "no owner given";
        double [] sum = sums.get(key);
        if (sum == null) {
          sums.put(key, sum = new double [8]);
        }
        for (int i = 0; i < 8; i++) {
          sum [i] += values [i];
        }
      }
      for (int i = 0; i < 8; i++) {
        totals [i] += values [i];
      }
    }
    if (count == 0) {
      return "The home has no fence built by add_fence.";
    } else if (gatesOnly) {
      return gateCount == 0 ? "The fences of the home have no gate: add_gate opens one." : text.toString();
    }
    for (Map<String, double []> sums : Arrays.asList(byStyle, byOwner)) {
      if (sums == byOwner && sums.size() == 1 && sums.containsKey("no owner given")) {
        continue;
      }
      text.append(sums == byStyle ? "\nBy style: " : "\nBy owner: ");
      boolean first = true;
      for (Map.Entry<String, double []> entry : sums.entrySet()) {
        text.append(first ? "" : "; ").append(entry.getKey()).append(' ').append(format(entry.getValue() [6])).append(" m of line (")
            .append(format(entry.getValue() [6] / 0.3048)).append(" ft)");
        if (entry.getValue() [7] >= 0.005) {
          text.append(", ").append(format(entry.getValue() [0])).append(" m of fence");
        }
        first = false;
      }
    }
    text.append("\nTotal: ").append(format(totals [6])).append(" m of line (").append(format(totals [6] / 0.3048)).append(" ft): ")
        .append(format(totals [0])).append(" m of fence and ").append(format(totals [7])).append(" m of gates, ")
        .append((int)totals [1]).append(" posts, ").append((int)totals [2]).append(" panels, ").append((int)totals [3])
        .append(" gates, ").append((int)totals [4]).append(" bags of concrete, about ").append((int)totals [5]).append(" fasteners");
    return text.toString();
  }
}
