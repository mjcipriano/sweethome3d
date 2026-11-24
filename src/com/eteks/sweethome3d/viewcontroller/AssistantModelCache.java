/*
 * AssistantModelCache.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.eteks.sweethome3d.tools.OperatingSystem;

/**
 * The models built by the assistant kept in a folder of the user, to get them
 * back without building them again: a model is found by what it was built from
 * and by the version of the classes that built it.
 * The system property <code>com.eteks.sweethome3d.assistantModelCache</code> gives
 * another folder, or <code>false</code> to keep nothing.
 */
final class AssistantModelCache {
  static final String FOLDER_PROPERTY = "com.eteks.sweethome3d.assistantModelCache";
  static final String SIZE_PROPERTY = "com.eteks.sweethome3d.assistantModelCacheSize";

  private static final String KEY_ENTRY = "key.txt";
  private static final String BOUNDS_ENTRY = "bounds.txt";
  private static final long   DEFAULT_MAX_SIZE = 256L * 1024 * 1024;
  private static final long   MAX_ENTRY_LENGTH = 64L * 1024 * 1024;

  /** What builds the models: when one of them changes, the models kept aren't used anymore. */
  private static final String [] BUILDERS = {"AssistantPlantBuilder.class", "AssistantPlantBuilder$Plant.class",
      "AssistantPlantBuilder$Species.class", "AssistantPlantBuilder$Form.class", "AssistantTreeBuilder.class",
      "AssistantLeafTextures.class", "AssistantMeshBuilder.class", "AssistantMeshBuilder$Material.class",
      "AssistantMeshBuilder$Face.class", "AssistantPatterns.class", "AssistantGeneratedModel.class",
      "AssistantModelCache.class", "AssistantPlantProfiles.properties"};

  private static String version;

  private AssistantModelCache() {
  }

  /**
   * Returns the folder where models are kept, or <code>null</code> if they aren't kept.
   */
  static File getFolder() {
    try {
      String folder = System.getProperty(FOLDER_PROPERTY);
      if (folder == null) {
        return new File(OperatingSystem.getDefaultApplicationFolder(), "cache" + File.separator + "models");
      } else if (folder.length() == 0 || "false".equalsIgnoreCase(folder) || "none".equalsIgnoreCase(folder)) {
        return null;
      } else {
        return new File(folder);
      }
    } catch (IOException ex) {
      return null;
    } catch (RuntimeException ex) {
      // No right to read properties or folders
      return null;
    }
  }

  private static long getMaxSize() {
    try {
      String size = System.getProperty(SIZE_PROPERTY);
      if (size != null) {
        return Math.max(0, Long.parseLong(size.trim()));
      }
    } catch (RuntimeException ex) {
      // The default size
    }
    return DEFAULT_MAX_SIZE;
  }

  /**
   * Returns what tells the builders of this version from the ones of another version.
   */
  private static synchronized String getVersion() {
    if (version == null) {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte [] buffer = new byte [8192];
        for (String builder : BUILDERS) {
          InputStream in = AssistantModelCache.class.getResourceAsStream(builder);
          if (in == null) {
            continue;
          }
          try {
            digest.update(builder.getBytes("UTF-8"));
            for (int read; (read = in.read(buffer)) != -1; ) {
              digest.update(buffer, 0, read);
            }
          } finally {
            in.close();
          }
        }
        version = toHex(digest.digest());
      } catch (IOException ex) {
        version = "1";
      } catch (NoSuchAlgorithmException ex) {
        version = "1";
      }
    }
    return version;
  }

  private static String toHex(byte [] bytes) {
    StringBuilder text = new StringBuilder();
    for (byte b : bytes) {
      text.append(String.format("%02x", b & 0xFF));
    }
    return text.toString();
  }

  private static File getFile(File folder, String key) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update((getVersion() + "\n" + key).getBytes("UTF-8"));
      return new File(folder, toHex(digest.digest()) + ".zip");
    } catch (NoSuchAlgorithmException ex) {
      throw new IOException(ex.getMessage());
    }
  }

  /**
   * Returns the model kept for <code>key</code>, or <code>null</code> if there's none that can be read.
   */
  static AssistantGeneratedModel load(String prefix, String key) {
    File folder = getFolder();
    if (folder == null) {
      return null;
    }
    try {
      File file = getFile(folder, key);
      if (!file.isFile() || file.length() > MAX_ENTRY_LENGTH) {
        return null;
      }
      Map<String, byte []> files = new LinkedHashMap<String, byte []>();
      ZipInputStream zip = new ZipInputStream(new FileInputStream(file));
      try {
        long length = 0;
        byte [] buffer = new byte [8192];
        for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
          if (!entry.getName().matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")) {
            return null;
          }
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          for (int read; (read = zip.read(buffer)) != -1; ) {
            length += read;
            if (length > MAX_ENTRY_LENGTH) {
              return null;
            }
            out.write(buffer, 0, read);
          }
          files.put(entry.getName(), out.toByteArray());
        }
      } finally {
        zip.close();
      }
      byte [] storedKey = files.remove(KEY_ENTRY);
      byte [] storedBounds = files.remove(BOUNDS_ENTRY);
      if (storedKey == null || storedBounds == null || !key.equals(new String(storedKey, "UTF-8"))) {
        return null;
      }
      String [] values = new String(storedBounds, "UTF-8").trim().split(" ");
      float [] bounds = new float [6];
      for (int i = 0; i < 6; i++) {
        bounds [i] = Float.parseFloat(values [i]);
      }
      AssistantGeneratedModel model = AssistantGeneratedModel.store(prefix, files,
          bounds [3] - bounds [0], bounds [5] - bounds [2], bounds [4] - bounds [1]);
      model.setBounds(bounds);
      // The models used the longest time ago are the ones removed first
      file.setLastModified(System.currentTimeMillis());
      return model;
    } catch (IOException ex) {
      return null;
    } catch (RuntimeException ex) {
      // A file that wasn't written by this class
      return null;
    }
  }

  /**
   * Returns the model of <code>mesh</code>, kept for <code>key</code> if models are kept.
   */
  static AssistantGeneratedModel store(String prefix, String key, AssistantMeshBuilder mesh) throws IOException {
    Map<String, byte []> files = mesh.getFiles(prefix);
    float [] bounds = mesh.getBounds();
    AssistantGeneratedModel model = AssistantGeneratedModel.store(prefix, files,
        bounds [3] - bounds [0], bounds [5] - bounds [2], bounds [4] - bounds [1]);
    model.setBounds(bounds);
    File folder = getFolder();
    long maxSize = getMaxSize();
    if (folder != null && maxSize > 0) {
      File temporaryFile = null;
      try {
        if (folder.isDirectory() || folder.mkdirs()) {
          Map<String, byte []> entries = new LinkedHashMap<String, byte []>(files);
          entries.put(KEY_ENTRY, key.getBytes("UTF-8"));
          entries.put(BOUNDS_ENTRY, String.format(Locale.US, "%s %s %s %s %s %s", bounds [0], bounds [1], bounds [2],
              bounds [3], bounds [4], bounds [5]).getBytes("UTF-8"));
          File file = getFile(folder, key);
          // Written beside then renamed, for another program not to read the half of it
          temporaryFile = File.createTempFile("model", ".tmp", folder);
          AssistantGeneratedModel.writeZip(temporaryFile, entries);
          if (file.exists()) {
            file.delete();
          }
          if (temporaryFile.renameTo(file)) {
            temporaryFile = null;
            prune(folder, maxSize);
          }
        }
      } catch (IOException ex) {
        // The model is built again the next time
      } catch (RuntimeException ex) {
        // No right to write in the folder
      } finally {
        if (temporaryFile != null) {
          temporaryFile.delete();
        }
      }
    }
    return model;
  }

  /**
   * Removes the models used the longest time ago while the folder is larger than <code>maxSize</code> bytes.
   */
  private static void prune(File folder, long maxSize) {
    File [] files = folder.listFiles();
    if (files == null) {
      return;
    }
    long size = 0;
    long now = System.currentTimeMillis();
    for (File file : files) {
      if (file.getName().endsWith(".tmp") && now - file.lastModified() > 3600000L) {
        // What a program that stopped left
        file.delete();
      } else if (file.getName().endsWith(".zip")) {
        size += file.length();
      }
    }
    if (size <= maxSize) {
      return;
    }
    Arrays.sort(files, new Comparator<File>() {
        public int compare(File file1, File file2) {
          long time1 = file1.lastModified();
          long time2 = file2.lastModified();
          return time1 < time2 ? -1 : time1 > time2 ? 1 : 0;
        }
      });
    for (File file : files) {
      if (size <= maxSize * 3 / 4) {
        break;
      }
      if (file.getName().endsWith(".zip")) {
        long length = file.length();
        if (file.delete()) {
          size -= length;
        }
      }
    }
  }
}
