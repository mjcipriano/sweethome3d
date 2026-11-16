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
    String cleanLabel = label != null ? label.replaceAll("[^\\p{L}\\p{N} _.,()-]", " ").replaceAll("\\s+", " ").trim() : "";
    if (cleanLabel.length() > 60) {
      cleanLabel = cleanLabel.substring(0, 60).trim();
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

  private static String join(List<String> values) {
    StringBuilder text = new StringBuilder();
    for (String value : values) {
      text.append(text.length() > 0 ? ", " : "").append(value);
    }
    return text.toString();
  }
}
