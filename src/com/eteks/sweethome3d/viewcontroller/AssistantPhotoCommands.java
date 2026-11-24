/*
 * AssistantPhotoCommands.java 23 nov. 2026
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;

/**
 * The commands that place photos in the plan from the position and the direction their camera recorded,
 * look at the home from where a photo was taken, and find a place from what two photos show.
 */
public class AssistantPhotoCommands {
  /** The property of a home that keeps its photos, in JSON. */
  static final String PHOTOS_PROPERTY = "assistantPhotos";

  private static final long MAX_PHOTO_LENGTH = 60L * 1024 * 1024;
  private static final int  MAX_PHOTOS = 300;
  /** The accuracy in cm of the place a phone records, when its photo doesn't tell it. */
  private static final float DEFAULT_GPS_ACCURACY = 800;
  /** How well a thing is pointed across a photo, in degrees. */
  private static final double MATCH_ACCURACY = 1.5;

  private final Home                     home;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantPhotoCommands(Home home, HomeController homeController, AssistantCommandExecutor executor) {
    this.home = home;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"import_photos\",\"folder\":\"<absolute path>\"}  "
        + "(or \"paths\":[...]: reads the place, the direction and the lens the camera wrote in each JPEG photo and marks it in the "
        + "plan with the cone of what it shows, a circle of its accuracy and a label; it needs the georeference of the home. A photo "
        + "without place is left out, one without direction gets a mark without cone. The answer gives each photo an id like PH1. "
        + "\"tag\" names the set; list_photos lists them, remove_photos takes them away with \"ids\" or all of them)\n"
        + "  {\"action\":\"add_photo\",\"path\":\"<file>\",\"x\":N,\"y\":N,\"heading\":N,\"fov\":65}  "
        + "(a photo placed by hand: heading in degrees clockwise from the top of the plan)\n"
        + "  {\"action\":\"photo_viewpoint\",\"photo\":\"PH1\"}  "
        + "(moves the camera of the 3D view where the photo was taken, 160 cm above the ground, looking the same way with the "
        + "same field of view. get_3d_image with \"photo\":\"PH1\" draws that view beside the photo without moving the camera: "
        + "the photo is turned upright as its camera held it, and the picture takes its shape and its field of view, upright "
        + "for a photo taken upright)\n"
        + "  {\"action\":\"refine_photo_pose\",\"photo\":\"PH1\",\"matches\":[{\"u\":0.25,\"plan\":[x,y]},{\"u\":0.8,\"plan\":[x,y]}]}  "
        + "(corrects the place and the direction of a photo from things seen in it whose place in the plan is known: u is where "
        + "each one is across the photo seen upright, 0 at its left edge and 1 at its right edge. Two matches correct its "
        + "direction, three or more its place too, the place its GPS recorded weighing as much as its accuracy: the one of the "
        + "photo, 8 m without it, or \"gps_accuracy_m\":N. A result farther than this accuracy, in a building or with a match "
        + "behind the camera isn't applied, and the answer tells why: \"force\":true applies it, \"keep_place\":true corrects "
        + "only the direction)\n"
        + "  {\"action\":\"locate_from_photos\",\"rays\":[{\"photo\":\"PH1\",\"u\":0.4},{\"photo\":\"PH2\",\"u\":0.7}]}  "
        + "(the point of the plan where the lines of sight of two photos or more cross, towards something seen in each one: "
        + "a tree, a post, a corner of a shed; the answer tells how well they agree)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("import_photos".equals(action) || "add_photo".equals(action)) {
        importPhotos(action, command, notes);
      } else if ("list_photos".equals(action)) {
        notes.add(describePhotos(this.home));
      } else if ("remove_photos".equals(action)) {
        removePhotos(command, tally, notes);
      } else if ("photo_viewpoint".equals(action)) {
        photoViewpoint(command, tally, notes);
      } else if ("refine_photo_pose".equals(action)) {
        refinePose(command, notes);
      } else if ("locate_from_photos".equals(action)) {
        locate(command, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    } catch (RuntimeException ex) {
      notes.add("Couldn't run " + action + " (" + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()) + ")");
    }
    return true;
  }

  private static String format(double value) {
    return AssistantStructureCommands.format((float)value);
  }

  // ---- The photos of a home ----

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> getPhotos(Home home) {
    List<Map<String, Object>> photos = new ArrayList<Map<String, Object>>();
    String property = home.getProperty(PHOTOS_PROPERTY);
    if (property != null) {
      try {
        Object parsed = JSONParser.parse(property);
        if (parsed instanceof List) {
          for (Object photo : (List<?>)parsed) {
            if (photo instanceof Map) {
              photos.add(new LinkedHashMap<String, Object>((Map<String, Object>)photo));
            }
          }
        }
      } catch (IllegalArgumentException ex) {
        // No photo
      }
    }
    return photos;
  }

  private void setPhotos(List<Map<String, Object>> photos) {
    this.home.setProperty(PHOTOS_PROPERTY, photos.isEmpty() ? null : AssistantShapeCommands.toJson(new ArrayList<Object>(photos)));
  }

  static Map<String, Object> getPhoto(Home home, String id) {
    for (Map<String, Object> photo : getPhotos(home)) {
      if (id != null && id.trim().equalsIgnoreCase(String.valueOf(photo.get("id")))) {
        return photo;
      }
    }
    return null;
  }

  private Map<String, Object> getPhoto(String id) throws IOException {
    Map<String, Object> photo = getPhoto(this.home, id);
    if (photo == null) {
      throw new IOException(id == null ? "give the id of the photo: \"photo\":\"PH1\" (list_photos lists them)"
          : "no photo " + id + ": list_photos lists the photos of the home");
    }
    return photo;
  }

  private static float number(Map<?, ?> map, String key, float defaultValue) {
    return AssistantFurnitureBuilder.getNumber(map, key, defaultValue);
  }

  /**
   * Returns the photos of a home, for the tools that list what agents may name.
   */
  static String describePhotos(Home home) {
    List<Map<String, Object>> photos = getPhotos(home);
    if (photos.isEmpty()) {
      return "The home has no photo: import_photos places the ones of a folder from the position their camera recorded.";
    }
    StringBuilder text = new StringBuilder("Photos placed in the plan (" + photos.size() + "):");
    for (Map<String, Object> photo : photos) {
      text.append("\n- ").append(photo.get("id")).append(" \"").append(photo.get("name")).append("\" at (")
          .append(format(number(photo, "x", 0))).append(", ").append(format(number(photo, "y", 0))).append(")");
      if (photo.get("heading") != null) {
        text.append(", looking ").append(format(number(photo, "heading", 0))).append(" degrees clockwise from the top of the plan, ")
            .append(format(number(photo, "fov", 65))).append(" degrees wide");
      } else {
        text.append(", direction unknown");
      }
      if (photo.get("accuracy") != null) {
        text.append(", within ").append(format(number(photo, "accuracy", 0) / 100)).append(" m");
      }
      if (photo.get("date") != null) {
        text.append(", taken ").append(photo.get("date"));
      }
      if (photo.get("refined") != null) {
        text.append(", corrected from ").append(photo.get("refined")).append(" matches");
      }
      text.append(", file ").append(photo.get("path"));
    }
    return text.toString();
  }

  // ---- EXIF ----

  /**
   * Returns what the camera wrote in a JPEG file: latitude, longitude, altitude, direction, accuracy,
   * focal length, focal length for a 35 mm film, date. A value that isn't written is missing.
   */
  static Map<String, Object> readExif(byte [] data) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    if (data.length < 4 || (data [0] & 0xFF) != 0xFF || (data [1] & 0xFF) != 0xD8) {
      return values;
    }
    int offset = 2;
    while (offset + 4 <= data.length && (data [offset] & 0xFF) == 0xFF) {
      int marker = data [offset + 1] & 0xFF;
      int length = ((data [offset + 2] & 0xFF) << 8) | (data [offset + 3] & 0xFF);
      if (marker == 0xE1 && offset + 10 <= data.length && data [offset + 4] == 'E' && data [offset + 5] == 'x'
          && data [offset + 6] == 'i' && data [offset + 7] == 'f') {
        int start = offset + 10;
        int end = Math.min(data.length, offset + 2 + length);
        if (start + 8 > end) {
          return values;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, start, end - start).slice();
        buffer.order(buffer.get(0) == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        try {
          Map<Integer, Object> ifd0 = readDirectory(buffer, buffer.getInt(4));
          if (ifd0.get(0x0112) instanceof Number && ((Number)ifd0.get(0x0112)).intValue() >= 1
              && ((Number)ifd0.get(0x0112)).intValue() <= 8) {
            values.put("orientation", Integer.valueOf(((Number)ifd0.get(0x0112)).intValue()));
          }
          if (ifd0.get(0x8769) instanceof Number) {
            Map<Integer, Object> exif = readDirectory(buffer, ((Number)ifd0.get(0x8769)).intValue());
            if (exif.get(0x9003) != null) {
              values.put("date", exif.get(0x9003).toString().trim());
            }
            if (exif.get(0x920A) instanceof double []) {
              values.put("focal_length", Double.valueOf(((double [])exif.get(0x920A)) [0]));
            }
            if (exif.get(0xA405) instanceof Number && ((Number)exif.get(0xA405)).doubleValue() > 0) {
              values.put("focal_length_35", Double.valueOf(((Number)exif.get(0xA405)).doubleValue()));
            }
          }
          if (ifd0.get(0x8825) instanceof Number) {
            Map<Integer, Object> gps = readDirectory(buffer, ((Number)ifd0.get(0x8825)).intValue());
            if (gps.get(2) instanceof double [] && gps.get(4) instanceof double [] && ((double [])gps.get(2)).length >= 3
                && ((double [])gps.get(4)).length >= 3) {
              double [] latitude = (double [])gps.get(2);
              double [] longitude = (double [])gps.get(4);
              double lat = latitude [0] + latitude [1] / 60 + latitude [2] / 3600;
              double lon = longitude [0] + longitude [1] / 60 + longitude [2] / 3600;
              values.put("lat", Double.valueOf("S".equalsIgnoreCase(String.valueOf(gps.get(1)).trim()) ? -lat : lat));
              values.put("lon", Double.valueOf("W".equalsIgnoreCase(String.valueOf(gps.get(3)).trim()) ? -lon : lon));
            }
            if (gps.get(6) instanceof double []) {
              values.put("altitude", Double.valueOf(((double [])gps.get(6)) [0]));
            }
            if (gps.get(17) instanceof double []) {
              values.put("direction", Double.valueOf(((double [])gps.get(17)) [0]));
              values.put("direction_ref", gps.get(16) != null ? gps.get(16).toString().trim() : "T");
            }
            if (gps.get(31) instanceof double []) {
              values.put("accuracy", Double.valueOf(((double [])gps.get(31)) [0]));
            }
          }
        } catch (RuntimeException ex) {
          // What was read is kept
        }
        return values;
      } else if (marker == 0xDA || length < 2) {
        break;
      }
      offset += 2 + length;
    }
    return values;
  }

  /**
   * Returns the way the picture of a JPEG file is turned, as its camera wrote it: 1 for a picture
   * stored upright, 3 upside down, 6 and 8 for a picture to turn by a quarter clockwise and
   * counter clockwise, 2, 4, 5 and 7 for mirrored ones.
   */
  public static int getOrientation(byte [] data) {
    Object orientation = readExif(data).get("orientation");
    return orientation instanceof Number ? ((Number)orientation).intValue() : 1;
  }

  /**
   * Returns the width of a photo divided by its height once it is turned upright, or 0 if it can't be read.
   */
  static float getAspect(byte [] data) {
    int [] size = AssistantGeneratedModel.getImageSize(data);
    if (size == null || size [0] < 1 || size [1] < 1) {
      return 0;
    }
    return getOrientation(data) >= 5 ? (float)size [1] / size [0] : (float)size [0] / size [1];
  }

  /**
   * Returns the shape and the width of view of a photo, as seen upright: its width divided by its
   * height (0 if unknown) and the angle in degrees it sees from its left edge to its right edge.
   */
  static float [] getShape(Map<String, Object> photo) {
    float fieldOfView = number(photo, "fov", 65);
    float aspect = number(photo, "aspect", 0);
    if (aspect <= 0 && photo.get("path") != null) {
      // A photo placed by an older version, which took the long side of every photo for its width
      try {
        aspect = getAspect(AssistantGeneratedModel.readFile(new File(photo.get("path").toString()), MAX_PHOTO_LENGTH));
        if (aspect > 0 && aspect < 1 && photo.get("lat") != null) {
          fieldOfView = (float)Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(fieldOfView / 2)) * aspect));
        }
      } catch (IOException ex) {
        aspect = 0;
      }
    }
    return new float [] {aspect, fieldOfView};
  }

  /**
   * Returns the values of a directory of tags: a text, a number, or the numbers of a list of fractions.
   */
  private static Map<Integer, Object> readDirectory(ByteBuffer buffer, int offset) {
    Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();
    if (offset < 0 || offset + 2 > buffer.limit()) {
      return values;
    }
    int count = buffer.getShort(offset) & 0xFFFF;
    for (int i = 0; i < count && offset + 2 + (i + 1) * 12 <= buffer.limit(); i++) {
      int entry = offset + 2 + i * 12;
      int tag = buffer.getShort(entry) & 0xFFFF;
      int type = buffer.getShort(entry + 2) & 0xFFFF;
      int valueCount = buffer.getInt(entry + 4);
      int [] sizes = {0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8};
      if (type >= sizes.length || valueCount < 0 || valueCount > 4096) {
        continue;
      }
      int valueOffset = (long)sizes [type] * valueCount <= 4 ? entry + 8 : buffer.getInt(entry + 8);
      if (valueOffset < 0 || valueOffset + (long)sizes [type] * valueCount > buffer.limit()) {
        continue;
      }
      if (type == 2) {
        StringBuilder text = new StringBuilder();
        for (int k = 0; k < valueCount && buffer.get(valueOffset + k) != 0; k++) {
          text.append((char)(buffer.get(valueOffset + k) & 0xFF));
        }
        values.put(tag, text.toString());
      } else if (type == 3) {
        values.put(tag, Integer.valueOf(buffer.getShort(valueOffset) & 0xFFFF));
      } else if (type == 4 || type == 9) {
        values.put(tag, Integer.valueOf(buffer.getInt(valueOffset)));
      } else if (type == 5 || type == 10) {
        double [] numbers = new double [valueCount];
        for (int k = 0; k < valueCount; k++) {
          long numerator = type == 5 ? buffer.getInt(valueOffset + k * 8) & 0xFFFFFFFFL : buffer.getInt(valueOffset + k * 8);
          long denominator = type == 5 ? buffer.getInt(valueOffset + k * 8 + 4) & 0xFFFFFFFFL : buffer.getInt(valueOffset + k * 8 + 4);
          numbers [k] = denominator != 0 ? (double)numerator / denominator : 0;
        }
        values.put(tag, numbers);
      } else if (type == 1 || type == 7) {
        values.put(tag, Integer.valueOf(buffer.get(valueOffset) & 0xFF));
      }
    }
    return values;
  }

  // ---- Commands ----

  private void importPhotos(String action, AssistantCommand command, List<String> notes) throws IOException {
    List<File> files = new ArrayList<File>();
    boolean byHand = "add_photo".equals(action);
    if (command.getString("folder") != null && !byHand) {
      File folder = new File(command.getString("folder").trim().replaceFirst("^~/", System.getProperty("user.home").replace("\\", "/") + "/"));
      if (!folder.isAbsolute() || !folder.isDirectory()) {
        throw new IOException("\"folder\" must be the absolute path of a folder that exists");
      }
      File [] list = folder.listFiles();
      if (list != null) {
        Arrays.sort(list);
        for (File file : list) {
          String name = file.getName().toLowerCase(Locale.ENGLISH);
          if (file.isFile() && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
            files.add(file);
          }
        }
      }
    }
    List<String> paths = new ArrayList<String>(command.getStringList("paths"));
    if (command.getString("path") != null) {
      paths.add(command.getString("path"));
    }
    for (String path : paths) {
      File file = new File(path.trim().replaceFirst("^~/", System.getProperty("user.home").replace("\\", "/") + "/"));
      if (!file.isAbsolute() || !file.isFile()) {
        throw new IOException("no file " + path + ": a photo is given by its absolute path");
      }
      files.add(file);
    }
    if (files.isEmpty()) {
      throw new IOException(byHand ? "give the \"path\" of the photo" : "no JPEG photo: give \"folder\" or \"paths\":[...]");
    } else if (files.size() > MAX_PHOTOS) {
      throw new IOException(files.size() + " photos is more than " + MAX_PHOTOS + ": give them in several calls");
    }
    HomeGeoreference georeference = HomeGeoreference.get(this.home);
    if (georeference == null && !byHand) {
      throw new IOException("the photos are placed by latitude and longitude: the home needs its georeference, which set_georeference gives");
    }
    Level level = this.executor.getShapeCommands().getLevel(command, action, notes);
    List<Map<String, Object>> photos = getPhotos(this.home);
    int nextId = 1;
    for (Map<String, Object> photo : photos) {
      try {
        nextId = Math.max(nextId, Integer.parseInt(String.valueOf(photo.get("id")).substring(2)) + 1);
      } catch (RuntimeException ex) {
        // Not a number
      }
    }
    List<String> withoutPlace = new ArrayList<String>();
    List<Map<String, Object>> added = new ArrayList<Map<String, Object>>();
    int withoutDirection = 0;
    for (File file : files) {
      byte [] data = AssistantGeneratedModel.readFile(file, MAX_PHOTO_LENGTH);
      Map<String, Object> exif = byHand ? new LinkedHashMap<String, Object>() : readExif(data);
      // The shape of the photo once it is turned as its camera held it
      float aspect = getAspect(data);
      Map<String, Object> photo = new LinkedHashMap<String, Object>();
      double x = command.getDouble("x", Double.NaN);
      double y = command.getDouble("y", Double.NaN);
      if (!byHand) {
        if (!(exif.get("lat") instanceof Number)) {
          withoutPlace.add(file.getName());
          continue;
        }
        double [] plan = georeference.toPlan(((Number)exif.get("lat")).doubleValue(), ((Number)exif.get("lon")).doubleValue());
        x = plan [0];
        y = plan [1];
        photo.put("lat", exif.get("lat"));
        photo.put("lon", exif.get("lon"));
      } else if (Double.isNaN(x) || Double.isNaN(y)) {
        throw new IOException("give the place the photo was taken from: \"x\" and \"y\"");
      }
      if (!AssistantCommandExecutor.coordinateSane(x) || !AssistantCommandExecutor.coordinateSane(y)) {
        withoutPlace.add(file.getName() + " (more than 10 km from the plan)");
        continue;
      }
      photo.put("id", "PH" + nextId++);
      photo.put("name", command.getString("name") != null && byHand ? command.getString("name") : file.getName());
      photo.put("path", file.getPath());
      photo.put("x", Double.valueOf(Math.round(x)));
      photo.put("y", Double.valueOf(Math.round(y)));
      if (byHand && command.has("heading")) {
        photo.put("heading", Double.valueOf(command.getDouble("heading", 0)));
      } else if (exif.get("direction") instanceof Number) {
        // From the true north to the top of the plan; a magnetic direction is taken as it is
        double heading = ((Number)exif.get("direction")).doubleValue() + georeference.getRotation();
        photo.put("heading", Double.valueOf(Math.round(((heading % 360) + 360) % 360 * 10) / 10.));
      } else {
        withoutDirection++;
      }
      double fieldOfView = command.getDouble("fov", Double.NaN);
      if (Double.isNaN(fieldOfView) && exif.get("focal_length_35") instanceof Number) {
        // Across the photo: the long side of the film, or its short side for a photo taken upright
        fieldOfView = Math.toDegrees(2 * Math.atan(36 * (aspect > 0 && aspect < 1 ? aspect : 1)
            / (2 * ((Number)exif.get("focal_length_35")).doubleValue())));
      }
      if (aspect > 0) {
        photo.put("aspect", Double.valueOf(Math.round(aspect * 1000) / 1000.));
      }
      photo.put("fov", Double.valueOf(Double.isNaN(fieldOfView) ? 65 : Math.round(Math.max(10, Math.min(150, fieldOfView)) * 10) / 10.));
      if (exif.get("accuracy") instanceof Number) {
        photo.put("accuracy", Double.valueOf(Math.round(((Number)exif.get("accuracy")).doubleValue() * 100)));
      }
      if (exif.get("altitude") instanceof Number) {
        photo.put("altitude_m", exif.get("altitude"));
      }
      if (exif.get("date") != null) {
        photo.put("date", exif.get("date"));
      }
      if (command.getString("tag") != null) {
        photo.put("tag", command.getString("tag"));
      }
      added.add(photo);
      photos.add(photo);
    }
    if (added.isEmpty()) {
      throw new IOException("none of the " + files.size() + " photos has a place written by its camera: " + withoutPlace
          + ". add_photo places one by hand with x, y and heading");
    }
    setPhotos(photos);
    for (Map<String, Object> photo : added) {
      drawMarker(photo, level);
    }
    StringBuilder text = new StringBuilder("Placed " + added.size() + (added.size() == 1 ? " photo" : " photos") + " in the plan: ");
    for (int i = 0; i < added.size() && i < 40; i++) {
      Map<String, Object> photo = added.get(i);
      text.append(i > 0 ? "; " : "").append(photo.get("id")).append(" \"").append(photo.get("name")).append("\" at (")
          .append(format(number(photo, "x", 0))).append(", ").append(format(number(photo, "y", 0))).append(")")
          .append(photo.get("heading") != null ? " looking " + format(number(photo, "heading", 0)) + " deg" : "");
    }
    if (added.size() > 40) {
      text.append("; ... list_photos lists them all");
    }
    if (withoutDirection > 0) {
      text.append(". ").append(withoutDirection).append(" have no direction: their mark has no cone, and refine_photo_pose finds it");
    }
    if (!withoutPlace.isEmpty()) {
      text.append(". Left out, without place: ").append(withoutPlace.size() > 10 ? withoutPlace.subList(0, 10) + "..." : withoutPlace);
    }
    text.append(". The place a phone records is often 3 to 10 m off: refine_photo_pose corrects it from things seen in the photo");
    notes.add(text.toString());
  }

  /**
   * Draws the mark of a photo in the plan: the cone it shows, the circle of its accuracy and its id.
   */
  private void drawMarker(Map<String, Object> photo, Level level) throws IOException {
    float x = number(photo, "x", 0);
    float y = number(photo, "y", 0);
    String id = String.valueOf(photo.get("id"));
    List<Polyline> polylines = new ArrayList<Polyline>();
    AssistantSiteCommands siteCommands = this.executor.getSiteCommands();
    if (photo.get("heading") != null) {
      double heading = Math.toRadians(number(photo, "heading", 0));
      double half = Math.toRadians(number(photo, "fov", 65) / 2);
      float reach = 600;
      float [][] cone = {{x + (float)Math.sin(heading - half) * reach, y - (float)Math.cos(heading - half) * reach}, {x, y},
                         {x + (float)Math.sin(heading + half) * reach, y - (float)Math.cos(heading + half) * reach}};
      polylines.add(siteCommands.createPolyline(cone, false, level, id, "photo", "#C0392B", 1.5, null, false, Double.NaN));
    } else {
      float [][] cross = {{x - 30, y}, {x + 30, y}};
      polylines.add(siteCommands.createPolyline(cross, false, level, id, "photo", "#C0392B", 1.5, null, false, Double.NaN));
    }
    float accuracy = number(photo, "accuracy", 0);
    if (accuracy > 50) {
      float [][] circle = new float [24][];
      for (int i = 0; i < circle.length; i++) {
        double angle = Math.PI * 2 * i / circle.length;
        circle [i] = new float [] {x + (float)Math.cos(angle) * accuracy, y + (float)Math.sin(angle) * accuracy};
      }
      polylines.add(siteCommands.createPolyline(circle, true, level, id, "photo", "#C0392B", 0.8, "dashed", false, Double.NaN));
    }
    siteCommands.addPolylines(polylines, level);
    Label label = new Label(id, x, y + 25);
    label.setLevel(level);
    label.setProperty(AssistantSiteCommands.LINE_KIND_PROPERTY, "photo");
    label.setProperty(AssistantSiteCommands.LINE_NAME_PROPERTY, id);
    this.executor.beginTurnUpdate();
    this.homeController.getPlanController().addLabels(Collections.singletonList(label));
  }

  /**
   * Deletes the marks of photos, or of all of them if <code>ids</code> is <code>null</code>.
   */
  private int deleteMarkers(List<String> ids) {
    List<Selectable> marks = new ArrayList<Selectable>();
    for (Polyline polyline : this.home.getPolylines()) {
      if ("photo".equals(polyline.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))
          && (ids == null || ids.contains(polyline.getProperty(AssistantSiteCommands.LINE_NAME_PROPERTY)))) {
        marks.add(polyline);
      }
    }
    for (Label label : this.home.getLabels()) {
      if ("photo".equals(label.getProperty(AssistantSiteCommands.LINE_KIND_PROPERTY))
          && (ids == null || ids.contains(label.getProperty(AssistantSiteCommands.LINE_NAME_PROPERTY)))) {
        marks.add(label);
      }
    }
    if (!marks.isEmpty()) {
      this.executor.beginTurnUpdate();
      this.homeController.getPlanController().deleteItems(marks);
    }
    return marks.size();
  }

  private void removePhotos(AssistantCommand command, int [] tally, List<String> notes) {
    List<String> ids = new ArrayList<String>();
    for (String id : command.getStringList("ids")) {
      ids.add(id.trim().toUpperCase(Locale.ENGLISH));
    }
    if (command.getString("photo") != null) {
      ids.add(command.getString("photo").trim().toUpperCase(Locale.ENGLISH));
    }
    List<Map<String, Object>> photos = getPhotos(this.home);
    int count = photos.size();
    if (ids.isEmpty()) {
      photos.clear();
    } else {
      for (int i = photos.size() - 1; i >= 0; i--) {
        if (ids.contains(String.valueOf(photos.get(i).get("id")))) {
          photos.remove(i);
        }
      }
    }
    setPhotos(photos);
    tally [5] += deleteMarkers(ids.isEmpty() ? null : ids);
    notes.add("Removed " + (count - photos.size()) + " photos from the plan; their files aren't touched");
  }

  private void photoViewpoint(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    Map<String, Object> photo = getPhoto(command.getString("photo") != null ? command.getString("photo") : command.getString("id"));
    if (photo.get("heading") == null) {
      throw new IOException("the photo " + photo.get("id") + " has no direction: refine_photo_pose finds it from two things seen in it");
    }
    double heading = Math.toRadians(number(photo, "heading", 0));
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    parameters.put("x", photo.get("x"));
    parameters.put("y", photo.get("y"));
    parameters.put("height", Double.valueOf(command.getDouble("height", 160)));
    parameters.put("look_at_x", Double.valueOf(number(photo, "x", 0) + Math.sin(heading) * 1000));
    parameters.put("look_at_y", Double.valueOf(number(photo, "y", 0) - Math.cos(heading) * 1000));
    parameters.put("look_at_height", Double.valueOf(command.getDouble("height", 160)));
    parameters.put("mode", "visitor");
    parameters.put("fov", Double.valueOf(getShape(photo) [1]));
    List<String> cameraNotes = new ArrayList<String>();
    this.executor.applySubCommand(new AssistantCommand("set_camera", parameters), tally, cameraNotes);
    notes.add("Moved the camera where the photo " + photo.get("id") + " \"" + photo.get("name") + "\" was taken: at ("
        + format(number(photo, "x", 0)) + ", " + format(number(photo, "y", 0)) + "), looking " + format(number(photo, "heading", 0))
        + " degrees clockwise from the top of the plan, " + format(number(photo, "fov", 65)) + " degrees wide. get_3d_image with "
        + "\"photo\":\"" + photo.get("id") + "\" draws this view beside the photo");
  }

  /**
   * Returns the direction in the plan, in degrees clockwise from its top, of what is at <code>u</code> across a photo.
   */
  private static double getBearing(Map<String, Object> photo, double u) {
    double half = Math.toRadians(getShape(photo) [1] / 2);
    return number(photo, "heading", 0) + Math.toDegrees(Math.atan((u - 0.5) * 2 * Math.tan(half)));
  }

  private static double wrap(double degrees) {
    return ((degrees % 360) + 540) % 360 - 180;
  }

  private void refinePose(AssistantCommand command, List<String> notes) throws IOException {
    Map<String, Object> photo = getPhoto(command.getString("photo") != null ? command.getString("photo") : command.getString("id"));
    Object matchesValue = command.getParameters().get("matches");
    List<double []> matches = new ArrayList<double []>();
    if (matchesValue instanceof List) {
      for (Object item : (List<?>)matchesValue) {
        if (item instanceof Map && ((Map<?, ?>)item).get("u") instanceof Number && ((Map<?, ?>)item).get("plan") instanceof List
            && ((List<?>)((Map<?, ?>)item).get("plan")).size() >= 2 && ((List<?>)((Map<?, ?>)item).get("plan")).get(0) instanceof Number) {
          List<?> plan = (List<?>)((Map<?, ?>)item).get("plan");
          matches.add(new double [] {((Number)((Map<?, ?>)item).get("u")).doubleValue(), ((Number)plan.get(0)).doubleValue(),
                                     ((Number)plan.get(1)).doubleValue()});
        }
      }
    }
    if (matches.size() < 2) {
      throw new IOException("give 2 \"matches\" at least: {\"u\":0.25,\"plan\":[x,y]}, u being where the thing is across the photo "
          + "(0 = its left edge, 1 = its right edge) and plan its place");
    }
    double half = Math.toRadians(getShape(photo) [1] / 2);
    double startX = number(photo, "x", 0);
    double startY = number(photo, "y", 0);
    boolean force = command.getBoolean("force", false);
    boolean movePlace = matches.size() >= 3 && !command.getBoolean("keep_place", false);
    // How far the place a phone recorded may be from the true one
    double accuracy = command.has("gps_accuracy_m") ? command.getDouble("gps_accuracy_m", 8) * 100
        : number(photo, "accuracy", DEFAULT_GPS_ACCURACY);
    accuracy = Math.max(100, Math.min(10000, accuracy));
    // The place around the recorded one, and the direction, that put the things where the photo shows them
    double bestX = startX;
    double bestY = startY;
    double bestHeading = 0;
    double bestCost = Double.MAX_VALUE;
    double bestError = Double.MAX_VALUE;
    double reach = movePlace ? Math.max(1500, accuracy * 3) : 0;
    for (double step = reach > 0 ? reach / 8 : 1; ; step /= 4) {
      double centerX = bestX;
      double centerY = bestY;
      for (int column = -8; column <= 8; column++) {
        for (int row = -8; row <= 8; row++) {
          double x = movePlace ? centerX + column * step : startX;
          double y = movePlace ? centerY + row * step : startY;
          if (movePlace && !force && !(x == startX && y == startY) && isInBuilding(x, y)) {
            // No photo of the outside is taken from within a building
            continue;
          }
          // The direction that fits best from this place
          double sumSin = 0;
          double sumCos = 0;
          double [] offsets = new double [matches.size()];
          for (int i = 0; i < matches.size(); i++) {
            double [] match = matches.get(i);
            double bearing = Math.toDegrees(Math.atan2(match [1] - x, -(match [2] - y)));
            offsets [i] = bearing - Math.toDegrees(Math.atan((match [0] - 0.5) * 2 * Math.tan(half)));
            sumSin += Math.sin(Math.toRadians(offsets [i]));
            sumCos += Math.cos(Math.toRadians(offsets [i]));
          }
          double heading = Math.toDegrees(Math.atan2(sumSin, sumCos));
          double error = 0;
          for (double offset : offsets) {
            error += wrap(offset - heading) * wrap(offset - heading);
          }
          // The recorded place is a measure too, as good as its accuracy; forced, it only breaks ties
          double distance = Math.hypot(x - startX, y - startY);
          double cost = force ? error + 1E-9 * distance
              : error / (MATCH_ACCURACY * MATCH_ACCURACY) + distance * distance / (accuracy * accuracy);
          if (cost < bestCost) {
            bestCost = cost;
            bestError = error;
            bestX = x;
            bestY = y;
            bestHeading = heading;
          }
          if (!movePlace) {
            break;
          }
        }
        if (!movePlace) {
          break;
        }
      }
      if (!movePlace || step < 5) {
        break;
      }
    }
    double residual = Math.sqrt(bestError / matches.size());
    double moved = Math.hypot(bestX - startX, bestY - startY);
    double turned = photo.get("heading") != null ? wrap(bestHeading - number(photo, "heading", 0)) : Double.NaN;
    String hint = ": check the matches (\"u\" goes from 0 at the left edge to 1 at the right edge of the photo seen upright, and "
        + "\"plan\" is the place of the same thing). \"keep_place\":true corrects only the direction, \"gps_accuracy_m\":N "
        + "tells another accuracy, and \"force\":true applies the result anyway";
    if (!force) {
      for (int i = 0; i < matches.size(); i++) {
        double [] match = matches.get(i);
        double bearing = Math.toDegrees(Math.atan2(match [1] - bestX, -(match [2] - bestY)));
        if (Math.abs(wrap(bearing - bestHeading)) > 90) {
          throw new IOException("nothing was changed: the match " + (i + 1) + " at (" + format(match [1]) + ", " + format(match [2])
              + ") would be behind the camera" + hint);
        }
      }
      if (movePlace && moved > accuracy) {
        throw new IOException("nothing was changed: the matches put the camera " + format(moved / 100) + " m from the place its GPS "
            + "recorded, at (" + format(bestX) + ", " + format(bestY) + "), more than the GPS accuracy " + format(accuracy / 100)
            + " m, with matches " + format(residual) + " degrees off on average" + hint);
      }
    }
    boolean inBuilding = isInBuilding(bestX, bestY);
    List<Map<String, Object>> photos = getPhotos(this.home);
    for (Map<String, Object> other : photos) {
      if (String.valueOf(photo.get("id")).equals(String.valueOf(other.get("id")))) {
        other.put("x", Double.valueOf(Math.round(bestX)));
        other.put("y", Double.valueOf(Math.round(bestY)));
        other.put("heading", Double.valueOf(Math.round((((bestHeading % 360) + 360) % 360) * 10) / 10.));
        other.put("refined", Double.valueOf(matches.size()));
        if (movePlace) {
          other.remove("accuracy");
        }
        photo = other;
      }
    }
    setPhotos(photos);
    deleteMarkers(Collections.singletonList(String.valueOf(photo.get("id"))));
    drawMarker(photo, this.home.getSelectedLevel());
    notes.add("Corrected the photo " + photo.get("id") + " from " + matches.size() + " matches: " + (movePlace
            ? "its place moved by " + format(moved / 100) + " m to (" + format(bestX) + ", " + format(bestY) + "), within the GPS accuracy "
                + format(accuracy / 100) + " m that weighs the place recorded, "
            : "its place kept, ")
        + "its direction " + (Double.isNaN(turned) ? "found: " : "turned by " + format(turned) + " degrees to ")
        + format(((bestHeading % 360) + 360) % 360) + " degrees. The matches are " + format(residual) + " degrees off on average"
        + (residual > 3 ? ": more than 3 degrees means a match is wrong, or the field of view of the photo (\"fov\" "
            + format(number(photo, "fov", 65)) + ") isn't the one of its lens" : "")
        + (inBuilding ? ". Warning: this place is inside a building" : "")
        + (movePlace ? "" : matches.size() >= 3 ? "" : ". With 3 matches or more its place is corrected too"));
  }

  /**
   * Returns <code>true</code> if a place is in a room under a ceiling or in a neighbor building.
   */
  private boolean isInBuilding(double x, double y) {
    for (Room room : this.home.getRooms()) {
      if (room.isCeilingVisible() && room.containsPoint((float)x, (float)y, 0)) {
        return true;
      }
    }
    for (HomePieceOfFurniture piece : this.home.getFurniture()) {
      if ("context_building".equals(piece.getProperty(AssistantGardenCommands.GARDEN_PROPERTY))
          && piece.containsPoint((float)x, (float)y, 0)) {
        return true;
      }
    }
    return false;
  }

  private void locate(AssistantCommand command, List<String> notes) throws IOException {
    Object raysValue = command.getParameters().get("rays");
    List<double []> rays = new ArrayList<double []>();
    List<String> names = new ArrayList<String>();
    if (raysValue instanceof List) {
      for (Object item : (List<?>)raysValue) {
        if (item instanceof Map && ((Map<?, ?>)item).get("photo") != null && ((Map<?, ?>)item).get("u") instanceof Number) {
          Map<String, Object> photo = getPhoto(((Map<?, ?>)item).get("photo").toString());
          if (photo.get("heading") == null) {
            throw new IOException("the photo " + photo.get("id") + " has no direction: refine_photo_pose finds it");
          }
          double bearing = Math.toRadians(getBearing(photo, ((Number)((Map<?, ?>)item).get("u")).doubleValue()));
          rays.add(new double [] {number(photo, "x", 0), number(photo, "y", 0), Math.sin(bearing), -Math.cos(bearing)});
          names.add(String.valueOf(photo.get("id")));
        }
      }
    }
    if (rays.size() < 2) {
      throw new IOException("give 2 \"rays\" at least, from two photos: {\"photo\":\"PH1\",\"u\":0.4}, u being where the thing is "
          + "across the photo (0 = its left edge, 1 = its right edge)");
    }
    // The point the closest to every line of sight
    double a11 = 0;
    double a12 = 0;
    double a22 = 0;
    double b1 = 0;
    double b2 = 0;
    for (double [] ray : rays) {
      double nx = -ray [3];
      double ny = ray [2];
      a11 += nx * nx;
      a12 += nx * ny;
      a22 += ny * ny;
      double offset = nx * ray [0] + ny * ray [1];
      b1 += nx * offset;
      b2 += ny * offset;
    }
    double determinant = a11 * a22 - a12 * a12;
    if (Math.abs(determinant) < 1E-4) {
      throw new IOException("the lines of sight are parallel: the photos look the same way at the same thing. "
          + "Use photos taken from places farther apart");
    }
    double x = (b1 * a22 - b2 * a12) / determinant;
    double y = (a11 * b2 - a12 * b1) / determinant;
    double worst = 0;
    boolean behind = false;
    double smallestAngle = 180;
    for (int i = 0; i < rays.size(); i++) {
      double [] ray = rays.get(i);
      worst = Math.max(worst, Math.abs(-ray [3] * (x - ray [0]) + ray [2] * (y - ray [1])));
      behind |= (x - ray [0]) * ray [2] + (y - ray [1]) * ray [3] < 0;
      for (int j = i + 1; j < rays.size(); j++) {
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, ray [2] * rays.get(j) [2] + ray [3] * rays.get(j) [3]))));
        smallestAngle = Math.min(smallestAngle, Math.min(angle, 180 - angle));
      }
    }
    if (behind) {
      throw new IOException("the lines of sight cross behind a camera, at (" + format(x) + ", " + format(y)
          + "): a \"u\" or the direction of a photo is wrong");
    }
    notes.add("The lines of sight of " + names + " cross at (" + format(x) + ", " + format(y) + ")"
        + (rays.size() > 2 ? ", within " + format(worst) + " cm of each line" : "")
        + ". They cross at " + format(smallestAngle) + " degrees" + (smallestAngle < 15
            ? ": under 15 degrees the place is uncertain along the lines of sight, by meters. Photos from places farther apart are surer"
            : "") + ". The place is as right as the places and the directions of the photos");
  }
}
