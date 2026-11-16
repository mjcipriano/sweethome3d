/*
 * AssistantLayerCommands.java 16 Nov 2026
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

import java.util.ArrayList;
import java.util.List;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Selectable;

/**
 * The commands of the design assistant about the tags of furniture and the
 * layers they make (see {@link HomeItemTags}).
 * @author Sweet Home 3D
 */
class AssistantLayerCommands {
  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantLayerCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    if ("add_tags".equals(action) || "add_tag".equals(action) || "tag".equals(action)) {
      changeTags(command, tally, notes, command.getStringList(command.has("tags") ? "tags" : "tag"), null, false);
    } else if ("remove_tags".equals(action) || "remove_tag".equals(action) || "untag".equals(action)) {
      changeTags(command, tally, notes, null, command.getStringList(command.has("tags") ? "tags" : "tag"), false);
    } else if ("set_tags".equals(action)) {
      changeTags(command, tally, notes, command.getStringList("tags"), null, true);
    } else if ("show_layer".equals(action) || "hide_layer".equals(action) || "set_layer_visible".equals(action)) {
      boolean visible = "show_layer".equals(action)
          || "set_layer_visible".equals(action) && command.getBoolean("visible", true);
      setLayerVisible(command, visible, tally, notes);
    } else if ("select_layer".equals(action)) {
      List<HomePieceOfFurniture> furniture = getLayer(command, notes, action);
      if (furniture != null) {
        this.home.setSelectedItems(new ArrayList<Selectable>(furniture));
        notes.add("Selected " + furniture.size() + (furniture.size() == 1 ? " piece" : " pieces"));
      }
    } else if ("list_layers".equals(action) || "list_tags".equals(action)) {
      notes.add(describeLayers(this.home));
    } else {
      return false;
    }
    return true;
  }

  /**
   * Returns the pieces a command designates: the ones with a tag and / or of
   * a type when it has a <code>tag</code> or <code>type</code> filter,
   * otherwise its targets (ids, names, selection).
   */
  private List<HomePieceOfFurniture> getTargets(AssistantCommand command, String filterTagParameter,
                                                List<String> notes, String action) {
    String type = command.getString("type");
    String filterTag = filterTagParameter != null ? command.getString(filterTagParameter) : null;
    if (type != null || filterTag != null) {
      String parsedType = HomeItemTags.parseType(type);
      if (type != null && parsedType == null) {
        notes.add("Ignored " + action + ": unknown type \"" + type + "\" (types: "
            + HomeItemTags.getTypes() + ")");
        return null;
      }
      return HomeItemTags.getFurniture(this.home, filterTag, parsedType);
    }
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture) {
        furniture.add((HomePieceOfFurniture)item);
      }
    }
    return furniture;
  }

  private void changeTags(AssistantCommand command, int [] tally, List<String> notes,
                          List<String> added, List<String> removed, boolean replace) {
    String action = command.getAction();
    // "with_tag" filters the pieces to change, as "tag" is the tag to add or remove
    List<HomePieceOfFurniture> furniture = getTargets(command, "with_tag", notes, action);
    if (furniture == null) {
      return;
    }
    if (furniture.isEmpty()) {
      notes.add("No furniture matches " + action + " (tags apply to furniture, doors and windows)");
      return;
    }
    if (!replace && (added == null || added.isEmpty()) && (removed == null || removed.isEmpty())) {
      notes.add("Ignored " + action + " without \"tags\"");
      return;
    }
    this.executor.beginTurnUpdate();
    int changed = HomeItemTags.changeTags(this.home, this.homeController.getUndoableEditSupport(),
        furniture, added, removed, replace);
    tally [4] += changed;
    notes.add("Tags changed on " + changed + (changed == 1 ? " piece" : " pieces")
        + "; tags of the home: " + HomeItemTags.getAllTags(this.home));
  }

  private List<HomePieceOfFurniture> getLayer(AssistantCommand command, List<String> notes, String action) {
    if (command.getString("tag") == null && command.getString("type") == null) {
      notes.add("Ignored " + action + " without \"tag\" or \"type\"");
      return null;
    }
    return getTargets(command, "tag", notes, action);
  }

  private void setLayerVisible(AssistantCommand command, boolean visible, int [] tally, List<String> notes) {
    String action = command.getAction();
    List<HomePieceOfFurniture> furniture = getLayer(command, notes, action);
    if (furniture == null) {
      return;
    }
    if (furniture.isEmpty()) {
      notes.add("The layer of " + action + " is empty; " + describeLayers(this.home));
      return;
    }
    this.executor.beginTurnUpdate();
    int changed = HomeItemTags.setVisible(this.homeController.getUndoableEditSupport(), furniture, visible);
    tally [4] += changed;
    notes.add((visible ? "Shown " : "Hidden ") + changed + " of the " + furniture.size()
        + (furniture.size() == 1 ? " piece" : " pieces") + " of the layer");
  }

  /**
   * Returns the layers of <code>home</code> with the count of their pieces
   * and of the hidden ones.
   */
  static String describeLayers(Home home) {
    StringBuilder text = new StringBuilder("Layers by tag: ");
    List<String> tags = HomeItemTags.getAllTags(home);
    if (tags.isEmpty()) {
      text.append("none");
    }
    for (int i = 0; i < tags.size(); i++) {
      appendLayer(text, i, tags.get(i), HomeItemTags.getFurniture(home, tags.get(i), null));
    }
    text.append("; by type: ");
    int index = 0;
    for (String type : HomeItemTags.getTypes()) {
      List<HomePieceOfFurniture> furniture = HomeItemTags.getFurniture(home, null, type);
      if (!furniture.isEmpty()) {
        appendLayer(text, index++, type, furniture);
      }
    }
    if (index == 0) {
      text.append("none");
    }
    return text.toString();
  }

  private static void appendLayer(StringBuilder text, int index, String name, List<HomePieceOfFurniture> furniture) {
    int hidden = 0;
    for (HomePieceOfFurniture piece : furniture) {
      if (!piece.isVisible()) {
        hidden++;
      }
    }
    if (index > 0) {
      text.append(", ");
    }
    text.append(name).append(" (").append(furniture.size());
    if (hidden > 0) {
      text.append(", ").append(hidden == furniture.size() ? "hidden" : hidden + " hidden");
    }
    text.append(')');
  }
}
