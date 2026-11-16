/*
 * HomeDesignToolsView.java 16 Nov 2026
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
package com.eteks.sweethome3d.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.AssistantDesignCommands;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeReport;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Dialogs of tools found in comparable interior design programs: replacing
 * furniture in place, rooms drawn from a shape, decoration styles and the
 * report of the quantities of a home. They run the same undoable commands as
 * the design assistant.
 * @author Sweet Home 3D
 */
public final class HomeDesignToolsView {
  private HomeDesignToolsView() {
  }

  /**
   * Runs an assistant command and shows its answer if it changed nothing.
   * @return <code>true</code> if the home was changed
   */
  private static boolean run(JComponent parent, Home home, UserPreferences preferences,
                             HomeController controller, String command) {
    AssistantCommandExecutor executor = new AssistantCommandExecutor(home, preferences, controller);
    String summary = executor.execute(AssistantCommandParser.parse("{\"commands\":[" + command + "]}").getCommands());
    if (summary != null && !summary.startsWith("Applied changes")) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), summary);
      return false;
    }
    return true;
  }

  /**
   * Replaces the selected furniture of the home by the piece selected in
   * the catalog, at the same place.
   */
  public static void replaceSelectedFurniture(JComponent parent, Home home, UserPreferences preferences,
                                              HomeController controller) {
    List<HomePieceOfFurniture> selection = Home.getFurnitureSubList(home.getSelectedItems());
    List<CatalogPieceOfFurniture> catalogSelection = controller.getFurnitureCatalogController().getSelectedFurniture();
    if (selection.isEmpty() || catalogSelection.size() != 1) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
          "<html>To replace furniture:<br>1. select the pieces to replace in the plan or the furniture list,<br>"
          + "2. select the one piece that replaces them in the furniture catalog,<br>"
          + "3. choose this menu item again.<br><br>The new piece takes the place, angle, level and tags of the old one.</html>");
      return;
    }
    run(parent, home, preferences, controller, "{\"action\":\"replace_furniture\",\"target\":\"selection\",\"name\":"
        + JSONParser.toJSONString(catalogSelection.get(0).getName()) + "}");
  }

  /**
   * Asks for a shape and its sizes, and adds the room with its walls.
   */
  public static void showRoomShapeDialog(JComponent parent, Home home, UserPreferences preferences,
                                         HomeController controller) {
    final JComboBox shapeComboBox = new JComboBox(new String [] {
        "Rectangle", "L shape (a corner cut out)", "T shape (a bar and a leg)", "U shape (a notch in one side)"});
    JTextField nameField = new JTextField(14);
    final JTextField widthField = new JTextField("500", 6);
    final JTextField depthField = new JTextField("400", 6);
    final JTextField cutWidthField = new JTextField("200", 6);
    final JTextField cutDepthField = new JTextField("200", 6);
    final JLabel cutWidthLabel = new JLabel("Width of the part cut out (cm):");
    final JLabel cutDepthLabel = new JLabel("Depth of the part cut out (cm):");
    ActionListener shapeListener = new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          boolean cut = shapeComboBox.getSelectedIndex() > 0;
          cutWidthField.setEnabled(cut);
          cutDepthField.setEnabled(cut);
          cutWidthLabel.setEnabled(cut);
          cutDepthLabel.setEnabled(cut);
          cutWidthLabel.setText(shapeComboBox.getSelectedIndex() == 2
              ? "Width cut out on each side of the leg (cm):" : "Width of the part cut out (cm):");
        }
      };
    shapeComboBox.addActionListener(shapeListener);
    shapeListener.actionPerformed(null);
    JPanel panel = new JPanel(new GridLayout(0, 2, 6, 4));
    panel.add(new JLabel("Shape:"));
    panel.add(shapeComboBox);
    panel.add(new JLabel("Name:"));
    panel.add(nameField);
    panel.add(new JLabel("Overall width, inside the walls (cm):"));
    panel.add(widthField);
    panel.add(new JLabel("Overall depth, inside the walls (cm):"));
    panel.add(depthField);
    panel.add(cutWidthLabel);
    panel.add(cutWidthField);
    panel.add(cutDepthLabel);
    panel.add(cutDepthField);
    JPanel message = new JPanel(new BorderLayout(0, 8));
    message.add(panel, BorderLayout.CENTER);
    message.add(new JLabel("<html>The room is added with its walls to the right of the plan, selected:<br>"
        + "drag it where you want it.</html>"), BorderLayout.SOUTH);
    if (JOptionPane.showConfirmDialog(SwingUtilities.getRootPane(parent), message, "Add room from shape",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
      return;
    }
    try {
      String shape = AssistantDesignCommands.ROOM_SHAPES [shapeComboBox.getSelectedIndex()];
      int roomCount = home.getRooms().size();
      int wallCount = home.getWalls().size();
      if (run(parent, home, preferences, controller, "{\"action\":\"add_room_shape\",\"shape\":\"" + shape + "\","
            + "\"name\":" + JSONParser.toJSONString(nameField.getText().trim())
            + ",\"width\":" + parse(widthField) + ",\"depth\":" + parse(depthField)
            + ",\"cut_width\":" + parse(cutWidthField) + ",\"cut_depth\":" + parse(cutDepthField) + "}")) {
        // Select the new room and walls, ready to be moved
        List<com.eteks.sweethome3d.model.Selectable> added = new java.util.ArrayList<com.eteks.sweethome3d.model.Selectable>();
        added.addAll(home.getRooms().subList(Math.min(roomCount, home.getRooms().size()), home.getRooms().size()));
        List<com.eteks.sweethome3d.model.Wall> walls = new java.util.ArrayList<com.eteks.sweethome3d.model.Wall>(home.getWalls());
        added.addAll(walls.subList(Math.min(wallCount, walls.size()), walls.size()));
        home.setSelectedItems(added);
      }
    } catch (NumberFormatException ex) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "Sizes must be numbers, in centimeters.");
    }
  }

  private static double parse(JTextField field) {
    return Double.parseDouble(field.getText().trim().replace(',', '.'));
  }

  /**
   * An icon showing the colors of the walls and of the floor of a style.
   */
  private static final class StyleIcon implements Icon {
    private final AssistantDesignCommands.Style style;

    StyleIcon(AssistantDesignCommands.Style style) {
      this.style = style;
    }

    public int getIconWidth() {
      return 36;
    }

    public int getIconHeight() {
      return 18;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(new Color(this.style.getWallColor()));
      g.fillRect(x, y, 18, 18);
      g.setColor(new Color(this.style.getFloorColor()));
      g.fillRect(x + 18, y, 18, 18);
      g.setColor(Color.GRAY);
      g.drawRect(x, y, 35, 17);
    }
  }

  /**
   * Asks for a decoration style and applies it to the selected rooms, or to
   * every room of the selected level.
   */
  public static void showStyleDialog(JComponent parent, Home home, UserPreferences preferences,
                                     HomeController controller) {
    JComboBox styleComboBox = new JComboBox(AssistantDesignCommands.getStyles().toArray());
    styleComboBox.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
          JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (value instanceof AssistantDesignCommands.Style) {
            label.setIcon(new StyleIcon((AssistantDesignCommands.Style)value));
          }
          return label;
        }
      });
    boolean roomsSelected = !Home.getRoomsSubList(home.getSelectedItems()).isEmpty();
    JPanel message = new JPanel(new BorderLayout(0, 8));
    message.add(new JLabel("<html>Colors the walls, the floor and the ceiling of "
        + (roomsSelected ? "the <b>selected rooms</b>" : "<b>every room of this level</b> (select rooms to limit it)")
        + ".<br>The floor gets a matching texture of the catalog when there is one.<br>"
        + "One step of Edit &gt; Undo brings everything back.</html>"), BorderLayout.NORTH);
    message.add(styleComboBox, BorderLayout.CENTER);
    if (JOptionPane.showConfirmDialog(SwingUtilities.getRootPane(parent), message, "Apply style to rooms",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
      AssistantDesignCommands.Style style = (AssistantDesignCommands.Style)styleComboBox.getSelectedItem();
      run(parent, home, preferences, controller, "{\"action\":\"apply_style\",\"style\":"
          + JSONParser.toJSONString(style.getName()) + (roomsSelected ? ",\"target\":\"selection\"" : "") + "}");
    }
  }

  /**
   * Shows the quantities of the home (areas, walls, furniture and prices)
   * and its budget, computed from unit prices that can be changed here.
   */
  public static void showReportDialog(final JComponent parent, final Home home) {
    final JTextArea textArea = new JTextArea(HomeReport.describe(home), 24, 70);
    textArea.setEditable(false);
    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textArea.getFont().getSize()));
    textArea.setCaretPosition(0);
    // Unit prices of the budget
    final JTextField [] priceFields = new JTextField [HomeReport.UNIT_PRICE_KEYS.length];
    JPanel pricesPanel = new JPanel(new GridLayout(0, 2, 6, 3));
    for (int i = 0; i < priceFields.length; i++) {
      java.math.BigDecimal price = HomeReport.getUnitPrice(home, HomeReport.UNIT_PRICE_KEYS [i]);
      priceFields [i] = new JTextField(price != null ? price.toPlainString() : "", 8);
      pricesPanel.add(new JLabel(HomeReport.getUnitPriceLabel(HomeReport.UNIT_PRICE_KEYS [i]) + ":"));
      pricesPanel.add(priceFields [i]);
    }
    final JTextField currencyField = new JTextField(
        HomeReport.getBudgetCurrency(home) != null ? HomeReport.getBudgetCurrency(home) : "", 8);
    pricesPanel.add(new JLabel("Currency (EUR, USD...):"));
    pricesPanel.add(currencyField);
    JButton updateButton = new JButton("Update budget");
    updateButton.setToolTipText("Stores these unit prices in the home and computes the budget again");
    updateButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          try {
            java.math.BigDecimal [] prices = new java.math.BigDecimal [priceFields.length];
            for (int i = 0; i < priceFields.length; i++) {
              String text = priceFields [i].getText().trim().replace(',', '.');
              prices [i] = text.length() > 0 ? new java.math.BigDecimal(text) : null;
            }
            for (int i = 0; i < priceFields.length; i++) {
              HomeReport.setUnitPrice(home, HomeReport.UNIT_PRICE_KEYS [i], prices [i]);
            }
            HomeReport.setBudgetCurrency(home, currencyField.getText());
            textArea.setText(HomeReport.describe(home));
            textArea.setCaretPosition(textArea.getDocument().getLength());
          } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "Prices must be numbers; leave a field empty for no price.");
          }
        }
      });
    JButton copyButton = new JButton("Copy");
    copyButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textArea.getText()), null);
        }
      });
    JButton saveButton = new JButton("Save as text file...");
    saveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          JFileChooser chooser = new JFileChooser();
          chooser.setSelectedFile(new File("home-report.txt"));
          if (chooser.showSaveDialog(SwingUtilities.getRootPane(parent)) == JFileChooser.APPROVE_OPTION) {
            writeFile(parent, chooser.getSelectedFile(), textArea.getText());
          }
        }
      });
    JPanel buttons = new JPanel();
    buttons.add(updateButton);
    buttons.add(copyButton);
    buttons.add(saveButton);
    JPanel southPanel = new JPanel(new BorderLayout(0, 4));
    southPanel.add(pricesPanel, BorderLayout.CENTER);
    southPanel.add(buttons, BorderLayout.SOUTH);
    JPanel panel = new JPanel(new BorderLayout(0, 6));
    panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
    panel.add(southPanel, BorderLayout.SOUTH);
    JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), panel, "Home report and budget",
        JOptionPane.PLAIN_MESSAGE);
  }

  private static boolean writeFile(JComponent parent, File file, String text) {
    try {
      Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
      try {
        writer.write(text);
      } finally {
        writer.close();
      }
      return true;
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
          "The file couldn't be written: " + ex.getMessage());
      return false;
    }
  }

  /**
   * Asks for a side and writes the elevation drawing of the home seen from it.
   */
  public static void showElevationDialog(JComponent parent, Home home) {
    JComboBox sideComboBox = new JComboBox(new String [] {
        "Front - seen from the bottom of the plan (south)", "Back - seen from the top of the plan (north)",
        "Right side (east)", "Left side (west)"});
    JTextField sectionField = new JTextField("0", 6);
    JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
    panel.add(new JLabel("Side the home is seen from:"));
    panel.add(sideComboBox);
    panel.add(new JLabel("<html>Section: distance in cm behind the front of the home where it is cut,<br>"
        + "to show the inside (0 = the outside of the home):</html>"));
    panel.add(sectionField);
    panel.add(new JLabel("<html>The drawing is a SVG file at 1 unit per cm, made of the outline of each<br>"
        + "wall, door, window and piece of furniture. Browsers, Inkscape and<br>"
        + "office suites open it.</html>"));
    if (JOptionPane.showConfirmDialog(SwingUtilities.getRootPane(parent), panel, "Export elevation drawing",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
      return;
    }
    float section;
    try {
      section = Math.max(0, (float)parse(sectionField));
    } catch (NumberFormatException ex) {
      section = 0;
    }
    String side = com.eteks.sweethome3d.viewcontroller.ElevationDrawing.SIDES [sideComboBox.getSelectedIndex()];
    String svg = com.eteks.sweethome3d.viewcontroller.ElevationDrawing.createSVG(home, side, section);
    if (svg == null) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "There is nothing to draw from this side.");
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File("elevation-" + side + ".svg"));
    if (chooser.showSaveDialog(SwingUtilities.getRootPane(parent)) == JFileChooser.APPROVE_OPTION) {
      File file = chooser.getSelectedFile();
      if (!file.getName().toLowerCase().endsWith(".svg")) {
        file = new File(file.getPath() + ".svg");
      }
      if (writeFile(parent, file, svg)) {
        try {
          java.awt.Desktop.getDesktop().browse(file.toURI());
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), "The drawing is in " + file);
        }
      }
    }
  }

  /**
   * Shows the versions of the home: save one, open one, compare one with
   * the current state, delete one.
   */
  public static void showVersionsDialog(final JComponent parent, final Home home, final HomeController controller) {
    if (home.getName() == null) {
      JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
          "Save the home in a file first: its versions are kept in a folder beside that file.");
      return;
    }
    final javax.swing.DefaultListModel listModel = new javax.swing.DefaultListModel();
    final java.util.List<File> versions = new java.util.ArrayList<File>();
    final Runnable refresh = new Runnable() {
        public void run() {
          versions.clear();
          versions.addAll(com.eteks.sweethome3d.viewcontroller.HomeVersions.getVersions(home));
          listModel.clear();
          for (File version : versions) {
            listModel.addElement(com.eteks.sweethome3d.viewcontroller.HomeVersions.getLabel(version));
          }
        }
      };
    refresh.run();
    final JList list = new JList(listModel);
    list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    list.setVisibleRowCount(10);
    final JTextArea comparisonArea = new JTextArea(9, 52);
    comparisonArea.setEditable(false);
    comparisonArea.setLineWrap(true);
    comparisonArea.setWrapStyleWord(true);
    comparisonArea.setText("Choose a version and Compare to see what changed since.");
    JButton saveButton = new JButton("Save a version...");
    JButton compareButton = new JButton("Compare with now");
    JButton openButton = new JButton("Open in a new window");
    JButton deleteButton = new JButton("Delete");
    saveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          String label = JOptionPane.showInputDialog(SwingUtilities.getRootPane(parent),
              "Name of this version (for instance \"before moving the kitchen\"):", "Save a version",
              JOptionPane.QUESTION_MESSAGE);
          if (label != null) {
            try {
              controller.saveHomeVersion(label);
              refresh.run();
              list.setSelectedIndex(0);
            } catch (Exception ex) {
              JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
                  "The version couldn't be saved: " + ex.getMessage());
            }
          }
        }
      });
    compareButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          int index = list.getSelectedIndex();
          if (index >= 0) {
            try {
              comparisonArea.setText("From \"" + listModel.get(index) + "\" to now:\n"
                  + controller.compareWithHomeVersion(versions.get(index)));
              comparisonArea.setCaretPosition(0);
            } catch (Exception ex) {
              comparisonArea.setText("This version couldn't be read: " + ex.getMessage());
            }
          }
        }
      });
    openButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          int index = list.getSelectedIndex();
          if (index >= 0) {
            controller.open(versions.get(index).getPath());
          }
        }
      });
    deleteButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          int index = list.getSelectedIndex();
          if (index >= 0 && JOptionPane.showConfirmDialog(SwingUtilities.getRootPane(parent),
                "Delete the version \"" + listModel.get(index) + "\"? This can't be undone.", "Delete version",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            versions.get(index).delete();
            refresh.run();
          }
        }
      });
    JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
    buttons.add(saveButton);
    buttons.add(compareButton);
    buttons.add(openButton);
    buttons.add(deleteButton);
    JPanel east = new JPanel(new BorderLayout());
    east.add(buttons, BorderLayout.NORTH);
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.add(new JLabel("<html>A version is a copy of the home as it is now, kept in the folder<br><i>"
        + com.eteks.sweethome3d.viewcontroller.HomeVersions.getFolder(home).getName() + "</i> beside its file. "
        + "Opening one shows it in another window:<br>save it under the name of the home there to come back to it.</html>"),
        BorderLayout.NORTH);
    panel.add(new JScrollPane(list), BorderLayout.CENTER);
    panel.add(east, BorderLayout.EAST);
    panel.add(new JScrollPane(comparisonArea), BorderLayout.SOUTH);
    JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), panel, "Versions of the home",
        JOptionPane.PLAIN_MESSAGE);
  }
}
