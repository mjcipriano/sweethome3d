/*
 * FurnitureLayersView.java 16 Nov 2026
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
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.table.AbstractTableModel;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeItemTags;

/**
 * The user interface of the tags of furniture and of the layers they make:
 * a <i>Tags</i> menu for the furniture list and the plan, and a window listing
 * the layers by tag and by type of item with a box to show or hide each one.
 * @author Sweet Home 3D
 */
public final class FurnitureLayersView {
  private static final String DIALOG_PROPERTY = "com.eteks.sweethome3d.swing.FurnitureLayersView.dialog";

  private FurnitureLayersView() {
  }

  private static List<HomePieceOfFurniture> getSelectedFurniture(Home home) {
    return Home.getFurnitureSubList(home.getSelectedItems());
  }

  /**
   * Returns the name displayed for a type of furniture.
   */
  static String getTypeName(String type) {
    if (HomeItemTags.TYPE_DOORS_AND_WINDOWS.equals(type)) {
      return "Doors and windows";
    } else if (HomeItemTags.TYPE_LIGHTS.equals(type)) {
      return "Lights";
    } else if (HomeItemTags.TYPE_STAIRS.equals(type)) {
      return "Stairs";
    } else if (HomeItemTags.TYPE_GROUPS.equals(type)) {
      return "Groups";
    } else {
      return "Other furniture";
    }
  }

  /**
   * Returns a menu to tag the selected furniture: a box for each tag of the
   * home, ticked when every selected piece has it, and items to create a tag
   * and to open the window of the layers.
   */
  public static JMenu createTagsMenu(String title, String layersTitle, final JComponent parent,
                                     final Home home, final HomeController controller) {
    final JMenu menu = new JMenu(title);
    final String layersItemTitle = layersTitle;
    menu.addMenuListener(new MenuListener() {
        public void menuSelected(MenuEvent ev) {
          menu.removeAll();
          final List<HomePieceOfFurniture> selection = getSelectedFurniture(home);
          for (final String tag : HomeItemTags.getAllTags(home)) {
            int tagged = 0;
            for (HomePieceOfFurniture piece : selection) {
              if (HomeItemTags.hasTag(piece, tag)) {
                tagged++;
              }
            }
            final boolean allTagged = !selection.isEmpty() && tagged == selection.size();
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(
                tag + (tagged > 0 && !allTagged ? "  (" + tagged + " of " + selection.size() + ")" : ""), allTagged);
            item.setEnabled(!selection.isEmpty());
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                  controller.changeFurnitureTags(selection,
                      allTagged ? null : Collections.singletonList(tag),
                      allTagged ? Collections.singletonList(tag) : null, false);
                }
              });
            menu.add(item);
          }
          if (menu.getMenuComponentCount() > 0) {
            menu.addSeparator();
          }
          JMenuItem newTagItem = new JMenuItem("New tag...");
          newTagItem.setEnabled(!selection.isEmpty());
          newTagItem.addActionListener(new ActionListener() {
              public void actionPerformed(ActionEvent ev) {
                List<String> tags = askTags(parent, "Tags to add to the " + selection.size()
                    + (selection.size() == 1 ? " selected piece" : " selected pieces")
                    + " (separate several tags with commas):", "");
                if (!tags.isEmpty()) {
                  controller.changeFurnitureTags(selection, tags, null, false);
                }
              }
            });
          menu.add(newTagItem);
          JMenuItem removeItem = new JMenuItem("Remove all tags");
          boolean tagged = false;
          for (HomePieceOfFurniture piece : selection) {
            tagged |= !HomeItemTags.getTags(piece).isEmpty();
          }
          removeItem.setEnabled(tagged);
          removeItem.addActionListener(new ActionListener() {
              public void actionPerformed(ActionEvent ev) {
                controller.changeFurnitureTags(selection, null, null, true);
              }
            });
          menu.add(removeItem);
          menu.addSeparator();
          JMenuItem layersItem = new JMenuItem(layersItemTitle);
          layersItem.addActionListener(new ActionListener() {
              public void actionPerformed(ActionEvent ev) {
                showLayersDialog(parent, home, controller);
              }
            });
          menu.add(layersItem);
        }

        public void menuDeselected(MenuEvent ev) {
        }

        public void menuCanceled(MenuEvent ev) {
        }
      });
    return menu;
  }

  private static List<String> askTags(Component parent, String message, String initialValue) {
    Object answer = JOptionPane.showInputDialog(SwingUtilities.getRootPane(parent), message, "Tags",
        JOptionPane.QUESTION_MESSAGE, null, null, initialValue);
    return HomeItemTags.parseTags(answer != null ? answer.toString() : null);
  }

  /**
   * A layer listed in the window: the pieces with a tag, or of a type.
   */
  private static final class Layer {
    private final String tag;
    private final String type;
    private final int    count;
    private final int    hiddenCount;

    Layer(Home home, String tag, String type) {
      this.tag = tag;
      this.type = type;
      List<HomePieceOfFurniture> furniture = HomeItemTags.getFurniture(home, tag, type);
      int hidden = 0;
      for (HomePieceOfFurniture piece : furniture) {
        if (!piece.isVisible()) {
          hidden++;
        }
      }
      this.count = furniture.size();
      this.hiddenCount = hidden;
    }

    String getName() {
      return this.tag != null
          ? (HomeItemTags.UNTAGGED.equals(this.tag) ? "(without tag)" : this.tag)
          : getTypeName(this.type);
    }

    boolean isUserTag() {
      return this.tag != null && !HomeItemTags.UNTAGGED.equals(this.tag);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Layer)) {
        return false;
      }
      Layer layer = (Layer)obj;
      return (this.tag == null ? layer.tag == null : this.tag.equals(layer.tag))
          && (this.type == null ? layer.type == null : this.type.equals(layer.type))
          && this.count == layer.count && this.hiddenCount == layer.hiddenCount;
    }

    @Override
    public int hashCode() {
      return (this.tag != null ? this.tag.hashCode() : 0) + (this.type != null ? this.type.hashCode() : 0);
    }
  }

  /**
   * Returns the layers of a home: its tags, the pieces without tag, then the types in use.
   */
  static List<Layer> getLayers(Home home) {
    List<Layer> layers = new ArrayList<Layer>();
    List<String> tags = HomeItemTags.getAllTags(home);
    for (String tag : tags) {
      layers.add(new Layer(home, tag, null));
    }
    if (!tags.isEmpty()) {
      Layer untagged = new Layer(home, HomeItemTags.UNTAGGED, null);
      if (untagged.count > 0) {
        layers.add(untagged);
      }
    }
    for (String type : HomeItemTags.getTypes()) {
      Layer layer = new Layer(home, null, type);
      if (layer.count > 0) {
        layers.add(layer);
      }
    }
    return layers;
  }

  private static final class LayersTableModel extends AbstractTableModel {
    private final Home                home;
    private final HomeController controller;
    private List<Layer>          layers;

    LayersTableModel(Home home, HomeController controller) {
      this.home = home;
      this.controller = controller;
      this.layers = getLayers(home);
    }

    /**
     * Reads the layers again and returns <code>true</code> if they changed.
     */
    boolean update() {
      List<Layer> layers = getLayers(this.home);
      if (layers.equals(this.layers)) {
        return false;
      }
      boolean sameRows = layers.size() == this.layers.size();
      for (int i = 0; sameRows && i < layers.size(); i++) {
        sameRows = layers.get(i).getName().equals(this.layers.get(i).getName());
      }
      this.layers = layers;
      if (sameRows) {
        // Keep the selected rows
        fireTableRowsUpdated(0, layers.size() - 1);
      } else {
        fireTableDataChanged();
      }
      return true;
    }

    Layer getLayer(int row) {
      return this.layers.get(row);
    }

    public int getRowCount() {
      return this.layers.size();
    }

    public int getColumnCount() {
      return 4;
    }

    @Override
    public String getColumnName(int column) {
      return new String [] {"Visible", "Layer", "Kind", "Items"} [column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return column == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == 0;
    }

    public Object getValueAt(int row, int column) {
      Layer layer = this.layers.get(row);
      switch (column) {
        case 0 :
          return Boolean.valueOf(layer.hiddenCount < layer.count);
        case 1 :
          return layer.getName();
        case 2 :
          return layer.tag != null ? "Tag" : "Type";
        default :
          return layer.count + (layer.hiddenCount > 0 && layer.hiddenCount < layer.count
              ? "  (" + layer.hiddenCount + " hidden)" : "");
      }
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (column == 0) {
        Layer layer = this.layers.get(row);
        // A layer partly hidden is shown first
        boolean visible = layer.hiddenCount > 0;
        this.controller.setFurnitureVisible(
            HomeItemTags.getFurniture(this.home, layer.tag, layer.type), visible);
        update();
      }
    }
  }

  /**
   * Shows the window of the layers of a home, where each layer can be shown,
   * hidden and selected, and the tags managed.
   */
  public static void showLayersDialog(JComponent parent, final Home home, final HomeController controller) {
    final JComponent rootPane = SwingUtilities.getRootPane(parent);
    if (rootPane != null && rootPane.getClientProperty(DIALOG_PROPERTY) instanceof JDialog) {
      JDialog dialog = (JDialog)rootPane.getClientProperty(DIALOG_PROPERTY);
      dialog.setVisible(true);
      dialog.toFront();
      return;
    }
    Window window = SwingUtilities.getWindowAncestor(parent);
    final JDialog dialog = new JDialog(window, "Layers and tags");
    final LayersTableModel model = new LayersTableModel(home, controller);
    final JTable table = new JTable(model);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.getColumnModel().getColumn(0).setMaxWidth(60);
    table.getColumnModel().getColumn(1).setPreferredWidth(180);
    table.getColumnModel().getColumn(2).setMaxWidth(60);
    table.setFillsViewportHeight(true);

    final JButton selectButton = new JButton("Select items");
    final JButton addButton = new JButton("Add selected furniture");
    final JButton removeButton = new JButton("Remove selected furniture");
    final JButton newTagButton = new JButton("New tag...");
    final JButton renameButton = new JButton("Rename tag...");
    final JButton deleteButton = new JButton("Delete tag");
    final JButton showAllButton = new JButton("Show everything");
    selectButton.setToolTipText("Selects the furniture of the chosen layers in the plan and the furniture list");
    addButton.setToolTipText("Gives the chosen tag to the furniture selected in the plan or the furniture list");
    removeButton.setToolTipText("Removes the chosen tag from the selected furniture");
    newTagButton.setToolTipText("Tags the selected furniture with a new tag");
    deleteButton.setToolTipText("Removes the chosen tag from every piece; the furniture itself is kept");

    final Runnable buttonsUpdater = new Runnable() {
        public void run() {
          int [] rows = table.getSelectedRows();
          boolean oneTag = rows.length == 1 && model.getLayer(rows [0]).isUserTag();
          boolean furnitureSelected = !getSelectedFurniture(home).isEmpty();
          selectButton.setEnabled(rows.length > 0);
          addButton.setEnabled(oneTag && furnitureSelected);
          removeButton.setEnabled(oneTag && furnitureSelected);
          newTagButton.setEnabled(furnitureSelected);
          renameButton.setEnabled(oneTag);
          deleteButton.setEnabled(oneTag);
        }
      };
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent ev) {
          buttonsUpdater.run();
        }
      });

    selectButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          List<Selectable> items = new ArrayList<Selectable>();
          for (int row : table.getSelectedRows()) {
            Layer layer = model.getLayer(row);
            for (HomePieceOfFurniture piece : HomeItemTags.getFurniture(home, layer.tag, layer.type)) {
              if (!items.contains(piece)) {
                items.add(piece);
              }
            }
          }
          home.setSelectedItems(items);
        }
      });
    ActionListener tagSelectionListener = new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          List<String> tag = Collections.singletonList(model.getLayer(table.getSelectedRow()).tag);
          boolean add = ev.getSource() == addButton;
          controller.changeFurnitureTags(getSelectedFurniture(home),
              add ? tag : null, add ? null : tag, false);
          model.update();
        }
      };
    addButton.addActionListener(tagSelectionListener);
    removeButton.addActionListener(tagSelectionListener);
    newTagButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          List<HomePieceOfFurniture> selection = getSelectedFurniture(home);
          List<String> tags = askTags(dialog, "Tags to add to the " + selection.size()
              + (selection.size() == 1 ? " selected piece" : " selected pieces")
              + " (separate several tags with commas):", "");
          if (!tags.isEmpty()) {
            controller.changeFurnitureTags(selection, tags, null, false);
            model.update();
          }
        }
      });
    renameButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          String tag = model.getLayer(table.getSelectedRow()).tag;
          List<String> tags = askTags(dialog, "New name of the tag \"" + tag + "\":", tag);
          if (tags.size() == 1 && !tags.get(0).equals(tag)) {
            controller.renameFurnitureTag(tag, tags.get(0));
            model.update();
          }
        }
      });
    deleteButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          String tag = model.getLayer(table.getSelectedRow()).tag;
          controller.changeFurnitureTags(HomeItemTags.getFurniture(home, tag, null),
              null, Collections.singletonList(tag), false);
          model.update();
        }
      });
    showAllButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          controller.setFurnitureVisible(home.getFurniture(), true);
          model.update();
        }
      });

    JPanel buttonsPanel = new JPanel(new GridLayout(0, 1, 0, 4));
    buttonsPanel.add(selectButton);
    buttonsPanel.add(showAllButton);
    buttonsPanel.add(new JLabel(" "));
    buttonsPanel.add(newTagButton);
    buttonsPanel.add(addButton);
    buttonsPanel.add(removeButton);
    buttonsPanel.add(renameButton);
    buttonsPanel.add(deleteButton);
    JPanel eastPanel = new JPanel(new BorderLayout());
    eastPanel.add(buttonsPanel, BorderLayout.NORTH);
    JLabel helpLabel = new JLabel("<html>Untick a layer to hide its furniture in the plan and the 3D view.<br>"
        + "Tag furniture from here or with a right click in the furniture list (Tags);<br>"
        + "a piece may have several tags, shown in the Tags column of the list.</html>");
    helpLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    JPanel panel = new JPanel(new BorderLayout(8, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    panel.add(helpLabel, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    panel.add(eastPanel, BorderLayout.EAST);
    dialog.setContentPane(panel);
    table.setPreferredScrollableViewportSize(new java.awt.Dimension(400, 260));
    table.getColumnModel().getColumn(3).setPreferredWidth(110);
    dialog.pack();
    dialog.setLocationRelativeTo(window);

    // The home changes from many places (plan, undo, assistant): look at it regularly
    final Timer timer = new Timer(600, new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          model.update();
          buttonsUpdater.run();
        }
      });
    dialog.addWindowListener(new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent ev) {
          timer.start();
        }

        @Override
        public void windowClosed(WindowEvent ev) {
          timer.stop();
          if (rootPane != null) {
            rootPane.putClientProperty(DIALOG_PROPERTY, null);
          }
        }
      });
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    if (rootPane != null) {
      rootPane.putClientProperty(DIALOG_PROPERTY, dialog);
    }
    buttonsUpdater.run();
    dialog.setVisible(true);
  }
}
