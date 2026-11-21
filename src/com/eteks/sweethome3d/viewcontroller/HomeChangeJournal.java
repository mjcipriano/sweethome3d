/*
 * HomeChangeJournal.java 16 Nov 2026
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.eteks.sweethome3d.model.CollectionEvent;
import com.eteks.sweethome3d.model.CollectionListener;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * Keeps what changed in a home outside of the calls of a client: the items the
 * user added, deleted or modified by hand since the client last asked.
 * @author Sweet Home 3D
 */
public final class HomeChangeJournal {
  private static final int MAX_ENTRIES = 400;

  private final Home home;
  /** The items changed, with what happened to each: "added", "deleted" or the names of its properties. */
  private final Map<HomeObject, TreeSet<String>> changes = new LinkedHashMap<HomeObject, TreeSet<String>>();
  private final Map<HomeObject, String>          descriptions = new LinkedHashMap<HomeObject, String>();
  private boolean paused;
  private int     overflow;

  private final PropertyChangeListener itemListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent ev) {
        if (ev.getSource() instanceof HomeObject && !HomeItemIds.ID_PROPERTY.equals(ev.getPropertyName())) {
          record((HomeObject)ev.getSource(), ev.getPropertyName().toLowerCase(java.util.Locale.ENGLISH));
        }
      }
    };

  /**
   * Creates a journal that listens to <code>home</code> from now on.
   */
  public HomeChangeJournal(Home home) {
    this.home = home;
    listen(home.getFurniture());
    listen(home.getWalls());
    listen(home.getRooms());
    listen(home.getPolylines());
    listen(home.getDimensionLines());
    listen(home.getLabels());
    listen(home.getLevels());
    home.addFurnitureListener(new ItemsListener<HomePieceOfFurniture>());
    home.addWallsListener(new ItemsListener<Wall>());
    home.addRoomsListener(new ItemsListener<Room>());
    home.addPolylinesListener(new ItemsListener<Polyline>());
    home.addDimensionLinesListener(new ItemsListener<DimensionLine>());
    home.addLabelsListener(new ItemsListener<Label>());
    home.addLevelsListener(new ItemsListener<Level>());
  }

  private void listen(Collection<? extends HomeObject> items) {
    for (HomeObject item : items) {
      item.addPropertyChangeListener(this.itemListener);
    }
  }

  private class ItemsListener<T extends HomeObject> implements CollectionListener<T> {
    public void collectionChanged(CollectionEvent<T> ev) {
      HomeObject item = ev.getItem();
      if (ev.getType() == CollectionEvent.Type.ADD) {
        item.addPropertyChangeListener(itemListener);
        record(item, "added");
      } else if (ev.getType() == CollectionEvent.Type.DELETE) {
        item.removePropertyChangeListener(itemListener);
        record(item, "deleted");
      }
    }
  }

  private void record(HomeObject item, String change) {
    if (this.paused) {
      return;
    }
    TreeSet<String> itemChanges = this.changes.get(item);
    if (itemChanges == null) {
      if (this.changes.size() >= MAX_ENTRIES) {
        this.overflow++;
        return;
      }
      this.changes.put(item, itemChanges = new TreeSet<String>());
    }
    if ("deleted".equals(change) && itemChanges.contains("added")) {
      // Added then deleted: nothing happened
      this.changes.remove(item);
      this.descriptions.remove(item);
      return;
    }
    itemChanges.add(change);
    this.descriptions.put(item, describe(item));
  }

  private static String describe(HomeObject item) {
    String id = HomeItemIds.getId(item);
    String prefix = id != null ? id + " " : "";
    if (item instanceof HomePieceOfFurniture) {
      HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
      return prefix + "\"" + piece.getName() + "\" at (" + Math.round(piece.getX()) + ", " + Math.round(piece.getY()) + ")";
    } else if (item instanceof Wall) {
      Wall wall = (Wall)item;
      return prefix + "wall from (" + Math.round(wall.getXStart()) + ", " + Math.round(wall.getYStart()) + ") to ("
          + Math.round(wall.getXEnd()) + ", " + Math.round(wall.getYEnd()) + ")";
    } else if (item instanceof Room) {
      return prefix + "room" + (((Room)item).getName() != null ? " \"" + ((Room)item).getName() + "\"" : "");
    } else if (item instanceof Level) {
      return prefix + "level \"" + ((Level)item).getName() + "\"";
    } else if (item instanceof Polyline) {
      return prefix + "line";
    } else if (item instanceof Label) {
      return prefix + "label \"" + ((Label)item).getText() + "\"";
    } else if (item instanceof DimensionLine) {
      return prefix + "dimension line";
    }
    return prefix + "item";
  }

  /**
   * Stops or starts again recording: the changes made by the calls of the client aren't recorded.
   */
  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  /**
   * Returns the count of items changed since the journal was last cleared.
   */
  public int getChangeCount() {
    return this.changes.size() + this.overflow;
  }

  /**
   * Returns the changes recorded, and forgets them if <code>clear</code> is <code>true</code>.
   */
  public String describe(boolean clear) {
    if (this.changes.isEmpty()) {
      return "The user changed nothing by hand since you last asked.";
    }
    List<String> added = new ArrayList<String>();
    List<String> deleted = new ArrayList<String>();
    List<String> modified = new ArrayList<String>();
    for (Map.Entry<HomeObject, TreeSet<String>> entry : this.changes.entrySet()) {
      String description = this.descriptions.get(entry.getKey());
      // The id may have been given since the change
      String id = HomeItemIds.getId(entry.getKey());
      if (id != null && !description.startsWith(id + " ")) {
        description = id + " " + description;
      }
      if (entry.getValue().contains("deleted")) {
        deleted.add(description);
      } else if (entry.getValue().contains("added")) {
        added.add(description);
      } else {
        modified.add(description + ": " + entry.getValue().toString().replace("[", "").replace("]", ""));
      }
    }
    StringBuilder text = new StringBuilder("Changed by the user by hand since you last asked ("
        + getChangeCount() + " items):\n");
    append(text, "Added", added);
    append(text, "Deleted", deleted);
    append(text, "Modified", modified);
    if (this.overflow > 0) {
      text.append("... and changes of ").append(this.overflow).append(" more items: call get_home to read the home again.\n");
    }
    text.append("Ids you got before these changes still designate the same items; the ones of deleted items are gone.");
    if (clear) {
      this.changes.clear();
      this.descriptions.clear();
      this.overflow = 0;
    }
    return text.toString();
  }

  private static void append(StringBuilder text, String title, List<String> lines) {
    if (!lines.isEmpty()) {
      text.append(title).append(" (").append(lines.size()).append("):\n");
      for (int i = 0; i < lines.size() && i < 60; i++) {
        text.append("- ").append(lines.get(i)).append('\n');
      }
      if (lines.size() > 60) {
        text.append("- ... and ").append(lines.size() - 60).append(" more\n");
      }
    }
  }

  /**
   * Returns the home this journal listens to.
   */
  public Home getHome() {
    return this.home;
  }
}
