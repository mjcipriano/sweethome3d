/*
 * AssistantOutdoorCommands.java 23 nov. 2026
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

import java.awt.geom.Area;
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

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;

/**
 * The commands that build what stands around a house: decks, patios, pergolas, sheds, pools, driveways,
 * drainage, utilities and raised beds, and the buildings, the cars and the people that give its context.
 */
class AssistantOutdoorCommands {
  /** The property of a piece that keeps the description of the structure it shows, in JSON. */
  static final String OUTDOOR_SPEC_PROPERTY = "assistantOutdoor";
  /** The property of a home that keeps the detail of the buildings around it. */
  static final String CONTEXT_DETAIL_PROPERTY = "assistantContextDetail";

  private static final List<String> COMMON_KEYS = Arrays.asList("action", "x", "y", "angle", "name", "tags", "level", "color",
      "width", "depth", "height", "offset", "owner");
  private static final List<String> UTILITIES = Arrays.asList("ac_condenser", "generator", "meter", "propane_tank",
      "septic_marker", "well_marker", "utility_pole", "transformer_box", "mailbox", "street_light", "street_sign", "fire_hydrant");
  private static final List<String> DRAINAGE = Arrays.asList("downspout_extension", "french_drain", "catch_basin",
      "dry_creek_bed", "culvert");

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantOutdoorCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    String place = "\"x\":N,\"y\":N,\"angle\":N";
    return "  {\"action\":\"add_deck\",\"points\":[[x,y],...],\"height\":60}  "
        + "(a deck over a polygon, or " + place + ",\"width\":N,\"depth\":N: its boards at \"height\" cm above the ground of its "
        + "highest side, on posts and beams. \"decking\":\"wood|composite\" with \"direction\" in degrees and \"color\", "
        + "\"skirting\":\"lattice|horizontal_slat|solid|none\", \"railing\":\"<a style of fence>\" (or {\"style\",\"height\"...}, "
        + "built as a fence around it, open at the stairs), \"stairs\":{\"edge\":N,\"width\":120,\"at\":0.5} down to the ground "
        + "from the edge N (1 = from the first point to the second one), \"benches\":[N,...] along edges)\n"
        + "  {\"action\":\"add_patio\",\"points\":[[x,y],...],\"pattern\":\"running_bond|herringbone|basket|random_ashlar|flagstone|concrete\"}  "
        + "(or add_pavers: a paved surface laid on the ground, with \"color\", \"thickness\" (6), "
        + "\"border\":{\"width\":20,\"color\":\"#RRGGBB\"})\n"
        + "  {\"action\":\"add_pergola\"," + place + ",\"width\":360,\"depth\":300,\"height\":240}  "
        + "(posts, beams and rafters; \"rafters\":N, \"slats\":true. add_arbor is a small one with lattice sides and "
        + "\"arch\":true; add_gazebo has \"sides\":6|8, a floor, a railing and a pointed roof; add_carport posts and a roof "
        + "\"roof\":\"flat|gable\")\n"
        + "  {\"action\":\"add_shed\"," + place + ",\"width\":300,\"depth\":240,\"height\":210,\"roof\":\"gable|lean_to|gambrel\"}  "
        + "(walls, a roof and \"doors\":1|2 on its front; \"window\":true, \"color\", \"roof_color\", \"trim_color\"; "
        + "add_greenhouse is the same shape in glass on a frame)\n"
        + "  {\"action\":\"add_playset\"," + place + "}  (a fort with a roof, a slide and a beam of swings: \"swings\":N, \"slide\":false; "
        + "add_trampoline has \"diameter\" and \"net\":true)\n"
        + "  {\"action\":\"add_pool\",\"points\":[[x,y],...],\"type\":\"in_ground|above_ground\"}  "
        + "(or " + place + ",\"width\":N,\"depth\":N, or \"diameter\":N for a round one: water, a coping, and \"deck_width\":N of "
        + "paving around it. The ground isn't dug: add_pad lowers it. add_pond is water with an edge of stones; "
        + "add_firepit has \"diameter\" and \"seat_wall\":true; add_outdoor_kitchen \"width\", \"grill\":true)\n"
        + "  {\"action\":\"add_driveway\",\"points\":[[x,y],[x,y],...],\"width\":350,\"material\":\"concrete|asphalt|gravel|pavers\"}  "
        + "(a driveway along a center line and laid on the ground, wider at its last point with \"apron\":N cm; \"polygon\":[...] "
        + "instead for any shape; \"grade\":true shapes the ground first with grade_path. add_sidewalk (\"width\":150) and "
        + "add_curb (\"height\":15) run along a line the same way)\n"
        + "  {\"action\":\"add_retaining_wall\",\"points\":[[x,y],[x,y],...],\"height\":90,\"low_side\":\"left|right\"}  "
        + "(cuts the ground into a step along a line - the ground of the low side goes down by \"height\" over "
        + "\"terrace_width\" (300) cm - and builds the wall that holds it, in a style of masonry: \"style\":\"retaining_wall\", "
        + "\"stone\", \"color\")\n"
        + "  {\"action\":\"add_steps_on_slope\",\"points\":[[x,y],[x,y]],\"width\":120}  (steps from a point to another one, as many "
        + "as the ground between them asks for, each \"rise\" 15 to 18 cm high; \"material\":\"stone|wood|concrete\")\n"
        + "  {\"action\":\"add_drainage\",\"type\":\"" + join(DRAINAGE) + "\",\"points\":[[x,y],[x,y]]}  "
        + "(what carries the water away, along a line or at \"x\",\"y\" for a catch basin)\n"
        + "  {\"action\":\"add_utility\",\"type\":\"" + join(UTILITIES) + "\"," + place + "}  "
        + "(a small item of the site at its usual size; a mailbox has \"style\":\"post|brick|cluster\"; a utility pole \"to\":[x,y] "
        + "for its wires to the next one; a street sign \"text\")\n"
        + "  {\"action\":\"add_raised_bed\"," + place + ",\"width\":240,\"depth\":120,\"height\":30,\"material\":\"wood|stone|metal\"}  "
        + "(a frame filled with soil. add_garden_enclosure builds \"beds\":{\"rows\":2,\"columns\":3} of them with paths between, "
        + "and a fence around with a gate: \"fence\":{\"style\":\"garden_wire_tpost\",\"height\":150})\n"
        + "  {\"action\":\"add_callout\",\"text\":\"<text>\",\"x\":N,\"y\":N,\"to\":[x,y]}  (a label in the plan with a line "
        + "from it to what it names)\n"
        + "  {\"action\":\"add_context_building\",\"points\":[[x,y],...],\"stories\":2,\"roof\":\"hip|gable|flat\"}  "
        + "(a neighbor's house as a simple mass on the ground: walls of \"stories\" x 280 cm (or \"height\"), a roof with "
        + "\"pitch\", \"siding\":\"siding|brick|stucco\" with \"color\" and \"roof_color\", \"garage_doors\":[{\"edge\":N,\"at\":0.5,"
        + "\"width\":480}], \"owner\". It has the tag neighbor: hide_layer with that tag hides every neighbor at once. "
        + "set_context_detail \"detail\":\"massing|textured|detailed\" chooses how much is drawn for the home)\n"
        + "  {\"action\":\"add_context_vehicle\"," + place + ",\"type\":\"car|suv|pickup|van\"}  (a car for the scale, with \"color\"; "
        + "add_context_person a standing figure, \"height\":170)\n";
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
    try {
      if ("add_deck".equals(action)) {
        addDeck(command, tally, notes);
      } else if ("add_patio".equals(action) || "add_pavers".equals(action)) {
        addPatio(action, command, tally, notes);
      } else if ("add_pergola".equals(action) || "add_arbor".equals(action) || "add_carport".equals(action)) {
        addPergola(action, command, tally, notes);
      } else if ("add_gazebo".equals(action)) {
        addGazebo(command, tally, notes);
      } else if ("add_shed".equals(action) || "add_greenhouse".equals(action)) {
        addShed(action, command, tally, notes);
      } else if ("add_playset".equals(action)) {
        addPlayset(command, tally, notes);
      } else if ("add_trampoline".equals(action)) {
        addTrampoline(command, tally, notes);
      } else if ("add_pool".equals(action) || "add_pond".equals(action)) {
        addPool(action, command, tally, notes);
      } else if ("add_firepit".equals(action)) {
        addFirepit(command, tally, notes);
      } else if ("add_outdoor_kitchen".equals(action)) {
        addOutdoorKitchen(command, tally, notes);
      } else if ("add_driveway".equals(action) || "add_sidewalk".equals(action) || "add_curb".equals(action)) {
        addDriveway(action, command, tally, notes);
      } else if ("add_retaining_wall".equals(action)) {
        addRetainingWall(command, tally, notes);
      } else if ("add_steps_on_slope".equals(action)) {
        addSteps(command, tally, notes);
      } else if ("add_drainage".equals(action)) {
        addDrainage(command, tally, notes);
      } else if ("add_utility".equals(action)) {
        addUtility(command, tally, notes);
      } else if ("add_raised_bed".equals(action)) {
        addRaisedBed(command, tally, notes);
      } else if ("add_garden_enclosure".equals(action)) {
        addGardenEnclosure(command, tally, notes);
      } else if ("add_callout".equals(action)) {
        addCallout(command, notes);
      } else if ("add_context_building".equals(action)) {
        addContextBuilding(command, tally, notes);
      } else if ("add_context_vehicle".equals(action)) {
        addVehicle(command, tally, notes);
      } else if ("add_context_person".equals(action)) {
        addPerson(command, tally, notes);
      } else if ("set_context_detail".equals(action)) {
        String detail = command.getString("detail") != null ? command.getString("detail").trim().toLowerCase(Locale.ENGLISH) : "";
        if (!Arrays.asList("massing", "textured", "detailed").contains(detail)) {
          throw new IOException("\"detail\" is massing, textured or detailed");
        }
        this.home.setProperty(CONTEXT_DETAIL_PROPERTY, detail);
        notes.add("The buildings added by add_context_building are now drawn as \"" + detail + "\"; the ones that exist keep their look");
      } else {
        return false;
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

  // ---- Common ----

  private static float number(AssistantCommand command, String key, float defaultValue, float minimum, float maximum) throws IOException {
    double value = command.getDouble(key, defaultValue);
    if (!(value >= minimum && value <= maximum)) {
      throw new IOException("\"" + key + "\" goes from " + format(minimum) + " to " + format(maximum));
    }
    return (float)value;
  }

  private static int color(AssistantCommand command, String key, int defaultColor) throws IOException {
    String text = command.getString(key);
    if (text == null) {
      return defaultColor;
    }
    Integer color = AssistantCommandExecutor.parseColor(text);
    if (color == null) {
      throw new IOException("\"" + key + "\" must be written #RRGGBB");
    }
    return color & 0xFFFFFF;
  }

  private static int shade(int color, float factor) {
    int red = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * factor)));
    int green = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * factor)));
    int blue = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
    return red << 16 | green << 8 | blue;
  }

  private float ground(float x, float y, Level level) {
    return this.executor.getShapeCommands().getStandingHeight(x, y) - (level != null ? level.getElevation() : 0);
  }

  /**
   * A model built around the origin, x to the right and z to the front, then put at its place and turned.
   */
  private final class Build {
    final AssistantMeshBuilder mesh = new AssistantMeshBuilder();

    AssistantMeshBuilder.Material color(int color) {
      return this.mesh.getMaterial(color);
    }

    AssistantMeshBuilder.Material pattern(String pattern, float width, float height, int color) {
      return this.mesh.getMaterial(pattern, width, height, color, shade(color, 0.65f), 0);
    }

    void box(float x1, float z1, float x2, float z2, float low, float high, AssistantMeshBuilder.Material material) {
      if (Math.abs(x2 - x1) > 0.05f && Math.abs(z2 - z1) > 0.05f && high - low > 0.05f) {
        this.mesh.addBox(Math.min(x1, x2), low, Math.min(z1, z2), Math.max(x1, x2), high, Math.max(z1, z2), material);
      }
    }

    void post(float x, float z, float size, float low, float high, AssistantMeshBuilder.Material material) {
      box(x - size / 2, z - size / 2, x + size / 2, z + size / 2, low, high, material);
    }

    void cylinder(float x, float z, float radius, float low, float high, int sides, AssistantMeshBuilder.Material material) {
      float [][] section = new float [sides][];
      for (int i = 0; i < sides; i++) {
        double angle = Math.PI * 2 * i / sides;
        section [i] = new float [] {x + (float)Math.cos(angle) * radius, low, z + (float)Math.sin(angle) * radius};
      }
      this.mesh.addSolid(section, new float [] {0, high - low, 0}, material);
    }

    /**
     * Adds a roof with two slopes over a rectangle, its ridge along x or along z.
     */
    void gable(float x1, float z1, float x2, float z2, float eave, float ridge, boolean alongX, AssistantMeshBuilder.Material material) {
      if (alongX) {
        float middle = (z1 + z2) / 2;
        this.mesh.addSolid(new float [][] {{x1, eave, z1}, {x1, eave, z2}, {x1, ridge, middle}}, new float [] {x2 - x1, 0, 0}, material);
      } else {
        float middle = (x1 + x2) / 2;
        this.mesh.addSolid(new float [][] {{x1, eave, z1}, {x2, eave, z1}, {middle, ridge, z1}}, new float [] {0, 0, z2 - z1}, material);
      }
    }

    /**
     * Adds a solid with a top that leans, from <code>high1</code> at z1 to <code>high2</code> at z2.
     */
    void wedge(float x1, float z1, float x2, float z2, float low, float high1, float high2, AssistantMeshBuilder.Material material) {
      this.mesh.addSolid(new float [][] {{x1, low, z1}, {x1, low, z2}, {x1, high2, z2}, {x1, high1, z1}}, new float [] {x2 - x1, 0, 0}, material);
    }

    void pyramid(float x, float z, float radius, float low, float high, int sides, AssistantMeshBuilder.Material material) {
      float [][] base = new float [sides][];
      for (int i = 0; i < sides; i++) {
        double angle = Math.PI * 2 * (i + 0.5) / sides;
        base [i] = new float [] {x + (float)Math.cos(angle) * radius, low, z + (float)Math.sin(angle) * radius};
      }
      float [] center = {x, low + 1, z};
      for (int i = 0; i < sides; i++) {
        this.mesh.addOutward(new float [][] {base [i], base [(i + 1) % sides], {x, high, z}}, center, material);
      }
      this.mesh.addOutward(base, new float [] {x, high, z}, material);
    }

    /**
     * Adds a surface without thickness seen from both sides.
     */
    void sheet(float [][] points, AssistantMeshBuilder.Material material) {
      this.mesh.addPolygon(points, material, 0, 1, 0.3f);
      this.mesh.addPolygon(points, material, 0, -1, -0.3f);
    }
  }

  private static Set<String> getTags(AssistantCommand command, String... tags) {
    Set<String> all = new LinkedHashSet<String>(Arrays.asList(tags));
    Object value = command.getParameters().get("tags");
    if (value instanceof List) {
      for (Object tag : (List<?>)value) {
        all.addAll(HomeItemTags.parseTags(String.valueOf(tag)));
      }
    } else if (value != null) {
      all.addAll(HomeItemTags.parseTags(value.toString()));
    }
    return all;
  }

  /**
   * Adds the piece that shows a model built around the origin, at the place and the angle of a command.
   */
  private HomePieceOfFurniture place(Build build, AssistantCommand command, String defaultName, String kind, int [] tally,
                                     List<String> notes, String... tags) throws IOException {
    float x = (float)command.getDouble("x", Double.NaN);
    float y = (float)command.getDouble("y", Double.NaN);
    if (Float.isNaN(x) || Float.isNaN(y) || !AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)) {
      throw new IOException("give its place: \"x\" and \"y\" in cm, the middle of what is built");
    }
    return place(build, command, defaultName, kind, x, y, (float)command.getDouble("angle", 0), tally, notes, tags);
  }

  private HomePieceOfFurniture place(Build build, AssistantCommand command, String defaultName, String kind, float x, float y,
                                     float angle, int [] tally, List<String> notes, String... tags) throws IOException {
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    Level level = shapeCommands.getLevel(command, command.getAction(), notes);
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : defaultName;
    AssistantGeneratedModel model = build.mesh.store(kind);
    HomePieceOfFurniture piece = shapeCommands.createPiece(name, model, level, true, "site");
    float [] bounds = model.getBounds();
    double radians = Math.toRadians(angle);
    float centerX = (bounds [0] + bounds [3]) / 2;
    float centerZ = (bounds [2] + bounds [5]) / 2;
    piece.setX(x + (float)(centerX * Math.cos(radians) - centerZ * Math.sin(radians)));
    piece.setY(y + (float)(centerX * Math.sin(radians) + centerZ * Math.cos(radians)));
    piece.setAngle((float)radians);
    piece.setElevation(ground(x, y, level) + bounds [1] + (float)command.getDouble("offset", 0));
    finish(piece, command, kind, tags);
    shapeCommands.add(piece);
    tally [0]++;
    return piece;
  }

  private void finish(HomePieceOfFurniture piece, AssistantCommand command, String kind, String... tags) {
    piece.setProperty(AssistantGardenCommands.GARDEN_PROPERTY, kind);
    piece.setProperty(AssistantCommandExecutor.OUTDOOR_PROPERTY, "true");
    piece.setProperty(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(getTags(command, tags)));
    Map<String, Object> spec = new LinkedHashMap<String, Object>(command.getParameters());
    spec.remove("action");
    piece.setProperty(OUTDOOR_SPEC_PROPERTY, AssistantShapeCommands.toJson(spec));
  }

  /**
   * Adds the piece that shows a model built at its place in the plan.
   */
  private HomePieceOfFurniture addFixed(AssistantMeshBuilder mesh, AssistantCommand command, String defaultName, String kind,
                                        int [] tally, List<String> notes, String... tags) throws IOException {
    AssistantShapeCommands shapeCommands = this.executor.getShapeCommands();
    Level level = shapeCommands.getLevel(command, command.getAction(), notes);
    String name = command.getString("name") != null && command.getString("name").trim().length() > 0
        ? command.getString("name").trim() : defaultName;
    HomePieceOfFurniture piece = shapeCommands.createPiece(name, mesh.store(kind), level, false, "site");
    finish(piece, command, kind, tags);
    shapeCommands.add(piece);
    tally [0]++;
    return piece;
  }

  private void warnUnknown(AssistantCommand command, List<String> notes, String... keys) {
    List<String> unknown = new ArrayList<String>();
    for (String key : command.getParameters().keySet()) {
      if (!COMMON_KEYS.contains(key) && !Arrays.asList(keys).contains(key)) {
        unknown.add(key);
      }
    }
    if (!unknown.isEmpty()) {
      List<String> valid = new ArrayList<String>(Arrays.asList(keys));
      notes.add("Warning: " + command.getAction() + " ignored " + (unknown.size() == 1 ? "the key " : "the keys ") + unknown
          + ". Its own keys: " + valid + ", with x, y, angle, width, depth, height, color, name, tags and level");
    }
  }

  /**
   * Returns the polygon of a command: its points, or the rectangle of its place and its sizes.
   */
  private float [][] getPolygon(AssistantCommand command, float defaultWidth, float defaultDepth) throws IOException {
    List<float []> points = command.getPoints(command.has("polygon") ? "polygon" : "points");
    if (points.size() >= 3) {
      for (float [] point : points) {
        if (!AssistantCommandExecutor.coordinateSane(point [0]) || !AssistantCommandExecutor.coordinateSane(point [1])) {
          throw new IOException("points out of range");
        }
      }
      if (points.size() > 3 && Math.hypot(points.get(0) [0] - points.get(points.size() - 1) [0],
              points.get(0) [1] - points.get(points.size() - 1) [1]) < 0.5) {
        points.remove(points.size() - 1);
      }
      return points.toArray(new float [points.size()][]);
    }
    float x = (float)command.getDouble("x", Double.NaN);
    float y = (float)command.getDouble("y", Double.NaN);
    if (Float.isNaN(x) || Float.isNaN(y)) {
      throw new IOException("give \"points\":[[x,y],[x,y],[x,y],...], or its middle \"x\",\"y\" with \"width\" and \"depth\"");
    }
    float diameter = (float)command.getDouble("diameter", Double.NaN);
    if (!Float.isNaN(diameter)) {
      // Round
      float [][] circle = new float [24][];
      for (int i = 0; i < circle.length; i++) {
        double angle = Math.PI * 2 * i / circle.length;
        circle [i] = new float [] {x + (float)Math.cos(angle) * diameter / 2, y + (float)Math.sin(angle) * diameter / 2};
      }
      return circle;
    }
    float width = number(command, "width", defaultWidth, 10, 20000);
    float depth = number(command, "depth", defaultDepth, 10, 20000);
    double radians = Math.toRadians(command.getDouble("angle", 0));
    float [][] corners = {{-width / 2, -depth / 2}, {width / 2, -depth / 2}, {width / 2, depth / 2}, {-width / 2, depth / 2}};
    for (float [] corner : corners) {
      float cornerX = corner [0];
      corner [0] = x + (float)(cornerX * Math.cos(radians) - corner [1] * Math.sin(radians));
      corner [1] = y + (float)(cornerX * Math.sin(radians) + corner [1] * Math.cos(radians));
    }
    return corners;
  }

  private static double getArea(float [][] polygon) {
    double area = 0;
    for (int i = 0; i < polygon.length; i++) {
      float [] next = polygon [(i + 1) % polygon.length];
      area += polygon [i][0] * next [1] - next [0] * polygon [i][1];
    }
    return area / 2;
  }

  private static List<Object> toList(float [][] points) {
    List<Object> list = new ArrayList<Object>();
    for (float [] point : points) {
      list.add(Arrays.asList((Object)Double.valueOf(point [0]), Double.valueOf(point [1])));
    }
    return list;
  }

  /**
   * Runs a command of another class and returns the piece it added, or <code>null</code>.
   */
  private HomePieceOfFurniture run(String action, Map<String, Object> parameters, int [] tally, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> before = new ArrayList<HomePieceOfFurniture>(this.home.getFurniture());
    List<String> subNotes = new ArrayList<String>();
    this.executor.applySubCommand(new AssistantCommand(action, parameters), tally, subNotes);
    for (String note : subNotes) {
      if (note.startsWith("Couldn't")) {
        throw new IOException(note.substring(note.indexOf('(') + 1, note.lastIndexOf(')')));
      } else if (note.startsWith("Warning")) {
        notes.add(note);
      }
    }
    HomePieceOfFurniture added = null;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if (!before.contains(piece) && !HomeTerrain.isTerrain(piece)) {
        added = piece;
      }
    }
    return added;
  }

  // ---- Decks and patios ----

  private void addDeck(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float [][] polygon = getPolygon(command, 400, 300);
    Level level = this.executor.getShapeCommands().getLevel(command, "add_deck", notes);
    float height = number(command, "height", 60, 5, 1200);
    float highest = -Float.MAX_VALUE;
    float lowest = Float.MAX_VALUE;
    for (float [] point : polygon) {
      float ground = ground(point [0], point [1], level);
      highest = Math.max(highest, ground);
      lowest = Math.min(lowest, ground);
    }
    float top = highest + height;
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    String decking = command.getString("decking") != null ? command.getString("decking").toLowerCase(Locale.ENGLISH) : "wood";
    int color = color(command, "color", decking.startsWith("comp") ? 0x7A6A5A : 0x9A7B55);
    AssistantMeshBuilder.Material boards = mesh.getMaterial("hboards", 300, 14, color, shade(color, 0.6f), 0);
    AssistantMeshBuilder.Material frame = mesh.getMaterial(shade(color, 0.8f));
    Area area = AssistantMeshBuilder.getArea(polygon);
    if (area.isEmpty()) {
      throw new IOException("the points enclose no area");
    }
    // Boards on a frame
    mesh.addPrism(area, top - 3, new float [] {top, 0, 0}, boards, frame);
    mesh.addPrism(area, top - 22, new float [] {top - 3, 0, 0}, frame, frame);
    // Posts under it, down to the ground
    float [] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    for (float [] point : polygon) {
      bounds [0] = Math.min(bounds [0], point [0]);
      bounds [1] = Math.min(bounds [1], point [1]);
      bounds [2] = Math.max(bounds [2], point [0]);
      bounds [3] = Math.max(bounds [3], point [1]);
    }
    int posts = 0;
    int columns = Math.max(1, Math.round((bounds [2] - bounds [0]) / 200));
    int rows = Math.max(1, Math.round((bounds [3] - bounds [1]) / 200));
    for (int row = 0; row <= rows; row++) {
      for (int column = 0; column <= columns; column++) {
        float x = bounds [0] + 12 + (bounds [2] - bounds [0] - 24) * column / columns;
        float y = bounds [1] + 12 + (bounds [3] - bounds [1] - 24) * row / rows;
        if (AssistantRoomFurnisher.contains(polygon, x, y)) {
          mesh.addBox(x - 5, ground(x, y, level) - 5, y - 5, x + 5, top - 22, y + 5, frame);
          posts++;
        }
      }
    }
    // What closes the space under it
    String skirting = command.getString("skirting") != null ? command.getString("skirting").toLowerCase(Locale.ENGLISH) : "none";
    if (!skirting.equals("none") && !skirting.equals("false") && height > 20) {
      AssistantMeshBuilder.Material skirt = skirting.startsWith("lattice")
          ? mesh.getMaterial("lattice_diagonal", 12, 12, shade(color, 1.1f), color, 0.3f)
          : skirting.startsWith("horizontal") ? mesh.getMaterial("hboards", 60, 12, shade(color, 0.9f), shade(color, 0.3f), 0)
          : mesh.getMaterial(shade(color, 0.9f));
      for (int i = 0; i < polygon.length; i++) {
        float [] a = polygon [i];
        float [] b = polygon [(i + 1) % polygon.length];
        float [][] quad = {{a [0], ground(a [0], a [1], level) - 3, a [1]}, {b [0], ground(b [0], b [1], level) - 3, b [1]},
                           {b [0], top - 22, b [1]}, {a [0], top - 22, a [1]}};
        mesh.addPolygon(quad, skirt, b [1] - a [1], 0, -(b [0] - a [0]));
        mesh.addPolygon(quad, skirt, -(b [1] - a [1]), 0, b [0] - a [0]);
      }
    }
    // Stairs down to the ground
    String stairsText = "";
    float [] stairGap = null;
    Object stairs = command.getParameters().get("stairs");
    if (stairs instanceof Map || Boolean.TRUE.equals(stairs)) {
      Map<?, ?> map = stairs instanceof Map ? (Map<?, ?>)stairs : Collections.emptyMap();
      int edge = Math.max(1, Math.min(polygon.length, Math.round(AssistantFurnitureBuilder.getNumber(map, "edge", 1)))) - 1;
      float stairWidth = Math.max(60, Math.min(600, AssistantFurnitureBuilder.getNumber(map, "width", 120)));
      float at = Math.max(0, Math.min(1, AssistantFurnitureBuilder.getNumber(map, "at", 0.5f)));
      float [] a = polygon [edge];
      float [] b = polygon [(edge + 1) % polygon.length];
      double length = Math.hypot(b [0] - a [0], b [1] - a [1]);
      float dx = (float)((b [0] - a [0]) / length);
      float dy = (float)((b [1] - a [1]) / length);
      // Away from the deck
      float sign = getArea(polygon) > 0 ? 1 : -1;
      float nx = dy * sign;
      float ny = -dx * sign;
      float middle = (float)Math.max(stairWidth / 2, Math.min(length - stairWidth / 2, length * at));
      float startX = a [0] + dx * middle;
      float startY = a [1] + dy * middle;
      float drop = top - ground(startX + nx * 100, startY + ny * 100, level);
      int steps = Math.max(1, (int)Math.ceil(drop / 18));
      float rise = drop / steps;
      for (int step = 1; step < steps; step++) {
        float stepTop = top - rise * step;
        float out = 28 * (step - 1);
        float [][] section = {{startX - dx * stairWidth / 2 + nx * out, stepTop - 4, startY - dy * stairWidth / 2 + ny * out},
                              {startX + dx * stairWidth / 2 + nx * out, stepTop - 4, startY + dy * stairWidth / 2 + ny * out},
                              {startX + dx * stairWidth / 2 + nx * out, stepTop, startY + dy * stairWidth / 2 + ny * out},
                              {startX - dx * stairWidth / 2 + nx * out, stepTop, startY - dy * stairWidth / 2 + ny * out}};
        mesh.addSolid(section, new float [] {nx * 28, 0, ny * 28}, boards);
      }
      stairsText = ", " + steps + " steps of " + format(rise) + " cm down from its edge " + (edge + 1);
      stairGap = new float [] {edge, middle, stairWidth};
    }
    // Benches along edges
    List<String> benches = command.getStringList("benches");
    for (String bench : benches) {
      try {
        int edge = Math.max(1, Math.min(polygon.length, Integer.parseInt(bench.trim().replace(".0", "")))) - 1;
        float [] a = polygon [edge];
        float [] b = polygon [(edge + 1) % polygon.length];
        double length = Math.hypot(b [0] - a [0], b [1] - a [1]);
        float dx = (float)((b [0] - a [0]) / length);
        float dy = (float)((b [1] - a [1]) / length);
        float sign = getArea(polygon) > 0 ? -1 : 1;
        float nx = dy * sign;
        float ny = -dx * sign;
        float [][] section = {{a [0] + dx * 20 + nx * 5, top + 38, a [1] + dy * 20 + ny * 5},
                              {a [0] + dx * 20 + nx * 45, top + 38, a [1] + dy * 20 + ny * 45},
                              {a [0] + dx * 20 + nx * 45, top + 44, a [1] + dy * 20 + ny * 45},
                              {a [0] + dx * 20 + nx * 5, top + 44, a [1] + dy * 20 + ny * 5}};
        mesh.addSolid(section, new float [] {dx * (float)(length - 40), 0, dy * (float)(length - 40)}, boards);
        for (float along : new float [] {30, (float)length - 30}) {
          mesh.addBox(a [0] + dx * along + nx * 25 - 4, top, a [1] + dy * along + ny * 25 - 4,
              a [0] + dx * along + nx * 25 + 4, top + 38, a [1] + dy * along + ny * 25 + 4, frame);
        }
      } catch (NumberFormatException ex) {
        throw new IOException("\"benches\" lists the numbers of the edges with a bench, like [2,3]");
      }
    }
    HomePieceOfFurniture deck = addFixed(mesh, command, "Deck", "deck", tally, notes, "site", "deck");
    String railingText = "";
    Object railing = command.getParameters().get("railing");
    if (railing != null && !"none".equals(railing.toString()) && !Boolean.FALSE.equals(railing)) {
      // A fence around it, standing on the boards
      Map<String, Object> fence = new LinkedHashMap<String, Object>();
      if (railing instanceof Map) {
        for (Map.Entry<?, ?> entry : ((Map<?, ?>)railing).entrySet()) {
          fence.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      } else if (!Boolean.TRUE.equals(railing)) {
        fence.put("style", railing.toString());
      }
      if (fence.get("style") == null) {
        fence.put("style", "picket");
      }
      if (fence.get("height") == null) {
        fence.put("height", Double.valueOf(100));
      }
      fence.put("points", toList(polygon));
      fence.put("closed", Boolean.TRUE);
      fence.put("follow_ground", "level");
      fence.put("post_spacing", fence.get("post_spacing") != null ? fence.get("post_spacing") : Double.valueOf(180));
      fence.put("name", deck.getName() + " railing");
      if (command.getString("level") != null) {
        fence.put("level", command.getString("level"));
      }
      if (stairGap != null) {
        double [] distances = AssistantFenceBuilder.getDistances(polygon, true);
        Map<String, Object> gap = new LinkedHashMap<String, Object>();
        gap.put("at", Double.valueOf(distances [(int)stairGap [0]] + stairGap [1]));
        gap.put("width", Double.valueOf(stairGap [2]));
        gap.put("type", "gap");
        fence.put("openings", Collections.singletonList(gap));
      }
      // The ground under a deck isn't level: the railing is raised to the boards from the highest ground
      fence.put("elevation", Double.valueOf(top - highest));
      HomePieceOfFurniture fencePiece = run("add_fence", fence, tally, notes);
      if (fencePiece != null) {
        railingText = ", with the railing " + this.executor.getId(fencePiece) + " (" + fence.get("style") + ")";
      }
    }
    notes.add("Added the deck " + this.executor.getId(deck) + " \"" + deck.getName() + "\": " + format(Math.abs(getArea(polygon)) / 10000)
        + " m2, its boards at " + format(top) + " cm (" + format(height) + " cm above the highest ground under it"
        + (highest - lowest > 5 ? ", " + format(top - lowest) + " cm above the lowest" : "") + "), on " + posts + " posts"
        + stairsText + railingText);
    warnUnknown(command, notes, "points", "polygon", "decking", "direction", "skirting", "railing", "stairs", "benches");
  }

  private void addPatio(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float [][] polygon = getPolygon(command, 400, 300);
    Level level = this.executor.getShapeCommands().getLevel(command, action, notes);
    float thickness = number(command, "thickness", 6, 1, 60);
    String pattern = command.getString("pattern") != null ? command.getString("pattern").trim().toLowerCase(Locale.ENGLISH) : "running_bond";
    int color = color(command, "color", pattern.equals("concrete") ? 0xBDB8AE : pattern.equals("flagstone") ? 0x9A948A : 0xA8705A);
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material paving;
    if (pattern.equals("running_bond")) {
      paving = mesh.getMaterial("brick_running", 40, 40, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("herringbone")) {
      paving = mesh.getMaterial("herringbone", 80, 80, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("basket") || pattern.equals("basketweave")) {
      paving = mesh.getMaterial("basket", 80, 80, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("random_ashlar") || pattern.equals("ashlar")) {
      paving = mesh.getMaterial("ashlar", 180, 180, color, shade(color, 0.6f), 0);
    } else if (pattern.equals("flagstone")) {
      paving = mesh.getMaterial("flagstone", 220, 220, color, shade(color, 0.55f), 0);
    } else if (pattern.equals("concrete") || pattern.equals("plain")) {
      paving = mesh.getMaterial("stucco", 60, 60, color, color, 0);
    } else {
      throw new IOException("\"pattern\" is running_bond, herringbone, basket, random_ashlar, flagstone or concrete");
    }
    Area area = AssistantMeshBuilder.getArea(polygon);
    if (area.isEmpty()) {
      throw new IOException("the points enclose no area");
    }
    // Laid level on the highest ground under it
    float base = -Float.MAX_VALUE;
    float lowest = Float.MAX_VALUE;
    for (float [] point : polygon) {
      base = Math.max(base, ground(point [0], point [1], level));
      lowest = Math.min(lowest, ground(point [0], point [1], level));
    }
    Object border = command.getParameters().get("border");
    if (border instanceof Map || Boolean.TRUE.equals(border)) {
      Map<?, ?> map = border instanceof Map ? (Map<?, ?>)border : Collections.emptyMap();
      float width = Math.max(5, Math.min(100, AssistantFurnitureBuilder.getNumber(map, "width", 20)));
      Integer borderColor = map.get("color") != null ? AssistantCommandExecutor.parseColor(map.get("color").toString()) : null;
      AssistantMeshBuilder.Material band = mesh.getMaterial(borderColor != null ? borderColor & 0xFFFFFF : shade(color, 0.7f));
      float inside = getArea(polygon) > 0 ? -1 : 1;
      float [][] inner = AssistantFenceCommands.offsetLine(polygon, true, width * inside);
      Area innerArea = AssistantMeshBuilder.getArea(inner);
      Area ring = new Area(area);
      ring.subtract(innerArea);
      mesh.addPrism(ring, lowest - 5, new float [] {base + thickness, 0, 0}, band, band);
      if (!innerArea.isEmpty()) {
        mesh.addPrism(innerArea, lowest - 5, new float [] {base + thickness, 0, 0}, paving, band);
      }
    } else {
      mesh.addPrism(area, lowest - 5, new float [] {base + thickness, 0, 0}, paving, mesh.getMaterial(shade(color, 0.8f)));
    }
    HomePieceOfFurniture patio = addFixed(mesh, command, "Patio", "patio", tally, notes, "site", "patio");
    notes.add("Added the patio " + this.executor.getId(patio) + " \"" + patio.getName() + "\": " + format(Math.abs(getArea(polygon)) / 10000)
        + " m2 of " + pattern + ", its top at " + format(base + thickness) + " cm"
        + (base - lowest > 10 ? ". The ground falls by " + format(base - lowest) + " cm under it: add_pad levels it first" : ""));
    warnUnknown(command, notes, "points", "polygon", "pattern", "thickness", "border", "diameter");
  }

  // ---- Pergolas, gazebos and sheds ----

  private void addPergola(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    boolean arbor = "add_arbor".equals(action);
    boolean carport = "add_carport".equals(action);
    float width = number(command, "width", arbor ? 150 : carport ? 600 : 360, 40, 3000);
    float depth = number(command, "depth", arbor ? 80 : carport ? 550 : 300, 30, 3000);
    float height = number(command, "height", arbor ? 220 : 240, 100, 800);
    int color = color(command, "color", carport ? 0xE6E2D8 : 0x8B6B47);
    Build build = new Build();
    AssistantMeshBuilder.Material wood = build.color(color);
    float post = arbor ? 9 : carport ? 14 : 12;
    int postsAlong = Math.max(2, Math.round(width / 300) + 1);
    for (int i = 0; i < postsAlong; i++) {
      float x = -width / 2 + post / 2 + (width - post) * i / (postsAlong - 1);
      for (int side = -1; side <= 1; side += 2) {
        build.post(x, side * (depth / 2 - post / 2), post, 0, height, wood);
      }
    }
    String name = arbor ? "Arbor" : carport ? "Carport" : "Pergola";
    if (carport) {
      String roof = command.getString("roof") != null ? command.getString("roof").toLowerCase(Locale.ENGLISH) : "flat";
      AssistantMeshBuilder.Material roofing = build.pattern("shingles", 100, 100, color(command, "roof_color", 0x5A5A5A));
      for (int side = -1; side <= 1; side += 2) {
        build.box(-width / 2, side * (depth / 2 - post / 2) - 4, width / 2, side * (depth / 2 - post / 2) + 4, height, height + 20, wood);
      }
      if (roof.startsWith("gable")) {
        build.gable(-width / 2 - 20, -depth / 2 - 20, width / 2 + 20, depth / 2 + 20, height + 20, height + 20 + depth * 0.2f, true, roofing);
      } else {
        build.wedge(-width / 2 - 20, -depth / 2 - 20, width / 2 + 20, depth / 2 + 20, height + 20, height + 38, height + 28, roofing);
      }
    } else {
      // Beams along it, rafters across, that go beyond the posts
      for (int side = -1; side <= 1; side += 2) {
        build.box(-width / 2 - 25, side * (depth / 2 - post / 2) - 3, width / 2 + 25, side * (depth / 2 - post / 2) + 3, height - 14,
            height + 4, wood);
      }
      int rafters = Math.max(3, Math.round((float)command.getDouble("rafters", Math.round(width / (arbor ? 30 : 45)) + 1)));
      for (int i = 0; i < rafters; i++) {
        float x = -width / 2 + width * i / (rafters - 1);
        if (arbor && command.getBoolean("arch", false)) {
          // An arch drawn with three boards
          build.wedge(x - 2, -depth / 2 - 15, x + 2, 0, height + 4, height + 14, height + 34, wood);
          build.wedge(x - 2, 0, x + 2, depth / 2 + 15, height + 4, height + 34, height + 14, wood);
        } else {
          build.box(x - 2, -depth / 2 - 30, x + 2, depth / 2 + 30, height + 4, height + 18, wood);
        }
      }
      if (command.getBoolean("slats", false)) {
        int slats = Math.max(2, Math.round(depth / 20));
        for (int i = 0; i < slats; i++) {
          float z = -depth / 2 + depth * (i + 0.5f) / slats;
          build.box(-width / 2 - 20, z - 2, width / 2 + 20, z + 2, height + 18, height + 22, wood);
        }
      }
      if (arbor) {
        AssistantMeshBuilder.Material lattice = build.mesh.getMaterial("lattice_diagonal", 14, 14, color, color, 0.3f);
        for (int side = -1; side <= 1; side += 2) {
          float x = side * (width / 2 - post / 2);
          build.sheet(new float [][] {{x, 15, -depth / 2 + post}, {x, 15, depth / 2 - post}, {x, height - 20, depth / 2 - post},
                                      {x, height - 20, -depth / 2 + post}}, lattice);
        }
      }
    }
    HomePieceOfFurniture piece = place(build, command, name, name.toLowerCase(Locale.ENGLISH), tally, notes, "site",
        name.toLowerCase(Locale.ENGLISH));
    notes.add("Added the " + name.toLowerCase(Locale.ENGLISH) + " " + this.executor.getId(piece) + " \"" + piece.getName() + "\": "
        + format(width) + " x " + format(depth) + " cm, " + format(height) + " cm under its beams, on " + postsAlong * 2 + " posts");
    warnUnknown(command, notes, "rafters", "slats", "arch", "roof", "roof_color");
  }

  private void addGazebo(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float diameter = number(command, "diameter", (float)command.getDouble("width", 360), 150, 1500);
    float height = number(command, "height", 230, 150, 600);
    int sides = Math.round(number(command, "sides", 8, 4, 12));
    int color = color(command, "color", 0xF2EFE6);
    Build build = new Build();
    AssistantMeshBuilder.Material wood = build.color(color);
    float radius = diameter / 2;
    build.cylinder(0, 0, radius, 0, 18, sides, build.pattern("hboards", 200, 14, 0x9A7B55));
    for (int i = 0; i < sides; i++) {
      double angle = Math.PI * 2 * (i + 0.5) / sides - Math.PI / sides;
      float x = (float)Math.cos(angle) * (radius - 8);
      float z = (float)Math.sin(angle) * (radius - 8);
      build.post(x, z, 10, 18, height, wood);
      // A rail between two posts, except at the front
      double next = Math.PI * 2 * (i + 1.5) / sides - Math.PI / sides;
      float x2 = (float)Math.cos(next) * (radius - 8);
      float z2 = (float)Math.sin(next) * (radius - 8);
      if (Math.abs((z + z2) / 2 - (radius - 8)) > radius * 0.25f) {
        build.mesh.addBar(new float [] {x, 18 + 90, z}, new float [] {x2, 18 + 90, z2}, 5, 6, wood);
        build.mesh.addBar(new float [] {x, 18 + 25, z}, new float [] {x2, 18 + 25, z2}, 4, 5, wood);
      }
    }
    build.pyramid(0, 0, radius + 25, height, height + radius * 0.6f, sides,
        build.pattern("shingles", 100, 100, color(command, "roof_color", 0x5A5048)));
    HomePieceOfFurniture piece = place(build, command, "Gazebo", "gazebo", tally, notes, "site", "gazebo");
    notes.add("Added the gazebo " + this.executor.getId(piece) + " \"" + piece.getName() + "\": " + sides + " sides, "
        + format(diameter) + " cm across, " + format(height + radius * 0.6f) + " cm high");
    warnUnknown(command, notes, "diameter", "sides", "roof_color");
  }

  private void addShed(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    boolean greenhouse = "add_greenhouse".equals(action);
    float width = number(command, "width", 300, 80, 2000);
    float depth = number(command, "depth", 240, 80, 2000);
    float height = number(command, "height", greenhouse ? 200 : 210, 100, 600);
    String roof = command.getString("roof") != null ? command.getString("roof").trim().toLowerCase(Locale.ENGLISH) : "gable";
    if (!Arrays.asList("gable", "lean_to", "gambrel").contains(roof)) {
      throw new IOException("\"roof\" is gable, lean_to or gambrel");
    }
    int color = color(command, "color", greenhouse ? 0xCFE3E0 : 0x8E5A45);
    int trimColor = color(command, "trim_color", 0xF2EFE6);
    Build build = new Build();
    AssistantMeshBuilder.Material walls = greenhouse ? build.mesh.getMaterial(color, 0.3f) : build.pattern("boards", 20, 100, color);
    AssistantMeshBuilder.Material trim = build.color(trimColor);
    AssistantMeshBuilder.Material roofing = greenhouse ? build.mesh.getMaterial(color, 0.3f)
        : build.pattern("shingles", 100, 100, color(command, "roof_color", 0x4E4A46));
    float rise = roof.equals("lean_to") ? Math.min(60, depth * 0.2f) : roof.equals("gambrel") ? width * 0.4f : width * 0.25f;
    build.box(-width / 2, -depth / 2, width / 2, depth / 2, 0, height, walls);
    float overhang = greenhouse ? 0 : 12;
    if (roof.equals("lean_to")) {
      build.wedge(-width / 2 - overhang, -depth / 2 - overhang, width / 2 + overhang, depth / 2 + overhang, height, height + 8,
          height + rise, roofing);
    } else if (roof.equals("gambrel")) {
      // A steep slope then a gentle one
      float [][] section = {{-width / 2 - overhang, height, -depth / 2 - overhang}, {width / 2 + overhang, height, -depth / 2 - overhang},
                            {width * 0.3f, height + rise * 0.7f, -depth / 2 - overhang}, {0, height + rise, -depth / 2 - overhang},
                            {-width * 0.3f, height + rise * 0.7f, -depth / 2 - overhang}};
      build.mesh.addSolid(section, new float [] {0, 0, depth + overhang * 2}, roofing);
    } else {
      build.gable(-width / 2 - overhang, -depth / 2 - overhang, width / 2 + overhang, depth / 2 + overhang, height, height + rise, false,
          roofing);
    }
    if (greenhouse) {
      // Its frame
      for (int i = 0; i <= Math.max(2, Math.round(width / 80)); i++) {
        float x = -width / 2 + width * i / Math.max(2, Math.round(width / 80));
        for (int side = -1; side <= 1; side += 2) {
          build.post(x, side * depth / 2, 4, 0, height, trim);
        }
      }
      build.box(-width / 2, -depth / 2 - 2, width / 2, -depth / 2 + 2, height - 4, height, trim);
      build.box(-width / 2, depth / 2 - 2, width / 2, depth / 2 + 2, height - 4, height, trim);
      build.box(-width / 2, -depth / 2, width / 2, depth / 2, 0, 12, build.color(0x8E8E88));
    }
    // Doors on its front, the side of the positive z
    int doors = Math.max(0, Math.min(2, Math.round((float)command.getDouble("doors", width > 280 ? 2 : 1))));
    float doorWidth = Math.min(90, width * 0.35f);
    float doorHeight = Math.min(190, height - 10);
    AssistantMeshBuilder.Material door = greenhouse ? trim : build.color(shade(color, 0.8f));
    for (int i = 0; i < doors; i++) {
      float x = doors == 1 ? 0 : (i == 0 ? -doorWidth / 2 - 1 : doorWidth / 2 + 1);
      build.box(x - doorWidth / 2, depth / 2, x + doorWidth / 2, depth / 2 + 3, 5, doorHeight, door);
      build.box(x - doorWidth / 2 - 5, depth / 2, x - doorWidth / 2, depth / 2 + 4, 0, doorHeight + 5, trim);
      build.box(x + doorWidth / 2, depth / 2, x + doorWidth / 2 + 5, depth / 2 + 4, 0, doorHeight + 5, trim);
    }
    if (command.getBoolean("window", false) && !greenhouse) {
      build.box(width / 2, -30, width / 2 + 3, 30, height * 0.5f, height * 0.5f + 60, build.color(0x9CB7C4));
      build.box(width / 2, -35, width / 2 + 4, 35, height * 0.5f - 5, height * 0.5f, trim);
    }
    String name = greenhouse ? "Greenhouse" : "Shed";
    HomePieceOfFurniture piece = place(build, command, name, name.toLowerCase(Locale.ENGLISH), tally, notes, "site",
        name.toLowerCase(Locale.ENGLISH));
    notes.add("Added the " + name.toLowerCase(Locale.ENGLISH) + " " + this.executor.getId(piece) + " \"" + piece.getName() + "\": "
        + format(width) + " x " + format(depth) + " cm, walls " + format(height) + " cm high under a " + roof.replace('_', ' ')
        + " roof " + format(height + rise) + " cm high, " + doors + (doors == 1 ? " door" : " doors") + " on its front (the side "
        + "of the positive y at the angle 0)");
    warnUnknown(command, notes, "roof", "doors", "window", "roof_color", "trim_color");
  }

  // ---- Play and water ----

  private void addPlayset(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    int color = color(command, "color", 0x9A7B55);
    int swings = Math.max(0, Math.min(4, Math.round((float)command.getDouble("swings", 2))));
    Build build = new Build();
    AssistantMeshBuilder.Material wood = build.color(color);
    // A fort: a platform under a roof
    for (float x : new float [] {-75, 75}) {
      for (float z : new float [] {-75, 75}) {
        build.post(x, z, 10, 0, 300, wood);
      }
    }
    build.box(-80, -80, 80, 80, 140, 150, wood);
    build.box(-80, -80, 80, -76, 150, 215, wood);
    build.box(-80, -80, -76, 80, 150, 215, wood);
    build.gable(-95, -95, 95, 95, 300, 350, true, build.color(0x2F6B4A));
    // A ladder at its back
    for (int i = 0; i < 5; i++) {
      build.box(-30, -92, 30, -86, 20 + i * 26, 25 + i * 26, wood);
    }
    if (command.getBoolean("slide", true)) {
      build.wedge(-25, 80, 25, 330, 0, 145, 12, build.color(0xE0B020));
    }
    if (swings > 0) {
      float beam = 110 * swings + 60;
      build.box(80, -6, 80 + beam, 6, 225, 240, wood);
      // An A frame at its end
      build.mesh.addBar(new float [] {80 + beam, 232, 0}, new float [] {80 + beam, 0, -110}, 9, 9, wood);
      build.mesh.addBar(new float [] {80 + beam, 232, 0}, new float [] {80 + beam, 0, 110}, 9, 9, wood);
      for (int i = 0; i < swings; i++) {
        float x = 80 + 80 + i * 110;
        build.box(x - 22, -8, x + 22, 8, 48, 52, build.color(0x2A2A2A));
        build.box(x - 21, -1, x - 19, 1, 52, 225, build.color(0x8E9398));
        build.box(x + 19, -1, x + 21, 1, 52, 225, build.color(0x8E9398));
      }
    }
    HomePieceOfFurniture piece = place(build, command, "Playset", "playset", tally, notes, "site", "playset");
    notes.add("Added the playset " + this.executor.getId(piece) + " \"" + piece.getName() + "\": a fort with a roof"
        + (command.getBoolean("slide", true) ? ", a slide" : "") + (swings > 0 ? ", " + swings + " swings" : "") + ", "
        + format(piece.getWidth()) + " x " + format(piece.getDepth()) + " cm");
    warnUnknown(command, notes, "swings", "slide");
  }

  private void addTrampoline(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float diameter = number(command, "diameter", (float)command.getDouble("width", 420), 100, 800);
    float height = number(command, "height", 90, 30, 200);
    Build build = new Build();
    float radius = diameter / 2;
    build.cylinder(0, 0, radius, height - 6, height, 16, build.color(0x1F4E8C));
    build.cylinder(0, 0, radius - 30, height, height + 0.5f, 16, build.color(0x1E1E1E));
    for (int i = 0; i < 6; i++) {
      double angle = Math.PI * 2 * i / 6;
      build.post((float)Math.cos(angle) * (radius - 5), (float)Math.sin(angle) * (radius - 5), 4, 0, height, build.color(0x8E9398));
      if (command.getBoolean("net", false)) {
        build.post((float)Math.cos(angle) * (radius - 5), (float)Math.sin(angle) * (radius - 5), 4, height, height + 180, build.color(0x8E9398));
        double next = Math.PI * 2 * (i + 1) / 6;
        build.sheet(new float [][] {
            {(float)Math.cos(angle) * (radius - 5), height, (float)Math.sin(angle) * (radius - 5)},
            {(float)Math.cos(next) * (radius - 5), height, (float)Math.sin(next) * (radius - 5)},
            {(float)Math.cos(next) * (radius - 5), height + 180, (float)Math.sin(next) * (radius - 5)},
            {(float)Math.cos(angle) * (radius - 5), height + 180, (float)Math.sin(angle) * (radius - 5)}},
            build.mesh.getMaterial("grid", 4, 4, 0x2A2A2A, 0x2A2A2A, 0.12f));
      }
    }
    HomePieceOfFurniture piece = place(build, command, "Trampoline", "trampoline", tally, notes, "site", "trampoline");
    notes.add("Added the trampoline " + this.executor.getId(piece) + ": " + format(diameter) + " cm across"
        + (command.getBoolean("net", false) ? ", with its net" : ""));
    warnUnknown(command, notes, "diameter", "net");
  }

  private void addPool(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    boolean pond = "add_pond".equals(action);
    float [][] polygon = getPolygon(command, pond ? 300 : 800, pond ? 200 : 400);
    Level level = this.executor.getShapeCommands().getLevel(command, action, notes);
    String type = command.getString("type") != null ? command.getString("type").trim().toLowerCase(Locale.ENGLISH) : "in_ground";
    boolean above = type.startsWith("above");
    float base = -Float.MAX_VALUE;
    float lowest = Float.MAX_VALUE;
    for (float [] point : polygon) {
      base = Math.max(base, ground(point [0], point [1], level));
      lowest = Math.min(lowest, ground(point [0], point [1], level));
    }
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material water = mesh.getMaterial("water", 150, 150, color(command, "color", pond ? 0x3F6E78 : 0x3FA7D6), 0x8FD3EE, 0);
    Area area = AssistantMeshBuilder.getArea(polygon);
    if (area.isEmpty()) {
      throw new IOException("the points enclose no area");
    }
    float outside = getArea(polygon) > 0 ? 1 : -1;
    float coping = pond ? 25 : above ? 8 : 30;
    float [][] outer = AssistantFenceCommands.offsetLine(polygon, true, coping * outside);
    Area ring = AssistantMeshBuilder.getArea(outer);
    Area whole = new Area(ring);
    ring.subtract(area);
    float wall = above ? number(command, "height", 130, 40, 300) : 0;
    AssistantMeshBuilder.Material edge = pond ? mesh.getMaterial("fieldstone", 60, 60, 0x8C8478, 0x4A4640, 0)
        : above ? mesh.getMaterial(0xDCE4E8) : mesh.getMaterial("stucco", 40, 40, color(command, "coping_color", 0xD8D2C4), 0xD8D2C4, 0);
    // Water a little under its edge
    mesh.addPrism(area, lowest - 5, new float [] {base + wall + (pond ? 2 : 4) - (above ? 12 : 0), 0, 0}, water, edge);
    mesh.addPrism(ring, lowest - 5, new float [] {base + wall + (pond ? 9 : 10), 0, 0}, edge, edge);
    float deckWidth = pond ? 0 : (float)Math.max(0, Math.min(1000, command.getDouble("deck_width", 0)));
    if (deckWidth > 0 && !above) {
      float [][] deck = AssistantFenceCommands.offsetLine(polygon, true, (coping + deckWidth) * outside);
      Area deckArea = AssistantMeshBuilder.getArea(deck);
      deckArea.subtract(whole);
      AssistantMeshBuilder.Material paving = mesh.getMaterial("stucco", 60, 60, 0xC9C2B4, 0xC9C2B4, 0);
      mesh.addPrism(deckArea, lowest - 5, new float [] {base + 6, 0, 0}, paving, paving);
    }
    String name = pond ? "Pond" : "Pool";
    HomePieceOfFurniture piece = addFixed(mesh, command, name, name.toLowerCase(Locale.ENGLISH), tally, notes, "site",
        name.toLowerCase(Locale.ENGLISH));
    notes.add("Added the " + name.toLowerCase(Locale.ENGLISH) + " " + this.executor.getId(piece) + " \"" + piece.getName() + "\": "
        + format(Math.abs(getArea(polygon)) / 10000) + " m2 of water" + (above ? ", " + format(wall) + " cm above the ground" : "")
        + (deckWidth > 0 ? ", with " + format(deckWidth) + " cm of paving around it" : "")
        + (pond || above ? "" : ". The ground isn't dug under it. A fence with \"style\":\"pool_code\" around it is checked by check_home"));
    warnUnknown(command, notes, "points", "polygon", "type", "diameter", "deck_width", "coping_color");
  }

  private void addFirepit(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float diameter = number(command, "diameter", (float)command.getDouble("width", 110), 40, 400);
    float height = number(command, "height", 40, 10, 120);
    Build build = new Build();
    AssistantMeshBuilder.Material stone = build.pattern("ashlar", 60, 40, color(command, "color", 0x8C8478));
    build.cylinder(0, 0, diameter / 2, 0, height, 12, stone);
    build.cylinder(0, 0, diameter / 2 - 14, height, height + 0.5f, 12, build.color(0x2A2420));
    if (command.getBoolean("seat_wall", false)) {
      // A low wall on half a circle around it
      float radius = diameter / 2 + 150;
      for (int i = 0; i < 8; i++) {
        double angle = Math.PI * (i / 8.) + Math.PI;
        double next = Math.PI * ((i + 1) / 8.) + Math.PI;
        build.mesh.addBar(new float [] {(float)Math.cos(angle) * radius, 45, (float)Math.sin(angle) * radius},
            new float [] {(float)Math.cos(next) * radius, 45, (float)Math.sin(next) * radius}, 40, 45, stone);
      }
    }
    HomePieceOfFurniture piece = place(build, command, "Fire pit", "firepit", tally, notes, "site", "firepit");
    notes.add("Added the fire pit " + this.executor.getId(piece) + ": " + format(diameter) + " cm across"
        + (command.getBoolean("seat_wall", false) ? ", with a seat wall around its back" : ""));
    warnUnknown(command, notes, "diameter", "seat_wall");
  }

  private void addOutdoorKitchen(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float width = number(command, "width", 300, 100, 1000);
    float depth = number(command, "depth", 70, 40, 200);
    float height = number(command, "height", 92, 60, 130);
    Build build = new Build();
    build.box(-width / 2, -depth / 2, width / 2, depth / 2, 0, height - 4, build.pattern("ashlar", 80, 60, color(command, "color", 0x9A8E80)));
    build.box(-width / 2 - 3, -depth / 2 - 3, width / 2 + 3, depth / 2 + 3, height - 4, height, build.color(0x3A3A3A));
    if (command.getBoolean("grill", true)) {
      build.box(-45, -depth / 2 + 6, 45, depth / 2 - 6, height, height + 28, build.color(0xB8BCC0));
      build.box(-40, depth / 2 - 6, 40, depth / 2 - 3, height + 12, height + 16, build.color(0x2A2A2A));
    }
    HomePieceOfFurniture piece = place(build, command, "Outdoor kitchen", "outdoor_kitchen", tally, notes, "site", "outdoor_kitchen");
    notes.add("Added the outdoor kitchen " + this.executor.getId(piece) + ": a counter " + format(width) + " cm long"
        + (command.getBoolean("grill", true) ? " with a grill" : ""));
    warnUnknown(command, notes, "grill");
  }

  // ---- Driveways, walls and steps ----

  private void addDriveway(String action, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    boolean curb = "add_curb".equals(action);
    boolean sidewalk = "add_sidewalk".equals(action);
    String material = command.getString("material") != null ? command.getString("material").trim().toLowerCase(Locale.ENGLISH)
        : curb || sidewalk ? "concrete" : "concrete";
    int color = color(command, "color", material.equals("asphalt") ? 0x3A3A3C : material.equals("gravel") ? 0xA8A294
        : material.equals("pavers") ? 0xA8705A : 0xBDB8AE);
    String name = command.getString("name") != null ? command.getString("name") : curb ? "Curb" : sidewalk ? "Sidewalk" : "Driveway";
    if (command.getParameters().get("polygon") instanceof List) {
      // Any shape, as a slab
      Map<String, Object> slab = new LinkedHashMap<String, Object>();
      slab.put("points", command.getParameters().get("polygon"));
      slab.put("thickness", Double.valueOf(command.getDouble("thickness", 10)));
      slab.put("color", String.format("#%06X", color));
      slab.put("name", name);
      HomePieceOfFurniture piece = run("add_slab", slab, tally, notes);
      notes.add("Added the " + name.toLowerCase(Locale.ENGLISH) + " " + (piece != null ? this.executor.getId(piece) : "")
          + " over its polygon, as a slab of " + material);
      return;
    }
    List<float []> points = command.getPoints("points");
    if (points.size() < 2) {
      throw new IOException("give its center line \"points\":[[x,y],[x,y],...], or \"polygon\":[...] for any shape");
    }
    float width = number(command, "width", curb ? 15 : sidewalk ? 150 : 350, 5, 3000);
    if (command.getBoolean("grade", false)) {
      Map<String, Object> grading = new LinkedHashMap<String, Object>();
      grading.put("points", command.getParameters().get("points"));
      grading.put("width", Double.valueOf(width));
      run("grade_path", grading, tally, notes);
    }
    Map<String, Object> path = new LinkedHashMap<String, Object>();
    path.put("points", command.getParameters().get("points"));
    path.put("width", Double.valueOf(width));
    path.put("thickness", Double.valueOf(command.getDouble(curb ? "height" : "thickness", curb ? 15 : 8)));
    path.put("color", String.format("#%06X", color));
    path.put("name", name);
    if (command.getString("level") != null) {
      path.put("level", command.getString("level"));
    }
    HomePieceOfFurniture piece = run("add_path", path, tally, notes);
    String apronText = "";
    float apron = (float)command.getDouble("apron", curb || sidewalk ? 0 : 0);
    if (apron > 0 && piece != null) {
      // A flare at its last point, where it meets the street
      float [] last = points.get(points.size() - 1);
      float [] previous = points.get(points.size() - 2);
      double length = Math.hypot(last [0] - previous [0], last [1] - previous [1]);
      float dx = (float)((last [0] - previous [0]) / length);
      float dy = (float)((last [1] - previous [1]) / length);
      float reach = Math.min((float)length, apron);
      float [][] flare = {{last [0] - dx * reach + dy * width / 2, last [1] - dy * reach - dx * width / 2},
                          {last [0] + dy * (width / 2 + apron), last [1] - dx * (width / 2 + apron)},
                          {last [0] - dy * (width / 2 + apron), last [1] + dx * (width / 2 + apron)},
                          {last [0] - dx * reach - dy * width / 2, last [1] - dy * reach + dx * width / 2}};
      Map<String, Object> slab = new LinkedHashMap<String, Object>();
      slab.put("points", toList(flare));
      slab.put("thickness", Double.valueOf(8));
      slab.put("color", String.format("#%06X", color));
      slab.put("name", name + " apron");
      HomePieceOfFurniture flarePiece = run("add_slab", slab, tally, notes);
      if (flarePiece != null) {
        Level level = flarePiece.getLevel();
        flarePiece.setElevation(ground(last [0], last [1], level) - 4);
        apronText = ", with the apron " + this.executor.getId(flarePiece) + " at its last point";
      }
    }
    if (piece != null) {
      piece.setProperty(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(getTags(command, "garden", "site",
          curb ? "curb" : sidewalk ? "sidewalk" : "driveway")));
    }
    double length = 0;
    for (int i = 0; i < points.size() - 1; i++) {
      length += Math.hypot(points.get(i + 1) [0] - points.get(i) [0], points.get(i + 1) [1] - points.get(i) [1]);
    }
    notes.add("Added the " + name.toLowerCase(Locale.ENGLISH) + " " + (piece != null ? this.executor.getId(piece) : "") + ": "
        + format(length / 100) + " m long and " + format(width) + " cm wide, of " + material + ", laid on the ground" + apronText
        + (command.getBoolean("grade", false) ? ", the ground graded under it" : ""));
    warnUnknown(command, notes, "points", "polygon", "material", "thickness", "apron", "grade");
  }

  private void addRetainingWall(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<float []> points = command.getPoints("points");
    if (points.size() < 2) {
      throw new IOException("give the line of the wall: \"points\":[[x,y],[x,y],...]");
    }
    float height = number(command, "height", 90, 10, 600);
    float terrace = number(command, "terrace_width", 300, 50, 5000);
    String lowSide = command.getString("low_side") != null ? command.getString("low_side").trim().toLowerCase(Locale.ENGLISH) : "right";
    boolean left = lowSide.startsWith("l");
    float [][] line = points.toArray(new float [points.size()][]);
    boolean terrain = false;
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      terrain |= HomeTerrain.isTerrain(piece);
    }
    String groundText = ". The home has no terrain: the wall is built, the ground isn't cut";
    if (terrain) {
      this.executor.getTerrainCommands().cutStep(line, left, height, terrace, tally, notes);
      groundText = ", the ground of its " + (left ? "left" : "right") + " side cut down by " + format(height) + " cm over "
          + format(terrace) + " cm";
    }
    Map<String, Object> fence = new LinkedHashMap<String, Object>();
    fence.put("style", command.getString("style") != null ? command.getString("style") : "retaining_wall");
    fence.put("points", command.getParameters().get("points"));
    fence.put("retains", left ? "right" : "left");
    fence.put("height", Double.valueOf(command.getDouble("reveal", 15)));
    fence.put("name", command.getString("name") != null ? command.getString("name") : "Retaining wall");
    for (String key : new String [] {"stone", "color", "thickness", "pattern", "level", "owner"}) {
      if (command.getParameters().get(key) != null) {
        fence.put(key, command.getParameters().get(key));
      }
    }
    HomePieceOfFurniture wall = run("add_fence", fence, tally, notes);
    notes.add("Added the retaining wall " + (wall != null ? this.executor.getId(wall) : "") + groundText);
  }

  private void addSteps(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<float []> points = command.getPoints("points");
    if (points.size() != 2) {
      throw new IOException("give the two ends of the steps: \"points\":[[x,y],[x,y]]");
    }
    Level level = this.executor.getShapeCommands().getLevel(command, "add_steps_on_slope", notes);
    float width = number(command, "width", 120, 40, 1000);
    float maxRise = number(command, "rise", 17, 8, 25);
    String material = command.getString("material") != null ? command.getString("material").toLowerCase(Locale.ENGLISH) : "stone";
    float [] a = points.get(0);
    float [] b = points.get(1);
    float groundA = ground(a [0], a [1], level);
    float groundB = ground(b [0], b [1], level);
    if (groundA > groundB) {
      // From the bottom up
      float [] swap = a;
      a = b;
      b = swap;
      float swapGround = groundA;
      groundA = groundB;
      groundB = swapGround;
    }
    double length = Math.hypot(b [0] - a [0], b [1] - a [1]);
    float climb = groundB - groundA;
    if (length < 20 || climb < 5) {
      throw new IOException("the ground climbs by " + format(climb) + " cm over " + format(length) + " cm between these points: "
          + "no step is needed");
    }
    int steps = Math.max(1, (int)Math.ceil(climb / maxRise));
    float rise = climb / steps;
    float run = (float)length / steps;
    float dx = (float)((b [0] - a [0]) / length);
    float dy = (float)((b [1] - a [1]) / length);
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    int color = color(command, "color", material.startsWith("wood") ? 0x8B6B47 : material.startsWith("conc") ? 0xBDB8AE : 0x8C8478);
    AssistantMeshBuilder.Material tread = material.startsWith("stone") ? mesh.getMaterial("ashlar", 120, 100, color, shade(color, 0.6f), 0)
        : mesh.getMaterial(color);
    for (int i = 0; i < steps; i++) {
      float x = a [0] + dx * run * i;
      float y = a [1] + dy * run * i;
      float top = groundA + rise * (i + 1);
      float [][] section = {{x - dy * width / 2, groundA + rise * i - 20, y + dx * width / 2},
                            {x + dy * width / 2, groundA + rise * i - 20, y - dx * width / 2},
                            {x + dy * width / 2, top, y - dx * width / 2}, {x - dy * width / 2, top, y + dx * width / 2}};
      mesh.addSolid(section, new float [] {dx * run, 0, dy * run}, tread);
    }
    HomePieceOfFurniture piece = addFixed(mesh, command, "Steps", "steps", tally, notes, "site", "steps");
    notes.add("Added the steps " + this.executor.getId(piece) + ": " + steps + " steps of " + format(rise) + " cm, each "
        + format(run) + " cm deep, climbing " + format(climb) + " cm over " + format(length / 100) + " m"
        + (run < 26 ? ". They are steep: longer steps need the two points farther apart" : ""));
    warnUnknown(command, notes, "points", "rise", "material");
  }

  // ---- Drainage, utilities and beds ----

  private void addDrainage(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String type = command.getString("type") != null ? command.getString("type").trim().toLowerCase(Locale.ENGLISH) : "";
    if (!DRAINAGE.contains(type)) {
      throw new IOException("\"type\" is one of " + DRAINAGE);
    }
    if (type.equals("catch_basin")) {
      Build build = new Build();
      build.box(-25, -25, 25, 25, -2, 2, build.color(0x3A3A3A));
      build.box(-20, -20, 20, 20, 2, 2.5f, build.mesh.getMaterial("grid", 5, 5, 0x1E1E1E, 0x1E1E1E, 0.4f));
      HomePieceOfFurniture piece = place(build, command, "Catch basin", "drainage", tally, notes, "site", "drainage");
      notes.add("Added the catch basin " + this.executor.getId(piece));
      return;
    }
    List<float []> points = command.getPoints("points");
    if (points.size() < 2) {
      throw new IOException("give the line it follows: \"points\":[[x,y],[x,y],...]");
    }
    Map<String, Object> path = new LinkedHashMap<String, Object>();
    path.put("points", command.getParameters().get("points"));
    float width = type.equals("downspout_extension") ? 12 : type.equals("french_drain") ? 40 : type.equals("culvert") ? 60 : 120;
    path.put("width", Double.valueOf(command.getDouble("width", width)));
    path.put("thickness", Double.valueOf(type.equals("downspout_extension") ? 10 : type.equals("culvert") ? 45 : 5));
    path.put("color", type.equals("downspout_extension") ? "#E6E2D8" : type.equals("french_drain") ? "#9A968C"
        : type.equals("culvert") ? "#6E7275" : "#8C8478");
    path.put("name", command.getString("name") != null ? command.getString("name") : type.replace('_', ' '));
    HomePieceOfFurniture piece = run("add_path", path, tally, notes);
    if (piece != null) {
      piece.setProperty(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(getTags(command, "garden", "site", "drainage")));
    }
    double length = 0;
    for (int i = 0; i < points.size() - 1; i++) {
      length += Math.hypot(points.get(i + 1) [0] - points.get(i) [0], points.get(i + 1) [1] - points.get(i) [1]);
    }
    notes.add("Added the " + type.replace('_', ' ') + " " + (piece != null ? this.executor.getId(piece) : "") + ": "
        + format(length / 100) + " m long, drawn on the ground");
  }

  private void addUtility(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String type = command.getString("type") != null ? command.getString("type").trim().toLowerCase(Locale.ENGLISH).replace(' ', '_') : "";
    if (!UTILITIES.contains(type)) {
      throw new IOException("\"type\" is one of " + UTILITIES);
    }
    Build build = new Build();
    AssistantMeshBuilder.Material grey = build.color(color(command, "color", 0x9EA3A6));
    AssistantMeshBuilder.Material dark = build.color(0x3A3A3A);
    String extra = "";
    if (type.equals("ac_condenser")) {
      build.box(-45, -45, 45, 45, 0, 8, build.color(0xBDB8AE));
      build.box(-38, -38, 38, 38, 8, 85, grey);
      build.cylinder(0, 0, 28, 85, 86, 12, dark);
    } else if (type.equals("generator")) {
      build.box(-65, -35, 65, 35, 0, 8, build.color(0xBDB8AE));
      build.box(-60, -32, 60, 32, 8, 75, build.color(color(command, "color", 0xC9B88A)));
    } else if (type.equals("meter")) {
      build.box(-12, -5, 12, 5, 120, 160, grey);
      build.cylinder(0, 6, 9, 135, 152, 10, build.mesh.getMaterial(0xDCE4E8, 0.5f));
      build.box(-2, -2, 2, 2, 0, 120, grey);
    } else if (type.equals("propane_tank")) {
      float length = number(command, "width", 240, 60, 600);
      build.mesh.addSolid(octagon(-length / 2, 60, 0, 40, true), new float [] {length, 0, 0}, build.color(color(command, "color", 0xECECE6)));
      build.box(-length / 3, -15, -length / 3 + 12, 15, 0, 25, dark);
      build.box(length / 3 - 12, -15, length / 3, 15, 0, 25, dark);
    } else if (type.equals("septic_marker") || type.equals("well_marker")) {
      build.cylinder(0, 0, type.startsWith("well") ? 12 : 30, 0, type.startsWith("well") ? 45 : 5, 12,
          build.color(type.startsWith("well") ? 0x2F5D8C : 0x3F6B35));
    } else if (type.equals("utility_pole")) {
      float height = number(command, "height", 1000, 300, 2500);
      build.cylinder(0, 0, 14, 0, height, 8, build.color(0x6B4F34));
      build.box(-110, -5, 110, 5, height - 80, height - 68, build.color(0x6B4F34));
      if (command.getParameters().get("to") instanceof List) {
        extra = ". Its wires aren't drawn: add_polyline with \"visible_in_3d\":true and \"elevation\" draws a line to the next pole";
      }
    } else if (type.equals("transformer_box")) {
      build.box(-60, -55, 60, 55, 0, 10, build.color(0xBDB8AE));
      build.box(-55, -50, 55, 50, 10, 95, build.color(color(command, "color", 0x3F6B4A)));
    } else if (type.equals("mailbox")) {
      String style = command.getString("style") != null ? command.getString("style").toLowerCase(Locale.ENGLISH) : "post";
      if (style.equals("brick")) {
        build.box(-25, -25, 25, 25, 0, 130, build.pattern("brick_running", 43, 30, 0x9C4A35));
        build.box(-28, -28, 28, 28, 130, 136, build.color(0xD8D2C4));
        build.box(-10, 25, 10, 27, 95, 118, dark);
      } else if (style.equals("cluster")) {
        build.box(-8, -8, 8, 8, 0, 70, dark);
        build.box(-40, -22, 40, 22, 70, 160, grey);
      } else {
        build.post(0, 0, 9, 0, 105, build.color(0x8B6B47));
        build.box(-9, -8, 9, 38, 105, 127, build.color(color(command, "color", 0x2A2A2A)));
      }
    } else if (type.equals("street_light")) {
      float height = number(command, "height", 500, 200, 1500);
      build.cylinder(0, 0, 7, 0, height, 8, dark);
      build.box(-4, 0, 4, 90, height - 6, height, dark);
      build.box(-12, 70, 12, 105, height - 12, height - 6, build.color(0xFFF3C0));
    } else if (type.equals("street_sign")) {
      build.cylinder(0, 0, 3, 0, 250, 6, grey);
      build.box(-38, -1, 38, 1, 215, 235, build.color(color(command, "color", 0x2F6B4A)));
      if (command.getString("text") != null) {
        extra = " \"" + command.getString("text") + "\" (its text isn't written on it: add_label names it in the plan)";
      }
    } else if (type.equals("fire_hydrant")) {
      build.cylinder(0, 0, 11, 0, 55, 8, build.color(color(command, "color", 0xC8281E)));
      build.cylinder(0, 0, 8, 55, 68, 8, build.color(color(command, "color", 0xC8281E)));
      build.box(-18, -4, 18, 4, 36, 46, build.color(color(command, "color", 0xC8281E)));
    }
    String name = type.replace('_', ' ');
    name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    HomePieceOfFurniture piece = place(build, command, name, "utility", tally, notes, "site", "utility", type);
    notes.add("Added the " + type.replace('_', ' ') + " " + this.executor.getId(piece) + extra + ": " + format(piece.getWidth())
        + " x " + format(piece.getDepth()) + " x " + format(piece.getHeight()) + " cm");
    warnUnknown(command, notes, "type", "style", "to", "text");
  }

  private static float [][] octagon(float x, float y, float z, float radius, boolean acrossX) {
    float [][] section = new float [8][];
    for (int i = 0; i < 8; i++) {
      double angle = Math.PI * 2 * i / 8;
      section [i] = new float [] {x, y + (float)Math.sin(angle) * radius, z + (float)Math.cos(angle) * radius};
    }
    return section;
  }

  private void buildRaisedBed(Build build, float x, float z, float width, float depth, float height, String material, int color) {
    AssistantMeshBuilder.Material frame = material.startsWith("stone") ? build.pattern("ashlar", 80, 60, color)
        : material.startsWith("metal") ? build.pattern("corrugated", 30, 30, color) : build.color(color);
    float board = material.startsWith("stone") ? 15 : 4;
    build.box(x - width / 2, z - depth / 2, x + width / 2, z - depth / 2 + board, 0, height, frame);
    build.box(x - width / 2, z + depth / 2 - board, x + width / 2, z + depth / 2, 0, height, frame);
    build.box(x - width / 2, z - depth / 2 + board, x - width / 2 + board, z + depth / 2 - board, 0, height, frame);
    build.box(x + width / 2 - board, z - depth / 2 + board, x + width / 2, z + depth / 2 - board, 0, height, frame);
    build.box(x - width / 2 + board, z - depth / 2 + board, x + width / 2 - board, z + depth / 2 - board, 0, height - 5,
        build.pattern("mulch", 40, 40, 0x4A3524));
  }

  private void addRaisedBed(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float width = number(command, "width", 240, 30, 2000);
    float depth = number(command, "depth", 120, 30, 2000);
    float height = number(command, "height", 30, 8, 150);
    String material = command.getString("material") != null ? command.getString("material").toLowerCase(Locale.ENGLISH) : "wood";
    Build build = new Build();
    buildRaisedBed(build, 0, 0, width, depth, height, material,
        color(command, "color", material.startsWith("stone") ? 0x8C8478 : material.startsWith("metal") ? 0x9EA3A6 : 0x8B6B47));
    HomePieceOfFurniture piece = place(build, command, "Raised bed", "raised_bed", tally, notes, "site", "raised_bed");
    notes.add("Added the raised bed " + this.executor.getId(piece) + ": " + format(width) + " x " + format(depth) + " cm, "
        + format(height) + " cm high, of " + material);
    warnUnknown(command, notes, "material");
  }

  private void addGardenEnclosure(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Map<?, ?> beds = command.getParameters().get("beds") instanceof Map ? (Map<?, ?>)command.getParameters().get("beds") : Collections.emptyMap();
    int rows = Math.max(1, Math.min(10, Math.round(AssistantFurnitureBuilder.getNumber(beds, "rows", 2))));
    int columns = Math.max(1, Math.min(10, Math.round(AssistantFurnitureBuilder.getNumber(beds, "columns", 3))));
    float bedWidth = Math.max(40, AssistantFurnitureBuilder.getNumber(beds, "width", 240));
    float bedDepth = Math.max(40, AssistantFurnitureBuilder.getNumber(beds, "depth", 120));
    float bedHeight = Math.max(8, AssistantFurnitureBuilder.getNumber(beds, "height", 30));
    float path = Math.max(30, (float)command.getDouble("path", 90));
    String material = beds.get("material") != null ? beds.get("material").toString().toLowerCase(Locale.ENGLISH) : "wood";
    float width = columns * bedWidth + (columns + 1) * path;
    float depth = rows * bedDepth + (rows + 1) * path;
    Build build = new Build();
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        buildRaisedBed(build, -width / 2 + path + bedWidth / 2 + column * (bedWidth + path),
            -depth / 2 + path + bedDepth / 2 + row * (bedDepth + path), bedWidth, bedDepth, bedHeight, material, 0x8B6B47);
      }
    }
    HomePieceOfFurniture piece = place(build, command, "Garden beds", "raised_bed", tally, notes, "site", "raised_bed");
    // The fence around them, with a gate in the middle of its front
    float x = (float)command.getDouble("x", 0);
    float y = (float)command.getDouble("y", 0);
    double radians = Math.toRadians(command.getDouble("angle", 0));
    float [][] corners = {{-width / 2, -depth / 2}, {width / 2, -depth / 2}, {width / 2, depth / 2}, {-width / 2, depth / 2}};
    for (float [] corner : corners) {
      float cornerX = corner [0];
      corner [0] = x + (float)(cornerX * Math.cos(radians) - corner [1] * Math.sin(radians));
      corner [1] = y + (float)(cornerX * Math.sin(radians) + corner [1] * Math.cos(radians));
    }
    Map<String, Object> fence = new LinkedHashMap<String, Object>();
    if (command.getParameters().get("fence") instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)command.getParameters().get("fence")).entrySet()) {
        fence.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    if (fence.get("style") == null) {
      fence.put("style", "garden_wire_tpost");
    }
    if (fence.get("height") == null) {
      fence.put("height", Double.valueOf(150));
    }
    fence.put("points", toList(corners));
    fence.put("closed", Boolean.TRUE);
    fence.put("name", "Garden fence");
    Map<String, Object> gate = new LinkedHashMap<String, Object>();
    gate.put("at", Double.valueOf(width + depth + width / 2));
    gate.put("width", Double.valueOf(Math.min(100, path + 10)));
    gate.put("type", "single_swing");
    fence.put("openings", Collections.singletonList(gate));
    HomePieceOfFurniture fencePiece = run("add_fence", fence, tally, notes);
    notes.add("Added the garden " + this.executor.getId(piece) + ": " + rows * columns + " raised beds of " + format(bedWidth) + " x "
        + format(bedDepth) + " cm with paths of " + format(path) + " cm, " + format(width) + " x " + format(depth) + " cm in all"
        + (fencePiece != null ? ", inside the fence " + this.executor.getId(fencePiece) + " (" + fence.get("style")
            + ") with a gate in the middle of its front" : ""));
    warnUnknown(command, notes, "beds", "path", "fence");
  }

  private void addCallout(AssistantCommand command, List<String> notes) throws IOException {
    String text = command.getString("text");
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    if (text == null || text.trim().length() == 0 || Double.isNaN(x) || Double.isNaN(y)) {
      throw new IOException("give its \"text\" and its place \"x\",\"y\"; \"to\":[x,y] draws a line to what it names");
    }
    Level level = this.executor.getShapeCommands().getLevel(command, "add_callout", notes);
    Label label = new Label(text.trim(), (float)x, (float)y);
    label.setLevel(level);
    this.executor.beginTurnUpdate();
    this.homeController.getPlanController().addLabels(Collections.singletonList(label));
    String leader = "";
    if (command.getParameters().get("to") instanceof List && ((List<?>)command.getParameters().get("to")).size() >= 2
        && ((List<?>)command.getParameters().get("to")).get(0) instanceof Number) {
      List<?> to = (List<?>)command.getParameters().get("to");
      float [][] line = {{(float)x, (float)y + 8}, {((Number)to.get(0)).floatValue(), ((Number)to.get(1)).floatValue()}};
      Polyline polyline = this.executor.getSiteCommands().createPolyline(line, false, level, text.trim(), "callout",
          command.getString("color"), 1, null, false, Double.NaN);
      polyline.setEndArrowStyle(Polyline.ArrowStyle.DELTA);
      this.executor.getSiteCommands().addPolylines(Collections.singletonList(polyline), level);
      leader = ", with the line " + this.executor.getId(polyline) + " to (" + format(line [1][0]) + ", " + format(line [1][1]) + ")";
    }
    notes.add("Added the callout \"" + text.trim() + "\" at (" + format(x) + ", " + format(y) + ")" + leader);
  }

  // ---- Context ----

  private void addContextBuilding(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float [][] polygon = getPolygon(command, 1200, 900);
    Level level = this.executor.getShapeCommands().getLevel(command, "add_context_building", notes);
    float stories = number(command, "stories", 1, 0.5f, 60);
    float height = number(command, "height", stories * 280 + 30, 100, 20000);
    String detail = command.getString("detail") != null ? command.getString("detail").toLowerCase(Locale.ENGLISH)
        : this.home.getProperty(CONTEXT_DETAIL_PROPERTY) != null ? this.home.getProperty(CONTEXT_DETAIL_PROPERTY) : "textured";
    String siding = command.getString("siding") != null ? command.getString("siding").trim().toLowerCase(Locale.ENGLISH) : "siding";
    int color = color(command, "color", siding.startsWith("brick") ? 0x9C4A35 : siding.startsWith("stucco") ? 0xE0D5C0 : 0xD8D2C4);
    String roof = command.getString("roof") != null ? command.getString("roof").trim().toLowerCase(Locale.ENGLISH) : "hip";
    if (!Arrays.asList("hip", "gable", "flat", "shed").contains(roof)) {
      throw new IOException("\"roof\" is hip, gable, flat or shed");
    }
    Area area = AssistantMeshBuilder.getArea(polygon);
    if (area.isEmpty()) {
      throw new IOException("the points enclose no area");
    }
    float base = Float.MAX_VALUE;
    float highest = -Float.MAX_VALUE;
    for (float [] point : polygon) {
      base = Math.min(base, ground(point [0], point [1], level));
      highest = Math.max(highest, ground(point [0], point [1], level));
    }
    AssistantMeshBuilder mesh = new AssistantMeshBuilder();
    AssistantMeshBuilder.Material walls;
    if (detail.equals("massing")) {
      walls = mesh.getMaterial(color);
    } else if (siding.startsWith("brick")) {
      walls = mesh.getMaterial("brick_running", 43, 30, color, 0xC9C2B4, 0);
    } else if (siding.startsWith("stucco")) {
      walls = mesh.getMaterial("stucco", 40, 40, color, color, 0);
    } else if (detail.equals("detailed")) {
      // Siding with a window on each floor every 3 m
      walls = mesh.getMaterial("windows", 300, 280, color, 0x5A7080, 0);
    } else {
      walls = mesh.getMaterial("siding", 100, 60, color, color, 0);
    }
    float top = highest + height;
    mesh.addPrism(area, base - 20, new float [] {top, 0, 0}, mesh.getMaterial(shade(color, 0.85f)), walls);
    // Garage doors on its edges
    int garageDoors = 0;
    if (command.getParameters().get("garage_doors") instanceof List && !detail.equals("massing")) {
      float outside = getArea(polygon) > 0 ? 1 : -1;
      for (Object item : (List<?>)command.getParameters().get("garage_doors")) {
        if (!(item instanceof Map)) {
          continue;
        }
        Map<?, ?> door = (Map<?, ?>)item;
        int edge = Math.max(1, Math.min(polygon.length, Math.round(AssistantFurnitureBuilder.getNumber(door, "edge", 1)))) - 1;
        float doorWidth = Math.max(150, Math.min(900, AssistantFurnitureBuilder.getNumber(door, "width", 480)));
        float at = Math.max(0, Math.min(1, AssistantFurnitureBuilder.getNumber(door, "at", 0.5f)));
        float [] a = polygon [edge];
        float [] b = polygon [(edge + 1) % polygon.length];
        double length = Math.hypot(b [0] - a [0], b [1] - a [1]);
        float dx = (float)((b [0] - a [0]) / length);
        float dy = (float)((b [1] - a [1]) / length);
        float middle = (float)Math.max(doorWidth / 2, Math.min(length - doorWidth / 2, length * at));
        float x = a [0] + dx * middle;
        float y = a [1] + dy * middle;
        float doorBase = ground(x, y, level);
        float [][] section = {{x - dx * doorWidth / 2, doorBase, y - dy * doorWidth / 2}, {x + dx * doorWidth / 2, doorBase, y + dy * doorWidth / 2},
                              {x + dx * doorWidth / 2, doorBase + 215, y + dy * doorWidth / 2}, {x - dx * doorWidth / 2, doorBase + 215, y - dy * doorWidth / 2}};
        mesh.addSolid(section, new float [] {dy * outside * 4, 0, -dx * outside * 4},
            mesh.getMaterial("hboards", 100, 50, 0xF2EFE6, 0xB8B4AA, 0));
        garageDoors++;
      }
    }
    HomePieceOfFurniture building = addFixed(mesh, command, "Neighbor house", "context_building", tally, notes, "site", "neighbor",
        "context");
    String roofText = ", under a flat roof";
    if (!roof.equals("flat") && !detail.equals("massing")) {
      // The roof that add_roof builds over the same polygon
      Map<String, Object> roofSpec = new LinkedHashMap<String, Object>();
      roofSpec.put("points", toList(polygon));
      roofSpec.put("eave_height", Double.valueOf(top - (level != null ? 0 : 0)));
      roofSpec.put("type", roof);
      roofSpec.put("pitch", Double.valueOf(command.getDouble("pitch", 25)));
      roofSpec.put("overhang", Double.valueOf(command.getDouble("overhang", 30)));
      roofSpec.put("color", command.getString("roof_color") != null ? command.getString("roof_color") : "#4E4A46");
      roofSpec.put("gable_walls", Boolean.TRUE);
      roofSpec.put("gable_color", String.format("#%06X", color));
      if (command.getString("level") != null) {
        roofSpec.put("level", command.getString("level"));
      }
      try {
        HomePieceOfFurniture roofPiece = run("add_roof", roofSpec, tally, notes);
        if (roofPiece != null) {
          roofPiece.setName(building.getName() + " roof");
          roofPiece.setProperty(HomeItemTags.TAGS_PROPERTY, HomeItemTags.formatTags(getTags(command, "site", "neighbor", "context")));
          roofText = ", under the " + roof + " roof " + this.executor.getId(roofPiece);
        }
      } catch (IOException ex) {
        roofText = ", under a flat roof (a " + roof + " roof couldn't be built over this shape: " + ex.getMessage() + ")";
      }
    }
    if (command.getString("owner") != null) {
      building.setProperty("owner", command.getString("owner"));
    }
    notes.add("Added the building " + this.executor.getId(building) + " \"" + building.getName() + "\" (" + detail + "): "
        + format(Math.abs(getArea(polygon)) / 10000) + " m2 on the ground, walls " + format(height) + " cm high" + roofText
        + (garageDoors > 0 ? ", " + garageDoors + (garageDoors == 1 ? " garage door" : " garage doors") : "")
        + ". It has the tag neighbor: {\"action\":\"hide_layer\",\"tag\":\"neighbor\"} hides every neighbor");
    warnUnknown(command, notes, "points", "polygon", "stories", "roof", "pitch", "overhang", "siding", "roof_color", "garage_doors",
        "detail");
  }

  private void addVehicle(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String type = command.getString("type") != null ? command.getString("type").trim().toLowerCase(Locale.ENGLISH) : "car";
    float length = type.equals("pickup") ? 560 : type.equals("van") ? 520 : type.equals("suv") ? 480 : 450;
    float width = type.equals("car") ? 180 : 195;
    float body = type.equals("car") ? 75 : 95;
    float cabin = type.equals("van") ? 100 : type.equals("car") ? 55 : 70;
    Build build = new Build();
    AssistantMeshBuilder.Material paint = build.color(color(command, "color", 0x7A8794));
    AssistantMeshBuilder.Material glass = build.color(0x2E3A44);
    // Its length along z, its front towards the positive z
    build.box(-width / 2, -length / 2, width / 2, length / 2, 22, 22 + body, paint);
    float cabinStart = type.equals("pickup") ? -length * 0.05f : -length * (type.equals("van") ? 0.46f : 0.3f);
    float cabinEnd = type.equals("pickup") ? length * 0.25f : length * (type.equals("van") ? 0.3f : 0.2f);
    build.box(-width / 2 + 8, cabinStart, width / 2 - 8, cabinEnd, 22 + body, 22 + body + cabin, glass);
    build.box(-width / 2 + 6, cabinStart + 6, width / 2 - 6, cabinEnd - 6, 22 + body + cabin, 22 + body + cabin + 3, paint);
    for (float z : new float [] {-length * 0.32f, length * 0.32f}) {
      for (int side = -1; side <= 1; side += 2) {
        build.mesh.addSolid(octagon(side * (width / 2 - 20), 32, z, 32, true), new float [] {side * 22, 0, 0}, build.color(0x1A1A1A));
      }
    }
    String name = Character.toUpperCase(type.charAt(0)) + type.substring(1);
    HomePieceOfFurniture piece = place(build, command, name, "context_vehicle", tally, notes, "site", "context", "vehicle");
    notes.add("Added the " + type + " " + this.executor.getId(piece) + " for the scale: " + format(length) + " cm long, its front "
        + "towards the positive y at the angle 0");
    warnUnknown(command, notes, "type");
  }

  private void addPerson(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    float height = number(command, "height", 170, 50, 230);
    Build build = new Build();
    AssistantMeshBuilder.Material clothes = build.color(color(command, "color", 0x4A6A8A));
    float scale = height / 170;
    build.cylinder(-9 * scale, 0, 7 * scale, 0, 85 * scale, 8, build.color(0x3A3A44));
    build.cylinder(9 * scale, 0, 7 * scale, 0, 85 * scale, 8, build.color(0x3A3A44));
    build.box(-20 * scale, -11 * scale, 20 * scale, 11 * scale, 85 * scale, 145 * scale, clothes);
    build.cylinder(-25 * scale, 0, 5 * scale, 85 * scale, 142 * scale, 6, clothes);
    build.cylinder(25 * scale, 0, 5 * scale, 85 * scale, 142 * scale, 6, clothes);
    build.cylinder(0, 0, 11 * scale, 148 * scale, 170 * scale, 8, build.color(0xD9B38C));
    HomePieceOfFurniture piece = place(build, command, "Person", "context_person", tally, notes, "site", "context", "person");
    notes.add("Added the figure " + this.executor.getId(piece) + " for the scale: " + format(height) + " cm high");
    warnUnknown(command, notes);
  }
}
