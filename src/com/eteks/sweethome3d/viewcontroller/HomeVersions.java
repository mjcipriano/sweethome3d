/*
 * HomeVersions.java 16 Nov 2026
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeRecorder;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

/**
 * Named versions of a home: copies of the home saved in a folder beside its
 * file (<code>My home.sh3d.versions</code>), to come back to an earlier state
 * of a design or to compare two of them.
 * @author Sweet Home 3D
 */
public final class HomeVersions {
  private static final String FOLDER_SUFFIX = ".versions";
  private static final String EXTENSION = ".sh3d";
  private static final String DATE_FORMAT = "yyyyMMdd-HHmmss";

  private HomeVersions() {
  }

  /**
   * Returns the folder of the versions of <code>home</code>, or
   * <code>null</code> if the home was never saved.
   */
  public static File getFolder(Home home) {
    return home.getName() != null ? new File(home.getName() + FOLDER_SUFFIX) : null;
  }

  /**
   * Returns the versions of <code>home</code>, the most recent first.
   */
  public static List<File> getVersions(Home home) {
    List<File> versions = new ArrayList<File>();
    File folder = getFolder(home);
    File [] files = folder != null ? folder.listFiles() : null;
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().toLowerCase(Locale.ENGLISH).endsWith(EXTENSION)) {
          versions.add(file);
        }
      }
    }
    Collections.sort(versions, Collections.reverseOrder());
    return versions;
  }

  /** The longest label kept in the name of the file of a version. */
  static final int MAX_LABEL_LENGTH = 150;
  private static final int MIN_LABEL_LENGTH = 40;
  private static final int MAX_PATH_LENGTH = 250;

  /**
   * Returns <code>label</code> as the name of a file can hold it under any system: its letters, its digits
   * and <code>_ . , ( ) - + = ' ! @ # % & [ ]</code> kept, the other characters replaced by a space, without
   * space nor dot at its end.
   */
  public static String cleanLabel(String label) {
    if (label == null) {
      return "";
    }
    String cleanLabel = label.replaceAll("[^\\p{L}\\p{N} _.,()+=\\[\\]'!@#%&-]", " ").replaceAll("\\s+", " ").trim();
    // Windows drops the dots and the spaces at the end of a name
    while (cleanLabel.endsWith(".") || cleanLabel.endsWith(" ")) {
      cleanLabel = cleanLabel.substring(0, cleanLabel.length() - 1);
    }
    return cleanLabel;
  }

  /**
   * Returns the label of a version without its date, from the name of its file.
   */
  public static String getLabelText(File version) {
    String name = version.getName();
    if (name.endsWith(EXTENSION)) {
      name = name.substring(0, name.length() - EXTENSION.length());
    }
    return name.length() > DATE_FORMAT.length() ? name.substring(DATE_FORMAT.length()).trim() : "";
  }

  /**
   * Returns the label and the date of a version, from the name of its file.
   */
  public static String getLabel(File version) {
    String name = version.getName().substring(0, version.getName().length() - EXTENSION.length());
    if (name.length() >= DATE_FORMAT.length()) {
      try {
        Date date = new SimpleDateFormat(DATE_FORMAT).parse(name.substring(0, DATE_FORMAT.length()));
        String label = name.substring(DATE_FORMAT.length()).trim();
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date) + (label.length() > 0 ? "  " + label : "");
      } catch (java.text.ParseException ex) {
        // Not a name given by this class
      }
    }
    return name;
  }

  /**
   * Saves a copy of <code>home</code> as a new version and returns its file.
   * The home itself, its name and its modified state don't change.
   */
  public static File saveVersion(Home home, HomeRecorder recorder, String label) throws RecorderException {
    File folder = getFolder(home);
    if (folder == null) {
      throw new RecorderException("Save the home in a file first");
    }
    if (!folder.isDirectory() && !folder.mkdirs()) {
      throw new RecorderException("Can't create the folder " + folder);
    }
    String cleanLabel = cleanLabel(label);
    // As long as the name of a file may be, with its folder, where paths are the shortest (260 characters under Windows)
    int maxLength = Math.max(MIN_LABEL_LENGTH, Math.min(MAX_LABEL_LENGTH,
        MAX_PATH_LENGTH - folder.getAbsolutePath().length() - 1 - DATE_FORMAT.length() - 1 - EXTENSION.length() - 6));
    if (cleanLabel.length() > maxLength) {
      cleanLabel = cleanLabel(cleanLabel.substring(0, maxLength));
    }
    String date = new SimpleDateFormat(DATE_FORMAT).format(new Date());
    File file = new File(folder, date + (cleanLabel.length() > 0 ? " " + cleanLabel : "") + EXTENSION);
    for (int i = 2; file.exists(); i++) {
      file = new File(folder, date + (cleanLabel.length() > 0 ? " " + cleanLabel : "") + " (" + i + ")" + EXTENSION);
    }
    recorder.writeHome(home.clone(), file.getPath());
    return file;
  }

  private static void collect(List<HomePieceOfFurniture> furniture, Map<String, int []> counts, int index) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collect(((HomeFurnitureGroup)piece).getFurniture(), counts, index);
      } else {
        String name = piece.getName() != null ? piece.getName() : "?";
        int [] count = counts.get(name);
        if (count == null) {
          counts.put(name, count = new int [2]);
        }
        count [index]++;
      }
    }
  }

  private static double [] measure(Home home) {
    double floor = 0;
    for (Room room : home.getRooms()) {
      floor += room.getArea() / 10000.;
    }
    double wallLength = 0;
    for (Wall wall : home.getWalls()) {
      wallLength += wall.getLength() / 100.;
    }
    return new double [] {home.getLevels().size(), home.getRooms().size(), floor, home.getWalls().size(), wallLength};
  }

  /**
   * Returns what changed from <code>oldHome</code> to <code>newHome</code>:
   * levels, rooms, floor area, walls and the furniture added and removed.
   */
  public static String compare(Home oldHome, Home newHome) {
    StringBuilder text = new StringBuilder();
    double [] oldMeasures = measure(oldHome);
    double [] newMeasures = measure(newHome);
    String [] names = {"Levels", "Rooms", "Floor area (m2)", "Walls", "Wall length (m)"};
    for (int i = 0; i < names.length; i++) {
      boolean count = i != 2 && i != 4;
      String format = count ? "%.0f" : "%.1f";
      text.append(names [i]).append(": ").append(String.format(Locale.US, format, oldMeasures [i]));
      if (Math.abs(oldMeasures [i] - newMeasures [i]) >= (count ? 0.5 : 0.05)) {
        text.append(" -> ").append(String.format(Locale.US, format, newMeasures [i]))
            .append(String.format(Locale.US, "  (" + (count ? "%+.0f" : "%+.1f") + ")", newMeasures [i] - oldMeasures [i]));
      } else {
        text.append("  (same)");
      }
      text.append('\n');
    }
    Map<String, int []> counts = new TreeMap<String, int []>(String.CASE_INSENSITIVE_ORDER);
    collect(oldHome.getFurniture(), counts, 0);
    collect(newHome.getFurniture(), counts, 1);
    List<String> added = new ArrayList<String>();
    List<String> removed = new ArrayList<String>();
    int oldTotal = 0;
    int newTotal = 0;
    for (Map.Entry<String, int []> entry : counts.entrySet()) {
      int [] count = entry.getValue();
      oldTotal += count [0];
      newTotal += count [1];
      if (count [1] > count [0]) {
        added.add((count [1] - count [0]) + " x " + entry.getKey());
      } else if (count [0] > count [1]) {
        removed.add((count [0] - count [1]) + " x " + entry.getKey());
      }
    }
    text.append("Furniture, doors and windows: ").append(oldTotal);
    text.append(oldTotal != newTotal ? " -> " + newTotal : "  (same count)").append('\n');
    if (!added.isEmpty()) {
      text.append("Added: ").append(join(added)).append('\n');
    }
    if (!removed.isEmpty()) {
      text.append("Removed: ").append(join(removed)).append('\n');
    }
    if (added.isEmpty() && removed.isEmpty() && Arrays.equals(oldMeasures, newMeasures)) {
      text.append("Same rooms, walls and furniture: only places, sizes or colors may differ.\n");
    }
    return text.toString().trim();
  }

  /**
   * Returns the version of <code>home</code> designated by <code>token</code>: its number in the
   * list of versions (1 = the most recent), the name of its file, or a part of its label.
   * @return the version, or <code>null</code> if none or several match
   */
  public static File find(Home home, String token) {
    List<File> versions = getVersions(home);
    if (token == null || versions.isEmpty()) {
      return null;
    }
    String text = token.trim();
    try {
      int index = Integer.parseInt(text);
      return index >= 1 && index <= versions.size() ? versions.get(index - 1) : null;
    } catch (NumberFormatException ex) {
      // A name
    }
    File match = null;
    // The label as the name of a file holds it, and as the one of a version too long was shortened
    String cleanText = cleanLabel(text).toLowerCase(Locale.ENGLISH);
    for (File version : versions) {
      String labelText = getLabelText(version).toLowerCase(Locale.ENGLISH);
      if (version.getName().equalsIgnoreCase(text) || version.getPath().equals(text)
          || getLabel(version).equalsIgnoreCase(text)
          || cleanText.length() > 0 && labelText.equals(cleanText)) {
        return version;
      }
      if (getLabel(version).toLowerCase(Locale.ENGLISH).contains(text.toLowerCase(Locale.ENGLISH))
          || cleanText.length() > 0 && (labelText.contains(cleanText)
              || labelText.length() >= 30 && cleanText.startsWith(labelText))) {
        if (match != null) {
          return null;
        }
        match = version;
      }
    }
    return match;
  }

  /**
   * Returns the list of the versions of <code>home</code> with their numbers.
   */
  public static String describe(Home home) {
    List<File> versions = getVersions(home);
    if (getFolder(home) == null) {
      return "This home was never saved in a file: it has no versions yet.";
    } else if (versions.isEmpty()) {
      return "This home has no version yet. They are kept in " + getFolder(home) + ".";
    }
    StringBuilder text = new StringBuilder("Versions of this home, the most recent first (in " + getFolder(home) + "):\n");
    for (int i = 0; i < versions.size(); i++) {
      text.append(i + 1).append(". ").append(getLabel(versions.get(i))).append("  (")
          .append(Math.max(1, versions.get(i).length() / 1024)).append(" KB)\n");
    }
    return text.toString().trim();
  }

  private static void collectItems(List<HomePieceOfFurniture> furniture, Map<String, Object> items) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        collectItems(((HomeFurnitureGroup)piece).getFurniture(), items);
      }
      String id = HomeItemIds.getId(piece);
      if (id != null) {
        items.put(id, piece);
      }
    }
  }

  private static Map<String, Object> collectItems(Home home) {
    Map<String, Object> items = new java.util.LinkedHashMap<String, Object>();
    collectItems(home.getFurniture(), items);
    for (Wall wall : home.getWalls()) {
      if (HomeItemIds.getId(wall) != null) {
        items.put(HomeItemIds.getId(wall), wall);
      }
    }
    for (Room room : home.getRooms()) {
      if (HomeItemIds.getId(room) != null) {
        items.put(HomeItemIds.getId(room), room);
      }
    }
    return items;
  }

  private static String describeItem(String id, Object item) {
    if (item instanceof HomePieceOfFurniture) {
      HomePieceOfFurniture piece = (HomePieceOfFurniture)item;
      return id + " \"" + piece.getName() + "\" at (" + Math.round(piece.getX()) + ", " + Math.round(piece.getY()) + ")";
    } else if (item instanceof Wall) {
      Wall wall = (Wall)item;
      return id + " wall from (" + Math.round(wall.getXStart()) + ", " + Math.round(wall.getYStart()) + ") to ("
          + Math.round(wall.getXEnd()) + ", " + Math.round(wall.getYEnd()) + ")";
    } else {
      Room room = (Room)item;
      return id + " room" + (room.getName() != null ? " \"" + room.getName() + "\"" : "") + " "
          + String.format(Locale.US, "%.1f", room.getArea() / 10000.) + " m2";
    }
  }

  private static String describeChange(Object oldItem, Object newItem) {
    List<String> changes = new ArrayList<String>();
    if (oldItem instanceof HomePieceOfFurniture && newItem instanceof HomePieceOfFurniture) {
      HomePieceOfFurniture oldPiece = (HomePieceOfFurniture)oldItem;
      HomePieceOfFurniture newPiece = (HomePieceOfFurniture)newItem;
      if (Math.hypot(oldPiece.getX() - newPiece.getX(), oldPiece.getY() - newPiece.getY()) > 0.5) {
        changes.add("moved from (" + Math.round(oldPiece.getX()) + ", " + Math.round(oldPiece.getY()) + ") to ("
            + Math.round(newPiece.getX()) + ", " + Math.round(newPiece.getY()) + ")");
      }
      if (Math.abs(oldPiece.getElevation() - newPiece.getElevation()) > 0.5) {
        changes.add("elevation " + Math.round(oldPiece.getElevation()) + " -> " + Math.round(newPiece.getElevation()));
      }
      if (Math.abs(Math.toDegrees(oldPiece.getAngle() - newPiece.getAngle())) > 0.5) {
        changes.add("turned to " + Math.round(Math.toDegrees(newPiece.getAngle())) + " degrees");
      }
      if (Math.abs(oldPiece.getWidth() - newPiece.getWidth()) > 0.5 || Math.abs(oldPiece.getDepth() - newPiece.getDepth()) > 0.5
          || Math.abs(oldPiece.getHeight() - newPiece.getHeight()) > 0.5) {
        changes.add("size " + Math.round(oldPiece.getWidth()) + " x " + Math.round(oldPiece.getDepth()) + " x "
            + Math.round(oldPiece.getHeight()) + " -> " + Math.round(newPiece.getWidth()) + " x "
            + Math.round(newPiece.getDepth()) + " x " + Math.round(newPiece.getHeight()));
      }
      if (oldPiece.getName() != null && !oldPiece.getName().equals(newPiece.getName())) {
        changes.add("renamed from \"" + oldPiece.getName() + "\"");
      }
      if (oldPiece.isVisible() != newPiece.isVisible()) {
        changes.add(newPiece.isVisible() ? "shown" : "hidden");
      }
      if (oldPiece.getColor() == null ? newPiece.getColor() != null : !oldPiece.getColor().equals(newPiece.getColor())) {
        changes.add("color changed");
      }
    } else if (oldItem instanceof Wall && newItem instanceof Wall) {
      Wall oldWall = (Wall)oldItem;
      Wall newWall = (Wall)newItem;
      if (Math.hypot(oldWall.getXStart() - newWall.getXStart(), oldWall.getYStart() - newWall.getYStart()) > 0.5
          || Math.hypot(oldWall.getXEnd() - newWall.getXEnd(), oldWall.getYEnd() - newWall.getYEnd()) > 0.5) {
        changes.add("moved or resized (" + Math.round(oldWall.getLength()) + " -> " + Math.round(newWall.getLength()) + " cm long)");
      }
      Float oldHeight = oldWall.getHeight();
      Float newHeight = newWall.getHeight();
      if (oldHeight == null ? newHeight != null : newHeight == null || Math.abs(oldHeight - newHeight) > 0.5) {
        changes.add("height " + (oldHeight != null ? String.valueOf(Math.round(oldHeight)) : "default") + " -> "
            + (newHeight != null ? String.valueOf(Math.round(newHeight)) : "default"));
      }
      if (Math.abs(oldWall.getThickness() - newWall.getThickness()) > 0.05) {
        changes.add("thickness " + oldWall.getThickness() + " -> " + newWall.getThickness());
      }
    } else if (oldItem instanceof Room && newItem instanceof Room) {
      Room oldRoom = (Room)oldItem;
      Room newRoom = (Room)newItem;
      if (Math.abs(oldRoom.getArea() - newRoom.getArea()) > 50 || !Arrays.deepEquals(oldRoom.getPoints(), newRoom.getPoints())) {
        changes.add("shape changed (" + String.format(Locale.US, "%.1f -> %.1f m2", oldRoom.getArea() / 10000., newRoom.getArea() / 10000.) + ")");
      }
      if (oldRoom.getName() == null ? newRoom.getName() != null : !oldRoom.getName().equals(newRoom.getName())) {
        changes.add("renamed from \"" + oldRoom.getName() + "\"");
      }
    }
    return changes.isEmpty() ? null : join(changes);
  }

  /**
   * Returns the items added, removed and changed from <code>oldHome</code> to <code>newHome</code>,
   * matched by their ids, at most <code>maxLines</code> of each kind.
   */
  public static String compareItems(Home oldHome, Home newHome, int maxLines) {
    Map<String, Object> oldItems = collectItems(oldHome);
    Map<String, Object> newItems = collectItems(newHome);
    if (oldItems.isEmpty() && newItems.isEmpty()) {
      return "";
    }
    List<String> added = new ArrayList<String>();
    List<String> removed = new ArrayList<String>();
    List<String> changed = new ArrayList<String>();
    for (Map.Entry<String, Object> entry : newItems.entrySet()) {
      Object oldItem = oldItems.get(entry.getKey());
      if (oldItem == null) {
        added.add(describeItem(entry.getKey(), entry.getValue()));
      } else {
        String change = describeChange(oldItem, entry.getValue());
        if (change != null) {
          changed.add(describeItem(entry.getKey(), entry.getValue()) + ": " + change);
        }
      }
    }
    for (Map.Entry<String, Object> entry : oldItems.entrySet()) {
      if (!newItems.containsKey(entry.getKey())) {
        removed.add(describeItem(entry.getKey(), entry.getValue()));
      }
    }
    StringBuilder text = new StringBuilder();
    appendLines(text, "Items added", added, maxLines);
    appendLines(text, "Items removed", removed, maxLines);
    appendLines(text, "Items changed", changed, maxLines);
    if (text.length() == 0) {
      text.append("No item with an id was added, removed, moved or resized.");
    }
    return text.toString().trim();
  }

  private static void appendLines(StringBuilder text, String title, List<String> lines, int maxLines) {
    if (!lines.isEmpty()) {
      text.append(title).append(" (").append(lines.size()).append("):\n");
      for (int i = 0; i < lines.size() && i < maxLines; i++) {
        text.append("- ").append(lines.get(i)).append('\n');
      }
      if (lines.size() > maxLines) {
        text.append("- ... and ").append(lines.size() - maxLines).append(" more\n");
      }
    }
  }

  private static String join(List<String> values) {
    StringBuilder text = new StringBuilder();
    for (String value : values) {
      text.append(text.length() > 0 ? ", " : "").append(value);
    }
    return text.toString();
  }
}
