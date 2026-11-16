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
   * Shows the quantities of the home: areas, walls, furniture and prices.
   */
  public static void showReportDialog(final JComponent parent, Home home) {
    final String report = HomeReport.describe(home);
    final JTextArea textArea = new JTextArea(report, 24, 64);
    textArea.setEditable(false);
    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textArea.getFont().getSize()));
    textArea.setCaretPosition(0);
    JButton copyButton = new JButton("Copy");
    copyButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
        }
      });
    JButton saveButton = new JButton("Save as text file...");
    saveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          JFileChooser chooser = new JFileChooser();
          chooser.setSelectedFile(new File("home-report.txt"));
          if (chooser.showSaveDialog(SwingUtilities.getRootPane(parent)) == JFileChooser.APPROVE_OPTION) {
            try {
              Writer writer = new OutputStreamWriter(new FileOutputStream(chooser.getSelectedFile()), "UTF-8");
              try {
                writer.write(report);
              } finally {
                writer.close();
              }
            } catch (IOException ex) {
              JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent),
                  "The file couldn't be written: " + ex.getMessage());
            }
          }
        }
      });
    JPanel buttons = new JPanel();
    buttons.add(copyButton);
    buttons.add(saveButton);
    JPanel panel = new JPanel(new BorderLayout(0, 6));
    panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
    panel.add(buttons, BorderLayout.SOUTH);
    JOptionPane.showMessageDialog(SwingUtilities.getRootPane(parent), panel, "Home report",
        JOptionPane.PLAIN_MESSAGE);
  }
}
