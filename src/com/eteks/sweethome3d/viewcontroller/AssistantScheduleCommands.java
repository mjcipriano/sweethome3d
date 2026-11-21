/*
 * AssistantScheduleCommands.java 21 nov. 2026
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * The commands that list doors, windows and symbols in schedules, add electrical and plumbing symbols, and estimate framing.
 */
class AssistantScheduleCommands {
  /** The type of an electrical or plumbing symbol. */
  static final String SYMBOL_TYPE_PROPERTY = HomeReport.SYMBOL_TYPE_PROPERTY;
  /** The circuit of an electrical symbol. */
  static final String SYMBOL_CIRCUIT_PROPERTY = "symbolCircuit";
  /** The mark of a door or a window in the schedules: D1, W2... */
  static final String SCHEDULE_MARK_PROPERTY = "scheduleMark";
  /** The id of the door or the window a label of the plan shows the mark of. */
  static final String MARK_LABEL_PROPERTY = "scheduleMarkOf";

  private static final int    MAX_SYMBOLS_PER_COMMAND = 200;
  private static final double CM_PER_FOOT = 30.48;

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;
  private final Map<String, Object []>   symbolModels = new HashMap<String, Object []>();

  AssistantScheduleCommands(Home home, UserPreferences preferences, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class,
   * each one starting with two spaces and ending with a new line.
   */
  static String getReference() {
    return "  {\"action\":\"door_schedule\",\"level\":\"<level>\",\"tag_marks\":true,\"labels\":true}  (the table of the doors grouped by "
        + "name and size: mark D1, D2... shared by the doors of the same name, width and height, name, width x height, sill height, "
        + "count, levels and ids; \"level\" keeps one level; \"tag_marks\" writes the mark in the property scheduleMark of each door and "
        + "\"labels\" also writes it in the plan next to each one, both undoable)\n"
        + "  {\"action\":\"window_schedule\",\"level\":\"<level>\",\"tag_marks\":true,\"labels\":true}  (the same table for the windows, "
        + "with the marks W1, W2...)\n"
        + "  {\"action\":\"room_schedule\",\"level\":\"<level>\"}  (the table of the rooms: id, name, level, area, perimeter, ceiling height "
        + "or headroom under a roof, floor finish, and net surface of the wall sides that face the room, openings deducted)\n"
        + "  {\"action\":\"schedule\",\"type\":\"doors|windows|doors_and_windows|furniture|symbols|rooms\",\"level\":\"<level>\"}  "
        + "(any of the schedules; furniture is grouped by name and size with counts and prices)\n"
        + "  {\"action\":\"export_schedule\",\"type\":\"doors_and_windows\",\"path\":\"<absolute .csv path>\",\"replace\":false}  "
        + "(writes the table of a schedule in a CSV file in UTF-8, for a spreadsheet; an existing file needs \"replace\":true)\n"
        + "  {\"action\":\"add_symbol\",\"type\":\"outlet\",\"wall\":\"W3\",\"offset\":120,\"side\":\"left|right\",\"height\":30,"
        + "\"label\":\"<text>\",\"circuit\":\"<name>\"}  (an electrical or plumbing symbol, drawn in the plan as on a drawing and shown in "
        + "3D as a small plate or box; types: outlet, double_outlet, switch, three_way_switch, ceiling_light, wall_light, fan, "
        + "smoke_detector, thermostat, data, tv, panel, sink, toilet_rough, shower_drain, hose_bib, water_heater, floor_drain. With "
        + "\"wall\" and \"offset\" (cm from the start of the wall) it stands on the face of the wall and is turned to it, on the given "
        + "side or else on the side of a room; without a wall give \"x\",\"y\" and \"angle\". \"height\" is the height of the center of "
        + "a wall symbol (outlets 30, switches 110, thermostat 150 by default), of the top of a ceiling symbol (the ceiling by default) "
        + "or of the bottom of a floor symbol. It is tagged electrical or plumbing and check_home ignores it)\n"
        + "  {\"action\":\"add_symbols\",\"room\":\"R3\",\"type\":\"outlet\",\"spacing\":360,\"circuit\":\"<name>\"}  (symbols along the "
        + "walls of a room, one every \"spacing\" cm at most on each wall, moved out of the door openings; answers their count and ids)\n"
        + "  {\"action\":\"symbol_schedule\",\"level\":\"<level>\"}  (the count of the symbols by type, by room and level, and by circuit)\n"
        + "  {\"action\":\"list_symbols\"}  (the types of symbols, where each one is fixed and its default height)\n"
        + "  {\"action\":\"framing_estimate\",\"ids\":[\"W1\",\"W2\"],\"level\":\"<level>\",\"stud_spacing\":40.64,\"stud\":\"2x4|2x6\","
        + "\"plates\":3,\"header\":\"2x8\",\"waste\":0.1}  (the lumber of the given straight walls, or of all of them, framed in wood: "
        + "studs every stud_spacing cm plus 2 per opening and 2 per corner, plates, doubled headers over the openings, sheathing and "
        + "drywall surface net of openings; in pieces, meters, feet and board feet. An ESTIMATE for a budget, not a structural design)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("door_schedule".equals(action)) {
        schedule("doors", command, tally, notes);
      } else if ("window_schedule".equals(action)) {
        schedule("windows", command, tally, notes);
      } else if ("opening_schedule".equals(action) || "door_and_window_schedule".equals(action)) {
        schedule("doors_and_windows", command, tally, notes);
      } else if ("room_schedule".equals(action)) {
        schedule("rooms", command, tally, notes);
      } else if ("furniture_schedule".equals(action)) {
        schedule("furniture", command, tally, notes);
      } else if ("symbol_schedule".equals(action)) {
        schedule("symbols", command, tally, notes);
      } else if ("schedule".equals(action)) {
        schedule(getScheduleType(command), command, tally, notes);
      } else if ("export_schedule".equals(action)) {
        exportSchedule(command, notes);
      } else if ("add_symbol".equals(action)) {
        addSymbol(command, tally, notes);
      } else if ("add_symbols".equals(action)) {
        addSymbols(command, tally, notes);
      } else if ("list_symbols".equals(action)) {
        listSymbols(notes);
      } else if ("framing_estimate".equals(action)) {
        estimateFraming(command, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    } catch (RuntimeException ex) {
      notes.add("Couldn't run " + action + " (" + ex + ")");
    }
    return true;
  }

  /**
   * Returns a number with at most <code>digits</code> decimals.
   */
  private static String format(double value, int digits) {
    String text = String.format(Locale.US, "%." + digits + "f", value);
    if (text.indexOf('.') >= 0) {
      text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    return text.equals("-0") ? "0" : text;
  }

  private static String format(double value) {
    return format(value, 1);
  }

  // ---- Tables ----

  /**
   * A schedule: a title, the names of its columns, its rows and remarks under them.
   */
  static final class Table {
    final String          title;
    final String []       headers;
    final List<String []> rows = new ArrayList<String []>();
    final List<String>    remarks = new ArrayList<String>();

    Table(String title, String... headers) {
      this.title = title;
      this.headers = headers;
    }

    String toText() {
      StringBuilder text = new StringBuilder(this.title);
      if (this.rows.isEmpty()) {
        text.append(": none");
      } else {
        text.append(":\n").append(join(this.headers, " | "));
        for (String [] row : this.rows) {
          text.append('\n').append(join(row, " | "));
        }
      }
      for (String remark : this.remarks) {
        text.append('\n').append(remark);
      }
      return text.toString();
    }

    String toCsv() {
      StringBuilder csv = new StringBuilder();
      appendCsvRow(csv, this.headers);
      for (String [] row : this.rows) {
        appendCsvRow(csv, row);
      }
      return csv.toString();
    }

    private static void appendCsvRow(StringBuilder csv, String [] row) {
      for (int i = 0; i < row.length; i++) {
        if (i > 0) {
          csv.append(',');
        }
        csv.append(quoteCsv(row [i]));
      }
      csv.append("\r\n");
    }
  }

  private static String join(String [] values, String separator) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      text.append(i > 0 ? separator : "").append(values [i]);
    }
    return text.toString();
  }

  private static String join(Iterable<String> values, String separator) {
    StringBuilder text = new StringBuilder();
    for (String value : values) {
      text.append(text.length() > 0 ? separator : "").append(value);
    }
    return text.toString();
  }

  /**
   * Returns a value of a CSV file, between quotes if it contains a comma, a quote, a new line
   * or spaces at its ends, its quotes being doubled.
   */
  static String quoteCsv(String value) {
    if (value == null) {
      return "";
    }
    boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0 || value.indexOf(';') >= 0
        || value.length() > 0 && (value.charAt(0) == ' ' || value.charAt(value.length() - 1) == ' ');
    return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
  }

  // ---- Schedules ----

  private static String getScheduleType(AssistantCommand command) throws IOException {
    String type = command.getString("type");
    if (type == null) {
      throw new IOException("\"type\" is doors, windows, doors_and_windows, furniture, symbols or rooms");
    }
    String text = type.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
    if (text.equals("doors") || text.equals("door")) {
      return "doors";
    } else if (text.equals("windows") || text.equals("window")) {
      return "windows";
    } else if (text.equals("doors_and_windows") || text.equals("openings") || text.equals("doors_windows")) {
      return "doors_and_windows";
    } else if (text.equals("furniture")) {
      return "furniture";
    } else if (text.equals("symbols") || text.equals("symbol")) {
      return "symbols";
    } else if (text.equals("rooms") || text.equals("room")) {
      return "rooms";
    }
    throw new IOException("\"type\" is doors, windows, doors_and_windows, furniture, symbols or rooms");
  }

  /**
   * Returns the levels a command keeps, or <code>null</code> if it keeps them all.
   */
  private List<Level> getLevels(AssistantCommand command) throws IOException {
    String token = command.getString("level");
    if (token == null || token.trim().length() == 0) {
      return null;
    }
    List<Level> levels = HomeItemIds.findLevels(this.home, token.trim());
    if (levels.isEmpty()) {
      throw new IOException("no level has the id or the name \"" + token + "\"");
    }
    return levels;
  }

  private static String getLevelName(Level level) {
    return level != null ? level.getName() : "-";
  }

  private void schedule(String type, AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    List<Level> levels = getLevels(command);
    if ("doors".equals(type) || "windows".equals(type) || "doors_and_windows".equals(type)) {
      Map<HomePieceOfFurniture, String> marks = new LinkedHashMap<HomePieceOfFurniture, String>();
      Table table = createOpeningSchedule(!"windows".equals(type), !"doors".equals(type), levels, marks);
      StringBuilder text = new StringBuilder(table.toText());
      if (command.getBoolean("tag_marks", false) || command.getBoolean("labels", false)) {
        text.append('\n').append(tagMarks(marks, command.getBoolean("labels", false), tally));
      }
      notes.add(text.toString());
    } else {
      notes.add(createSchedule(type, levels).toText());
    }
  }

  private Table createSchedule(String type, List<Level> levels) {
    if ("rooms".equals(type)) {
      return createRoomSchedule(levels);
    } else if ("furniture".equals(type)) {
      return createFurnitureSchedule(levels);
    } else if ("symbols".equals(type)) {
      return createSymbolSchedule(levels);
    } else {
      return createOpeningSchedule(!"windows".equals(type), !"doors".equals(type), levels,
          new LinkedHashMap<HomePieceOfFurniture, String>());
    }
  }

  private static void collect(List<HomePieceOfFurniture> furniture, List<HomePieceOfFurniture> pieces) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collect(((HomeFurnitureGroup)piece).getFurniture(), pieces);
      } else {
        pieces.add(piece);
      }
    }
  }

  private List<HomePieceOfFurniture> getFurniture(List<Level> levels) {
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    collect(this.home.getFurniture(), pieces);
    if (levels != null) {
      for (int i = pieces.size() - 1; i >= 0; i--) {
        if (!levels.contains(pieces.get(i).getLevel())) {
          pieces.remove(i);
        }
      }
    }
    return pieces;
  }

  private String getId(Object item) {
    String id = this.executor.getId(item);
    return id != null ? id : "?";
  }

  /**
   * Returns <code>true</code> if a door or a window is a door: its name tells it, or else
   * it stands on the floor and is tall enough to walk through.
   */
  static boolean isDoor(HomePieceOfFurniture piece) {
    String name = piece.getName() != null ? piece.getName().toLowerCase(Locale.ENGLISH) : "";
    if (name.indexOf("window") >= 0 || name.indexOf("fen\u00eatre") >= 0 || name.indexOf("fenetre") >= 0
        || name.indexOf("skylight") >= 0 || name.indexOf("fenster") >= 0) {
      return false;
    } else if (name.indexOf("door") >= 0 || name.indexOf("porte") >= 0 || name.indexOf("gate") >= 0
        || name.indexOf("t\u00fcr") >= 0) {
      return true;
    }
    return piece.getElevation() < 30 && piece.getHeight() >= 150;
  }

  /**
   * The doors or the windows that share a mark.
   */
  private static final class OpeningGroup {
    boolean                    door;
    String                     name;
    float                      width;
    float                      height;
    Set<Float>                 sills = new TreeSet<Float>();
    Set<String>                levels = new LinkedHashSet<String>();
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
  }

  /**
   * Returns the schedule of the doors and windows, grouped by kind, name and size.
   * @param marks receives the mark of each door and window
   */
  private Table createOpeningSchedule(boolean doors, boolean windows, List<Level> levels,
                                      Map<HomePieceOfFurniture, String> marks) {
    Map<String, OpeningGroup> groups = new LinkedHashMap<String, OpeningGroup>();
    for (HomePieceOfFurniture piece : getFurniture(levels)) {
      if (!piece.isDoorOrWindow() || (isDoor(piece) ? !doors : !windows)) {
        continue;
      }
      String name = piece.getName() != null ? piece.getName() : "";
      String key = (isDoor(piece) ? "D" : "W") + '\n' + name + '\n' + Math.round(piece.getWidth() * 10)
          + '\n' + Math.round(piece.getHeight() * 10);
      OpeningGroup group = groups.get(key);
      if (group == null) {
        group = new OpeningGroup();
        group.door = isDoor(piece);
        group.name = name;
        group.width = Math.round(piece.getWidth() * 10) / 10f;
        group.height = Math.round(piece.getHeight() * 10) / 10f;
        groups.put(key, group);
      }
      group.sills.add(Math.round(piece.getElevation() * 10) / 10f);
      group.levels.add(getLevelName(piece.getLevel()));
      group.pieces.add(piece);
    }
    List<OpeningGroup> sortedGroups = new ArrayList<OpeningGroup>(groups.values());
    Collections.sort(sortedGroups, new Comparator<OpeningGroup>() {
        public int compare(OpeningGroup group1, OpeningGroup group2) {
          if (group1.door != group2.door) {
            return group1.door ? -1 : 1;
          }
          int order = group1.name.compareToIgnoreCase(group2.name);
          if (order == 0) {
            order = Float.compare(group1.width, group2.width);
          }
          if (order == 0) {
            order = Float.compare(group1.height, group2.height);
          }
          return order;
        }
      });
    String title = doors && windows ? "Door and window schedule" : doors ? "Door schedule" : "Window schedule";
    Table table = new Table(title + " (cm)", "Mark", "Type", "Name", "Width", "Height", "Sill height", "Count", "Levels", "Ids");
    int doorTypes = 0;
    int windowTypes = 0;
    int doorCount = 0;
    int windowCount = 0;
    for (OpeningGroup group : sortedGroups) {
      String mark = group.door ? "D" + ++doorTypes : "W" + ++windowTypes;
      List<String> sills = new ArrayList<String>();
      for (Float sill : group.sills) {
        sills.add(format(sill));
      }
      List<String> ids = new ArrayList<String>();
      for (HomePieceOfFurniture piece : group.pieces) {
        ids.add(getId(piece));
        marks.put(piece, mark);
      }
      if (group.door) {
        doorCount += group.pieces.size();
      } else {
        windowCount += group.pieces.size();
      }
      table.rows.add(new String [] {mark, group.door ? "door" : "window", group.name, format(group.width),
          format(group.height), join(sills, " / "), String.valueOf(group.pieces.size()),
          join(group.levels, ", "), join(ids, " ")});
    }
    if (!table.rows.isEmpty()) {
      StringBuilder total = new StringBuilder("Total: ");
      if (doors) {
        total.append(doorCount).append(doorCount == 1 ? " door of " : " doors of ").append(doorTypes)
            .append(doorTypes == 1 ? " type" : " types");
      }
      if (windows) {
        total.append(doors ? ", " : "").append(windowCount).append(windowCount == 1 ? " window of " : " windows of ")
            .append(windowTypes).append(windowTypes == 1 ? " type" : " types");
      }
      table.remarks.add(total.toString());
    }
    return table;
  }

  /**
   * Writes the mark of each door and window in its properties, and in the plan if <code>labels</code> is true.
   */
  private String tagMarks(final Map<HomePieceOfFurniture, String> marks, boolean labels, int [] tally) {
    if (marks.isEmpty()) {
      return "No mark to write";
    }
    List<HomeObject> items = new ArrayList<HomeObject>();
    List<String> names = new ArrayList<String>();
    List<String> values = new ArrayList<String>();
    for (Map.Entry<HomePieceOfFurniture, String> entry : marks.entrySet()) {
      if (!entry.getValue().equals(entry.getKey().getProperty(SCHEDULE_MARK_PROPERTY))) {
        items.add(entry.getKey());
        names.add(SCHEDULE_MARK_PROPERTY);
        values.add(entry.getValue());
      }
    }
    setProperties(this.homeController, this.executor, items, names, values);
    tally [4] += items.size();
    String text = "Wrote the mark of " + marks.size() + (marks.size() == 1 ? " piece" : " pieces")
        + " in its property " + SCHEDULE_MARK_PROPERTY;
    if (labels) {
      // The labels of a previous schedule are replaced
      List<Label> oldLabels = new ArrayList<Label>();
      Set<String> ids = new LinkedHashSet<String>();
      for (HomePieceOfFurniture piece : marks.keySet()) {
        ids.add(getId(piece));
      }
      for (Label label : this.home.getLabels()) {
        if (ids.contains(label.getProperty(MARK_LABEL_PROPERTY))) {
          oldLabels.add(label);
        }
      }
      this.executor.beginTurnUpdate();
      if (!oldLabels.isEmpty()) {
        this.homeController.getPlanController().deleteItems(oldLabels);
      }
      List<Label> newLabels = new ArrayList<Label>();
      List<Level> labelLevels = new ArrayList<Level>();
      for (Map.Entry<HomePieceOfFurniture, String> entry : marks.entrySet()) {
        HomePieceOfFurniture piece = entry.getKey();
        // In front of the piece, clear of the wall
        float distance = piece.getDepth() / 2 + 25;
        Label label = new Label(entry.getValue(), piece.getX() - (float)Math.sin(piece.getAngle()) * distance,
            piece.getY() + (float)Math.cos(piece.getAngle()) * distance);
        label.setStyle(new TextStyle(14, true, false));
        label.setProperty(MARK_LABEL_PROPERTY, getId(piece));
        newLabels.add(label);
        labelLevels.add(piece.getLevel());
      }
      this.homeController.getPlanController().addLabels(newLabels);
      for (int i = 0; i < newLabels.size(); i++) {
        if (newLabels.get(i).getLevel() != labelLevels.get(i)) {
          newLabels.get(i).setLevel(labelLevels.get(i));
        }
      }
      text += ", and wrote " + newLabels.size() + (newLabels.size() == 1 ? " label" : " labels") + " in the plan"
          + (oldLabels.isEmpty() ? "" : " in place of " + oldLabels.size() + " older ones");
    }
    return text;
  }

  /**
   * Sets properties of items and posts the edit that undoes it. A <code>null</code> value removes a property.
   */
  private static void setProperties(HomeController homeController, AssistantCommandExecutor executor,
                                    final List<HomeObject> items, final List<String> names, final List<String> values) {
    if (items.isEmpty()) {
      return;
    }
    final List<String> oldValues = new ArrayList<String>();
    for (int i = 0; i < items.size(); i++) {
      oldValues.add(items.get(i).getProperty(names.get(i)));
      items.get(i).setProperty(names.get(i), values.get(i));
    }
    executor.beginTurnUpdate();
    homeController.getUndoableEditSupport().postEdit(new AbstractUndoableEdit() {
        @Override
        public void undo() throws CannotUndoException {
          super.undo();
          for (int i = items.size() - 1; i >= 0; i--) {
            items.get(i).setProperty(names.get(i), oldValues.get(i));
          }
        }

        @Override
        public void redo() throws CannotRedoException {
          super.redo();
          for (int i = 0; i < items.size(); i++) {
            items.get(i).setProperty(names.get(i), values.get(i));
          }
        }

        @Override
        public String getPresentationName() {
          return "AI assistant edit";
        }
      });
  }

  private static double getPerimeter(Room room) {
    float [][] points = room.getPoints();
    double perimeter = 0;
    for (int i = 0; i < points.length; i++) {
      float [] next = points [(i + 1) % points.length];
      perimeter += Math.hypot(next [0] - points [i][0], next [1] - points [i][1]);
    }
    return perimeter;
  }

  private Table createRoomSchedule(List<Level> levels) {
    Table table = new Table("Room schedule", "Id", "Name", "Level", "Area (m2)", "Perimeter (m)", "Ceiling height (cm)",
        "Floor finish", "Net wall surface (m2)");
    HomeQuantities quantities = new HomeQuantities(this.home);
    List<HomeRoofs.Roof> roofs = HomeRoofs.get(this.home);
    double totalArea = 0;
    for (Room room : this.home.getRooms()) {
      if (levels != null && !levels.contains(room.getLevel())) {
        continue;
      }
      String ceiling = "";
      try {
        double [] headroom = HomeRoofs.getHeadroom(this.home, roofs, room);
        if (headroom != null && !Double.isNaN(headroom [0])) {
          ceiling = Math.abs(headroom [1] - headroom [0]) < 0.5 ? format(headroom [0], 0)
              : format(headroom [0], 0) + " to " + format(headroom [1], 0);
        }
      } catch (RuntimeException ex) {
        // No ceiling height for this room
      }
      String finish = room.getFloorTexture() != null && room.getFloorTexture().getName() != null
          ? room.getFloorTexture().getName()
          : room.getFloorColor() != null ? String.format("#%06X", room.getFloorColor() & 0xFFFFFF) : "default";
      if (!room.isFloorVisible()) {
        finish = "no floor";
      }
      double [] walls = quantities.getWallSurface(room);
      double area = room.getArea() / 10000.;
      totalArea += area;
      table.rows.add(new String [] {getId(room), room.getName() != null ? room.getName() : "", getLevelName(room.getLevel()),
          format(area, 2), format(getPerimeter(room) / 100, 2), ceiling, finish,
          walls != null ? format(walls [2] / 10000, 2) : ""});
    }
    if (!table.rows.isEmpty()) {
      table.remarks.add("Total: " + table.rows.size() + (table.rows.size() == 1 ? " room, " : " rooms, ")
          + format(totalArea, 2) + " m2. The wall surface is the one of the wall sides that face the room, "
          + "doors and windows deducted; it is empty when no wall follows the room");
    }
    return table;
  }

  private static boolean isSymbol(HomePieceOfFurniture piece) {
    return piece.getProperty(SYMBOL_TYPE_PROPERTY) != null;
  }

  private Table createFurnitureSchedule(List<Level> levels) {
    Table table = new Table("Furniture schedule (cm)", "Name", "Width", "Depth", "Height", "Count", "Levels", "Price", "Ids");
    Map<String, List<HomePieceOfFurniture>> groups = new TreeMap<String, List<HomePieceOfFurniture>>(String.CASE_INSENSITIVE_ORDER);
    int count = 0;
    for (HomePieceOfFurniture piece : getFurniture(levels)) {
      if (piece.isDoorOrWindow() || isSymbol(piece)
          || piece.getProperty(AssistantCommandExecutor.STRUCTURE_PROPERTY) != null
          || "true".equals(piece.getProperty(AssistantCommandExecutor.AUTO_ROOF_PROPERTY))) {
        continue;
      }
      String key = (piece.getName() != null ? piece.getName() : "") + '\n' + Math.round(piece.getWidth())
          + '\n' + Math.round(piece.getDepth()) + '\n' + Math.round(piece.getHeight());
      List<HomePieceOfFurniture> group = groups.get(key);
      if (group == null) {
        groups.put(key, group = new ArrayList<HomePieceOfFurniture>());
      }
      group.add(piece);
      count++;
    }
    for (List<HomePieceOfFurniture> group : groups.values()) {
      HomePieceOfFurniture first = group.get(0);
      Set<String> levelNames = new LinkedHashSet<String>();
      List<String> ids = new ArrayList<String>();
      BigDecimal price = null;
      String currency = null;
      for (HomePieceOfFurniture piece : group) {
        levelNames.add(getLevelName(piece.getLevel()));
        ids.add(getId(piece));
        if (piece.getPrice() != null) {
          price = price != null ? price.add(piece.getPrice()) : piece.getPrice();
          currency = piece.getCurrency() != null ? piece.getCurrency() : currency;
        }
      }
      table.rows.add(new String [] {first.getName() != null ? first.getName() : "", format(Math.round(first.getWidth())),
          format(Math.round(first.getDepth())), format(Math.round(first.getHeight())), String.valueOf(group.size()),
          join(levelNames, ", "), price != null ? price.toPlainString() + (currency != null ? " " + currency : "") : "",
          join(ids, " ")});
    }
    if (count > 0) {
      table.remarks.add("Total: " + count + (count == 1 ? " piece of " : " pieces of ") + table.rows.size()
          + (table.rows.size() == 1 ? " kind" : " kinds")
          + ". Doors, windows, symbols and the parts of the building aren't listed here");
    }
    return table;
  }

  /**
   * Returns the room of the level of a piece that contains its center, or <code>null</code>.
   */
  private Room getRoom(HomePieceOfFurniture piece) {
    for (Room room : this.home.getRooms()) {
      if (room.getLevel() == piece.getLevel() && room.containsPoint(piece.getX(), piece.getY(), 0)) {
        return room;
      }
    }
    return null;
  }

  private String describeRoom(Room room) {
    return room == null ? "no room"
        : room.getName() != null && room.getName().length() > 0 ? room.getName() : getId(room);
  }

  private static void count(Map<String, Map<String, int []>> counts, String key, String type) {
    Map<String, int []> typeCounts = counts.get(key);
    if (typeCounts == null) {
      counts.put(key, typeCounts = new LinkedHashMap<String, int []>());
    }
    int [] count = typeCounts.get(type);
    if (count == null) {
      typeCounts.put(type, count = new int [1]);
    }
    count [0]++;
  }

  private static String describeCounts(Map<String, Map<String, int []>> counts) {
    StringBuilder text = new StringBuilder();
    for (Map.Entry<String, Map<String, int []>> entry : counts.entrySet()) {
      int total = 0;
      StringBuilder types = new StringBuilder();
      for (Map.Entry<String, int []> typeEntry : entry.getValue().entrySet()) {
        total += typeEntry.getValue() [0];
        types.append(types.length() > 0 ? ", " : "").append(typeEntry.getValue() [0]).append(' ').append(typeEntry.getKey());
      }
      text.append(text.length() > 0 ? "; " : "").append(entry.getKey()).append(": ").append(total)
          .append(" (").append(types).append(')');
    }
    return text.toString();
  }

  private Table createSymbolSchedule(List<Level> levels) {
    Table table = new Table("Symbol schedule", "Type", "Category", "Count", "Rooms", "Circuits", "Levels", "Ids");
    Map<String, List<HomePieceOfFurniture>> groups = new LinkedHashMap<String, List<HomePieceOfFurniture>>();
    for (AssistantSymbols.Type type : AssistantSymbols.getTypes()) {
      groups.put(type.name, new ArrayList<HomePieceOfFurniture>());
    }
    Map<String, Map<String, int []>> roomCounts = new LinkedHashMap<String, Map<String, int []>>();
    Map<String, Map<String, int []>> circuitCounts = new TreeMap<String, Map<String, int []>>();
    int total = 0;
    for (HomePieceOfFurniture piece : getFurniture(levels)) {
      if (!isSymbol(piece)) {
        continue;
      }
      String type = piece.getProperty(SYMBOL_TYPE_PROPERTY);
      List<HomePieceOfFurniture> group = groups.get(type);
      if (group == null) {
        groups.put(type, group = new ArrayList<HomePieceOfFurniture>());
      }
      group.add(piece);
      total++;
      count(roomCounts, describeRoom(getRoom(piece))
          + (piece.getLevel() != null ? " (" + piece.getLevel().getName() + ")" : ""), type);
      String circuit = piece.getProperty(SYMBOL_CIRCUIT_PROPERTY);
      count(circuitCounts, circuit != null ? circuit : "no circuit", type);
    }
    for (Map.Entry<String, List<HomePieceOfFurniture>> entry : groups.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      AssistantSymbols.Type type = AssistantSymbols.getType(entry.getKey());
      Set<String> rooms = new LinkedHashSet<String>();
      Set<String> circuits = new TreeSet<String>();
      Set<String> levelNames = new LinkedHashSet<String>();
      List<String> ids = new ArrayList<String>();
      for (HomePieceOfFurniture piece : entry.getValue()) {
        rooms.add(describeRoom(getRoom(piece)));
        if (piece.getProperty(SYMBOL_CIRCUIT_PROPERTY) != null) {
          circuits.add(piece.getProperty(SYMBOL_CIRCUIT_PROPERTY));
        }
        levelNames.add(getLevelName(piece.getLevel()));
        ids.add(getId(piece));
      }
      table.rows.add(new String [] {entry.getKey(), type != null ? type.category : "", String.valueOf(entry.getValue().size()),
          join(rooms, ", "), join(circuits, ", "), join(levelNames, ", "), join(ids, " ")});
    }
    if (total > 0) {
      table.remarks.add("By room: " + describeCounts(roomCounts));
      table.remarks.add("By circuit: " + describeCounts(circuitCounts));
      table.remarks.add("Total: " + total + (total == 1 ? " symbol" : " symbols"));
    }
    return table;
  }

  /**
   * Writes a schedule in a CSV file.
   */
  private void exportSchedule(AssistantCommand command, List<String> notes) throws IOException {
    String type = getScheduleType(command);
    String path = command.getString("path") != null ? command.getString("path") : command.getString("file");
    if (path == null || path.trim().length() == 0) {
      throw new IOException("give the absolute \"path\" of the .csv file to write");
    }
    path = path.trim();
    if (path.startsWith("~/")) {
      path = System.getProperty("user.home") + path.substring(1);
    }
    File file = new File(path);
    if (!file.isAbsolute()) {
      throw new IOException("\"path\" must be an absolute path, not \"" + path + "\"");
    } else if (!path.toLowerCase(Locale.ENGLISH).endsWith(".csv")) {
      throw new IOException("\"path\" must be the path of a .csv file");
    } else if (file.getParentFile() == null || !file.getParentFile().isDirectory()) {
      throw new IOException("the folder of \"path\" doesn't exist");
    } else if (file.isDirectory()) {
      throw new IOException(file + " is a folder");
    } else if (file.exists() && !command.getBoolean("replace", false)) {
      throw new IOException(file + " exists; give \"replace\":true to overwrite it");
    }
    Table table = createSchedule(type, getLevels(command));
    Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
    try {
      writer.write(table.toCsv());
    } finally {
      writer.close();
    }
    notes.add("Wrote the " + table.title.toLowerCase(Locale.ENGLISH) + " in " + file + ": " + table.rows.size()
        + (table.rows.size() == 1 ? " row" : " rows") + " under the titles " + join(table.headers, ", ")
        + " (CSV in UTF-8, values separated by commas)");
  }

  // ---- Unit prices of rooms and walls ----

  /**
   * Sets or removes the unit prices of the budget that rooms and walls have of their own, if the
   * command <code>set_unit_prices</code> designates some.
   * @return <code>false</code> if the command is about the prices of the whole home
   */
  static boolean setItemUnitPrices(Home home, HomeController homeController, AssistantCommandExecutor executor,
                                   AssistantCommand command, int [] tally, List<String> notes) {
    boolean clear = command.getBoolean("clear", false);
    boolean designated = command.has("room") || command.has("rooms") || command.has("wall") || command.has("walls")
        || command.has("id") || command.has("ids") || command.has("target") || command.has("where") || command.has("names");
    if (!designated && !clear) {
      return false;
    }
    try {
      List<HomeObject> targets = new ArrayList<HomeObject>();
      List<String> unknown = new ArrayList<String>();
      for (Selectable item : executor.resolveTargets(command)) {
        if ((item instanceof Room || item instanceof Wall) && !targets.contains(item)) {
          targets.add((HomeObject)item);
        }
      }
      List<String> tokens = new ArrayList<String>();
      tokens.addAll(command.getStringList("room"));
      tokens.addAll(command.getStringList("rooms"));
      tokens.addAll(command.getStringList("wall"));
      tokens.addAll(command.getStringList("walls"));
      for (String token : tokens) {
        HomeObject item = HomeItemIds.find(home, token.trim());
        boolean found = false;
        if (item instanceof Room || item instanceof Wall) {
          if (!targets.contains(item)) {
            targets.add(item);
          }
          found = true;
        } else {
          for (Room room : home.getRooms()) {
            if (token.trim().equalsIgnoreCase(room.getName())) {
              if (!targets.contains(room)) {
                targets.add(room);
              }
              found = true;
            }
          }
        }
        if (!found) {
          unknown.add(token);
        }
      }
      if (designated && targets.isEmpty()) {
        throw new IOException("no room and no wall is designated"
            + (unknown.isEmpty() ? "" : " by " + unknown)
            + "; give \"room\":\"R#\" or \"ids\":[\"R#\",\"W#\"], or no id for the prices of the whole home");
      }
      if (!designated) {
        // Every price of a room or a wall
        targets.addAll(home.getRooms());
        targets.addAll(home.getWalls());
      }
      String side = command.getString("side");
      if (side != null) {
        side = side.trim().toLowerCase(Locale.ENGLISH);
        if (!side.equals("left") && !side.equals("right")) {
          throw new IOException("\"side\" is left or right: the side of a wall seen from its start to its end");
        }
      }
      List<HomeObject> items = new ArrayList<HomeObject>();
      List<String> names = new ArrayList<String>();
      List<String> values = new ArrayList<String>();
      List<String> changes = new ArrayList<String>();
      List<String> ignored = new ArrayList<String>();
      for (HomeObject target : targets) {
        String id = executor.getId(target);
        if (clear) {
          String [] keys = target instanceof Wall ? HomeReport.WALL_PRICE_KEYS : HomeReport.UNIT_PRICE_KEYS;
          boolean cleared = false;
          for (String key : keys) {
            if (target.getProperty(HomeReport.getItemUnitPriceProperty(key)) != null) {
              items.add(target);
              names.add(HomeReport.getItemUnitPriceProperty(key));
              values.add(null);
              cleared = true;
            }
          }
          if (cleared) {
            changes.add(id + ": back to the prices of the home");
          }
          continue;
        }
        for (String key : HomeReport.UNIT_PRICE_KEYS) {
          if (!command.has(key)) {
            continue;
          }
          if (target instanceof Wall && !"paint".equals(key)) {
            if (!ignored.contains(key)) {
              ignored.add(key);
            }
            continue;
          }
          double price = command.getDouble(key, -1);
          String value = price >= 0 && price < 1E9 ? new BigDecimal(String.valueOf(price)).toPlainString() : null;
          String property = HomeReport.getItemUnitPriceProperty(target instanceof Wall && side != null ? key + "." + side : key);
          String oldValue = target.getProperty(property);
          if (value == null ? oldValue != null : !value.equals(oldValue)) {
            items.add(target);
            names.add(property);
            values.add(value);
          }
          changes.add(id + (target instanceof Wall && side != null ? " " + side + " side" : "") + ": " + key + " "
              + (value != null ? value : "back to the price of the home"));
        }
      }
      if (changes.isEmpty()) {
        notes.add(clear ? "No room and no wall had a price of its own"
            : "Ignored set_unit_prices: give \"paint\", \"flooring\", \"ceiling\" (per m2) or \"baseboard\" (per m) for a room, "
                + "\"paint\" for a wall, or \"clear\":true");
        return true;
      }
      setProperties(homeController, executor, items, names, values);
      tally [4] += new LinkedHashSet<HomeObject>(items).size();
      notes.add("Unit prices of their own: " + join(changes, "; ")
          + (ignored.isEmpty() ? "" : ". A wall only has a price of paint: " + join(ignored, ", ") + " ignored for walls")
          + ". home_report uses them in place of the prices of the home");
    } catch (IOException ex) {
      notes.add("Couldn't run set_unit_prices (" + ex.getMessage() + ")");
    } catch (RuntimeException ex) {
      notes.add("Couldn't run set_unit_prices (" + ex + ")");
    }
    return true;
  }

  // ---- Symbols ----

  private void listSymbols(List<String> notes) {
    StringBuilder text = new StringBuilder("Symbols of add_symbol (type: what it is, where it is fixed, default height in cm):");
    for (AssistantSymbols.Type type : AssistantSymbols.getTypes()) {
      text.append("\n- ").append(type.name).append(": ").append(type.label).append(", ").append(type.category).append(", ");
      if (type.mount == AssistantSymbols.WALL) {
        text.append("on a wall, center at ").append(format(type.height));
      } else if (type.mount == AssistantSymbols.CEILING) {
        text.append("under the ceiling");
      } else {
        text.append("on the floor");
      }
    }
    notes.add(text.toString());
  }

  private AssistantSymbols.Type getSymbolType(AssistantCommand command, String defaultType) throws IOException {
    String name = command.getString("type") != null ? command.getString("type") : defaultType;
    AssistantSymbols.Type type = AssistantSymbols.getType(name);
    if (type == null) {
      throw new IOException((name == null ? "\"type\" is required" : "unknown type \"" + name + "\"") + "; types: "
          + AssistantSymbols.describeTypes());
    }
    return type;
  }

  /**
   * Returns the model and the plan icon of a type of symbol, built once.
   */
  private Object [] getSymbolModel(AssistantSymbols.Type type) throws IOException {
    Object [] model = this.symbolModels.get(type.name);
    if (model == null) {
      model = new Object [] {
          AssistantFurnitureBuilder.build(AssistantSymbols.getParts(type), new ArrayList<String>()),
          AssistantSymbols.createPlanIcon(type)};
      this.symbolModels.put(type.name, model);
    }
    return model;
  }

  private double getCeilingHeight(Level level, Wall wall) {
    if (wall != null) {
      return HomeQuantities.getHeight(this.home, wall);
    }
    return level != null && level.getHeight() > 0 ? level.getHeight() : this.home.getWallHeight();
  }

  /**
   * Creates the piece of a symbol.
   * @param height the height of the center of a wall symbol, of the top of a ceiling one or of the
   *               bottom of a floor one, or NaN for its default height
   * @param normal the direction the symbol faces, or <code>null</code>
   */
  private HomePieceOfFurniture createSymbol(AssistantSymbols.Type type, Level level, double x, double y, double angle,
                                            double height, Wall wall, double [] normal, String label, String circuit)
      throws IOException {
    Object [] symbolModel = getSymbolModel(type);
    AssistantGeneratedModel model = (AssistantGeneratedModel)symbolModel [0];
    HomePieceOfFurniture piece = this.executor.getShapeCommands().createPiece(
        label != null ? label : type.label, model, level, true, "symbol");
    piece.setPlanIcon((Content)symbolModel [1]);
    piece.setX((float)x);
    piece.setY((float)y);
    piece.setAngle((float)angle);
    double elevation;
    if (type.mount == AssistantSymbols.WALL) {
      elevation = (Double.isNaN(height) ? type.height : height) - model.getHeight() / 2;
    } else if (type.mount == AssistantSymbols.CEILING) {
      elevation = (Double.isNaN(height) ? getCeilingHeight(level, wall) : height) - model.getHeight();
    } else {
      elevation = Double.isNaN(height) ? type.height : height;
    }
    piece.setElevation((float)Math.max(0, elevation));
    piece.setProperty(SYMBOL_TYPE_PROPERTY, type.name);
    if (circuit != null && circuit.trim().length() > 0) {
      piece.setProperty(SYMBOL_CIRCUIT_PROPERTY, circuit.trim());
    }
    piece.setProperty(HomeItemTags.TAGS_PROPERTY, type.category);
    piece.setDescription(type.label + (circuit != null && circuit.trim().length() > 0 ? ", circuit " + circuit.trim() : ""));
    if (label != null) {
      // Written in the plan beside the symbol
      float distance = Math.max(type.width, type.depth) / 2 + 12;
      piece.setNameVisible(true);
      piece.setNameStyle(new TextStyle(12));
      piece.setNameXOffset(normal != null ? (float)(normal [0] * distance) : 0);
      piece.setNameYOffset(normal != null ? (float)(normal [1] * distance) : distance);
    }
    return piece;
  }

  private Wall getWall(String token) throws IOException {
    HomeObject item = HomeItemIds.find(this.home, token.trim());
    if (!(item instanceof Wall)) {
      throw new IOException("no wall has the id \"" + token + "\"");
    }
    if (!HomeQuantities.isStraight((Wall)item) || ((Wall)item).getLength() < 1) {
      throw new IOException("a symbol is placed on a straight wall; on the round wall " + token
          + " give \"x\", \"y\" and \"angle\" in place of \"wall\"");
    }
    return (Wall)item;
  }

  /**
   * Returns {x, y, angle, normal x, normal y} of a symbol standing on a side of a wall.
   */
  private static double [] getPlaceOnWall(Wall wall, double offset, boolean left, AssistantSymbols.Type type) {
    double length = wall.getLength();
    double dx = (wall.getXEnd() - wall.getXStart()) / length;
    double dy = (wall.getYEnd() - wall.getYStart()) / length;
    double normalX = left ? dy : -dy;
    double normalY = left ? -dx : dx;
    double distance = wall.getThickness() / 2 + type.depth / 2;
    return new double [] {wall.getXStart() + dx * offset + normalX * distance,
                          wall.getYStart() + dy * offset + normalY * distance,
                          Math.atan2(-normalX, normalY), normalX, normalY};
  }

  private void addSymbol(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    AssistantSymbols.Type type = getSymbolType(command, null);
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    double height = command.getDouble("height", command.getDouble("elevation", Double.NaN));
    if (!Double.isNaN(height) && (height < 0 || !AssistantCommandExecutor.sizeSane(height))) {
      throw new IOException("\"height\" is out of range");
    }
    String label = command.getString("label") != null && command.getString("label").trim().length() > 0
        ? command.getString("label").trim() : null;
    HomePieceOfFurniture piece;
    String place;
    if (command.getString("wall") != null) {
      Wall wall = getWall(command.getString("wall"));
      double length = wall.getLength();
      double dx = (wall.getXEnd() - wall.getXStart()) / length;
      double dy = (wall.getYEnd() - wall.getYStart()) / length;
      boolean pointGiven = !Double.isNaN(x) && !Double.isNaN(y);
      double offset = command.getDouble("offset", pointGiven
          ? Math.max(0, Math.min(length, (x - wall.getXStart()) * dx + (y - wall.getYStart()) * dy)) : length / 2);
      if (offset < 0 || offset > length) {
        throw new IOException("\"offset\" goes from 0 to " + format(length) + " cm, the length of the wall "
            + command.getString("wall").trim());
      }
      String side = command.getString("side");
      boolean left;
      String sideReason = "";
      if (side != null) {
        side = side.trim().toLowerCase(Locale.ENGLISH);
        if (!side.equals("left") && !side.equals("right")) {
          throw new IOException("\"side\" is left or right: the side of the wall seen from its start to its end");
        }
        left = side.equals("left");
      } else {
        // Left of the wall: dy, -dx
        double pointSide = pointGiven ? (x - wall.getXStart()) * dy - (y - wall.getYStart()) * dx : 0;
        if (Math.abs(pointSide) > 0.5) {
          left = pointSide > 0;
          sideReason = ", the side of the point given";
        } else {
          double [] leftPlace = getPlaceOnWall(wall, offset, true, type);
          double [] rightPlace = getPlaceOnWall(wall, offset, false, type);
          boolean roomAtLeft = false;
          boolean roomAtRight = false;
          for (Room room : this.home.getRooms()) {
            if (room.getLevel() == wall.getLevel()) {
              roomAtLeft |= room.containsPoint((float)leftPlace [0], (float)leftPlace [1], 0);
              roomAtRight |= room.containsPoint((float)rightPlace [0], (float)rightPlace [1], 0);
            }
          }
          left = roomAtLeft || !roomAtRight;
          sideReason = roomAtLeft != roomAtRight ? ", the side of a room"
              : "; give \"side\":\"right\" for the other side";
        }
      }
      double [] wallPlace = getPlaceOnWall(wall, offset, left, type);
      piece = createSymbol(type, wall.getLevel(), wallPlace [0], wallPlace [1], wallPlace [2], height, wall,
          new double [] {wallPlace [3], wallPlace [4]}, label, command.getString("circuit"));
      place = "on the " + (left ? "left" : "right") + " side of the wall " + getId(wall) + sideReason + ", "
          + format(offset) + " cm from its start";
    } else {
      if (!AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)
          || Double.isNaN(x) || Double.isNaN(y)) {
        throw new IOException("give \"wall\":\"W#\" and \"offset\", or \"x\" and \"y\"");
      }
      Level level = this.executor.getShapeCommands().getLevel(command, "add_symbol", notes);
      piece = createSymbol(type, level, x, y, Math.toRadians(command.getDouble("angle", 0)), height, null, null,
          label, command.getString("circuit"));
      place = "at (" + format(x) + ", " + format(y) + ")";
    }
    this.executor.getShapeCommands().add(piece);
    HomeItemTags.showTagsColumn(this.home);
    tally [0]++;
    notes.add("Added the " + type.category + " symbol " + getId(piece) + " \"" + piece.getName() + "\" (" + type.name + ") "
        + place + ", " + describeHeight(type, piece)
        + (piece.getProperty(SYMBOL_CIRCUIT_PROPERTY) != null
            ? ", circuit \"" + piece.getProperty(SYMBOL_CIRCUIT_PROPERTY) + "\"" : ""));
  }

  private static String describeHeight(AssistantSymbols.Type type, HomePieceOfFurniture piece) {
    if (type.mount == AssistantSymbols.WALL) {
      return "its center " + format(piece.getElevation() + piece.getHeight() / 2) + " cm above the floor";
    } else if (type.mount == AssistantSymbols.CEILING) {
      return "its top " + format(piece.getElevation() + piece.getHeight()) + " cm above the floor";
    } else {
      return piece.getElevation() == 0 ? "on the floor" : format(piece.getElevation()) + " cm above the floor";
    }
  }

  /**
   * Returns the room a command designates with <code>room</code> or its ids.
   */
  private Room getRoom(AssistantCommand command) throws IOException {
    String token = command.getString("room");
    if (token != null) {
      HomeObject item = HomeItemIds.find(this.home, token.trim());
      if (item instanceof Room) {
        return (Room)item;
      }
      for (Room room : this.home.getRooms()) {
        if (token.trim().equalsIgnoreCase(room.getName())) {
          return room;
        }
      }
      throw new IOException("no room has the id or the name \"" + token + "\"");
    }
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof Room) {
        return (Room)item;
      }
    }
    throw new IOException("give the room with \"room\":\"R#\"");
  }

  /**
   * Adds symbols along the walls of a room at a regular spacing.
   */
  private void addSymbols(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Room room = getRoom(command);
    AssistantSymbols.Type type = getSymbolType(command, "outlet");
    double spacing = command.getDouble("spacing", 360);
    if (!(spacing >= 30) || !AssistantCommandExecutor.sizeSane(spacing)) {
      throw new IOException("\"spacing\" is 30 cm at least");
    }
    double height = command.getDouble("height", Double.NaN);
    if (!Double.isNaN(height) && (height < 0 || !AssistantCommandExecutor.sizeSane(height))) {
      throw new IOException("\"height\" is out of range");
    }
    double mountHeight = !Double.isNaN(height) ? height : type.mount == AssistantSymbols.WALL ? type.height : 0;
    List<HomeQuantities.Facing> facings = new HomeQuantities(this.home).getFacings(room);
    if (facings.isEmpty()) {
      throw new IOException("no straight wall follows the sides of the room " + getId(room)
          + "; place the symbols one by one with add_symbol");
    }
    List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
    int moved = 0;
    int skipped = 0;
    for (HomeQuantities.Facing facing : facings) {
      double length = facing.getLength();
      if (length < 60) {
        // Too short a wall
        continue;
      }
      // What stands in the way at the height of the symbols: doors, and windows that go down to them
      List<double []> openings = new ArrayList<double []>();
      for (HomeQuantities.Opening opening : facing.wallQuantity.openings) {
        if (!Double.isNaN(opening.start)
            && (type.mount != AssistantSymbols.WALL
                ? opening.bottom < 5
                : opening.bottom < mountHeight + 10 && opening.top > mountHeight - 10)) {
          openings.add(new double [] {opening.start - 15, opening.end + 15});
        }
      }
      int count = (int)Math.ceil(length / spacing - 1E-6);
      List<Double> positions = new ArrayList<Double>();
      for (int i = 0; i < count; i++) {
        double position = facing.start + (i + 0.5) * length / count;
        double [] opening = getOpeningAt(openings, position);
        if (opening != null) {
          // Beside the opening, at the closest place that is free
          double before = opening [0] - 5;
          double after = opening [1] + 5;
          boolean beforeFree = isFree(before, facing, openings, positions);
          boolean afterFree = isFree(after, facing, openings, positions);
          if (beforeFree && (!afterFree || position - before <= after - position)) {
            position = before;
          } else if (afterFree) {
            position = after;
          } else {
            skipped++;
            continue;
          }
          moved++;
        }
        positions.add(position);
      }
      for (Double position : positions) {
        if (pieces.size() >= MAX_SYMBOLS_PER_COMMAND) {
          break;
        }
        double [] place = getPlaceOnWall(facing.wallQuantity.wall, position, facing.left, type);
        pieces.add(createSymbol(type, room.getLevel(), place [0], place [1], place [2], height, facing.wallQuantity.wall,
            new double [] {place [3], place [4]}, null, command.getString("circuit")));
      }
    }
    if (pieces.isEmpty()) {
      throw new IOException("no place was found along the walls of the room " + getId(room));
    }
    List<String> ids = new ArrayList<String>();
    for (HomePieceOfFurniture piece : pieces) {
      this.executor.getShapeCommands().add(piece);
      ids.add(getId(piece));
    }
    HomeItemTags.showTagsColumn(this.home);
    tally [0] += pieces.size();
    notes.add("Added " + pieces.size() + " " + type.category + (pieces.size() == 1 ? " symbol " : " symbols ") + type.name
        + " along the walls of the room " + getId(room)
        + (room.getName() != null && room.getName().length() > 0 ? " \"" + room.getName() + "\"" : "")
        + ", one every " + format(spacing) + " cm at most on each wall, " + describeHeight(type, pieces.get(0)) + ": "
        + join(ids, " ")
        + (moved > 0 ? "; " + moved + " moved beside a door or a window" : "")
        + (skipped > 0 ? "; " + skipped + " left out for lack of room beside an opening" : "")
        + (pieces.size() >= MAX_SYMBOLS_PER_COMMAND ? "; a command adds " + MAX_SYMBOLS_PER_COMMAND + " symbols at most" : ""));
  }

  private static double [] getOpeningAt(List<double []> openings, double position) {
    for (double [] opening : openings) {
      if (position > opening [0] && position < opening [1]) {
        return opening;
      }
    }
    return null;
  }

  private static boolean isFree(double position, HomeQuantities.Facing facing, List<double []> openings, List<Double> positions) {
    if (position < facing.start + 10 || position > facing.end - 10 || getOpeningAt(openings, position) != null) {
      return false;
    }
    for (Double other : positions) {
      if (Math.abs(other - position) < 30) {
        return false;
      }
    }
    return true;
  }

  // ---- Framing ----

  /**
   * Returns {thickness, width} in inches of a piece of lumber named like 2x4.
   */
  private static double [] parseLumber(String text, String parameter) throws IOException {
    String [] sizes = text.trim().toLowerCase(Locale.ENGLISH).split("\\s*x\\s*");
    try {
      if (sizes.length == 2) {
        double thickness = Double.parseDouble(sizes [0]);
        double width = Double.parseDouble(sizes [1]);
        if (thickness >= 1 && thickness <= 8 && width >= 2 && width <= 16) {
          return new double [] {thickness, width};
        }
      }
    } catch (NumberFormatException ex) {
      // Refused below
    }
    throw new IOException("\"" + parameter + "\" is a size of lumber like 2x4 or 2x6");
  }

  private static String describeLength(double centimeters) {
    return format(centimeters / 100, 1) + " m (" + format(centimeters / CM_PER_FOOT, 0) + " ft)";
  }

  /**
   * Estimates the lumber of walls framed in wood.
   */
  private void estimateFraming(AssistantCommand command, List<String> notes) throws IOException {
    List<Level> levels = getLevels(command);
    List<Wall> walls = new ArrayList<Wall>();
    boolean designated = command.has("id") || command.has("ids") || command.has("target") || command.has("where");
    if (designated) {
      for (Selectable item : this.executor.resolveTargets(command)) {
        if (item instanceof Wall) {
          walls.add((Wall)item);
        }
      }
      if (walls.isEmpty()) {
        throw new IOException("no wall is designated; give \"ids\":[\"W#\",...], or no id for every wall");
      }
    } else {
      walls.addAll(this.home.getWalls());
    }
    double spacing = command.getDouble("stud_spacing", command.getDouble("spacing", 40.64));
    if (!(spacing >= 10 && spacing <= 200)) {
      throw new IOException("\"stud_spacing\" goes from 10 to 200 cm (40.64 is 16 in, 60.96 is 24 in)");
    }
    double [] stud = parseLumber(command.getString("stud") != null ? command.getString("stud") : "2x4", "stud");
    double [] header = parseLumber(command.getString("header") != null ? command.getString("header") : "2x8", "header");
    double plates = command.getDouble("plates", 3);
    if (!(plates >= 1 && plates <= 6)) {
      throw new IOException("\"plates\" goes from 1 to 6 (3 is a bottom plate and a double top plate)");
    }
    double waste = command.getDouble("waste", 0.1);
    if (waste > 1) {
      // Given in percent
      waste /= 100;
    }
    if (!(waste >= 0 && waste <= 1)) {
      throw new IOException("\"waste\" goes from 0 to 1 (0.1 adds 10 %)");
    }
    HomeQuantities quantities = new HomeQuantities(this.home);
    StringBuilder lines = new StringBuilder();
    int wallCount = 0;
    int roundWalls = 0;
    int studCount = 0;
    int openingCount = 0;
    double wallLength = 0;
    double studLength = 0;
    double headerLength = 0;
    double surface = 0;
    Set<String> corners = new LinkedHashSet<String>();
    for (Wall wall : walls) {
      if (levels != null && !levels.contains(wall.getLevel())) {
        continue;
      }
      HomeQuantities.WallQuantity quantity = quantities.get(wall);
      if (quantity == null || quantity.length < 1) {
        continue;
      }
      if (!HomeQuantities.isStraight(wall)) {
        roundWalls++;
        continue;
      }
      int wallStuds = (int)Math.ceil(quantity.length / spacing - 1E-6) + 1 + 2 * quantity.openings.size();
      double wallHeaders = 0;
      for (HomeQuantities.Opening opening : quantity.openings) {
        wallHeaders += opening.width + 2 * 7.6;
      }
      // A corner is counted once for the two walls that meet at it
      for (Wall joined : new Wall [] {wall.getWallAtStart(), wall.getWallAtEnd()}) {
        if (joined != null && joined != wall) {
          int hash1 = System.identityHashCode(wall);
          int hash2 = System.identityHashCode(joined);
          corners.add(Math.min(hash1, hash2) + ":" + Math.max(hash1, hash2) + ":" + (joined == wall.getWallAtStart()
              ? Math.round(wall.getXStart()) + "," + Math.round(wall.getYStart())
              : Math.round(wall.getXEnd()) + "," + Math.round(wall.getYEnd())));
        }
      }
      wallCount++;
      studCount += wallStuds;
      openingCount += quantity.openings.size();
      wallLength += quantity.length;
      studLength += wallStuds * quantity.height;
      headerLength += wallHeaders;
      surface += quantity.getNetSide() * 2;
      if (wallCount <= 40) {
        lines.append("\n- ").append(getId(wall)).append(": ").append(format(quantity.length / 100, 2)).append(" m long, ")
            .append(format(quantity.height / 100, 2)).append(" m high, ").append(wallStuds).append(" studs");
        if (!quantity.openings.isEmpty()) {
          lines.append(", ").append(quantity.openings.size()).append(quantity.openings.size() == 1 ? " opening" : " openings")
              .append(", ").append(format(wallHeaders / 100, 2)).append(" m of header");
        }
      }
    }
    if (wallCount == 0) {
      throw new IOException(roundWalls > 0 ? "only straight walls are estimated, and the walls given are round"
          : "there is no wall to estimate");
    }
    // Two more studs at each corner
    double meanHeight = studLength / studCount;
    int cornerStuds = 2 * corners.size();
    studCount += cornerStuds;
    studLength += cornerStuds * meanHeight;
    double plateLength = plates * wallLength;
    String studName = format(stud [0]) + "x" + format(stud [1]);
    String headerName = format(header [0]) + "x" + format(header [1]);
    // A board foot is 1 in x 12 in x 1 ft
    double studBoardFeet = stud [0] * stud [1] / 12 * (studLength + plateLength) / CM_PER_FOOT;
    double headerBoardFeet = header [0] * header [1] / 12 * 2 * headerLength / CM_PER_FOOT;
    double squareMeters = surface / 10000;
    StringBuilder text = new StringBuilder("Framing estimate of " + wallCount + (wallCount == 1 ? " straight wall, " : " straight walls, ")
        + describeLength(wallLength) + " long, studs " + studName + " every " + format(spacing, 2) + " cm ("
        + format(spacing / 2.54, 1) + " in) - an ESTIMATE for a budget, not a structural design:");
    text.append("\n- Studs: ").append(studCount).append(" pieces of ").append(studName).append(", ")
        .append(describeLength(studLength)).append(" in all (one every ").append(format(spacing, 2))
        .append(" cm plus one at the end of each wall, 2 more for each of the ").append(openingCount)
        .append(openingCount == 1 ? " opening" : " openings").append(" and for each of the ").append(corners.size())
        .append(corners.size() == 1 ? " corner)" : " corners)");
    text.append("\n- Plates: ").append(describeLength(plateLength)).append(" of ").append(studName).append(" (")
        .append(format(plates)).append(" along each wall)");
    text.append("\n- Headers: ").append(openingCount).append(openingCount == 1 ? " header, " : " headers, ")
        .append(describeLength(headerLength)).append(" long in all (the width of each opening plus 2 x 7.6 cm), doubled: ")
        .append(describeLength(2 * headerLength)).append(" of ").append(headerName);
    text.append("\n- Sheathing and drywall: ").append(format(squareMeters, 2)).append(" m2 (")
        .append(format(squareMeters * 10.7639, 0)).append(" sq ft) on the two sides of the walls, openings deducted, or ")
        .append(format(squareMeters / 2, 2)).append(" m2 on one side: about ")
        .append((int)Math.ceil(squareMeters * 10.7639 / 32 - 1E-6)).append(" sheets of 4 ft x 8 ft for both sides");
    text.append("\n- Board feet: ").append(format(studBoardFeet, 0)).append(" of ").append(studName)
        .append(" (studs and plates) and ").append(format(headerBoardFeet, 0)).append(" of ").append(headerName)
        .append(" (headers), ").append(format(studBoardFeet + headerBoardFeet, 0)).append(" in all");
    if (waste > 0) {
      text.append("\n- With ").append(format(waste * 100)).append(" % of waste: ")
          .append((int)Math.ceil(studCount * (1 + waste) - 1E-6)).append(" studs, ")
          .append(describeLength(plateLength * (1 + waste))).append(" of plates, ")
          .append(describeLength(2 * headerLength * (1 + waste))).append(" of headers, ")
          .append(format((studBoardFeet + headerBoardFeet) * (1 + waste), 0)).append(" board feet, ")
          .append(format(squareMeters * (1 + waste), 2)).append(" m2 of sheathing and drywall");
    }
    text.append("\nBy wall:").append(lines);
    if (wallCount > 40) {
      text.append("\n- ... and ").append(wallCount - 40).append(" more walls");
    }
    if (roundWalls > 0) {
      text.append("\n").append(roundWalls).append(roundWalls == 1 ? " round wall isn't" : " round walls aren't").append(" estimated");
    }
    text.append("\nNot counted: blocking, cripples, sills under windows, bracing, hardware, and what a building code or an "
        + "engineer asks for. The studs are as long as the walls are high");
    notes.add(text.toString());
  }
}
