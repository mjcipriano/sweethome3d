/*
 * HomeItemTags.java 16 Nov 2026
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEditSupport;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ObjectProperty;

/**
 * The tags of the furniture of a home and the layers they make. A piece may
 * have any number of tags, stored in its property <code>tags</code> as a text
 * separated by commas, which the furniture list displays in a column of its
 * own. A layer is the set of the pieces that have a given tag, or that are of
 * a given type (doors and windows, lights, stairs, groups, other furniture):
 * showing or hiding a layer changes the visibility of its pieces.
 * @author Sweet Home 3D
 */
public final class HomeItemTags {
  /** The name of the property of a piece that stores its tags. */
  public static final String TAGS_PROPERTY = "tags";
  /** The tag designating the pieces that have no tag, in a search. */
  public static final String UNTAGGED = "untagged";

  public static final String TYPE_FURNITURE = "furniture";
  public static final String TYPE_DOORS_AND_WINDOWS = "doors_and_windows";
  public static final String TYPE_LIGHTS = "lights";
  public static final String TYPE_STAIRS = "stairs";
  public static final String TYPE_GROUPS = "groups";

  private static final String [] TYPES = {TYPE_FURNITURE, TYPE_DOORS_AND_WINDOWS, TYPE_LIGHTS,
                                          TYPE_STAIRS, TYPE_GROUPS};
  private static final int MAX_TAG_LENGTH = 40;
  private static final int MAX_TAGS_PER_PIECE = 20;

  private HomeItemTags() {
  }

  /**
   * Returns the types of furniture that make a layer.
   */
  public static List<String> getTypes() {
    List<String> types = new ArrayList<String>();
    Collections.addAll(types, TYPES);
    return types;
  }

  /**
   * Returns the type of <code>piece</code>, one of the <code>TYPE_...</code> constants.
   */
  public static String getType(HomePieceOfFurniture piece) {
    if (piece instanceof HomeFurnitureGroup) {
      return TYPE_GROUPS;
    } else if (piece instanceof HomeDoorOrWindow || piece.isDoorOrWindow()) {
      return TYPE_DOORS_AND_WINDOWS;
    } else if (piece instanceof HomeLight) {
      return TYPE_LIGHTS;
    } else if (piece.getStaircaseCutOutShape() != null
               || "true".equals(piece.getProperty(AssistantObjectCommands.STAIRS_PROPERTY))) {
      return TYPE_STAIRS;
    } else {
      return TYPE_FURNITURE;
    }
  }

  /**
   * Returns the type a text designates ("doors", "Lights", "doors and windows"...),
   * or <code>null</code> if it's none of them.
   */
  public static String parseType(String text) {
    if (text == null) {
      return null;
    }
    String type = text.trim().toLowerCase(java.util.Locale.ENGLISH).replace(' ', '_').replace('-', '_');
    if (type.equals("doors") || type.equals("windows") || type.equals("door") || type.equals("window")
        || type.equals("doors_windows") || type.equals("door_or_window") || type.equals("doors_or_windows")) {
      return TYPE_DOORS_AND_WINDOWS;
    } else if (type.equals("light") || type.equals("lamps") || type.equals("lamp")) {
      return TYPE_LIGHTS;
    } else if (type.equals("stair") || type.equals("staircase") || type.equals("staircases")) {
      return TYPE_STAIRS;
    } else if (type.equals("group")) {
      return TYPE_GROUPS;
    } else if (type.equals("other") || type.equals("other_furniture")) {
      return TYPE_FURNITURE;
    }
    for (String knownType : TYPES) {
      if (knownType.equals(type)) {
        return knownType;
      }
    }
    return null;
  }

  /**
   * Returns the tags written in <code>text</code>, separated by commas or
   * semicolons, without empty tags or duplicates.
   */
  public static List<String> parseTags(String text) {
    List<String> tags = new ArrayList<String>();
    if (text != null) {
      for (String part : text.split("[,;\\n]")) {
        String tag = cleanTag(part);
        if (tag != null && indexOf(tags, tag) < 0 && tags.size() < MAX_TAGS_PER_PIECE) {
          tags.add(tag);
        }
      }
    }
    return tags;
  }

  /**
   * Returns <code>tag</code> without the characters a tag can't contain, or
   * <code>null</code> if nothing is left.
   */
  public static String cleanTag(String tag) {
    if (tag == null) {
      return null;
    }
    StringBuilder clean = new StringBuilder();
    for (int i = 0; i < tag.length(); i++) {
      char c = tag.charAt(i);
      if (c != ',' && c != ';' && !Character.isISOControl(c)) {
        clean.append(c);
      }
    }
    String text = clean.toString().trim().replaceAll("\\s+", " ");
    if (text.length() > MAX_TAG_LENGTH) {
      text = text.substring(0, MAX_TAG_LENGTH).trim();
    }
    return text.length() > 0 ? text : null;
  }

  /**
   * Returns the text stored for the given tags, or <code>null</code> if there's none.
   */
  public static String formatTags(Collection<String> tags) {
    StringBuilder text = new StringBuilder();
    for (String tag : tags) {
      if (text.length() > 0) {
        text.append(", ");
      }
      text.append(tag);
    }
    return text.length() > 0 ? text.toString() : null;
  }

  /**
   * Returns the tags of <code>piece</code>.
   */
  public static List<String> getTags(HomePieceOfFurniture piece) {
    return parseTags(piece.getProperty(TAGS_PROPERTY));
  }

  /**
   * Returns <code>true</code> if <code>piece</code> has the given tag, whatever its case.
   */
  public static boolean hasTag(HomePieceOfFurniture piece, String tag) {
    return indexOf(getTags(piece), tag) >= 0;
  }

  /**
   * Returns the tags used in <code>home</code>, sorted.
   */
  public static List<String> getAllTags(Home home) {
    List<String> tags = new ArrayList<String>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      for (String tag : getTags(piece)) {
        if (indexOf(tags, tag) < 0) {
          tags.add(tag);
        }
      }
    }
    Collections.sort(tags, String.CASE_INSENSITIVE_ORDER);
    return tags;
  }

  /**
   * Returns the pieces of <code>home</code> that have the given tag and are
   * of the given type. A <code>null</code> tag or type isn't checked, and the
   * tag {@link #UNTAGGED} designates the pieces without any tag.
   */
  public static List<HomePieceOfFurniture> getFurniture(Home home, String tag, String type) {
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      if (type != null && !type.equals(getType(piece))) {
        continue;
      }
      if (tag != null) {
        List<String> tags = getTags(piece);
        if (indexOf(tags, tag) < 0 && !(tags.isEmpty() && UNTAGGED.equalsIgnoreCase(tag))) {
          continue;
        }
      }
      furniture.add(piece);
    }
    return furniture;
  }

  /**
   * Adds tags to the given pieces and removes others, in one undoable
   * operation. A tag already used in the home with another case keeps the
   * way it's written.
   * @param replace if <code>true</code>, the tags of each piece are replaced by <code>addedTags</code>
   * @return the count of pieces whose tags changed
   */
  public static int changeTags(Home home, UndoableEditSupport undoSupport,
                               List<HomePieceOfFurniture> furniture,
                               Collection<String> addedTags, Collection<String> removedTags,
                               boolean replace) {
    List<String> homeTags = getAllTags(home);
    List<String> added = new ArrayList<String>();
    if (addedTags != null) {
      for (String tag : addedTags) {
        String clean = cleanTag(tag);
        if (clean != null) {
          int index = indexOf(homeTags, clean);
          clean = index >= 0 ? homeTags.get(index) : clean;
          if (indexOf(added, clean) < 0) {
            added.add(clean);
          }
        }
      }
    }
    List<HomePieceOfFurniture> modified = new ArrayList<HomePieceOfFurniture>();
    List<String> oldValues = new ArrayList<String>();
    List<String> newValues = new ArrayList<String>();
    for (HomePieceOfFurniture piece : furniture) {
      List<String> tags = replace ? new ArrayList<String>() : getTags(piece);
      if (removedTags != null) {
        for (String tag : removedTags) {
          int index = indexOf(tags, cleanTag(tag));
          if (index >= 0) {
            tags.remove(index);
          }
        }
      }
      for (String tag : added) {
        if (indexOf(tags, tag) < 0 && tags.size() < MAX_TAGS_PER_PIECE) {
          tags.add(tag);
        }
      }
      String oldValue = piece.getProperty(TAGS_PROPERTY);
      String newValue = formatTags(tags);
      if (newValue == null ? oldValue != null : !newValue.equals(oldValue)) {
        modified.add(piece);
        oldValues.add(oldValue);
        newValues.add(newValue);
        piece.setProperty(TAGS_PROPERTY, newValue);
      }
    }
    if (!modified.isEmpty()) {
      if (!added.isEmpty()) {
        showTagsColumn(home);
      }
      if (undoSupport != null) {
        undoSupport.postEdit(new TagsUndoableEdit(modified, oldValues, newValues));
      }
    }
    return modified.size();
  }

  /**
   * Renames a tag on every piece that has it, in one undoable operation.
   * @return the count of pieces whose tags changed
   */
  public static int renameTag(Home home, UndoableEditSupport undoSupport, String tag, String newTag) {
    newTag = cleanTag(newTag);
    if (newTag == null) {
      return 0;
    }
    List<HomePieceOfFurniture> modified = new ArrayList<HomePieceOfFurniture>();
    List<String> oldValues = new ArrayList<String>();
    List<String> newValues = new ArrayList<String>();
    for (HomePieceOfFurniture piece : home.getFurniture()) {
      List<String> tags = getTags(piece);
      int index = indexOf(tags, tag);
      if (index >= 0) {
        tags.remove(index);
        if (indexOf(tags, newTag) < 0) {
          tags.add(index, newTag);
        }
        String oldValue = piece.getProperty(TAGS_PROPERTY);
        String newValue = formatTags(tags);
        if (!newValue.equals(oldValue)) {
          modified.add(piece);
          oldValues.add(oldValue);
          newValues.add(newValue);
          piece.setProperty(TAGS_PROPERTY, newValue);
        }
      }
    }
    if (!modified.isEmpty() && undoSupport != null) {
      undoSupport.postEdit(new TagsUndoableEdit(modified, oldValues, newValues));
    }
    return modified.size();
  }

  /**
   * Shows or hides the given pieces in one undoable operation.
   * @return the count of pieces whose visibility changed
   */
  public static int setVisible(UndoableEditSupport undoSupport,
                               List<HomePieceOfFurniture> furniture, boolean visible) {
    List<HomePieceOfFurniture> modified = new ArrayList<HomePieceOfFurniture>();
    for (HomePieceOfFurniture piece : furniture) {
      if (piece.isVisible() != visible) {
        modified.add(piece);
        piece.setVisible(visible);
      }
    }
    if (!modified.isEmpty() && undoSupport != null) {
      undoSupport.postEdit(new VisibilityUndoableEdit(modified, visible));
    }
    return modified.size();
  }

  /**
   * Returns the description of the property of the tags, which lets the
   * furniture list display, sort and export them, and the furniture
   * modification pane edit them.
   */
  public static ObjectProperty createTagsProperty() {
    return new ObjectProperty(TAGS_PROPERTY, "Tags", ObjectProperty.Type.STRING, true, true, true);
  }

  /**
   * Adds the column of the tags to the furniture list of <code>home</code>,
   * if it isn't displayed yet.
   */
  public static void showTagsColumn(Home home) {
    ObjectProperty tagsProperty = createTagsProperty();
    List<ObjectProperty> properties = new ArrayList<ObjectProperty>(home.getFurnitureAdditionalProperties());
    if (!properties.contains(tagsProperty)) {
      properties.add(tagsProperty);
      home.setFurnitureAdditionalProperties(properties);
    }
    List<String> visibleProperties = new ArrayList<String>(home.getFurnitureVisiblePropertyNames());
    if (!visibleProperties.contains(TAGS_PROPERTY)) {
      visibleProperties.add(TAGS_PROPERTY);
      home.setFurnitureVisiblePropertyNames(visibleProperties);
    }
  }

  private static int indexOf(List<String> tags, String tag) {
    if (tag != null) {
      for (int i = 0; i < tags.size(); i++) {
        if (tags.get(i).equalsIgnoreCase(tag.trim())) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Undoable edit of the tags of some pieces.
   */
  private static final class TagsUndoableEdit extends AbstractUndoableEdit {
    private final List<HomePieceOfFurniture> furniture;
    private final List<String>               oldValues;
    private final List<String>               newValues;

    TagsUndoableEdit(List<HomePieceOfFurniture> furniture, List<String> oldValues, List<String> newValues) {
      this.furniture = furniture;
      this.oldValues = oldValues;
      this.newValues = newValues;
    }

    @Override
    public void undo() {
      super.undo();
      for (int i = 0; i < this.furniture.size(); i++) {
        this.furniture.get(i).setProperty(TAGS_PROPERTY, this.oldValues.get(i));
      }
    }

    @Override
    public void redo() {
      super.redo();
      for (int i = 0; i < this.furniture.size(); i++) {
        this.furniture.get(i).setProperty(TAGS_PROPERTY, this.newValues.get(i));
      }
    }

    @Override
    public String getPresentationName() {
      return "Modify tags";
    }
  }

  /**
   * Undoable edit of the visibility of the pieces of a layer.
   */
  private static final class VisibilityUndoableEdit extends AbstractUndoableEdit {
    private final List<HomePieceOfFurniture> furniture;
    private final boolean                    visible;

    VisibilityUndoableEdit(List<HomePieceOfFurniture> furniture, boolean visible) {
      this.furniture = furniture;
      this.visible = visible;
    }

    @Override
    public void undo() {
      super.undo();
      for (HomePieceOfFurniture piece : this.furniture) {
        piece.setVisible(!this.visible);
      }
    }

    @Override
    public void redo() {
      super.redo();
      for (HomePieceOfFurniture piece : this.furniture) {
        piece.setVisible(this.visible);
      }
    }

    @Override
    public String getPresentationName() {
      return this.visible ? "Show layer" : "Hide layer";
    }
  }
}
