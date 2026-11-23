/*
 * HomeToolsMenus.java 13 Nov 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.swing;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemIds;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * The menu items that run by hand what the design assistant and the MCP server do with
 * commands: garden, symbols, schedules, furnishing a room, the sun, doors, measures.
 * Each one builds a command from the selection and a few choices, and runs it as one
 * change that Edit &gt; Undo reverts.
 * @author Sweet Home 3D
 */
public class HomeToolsMenus {
  private static final String [] SYMBOL_TYPES = {"outlet", "double_outlet", "switch", "three_way_switch",
      "ceiling_light", "wall_light", "fan", "smoke_detector", "thermostat", "data", "tv", "panel",
      "sink", "toilet_rough", "shower_drain", "hose_bib", "water_heater", "floor_drain"};
  private static final String [] ROOM_TYPES = {"(guessed from the name of the room)", "bedroom", "living_room",
      "dining_room", "kitchen", "office", "bathroom", "kids_room", "entry"};

  private final JComponent               parent;
  private final Home                     home;
  private final HomeController           controller;
  private final AssistantCommandExecutor executor;

  public HomeToolsMenus(JComponent parent, Home home, UserPreferences preferences, HomeController controller) {
    this.parent = parent;
    this.home = home;
    this.controller = controller;
    this.executor = new AssistantCommandExecutor(home, preferences, controller);
    this.executor.setModelSizeProvider(new AssistantModelSizeProvider());
    this.executor.setModelInspector(new AssistantModelInspector());
  }

  // ---- Running commands ----

  private static Map<String, Object> command(String action, Object... fields) {
    Map<String, Object> command = new LinkedHashMap<String, Object>();
    command.put("action", action);
    for (int i = 0; i + 1 < fields.length; i += 2) {
      if (fields [i + 1] != null) {
        command.put((String)fields [i], fields [i + 1]);
      }
    }
    return command;
  }

  /**
   * Runs commands as one change and returns what they answer.
   */
  private String runCommands(Map<String, Object>... commands) {
    HomeItemIds.ensureIds(this.home);
    Map<String, Object> request = new LinkedHashMap<String, Object>();
    request.put("commands", new ArrayList<Object>(Arrays.asList(commands)));
    String summary = this.executor.execute(AssistantCommandParser.parse(JSONParser.toJSON(request)).getCommands());
    return summary != null ? summary : "Nothing was changed.";
  }

  private void showMessage(String title, String text) {
    // What is written for the assistant isn't for the user: the sentence about Undo, the tally, the ids created
    StringBuilder message = new StringBuilder();
    for (String line : text.replace(" Use Edit > Undo to revert.", "").split("\n")) {
      if (!line.startsWith("Applied changes:") && !line.startsWith("Ids of what was created:")
          && line.trim().length() > 0) {
        message.append(message.length() > 0 ? "\n" : "").append(line);
      }
    }
    text = message.length() > 0 ? message.toString() : "Done.";
    if (text.length() < 900 && text.split("\n").length <= 6) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(this.parent), "<html><body style='width: 420px'>"
          + text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</body></html>",
          title, JOptionPane.INFORMATION_MESSAGE);
    } else {
      showText(title, text, null);
    }
  }

  /**
   * Shows a long answer, with a button that writes the schedule <code>csvType</code> in a file if it isn't <code>null</code>.
   */
  private void showText(final String title, String text, final String csvType) {
    final JTextArea area = new JTextArea(text.replace(" Use Edit > Undo to revert.", "").trim(), 22, 96);
    area.setEditable(false);
    area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
    area.setCaretPosition(0);
    JPanel panel = new JPanel(new BorderLayout(0, 6));
    panel.add(new JScrollPane(area), BorderLayout.CENTER);
    if (csvType != null) {
      JButton exportButton = new JButton("Export as CSV...");
      exportButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent ev) {
            File file = chooseFile(title, ".csv");
            if (file != null) {
              showMessage(title, runCommands(command("export_schedule", "type", csvType, "path", file.getPath(), "replace", Boolean.TRUE)));
            }
          }
        });
      JPanel south = new JPanel(new BorderLayout());
      south.add(exportButton, BorderLayout.WEST);
      panel.add(south, BorderLayout.SOUTH);
    }
    JOptionPane.showMessageDialog(SwingUtilities.getRootPane(this.parent), panel, title, JOptionPane.PLAIN_MESSAGE);
  }

  private File chooseFile(String title, String extension) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(title);
    if (this.home.getName() != null) {
      File homeFile = new File(this.home.getName());
      chooser.setSelectedFile(new File(homeFile.getParentFile(),
          homeFile.getName().replaceFirst("\\.[^.]*$", "") + extension));
    }
    if (chooser.showSaveDialog(SwingUtilities.getRootPane(this.parent)) != JFileChooser.APPROVE_OPTION) {
      return null;
    }
    File file = chooser.getSelectedFile();
    if (!file.getName().toLowerCase().endsWith(extension)) {
      file = new File(file.getPath() + extension);
    }
    if (file.exists() && JOptionPane.showConfirmDialog(SwingUtilities.getRootPane(this.parent),
        file.getName() + " exists. Replace it?", title, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
      return null;
    }
    return file;
  }

  private boolean confirm(String title, JComponent form) {
    return JOptionPane.showConfirmDialog(SwingUtilities.getRootPane(this.parent), form, title,
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
  }

  private static JPanel form(Object... labelsAndFields) {
    JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
    for (int i = 0; i + 1 < labelsAndFields.length; i += 2) {
      panel.add(labelsAndFields [i] instanceof JComponent ? (JComponent)labelsAndFields [i] : new JLabel((String)labelsAndFields [i]));
      panel.add((JComponent)labelsAndFields [i + 1]);
    }
    return panel;
  }

  private static Double number(JTextField field, double minimum, double maximum) {
    try {
      double value = Double.parseDouble(field.getText().trim().replace(',', '.'));
      return value >= minimum && value <= maximum ? Double.valueOf(value) : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private JMenuItem item(String name, final Runnable action) {
    return new JMenuItem(new AbstractAction(name) {
        public void actionPerformed(ActionEvent ev) {
          action.run();
        }
      });
  }

  // ---- Selection ----

  private <T> List<T> getSelected(Class<T> type) {
    List<T> items = new ArrayList<T>();
    for (Selectable item : this.home.getSelectedItems()) {
      if (type.isInstance(item)) {
        items.add(type.cast(item));
      }
    }
    return items;
  }

  private List<String> getIds(List<?> items) {
    HomeItemIds.ensureIds(this.home);
    List<String> ids = new ArrayList<String>();
    for (Object item : items) {
      String id = HomeItemIds.getId(item);
      if (id != null) {
        ids.add(id);
      }
    }
    return ids;
  }

  private static List<Object> toList(float [][] points) {
    List<Object> list = new ArrayList<Object>();
    for (float [] point : points) {
      list.add(Arrays.asList(new Object [] {Double.valueOf(point [0]), Double.valueOf(point [1])}));
    }
    return list;
  }

  // ---- Garden ----

  /**
   * Returns the menu that builds fences, hedges, paths and planting beds along the selected line.
   */
  public JMenu createGardenMenu() {
    JMenu menu = new JMenu("Garden");
    menu.add(item("Fence along the selected line...", new Runnable() {
        public void run() {
          addGardenPiece("add_fence", "Fence");
        }
      }));
    menu.add(item("Hedge along the selected line...", new Runnable() {
        public void run() {
          addGardenPiece("add_hedge", "Hedge");
        }
      }));
    menu.add(item("Path along the selected line...", new Runnable() {
        public void run() {
          addGardenPiece("add_path", "Path");
        }
      }));
    menu.add(item("Planting bed in the selected line or room...", new Runnable() {
        public void run() {
          addGardenPiece("add_planting_bed", "Planting bed");
        }
      }));
    return menu;
  }

  private void addGardenPiece(String action, String title) {
    List<Polyline> lines = getSelected(Polyline.class);
    List<Room> rooms = getSelected(Room.class);
    boolean bed = "add_planting_bed".equals(action);
    if (lines.size() + (bed ? rooms.size() : 0) != 1) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(this.parent),
          "<html>Draw a line where the " + title.toLowerCase() + " goes with <b>Plan &gt; Create polylines</b>"
          + (bed ? " (around the bed), or a room," : ",") + "<br>select it, then choose this item again.</html>",
          title, JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    final Polyline line = lines.isEmpty() ? null : lines.get(0);
    float [][] points = line != null ? line.getPoints() : rooms.get(0).getPoints();
    if (points.length < (bed ? 3 : 2)) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(this.parent),
          bed ? "A planting bed needs a line of 3 points at least." : "The line needs 2 points at least.");
      return;
    }
    JCheckBox deleteLineCheckBox = new JCheckBox("Delete the line it is built from", true);
    Map<String, Object> command = command(action, "points", toList(points));
    if (line != null && line.isClosedPath() && !bed) {
      command.put("closed", Boolean.TRUE);
    }
    if ("add_fence".equals(action)) {
      final String [][] styles = com.eteks.sweethome3d.viewcontroller.HomeAssistantContext.getFenceStyles();
      String [] styleNames = new String [styles.length];
      int picket = 0;
      for (int i = 0; i < styles.length; i++) {
        styleNames [i] = styles [i][0];
        if (styles [i][0].equals("picket")) {
          picket = i;
        }
      }
      final JComboBox styleComboBox = new JComboBox(styleNames);
      styleComboBox.setMaximumRowCount(20);
      final JTextField heightField = new JTextField(styles [picket][3], 6);
      final JTextField spacingField = new JTextField(styles [picket][4], 6);
      final JLabel styleLabel = new JLabel(styles [picket][1] + ": " + styles [picket][2]);
      styleComboBox.setSelectedIndex(picket);
      // Each style comes with its usual sizes
      styleComboBox.addActionListener(new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent ev) {
            String [] style = styles [Math.max(0, styleComboBox.getSelectedIndex())];
            heightField.setText(style [3]);
            spacingField.setText(style [4]);
            String description = style [1] + ": " + style [2];
            styleLabel.setText(description.length() > 90 ? description.substring(0, 88) + "..." : description);
          }
        });
      if (!confirm(title, form("Style", styleComboBox, "", styleLabel, "Height (cm)", heightField,
          "Distance between posts (cm)", spacingField, deleteLineCheckBox, new JLabel()))) {
        return;
      }
      command.put("style", styleComboBox.getSelectedItem());
      command.put("height", number(heightField, 5, 800));
      command.put("post_spacing", number(spacingField, 20, 1500));
    } else if ("add_hedge".equals(action)) {
      JTextField heightField = new JTextField("150", 6);
      JTextField widthField = new JTextField("60", 6);
      if (!confirm(title, form("Height (cm)", heightField, "Width (cm)", widthField, deleteLineCheckBox, new JLabel()))) {
        return;
      }
      command.put("height", number(heightField, 20, 600));
      command.put("width", number(widthField, 10, 400));
    } else if ("add_path".equals(action)) {
      JTextField widthField = new JTextField("100", 6);
      JCheckBox stonesCheckBox = new JCheckBox("Stepping stones");
      JCheckBox edgingCheckBox = new JCheckBox("Edging");
      if (!confirm(title, form("Width (cm)", widthField, stonesCheckBox, edgingCheckBox, deleteLineCheckBox, new JLabel()))) {
        return;
      }
      command.put("width", number(widthField, 20, 1000));
      if (stonesCheckBox.isSelected()) {
        command.put("stepping_stones", new LinkedHashMap<String, Object>());
      }
      if (edgingCheckBox.isSelected()) {
        command.put("edging", Boolean.TRUE);
      }
    } else {
      JCheckBox borderCheckBox = new JCheckBox("Edging around it", true);
      if (line == null) {
        deleteLineCheckBox.setSelected(false);
        deleteLineCheckBox.setEnabled(false);
      }
      if (!confirm(title, form(borderCheckBox, new JLabel(), deleteLineCheckBox, new JLabel()))) {
        return;
      }
      if (borderCheckBox.isSelected()) {
        command.put("border", Boolean.TRUE);
      }
    }
    String summary;
    if (line != null && deleteLineCheckBox.isSelected()) {
      summary = runCommands(command, command("delete", "ids", getIds(lines)));
    } else {
      summary = runCommands(command);
    }
    showMessage(title, summary);
  }

  // ---- Symbols ----

  /**
   * Returns the menu that adds electrical and plumbing symbols.
   */
  public JMenu createSymbolsMenu() {
    JMenu menu = new JMenu("Electrical and plumbing symbols");
    menu.add(item("Add a symbol...", new Runnable() {
        public void run() {
          addSymbol();
        }
      }));
    menu.add(item("Outlets along the walls of the selected rooms...", new Runnable() {
        public void run() {
          addOutlets();
        }
      }));
    return menu;
  }

  private void addSymbol() {
    List<Wall> walls = getSelected(Wall.class);
    List<Room> rooms = getSelected(Room.class);
    Wall wall = walls.size() == 1 ? walls.get(0) : null;
    JComboBox typeComboBox = new JComboBox(SYMBOL_TYPES);
    JTextField labelField = new JTextField(10);
    JTextField circuitField = new JTextField(10);
    JTextField offsetField = new JTextField(wall != null ? String.valueOf(Math.round(wall.getLength() / 2)) : "", 6);
    JComboBox sideComboBox = new JComboBox(new String [] {"(the side of the room)", "left", "right"});
    JPanel form = wall != null
        ? form("Symbol", typeComboBox, "Distance from the start of the selected wall (cm)", offsetField,
            "Side of the wall", sideComboBox, "Text beside it (optional)", labelField, "Circuit (optional)", circuitField)
        : form("Symbol", typeComboBox, "Text beside it (optional)", labelField, "Circuit (optional)", circuitField);
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(form, BorderLayout.CENTER);
    panel.add(new JLabel(wall != null ? "The symbol goes on the selected wall."
        : !rooms.isEmpty() ? "The symbol goes at the middle of the selected room: move it as any piece of furniture."
        : "<html>The symbol goes at the middle of the plan: move it as any piece of furniture.<br>"
            + "Select a wall first to put it on that wall.</html>"), BorderLayout.SOUTH);
    if (!confirm("Add a symbol", panel)) {
      return;
    }
    Map<String, Object> command = command("add_symbol", "type", typeComboBox.getSelectedItem(),
        "label", labelField.getText().trim().length() > 0 ? labelField.getText().trim() : null,
        "circuit", circuitField.getText().trim().length() > 0 ? circuitField.getText().trim() : null);
    if (wall != null) {
      command.put("wall", HomeItemIds.getId(wall) != null ? HomeItemIds.getId(wall) : getIds(walls).get(0));
      Double offset = number(offsetField, 0, wall.getLength());
      command.put("offset", offset != null ? offset : Double.valueOf(wall.getLength() / 2));
      if (sideComboBox.getSelectedIndex() > 0) {
        command.put("side", sideComboBox.getSelectedItem());
      }
    } else if (!rooms.isEmpty()) {
      command.put("x", Double.valueOf(rooms.get(0).getXCenter()));
      command.put("y", Double.valueOf(rooms.get(0).getYCenter()));
    } else {
      float [] center = getPlanCenter();
      command.put("x", Double.valueOf(center [0]));
      command.put("y", Double.valueOf(center [1]));
    }
    showMessage("Add a symbol", runCommands(command));
  }

  private float [] getPlanCenter() {
    float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    for (Wall wall : this.home.getWalls()) {
      if (wall.isAtLevel(this.home.getSelectedLevel())) {
        minX = Math.min(minX, Math.min(wall.getXStart(), wall.getXEnd()));
        maxX = Math.max(maxX, Math.max(wall.getXStart(), wall.getXEnd()));
        minY = Math.min(minY, Math.min(wall.getYStart(), wall.getYEnd()));
        maxY = Math.max(maxY, Math.max(wall.getYStart(), wall.getYEnd()));
      }
    }
    return minX > maxX ? new float [] {0, 0} : new float [] {(minX + maxX) / 2, (minY + maxY) / 2};
  }

  private void addOutlets() {
    List<Room> rooms = getSelected(Room.class);
    if (rooms.isEmpty()) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(this.parent), "Select the rooms first (double-click in a room selects it).");
      return;
    }
    JComboBox typeComboBox = new JComboBox(new String [] {"outlet", "double_outlet", "data"});
    JTextField spacingField = new JTextField("360", 6);
    if (!confirm("Outlets along walls", form("Symbol", typeComboBox, "Distance between them (cm)", spacingField))) {
      return;
    }
    List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
    for (String id : getIds(rooms)) {
      commands.add(command("add_symbols", "room", id, "type", typeComboBox.getSelectedItem(),
          "spacing", number(spacingField, 50, 2000)));
    }
    showMessage("Outlets along walls", runCommands(commands.toArray(new Map [commands.size()])));
  }

  // ---- Schedules and reports ----

  /**
   * Returns the menu of the schedules.
   */
  public JMenu createSchedulesMenu() {
    JMenu menu = new JMenu("Schedules");
    String [][] schedules = {{"Doors and windows...", "doors_and_windows"}, {"Rooms...", "rooms"},
        {"Furniture...", "furniture"}, {"Symbols...", "symbols"}};
    for (final String [] schedule : schedules) {
      menu.add(item(schedule [0], new Runnable() {
          public void run() {
            showText("Schedule: " + schedule [0].replace("...", ""), runCommands(command("schedule", "type", schedule [1])), schedule [1]);
          }
        }));
    }
    menu.addSeparator();
    menu.add(item("Write door and window marks in the plan", new Runnable() {
        public void run() {
          showMessage("Door and window marks", runCommands(command("schedule", "type", "doors_and_windows",
              "tag_marks", Boolean.TRUE, "labels", Boolean.TRUE)));
        }
      }));
    return menu;
  }

  public JMenuItem createFramingEstimateItem() {
    return item("Framing estimate...", new Runnable() {
        public void run() {
          List<Wall> walls = getSelected(Wall.class);
          JTextField spacingField = new JTextField("40.64", 6);
          JComboBox studComboBox = new JComboBox(new String [] {"2x4", "2x6"});
          JPanel panel = new JPanel(new BorderLayout(0, 8));
          panel.add(form("Distance between studs (cm; 40.64 = 16 in)", spacingField, "Studs", studComboBox), BorderLayout.CENTER);
          panel.add(new JLabel(walls.isEmpty() ? "For every straight wall of the home (select walls first to count only them)."
              : "For the " + walls.size() + " selected walls."), BorderLayout.SOUTH);
          if (confirm("Framing estimate", panel)) {
            showText("Framing estimate", runCommands(command("framing_estimate", "ids", walls.isEmpty() ? null : getIds(walls),
                "stud_spacing", number(spacingField, 10, 200), "stud", studComboBox.getSelectedItem())), null);
          }
        }
      });
  }

  public JMenuItem createCheckPlanItem() {
    return item("Check the plan...", new Runnable() {
        public void run() {
          HomeItemIds.ensureIds(home);
          String report = executor.checkHome();
          if (!report.contains("fix_home")) {
            showText("Check the plan", report, null);
            return;
          }
          JTextArea area = new JTextArea(report.replaceAll("\\{\"action\":\"fix_home\"\\}", "the button Fix"), 18, 90);
          area.setEditable(false);
          area.setCaretPosition(0);
          Object [] options = {"Fix what can be fixed", "Close"};
          if (JOptionPane.showOptionDialog(SwingUtilities.getRootPane(parent), new JScrollPane(area), "Check the plan",
              JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options [1]) == 0) {
            showMessage("Check the plan", runCommands(command("fix_home")));
          }
        }
      });
  }

  public JMenuItem createMeasureItem() {
    return item("Measure the selection", new Runnable() {
        public void run() {
          List<Selectable> selection = new ArrayList<Selectable>(home.getSelectedItems());
          List<String> ids = getIds(selection);
          String summary;
          if (ids.size() == 2) {
            summary = runCommands(command("measure", "from", ids.get(0), "to", ids.get(1)));
          } else if (ids.size() == 1 && selection.get(0) instanceof HomePieceOfFurniture
                     && !(selection.get(0) instanceof HomeDoorOrWindow)) {
            summary = runCommands(command("measure", "clearance", ids.get(0)));
          } else if (!ids.isEmpty()) {
            summary = runCommands(command("measure", "ids", ids));
          } else {
            summary = "Select one piece of furniture to know the free space around it, two items to know the "
                + "distance between them, or more for the size of what they take together.";
          }
          showMessage("Measure", summary);
        }
      });
  }

  // ---- Furniture ----

  public JMenuItem createFurnishRoomItem() {
    return item("Furnish the selected room...", new Runnable() {
        public void run() {
          List<Room> rooms = getSelected(Room.class);
          if (rooms.size() != 1) {
            JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
                "Select one room first (double-click in a room selects it).");
            return;
          }
          JComboBox typeComboBox = new JComboBox(ROOM_TYPES);
          JCheckBox replaceCheckBox = new JCheckBox("Remove the furniture that is in the room first");
          JPanel panel = new JPanel(new BorderLayout(0, 8));
          panel.add(form("Kind of room", typeComboBox, replaceCheckBox, new JLabel()), BorderLayout.CENTER);
          panel.add(new JLabel("<html>Places catalog furniture against the free walls, clear of doors, windows and passages.<br>"
              + "A starting point to arrange by hand: Edit &gt; Undo removes it.</html>"), BorderLayout.SOUTH);
          if (confirm("Furnish a room", panel)) {
            showMessage("Furnish a room", runCommands(command("furnish_room", "room", getIds(rooms).get(0),
                "type", typeComboBox.getSelectedIndex() > 0 ? typeComboBox.getSelectedItem() : null,
                "replace", Boolean.valueOf(replaceCheckBox.isSelected()))));
          }
        }
      });
  }

  public JMenuItem createOpenDoorsItem(final boolean open) {
    return item(open ? "Open the selected doors and windows..." : "Close the selected doors and windows", new Runnable() {
        public void run() {
          List<HomePieceOfFurniture> pieces = getSelected(HomePieceOfFurniture.class);
          if (pieces.isEmpty()) {
            JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "Select doors or windows first.");
            return;
          }
          Double angle = Double.valueOf(0);
          if (open) {
            String answer = JOptionPane.showInputDialog(SwingUtilities.getRootPane(parent),
                "Angle in degrees (a negative angle opens the other way):", "90");
            if (answer == null) {
              return;
            }
            angle = number(new JTextField(answer), -180, 180);
            if (angle == null) {
              JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "The angle goes from -180 to 180.");
              return;
            }
          }
          String summary = runCommands(command("open_door", "ids", getIds(pieces), "angle", angle));
          if (summary.contains("can't be opened") || summary.contains("Couldn't")) {
            showMessage(open ? "Open doors and windows" : "Close doors and windows", summary);
          }
        }
      });
  }

  // ---- 3D view ----

  public JMenuItem createSunItem() {
    return item("Sun and shadows...", new Runnable() {
        public void run() {
          Object view3D = controller.getHomeController3D().getView();
          SunStudyPanel.showDialog(parent, home, view3D instanceof HomeComponent3D ? (HomeComponent3D)view3D : null);
        }
      });
  }

  /**
   * What draws the elevation picture of a side of the building.
   */
  public interface ElevationRenderer {
    /**
     * Returns the PNG picture of the given side, or <code>null</code> with the reason in <code>message [0]</code>.
     */
    byte [] render(String side, boolean dimensions, String [] message);
  }

  public JMenuItem createElevationPictureItem(final ElevationRenderer renderer) {
    return item("Export elevation picture...", new Runnable() {
        public void run() {
          JComboBox sideComboBox = new JComboBox(new String [] {"south", "north", "east", "west"});
          JCheckBox dimensionsCheckBox = new JCheckBox("Width of the building and heights of its levels", true);
          if (!confirm("Elevation picture", form("Side the building is seen from", sideComboBox, dimensionsCheckBox, new JLabel()))) {
            return;
          }
          String [] message = {null};
          byte [] image = renderer.render((String)sideComboBox.getSelectedItem(), dimensionsCheckBox.isSelected(), message);
          if (image == null) {
            JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
                message [0] != null ? message [0] : "The elevation couldn't be drawn.");
            return;
          }
          File file = chooseFile("Elevation picture", ".png");
          if (file != null) {
            try {
              java.io.OutputStream out = new java.io.FileOutputStream(file);
              try {
                out.write(image);
              } finally {
                out.close();
              }
            } catch (java.io.IOException ex) {
              JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "Couldn't write " + file + ": " + ex.getMessage());
            }
          }
        }
      });
  }
}
